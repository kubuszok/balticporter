package balticporter.tir

/** Every java `static { }` block the port emitted into a companion `object` that nothing
  * initialises.
  *
  * THE DEFECT. Java's class-initialisation trigger list (JLS 12.4.1) is short and exact: a class
  * `T` is initialised on the first `new T`, on the first access to a static member `T` DECLARES (a
  * compile-time constant excepted — that one is inlined and triggers nothing, `JS-C08`), on the
  * initialisation of one of `T`'s subclasses, on certain reflective actions, and on `main`. Scala
  * has no such list at all: a companion `object` initialises when something first touches the
  * OBJECT. `new T(…)` does not, and neither does any member of the class — so the block is emitted,
  * faithfully, and never runs.
  *
  * Where the block's effect is a REGISTRATION the failure is entirely silent: every later lookup
  * answers "not registered", and a library turns that into a plausible wrong answer rather than an
  * error. No omission exists (the block IS in the output), the port compiles, and every other check
  * counts the same (`ENGINE-LIMITS.md` K22).
  *
  * '''Why this is not the emitter's own answer read back.''' The census of `static { }` blocks is
  * taken HERE, from the trees — a `<clinit>` member is what the frontend calls one — and the
  * emitter contributes only the set of types it actually forced. So `_ => false` reproduces the
  * un-repaired engine on the same trees, and the two disagree exactly when a block reaches the
  * output through a path that attaches no trigger. The one thing that must come from the emitter is
  * the FORM it chose, because that is decided inline from whole-program reads and exists nowhere
  * else: an all-static class collapses to an `object`, and then there is no class to instantiate
  * and nothing is wrong.
  *
  * Declared nesting is the whole census: a java inner or anonymous class may not declare a static
  * initialiser at all (JLS 8.1.3), so a walk over class bodies misses none of them — the one place
  * `CLAUDE.md` §3's "a walk over class bodies finds no anonymous class" does not bite.
  *
  * '''Its honest limit''', stated because an over-claimed guarantee is how a mechanism stops being
  * audited: this counts triggers that do not FIRE, and says nothing about WHEN one fires relative to
  * java. Java initialises the class before `<init>` runs at all, the superclass constructor
  * included, and a Scala class-body statement runs after it — observable only where a super
  * constructor calls a method this class overrides which reads this class's statics. No criterion
  * for that is cheaper than the whole-program analysis it would take, and an over-approximate review
  * list is noise (`CLAUDE.md` §1), so `ENGINE-LIMITS.md` K22 states it and nothing counts it.
  */
object ClassInitTriggerCheck:

  val Name = "class-init-trigger"

  enum Issue:
    /** a `static { }` block in a type emitted as a CLASS, with no statement forcing its companion:
      * java's instantiation trigger, unreproduced. */
    case Unforced
    /** the SUBCLASS trigger: this type's companion is reachable by a static access, which in java
      * initialises it and therefore its superclass — whose `static { }` block the port does not run
      * on that path. */
    case SubclassInitUnforced

  object Issue:
    def classification(i: Issue): String = i match
      case Unforced =>
        "§1(a) ENGINE: reproducing java's class-initialisation triggers is a universal " +
          "java-vs-scala fact, never per-library policy. `TirEmitter.forceCompanion` puts a " +
          "`val _ = <type>` at the head of the class body for every type carrying a `static { }` " +
          "block. A finding here means the block reached the output through a rendering that has " +
          "no class body to put it in — fix the emitter, not the port."
      case SubclassInitUnforced =>
        "§1(a) ENGINE: JLS 12.4.1 initialises a class when one of its SUBCLASSES is initialised, " +
          "and a subclass with statics of its own is initialised by a bare `S.member` read that " +
          "touches no instance. `TirEmitter.forceCompanion` answers it from the subclass's own " +
          "companion body — fix the emitter, not the port."

  /** @param owner    the type carrying, or inheriting, the block
    * @param declarer where the `static { }` block is declared — the same as `owner` for
    *                 [[Issue.Unforced]] and an ancestor for [[Issue.SubclassInitUnforced]]
    */
  final case class Finding(issue: Issue, owner: String, declarer: String, form: String, origin: Origin):
    def detail: String = issue match
      case Issue.Unforced =>
        s"`$owner` is emitted as a `$form` carrying a java `static { }` block, and nothing " +
          s"initialises the companion the block lowered into: `new ${owner.split('.').last}(…)` " +
          s"runs the constructor and never touches the object, so the block's effects — a " +
          s"registration, a factory, a table — silently do not happen"
      case Issue.SubclassInitUnforced =>
        s"`$owner` inherits a java `static { }` block from `$declarer` and has a companion of its " +
          s"own, so java initialises `$declarer` first on any route that initialises `$owner` — a " +
          s"bare `$owner.member` read above all (JLS 12.4.1 item 7) — and initialising this port's " +
          s"`$owner` object reaches no other object"
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

    // ---- the instantiation trigger ----
    //
    // Only a type emitted as a `class` has one. An `object` is the all-static collapse — there is
    // nothing to instantiate and every route in touches the object; an `enum-class`'s constants
    // ARE companion members, so any use of one initialises it; a `trait` cannot carry a java
    // `static { }` at all (JLS 9.1.1) and is reported if one ever arrives, because a trait body
    // statement would run at every implementor's initialisation, which is MORE than java does.
    val selfInitialising = Set("object", "enum-class")
    val unforced = clinitBearers.toList.collect {
      case (s, cd) if mine(s) && !forced(s -> Instantiation) && !formOf(fqn(s)).exists(selfInitialising) =>
        Finding(Issue.Unforced, fqn(s), fqn(s), formOf(fqn(s)).getOrElse("?"), cd.origin)
    }

    // ---- the subclass trigger ----
    //
    // Asked of a type the emitter gave a COMPANION, and of no other: an object that is never
    // initialised runs nothing, so what is at stake is exactly "when `object S` initialises, has
    // the ancestor's `static { }` run". A type with no companion has no `S.member` to read and is
    // reached only by `new S`, which runs the ancestor's own class-body force through the super
    // constructor. Only the NEAREST bearing ancestor is asked for, because that ancestor's own
    // companion owes the same line for ITS nearest — one row per type, not one per chain.
    val parents = declared.map(cd => cd.symbol -> parentSyms(cd)).toMap
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
  private def nested(cd: Tree.ClassDef): List[Tree.ClassDef] =
    cd :: cd.body.collect { case c: Tree.ClassDef => nested(c) }.flatten

  /** a java `static { }` block — the frontend's `<clinit>`, never the instance `<initblock>`: the
    * two are different members running at different times and only one has this problem. */
  private def declaresClinit(cd: Tree.ClassDef)(using program: Program): Boolean =
    cd.body.exists {
      case d: Tree.DefDef => program.symbolOf(d.symbol).exists(s => s.name == "<clinit>" && s.flags.isStatic)
      case _              => false
    }

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
