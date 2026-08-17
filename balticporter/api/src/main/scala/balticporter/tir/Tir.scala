package balticporter.tir

/** Typed IR (TIR) — the re-compiler's working representation. See DESIGN.md §2.
  *
  * SHAPED LIKE `scala.quoted.Quotes#reflect` so that anyone who has written a Scala 3
  * macro finds the transformer API familiar: `TypeRepr`, `Tree`/`Statement`/
  * `Definition`/`Term`/`TypeTree`, `Symbol`. We do NOT use Quotes directly — its
  * contracts are subtle to satisfy outside a macro, and its `Symbol` hides internals
  * from users. So we OWN a close analog and deliberately EXPOSE MORE:
  *   - `Origin`: provenance back to the original Java source (Quotes has positions,
  *     but not cross-language origin);
  *   - `SymTag`: open domain semantics on symbols (e.g. "this Int is a GL layer");
  *   - a WHOLE-PROGRAM `XrefIndex` (`usagesOf`, `callersOf`) — Quotes is per-macro-
  *     expansion and cannot answer program-wide usage queries.
  *
  * Every node carries a fully STRUCTURED `TypeRepr` (never a flat string collapsing
  * applied params / mixins / self-types), resolved from Spoon — never re-inferred.
  * Starter subset of the Quotes node set; grown incrementally.
  */

/** Provenance to the original source. Our addition over Quotes' positions. */
final case class Origin(javaPath: String, line: Int, col: Int)
object Origin:
  val synthetic: Origin = Origin("<synthetic>", 0, 0)

/** WHICH `try` — see [[Tree.Try.id]] for why neither an `Origin` nor object identity can be that.
  *
  * A counter and nothing else: it is never printed, never emitted, never written to an artifact and
  * never compared across runs, so no output depends on the order tokens are handed out in. */
opaque type TryId = Long
object TryId:
  private val seq                = java.util.concurrent.atomic.AtomicLong(0L)
  def fresh(): TryId             = seq.incrementAndGet()
  extension (t: TryId) def raw: Long = t

/** WHICH `switch` — see [[Tree.Match.id]]. Same contract, same reasons, as [[TryId]]. */
opaque type MatchId = Long
object MatchId:
  private val seq                      = java.util.concurrent.atomic.AtomicLong(0L)
  def fresh(): MatchId                 = seq.incrementAndGet()
  extension (m: MatchId) def raw: Long = m

/** Stable symbol identity — interned, NEVER a string (the analog of `reflect.Symbol`
  * as a handle). Ergonomic queries hang off it as `(using Program)` extensions,
  * mirroring Quotes' `(using Quotes)` symbol methods. */
opaque type SymId = Int
object SymId:
  val None: SymId                   = -1
  def apply(i: Int): SymId          = i
  extension (s: SymId) def raw: Int = s

/** what a java METHOD REFERENCE's referenced executable declares — see [[Tree.MethodRef.referent]].
  *
  * The two cases are JLS 15.13.1's split at `Type::name`, which is ONE java syntax naming two
  * different functions: a `static` method is a qualified NAME, an instance method is an UNBOUND
  * reference whose receiver becomes the SAM's FIRST parameter (JLS 15.13.3).
  *
  * ==BOTH cases carry the ARITY, and the static one earns it at ZERO==
  * This enum used to say *"the arity rides on the second case only … a qualified name is eta-expanded
  * by scala against the target, exactly as javac did it"*. That is true of every arity but one.
  * Scala 3 does NOT eta-expand a NULLARY method from a bare name — `Type.m` where `m` is `def m(): T`
  * is a call missing its argument list (`method m must be called with () argument`), not a `() => T` —
  * so `Type::nilaryStatic` at a supplier-shaped SAM is the one static reference whose emitted form
  * has to be a lambda after all. Measured at 3 errors on the first library to write one
  * (`ENGINE-LIMITS.md` G32).
  *
  * The arity is therefore read for both, from the same place and for the reason stated at
  * [[Tree.MethodRef]]: `getParameters` on the REFERENCE survives a lenient parse — it erases what
  * each slot says, never how many there are — so this is a fact about java and never a default
  * standing in for one.
  *
  * At PACKAGE level and not inside `object Tree`, beside `Flags` and `Descriptor`: it is a fact
  * ABOUT a node and not a node, and `EmissionFieldCoverageSpec` scans `object Tree`'s case classes
  * for exactly that distinction — its aggregate set is pinned, so a non-Tree declared in there fails
  * the totality assertion rather than quietly joining the node census. */
enum Referent:
  case Static(arity: Int)
  case Instance(arity: Int)

// ---------------------------------------------------------------------------
// Flags — mirrors `reflect.Flags` (superset of what we currently populate).
// ---------------------------------------------------------------------------
final case class Flags(
    isAbstract: Boolean = false,
    isFinal: Boolean = false,
    isSealed: Boolean = false,
    isTrait: Boolean = false,
    isModule: Boolean = false, // `object`
    isEnum: Boolean = false,
    /** Java `record` (JLS 8.10) — a declaration whose components javac turns into a private final
      * field, a bare-name accessor, a canonical constructor and `equals`/`hashCode`/`toString`.
      *
      * A FLAG and not "does this class extend `java.lang.Record`", although both hold of every
      * record. The parent is a fact about a class file the port cannot write — scalac emits no JVM
      * record, so `Class.isRecord` is false whatever is named in the `extends` clause — and, going
      * the other way, scalac ACCEPTS `extends java.lang.Record` on a class that is not one, where
      * javac refuses it outright (JLS 8.1.4, both halves measured). What licenses synthesising the
      * four members javac generated is the java DECLARATION, and this flag is it.
      *
      * It carries ONE more conjunct than "java wrote `record`", deliberately: *and this program
      * still declares every member the synthesis reads*. A port may `dropMethods` an accessor, and
      * a synthesis over a SUBSET of the components would emit an `equals` and a `hashCode` java
      * never computed, at no error and no moved count. So a record whose [[Symbol.components]]
      * could not be joined in full arrives with this flag CLEAR and ships exactly as it did before
      * the row was lowered — which is loud, at §3's gate. */
    isRecord: Boolean = false,
    /** Java `@interface` — a declaration of an ANNOTATION type, not an ordinary interface. */
    isAnnotation: Boolean = false,
    isOpaque: Boolean = false, // `opaque type`

    isCase: Boolean = false,
    isImplicit: Boolean = false,
    isGiven: Boolean = false,
    isOverride: Boolean = false,
    isMutable: Boolean = false, // `var`
    isLazy: Boolean = false,
    isParam: Boolean = false,
    isVararg: Boolean = false, // Java `T...` → Scala `T*`
    isParamAccessor: Boolean = false,
    isPrivate: Boolean = false,
    isProtected: Boolean = false,
    /** Java's FOURTH access level — "default", or package-private: no `public`/`protected`/`private`
      * modifier at all. Three of Java's four levels used to collapse onto "neither of the two
      * booleans above", so a declaration written with no modifier produced flags byte-identical to a
      * `public` one and the TIR could not even STATE that a type was package-private (DESIGN §8.7).
      *
      * It is the JLS-EFFECTIVE level, not the presence of a modifier: an interface member is
      * implicitly `public` and an enum constructor implicitly `private`, so neither is this.
      * Exactly one of `isPrivate`, `isProtected`, `isPackagePrivate` is set, or all three are clear
      * and the declaration is public. */
    isPackagePrivate: Boolean = false,
    isStatic: Boolean = false, // JavaStatic
    isNative: Boolean = false, // Java `native` (JNI) — a Panama-FFI rewrite target
    isCovariant: Boolean = false,
    isContravariant: Boolean = false,
)

/** Open, extensible domain semantics attached to symbols by transforms. */
trait SymTag

// ---------------------------------------------------------------------------
// Trivia — the ORIGINAL COMMENTS, carried through the port.
// ---------------------------------------------------------------------------

/** Comment kinds preserved from the Java source. */
enum TriviaKind:
  case Line, Block, Javadoc

