package balticporter.transform

import balticporter.core.{MergeablePolicy, PolicyFinding, PolicyIssue, PolicyReport, PolicySource, SurfacePolicy}
import balticporter.tir.*

/** An array whose element type is an owner's TYPE PARAMETER is allocated, copied and cleared through
  * a WITNESS type class (`lowlevel.MkArray`-shaped) instead of `new Object[]`/`java.util.Arrays`, and
  * the parameter's `<: java.lang.Object` bound is dropped so a primitive or opaque element is
  * admissible. §1(b): mechanism universal; witness, member names and subjects are policy; empty
  * `subjectTypes` is the no-op (`CLAUDE.md` §1(b), §4.56; `ENGINE-LIMITS.md` K41). */
final class ElementWitnessTransform(
    /** the witness type's FQN — `lowlevel.MkArray`. Empty is the no-op. */
    val witness: String = "",
    /** the witness's member names, so a library naming them differently needs no engine change. */
    val members: ElementWitnessTransform.Members = ElementWitnessTransform.Members.Default,
    /** upstream class FQN -> the 0-based TYPE-PARAMETER INDEXES whose arrays move onto the witness.
      * Empty map is the no-op: no clause, no rewrite, no bound moved, no `policy=` segment. */
    val subjectTypes: Map[String, List[Int]] = Map.empty,
    /** the declarations that LOSE java's implicit `<: java.lang.Object` bound — a SUBJECT at its
      * element positions, any other named type at all of them, and every method they declare.
      * Apart from the subject set on purpose: a table that reads `null` as SLOT EMPTY keeps the
      * bound (`ElementWitnessCheck.Issue.OccupancySentinel`). A bound java WROTE is never dropped. */
    val dropBound: Set[String] = Set.empty,
    /** A library's own DEFAULT ARRAY FACTORY, which the witness subsumes: member key -> the Scala
      * that replaces a call to it, with one hole, `{elem}`. Read at an ARGUMENT position, so the
      * element type comes from the CALLEE'S FORMAL and the replacement can name it — a fixed
      * template resolved by inference instead resolves against the wrong scope in a constructor
      * delegation, and Scala Native refuses the `this` it needs (PROGRESS.md §13.29). */
    val defaultSuppliers: Map[String, String] = Map.empty,
    /** The witness for an element type that KEEPS java's `Object` bound, as Scala with one hole,
      * `{elem}`. A class the threading cannot reach — it is not a subject, and its element type is
      * still `<: java.lang.Object` — constructs a subject that now asks for a clause; this is the
      * value it takes without taking a parameter (`ENGINE-LIMITS.md` CT7). Empty REFUSES and counts
      * (`UnhandledCreation`) rather than emitting a file that cannot compile. */
    val boxedWitness: Option[String] = None,
    /** WHERE the rule applies. This phase MINTS a clause, so the no-op default is `Only(Set.empty)`
      * — but `subjectTypes` already names every declaration, so the scope is a second, narrower
      * screen a dependent uses to hold a base's type back (CLAUDE.md §1(b), D12). */
    val scope: RuleScope = RuleScope.Only(Set.empty),
) extends Phase, Rewrite, PolicySource, PolicyBound, SurfacePolicy, MergeablePolicy:

  import ElementWitnessTransform.*

  def name: String = "type-class-array"

  /** policy keys are written in the UPSTREAM namespace; package rename runs LAST (§4.56). */
  override def runsBefore: Set[String] = Set("package-rename")

  /** every seam this retyping opened and could not close (CLAUDE.md §1(b)). */
  def accountedBy: Set[String] = Set(ElementWitnessCheck.Name)

  /** is this instance a no-op? Read by `PortRun` so an empty instance requires no lane. */
  def isNoOp: Boolean = witness.isEmpty || subjectTypes.isEmpty

  // ---- surface ------------------------------------------------------------------------------

  /** Fingerprint: witness, member names, subjects with their indexes, dropped bounds, scope.
    * EMPTY when nothing is configured — the §1(b) fingerprint no-op rule. */
  def surfaceFingerprint: String =
    if isNoOp then ""
    else
      val subj = subjectTypes.toList.sortBy(_._1).map((k, is) => s"$k[${is.sorted.mkString(",")}]").mkString(",")
      val db   = if dropBound.isEmpty then "" else s"|unbound=${dropBound.toList.sorted.mkString(",")}"
      val bw   = boxedWitness.fold("")(w => s"|boxed=$w")
      val ds   = if defaultSuppliers.isEmpty then "" else
        "|suppliers=" + defaultSuppliers.toList.sorted.map((k, v) => s"$k->$v").mkString(",")
      val sc   = if scope.fingerprint.isEmpty then "" else s"|${scope.fingerprint}"
      s"witness=$witness|members=${members.fingerprint}|subjects=$subj$db$bw$ds$sc"

  /** Independent subjects UNION; the same subject with DIFFERENT indexes REFUSES — two answers for
    * which parameter an array is keyed on is a choice, not a composition. */
  def mergedWith(later: Phase): Either[String, MergeablePolicy.Merged] = later match
    case o: ElementWitnessTransform =>
      val witnessClash =
        if witness.nonEmpty && o.witness.nonEmpty && witness != o.witness then
          List(s"""both modules name a witness type, "$witness" and "${o.witness}" — the emitted """ +
            "constructors take a clause of ONE type, and two of them is a surface that compiles " +
            "alone and cannot compile together")
        else Nil
      val memberClash =
        if members != o.members && witness.nonEmpty && o.witness.nonEmpty then
          List(s"""both modules name the witness's members, "${members.fingerprint}" and """ +
            s""""${o.members.fingerprint}" — the emitted call sites spell one of them""")
        else Nil
      val subjectClash = for
        (k, is) <- o.subjectTypes.toList.sortBy(_._1)
        mine    <- subjectTypes.get(k)
        if mine.sorted != is.sorted
      yield s"""both modules make "$k" a subject at different type-parameter indexes, """ +
        s"""${mine.sorted.mkString("[", ",", "]")} and ${is.sorted.mkString("[", ",", "]")}"""
      (witnessClash ++ memberClash ++ subjectClash) match
        case Nil =>
          Right(MergeablePolicy.Merged(
            new ElementWitnessTransform(
              witness      = if witness.nonEmpty then witness else o.witness,
              members      = if witness.nonEmpty then members else o.members,
              subjectTypes = subjectTypes ++ o.subjectTypes,
              dropBound    = dropBound ++ o.dropBound,
              boxedWitness = boxedWitness.orElse(o.boxedWitness),
              defaultSuppliers = defaultSuppliers ++ o.defaultSuppliers,
              scope        = scope),
            (o.subjectTypes.keySet ++ o.dropBound -- subjectTypes.keySet -- dropBound)
              .map(MergeablePolicy.subjectOf)))
        case whys => Left(whys.mkString("; ") +
          " — two answers for one key is an element representation whose outcome depends on which " +
          "manifest was read")
    case other =>
      Left(s"`${other.name}` is not an `ElementWitnessTransform`, so there is no policy to compose")

  /** every shared-surface SUBJECT this instance's policy is keyed on. */
  def subjects: Set[String] =
    (subjectTypes.keySet ++ dropBound ++ defaultSuppliers.keySet ++ scope.entries)
      .map(MergeablePolicy.subjectOf)

  // ---- policy, bound before the pipeline starts ----------------------------------------------

  private var records: List[PolicyBinder.Record] = Nil
  private var malformed: List[PolicyFinding]     = Nil
  private var boundSubjects: Map[SymId, List[Int]] = Map.empty
  private var boundUnbound: Set[SymId]             = Set.empty
  private var boundSuppliers: Map[SymId, String]   = Map.empty

  def bindPolicy(binder: PolicyBinder): Unit =
    val bad = collection.mutable.ListBuffer.empty[PolicyFinding]
    if witness.isEmpty && subjectTypes.nonEmpty then
      bad += PolicyFinding(name, "ElementWitnessTransform.witness", "", PolicyIssue.Malformed,
        "subjects are declared and no witness type is named, so every creation would be rewritten " +
          "onto nothing and the phase would silently do half its work. Name the type class's FQN")
    subjectTypes.toList.sortBy(_._1).foreach { (fqn, idxs) =>
      if idxs.isEmpty then
        bad += PolicyFinding(name, "ElementWitnessTransform.subjectTypes", fqn, PolicyIssue.Malformed,
          "no type-parameter index is given, so nothing on this declaration is an element type and " +
            "the entry can move nothing. Give the 0-based index of each parameter whose arrays the " +
            "witness allocates")
      if idxs.distinct.sizeIs != idxs.size then
        bad += PolicyFinding(name, "ElementWitnessTransform.subjectTypes", fqn, PolicyIssue.Malformed,
          s"the index list ${idxs.mkString("[", ",", "]")} repeats a position — the constructor " +
            "would take the same clause twice")
      binder.bindType(name, "ElementWitnessTransform.subjectTypes", fqn)
        .toOption.foreach(s => boundSubjects = boundSubjects.updated(s, idxs.distinct))
    }
    dropBound.toList.sorted.foreach { fqn =>
      binder.bindType(name, "ElementWitnessTransform.dropBound", fqn)
        .toOption.foreach(s => boundUnbound = boundUnbound + s)
    }
    defaultSuppliers.toList.sortBy(_._1).foreach { (key, tmpl) =>
      if !tmpl.contains("{elem}") then
        bad += PolicyFinding(name, "ElementWitnessTransform.defaultSuppliers", key, PolicyIssue.Malformed,
          "the replacement names no `{elem}` hole, so every call site would be given the same " +
            "element type and the one this slot asks for is the only correct answer")
      binder.bindMembers(name, "ElementWitnessTransform.defaultSuppliers", key,
                         Ownership.Either)
        .toOption.toList.flatten.flatMap(_.sym)
        .foreach(sym => boundSuppliers = boundSuppliers.updated(sym, tmpl))
    }
    scope.entries.foreach(e => binder.bindScope(name, "ElementWitnessTransform.scope", e))
    malformed = bad.toList
    records   = binder.recordsFor(name)

  def policyReport: PolicyReport = PolicyReport.fromBindings(records) ++ PolicyReport(malformed)

  // ---- refusals, recorded as the run makes them ----------------------------------------------

  private val refusalLog = collection.mutable.ListBuffer.empty[ElementWitnessCheck.Finding]

  /** every refusal this run made, restricted to the units it emits — a dependent's `Program` holds
    * its base's units too, and a refusal inside one of those is the base's finding (D2). */
  def refusals(program: Program, units: List[Tree.ClassDef]): List[ElementWitnessCheck.Finding] =
    val own = units.map(_.symbol).toSet
    def unitOf(s: SymId, fuel: Int = 64): SymId =
      if s == SymId.None || fuel <= 0 then SymId.None
      else if own.contains(s) then s
      else program.symbolOf(s).map(x => unitOf(x.owner, fuel - 1)).getOrElse(SymId.None)
    refusalLog.toList.filter(f => f.unit == SymId.None || unitOf(f.unit) != SymId.None)

  private def refuse(issue: ElementWitnessCheck.Issue, subject: String, detail: String,
                     origin: Origin, unit: SymId): Unit =
    refusalLog += ElementWitnessCheck.Finding(issue, subject, detail, origin, unit)

  // ---- the run ------------------------------------------------------------------------------

  override def run(program0: Program): Program =
    refusalLog.clear()
    if isNoOp || boundSubjects.isEmpty then return program0
    given Program = program0

    val ownedClasses: List[Tree.ClassDef] =
      program0.units.flatMap(StandardTraversal.allClassDefs(_))

    /** every type parameter of an owned class whose upper bound is java's IMPLICIT `Object` — the
      * only ones this phase may move (a bound java WROTE is a fact about the library, §4.56). */
    val objectBounded: Set[SymId] =
      ownedClasses.flatMap(_.tparams).filter(td => td.rhs.tpe match
        case TypeRepr.TypeBounds(_, hi) => ElementWitnessTransform.isObjectType(program0, hi)
        case _                          => false).map(_.symbol).toSet

    /** every APPLIED TYPE this program writes down — the edges the bound drop propagates along. */
    val applications: List[(SymId, List[TypeRepr])] =
      val acc = collection.mutable.ListBuffer.empty[(SymId, List[TypeRepr])]
      val scan = new Phase:
        def name: String = "type-class-array/applications"
        override def transformType(t: TypeRepr)(using Program): TypeRepr =
          t match
            case TypeRepr.AppliedType(TypeRepr.TypeRef(_, tc), args) => acc += (tc -> args)
            case _                                                   => ()
          t
      program0.units.foreach(u => StandardTraversal.mapClassDef(scan, u)(using program0))
      StandardTraversal.mapSymbols(scan, program0.symbols)(using program0)
      acc.toList

    val tparamsOfClass: Map[SymId, List[SymId]] =
      ownedClasses.map(cd => cd.symbol -> cd.tparams.map(_.symbol)).toMap

    /** THE TYPE PARAMETERS whose implicit `<: java.lang.Object` bound goes — the `dropBound`
      * entries' own (a SUBJECT's element positions, another entry's all), CLOSED under
      * application: a parameter handed an already-unbounded argument cannot keep a bound the
      * argument does not satisfy. An obligation this phase's own drop created (CLAUDE.md §1(b)). */
    val unboundClassTparams: Set[SymId] =
      var dropped = boundUnbound.toList.flatMap { cls =>
        program0.definitionOf(cls).collect { case cd: Tree.ClassDef => cd }.toList.flatMap { cd =>
          val idxs = boundSubjects.get(cls).getOrElse(cd.tparams.indices.toList)
          idxs.flatMap(cd.tparams.lift).map(_.symbol)
        }
      }.toSet
      var changed = true
      while changed do
        changed = false
        applications.foreach { (tc, args) =>
          tparamsOfClass.get(tc).foreach { tps =>
            args.zip(tps).foreach {
              case (TypeRepr.TypeRef(_, p), tp)
                  if dropped(p) && objectBounded(tp) && !dropped(tp) =>
                dropped = dropped + tp; changed = true
              case _ => ()
            }
          }
        }
      dropped

    /** Which type ARGUMENT positions of a subject class are element positions — the question a
      * method's own type parameter is asked to decide whether it inherits the class's answers. */
    def elementIndexes(cls: SymId): List[Int] = boundSubjects.getOrElse(cls, Nil)

    /** Is `tp` handed to a subject class at one of ITS element positions, anywhere in `types`? */
    def feedsSubject(types: List[TypeRepr], tp: SymId, unboundOnly: Boolean): Boolean =
      var hit = false
      def walk(t: TypeRepr): Unit = t match
        case TypeRepr.AppliedType(TypeRepr.TypeRef(_, c), args) =>
          if boundSubjects.contains(c) && (!unboundOnly || boundUnbound(c)) then
            elementIndexes(c).foreach(i => args.lift(i).foreach {
              case TypeRepr.TypeRef(_, s) if s == tp => hit = true
              case _                                 => ()
            })
          args.foreach(walk)
        case TypeRepr.AppliedType(tc, args)  => walk(tc); args.foreach(walk)
        case TypeRepr.AndType(l, r)          => walk(l); walk(r)
        case TypeRepr.OrType(l, r)           => walk(l); walk(r)
        case TypeRepr.ByNameType(u)          => walk(u)
        case TypeRepr.TypeBounds(lo, hi)     => walk(lo); walk(hi)
        case _                               => ()
      types.foreach(walk)
      hit

    /** the types a method DECLARES — its formals and its result. */
    def signatureTypes(d: Tree.DefDef): List[TypeRepr] =
      d.paramss.flatten.map(_.tpt.tpe) :+ d.returnTpt.tpe

    /** Does this method's BODY construct a subject class at its own type parameter `tp`? That is
      * the shape of a generic FACTORY (`Array.of`, `ObjectSet.with`), which the constructor
      * threading cannot see: the clause belongs on the METHOD, not on any class. A RAW `new` —
      * java wrote no type argument — owes the witness just as much, and is recognised where the
      * declaration leaves only one answer for which parameter it means. */
    def constructsSubjectAt(d: Tree.DefDef, tp: SymId, unboundOnly: Boolean): Boolean =
      d.rhs.exists { body =>
        StandardTraversal.scanTerm(body, false) { (acc, t) =>
          acc || (t match
            case Tree.New(tpt, _, _, _) =>
              val head = tpt.tpe match
                case TypeRepr.AppliedType(TypeRepr.TypeRef(_, c), _) => Some(c)
                case TypeRepr.TypeRef(_, c)                          => Some(c)
                case _                                               => scala.None
              head.exists { c =>
                boundSubjects.contains(c) && (!unboundOnly || boundUnbound(c)) && {
                  val atTp = tpt.tpe match
                    case TypeRepr.AppliedType(_, args) =>
                      elementIndexes(c).exists(i => args.lift(i).contains(TypeRepr.TypeRef(TypeRepr.NoPrefix, tp)))
                    case _ => false
                  // …or java wrote the construction RAW / at a capture, and the declaration leaves
                  // exactly one answer for which of its parameters the witness is owed for.
                  atTp || d.tparams.sizeIs == 1 || feedsSubject(signatureTypes(d), tp, unboundOnly)
                }
              }
            case _ => false)
        }
      }

    /** every method DECLARED anywhere in this program, with the class that declares it. */
    val allDefs: List[Tree.DefDef] =
      program0.units.flatMap(StandardTraversal.allClassDefs(_))
        .flatMap(_.body.collect { case d: Tree.DefDef => d })

    /** THE METHODS THAT TAKE THE CLAUSE, to a fixpoint: a generic factory constructing a subject,
      * and then any single-parameter method that CALLS one — threading stops at a call as surely
      * as at a `new`, and the caller has no other way to supply what the callee now asks for. */
    val threadedMethods: Map[SymId, List[Tree.TypeDef]] =
      var acc = allDefs.flatMap { d =>
        val ts = d.tparams.filter(td => constructsSubjectAt(d, td.symbol, unboundOnly = false))
        Option.when(ts.nonEmpty)(d.symbol -> ts)
      }.toMap
      var changed = true
      while changed do
        changed = false
        allDefs.foreach { d =>
          if !acc.contains(d.symbol) && d.tparams.sizeIs == 1 then
            val calls = d.rhs.exists(body => StandardTraversal.scanTerm(body, false) { (a, t) =>
              a || (t match { case ap: Tree.Apply => acc.contains(ap.method); case _ => false }) })
            if calls then { acc = acc.updated(d.symbol, d.tparams); changed = true }
        }
      acc

    val mint = new WitnessMinter(program0)

    // ---- the per-declaration rewrite ---------------------------------------------------------

    /** `scala.Predef.summon[<witness>[<elem>]]`, as TEXT: the clause is ANONYMOUS (a named context
      * parameter shadows an emitted root package, `TirEmitterMembers.givenParam`), so the only way
      * to reach it is resolution. The element's name is the emitted type parameter's own. */
    def summonOf(elem: SymId): String =
      val nm = program0.symbolOf(elem).map(_.name).getOrElse("?")
      s"scala.Predef.summon[$witness[$nm]]"

    def elemOf(t: TypeRepr, elems: Set[SymId]): Option[(SymId, TypeRepr)] = t match
      case at @ TypeRepr.AppliedType(TypeRepr.TypeRef(_, arr), List(TypeRepr.TypeRef(_, e)))
          if elems(e) && program0.symbolOf(arr).exists(_.fullName == "scala.Array") => Some(e -> at)
      case _ => None

    /** one level of the engine's own `asInstanceOf[Array[…]]` peeled off an argument, so the
      * witness's `Array[T]` formal is met by a cast to `Array[T]` and not by one to `Array[Object]`. */
    def unwrapArrayCast(t: Term): Term = t match
      case Tree.Typed(inner, tpt, _, _) if isArrayType(program0, tpt.tpe) => inner
      case other                                                          => other

    def recast(t: Term, arrayTpe: TypeRepr, at: Origin): Term =
      Tree.Typed(unwrapArrayCast(t), TypeTree(arrayTpe, at), arrayTpe, at)

    /** WHAT EACH CLASS MUST SUPPLY ITSELF — the element types at which a class this phase did NOT
      * thread constructs a subject whose constructors now take a clause (`ENGINE-LIMITS.md` CT7).
      * Rendered text, since the given is spliced: a type PARAMETER by its emitted name, anything
      * else as `java.lang.Object`, which is what java's own raw construction meant. */
    val needsBoxed = collection.mutable.Map.empty[SymId, collection.mutable.LinkedHashSet[String]]

    /** Does this class's CONSTRUCTORS already take a clause of the witness type? Asked of the tree
      * because another phase may have put it there (`GlobalsToImplicitsTransform.requiredGivens`),
      * and a second, minted given would make every construction inside ambiguous. */
    val carriesClause: SymId => Boolean =
      val hits = ownedClasses.filter { cd =>
        cd.body.exists {
          case d: Tree.DefDef => d.paramss.flatten.exists(v =>
            program0.symbolOf(v.symbol).exists(_.flags.isGiven) && (v.tpt.tpe match
              case TypeRepr.AppliedType(TypeRepr.TypeRef(_, w), _) =>
                program0.symbolOf(w).exists(_.fullName == witness)
              case _ => false))
          case _ => false
        }
      }.map(_.symbol).toSet
      hits.contains

    def elementText(t: TypeRepr, ownTparams: Set[SymId]): Option[String] = t match
      case TypeRepr.TypeRef(_, e) if ownTparams(e) && !unboundClassTparams(e) =>
        program0.symbolOf(e).map(_.name)
      case TypeRepr.TypeRef(_, e) if unboundClassTparams(e) => scala.None
      case _                                                => Some("java.lang.Object")

    final class BodyRewrite(elems: Map[SymId, TypeRepr], owner: String, unit: SymId,
                            dropped: Set[SymId], home: SymId, ownTparams: Set[SymId],
                            threadedHere: Boolean) extends Phase:
      def name: String = "type-class-array/body"
      private val elemSet = elems.keySet

      override def transformTerm(t: Term)(using Program): Term = t match
        // (1) a CREATION at an element-typed slot, under the cast java's own source wrote.
        case Tree.Typed(inner, tpt, _, o) =>
          erasedArrayCast(inner, tpt.tpe, o)
          elemOf(tpt.tpe, elemSet) match
            case Some((e, arrTpe)) => creation(inner, e, arrTpe, o).getOrElse(t)
            case None              => Tree.Typed(completeRawNew(inner, tpt.tpe, o), tpt, t.tpe, t.origin)
        // (2) a RELEASE WRITE — `x[i] = null` for the garbage collector, not for absence.
        case a @ Tree.Assign(Tree.ArrayAccess(arr, idx, _, _), rhs, _, o, _)
            if isNullLiteral(rhs) =>
          elemOf(arr.tpe, elemSet) match
            case Some((e, arrTpe)) =>
              Tree.Opaque.spliced(
                List(s"${summonOf(e)}.${members.nullOut}(", ", ", ")"),
                List(recast(arr, arrTpe, o), idx), a.tpe, o)
            case None => a
        // (3) a DEFAULT ARRAY FACTORY the witness subsumes, at an argument slot that names the
        //     element type — read off the CALLEE'S FORMAL, never off inference (see the key's doc).
        case ap: Tree.Apply if ap.args.exists(isSupplierCall) =>
          val formals = formalsOf(ap.method)
          ap.copy(args = ap.args.zipWithIndex.map { (a, i) =>
            if !isSupplierCall(a) then a
            else
              val tmpl = boundSuppliers(Tree.uncomment(a).asInstanceOf[Tree.Apply].method)
              slotElement(formals.lift(i)) match
                case Some(e) =>
                  Tree.Opaque(tmpl.replace("{elem}",
                    program0.symbolOf(e).map(_.name).getOrElse("?")), a.tpe, a.origin)
                case scala.None =>
                  refuse(ElementWitnessCheck.Issue.UnhandledCreation, owner,
                    "a default array factory is passed at a slot whose element type this " +
                      "declaration does not hold, so the witness cannot be named here",
                    a.origin, unit)
                  a
          })
        // (4) `java.util.Arrays.fill(x, null)` / `fill(x, from, to, null)` — the same, in bulk.
        case ap @ Tree.Apply(_, args, m, _, o) if isArraysMember(program0, m, "fill") =>
          // the receiver may already carry the engine's own `asInstanceOf[Array[Object]]`, so the
          // element is read through the cast as well as at it (CLAUDE.md §1(b): read the DECLARATION).
          val head = args.headOption
          head.flatMap(h => elemOf(h.tpe, elemSet).orElse(elemOf(unwrapArrayCast(h).tpe, elemSet))
                              .map(h -> _)) match
            case Some((arr, (e, arrTpe))) if args.sizeIs == 2 && isNullLiteral(args(1)) =>
              // java's two-argument `fill` covers the WHOLE array; the witness takes a range, so
              // the receiver is read twice. Legal only for a STABLE PATH — anything else would
              // evaluate an effect java evaluated once, and is left alone and counted instead.
              if !isStablePath(arr) then
                refuse(ElementWitnessCheck.Issue.UnhandledCreation, owner,
                  "`Arrays.fill(a, null)` over a receiver that is not a stable path: the witness " +
                    "clears a RANGE, so `a` would be evaluated twice where java evaluated it once",
                  o, unit)
                ap
              else
                val h = Tree.Opaque.hole(0)
                Tree.Opaque(s"${summonOf(e)}.${members.nullOutRange}($h, 0, $h.length)",
                            ap.tpe, o, List(recast(arr, arrTpe, o)))
            case Some((arr, (e, arrTpe))) if args.sizeIs == 4 && isNullLiteral(args(3)) =>
              Tree.Opaque.spliced(
                List(s"${summonOf(e)}.${members.nullOutRange}(", ", ", ", ", ")"),
                List(recast(arr, arrTpe, o), args(1), args(2)), ap.tpe, o)
            case _ => ap
        // (5) REFERENCE IDENTITY on an operand whose bound this phase dropped. `eq`/`ne` live on
        //     `AnyRef`; the frontend ascribed only what java typed `Object` (SpoonTirBodyExprs
        //     `referenceIdentity`), which a type VARIABLE never is. The obligation is this phase's.
        case Tree.Apply(Tree.Select(l, op, st, so), List(r), m, tp, o)
            if isRefIdentity(program0, m) && (needsRef(l) || needsRef(r)) =>
          Tree.Apply(Tree.Select(asAnyRef(l), op, st, so), List(asAnyRef(r)), m, tp, o)
        case other => other

      private def needsRef(t: Term): Boolean = t.tpe match
        case TypeRepr.TypeRef(_, s) => unboundClassTparams(s)
        case _                      => false

      private def asAnyRef(t: Term): Term =
        if !needsRef(t) then t
        else
          val ar = TypeRepr.TypeRef(TypeRepr.NoPrefix, mint.anyRef)
          Tree.Typed(t, TypeTree(ar, t.origin), ar, t.origin)

      /** Every `new C[…]` of a threaded subject, with a WILDCARD argument filled in: a wildcard is
        * java's raw type, the emitter renders no argument for it, and a constructor that now takes
        * a witness has no way to say which element type it is for. The element types are RECORDED
        * so the enclosing declaration can be given the witness it must supply. */
      override def transformNew(n: Tree.New)(using Program): Term = n.tpt.tpe match
        case TypeRepr.AppliedType(tc @ TypeRepr.TypeRef(_, c), args) if boundSubjects.contains(c) =>
          val filled = args.map(fillArg)
          noteWitness(c, filled, n.origin)
          if filled == args then n
          else n.copy(tpt = TypeTree(TypeRepr.AppliedType(tc, filled), n.origin))
        case _ => n

      private def isSupplierCall(t: Term): Boolean = Tree.uncomment(t) match
        case ap: Tree.Apply => boundSuppliers.contains(ap.method)
        case _              => false

      /** the callee's DECLARED formals, when this program declares it. */
      private def formalsOf(m: SymId): List[TypeRepr] =
        program0.definitionOf(m).collect { case d: Tree.DefDef =>
          d.paramss.flatten.filterNot(v => program0.symbolOf(v.symbol).exists(_.flags.isGiven))
            .map(_.tpt.tpe)
        }.getOrElse(Nil)

      /** the ELEMENT type a supplier slot names — `F[Array[E]]` or `F[E]`, `E` one of the element
        * parameters in scope HERE. Anything else is refused rather than guessed. */
      private def slotElement(t: Option[TypeRepr]): Option[SymId] =
        def leaf(x: TypeRepr): Option[SymId] = x match
          case TypeRepr.TypeRef(_, e) if elemSet(e)              => Some(e)
          case TypeRepr.AppliedType(_, args)                     => args.flatMap(leaf).headOption
          case _                                                 => scala.None
        t.flatMap(leaf)

      private def fillArg(a: TypeRepr): TypeRepr = a match
        case _: TypeRepr.TypeBounds => TypeRepr.TypeRef(TypeRepr.NoPrefix, mint.javaObject)
        case TypeRepr.NoType        => TypeRepr.TypeRef(TypeRepr.NoPrefix, mint.javaObject)
        case other                  => other

      /** A RAW `new C(...)` of a threaded subject, completed with the type arguments the CAST
        * around it already states — java wrote none, and a constructor that now takes a witness
        * has no other way to say which element type it is for. A wildcard argument becomes
        * `java.lang.Object`, which is what java's raw type meant. Then the element type is
        * RECORDED, so the enclosing declaration can be given the witness it must supply. */
      private def completeRawNew(inner: Term, target: TypeRepr, o: Origin): Term =
        target match
          case TypeRepr.AppliedType(TypeRepr.TypeRef(_, c), args) if boundSubjects.contains(c) =>
            val filled    = args.map(fillArg)
            val completed = TypeRepr.AppliedType(TypeRepr.TypeRef(TypeRepr.NoPrefix, c), filled)
            noteWitness(c, filled, o)
            fillNew(inner, c, completed)
          case _ => inner

      /** the element positions this construction needs a witness at, recorded against the class
        * that must supply it. Nothing is recorded where the declaration is already threaded. */
      private def noteWitness(c: SymId, args: List[TypeRepr], o: Origin): Unit =
        if threadedHere || home == SymId.None || carriesClause(home) then ()
        else
          val wanted = elementIndexes(c).flatMap(i => args.lift(i))
            .flatMap(elementText(_, ownTparams))
          val texts = if wanted.isEmpty then List("java.lang.Object") else wanted
          if boxedWitness.isEmpty then
            refuse(ElementWitnessCheck.Issue.UnhandledCreation, owner,
              "this declaration constructs a witness-threaded type and takes no clause of its own, " +
                "and the policy names no `boxedWitness` for an element type that kept its bound",
              o, unit)
          else
            val set = needsBoxed.getOrElseUpdate(home, collection.mutable.LinkedHashSet.empty)
            texts.foreach(set += _)

      /** …and the HEAD `new` under the cast — never a nested one, whose own cast answers for it. */
      private def fillNew(t: Term, c: SymId, completed: TypeRepr): Term = t match
        case n: Tree.New if headSymOfType(n.tpt.tpe).contains(c) =>
          n.copy(tpt = TypeTree(completed, n.origin))
        case ap: Tree.Apply     => ap.copy(fun = fillNew(ap.fun, c, completed))
        case ta: Tree.TypeApply => ta.copy(fun = fillNew(ta.fun, c, completed))
        case other              => other

      private def headSymOfType(t: TypeRepr): Option[SymId] = t match
        case TypeRepr.AppliedType(TypeRepr.TypeRef(_, c), _) => Some(c)
        case TypeRepr.TypeRef(_, c)                          => Some(c)
        case _                                               => scala.None

      /** An element-typed array cast to `Array[Object]` — java's RAW view of its own array, which
        * this phase's bound drop turns into a run-time `ClassCastException` at a primitive element
        * type. Counted, never repaired: the repair is the RECEIVER's raw type (CLAUDE.md §3). */
      private def erasedArrayCast(inner: Term, target: TypeRepr, o: Origin): Unit =
        val castsToObjectArray = target match
          case TypeRepr.AppliedType(TypeRepr.TypeRef(_, arr), List(el))
              if program0.symbolOf(arr).exists(_.fullName == "scala.Array") =>
            ElementWitnessTransform.isObjectType(program0, el)
          case _ => false
        val fromDropped = inner.tpe match
          case TypeRepr.AppliedType(TypeRepr.TypeRef(_, arr), List(TypeRepr.TypeRef(_, e)))
              if program0.symbolOf(arr).exists(_.fullName == "scala.Array") => dropped(e)
          case _ => false
        if castsToObjectArray && fromDropped then
          refuse(ElementWitnessCheck.Issue.ErasedArrayCast, owner,
            "an element-typed array is presented as `Array[java.lang.Object]` — java's RAW view of " +
              "its own receiver, which no longer holds once the element type may be a primitive",
            o, unit)

      /** the four creation shapes, and the refusal for everything else this arm recognises. */
      private def creation(inner: Term, e: SymId, arrTpe: TypeRepr, o: Origin): Option[Term] =
        Tree.uncomment(inner) match
          case Tree.NewArray(_, List(n), None, _, _) =>
            Some(Tree.Opaque.spliced(List(s"${summonOf(e)}.${members.create}(", ")"), List(n), arrTpe, o))
          case na: Tree.NewArray =>
            refuse(ElementWitnessCheck.Issue.UnhandledCreation, owner,
              s"an array with ${na.dims.size} dimension(s)" +
                (if na.init.isDefined then " and an initialiser" else "") +
                " — the witness allocates one dimension and fills nothing", o, unit)
            None
          case Tree.Apply(_, args, m, _, _) if isArraysMember(program0, m, "copyOf") && args.sizeIs == 2 =>
            Some(Tree.Opaque.spliced(List(s"${summonOf(e)}.${members.copyOf}(", ", ", ")"),
                                     List(recast(args.head, arrTpe, o), args(1)), arrTpe, o))
          case Tree.Apply(_, args, m, _, _) if isArraysMember(program0, m, "copyOfRange") && args.sizeIs == 3 =>
            Some(Tree.Opaque.spliced(List(s"${summonOf(e)}.${members.copyOfRange}(", ", ", ", ", ")"),
                                     List(recast(args.head, arrTpe, o), args(1), args(2)), arrTpe, o))
          case Tree.Apply(_, _, m, _, _) if isReflectiveNewArray(program0, m) =>
            refuse(ElementWitnessCheck.Issue.UnhandledCreation, owner,
              "the array type is REFLECTED out of a `Class` argument java's own signature carries; " +
                "a witness would have to arrive on a clause this method does not declare", o, unit)
            None
          case _ => None

    // ---- the walk: subjects first, then every other declaration's own generic factories -------

    def unitOf(cd: Tree.ClassDef): SymId = cd.symbol

    def rewriteClass(cd: Tree.ClassDef, unit: SymId): Tree.ClassDef =
      val fqn = program0.symbolOf(cd.symbol).map(_.fullName).getOrElse("")
      // this class's OWN element parameters, when it is a subject the scope admits.
      val mine: Map[SymId, TypeRepr] =
        if !boundSubjects.contains(cd.symbol) then Map.empty
        else if !program0.symbolOf(cd.symbol).exists(s => scopeAdmits(program0, s)) then
          Map.empty
        else
          elementIndexes(cd.symbol).flatMap(i => cd.tparams.lift(i))
            .map(td => td.symbol -> TypeRepr.TypeRef(TypeRepr.NoPrefix, td.symbol)).toMap
      // A NESTED class does not see an enclosing class's context clause when java declared it
      // `static` — which every collection's iterator here is — so the enclosing element parameters
      // are NOT inherited into it. Each class answers for its own.
      val here = mine
      val tparams = cd.tparams.map(td => if unboundClassTparams(td.symbol) then dropUpperBound(td) else td)
      if cd.tparams.exists(td => unboundClassTparams(td.symbol)) then
        record(Decision(
          kind = Decision.Kind.RetypedSignature, subject = cd.symbol, subjectFqn = fqn,
          detail = Map(
            "elements" -> cd.tparams.filter(td => unboundClassTparams(td.symbol))
              .flatMap(td => program0.symbolOf(td.symbol)).map(_.name).mkString(","),
            "why" -> ("the element type's arrays are allocated by the witness, so java's implicit " +
              "`<: java.lang.Object` bound is not the port's — a primitive or opaque element type " +
              "is admissible")),
          reason = Reason.Configured(name, s"dropBound/$fqn"),
          origin = Decision.originOf(program0, cd.symbol)))
      val ownTps = cd.tparams.map(_.symbol).toSet
      val body   = cd.body.map(rewriteStat(_, here, fqn, unit, boundUnbound(cd.symbol),
                                           cd.symbol, ownTps, here.nonEmpty))
      // …and the witness this class must supply itself, minted at the head of its body (CT7).
      val givens = needsBoxed.get(cd.symbol).toList.flatMap(_.toList).zipWithIndex.map { (elem, i) =>
        record(Decision(
          kind = Decision.Kind.AddedMember, subject = cd.symbol, subjectFqn = fqn,
          detail = Map("member" -> s"bpMk$i", "element" -> elem,
            "why" -> ("this declaration constructs a witness-threaded type, takes no clause of " +
              "its own and its element type kept java's `Object` bound — so it holds the BOXED " +
              "witness, which is the representation java already used here")),
          reason = Reason.Configured(name, "boxedWitness"),
          origin = Decision.originOf(program0, cd.symbol)))
        Tree.Opaque(s"private given bpMk$i: $witness[$elem] = " +
                    boxedWitness.getOrElse("").replace("{elem}", elem),
                    TypeRepr.NoType, Origin.synthetic)
      }
      cd.copy(tparams = tparams, body = givens ++ body)

    def rewriteStat(s: Statement, classElems: Map[SymId, TypeRepr], owner: String,
                    unit: SymId, inUnbound: Boolean, home: SymId, ownTps: Set[SymId],
                    threadedHere: Boolean): Statement = s match
      case c: Tree.ClassDef => rewriteClass(c, unit)
      case d: Tree.DefDef =>
        // the method's OWN type parameters that a subject class receives at an element position.
        val threaded = threadedMethods.getOrElse(d.symbol, Nil)
        // A method DECLARED IN a type whose element bound is dropped is reached from that type's
        // own body with the unbounded parameter, so it inherits the drop — `TimSort[T]` calling
        // its companion's `binarySort[T <: java.lang.Object]` is a bound violation otherwise.
        val unboundHere = d.tparams.filter(td => inUnbound ||
          feedsSubject(signatureTypes(d), td.symbol, unboundOnly = true) ||
            constructsSubjectAt(d, td.symbol, unboundOnly = true))
        val elems = classElems ++ threaded.map(td =>
          td.symbol -> TypeRepr.TypeRef(TypeRepr.NoPrefix, td.symbol)).toMap
        val tparams = d.tparams.map(td =>
          if unboundHere.exists(_.symbol == td.symbol) then dropUpperBound(td) else td)
        val body = d.rhs.map(r => StandardTraversal.mapTerm(
          new BodyRewrite(elems, owner, unit,
            unboundClassTparams ++ unboundHere.map(_.symbol), home, ownTps,
            threadedHere || threaded.nonEmpty), r)(using program0))
        val paramss =
          if threaded.isEmpty then d.paramss
          else d.paramss :+ threaded.map(td => mint.usingParam(d.symbol, witness, td.symbol, d.origin))
        if threaded.nonEmpty then
          record(Decision(
            kind = Decision.Kind.RetypedSignature, subject = d.symbol,
            subjectFqn = Decision.fqnOf(program0, d.symbol, owner),
            detail = Map(
              "clause"   -> threaded.flatMap(td => program0.symbolOf(td.symbol)).map(_.name).mkString(","),
              "witness"  -> witness,
              "why"      -> ("this generic factory constructs a subject at its OWN type parameter, " +
                "which no constructor's clause can supply — the witness arrives here")),
            reason = Reason.Configured(name, s"subjectTypes/$owner"),
            origin = d.origin))
        d.copy(tparams = tparams, paramss = paramss, rhs = body)
      case v: Tree.ValDef =>
        v.copy(rhs = v.rhs.map(r => StandardTraversal.mapTerm(
          new BodyRewrite(classElems, owner, unit, unboundClassTparams, home, ownTps,
                          threadedHere), r)(using program0)))
      case t: Term =>
        StandardTraversal.mapTerm(
          new BodyRewrite(classElems, owner, unit, unboundClassTparams, home, ownTps,
                          threadedHere), t)(using program0)
      case other => other

    val units1 = program0.units.map(u => rewriteClass(u, unitOf(u)))

    // ---- the SYMBOL side of the bound drop ----------------------------------------------------
    val symbols1 = unboundClassTparams.foldLeft(program0.symbols) { (tbl, tp) =>
      tbl.get(tp) match
        case Some(s) => tbl.updated(s.copy(info = ElementWitnessTransform.withoutObjectBound(program0, s.info)))
        case None    => tbl
    }

    // ---- an UNBOUNDED WILDCARD at a position this phase unbound --------------------------------
    // java's RAW type. While the parameter was `<: java.lang.Object` the capture conformed to
    // `Object` everywhere java's own erasure did; unbounded it no longer does, at a line the port
    // never wrote. `Object` is what java's raw type meant — an obligation this drop created.
    val wildcardFill = new Phase:
      def name: String = "type-class-array/raw-wildcard"
      override def transformType(t: TypeRepr)(using Program): TypeRepr = t match
        case TypeRepr.AppliedType(tc @ TypeRepr.TypeRef(_, c), args) =>
          tparamsOfClass.get(c).filter(_.sizeIs == args.size) match
            case Some(tps) =>
              val filled = args.zip(tps).map {
                case (TypeRepr.TypeBounds(_, TypeRepr.NoType), tp) if unboundClassTparams(tp) =>
                  TypeRepr.TypeRef(TypeRepr.NoPrefix, mint.javaObject)
                case (a, _) => a
              }
              if filled == args then t else TypeRepr.AppliedType(tc, filled)
            case _ => t
        case _ => t

    val units2   = units1.map(u => StandardTraversal.mapClassDef(wildcardFill, u)(using program0))
    val symbols2 = StandardTraversal.mapSymbols(wildcardFill,
                     SymbolTable(symbols1.all ++ mint.minted))(using program0)

    // ---- the two populations the policy did NOT take over -------------------------------------
    census(program0)

    program0.rebuilt(units = units2, symbols = symbols2)

  /** The policy's COMPLEMENT over every owned generic class (CLAUDE.md §4.56): a class keeping its
    * bound that compares an element-typed value with `null` is an empty-slot sentinel no coercion
    * moves (`OccupancySentinel`); a class the policy never names creating an array at its own
    * parameter is `NonSubject`. Read off the TYPE: `x[i] == null`, `k != null`, and
    * `while ((k = x[n]) != null)` are one question (ENGINE-LIMITS K41). */
  private def census(program: Program): Unit =
    given Program = program
    program.units.foreach { u =>
      StandardTraversal.allClassDefs(u).foreach { cd =>
        val subject = boundSubjects.contains(cd.symbol)
        val idxs    = if subject then boundSubjects(cd.symbol) else cd.tparams.indices.toList
        val elems   = idxs.flatMap(cd.tparams.lift).map(_.symbol).toSet
        if elems.nonEmpty then
          val fqn  = program.symbolOf(cd.symbol).map(_.fullName).getOrElse("")
          val seen = collection.mutable.Set.empty[(String, String, Int)]
          def once(k: ElementWitnessCheck.Issue, o: Origin)(f: => Unit): Unit =
            if seen.add((k.toString, o.javaPath, o.line)) then f
          def isElem(t: TypeRepr): Option[SymId] = t match
            case TypeRepr.TypeRef(_, e) if elems(e) => Some(e)
            case _                                  => scala.None
          def elemArray(t: TypeRepr): Option[SymId] = t match
            case TypeRepr.AppliedType(TypeRepr.TypeRef(_, arr), List(TypeRepr.TypeRef(_, e)))
                if elems(e) && program.symbolOf(arr).exists(_.fullName == "scala.Array") => Some(e)
            case _ => scala.None
          def nameOf(e: SymId) = program.symbolOf(e).map(_.name).getOrElse("?")
          StandardTraversal.scanClassDef(cd, ()) { (_, t) =>
            t match
              // the bound is KEPT, and here is the reason it is kept.
              case Tree.Apply(Tree.Select(l, _, _, _), List(r), m, _, o)
                  if !boundUnbound(cd.symbol) &&
                    ElementWitnessTransform.isNullComparison(program, m) &&
                    (ElementWitnessTransform.isNullLiteral(l) || ElementWitnessTransform.isNullLiteral(r)) &&
                    List(l, r).flatMap(x => isElem(x.tpe)).nonEmpty =>
                val e = List(l, r).flatMap(x => isElem(x.tpe)).head
                once(ElementWitnessCheck.Issue.OccupancySentinel, o) {
                  refuse(ElementWitnessCheck.Issue.OccupancySentinel, fqn,
                    s"`null` at a `${nameOf(e)}` slot answers IS THIS SLOT EMPTY, so the element " +
                      "type keeps java's `<: java.lang.Object` bound and stays boxed", o, u.symbol)
                }
              // …and here is the population the policy left alone.
              case Tree.Typed(inner, tpt, _, o) if !subject =>
                val creates = Tree.uncomment(inner) match
                  case _: Tree.NewArray          => true
                  case Tree.Apply(_, _, m, _, _) =>
                    ElementWitnessTransform.isArraysMember(program, m, "copyOf") ||
                      ElementWitnessTransform.isArraysMember(program, m, "copyOfRange")
                  case _                         => false
                if creates then elemArray(tpt.tpe).foreach { e =>
                  once(ElementWitnessCheck.Issue.NonSubject, o) {
                    refuse(ElementWitnessCheck.Issue.NonSubject, fqn,
                      s"an `Array[${nameOf(e)}]` is created here and `subjectTypes` does not name " +
                        "this declaration", o, u.symbol)
                  }
                }
              case _ => ()
            ()
          }
      }
    }

  private def scopeAdmits(program: Program, s: Symbol): Boolean =
    scope match
      case RuleScope.Only(include) if include.isEmpty => true // the scope is unset; `subjectTypes` decides
      case sc                                         => sc.includes(program, s)

  private def dropUpperBound(td: Tree.TypeDef)(using program: Program): Tree.TypeDef =
    val next = ElementWitnessTransform.withoutObjectBound(program, td.rhs.tpe)
    if next == td.rhs.tpe then td else td.copy(rhs = TypeTree(next, td.rhs.origin))

  /** A symbol MINTER for one run — a value the run owns, never phase-instance state. */
  private final class WitnessMinter(program: Program):
    private var next = program.symbols.all.map(_.id.raw).maxOption.getOrElse(-1) + 1
    private val buf  = collection.mutable.ListBuffer.empty[Symbol]
    private val usings = collection.mutable.Map.empty[(SymId, SymId), SymId]
    private var anyRefSym: Option[SymId] = None

    def minted: List[Symbol] = buf.toList

    private def fresh(): SymId = { val id = SymId(next); next += 1; id }

    private def tpe(nm: String, full: String): SymId =
      val id = fresh()
      buf += Symbol(id, nm, full, Flags(), SymId.None, TypeRepr.TypeRef(TypeRepr.NoPrefix, id))
      id

    def anyRef: SymId =
      anyRefSym.getOrElse { val id = tpe("AnyRef", "scala.AnyRef"); anyRefSym = Some(id); id }

    private var objectSym: Option[SymId] = scala.None
    def javaObject: SymId =
      objectSym.getOrElse { val id = tpe("Object", "java.lang.Object"); objectSym = Some(id); id }

    /** the clause — ANONYMOUS, for `TirEmitterMembers.givenParam`'s reason: a named context
      * parameter shadows a fully-qualified reference and nothing reads the name. One per (owner,
      * element), so a two-element factory takes two. */
    def usingParam(owner: SymId, witnessFqn: String, elem: SymId, at: Origin): Tree.ValDef =
      val w   = tpe(witnessFqn.split('.').last, witnessFqn)
      val tpt = TypeRepr.AppliedType(TypeRepr.TypeRef(TypeRepr.NoPrefix, w),
                                     List(TypeRepr.TypeRef(TypeRepr.NoPrefix, elem)))
      val id = usings.getOrElseUpdate((owner, elem), {
        val s = fresh()
        buf += Symbol(s, "", MemberKey(witnessFqn, "<using>").render, Flags(isParam = true, isGiven = true),
                      owner, tpt)
        s
      })
      Tree.ValDef(id, TypeTree(tpt, at), scala.None, at)

