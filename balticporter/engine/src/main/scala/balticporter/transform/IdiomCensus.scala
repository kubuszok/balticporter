package balticporter.transform

import balticporter.tir.*

/** WAVE 0's OTHER TWO INERT CENSUSES — the `return this` split, and the collapsible bean pairs.
  *
  * Both are here for [[SamLambdaCensus]]'s reason and not a new one: a census is a PHASE, at the
  * POSITION its transformer will occupy, because a phase measures what it is HANDED. See that
  * class's doc for the argument and for the number behind it.
  *
  * Neither writes anything. `run` returns its argument, and the wave gate is 0 member digests on
  * every port.
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

/** the collapsible-bean-pair census — §1.1's denominator, taken at `bean-properties`' OWN position.
  *
  * The intersection this publishes is the one §8.5's deferred collapse is gated on: '''configured
  * pairs ∩ trivial-body ∩ unanchored closure ∩ no override below'''. Every other number is a
  * prediction; this is the population.
  *
  * It takes the PAIRS the port already configured — it declares no policy of its own, and could
  * not: the pair map IS the include list, and a second knob beside it would give one decision two
  * homes and let a pair be listed and then silently scoped out (§8.5's own refusal). A port with no
  * `bean-properties` phase gets no census, which is not a gap: there are no configured pairs to
  * intersect, so the honest denominator is that there is nothing to say.
  */
