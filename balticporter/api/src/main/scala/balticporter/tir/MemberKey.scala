package balticporter.tir

/** MEMBER IDENTITY — one grammar for "which member", written once and read once.
  * `Symbol.fullName` is `owner#name` with NO parameter list, so overload identity had FIVE
  * independent spellings across the engine before this (118 `Ambiguous` of 263, measured). A
  * SEPARATE FIELD rather than a fourth `fullName` separator — widening it would move every keyed
  * artifact, and a `.`/`$` descriptor would give a rename a cut INSIDE a parameter list. */
final case class MemberKey(owner: String, name: String, descriptor: Option[Descriptor]):

  /** the string a policy author writes — `owner#name` or `owner#name(P1,P2)`. Round-trips through
    * [[MemberKey.parse]]. */
  def render: String = owner + "#" + name + descriptor.fold("")(d => "(" + d.render + ")")

  override def toString: String = render

  /** a key with no parameter list. Legal, and it means different things at different seams — see
    * [[MemberKey]]'s companion for the one asymmetry this project decided on purpose. */
  def isBare: Boolean = descriptor.isEmpty

  /** the same member with its parameter list forgotten — what an overload SET is grouped by. */
  def bare: MemberKey = if isBare then this else copy(descriptor = None)

  /** COULD THESE TWO KEYS NAME ONE MEMBER? — asked of DECLARED keys, never `equals` (a "may",
    * before a `Program` exists). `Foo#bar` and `Foo#bar(int)` are two legal spellings of one
    * selection when `bar` has a single overload; string comparison missed real disagreements in
    * `ManifestAgreement` (measured). Two DISTINCT descriptors stay unrelated (injective within an
    * overload set). Where a `Program` exists, the BINDER answers exactly instead. */
  def overlaps(other: MemberKey): Boolean =
    owner == other.owner && name == other.name &&
      (isBare || other.isBare || descriptor == other.descriptor)

