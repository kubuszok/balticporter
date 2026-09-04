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

import java.nio.file.{Files, Path}

import scala.jdk.CollectionConverters.*

/** Populates the typed IR ([[balticporter.tir]]) directly from Spoon's resolved model: every
  * declaration mints a stable-identity [[Symbol]], every type reference resolves to a structured
  * [[TypeRepr]], and externals are lazily interned so `usagesOf` works with no local definition.
  * Scope: declarations, signatures, types, and method bodies. [[Xref.build]] indexes usages.
  */
object SpoonTir:

  /** the PUBLIC instance methods of `java.lang.Object`, by Spoon signature — JLS 9.8's exclusion
    * when counting a functional interface's abstract methods. `clone`/`finalize` excluded: they
    * are `protected`, so JLS 9.8 does not exclude them. */
  private[spoon] val ObjectPublicSignatures: Set[String] = Set(
    "equals(java.lang.Object)", "hashCode()", "toString()", "getClass()",
    "notify()", "notifyAll()", "wait()", "wait(long)", "wait(long,int)")

  /** Sentinel entries `annotationsOf` can put in `Symbol.droppedAnnotations` beside real annotation
    * names, so `omissions` distinguishes the reasons a drop happened: `<unresolved>` (type would
    * not resolve), `<unreadable-annotations>` (whole set failed to read), `<annotation-arguments-failed>`
    * (constant-expression path threw — an engine defect, not policy). */
  val UnresolvedAnnotation = "<unresolved>"
  val UnreadableAnnotations = "<unreadable-annotations>"
  val FailedAnnotationArguments = "<annotation-arguments-failed>"

  /** Build a [[Program]] from already-resolved top-level Spoon types. `catalog` is the run's
    * obligation log — a parameter, not a field of `Program`, because a log is a value one run owns
    * (CLAUDE.md §5.1); default is a fresh discarding log. */
  def fromTypes(types: List[CtType[?]], subs: Substitutions = Substitutions.none,
                catalog: CatalogLog = CatalogLog.discarding,
                annotations: AnnotationPolicy = AnnotationPolicy.none): Program =
    new Builder(subs, catalog = catalog, annotations = annotations).build(types)

  /** Build the Spoon model over a whole closure and return its top-level types. Full classpath by
    * default; `lenient` uses noClasspath mode so unconfigured external deps still parse (unresolved
    * types degrade to unmapped references). */
  def buildModel(cfg: FrontendConfig, lenient: Boolean = false): List[CtType[?]] =
    val launcher = new Launcher
    val env      = launcher.getEnvironment
    env.setComplianceLevel(21)
    // comments must stay enabled — the licence-notice harvest below needs them (CLAUDE.md §4.58)
    env.setCommentEnabled(true)
    env.setNoClasspath(lenient)
    env.setSourceClasspath(cfg.classpath.map(_.toString).toArray)
    if cfg.resolutionRoots.nonEmpty then
      cfg.resolutionRoots.foreach(r => addResolutionRoot(launcher, r, cfg.resolutionExcludes))
      // declared inputs: an absent one is fatal with a named diagnostic (CLAUDE.md §5.4)
      val covered = cfg.resolutionRoots.map(r => RealPath.ofExisting(r, "resolution root"))
      cfg.files
        .map(f => RealPath.ofExisting(cfg.sourceRoot.resolve(f), s"declared source file $f"))
        .filterNot(abs => covered.exists(abs.startsWith))
        .foreach(abs => launcher.addInputResource(abs.toString))
    else cfg.files.foreach(f => launcher.addInputResource(cfg.sourceRoot.resolve(f).toString))
    launcher.buildModel().getAllTypes.asScala.toList.filter(_.getDeclaringType == null)

  /** Add one resolution root, minus whatever the port excluded from it
    * ([[balticporter.core.FrontendConfig.resolutionExcludes]]). No exclusions: add the directory
    * whole. With exclusions: add surviving `.java` files individually; `cfg.resolutionRoots` itself
    * stays unchanged. Matched at a path separator, never substring (CLAUDE.md §4.56). */
  private def addResolutionRoot(launcher: Launcher, root: Path, excludes: List[String]): Unit =
    if excludes.isEmpty then launcher.addInputResource(root.toString)
    else
      val rr   = RealPath.ofExisting(root, "resolution root")
      val cuts = excludes.map(_.stripSuffix("/"))
      Files.walk(rr).iterator.asScala
        .filter(p => p.toString.endsWith(".java"))
        .filterNot { p =>
          val rel = rr.relativize(p).toString
          cuts.exists(c => rel == c || rel.startsWith(c + "/"))
        }
        .foreach(p => launcher.addInputResource(p.toString))

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

  /** The same, over several compilation units — needed to test package-boundary rules (default
    * access, `protected`, cross-package overrides) that one snippet cannot exercise. Each pair is
    * `fileName -> code`; each unit's text is looked up by file name for comment slicing. */
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
    // VirtualFile has no source buffer of its own — pass texts explicitly or comments re-print (§4.58)
    new Builder(subs, sources.toMap, catalog, annotations).build(tops)

  /** The one classification of a Spoon type reference. Since `CtWildcardReference` extends
    * `CtTypeParameterReference`, the wildcard arm must be matched ABOVE the variable arm or `?`
    * reads as a type variable — derived once here so no caller re-derives it by `isInstanceOf`.
    * `ref`/`args` let a caller treat Prim/Intersection/Named alike. CLAUDE.md §4.56, ENGINE-LIMITS G21 */
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
      r.getActualTypeArguments.asScala.toList

    /** THE derivation. The WILDCARD arm is first, and that order is the whole point (see the enum's
      * doc): `CtWildcardReference <: CtTypeParameterReference`, so any other order silently answers
      * "type variable" for every `?` in the program. */
    def of(tr: CtTypeReference[?]): TypeShape = tr match
      case null                              => Absent
      case w: CtWildcardReference            => Wildcard(w, Option(w.getBoundingType), w.isUpper)
      case tv: CtTypeParameterReference      => Variable(tv)
      case a: CtArrayTypeReference[?]        => Arr(a, a.getComponentType)
      case i: CtIntersectionTypeReference[?] =>
        Intersection(i, i.getBounds.asScala.toList)
      case p if (p.isPrimitive) => Prim(p)
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

    /** Register a SECOND key for an existing SymId, so `resolve`/`external` on the alias return
      * `id` rather than minting a new one. Used for anonymous classes, whose internal key and
      * Spoon's `getQualifiedName` key must resolve to the same symbol. */
    def alias(key: String, id: SymId): Unit = byKey(key) = id

    def define(key: String)(mk: SymId => Symbol): SymId =
      val id = resolve(key)
      syms(id) = mk(id)
      id

    /** Ensure a minimal stub exists for an external reference; never clobbers a real definition.
      * `owner` is `SymId.None` for a TYPE (external types are rooted outside the program); an
      * external MEMBER must carry its owning type's id, or `owner#name` cannot identify it and
      * every ownership-keyed lookup (e.g. `PortabilityCheck`) silently never fires. */
    def external(key: String, name: String, owner: SymId = SymId.None,
                 descriptor: Option[Descriptor] = None, info: TypeRepr = NoType,
                 annotations: List[Annot] = Nil): SymId =
      val id = resolve(key)
      if !syms.contains(id) then syms(id) = Symbol(id, name, key, Flags(), owner, info, descriptor = descriptor, annotations = annotations)
      else
        // fill holes only — never overwrite a real declaration (that happens via `define`)
        var s = syms(id)
        if descriptor.isDefined && s.descriptor.isEmpty then s = s.copy(descriptor = descriptor)
        if info != NoType && s.info == NoType then s = s.copy(info = info)
        if annotations.nonEmpty && s.annotations.isEmpty then s = s.copy(annotations = annotations)
        syms(id) = s
      id

    def table: SymbolTable        = SymbolTable(syms.values)
    def idOf(key: String): SymId  = byKey(key)
    /** the symbol at `key` IF one was really defined there. Deliberately not `resolve`, which mints
      * an id for a key nobody defined and reaches the emitter as `?`. */
    def defined(key: String): Option[(SymId, Symbol)] =
      byKey.get(key).flatMap(id => syms.get(id).map(id -> _))
    def fullNameOf(id: SymId): String = syms.get(id).map(_.fullName).getOrElse("?")
    /** the DECLARED type this frontend interned for `id` — `NoType` where nothing was declared.
      * Answers *did this frontend retype this declaration?* (CLAUDE.md §4.56). */
    def infoOf(id: SymId): TypeRepr = syms.get(id).map(_.info).getOrElse(NoType)
    /** the interned OWNER of a member — the type that declares it (not the subclass name it was
      * reached through, T14). `SymId.None` for a type or an unresolved member. */
    def ownerOf(id: SymId): SymId = syms.get(id).map(_.owner).getOrElse(SymId.None)

  /** @param inMemorySources each compilation unit's text by file name, for units with no buffer of
    *   their own (`fromSources`). Empty for real files. Keyed because a position is meaningful in
    *   only one unit's buffer (CLAUDE.md §4.58). */
  private final class Builder(subs: Substitutions = Substitutions.none,
                              inMemorySources: Map[String, String] = Map.empty,
                              catalog: CatalogLog = CatalogLog.discarding,
                              annotations: AnnotationPolicy = AnnotationPolicy.none):
    /** the run's obligation log, in scope for every `Lowering.of` in this builder — `given` rather
      * than threaded through forty signatures that only the dispatch method needs it. */
    private given CatalogLog = catalog
    private val minter   = new Minter
    private val tpScopes = collection.mutable.ArrayDeque[Map[String, SymId]]()
    /** an executable's own type parameters that are ERASED rather than declared — name → the type
      * every occurrence renders as. Consulted AHEAD of `tpScopes` since such a name has no binder.
      * One frame per executable. See [[unwritableResultVars]]. */
    private val tpErased = collection.mutable.ArrayDeque[Map[String, TypeRepr]]()
    private val selfRawStack = collection.mutable.ArrayDeque[(SymId, List[SymId])]()
    /** Type params LEGALLY in scope at the current point, respecting static-nested boundaries (a
      * static nested type cannot see its enclosing type's params). Distinct from `tpScopes`, which
      * keeps every enclosing frame for reference resolution. */
    private val tpAccessible = collection.mutable.ArrayDeque[Map[String, SymId]]()
    /** names contributed by EXECUTABLES, parallel to `tpAccessible` (which merges each level into
      * one map, so a frame cannot simply be skipped). Hidden under [[atDeclScope]]. */
    private val tpExecNames = collection.mutable.ArrayDeque[Set[String]]()

    /** The instantiation this class gives its ANCESTORS' type parameters, by name — needed so an
      * overriding member can fill a raw inherited type the same way the inherited declaration did,
      * rather than independently landing on `[?]` and disagreeing with it. */
    private val inheritedInst = collection.mutable.ArrayDeque[Map[String, (TypeRepr, CtTypeReference[?])]]()
    /** …the same instantiation keyed by DECLARATION — see [[instantiationByDecl]]. Read only by
      * [[inheritedFormal]], at a call to a member an ANCESTOR declares. */
    private val inheritedByDecl = collection.mutable.ArrayDeque[Map[(String, String), CtTypeReference[?]]]()
    /** FQNs of the enclosing class and its ancestors — a raw type nested in any of them is filled
      * from the names in scope, since those are the names it was declared against. */
    private val enclosingFqns = collection.mutable.ArrayDeque[Set[String]]()
    /** FQNs of this class's ancestors — the only declarations whose formals are written in type
      * variables the inherited instantiation can speak about. */
    private val ancestorFqns = collection.mutable.ArrayDeque[Set[String]]()
    private var noInheritFill = false
    /** true while translating a member this class INHERITS (an override), so the inherited
      * instantiation applies only there — a member the class declares for itself has no such
      * obligation, and an unrelated ancestor's same-named type param must not leak into it. */
    private var inOverridingMember = false
    /** The map is keyed by NAME, so an unrelated ancestor's `T` can collide with the type being
      * filled; require the candidate to satisfy the formal's own BOUND to make the match safe. */
    private def inheritedTp(f: CtTypeParameter): Option[TypeRepr] =
      if true || noInheritFill || !inOverridingMember then scala.None // sge design: no inherited fill
      else inheritedInst.headOption.flatMap(_.get(f.getSimpleName)).collect {
        case (r, ref) if boundAdmits(f, ref) => r
      }

    private def boundAdmits(f: CtTypeParameter, cand: CtTypeReference[?]): Boolean =
      Option(f.getSuperclass).filter(_.getQualifiedName != "java.lang.Object") match
        case None    => true
        case Some(b) => cand.isSubtypeOf(b)
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

    /** Every executable this walk considered, INCLUDING ones policy removed — published as
      * [[MemberIndex]], since a dropped executable has no symbol elsewhere to recover it from. */
    private val seenMembers = collection.mutable.ListBuffer.empty[(MemberKey, MemberFacts)]
    private val seenTypes   = collection.mutable.Set.empty[String]

    def build(types: List[CtType[?]]): Program =
      // headers harvested BEFORE any type translates — positional claim must run first (§4.58)
      val headers = types.map(fileHeader)
      val units   = types.zip(headers).map((t, h) => classDef(t).copy(unitLeading = h))
      new Program(units, minter.table, Xref.build(units),
                  MemberIndex(seenMembers.toList, seenTypes.toSet))

    // ---- trivia (the original comments) -------------------------------------
    // verbatim slices out of the source buffer; a CLAIMED set so a coarse harvest only scoops
    // what no closer one took.

    /** Every comment handed out, by IDENTITY (not equality — two `// TODO`s are two comments), so
      * `deepComments`'s net cast over a subtree does not re-emit one a closer harvest already took. */
    private val claimed: java.util.Set[CtComment] =
      java.util.Collections.newSetFromMap(new java.util.IdentityHashMap[CtComment, java.lang.Boolean]())

    /** VERBATIM comment text, sliced from the original source (delimiters included). Never
      * `CtComment.toString`, which re-prints and loses exact formatting — unacceptable for a licence
      * notice (CLAUDE.md §4.57). Re-printed form is the fallback for a comment with no position. */
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

    /** The compilation unit's original text, for slicing. `""` when Spoon has no buffer (the
      * normal case for an in-memory `VirtualFile`). Guard against `Some(null)`: a bare `.map` over
      * the unit yields it and NPEs downstream. */
    private def sourceOf(el: CtElement): String =
      val pos = el.getPosition
      if pos == null || !pos.isValidPosition then inMemoryFor(null)
      else Option(pos.getCompilationUnit).flatMap(cu => Option(cu.getOriginalSourceCode)).getOrElse(inMemoryFor(pos))

    /** the in-memory buffer this position belongs to, by file name. Falls back to the only source
      * when exactly one exists; falls back to `""` (never guesses the wrong file) otherwise. */
    private def inMemoryFor(pos: spoon.reflect.cu.SourcePosition): String =
      Option(pos).filter(_.isValidPosition).flatMap(p => Option(p.getFile)).map(_.getName)
        .flatMap(inMemorySources.get)
        .orElse(Option.when(inMemorySources.sizeIs == 1)(inMemorySources.values.head))
        .getOrElse("")

    /** the comments Spoon attached DIRECTLY to `el`. Deliberately NOT wrapped in a `catch` — a
      * harvest that throws is a defect to see (CLAUDE.md §4.6). */
    private def leadingOf(el: CtElement): List[Trivia] =
      el.getComments.asScala.toList.filter(unheaded).map { c => claimed.add(c); triviaOf(c) }

    /** WHERE a comment is: file + start offset. `claimed` uses object identity, which a comment
      * the parser attached nowhere cannot provide — the file header instead claims by span. */
    private def spanOf(c: CtComment): Option[(String, Int)] =
      val p = c.getPosition
      if p == null || !p.isValidPosition then scala.None
      else Some(unitKeyOf(p) -> p.getSourceStart)

    private def unitKeyOf(p: spoon.reflect.cu.SourcePosition): String =
      Option(p.getFile).map(_.getPath)
        .orElse(Option(p.getCompilationUnit).flatMap(cu => Option(cu.getFile)).map(_.getPath))
        // in-memory units with no file: fall back to the unit OBJECT identity, not "<unknown>"
        .orElse(Option(p.getCompilationUnit).map(cu => "cu@" + System.identityHashCode(cu)))
        .getOrElse("<unknown>")

    /** spans the FILE HEADER has taken. Not `claimed`: see [[spanOf]]. */
    private val headerSpans = collection.mutable.Set.empty[(String, Int)]

    /** a comment the file header did NOT take — the filter every finer harvest applies, so a
      * leading block that Spoon ALSO attached to the type is not emitted twice. */
    private def unheaded(c: CtComment): Boolean = spanOf(c).forall(!headerSpans.contains(_))

    /** Comments Spoon attached to expression-level descendants, hoisted to the nearest enclosing
      * harvest point (the TIR carries trivia only on declarations/statements). MUST be called AFTER
      * the element's children have translated, or it swallows their comments too. */
    private def deepComments(el: CtElement): List[Trivia] =
      el.getElements(new spoon.reflect.visitor.filter.TypeFilter[CtComment](classOf[CtComment]))
        .asScala.toList.filter(unheaded).filter(claimed.add).map(triviaOf)

    /** The FILE's own header: everything above the first line of code, plus anything hanging off
      * the imports — the licence, in every library seen so far. Read POSITIONALLY, not from the
      * parser's attachment model, which mis-attaches the second of two leading block comments to
      * the package declaration (ENGINE-LIMITS V3). Does not respect `claimed`: two top-level types
      * from one file each need the header, so it is cached per compilation unit instead. */
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
      // then the parser's own — an import's comments, and any comment with no usable position
      val attached = cu.getComments.asScala.toList ++ cu.getImports.asScala.toList.flatMap(_.getComments.asScala)
      attached.foreach(claimed.add)
      val taken    = fromText.map(_._1).toSet
      val fromTree = attached.flatMap { c =>
        val at = spanOf(c).map(_._2)
        if at.exists(taken.contains) then Nil else List(at.getOrElse(Int.MaxValue) -> triviaOf(c))
      }
      // the header OWNS these spans — leadingOf/deepComments skip them, so nothing is emitted twice
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

    /** the position's COLUMN, or 0 where the unit has no source buffer to search (an in-memory unit
      * may have no `getOriginalSourceCode`, and Spoon's own column search then crashes). ZERO is
      * honest here — every `Origin` reader keys on FILE and LINE, never on the column. */
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

    /** The member's DESCRIPTOR — its source-level parameter spelling, read from the PARSER (not
      * from the retyped `MethodType`, so `equals(Object)` stays `Object` rather than `scala.Any`).
      * Spelling matches `isDropped`'s (`dropMethods` keys against it). ALL parameters or none
      * ([[Descriptor.total]]) — a partial descriptor matches the wrong overload. */
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
      * parsed from a source this run owns? The one spelling of "is this external" (via
      * [[isExternalCallee]]); no declaring type means external, an unreadable parent means
      * conservatively NOT external (suppresses casts/spreads rather than guessing). */
    private def isShadowDecl(m: CtExecutable[?]): Boolean =
      Option(m.getParent(classOf[CtType[?]])) match
        case scala.None    => true
        case Some(t)       => t.isShadow

    /** …the same question asked of a call's REFERENCE: no declaration at all means external, since
      * this program's own members are always parsed and therefore have one. */
    private def isExternalCallee(ex: CtExecutableReference[?]): Boolean =
      Option(ex.getExecutableDeclaration) match
        case scala.None => true
        case Some(d)    => isShadowDecl(d)

    /** The `MethodType` of an EXTERNAL member (ENGINE-LIMITS K15) — only for a SHADOW declaration
      * ([[isShadowDecl]]). Rendered SCOPE-FREE: a type variable, intersection or raw generic renders
      * as no answer rather than a name from the CALLER's scope, since an external symbol is interned
      * once and never clobbered. ALL slots or NONE ([[Descriptor.total]]'s rule). */
    private def externalSignature(m: CtExecutable[?]): TypeRepr =
      if !isShadowDecl(m) then NoType
      else
        val ps  = m.getParameters.asScala.toList
        val slots = ps.map(p => p.getSimpleName -> externalSlot(p.getType))
        // a constructor's result is `Unit`, which is what `execDef` renders for the members this
        // program DECLARES. One grammar: a reader that has a `MethodType` must not have to ask
        // where it came from before it can read the result slot.
        val ret = m match
          case _: CtConstructor[?] => unitT
          case _                   => externalSlot(m.getType)
        if ret == NoType || slots.exists(_._2 == NoType) then NoType
        else MethodType(slots, ret)

    /** one SLOT of [[externalSignature]] — a parameter's or result's declared type, or `NoType`
      * where no scope-free name exists. A slot that cannot render is unknown; a type ARGUMENT that
      * cannot render is `?` (Spoon's reflective reconstruction loses the exact bound). */
    private def externalSlot(tr: CtTypeReference[?]): TypeRepr = TypeShape.of(tr) match
      case TypeShape.Absent      => NoType
      case TypeShape.Prim(p)     => tpe(p)
      case TypeShape.Arr(_, c) =>
        externalSlot(c) match
          case NoType => NoType
          case e      => AppliedType(TypeRef(NoPrefix, minter.external("scala.Array", "Array")), List(e))
      // a type variable or intersection at the slot names something only the CALLEE's scope has
      case TypeShape.Variable(_)       => NoType
      case TypeShape.Intersection(_, _) => NoType
      // PRESERVED SHADOW: wildcard answers NoType here too, not `?` (ENGINE-LIMITS G21)
      case TypeShape.Wildcard(_, _, _) => NoType
      case s @ TypeShape.Named(r, _) =>
        val head  = TypeRef(NoPrefix, typeSym(r))
        val args  = s.args
        val arity = formalArity(r)
        // scope-free fill: wildcards, never names from the reading point (`tpe`'s own no-scope answer)
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

    /** Render a type as its DECLARATION site would have, not as the current reading scope would —
      * the name-directed raw fill is scope-dependent by design, so re-rendering in the reading
      * scope would silently disagree with the declared type. Hides executable frames only: a
      * field's type cannot mention a method's type parameters, so this is exact, not approximate. */
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

    /** the type an ERASED type-parameter name renders as, or `None` for an ordinary one. Consulted
      * AHEAD of [[resolveTypeParam]]: an erased parameter was never minted and has no id. */
    private def erasedTypeParam(name: String): Option[TypeRepr] =
      tpErased.iterator.collectFirst { case m if m.contains(name) => m(name) }

    /** an executable's own type parameters that have NO WRITABLE INSTANTIATION ANYWHERE — java's
      * UNCHECKED generic method (JLS 8.4.2 subsignature-by-erasure), erased at the declaration to
      * its own bound. Three conditions, all required: the variable occurs in no PARAMETER type; the
      * bound MENTIONS THE VARIABLE ITSELF (F-bound, the load-bearing conjunct, ENGINE-LIMITS G8);
      * the RESULT mentions the variable. Does not touch a variable the DECLARING TYPE owns. */
    private def unwritableResultVars(m: CtExecutable[?]): List[CtTypeParameter] = m match
      case ftd: CtFormalTypeDeclarer =>
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

    // Java's type parameters are always reference types (`<T>` means `<T extends Object>`);
    // scala's `[T]` means `T <: Any`, strictly weaker — restoring the bound is a java fact (§1a).
    /** parent formal NAME -> the argument this class supplies, walking supertypes breadth-first
      * (so a grandparent's names are covered too). */
    private def ancestorsOf(t: CtType[?]): Set[String] =
      val acc = collection.mutable.Set[String](t.getQualifiedName)
      def walk(r: CtTypeReference[?], fuel: Int): Unit =
        if r != null && fuel > 0 && !acc.contains(r.getQualifiedName) then
          acc += r.getQualifiedName
          val d = typeDeclarationOf(r).orNull
          if d != null then
            val ups: List[CtTypeReference[?]] =
              (d match { case c: CtClass[?] => Option(c.getSuperclass).toList; case _ => Nil }) ++
                (d.getSuperInterfaces.asScala.toList)
            ups.foreach(walk(_, fuel - 1))
      val ups0: List[CtTypeReference[?]] =
        (t match { case c: CtClass[?] => Option(c.getSuperclass).toList; case _ => Nil }) ++
          (t.getSuperInterfaces.asScala.toList)
      ups0.foreach(walk(_, 5))
      // NOT the class itself — its own helpers' formals must render outside the override gate too
      acc.toSet - t.getQualifiedName

    /** ONE walk over `t`'s ancestry, yielding every (DECLARING TYPE, formal name, argument) triple
      * the `extends`/`implements` clauses instantiate — two consumer maps fold from it so they
      * cannot drift (ENGINE-LIMITS F8, CLAUDE.md §4.56). Filters unnameable arguments here. */
    private def parentInstantiations(t: CtType[?]): List[(String, String, CtTypeReference[?])] =
      val out = collection.mutable.ListBuffer[(String, String, CtTypeReference[?])]()
      def walk(r: CtTypeReference[?], fuel: Int): Unit =
        if r != null && fuel > 0 then
          val decl = typeDeclarationOf(r).orNull
          if decl != null then
            val fs = decl.getFormalCtTypeParameters.asScala.toList
            val as = r.getActualTypeArguments.asScala.toList
            if fs.sizeIs == as.size then
              fs.zip(as).foreach { (f, a) =>
                // skip an argument naming a type variable not in scope — renders as illegal `?I`
                val nameable = a match
                  case tv: CtTypeParameterReference => resolveTypeParam(tv.getSimpleName).isDefined
                  case _                            => true
                if !a.isInstanceOf[CtWildcardReference] && nameable then
                  out += ((decl.getQualifiedName, f.getSimpleName, a))
              }
            val ups: List[CtTypeReference[?]] = decl match
              case c: CtClass[?] => Option(c.getSuperclass).toList
              case _             => Nil
            (ups ++ decl.getSuperInterfaces.asScala.toList).foreach(walk(_, fuel - 1))
      val sup: List[CtTypeReference[?]] = t match
        case c: CtClass[?] => Option(c.getSuperclass).toList
        case _             => Nil
      (sup ++ t.getSuperInterfaces.asScala.toList).foreach(walk(_, 4))
      out.toList

    private def instantiationOfParents(t: CtType[?]): Map[String, (TypeRepr, CtTypeReference[?])] =
      val out = collection.mutable.Map[String, (TypeRepr, CtTypeReference[?])]()
      parentInstantiations(t).foreach { (_, nm, a) =>
        if !out.contains(nm) then out(nm) = (tpe(a), a)
      }
      out.toMap

    /** …the same instantiations keyed by DECLARATION rather than name — `(owner FQN, formal name)`,
      * `ParentSubst`'s own identity — so two ancestors' same-named `T`s cannot collide. */
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
          val d = typeDeclarationOf(r).orNull
          if d != null then
            val ups: List[CtTypeReference[?]] =
              (d match { case c: CtClass[?] => Option(c.getSuperclass).toList; case _ => Nil }) ++
                (d.getSuperInterfaces.asScala.toList)
            ups.foreach(walk(_, fuel - 1))
      val ups0: List[CtTypeReference[?]] =
        (t match { case c: CtClass[?] => Option(c.getSuperclass).toList; case _ => Nil }) ++
          (t.getSuperInterfaces.asScala.toList)
      ups0.foreach(walk(_, 5))
      acc.toSet

    private def nestedInScope(r: CtTypeReference[?]): Boolean =
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

    private def boundsOf(tp: CtTypeParameter): TypeBounds =
      Option(tp.getSuperclass).filter(_.getQualifiedName != "java.lang.Object").map(fbound) match
        case Some(hi) => TypeBounds(NoType, hi)
        case None     => TypeBounds(NoType, objectT)

    /** Reconstruct a raw generic type's args from IN-SCOPE type parameters of the same NAME
      * (wildcards for the rest) — preserves self-reference/enclosing instantiation that a plain
      * wildcard fill erases. `None` for arity-0. Every slot is LICENSED first ([[licensedFills]]),
      * since java stops checking at a raw use and scala does not (ENGINE-LIMITS G30). */
    private def nameFilledArgs(r: CtTypeReference[?], resolve: String => Option[SymId],
                               resolveDecl: String => Option[CtTypeParameter]): Option[List[TypeRepr]] =
      val formals = typeDeclarationOf(r).map(_.getFormalCtTypeParameters.asScala.toList).getOrElse(Nil)
      if formals.isEmpty then None
      else
        val ok = licensedFills(formals, resolveDecl)
        Some(formals.map { f =>
          val nm = f.getSimpleName
          if ok(nm) then resolve(nm).map(pid => TypeRef(NoPrefix, pid)).getOrElse(TypeBounds(NoType, NoType))
          else TypeBounds(NoType, NoType)
        })

    /** WHICH of a raw type's formals may take the in-scope variable of the same name (CLAUDE.md
      * §4.56 at a BOUND, ENGINE-LIMITS G30). Licensed iff: the variable IS the formal (F-bound); the
      * formal is unbounded; or both declare the SAME bound, spelled the same ([[boundSpelling]]).
      * Propagates as a greatest fixpoint over free names in the bound. Unreadable bound licenses
      * the fill (the third value, never a fabricated `catch` answer, §4.6). */
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
            val free = Option(f.getSuperclass).map(mentionedTypeVarNames).getOrElse(Set.empty)
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

    /** The ERASURE of a type variable — its first bound with nested variables erased, else Object.
      * Used ONLY to build CASTS at wildcard-receiver call sites — must never drive a DECLARATION's
      * type (that breaks assignment of concrete generic values, measured catastrophic). */
    private def erasureOfFormal(f: CtTypeParameter, seen: Set[String], depth: Int): TypeRepr =
      Option(f.getSuperclass).filter(_.getQualifiedName != "java.lang.Object") match
        case None    => objectT
        case Some(b) => erasedType(b, seen + f.getSimpleName, depth)

    private def erasedType(b: CtTypeReference[?], seen: Set[String], depth: Int): TypeRepr =
      if depth <= 0 then objectT
      else TypeShape.of(b) match
        // `seen` breaks F-bounded cycles; wildcard asserts SOME type satisfies the bound, which
        // scalac accepts where a flat `Object` fails an invariant F-bound. PRESERVED SHADOW G21.
        case TypeShape.Wildcard(_, _, _) => objectT
        case TypeShape.Variable(tv) =>
          if seen(tv.getSimpleName) then TypeBounds(NoType, NoType)
          else
            val d = typeParamDeclOf(tv)
            d.map(erasureOfFormal(_, seen + tv.getSimpleName, 2)).getOrElse(objectT)
        case TypeShape.Arr(_, c) =>
          AppliedType(TypeRef(NoPrefix, minter.external("scala.Array", "Array")),
                      List(erasedType(c, seen, depth)))
        case TypeShape.Intersection(_, bounds) =>
          bounds.headOption.map(erasedType(_, seen, depth)).getOrElse(objectT)
        case TypeShape.Prim(p)      => tpe(p)
        // no caller passes null here; defer to `tpe`, the one place that answers for it
        case TypeShape.Absent       => tpe(b)
        case s @ TypeShape.Named(r, _) =>
          val head = TypeRef(NoPrefix, typeSym(r))
          s.args match
            case Nil =>
              val formals = typeDeclarationOf(r).map(_.getFormalCtTypeParameters.asScala.toList).getOrElse(Nil)
              if formals.isEmpty then head
              else AppliedType(head, formals.map { ff =>
                // `seen` breaks F-bounded cycles (`N extends Node<N,…>`); depth bounds the rest
                if seen(ff.getSimpleName) then TypeBounds(NoType, NoType)
                else erasureOfFormal(ff, seen, depth - 1)
              })
            case args => AppliedType(head, args.map(a => erasedType(a, seen, depth - 1)))

    /** the erasure a DECLARED formal is seen at through an erased receiver: resolves through `subst`
      * (receiver's own erased arguments, by formal NAME) or, failing that, its declaration. A RAW
      * generic formal is emitted name-FILLED, so it too must resolve through `subst`. */
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
          val d = typeParamDeclOf(tv)
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
         (r.getActualTypeArguments.size) > 0 then Nil
      else typeDeclarationOf(r).map(_.getFormalCtTypeParameters.asScala.toList).getOrElse(Nil)

    private def rawFormalsOf(r: CtTypeReference[?]): List[String] = rawFormalNodes(r).map(_.getSimpleName)

    /** [[mentionsTypeVar]], but aware that a RAW generic use is emitted name-FILLED from the
      * same-named in-scope parameters — so `ResourceData` inside `ParticleBatch<T>` really does
      * depend on `T`, even though nothing in the Spoon type says so. */
    private def mentionsTypeVarFilled(tr: CtTypeReference[?], names: Set[String]): Boolean = tr match
      case tv: CtTypeParameterReference => names(tv.getSimpleName) || boundMentions(tv, names)
      case arr: CtArrayTypeReference[?] => mentionsTypeVarFilled(arr.getComponentType, names)
      case _ =>
        val args = tr.getActualTypeArguments.asScala.toList
        if args.nonEmpty then args.exists(mentionsTypeVarFilled(_, names))
        else rawFormalsOf(tr).exists(names)

    /** A METHOD type variable declared in terms of the receiver's depends on the receiver just as a
      * bare formal does. Bound only, never the variable's own NAME (a same-named callee `<T>` is a
      * different variable — the confusion [[tpConcrete]] avoids). Depth-limited (F-bounds). */
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
      // PRESERVED SHADOW (ENGINE-LIMITS G21) — answers `false`, as the shadowed variable arm did
      case TypeShape.Wildcard(_, _, _)  => false
      case TypeShape.Arr(_, c)          => mentionsRawGeneric(c)
      case TypeShape.Prim(_)            => false
      case s =>
        if s.args.nonEmpty then s.args.exists(mentionsRawGeneric)
        else formalArity(s.ref) > 0

    /** the DECLARED type-parameter arity of a type reference — `Map` → 2, `String` → 0. The `catch`
      * covers ONLY `getTypeDeclaration` being absent (not on the classpath — normal, arity 0 is the
      * only answer); a declaration that resolves but cannot state its own arity PROPAGATES rather
      * than silently answering 0 (CLAUDE.md §4.6). */
    private def formalArity(r: CtTypeReference[?]): Int =
      typeDeclarationOf(r).map(_.getFormalCtTypeParameters.size).getOrElse(0)

    /** the ONE Spoon lookup in the arity family where an absent value is normal — see
      * [[formalArity]] for why nothing else in that computation may share its `catch`. */
    private def typeDeclarationOf(r: CtTypeReference[?]): Option[spoon.reflect.declaration.CtType[?]] =
      if r == null then scala.None
      else try Option(r.getTypeDeclaration) catch { case _: Throwable => scala.None }

    /** the ONE Spoon lookup for a type variable's declaration where an absent value is normal —
      * the type variable belongs to an external generic whose declaration is not on the classpath.
      * Callers treat `None` as "unknown/external"; see [[typeDeclarationOf]] for the same argument
      * at the type level.  `CLAUDE.md` §4.6: one function, one doc, one `catch`. */
    private def typeParamDeclOf(tv: CtTypeParameterReference): Option[CtTypeParameter] =
      try Option(tv.getDeclaration) catch { case _: Throwable => scala.None }

    /** the ONE Spoon lookup for an executable's declaration where an absent value is normal —
      * the method is external and its source declaration is not on the classpath.  Callers that
      * receive `None` decline the rule they were about to apply, which is the correct fallback
      * for an unknowable signature.  `CLAUDE.md` §4.6. */
    private def execDeclOf(ex: CtExecutableReference[?]): Option[CtExecutable[?]] =
      try Option(ex.getExecutableDeclaration) catch { case _: Throwable => scala.None }

    /** the ONE Spoon lookup for an annotation's type reference where an absent value is normal —
      * the annotation class might not be on the classpath.  `CLAUDE.md` §4.6. */
    private def annotationTypeRefOf(a: CtAnnotation[?]): Option[CtTypeReference[?]] =
      try Option(a.getAnnotationType) catch { case _: Throwable => scala.None }

    /** the ONE Spoon lookup for a FIELD's declaration where an absent value is normal — external
      * class not on the classpath. Callers decline the rule they were about to apply. CLAUDE.md §4.6 */
    private def fieldDeclOf(ref: CtFieldReference[?]): Option[CtField[?]] =
      try Option(ref.getFieldDeclaration) catch { case _: Throwable => scala.None }

    /** JAVA'S FUNCTIONAL-INTERFACE QUESTION (JLS 9.8), computed here from the class file (CLAUDE.md
      * §4.56) since the TIR only interns members the program references. Target must be an
      * INTERFACE; abstract methods counted INHERITED (`getAllMethods`); `static`/`default` excluded;
      * a member override-equivalent to `java.lang.Object`'s excluded. Unreadable → [[Sam.Answer.Unreadable]],
      * never `No`. `java.io.Serializable` reported BESIDE the answer, not folded into it. */
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
      * [[samResultTpt]] cannot disagree. Counted MODULO OVERRIDING (a re-declared inherited method,
      * e.g. a JVM bridge shape, is ONE method to java and two to `getSignature`): same simple name,
      * same arity, dropped one declared by a STRICT SUPERTYPE. Unrelated supertypes stay two. */
    private def samAbstracts(r: CtTypeReference[?]): List[CtMethod[?]] =
      typeDeclarationOf(r) match
        case Some(d: CtInterface[?]) =>
          val all = d.getAllMethods.asScala.toList.filter { m =>
            m.hasModifier(ModifierKind.ABSTRACT) &&
              !m.hasModifier(ModifierKind.STATIC) &&
              // belt-and-brace: modifier test above already declines default methods
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

    /** THE SAM METHOD'S RESULT TYPE for a lambda the SOURCE wrote (ENGINE-LIMITS I9) — needed since
      * the emitter interposes a nested `def` needing a result type from the SAM method, not the
      * interface. A generic result is ADAPTED at the target via Spoon's [[TypeAdaptor]]. Refused
      * where adaptation cannot answer — counted by `OmissionCheck.unnameableLambdaReturn` (§4.6). */
    private def samResultTpt(l: CtLambda[?]): Option[TypeTree] =
      if !returnsAValue(l) then scala.None
      else samAbstracts(l.getType) match
        case one :: Nil =>
          val rt = Option(one.getType).map(adaptedToTarget(l.getType, _))
          rt.filter(!mentionsTypeVariable(_, 8)).map(r => tt(tpe(r), l))
        case _ => scala.None

    /** the SAM method's declared result, read IN THE TARGET REFERENCE'S CONTEXT. Asked only where
      * the declared type mentions a variable. Default on failure is the UNADAPTED type, which still
      * mentions the variable — so the caller refuses rather than reading a fabricated answer (§4.6). */
    private def adaptedToTarget(target: CtTypeReference[?], t: CtTypeReference[?]): CtTypeReference[?] =
      if target == null || !mentionsTypeVariable(t, 8) then t
      else
        Option(new TypeAdaptor(target).adaptType(t)).getOrElse(t)

    /** does THIS lambda hold a `return` with a VALUE — stopping at a nested lambda/anonymous method,
      * whose `return`s are that construct's own (JLS 15.27.2). Asked of the java, before the body
      * translates, since `TirEmitter`'s equivalent walks the lowered tree. */
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
        r.getActualTypeArguments.asScala.exists(mentionsTypeVariable(_, fuel - 1))

    /** does this type's ancestry reach `java.io.Serializable`? Unreadable ancestor answers `false`
      * (the SAM answer for it is `Unreadable`, refused under its own guard). Bounded by `seen`
      * (each qualified name walked at most once) rather than a fuel counter — a hierarchy is as
      * deep as somebody wrote it. */
    private def serializableAncestry(r: CtTypeReference[?]): Boolean =
      val seen = collection.mutable.Set.empty[String]
      def walk(x: CtTypeReference[?]): Boolean =
        if x == null then false
        else if x.getQualifiedName == "java.io.Serializable" then true
        else if !seen.add(x.getQualifiedName) then false
        else typeDeclarationOf(x).exists { d =>
          val ups: List[CtTypeReference[?]] =
            (d match { case c: CtClass[?] => Option(c.getSuperclass).toList; case _ => Nil }) ++
              (d.getSuperInterfaces.asScala.toList)
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
      isGenericUse(tr) && (tr.getActualTypeArguments.isEmpty)

    /** does every type variable this type mentions resolve HERE? `tpe` renders an unresolved one as
      * a `?T` stub, which is not valid Scala — so a synthesized cast must never target such a type. */
    private def tpResolvable(tr: CtTypeReference[?]): Boolean = TypeShape.of(tr) match
      case TypeShape.Absent       => true
      case TypeShape.Variable(tv) => resolveTypeParam(tv.getSimpleName).isDefined
      // PRESERVED SHADOW (ENGINE-LIMITS G21) — answers `false` for every wildcard
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
      * LITERALLY the one in scope here ([[sameVarInScope]] — same declaration, not just same name).
      * A separate function, not a flag, since every existing `tpConcrete` caller must keep its
      * current answer (one derivation per question). */
    private def tpNameableHere(tr: CtTypeReference[?]): Boolean = TypeShape.of(tr) match
      case TypeShape.Absent       => true
      case TypeShape.Variable(tv) => sameVarInScope(tv)
      // PRESERVED SHADOW (ENGINE-LIMITS G21) — `Class<?>` answers `false` here
      case TypeShape.Wildcard(_, _, _) => false
      case TypeShape.Arr(_, c)         => tpNameableHere(c)
      case s                           => s.args.forall(tpNameableHere)
    // strictly weaker than `tpConcrete` — every non-variable arm agrees, so a concrete type passes too

    /** Is this callee type VARIABLE literally the one in scope here — same declaration, hence the
      * same minted symbol, not merely the same NAME (the case [[tpConcrete]] excludes wrongly, e.g.
      * a self-call inside the declaring class). Class formals mint at `<FQN>$$<name>`. */
    private def sameVarInScope(tv: CtTypeParameterReference): Boolean =
      Option(tv.getDeclaration).map(_.getParent) match
        case Some(ct: CtType[?]) =>
          resolveTypeParam(tv.getSimpleName)
            .contains(minter.resolve(ct.getQualifiedName + "$$" + tv.getSimpleName))
        case _ => false

    /** Concrete, or mentioning only type variables OWNED BY THE CALLEE — never in scope at the call
      * site, so Java's view of the formal is its erasure (an unbounded `<T>` is `Object`), which is
      * what an unchecked cast must name for Scala to infer `T`. Unbounded variables must NOT be
      * excluded: measured false, costs `AssetManager.load` casts sge also writes by hand. */
    private def calleeBounded(tr: CtTypeReference[?]): Boolean = TypeShape.of(tr) match
      case TypeShape.Absent => true
      // PRESERVED SHADOW (ENGINE-LIMITS G21)
      case TypeShape.Wildcard(_, _, _) => false
      case TypeShape.Variable(tv) if sameVarInScope(tv) => true
      case TypeShape.Variable(tv) =>
        (typeParamDeclOf(tv)).exists { d =>
          d.getParent.isInstanceOf[CtExecutable[?]]
        }
      case TypeShape.Arr(_, c) => calleeBounded(c)
      case s                   => s.args.forall(calleeBounded)

    /** `tpe`, but every type variable replaced by the erasure of its bound (see [[calleeBounded]]);
      * identical to `tpe` on a variable-free type. */
    private def tpBoundErased(tr: CtTypeReference[?]): TypeRepr = TypeShape.of(tr) match
      // PRESERVED SHADOW (ENGINE-LIMITS G21) — answers `objectT`, as the variable arm did
      case TypeShape.Wildcard(_, _, _) => objectT
      case TypeShape.Variable(tv) if sameVarInScope(tv) => tpe(tv)
      case TypeShape.Variable(tv) =>
        (typeParamDeclOf(tv))
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
        val args = tr.getActualTypeArguments.asScala.toList
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
      // PRESERVED SHADOW (ENGINE-LIMITS G21) — reports the literal name "?", never bound variables
      case TypeShape.Wildcard(w, _, _) => Set(w.getSimpleName)
      case TypeShape.Arr(_, c)         => typeVarsOf(c)
      case TypeShape.Prim(_)           => Set.empty
      case s                           => s.args.toSet.flatMap(typeVarsOf)

    /** does this type mention ANY type variable (directly, in an array element, or in an argument)? */
    private def mentionsAnyTypeVar(tr: CtTypeReference[?]): Boolean = TypeShape.of(tr) match
      case TypeShape.Absent      => false
      case TypeShape.Variable(_) => true
      // SHADOW FLIPPED (ENGINE-LIMITS G21): `?` mentions no type var itself; its bound is walked
      case TypeShape.Wildcard(_, b, _) => b.exists(mentionsAnyTypeVar)
      case TypeShape.Arr(_, c)         => mentionsAnyTypeVar(c)
      case s                           => s.args.exists(mentionsAnyTypeVar)

    /** Is `actual` the same type as `want` with some type ARGUMENTS collapsed to `Object` or a
      * wildcard? Exactly the shape of an UNCHECKED CONVERSION java performs silently. Compares
      * RENDERED types, requires the same type constructor and arity — narrow deliberately. */
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
      * here — compared by minted id (`<owner FQN>$$T`), not by name, since a callee's `<T>` could
      * otherwise silently bind to an unrelated in-scope `T`. Method-level params never match. */
    private def sameTypeParamHere(tv: CtTypeParameterReference): Boolean =
      val owner = (typeParamDeclOf(tv))
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
        tr.getActualTypeArguments.asScala.exists(mentionsTypeVar(_, names))

    /** Translate a type-parameter bound. A RAW generic bound (`N extends Node`) is Java's idiom
      * for a self-referential (F-)bound; name-directed fill (see [[nameFilledArgs]]) rebuilds it
      * — the decl's own params are already in scope here (minted before bounds are translated) —
      * rather than erasing to `Node[?, ?, ?]`. Non-raw / array / intersection / type-var bounds
      * defer to `tpe`. */
    private def fbound(tr: CtTypeReference[?]): TypeRepr =
      val isRawGeneric = !tr.isInstanceOf[CtTypeParameterReference] &&
        !tr.isInstanceOf[CtArrayTypeReference[?]] && !tr.isInstanceOf[CtIntersectionTypeReference[?]] &&
        !tr.isInstanceOf[CtWildcardReference] && !tr.isPrimitive && tr.getActualTypeArguments.isEmpty
      // bounds translate with the decl's own frame freshly in scope — `resolveTypeParam` is complete here
      (if isRawGeneric then nameFilledArgs(tr, resolveTypeParam, resolveTypeParamDecl) else None) match
        case Some(args) => AppliedType(TypeRef(NoPrefix, typeSym(tr)), args)
        case None       => tpe(tr)

    // ---- types ----
    /** THE TYPE-REFERENCE DISPATCH — the frontend half of the catalog's fourth obligation surface.
      * Wraps HERE rather than per-arm, so no arm can opt out of the consult. `CtTypeReference` is
      * neither statement nor expression, so neither other wrapper reaches it. `null` enters no
      * scope and owes nothing; answers `objectT`. */
    private def tpe(tr: CtTypeReference[?]): TypeRepr =
      if tr == null then objectT
      else
        val at = originOf(tr)
        Typing.ofReference(SpoonKinds.refNameOf(tr.getClass), at, tr)(tpeArm(tr, at))

    /** JS-G07 and JS-G08 — the two questions a PLAIN class reference is asked, STATED ONCE and
      * called from both arms a `CtTypeReference` reaches (primitive fast path and the general arm
      * are ONE Spoon kind). Both about a RAW USE (JLS 4.8); G08 narrows to sites where the fill
      * DEPENDS on the frame (a companion body cannot name the class's own params). */
    private def rawUseConsults(r: CtTypeReference[?], at: Origin)(using Obligations): Unit =
      val raw = isRawGenericUse(r)
      Obligations.consult(JS.G(7), at)(Option.when(raw)(()))
      Obligations.consult(JS.G(8), at)(Option.when(raw && (inStatic || nestedInScope(r)))(()))

    private def tpeArm(tr: CtTypeReference[?], at: Origin)(using Obligations): TypeRepr = tr match
      case arr: CtArrayTypeReference[?] =>
        AppliedType(TypeRef(NoPrefix, minter.external("scala.Array", "Array")), List(tpe(arr.getComponentType)))
      case inter: CtIntersectionTypeReference[?] =>
        inter.getBounds.asScala.toList.map(tpe).reduce(AndType(_, _))
      // `? super java.lang.Object` has exactly one inhabitant (`Object`) — java has no supertype
      // of it, so unlike `? extends Object` this bound must NOT be dropped to a bare wildcard.
      case w: CtWildcardReference =>
        val bound = Option(w.getBoundingType)
        val isObj = bound.exists(_.getQualifiedName == "java.lang.Object")
        val written = bound.filter(_.getQualifiedName != "java.lang.Object")
        // JS-G01: a bare `?` is the one form both languages spell the same way
        Obligations.consult(JS.G(1), at)(written)
        // JS-G03: `? super Object` is the one wildcard that is not a family at all
        Obligations.consult(JS.G(3), at)(Option.when(!w.isUpper && isObj)(()))
        if !w.isUpper && isObj then objectT
        else
          val b = written.map(tpe)
          if w.isUpper then TypeBounds(NoType, b.getOrElse(NoType)) else TypeBounds(b.getOrElse(NoType), NoType)
      // an ERASED parameter ([[unwritableResultVars]]) was deliberately never minted — JS-G49, not G12
      case tv: CtTypeParameterReference if erasedTypeParam(tv.getSimpleName).isDefined =>
        Obligations.consult(JS.G(49), at)(erasedTypeParam(tv.getSimpleName))
        // JS-G12 NOT-FIRED: `None` is a fact — an erased name is not one with no nameable type
        Obligations.consult(JS.G(12), at)(scala.None)
        erasedTypeParam(tv.getSimpleName).get
      case tv: CtTypeParameterReference =>
        // JS-G49 NOT-FIRED: a name resolving to a binder here was not erased
        Obligations.consult(JS.G(49), at)(scala.None)
        val here = resolveTypeParam(tv.getSimpleName)
        // JS-G12: no binder in scope mints a MARKER, which must never reach emitted output
        Obligations.consult(JS.G(12), at)(Option.when(here.isEmpty)(()))
        val id = here.getOrElse(minter.external(Symbol.UnresolvedTypeVarPrefix + tv.getSimpleName, tv.getSimpleName))
        TypeRef(NoPrefix, id)
      case p if p.isPrimitive =>
        // a primitive reaches the SAME obligation scope the arm below does
        rawUseConsults(p, at)
        TypeRef(NoPrefix, minter.external("scala." + primName(p.getSimpleName), p.getSimpleName))
      case r =>
        rawUseConsults(r, at)
        val head = TypeRef(NoPrefix, typeSym(r))
        r.getActualTypeArguments.asScala.toList match
          case Nil =>
            // raw use: fill declared arity with wildcards (`Class` → `Class[?]`) so it type-checks
            val arity = formalArity(r)
            // a raw use of the enclosing class fills with its own type params, not wildcards
            selfRawStack.headOption match
              // disabled: sge renders a raw SELF-use `[?]` too
              case Some((cls, params)) if false && !inStatic && cls == typeSym(r) && params.nonEmpty && params.sizeIs == arity =>
                AppliedType(head, params.map(p => TypeRef(NoPrefix, p)))
              case _ =>
                // reconstruct args from same-named in-scope params, else fall back to wildcards
                if arity <= 0 then head
                else if inStatic then AppliedType(head, List.fill(arity)(TypeBounds(NoType, NoType)))
                else
                  val formals = typeDeclarationOf(r).map(_.getFormalCtTypeParameters.asScala.toList).getOrElse(Nil)
                  if formals.isEmpty then AppliedType(head, List.fill(arity)(TypeBounds(NoType, NoType)))
                  else AppliedType(head, formals.map { f =>
                    // fill from an in-scope name only when the raw type is the enclosing class or nested in it
                    (if nestedInScope(r) then accessibleTp(f.getSimpleName) else scala.None)
                      .map(id => TypeRef(NoPrefix, id))
                      .orElse(inheritedTp(f))                       // what THIS class instantiated it as
                      .getOrElse(TypeBounds(NoType, NoType))
                  })
          case args => AppliedType(head, args.map(tpe))

    /** id of a referenced class type — our own or an external stub. Carries `@FunctionalInterface`
      * from the class file when readable (JS-C52, whether a method reference needs an explicit
      * lambda). REFUTER polarity (CLAUDE.md §4.56): unreadable means unannotated, the safe direction. */
    private def typeSym(r: CtTypeReference[?]): SymId =
      val anns = try
        val td = r.getTypeDeclaration
        if td != null && td.getAnnotations.asScala.exists(
          _.getAnnotationType.getQualifiedName == "java.lang.FunctionalInterface")
        then List(Annot(TypeRef(NoPrefix, minter.external("java.lang.FunctionalInterface", "FunctionalInterface")), Nil, Origin.synthetic))
        else Nil
      catch case _: Exception => Nil // unreadable class file — refuter polarity: treat as unannotated
      minter.external(typeKey(r), r.getSimpleName, annotations = anns)

    private def primName(j: String): String = j match
      case "int"     => "Int";  case "long"    => "Long";  case "short"  => "Short"
      case "byte"    => "Byte"; case "char"    => "Char";  case "boolean" => "Boolean"
      case "float"   => "Float"; case "double" => "Double"; case "void"  => "Unit"
      case other     => other.capitalize

    // ---- declarations ----
    /** @param owner overrides the DECLARING TYPE as owner; set only for a METHOD-LOCAL class (JS-C30).
      * @param sourceName overrides the symbol's name; set only for a local class (Spoon's qualified
      *   name holds java's binary disambiguator, not a legal Scala identifier).
      * @param selfClass , outerVars the ANONYMOUS-class wiring, reused verbatim for local classes. */
    private def classDef(t: CtType[?], owner: Option[SymId] = scala.None,
                         sourceName: Option[String] = scala.None,
                         selfClass: SymId = SymId.None,
                         outerVars: Map[String, SymId] = Map.empty): Tree.ClassDef =
      val id   = defineType(t, owner, sourceName)
      // claimed FIRST, before any member translates, so a member's deepComments cannot reach it
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
      // fields carry their source positions — JLS 12.5 step 4 interleaves them with init blocks (step4 below)
      val local  = owner.isDefined
      val bodySelf = if local then id else SymId.None
      // no `catch` — a swallowed failure would be indistinguishable from "not a local class" (§4.6)
      val bodyQName = if local then t.getQualifiedName else ""
      val fields = t.getFields.asScala.toList
        .filterNot(_.isInstanceOf[CtEnumValue[?]])
        .sortBy(posKey)
        .map(f => posKey(f) -> fieldDef(id, f, selfClass, outerVars, bodySelf, bodyQName))
      // include enum constructors — the emitter folds their params into the sealed class's primary ctor
      // Substitutions.dropMethods, keyed owner#name or owner#name(P1,P2) for one overload
      def isDropped(e: CtExecutable[?], name: String): Boolean =
        subs.dropsMethod(t.getQualifiedName, name,
          e.getParameters.asScala.toList.map(p => Option(p.getType).map(_.getSimpleName).getOrElse("?")))
      seenTypes += t.getQualifiedName
      /** one row of [[seenMembers]] — WHAT THIS WALK SAW at this member's identity. */
      def note(e: CtExecutable[?], nm: String, sym: Option[SymId], dropped: Boolean): Unit =
        seenMembers += MemberKey(t.getQualifiedName, nm, descriptorOf(e)) ->
          MemberFacts(sym, execFlags(e), originOf(e), dropped)
      /** Record what this walk SAW, then translate what survives — the last place the dropped half
        * exists ([[seenMembers]]). Interleaved so minting order stays exactly what it was, since a
        * run's `SymId` assignment is deterministic-artifact-load-bearing. */
      def walked[E <: CtExecutable[?]](es: List[E], nameOf: E => String)(mk: E => Tree.DefDef): List[Tree.DefDef] =
        es.flatMap { e =>
          val nm = nameOf(e)
          if isDropped(e, nm) then { note(e, nm, scala.None, dropped = true); Nil }
          else
            val d = mk(e)
            note(e, nm, Some(d.symbol), dropped = false)
            List(d)
        }
      // JS-C43: Spoon does not synthesise a nested record's implicit canonical constructor — repair it
      t match
        case r: CtRecord => r.createCanonicalConstructorIfMissing()
        case _           => ()
      val ctors = t match
        case c: CtClass[?] => walked(c.getConstructors.asScala.toList.sortBy(posKey), _ => "<init>")(
                                execDef(id, _, "<init>", selfClass, outerVars, false, bodySelf, bodyQName))
        case _             => Nil
      // ordinary methods consult the hierarchy for `override` — RefChecks never ran to report its absence (§3)
      val methods = walked(t.getMethods.asScala.toList.sortBy(posKey), _.getSimpleName)(m =>
        execDef(id, m, m.getSimpleName, selfClass, outerVars, overridesInherited(m), bodySelf, bodyQName))
      // java initialiser blocks (`static { }`, instance `{ }`) — translated as synthetic members;
      // silent omission is forbidden (DESIGN.md §3.4). Emitter inlines at the equivalent scala point.
      val initBlocks = t match
        case c: CtClass[?] =>
          c.getAnonymousExecutables.asScala.toList.sortBy(posKey).map { ae =>
            val nm = if ae.hasModifier(ModifierKind.STATIC) then "<clinit>" else "<initblock>"
            val d  = execDef(id, ae, nm, selfClass, outerVars, false, bodySelf, bodyQName)
            // an initialiser block is an index entry (a port can key policy on it, e.g. body substitution)
            // NOT routed through `walked`: `dropMethods` does not apply to initialisers
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
      // JLS 12.5 step 4 is ONE textual-order sequence — fields and instance init blocks interleaved.
      // Sorted AFTER every symbol is minted (minting order must stay deterministic, `sortBy` is stable).
      val step4 = (fields ++ initBlocks).sortBy(_._1).map(_._2)
      // JS-C43: `isRecord` is CLEARED when the component join fails — it means "record AND every
      // synthesised member's source still declares", not merely "java wrote `record`".
      val comps = recordComponents(t, id)
      if t.isInstanceOf[CtRecord] then
        minter.defined(typeKey(t.getReference)).foreach((sid, sy) =>
          minter.set(sid, sy.copy(components = comps.getOrElse(Nil),
                                  flags = sy.flags.copy(isRecord = comps.isDefined))))
        // component field access untouched (JLS 8.10.1 private final); widening is `TirEmitter.recordClashWidening`'s job (§4.55)
      Tree.ClassDef(id, parents, selfType = None,
        body = step4 ++ canonicalised(t, id, comps.getOrElse(Nil), ctors) ++
               accessorBodies(t, id, comps.getOrElse(Nil), methods) ++ nested,
        origin = originOf(t), tparams = tpDefs, enumCases = enumCases, leading = lead)

    /** JS-C43 — an IMPLICIT record accessor RETURNS ITS COMPONENT'S FIELD (JLS 8.10.3), written out
      * explicitly for EVERY implicit accessor (a nested record's, from Spoon, calls itself forever
      * in scala's one namespace). An accessor the record WROTE is untouched (`isImplicit` gate). */
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
              // matches `stmts`' shape for a real `return`, so derived and written accessors are the same tree
              val nothing = TypeRef(NoPrefix, minter.external("scala.Nothing", "Nothing"))
              d.copy(rhs = Some(Tree.Block(List(Tree.Return(Some(read), nothing, at)),
                                           Tree.Literal(Constant.UnitC, unitT, at), unitT, at)))
        }

    /** java's RECORD COMPONENTS, in DECLARATION ORDER, each joined to its FIELD and ACCESSOR by the
      * same keys `fieldDef1`/`execDef` use — a name-keyed join cannot work once the emitter renames.
      * SORTED BY POSITION, not Spoon's `Set` iteration order. ALL OR NOTHING: any component missing
      * its field or accessor answers `None` for the WHOLE record (never a short list, which would
      * silently compute `equals`/`hashCode` over a subset). `Some(Nil)` for a genuinely empty record. */
    private def recordComponents(t: CtType[?], id: SymId): Option[List[RecordComponent]] = t match
      case r: CtRecord =>
        val declared = r.getRecordComponents.asScala.toList.sortBy(posKey)
        val joined = declared.flatMap { c =>
          val nm  = c.getSimpleName
          // the ACCESSOR is java's own definition (JLS 8.10.3): the nilary method with the component's name
          val acc = t.getMethods.asScala.find(m => m.getSimpleName == nm && m.getParameters.isEmpty)
          for
            (fid, _) <- minter.defined(memberKey(id, nm))
            m        <- acc
            (aid, _) <- minter.defined(memberKey(id, nm + erasedSig(m)))
          yield RecordComponent(nm, fid, aid)
        }
        Option.when(joined.sizeIs == declared.size)(joined)
      case _ => scala.None

    /** THE CANONICAL CONSTRUCTOR AS JLS 8.10.4 DECLARES IT — two parser defects repaired: (1) a
      * compact constructor's implicit trailing field assignments, appended after Spoon's modeled
      * body, components matched to parameters BY POSITION; (2) the implicit constructor's parameter
      * order, reordered to the HEADER's order by reading which field each parameter assigns to. */
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
          case Tree.Assign(Tree.Select(_: Tree.This, f, _, _), Tree.Ident(p, _, _), _, _, _) => p -> f
        }.toMap
        val want = comps.map(_.field)
        val inOrder = want.flatMap(f => ps.find(p => assignedTo.get(p.symbol).contains(f)))
        if inOrder.sizeIs != ps.size || inOrder.sizeIs != want.size then d
        else d.copy(paramss = List(inOrder) ++ d.paramss.drop(1))

    /** a Java enum constant → `EnumCase`: ctor args, and any per-constant method/field overrides
      * from its anonymous-class body, keyed under the CONSTANT so they don't collide with the
      * enum's abstract members. FIELDS included (JLS 8.1.3 permits `static final` constants in an
      * anonymous class) — harvesting only `CtMethod` silently dropped them (4 errors, uncounted). */
    private def enumCase(enumId: SymId, v: CtEnumValue[?]): Tree.EnumCase =
      val vlead = leadingOf(v)
      val caseId = minter.define(memberKey(enumId, v.getSimpleName))(sid =>
        Symbol(sid, v.getSimpleName, qualified(enumId, v.getSimpleName), Flags(isStatic = true), enumId, TypeRef(NoPrefix, enumId))
      )
      val bt = new BodyTranslator(enumId, enumId)
      val (args, body) = v.getDefaultExpression match
        case nc: CtNewClass[?] =>
          val a = nc.getArguments.asScala.toList.map(bt.exprOf)
          val dropped = List.newBuilder[String]
          val b = Option(nc.getAnonymousClass).toList.flatMap(_.getTypeMembers.asScala.toList.sortBy(posKey)).flatMap {
            case f: CtField[?]  => List(fieldDef(caseId, f))
            case m: CtMethod[?] => List(execDef(caseId, m, m.getSimpleName, overrides = overridesInherited(m)))
            case c: CtConstructor[?] if c.isImplicit => Nil // compiler-synthesised anonymous ctor
            case a: CtAnonymousExecutable if !a.hasModifier(ModifierKind.STATIC) =>
              List(execDef(caseId, a, "<initblock>"))
            case other =>
              dropped += s"${other.getClass.getSimpleName.stripSuffix("Impl")} ${other.getSimpleName}"
              Nil
          }
          (a, b)
        case cc: CtConstructorCall[?] => (cc.getArguments.asScala.toList.map(bt.exprOf), Nil)
        case _                        => (Nil, Nil)
      Tree.EnumCase(caseId, args, body, originOf(v), leading = vlead ++ deepComments(v))

    /** distinguishes anonymous classes within one enclosing type; traversal order is deterministic
      * (every member list is `sortBy(posKey)`), so the minted keys are stable across runs. */
    private var anonSeq = 0

    /** A Java ANONYMOUS CLASS — `new Base(args) { members }`. Members are owned by a SYNTHETIC
      * symbol (so two listeners' `clicked` cannot collide), but bodies translate with `this` bound
      * to the ENCLOSING class (Spoon's own reading of the implicit `this`; emitter renders
      * `Outer.this.m`). Captured locals seeded by NAME, closed over directly. */
    private def anonClass(nc: CtNewClass[?], enclosing: SymId, outerVars: Map[String, SymId]): Option[Tree.AnonClass] =
      Option(nc.getAnonymousClass).map { ac =>
        anonSeq += 1
        val key = s"${minterKeyOf(enclosing)}#<anon>$anonSeq"
        val id = minter.define(key)(sid =>
          Symbol(sid, "<anon>", minter.fullNameOf(enclosing) + "$" + anonSeq, Flags(), enclosing, TypeRef(NoPrefix, sid))
        )
        // the name Spoon gives the anonymous class (`DragAndDrop$1`) — how a `this` inside the body
        // that means the ANONYMOUS instance is recognised.
        val qname = ac.getQualifiedName
        // alias Spoon's qualified name to this SymId so external lookups find it, not a second one
        minter.alias(qname, id)
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
        // SAM question asked of the type `new` NAMED, not the anonymous class Spoon synthesised
        val sam = try samAnswerOf(nc.getType) catch { case _: Throwable => Sam.Answer.Unreadable }
        Tree.AnonClass(id, members, originOf(nc), dropped.result(), sam)
      }

    /** Does this anonymous-class method OVERRIDE an inherited one? Scala requires `override` when
      * concrete, permits it when abstract — marking every genuine override is safe either way.
      * Spoon answers it from the resolved hierarchy where it can; falls back to name+arity over
      * resolvable supertypes; `java.lang.Object`'s members need [[universalMember]] separately
      * since Spoon has no `Object` in the model under noClasspath. */
    private def overridesInherited(m: CtMethod[?]): Boolean =
      // a java STATIC method never overrides — it HIDES; excluded here since Spoon's own
      // `getTopDefinitions` cannot tell the two apart
      !(m.isStatic) &&
        (universalMember(m) || inheritedFromSource(m))

    /** Does this redeclare one of `java.lang.Object`'s members? Matched on the full SIGNATURE, not
      * name and arity: `equals(VertexAttribute)` is an OVERLOAD that overrides nothing, and marking
      * it `override` is an error scala reports and java has no opinion on. */
    private def universalMember(m: CtMethod[?]): Boolean =
      val ps = m.getParameters.asScala.toList.map(p =>
        Option(p.getType).map(_.getQualifiedName).getOrElse("?"))
      (m.getSimpleName, ps) match
        case ("toString" | "hashCode" | "clone" | "finalize", Nil) => true
        case ("equals", List("java.lang.Object"))                   => true
        case _                                                       => false

    private def inheritedFromSource(m: CtMethod[?]): Boolean =
      val top = m.getTopDefinitions.asScala.toList
      if top.exists(_ ne m) then true
      else
        val n   = m.getSimpleName
        // by full SIGNATURE, not arity — java overloads freely
        def sig(x: CtMethod[?]): List[String] =
          x.getParameters.asScala.toList.map(p => Option(p.getType).map(_.getQualifiedName).getOrElse("?"))
        val mine = sig(m)
        // ancestor signature read under the `extends` clause's SUBSTITUTION (ENGINE-LIMITS K28.2) —
        // an exact-string comparison silently misses an override through a generic superclass.
        // frame composed one edge at a time; a RAW supertype contributes an EMPTY frame (declines,
        // which errs toward a missing `override` rather than a spurious one).
        def declares(t: CtTypeReference[?], subst: Map[String, String], fuel: Int): Boolean =
          if t == null || fuel <= 0 then false
          else
            val decl = typeDeclarationOf(t).orNull
            if decl == null then false
            else
              // this declaration's own frame: formals bound to the `extends` clause's arguments
              val formals = decl.getFormalCtTypeParameters.asScala.toList.map(_.getSimpleName)
              val actuals = (t.getActualTypeArguments.asScala.toList)
                .map { a =>
                  val q = a.getQualifiedName
                  subst.getOrElse(q, q)
                }
              val here = if formals.sizeIs == actuals.size then formals.zip(actuals).toMap else Map.empty[String, String]
              // a PRIVATE ancestor method is not inherited, so it cannot be overridden
              decl.getMethods.asScala.exists(x => x.getSimpleName == n && (x ne m) &&
                                                  sig(x).map(s => here.getOrElse(s, s)) == mine &&
                                                  !(x.isPrivate)) ||
                (decl match { case c: CtClass[?] => declares(c.getSuperclass, here, fuel - 1); case _ => false }) ||
                (decl.getSuperInterfaces.asScala.exists(declares(_, here, fuel - 1)))
        m.getDeclaringType match
          case c: CtClass[?] =>
            declares(c.getSuperclass, Map.empty, 8) ||
              (c.getSuperInterfaces.asScala.exists(declares(_, Map.empty, 8)))
          case _ => false

    private def defineType(t: CtType[?], owner: Option[SymId] = scala.None,
                          sourceName: Option[String] = scala.None): SymId =
      val q = typeKey(t.getReference)
      // substituted type stays in the model with resolved references, tagged for later rewriting
      val tags: Set[SymTag] = if subs.dropsType(q) then Set(Substituted(q)) else Set.empty
      // a type's annotation values are constant expressions (ENGINE-LIMITS T16); `resolve` first so
      // the translator can be built against the id before the record exists; WHICH families carry
      // is the port's ([[AnnotationPolicy]]), default empty
      val (anns, annDropped) =
        annotationsOf(t, Some(new BodyTranslator(minter.resolve(q), minter.resolve(q))), annotations.claims)
      minter.define(q)(id =>
        Symbol(id, sourceName.getOrElse(t.getSimpleName), q, typeFlags(t),
               owner.getOrElse(ownerSym(t)), TypeRef(NoPrefix, id), tags = tags,
               annotations = anns, droppedAnnotations = annDropped, permits = permittedTypes(t)))

    /** java's `permits` clause, INTERNED (ids, not names — [[balticporter.tir.Symbol.permits]]).
      * A permitted type the parse never saw interns as an external stub no subtype set can contain,
      * which is the case the seal must widen for. SORTED by key (Spoon hands back a `Set`). */
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

    /** …the DECLARATION obligation scope (`Dispatch.Declaration`) — a field initialiser is a JLS
      * 5.2 assignment slot like a local's, but `CtField` enters neither statement nor expression
      * dispatch, so it needed its own. Opens for EVERY field, initialiser or not (ENGINE-LIMITS F8). */
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
      // a STATIC field's initialiser sees NONE of the class's type parameters (java's rule, scala's too)
      val staticFrame = fieldFlags(f).isStatic
      if staticFrame then tpAccessible.prepend(Map.empty)
      val rhs =
        try
          val bt = new BodyTranslator(id, selfOf(owner, selfClass), anonSelf, anonQName)
          bt.seedVars(outerVars)
          // at the ARM, never inside `coerce` — unreached for a field with no initialiser
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
      // claimed before the body translates, so a statement's deepComments cannot reach the Javadoc
      val mlead = leadingOf(m)
      val allTps = m match
        case ftd: CtFormalTypeDeclarer => ftd.getFormalCtTypeParameters.asScala.toList
        case _                         => Nil
      // minus [[unwritableResultVars]] — erased to their own bound, no `[B <: …]` clause emitted
      val erasedTps = unwritableResultVars(m)
      val mtps      = allTps.filterNot(tp => erasedTps.exists(_.getSimpleName == tp.getSimpleName))
      val (frame, tpDefs) = mintTypeParams(mkey, id, mtps)
      val savedOverriding = inOverridingMember
      inOverridingMember = overrides
      tpScopes.prepend(frame); tpIsExec.prepend(true); tpDecls.prepend(declFrame(mtps))
      // TWO passes: an F-bound mentions ITSELF, so seed `?` first, then replace with the bound it produced
      tpErased.prepend(erasedTps.map(_.getSimpleName -> (TypeBounds(NoType, NoType): TypeRepr)).toMap)
      if erasedTps.nonEmpty then
        val bounds = erasedTps.map(tp => tp.getSimpleName -> tpe(tp.getSuperclass)).toMap
        tpErased.remove(0); tpErased.prepend(bounds)
      // a static method sees ONLY its own params, not the class's — carried in the FRAME, not the
      // per-executable `inStatic` flag (which an inner anonymous instance method would reset)
      tpAccessible.prepend(
        (if execFlags(m).isStatic then Map.empty else tpAccessible.headOption.getOrElse(Map.empty)) ++ frame)
      tpExecNames.prepend(tpExecNames.headOption.getOrElse(Set.empty) ++ frame.keySet)
      val bt = new BodyTranslator(id, selfOf(owner, selfClass), anonSelf, anonQName)
      bt.seedVars(outerVars) // an anonymous class captures the enclosing method's effectively-final locals
      val ps = m.getParameters.asScala.toList
      // `equals(Object)` must render as `equals(x: Any)` or it CLASHES with `AnyRef.equals` after
      // erasure instead of overriding it — `Any` is wider so no body using the param can break
      def anyForEquals(p: CtParameter[?]): TypeRepr =
        if name == "equals" && ps.sizeIs == 1 &&
           Option(p.getType).exists(_.getQualifiedName == "java.lang.Object")
        then TypeRef(NoPrefix, minter.external("scala.Any", "Any"))
        else tpe(p.getType)
      // parameter annotations, harvested like a field's/method's (previously never called, silently
      // dropping nullability contracts stated ON PARAMETERS — 389 sites on the most-annotated port)
      // fullName computed from OWNER, not `id` (whose symbol is not yet `set`)
      val methodFullName = qualified(owner, name)
      val pvs = ps.map { p =>
        val pt  = anyForEquals(p)
        val (panns, pannDropped) = annotationsOf(p, Some(bt))
        val paramFullName = methodFullName + "#" + p.getSimpleName
        val pid = minter.define(minterKeyOf(id) + "%" + p.getSimpleName)(sid =>
          Symbol(sid, p.getSimpleName, paramFullName, Flags(isParam = true, isVararg = p.isVarArgs), id, pt,
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
      // translate the body — makes Call/field-ref usages and `callersOf` real; abstract/interface
      // methods have none. An ANNOTATION TYPE ELEMENT also has none, but carries JLS 9.6.2's DEFAULT
      // off `getDefaultExpression` (§4.56 parser-hierarchy hazard: `CtAnnotationMethod extends CtMethod`)
      val body = Option(m.getBody).map(b => bt.methodBody(b)).orElse(annotationDefault(m, bt))
      tpScopes.remove(0); tpIsExec.remove(0); tpDecls.remove(0); tpAccessible.remove(0); tpExecNames.remove(0)
      tpErased.remove(0)
      inOverridingMember = savedOverriding
      Tree.DefDef(id, paramss = List(pvs), returnTpt = tt(ret, m), rhs = body, origin = originOf(m),
                  tparams = tpDefs, leading = mlead)
    }

    /** JLS 9.6.2's DEFAULT VALUE, for the one executable that has one and no body — `None`
      * otherwise. Emitter reads this `rhs` as the default of the constructor parameter the
      * `@interface` element becomes (`TirEmitter.classDef1`'s annotation arm). */
    private def annotationDefault(m: CtExecutable[?], bt: BodyTranslator): Option[Term] = m match
      case am: CtAnnotationMethod[?] =>
        Option(am.getDefaultExpression).map(e => bt.coercedExprOf(am.getType, e))
      case _ => scala.None

    /** Java annotations on a declaration, plus the names of any this could not carry. Element values
      * translate on the ordinary expression path; one that throws is REPORTED (`dropped`), never
      * silently emitted without its arguments. */
    private def annotationsOf(el: CtElement, bt: Option[BodyTranslator],
                              claimed: String => Boolean = _ => true): (List[Annot], List[String]) =
      val out     = collection.mutable.ListBuffer[Annot]()
      val dropped = collection.mutable.ListBuffer[String]()
      // a set that cannot be READ AT ALL is COUNTED, not read as "this declaration has none" (§4.6)
      val as =
        try el.getAnnotations.asScala.toList
        catch { case _: Throwable => dropped += SpoonTir.UnreadableAnnotations; Nil }
      as.foreach { a =>
        val ref = annotationTypeRefOf(a).orNull
        if ref == null then dropped += SpoonTir.UnresolvedAnnotation
        else
          val fqn = ref.getQualifiedName
          // a VALUE LIST that will not read is not an EMPTY one — `None` routes to reporting,
          // `Some(Nil)` is a real marker annotation
          val vals: Option[List[(String, Object)]] =
            try Some(a.getValues.asScala.toList)
            catch { case _: Throwable => scala.None }
          // no translator in scope: only MARKER annotations carry; one with args is REPORTED, not emitted bare
          if vals.contains(Nil) then
            out += Annot(TypeRef(NoPrefix, minter.external(fqn, simpleName(fqn))), Nil, originOf(a))
          // WITH ARGUMENTS carried only where a translator exists AND the port claims the family
          else if !claimed(fqn) then dropped += fqn
          else (bt, vals) match
            case (None, _)          => dropped += fqn
            // value list would not READ — arguments unknown, not absent
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
              // a FAILED translation is not a DECLINED one — the sentinel rides BESIDE the bare FQN
              // (never folded in) so every exact-name consumer still matches it
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
          typeDeclarationOf(ref).flatMap(d =>
                d.getAllMethods.asScala.find(_.getSimpleName == key).flatMap(m => Option(m.getType)))
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

    /** Java's FOURTH access level, and it is NOT "no modifier is present". Default access is
      * granted implicitly in three places (DESIGN §8.7): interface/`@interface` members (JLS 9.4,
      * 9.6, 9.5, 9.3) are `public`; enum constructors (JLS 8.9.2) are `private`; enum constants and
      * anonymous/local classes carry no user-written access. Read from the JLS, not
      * `hasModifier(PUBLIC)`, since the parser's implicit-modifier model is what's in question. */
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
        // JS-C44: the RAW java fact only — whether scala can reproduce the seal is the emitter's
        // question (`TirEmitter.sealOf`). WHICH types are permitted is recorded separately (`permittedTypes`)
        isSealed = has(t, SEALED),
        isTrait = isTrait,
        isEnum = t.isInstanceOf[CtEnum[?]],
        // JS-C43: raw java fact only — `CtRecord extends CtClass`, so without this flag nothing
        // downstream could tell a record from an ordinary class
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

    /** a METHOD-LOCAL class's SOURCE name (JLS 14.3, catalog JS-C30). Spoon's binary name
      * (`1Local`) is the interning key, not a legal identifier — strip the leading digit run
      * (JLS 3.8). Where stripping leaves nothing, keep the binary name (fails loudly, not silently). */
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

    /** MINT A MARKER instead of failing the whole compilation unit (DESIGN.md §6.2/§6.5) — the port
      * still doesn't ship (emission gate refuses any open marker, §6.4), but the failure is now the
      * size of the construct and the run REPORTS which one, where, and a possible fix. Falls back
      * to [[unsupported]]'s throw only where there is no position to key [[Tree.Unportable.markerKey]] on. */
    /** `about` is the node the refusal is ABOUT, where that differs from the node the marker STANDS
      * at — needed where the unlowered node has no source POSITION (e.g. Spoon's unpositioned
      * `CtCasePattern`), so the marker anchors on a positioned enclosing node while the KIND still
      * comes from the node with no arm. */
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
          // catalog pointer only for a refusal about the NODE KIND itself — an in-arm blind spot is a different fact
          diff   = if kind.isEmpty then SpoonKinds.byName.get(kindName).flatMap(_.catalog) else scala.None,
          what   = what,
          tpe    = tpe,
          origin = o,
        )

    // -----------------------------------------------------------------------
    /** Translates one method/ctor/field-initializer body into TIR terms, resolving every reference
      * to a `SymId`. Unmodeled constructs fail loudly via `Unsupported`.
      * `classId` is the enclosing class (for `this`); `methodId` owns locals.
      * `anonSelf`/`anonQName`, set only for an ANONYMOUS class's members: the synthetic instance
      * symbol and Spoon's name for it — `classId` stays the ENCLOSING class regardless. */
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

      /** A `this` used as a VALUE. Inside an anonymous class body it denotes the ANONYMOUS instance,
        * not the enclosing one — only for a `this` Spoon EXPLICITLY types as the anonymous class,
        * and only in value position (as a member-access TARGET, the existing bare-name resolution
        * stays in charge, matching java's own lexical resolution). */
      private def thisOf(ta: CtThisAccess[?], el: CtElement): Term =
        if anonSelf != SymId.None && anonQName.nonEmpty &&
           Option(ta.getType).map(_.getQualifiedName).contains(anonQName)
        then Tree.This(anonSelf, TypeRef(NoPrefix, anonSelf), originOf(el))
        else thisTerm(el)

      /** `Outer.this` — the enclosing instance. Only for a type that LEXICALLY ENCLOSES the access;
        * Spoon also reports a plain `this` typed at an INHERITED member's DECLARING type, which
        * `Outer.this` syntax cannot denote and must not be qualified as. */
      private def outerThis(ta: CtThisAccess[?]): Option[Term] =
        val q     = ta.getType.getQualifiedName
        var here  = ta.getParent(classOf[CtType[?]])
        var found = false
        // walk OUT only while each step captures an enclosing (non-static inner) instance
        while here != null && capturesEnclosing(here) && !found do
          here = here.getDeclaringType
          if here != null && here.getQualifiedName == q then found = true
        // an ANONYMOUS enclosing class has NO NAME — emitted bare, it resolves lexically as java did
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
      // ORDER matters: (1) leadingOf(s) claims Spoon's own attachment, (2) stmt(s) translates
      // (nested statements claim their own), (3) deepComments(s) scoops what is left. A trailing
      // comment with nothing after it goes to `Tree.Block.trailing`, never discarded.

      /** Translate a statement list, folding comment-statements into the statement that follows.
        * Second half of the pair is what is LEFT (a block's `trailing`) — returned, not attached,
        * since not every statement list is a block. */
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
            // declarations (ValDef/ClassDef/DefDef) carry their own `leading` field — no `Tree.Commented` wrapper (not a Term)
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

      /** One statement, with a java LABEL on a non-loop statement turned into [[Tree.Labeled]] —
        * `break L` leaves THAT statement, so a labelled `if`/block/`switch` needs a node. A LOOP's
        * label is read into its own node field instead (also `continue L`'s target). */
      private def stmt(s: CtStatement): Statement =
        val k = stmtKind(s)
        labelOf(s) match
          // a labelled loop already carries its label; a `ValDef` cannot be labelled (JLS 14.7)
          case Some(l) if !carriesOwnLabel(k) =>
            k match
              case t: Term => TirTrace.mint(Tree.Labeled(l, t, unitT, originOf(s)))
              case other   => other
          case _ => k

      /** does this translated statement already hold its java label in a field of its own? */
      private def carriesOwnLabel(k: Statement): Boolean = k match
        case _: Tree.While | _: Tree.For | _: Tree.ForEach | _: Tree.DoWhile => true
        case _                                                               => false

      /** JS-E03/E04's PREDICATE, as ONE function (not copied at its two consult sites): the target
        * type when java's implicit narrowing applies to a compound assignment, else `None`. Narrows
        * whenever `max(rhsRank, intRank) > targetRank` (java's binary numeric promotion). */
      private def compoundNarrow(a: CtOperatorAssignment[?, ?]): Option[CtTypeReference[?]] =
        val lt = a.getAssigned.getType
        val rt = a.getAssignment.getType
        val narrow = lt != null && lt.isPrimitive && rt != null && rt.isPrimitive &&
          primRank.get(lt.getSimpleName).exists(l =>
            primRank.get(rt.getSimpleName).exists(r => math.max(r, primRank("int")) > l))
        if narrow then Some(lt) else scala.None

      /** THE STATEMENT DISPATCH — obligation wrapper sits HERE, not per-arm, so no arm can opt out
        * of it (DESIGN.md §2.8). `Lowering.of` maps the runtime class to its registry name once. */
      private def stmtKind(s: CtStatement): Statement =
        // `s` is the SUBJECT — the node itself, so a delegation into the expression dispatch
        // (`case inv: CtInvocation => expr(inv)`) can be joined to this scope by identity.
        Lowering.of(SpoonKinds.nameOf(s.getClass), Dispatch.Statement, originOf(s), s)(stmtArm(s))

      private def stmtArm(s: CtStatement)(using Obligations): Statement = s match
        case v: CtLocalVariable[?] =>
          val vt = tpe(v.getType)
          val id = defineLocal(v, vt) // sets isMutable when the local is reassigned
          // JS-G09/G13/G14 slot rows — no initialiser means empty list, honest "does not apply"
          slotConsults(Option(v.getDefaultExpression).map(v.getType -> _).toList, originOf(v))
          val rhs = Option(v.getDefaultExpression).map(e => coerce(v.getType, e, expr(e)))
          Tree.ValDef(id, tt(vt, v), rhs, originOf(v))
        case a: CtOperatorAssignment[?, ?] =>
          // Java compound assignment narrows implicitly: `int += float` means `= (int)(i + f)`.
          val lhs = expr(a.getAssigned)
          val rhs = expr(a.getAssignment)
          val op  = opText(a.getKind).getOrElse { unknownOp(a.getKind, a, ty(a)); "?" }
          // JS-E03 CONSULTED, not just done, so the coverage lane can count the decision
          val narrow = Obligations.consult(JS.E(3), originOf(a))(compoundNarrow(a))
            .map(t => tpe(t))
          // JS-E17: lvalue single evaluation (F7) — emitter binds non-trivial subexpressions once
          Obligations.consult(JS.E(17), originOf(a))(Some(()))
          Tree.Assign(lhs, rhs, unitT, originOf(a), compound = Some((op, narrow)))
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
          // SE9 form (`try (existingLocal)`, JLS 14.20.3) is a variable REFERENCE, not a
          // `CtLocalVariable` — refused LOUDLY (M6) rather than silently closing one resource fewer
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
            case POSTINC | PREINC =>
              val t = expr(u.getOperand)
              val narrow = incNarrowType(u.getOperand)
              Tree.Assign(t, one, unitT, originOf(u), compound = Some(("+", narrow)))
            case POSTDEC | PREDEC =>
              val t = expr(u.getOperand)
              val narrow = incNarrowType(u.getOperand)
              Tree.Assign(t, one, unitT, originOf(u), compound = Some(("-", narrow)))
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

      /** THE ENHANCED-FOR'S ITERABLE, at the type JAVA READ IT AT (ENGINE-LIMITS G31). JLS 14.14.2
        * iterates at `Iterable<T>` found among the supertypes, not the expression's own type — an
        * F-BOUNDED wildcard capture fails at an INFERRED type in scala (`E057`) unless ascribed here.
        * Ordinary bounded wildcards are left alone (capture-convert unaided, §5's widening rule).
        * Declines where the found `Iterable` argument mentions a type VARIABLE (§4.6). */
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
          val formals = typeDeclarationOf(r).map(_.getFormalCtTypeParameters.asScala.toList).getOrElse(Nil)
          formals.zip(as).exists { (f, a) =>
            TypeShape.of(a).isInstanceOf[TypeShape.Wildcard] &&
              Option(f.getSuperclass).exists(b => mentionedTypeVarNames(b)(f.getSimpleName))
          }
        case _ => false

      /** `java.lang.Iterable<E>` as reached from `r`'s supertypes, and only where `E` is a type this
        * scope can WRITE — java's own enhanced-for lookup, with §4.6's honest decline. */
      private def javaIterableSuper(r: CtTypeReference[?]): Option[CtTypeReference[?]] =
        def walk(ref: CtTypeReference[?], fuel: Int): Option[CtTypeReference[?]] =
          if ref == null || fuel <= 0 then scala.None
          else if ref.getQualifiedName == "java.lang.Iterable" then Some(ref)
          else
            val d = typeDeclarationOf(ref).orNull
            if d == null then scala.None
            else
              val ups = (d match { case c: CtClass[?] => Option(c.getSuperclass).toList; case _ => Nil }) ++
                        (d.getSuperInterfaces.asScala.toList)
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
        * view of the access was ERASED (a RAW-bounded type variable). Restates the unchecked step.
        * Guarded on the parameter's name resolving here and the value not already having that type. */
      private def toDeclaredTypeParam(assigned: CtExpression[?], e: CtExpression[?], t: Term): Term =
        declaredTypeOf(assigned) match
          case Some(tp: CtTypeParameterReference) => toTypeParam(tp, e, t)
          case _                                  => t

      /** the DECLARED type of an assignment target — the field's / local's own declaration, not
        * Spoon's (possibly raw-erased) view of the access. The field path goes through
        * [[fieldDeclOf]]; the variable path wraps `getDeclaration` for the same noClasspath
        * reason — a local's declaration is absent when the enclosing method is external. */
      private def declaredTypeOf(assigned: CtExpression[?]): Option[CtTypeReference[?]] =
        assigned match
          case fw: CtFieldWrite[?]    => fieldDeclOf(fw.getVariable).map(_.getType)
          case vw: CtVariableWrite[?] =>
            try Option(vw.getVariable.getDeclaration).map(_.getType)
            catch { case _: Throwable => None }
          case _                      => None

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

      /** Java's UNCHECKED generic conversion — a RAW-typed value converts to any instantiation
        * without a check. Raw uses render CONTEXT-dependently, so the same java type can render two
        * ways in two scopes; emit exactly the cast java performs implicitly. Gated to targets whose
        * type variables all resolve here (never synthesize a `?T` stub). */
      /** JS-G31 — a POLY EXPRESSION (JLS 15.2): a LAMBDA or a METHOD REFERENCE, typed by the slot it
        * fills, in both languages. A cast at such an argument would elaborate the literal to a
        * `scala.FunctionN` FIRST, then fail the cast — so the faithful emission is the literal AT
        * THE SLOT, never a cast (probed against scala 3.8.4 for every SAM-conversion shape). ONE
        * function: written twice before, the two copies disagreed (ENGINE-LIMITS F8). */
      private def polyExpression(e: CtExpression[?]): Boolean =
        e.isInstanceOf[CtLambda[?]] || e.isInstanceOf[CtExecutableReferenceExpression[?, ?]]

      /** JS-G31's answer AT THE CALL — every POLY-EXPRESSION argument restored to what `expr`
        * produced, with any cast an argument arm added removed. Answered ONCE here rather than per
        * arm (six and growing). ARITY answered PER INDEX, never by declining the whole call: a
        * vararg-packed tail is answered INSIDE the array against the arguments it was built from. */
      private def polyArgsUncast(argEs: List[CtExpression[?]], args: List[Term], at: Origin)
                                (using Obligations): List[Term] =
        Obligations.consult(JS.G(31), at) {
          val poly = argEs.zipWithIndex.collect { case (e, i) if polyExpression(e) => i }.toSet
          if poly.isEmpty then scala.None
          else if args.sizeIs == argEs.size then
            Some(args.zipWithIndex.map { (t, i) => if poly(i) then uncastAdded(t, argEs(i)) else t })
          else packedUncast(argEs, args, poly)
        }.getOrElse(args)

      /** …the OTHER half: a poly expression takes its type from the SLOT, and an OVERLOAD SET gives
        * scala no single slot to type a lambda literal from (javac resolves by argument SHAPE;
        * scalac types the literal FIRST — `E134`, probed at scala 3.8.4). Ascribes an ASCRIPTION,
        * never a CAST (polyExpression's refusal still stands — a cast would elaborate the literal
        * to a `Function0` first, then fail). Fires only when: the argument is a LAMBDA (a method
        * reference is excluded, handled by `TirEmitter.samAscribed`); the callee is OVERLOADED at
        * this arity with the slot naming no expected type ([[overloadedSamSlot]]); the target is
        * NAMEABLE HERE ([[tpNameableHere]]) and java wrote no cast of its own. Target is the
        * LAMBDA'S OWN type (same as [[samResultTpt]]), never the callee's re-derived formal. */
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

      /** the LAMBDA whose own type is the target this argument ascribes to — the argument itself,
        * or a BRANCH of a poly CONDITIONAL (JLS 15.25 pushes the target type through both branches,
        * ENGINE-LIMITS K30 face 3). Target ascribed on the WHOLE conditional, not each branch.
        * `polyExpression` deliberately NOT widened to match — different catalog population. */
      private def samLambdaOf(e: CtExpression[?]): Option[CtLambda[?]] = e match
        case l: CtLambda[?]      => Some(l)
        case c: CtConditional[?] =>
          List(c.getThenExpression, c.getElseExpression).collectFirst { case l: CtLambda[?] => l }
        case _                   => scala.None

      /** is the callee overloaded at this arity, AND does the slot at argument `i` fail to give
        * scala an expected type — [[polyArgsAscribed]]'s whole decision, read off the declaring
        * type's ALL methods (not just declared, since java's overload set spans the hierarchy) by
        * QUALIFIED NAME at that index. Fires when the alternatives DISAGREE at `i`, or agree on a
        * TYPE VARIABLE the call has yet to infer (scala must resolve the overload by typing the
        * arguments first, unlike java which solves `T` from another slot). Unreadable declaration →
        * no alternatives, nothing ascribed (§4.6); `RuntimeException` only, so a deep model's
        * `StackOverflowError` is not swallowed. */
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
          // the two shapes `varargPack` materialises — a SPREAD or an array literal; a third declines
          val packed = args.last match
            case r: Tree.Repeated => elems(r.elems).map(es => r.copy(elems = es))
            case n: Tree.NewArray => n.init.flatMap(elems).map(es => n.copy(init = Some(es)))
            case _                => scala.None
          packed.map(headTs :+ _)

      /** the casts an ARGUMENT ARM added, removed; the ones the JAVA SOURCE wrote, kept. Unreadable
        * cast list DECLINES (not "java wrote none") — a term left as-is is at worst a cast too many.
        * `RuntimeException` only, so a `StackOverflowError` is not swallowed (CLAUDE.md §4.58). */
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
        * class instantiated them with — `None` where nothing substitutes. Closes the gap where the
        * formal is literally an ancestor's own type variable (`isGenericUse` declines it, though
        * ENGINE-LIMITS G12's rule against resolving a callee's own variables does not apply — the
        * `extends` clause says what THIS class instantiated it as, same fact as `ParentSubst`,
        * CLAUDE.md §4.56). Keyed by (owner FQN, formal name), never by name alone. Does NOT
        * substitute a WILDCARD formal (`tpe` has no shape for it) — declines rather than misrenders. */
      /** how many `[]` a type reference carries — the ARITY half of `ENGINE-LIMITS.md` G26's
        * comparison, which is the one thing that decides whether a cast at an inherited formal is a
        * translation or a `ClassCastException`. */
      private def arrayDims(tr: CtTypeReference[?]): Int = tr match
        case a: CtArrayTypeReference[?] => 1 + arrayDims(a.getComponentType)
        case _                          => 0

      private def inheritedFormal(tr: CtTypeReference[?], fuel: Int = 6): Option[TypeRepr] =
        if fuel <= 0 then scala.None
        else TypeShape.of(tr) match
          // wildcard arm above variable — declines either way, deliberately (see doc above)
          case TypeShape.Wildcard(_, _, _) => scala.None
          case TypeShape.Variable(tv) =>
            for
              d     <- typeParamDeclOf(tv)
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
        // the type the argument has WHERE IT STANDS ([[castType]], not `e.getType`) — a cast is
        // what moves it, and the pre-cast type mentions no raw generic at all
        val et = castType(e)
        // a CLASS LITERAL's Spoon type lies about raw-ness (`AddAction.class` types raw `Class`,
        // emits `classOf[AddAction]`); casting it would destroy the inference it feeds
        val classLit = e match
          case fr: CtFieldRead[?] => fr.getVariable.getSimpleName == "class"
          case _                  => false
        // a METHOD REFERENCE belongs with the lambda: both are poly expressions typed FROM the target
        val bad = classLit || polyExpression(e) || e.isInstanceOf[CtLiteral[?]] ||
          e.isInstanceOf[CtNewArray[?]] || e.isInstanceOf[CtConditional[?]]
        // …the INHERITED formal, which the gates below cannot reach: a formal written as an
        // ancestor's own type variable is not a `isGenericUse` at all, and `tpResolvable` answers
        // `false` for it because the variable is not in THIS class's scope. See [[inheritedFormal]]
        // — the `extends` clause resolves it, exactly and only for that case.
        //
        // A DIMENSION MISMATCH DECLINES rather than casts (ENGINE-LIMITS G26): a cast there would
        // make an arity defect COMPILE and throw at run time instead of a loud typer error (§3).
        // Computed ONCE, behind the two cheap tests, so the denominators do not move for nothing.
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
          // a CALLEE's formal belongs to its own declaration, not the caller's inherited
          // instantiation — except for a callee this class DECLARES itself, whose formals are
          // written in ITS OWN variables (measured: restricting too narrowly gave 3->2, too wide 3->35)
          val ownCallee =
            Option(e.getParent(classOf[CtInvocation[?]]))
                  .flatMap(inv => Option(inv.getExecutable.getDeclaringType))
                  .exists(dt => !ancestorFqns.headOption.getOrElse(Set.empty).contains(dt.getQualifiedName))
          val savedOv = inOverridingMember
          if ownCallee then inOverridingMember = false
          val ct = try if ownScope then tpe(target) else tpBoundErased(target)
                   finally inOverridingMember = savedOv
          Tree.Typed(t, tt(ct, e), ct, originOf(e))

      /** JS-G13's clause, as a function of the SLOT — java's array covariance (JLS 10.10) puts a
        * value of one array type where another is declared, and scala's `Array` is invariant.
        * Extracted so [[coerce]]/[[slotConsults]] read ONE predicate (ENGINE-LIMITS F8). */
      private def arrayCovSlot(target: CtTypeReference[?], et: CtTypeReference[?]): Boolean =
        target != null && target.isInstanceOf[CtArrayTypeReference[?]] && et != null &&
          et.isInstanceOf[CtArrayTypeReference[?]] && target.getQualifiedName != et.getQualifiedName

      /** …the SAME question asked at the RENDERING, which a java-name test cannot see: java's own
        * ERASURE can collapse two array types into one (e.g. an F-bounded `<E> E[] getUniverse`),
        * so [[arrayCovSlot]] finds nothing to compare while the emitted `Array[E]` still disagrees
        * with `Array[Enum[?]]` (scala arrays are INVARIANT). `want` is HANDED IN, not re-looked-up
        * — a second `tpe(target)` moves the lowering denominators for nothing (measured, 1,675). */
      private def arrayCovRendered(target: CtTypeReference[?], want: TypeRepr, t: Term): Boolean =
        target != null && target.isInstanceOf[CtArrayTypeReference[?]] &&
          isScalaArrayType(t.tpe) && isScalaArrayType(want) && want != t.tpe

      /** JS-G14's clause — a primitive at a reference slot is java autoboxing, and the boxing's
        * target is the WRAPPER rather than the (often erased) formal. See [[arrayCovSlot]] for why
        * this is a named predicate rather than an inline condition. */
      private def boxingSlot(target: CtTypeReference[?], et: CtTypeReference[?]): Boolean =
        target != null && et != null && et.isPrimitive && !target.isPrimitive &&
          !target.isInstanceOf[CtTypeParameterReference] && !target.isInstanceOf[CtArrayTypeReference[?]]

      /** JS-G09's question at a slot — java's UNCHECKED CONVERSION (JLS 5.1.9), legal at a raw type,
        * no scala image but a cast. A SHAPE test, deliberately not [[uncheckedGeneric]]'s narrower
        * gate list, so a refused site still reports "the difference applies here". */
      private def uncheckedSlot(target: CtTypeReference[?], et: CtTypeReference[?]): Boolean =
        target != null && et != null && isGenericUse(target) &&
          (mentionsRawGeneric(et) || mentionsRawGeneric(target))

      /** THE SLOT ROWS, consulted at every arm that has a slot — JS-G09, JS-G13, JS-G14. One
        * function, six call sites (`Differences.everySlot`, one JLS 5.2 conversion). Called from the
        * ARM, never [[coerce]] (unreached for a slot-less node, honest discharge). Reads [[castType]]
        * bare, same as `coerce` (ENGINE-LIMITS K17, CLAUDE.md §4.6). */
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

      /** the (formal, argument) pairs of a call — the slot list [[slotConsults]] wants. Empty where
        * arities disagree (same case `coerceArgs` declines). Formals read BARE — a `catch` here
        * would fabricate "this callee takes no parameters" (CLAUDE.md §4.6). */
      private def argSlots(ex: CtExecutableReference[?], argEs: List[CtExpression[?]]):
          List[(CtTypeReference[?], CtExpression[?])] =
        val formals = ex.getParameters.asScala.toList
        if formals.sizeIs == argEs.size then formals.zip(argEs).filter(_._1 != null) else Nil

      private def coerce(target: CtTypeReference[?], e: CtExpression[?], t: Term, arrayCov: Boolean = true,
                         tpToObject: Boolean = true, unchecked: Boolean = true): Term =
        val isNull = e match { case l: CtLiteral[?] => l.getValue == null; case _ => false }
        // the type the COERCED TERM actually has — [[castType]], not `e.getType`, since `expr`
        // already folded java's own casts onto it (JLS 5.2, 5.3). Matters for `boxing` below, which
        // names the WRAPPER to convert to, not just whether to (ENGINE-LIMITS K17 face 3).
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
        // a boxed wrapper at a PRIMITIVE slot is java auto-UNBOXING (possibly with widening) — emit
        // the explicit `.xxxValue()`. Only a CROSS-type unbox needs this (same-type is Predef's job)
        if et != null && !et.isPrimitive && target.isPrimitive && wrapperOf.values.toSet(et.getQualifiedName)
          && wrapperOf.get(target.getSimpleName).exists(_ != et.getQualifiedName) then
          return unbox(t, et.getQualifiedName, target.getSimpleName, e)
        // a LOSSY WIDENING conversion (JLS 5.1.2) — scala's implicit int2float/long2float/long2double
        // are deprecated (precision loss); emit the explicit `.toFloat`/`.toDouble` instead
        if et != null && et.isPrimitive && target.isPrimitive then
          val pair = (et.getSimpleName, target.getSimpleName)
          val lossyTarget = pair match
            case ("int", "float") | ("long", "float")  => Some(("toFloat", "scala.Float"))
            case ("long", "double")                    => Some(("toDouble", "scala.Double"))
            case _                                     => scala.None
          lossyTarget.foreach { (method, resultFqn) =>
            val msym = minter.external(s"scala.${primName(et.getSimpleName)}#$method", method)
            return Tree.Select(t, msym, TypeRef(NoPrefix, minter.external(resultFqn, resultFqn.split('.').last)), originOf(e))
          }
        // a type-parameter value flowing into a genuinely-`Object` slot (a return/assignment/var-init
        // where the target type is really `java.lang.Object`, not an erased formal — call args are
        // handled by `typeParamToObject` off the DECLARED formal, so this stays off that path):
        // Java erases `T` to `Object`; Scala's unbounded `T <: Any` does not conform. Cast it.
        val tpObj = tpToObject && et != null && et.isInstanceOf[CtTypeParameterReference] &&
          target.getQualifiedName == "java.lang.Object"
        // box to the primitive's WRAPPER, not the (often Object-erased) formal — satisfies both an
        // erased `Object` slot and a real `Integer`/`Number` one. Hoisted ABOVE `cast` so
        // `arrayCovRendered` reuses this rendering rather than a second `tpe(target)` lowering.
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
          // a target naming an ANCESTOR's type variable is rendered through the `extends` clause
          // ([[uncheckedGeneric]]'s own fact, ENGINE-LIMITS G12) — else `tpe` renders a sentinel
          // `Array[?]`. Asked ONLY where a cast is really emitted, to avoid moving denominators for nothing.
          val cct = if mentionsAnyTypeVar(target) then inheritedFormal(target).getOrElse(ct) else ct
          Tree.Typed(t, tt(cct, e), cct, originOf(e))
        else if unchecked then
          // a CONDITIONAL's unchecked conversion belongs to its BRANCHES (java assigns each operand
          // to the target type separately, K30 face 3) — recurses through `coerce`, not
          // `uncheckedGeneric` directly, so each branch gets whatever conversion IT needs
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
          // decided on the RENDERED types, not Spoon's — an erased receiver's Spoon type still says
          // `Array<K>` while the emitted term is `Array[Object]`, which only the TIR's erased type sees
          if (u ne t) || !tpAccessibleHere(target) || !uncheckedFrom(t.tpe, ct) then u
          else Tree.Typed(t, tt(ct, e), ct, originOf(e))

      private val wrapperOf = Map(
        "byte" -> "java.lang.Byte", "short" -> "java.lang.Short", "char" -> "java.lang.Character",
        "int" -> "java.lang.Integer", "long" -> "java.lang.Long", "float" -> "java.lang.Float",
        "double" -> "java.lang.Double", "boolean" -> "java.lang.Boolean")
      private val valueMethod = Map(
        "int" -> "intValue", "long" -> "longValue", "float" -> "floatValue", "double" -> "doubleValue",
        "short" -> "shortValue", "byte" -> "byteValue", "boolean" -> "booleanValue", "char" -> "charValue")
      /** `wrapper.<prim>Value()` — explicit unboxing to a primitive, plus the WIDENING beside it
        * where a shortcut would name a nonexistent member. Java's unboxing is TWO conversions (JLS
        * 5.1.8 then 5.1.2); collapsed to one call for the six `Number` wrappers (ENGINE-LIMITS K17
        * face 2). `Character`/`Boolean` are NOT `Number`s — emitted as two explicit steps instead.
        * @param from the wrapper's FQN — the SOURCE, known by both callers. */
      private def unbox(t: Term, from: String, prim: String, e: CtElement): Term =
        def primT(p: String) = TypeRef(NoPrefix, minter.external("scala." + primName(p), p))
        // the wrapper's OWN primitive, and whether reaching `prim` from it needs a second step.
        val own    = wrapperOf.collectFirst { case (p, w) if w == from => p }.getOrElse(prim)
        val viaOwn = own != prim && (own == "char" || own == "boolean")
        val step   = if viaOwn then own else prim
        valueMethod.get(step) match
          case Some(vm) =>
            // owner deliberately left None for Number members (interning it would re-key every
            // downstream finding, measured). Two-step path keys on the WRAPPER instead — `charValue`
            // is not a `Number` member, and moving the existing key would re-key for no gain.
            val vsym = minter.external(if viaOwn then s"$from#$vm" else "java.lang.Number#" + vm, vm)
            val call = Tree.Apply(Tree.Select(t, vsym, NoType, originOf(e)), Nil, vsym, primT(step), originOf(e))
            if viaOwn then Tree.Typed(call, tt(primT(prim), e), primT(prim), originOf(e)) else call
          case None => t
      private def boxedPrimitive(prim: String): TypeRepr =
        wrapperOf.get(prim) match
          case Some(fqn) => TypeRef(NoPrefix, minter.external(fqn, simpleName(fqn)))
          case None      => TypeRef(NoPrefix, minter.external("java.lang.Object", "Object"))

      /** Java VARARGS at the CALL SITE. `T...` is emitted `Array[T]`, so a call passing elements
        * POSITIONALLY has to materialize the array java would build; an already-array or generic
        * component is left alone. Stops at the program's EDGE (ENGINE-LIMITS K6.5): an EXTERNAL
        * callee's `T...` is a class file scalac reads as REPEATED, so it gets `Tree.Repeated`
        * (emitted as elements, no spread syntax) instead of a pack. Ownership decided STRUCTURALLY
        * (§4.56) from the declaring type being a shadow, never from the name. */
      /** JS-G38's question, as a function of the vararg slot: does the argument ALREADY hold the
        * array java would otherwise build? Named because [[varargPack]] and [[callConsults]] both
        * ask it (ENGINE-LIMITS F8). Rules: the CAST wins where there is one (outermost first); a
        * PRIMITIVE array component must match exactly (java packs `int[]` at `Object...` into ONE
        * element, CLAUDE.md §4.4); a bare `null` IS the array; ARRAY DIMENSION decides the
        * reference case via `dims(arg) >= dims(comp) + 1` (java packs at `H[]...`, ENGINE-LIMITS G26). */
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

      /** the callee's declared parameters, or `scala.None` where the declaration cannot be read
        * (CLAUDE.md §4.6), shared by [[varargPack]]/[[callConsults]] so they never disagree. At a
        * `CtNewClass` the parser SYNTHESISES a wrong declaration (§4.59) — Spoon's anonymous-subtype
        * constructor has no real parameter list — so the SUPERCLASS's constructor is read instead
        * (JLS 15.9.5.1), chosen by the ERASED parameter types the reference carries. */
      private def declParams(ex: CtExecutableReference[?]): Option[List[CtParameter[?]]] =
        anonSuperCtor(ex).orElse(execDeclOf(ex))
              .map(_.getParameters.asScala.toList)

      /** the SUPERCLASS constructor an anonymous-class construction really invokes — see
        * [[declParams]]. Matches the erased signature FIRST, arity only where unambiguous (a
        * generic constructor's names don't meet under noClasspath erasure, JS-G18). */
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
        * Called from [[coerceArgs]] (the ONE function both `invocation`/`ctorCall` reach). Predicates
        * read off the REFERENCE/DECLARATION, not by re-running [[varargPack]]. */
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
            // already an array in the vararg slot — passed THROUGH where the callee is ours, a
            // SPREAD at an external one (see `passThrough`). Component types must agree (java
            // packs a primitive array mismatch instead, CLAUDE.md §4.4); reference components pass
            val passesArray = argEs.sizeIs == l.size && varargHoldsArray(comp, argEs.last)
            // the vararg element type, in priority order: the DECLARED component when concrete
            // (preferred over argument inference — ENGINE-LIMITS §0/G1, erase USES never
            // DECLARATIONS, 94 errors otherwise); else the RECEIVER's instantiation for a known
            // receiver's own type variable (ENGINE-LIMITS G12); else inferred from the trailing
            // arguments' own type, only when they all agree on one concrete type
            val elemRef: Option[CtTypeReference[?]] =
              if comp != null && tpConcrete(comp) then Some(comp)
              else if comp != null && !comp.isInstanceOf[CtTypeParameterReference] then
                Some(comp)
              else if comp != null && recvSubst.contains(comp.getSimpleName) then
                Some(recvSubst(comp.getSimpleName))
              else
                val ts = argEs.drop(fixed).map(e => e.getType)
                Option.when(ts.nonEmpty && ts.forall(t => t != null && !t.isPrimitive && tpConcrete(t)) &&
                            ts.map(_.getQualifiedName).distinct.sizeIs == 1)(ts.head)
            // the declaring type is a SHADOW iff reconstructed from bytecode — one answer for
            // which side of the program's edge the CALLEE is on
            val external = isExternalCallee(ex)
            if comp == null || argEs.sizeIs < fixed then None
            else if passesArray then passedThrough(ex, argEs, external, recvSubst)
            else if elemRef.isEmpty then None
            else
              val (head, rest) = argEs.splitAt(fixed)
              val fixedTerms = head.zipWithIndex.map { (e, i) => coerce(l(i).getType, e, expr(e)) }
              // THE ELEMENT TYPE, with an ANCESTOR's type variables replaced (ENGINE-LIMITS G26) —
              // else `tpe` renders a `?H` sentinel. [[inheritedFormal]] is the SAME lookup the
              // inherited-formal cast uses; `scala.None` where nothing substitutes. Argument
              // INFERENCE remains refused here (measured worse, 81 -> 83, twice).
              val ct = inheritedFormal(elemRef.get).getOrElse(tpe(elemRef.get))
              val elems = rest.map(e => coerce(elemRef.get, e, expr(e)))
              val at = AppliedType(TypeRef(NoPrefix, minter.external("scala.Array", "Array")), List(ct))
              val o = argEs.headOption.map(originOf).getOrElse(Origin.synthetic)
              Some(fixedTerms :+ (
                if external then Tree.Repeated(elems, at, o)
                else Tree.NewArray(TypeTree(ct, o), Nil, Some(elems), at, o)))
          case _ => None

      /** java already holds the array and passes it WHOLE through the `T...` slot — the MIRROR of
        * the pack above (ENGINE-LIMITS K6.5). Callee OURS: `None`, ordinary argument list. Callee a
        * CLASS FILE: scalac reads `T...` as REPEATED and a bare array conforms as ONE element (a
        * silent `Object`-element bug, CLAUDE.md §4.4, or an uncounted compile error otherwise) — so
        * the array is SPREAD (`arr*`), which still ALIASES `arr` as java's pass-through does. */
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
        // the CALL dispatches' area-G consults, at the one function both `invocation`/`ctorCall` reach
        callConsults(ex, argEs, at)
        slotConsults(argSlots(ex, argEs), at)
        varargPack(ex, argEs, recvSubst).getOrElse(coerceArgsFixed(ex, argEs, recvSubst))

      /** the receiver's own type arguments, by the declaring class's parameter NAMES — `Graph<V>`
        * called on `DirectedGraph<Integer>` gives `V -> Integer`. Only a fully known instantiation
        * (same arity, every argument NAMEABLE HERE — not merely CONCRETE, `tpConcrete` excludes the
        * caller's own variable wrongly, [[tpNameableHere]] is the repair — no wildcards). */
      private def receiverTypeArgs(inv: CtInvocation[?]): Map[String, CtTypeReference[?]] =
        val rt = inv.getTarget match
          case null => null
          case _: CtSuperAccess[?] | _: CtTypeAccess[?] => null
          case t    => castType(t)
        typeArgSubst(rt)

      /** what a REFERENCE's instantiation says the DECLARING type's formals are — `Bag<V>` says
        * `E := V`, by position. ONE derivation, read by [[receiverTypeArgs]] and by
        * [[nullToSamResult]] — Spoon's `TypeAdaptor` measurably does NOT answer this for a lambda
        * target, so it is derived here rather than asked of it. */
      private def typeArgSubst(rt: CtTypeReference[?]): Map[String, CtTypeReference[?]] =
        if rt == null || rt.isPrimitive || rt.isInstanceOf[CtArrayTypeReference[?]] ||
           rt.isInstanceOf[CtTypeParameterReference] || rt.isInstanceOf[CtWildcardReference] then Map.empty
        else
          val formals = typeDeclarationOf(rt).map(_.getFormalCtTypeParameters.asScala.toList).getOrElse(Nil)
          val actuals = rt.getActualTypeArguments.asScala.toList
          if formals.nonEmpty && actuals.sizeIs == formals.size &&
             actuals.forall(a => !a.isInstanceOf[CtWildcardReference] && tpNameableHere(a))
          then formals.map(_.getSimpleName).zip(actuals).toMap
          else Map.empty

      /** @param recvSubst the RECEIVER's own type arguments ([[receiverTypeArgs]]) — THREADED from
        *   [[coerceArgs]], never re-derived (F8). Only [[nullToTypeParam]] reads it, for G12's rule:
        *   a callee's own type variables do not resolve at the call site, but the CLASS's do. */
      private def coerceArgsFixed(ex: CtExecutableReference[?], argEs: List[CtExpression[?]],
                                  recvSubst: Map[String, CtTypeReference[?]] = Map.empty): List[Term] =
        // array covariance at call args DISABLED for OUR OWN methods (Spoon erases `T[]` to
        // `Object[]`, which would break the overloaded Scala method wanting invariant `Array[T]`) —
        // enabled only for EXTERNAL callees, whose real (class-file) formal genuinely is `Object[]`
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

      /** `null` passed to a callee slot whose real (un-erased) formal is a type parameter — cast it
        * (`m(null)` → `m(null.asInstanceOf[T])`). Dominant case: a self-call in scope. Second case:
        * the RECEIVER's, G12's rule — the declaring CLASS's variables resolve through the receiver's
        * type arguments, tried FIRST (exact, keyed on the DECLARING CLASS's own formals, unlike the
        * name-based `resolveTypeParam`), and only for a variable the class declares (§4.56). */
      private def nullToTypeParam(e: CtExpression[?], declFormal: Option[CtTypeReference[?]],
                                  recvSubst: Map[String, CtTypeReference[?]], t: Term): Term =
        val isNull = e match { case l: CtLiteral[?] => l.getValue == null; case _ => false }
        def classOwned(tp: CtTypeParameterReference): Boolean =
          Option(tp.getDeclaration).map(_.getParent).exists(_.isInstanceOf[CtType[?]])
        def cast(target: CtTypeReference[?]): Term = Tree.Typed(t, tt(tpe(target), e), tpe(target), originOf(e))
        declFormal match
          case Some(tp: CtTypeParameterReference) if isNull &&
            classOwned(tp) && recvSubst.get(tp.getSimpleName).exists(tpNameableHere) =>
            cast(recvSubst(tp.getSimpleName))
          // through the BARRIER-AWARE frame — *is this name WRITABLE here*, not just *does it
          // resolve* (`resolveTypeParam` sees every enclosing scope by name, including ones java
          // forbids naming, e.g. a `static` member and its class's, JLS 8.4.4). [[tpAccessibleHere]]
          // is used by every other cast this frontend builds — deliberately WEAKER than
          // `sameVarInScope`, which was tried and wrong in both directions (measured 0 -> 2 on two
          // ports, §5's narrowing-is-not-exempt).
          case Some(tp: CtTypeParameterReference) if isNull && tpAccessibleHere(tp) =>
            cast(tp)
          case _ => t

      /** An ARRAY argument whose emitted element type is not the declared formal's — java arrays
        * are COVARIANT with an erased generic element, scala's are INVARIANT. Faithful port is an
        * explicit `asInstanceOf` at the USE, never a widened DECLARATION (measured catastrophic,
        * see [[erasureOfFormal]]). Driven by the DECLARATION's formal, never the reference's erased
        * one. Gated on [[formalNameableHere]] so the cast never names an unwritable variable. */
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
        val et = e.getType
        // A read through a WILDCARD-filled receiver is the other value Scala types as `Any`.
        // `for (Iterator iter = it.iterator(); …) append(iter.next())` reads a RAW `Iterator`, which
        // Java types as `Object`; we render the raw receiver `JavaIterator[?]`, so Scala's result is
        // the wildcard — weaker than `Object`, and rejected by an `Object` slot.
        val wildcardRead = e match
          case inv: CtInvocation[?] =>
            val rt = Option(inv.getTarget).map(_.getType).orNull
            rt != null && !rt.isPrimitive && isGenericUse(rt) && hasWildcard(tpe(rt))
          case _ => false
        // the THIRD value scala types wider than `Object` is one THIS FRONTEND made:
        // `execDef.anyForEquals` retypes `equals(Object)`'s parameter to `scala.Any`, so forwarding
        // it hands `Any` to an `Object` slot. Read off THIS FRONTEND's own record (§4.56), not the
        // java — the widening happened only at the DECLARATION.
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
        val selT  = Option(s.getSelector.getType).map(tpe).getOrElse(NoType)
        val arms  = switchArms(cases, s, selT, unitT, isExpr = false)
        // java's switch with no `default` FALLS OUT; scala's `match` throws `MatchError` — add the
        // fall-out arm java has, except where [[isEnhanced]] says java does not fall out either
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

      /** A SWITCH EXPRESSION — JLS 15.28, catalog `JS-S09`. `CtSwitchExpression` does NOT extend
        * `CtSwitch`, so the statement arm never caught it; `Tree.Match` already renders in either
        * position, so only the arms differ. THREE JLS rules: no fall-out arm (must be EXHAUSTIVE,
        * 15.28.1); an arm produces a VALUE (tail `yield` peeled into the result, others stay
        * [[Tree.Yield]] under a boundary); `yield` NOT unwrapped here (only [[caseBody]] undoes
        * Spoon's arrow-arm normalisation for statements). Fallthrough/break/label rules shared with
        * the statement form via [[switchArms]] (ENGINE-LIMITS F8). */
      private def switchExpr(sw: CtSwitchExpression[?, ?])(using Obligations): Term =
        val resT = ty(sw)
        // JS-S09 — always fires: every switch expression needs the image, and choosing `Tree.Match`
        // for it is the whole content of the row.
        Obligations.consult(JS.S(9), originOf(sw))(Some(()))
        val cases = sw.getCases.asScala.toList
        val selT  = Option(sw.getSelector.getType).map(tpe).getOrElse(NoType)
        Tree.Match(expr(sw.getSelector), switchArms(cases, sw, selT, resT, isExpr = true), resT, originOf(sw),
                   isExpr = true)

      /** the statements of one `case`, with Spoon's ARROW normalisation undone where java has no
        * such construct. Flattens an arrow-arm's `CtBlock`; unwraps an arrow STATEMENT arm's
        * synthetic `CtYieldStatement` (JLS 14.21: `yield` is legal only in a switch EXPRESSION). */
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

      /** one case LABEL — the SPLIT `JS-S10` is about. A TYPE PATTERN (JLS 14.11.1) lowers exactly
        * to `Tree.TypePattern` + `CaseDef.guard`. A RECORD pattern too (`JS-C43`'s derived
        * `unapply`, see [[recordPattern]]). An UNNAMED pattern stays refused (no source Spoon 11.5
        * builds one, ENGINE-LIMITS T19). MARKER minted HERE, not via `expr`'s default, since
        * `CtCasePattern` carries no source POSITION (falls back to the unit-fatal throw otherwise) —
        * carries the SELECTOR's type, not the pattern's own `java.lang.Void`. The binding is an
        * ordinary local: probed, its `CtLocalVariable` carries its own valid position even though
        * the wrapper does not, so two same-named arms intern as two symbols correctly. */
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

      /** `case Point(int x, int y) ->` — java's RECORD PATTERN, as scala's constructor pattern. THE
        * ONE DISTINCTION: JLS 14.30.2's UNCONDITIONAL component pattern matches `null`, a narrowing
        * one (`Tree.TypePattern`) does not — scala needs `Tree.BindPattern` for the first. Asked of
        * SPOON's `isSubtypeOf` (JLS 4.10), narrowing arm taken where it cannot answer. A component
        * that is neither shape is refused IN PLACE. The RECORD ITSELF must be one this run LOWERS —
        * `JS-C43`'s derived `unapply` names nothing for a dependency's record — decided
        * STRUCTURALLY (does this parse hold a `CtRecord` declaration, §4.56), never by name. */
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

      /** is this an ENHANCED switch STATEMENT — one java requires EXHAUSTIVE (JLS 14.11.2), so it
        * does NOT fall out? Asks BOTH of 14.11.2's disjuncts: the LABEL shape (a pattern/`null`), and
        * the SELECTOR'S TYPE (a QUALIFIED ENUM CONSTANT betrays nothing in the label list, JEP 441 —
        * javac compiles a `MatchException` throw where a naive read would answer classic). Deciding
        * from the label alone is WRONG (measured against javac). `noClasspath` unresolvable →
        * `false` (§4.6, the pre-existing behaviour). Both throw where it fires, different classes. */
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
            val decl = typeDeclarationOf(r)
            decl.exists {
              case _: CtEnum[?]                       => false
              case _: CtClass[?] | _: CtInterface[?]  => true
              case _                                  => false
            }
          }
        }

      // ---- expressions ----
      // ---- expressions ----
      /** the casts the SOURCE wrote, applied innermost-first. `(T) x` is `x.asInstanceOf[T]` except
        * one case: a boxed-WRAPPER operand cast to a primitive is a CONVERSION in java (JLS 5.1.8
        * then 5.1.2), while `asInstanceOf` demands the exact wrapper and throws on a mismatched one
        * — probed against javac/scalac 3.8.4 over all 45 (runtime class x primitive) cells. `Object`
        * and `Number` deliberately excluded: java performs no `Number` dispatch there either (JLS
        * 5.5), so converting would be UNFAITHFUL. Same-type unbox left to `Predef` (see [[coerce]]). */
      private def expr(e: CtExpression[?]): Term =
        val core  = exprNoCast(e)
        val casts = e.getTypeCasts.asScala.toList
        val et0: CtTypeReference[?] = e.getType
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

      /** THE TYPE AN EXPRESSION HAS WHERE IT STANDS — after the source's own casts, at the OUTERMOST
        * one, the HEAD of `getTypeCasts` ([[expr]] folds `foldRight`, so the head is the OUTER
        * `Tree.Typed`, matching java's order). ONE function, six callers (CLAUDE.md §4.6,
        * ENGINE-LIMITS F8) — the idiom was written six times taking `lastOption`, the INNERMOST
        * cast, silently wrong. `null` where Spoon has no answer; callers decline honestly. */
      private def castType(e: CtExpression[?]): CtTypeReference[?] =
        e.getTypeCasts.asScala.headOption.getOrElse(e.getType)

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
          val rhs = expr(a.getAssignment)
          val op  = opText(a.getKind).getOrElse { unknownOp(a.getKind, a, ty(a)); "?" }
          // JS-E04 — the same difference as JS-E03 at the other dispatch, and the same PREDICATE.
          // `compoundNarrow` is one function precisely because this pair is what the catalog splits
          // into two rows: the narrowing is java's implicit cast back to the left-hand type
          // (JLS 15.26.2), and it is owed wherever the assignment happens — the position only
          // decides whether the resulting value is also used.
          val narrow = Obligations.consult(JS.E(4), originOf(a))(compoundNarrow(a))
            .map(t => tpe(t))
          // JS-E17 — lvalue single evaluation (F7), expression dispatch. Same as the statement arm.
          Obligations.consult(JS.E(17), originOf(a))(Some(()))
          val st  = Tree.Assign(lhs, rhs, unitT, originOf(a), compound = Some((op, narrow)))
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

      /** JS-E05's NUMERIC half — JLS 15.25.2's binary numeric promotion, performed ON THE OPERAND,
        * not as a cast at the enclosing slot (a cast is not a conversion, ENGINE-LIMITS K17): java
        * computes a conditional's PRIMITIVE type where scala takes the lub of its branches, so each
        * operand is converted here to match. BOTH DIRECTIONS: scala 3 dropped weak conformance
        * (unlike scala 2), so the WIDENING half is needed too whenever no expected type reaches the
        * branches (probed at 3.8.4). Never promotes on its own (target is Spoon's own answer) and
        * never touches a REFERENCE conditional (lub both sides already). Operand read through
        * [[castType]] (after its OWN casts), same idiom as every other reader in this file. A type
        * Spoon cannot resolve is left ALONE (§4.6), never asserts a false type. */
      private def promotedBranch(c: CtConditional[?], be: CtExpression[?], t: Term): Term =
        val cj = c.getType
        val bj = castType(be)
        if cj == null || bj == null || !cj.isPrimitive || cj.getQualifiedName == bj.getQualifiedName then t
        else if !bj.isPrimitive then
          // a boxed operand at a primitive conditional: java UNBOXES it, then widens. Only a wrapper
          // can stand here in valid java — anything else needed a cast the source itself wrote.
          if wrapperOf.values.toSet(bj.getQualifiedName) then unbox(t, bj.getQualifiedName, cj.getSimpleName, be) else t
        else if primRank.contains(bj.getSimpleName) && primRank.contains(cj.getSimpleName) then
          // BOTH DIRECTIONS — the two primitives differ, so java converted and the port owes it
          // (JS-E06: `asInstanceOf` between statically primitive types is a conversion both ways).
          // Redundant where an expected type already reaches the branch, never WRONG.
          Tree.Typed(t, tt(tpe(cj), be), tpe(cj), originOf(be))
        else t

      /** the conditional's static type is safe to ascribe onto a null branch — a concrete type, or a
        * type parameter that actually resolves in scope (not the `?T` unresolved stub). */
      private def condTypeResolves(c: CtConditional[?]): Boolean =
        (c.getType) match
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
        val idiomCasts = na.getTypeCasts.asScala.toList
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

      /** [[nullToTypeParam]]'s rule at the ONE expression position with no formal: an EXPRESSION-
        * bodied lambda takes its body type from the SAM's RESULT (JLS 15.27.3), so where that
        * result is the target's own type variable, `Null` needs an explicit cast (it conforms to
        * every reference type but no ABSTRACT one). Variable resolved through the TARGET's own
        * instantiation ([[typeArgSubst]], G12's rule at a lambda). `Factory<String>` (a concrete
        * actual) needs and gets nothing — an over-approximation no count could see (§5). */
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
            val decl = typeDeclarationOf(here)
            val supers = decl.toList.flatMap { d =>
              (d.getSuperInterfaces.asScala.toList) ++
                (Option(d.getSuperclass).toList)
            }
            supers.iterator.map { s =>
              // `s`'s actuals are written in `here`'s scope, so they are read THROUGH `subst`
              // before they become `s`'s own frame.
              val formals = typeDeclarationOf(s).map(_.getFormalCtTypeParameters.asScala.toList.map(_.getSimpleName)).getOrElse(Nil)
              val actuals = (s.getActualTypeArguments.asScala.toList)
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
          case None    => ex.isStatic
        // the ARITY is read for BOTH cases, not only the unbound one: a NILARY static reference is
        // the one qualified name scala will not eta-expand, so the emitter needs the number there
        // too (see [[Referent]], `ENGINE-LIMITS.md` G32).
        val n = decl.map(_.getParameters.asScala.size)
          .getOrElse(ex.getParameters.asScala.size)
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
          val formals = typeDeclarationOf(rt).map(_.getFormalCtTypeParameters.asScala.toList).getOrElse(Nil)
          val actuals = rt.getActualTypeArguments.asScala.toList
          // a BOUNDED wildcard (`IntMap<? extends V> map`) still gives a usable capture — `map.zeroValue`
          // conforms to `V` — so only a raw use or an UNBOUNDED `?` needs the erased view here.
          val useless = (a: CtTypeReference[?]) => a match
            case w: CtWildcardReference => Option(w.getBoundingType).forall(_.getQualifiedName == "java.lang.Object")
            case _                      => false
          val unknown = formals.nonEmpty && (actuals.isEmpty || actuals.exists(useless))
          val names   = formals.map(_.getSimpleName).toSet
          val declTpe = fieldDeclOf(ref).map(_.getType).filter(_ != null)
          val depends = declTpe.exists(mentionsTypeVarFilled(_, names))
          if unknown && depends then
            // same F-bound treatment as `erasedReceiverView`: an F-bounded class has no erased
            // image, so fill from the enclosing scope's own variables and leave what cannot be
            // named as `?`. Without this a FIELD access through such a receiver still emitted
            // `Node[Node[?, Object, Actor], Object, Actor]`, which fails its own bound.
            def isFB(f: CtTypeParameter): Boolean =
              Option(f.getSuperclass).exists(b => mentionsTypeVarFilled(b, Set(f.getSimpleName)))
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

      /** the type that DECLARES a static field `name` (degrades to `None` on shadow/unresolved
        * types). Walks the WHOLE inheritance closure (superclass AND superinterfaces, CLAUDE.md
        * §1a — a java interface constant is inherited through `implements` too), breadth-first with
        * the class edge FIRST (java's own shadowing precedence). */
      private def declaringStaticType(accessed: CtTypeReference[?], name: String): Option[CtType[?]] =
        val seen  = collection.mutable.Set[String]()
        val queue = collection.mutable.Queue[CtType[?]]()
        def decl(r: CtTypeReference[?]): CtType[?] =
          typeDeclarationOf(r).orNull
        Option(decl(accessed)).foreach(queue.enqueue)
        while queue.nonEmpty do
          val t = queue.dequeue()
          if t != null && seen.add(t.getQualifiedName) then
            if (t.getFields.asScala.exists(_.getSimpleName == name)) then return Some(t)
            val parents =
              Option(t.getSuperclass).toList ++ t.getSuperInterfaces.asScala.toList
            parents.map(decl).filter(_ != null).foreach(queue.enqueue)
        None

      private def fieldSym(ref: CtFieldReference[?]): SymId =
        val ownerQ = fieldDeclOf(ref).flatMap(fd => Option(fd.getDeclaringType)).map(_.getQualifiedName)
          .orElse(Option(ref.getDeclaringType).map(_.getQualifiedName))
          .getOrElse("java.lang.Object")
        val ownerId = minter.external(ownerQ, simpleName(ownerQ))
        externalMember(ownerId, ref.getSimpleName, ref.getSimpleName, info = externalFieldType(ref))

      /** the DECLARED type of an EXTERNAL field, as a class file states it — [[externalSignature]]'s
        * fact for a field (the seam a `Select` node makes is invisible to anything keyed on
        * `Tree.Apply`, ENGINE-LIMITS K15). Rendered SCOPE-FREE through [[externalSlot]]. Only for a
        * SHADOW declaration; a program-declared field gets its real type from `fieldDef`. */
      private def externalFieldType(ref: CtFieldReference[?]): TypeRepr =
        fieldDeclOf(ref) match
          case scala.None => NoType // no declaration to read — not evidence of anything
          case Some(fd)   =>
            val shadow = Option(fd.getParent(classOf[CtType[?]])).forall(_.isShadow)
            if !shadow then NoType else externalSlot(fd.getType)

      /** Java's WILDCARD/RAW-receiver calls: scala gives every member access a fresh CAPTURE, so a
        * value off one receiver never conforms to another's formal. Java's own view is ERASED,
        * performed unchecked — cast the RECEIVER to its erased instantiation and each dependent
        * argument to that formal's erasure (same erasure rules, so they agree). Gated to calls that
        * genuinely DEPEND on the receiver's type variables. */
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
                fieldDeclOf(fa.getVariable).map(_.getType)
                      .exists(_.isInstanceOf[CtTypeParameterReference])
              case _ => false
            if declaredVar then None
            else if rt == null || rt.isPrimitive || rt.isInstanceOf[CtArrayTypeReference[?]]
               || rt.isInstanceOf[CtTypeParameterReference] || rt.isInstanceOf[CtWildcardReference] then None
            else
              val formals = typeDeclarationOf(rt).map(_.getFormalCtTypeParameters.asScala.toList).getOrElse(Nil)
              val actuals = rt.getActualTypeArguments.asScala.toList
              val unknown = formals.nonEmpty && (actuals.isEmpty || actuals.exists(_.isInstanceOf[CtWildcardReference]))
              val names   = formals.map(_.getSimpleName).toSet
              val depends =
                execDeclOf(ex)
                      .map(_.getParameters.asScala.toList.map(_.getType))
                      .exists(_.exists(p => p != null && mentionsTypeVarFilled(p, names)))
              // Only an F-BOUNDED class needs this: there the erasure has no Scala image at all,
              // so the name-directed fill is the only expressible reading. Everywhere else the
              // erasure is load-bearing and preferring in-scope names by NAME measured 2 -> 9.
              def isFBounded(f: CtTypeParameter): Boolean =
                Option(f.getSuperclass).exists(b => mentionsTypeVarFilled(b, Set(f.getSimpleName)))
              val anyFBounded = formals.exists(isFBounded)
              // THE VIEW IS DECIDED PER POSITION — `unknown` asked of the WHOLE list is wrong for a
              // MIXED one (a position java left WRITTEN must be carried, not erased). Carried only
              // where the argument mentions NO TYPE VARIABLE (the three erasure readings must AGREE,
              // ENGINE-LIMITS G21). F-BOUNDED classes excluded whole (arguments discharge each
              // other's bounds).
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
                  // prefer the NAME-DIRECTED fill over the erasure — an F-bound's erasure has no
                  // finite Scala image, but the enclosing scope's own variables discharge the bound
                  // by construction (same rule `nameFilledArgs` already applies to types)
                  val named = if inStatic || !anyFBounded then scala.None else accessibleTp(f.getSimpleName)
                  named.map { id => val r = TypeRef(NoPrefix, id); namedOf(f.getSimpleName) = r; r }.getOrElse {
                    if anyFBounded || isFBounded(f) then TypeBounds(NoType, NoType)
                    else erasureOfFormal(f, Set.empty, 2)
                  }
                } }
                val subst = formals.map(_.getSimpleName).zip(args).toMap
                Some((AppliedType(TypeRef(NoPrefix, typeSym(rt)), args), subst, namedOf.toMap))
              else None

      /** `(N) this` — the SELF-TYPE conversion at a raw call. Java accepted `this` at a raw
        * receiver's `N` formal only because the receiver is raw — libGDX writes `(N) this` itself
        * where it isn't. Restricted to `this`: a general "cast any argument" rule measured 1 -> 11. */
      private def selfTypeArgs(
          ex: CtExecutableReference[?], argEs: List[CtExpression[?]], args: List[Term],
          nm: Map[String, TypeRepr],
      ): List[Term] =
        if nm.isEmpty then args
        else
          val ps = execDeclOf(ex).map(_.getParameters.asScala.toList.map(_.getType))
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
        val ps = execDeclOf(ex).map(_.getParameters.asScala.toList.map(_.getType))
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

      /** Java's UNCHECKED conversion at an ARGUMENT, when the RECEIVER's instantiation is KNOWN —
        * the complement of [[erasedReceiverView]] (whose receiver is UNKNOWN, mutually exclusive).
        * Narrow: only a formal mentioning a receiver type variable, only an argument our raw fill
        * wildcarded, only when the substituted formal is fully nameable here. */
      private def knownReceiverArgs(inv: CtInvocation[?], argEs: List[CtExpression[?]], args: List[Term]): List[Term] =
        val rt = inv.getTarget match
          case null => null
          case _: CtSuperAccess[?] | _: CtTypeAccess[?] | _: CtThisAccess[?] => null
          case t    => castType(t)
        if rt == null || rt.isPrimitive || rt.isInstanceOf[CtArrayTypeReference[?]] ||
           rt.isInstanceOf[CtTypeParameterReference] || rt.isInstanceOf[CtWildcardReference] then args
        else
          val formals = typeDeclarationOf(rt).map(_.getFormalCtTypeParameters.asScala.toList).getOrElse(Nil)
          val actuals = rt.getActualTypeArguments.asScala.toList
          // fully KNOWN instantiation (same arity, every variable nameable). A WILDCARD actual is
          // admitted too — it cannot drive the cast but makes a NARROWER argument illegal, and java
          // converts silently there (see the per-argument guard below).
          val known = formals.nonEmpty && actuals.sizeIs == formals.size && actuals.forall(tpResolvable)
          val ps = execDeclOf(inv.getExecutable).map(_.getParameters.asScala.toList.map(_.getType))
          (known, ps) match
            case (true, Some(l)) if l.sizeIs == args.size && argEs.sizeIs == args.size =>
              // a FIELD receiver's arguments must be rendered as the FIELD's declaration rendered
              // them, not re-filled from the enclosing method's own type parameters
              val fieldRecv = inv.getTarget.isInstanceOf[CtFieldAccess[?]]
              val subst = formals.map(_.getSimpleName)
                .zip(if fieldRecv then atDeclScope(actuals.map(tpe)) else actuals.map(tpe)).toMap
              val rawElement = actuals.exists(a => isRawGenericUse(a))
              args.zipWithIndex.map { (t, i) =>
                val f = l(i)
                if f == null || !mentionsTypeVarBounded(f, subst.keySet) then t
                else substFormal(f, subst) match
                  // fires on any of: the ARGUMENT was wildcarded by our raw fill (`hasWildcard`);
                  // the SLOT is wildcarded and the argument more precise (`uncheckedFrom(ct,t.tpe)`);
                  // the receiver's own type argument is a RAW use (`rawElement`); the ARGUMENT is an
                  // Object-parameterised view of the slot's type via an ERASED RECEIVER
                  // (`uncheckedFrom(t.tpe,ct)`) — all java's unchecked conversion, narrow by
                  // construction (same type constructor and arity, ENGINE-LIMITS's own shape)
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
          case t    => (t.getType) match
            case tv: CtTypeParameterReference =>
              // only a RAW-generic bound erases the members; a properly applied bound does not.
              val d = typeParamDeclOf(tv)
              d.flatMap(x => Option(x.getSuperclass)).exists(isRawGenericUse)
            case _ => false
        if !recvIsTypeVar then args
        else
          val ps = execDeclOf(inv.getExecutable).map(_.getParameters.asScala.toList.map(_.getType))
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
        // JS-C01/C02: a java `static` is INHERITED, a scala companion inherits nothing — re-point
        // at the DECLARING type. Read off the reference, not by re-running the BFS.
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
        // JS-G29/G30: a variable some FORMAL mentions infers the same in both languages (G29); one
        // no formal mentions resolves to its BOUND in java and `Nothing` in scala (G30)
        val calleeTpNames = execDeclOf(ex)
                                  .collect { case m: CtMethod[?] => m.getFormalCtTypeParameters.asScala.toList }
                                  .getOrElse(Nil).map(_.getSimpleName).toSet
        val constrained = calleeTpNames.nonEmpty &&
            Option(ex.getExecutableDeclaration).exists(
              _.getParameters.asScala.exists(p => mentionsTypeVar(p.getType, calleeTpNames)))
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

      /** The downcast an ERASED RECEIVER's result needs — java pays for calling through the erased
        * view ([[erasedReceiverView]]) with an implicit downcast on the result, which this writes
        * down. Gated on the un-erased result being nameable HERE and actually different. */
      private def erasedRecvResult(
          inv: CtInvocation[?], recv: Option[(TypeRepr, Map[String, TypeRepr], Map[String, TypeRepr])], app: Term,
      ): Term = recv match
        case None => app
        case Some((_, subst, _)) =>
          val declRet = execDeclOf(inv.getExecutable)
                              .collect { case m: CtMethod[?] => m.getType }
          // the un-erased reading comes from the receiver's DECLARED arguments, not Spoon's type
          // for the call — a wildcard receiver's CAPTURE has no scala name
          val declSubst: Map[String, TypeRepr] =
            val t  = inv.getTarget
            val rt = castType(t)
            val fs = Option(rt.getTypeDeclaration).map(_.getFormalCtTypeParameters.asScala.toList).getOrElse(Nil)
            val as = rt.getActualTypeArguments.asScala.toList
            if fs.sizeIs != as.size then Map.empty
            else fs.map(_.getSimpleName).zip(as.map {
              case w: CtWildcardReference => Option(w.getBoundingType).filter(_ => w.isUpper).orNull
              case a                      => a
            }).collect { case (n, a) if a != null && tpResolvable(a) => n -> tpe(a) }.toMap
          // a RAW declared result through an ERASED receiver is where the node's type and the
          // emitted scala part company (ENGINE-LIMITS §0) — re-TYPE the node, emit nothing
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

      /** The result type an ERASED ARGUMENT drags with it — recording Spoon's un-erased type would
        * make the TIR assert what the emitted scala does not have. Only the erasure WE introduced
        * is modelled, decided from the EMITTED argument (a JDK shadow's formals are unreliable). */
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
              t.getAllMethods.asScala.exists(m => m.getSimpleName == name && !m.hasModifier(ModifierKind.STATIC))
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

      /** A method TYPE PARAMETER that appears in NO FORMAL, at a call with no target type either
        * (ENGINE-LIMITS G22). Java instantiates it at its BOUND (JLS 18, no constraints/target);
        * scala instantiates it at `Nothing`, and a selection on `Nothing` fails. [[pinTypeArgs]]
        * declines here (no argument mentions the variable) — this pins the DECLARATION's answer
        * instead. Four conditions: no formal mentions the variable; the call has no TARGET TYPE
        * (it stands as the RECEIVER of another selection); every variable has a REAL bound
        * (unbounded means `Object`, G24's territory); the bound mentions no type variable of its own. */
      private def pinUnconstrainedTypeArgs(fun: Term, inv: CtInvocation[?], o: Origin): Term =
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

      /** G22's pin at the shape its FOURTH condition declines — an F-BOUND — by ascribing the
        * RESULT instead of instantiating the ARGUMENT (ENGINE-LIMITS G8.7). No denotable `X`
        * satisfies an F-bound as a type ARGUMENT (G8's expressiveness limit); an ASCRIPTION does not
        * need to — the argument still infers `Nothing` (legal), and the ascription supplies the TYPE
        * THE SELECTION READS instead. NOT a fifth attempt at G8's fill: fires where the fill CANNOT
        * be written, G22's first three conditions plus: the RESULT is one of the method's own
        * variables; every named variable in the bound is the METHOD's own (rendered `?`) or writable
        * here (`tpNameableHere`, §4.6). */
      private def ascribeUnconstrainedResult(inv: CtInvocation[?], app: Term, o: Origin): Term =
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
              val recvT  = Option(inv.getTarget).map(castType).orNull
              val ownerQ = Option(m.getDeclaringType).map(_.getQualifiedName)
              val viaRecv: String => Option[CtTypeReference[?]] = n =>
                ownerQ.flatMap(q => if recvT == null then scala.None else actualFor(recvT, q, n, 8))
              wildcardOwnVars(bound.get, names, viaRecv) match
                case Some(t)    => Tree.Typed(app, tt(t, inv), t, o)
                case scala.None => app

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
          val args = other.getActualTypeArguments.asScala.toList
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
        case r => r.getActualTypeArguments.asScala.exists(mentionsNamedTypeVar)

      /** does this invocation stand as the RECEIVER of another member access — the one position that
        * gives its result no expected type at all, and the one where scala's `Nothing` is then
        * selected from? See [[pinUnconstrainedTypeArgs]]. */
      private def isReceiverOfSelection(inv: CtInvocation[?]): Boolean =
        inv.getParent match
          case p: CtInvocation[?]   => p.getTarget eq inv
          case p: CtFieldAccess[?]  => p.getTarget eq inv
          case _                    => false

      /** `int.class` etc. — Java types a primitive class literal as `Class<Integer>` (boxed), but we
        * emit it as `classOf[scala.Int]` (`Class[Int]`). Baseline inference binds a `Class<T>` param's
        * `T` to the primitive and matches; pinning `T` to the boxed wrapper would break that. So a
        * call carrying one of these must keep inference free — don't pin its type arguments. */
      private def isPrimitiveClassLiteral(e: CtExpression[?]): Boolean = e match
        case fr: CtFieldRead[?] if fr.getVariable.getSimpleName == "class" =>
          fr.getTarget match
            case ta: CtTypeAccess[?] => ta.getAccessedType.isPrimitive
            case _                   => false
        case _ => false

      private val boxedWrappers = Set(
        "java.lang.Integer", "java.lang.Long", "java.lang.Short", "java.lang.Byte",
        "java.lang.Character", "java.lang.Boolean", "java.lang.Float", "java.lang.Double")
      private def isBoxedWrapper(t: CtTypeReference[?]): Boolean =
        boxedWrappers(t.getQualifiedName)

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
          val ps = execDeclOf(cc.getExecutable).map(_.getParameters.asScala.toList.map(_.getType))
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
        * ones — java checked none of it (constructor is raw), but SOME instantiation must be
        * chosen, recovered from a precise argument's own supertype chain. Casting the OTHER way
        * (precise argument DOWN to erased) measured worse (23/5/43 errors, ENGINE-LIMITS G13).
        * Narrow: one class type parameter, one binding found, only arguments AT the erasure touched. */
      private def rawCtorSpecialisation(
          cc: CtConstructorCall[?], l: List[CtTypeReference[?]], argEs: List[CtExpression[?]], args: List[Term],
      ): Map[Int, TypeRepr] =
        val clsFormals = Option(cc.getType).flatMap(typeDeclarationOf)
                               .map(_.getFormalCtTypeParameters.asScala.toList).getOrElse(Nil)
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

      /** An APPLIED generic constructor call whose argument java unchecked-converted — read through
        * the erased receiver view, scala needs the conversion java made implicitly written out.
        * Target is the declared formal with the class's own parameters substituted by the call's
        * EXPLICIT type arguments (else `uncheckedGeneric` would render `?T`). Raw counterpart is
        * [[rawCtorArgs]]. Gated on the ARGUMENT mentioning a raw generic. */
      private def appliedCtorArgs(cc: CtConstructorCall[?], argEs: List[CtExpression[?]], args: List[Term]): List[Term] =
        val actuals = cc.getType.getActualTypeArguments.asScala.toList
        val formals = Option(cc.getType).flatMap(typeDeclarationOf)
                            .map(_.getFormalCtTypeParameters.asScala.toList.map(_.getSimpleName)).getOrElse(Nil)
        val ps = execDeclOf(cc.getExecutable).map(_.getParameters.asScala.toList.map(_.getType))
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

      /** SymId of a called executable — via its declaration (keyed identically to how we define our
        * own methods) or, for unresolved externals, by its reference. Under `noClasspath`,
        * `getExecutableDeclaration` can resolve to an UNRELATED same-named method — guarded
        * STRUCTURALLY (declaration's owner must be the receiver's type or a SUPERTYPE); disagreement
        * falls through to interning by the RECEIVER's declaring type (CLAUDE.md §4.56, ENGINE-LIMITS G34). */
      private def methodSym(ex: CtExecutableReference[?]): SymId =
        Option(ex.getExecutableDeclaration).filter(decl => declAgrees(decl, ex)) match
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

      /** Does the resolved declaration's declaring type agree with the reference's declaring type?
        *
        * The declaration's owner must be the SAME type as the reference's declaring type, or a
        * SUPERTYPE of it (an inherited method is a valid resolution). An unrelated type sharing a
        * name — `Field#getType` for a call on `Application` — does not agree.
        *
        * Where no declaring type is available on the REFERENCE (null), the check cannot be
        * performed, so the declaration is accepted — the alternative is to reject every untyped
        * reference, which is the more dangerous direction (§4.56: state the refutation).
        *
        * The hierarchy walk uses [[typeDeclarationOf]] and the superclass/superinterface chain,
        * the same mechanism [[declaringStaticType]] uses for static field inheritance. */
      private def declAgrees(decl: CtExecutable[?], ref: CtExecutableReference[?]): Boolean =
        val refDeclType = Option(ref.getDeclaringType)
        refDeclType match
          case scala.None => true // no reference type to compare — accept the declaration
          case Some(refType) =>
            val (declQ, _) = declType(decl)
            val refQ = refType.getQualifiedName
            // same type — the common case
            if declQ == refQ then true
            // the declaration's type is a supertype of the reference's type — inherited method
            else isSupertypeOf(refType, declQ)

      /** Is `targetQ` a supertype of `start`?  BFS over the hierarchy the frontend already has —
        * superclass then superinterfaces — the same shape as [[selfAndAncestors]].
        *
        * Where the hierarchy is unreadable (shadow types whose parents are not on the classpath),
        * the walk stops at that node and the answer is `false`, which is the safe direction: a
        * mis-resolution is then caught and falls through to the reference branch.
        *
        * No bare `catch` — parents are accessed through [[typeDeclarationOf]] (the ONE Spoon lookup
        * where absence is normal, `CLAUDE.md` §4.6), and `getQualifiedName` on a `CtTypeReference`
        * is a string accessor that does not resolve (`CLAUDE.md` §4.6: name the one lookup). */
      private def isSupertypeOf(start: CtTypeReference[?], targetQ: String): Boolean =
        val seen  = collection.mutable.Set[String]()
        val queue = collection.mutable.Queue[CtTypeReference[?]]()
        queue.enqueue(start)
        var found = false
        while queue.nonEmpty && !found do
          val r = queue.dequeue()
          if r != null then
            val q = r.getQualifiedName
            if q != null && seen.add(q) then
              typeDeclarationOf(r) match
                case Some(t) =>
                  val parents: List[CtTypeReference[?]] =
                    (t match { case c: CtClass[?] => Option(c.getSuperclass).toList; case _ => Nil }) ++
                      t.getSuperInterfaces.asScala.toList
                  parents.foreach { p =>
                    if p != null then
                      if p.getQualifiedName == targetQ then found = true
                      else queue.enqueue(p)
                  }
                case scala.None =>
                  // no declaration available — stop the walk at this node.
                  // The safe direction (§4.56): an unresolvable hierarchy cannot confirm the
                  // declaration agrees, so we decline and fall through to the reference branch.
                  ()
        found

      private def declType(decl: CtExecutable[?]): (String, String) = decl match
        case tm: CtTypeMember if tm.getDeclaringType != null => (tm.getDeclaringType.getQualifiedName, tm.getDeclaringType.getSimpleName)
        case _ =>
          val t = decl.getParent(classOf[CtType[?]])
          if t != null then (t.getQualifiedName, t.getSimpleName) else ("java.lang.Object", "Object")

      private def typeTerm(ta: CtTypeAccess[?], at: CtElement): Term =
        val q  = ta.getAccessedType.getQualifiedName
        val id = minter.external(q, simpleName(q))
        Tree.Ident(id, TypeRef(NoPrefix, id), originOf(at))

      /** T14 — the receiver of a STATIC CALL is the member's DECLARING type, not the type the
        * source wrote (java lets a static be named through ANY subclass; scala companions inherit
        * nothing — 20 errors on one library from a single upstream idiom). Read off the SYMBOL'S
        * OWNER, never the written name (CLAUDE.md §4.56) — `methodSym` derives it from the resolved
        * declaration. Re-qualifies for an IN-PROGRAM parent too (same mechanism as the companion
        * re-export, so the two cannot disagree). */
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
        val ot = opnd.getType
        if ot != null && ot.isPrimitive && Set("byte", "short", "char").contains(ot.getSimpleName)
        then Tree.Typed(res, tt(tpe(ot), opnd), tpe(ot), originOf(opnd)) else res

      /** The narrowing TYPE for an increment, or `None` — the same predicate as `incNarrow`, returning
        * the type rather than wrapping. Used by the statement arm where the narrowing is carried on
        * `Tree.Assign.compound` rather than wrapped in `Tree.Typed`. */
      private def incNarrowType(opnd: CtExpression[?]): Option[TypeRepr] =
        val ot = opnd.getType
        if ot != null && ot.isPrimitive && Set("byte", "short", "char").contains(ot.getSimpleName)
        then Some(tpe(ot)) else scala.None

      private def isStringConcat(b: CtBinaryOperator[?]): Boolean =
        Option(b.getType).exists(_.getQualifiedName == "java.lang.String")
      private def isStringTyped(e: CtExpression[?]): Boolean =
        Option(e.getType).exists(_.getQualifiedName == "java.lang.String")
      /** `java.lang.String.valueOf(t)` — make a non-String operand a String for concatenation. */
      private def stringify(t: Term, el: CtElement): Term =
        val strSym = minter.external("java.lang.String", "String")
        val vSym   = minter.external("java.lang.String#valueOf", "valueOf", strSym)
        Tree.Apply(Tree.Select(Tree.Ident(strSym, TypeRef(NoPrefix, strSym), originOf(el)), vSym, NoType, originOf(el)),
          List(t), vSym, TypeRef(NoPrefix, strSym), originOf(el))

      /** Java's `==` between REFERENCE types is identity; scala's `==` is `equals` — inside an
        * `equals` implementation that is infinite recursion (151 sites in gdx core, found only by
        * running the suite). `eq` is faithful for boxed wrappers/enums/interned Strings too, since
        * java compares those by identity as well. Skipped for `null` or PRIMITIVE operands.
        * `Any`-typed operands go through `AnyRef`, where `eq` lives. */
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
          val t = e.getType
          t != null && !t.isPrimitive
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
            val objTyped = Option(e.getType).exists(_.getQualifiedName == "java.lang.Object")
            if objTyped || t.tpe == anyT then Tree.Typed(t, tt(anyRef, e), anyRef, originOf(e)) else t
          val op = if b.getKind == EQ then "eq" else "ne"
          Some(binApply(op, asRef(l), asRef(r), ty(b)))

      private def binApply(op: String, l: Term, r: Term, resT: TypeRepr): Term =
        Tree.Apply(Tree.Select(l, opId(op), NoType, l.origin), List(r), opId(op), resT, l.origin)
      private def unApply(op: String, o: Term, resT: TypeRepr): Term =
        Tree.Apply(Tree.Select(o, opId(op), NoType, o.origin), Nil, opId(op), resT, o.origin)

      /** the Scala spelling of a java binary operator, or `scala.None` for a kind this arm does not
        * enumerate. An `Option`, not a defaulted string: `BinaryOperatorKind` is a java enum scalac
        * cannot check, and a fabricated name (`"?" + other`) would silently become a real method
        * call in `binApply`. `INSTANCEOF` (java's twentieth kind) never reaches here; enumerates
        * the other nineteen. */
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