final class BeanCollapseCensus(pairs: Map[String, String]) extends Phase, IdiomPhase, PolicyBound:

  def name: String = "idiom-bean-collapse-census"

  def idiomKinds: Set[IdiomKind] = Set(IdiomKind.BeanCollapse)

  /** THE ACCESSORS, BOUND — never looked up by name.
    *
    * §8.1's convention and the transform-package lint that enforces it: a member found by scanning
    * the symbol table for `name == x && owner.fullName == y` is overload-blind and is a policy
    * decision taken from a string (§4.56). `PolicyBinder` is the one place that question is
    * answered, and asking it HERE rather than copying `BeanPropertyTransform`'s answer is the same
    * key resolved by the same two stages — which is what makes the census's denominator a fact
    * about the pairs THAT phase will see (§8.15's census rule) rather than a second opinion about
    * them.
    *
    * The key is RENDERED from a `MemberKey` and is BARE, exactly as the phase's own is: a bare key
    * names every overload, and the census then asks each hit whether it has the accessor's SHAPE
    * (arity 0 for a getter, 1 for a setter), because `getX(int)` beside `getX()` is a member the
    * collapse must not touch. Nothing here is REPORTED as policy — a never-matched pair is
    * `bean-properties`' finding and one key producing two rows would be the double-count §8.5's
    * "one decision, one home" rule is about — so the census reads the bindings and files its own
    * `AccessorNotFound` refusal instead, in the lane the reader of a denominator is looking at. */
  private var boundAccessors: Map[String, List[SymId]] = Map.empty

  def bindPolicy(binder: PolicyBinder): Unit =
    boundAccessors = pairs.toList.flatMap { (key, accessors) =>
      val owner = key.takeWhile(_ != '#')
      val (getter, setter) = BeanCollapseCensus.split(accessors)
      (getter :: setter.toList).map { a =>
        val k = MemberKey(owner, a).render
        k -> binder.bindMembers(name, s"BeanCollapseCensus(pairs) `$key`", k)
          .toOption.getOrElse(Nil).flatMap(_.sym)
      }
    }.toMap

  /** immediately before the phase whose policy it reads. Not "first" — the intersection is a fact
    * about the pairs THAT phase will see, and every surface phase ahead of it has already moved
    * what it reads (`SamLambdaCensus`'s doc, D1's argument). */
  override def runsBefore: Set[String] = Set("bean-properties")

  override def run(program: Program): Program =
    given Program = program
    if pairs.isEmpty then program
    else
      val graph = OverrideGraph.build(program)
      pairs.toList.sorted.foreach { (key, accessors) =>
        val (getter, setter) = BeanCollapseCensus.split(accessors)
        val owner = key.takeWhile(_ != '#')
        val prop  = key.dropWhile(_ != '#').drop(1)
        val g = member(program, owner, getter, arity = 0)
        val verdict = g match
          case scala.None => refuse("AccessorNotFound",
            "the configured getter is not a declaration this program has — `bean-properties` " +
              "reports it as `NeverMatched`, and nothing here may synthesise one (§8.5: never " +
              "invent a member)")
          case Some(gs) =>
            val closure = graph.closureOf(gs)
            val fieldOf = trivialField(program, gs)
            if closure.isAnchored then refuse("AnchoredClosure",
              closure.anchorReason(program).getOrElse("the component reaches a declaration this " +
                "program cannot move") + " — whole-or-none applies to the collapse exactly as it " +
                "applies to the rename")
            else if overriddenBelow(program, graph, gs) then refuse("OverriddenBelow",
              "a subclass overrides the accessor, and a scala `var` cannot be overridden. An " +
                "interface ABOVE would be fine — a `var` implements an abstract `def x`/`def x_=` " +
                "— but a declaration BELOW is not")
            else if fieldOf.isEmpty then refuse("ComputedBody",
              "the getter's body is not exactly `return this.f`, so the field and the property are " +
                "not the same value and collapsing them would change what a read computes. This is " +
                "why §8.5 kept bodies verbatim")
            else setter.flatMap(s => member(program, owner, s, arity = 1)) match
              case scala.None if setter.isDefined => refuse("AccessorNotFound",
                "the configured setter is not a declaration this program has")
              case ss =>
                val sField = ss.flatMap(s => trivialSetterField(program, s))
                if ss.isDefined && sField.isEmpty then refuse("ComputedBody",
                  "the setter's body is not exactly `this.f = v` — it validates, fires a listener " +
                    "or writes something else")
                else if ss.isDefined && sField != fieldOf then refuse("SplitFields",
                  "the getter and the setter touch DIFFERENT fields, so there is no single field " +
                    "to become the `var`")
                else IdiomVerdict.Refused("PhaseNotEnabled",
                  "NOT a property of the pair: it passes every §8.5 obligation. The collapse is " +
                    "not in this pipeline, so this row is the population a later wave converts — " +
                    "read it as the denominator, not as a delta the port carries")
        consider(IdiomCandidate(IdiomKind.BeanCollapse, verdict, key,
          s"property `$prop` via `$accessors`",
          g.flatMap(program.symbolOf).map(_.origin).getOrElse(Origin.synthetic)))
      }
      program

  private def refuse(g: String, why: String): IdiomVerdict = IdiomVerdict.Refused(g, why)

  /** the bound declaration with the ACCESSOR'S SHAPE — see [[bindPolicy]] for why the key is bare
    * and why the arity is asked here rather than in the key. `arity` is the java accessor's: 0 for
    * a getter, 1 for a setter. A hit whose definition this program does not hold (an external, a
    * dropped member) has no shape to ask about and is not a candidate for a collapse either. */
  private def member(p: Program, owner: String, accessor: String, arity: Int): Option[SymId] =
    val hits = boundAccessors.getOrElse(MemberKey(owner, accessor).render, Nil)
    hits.find(s => p.definitionOf(s).collect {
      case d: Tree.DefDef => d.paramss.flatten.sizeIs == arity
    }.getOrElse(false))

  /** the field a TRIVIAL getter reads — `return this.f`, and nothing else. `None` for every other
    * body, which is a refusal and never a widening.
    *
    * Read through [[soleStatement]], because a method body is a `Tree.Block` whose STATEMENTS hold
    * the `return` and whose value position holds a unit literal — matching on the return alone
    * reports every accessor in every port as computed, which is a denominator that would have
    * decided a wave. */
  private def trivialField(p: Program, getter: SymId): Option[SymId] =
    p.definitionOf(getter).collect { case d: Tree.DefDef => d }.flatMap(_.rhs)
      .flatMap(soleStatement).flatMap {
        case Tree.Return(Some(t), _, _) => selectedField(t)
        case other: Term                => selectedField(other)
        case _                          => scala.None
      }

  /** …and the field a TRIVIAL setter writes — `this.f = v`. */
  private def trivialSetterField(p: Program, setter: SymId): Option[SymId] =
    p.definitionOf(setter).collect { case d: Tree.DefDef => d }.flatMap(_.rhs)
      .flatMap(soleStatement).flatMap {
        case Tree.Assign(lhs, _, _, _)            => selectedField(lhs)
        case Tree.Return(Some(a: Tree.Assign), _, _) => selectedField(a.lhs)
        case _                                    => scala.None
      }

  /** the ONE thing a body does, or `None` where it does more than one.
    *
    * A `Tree.Block`'s value position carries a unit literal for a `void`-shaped body and the real
    * work sits in `stats`; for a value-returning body the `return` is a statement and the value
    * position is again a filler. So "exactly one statement" is the question, and the block's own
    * value position counts only when there are no statements at all. Anything else is a body that
    * does more than move a field, which is the refusal §8.5 kept bodies verbatim for. */
  private def soleStatement(t: Term): Option[balticporter.tir.Statement] = t match
    case Tree.Block(Nil, e, _, _, _)          => soleStatement(e)
    case Tree.Block(List(one), e, _, _, _)    =>
      one match
        case b: Tree.Block => soleStatement(b)
        case other         => Some(other)
    case other                                => Some(other)

  private def selectedField(t: Term): Option[SymId] = t match
    case Tree.Select(_: Tree.This, s, _, _) => Some(s)
    case Tree.Ident(s, _, _)                => Some(s)
    case _                                  => scala.None

  private def overriddenBelow(p: Program, g: OverrideGraph, m: SymId): Boolean =
    val owner = p.symbolOf(m).map(_.owner)
    g.closureOf(m).members.exists(o => o != m &&
      p.symbolOf(o).map(_.owner) != owner && g.parentsOf(p.symbolOf(o).map(_.owner).getOrElse(SymId.None))
        .contains(owner.getOrElse(SymId.None)))

object BeanCollapseCensus:
  /** `"getX/setX"` or `"getX"` — the spelling `bean-properties` already uses for its VALUE, read
    * the same way here so the two cannot disagree about what a pair is. */
  def split(accessors: String): (String, Option[String]) =
    accessors.split('/').toList match
      case g :: s :: _ => (g.trim, Some(s.trim))
      case g :: Nil    => (g.trim, scala.None)
      case _           => (accessors.trim, scala.None)
