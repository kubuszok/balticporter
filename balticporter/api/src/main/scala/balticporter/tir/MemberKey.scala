package balticporter.tir

/** MEMBER IDENTITY — one grammar for "which member", written once and read once. `Symbol.fullName`
  * is `owner#name` with NO parameter list, so overload identity had FIVE independent spellings
  * across the engine before this — a local arity test was measured as insufficient (118
  * `Ambiguous` of 263). A SEPARATE FIELD rather than a fourth `fullName` separator: widening
  * `fullName` would move every row of every promoted artifact keyed on it, and a descriptor's `.`/`$`
  * would give a package rename a place to cut INSIDE a parameter list (CLAUDE.md §4.56's trap).
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

  /** COULD THESE TWO KEYS NAME ONE MEMBER? — the question every policy layer that compares two
    * DECLARED keys has to ask, and the one they were all asking of the STRINGS.
    *
    * `Foo#bar` and `Foo#bar(int)` are two legal spellings of one selection wherever `bar` has a
    * single overload, and a comparison by string says they are unrelated. Both directions of that
    * are wrong and both were live in `ManifestAgreement`: a base's `Foo#bar = accept-risk` beside a
    * dependent's `Foo#bar(int) = ascribe-javac-choice` is two contradictory answers about one member
    * with NO divergence reported, and a mirroring module restating its base's selection under the
    * other spelling took a fatal `MissingResolution` for agreeing.
    *
    * Two DISTINCT descriptors are a different matter and stay unrelated: a source-level descriptor is
    * injective within an overload set ([[Descriptor]]), so `bar(int)` and `bar(String)` really are two
    * members and reporting them as one answer would be the over-approximation in the other direction.
    * So: same owner, same name, and at least one side bare — or the very same descriptor.
    *
    * It is deliberately NOT `equals`: this is a "may", asked before a `Program` exists. Where one
    * does, the BINDER answers exactly (`ResolutionPlan.troubles`' conflict lane compares the
    * declarations two keys BOUND to), and the two are the same rule at two levels of evidence. */
  def overlaps(other: MemberKey): Boolean =
    owner == other.owner && name == other.name &&
      (isBare || other.isBare || descriptor == other.descriptor)

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
    * com.foo.Enum#CONSTANT#member      an enum constant's body member — the LAST `#` separates
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
    * nilary overload alone.
    *
    * A policy whose keys are nested UNDER their owner (`"a.B" { dispose = "close" }`) has the two
    * halves already apart, and splicing them back together at the reader is how a phase ends up
    * building member identity from a string — the exact shape `PolicyKeyLintSpec` forbids. The
    * splice belongs in the one file that owns this grammar, so it lives here and the reader gets a
    * parsed [[MemberKey]] or a [[Malformed]] with the same message any other key would produce.
    */
  def parseIn(owner: String, member: String): Either[Malformed, MemberKey] =
    if owner.isEmpty then Left(Malformed(member, "the owner is empty: a member key names its declaring type in full"))
    else if member.contains('#') then
      Left(Malformed(member, "a `#` in a member segment whose owner is already named — write the " +
        "member alone (`dispose`), or one overload of it (`dispose()`)"))
    else parse(spell(owner, member)).left.map(m => m.copy(key = member))

  /** The `owner#member` SPELLING of a segment key that [[parseIn]] could not parse — the same
    * splice, in the same file, for the one caller a `MemberKey` cannot serve.
    *
    * A finding about a malformed segment has no parsed key to [[render]], and reporting it under
    * the bare segment alone is not a cosmetic loss: `MergeablePolicy.subjectOf` reads a finding's
    * key for its leading FQN, so a bare `dispose()` yields `dispose()` as its own subject, matches
    * no manifest's contributed set, and the run's own-keys filter DROPS the finding — a malformed
    * entry silently unreported on exactly the merged phase where a dependent's typo lives. */
  def spell(owner: String, member: String): String = owner + "#" + member

  /** [[MemberKey.overlaps]] over two DECLARED strings — what a manifest layer holds.
    *
    * A key outside the grammar (a TYPE key, which has no `#`, or a typo the binder will refuse) is
    * compared by string, which is exact for the first and honest for the second: an unparseable key
    * names nothing, so claiming it might name the same member as another would be a fact invented
    * about a string (§4.6). */
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

  /** …the SIMPLE spelling, which is what this grammar is: `java.lang.Object` is `Object` and
    * `java.util.Map$Entry` is `Entry`.
    *
    * A [[Named]] is supposed to hold a simple name already — [[Descriptor]] says so — and a POLICY
    * AUTHOR routinely writes the qualified one, because that is what the reports show them. An
    * external member's `Symbol.fullName` is its interning key, so a `collection-boundary` row prints
    * `…#identityHashCode(java.lang.Object)`; copied into a manifest that key matched NOTHING, and two
    * ports carry a comment explaining that it must be written bare. This is that trap removed rather
    * than documented.
    *
    * Cut only at a SEPARATOR and only at the LAST one (§4.56): `.` between packages and the
    * top-level type, `$` before a nested type. A key already written simply is its own answer, so the
    * normalisation is the identity on every key any manifest holds today. */
  def simple: Param = this match
    case Named(n)   => Named(n.substring(math.max(n.lastIndexOf('.'), n.lastIndexOf('$')) + 1))
    case Prim(n)    => Prim(n)
    case Arr(of)    => Arr(of.simple)
    case Unresolved => Unresolved

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
  /** DOES A DECLARED DESCRIPTOR NAME THIS ONE? — compared through [[Param.simple]] on both sides.
    *
    * Never `==`, and the reason is one an equality cannot express: this grammar is the SIMPLE
    * spelling, while every report a policy author copies a key out of shows the QUALIFIED one (an
    * external member's `Symbol.fullName` is its interning key, parameters and all). Two ports carry a
    * comment saying "write it bare, the descriptor form never matches" — which is a trap documented
    * twice and removed nowhere, and the binder holds both strings at the moment it fails.
    *
    * Arity first, because that is the cheap half and the one that is never ambiguous. What this can
    * admit that `==` could not is two overloads whose parameter simple names collide across packages
    * (`m(java.util.List)` beside `m(com.foo.List)`, which java permits): both then match one key, and
    * the binder's own `Ambiguous` refusal names them with their qualified signatures — a refusal that
    * says which two, rather than a silent pick. */
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
    params(info).flatMap(ps => total(ps.map(paramOfType(program, _))))

  /** ONE parameter position's spelling, from its type.
    *
    * Extracted from [[ofInfo]] rather than copied, for `ParentSubst`'s own reason (§4.56: one
    * derivation, not one per caller). [[OverrideGraph]] needs it to read a PARENT's descriptor
    * through the arguments a subclass instantiates it with, and a second walk that spelled
    * `scala.Array[X]` as `Array` rather than `X[]` would make the two sides of one override edge
    * incomparable in exactly the family the edge is hardest to see. */
  def paramOfType(program: Program, t: TypeRepr): Param =
    def nameOf(s: SymId): Param = program.symbolOf(s).map(_.name).fold(Param.Unresolved)(paramOf)
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
