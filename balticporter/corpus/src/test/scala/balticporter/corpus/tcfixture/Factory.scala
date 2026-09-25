package balticporter.corpus.tcfixture

import scala.quoted.*

/** What a CONSUMER writes for `type-class-params`: how to build a `T` with no reflection. The instance is derived at compile time for a concrete class with an accessible no-argument constructor; any
  * other type is a compile error at the call that asks for one.
  */
trait Factory[T]:
  def create(): T

object Factory:
  inline given derived[T]: Factory[T] = ${ derivedImpl[T] }

  private def derivedImpl[T: Type](using q: Quotes): Expr[Factory[T]] =
    import q.reflect.*
    val tpe = TypeRepr.of[T]
    val cls = tpe.classSymbol.getOrElse(report.errorAndAbort(s"${tpe.show} is not a class, so no Factory can be derived for it"))
    if cls.flags.is(Flags.Abstract) || cls.flags.is(Flags.Trait) then report.errorAndAbort(s"${tpe.show} is abstract, so no Factory can be derived for it")
    val nilary = (cls.primaryConstructor :: cls.declarations.filter(_.isClassConstructor)).find { c =>
      !c.isNoSymbol && !c.flags.is(Flags.Private) && !c.flags.is(Flags.Protected) && c.paramSymss.forall(ps => ps.isEmpty || ps.head.isTypeParam)
    }
    val ctor  = nilary.getOrElse(report.errorAndAbort(s"${tpe.show} has no accessible no-argument constructor, so no Factory can be derived for it"))
    val built = New(Inferred(tpe)).select(ctor).appliedToArgs(Nil).asExprOf[T]
    '{
      new Factory[T]:
        def create(): T = $built
    }

/** a class the fixture cannot construct: its only constructor takes an argument. */
final class NeedsArgument(val n: Int)
