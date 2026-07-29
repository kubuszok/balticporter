package balticporter.emit

import balticporter.core.{EngineInfo, Provenance}
import balticporter.tir.*

/** Emission backend: the TRANSFORMED typed TIR → Scala 3 source (RECOMPILER.md step 3,
  * "source pretty-printing"). Because every node carries its resolved `TypeRepr` and every
  * reference a `SymId`, the emitter inserts the right form by construction — it looks names
  * up in the `Program`'s symbol table rather than re-deriving them, so the inference/diamond
  * bugs of the string-printer era cannot occur.
  *
  * First cut: covers the whole node set. A few Java-only control forms have no direct Scala
  * surface and are lowered approximately (marked inline) — `break`/`continue` (need
  * `boundary`), do-while (dropped in Scala 3), and inc/dec used as a value; those are the
  * emitter's known refinement points, not populator gaps.
  */
/** @param externalConcrete
  *   concrete instance members of parents the program does NOT contain — a phase that INJECTS a
  *   supertype as ready Scala (the collection shims) must declare what it brought, keyed by FQN as
  *   `(name, param counts)`. Without it [[diamondOverrides]] sees the injected parent as empty and
  *   misses every conflict against it.
  * @param provenance
  *   attribution for the upstream this port derives from, stamped as a header on every emitted
  *   unit (see [[emitUnit]]). `None` emits no header, which keeps snippet/demo call sites and the
  *   engine's own tests terse — but a real port ALWAYS passes it: the originals this project ports
  *   are licensed (Apache-2.0 so far), and a derived work ships its notice. A green build cannot
  *   report a missing one, so nothing but passing it makes the output compliant.
  */
