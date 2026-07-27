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

  /** APIs unavailable on Scala.js / Scala Native. */
  val jsAndNative: List[Rule] = List(
    Rule("java.lang.reflect.", "runtime reflection does not exist on Scala.js / Native"),
    Rule("java.lang.ClassLoader", "no class loading off the JVM"),
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
    violations.filterNot(v => owningType(program, v.enclosing).exists(isDropped))

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
    rules.flatMap { r =>
      val needle = if r.exactMember then r.api.substring(r.api.indexOf('#') + 1) else r.api
      val n = code.count(_.contains(needle))
      if n == 0 then Nil else List(s"$fileName: $needle × $n — ${r.why}")
    }

  /** grouped one-line summary, most-referenced first. */
  def summary(violations: List[Violation]): String =
    if violations.isEmpty then "  none"
    else
      violations.groupBy(_.api).toList.sortBy(-_._2.size)
        .map((api, vs) => s"  $api: ${vs.size} site(s) — ${vs.head.why}")
        .mkString("\n")
