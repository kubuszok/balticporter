package balticporter.frontend.spoon

import balticporter.core.{AnnotationPolicy, FrontendConfig, RealPath, Substituted, Substitutions}
import balticporter.catalog.{CatalogLog, Dispatch, JS, Lowering, Obligations, Typing}
import balticporter.tir.*
import balticporter.tir.TypeRepr.*

import spoon.Launcher
import spoon.reflect.code.*
import spoon.reflect.declaration.*
import spoon.reflect.reference.*
import spoon.support.adaption.TypeAdaptor
import spoon.support.compiler.VirtualFile

import scala.jdk.CollectionConverters.*

/** Populates the typed IR ([[balticporter.tir]]) DIRECTLY from Spoon's resolved model —
  * the re-compiler's build-order step 2. Unlike the BIR frontend, nothing collapses to
  * strings: every declaration mints a stable-identity [[Symbol]], every type reference
  * resolves to a structured [[TypeRepr]] pointing at a symbol, and externals (JDK/library
  * types) are lazily interned so `usagesOf(java.util.List)` works even with no local
  * definition. [[Xref.build]] then indexes every usage by position.
  *
  * Scope: declarations, signatures, TYPES, and method BODIES — the full substrate the
  * whole-program transforms query. Bodies translate to TIR terms with every reference
  * resolved to a `SymId` (see [[Builder.BodyTranslator]]), so `usagesOf`/`callersOf` are
  * real over actual code. Type-position tracing includes class/method type-parameter
  * F-bounds. The whole liqp corpus (135 types) translates with no `Unsupported`.
  */
object SpoonTir:

  /** the PUBLIC instance methods of `java.lang.Object`, by Spoon signature — the exclusion JLS 9.8
    * makes when counting a functional interface's abstract methods, and the reason
    * `java.util.Comparator` (which redeclares `equals`) is one.
    *
    * `clone` and `finalize` are deliberately absent: they are `protected`, so JLS 9.8 does not
    * exclude them and an interface redeclaring one abstract really does have two. */
  private[spoon] val ObjectPublicSignatures: Set[String] = Set(
    "equals(java.lang.Object)", "hashCode()", "toString()", "getClass()",
    "notify()", "notifyAll()", "wait()", "wait(long)", "wait(long,int)")

  /** the three SENTINEL entries `annotationsOf` can put in `Symbol.droppedAnnotations` beside real
    * annotation names, so `omissions` distinguishes the reasons a drop happened.
    *
    * `<unresolved>` (the oldest) is an annotation whose TYPE would not resolve — there is no name to
    * report. `<unreadable-annotations>` is the whole SET failing to read, which used to be an
    * uncounted `Nil` and therefore a declaration that looked as though it carried none.
    * `<annotation-arguments-failed>` marks the one drop that is an ENGINE DEFECT rather than
    * policy: the constant-expression path threw. Sentinels rather than decorated names, because
    * `Symbol.droppedAnnotations` is documented as annotations BY NAME and every consumer matches an
    * FQN exactly (`TestFrameworkTransform` must still recognise a `@Test` whose arguments would not
    * translate). */
  val UnresolvedAnnotation = "<unresolved>"
  val UnreadableAnnotations = "<unreadable-annotations>"
  val FailedAnnotationArguments = "<annotation-arguments-failed>"

  /** Build a [[Program]] from already-resolved top-level Spoon types.
    *
    * `catalog` is the run's OBLIGATION LOG (`balticporter.catalog`). It is a parameter and not a
    * field of the returned `Program` for the reason `DecisionLog` is a parameter of the pipeline: a
    * log is a value ONE RUN owns (`CLAUDE.md` §5.1), and `Determinism.Full` translates twice — two
    * translations sharing one log would double every consult in it. The default is a fresh
    * discarding log, so a caller that does not want coverage does not have to hold one, and nothing
    * accumulates across two calls. */
  def fromTypes(types: List[CtType[?]], subs: Substitutions = Substitutions.none,
                catalog: CatalogLog = CatalogLog.discarding,
                annotations: AnnotationPolicy = AnnotationPolicy.none): Program =
    new Builder(subs, catalog = catalog, annotations = annotations).build(types)

  /** Build the Spoon model over a whole closure and return its top-level types. Full
    * classpath by default (like the BIR frontend); `lenient` uses noClasspath mode so a
    * library with unconfigured external deps still parses (types resolve where possible,
    * unresolved ones degrade to unmapped references — fine for construct coverage). */
  def buildModel(cfg: FrontendConfig, lenient: Boolean = false): List[CtType[?]] =
    val launcher = new Launcher
    val env      = launcher.getEnvironment
    env.setComplianceLevel(21)
    // Comments are PART OF THE PORT (see `Builder.triviaOf`): the licence notice every emitted file
    // is obliged to reproduce, and the documentation that makes the output readable. With this off
    // Spoon attaches none of them and the whole harvest below sees an empty model.
    env.setCommentEnabled(true)
    env.setNoClasspath(lenient)
    env.setSourceClasspath(cfg.classpath.map(_.toString).toArray)
    if cfg.resolutionRoots.nonEmpty then
      cfg.resolutionRoots.foreach(r => launcher.addInputResource(r.toString))
      // §5.4 on both operands, and STRICT on both: these are DECLARED inputs, so an absent one is
      // fatal with a diagnostic naming it (§5.1's missing-input rule) rather than a bare
      // `NoSuchFileException` from deep inside a `map`. `RealPath.of` would be worse than either —
      // it would normalise the absent path and hand the resolver a root that is not there.
      val covered = cfg.resolutionRoots.map(r => RealPath.ofExisting(r, "resolution root"))
      cfg.files
        .map(f => RealPath.ofExisting(cfg.sourceRoot.resolve(f), s"declared source file $f"))
        .filterNot(abs => covered.exists(abs.startsWith))
        .foreach(abs => launcher.addInputResource(abs.toString))
    else cfg.files.foreach(f => launcher.addInputResource(cfg.sourceRoot.resolve(f).toString))
    launcher.buildModel().getAllTypes.asScala.toList.filter(_.getDeclaringType == null)

  /** Translate each top-level type in ISOLATION (fresh symbol space), returning per-type
    * success (symbol count) or the failure. Used to MEASURE corpus coverage — which
    * constructs still hit `Unsupported` — without one bad file sinking the batch. */
  def coverage(types: List[CtType[?]]): List[(String, Either[Throwable, Int])] =
    types.map { t =>
      t.getQualifiedName -> scala.util.Try(new Builder().build(List(t)).symbols.all.size).toEither
    }

  /** Convenience for tests / snippets: parse one in-memory source (no external classpath;
    * JDK types resolve by qualified name) and populate the TIR from its top-level types. */
  def fromSource(code: String, fileName: String = "Snippet.java",
                 subs: Substitutions = Substitutions.none,
                 catalog: CatalogLog = CatalogLog.discarding,
                 annotations: AnnotationPolicy = AnnotationPolicy.none): Program =
    fromSources(List(fileName -> code), subs, catalog, annotations)

  /** The same, over SEVERAL compilation units — because a Java file holds exactly one package, and
    * every rule about a PACKAGE BOUNDARY (default access, `protected`, a cross-package override)
    * is therefore untestable from one snippet. Each pair is `fileName -> code`.
    *
    * The buffer handed to the builder is the CONCATENATION, which is only used to slice comments
    * out by position; Spoon reports positions per compilation unit, so each unit's own text is
    * looked up by file name rather than by offset into a joined string. */
  def fromSources(sources: List[(String, String)],
                  subs: Substitutions = Substitutions.none,
                  catalog: CatalogLog = CatalogLog.discarding,
                  annotations: AnnotationPolicy = AnnotationPolicy.none): Program =
    val launcher = new Launcher
    val env      = launcher.getEnvironment
    env.setComplianceLevel(21)
    env.setCommentEnabled(true)
    env.setNoClasspath(true)
    sources.foreach((name, code) => launcher.addInputResource(new VirtualFile(code, name)))
    val model = launcher.buildModel()
    val tops  = model.getAllTypes.asScala.toList.filter(_.getDeclaringType == null)
    // the source texts are handed to the builder because a `VirtualFile` has no file behind it and
    // Spoon's `CtCompilationUnit.getOriginalSourceCode` therefore returns null — comments would
    // fall back to Spoon's RE-PRINTED form and this convenience API would quietly be the one path
    // that does not preserve them verbatim. It is the same buffer either way; only its source
    // differs.
    new Builder(subs, sources.toMap, catalog, annotations).build(tops)

  // -------------------------------------------------------------------------
  /** ==THE ONE CLASSIFICATION OF A SPOON TYPE REFERENCE==
    *
    * Spoon's `CtWildcardReference` EXTENDS `CtTypeParameterReference`, so `case tv:
    * CtTypeParameterReference` claims every `?` and a wildcard arm written BELOW it is unreachable.
    * Thirteen `match`es in this file had exactly that shape, each one deciding, on its own, that a
    * `?` is a type VARIABLE — which is the reading that makes `Class<?>` "not nameable here" and is
    * `ENGINE-LIMITS.md` G21's second blocker.
    *
    * `CLAUDE.md` §4.56 is the rule this discharges: a phase may only conclude something about a
    * type from a STRUCTURAL fact, and the structural fact here is the KIND of reference. It is
    * derived ONCE, in [[TypeShape.of]], with the wildcard arm ABOVE the variable arm — so growing a
    * kind is one edit, and no caller re-derives the taxonomy by `isInstanceOf`. What each caller
    * then does per kind is the CALLER's answer and is written out at the caller, including the
    * kinds it does not distinguish: `ref` and `args` are the two projections a caller needs to
    * treat `Prim`/`Intersection`/`Named` alike, which is what most of them did before by falling
    * into a `case r =>`.
    *
    * The migration itself changed NO answer (§5: the unification measured flat on all fifteen
    * ports). Where a wildcard's answer was the variable arm's, that answer is written out AS the
    * wildcard arm and marked — a preserved shadow, now visible and changeable one site at a time
    * with a measurement, rather than a dead arm nobody could see. */
  private[spoon] enum TypeShape:
    case Absent
    case Wildcard(w: CtWildcardReference, bound: Option[CtTypeReference[?]], upper: Boolean)
    case Variable(tv: CtTypeParameterReference)
    case Arr(a: CtArrayTypeReference[?], component: CtTypeReference[?])
    case Intersection(i: CtIntersectionTypeReference[?], bounds: List[CtTypeReference[?]])
    case Prim(p: CtTypeReference[?])
    case Named(r: CtTypeReference[?], as: List[CtTypeReference[?]])

    /** the reference this shape classifies — `null` only for [[Absent]]. */
    def ref: CtTypeReference[?] = this match
      case Absent             => null
      case Wildcard(w, _, _)  => w
      case Variable(tv)       => tv
      case Arr(a, _)          => a
      case Intersection(i, _) => i
      case Prim(p)            => p
      case Named(r, _)        => r

    /** the reference's own type ARGUMENTS exactly as `getActualTypeArguments` reports them, so a
      * caller that treats several kinds alike reproduces the `case r =>` it used to fall into. */
    def args: List[CtTypeReference[?]] = this match
      case Absent       => Nil
      case Named(_, as) => as
      case s            => TypeShape.actualArgs(s.ref)

  private[spoon] object TypeShape:
    private def actualArgs(r: CtTypeReference[?]): List[CtTypeReference[?]] =
      try r.getActualTypeArguments.asScala.toList catch { case _: Throwable => Nil }

    /** THE derivation. The WILDCARD arm is first, and that order is the whole point (see the enum's
      * doc): `CtWildcardReference <: CtTypeParameterReference`, so any other order silently answers
      * "type variable" for every `?` in the program. */
    def of(tr: CtTypeReference[?]): TypeShape = tr match
      case null                              => Absent
      case w: CtWildcardReference            => Wildcard(w, Option(w.getBoundingType), w.isUpper)
      case tv: CtTypeParameterReference      => Variable(tv)
      case a: CtArrayTypeReference[?]        => Arr(a, a.getComponentType)
      case i: CtIntersectionTypeReference[?] =>
        Intersection(i, try i.getBounds.asScala.toList catch { case _: Throwable => Nil })
      case p if (try p.isPrimitive catch { case _: Throwable => false }) => Prim(p)
      case r                                 => Named(r, actualArgs(r))

  /** Interns symbols by a stable string key (qualified names for types, `owner#member`
    * for members, `decl$$Name` for type params). One id per key, monotonic. */
  private final class Minter:
    private var next  = 0
    private val byKey = collection.mutable.Map[String, SymId]()
    private val syms  = collection.mutable.Map[SymId, Symbol]()

    def resolve(key: String): SymId =
      byKey.getOrElseUpdate(key, { val id = SymId(next); next += 1; id })

    def set(id: SymId, sym: Symbol): Unit = syms(id) = sym

    def define(key: String)(mk: SymId => Symbol): SymId =
      val id = resolve(key)
      syms(id) = mk(id)
      id

    /** Ensure a minimal stub exists for an external reference (never clobbers a real
      * definition, so define-after-reference wins).
      *
      * `owner` is `SymId.None` for a TYPE — an external type is by definition rooted outside the
      * program, and every "is this ours?" predicate in the engine decides exactly that by climbing
      * to `SymId.None` (`PackageRenameTransform.ownedSymbols`, `Cache.topOwner`). An external
      * MEMBER, however, must carry the id of the external type it hangs off, or it is
      * indistinguishable from a root: its `fullName` is the INTERNING key (`@8#forName(…)`), so
      * `owner#name` is the only place its real identity lives. Nine `PortabilityCheck` rules —
      * `Class#forName`, `Class#newInstance`, `System#getProperty` and the six reflective readers —
      * asked for exactly that string and got `None` from every external member for the whole
      * history of the project, so they never fired once. Ownership still terminates at `SymId.None`
      * one level up, so nothing that climbs the chain changes answer. */
    def external(key: String, name: String, owner: SymId = SymId.None,
                 descriptor: Option[Descriptor] = None, info: TypeRepr = NoType): SymId =
      val id = resolve(key)
      if !syms.contains(id) then syms(id) = Symbol(id, name, key, Flags(), owner, info, descriptor = descriptor)
      else
        // `external` NEVER clobbers, so a stub interned by an earlier, UNRESOLVED reference would
        // otherwise keep its empty descriptor for the whole run while a later, resolved one knew the
        // answer. The descriptor is the one field where filling a hole is strictly better information:
        // it is derived from the parser's own declaration and cannot contradict a previous fill.
        //
        // `info` fills the same way and for the same reason — and only ever a HOLE. A member the
        // program DECLARES gets its real signature from `execDef`, through `define`, which does
        // clobber; the fill here can therefore never overwrite a declaration, only precede one.
        var s = syms(id)
        if descriptor.isDefined && s.descriptor.isEmpty then s = s.copy(descriptor = descriptor)
        if info != NoType && s.info == NoType then s = s.copy(info = info)
        syms(id) = s
      id

    def table: SymbolTable        = SymbolTable(syms.values)
    def idOf(key: String): SymId  = byKey(key)
    /** the symbol at `key` IF one was really defined there.
      *
      * Deliberately not `resolve`: that MINTS an id for a key nobody defined, and a dangling id — a
      * `SymId` with no `Symbol` behind it — reaches the emitter as `?`. The one caller asks about a
      * member java DERIVED and this program may still not have (a record accessor a port's
      * `dropMethods` removed), where "there is nothing here" is the answer, not a new id. */
    def defined(key: String): Option[(SymId, Symbol)] =
      byKey.get(key).flatMap(id => syms.get(id).map(id -> _))
    def fullNameOf(id: SymId): String = syms.get(id).map(_.fullName).getOrElse("?")
    /** the DECLARED type this frontend interned for `id` — `NoType` where nothing was declared.
      * Read to answer *did this frontend RETYPE this declaration?*, which is §4.56's rule at the
      * one widening the frontend performs itself (`execDef.anyForEquals`). */
    def infoOf(id: SymId): TypeRepr = syms.get(id).map(_.info).getOrElse(NoType)
    /** the interned OWNER of a member — the type that DECLARES it, which for a member reached
      * through a subclass name is NOT the type the source wrote (T14). `SymId.None` for a type,
      * and for a member whose declaration the parse could not resolve, which is what makes reading
      * it a safe no-op rather than a guess. */
    def ownerOf(id: SymId): SymId = syms.get(id).map(_.owner).getOrElse(SymId.None)

  /** @param inMemorySources
    *   each compilation unit's text BY FILE NAME, for the units where Spoon has none of its own —
    *   see `fromSources`. Empty for a model built over real files, where every unit carries its own
    *   buffer. Keyed rather than a single string because two in-memory units have two buffers and
    *   one position is only meaningful in ONE of them: slicing unit B's comment out of unit A's
    *   text is exactly the silent mis-preservation §4.58 is about. */
  private final class Builder(subs: Substitutions = Substitutions.none,
                              inMemorySources: Map[String, String] = Map.empty,
                              catalog: CatalogLog = CatalogLog.discarding,
                              annotations: AnnotationPolicy = AnnotationPolicy.none):
    /** the run's obligation log, in scope for every `Lowering.of` in this builder. `given` rather
      * than a parameter on every lowering method: the wrapper is at the DISPATCH and the dispatch
      * is one method, so threading it explicitly would be forty signatures carrying a value one of
      * them uses. */
    private given CatalogLog = catalog
    private val minter   = new Minter
    private val tpScopes = collection.mutable.ArrayDeque[Map[String, SymId]]()
    /** an executable's own type parameters that are ERASED rather than declared — name → the type
      * every occurrence of the variable renders as. Parallel to `tpScopes` and consulted AHEAD of
      * it, because such a name has no binder to resolve to: it was never minted.
      *
      * One frame per executable, pushed and popped with that executable's `tpScopes` frame. See
      * [[unwritableResultVars]] for which parameters land here and why. */
    private val tpErased = collection.mutable.ArrayDeque[Map[String, TypeRepr]]()
    private val selfRawStack = collection.mutable.ArrayDeque[(SymId, List[SymId])]()
    /** Type params LEGALLY in scope at the current point, respecting static-nested boundaries: a
      * static nested class / interface / enum cannot see its enclosing type's params, unlike a
      * non-static inner class. Distinct from `tpScopes`/`resolveTypeParam`, which keep every
      * enclosing frame for reference resolution — name-directed raw-fill must NOT emit a param the
      * emitted Scala can't see (that produced `Not found: type T` inside static-nested `SaveData`). */
    private val tpAccessible = collection.mutable.ArrayDeque[Map[String, SymId]]()
    /** names contributed by EXECUTABLES, parallel to `tpAccessible` (which merges each level into
      * one map, so a frame cannot simply be skipped). Hidden under [[atDeclScope]]. */
    private val tpExecNames = collection.mutable.ArrayDeque[Set[String]]()

    /** The instantiation this class gives its ANCESTORS' type parameters, by their names.
      *
      * `AssetLoader<T, P>` declares a RAW `Array<AssetDescriptor> getDependencies(…)`. Inside the
      * parent the name-directed fill matches `AssetDescriptor`'s own `T` to `AssetLoader`'s `T`, so
      * the inherited member reads `Array[AssetDescriptor[T]]` — and in
      * `BitmapFontLoader extends AsynchronousAssetLoader<BitmapFont, BitmapFontParameter>` that is
      * `Array[AssetDescriptor[BitmapFont]]`. The OVERRIDE re-renders the same raw type with no `T`
      * in scope, gets `AssetDescriptor[?]`, and scala rejects the pair. Java has no such problem:
      * both sides are raw and it checks neither.
      *
      * So a class must be able to see what it instantiated its parents' names AS. */
    private val inheritedInst = collection.mutable.ArrayDeque[Map[String, (TypeRepr, CtTypeReference[?])]]()
    /** …the same instantiation keyed by DECLARATION — see [[instantiationByDecl]]. Read only by
      * [[inheritedFormal]], at a call to a member an ANCESTOR declares. */
    private val inheritedByDecl = collection.mutable.ArrayDeque[Map[(String, String), CtTypeReference[?]]]()
    /** FQNs of the enclosing class and its ancestors — a raw type NESTED in any of them is filled
      * from the names in scope, because those names are the ones it was declared against.
      * `Entries` lives in `ObjectMap[K,V]`; inside `OrderedMap[K,V] extends ObjectMap[K,V]` it is
      * still `Entries[K,V]`, and the inherited field `entries1` is declared at exactly that type. */
    private val enclosingFqns = collection.mutable.ArrayDeque[Set[String]]()
    /** FQNs of this class's ancestors — the only declarations whose formals are written in type
      * variables the inherited instantiation can speak about. */
    private val ancestorFqns = collection.mutable.ArrayDeque[Set[String]]()
    private var noInheritFill = false
    /** true while translating a member this class INHERITS (an override). The inherited
      * instantiation exists to make such a member agree with the one it overrides — that is the
      * whole reason it was introduced. A member the class declares for ITSELF carries no such
      * obligation, and taking the entry there is exactly the `AssetLoadingTask` misfire: a private
      * `Array<AssetDescriptor> dependencies` field picking up `T -> Void` from
      * `implements AsyncTask<Void>`, because `AssetDescriptor`'s formal is also called `T`.
      *
      * Filtering the MAP cannot fix that: the `T -> Void` entry is genuinely needed, since
      * `AssetLoadingTask.call()` really does return `Void`. Four map-level guards measured 161,
      * 161, 142 and 141 for this reason. The obligation is a property of the SITE. */
    private var inOverridingMember = false
    /** The map is keyed by NAME, so an unrelated ancestor's `T` can collide with the `T` of the type
      * being filled — `Button`'s inherited `T = ButtonStyle` reaching `ButtonGroup<T extends
      * Button>`, which is not a `Button` at all. Require the candidate to satisfy the formal's own
      * BOUND; that is what makes the name match evidence rather than coincidence. */
    private def inheritedTp(f: CtTypeParameter): Option[TypeRepr] =
      if true || noInheritFill || !inOverridingMember then scala.None // sge design: no inherited fill
      else inheritedInst.headOption.flatMap(_.get(f.getSimpleName)).collect {
        case (r, ref) if boundAdmits(f, ref) => r
      }

    private def boundAdmits(f: CtTypeParameter, cand: CtTypeReference[?]): Boolean =
      Option(f.getSuperclass).filter(_.getQualifiedName != "java.lang.Object") match
        case None    => true
        case Some(b) => try cand.isSubtypeOf(b) catch { case _: Throwable => false }
    private def accessibleTp(name: String): Option[SymId] =
      if declScopeOnly && tpExecNames.headOption.exists(_.contains(name)) then None
      else tpAccessible.headOption.flatMap(_.get(name))
    /** A nested type captures its enclosing type's params iff it is a NON-static inner class. */
    private def capturesEnclosing(t: CtType[?]): Boolean =
      t.getDeclaringType != null && t.isInstanceOf[CtClass[?]] && !t.hasModifier(ModifierKind.STATIC)
    private var inStatic = false
    private def withStatic[A](s: Boolean)(f: => A): A =
      val prev = inStatic; inStatic = s
      try f finally inStatic = prev

    /** WHAT THIS WALK SAW — every executable it was asked to consider, INCLUDING the ones policy
      * removed. Published on the `Program` as [[MemberIndex]], which explains why the dropped half
      * cannot be recovered anywhere else: after `classDef` filters an executable out, it has no
      * symbol, no `DefDef` and no row in the symbol table, so a `dropMethods` key naming it would be
      * reported as a typo on every run that WORKED. */
    private val seenMembers = collection.mutable.ListBuffer.empty[(MemberKey, MemberFacts)]
    private val seenTypes   = collection.mutable.Set.empty[String]

    def build(types: List[CtType[?]]): Program =
      // the FILE header goes on every top-level type the file declares, and the type's own
      // comments come from `classDef` — see `fileHeader` for why the two are separate fields.
      //
      // Harvested BEFORE any type translates, which is not an ordering detail: the header is
      // decided by POSITION now (everything above the first line of code), and a positional claim
      // can only keep a finer harvest off a comment if it is made before that harvest runs.
      val headers = types.map(fileHeader)
      val units   = types.zip(headers).map((t, h) => classDef(t).copy(unitLeading = h))
      new Program(units, minter.table, Xref.build(units),
                  MemberIndex(seenMembers.toList, seenTypes.toSet))

    // ---- trivia (the original comments) -------------------------------------
    //
    // Ported from the BIR frontend, which got this right and is the only place it existed:
    // VERBATIM slices out of the source buffer, and a CLAIMED set so a coarse harvest point only
    // scoops what no closer one took. Both properties are load-bearing; see each below.

    /** Every comment handed out, by IDENTITY. `deepComments` is a net cast over a whole subtree, so
      * without this a comment inside a nested statement would be emitted twice — once above the
      * statement it belongs to and once above the statement that contains it. Identity, not
      * equality: two `// TODO` comments in one method are two comments. */
    private val claimed: java.util.Set[CtComment] =
      java.util.Collections.newSetFromMap(new java.util.IdentityHashMap[CtComment, java.lang.Boolean]())

    /** VERBATIM comment text, sliced from the original source (delimiters included).
      *
      * Never `CtComment.toString`, which RE-PRINTS from the parsed model: Spoon reflows the body,
      * normalises the ` * ` gutter and drops the exact indentation of a `<pre>` block or a
      * commented-out code sample. For a licence notice — the one comment a derived work must
      * reproduce — "close enough" is not a category that exists (CLAUDE.md §4.57). The re-printed
      * form is kept only as the fallback for a comment with no usable position, where there is
      * nothing to slice. */
    private def triviaOf(c: CtComment): Trivia =
      val kind = c.getCommentType match
        case CtComment.CommentType.JAVADOC => TriviaKind.Javadoc
        case CtComment.CommentType.INLINE  => TriviaKind.Line
        case _                             => TriviaKind.Block
      val pos = c.getPosition
      val src = sourceOf(c)
      val text =
        if pos != null && pos.isValidPosition && src.nonEmpty &&
           pos.getSourceEnd >= pos.getSourceStart && pos.getSourceEnd < src.length
        then src.substring(pos.getSourceStart, pos.getSourceEnd + 1)
        else c.toString
      Trivia(kind, text)

    /** The compilation unit's original text, for slicing. `""` when Spoon has no buffer for it —
      * which is the NORMAL case for an in-memory `VirtualFile` (`SpoonTir.fromSource`), where
      * `getOriginalSourceCode` returns null. Note the two `Option`s: `.map` over the unit alone
      * yields `Some(null)`, and the `null` then reaches `triviaOf` and NPEs — which, swallowed by
      * a broad `catch` one level up, made the whole harvest silently produce nothing. */
    private def sourceOf(el: CtElement): String =
      val pos = el.getPosition
      if pos == null || !pos.isValidPosition then inMemoryFor(null)
      else Option(pos.getCompilationUnit).flatMap(cu => Option(cu.getOriginalSourceCode)).getOrElse(inMemoryFor(pos))

    /** the in-memory buffer THIS position belongs to, by the unit's file name. Falls back to the
      * only source when there is exactly one (the single-snippet convenience path, where the name
      * is an implementation detail nobody passed), and to `""` when several are in play and the
      * position names none of them — which degrades a comment to Spoon's re-printed form rather
      * than slicing it out of the wrong file. */
    private def inMemoryFor(pos: spoon.reflect.cu.SourcePosition): String =
      Option(pos).filter(_.isValidPosition).flatMap(p => Option(p.getFile)).map(_.getName)
        .flatMap(inMemorySources.get)
        .orElse(Option.when(inMemorySources.sizeIs == 1)(inMemorySources.values.head))
        .getOrElse("")

    /** the comments Spoon attached DIRECTLY to `el` — its Javadoc and anything written above it.
      * Deliberately NOT wrapped in a `catch`: a harvest that throws is a defect to see, and a
      * blanket catch here is exactly what hid the null above. */
    private def leadingOf(el: CtElement): List[Trivia] =
      el.getComments.asScala.toList.filter(unheaded).map { c => claimed.add(c); triviaOf(c) }

    /** WHERE a comment is, as a pair a set can hold: the file it is in and the offset it starts
      * at. The identity `claimed` uses is the parser's OBJECT, which is exactly what the file
      * header can no longer rely on — a comment the parser attached nowhere has no object to
      * claim, so the header claims a SPAN and every finer harvest is held off by span too. */
    private def spanOf(c: CtComment): Option[(String, Int)] =
      val p = c.getPosition
      if p == null || !p.isValidPosition then scala.None
      else Some(unitKeyOf(p) -> p.getSourceStart)

    private def unitKeyOf(p: spoon.reflect.cu.SourcePosition): String =
      Option(p.getFile).map(_.getPath)
        .orElse(Option(p.getCompilationUnit).flatMap(cu => Option(cu.getFile)).map(_.getPath))
        // an in-memory `VirtualFile` may report no file at all, and "<unknown>" for every unit
        // would make two snippets share one header. The unit OBJECT is the identity then.
        .orElse(Option(p.getCompilationUnit).map(cu => "cu@" + System.identityHashCode(cu)))
        .getOrElse("<unknown>")

    /** spans the FILE HEADER has taken. Not `claimed`: see [[spanOf]]. */
    private val headerSpans = collection.mutable.Set.empty[(String, Int)]

    /** a comment the file header did NOT take — the filter every finer harvest applies, so a
      * leading block that Spoon ALSO attached to the type is not emitted twice. */
    private def unheaded(c: CtComment): Boolean = spanOf(c).forall(!headerSpans.contains(_))

    /** Comments Spoon attached to EXPRESSION-level descendants — an argument, a link in a fluent
      * chain, an initialiser. The TIR carries trivia on declarations and on statements only, so
      * these hoist to the nearest enclosing harvest point.
      *
      * MUST be called AFTER the element's children have been translated, so that nested statements
      * have already claimed theirs and this scoops only what nothing closer wanted. Called before,
      * it swallows the whole subtree's comments and prints them all above the outermost statement. */
    private def deepComments(el: CtElement): List[Trivia] =
      el.getElements(new spoon.reflect.visitor.filter.TypeFilter[CtComment](classOf[CtComment]))
        .asScala.toList.filter(unheaded).filter(claimed.add).map(triviaOf)

    /** The FILE's own header: everything above the first line of CODE, plus anything hanging off
      * the imports. In every library this engine has seen, that is the licence.
      *
      * ## Why this one harvest reads TEXT and not the parser
      *
      * A parser's attachment model is precisely the thing that cannot be trusted here, and it was
      * measured (`ENGINE-LIMITS.md` V3): where a file opens with TWO consecutive block comments,
      * `CtCompilationUnit.getComments` carries the FIRST and the second goes to the PACKAGE
      * DECLARATION — the one attachment site this walk never read (probed and pinned in the
      * testkit). In one generated-parser family the block that fell down that gap is the APACHE
      * NOTICE itself, behind three `//` generator lines the parser attached first, which makes
      * this a §4.57/§4.58 obligation rather than a tidiness item.
      *
      * Reading one more of the parser's slots is NOT the fix, and that is the point of doing it
      * positionally: the next shape lands in a slot nobody enumerated, and no set of slots can say
      * which of two blocks came FIRST — the order of a licence and the banner above it is text's
      * answer alone. So the rule needs no parser at all: a comment is the FILE's iff no code
      * precedes it (`CommentScanner.firstCodeOffset`). The parser-attached ones are still read —
      * they are how a comment with no usable position, and anything hanging off an import, still
      * arrives — and merged by offset, so a block both sides see is emitted once.
      *
      * This is also the ONE harvest that does not respect `claimed`. A Java file with two top-level
      * types becomes two Scala files, and each of them is a derived work that must carry the
      * notice; claimed-once would give it to the first and leave the second unattributed. The
      * answer is therefore CACHED per compilation unit rather than recomputed — recomputing would
      * be correct too, but the cache is what makes "each type gets the same header" a fact of the
      * code instead of a property of two harvests agreeing. */
    private val fileHeaders = collection.mutable.Map.empty[String, List[Trivia]]

    private def fileHeader(t: CtType[?]): List[Trivia] =
      val pos = t.getPosition
      if pos == null || !pos.isValidPosition || pos.getCompilationUnit == null then Nil
      else fileHeaders.getOrElseUpdate(unitKeyOf(pos), harvestHeader(t, pos))

    private def harvestHeader(t: CtType[?], pos: spoon.reflect.cu.SourcePosition): List[Trivia] =
      val cu   = pos.getCompilationUnit
      val key  = unitKeyOf(pos)
      val src  = sourceOf(t)
      // TEXT first: every comment above the first character a compiler would read.
      val positional =
        if src.isEmpty then Nil
        else
          val cut = balticporter.core.CommentScanner.firstCodeOffset(src)
          balticporter.core.CommentScanner.scanAt(src).filter(_.start < cut)
      val fromText = positional.map(a => a.start -> Trivia(kindOf(a.kind), a.text))
      // …then the parser's own, which still contribute: an import's comments sit BELOW the cut,
      // and a comment with no usable position has no offset to be found by.
      val attached = cu.getComments.asScala.toList ++ cu.getImports.asScala.toList.flatMap(_.getComments.asScala)
      attached.foreach(claimed.add)
      val taken    = fromText.map(_._1).toSet
      val fromTree = attached.flatMap { c =>
        val at = spanOf(c).map(_._2)
        if at.exists(taken.contains) then Nil else List(at.getOrElse(Int.MaxValue) -> triviaOf(c))
      }
      // the header OWNS these spans: `leadingOf` and `deepComments` skip them from here on, so a
      // leading block the parser also attached to the type cannot be emitted a second time.
      fromText.foreach((at, _) => headerSpans += (key -> at))
      (fromText ++ fromTree).sortBy(_._1).map(_._2)

    private def kindOf(k: balticporter.core.TriviaKind): TriviaKind = k match
      case balticporter.core.TriviaKind.Line    => TriviaKind.Line
      case balticporter.core.TriviaKind.Block   => TriviaKind.Block
      case balticporter.core.TriviaKind.Javadoc => TriviaKind.Javadoc

    // ---- provenance ----
    private def originOf(el: CtElement): Origin =
      val p = el.getPosition
      if p != null && p.isValidPosition then
        Origin(Option(p.getFile).map(_.getPath).getOrElse("<unknown>"), p.getLine, columnOf(p))
      else Origin.synthetic

    /** the position's COLUMN, or 0 where the unit has no source buffer to search.
      *
      * `isValidPosition` does not cover this and the failure is a CRASH, not a bad value: Spoon
      * computes a column by SCANNING the compilation unit's original source
      * (`SourcePositionImpl.searchColumnNumber`), and an in-memory unit may have none — the same
      * null `getOriginalSourceCode` `CLAUDE.md` §4.58 makes the trivia harvest carry its text for.
      * A position on such a unit is otherwise perfectly good, so the whole translation died on a
      * `NullPointerException` with no origin and no construct name, which is the one failure shape
      * this frontend must not have (§4.45).
      *
      * ZERO is honest here and is not a fabricated fact (§4.6): every reader of an `Origin` keys on
      * the FILE and the LINE — `srcmap.tsv`, `errors.tsv`, a finding's location, the correlator —
      * and the column is decoration none of them joins on. What is refused is inventing a column,
      * not reporting the origin. A file-backed unit caches its buffer on first read, so this costs a
      * cached field access per element and changes no port's output: every corpus source is a real
      * file and every one of them has a buffer. */
    private def columnOf(p: spoon.reflect.cu.SourcePosition): Int =
      val cu = p.getCompilationUnit
      if cu == null || cu.getOriginalSourceCode == null then 0 else p.getColumn

    private def tt(t: TypeRepr, el: CtElement): TypeTree = TypeTree(t, originOf(el))

    // ---- keys ----
    private def typeKey(t: CtTypeReference[?]): String = t.getQualifiedName
    private def memberKey(owner: SymId, sig: String): String = minterKeyOf(owner) + "#" + sig
    /** An external MEMBER always knows its owner — the key is derived from it. Passing it on is
      * what lets `owner#name` be reconstructed downstream (see `Minter.external`). */
    private def externalMember(owner: SymId, sig: String, name: String,
                               descriptor: Option[Descriptor] = None,
                               info: TypeRepr = NoType): SymId =
      minter.external(memberKey(owner, sig), name, owner, descriptor, info)
    private def minterKeyOf(id: SymId): String = "@" + id.raw // members hang off their owner's id
    private def erasedSig(m: CtExecutable[?]): String =
      val ps = m.getParameters.asScala.toList
        .map(p => scala.util.Try(p.getType.getQualifiedName).getOrElse("?"))
        .mkString(",")
      s"($ps)"

    /** The member's DESCRIPTOR — its source-level parameter spelling, read from the PARSER.
      *
      * This is the one place a descriptor is derived for a member the frontend declares, and it is
      * read HERE, from `CtParameter.getType`, rather than downstream from the `MethodType` this
      * method is about to build. That ordering is the whole of the `equals` divergence's fix:
      * `execDef` retypes a 1-argument `equals(Object)`'s parameter to `scala.Any` (Scala's
      * `Object.equals` takes `Any`), so a descriptor read from `info` says `Any` and every manifest
      * in existence says `Object`. Read before the retyping there is nothing to reconcile.
      *
      * The spelling is `getSimpleName` — grammar-identical to `isDropped`'s, which is what a
      * `dropMethods` key already matches against, so no existing key changes meaning. An ARRAY is
      * decomposed STRUCTURALLY rather than taken from `getSimpleName` (which happens to render
      * `int[]` as well): a Java vararg `T…` is a `CtArrayTypeReference` too, and going through
      * [[Param.Arr]] makes the two spell identically by construction rather than by coincidence.
      *
      * ALL parameters or none ([[Descriptor.total]]): a key with one parameter guessed matches the
      * wrong overload, which is worse than no key. */
    private def descriptorOf(m: CtExecutable[?]): Option[Descriptor] =
      def paramOf(r: CtTypeReference[?]): Param = r match
        case null                        => Param.Unresolved
        case a: CtArrayTypeReference[?]  => paramOf(a.getComponentType) match
          case Param.Unresolved => Param.Unresolved
          case of               => Param.Arr(of)
        case other                       =>
          scala.util.Try(other.getSimpleName).toOption.fold(Param.Unresolved)(Descriptor.paramOf)
      val ps = scala.util.Try(m.getParameters.asScala.toList).getOrElse(Nil)
      Descriptor.total(ps.map(p => scala.util.Try(p.getType).toOption.fold(Param.Unresolved)(paramOf)))

    /** Is this executable's declaration a SHADOW — reconstructed from a class file rather than
      * parsed from a source this run owns?
      *
      * The same test `coerceArgsFixed` and `varargPack` use — through [[isExternalCallee]], so there
      * is ONE spelling of "is this external": `getExecutableDeclaration` is non-null for a JDK
      * member under `noClasspath` too, so null-ness is not the external signal, and a second
      * spelling would be a second answer.
      *
      * The two absences are DIFFERENT answers and are written out rather than left to `forall`,
      * which is vacuously true on `None` and therefore hides which one was meant:
      *
      *   - NO DECLARING TYPE — an executable Spoon parented to nothing. Nothing in this program
      *     declares it, so it is external, and that is the same answer the null reference gets;
      *   - a THROW out of `getParent` — a model in a state this cannot read. Not the same claim:
      *     the conservative answer is "not external", which suppresses the erasure cast and the
      *     spread rather than inserting either on no evidence. */
    private def isShadowDecl(m: CtExecutable[?]): Boolean =
      try
        Option(m.getParent(classOf[CtType[?]])) match
          case scala.None    => true
          case Some(t)       => t.isShadow
      catch { case _: Throwable => false }

    /** …and the same question asked of a call's REFERENCE, which is where every caller starts.
      *
      * A reference with no declaration at all is external by the same rule: this program's own
      * members are parsed, so they have one. */
    private def isExternalCallee(ex: CtExecutableReference[?]): Boolean =
      Option(ex.getExecutableDeclaration) match
        case scala.None => true
        case Some(d)    => isShadowDecl(d)

    /** The `MethodType` of an EXTERNAL member — the fix `ENGINE-LIMITS.md` K15 names, and the fact
      * every consumer of that seam was blocked on.
      *
      * Until this existed, every external member the frontend interned carried `NoType` (measured
      * at 1157 on one library, `java.lang.Object#toString` included), so no phase could ask what a
      * method the program does not declare TAKES or RETURNS. That is the whole of K15's consumer
      * half: a retyped `mutable.Set` handed to a class file's `java.util.Set` formal is a break
      * nothing could see, because the position-blind retyping moved the call node's type on both
      * sides while the class file's own signature cannot move at all.
      *
      * Two properties, and neither is negotiable:
      *
      *  - **it is rendered SCOPE-FREE.** [[tpe]] resolves a type variable by NAME against the
      *    scopes the walk is currently inside, and fills a raw generic from the names accessible
      *    HERE. Both are right for a type written in the program and catastrophic for one read out
      *    of a class file: `java.util.List<E>.add(E)` would bind the callee's `E` to whatever `E`
      *    the CALLER happens to declare, and — because an external symbol is interned once and
      *    never clobbered — the first call site to reach it would decide the signature for the
      *    whole run. So a type variable, an intersection and a raw generic each render as *no
      *    answer*, never as a name this scope supplies.
      *  - **ALL of it or NONE of it**, exactly [[Descriptor.total]]'s rule and for a sharper
      *    reason. A partially-resolvable class file is one the parse was LENIENT about, and a
      *    signature read from it is not evidence about the slots that DID resolve: the measured
      *    case is a generated parser's constructor whose one parameter type is itself unresolvable,
      *    where an arity-correct-looking signature with one hole in it would be read as a fact.
      *    So one unrenderable slot — parameter or result — leaves the member signature-less, which
      *    is the state every external member was in before this method existed and which every
      *    consumer already handles.
      *
      * Only for a SHADOW declaration ([[isShadowDecl]]): a member the program declares gets its
      * real signature from `execDef`, and a second, weaker rendering of the same member is a second
      * truth about it. */
    private def externalSignature(m: CtExecutable[?]): TypeRepr =
      if !isShadowDecl(m) then NoType
      else
        val ps  = try m.getParameters.asScala.toList catch { case _: Throwable => Nil }
        val slots = ps.map(p => p.getSimpleName -> externalSlot(try p.getType catch { case _: Throwable => null }))
        // a constructor's result is `Unit`, which is what `execDef` renders for the members this
        // program DECLARES. One grammar: a reader that has a `MethodType` must not have to ask
        // where it came from before it can read the result slot.
        val ret = m match
          case _: CtConstructor[?] => unitT
          case _                   => externalSlot(try m.getType catch { case _: Throwable => null })
        if ret == NoType || slots.exists(_._2 == NoType) then NoType
        else MethodType(slots, ret)

    /** one SLOT of [[externalSignature]] — a parameter's or the result's declared type as a class
      * file states it, or `NoType` where this program has no scope-free name for it.
      *
      * A slot and a type ARGUMENT are not the same question, which is the distinction the two
      * methods here draw. A slot that cannot be rendered is unknown and says so; an ARGUMENT that
      * cannot be rendered is `?`, because Spoon reconstructs a shadow type by REFLECTION and a
      * class file's erasure genuinely does not say — `String.join`'s
      * `Iterable<? extends CharSequence>` arrives as `Iterable<T>`, echoing the interface's own
      * formal. `Iterable[?]` records exactly what was read: the head is exact, and the head is the
      * whole of the question a boundary asks. */
    private def externalSlot(tr: CtTypeReference[?]): TypeRepr = TypeShape.of(tr) match
      case TypeShape.Absent      => NoType
      case TypeShape.Prim(p)     => tpe(p)
      case TypeShape.Arr(_, c) =>
        externalSlot(c) match
          case NoType => NoType
          case e      => AppliedType(TypeRef(NoPrefix, minter.external("scala.Array", "Array")), List(e))
      // a type VARIABLE at the slot itself names something only the CALLEE's scope has, and an
      // intersection would be filled from names this scope supplies. Neither is a fact about the
      // class file, so neither is recorded.
      case TypeShape.Variable(_)       => NoType
      case TypeShape.Intersection(_, _) => NoType
      // A wildcard is scope-free — `?` is a fresh existential — and its BOUND would go through the
      // ARGUMENT rendering, since an unrenderable bound is `?` and not a refusal. That is what the
      // arm below the variable one said, and it never ran: `NoType` is what this slot has answered
      // for every `?` since it was written (see `TypeShape`'s doc, `ENGINE-LIMITS.md` G21).
      // PRESERVED SHADOW — a change here is its own measurement.
      case TypeShape.Wildcard(_, _, _) => NoType
      case s @ TypeShape.Named(r, _) =>
        val head  = TypeRef(NoPrefix, typeSym(r))
        val args  = s.args
        val arity = formalArity(r)
        // `tpe` would fill a bare generic from the names accessible at the READING point — the one
        // scope this rendering may not consult — so the fill here is the WILDCARD one, which is
        // what `tpe` itself produces where no name is in scope.
        if args.isEmpty then
          if arity <= 0 then head else AppliedType(head, List.fill(arity)(TypeBounds(NoType, NoType)))
        else AppliedType(head, args.map(externalArg))

    /** a type ARGUMENT of [[externalSlot]] — the same rendering, with "cannot be named here"
      * spelled `?` instead of refused. */
    private def externalArg(tr: CtTypeReference[?]): TypeRepr = externalSlot(tr) match
      case NoType => TypeBounds(NoType, NoType)
      case t      => t

    // ---- type parameter resolution ----
    /** parallel to `tpScopes`: is this frame an EXECUTABLE's own type parameters? */
    private val tpIsExec = collection.mutable.ArrayDeque[Boolean]()
    /** parallel to `tpScopes`: the SPOON declarations behind the ids, read by
      * [[resolveTypeParamDecl]]. Pushed and popped with `tpScopes` — the two are indexed together. */
    private val tpDecls = collection.mutable.ArrayDeque[Map[String, CtTypeParameter]]()

    /** Render a type as its DECLARATION site would have, not as the current scope would.
      *
      * The name-directed raw fill is scope-dependent BY DESIGN — the same raw `AssetLoader`
      * becomes `AssetLoader[T, P]` inside `setLoader<T, P>` and `AssetLoader[?, ?]` at a field of
      * a class with no such names. That is wanted: it is what preserves self-reference. What is
      * NOT wanted is re-rendering a DECLARED entity's type in the reading scope, because then the
      * engine's own two renderings of one Java type silently agree when the emitted Scala does
      * not, and the unchecked cast that should bridge them is never emitted.
      *
      * A FIELD's type cannot mention a method's type parameters — no scope in Java lets it — so
      * hiding the executable frames is exact here, not an approximation. */
    private def atDeclScope[A](f: => A): A =
      val saved = declScopeOnly
      declScopeOnly = true
      try f finally declScopeOnly = saved
    private var declScopeOnly = false

    /** WHICH frame a name resolves in — stated once, because [[resolveTypeParam]] and
      * [[resolveTypeParamDecl]] must answer about the SAME declaration or the raw fill's licence
      * (`licensedFills`) is read off one variable and applied to another. */
    private def tpFrameOf(name: String): Option[Int] =
      tpScopes.iterator.zipAll(tpIsExec.iterator, Map.empty[String, SymId], false).zipWithIndex.collectFirst {
        case ((m, isExec), i) if m.contains(name) && !(declScopeOnly && isExec) => i
      }

    private def resolveTypeParam(name: String): Option[SymId] =
      tpFrameOf(name).map(i => tpScopes(i)(name))

    /** the type an ERASED type-parameter name renders as, or `None` if the name is an ordinary one.
      *
      * Consulted ahead of [[resolveTypeParam]] at every variable occurrence, because an erased
      * parameter was never minted and has no id to resolve to — reaching `resolveTypeParam` it
      * would take the unresolved-marker arm and report `JS-G12` about a variable the frontend
      * itself decided not to declare. */
    private def erasedTypeParam(name: String): Option[TypeRepr] =
      tpErased.iterator.collectFirst { case m if m.contains(name) => m(name) }

    /** an executable's own type parameters that have NO WRITABLE INSTANTIATION ANYWHERE and carry no
      * information — java's UNCHECKED generic method, erased at the declaration to its own bound.
      *
      * ==The construct==
      * {{{
      * <B extends ISequenceBuilder<B, T>> B getBuilder();          // in IRichSequence<T>
      * public SequenceBuilder getBuilder();                        // @Override, in BasedSequence
      * }}}
      * Java permits the second to override the first: JLS 8.4.2 makes a signature a SUBSIGNATURE of
      * one whose ERASURE it is, so an implementor may drop the type parameter entirely and javac
      * issues an unchecked warning. Scala has no such rule — a method with no type parameters
      * cannot override one with them — so the emitted pair is `E038 has a different signature` at
      * the narrowing declaration and `needs to be abstract` at every concrete class below it, which
      * is `CLAUDE.md` §1(a)'s *java allows unchecked conversion at a raw type; scala does not*, read
      * at an override edge instead of at an assignment.
      *
      * ==Why the ERASURE is the honest image, and not a loss==
      * `ENGINE-LIMITS.md` G8 priced four ways of INSTANTIATING such a parameter and every one was
      * worse, for one reason it measured rather than assumed: **no denotable `X` satisfies
      * `X <: ISequenceBuilder<X, T>`**. So the parameter is not merely unsound, it is UNWRITABLE —
      * no caller can supply an argument for it and no implementation can produce one without a cast,
      * which is why java's own implementors here all write `return (B) …;` under a
      * `//noinspection unchecked`. A parameter nobody can instantiate and nobody can honour carries
      * exactly as much information as its bound, and its bound — with the self-reference wildcarded
      * — is an ordinary type both languages can write. That is the same type `ENGINE-LIMITS.md` G8.7
      * already ascribes at the USE and found sufficient there; this states it at the DECLARATION,
      * where it also repairs the override edges a use-site ascription cannot reach.
      *
      * ==Three conjuncts, and each one is a way the rule would be wrong without it==
      *   - **the variable occurs in NO PARAMETER type.** One that does is constrained by its
      *     argument and both languages infer it the same way — `<E extends Enum<E>> E[]
      *     getUniverse(Class<E> t)` is ordinary generic java and erasing it would throw away the
      *     caller's own answer. This is `pinUnconstrainedTypeArgs`' first condition verbatim;
      *   - **the bound MENTIONS THE VARIABLE ITSELF** — an F-bound. This is the load-bearing
      *     conjunct and the one G8 measured: an ordinary bound (`<T extends Node> T first()`) has
      *     denotable instantiations, callers DO write them, and erasing it to `Node` would lose a
      *     type the port's own code uses. `<T> List<T> emptyList()` is the same case at a vacuous
      *     bound and declines here twice over;
      *   - **the RESULT mentions the variable.** Otherwise the parameter is unused and erasing it
      *     changes no emitted type, so there is nothing to record and nothing to gain.
      *
      * Note what this deliberately does NOT do: it does not touch a variable the DECLARING TYPE
      * owns. `IRichSequence<T>`'s `T` is written at every use and instantiated by every implementor;
      * only the METHOD's own parameters are candidates. */
    private def unwritableResultVars(m: CtExecutable[?]): List[CtTypeParameter] = m match
      case ftd: CtFormalTypeDeclarer =>
        try
          val tps    = ftd.getFormalCtTypeParameters.asScala.toList
          val result = m match
            case _: CtConstructor[?]      => null
            case named: CtTypedElement[?] => named.getType
            case _                        => null
          if tps.isEmpty || result == null then Nil
          else
            val ps = m.getParameters.asScala.toList
            tps.filter { tp =>
              val n     = tp.getSimpleName
              val bound = Option(tp.getSuperclass).filter(_.getQualifiedName != "java.lang.Object")
              bound.exists(mentionsTypeVarBounded(_, Set(n))) &&
                !ps.exists(p => mentionsTypeVarBounded(p.getType, Set(n))) &&
                mentionsTypeVarBounded(result, Set(n))
            }
        catch { case _: Throwable => Nil }
      case _ => Nil

    /** the SPOON DECLARATION behind [[resolveTypeParam]]'s id — its bound is what a raw fill's
      * licence is read from, and no `Symbol` carries java's own spelling of it. */
    private def resolveTypeParamDecl(name: String): Option[CtTypeParameter] =
      tpFrameOf(name).flatMap(i => if i < tpDecls.size then tpDecls(i).get(name) else None)

    /** Mint ids for all formals FIRST (so bounds can self-reference — F-bounds), then
      * translate each bound with the frame in scope. Returns the frame and the TypeDefs. */
    private def mintTypeParams(declKey: String, owner: SymId, tps: List[CtTypeParameter]): (Map[String, SymId], List[Tree.TypeDef]) =
      val frame = tps.map(tp => tp.getSimpleName -> minter.resolve(declKey + "$$" + tp.getSimpleName)).toMap
      tpScopes.prepend(frame); tpIsExec.prepend(false) // a bound resolves in its own declarer's scope
      tpDecls.prepend(declFrame(tps))
      val defs = tps.map { tp =>
        val id     = frame(tp.getSimpleName)
        val bounds = boundsOf(tp)
        minter.set(id, Symbol(id, tp.getSimpleName, declKey + "$$" + tp.getSimpleName, Flags(isParam = true), owner, bounds))
        Tree.TypeDef(id, tt(bounds, tp), originOf(tp))
      }
      tpScopes.remove(0); tpIsExec.remove(0); tpDecls.remove(0)
      (frame, defs)

    /** the `tpDecls` frame for a declaration's formals — one derivation, so the three sites that
      * push a `tpScopes` frame cannot disagree about which declaration a name stands for. */
    private def declFrame(tps: List[CtTypeParameter]): Map[String, CtTypeParameter] =
      tps.map(tp => tp.getSimpleName -> tp).toMap

    /** Java's type parameters are ALWAYS reference types: `<T>` means `<T extends Object>`, since
      * Java has no primitive type arguments. Scala's `[T]` means `T <: Any`, which is STRICTLY
      * weaker — and the gap is not academic. A value read at such a `T` (through a raw receiver,
      * say `OrderedMapValues`'s raw `Array keys`, whose `keys.get(i)` Java types as `Object`)
      * then conforms to nothing that wants `Object`, because `Any` is not `Object`. Restoring the
      * implicit upper bound is a fact about Java, not about any library. */
    /** parent formal NAME -> the argument this class supplies, walking supertypes breadth-first so
      * a grandparent's names are covered too (`AsynchronousAssetLoader<T,P> extends AssetLoader<T,P>`). */
    private def ancestorsOf(t: CtType[?]): Set[String] =
      val acc = collection.mutable.Set[String](t.getQualifiedName)
      def walk(r: CtTypeReference[?], fuel: Int): Unit =
        if r != null && fuel > 0 && !acc.contains(r.getQualifiedName) then
          acc += r.getQualifiedName
          val d = try r.getTypeDeclaration catch { case _: Throwable => null }
          if d != null then
            val ups: List[CtTypeReference[?]] =
              (d match { case c: CtClass[?] => Option(c.getSuperclass).toList; case _ => Nil }) ++
                (try d.getSuperInterfaces.asScala.toList catch { case _: Throwable => Nil })
            ups.foreach(walk(_, fuel - 1))
      val ups0: List[CtTypeReference[?]] =
        (t match { case c: CtClass[?] => Option(c.getSuperclass).toList; case _ => Nil }) ++
          (try t.getSuperInterfaces.asScala.toList catch { case _: Throwable => Nil })
      ups0.foreach(walk(_, 5))
      // NOT the class itself: a helper it declares (`removeDuplicates`) is not written in an
      // ancestor's variables either, so its formals must render outside the override gate too.
      acc.toSet - t.getQualifiedName

    /** ONE walk over `t`'s ancestry, in breadth order, yielding every (DECLARING TYPE, formal name,
      * argument) triple the `extends`/`implements` clauses instantiate.
      *
      * Two maps are folded from it and the reason they are one walk is `ENGINE-LIMITS.md` F8's shape
      * (`CLAUDE.md` §4.56): a second copy of this traversal is a second answer to "what did this
      * class instantiate its parents' names as", and the two would drift. The FILTER is here rather
      * than in either consumer because it is about whether the argument can be NAMED at all — an
      * unresolvable variable renders as the `?I` stub and a wildcard has no name — which is a
      * precondition for every use, whether the answer fills a raw type or becomes a cast target. */
    private def parentInstantiations(t: CtType[?]): List[(String, String, CtTypeReference[?])] =
      val out = collection.mutable.ListBuffer[(String, String, CtTypeReference[?])]()
      def walk(r: CtTypeReference[?], fuel: Int): Unit =
        if r != null && fuel > 0 then
          val decl = try r.getTypeDeclaration catch { case _: Throwable => null }
          if decl != null then
            val fs = decl.getFormalCtTypeParameters.asScala.toList
            val as = r.getActualTypeArguments.asScala.toList
            if fs.sizeIs == as.size then
              fs.zip(as).foreach { (f, a) =>
                // skip an argument that names a type variable NOT in scope here: `tpe` renders
                // those as the unresolved stub `?I`, which is not a legal scala type and reached
                // the output as `new Array[?I](…)`.
                val nameable = a match
                  case tv: CtTypeParameterReference => resolveTypeParam(tv.getSimpleName).isDefined
                  case _                            => true
                if !a.isInstanceOf[CtWildcardReference] && nameable then
                  out += ((decl.getQualifiedName, f.getSimpleName, a))
              }
            val ups: List[CtTypeReference[?]] = decl match
              case c: CtClass[?] => Option(c.getSuperclass).toList
              case _             => Nil
            try (ups ++ decl.getSuperInterfaces.asScala.toList).foreach(walk(_, fuel - 1))
            catch { case _: Throwable => () }
      try
        val sup: List[CtTypeReference[?]] = t match
          case c: CtClass[?] => Option(c.getSuperclass).toList
          case _             => Nil
        (sup ++ t.getSuperInterfaces.asScala.toList).foreach(walk(_, 4))
      catch { case _: Throwable => () }
      out.toList

    private def instantiationOfParents(t: CtType[?]): Map[String, (TypeRepr, CtTypeReference[?])] =
      val out = collection.mutable.Map[String, (TypeRepr, CtTypeReference[?])]()
      parentInstantiations(t).foreach { (_, nm, a) =>
        if !out.contains(nm) then try out(nm) = (tpe(a), a) catch { case _: Throwable => () }
      }
      out.toMap

    /** …the same instantiations keyed by the DECLARATION rather than by the name — `(owner FQN,
      * formal name)`, which is exactly `ParentSubst`'s identity in the TIR and is what makes a
      * lookup evidence rather than a coincidence. [[instantiationOfParents]]'s name key is the one
      * `inheritedTp` measured at 161/142/141 and is switched off for it; nothing keyed this way can
      * collide, because two ancestors' `T`s are two different keys. */
    private def instantiationByDecl(t: CtType[?]): Map[(String, String), CtTypeReference[?]] =
      val out = collection.mutable.Map[(String, String), CtTypeReference[?]]()
      parentInstantiations(t).foreach { (owner, nm, a) => if !out.contains(owner -> nm) then out(owner -> nm) = a }
      out.toMap

    /** is `r` declared INSIDE a class currently on the enclosing-class stack? */
    private def selfAndAncestors(t: CtType[?]): Set[String] =
      val acc = collection.mutable.Set[String](t.getQualifiedName)
      def walk(r: CtTypeReference[?], fuel: Int): Unit =
        if r != null && fuel > 0 && !acc.contains(r.getQualifiedName) then
          acc += r.getQualifiedName
          val d = try r.getTypeDeclaration catch { case _: Throwable => null }
          if d != null then
            val ups: List[CtTypeReference[?]] =
              (d match { case c: CtClass[?] => Option(c.getSuperclass).toList; case _ => Nil }) ++
                (try d.getSuperInterfaces.asScala.toList catch { case _: Throwable => Nil })
            ups.foreach(walk(_, fuel - 1))
      try
        val ups0: List[CtTypeReference[?]] =
          (t match { case c: CtClass[?] => Option(c.getSuperclass).toList; case _ => Nil }) ++
            (try t.getSuperInterfaces.asScala.toList catch { case _: Throwable => Nil })
        ups0.foreach(walk(_, 5))
      catch { case _: Throwable => () }
      acc.toSet

    private def nestedInScope(r: CtTypeReference[?]): Boolean =
      try
        val decl = r.getTypeDeclaration
        if decl == null then false
        else
          val owners = enclosingFqns.headOption.getOrElse(Set.empty)
          var d = decl.getDeclaringType
          var hit = false
          var fuel = 5
          while d != null && !hit && fuel > 0 do
            if owners.contains(d.getQualifiedName) then hit = true
            d = d.getDeclaringType; fuel -= 1
          hit
      catch { case _: Throwable => false }

    private def boundsOf(tp: CtTypeParameter): TypeBounds =
      Option(tp.getSuperclass).filter(_.getQualifiedName != "java.lang.Object").map(fbound) match
        case Some(hi) => TypeBounds(NoType, hi)
        case None     => TypeBounds(NoType, objectT)

    /** Reconstruct a raw generic type's args from IN-SCOPE type parameters of the same NAME
      * (wildcards for the rest): `Node` under `Tree[N,V]` → `[N, V, ?]`, `Node` under
      * `Node[N,V,A]` → `[N, V, A]`, nested `Entries` under `ObjectMap[K,V]` → `[K, V]`, a
      * libgdx `Array` param → `[T]`. This preserves the self-reference / enclosing
      * instantiation that a plain wildcard fill erases — the erasure is what turns
      * `node.parent`, `this.entries1`, `array.items` into path-dependent captures that unify
      * with nothing. Returns None for a non-generic (arity-0) type.
      *
      * Every filled slot is LICENSED first (see [[licensedFills]]) — java stops checking at a raw
      * use and scala does not, so a name that matches by coincidence re-imposes a bound java never
      * checked (`ENGINE-LIMITS.md` G30). */
    private def nameFilledArgs(r: CtTypeReference[?], resolve: String => Option[SymId],
                               resolveDecl: String => Option[CtTypeParameter]): Option[List[TypeRepr]] =
      val formals = try Option(r.getTypeDeclaration).map(_.getFormalCtTypeParameters.asScala.toList).getOrElse(Nil)
                    catch { case _: Throwable => Nil }
      if formals.isEmpty then None
      else
        val ok = licensedFills(formals, resolveDecl)
        Some(formals.map { f =>
          val nm = f.getSimpleName
          if ok(nm) then resolve(nm).map(pid => TypeRef(NoPrefix, pid)).getOrElse(TypeBounds(NoType, NoType))
          else TypeBounds(NoType, NoType)
        })

    /** WHICH of a raw type's formals may take the in-scope variable of the same name — `CLAUDE.md`
      * §4.56 read at a BOUND, and `ENGINE-LIMITS.md` G30's discriminator.
      *
      * The fill is a SUBSTITUTION of the raw type's formals by the names in scope, and java licenses
      * NOTHING about it: JLS 4.8 stops checking at a raw use, so `B extends ReferenceNode` is legal
      * java however `ReferenceNode`'s own parameters are bounded. Scala checks the applied type, so a
      * fill has to carry its own evidence that each variable really can stand in the slot it is put
      * in. Three structural facts do, and no conformance lookup is asked of a `noClasspath` model —
      * one that answered `false` for a readable hierarchy would drop the fill libGDX's `Tree`/`Node`
      * family needs, which is the same regression by another route:
      *
      *  - the in-scope variable IS the formal — java's F-BOUND idiom, `N extends Node` written
      *    inside `Node`, where the substitution is the identity;
      *  - the formal is UNBOUNDED (`extends Object`), which every reference type discharges, and
      *    java has no primitive type arguments;
      *  - the two are declared with the SAME bound, spelled the same
      *    ([[boundSpelling]]) — java's own statement that they range over the same types. libGDX's
      *    `Tree<N extends Node, V>` passes here (`Node`'s slot 0 asks for `Node` and `Tree`'s `N` is
      *    declared `Node`); flexmark's `ReferencingNode<…, B extends ReferenceNode>` does not
      *    (`ReferenceNode`'s slot 1 asks for `Node`, and `B` is declared `ReferenceNode`).
      *
      * …and the third fact only holds if the FREE NAMES in that bound mean the same thing on both
      * sides, so a slot whose formal bound MENTIONS another formal is licensed only where that one
      * is too — a greatest fixpoint, not a per-slot test. The propagation is not tidiness: scalac
      * substitutes a declined slot as a PROJECTION rather than as a wildcard, so keeping `R` beside a
      * declined `B` reads `Type argument R does not conform to upper bound
      * NodeRepository[ReferenceNode[R, ?, ?]#B]` — measured, at scalac 3.8.4.
      *
      * An UNREADABLE bound licenses the fill, which is the third value rather than a `catch` with a
      * fabricated answer (§4.6): declining there is the `false`-for-a-readable-hierarchy regression
      * above, arriving through the failure path instead. */
    private def licensedFills(formals: List[CtTypeParameter],
                              resolveDecl: String => Option[CtTypeParameter]): Set[String] =
      val names = formals.map(_.getSimpleName).toSet
      def spelled(tp: CtTypeParameter): Option[Option[String]] =
        try Some(Option(tp.getSuperclass).filter(_.getQualifiedName != "java.lang.Object").map(boundSpelling))
        catch { case _: Throwable => None }   // unreadable — see the doc's third value
      var ok = formals.filter { f =>
        resolveDecl(f.getSimpleName) match
          case None    => false                     // nothing in scope: the slot is a wildcard anyway
          case Some(p) => (p eq f) || ((spelled(f), spelled(p)) match
            case (Some(None), _)              => true                 // the formal is unbounded
            case (Some(Some(a)), Some(Some(b))) => a == b             // java declared both the same
            case (None, _) | (_, None)        => true                 // unreadable
            case _                            => false)
      }.map(_.getSimpleName).toSet
      var changed = true
      while changed do
        val next = ok.filter { nm =>
          formals.find(_.getSimpleName == nm).forall { f =>
            val free = try Option(f.getSuperclass).map(mentionedTypeVarNames).getOrElse(Set.empty)
                       catch { case _: Throwable => Set.empty[String] }
            free.forall(v => !names(v) || ok(v))
          }
        }
        changed = next.size != ok.size
        ok = next
      ok

    /** A bound as JAVA WROTE IT, with type variables by their own name — the comparison
      * [[licensedFills]] makes. Not a rendering: it is only ever compared to another one of these. */
    private def boundSpelling(r: CtTypeReference[?]): String = TypeShape.of(r) match
      case TypeShape.Absent             => "?"
      case TypeShape.Wildcard(_, b, up) => b.map(x => (if up then "? extends " else "? super ") + boundSpelling(x)).getOrElse("?")
      case TypeShape.Variable(tv)       => "$" + tv.getSimpleName
      case TypeShape.Arr(_, c)          => boundSpelling(c) + "[]"
      case TypeShape.Intersection(_, bs) => bs.map(boundSpelling).mkString(" & ")
      case TypeShape.Prim(p)            => p.getQualifiedName
      case TypeShape.Named(n, as)       =>
        n.getQualifiedName + (if as.isEmpty then "" else as.map(boundSpelling).mkString("<", ",", ">"))

    /** the NAMED type variables a bound mentions — [[mentionsTypeVar]]'s question asked the other way
      * round, and with the wildcard arm ahead of the variable one (§4.56's dead-arm rule). */
    private def mentionedTypeVarNames(r: CtTypeReference[?]): Set[String] = TypeShape.of(r) match
      case TypeShape.Absent              => Set.empty
      case TypeShape.Wildcard(_, b, _)   => b.map(mentionedTypeVarNames).getOrElse(Set.empty)
      case TypeShape.Variable(tv)        => Set(tv.getSimpleName)
      case TypeShape.Arr(_, c)           => mentionedTypeVarNames(c)
      case TypeShape.Intersection(_, bs) => bs.flatMap(mentionedTypeVarNames).toSet
      case TypeShape.Prim(_)             => Set.empty
      case TypeShape.Named(_, as)        => as.flatMap(mentionedTypeVarNames).toSet

    private def objectT: TypeRepr = TypeRef(NoPrefix, minter.external("java.lang.Object", "Object"))

    /** The ERASURE of a type variable — its first bound with nested variables erased, else Object:
      * `T` → `Object`, `P extends AssetLoaderParameters<T>` → `AssetLoaderParameters[Object]`.
      * Used ONLY to build CASTS at wildcard-receiver call sites (see `erasedReceiverView`); it must
      * never drive a DECLARATION's type — declaring raw fields/params erased instead of wildcard
      * breaks assignment of concrete generic values (`Array[Object]` refuses `Array[String]`) and
      * was measured catastrophic (+277). */
    private def erasureOfFormal(f: CtTypeParameter, seen: Set[String], depth: Int): TypeRepr =
      Option(f.getSuperclass).filter(_.getQualifiedName != "java.lang.Object") match
        case None    => objectT
        case Some(b) => erasedType(b, seen + f.getSimpleName, depth)

    private def erasedType(b: CtTypeReference[?], seen: Set[String], depth: Int): TypeRepr =
      if depth <= 0 then objectT
      else TypeShape.of(b) match
        // a NESTED type variable erases through its own declaration, exactly as a bare one does (see
        // `erasedFormal`) — collapsing it straight to `Object` made the two sides of the same erased
        // call disagree: the RECEIVER cast said `Node[Node[Object,Object,Actor], Object, Actor]`
        // while the ARGUMENT cast for the very same `N` said `Tree[Object, Object]`. `seen` breaks
        // F-bounded cycles; the depth is pinned to the one both call sites use.
        // the F-BOUND cycle. Collapsing it to `Object` produces `Node[Object, Object, Actor]`,
        // which fails `N <: Node[N,V,A]` — and so does every finite unrolling, since `Node` is
        // invariant in `N`. Java carries the same bound and does not check it at an erased use;
        // Scala checks. A WILDCARD asserts only that SOME type satisfies the bound, which is
        // exactly the erased claim, and is the one form scalac accepts here.
        // …and the WILDCARD arm now stands where its own answer already was: a `?` has no
        // declaration, so the variable arm below used to reach `getOrElse(objectT)` for it and
        // answer exactly this. Answer-preserving by derivation, not by measurement.
        case TypeShape.Wildcard(_, _, _) => objectT
        case TypeShape.Variable(tv) =>
          if seen(tv.getSimpleName) then TypeBounds(NoType, NoType)
          else
            val d = try Option(tv.getDeclaration) catch { case _: Throwable => None }
            d.map(erasureOfFormal(_, seen + tv.getSimpleName, 2)).getOrElse(objectT)
        case TypeShape.Arr(_, c) =>
          AppliedType(TypeRef(NoPrefix, minter.external("scala.Array", "Array")),
                      List(erasedType(c, seen, depth)))
        case TypeShape.Intersection(_, bounds) =>
          bounds.headOption.map(erasedType(_, seen, depth)).getOrElse(objectT)
        case TypeShape.Prim(p)      => tpe(p)
        // no caller passes a null reference here (every one hands over a bound, a component, an
        // argument or a formal's superclass); `tpe` is the one place that answers for one, so defer
        // to it rather than inventing a second answer.
        case TypeShape.Absent       => tpe(b)
        case s @ TypeShape.Named(r, _) =>
          val head = TypeRef(NoPrefix, typeSym(r))
          s.args match
            case Nil =>
              val formals = try Option(r.getTypeDeclaration).map(_.getFormalCtTypeParameters.asScala.toList).getOrElse(Nil)
                            catch { case _: Throwable => Nil }
              if formals.isEmpty then head
              else AppliedType(head, formals.map { ff =>
                // `seen` breaks F-bounded cycles (`N extends Node<N,…>`); depth bounds the rest
                if seen(ff.getSimpleName) then TypeBounds(NoType, NoType)
                else erasureOfFormal(ff, seen, depth - 1)
              })
            case args => AppliedType(head, args.map(a => erasedType(a, seen, depth - 1)))

    /** the erasure a DECLARED formal is seen at through an erased receiver: a bare type variable
      * resolves through `subst` (the receiver's own erased arguments, by formal NAME) or, failing
      * that, through its declaration (so `P` → `AssetLoaderParameters[Object]`, not `Object`). A RAW
      * generic formal (`void save(AssetManager, ResourceData)` inside `ParticleBatch<T>`) is emitted
      * name-FILLED as `ResourceData[T]` — so it too must resolve through `subst`, or the cast we
      * build would not be the type the declaration actually asks for. */
    /** replace every occurrence of one rendered type by another. */
    private def substRepr(t: TypeRepr, from: TypeRepr, to: TypeRepr): TypeRepr =
      if t == from then to
      else t match
        case AppliedType(tc, as) => AppliedType(substRepr(tc, from, to), as.map(substRepr(_, from, to)))
        case other               => other

    private def erasedFormal(f: CtTypeReference[?], subst: Map[String, TypeRepr] = Map.empty,
                             named: Map[String, TypeRepr] = Map.empty): TypeRepr = f match
      case tv: CtTypeParameterReference =>
        subst.getOrElse(tv.getSimpleName, {
          val d = try Option(tv.getDeclaration) catch { case _: Throwable => None }
          d.map(erasureOfFormal(_, Set.empty, 2)).getOrElse(objectT)
        })
      case r if subst.nonEmpty && rawFormalsOf(r).exists(n => subst.contains(n)) =>
        AppliedType(TypeRef(NoPrefix, typeSym(r)), rawFormalNodes(r).map { ff =>
          subst.getOrElse(ff.getSimpleName, erasureOfFormal(ff, Set.empty, 1))
        })
      case other => erasedType(other, Set.empty, 2)

    /** formal-parameter NODES of a RAW use of a generic type (empty for anything else). */
    private def rawFormalNodes(r: CtTypeReference[?]): List[CtTypeParameter] =
      if r == null || r.isPrimitive || r.isInstanceOf[CtWildcardReference] ||
         r.isInstanceOf[CtArrayTypeReference[?]] ||
         (try r.getActualTypeArguments.size catch { case _: Throwable => 1 }) > 0 then Nil
      else try Option(r.getTypeDeclaration).map(_.getFormalCtTypeParameters.asScala.toList).getOrElse(Nil)
           catch { case _: Throwable => Nil }

    private def rawFormalsOf(r: CtTypeReference[?]): List[String] = rawFormalNodes(r).map(_.getSimpleName)

    /** [[mentionsTypeVar]], but aware that a RAW generic use is emitted name-FILLED from the
      * same-named in-scope parameters — so `ResourceData` inside `ParticleBatch<T>` really does
      * depend on `T`, even though nothing in the Spoon type says so. */
    private def mentionsTypeVarFilled(tr: CtTypeReference[?], names: Set[String]): Boolean = tr match
      case tv: CtTypeParameterReference => names(tv.getSimpleName) || boundMentions(tv, names)
      case arr: CtArrayTypeReference[?] => mentionsTypeVarFilled(arr.getComponentType, names)
      case _ =>
        val args = try tr.getActualTypeArguments.asScala.toList catch { case _: Throwable => Nil }
        if args.nonEmpty then args.exists(mentionsTypeVarFilled(_, names))
        else rawFormalsOf(tr).exists(names)

    /** A METHOD type variable declared in terms of the receiver's — `<T extends K> V get(T key)` on
      * `ObjectMap<K,V>` — depends on the receiver just as surely as a bare `K` formal does. Java
      * erases the bound along with everything else at a raw receiver, so a caller holding a real
      * `K` cannot reach that formal without the same erasure the receiver got.
      *
      * Bound only, never the variable's own name: a callee's `<T>` that happens to share a name
      * with one of the receiver's parameters is a different variable, and matching it would be the
      * name-based confusion [[tpConcrete]] exists to avoid. Depth-limited because a Java bound may
      * be F-bounded (`N extends Node<N,…>`) and would otherwise recurse forever. */
    private def boundMentions(tv: CtTypeParameterReference, names: Set[String], fuel: Int = 2): Boolean =
      fuel > 0 && (try
        Option(tv.getDeclaration).flatMap(d => Option(d.getSuperclass))
          .filter(_.getQualifiedName != "java.lang.Object")
          .exists {
            case b: CtTypeParameterReference => names(b.getSimpleName) || boundMentions(b, names, fuel - 1)
            case b                           => mentionsTypeVarFilled(b, names)
          }
      catch { case _: Throwable => false })

    /** does this type involve a RAW use of a generic type — itself, or anywhere in its arguments
      * (`Class`, `ObjectMap<String, AssetLoader>`)? A raw use is exactly where Java stops checking
      * and where our rendering is CONTEXT-dependent (wildcards, or name-directed fill), so the two
      * ends of an assignment need not agree even when Java's do. */
    private def mentionsRawGeneric(tr: CtTypeReference[?]): Boolean = TypeShape.of(tr) match
      case TypeShape.Absent      => false
      case TypeShape.Variable(_) => false
      // PRESERVED SHADOW (`ENGINE-LIMITS.md` G21): the arm written here descended into the BOUND,
      // and the variable arm above it answered `false` for every `?` instead. `false` is what this
      // predicate has said since it was written; changing it is its own measurement.
      case TypeShape.Wildcard(_, _, _)  => false
      case TypeShape.Arr(_, c)          => mentionsRawGeneric(c)
      case TypeShape.Prim(_)            => false
      case s =>
        if s.args.nonEmpty then s.args.exists(mentionsRawGeneric)
        else formalArity(s.ref) > 0

    /** the DECLARED type-parameter arity of a type reference — `Map` → 2, `String` → 0.
      *
      * ONE function, and the bare `catch` narrowed to the one lookup where an absent value is
      * NORMAL. It used to read
      *
      * {{{ try Option(r.getTypeDeclaration).map(_.getFormalCtTypeParameters.size).getOrElse(0)
      *     catch { case _: Throwable => 0 } }}}
      *
      * at FIVE sites, and the shape is the one `CLAUDE.md` §4.58 names about a harvest and the
      * auditor hunts for generally: a broad `catch` whose default quietly means *the rule does not
      * apply*. Here the default means arity ZERO, and arity zero is not "unknown" — it is the
      * statement that the type takes no arguments, which is what `tpe` then emits. So a resolution
      * failure inside a declaration Spoon HAS became a raw type rendered un-applied, silently, with
      * a green compile.
      *
      * The two halves are different facts and are now spelled differently:
      *
      *   - '''`getTypeDeclaration` absent''' — the type is not on the classpath. Normal, extremely
      *     common (every external non-generic class), and 0 is the only answer available. Wrapped;
      *   - '''a declaration that cannot state its own arity''' — an engine-visible defect in the
      *     model, and it now propagates instead of being absorbed. There is no honest default: this
      *     function's caller is about to decide how many type arguments to emit.
      *
      * Note what this does NOT claim to fix: a RAW use of a generic type whose declaration is
      * absent still answers 0, because nothing available can say otherwise. That case is the
      * classpath's, not the catch's. */
    private def formalArity(r: CtTypeReference[?]): Int =
      typeDeclarationOf(r).map(_.getFormalCtTypeParameters.size).getOrElse(0)

    /** the ONE Spoon lookup in the arity family where an absent value is normal — see
      * [[formalArity]] for why nothing else in that computation may share its `catch`. */
    private def typeDeclarationOf(r: CtTypeReference[?]): Option[spoon.reflect.declaration.CtType[?]] =
      if r == null then scala.None
      else try Option(r.getTypeDeclaration) catch { case _: Throwable => scala.None }

    /** JAVA'S FUNCTIONAL-INTERFACE QUESTION (JLS 9.8), asked of the class file — the ONE place it
      * is asked, and the ONLY place it can be.
      *
      * See [[balticporter.tir.Sam]] for why the answer is computed here and carried on the node
      * rather than derived by the phase that acts on it: the TIR interns an external type's members
      * only where the program REFERENCES them, so a phase deriving the answer from the symbol table
      * would be reading what this run happened to parse rather than what the class DECLARES
      * (`CLAUDE.md` §4.56).
      *
      * The rule is java's own, item by item, and each item is a refusal the census counts:
      *
      *   - the target must be an INTERFACE. Interfaces only, because that is java's rule, and
      *     because a scala SAM conversion to an abstract CLASS carries a constructor question no
      *     guard downstream answers;
      *   - abstract methods are counted INHERITED as well as declared (`getAllMethods`), which is
      *     why a name-based classification cannot do this job;
      *   - `static` and `default` methods do not count — a `default` method has a body;
      *   - a method override-equivalent to a PUBLIC method of `java.lang.Object` does not count.
      *     That exclusion is not a detail: it is the whole reason `java.util.Comparator`, which
      *     redeclares `equals(Object)` beside `compare`, is a functional interface at all.
      *
      * `java.io.Serializable` is reported BESIDE the answer rather than folded into it, because it
      * is not a statement about SAM-ness: such a type IS a functional interface and the port
      * declines the CONVERSION, since a serializable lambda's serialized form is not the anonymous
      * class's. Two different facts, two different fields.
      *
      * The ONE lookup wrapped is [[typeDeclarationOf]]'s, which is where an absent value is normal
      * (`CLAUDE.md` §4.6) — and its default here is [[Sam.Answer.Unreadable]] and never `No`, so a
      * classpath gap is counted as one instead of reading as "this port has no SAM sites". */
    private def samAnswerOf(r: CtTypeReference[?]): Sam.Answer =
      typeDeclarationOf(r) match
        case scala.None       => Sam.Answer.Unreadable
        case Some(_: CtInterface[?]) =>
          samAbstracts(r) match
            case one :: Nil =>
              Sam.Answer.Yes(one.getSimpleName, one.getParameters.size, serializableAncestry(r))
            case Nil        => Sam.Answer.No("no abstract method — nothing for a lambda to implement")
            case several    =>
              Sam.Answer.No(s"${several.size} abstract methods (${several.map(_.getSimpleName).sorted.distinct
                .mkString(", ")}) — java's own SAM rule (JLS 9.8) admits exactly one")
        case Some(_)          => Sam.Answer.No("the target is a CLASS, not an interface")

    /** JLS 9.8's COUNT, as a list — the one place the rule is spelled, so [[samAnswerOf]] and
      * [[samResultTpt]] cannot disagree about which method a functional interface has.
      *
      * '''Counted MODULO OVERRIDING, which the signature key alone does not do.''' JLS 9.8 counts
      * abstract methods *whose signatures are not override-equivalent*, and a re-declaration is the
      * ordinary way an interface documents the one it inherits:
      * `interface F<T> extends Function<Holder, T> { @Override T apply(Holder h); }`. Those two are
      * ONE method to java, and two to `getSignature`, because the inherited one's erased parameter
      * is the supertype's variable (`apply(T)`) and the declared one's is the argument
      * (`apply(Holder)`) — the shape a JVM BRIDGE exists for. Read as two, a functional interface
      * that java accepts a lambda for answers *not a SAM* to every question this frontend asks
      * about it.
      *
      * The collapse is deliberately structural and conservative: same simple name, same arity, and
      * the one being dropped is declared by a STRICT SUPERTYPE of the other's declarer. Two
      * abstract members inherited from UNRELATED supertypes stay two — java would call them
      * override-equivalent and this does not, which keeps the pre-existing answer for a shape no
      * corpus library has and which errs toward *not a SAM*. */
    private def samAbstracts(r: CtTypeReference[?]): List[CtMethod[?]] =
      typeDeclarationOf(r) match
        case Some(d: CtInterface[?]) =>
          val all = d.getAllMethods.asScala.toList.filter { m =>
            m.hasModifier(ModifierKind.ABSTRACT) &&
              !m.hasModifier(ModifierKind.STATIC) &&
              // a `default` method is not abstract, so the modifier test above already declines it;
              // this is the belt to that brace and is spelled through Spoon's own predicate rather
              // than through a modifier constant it does not have.
              !m.isDefaultMethod &&
              !SpoonTir.ObjectPublicSignatures.contains(m.getSignature)
          }.map(m => m.getSignature -> m).toMap.values.toList
          all.filterNot(m => all.exists(o => (o ne m) && redeclares(o, m)))
        case _ => Nil

    /** does `sub` RE-DECLARE `sup` — the same member seen twice, once at each declarer? */
    private def redeclares(sub: CtMethod[?], sup: CtMethod[?]): Boolean =
      sub.getSimpleName == sup.getSimpleName &&
        sub.getParameters.size == sup.getParameters.size &&
        (for
          a <- Option(sub.getDeclaringType).map(_.getReference)
          b <- Option(sup.getDeclaringType).map(_.getReference)
          if a.getQualifiedName != b.getQualifiedName
        yield a.isSubtypeOf(b)).getOrElse(false)

    /** THE SAM METHOD'S RESULT TYPE, for a lambda the SOURCE wrote — the second supplier of
      * [[Tree.Lambda.resultTpt]] and the one `ENGINE-LIMITS.md` I9 left open.
      *
      * A java lambda body is a METHOD body: `return` is legal in it and leaves the LAMBDA. Scala's
      * lambda is an expression, so the emitter interposes a nested `def` (`JS-S21`) — and a `def`
      * needs a result type, which is the SAM METHOD's and not the functional interface's. An
      * anonymous class hands the conversion its own `DefDef`; a lambda has no method ANYWHERE in
      * the program, because javac inferred the type from a class file. This is that class file,
      * read once, at the only place that holds it.
      *
      * ==Asked only where the lambda NEEDS it, which is not the emitter's condition leaking==
      * The field's meaning is *the type this node's nested `def` must declare*, and a lambda with
      * no value-returning `return` renders no `def` at all. Filling it everywhere would put a type
      * on ~every lambda in the corpus that the emitted text never names — and `Xref` registers it
      * as a USAGE, so a portability or dependency count would move for a type nothing writes.
      *
      * ==And a GENERIC result is ADAPTED at the TARGET, which is M6 narrowed one more turn==
      * `Supplier<String>.get` is declared `T get()`, and `T` is not a name the emitted code can
      * write. That was left REFUSED on the grounds that substituting the reference's actual
      * arguments for the declaration's formals is a different mechanism from reading a class file —
      * true when it was written, and a mechanism-absence argument rather than a semantic one. The
      * substitution is not a guess: `Function<PatternTypeFlags, Pattern>` says what `R` is, in the
      * FORMAL of the call the lambda is an argument to, and it is SPOON'S OWN rule that performs it
      * ([[TypeAdaptor]]), composed along the hierarchy so a `interface F extends Function<A,B>`
      * target adapts as exactly as a direct one does.
      *
      * What stays refused is what the adaptation cannot ANSWER: a result still mentioning a type
      * variable after it — a RAW target, an unreadable class file, a variable bound by the METHOD
      * rather than by the type. Those yield `None` and `OmissionCheck.unnameableLambdaReturn` counts
      * the site, because a guessed `T` or an erased `Object` is §4.6's fabricated fact: it compiles.
      *
      * The residue this closes was NOT loud. `ENGINE-LIMITS.md` I9 measured the same construct on
      * another library at 0 compile errors — a scala `return` inside a closure is a NON-LOCAL RETURN
      * from the enclosing method, legal and something else entirely — so the count is the only
      * instrument this has ever had, and it is the count that has to move. */
    private def samResultTpt(l: CtLambda[?]): Option[TypeTree] =
      if !returnsAValue(l) then scala.None
      else samAbstracts(l.getType) match
        case one :: Nil =>
          val rt = Option(one.getType).map(adaptedToTarget(l.getType, _))
          rt.filter(!mentionsTypeVariable(_, 8)).map(r => tt(tpe(r), l))
        case _ => scala.None

    /** the SAM method's declared result, read IN THE TARGET REFERENCE'S CONTEXT.
      *
      * Asked only where the declared type mentions a variable — there is nothing to adapt otherwise,
      * and an adaptation that rebuilt every SAM result would put a second spelling of the same type
      * on a node the emitter compares nothing about.
      *
      * The `catch` is §4.6-shaped rather than defensive: this frontend runs `noClasspath`, so the
      * hierarchy `TypeAdaptor` walks may simply not be there, and the default it falls back to is
      * the UNADAPTED type — which still mentions the variable, so the caller REFUSES and the site
      * keeps its counted `omissions` row. The default is therefore distinguishable from a real
      * answer by construction, which is the one property §4.6 asks of a fallback. */
    private def adaptedToTarget(target: CtTypeReference[?], t: CtTypeReference[?]): CtTypeReference[?] =
      if target == null || !mentionsTypeVariable(t, 8) then t
      else
        try Option(new TypeAdaptor(target).adaptType(t)).getOrElse(t)
        catch { case _: Throwable => t }

    /** does THIS lambda hold a `return` with a VALUE — stopping at a nested lambda or anonymous
      * method, whose `return`s are that construct's. The same question `TirEmitter.collectReturns`
      * and `OmissionCheck.valuedReturns` ask of the lowered tree, asked here of the java because
      * this runs before the body is translated. Spoon answers it directly: a `CtLambda` IS a
      * `CtExecutable`, and so is an anonymous class's `CtMethod`, so "the nearest enclosing
      * executable is me" is exactly the binding rule (JLS 15.27.2) and not an approximation of it. */
    private def returnsAValue(l: CtLambda[?]): Boolean =
      val body = l.getBody
      body != null &&
        body.getElements(new spoon.reflect.visitor.filter.TypeFilter(classOf[CtReturn[?]]))
          .asScala.exists(r => r.getReturnedExpression != null &&
            (r.getParent(classOf[spoon.reflect.declaration.CtExecutable[?]]) eq l))

    /** a type that mentions a TYPE VARIABLE anywhere — the declaration's own `T`, an array of one,
      * or one inside an argument. Fuel-bounded, because a bound can be recursive
      * (`T extends Comparable<T>`) and an unbounded walk on a class file is a hang. */
    private def mentionsTypeVariable(r: CtTypeReference[?], fuel: Int): Boolean =
      if r == null || fuel <= 0 then false
      else r.isInstanceOf[CtTypeParameterReference] ||
        (r match
          case a: CtArrayTypeReference[?] => mentionsTypeVariable(a.getComponentType, fuel - 1)
          case _                          => false) ||
        (try r.getActualTypeArguments.asScala.exists(mentionsTypeVariable(_, fuel - 1))
         catch { case _: Throwable => false })

    /** does this type's ancestry reach `java.io.Serializable`? Read through the same declaration
      * lookup, so an UNREADABLE ancestor answers `false` — and that half really cannot disagree in a
      * direction that converts, because the SAM answer for a type this run cannot read is
      * `Unreadable` and the site is refused under its own guard.
      *
      * ==What that argument does NOT cover, and why the fuel is gone==
      * It says nothing about a READABLE ancestry that is simply DEEP. Bounded at `fuel = 6` the walk
      * answered `false` seven links up while the SAM answer was `Yes`, so the site converted a
      * SERIALIZABLE target — the one thing guard 6 exists to decline — with no error, no moved count
      * and nothing to see. The comment that stood here read as if the bound were safe.
      *
      * The `seen` set is the real bound and always was: a qualified name is walked at most once and
      * the type graph is finite, so the recursion terminates without a counter. A fuel beside it is
      * not a belt to that brace, it is a SECOND bound that can only ever be wrong — a hierarchy is
      * as deep as somebody wrote it, and 6 was a number nothing derived. */
    private def serializableAncestry(r: CtTypeReference[?]): Boolean =
      val seen = collection.mutable.Set.empty[String]
      def walk(x: CtTypeReference[?]): Boolean =
        if x == null then false
        else if x.getQualifiedName == "java.io.Serializable" then true
        else if !seen.add(x.getQualifiedName) then false
        else typeDeclarationOf(x).exists { d =>
          val ups: List[CtTypeReference[?]] =
            (d match { case c: CtClass[?] => Option(c.getSuperclass).toList; case _ => Nil }) ++
              (try d.getSuperInterfaces.asScala.toList catch { case _: Throwable => Nil })
          ups.exists(walk)
        }
      walk(r)

    /** a use of a GENERIC class — an instantiation (`Class<T>`) or a raw one (`Class`). */
    private def isGenericUse(tr: CtTypeReference[?]): Boolean = TypeShape.of(tr) match
      case TypeShape.Absent            => false
      case TypeShape.Variable(_)       => false
      case TypeShape.Wildcard(_, _, _) => false   // the arm the variable one shadowed said the same
      case TypeShape.Arr(_, _)         => false
      case TypeShape.Prim(_)           => false
      case s                           => s.args.nonEmpty || formalArity(s.ref) > 0

    /** a RAW use of a generic class — `Cell`, not `Cell<T>`. Exactly where Java stops checking. */
    private def isRawGenericUse(tr: CtTypeReference[?]): Boolean =
      isGenericUse(tr) && (try tr.getActualTypeArguments.isEmpty catch { case _: Throwable => false })

    /** does every type variable this type mentions resolve HERE? `tpe` renders an unresolved one as
      * a `?T` stub, which is not valid Scala — so a synthesized cast must never target such a type. */
    private def tpResolvable(tr: CtTypeReference[?]): Boolean = TypeShape.of(tr) match
      case TypeShape.Absent       => true
      case TypeShape.Variable(tv) => resolveTypeParam(tv.getSimpleName).isDefined
      // PRESERVED SHADOW (`ENGINE-LIMITS.md` G21). The variable arm above used to claim every `?`
      // and answer `resolveTypeParam("?")`, which no scope can define — so `false` is what this has
      // said for every wildcard since it was written, and the bound-descending arm never ran.
      case TypeShape.Wildcard(_, _, _) => false
      case TypeShape.Arr(_, c)         => tpResolvable(c)
      case s                           => s.args.forall(tpResolvable)

    /** free of type VARIABLES entirely. A callee's formal must satisfy this before we may render it
      * at a CALL SITE: `resolveTypeParam` is name-based, so a callee's `<T>` would silently bind to
      * an unrelated in-scope `T` (`ResourceData<T>` vs `Json.readValue<T>`) and emit a wrong cast. */
    private def tpConcrete(tr: CtTypeReference[?]): Boolean = TypeShape.of(tr) match
      case TypeShape.Absent            => true
      case TypeShape.Variable(_)       => false
      case TypeShape.Wildcard(_, _, _) => false   // PRESERVED SHADOW — `ENGINE-LIMITS.md` G21
      case TypeShape.Arr(_, c)         => tpConcrete(c)
      case s                           => s.args.forall(tpConcrete)

    /** [[tpConcrete]] with its one over-exclusion repaired: a type VARIABLE passes when it is
      * LITERALLY the one in scope here ([[sameVarInScope]] — the same declaration, hence the same
      * minted symbol, never merely the same name).
      *
      * `tpConcrete`'s own doc says what it is protecting against and its neighbour says what it
      * excludes wrongly: a callee's `<T>` must not silently bind to an unrelated in-scope `T`, and
      * the CALLER'S OWN variable is not that case — `OrderedMultiMap<K, V>` naming `V` at a call
      * inside itself is exact, not a guess. Written as its own function rather than as a flag on
      * `tpConcrete`, because the two answer different questions and every existing caller of that
      * one must keep the answer it has (F8: one derivation per question, not one function per
      * shape). */
    private def tpNameableHere(tr: CtTypeReference[?]): Boolean = TypeShape.of(tr) match
      case TypeShape.Absent       => true
      case TypeShape.Variable(tv) => sameVarInScope(tv)
      // PRESERVED SHADOW (`ENGINE-LIMITS.md` G21) — and the one this entry is ABOUT: a `?` fell
      // into the variable arm, `sameVarInScope` found no declaration for it, and `Class<?>` was
      // therefore "not nameable here". `false` is the answer that has been given; the question of
      // whether a `?` is writable is a question about the POSITION it stands at and is answered
      // where that position is known.
      case TypeShape.Wildcard(_, _, _) => false
      case TypeShape.Arr(_, c)         => tpNameableHere(c)
      case s                           => s.args.forall(tpNameableHere)
    // …and note this is STRICTLY WEAKER than `tpConcrete`, not a different question: every arm but
    // the variable one is that function's, so a concrete type passes here too and no caller needs
    // the disjunction spelled out.

    /** Is this callee type VARIABLE literally the one in scope here — the same declaration, hence
      * the same minted symbol, not merely the same NAME? That is the case [[tpConcrete]]'s
      * name-based caution excludes wrongly: a SELF-CALL inside the declaring class
      * (`Node<N,V,A>.addToTree(Tree<N,V>, int)` invoked from another `Node` method) names the
      * caller's own variables, so rendering the formal here is exact, not a guess. Class formals
      * are minted at `<declaring FQN>$$<name>`, so equality of ids IS declaration identity. */
    private def sameVarInScope(tv: CtTypeParameterReference): Boolean =
      try
        Option(tv.getDeclaration).map(_.getParent) match
          case Some(ct: CtType[?]) =>
            resolveTypeParam(tv.getSimpleName)
              .contains(minter.resolve(ct.getQualifiedName + "$$" + tv.getSimpleName))
          case _ => false
      catch { case _: Throwable => false }

    /** Concrete, or mentioning only type variables OWNED BY THE CALLEE. Such a variable is never in
      * scope at the call site, so Java's view of the formal is its erasure — `TextureDescriptor<T
      * extends Texture>` is `TextureDescriptor<Texture>` there, and an unbounded `<T>` is
      * `Object` — which is exactly what an unchecked cast must name for Scala to then infer `T`.
      *
      * Unbounded variables were once excluded here, on the theory that erasing them to `Object`
      * defeats an inference that would have worked off the expected type. Measured false: it costs
      * `AssetManager.load(AssetDescriptor)` — passing a RAW `AssetLoaderParameters` field into
      * `load(String, Class<T>, AssetLoaderParameters<T>)`, where the sibling `Class` argument
      * already pins `T = Object` — and the one case it protected was a poly expression, now
      * excluded at source in [[uncheckedGeneric]]'s `bad` list where it belongs. sge writes the
      * same two casts by hand (`desc.type.asInstanceOf[Class[Any]]`, `desc.params.asInstanceOf[…
      * AssetLoaderParameters[Any]]`), which is the shape this produces. */
    private def calleeBounded(tr: CtTypeReference[?]): Boolean = TypeShape.of(tr) match
      case TypeShape.Absent => true
      // PRESERVED SHADOW (`ENGINE-LIMITS.md` G21): a `?` has no declaration, so both variable arms
      // below declined it and the answer was `false` — never the bound walk written under them.
      case TypeShape.Wildcard(_, _, _) => false
      case TypeShape.Variable(tv) if sameVarInScope(tv) => true
      case TypeShape.Variable(tv) =>
        (try Option(tv.getDeclaration) catch { case _: Throwable => None }).exists { d =>
          d.getParent.isInstanceOf[CtExecutable[?]]
        }
      case TypeShape.Arr(_, c) => calleeBounded(c)
      case s                   => s.args.forall(calleeBounded)

    /** `tpe`, but every type variable replaced by the erasure of its bound (see [[calleeBounded]]);
      * identical to `tpe` on a variable-free type. */
    private def tpBoundErased(tr: CtTypeReference[?]): TypeRepr = TypeShape.of(tr) match
      // PRESERVED SHADOW, and the FOURTEENTH site — one G21's census could not see, because it has
      // no wildcard ARM to be shadowed. It carried the exclusion on the APPLIED arm instead
      // (`!r.isInstanceOf[CtWildcardReference]`), which never ran: the variable arm above claimed
      // every `?` first, found no declaration and answered `objectT`. That is the answer preserved
      // here, and the dead guard is gone rather than left reading as live.
      case TypeShape.Wildcard(_, _, _) => objectT
      case TypeShape.Variable(tv) if sameVarInScope(tv) => tpe(tv)
      case TypeShape.Variable(tv) =>
        (try Option(tv.getDeclaration) catch { case _: Throwable => None })
          .map(erasureOfFormal(_, Set.empty, 2)).getOrElse(objectT)
      case TypeShape.Arr(_, c) =>
        AppliedType(TypeRef(NoPrefix, minter.external("scala.Array", "Array")),
                    List(tpBoundErased(c)))
      case TypeShape.Named(r, as) if as.nonEmpty =>
        AppliedType(TypeRef(NoPrefix, typeSym(r)), as.map(tpBoundErased))
      case s => tpe(s.ref)

    /** `tpe` of a callee's DECLARED formal with the receiver's type variables replaced by the
      * receiver's own (known) type ARGUMENTS. `None` whenever any part cannot be named here — a raw
      * generic, an intersection, or a variable outside `subst` that does not resolve in this scope —
      * so a cast is only ever built out of a type the emitted Scala can actually see. */
    private def substFormal(f: CtTypeReference[?], subst: Map[String, TypeRepr]): Option[TypeRepr] =
      def all(l: List[CtTypeReference[?]]): Option[List[TypeRepr]] =
        l.foldRight(Option(List.empty[TypeRepr]))((x, acc) => acc.flatMap(t => substFormal(x, subst).map(_ :: t)))
      f match
        case null => None
        case arr: CtArrayTypeReference[?] =>
          substFormal(arr.getComponentType, subst).map(c =>
            AppliedType(TypeRef(NoPrefix, minter.external("scala.Array", "Array")), List(c)))
        case w: CtWildcardReference =>
          Option(w.getBoundingType).filter(_.getQualifiedName != "java.lang.Object") match
            case None    => Some(TypeBounds(NoType, NoType))
            case Some(b) => substFormal(b, subst).map(u =>
                              if w.isUpper then TypeBounds(NoType, u) else TypeBounds(u, NoType))
        case tv: CtTypeParameterReference =>
          subst.get(tv.getSimpleName).orElse(resolveTypeParam(tv.getSimpleName).map(id => TypeRef(NoPrefix, id)))
        case p if p.isPrimitive                   => Some(tpe(p))
        case _: CtIntersectionTypeReference[?]    => None
        case r => r.getActualTypeArguments.asScala.toList match
          case Nil  => Option.when(formalArity(r) == 0)(TypeRef(NoPrefix, typeSym(r)))
          case as   => all(as).map(x => AppliedType(TypeRef(NoPrefix, typeSym(r)), x))

    /** [[mentionsTypeVarFilled]], but also descending into WILDCARD BOUNDS (`Array<? extends T>`
      * does depend on `T`). Kept separate: the existing predicate gates the erased-receiver path,
      * whose measured behaviour must not shift. */
    private def mentionsTypeVarBounded(tr: CtTypeReference[?], names: Set[String]): Boolean = tr match
      case w: CtWildcardReference => Option(w.getBoundingType).exists(mentionsTypeVarBounded(_, names))
      case tv: CtTypeParameterReference => names(tv.getSimpleName)
      case arr: CtArrayTypeReference[?] => mentionsTypeVarBounded(arr.getComponentType, names)
      case _ =>
        val args = try tr.getActualTypeArguments.asScala.toList catch { case _: Throwable => Nil }
        if args.nonEmpty then args.exists(mentionsTypeVarBounded(_, names))
        else rawFormalsOf(tr).exists(names)

    /** [[tpResolvable]], but through the BARRIER-aware frame: `resolveTypeParam` sees every enclosing
      * scope's parameters by name, while a `static` nested class cannot actually name the outer
      * class's — emitting one there is `Not found: type T`. */
    private def tpAccessibleHere(tr: CtTypeReference[?]): Boolean = TypeShape.of(tr) match
      case TypeShape.Absent            => true
      case TypeShape.Variable(tv)      => accessibleTp(tv.getSimpleName).isDefined
      case TypeShape.Wildcard(_, _, _) => false   // PRESERVED SHADOW — `accessibleTp("?")` is empty
      case TypeShape.Arr(_, c)         => tpAccessibleHere(c)
      case s                           => s.args.forall(tpAccessibleHere)

    /** the NAMES of every type variable a type mentions. */
    private def typeVarsOf(tr: CtTypeReference[?]): Set[String] = TypeShape.of(tr) match
      case TypeShape.Absent       => Set.empty
      case TypeShape.Variable(tv) => Set(tv.getSimpleName)
      // PRESERVED SHADOW (`ENGINE-LIMITS.md` G21) — and the one whose preserved answer is not a
      // constant: a wildcard reached the variable arm, whose `getSimpleName` for one is the literal
      // `"?"` (`CtWildcardReferenceImpl`'s constructor sets it), so that is the name this has been
      // reporting. Never the bound's variables, which is what the arm below it said.
      case TypeShape.Wildcard(w, _, _) => Set(w.getSimpleName)
      case TypeShape.Arr(_, c)         => typeVarsOf(c)
      case TypeShape.Prim(_)           => Set.empty
      case s                           => s.args.toSet.flatMap(typeVarsOf)

    /** does this type mention ANY type variable (directly, in an array element, or in an argument)? */
    private def mentionsAnyTypeVar(tr: CtTypeReference[?]): Boolean = TypeShape.of(tr) match
      case TypeShape.Absent      => false
      case TypeShape.Variable(_) => true
      // THE FIRST PRESERVED SHADOW FLIPPED (`ENGINE-LIMITS.md` G21). A `?` is NOT a type variable —
      // it is an existential the source wrote down, and `Class<?>` mentions none. The arm below the
      // variable one always said so and never ran; while it did not, no rule could ask *did the
      // source WRITE this argument out of types this port can name*, which is the question G21's
      // per-position erasure is made of. The BOUND is walked, because `List<? extends T>` really
      // does mention `T`.
      case TypeShape.Wildcard(_, b, _) => b.exists(mentionsAnyTypeVar)
      case TypeShape.Arr(_, c)         => mentionsAnyTypeVar(c)
      case s                           => s.args.exists(mentionsAnyTypeVar)

    /** Is `actual` the same type as `want` with some type ARGUMENTS collapsed — to `Object` (read
      * through an erased view) or to a wildcard (our raw fill)? That is precisely the shape of an
      * UNCHECKED CONVERSION: Java stops checking at a raw or erased use and lets the value flow
      * into any instantiation, so a mismatch of exactly this shape is one Java performed silently
      * and Scala needs written out.
      *
      * Deliberately narrow. It compares the RENDERED types, so it can only fire where the emitted
      * Scala really does disagree, and it demands the same type constructor and arity — an
      * unrelated mismatch, a subtype, or a differently-shaped type is not this and is left alone. */
    private def uncheckedFrom(actual: TypeRepr, want: TypeRepr): Boolean = (actual, want) match
      case (AppliedType(tc1, as1), AppliedType(tc2, as2)) if tc1 == tc2 && as1.sizeIs == as2.size =>
        as1.zip(as2).exists((a, w) => a != w) &&
          as1.zip(as2).forall((a, w) => a == w || a == objectT || a.isInstanceOf[TypeBounds] || uncheckedFrom(a, w))
      case _ => false

    /** does this rendered type carry a WILDCARD anywhere — i.e. is it the product of our raw fill? */
    private def hasWildcard(t: TypeRepr): Boolean = t match
      case _: TypeBounds       => true
      case AppliedType(tc, as) => hasWildcard(tc) || as.exists(hasWildcard)
      case _                   => false

    /** Is this type-parameter reference THE SAME parameter as the one its simple name resolves to
      * here? `accessibleTp`/`resolveTypeParam` are name-based, so a callee's `<T>` silently binds to
      * an unrelated in-scope `T`; comparing against the id its own declaring type minted
      * (`<owner qualified name>$$T`, see [[mintTypeParams]]) makes the identity exact. Method-level
      * parameters are never the same parameter — they exist only inside the callee. */
    private def sameTypeParamHere(tv: CtTypeParameterReference): Boolean =
      val owner = (try Option(tv.getDeclaration) catch { case _: Throwable => None })
        .flatMap(d => Option(d.getParent)).collect { case ct: CtType[?] => ct.getQualifiedName }
      owner.exists(o => accessibleTp(tv.getSimpleName).exists(id =>
        minter.fullNameOf(id) == o + "$$" + tv.getSimpleName))

    /** Can this DECLARED formal be named verbatim at the current call site? Concrete parts always;
      * a type variable only when it is literally the same parameter ([[sameTypeParamHere]]). */
    private def formalNameableHere(tr: CtTypeReference[?]): Boolean = TypeShape.of(tr) match
      case TypeShape.Absent             => false
      case TypeShape.Variable(tv)       => sameTypeParamHere(tv)
      case TypeShape.Arr(_, c)          => formalNameableHere(c)
      case TypeShape.Wildcard(_, _, _)  => false   // the arm the variable one shadowed said the same
      case TypeShape.Intersection(_, _) => false
      case TypeShape.Prim(_)            => true
      case s =>
        if s.args.nonEmpty then s.args.forall(formalNameableHere) else formalArity(s.ref) == 0

    /** does this rendered type name `scala.Array`? */
    private def isScalaArrayType(t: TypeRepr): Boolean = t match
      case AppliedType(TypeRef(_, s), _ :: Nil) => minter.fullNameOf(s) == "scala.Array"
      case _                                    => false

    /** does this type mention any of `names` as a type variable (directly or in its arguments)? */
    private def mentionsTypeVar(tr: CtTypeReference[?], names: Set[String]): Boolean = tr match
      case tv: CtTypeParameterReference => names(tv.getSimpleName)
      case arr: CtArrayTypeReference[?] => mentionsTypeVar(arr.getComponentType, names)
      case _ =>
        try tr.getActualTypeArguments.asScala.exists(mentionsTypeVar(_, names))
        catch { case _: Throwable => false }

    /** Translate a type-parameter bound. A RAW generic bound (`N extends Node`) is Java's idiom
      * for a self-referential (F-)bound; name-directed fill (see [[nameFilledArgs]]) rebuilds it
      * — the decl's own params are already in scope here (minted before bounds are translated) —
      * rather than erasing to `Node[?, ?, ?]`. Non-raw / array / intersection / type-var bounds
      * defer to `tpe`. */
    private def fbound(tr: CtTypeReference[?]): TypeRepr =
      val isRawGeneric = !tr.isInstanceOf[CtTypeParameterReference] &&
        !tr.isInstanceOf[CtArrayTypeReference[?]] && !tr.isInstanceOf[CtIntersectionTypeReference[?]] &&
        !tr.isInstanceOf[CtWildcardReference] && !tr.isPrimitive && tr.getActualTypeArguments.isEmpty
      // bounds are translated inside `mintTypeParams` with the decl's own frame freshly in scope
      // (no static-nested boundary crossed), so `resolveTypeParam` is the right, complete source.
      (if isRawGeneric then nameFilledArgs(tr, resolveTypeParam, resolveTypeParamDecl) else None) match
        case Some(args) => AppliedType(TypeRef(NoPrefix, typeSym(tr)), args)
        case None       => tpe(tr)

    // ---- types ----
    /** THE TYPE-REFERENCE DISPATCH — the frontend half of the catalog's FOURTH obligation surface,
      * and the wrapper sits HERE for [[stmtKind]]'s reason: written inside each `case`, an arm
      * could decline to wrap, and an arm that opts out is the same shape as the defect the
      * mechanism exists to catch.
      *
      * A `CtTypeReference` is neither a statement nor an expression, so neither of the other two
      * wrappers could ever enter one — which is why ten rows about wildcards, raw fills, F-bounds,
      * unbound type variables and nested-vs-inner types carried `Attaches.Unmechanised` naming this
      * exact gap. `SpoonKinds.refNameOf` maps the node's runtime class to its registry name ONCE,
      * before any arm is tried.
      *
      * A NULL reference is not a node and enters no scope: there is nothing there to owe a consult,
      * which is `SpoonKinds.Claim.Positional`'s rule read at its limit — a node that does not enter
      * the dispatch owes nothing. Its answer is `objectT`, exactly as the arm it replaces was. */
    private def tpe(tr: CtTypeReference[?]): TypeRepr =
      if tr == null then objectT
      else
        val at = originOf(tr)
        Typing.ofReference(SpoonKinds.refNameOf(tr.getClass), at, tr)(tpeArm(tr, at))

    /** JS-G07 and JS-G08 — the two questions a PLAIN class reference is asked, STATED ONCE and
      * called from both arms that a `CtTypeReference` reaches.
      *
      * `slotConsults`' shape at the type surface, and needed for the same reason: the primitive
      * fast path and the general arm are ONE Spoon kind, so a consult written in the general arm
      * alone is a hole at every `int`, `boolean` and `void` in the program — which is most of them.
      *
      * Both predicates are about a RAW USE (JLS 4.8), which is where java stops checking and scala
      * cannot: `isRawGenericUse` is the engine's own test for it, so the consult and the fill below
      * read one predicate rather than two spellings of one. JS-G08 narrows it to the sites where
      * the fill actually DEPENDS on the frame — a companion body cannot name the class's own
      * parameters and a nested use can, so one java type genuinely renders two ways. */
    private def rawUseConsults(r: CtTypeReference[?], at: Origin)(using Obligations): Unit =
      val raw = isRawGenericUse(r)
      Obligations.consult(JS.G(7), at)(Option.when(raw)(()))
      Obligations.consult(JS.G(8), at)(Option.when(raw && (inStatic || nestedInScope(r)))(()))

    private def tpeArm(tr: CtTypeReference[?], at: Origin)(using Obligations): TypeRepr = tr match
      case arr: CtArrayTypeReference[?] =>
        AppliedType(TypeRef(NoPrefix, minter.external("scala.Array", "Array")), List(tpe(arr.getComponentType)))
      case inter: CtIntersectionTypeReference[?] =>
        inter.getBounds.asScala.toList.map(tpe).reduce(AndType(_, _))
      // `? super java.lang.Object` HAS EXACTLY ONE INHABITANT, and it is not a wildcard.
      //
      // Java has no supertype of `Object`, so `List<? super Object>` can only ever be a
      // `List<Object>` — the lower bound says EVERYTHING, where an upper `? extends Object` says
      // nothing and is correctly dropped. Rendered `[?]` (both dropped by the same filter) the two
      // become the same type and a `list.addAll(other)` java accepts by capture conversion has an
      // element that unifies to `Nothing`: `Required: IterableOnce[Nothing & Any]`, measured at 4
      // sites on one library. This is not an approximation — it is the only java instantiation, so
      // naming it loses nothing. A `? super X` for any OTHER `X` really is a family and keeps its
      // bound.
      case w: CtWildcardReference =>
        val bound = Option(w.getBoundingType)
        val isObj = bound.exists(_.getQualifiedName == "java.lang.Object")
        val written = bound.filter(_.getQualifiedName != "java.lang.Object")
        // JS-G01 — java's use-site variance HAS a scala counterpart, in a different grammar. The
        // difference APPLIES where a bound has to be carried across: a bare `?` is the one form
        // both languages spell the same way, and `? extends Object` IS a bare `?`.
        Obligations.consult(JS.G(1), at)(written)
        // JS-G03 — and `? super Object` is the one wildcard that is not a family at all. Java has
        // no supertype of `Object`, so the lower bound admits exactly `Object`.
        Obligations.consult(JS.G(3), at)(Option.when(!w.isUpper && isObj)(()))
        if !w.isUpper && isObj then objectT
        else
          val b = written.map(tpe)
          if w.isUpper then TypeBounds(NoType, b.getOrElse(NoType)) else TypeBounds(b.getOrElse(NoType), NoType)
      // An ERASED parameter ([[unwritableResultVars]]) resolves to its own bound and not to a
      // binder, so it is answered AHEAD of the marker arm below: it was deliberately never minted,
      // and reporting `JS-G12` about it would say the frontend could not name a variable it chose
      // not to declare. JS-G49 is the row that IS about it.
      case tv: CtTypeParameterReference if erasedTypeParam(tv.getSimpleName).isDefined =>
        Obligations.consult(JS.G(49), at)(erasedTypeParam(tv.getSimpleName))
        // …and JS-G12 NOT-FIRED, because this arm inherits the node's obligations (`CLAUDE.md` §3).
        // `None` is a FACT here rather than a default: a name this frontend ERASED is one it chose
        // not to declare, which is the opposite of a variable that has no nameable type.
        Obligations.consult(JS.G(12), at)(scala.None)
        erasedTypeParam(tv.getSimpleName).get
      case tv: CtTypeParameterReference =>
        // …and JS-G49 NOT-FIRED for the same reason, from the other side: a name that resolves to a
        // binder here is not one `unwritableResultVars` erased.
        Obligations.consult(JS.G(49), at)(scala.None)
        val here = resolveTypeParam(tv.getSimpleName)
        // JS-G12 — this is where the frontend finds out that a type variable has NO NAMEABLE type:
        // the name resolves to no binder in this scope, so what is minted is a MARKER rather than a
        // name, and the emitter's standing obligation is that it never reaches the output.
        Obligations.consult(JS.G(12), at)(Option.when(here.isEmpty)(()))
        val id = here.getOrElse(minter.external(Symbol.UnresolvedTypeVarPrefix + tv.getSimpleName, tv.getSimpleName))
        TypeRef(NoPrefix, id)
      case p if p.isPrimitive =>
        // a primitive is a plain `CtTypeReference` and reaches the SAME obligation scope the arm
        // below does — see `rawUseConsults`.
        rawUseConsults(p, at)
        TypeRef(NoPrefix, minter.external("scala." + primName(p.getSimpleName), p.getSimpleName))
      case r =>
        rawUseConsults(r, at)
        val head = TypeRef(NoPrefix, typeSym(r))
        r.getActualTypeArguments.asScala.toList match
          case Nil =>
            // a RAW use of a generic type — Java allows it, Scala requires arguments. Fill the
            // declared arity with wildcards (`Class` → `Class[?]`), so the reference type-checks.
            // (Wildcards beat `Object` overall: a raw value more often flows INTO a generic slot
            // than needs a concrete arg. The residual raw-into-type-param sites are cast below.)
            val arity = formalArity(r)
            // a raw use of the class we're currently INSIDE, in a NON-static member (where the class's
            // own type params are in scope): fill with them (`ArrayMap[K,V]`) instead of wildcards, so
            // member accesses stay on the enclosing instantiation rather than a path-dependent capture.
            selfRawStack.headOption match
              // sge renders a raw SELF-use `[?]` too — `Cell.set(cell: Cell[?])` inside `Cell[T]`.
              case Some((cls, params)) if false && !inStatic && cls == typeSym(r) && params.nonEmpty && params.sizeIs == arity =>
                AppliedType(head, params.map(p => TypeRef(NoPrefix, p)))
              case _ =>
                // outside the enclosing class: reconstruct args from same-named in-scope params
                // (nested `Entries` → `Entries[K,V]`, param `Array` → `Array[T]`) so member
                // projections stay path-INdependent. Gated to non-static contexts — a companion
                // object can't see the class's type params. Falls back to wildcards.
                if arity <= 0 then head
                else if inStatic then AppliedType(head, List.fill(arity)(TypeBounds(NoType, NoType)))
                else
                  val formals = typeDeclarationOf(r).map(_.getFormalCtTypeParameters.asScala.toList).getOrElse(Nil)
                  if formals.isEmpty then AppliedType(head, List.fill(arity)(TypeBounds(NoType, NoType)))
                  else AppliedType(head, formals.map { f =>
                    // sge renders EVERY raw generic `[?]` — parent, overrides and fields alike
                    // (`AssetLoader.getDependencies: DynamicArray[AssetDescriptor[?]]`). Filling from
                    // an in-scope name is only right when the raw type is the enclosing class or
                    // NESTED in it (`Entries` inside `ObjectMap[K,V]`); for an unrelated generic the
                    // name match is coincidence and the result is semantically wrong.
                    (if nestedInScope(r) then accessibleTp(f.getSimpleName) else scala.None)
                      .map(id => TypeRef(NoPrefix, id))
                      .orElse(inheritedTp(f))                       // what THIS class instantiated it as
                      .getOrElse(TypeBounds(NoType, NoType))
                  })
          case args => AppliedType(head, args.map(tpe))

    /** id of a referenced class type — our own (already defined) or an external stub. */
    private def typeSym(r: CtTypeReference[?]): SymId = minter.external(typeKey(r), r.getSimpleName)

    private def primName(j: String): String = j match
      case "int"     => "Int";  case "long"    => "Long";  case "short"  => "Short"
      case "byte"    => "Byte"; case "char"    => "Char";  case "boolean" => "Boolean"
      case "float"   => "Float"; case "double" => "Double"; case "void"  => "Unit"
      case other     => other.capitalize

    // ---- declarations ----
    /** @param owner
      *   overrides the DECLARING TYPE as the symbol's owner. Set only for a METHOD-LOCAL class
      *   (JS-C30), whose owner is the enclosing executable — see the `stmtArm` arm for why the
      *   declaring type is the wrong answer there.
      * @param sourceName
      *   overrides the name the symbol carries. Set only for a local class, where Spoon's
      *   qualified name holds java's binary disambiguator (`Outer$1Local`) — the right INTERNING
      *   key and not a legal Scala identifier.
      * @param selfClass , outerVars
      *   the ANONYMOUS-class wiring, reused verbatim: a local class reaches the enclosing
      *   instance's members exactly as an anonymous one does, and captures the enclosing method's
      *   locals the same way. Empty for every ordinary declaration, which is the pre-existing path.
      */
    private def classDef(t: CtType[?], owner: Option[SymId] = scala.None,
                         sourceName: Option[String] = scala.None,
                         selfClass: SymId = SymId.None,
                         outerVars: Map[String, SymId] = Map.empty): Tree.ClassDef =
      val id   = defineType(t, owner, sourceName)
      // claimed FIRST, before any member translates: the type's Javadoc is attached to the type
      // element, and a member's `deepComments` must not be able to reach it.
      val lead = leadingOf(t)
      val (frame, tpDefs) = mintTypeParams(typeKey(t.getReference), id, t.getFormalCtTypeParameters.asScala.toList)
      tpScopes.prepend(frame); tpIsExec.prepend(false)
      tpDecls.prepend(declFrame(t.getFormalCtTypeParameters.asScala.toList))
      selfRawStack.prepend(id -> t.getFormalCtTypeParameters.asScala.toList.map(tp => frame(tp.getSimpleName)))
      inheritedInst.prepend(instantiationOfParents(t))
      inheritedByDecl.prepend(instantiationByDecl(t))
      enclosingFqns.prepend(enclosingFqns.headOption.getOrElse(Set.empty) ++ selfAndAncestors(t))
      ancestorFqns.prepend(ancestorsOf(t))
      val enclosingAcc = if capturesEnclosing(t) then tpAccessible.headOption.getOrElse(Map.empty) else Map.empty
      tpAccessible.prepend(enclosingAcc ++ frame)
      // an anonymous/local class capturing an enclosing method's params keeps them EXEC-contributed
      tpExecNames.prepend(if capturesEnclosing(t) then tpExecNames.headOption.getOrElse(Set.empty) else Set.empty)
      val savedStatic = inStatic; inStatic = false // a class body isn't a static context for its instance members
      val parents = superTypes(t)
      // …carried WITH their source positions, because a field and an initialiser BLOCK are one
      // sequence in JLS 12.5 step 4 and the body list has to interleave them. See `step4` below.
      // a LOCAL class takes the anonymous-class body wiring — see the parameter docs. `SymId.None`
      // and `""` for every other declaration, which is the path every existing caller takes.
      val local  = owner.isDefined
      val bodySelf = if local then id else SymId.None
      // NO `catch` here, and its absence is the point (§4.6): `""` is precisely the value the
      // NON-local path uses, so a swallowed failure would be indistinguishable from "this is not a
      // local class" — a fabricated fact, at a value the anonymous-class body wiring then reads.
      // And the default would be unreachable anyway: `isDropped` and `seenTypes` below call
      // `t.getQualifiedName` unguarded a few lines later, so a `CtType` that cannot spell its own
      // name takes the whole translation down there rather than degrading here.
      val bodyQName = if local then t.getQualifiedName else ""
      val fields = t.getFields.asScala.toList
        .filterNot(_.isInstanceOf[CtEnumValue[?]])
        .sortBy(posKey)
        .map(f => posKey(f) -> fieldDef(id, f, selfClass, outerVars, bodySelf, bodyQName))
      // include enum constructors too — the emitter folds their PARAMS into the sealed class's primary
      // constructor so each constant (`Nearest(GL_NEAREST)`) has a matching parameter to pass to.
      // Substitutions.dropMethods: a member opted out of mechanical translation (a ready Scala
      // equivalent is injected in its place, or every use of it was rewritten away). Keyed
      // `owner#name` for all overloads, or `owner#name(P1,P2)` on erased parameter simple names
      // for one. Constructors are keyed `<init>` and need that precision to be droppable at all.
      def isDropped(e: CtExecutable[?], name: String): Boolean =
        subs.dropsMethod(t.getQualifiedName, name,
          e.getParameters.asScala.toList.map(p => Option(p.getType).map(_.getSimpleName).getOrElse("?")))
      seenTypes += t.getQualifiedName
      /** one row of [[seenMembers]] — WHAT THIS WALK SAW at this member's identity. */
      def note(e: CtExecutable[?], nm: String, sym: Option[SymId], dropped: Boolean): Unit =
        seenMembers += MemberKey(t.getQualifiedName, nm, descriptorOf(e)) ->
          MemberFacts(sym, execFlags(e), originOf(e), dropped)
      /** Record what this walk SAW, then translate what survives — the two halves of the same pass,
        * because this is the last place the dropped half exists at all ([[seenMembers]]).
        *
        * The interleave is deliberate and load-bearing: `isDropped` and `descriptorOf` mint nothing,
        * so the order symbols are MINTED in is exactly what it was before this record existed
        * (`filterNot` then `map`), and a run's `SymId` assignment — which every deterministic
        * artifact depends on — cannot move. */
      def walked[E <: CtExecutable[?]](es: List[E], nameOf: E => String)(mk: E => Tree.DefDef): List[Tree.DefDef] =
        es.flatMap { e =>
          val nm = nameOf(e)
          if isDropped(e, nm) then { note(e, nm, scala.None, dropped = true); Nil }
          else
            val d = mk(e)
            note(e, nm, Some(d.symbol), dropped = false)
            List(d)
        }
      // JS-C43 — A NESTED RECORD ARRIVES WITH NO CONSTRUCTOR AT ALL. Spoon synthesises a record's
      // implicit canonical constructor for a TOP-LEVEL declaration and not for a nested one
      // (`getConstructors.size` is 1 and 0, probed on the same file), so the emitted class had no
      // way to be constructed and no statement assigning a single component. `createCanonical-
      // ConstructorIfMissing` is Spoon's own repair for exactly this and is idempotent, so it is a
      // no-op on the top-level shape and on a record that wrote its constructor out.
      t match
        case r: CtRecord => r.createCanonicalConstructorIfMissing()
        case _           => ()
      val ctors = t match
        case c: CtClass[?] => walked(c.getConstructors.asScala.toList.sortBy(posKey), _ => "<init>")(
                                execDef(id, _, "<init>", selfClass, outerVars, false, bodySelf, bodyQName))
        case _             => Nil
      // ordinary methods went through with the DEFAULT `overrides = false`; only anonymous-class
      // methods ever consulted the hierarchy. Scala requires `override` where java requires
      // nothing, and RefChecks — the phase that says so — had never run to report it.
      val methods = walked(t.getMethods.asScala.toList.sortBy(posKey), _.getSimpleName)(m =>
        execDef(id, m, m.getSimpleName, selfClass, outerVars, overridesInherited(m), bodySelf, bodyQName))
      // Java INITIALIZER BLOCKS — `static { … }` and instance `{ … }`. These were previously
      // dropped on the floor: nothing referenced `CtAnonymousExecutable`, so `MathUtils` never
      // built its sin/cos table, `CRC` never built its table and `Colors` never registered a
      // colour — a port that compiles clean and computes `sin(x) = 0`. Silent omission is exactly
      // what this engine forbids (DESIGN.md §3.4), so they are translated like any other executable
      // and carried as synthetic members; the emitter inlines their body at the right place (a
      // static block into the companion object, an instance block into the class body), where
      // Scala runs it at initialisation — the same point Java does.
      val initBlocks = t match
        case c: CtClass[?] =>
          c.getAnonymousExecutables.asScala.toList.sortBy(posKey).map { ae =>
            val nm = if ae.hasModifier(ModifierKind.STATIC) then "<clinit>" else "<initblock>"
            val d  = execDef(id, ae, nm, selfClass, outerVars, false, bodySelf, bodyQName)
            // AN INITIALISER BLOCK IS AN INDEX ENTRY. It is an executable the frontend read out of
            // Java, and a port really does key policy on one — gdx-vfx replaces the BODY of
            // `VfxGLUtils#<clinit>`, whose Java branches on a reflective class the base drops.
            // Left out, the binder found the symbol in the program, found the owner in `seenTypes`,
            // and concluded from the structure that the ENGINE had minted it: a `SyntheticTarget`
            // refusal, of a hand-written `static { }` block, which is the opposite of true. The
            // `policy-binding` check is what caught it (vfx, `policy 0 -> 1`) — the whole reason
            // that check exists while both answers are still computable.
            //
            // NOT routed through `walked`: `dropMethods` is not consulted for an initialiser, and
            // sending it through the drop test would silently make init blocks droppable — a
            // widening this commit has no business making.
            note(ae, nm, Some(d.symbol), dropped = false)
            posKey(ae) -> d
          }
        case _ => Nil
      val nested  = t.getNestedTypes.asScala.toList.sortBy(posKey).map(n => classDef(n))
      val enumCases = t match
        case e: CtEnum[?] => e.getEnumValues.asScala.toList.map(enumCase(id, _))
        case _            => Nil
      tpScopes.remove(0); tpIsExec.remove(0); tpDecls.remove(0); inheritedInst.remove(0); inheritedByDecl.remove(0)
      enclosingFqns.remove(0); ancestorFqns.remove(0)
      selfRawStack.remove(0); tpAccessible.remove(0); tpExecNames.remove(0); inStatic = savedStatic
      // JLS 12.5 STEP 4 IS ONE SEQUENCE, IN TEXTUAL ORDER — field initialisers and instance
      // initialiser blocks together (12.4.2 step 9 says the same of the static pair). Grouped
      // `fields ++ … ++ initBlocks`, every block landed behind every field, so
      // `{ b = 2; } int b = 5;` emitted the assignment java ran FIRST last and left `b == 2` where
      // java leaves 5. Valid Scala, no compile error, no check count, and only a run can see it —
      // C12's shape at the other member of the same step.
      //
      // Sorted AFTER every symbol is minted, never before: the minting order stays
      // fields → ctors → methods → initBlocks, because a run's `SymId` assignment is what every
      // deterministic artifact is keyed on (see `walked` above for the same reasoning). `sortBy` is
      // stable, so members with no valid position keep the grouping they had.
      val step4 = (fields ++ initBlocks).sortBy(_._1).map(_._2)
      // JS-C43 — the two things a java RECORD needs that its members do not carry. Both are read
      // HERE, where the components' field and accessor symbols have just been minted and the
      // Spoon node is still in hand; neither is derivable downstream (see `recordComponents`).
      //
      // `isRecord` is CLEARED where the join failed, and that is the flag's whole contract: it does
      // not say "java wrote `record`", it says "java wrote `record` AND this program still declares
      // every member the synthesis reads". A flag that meant only the first would license a
      // synthesis over a subset of the components.
      val comps = recordComponents(t, id)
      if t.isInstanceOf[CtRecord] then
        minter.defined(typeKey(t.getReference)).foreach((sid, sy) =>
          minter.set(sid, sy.copy(components = comps.getOrElse(Nil),
                                  flags = sy.flags.copy(isRecord = comps.isDefined))))
        // NOTHING IS DONE TO THE COMPONENT FIELD'S ACCESS, and that was PROBED rather than assumed.
        // JLS 8.10.1 makes it `private final` and Spoon reports exactly that, so there is no flag to
        // repair; what the emitted `var x$field` then shows is `TirEmitter.recordClashWidening` —
        // every renamed field is widened to public, with its own recorded decision, because a name
        // java read from an enclosing class is not reachable at the new one. A record's field is
        // renamed on EVERY record (the component, the field and the accessor share one java name),
        // so that rule always applies here; it is a universal §4.55 rule with its own note and it is
        // not this row's to narrow.
      Tree.ClassDef(id, parents, selfType = None,
        body = step4 ++ canonicalised(t, id, comps.getOrElse(Nil), ctors) ++
               accessorBodies(t, id, comps.getOrElse(Nil), methods) ++ nested,
        origin = originOf(t), tparams = tpDefs, enumCases = enumCases, leading = lead)

    /** JS-C43 — an IMPLICIT record accessor RETURNS ITS COMPONENT'S FIELD (JLS 8.10.3), written out.
      *
      * The third thing the parser hands over wrong, and the worst of the three. Spoon gives a nested
      * record's implicit accessor a body whose field read does not resolve, so `def bo()` emitted
      * `return bo` — which in scala's ONE namespace is the METHOD, and the accessor calls itself
      * forever. It type-checks; the compile is green; the port stack-overflows the first time
      * anything reads a component.
      *
      * Written out for EVERY implicit accessor rather than only where the resolution failed, so that
      * the emitted record does not depend on whether it was declared at the top level (where Spoon
      * resolves the same body correctly). What java derives, this derives, and it derives it once.
      *
      * An accessor the record WROTE is untouched — `isImplicit` is the whole test — because that one
      * is real java whose body may be anything, and JLS 8.10.3 says java calls it. */
    private def accessorBodies(t: CtType[?], id: SymId, comps: List[RecordComponent],
                               methods: List[Tree.DefDef]): List[Tree.DefDef] =
      if comps.isEmpty then methods
      else
        val derived: Map[SymId, RecordComponent] = comps.flatMap { c =>
          t.getMethods.asScala
            .find(m => m.isImplicit && m.getSimpleName == c.name && m.getParameters.isEmpty)
            .flatMap(m => minter.defined(memberKey(id, c.name + erasedSig(m))).map(_._1 -> c))
        }.toMap
        methods.map { d =>
          derived.get(d.symbol) match
            case scala.None    => d
            case Some(c) =>
              val at = d.origin
              val ft = minter.defined(memberKey(id, c.name)).map(_._2.info).getOrElse(NoType)
              val read = Tree.Select(Tree.This(id, TypeRef(NoPrefix, id), at), c.field, ft, at)
              // `scala.Nothing` on the `Return` and `Unit` on the block — the shapes `stmts` gives a
              // real `return`, so a derived accessor and a written one are the same tree.
              val nothing = TypeRef(NoPrefix, minter.external("scala.Nothing", "Nothing"))
              d.copy(rhs = Some(Tree.Block(List(Tree.Return(Some(read), nothing, at)),
                                           Tree.Literal(Constant.UnitC, unitT, at), unitT, at)))
        }

    /** java's RECORD COMPONENTS, in DECLARATION ORDER, each joined to the FIELD and the ACCESSOR
      * javac derived from it — see [[balticporter.tir.RecordComponent]] for why both.
      *
      * Read from the SPOON node and interned through the same keys `fieldDef1` and `execDef` used,
      * which is what makes the join structural. A name-keyed join at emission cannot work: java
      * gives the component, the field and the accessor ONE name and scala has one namespace, so the
      * emitter has already renamed the field by the time anything is written.
      *
      * SORTED BY POSITION rather than taken in `getRecordComponents`' iteration order. Spoon's
      * return type is a `Set` — its implementation happens to be insertion-ordered, which is a fact
      * about a class this engine does not own — and the order is the whole semantics of `toString`,
      * `equals`, `hashCode` and every deconstruction.
      *
      * ALL OR NOTHING, which is what the `Option` is for. A component whose field or accessor this
      * program does not DECLARE — a port may `dropMethods` an accessor — answers `None` for the
      * WHOLE record rather than shortening the list, and `None` is also what clears `isRecord`, so
      * the class ships exactly as it did before this row was lowered. A SHORT list is the one answer
      * worse than none: `equals` and `hashCode` over a SUBSET of the components are valid scala
      * computing something java never computed, at no error and no moved count. `Some(Nil)` is a
      * different fact and a legal one — `record Empty()` has no components, and java gives it an
      * `equals` and a `toString` all the same. */
    private def recordComponents(t: CtType[?], id: SymId): Option[List[RecordComponent]] = t match
      case r: CtRecord =>
        val declared = r.getRecordComponents.asScala.toList.sortBy(posKey)
        val joined = declared.flatMap { c =>
          val nm  = c.getSimpleName
          // the ACCESSOR is java's own definition of one (JLS 8.10.3): the NILARY method with the
          // component's name. Looked up on the Spoon type so that an EXPLICITLY WRITTEN accessor —
          // which java permits, and which then answers something other than the field — is the one
          // that is found, exactly as it is the one java calls.
          val acc = t.getMethods.asScala.find(m => m.getSimpleName == nm && m.getParameters.isEmpty)
          for
            (fid, _) <- minter.defined(memberKey(id, nm))
            m        <- acc
            (aid, _) <- minter.defined(memberKey(id, nm + erasedSig(m)))
          yield RecordComponent(nm, fid, aid)
        }
        Option.when(joined.sizeIs == declared.size)(joined)
      case _ => scala.None

    /** THE CANONICAL CONSTRUCTOR AS JLS 8.10.4 DECLARES IT — two repairs, both of them defects the
      * parser hands over and neither of them visible to a compile.
      *
      * ==1. A compact constructor's implicit field assignments==
      *
      * `public Point { if (x < 0) throw …; }` is the whole constructor a record may write, and java
      * appends `this.x = x;` for every component AFTER that body, reading the parameters as the
      * body left them (which is what makes a compact constructor able to NORMALISE its arguments).
      * Spoon models the written body and not the appended half, so the emitted class assigned
      * nothing: every backing field kept its default and every accessor answered `0`/`null`, in a
      * class that compiles perfectly. That is a §4.4-class defect arriving through a declaration —
      * no error, no moved count, and only a run could see it.
      *
      * The canonical constructor written out IN FULL is untouched, because its assignments are real
      * java statements the ordinary path already translated; `isCompactConstructor` is Spoon's own
      * answer to which is which, and it is asked rather than inferred from "does this body assign
      * the field", which would be a guess about a body that may assign it conditionally.
      *
      * Components are matched to parameters BY POSITION — a compact constructor's parameter list is
      * the header's, in order, by construction (JLS 8.10.4) — never by name.
      *
      * ==2. The IMPLICIT constructor's parameter ORDER==
      *
      * JLS 8.10.4 makes the canonical constructor's formal parameters the components, IN THE HEADER'S
      * ORDER. Spoon builds its implicit one from `getFields()`, which is not that order and is not
      * even stable across component types — `record Prims(boolean bo, byte by, short sh, char ch,
      * int in, long lo, float fl, double du)` arrives as `(bo, du, fl, lo, in, ch, sh, by)`. The
      * `CtorFunnel` then promotes those parameters into the emitted class's own list while every
      * translated `new Prims(…)` keeps java's argument order, so the two disagree PER SLOT: a
      * compile error where the types differ, and a silently transposed pair where they do not.
      *
      * Reordered by WHICH FIELD EACH PARAMETER IS ASSIGNED TO, read out of the constructor's own
      * body, rather than by matching parameter names to component names. The name correspondence is
      * real (JLS 8.10.1 gives all three one name) but it is a string test about a list this function
      * is reordering, and the body already states the pairing structurally. A constructor whose body
      * does not have that shape — one the record WROTE, which may assign conditionally or not at all
      * — accounts for no component and is left exactly as it is. */
    private def canonicalised(t: CtType[?], id: SymId, comps: List[RecordComponent],
                              ctors: List[Tree.DefDef]): List[Tree.DefDef] =
      if comps.isEmpty then ctors
      else
        val byId: Map[SymId, CtConstructor[?]] = t match
          case c: CtClass[?] =>
            c.getConstructors.asScala.toList
              .flatMap(k => minter.defined(memberKey(id, "<init>" + erasedSig(k))).map(_._1 -> k))
              .toMap
          case _ => Map.empty
        val compact = byId.collect { case (sid, k) if k.isCompactConstructor => sid }.toSet
        val implicitCanonical = byId.collect { case (sid, k) if k.isImplicit => sid }.toSet
        ctors.map(reordered(comps, implicitCanonical, _)).map { d =>
          if !compact.contains(d.symbol) then d
          else
            val ps = d.paramss.headOption.getOrElse(Nil)
            val at = d.origin
            val assigns = comps.zipWithIndex.flatMap { (c, k) =>
              ps.lift(k).map { p =>
                val ft = minter.defined(memberKey(id, c.name)).map(_._2.info).getOrElse(NoType)
                Tree.Assign(
                  Tree.Select(Tree.This(id, TypeRef(NoPrefix, id), at), c.field, ft, at),
                  Tree.Ident(p.symbol, p.tpt.tpe, at), unitT, at)
              }
            }
            d.copy(rhs = d.rhs.map {
              case b: Tree.Block => b.copy(stats = b.stats ++ assigns)
              case other         => Tree.Block(other :: assigns, Tree.Literal(Constant.UnitC, unitT, at), unitT, at)
            })
        }

    /** …repair 2 of [[canonicalised]] — the implicit canonical constructor's parameter ORDER, read
      * off the assignments in its own body. Untouched where the body does not account for EVERY
      * component exactly once, which is both the java-written constructor and any shape this rule
      * has not seen. */
    private def reordered(comps: List[RecordComponent], implicitCanonical: Set[SymId],
                          d: Tree.DefDef): Tree.DefDef =
      if !implicitCanonical.contains(d.symbol) then d
      else
        val ps = d.paramss.headOption.getOrElse(Nil)
        /** parameter -> the component field this constructor assigns it to. */
        val assignedTo: Map[SymId, SymId] = d.rhs.toList.flatMap {
          case b: Tree.Block => b.stats
          case other         => List(other)
        }.collect {
          case Tree.Assign(Tree.Select(_: Tree.This, f, _, _), Tree.Ident(p, _, _), _, _) => p -> f
        }.toMap
        val want = comps.map(_.field)
        val inOrder = want.flatMap(f => ps.find(p => assignedTo.get(p.symbol).contains(f)))
        if inOrder.sizeIs != ps.size || inOrder.sizeIs != want.size then d
        else d.copy(paramss = List(inOrder) ++ d.paramss.drop(1))

    /** a Java enum constant → `EnumCase`: its ctor args, and any per-constant method overrides
      * (from its anonymous-class body), each keyed under the CONSTANT so it doesn't collide
      * with the enum's abstract method of the same name.
      *
      * ==FIELDS as well as methods==
      * A Java enum constant's body is an anonymous class body, so it may declare fields — and
      * `static final` ones at that, since JLS 8.1.3 permits statics in an anonymous class when they
      * are constant variables. `DefaultRoomType.CASTLE { public static final int MIN_SIZE = 7,
      * MIN_TOWER = 3; … }` in noise4j is the shape, read UNQUALIFIED from the constant's own
      * `carve` and `isValid` bodies two lines below. Harvesting only `CtMethod` dropped both,
      * silently: the emitted `case object` was structurally correct and referred to two names that
      * did not exist (4 errors, and no check saw them — the omissions check counts what the TIR
      * carries, and this never reached the TIR at all).
      *
      * They need no home of their own. A Scala `case object`'s body IS the constant's scope, so a
      * `val` there is exactly the Java static's visibility — which is why this is a frontend
      * harvest and not an emitter change.
      */
    private def enumCase(enumId: SymId, v: CtEnumValue[?]): Tree.EnumCase =
      val vlead = leadingOf(v)
      val caseId = minter.define(memberKey(enumId, v.getSimpleName))(sid =>
        Symbol(sid, v.getSimpleName, qualified(enumId, v.getSimpleName), Flags(isStatic = true), enumId, TypeRef(NoPrefix, enumId))
      )
      val bt = new BodyTranslator(enumId, enumId)
      val (args, body) = v.getDefaultExpression match
        case nc: CtNewClass[?] =>
          val a = nc.getArguments.asScala.toList.map(bt.exprOf)
          val b = Option(nc.getAnonymousClass).toList.flatMap(_.getTypeMembers.asScala.toList).collect {
            case f: CtField[?]  => fieldDef(caseId, f)
            case m: CtMethod[?] => execDef(caseId, m, m.getSimpleName)
          }
          (a, b)
        case cc: CtConstructorCall[?] => (cc.getArguments.asScala.toList.map(bt.exprOf), Nil)
        case _                        => (Nil, Nil)
      Tree.EnumCase(caseId, args, body, originOf(v), leading = vlead ++ deepComments(v))

    /** distinguishes anonymous classes within one enclosing type; traversal order is deterministic
      * (every member list is `sortBy(posKey)`), so the minted keys are stable across runs. */
    private var anonSeq = 0

    /** A Java ANONYMOUS CLASS — `new Base(args) { members }`.
      *
      * Until this existed, `ctorCall` read `CtConstructorCall` and never asked whether the node was
      * the `CtNewClass` subtype, so every anonymous body in the corpus was DISCARDED: `addListener(
      * new ClickListener() { public void clicked(…) {…} })` emitted as a bare `new ClickListener()`.
      * That compiles — a listener with no overrides is a valid listener — and every libGDX button
      * silently did nothing. Scala's anonymous-class expression is the exact counterpart, so the
      * members translate through the ordinary declaration machinery.
      *
      * Two things differ from a named class:
      *   - the members are owned by a SYNTHETIC symbol, so their keys cannot collide with the
      *     enclosing class's (two listeners in one class both declaring `clicked` would otherwise
      *     intern to one symbol);
      *   - but their bodies translate with `this` bound to the ENCLOSING class, because that is what
      *     Spoon reports for the implicit `this` of every enclosing member they reach. The emitter
      *     renders it `Outer.this.m` — inside a Scala anonymous class a bare `this` is the anonymous
      *     instance, exactly as in Java.
      *
      * Captured locals need no lowering: javac synthesises constructor parameters for them, Scala
      * closes over them directly. They are seeded by NAME so the xref resolves them to the real
      * local rather than a stub. */
    private def anonClass(nc: CtNewClass[?], enclosing: SymId, outerVars: Map[String, SymId]): Option[Tree.AnonClass] =
      Option(nc.getAnonymousClass).map { ac =>
        anonSeq += 1
        val key = s"${minterKeyOf(enclosing)}#<anon>$anonSeq"
        val id = minter.define(key)(sid =>
          Symbol(sid, "<anon>", minter.fullNameOf(enclosing) + "$" + anonSeq, Flags(), enclosing, TypeRef(NoPrefix, sid))
        )
        // the name Spoon gives the anonymous class (`DragAndDrop$1`) — how a `this` inside the body
        // that means the ANONYMOUS instance is recognised.
        val qname = try ac.getQualifiedName catch { case _: Throwable => "" }
        val dropped = List.newBuilder[String]
        val members = ac.getTypeMembers.asScala.toList.sortBy(posKey).flatMap {
          case f: CtField[?]  => List(fieldDef(id, f, enclosing, outerVars, id, qname))
          case m: CtMethod[?] =>
            List(execDef(id, m, m.getSimpleName, enclosing, outerVars, overridesInherited(m), id, qname))
          case c: CtConstructor[?] if c.isImplicit => Nil // the compiler-synthesised anonymous ctor
          // instance-initializer block (the double-brace idiom) — plain statements in the anon body
          case a: CtAnonymousExecutable if !a.hasModifier(ModifierKind.STATIC) =>
            List(execDef(id, a, "<initblock>", enclosing, outerVars, false, id, qname))
          case other =>
            dropped += s"${other.getClass.getSimpleName.stripSuffix("Impl")} ${other.getSimpleName}"
            Nil
        }
        // java's SAM question, asked ONCE, where the class file is — see `samAnswerOf`. The target
        // is the type the `new` NAMED, not the anonymous class Spoon synthesised for it.
        val sam = try samAnswerOf(nc.getType) catch { case _: Throwable => Sam.Answer.Unreadable }
        Tree.AnonClass(id, members, originOf(nc), dropped.result(), sam)
      }

    /** Does this anonymous-class method OVERRIDE an inherited one? Scala REQUIRES `override` when
      * the redefined member is concrete — and `ClickListener.clicked` has an empty body, so every
      * listener body in libGDX is one — while merely PERMITTING it when the member is abstract
      * (`Comparator.compare`). Marking every genuine override is therefore both necessary and safe.
      *
      * Spoon answers it from the resolved hierarchy; where it cannot (an unresolved supertype under
      * noClasspath), fall back to a name+arity match over the supertypes that DO resolve, and to
      * `false` when even that is unknown — an absent `override` fails loudly, a spurious one would
      * too, so neither can be silent. */
    /** every java class silently extends `java.lang.Object`, and its members land on scala's `Any`
      * / `AnyRef` — which scala requires `override` for and java does not. Spoon reports no
      * inherited declaration for them (there is no `Object` in the model under noClasspath), so the
      * hierarchy walk below cannot see it. These five are the whole set java lets you redeclare. */
    private def overridesInherited(m: CtMethod[?]): Boolean =
      // A java STATIC method never overrides — it HIDES. `SnapshotArray.with` and `Array.with` are
      // two unrelated statics that java resolves by the static type of the receiver; and in scala
      // they land in COMPANION objects, which inherit nothing from each other at all. Spoon's
      // `getTopDefinitions` reports the hidden one just as it reports a real override, so this has
      // to be excluded here rather than relied on downstream.
      !(try m.isStatic catch { case _: Throwable => false }) &&
        (universalMember(m) || inheritedFromSource(m))

    /** Does this redeclare one of `java.lang.Object`'s members? Matched on the full SIGNATURE, not
      * name and arity: `equals(VertexAttribute)` is an OVERLOAD that overrides nothing, and marking
      * it `override` is an error scala reports and java has no opinion on. */
    private def universalMember(m: CtMethod[?]): Boolean =
      val ps = m.getParameters.asScala.toList.map(p =>
        try p.getType.getQualifiedName catch { case _: Throwable => "?" })
      (m.getSimpleName, ps) match
        case ("toString" | "hashCode" | "clone" | "finalize", Nil) => true
        case ("equals", List("java.lang.Object"))                   => true
        case _                                                       => false

    private def inheritedFromSource(m: CtMethod[?]): Boolean =
      val top = try m.getTopDefinitions.asScala.toList catch { case _: Throwable => Nil }
      if top.exists(_ ne m) then true
      else
        val n   = m.getSimpleName
        // by full SIGNATURE, not arity: java overloads freely, and `draw(Batch, float)` does not
        // override an inherited `draw(Batch, int)`. Marking it `override` is an error scala reports
        // ("overrides nothing") and java has no opinion on — 48 sites in this corpus alone.
        def sig(x: CtMethod[?]): List[String] =
          x.getParameters.asScala.toList.map(p => try p.getType.getQualifiedName catch { case _: Throwable => "?" })
        val mine = sig(m)
        // …AND THE ANCESTOR'S SIGNATURE IS READ UNDER THE `extends` CLAUSE'S SUBSTITUTION, which is
        // what an exact-string comparison silently omits. `AstActionHandler<C, N, A, H>` declares
        // `processNode(N, boolean, BiConsumer<N, A>)`, and a subclass reached through
        // `extends NodeVisitor extends AstActionHandler<NodeVisitor, Node, Visitor<Node>, …>`
        // declares `processNode(Node, boolean, BiConsumer<Node, Visitor<Node>>)`. Those are ONE
        // member to java (JLS 8.4.2, after the substitution) and two strings here, so the fallback
        // answered `false` for every override through a generic superclass whose parameter the
        // method actually mentions — 3 rows on one port, each an emitted member with NO modifier
        // under a parent that declares it, which `RefChecks` reports as ``needs `override` modifier``
        // and nothing else can see (`ENGINE-LIMITS.md` K28.2).
        //
        // The frame is composed one edge at a time and the actuals of each `extends` clause are read
        // THROUGH the frame in force where they are WRITTEN — the same composition `actualFor` makes
        // for the SAM question, at a different value type on purpose: this comparison is over
        // `getQualifiedName`, which is already erased, so a frame of NAMES is exactly as precise as
        // the question and a frame of references would be a second, finer answer nothing here reads.
        //
        // A RAW supertype contributes an EMPTY frame, so the ancestor's variable stays unsubstituted
        // and the match declines — java would erase it to its bound, and declining is the direction
        // whose error is a missing `override` scalac names rather than a spurious one it rejects.
        def declares(t: CtTypeReference[?], subst: Map[String, String], fuel: Int): Boolean =
          if t == null || fuel <= 0 then false
          else
            val decl = try t.getTypeDeclaration catch { case _: Throwable => null }
            if decl == null then false
            else
              // this declaration's own frame: its formal parameters bound to the arguments the
              // `extends` clause wrote, each first read through the frame of the scope it is in.
              val formals = try decl.getFormalCtTypeParameters.asScala.toList.map(_.getSimpleName)
                            catch { case _: Throwable => Nil }
              val actuals = (try t.getActualTypeArguments.asScala.toList catch { case _: Throwable => Nil })
                .map { a =>
                  val q = try a.getQualifiedName catch { case _: Throwable => "?" }
                  subst.getOrElse(q, q)
                }
              val here = if formals.sizeIs == actuals.size then formals.zip(actuals).toMap else Map.empty[String, String]
              // a PRIVATE ancestor method is not inherited at all, so it cannot be overridden:
              // `GL30Interceptor.check()` is private and `GL31Interceptor.check()` is protected —
              // two unrelated methods to java, and `override` on the second is an error.
              decl.getMethods.asScala.exists(x => x.getSimpleName == n && (x ne m) &&
                                                  sig(x).map(s => here.getOrElse(s, s)) == mine &&
                                                  !(try x.isPrivate catch { case _: Throwable => false })) ||
                (decl match { case c: CtClass[?] => declares(c.getSuperclass, here, fuel - 1); case _ => false }) ||
                (try decl.getSuperInterfaces.asScala.exists(declares(_, here, fuel - 1)) catch { case _: Throwable => false })
        m.getDeclaringType match
          case c: CtClass[?] =>
            declares(c.getSuperclass, Map.empty, 8) ||
              (try c.getSuperInterfaces.asScala.exists(declares(_, Map.empty, 8)) catch { case _: Throwable => false })
          case _ => false

    private def defineType(t: CtType[?], owner: Option[SymId] = scala.None,
                          sourceName: Option[String] = scala.None): SymId =
      val q = typeKey(t.getReference)
      // A substituted type stays in the model with its references resolved (see `Substituted`), but
      // carries the tag so later phases can rewrite uses into whatever replaces it.
      val tags: Set[SymTag] = if subs.dropsType(q) then Set(Substituted(q)) else Set.empty
      // A TYPE's annotation values are constant expressions, so they translate on the ordinary
      // expression path — and this was the one harvest with no translator to run it, which dropped
      // every argument-bearing annotation on every type in every port (`ENGINE-LIMITS.md` T16).
      //
      // `resolve` FIRST and `define` after: they mint the same id for the same key (`define` calls
      // `resolve`), so the symbol order every later pass depends on is unchanged and the translator
      // can be built against the id before the record exists. `methodId = classId = id` is the
      // shape `enumCase` already uses for an enum constant's arguments — `this` denotes the type,
      // and a constant expression owns no locals.
      //
      // WHICH families are carried is the port's ([[AnnotationPolicy]]); the default claims none,
      // so this reads exactly as it did before the translator arrived.
      val (anns, annDropped) =
        annotationsOf(t, Some(new BodyTranslator(minter.resolve(q), minter.resolve(q))), annotations.claims)
      minter.define(q)(id =>
        Symbol(id, sourceName.getOrElse(t.getSimpleName), q, typeFlags(t),
               owner.getOrElse(ownerSym(t)), TypeRef(NoPrefix, id), tags = tags,
               annotations = anns, droppedAnnotations = annDropped, permits = permittedTypes(t)))

    /** java's `permits` clause, INTERNED — see [[balticporter.tir.Symbol.permits]] for why the ids
      * and not the names, and `TirEmitter.sealOf` for the one question they answer.
      *
      * `Minter.external` is exactly the right interning door: a permitted type this program declares
      * is (or will be) `define`d under the same key, so both sides of the accounting compare one id;
      * a permitted type the parse never saw — an `excludeGlobs` file, a unit whose translation was
      * refused — interns as an external stub that no subtype set can contain, which is the case the
      * seal must be widened for.
      *
      * SORTED by key, because Spoon hands back a `Set` and this list reaches an emitted DECISION's
      * detail: an artifact whose row order is a hash order is one no baseline can diff. */
    private def permittedTypes(t: CtType[?]): List[SymId] =
      t match
        case s: spoon.reflect.declaration.CtSealable =>
          s.getPermittedTypes.asScala.toList
            .map(r => typeKey(r) -> r)
            .sortBy(_._1)
            .map((k, r) => minter.external(k, r.getSimpleName))
        case _ => Nil

    private def ownerSym(t: CtType[?]): SymId =
      Option(t.getDeclaringType).map(dt => minter.external(typeKey(dt.getReference), dt.getSimpleName)).getOrElse(SymId.None)

    /** superclass (Extends, first) then interfaces (Mixin) — the parent linearization. */
    private def superTypes(t: CtType[?]): List[TypeTree] =
      val sc = t match
        case c: CtClass[?] => Option(c.getSuperclass).filter(_.getQualifiedName != "java.lang.Object")
        case _             => None
      (sc.toList ++ t.getSuperInterfaces.asScala.toList).map(tr => tt(tpe(tr), t))

    /** `selfClass` is the class a body's `this` denotes when it is NOT the member's owner — the
      * case for an ANONYMOUS class, whose members are owned by a synthetic symbol (so their keys
      * stay distinct) while Java's implicit `this` inside them still reports the ENCLOSING class
      * for every enclosing member it reaches. Defaulting to `owner` keeps every other caller
      * unchanged. */
    private def selfOf(owner: SymId, selfClass: SymId): SymId =
      if selfClass == SymId.None then owner else selfClass

    /** …inside the DECLARATION obligation scope, which is the third one this frontend opens.
      *
      * A field's initialiser is a JLS 5.2 assignment slot exactly as a local's is — `coercedExprOf`
      * is literally the same call — and it was the one slot no row could be owed at, because a
      * `CtField` is neither a `CtStatement` nor a `CtExpression` and therefore enters neither
      * dispatch. `Differences.everySlot` names it now (`Dispatch.Declaration`), and the consults are
      * the same three, through the same function: a rule stated twice is a rule that gets two
      * answers (`ENGINE-LIMITS.md` F8).
      *
      * The scope opens for EVERY field, initialiser or not — the empty slot list answers `None`
      * three times and discharges honestly, which is the same shape a local with no initialiser or
      * a bare `return` already has. */
    private def fieldDef(owner: SymId, f: CtField[?], selfClass: SymId = SymId.None, outerVars: Map[String, SymId] = Map.empty,
                         anonSelf: SymId = SymId.None, anonQName: String = ""): Tree.ValDef =
      Lowering.of(SpoonKinds.nameOf(f.getClass), Dispatch.Declaration, originOf(f), f)(fieldDef1(owner, f, selfClass, outerVars, anonSelf, anonQName))

    private def fieldDef1(owner: SymId, f: CtField[?], selfClass: SymId, outerVars: Map[String, SymId],
                          anonSelf: SymId, anonQName: String)(using Obligations): Tree.ValDef = withStatic(fieldFlags(f).isStatic) {
      val ft = tpe(f.getType)
      val flead = leadingOf(f)
      val (fanns, fannDropped) = annotationsOf(f, None)
      val id = minter.define(memberKey(owner, f.getSimpleName))(sid =>
        Symbol(sid, f.getSimpleName, qualified(owner, f.getSimpleName), fieldFlags(f), owner, ft,
               annotations = fanns, droppedAnnotations = fannDropped)
      )
      // a field initializer is a real expression: translate it so its usages are traced,
      // attributed to the field (not a method).
      //
      // A STATIC field's initialiser sees NONE of the class's type parameters — java's rule, and
      // scala's too once the field lands in the companion object. Carried in the frame for the
      // reason `execDef` states: an anonymous class in that initialiser declares INSTANCE methods,
      // which reset the `inStatic` flag the fill site reads.
      val staticFrame = fieldFlags(f).isStatic
      if staticFrame then tpAccessible.prepend(Map.empty)
      val rhs =
        try
          val bt = new BodyTranslator(id, selfOf(owner, selfClass), anonSelf, anonQName)
          bt.seedVars(outerVars)
          // …AT THE ARM, never inside `coerce` — `coerce` is not reached for a field with no
          // initialiser, so a consult in there would report a hole at exactly the declarations
          // where the difference does not apply (`slotConsults`' own note).
          bt.slotConsultsAt(Option(f.getDefaultExpression).map(f.getType -> _).toList, originOf(f))
          Option(f.getDefaultExpression).map(e => bt.coercedExprOf(f.getType, e))
        finally if staticFrame then tpAccessible.remove(0)
      // `deepComments` AFTER the initialiser translated: a comment inside `new Foo(/* why */ 3)`
      // has nowhere of its own in the TIR and hoists to the field.
      Tree.ValDef(id, tt(ft, f), rhs = rhs, origin = originOf(f), leading = flead ++ deepComments(f))
    }

    private def execDef(owner: SymId, m: CtExecutable[?], name: String, selfClass: SymId = SymId.None,
                        outerVars: Map[String, SymId] = Map.empty, overrides: Boolean = false,
                        anonSelf: SymId = SymId.None, anonQName: String = ""): Tree.DefDef = withStatic(execFlags(m).isStatic) {
      val mkey = memberKey(owner, name + erasedSig(m))
      val id   = minter.resolve(mkey)
      // claimed before the body translates, so a statement's `deepComments` cannot reach the
      // method's own Javadoc. No `deepComments` HERE: every statement in the body harvests its
      // own subtree, which is what puts an expression comment above the statement it was written
      // in rather than above the whole method.
      val mlead = leadingOf(m)
      val allTps = m match
        case ftd: CtFormalTypeDeclarer => ftd.getFormalCtTypeParameters.asScala.toList
        case _                         => Nil
      // …minus the ones java itself cannot instantiate. See [[unwritableResultVars]]: these are
      // ERASED to their own bound rather than declared, so they never reach `mintTypeParams` and no
      // `[B <: …]` clause is emitted for them.
      val erasedTps = unwritableResultVars(m)
      val mtps      = allTps.filterNot(tp => erasedTps.exists(_.getSimpleName == tp.getSimpleName))
      val (frame, tpDefs) = mintTypeParams(mkey, id, mtps)
      val savedOverriding = inOverridingMember
      inOverridingMember = overrides
      tpScopes.prepend(frame); tpIsExec.prepend(true); tpDecls.prepend(declFrame(mtps))
      // TWO passes, because an F-bound mentions ITSELF: seed every erased name at `?` so that
      // translating the bound renders the self-reference as a wildcard, then replace the frame with
      // the bound that produced. One mechanism for both halves — the `?` an F-bound's self-reference
      // becomes is the same `?` the whole erasure is built out of.
      tpErased.prepend(erasedTps.map(_.getSimpleName -> (TypeBounds(NoType, NoType): TypeRepr)).toMap)
      if erasedTps.nonEmpty then
        val bounds = erasedTps.map(tp => tp.getSimpleName -> tpe(tp.getSuperclass)).toMap
        tpErased.remove(0); tpErased.prepend(bounds)
      // a method sees its class's accessible params plus its own — and a STATIC one sees ONLY its
      // own, because java's static context has no access to the class's parameters and scala's
      // companion object cannot name them either. Carried in the FRAME rather than left to the
      // `inStatic` flag at the fill site: the flag is per-EXECUTABLE, so it is reset the moment an
      // anonymous class inside a static initialiser declares an instance method, and the enclosing
      // class's `T` becomes reachable again. That is `Not found: type T`, three times in gdx-vfx's
      // `PrioritizedArray` — whose `static final Pool<Wrapper> pool = new Pool<Wrapper>() { … }`
      // inside `class Wrapper<T>` is written RAW for exactly the reason java gives.
      tpAccessible.prepend(
        (if execFlags(m).isStatic then Map.empty else tpAccessible.headOption.getOrElse(Map.empty)) ++ frame)
      tpExecNames.prepend(tpExecNames.headOption.getOrElse(Set.empty) ++ frame.keySet)
      val bt = new BodyTranslator(id, selfOf(owner, selfClass), anonSelf, anonQName)
      bt.seedVars(outerVars) // an anonymous class captures the enclosing method's effectively-final locals
      val ps = m.getParameters.asScala.toList
      // `public boolean equals (Object obj)` overrides `java.lang.Object.equals`, which in scala is
      // `equals(x: Any)`. Rendered `equals(obj: Object)` it does not override it — it CLASHES with
      // it, same signature after erasure, and scala rejects the class outright. `Any` is also the
      // wider type, so no body that used the parameter can break: `instanceof`, `==` and a cast all
      // work on it. (52 classes in gdx core; invisible until the last RefChecks error cleared,
      // since the name-clash check runs in a still later phase.)
      def anyForEquals(p: CtParameter[?]): TypeRepr =
        if name == "equals" && ps.sizeIs == 1 &&
           (try p.getType.getQualifiedName == "java.lang.Object" catch { case _: Throwable => false })
        then TypeRef(NoPrefix, minter.external("scala.Any", "Any"))
        else tpe(p.getType)
      // A PARAMETER's annotations are harvested like a field's and a method's.
      //
      // They were the one declaration kind `annotationsOf` was never called for, and the omission
      // was invisible from either end: nothing renders a parameter annotation, so the emitted file
      // is identical with them and without them, and no check could report a symbol property that
      // was never populated. It is a real gap, not a formatting one — a Java library states most of
      // its nullability contract ON PARAMETERS (`@Null Actor a`), and a phase that reads
      // `Symbol.annotations` therefore saw a library's returns and fields and none of its
      // arguments. Measured on the corpus's most-annotated port: 389 upstream parameter sites
      // reaching zero symbols.
      //
      // With the body translator in scope, exactly as for a METHOD: an annotation carrying
      // arguments is then carried whole instead of being reported as undroppable, which is the
      // difference between `@A(x)` and `@A` — a different annotation (see `annotationsOf`).
      val pvs = ps.map { p =>
        val pt  = anyForEquals(p)
        val (panns, pannDropped) = annotationsOf(p, Some(bt))
        val pid = minter.define(minterKeyOf(id) + "%" + p.getSimpleName)(sid =>
          Symbol(sid, p.getSimpleName, qualified(id, p.getSimpleName), Flags(isParam = true, isVararg = p.isVarArgs), id, pt,
                 annotations = panns, droppedAnnotations = pannDropped)
        )
        bt.registerVar(p, pid)
        Tree.ValDef(pid, tt(pt, p), rhs = None, origin = originOf(p))
      }
      val ret = m match
        // a constructor's Spoon type is its declaring class; that is not a return
        // position, so don't record it as a member type — use Unit.
        case _: CtConstructor[?]      => unitT
        case named: CtTypedElement[?] => tpe(named.getType)
        case _                        => unitT
      val sig = MethodType(ps.map(p => p.getSimpleName -> anyForEquals(p)), ret)
      val (anns, annDropped) = annotationsOf(m, Some(bt))
      // `descriptorOf` reads the PARSER's parameter types, so it is `Object` here and not the `Any`
      // `sig` above already carries — see its own note.
      minter.set(id, Symbol(id, name, qualified(owner, name), execFlags(m).copy(isOverride = overrides), owner, sig,
                            annotations = anns, droppedAnnotations = annDropped, descriptor = descriptorOf(m)))
      // translate the body (with param + type-param scope in place) — this is what makes
      // Call / field-ref usages and `callersOf` real. Abstract/interface methods have none.
      //
      // …and an ANNOTATION TYPE ELEMENT has no body EITHER, which is why this arm read it as an
      // ordinary abstract method and lost the one thing it does carry. `CtAnnotationMethod`
      // EXTENDS `CtMethod`, so this arm takes it (§4.56's parser-hierarchy hazard, arrived at
      // through the dispatch rather than through an arm order), `getBody` is null for every one,
      // and JLS 9.6.2's DEFAULT hangs off `getDefaultExpression` — a slot nothing here read. It
      // is the same JLS 5.2 assignment slot a field initialiser is, so it goes through the same
      // `coercedExprOf` call `fieldDef1` makes.
      val body = Option(m.getBody).map(b => bt.methodBody(b)).orElse(annotationDefault(m, bt))
      tpScopes.remove(0); tpIsExec.remove(0); tpDecls.remove(0); tpAccessible.remove(0); tpExecNames.remove(0)
      tpErased.remove(0)
      inOverridingMember = savedOverriding
      Tree.DefDef(id, paramss = List(pvs), returnTpt = tt(ret, m), rhs = body, origin = originOf(m),
                  tparams = tpDefs, leading = mlead)
    }

    /** JLS 9.6.2's DEFAULT VALUE, for the one executable that has one and no body.
      *
      * `None` for every other executable, which is what makes the `orElse` above exact rather than
      * a fallback: a java method either HAS a body or is abstract, and only an annotation type
      * element can be neither and still carry an expression. The emitter reads this `rhs` as the
      * default of the constructor parameter the element becomes — see `TirEmitter.classDef1`'s
      * annotation arm, which is the one place a `Tree.DefDef` with a body can be an `@interface`
      * member at all (JLS 9.6 admits only elements, constants and member types in one). */
    private def annotationDefault(m: CtExecutable[?], bt: BodyTranslator): Option[Term] = m match
      case am: CtAnnotationMethod[?] =>
        Option(am.getDefaultExpression).map(e => bt.coercedExprOf(am.getType, e))
      case _ => scala.None

    /** Java annotations on a declaration, plus the names of any this could not carry.
      *
      * Annotation element values are constant expressions, so they translate with the ordinary
      * expression path; one that throws is REPORTED (its annotation goes to `dropped`) rather than
      * silently emitted without its arguments, which would be the same defect one level down.
      * Spoon's `@interface` for a JDK/test annotation is a shadow, so the type is taken from the
      * reference's qualified name and needs no declaration. */
    private def annotationsOf(el: CtElement, bt: Option[BodyTranslator],
                              claimed: String => Boolean = _ => true): (List[Annot], List[String]) =
      val out     = collection.mutable.ListBuffer[Annot]()
      val dropped = collection.mutable.ListBuffer[String]()
      // …and a set that cannot be READ AT ALL is COUNTED, not read as "this declaration has none"
      // (§4.6). `Nil` alone is a fabricated fact of the worst kind here: every annotation on the
      // declaration disappears, `OmissionCheck` sees an empty `droppedAnnotations` and reports
      // nothing, and a `@Test` or a `@JsonProperty` is simply not there. The sentinel is the one
      // this function already uses one line down for an annotation whose TYPE will not resolve.
      val as =
        try el.getAnnotations.asScala.toList
        catch { case _: Throwable => dropped += SpoonTir.UnreadableAnnotations; Nil }
      as.foreach { a =>
        val ref = try a.getAnnotationType catch { case _: Throwable => null }
        if ref == null then dropped += SpoonTir.UnresolvedAnnotation
        else
          val fqn = ref.getQualifiedName
          // …and a VALUE LIST that will not read is not an EMPTY one. Left as `Nil` it took the
          // marker-annotation path below and emitted `@A` BARE — the exact thing the comment under
          // that path forbids, arriving through a catch rather than through the logic. `None` here
          // is "unknown" and routes to the reporting arm; `Some(Nil)` is a real marker annotation.
          val vals: Option[List[(String, Object)]] =
            try Some(a.getValues.asScala.toList)
            catch { case _: Throwable => scala.None }
          // Without an expression translator in scope only MARKER annotations can be carried
          // faithfully; one with arguments is REPORTED rather than emitted bare, since emitting
          // `@A` where Java wrote `@A(x)` changes its meaning.
          if vals.contains(Nil) then
            out += Annot(TypeRef(NoPrefix, minter.external(fqn, simpleName(fqn))), Nil, originOf(a))
          // …and one WITH ARGUMENTS is carried only where a translator exists AND the port claims
          // the family. `claimed` defaults to "every one", which is what a site with a translator
          // has always done; the TYPE harvest passes the port's policy, whose default claims none.
          // Either way an uncarried annotation is REPORTED rather than emitted bare — `@A` where
          // java wrote `@A(x)` is a different annotation.
          else if !claimed(fqn) then dropped += fqn
          else (bt, vals) match
            case (None, _)          => dropped += fqn
            // the value list would not READ — reported, and the arguments are unknown rather than
            // absent, which is the distinction the `Option` above exists to carry.
            case (_, scala.None)    =>
              dropped += fqn
              dropped += SpoonTir.UnreadableAnnotations
            case (Some(b), Some(vs)) =>
              try
                val args = vs.map { (k, v) =>
                  val e0 = v.asInstanceOf[CtExpression[?]]
                  k -> arrayShorthand(ref, k, e0, b.exprOf(e0))
                }
                out += Annot(TypeRef(NoPrefix, minter.external(fqn, simpleName(fqn))), args, originOf(a))
              // A TRANSLATION THAT FAILED is not a translation DECLINED, and both used to write the
              // same one string. `!claimed` and a missing translator are POLICY — the port said no —
              // while this is the expression path throwing on a constant expression, which is an
              // engine defect somebody has to be able to see. The FQN stays in the list on its own,
              // because every consumer of `droppedAnnotations` matches it exactly and by name
              // (`TestFrameworkTransform` still has to recognise a `@Test` whose arguments would not
              // translate, or the suite loses a test); the sentinel rides BESIDE it, as
              // `<unresolved>` already does, so `omissions` shows one extra row saying which of the
              // two kinds of drop happened. Not folded into the FQN: `Symbol.droppedAnnotations` is
              // documented as annotations "by name", and a decorated name is a name nothing matches.
              catch
                case _: Throwable =>
                  dropped += fqn
                  dropped += SpoonTir.FailedAnnotationArguments
      }
      (out.toList, dropped.toList)

    /** Java's single-value shorthand for an ARRAY-typed annotation element.
      *
      * `@SuppressWarnings("unchecked")` means `value = {"unchecked"}`. Scala has no such shorthand
      * and wants `Array("unchecked")`, so the element's DECLARED type decides whether to wrap.
      * Left alone when the declaration cannot be read — a wrong wrap would be worse than the
      * compile error it replaces. */
    private def arrayShorthand(ref: CtTypeReference[?], key: String, e: CtExpression[?], t: Term): Term =
      if e.isInstanceOf[CtNewArray[?]] then t
      else
        val declared =
          try Option(ref.getTypeDeclaration).flatMap(d =>
                d.getAllMethods.asScala.find(_.getSimpleName == key).flatMap(m => Option(m.getType)))
          catch { case _: Throwable => None }
        declared match
          case Some(arr: CtArrayTypeReference[?]) =>
            val ct = tpe(arr.getComponentType)
            val at = AppliedType(TypeRef(NoPrefix, minter.external("scala.Array", "Array")), List(ct))
            Tree.NewArray(TypeTree(ct, originOf(e)), Nil, Some(List(t)), at, originOf(e))
          case _ => t

    private def qualified(owner: SymId, member: String): String = minter.fullNameOf(owner) + "#" + member
    private def unitT: TypeRepr = TypeRef(NoPrefix, minter.external("scala.Unit", "Unit"))

    // ---- flags ----
    private def has(m: CtModifiable, k: ModifierKind): Boolean = m.hasModifier(k)
    import ModifierKind.*

    /** Java's FOURTH access level, and it is NOT "no modifier is present".
      *
      * "Package-private" is what the JLS calls *default access*, and the language grants public or
      * private access implicitly in three places where nothing is written (DESIGN §8.7):
      *
      *   - a member of an INTERFACE or of an `@interface` is implicitly `public` (JLS 9.4, 9.6) —
      *     so is a type nested in one (JLS 9.5), and so is an interface FIELD (JLS 9.3);
      *   - an ENUM constructor is implicitly `private` (JLS 8.9.2), and declaring it `public` or
      *     `protected` is a compile error, so the absent modifier is the strongest level rather
      *     than the default one;
      *   - an enum CONSTANT and an anonymous/local class carry no user-written access at all.
      *
      * Reading `hasModifier(PUBLIC)` instead would trust the parser's implicit-modifier model for
      * exactly the declarations where the model is the thing in question (§4.58), so the rule is
      * spelled here from the JLS and the DECLARING TYPE, which Spoon reports structurally. */
    private def implicitlyPublic(el: CtElement): Boolean = el match
      case m: CtTypeMember => m.getDeclaringType.isInstanceOf[CtInterface[?]]
      case _               => false

    /** JLS-effective access for one declaration: exactly one of the three is set, or all are clear
      * and it is public. `implicitPrivate` is the enum-constructor case. */
    private def access(m: CtModifiable, el: CtElement, implicitPrivate: Boolean = false): (Boolean, Boolean, Boolean) =
      val priv = has(m, PRIVATE) || (implicitPrivate && !has(m, PUBLIC) && !has(m, PROTECTED) && !has(m, PRIVATE))
      val prot = !priv && has(m, PROTECTED)
      val pkg  = !priv && !prot && !has(m, PUBLIC) && !implicitlyPublic(el)
      (priv, prot, pkg)

    private def typeFlags(t: CtType[?]): Flags =
      val isAnnot = t.isInstanceOf[spoon.reflect.declaration.CtAnnotationType[?]]
      // a Java `@interface` IS a `CtInterface`, but it must not become a Scala trait — see the
      // emitter: an annotation type is a CLASS extending `scala.annotation.StaticAnnotation`, or
      // nothing can be annotated with it.
      val isTrait = t.isInstanceOf[CtInterface[?]] && !isAnnot
      // an ANONYMOUS or LOCAL class has no access modifier to read and no package half to lose:
      // nothing outside the expression that declares it can name it at all.
      val anonymous = t.getSimpleName == null || t.getSimpleName.isEmpty || t.getSimpleName.forall(_.isDigit)
      val (priv, prot, pkg) = access(t, t)
      Flags(
        isAnnotation = isAnnot,
        isAbstract = (has(t, ABSTRACT) || isTrait) && !isAnnot,
        isFinal = has(t, FINAL),
        // JS-C44 — the RAW java fact and nothing more. Whether scala can reproduce the seal is a
        // question about where the permitted subtypes LAND, which is an emitted-file question and
        // therefore the emitter's (`TirEmitter.sealOf`); deciding it here would put half a rule in
        // a frontend that has no idea what the port writes to which file.
        //
        // The OTHER half of java's clause — WHICH types it permits — is not a flag and is recorded
        // beside this one, on the symbol (`permittedTypes`). The emitter cannot reconstruct it from
        // the extends-edges it can see: a permitted subtype in a file this run never parsed leaves
        // no edge at all, and a seal decided from the survivors alone is a seal java did not write.
        isSealed = has(t, SEALED),
        isTrait = isTrait,
        isEnum = t.isInstanceOf[CtEnum[?]],
        // JS-C43 — the raw java fact, exactly as `isSealed` above. `CtRecord` extends `CtClass`, so
        // the class arm takes a record and, before this flag existed, nothing downstream could tell
        // that it had: the components arrived as ordinary fields, the accessors as ordinary
        // methods, and the class extended `java.lang.Record` with the three members javac generates
        // simply absent. Which members that licenses SYNTHESISING is the emitter's question and
        // this is the one fact it needs.
        isRecord = t.isInstanceOf[CtRecord],
        isPrivate = priv,
        isProtected = prot,
        isPackagePrivate = pkg && !anonymous,
        isStatic = has(t, STATIC),
      )

    private def fieldFlags(f: CtField[?]): Flags =
      // an ENUM CONSTANT is `public static final` implicitly (JLS 8.9.3) and Spoon models it as a
      // field of the enum, which is a CtClass and not a CtInterface — so it needs its own answer.
      val enumConstant = f.isInstanceOf[CtEnumValue[?]]
      val (priv, prot, pkg) = access(f, f)
      Flags(
        isFinal = has(f, FINAL),
        isMutable = !has(f, FINAL),
        isStatic = has(f, STATIC),
        isPrivate = priv,
        isProtected = prot,
        isPackagePrivate = pkg && !enumConstant,
      )

    private def execFlags(m: CtExecutable[?]): Flags = m match
      case mod: CtModifiable =>
        val enumCtor = m match
          case c: CtConstructor[?] => c.getDeclaringType.isInstanceOf[CtEnum[?]]
          case _                   => false
        val (priv, prot, pkg) = access(mod, m, implicitPrivate = enumCtor)
        Flags(
          isAbstract = has(mod, ABSTRACT),
          isFinal = has(mod, FINAL),
          isStatic = has(mod, STATIC),
          isPrivate = priv,
          isProtected = prot,
          isPackagePrivate = pkg,
          isNative = has(mod, NATIVE),
        )
      case _ => Flags()

    private def posKey(el: CtElement): Int =
      val p = el.getPosition
      if p != null && p.isValidPosition then p.getSourceStart else Int.MaxValue

    /** a METHOD-LOCAL class's SOURCE name — JLS 14.3, catalog JS-C30.
      *
      * Spoon reports the BINARY simple name for a local class (`1Local` for the first `Local` in a
      * type), which is the right interning key and is not an identifier: JLS 3.8 says a java
      * identifier may not BEGIN with a digit, so the leading run of digits is exactly the
      * disambiguator and stripping it can never eat a name the source wrote. Where the strip would
      * leave nothing the binary name is kept, which is a name that cannot compile rather than a
      * name that silently means something else. */
    private def localName(t: CtType[?]): String =
      val binary  = t.getSimpleName
      val stripped = binary.dropWhile(_.isDigit)
      if stripped.isEmpty then binary else stripped

    private def simpleName(q: String): String =
      val afterDot = q.substring(q.lastIndexOf('.') + 1)
      afterDot.substring(afterDot.lastIndexOf('$') + 1)

    private def unsupported(el: CtElement, what: String): Nothing =
      val p    = el.getPosition
      val line = if p != null && p.isValidPosition then p.getLine.toString else "?"
      val path = if p != null && p.isValidPosition && p.getFile != null then p.getFile.getPath else "<snippet>"
      throw balticporter.core.Unsupported(path, line, what)

    /** MINT A MARKER instead of failing the whole unit — `DESIGN.md` §6.2/§6.5's first mint site.
      *
      * [[unsupported]] is a refusal and it is the RIGHT kind of refusal; what it is not is
      * per-site. It throws, so one node the frontend cannot model costs the whole COMPILATION
      * UNIT: a single `record` declaration in a 135-file library takes that file's every other
      * type with it, and there is no measured step between "the library ports" and "the library
      * ports except for this file". That is what makes adopting a new syntax family
      * all-or-nothing, and it is exactly what §6.2's marker exists to replace.
      *
      * What changes and what does not: the port still does not ship — the emission gate refuses on
      * any open marker (§6.4) — but the failure is now the size of the construct, every other
      * declaration in the unit translates, and the run REPORTS which construct it was, where, and
      * what a fix would be. Silence is what was never on offer either way (§3.4).
      *
      * '''Falls back to the throw where there is no position.''' §6.2 requires a marker to point at
      * real Java, and [[Tree.Unportable.markerKey]] — the identity the conservation check compares
      * two programs on — is derived from that origin. A marker at `<synthetic>:0:0` would collide
      * with every other one, and the check keyed on it would then report nothing, confidently. A
      * unit-fatal throw for one positionless node is a worse outcome and a truthful one.
      *
      * The catalog id comes from [[SpoonKinds]] rather than from a table here: that registry
      * already says what this frontend does with every kind a Java source can produce, and a second
      * mapping beside it would be a second answer to one question. */
    /** `about` is the node the refusal is ABOUT, where that is not the node the marker STANDS at.
      *
      * The two are the same at every dispatch default, and they come apart wherever the unlowered
      * node carries no source POSITION. Spoon builds `CtCasePattern` as an unpositioned wrapper, so
      * a marker minted at the pattern itself hits the fallback above and the whole unit is lost —
      * which made the registry's `MarkedUnportable` claim for that kind false, with nothing able to
      * report it: the claim is prose, and the one spec that would have seen it was written against
      * a different kind. The enclosing `CtCase` is real java at a real line, which is what §6.2
      * requires of a marker, while the KIND and therefore the catalog row still come from the node
      * the frontend actually has no arm for. */
    private def unlowered(el: CtElement, what: String, tpe: TypeRepr,
                          kind: Option[UnportableKind] = scala.None,
                          about: CtElement = null): Term =
      val subject = if about == null then el else about
      val o = originOf(el)
      if o == Origin.synthetic || o.javaPath.isEmpty then unsupported(el, what)
      else
        val kindName = SpoonKinds.nameOf(subject.getClass)
        Tree.Unportable.open(
          inner  = Tree.Literal(Constant.UnitC, unitT, o),
          kind   = kind.getOrElse(UnportableKind.UnmodelledNodeKind(kindName)),
          // the catalog pointer belongs to the NODE KIND, so it is only right when the refusal IS
          // about the node kind. A blind spot INSIDE an arm that does dispatch on this kind is a
          // different fact — the kind is handled, this shape of it is not — and pointing it at the
          // kind's row would make the registry describe the engine's gap instead of Java's.
          diff   = if kind.isEmpty then SpoonKinds.byName.get(kindName).flatMap(_.catalog) else scala.None,
          what   = what,
          tpe    = tpe,
          origin = o,
        )

    // -----------------------------------------------------------------------
    /** Translates one method/ctor/field-initializer body into TIR terms, resolving every
      * reference to a `SymId`. Covered: locals, assignments, `if`/`while`/`return`/`throw`,
      * blocks, method calls, constructor calls, field/variable access, `this`, casts,
      * ternary, operators (as `x.op(y)` — the quotes.reflect shape), literals. Constructs
      * not yet modeled (for-loops, switch, try, lambdas, arrays, method refs) fail loudly
      * via `Unsupported`, the same anti-omission stance as the BIR frontend — the body node
      * set grows the same way the BIR one did.
      *
      * `classId` is the enclosing class (for `this`); `methodId` owns locals. */
    /** `anonSelf`/`anonQName` are set only for the members of an ANONYMOUS class: the synthetic
      * symbol standing for the anonymous instance, and the name Spoon gives it (`DragAndDrop$1`).
      * `classId` stays the ENCLOSING class, because that is what Spoon reports for the implicit
      * `this` of every enclosing member the body reaches. */
    private final class BodyTranslator(methodId: SymId, classId: SymId,
                                       anonSelf: SymId = SymId.None, anonQName: String = ""):
      private val varIds  = new java.util.IdentityHashMap[CtVariable[?], SymId]()
      private val nameIds = collection.mutable.Map[String, SymId]()

      def registerVar(v: CtVariable[?], id: SymId): Unit =
        varIds.put(v, id); nameIds(v.getSimpleName) = id

      /** locals visible from HERE, by name — handed to a nested anonymous class so the locals it
        * CAPTURES resolve to the real symbols rather than to `?var$x` stubs (the emitted text was
        * already right, since Scala captures by name too; this keeps the xref honest). */
      def varScope: Map[String, SymId] = nameIds.toMap
      def seedVars(m: Map[String, SymId]): Unit = m.foreach { (n, id) => if !nameIds.contains(n) then nameIds(n) = id }

      private def nothingT = TypeRef(NoPrefix, minter.external("scala.Nothing", "Nothing"))
      private def selfT    = TypeRef(NoPrefix, classId)
      private def ty(e: CtTypedElement[?]): TypeRepr = Option(e.getType).map(tpe).getOrElse(NoType)
      private def thisTerm(el: CtElement): Term  = Tree.This(classId, selfT, originOf(el))
      /** a java LABEL on a loop (`outer: for (…)`), the target of `break outer` / `continue outer`.
        * Kept so the emitter can name the corresponding `boundary` and jump to it explicitly. */
      private def labelOf(s: CtStatement): Option[String] =
        try Option(s.getLabel).filter(_.nonEmpty) catch { case _: Throwable => scala.None }

      private def superTerm(el: CtElement): Term = Tree.Super(classId, selfT, originOf(el))
      /** true when a `this`-access targets THIS class (not an enclosing one) — only then does
        * it need qualifying; an outer `Outer.this.x` resolves bare in Scala. */
      private def isOwnThis(ta: CtThisAccess[?]): Boolean =
        Option(ta.getType).map(_.getQualifiedName).forall(_ == minter.fullNameOf(classId))

      /** A `this` used as a VALUE. Inside an anonymous class body it denotes the ANONYMOUS
        * instance — `DragAndDrop`'s drag listener passes `this` to `stage.cancelTouchFocusExcept(
        * EventListener, Actor)`, and it means the LISTENER, not the `DragAndDrop`; emitting
        * `DragAndDrop.this` there is not merely verbose, it is a different object.
        *
        * Only for a `this` Spoon EXPLICITLY types as the anonymous class, and only in value
        * position. As the TARGET of a member access Spoon reports the anonymous class whatever the
        * member's real owner is (`List`'s key listener calling `setSelectedIndex`, declared on
        * `List`), so there the existing resolution — which falls back to the bare name Scala
        * resolves lexically, exactly as Java did — stays in charge. */
      private def thisOf(ta: CtThisAccess[?], el: CtElement): Term =
        if anonSelf != SymId.None && anonQName.nonEmpty &&
           Option(ta.getType).map(_.getQualifiedName).contains(anonQName)
        then Tree.This(anonSelf, TypeRef(NoPrefix, anonSelf), originOf(el))
        else thisTerm(el)

      /** `Outer.this` — the enclosing instance, as its own class symbol. Only for a type that
        * LEXICALLY ENCLOSES the class the access sits in: Spoon also reports a plain `this` used to
        * reach an INHERITED member under the member's DECLARING type (`this.isGlobal` inside
        * `DynamicsModifier.Rotation2D extends DynamicsModifier` comes back typed `DynamicsModifier`),
        * and qualifying that would name a supertype Scala's `Outer.this` syntax cannot denote. */
      private def outerThis(ta: CtThisAccess[?]): Option[Term] =
        val q     = ta.getType.getQualifiedName
        var here  = ta.getParent(classOf[CtType[?]])
        var found = false
        // walk OUT only while each step really captures an enclosing instance (a non-static inner
        // class); a `static` nested class has no `Outer.this` at all, and Spoon reporting one there
        // means it was an inherited-member access, not an enclosing-instance one.
        while here != null && capturesEnclosing(here) && !found do
          here = here.getDeclaringType
          if here != null && here.getQualifiedName == q then found = true
        // An ANONYMOUS enclosing class has NO NAME, so Scala has no `Outer.this` for it (`Pixmap`'s
        // download listener calls its own `failed(t)` from a nested `Runnable`). Emitted bare, the
        // reference resolves lexically to that enclosing anonymous class's member — which is exactly
        // what Java resolved it to. Qualifying it with the name Spoon reports (`Pixmap$1`) would
        // name a type that does not exist in the emitted code.
        val anonymous = here match { case c: CtClass[?] => c.isAnonymous; case _ => false }
        Option.when(found && !anonymous) {
          val id = minter.external(q, simpleName(q))
          Tree.This(id, TypeRef(NoPrefix, id), originOf(ta))
        }

      /** entry: a method/ctor block → a TIR `Block` (statements, Unit result). */
      def methodBody(b: CtBlock[?]): Term = blockOf(b.getStatements.asScala.toList, b)

      /** a statement list and the element it came from → a TIR `Block`, with whatever comments
        * were written after the last statement kept in the block's `trailing` slot.
        *
        * The ONE place a `Tree.Block` is built out of `stmts`: the leftover was previously dropped
        * at each of the three call sites independently, which is exactly the shape that makes a
        * fix land in two of them. */
      private def blockOf(ss: List[CtStatement], el: CtElement): Tree.Block =
        val (sts, trail) = stmts(ss)
        Tree.Block(sts, unit(el), unitT, originOf(el), trail)

      // ---- statement trivia ---------------------------------------------------
      //
      // Three things happen per statement, and the ORDER is the whole design:
      //   1. `leadingOf(s)` claims what Spoon attached to the statement itself;
      //   2. `stmt(s)` translates it — and every nested statement claims its own comments there;
      //   3. `deepComments(s)` scoops whatever is LEFT in the subtree — expression-level comments
      //      the TIR has no node for, which therefore hoist to this statement.
      // Run (3) before (2) and a comment inside an `if`'s then-branch lands above the `if`.
      //
      // A comment with nothing after it inside a block (a trailing `// TODO`) is attached by Spoon
      // as a STATEMENT of its own, so it is carried as `pending` onto the next statement — and
      // where there is none it used to be DISCARDED. It had already been CLAIMED by then, so no
      // coarser harvest could recover it either: claim-then-drop, and the single largest category
      // of comment this port lost. `Tree.Block.trailing` is where it goes now, which places it
      // exactly where java wrote it and needs no fallback (see that field's doc).

      /** Translate a statement list, folding comment-statements into the statement that follows.
        *
        * The second half of the pair is what is LEFT when the list ends on comments — the block's
        * `trailing`. Returned rather than attached here, because a statement list is not always a
        * block (a `case` arm's is one, a single-statement body's is not) and the caller is the one
        * that knows which node carries it. */
      private def stmts(ss: List[CtStatement]): (List[Statement], List[Trivia]) =
        val out     = List.newBuilder[Statement]
        var pending = List.empty[Trivia]
        ss.foreach {
          case c: CtComment => claimed.add(c); pending = pending :+ triviaOf(c)
          case s =>
            out += withTrivia(pending, s)
            pending = Nil
        }
        (out.result(), pending)

      /** one statement, with `pending` plus its own plus its subtree's leftovers attached. */
      private def withTrivia(pending: List[Trivia], s: CtStatement): Statement =
        val own = leadingOf(s)
        val k   = stmt(s)
        val all = pending ++ own ++ deepComments(s)
        if all.isEmpty then k
        else
          k match
            // a local variable declaration has a `leading` field of its own — no wrapper needed,
            // and none wanted: `Tree.Commented` is a TERM and a `ValDef` is not. Same for the two
            // other DECLARATIONS a java block can hold: a local class and (through a lowering) a
            // local `def`, both of which have the field and neither of which is a `Term`, so the
            // wrapper cannot reach them and `case other => other` was dropping the comment.
            case v: Tree.ValDef   => v.copy(leading = all ++ v.leading)
            case c: Tree.ClassDef => c.copy(leading = all ++ c.leading)
            case d: Tree.DefDef   => d.copy(leading = all ++ d.leading)
            case t: Term          => TirTrace.mint(Tree.Commented(all, t))
            case other            => other

      def exprOf(e: CtExpression[?]): Term = expr(e)
      /** translate an initializer, coercing it to `target` (null → type param, narrowing, etc.). */
      def coercedExprOf(target: CtTypeReference[?], e: CtExpression[?]): Term = coerce(target, e, expr(e))

      private def unit(el: CtElement): Term = Tree.Literal(Constant.UnitC, unitT, originOf(el))

      private def blockTerm(s: CtStatement): Term = s match
        case null          => Tree.Block(Nil, Tree.Literal(Constant.UnitC, unitT, Origin.synthetic), unitT, Origin.synthetic)
        case b: CtBlock[?] => blockOf(b.getStatements.asScala.toList, b)
        case single        => Tree.Block(List(withTrivia(Nil, single)), unit(single), unitT, originOf(single))

      // ---- statements ----

      /** One statement, with a java LABEL on a non-loop statement turned into [[Tree.Labeled]].
        *
        * Java's `LabeledStatement` takes any statement, and `break L` leaves THAT statement — so a
        * label on an `if`, a bare block or a `switch` is a control-flow construct of its own and
        * needs a node. A LOOP's label is not wrapped: `While`/`For`/`ForEach`/`DoWhile` read it
        * with `labelOf` into their own field, because it is also the target of `continue L`, whose
        * boundary goes around the loop BODY rather than around the loop.
        *
        * `Tree.Labeled` therefore appears only where the label has nowhere else to live, and the
        * two encodings can never both claim one label. */
      private def stmt(s: CtStatement): Statement =
        val k = stmtKind(s)
        labelOf(s) match
          // a labelled loop already carries its label; a `ValDef` cannot be labelled at all (JLS
          // 14.7 — a local declaration is a BlockStatement, not a Statement), so anything else
          // that is a term gets the wrapper and anything that is not is left exactly as it was.
          case Some(l) if !carriesOwnLabel(k) =>
            k match
              case t: Term => TirTrace.mint(Tree.Labeled(l, t, unitT, originOf(s)))
              case other   => other
          case _ => k

      /** does this translated statement already hold its java label in a field of its own? */
      private def carriesOwnLabel(k: Statement): Boolean = k match
        case _: Tree.While | _: Tree.For | _: Tree.ForEach | _: Tree.DoWhile => true
        case _                                                               => false

      /** JS-E03/E04's PREDICATE, as one function: the target type when java's implicit narrowing
        * applies to a compound assignment here, `scala.None` when it does not.
        *
        * One function and not two copies, because the two positions this is consulted from are
        * exactly the pair the catalog splits into two rows — and a predicate copied into both is a
        * predicate that will be fixed in one. Java's binary numeric promotion lifts
        * `byte`/`short`/`char` operands to at least `int`, so the op result may be wider than the
        * target (`byte += byte` computes an `int`); narrow back whenever
        * `max(rhsRank, intRank) > targetRank`. */
      private def compoundNarrow(a: CtOperatorAssignment[?, ?]): Option[CtTypeReference[?]] =
        val lt = a.getAssigned.getType
        val rt = try a.getAssignment.getType catch { case _: Throwable => null }
        val narrow = lt != null && lt.isPrimitive && rt != null && rt.isPrimitive &&
          primRank.get(lt.getSimpleName).exists(l =>
            primRank.get(rt.getSimpleName).exists(r => math.max(r, primRank("int")) > l))
        if narrow then Some(lt) else scala.None

      /** THE STATEMENT DISPATCH — and the obligation wrapper sits HERE, not in an arm.
        *
        * `Lowering.of` maps the node's runtime class to its registry name ONCE and enters the
        * obligation scope before any `case` is tried, so an arm is incapable of escaping its
        * obligations because it never had the choice. Written inside each `case`, the failure mode
        * would be an arm that declines to wrap — which is the same shape as the defect the
        * mechanism exists to catch (`DESIGN.md` §2.8). Cost is one `Map` lookup per statement, and
        * `Nil` for every kind nothing attaches to. */
      private def stmtKind(s: CtStatement): Statement =
        // `s` is the SUBJECT — the node itself, so a delegation into the expression dispatch
        // (`case inv: CtInvocation => expr(inv)`) can be joined to this scope by identity.
        Lowering.of(SpoonKinds.nameOf(s.getClass), Dispatch.Statement, originOf(s), s)(stmtArm(s))

      private def stmtArm(s: CtStatement)(using Obligations): Statement = s match
        case v: CtLocalVariable[?] =>
          val vt = tpe(v.getType)
          val id = defineLocal(v, vt) // sets isMutable when the local is reassigned
          // the SLOT rows — JS-G09/G13/G14. A local with no initialiser has no slot, the list is
          // empty and all three answer "does not apply", which is the honest discharge (see
          // `slotConsults` on why the consult may not live inside `coerce`).
          slotConsults(Option(v.getDefaultExpression).map(v.getType -> _).toList, originOf(v))
          val rhs = Option(v.getDefaultExpression).map(e => coerce(v.getType, e, expr(e)))
          Tree.ValDef(id, tt(vt, v), rhs, originOf(v))
        case a: CtOperatorAssignment[?, ?] =>
          // Java compound assignment narrows implicitly: `int += float` means `= (int)(i + f)`.
          val lhs = expr(a.getAssigned)
          val res = opText(a.getKind).fold(unknownOp(a.getKind, a, ty(a)))(
            op => binApply(op, lhs, expr(a.getAssignment), ty(a)))
          // JS-E03, CONSULTED rather than merely done: the catalog attaches it to this dispatch, so
          // the wrapper reports an arm that returns without asking. `compoundNarrow` is the whole
          // predicate — `Some(target)` when java's implicit narrowing applies here — which is what
          // makes the consult a decision the coverage lane can count rather than a formality.
          val out = Obligations.consult(JS.E(3), originOf(a))(compoundNarrow(a))
            .fold(res)(t => Tree.Typed(res, tt(tpe(t), a), tpe(t), originOf(a)))
          Tree.Assign(lhs, out, unitT, originOf(a))
        case a: CtAssignment[?, ?] =>
          val tgt = Option(a.getAssigned.getType)
          val rhs = a.getAssignment
          val lhs = expr(a.getAssigned)
          slotConsults(tgt.map(_ -> rhs).toList, originOf(a))
          val v   = tgt.map(coerce(_, rhs, expr(rhs))).getOrElse(expr(rhs))
          Tree.Assign(lhs, toDeclaredTypeParam(a.getAssigned, rhs, v), unitT, originOf(a))
        case i: CtIf =>
          val elze = Option(i.getElseStatement).map(blockTerm).getOrElse(unit(i))
          Tree.If(expr(i.getCondition), blockTerm(i.getThenStatement), elze, unitT, originOf(i))
        case r: CtReturn[?] =>
          // coerce the returned value to the method's declared return type (null → type param, etc.).
          val target = Option(r.getParent(classOf[CtMethod[?]])).flatMap(m => Option(m.getType))
          slotConsults(target.zip(Option(r.getReturnedExpression)).toList, originOf(r))
          val ret = Option(r.getReturnedExpression).map(e => target.map(tp => coerce(tp, e, expr(e))).getOrElse(expr(e)))
          Tree.Return(ret, nothingT, originOf(r))
        case w: CtWhile =>
          Tree.While(expr(w.getLoopingExpression), blockTerm(w.getBody), unitT, originOf(w), labelOf(w))
        case t: CtThrow =>
          Tree.Throw(expr(t.getThrownExpression), nothingT, originOf(t))
        case b: CtBlock[?]      => blockTerm(b)
        case inv: CtInvocation[?] => expr(inv)
        case cc: CtConstructorCall[?] => ctorCall(cc)
        case f: CtForEach =>
          val v  = f.getVariable
          val vt = tpe(v.getType)
          val id = defineLocal(v, vt)
          Tree.ForEach(Tree.ValDef(id, tt(vt, v), None, originOf(v)), iterableOperand(f.getExpression), blockTerm(f.getBody), unitT, originOf(f), labelOf(f))
        case f: CtFor =>
          val init = f.getForInit.asScala.toList.map(stmt)
          val cond = Option(f.getExpression).map(expr)
          val upd  = f.getForUpdate.asScala.toList.map(stmt)
          Tree.For(init, cond, upd, blockTerm(f.getBody), unitT, originOf(f), labelOf(f))
        case t: CtTryWithResource =>
          // A `collect` here is a SILENT DROP for every shape it does not name, and the SE9 form
          // (`try (existingEffectivelyFinalLocal) { … }`, JLS 14.20.3) is one: Spoon models that
          // resource as a variable REFERENCE, not a `CtLocalVariable`, so it fell out of the list
          // and the emitter closed one resource fewer than java does — with no error, no count and
          // nothing in the tree to say a resource had been there. Refused LOUDLY instead (M6): the
          // faithful translation is a fresh alias binding, and minting a local symbol for it is a
          // change worth making the day a corpus library writes one. None does today.
          val res = t.getResources.asScala.toList.map {
            case lv: CtLocalVariable[?] =>
              val rt = tpe(lv.getType)
              Tree.ValDef(defineLocal(lv, rt), tt(rt, lv), Option(lv.getDefaultExpression).map(expr), originOf(lv))
            case other =>
              unsupported(other, "a try-with-resources resource that is not a local DECLARATION " +
                "(JLS 14.20.3's SE9 form, an existing effectively-final variable): it needs a fresh " +
                "alias binding to close, and dropping it closes one resource fewer than java does")
          }
          tryStmt(t, res)
        case t: CtTry             => tryStmt(t, Nil)
        case s: CtSwitch[?]       => switchStmt(s)
        case b: CtBreak           => Tree.Break(Option(b.getTargetLabel), nothingT, originOf(b))
        // `yield v` — JLS 14.21, and only ever a NON-TAIL one by the time it is reached from here:
        // a switch-expression arm's LAST statement is peeled into the arm's value by `armValue`,
        // and an arrow-form STATEMENT arm's Spoon-synthesised wrapper is undone by `caseBody`. What
        // is left is a `yield` that leaves the arm from inside an `if` or a nested block, which
        // scala can only express as a value-carrying `boundary` the emitter puts around the ARM.
        case y: CtYieldStatement  => Tree.Yield(expr(y.getExpression), nothingT, originOf(y))
        case c: CtContinue        => Tree.Continue(Option(c.getTargetLabel), nothingT, originOf(c))
        case a: CtAssert[?]       => Tree.Assert(expr(a.getAssertExpression), Option(a.getExpression).map(expr), unitT, originOf(a))
        case d: CtDo              =>
          // JS-S18, the FRONTEND half — Scala 3 removed `do`-`while`, so there is no keyword to map
          // to and the loop needs a node of its own for the emitter to give it a shape. Always
          // fires: every java `do` needs the image. The row attaches at BOTH surfaces, and this
          // consult is why — the emitter's alone would claim coverage for a decision taken here.
          Obligations.consult(JS.S(18), originOf(d))(Some(()))
          Tree.DoWhile(blockTerm(d.getBody), expr(d.getLoopingExpression), unitT, originOf(d), labelOf(d))
        case y: CtSynchronized    =>
          // JS-S22 — java's `synchronized` STATEMENT has a scala image with the same monitor
          // bytecode (`.synchronized`), and choosing it is the whole content of this row. Always
          // fires: every `synchronized` block needs the mapping.
          Obligations.consult(JS.S(22), originOf(y))(Some(()))
          Tree.Synchronized(expr(y.getExpression), blockTerm(y.getBlock), unitT, originOf(y))
        case u: CtUnaryOperator[?] =>
          import UnaryOperatorKind.*
          val one = Tree.Literal(Constant.IntC(1), ty(u), originOf(u))
          u.getKind match
            case POSTINC | PREINC => val t = expr(u.getOperand); Tree.Assign(t, incNarrow(u.getOperand, binApply("+", t, one, ty(u))), unitT, originOf(u))
            case POSTDEC | PREDEC => val t = expr(u.getOperand); Tree.Assign(t, incNarrow(u.getOperand, binApply("-", t, one, ty(u))), unitT, originOf(u))
            case _                => expr(u)
        // A free-floating comment arriving as a STATEMENT. `stmts` folds these into the statement
        // that follows, so one reaching here is a body that is ONLY a comment (`if (x) /* no-op */;`)
        // — Java's empty statement. NOT claimed: leaving it unclaimed lets the enclosing
        // statement's `deepComments` pick the text up, which is the only place left to put it.
        case c: CtComment => Tree.Literal(Constant.UnitC, unitT, originOf(c))
        // A METHOD-LOCAL NAMED CLASS — JLS 14.3, catalog JS-C30. `Tree.ClassDef` is a `Statement`,
        // so the node the TIR needs already existed; what was missing was the arm. Two things this
        // arm decides that the DECLARATION path does not:
        //
        //   - the OWNER is the enclosing EXECUTABLE, not the enclosing type. Spoon reports a
        //     declaring TYPE for a local class (it is nested in the binary name), and taking that
        //     would make every "is this a member of `Outer`?" question answer yes: the emitter
        //     would render `Outer#Local`, a type projection naming a member that does not exist.
        //     Owning it by the method is also the structurally true statement — §4.56's ownership
        //     chain still reaches the unit through the method, so the symbol stays OWNED;
        //   - the NAME is java's SOURCE name. Spoon's qualified name carries the binary
        //     disambiguator (`p.Outer$1Local`), which is the right INTERNING key — the `new Local()`
        //     reference resolves through it — and is not a legal Scala identifier.
        //
        // Captures need no lowering, exactly as for an anonymous class: javac synthesises
        // constructor parameters for them and Scala closes over them directly.
        case c: CtClass[?] =>
          // JS-C30, consulted rather than merely done: the catalog attaches the row to THIS
          // dispatch, so the wrapper reports an arm that returns without asking. It fires at every
          // local class, which is the whole population the row is about — a `CtClass` reaching the
          // STATEMENT dispatch is a local class by construction, since every other one is walked
          // from its declaring type.
          Obligations.consult(JS.C(30), originOf(c))(Some(()))
          classDef(c, owner = Some(methodId), sourceName = Some(localName(c)),
                   selfClass = classId, outerVars = varScope)
        // NO ARM EXISTS for this Java statement kind. A MARKER, not a throw: the failure is the
        // size of the construct rather than the size of the file, and the gate still refuses to
        // ship the port (§6.4). `unitT` because a statement produces no value.
        case other => unlowered(other, s"statement ${SpoonKinds.nameOf(other.getClass)}", unitT)

      /** THE ENHANCED-FOR'S ITERABLE, at the type JAVA READ IT AT — `ENGINE-LIMITS.md` G31.
        *
        * JLS 14.14.2 does not iterate the expression's own type: it looks `Iterable<T>` up among that
        * type's supertypes and iterates at `T`. Scala's `for` is a `foreach` CALL on the expression
        * as written, the java-shaped iterable's `foreach` is an EXTENSION, and applying an extension
        * to a WILDCARD application means CAPTURE CONVERSION. Dotty performs that by substituting
        * `Any` for the parameter, which is exact for an ordinary bound and cannot work for an
        * F-BOUNDED one: the capture's upper bound comes out `Seq[Any]` while its own slot asks for
        * `Seq[CAP]`, so the application is rejected at an INFERRED type (`E057`). No spelling of the
        * wildcard repairs it — java's own `Seq<? extends Seq<?>>` fails identically, measured at
        * scalac 3.8.4 — because the F-bound has no finite unrolling.
        *
        * So exactly there, and nowhere else, the operand is put at the supertype java read. The
        * guard is the F-BOUND and not the wildcard: an ordinary bounded wildcard capture-converts
        * unaided, and ascribing those would be a correct-but-unnecessary rewrite on every port that
        * has one (§5's widening rule). Declines where the found `Iterable` argument mentions a type
        * VARIABLE — that element has no text this scope can write, and inventing one is §4.6's
        * fabricated fact. */
      private def iterableOperand(e: CtExpression[?]): Term =
        val t  = expr(e)
        val et = try Option(e.getType) catch { case _: Throwable => scala.None }
        et.filter(fboundWildcardUse).flatMap(javaIterableSuper) match
          case Some(iter) => val ty = tpe(iter); Tree.Typed(t, tt(ty, e), ty, originOf(e))
          case scala.None => t

      /** is this an application with a WILDCARD at a SELF-REFERENTIALLY bounded slot — the one shape
        * scala's capture conversion cannot answer? Read off the DECLARATION's own bounds, and the
        * unreadable answer is `false`, which is the pre-rule emission: the failure path leaves the
        * port exactly where it was rather than interposing a view on evidence nobody has (§4.6). */
      private def fboundWildcardUse(r: CtTypeReference[?]): Boolean = TypeShape.of(r) match
        case TypeShape.Named(_, as) if as.nonEmpty =>
          val formals = try Option(r.getTypeDeclaration).map(_.getFormalCtTypeParameters.asScala.toList).getOrElse(Nil)
                        catch { case _: Throwable => Nil }
          formals.zip(as).exists { (f, a) =>
            TypeShape.of(a).isInstanceOf[TypeShape.Wildcard] &&
              (try Option(f.getSuperclass).exists(b => mentionedTypeVarNames(b)(f.getSimpleName))
               catch { case _: Throwable => false })
          }
        case _ => false

      /** `java.lang.Iterable<E>` as reached from `r`'s supertypes, and only where `E` is a type this
        * scope can WRITE — java's own enhanced-for lookup, with §4.6's honest decline. */
      private def javaIterableSuper(r: CtTypeReference[?]): Option[CtTypeReference[?]] =
        def walk(ref: CtTypeReference[?], fuel: Int): Option[CtTypeReference[?]] =
          if ref == null || fuel <= 0 then scala.None
          else if ref.getQualifiedName == "java.lang.Iterable" then Some(ref)
          else
            val d = try ref.getTypeDeclaration catch { case _: Throwable => null }
            if d == null then scala.None
            else
              val ups = (d match { case c: CtClass[?] => Option(c.getSuperclass).toList; case _ => Nil }) ++
                        (try d.getSuperInterfaces.asScala.toList catch { case _: Throwable => Nil })
              ups.iterator.map(walk(_, fuel - 1)).collectFirst { case Some(x) => x }
        walk(r, 6).filter(i => TypeShape.of(i).args match
          case List(el) => mentionedTypeVarNames(el).isEmpty
          case _        => false)

      private def defineLocal(v: CtVariable[?], vt: TypeRepr): SymId =
        val key = "@" + methodId.raw + "$L$" + v.getSimpleName + "#" + posKey(v)
        val mut = v.isInstanceOf[CtLocalVariable[?]] && isReassigned(v)
        val id  = minter.define(key)(sid => Symbol(sid, v.getSimpleName, v.getSimpleName, Flags(isMutable = mut), methodId, vt))
        registerVar(v, id)
        id

      /** does the enclosing method body write to `v` after its declaration? (then it's a `var`). */
      private def isReassigned(v: CtVariable[?]): Boolean =
        val scope = v.getParent(classOf[CtExecutable[?]])
        scope != null && writesToVar(scope, v.getSimpleName)

      private def writesToVar(scope: CtElement, name: String): Boolean =
        val assigns = scope.getElements(new spoon.reflect.visitor.filter.TypeFilter(classOf[CtAssignment[?, ?]])).asScala
        val unaries = scope.getElements(new spoon.reflect.visitor.filter.TypeFilter(classOf[CtUnaryOperator[?]])).asScala
        assigns.exists { a =>
          a.getAssigned match { case w: CtVariableWrite[?] => w.getVariable.getSimpleName == name; case _ => false }
        } || unaries.exists { u =>
          import UnaryOperatorKind.*
          Set(POSTINC, POSTDEC, PREINC, PREDEC).contains(u.getKind) &&
            (u.getOperand match { case va: CtVariableAccess[?] => va.getVariable.getSimpleName == name; case _ => false })
        }

      /** Assignment to a member whose DECLARED (emitted) type is a bare TYPE PARAMETER, where Java's
        * own view of the access was the ERASED one. Reading a member through a RAW-bounded type
        * variable (`N extends Node`, `N node; node.parent`) erases it to the bound, so Java accepts
        * `node.parent = null` and `node.parent = this` unchecked — while the emitted field keeps its
        * `N`. Restate the unchecked step. Guarded on the parameter's NAME resolving in the current
        * scope (never emit the `?T` unresolved stub) and on the value not already having that type. */
      private def toDeclaredTypeParam(assigned: CtExpression[?], e: CtExpression[?], t: Term): Term =
        declaredTypeOf(assigned) match
          case Some(tp: CtTypeParameterReference) => toTypeParam(tp, e, t)
          case _                                  => t

      /** the DECLARED type of an assignment target — the field's / local's own declaration, not
        * Spoon's (possibly raw-erased) view of the access. */
      private def declaredTypeOf(assigned: CtExpression[?]): Option[CtTypeReference[?]] =
        try assigned match
          case fw: CtFieldWrite[?]    => Option(fw.getVariable.getFieldDeclaration).map(_.getType)
          case vw: CtVariableWrite[?] => Option(vw.getVariable.getDeclaration).map(_.getType)
          case _                      => None
        catch { case _: Throwable => None }

      /** cast `t` to the in-scope resolution of type parameter `tp`, unless it already has it. */
      private def toTypeParam(tp: CtTypeParameterReference, e: CtExpression[?], t: Term): Term =
        resolveTypeParam(tp.getSimpleName) match
          case Some(inScope) if t.tpe != TypeRef(NoPrefix, inScope) =>
            val tr = TypeRef(NoPrefix, inScope)
            Tree.Typed(t, tt(tr, e), tr, originOf(e))
          case _ => t

      /** Java permits two implicit conversions Scala forbids: array covariance (`Sub[]` → a
        * `Super[]` slot) and `null` → a type parameter. Insert an explicit `asInstanceOf` so the
        * ported assignment/initializer type-checks. */
      private val primRank = Map("byte" -> 1, "short" -> 2, "char" -> 2, "int" -> 3, "long" -> 4, "float" -> 5, "double" -> 6)

      /** Java's UNCHECKED generic conversion. A value whose static type involves a RAW use of a
        * generic type converts to any instantiation of it without a check (`Class` → `Class<T>`);
        * and because we render raw uses CONTEXT-dependently (wildcards, or name-directed fill from
        * the in-scope type parameters), even the *same* Java type written in two scopes can render
        * differently (`ObjectMap<String, AssetLoader>` → `[String, AssetLoader[?, ?]]` as a field,
        * `[String, AssetLoader[T, P]]` inside `setLoader<T, P>`). Emit exactly the cast Java
        * performs implicitly. Gated to a GENERIC target whose type variables all resolve here, so
        * we never synthesize a `?T` stub; declarations keep their own types untouched. */
      /** JS-G31 — a POLY EXPRESSION (JLS 15.2): a LAMBDA or a METHOD REFERENCE.
        *
        * Neither has a type of its own. Java gives it the type of the slot it fills, and so does
        * Scala — a function literal SAM-converts when the EXPECTED type is the interface. So a cast
        * at such an argument is not a conversion java performed and we are writing down; there was
        * no conversion. Written as a cast the literal is elaborated FIRST, to a `scala.FunctionN`,
        * and the cast then asserts that a `Function0` is a `Supplier`, which it is not:
        *
        * {{{
        * Optional.ofNullable(location).orElseGet(() -> Paths.get(".").toAbsolutePath())   // java
        * // ClassCastException: TemplateParser$$Lambda cannot be cast to java.util.function.Supplier
        * }}}
        *
        * PROBED against scala 3.8.4 before this was written, because "does Scala SAM-convert here"
        * is not a question to answer from first principles: it converts at a WILDCARD-applied slot
        * (`Supplier[? <: Path]`), at a contravariant one (`Comparator[? super T]`), at both
        * directions in one formal (`Function[? super K, ? <: V]`) and at a bare `Supplier[?]` — and
        * it refuses only where java refuses too (a GENERIC function type, which JLS 15.27.3 forbids
        * a lambda at) or at an INTERSECTION target, which the frontend has no model for. So the
        * faithful emission is the literal AT THE SLOT and nothing else — never a cast, and never an
        * anonymous class synthesised where the language already does the work.
        *
        * ONE function, because this rule was written twice and the two copies disagreed
        * (`ENGINE-LIMITS.md` F8's shape): `uncheckedGeneric` had the method-reference case,
        * `appliedCtorArgs` did not, and the third arm — `knownReceiverArgs` — had no list at all,
        * which is where all 27 of liqp's failures came from. */
      private def polyExpression(e: CtExpression[?]): Boolean =
        e.isInstanceOf[CtLambda[?]] || e.isInstanceOf[CtExecutableReferenceExpression[?, ?]]

      /** JS-G31's answer AT THE CALL — every POLY-EXPRESSION argument restored to what `expr`
        * produced for it, with any cast an argument arm wrapped it in removed.
        *
        * Answered here rather than in each arm, and that is the point: the arms are six and
        * growing, each with its own reason for casting, and a rule stated once per arm is a rule
        * that will be missing from the seventh. `expr` folds the java-written casts on an
        * expression innermost-first, one `Tree.Typed` per `getTypeCasts` entry, so those are
        * exactly the innermost `own` layers and everything outside them was added by an arm — which
        * makes "keep what java wrote, drop what we added" decidable rather than a guess.
        *
        * `Some` only where the call really has a poly argument, so `fired` counts the sites where
        * the difference APPLIES and `consulted` counts the calls that asked.
        *
        * ==the ARITY is answered per INDEX, never by declining the call==
        * `args.sizeIs != argEs.size => None` reads to the catalog as *the difference does not apply
        * at this call*, and that is a vacuous guard: java's own vararg materialisation collapses N
        * trailing arguments into ONE array term (`varargPack`), so every vararg call with two or
        * more variadic arguments declined — including for a poly expression in the FIXED prefix,
        * which lines up position by position. The prefix is paired by index and the packed tail is
        * answered INSIDE the array against the arguments it was built from. Where no
        * correspondence can be established at all the answer is still `None`, and it now means what
        * it says: not "the arity differs" but "nothing here pairs with a source expression". */
      private def polyArgsUncast(argEs: List[CtExpression[?]], args: List[Term], at: Origin)
                                (using Obligations): List[Term] =
        Obligations.consult(JS.G(31), at) {
          val poly = argEs.zipWithIndex.collect { case (e, i) if polyExpression(e) => i }.toSet
          if poly.isEmpty then scala.None
          else if args.sizeIs == argEs.size then
            Some(args.zipWithIndex.map { (t, i) => if poly(i) then uncastAdded(t, argEs(i)) else t })
          else packedUncast(argEs, args, poly)
        }.getOrElse(args)

      /** …and the OTHER half of the same sentence: a poly expression takes its type from the SLOT,
        * and where the slot is an OVERLOAD SET scala cannot use it to type the literal at all.
        *
        * [[polyArgsUncast]] removes the type an argument arm gave a lambda; this puts back the ONE
        * type JAVA ITSELF resolved, at the single shape where leaving the literal bare is not the
        * faithful emission. A class declaring `tagLine(CharSequence, boolean)` beside
        * `tagLine(CharSequence, Runnable)` is resolved by javac from the argument's SHAPE; scalac
        * types a function literal BEFORE it can use an expected type, so all three alternatives fail
        * at once and the error names none of them as the intended one:
        *
        * {{{
        * fa.tagLine("li", () => fa.text("x"))                          // E134 — none match
        * fa.tagLine("li", (() => fa.text("x")): java.lang.Runnable)    // java's own resolution
        * }}}
        *
        * PROBED against scala 3.8.4 before this was written, with the NEGATIVE in the same
        * statement: an unoverloaded `tagIndent(CharSequence, Runnable)` takes the bare literal
        * exactly as [[polyExpression]] says it does, so ascribing there would be emitted text for
        * nothing.
        *
        * ==an ASCRIPTION, never a CAST — which is why [[polyExpression]]'s refusal still stands==
        * That refusal is about `asInstanceOf`: written as a cast the literal elaborates to a
        * `Function0` FIRST and the cast then asserts that a `Function0` is a `Runnable`, which
        * throws. `TirEmitter.polyOperand` is the arm that renders a `Tree.Typed` over a poly term as
        * `(e: T)` rather than as a cast, and it exists for precisely this node — so what is minted
        * here is scala's own SAM conversion at an expected type, and not a conversion java performed
        * and we are writing down.
        *
        * ==three conjuncts, each ruling out emitted text for nothing (`CLAUDE.md` §5)==
        *   - '''the argument is a LAMBDA.''' A METHOD REFERENCE is a poly expression too and is
        *     deliberately excluded. `TirEmitter.samAscribed` already answers this same question for
        *     the two reference forms it renders as a function literal; the third — a STATIC
        *     reference — renders as a bare qualified NAME, where an ascription APPLIES a nilary
        *     method (`(r.run: Runnable)` is `Found: Unit`, measured on the same probe). Two
        *     mechanisms for one question is F8's finding; the lambda is the node that has no other
        *     answer, and it is the only one this adds;
        *   - '''the callee is OVERLOADED AT THIS ARITY, and the SLOT names no expected type'''
        *     ([[overloadedSamSlot]]) — the alternatives either DISAGREE where the lambda stands, or
        *     agree on a TYPE VARIABLE the call has yet to infer. With one alternative scala already
        *     has the expected type; with two that agree on a CONCRETE interface the lambda
        *     discriminates nothing and the ascription would change no resolution;
        *   - '''the target is NAMEABLE HERE''' ([[tpNameableHere]]) '''and java wrote no cast of its
        *     own.''' `uncastAdded` keeps the casts the SOURCE wrote, so a term that is already a
        *     `Tree.Typed` when this runs is java's own and is left alone.
        *
        * The target is the LAMBDA'S OWN type — the functional interface javac resolved, and the same
        * reference [[samResultTpt]] reads its SAM out of. Not the formal re-derived from the callee,
        * which would be a second spelling of one fact (F8) and would have to answer for a formal
        * expressed in the callee's own variables, which this position cannot name (G12). */
      private def polyArgsAscribed(ex: CtExecutableReference[?], argEs: List[CtExpression[?]],
                                   args: List[Term]): List[Term] =
        if args.sizeIs != argEs.size then args
        else args.zipWithIndex.map { (t, i) =>
          samLambdaOf(argEs(i)) match
            case Some(l) if !t.isInstanceOf[Tree.Typed] && overloadedSamSlot(ex, argEs.size, i) =>
              val lt = l.getType
              if lt == null || !tpNameableHere(lt) then t
              else
                val r = tpe(lt)
                if r == NoType then t else Tree.Typed(t, tt(r, l), r, originOf(argEs(i)))
            case _ => t
        }

      /** the LAMBDA whose own type is the target this argument has to be ascribed to — the argument
        * itself, or a BRANCH of a poly CONDITIONAL.
        *
        * JLS 15.25 makes `c ? lambda : lambda` a poly expression in its own right: java pushes the
        * target type THROUGH the conditional and types each branch against it, which is exactly
        * what a scala ascription on the whole `if` does. Read off the argument's shape rather than
        * off the emitted term, because that is where java's own rule is stated — and it is the same
        * sentence `coerce` already answers one artifact over (a conditional's conversion belongs to
        * its BRANCHES, `ENGINE-LIMITS.md` K30 face 3), met here at a poly operand instead of a
        * collection.
        *
        * The target comes from a branch and the ascription goes on the WHOLE conditional: java
        * required both branches to be compatible with one target, so one branch names it for both,
        * and ascribing the branches separately would write the same type twice for a conditional
        * whose own type is then still inferred.
        *
        * `polyExpression` is deliberately NOT widened to match. That predicate is JS-G31's
        * population — what [[polyArgsUncast]] strips a cast off — and a conditional is not a term
        * an argument arm wrapped; widening it would move a catalog count for a different question. */
      private def samLambdaOf(e: CtExpression[?]): Option[CtLambda[?]] = e match
        case l: CtLambda[?]      => Some(l)
        case c: CtConditional[?] =>
          List(c.getThenExpression, c.getElseExpression).collectFirst { case l: CtLambda[?] => l }
        case _                   => scala.None

      /** is the callee overloaded at this arity, AND does the slot at argument `i` fail to give
        * scala an expected type for the literal? — the whole of [[polyArgsAscribed]]'s decision,
        * read off the declaring type's own members and never off a name.
        *
        * ==the slot fails in TWO ways, and this asked only about the first==
        * The original reading was *do the alternatives DISAGREE at `i`* — `boolean` against
        * `Runnable`, where no single expected type exists — with the negative beside it: two
        * alternatives that both take a `Runnable` there give the lambda the same expected type
        * whichever wins, so ascribing would be emitted text for nothing. That negative is real and
        * stands. What it does not cover is a slot that is a TYPE VARIABLE THIS CALL HAS YET TO
        * INFER, where the alternatives agree perfectly and still name no type:
        *
        * {{{
        * <T> MutableDataHolder set(DataKey<T> key, T value);           // both read `T` at index 1
        * <T> MutableDataHolder set(NullableDataKey<T> key, T value);   // they disagree at index 0
        * }}}
        *
        * Java solves `T` from the KEY and then types the lambda against it; scala must resolve the
        * overload before it can use either slot, and it resolves by typing the arguments — so a
        * literal with written parameter types elaborates to a `scala.Function1` first and matches
        * no alternative. Measured as `Found: CharSequence => Pair[Integer, Integer]`, at a call the
        * index-local test declined because both formals spell `T`.
        *
        * So the second disjunct is the SLOT'S OWN SHAPE rather than a comparison: a formal that is
        * a type variable is one scala cannot read an expected type out of, and with one alternative
        * it would have solved it from the sibling anyway — which is why the overload conjunct stays
        * and this is a disjunct under it rather than a rule of its own.
        *
        * The alternatives are the ones SCALA will see, so they are the type's ALL methods rather
        * than its declared ones: java's overload set spans the hierarchy and so does scala's, and a
        * declared-only reading would decline exactly where a base class contributes the second
        * alternative. Compared by the formal's QUALIFIED NAME at that index, because what has to
        * differ is the SLOT — `boolean` against `Runnable` is the pair this began with — and not the
        * instantiation, which a generic alternative would make differ for no reason.
        *
        * The `catch` is §4.6-shaped and its default is distinguishable from a real answer: an
        * unreadable declaration yields NO alternatives, so nothing is ascribed and the emission is
        * byte-for-byte what it was — at worst the loud `E134` this exists to remove, and never a
        * type invented for a slot. `RuntimeException`, so a deep model's `StackOverflowError` is not
        * swallowed by a helper (`uncastAdded`'s own rule, one function over). */
      private def overloadedSamSlot(ex: CtExecutableReference[?], arity: Int, i: Int): Boolean =
        val alts: List[List[CtTypeReference[?]]] =
          try
            Option(ex.getDeclaringType).flatMap(d => Option(d.getTypeDeclaration)).toList.flatMap { ct =>
              val es: List[CtExecutable[?]] =
                if ex.isConstructor then ct match
                  case cl: CtClass[?] => cl.getConstructors.asScala.toList
                  case _              => Nil
                else ct.getAllMethods.asScala.toList.filter(_.getSimpleName == ex.getSimpleName)
              es.map(_.getParameters.asScala.toList.map(_.getType))
            }
          catch { case _: RuntimeException => Nil }
        val here = alts.filter(_.sizeIs == arity)
        val slots = here.flatMap(ps => Option(ps(i)))
        here.sizeIs > 1 &&
          (slots.map(_.getQualifiedName).distinct.sizeIs > 1 ||
           // …and spelled with the wildcard excluded because Spoon's `CtWildcardReference` EXTENDS
           // `CtTypeParameterReference`, so a bare `isInstanceOf` claims every `?` as a variable
           // (`mentionsNamedTypeVar` carries the same note). A java FORMAL cannot be a bare
           // wildcard, so this excludes nothing that exists — it keeps the test from reading as the
           // one that is wrong everywhere else in this file.
           slots.exists(s => s.isInstanceOf[CtTypeParameterReference] &&
                             !s.isInstanceOf[CtWildcardReference]))

      /** [[polyArgsUncast]] where a VARARG PACK has changed the arity: `args` is the fixed prefix
        * plus ONE term holding the variadic elements, built from the `argEs` tail in order. */
      private def packedUncast(argEs: List[CtExpression[?]], args: List[Term],
                               poly: Set[Int]): Option[List[Term]] =
        val fixed = args.size - 1
        if fixed < 0 || argEs.sizeIs <= fixed then scala.None
        else
          val (headEs, restEs) = argEs.splitAt(fixed)
          val headTs = args.take(fixed).zipWithIndex.map { (t, i) => if poly(i) then uncastAdded(t, headEs(i)) else t }
          def elems(es: List[Term]): Option[List[Term]] =
            if es.sizeIs != restEs.size then scala.None
            else Some(es.zipWithIndex.map { (t, k) => if poly(fixed + k) then uncastAdded(t, restEs(k)) else t })
          // the two shapes `varargPack` materialises — a SPREAD at an external callee, an array
          // literal at one the port declares. A third shape is a pack nobody has built, and
          // declining on it is the honest answer rather than a guess about which term is which.
          val packed = args.last match
            case r: Tree.Repeated => elems(r.elems).map(es => r.copy(elems = es))
            case n: Tree.NewArray => n.init.flatMap(elems).map(es => n.copy(init = Some(es)))
            case _                => scala.None
          packed.map(headTs :+ _)

      /** the casts an ARGUMENT ARM added, removed; the ones the JAVA SOURCE wrote, kept.
        *
        * The failure DIRECTION is the whole of the `catch`: an unreadable cast list used to read as
        * "java wrote NONE", which strips java's own conversions along with the arms', silently and
        * at the one node kind where that is a semantic change. Unreadable now DECLINES — a term
        * left exactly as the arms built it is at worst a cast too many, which is the error this
        * function exists to remove and not a conversion it invented. `RuntimeException`, so a
        * `StackOverflowError` from a deep tree is not swallowed by a helper (`CLAUDE.md` §4.58's
        * rule about a `catch` around a harvest, met at an argument list). */
      private def uncastAdded(t: Term, e: CtExpression[?]): Term =
        val own = try Some(e.getTypeCasts.size) catch { case _: RuntimeException => scala.None }
        def depth(x: Term): Int = x match
          case Tree.Typed(inner, _, _, _) => 1 + depth(inner)
          case _                          => 0
        def strip(x: Term, n: Int): Term =
          if n <= 0 then x
          else x match
            case Tree.Typed(inner, _, _, _) => strip(inner, n - 1)
            case other                      => other
        own.fold(t)(n => strip(t, depth(t) - n))

      /** THE FORMAL OF AN INHERITED CALLEE, with the ANCESTOR's type variables replaced by what THIS
        * class instantiated them with — `None` where nothing substitutes.
        *
        * ==The gap this closes, and why `uncheckedGeneric` could not see it==
        * `AstActionHandler<C, N, A, H>` declares `addActionHandler(H handler)`, and
        * `AttributeProviderAdapter extends AstActionHandler<…, AttributeProvidingHandler<Node>>`
        * calls it with its own RAW `AttributeProvidingHandler` parameter. Java admits that by
        * UNCHECKED CONVERSION at a raw type (JLS 5.1.9) and scala has no such rule, so the faithful
        * emission is the cast java performs implicitly — which is exactly what this function's
        * caller is for. It declined at the first gate: the formal is literally `H`, a
        * `CtTypeParameterReference`, and `isGenericUse` answers `false` for one.
        *
        * `ENGINE-LIMITS.md` G12 is why that gate is right in general — *a callee's own type
        * variables do not resolve at the call site*, and a cast naming one renders a `?T` stub. An
        * INHERITED formal is the one case where they DO: the variable belongs to an ancestor, and
        * the `extends` clause says what this class instantiated it as. That is the same fact
        * `ParentSubst` carries in the TIR (`CLAUDE.md` §4.56) and it is EXACT rather than a guess.
        *
        * ==Keyed by DECLARATION, never by name==
        * `(owner FQN, formal name)`, which is `ParentSubst`'s own identity. The name-keyed map beside
        * it ([[instantiationOfParents]]) is the one `inheritedTp` measured at 161/142/141 and is
        * switched off for it — an unrelated ancestor's `T` colliding with the `T` being filled. Two
        * ancestors' `T`s are two different keys here, so a hit is evidence.
        *
        * ==What it deliberately does NOT substitute==
        * A WILDCARD formal (`? extends H`). The substitution would be right and the RENDERING is
        * not — `tpe` has no shape for a bounded wildcard whose bound this function replaced — and
        * the failure direction of declining is a MISSED cast, i.e. the loud compile error this
        * function exists to remove and never a conversion it invented. Complete over the other
        * three shapes (variable, array, applied) for §4.56's reason: a partial type walk is right
        * for the target it was written against and silently answers "nothing here" for the next. */
      /** how many `[]` a type reference carries — the ARITY half of `ENGINE-LIMITS.md` G26's
        * comparison, which is the one thing that decides whether a cast at an inherited formal is a
        * translation or a `ClassCastException`. */
      private def arrayDims(tr: CtTypeReference[?]): Int = tr match
        case a: CtArrayTypeReference[?] => 1 + arrayDims(a.getComponentType)
        case _                          => 0

      private def inheritedFormal(tr: CtTypeReference[?], fuel: Int = 6): Option[TypeRepr] =
        if fuel <= 0 then scala.None
        else TypeShape.of(tr) match
          // the WILDCARD arm now stands above the variable one, which claimed every `?` and
          // declined it anyway (no declaration to look up) — the doc above says the decline is
          // deliberate, and it is the answer either way.
          case TypeShape.Wildcard(_, _, _) => scala.None
          case TypeShape.Variable(tv) =>
            for
              d     <- (try Option(tv.getDeclaration) catch { case _: Throwable => scala.None })
              owner <- (d.getParent match { case ct: CtType[?] => Some(ct.getQualifiedName); case _ => scala.None })
              if ancestorFqns.headOption.getOrElse(Set.empty).contains(owner)
              arg   <- inheritedByDecl.headOption.flatMap(_.get(owner -> tv.getSimpleName))
              r     <- (try Some(tpe(arg)) catch { case _: Throwable => scala.None })
            yield r
          case TypeShape.Arr(_, c) =>
            inheritedFormal(c, fuel - 1).map(e =>
              AppliedType(TypeRef(NoPrefix, minter.external("scala.Array", "Array")), List(e)))
          case TypeShape.Absent | TypeShape.Prim(_) => scala.None
          case s =>
            val as   = s.args
            val subs = as.map(a => inheritedFormal(a, fuel - 1))
            if as.isEmpty || subs.forall(_.isEmpty) then scala.None
            else
              try Some(AppliedType(TypeRef(NoPrefix, typeSym(s.ref)),
                                   as.zip(subs).map((a, x) => x.getOrElse(tpe(a)))))
              catch { case _: Throwable => scala.None }

      private def uncheckedGeneric(target: CtTypeReference[?], e: CtExpression[?], t: Term,
                                   rawTarget: Boolean = true, ownScope: Boolean = true): Term =
        // THE TYPE THE ARGUMENT HAS WHERE IT STANDS — [[castType]], not `e.getType`. Java decides
        // its unchecked conversion (JLS 5.1.9) on the type the value has AT THE SLOT, and a cast is
        // the one thing that moves it: `gallopRight((Comparable) a[i], …)` at a `Comparable<Object>`
        // formal is raw-to-parameterised, which is the whole of what this function is for. Read
        // BEFORE the cast the same argument is an `Object`, which mentions no raw generic at all —
        // so the one shape java writes this conversion for is the one shape that declined.
        val et = castType(e)
        // a CLASS LITERAL is the one expression whose Spoon type lies about raw-ness: `AddAction.class`
        // types as raw `Class`, yet we emit `classOf[AddAction]` — precisely `Class[AddAction]`. Casting
        // it (to `Class[Action]`, the formal's bound) would destroy the very inference it feeds.
        val classLit = e match
          case fr: CtFieldRead[?] => fr.getVariable.getSimpleName == "class"
          case _                  => false
        // A METHOD REFERENCE (`Array::new`) belongs with the lambda: both are poly expressions
        // whose type comes FROM the target, so a cast can only destroy the inference it feeds.
        // Measured: without this, `addPool(Array.class, Array::new)` casts the supplier to
        // `PoolSupplier[Object]` while `Array.class` pins `T = Array[?]`, and the overload
        // resolves against nothing.
        val bad = classLit || polyExpression(e) || e.isInstanceOf[CtLiteral[?]] ||
          e.isInstanceOf[CtNewArray[?]] || e.isInstanceOf[CtConditional[?]]
        // …the INHERITED formal, which the gates below cannot reach: a formal written as an
        // ancestor's own type variable is not a `isGenericUse` at all, and `tpResolvable` answers
        // `false` for it because the variable is not in THIS class's scope. See [[inheritedFormal]]
        // — the `extends` clause resolves it, exactly and only for that case.
        //
        // A DIMENSION MISMATCH DECLINES, and that is not tidiness: at an `H[]...` slot java PACKS a
        // one-dimensional argument into a fresh `H[][]` (`ENGINE-LIMITS.md` G26, javac-verified),
        // and this port forwards it. A cast over that makes the arity defect COMPILE — the emitted
        // `handlers.asInstanceOf[Array[Array[H[Node]]]]` is a `checkcast [[L…` against a value that
        // is `[L…`, i.e. a `ClassCastException` at run time where a loud typer error stood. Measured
        // at 6 sites on ssg-md; §3's rule is that a green compile says nothing, and trading an error
        // for a run-time throw is the one direction this engine may not take. The arity fix is G26's
        // and this stays declined until it ships.
        //
        // Computed ONCE and behind the two cheap tests: [[inheritedFormal]] calls `tpe`, so asking
        // it twice charges the type-lowering obligation consults twice and moves their DENOMINATORS
        // on every port for nothing (measured on libGDX core, whose emitted text is unchanged).
        val inherited =
          if target == null || et == null || bad then scala.None
          else if !mentionsRawGeneric(et) || arrayDims(target) != arrayDims(et) then scala.None
          else inheritedFormal(target)
        if target == null || et == null || bad then t
        else if inherited.isDefined then
          val ct = inherited.get
          Tree.Typed(t, tt(ct, e), ct, originOf(e))
        else if !isGenericUse(target) then t
        else if !(if ownScope then tpResolvable(target) else tpConcrete(target) || calleeBounded(target)) then t
        else if !mentionsRawGeneric(et) && !(rawTarget && mentionsRawGeneric(target)) then t
        else
          // A CALLEE's formal belongs to the callee's declaration, not to the caller. Rendering
          // `removeDuplicates(Array<AssetDescriptor>)`'s parameter from inside an OVERRIDING method
          // let that method's inherited instantiation fill it — `Array[AssetDescriptor[Void]]` —
          // while `removeDuplicates` itself, being no override, declares `Array[AssetDescriptor[?]]`.
          // Two renderings of one signature, which is this engine's most persistent defect shape.
          // …but only for a callee this class DECLARES itself (`removeDuplicates`, a private
          // helper). An INHERITED callee's formals are written in the ancestor's variables and do
          // need the caller's instantiation; clearing the gate for those too measured 3 -> 35.
          // The caller's inherited instantiation can only speak about type variables declared by
          // its OWN ancestors. A formal belonging to any other class — `AssetManager`'s
          // `injectDependencies(Array<AssetDescriptor>)`, called from `AssetLoadingTask` — must be
          // rendered without it. Restricting this to same-class callees only was not enough (3 -> 2
          // instead of 0); clearing it for ALL callees was too much (3 -> 35), because a formal
          // inherited from an ancestor genuinely is written in the ancestor's variables.
          val ownCallee =
            try Option(e.getParent(classOf[CtInvocation[?]]))
                  .flatMap(inv => Option(inv.getExecutable.getDeclaringType))
                  .exists(dt => !ancestorFqns.headOption.getOrElse(Set.empty).contains(dt.getQualifiedName))
            catch { case _: Throwable => false }
          val savedOv = inOverridingMember
          if ownCallee then inOverridingMember = false
          val ct = try if ownScope then tpe(target) else tpBoundErased(target)
                   finally inOverridingMember = savedOv
          Tree.Typed(t, tt(ct, e), ct, originOf(e))

      /** JS-G13's clause, as a function of the SLOT — java's array covariance (JLS 10.10) put a value
        * of one array type where another is declared, and scala's `Array` is invariant.
        *
        * Extracted so [[coerce]] and [[slotConsults]] read ONE predicate: a consult that re-derived
        * the condition beside the clause it is about would be a second answer to one question, which
        * is `ENGINE-LIMITS.md` F8's shape (`CLAUDE.md` §4.56). `coerce` keeps its own `arrayCov`
        * gate on top — that flag is a fact about the CALLEE (own methods keep the invariant
        * `Array[T]`), not about the slot. */
      private def arrayCovSlot(target: CtTypeReference[?], et: CtTypeReference[?]): Boolean =
        target != null && target.isInstanceOf[CtArrayTypeReference[?]] && et != null &&
          et.isInstanceOf[CtArrayTypeReference[?]] && target.getQualifiedName != et.getQualifiedName

      /** …AND THE SAME QUESTION ASKED AT THE RENDERING, which is the half a java-name test cannot
        * see.
        *
        * [[arrayCovSlot]] compares the two JAVA array types, and that is exact wherever java wrote
        * two different ones. It answers NO where JAVA'S OWN ERASURE collapses them into one:
        * `Enum<?>[] universe = getUniverse(elementType)` with `<E extends Enum<E>> E[] getUniverse`
        * reads `java.lang.Enum[]` on BOTH sides, so there is nothing to compare — while the emitted
        * term is an `Array[E]` at an `Array[Enum[?]]` slot, and scala's arrays are INVARIANT.
        *
        * `ENGINE-LIMITS.md` §0's rule read at a slot: the recorded java type is not a witness of
        * what the emitter will print. Where the two readings disagree the comparison has to be at
        * the RENDERING, and the TERM is the only side that has one — which is why this takes the
        * term rather than `castType(e)`.
        *
        * It can only ever ADD a cast where scala would have rejected the slot outright, because two
        * DIFFERENT `Array[…]` renderings never conform in either direction; where they are equal it
        * declines by arithmetic. That is the whole of its safety argument, and it is why it rides on
        * the same `arrayCov` gate rather than on one of its own.
        *
        * `want` is HANDED IN rather than looked up, and that is measured rather than stylistic: a
        * second `tpe(target)` is a second type LOWERING, and the lowering counters are the
        * denominators every `catalog(consulted)` row prints inside its own text — 1,675 extra
        * consults on a port whose emission did not move by one byte. */
      private def arrayCovRendered(target: CtTypeReference[?], want: TypeRepr, t: Term): Boolean =
        target != null && target.isInstanceOf[CtArrayTypeReference[?]] &&
          isScalaArrayType(t.tpe) && isScalaArrayType(want) && want != t.tpe

      /** JS-G14's clause — a primitive at a reference slot is java autoboxing, and the boxing's
        * target is the WRAPPER rather than the (often erased) formal. See [[arrayCovSlot]] for why
        * this is a named predicate rather than an inline condition. */
      private def boxingSlot(target: CtTypeReference[?], et: CtTypeReference[?]): Boolean =
        target != null && et != null && et.isPrimitive && !target.isPrimitive &&
          !target.isInstanceOf[CtTypeParameterReference] && !target.isInstanceOf[CtArrayTypeReference[?]]

      /** JS-G09's question at a slot — java's UNCHECKED CONVERSION (JLS 5.1.9), which is legal at a
        * raw type and has no scala image but a cast.
        *
        * This one is a SHAPE test and deliberately not [[uncheckedGeneric]]'s gate list: the consult
        * asks *does this difference APPLY here*, and `uncheckedGeneric` answers the narrower
        * question *and is a cast emittable here* (it declines for a poly expression, for a class
        * literal, for a callee-bounded formal). A consult keyed on the narrower one would report
        * "the difference does not apply" at every site where it applies and the engine refuses. */
      private def uncheckedSlot(target: CtTypeReference[?], et: CtTypeReference[?]): Boolean =
        target != null && et != null && isGenericUse(target) &&
          (mentionsRawGeneric(et) || mentionsRawGeneric(target))

      /** THE SLOT ROWS, consulted at every arm that has a slot — JS-G09, JS-G13, JS-G14.
        *
        * One function and six call sites, which is the convergence `Differences.everySlot` names: a
        * local's initialiser, an assignment, a `return`, a call argument, a `new`'s argument and an
        * array initialiser's element are six node kinds reaching ONE conversion (JLS 5.2), and a rule
        * stated once per arm is a rule the next arm will not have.
        *
        * Called from the ARM and never from [[coerce]]: `coerce` is not reached for a local with no
        * initialiser, a bare `return` or a zero-argument call, so a consult inside it would leave a
        * hole at exactly the nodes where the difference does not apply. `slots` is empty at those
        * nodes, all three consults answer `scala.None`, and the obligation is discharged honestly.
        *
        * The type read at each slot is [[castType]], not `e.getType` — the same reading `coerce`
        * takes, and for the same reason (`ENGINE-LIMITS.md` K17: a cast expression's type IS the
        * cast's, and it is that type the surrounding context converts). READ BARE, too: `castType`
        * already answers `null` where Spoon has no answer, and its callers each decline on that, so
        * a second `catch` around it here could only ever hide a divergence between this reading and
        * `coerce`'s — which is the same call, two lines apart (`CLAUDE.md` §4.6). */
      private def slotConsults(slots: List[(CtTypeReference[?], CtExpression[?])], at: Origin)
                              (using Obligations): Unit =
        val pairs = slots.map((tg, e) => (tg, castType(e)))
        Obligations.consult(JS.G(13), at)(Option.when(pairs.exists((tg, et) => arrayCovSlot(tg, et)))(()))
        Obligations.consult(JS.G(14), at)(Option.when(pairs.exists((tg, et) => boxingSlot(tg, et)))(()))
        Obligations.consult(JS.G(9),  at)(Option.when(pairs.exists((tg, et) => uncheckedSlot(tg, et)))(()))

      /** [[slotConsults]] reached from the DECLARATION dispatch — `fieldDef`'s one caller.
        *
        * A forwarder and not a second statement of the rule: the slot rows are decided in one
        * function whichever dispatch reached them, and this exists only because the three
        * predicates read `castType`, which is a `BodyTranslator` member. */
      def slotConsultsAt(slots: List[(CtTypeReference[?], CtExpression[?])], at: Origin)
                        (using Obligations): Unit = slotConsults(slots, at)

      /** the (formal, argument) pairs of a call — the slot list [[slotConsults]] wants at the two
        * call dispatches. Empty where the arities disagree, which is exactly the case `coerceArgs`
        * declines to coerce.
        *
        * The formals are read BARE, exactly as `coerceArgsFixed` reads them one function away. A
        * `catch` here would answer `Nil`, and `Nil` is not "unknown" — it is *this callee takes no
        * parameters*, which makes every arity disagree and reports all three slot rows as not
        * applying at every argument of that call (`CLAUDE.md` §4.6: a default the caller cannot
        * distinguish from a real answer is a fabricated fact). If this reading throws, the
        * translation about to run throws on the same call, so swallowing it here only removes the
        * evidence. */
      private def argSlots(ex: CtExecutableReference[?], argEs: List[CtExpression[?]]):
          List[(CtTypeReference[?], CtExpression[?])] =
        val formals = ex.getParameters.asScala.toList
        if formals.sizeIs == argEs.size then formals.zip(argEs).filter(_._1 != null) else Nil

      private def coerce(target: CtTypeReference[?], e: CtExpression[?], t: Term, arrayCov: Boolean = true,
                         tpToObject: Boolean = true, unchecked: Boolean = true): Term =
        val isNull = e match { case l: CtLiteral[?] => l.getValue == null; case _ => false }
        // THE TYPE THE COERCED TERM ACTUALLY HAS — [[castType]], not `e.getType`. Every caller
        // hands `t = expr(e)`, and `expr` has already folded the casts the SOURCE wrote onto that
        // term, so `e.getType` describes something that is no longer on the tree. Java agrees: a
        // cast expression's type IS the cast's type, and it is THAT type the surrounding
        // assignment or invocation context converts (JLS 5.2, 5.3).
        //
        // Where it bites is the `boxing` branch below, because that branch does not merely decide
        // WHETHER to convert, it names the wrapper to convert TO. `return (long) Math.ceil(d);`
        // from a method returning `Object` boxes to `java.lang.Long` in java (JLS 5.1.7, at the
        // cast's type); read as the pre-cast `double` it emitted
        // `…asInstanceOf[scala.Long].asInstanceOf[java.lang.Double]`, which is an ASSERTION that a
        // `Long` is a `Double` and throws — `ENGINE-LIMITS.md` K17's rule (a cast is not a
        // conversion) reached through the wrapper CHOICE rather than through the cast.
        //
        // The other direction needs nothing and must not get it: `(double) anObject` is a
        // checkcast to `java.lang.Double` followed by an unbox in java, never a `Number` dispatch,
        // so it THROWS for a `Long` — and `asInstanceOf[scala.Double]` on an `Any` throws in
        // exactly the same 45 cells. Probed both compilers; see K17 face 3.
        val et     = castType(e)
        val narrowing = target.isPrimitive && et != null && et.isPrimitive &&
          primRank.get(target.getSimpleName).exists(tr => primRank.get(et.getSimpleName).exists(_ > tr))
        // a primitive flowing into a concrete REFERENCE slot (`Object`, `Number`, …) is Java
        // autoboxing — Scala won't box into every such position, so make it explicit.
        val boxing = boxingSlot(target, et)
        // a value erased to `Object` (a generic method's result) flowing into a more specific
        // slot — Java inserts an unchecked downcast; Scala needs it explicit.
        val downcast = et != null && et.getQualifiedName == "java.lang.Object" &&
          !target.isPrimitive && target.getQualifiedName != "java.lang.Object"
        // a boxed wrapper flowing into a PRIMITIVE slot is Java auto-UNBOXING (possibly with a widening,
        // `Integer`→`float`); Scala does neither implicitly, so emit the explicit `n.floatValue()`
        // (every `Number` wrapper carries all the `xxxValue()` accessors; `Boolean`/`Character` their own).
        // Only a CROSS-type unbox (`Integer`→`float`) needs this — a same-type unbox (`Integer`→`int`)
        // is already handled by Scala's `Predef.Integer2int`, and forcing `.intValue()` there only
        // perturbs surrounding resolution.
        if et != null && !et.isPrimitive && target.isPrimitive && wrapperOf.values.toSet(et.getQualifiedName)
          && wrapperOf.get(target.getSimpleName).exists(_ != et.getQualifiedName) then
          return unbox(t, et.getQualifiedName, target.getSimpleName, e)
        // a type-parameter value flowing into a genuinely-`Object` slot (a return/assignment/var-init
        // where the target type is really `java.lang.Object`, not an erased formal — call args are
        // handled by `typeParamToObject` off the DECLARED formal, so this stays off that path):
        // Java erases `T` to `Object`; Scala's unbounded `T <: Any` does not conform. Cast it.
        val tpObj = tpToObject && et != null && et.isInstanceOf[CtTypeParameterReference] &&
          target.getQualifiedName == "java.lang.Object"
        // Box to the primitive's WRAPPER (`int` → `java.lang.Integer`), not the (often Object-erased)
        // formal: the wrapper is what Java autoboxing yields and it satisfies both the erased `Object`
        // slot AND a real `Integer`/`Number` one — where casting straight to `Object` fails an
        // `Integer` parameter that Spoon erased at the call reference.
        //
        // Hoisted ABOVE `cast` so `arrayCovRendered` can read the rendering this line already
        // computes. That is not tidiness: a SECOND `tpe(target)` is a second type LOWERING, and the
        // lowering counters are the denominators every `catalog(consulted)` row prints inside its own
        // text — measured at 1,675 extra consults on libGDX for an emission that was byte-identical
        // (G12's own caution, met one predicate over).
        val ct = if boxing then boxedPrimitive(et.getSimpleName) else tpe(target)
        val cast =
          tpObj ||                                                                // T → Object (non-arg)
          (isNull && target.isInstanceOf[CtTypeParameterReference]) ||             // null → type param
          (arrayCov && (arrayCovSlot(target, et) ||                               // array covariance
                        arrayCovRendered(target, ct, t))) ||                      // …at the RENDERING
          narrowing ||                                                            // int → short/byte/char
          boxing ||                                                               // int → Object/Number
          downcast                                                                // Object → specific
        if cast then
          // …AND A TARGET NAMING AN ANCESTOR'S TYPE VARIABLE IS RENDERED THROUGH THE `extends`
          // CLAUSE, which is [[uncheckedGeneric]]'s own fact read at the arm beside it. `tpe` has no
          // meaning for a variable that is not in THIS class's scope and renders a sentinel, so an
          // array-covariance cast at an inherited `H[]` slot emitted `Array[?]` — a cast whose
          // target names nothing, which is worse than the mismatch it was inserted to remove. The
          // substitution is EXACT (`ENGINE-LIMITS.md` G12) and it matters here because the vararg
          // PACK builds its array at the resolved component: rendered two different ways, the
          // element and the array around it disagree at every packed call.
          //
          // Asked ONLY where a cast is really being emitted and only where the target mentions a
          // variable at all: [[inheritedFormal]] calls `tpe`, and a lookup inside a value the caller
          // may not use moves the type-lowering denominators on every port for nothing (G12's own
          // measurement, libGDX core 82207 -> 82211 against 82207 -> 82209).
          val cct = if mentionsAnyTypeVar(target) then inheritedFormal(target).getOrElse(ct) else ct
          Tree.Typed(t, tt(cct, e), cct, originOf(e))
        else if unchecked then
          // A CONDITIONAL's unchecked conversion belongs to its BRANCHES, not to the whole
          // expression. Java's rules for a reference conditional in an assignment context assign
          // each operand to the target type separately, which is exactly why `uncheckedGeneric`
          // refuses the conditional itself: casting a poly expression destroys the inference it
          // feeds. Refusing without descending simply loses the conversion — measured in
          // simple-graphs' `AStarSearch.getPath`, `path = end != null ? new AlgorithmPath<>(end) :
          // Path.EMPTY_PATH`, where the RAW static `EMPTY_PATH` renders `Path[?]` against a `Path[V]`
          // field and Java's unchecked conversion had nowhere to land.
          //
          // Recursing through `coerce` and not `uncheckedGeneric` directly, so a branch gets whatever
          // conversion IT needs; a nested conditional resolves the same way, one level down.
          conditionalBranches(e, t) match
            case Some((c, i)) =>
              val th = coerce(target, c.getThenExpression, i.thenp, arrayCov, tpToObject, unchecked)
              val el = coerce(target, c.getElseExpression, i.elsep, arrayCov, tpToObject, unchecked)
              if (th ne i.thenp) || (el ne i.elsep) then i.copy(thenp = th, elsep = el) else t
            case None => uncheckedOf(target, e, t, ct)

        else t

      /** the conditional and the `If` it produced, when `t` really is that conditional's translation.
        * Both halves are checked: `expr` may have wrapped or replaced it, and rebuilding something
        * that is no longer an `If` would silently drop a branch. */
      private def conditionalBranches(e: CtExpression[?], t: Term): Option[(CtConditional[?], Tree.If)] =
        (e, t) match
          case (c: CtConditional[?], i: Tree.If) => Some((c, i))
          case _                                 => None

      /** the unchecked-conversion decision for a NON-conditional expression — extracted only so the
        * branch recursion above reads as one case beside it. */
      private def uncheckedOf(target: CtTypeReference[?], e: CtExpression[?], t: Term, ct: TypeRepr): Term =
          val u = uncheckedGeneric(target, e, t)
          // Java's unchecked conversion, decided on the RENDERED types rather than Spoon's. A value
          // read through an ERASED receiver (`map.keys$field` off an `OrderedMap[Object, Object]`)
          // has a Spoon type that still says `Array<K>`, so nothing above sees a mismatch — but the
          // Scala we emit for it really is `Array[Object]` flowing into an `Array[K]` slot. The TIR
          // now carries that erased type honestly (see `erasedFieldReceiver`), which is what makes
          // this decidable here at all.
          if (u ne t) || !tpAccessibleHere(target) || !uncheckedFrom(t.tpe, ct) then u
          else Tree.Typed(t, tt(ct, e), ct, originOf(e))

      private val wrapperOf = Map(
        "byte" -> "java.lang.Byte", "short" -> "java.lang.Short", "char" -> "java.lang.Character",
        "int" -> "java.lang.Integer", "long" -> "java.lang.Long", "float" -> "java.lang.Float",
        "double" -> "java.lang.Double", "boolean" -> "java.lang.Boolean")
      private val valueMethod = Map(
        "int" -> "intValue", "long" -> "longValue", "float" -> "floatValue", "double" -> "doubleValue",
        "short" -> "shortValue", "byte" -> "byteValue", "boolean" -> "booleanValue", "char" -> "charValue")
      /** `wrapper.<prim>Value()` — explicit unboxing of a boxed number/boolean/char to a primitive,
        * and the WIDENING beside it where the shortcut would name a member that does not exist.
        *
        * Java's unboxing is TWO conversions: JLS 5.1.8 unboxes at the WRAPPER'S OWN primitive, and
        * 5.1.2's widening primitive conversion then takes it to the slot. Collapsing them into one
        * `xxxValue()` keyed on the TARGET is exact for the six `java.lang.Number` wrappers — every
        * one of them carries the whole `byteValue()`…`doubleValue()` family, so `Long` → `double`
        * really is `doubleValue()`, which is the shape `ENGINE-LIMITS.md` K17 face 2 measured.
        *
        * `Character` and `Boolean` are NOT `Number`s. They carry `charValue()` / `booleanValue()`
        * and nothing else, so a `Character` at an `int` slot emitted `c.intValue()` — a member no
        * class in the chain declares. LOUD rather than silent, which is the one thing in its favour.
        * The two steps are emitted instead: unbox at the wrapper's own primitive, then convert.
        *
        * @param from the wrapper's FQN — the SOURCE. Both callers know it (`coerce` from the
        *             expression's type, `promotedBranch` from the branch's), and the question
        *             "which primitive does this wrapper actually carry" cannot be asked without it. */
      private def unbox(t: Term, from: String, prim: String, e: CtElement): Term =
        def primT(p: String) = TypeRef(NoPrefix, minter.external("scala." + primName(p), p))
        // the wrapper's OWN primitive, and whether reaching `prim` from it needs a second step.
        val own    = wrapperOf.collectFirst { case (p, w) if w == from => p }.getOrElse(prim)
        val viaOwn = own != prim && (own == "char" || own == "boolean")
        val step   = if viaOwn then own else prim
        valueMethod.get(step) match
          case Some(vm) =>
            // owner deliberately left None: the key is already a readable FQN, no portability
            // rule targets `Number`'s members, and interning `java.lang.Number` HERE moves it
            // earlier in the id sequence — which re-keys every downstream finding whose owner is
            // an external member (their `fullName` embeds the raw id). Measured: 2 findings
            // diffed as removed-and-re-added for no change in what was found.
            //
            // The two-step path keys on the WRAPPER instead, and that is not an inconsistency to
            // tidy: `charValue` is not a `Number` member, so filing it under one would hide it from
            // any portability rule that names `java.lang.Character` — while MOVING the existing key
            // would re-key every finding after it for no behavioural gain. New key, honest from the
            // start; old key, left where it is.
            val vsym = minter.external(if viaOwn then s"$from#$vm" else "java.lang.Number#" + vm, vm)
            val call = Tree.Apply(Tree.Select(t, vsym, NoType, originOf(e)), Nil, vsym, primT(step), originOf(e))
            if viaOwn then Tree.Typed(call, tt(primT(prim), e), primT(prim), originOf(e)) else call
          case None => t
      private def boxedPrimitive(prim: String): TypeRepr =
        wrapperOf.get(prim) match
          case Some(fqn) => TypeRef(NoPrefix, minter.external(fqn, simpleName(fqn)))
          case None      => TypeRef(NoPrefix, minter.external("java.lang.Object", "Object"))

      /** Java VARARGS at the CALL SITE. `T...` is emitted as a plain `Array[T]` parameter (Scala's
        * `T*` would need spread syntax and overload-aware resolution at every call), so a call that
        * passes the elements POSITIONALLY — `new VertexAttributes(a, b, c)` — has to materialize the
        * array Java would have built: `new VertexAttributes(scala.Array[VertexAttribute](a, b, c))`.
        * A call that already passes an array (Java permits that too) is left alone; so is a generic
        * `T...` component, whose element type would not render at the call site.
        *
        * ==…and that convention stops at the program's edge (`ENGINE-LIMITS.md` K6.5, third case)==
        * The materialised pack is right because BOTH halves are ours: the emitted `def f(xs:
        * Array[T])` and the emitted `f(Array[T](a, b))` agree by construction. An EXTERNAL callee's
        * half is a CLASS FILE nothing in this port can move, and scalac reads a java `T...` there as
        * a REPEATED parameter — so the pack is one argument too many, at every external java vararg
        * method, which every library meets. `Paths.get(".")` emitted `Paths.get(".",
        * Array[String]())` and read `Found: Array[String] / Required: String`.
        *
        * **The loud half is the smaller half.** Where the repeated element is `Object` the pack
        * CONFORMS — `Array[Object] <: Object` — so `String.format(fmt, Array[Object](a, b))`
        * compiles and passes the array as a SINGLE `%s`, which is CLAUDE.md §4.4's shape exactly: no
        * error, no moved count, and a wrong string at run time. 9 such sites in one library against
        * 9 that failed to compile.
        *
        * So an external callee gets `Tree.Repeated`, which the emitter renders as the ELEMENTS —
        * `CLAUDE.md` §6's spread with no spread syntax needed, and the same normalisation K6.5's
        * `Arrays.asList` rewrite already performs one layer up. Ownership is decided STRUCTURALLY
        * (§4.56) from the DECLARING type being a shadow — a reconstruction from bytecode — never
        * from the name: a resolution root's java is parsed as source and stays ours, which is what
        * keeps a dependent port's calls into its base on the materialised form both modules emit. */
      /** JS-G38's question, as a function of the vararg slot: does the argument in it ALREADY hold
        * the array java would otherwise have built?
        *
        * Named because [[varargPack]] and [[callConsults]] both ask it, and a copy is a second
        * answer (`ENGINE-LIMITS.md` F8). The two facts inside it are java's own, not conveniences:
        *
        *   - the CAST wins where there is one. Spoon types `(String[]) null` by the literal, and it
        *     is the cast java resolved the slot against, so it is the cast's component that decides.
        *     OUTERMOST first, which is the head (see [[castType]]);
        *   - the COMPONENT TYPES have to agree. Java's rule for the slot is ASSIGNABILITY and a
        *     PRIMITIVE array is assignable to nothing but its own array type, so `int[]` at an
        *     `Object...`/`T...` slot is not a pass-through at all — java materialises
        *     `new Object[]{ intArr }`, ONE element holding the array. That is what
        *     `Arrays.asList(intArr)` (a `List<int[]>` of size 1) and `String.format("%s", intArr)`
        *     (one `%s`, printing `[I@…`) both are, and reading it as a pass-through is
        *     `CLAUDE.md` §4.4's shape twice over. A REFERENCE component is left alone —
        *     `String[] <: Object[]` is java's own array covariance and the forward really is one;
        *   - a BARE `null` IS the array; `(String) null` is not. The cast names the COMPONENT type,
        *     which is exactly how java disambiguates the two;
        *   - …and the ARRAY DIMENSION is what decides the REFERENCE case, which "is the argument an
        *     array" silently did the work of for as long as no corpus vararg's component was ITSELF
        *     an array. Java's rule for the slot is assignability to the PARAMETER's array type: at
        *     an `H[]...` slot the parameter is `H[][]`, a plain `H[]` is assignable to the COMPONENT
        *     and not to the parameter, so java PACKS. Read as a pass-through the port forwards a
        *     one-dimensional array into a two-dimensional slot — the ARITY of the emitted call is
        *     wrong before its element type is. `dims(arg) >= dims(comp) + 1` answers all five of
        *     `ENGINE-LIMITS.md` G26's javac-probed cells with no subtyping oracle, and every shape
        *     javac REJECTS (a `String[][]` at a `String...`) is outside it either way. Note the two
        *     conjuncts are ONE rule read at its two kinds: the primitive test is assignability where
        *     the component is primitive, this is assignability where it is an array. */
      /* …and every one of the three reads below is BARE, because `varargPack` — the TRANSLATION
         this predicate is about, which calls this very function — reads all three bare within ten
         lines: `arr.getComponentType` in its own `comp`, `e.getTypeCasts` in `expr`'s cast fold,
         `e.getType` through `ty`. A `catch` on the consult side of a value the translation reads
         unwrapped can only ever hide a divergence between the two, and each default was a
         statement rather than an absence: `getComponentType` failing answered *the components
         agree* (so the argument passes through and java's `new Object[]{ x }` is not built),
         `getTypeCasts` failing answered *the source wrote no cast*. `CLAUDE.md` §4.6. The `null`
         handling is unchanged — an absent `getType` is normal under `noClasspath` and is what the
         `collectFirst` declines on. */
      private def varargHoldsArray(comp: CtTypeReference[?], e: CtExpression[?]): Boolean =
        def componentAgrees(arr: CtArrayTypeReference[?]): Boolean =
          val ac = arr.getComponentType
          if ac == null || comp == null then true
          else if ac.isPrimitive || comp.isPrimitive then ac.getQualifiedName == comp.getQualifiedName
          else arrayDims(arr) >= arrayDims(comp) + 1
        val casts = e.getTypeCasts.asScala.toList
        val own   = e.getType
        (casts :+ own).collectFirst { case a: CtArrayTypeReference[?] => a }.exists(componentAgrees) ||
          (e match { case lit: CtLiteral[?] => lit.getValue == null && casts.isEmpty; case _ => false })

      /** the callee's declared parameters, or `scala.None` where the declaration cannot be read —
        * the ONE lookup where an absent value is normal (`CLAUDE.md` §4.6), shared by [[varargPack]]
        * and [[callConsults]] so the two never disagree about whether a callee is variadic.
        *
        * ==And at a `CtNewClass` the parser SYNTHESISES the declaration this reads==
        * `CLAUDE.md` §4.59 exactly, met at a constructor. The executable reference of
        * `new P(a, b) { … }` names the ANONYMOUS SUBTYPE's constructor, and Spoon materialises that
        * one with a single parameter of NO type and `isVarArgs = false` — which is not "unknown", it
        * is *this callee is not variadic*, the §4.6 fabricated fact baked into the model rather than
        * into a `catch`. Every reader here then answers about a constructor java never wrote:
        * [[varargPack]] leaves N arguments loose against the one `Array[T]` formal the parent's
        * `T...` emitted, which scala AUTO-TUPLES where the parent declares one constructor (a green
        * compile, an argument of the wrong shape) and reports an overload failure where it declares
        * several. The very same `new P(a, b)` WITHOUT a body packs correctly, so nothing about the
        * translation is inconsistent — only the two `new`s are read from different declarations.
        *
        * JLS 15.9.5.1 says what java derives: the anonymous class's constructor takes the SUPERCLASS
        * constructor's parameters and passes them straight through. So the declaration to read is the
        * SUPERCLASS's, chosen by the ERASED parameter types the REFERENCE carries — the one part of
        * `ex` that is not synthesised. An anonymous class over an INTERFACE invokes `Object()`, has
        * no parameter to match and falls out here, which is the right answer and not a decline. */
      private def declParams(ex: CtExecutableReference[?]): Option[List[CtParameter[?]]] =
        try anonSuperCtor(ex).orElse(Option(ex.getExecutableDeclaration))
              .map(_.getParameters.asScala.toList)
        catch { case _: Throwable => scala.None }

      /** the SUPERCLASS constructor an anonymous-class construction really invokes — see
        * [[declParams]]. `scala.None` for every executable that is not one, which is every call in a
        * program with no anonymous class in it.
        *
        * The erased signature the reference carries is matched FIRST and the arity only where it is
        * unambiguous, in that order and not the other way round: `noClasspath` erases a REFERENCE's
        * generic formals and not a DECLARATION's (JS-G18), so a generic constructor's names do not
        * meet and the arity is the honest fallback — while a parent with three one-argument
        * constructors (`T...`, `T[]...`, `Collection<T>`) is a shape any collection library has, and
        * there the names are the only thing that tells them apart. Neither answering leaves the
        * lookup declining exactly as it did before this existed. */
      private def anonSuperCtor(ex: CtExecutableReference[?]): Option[CtExecutable[?]] =
        val cands =
          for
            dt   <- Option(ex.getDeclaringType).toList
            decl <- Option(dt.getDeclaration).toList.collect { case c: CtType[?] if c.isAnonymous => c }
            sup  <- Option(decl.getSuperclass).toList
            supD <- Option(sup.getDeclaration).toList.collect { case c: CtClass[?] => c }
            ctor <- supD.getConstructors.asScala.toList
          yield ctor
        val want = ex.getParameters.asScala.toList.map(t => Option(t).map(_.getQualifiedName))
        def named = cands.filter(_.getParameters.asScala.toList
          .map(p => Option(p.getType).map(_.getQualifiedName)) == want)
        def arity = cands.filter(_.getParameters.size == want.size)
        named match
          case one :: Nil => Some(one)
          case _          => arity match
            case one :: Nil => Some(one)
            case _          => scala.None

      /** THE CALL ROWS, consulted at every call dispatch — JS-G18, JS-G32, JS-G37…G40, JS-G42.
        *
        * Called from [[coerceArgs]], which is the ONE function both `invocation` and `ctorCall` reach
        * unconditionally, so the two dispatches cannot answer differently and an anonymous-class
        * construction is not a third copy.
        *
        * Every predicate is read off the callee's REFERENCE and DECLARATION rather than by re-running
        * [[varargPack]]: the consult asks *does this difference apply at this call*, which is a
        * question about the shape, and whether the pack, the spread or the erasure cast came out
        * right is the edge-case suite's job (`CatalogAreaGSpec`). */
      private def callConsults(ex: CtExecutableReference[?], argEs: List[CtExpression[?]], at: Origin)
                              (using Obligations): Unit =
        // BARE, for [[argSlots]]' reason: `coerceArgsFixed` and `passedThrough` both read
        // `isExternalCallee` unwrapped, and `false` here is not "unknown" — it is *this callee is
        // one of ours*, which is the fact JS-G37 and JS-G39/G40 are the two sides of, so a swallowed
        // failure would move the consult from one row to its opposite (`CLAUDE.md` §4.6).
        val external = isExternalCallee(ex)
        val ps       = declParams(ex)
        val variadic = ps.exists(l => l.nonEmpty && l.last.isVarArgs)
        val comp     = ps.filter(_ => variadic).map(_.last.getType).collect {
          case a: CtArrayTypeReference[?] => a.getComponentType }.orNull
        val holds    = variadic && ps.exists(l => argEs.sizeIs == l.size) &&
          argEs.lastOption.exists(varargHoldsArray(comp, _))
        // JS-G18 — under `noClasspath` an executable REFERENCE erases its generic formals and the
        // DECLARATION does not, so an argument at an external callee is where the two readings meet.
        Obligations.consult(JS.G(18), at)(Option.when(external && argEs.nonEmpty)(()))
        // JS-G32 — a formal written in the CALLEE's own type variables, which are not in scope here.
        Obligations.consult(JS.G(32), at)(Option.when(
          ps.exists(_.exists(p => Option(p.getType).exists(f => mentionsAnyTypeVar(f) && !tpResolvable(f)))))(()))
        // JS-G37 — java materialised the array and an in-program callee's parameter is emitted
        // `Array[T]`, so the call has to materialise it too.
        Obligations.consult(JS.G(37), at)(Option.when(variadic && !external && !holds)(()))
        // JS-G38 — …and where the slot already holds one, re-packing it would build an array of one.
        Obligations.consult(JS.G(38), at)(Option.when(variadic && holds)(()))
        // JS-G39 — an EXTERNAL callee's `T...` is a class file's, which scalac reads as a REPEATED
        // parameter; JS-G40 is the two composed, which is java's own vararg-forwarding idiom.
        Obligations.consult(JS.G(39), at)(Option.when(variadic && external)(()))
        Obligations.consult(JS.G(40), at)(Option.when(variadic && external && holds)(()))
        // JS-G42 — the component's element type is not at the call site whenever the declared
        // component is not already a concrete type.
        Obligations.consult(JS.G(42), at)(Option.when(variadic && comp != null && !tpConcrete(comp))(()))

      private def varargPack(ex: CtExecutableReference[?], argEs: List[CtExpression[?]],
                             recvSubst: Map[String, CtTypeReference[?]]): Option[List[Term]] =
        val ps = declParams(ex)
        ps match
          case Some(l) if l.nonEmpty && l.last.isVarArgs =>
            val fixed = l.size - 1
            val comp = l.last.getType match
              case arr: CtArrayTypeReference[?] => arr.getComponentType
              case _                            => null
            // already an array in the vararg slot (`f(arr)`, `f((String[]) null)`) — Java passes it
            // through, and so do we WHERE THE CALLEE IS OURS. At an EXTERNAL one it becomes a
            // SPREAD, which is the mirror of the pack below and the same fact: see `passThrough`.
            // The CASTS matter: Spoon types `(String[]) null` by the literal, not the cast, and
            // packing it would build `Array[String](null: Array[String])`.
            // …AND THE COMPONENT TYPES HAVE TO AGREE. Java's rule for the slot is ASSIGNABILITY,
            // and a PRIMITIVE array is assignable to nothing but its own array type — so `int[]` at
            // an `Object...`/`T...` slot is not a pass-through at all: java materialises
            // `new Object[]{ intArr }`, ONE element holding the array. That is the classic gotcha
            // `Arrays.asList(intArr)` (a `List<int[]>` of size 1) and `String.format("%s", intArr)`
            // (one `%s`, printing `[I@…`) are both instances of, and reading it as a pass-through is
            // CLAUDE.md §4.4's shape twice over: `Arrays.asList(intArr*)` compiles and yields a list
            // of five, `String.format(fmt, intArr*)` changes the call's arity. Neither moves a
            // count. A REFERENCE component is left alone — `String[] <: Object[]` is java's own
            // array covariance and the forward really is a forward.
            val passesArray = argEs.sizeIs == l.size && varargHoldsArray(comp, argEs.last)
            // A GENERIC vararg component (`static <T> Array<T> with (T... array)`) cannot be named at
            // the call site — but Java materialises the array from the ARGUMENTS' own type, and
            // naming that lets Scala infer `T` exactly as Java did. Only when every trailing
            // argument agrees on one concrete type; a mixed set would need a lub we have no business
            // computing here.
            val elemRef: Option[CtTypeReference[?]] =
              if comp != null && tpConcrete(comp) then Some(comp)
              // ZERO variadic arguments — `Family.all()` against `all(Class<? extends Component>...)`.
              // Java materialises an EMPTY array, so there is nothing to infer the element type FROM
              // and nothing that needs inferring: the declared component type is already exactly what
              // the parameter renders as. Without this the call emitted no argument at all and the
              // method looked as though it were missing one, which is how it surfaced.
              //
              // Guarded on the component not being a bare type VARIABLE: `static <T> Array<T> with(T...)`
              // called as `with()` would name a `T` that does not exist at the call site — the same
              // reason the inference branch below refuses a generic component.
              // The DECLARED component type, whenever it is not a bare type variable.
              //
              // Argument inference is the wrong source here and `Family.all(ComponentA.class)` shows
              // why: Spoon types a class literal as RAW `Class`, so the inferred element renders
              // `Class[?]` while the parameter it is being passed to declares
              // `Array[Class[? <: Component]]` — 94 errors in Ashley's suite, all one shape. This is
              // ENGINE-LIMITS §0 (two renderings of one Java type: a declaration in one scope, a use
              // re-rendered in another) and the rule that resolves it is G1, erase USES and never
              // DECLARATIONS: the array being built is the parameter's own declared type.
              //
              // A bare type variable is still excluded — `<T> with(T...)` names a `T` that does not
              // exist at the call site — and that is what the inference branch below remains for.
              else if comp != null && !comp.isInstanceOf[CtTypeParameterReference] then
                Some(comp)
              // A bare `V...` on a KNOWN receiver: `graph.addVertices(0, 1, 2)` where
              // `graph : DirectedGraph<Integer>`. The element type is not at the call site and is not
              // inferable from the arguments either — java AUTOBOXES `int` literals into `Integer[]`,
              // so the argument types (`int`) name the wrong thing and the branch below rejects them
              // as primitive. It is the RECEIVER that says what `V` is, which is the same rule
              // `knownReceiverArgs` and `appliedCtorArgs` already apply one level out (ENGINE-LIMITS
              // G12: a callee's own type variables do not resolve at the call site, but the CLASS's
              // do, through the receiver's type arguments).
              else if comp != null && recvSubst.contains(comp.getSimpleName) then
                Some(recvSubst(comp.getSimpleName))
              else
                val ts = argEs.drop(fixed).map(e => try e.getType catch { case _: Throwable => null })
                Option.when(ts.nonEmpty && ts.forall(t => t != null && !t.isPrimitive && tpConcrete(t)) &&
                            ts.map(_.getQualifiedName).distinct.sizeIs == 1)(ts.head)
            // the declaring type is a SHADOW exactly when it was reconstructed from bytecode —
            // the same signal `coerceArgsFixed` reads for the erasure cast, and the only one that
            // survives `noClasspath` (where `getExecutableDeclaration` is non-null for the JDK too).
            // ONE answer for both directions: which side of the program's edge the CALLEE is on.
            val external = isExternalCallee(ex)
            if comp == null || argEs.sizeIs < fixed then None
            else if passesArray then passedThrough(ex, argEs, external, recvSubst)
            else if elemRef.isEmpty then None
            else
              val (head, rest) = argEs.splitAt(fixed)
              val fixedTerms = head.zipWithIndex.map { (e, i) => coerce(l(i).getType, e, expr(e)) }
              // THE ELEMENT TYPE, with an ANCESTOR's type variables replaced by what THIS class
              // instantiated them with — the half `ENGINE-LIMITS.md` G26 stated it had no answer for
              // and wave 5 supplied. The array the pack materialises is the PARAMETER's own declared
              // component, and where that component is written in the callee's own variables `tpe`
              // renders the unresolvable one as a `?H` SENTINEL: a type nothing can be passed at,
              // with the arity right and the element wrong (measured `markers` 0 -> 1, 18 references
              // in one module). [[inheritedFormal]] is the SAME lookup the inherited-formal cast
              // uses — keyed by `(declaring type FQN, formal name)`, resolved through the `extends`
              // clause — so `H[]` renders as the instantiation this class wrote down, and the cast
              // on the element below then agrees with the array being built around it. `scala.None`
              // wherever nothing substitutes, which is every component that was already nameable:
              // argument INFERENCE remains refused here (it is what measured 81 -> 83, twice).
              val ct = inheritedFormal(elemRef.get).getOrElse(tpe(elemRef.get))
              val elems = rest.map(e => coerce(elemRef.get, e, expr(e)))
              val at = AppliedType(TypeRef(NoPrefix, minter.external("scala.Array", "Array")), List(ct))
              val o = argEs.headOption.map(originOf).getOrElse(Origin.synthetic)
              Some(fixedTerms :+ (
                if external then Tree.Repeated(elems, at, o)
                else Tree.NewArray(TypeTree(ct, o), Nil, Some(elems), at, o)))
          case _ => None

      /** java already holds the array and passes it WHOLE through the `T...` slot — the MIRROR of
        * the pack above, and the same fact about the program's edge (`ENGINE-LIMITS.md` K6.5).
        *
        * Where the callee is OURS the parameter is emitted `def f(xs: Array[T])`, so passing the
        * array as it stands is exactly right and nothing has to happen: `None`, which leaves
        * `coerceArgsFixed` to render the ordinary argument list. That is the case every in-program
        * vararg method is in, and it is what a dependent port's calls into its BASE are in too — a
        * resolution root's java is parsed as source and stays ours.
        *
        * Where the callee is a CLASS FILE nothing in this port can move, scalac reads that `T...`
        * as a REPEATED parameter, and a bare array conforms as ONE element. Java's own
        * vararg-FORWARDING idiom is exactly this shape — `String.format(fmt, args)`,
        * `Arrays.asList(xs)`, `logger.debug(msg, args)` — so it is not an edge case:
        *
        *   - where the repeated element is `Object` the bare array COMPILES and means something
        *     else. `String.format("%s-%s", args)` prints the array as a single `%s` and then throws
        *     `MissingFormatArgumentException` for the second — CLAUDE.md §4.4's shape exactly: no
        *     error, no moved count, and a wrong answer at run time. Measured;
        *   - otherwise it is an uncounted compile error at every such call.
        *
        * So the array is SPREAD, and the spread is faithful rather than a compromise: measured on
        * 3.8.4, `java.util.Arrays.asList(arr*)` yields a list of `arr.length` elements that still
        * ALIASES `arr` — writes through it are visible — which is precisely what java's own
        * pass-through does. (It is the reason a `Buffer` COPY at the same call is refused one layer
        * up, in the `asList` rewrite; the spread has no such cost.) A bare `null` in the slot is
        * java's null ARRAY, and `f(null*)` renders it as one: it compiles, and it throws where java
        * throws. */
      private def passedThrough(ex: CtExecutableReference[?], argEs: List[CtExpression[?]],
                                external: Boolean,
                                recvSubst: Map[String, CtTypeReference[?]]): Option[List[Term]] =
        if !external then None
        else
          // through `coerceArgsFixed`, never around it: the erasure cast a java `Object...` formal
          // needs (`args.asInstanceOf[Array[Object]]`) is that function's answer, and a second
          // spelling of it here would be a second answer.
          val terms = coerceArgsFixed(ex, argEs, recvSubst)
          if terms.sizeIs != argEs.size then None
          else Some(terms.init :+ Tree.Spread(terms.last, terms.last.tpe, originOf(argEs.last)))

      /** coerce each argument to its formal parameter type (Java autoboxing / numeric narrowing
        * that Scala won't do implicitly). Skipped when arities differ (varargs spread etc.). */
      private def coerceArgs(ex: CtExecutableReference[?], argEs: List[CtExpression[?]], at: Origin,
                             recvSubst: Map[String, CtTypeReference[?]] = Map.empty)
                            (using Obligations): List[Term] =
        // THE CALL DISPATCHES' AREA-G CONSULTS, both families, at the one function `invocation` and
        // `ctorCall` reach unconditionally — an argument list is where java's method-invocation
        // conversion (JLS 15.12.4.2) and its assignment conversion (JLS 5.2) are both performed.
        // `at` is the CALL's origin and not the first argument's, because a zero-argument call has no
        // argument to point at and a finding owes a line somebody can open.
        callConsults(ex, argEs, at)
        slotConsults(argSlots(ex, argEs), at)
        varargPack(ex, argEs, recvSubst).getOrElse(coerceArgsFixed(ex, argEs, recvSubst))

      /** the receiver's own type arguments, by the declaring class's parameter NAMES — `Graph<V>`
        * called on a `DirectedGraph<Integer>` gives `V -> Integer`.
        *
        * Only a fully known instantiation: same arity, every argument nameable here, no wildcards. A
        * wildcard would put a `?` where a real type has to go, which is the `?T` stub this frontend
        * refuses to emit everywhere else.
        *
        * '''NAMEABLE HERE, not CONCRETE.''' The gate was `tpConcrete`, which answers `false` for a
        * type PARAMETER — so a field typed at the enclosing class's own variable
        * (`OrderedSet<V> keySet` inside `OrderedMultiMap<K, V>`) offered the substitution
        * `E := V` and it was discarded before anything could use it, which is why
        * `keySet.add(null)` had no type to cast the `null` at. `tpConcrete`'s neighbour already
        * documents that as the case it "excludes wrongly", and [[tpNameableHere]] is that repair:
        * the caller's OWN variable is a type this scope can write down. */
      private def receiverTypeArgs(inv: CtInvocation[?]): Map[String, CtTypeReference[?]] =
        val rt = inv.getTarget match
          case null => null
          case _: CtSuperAccess[?] | _: CtTypeAccess[?] => null
          case t    => castType(t)
        typeArgSubst(rt)

      /** what a REFERENCE's instantiation says the DECLARING type's formals are — `Bag<V>` says
        * `E := V` for `class Bag<E>`, by position and by the declaration's own names.
        *
        * One derivation, read by [[receiverTypeArgs]] (a call's receiver) and by
        * [[nullToSamResult]] (a lambda's target). Both ask the same question of a reference and
        * both need the same two guards: a WILDCARD actual is a capture with no name, and an actual
        * only the callee can write is §4.6's fabricated fact. Spoon's own `TypeAdaptor` answers
        * this for a receiver and measurably does NOT for a lambda target — it handed back the
        * interface's own `V` for a `Factory<T>` target — so the substitution is derived here rather
        * than asked of it. */
      private def typeArgSubst(rt: CtTypeReference[?]): Map[String, CtTypeReference[?]] =
        if rt == null || rt.isPrimitive || rt.isInstanceOf[CtArrayTypeReference[?]] ||
           rt.isInstanceOf[CtTypeParameterReference] || rt.isInstanceOf[CtWildcardReference] then Map.empty
        else
          val formals = try Option(rt.getTypeDeclaration).map(_.getFormalCtTypeParameters.asScala.toList).getOrElse(Nil)
                        catch { case _: Throwable => Nil }
          val actuals = try rt.getActualTypeArguments.asScala.toList catch { case _: Throwable => Nil }
          if formals.nonEmpty && actuals.sizeIs == formals.size &&
             actuals.forall(a => !a.isInstanceOf[CtWildcardReference] && tpNameableHere(a))
          then formals.map(_.getSimpleName).zip(actuals).toMap
          else Map.empty

      /** @param recvSubst
        *   the RECEIVER's own type arguments, by the declaring class's parameter names
        *   ([[receiverTypeArgs]]) — THREADED from [[coerceArgs]] rather than re-derived here,
        *   because two derivations of one substitution is F8's finding with a longer fuse. Only
        *   [[nullToTypeParam]] reads it today, and it reads it for G12's rule: a callee's own type
        *   variables do not resolve at the call site, but the CLASS's do, through the receiver. */
      private def coerceArgsFixed(ex: CtExecutableReference[?], argEs: List[CtExpression[?]],
                                  recvSubst: Map[String, CtTypeReference[?]] = Map.empty): List[Term] =
        // Array covariance at call args is DISABLED for OUR OWN methods — Spoon erases a generic
        // array formal (`T[]`) to `Object[]`, and casting the arg to `Array[Object]` breaks the
        // (overloaded) Scala method that actually wants the invariant `Array[T]`. But for EXTERNAL
        // (JDK/library) callees there is no `Array[T]` Scala overload — the real method genuinely
        // takes `Object[]` (Java erasure), so `Arrays.fill(items: Array[T], …)` / `copyOf` need the
        // `items.asInstanceOf[Array[Object]]` erasure cast. Enable covariance only for those.
        // A JDK/library method's declaration is a SHADOW type (reconstructed from bytecode/reflection);
        // our own source types are non-shadow. (`getExecutableDeclaration` is non-null even for JDK
        // methods under noClasspath, so isShadow — not null-ness — is the reliable external signal.)
        val external = isExternalCallee(ex)
        val formals = ex.getParameters.asScala.toList
        // Under noClasspath, an executable REFERENCE erases a generic formal `T` to `Object`, so
        // `coerce` sees `null → Object` (legal) and skips the cast — yet the emitted method keeps
        // the real `T`, where `null → T` fails. Consult the DECLARATION's un-erased formals to
        // recover the type parameter and cast the null there (`set(null.asInstanceOf[T])`).
        val declFormals: Int => Option[CtTypeReference[?]] =
          val ps = Option(ex.getExecutableDeclaration).map(_.getParameters.asScala.toList.map(_.getType))
          i => ps.flatMap(l => if i < l.size then Option(l(i)) else None)
        if formals.size == argEs.size then
          argEs.zipWithIndex.map { (e, i) =>
            val base = expr(e)
            val c = nullToTypeParam(e, declFormals(i), recvSubst,
              coerce(formals(i), e, base, arrayCov = external, tpToObject = false, unchecked = false))
            val o = typeParamToObject(e, declFormals(i), c)
            // Java's unchecked conversion at an argument, off the DECLARATION's formal (the
            // reference's is erased under noClasspath). `rawTarget = false`: a raw FORMAL belongs to
            // the callee's scope, where name-directed fill can resolve differently than here, so
            // only a raw ARGUMENT type drives this cast. Skipped when a coercion already fired.
            if o ne base then o
            else
              val a = arrayFormalCast(e, declFormals(i), o)
              if a ne o then a
              else declFormals(i).map(f => uncheckedGeneric(f, e, o, rawTarget = false, ownScope = false)).getOrElse(o)
          }
        else argEs.map(expr)

      /** `null` passed to a callee slot whose real (un-erased) formal is a type parameter — cast it,
        * so the ported call type-checks (`m(null)` → `m(null.asInstanceOf[T])`). The dominant case is
        * a self-call inside the generic class, where `T` is in scope at the call site.
        *
        * '''And the second case is the RECEIVER's, which is G12's rule and not a widening of the
        * first.''' A callee's own type variables do not resolve at the call site — that is why the
        * `resolveTypeParam` arm exists and why it is name-based and therefore narrow — but the
        * declaring CLASS's do, through the receiver's type arguments: `OrderedSet<E>.add(E)` called
        * on an `OrderedSet<V>` field inside `OrderedMultiMap<K, V>` says `E := V` exactly, and `V`
        * is a type this scope can write. Without it the emitted `add(null)` is `Found: Null /
        * Required: E`, which is what two of ssg-md's five `null`-at-a-type-parameter errors were.
        *
        * The receiver's answer is asked FIRST because it is the exact one: `resolveTypeParam` is a
        * NAME lookup, so a callee's `<E>` could silently find an unrelated in-scope `E`, while
        * `recvSubst` is keyed on the DECLARING CLASS's own formals. For that same reason it is only
        * consulted for a variable the class declares — a METHOD's own `<E>` shadowing the class's is
        * ordinary java, and substituting the class's instantiation into it would be §4.56's name
        * hazard at a type variable. */
      private def nullToTypeParam(e: CtExpression[?], declFormal: Option[CtTypeReference[?]],
                                  recvSubst: Map[String, CtTypeReference[?]], t: Term): Term =
        val isNull = e match { case l: CtLiteral[?] => l.getValue == null; case _ => false }
        def classOwned(tp: CtTypeParameterReference): Boolean =
          try Option(tp.getDeclaration).map(_.getParent).exists(_.isInstanceOf[CtType[?]])
          catch { case _: Throwable => false }
        def cast(target: CtTypeReference[?]): Term = Tree.Typed(t, tt(tpe(target), e), tpe(target), originOf(e))
        declFormal match
          case Some(tp: CtTypeParameterReference) if isNull &&
            classOwned(tp) && recvSubst.get(tp.getSimpleName).exists(tpNameableHere) =>
            cast(recvSubst(tp.getSimpleName))
          // only when the callee's type parameter actually RESOLVES in scope here (a self-call inside
          // the same generic class) — otherwise `tpe` yields the `?T` unresolved stub, invalid syntax.
          case Some(tp: CtTypeParameterReference) if isNull && resolveTypeParam(tp.getSimpleName).isDefined =>
            cast(tp)
          case _ => t

      /** An ARRAY argument whose emitted element type is not the declared formal's.
        *
        * Java arrays are COVARIANT and erase their generic element type; Scala's are INVARIANT.
        * Each of these is legal in Java and rejected by Scala, and all three occur in libgdx:
        *   - a wildcard CAPTURE — `addAll(Array<? extends T> a)` calling `addAll(a.items, 0, a.size)`,
        *     where `a.items` types as `Array[a.T]`, not `Array[T]`;
        *   - a `T[]` value in an `Object[]` formal — `Sort.sort(Object[], int, int)` receiving `items`;
        *   - plain covariance, `Sub[]` into a `Super[]` slot.
        * The reference is bit-identical on the JVM (Java's own check is the runtime
        * `ArrayStoreException`, which no Scala rendering reproduces either way), so the faithful
        * port of the Java conversion is an explicit `asInstanceOf` at the USE — never a widened
        * DECLARATION, which was measured catastrophic (see [[erasureOfFormal]]).
        *
        * Driven by the DECLARATION's formal, never the reference's: under noClasspath a reference
        * erases `T[]` to `Object[]`, and casting to `Array[Object]` is precisely what breaks our own
        * `addAll(Array[T], …)` — which is why blanket array covariance stays OFF for source callees
        * in [[coerceArgsFixed]]. Gated on [[formalNameableHere]] so the cast never names a type
        * variable that is only the callee's, nor one this scope cannot see. */
      private def arrayFormalCast(e: CtExpression[?], declFormal: Option[CtTypeReference[?]], t: Term): Term =
        declFormal match
          case Some(arr: CtArrayTypeReference[?]) if formalNameableHere(arr) && isScalaArrayType(t.tpe) =>
            val want = tpe(arr)
            if want == t.tpe || !isScalaArrayType(want) then t
            else Tree.Typed(t, tt(want, e), want, originOf(e))
          case _ => t

      /** A type-parameter-typed value flowing into a slot whose real formal is concretely
        * `java.lang.Object` (`Json.writeValue(String, Object, …)`): Java erases `T` to `Object`, but
        * Scala's unbounded `T <: Any` does NOT conform to `Object`. Cast (`resource.asInstanceOf[Object]`).
        * Gated on the DECLARED formal being Object — NOT a type parameter erased to Object — so we
        * never break our own `foo(x: T)` methods (whose real Scala signature keeps the invariant `T`). */
      private def typeParamToObject(e: CtExpression[?], declFormal: Option[CtTypeReference[?]], t: Term): Term =
        val et = try e.getType catch { case _: Throwable => null }
        // A read through a WILDCARD-filled receiver is the other value Scala types as `Any`.
        // `for (Iterator iter = it.iterator(); …) append(iter.next())` reads a RAW `Iterator`, which
        // Java types as `Object`; we render the raw receiver `JavaIterator[?]`, so Scala's result is
        // the wildcard — weaker than `Object`, and rejected by an `Object` slot.
        val wildcardRead = e match
          case inv: CtInvocation[?] =>
            val rt = try Option(inv.getTarget).map(_.getType).orNull catch { case _: Throwable => null }
            rt != null && !rt.isPrimitive && isGenericUse(rt) && hasWildcard(tpe(rt))
          case _ => false
        // …and the THIRD value scala types as wider than `Object` is one THIS FRONTEND made.
        // `execDef.anyForEquals` retypes a 1-argument `equals(Object)`'s parameter to `scala.Any`,
        // which is what makes it override `Object.equals` instead of clashing with it — and every
        // forwarding of that parameter (`SequenceUtils.equals(this, o)`, the ordinary shape for a
        // library with a shared equality helper) then hands an `Any` to an `Object` slot.
        //
        // Read off THIS FRONTEND'S OWN RECORD and not off the java or off the reference's node type
        // (§4.56): the java says `Object` at both ends and the reference's `ty(e)` says `Object`
        // too, because the widening happened at the DECLARATION and nowhere else. So the question
        // is *what did I intern for this symbol* — and a declaration interned at `scala.Any` NEVER
        // conforms to `java.lang.Object`, which makes the cast exact wherever it fires. It also
        // BOXES a primitive, which is what java's already-boxed `o` was.
        val anyDeclared = t match
          case Tree.Ident(s, _, _) => minter.infoOf(s) match
            case TypeRef(_, a) => minter.fullNameOf(a) == "scala.Any"
            case _             => false
          case _ => false
        declFormal match
          case Some(f) if !f.isInstanceOf[CtTypeParameterReference] && f.getQualifiedName == "java.lang.Object"
                       && ((et != null && et.isInstanceOf[CtTypeParameterReference]) || wildcardRead || anyDeclared) =>
            val obj = TypeRef(NoPrefix, minter.external("java.lang.Object", "Object"))
            Tree.Typed(t, tt(obj, e), obj, originOf(e))
          case _ => t

      private def tryStmt(t: CtTry, resources: List[Tree.ValDef])(using Obligations): Term =
        var multiCatch = false
        val catches = t.getCatchers.asScala.toList.map { c =>
          val p  = c.getParameter
          val pt = p.getMultiTypes.asScala.toList match
            case Nil    => tpe(p.getType)
            case multi  => multiCatch = true; multi.map(tpe).reduce(OrType(_, _))
          val id = defineLocal(p, pt)
          Tree.CatchCase(Tree.ValDef(id, tt(pt, p), None, originOf(p)), blockTerm(c.getBody))
        }
        // JS-S14 — java's multi-catch `A | B` has a scala image (a union type in the pattern), and
        // that image is built HERE. Fires where the source really wrote one.
        Obligations.consult(JS.S(14), originOf(t))(Option.when(multiCatch)(()))
        Tree.Try(resources, blockTerm(t.getBody), catches, Option(t.getFinalizer).map(blockTerm), unitT, originOf(t))

      /** Java switch → TIR `Match`. Empty (grouping) cases merge their labels into the next;
        * genuine fallthrough is lowered by TAIL DUPLICATION — a non-terminated case's body is
        * its own statements followed by the next case's closure (the same faithful lowering
        * the BIR frontend uses, RESEARCH §4.2), so no `Unsupported`. */
      private def switchStmt(s: CtSwitch[?])(using Obligations): Term =
        val cases = s.getCases.asScala.toList
        val selT  = try Option(s.getSelector.getType).map(tpe).getOrElse(NoType) catch { case _: Throwable => NoType }
        val arms  = switchArms(cases, s, selT, unitT, isExpr = false)
        // Java's switch with no `default` simply FALLS OUT when nothing matches; scala's `match`
        // throws `MatchError`. `switch (data[p]) { case '\\': …; case '"': … }` scanning an
        // ordinary character is the normal path, not an error — it threw on the first letter of
        // every quoted string. Add the fall-out arm java already has.
        //
        // …EXCEPT where java does NOT fall out, which is what [[isEnhanced]] answers.
        val needsFallOut = !arms.exists(_.isDefault) && !isEnhanced(cases, s.getSelector)
        // JS-S05 — a `switch` with no `default` FALLS OUT when nothing matches; a `match` with no
        // `case _` throws `MatchError`, and falling out is often the NORMAL path (a scanner reading
        // an ordinary character). Fires exactly where the arm has to be synthesised — read off the
        // decision itself, so the consult cannot say something the code does not do.
        Obligations.consult(JS.S(5), originOf(s))(Option.when(needsFallOut)(()))
        val withDefault =
          if !needsFallOut then arms
          else arms :+ Tree.CaseDef(Nil, None, unit(s), isDefault = true)
        Tree.Match(expr(s.getSelector), withDefault, unitT, originOf(s))

      /** A SWITCH EXPRESSION — JLS 15.28, catalog `JS-S09`. `switch` in value position, with `yield`
        * (JLS 14.21) as the arm's own way of producing one.
        *
        * `CtSwitchExpression` extends `CtExpression` and `CtAbstractSwitch` and NOT `CtSwitch`, so
        * the statement arm could never have caught it — which is why the construct was refused at
        * its kind rather than mis-lowered. What it needs is not a new node: a scala `match` IS an
        * expression, so [[Tree.Match]] already carries the shape and the emitter already renders it
        * in either position. The work is the arms.
        *
        * THREE things differ from the statement form, each of them a JLS rule and not a
        * convenience:
        *
        *   - '''no fall-out arm.''' JLS 15.28.1 requires a switch expression to be EXHAUSTIVE, so
        *     java never falls out of one — appending `case _ => ()` would answer `()` where java
        *     answers nothing, and would widen the expression's type to boot. Where java's own
        *     exhaustiveness fails at run time (a separately-compiled enum that gained a constant)
        *     it throws, and so does scala's `match`; the two throw different classes and both
        *     throw, which is the faithful half of that cell;
        *   - '''an arm produces a VALUE.''' A `yield` written as the arm's last statement IS the
        *     arm's value and is peeled into the block's result term; one written anywhere else is
        *     an abrupt completion from depth and stays a [[Tree.Yield]] for the emitter to wrap in
        *     a value-carrying `boundary`. An arm that cannot complete normally at all — `case 1 ->
        *     throw new X()`, or a block whose every path yields — carries its last statement as the
        *     block's result, which java's own definite-completion rule (JLS 15.28.1) is what makes
        *     safe: the term is a `Throw` or an `if` both of whose branches jump, and both are
        *     `Nothing` in scala;
        *   - '''`yield` is NOT unwrapped.''' Spoon normalises an arrow-form STATEMENT arm's
        *     expression into a `CtYieldStatement` too, which is a parser artifact — java has no
        *     such construct (JLS 14.21) — so [[caseBody]] undoes it there and leaves it here.
        *
        * Everything else is shared with the statement form through [[switchArms]], deliberately:
        * fallthrough, the labelled-vs-unlabelled break distinction and the empty-arm label
        * accumulation are the SAME rules at either position (a colon-form switch expression falls
        * through exactly as a colon-form statement does), and a second copy is the shape
        * `ENGINE-LIMITS.md` F8 is about. */
      private def switchExpr(sw: CtSwitchExpression[?, ?])(using Obligations): Term =
        val resT = ty(sw)
        // JS-S09 — always fires: every switch expression needs the image, and choosing `Tree.Match`
        // for it is the whole content of the row.
        Obligations.consult(JS.S(9), originOf(sw))(Some(()))
        val cases = sw.getCases.asScala.toList
        val selT  = try Option(sw.getSelector.getType).map(tpe).getOrElse(NoType) catch { case _: Throwable => NoType }
        Tree.Match(expr(sw.getSelector), switchArms(cases, sw, selT, resT, isExpr = true), resT, originOf(sw),
                   isExpr = true)

      /** the statements of one `case`, with Spoon's ARROW normalisation undone where java has no
        * such construct.
        *
        * Two shapes are flattened. An arrow-form arm with a BLOCK body arrives as a single
        * `CtBlock`, and its statements are the arm's — the same flattening the colon form has
        * always needed. An arrow-form STATEMENT arm (`case 1 -> doIt();`) arrives as a
        * `CtYieldStatement` wrapping the statement expression, which JLS 14.21 says is not a java
        * construct at all: `yield` is legal only inside a switch EXPRESSION. Carried through, it
        * would put a [[Tree.Yield]] in a switch statement's arm and the emitter would look for a
        * boundary that is not there. */
      private def caseBody(c: CtCase[?], isExpr: Boolean): List[CtStatement] =
        val raw = c.getStatements.asScala.toList match
          case List(b: CtBlock[?]) => b.getStatements.asScala.toList
          case l                   => l
        if isExpr then raw
        else raw.map {
          case y: CtYieldStatement => y.getExpression match
            case st: CtStatement => st
            case _               => y
          case other => other
        }

      /** ONE java switch's arms, at either of its two positions.
        *
        * The fallthrough lowering, the labelled-vs-unlabelled break distinction and the empty-arm
        * label accumulation are the same rules for a statement and for an expression — java's
        * colon form falls through in both — so they are stated once. What the caller supplies is
        * how an arm's BODY becomes a term: a statement arm is a `Unit` block, an expression arm is
        * a block whose result is the arm's value. */
      private def switchArms(cases: List[CtCase[?]], el: CtElement, selT: TypeRepr, resT: TypeRepr,
                             isExpr: Boolean)(using Obligations): List[Tree.CaseDef] =
        // per case: (body without a trailing break, terminated?)
        val split = cases.map { c =>
          val raw = caseBody(c, isExpr)
          // A trailing COMMENT is not a terminator. With comments enabled Spoon hands back a
          // free-floating `// …` as a statement of its own, and it can be the last one — reading
          // `last` literally would then miss the `break` behind it and fall the case through.
          raw.reverse.dropWhile(_.isInstanceOf[CtComment]) match
            // …an UNLABELLED one. `case '"': break outer;` does not end the case, it leaves the
            // enclosing LOOP; stripping it as a terminator silently deleted the jump, and the
            // quoted-string scanner in `JsonSkimmer` ran off the end of every string.
            case (b: CtBreak) :: _ if b.getTargetLabel == null => (raw.filterNot(_ eq b), true)
            // An ARROW arm NEVER falls through — JLS 14.11.2 gives the arrow form exactly one
            // statement group and no fallthrough at all, which is the whole reason SE14 added it.
            // Read off the CASE KIND and not off the body: an arrow arm's body carries no
            // terminator to find, so a rule that only looked for one would duplicate the NEXT
            // arm's tail into every arrow arm in the switch.
            case rest => (raw, c.getCaseKind == CaseKind.ARROW || rest.headOption.exists {
              // …and a `yield` terminates a colon-form EXPRESSION arm, exactly as a `return` and a
              // `throw` terminate a statement one: JLS 14.21 completes the whole switch expression
              // abruptly, so nothing after it in the next case can be reached from here.
              case _: CtReturn[?] | _: CtThrow | _: CtYieldStatement => true
              case _                                                 => false
            })
        }
        // JS-S07 — only an UNLABELLED trailing `break` terminates a case; a labelled one leaves the
        // enclosing LOOP, and stripping it as a terminator deletes the jump. Read off the split that
        // has just been taken, so the consult cannot say something the code does not do. It fires
        // where a case really ended on a bare `break`, which is the shape the distinction is about.
        Obligations.consult(JS.S(7), originOf(el))(
          Option.when(cases.zip(split).exists { (c, sp) => sp._2 && sp._1.size != caseBody(c, isExpr).size })(()))
        val closures = new Array[List[CtStatement]](cases.length)
        for i <- cases.indices.reverse do
          val (body, terminated) = split(i)
          closures(i) = if terminated || i == cases.length - 1 then body else body ++ closures(i + 1)
        // JS-S04 — java's switch FALLS THROUGH into the next case's statements and a `match` arm
        // never does, so a non-terminated arm is lowered by DUPLICATING the next case's tail into
        // it. Fires where an arm really runs on: a case that is neither terminated nor last and has
        // statements of its own.
        Obligations.consult(JS.S(4), originOf(el))(
          Option.when(cases.indices.exists(i =>
            !split(i)._2 && i != cases.length - 1 && split(i)._1.nonEmpty))(()))
        // JS-S10 — a PATTERN case label (JLS 14.11.1). Consulted at every switch and fired where one
        // is really written, and stated HERE rather than inside `caseLabel` for two reasons: the
        // obligation is owed at the two switch kinds' dispatches, which is where the scope is; and
        // `caseLabel` has two arms — the lowered type pattern and the refused record one — so a
        // consult written in each would be the F8 shape, one rule with two copies.
        Obligations.consult(JS.S(10), originOf(el))(
          Option.when(cases.exists(_.getCaseExpressions.asScala.exists(_.isInstanceOf[CtCasePattern])))(()))
        val out     = List.newBuilder[Tree.CaseDef]
        var pending = List.empty[Term]
        cases.zipWithIndex.foreach { case (c, idx) =>
          val labels    = c.getCaseExpressions.asScala.toList.map(caseLabel(_, c, selT))
          // `case null, default ->` (JLS 14.11.1) is ONE case that is both a null label and the
          // default. Read from `getIncludesDefault` rather than from an empty label list, or the
          // arm would render `case null` and leave the switch without the default java wrote.
          val isDefault = labels.isEmpty || c.getIncludesDefault
          val isLast    = idx == cases.length - 1
          if split(idx)._1.isEmpty && caseBody(c, isExpr).isEmpty && !isDefault && !isLast then pending = pending ++ labels
          else
            // …through `blockOf`, so an arm that ENDS on a comment keeps it. This is where the
            // shape is MANUFACTURED as often as it is written: the case-terminator `break` is
            // deleted above, and a comment written above that break becomes the arm's last
            // statement the moment it goes.
            val body =
              if isExpr then armValue(closures(idx), c, resT) else blockOf(closures(idx), c)
            out += Tree.CaseDef(pending ++ labels, Option(c.getGuard).map(expr), body, isDefault)
            pending = Nil
        }
        out.result()

      /** one case LABEL — and the SPLIT `JS-S10` is about.
        *
        * A TYPE PATTERN (`case String s ->`, JLS 14.11.1) has an EXACT image and is lowered: a
        * scala arm's typed pattern binds, narrows and composes with the `if` guard the emitter
        * already renders, so the whole construct is `Tree.TypePattern` plus `CaseDef.guard`. Note
        * what that composes with elsewhere and needs no help for: `case null ->` is an ordinary
        * literal label, which `TirEmitter.selectorCanBeNull` already reads as java's own opt-out
        * from the implicit NPE (`JS-S08`), and a pattern label makes the switch ENHANCED, which
        * `isEnhanced` already reads as "java does not fall out of this one".
        *
        * A RECORD pattern does too, now that `JS-C43` derives an `unapply` over the very accessors
        * java reads (JLS 14.30.1) — see [[recordPattern]] for the one distinction it has to make
        * that a type pattern does not.
        *
        * An UNNAMED pattern keeps the refusal, and it is a refusal nobody can trigger: no source
        * Spoon 11.5 accepts builds a `CtUnnamedPattern` at all (`ENGINE-LIMITS.md` T19).
        *
        * THE MARKER IS MINTED HERE rather than reached through `expr`'s default, because the
        * pattern node carries no source POSITION: Spoon builds `CtCasePattern` as an unpositioned
        * wrapper, so a marker minted at it falls back to the unit-fatal throw. The enclosing
        * `CtCase` is real java at a real line; the KIND, and with it the catalog row, still comes
        * from the pattern the frontend has no arm for. It carries the SELECTOR's type, which is
        * what a case label's type is — a `CtCasePattern` reports `java.lang.Void`, and typing the
        * label slot with that would put a type in the tree no later phase could read as the
        * scrutinee's.
        *
        * THE BINDING IS AN ORDINARY LOCAL, and that was worth PROBING rather than assuming, because
        * the missing position above makes the opposite plausible: java lets two arms of one switch
        * bind the same NAME (`case String v ->` beside `case Integer v ->`), `defineLocal`'s key is
        * `name#sourceStart`, and `posKey` answers `Int.MaxValue` where there is no position — so a
        * pattern variable that inherited the wrapper's missing position would intern both arms'
        * bindings as ONE symbol with ONE type. It does not: the `CtLocalVariable` inside a
        * `CtTypePattern` carries its own valid position (measured at two distinct source offsets for
        * exactly that fixture), so `defineLocal` is exact here with no special case. The unpositioned
        * node is the WRAPPER and only the wrapper. */
      private def caseLabel(e: CtExpression[?], c: CtCase[?], selT: TypeRepr): Term = e match
        case cp: CtCasePattern => cp.getPattern match
          case tp: CtTypePattern =>
            val v  = tp.getVariable
            val vt = tpe(v.getType)
            Tree.TypePattern(defineLocal(v, vt), tt(vt, v), vt, originOf(c))
          case rp: CtRecordPattern => recordPattern(rp, c, selT)
          case other =>
            unlowered(c, s"a pattern case label — ${SpoonKinds.nameOf(other.getClass)} " +
              "(JLS 14.11.1). No source this parser accepts builds one, so this refusal is a " +
              "claim about a node that has never been handed over (ENGINE-LIMITS T19)",
              selT, about = other)
        case other => expr(other)

      /** `case Point(int x, int y) ->` — java's RECORD PATTERN, as scala's constructor pattern.
        *
        * THE ONE DISTINCTION THIS HAS TO MAKE, and it is not cosmetic. JLS 14.30.2 calls a component
        * pattern UNCONDITIONAL when its type already covers the component's, and an unconditional
        * pattern matches a `null` component; a narrowing one does not. Scala's typed pattern is the
        * image of the second and NOT of the first — `case One(s: String)` fails on a null `s` where
        * java's `case One(String s)` binds it — so the two need different scala text, which is what
        * `Tree.BindPattern` beside `Tree.TypePattern` is for. Both directions measured, in both
        * languages, on the same fixtures.
        *
        * The question is asked of SPOON — `isSubtypeOf`, which is JLS 4.10's own relation — and not
        * of type equality alone, because a WIDENING pattern (`case One(Object x)` at a `String`
        * component) is unconditional too. Where it cannot answer, the narrowing arm is taken: a type
        * test where java performs one is exact, and the residue is a `null` component under a
        * widening pattern the parser could not resolve, which is the conservative side.
        *
        * A component pattern that is neither a type pattern nor a nested record pattern is refused
        * IN PLACE, at the size of the component, rather than taking the whole label down.
        *
        * ==AND THE RECORD ITSELF HAS TO BE ONE THIS RUN LOWERS==
        *
        * The extractor this arm names is DERIVED — `JS-C43` writes an `unapply` into the companion of
        * every record the run emits — so a pattern over a record the run does NOT model has nothing
        * to name. A java record from a DEPENDENCY is the shape: scala derives no extractor for a
        * java record read out of a class file, so the emitted `case dep.Rec(x, y)` would be a bare
        * `Not Found`. Refused per site instead, and refused STRUCTURALLY — the question is *does
        * this parse hold a `CtRecord` declaration for the type the pattern names*, never a name test
        * (§4.56). A record in a RESOLUTION ROOT passes, and correctly: the base module emits it, and
        * `JS-C43` puts the same `unapply` there. */
      private def recordPattern(rp: CtRecordPattern, c: CtCase[?], selT: TypeRepr): Term =
        val rt   = tpe(rp.getRecordType)
        val at   = originOf(c)
        // the DECLARATION the pattern names, if this parse has one — the licence for the extractor
        // and, in its `getRecordComponents`, java's own answer to "is this pattern unconditional".
        val decl = Option(rp.getRecordType).flatMap(r => Option(r.getTypeDeclaration)).collect {
          case r: CtRecord => r
        }
        if decl.isEmpty then
          return unlowered(c, "a RECORD PATTERN over a record this run does not model (JLS 14.30.1). " +
            "The extractor a record pattern deconstructs through is DERIVED — JS-C43 writes an " +
            "`unapply` over the accessors into every record this run emits — and scala derives none " +
            "for a java record read out of a class file, so a pattern over one from a dependency " +
            "would name nothing", selT, about = rp)
        val comps = decl.toList
          .flatMap(_.getRecordComponents.asScala.toList.sortBy(posKey).map(_.getType))
        val subs = rp.getPatternList.asScala.toList.zipWithIndex.map { (p, k) =>
          p match
            case tp: CtTypePattern =>
              val v  = tp.getVariable
              val vt = tpe(v.getType)
              val id = defineLocal(v, vt)
              if unconditional(comps.lift(k), v.getType) then Tree.BindPattern(id, vt, at)
              else Tree.TypePattern(id, tt(vt, v), vt, at)
            case nested: CtRecordPattern => recordPattern(nested, c, selT)
            case other =>
              unlowered(c, s"a record-pattern COMPONENT — ${SpoonKinds.nameOf(other.getClass)} " +
                "(JLS 14.30.1)", tpe(rp.getRecordType), about = other)
        }
        Tree.RecordPattern(tt(rt, rp), subs, rt, at)

      /** is a component pattern UNCONDITIONAL — does its type already cover the component's (JLS
        * 14.30.2)? `false` where the component's type is unknown, which is the narrowing arm and the
        * conservative side. */
      private def unconditional(component: Option[CtTypeReference[?]], pattern: CtTypeReference[?]): Boolean =
        component.exists(ct => ct == pattern || ct.isSubtypeOf(pattern))

      /** one switch-EXPRESSION arm's statements as a term whose VALUE is the arm's.
        *
        * The last statement is the arm's result, and where it is a `yield` the node is peeled: a
        * tail `yield` is what a scala arm already means, so carrying it would make every arm need a
        * boundary it does not want. Everything else is left exactly as translated — a `Throw`, or
        * an `if` whose branches all jump, is `Nothing` in scala and conforms wherever the switch's
        * type is used, which is java's own definite-completion rule (JLS 15.28.1) doing the work. */
      private def armValue(ss: List[CtStatement], el: CtElement, resT: TypeRepr): Term =
        val (sts, trail)  = stmts(ss)
        val (init, value) = sts.lastOption match
          case Some(t: Term) => (sts.init, unYield(t))
          case _             => (sts, unit(el))
        Tree.Block(init, value, resT, originOf(el), trail)

      /** peel a TAIL `yield` to the value it carries — through a comment wrapper, which is where
        * the trivia harvest puts an arm's own comments. */
      private def unYield(t: Term): Term = t match
        case y: Tree.Yield     => y.value
        case c: Tree.Commented => c.stmt match
          case y: Tree.Yield => c.copy(stmt = y.value)
          case _             => t
        case _                 => t

      /** is this an ENHANCED switch STATEMENT — one java requires to be EXHAUSTIVE (JLS 14.11.2),
        * and therefore one it does NOT fall out of?
        *
        * JLS 14.11.2 gives TWO disjuncts and this asks BOTH, which is a correction: it used to ask
        * the labels alone, on the argument that a selector outside the classic set
        * (`char`/`byte`/`short`/`int`, their boxes, `String`, an enum) admits no constant label at
        * all. JEP 441 is what makes that false — a QUALIFIED ENUM CONSTANT is a constant label at a
        * selector typed as the enum's SUPERTYPE, and it is neither a pattern nor a `null`, so
        * nothing in the label list betrays it. Measured against javac 22.0.2: on
        * `switch (c) { case Coin.HEADS: …; case Coin.TAILS: … }` over a sealed `Currency`, javac
        * calls the statement AFTER the switch an `unreachable statement` and compiles
        * `new MatchException` at the fall-out — so a synthesised `case _ => ()` is a silent §4.4,
        * the exceptional path turned into a no-op.
        *
        * ==THE SELECTOR'S TYPE, AND NOT THE LABEL'S SHAPE==
        *
        * The tempting cheaper test — "does any label read a qualified enum constant" — is WRONG,
        * and also measured: `switch (e) { case E.A: return 1; }` on a selector of the enum's own
        * type compiles, runs and FALLS OUT (JLS 14.11.2's first disjunct is about the selector, and
        * an enum type is inside the classic set however its constants are spelled). Deciding from
        * the label would delete the arm java is exercising there.
        *
        * ==AND `PROVABLY` OUTSIDE, WHICH IS THE HALF THE OLD ARGUMENT HAD RIGHT==
        *
        * Reading the selector's type means RESOLVING it, and `noClasspath` cannot always do that. A
        * fall-out arm dropped because a type failed to resolve is §4.4's defect in the other
        * direction, on every classic switch in a corpus — so [[selectorOutsideClassicSet]] answers
        * `false` wherever it cannot see a declaration, which is exactly the behaviour this question
        * had before it asked.
        *
        * Where it fires, scala's `match` throws `MatchError` where java throws `MatchException`:
        * both throw, which is the honest image of an exhaustiveness java checks and scala cannot. */
      private def isEnhanced(cases: List[CtCase[?]], selector: CtExpression[?]): Boolean =
        cases.exists(_.getCaseExpressions.asScala.exists {
          case _: CtCasePattern      => true
          case l: CtLiteral[?]       => l.getValue == null
          case _                     => false
        }) || selectorOutsideClassicSet(selector)

      /** JLS 14.11.2's classic selector set, by qualified name. A selector typed as one of these is
        * a classic switch however its labels are spelled; an ENUM is the set's sixth member and is
        * asked structurally below, because there is no name to list. */
      private val ClassicSelectorTypes = Set(
        "char", "byte", "short", "int",
        "java.lang.Character", "java.lang.Byte", "java.lang.Short", "java.lang.Integer",
        "java.lang.String")

      /** does the selector's type PROVABLY resolve to something outside [[ClassicSelectorTypes]]?
        *
        * `false` is the answer for everything this cannot see — an absent type, a name that is not
        * in the set but whose declaration does not resolve, a type parameter, an annotation type —
        * and that default is the pre-existing behaviour rather than a fabricated fact (§4.6): it
        * says *this switch keeps the fall-out arm java's classic form has*, which is what every
        * switch in this engine's corpora got before the question was asked at all. The one lookup
        * wrapped is the RESOLUTION, where an absent value is normal under `noClasspath`. */
      private def selectorOutsideClassicSet(selector: CtExpression[?]): Boolean =
        val ref = try Option(selector.getType) catch { case _: Throwable => None }
        ref.exists { r =>
          !r.isPrimitive && !ClassicSelectorTypes.contains(r.getQualifiedName) && {
            val decl = try Option(r.getTypeDeclaration) catch { case _: Throwable => None }
            decl.exists {
              case _: CtEnum[?]                       => false
              case _: CtClass[?] | _: CtInterface[?]  => true
              case _                                  => false
            }
          }
        }

      // ---- expressions ----
      /** the casts the SOURCE wrote, applied innermost-first — each one rendered as the thing java
        * does AT it, which is not always an assertion.
        *
        * `(T) x` is `x.asInstanceOf[T]` for every combination but one, and that one is this row's
        * own sentence: a cast whose OPERAND is a statically-known boxed WRAPPER and whose target is
        * a primitive is a CONVERSION in java — JLS 5.1.8 unboxes at the wrapper's own primitive and
        * 5.1.2 widens from there, so `(double) aLong` is `7.0`. Scala's `asInstanceOf[scala.Double]`
        * on a reference is `unboxToDouble`, which demands a `java.lang.Double` and throws on a
        * `Long`. Rendered as a cast the port asserts where java converted.
        *
        * WHY THE OPERAND HAS TO BE A KNOWN WRAPPER, and why `Object` and `Number` are deliberately
        * NOT in this branch. Java's rule for a reference operand it does not know (JLS 5.5) is a
        * narrowing reference conversion to the EXACT wrapper followed by an unbox — it performs no
        * `Number` dispatch, so `(double) o` on an `Object` holding a `Long` throws in java too, and
        * `asInstanceOf[scala.Double]` throws in exactly the same cells. Probed against javac and
        * scalac 3.8.4 over all 45 of (9 runtime classes x 5 primitives); they agree everywhere.
        * Converting there would be UNFAITHFUL — it would answer where java raises.
        *
        * A same-type unbox (`(int) anInteger`) is left alone for the reason [[coerce]] states: it
        * is `Predef`'s already, and forcing the accessor only perturbs surrounding resolution. */
      private def expr(e: CtExpression[?]): Term =
        val core  = exprNoCast(e)
        val casts = e.getTypeCasts.asScala.toList
        val et0: CtTypeReference[?] = try e.getType catch { case _: Throwable => null }
        // the fold carries the type the term HAS at each step: `e.getType` under the innermost
        // cast, and thereafter the cast below the one being applied.
        casts.foldRight((core, et0)) { case (t, (acc, src)) =>
          (castOf(t, src, acc, e), t: CtTypeReference[?])
        }._1

      /** one source cast, as the conversion or the assertion java performs there. See [[expr]]. */
      private def castOf(target: CtTypeReference[?], src: CtTypeReference[?], acc: Term,
                         e: CtExpression[?]): Term =
        val unboxing = target != null && target.isPrimitive && src != null && !src.isPrimitive &&
          wrapperOf.values.toSet(src.getQualifiedName) &&
          wrapperOf.get(target.getSimpleName).exists(_ != src.getQualifiedName)
        if unboxing then unbox(acc, src.getQualifiedName, target.getSimpleName, e)
        else
          val ct = tpe(target); Tree.Typed(acc, tt(ct, e), ct, originOf(e))

      /** THE TYPE AN EXPRESSION HAS WHERE IT STANDS — after the casts the SOURCE wrote, which is
        * the OUTERMOST one, which is the HEAD of `getTypeCasts`.
        *
        * `e.getType` is Spoon's answer for the expression BEFORE its own casts, and every reader
        * that dispatches on a receiver's or an operand's static type wants the type javac
        * dispatched on — `((AsynchronousAssetLoader) loader).unloadAsync(…)` is the whole reason a
        * source writes one. So the cast list decides, and WHICH END of it is not a matter of taste:
        * [[expr]] folds it with `foldRight`, so the head becomes the OUTER `Tree.Typed` and
        * `(Integer)(Object) o` emits `o.asInstanceOf[Object].asInstanceOf[Integer]` — java's own
        * order, and the head is java's outermost cast.
        *
        * ONE function, six callers (`CLAUDE.md` §4.6's shape, and `ENGINE-LIMITS.md` F8's): the
        * idiom was written six times taking `lastOption`, which is the INNERMOST cast, so every one
        * of them read the type the source had already converted away from — under a comment that
        * said "outermost". A seventh reader would have copied the seventh. Null where Spoon has no
        * answer at all: the callers each decline on that, which is honest rather than fabricating a
        * default (`CLAUDE.md` §4.6).
        *
        * The FROZEN BIR frontend (`SpoonFrontend.typedArg`) holds the same idiom and is left as it
        * is: no measure lane runs that path, so a change there is unmeasurable by construction. */
      private def castType(e: CtExpression[?]): CtTypeReference[?] =
        try e.getTypeCasts.asScala.headOption.getOrElse(e.getType) catch { case _: Throwable => null }

      /** THE EXPRESSION DISPATCH — the wrapper's second entry, symmetrical with [[stmtKind]] and
        * for the same reason. See that method for why it is here and not in the arms. */
      private def exprNoCast(e: CtExpression[?]): Term =
        Lowering.of(SpoonKinds.nameOf(e.getClass), Dispatch.Expression, originOf(e), e)(exprArm(e))

      private def exprArm(e: CtExpression[?])(using Obligations): Term = e match
        case l: CtLiteral[?]      => literal(l)
        case f: CtFieldRead[?]    => fieldAccess(f.getVariable, f.getTarget, e)
        case f: CtFieldWrite[?]   => fieldAccess(f.getVariable, f.getTarget, e)
        // `Outer.this` USED AS A VALUE (`listener.keyTyped(TextField.this, c)` from an inner
        // listener): Scala's bare `this` names the INNERMOST class, so the enclosing instance has
        // to be named explicitly. Carry the enclosing class's symbol; the emitter qualifies it.
        case ta: CtThisAccess[?] if !isOwnThis(ta) && outerThis(ta).isDefined => outerThis(ta).get
        case ta: CtThisAccess[?]  => thisOf(ta, e)
        case v: CtVariableRead[?] => Tree.Ident(resolveVar(v.getVariable), ty(e), originOf(e))
        case v: CtVariableWrite[?] => Tree.Ident(resolveVar(v.getVariable), ty(e), originOf(e))
        case inv: CtInvocation[?] => invocation(inv)
        case cc: CtConstructorCall[?] => ctorCall(cc)
        case a: CtArrayRead[?]  => Tree.ArrayAccess(expr(a.getTarget), expr(a.getIndexExpression), ty(e), originOf(e))
        case a: CtArrayWrite[?] => Tree.ArrayAccess(expr(a.getTarget), expr(a.getIndexExpression), ty(e), originOf(e))
        case na: CtNewArray[?]  => newArray(na)
        case l: CtLambda[?]     => lambda(l)
        case mr: CtExecutableReferenceExpression[?, ?] => methodRef(mr)
        case b: CtBinaryOperator[?] =>
          // BOTH consults happen at EVERY binary operator, `instanceof` included. The catalog
          // attaches them to the NODE KIND, and an arm that asked only at the nodes where it
          // already knew the answer would be discharging its obligation on a condition of its own
          // choosing — which is the shape the wrapper exists to make impossible. Neither predicate
          // touches an operand until it has ruled the kind in, so asking everywhere costs a
          // `getKind` comparison and translates nothing twice.
          val stringified = Obligations.consult(JS.E(14), originOf(b))(stringConcatLeft(b))
          val identity    = Obligations.consult(JS.E(1), originOf(b))(referenceIdentity(b))
          if b.getKind == BinaryOperatorKind.INSTANCEOF then
            b.getRightHandOperand match
              case ta: CtTypeAccess[?] =>
                val tp = tpe(ta.getAccessedType)
                Tree.InstanceOf(expr(b.getLeftHandOperand), tt(tp, b), ty(b), originOf(b))
              // SE16's PATTERN form — `o instanceof String s`, JLS 15.20.2. REFUSED, and the
              // refusal is the whole content of `ENGINE-LIMITS.md` T18: the pattern is not the
              // gap (scala's `case s: T =>` binds perfectly), the SCOPE is. JLS 6.3.1 gives the
              // binding a FLOW scope — `if (!(o instanceof T s)) return; use(s);` puts it in scope
              // AFTER the `if` — so no lexical placement of a `val` is faithful, and a hoisted
              // `var` diverges under CAPTURE, which is a §4.4 defect with no compile error.
              //
              // MARKED rather than thrown, which is the half that changed. The TYPE OPERAND is a
              // position a term-level marker cannot stand in, and that is why this was one of
              // `unsupported`'s sites and cost the whole compilation unit — but the WHOLE
              // `instanceof` is a boolean EXPRESSION, and that shape a term marker takes exactly.
              // The kind, and with it the catalog row, still names the pattern node.
              case p: CtPattern =>
                unlowered(b, s"an `instanceof` PATTERN binding — ${SpoonKinds.nameOf(p.getClass)} " +
                  "(JLS 15.20.2). Java's binding is FLOW-SCOPED (JLS 6.3.1), so no lexical `val` " +
                  "placement is faithful and a hoisted `var` diverges under capture; see " +
                  "ENGINE-LIMITS T18 for the three placements measured", ty(b), about = p)
              case other => unsupported(other, "instanceof right operand")
          else
            stringified.orElse(identity).getOrElse(
              opText(b.getKind).fold(unknownOp(b.getKind, b, ty(b)))(
                op => binApply(op, expr(b.getLeftHandOperand), expr(b.getRightHandOperand), ty(b))))
        case u: CtUnaryOperator[?] =>
          import UnaryOperatorKind.*
          // JS-E02, consulted at every unary operator for the reason above; `incDecOf` answers
          // `scala.None` for the four that are not increments.
          Obligations.consult(JS.E(2), originOf(u))(incDecOf(u)).getOrElse(u.getKind match
            case NOT     => unApply("unary_!", expr(u.getOperand), ty(u))
            case NEG     => unApply("unary_-", expr(u.getOperand), ty(u))
            case POS     => unApply("unary_+", expr(u.getOperand), ty(u))
            case COMPL   => unApply("unary_~", expr(u.getOperand), ty(u))
            // Java has eight unary operators; `incDecOf` above answers four of them and this match
            // the other four, so the default is unreachable TODAY and is not there for Java — it is
            // there for SPOON. `getKind` is a Java enum from a dependency, not a sealed Scala one,
            // so scalac cannot check this match: a Spoon upgrade that adds a kind produces a
            // `MatchError` at some depth of the expression translator, with no origin, no construct
            // name and nothing to classify it by. That is the one failure shape this frontend must
            // not have — an error an agent cannot classify costs it a full investigation (§4.45) —
            // so the default is a MARKER, located and named, and `FrontendBlindSpot` rather than
            // `UnmodelledNodeKind` because the node kind IS dispatched on here. What is missing is
            // one shape of it.
            case other =>
              unlowered(u, s"unary operator kind '$other' — this arm enumerates java's eight and " +
                "the parser produced a ninth", ty(u), Some(UnportableKind.FrontendBlindSpot)))
        // assignment used as a VALUE (`return a = v`, `while ((line = read()) != null)`):
        // Java yields the assigned value, Scala's `=` is Unit — lower to `{ lhs = rhs; lhs }`.
        case a: CtOperatorAssignment[?, ?] =>
          val lhs = expr(a.getAssigned)
          val res = opText(a.getKind).fold(unknownOp(a.getKind, a, ty(a)))(
            op => binApply(op, lhs, expr(a.getAssignment), ty(a)))
          // JS-E04 — the same difference as JS-E03 at the other dispatch, and the same PREDICATE.
          // `compoundNarrow` is one function precisely because this pair is what the catalog splits
          // into two rows: the narrowing is java's implicit cast back to the left-hand type
          // (JLS 15.26.2), and it is owed wherever the assignment happens — the position only
          // decides whether the resulting value is also used. Without it the emitted store is an
          // `int` into a `byte` slot, which is the LOUD half of the row.
          val rhs2 = Obligations.consult(JS.E(4), originOf(a))(compoundNarrow(a))
            .fold(res)(t => Tree.Typed(res, tt(tpe(t), a), tpe(t), originOf(a)))
          val st  = Tree.Assign(lhs, rhs2, unitT, originOf(a))
          Tree.Block(List(st), lhs, ty(a), originOf(a))
        case a: CtAssignment[?, ?] =>
          // Java's assignment-as-EXPRESSION needs the same coercion as the statement form. It did
          // not have it, so a conversion Java made silently was written out on one path and dropped
          // on the other — `data = (this.data = Arrays.copyOf(data, n))` kept the erased
          // `Array[Object]` the argument cast produced, in an `Array[T]` field.
          val lhs = expr(a.getAssigned)
          val rhs = a.getAssignment
          slotConsults(Option(a.getAssigned.getType).map(_ -> rhs).toList, originOf(a))
          val v   = Option(a.getAssigned.getType).map(coerce(_, rhs, expr(rhs))).getOrElse(expr(rhs))
          val st  = Tree.Assign(lhs, toDeclaredTypeParam(a.getAssigned, rhs, v), unitT, originOf(a))
          // JS-E15. This consult ALWAYS fires and that is the honest answer, not a formality: an
          // assignment reaching the EXPRESSION dispatch is by definition one whose value java
          // yields, so the difference applies at every site. Where it did not apply the answer is
          // `st` itself — a plain `Unit` assignment, which is exactly what the statement dispatch
          // emits — so the two branches are the two languages' two forms and neither is dead text.
          Obligations.consult(JS.E(15), originOf(a))(Some(lhs))
            .fold[Term](st)(v2 => Tree.Block(List(st), v2, ty(a), originOf(a)))
        case c: CtConditional[?] =>
          // Java `b ? x : null` typed as the type parameter `V`; Scala infers `x.type | Null`, which
          // won't satisfy a `V` slot. Cast a null branch to the conditional's own type so the ternary
          // stays `V`. Guarded: only when that type resolves (never emit the `?T` unresolved stub).
          val ct = ty(c)
          // JS-E05, and note what this consult does and does not discharge — the comment that used
          // to stand here said the row was `Partial` and that the open half was "the emitter
          // dropping `Tree.If`'s `tpe`", which had been out of date since the row was closed and
          // pointed a reader at the wrong file.
          //
          // The row is `Handled` and this arm is HALF of what handles it. Java COMPUTES the
          // conditional's type (JLS 15.25.2) where scala takes the lub of the branches; the two
          // disagree in exactly two shapes and each has its own answer here. A NULL branch is
          // ascribed to the conditional's own type — that is this consult. Every PRIMITIVE
          // disagreement is a conversion java performed, and it is performed on the OPERAND by
          // [[promotedBranch]], in both directions, which is why nothing is left for the emitter to
          // ascribe. A reference conditional with no null branch is a lub on both sides and needs
          // neither.
          val ascribe = Obligations.consult(JS.E(5), originOf(c))(
            if ct != NoType && condTypeResolves(c) then Some(ct) else scala.None)
          def branch(be: CtExpression[?]): Term =
            val t      = expr(be)
            val isNull = be match { case l: CtLiteral[?] => l.getValue == null; case _ => false }
            if isNull then ascribe.fold(t)(a2 => Tree.Typed(t, tt(a2, be), a2, originOf(be)))
            else promotedBranch(c, be, t)
          Tree.If(expr(c.getCondition), branch(c.getThenExpression), branch(c.getElseExpression), ct, originOf(c))
        case ta: CtTypeAccess[?] => Tree.Literal(Constant.ClassOfC(tpe(ta.getAccessedType)), ty(e), originOf(e))
        case sw: CtSwitchExpression[?, ?] => switchExpr(sw)
        // …and the same for an EXPRESSION. The marker carries the expression's own type, so the
        // tree stays typed and every phase after this one reads the slot exactly as it would
        // have — which is the whole reason the marker is a wrapper rather than a hole.
        case other => unlowered(other, s"expression ${SpoonKinds.nameOf(other.getClass)}", ty(e))

      /** JS-E05's NUMERIC half — JLS §15.25.2's binary numeric promotion, performed ON THE OPERAND.
        *
        * Java COMPUTES a conditional's type; scala takes the lub of its branches, and wherever
        * java's answer is a PRIMITIVE the two disagree BY A CONVERSION. The worked example, and the
        * one that measured it:
        *
        * {{{
        * str.matches("\\d+") ? Long.valueOf(str) : Double.valueOf(str)   // java: a `double`
        * }}}
        *
        * JLS 15.25.2 unboxes both operands, promotes them to `double` and re-boxes the result, so
        * the expression's type really is `Double` and the `Long` branch really does become one.
        * Scala's `if` has no such rule: its type is the lub (`java.lang.Number`) and the branch value
        * stays a `Long`. The engine read java's type correctly and wrote it as a CAST at the
        * enclosing slot, which is the whole error — `java.lang.Long cannot be cast to
        * java.lang.Double`. **A cast is not a conversion** (`ENGINE-LIMITS.md` K17).
        *
        * So the conversion goes where java performed it — on each operand — and the `if` then really
        * HAS the type java says it has, which is also why the emitter has nothing left to ascribe.
        *
        * ==BOTH DIRECTIONS, because scala 3 has no weak conformance==
        *
        * The first cut converted only the NARROWING direction, on the claim that "`if` branches
        * conform weakly, so a widening needs nothing". That is SCALA 2's rule. Scala 3 dropped weak
        * conformance and harmonises only where an EXPECTED type reaches the branches, so with none
        * — a string concatenation, an `Object` slot, a `var` — `if (b) i else d` types as
        * `Int | Double`, the `Int` branch boxes to a `java.lang.Integer`, and java's `double` is
        * gone. Probed on 3.8.4: `b ? 3 : 15.25` prints `3` against java's `3.0`, and the
        * `asInstanceOf[java.lang.Double]` an enclosing slot writes throws on it. The widening cast
        * is redundant wherever the expected type existed and wrong nowhere, because between two
        * statically primitive types `asInstanceOf` is a conversion in both directions.
        *
        * ==Two things this deliberately does not do==
        *
        *   - **it never promotes on its own.** The target is Spoon's own answer for the conditional,
        *     so §15.25.2's bullet 2 — a `byte` operand against a constant `int` representable in
        *     `byte` keeps the conditional at `byte` — holds by construction, and this NARROWS the
        *     constant rather than widening the `byte`. A rule that always promoted would be
        *     unfaithful in exactly that case;
        *   - **it does not touch a REFERENCE conditional.** Java's type there is a lub and scala's is
        *     also a lub; the one shape known to diverge is a `null` branch, which the ascription
        *     beside this call already carries.
        *
        * ==Why the SAME-TYPE unbox happens here and is declined by `coerce`==
        *
        * `coerce` leaves `Integer` → `int` to `Predef.Integer2int`, and forcing `.intValue()` at an
        * argument slot only perturbs the resolution around it. That reasoning needs an EXPECTED type,
        * and a conditional branch has none — the branch is typed on its own and then lubbed. So a
        * `java.lang.Double` operand of a `double` conditional stays boxed, the lub misses java's type
        * by one conversion, and the enclosing coercion asserts a fact that is false. Both operands
        * are converted here, cross-type and same-type alike.
        *
        * ==The operand's type is the one AFTER its own casts==
        *
        * `be.getType` is the type Spoon records for the expression BEFORE the source's own casts,
        * which `expr` applies on top — so the branch is read through [[castType]], whose own doc
        * says which end of `getTypeCasts` is the outermost and why. Read without them,
        * `pole == 0 ? (float) Math.asin(…) : pole * PI * 0.5f` looks like a `double` operand of a
        * `float` conditional and earns a narrowing this pass would emit on top of the one the source
        * already wrote — a third `asInstanceOf[scala.Float]` on a term that is already a `Float`.
        * Measured on libGDX before it was read: every such site's digest moved for a cast that says
        * nothing. Same idiom as every other reader of this question in the file.
        *
        * A type Spoon cannot resolve leaves the branch ALONE, which is honest rather than a
        * fabricated default (`CLAUDE.md` §4.6): it declines to convert, and never asserts a type. */
      private def promotedBranch(c: CtConditional[?], be: CtExpression[?], t: Term): Term =
        val cj = try c.getType catch { case _: Throwable => null }
        val bj = castType(be)
        if cj == null || bj == null || !cj.isPrimitive || cj.getQualifiedName == bj.getQualifiedName then t
        else if !bj.isPrimitive then
          // a boxed operand at a primitive conditional: java UNBOXES it, then widens. Only a wrapper
          // can stand here in valid java — anything else needed a cast the source itself wrote.
          if wrapperOf.values.toSet(bj.getQualifiedName) then unbox(t, bj.getQualifiedName, cj.getSimpleName, be) else t
        else if primRank.contains(bj.getSimpleName) && primRank.contains(cj.getSimpleName) then
          // BOTH DIRECTIONS, and there is no third case: the two are primitive and they differ, so
          // java performed a conversion and the port owes it. The narrowing half is bullet 2's;
          // the WIDENING half was left out on the claim that "`if` branches conform weakly", which
          // is SCALA 2's rule — scala 3 dropped weak conformance, so `if (b) i else d` types as
          // `Int | Double`, the `Int` branch BOXES, and the value java computed as a `double` is a
          // `java.lang.Integer` at run time. An enclosing `asInstanceOf[java.lang.Double]` then
          // throws on a shape as small as `b ? 3 : 15.25`, and a string concatenation over it
          // prints `3` where java prints `3.0`.
          //
          // Redundant where an expected type already reaches the branch, and never WRONG:
          // `asInstanceOf` between two statically primitive types is a CONVERSION in scala, in
          // both directions (JS-E06), so the widening one says exactly what java's promotion did.
          // `primRank` is the guard rather than a direction test — it holds the NUMERIC primitives,
          // and `boolean` is the one primitive no promotion can reach (a `boolean` conditional's
          // operands are both `boolean`, which the same-name test above has already returned on).
          Tree.Typed(t, tt(tpe(cj), be), tpe(cj), originOf(be))
        else t

      /** the conditional's static type is safe to ascribe onto a null branch — a concrete type, or a
        * type parameter that actually resolves in scope (not the `?T` unresolved stub). */
      private def condTypeResolves(c: CtConditional[?]): Boolean =
        (try c.getType catch { case _: Throwable => null }) match
          case null                         => false
          case tp: CtTypeParameterReference => resolveTypeParam(tp.getSimpleName).isDefined
          case _                            => true

      private def literal(l: CtLiteral[?]): Term =
        val c: Constant = l.getValue match
          case null                    => Constant.NullC
          case b: java.lang.Boolean    => Constant.BoolC(b)
          case ch: java.lang.Character => Constant.CharC(ch)
          case s: java.lang.String     => Constant.StringC(s)
          case n: java.lang.Integer    => Constant.IntC(n)
          case n: java.lang.Long       => Constant.LongC(n)
          case n: java.lang.Double     => Constant.DoubleC(n)
          case n: java.lang.Float      => Constant.FloatC(n)
          case n: java.lang.Byte       => Constant.ByteC(n)
          case n: java.lang.Short      => Constant.ShortC(n)
          case other                   => unsupported(l, s"literal ${other.getClass.getSimpleName}")
        Tree.Literal(c, ty(l), originOf(l))

      private def resolveVar(ref: CtVariableReference[?]): SymId =
        val decl = Option(ref.getDeclaration).orNull
        if decl != null && varIds.containsKey(decl) then varIds.get(decl)
        else nameIds.getOrElse(ref.getSimpleName, minter.external("?var$" + ref.getSimpleName, ref.getSimpleName))

      private def newArray(na: CtNewArray[?])(using Obligations): Term =
        val elemT = na.getType match
          case arr: CtArrayTypeReference[?] => tpe(arr.getComponentType)
          case t                            => tpe(t)
        val inits = na.getElements.asScala.toList
        val dims  = na.getDimensionExpressions.asScala.toList
        val et    = tt(elemT, na)
        // An array INITIALISER is a slot like any other: `new Object[]{type, true}` autoboxes in
        // Java, and Scala will not box into an `Array[Object]` on its own. Coerce each element to
        // the component type, exactly as a call argument is coerced to its formal.
        val comp = na.getType match
          case arr: CtArrayTypeReference[?] => arr.getComponentType
          case _                            => null
        def elem(e: CtExpression[?]): Term =
          if comp == null then expr(e) else coerce(comp, e, expr(e))
        // an array INITIALISER is a slot list — JS-G09/G13/G14, exactly as at a call.
        slotConsults(if comp == null then Nil else inits.map(comp -> _), originOf(na))
        // JS-G15 — java FORBIDS `new T[n]` (JLS 15.10.1), so the only generic array creation that can
        // reach this arm is the CAST IDIOM, `(T[]) new Object[n]`. The predicate is therefore the
        // idiom itself — a cast on this creation whose target is an array of something generic — and
        // NOT "the component is a type variable", which javac already made unreachable. What handles
        // it is JS-G13's generality: the idiom is a covariant array store and `coerce`'s `arrayCov`
        // clause is what writes it out.
        val idiomCasts = try na.getTypeCasts.asScala.toList catch { case _: Throwable => Nil }
        Obligations.consult(JS.G(15), originOf(na))(Option.when(
          idiomCasts.collect { case a: CtArrayTypeReference[?] => a.getComponentType }
            .exists(c => c != null && (c.isInstanceOf[CtTypeParameterReference] || isGenericUse(c))))(()))
        if inits.nonEmpty || dims.isEmpty then Tree.NewArray(et, Nil, Some(inits.map(elem)), ty(na), originOf(na))
        else Tree.NewArray(et, dims.map(expr), None, ty(na), originOf(na))

      private def lambda(l: CtLambda[?]): Term =
        val pvs = l.getParameters.asScala.toList.map { p =>
          val pt = tpe(p.getType)
          Tree.ValDef(defineLocal(p, pt), tt(pt, p), None, originOf(p))
        }
        val body =
          if l.getExpression != null then nullToSamResult(l, expr(l.getExpression))
          else if l.getBody != null then blockTerm(l.getBody)
          else unsupported(l, "lambda without body")
        // …and the SAM METHOD's result type where the class file states one (`ENGINE-LIMITS.md` I9).
        // Without it the emitter cannot name the nested `def` that restores java's
        // `return`-leaves-the-LAMBDA, and what it leaves instead is not a compile error but a scala
        // NON-LOCAL RETURN from the enclosing method — valid, green, and something else (M6).
        Tree.Lambda(pvs, body, ty(l), originOf(l), resultTpt = samResultTpt(l))

      /** [[nullToTypeParam]]'s rule at the ONE expression position that has no formal to read.
        *
        * Every other `null` this frontend ascribes sits in an argument list, so the cast is driven
        * by `declFormals(i)`. An EXPRESSION-bodied lambda has no such slot: java takes the body's
        * type from the SAM's RESULT (JLS 15.27.3), and where that result is the target's type
        * variable the emitted `(o: DataHolder) => null` is `Found: Null / Required: T`. `Null`
        * conforms to every reference type and to no ABSTRACT one, which is why this fires for a
        * variable and for nothing else — `x -> null` at a `Function<A, String>` needs no cast and
        * must not get one.
        *
        * The variable is resolved through the TARGET's own instantiation ([[typeArgSubst]]), which
        * is G12's rule at a lambda: `Factory<V>.make(): V` at a `Factory<T>` target says `V := T`
        * exactly, keyed on the DECLARING interface's formals rather than on a name. Where the
        * target says nothing — a raw target, an unreadable declaration, a wildcard actual — the
        * body keeps its bare `null` and its error, because a guessed `T` is §4.6's fabricated
        * fact.
        *
        * And the ACTUAL has to be abstract too, which is where this is narrower than the argument
        * arm rather than a copy of it. `Null` conforms to every reference type, so `Factory<String>`
        * needs nothing and a cast there would be text on every `x -> null` in a corpus for a
        * failure that cannot happen — an over-approximation being the one shape no count can see
        * (§5). The condition is exactly scala's: an ABSTRACT type is what `Null` does not
        * conform to. */
      private def nullToSamResult(l: CtLambda[?], t: Term): Term =
        val isNull = l.getExpression match { case lit: CtLiteral[?] => lit.getValue == null; case _ => false }
        if !isNull then t
        else samAbstracts(l.getType) match
          case one :: Nil =>
            (Option(one.getType), Option(one.getDeclaringType).map(_.getQualifiedName)) match
              case (Some(tp: CtTypeParameterReference), Some(owner)) =>
                actualFor(l.getType, owner, tp.getSimpleName, 8) match
                  case Some(tv: CtTypeParameterReference) if tpNameableHere(tv) =>
                    Tree.Typed(t, tt(tpe(tv), l), tpe(tv), originOf(l))
                  case _ => t
              case _ => t
          case _ => t

      /** What does reference `at` say the variable `tv`, DECLARED BY `owner`, is?
        *
        * `Maker<T> extends Fn<String, V>` and the SAM is `Fn.apply(): R`, so the answer to *what is
        * `R` here* is composed one edge at a time: `Maker`'s `V := T` from the reference, then
        * `Fn`'s `R := V` from the `extends` clause, read through the first map. Spoon's own
        * `TypeAdaptor` is what this replaces, measurably: under `noClasspath` it handed back the
        * interface's own variable un-adapted for BOTH a direct target and an inherited SAM.
        *
        * Fuel-bounded like every other hierarchy walk in this frontend, and it answers `None`
        * wherever a declaration cannot be read — a raw supertype, an arity mismatch, an absent
        * class file. The caller's fallback is the bare `null` and its compile error, which is the
        * honest residue (§4.6). */
      private def actualFor(at: CtTypeReference[?], owner: String, tv: String,
                            fuel: Int): Option[CtTypeReference[?]] =
        def walk(here: CtTypeReference[?], subst: Map[String, CtTypeReference[?]], f: Int): Option[CtTypeReference[?]] =
          if f <= 0 || here == null then scala.None
          else if here.getQualifiedName == owner then subst.get(tv)
          else
            val decl = try Option(here.getTypeDeclaration) catch { case _: Throwable => scala.None }
            val supers = decl.toList.flatMap { d =>
              (try d.getSuperInterfaces.asScala.toList catch { case _: Throwable => Nil }) ++
                (try Option(d.getSuperclass).toList catch { case _: Throwable => Nil })
            }
            supers.iterator.map { s =>
              // `s`'s actuals are written in `here`'s scope, so they are read THROUGH `subst`
              // before they become `s`'s own frame.
              val formals = try Option(s.getTypeDeclaration).map(_.getFormalCtTypeParameters.asScala.toList.map(_.getSimpleName)).getOrElse(Nil)
                            catch { case _: Throwable => Nil }
              val actuals = (try s.getActualTypeArguments.asScala.toList catch { case _: Throwable => Nil })
                .map { case tp: CtTypeParameterReference => subst.getOrElse(tp.getSimpleName, tp); case o => o }
              val frame = if formals.sizeIs == actuals.size then formals.zip(actuals).toMap else Map.empty
              walk(s, frame, f - 1)
            }.collectFirst { case Some(r) => r }
        walk(at, typeArgSubst(at), fuel)

      private def methodRef(mr: CtExecutableReferenceExpression[?, ?])(using Obligations): Term =
        val mid = methodSym(mr.getExecutable)
        val qual: Either[TypeTree, Term] = mr.getTarget match
          case ta: CtTypeAccess[?] => Left(tt(tpe(ta.getAccessedType), mr))
          case t                   => Right(expr(t))
        // JS-G43 — five forms share one java syntax and each is a different scala lambda, so the
        // FRONTEND half of the row is exactly this: carry the reference as its own node rather than
        // guessing a shape here. Always fires, and that is the honest answer — every method
        // reference is one of the five and every one of them needs the discrimination the emitter
        // then performs, off the [[Referent]] this reads.
        Obligations.consult(JS.G(43), originOf(mr))(Some(()))
        Tree.MethodRef(qual, mid, ty(mr), originOf(mr), referentOf(mr.getExecutable))

      /** the referenced executable's `static` modifier and its declared ARITY — read HERE, from the
        * parser, because neither survives to the symbol for an EXTERNAL member (see
        * [[Tree.MethodRef.referent]]).
        *
        * The DECLARATION answers where the parse resolved one, which includes a shadow
        * reconstructed from a class file — `java.util.Objects#isNull`'s `static` is a fact about
        * that class file and `execFlags` reads exactly the modifier java wrote. The REFERENCE is
        * the fallback, and its arity is still exact: a lenient parse erases what a slot SAYS, never
        * how many slots there are. `isStatic` on a reference with no declaration is the one value
        * here that can be a guess, which is why the declaration is asked first. */
      private def referentOf(ex: CtExecutableReference[?]): Referent =
        val decl = Option(ex.getExecutableDeclaration)
        val stat = decl match
          case Some(d) => execFlags(d).isStatic
          case None    => try ex.isStatic catch { case _: Throwable => false }
        // the ARITY is read for BOTH cases, not only the unbound one: a NILARY static reference is
        // the one qualified name scala will not eta-expand, so the emitter needs the number there
        // too (see [[Referent]], `ENGINE-LIMITS.md` G32).
        val n = decl.map(_.getParameters.asScala.size)
          .getOrElse(try ex.getParameters.asScala.size catch { case _: Throwable => 0 })
        if stat then Referent.Static(n) else Referent.Instance(n)

      private def fieldAccess(ref: CtFieldReference[?], target: CtExpression[?], at: CtExpression[?])
                             (using Obligations): Term =
        // JS-C02 / JS-C05 — the same two facts as at an invocation, arriving at a FIELD. A static
        // field reached through a type name is inherited through `extends` AND through `implements`
        // (JLS 9.3), and a static nested CONSTANT reached through a subclass's name is the same
        // question with a nested path on it. Both consulted here, at the one arm both field
        // dispatches reach, and both read off the reference: `getDeclaringType` IS the declarer, so
        // no BFS is re-run to answer a diagnostic.
        val staticRecv = target.isInstanceOf[CtTypeAccess[?]]
        Obligations.consult(JS.C(2), originOf(at))(Option.when(staticRecv && (target match
          case ta: CtTypeAccess[?] =>
            // Through `declaringStaticType` — this row's own evidence symbol — and not through the
            // reference's `getDeclaringType` or its `getFieldDeclaration`. For `C.X` where `X` is
            // `K`'s constant, Spoon's reference reads back the type the SOURCE WROTE and the
            // declaration does not resolve at all, so both answered "not inherited" at exactly the
            // interface-constant shape the row is named for. The BFS is the fact; asking it here
            // costs one extra walk of an inheritance closure, and only at a STATIC field read.
            Option(ta.getAccessedType).exists(a =>
              declaringStaticType(a, ref.getSimpleName).exists(_.getQualifiedName != a.getQualifiedName))
          case _ => false))(()))
        Obligations.consult(JS.C(5), originOf(at))(Option.when(staticRecv)(()))
        if ref.getSimpleName == "class" then
          // `Foo.class` → `classOf[Foo]`: the argument is the ACCESSED type (`Foo`), not the type
          // of the `.class` expression (`java.lang.Class[Foo]`, which is what `ty(at)` gives).
          val accessed = target match { case ta: CtTypeAccess[?] => tpe(ta.getAccessedType); case _ => ty(at) }
          Tree.Literal(Constant.ClassOfC(accessed), ty(at), originOf(at))
        else if ref.getSimpleName == "length" && Option(ref.getDeclaringType).exists(_.isInstanceOf[CtArrayTypeReference[?]]) then
          Tree.ArrayLength(expr(target), ty(at), originOf(at))
        else
          val fid = fieldSym(ref)
          target match
            case ta: CtTypeAccess[?]                 => staticFieldAccess(ta, ref, fid, at) // static (re-qualify if inherited)
            // fields: `super.f`, an outer `Outer.this.f`, and implicit `f` all resolve as a BARE
            // name in Scala (inherited or enclosing). Only an OWN `this.f` needs qualifying.
            case _: CtSuperAccess[?]                 => Tree.Ident(fid, ty(at), originOf(at))
            case null                                => Tree.Ident(fid, ty(at), originOf(at))
            case ta: CtThisAccess[?] if !isOwnThis(ta) =>
              // as for calls: `Outer.this.f` is written precisely when an inherited/own `f` would
              // otherwise win, so the qualification is load-bearing. Bare only when the enclosing
              // instance is not really reachable (a static-nested boundary, or an inherited owner).
              outerThis(ta).map(q => Tree.Select(q, fid, ty(at), originOf(at)))
                .getOrElse(Tree.Ident(fid, ty(at), originOf(at)))
            case _: CtThisAccess[?]                  => Tree.Select(thisTerm(at), fid, ty(at), originOf(at))
            case other =>
              // wildcard/raw receiver whose field type depends on its type vars → read through the
              // ERASED view, exactly as for a call (see `erasedReceiverView`). The READ's type
              // moves with the receiver: `data.type` off `AssetData[Object]` is `Class[Object]`,
              // not the `Class[T]` Spoon reports for the un-erased receiver. Carrying Spoon's type
              // here made the TIR disagree with the Scala the emitter then produced, and every
              // later rule that consults `t.tpe` — the argument coercions especially — silently
              // decided there was nothing to convert.
              erasedFieldReceiver(ref, other) match
                case Some((et, ft)) =>
                  val recv = Tree.Typed(expr(other), tt(et, other), et, originOf(other))
                  Tree.Select(recv, fid, ft, originOf(at))
                case None => Tree.Select(expr(other), fid, ty(at), originOf(at))

      /** A field read/written through a WILDCARD/RAW receiver: just like a call, `assetDesc.type` off
        * an `AssetDescriptor[?]` yields a fresh CAPTURE (`Class[?1.T]`) that unifies with nothing
        * downstream — `addAsset(…, task.assetDesc.type, task.asset)` then wants `?1.T` where the
        * asset is an `Object`. Java reads such a field at the ERASED type; emit that view.
        *
        * Returns BOTH the receiver's erased type and the field's type as seen through it, because
        * the two must be produced together: a `Tree.Select` carrying Spoon's un-erased field type
        * over an erased receiver is a TIR node whose `tpe` the emitted Scala does not have. */
      private def erasedFieldReceiver(ref: CtFieldReference[?], target: CtExpression[?]): Option[(TypeRepr, TypeRepr)] =
        val rt = castType(target)
        if rt == null || rt.isPrimitive || rt.isInstanceOf[CtArrayTypeReference[?]] ||
           rt.isInstanceOf[CtTypeParameterReference] || rt.isInstanceOf[CtWildcardReference] then None
        else
          val formals = try Option(rt.getTypeDeclaration).map(_.getFormalCtTypeParameters.asScala.toList).getOrElse(Nil)
                        catch { case _: Throwable => Nil }
          val actuals = try rt.getActualTypeArguments.asScala.toList catch { case _: Throwable => Nil }
          // a BOUNDED wildcard (`IntMap<? extends V> map`) still gives a usable capture — `map.zeroValue`
          // conforms to `V` — so only a raw use or an UNBOUNDED `?` needs the erased view here.
          val useless = (a: CtTypeReference[?]) => a match
            case w: CtWildcardReference => Option(w.getBoundingType).forall(_.getQualifiedName == "java.lang.Object")
            case _                      => false
          val unknown = formals.nonEmpty && (actuals.isEmpty || actuals.exists(useless))
          val names   = formals.map(_.getSimpleName).toSet
          val declTpe = try Option(ref.getFieldDeclaration).map(_.getType).filter(_ != null)
                        catch { case _: Throwable => None }
          val depends = declTpe.exists(mentionsTypeVarFilled(_, names))
          if unknown && depends then
            // same F-bound treatment as `erasedReceiverView`: an F-bounded class has no erased
            // image, so fill from the enclosing scope's own variables and leave what cannot be
            // named as `?`. Without this a FIELD access through such a receiver still emitted
            // `Node[Node[?, Object, Actor], Object, Actor]`, which fails its own bound.
            def isFB(f: CtTypeParameter): Boolean =
              try Option(f.getSuperclass).exists(b => mentionsTypeVarFilled(b, Set(f.getSimpleName)))
              catch { case _: Throwable => false }
            val anyFB = formals.exists(isFB)
            val erasedArgs = formals.map { f =>
              val nm = if inStatic || !anyFB then scala.None else accessibleTp(f.getSimpleName)
              // on the name-filled path a formal we cannot name must be `?`, not its erasure: the
              // F-bound names its SIBLINGS, so pinning `A` to `Actor` while `N`'s bound still reads
              // `Node[N, V, A]` leaves `N` failing its own bound.
              nm.map(id => TypeRef(NoPrefix, id)).getOrElse {
                if anyFB || isFB(f) then TypeBounds(NoType, NoType) else erasureOfFormal(f, Set.empty, 2)
              }
            }
            val subst      = formals.map(_.getSimpleName).zip(erasedArgs).toMap
            Some((AppliedType(TypeRef(NoPrefix, typeSym(rt)), erasedArgs),
                  declTpe.map(erasedFormal(_, subst)).getOrElse(objectT)))
          else None

      /** A Java static field read through a SUBCLASS (`Rotational3D.TMP_V3`, where `TMP_V3` is declared
        * in an ancestor `DynamicsModifier`) — Scala companion objects don't inherit statics, so resolve
        * the field to its real DECLARING type and qualify by that (`DynamicsModifier.TMP_V3`). Falls back
        * to the written qualifier when the declaring type can't be located. */
      private def staticFieldAccess(ta: CtTypeAccess[?], ref: CtFieldReference[?], fid: SymId, at: CtExpression[?]): Term =
        val name  = ref.getSimpleName
        val ownerT = declaringStaticType(ta.getAccessedType, name)
        ownerT match
          case Some(t) if t.getQualifiedName != ta.getAccessedType.getQualifiedName =>
            val ownerId = minter.external(t.getQualifiedName, simpleName(t.getQualifiedName))
            val fid2    = externalMember(ownerId, name, name)
            Tree.Select(Tree.Ident(ownerId, TypeRef(NoPrefix, ownerId), originOf(at)), fid2, ty(at), originOf(at))
          case _ => Tree.Select(typeTerm(ta, at), fid, ty(at), originOf(at))

      /** the type that DECLARES a static field `name` (source types only; degrades to None on
        * shadow/unresolved types).
        *
        * The walk is over the whole INHERITANCE CLOSURE — superclass AND superinterfaces — not the
        * superclass chain alone, because a java INTERFACE CONSTANT is `static` and is inherited
        * through `implements` (`CLAUDE.md` §1(a)'s own example of the rule). Read up the superclass
        * only, `Impl.MAX` for `interface Consts { int MAX = 7; }` resolves to nothing, the walk
        * declines, and the written receiver — whose companion inherits nothing — is emitted.
        *
        * Breadth-first with the class edge taken before the interface ones, which is java's own
        * precedence: a field declared or inherited through the superclass chain SHADOWS one of the
        * same name reachable through an interface. Two interfaces offering one name is ambiguous in
        * java too, so nothing here has to break that tie — such a program does not compile. */
      private def declaringStaticType(accessed: CtTypeReference[?], name: String): Option[CtType[?]] =
        val seen  = collection.mutable.Set[String]()
        val queue = collection.mutable.Queue[CtType[?]]()
        def decl(r: CtTypeReference[?]): CtType[?] =
          try r.getTypeDeclaration catch { case _: Throwable => null }
        Option(decl(accessed)).foreach(queue.enqueue)
        while queue.nonEmpty do
          val t = queue.dequeue()
          if t != null && seen.add(t.getQualifiedName) then
            if (try t.getFields.asScala.exists(_.getSimpleName == name) catch { case _: Throwable => false }) then return Some(t)
            val parents =
              try Option(t.getSuperclass).toList ++ t.getSuperInterfaces.asScala.toList
              catch { case _: Throwable => Nil }
            parents.map(decl).filter(_ != null).foreach(queue.enqueue)
        None

      private def fieldSym(ref: CtFieldReference[?]): SymId =
        val ownerQ = Option(ref.getFieldDeclaration).flatMap(fd => Option(fd.getDeclaringType)).map(_.getQualifiedName)
          .orElse(Option(ref.getDeclaringType).map(_.getQualifiedName))
          .getOrElse("java.lang.Object")
        val ownerId = minter.external(ownerQ, simpleName(ownerQ))
        externalMember(ownerId, ref.getSimpleName, ref.getSimpleName, info = externalFieldType(ref))

      /** the DECLARED type of an EXTERNAL field, as a class file states it — [[externalSignature]]'s
        * fact for the other kind of member, and read by exactly the same rules.
        *
        * A field is the one member a phase can meet in value position without a call node, so the
        * seam it makes is invisible to everything keyed on `Tree.Apply`: an ANTLR context's
        * `public List<ParseTree> children` really is a `java.util.List`, while the position-blind
        * retyping moved the SELECT node's type to `Buffer` and no check compares the two.
        * `ENGINE-LIMITS.md` K15 states the rule for callees; a field is the same fact one node kind
        * along, and the answer is the same one: ask the class file.
        *
        * Rendered SCOPE-FREE through [[externalSlot]], for the reason stated there — a field typed
        * at the declaring class's own type variable (`Node<N>.parent`) must not bind to whatever
        * `N` the CALLER declares, because an external symbol is interned once and the first
        * reference in the run would otherwise decide it for every other. `NoType` is then "no
        * answer", which is the state every external field was in before this existed.
        *
        * Only for a SHADOW declaration: a field the program declares gets its real type from
        * `fieldDef`, and a second, weaker rendering of the same member is a second truth about it. */
      private def externalFieldType(ref: CtFieldReference[?]): TypeRepr =
        try Option(ref.getFieldDeclaration) match
          case scala.None => NoType // no declaration to read — not evidence of anything
          case Some(fd)   =>
            val shadow = Option(fd.getParent(classOf[CtType[?]])).forall(_.isShadow)
            if !shadow then NoType else externalSlot(try fd.getType catch { case _: Throwable => null })
        catch { case _: Throwable => NoType }

      /** Java's WILDCARD/RAW-receiver calls. When the receiver's static type leaves its arguments
        * unknown (raw use, or wildcards), Scala gives every member access a fresh CAPTURE — so a
        * value read off one such receiver never conforms to a formal of another
        * (`assetDesc.params` : `AssetLoaderParameters[?1.T]` into `asyncLoader.loadAsync`'s
        * `asyncLoader.P`). Java's own view of such a call is the ERASED one, performed unchecked.
        * Emit exactly that: cast the RECEIVER to its erased instantiation and each argument whose
        * declared formal mentions one of the receiver's type variables to that formal's erasure —
        * both use the same erasure rules, so they agree. Declarations keep their wildcards, which
        * is what preserves assignment of concrete generic values.
        *
        * Gated to calls that genuinely DEPEND on the receiver's type variables; a wildcard receiver
        * whose callee ignores them needs no cast. Returns the erased receiver type + the receiver's
        * type-variable names. */
      private def erasedReceiverView(inv: CtInvocation[?]): Option[(TypeRepr, Map[String, TypeRepr], Map[String, TypeRepr])] =
        val ex = inv.getExecutable
        if ex.isConstructor then None
        else inv.getTarget match
          case null => None
          case _: CtSuperAccess[?] | _: CtTypeAccess[?] | _: CtThisAccess[?] => None
          case t =>
            // an explicit CAST is what fixes the static type javac dispatched on
            // (`((AsynchronousAssetLoader) loader).unloadAsync(…)`) — Spoon keeps it beside the
            // expression, whose own type is still the field's, so the outermost cast wins.
            val rt = castType(t)
            // A FIELD read reports the reference's erased view, not the declaration's: `node.parent`
            // of `public N parent` types as the RAW `Node` under noClasspath, which reads as "the
            // arguments are unknown" and triggers an erasure the code never needed — Java's own type
            // for it is simply `N`. The declaration is the honest source, exactly as it is for the
            // raw fill (`atDeclScope`), so consult it and decline when it names a type variable.
            val declaredVar = t match
              case fa: CtFieldAccess[?] =>
                try Option(fa.getVariable.getFieldDeclaration).map(_.getType)
                      .exists(_.isInstanceOf[CtTypeParameterReference])
                catch { case _: Throwable => false }
              case _ => false
            if declaredVar then None
            else if rt == null || rt.isPrimitive || rt.isInstanceOf[CtArrayTypeReference[?]]
               || rt.isInstanceOf[CtTypeParameterReference] || rt.isInstanceOf[CtWildcardReference] then None
            else
              val formals = try Option(rt.getTypeDeclaration).map(_.getFormalCtTypeParameters.asScala.toList).getOrElse(Nil)
                            catch { case _: Throwable => Nil }
              val actuals = rt.getActualTypeArguments.asScala.toList
              val unknown = formals.nonEmpty && (actuals.isEmpty || actuals.exists(_.isInstanceOf[CtWildcardReference]))
              val names   = formals.map(_.getSimpleName).toSet
              val depends =
                try Option(ex.getExecutableDeclaration)
                      .map(_.getParameters.asScala.toList.map(_.getType))
                      .exists(_.exists(p => p != null && mentionsTypeVarFilled(p, names)))
                catch { case _: Throwable => false }
              // Only an F-BOUNDED class needs this: there the erasure has no Scala image at all,
              // so the name-directed fill is the only expressible reading. Everywhere else the
              // erasure is load-bearing and preferring in-scope names by NAME measured 2 -> 9.
              def isFBounded(f: CtTypeParameter): Boolean =
                try Option(f.getSuperclass).exists(b => mentionsTypeVarFilled(b, Set(f.getSimpleName)))
                catch { case _: Throwable => false }
              val anyFBounded = formals.exists(isFBounded)
              // THE VIEW IS DECIDED PER POSITION, because `unknown` is ONE question asked of the
              // WHOLE argument list and then applied at every position. That is exact for a RAW use
              // — nothing is written anywhere — and wrong for the ordinary MIXED one:
              // `Function<? super D, Class<?>>` leaves position 0 unknown and WRITES position 1, and
              // java's own view of `apply` there returns `Class<?>`, not `Object`. Erased at both, a
              // value java handed straight to a `Class<?>` slot arrives needing a conversion java
              // never performed (`Found: Object / Required: Class[?]`).
              //
              // A position is CARRIED only where the source wrote an argument that mentions NO TYPE
              // VARIABLE, and that second conjunct is the blocker rather than a tuning. This call's
              // argument side is THREE readings of one erasure — `eraseDependentArgs` off `subst`,
              // `knownReceiverArgs` off its own substitution, and the erasure `coerceArgsFixed`
              // synthesises at the executable REFERENCE's already-erased formal — and the receiver's
              // view has to AGREE with all three or the two halves of one call disagree. They agree
              // about a variable-free argument, which no substitution can move; they provably do not
              // about one that mentions a variable, which is libGDX `OrderedMap#putAll` measured at
              // 0 -> 1 (`ENGINE-LIMITS.md` G21). Until those three are one derivation, this rule
              // carries only what they cannot disagree about.
              //
              // An F-BOUNDED class is excluded whole: there the arguments discharge EACH OTHER's
              // bounds, and a mixture of written and filled ones discharges nothing.
              def writtenAt(i: Int): Option[TypeRepr] =
                if anyFBounded then scala.None
                else actuals.lift(i).flatMap { a =>
                  TypeShape.of(a) match
                    // exactly what the source left UNKNOWN — this is `unknown`'s own criterion, asked
                    // where java asks it.
                    case TypeShape.Wildcard(_, _, _) => scala.None
                    case _ if mentionsAnyTypeVar(a)  => scala.None
                    case _                           => Some(tpe(a))
                }
              if unknown && depends then
                // An F-bounded formal erases to a WILDCARD here too, for the same reason it does
                // inside `erasedType`: `Node[Node[?, Object, Actor], Object, Actor]` still fails
                // `N <: Node[N,V,A]`, because `Node` is invariant and the argument would have to be
                // the very type being written. Only `?` discharges the bound.
                val namedOf = collection.mutable.Map[String, TypeRepr]()
                val args  = formals.zipWithIndex.map { (f, i) => writtenAt(i).getOrElse {
                  // Prefer the NAME-DIRECTED fill over the erasure. `Tree tree = getTree();
                  // tree.remove(this)` inside `Node<N,V,A>` is raw in Java's own source, and the
                  // erasure has no Scala image at all: `Tree<N extends Node<N,V,?>, V>` admits no
                  // finite argument, since every candidate must equal the type being written and
                  // `Tree` is invariant. But `Tree[N, V]` — the enclosing scope's OWN variables —
                  // discharges the bound by construction, because `N`'s bound is exactly what
                  // `Tree` asks for. It is also the more faithful reading: Java resolved the raw
                  // call against the very instantiation the enclosing class is parameterised by.
                  // Same rule the raw FILL already applies to types (`nameFilledArgs`); this brings
                  // the erased-receiver path into line with it instead of contradicting it.
                  val named = if inStatic || !anyFBounded then scala.None else accessibleTp(f.getSimpleName)
                  named.map { id => val r = TypeRef(NoPrefix, id); namedOf(f.getSimpleName) = r; r }.getOrElse {
                    if anyFBounded || isFBounded(f) then TypeBounds(NoType, NoType)
                    else erasureOfFormal(f, Set.empty, 2)
                  }
                } }
                val subst = formals.map(_.getSimpleName).zip(args).toMap
                Some((AppliedType(TypeRef(NoPrefix, typeSym(rt)), args), subst, namedOf.toMap))
              else None

      /** `(N) this` — the SELF-TYPE conversion at a raw call.
        *
        * `Tree tree = getTree(); tree.remove(this)` inside `Node<N, V, A>`: `Tree.remove` takes an
        * `N`, and `this` is a `Node[N, V, A]`, which is not one. Java accepted it solely because
        * `tree` is raw — and where the receiver is NOT raw, libGDX writes `(N) this` itself, in
        * this very file. So this is Java's own conversion made explicit, not an invention.
        *
        * Restricted to `this`: a general "cast any argument to the named variable" rule reaches
        * arguments that are already correct and measured 1 -> 11. The self-type is the only one
        * whose intent a raw receiver leaves unambiguous. */
      private def selfTypeArgs(
          ex: CtExecutableReference[?], argEs: List[CtExpression[?]], args: List[Term],
          nm: Map[String, TypeRepr],
      ): List[Term] =
        if nm.isEmpty then args
        else
          val ps = try Option(ex.getExecutableDeclaration).map(_.getParameters.asScala.toList.map(_.getType))
                   catch { case _: Throwable => None }
          ps match
            case Some(l) if l.sizeIs == args.size && argEs.sizeIs == args.size =>
              args.zipWithIndex.map { (t, i) =>
                (l(i), argEs(i)) match
                  case (tv: CtTypeParameterReference, _: CtThisAccess[?])
                      if nm.contains(tv.getSimpleName) && nm(tv.getSimpleName) != t.tpe =>
                    val ct = nm(tv.getSimpleName)
                    Tree.Typed(t, tt(ct, argEs(i)), ct, t.origin)
                  case _ => t
              }
            case _ => args

      /** cast each argument whose DECLARED formal mentions a receiver type variable to that
        * formal's erasure, matching the erased receiver the call is now made through. */
      private def eraseDependentArgs(
          ex: CtExecutableReference[?], argEs: List[CtExpression[?]], args: List[Term], subst: Map[String, TypeRepr],
          named: Map[String, TypeRepr] = Map.empty,
      ): List[Term] =
        val names = subst.keySet
        val ps = try Option(ex.getExecutableDeclaration).map(_.getParameters.asScala.toList.map(_.getType))
                 catch { case _: Throwable => None }
        ps match
          case Some(l) if l.sizeIs == args.size && argEs.sizeIs == args.size =>
            args.zipWithIndex.map { (t, i) =>
              val f = l(i)
              if f == null || !mentionsTypeVarFilled(f, names) then t
              else
                val et = erasedFormal(f, subst)
                // a BARE `?` is not a type one can cast to (`asInstanceOf[?]` is a syntax error).
                // It arises when the formal is exactly an F-bounded variable, whose erasure is now
                // the wildcard — and there the cast has nothing to say anyway: the receiver was
                // already erased to `Node[?, …]`, so the argument's own type is what must match it.
                if et.isInstanceOf[TypeBounds] then t
                else Tree.Typed(t, tt(et, argEs(i)), et, t.origin)
            }
          case _ => args

      /** Java's UNCHECKED conversion at an ARGUMENT, when the RECEIVER's instantiation is KNOWN.
        * `influencers.addAll(json.readValue("influencers", Array.class, Influencer.class, map))`:
        * `Array.class` is a RAW class literal, so Java's result is the raw `Array` and the call is
        * accepted unchecked — while we render that result `Array[?]`, which matches none of
        * `addAll`'s overloads. The formal Java actually asked for is `Array<? extends T>` with the
        * receiver's `T`, and the receiver names it (`Array[Influencer]`), so substitute and cast.
        *
        * The complement of [[erasedReceiverView]], which handles the receiver whose arguments are
        * UNKNOWN — the two gates are mutually exclusive. Narrow on both ends: only a formal that
        * mentions a receiver type variable, only an argument our raw fill actually wildcarded, and
        * only when the substituted formal is fully nameable here. */
      private def knownReceiverArgs(inv: CtInvocation[?], argEs: List[CtExpression[?]], args: List[Term]): List[Term] =
        val rt = inv.getTarget match
          case null => null
          case _: CtSuperAccess[?] | _: CtTypeAccess[?] | _: CtThisAccess[?] => null
          case t    => castType(t)
        if rt == null || rt.isPrimitive || rt.isInstanceOf[CtArrayTypeReference[?]] ||
           rt.isInstanceOf[CtTypeParameterReference] || rt.isInstanceOf[CtWildcardReference] then args
        else
          val formals = try Option(rt.getTypeDeclaration).map(_.getFormalCtTypeParameters.asScala.toList).getOrElse(Nil)
                        catch { case _: Throwable => Nil }
          val actuals = rt.getActualTypeArguments.asScala.toList
          // fully KNOWN instantiation: same arity, no wildcards, every variable nameable here
          // A WILDCARD actual is admitted too. It cannot drive the original cast (that one needs a
          // fully known instantiation), but it is exactly what makes the NARROWER argument illegal:
          // `loaders : ObjectMap[Class[?], ObjectMap[String, AssetLoader[?, ?]]]` asks its `put` for
          // an `AssetLoader[?, ?]` while the value at hand is an `AssetLoader[T, P]`. Java converts
          // silently at the wildcard; Scala needs it written. See the per-argument guard below.
          val known = formals.nonEmpty && actuals.sizeIs == formals.size && actuals.forall(tpResolvable)
          val ps = try Option(inv.getExecutable.getExecutableDeclaration).map(_.getParameters.asScala.toList.map(_.getType))
                   catch { case _: Throwable => None }
          (known, ps) match
            case (true, Some(l)) if l.sizeIs == args.size && argEs.sizeIs == args.size =>
              // A FIELD receiver's arguments must be rendered as the FIELD's declaration rendered
              // them. Re-rendering them here let the enclosing method's type parameters feed the
              // raw fill, so `this.loaders`'s value type came out `ObjectMap[String,
              // AssetLoader[T, P]]` — identical to the argument, so no cast was emitted — while
              // the field itself is declared `ObjectMap[String, AssetLoader[?, ?]]` and rejects it.
              val fieldRecv = inv.getTarget.isInstanceOf[CtFieldAccess[?]]
              val subst = formals.map(_.getSimpleName)
                .zip(if fieldRecv then atDeclScope(actuals.map(tpe)) else actuals.map(tpe)).toMap
              val rawElement = actuals.exists(a => try isRawGenericUse(a) catch { case _: Throwable => false })
              args.zipWithIndex.map { (t, i) =>
                val f = l(i)
                if f == null || !mentionsTypeVarBounded(f, subst.keySet) then t
                else substFormal(f, subst) match
                  // `hasWildcard(t.tpe)`: our raw fill wildcarded the ARGUMENT, and the receiver's
                  // known instantiation says what it really is.
                  // `uncheckedFrom(ct, t.tpe)`: the reverse — the SLOT is wildcarded and the
                  // argument is the more precise type. Both are Java's unchecked conversion; only
                  // the direction differs. A bare `?` target is not a type one can cast to.
                  // …or the RECEIVER's own type argument is a RAW use (`Array<AssetDescriptor> deps`).
                  // That is where javac stopped checking: a raw element type accepts any
                  // instantiation, so `deps.add(new AssetDescriptor<TextureAtlas>(…))` is legal java
                  // even though our fill typed the element `AssetDescriptor[ParticleEffect]` — the
                  // name-directed fill having resolved `AssetDescriptor`'s `T` against the enclosing
                  // loader's. Both sides are concrete for us, so java's silent conversion must be
                  // written. (The ARGUMENT is not raw here — spoon fills the diamond — so testing
                  // the argument instead is a no-op; measured.)
                  // …and `uncheckedFrom(t.tpe, ct)`, the ERASED direction: the ARGUMENT is an
                  // `Object`-parameterised view of exactly the slot's type. That is what a chain
                  // through an ERASED RECEIVER produces — `pool.obtain().asInstanceOf[Wrapper[
                  // Object]].initialize(…)` has result type `Wrapper[Object]` where the slot is
                  // `Wrapper[T]` — and it is java's unchecked conversion just as much as the other
                  // two: javac stopped checking at the raw `Pool<Wrapper>` the value came from.
                  // Narrow by construction: `uncheckedFrom` demands the same type CONSTRUCTOR, the
                  // same arity, and every differing argument to be `Object` or a wildcard, which is
                  // precisely the shape of an erased or raw use and of nothing else.
                  case Some(ct) if ct != t.tpe && !ct.isInstanceOf[TypeBounds] &&
                                   (hasWildcard(t.tpe) || uncheckedFrom(ct, t.tpe) ||
                                    uncheckedFrom(t.tpe, ct) || rawElement) =>
                    Tree.Typed(t, tt(ct, argEs(i)), ct, t.origin)
                  case _ => t
              }
            case _ => args

      /** A call THROUGH A TYPE VARIABLE whose bound is a RAW generic (`N extends Node`;
        * `N parent; parent.remove(this)`): Java sees the callee's members ERASED, so it accepts
        * arguments the un-erased Scala signature (`def remove(node: N)`) rejects. Cast each argument
        * whose DECLARED formal is a type parameter to that parameter as resolved HERE. Gated on the
        * receiver being a type variable, so it can never touch an ordinary generic call (where
        * casting to a same-named parameter of the callee would be plain wrong). */
      private def typeVarReceiverArgs(inv: CtInvocation[?], argEs: List[CtExpression[?]], args: List[Term]): List[Term] =
        val recvIsTypeVar = inv.getTarget match
          case null => false
          case t    => (try t.getType catch { case _: Throwable => null }) match
            case tv: CtTypeParameterReference =>
              // only a RAW-generic bound erases the members; a properly applied bound does not.
              val d = try Option(tv.getDeclaration) catch { case _: Throwable => None }
              d.flatMap(x => Option(x.getSuperclass)).exists(isRawGenericUse)
            case _ => false
        if !recvIsTypeVar then args
        else
          val ps = try Option(inv.getExecutable.getExecutableDeclaration).map(_.getParameters.asScala.toList.map(_.getType))
                   catch { case _: Throwable => None }
          ps match
            case Some(l) if l.sizeIs == args.size && argEs.sizeIs == args.size =>
              args.zipWithIndex.map { (t, i) =>
                l(i) match
                  case tp: CtTypeParameterReference => toTypeParam(tp, argEs(i), t)
                  case _                            => t
              }
            case _ => args

      private def invocation(inv: CtInvocation[?])(using Obligations): Term =
        val ex   = inv.getExecutable
        val mid  = methodSym(ex)
        // JS-C01 / JS-C02 — a java `static` is INHERITED by every subclass and through every
        // implemented interface; a scala companion inherits nothing, so `Sub.m()` has to be
        // re-pointed at the type that DECLARES `m`. Consulted at the head of the one arm every
        // invocation reaches, and read off the reference rather than by re-running
        // `declaringStaticType`'s BFS: the executable reference already carries its declaring type.
        // JS-C01 fires wherever the receiver is a TYPE (the position where the difference exists at
        // all) and JS-C02 where that type is not the declarer — java's inheritance actually used.
        val staticRecv = inv.getTarget.isInstanceOf[CtTypeAccess[?]]
        Obligations.consult(JS.C(1), originOf(inv))(Option.when(staticRecv)(()))
        Obligations.consult(JS.C(2), originOf(inv))(Option.when(staticRecv && (inv.getTarget match
          case ta: CtTypeAccess[?] =>
            val written = Option(ta.getAccessedType).map(_.getQualifiedName)
            val decl    = Option(ex.getDeclaringType).map(_.getQualifiedName)
            written.isDefined && decl.isDefined && written != decl
          case _ => false))(()))
        val argEs = inv.getArguments.asScala.toList
        val erasedRecv = erasedReceiverView(inv)
        val recvSubst  = receiverTypeArgs(inv)
        // JS-G22 — a raw member access through an ERASED RECEIVER types the CALL and not only the
        // receiver: java inserted a checkcast on the way back out, and `erasedRecvResult` writes it.
        // Read off the view this arm has just computed, never re-derived (§4.56).
        Obligations.consult(JS.G(22), originOf(inv))(Option.when(erasedRecv.isDefined)(()))
        // JS-G29 / JS-G30 — the two halves of java's inference at a call, consulted TOGETHER because
        // `pinTypeArgs` forks into them and the fork is exactly the catalog's split: a variable some
        // FORMAL mentions is determined by its argument and both languages infer it the same way
        // (G29, the ordinary case), while one no formal mentions is constrained only by its BOUND —
        // which java resolves TO that bound and scala resolves to `Nothing` (G30). Read off the
        // callee's DECLARATION, which is where java read them, and not by re-running the pin.
        val calleeTpNames = try Option(ex.getExecutableDeclaration)
                                  .collect { case m: CtMethod[?] => m.getFormalCtTypeParameters.asScala.toList }
                                  .getOrElse(Nil).map(_.getSimpleName).toSet
                            catch { case _: Throwable => Set.empty[String] }
        val constrained = calleeTpNames.nonEmpty && (try
            Option(ex.getExecutableDeclaration).exists(
              _.getParameters.asScala.exists(p => mentionsTypeVar(p.getType, calleeTpNames)))
          catch { case _: Throwable => false })
        Obligations.consult(JS.G(29), originOf(inv))(Option.when(constrained)(()))
        Obligations.consult(JS.G(30), originOf(inv))(Option.when(calleeTpNames.nonEmpty && !constrained)(()))
        val args0 = erasedRecv match
          // A NAME-FILLED receiver needs no argument erasure at all. The callee's formals are then
          // expressed in the caller's OWN type variables (`addToTree(Tree<N,V>)` against a receiver
          // read as `Node[N, V, Actor]`), and the values at hand already have those types — `this`
          // IS a `Tree[N, V]`. Erasing them re-introduced the mismatch the name-fill just removed.
          case Some((_, subst, named)) if named.isEmpty =>
            eraseDependentArgs(ex, argEs, coerceArgs(ex, argEs, originOf(inv), recvSubst), subst)
          case Some((_, _, nm)) => selfTypeArgs(ex, argEs, coerceArgs(ex, argEs, originOf(inv), recvSubst), nm)
          case None             => coerceArgs(ex, argEs, originOf(inv), recvSubst)
        val o    = originOf(inv)
        // JS-G31. Every arm above may cast an argument to the formal it read; a POLY EXPRESSION is
        // the one argument that has no type to cast FROM, so the call answers for it here, once,
        // after all of them have run. See `polyExpression` for the probe this rests on.
        val args = polyArgsAscribed(ex, argEs,
          polyArgsUncast(argEs, typeVarReceiverArgs(inv, argEs, knownReceiverArgs(inv, argEs, args0)), o))
        val fun: Term =
          if ex.isConstructor then
            // super()/this() delegation — target class ≠ enclosing ⇒ super (Spoon often nulls the target).
            val superCtor = inv.getTarget.isInstanceOf[CtSuperAccess[?]] ||
              Option(ex.getDeclaringType).map(_.getQualifiedName).exists(_ != minter.fullNameOf(classId))
            Tree.Select(if superCtor then superTerm(inv) else thisTerm(inv), mid, NoType, o)
          else inv.getTarget match
            case _: CtSuperAccess[?]  => Tree.Select(superTerm(inv), mid, NoType, o)
            case ta: CtTypeAccess[?]  => Tree.Select(staticCallQualifier(ta, mid, inv), mid, NoType, o)
            // implicit (no target): a BARE reference resolves an own OR an ENCLOSING member
            // (Scala inner classes see the outer's members by simple name). Explicit `this.m`
            // stays qualified — it's used precisely to defeat param/local shadowing.
            case null                                  =>
              shadowedImplicitCall(inv, mid, o).getOrElse(Tree.Ident(mid, NoType, o))
            // `Outer.this.m(…)`. Java resolves a simple name against the INNERMOST type that has
            // such a member, so `CharArray.this.append(cbuf)` inside `CharArrayWriter extends Writer`
            // is qualified precisely because the inherited `Writer.append` would otherwise win.
            // Emitted bare, Scala calls that ambiguous. Keep Java's qualification.
            case ta: CtThisAccess[?] if !isOwnThis(ta) =>
              shadowedImplicitCall(inv, mid, o)
                .orElse(outerThis(ta).map(q => Tree.Select(q, mid, NoType, o)))
                .getOrElse(Tree.Ident(mid, NoType, o))
            case _: CtThisAccess[?]                    => Tree.Select(thisTerm(inv), mid, NoType, o)
            case t =>
              val recv = expr(t)
              // wildcard/raw receiver whose callee depends on its type vars → call through the
              // ERASED view (Java's own), so the formals stop being per-access captures.
              val recv2 = erasedRecv match
                case Some((et, _, _)) => Tree.Typed(recv, tt(et, t), et, originOf(t))
                case None             => recv
              Tree.Select(recv2, mid, NoType, o)
        val app = ascribeUnconstrainedResult(inv,
          Tree.Apply(pinTypeArgs(fun, inv, o), args, mid, erasedResult(args, ty(inv)), o), o)
        erasedRecvResult(inv, erasedRecv, app)

      /** The downcast an ERASED RECEIVER's result needs.
        *
        * Calling through the erased view ([[erasedReceiverView]]) is Java's own move, and Java pays
        * for it the same way at the other end: a result declared in the receiver's type variables
        * comes back ERASED, and every use of it carries an implicit downcast. `OrderedMap<K,V>`'s
        * `putAll(OrderedMap<T, ? extends V> map)` calls `map.get((T) key)` and hands the result
        * straight to `put(K, V)` — through `OrderedMap[Object, Object]` that is an `Object`, and
        * `V` is required. Java inserted the checkcast; this writes it down.
        *
        * Gated on the un-erased result being nameable HERE and actually different, so a callee
        * whose result does not move with the receiver (or one Spoon already types as erased) is
        * untouched. */
      private def erasedRecvResult(
          inv: CtInvocation[?], recv: Option[(TypeRepr, Map[String, TypeRepr], Map[String, TypeRepr])], app: Term,
      ): Term = recv match
        case None => app
        case Some((_, subst, _)) =>
          val declRet = try Option(inv.getExecutable.getExecutableDeclaration)
                              .collect { case m: CtMethod[?] => m.getType }
                        catch { case _: Throwable => None }
          // The un-erased reading comes from the receiver's DECLARED arguments, not from Spoon's
          // type for the call: through a wildcard receiver Spoon reports the CAPTURE (`map.V`),
          // which has no Scala name. `OrderedMap<T, ? extends V> map` says `V ↦ ? extends V`, and
          // the bound is what Java's own checkcast lands on — `put(K, V)` accepts it precisely
          // because every `? extends V` is a `V`.
          val declSubst: Map[String, TypeRepr] =
            try
              val t  = inv.getTarget
              val rt = castType(t)
              val fs = Option(rt.getTypeDeclaration).map(_.getFormalCtTypeParameters.asScala.toList).getOrElse(Nil)
              val as = rt.getActualTypeArguments.asScala.toList
              if fs.sizeIs != as.size then Map.empty
              else fs.map(_.getSimpleName).zip(as.map {
                case w: CtWildcardReference => Option(w.getBoundingType).filter(_ => w.isUpper).orNull
                case a                      => a
              }).collect { case (n, a) if a != null && tpResolvable(a) => n -> tpe(a) }.toMap
            catch { case _: Throwable => Map.empty }
          // A RAW declared result, read through an ERASED receiver, is where the node's type and the
          // emitted scala part company (ENGINE-LIMITS §0). `Wrapper initialize(T, int)` called on
          // `pool.obtain().asInstanceOf[Wrapper[Object]]` EMITS a `Wrapper[Object]` — the receiver
          // cast decided that — while `ty(inv)` renders the raw `Wrapper` through the caller's own
          // name-directed fill and says `Wrapper[T]`. Nothing is cast here, because nothing is
          // wrong with the expression; what is wrong is the type recorded ON it, and every later
          // rule that consults `tpe` then reasons about a type the output does not have. The one
          // that matters is `knownReceiverArgs`, which found argument and slot equal and emitted no
          // unchecked conversion for a conversion java really did perform.
          //
          // `substFormal` cannot answer this: it returns `None` for a raw use with arity > 0, by
          // design, because there is nothing to substitute. The erased instantiation is what the
          // receiver cast already committed to.
          // re-TYPE the call node, emitting nothing: `Apply` is the only shape this path produces.
          def retyped(t: Term, want: Option[TypeRepr]): Term = (t, want) match
            case (a: Tree.Apply, Some(w)) if w != a.tpe => a.copy(tpe = w)
            case _                                      => t
          val rawErasedResult = declRet.filter(d => d != null && !d.isPrimitive && isRawGenericUse(d)).flatMap { d =>
            val arity = formalArity(d)
            Option.when(arity > 0)(AppliedType(TypeRef(NoPrefix, typeSym(d)), List.fill(arity)(objectT)))
          }
          declRet match
            case Some(d) if d != null && !d.isPrimitive && mentionsTypeVarFilled(d, subst.keySet) =>
              substFormal(d, declSubst) match
                case Some(ct) if ct != app.tpe && ct != NoType && !hasWildcard(ct) =>
                  Tree.Typed(app, tt(ct, inv), ct, originOf(inv))
                case _ => retyped(app, rawErasedResult)
            case _ => retyped(app, rawErasedResult)

      /** The result type an ERASED ARGUMENT drags with it.
        *
        * `Arrays.copyOf(T[] a, int n): T[]` handed `data.asInstanceOf[Array[Object]]` — the
        * erasure cast `coerceArgsFixed` inserts for an external array formal — returns an
        * `Array[Object]`, whatever Spoon says the Java expression's type was. Recording Spoon's
        * `Array[T]` on the node makes the TIR assert something the emitted Scala does not have, and
        * then the assignment back into `Array[T]` looks fine to every rule that consults `tpe` and
        * fails in the compiler.
        *
        * Only the erasure WE introduced is modelled, and only where the callee's result actually
        * moves with that argument (`fill(Object[], Object): void` shares nothing, so nothing
        * changes). Deciding it from the emitted argument rather than from the declaration matters:
        * under noClasspath a JDK shadow's formals are not reliable, but what we emitted is. */
      private def erasedResult(args: List[Term], declared: TypeRepr): TypeRepr =
        val erasedArrayArg = args.exists { case Tree.Typed(_, _, at, _) => at == arrayOfObject; case _ => false }
        declared match
          // Decided from the EMITTED argument and Spoon's result type, never from the callee's
          // declaration: `java.util.Arrays` is a shadow under noClasspath and often carries no
          // return type at all, so a declaration-driven rule silently does nothing here.
          case AppliedType(_, List(a)) if erasedArrayArg && isScalaArrayType(declared) && isTypeParamRef(a) =>
            arrayOfObject
          case _ => declared

      /** is this rendered type a reference to a type PARAMETER? (they are minted `<owner>$$<name>`) */
      private def isTypeParamRef(t: TypeRepr): Boolean = t match
        case TypeRef(_, s) => minter.fullNameOf(s).contains("$$")
        case _             => false

      private lazy val arrayOfObject: TypeRepr =
        AppliedType(TypeRef(NoPrefix, minter.external("scala.Array", "Array")), List(objectT))

      /** Java keeps SEPARATE namespaces for variables and methods; Scala does not. `boolean delete =
        * …; cursor = delete(false);` is legal Java — the call still reaches the METHOD `delete` —
        * but in Scala the local hides it ("value delete does not take parameters"). Qualify such an
        * implicit-target call with the instance whose class provides the method, which is exactly
        * what Java resolved (`TextField.this.delete(false)` from an inner listener). Only for
        * INSTANCE methods in a non-static context; anything else keeps the bare name. */
      private def shadowedImplicitCall(inv: CtInvocation[?], mid: SymId, o: Origin): Option[Term] =
        val name = inv.getExecutable.getSimpleName
        val exec = inv.getParent(classOf[CtExecutable[?]])
        val shadowed = !inStatic && exec != null &&
          exec.getElements(new spoon.reflect.visitor.filter.TypeFilter(classOf[CtVariable[?]])).asScala
            .exists(v => !v.isInstanceOf[CtField[?]] && v.getSimpleName == name)
        if !shadowed then None
        else
          // innermost enclosing class that PROVIDES the method (own or inherited) — that is the
          // `this` Java picked.
          var t: CtType[?]      = inv.getParent(classOf[CtType[?]])
          var res: Option[Term] = None
          while t != null && res.isEmpty do
            val provides =
              try t.getAllMethods.asScala.exists(m => m.getSimpleName == name && !m.hasModifier(ModifierKind.STATIC))
              catch { case _: Throwable => false }
            if provides then
              val id = minter.external(t.getQualifiedName, t.getSimpleName)
              res = Some(Tree.Select(Tree.This(id, TypeRef(NoPrefix, id), o), mid, NoType, o))
            else t = t.getDeclaringType
          res

      /** Java resolves a generic call's type arguments (often by inference — e.g. `props.get("k",
        * Integer.class)` binds `T=Integer`); Scala re-infers from the EXPECTED type, which can pick
        * a different `T` (here `Int`, forcing `Class[Int]` on the arg → E007). Pin the Java-resolved
        * arguments as an explicit `m[Targs](...)` so inference matches Java and unboxing conversions
        * (`Integer` → `Int`) apply at the result. Conservative: only when every argument is a fully
        * concrete class type — wildcards / type-variables can be unresolved under noClasspath. */
      private def pinTypeArgs(fun: Term, inv: CtInvocation[?], o: Origin): Term =
        if inv.getExecutable.isConstructor then return fun
        val formals = Option(inv.getExecutable.getExecutableDeclaration).collect {
          case m: CtMethod[?] => m.getFormalCtTypeParameters.size
        }.getOrElse(0)
        val actuals = inv.getActualTypeArguments.asScala.toList
        // Restricted to boxed-primitive wrappers: that is the whole beneficial case — a `Class<T>: T`
        // call whose `T` Scala would mis-infer to the primitive (demanding `Class[Int]`) when Java
        // bound it to the wrapper (`Class[Integer]`). Pinning `T=Integer` fixes it and lets
        // `Predef.Integer2int` unbox the result. Pinning other kinds (raw generics → `Array[?]`,
        // path-dependent types) only disturbs overload resolution, so leave those to free inference.
        if formals > 0 && actuals.nonEmpty && actuals.sizeIs == formals && actuals.forall(isBoxedWrapper)
          && !inv.getArguments.asScala.exists(isPrimitiveClassLiteral)
        then Tree.TypeApply(fun, actuals.map(a => tt(tpe(a), inv)), NoType, o)
        else pinUnconstrainedTypeArgs(fun, inv, o)

      /** A method TYPE PARAMETER that appears in NO FORMAL, at a call that gives it no target type
        * either — `ENGINE-LIMITS.md` G22.
        *
        * {{{
        * <T extends Map<String, ?>> T getRegistry(String name);
        * …
        * assertTrue(context.getRegistry(REGISTRY_FOR).isEmpty());
        * }}}
        *
        * Nothing at the call constrains `T`. Java then instantiates it at its BOUND (JLS 18: with no
        * constraints and no target type, resolution takes the upper bound) and `isEmpty()` resolves;
        * Scala instantiates an unconstrained variable at its LOWER bound and the selection fails with
        * `Found: Nothing / Required: ?{ isEmpty: ? }`. Nothing about the receiver or the retyping is
        * wrong — the two languages disagree about what an unconstrained variable is — so the answer
        * java gave is written down.
        *
        * [[pinTypeArgs]] above is the NEIGHBOURING case and declines here, correctly: it pins what
        * the ARGUMENTS determined, and no argument mentions `T`. The answer here is a fact about the
        * DECLARATION instead, which is what makes it a different rule rather than a widening of that
        * one.
        *
        * Four conditions, and each is a way the pin would be wrong without it:
        *
        *   - **no formal mentions the variable.** One that does is constrained by its argument, and
        *     both languages infer it the same way;
        *   - **the call has no TARGET TYPE.** `Map<String,Integer> m = ctx.getRegistry(k)` gives java
        *     AND scala the target to infer from, and pinning the bound there would emit
        *     `Map[String, ?]` where `Map[String, Integer]` was written. The shape with no target is
        *     the one where scala says `Nothing`: the call standing as the RECEIVER of another
        *     selection, which is exactly where that `Nothing` is then selected from;
        *   - **every variable has a REAL bound.** An unbounded `T` means `T extends Object`, and
        *     pinning that is G24's territory — a bound scala does not read as vacuous — for no gain:
        *     an `Object` receiver has no member worth selecting;
        *   - **the bound mentions no type variable of its own.** An F-bound or a bound naming the
        *     enclosing class's parameter is not a type this call site can write down. */
      private def pinUnconstrainedTypeArgs(fun: Term, inv: CtInvocation[?], o: Origin): Term =
        try
          Option(inv.getExecutable.getExecutableDeclaration).collect { case m: CtMethod[?] => m } match
            case scala.None => fun
            case Some(m) =>
              val fs    = m.getFormalCtTypeParameters.asScala.toList
              val names = fs.map(_.getSimpleName).toSet
              val bounds = fs.map(f => Option(f.getSuperclass)
                .filter(_.getQualifiedName != "java.lang.Object").filterNot(mentionsNamedTypeVar))
              if fs.isEmpty || bounds.exists(_.isEmpty) then fun
              else if m.getParameters.asScala.exists(p => mentionsTypeVar(p.getType, names)) then fun
              else if !isReceiverOfSelection(inv) then fun
              else Tree.TypeApply(fun, bounds.flatten.map(b => tt(tpe(b), inv)), NoType, o)
        catch { case _: Throwable => fun }

      /** G22's pin at the shape its FOURTH condition declines — an F-BOUND — by ascribing the
        * RESULT instead of instantiating the ARGUMENT. `ENGINE-LIMITS.md` G8.7.
        *
        * {{{
        * <B extends ISequenceBuilder<B, T>> B getBuilder();   // in IRichSequence<T>
        * …
        * getBuilder().append(padding).append(this).toSequence()
        * }}}
        *
        * The two pins answer the same disagreement — scala instantiates an unconstrained variable at
        * `Nothing` and a selection on `Nothing` is rejected — and they differ in WHAT can be written
        * down. A type ARGUMENT must satisfy the bound, and no denotable `X` satisfies
        * `X <: ISequenceBuilder<X, T>`; that is `ENGINE-LIMITS.md` G8's expressiveness limit, priced
        * four times. An ASCRIPTION does not have to: the argument still infers `Nothing` (legal —
        * `Nothing` is below every bound), and what the ascription supplies is the TYPE THE SELECTION
        * READS. `ISequenceBuilder[?, T]` is a perfectly ordinary type to write there, and its
        * `append` returns the capture, which is itself an `ISequenceBuilder[capture, T]` — so the
        * whole java chain re-types with nothing filled in.
        *
        * So this is NOT a fifth attempt at G8's fill and must not be read as one. It fires exactly
        * where the fill CANNOT be written, and its conditions are G22's first three unchanged plus
        * two of its own:
        *
        *   - **the RESULT is one of the method's own variables**, which is what makes the ascription
        *     land on the value the selection is about;
        *   - **every named variable in the bound is either the METHOD's own (rendered `?`) or one
        *     THIS scope can write** (`tpNameableHere`). The first is what makes an F-bound
        *     expressible at all; the second is §4.6 — a bound naming a variable only the callee
        *     declares has no honest text, and the call keeps its error. */
      private def ascribeUnconstrainedResult(inv: CtInvocation[?], app: Term, o: Origin): Term =
        try
          Option(inv.getExecutable.getExecutableDeclaration).collect { case m: CtMethod[?] => m } match
            case scala.None => app
            case Some(m) =>
              val fs    = m.getFormalCtTypeParameters.asScala.toList
              val names = fs.map(_.getSimpleName).toSet
              val resultVar = Option(m.getType).collect {
                case tp: CtTypeParameterReference if !tp.isInstanceOf[CtWildcardReference] && names(tp.getSimpleName) => tp
              }
              val bound = resultVar
                .flatMap(tp => fs.find(_.getSimpleName == tp.getSimpleName))
                .flatMap(f => Option(f.getSuperclass))
                .filter(_.getQualifiedName != "java.lang.Object")
              // ONLY where the type-argument pin declined, so one seam has one mechanism: a bound
              // with no named variable in it is G22's and is already answered there.
              if bound.isEmpty || !bound.exists(mentionsNamedTypeVar) then app
              else if m.getParameters.asScala.exists(p => mentionsTypeVar(p.getType, names)) then app
              else if !isReceiverOfSelection(inv) then app
              else
                // a variable the DECLARING TYPE owns is resolved through the RECEIVER's own
                // instantiation, exactly as G12 does at an argument: `IRichSequence<T>`'s `T`, read
                // from a `this` of type `IRichSequenceBase<T>`, IS this scope's `T` — a different
                // DECLARATION, so `sameVarInScope` alone answers no and would decline the whole
                // rule on the shape it exists for.
                val recvT  = try Option(inv.getTarget).map(castType).orNull catch { case _: Throwable => null }
                val ownerQ = Option(m.getDeclaringType).map(_.getQualifiedName)
                val viaRecv: String => Option[CtTypeReference[?]] = n =>
                  ownerQ.flatMap(q => if recvT == null then scala.None else actualFor(recvT, q, n, 8))
                wildcardOwnVars(bound.get, names, viaRecv) match
                  case Some(t)    => Tree.Typed(app, tt(t, inv), t, o)
                  case scala.None => app
        catch { case _: Throwable => app }

      /** the bound, with the METHOD's own variables rendered `?` and every other one required to be
        * writable here. `None` where one is not — see [[ascribeUnconstrainedResult]].
        *
        * The WILDCARD arm comes first for G22's own reason: Spoon's `CtWildcardReference` EXTENDS
        * `CtTypeParameterReference`, so a variable arm above it claims every `?`. */
      private def wildcardOwnVars(r: CtTypeReference[?], own: Set[String],
                                  viaRecv: String => Option[CtTypeReference[?]]): Option[TypeRepr] = r match
        case null                   => scala.None
        case w: CtWildcardReference => Some(tpe(w))
        case tp: CtTypeParameterReference =>
          if own(tp.getSimpleName) then Some(TypeBounds(NoType, NoType))
          else if tpNameableHere(tp) then Some(tpe(tp))
          else viaRecv(tp.getSimpleName).filter(tpNameableHere).map(tpe)
        case other =>
          val args = try other.getActualTypeArguments.asScala.toList catch { case _: Throwable => Nil }
          if args.isEmpty then Some(tpe(other))
          else
            val mapped = args.map(a => wildcardOwnVars(a, own, viaRecv))
            if mapped.exists(_.isEmpty) then scala.None
            else Some(AppliedType(TypeRef(NoPrefix, typeSym(other)), mapped.flatten))

      /** does this type mention a NAMED type variable — one an F-bound or an enclosing class
        * declares — as opposed to a WILDCARD, which is writable anywhere?
        *
        * `mentionsAnyTypeVar` cannot answer it: Spoon's `CtWildcardReference` EXTENDS
        * `CtTypeParameterReference`, so its `case _: CtTypeParameterReference => true` claims every
        * `?` as a variable and the wildcard arm below it is dead. `Map<String, ?>` is the bound this
        * pin exists for, so answering "yes" there declines the whole rule. */
      private def mentionsNamedTypeVar(tr: CtTypeReference[?]): Boolean = tr match
        case null                         => false
        case w: CtWildcardReference       => Option(w.getBoundingType).exists(mentionsNamedTypeVar)
        case _: CtTypeParameterReference  => true
        case arr: CtArrayTypeReference[?] => mentionsNamedTypeVar(arr.getComponentType)
        case r => try r.getActualTypeArguments.asScala.exists(mentionsNamedTypeVar) catch { case _: Throwable => false }

      /** does this invocation stand as the RECEIVER of another member access — the one position that
        * gives its result no expected type at all, and the one where scala's `Nothing` is then
        * selected from? See [[pinUnconstrainedTypeArgs]]. */
      private def isReceiverOfSelection(inv: CtInvocation[?]): Boolean =
        try inv.getParent match
          case p: CtInvocation[?]   => p.getTarget eq inv
          case p: CtFieldAccess[?]  => p.getTarget eq inv
          case _                    => false
        catch { case _: Throwable => false }

      /** `int.class` etc. — Java types a primitive class literal as `Class<Integer>` (boxed), but we
        * emit it as `classOf[scala.Int]` (`Class[Int]`). Baseline inference binds a `Class<T>` param's
        * `T` to the primitive and matches; pinning `T` to the boxed wrapper would break that. So a
        * call carrying one of these must keep inference free — don't pin its type arguments. */
      private def isPrimitiveClassLiteral(e: CtExpression[?]): Boolean = e match
        case fr: CtFieldRead[?] if fr.getVariable.getSimpleName == "class" =>
          fr.getTarget match
            case ta: CtTypeAccess[?] => try ta.getAccessedType.isPrimitive catch { case _: Throwable => false }
            case _                   => false
        case _ => false

      private val boxedWrappers = Set(
        "java.lang.Integer", "java.lang.Long", "java.lang.Short", "java.lang.Byte",
        "java.lang.Character", "java.lang.Boolean", "java.lang.Float", "java.lang.Double")
      private def isBoxedWrapper(t: CtTypeReference[?]): Boolean =
        try boxedWrappers(t.getQualifiedName) catch { case _: Throwable => false }

      private def ctorCall(cc: CtConstructorCall[?])(using Obligations): Term =
        // A RAW `new` is the one place the inherited instantiation must NOT fill: the constructor
        // ARGUMENTS decide the parameter there. `new AssetDescriptor(name, TextureAtlas.class)`
        // inside `BitmapFontLoader extends …<BitmapFont, …>` is a `TextureAtlas` descriptor, not a
        // `BitmapFont` one — java resolved it from the argument, having erased the constructor.
        // (Suppressing the inherited fill for whole method BODIES instead measured 36 -> 59; local
        // declarations there genuinely do need it to match the signatures they feed.)
        val savedNoInherit = noInheritFill
        noInheritFill = true
        val t    = tpe(cc.getType)
        noInheritFill = savedNoInherit
        val cid  = methodSym(cc.getExecutable)
        val argEs = cc.getArguments.asScala.toList
        // JS-G31, as at an invocation — the constructor's argument arms are three more of the same
        // family, and a `new` takes a lambda exactly as a call does. The row ATTACHES at the
        // invocation dispatch and this consult is recorded without being owed, which is the honest
        // shape: `Attaches` holds one surface, and a row nothing attaches here would still be a row
        // this arm had considered.
        val args = polyArgsAscribed(cc.getExecutable, argEs, polyArgsUncast(
          argEs, appliedCtorArgs(cc, argEs, rawCtorArgs(cc, argEs, coerceArgs(cc.getExecutable, argEs, originOf(cc)))), originOf(cc)))
        // `CtNewClass` IS a `CtConstructorCall` — the anonymous body hangs off the subtype, and
        // reading only the supertype is what silently dropped every one of them.
        val anon = cc match
          case nc: CtNewClass[?] => anonClass(nc, classId, varScope)
          case _                 => None
        // JS-C31 — anonymous class construction and capture; and JS-C17 — DOUBLE-BRACE
        // INITIALISATION, which is that construct plus an instance initialiser and nothing else.
        // Consulted here rather than inside `anonClass`, because `anonClass` returns `None` for a
        // `CtNewClass` with no body and a row consulted only where it fires is a row whose consult
        // count says nothing. A plain `CtConstructorCall` records both without being owed them —
        // the attachment is at `CtNewClass`, which is the kind that carries the body.
        Obligations.consult(JS.C(31), originOf(cc))(Option.when(anon.isDefined)(()))
        Obligations.consult(JS.C(17), originOf(cc))(Option.when(cc match
          case nc: CtNewClass[?] =>
            Option(nc.getAnonymousClass).exists(!_.getAnonymousExecutables.isEmpty)
          case _ => false)(()))
        // JS-G10 — a RAW anonymous class WITH a body, which is REFUSED rather than approximated
        // (`ENGINE-LIMITS.md` G10): without a body scala infers the argument from the expected type,
        // and with one the anonymous class's type is fixed, so a raw use gives `Parent[Nothing]` and
        // naming the argument does not help either. Consulted at the kind that carries the body, so
        // the refusal is a decision the coverage lane can count rather than a silence.
        Obligations.consult(JS.G(10), originOf(cc))(Option.when(cc match
          case nc: CtNewClass[?] => anon.isDefined && isRawGenericUse(nc.getType)
          case _                 => false)(()))
        Tree.Apply(Tree.New(tt(t, cc), t, originOf(cc), anon), args, cid, t, originOf(cc))

      /** A RAW constructor call — `return new Values(this)` inside `ArrayMap<K,V>`, where
        * `Values<V>(ArrayMap<Object,V> map)`. Java checks a raw `new`'s arguments against the ERASED
        * constructor, so `this : ArrayMap<K,V>` passes unchecked. We render the raw type name-FILLED
        * from the in-scope parameters (`new Values[V](…)`), which re-imposes the un-erased formal —
        * so the arguments have to be filled by the SAME name-directed rule, or the two halves of one
        * raw use disagree. Only for formals that mention a type variable resolving here. */
      private def rawCtorArgs(cc: CtConstructorCall[?], argEs: List[CtExpression[?]], args: List[Term]): List[Term] =
        if !isRawGenericUse(cc.getType) then args
        else
          val ps = try Option(cc.getExecutable.getExecutableDeclaration).map(_.getParameters.asScala.toList.map(_.getType))
                   catch { case _: Throwable => None }
          ps match
            case Some(l) if l.sizeIs == args.size && argEs.sizeIs == args.size =>
              val specialised = rawCtorSpecialisation(cc, l, argEs, args)
              args.zipWithIndex.map { (t, i) =>
                val f = l(i)
                if f == null || !isGenericUse(f) || !mentionsAnyTypeVar(f) || !tpAccessibleHere(f) then
                  specialised.get(i).fold(t)(ct => Tree.Typed(t, tt(ct, argEs(i)), ct, t.origin))
                else
                  val ct = tpe(f)
                  if ct == t.tpe || hasWildcard(ct) then t else Tree.Typed(t, tt(ct, argEs(i)), ct, t.origin)
              }
            case _ => args

      /** SPECIALISE the erased arguments of a raw constructor call, rather than erasing the precise
        * ones.
        *
        * `new AssetDescriptor(data.filename, data.type, parameter)` inside a scope with no `T`:
        * argument 2 is read through an erased receiver, so it arrives as `Class[Object]` and pins
        * `T = Object`; argument 3 is a `ParticleEffectParameter`, so Scala rejects the call. Java
        * checked none of it — the constructor is raw — but SOME instantiation has to be chosen, and
        * the one Java means is recoverable: argument 3's own supertype chain reaches
        * `AssetLoaderParameters<ParticleEffect>`, giving `T = ParticleEffect`.
        *
        * Casting the other way (the precise argument DOWN to `AssetLoaderParameters[Object]`) was
        * tried under three separate gates and measured 23, 5 and 43 errors — see
        * ENGINE-LIMITS.md G13. It destroys the only information at the call site that says what the
        * instantiation is. This direction keeps it: the ERASED argument is cast UP to the binding
        * the precise one implies, which is exactly the unchecked conversion javac performed.
        *
        * Deliberately narrow: one class type parameter, a binding found in exactly one place, and
        * only arguments currently sitting AT the erasure are touched. Returns index -> cast target.
        */
      private def rawCtorSpecialisation(
          cc: CtConstructorCall[?], l: List[CtTypeReference[?]], argEs: List[CtExpression[?]], args: List[Term],
      ): Map[Int, TypeRepr] =
        val clsFormals = try Option(cc.getType.getTypeDeclaration)
                               .map(_.getFormalCtTypeParameters.asScala.toList).getOrElse(Nil)
                         catch { case _: Throwable => Nil }
        if clsFormals.sizeIs != 1 then Map.empty
        else
          val v = clsFormals.head.getSimpleName
          val erased = erasureOfFormal(clsFormals.head, Set.empty, 2)
          // where does the class variable sit inside a formal, and what does the ACTUAL argument
          // bind it to? Only `G<...V...>` shapes; the argument's own supertype chain supplies it.
          def bindingFrom(f: CtTypeReference[?], e: CtExpression[?]): Option[CtTypeReference[?]] =
            val idx = f.getActualTypeArguments.asScala.toList.indexWhere {
              case tv: CtTypeParameterReference => tv.getSimpleName == v
              case _                            => false
            }
            if idx < 0 then None
            else
              val head = f.getQualifiedName
              def walk(t: CtTypeReference[?], depth: Int): Option[CtTypeReference[?]] =
                if t == null || depth <= 0 then None
                else if t.getQualifiedName == head then
                  t.getActualTypeArguments.asScala.toList.lift(idx)
                    .filterNot(a => a == null || a.isInstanceOf[CtWildcardReference] ||
                                    a.isInstanceOf[CtTypeParameterReference])
                else
                  val ups = Option(t.getSuperclass).toList ++ t.getSuperInterfaces.asScala.toList
                  ups.iterator.flatMap(u => walk(u, depth - 1)).nextOption()
              try walk(e.getType, 6) catch { case _: Throwable => None }

          val bindings = l.zipWithIndex.flatMap { (f, i) =>
            if f == null then Nil else bindingFrom(f, argEs(i)).map(b => tpe(b)).toList
          }.distinct
          bindings match
            case List(b) if b != erased =>
              // only rewrite the arguments that are AT the erasure — those are the ones whose own
              // rendering lost the instantiation and would otherwise pin `T` to `Object`.
              l.zipWithIndex.flatMap { (f, i) =>
                if f == null || !mentionsTypeVarFilled(f, Set(v)) then Nil
                else
                  val at = erasedFormal(f, Map(v -> erased))
                  if at != args(i).tpe then Nil
                  // `erasedFormal` resolves `subst` only for a BARE type variable; inside
                  // `Class<T>` it erases the nested `T` regardless, so it hands back the same
                  // `Class[Object]` we are trying to move away from. Substitute on the RENDERED
                  // type instead — sound precisely because this arm already established that the
                  // argument sits AT the erasure, so every `Object` in it stands for `v`.
                  else List(i -> substRepr(at, erased, b))
              }.toMap
            case _ => Map.empty

      /** An APPLIED generic constructor call whose argument Java unchecked-converted.
        *
        * `new AssetDescriptor<T>(data.filename, data.type)` inside `ResourceData<T>`, where the
        * loop variable `data` is a RAW `AssetData`: Java reads `data.type` at the ERASED `Class`
        * and converts it to `Class<T>` without a check. We read it through the erased receiver view
        * — `Class[Object]`, which IS its static type — so Scala then needs the conversion Java made
        * implicitly, written out.
        *
        * The target is the declared formal with the class's own parameters replaced by the call's
        * EXPLICIT type arguments (`Class<T>` ↦ `Class[T]`, `T` being `ResourceData`'s). Without that
        * substitution the formal names a variable that exists only inside the callee, which is
        * exactly why `coerceArgsFixed`'s `uncheckedGeneric` declines these: it would render `?T`.
        *
        * The raw counterpart is [[rawCtorArgs]]; this is the applied one. Gated on the ARGUMENT
        * mentioning a raw generic, so it fires only where Java itself stopped checking — an ordinary
        * subtype argument is left alone. */
      private def appliedCtorArgs(cc: CtConstructorCall[?], argEs: List[CtExpression[?]], args: List[Term]): List[Term] =
        val actuals = try cc.getType.getActualTypeArguments.asScala.toList catch { case _: Throwable => Nil }
        val formals = try Option(cc.getType.getTypeDeclaration)
                            .map(_.getFormalCtTypeParameters.asScala.toList.map(_.getSimpleName)).getOrElse(Nil)
                      catch { case _: Throwable => Nil }
        val ps = try Option(cc.getExecutable.getExecutableDeclaration).map(_.getParameters.asScala.toList.map(_.getType))
                 catch { case _: Throwable => None }
        if actuals.isEmpty || actuals.sizeIs != formals.size || !tpAccessibleHere(cc.getType) then args
        else ps match
          case Some(l) if l.sizeIs == args.size && argEs.sizeIs == args.size =>
            val subst = formals.zip(actuals.map(tpe)).toMap
            args.zipWithIndex.map { (t, i) =>
              val f  = l(i)
              // the same expression kinds `uncheckedGeneric` refuses: a class literal types as raw
              // `Class` yet emits as `classOf[X]`, and casting a literal/lambda/array-initialiser
              // only destroys the inference it feeds.
              val bad = argEs(i) match
                case fr: CtFieldRead[?] => fr.getVariable.getSimpleName == "class"
                case e                  => polyExpression(e) || e.isInstanceOf[CtLiteral[?]] ||
                                           e.isInstanceOf[CtNewArray[?]] || e.isInstanceOf[CtConditional[?]]
              if f == null || bad || !mentionsAnyTypeVar(f) then t
              else substFormal(f, subst) match
                case Some(ct) if ct != t.tpe && !hasWildcard(ct) => Tree.Typed(t, tt(ct, argEs(i)), ct, t.origin)
                case _                                           => t
            }
          case _ => args

      /** SymId of a called executable — via its declaration (keyed identically to how we
        * define our own methods, so call sites and defs share one symbol) or, for
        * unresolved externals, by its reference. */
      private def methodSym(ex: CtExecutableReference[?]): SymId =
        Option(ex.getExecutableDeclaration) match
          case Some(decl) =>
            val (q, s) = declType(decl)
            val ownerId = minter.external(q, s)
            val nm      = if decl.isInstanceOf[CtConstructor[?]] then "<init>" else decl.getSimpleName
            externalMember(ownerId, nm + erasedSig(decl), nm, descriptorOf(decl), externalSignature(decl))
          case None =>
            val ownerQ  = Option(ex.getDeclaringType).map(_.getQualifiedName).getOrElse("java.lang.Object")
            val ownerId = minter.external(ownerQ, simpleName(ownerQ))
            val nm      = if ex.isConstructor then "<init>" else ex.getSimpleName
            val sig     = ex.getParameters.asScala.toList.map(p => scala.util.Try(p.getQualifiedName).getOrElse("?")).mkString(",")
            // NO DESCRIPTOR, deliberately. With no declaration this is the REFERENCE's formals,
            // which a lenient parse erases systematically (`<T> void m(T)` reads `m(Object)`), so
            // recording them would manufacture a precise-looking key that names the wrong overload.
            // This is the design's admitted residue: the failure is LOUD at bind time — an unbound
            // key naming a real member — instead of a silent degrade to arity at match time.
            externalMember(ownerId, s"$nm($sig)", nm)

      private def declType(decl: CtExecutable[?]): (String, String) = decl match
        case tm: CtTypeMember if tm.getDeclaringType != null => (tm.getDeclaringType.getQualifiedName, tm.getDeclaringType.getSimpleName)
        case _ =>
          val t = decl.getParent(classOf[CtType[?]])
          if t != null then (t.getQualifiedName, t.getSimpleName) else ("java.lang.Object", "Object")

      private def typeTerm(ta: CtTypeAccess[?], at: CtElement): Term =
        val q  = ta.getAccessedType.getQualifiedName
        val id = minter.external(q, simpleName(q))
        Tree.Ident(id, TypeRef(NoPrefix, id), originOf(at))

      /** T14 — the receiver of a STATIC CALL, which is the member's DECLARING type and not the type
        * the source wrote.
        *
        * `java.time.ZoneOffset.systemDefault()` is ordinary java: `systemDefault` is declared
        * `static` on `ZoneId`, `ZoneOffset extends ZoneId`, and java lets a static be named through
        * ANY subclass. Scala companion objects inherit nothing from each other, so the same text
        * emitted verbatim is `value systemDefault is not a member of object java.time.ZoneOffset`,
        * every time — 20 errors on one library's suite from a single upstream idiom. The
        * `staticFieldAccess` above is the same fact arriving at a FIELD; this is its other half.
        *
        * Read off the SYMBOL'S OWNER, never off the written name (`CLAUDE.md` §4.56): `methodSym`
        * derives that owner from the resolved executable's own declaration, so it IS the declaring
        * type wherever the parse resolved one, and is the written type — hence a no-op here — where
        * it did not. Java resolved the member statically, so this is exact for the same reason
        * §4.55's renames are: the reference already points at the symbol java chose.
        *
        * It re-qualifies for an IN-PROGRAM parent too, where `TirEmitter.classDef`'s companion
        * re-export would also have delivered the name. That is deliberate, not redundant: naming
        * the declaring type is what the java means, one mechanism covers both, and the two cannot
        * disagree — an inaccessible declaring type is equally unnameable by the re-export, which is
        * emitted as `export <declaring>.*` in the very same file. */
      private def staticCallQualifier(ta: CtTypeAccess[?], mid: SymId, at: CtElement): Term =
        val written  = ta.getAccessedType.getQualifiedName
        val declaring = minter.ownerOf(mid)
        if declaring != SymId.None && minter.fullNameOf(declaring) != written then
          Tree.Ident(declaring, TypeRef(NoPrefix, declaring), originOf(at))
        else typeTerm(ta, at)

      // operators as `recv.op(args)` — the quotes.reflect shape (no dedicated node).
      private def opId(op: String): SymId = minter.external("scala.<op>#" + op, op)
      /** `i++`/`i--` on a byte/short/char narrows (`i = (short)(i + 1)`) — cast the result back. */
      private def incNarrow(opnd: CtExpression[?], res: Term): Term =
        val ot = try opnd.getType catch { case _: Throwable => null }
        if ot != null && ot.isPrimitive && Set("byte", "short", "char").contains(ot.getSimpleName)
        then Tree.Typed(res, tt(tpe(ot), opnd), tpe(ot), originOf(opnd)) else res

      private def isStringConcat(b: CtBinaryOperator[?]): Boolean =
        try b.getType.getQualifiedName == "java.lang.String" catch { case _: Throwable => false }
      private def isStringTyped(e: CtExpression[?]): Boolean =
        try e.getType.getQualifiedName == "java.lang.String" catch { case _: Throwable => false }
      /** `java.lang.String.valueOf(t)` — make a non-String operand a String for concatenation. */
      private def stringify(t: Term, el: CtElement): Term =
        val strSym = minter.external("java.lang.String", "String")
        val vSym   = minter.external("java.lang.String#valueOf", "valueOf", strSym)
        Tree.Apply(Tree.Select(Tree.Ident(strSym, TypeRef(NoPrefix, strSym), originOf(el)), vSym, NoType, originOf(el)),
          List(t), vSym, TypeRef(NoPrefix, strSym), originOf(el))

      /** Java's `==` between REFERENCE types is identity; scala's `==` is `equals`.
        *
        * Every one of these is a silent semantic change, and inside an `equals` implementation it
        * is an infinite recursion: `LongArray.equals` opens with java's `if (object == this)`,
        * which as scala `==` calls `equals` again. The suite found it on the first run — no
        * compiler ever would have. 151 sites in gdx core; the engine emitted `eq` at none of them.
        *
        * `eq` is the faithful operator and it is right for the cases that look like exceptions too:
        * java compares boxed wrappers, enum constants and interned Strings by identity as well,
        * and `==` would quietly answer a different question for each.
        *
        * Skipped when either side is `null` — scala's `x == null` already IS a reference check and
        * reads better — and when either static type is PRIMITIVE, where `==` is value equality in
        * both languages. `Any`-typed operands (java's `equals(Object)` parameter, which scala must
        * render `equals(Any)`) go through `AnyRef`, since `eq` lives there. */
      /** JS-E14's PREDICATE: java string concatenation with a NON-`String` left operand
        * (`obj + "s"`). Scala has no `+` on `obj`, so the left is stringified
        * (`String.valueOf(obj) + "s"`). `scala.None` — nothing to do — for every other operator,
        * and the kind is ruled in before either operand is translated. */
      private def stringConcatLeft(b: CtBinaryOperator[?]): Option[Term] =
        if b.getKind == BinaryOperatorKind.PLUS && isStringConcat(b) && !isStringTyped(b.getLeftHandOperand)
        then Some(binApply("+", stringify(expr(b.getLeftHandOperand), b), expr(b.getRightHandOperand), ty(b)))
        else scala.None

      /** JS-E02's PREDICATE: `++`/`--` in either position, which java evaluates to the value BEFORE
        * the update for the postfix forms. `Tree.IncDec` carries the distinction; the emitter is
        * what renders `{ val p = x; x += 1; p }` rather than `{ x += 1; x }`. */
      private def incDecOf(u: CtUnaryOperator[?]): Option[Term] =
        import UnaryOperatorKind.*
        u.getKind match
          case POSTINC => Some(Tree.IncDec(expr(u.getOperand), "+", post = true, ty(u), originOf(u)))
          case POSTDEC => Some(Tree.IncDec(expr(u.getOperand), "-", post = true, ty(u), originOf(u)))
          case PREINC  => Some(Tree.IncDec(expr(u.getOperand), "+", post = false, ty(u), originOf(u)))
          case PREDEC  => Some(Tree.IncDec(expr(u.getOperand), "-", post = false, ty(u), originOf(u)))
          case _       => scala.None

      private def referenceIdentity(b: CtBinaryOperator[?]): Option[Term] =
        import BinaryOperatorKind.*
        val (l, r) = (b.getLeftHandOperand, b.getRightHandOperand)
        def isNull(e: CtExpression[?]) = e match { case lit: CtLiteral[?] => lit.getValue == null; case _ => false }
        def refTyped(e: CtExpression[?]) =
          try
            val t = e.getType
            t != null && !t.isPrimitive
          catch { case _: Throwable => false }
        if (b.getKind != EQ && b.getKind != NE) || isNull(l) || isNull(r) then scala.None
        else if !refTyped(l) || !refTyped(r) then scala.None
        else
          val anyRef = TypeRef(NoPrefix, minter.external("scala.AnyRef", "AnyRef"))
          val anyT = TypeRef(NoPrefix, minter.external("scala.Any", "Any"))
          // `eq` lives on `AnyRef`. A `java.lang.Object` operand may have been rendered `Any` —
          // java's `equals(Object)` parameter must be, since scala's `Object.equals` takes `Any` —
          // and the emitted term still carries spoon's type, so the rendering cannot be read off
          // it. Ascribe on the java type instead: every `Object` value IS an `AnyRef`, so this is
          // a no-op wherever the widening was not needed.
          def asRef(e: CtExpression[?]): Term =
            val t = expr(e)
            val objTyped = try Option(e.getType).exists(_.getQualifiedName == "java.lang.Object")
                           catch { case _: Throwable => false }
            if objTyped || t.tpe == anyT then Tree.Typed(t, tt(anyRef, e), anyRef, originOf(e)) else t
          val op = if b.getKind == EQ then "eq" else "ne"
          Some(binApply(op, asRef(l), asRef(r), ty(b)))

      private def binApply(op: String, l: Term, r: Term, resT: TypeRepr): Term =
        Tree.Apply(Tree.Select(l, opId(op), NoType, l.origin), List(r), opId(op), resT, l.origin)
      private def unApply(op: String, o: Term, resT: TypeRepr): Term =
        Tree.Apply(Tree.Select(o, opId(op), NoType, o.origin), Nil, opId(op), resT, o.origin)

      /** the Scala spelling of a java binary operator, or `scala.None` for a kind this arm does not
        * enumerate.
        *
        * An `Option` and not a defaulted string, for the reason [[UnaryOperatorKind]]'s twin arm
        * gives at length: `BinaryOperatorKind` is a java enum from a DEPENDENCY, not a sealed Scala
        * one, so scalac cannot check this match and a Spoon upgrade that adds a kind falls through.
        * The default used to be `"?" + other` — which is not a diagnostic, it is a METHOD NAME:
        * `binApply` would build `l.?NEWKIND(r)`, the emitter would render it, and the port would
        * carry a call to a member nobody declares. Best case a compile error naming a symbol that
        * appears nowhere in the java; worst case, in a position where scalac infers rather than
        * resolves, nothing at all. `INSTANCEOF` is java's twentieth kind and never reaches here —
        * the arm above it branches first — so what this enumerates is the nineteen operators. */
      private def opText(k: BinaryOperatorKind): Option[String] =
        import BinaryOperatorKind.*
        k match
          case PLUS => Some("+"); case MINUS => Some("-"); case MUL => Some("*")
          case DIV => Some("/"); case MOD => Some("%")
          case AND => Some("&&"); case OR => Some("||"); case BITAND => Some("&")
          case BITOR => Some("|"); case BITXOR => Some("^")
          case EQ => Some("=="); case NE => Some("!="); case LT => Some("<")
          case LE => Some("<="); case GT => Some(">"); case GE => Some(">=")
          case SL => Some("<<"); case SR => Some(">>"); case USR => Some(">>>")
          case _ => scala.None

      /** the MARKER for an operator kind [[opText]] does not enumerate — located, named, and
        * `FrontendBlindSpot` rather than `UnmodelledNodeKind` because the node KIND is dispatched
        * on here and what is missing is one shape of it. The emission gate then refuses to ship the
        * port until it is closed (`DESIGN.md` §6.4), which is the whole difference between this and
        * a method name nobody can resolve. */
      private def unknownOp(k: BinaryOperatorKind, at: CtElement, tpe: TypeRepr): Term =
        unlowered(at, s"binary operator kind '$k' — this arm enumerates java's nineteen and the " +
          "parser produced a twentieth", tpe, Some(UnportableKind.FrontendBlindSpot))
