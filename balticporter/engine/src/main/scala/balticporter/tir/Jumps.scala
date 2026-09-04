package balticporter.tir

/** Java's `break`/`continue` binding rules as predicates over a subtree.
  *
  * Shared by `TirEmitter` (boundary placement) and `BreakCatchCheck` (crossing detection).
  * Uses product reflection to walk children (stops at re-binding constructs, so
  * `StandardTraversal` cannot serve here). */
object Jumps:

  /** True if this subtree contains an unlabelled `break` belonging to THIS construct.
    * Stops at nested loops/switches (they re-bind unlabelled `break`). */
  def breaksOut(t: Any): Boolean = t match
    case Tree.Break(scala.None, _, _)                     => true
    case _: Tree.While | _: Tree.DoWhile | _: Tree.Match |
         _: Tree.For | _: Tree.ForEach                    => false // binds to the inner one
    case xs: Iterable[?]                                  => xs.exists(breaksOut)
    case Some(x)                                          => breaksOut(x)
    case p: Product                                       => p.productIterator.exists(breaksOut)
    case _                                                => false

  /** True if this subtree contains an unlabelled `continue` for THIS loop.
    * Does NOT stop at `match` (java's `continue` inside a switch continues the enclosing loop). */
  def continuesIn(t: Any): Boolean = t match
    case Tree.Continue(scala.None, _, _)                                  => true
    case _: Tree.While | _: Tree.DoWhile | _: Tree.For | _: Tree.ForEach  => false
    case xs: Iterable[?]                                                  => xs.exists(continuesIn)
    case Some(x)                                                          => continuesIn(x)
    case p: Product                                                       => p.productIterator.exists(continuesIn)
    case _                                                                => false

  /** True if this subtree contains a non-tail `yield` for THIS switch expression (JLS 14.21).
    * Stops at nested switch EXPRESSIONS only (not statements); also stops at lambdas, defs
    * and class bodies. */
  def yieldsOut(t: Any): Boolean = t match
    case _: Tree.Yield                                            => true
    case m: Tree.Match if m.isExpr                                => false // binds to the inner one
    case _: Tree.Lambda | _: Tree.DefDef | _: Tree.AnonClass |
         _: Tree.ClassDef                                         => false
    case xs: Iterable[?]                                          => xs.exists(yieldsOut)
    case Some(x)                                                  => yieldsOut(x)
    case p: Product                                               => p.productIterator.exists(yieldsOut)
    case _                                                        => false

  /** True if this subtree contains a `break L` / `continue L` naming this label, at any depth. */
  def jumpsTo(t: Any, label: String, brk: Boolean): Boolean = t match
    case Tree.Break(Some(l), _, _) if brk     => l == label
    case Tree.Continue(Some(l), _, _) if !brk => l == label
    case xs: Iterable[?]                      => xs.exists(jumpsTo(_, label, brk))
    case Some(x)                              => jumpsTo(x, label, brk)
    case p: Product                           => p.productIterator.exists(jumpsTo(_, label, brk))
    case _                                    => false

  /** The label a loop carries, if any (loops store their label in the node, not in `Tree.Labeled`).
    * // ENGINE-LIMITS F1 */
  def loopLabel(t: Term): Option[String] = t match
    case w: Tree.While   => w.label
    case d: Tree.DoWhile => d.label
    case f: Tree.For     => f.label
    case e: Tree.ForEach => e.label
    case _               => scala.None

  def isLoop(t: Term): Boolean = t match
    case _: Tree.While | _: Tree.DoWhile | _: Tree.For | _: Tree.ForEach => true
    case _                                                               => false

  /** The caught types a `scala.util.boundary.Break` is an instance of.
    * Matched by name (these are JDK types no phase retypes or renames). */
  val BreakCatchable: Set[String] = Set(
    "java.lang.Throwable",
    "java.lang.Exception",
    "java.lang.RuntimeException",
    "scala.util.boundary.Break",
  )

  /** Can an arm catching `t` match a `Break`? Unfolds multi-catch and applied types. */
  def catchesBreak(t: TypeRepr)(using program: Program): Boolean = t match
    case TypeRepr.OrType(l, r)      => catchesBreak(l) || catchesBreak(r)
    case TypeRepr.AndType(l, r)     => catchesBreak(l) || catchesBreak(r)
    case TypeRepr.AppliedType(c, _) => catchesBreak(c)
    case TypeRepr.TypeRef(_, s)     => program.symbolOf(s).exists(sym => BreakCatchable(sym.fullName))
    case _                          => false
