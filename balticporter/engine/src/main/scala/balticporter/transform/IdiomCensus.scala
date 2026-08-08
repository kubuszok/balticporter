package balticporter.transform

import balticporter.tir.*

/** WAVE 0's SURVIVING INERT CENSUS — the `return this` split.
  *
  * A census is a PHASE, at the POSITION its transformer will occupy, because a phase measures what
  * it is HANDED and every surface phase ahead of it has already moved what it reads. It writes
  * nothing: `run` returns its argument, and the gate is 0 member digests on every port.
  *
  * ==Two censuses stood here and both are gone, by the rule that put them there==
  * `SamLambdaCensus` retired when `SamLambdaTransform` landed, and `BeanCollapseCensus` when
  * [[BeanPropertyTransform]] gained the `var`/`val` collapse. A transformer files one row per site
  * CONSIDERED — `Converted` or `Refused(guard)` — which IS the denominator the census published, so
  * a census beside it is a second answer to its own question and doubles every row in the lane
  * (§4.6, one mechanism per seam). What survives here is the one question no transformer answers
  * yet.
  */
object IdiomCensus:

  /** WHAT A `this.type` NARROWING WOULD BUY, split the one way that decides whether it is worth a
    * wave at all.
    *
    * For a method whose DECLARED return type IS the declaring class, `this.type` adds nothing at a
    * call on that class and adds precision only at a call on a SUBCLASS. For a method whose
    * declared return type is a STRICT ANCESTOR — a fluent builder returning the interface — it
    * removes a downcast at every chained call. Which bucket a corpus's `return this;` population
    * falls into cannot be grepped, and a wave spent on hundreds of member digests for zero removed
    * downcasts is a wave spent on churn. So the split is published and the decision is a
    * maintainer's, taken on a number.
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

  /** WHAT THE POPULATION IS — and it is deliberately NOT "every method".
    *
    * A census's numerator is only readable against a denominator a reader can recognise, and "every
    * `DefDef` in the program" is not one: it would report thousands of rows saying *this method does
    * not return `this`*, which is true of almost every method ever written and tells a reader
    * nothing. The population is the java construct §1.3 is about — a method with at least one
    * `return this` — and everything outside it produces no row at all. A constructor, a `static`, a
    * body-less declaration, and a return type that is not a reference to a type this program
    * declares are outside it for the same reason. */

/** the `return this;` census — §1.3's go/no-go, taken at §1.3's planned position. */
final class ReturnThisCensus extends Phase, IdiomPhase:

  def name: String = "idiom-return-this-census"

  def idiomKinds: Set[IdiomKind] = Set(IdiomKind.NarrowedReturn)

  /** the narrowing moves a declaration's RETURN TYPE, so it runs before every retyping phase for
    * `SamLambdaCensus`'s reason — the type it would compare against must be java's own. */
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

  /** `None` for a declaration OUTSIDE the population — see [[IdiomCensus.ReturnShape]]'s note for
    * why that is no row at all rather than a fourth bucket. */
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
      // every `return` in the body, plus the body's own value position — a tail expression is a
      // return java wrote without the keyword once the lowering has peeled it.
      val rets    = d.rhs.toList.flatMap(collectReturns)
      val answers = rets.flatMap(_.toList) ++ d.rhs.toList
      if !answers.exists(isThisTerm) then scala.None      // outside the population
      else if !rets.forall(_.exists(isThisTerm)) then Some(NotAlwaysThis)
      else if retSym.contains(clsSym) then Some(SelfTyped)
      else Some(AncestorTyped)

  /** every `return`'s operand in this body. Through `StandardTraversal` so a node kind added
    * tomorrow is visited (§3) — and note this deliberately includes a `return` inside a nested
    * lambda or anonymous body, which is NOT this method's return: the census reports the SHAPE and
    * a transformer would have to read the lowered tree, so an over-count here lands in
    * `NotAlwaysThis` and never in a bucket that would convert. */
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
