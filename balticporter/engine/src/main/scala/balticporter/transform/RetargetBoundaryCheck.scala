package balticporter.transform

import balticporter.tir.*

/** The RETARGET boundary, in the direction the subtyping argument does not cover — every value the
  * JDK PRODUCES at a retargeted type, counted.
  *
  * ==Why a second boundary check==
  * `CollectionBoundaryCheck` measures the seam a `typeMap` retyping creates, and it is deliberately
  * blind to a retarget: `CollectionsTransform.retargetedTypes` is kept out of `mappedTypes` and
  * `retypedTargets` because a retarget's precondition — '''the scala target is usable wherever the
  * java source was''' — says there is no seam to bridge. `java.util.Comparator -> scala.math.Ordering`
  * holds it: `Ordering[T] <: Comparator[T]`, so a retyped value reaches every slot that still says
  * `Comparator`.
  *
  * '''That argument is one-directional, and nothing counted the other direction.''' It licenses
  * `Ordering` flowing INTO a `Comparator` slot. It says nothing about a `Comparator` the JDK HANDS
  * BACK — `Collections.reverseOrder()`, `TreeMap.comparator()`, `Comparator.comparing(…)`,
  * `String.CASE_INSENSITIVE_ORDER` — flowing into a slot this phase retyped. The value is a
  * `Comparator` and the slot says `Ordering`, which is the subtyping the wrong way round; and
  * because the phase's position-blind `transformType` has already moved the node's OWN type, both
  * sides of that slot read `Ordering` and `CollectionBoundaryCheck` reports nothing. Through a
  * rewritten cast it is worse than a compile error: a `ClassCastException` at run time with a green
  * compile, which is CLAUDE.md §3's whole subject.
  *
  * ==What this counts, and what it does NOT do==
  * It COUNTS producer references and refuses them with the §1 classification. It synthesises no
  * coercion — see `ENGINE-LIMITS.md` K14 for the scope decision and what a wrapper would cost. A
  * count that exists and reads 0 is the point: 0 from a check that runs is a measurement, and 0
  * from a check that does not exist is the sentence "we know about that one" living in prose
  * (CLAUDE.md §5.1's objection to a hand-maintained ledger).
  *
  * Three shapes, kept apart because the READER's next action differs:
  *
  *   - a call or field read whose member is the JDK's OWN external symbol and whose value the phase
  *     retyped to a retarget target;
  *   - a STATIC RECEIVER left naming the java source type — `CollectionsTransform` has no
  *     `transformIdent`, so `Comparator.naturalOrder()` keeps its java receiver while the node type
  *     around it moved;
  *   - a CAST to a retarget target from anything that is not one — the shape with no compile error
  *     at all.
  *
  * ==Universal, parameterised by the phase's own record==
  * §1(a) in mechanism — a retype that moves a declaration and not the value behind it is a fact
  * about java and scala — taking the retarget table as a PARAMETER, so an empty table makes it a
  * no-op by arithmetic. Every question it asks about a type is a membership test in what the PHASE
  * ITSELF did (CLAUDE.md §4.56): it holds no type list of its own.
  */
