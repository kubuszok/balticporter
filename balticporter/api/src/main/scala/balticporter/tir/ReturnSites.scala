package balticporter.tir

/** Walks every `return` of a method body and the body's tail value, without crossing into a lambda or a local class (a `return` there belongs to that function, JLS 14.17). A phase that retypes a
  * method's result maps both through here rather than writing its own recursion. `onTail` fires only where the expression is the method's value; a statement in a block never is.
  */
object ReturnSites:
  def map(t: Term, tail: Boolean = true)(onReturn: Term => Term, onTail: Term => Term = identity): Term =
    def walk(t: Term, tail: Boolean): Term = t match
      case x: Tree.Return       => x.copy(expr = x.expr.map(onReturn))
      case x: Tree.Block        => x.copy(stats = x.stats.map { case s: Term => walk(s, false); case s => s }, expr = walk(x.expr, tail))
      case x: Tree.If           => x.copy(thenp = walk(x.thenp, tail), elsep = walk(x.elsep, tail))
      case x: Tree.While        => x.copy(body = walk(x.body, false))
      case x: Tree.DoWhile      => x.copy(body = walk(x.body, false))
      case x: Tree.For          => x.copy(body = walk(x.body, false))
      case x: Tree.ForEach      => x.copy(body = walk(x.body, false))
      case x: Tree.Synchronized => x.copy(body = walk(x.body, tail))
      case x: Tree.Labeled      => x.copy(stmt = walk(x.stmt, tail))
      case x: Tree.Commented    => x.copy(stmt = walk(x.stmt, tail))
      case x: Tree.Try          =>
        x.copy(
          body = walk(x.body, tail),
          catches = x.catches.map(c => c.copy(body = walk(c.body, tail))),
          finalizer = x.finalizer.map(walk(_, false))
        )
      case x: Tree.Match => x.copy(cases = x.cases.map(c => c.copy(body = walk(c.body, tail))))
      case other if tail => onTail(other)
      case other         => other
    walk(t, tail)