/** One comment, VERBATIM — delimiters included, sliced out of the original source buffer rather
  * than re-printed from a parsed model.
  *
  * Why the text is a raw slice and not a normalised body: a comment is the one artefact of a port
  * whose whole value is its exact words. Re-printed from `CtComment.toString` a Javadoc loses
  * `@param` alignment, `<pre>` blocks and — critically — the exact wording of a LICENCE, which is
  * the one comment a derived work is legally obliged to reproduce (CLAUDE.md §4.57). Slicing keeps
  * that honest by construction rather than by a rendering rule nobody checks.
  *
  * Deliberately NOT shared with `balticporter.core.Trivia` (the BIR path's identical-looking
  * record): that file is FROZEN and slated for deletion once its ten corpus programs move over,
  * and a TIR node referring to it would make the TIR undeletable-with-it. Four duplicated lines is
  * the cheaper of the two costs. */
final case class Trivia(kind: TriviaKind, text: String)

/** A Java ANNOTATION on a declaration — `@Test`, `@Override`, `@Deprecated`, `@Null`.
  *
  * Carried because dropping annotations is not cosmetic. A JUnit suite whose `@Test` did not
  * survive runs ZERO tests and reports SUCCESS: green build, green suite, nothing executed — the
  * only silent-omission defect found in this corpus that manufactures the evidence that behaviour
  * is fine, and one that conceals itself by disabling the very gate meant to catch such things.
  * Beyond tests, `@Override` is checkable intent, `@Deprecated` and `@Null` are API contract, and
  * `@SuppressWarnings` is deliberate.
  *
  * `args` are the annotation's element values, by name (Java's single-element `@A(x)` is named
  * `value`) — dropping an ARGUMENT is the same defect one level down, so they are carried as real
  * `Term`s rather than text. */
final case class Annot(tpe: TypeRepr, args: List[(String, Term)], origin: Origin)

/** A declaration's symbol record (the analog of `reflect.Symbol`'s backing data).
  * `info` is its type: a value/field type, a `MethodType`/`PolyType` for methods, a
  * `TypeBounds` for type params/abstract types, or the class `TypeRef` for classes.
  * Cross-references (declarations, members, usages) are answered by `Program`, so a
  * plain record stays serializable while the graph lives in the tables. */
final case class Symbol(
    id: SymId,
    name: String,
    fullName: String,
    flags: Flags,
    owner: SymId,           // SymId.None at the root
    info: TypeRepr,
    // `privateWithin` — a `SymId` stub mirroring `reflect.Symbol`, populated nowhere and read
    // nowhere — used to sit here. It is now `Flags.isPackagePrivate`, and deliberately NOT a
    // symbol: a `SymId` cannot name a package in this TIR, because packages are `fullName`
    // segments and not symbols (DESIGN §8.7). The qualifier a `private[p]` renders with is derived
    // from the emitter's CURRENT emitted package, which is a fact the emitter has and no symbol
    // carries.
    origin: Origin = Origin.synthetic,
    tags: Set[SymTag] = Set.empty,
    /** the declaration's Java annotations, in source order. See [[Annot]] — losing these is a
      * silent correctness defect, not a formatting one. */
    annotations: List[Annot] = Nil,
    /** annotations the frontend could NOT carry, by name — reported by `OmissionCheck` rather
      * than discarded, so a gap here is a number on every run. */
    droppedAnnotations: List[String] = Nil,
    /** the member's PARAMETER SPELLING — the half of member identity [[fullName]] does not carry,
      * and the whole of overload identity. See [[Descriptor]] for the grammar and for the two
      * cross-grammar divergences it closes.
      *
      * Three readings of `None`, and only one of them is a gap:
      *
      *  - a FIELD, a TYPE, a parameter, a local — not an executable, so there is nothing to spell.
      *    `owner#name` is its COMPLETE identity, and a binder that reported this as unresolved would
      *    produce a finding for every field in the program.
      *  - an EXTERNAL member whose declaration the frontend could not resolve. That one IS a gap,
      *    and it is the surviving residue of the arity fallback this field replaces — made LOUD at
      *    bind time here rather than silently degrading to arity at match time.
      *  - a member with one unnameable formal: ALL of them or none ([[Descriptor.total]]).
      *
      * NEVER folded into [[fullName]] and never printed by `TirPrinter.canonical`: it is a symbol
      * PROPERTY, not part of the symbol's NAME, and every promoted artifact in every lane is keyed
      * on the name. See [[MemberKey]] for the whole of that argument. */
    descriptor: Option[Descriptor] = None,
    /** java's `permits` clause — the types this `sealed` declaration NAMES as its subclasses (JLS
      * 8.1.1.2). Empty for everything that is not a sealed type, and empty for a sealed type that
      * omitted the clause because java infers it from the compilation unit.
      *
      * '''Interned, never a name.''' The one question this list is asked is *does the set of
      * subtypes this program declares account for every type java permitted* — and a permitted type
      * the parse never saw (an `excludeGlobs` file, a unit-fatal refusal) is precisely the case that
      * must be detected. A NAME could not answer it: the permitted string is written in the UPSTREAM
      * namespace and a rename runs last (§4.56), so a name-keyed join would report every permitted
      * type unaccounted on a renaming port and none on any other. An interned id is the structural
      * answer — a permitted type the program declares resolves to the SAME id its `ClassDef` carries
      * (`Minter.external` never clobbers a definition), and one it does not stays an external stub
      * that no subtype set can contain.
      *
      * It is NOT a [[Flags]], although it travels with `Flags.isSealed`: flags mirror
      * `reflect.Flags`, and `permits` is a class-header CLAUSE rather than a modifier. */
    permits: List[SymId] = Nil,
    /** java's RECORD COMPONENTS, in DECLARATION ORDER — empty for everything that is not a record.
      *
      * Order is the whole of it: `equals`, `hashCode`, `toString` and every deconstruction read the
      * components in the order the header wrote them, so a `Set` (which is what Spoon hands back)
      * is not the shape this can be carried in. Sorted by source position at the harvest.
      *
      * INTERNED, for [[permits]]'s reason and one more of its own: the field, the accessor and the
      * component all share ONE java name (JLS 8.10.1) and scala has one namespace, so the emitter's
      * clash resolution renames the field (`x` -> `x$field`) before anything is written. A
      * name-keyed lookup at emission would therefore find the accessor for both, which is not a
      * hypothetical — it is the shape every record has. */
    components: List[RecordComponent] = Nil,
)

/** one java RECORD COMPONENT (JLS 8.10.1) — the three declarations java derives from one name.
  *
  * `field` and `accessor` are both carried because java reads DIFFERENT ones: `equals`, `hashCode`
  * and `toString` are generated over the FIELDS (measured — a record whose accessor is overridden
  * to double its component still prints and hashes the undoubled field), while a record PATTERN
  * deconstructs through the ACCESSOR (JLS 14.30.1, measured at the same fixture: the pattern binds
  * the doubled value). A synthesis that read one of the two for both would be right on every record
  * that does not override an accessor, which is most of them.
  *
  * @param name the component's own name — the java one, before any clash resolution, which is what
  *   java's `toString` prints as the `name=` label.
  */
final case class RecordComponent(name: String, field: SymId, accessor: SymId)

object Symbol:

  /** The `fullName` a frontend interns a type VARIABLE it could not resolve under.
    *
    * A java type argument may name a variable that has no binder in the reading scope — the
    * INFERRED argument of a diamond (`new ArrayList<>(coll)`) is the standing case, and a callee's
    * own `<T>` reached from outside is another. There is no symbol to point at, so the frontend
    * mints one whose name is a marker rather than a name.
    *
    * `?` opens it because no java identifier and no java FQN can, so the marker is unambiguous and
    * costs no field. Spelled HERE and read through [[isUnresolvedTypeVar]] by both sides: it is a
    * convention BETWEEN a frontend and the emitter, and a convention spelled twice is one that
    * drifts. What the emitter owes it is that such a symbol NEVER reaches the output — `?E` names
    * nothing in Scala and does not even lex (`ENGINE-LIMITS.md` G2 settles what does: `?`). */
  val UnresolvedTypeVarPrefix = "?"

  def isUnresolvedTypeVar(fullName: String): Boolean = fullName.startsWith(UnresolvedTypeVarPrefix)

