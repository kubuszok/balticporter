package balticporter.tir

/** Every java CLASS INITIALISER the port emitted into a companion `object` that nothing
  * initialises.
  *
  * THE DEFECT. Java's class-initialisation trigger list (JLS 12.4.1) is short and exact: a class
  * `T` is initialised on the first `new T`, on the first access to a static member `T` DECLARES (a
  * compile-time constant excepted — that one is inlined and triggers nothing, `JS-C08`), on the
  * initialisation of one of `T`'s subclasses, on certain reflective actions, and on `main`. Scala
  * has no such list at all: a companion `object` initialises when something first touches the
  * OBJECT. `new T(…)` does not, and neither does any member of the class — so the initialiser is
  * emitted, faithfully, and never runs.
  *
  * Where its effect is a REGISTRATION the failure is entirely silent: every later lookup answers
  * "not registered", and a library turns that into a plausible wrong answer rather than an error.
  * No omission exists (the code IS in the output), the port compiles, and every other check counts
  * the same (`ENGINE-LIMITS.md` K22).
  *
  * '''The census is JLS 12.4.2 STEP 9, never a node kind.''' Java's class initialiser runs the
  * static FIELD INITIALISERS and the `static { }` BLOCKS as one sequence, in textual order — the
  * same pairing §4.55 records for the instance half — so `static { Registry.register(…); }` and
  * `private static final boolean R = Registry.register(…)` are the SAME construct written two ways,
  * and java initialises `T` at `new T` for either. Keyed on the block alone this check answered for
  * one of them and reported 0 for the other, on trees that had the defect. [[stepNine]] is what
  * both this census and the emitter's repair ask, so the two cannot disagree about which types owe
  * a trigger.
  *
  * What that widening must NOT reach is a COMPILE-TIME CONSTANT, and the exclusion is not a
  * softening — it is `JS-C08` and it is what keeps the repair safe against §4.4's `Vector3`/
  * `Matrix4` cycle. javac inlines a constant variable's every use, so reading one initialises
  * nothing at all, and this port emits it `inline val` for exactly that reason. A trigger there
  * would be a trigger java never had.
  *
  * '''Why this is not the emitter's own answer read back.''' The census is taken HERE, from the
  * trees, and the emitter contributes only the set of types it actually forced. So `_ => false`
  * reproduces the un-repaired engine on the same trees, and the two disagree exactly when a class
  * initialiser reaches the output through a path that attaches no trigger. The one thing that must
  * come from the emitter is the FORM it chose, because that is decided inline from whole-program
  * reads and exists nowhere else: an all-static class collapses to an `object`, and then there is
  * no class to instantiate and nothing is wrong.
  *
  * Declared nesting is the whole census: a java inner or anonymous class may not declare a static
  * initialiser at all (JLS 8.1.3), so a walk over class bodies misses none of them — the one place
  * `CLAUDE.md` §3's "a walk over class bodies finds no anonymous class" does not bite.
  *
  * '''Its honest limit''', stated because an over-claimed guarantee is how a mechanism stops being
  * audited: this counts triggers that do not FIRE, and says nothing about WHEN one fires relative
  * to java. Java initialises the class before `<init>` runs at all, the superclass constructor
  * included, and a Scala class-body statement runs after it. The case that sounds like the problem
  * — a super constructor calling a method this class overrides which reads this class's statics —
  * largely SELF-HEALS, because that read is an access to the companion and initialises it on the
  * spot. What does not heal is mutual ordering through a THIRD PARTY: the superclass constructor
  * asks a registry what is registered, and this class's initialiser is what registers it, so java
  * answers "yes" and the port answers "no" — nothing on either path touches the object early
  * enough. No criterion for that is cheaper than the whole-program analysis it would take, and an
  * over-approximate review list is noise (`CLAUDE.md` §1), so `ENGINE-LIMITS.md` K22 states it and
  * nothing counts it.
  */
