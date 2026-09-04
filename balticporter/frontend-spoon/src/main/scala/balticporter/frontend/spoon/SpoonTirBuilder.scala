package balticporter.frontend.spoon

// Split out of SpoonTir.scala for file size (context diet S2): the Spoon-model-to-TIR Builder.

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
import balticporter.frontend.spoon.SpoonTir.TypeShape

/** @param inMemorySources each compilation unit's text by file name, for units with no buffer of
  *   their own (`fromSources`). Empty for real files. Keyed because a position is meaningful in
  *   only one unit's buffer (CLAUDE.md §4.58). */
private[spoon] final class Builder(subs: Substitutions = Substitutions.none,
                            inMemorySources: Map[String, String] = Map.empty,
                            catalog: CatalogLog = CatalogLog.discarding,
                            annotations: AnnotationPolicy = AnnotationPolicy.none):
  /** the run's obligation log, in scope for every `Lowering.of` in this builder — `given` rather
    * than threaded through forty signatures that only the dispatch method needs it. */
  private[spoon] given CatalogLog = catalog
  private[spoon] val minter   = new Minter
  private[spoon] val tpScopes = collection.mutable.ArrayDeque[Map[String, SymId]]()
  /** an executable's own type parameters that are ERASED rather than declared — name → the type
    * every occurrence renders as. Consulted AHEAD of `tpScopes` since such a name has no binder.
    * One frame per executable. See [[unwritableResultVars]]. */
  private[spoon] val tpErased = collection.mutable.ArrayDeque[Map[String, TypeRepr]]()
  private[spoon] val selfRawStack = collection.mutable.ArrayDeque[(SymId, List[SymId])]()
  /** Type params LEGALLY in scope at the current point, respecting static-nested boundaries (a
    * static nested type cannot see its enclosing type's params). Distinct from `tpScopes`, which
    * keeps every enclosing frame for reference resolution. */
  private[spoon] val tpAccessible = collection.mutable.ArrayDeque[Map[String, SymId]]()
  /** names contributed by EXECUTABLES, parallel to `tpAccessible` (which merges each level into
    * one map, so a frame cannot simply be skipped). Hidden under [[atDeclScope]]. */
  private[spoon] val tpExecNames = collection.mutable.ArrayDeque[Set[String]]()

  /** The instantiation this class gives its ANCESTORS' type parameters, by name — needed so an
    * overriding member can fill a raw inherited type the same way the inherited declaration did,
    * rather than independently landing on `[?]` and disagreeing with it. */
  private[spoon] val inheritedInst = collection.mutable.ArrayDeque[Map[String, (TypeRepr, CtTypeReference[?])]]()
  /** …the same instantiation keyed by DECLARATION — see [[instantiationByDecl]]. Read only by
    * [[inheritedFormal]], at a call to a member an ANCESTOR declares. */
  private[spoon] val inheritedByDecl = collection.mutable.ArrayDeque[Map[(String, String), CtTypeReference[?]]]()
  /** FQNs of the enclosing class and its ancestors — a raw type nested in any of them is filled
    * from the names in scope, since those are the names it was declared against. */
  private[spoon] val enclosingFqns = collection.mutable.ArrayDeque[Set[String]]()
  /** FQNs of this class's ancestors — the only declarations whose formals are written in type
    * variables the inherited instantiation can speak about. */
  private[spoon] val ancestorFqns = collection.mutable.ArrayDeque[Set[String]]()
  private[spoon] var noInheritFill = false
  /** true while translating a member this class INHERITS (an override), so the inherited
    * instantiation applies only there — a member the class declares for itself has no such
    * obligation, and an unrelated ancestor's same-named type param must not leak into it. */
  private[spoon] var inOverridingMember = false
  /** The map is keyed by NAME, so an unrelated ancestor's `T` can collide with the type being
    * filled; require the candidate to satisfy the formal's own BOUND to make the match safe. */
  private[spoon] def inheritedTp(f: CtTypeParameter): Option[TypeRepr] =
    if true || noInheritFill || !inOverridingMember then scala.None // sge design: no inherited fill
    else inheritedInst.headOption.flatMap(_.get(f.getSimpleName)).collect {
      case (r, ref) if boundAdmits(f, ref) => r
    }

  private[spoon] def boundAdmits(f: CtTypeParameter, cand: CtTypeReference[?]): Boolean =
    Option(f.getSuperclass).filter(_.getQualifiedName != "java.lang.Object") match
      case None    => true
      case Some(b) => cand.isSubtypeOf(b)
  private[spoon] def accessibleTp(name: String): Option[SymId] =
    if declScopeOnly && tpExecNames.headOption.exists(_.contains(name)) then None
    else tpAccessible.headOption.flatMap(_.get(name))
  /** A nested type captures its enclosing type's params iff it is a NON-static inner class. */
  private[spoon] def capturesEnclosing(t: CtType[?]): Boolean =
    t.getDeclaringType != null && t.isInstanceOf[CtClass[?]] && !t.hasModifier(ModifierKind.STATIC)
  private[spoon] var inStatic = false
  private[spoon] def withStatic[A](s: Boolean)(f: => A): A =
    val prev = inStatic; inStatic = s
    try f finally inStatic = prev

  /** Every executable this walk considered, INCLUDING ones policy removed — published as
    * [[MemberIndex]], since a dropped executable has no symbol elsewhere to recover it from. */
  private[spoon] val seenMembers = collection.mutable.ListBuffer.empty[(MemberKey, MemberFacts)]
  private[spoon] val seenTypes   = collection.mutable.Set.empty[String]

  private[spoon] def build(types: List[CtType[?]]): Program =
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
  private[spoon] val claimed: java.util.Set[CtComment] =
    java.util.Collections.newSetFromMap(new java.util.IdentityHashMap[CtComment, java.lang.Boolean]())

  /** VERBATIM comment text, sliced from the original source (delimiters included). Never
    * `CtComment.toString`, which re-prints and loses exact formatting — unacceptable for a licence
    * notice (CLAUDE.md §4.57). Re-printed form is the fallback for a comment with no position. */
  private[spoon] def triviaOf(c: CtComment): Trivia =
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
  private[spoon] def sourceOf(el: CtElement): String =
    val pos = el.getPosition
    if pos == null || !pos.isValidPosition then inMemoryFor(null)
    else Option(pos.getCompilationUnit).flatMap(cu => Option(cu.getOriginalSourceCode)).getOrElse(inMemoryFor(pos))

  /** the in-memory buffer this position belongs to, by file name. Falls back to the only source
    * when exactly one exists; falls back to `""` (never guesses the wrong file) otherwise. */
  private[spoon] def inMemoryFor(pos: spoon.reflect.cu.SourcePosition): String =
    Option(pos).filter(_.isValidPosition).flatMap(p => Option(p.getFile)).map(_.getName)
      .flatMap(inMemorySources.get)
      .orElse(Option.when(inMemorySources.sizeIs == 1)(inMemorySources.values.head))
      .getOrElse("")

  /** the comments Spoon attached DIRECTLY to `el`. Deliberately NOT wrapped in a `catch` — a
    * harvest that throws is a defect to see (CLAUDE.md §4.6). */
  private[spoon] def leadingOf(el: CtElement): List[Trivia] =
    el.getComments.asScala.toList.filter(unheaded).map { c => claimed.add(c); triviaOf(c) }

  /** WHERE a comment is: file + start offset. `claimed` uses object identity, which a comment
    * the parser attached nowhere cannot provide — the file header instead claims by span. */
  private[spoon] def spanOf(c: CtComment): Option[(String, Int)] =
    val p = c.getPosition
    if p == null || !p.isValidPosition then scala.None
    else Some(unitKeyOf(p) -> p.getSourceStart)

  private[spoon] def unitKeyOf(p: spoon.reflect.cu.SourcePosition): String =
    Option(p.getFile).map(_.getPath)
      .orElse(Option(p.getCompilationUnit).flatMap(cu => Option(cu.getFile)).map(_.getPath))
      // in-memory units with no file: fall back to the unit OBJECT identity, not "<unknown>"
      .orElse(Option(p.getCompilationUnit).map(cu => "cu@" + System.identityHashCode(cu)))
      .getOrElse("<unknown>")

  /** spans the FILE HEADER has taken. Not `claimed`: see [[spanOf]]. */
  private[spoon] val headerSpans = collection.mutable.Set.empty[(String, Int)]

  /** a comment the file header did NOT take — the filter every finer harvest applies, so a
    * leading block that Spoon ALSO attached to the type is not emitted twice. */
  private[spoon] def unheaded(c: CtComment): Boolean = spanOf(c).forall(!headerSpans.contains(_))

  /** Comments Spoon attached to expression-level descendants, hoisted to the nearest enclosing
    * harvest point (the TIR carries trivia only on declarations/statements). MUST be called AFTER
    * the element's children have translated, or it swallows their comments too. */
  private[spoon] def deepComments(el: CtElement): List[Trivia] =
    el.getElements(new spoon.reflect.visitor.filter.TypeFilter[CtComment](classOf[CtComment]))
      .asScala.toList.filter(unheaded).filter(claimed.add).map(triviaOf)

  /** The FILE's own header: everything above the first line of code, plus anything hanging off
    * the imports — the licence, in every library seen so far. Read POSITIONALLY, not from the
    * parser's attachment model, which mis-attaches the second of two leading block comments to
    * the package declaration (ENGINE-LIMITS V3). Does not respect `claimed`: two top-level types
    * from one file each need the header, so it is cached per compilation unit instead. */
  private[spoon] val fileHeaders = collection.mutable.Map.empty[String, List[Trivia]]

  private[spoon] def fileHeader(t: CtType[?]): List[Trivia] =
    val pos = t.getPosition
    if pos == null || !pos.isValidPosition || pos.getCompilationUnit == null then Nil
    else fileHeaders.getOrElseUpdate(unitKeyOf(pos), harvestHeader(t, pos))

  private[spoon] def harvestHeader(t: CtType[?], pos: spoon.reflect.cu.SourcePosition): List[Trivia] =
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

  private[spoon] def kindOf(k: balticporter.core.TriviaKind): TriviaKind = k match
    case balticporter.core.TriviaKind.Line    => TriviaKind.Line
    case balticporter.core.TriviaKind.Block   => TriviaKind.Block
    case balticporter.core.TriviaKind.Javadoc => TriviaKind.Javadoc

  // ---- provenance ----
  private[spoon] def originOf(el: CtElement): Origin =
    val p = el.getPosition
    if p != null && p.isValidPosition then
      Origin(Option(p.getFile).map(_.getPath).getOrElse("<unknown>"), p.getLine, columnOf(p))
    else Origin.synthetic

  /** the position's COLUMN, or 0 where the unit has no source buffer to search (an in-memory unit
    * may have no `getOriginalSourceCode`, and Spoon's own column search then crashes). ZERO is
    * honest here — every `Origin` reader keys on FILE and LINE, never on the column. */
  private[spoon] def columnOf(p: spoon.reflect.cu.SourcePosition): Int =
    val cu = p.getCompilationUnit
    if cu == null || cu.getOriginalSourceCode == null then 0 else p.getColumn

  private[spoon] def tt(t: TypeRepr, el: CtElement): TypeTree = TypeTree(t, originOf(el))

  // ---- keys ----
  private[spoon] def typeKey(t: CtTypeReference[?]): String = t.getQualifiedName
  private[spoon] def memberKey(owner: SymId, sig: String): String = minterKeyOf(owner) + "#" + sig
  /** An external MEMBER always knows its owner — the key is derived from it. Passing it on is
    * what lets `owner#name` be reconstructed downstream (see `Minter.external`). */
  private[spoon] def externalMember(owner: SymId, sig: String, name: String,
                             descriptor: Option[Descriptor] = None,
                             info: TypeRepr = NoType): SymId =
    minter.external(memberKey(owner, sig), name, owner, descriptor, info)
  private[spoon] def minterKeyOf(id: SymId): String = "@" + id.raw // members hang off their owner's id
  private[spoon] def erasedSig(m: CtExecutable[?]): String =
    val ps = m.getParameters.asScala.toList
      .map(p => scala.util.Try(p.getType.getQualifiedName).getOrElse("?"))
      .mkString(",")
    s"($ps)"

  /** The member's DESCRIPTOR — its source-level parameter spelling, read from the PARSER (not
    * from the retyped `MethodType`, so `equals(Object)` stays `Object` rather than `scala.Any`).
    * Spelling matches `isDropped`'s (`dropMethods` keys against it). ALL parameters or none
    * ([[Descriptor.total]]) — a partial descriptor matches the wrong overload. */
  private[spoon] def descriptorOf(m: CtExecutable[?]): Option[Descriptor] =
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
  private[spoon] def isShadowDecl(m: CtExecutable[?]): Boolean =
    Option(m.getParent(classOf[CtType[?]])) match
      case scala.None    => true
      case Some(t)       => t.isShadow

  /** …the same question asked of a call's REFERENCE: no declaration at all means external, since
    * this program's own members are always parsed and therefore have one. */
  private[spoon] def isExternalCallee(ex: CtExecutableReference[?]): Boolean =
    Option(ex.getExecutableDeclaration) match
      case scala.None => true
      case Some(d)    => isShadowDecl(d)

  /** The `MethodType` of an EXTERNAL member (ENGINE-LIMITS K15) — only for a SHADOW declaration
    * ([[isShadowDecl]]). Rendered SCOPE-FREE: a type variable, intersection or raw generic renders
    * as no answer rather than a name from the CALLER's scope, since an external symbol is interned
    * once and never clobbered. ALL slots or NONE ([[Descriptor.total]]'s rule). */
  private[spoon] def externalSignature(m: CtExecutable[?]): TypeRepr =
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
  private[spoon] def externalSlot(tr: CtTypeReference[?]): TypeRepr = TypeShape.of(tr) match
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
  private[spoon] def externalArg(tr: CtTypeReference[?]): TypeRepr = externalSlot(tr) match
    case NoType => TypeBounds(NoType, NoType)
    case t      => t

  // ---- type parameter resolution ----
  /** parallel to `tpScopes`: is this frame an EXECUTABLE's own type parameters? */
  private[spoon] val tpIsExec = collection.mutable.ArrayDeque[Boolean]()
  /** parallel to `tpScopes`: the SPOON declarations behind the ids, read by
    * [[resolveTypeParamDecl]]. Pushed and popped with `tpScopes` — the two are indexed together. */
  private[spoon] val tpDecls = collection.mutable.ArrayDeque[Map[String, CtTypeParameter]]()

  /** Render a type as its DECLARATION site would have, not as the current reading scope would —
    * the name-directed raw fill is scope-dependent by design, so re-rendering in the reading
    * scope would silently disagree with the declared type. Hides executable frames only: a
    * field's type cannot mention a method's type parameters, so this is exact, not approximate. */
  private[spoon] def atDeclScope[A](f: => A): A =
    val saved = declScopeOnly
    declScopeOnly = true
    try f finally declScopeOnly = saved
  private[spoon] var declScopeOnly = false

  /** WHICH frame a name resolves in — stated once, because [[resolveTypeParam]] and
    * [[resolveTypeParamDecl]] must answer about the SAME declaration or the raw fill's licence
    * (`licensedFills`) is read off one variable and applied to another. */
  private[spoon] def tpFrameOf(name: String): Option[Int] =
    tpScopes.iterator.zipAll(tpIsExec.iterator, Map.empty[String, SymId], false).zipWithIndex.collectFirst {
      case ((m, isExec), i) if m.contains(name) && !(declScopeOnly && isExec) => i
    }

  private[spoon] def resolveTypeParam(name: String): Option[SymId] =
    tpFrameOf(name).map(i => tpScopes(i)(name))

  /** the type an ERASED type-parameter name renders as, or `None` for an ordinary one. Consulted
    * AHEAD of [[resolveTypeParam]]: an erased parameter was never minted and has no id. */
  private[spoon] def erasedTypeParam(name: String): Option[TypeRepr] =
    tpErased.iterator.collectFirst { case m if m.contains(name) => m(name) }

  /** an executable's own type parameters that have NO WRITABLE INSTANTIATION ANYWHERE — java's
    * UNCHECKED generic method (JLS 8.4.2 subsignature-by-erasure), erased at the declaration to
    * its own bound. Three conditions, all required: the variable occurs in no PARAMETER type; the
    * bound MENTIONS THE VARIABLE ITSELF (F-bound, the load-bearing conjunct, ENGINE-LIMITS G8);
    * the RESULT mentions the variable. Does not touch a variable the DECLARING TYPE owns. */
  private[spoon] def unwritableResultVars(m: CtExecutable[?]): List[CtTypeParameter] = m match
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
  private[spoon] def resolveTypeParamDecl(name: String): Option[CtTypeParameter] =
    tpFrameOf(name).flatMap(i => if i < tpDecls.size then tpDecls(i).get(name) else None)

  /** Mint ids for all formals FIRST (so bounds can self-reference — F-bounds), then
    * translate each bound with the frame in scope. Returns the frame and the TypeDefs. */
  private[spoon] def mintTypeParams(declKey: String, owner: SymId, tps: List[CtTypeParameter]): (Map[String, SymId], List[Tree.TypeDef]) =
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
  private[spoon] def declFrame(tps: List[CtTypeParameter]): Map[String, CtTypeParameter] =
    tps.map(tp => tp.getSimpleName -> tp).toMap

  // Java's type parameters are always reference types (`<T>` means `<T extends Object>`);
  // scala's `[T]` means `T <: Any`, strictly weaker — restoring the bound is a java fact (§1a).
  /** parent formal NAME -> the argument this class supplies, walking supertypes breadth-first
    * (so a grandparent's names are covered too). */
  private[spoon] def ancestorsOf(t: CtType[?]): Set[String] =
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
  private[spoon] def parentInstantiations(t: CtType[?]): List[(String, String, CtTypeReference[?])] =
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

  private[spoon] def instantiationOfParents(t: CtType[?]): Map[String, (TypeRepr, CtTypeReference[?])] =
    val out = collection.mutable.Map[String, (TypeRepr, CtTypeReference[?])]()
    parentInstantiations(t).foreach { (_, nm, a) =>
      if !out.contains(nm) then out(nm) = (tpe(a), a)
    }
    out.toMap

  /** …the same instantiations keyed by DECLARATION rather than name — `(owner FQN, formal name)`,
    * `ParentSubst`'s own identity — so two ancestors' same-named `T`s cannot collide. */
  private[spoon] def instantiationByDecl(t: CtType[?]): Map[(String, String), CtTypeReference[?]] =
    val out = collection.mutable.Map[(String, String), CtTypeReference[?]]()
    parentInstantiations(t).foreach { (owner, nm, a) => if !out.contains(owner -> nm) then out(owner -> nm) = a }
    out.toMap

  /** is `r` declared INSIDE a class currently on the enclosing-class stack? */
  private[spoon] def selfAndAncestors(t: CtType[?]): Set[String] =
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

  private[spoon] def nestedInScope(r: CtTypeReference[?]): Boolean =
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

  private[spoon] def boundsOf(tp: CtTypeParameter): TypeBounds =
    Option(tp.getSuperclass).filter(_.getQualifiedName != "java.lang.Object").map(fbound) match
      case Some(hi) => TypeBounds(NoType, hi)
      case None     => TypeBounds(NoType, objectT)

  /** Reconstruct a raw generic type's args from IN-SCOPE type parameters of the same NAME
    * (wildcards for the rest) — preserves self-reference/enclosing instantiation that a plain
    * wildcard fill erases. `None` for arity-0. Every slot is LICENSED first ([[licensedFills]]),
    * since java stops checking at a raw use and scala does not (ENGINE-LIMITS G30). */
  private[spoon] def nameFilledArgs(r: CtTypeReference[?], resolve: String => Option[SymId],
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
  private[spoon] def licensedFills(formals: List[CtTypeParameter],
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
  private[spoon] def boundSpelling(r: CtTypeReference[?]): String = TypeShape.of(r) match
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
  private[spoon] def mentionedTypeVarNames(r: CtTypeReference[?]): Set[String] = TypeShape.of(r) match
    case TypeShape.Absent              => Set.empty
    case TypeShape.Wildcard(_, b, _)   => b.map(mentionedTypeVarNames).getOrElse(Set.empty)
    case TypeShape.Variable(tv)        => Set(tv.getSimpleName)
    case TypeShape.Arr(_, c)           => mentionedTypeVarNames(c)
    case TypeShape.Intersection(_, bs) => bs.flatMap(mentionedTypeVarNames).toSet
    case TypeShape.Prim(_)             => Set.empty
    case TypeShape.Named(_, as)        => as.flatMap(mentionedTypeVarNames).toSet

  private[spoon] def objectT: TypeRepr = TypeRef(NoPrefix, minter.external("java.lang.Object", "Object"))

  /** The ERASURE of a type variable — its first bound with nested variables erased, else Object.
    * Used ONLY to build CASTS at wildcard-receiver call sites — must never drive a DECLARATION's
    * type (that breaks assignment of concrete generic values, measured catastrophic). */
  private[spoon] def erasureOfFormal(f: CtTypeParameter, seen: Set[String], depth: Int): TypeRepr =
    Option(f.getSuperclass).filter(_.getQualifiedName != "java.lang.Object") match
      case None    => objectT
      case Some(b) => erasedType(b, seen + f.getSimpleName, depth)

  private[spoon] def erasedType(b: CtTypeReference[?], seen: Set[String], depth: Int): TypeRepr =
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
  private[spoon] def substRepr(t: TypeRepr, from: TypeRepr, to: TypeRepr): TypeRepr =
    if t == from then to
    else t match
      case AppliedType(tc, as) => AppliedType(substRepr(tc, from, to), as.map(substRepr(_, from, to)))
      case other               => other

  private[spoon] def erasedFormal(f: CtTypeReference[?], subst: Map[String, TypeRepr] = Map.empty,
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
  private[spoon] def rawFormalNodes(r: CtTypeReference[?]): List[CtTypeParameter] =
    if r == null || r.isPrimitive || r.isInstanceOf[CtWildcardReference] ||
       r.isInstanceOf[CtArrayTypeReference[?]] ||
       (r.getActualTypeArguments.size) > 0 then Nil
    else typeDeclarationOf(r).map(_.getFormalCtTypeParameters.asScala.toList).getOrElse(Nil)

  private[spoon] def rawFormalsOf(r: CtTypeReference[?]): List[String] = rawFormalNodes(r).map(_.getSimpleName)

  /** [[mentionsTypeVar]], but aware that a RAW generic use is emitted name-FILLED from the
    * same-named in-scope parameters — so `ResourceData` inside `ParticleBatch<T>` really does
    * depend on `T`, even though nothing in the Spoon type says so. */
  private[spoon] def mentionsTypeVarFilled(tr: CtTypeReference[?], names: Set[String]): Boolean = tr match
    case tv: CtTypeParameterReference => names(tv.getSimpleName) || boundMentions(tv, names)
    case arr: CtArrayTypeReference[?] => mentionsTypeVarFilled(arr.getComponentType, names)
    case _ =>
      val args = tr.getActualTypeArguments.asScala.toList
      if args.nonEmpty then args.exists(mentionsTypeVarFilled(_, names))
      else rawFormalsOf(tr).exists(names)

  /** A METHOD type variable declared in terms of the receiver's depends on the receiver just as a
    * bare formal does. Bound only, never the variable's own NAME (a same-named callee `<T>` is a
    * different variable — the confusion [[tpConcrete]] avoids). Depth-limited (F-bounds). */
  private[spoon] def boundMentions(tv: CtTypeParameterReference, names: Set[String], fuel: Int = 2): Boolean =
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
  private[spoon] def mentionsRawGeneric(tr: CtTypeReference[?]): Boolean = TypeShape.of(tr) match
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
  private[spoon] def formalArity(r: CtTypeReference[?]): Int =
    typeDeclarationOf(r).map(_.getFormalCtTypeParameters.size).getOrElse(0)

  /** the ONE Spoon lookup in the arity family where an absent value is normal — see
    * [[formalArity]] for why nothing else in that computation may share its `catch`. */
  private[spoon] def typeDeclarationOf(r: CtTypeReference[?]): Option[spoon.reflect.declaration.CtType[?]] =
    if r == null then scala.None
    else try Option(r.getTypeDeclaration) catch { case _: Throwable => scala.None }

  /** the ONE Spoon lookup for a type variable's declaration where an absent value is normal —
    * the type variable belongs to an external generic whose declaration is not on the classpath.
    * Callers treat `None` as "unknown/external"; see [[typeDeclarationOf]] for the same argument
    * at the type level.  `CLAUDE.md` §4.6: one function, one doc, one `catch`. */
  private[spoon] def typeParamDeclOf(tv: CtTypeParameterReference): Option[CtTypeParameter] =
    try Option(tv.getDeclaration) catch { case _: Throwable => scala.None }

  /** the ONE Spoon lookup for an executable's declaration where an absent value is normal —
    * the method is external and its source declaration is not on the classpath.  Callers that
    * receive `None` decline the rule they were about to apply, which is the correct fallback
    * for an unknowable signature.  `CLAUDE.md` §4.6. */
  private[spoon] def execDeclOf(ex: CtExecutableReference[?]): Option[CtExecutable[?]] =
    try Option(ex.getExecutableDeclaration) catch { case _: Throwable => scala.None }

  /** the ONE Spoon lookup for an annotation's type reference where an absent value is normal —
    * the annotation class might not be on the classpath.  `CLAUDE.md` §4.6. */
  private[spoon] def annotationTypeRefOf(a: CtAnnotation[?]): Option[CtTypeReference[?]] =
    try Option(a.getAnnotationType) catch { case _: Throwable => scala.None }

  /** the ONE Spoon lookup for a FIELD's declaration where an absent value is normal — external
    * class not on the classpath. Callers decline the rule they were about to apply. CLAUDE.md §4.6 */
  private[spoon] def fieldDeclOf(ref: CtFieldReference[?]): Option[CtField[?]] =
    try Option(ref.getFieldDeclaration) catch { case _: Throwable => scala.None }

  /** JAVA'S FUNCTIONAL-INTERFACE QUESTION (JLS 9.8), computed here from the class file (CLAUDE.md
    * §4.56) since the TIR only interns members the program references. Target must be an
    * INTERFACE; abstract methods counted INHERITED (`getAllMethods`); `static`/`default` excluded;
    * a member override-equivalent to `java.lang.Object`'s excluded. Unreadable → [[Sam.Answer.Unreadable]],
    * never `No`. `java.io.Serializable` reported BESIDE the answer, not folded into it. */
  private[spoon] def samAnswerOf(r: CtTypeReference[?]): Sam.Answer =
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
  private[spoon] def samAbstracts(r: CtTypeReference[?]): List[CtMethod[?]] =
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
  private[spoon] def redeclares(sub: CtMethod[?], sup: CtMethod[?]): Boolean =
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
  private[spoon] def samResultTpt(l: CtLambda[?]): Option[TypeTree] =
    if !returnsAValue(l) then scala.None
    else samAbstracts(l.getType) match
      case one :: Nil =>
        val rt = Option(one.getType).map(adaptedToTarget(l.getType, _))
        rt.filter(!mentionsTypeVariable(_, 8)).map(r => tt(tpe(r), l))
      case _ => scala.None

  /** the SAM method's declared result, read IN THE TARGET REFERENCE'S CONTEXT. Asked only where
    * the declared type mentions a variable. Default on failure is the UNADAPTED type, which still
    * mentions the variable — so the caller refuses rather than reading a fabricated answer (§4.6). */
  private[spoon] def adaptedToTarget(target: CtTypeReference[?], t: CtTypeReference[?]): CtTypeReference[?] =
    if target == null || !mentionsTypeVariable(t, 8) then t
    else
      Option(new TypeAdaptor(target).adaptType(t)).getOrElse(t)

  /** does THIS lambda hold a `return` with a VALUE — stopping at a nested lambda/anonymous method,
    * whose `return`s are that construct's own (JLS 15.27.2). Asked of the java, before the body
    * translates, since `TirEmitter`'s equivalent walks the lowered tree. */
  private[spoon] def returnsAValue(l: CtLambda[?]): Boolean =
    val body = l.getBody
    body != null &&
      body.getElements(new spoon.reflect.visitor.filter.TypeFilter(classOf[CtReturn[?]]))
        .asScala.exists(r => r.getReturnedExpression != null &&
          (r.getParent(classOf[spoon.reflect.declaration.CtExecutable[?]]) eq l))

  /** a type that mentions a TYPE VARIABLE anywhere — the declaration's own `T`, an array of one,
    * or one inside an argument. Fuel-bounded, because a bound can be recursive
    * (`T extends Comparable<T>`) and an unbounded walk on a class file is a hang. */
  private[spoon] def mentionsTypeVariable(r: CtTypeReference[?], fuel: Int): Boolean =
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
  private[spoon] def serializableAncestry(r: CtTypeReference[?]): Boolean =
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
  private[spoon] def isGenericUse(tr: CtTypeReference[?]): Boolean = TypeShape.of(tr) match
    case TypeShape.Absent            => false
    case TypeShape.Variable(_)       => false
    case TypeShape.Wildcard(_, _, _) => false   // the arm the variable one shadowed said the same
    case TypeShape.Arr(_, _)         => false
    case TypeShape.Prim(_)           => false
    case s                           => s.args.nonEmpty || formalArity(s.ref) > 0

  /** a RAW use of a generic class — `Cell`, not `Cell<T>`. Exactly where Java stops checking. */
  private[spoon] def isRawGenericUse(tr: CtTypeReference[?]): Boolean =
    isGenericUse(tr) && (tr.getActualTypeArguments.isEmpty)

  /** does every type variable this type mentions resolve HERE? `tpe` renders an unresolved one as
    * a `?T` stub, which is not valid Scala — so a synthesized cast must never target such a type. */
  private[spoon] def tpResolvable(tr: CtTypeReference[?]): Boolean = TypeShape.of(tr) match
    case TypeShape.Absent       => true
    case TypeShape.Variable(tv) => resolveTypeParam(tv.getSimpleName).isDefined
    // PRESERVED SHADOW (ENGINE-LIMITS G21) — answers `false` for every wildcard
    case TypeShape.Wildcard(_, _, _) => false
    case TypeShape.Arr(_, c)         => tpResolvable(c)
    case s                           => s.args.forall(tpResolvable)

  /** free of type VARIABLES entirely. A callee's formal must satisfy this before we may render it
    * at a CALL SITE: `resolveTypeParam` is name-based, so a callee's `<T>` would silently bind to
    * an unrelated in-scope `T` (`ResourceData<T>` vs `Json.readValue<T>`) and emit a wrong cast. */
  private[spoon] def tpConcrete(tr: CtTypeReference[?]): Boolean = TypeShape.of(tr) match
    case TypeShape.Absent            => true
    case TypeShape.Variable(_)       => false
    case TypeShape.Wildcard(_, _, _) => false   // PRESERVED SHADOW — `ENGINE-LIMITS.md` G21
    case TypeShape.Arr(_, c)         => tpConcrete(c)
    case s                           => s.args.forall(tpConcrete)

  /** [[tpConcrete]] with its one over-exclusion repaired: a type VARIABLE passes when it is
    * LITERALLY the one in scope here ([[sameVarInScope]] — same declaration, not just same name).
    * A separate function, not a flag, since every existing `tpConcrete` caller must keep its
    * current answer (one derivation per question). */
  private[spoon] def tpNameableHere(tr: CtTypeReference[?]): Boolean = TypeShape.of(tr) match
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
  private[spoon] def sameVarInScope(tv: CtTypeParameterReference): Boolean =
    Option(tv.getDeclaration).map(_.getParent) match
      case Some(ct: CtType[?]) =>
        resolveTypeParam(tv.getSimpleName)
          .contains(minter.resolve(ct.getQualifiedName + "$$" + tv.getSimpleName))
      case _ => false

  /** Concrete, or mentioning only type variables OWNED BY THE CALLEE — never in scope at the call
    * site, so Java's view of the formal is its erasure (an unbounded `<T>` is `Object`), which is
    * what an unchecked cast must name for Scala to infer `T`. Unbounded variables must NOT be
    * excluded: measured false, costs `AssetManager.load` casts sge also writes by hand. */
  private[spoon] def calleeBounded(tr: CtTypeReference[?]): Boolean = TypeShape.of(tr) match
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
  private[spoon] def tpBoundErased(tr: CtTypeReference[?]): TypeRepr = TypeShape.of(tr) match
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
  private[spoon] def substFormal(f: CtTypeReference[?], subst: Map[String, TypeRepr]): Option[TypeRepr] =
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
  private[spoon] def mentionsTypeVarBounded(tr: CtTypeReference[?], names: Set[String]): Boolean = tr match
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
  private[spoon] def tpAccessibleHere(tr: CtTypeReference[?]): Boolean = TypeShape.of(tr) match
    case TypeShape.Absent            => true
    case TypeShape.Variable(tv)      => accessibleTp(tv.getSimpleName).isDefined
    case TypeShape.Wildcard(_, _, _) => false   // PRESERVED SHADOW — `accessibleTp("?")` is empty
    case TypeShape.Arr(_, c)         => tpAccessibleHere(c)
    case s                           => s.args.forall(tpAccessibleHere)

  /** the NAMES of every type variable a type mentions. */
  private[spoon] def typeVarsOf(tr: CtTypeReference[?]): Set[String] = TypeShape.of(tr) match
    case TypeShape.Absent       => Set.empty
    case TypeShape.Variable(tv) => Set(tv.getSimpleName)
    // PRESERVED SHADOW (ENGINE-LIMITS G21) — reports the literal name "?", never bound variables
    case TypeShape.Wildcard(w, _, _) => Set(w.getSimpleName)
    case TypeShape.Arr(_, c)         => typeVarsOf(c)
    case TypeShape.Prim(_)           => Set.empty
    case s                           => s.args.toSet.flatMap(typeVarsOf)

  /** does this type mention ANY type variable (directly, in an array element, or in an argument)? */
  private[spoon] def mentionsAnyTypeVar(tr: CtTypeReference[?]): Boolean = TypeShape.of(tr) match
    case TypeShape.Absent      => false
    case TypeShape.Variable(_) => true
    // SHADOW FLIPPED (ENGINE-LIMITS G21): `?` mentions no type var itself; its bound is walked
    case TypeShape.Wildcard(_, b, _) => b.exists(mentionsAnyTypeVar)
    case TypeShape.Arr(_, c)         => mentionsAnyTypeVar(c)
    case s                           => s.args.exists(mentionsAnyTypeVar)

  /** Is `actual` the same type as `want` with some type ARGUMENTS collapsed to `Object` or a
    * wildcard? Exactly the shape of an UNCHECKED CONVERSION java performs silently. Compares
    * RENDERED types, requires the same type constructor and arity — narrow deliberately. */
  private[spoon] def uncheckedFrom(actual: TypeRepr, want: TypeRepr): Boolean = (actual, want) match
    case (AppliedType(tc1, as1), AppliedType(tc2, as2)) if tc1 == tc2 && as1.sizeIs == as2.size =>
      as1.zip(as2).exists((a, w) => a != w) &&
        as1.zip(as2).forall((a, w) => a == w || a == objectT || a.isInstanceOf[TypeBounds] || uncheckedFrom(a, w))
    case _ => false

  /** does this rendered type carry a WILDCARD anywhere — i.e. is it the product of our raw fill? */
  private[spoon] def hasWildcard(t: TypeRepr): Boolean = t match
    case _: TypeBounds       => true
    case AppliedType(tc, as) => hasWildcard(tc) || as.exists(hasWildcard)
    case _                   => false

  /** Is this type-parameter reference THE SAME parameter as the one its simple name resolves to
    * here — compared by minted id (`<owner FQN>$$T`), not by name, since a callee's `<T>` could
    * otherwise silently bind to an unrelated in-scope `T`. Method-level params never match. */
  private[spoon] def sameTypeParamHere(tv: CtTypeParameterReference): Boolean =
    val owner = (typeParamDeclOf(tv))
      .flatMap(d => Option(d.getParent)).collect { case ct: CtType[?] => ct.getQualifiedName }
    owner.exists(o => accessibleTp(tv.getSimpleName).exists(id =>
      minter.fullNameOf(id) == o + "$$" + tv.getSimpleName))

  /** Can this DECLARED formal be named verbatim at the current call site? Concrete parts always;
    * a type variable only when it is literally the same parameter ([[sameTypeParamHere]]). */
  private[spoon] def formalNameableHere(tr: CtTypeReference[?]): Boolean = TypeShape.of(tr) match
    case TypeShape.Absent             => false
    case TypeShape.Variable(tv)       => sameTypeParamHere(tv)
    case TypeShape.Arr(_, c)          => formalNameableHere(c)
    case TypeShape.Wildcard(_, _, _)  => false   // the arm the variable one shadowed said the same
    case TypeShape.Intersection(_, _) => false
    case TypeShape.Prim(_)            => true
    case s =>
      if s.args.nonEmpty then s.args.forall(formalNameableHere) else formalArity(s.ref) == 0

  /** does this rendered type name `scala.Array`? */
  private[spoon] def isScalaArrayType(t: TypeRepr): Boolean = t match
    case AppliedType(TypeRef(_, s), _ :: Nil) => minter.fullNameOf(s) == "scala.Array"
    case _                                    => false

  /** does this type mention any of `names` as a type variable (directly or in its arguments)? */
  private[spoon] def mentionsTypeVar(tr: CtTypeReference[?], names: Set[String]): Boolean = tr match
    case tv: CtTypeParameterReference => names(tv.getSimpleName)
    case arr: CtArrayTypeReference[?] => mentionsTypeVar(arr.getComponentType, names)
    case _ =>
      tr.getActualTypeArguments.asScala.exists(mentionsTypeVar(_, names))

  /** Translate a type-parameter bound. A RAW generic bound (`N extends Node`) is Java's idiom
    * for a self-referential (F-)bound; name-directed fill (see [[nameFilledArgs]]) rebuilds it
    * — the decl's own params are already in scope here (minted before bounds are translated) —
    * rather than erasing to `Node[?, ?, ?]`. Non-raw / array / intersection / type-var bounds
    * defer to `tpe`. */
  private[spoon] def fbound(tr: CtTypeReference[?]): TypeRepr =
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
  private[spoon] def tpe(tr: CtTypeReference[?]): TypeRepr =
    if tr == null then objectT
    else
      val at = originOf(tr)
      Typing.ofReference(SpoonKinds.refNameOf(tr.getClass), at, tr)(tpeArm(tr, at))

  /** JS-G07 and JS-G08 — the two questions a PLAIN class reference is asked, STATED ONCE and
    * called from both arms a `CtTypeReference` reaches (primitive fast path and the general arm
    * are ONE Spoon kind). Both about a RAW USE (JLS 4.8); G08 narrows to sites where the fill
    * DEPENDS on the frame (a companion body cannot name the class's own params). */
  private[spoon] def rawUseConsults(r: CtTypeReference[?], at: Origin)(using Obligations): Unit =
    val raw = isRawGenericUse(r)
    Obligations.consult(JS.G(7), at)(Option.when(raw)(()))
    Obligations.consult(JS.G(8), at)(Option.when(raw && (inStatic || nestedInScope(r)))(()))

  private[spoon] def tpeArm(tr: CtTypeReference[?], at: Origin)(using Obligations): TypeRepr = tr match
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
  private[spoon] def typeSym(r: CtTypeReference[?]): SymId =
    val anns = try
      val td = r.getTypeDeclaration
      if td != null && td.getAnnotations.asScala.exists(
        _.getAnnotationType.getQualifiedName == "java.lang.FunctionalInterface")
      then List(Annot(TypeRef(NoPrefix, minter.external("java.lang.FunctionalInterface", "FunctionalInterface")), Nil, Origin.synthetic))
      else Nil
    catch case _: Exception => Nil // unreadable class file — refuter polarity: treat as unannotated
    minter.external(typeKey(r), r.getSimpleName, annotations = anns)

  private[spoon] def primName(j: String): String = j match
    case "int"     => "Int";  case "long"    => "Long";  case "short"  => "Short"
    case "byte"    => "Byte"; case "char"    => "Char";  case "boolean" => "Boolean"
    case "float"   => "Float"; case "double" => "Double"; case "void"  => "Unit"
    case other     => other.capitalize

  // ---- declarations ----
  /** @param owner overrides the DECLARING TYPE as owner; set only for a METHOD-LOCAL class (JS-C30).
    * @param sourceName overrides the symbol's name; set only for a local class (Spoon's qualified
    *   name holds java's binary disambiguator, not a legal Scala identifier).
    * @param selfClass , outerVars the ANONYMOUS-class wiring, reused verbatim for local classes. */
  private[spoon] def classDef(t: CtType[?], owner: Option[SymId] = scala.None,
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
  private[spoon] def accessorBodies(t: CtType[?], id: SymId, comps: List[RecordComponent],
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
  private[spoon] def recordComponents(t: CtType[?], id: SymId): Option[List[RecordComponent]] = t match
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
  private[spoon] def canonicalised(t: CtType[?], id: SymId, comps: List[RecordComponent],
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
  private[spoon] def reordered(comps: List[RecordComponent], implicitCanonical: Set[SymId],
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
  private[spoon] def enumCase(enumId: SymId, v: CtEnumValue[?]): Tree.EnumCase =
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
  private[spoon] var anonSeq = 0

  /** A Java ANONYMOUS CLASS — `new Base(args) { members }`. Members are owned by a SYNTHETIC
    * symbol (so two listeners' `clicked` cannot collide), but bodies translate with `this` bound
    * to the ENCLOSING class (Spoon's own reading of the implicit `this`; emitter renders
    * `Outer.this.m`). Captured locals seeded by NAME, closed over directly. */
  private[spoon] def anonClass(nc: CtNewClass[?], enclosing: SymId, outerVars: Map[String, SymId]): Option[Tree.AnonClass] =
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
  private[spoon] def overridesInherited(m: CtMethod[?]): Boolean =
    // a java STATIC method never overrides — it HIDES; excluded here since Spoon's own
    // `getTopDefinitions` cannot tell the two apart
    !(m.isStatic) &&
      (universalMember(m) || inheritedFromSource(m))

  /** Does this redeclare one of `java.lang.Object`'s members? Matched on the full SIGNATURE, not
    * name and arity: `equals(VertexAttribute)` is an OVERLOAD that overrides nothing, and marking
    * it `override` is an error scala reports and java has no opinion on. */
  private[spoon] def universalMember(m: CtMethod[?]): Boolean =
    val ps = m.getParameters.asScala.toList.map(p =>
      Option(p.getType).map(_.getQualifiedName).getOrElse("?"))
    (m.getSimpleName, ps) match
      case ("toString" | "hashCode" | "clone" | "finalize", Nil) => true
      case ("equals", List("java.lang.Object"))                   => true
      case _                                                       => false

  private[spoon] def inheritedFromSource(m: CtMethod[?]): Boolean =
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

  private[spoon] def defineType(t: CtType[?], owner: Option[SymId] = scala.None,
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
  private[spoon] def permittedTypes(t: CtType[?]): List[SymId] =
    t match
      case s: spoon.reflect.declaration.CtSealable =>
        s.getPermittedTypes.asScala.toList
          .map(r => typeKey(r) -> r)
          .sortBy(_._1)
          .map((k, r) => minter.external(k, r.getSimpleName))
      case _ => Nil

  private[spoon] def ownerSym(t: CtType[?]): SymId =
    Option(t.getDeclaringType).map(dt => minter.external(typeKey(dt.getReference), dt.getSimpleName)).getOrElse(SymId.None)

  /** superclass (Extends, first) then interfaces (Mixin) — the parent linearization. */
  private[spoon] def superTypes(t: CtType[?]): List[TypeTree] =
    val sc = t match
      case c: CtClass[?] => Option(c.getSuperclass).filter(_.getQualifiedName != "java.lang.Object")
      case _             => None
    (sc.toList ++ t.getSuperInterfaces.asScala.toList).map(tr => tt(tpe(tr), t))

  /** `selfClass` is the class a body's `this` denotes when it is NOT the member's owner — the
    * case for an ANONYMOUS class, whose members are owned by a synthetic symbol (so their keys
    * stay distinct) while Java's implicit `this` inside them still reports the ENCLOSING class
    * for every enclosing member it reaches. Defaulting to `owner` keeps every other caller
    * unchanged. */
  private[spoon] def selfOf(owner: SymId, selfClass: SymId): SymId =
    if selfClass == SymId.None then owner else selfClass

  /** …the DECLARATION obligation scope (`Dispatch.Declaration`) — a field initialiser is a JLS
    * 5.2 assignment slot like a local's, but `CtField` enters neither statement nor expression
    * dispatch, so it needed its own. Opens for EVERY field, initialiser or not (ENGINE-LIMITS F8). */
  private[spoon] def fieldDef(owner: SymId, f: CtField[?], selfClass: SymId = SymId.None, outerVars: Map[String, SymId] = Map.empty,
                       anonSelf: SymId = SymId.None, anonQName: String = ""): Tree.ValDef =
    Lowering.of(SpoonKinds.nameOf(f.getClass), Dispatch.Declaration, originOf(f), f)(fieldDef1(owner, f, selfClass, outerVars, anonSelf, anonQName))

  private[spoon] def fieldDef1(owner: SymId, f: CtField[?], selfClass: SymId, outerVars: Map[String, SymId],
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

  private[spoon] def execDef(owner: SymId, m: CtExecutable[?], name: String, selfClass: SymId = SymId.None,
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
  private[spoon] def annotationDefault(m: CtExecutable[?], bt: BodyTranslator): Option[Term] = m match
    case am: CtAnnotationMethod[?] =>
      Option(am.getDefaultExpression).map(e => bt.coercedExprOf(am.getType, e))
    case _ => scala.None

  /** Java annotations on a declaration, plus the names of any this could not carry. Element values
    * translate on the ordinary expression path; one that throws is REPORTED (`dropped`), never
    * silently emitted without its arguments. */
  private[spoon] def annotationsOf(el: CtElement, bt: Option[BodyTranslator],
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
  private[spoon] def arrayShorthand(ref: CtTypeReference[?], key: String, e: CtExpression[?], t: Term): Term =
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

  private[spoon] def qualified(owner: SymId, member: String): String = minter.fullNameOf(owner) + "#" + member
  private[spoon] def unitT: TypeRepr = TypeRef(NoPrefix, minter.external("scala.Unit", "Unit"))

  // ---- flags ----
  private[spoon] def has(m: CtModifiable, k: ModifierKind): Boolean = m.hasModifier(k)
  import ModifierKind.*

  /** Java's FOURTH access level, and it is NOT "no modifier is present". Default access is
    * granted implicitly in three places (DESIGN §8.7): interface/`@interface` members (JLS 9.4,
    * 9.6, 9.5, 9.3) are `public`; enum constructors (JLS 8.9.2) are `private`; enum constants and
    * anonymous/local classes carry no user-written access. Read from the JLS, not
    * `hasModifier(PUBLIC)`, since the parser's implicit-modifier model is what's in question. */
  private[spoon] def implicitlyPublic(el: CtElement): Boolean = el match
    case m: CtTypeMember => m.getDeclaringType.isInstanceOf[CtInterface[?]]
    case _               => false

  /** JLS-effective access for one declaration: exactly one of the three is set, or all are clear
    * and it is public. `implicitPrivate` is the enum-constructor case. */
  private[spoon] def access(m: CtModifiable, el: CtElement, implicitPrivate: Boolean = false): (Boolean, Boolean, Boolean) =
    val priv = has(m, PRIVATE) || (implicitPrivate && !has(m, PUBLIC) && !has(m, PROTECTED) && !has(m, PRIVATE))
    val prot = !priv && has(m, PROTECTED)
    val pkg  = !priv && !prot && !has(m, PUBLIC) && !implicitlyPublic(el)
    (priv, prot, pkg)

  private[spoon] def typeFlags(t: CtType[?]): Flags =
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

  private[spoon] def fieldFlags(f: CtField[?]): Flags =
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

  private[spoon] def execFlags(m: CtExecutable[?]): Flags = m match
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

  private[spoon] def posKey(el: CtElement): Int =
    val p = el.getPosition
    if p != null && p.isValidPosition then p.getSourceStart else Int.MaxValue

  /** a METHOD-LOCAL class's SOURCE name (JLS 14.3, catalog JS-C30). Spoon's binary name
    * (`1Local`) is the interning key, not a legal identifier — strip the leading digit run
    * (JLS 3.8). Where stripping leaves nothing, keep the binary name (fails loudly, not silently). */
  private[spoon] def localName(t: CtType[?]): String =
    val binary  = t.getSimpleName
    val stripped = binary.dropWhile(_.isDigit)
    if stripped.isEmpty then binary else stripped

  private[spoon] def simpleName(q: String): String =
    val afterDot = q.substring(q.lastIndexOf('.') + 1)
    afterDot.substring(afterDot.lastIndexOf('$') + 1)

  private[spoon] def unsupported(el: CtElement, what: String): Nothing =
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
  private[spoon] def unlowered(el: CtElement, what: String, tpe: TypeRepr,
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
  private[spoon] final class BodyTranslator(
      private[spoon] val methodId: SymId, private[spoon] val classId: SymId,
      private[spoon] val anonSelf: SymId = SymId.None, private[spoon] val anonQName: String = ""
  ) extends SpoonTirBodyCore with SpoonTirBodyStmts with SpoonTirBodyExprs:
    private[spoon] val outer: Builder = Builder.this