// ---------------------------------------------------------------------------
// Constants — mirrors `reflect.Constant`.
// ---------------------------------------------------------------------------
enum Constant:
  case BoolC(v: Boolean)
  case ByteC(v: Byte)
  case ShortC(v: Short)
  case CharC(v: Char)
  case IntC(v: Int)
  case LongC(v: Long)
  case FloatC(v: Float)
  case DoubleC(v: Double)
  case StringC(v: String)
  case NullC
  case UnitC
  case ClassOfC(tpe: TypeRepr)

// ---------------------------------------------------------------------------
// TypeRepr — the STRUCTURED type algebra, mirroring `reflect.TypeRepr`. Every
// constituent stays addressable: transforms can rewrite applied args, split/compose
// mixins (And/Or), read self-types (ThisType) and F-bounds (TypeBounds on params).
// ---------------------------------------------------------------------------
sealed trait TypeRepr
object TypeRepr:
  case object NoPrefix                                                      extends TypeRepr
  case object NoType                                                        extends TypeRepr
  final case class ConstantType(value: Constant)                           extends TypeRepr
  /** named type reference to a type symbol (`prefix#sym`); prefix `NoPrefix` when plain. */
  final case class TypeRef(prefix: TypeRepr, sym: SymId)                    extends TypeRepr
  /** singleton / path-dependent: a term's type (`p.type`, the prefix of `p.T`). */
  final case class TermRef(prefix: TypeRepr, sym: SymId)                    extends TypeRepr
  /** `C.this` — self reference (needed for self-types and path-dependence). */
  final case class ThisType(cls: SymId)                                     extends TypeRepr
  final case class SuperType(thistpe: TypeRepr, supertpe: TypeRepr)         extends TypeRepr
  /** applied type constructor `tycon[args]`; args may be `TypeBounds` (wildcards). */
  final case class AppliedType(tycon: TypeRepr, args: List[TypeRepr])       extends TypeRepr
  /** intersection / mixin: `A & B` (`A with B`). */
  final case class AndType(left: TypeRepr, right: TypeRepr)                 extends TypeRepr
  /** union: `A | B`. */
  final case class OrType(left: TypeRepr, right: TypeRepr)                  extends TypeRepr
  final case class ByNameType(underlying: TypeRepr)                         extends TypeRepr
  /** bounds `>: lo <: hi` — also the wildcard type `?` (a `TypeBounds` arg). */
  final case class TypeBounds(low: TypeRepr, hi: TypeRepr)                  extends TypeRepr
  /** structural refinement `parent { type/def name: info }`. */
  final case class Refinement(parent: TypeRepr, name: String, info: TypeRepr) extends TypeRepr
  /** method type `(params): result`; contextual/implicit flag for `using` clauses. */
  final case class MethodType(params: List[(String, TypeRepr)], result: TypeRepr, isImplicit: Boolean = false)
      extends TypeRepr
  /** poly method type `[tparams]: result` with per-param bounds. */
  final case class PolyType(params: List[(String, TypeBounds)], result: TypeRepr) extends TypeRepr
  /** higher-kinded type lambda `[X <: U] =>> body`. */
  final case class TypeLambda(params: List[(String, TypeBounds)], body: TypeRepr) extends TypeRepr
  /** reference to the i-th parameter of an enclosing Method/Poly/TypeLambda binder. */
  final case class ParamRef(binder: TypeRepr, idx: Int)                     extends TypeRepr

  val AnyBounds: TypeBounds = TypeBounds(NoType, NoType) // NoType lo/hi ⇒ Nothing/Any

// ---------------------------------------------------------------------------
// Trees — mirrors `reflect.Tree`: Tree > Statement > {Definition, Term}; TypeTree
// (a syntactic type carrying its `TypeRepr`) is a sibling. Not every Tree has a
// `tpe` (Definitions carry a `symbol`, Terms a `tpe`) — same split as Quotes.
// ---------------------------------------------------------------------------
sealed trait Tree:
  def origin: Origin

sealed trait Statement extends Tree

sealed trait Definition extends Statement:
  def symbol: SymId

sealed trait Term extends Statement:
  def tpe: TypeRepr

/** a syntactic type occurrence (`reflect.TypeTree`) — carries its resolved
  * `TypeRepr` and an `Origin`, bridging tree positions and the type algebra. */
final case class TypeTree(tpe: TypeRepr, origin: Origin) extends Tree

