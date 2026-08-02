package balticporter.tir

/** Java's binding rules for `break` and `continue`, as predicates over a subtree.
  *
  * ONE definition, two readers. `TirEmitter` asks these to decide where a `boundary` goes; the
  * `break-catch` check asks the same questions to decide which jumps CROSS a translated `catch`
  * (CLAUDE.md §4.4). Two copies of "which construct does this jump belong to" would be two
  * answers, and the one that is wrong is the one nothing measures — a check whose predicate is
  * broader than the emitter's reports a site the emitter handled, and one that is narrower reports
  * zero for a site nobody handled.
  *
  * Product reflection rather than a hand-written case per node: a hand-rolled walk that stops one
  * node short is exactly how two of this project's silent defects survived (CLAUDE.md §3), and
  * there is no generic child accessor on the TIR to use instead. `StandardTraversal` cannot serve
  * here — every one of these predicates has to STOP at a construct that re-binds the jump, and a
  * scan that reaches every node by contract has no way to express that.
  */
object Jumps:

  /** an unlabelled `break` that belongs to THIS construct.
    *
    * Stops descending at a nested loop or switch, since java's unlabelled `break` binds to the
    * innermost enclosing one — a `boundary` placed around the outer loop would otherwise catch a
    * break the inner construct owns. */
  def breaksOut(t: Any): Boolean = t match
    case Tree.Break(scala.None, _, _)                     => true
    case _: Tree.While | _: Tree.DoWhile | _: Tree.Match |
         _: Tree.For | _: Tree.ForEach                    => false // binds to the inner one
    case xs: Iterable[?]                                  => xs.exists(breaksOut)
    case Some(x)                                          => breaksOut(x)
    case p: Product                                       => p.productIterator.exists(breaksOut)
    case _                                                => false

  /** an unlabelled `continue` belonging to THIS loop. Unlike `breaksOut` it does NOT stop at a
    * `match`: java's `continue` inside a switch continues the enclosing LOOP. */
  def continuesIn(t: Any): Boolean = t match
    case Tree.Continue(scala.None, _, _)                                  => true
    case _: Tree.While | _: Tree.DoWhile | _: Tree.For | _: Tree.ForEach  => false
    case xs: Iterable[?]                                                  => xs.exists(continuesIn)
    case Some(x)                                                          => continuesIn(x)
    case p: Product                                                       => p.productIterator.exists(continuesIn)
    case _                                                                => false

  /** a `break L` / `continue L` naming this construct, at ANY depth — a labelled jump crosses
    * nested loops and switches by definition, which is what it is for. */
  def jumpsTo(t: Any, label: String, brk: Boolean): Boolean = t match
    case Tree.Break(Some(l), _, _) if brk     => l == label
    case Tree.Continue(Some(l), _, _) if !brk => l == label
    case xs: Iterable[?]                      => xs.exists(jumpsTo(_, label, brk))
    case Some(x)                              => jumpsTo(x, label, brk)
    case p: Product                           => p.productIterator.exists(jumpsTo(_, label, brk))
    case _                                    => false

  /** The label a loop carries, if any — the one place a java label is NOT a [[Tree.Labeled]].
    *
    * A labelled loop keeps its label in its own node, because that same label is `continue L`'s
    * target and the two boundaries go in different places (`ENGINE-LIMITS.md` F1). Anything asking
    * "which labels are in scope here" therefore has to read both encodings. */
  def loopLabel(t: Term): Option[String] = t match
    case w: Tree.While   => w.label
    case d: Tree.DoWhile => d.label
    case f: Tree.For     => f.label
    case e: Tree.ForEach => e.label
    case _               => scala.None

  def isLoop(t: Term): Boolean = t match
    case _: Tree.While | _: Tree.DoWhile | _: Tree.For | _: Tree.ForEach => true
    case _                                                               => false

  // ---- what a translated `catch` can intercept ---------------------------------------------
  //
  // `scala.util.boundary.Break[T] extends RuntimeException(null, null, false, false)` — read off
  // the 3.8.x library source, `scala/util/boundary.scala`, not assumed. It is deliberately NOT a
  // `scala.util.control.ControlThrowable`, so `NonFatal` returns true for it as well: every
  // idiomatic "swallow anything non-fatal" arm catches a jump too.
  //
  // Matching by NAME is what §4.56 forbids a phase from doing about a type it did not create — but
  // these four are the JDK's own, which no phase in this engine declares, retypes or renames (a
  // package rename rewrites OWNED symbols only). There is nothing structural to read: the frontend
  // interned them as external symbols with no definition. The set is the exact supertype chain of
  // `Break` that java permits in a `catch`, and it is closed: java has no other way to spell a type
  // that a `RuntimeException` is an instance of.

  /** the caught types a `scala.util.boundary.Break` is an instance of. */
  val BreakCatchable: Set[String] = Set(
    "java.lang.Throwable",
    "java.lang.Exception",
    "java.lang.RuntimeException",
    "scala.util.boundary.Break",
  )

  /** can an arm catching `t` match a `Break`? Unfolds java's multi-catch (`catch (A | B e)`) and
    * an applied type, so `Break[?]` and a union both answer for their constituents. */
  def catchesBreak(t: TypeRepr)(using program: Program): Boolean = t match
    case TypeRepr.OrType(l, r)      => catchesBreak(l) || catchesBreak(r)
    case TypeRepr.AndType(l, r)     => catchesBreak(l) || catchesBreak(r)
    case TypeRepr.AppliedType(c, _) => catchesBreak(c)
    case TypeRepr.TypeRef(_, s)     => program.symbolOf(s).exists(sym => BreakCatchable(sym.fullName))
    case _                          => false
