package balticporter.tir

/** MEMBER IDENTITY — one grammar for "which member", written once and read once.
  *
  * ==Why this exists at all==
  * `Symbol.fullName` is `owner#name` and carries NO parameter list, so `X#m` is the same string for
  * every overload of `m` while each overload is a distinct symbol with distinct call sites. Every
  * seam that had to tell two overloads apart therefore grew its own answer, and the engine ended up
  * with FIVE spellings of one idea, two of which disagree (see [[Descriptor]]'s note on the two
  * divergences). A local arity test was measured as the alternative and produced 118 `Ambiguous`
  * results out of 263 — overload identity is not a refinement of a name, it is the whole of it.
  *
  * ==Why it is a SEPARATE FIELD and not a fourth separator in `fullName`==
  * Two reasons, and the first is decisive. A finding's stable id hashes its owner's `fullName`, as
  * do `decisions.tsv`'s subject, `TirPrinter.canonical` and therefore every cache key — so widening
  * `fullName` moves every row of every promoted artifact in every lane, for a change that adds no
  * information any of them display. And a descriptor contains `.` (`java.lang.String`) and `$` (a
  * nested parameter type), which would give `RuleScope.covers` and the package rename a place to cut
  * INSIDE a parameter list: CLAUDE.md §4.56's trap re-opened in the one function this project has
  * been bitten by twice. `Symbol.fullName` is left exactly as it was.
  */
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

object MemberKey:

  /** Why a declared key is not a key at all. Distinct from "named nothing": a malformed key COULD
    * never match, and reporting it as a typo (`NeverMatched`) sends its author looking for a member
    * that is right there. */
  final case class Malformed(key: String, what: String)

  /** Parse a declared policy key.
    *
    * The grammar is deliberately small and everything outside it is REFUSED rather than
    * best-effort'd, because the failure mode of a lenient parse is a key that binds to the wrong
    * member and says nothing:
    *
    * {{{
    * com.foo.Bar#baz              a member, every overload      (bare)
    * com.foo.Bar#baz(int,String)  exactly one overload
    * com.foo.Bar#baz()            the NO-ARGUMENT overload — not the same as the bare form
    * com.foo.Outer$Inner#<init>(int)   a nested owner, a constructor
    * }}}
    *
    * A type ARGUMENT is the one refusal worth naming precisely: `X#m(Class<T>)` is what an author
    * writes when they copy a Java signature, and it can never match, since the grammar is the
    * ERASED source spelling. Reported as malformed AT the `<` rather than silently as never-matched,
    * because those two readings contradict each other and the reader then has to work out which is
    * true. */
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

    val hash = key.indexOf('#')
    if hash < 0 then bad("no `#`: a member key is `owner#name`, or `owner#name(P1,P2)` for one overload")
    else if key.indexOf('#', hash + 1) >= 0 then bad("more than one `#`: `owner#name` has exactly one")
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

  /** parse, or throw — for a literal written in engine code or a spec, never for policy. */
  def of(key: String): MemberKey =
    parse(key).fold(m => throw new IllegalArgumentException(s"""malformed member key "${m.key}": ${m.what}"""), identity)

  def apply(owner: String, name: String): MemberKey = MemberKey(owner, name, scala.None)

/** ONE parameter's SOURCE-LEVEL spelling.
  *
  * Not a JVM erasure, and that is sound rather than convenient: Java forbids two methods in one
  * class with the same erasure, so source-level spelling is ALREADY injective within an overload set
  * (`void m(T)` beside `void m(String)` is legal and the two spell differently; `<X> void m(X)`
  * beside `void m(Object)` is illegal). The descriptor therefore does not need to be truly erased —
  * it needs to be CONSISTENTLY DERIVED. That is what keeps a manifest key exactly the string a
  * policy author already writes. */
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

