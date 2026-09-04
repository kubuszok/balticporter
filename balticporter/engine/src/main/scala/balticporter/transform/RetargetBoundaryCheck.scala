package balticporter.transform

import balticporter.tir.*

/** The RETARGET boundary, in the direction subtyping does not cover — every value the JDK
  * PRODUCES at a retargeted type, counted. A retarget licenses a value flowing INTO a slot; it
  * says nothing about the JDK HANDING one BACK — a direction `CollectionBoundaryCheck` cannot see
  * since `transformType` already moved both sides of the slot. Counts three shapes (producer
  * reference, static receiver, cast); synthesises no coercion (ENGINE-LIMITS K14). */
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
    /** `remove()` on a `JavaIterator` bridged from a retarget target's own `iterator` — the bridge
      * has no handle on the collection, so `remove` refuses at run time (a counted REFUSAL). */
    case IteratorRemove

  object Issue:
    /** which of §1's three kinds the fix is (CLAUDE.md §4.45). */
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

  /** …restricted to units the run EMITS — a dependent's `Program` holds its base's units too, whose
    * sites are the base's finding (ENGINE-LIMITS D2). */
  def check(program: Program, units: List[Tree.ClassDef], retargeted: Map[String, String]): List[Finding] =
    if retargeted.isEmpty then Nil
    else
      val out     = collection.mutable.ListBuffer[Finding]()
      val sources = retargeted.keySet
      // the SOURCE for a target, so a finding can name the type the value really has. A target
      // reached from two sources names them both.
      val sourceOf: Map[String, String] =
        retargeted.groupBy(_._2).map((t, e) => t -> e.keys.toList.sorted.mkString("/"))
      given Program = program

      def fqn(t: TypeRepr): Option[String] = headSym(t).flatMap(program.symbolOf).map(_.fullName)
      def targeted(t: TypeRepr): Option[String] = fqn(t).filter(sourceOf.contains)
      def isMethod(s: SymId): Boolean =
        program.symbolOf(s).exists(_.info.isInstanceOf[TypeRepr.MethodType | TypeRepr.PolyType])

      /** the value at this site is produced OUTSIDE this program — `Program.owns` (§4.56). A base's
        * declaration retyped by this run's phase still agrees with its signature. */
      def external(s: SymId): Boolean = s != SymId.None && !program.owns(s)

      /** a CONSTRUCTOR application is never a producer, whatever `owns` says: `new Comparator<T>(){…}`
        * constructs its value at the retyped type, but an anonymous class's `<init>` does not climb
        * to a unit symbol and reads as external — excluded structurally (CLAUDE.md §3). */
      def constructs(t: Tree.Apply): Boolean =
        t.fun.isInstanceOf[Tree.New] || program.symbolOf(t.method).exists(_.name == "<init>")

      def fullNameOf(s: SymId): Option[String] = program.symbolOf(s).map(_.fullName)
      val iteratorFromFqn   = "balticporter.runtime.JavaIterator.from"
      val iteratorRemoveFqn = "balticporter.runtime.JavaIterator#remove"

      val scan = new Phase:
        def name: String = "collection-retarget-check"

        // per enclosing MEMBER, the retarget sources whose `iterator` this member bridged through
        // `JavaIterator.from`; a `remove()` on a `JavaIterator` in the same member attributes to them.
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
          // the RECEIVER half: a static access is an `Ident` of the TYPE's own symbol, which
          // `transformType` never reaches. Read off the call so only a receiver position counts.
          t.fun match
            case Tree.Select(q: Tree.Ident, _, _, _) => staticReceiver(q, t.method)
            case _                                   => ()
          t

        private def staticReceiver(q: Tree.Ident, method: SymId): Unit =
          program.symbolOf(q.sym).map(_.fullName).filter(sources.contains).foreach { src =>
            out += Finding(Issue.StaticReceiver, "static receiver", src, retargeted(src), q.origin, method)
          }

        override def transformSelect(t: Tree.Select)(using Program): Term =
          // fields only — a method's `Select` is the `fun` of the `Apply` above.
          if external(t.sym) && !isMethod(t.sym) then
            targeted(t.tpe).foreach(tt => out += Finding(Issue.ExternalProducer, "field read",
              sourceOf(tt), tt, t.origin, t.sym))
          t

        override def transformTerm(t: Term)(using Program): Term =
          t match
            case c: Tree.Typed =>
              // a cast whose target moved with the type, excluding an operand already on the
              // target side (the phase moved both ends of that identity together).
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
