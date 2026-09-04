package balticporter.transform

import balticporter.tir.*

/** Decides whether a java anonymous class implementing a single-abstract-method interface can
  * become a scala lambda ascribed to that interface. `decide` is the single source of truth (no
  * separate census, CLAUDE.md §4.6). Every behavioural delta is made impossible by a [[Guard]] or
  * by the ascribed-lambda SHAPE, or counted on the conversion's own `Decision`. `DESIGN.md` §8.15. */
object SamLambda:

  /** WHY a site was declined. A closed enum, because [[IdiomVerdict.Refused]]'s `guard` is the
    * string a reader classifies a row by and an open one would let two sites describe one refusal
    * two ways. Each case's [[why]] says whether the refusal is permanent. */
  enum Guard:
    case NotSam, Unreadable, BodyNotSingle, SelfReference, NonCapturing, Serializable,
         MethodMismatch, GenericMethod, ConstructorArgs

    def why: String = this match
      case NotSam =>
        "PERMANENT and correct: java's own SAM rule (JLS 9.8) does not admit this type, so there " +
          "is no lambda to write"
      case Unreadable =>
        "NOT a statement about the type: the class file is not on this port's classpath, so the " +
          "question could not be asked. Widening the frontend classpath would move this row; " +
          "assuming an answer here would be a fabricated fact (§4.6)"
      case BodyNotSingle =>
        "PERMANENT: a scala lambda is one expression and has no place for a field, an instance " +
          "initialiser, a helper method or a nested type. The anonymous class is the faithful form"
      case SelfReference =>
        "PERMANENT: `this`/`super` inside a java anonymous class names the ANON INSTANCE and " +
          "inside a scala lambda names the ENCLOSING class — the conversion would compile and mean " +
          "something else (§4.4)"
      case NonCapturing =>
        "PERMANENT, and licensed by the SPECIFICATION'S SILENCE rather than by any measurement: " +
          "JVMS 5.4.3.6 and the `LambdaMetafactory` contract leave a lambda's instance identity " +
          "UNSPECIFIED in both directions, so a non-capturing lambda MAY be the same object at two " +
          "evaluations while `new I(){…}` never is. A library that removes a listener by identity, " +
          "keys an `IdentityHashMap` or synchronises on the instance would then differ, with a " +
          "green compile and no moved count"
      case Serializable =>
        "PERMANENT: the target extends `java.io.Serializable`, and a serializable lambda's " +
          "serialized form is not the anonymous class's — `$deserializeLambda$` is not a " +
          "translation of a class descriptor"
      case MethodMismatch =>
        "the single member does not implement the interface's single abstract method by name and " +
          "arity, so what a lambda would supply is not what the anonymous class supplied"
      case GenericMethod =>
        "PERMANENT with the IR as it stands: the implemented method declares its own TYPE " +
          "PARAMETERS and a scala function literal has nowhere to put them, so the conversion " +
          "would drop a binder java wrote"
      case ConstructorArgs =>
        "the `new` carries CONSTRUCTOR ARGUMENTS, which java's SAM rule cannot produce — an " +
          "INTERFACE has no constructor to pass them to. A site reaching this guard is an engine " +
          "defect upstream of it, not a residue, and it is counted rather than silently dropped"

  /** the ANSWER for one site. `Convert` carries everything the rewrite needs, so the transformer
    * never re-reads the tree to find it. */
  enum Verdict:
    case Convert(method: Tree.DefDef, iface: String, javaClassName: String)
    case Refuse(guard: Guard, iface: String)

  /** The decision for one `new T(){…}` in one enclosing declaration. Guard order decides which
    * guard a multiply-refused site is attributed to — cheapest fact first. */
  def decide(nw: Tree.New, anon: Tree.AnonClass)(using p: Program): Verdict =
    val iface = typeName(nw.tpt.tpe)
    def refuse(g: Guard) = Verdict.Refuse(g, iface)
    anon.sam match
      case Sam.Answer.Unreadable    => refuse(Guard.Unreadable)
      case Sam.Answer.No(_)         => refuse(Guard.NotSam)
      case Sam.Answer.Yes(m, arity, ser) =>
        if ser then refuse(Guard.Serializable)
        else if anon.dropped.nonEmpty then refuse(Guard.BodyNotSingle)
        else
          anon.body match
            case (d: Tree.DefDef) :: Nil =>
              val name = p.symbolOf(d.symbol).map(_.name).getOrElse("")
              if d.rhs.isEmpty then refuse(Guard.BodyNotSingle)
              else if d.tparams.nonEmpty then refuse(Guard.GenericMethod)
              else if name != m || d.paramss.flatten.sizeIs != arity then refuse(Guard.MethodMismatch)
              else if selfReferences(d, anon.symbol, ancestryOf(nw, anon)) then refuse(Guard.SelfReference)
              else if !captures(d, anon.symbol) then refuse(Guard.NonCapturing)
              else Verdict.Convert(d, iface, javaClassNameOf(anon))
            case _ => refuse(Guard.BodyNotSingle)

  /** does the body name the ANON INSTANCE — the §4.4 meaning change (`this`/`super` bound to the
    * anon, or a bare reference resolving to an inherited member). A qualified outer `this` is fine
    * under a lambda and not refused. Bare-reference form exists because the frontend drops
    * `this.` receivers and resolves them lexically, so `this.toString()` arrives as a plain
    * `Ident` — matched here by owner rather than node shape. */
  private def selfReferences(d: Tree.DefDef, anonSym: SymId, ancestry: Set[SymId])
                            (using p: Program): Boolean =
    d.rhs.exists(b => StandardTraversal.scanTerm(b, false) { (acc, t) =>
      acc || (t match
        case Tree.This(c, _, _)  => c == anonSym
        case Tree.Super(c, _, _) => c == anonSym
        case Tree.Ident(s, _, _) => !namesAType(s) && p.symbolOf(s).exists { sym =>
          sym.owner == anonSym || ancestry.contains(sym.owner) ||
            p.symbolOf(sym.owner).exists(_.fullName == SamLambda.JavaLangObject)
        }
        case _ => false)
    })

  /** `java.lang.Object` is an IMPLICIT parent the tree never writes down, so an ancestry walk over
    * declared parents cannot reach it — kept as a name instead. */
  private val JavaLangObject = "java.lang.Object"

  /** The anon's strict ancestry — the SAM interface it named plus every parent this program
    * DECLARES, transitively. Seeded from `nw.tpt` (the anon symbol carries no parent list). An
    * external super-interface is not reached — its members are unknowable, covered by
    * `Guard.Unreadable` instead of guessed. Cycle-safe and fuel-bounded. */
  private def ancestryOf(nw: Tree.New, anon: Tree.AnonClass)(using p: Program): Set[SymId] =
    def headSym(t: TypeRepr): Option[SymId] = t match
      case TypeRepr.TypeRef(_, s)      => Some(s)
      case TypeRepr.AppliedType(tc, _) => headSym(tc)
      case _                           => scala.None
    val out  = collection.mutable.Set.empty[SymId]
    val work = collection.mutable.Queue.from(headSym(nw.tpt.tpe))
    var fuel = 256
    while work.nonEmpty && fuel > 0 do
      fuel -= 1
      val s = work.dequeue()
      if s != SymId.None && out.add(s) then
        p.definitionOf(s).collect { case cd: Tree.ClassDef => cd }.foreach { cd =>
          cd.parents.foreach {
            case tt: TypeTree => headSym(tt.tpe).foreach(work.enqueue)
            case t: Term      => headSym(t.tpe).foreach(work.enqueue)
          }
        }
    out.toSet

  /** does this `Ident` name a TYPE rather than a member? A type reference is emitted
    * fully-qualified (§6) so it cannot re-resolve wrongly, and must not be refused as a capture. */
  private def namesAType(s: SymId)(using p: Program): Boolean =
    p.definitionOf(s).exists(_.isInstanceOf[Tree.ClassDef]) ||
      p.symbolOf(s).exists(_.info match
        case TypeRepr.TypeRef(_, head) => head == s
        case _                         => false)

  /** what one conversion was, carried from the rewrite to the attribution one hook later. */
  private[transform] final case class Converted(iface: String, javaClassName: String,
                                                method: String, origin: Origin)

  /** does the body CAPTURE an enclosing binding — instance identity, guard 5. Three structural
    * shapes count: `this` naming an ENCLOSING class, a reference to a local/parameter owned by an
    * outer executable, or a reference to a non-static instance member of an enclosing instance. A
    * local declared inside the body is not a capture. "provably evaluated once" is NOT
    * implemented — such sites are counted under [[Guard.NonCapturing]] rather than guessed. */
  private def captures(d: Tree.DefDef, anonSym: SymId)(using p: Program): Boolean =
    d.rhs.exists(b => StandardTraversal.scanTerm(b, false) { (acc, t) =>
      acc || (t match
        case Tree.This(c, _, _) => c != anonSym
        case Tree.Ident(s, _, _) =>
          isEnclosingBinding(s, d.symbol, anonSym) || isEnclosingMember(s, anonSym)
        case _                   => false)
    })

  /** is `s` a local or parameter of an executable OUTSIDE this anonymous class? */
  private def isEnclosingBinding(s: SymId, anonMethod: SymId, anonSym: SymId)(using p: Program): Boolean =
    p.symbolOf(s).exists { sym =>
      val owner = sym.owner
      owner != SymId.None && owner != anonMethod && owner != anonSym &&
        p.definitionOf(owner).exists(_.isInstanceOf[Tree.DefDef])
    }

  /** is `s` an instance member of an ENCLOSING instance ([[captures]]'s third shape)? `static` is
    * read off the referenced symbol, not its owner, so a companion constant is not a capture. */
  private def isEnclosingMember(s: SymId, anonSym: SymId)(using p: Program): Boolean =
    p.symbolOf(s).exists { sym =>
      !sym.flags.isStatic && sym.owner != SymId.None && sym.owner != anonSym &&
        p.symbolOf(sym.owner).exists(o => !PolicyBinder.isExecutable(o.info))
    }

  /** the name java gave the anonymous class — `Outer$1`. Recorded on the decision: the one thing
    * the lambda cannot carry (`getClass()` differs). */
  def javaClassNameOf(anon: Tree.AnonClass)(using p: Program): String =
    p.symbolOf(anon.symbol).map(_.fullName).getOrElse("<anon>")

  private def typeName(t: TypeRepr)(using p: Program): String = t match
    case TypeRepr.TypeRef(_, s)       => p.symbolOf(s).map(_.fullName).getOrElse(s.toString)
    case TypeRepr.AppliedType(tc, _)  => typeName(tc)
    case other                        => other.toString

