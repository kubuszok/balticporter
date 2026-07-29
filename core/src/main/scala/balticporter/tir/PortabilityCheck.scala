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
  )

  def check(program: Program, rules: List[Rule] = jsAndNative): List[Violation] =
    val out = checkAll(program, rules)
    given Program = program
    // recorded separately from `inEmittedCode` below: the two numbers answer different questions
    // (what the program references vs. what the SHIPPED code references) and a run that reports
    // only one of them cannot show a substitution moving a violation out of the deliverable.
    CheckReport.record("portability(all)", out.map(_.report("portability(all)")))
    out

  private def checkAll(program: Program, rules: List[Rule]): List[Violation] =
    program.referenced.toList.flatMap { id =>
      program.symbolOf(id) match
        case scala.None => Nil
        case Some(sym) =>
          // a MEMBER is identified by `owner#name` (an external member's own fullName is an
          // internal interning key, so the owner's name is what carries meaning here).
          val memberName = program.symbolOf(sym.owner).map(o => s"${o.fullName}#${sym.name}")
          val hit = rules.find { r =>
            if r.exactMember then memberName.contains(r.api)
            else sym.fullName == r.api || sym.fullName.startsWith(r.api)
          }
          hit match
            case scala.None    => Nil
            case Some(r) =>
              val api = if r.exactMember then memberName.getOrElse(r.api) else sym.fullName
              program.usages(id).map(u => Violation(api, r.why, u.site.origin, u.kind, u.enclosing))
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
  def inEmittedCode(program: Program, violations: List[Violation], isDropped: SymId => Boolean): List[Violation] =
    val out = violations.filterNot(v => owningType(program, v.enclosing).exists(isDropped))
    given Program = program
    CheckReport.record("portability(emitted)", out.map(_.report("portability(emitted)")))
    out

  /** INJECTED replacements never pass through the TIR — they are copied verbatim — so the symbol
    * table cannot see them. They are still shipped, so scan their text for the same rules. Coarse
    * by nature (no symbols to resolve), but it closes the hole that matters: a hand-written shim
    * that quietly reintroduces the very API the substitution was meant to remove.
    */
  def inInjectedSource(fileName: String, source: String, rules: List[Rule] = jsAndNative): List[String] =
    // comments discuss the very APIs being removed (a swap-point note naming `newInstance` is not
    // a use of it), so scan code lines only — otherwise the count is noise.
    val code = source.linesIterator
      .map(_.trim)
      .filterNot(l => l.startsWith("//") || l.startsWith("*") || l.startsWith("/*"))
      .toList
    val out = rules.flatMap { r =>
      val needle = if r.exactMember then r.api.substring(r.api.indexOf('#') + 1) else r.api
      val n = code.count(_.contains(needle))
      if n == 0 then Nil else List((needle, n, r.why, s"$fileName: $needle × $n — ${r.why}"))
    }
    // called once per injected file, so this ACCUMULATES rather than replaces. The count is
    // deliberately not part of the finding id: a shim gaining a second use of an API it already
    // used is the same finding, and should not read as one fixed plus one new.
    CheckReport.append("portability(injected)",
      out.map((needle, n, why, _) => CheckReport.Finding("portability(injected)", needle, fileName, fileName, n, why)))
    out.map(_._4)

  /** grouped one-line summary, most-referenced first. */
  def summary(violations: List[Violation]): String =
    if violations.isEmpty then "  none"
    else
      violations.groupBy(_.api).toList.sortBy(-_._2.size)
        .map((api, vs) => s"  $api: ${vs.size} site(s) — ${vs.head.why}")
        .mkString("\n")
