package balticporter.tir

import balticporter.catalog.{ApiRow, ApiRows, DiffId, Platform}

/** Which JDK APIs the port still depends on that a cross-platform target cannot provide.
  *
  * sge targets Scala Native and Scala.js as well as the JVM, and that is WHY several libGDX
  * facilities were removed rather than ported: `utils.reflect` and the `Class`-driven `Json`
  * serializer have no counterpart off the JVM. A port can look finished — compile clean, tests
  * green — and still be JVM-only.
  *
  * Compiling with `--js` does NOT catch this: Scala.js type-checks against the JDK's signatures,
  * so `java.lang.reflect.Field` and `Class.forName` compile without complaint. The failure appears
  * only when the Scala.js LINKER runs ("Referring to non-existent class java.lang.reflect.Field"),
  * and linking reports only what is reachable from an entry point — which a library does not have.
  *
  * This check is the pre-flight version, and is strictly more thorough for a library: the TIR
  * already knows every external symbol referenced anywhere in the program, so violations are
  * enumerated exactly, with source locations, for ALL code rather than the reachable subset. The
  * linker remains the end-to-end authority once an application entry point exists.
  *
  * Deliberately narrow: it encodes the rules we have actually established, rather than pretending
  * to model the whole of Scala.js/Native JDK support. A rule earns its place by being a known
  * removal reason.
  *
  * ==This is CLAUDE.md §1(b), and the parameter is the TARGET SET==
  * The mechanism — match a rule against [[ExternalUsage]], report every site — is the same for every
  * library and does not change. What differs is WHICH rules apply, and the answer is a fact about
  * the BACKENDS a module is ported for: `java.nio.file` is absent on Scala.js and fully implemented
  * on Scala Native, so one shared verdict is wrong for one of them whichever way it is written. Nine
  * families disagree that way and SEVEN of this list's rules were measurably too broad for Scala
  * Native 0.5.x — `java.nio.channels.`, `java.nio.file.`, `java.util.concurrent.`, `java.lang.Thread`,
  * `java.lang.ProcessBuilder`, `java.util.zip.`, `java.net.` — with `java.lang.System#getProperty` an
  * eighth found the same way.
  *
  * So every [[Rule]] carries the set of platforms it is a refusal FOR, and [[rulesFor]] selects the
  * ones any declared target asks about. `PortManifest.targets` defaults to all three, which is
  * exactly the behaviour this list had when there was one list: a port that declares nothing is
  * checked as it was, and the re-scoping is observable only to a port that says `targets = [jvm,
  * native]` and takes the drop as its own decision.
  *
  * ==The FACT lives in the catalog; this list is the MATCHER==
  * Each rule cites the `balticporter.catalog.ApiRows` row that holds the availability claim and its
  * version anchor, and `PortabilityRuleMatrixSpec` holds the two together: a rule may not claim a
  * platform on which its own cited row says `Keep`. That is the direction that matters — the
  * registry cannot silently drift away from a rule that keeps firing — and it deliberately does not
  * generate the list, because a generated list would drop every rule the survey has no row for
  * (`org.junit.`, `org.hamcrest.`, `java.lang.ClassLoader`, the exact `Class#…` readers) and that is
  * a lane reset rather than a re-scoping.
  */