final class TirEmitter(
    source: Program,
    externalConcrete: Map[String, Set[(String, List[Int])]] = Map.empty,
    provenance: Option[Provenance] = scala.None,
):
  // normalize away Java member-name clashes (a field `x` alongside a method `x()`) before
  // rendering — Scala forbids them; renaming the field symbol propagates to every reference.
  private val prepared =
    TirEmitter.funnelParamRenames(TirEmitter.resolveFieldShadowing(TirEmitter.resolveMemberClashes(source)))
  /** which Java constructor becomes each class's Scala primary, and which `super(args)` can be
    * replayed as statements — whole-program decisions. */
  private val plans = CtorFunnel.Plans(prepared)
  // a replayed parent constructor's statements execute one level down, so the private members
  // they reach must be visible there. Widening only rewrites symbol FLAGS — the trees `plans`
  // was computed over are untouched, so it still applies.
  private val program = TirEmitter.widen(prepared, plans.widenedMembers)

  def emit: String = program.units.map(emitUnit).mkString("\n\n")

  /** types declared in the unit currently being rendered (in scope by simple name). */
  private var currentDeclared: Set[SymId] = Set.empty
  /** the class whose body is being rendered — a constructor's funnel plan is looked up by it. */
  private var currentClass: Option[Tree.ClassDef] = None
  /** simple name of the TOP-LEVEL type being rendered — the qualifier a java `private` needs. */
  private var currentTopLevel: String = ""
  /** the top-level type's symbol, and the class whose body is being rendered right now. A java
    * `private` needs a qualifier only when the two DIFFER, i.e. the member lives in a NESTED class. */
  private var currentTopLevelSym: SymId = SymId.None
  private var currentOwnerSym: SymId    = SymId.None

  def emitUnit(cd: Tree.ClassDef): String =
    currentDeclared = declaredTypes(cd)
    currentTopLevel = esc(sym(cd.symbol).name)
    currentTopLevelSym = cd.symbol
    currentOwnerSym = cd.symbol
    slots.clear(); stmtSeq.clear()
    val body = classDef(cd, 0)
    val full = sym(cd.symbol).fullName
    val pkg  = if full.contains('.') then s"package ${full.substring(0, full.lastIndexOf('.'))}\n\n" else ""
    val text = header(cd) + pkg + body
    if SrcMap.enabled then recordedMap(full) = srcMapOf(full, cd, text)
    text

  /** THIS emitter's source map — never a process-global table. Idempotent per unit: re-emitting a
    * unit replaces its entries, so an emitter run twice does not double the map. The orchestrator
    * writes it (`SrcMap.write`); two emitters in one JVM cannot see each other's. */
  def srcMap: SrcMap.Recording = SrcMap.Recording(recordedMap.values.toList.flatten, recordedMisses.toList)

  private val recordedMap    = collection.mutable.LinkedHashMap.empty[String, List[SrcMap.Entry]]
  private val recordedMisses = collection.mutable.ListBuffer.empty[String]

  // ---------------------------------------------------------------------------
  // SOURCE MAP — member → emitted line range → Java Origin (UNPORTABLE-DESIGN.md §5.2)
  //
  // Emitted Scala had provenance only at the FILE level, so a scalac error or a stack frame could
  // not be attributed to a member, let alone to the Java that produced it. Every TIR node already
  // carries an `Origin` and the emitter knows the text it is writing; the two just never met.
  //
  // Positions are recovered by SEARCH rather than by threading an offset through every rendering
  // function: each class-body member is remembered as the exact string it rendered to, and
  // `srcMapOf` locates those strings in the finished unit. That keeps the instrumentation to one
  // wrapper — `memberStat` — instead of a cursor parameter on ~40 methods, and it cannot change a
  // byte of output, which is the property that lets the map be added without re-measuring the port.
  //
  // The search is sound because slots are reserved PRE-ORDER (a slot is appended before its member
  // renders, so a nested class precedes its own members) and the cursor only ever moves forward by
  // one character past a match. A nested member is therefore still findable inside its owner, while
  // two textually identical siblings resolve to two different positions.
  // ---------------------------------------------------------------------------

  private final class Slot(val member: String, val kind: String, val origin: Origin):
    var text: String = ""
  private val slots   = collection.mutable.ArrayBuffer.empty[Slot]
  private val stmtSeq = collection.mutable.Map.empty[String, Int]

  /** [[stat]] for a member of a CLASS BODY, remembering what it rendered to. Identical to `stat`
    * in every observable way, and not even called when the map is off. */
  private def memberStat(s: Statement, i: Int): String =
    if !SrcMap.enabled then stat(s, i)
    else
      val slot = new Slot(memberKey(s), memberKind(s), s.origin)
      slots += slot
      val t = stat(s, i)
      slot.text = t
      t

  /** A member's stable identity. `owner#name` for anything that has a symbol — the form the rest
    * of this engine already uses (`Substitutions.dropMethods`, `RewriteTrace`) — with the
    * parameter types appended for a `def`, because Java overloading routinely puts eight `encode`s
    * in one class and a key that merges them cannot say which one changed.
    *
    * A class-body statement with NO symbol (a `@Test` body that a phase lowered to
    * `test("…"){ … }`, an initialiser) gets an ordinal within its owner. Ordinals shift when a
    * sibling is added, which over-reports change and never under-reports it; the statement's own
    * `Origin` — recorded beside it — is the part that actually locates the Java. */
  private def memberKey(s: Statement): String =
    val owner = classStack.lastOption.map(x => sym(x).fullName).getOrElse("?")
    s match
      case d: Tree.DefDef if !isInitBlock(d) =>
        s"${sym(d.symbol).fullName}(${d.paramss.flatten.map(v => shortTpe(v.tpt.tpe)).mkString(",")})"
      case d: Definition => sym(d.symbol).fullName
      case _ =>
        val n = stmtSeq.getOrElse(owner, 0) + 1
        stmtSeq(owner) = n
        s"$owner#<stmt$n>"

  private def memberKind(s: Statement): String = s match
    case _: Tree.ClassDef => "class"
    case d: Tree.DefDef   =>
      if isInitBlock(d) then "init" else if sym(d.symbol).name == "<init>" then "ctor" else "def"
    case _: Tree.ValDef  => "val"
    case _: Tree.TypeDef => "type"
    case _               => "stmt"

  /** simple, structural rendering of a parameter type — enough to tell two overloads apart without
    * dragging fully-qualified names (and their churn) into a key. */
  private def shortTpe(t: TypeRepr): String = t match
    case TypeRepr.AppliedType(tc, as) if as.nonEmpty => shortTpe(tc) + as.map(shortTpe).mkString("<", ",", ">")
    case _                                           => headSymOf(t).map(x => sym(x).name).getOrElse("?")

  /** Locate every remembered member in the finished unit text. The unit itself is always entry
    * one, spanning the whole file: a line that falls between members (a brace, a blank line, the
    * package clause) then still resolves to the right Java FILE instead of to nothing. */
  private def srcMapOf(unit: String, cd: Tree.ClassDef, text: String): List[SrcMap.Entry] =
    val root   = SrcMap.sourceRootOf(unit, cd.origin.javaPath)
    val starts = collection.mutable.ArrayBuffer(0)
    var k      = text.indexOf('\n')
    while k >= 0 do { starts += k + 1; k = text.indexOf('\n', k + 1) }
    val ls = starts.toArray
    def lineOf(off: Int): Int =
      var lo = 0; var hi = ls.length - 1
      while lo < hi do { val mid = (lo + hi + 1) / 2; if ls(mid) <= off then lo = mid else hi = mid - 1 }
      lo + 1
    val out = collection.mutable.ListBuffer(
      SrcMap.Entry(unit, unit, "class", 1, lineOf(math.max(0, text.length - 1)),
                   SrcMap.relativise(cd.origin.javaPath, root), cd.origin.line,
                   TirPrinter.sha256(text).take(16)))
    var cursor = 0
    slots.foreach { s =>
      if s.text.nonEmpty then
        val at = text.indexOf(s.text, cursor)
        // A member that cannot be found in the finished text is a hole in the map, and a map with
        // silent holes attributes an error to the wrong member. Counted and printed (SrcMap.write),
        // never swallowed — CLAUDE.md §3: the check arrives with the translation.
        if at < 0 then recordedMisses += s"$unit#${s.member}"
        else
          cursor = at + 1
          val st = lineOf(at)
          out += SrcMap.Entry(unit, s.member, s.kind, st, st + s.text.count(_ == '\n'),
                              SrcMap.relativise(s.origin.javaPath, root), s.origin.line,
                              TirPrinter.sha256(s.text).take(16))
    }
    out.toList

  /** The attribution + do-not-edit banner, in the same shape the BIR printer
    * ([[balticporter.emit.ScalaPrinter.header]]) has always emitted — one header, so a port that
    * still runs both backends produces one kind of file. Empty when no [[Provenance]] was given.
    *
    * The "Ported from" line names the ORIGINAL JAVA FILE for THIS unit, taken from the unit's own
    * `Origin` rather than reconstructed from its package: a nested or renamed type does not live at
    * the path its FQN suggests, and after [[balticporter.transform.PackageRenameTransform]] the FQN
    * is not the upstream one at all — attribution has to point at the upstream file, which is
    * exactly what `Origin` records and nothing else does. */
  private def header(cd: Tree.ClassDef): String = provenance match
    case scala.None => ""
    case Some(p) =>
      s"""|/*
          | * Generated by Baltic Porter ${EngineInfo.version} — DO NOT EDIT; regenerate instead.
          | *
          | * Ported from: ${sourcePathOf(cd.origin, p)}
          | * Original license: ${p.originalLicense} (see ${p.upstreamName} upstream)
          | * upstream-commit: ${p.upstreamCommit}
          | */
          |""".stripMargin

  /** The unit's Java source path as it should READ in a header: repo-relative where we can make it
    * so, honest where we cannot.
    *
    * `Origin.javaPath` is whatever absolute path the parser saw, so it is machine-local; three
    * outcomes, in order:
    *   1. relative to `Provenance.sourceRoot` — the reproducible answer, and what a real port sets;
    *   2. failing that, cut at `sourcePathPrefix` if the path contains it — the same answer without
    *      the configuration, when the prefix happens to be a real path segment;
    *   3. failing both, the path AS RECORDED — and flagged when it is ABSOLUTE, since only then is
    *      it machine-local. A wrong-but-plausible path is worse than a visibly unconfigured one:
    *      this is attribution, and the point of the line is that someone can find the original.
    *      Synthetic/unknown origins say so outright rather than naming a file that was never read.
    *
    * Case 3 is a §1(b) diagnostic — configure `Provenance.sourceRoot` — not an engine defect. */
  private def sourcePathOf(o: Origin, p: Provenance): String =
    val raw = o.javaPath
    if raw.isEmpty || raw == "<synthetic>" || raw == "<unknown>" then
      "<unknown — the frontend recorded no source origin for this unit>"
    else
      val root   = p.sourceRoot.stripSuffix("/")
      val marker = p.sourcePathPrefix.stripSuffix("/")
      val rel =
        if root.nonEmpty && raw.startsWith(root) then Some(raw.substring(root.length).stripPrefix("/"))
        else if marker.nonEmpty && raw.contains(marker + "/") then
          Some(raw.substring(raw.indexOf(marker + "/") + marker.length + 1))
        else scala.None
      rel match
        case Some(r) if marker.nonEmpty                       => s"$marker/$r"
        case Some(r)                                          => r
        case scala.None if new java.io.File(raw).isAbsolute() =>
          s"$raw  (path as recorded — set Provenance.sourceRoot to relativise it)"
        case scala.None => raw // already relative: reproducible as it stands

  /** every type symbol that appears as a parent (extends/mixin) anywhere in the program — an
    * all-static class in this set must stay a `class`, since an `object` can't be extended. */
  private lazy val extendedTypes: Set[SymId] =
    val acc = collection.mutable.Set[SymId]()
    def headSym(t: TypeRepr): Option[SymId] = t match
      case TypeRepr.TypeRef(_, s)      => Some(s)
      case TypeRepr.AppliedType(tc, _) => headSym(tc)
      case _                           => None
    def scan(cd: Tree.ClassDef): Unit =
      cd.parents.foreach {
        case tt: TypeTree => headSym(tt.tpe).foreach(acc += _)
        case term: Term   => headSym(term.tpe).foreach(acc += _)
      }
      cd.body.foreach { case c: Tree.ClassDef => scan(c); case _ => () }
    program.units.foreach(scan)
    acc.toSet

  /** every type symbol the program INSTANTIATES — an all-static class in this set must stay a
    * `class`, for the same reason as [[extendedTypes]]: you cannot `new` an object.
    *
    * The case that needed it is an EMPTY Java class. `private static class Dummy { }` has no
    * members, so "every member is static" is VACUOUSLY true and the collapse fired; the
    * `cd.body.nonEmpty` guard did not stop it, because the TIR carries a synthesised default
    * constructor and the body is therefore not empty. It emitted as `object Dummy`, and every
    * `new Dummy()` and every `Signal<Dummy>` in Ashley's suite stopped compiling — 26 errors from
    * one empty class.
    *
    * Walks with the STANDARD traversal rather than a private recursion (CLAUDE.md §3): a `new` can
    * appear anywhere a term can, including inside an anonymous class body, and a hand-rolled walk
    * here would find the ones its author remembered. */
  private lazy val instantiatedTypes: Set[SymId] =
    val acc = collection.mutable.Set[SymId]()
    def headSym(t: TypeRepr): Option[SymId] = t match
      case TypeRepr.TypeRef(_, s)      => Some(s)
      case TypeRepr.AppliedType(tc, _) => headSym(tc)
      case _                           => None
    val collect = new Phase:
      def name: String = "emit/instantiated-types"
      override def transformNew(t: Tree.New)(using Program): Term =
        headSym(t.tpt.tpe).foreach(acc += _)
        t
    given Program = program
    program.units.foreach(u => StandardTraversal.mapClassDef(collect, u))
    acc.toSet

  private def declaredTypes(cd: Tree.ClassDef): Set[SymId] =
    val acc = collection.mutable.Set[SymId](cd.symbol)
    cd.body.foreach { case c: Tree.ClassDef => acc ++= declaredTypes(c); case _ => () }
    acc.toSet

  /** head symbols of a class's parent types (extends + mixins). */
  private def parentSymsOf(cd: Tree.ClassDef): List[SymId] =
    def headSym(t: TypeRepr): Option[SymId] = t match
      case TypeRepr.TypeRef(_, s) => Some(s)
      case TypeRepr.AppliedType(tc, _) => headSym(tc)
      case _ => None
    cd.parents.flatMap { case tt: TypeTree => headSym(tt.tpe); case term: Term => headSym(term.tpe) }

  /** our-own types that have at least one `static` member (so a companion `object` holds it). */
  private lazy val typesWithStatics: Set[SymId] =
    val acc = collection.mutable.Set[SymId]()
    def scan(cd: Tree.ClassDef): Unit =
      if cd.body.exists { case d: Definition => sym(d.symbol).flags.isStatic; case _ => false } then acc += cd.symbol
      cd.body.foreach { case c: Tree.ClassDef => scan(c); case _ => () }
    program.units.foreach(scan)
    acc.toSet

  /** each type → the names of the `static` members it DECLARES itself. */
  private lazy val ownStaticsBySym: Map[SymId, Set[String]] =
    val m = collection.mutable.Map[SymId, Set[String]]()
    def scan(cd: Tree.ClassDef): Unit =
      m(cd.symbol) = cd.body.collect { case d: Definition if sym(d.symbol).flags.isStatic => esc(sym(d.symbol).name) }.toSet
      cd.body.foreach { case c: Tree.ClassDef => scan(c); case _ => () }
    program.units.foreach(scan); m.toMap

  /** every static name a companion re-export of `s` delivers, mapped to the type that DECLARES it —
    * `s`'s own statics, then its ancestors' (nearest declaration wins, as in Java). The owner is
    * what makes two exports comparable: the same name from the same declaring type is the same
    * constant arriving twice (a diamond — `GL30Interceptor extends GLInterceptor with GL30`, where
    * `GLInterceptor` implements `GL20` and `GL30` extends it), which Scala rejects as a duplicate
    * definition; the same name from DIFFERENT types is a real redeclaration and must not be merged. */
  private def staticOwnersOf(s: SymId, seen: Set[SymId] = Set.empty): Map[String, SymId] =
    if seen(s) then Map.empty
    else
      val inherited = parentsBySym.getOrElse(s, Nil)
        .foldLeft(Map.empty[String, SymId])((acc, p) => staticOwnersOf(p, seen + s) ++ acc)
      inherited ++ ownStaticsBySym.getOrElse(s, Set.empty).map(_ -> s).toMap

  /** each type → its parent symbols (whole program). */
  private lazy val parentsBySym: Map[SymId, List[SymId]] =
    val m = collection.mutable.Map[SymId, List[SymId]]()
    def scan(cd: Tree.ClassDef): Unit =
      m(cd.symbol) = parentSymsOf(cd); cd.body.foreach { case c: Tree.ClassDef => scan(c); case _ => () }
    program.units.foreach(scan); m.toMap

  /** does this type OR any ancestor have static members? (so its companion carries or re-exports
    * them — the export chain must pass THROUGH intermediates that add no statics of their own). */
  private def staticsReachable(s: SymId, seen: Set[SymId] = Set.empty): Boolean =
    !seen(s) && (typesWithStatics(s) || parentsBySym.getOrElse(s, Nil).exists(p => staticsReachable(p, seen + s)))

  /** every strict ancestor of `s`. */
  private def ancestorsOf(s: SymId, seen: Set[SymId] = Set.empty): Set[SymId] =
    parentsBySym.getOrElse(s, Nil).filterNot(seen).foldLeft(Set.empty[SymId]) { (acc, p) =>
      acc + p ++ ancestorsOf(p, seen + s + p)
    }

  // ---- names ----
  private def sym(id: SymId): Symbol = program.symbolOf(id).getOrElse(Symbol(id, "?", "?", Flags(), SymId.None, TypeRepr.NoType))
  private def local(id: SymId): String = esc(sym(id).name)

  /** a method symbol's declared parameter types, empty when its info is not a method type — used to
    * give an unbound method reference the arity java gave it. */
  private def methodParams(id: SymId): List[TypeRepr] = sym(id).info match
    case TypeRepr.MethodType(ps, _, _)                       => ps.map(_._2)
    case TypeRepr.PolyType(_, TypeRepr.MethodType(ps, _, _)) => ps.map(_._2)
    case _                                                   => Nil

  private val keywords = Set(
    "type", "object", "val", "var", "def", "class", "trait", "enum", "given", "match", "case",
    "if", "else", "while", "do", "for", "yield", "then", "with", "extends", "new", "this", "super",
    "null", "true", "false", "import", "package", "override", "final", "abstract", "sealed", "private",
    "protected", "implicit", "lazy", "return", "throw", "try", "catch", "finally", "forSome", "using",
    "export", "inline", "opaque", "transparent", "derives", "extension", "macro", "end", "as", "wait",
  )
  /** backtick an identifier that collides with a Scala keyword. */
  private def esc(name: String): String = if keywords(name) then s"`$name`" else name
  /** a TYPE symbol's rendered name. FULLY QUALIFIED by default — for the structural Java→Scala
    * phase we emit fully-qualified references and generate NO imports, which deletes the entire
    * import-decision bug class (import-vs-projection, shadowing, static-receiver qualification):
    * a reference is now a context-free function of the symbol's owner chain. Only two things
    * stay unqualified: type params, and a type declared in THIS unit (in scope by simple name).
    * Human-readable imports are a separate, optional beautification backend, not a correctness
    * prerequisite. (A later refinement handles givens/extensions, which FQN genuinely can't name.) */
  private def typeSym(id: SymId): String =
    val s = sym(id)
    if tparamSubst.contains(id) then tpe(tparamSubst(id)) // ctor type param → its bound
    else if s.flags.isParam then esc(s.name)
    // a Java `static` nested class is lowered into the enclosing type's companion `object`, so it
    // is named through the value path `Outer.Inner` — NOT by simple name (companion members aren't
    // in the class's scope) and NOT `Outer#Inner` (a type projection can't reach a companion member).
    else if s.flags.isStatic && s.fullName.contains('$') then nestedPath(id)
    // a Java INNER (non-static) class is a PATH-dependent type in Scala: named by simple name inside
    // the enclosing class it means `this.Inner`, so the same Java type reached through two different
    // instances (`pa.Channel` vs `ParallelArray#Channel` from another file) never unifies, and a
    // method bounded `<T extends Channel>` cannot accept an initializer written against the outer
    // view. Name it by PROJECTION everywhere instead — one type for all instances. `extends` and
    // `new` need an instantiable/stable name, so those two positions opt out (see `namedInner`).
    else if program.definitionOf(id).isDefined && currentDeclared(id) then
      if namedInner || !isInnerClass(id) then esc(s.name) // declared here — in scope
      else nestedPath(id)
    else if program.symbolOf(s.owner).exists(_.flags.isModule) then s"${typeValue(s.owner)}.${esc(s.name)}" // object's type member → path-dependent `O.T`
    // An inner class of an ANCESTOR is an INHERITED member type, in scope by its simple name
    // anywhere inside the subclass — `class TextArea extends TextField` sees
    // `TextFieldClickListener` exactly as Java did. The projection is not merely verbose here, it
    // is illegal: `TextField#TextFieldClickListener` needs `TextField` to be an immutable path.
    else if inheritedNested(s.owner) then esc(s.name)
    else nestedPath(id)                                             // non-static inner class elsewhere → `Outer#Inner`

  /** is `owner` an ancestor of some class we are currently rendering inside? */
  private def inheritedNested(owner: SymId): Boolean =
    owner != SymId.None && classStack.exists(c => c != owner && ancestorsOf(c).contains(owner))

  /** the path to a NESTED type, choosing a separator PER LEVEL: `.` where that level is a Java
    * `static` nested class (lowered into the enclosing companion `object`, so reachable only through
    * the value path) and `#` where it is a genuine inner class (a type projection). A blanket
    * `fullName.replace('$', '#')` gets a MIXED chain wrong — `ModelInfluencer.Random` is static and
    * holds the inner `ModelInstancePool`, so the type is `ModelInfluencer.Random#ModelInstancePool`
    * while `ModelInfluencer#Random#ModelInstancePool` names nothing at all. Falls back to the
    * blanket form whenever an owner symbol is unknown, so this can only ever add precision. */
  private def nestedPath(id: SymId): String =
    def go(x: SymId): Option[String] =
      val sx = sym(x)
      if !sx.fullName.contains('$') then Some(sx.fullName)
      else if sx.owner == SymId.None || program.symbolOf(sx.owner).isEmpty then None
      else go(sx.owner).map(p => p + (if sx.flags.isStatic then "." else "#") + esc(sx.name))
    // The fallback fires exactly when an owner is UNKNOWN, which for a type we do not define means
    // an external/JDK one. Name those with `.`: a Java nested type is reached as `Outer.Inner` in
    // Scala, and a `#` projection is not even available — it needs the prefix to be an immutable
    // path, which a bare external class name is not (`java.nio.channels.FileChannel#MapMode`).
    go(id).getOrElse:
      val sep = if program.definitionOf(id).isEmpty then '.' else '#'
      sym(id).fullName.replace('$', sep)

  /** a NON-static nested class of one of our own NON-GENERIC classes (not of a companion `object`).
    * A generic enclosing class is excluded: `Octree#OctreeNode` is not a legal projection — the
    * prefix would need type arguments, which the reference does not carry. */
  private def isInnerClass(id: SymId): Boolean =
    val s = sym(id)
    !s.flags.isStatic && s.owner != SymId.None && s.fullName.contains('$') &&
      !program.symbolOf(s.owner).exists(_.flags.isModule) &&
      program.definitionOf(s.owner).exists { case c: Tree.ClassDef => c.tparams.isEmpty; case _ => false }

  /** inside an `extends` clause or a `new`, where a type projection is not legal — render inner
    * classes by their simple (in-scope) name there. */
  private var namedInner = false
  private def byName[A](f: => String): String =
    val prev = namedInner; namedInner = true
    try f finally namedInner = prev

  private def ind(n: Int): String = "  " * n

  // ---- definitions ----
  /** the classes currently being rendered, outermost first. Lets a `Tree.This` naming an ENCLOSING
    * class render Java's qualified `Outer.this` rather than a bare `this` (which names the inner one). */
  private val classStack = collection.mutable.ArrayDeque[SymId]()

  private def classDef(cd: Tree.ClassDef, i: Int): String =
    val outer = currentClass
    currentClass = Some(cd)
    classStack.append(cd.symbol)
    try classDef0(cd, i) finally { classStack.removeLast(); currentClass = outer }

  /** Parameter symbol -> the type it must be RENDERED at, because the member overrides one
    * inherited through a RAW parent.
    *
    * Java sees a raw supertype's members ERASED: `class ParticleController implements
    * ResourceData.Configurable` (raw) implements `save(AssetManager, ResourceData)` at the
    * erasure, not at any instantiation. Our raw fill independently rendered the parameter
    * `ResourceData[?]`, while the parent — which cannot keep a wildcard, `extends
    * Configurable[?]` being illegal Scala — was de-wildcarded to `Configurable[Object]`. Two
    * renderings of one raw type in one class, and the override implements neither.
    *
    * Both now come from ONE answer: [[deWildcardedArgs]] decides the parent's arguments, and the
    * same substitution is applied to the parent's declared parameter types to give this class's
    * overriding parameters. Agreement is by construction rather than by two rules happening to
    * coincide — which is what the earlier name-directed inherited-instantiation rule could not
    * promise, and why it was reverted.
    *
    * Only slots where OUR rendering is a wildcard are touched, so an override that already agrees
    * is left exactly as it is. */
  private def rawParentAlignment: Map[SymId, TypeRepr] =
    val out    = collection.mutable.Map[SymId, TypeRepr]()
    val done   = collection.mutable.Set[SymId]()
    val declOf = collection.mutable.Map[SymId, Tree.ClassDef]()
    def index(cd: Tree.ClassDef): Unit =
      declOf(cd.symbol) = cd
      cd.body.foreach { case c: Tree.ClassDef => index(c); case _ => () }
      cd.enumCases.foreach(_.body.foreach { case c: Tree.ClassDef => index(c); case _ => () })
    program.units.foreach(index)
    def methodsOf(cd: Tree.ClassDef) = cd.body.collect {
      case d: Tree.DefDef if sym(d.symbol).name != "<init>" => d
    }
    /** parents first, so a grandchild aligns against its parent's ALREADY-aligned view. */
    def visit(cd: Tree.ClassDef, seen: Set[SymId]): Unit =
      if !done(cd.symbol) && !seen(cd.symbol) then
        done += cd.symbol
        val ours = methodsOf(cd)
        for
          p    <- cd.parents
          pt    = p match { case tt: TypeTree => tt.tpe; case t: Term => t.tpe }
          tycon <- headSymOf(pt)
          pcd  <- declOf.get(tycon)
        do
          visit(pcd, seen + cd.symbol)
          val subst = pt match
            case TypeRepr.AppliedType(tc, args)
                if args.exists { case _: TypeRepr.TypeBounds => true; case _ => false } &&
                   pcd.tparams.sizeIs == args.size =>
              pcd.tparams.map(_.symbol).zip(deWildcardedArgs(tc, args))
                .collect { case (s, Some(t)) => s -> t }.toMap
            case _ => Map.empty[SymId, TypeRepr]
          // Search the parent CHAIN, not just the direct parent's own body: `RegionInfluencer
          // extends Influencer extends ParticleControllerComponent implements Configurable` — only
          // the last declares `load`, and `Influencer` in between declares nothing at all.
          def findUp(c: Tree.ClassDef, name: String, ar: List[Int], seen: Set[SymId]): Option[Tree.DefDef] =
            if seen(c.symbol) then scala.None
            else methodsOf(c).find(d => sym(d.symbol).name == name && d.paramss.map(_.size) == ar)
              .orElse(c.parents.iterator.map { case tt: TypeTree => tt.tpe; case x: Term => x.tpe }
                .flatMap(t => headSymOf(t).flatMap(declOf.get))
                .flatMap(pp => findUp(pp, name, ar, seen + c.symbol)).nextOption())
          for
            od <- ours
            pd <- findUp(pcd, sym(od.symbol).name, od.paramss.map(_.size), Set.empty)
            (ops, pps) <- od.paramss.zip(pd.paramss)
            (op, pp)   <- ops.zip(pps)
            if hasWildcardArg(op.tpt.tpe) && !out.contains(op.symbol)
          do
            val aligned = substTp(out.getOrElse(pp.symbol, pp.tpt.tpe), subst)
            // The parent member is matched by NAME AND ARITY, which is all java overriding needs
            // — and far too little on its own. `Environment extends Attributes` inherits
            // `remove(long mask)`, which matches `Environment.remove(BaseLight)` on both counts;
            // aligning to it rendered three overloads as `remove(Long)`. An alignment is only ever
            // the SAME type at different arguments, so require the head constructor to agree.
            if !hasWildcardArg(aligned) && headSymOf(aligned) == headSymOf(op.tpt.tpe) then
              out(op.symbol) = aligned
    program.units.foreach(u => declOf.values.foreach(visit(_, Set.empty)))
    out.toMap

  private lazy val overrideAlign: Map[SymId, TypeRepr] = rawParentAlignment

  /** An ARGUMENT reaching a parameter [[rawParentAlignment]] re-rendered. Java accepted the call
    * because the callee's formal was RAW there (`ParticleEffect.save(AssetManager, ResourceData)`
    * taking a `ResourceData<ParticleEffect>`); once the formal reads `ResourceData[Object]` the
    * conversion java made silently has to be written. Only fires where the argument disagrees. */
  private def alignedArgs(m: SymId, args: List[Term], i: Int): Option[List[String]] =
    val ps = program.definitionOf(m).collect { case d: Tree.DefDef => d.paramss.flatten }.getOrElse(Nil)
    if ps.sizeIs != args.size || !ps.exists(v => overrideAlign.contains(v.symbol)) then scala.None
    else Some(args.zip(ps).map { (a, v) =>
      overrideAlign.get(v.symbol).filter(_ != a.tpe) match
        case Some(t) => s"${operand(a, i)}.asInstanceOf[${tpe(t)}]"
        case None    => term(a, i)
    })

  /** A cast onto a parameter that [[rawParentAlignment]] re-rendered must land on the type the
    * parameter now HAS. The frontend built these casts against the raw fill's `ResourceData[?]`;
    * once the declaration reads `ResourceData[Object]` the same cast narrows to a wildcard the
    * callee will not take — and a cast to `T[?]` asserts nothing in the first place, so following
    * the alignment loses nothing. Only wildcarded targets on an aligned symbol are touched. */
  private def castTarget(e: Term, target: TypeRepr): TypeRepr =
    if !hasWildcardArg(target) then target
    else
      val s = e match
        case Tree.Ident(x, _, _)     => Some(x)
        case Tree.Select(_, x, _, _) => Some(x)
        case _                       => scala.None
      s.flatMap(overrideAlign.get).getOrElse(target)

  /** `this` in Scala always names the INNERMOST class, where Java's `Outer.this` names an enclosing
    * one. Qualify by simple name when the symbol is an enclosing class actually being rendered
    * around this point; anything else (an inherited/unknown owner) keeps the bare `this`.
    *
    * A SUPERTYPE is never qualified even when it also encloses: libGDX nests subclasses inside their
    * own base (`DynamicsModifier.FaceDirection extends DynamicsModifier`), and constructor replay
    * moves the base's `this` statements into the subclass body — there the bare `this` is exactly
    * right, while `DynamicsModifier.this` would name the companion object. */
  private def thisRef(s: SymId): String =
    val inner = classStack.lastOption
    if inner.contains(s) || !classStack.contains(s) || inner.exists(inheritsFrom(_, s)) then "this"
    else s"${esc(sym(s).name)}.this"

  /** is `child` `anc`, or a (transitive) subtype of it, among our own definitions? */
  private def inheritsFrom(child: SymId, anc: SymId): Boolean =
    val seen = collection.mutable.Set[SymId]()
    def go(c: SymId): Boolean =
      c == anc || (seen.add(c) && program.definitionOf(c).collect { case cd: Tree.ClassDef =>
        parentSymsOf(cd).exists(go)
      }.getOrElse(false))
    go(child)

  private def classDef0(cd: Tree.ClassDef, i: Int): String =
    if sym(cd.symbol).flags.isEnum then return enumDef(cd, i)
    val savedOwner = currentOwnerSym
    currentOwnerSym = cd.symbol
    try classDef1(cd, i) finally currentOwnerSym = savedOwner

  private def classDef1(cd: Tree.ClassDef, i: Int): String =
    val s  = sym(cd.symbol)
    val kw =
      if s.flags.isModule then "object"
      else if s.flags.isTrait then "trait"
      else "class"
    val tps     = if cd.tparams.isEmpty then "" else "[" + cd.tparams.map(typeParam).mkString(", ") + "]"
    // lower Java constructors: `CtorFunnel` nominates one to become Scala's PRIMARY. Its body is
    // inlined (those statements run at construction), its `super(args)` moves into the `extends`
    // clause (which also fixes parents that need constructor arguments), and its PARAMETERS become
    // the class's parameters. Every other constructor stays a `def this(...)` delegating to it.
    val plan    = if s.flags.isModule then CtorFunnel.Plan.none else plans(cd)
    val (loweredBody, superArgs) = (lowerCtors(cd.body, plan), plan.superArgs)
    val pparams = plan.primaryParams
    val prim    = if pparams.isEmpty then "" else s"(${pparams.map(param).mkString(", ")})"
    val superTpe = cd.parents.headOption.map { case tt: TypeTree => tt.tpe; case t: Term => t.tpe }
    val parents = cd.parents.map(parent).filter(_.nonEmpty) match
      case Nil                          => Nil
      case h :: t if superArgs.nonEmpty =>
        val as = superArgs.zipWithIndex.map((a, n) => superArg(superTpe.getOrElse(TypeRepr.NoType), a, n, i))
        s"$h(${as.mkString(", ")})" :: t
      case all                          => all
    val ext     = if parents.isEmpty then "" else " extends " + parents.mkString(" with ")
    // an all-static utility class (no instance state, no supertype) is just an `object` — so its
    // static members and nested types live together and see each other by simple name.
    val hasInstanceState = cd.body.exists {
      case d: Tree.DefDef => sym(d.symbol).name != "<init>" && !sym(d.symbol).flags.isStatic
      case v: Tree.ValDef => !sym(v.symbol).flags.isStatic
      case _              => false
    }
    // an all-static class can only collapse to an `object` if nobody EXTENDS it (you can't extend
    // an object) — otherwise it stays a `class` with its statics in a companion object.
    if kw == "class" && parents.isEmpty && cd.body.nonEmpty && !hasInstanceState && pparams.isEmpty &&
       !extendedTypes(cd.symbol) && !instantiatedTypes(cd.symbol) then
      val members = cd.body.filterNot { case d: Tree.DefDef => sym(d.symbol).name == "<init>"; case _ => false }
      val ob = orderBody(members, pparams.nonEmpty).map(memberStat(_, i + 1)).filter(_.nonEmpty).mkString("\n")
      return s"${ind(i)}object ${esc(s.name)}$tps {\n$ob\n${ind(i)}}"
    // Java statics have no instance home in Scala — they move to the companion object.
    val (statics, instance) = if s.flags.isModule then (Nil, loweredBody) else loweredBody.partition(isStatic)
    val self    = cd.selfType.map(st => s"${ind(i + 1)}self: ${tpe(st.tpe)} =>\n").getOrElse("")
    val body0   = joinStats(orderBody(instance, pparams.nonEmpty).map(memberStat(_, i + 1)).filter(_.nonEmpty))
    val diamonds = diamondOverrides(cd, i + 1)
    val body    = if diamonds.isEmpty then body0 else joinStats(List(body0).filter(_.nonEmpty) ++ diamonds)
    val open    = if body.isEmpty && self.isEmpty then "" else s" {\n$self$body\n${ind(i)}}"
    val abs     = if kw == "class" && s.flags.isAbstract then "abstract " else ""
    // Scala (unlike Java) forbids a NON-private member from referring to a `private` type in its
    // signature — a public `Values extends MapIterator` / field `pool: ModelInstancePool` where the
    // referent is private is an error. Java nested classes leak this way constantly; drop the class's
    // `private` (visibility-widening is always compile-safe) so those references type-check.
    // A Java `@interface` is an ANNOTATION TYPE. Emitted as an ordinary interface it becomes a
    // trait, and then nothing can be annotated with it — 161 errors' worth of `@Null` in this
    // corpus alone. Scala's equivalent is a class extending `StaticAnnotation`.
    val cls     =
      if s.flags.isAnnotation then
        s"${annots(s, i)}${ind(i)}class ${esc(s.name)}$tps$prim extends scala.annotation.StaticAnnotation"
      else s"${annots(s, i)}${ind(i)}${mods(s.flags.copy(isPrivate = false))}$abs$kw ${esc(s.name)}$tps$prim$ext$open"
    // Java interface/parent CONSTANTS are `static`, so they live in the parent's companion object
    // — which Scala does NOT inherit. Re-export each static-bearing parent's companion so an
    // inherited constant accessed via a subclass (`GL30.GL_LUMINANCE`, declared in `GL20`) resolves.
    // exclude the class's OWN static names from the re-export (a subtype may redeclare a parent
    // constant — OpenGL's GL31 vs GL30 — which would otherwise be a duplicate/conflicting export).
    val ownStaticNames = statics.collect { case d: Definition => esc(sym(d.symbol).name) }.distinct
    // Two exports must not both deliver the same name. `GL20Interceptor extends GLInterceptor with
    // GL20` and `GLInterceptor` itself implements `GL20`, so `GLInterceptor`'s companion ALREADY
    // re-exports `GL20`'s constants by this rule — a second `export GL20.*` is a duplicate
    // definition, not extra reach. Drop a parent another exported parent wholly subsumes, and for
    // the DIAMOND that remains (`GLInterceptor` and `GL30` meeting at `GL20`) exclude, from each
    // later export, every name an earlier one already delivered FROM THE SAME DECLARING TYPE. The
    // same-owner test is what keeps this safe: a genuine redeclaration (`GL31` shadowing a `GL30`
    // constant) has a different owner, so it is never silently merged away.
    val exported       = parentSymsOf(cd).filter(p => staticsReachable(p))
    val kept           = exported.filterNot(p => exported.exists(q => q != p && ancestorsOf(q).contains(p)))
    val delivered      = kept.map(staticOwnersOf(_))
    val extraExcl      = Array.fill(kept.size)(Set.empty[String])
    delivered.flatMap(_.keys).distinct.foreach { n =>
      val at = delivered.indices.filter(j => delivered(j).contains(n)).toList
      if at.sizeIs > 1 then
        val owners = at.map(delivered(_)(n)).distinct
        // Same owner everywhere ⇒ the SAME constant arriving twice; keep the first export and drop
        // the rest. Different owners ⇒ a real redeclaration, and the one Java resolves to is the
        // most specific — the owner that descends from all the others. Incomparable owners are
        // ambiguous in Java too, so keep the first and let the redeclaration be the loser rather
        // than guess.
        val winner = at.find(j => owners.forall(o => o == delivered(j)(n) || ancestorsOf(delivered(j)(n)).contains(o)))
          .getOrElse(at.head)
        at.filter(_ != winner).foreach(j => extraExcl(j) = extraExcl(j) + n)
    }
    val parentExports  = kept.zipWithIndex.map { (p, j) =>
      val excluded = (ownStaticNames.toSet ++ extraExcl(j)).toList.sorted
      val sel      = if excluded.isEmpty then "*" else s"{${excluded.map(_ + " => _").mkString(", ")}, *}"
      s"${ind(i + 1)}export ${typeValue(p)}.$sel"
    }
    if statics.isEmpty && parentExports.isEmpty then cls
    else
      val sb = (parentExports ++ orderBody(statics).map(memberStat(_, i + 1)).filter(_.nonEmpty)).mkString("\n")
      s"$cls\n${ind(i)}object ${esc(s.name)} {\n$sb\n${ind(i)}}"

  /** Java enum → `sealed abstract class Name <parents-minus-Enum> { members }` plus a
    * companion `object` holding each constant as a `case object` and a `values` array. */
  private def enumDef(cd: Tree.ClassDef, i: Int): String =
    val s       = sym(cd.symbol)
    val name    = esc(s.name)
    val parents = cd.parents.map(parent).filter(p => p.nonEmpty && !p.startsWith("java.lang.Enum"))
    val ext     = if parents.isEmpty then "" else " extends " + parents.mkString(" with ")
    val (statics, instance0) = cd.body.partition(isStatic)
    // A Java enum constructor's PARAMS become the sealed class's primary constructor params (as `var`
    // fields), so `case object Nearest extends TextureFilter(GL_NEAREST)` has somewhere to pass its
    // arg. Drop the constructor itself and any field that a param supersedes (same name).
    val ctorParams = instance0.collectFirst { case d: Tree.DefDef if sym(d.symbol).name == "<init>" => d.paramss.flatten }.getOrElse(Nil)
    val paramNames = ctorParams.map(v => sym(v.symbol).name).toSet
    val instance   = instance0.filterNot {
      case d: Tree.DefDef => sym(d.symbol).name == "<init>"
      case v: Tree.ValDef => paramNames(sym(v.symbol).name)
      case _              => false
    }
    val eprimary = if ctorParams.isEmpty then "" else s"(${ctorParams.map(v => s"var ${esc(sym(v.symbol).name)}: ${tpe(v.tpt.tpe)}").mkString(", ")})"
    // Java's final `Enum.name()` — a `case object`'s `toString` IS its declared name (= the Java
    // constant name), so `name()` returns it. Skip if the enum already declares a `name` member.
    val hasName = instance.exists { case d: Definition => sym(d.symbol).name == "name"; case _ => false }
    val nameM   = if hasName then Nil else List(s"${ind(i + 1)}def name(): java.lang.String = this.toString()")
    val members = orderBody(instance).map(memberStat(_, i + 1)).filter(_.nonEmpty) ++ nameM
    val cbody   = members.mkString("\n")
    val cls     = s"${ind(i)}sealed abstract class $name$eprimary$ext" + (if cbody.isEmpty then "" else s" {\n$cbody\n${ind(i)}}")
    val cases = cd.enumCases.map { ec =>
      val cn   = esc(sym(ec.symbol).name)
      val args = if ec.ctorArgs.isEmpty then "" else s"(${ec.ctorArgs.map(term(_, i + 1)).mkString(", ")})"
      val body = if ec.body.isEmpty then "" else s" {\n${ec.body.map(stat(_, i + 2)).mkString("\n")}\n${ind(i + 1)}}"
      s"${ind(i + 1)}case object $cn extends $name$args$body"
    }
    // `def` (not `val`) so Java's `E.values()` call site type-checks; also a no-paren read works.
    val values = s"${ind(i + 1)}def values(): scala.Array[$name] = scala.Array(${cd.enumCases.map(ec => esc(sym(ec.symbol).name)).mkString(", ")})"
    // Java's `Enum.valueOf(String)` — resolve a constant by name (throws like the JDK on no match).
    val vArms  = cd.enumCases.map(ec => esc(sym(ec.symbol).name)).map(n => s"""${ind(i + 2)}case "$n" => $n""").mkString("\n")
    val valueOf = s"${ind(i + 1)}def valueOf(name: java.lang.String): $name = name match {\n$vArms\n${ind(i + 2)}case _ => throw new java.lang.IllegalArgumentException(name)\n${ind(i + 1)}}"
    val objBody = (cases :+ values :+ valueOf) ++ statics.map(memberStat(_, i + 1)).filter(_.nonEmpty)
    s"$cls\n${ind(i)}object $name {\n${objBody.mkString("\n")}\n${ind(i)}}"

  // a Java `static` nested class has no instance home in Scala → it moves to the companion
  // `object` alongside static vals/defs. A non-static inner class stays in the class body.
  /** Replace the constructor `CtorFunnel` promoted to Scala's PRIMARY by its own body statements
    * — they run at construction, which is where a Scala class body runs them too. Its `super(args)`
    * has already been lifted into the `extends` clause and its parameters into the class's
    * parameter list; every other constructor stays a secondary `def this(...)`. */
  private def lowerCtors(body: List[Statement], plan: CtorFunnel.Plan): List[Statement] =
    plan.primary match
      case None    => body
      case Some(c) => body.flatMap { case d: Tree.DefDef if d.symbol == c.symbol => plan.primaryBody; case s => List(s) }

  /** a Java `static { … }` / instance `{ … }` initializer block, carried as a synthetic member. */
  private def isInitBlock(d: Tree.DefDef): Boolean =
    val n = sym(d.symbol).name
    n == "<clinit>" || n == "<initblock>"

  private def isStatic(s: Statement): Boolean = s match
    case d: Tree.ClassDef => sym(d.symbol).flags.isStatic
    case d: Definition    => sym(d.symbol).flags.isStatic
    case _                => false

  /** Scala secondary constructors must delegate to a PRECEDING constructor, so order fields first,
    * then constructors in DELEGATION-TOPOLOGICAL order (each ctor's `this(args)` target emitted
    * before it), then everything else. Arity is not a reliable proxy — a 3-arg convenience ctor can
    * delegate to a 1-arg one (`Texture(pixmap,fmt,mip)` → `Texture(data)`), so we follow the actual
    * `this(...)` edges, keyed by the target ctor's own symbol. */
  private def orderBody(body: List[Statement], paramfulPrimary: Boolean = false): List[Statement] =
    def isCtor(s: Statement) = s match { case d: Tree.DefDef => sym(d.symbol).name == "<init>"; case _ => false }
    // the peer ctor this one delegates to via a leading `this(args)` (NOT super, NOT the no-arg
    // primary) — its symbol identifies the exact target constructor.
    def delegateTarget(d: Tree.DefDef): Option[SymId] = d.rhs match
      case Some(Tree.Block((Tree.Apply(Tree.Select(r, m, _, _), args, _, _, _)) :: _, _, _, _))
          if sym(m).name == "<init>" && args.nonEmpty && !r.isInstanceOf[Tree.Super] => Some(m)
      case _ => None
    // a no-arg constructor whose body is only super/this delegation is degenerate — Scala's
    // implicit primary constructor already is no-arg, and `def this() = this()` self-recurses.
    // Only when the primary IS no-arg: against a PARAMFUL primary a `C() { this(16); }` is the
    // only thing that makes `new C()` legal at all, so it must be emitted.
    def degenerate(d: Tree.DefDef): Boolean =
      !paramfulPrimary && d.paramss.flatten.isEmpty && (d.rhs match
        case Some(Tree.Block(stats, _, _, _)) =>
          stats.forall { case Tree.Apply(Tree.Select(_, m, _, _), _, _, _, _) => sym(m).name == "<init>"; case _ => false }
        case _ => true)
    val ctorList = body.collect { case d: Tree.DefDef if isCtor(d) && !degenerate(d) => d }
    val bySym    = ctorList.map(d => d.symbol -> d).toMap
    // DFS post-order = topological order (a target is appended before its caller); `inProgress`
    // breaks any (illegal) cycle so a malformed chain can't loop forever.
    val ordered    = collection.mutable.ListBuffer[Tree.DefDef]()
    val visited    = collection.mutable.Set[SymId]()
    val inProgress = collection.mutable.Set[SymId]()
    def visit(d: Tree.DefDef): Unit =
      if !visited(d.symbol) && !inProgress(d.symbol) then
        inProgress += d.symbol
        delegateTarget(d).flatMap(bySym.get).foreach(visit)
        inProgress -= d.symbol
        visited += d.symbol
        ordered += d
    ctorList.foreach(visit)
    val fields = body.collect { case v: Tree.ValDef => v }
    val rest   = body.filterNot(s => isCtor(s) || s.isInstanceOf[Tree.ValDef])
    fields ++ ordered.toList ++ rest

  private def typeParam(td: Tree.TypeDef): String =
    val name = esc(sym(td.symbol).name)
    td.rhs.tpe match
      case TypeRepr.TypeBounds(TypeRepr.NoType, TypeRepr.NoType) => name
      case TypeRepr.TypeBounds(lo, hi) =>
        val l = if lo == TypeRepr.NoType then "" else s" >: ${tpe(lo)}"
        val h = if hi == TypeRepr.NoType then "" else s" <: ${tpe(hi)}"
        s"$name$l$h"
      case other => s"$name <: ${tpe(other)}"

  /** The parent's promoted-constructor formal at position `n`, with the parent's OWN type
    * parameters replaced by whatever the `extends` clause supplies for them — `MapIterator<V>`'s
    * `IntMap<V>` seen from `Entries[V] extends MapIterator[V]` is `IntMap[V]`, `V` now being
    * `Entries`'. `None` when the parent is external, has no promoted constructor, or is applied at
    * a different arity than it declares. */
  private def superFormal(parent: TypeRepr, n: Int): Option[TypeRepr] =
    val actuals = parent match
      case TypeRepr.AppliedType(_, as) => as
      case _                           => Nil
    for
      tycon <- headSymOf(parent)
      pcd   <- program.definitionOf(tycon).collect { case c: Tree.ClassDef => c }
      if pcd.tparams.sizeIs == actuals.size
      p     <- plans(pcd).primaryParams.lift(n)
    yield substTp(p.tpt.tpe, pcd.tparams.map(_.symbol).zip(actuals).toMap)

  /** An argument lifted into the `extends` clause.
    *
    * The argument kept the wildcard fill its DECLARATION was rendered with (`map$p: IntMap[?]`),
    * but the parent's constructor asks for that same Java raw type read in another position, where
    * a wildcard could not survive. Both are the same type to Java, which passed it unchecked; this
    * writes the conversion down.
    *
    * WHICH type to name is decided by the parent, not by the argument alone. Where the parent's
    * own wildcards were eliminated to reach the `extends` clause ([[deWildcarded]]) — `Keys extends
    * MapIterator` becoming `MapIterator[AnyRef]` — the same elimination is right. But a parent
    * applied to NAMED arguments never lost anything: `Entries[V] extends MapIterator[V]` asks for
    * `IntMap[V]`, and eliminating the argument's wildcard independently produced
    * `IntMap[Object]` — the right shape at the wrong type, which scalac rejects. So take the
    * parent's formal under its actual instantiation whenever it is available, and fall back to
    * the isolated elimination only for a parent we cannot see into. */
  private def superArg(parent: TypeRepr, a: Term, n: Int, i: Int): String =
    if !hasWildcardArg(a.tpe) then term(a, i)
    else
      val target = superFormal(parent, n).filterNot(hasWildcardArg).map(tpe)
        .getOrElse(deWildcarded(a.tpe, named = false))
      s"${term(a, i)}.asInstanceOf[$target]"

  private def parent(p: Term | TypeTree): String = p match
    case tt: TypeTree  => parentTpe(tt.tpe)
    case t: Term  => parentTpe(t.tpe)

  /** a parent type in an `extends` clause: a wildcard type argument (`Foo[?, ?]`, from a raw
    * generic supertype) is ILLEGAL here — replace each `?` with its upper bound (or `AnyRef`). */
  /** Only the HEAD is a `namedInner` position. A type ARGUMENT of the parent is an ordinary type
    * position: the simple name of an inner class is NOT in scope in an `extends` clause
    * (`ParticleEffectPool extends Pool[PooledEffect]` → `Not found: type PooledEffect`), while the
    * projection that `typeSym` would otherwise give is both legal and correct there. */
  private def parentTpe(t: TypeRepr): String = deWildcarded(t, named = true)

  /** the head symbol of a (possibly applied) type. */
  private def headSymOf(t: TypeRepr): Option[SymId] = t match
    case TypeRepr.TypeRef(_, s)      => Some(s)
    case TypeRepr.AppliedType(tc, _) => headSymOf(tc)
    case _                           => None

  /** a type's own parameters paired with their declared upper bounds (`NoType` when unbounded). */
  private def declBounds(tycon: SymId): List[(SymId, TypeRepr)] =
    program.definitionOf(tycon).collect { case c: Tree.ClassDef =>
      c.tparams.map(tp => tp.symbol -> (tp.rhs.tpe match
        case TypeRepr.TypeBounds(_, hi) if hi != TypeRepr.NoType => hi
        case _                                                   => TypeRepr.NoType))
    }.getOrElse(Nil)

  /** does this type mention the given symbol anywhere — the F-bound test (`N <: Node[N,V,A]`)? */
  private def mentionsSym(t: TypeRepr, s: SymId): Boolean = t match
    case TypeRepr.TypeRef(_, x)             => x == s
    case TypeRepr.AppliedType(tc, as)       => mentionsSym(tc, s) || as.exists(mentionsSym(_, s))
    case TypeRepr.TypeBounds(lo, hi)        => mentionsSym(lo, s) || mentionsSym(hi, s)
    case TypeRepr.AndType(l, r)             => mentionsSym(l, s) || mentionsSym(r, s)
    case TypeRepr.OrType(l, r)              => mentionsSym(l, s) || mentionsSym(r, s)
    case _                                  => false

  private def substTp(t: TypeRepr, m: Map[SymId, TypeRepr]): TypeRepr = t match
    case TypeRepr.TypeRef(_, s) if m.contains(s) => m(s)
    case TypeRepr.AppliedType(tc, as)            => TypeRepr.AppliedType(substTp(tc, m), as.map(substTp(_, m)))
    case other                                   => other

  /** Render a type with every WILDCARD argument eliminated — illegal in an `extends` clause, and
    * illegal as the target of a cast.
    *
    * A wildcard becomes its own written bound, else the type PARAMETER's declared upper bound, else
    * `AnyRef`. Consulting the declaration is what the plain `AnyRef` fill got wrong: it produced
    * `extends ParticleControllerRenderer[AnyRef, AnyRef]` for a class whose parameters are
    * `D <: ParticleControllerRenderData, T <: ParticleBatch[D]`, which fails its own bounds.
    * Arguments resolve LEFT TO RIGHT with the earlier choices substituted in, because a later bound
    * may name an earlier parameter — as `T <: ParticleBatch[D]` does.
    *
    * `named` selects the head's rendering. It is a Boolean rather than the `byName` combinator
    * passed as a function because `byName` sets a mutable flag AROUND evaluating its by-name
    * argument; handing it to a strict `String => String` parameter evaluates the head first and
    * silently loses the flag (which turned `extends Channel` into `extends ParallelArray#Channel`). */
  /** The de-wildcarding CHOICE, as types rather than as text — the same decision [[deWildcarded]]
    * renders, exposed so that a parent's elimination and the members that override through it can
    * be driven from ONE answer. `None` where the slot stays a wildcard (F-bounded, or nothing to
    * fill from), which is exactly where an override cannot be aligned either. */
  private def deWildcardedArgs(tc: TypeRepr, args: List[TypeRepr]): List[Option[TypeRepr]] =
    val bounds = headSymOf(tc).map(declBounds).getOrElse(Nil)
    args.zipWithIndex.foldLeft((List.empty[Option[TypeRepr]], Map.empty[SymId, TypeRepr])) {
      case ((acc, m), (a, i)) =>
        val fBounded = bounds.lift(i).exists((p, hi) => hi != TypeRepr.NoType && mentionsSym(hi, p))
        val chosen: Option[TypeRepr] = a match
          case _: TypeRepr.TypeBounds if fBounded                  => scala.None
          case TypeRepr.TypeBounds(_, hi) if hi != TypeRepr.NoType => Some(substTp(hi, m))
          case _: TypeRepr.TypeBounds =>
            bounds.lift(i).map(_._2).filter(_ != TypeRepr.NoType).map(substTp(_, m))
          case other => Some(other)
        val m2 = (bounds.lift(i), chosen) match
          case (Some((pp, _)), Some(c)) => m + (pp -> c)
          case _                        => m
        (acc :+ chosen, m2)
    }._1

  private def deWildcarded(t: TypeRepr, named: Boolean): String =
    def head(f: => String): String = if named then byName(f) else f
    t match
      case TypeRepr.AppliedType(tc, args) =>
        val bounds = headSymOf(tc).map(declBounds).getOrElse(Nil)
        val (as, _) = args.zipWithIndex.foldLeft((List.empty[String], Map.empty[SymId, TypeRepr])) {
          case ((acc, m), (a, i)) =>
            // An F-BOUNDED parameter (`N extends Node<N,V,A>`) cannot be eliminated at all: no
            // finite type satisfies `N <: Node[N,V,A]` except a real subclass. `Node[Object, …]`
            // fails the bound, and so does every unrolling — `Node[Node[Object,…], …]` needs its
            // argument to be the very type being defined, and `Node` is invariant. Java has the
            // same bound and simply does not check it at an erased use; Scala does. The one type
            // that works is the WILDCARD, which asserts only that SOME type satisfies the bound —
            // verified against scalac before writing this. So an F-bounded slot stays `?`.
            val fBounded = bounds.lift(i).exists((p, hi) => hi != TypeRepr.NoType && mentionsSym(hi, p))
            val chosen: Option[TypeRepr] = a match
              case _: TypeRepr.TypeBounds if fBounded                  => scala.None
              case TypeRepr.TypeBounds(_, hi) if hi != TypeRepr.NoType => Some(substTp(hi, m))
              case _: TypeRepr.TypeBounds =>
                bounds.lift(i).map(_._2).filter(_ != TypeRepr.NoType).map(substTp(_, m))
              case other => Some(other)
            val rendered =
              if chosen.isEmpty && fBounded then "?" else chosen.map(tpe).getOrElse("scala.AnyRef")
            val m2 = (bounds.lift(i), chosen) match
              case (Some((p, _)), Some(c)) => m + (p -> c)
              case _                       => m
            (acc :+ rendered, m2)
        }
        s"${head(tpe(tc))}[${as.mkString(", ")}]"
      case _ => head(tpe(t))

  /** does this type carry a wildcard argument anywhere? */
  private def hasWildcardArg(t: TypeRepr): Boolean = t match
    case _: TypeRepr.TypeBounds      => true
    case TypeRepr.AppliedType(tc, a) => hasWildcardArg(tc) || a.exists(hasWildcardArg)
    case _                           => false

  private def stat(s: Statement, i: Int): String = s match
    case c: Tree.ClassDef => classDef(c, i)
    // a Java initializer block is carried as a synthetic member; emit its BODY inline rather than
    // a `def`, since a block in a class/object body runs at initialisation — where Java runs it
    // too — and `orderBody` has already placed it after the field declarations it fills.
    // `locally` is REQUIRED, not decoration: a bare `{ … }` on the line after a field initialised
    // with `new T(…)` is parsed as that constructor's anonymous-class body
    // (`new Array[Float](n) { … }`), which fails as "anonymous class cannot extend final class".
    case d: Tree.DefDef if isInitBlock(d) => d.rhs.map(r => s"${ind(i)}locally ${term(r, i)}").getOrElse("")
    case d: Tree.DefDef   => defDef(d, i)
    case v: Tree.ValDef   => valDef(v, i)
    case t: Tree.TypeDef  => s"${ind(i)}${if sym(t.symbol).flags.isOpaque then "opaque " else ""}type ${esc(sym(t.symbol).name)} = ${tpe(t.rhs.tpe)}"
    case t: Term     => ind(i) + term(t, i)

  /** ctor type-parameter substitution (Scala secondary ctors can't be generic) → their bounds. */
  private var tparamSubst: Map[SymId, TypeRepr] = Map.empty

  /** Disambiguate a member that arrives CONCRETE from both the superclass and a mixin.
    *
    * Java has single inheritance of implementation, so this is never ambiguous there: a concrete
    * superclass method simply IMPLEMENTS the interface's, default or not. `IntMap.Entries extends
    * MapIterator implements Iterable<Entry>, Iterator<Entry>` gets `MapIterator.remove()`, and
    * java's `Iterator.remove` is satisfied by it. Scala linearises instead and refuses: "class
    * Entries inherits conflicting members … (Note: this can be resolved by declaring an override
    * in class Entries.)" — 11 sites in gdx core.
    *
    * So declare it, forwarding to the parent JAVA would have run: the SUPERCLASS, which is the head
    * of the parents list. This is a rendering repair rather than a tree rewrite because that is all
    * it is — no new symbol exists, no call site changes, and the forwarder is exactly the method
    * the class already had.
    *
    * `Tree.Super`'s `cls` is always the enclosing class ([[SpoonTir.superTerm]]), so a qualified
    * `super[X]` has no TIR form; the text is emitted directly. */
  private def diamondOverrides(cd: Tree.ClassDef, i: Int): List[String] =
    def headOf(t: TypeRepr): Option[SymId] = headSymOf(t)
    val parentTs = cd.parents.map { case tt: TypeTree => tt.tpe; case t: Term => t.tpe }
    if parentTs.sizeIs < 2 then Nil
    else
      def classOf_(t: TypeRepr): Option[Tree.ClassDef] =
        headOf(t).flatMap(x => program.definitionOf(x)).collect { case c: Tree.ClassDef => c }
      /** concrete instance methods, name -> the DefDef, walking a parent chain. */
      def externalOf(t: TypeRepr): Set[(String, List[Int])] =
        headOf(t).map(x => sym(x).fullName).flatMap(externalConcrete.get).getOrElse(Set.empty)
      def concrete(t: TypeRepr, seen: Set[SymId] = Set.empty): Map[(String, List[Int]), Tree.DefDef] =
        classOf_(t) match
          case Some(c) if !seen(c.symbol) =>
            val own = c.body.collect {
              case d: Tree.DefDef if d.rhs.isDefined && sym(d.symbol).name != "<init>" &&
                                     !sym(d.symbol).flags.isStatic =>
                (sym(d.symbol).name, d.paramss.map(_.size)) -> d
            }.toMap
            c.parents.map { case tt: TypeTree => tt.tpe; case x: Term => x.tpe }
              .foldLeft(own)((acc, pt) => concrete(pt, seen + c.symbol) ++ acc)
          case _ => Map.empty
      val sup     = concrete(parentTs.head)
      val mixins  = parentTs.tail.flatMap(t => concrete(t).keySet ++ externalOf(t)).toSet
      val ownKeys = cd.body.collect {
        case d: Tree.DefDef => (sym(d.symbol).name, d.paramss.map(_.size))
      }.toSet
      val supName = classOf_(parentTs.head).map(c => esc(sym(c.symbol).name))
      supName.toList.flatMap { sn =>
        sup.toList.filter((k, _) => mixins(k) && !ownKeys(k)).sortBy((k, _) => k._1).map { (_, d) =>
          val n   = esc(sym(d.symbol).name)
          val pss = d.paramss.map(paramClause).mkString
          val as  = d.paramss.map(ps => ps.map(v => esc(sym(v.symbol).name)).mkString("(", ", ", ")")).mkString
          s"${ind(i)}override def $n$pss: ${tpe(d.returnTpt.tpe)} = super[$sn].$n$as"
        }
      }

  private def defDef(d: Tree.DefDef, i: Int): String =
    val s     = sym(d.symbol)
    val isCtor = s.name == "<init>"
    val name  = if isCtor then "this" else esc(s.name)
    // a Java generic constructor (`<T extends X> C(...)`) has no Scala form — secondary ctors can't
    // be generic. Drop the type params and substitute each with its upper bound throughout the ctor.
    val savedSubst = tparamSubst
    if isCtor && d.tparams.nonEmpty then
      tparamSubst = savedSubst ++ d.tparams.map(tp => tp.symbol -> (tp.rhs.tpe match
        case TypeRepr.TypeBounds(_, hi) if hi != TypeRepr.NoType => hi
        case _ => TypeRepr.NoType)).toMap // unbounded → `Any`
    val tps   = if isCtor || d.tparams.isEmpty then "" else "[" + d.tparams.map(typeParam).mkString(", ") + "]"
    val pss   = d.paramss.map(paramClause).mkString
    val ret   = if isCtor then "" else s": ${tpe(d.returnTpt.tpe)}"
    // a Java `while(true){ … return … }` idiom: the loop never falls through, but Scala types
    // `while(true)` as Unit, so a non-Unit method needs an unreachable tail after it.
    val needsUnreachable = !isCtor && !isUnitType(d.returnTpt.tpe) && d.rhs.exists(endsInInfiniteLoop)
    val rhs =
      if isCtor then s" = ${ctorBody(d, i)}"
      else d.rhs.map(r =>
        if needsUnreachable then s" = {\n${ind(i + 1)}${term(r, i + 1)}\n${ind(i + 1)}throw new java.lang.RuntimeException(\"unreachable\")\n${ind(i)}}"
        else s" = ${term(r, i)}").getOrElse("")
    tparamSubst = savedSubst // restore (ctor type-param substitution was local to this def)
    s"${annots(s, i)}${ind(i)}${mods(s.flags, privateQualifier(s.owner))}def $name$tps$pss$ret$rhs"

  /** does this loop body contain an unlabelled `break` that belongs to THIS loop?
    *
    * Stops descending at a nested loop or switch, since java's unlabelled `break` binds to the
    * innermost enclosing one — a `boundary` placed around the outer loop would otherwise catch a
    * break the inner construct owns. */
  /** Loop-jump scope, as scala `boundary` nesting.
    *
    * `break` leaves the loop and `continue` skips to the next iteration, so they need boundaries in
    * DIFFERENT places: one around the whole loop, one around the loop BODY. When a loop needs both,
    * the body boundary is the innermost, so an un-annotated `break(())` inside it would continue
    * rather than break — the outer one has to be NAMED and targeted explicitly
    * (`boundary { brk ?=> … break(())(using brk) }`, verified against scalac).
    *
    * `breakTarget`: `None` = no enclosing loop boundary, so a `break` here belongs to a SWITCH;
    * `Some("")` = an unnamed one is innermost; `Some(name)` = it must be named because a body
    * boundary sits inside it. Cleared by `match`, since java's `break` there binds to the switch —
    * but `contBoundary` is NOT, because a `continue` inside a switch still continues the loop. */
  private var breakTarget: Option[String] = scala.None
  private var contBoundary = false
  private var labelSeq = 0
  private def inLoop[A](brk: Option[String], cont: Boolean)(f: => A): A =
    val (sb, sc) = (breakTarget, contBoundary)
    breakTarget = brk; contBoundary = cont
    try f finally { breakTarget = sb; contBoundary = sc }
  private def inSwitch[A](f: => A): A =
    val sb = breakTarget
    breakTarget = scala.None
    try f finally breakTarget = sb

  /** java LABEL -> the scala boundary name a `break`/`continue` naming it must target. A labelled
    * jump can sit at any depth, so unlike the unlabelled ones these are looked up, not scoped. */
  private val labelBreak = collection.mutable.Map[String, String]()
  private val labelCont  = collection.mutable.Map[String, String]()

  /** Render a loop with whatever boundaries its jumps need.
    *
    * Up to two: one around the LOOP for `break`, one around the BODY for `continue`. A loop needing
    * both must NAME the outer one — the body boundary is innermost, so an un-annotated `break(())`
    * inside it would continue instead. A LABELLED loop names whichever of the two its label is
    * actually jumped to, and registers the name for the duration of the body. */
  /** A java enhanced-for BINDING is a declaration with its own type; scala's `for (x <- xs)` binds at
    * the ITERABLE's element type. They agree in the ordinary case and java lets them differ:
    *
    * {{{ for (Object e : collection) if (!contains(e)) …   // Collection<?>, binding widened to Object }}}
    *
    * Java resolves every use of `e` in the body against `Object`; scala resolves them against the
    * element type, which for a wildcard is an unusable capture — so `contains(e)` fails with
    * `Found: ?1.CAP`. `Array.containsAll` and `NodeCollection.containsAll` in simple-graphs are
    * exactly this, and no amount of retyping at the collection fixes it: the loss is at the BINDING.
    *
    * Returns the declared type to re-bind at, or `None` when scala's own binding is already exact.
    *
    * Conservative in ONE direction on purpose. A difference must be PROVABLE — an element type this
    * function cannot read is treated as agreeing, because inventing an alias on no evidence would
    * add a cast to every for-each in the corpus to fix the handful that need one. The cast itself is
    * sound wherever it does fire: java only permits a WIDENING here, so the value already has the
    * declared type at runtime. */
  private def widenedBinding(b: Tree.ValDef, it: Term): Option[String] =
    elementTpe(it.tpe).filter(_ != b.tpt.tpe).map(_ => tpe(b.tpt.tpe))

  /** the element type of something java could put in an enhanced-for: an applied generic's single
    * argument, or an array's element. `None` = not readable, which callers must treat as no evidence
    * rather than as a difference. */
  private def elementTpe(t: TypeRepr): Option[TypeRepr] = t match
    case TypeRepr.AppliedType(_, List(el)) => Some(el)
    case _                                 => scala.None

  private def loopWithJumps(body: Tree, label: Option[String], render: (=> String) => String,
                            bodyStr: => String): String =
    val lblB = label.filter(l => jumpsTo(body, l, brk = true))
    val lblC = label.filter(l => jumpsTo(body, l, brk = false))
    val hasB = breaksOut(body) || lblB.isDefined
    val hasC = continuesIn(body) || lblC.isDefined
    if !hasB && !hasC then render(bodyStr)
    else
      labelSeq += 1
      val seq  = labelSeq
      // the break boundary must be named when a body boundary sits inside it, or when a labelled
      // `break` names it from a nested loop
      val bName = if hasB && (hasC || lblB.isDefined) then s"brk$$$seq" else ""
      val cName = if hasC && lblC.isDefined then s"cnt$$$seq" else ""
      lblB.foreach(l => labelBreak(l) = bName)
      lblC.foreach(l => labelCont(l) = cName)
      val inner =
        try inLoop(if hasB then Some(bName) else scala.None, hasC) {
          if !hasC then bodyStr
          else if cName.isEmpty then s"scala.util.boundary { $bodyStr }"
          else s"scala.util.boundary { ($cName: scala.util.boundary.Label[scala.Unit]) ?=> $bodyStr }"
        }
        finally { lblB.foreach(labelBreak.remove); lblC.foreach(labelCont.remove) }
      val loop = render(inner)
      if !hasB then loop
      else if bName.isEmpty then s"scala.util.boundary { $loop }"
      else s"scala.util.boundary { ($bName: scala.util.boundary.Label[scala.Unit]) ?=> $loop }"

  /** a `break L` / `continue L` naming this loop, at ANY depth — a labelled jump crosses nested
    * loops and switches by definition, which is what it is for. */
  private def jumpsTo(t: Any, label: String, brk: Boolean): Boolean = t match
    case Tree.Break(Some(l), _, _) if brk     => l == label
    case Tree.Continue(Some(l), _, _) if !brk => l == label
    case xs: Iterable[?]                      => xs.exists(jumpsTo(_, label, brk))
    case Some(x)                              => jumpsTo(x, label, brk)
    case p: Product                           => p.productIterator.exists(jumpsTo(_, label, brk))
    case _                                    => false

  /** an unlabelled `continue` belonging to THIS loop. Unlike `breaksOut` it does NOT stop at a
    * `match`: java's `continue` inside a switch continues the enclosing LOOP. */
  private def continuesIn(t: Any): Boolean = t match
    case Tree.Continue(scala.None, _, _)                                  => true
    case _: Tree.While | _: Tree.DoWhile | _: Tree.For | _: Tree.ForEach  => false
    case xs: Iterable[?]                                                  => xs.exists(continuesIn)
    case Some(x)                                                          => continuesIn(x)
    case p: Product                                                       => p.productIterator.exists(continuesIn)
    case _                                                                => false

  private def breaksOut(t: Any): Boolean = t match
    case Tree.Break(scala.None, _, _)                     => true
    case _: Tree.While | _: Tree.DoWhile | _: Tree.Match |
         _: Tree.For | _: Tree.ForEach                    => false // binds to the inner one
    case xs: Iterable[?]                                  => xs.exists(breaksOut)
    case Some(x)                                          => breaksOut(x)
    // Product reflection rather than a hand-written case per node: a hand-rolled walk that stops
    // one node short is exactly how two of this project's silent defects survived (CLAUDE.md §3),
    // and there is no generic child accessor on the TIR to use instead.
    case p: Product                                       => p.productIterator.exists(breaksOut)
    case _                                                => false

  private def isUnitType(t: TypeRepr): Boolean = t match
    case TypeRepr.TypeRef(_, s) => sym(s).fullName == "scala.Unit"
    case _ => false
  /** the method body is (or ends in) an infinite `while(true)` / `for(;;)`. */
  private def endsInInfiniteLoop(t: Term): Boolean = t match
    // …unless it can BREAK out. Before `break` was emitted the loop really was infinite and the
    // unreachable tail was correct; now `boundary { while (true) … }` returns normally, and the
    // synthetic `throw` after it is reached on every exit.
    case Tree.While(Tree.Literal(Constant.BoolC(true), _, _), b, _, _, _) => !breaksOut(b)
    case Tree.For(_, None, b, _, _, _, _)                                 => !breaksOut(b)
    case Tree.Block(stats, e, _, _) =>
      endsInInfiniteLoop(e) || (e match {
        case Tree.Literal(Constant.UnitC, _, _) => stats.lastOption.collect { case x: Term => x }.exists(endsInInfiniteLoop)
        case _ => false
      })
    case _ => false

  /** A Scala secondary constructor must delegate to `this(...)` first — never `super(...)`.
    * Keep a Java `this(args)` delegation. A leading `super(args)` becomes `this()` followed by
    * the parent constructor's own statements, when `CtorFunnel` has established that the two
    * together run exactly what `super(args)` ran; where it has not, the arguments are still lost
    * and `OmissionCheck` still counts them. */
  private def ctorBody(cdef: Tree.DefDef, i: Int): String =
    val stats  = CtorFunnel.stmtsOf(cdef)
    val replay = currentClass.flatMap(plans.replayFor(_, cdef)).getOrElse(Nil)
    val (deleg, rest) = stats match
      case (Tree.Apply(Tree.Select(r, m, _, _), args, _, _, _)) :: tl if sym(m).name == "<init>" =>
        val d = r match
          case _: Tree.Super => superDelegation(args, i + 1)
          case _             => s"this(${args.map(term(_, i + 1)).mkString(", ")})"
        (d, tl)
      case all => ("this()", all)
    val lines = (ind(i + 1) + deleg) :: (replay ++ rest).map(stat(_, i + 1)).filter(_.trim.nonEmpty)
    s"{\n${joinStats(lines)}\n${ind(i)}}"

  /** A secondary constructor's `super(args)` — which scala cannot write — expressed as a
    * delegation to the PRIMARY, whose own `extends Parent(…)` makes the call.
    *
    * Exact when the primary passes its parameters STRAIGHT THROUGH to super (which is what
    * `CtorFunnel.plan0` requires before nominating it): substituting this constructor's super
    * ARGUMENTS for those parameters reproduces the same parent call. A shorter argument list is
    * padded with `null`, which is what the parent's own narrower overload would have left there,
    * and is what the reference port writes by hand (`Exception(message, cause.orNull)`); a padded
    * PRIMITIVE has no such value, so those decline and stay counted by `OmissionCheck`.
    *
    * `this()` otherwise — the arguments really are lost there, and the check still says so. */
  private def superDelegation(args: List[Term], i: Int): String =
    val plan = currentClass.map(plans.apply).getOrElse(CtorFunnel.Plan.none)
    val ps   = plan.primaryParams
    if ps.isEmpty || plan.superArgs.size != ps.size || args.sizeIs > ps.size then "this()"
    else
      // Matched by TYPE, not position: `GdxRuntimeException(Throwable t) { super(t); }` reaches the
      // parent's `(Throwable)` overload, so `t` belongs in the CAUSE slot — positionally it landed
      // in the message slot and did not even type-check. Each parameter takes the first unused
      // argument of its own type, and whatever is left is `null`, which is what the narrower
      // overload would have left there.
      def name(t: TypeRepr): String = t match
        case TypeRepr.TypeRef(_, sy)      => sym(sy).fullName
        case TypeRepr.AppliedType(tc, _)  => name(tc)
        case _                            => ""
      val used = collection.mutable.Set[Int]()
      val slots = ps.map { v =>
        val want = name(v.tpt.tpe)
        args.zipWithIndex.find((a, k) => !used(k) && name(a.tpe) == want) match
          case Some((a, k)) => used += k; Some(term(a, i))
          case scala.None   =>
            if primitiveNames(want) then scala.None else Some(s"null.asInstanceOf[${tpe(v.tpt.tpe)}]")
      }
      // every argument must find a home, or the call we build is not the one java made
      if slots.exists(_.isEmpty) || used.size != args.size then "this()"
      else s"this(${slots.flatten.mkString(", ")})"

  private val primitiveNames = Set("scala.Int", "scala.Long", "scala.Float", "scala.Double",
                                   "scala.Short", "scala.Byte", "scala.Char", "scala.Boolean", "scala.Unit")

  /** a parameter clause; a clause of `given` params renders as a Scala 3 `using` clause. */
  private def paramClause(ps: List[Tree.ValDef]): String =
    if ps.nonEmpty && ps.forall(p => sym(p.symbol).flags.isGiven) then s"(using ${ps.map(param).mkString(", ")})"
    else s"(${ps.map(param).mkString(", ")})"

  // NOTE: Java `T...` → Scala `T*` is deferred — it also needs array-spread (`arr: _*`) at call
  // sites and overload-aware resolution, else `f(array)` calls break. Emitting the param type
  // as `Array[T]` keeps varargs callable positionally via the array.
  private def param(v: Tree.ValDef): String =
    s"${esc(sym(v.symbol).name)}: ${tpe(overrideAlign.getOrElse(v.symbol, v.tpt.tpe))}"

  private def valDef(v: Tree.ValDef, i: Int): String =
    val s = sym(v.symbol)
    if s.flags.isGiven then
      return s"${ind(i)}given ${esc(s.name)}: ${tpe(v.tpt.tpe)}${v.rhs.map(r => s" = ${term(r, i)}").getOrElse("")}"
    v.rhs match
      case Some(r) if isJavaConstant(v, s) =>
        // Java calls this a CONSTANT VARIABLE (JLS 4.12.4): `static final` of primitive or String
        // type with a constant initialiser. javac INLINES every use, so reading `Matrix4.M00` does
        // NOT trigger `Matrix4`'s class initialiser — which is the only reason libgdx's static
        // initialisers are not a cycle. `Vector3`'s creates a `Matrix4`, whose constructor reads
        // `Matrix4.M00`; emitted as an ordinary typed `val` that call initialises `Matrix4`, which
        // creates a `Vector3` that is still half-built, and the JVM throws
        // `ExceptionInInitializerError`. Scala's equivalent of the java rule is `inline val` —
        // note WITHOUT the type ascription, which would defeat the constant type.
        s"${ind(i)}${mods(s.flags).replace("final ", "")}inline val ${esc(s.name)} = ${constAt(r, v.tpt.tpe)}"
      case Some(r) =>
        val kw = if s.flags.isMutable then "var" else "val"
        val q  = privateQualifier(s.owner)
        val m  = if kw == "var" then mods(s.flags, q).replace("final ", "") else mods(s.flags, q)
        s"${ind(i)}$m$kw ${esc(s.name)}: ${tpe(v.tpt.tpe)} = ${term(r, i)}"
      case None =>
        // an uninitialized Java field: a `var` defaulted so constructors can assign it (a bare
        // `val x: T` is an abstract member and won't compile in a class). `final var` is
        // contradictory in Scala, so `final` is dropped here.
        s"${ind(i)}${mods(s.flags, privateQualifier(s.owner)).replace("final ", "")}var ${esc(s.name)}: ${tpe(v.tpt.tpe)} = ${defaultFor(v.tpt.tpe)}"

  /** the literal rendered AT the field's declared type.
    *
    * `inline val` takes its constant type from the literal, so the type ascription that would
    * normally carry it is rejected ("inline value must have a literal constant type"). Java's
    * `static private final float degFull = 360` therefore has to emit `360.0f`, not `360`: as an
    * `Int` constant it turned `SIN_COUNT / degFull` into INTEGER division and `MathUtils.cosDeg(90)`
    * returned -0.07 instead of 0. */
  private def constAt(r: Term, t: TypeRepr): String = (r, t) match
    case (Tree.Literal(c, _, _), TypeRepr.TypeRef(_, sy)) =>
      def num: Option[BigDecimal] = c match
        case Constant.ByteC(v)  => Some(BigDecimal(v.toInt)); case Constant.ShortC(v) => Some(BigDecimal(v.toInt))
        case Constant.IntC(v)   => Some(BigDecimal(v));       case Constant.LongC(v)  => Some(BigDecimal(v))
        case Constant.FloatC(v) => Some(BigDecimal(v.toDouble)); case Constant.DoubleC(v) => Some(BigDecimal(v))
        case Constant.CharC(v)  => Some(BigDecimal(v.toInt)); case _ => scala.None
      (sym(sy).fullName, num) match
        case ("scala.Float", Some(n))  => s"${n.toFloat}f"
        case ("scala.Double", Some(n)) => val d = n.toDouble; if d == d.toLong then s"$d" else d.toString
        case ("scala.Long", Some(n))   => s"${n.toLong}L"
        case ("scala.Short", Some(n))  => s"${n.toShort}"
        case ("scala.Byte", Some(n))   => s"${n.toByte}"
        case _                          => constant(c)
    case _ => term(r, 0)

  /** a java CONSTANT VARIABLE: `static final`, primitive or `String`, literal initialiser. */
  private def isJavaConstant(v: Tree.ValDef, s: Symbol): Boolean =
    s.flags.isStatic && s.flags.isFinal && !s.flags.isMutable &&
      (v.rhs match { case Some(_: Tree.Literal) => true; case _ => false }) &&
      (v.tpt.tpe match
        case TypeRepr.TypeRef(_, x) =>
          val n = sym(x).fullName
          primitiveNames(n) || n == "java.lang.String"
        case _ => false)

  private def defaultFor(t: TypeRepr): String = t match
    case TypeRepr.TypeRef(_, s) => sym(s).fullName match
        case "scala.Int" | "scala.Short" | "scala.Byte" => "0"
        case "scala.Long"                               => "0L"
        case "scala.Float"                              => "0.0f"
        case "scala.Double"                             => "0.0"
        case "scala.Boolean"                            => "false"
        case "scala.Char"                               => "'\\u0000'"
        case "scala.Unit"                               => "()"
        case _                                          => s"null.asInstanceOf[${tpe(t)}]"
    case _ => s"null.asInstanceOf[${tpe(t)}]"

  /** A declaration's Java annotations, rendered ahead of it.
    *
    * FULLY QUALIFIED like every other reference this phase emits, so `@Test` becomes
    * `@org.junit.Test` and needs no import. Losing these is a silent correctness defect: a JUnit
    * suite whose `@Test` did not survive runs ZERO tests and reports SUCCESS. */
  private def annots(s: Symbol, i: Int): String =
    if s.annotations.isEmpty then ""
    else s.annotations.map { a =>
      val args = if a.args.isEmpty then ""
                 // Java's single-element `@A(x)` names its value `value`; Scala takes it positionally.
                 else if a.args.sizeIs == 1 && a.args.head._1 == "value" then s"(${term(a.args.head._2, i)})"
                 else s"(${a.args.map((k, v) => s"$k = ${term(v, i)}").mkString(", ")})"
      s"${ind(i)}@${tpe(a.tpe)}$args\n"
    }.mkString

  /** The top-level type a symbol lives in, when it is NOT that type itself — i.e. the qualifier a
    * nested class's `private` member needs.
    *
    * Java scopes `private` to the enclosing TOP-LEVEL class: a nested class's private field is
    * readable by the outer class and vice versa, and the outer class's privates are readable from
    * the nested one. Scala's bare `private` is class-only, so the faithful rendering is
    * `private[TopLevel]`. Without it, an outer class reading its own nested class's field —
    * ordinary Java, and what Ashley's `PooledEngineTests` does — does not compile.
    *
    * Applied ONLY to a NESTED class's members. Qualifying a top-level class's own `private` was
    * tried and REGRESSED libGDX by one error: `GL30Interceptor.check` is private, so
    * `GL31Interceptor.check` overrides nothing — and `private[GL30Interceptor]` changed that, which
    * scala then demanded an `override` for. Java's `private` on a top-level class's member is
    * already exactly scala's, so widening it is not a no-op, it is a different program.
    *
    * Deriving the qualifier from the symbol's OWNER chain was tried first and returned nothing; the
    * class currently being rendered is the fact the emitter actually has. */
  private def privateQualifier(owner: SymId): Option[String] =
    Option.when(currentTopLevel.nonEmpty && currentOwnerSym != currentTopLevelSym)(currentTopLevel)

  private def mods(f: Flags): String = mods(f, scala.None)

  private def mods(f: Flags, privateIn: Option[String]): String =
    val parts = List(
      if f.isPrivate then privateIn.fold("private ")(o => s"private[$o] ") else "",
      // Java `protected` (package + any-instance-in-subclass) is MORE permissive than Scala
      // `protected` (this-instance only), so a faithful port emits it as public — loosening
      // visibility can only remove access errors, never introduce them.
      "",
      // `private override` is illegal in scala, and the pair is contradictory: a PRIVATE java
      // method is invisible to subclasses, so it overrides nothing — a name/arity agreement with an
      // inherited member is coincidence. `private` is the faithful half; drop the modifier.
      if f.isOverride && !f.isPrivate then "override " else "",
      if f.isFinal then "final " else "",
      if f.isSealed then "sealed " else "",
      if f.isImplicit then "implicit " else "",
      if f.isLazy then "lazy " else "",
    )
    parts.mkString

  // ---- terms ----
  /** true when an `Ident`'s symbol is actually a TYPE used as a value (a static-access
    * receiver like `Float.compare`) — those must render as the (qualified) type name. */
  private def isTypeRef(id: SymId): Boolean = program.definitionOf(id) match
    case Some(_: Tree.ClassDef) => true
    case Some(_)                => false
    case None                   => val f = sym(id).fullName; f.contains('.') && !f.contains('#') && sym(id).info == TypeRepr.NoType

  /** a type used as a VALUE (static-access receiver) — dotted path, never a `#` projection
    * (which is type-position-only syntax). */
  private def typeValue(id: SymId): String =
    val s = sym(id)
    // a static nested type lives in the companion `object`, so name it through the value path
    // `Outer.Inner` even from inside `Outer` (companion members aren't in the class's scope).
    if s.flags.isStatic && s.fullName.contains('$') then s.fullName.replace('$', '.')
    else if currentDeclared(id) || inheritedNested(s.owner) then esc(s.name)
    else s.fullName.replace('$', '.')

  /** a static member lives in the companion `object`; even inside its own class it must be
    * named `Owner.member`, since a Scala class doesn't see its companion's members unqualified. */
  private def staticRef(s: SymId): String =
    val sm = sym(s)
    if sm.flags.isStatic && sm.owner != SymId.None && program.symbolOf(sm.owner).exists(_.info.isInstanceOf[TypeRepr.TypeRef])
    then s"${typeValue(sm.owner)}.${esc(sm.name)}"
    else if shadowedByCompanionStatic(s) then s"this.${esc(sm.name)}"
    else local(s)

  /** Does a bare reference to this INSTANCE member collide with a static of the same name that the
    * enclosing companion carries or re-exports?
    *
    * `DepthShader.Config` inherits an instance field `defaultCullFace` and writes it bare, exactly
    * as Java did — but `object DepthShader` also holds a static `defaultCullFace`, and Scala reports
    * the bare name as ambiguous between the two. Java had no such clash: statics and instance
    * fields live in one namespace there, and the inherited instance field simply wins.
    *
    * `this.` says what Java meant. Decided from the TIR symbol — the reference resolves to an
    * instance member of an ANCESTOR — rather than from the frontend, which cannot see it: Spoon
    * does not resolve this reference to a `CtFieldWrite` under noClasspath at all. */
  private def shadowedByCompanionStatic(s: SymId): Boolean =
    val sm = sym(s)
    !sm.flags.isStatic && sm.owner != SymId.None && sm.info != TypeRepr.NoType &&
      !sm.info.isInstanceOf[TypeRepr.MethodType] && !sm.info.isInstanceOf[TypeRepr.PolyType] &&
      classStack.lastOption.exists { cur =>
        // an INHERITED member (declaring it here would shadow the static on its own)
        cur != sm.owner && ancestorsOf(cur).contains(sm.owner) &&
          classStack.exists(c => staticOwnersOf(c).contains(esc(sm.name)))
      }

  private def term(t: Term, i: Int): String = t match
    case Tree.Ident(s, _, _)            => if isTypeRef(s) then typeValue(s) else staticRef(s)
    case Tree.Literal(c, _, _)          => constant(c)
    case Tree.This(s, _, _)             => thisRef(s)
    case Tree.Super(_, _, _)            => "super"
    case Tree.Select(q, s, _, _)        => s"${term(q, i)}.${local(s)}"
    case Tree.New(tpt, _, _, anon)      => s"new ${ctorTpe(tpt.tpe)}${anonBody(anon, i)}"
    case Tree.Apply(fun, args, _, _, _) => applyStr(fun, args, i)
    case Tree.TypeApply(fun, targs, _, _) => s"${term(fun, i)}[${targs.map(a => tpe(a.tpe)).mkString(", ")}]"
    case Tree.Assign(l, r, _, _)        => s"${term(l, i)} = ${term(r, i)}"
    case Tree.Block(stats, expr, _, _)  => block(stats, expr, i)
    case Tree.Lambda(ps, body, _, _)    => s"(${ps.map(param).mkString(", ")}) => ${term(body, i)}"
    case Tree.If(c, th, el, _, _)       => s"if (${term(c, i)}) ${term(th, i)} else ${term(el, i)}"
    case Tree.Typed(e, tpt, _, _)       => s"${operand(e, i)}.asInstanceOf[${tpe(castTarget(e, tpt.tpe))}]" // Java cast
    case Tree.Repeated(es, _, _)        => es.map(term(_, i)).mkString(", ")
    case Tree.Return(e, _, _)           => "return" + e.map(x => " " + term(x, i)).getOrElse("")
    case Tree.While(c, b, _, _, lbl)    =>
      loopWithJumps(b, lbl, bd => s"while (${term(c, i)}) $bd", term(b, i))
    case Tree.Throw(e, _, _)            => s"throw ${term(e, i)}"
    case Tree.InstanceOf(e, tpt, _, _)  => s"${term(e, i)}.isInstanceOf[${tpe(tpt.tpe)}]"
    case Tree.ArrayAccess(a, idx, _, _) => s"${term(a, i)}(${term(idx, i)})"
    case Tree.ArrayLength(a, _, _)      => s"${term(a, i)}.length"
    case Tree.NewArray(el, dims, init, _, _) =>
      init match
        // `scala.Array`, fully qualified: a bare `Array` collides with libGDX's own
        // `com.badlogic.gdx.utils.Array` inside that package (same-package name resolution).
        case Some(es) => s"scala.Array[${tpe(el.tpe)}](${es.map(term(_, i)).mkString(", ")})"
        // Java `new T[a][b]` gives every dimension a size; Scala's `new Array` takes only ONE, so a
        // MULTI-dimension allocation lowers to `Array.ofDim[base](a, b)`. A single dim (incl. partial
        // `new T[a][]`) stays `new Array[elem](a)`.
        case None if dims.sizeIs > 1 => s"scala.Array.ofDim[${tpe(baseElem(el.tpe))}](${dims.map(term(_, i)).mkString(", ")})"
        case None     => s"new scala.Array[${tpe(el.tpe)}](${dims.map(term(_, i)).mkString(", ")})"
    case Tree.ForEach(b, it, body, _, _, lbl) =>
      val raw  = sym(b.symbol).name
      val name = esc(raw)
      widenedBinding(b, it) match
        case None       => loopWithJumps(body, lbl, bd => s"for ($name <- ${term(it, i)}) $bd", term(body, i))
        case Some(decl) =>
          // the alias is INSIDE the loop body, so it is re-bound each iteration exactly as java's is,
          // and outside any `continue` boundary `loopWithJumps` adds — which is where java runs it.
          // Derive the fresh name from the RAW one and escape THAT: appending to the escaped form
          // gives `` `object`$e ``, which is not an identifier at all (measured, 0 -> 3 on libGDX,
          // as an E040 syntax error). A suffixed keyword needs no escape, so `esc` is a no-op here —
          // but only because it is applied to the whole name.
          val fresh = esc(s"$raw$$e")
          loopWithJumps(body, lbl,
            bd => s"for ($fresh <- ${term(it, i)}) { val $name: $decl = $fresh.asInstanceOf[$decl]; $bd }",
            term(body, i))
    case Tree.For(init, cond, upd, body, _, _, lbl) =>
      // the UPDATE must run on a `continue` too, so it sits OUTSIDE the per-iteration boundary —
      // which is exactly where java's `for` runs it.
      val is = init.map(stat(_, 0)).mkString("; ")
      val c  = cond.map(term(_, i)).getOrElse("true")
      val u  = upd.map(stat(_, 0)).mkString("; ")
      loopWithJumps(body, lbl, bd => s"{ $is; while ($c) { $bd; $u } }", term(body, i))
    case Tree.Try(res, body, catches, fin, _, _) => tryStr(res, body, catches, fin, i)
    case Tree.Match(scr, cases, _, _)   => inSwitch(matchStr(scr, cases, i))
    case Tree.MethodRef(q, s, mrT, _)   =>
      val isCtor = sym(s).name == "<init>" // `Type::new` → a factory function `() => new Type()`
      q match
        // an ARRAY constructor reference `T[]::new` is an `IntFunction[T[]]` — `(size) => new T[size]`
        // (Scala arrays need a length), NOT a no-arg supplier. One-layer element = the array's row type.
        // a constructor reference must name an INSTANTIABLE type: `new T[?]()` is rejected
        // ("type argument must be fully defined"), so route through `ctorTpe`, which drops
        // wildcard arguments and lets Scala infer them — and erase a wildcard array element to
        // `Object`, which is what Java's raw `T[]::new` means anyway.
        case Left(tt) if isCtor => tt.tpe match
          case TypeRepr.AppliedType(TypeRepr.TypeRef(_, as), List(el)) if sym(as).fullName == "scala.Array" =>
            val elem = el match
              case _: TypeRepr.TypeBounds => "java.lang.Object"
              case other                  => tpe(other)
            s"((size: scala.Int) => new scala.Array[$elem](size))"
          case _ => samAscribed(s"(() => new ${ctorTpe(tt.tpe)}())", mrT, tt.tpe)
        // `Type::method` is TWO different java forms sharing one syntax, and only one of them is a
        // qualified name. For a STATIC method it is `Type.method`. For an INSTANCE method it is an
        // UNBOUND reference — the receiver becomes the function's first parameter, so
        // `Edge<V>::getWeight` means `(self: Edge[V]) => self.getWeight()`. Emitted as a name it is
        // `sge.graphs.Edge[V].getWeight`, which is not even a member access: measured as
        // `value Edge is not a member of sge.graphs` in simple-graphs' MinimumWeightSpanningTree,
        // where the reference is a `Comparator` key extractor.
        case Left(tt) if sym(s).flags.isStatic => s"${tpe(tt.tpe)}.${local(s)}"
        case Left(tt) =>
          val self  = "self$"
          val extra = methodParams(s).zipWithIndex.map((pt, k) => s"a$k$$: ${tpe(pt)}")
          val ps    = (s"$self: ${tpe(tt.tpe)}" :: extra).mkString(", ")
          val as    = methodParams(s).indices.map(k => s"a$k$$").mkString(", ")
          samAscribed(s"(($ps) => $self.${local(s)}($as))", mrT, tt.tpe)
        case Right(e)           => s"${term(e, i)}.${local(s)}"
    // Java's `break` leaves the loop; emitted as a no-op it did NOT, and the loop ran on.
    // `CharArray.deleteAll` scanned to the end of the array instead of stopping at the first
    // non-matching char and deleted most of the string. 290 sites, 73 files, all compiling.
    // Scala 3's `boundary`/`break` is the faithful shape, and is what the reference port uses.
    // LABELLED breaks are NOT covered — they still emit the no-op, and the count above is the
    // measure of what is left.
    case Tree.Break(scala.None, _, _) if breakTarget.isDefined =>
      breakTarget.filter(_.nonEmpty) match
        case Some(n) => s"scala.util.boundary.break(())(using $n)" // a body boundary sits inside
        case _       => "scala.util.boundary.break(())"
    // a `break` with no boundary around it belongs to a SWITCH, where it means "end this case" —
    // which scala's `match` does anyway. LABELLED jumps are not covered; the emitted-comment
    // counts are the measure of what is left.
    case Tree.Break(Some(l), _, _) if labelBreak.contains(l) =>
      val n = labelBreak(l)
      if n.isEmpty then "scala.util.boundary.break(())" else s"scala.util.boundary.break(())(using $n)"
    case Tree.Break(_, _, _)            => "/* break */ ()"
    case Tree.Continue(scala.None, _, _) if contBoundary => "scala.util.boundary.break(())"
    case Tree.Continue(Some(l), _, _) if labelCont.contains(l) =>
      val n = labelCont(l)
      if n.isEmpty then "scala.util.boundary.break(())" else s"scala.util.boundary.break(())(using $n)"
    case Tree.Continue(_, _, _)         => "/* continue */ ()" // TODO: scala.util.boundary
    case Tree.Assert(c, m, _, _)        => s"assert(${term(c, i)}${m.map(x => ", " + term(x, i)).getOrElse("")})"
    // Java's POST-increment yields the value BEFORE the update; the pre-form yields it after.
    // Rendered identically, `values[tail++] = object` stored at the NEW index — every circular
    // buffer in the corpus was off by one, `Queue.indexOf` among them, and it compiled. The
    // temporary is what makes the post-form exact; the target is still re-evaluated for the
    // assignment, exactly as the pre-form already did.
    case Tree.IncDec(tgt, op, post, _, _) =>
      if post then s"{ val ${'$'}prev = ${term(tgt, i)}; ${term(tgt, i)} $op= 1; ${'$'}prev }"
      else s"{ ${term(tgt, i)} $op= 1; ${term(tgt, i)} }"
    case Tree.DoWhile(b, c, _, _, lbl)  => // Scala 3 has no do-while
      loopWithJumps(b, lbl, bd => s"while ({ $bd; ${term(c, i)} }) ()", term(b, i))
    case Tree.Synchronized(l, b, _, _)  => s"${term(l, i)}.synchronized ${term(b, i)}"
    case Tree.Opaque(raw, _, _)         => raw

  /** A Java constructor reference (`Foo::new`) is typed by the TARGET functional interface Java
    * resolved, not by `Foo`. Emitted bare, `() => new Foo()` is a `Function0`, which Scala
    * SAM-converts to ANY single-abstract-method type — so an overload set offering two of them
    * (`PoolManager.addPool(Class, Pool)` vs `(Class, PoolSupplier)`) becomes AMBIGUOUS where
    * Java's was not. Re-state the resolved target as an ascription.
    *
    * Strictly guarded, because the ascription is only sound when the frontend really gave us the
    * functional interface: the type must be concrete (no type variables, no `NoType`) and must not
    * be the constructed type itself. Anything else falls back to the bare lambda — the previous
    * behaviour — so this can only ever narrow, never mis-type. */
  private def samAscribed(fn: String, target: TypeRepr, ctor: TypeRepr): String =
    def headOf(t: TypeRepr): Option[SymId] = t match
      case TypeRepr.TypeRef(_, s)      => Some(s)
      case TypeRepr.AppliedType(tc, _) => headOf(tc)
      case _                           => None
    def concrete(t: TypeRepr): Boolean = t match
      case TypeRepr.TypeRef(_, s)       => !sym(s).flags.isParam
      case TypeRepr.AppliedType(tc, as) => concrete(tc) && as.forall(arg)
      case _                            => false
    // a bare `?` is a legal type ARGUMENT (`PoolSupplier[Array[?]]`) though not a legal type
    def arg(t: TypeRepr): Boolean = t match
      case TypeRepr.TypeBounds(TypeRepr.NoType, TypeRepr.NoType) => true
      case other                                                 => concrete(other)
    val ok = concrete(target) && headOf(target) != headOf(ctor)
    if ok then s"($fn: ${tpe(target)})" else fn

  /** a STATIC member reached through an instance expression rather than through its own type. */
  private def staticThroughInstance(recv: Term, m: SymId): Boolean =
    val s = sym(m)
    s.flags.isStatic && s.owner != SymId.None && program.symbolOf(s.owner).isDefined && (recv match
      // already qualified by the owning TYPE — `Family.one(…)` — which is what we want to emit.
      case Tree.Ident(q, _, _)     => q != s.owner
      case Tree.Select(_, q, _, _) => q != s.owner
      case _                       => true)

  /** conservatively: can evaluating this term have an effect? Only shapes that provably cannot are
    * treated as free, because being wrong in the other direction DROPS an effect. */
  private def effectFree(t: Term): Boolean = t match
    case _: Tree.Ident | _: Tree.This | _: Tree.Literal => true
    case Tree.Select(q, _, _, _)                        => effectFree(q)
    case _                                              => false

  private def applyStr(fun: Term, args: List[Term], i: Int): String = fun match
    case Tree.New(tpt, _, _, anon) =>
      s"new ${ctorTpe(tpt.tpe)}(${args.map(term(_, i)).mkString(", ")})${anonBody(anon, i)}"
    // operators (populator tags them `scala.<op>#…`) render infix / prefix, not `.op(x)`.
    case Tree.Select(recv, m, _, _) if sym(m).fullName.startsWith("scala.<op>#") =>
      val op = sym(m).name
      if op.startsWith("unary_") then s"${op.stripPrefix("unary_")}${operand(recv, i)}"
      else s"${operand(recv, i)} $op ${args.map(operand(_, i)).mkString(", ")}"
    case Tree.Select(recv, m, _, _) if sym(m).name == "<init>" =>
      val kw = recv match { case _: Tree.Super => "super"; case _ => "this" }
      s"$kw(${args.map(term(_, i)).mkString(", ")})"
    // JAVA PERMITS A STATIC MEMBER TO BE CALLED THROUGH AN INSTANCE — `family.one(…)` where `one`
    // is `static`. Scala does not: a static emits into the companion, which an instance cannot
    // reach. Java evaluates the receiver and DISCARDS it, so the faithful rendering keeps the
    // receiver's effects and calls the static on its owner.
    //
    // A receiver that cannot have effects (a name, `this`, a field read) is simply dropped; one
    // that can (a call, a `new`) is evaluated first in a block, because silently discarding an
    // effect is precisely the class of defect a green compile hides (CLAUDE.md §4.4).
    //
    // Worked example: Ashley's `Family.all(A, B).get().one(C, D)` — `get()` returns a `Family` and
    // `one` is static on it, which javac accepts with a warning and scalac rejects outright.
    case Tree.Select(recv, m, _, _) if staticThroughInstance(recv, m) =>
      val call = s"${typeValue(sym(m).owner)}.${local(m)}(${args.map(term(_, i)).mkString(", ")})"
      if effectFree(recv) then call else s"{ ${term(recv, i)}; $call }"
    case Tree.Select(_, m, _, _) if numericOverloadAscription(m).isDefined =>
      s"(${term(fun, i)}: ${numericOverloadAscription(m).get})(${args.map(term(_, i)).mkString(", ")})"
    case _ =>
      val as = (fun match
        case Tree.Select(_, m, _, _) => alignedArgs(m, args, i)
        case Tree.Ident(m, _, _)     => alignedArgs(m, args, i)
        case _                       => scala.None
      ).getOrElse(args.map(term(_, i)))
      s"${term(fun, i)}(${as.mkString(", ")})"

  /** widening rank — a value of rank r converts implicitly to any numeric type of higher rank.
    * `Char` and `Short` share a rank because neither widens to the other. */
  private val numericRank = Map("scala.Byte" -> 1, "scala.Short" -> 2, "scala.Char" -> 2,
                                "scala.Int" -> 3, "scala.Long" -> 4, "scala.Float" -> 5,
                                "scala.Double" -> 6)

  /** Java resolves an overload by EXACT match; Scala widens numerics first and then finds no
    * most-specific alternative.
    *
    * `Sprite.setRegion(int, int, int, int)` and `setRegion(float, float, float, float)` are both
    * applicable to four `Int` arguments — Java simply picks the `int` one, Scala reports an
    * ambiguity. Ascribing the method's function type names the alternative Java chose:
    * `(this.setRegion: (Int, Int, Int, Int) => Unit)(x, y, w, h)`.
    *
    * Fires only where the clash actually exists: a sibling of the same name and arity is WEAKLY
    * WIDER at every position and strictly wider at one, so the very same arguments reach it by
    * widening. `append(int)` beside `append(char)` is not that shape — `char` does not absorb an
    * `int` — and needs no help. Checking the direction is what keeps this from ascribing every
    * numeric call in the program (measured: 175 sites where 1 was ambiguous). */
  private def numericOverloadAscription(m: SymId): Option[String] =
    def numericParams(d: Tree.DefDef): Option[List[TypeRepr]] =
      val ps = d.paramss.flatten.map(_.tpt.tpe)
      Option.when(ps.nonEmpty && ps.forall(p => headSymOf(p).exists(s => numericRank.contains(sym(s).fullName))))(ps)
    def rank(t: TypeRepr): Int = headSymOf(t).flatMap(s => numericRank.get(sym(s).fullName)).getOrElse(0)
    def absorbs(wider: List[TypeRepr], here: List[TypeRepr]): Boolean =
      wider.sizeIs == here.size &&
        wider.zip(here).forall((w, h) => w == h || rank(w) > rank(h)) &&
        wider.zip(here).exists((w, h) => w != h)
    for
      d      <- program.definitionOf(m).collect { case d: Tree.DefDef => d }
      ps     <- numericParams(d)
      owner  <- program.definitionOf(sym(m).owner).collect { case c: Tree.ClassDef => c }
      if owner.body.exists {
        case o: Tree.DefDef =>
          o.symbol != m && sym(o.symbol).name == sym(m).name &&
            numericParams(o).exists(absorbs(_, ps))
        case _ => false
      }
    yield s"(${ps.map(tpe).mkString(", ")}) => ${tpe(d.returnTpt.tpe)}"

  /** A Java anonymous class's body → Scala's anonymous-class expression `new Base(args) { … }`.
    *
    * The anonymous class's own symbol is pushed on `classStack` while its members render, which is
    * what makes `thisRef` qualify an enclosing reference as `Outer.this.m`: inside a Scala
    * anonymous class the bare `this` is the anonymous instance, exactly as in Java, so an enclosing
    * member reached implicitly must be named through the outer instance. Captured locals need no
    * lowering at all — Scala closes over them where javac had to synthesise ctor parameters.
    *
    * `Some(Nil)` (the super-type-token idiom `new Base(){}`) still renders the braces: `new Base()`
    * and `new Base(){}` are DIFFERENT types, and only the latter has the reified supertype. */
  private def anonBody(anon: Option[Tree.AnonClass], i: Int): String = anon match
    case None    => ""
    case Some(a) =>
      classStack.append(a.symbol)
      val members = try a.body.map(stat(_, i + 1)).filter(_.trim.nonEmpty) finally classStack.removeLast()
      if members.isEmpty then " {}" else s" {\n${joinStats(members)}\n${ind(i)}}"

  /** parenthesize a term when it is an operand, where bare juxtaposition would misparse:
    * an operator application (precedence) and any control-flow expression — `if`/`match`
    * as an operand (`a + if (c) x else y`) needs parens or Scala reads "end of statement". */
  private def operand(t: Term, i: Int): String = t match
    case Tree.Apply(Tree.Select(_, m, _, _), _, _, _, _) if sym(m).fullName.startsWith("scala.<op>#") =>
      s"(${term(t, i)})"
    case _: Tree.If | _: Tree.Match | _: Tree.Lambda => s"(${term(t, i)})"
    case _ => term(t, i)

  private def block(stats: List[Statement], expr: Term, i: Int): String =
    // drop a redundant trailing `()` when the block already has statements (Java void bodies).
    val tail = expr match
      case Tree.Literal(Constant.UnitC, _, _) if stats.nonEmpty => Nil
      case _                                                    => List(ind(i + 1) + term(expr, i + 1))
    val lines = (stats.map(stat(_, i + 1)) ++ tail).filter(_.trim.nonEmpty)
    s"{\n${joinStats(lines)}\n${ind(i)}}"

  /** join block statements, terminating one with `;` when the NEXT begins with `{` — otherwise
    * Scala greedily reads `new T(a)\n{ … }` as an anonymous-class body rather than two statements. */
  private def joinStats(lines: List[String]): String = lines match
    case Nil => ""
    case h :: t =>
      val sb = new StringBuilder(h)
      t.foreach { l => if l.trim.startsWith("{") then sb.append(";"); sb.append("\n").append(l) }
      sb.toString

  private def tryStr(res: List[Tree.ValDef], body: Term, catches: List[Tree.CatchCase], fin: Option[Term], i: Int): String =
    val r  = res.map(v => s"${ind(i + 1)}${valDef(v, 0)}\n").mkString
    val cs = catches.map(c => s"${ind(i + 1)}case ${esc(sym(c.param.symbol).name)}: ${tpe(c.param.tpt.tpe)} => ${term(c.body, i + 1)}").mkString("\n")
    val cl = if catches.isEmpty then "" else s" catch {\n$cs\n${ind(i)}}"
    val fl = fin.map(f => s" finally ${term(f, i)}").getOrElse("")
    s"try ${term(body, i)}$cl$fl" // resources: r prepended when the backend lowers auto-close

  private def matchStr(scr: Term, cases: List[Tree.CaseDef], i: Int): String =
    val cs = cases.map { c =>
      val pat = if c.isDefault then "_" else c.labels.map(term(_, i)).mkString(" | ")
      s"${ind(i + 1)}case $pat => ${term(c.body, i + 1)}"
    }.mkString("\n")
    s"${term(scr, i)} match {\n$cs\n${ind(i)}}"

  // ---- types ----
  /** a type in `new` position: `new Foo[?]` is illegal (you can't instantiate a wildcard), so
    * when a raw generic type carries wildcard args, drop them and let Scala infer the arguments
    * from the expected type (`new Foo(...)`). */
  /** As in `parentTpe`, only the HEAD is a `namedInner` position — the arguments are ordinary. */
  private def ctorTpe(t: TypeRepr): String = t match
    case TypeRepr.AppliedType(tc, args) if args.exists(_.isInstanceOf[TypeRepr.TypeBounds]) => byName(tpe(tc))
    case TypeRepr.AppliedType(tc, args) => s"${byName(tpe(tc))}[${args.map(tpe).mkString(", ")}]"
    case _ => byName(tpe(t))

  /** strip `scala.Array[...]` layers to the base element type (for `Array.ofDim[base](dims)`). */
  private def baseElem(t: TypeRepr): TypeRepr = t match
    case TypeRepr.AppliedType(TypeRepr.TypeRef(_, s), List(e)) if sym(s).fullName == "scala.Array" => baseElem(e)
    case _ => t

  private def tpe(t: TypeRepr): String = t match
    case TypeRepr.NoType | TypeRepr.NoPrefix   => "Any"
    case TypeRepr.TypeRef(_, s)                => typeSym(s)
    case TypeRepr.TermRef(_, s)                => s"${typeSym(s)}.type"
    case TypeRepr.ThisType(_)                  => "this.type"
    case TypeRepr.SuperType(_, sup)            => tpe(sup)
    case TypeRepr.ConstantType(c)              => constant(c)
    case TypeRepr.AppliedType(tc, as)          => s"${tpe(tc)}[${as.map(tpe).mkString(", ")}]"
    case TypeRepr.AndType(l, r)                => s"${tpe(l)} & ${tpe(r)}"
    case TypeRepr.OrType(l, r)                 => s"${tpe(l)} | ${tpe(r)}"
    case TypeRepr.ByNameType(u)                => s"=> ${tpe(u)}"
    case TypeRepr.TypeBounds(TypeRepr.NoType, TypeRepr.NoType) => "?"
    case TypeRepr.TypeBounds(lo, hi) =>
      val l = if lo == TypeRepr.NoType then "" else s" >: ${tpe(lo)}"
      val h = if hi == TypeRepr.NoType then "" else s" <: ${tpe(hi)}"
      s"?$l$h"
    case TypeRepr.Refinement(p, _, _)          => tpe(p)
    case TypeRepr.MethodType(ps, res, _)       => s"(${ps.map((_, pt) => tpe(pt)).mkString(", ")}) => ${tpe(res)}"
    case TypeRepr.PolyType(_, res)             => tpe(res)
    case TypeRepr.TypeLambda(ps, body)         => s"[${ps.map(_._1).mkString(", ")}] =>> ${tpe(body)}"
    case TypeRepr.ParamRef(_, _)               => "?"

  // ---- constants ----
  private def constant(c: Constant): String = c match
    case Constant.BoolC(v)   => v.toString
    case Constant.ByteC(v)   => v.toString
    case Constant.ShortC(v)  => v.toString
    case Constant.IntC(v)    => v.toString
    case Constant.LongC(v)   => s"${v}L"
    case Constant.FloatC(v)  => s"${v}f"
    case Constant.DoubleC(v) => v.toString
    case Constant.CharC('\'') => "'\\''" // a single-quote char must be escaped inside `'…'`
    case Constant.CharC(v)   => s"'${escape(v.toString)}'"
    case Constant.StringC(v) => "\"" + escape(v) + "\""
    case Constant.NullC      => "null"
    case Constant.UnitC      => "()"
    case Constant.ClassOfC(t) => s"classOf[${tpe(t)}]"

  private def escape(s: String): String =
    s.flatMap {
      case '\\' => "\\\\"; case '"' => "\\\""; case '\n' => "\\n"; case '\r' => "\\r"; case '\t' => "\\t"
      case c    => c.toString
    }