object RetargetBoundaryCheck:

  /** The check's name in `findings.tsv`. */
  val Name = "collection-retarget"

  enum Issue:
    /** the JDK's own member hands back a value at the SOURCE type, into a slot the phase moved. */
    case ExternalProducer
    /** a static receiver still naming the java source type, under a node type that moved. */
    case StaticReceiver
    /** a cast TO the retarget target from something that is not one — a runtime `ClassCastException`
      * with a green compile. */
    case CastToTarget
    /** `remove()` on a `JavaIterator` BRIDGED from a retarget target's own `iterator` — the bridge
      * has no handle on the collection, so its `remove` is the runtime's own
      * `UnsupportedOperationException`, and java's removing iteration is a counted REFUSAL. */
    case IteratorRemove

  object Issue:
    /** which of §1's three kinds the fix is — the thing a bare typer error cannot say, and here
      * there is often no typer error at all. */
    def classification(i: Issue): String = i match
      case ExternalProducer =>
        "§1(a) engine gap, or §1(b) policy: an external member PRODUCES the java type this port " +
          "retargets, and the retarget's subtyping licence runs the other way — the produced value " +
          "is NOT the scala type the slot now declares. Wrap it at this site, or move the type out " +
          "of `retarget` into `typeMap` with a kind and a factory, where the seam becomes a counted " +
          "`coerce` boundary (DESIGN.md §8.12)."
      case StaticReceiver =>
        "§1(a) engine gap: the receiver of a STATIC access is a term reference to the type's own " +
          "symbol, which `transformType` cannot see, so it still names the java type while the " +
          "node's type moved. The value produced is the java one — the same defect a redirect fixes " +
          "with a member TWIN (`TypeRedirectTransform.transformIdent`)."
      case CastToTarget =>
        "§1(a) engine gap, and the one with NO compile error: the cast target moved with the type " +
          "and the value did not, so this is a `ClassCastException` at run time on a port that " +
          "compiles clean (CLAUDE.md §4.4's class of defect). Refuse the retarget for this type or " +
          "wrap the operand."
      case IteratorRemove =>
        "§1(a) engine gap, REFUSED and counted: java's `Iterator.remove()` mutates the collection " +
          "the iterator came from, and the scala target's `iterator` is a read-only view, so " +
          "`JavaIterator.from(x.iterator)` can only refuse — `UnsupportedOperationException` at run " +
          "time, on a port that compiles clean. A faithful image is a removing iterator minted " +
          "OVER THE COLLECTION (index-tracking, calling the target's own remove), which is a " +
          "runtime shim the bridge does not have yet (ENGINE-LIMITS.md K34)."

  /** one producer-direction site. `produced` is the java type the value really has; `slot` is what
    * the emitted code now says. */
  final case class Finding(issue: Issue, what: String, produced: String, slot: String,
                           origin: Origin, enclosing: SymId):
    def detail: String = s"$what: produces $produced / emitted as $slot"
    def render: String = s"$issue $what — produces $produced / emitted as $slot  (${origin.javaPath}:${origin.line})"
    def report(using program: Program): CheckReport.Finding =
      CheckReport.Finding(Name, issue.toString, program.symbolOf(enclosing).map(_.fullName).getOrElse("?"),
        CheckReport.relativise(origin.javaPath), origin.line, detail)

  def check(program: Program, retargeted: Map[String, String]): List[Finding] =
    check(program, program.units, retargeted)

  /** …restricted to the units the run EMITS, for `CollectionBoundaryCheck`'s reason: a dependent
    * port's `Program` holds its base's units too, and a site inside one of those is the BASE's
    * finding (`ENGINE-LIMITS.md` D2). */
  def check(program: Program, units: List[Tree.ClassDef], retargeted: Map[String, String]): List[Finding] =
    if retargeted.isEmpty then Nil
    else
      val out     = collection.mutable.ListBuffer[Finding]()
      val sources = retargeted.keySet
      // the SOURCE for a target, so a finding can name the type the value really has rather than
      // only the type the slot claims. A target reached from two sources names them both.
      val sourceOf: Map[String, String] =
        retargeted.groupBy(_._2).map((t, e) => t -> e.keys.toList.sorted.mkString("/"))
      given Program = program

      def fqn(t: TypeRepr): Option[String] = headSym(t).flatMap(program.symbolOf).map(_.fullName)
      def targeted(t: TypeRepr): Option[String] = fqn(t).filter(sourceOf.contains)
      def isMethod(s: SymId): Boolean =
        program.symbolOf(s).exists(_.info.isInstanceOf[TypeRepr.MethodType | TypeRepr.PolyType])

      /** The value at this site is produced OUTSIDE this program. `Program.owns` is the structural
        * climb (§4.56) and it is the right one here even in a dependent: a base's declaration was
        * retyped by THIS run's phase too (the program spans it), so its values agree with their
        * signatures. What does not is a symbol the frontend interned with no declaration at all. */
      def external(s: SymId): Boolean = s != SymId.None && !program.owns(s)

      /** A CONSTRUCTOR application is never a producer, whatever `owns` says about its symbol.
        *
        * `new Comparator<T>(){…}` constructs its value AT THE TYPE THE SOURCE NAMES, and the phase
        * retyped that occurrence and the body under it in the same tree — the emitted
        * `new Ordering[T]{…}` really is an `Ordering`. It has to be excluded structurally rather
        * than by `owns`, because an ANONYMOUS class's `<init>` does not climb to a unit symbol and
        * so reads as external. Measured, and this is the only reason the exclusion exists: the
        * check's first corpus run reported **11 findings, every one of them this shape** — 4 in
        * libGDX core, 6 in its suite, 1 in anim8 — on code that is correct. A counter that reports
        * a working retarget as a residue is worse than no counter (CLAUDE.md §3). */
      def constructs(t: Tree.Apply): Boolean =
        t.fun.isInstanceOf[Tree.New] || program.symbolOf(t.method).exists(_.name == "<init>")

      def fullNameOf(s: SymId): Option[String] = program.symbolOf(s).map(_.fullName)
      val iteratorFromFqn   = "balticporter.runtime.JavaIterator.from"
      val iteratorRemoveFqn = "balticporter.runtime.JavaIterator#remove"

      val scan = new Phase:
        def name: String = "collection-retarget-check"

        // Per enclosing MEMBER, the retarget sources whose `iterator` this member bridged through
        // `JavaIterator.from` — read off the bridge's own shape (`from(<recv>.iterator)`), which
        // is the phase's own emission and not a name test on the receiver. A `remove()` on a
        // `JavaIterator` in the same member is then attributed to them: the bridge value has no
        // provenance past its declaration, so the member is the honest unit of attribution.
        private var bridgedHere: List[String] = Nil
        private var removesHere: List[(Origin, SymId)] = Nil

        override def transformDefDef(d: Tree.DefDef)(using Program): Tree.DefDef =
          bridgedHere = Nil; removesHere = Nil
          val r = super.transformDefDef(d)
          for src <- bridgedHere.distinct; (o, m) <- removesHere do
            out += Finding(Issue.IteratorRemove, "iterator remove", src, retargeted(src), o, m)
          r

        override def transformApply(t: Tree.Apply)(using Program): Term =
          if fullNameOf(t.method).contains(iteratorFromFqn) then
            t.args match
              case Tree.Select(recv, _, _, _) :: Nil =>
                targeted(recv.tpe).foreach(tt => bridgedHere ::= sourceOf(tt))
              case _ => ()
          if fullNameOf(t.method).contains(iteratorRemoveFqn) then
            removesHere ::= (t.origin, t.method)
          if external(t.method) && !constructs(t) then
            targeted(t.tpe).foreach(tt => out += Finding(Issue.ExternalProducer, "call",
              sourceOf(tt), tt, t.origin, t.method))
          // …and the RECEIVER half: a static access is an `Ident` of the TYPE's own symbol, which
          // `transformType` never reaches. Read off the call rather than from a bare `Ident` arm so
          // that only a receiver position is counted — a type name cannot occur as a value anywhere
          // else in this IR.
          t.fun match
            case Tree.Select(q: Tree.Ident, _, _, _) => staticReceiver(q, t.method)
            case _                                   => ()
          t

        private def staticReceiver(q: Tree.Ident, method: SymId): Unit =
          program.symbolOf(q.sym).map(_.fullName).filter(sources.contains).foreach { src =>
            out += Finding(Issue.StaticReceiver, "static receiver", src, retargeted(src), q.origin, method)
          }

        override def transformSelect(t: Tree.Select)(using Program): Term =
          // FIELDS only: a method's `Select` is the `fun` of the `Apply` above, and counting both
          // would report one call twice.
          if external(t.sym) && !isMethod(t.sym) then
            targeted(t.tpe).foreach(tt => out += Finding(Issue.ExternalProducer, "field read",
              sourceOf(tt), tt, t.origin, t.sym))
          t

        override def transformTerm(t: Term)(using Program): Term =
          t match
            case c: Tree.Typed =>
              // a CAST whose target moved with the type. Only where the operand is NOT already on
              // the target side: casting one retargeted value to the same type is the identity, and
              // the phase moved both ends of it together.
              for
                tt <- targeted(c.tpt.tpe)
                if !targeted(c.expr.tpe).contains(tt)
              do out += Finding(Issue.CastToTarget, "cast", sourceOf(tt), tt, c.origin, SymId.None)
            case _ => ()
          t

      units.foreach(u => StandardTraversal.mapClassDef(scan, u))
      out.toList.distinct

  private def headSym(t: TypeRepr): Option[SymId] = t match
    case TypeRepr.TypeRef(_, s)      => Some(s)
    case TypeRepr.AppliedType(tc, _) => headSym(tc)
    case _                           => scala.None

  /** grouped one-line summary, worst family first, each with its §1 classification. */
  def summary(fs: List[Finding]): String =
    if fs.isEmpty then "  none"
    else
      fs.groupBy(_.issue).toList.sortBy((_, v) => -v.size).map { (issue, vs) =>
        val head  = s"  ${vs.size} × $issue\n  ${Issue.classification(issue)}"
        val sites = vs.groupBy(f => (f.what, f.produced, f.slot)).toList.sortBy((_, v) => -v.size).take(10)
          .map { case ((what, p, s), ss) => s"    ${ss.size} × $what: produces $p / emitted as $s" }
        (head :: sites).mkString("\n")
      }.mkString("\n")
