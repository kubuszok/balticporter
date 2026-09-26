package balticporter.tir

/** Decides whether a java `enum` is emitted as a scala 3 `enum` or the sealed-class shape.
  *
  * The `enum` form extends `java.lang.Enum[X]` but cannot express constants with class bodies or members that collide with `Enum`'s finals. Refusals are counted via `OmissionCheck.enumShapeRefusals`.
  * All predicates read java's own declaration.
  */
object EnumShape:

  /** Members `java.lang.Enum` declares final, plus `values`/`valueOf` from the companion. Screened against all member names (deliberate over-approximation by bare name). */
  val Reserved: Set[String] =
    Set(
      "name",
      "ordinal",
      "compareTo",
      "getDeclaringClass",
      "describeConstable",
      "equals",
      "hashCode",
      "clone",
      "finalize",
      "values",
      "valueOf"
    )

  /** `None` = emitted as scala 3 `enum`; `Some(reason)` = sealed shape, with the guard. */
  def refusal(program: Program, cd: Tree.ClassDef): Option[String] =
    if !program.symbolOf(cd.symbol).exists(_.flags.isEnum) then scala.None
    else
      def nameOf(s: SymId): String = program.symbolOf(s).map(_.name).getOrElse("")
      // java permits an enum with no constants; scala 3 `enum` requires at least one case
      if cd.enumCases.isEmpty then Some("the enum declares no constants and a scala 3 `enum` must declare at least one case")
      else
        val bodied = cd.enumCases.filter(_.body.nonEmpty).map(ec => nameOf(ec.symbol))
        // scala 3 enum case has no template body
        if bodied.nonEmpty then Some(s"constant(s) ${bodied.mkString(", ")} carry a class body, which a scala 3 enum case cannot")
        else
          // only a parameter that BECOMES a member is screened; one java kept a plain parameter is
          // emitted as one, where a reserved name is legal
          val params  = memberParams(program, cd).toList.map(nameOf).sorted
          val members = cd.body.collect { case d: Definition => nameOf(d.symbol) }
          (params ++ members).find(Reserved).map { n =>
            s"`$n` collides with a member java.lang.Enum declares final (or with the scala 3 enum's own companion)"
          }

  /** The primary constructor's parameters the emitted class must hold as MEMBERS: one standing for a field it supersedes, or one the constructor body reassigns. Every other parameter is java's plain
    * constructor parameter, visible to the constructor alone, and is emitted without `val`/`var`.
    */
  def memberParams(program: Program, cd: Tree.ClassDef): Set[SymId] =
    CtorFunnel.enumPrimaryCtor(program, cd) match
      case scala.None => Set.empty
      case Some(ctor) =>
        val params  = CtorFunnel.valueParams(program, ctor).map(_.symbol).toSet
        val written = collection.mutable.Set.empty[SymId]
        val scan    = new Phase:
          def name:                                           String = "enum-shape/written-params"
          override def transformTerm(t: Term)(using Program): Term   =
            t match
              case Tree.Assign(Tree.Ident(s, _, _), _, _, _, _) if params(s) => written += s
              case _                                                         => ()
            t
        CtorFunnel.stmtsOf(ctor).foreach { case t: Term => StandardTraversal.mapTerm(scan, t)(using program); case _ => () }
        CtorFunnel.enumSupersededBy(program, cd).keySet.intersect(params) ++ written

  /** True when the emitter renders this class as a scala 3 `enum`. */
  def isScalaEnum(program: Program, cd: Tree.ClassDef): Boolean =
    program.symbolOf(cd.symbol).exists(_.flags.isEnum) && refusal(program, cd).isEmpty