object TirEmitter:
  /** Drop `private` from the given members. Java lets a parent constructor write its own private
    * fields; when those statements are REPLAYED one level down (see `CtorFunnel.replayFor`) they
    * execute in the subclass, where `private` no longer reaches. Widening visibility can only
    * remove access errors, never introduce one, and never changes behaviour. */
  def widen(p: Program, members: Set[SymId]): Program =
    if members.isEmpty then p
    else
      val syms = p.symbols.all.map(s =>
        if members(s.id) then s.copy(flags = s.flags.copy(isPrivate = false)) else s
      )
      new Program(p.units, SymbolTable(syms), p.xref)

  /** Promoting a constructor to Scala's primary widens the SCOPE of everything it declares: its
    * parameters become class parameters and its top-level locals become class members, both
    * visible to the whole body instead of to the constructor alone. That is the only hazard in
    * the promotion, and it has two faces — a name shared with one of the class's own members is
    * a double definition, and a name shared with an INHERITED member silently captures every
    * unqualified read of it (`this.viewport = viewport` still works; a bare `viewport` no longer
    * means the field). Suffixing `$p` removes both: parameters are positional and the locals are
    * unreachable from outside, so the rename is invisible everywhere it matters.
    */
  def funnelParamRenames(p: Program): Program =
    val renames = collection.mutable.Map[SymId, String]()
    val plans = CtorFunnel.Plans(p)
    def nm(id: SymId): String = p.symbolOf(id).map(_.name).getOrElse("")
    def parentSyms(cd: Tree.ClassDef): List[SymId] =
      def hs(t: TypeRepr): Option[SymId] = t match
        case TypeRepr.TypeRef(_, s)      => Some(s)
        case TypeRepr.AppliedType(tc, _) => hs(tc)
        case _                           => scala.None
      cd.parents.flatMap { case tt: TypeTree => hs(tt.tpe); case t: Term => hs(t.tpe) }
    val declOf   = collection.mutable.Map[SymId, Tree.ClassDef]()
    def index(cd: Tree.ClassDef): Unit =
      declOf(cd.symbol) = cd
      cd.body.foreach { case c: Tree.ClassDef => index(c); case _ => () }
    p.units.foreach(index)
    // EFFECTIVE names: a parent's promoted param already renamed to `attributes$p` must read as
    // TAKEN here, or the child renames its own `attributes` to the same thing and the collision
    // simply moves up a level (measured on `DepthShader extends DefaultShader`). Requires the
    // parents-first scan below.
    def eff(id: SymId): String = renames.getOrElse(id, nm(id))
    def ownNames(cd: Tree.ClassDef): Set[String] =
      cd.body.collect {
        case d: Tree.DefDef if nm(d.symbol) != "<init>" => eff(d.symbol)
        case v: Tree.ValDef                             => eff(v.symbol)
        case c: Tree.ClassDef                           => eff(c.symbol)
      }.toSet ++ widenedOf(cd).map(v => eff(v.symbol))
    /** everything this class's promoted constructor contributes to the class BODY — its params and
      * its top-level locals. Neither is in `cd.body`, and both become members, so both are names a
      * SUBCLASS must avoid: `DepthShader extends DefaultShader` promotes the same two constructor
      * locals and landed on `attributes$p` twice. */
    def widenedOf(cd: Tree.ClassDef): List[Tree.ValDef] =
      val pl = plans(cd)
      pl.primaryParams ++ pl.primaryBody.collect { case v: Tree.ValDef => v }
    def visibleNames(cd: Tree.ClassDef, seen: Set[SymId] = Set.empty): Set[String] =
      if seen(cd.symbol) then Set.empty
      else ownNames(cd) ++ parentSyms(cd).flatMap(declOf.get).flatMap(visibleNames(_, seen + cd.symbol))
    val scanned = collection.mutable.Set[SymId]()
    def scan(cd: Tree.ClassDef): Unit =
      if scanned(cd.symbol) then return
      scanned += cd.symbol
      parentSyms(cd).flatMap(declOf.get).foreach(scan) // parents first, so `eff` is settled
      val plan = plans(cd)
      if plan.primary.isDefined then
        val taken = collection.mutable.Set.from(visibleNames(cd))
        // the promoted constructor's params, then the top-level locals of its body (nested
        // blocks keep their own scope and never reach the class body)
        val widened = widenedOf(cd)
        widened.foreach { v =>
          val n = nm(v.symbol)
          if taken(n) then
            var fresh = n + "$p"
            while taken(fresh) do fresh += "$"
            taken += fresh
            renames(v.symbol) = fresh
        }
      cd.body.foreach { case c: Tree.ClassDef => scan(c); case _ => () }
    p.units.foreach(scan)
    if renames.isEmpty then p
    else new Program(p.units, SymbolTable(p.symbols.all.map(s => renames.get(s.id).map(n => s.copy(name = n)).getOrElse(s))), p.xref)

  /** Rename any field that SHADOWS an inherited member.
    *
    * Java fields shadow rather than override, and are resolved by the STATIC type of the receiver:
    * `ParallelArray.Channel` declares `Object data` and `FloatChannel extends Channel` declares
    * `float[] data`, so both objects exist and `((Channel) fc).data` and `fc.data` are different
    * storage. Scala has no such thing — a `var` cannot be overridden at all, let alone at an
    * incompatible type — and there is no rendering that keeps one name.
    *
    * So the shadowing field gets a fresh name. That is exact rather than approximate precisely
    * BECAUSE Java resolved these statically: every reference in the TIR already points at the
    * symbol Java chose, so renaming the symbol re-points exactly the references Java meant and no
    * others. Confirmed against the reference port, which does the same by hand and says why:
    * "renamed to floatData/intData/objectData … Java shadowed the field; Scala can't".
    *
    * Fields shadowing an inherited METHOD (`TextField`'s `layout` field under `Widget.layout()`)
    * are the same defect through Java's separate namespaces for the two, and are renamed here too.
    * Statics are exempt: they land in the companion, which inherits nothing. */
  def resolveFieldShadowing(p: Program): Program =
    val renames = collection.mutable.Map[SymId, String]()
    def nm(id: SymId): String = p.symbolOf(id).map(_.name).getOrElse("")
    def headSym(t: TypeRepr): Option[SymId] = t match
      case TypeRepr.TypeRef(_, s) => Some(s); case TypeRepr.AppliedType(tc, _) => headSym(tc); case _ => None
    val declOf  = collection.mutable.Map[SymId, Tree.ClassDef]()
    def index(cd: Tree.ClassDef): Unit =
      declOf(cd.symbol) = cd
      cd.body.foreach { case c: Tree.ClassDef => index(c); case _ => () }
      cd.enumCases.foreach(_.body.foreach { case c: Tree.ClassDef => index(c); case _ => () })
    p.units.foreach(index)
    /** EFFECTIVE names — a renamed ancestor field contributes its NEW name, so a descendant asking
      * "is this taken?" sees what will actually be emitted. Requires parents-first scanning. */
    def eff(id: SymId): String = renames.getOrElse(id, nm(id))
    def instanceMembers(cd: Tree.ClassDef): Set[String] =
      cd.body.collect {
        case d: Tree.DefDef if nm(d.symbol) != "<init>" && !p.symbolOf(d.symbol).exists(_.flags.isStatic) => eff(d.symbol)
        case v: Tree.ValDef if !p.symbolOf(v.symbol).exists(_.flags.isStatic)                             => eff(v.symbol)
      }.toSet
    def inherited(cd: Tree.ClassDef, seen: Set[SymId] = Set.empty): Set[String] =
      cd.parents.flatMap { case tt: TypeTree => headSym(tt.tpe); case t: Term => headSym(t.tpe) }
        .filterNot(seen)
        .flatMap(declOf.get)
        .flatMap(pcd => instanceMembers(pcd) ++ inherited(pcd, seen + cd.symbol))
        .toSet
    val scanned = collection.mutable.Set[SymId]()
    def scan(cd: Tree.ClassDef): Unit =
      if scanned(cd.symbol) then return
      scanned += cd.symbol
      // parents FIRST, so `eff` above already reflects an ancestor's rename
      cd.parents.flatMap { case tt: TypeTree => headSym(tt.tpe); case t: Term => headSym(t.tpe) }
        .flatMap(declOf.get).foreach(scan)
      val shadowed = inherited(cd)
      cd.body.foreach {
        case v: Tree.ValDef if shadowed(nm(v.symbol)) && !p.symbolOf(v.symbol).exists(_.flags.isStatic) =>
          // The fresh name must not ITSELF be inherited. `CheckBox.style` shadows
          // `TextButton.style`, which shadows `Button.style` — renaming both to `style$shadow`
          // just relocated the collision one level up. Keep appending until the name is free
          // (the same idiom `funnelParamRenames` uses).
          var fresh = nm(v.symbol) + "$shadow"
          while shadowed(fresh) do fresh += "$"
          renames(v.symbol) = fresh
        case c: Tree.ClassDef => scan(c)
        case _                => ()
      }
      cd.enumCases.foreach(_.body.foreach { case c: Tree.ClassDef => scan(c); case _ => () })
    p.units.foreach(scan)
    if renames.isEmpty then p
    else
      // same visibility relaxation as `resolveMemberClashes`: a renamed field must stay reachable
      // from wherever java read it.
      val syms = p.symbols.all.map(s =>
        renames.get(s.id).map(n => s.copy(name = n, flags = s.flags.copy(isPrivate = false, isProtected = false))).getOrElse(s)
      )
      new Program(p.units, SymbolTable(syms), p.xref)

  /** Rename any field whose simple name collides with a method in the same class (legal in
    * Java, illegal in Scala) by suffixing `$field`. Renaming the symbol propagates to every
    * reference, since the emitter reads names from the symbol table. */
  def resolveMemberClashes(p: Program): Program =
    val renames = collection.mutable.Map[SymId, String]()
    def nm(id: SymId): String = p.symbolOf(id).map(_.name).getOrElse("")
    def headSym(t: TypeRepr): Option[SymId] = t match
      case TypeRepr.TypeRef(_, s) => Some(s); case TypeRepr.AppliedType(tc, _) => headSym(tc); case _ => None
    // per-class method names, and the parent edges — a Java field can coexist with a same-named
    // METHOD in a SUBCLASS (`hasNext` field + `hasNext()` from Iterator), which Scala forbids.
    val methodsOf = collection.mutable.Map[SymId, Set[String]]()
    val childrenOf = collection.mutable.Map[SymId, List[SymId]]().withDefaultValue(Nil)
    def index(cd: Tree.ClassDef): Unit =
      methodsOf(cd.symbol) = cd.body.collect { case d: Tree.DefDef => nm(d.symbol) }.toSet
      cd.parents.foreach { case tt: TypeTree => headSym(tt.tpe).foreach(pp => childrenOf(pp) = cd.symbol :: childrenOf(pp)); case _ => () }
      cd.body.foreach { case c: Tree.ClassDef => index(c); case _ => () }
    p.units.foreach(index)
    def selfOrDescMethods(c: SymId, seen: Set[SymId] = Set.empty): Set[String] =
      if seen(c) then Set.empty
      else methodsOf.getOrElse(c, Set.empty) ++ childrenOf(c).flatMap(ch => selfOrDescMethods(ch, seen + c))
    def scan(cd: Tree.ClassDef): Unit =
      val clashNames = selfOrDescMethods(cd.symbol)
      cd.body.foreach {
        case v: Tree.ValDef if clashNames(nm(v.symbol)) => renames(v.symbol) = nm(v.symbol) + "$field"
        case c: Tree.ClassDef                           => scan(c)
        case _                                           => ()
      }
      cd.enumCases.foreach(_.body.foreach { case c: Tree.ClassDef => scan(c); case _ => () })
    p.units.foreach(scan)
    if renames.isEmpty then p
    else
      // also relax visibility: Java lets the enclosing class read a nested class's private
      // field (`point.x`); Scala does not, so a renamed clash-field must stay accessible.
      val syms = p.symbols.all.map(s =>
        renames.get(s.id).map(n => s.copy(name = n, flags = s.flags.copy(isPrivate = false, isProtected = false))).getOrElse(s)
      )
      new Program(p.units, SymbolTable(syms), p.xref)