object Tree:
  // ---- definitions ----
  /** class / trait / object / enum. `parents` are the (typed) super constructors /
    * mixins; `selfType` carries `self: S =>` and F-bounded self annotations; `tparams`
    * are the class's own type parameters as `TypeDef`s whose `rhs` is a `TypeBounds`
    * — so a class F-bound `class C[T <: IRich[T]]` is a first-class, walkable node
    * (the bound references `T`'s own symbol, and the xref traces it). */
  final case class ClassDef(
      symbol: SymId,
      parents: List[Term | TypeTree],
      selfType: Option[TypeTree],
      body: List[Statement],
      origin: Origin,
      tparams: List[TypeDef] = Nil,
      enumCases: List[EnumCase] = Nil,
      /** the type's OWN comments — its Javadoc and anything else written directly above it. */
      leading: List[Trivia] = Nil,
      /** comments that belong to the FILE rather than to this type: everything above the `package`
        * clause, plus anything hanging off the imports. That is where the Apache/BSD notice lives
        * in every library in this corpus, and it is why it is a SECOND field instead of being
        * merged into `leading`.
        *
        * The distinction is one the emitter needs and cannot recover: a `package` clause must
        * precede the class, so a file header merged into `leading` would be emitted BELOW the
        * `package` line — legal, but no longer the file's header. And a Java file with two
        * top-level types becomes two Scala files, each of which is a derived work in its own
        * right; each therefore carries the notice, which is the one place the frontend's
        * claimed-once rule is deliberately not applied ([[Trivia]], `SpoonTir.fileHeader`).
        *
        * Non-empty only on a top-level unit; a nested `ClassDef` has no file of its own. */
      unitLeading: List[Trivia] = Nil,
  ) extends Definition

  /** one Java enum constant — `NAME(ctorArgs) { body }`. Lowered to a Scala `case object`
    * extending the enum's sealed class. `body` carries per-constant method overrides. */
  final case class EnumCase(symbol: SymId, ctorArgs: List[Term], body: List[Statement], origin: Origin,
                            /** the constant's own comments. */
                            leading: List[Trivia] = Nil)

  /** type alias / abstract type member / type parameter (`type T = …` / `type T <: U`).
    * For a type parameter, `rhs` carries a `TypeBounds`. */
  final case class TypeDef(symbol: SymId, rhs: TypeTree, origin: Origin) extends Definition

  final case class DefDef(
      symbol: SymId,
      paramss: List[List[ValDef]],
      returnTpt: TypeTree,
      rhs: Option[Term],
      origin: Origin,
      tparams: List[TypeDef] = Nil,
      /** the method's/constructor's own comments — Javadoc above the declaration. */
      leading: List[Trivia] = Nil,
  ) extends Definition

  final case class ValDef(symbol: SymId, tpt: TypeTree, rhs: Option[Term], origin: Origin,
                          /** the field's/local's own comments. */
                          leading: List[Trivia] = Nil) extends Definition

  // ---- terms (each reference resolves to a SymId) ----
  final case class Ident(sym: SymId, tpe: TypeRepr, origin: Origin)                     extends Term
  final case class Select(qual: Term, sym: SymId, tpe: TypeRepr, origin: Origin)        extends Term
  final case class Literal(const: Constant, tpe: TypeRepr, origin: Origin)              extends Term
  final case class This(cls: SymId, tpe: TypeRepr, origin: Origin)                      extends Term
  /** `super` (receiver of `super.m(...)` / `super(...)`) — distinct from `this` so the
    * backend can emit `super`-dispatch and constructor delegation correctly. */
  final case class Super(cls: SymId, tpe: TypeRepr, origin: Origin)                     extends Term
  /** the BODY of a Java anonymous class — `new Base(args) { members }`. `symbol` is a synthetic
    * type symbol that OWNS the members (so their keys never collide with the enclosing class's,
    * and `this` inside them names the anonymous instance, as in Java). `body` carries fields,
    * methods and instance-initializer blocks; `dropped` names any member the frontend could NOT
    * translate, so [[OmissionCheck]] can report it instead of losing it silently.
    *
    * An empty `body` is meaningful: `new Base(){}` (the super-type-token idiom) really has none.
    * `New.anon = None` means "not an anonymous class at all", which is why the distinction is an
    * `Option` and not a possibly-empty list. */
  final case class AnonClass(symbol: SymId, body: List[Statement], origin: Origin, dropped: List[String] = Nil,
                             /** what the CLASS FILE says about the type this anonymous class named —
                               * see [[Sam]] for why the question is answered in the frontend and
                               * carried here rather than asked by the phase that acts on it.
                               *
                               * Defaults to [[Sam.Answer.unknown]], which is the conservative arm:
                               * a tree nobody asked the question about refuses loudly in
                               * `idiom(refused)` rather than converting on a fact nothing
                               * established. */
                             sam: Sam.Answer = Sam.Answer.unknown)

  /** `new T` / `new T { … }`. `anon` is present exactly when Java wrote an anonymous-class body
    * (a `CtNewClass`); its members are Scala's anonymous-class-expression members. Dropping it
    * is the failure this node's shape exists to prevent — a listener that compiles and does
    * nothing. */
  final case class New(tpt: TypeTree, tpe: TypeRepr, origin: Origin, anon: Option[AnonClass] = None) extends Term
  final case class Apply(fun: Term, args: List[Term], method: SymId, tpe: TypeRepr, origin: Origin) extends Term
  final case class TypeApply(fun: Term, targs: List[TypeTree], tpe: TypeRepr, origin: Origin)       extends Term
  final case class Assign(lhs: Term, rhs: Term, tpe: TypeRepr, origin: Origin)          extends Term
  /** @param trailing
    *   comments written at the END of the block, after the last statement — the one comment
    *   position java has and the TIR had no carrier for.
    *
    *   A frontend folds a comment onto the statement that FOLLOWS it; a comment with nothing
    *   after it therefore had nowhere to go, and was dropped after being claimed — which put it
    *   beyond every coarser harvest too. It is not a rare shape: an empty override body whose
    *   whole content is `// Do nothing by default.`, commented-out code at the tail of a method,
    *   and a switch arm whose comment became trailing the moment the case-terminator `break` was
    *   filtered out (CLAUDE.md §4.4) are all this.
    *
    *   A slot here places them EXACTLY, because end-of-block is precisely where java had them —
    *   no hoisting to a surviving node, which `ENGINE-LIMITS.md` V1 refuses for the right reason
    *   (a comment printed above a member says something false about it). */
  final case class Block(stats: List[Statement], expr: Term, tpe: TypeRepr, origin: Origin,
                         trailing: List[Trivia] = Nil) extends Term
  /** anonymous function (`reflect.Closure`/`Block(DefDef,Closure)` simplified).
    *
    * @param resultTpt THE SAM METHOD'S OWN RESULT TYPE, where whoever built this node knew it.
    *
    * A java lambda body is a METHOD body, so `return` is legal in it and means *leave the lambda*;
    * a scala lambda is an EXPRESSION and rejects `return` outright. The emitter restores java's
    * meaning by interposing a nested `def` (`JS-S21`) — and a `def` needs a RESULT TYPE, which is
    * the method's and never the functional interface's. `tpe` above is the INTERFACE
    * (`java.util.Comparator[String]`); the type the `def` needs is `compare`'s `int`, and no
    * amount of reading `tpe` produces it, which is `ENGINE-LIMITS.md` M6's own sentence and was
    * true of every lambda in the IR until a node could carry the answer.
    *
    * `None` means NOBODY KNEW, never `Unit`: the emitter then falls back to the one case it can
    * decide from the body alone (every `return` valueless ⇒ a java `void` lambda) and REFUSES the
    * rest, loudly and counted (`OmissionCheck.unnameableLambdaReturn`). §4.6's rule literally — a
    * default indistinguishable from a real answer is a fabricated fact, and a guessed result type
    * is a `def` that COMPILES while meaning something else.
    *
    * Who fills it in is a fact about who holds a `DefDef`: `SamLambdaTransform` converts an
    * anonymous class and therefore holds the anon's own method, `returnTpt` included. A lambda the
    * SOURCE wrote has no `DefDef` anywhere — java wrote the body and javac inferred the type from
    * the target — so the frontend leaves this `None` and M6's refusal stands there, narrowed to
    * exactly the sites where nothing in the program states the type. */
  final case class Lambda(params: List[ValDef], body: Term, tpe: TypeRepr, origin: Origin,
                          resultTpt: Option[TypeTree] = None) extends Term
  final case class If(cond: Term, thenp: Term, elsep: Term, tpe: TypeRepr, origin: Origin) extends Term
  final case class Typed(expr: Term, tpt: TypeTree, tpe: TypeRepr, origin: Origin)      extends Term
  /** varargs sequence (`reflect.Repeated`). */
  final case class Repeated(elems: List[Term], tpe: TypeRepr, origin: Origin)           extends Term
  /** `expr*` — an ARRAY passed THROUGH a repeated parameter, which is one argument and not a list.
    *
    * The mirror of [[Repeated]], and needed for the same reason: java lets a call fill a `T...`
    * slot with an array it already holds (`String.format(fmt, args)`), and the callee's half decides
    * what that means. Where the callee is OURS the parameter is emitted `Array[T]` and the array is
    * passed as it stands — no node. Where the callee is a CLASS FILE scalac reads `T...` as a
    * REPEATED parameter, so the bare array conforms as ONE element; the faithful form is the spread
    * (`ENGINE-LIMITS.md` K6.5, fourth case).
    *
    * `tpe` is the ARRAY's type, because that is what the term still is — the `*` is a fact about the
    * position, exactly as `Repeated`'s flattening is. */
  final case class Spread(expr: Term, tpe: TypeRepr, origin: Origin)                    extends Term
  /** `return e` — imperative early exit (Java bodies). `tpe` is Nothing. */
  final case class Return(expr: Option[Term], tpe: TypeRepr, origin: Origin)            extends Term
  /** `while (cond) body` — imperative loop (Java bodies). `tpe` is Unit. */
  final case class While(cond: Term, body: Term, tpe: TypeRepr, origin: Origin, label: Option[String] = None) extends Term
  /** `throw e`. `tpe` is Nothing. */
  final case class Throw(expr: Term, tpe: TypeRepr, origin: Origin)                     extends Term
  /** `x instanceof T` — the tested type is a real type usage. */
  final case class InstanceOf(expr: Term, tpt: TypeTree, tpe: TypeRepr, origin: Origin) extends Term
  /** `array(index)` element access. */
  final case class ArrayAccess(array: Term, index: Term, tpe: TypeRepr, origin: Origin) extends Term
  /** `array.length`. */
  final case class ArrayLength(array: Term, tpe: TypeRepr, origin: Origin)              extends Term
  /** `new T[dims]` and/or `new T[]{ init }`; `init` present for a brace initializer. */
  final case class NewArray(elem: TypeTree, dims: List[Term], init: Option[List[Term]], tpe: TypeRepr, origin: Origin) extends Term
  /** `for (binding : iterable) body` (Java enhanced-for). */
  final case class ForEach(binding: ValDef, iterable: Term, body: Term, tpe: TypeRepr, origin: Origin, label: Option[String] = None) extends Term
  /** `for (init; cond; update) body` (Java classic-for). */
  final case class For(init: List[Statement], cond: Option[Term], update: List[Statement], body: Term, tpe: TypeRepr, origin: Origin, label: Option[String] = None) extends Term
  /** `try (resources) body catch cases finally fin`. `resources` are the try-with-resources
    * bindings (empty for a plain `try`); each is auto-closed — a lowering concern for the
    * backend, kept structural here.
    *
    * @param id WHICH `try` this is, for the one question two `try`s must never share an answer to:
    *           did the emitter put a `Break` re-throw arm on it (`ENGINE-LIMITS.md` K16 / CLAUDE.md
    *           §4.4)? Neither of the two obvious keys can carry it —
    *
    *             - an `Origin` is a path, a line and a column, and a nested one-liner shares all
    *               three, while every SYNTHESISED `try` carries `Origin.synthetic`;
    *             - object identity does not survive `StandardTraversal`, which rebuilds every node
    *               it walks (a `scan` is a `map` with a side effect), so the emitter's node and the
    *               check's are different objects for the same `try`.
    *
    *           A token minted at construction survives both: `copy` carries it, so every rebuild is
    *           the SAME try, and two separately constructed `try`s are never confused. It is
    *           excluded from `equals`/`hashCode` — an identity is not part of the value — and it
    *           reaches no emitted text, no artifact and no printer, so nothing about a run's output
    *           depends on it. */
  final case class Try(resources: List[ValDef], body: Term, catches: List[CatchCase],
                       finalizer: Option[Term], tpe: TypeRepr, origin: Origin,
                       id: TryId = TryId.fresh()) extends Term:
    // structural equality EXCLUDING `id`: every existing comparison keeps meaning, and the token is
    // used where it is meant to be used — as an explicit key.
    override def equals(o: Any): Boolean = o match
      case t: Try => t.resources == resources && t.body == body && t.catches == catches &&
                     t.finalizer == finalizer && t.tpe == tpe && t.origin == origin
      case _      => false
    override def hashCode: Int = (resources, body, catches, finalizer, tpe, origin).hashCode
  /** one `catch (param) body`; `param.tpt` may be an `OrType` for multi-catch. */
  final case class CatchCase(param: ValDef, body: Term)
  /** `scrutinee match { cases }` (from a Java switch).
    *
    * Carries a token for the same reason [[Try]] does: the `switch-null` check has to ask the
    * emitter "did you guard THIS one", and neither an `Origin` (two switches can share a path,
    * line and column, and every synthesised node carries `Origin.synthetic`) nor object identity
    * (`StandardTraversal` rebuilds every node it walks) can answer that. */
  final case class Match(scrutinee: Term, cases: List[CaseDef], tpe: TypeRepr, origin: Origin,
                         /** was this a switch EXPRESSION in the java (JLS 15.28), or a switch
                           * STATEMENT (14.11)?
                           *
                           * One `Tree.Match` renders both, because a scala `match` IS an expression
                           * — but the two java constructs BIND A `yield` differently and nothing
                           * else in the node says which one this was. JLS 14.21 sends a `yield` to
                           * the innermost enclosing switch EXPRESSION, and a switch STATEMENT
                           * between it and its target is simply an intervening construct: javac
                           * (22.0.2) runs `case 1 -> { switch (b) { case 2: yield 10; … } yield 20; }`
                           * and answers 10. Read as "any `Tree.Match` re-binds a yield", the outer
                           * arm got no value-carrying boundary and the inner statement switch
                           * minted one at ITS own type — a `break(10)` at a `Label[Unit]`, which is
                           * loudly uncompilable.
                           *
                           * `tpe` cannot stand in for this. A statement switch types as `Unit` and
                           * a switch expression never does, so the pair happens to be
                           * distinguishable today — but that is a coincidence of java's rule that a
                           * switch expression has a value, not a fact this IR states, and a phase
                           * that retypes a `Match` would silently decide a parsing question. */
                         isExpr: Boolean = false,
                         id: MatchId = MatchId.fresh()) extends Term:
    // structural equality EXCLUDING `id`, exactly as `Try` does — every existing comparison keeps
    // meaning and the token is used only where it is meant to be, as an explicit key. `isExpr` is
    // IN, because two matches that bind a `yield` differently are not the same node.
    override def equals(o: Any): Boolean = o match
      case m: Match => m.scrutinee == scrutinee && m.cases == cases && m.tpe == tpe &&
                       m.origin == origin && m.isExpr == isExpr
      case _        => false
    override def hashCode: Int = (scrutinee, cases, tpe, origin, isExpr).hashCode
  /** one case: `labels` are the constant patterns (empty ⇒ `default`/`case _`). */
  final case class CaseDef(labels: List[Term], guard: Option[Term], body: Term, isDefault: Boolean)
  /** a method value `qualifier :: method` (`Foo::bar`, `x::baz`, `Foo::new`).
    *
    * [[referent]] is what the PARSER read off the referenced executable, and it is on the node
    * because the two facts it carries are not reliably on the SYMBOL. An external member is
    * interned with no `Flags` at all, so `flags.isStatic` answers `false` for `java.util.Objects`'s
    * statics; and its `info` is `NoType` wherever one slot cannot be named scope-free, so
    * `Comparable::compareTo`'s `MethodType` is absent because its one parameter is a type VARIABLE.
    * Both then read as a positive statement about java — *not static*, *takes no arguments* — which
    * is `CLAUDE.md` §4.6's fabricated fact at a method reference, and the two happen to fail in
    * OPPOSITE directions (a static expanded as an unbound instance reference, an unbound instance
    * reference expanded with no arguments). The arity survives a lenient parse even where the TYPES
    * do not: `getParameters` erases what each slot says, never how many there are. */
  final case class MethodRef(qualifier: Either[TypeTree, Term], method: SymId, tpe: TypeRepr,
                             origin: Origin, referent: Referent) extends Term
  /** `break` / `break label` — loop/switch exit. `tpe` is Nothing. */
  final case class Break(label: Option[String], tpe: TypeRepr, origin: Origin)          extends Term
  /** `continue` / `continue label`. `tpe` is Nothing. */
  final case class Continue(label: Option[String], tpe: TypeRepr, origin: Origin)        extends Term

  /** `yield v` from a switch EXPRESSION's arm, at a position that is NOT the arm's value — JLS
    * 14.21.
    *
    * Only the non-tail ones reach the IR, and that is the whole design. A `yield` written as the
    * last statement of an arm is the arm's VALUE and needs no node: the frontend peels it into the
    * arm block's result term, which is what scala's `match` arm already means. One written anywhere
    * else — inside an `if`, inside a nested block, before another statement — is an ABRUPT
    * COMPLETION of the enclosing switch expression from arbitrary depth, and scala has no
    * expression-level jump. Its exact image is a value-carrying `scala.util.boundary`, which the
    * emitter interposes around the ARM exactly as it interposes one for a mid-case `break`, and
    * this node is what tells it to.
    *
    * `tpe` is Nothing: a `yield` completes abruptly, so it produces no value where it stands — the
    * value it carries belongs to the switch expression. Modelled as [[Break]] and [[Return]] are,
    * for the same reason.
    *
    * A `yield` NEVER reaches a switch STATEMENT's arm. Spoon normalises an arrow-form statement
    * arm (`case 1 -> doIt();`) into a `CtYieldStatement` wrapping the statement expression, which
    * is a parser artifact and not java — JLS 14.21 permits `yield` only inside a switch expression
    * — so the frontend unwraps it there rather than carrying a node java does not have. */
  final case class Yield(value: Term, tpe: TypeRepr, origin: Origin)                     extends Term

  /** `case String s ->` — java's TYPE PATTERN standing as a case label (JLS 14.11.1, 14.30.1).
    *
    * A `Term` because [[CaseDef.labels]] is a list of them, and valid in NO OTHER POSITION: the
    * emitter renders it `name: Type`, which is a pattern and not an expression. Modelled as a node
    * rather than as a `Typed(Ident(bind), tpt)` because those two mean different things — a `Typed`
    * is an ascription of a term that already exists, and this BINDS one that does not.
    *
    * `bind` is a local symbol the frontend mints for the pattern's variable, interned against the
    * enclosing `CtCase` rather than against the variable: java lets two arms of one switch bind the
    * same NAME, and Spoon gives the pattern wrapper no source position for a per-variable key to
    * use, so keying on the variable would intern both arms' bindings as one symbol with one type.
    *
    * A RECORD pattern is [[RecordPattern]] and an UNCONDITIONAL component pattern is [[BindPattern]]
    * — three nodes for three different scala texts, rather than one node with a flag. */
  final case class TypePattern(bind: SymId, tpt: TypeTree, tpe: TypeRepr, origin: Origin) extends Term

  /** `case Point(int x, int y) ->` — java's RECORD PATTERN (JLS 14.30.1), as a case label.
    *
    * Scala's constructor pattern is the exact image, and the languages differ only in what they
    * deconstruct THROUGH: java calls the record's ACCESSORS and scala calls an `unapply`. `JS-C43`
    * derives one over exactly those accessors on every emitted record, which is what makes this
    * node renderable at all — and what makes it EXACT rather than approximate, because an overridden
    * accessor changes what java binds and would not change what a case class's generated extractor
    * bound (`ENGINE-LIMITS.md` T19, T20).
    *
    * `patterns` are the component patterns IN ORDER, each a [[BindPattern]], a [[TypePattern]] or a
    * nested `RecordPattern`. Which of the first two a component takes is JLS 14.30.2's own
    * distinction and it is not cosmetic: an UNCONDITIONAL component pattern matches a `null`
    * component and a scala type test does not (measured, both languages).
    *
    * `tpt` is the RECORD's type. The emitter names the extractor through it — the companion's VALUE
    * path — never through the scrutinee, which may be any supertype. */
  final case class RecordPattern(tpt: TypeTree, patterns: List[Term], tpe: TypeRepr, origin: Origin) extends Term

  /** `case Point(x, y)`'s `x` — a component binding with NO type test (JLS 14.30.2's UNCONDITIONAL
    * pattern), which is what java writes when the pattern's type already covers the component's.
    *
    * Its own node and not a [[TypePattern]] whose test happens to be trivial, because the two are
    * different scala programs at a `null` component: `case One(s)` binds `null` and `case One(s:
    * String)` does not match at all. Java's answer is the first (measured: `new One(null)` matches
    * `case One(String s)` and does NOT match `case Two(String s, int n)` where the component is
    * `Object`), so the two cases must be spellable apart.
    *
    * Valid only INSIDE a [[RecordPattern]]: a bare binding standing as a whole case label would be
    * scala's catch-all, which is a different arm from java's. */
  final case class BindPattern(bind: SymId, tpe: TypeRepr, origin: Origin) extends Term

  /** `name: stmt` — a java label on a statement that is NOT a loop, the target of `break name`.
    *
    * Java's `LabeledStatement` accepts ANY statement, and `break L` leaves exactly that statement
    * — an `if`, a bare block and a `switch` are all legal targets, and all three occur in the
    * corpus (`JsonReader`'s `outer:` on an `if`, `TextField`'s `keys:`/`selection:` on blocks,
    * `GlyphLayout`'s `runEnded:` on a block). Modelled as a WRAPPER rather than as an `Option`
    * field on every statement node for the obvious reason, and because the boundary the backend
    * emits really is a construct around the statement.
    *
    * LOOPS do NOT use this node: `While`/`For`/`ForEach`/`DoWhile` carry the label in their own
    * `label` field, because a loop's label is the target of `continue L` as well, and the two
    * jumps need boundaries in DIFFERENT places (around the loop / around its body). Splitting
    * them would put that decision in two places; the frontend is the one that chooses, and it
    * only mints a `Labeled` for a non-loop statement.
    *
    * `tpe` is Unit — java's labelled statement is a statement, never a value. */
  final case class Labeled(name: String, stmt: Term, tpe: TypeRepr, origin: Origin)      extends Term
  /** `assert cond` / `assert cond : msg`. `tpe` is Unit. */
  final case class Assert(cond: Term, msg: Option[Term], tpe: TypeRepr, origin: Origin)  extends Term
  /** `i++` / `++i` / `i--` / `--i` as an expression; `op` is `"+"`/`"-"`, `post` the position. */
  final case class IncDec(target: Term, op: String, post: Boolean, tpe: TypeRepr, origin: Origin) extends Term
  /** `do body while (cond)`. `tpe` is Unit. */
  final case class DoWhile(body: Term, cond: Term, tpe: TypeRepr, origin: Origin, label: Option[String] = None) extends Term
  /** `synchronized (lock) body`. `tpe` is Unit. */
  final case class Synchronized(lock: Term, body: Term, tpe: TypeRepr, origin: Origin)   extends Term

  /** THE MARKER — a construct with no faithful Scala image, recorded IN THE TREE.
    *
    * `DESIGN.md` §6.2, built. What §3.4 forbids is not best effort; it is SILENT best effort, and
    * until this node existed the engine had two answers and no third. `TirEmitter.unrenderable` is
    * one — four call sites, whose shipping-default output is a comment and `()`, in a mode no
    * measure lane runs. `SpoonTir.unsupported` is the other, and it is a THROW that is not
    * per-site: it fails the whole COMPILATION UNIT, so one unmodelled declaration in a 135-file
    * library does not cost one node, it costs that file. This is the per-site record that makes
    * adopting a new syntax family an incremental, measured step instead of an all-or-nothing one.
    *
    * ==Why it lives in the tree==
    *
    * §6.2 rejects three alternatives, each for a reason this codebase has already paid for once,
    * and they are not re-argued here. The short form: trees have no identity — [[StandardTraversal]]
    * rebuilds every node on every phase — so a side table has nothing stable to key on and a phase
    * that deletes a subtree leaves an entry that either vanishes silently or falsely blocks; a
    * `diag` field on every node touches every construction site for something empty almost
    * everywhere; and symbol tags cannot pin the exact expression that the fence, the error
    * correlation and the diff all need.
    *
    * ==What a phase sees==
    *
    * The traversal recurses INTO [[inner]] and rebuilds the wrapper, so every phase's hooks reach
    * the approximation exactly as they reach any other term — and a phase that pattern-matches for
    * a specific shape simply fails to match a wrapped one and leaves it alone. '''The safe default
    * is marker-preserved, code-untouched.''' Erasing a marker requires deliberately matching it and
    * constructing a replacement, which is the point: discharge is an explicit act
    * ([[MarkerState.Resolved]]), and `MarkerCheck` is what tells a discharge from an erasure. Those
    * two are the same size in the output and nothing like each other in a port.
    *
    * ==What emission does==
    *
    * An `Open` marker is never shipped. In deliverable mode the gate runs before anything is
    * written (§6.4) and the tree is not written at all; the emitter, asked to render one anyway —
    * which is what a fixture with no orchestrator around it does — emits `compiletime.error`, so
    * the loudest available answer is also the DEFAULT one. In best-effort mode it renders as
    * [[inner]] inside deterministic comment fences and the file gains a banner. A `Resolved` marker
    * renders as its inner and nothing else: it is a record of work done, not a residue.
    *
    * @param inner the approximation. For a construct with no approximation at all this is the unit
    *   literal, and it is deliberately NOT an `Option`: every consumer would then carry a branch
    *   for "no term here", and a node the emitter cannot render is one the gate catches anyway
    * @param kind  the taxonomy — a CLOSED engine enum ([[UnportableKind]])
    * @param state `Open` until a phase discharges it
    * @param diff  the `balticporter.catalog` row this marker is an INSTANCE of, where one names it.
    *   `None` is an honest state: inventing a row to point at would make the registry describe the
    *   engine's failures instead of Java and Scala
    * @param what  one line, in the mint site's own words. The taxonomy says which FAMILY the
    *   failure belongs to; this says which member of it
    */
  final case class Unportable(inner: Term, kind: UnportableKind, state: MarkerState,
                              diff: Option[balticporter.catalog.DiffId], what: String,
                              tpe: TypeRepr, origin: Origin) extends Term:

    /** discharged BY A NAMED PHASE, with what it did. The replacement term is the caller's to
      * build; this records that the marker was ANSWERED rather than deleted. */
    def resolved(byPhase: String, how: String): Unportable =
      copy(state = MarkerState.Resolved(byPhase, how))

    /** the identity a CONSERVATION check compares two programs on.
      *
      * Not object identity and not a `SymId`: the traversal rebuilds every node on every phase —
      * §6.2's own reason for rejecting a side table — and a marker is minted at an EXPRESSION,
      * which has no symbol. The origin plus the mint site's own words is what survives a rebuild,
      * and it is distinguishing only because [[Unportable.open]] refuses a synthetic origin:
      * `<synthetic>:0:0` would collapse every marker in the program onto one key, and the check
      * keyed on it would then report nothing, confidently. */
    def markerKey: String = s"${kind.label}@${origin.javaPath}:${origin.line}:${origin.col}|$what"

  object Unportable:

    /** MINT one. Refuses a synthetic origin — §6.2's rule that *a marker must point at real Java*,
      * and the precondition [[Unportable.markerKey]] depends on. A mint site with no position has
      * to keep whatever loud answer it had; that is a worse outcome for one node and a truthful
      * one, where a marker nothing can locate is neither. */
    def open(inner: Term, kind: UnportableKind, diff: Option[balticporter.catalog.DiffId],
             what: String, tpe: TypeRepr, origin: Origin): Unportable =
      require(origin != Origin.synthetic && origin.javaPath.nonEmpty,
        s"Unportable.open: a marker must point at real Java, and this one has no position ($what)")
      Unportable(inner, kind, MarkerState.Open, diff, what, tpe, origin)

    /** the fence a BEST-EFFORT emission wraps an open marker in. Comment-shaped and deterministic:
      * a comment cannot change program shape, which is the whole reason the fence is admissible
      * (§6.4), and determinism is what lets `diff -r` of two run directories mean anything. */
    def fence(m: Unportable): (String, String) =
      (s"/* balticporter:unportable ${m.kind.label}${m.diff.fold("")(d => s" $d")} — ${safe(m.what)} */",
       "/* balticporter:end-unportable */")

    /** a fence may never OPEN or CLOSE a comment: Scala block comments NEST (§4.58), so a block
      * opener in the mint site's own words would swallow the rest of the file. This very scaladoc
      * failed to compile the first time it was written, which is the shortest available argument
      * that the rule is not theoretical. */
    def safe(s: String): String = s.replace("/*", "/ *").replace("*/", "* /")

  /** an as-yet-unmodeled TERM, kept typed (a full structured `tpe`) so the tree stays
    * whole while the node set grows. TYPES are never opaque; only unmodeled terms are.
    *
    * ==`holes` — ready-made Scala with TREES spliced into it==
    * The plain form is closed text: a hand-written method body, a generated FFI downcall. A
    * CALL-SITE substitution cannot be, because its replacement has to name the receiver and the
    * arguments of the call it replaces, and those are TERMS the phase is holding — a `Buffer` the
    * collections phase has already retyped, an argument that is itself a call. So `raw` may carry
    * NUMBERED HOLES ([[Opaque.hole]]) and `holes` supplies one term per index; the emitter renders
    * each hole with its own term rendering and splices the result ([[spliced]]).
    *
    * Three properties follow from the holes being trees rather than text, and each of them is the
    * reason it is done this way:
    *
    *   - '''a later phase still sees them.''' `StandardTraversal` maps into `holes`, so the package
    *     rename, the collections retyping and every check reach a spliced argument exactly as they
    *     reach any other term. Text would be invisible to all of them — the failure §4.56 describes
    *     for policy written in the wrong namespace, one layer down.
    *   - '''the xref still records them.''' A symbol used only inside a hole is a real usage; read
    *     as text it would look dead.
    *   - '''nothing has to render Scala outside the emitter.''' A phase cannot print a term (the
    *     emitter is the only thing that can), which is precisely why the substitution cannot be
    *     performed as a string at the phase.
    *
    * `holes = Nil` — the default, and every existing construction site — is the closed form, and
    * [[spliced]] then returns `raw` untouched: no marker scan runs, so text that happens to contain
    * one cannot be misread. */
  final case class Opaque(raw: String, tpe: TypeRepr, origin: Origin, holes: List[Term] = Nil)
      extends Term:

    /** `raw` with each hole replaced by `render` of the term it names.
      *
      * A hole index with no term is left as the marker rather than dropped or defaulted: it can
      * only arise from a malformed construction, and a marker that survives into the output is a
      * compile error naming the file, where a silent drop would be a wrong program that compiles. */
    def spliced(render: Term => String): String =
      if holes.isEmpty then raw
      else
        val sb = new StringBuilder
        var i  = 0
        while i < raw.length do
          val c = raw.charAt(i)
          if c != Opaque.Mark then { sb.append(c); i += 1 }
          else
            val close = raw.indexOf(Opaque.Mark.toInt, i + 1)
            val idx   = if close < 0 then scala.None else raw.substring(i + 1, close).toIntOption
            idx.filter(holes.indices.contains) match
              case Some(n) => sb.append(render(holes(n))); i = close + 1
              case scala.None => sb.append(c); i += 1
        sb.toString

  object Opaque:
    /** The hole delimiter: NUL, which cannot occur in Scala source and therefore needs no escape
      * grammar. A printable delimiter would need one, and an escape grammar over ready-made Scala
      * is a second parser for text the engine deliberately does not parse. */
    val Mark: Char = 0.toChar

    /** the marker for hole `i`, as it appears inside [[Opaque.raw]]. */
    def hole(i: Int): String = s"$Mark$i$Mark"

    /** `parts` interleaved with holes `0 … parts.size - 2`. `parts` always has one more element
      * than `holes`; a mismatch is a construction bug and is refused here rather than emitted. */
    def spliced(parts: List[String], holes: List[Term], tpe: TypeRepr, origin: Origin): Opaque =
      require(parts.size == holes.size + 1,
        s"Opaque.spliced: ${parts.size} literal parts for ${holes.size} holes — expected ${holes.size + 1}")
      Opaque(parts.head + parts.tail.zipWithIndex.map((p, i) => hole(i) + p).mkString, tpe, origin, holes)

  /** A statement with the comments written above it. The one node that exists purely to carry
    * [[Trivia]], and the reason DECLARATIONS do not need one: a field, method or class has a
    * `leading` field of its own, while a STATEMENT inside a body is an ordinary `Term` with
    * nowhere to put it and forty case classes that would each need the field.
    *
    * It is transparent to everything by design. `tpe` and `origin` delegate, so a `Commented`
    * types and locates exactly as its statement does; [[StandardTraversal]] recurses into `stmt`
    * and REBUILDS the wrapper, so a phase that overrides no hook passes it through untouched and
    * a phase that rewrites the inner term keeps the comment. The frontend only ever wraps a
    * statement that HAS a comment, so a body with none is byte-for-byte the tree it was before
    * this node existed.
    *
    * What it is NOT transparent to is a pattern match on statement SHAPE — `stmtsOf(ctor).head
    * match { case Tree.Apply(Tree.Select(…)) => … }` stops matching the moment somebody writes a
    * comment above a `super(…)` call, and that particular one silently drops the super call
    * (CLAUDE.md §4.4). Every such site matches through [[Uncommented]] instead; grep for it before
    * writing a new one. */
  final case class Commented(leading: List[Trivia], stmt: Term) extends Term:
    def tpe: TypeRepr  = stmt.tpe
    def origin: Origin = stmt.origin

  /** See THROUGH any number of [[Commented]] wrappers — the extractor form, for a pattern match on
    * a statement's shape: `case Tree.Uncommented(Tree.Apply(fun, args, _, _, _)) => …`. Matches
    * every term, wrapped or not, so it is a drop-in around an existing pattern. */
  object Uncommented:
    def unapply(t: Term): Some[Term] = Some(uncomment(t))

  /** the statement itself, with its comment wrappers removed. */
  @annotation.tailrec
  def uncomment(t: Term): Term = t match
    case Commented(_, inner) => uncomment(inner)
    case other               => other

  /** the trivia on `t`, outermost first — `Nil` for an unwrapped statement. */
  def triviaOn(t: Term): List[Trivia] = t match
    case Commented(l, inner) => l ++ triviaOn(inner)
    case _                   => Nil

  /** put `original`'s comments back on a `rewritten` replacement for it. A rewrite that builds a
    * fresh statement (rather than `copy`-ing) drops the wrapper, and the comment goes with it. */
  def recomment(original: Term, rewritten: Term): Term =
    triviaOn(original) match
      case Nil => rewritten
      case ts  => Commented(ts, uncomment(rewritten))

