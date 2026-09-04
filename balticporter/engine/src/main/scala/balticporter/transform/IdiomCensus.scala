package balticporter.transform

import balticporter.tir.*

/** The `return this` census — an INERT phase (writes nothing, 0 member digests) at the position
  * its eventual transformer would occupy, so it measures the same input a transformer would. Once
  * a construct gets a real transformer, its census retires — the transformer's own refusal
  * population becomes the denominator (§4.6, one mechanism per seam). */
object IdiomCensus:

  /** Splits `return this` methods by what a `this.type` narrowing would buy: nothing at a
    * self-typed return (precision only on a subclass call) vs. a removed downcast at every chained
    * call on an ancestor-typed one. Published so the go/no-go is a maintainer's, taken on a number.
    */
  enum ReturnShape:
    /** every `return` is `this` and the declared return type is the declaring class itself. */
    case SelfTyped
    /** …and the declared return type is a STRICT ANCESTOR — the bucket that buys something. */
    case AncestorTyped
    /** at least one `return this` and at least one that is not, so the narrowing is unavailable. */
    case NotAlwaysThis

    def why: String = this match
      case SelfTyped =>
        "the declared return type is ALREADY the declaring class, so `this.type` buys precision " +
          "only at a call on a SUBCLASS — real, but small, and it is the bucket that makes a " +
          "blanket narrowing mostly churn"
      case AncestorTyped =>
        "the declared return type is a STRICT ANCESTOR, so every chained call currently needs a " +
          "downcast that `this.type` would remove — this is the bucket a narrowing wave is FOR"
      case NotAlwaysThis =>
        "PERMANENT: this method answers `this` on one path and something else on another, so " +
          "`this.type` would be a lie"

  /** The population is deliberately NOT "every method" — only one with at least one `return this`;
    * a constructor, a `static`, a body-less declaration, or a non-program return type is outside
    * it and produces no row. */

/** the `return this;` census — §1.3's go/no-go, taken at §1.3's planned position. */
final class ReturnThisCensus extends Phase, IdiomPhase:

  def name: String = "idiom-return-this-census"

  def idiomKinds: Set[IdiomKind] = Set(IdiomKind.NarrowedReturn)

  /** the narrowing moves a declaration's return type, so this must run before every retyping
    * phase — the type it compares against must be java's own. */
  override def runsBefore: Set[String] = Set("java-collections->scala", "package-rename")

  override def run(program: Program): Program =
    given Program = program
    program.units.foreach { u =>
      StandardTraversal.allClassDefs(u).foreach { cd =>
        val clsSym = cd.symbol
        cd.body.foreach {
          case d: Tree.DefDef => shapeOf(clsSym, d).foreach(sh => consider(row(d, sh)))
          case _              => ()
        }
      }
    }
    program

  private def row(d: Tree.DefDef, shape: IdiomCensus.ReturnShape)(using p: Program): IdiomCandidate =
    val fqn = p.symbolOf(d.symbol).map(_.fullName).getOrElse("<unknown>")
    IdiomCandidate(IdiomKind.NarrowedReturn, IdiomVerdict.Refused(shape.toString, shape.why), fqn,
      "a method that answers `this`", d.origin)

  /** `None` for a declaration outside the population (no row, not a fourth bucket). */
  private def shapeOf(clsSym: SymId, d: Tree.DefDef)(using p: Program)
      : Option[IdiomCensus.ReturnShape] =
    import IdiomCensus.ReturnShape.*
    val sym      = p.symbolOf(d.symbol)
    val isCtor   = sym.exists(s => s.name == "<init>" || s.name == "this")
    val isStatic = sym.exists(_.flags.isStatic)
    val retSym = d.returnTpt.tpe match
      case TypeRepr.TypeRef(_, s)                          => Some(s)
      case TypeRepr.AppliedType(TypeRepr.TypeRef(_, s), _) => Some(s)
      case _                                               => scala.None
    if isCtor || isStatic || d.rhs.isEmpty || !retSym.exists(p.owned.contains) then scala.None
    else
      // every `return`, plus the body's tail value (an implicit return once lowered)
      val rets    = d.rhs.toList.flatMap(collectReturns)
      val answers = rets.flatMap(_.toList) ++ d.rhs.toList
      if !answers.exists(isThisTerm) then scala.None      // outside the population
      else if !rets.forall(_.exists(isThisTerm)) then Some(NotAlwaysThis)
      else if retSym.contains(clsSym) then Some(SelfTyped)
      else Some(AncestorTyped)

  /** every `return`'s operand in this body, via `StandardTraversal` (§3). Deliberately includes a
    * `return` inside a nested lambda/anonymous body — over-counting here only lands a row in
    * `NotAlwaysThis`, never in a bucket that would convert. */
  private def collectReturns(t: Term)(using Program): List[Option[Term]] =
    StandardTraversal.scanTerm(t, List.empty[Option[Term]]) { (acc, x) =>
      x match
        case Tree.Return(e, _, _) => acc :+ e
        case _                    => acc
    }

  private def isThisTerm(t: Term): Boolean = t match
    case _: Tree.This             => true
    case Tree.Typed(e, _, _, _)   => isThisTerm(e)
    case Tree.Block(_, e, _, _, _) => isThisTerm(e)
    case _                        => false
