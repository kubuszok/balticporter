package balticporter.tir

import balticporter.catalog.{ApiRow, ApiRows, DiffId, FixKind, Platform}

/** Which JDK APIs the port still depends on that a cross-platform target cannot provide. sge
  * targets JS/Native/JVM, so a port can compile and test green on the JVM and still be JVM-only.
  * The TIR pre-flight replaces `--js` linking: every external symbol reference is enumerated.
  * §1(b), parameterised on the TARGET SET — a rule carries the platforms it refuses FOR, matched
  * against `ApiRows` (never claiming a platform its cited row says `Keep`). */
object PortabilityCheck extends RemedySource:

  /** THE `CheckReport` LANE THIS CHECK'S EMITTED-CODE RESIDUE IS COUNTED IN — one spelling, since a
    * lane had THREE literals to agree by inspection: `PortRun.PortabilityEmitted`,
    * [[AcceptJvmOnly]]'s own, and `RemediationTransform.Lane`'s. Lives on the CHECK, which mints
    * the rows; `PortRun` and `RemediationTransform` both read it. */
  val EmittedLane: String = "portability(emitted)"

  /** THE ONE REMEDY THIS CHECK CAN CARRY OUT — see [[AcceptJvmOnly]]. Declared here since this
    * lane's producer IS a check, not a phase. */
  def remedies: List[Remedy] = List(AcceptJvmOnly)

  /** WHAT IS NOT ON THIS MENU: drop/inline/redirect are on the PHASE's menu
    * (`RemediationTransform`), which changes the tree before this check runs; suppressing the row
    * is REFUSED (narrow `PortManifest.targets` instead); a per-API acceptance keyed on the FQN is
    * REFUSED (`verdictOverrides` already does this, read by `rulesFor`). [[Remedy.AnyKind]] rather
    * than `alsoKinds`: this lane's kind column is the offending API's FQN, an open set. */

  /** `accept-jvm-only` — *this location is JVM-only and I know it; stop reporting it.* Changes NO
    * tree, moves a row into `remediation(resolved)`. CONSISTENCY: accepting a JVM-only API while
    * `targets` includes Scala.js/Native is REPORTED as a contradiction and never applied — the
    * apply arm is unreachable by construction (`ENGINE-LIMITS.md` P6). Honest answers: narrow
    * `targets`, or a `verdictOverrides` entry. */
  val AcceptJvmOnly: Remedy = Remedy(
    id = "accept-jvm-only", lane = EmittedLane, kind = Remedy.AnyKind,
    emissionAffecting = false, fix = FixKind.Universal, subject = Remedy.Subject.OwnedMember,
    what = "accept this location as JVM-only: the row moves to `remediation(resolved)` and no tree " +
      "changes — refused, with both real knobs named, on a port whose `targets` claim Scala.js or " +
      "Scala Native")

  /** @param api a prefix (`java.nio.file.`) or an exact `owner#member` @param why one sentence
    * @param exactMember which of the two `api` is @param on THE PLATFORMS THIS RULE IS A REFUSAL
    * FOR (unconditional would tell a JVM+Native port to remove APIs that work on both) @param at
    * the catalog row holding the availability FACT, `None` only for a non-JDK dependency. */
  final case class Rule(
      api: String,
      why: String,
      exactMember: Boolean = false,
      on: Set[Platform] = Rule.OffJvm,
      at: Option[DiffId] = scala.None,
  ):
    /** does this rule ask a question of any platform in `targets`? */
    def asks(targets: Set[Platform]): Boolean = on.exists(targets.contains)

  object Rule:
    /** both non-JVM backends — the answer for a rule whose two platforms agree, which is most of
      * them, and the value this list carried implicitly when it had no per-platform half. */
    val OffJvm: Set[Platform]     = Set(Platform.ScalaJs, Platform.ScalaNative)
    val JsOnly: Set[Platform]     = Set(Platform.ScalaJs)
    val NativeOnly: Set[Platform] = Set(Platform.ScalaNative)

  final case class Violation(api: String, why: String, origin: Origin, kind: UsageKind, enclosing: SymId):
    def render: String = s"$api — $why  (${origin.javaPath}:${origin.line})"
    def report(check: String)(using program: Program): CheckReport.Finding =
      CheckReport.Finding(check, api, program.symbolOf(enclosing).map(_.fullName).getOrElse("?"),
        CheckReport.relativise(origin.javaPath), origin.line, s"$kind — $why")

  private def p(n: Int) = Some(DiffId(balticporter.catalog.Area.P, n))
  private def l(n: Int) = Some(DiffId(balticporter.catalog.Area.L, n))

  /** APIs a non-JVM backend cannot provide as they stand, each with the backends it is about.
    *
    * Read [[rulesFor]] rather than this list: a port's rule set is this filtered by its declared
    * targets, and the SIZE of that filtered set is what a run states. */
  val all: List[Rule] = List(
    Rule("java.lang.reflect.", "runtime reflection does not exist on Scala.js / Native",
      at = p(24)),
    Rule("java.lang.ClassLoader", "no class loading off the JVM"),
    // THE EIGHT RE-SCOPED RULES: each was written as a fact about "off the JVM" and is really a
    // fact about Scala.js alone — Scala Native 0.5.x implements every one of these for real.
    // MEMBER-LEVEL exceptions come FIRST: `find` takes the first match, so a specific rule must
    // precede its family or the exception never fires.
    Rule("java.net.IDN", "the class exists on both backends and is unusable on either without " +
      "ICU-like tables — a hand-written RFC 3492 Punycode replacement is what a reference port " +
      "shipped, which is direct evidence the JDK class is not viable cross-platform",
      at = p(14)),
    Rule("java.nio.channels.SocketChannel", "verified absent from the Native javalib, which has " +
      "real FileChannel/FileLock — this is the half of java.nio.channels the family prefix gets " +
      "WRONG for Native, and it does not exist on Scala.js at all",
      at = p(8)),
    Rule("java.nio.channels.ServerSocketChannel", "the same split as SocketChannel: Native's " +
      "java.nio.channels is FILE channels only",
      at = p(9)),
    Rule("java.net.", "networking is JVM-only on Scala.js — a browser has no raw sockets, and " +
      "fetch/XHR is the JS-native equivalent; Scala Native implements the family, IPv6 included",
      on = Rule.JsOnly, at = p(11)),
    Rule("java.nio.channels.", "the java.nio.channels package does not exist in the Scala.js " +
      "javalib; Scala Native has real FileChannel/FileLock (its SOCKET channels are a separate rule)",
      on = Rule.JsOnly, at = p(7)),
    Rule("java.nio.file.", "the java.nio.file filesystem API does not exist in the Scala.js " +
      "javalib; Scala Native implements Path/Files/WatchService/DirectoryStream and an spi package",
      on = Rule.JsOnly, at = p(10)),
    Rule("java.util.concurrent.", "Scala.js is single-threaded and ships no ExecutorService " +
      "family — the reference port's answer is one concurrency trait with a per-backend " +
      "implementation; Scala Native has the JSR-166 ports for real",
      on = Rule.JsOnly, at = p(15)),
    Rule("java.lang.Thread", "Scala.js's Thread is a private-constructor singleton — getId is " +
      "hardcoded, run() is empty and currentThread() is always the same instance, so `new Thread(r)` " +
      "does not even compile there; Scala Native has real OS threads",
      on = Rule.JsOnly, at = p(20)),
    Rule("java.lang.ProcessBuilder", "a browser cannot spawn a process; Scala Native has " +
      "Process/ProcessBuilder/ProcessHandle for real",
      on = Rule.JsOnly, at = p(21)),
    // A JUnit suite runs on the JVM alone; MUnit/utest is the cross-platform answer.
    Rule("org.junit.", "JUnit is JVM-only; cross-platform Scala needs MUnit/utest"),
    Rule("junit.framework.", "JUnit 3 is JVM-only; cross-platform Scala needs MUnit/utest"),
    // Hamcrest is JUnit's OTHER assertion vocabulary, arriving TRANSITIVELY, easy to miss.
    Rule("org.hamcrest.", "Hamcrest is JVM-only, and arrives TRANSITIVELY with junit; MUnit has no " +
      "matcher algebra, so each `assertThat(x, is(y))` has to become the assertion it means"),
    Rule("java.lang.System#getProperty", "Scala.js answers getProperty from a LINK-TIME-configured " +
      "properties table, never the live host's — it works, with values the build decides, which is a " +
      "caveat to read rather than a call to remove; Scala Native has the real thing",
      exactMember = true, on = Rule.JsOnly, at = p(33)),
    // the OPPOSITE shape: getenv silently returns empty/null rather than throwing.
    Rule("java.lang.System#getenv", "Scala.js returns an empty map from getenv() and null from " +
      "getenv(name), always and without failing — so a read silently answers 'not set' rather than " +
      "throwing; Scala Native reads the real environment. The OPPOSITE shape from getProperty",
      exactMember = true, on = Rule.JsOnly, at = p(34)),
    Rule("java.util.zip.", "no zlib in a browser without a JS dependency; Scala Native implements " +
      "the family and recently bug-fixed it for UTF-8",
      on = Rule.JsOnly, at = p(28)),
    // The one COLLECTION on this list whose answer is a REFUSAL, not a mapping (ENGINE-LIMITS M6).
    // HAND-WRITTEN, does NOT derive from ApiRows: a Refuse row produces no question by itself.
    Rule("java.util.WeakHashMap", "JS's WeakMap requires object keys and cannot enumerate, so no " +
      "faithful target exists there and a strong-referencing map would silently change what the " +
      "program retains; Scala Native has the real class",
      on = Rule.JsOnly, at = l(37)),
    // ServiceLoader is JVM-only twice over: reflective instantiation, and it reads a
    // META-INF/services FILE this engine does not emit. BOTH DEPENDENCY RULES now (DESIGN.md
    // §8.19's Depend verdict) since the API exists off the JVM via a cross-platform wrapper.
    Rule("java.util.ServiceLoader", "the class does not exist in the Scala.js javalib at all, so " +
      "this is a COMPILE-TIME resolution failure there rather than a linker one — Scala.js reaches " +
      "providers only by REGISTRATION, which is what the wrapper's build-time codegen supplies from " +
      "the same META-INF/services descriptor `PortManifest.serviceProviders` already ships",
      on = Rule.JsOnly, at = p(25)),
    Rule("java.util.ServiceLoader", "Scala Native has it FOR REAL, resolved at LINK time — but its " +
      "`load` is a toolchain INTRINSIC that only accepts a literal classOf, so no Class-taking " +
      "wrapper can delegate to it and nativeConfig.withServiceProviders enlistment serves only " +
      "direct load(classOf[Concrete]) sites, which a ported library's generic lookup is not. Native " +
      "therefore resolves providers by REGISTRATION exactly as Scala.js does, off the same " +
      "descriptor (ENGINE-LIMITS P5), and a package rename moves both that file's NAME and its " +
      "CONTENTS",
      on = Rule.NativeOnly, at = p(25)),
    Rule("javax.", "the javax.* stack is JVM-only", at = p(29)),
    Rule("java.lang.Class#forName", "runtime class lookup by name is JVM-only", exactMember = true, at = p(24)),
    Rule("java.lang.Class#newInstance", "reflective instantiation is JVM-only", exactMember = true, at = p(24)),
    Rule("java.lang.Class#getDeclaredFields", "reflective member access is JVM-only", exactMember = true, at = p(24)),
    Rule("java.lang.Class#getDeclaredMethods", "reflective member access is JVM-only", exactMember = true, at = p(24)),
    Rule("java.lang.Class#getDeclaredConstructor", "reflective member access is JVM-only", exactMember = true, at = p(24)),
    Rule("java.lang.Class#getFields", "reflective member access is JVM-only", exactMember = true, at = p(24)),
    Rule("java.lang.Class#getMethods", "reflective member access is JVM-only", exactMember = true, at = p(24)),
    Rule("java.lang.Class#getConstructor", "reflective member access is JVM-only", exactMember = true, at = p(24)),
    // the SINGULAR readers — same family as their plural twins; a gap here let Remediator offer to
    // forward a reflective call while reporting the port improved.
    Rule("java.lang.Class#getDeclaredField", "reflective member access is JVM-only", exactMember = true, at = p(24)),
    Rule("java.lang.Class#getDeclaredMethod", "reflective member access is JVM-only", exactMember = true, at = p(24)),
    Rule("java.lang.Class#getField", "reflective member access is JVM-only", exactMember = true, at = p(24)),
    Rule("java.lang.Class#getMethod", "reflective member access is JVM-only", exactMember = true, at = p(24)),
    // ---- TIME, TEXT AND LOCALE — the refusals. The whole area had ZERO rules. ----
    // Most of the area is a DEPENDENCY rather than a refusal (scala-java-time, scala-java-locales),
    // living on the dependency-coverage lane instead. What is HERE is the residue: classes with no
    // implementation in any surveyed source tree, platform javalib or third-party artifact.
    Rule("java.text.MessageFormat", "no implementation anywhere surveyed — not in either core " +
      "javalib, not in the locales artifact. The only path is a hand-written shim over the format " +
      "subset one library actually uses, which is §1(c) knowledge about that library",
      at = l(68)),
    Rule("java.text.Collator", "locale-aware collation has no cross-platform Scala answer today; " +
      "both backends leave you lexicographic-by-codepoint ordering and nothing else",
      at = l(69)),
    Rule("java.text.BreakIterator", "no implementation in any surveyed source tree — the same " +
      "answer as Collator, for the same reason",
      at = l(70)),
    Rule("java.util.Calendar", "no cross-platform implementation exists; java.time is the " +
      "replacement and it is a DEPENDENCY rather than a rewrite of this class",
      at = l(72)),
    Rule("java.util.GregorianCalendar", "the same answer as its superclass — no cross-platform " +
      "implementation exists",
      at = l(72)),
    Rule("java.util.TimeZone", "absent from both core javalibs AND from the locales artifact, whose " +
      "java/util tree holds Currency alone. `java.time.ZoneId` is the cross-platform replacement " +
      "and it is not a TimeZone shim: rewrite the CALL SITE where it can be rewritten, and shim " +
      "only the surface a caller strictly needs",
      at = l(71)),
    // ---- TIME, TEXT AND LOCALE — the DEPENDENCIES. ----
    // The API exists off the JVM, in an artifact the build must add, so Verdict.Depend routes
    // these to dependency-coverage instead — only the finding kind differs from the refusals above.
    Rule("java.time.ZoneId#of", "zone lookup is BY STRING, so the whole IANA database ships unless " +
      "the build generates a trimmed one — this is a BUILD-TIME action (`sbt-tzdb`) and not only a " +
      "library add, and the untrimmed database is a measurable bundle cost",
      exactMember = true, at = l(63)),
    Rule("java.time.", "Scala.js's working java.time classes live only in an unpublished " +
      "ext-dummies module application code cannot depend on, and Scala Native has no java.time " +
      "package at all — its own docs point at this artifact. Covers java.time.format and " +
      "java.time.temporal at no separate cost",
      at = l(60)),
    Rule("java.util.Locale", "the same unpublished-stub story on Scala.js (its default is ROOT) and " +
      "absent on Native. The replacement is CLDR-backed at a NEWER CLDR than the JDK's, so " +
      "locale-derived STRINGS can differ from the JVM's even where the API matches",
      at = l(65)),
    Rule("java.text.DecimalFormat", "an ext-dummies placeholder with almost no surface on Scala.js " +
      "and absent on Native; the artifact covers the family, setRoundingMode included",
      at = l(66)),
    Rule("java.text.NumberFormat", "the same artifact as DecimalFormat, which it is the supertype of",
      at = l(66)),
    Rule("java.text.DecimalFormatSymbols", "the same artifact as DecimalFormat", at = l(66)),
    Rule("java.text.SimpleDateFormat", "an ext-dummies placeholder on Scala.js and absent on Native",
      at = l(67)),
    Rule("java.text.DateFormat", "the same artifact as SimpleDateFormat", at = l(67)),
    Rule("java.text.DateFormatSymbols", "the same artifact as SimpleDateFormat", at = l(67)),
    Rule("java.util.Currency", "supplied by the locales artifact's java/util tree, which holds this " +
      "class and nothing else", at = l(74)),
    // ---- and the two families spec-7 found with NO rule at all ----
    Rule("java.security.MessageDigest", "absent from BOTH core javalibs — the exception types " +
      "(NoSuchAlgorithmException) exist so a catch site still compiles while nothing throws them " +
      "from a working digest, which is the quietest possible shape for this gap",
      at = p(26)),
    Rule("java.security.SecureRandom", "absent from Scala.js's core javalib. UNSTATED for Native — " +
      "the survey did not confirm its absence there, so this rule claims Scala.js alone",
      on = Rule.JsOnly, at = p(27)),
    Rule("java.lang.ref.WeakReference", "removed from Scala.js's core javalib in 1.6.0 — its " +
      "maintainers took OUT a stub that silently held STRONG references rather than leave a " +
      "wrong-but-compiling one, which is CLAUDE.md §3's argument made by a platform team about " +
      "their own stdlib. The opt-in artifact implements it over ECMAScript 2021's WeakRef; Scala " +
      "Native's is GC-integrated with a dedicated handler thread",
      on = Rule.JsOnly, at = p(31)),
    Rule("java.lang.ref.ReferenceQueue", "the same artifact as WeakReference, which it is useless " +
      "without", on = Rule.JsOnly, at = p(31)),
    Rule("java.lang.ref.Cleaner", "expected to be the same opt-in artifact as WeakReference. " +
      "UNSTATED on both non-JVM platforms — the survey did not reach this class and the verdict is " +
      "inherited by expectation rather than by measurement",
      on = Rule.JsOnly, at = p(32)),
  )

  /** THE §1(b) PARAMETER APPLIED: the UNPORTABILITY rules any of `targets` asks about. An empty
    * target set is the no-op but NOT the default a port gets (`PortManifest.targets` defaults to
    * all three, so no baseline moves by acquiring the parameter). Complement is
    * [[dependencyRulesFor]]; the two PARTITION [[all]]. */
  def rulesFor(targets: Set[Platform], overrides: Overrides = Map.empty): List[Rule] =
    all.filter(r => stillAsks(r, targets, overrides) && !isDependency(r, targets, overrides))

  /** the rules whose answer is an ARTIFACT rather than a removal — feeds `DependencyCheck`, never
    * `portability(*)`, since a `Verdict.Depend` finding is unanswerable as an unportability (the
    * reader would be told to remove a call one `libraryDependencies` line makes correct). */
  def dependencyRulesFor(targets: Set[Platform], overrides: Overrides = Map.empty): List[Rule] =
    all.filter(r => stillAsks(r, targets, overrides) && isDependency(r, targets, overrides))

  /** the map a port may override a row's RECOMMENDATION with — never its availability. */
  type Overrides = Map[DiffId, Map[Platform, balticporter.catalog.Verdict]]

  /** Does this rule still ask a question of any declared target, AFTER the port's own overrides?
    * Without an override this is exactly [[Rule.asks]]. WITH one, a `verdictOverrides` entry
    * saying `Keep` is the port accepting the JDK type on that backend, so the rule leaves BOTH
    * lanes. A rule with no cited row always asks — nothing to override. */
  private def stillAsks(r: Rule, targets: Set[Platform], overrides: Overrides): Boolean =
    r.asks(targets) && rowOf(r).forall { row =>
      r.on.intersect(targets).exists(p => row.verdictOn(p, overrides).actionable)
    }

  /** Is every platform this rule still asks about answered by an ARTIFACT? ALL rather than ANY:
    * `DependencyCoverageSpec` asserts no rule in the list is MIXED. A rule with no cited row is
    * never a dependency. Read THROUGH the overrides, so a port shipping its own shim changes the
    * verdict here rather than in a second, potentially disagreeing filter. */
  private def isDependency(r: Rule, targets: Set[Platform], overrides: Overrides): Boolean =
    rowOf(r).exists { row =>
      val asked = r.on.intersect(targets).filter(p => row.verdictOn(p, overrides).actionable)
      asked.nonEmpty && asked.forall(p => row.verdictOn(p, overrides).dependency.isDefined)
    }

  /** THE CATALOG ROW behind a rule, where the survey has one. */
  def rowOf(r: Rule): Option[ApiRow] = r.at.flatMap(ApiRows.byId.get)

  /** Every violation the PROGRAM references. Recorded as `portability(all)`, separately from
    * `inEmittedCode` below — the two answer different questions (referenced vs. SHIPPED). */
  def check(program: Program, rules: List[Rule] = all): List[Violation] =
    checkAll(program, rules)

  /** Does `rule` name `fullName`? THE ONE MATCHER, cutting at a separator — a bare `startsWith` is
    * §4.56's hazard (`java.lang.Thread` covered `java.lang.ThreadLocal`, which Scala.js implements). */
  def names(rule: Rule, fullName: String): Boolean =
    val prefix = if rule.api.nonEmpty && RuleScope.isBoundary(rule.api.last) then rule.api.init else rule.api
    RuleScope.covers(fullName, prefix)

  /** The RULE FILTER, over [[ExternalUsage.all]] (every referenced symbol's `owner#name` and
    * usages), which answers more questions than [[all]] asks. Order-preserving on purpose:
    * `ExternalUsage.all` iterates in the same order this loop did, so `portability(all)`'s
    * baseline stays byte-identical rather than merely equal in count. */
  private def checkAll(program: Program, rules: List[Rule]): List[Violation] =
    ExternalUsage.all(program).flatMap { row =>
      val hit = rules.find { r =>
        if r.exactMember then row.member.contains(r.api)
        else names(r, row.fullName)
      }
      hit match
        case scala.None => Nil
        case Some(r) =>
          val api = if r.exactMember then row.member.getOrElse(r.api) else row.fullName
          row.usages.map(u => Violation(api, r.why, u.site.origin, u.kind, u.enclosing))
    }

  /** the top-level type a definition belongs to — used to attribute a violation to the unit that
    * will (or will not) be emitted. */
  def owningType(program: Program, from: SymId): Option[SymId] =
    def climb(s: SymId, fuel: Int): Option[SymId] =
      if s == SymId.None || fuel == 0 then scala.None
      else
        program.symbolOf(s) match
          case scala.None => scala.None
          case Some(sym) =>
            val isType = program.definitionOf(sym.id).exists(_.isInstanceOf[Tree.ClassDef])
            if isType && (sym.owner == SymId.None || !program.symbolOf(sym.owner).exists(_ => true)) then Some(sym.id)
            else if isType then climb(sym.owner, fuel - 1).orElse(Some(sym.id))
            else climb(sym.owner, fuel - 1)
    climb(from, 64)

  /** Violations occurring in code that is actually EMITTED — a violation inside a substituted
    * (dropped) type would overstate the problem. @param isExcluded a type this run does NOT ship —
    * either DROPPED (`Substitutions.dropTypes`) or merely RESOLVED against (`resolutionRoots`, a
    * foreign unit). The second was once missing and the misattribution was total (Ashley: 67
    * sites, none its own). */
  def inEmittedCode(program: Program, violations: List[Violation], isExcluded: SymId => Boolean): List[Violation] =
    violations.filterNot(v => owningType(program, v.enclosing).exists(isExcluded))

  /** INJECTED replacements are copied verbatim, so the symbol table cannot see them; scan their
    * text for the same rules instead. Coarse, but closes the hole: a hand-written shim quietly
    * reintroducing the API the substitution was meant to remove. */
  def inInjectedSource(fileName: String, source: String, rules: List[Rule] = all): List[InjectedViolation] =
    // comments discuss the very APIs being removed (a swap-point note naming `newInstance` is not
    // a use of it), so scan code lines only — otherwise the count is noise.
    val code = source.linesIterator
      .map(_.trim)
      .filterNot(l => l.startsWith("//") || l.startsWith("*") || l.startsWith("/*"))
      .toList
    rules.flatMap { r =>
      val needle = if r.exactMember then r.api.substring(r.api.indexOf('#') + 1) else r.api
      val n = code.count(_.contains(needle))
      if n == 0 then Nil else List(InjectedViolation(fileName, needle, n, r.why))
    }

  /** One injected file's use of an API its substitution was meant to remove. The COUNT is carried
    * in the `line` column and left out of the finding id, so a second use is not a new finding. */
  final case class InjectedViolation(file: String, api: String, count: Int, why: String):
    def render: String = s"$file: $api × $count — $why"
    def report: CheckReport.Finding =
      CheckReport.Finding("portability(injected)", api, file, file, count, why)

  /** grouped one-line summary, most-referenced first, followed by the remediation block when the
    * caller computed one (§4.45 — [[Remediator]] states the mechanism and, where verifiable, the
    * literal manifest line). `fixes` is a PARAMETER, computed and passed together by `PortRun`, so
    * there is no hidden state to go stale. */
  def summary(violations: List[Violation], fixes: List[Remediator.Suggestion] = Nil): String =
    if violations.isEmpty then "  none"
    else
      val head = violations.groupBy(_.api).toList.sortBy(-_._2.size)
        .map((api, vs) => s"  $api: ${vs.size} site(s) — ${vs.head.why}")
        .mkString("\n")
      if fixes.isEmpty then head
      else head + "\n  REMEDIATION — what to paste, and what was only observed:\n" + Remediator.summary(fixes)
