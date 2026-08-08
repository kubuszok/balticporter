package balticporter.tir

import balticporter.catalog.{ApiRow, ApiRows, DiffId, FixKind, Platform}

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
object PortabilityCheck extends RemedySource:

  /** THE ONE REMEDY THIS CHECK CAN CARRY OUT — see [[AcceptJvmOnly]]. Declared here rather than on a
    * phase because this lane's producer IS a check: a plain object the orchestrator calls, which is
    * exactly the half of the residue population `RemedySource` exists beside `Phase` for. */
  def remedies: List[Remedy] = List(AcceptJvmOnly)

  /** `accept-jvm-only` — *this location is JVM-only and I know it; stop reporting it.*
    *
    * ==What it does, and what it deliberately does not==
    * It changes NO tree. It moves a row out of `portability(emitted)` and into
    * `remediation(resolved)`, which is why `emissionAffecting` is `false` — the one remedy so far
    * that `DESIGN.md` §8.16 predicted would belong in §1.5's not-inherited column if any ever did.
    * (The FIELD is what states that; `PortManifest.resolutions` is still inherited as a whole,
    * because it holds the other three as well.)
    *
    * ==The CONSISTENCY test, and the finding it produced==
    * A port's `PortManifest.targets` is a statement about the MODULE: *this port is built for these
    * backends*. Accepting a JVM-only API at a location is a statement about the same module in the
    * opposite direction, so the two cannot both be true — and the honest answers are the two knobs
    * that already exist: narrow `targets`, or state a `verdictOverrides` entry for the API this port
    * ships its own answer for. So a selection on a port whose `targets` include Scala.js or Scala
    * Native is REPORTED as a contradiction and never applied.
    *
    * '''That test makes the apply arm unreachable, and it is a RESULT rather than an oversight.'''
    * `rulesFor` filters the rule list by the declared targets and NO rule in it asks about the JVM,
    * so a port with `targets = Set(Jvm)` has zero portability findings and nothing to accept, while
    * any other port contradicts itself by accepting. Measured on the corpus: 0 of the rules name
    * `Platform.Jvm`. What the remedy is therefore FOR is the contradiction itself — a port reaching
    * for "accept" is told, with the two real knobs named, instead of silently ignoring the row,
    * which is what every port does today. `ENGINE-LIMITS.md` P6 carries the number and the reasoning.
    */
  val AcceptJvmOnly: Remedy = Remedy(
    id = "accept-jvm-only", lane = "portability(emitted)", kind = Remedy.AnyKind,
    emissionAffecting = false, fix = FixKind.Universal, subject = Remedy.Subject.OwnedMember,
    what = "accept this location as JVM-only: the row moves to `remediation(resolved)` and no tree " +
      "changes — refused, with both real knobs named, on a port whose `targets` claim Scala.js or " +
      "Scala Native")

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
    // …and the MEMBER-LEVEL exceptions to two of them, which is why they come FIRST: a re-scoped
    // prefix says "Native is fine here" and Native is not fine at every member under it. `find`
    // takes the first match, so the specific rule has to precede the family or its `why` never
    // reaches a reader targeting both.
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
    // …and its SIBLING, which had no rule at all and is the OPPOSITE shape. The one member the old
    // "system properties are JVM-only" sentence was true of was the one nothing checked: Scala.js's
    // `getenv()` is unconditionally an empty map and `getenv(name)` unconditionally null, while
    // Scala Native reads the real environment. A read that silently returns nothing is exactly the
    // §4.4 class of defect — no exception, no count, a plausible wrong answer.
    Rule("java.lang.System#getenv", "Scala.js returns an empty map from getenv() and null from " +
      "getenv(name), always and without failing — so a read silently answers 'not set' rather than " +
      "throwing; Scala Native reads the real environment. The OPPOSITE shape from getProperty",
      exactMember = true, on = Rule.JsOnly, at = p(34)),
    Rule("java.util.zip.", "no zlib in a browser without a JS dependency; Scala Native implements " +
      "the family and recently bug-fixed it for UTF-8",
      on = Rule.JsOnly, at = p(28)),
    // The one COLLECTION on this list, and the one row of the platform survey whose answer is a
    // REFUSAL rather than a mapping. Every other absent `java.util` type has a Scala counterpart
    // with the same meaning and is retyped by `CollectionsTransform`; this one has none anywhere.
    // JS's own `WeakMap` requires OBJECT keys and cannot be enumerated, so it cannot back a `Map`
    // — and degrading to a strong-referencing map would compile, pass, and silently change what the
    // program retains, which is exactly what `Collections.unmodifiableXxx` was correctly refused
    // for (ENGINE-LIMITS M6). Native has the real thing, so the rule is JS-only and a JVM+Native
    // port is told nothing.
    //
    // NOTE the rule list is HAND-WRITTEN and does NOT derive from `ApiRows`: a survey row saying
    // `Refuse` on a platform produces no question by itself, which is why this row sat stated and
    // unasked. The two are joined by `at`, and that is the whole of the join.
    Rule("java.util.WeakHashMap", "JS's WeakMap requires object keys and cannot enumerate, so no " +
      "faithful target exists there and a strong-referencing map would silently change what the " +
      "program retains; Scala Native has the real class",
      on = Rule.JsOnly, at = l(37)),
    // `ServiceLoader` is JVM-only twice over, and the SECOND reason is the one nothing else can
    // see. It is reflective instantiation, so Scala.js / Native cannot provide it — and it reads a
    // `META-INF/services` FILE, which this engine does not emit: the pipeline produces `.scala` and
    // nothing else, so the providers are constructed from OUTSIDE the closure (CLAUDE.md §1's
    // "a class a FRAMEWORK instantiates has no caller to change") off a resource no phase carries.
    // With that file absent the loader finds zero providers, the registration silently no-ops, and
    // there is no compile error, no other check count and no finding. This rule is not the fix —
    // the port has to ship the file by hand, and a rename moves both its NAME and its CONTENTS —
    // but it is what makes the dependence a NUMBER rather than a thing someone has to remember.
    // …and the two platforms need DIFFERENT verdicts, which one rule with one `why` was hiding: a
    // NATIVE-only port has a cheaper fix available than a JS-targeting one, and could not learn it
    // from a sentence that said "JVM-only". The JS rule is first because it is the stricter of the
    // two — a port targeting both is told the thing that stops it dead.
    Rule("java.util.ServiceLoader", "the class does not exist in the Scala.js javalib at all, so " +
      "this is a COMPILE-TIME resolution failure there rather than a linker one — a Scala.js port " +
      "must not port ServiceLoader usage, full stop",
      on = Rule.JsOnly, at = p(25)),
    Rule("java.util.ServiceLoader", "Scala Native has it FOR REAL, resolved at LINK time: the " +
      "toolchain enlists every provider reachable from an entrypoint, discovering them from the " +
      "META-INF/services resources of dependencies, with nativeConfig.withServiceProviders as the " +
      "explicit override and a per-provider link-time status. So this is a MAP path and not a " +
      "refusal — but it is gated on a resource file this pipeline emits nothing for (ENGINE-LIMITS " +
      "P5), and a package rename moves both that file's NAME and its CONTENTS",
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
    // ---- TIME, TEXT AND LOCALE — the refusals. The whole area had ZERO rules. ----
    //
    // A survey of it found twenty rows and not one of them was checked, so a library formatting a
    // date or collating a string passed this check clean while being unbuildable on either non-JVM
    // backend. Most of the area is a DEPENDENCY rather than a refusal (`scala-java-time`,
    // `scala-java-locales`), and those rows live on the `dependency-coverage` lane — conflating the
    // two makes the finding unanswerable, because the reader is told to remove a call that a
    // one-line `libraryDependencies` entry makes correct. What is HERE is the residue: the classes
    // for which no implementation exists in any surveyed source tree, platform javalib and
    // third-party artifact alike.
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
    // ---- TIME, TEXT AND LOCALE — the DEPENDENCIES. Twenty rows, and NOT ONE was checked. ----
    //
    // These are the other half of the area, and they are a different KIND of answer: the API exists
    // off the JVM, in an artifact the build has to add. Reported as an unportability the finding is
    // unanswerable — the reader is told to remove a call that a one-line `libraryDependencies` entry
    // makes correct — so `Verdict.Depend` routes them to `dependency-coverage` instead, and
    // `rulesFor` never yields one. They are HERE, in the same list, because the matcher, the
    // enumeration and the citation are identical; only the finding kind differs.
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

  /** THE §1(b) PARAMETER APPLIED: the UNPORTABILITY rules any of `targets` asks about.
    *
    * An empty target set selects nothing and makes the check a no-op, which is §1(b)'s own rule for
    * a parameter's empty value — but it is deliberately NOT the default any port gets. See
    * `PortManifest.targets`: the default is all three platforms, i.e. what this check did before it
    * had a parameter, so no port's baseline moves by acquiring one.
    *
    * The complement is [[dependencyRulesFor]], and the two PARTITION [[all]].
    */
  def rulesFor(targets: Set[Platform], overrides: Overrides = Map.empty): List[Rule] =
    all.filter(r => stillAsks(r, targets, overrides) && !isDependency(r, targets, overrides))

  /** …and the rules whose answer is an ARTIFACT rather than a removal.
    *
    * These feed `DependencyCheck`, never `portability(*)`. A `Verdict.Depend` is a build-graph fact
    * — the API exists off the JVM, in something the build does not name — and reported as an
    * unportability the finding is unanswerable: the reader is told to remove a call that one
    * `libraryDependencies` line makes correct. */
  def dependencyRulesFor(targets: Set[Platform], overrides: Overrides = Map.empty): List[Rule] =
    all.filter(r => stillAsks(r, targets, overrides) && isDependency(r, targets, overrides))

  /** the map a port may override a row's RECOMMENDATION with — never its availability. */
  type Overrides = Map[DiffId, Map[Platform, balticporter.catalog.Verdict]]

  /** Does this rule still ask a question of any declared target, AFTER the port's own overrides?
    *
    * Without an override this is exactly [[Rule.asks]], because commit 1's spec already forbids a
    * rule from claiming a platform its row calls `Keep`. WITH one it is the port's own escape: a
    * `verdictOverrides` entry saying `Keep` is a statement that this port accepts the JDK type on
    * that backend, and the rule then leaves BOTH lanes rather than falling through to the
    * unportability one. What such an override cannot touch is `by`, the availability FACT.
    *
    * A rule with no cited row always asks — there is nothing to override. */
  private def stillAsks(r: Rule, targets: Set[Platform], overrides: Overrides): Boolean =
    r.asks(targets) && rowOf(r).forall { row =>
      r.on.intersect(targets).exists(p => row.verdictOn(p, overrides).actionable)
    }

  /** Is every platform this rule still asks about answered by an ARTIFACT?
    *
    * ALL rather than ANY, deliberately, and `DependencyCoverageSpec` asserts that no rule in the
    * list is MIXED — some targets `Depend` and others not — so the classification is exact rather
    * than a rounding. A rule with no cited row is never a dependency: the survey is where a
    * coordinate comes from, and a rule with no row (`org.junit.`, `ClassLoader`) has none to give.
    *
    * Read THROUGH the overrides, which is what makes the third of `dependency-coverage`'s three
    * conjuncts structural rather than a second filter that could disagree with the first: a port
    * that declared it ships its own shim has changed the verdict, so the row stops being a
    * dependency here and never becomes a requirement at all. */
  private def isDependency(r: Rule, targets: Set[Platform], overrides: Overrides): Boolean =
    rowOf(r).exists { row =>
      val asked = r.on.intersect(targets).filter(p => row.verdictOn(p, overrides).actionable)
      asked.nonEmpty && asked.forall(p => row.verdictOn(p, overrides).dependency.isDefined)
    }

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
