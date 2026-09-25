package balticporter.transform

import balticporter.tir.*

/** Decides what an `OpaqueSpec.Target.OwnClass` conversion does to its java constants class: which constants and receivers it retypes, which statics become extensions, and every refusal, each with
  * the site that caused it. Pure: reads the program, changes nothing.
  */
object OpaqueOwnClass:

  /** members every value has, so an extension of that name is never selected (`a.toString` calls the primitive's own); measured on scalac: the call compiles and answers the primitive's `toString`.
    */
  val UniversalNames: Set[String] =
    Set(
      "toString",
      "hashCode",
      "equals",
      "getClass",
      "isInstanceOf",
      "asInstanceOf",
      "==",
      "!=",
      "##",
      "eq",
      "ne",
      "synchronized",
      "wait",
      "notify",
      "notifyAll",
      "clone",
      "finalize"
    )

  /** one site that stops the conversion; `guard` is the refusal's name in the lane. */
  final case class Refusal(guard: String, subject: String, detail: String, origin: Origin, unit: SymId)

  /** what the conversion does, all by symbol. `receivers` covers every static taking the primitive first, extension or not; `declined` and `patternConstants` are the counted exceptions.
    */
  final case class Plan(
    cls:              SymId,
    constants:        Set[SymId],
    inlineConstants:  Set[SymId],
    extensions:       Set[SymId],
    receivers:        Set[SymId],
    ctors:            Set[SymId],
    declined:         List[(SymId, String)],
    patternConstants: List[(SymId, Origin)]
  ):
    def seeds: Set[SymId] = constants ++ receivers

  /** the plan, or the refusals. Sites in modules other than the class's own are refusals the class's owner never saw, so they are returned apart (`foreign`) and do not stop the conversion.
    */
  final case class Outcome(plan: Option[Plan], refusals: List[Refusal], foreign: List[Refusal])

  def plan(p: Program, cls: Tree.ClassDef, target: OpaqueSpec.Target.OwnClass, isPrim: TypeRepr => Boolean, sameModule: SymId => Boolean): Outcome =
    given Program = p
    val s         = p.symbolOf(cls.symbol).get
    def fqn(id: SymId):                    String = p.symbolOf(id).map(_.fullName).getOrElse(id.toString)
    def unitOf(id: SymId, fuel: Int = 64): SymId  =
      p.symbolOf(id) match
        case Some(x) if x.owner != SymId.None && fuel > 0 => unitOf(x.owner, fuel - 1)
        case _                                            => id
    def refuse(guard: String, subject: SymId, why: String, origin: Origin): Refusal =
      Refusal(guard, fqn(subject), why, origin, unitOf(subject))
    def isCtor(d:  Tree.DefDef) = p.symbolOf(d.symbol).exists(_.name == "<init>")
    def static(id: SymId)       = p.symbolOf(id).exists(_.flags.isStatic)
    val refusals                = List.newBuilder[Refusal]
    val foreign                 = List.newBuilder[Refusal]

    // the SHAPE: a plain, non-generic class whose only parent is `Object`
    val f            = s.flags
    val shapeProblem =
      if f.isTrait || f.isEnum || f.isRecord || f.isAnnotation || f.isModule then Some("it is not a plain class")
      else if cls.tparams.nonEmpty then Some("it declares type parameters")
      else if cls.parents.exists {
          case tt: TypeTree => !isObject(p, tt.tpe)
          case t:  Term     => !isObject(p, t.tpe)
        }
      then Some("it extends something other than `java.lang.Object`")
      else
        val taken = cls.body.collect { case d: Definition if !d.isInstanceOf[Tree.DefDef] || !isCtor(d.asInstanceOf[Tree.DefDef]) => p.symbolOf(d.symbol).map(_.name).getOrElse("") }.toSet
        List(target.wrapName, target.unwrapName).find(taken).map(n => s"it declares a member named `$n`, the name the conversion mints its coercion under")
    shapeProblem.foreach(why => refusals += refuse("shape", cls.symbol, why, cls.origin))

    // INSTANCE MEMBERS: anything but a constructor that is not static
    cls.body.foreach {
      case d: Tree.DefDef if !isCtor(d) && !static(d.symbol) => refusals += refuse("instance-member", d.symbol, "an instance method has no home in an opaque type's companion", d.origin)
      case v: Tree.ValDef if !static(v.symbol)               => refusals += refuse("instance-member", v.symbol, "an instance field has no home in an opaque type's companion", v.origin)
      case c: Tree.ClassDef if !static(c.symbol)             => refusals += refuse("instance-member", c.symbol, "an inner class needs an enclosing instance", c.origin)
      case _ => ()
    }

    // every use of the class that is not a static-member qualifier; a method reference's `Align::m`
    // records its qualifier as a type position, matched by node identity off the member's own usages
    val refQualifiers         = cls.body.collect { case d: Definition => d.symbol }.flatMap(p.usages).collect { case Usage(_, Tree.MethodRef(Left(q), _, _, _, _), _) => q }
    def qualifier(site: Tree) = refQualifiers.exists(_ eq site)
    p.usages(cls.symbol).foreach { u =>
      val guard = u.kind match
        case _ if qualifier(u.site)                                   => None
        case UsageKind.TermRef | UsageKind.Call                       => None
        case UsageKind.Instantiate                                    => Some("constructed" -> "the class is instantiated, and an opaque type has no constructor")
        case UsageKind.Extends | UsageKind.Mixin | UsageKind.SelfType => Some("subclassed" -> "the class is extended, and an opaque type cannot be")
        case _                                                        => Some("used-as-type" -> "the class is named as a type, which would silently mean the primitive")
      guard.foreach { (g, why) =>
        val r = refuse(g, u.enclosing, why, u.site.origin)
        if sameModule(unitOf(u.enclosing)) then refusals += r else foreign += r
      }
    }

    val rs = refusals.result()
    if rs.nonEmpty then Outcome(None, rs, foreign.result())
    else
      val constants = cls.body.collect {
        case v: Tree.ValDef if p.symbolOf(v.symbol).exists(x => x.flags.isStatic && x.flags.isFinal && !x.flags.isMutable && isPrim(x.info)) => v
      }
      // a LITERAL constant is javac-inlined: `inline def` keeps reading it initialiser-free, unless a
      // case label names it, which needs a stable identifier
      val labels = p.units.flatMap { u =>
        StandardTraversal.scanClassDef(u, List.empty[(SymId, Origin)]) { (acc, t) =>
          t match
            case m: Tree.Match => acc ++ m.cases.flatMap(_.labels).flatMap(l => refOf(l).map(_ -> l.origin))
            case _ => acc
        }
      }
      val literal                    = constants.filter(_.rhs.exists(r => Tree.uncomment(r).isInstanceOf[Tree.Literal]))
      val labelled                   = labels.toMap
      val (patterned, inlinedConsts) = literal.partition(v => labelled.contains(v.symbol))
      val referenced                 = p.units.flatMap { u =>
        StandardTraversal.scanClassDef(u, List.empty[SymId]) { (acc, t) =>
          t match
            case r: Tree.MethodRef => r.method :: acc
            case _ => acc
        }
      }.toSet
      val primFirst = cls.body.collect {
        case d: Tree.DefDef if !isCtor(d) && static(d.symbol) && d.paramss.headOption.flatMap(_.headOption).exists(pr => p.symbolOf(pr.symbol).exists(x => isPrim(x.info) && !x.flags.isVararg)) => d
      }
      val declined = primFirst.flatMap { d =>
        val n = p.symbolOf(d.symbol).map(_.name).getOrElse("")
        if UniversalNames(n) then Some(d.symbol -> s"`$n` is a member every value has, so an extension of that name would never be selected")
        else if referenced(d.symbol) then Some(d.symbol -> "a method reference names it, and an extension has no method-reference form")
        else None
      }
      val declinedIds = declined.map(_._1).toSet
      Outcome(
        Some(
          Plan(
            cls = cls.symbol,
            constants = constants.map(_.symbol).toSet,
            inlineConstants = inlinedConsts.map(_.symbol).toSet,
            extensions = primFirst.map(_.symbol).toSet -- declinedIds,
            receivers = primFirst.flatMap(_.paramss.head.headOption.map(_.symbol)).toSet,
            ctors = cls.body.collect { case d: Tree.DefDef if isCtor(d) => d.symbol }.toSet,
            declined = declined,
            patternConstants = patterned.map(v => v.symbol -> labelled(v.symbol))
          )
        ),
        Nil,
        foreign.result()
      )

  private def isObject(p: Program, t: TypeRepr): Boolean = t match
    case TypeRepr.TypeRef(_, s) => p.symbolOf(s).exists(_.fullName == "java.lang.Object")
    case _                      => false

  private def refOf(t: Term): Option[SymId] = Tree.uncomment(t) match
    case Tree.Ident(s, _, _)     => Some(s)
    case Tree.Select(_, s, _, _) => Some(s)
    case _                       => None
