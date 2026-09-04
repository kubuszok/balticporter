package balticporter.tir

/** CONSTRUCTION PROVENANCE for a node kind, behind a flag — "which code produced this?"
  * (CLAUDE.md §4.6's kill switch, generalized). No field added to every node (identity/equality
  * untouched) — instead PRINTED at construction, from the live stack, only for kinds named in
  * `balticporter.traceNode`. Usage: `TirTrace.mint(Tree.Typed(...))`. STATUS: mechanism exists,
  * call sites are NOT wired — wiring is a one-line change left to whoever debugs a construction site. */
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