object MemberKey:

  /** Why a declared key is not a key at all. Distinct from "named nothing": a malformed key COULD
    * never match, and reporting it as a typo (`NeverMatched`) sends its author looking for a member
    * that is right there. */
  final case class Malformed(key: String, what: String)

  /** Parse a declared policy key. Small grammar, REFUSED rather than best-effort'd — a lenient
    * parse binds to the wrong member silently. `owner#name` (bare, every overload),
    * `owner#name(P1,P2)` (one overload), `owner#name()` (nilary, distinct from bare),
    * `Outer$Inner#<init>(int)`, `Enum#CONSTANT#member` (last `#` separates). A type ARGUMENT
    * (`Class<T>`) is refused as MALFORMED at the `<`, since the grammar is the erased spelling. */
  def parse(key: String): Either[Malformed, MemberKey] =
    def bad(what: String) = Left(Malformed(key, what))

    /** The type-argument refusal, scoped to ONE region of the key. Deliberately not a test over the
      * whole string: `<init>` is a member NAME with angle brackets in it, and a whole-string test
      * refused every constructor key in every manifest — the spec that caught it is the reason this
      * is written per region rather than once at the top. */
    def noTypeArgs(region: String, at: Int, what: String): Option[Malformed] =
      val lt = region.indexOf('<')
      val gt = region.indexOf('>')
      if lt >= 0 then
        Some(Malformed(key, s"a type ARGUMENT at index ${at + lt} (`${region.substring(lt)}`) in the $what: " +
          "a member key is the ERASED source spelling, so write `Class`, not `Class<T>` — a generic key " +
          "can never match"))
      else if gt >= 0 then
        Some(Malformed(key, s"a `>` at index ${at + gt} in the $what, with no `<`: the key grammar has no " +
          "type arguments"))
      else scala.None

    /** a member NAME is an identifier, or one of the angle-delimited special names Java itself uses
      * (`<init>`, and the engine's `<clinit>` / `<initblock>` beside it). */
    def badName(nm: String): Option[Malformed] =
      if nm.startsWith("<") && nm.endsWith(">") && nm.length > 2 then scala.None
      else noTypeArgs(nm, 0, "member name")

    val hash = key.lastIndexOf('#')
    if hash < 0 then bad("no `#`: a member key is `owner#name`, or `owner#name(P1,P2)` for one overload")
    else
      val owner = key.substring(0, hash)
      val rest  = key.substring(hash + 1)
      if owner.isEmpty then bad("the owner is empty: a member key names its declaring type in full")
      else noTypeArgs(owner, 0, "owner").map(Left(_)).getOrElse {
        val open = rest.indexOf('(')
        if open < 0 then
          if rest.isEmpty then bad("nothing after the `#`: a member key names a member")
          else badName(rest).map(Left(_)).getOrElse(Right(MemberKey(owner, rest, scala.None)))
        else if !rest.endsWith(")") then
          bad("an unclosed parameter list: `(` with no matching `)` at the end of the key")
        else if rest.indexOf('(', open + 1) >= 0 then bad("more than one `(`")
        else
          val nm    = rest.substring(0, open)
          val inner = rest.substring(open + 1, rest.length - 1)
          if nm.isEmpty then bad("nothing between the `#` and the `(`: a member key names a member")
          else badName(nm).map(Left(_)).getOrElse {
            noTypeArgs(inner, hash + 1 + open + 1, "parameter list").map(Left(_)).getOrElse {
              val parts = if inner.isBlank then Nil else inner.split(",", -1).toList.map(_.trim)
              if parts.exists(_.isEmpty) then
                bad("an empty parameter between commas — a stray or trailing comma")
              else
                val ps   = parts.map(Descriptor.paramOf)
                val bads = parts.zip(ps).collect { case (w, Param.Unresolved) => w }
                if bads.nonEmpty then bad(s"unreadable parameter type(s): ${bads.mkString(", ")}")
                else Right(MemberKey(owner, nm, Some(Descriptor(ps))))
            }
          }
      }

  /** Parse a MEMBER SEGMENT against an owner named separately — `dispose`, or `dispose()` for the
    * nilary overload alone. A policy nested UNDER its owner has the halves already apart; splicing
    * them at the reader would build member identity from a string (forbidden by
    * `PolicyKeyLintSpec`), so the splice lives here instead. */
  def parseIn(owner: String, member: String): Either[Malformed, MemberKey] =
    if owner.isEmpty then Left(Malformed(member, "the owner is empty: a member key names its declaring type in full"))
    else if member.contains('#') then
      Left(Malformed(member, "a `#` in a member segment whose owner is already named — write the " +
        "member alone (`dispose`), or one overload of it (`dispose()`)"))
    else parse(spell(owner, member)).left.map(m => m.copy(key = member))

  /** The `owner#member` SPELLING of a segment key that [[parseIn]] could not parse. A malformed
    * segment has no parsed key to [[render]], so reporting it bare would give
    * `MergeablePolicy.subjectOf` the wrong leading FQN and silently drop the finding under the
    * run's own-keys filter. */
  def spell(owner: String, member: String): String = owner + "#" + member

  /** [[MemberKey.overlaps]] over two DECLARED strings — what a manifest layer holds. A key outside
    * the grammar (a TYPE key, or a typo) is compared by string, exact for the first and honest for
    * the second: an unparseable key names nothing, so claiming an overlap would be invented (§4.6). */
  def mayNameSame(a: String, b: String): Boolean =
    (parse(a), parse(b)) match
      case (Right(x), Right(y)) => x.overlaps(y)
      case _                    => a == b

  /** the OVERLOAD SET a declared key belongs to — `owner#name`, or the key itself where it is not a
    * member key at all. What a comparison groups by before asking [[mayNameSame]] pairwise: overlap
    * is not transitive (`bar(int)` and `bar(String)` meet only through a bare `bar`), so the group is
    * the widest set that can possibly contain a disagreement and the pairwise test is what finds one. */
  def overloadSetOf(key: String): String = parse(key).fold(_ => key, _.bare.render)

  /** parse, or throw — for a literal written in engine code or a spec, never for policy. */
  def of(key: String): MemberKey =
    parse(key).fold(m => throw new IllegalArgumentException(s"""malformed member key "${m.key}": ${m.what}"""), identity)

  def apply(owner: String, name: String): MemberKey = MemberKey(owner, name, scala.None)

/** ONE parameter's SOURCE-LEVEL spelling. Not a JVM erasure, and that is sound: java forbids two
  * methods with the same erasure, so source spelling is already injective within an overload set
  * (`void m(T)` vs `void m(String)` differ; `<X> void m(X)` vs `void m(Object)` is illegal). The
  * descriptor needs only to be CONSISTENTLY DERIVED, keeping a key the string a policy author writes. */