// ---------------------------------------------------------------------------
// Whole-program index + program — our layer BEYOND Quotes.
// ---------------------------------------------------------------------------

/** Interned symbol store. Tagging returns a new table (immutable). */
final class SymbolTable(private val syms: Map[SymId, Symbol]):
  def apply(id: SymId): Symbol       = syms(id)
  def get(id: SymId): Option[Symbol] = syms.get(id)
  def all: Iterable[Symbol]          = syms.values
  def withTag(id: SymId, tag: SymTag): SymbolTable =
    new SymbolTable(syms.updated(id, syms(id).copy(tags = syms(id).tags + tag)))
  def updated(sym: Symbol): SymbolTable = new SymbolTable(syms.updated(sym.id, sym))
object SymbolTable:
  def apply(syms: Iterable[Symbol]): SymbolTable = new SymbolTable(syms.map(s => s.id -> s).toMap)

/** The POSITION a symbol is used in — so a query can ask "used as a mixin / type
  * argument / member type / bound / …", not merely "used". This is what makes the
  * sge/ssg rewrites tractable: java→scala collections retype every `MemberType` and
  * `TypeArg` site; Int→opaque retypes `TypeArg`/`MemberType` flows; a mixin swap edits
  * `Mixin`/`Extends`. Type positions are first-class, not just call sites. */