object PortabilityCheck:

  /** @param api         a prefix (`java.nio.file.`) or an exact `owner#member`
    * @param why         one sentence, and it is what the reader acts on
    * @param exactMember which of the two `api` is
    * @param on          THE PLATFORMS THIS RULE IS A REFUSAL FOR. A rule that applied to a port
    *                    whichever backends it targets is a rule that tells a JVM+Native port to
    *                    remove seven categories of API that work perfectly on both.
    * @param at          the catalog row holding the availability FACT and its version anchor.
    *                    `None` only where the javalib survey has no row — a non-JDK dependency
    *                    (`org.junit.`) or a member whose family row cannot express it
    */
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
    val OffJvm: Set[Platform] = Set(Platform.ScalaJs, Platform.ScalaNative)
    val JsOnly: Set[Platform] = Set(Platform.ScalaJs)

  final case class Violation(api: String, why: String, origin: Origin, kind: UsageKind, enclosing: SymId):
    def render: String = s"$api — $why  (${origin.javaPath}:${origin.line})"
    def report(check: String)(using program: Program): CheckReport.Finding =
      CheckReport.Finding(check, api, program.symbolOf(enclosing).map(_.fullName).getOrElse("?"),
        CheckReport.relativise(origin.javaPath), origin.line, s"$kind — $why")

  private def p(n: Int) = Some(DiffId(balticporter.catalog.Area.P, n))

  /** APIs a non-JVM backend cannot provide as they stand, each with the backends it is about.
    *
    * Read [[rulesFor]] rather than this list: a port's rule set is this filtered by its declared
    * targets, and the SIZE of that filtered set is what a run states. */
  val all: List[Rule] = List(
    Rule("java.lang.reflect.", "runtime reflection does not exist on Scala.js / Native",
      at = p(24)),
    Rule("java.lang.ClassLoader", "no class loading off the JVM"),
    // Reflection was the only thing this checked until 2026-07-28, so it reported ZERO for a
    // corpus containing an HTTP client, a thread pool and NIO channels. A check that reports zero
    // is only as good as its coverage — the same failure as the annotations one, and found the
    // same way: by asking why a known-unportable class was not being flagged.
    //
    // THE EIGHT RE-SCOPED RULES. Each was written as a fact about "off the JVM" and is a fact about
    // Scala.js alone: Scala Native 0.5.x implements every one of these families for real —
    // `Path`/`Files`/`WatchService`, 60+ `java.util.concurrent` files ported from JSR-166, real OS
    // threads, `Process`/`ProcessBuilder`/`ProcessHandle`, a `java.util.zip` recently bug-fixed for
    // UTF-8, sockets with IPv6, and a `System.getenv` that reads the real environment. A JVM+Native
    // port was being told to remove seven categories of API that work on both of its targets, which
    // is the over-conservative rule §1's balance section warns about, and the fix is the target set
    // rather than a second list.
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
    // A ported TEST SUITE is the project's only behavioural gate, and a JUnit one runs on the JVM
    // alone — neither Scala.js nor Native has JUnit. Emitting java's tests as JUnit-in-Scala
    // therefore produces a gate that cannot execute on the platforms the port EXISTS for, while
    // looking like full test coverage. Cross-platform Scala wants MUnit (or utest); converting is
    // structural, not a rename, because a `@Test` method becomes a `test("…") { … }` block.
    Rule("org.junit.", "JUnit is JVM-only; cross-platform Scala needs MUnit/utest"),
    Rule("junit.framework.", "JUnit 3 is JVM-only; cross-platform Scala needs MUnit/utest"),
    // Hamcrest is JUnit's OTHER assertion vocabulary — `assertThat(x, is(equalTo(y)))` — and it
    // arrives transitively with junit rather than as a declared dependency, which is exactly why
    // it was missed. `TestFrameworkTransform` deliberately does not translate it (MUnit has no
    // matcher algebra to map a matcher onto) and PRINTS what it left behind; nothing RECORDED it,
    // because the two rules above name the framework and not the vocabulary reached through it.
    // A suite could therefore be 100% hamcrest and every portability lane read zero — the same
    // "a check reporting zero is only as good as its coverage" failure as the reflection-only
    // list above, found the same way: by asking why a known-unportable package was not flagged.
    Rule("org.hamcrest.", "Hamcrest is JVM-only, and arrives TRANSITIVELY with junit; MUnit has no " +
      "matcher algebra, so each `assertThat(x, is(y))` has to become the assertion it means"),
    // The EIGHTH, and its `why` was not merely too broad but inaccurate: Scala.js DOES implement
    // `getProperty`, against a table populated at LINK time from the build's own options. It
    // compiles, it runs, and it returns whatever the port configured — never the live host's value.
    // Saying "JVM-only" of that sends a reader to delete a call that works.
    Rule("java.lang.System#getProperty", "Scala.js answers getProperty from a LINK-TIME-configured " +
      "properties table, never the live host's — it works, with values the build decides, which is a " +
      "caveat to read rather than a call to remove; Scala Native has the real thing",
      exactMember = true, on = Rule.JsOnly, at = p(33)),
    Rule("java.util.zip.", "no zlib in a browser without a JS dependency; Scala Native implements " +
      "the family and recently bug-fixed it for UTF-8",
      on = Rule.JsOnly, at = p(28)),
    // `ServiceLoader` is JVM-only twice over, and the SECOND reason is the one nothing else can
    // see. It is reflective instantiation, so Scala.js / Native cannot provide it — and it reads a
    // `META-INF/services` FILE, which this engine does not emit: the pipeline produces `.scala` and
    // nothing else, so the providers are constructed from OUTSIDE the closure (CLAUDE.md §1's
    // "a class a FRAMEWORK instantiates has no caller to change") off a resource no phase carries.
    // With that file absent the loader finds zero providers, the registration silently no-ops, and
    // there is no compile error, no other check count and no finding. This rule is not the fix —
    // the port has to ship the file by hand, and a rename moves both its NAME and its CONTENTS —
    // but it is what makes the dependence a NUMBER rather than a thing someone has to remember.
    Rule("java.util.ServiceLoader", "reflective provider lookup is JVM-only, AND it reads a " +
      "META-INF/services file no phase emits or renames — absent, it finds zero providers and " +
      "the registration silently does nothing", at = p(25)),
    Rule("javax.", "the javax.* stack is JVM-only", at = p(29)),
    Rule("java.lang.Class#forName", "runtime class lookup by name is JVM-only", exactMember = true, at = p(24)),
    Rule("java.lang.Class#newInstance", "reflective instantiation is JVM-only", exactMember = true, at = p(24)),
    Rule("java.lang.Class#getDeclaredFields", "reflective member access is JVM-only", exactMember = true, at = p(24)),
    Rule("java.lang.Class#getDeclaredMethods", "reflective member access is JVM-only", exactMember = true, at = p(24)),
    Rule("java.lang.Class#getDeclaredConstructor", "reflective member access is JVM-only", exactMember = true, at = p(24)),
    Rule("java.lang.Class#getFields", "reflective member access is JVM-only", exactMember = true, at = p(24)),
    Rule("java.lang.Class#getMethods", "reflective member access is JVM-only", exactMember = true, at = p(24)),
    Rule("java.lang.Class#getConstructor", "reflective member access is JVM-only", exactMember = true, at = p(24)),
    // The SINGULAR readers, added the moment the rules above started firing at all. They are the
    // same family as their plural twins — each returns a `java.lang.reflect.*` — and leaving them
    // out was invisible while no member rule fired. It stopped being invisible immediately:
    // `Remediator` reads this list to decide which members of a static wrapper may be inlined, and
    // with no rule for `getDeclaredField` it offered to forward one, which would have moved a
    // reflective call from the wrapper to every call site while reporting the port improved.
    // A gap in a rule list is not neutral once something else reasons from it.
    Rule("java.lang.Class#getDeclaredField", "reflective member access is JVM-only", exactMember = true, at = p(24)),
    Rule("java.lang.Class#getDeclaredMethod", "reflective member access is JVM-only", exactMember = true, at = p(24)),
    Rule("java.lang.Class#getField", "reflective member access is JVM-only", exactMember = true, at = p(24)),
    Rule("java.lang.Class#getMethod", "reflective member access is JVM-only", exactMember = true, at = p(24)),
  )

  /** THE §1(b) PARAMETER APPLIED: the rules any of `targets` asks about.
    *
    * An empty target set selects nothing and makes the check a no-op, which is §1(b)'s own rule for
    * a parameter's empty value — but it is deliberately NOT the default any port gets. See
    * `PortManifest.targets`: the default is all three platforms, i.e. what this check did before it
    * had a parameter, so no port's baseline moves by acquiring one.
    */
  def rulesFor(targets: Set[Platform]): List[Rule] = all.filter(_.asks(targets))

  /** THE CATALOG ROW behind a rule, where the survey has one. */
  def rowOf(r: Rule): Option[ApiRow] = r.at.flatMap(ApiRows.byId.get)

  /** Every violation the PROGRAM references. Recorded by the orchestrator as `portability(all)`,
    * separately from `inEmittedCode` below: the two numbers answer different questions (what the
    * program references vs. what the SHIPPED code references) and a run that reports only one of
    * them cannot show a substitution moving a violation out of the deliverable. */
  def check(program: Program, rules: List[Rule] = all): List[Violation] =
    checkAll(program, rules)

  /** Does `rule` name `fullName`? THE ONE MATCHER, and it cuts at a separator.
    *
    * A bare `startsWith` is §4.56's own hazard — `java.foo` covering `java.foobar` — and it was
    * live here rather than hypothetical: `java.lang.Thread` covered `java.lang.ThreadLocal`, which
    * Scala.js implements. The existing prefixes end in `.` and a `RuleScope.covers` call takes the
    * separator from the NAME, so the trailing one is stripped before asking: `java.nio.file` covers
    * `java.nio.file.Path` and does not cover a `java.nio.filesystem` nobody has written yet. */
  def names(rule: Rule, fullName: String): Boolean =
    val prefix = if rule.api.nonEmpty && RuleScope.isBoundary(rule.api.last) then rule.api.init else rule.api
    RuleScope.covers(fullName, prefix)

  /** The RULE FILTER, over an enumeration this object no longer owns.
    *
    * The walk it used to perform inline — every referenced symbol, its `owner#name` (a MEMBER is
    * identified that way, since an external member's own `fullName` is an interning key), and every
    * recorded usage — is [[ExternalUsage.all]], because it answers more questions than [[all]]
    * asks and throwing it away was the reason no artifact of a port's external dependencies
    * existed anywhere.
    *
    * This sentence used to say *"these 34 rules"*. It was never 34: the list was 21 when the number
    * was last edited beside it and is [[all]]`.size` now, and nothing computed either. The
    * count is DELETED here rather than corrected — a corrected constant is a constant that goes
    * stale again — and the run states the derived one instead (`PortRun`'s PORTABILITY line). This
    * is `PROGRESS.md` §12.3's rule ("a residue number restated in prose beside the artifact that
    * computes it is a number that goes stale silently") reproduced inside the engine's own source,
    * and it escaped: the commit subjects `9eba29b3` (*"rules 34 -> 35"*) and `0aa10cd6`
    * (*"rules 35 -> 36"*) both quote this phantom while the list they edited went 25 -> 26 -> 27.
    * Those two subjects are WRONG and cannot be regenerated; do not trust a rule count from the
    * git log.
    *
    * The lift is order-preserving on purpose: [[ExternalUsage.all]] iterates
    * `program.referenced.toList` and each symbol's usages in exactly the order this loop did, so
    * `portability(all)`'s promoted baseline in thirteen lanes is byte-identical rather than
    * merely equal in count. */
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

  /** Violations occurring in code that is actually EMITTED. A violation inside a substituted type
    * is not shipped — that type's declaration is dropped — so counting it would overstate the
    * problem; the point of the number is what the FINAL code depends on. */
  /** @param isExcluded
    *   a type this run does NOT ship. Two disjoint reasons, and both must be here or the number
    *   describes something other than the deliverable:
    *
    *   - the port DROPPED it (`Substitutions.dropTypes`) — the original reason for this filter;
    *   - the run merely RESOLVED against it (`FrontendConfig.resolutionRoots`) — another module's
    *     unit, which that module reports and this one must not.
    *
    *   The second was missing and the misattribution was total, not marginal: Ashley, a 21-file
    *   dependent of libGDX core, reported **67 portability sites of which none were Ashley's**.
    *   Every one belonged to the 605 units it only resolved against. Scaled across sge's 17
    *   extension modules, each would have re-reported the base's entire finding set as its own.
    */
  def inEmittedCode(program: Program, violations: List[Violation], isExcluded: SymId => Boolean): List[Violation] =
    violations.filterNot(v => owningType(program, v.enclosing).exists(isExcluded))

  /** INJECTED replacements never pass through the TIR — they are copied verbatim — so the symbol
    * table cannot see them. They are still shipped, so scan their text for the same rules. Coarse
    * by nature (no symbols to resolve), but it closes the hole that matters: a hand-written shim
    * that quietly reintroduces the very API the substitution was meant to remove.
    */
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

  /** One injected file's use of an API its substitution was meant to remove.
    *
    * The COUNT is deliberately carried in the `line` column and left out of the finding id: a shim
    * gaining a second use of an API it already used is the same finding, and should not read as one
    * fixed plus one new. */
  final case class InjectedViolation(file: String, api: String, count: Int, why: String):
    def render: String = s"$file: $api × $count — $why"
    def report: CheckReport.Finding =
      CheckReport.Finding("portability(injected)", api, file, file, count, why)

  /** grouped one-line summary, most-referenced first, followed by the remediation block when the
    * caller computed one. A finding an agent can act on beats a finding it must first investigate
    * (CLAUDE.md §4.45) — [[Remediator]] states the mechanism and, where the precondition is
    * verifiable, the literal manifest line.
    *
    * `fixes` is a PARAMETER. It used to be a `private var` written by [[inEmittedCode]] and read
    * back here, keyed to the exact violation list, because `summary` has no `Program` and there was
    * no orchestrator to hold the pair. There is one now: `PortRun` computes both and passes them
    * together, so there is no hidden state to go stale and no ordering requirement between two
    * calls. */
  def summary(violations: List[Violation], fixes: List[Remediator.Suggestion] = Nil): String =
    if violations.isEmpty then "  none"
    else
      val head = violations.groupBy(_.api).toList.sortBy(-_._2.size)
        .map((api, vs) => s"  $api: ${vs.size} site(s) — ${vs.head.why}")
        .mkString("\n")
      if fixes.isEmpty then head
      else head + "\n  REMEDIATION — what to paste, and what was only observed:\n" + Remediator.summary(fixes)
