package balticporter.tir

/** WHICH of the two shapes a java `enum` is emitted in — and it is a fact about the JAVA
  * declaration, so the emitter, the call sites and [[OmissionCheck]] all read it here.
  *
  * ==A java enum IS a `java.lang.Enum`, and that is a TYPE fact a port cannot decline==
  * `class E extends Enum<E>` is not decoration: java's own libraries bound on it
  * (`EnumSet.noneOf(Class<E>)`, `EnumMap`, `Comparable<E>`), and so does any library that writes
  * `<E extends Enum<E> & Something>` — which is ordinary java that any codebase may contain. A port
  * whose enums are not `java.lang.Enum`s cannot satisfy one such bound, at any call, anywhere.
  *
  * Scala 3 makes exactly ONE form of that supertype available: `enum X extends java.lang.Enum[X]`.
  * A `sealed abstract class` may not name it — scalac answers *"only enums defined with the enum
  * syntax can"* — so the choice is not between two spellings of one shape, it is between the
  * conforming shape and a shape that is not a `java.lang.Enum` at all.
  *
  * ==…and the `enum` form cannot express every java enum, so the shape is DERIVED and the refusal
  * is COUNTED==
  * A scala 3 enum CASE has no template body, and every member of `java.lang.Enum` that java made
  * FINAL is one an emitted member may not collide with. Java has two namespaces and scala has one,
  * so a java enum may perfectly well declare a FIELD called `name` beside the final `name()` it
  * inherits (`CLAUDE.md` §4.55's rule read at an enum). Each such enum keeps the pre-existing
  * `sealed abstract class` + `case object` shape, which compiles and behaves the same in every
  * respect but this supertype — and `OmissionCheck.enumShapeRefusals` is one row per refusal naming
  * the guard, because a shape decided per declaration and reported nowhere is exactly the silent
  * half of `CLAUDE.md` §3.
  *
  * Every predicate below is a question about java's own declaration, never about a name the emitter
  * happens to have chosen (§4.56): the constants come from `enumCases`, the parameters from
  * [[CtorFunnel.enumPrimaryCtor]] — the same derivation the emitter renders from — and the member
  * names from the class body. */
object EnumShape:

  /** the members `java.lang.Enum` declares FINAL, plus the two the scala 3 `enum` desugaring puts in
    * the COMPANION. An emitted member of any of these names cannot coexist with the parent.
    *
    * The list is java's, read off `java.lang.Enum`'s own declaration, and it is longer than the two
    * this engine already synthesised: `name()` and `ordinal()` are the pair the sealed shape had to
    * supply itself (`ENGINE-LIMITS.md` T11/T13), and `compareTo`, `getDeclaringClass`, `equals`,
    * `hashCode`, `clone` and `finalize` arrive only now, because they arrive with the PARENT.
    *
    * Note which direction each one can fail from. Java forbids a java enum to DECLARE most of them —
    * overriding a final method is a compile error there — so the reachable shape is the one java
    * permits and scala does not: a FIELD, or a promoted constructor PARAMETER, whose name equals a
    * final method's. That is not hypothetical; it is the shape `ENGINE-LIMITS.md` T11 was written
    * about. `values`/`valueOf` sit here for the companion's sake rather than the class's.
    *
    * The screen below reads EVERY member and not only the fields, which is a deliberate
    * OVER-APPROXIMATION and is stated as one. A java `values(int)` or `clone(int)` is a legal
    * OVERLOAD that scala would accept beside the inherited member — java resolves by signature and
    * so does scala here — so refusing on the bare name keeps the sealed shape for an enum the `enum`
    * form could have expressed. What it cannot do is the other error: admit a declaration the parent
    * rejects, which is a compile failure in the emitted port rather than a counted row. Zero sites in
    * the corpus take the over-approximation; if one appears, narrowing this to `ValDef`s plus the
    * promoted parameters is the exact rule and the finding is where it will be visible. */
  val Reserved: Set[String] =
    Set("name", "ordinal", "compareTo", "getDeclaringClass", "describeConstable",
        "equals", "hashCode", "clone", "finalize", "values", "valueOf")

  /** `None` — this java enum is emitted as a scala 3 `enum extends java.lang.Enum[X]`.
    * `Some(reason)` — it is emitted in the sealed shape, and `reason` is the guard, in the words a
    * finding prints. */
  def refusal(program: Program, cd: Tree.ClassDef): Option[String] =
    if !program.symbolOf(cd.symbol).exists(_.flags.isEnum) then scala.None
    else
      def nameOf(s: SymId): String = program.symbolOf(s).map(_.name).getOrElse("")
      // a scala 3 `enum` must declare at least one case; java permits an enum with none (a holder
      // for statics, `enum E { ; static int x; }`) and that one is left in the sealed shape rather
      // than emitted as a form the parser rejects.
      if cd.enumCases.isEmpty then Some("the enum declares no constants and a scala 3 `enum` must declare at least one case")
      else
        val bodied = cd.enumCases.filter(_.body.nonEmpty).map(ec => nameOf(ec.symbol))
        // JLS 8.9.1's per-constant CLASS BODY is an anonymous subclass; a scala 3 enum case has no
        // template body at all (`case A extends E { … }` does not parse), so an enum with one keeps
        // the sealed shape — where the body IS a `case object`'s body and is already emitted
        // (`ENGINE-LIMITS.md` T8).
        if bodied.nonEmpty then
          Some(s"constant(s) ${bodied.mkString(", ")} carry a class body, which a scala 3 enum case cannot")
        else
          val params  = CtorFunnel.enumPrimaryCtor(program, cd).toList
            .flatMap(CtorFunnel.valueParams(program, _)).map(v => nameOf(v.symbol))
          val members = cd.body.collect { case d: Definition => nameOf(d.symbol) }
          // a promoted PARAMETER is a member of the emitted class exactly as a field is — which is
          // the whole of `CLAUDE.md` §4.55's "count what the constructor funnel PROMOTES" — so both
          // lists are screened, and against the same set.
          (params ++ members).find(Reserved).map { n =>
            s"`$n` collides with a member java.lang.Enum declares final (or with the scala 3 enum's own companion)"
          }

  /** true where the emitter renders this class as a scala 3 `enum`. The emitter's own question, and
    * the one a CALL SITE asks before dropping the parens from `values()`. */
  def isScalaEnum(program: Program, cd: Tree.ClassDef): Boolean =
    program.symbolOf(cd.symbol).exists(_.flags.isEnum) && refusal(program, cd).isEmpty