enum UsageKind:
  case Call        // invoked as a method (`Apply.method`)
  case TermRef     // referenced as a term/value/object (`Ident`/`Select`)
  case Instantiate // `new T(...)` — the constructed type
  case Extends     // primary supertype in a class's parent list
  case Mixin       // an additional mixin parent, or a member of an `A & B` intersection
  case SelfType    // a class self-type `self: S =>`
  case TypeArg     // a type argument in `F[Arg]` — i.e. a type-PARAMETER position
  case Tycon       // the constructor being applied in `F[...]`
  case Bound       // inside a `TypeBounds` — a type-param bound (incl. F-bound) or wildcard
  case MemberType  // the declared type of a field / val / param / def-result
  case TypeRefPos  // any other type-reference position (prefix, alias rhs, ascription)

/** One recorded use of a symbol: WHERE (`kind`), at which tree node (`site`), and inside
  * which enclosing definition (`enclosing` — the nearest containing method/field/class
  * symbol). `enclosing` is what makes `callersOf` a real call-graph edge. */
final case class Usage(kind: UsageKind, site: Tree, enclosing: SymId = SymId.None)

/** Whole-program cross-reference index: symbol → its definition, and symbol → every
  * usage site KINDED by position (term refs AND every type position — external type,
  * type argument, member type, mixin, bound, self-type — since `TypeRef`/`TermRef`
  * name symbols wherever they occur). This is what Quotes cannot give you across a
  * program. Built by [[Xref.build]] over the tree, so it RE-DERIVES after each phase
  * rewrites — the pipeline rebuilds it between phases. */
