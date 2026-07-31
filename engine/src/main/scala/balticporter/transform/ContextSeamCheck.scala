package balticporter.transform

import balticporter.tir.*

/** The CONTEXT BOUNDARY, counted — every place the threading stopped, and why.
  *
  * ==Why this exists==
  * [[GlobalsToImplicitsTransform]] turns a Java static holder into a Scala 3 `using` parameter
  * threaded through everything that reaches it. The mechanism is a CLOSURE, and a closure has an
  * edge: a declaration that reads the holder and cannot take a parameter. A class initialiser has
  * no signature; a static field's initialiser runs before anything could pass it a context; an
  * override component that reaches an unparsed parent is not this program's to re-sign.
  *
  * The predecessor design closed that edge with an ambient `given T = new T()` in the context's
  * companion — and that made every seam SILENT. An unthreaded caller of a threaded callee compiled
  * cleanly against the ambient default, so the global was reintroduced with extra steps and nothing
  * counted it. DESIGN.md §8.4 deletes the ambient given, which makes an unthreaded owned caller of a
  * threaded callee '''impossible by construction''' — the closure would have threaded it — EXCEPT
  * across a refused boundary. Those sites are exactly what this counts.
  *
  * So: a number with an origin and a CLAUDE.md §1 classification, available before any compiler
  * runs. A port that enables the phase sees the size of its boundary immediately.
  *
  * ==The four kinds, and why they are four==
  * Each one is a different instruction to its reader, which is the same argument
  * [[balticporter.tir.NotBound]] makes for refusing to collapse its cases:
  *
  *   - [[Kind.ResidualGlobalRead]] — the read is still a GLOBAL read. Either it was rewritten to the
  *     context companion's `global` (`boundary = "residual-global"`) or it was left naming the
  *     upstream holder (`boundary = "refuse"`). This is the count that says how much of the global
  *     survived, and it is the one a port drives to zero.
  *   - [[Kind.DeferredInit]] — a static initialiser was rewritten to initialise on first READ
  *     instead of at class initialisation. That is an EAGER→LAZY semantic change, never a default,
  *     and it is counted here as well as recorded as a `Decision` because the two answer different
  *     questions: the decision says what one declaration became, this says how many there are.
  *   - [[Kind.CapturedContext]] — the read is inside a lexically nested body (an anonymous class,
  *     an enum-constant body) whose own method could not be re-signed, so the context is CAPTURED
  *     from the enclosing declaration's clause. It is correct and free; it is counted because a
  *     captured context outlives the call that supplied it, which is a fact about the port a
  *     reader should be able to size without reading every anonymous body.
  *   - [[Kind.FrozenComponent]] — an override component this program may not re-sign
  *     ([[balticporter.tir.OverrideGraph.Closure.isAnchored]]), or a trait whose own body needs the
  *     context and has no `promoteToClass` entry. Threading half a component is a broken `override`,
  *     so the whole of it is refused; each refusal names the member and what froze it.
  *
  * ==Its gate is the BASELINE, not a constant==
  * Deliberately no hard-coded fatality on any kind. The count is a finding and the committed
  * baseline is what a rise fails against — the same shape as the trivia check. "Fatal when this kind
  * is non-zero" bakes one library's census into the engine (CLAUDE.md §1) and is exactly the
  * per-library constant this repository keeps having to remove: a NEW library's honest first run
  * would fail on a number that is simply its starting point.
  *
  * ==Universal in mechanism, parameterised by the policy==
  * §1(a) in shape — "a signature cannot be added here" is a fact about Java and Scala — and it
  * counts nothing at all unless a port declared a holder. An empty `holders` list makes it a no-op
  * by arithmetic: the phase records no seam, so this records nothing, exactly the way
  * `collection-boundary` records nothing for a port with no collections phase.
  */
object ContextSeamCheck:

  /** The check's name in `findings.tsv`. */
  val Name = "context-seam"

  /** what kind of seam this is, which is what decides who fixes it (CLAUDE.md §1). */
  enum Kind(val label: String):
    case ResidualGlobalRead extends Kind("residual-global-read")
    case DeferredInit       extends Kind("deferred-init")
    case CapturedContext    extends Kind("captured-context")
    case FrozenComponent    extends Kind("frozen-component")

  object Kind:
    /** which of §1's three kinds the fix is — the thing a bare typer error cannot say. */
    def classification(k: Kind): String = k match
      case ResidualGlobalRead =>
        "§1(b) PER-LIBRARY: this read still reaches a global. It is at a site with no signature to " +
          "thread a context through — a class initialiser, a static field's initialiser, or a " +
          "declaration inside a refused override component. Move it behind a method the closure " +
          "can reach, give the site a `sites` policy (`lazy-init`), or accept it and set " +
          "`boundary = \"residual-global\"` so the read at least names the context rather than the " +
          "upstream holder. The engine needs no change."
      case DeferredInit =>
        "§1(b) PER-LIBRARY and DELIBERATE: a `sites` entry asked for `lazy-init`, so this static is " +
          "now initialised at first READ instead of at class initialisation. Java runs a class " +
          "initialiser at first active use of the class; the two coincide only when nothing else " +
          "in the class is touched first. Read the decision row and confirm that holds here."
      case CapturedContext =>
        "§1(a) and CORRECT: the read is inside a lexically nested body whose own signature could " +
          "not change, so it captures the context from the enclosing declaration's clause. Nothing " +
          "to fix; the count exists so a port can size how much of its context outlives the call " +
          "that supplied it."
      case FrozenComponent =>
        "§1(b)/§1(a): this override component reaches a declaration this program does not own — an " +
          "unparsed parent, or a resolution root's — so its signature is not this module's to " +
          "change, and threading half a component is a broken `override`. If the parent IS ported, " +
          "port it in the same run; if it is a trait of this program's own whose body needs the " +
          "context, add it to `promoteToClass`; otherwise the reads inside it stay global and are " +
          "counted above."

  /** one seam.
    *
    * @param subject   the DECLARATION the seam is at, fully qualified at the time it was found.
    * @param key       the policy entry a reader edits — the holder FQN, or the `sites` key.
    * @param enclosing the declaration's symbol, for the ownership filter and for attribution.
    */
  final case class Finding(kind: Kind, subject: String, key: String, detail: String,
                           origin: Origin, enclosing: SymId):
    def render: String = s"${kind.label} $subject — $detail  (${origin.javaPath}:${origin.line})"
    def report: CheckReport.Finding =
      CheckReport.Finding(Name, kind.label, subject, CheckReport.relativise(origin.javaPath),
                          origin.line, s"$detail [key=$key]")

  /** grouped one-line summary, worst family first, each with its §1 classification — the whole point
    * of the check is that a reader does not have to work out who fixes it. */
  def summary(fs: List[Finding]): String =
    if fs.isEmpty then "  none"
    else
      fs.groupBy(_.kind).toList.sortBy((_, v) => -v.size).map { (kind, vs) =>
        val head  = s"  ${vs.size} × ${kind.label}\n  ${Kind.classification(kind)}"
        val sites = vs.groupBy(f => (f.subject, f.detail)).toList.sortBy((_, v) => -v.size).take(10)
          .map { case ((subj, det), ss) => s"    ${ss.size} × $subj: $det" }
        (head :: sites).mkString("\n")
      }.mkString("\n")
