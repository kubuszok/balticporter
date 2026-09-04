package balticporter.transform

import balticporter.tir.*

/** THE `var`/`val` COLLAPSE'S DECISION — every guard, stated once (`DESIGN.md` §8.5). A java bean
  * pair over a trivial backing field and a scala `var` are the same value with two spellings, so
  * [[BeanPropertyTransform]] can collapse the `def` pair — `Converted` or `Refused(guard)`, what
  * `idiom(refused)` reads. Each guard blocks one delta from the faithful translation. The pairs
  * map IS the include list; no `RuleScope` (would let a pair be listed then silently scoped out). */
object BeanCollapse:

  /** WHY a configured pair was not collapsed. A closed enum, because [[IdiomVerdict.Refused]]'s
    * `guard` is the string a reader classifies a row by and an open one would let two sites describe
    * one refusal two ways. */
  enum Guard:
    case PairRefused, AnchoredClosure, OverriddenBelow, ConcreteRelative, ComputedBody, SplitFields,
         FieldNotOwned, NotRequested, VarWithoutSetter, ValWithSetter, MutableStorage, ExposedField

    def why: String = this match
      case PairRefused =>
        "NOT a statement about the collapse: the PAIR itself was refused, so there is no property " +
          "to collapse. The `policy` finding for this key says why, and fixing that is what makes " +
          "this row answerable"
      case AnchoredClosure =>
        "the accessor's override component reaches a declaration this program cannot move (an " +
          "unparsed parent, or a resolution root's), so this run may not decide its shape — " +
          "whole-or-none applies to the collapse exactly as it applies to the rename"
      case OverriddenBelow =>
        "PERMANENT: a subclass overrides the accessor and a scala `var` cannot be overridden. An " +
          "interface ABOVE would be fine — a `var` implements an abstract `def x`/`def x_=` — but " +
          "a declaration BELOW is not"
      case ConcreteRelative =>
        "PERMANENT: another declaration in the accessor's override component has a BODY, and a " +
          "scala `var` can IMPLEMENT an abstract `def x`/`def x_=` but cannot OVERRIDE a concrete " +
          "one. An abstract member above — an interface's, or an abstract class's — is fine, which " +
          "is the case this guard is careful to admit"
      case ComputedBody =>
        "PERMANENT: an accessor's body is not exactly `return this.f` / `this.f = v`, so the field " +
          "and the property are not the same value and collapsing them would change what a read " +
          "computes or drop what a write does. This is why the def-pair keeps bodies verbatim"
      case SplitFields =>
        "PERMANENT: the getter and the setter touch DIFFERENT fields, so there is no single field " +
          "to become the `var`"
      case FieldNotOwned =>
        "the trivial body names a value this program does not DECLARE as a field of the accessor's " +
          "own class — an inherited field, or one whose declaration this run does not hold — so " +
          "there is no declaration here to become the property"
      case NotRequested =>
        "NOT a defect and NOT a limit: the pair passes every §8.5 obligation and the port has not " +
          "asked for it. Write `target = \"var\"` (or `\"val\"`) on this entry to convert it; read " +
          "this row as the DENOMINATOR the converted lane is drawn from"
      case VarWithoutSetter =>
        "`target = \"var\"` on a GET-ONLY entry would publish a setter java never had. Name the " +
          "setter in `accessors`, or ask for `\"val\"`"
      case ValWithSetter =>
        "`target = \"val\"` on a pair WITH a setter would delete a writer java published. Ask for " +
          "`\"var\"`, or drop the setter from `accessors`"
      case MutableStorage =>
        "`target = \"val\"` needs storage java wrote HERE and never wrote again — a field with its " +
          "own declaration initialiser that NOTHING in this program assigns. A field the " +
          "constructor fills has its `val`/`var` keyword decided by the constructor funnel, which " +
          "this phase cannot see, and a field something assigns cannot be a `val` at all: the " +
          "honest answer is to refuse rather than to guess, because an over-refusal is a counted " +
          "skip and an under-refusal is `E052 Reassignment to val` at best"
      case ExposedField =>
        "the port asks for two opposite things at one type: `public-field-accessors` PUTS java-bean " +
          "names on this field for a reflective framework to find (`ENGINE-LIMITS.md` K21 face 2), " +
          "and the collapse TAKES them off. Remove one of the two policy entries"

  /** the ANSWER for one configured pair. */
  enum Verdict:
    /** collapse it — `field` is the backing declaration that becomes the property. */
    case Collapse(field: SymId)
    case Refuse(guard: Guard)

  /** THE DECISION, for one pair the def-pair path accepted. Stated obligations-first and `target`
    * LAST, deliberately: a pair the port has not asked for still gets a §8.5 verdict, so
    * `NotRequested` reports only after the collapse is shown possible — the reading a maintainer
    * deciding whether to widen an enablement needs. */
  def decide(p: Program, graph: OverrideGraph, prop: BeanPropertyTransform.Property,
             target: BeanPropertyTransform.Target, exposed: RuleScope, written: Set[SymId]): Verdict =
    import BeanPropertyTransform.Target
    val g   = prop.getter
    val acc = g :: prop.setter.toList
    if acc.exists(a => graph.closureOf(a).isAnchored) then Verdict.Refuse(Guard.AnchoredClosure)
    else if acc.exists(a => overriddenBelow(graph, a)) then Verdict.Refuse(Guard.OverriddenBelow)
    else if acc.exists(a => concreteRelative(p, graph, a)) then Verdict.Refuse(Guard.ConcreteRelative)
    else
      trivialField(p, g) match
        case scala.None => Verdict.Refuse(Guard.ComputedBody)
        case Some(f) =>
          val sField = prop.setter.map(s => trivialSetterField(p, s))
          if sField.contains(scala.None) then Verdict.Refuse(Guard.ComputedBody)
          else if sField.exists(_ != Some(f)) then Verdict.Refuse(Guard.SplitFields)
          else if !ownedFieldOf(p, f, g) then Verdict.Refuse(Guard.FieldNotOwned)
          else if target == Target.DefPair then Verdict.Refuse(Guard.NotRequested)
          else if target == Target.Var && prop.setter.isEmpty then
            Verdict.Refuse(Guard.VarWithoutSetter)
          else if target == Target.Val && prop.setter.isDefined then
            Verdict.Refuse(Guard.ValWithSetter)
          else if target == Target.Val && !immutableHere(p, f, written) then
            Verdict.Refuse(Guard.MutableStorage)
          else if isExposed(p, exposed, f) then Verdict.Refuse(Guard.ExposedField)
          else Verdict.Collapse(f)

  /** does the port ALSO ask for java-bean accessors on this field? See [[Guard.ExposedField]].
    * Asked of the FIELD through [[PublicFieldAccessorTransform]]'s own `RuleScope` (§4.56); its
    * default scope (`Only(Set.empty)`) includes nothing, so a port without that phase asks nothing. */
  private def isExposed(p: Program, exposed: RuleScope, field: SymId): Boolean =
    p.symbolOf(field).exists(exposed.includes(p, _))

  /** is `f` a FIELD of the accessor's OWN class, declared by this program? Structural: `p.owns`
    * (§4.56), matching owners, and the `Tree.ValDef`-under-`Tree.ClassDef` shape — never a local
    * or a parameter, and never an inherited field this phase may not move. */
  private def ownedFieldOf(p: Program, f: SymId, getter: SymId): Boolean =
    p.owns(f) &&
      p.symbolOf(f).map(_.owner) == p.symbolOf(getter).map(_.owner) &&
      p.definitionOf(f).exists(_.isInstanceOf[Tree.ValDef]) &&
      p.symbolOf(f).exists(s => !s.flags.isStatic && !s.flags.isParam)

  /** may this field really be emitted as a `val`? See [[Guard.MutableStorage]]. Neither conjunct is
    * java's `final` keyword — a non-`final` field that is never reassigned is common, and refusing
    * on the modifier would decline most of the get-only population for a syntax fact rather than a
    * program one. Structural instead: the initialiser is on the node, and nothing writes it (the
    * whole-program scan [[writtenSymbols]]). */
  private def immutableHere(p: Program, f: SymId, written: Set[SymId]): Boolean =
    !written.contains(f) &&
      p.definitionOf(f).collect { case v: Tree.ValDef => v }.exists(_.rhs.isDefined)

  /** every symbol the WHOLE PROGRAM assigns or increments, taken once — a setter in another member
    * or type is invisible from the declaration (the same argument `CtorFunnel.writesPerField`
    * makes). Through `StandardTraversal`, so a node kind added tomorrow is visited (§3). */
  def writtenSymbols(p: Program): Set[SymId] =
    given Program = p
    val acc = collection.mutable.Set.empty[SymId]
    def target(t: Term): Unit = t match
      case Tree.Ident(s, _, _)     => acc += s
      case Tree.Select(_, s, _, _) => acc += s
      case _                       => ()
    val ph = new Phase:
      def name = "bean-collapse/writes"
      override def transformTerm(t: Term)(using Program): Term =
        t match
          case Tree.Assign(lhs, _, _, _, _)   => target(lhs)
          case Tree.IncDec(tg, _, _, _, _) => target(tg)
          case _                           => ()
        t
    p.units.foreach(u => StandardTraversal.mapStat(ph, u))
    acc.toSet

  /** the field a TRIVIAL getter reads — `return this.f`, and nothing else. `None` for every other
    * body, which is a refusal and never a widening. Read through [[soleStatement]]: a method body
    * is a `Tree.Block` whose STATEMENTS hold the `return`, and matching on the return alone would
    * report every accessor as computed. */
  def trivialField(p: Program, getter: SymId): Option[SymId] =
    p.definitionOf(getter).collect { case d: Tree.DefDef => d }.flatMap(_.rhs)
      .flatMap(soleStatement).flatMap {
        case Tree.Return(Some(t), _, _) => selectedField(t)
        case other: Term                => selectedField(other)
        case _                          => scala.None
      }

  /** …and the field a TRIVIAL setter writes — `this.f = v`. */
  def trivialSetterField(p: Program, setter: SymId): Option[SymId] =
    p.definitionOf(setter).collect { case d: Tree.DefDef => d }.flatMap(_.rhs)
      .flatMap(soleStatement).flatMap {
        case Tree.Assign(lhs, _, _, _, _)               => selectedField(lhs)
        case Tree.Return(Some(a: Tree.Assign), _, _) => selectedField(a.lhs)
        case _                                       => scala.None
      }

  /** the ONE thing a body does, or `None` where it does more than one. A `Tree.Block`'s value
    * position is a filler unit literal for a `void`-shaped body (the real work sits in `stats`), so
    * "exactly one statement" is the question, counting the value position only when `stats` is
    * empty. Anything else does more than move a field — the refusal §8.5 kept bodies verbatim for. */
  def soleStatement(t: Term): Option[Statement] = t match
    case Tree.Block(Nil, e, _, _, _)       => soleStatement(e)
    case Tree.Block(List(one), _, _, _, _) =>
      one match
        case b: Tree.Block => soleStatement(b)
        case other         => Some(other)
    case other => Some(other)

  private def selectedField(t: Term): Option[SymId] = t match
    case Tree.Select(_: Tree.This, s, _, _) => Some(s)
    case Tree.Ident(s, _, _)                => Some(s)
    case _                                  => scala.None

  /** does any OTHER declaration of this member's component have a BODY? See
    * [[Guard.ConcreteRelative]] — the half of the override question `overriddenBelow` does not
    * answer, deciding whether the emitted `var` is an IMPLEMENTATION or an (illegal) OVERRIDE. */
  def concreteRelative(p: Program, g: OverrideGraph, m: SymId): Boolean =
    g.closureOf(m).members.exists(o => o != m &&
      p.definitionOf(o).collect { case d: Tree.DefDef => d }.exists(_.rhs.isDefined))

  /** does a SUBCLASS of the declaring class declare this member too? See [[Guard.OverriddenBelow]] —
    * an ancestor is fine and a descendant is not. Uses `OverrideGraph.overriders`'s transitive
    * answer, not a one-hop direct-parent test: a re-declaration two levels down would otherwise read
    * as "nothing below" and the collapse would emit a `var` it cannot legally be. */
  def overriddenBelow(g: OverrideGraph, m: SymId): Boolean =
    g.overriders(m).nonEmpty