/** Converts an anonymous class implementing a single-abstract-method interface into a lambda,
  * ASCRIBED to that interface. §1 kind (a): unparameterised — which types are SAM is structural.
  * The ascription keeps every call's candidate set unchanged (no `JS-C22`/`JS-C23` risk); rewrites
  * no call site, moves no declaration, mints no unit. Runs before every retyping phase. */
final class SamLambdaTransform extends Phase, IdiomPhase:

  def name: String = "sam-anon->lambda"

  def idiomKinds: Set[IdiomKind] = Set(IdiomKind.SamLambda)

  override def runsBefore: Set[String] = Set("java-collections->scala", "package-rename")

  override def run(program: Program): Program =
    given Program = program
    program.rebuilt(program.units.map(u => StandardTraversal.mapClassDef(new Converter(this), u)))

  /** The rewriting traversal and the attribution of what it did. The traversal is bottom-up, so
    * the enclosing definition's hook fires after every conversion in its body and claims them — a
    * nested `def` takes its own, the enclosing member takes the rest. One `Decision` per
    * declaration with a `count`, never one per site. // CLAUDE.md §5.1
    */
  private final class Converter(owner: SamLambdaTransform) extends Phase:
    def name: String = "sam-anon->lambda/convert"

    private val pending = collection.mutable.ListBuffer.empty[SamLambda.Converted]
    private val refused = collection.mutable.ListBuffer.empty[(SamLambda.Guard, String, Origin)]

    /** The conversion happens at the `Apply`, not the `Tree.New`: the frontend models
      * `new I(){…}` as `Apply(New(…, anon), args, <init>)`, and converting at `New` alone would
      * leave the `Apply` standing and emit a lambda applied to the constructor args. A non-empty
      * `args` here is counted (`Guard.ConstructorArgs`) rather than silently dropped. */
    override def transformApply(t: Tree.Apply)(using p: Program): Term = t.fun match
      case nw @ Tree.New(_, _, _, Some(anon)) =>
        SamLambda.decide(nw, anon) match
          case SamLambda.Verdict.Convert(_, iface, _) if t.args.nonEmpty =>
            file(SamLambda.Guard.ConstructorArgs, iface, nw); t
          case SamLambda.Verdict.Convert(d, iface, javaName) =>
            pending += SamLambda.Converted(iface, javaName,
              p.symbolOf(d.symbol).map(_.name).getOrElse("?"), nw.origin)
            // `Typed` over a lambda renders as an ascription, not `asInstanceOf`.
            // resultTpt = the SAM method's own declared return type, needed by the emitter to
            // restore `return`-leaves-the-lambda (`JS-S21`) — distinct from the ascription type.
            // ENGINE-LIMITS I9
            Tree.Typed(Tree.Lambda(d.paramss.flatten, d.rhs.get, nw.tpe, nw.origin,
                                   resultTpt = Some(d.returnTpt)),
                       nw.tpt, nw.tpe, nw.origin)
          case SamLambda.Verdict.Refuse(g, iface) =>
            file(g, iface, nw); t
      case _ => t

    private def file(g: SamLambda.Guard, iface: String, nw: Tree.New): Unit =
      refused += ((g, iface, nw.origin))

    override def transformDefDef(t: Tree.DefDef)(using p: Program): Tree.DefDef =
      claim(t.symbol, t.origin); t

    override def transformValDef(t: Tree.ValDef)(using p: Program): Tree.ValDef =
      claim(t.symbol, t.origin); t

    override def transformClassDef(t: Tree.ClassDef)(using p: Program): Tree.ClassDef =
      claim(t.symbol, t.origin); t

    /** is this definition a MEMBER of a type, as opposed to a local/parameter? A conversion inside
      * `Runnable a = new Runnable(){…};` must not be claimed by the local `a` — it has no row in
      * `members.tsv` to join on — so it bubbles up to the enclosing member instead. // CLAUDE.md §4.55
      */
    private def isMember(sym: SymId)(using p: Program): Boolean =
      p.symbolOf(sym).map(_.owner).flatMap(p.definitionOf).exists(_.isInstanceOf[Tree.ClassDef])

    /** everything considered since the last member-level definition hook belongs to THIS
      * declaration — conversions and refusals alike; `idiom(refused)` is read per-declaration. */
    private def claim(sym: SymId, at: Origin)(using p: Program): Unit =
      val fqn = p.symbolOf(sym).map(_.fullName).getOrElse("<unknown>")
      if refused.nonEmpty && isMember(sym) then
        val rs = refused.toList
        refused.clear()
        rs.foreach { (g, iface, o) =>
          owner.consider(IdiomCandidate(IdiomKind.SamLambda, IdiomVerdict.Refused(g.toString, g.why),
            fqn, s"anonymous `$iface`", o))
        }
      if pending.nonEmpty && isMember(sym) then
        val cs = pending.toList
        pending.clear()
        cs.foreach(c => owner.consider(IdiomCandidate(IdiomKind.SamLambda, IdiomVerdict.Converted,
          fqn, s"anonymous `${c.iface}` -> lambda", c.origin)))
        owner.record(Decision(Decision.Kind.SamLambda, sym, fqn, Map(
          "count"     -> cs.size.toString,
          "interface" -> cs.map(_.iface).distinct.sorted.mkString(" "),
          "method"    -> cs.map(_.method).distinct.sorted.mkString(" "),
          // the residue no guard can reach — see `Decision.Kind.SamLambda`.
          "was"       -> cs.map(_.javaClassName).distinct.sorted.mkString(" "),
          "why"       -> ("java's anonymous class had a STABLE class name; a lambda's is a hidden " +
                          "class, so getClass/getSimpleName/toString read differently here"),
        ), Reason.Universal("anon-SAM->lambda(DESIGN §8.15)"), at))