object ClassInitTriggerCheck:

  val Name = "class-init-trigger"

  enum Issue:
    /** a java class initialiser in a type emitted as a CLASS, with no statement forcing its
      * companion: java's instantiation trigger, unreproduced. */
    case Unforced
    /** the SUBCLASS trigger: this type's companion is reachable by a static access, which in java
      * initialises it and therefore its superclass — whose class initialiser the port does not run
      * on that path. */
    case SubclassInitUnforced
    /** the trigger the repair DECLINED: forcing this companion would re-enter an initialisation
      * that is already in progress, and scala's module init does not survive what java's tolerates.
      * A refusal, counted — never a gap. */
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

  /** the name a finding gives the construct, which is the reader's first question: java writes the
    * same class initialiser two ways and only one of them looks like one. */
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

  /** the two triggers this engine can attach, named the way the porter note names them so a
    * finding and the emitted `/* porter: forced-class-init trigger=… */` read as one fact. */
  val Instantiation = "instantiation"
  val SubclassInit  = "subclass-init"

  /** @param forced  which (type, trigger) pairs the emitter attached
    *                ([[balticporter.emit.TirEmitter.forcedClassInits]])
    * @param shapeOf what the emitter WROTE for an emitted FQN. Two fields are read and both are
    *                facts only the emitter has: `form` (an all-static class collapses to an
    *                `object`, decided inline from four whole-program reads) and `companion`
    *                (whether an object exists at that name at all). Absent means the run did not
    *                emit the type.
    */
  def check(program: Program, units: List[Tree.ClassDef],
            forced: Set[(SymId, String)], shapeOf: String => Option[Surface.TypeShape]): List[Finding] =
    given Program = program
    val formOf: String => Option[String] = f => shapeOf(f).map(_.form)
    // THE SUBJECTS ARE THIS RUN'S OWN UNITS AND THE CENSUS IS THE WHOLE PROGRAM — different
    // questions, and `ENGINE-LIMITS.md` D2 governs only the first. A dependent's model CONTAINS its
    // base's units (§1.5), so the ancestor that declares the `static { }` may live in the base; a
    // census scoped to the emitted units would not see it, while the EMITTER — which walks
    // `program.units` — does. The two would then disagree about which types owe the line, and the
    // one that would be silent is the one that reports. This is the shape §4.56 is about read at a
    // check: derive the fact from the same place the repair derives it, never from the narrower
    // list that happens to be in hand.
    val declared = program.units.flatMap(nested)
    val all      = units.flatMap(nested)
    val mine     = all.map(_.symbol).toSet
    val clinitBearers: Map[SymId, Tree.ClassDef] =
      declared.filter(cd => declaresClinit(cd)).map(cd => cd.symbol -> cd).toMap
    // …and which of them the repair had to DECLINE, derived here from the same function the
    // emitter decides with, so a refusal is reported as a refusal and never as a gap.
    val reentrant = reentrantBearers(program, clinitBearers)

    // ---- the instantiation trigger ----
    //
    // ONLY A FORM WITH A `new` HAS ONE, and the exclusion list is derived from that question and
    // not from what the emitter happens to decline. An `object` is the all-static collapse — there
    // is nothing to instantiate and every route in touches the object; an `enum-class`'s constants
    // ARE companion members, so any use of one initialises it; and a `trait` or an `annotation` is
    // a java INTERFACE, which cannot be instantiated at all.
    //
    // The interface is the one this widening added, and it is not an approximation — it is exact,
    // twice. JLS 9.1.1 forbids a `static { }` block in an interface but says nothing about a FIELD,
    // and an interface field with a non-constant initialiser (`ArraySupplier.ANY = size -> …`) IS
    // step-9 content; so the census correctly sees it and the trigger correctly does not exist:
    // java's only route into an interface's initialisation is a use of a non-constant static it
    // declares (JLS 12.4.1), which in scala is an access to the companion — already exact — and
    // item 7 does NOT reach a superinterface unless that interface declares a default method. Read
    // as a defect these were 4 findings on libGDX core (`GL30`, `GL31`, `GLErrorListener`,
    // `ArraySupplier`), every one of them a type nothing can `new`.
    // BOTH enum forms are here and for one reason: an enum's constants ARE companion members in
    // either shape — `case object`s the emitter writes (`enum-class`), or the scala 3 desugaring's
    // own (`enum`, `EnumShape`) — so any use of one initialises the companion. A set naming only the
    // shape that existed when it was written would have started reporting every conforming enum as
    // an unforced bearer the day the second shape shipped, which is §4.56's stale filter exactly.
    val notInstantiable = Set("object", "enum-class", "enum", "trait", "annotation")
    val unforced = clinitBearers.toList.collect {
      case (s, cd) if mine(s) && !forced(s -> Instantiation) && !formOf(fqn(s)).exists(notInstantiable) =>
        val cyclic = reentrant.get(s)
        Finding(if cyclic.isDefined then Issue.ReentrantRefused else Issue.Unforced,
                fqn(s), cyclic.map(fqn).getOrElse(fqn(s)), formOf(fqn(s)).getOrElse("?"), cd.origin)
    }

    // ---- the subclass trigger ----
    //
    // Asked of a type the emitter gave a COMPANION, and of no other: an object that is never
    // initialised runs nothing, so what is at stake is exactly "when `object S` initialises, has
    // the ancestor's `static { }` run". A type with no companion has no `S.member` to read and is
    // reached only by `new S`, which runs the ancestor's own class-body force through the super
    // constructor. Only the NEAREST bearing ancestor is asked for, because that ancestor's own
    // companion owes the same line for ITS nearest — one row per type, not one per chain.
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

  /** every class DECLARED under `cd`, itself included. */
  private def nested(cd: Tree.ClassDef)(using Program): List[Tree.ClassDef] =
    // `allClassDefs`, not a `cd.body` recursion — see `TirEmitter.allDeclaredClasses`: a class body
    // is one node short of java, because a method-LOCAL class (`JS-C30`) is a block statement.
    StandardTraversal.allClassDefs(cd)

  /** THE ANCESTOR EDGES JLS 12.4.1 ITEM 7 ACTUALLY TRAVERSES, for the whole program — the walk both
    * the subclass census and the emitter's `nearestClinitAncestor` climb.
    *
    * "Initialising a class initialises its superclasses" is the half everybody quotes; the JLS
    * sentence continues '''"…as well as any superinterfaces that declare any default methods"'''. An
    * interface with no default method is NOT initialised when an implementor is, so an edge to one
    * is an edge java does not have — and a force across it is a trigger java never had, which is the
    * one thing this repair may not add. Invisible until the census widened past the `static { }`
    * block, because JLS 9.1.1 keeps a block out of an interface and nothing else made one a bearer;
    * with FIELDS in the census, libGDX's `GL31Interceptor` promptly acquired a
    * `val _ = sge.graphics.GL30` that java's item 7 does not sanction.
    *
    * A parent this program does not DECLARE is kept: its interface-ness is a fact about a class
    * file, the walk cannot see its own parents anyway, and it is never a bearer (the census is over
    * `program.units`), so keeping it is a no-op that reads honestly. */
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

  /** THE BEARERS WHOSE FORCE WOULD BE RE-ENTRANT, each mapped to the bearer it comes back through
    * — `ENGINE-LIMITS.md` K22's second face, and the reason the widened repair is not unconditional.
    *
    * Java runs a cyclic pair of class initialisers and SURVIVES: JLS 12.4.2 step 3 says a thread
    * that is already initialising `T` and re-enters `T` simply proceeds, reading whatever `T`'s
    * statics hold at that moment. Scala has no such rule for a companion, so a force that re-enters
    * an initialisation in progress reads a half-built module and the initialiser throws — measured
    * on libGDX's `Vector3`/`Matrix4` pair (`Vector3.tmpMat = new Matrix4()`,
    * `Matrix4.l_vez = new Vector3()`) as `ExceptionInInitializerError` at first use: 6 newly-failing
    * tests, 20 more never reached, from a repair whose whole argument was that it adds no trigger
    * java lacks. It adds none — and one of java's own is not reproducible here.
    *
    * The graph is over BEARERS only, and an edge `B -> C` is drawn where B's own step-9 members
    * INSTANTIATE C (which is where the force fires) or READ A STATIC of C (which touches C's
    * companion in scala whether or not anything was forced — an edge that predates this repair and
    * has to be in the graph, because a cycle needs only ONE new edge to close). Constructor and
    * method bodies are NOT followed: an over-approximation here does not leave a defect, it declines
    * a repair that would have worked, and the population it would decline is unbounded.
    *
    * '''A SELF-EDGE IS NOT RE-ENTRANCE''', and the distinction is the whole reason this is a graph
    * and not a "does the initialiser mention its own type" test. An initialiser touching its own
    * type is what every initialiser does — `static { hits = 1; }`, `static final Matrix4 tmpMat =
    * new Matrix4()` — and scala survives it exactly as java does, because dotty assigns `MODULE$`
    * as the FIRST statement of the module constructor: a re-entrant read gets the same half-built
    * object java's step 3 hands back. What has no counterpart is the MUTUAL cycle, where the second
    * module's `MODULE$` has not been assigned at all yet. Included, the self-edge declined the
    * trigger for every plain `static { }` bearer in the corpus, which is the repair switched off
    * under the name of a refusal. */
  def reentrantBearers(program: Program, bearers: Map[SymId, Tree.ClassDef]): Map[SymId, SymId] =
    given Program = program
    if bearers.sizeIs < 2 then Map.empty
    else
      val edges: Map[SymId, Set[SymId]] =
        bearers.map((s, cd) => s -> (initEdges(cd, bearers.keySet) - s))
      // a bearer is re-entrant iff it can reach ITSELF through at least one other bearer; the value
      // is the first step of that walk, which is what a reader needs to see the pair.
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

  /** the bearers `cd`'s OWN step-9 members reach directly — a `new C`, or a read of a static C
    * declares. Both touch `object C` in the emitted scala; only the first is new. */
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

  /** DOES THIS TYPE'S CLASS INITIALISER DO ANYTHING? — JLS 12.4.2 step 9, and the one question both
    * this census and `TirEmitter`'s repair ask.
    *
    * Step 9 runs a class's static FIELD INITIALISERS and its `static { }` BLOCKS as a single
    * sequence in textual order, which makes them one construct with two spellings; java initialises
    * the class at `new T` for either. Asked of a member list rather than of a `ClassDef` because the
    * emitter asks it of the LOWERED statics it is about to write into the companion, and this check
    * asks it of the declared body — the same predicate over two lists is what stops the two from
    * drifting (`ENGINE-LIMITS.md` K22).
    *
    * THREE members are step-9 content and one is not:
    *
    *   - a `<clinit>` DefDef — the frontend's name for a `static { }` block. Never the instance
    *     `<initblock>`, which is step 4 of JLS 12.5 and runs at construction in both languages;
    *   - a static `ValDef` WITH an initialiser — the registration written as a field, and the case
    *     a block-shaped census could not see;
    *   - …unless it is a java CONSTANT VARIABLE, which javac inlines and this port emits
    *     `inline val`: reading one initialises nothing in either language (`JS-C08`), so a trigger
    *     there would be a trigger java never had, and it is the exclusion that keeps this repair
    *     out of §4.4's `Vector3`/`Matrix4` cycle;
    *   - a static `ValDef` with NO initialiser is the JVM default and compiles to no code at all.
    */
  def stepNine(members: List[Statement])(using program: Program): Boolean =
    members.exists {
      case d: Tree.DefDef => program.symbolOf(d.symbol).exists(s => s.name == "<clinit>" && s.flags.isStatic)
      case v: Tree.ValDef =>
        v.rhs.isDefined && program.symbolOf(v.symbol).exists(s => s.flags.isStatic && !constantVariable(v, s))
      case _ => false
    }

  /** a java CONSTANT VARIABLE (JLS 4.12.4): `static final`, of primitive or `String` type, with a
    * constant initialiser. ONE definition, read by [[stepNine]] and by the emitter's `inline val`
    * arm — the safety argument above is exactly that those two agree, and two copies of this test
    * is how they would stop agreeing. */
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