/** A member's parameter spelling — the half of member identity `Symbol.fullName` does not carry.
  *
  * ==The two divergences this closes, and where they came from==
  * Both were latent, both invisible to every count, and neither is exercised by the current corpus —
  * which is what makes them a trap set for the NEXT library rather than a present defect:
  *
  *  - '''an ARRAY parameter.''' Spoon's `getSimpleName` for an array reference is
  *    `component + "[]"`, so a manifest key spells `Owner#copy(int[])`; the TIR renders the same
  *    type `AppliedType(Array, [Int])` and a key built from the tycon's name spelled it
  *    `Owner#copy(Array)`. One member, two keys, each invisible to the other seam. A Java `T…`
  *    vararg is an array reference too, so every vararg member had the same split. This type spells
  *    it `int[]` on BOTH sides — Java's own spelling, and the one that changes no existing key.
  *  - '''`equals(Object)`.''' The frontend deliberately retypes a 1-argument `equals`'s parameter to
  *    `scala.Any` before building the `MethodType`, because Scala's `Object.equals` takes `Any`. A
  *    descriptor read from `info` therefore says `Any` and a manifest says `Object`. This is closed
  *    AT THE SOURCE — the descriptor is read from the frontend's parser BEFORE the retyping — rather
  *    than reconciled downstream, so `Object` is simply what the field says. [[ofInfo]] is the one
  *    place the old answer survives, and it survives on purpose (see its own note).
  */
final case class Descriptor(params: List[Param]):
  /** the spelling: SIMPLE names, comma-separated, no spaces — `int,String,Class`. */
  def render: String = params.map(_.render).mkString(",")
  def arity: Int     = params.size
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

  /** The ENGINE's derivation, from a symbol's `info`.
    *
    * This is the FALLBACK, not the source: it answers for a symbol the frontend interned without a
    * declaration, and for a member the ENGINE minted after the frontend ran (a synthetic primary has
    * no Java behind it and still needs a descriptor in the same grammar, so a published contract row
    * and a policy key can never be in two spellings).
    *
    * '''It cannot answer for `equals`, and does not pretend to.''' `info` has already been retyped
    * (`equals(Object)` reads `Any`), and inverting that here would put the retyping rule in a second
    * place — the general form of §4.56's lesson: a derivation may only conclude what its own input
    * says. For every member the frontend declared, `Symbol.descriptor` is the answer and this is not
    * consulted; a spec pins the agreement, and pins `equals` as its one exception. */
  def ofInfo(program: Program, info: TypeRepr): Option[Descriptor] =
    def params(t: TypeRepr): Option[List[TypeRepr]] = t match
      case TypeRepr.MethodType(ps, _, _) => Some(ps.map(_._2))
      case TypeRepr.PolyType(_, r)       => params(r)
      case _                             => scala.None
    def nameOf(s: SymId): Param = program.symbolOf(s).map(_.name).fold(Param.Unresolved)(paramOf)
    def of(t: TypeRepr): Param = t match
      // the array un-map: `scala.Array[X]` is written `X[]`, which is what the frontend's own
      // parser spells and what a manifest already contains.
      case TypeRepr.AppliedType(TypeRepr.TypeRef(_, s), List(arg))
          if program.symbolOf(s).exists(_.fullName == "scala.Array") =>
        of(arg) match
          case Param.Unresolved => Param.Unresolved
          case inner            => Param.Arr(inner)
      case TypeRepr.TypeRef(_, s)                          => nameOf(s)
      case TypeRepr.AppliedType(TypeRepr.TypeRef(_, s), _) => nameOf(s)
      case _                                               => Param.Unresolved
    params(info).flatMap(ps => total(ps.map(of)))

  /** …for a symbol. `None` for anything that is not an executable — which for a FIELD is the
    * COMPLETE answer and not a gap: `owner#name` is the total identity of a field, and a binder that
    * reported it as unresolved would produce a finding for every field in the program. */
  def ofInfo(program: Program, sym: Symbol): Option[Descriptor] = ofInfo(program, sym.info)