object ElementWitnessTransform:

  /** The witness's member names — one per operation the mechanism performs. A library naming them
    * differently is a POLICY value, never an engine change (CLAUDE.md §1(b)). */
  final case class Members(
      create: String       = "create",
      copyOf: String       = "copyOf",
      copyOfRange: String  = "copyOfRange",
      nullOut: String      = "nullOut",
      nullOutRange: String = "nullOutRange",
  ):
    def fingerprint: String = s"$create/$copyOf/$copyOfRange/$nullOut/$nullOutRange"

  object Members:
    val Default: Members = Members()

  /** the `requiredGivens` entry each subject owes `GlobalsToImplicitsTransform` — the CONSTRUCTOR
    * half of the clause, threaded by the phase that already owns that mechanism (`ENGINE-LIMITS.md`
    * CT7). One value, two phases: a port never states the subject list twice. */
  def constructorGivens(subjectTypes: Map[String, List[Int]], witness: String): Map[String, String] =
    if witness.isEmpty then Map.empty
    else subjectTypes.collect {
      case (fqn, idxs) if idxs.nonEmpty =>
        fqn -> idxs.distinct.sorted.map(i => s"$witness:$i").mkString("|")
    }

  /** `TypeBounds` with JAVA'S IMPLICIT `Object` upper bound removed — a bound the java source
    * WROTE (`<T extends Comparable>`) is a fact about the library and stays (CLAUDE.md §4.56). */
  def withoutObjectBound(program: Program, t: TypeRepr): TypeRepr = t match
    case TypeRepr.TypeBounds(lo, hi) if isObjectType(program, hi) => TypeRepr.TypeBounds(lo, TypeRepr.NoType)
    case other                                                    => other

  def isObjectType(program: Program, t: TypeRepr): Boolean = t match
    case TypeRepr.TypeRef(_, s) =>
      program.symbolOf(s).exists(x => x.fullName == "java.lang.Object" || x.fullName == "scala.AnyRef")
    case _ => false

  def isArrayType(program: Program, t: TypeRepr): Boolean = t match
    case TypeRepr.AppliedType(TypeRepr.TypeRef(_, arr), _) =>
      program.symbolOf(arr).exists(_.fullName == "scala.Array")
    case _ => false

  /** a member of `java.util.Arrays` — read through the OWNER SYMBOL, never off the member's own
    * `fullName`: the frontend interns an unresolved owner as `@<id>`, so a name test on the member
    * matches nothing at exactly the JDK calls this phase exists to rewrite (CLAUDE.md §4.56). */
  def isArraysMember(program: Program, m: SymId, member: String): Boolean =
    program.symbolOf(m).exists(s => s.name == member && ownerNamed(program, s, "java.util.Arrays"))

  /** the symbol's OWNER's fully-qualified name, compared whole. */
  def ownerNamed(program: Program, s: Symbol, fqn: String): Boolean =
    program.symbolOf(s.owner).exists(_.fullName == fqn)

  /** `java.lang.reflect.Array.newInstance` — java's only way to allocate an array of a type known
    * at run time, and the one a `Class`-taking factory reaches. Matched on the JDK member; a
    * library's own wrapper is recognised by the member it forwards to at its own call site. */
  def isReflectiveNewArray(program: Program, m: SymId): Boolean =
    program.symbolOf(m).exists(s => s.name == "newInstance" &&
      program.symbolOf(s.owner).exists(o =>
        o.fullName == "java.lang.reflect.Array" || o.name == "ArrayReflection"))

  def isRefIdentity(program: Program, m: SymId): Boolean =
    program.symbolOf(m).exists(s => s.name == "eq" || s.name == "ne")

  def isNullComparison(program: Program, m: SymId): Boolean =
    program.symbolOf(m).exists(s => s.name == "==" || s.name == "!=")

  def isNullLiteral(t: Term): Boolean = Tree.uncomment(t) match
    case Tree.Literal(Constant.NullC, _, _) => true
    case Tree.Typed(inner, _, _, _)         => isNullLiteral(inner)
    case _                                  => false

  /** Can this term be READ TWICE without changing what the program does — an identifier, `this`,
    * or a selection through one of those? Anything else is an effect java evaluated once. */
  def isStablePath(t: Term): Boolean = Tree.uncomment(t) match
    case _: Tree.Ident              => true
    case _: Tree.This               => true
    case Tree.Select(q, _, _, _)    => isStablePath(q)
    case Tree.Typed(e, _, _, _)     => isStablePath(e)
    case _                          => false