enum Param:
  /** a class, interface or type variable, by SIMPLE name — `Class`, `String`, `Entry`, `T`. */
  case Named(simpleName: String)
  /** a Java primitive, in JAVA's spelling — `int`, never `Int`. */
  case Prim(javaName: String)
  /** an array of something, rendered `<of>[]`. A Java vararg `T…` is an array reference, so it is
    * `Arr(Named("T"))` and spells `T[]` — the same as a declared `T[]`, which is what Java's own
    * overload rules say it is. */
  case Arr(of: Param)
  /** one parameter the frontend could not name. POISONS the whole descriptor — see
    * [[Descriptor.total]]: a key with one parameter guessed is a key that matches the wrong
    * overload. */
  case Unresolved

  def render: String = this match
    case Named(n)   => n
    case Prim(n)    => n
    case Arr(of)    => of.render + "[]"
    case Unresolved => "?"

  /** …the SIMPLE spelling: `java.lang.Object` is `Object`. A policy author routinely writes the
    * qualified form (what reports show them via `Symbol.fullName`) —
    * this removes the trap rather than documenting it. Cut only at the LAST separator (§4.56):
    * `.` between packages/top-level type, `$` before a nested type. */
  def simple: Param = this match
    case Named(n)   => Named(n.substring(math.max(n.lastIndexOf('.'), n.lastIndexOf('$')) + 1))
    case Prim(n)    => Prim(n)
    case Arr(of)    => Arr(of.simple)
    case Unresolved => Unresolved

/** A member's parameter spelling — the half of member identity `Symbol.fullName` does not carry.
  * Two latent divergences closed here: an ARRAY parameter (Spoon spells `int[]`, the TIR's tycon
  * name spelled `Array` — both sides now spell it `int[]`, java's own form), and `equals(Object)`
  * (the frontend retypes its parameter to `scala.Any`; the descriptor is read BEFORE that retyping,
  * so it stays `Object` — [[ofInfo]] is the one place the old `Any` answer survives on purpose). */
final case class Descriptor(params: List[Param]):
  /** the spelling: SIMPLE names, comma-separated, no spaces — `int,String,Class`. */
  def render: String = params.map(_.render).mkString(",")
  def arity: Int     = params.size
  /** DOES A DECLARED DESCRIPTOR NAME THIS ONE? — compared through [[Param.simple]], never `==`: a
    * policy author copies the QUALIFIED spelling out of a report, while this grammar is SIMPLE.
    * Arity checked first (cheap, never ambiguous). Two overloads whose simple names collide across
    * packages both match one key; the binder's `Ambiguous` refusal then names both, qualified. */
  def matches(other: Descriptor): Boolean =
    params.sizeIs == other.params.size &&
      params.iterator.zip(other.params.iterator).forall((a, b) => a.simple == b.simple)

  /** does every parameter have a name? A descriptor with an [[Param.Unresolved]] in it is not a key
    * and must never be stored on a `Symbol` — see [[Descriptor.total]]. */
  def isTotal: Boolean = params.forall {
    case Param.Unresolved => false
    case Param.Arr(of)    => Descriptor(List(of)).isTotal
    case _                => true
  }
  override def toString: String = render