final class XrefIndex(
    private val defs: Map[SymId, Definition],
    private val usagesBySym: Map[SymId, List[Usage]],
):
  def definitionOf(s: SymId): Option[Definition] = defs.get(s)
  /** all usage sites of `s`, any position. */
  def usagesOf(s: SymId): List[Tree] = usagesBySym.getOrElse(s, Nil).map(_.site)
  /** kinded usages of `s` — ask "where, and how". */
  def usages(s: SymId): List[Usage] = usagesBySym.getOrElse(s, Nil)
  /** usage sites of `s` restricted to one position kind. */
  def usagesOf(s: SymId, kind: UsageKind): List[Tree] =
    usagesBySym.getOrElse(s, Nil).collect { case Usage(`kind`, site, _) => site }
  /** every symbol that has at least one recorded usage. */
  def referenced: Set[SymId] = usagesBySym.keySet

/** The transform substrate: all units, the symbol table, the xref index. Project-
  * owned transformers receive this, query it (Quotes-familiar), and rewrite. */
final class Program(
    val units: List[Tree.ClassDef],
    val symbols: SymbolTable,
    val xref: XrefIndex,
    /** WHAT THE FRONTEND SAW, dropped members included — see [[MemberIndex]].
      *
      * '''A REQUIRED parameter, not a defaulted field, and that is the whole design of this
      * commit.''' There are 28 `new Program(...)` sites outside tests and `Pipeline.runTraced`
      * rebuilds a fresh `Program` after EVERY phase; a default would have been silently dropped at
      * 27 of them and at every phase boundary — a regression no check count could see, which is
      * precisely the class of defect this repository has shipped before. Required, the migration is
      * mechanical and the compiler performs it. [[rebuilt]] is then THE way a phase returns a
      * program, so the next whole-program value costs one line instead of another 28. */
    val members: MemberIndex,
):
  export xref.{definitionOf, usagesOf, usages, referenced}

  def symbolOf(id: SymId): Option[Symbol] = symbols.get(id)

  /** Rebuild after a rewrite, CARRYING EVERYTHING THE CALLER DID NOT CHANGE.
    *
    * `new Program` is for the frontend and the pipeline; a phase uses this. The difference is not
    * brevity: a phase that constructs a `Program` positionally has to remember every whole-program
    * value that exists today, and the one it forgets is dropped in silence. */
  def rebuilt(
      units: List[Tree.ClassDef] = this.units,
      symbols: SymbolTable = this.symbols,
      xref: XrefIndex = this.xref,
      members: MemberIndex = this.members,
  ): Program = new Program(units, symbols, xref, members)

  /** Symbols this program DECLARES, as opposed to externals the frontend interned on first
    * reference — CLAUDE.md §4.56's "decide ownership STRUCTURALLY, never by name", as a value.
    *
    * The frontend interns an external symbol lazily with `owner = SymId.None` and no `Definition`,
    * while everything the program declares hangs off a top-level unit through the `owner` chain. So
    * a symbol is owned iff climbing its owners reaches a [[units]] symbol — stronger than "has a
    * definition", which anonymous-class symbols do not.
    *
    * It lives here, on the substrate, because it is not one phase's business: a rename asks it
    * before rewriting a prefix, and any rule that takes a [[RuleScope]] must ask it before deciding
    * that a policy entry FIRED — an entry naming a JDK type otherwise matches the interned external,
    * does nothing at all, and is reported as having matched (the §1(b) silent no-op). A §1(c) rule
    * compiles against `balticporter-api` alone (DESIGN.md §3.2), so the predicate has to be reachable
    * from here or it will be written a third time.
    *
    * Fuel-bounded: a corrupt owner cycle must not hang a phase, and a symbol that exhausts the fuel
    * counts as NOT owned — deciding on a guess is the failure this predicate exists to prevent. */
  lazy val owned: Set[SymId] =
    val roots = units.map(_.symbol).toSet
    def rooted(s: SymId, fuel: Int): Boolean =
      s != SymId.None && fuel > 0 && (roots(s) || symbols.get(s).exists(sym => rooted(sym.owner, fuel - 1)))
    symbols.all.collect { case s if rooted(s.id, 64) => s.id }.toSet

  /** does THIS program declare `id`? See [[owned]]. */
  def owns(id: SymId): Boolean = owned(id)

  /** the symbol a tree defines or references, if any (`reflect`-style `.symbol`). */
  def symbolIn(t: Tree): Option[SymId] = t match
    case d: Definition                   => Some(d.symbol)
    case Tree.Ident(s, _, _)             => Some(s)
    case Tree.Select(_, s, _, _)         => Some(s)
    case Tree.Apply(_, _, m, _, _)       => Some(m)
    case _                               => scala.None

  /** methods that call `method` (a call-graph edge) — walked by globals→implicits. Uses
    * each call usage's recorded `enclosing` definition, climbing to the nearest method. */
  def callersOf(method: SymId): List[SymId] =
    xref
      .usages(method)
      .collect { case Usage(UsageKind.Call, _, enc) if enc != SymId.None => enc }
      .flatMap(enclosingMethod)
      .distinct

  private def enclosingMethod(from: SymId): Option[SymId] =
    ownerChain(from).find(id => symbols.get(id).exists(s => s.info.isInstanceOf[TypeRepr.MethodType | TypeRepr.PolyType]))

  private def ownerChain(s: SymId): LazyList[SymId] =
    LazyList.unfold[SymId, SymId](s) { id =>
      if id == SymId.None then scala.None
      else Some((id, symbols.get(id).map(_.owner).getOrElse(SymId.None)))
    }
