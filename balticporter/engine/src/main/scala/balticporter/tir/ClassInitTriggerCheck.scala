package balticporter.tir

/** Counts java class initialisers (JLS 12.4.2 step 9) that reach a companion `object` but
  * have no trigger reproducing java's initialisation. The census uses [[stepNine]] (static field
  * initialisers + `static {}` blocks, excluding compile-time constants). The emitter supplies
  * which types it forced and what form it chose (object/class/enum/trait).
  * Limit: counts missing triggers, not ORDERING vs java. // ENGINE-LIMITS K22 */
object ClassInitTriggerCheck:

  val Name = "class-init-trigger"

  enum Issue:
    /** Java's instantiation trigger unreproduced: class emitted without companion force. */
    case Unforced
    /** JLS 12.4.1 item 7: subclass init should trigger ancestor's class init. */
    case SubclassInitUnforced
    /** Forcing declined: re-entrant initialisation that scala cannot survive. A counted refusal. */
    case ReentrantRefused

  object Issue:
    def classification(i: Issue): String = i match
      case ReentrantRefused =>
        "§1(a) ENGINE, and a REFUSAL rather than a defect — `ENGINE-LIMITS.md` K22's second face. " +
          "This type's class initialiser constructs (or reads a static of) a type whose own class " +
          "initialiser comes back to this one, so java runs both with a CYCLE in the graph and " +
          "survives it: the JVM lets a thread already initialising `T` re-enter `T` and read " +
          "whatever its statics hold so far (JLS 12.4.2 step 3). Scala's companion has no such " +
          "rule — a re-entrant force reads a half-built module and the initialiser throws — so " +
          "attaching java's instantiation trigger here would turn a program that works into one " +
          "that dies at first use, which is worse than the registration that silently does not " +
          "happen. The trigger is not attached and the residue is counted here. There is nothing " +
          "to configure: the fix, if one exists, is a different lowering for a companion whose " +
          "initialisation is cyclic."
      case Unforced =>
        "§1(a) ENGINE: reproducing java's class-initialisation triggers is a universal " +
          "java-vs-scala fact, never per-library policy. `TirEmitter.forceCompanion` puts a " +
          "`val _ = <type>` at the head of the class body for every type whose companion carries " +
          "JLS 12.4.2 step-9 content — a `static { }` block or a static field initialiser that is " +
          "not a compile-time constant. A finding here means that content reached the output " +
          "through a rendering that has no class body to put the trigger in — fix the emitter, " +
          "not the port."
      case SubclassInitUnforced =>
        "§1(a) ENGINE: JLS 12.4.1 initialises a class when one of its SUBCLASSES is initialised, " +
          "and a subclass with statics of its own is initialised by a bare `S.member` read that " +
          "touches no instance. `TirEmitter.forceCompanion` answers it from the subclass's own " +
          "companion body — fix the emitter, not the port."

  /** Human-readable name for the step-9 construct. */
  private val StepNine = "a java class initialiser (a `static { }` block or a non-constant static field initialiser)"

  /** @param owner    the type carrying, or inheriting, the class initialiser
    * @param declarer where the class initialiser is declared — the same as `owner` for
    *                 [[Issue.Unforced]] and an ancestor for [[Issue.SubclassInitUnforced]]
    */
  final case class Finding(issue: Issue, owner: String, declarer: String, form: String, origin: Origin):
    def detail: String = issue match
      case Issue.Unforced =>
        s"`$owner` is emitted as a `$form` carrying $StepNine, and nothing initialises the " +
          s"companion that content lowered into: `new ${owner.split('.').last}(…)` runs the " +
          s"constructor and never touches the object, so its effects — a registration, a factory, " +
          s"a table — silently do not happen"
      case Issue.SubclassInitUnforced =>
        s"`$owner` inherits $StepNine from `$declarer` and has a companion of its own, so java " +
          s"initialises `$declarer` first on any route that initialises `$owner` — a bare " +
          s"`$owner.member` read above all (JLS 12.4.1 item 7) — and initialising this port's " +
          s"`$owner` object reaches no other object"
      case Issue.ReentrantRefused =>
        s"`$owner` is emitted as a `$form` carrying $StepNine whose own initialisation comes back " +
          s"to `$owner` (through `$declarer`), so java's instantiation trigger cannot be " +
          s"reproduced here: the JVM tolerates re-entering a class it is already initialising and " +
          s"a scala companion does not. The trigger is DECLINED, which leaves this type's class " +
          s"initialiser running on scala's own schedule — first touch of the object"
    def render: String = s"$issue $owner ($form) <- $declarer  (${origin.javaPath}:${origin.line})"
    def report: CheckReport.Finding =
      CheckReport.Finding(Name, issue.toString, owner, CheckReport.relativise(origin.javaPath),
        origin.line, detail)

  /** Trigger names matching porter-note values. */
  val Instantiation = "instantiation"
  val SubclassInit  = "subclass-init"

  /** @param forced  emitter-attached (type, trigger) pairs
    * @param shapeOf emitter-decided form and companion presence for an emitted FQN */
  def check(program: Program, units: List[Tree.ClassDef],
            forced: Set[(SymId, String)], shapeOf: String => Option[Surface.TypeShape]): List[Finding] =
    given Program = program
    val formOf: String => Option[String] = f => shapeOf(f).map(_.form)
    // census over whole program (including base units); subjects restricted to this run's units (D2)
    val declared = program.units.flatMap(nested)
    val all      = units.flatMap(nested)
    val mine     = all.map(_.symbol).toSet
    val clinitBearers: Map[SymId, Tree.ClassDef] =
      declared.filter(cd => declaresClinit(cd)).map(cd => cd.symbol -> cd).toMap
    val reentrant = reentrantBearers(program, clinitBearers)

    // forms that have no `new` trigger: object, enum (constants ARE companion members), trait/annotation
    val notInstantiable = Set("object", "enum-class", "enum", "trait", "annotation")
    val unforced = clinitBearers.toList.collect {
      case (s, cd) if mine(s) && !forced(s -> Instantiation) && !formOf(fqn(s)).exists(notInstantiable) =>
        val cyclic = reentrant.get(s)
        Finding(if cyclic.isDefined then Issue.ReentrantRefused else Issue.Unforced,
                fqn(s), cyclic.map(fqn).getOrElse(fqn(s)), formOf(fqn(s)).getOrElse("?"), cd.origin)
    }

    // subclass trigger: only types with a companion; nearest bearing ancestor only
    val parents = item7Parents(program)
    def nearest(front: List[SymId], seen: Set[SymId]): Option[SymId] =
      val next = front.flatMap(parents.getOrElse(_, Nil)).filterNot(seen).distinct
      next.find(clinitBearers.contains) match
        case Some(a)           => Some(a)
        case _ if next.isEmpty => None
        case _                 => nearest(next, seen ++ next)
    val subclass = all.collect {
      case cd if shapeOf(fqn(cd.symbol)).exists(_.companion) && !forced(cd.symbol -> SubclassInit) =>
        nearest(List(cd.symbol), Set(cd.symbol))
          .map(a => Finding(Issue.SubclassInitUnforced, fqn(cd.symbol), fqn(a),
            formOf(fqn(cd.symbol)).getOrElse("?"), cd.origin))
    }.flatten

    (unforced ++ subclass).sortBy(f => (f.issue.toString, f.owner))

  /** Every class declared under `cd`, itself included. */
  private def nested(cd: Tree.ClassDef)(using Program): List[Tree.ClassDef] =
    StandardTraversal.allClassDefs(cd)

  /** Parent edges JLS 12.4.1 item 7 traverses: superclasses plus superinterfaces with default
    * methods. Interfaces without defaults are excluded (java does not initialise them). */
  def item7Parents(program: Program): Map[SymId, List[SymId]] =
    given Program = program
    val declared = program.units.flatMap(nested).map(cd => cd.symbol -> cd).toMap
    def initialisedWithImplementor(cd: Tree.ClassDef): Boolean =
      !program.symbolOf(cd.symbol).exists(_.flags.isTrait) ||
        cd.body.exists {
          case d: Tree.DefDef =>
            d.rhs.isDefined && program.symbolOf(d.symbol).exists(s => !s.flags.isStatic)
          case _ => false
        }
    declared.map((s, cd) =>
      s -> parentSyms(cd).filter(p => declared.get(p).forall(initialisedWithImplementor)))

  /** Bearers whose force would be re-entrant, each mapped to the bearer it cycles through.
    * Self-edges excluded (scala survives those). Only mutual cycles are re-entrant.
    * Graph edges: `new C` or static read of C in step-9 members. // ENGINE-LIMITS K22 */
  def reentrantBearers(program: Program, bearers: Map[SymId, Tree.ClassDef]): Map[SymId, SymId] =
    given Program = program
    if bearers.sizeIs < 2 then Map.empty
    else
      val edges: Map[SymId, Set[SymId]] =
        bearers.map((s, cd) => s -> (initEdges(cd, bearers.keySet) - s))
      bearers.keys.flatMap { b =>
        def reaches(from: SymId): Boolean =
          var seen  = Set(from)
          var front = List(from)
          var hit   = from == b
          while front.nonEmpty && !hit do
            val next = front.flatMap(edges.getOrElse(_, Set.empty)).distinct
            if next.contains(b) then hit = true
            front = next.filterNot(seen)
            seen  = seen ++ front
          hit
        edges.getOrElse(b, Set.empty).find(reaches).map(b -> _)
      }.toMap

  /** Bearers reachable from `cd`'s step-9 members via `new C` or static read of C. */
  private def initEdges(cd: Tree.ClassDef, bearers: Set[SymId])(using program: Program): Set[SymId] =
    val out = collection.mutable.Set.empty[SymId]
    def owningBearer(s: SymId): Option[SymId] =
      def climb(x: SymId, fuel: Int): Option[SymId] =
        if fuel <= 0 then scala.None
        else if bearers(x) then Some(x)
        else program.symbolOf(x).map(_.owner).filter(_ != SymId.None).flatMap(climb(_, fuel - 1))
      climb(s, 16)
    def headSym(t: TypeRepr): Option[SymId] = t match
      case TypeRepr.TypeRef(_, s)      => Some(s)
      case TypeRepr.AppliedType(tc, _) => headSym(tc)
      case _                           => scala.None
    val bodies = cd.body.filter(m => stepNine(List(m))).flatMap {
      case d: Tree.DefDef => d.rhs
      case v: Tree.ValDef => v.rhs
      case _              => scala.None
    }
    bodies.foreach { b =>
      StandardTraversal.scanTerm(b, ()) { (_, t) =>
        t match
          case n: Tree.New    => headSym(n.tpt.tpe).filter(bearers).foreach(out += _)
          case r: Tree.Ident  => if isStaticRef(r.sym) then owningBearer(r.sym).foreach(out += _)
          case r: Tree.Select => if isStaticRef(r.sym) then owningBearer(r.sym).foreach(out += _)
          case _              => ()
        ()
      }
    }
    out.toSet

  private def isStaticRef(s: SymId)(using program: Program): Boolean =
    program.symbolOf(s).exists(_.flags.isStatic)

  private def declaresClinit(cd: Tree.ClassDef)(using Program): Boolean = stepNine(cd.body)

  /** Does this member list contain JLS 12.4.2 step-9 content? Shared predicate for this census
    * and `TirEmitter`. Matches `<clinit>` DefDefs and static ValDefs with non-constant initialisers.
    * Excludes constant variables (javac inlines them; no trigger). // ENGINE-LIMITS K22 */
  def stepNine(members: List[Statement])(using program: Program): Boolean =
    members.exists {
      case d: Tree.DefDef => program.symbolOf(d.symbol).exists(s => s.name == "<clinit>" && s.flags.isStatic)
      case v: Tree.ValDef =>
        v.rhs.isDefined && program.symbolOf(v.symbol).exists(s => s.flags.isStatic && !constantVariable(v, s))
      case _ => false
    }

  /** JLS 4.12.4 constant variable: `static final`, primitive or String, literal initialiser.
    * Shared with the emitter's `inline val` arm. */
  def constantVariable(v: Tree.ValDef, s: Symbol)(using program: Program): Boolean =
    s.flags.isStatic && s.flags.isFinal && !s.flags.isMutable &&
      (v.rhs match { case Some(_: Tree.Literal) => true; case _ => false }) &&
      (v.tpt.tpe match
        case TypeRepr.TypeRef(_, x) =>
          val n = program.symbolOf(x).map(_.fullName).getOrElse("")
          CtorFunnel.primitiveTypeNames(n) || n == "java.lang.String"
        case _ => false)

  private def parentSyms(cd: Tree.ClassDef): List[SymId] =
    def headSym(t: TypeRepr): Option[SymId] = t match
      case TypeRepr.TypeRef(_, s)      => Some(s)
      case TypeRepr.AppliedType(tc, _) => headSym(tc)
      case _                           => None
    cd.parents.flatMap { case tt: TypeTree => headSym(tt.tpe); case term: Term => headSym(term.tpe) }

  private def fqn(s: SymId)(using program: Program): String =
    program.symbolOf(s).map(_.fullName).getOrElse("?")

  def summary(fs: List[Finding]): String =
    if fs.isEmpty then "  none"
    else
      fs.groupBy(_.issue).toList.sortBy((_, v) => -v.size).map { (issue, vs) =>
        val head  = s"  ${vs.size} × $issue\n  ${Issue.classification(issue)}"
        val sites = vs.sortBy(f => (f.origin.javaPath, f.origin.line)).take(10).map("    " + _.render)
        (head :: sites).mkString("\n")
      }.mkString("\n")