object Descriptor:

  val empty: Descriptor = Descriptor(Nil)

  /** ALL of them, or none. A descriptor with a single guessed parameter is a key that matches the
    * WRONG overload, which is strictly worse than no key at all — the rule `PortMapTransform`
    * already stated for its own precise key, kept here so there is one place it lives. */
  def total(params: List[Param]): Option[Descriptor] =
    val d = Descriptor(params)
    Option.when(d.isTotal)(d)

  /** Java's primitives, by their own spelling. The classification is by NAME on both derivation
    * paths — the parser's `isPrimitive` would be more direct but would let the two paths disagree,
    * and a spec pins that they never do. */
  val Primitives: Set[String] =
    Set("int", "long", "short", "byte", "char", "boolean", "float", "double", "void")

  /** Java's primitive at the SCALA value class the frontend interns it under (`boolean` →
    * `scala.Boolean`, the frontend's `primName`). The spelling is read off a type's
    * IDENTITY, never off a `Symbol.name`: an engine-minted value class carries a scala-spelled
    * name and two symbols may share one `fullName` (CLAUDE.md §4.56, ENGINE-LIMITS D15). */
  val ValueClassPrimitives: Map[String, String] = Map(
    "scala.Boolean" -> "boolean", "scala.Byte"   -> "byte",   "scala.Short"  -> "short",
    "scala.Char"    -> "char",    "scala.Int"    -> "int",    "scala.Long"   -> "long",
    "scala.Float"   -> "float",   "scala.Double" -> "double", "scala.Unit"   -> "void")

  /** …and the WRAPPER java names for each (JLS 5.1.7, plus `void`/`Void`, which only a class
    * literal reaches — JLS 15.8.2). Keyed the same way as [[ValueClassPrimitives]] so the two
    * cannot drift; `void` has no boxing conversion and is in this map for the class-literal
    * reading alone (`JS-E20`). */
  val ValueClassBoxes: Map[String, String] = Map(
    "scala.Boolean" -> "java.lang.Boolean", "scala.Byte"   -> "java.lang.Byte",
    "scala.Short"   -> "java.lang.Short",   "scala.Char"   -> "java.lang.Character",
    "scala.Int"     -> "java.lang.Integer", "scala.Long"   -> "java.lang.Long",
    "scala.Float"   -> "java.lang.Float",   "scala.Double" -> "java.lang.Double",
    "scala.Unit"    -> "java.lang.Void")

  /** one written parameter → a [[Param]]. `int[][]` nests; an empty or `?` spelling is
    * [[Param.Unresolved]]. */
  def paramOf(spelling: String): Param =
    if spelling.endsWith("[]") then
      paramOf(spelling.dropRight(2)) match
        case Param.Unresolved => Param.Unresolved
        case of               => Param.Arr(of)
    else if spelling.isEmpty || spelling == "?" then Param.Unresolved
    else if Primitives(spelling) then Param.Prim(spelling)
    else Param.Named(spelling)

  /** The ENGINE's derivation, from a symbol's `info` — a FALLBACK, not the source: answers for a
    * symbol interned without a declaration, or a member the engine minted. Cannot answer for
    * `equals`: `info` is already retyped (`equals(Object)` reads `Any`), and inverting that here
    * would duplicate the retyping rule (§4.56). `Symbol.descriptor` is consulted first where it exists. */
  def ofInfo(program: Program, info: TypeRepr): Option[Descriptor] =
    def params(t: TypeRepr): Option[List[TypeRepr]] = t match
      case TypeRepr.MethodType(ps, _, _) => Some(ps.map(_._2))
      case TypeRepr.PolyType(_, r)       => params(r)
      case _                             => scala.None
    params(info).flatMap(ps => total(ps.map(paramOfType(program, _))))

  /** ONE parameter position's spelling, from its type. Extracted from [[ofInfo]] rather than
    * copied (§4.56: one derivation). [[OverrideGraph]] reads a PARENT's descriptor through a
    * subclass's instantiation arguments; a second walk spelling `scala.Array[X]` differently would
    * make the two sides of an override edge incomparable. */
  def paramOfType(program: Program, t: TypeRepr): Param =
    def nameOf(s: SymId): Param =
      program.symbolOf(s).map(y => ValueClassPrimitives.getOrElse(y.fullName, y.name))
        .fold(Param.Unresolved)(paramOf)
    t match
      // the array un-map: `scala.Array[X]` is written `X[]`, which is what the frontend's own
      // parser spells and what a manifest already contains.
      case TypeRepr.AppliedType(TypeRepr.TypeRef(_, s), List(arg))
          if program.symbolOf(s).exists(_.fullName == "scala.Array") =>
        paramOfType(program, arg) match
          case Param.Unresolved => Param.Unresolved
          case inner            => Param.Arr(inner)
      case TypeRepr.TypeRef(_, s)                          => nameOf(s)
      case TypeRepr.AppliedType(TypeRepr.TypeRef(_, s), _) => nameOf(s)
      case _                                               => Param.Unresolved

  /** …for a symbol. `None` for anything that is not an executable — which for a FIELD is the
    * COMPLETE answer and not a gap: `owner#name` is the total identity of a field, and a binder that
    * reported it as unresolved would produce a finding for every field in the program. */
  def ofInfo(program: Program, sym: Symbol): Option[Descriptor] = ofInfo(program, sym.info)
