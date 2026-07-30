package balticporter.tir

/** CONSTRUCTION PROVENANCE for a node kind, behind a flag — "which code produced this?"
  *
  * The technique it replaces: a session that needed to know where a spurious `Tree.Typed` came
  * from added a tracer to all sixteen of its construction sites by hand, ran, read the stack, and
  * deleted them again. CLAUDE.md §4.6 records the lesson (a kill switch beats another condition)
  * but not the tool.
  *
  * The constraint, stated by the audit that asked for this: **do NOT add a field to every node.**
  * A `provenance` field would touch every case class and every construction site in the frontend
  * and the transforms, be `None` almost everywhere, and change the equality of every node — and
  * the codebase's precedent is targeted carriage on the node that needs it
  * (`AnonClass.dropped`, `Symbol.droppedAnnotations`), not universal fields.
  *
  * So provenance is not carried at all: it is PRINTED at the moment of construction, from the
  * live stack, and only for the node kinds named in `balticporter.traceNode`. The node is
  * returned unchanged and identity, equality and memory are untouched — with the flag unset this
  * is one `Set.isEmpty` check.
  *
  * Usage at a construction site:
  * {{{ TirTrace.mint(Tree.Typed(expr, tpt, tpe, origin)) }}}
  *
  * ## Status: the mechanism is here, the call sites are NOT wired
  *
  * Every construction site of the node kinds worth tracing lives in the Spoon frontend and the
  * Scala emitter. Wiring them is a one-line change per site and is deliberately left to whoever
  * next debugs one of those files; nothing else can be traced from here, because construction is
  * the one event that happens outside any traversal the engine controls.
  */
object TirTrace:

  /** node kinds to trace, e.g. `balticporter.traceNode=Typed,Apply`. `*` traces every wrapped
    * site. Matched against the node's simple class name (`Typed`, `Apply`, `ClassDef`). */
  def traces(kind: String): Boolean =
    DebugFlags.traceNode.contains("*") || DebugFlags.traceNode.contains(kind)

  /** How many stack frames of the constructing code to show. Two is enough to distinguish sixteen
    * sites in two files; more is noise. */
  val Frames = 3

  /** Print `t`'s construction site when its kind is traced; return `t` unchanged, always. */
  def mint[T <: Tree](t: T): T =
    val kind = t.getClass.getSimpleName
    if traces(kind) then
      val at = new Throwable().getStackTrace.iterator
        .dropWhile(f => f.getClassName.startsWith("balticporter.tir.TirTrace"))
        .take(Frames).map(f => s"${f.getClassName}.${f.getMethodName}(${f.getFileName}:${f.getLineNumber})")
        .mkString(" <- ")
      println(s"[balticporter] MINT $kind @${t.origin.javaPath}:${t.origin.line}  from $at")
    t

  /** the same, for a value that is not a `Tree` (a `TypeRepr`, a `Symbol`) — kind must be given
    * since there is no common supertype to read a name from. */
  def mintAs[T](kind: String, t: T): T =
    if traces(kind) then
      val at = new Throwable().getStackTrace.iterator
        .dropWhile(f => f.getClassName.startsWith("balticporter.tir.TirTrace"))
        .take(Frames).map(f => s"${f.getClassName}.${f.getMethodName}(${f.getFileName}:${f.getLineNumber})")
        .mkString(" <- ")
      println(s"[balticporter] MINT $kind  from $at")
    t
