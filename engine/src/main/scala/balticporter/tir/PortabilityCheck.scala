package balticporter.tir

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
  */
object PortabilityCheck:

  final case class Rule(api: String, why: String, exactMember: Boolean = false)

  final case class Violation(api: String, why: String, origin: Origin, kind: UsageKind, enclosing: SymId):
    def render: String = s"$api — $why  (${origin.javaPath}:${origin.line})"
    def report(check: String)(using program: Program): CheckReport.Finding =
      CheckReport.Finding(check, api, program.symbolOf(enclosing).map(_.fullName).getOrElse("?"),
        CheckReport.relativise(origin.javaPath), origin.line, s"$kind — $why")

  /** APIs unavailable on Scala.js / Scala Native. */
  val jsAndNative: List[Rule] = List(
    Rule("java.lang.reflect.", "runtime reflection does not exist on Scala.js / Native"),
    Rule("java.lang.ClassLoader", "no class loading off the JVM"),
    // Reflection was the only thing this checked until 2026-07-28, so it reported ZERO for a
    // corpus containing an HTTP client, a thread pool and NIO channels. A check that reports zero
    // is only as good as its coverage — the same failure as the annotations one, and found the
    // same way: by asking why a known-unportable class was not being flagged.
    Rule("java.net.", "networking is JVM-only; Scala.js needs fetch/XHR, Native its own stack"),
    Rule("java.nio.channels.", "NIO channels are JVM-only"),
    Rule("java.nio.file.", "the java.nio.file filesystem API is JVM-only"),
    Rule("java.util.concurrent.", "the java.util.concurrent runtime is JVM-only (Scala.js is single-threaded)"),
    Rule("java.lang.Thread", "threads do not exist on Scala.js"),
    Rule("java.lang.ProcessBuilder", "process spawning is JVM-only"),
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
    Rule("java.lang.System#getProperty", "system properties are JVM-only", exactMember = true),
    Rule("java.util.zip.", "java.util.zip is JVM-only"),
    Rule("javax.", "the javax.* stack is JVM-only"),
    Rule("java.lang.Class#forName", "runtime class lookup by name is JVM-only", exactMember = true),
    Rule("java.lang.Class#newInstance", "reflective instantiation is JVM-only", exactMember = true),
    Rule("java.lang.Class#getDeclaredFields", "reflective member access is JVM-only", exactMember = true),
    Rule("java.lang.Class#getDeclaredMethods", "reflective member access is JVM-only", exactMember = true),
    Rule("java.lang.Class#getDeclaredConstructor", "reflective member access is JVM-only", exactMember = true),
    Rule("java.lang.Class#getFields", "reflective member access is JVM-only", exactMember = true),
    Rule("java.lang.Class#getMethods", "reflective member access is JVM-only", exactMember = true),
    Rule("java.lang.Class#getConstructor", "reflective member access is JVM-only", exactMember = true),
    // The SINGULAR readers, added the moment the rules above started firing at all. They are the
    // same family as their plural twins — each returns a `java.lang.reflect.*` — and leaving them
    // out was invisible while no member rule fired. It stopped being invisible immediately:
    // `Remediator` reads this list to decide which members of a static wrapper may be inlined, and
    // with no rule for `getDeclaredField` it offered to forward one, which would have moved a
    // reflective call from the wrapper to every call site while reporting the port improved.
    // A gap in a rule list is not neutral once something else reasons from it.
    Rule("java.lang.Class#getDeclaredField", "reflective member access is JVM-only", exactMember = true),
    Rule("java.lang.Class#getDeclaredMethod", "reflective member access is JVM-only", exactMember = true),
    Rule("java.lang.Class#getField", "reflective member access is JVM-only", exactMember = true),
    Rule("java.lang.Class#getMethod", "reflective member access is JVM-only", exactMember = true),
  )

  /** Every violation the PROGRAM references. Recorded by the orchestrator as `portability(all)`,
    * separately from `inEmittedCode` below: the two numbers answer different questions (what the
    * program references vs. what the SHIPPED code references) and a run that reports only one of
    * them cannot show a substitution moving a violation out of the deliverable. */
  def check(program: Program, rules: List[Rule] = jsAndNative): List[Violation] =
    checkAll(program, rules)

  /** The RULE FILTER, over an enumeration this object no longer owns.
    *
    * The walk it used to perform inline — every referenced symbol, its `owner#name` (a MEMBER is
    * identified that way, since an external member's own `fullName` is an interning key), and every
    * recorded usage — is [[ExternalUsage.all]], because it answers more questions than these 34
    * rules ask and throwing it away was the reason no artifact of a port's external dependencies
    * existed anywhere.
    *
    * The lift is order-preserving on purpose: [[ExternalUsage.all]] iterates
    * `program.referenced.toList` and each symbol's usages in exactly the order this loop did, so
    * `portability(all)`'s promoted baseline in thirteen lanes is byte-identical rather than
    * merely equal in count. */
  private def checkAll(program: Program, rules: List[Rule]): List[Violation] =
    ExternalUsage.all(program).flatMap { row =>
      val hit = rules.find { r =>
        if r.exactMember then row.member.contains(r.api)
        else row.fullName == r.api || row.fullName.startsWith(r.api)
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
  def inInjectedSource(fileName: String, source: String, rules: List[Rule] = jsAndNative): List[InjectedViolation] =
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
