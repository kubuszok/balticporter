package balticporter.emit

import balticporter.catalog.{CatalogLog, JS, Obligations, Rendering, Typing}
import balticporter.core.{EngineInfo, Provenance, Substituted}
import balticporter.tir.*

/** Source map / trivia recovery / porter-note bookkeeping / statics & naming helpers split out of TirEmitter (context diet S1). */
private[emit] trait TirEmitterNotes:
  self: TirEmitter =>

  // Source map: member -> emitted line range -> Java Origin. // DESIGN.md §6.3
  // Positions recovered by searching finished text for remembered slot strings (pre-order).

  private[emit] final class Slot(val member: String, val kind: String, val origin: Origin, val indent: Int):
    var text: String = ""
  private[emit] val slots   = collection.mutable.ArrayBuffer.empty[Slot]
  private[emit] val stmtSeq = collection.mutable.Map.empty[String, Int]

  /** Like [[stat]] but records the rendered text for source-map and comment recovery. */
  private[emit] def memberStat(s: Statement, i: Int): String =
    val slot = new Slot(memberKey(s), memberKind(s), s.origin, i)
    slots += slot
    recordMemberShape(slot.member, s)
    val t = stat(s, i)
    slot.text = t
    t

  /** Record member's contract row (DESIGN.md §8.3), keyed by source-map key. */
  private[emit] def recordMemberShape(key: String, st: Statement): Unit =
    val symId = st match
      case d: Definition => Some(d.symbol)
      case _             => scala.None
    symId.foreach { id =>
      val m = sym(id)
      recordedMemberShapes(key) = Surface.MemberShape(
        // Emitted simple name, only where it differs from Java's.
        name      = renamedMembers.get(id).filter(_ != m.name).map(_ => m.name).getOrElse(""),
        vis       = visOf(m, currentOwnerSym),
        // Static members land in the companion.
        placement = if m.flags.isStatic then "companion" else "class",
        // Whether this member is a collapsed bean pair and into which shape.
        form      = collapsedForms.getOrElse(id, ""),
      )
    }

  /** Collapsed bean properties by symbol, from phase decisions (not emitter's own renames). */
  private[emit] lazy val collapsedForms: Map[SymId, String] =
    notes.all.iterator.collect {
      case d if d.kind == Decision.Kind.CollapsedProperty && d.subject != SymId.None &&
                d.detail.get("form").exists(_.nonEmpty) =>
        d.subject -> d.detail("form")
      // NullaryArity: dropped `()` is `form=parenless`.
      case d if d.kind == Decision.Kind.ParenlessConversion && d.subject != SymId.None =>
        d.subject -> "parenless"
    }.toMap

  /** Stable member identity: `owner#name(paramTypes)` for defs, `owner#name` for others,
    * ordinal for unsymboled statements. */
  private[emit] def memberKey(s: Statement): String =
    val owner = classStack.lastOption.map(x => sym(x).fullName).getOrElse("?")
    s match
      case d: Tree.DefDef if !isInitBlock(d) =>
        s"${sym(d.symbol).fullName}(${d.paramss.flatten.map(v => shortTpe(v.tpt.tpe)).mkString(",")})"
      case d: Definition => sym(d.symbol).fullName
      case _ =>
        val n = stmtSeq.getOrElse(owner, 0) + 1
        stmtSeq(owner) = n
        s"$owner#<stmt$n>"

  private[emit] def memberKind(s: Statement): String = s match
    case _: Tree.ClassDef => "class"
    case d: Tree.DefDef   =>
      if isInitBlock(d) then "init" else if sym(d.symbol).name == "<init>" then "ctor" else "def"
    case _: Tree.ValDef  => "val"
    case _: Tree.TypeDef => "type"
    case _               => "stmt"

  /** Simple type rendering for overload disambiguation in member keys. */
  private[emit] def shortTpe(t: TypeRepr): String = t match
    case TypeRepr.AppliedType(tc, as) if as.nonEmpty => shortTpe(tc) + as.map(shortTpe).mkString("<", ",", ">")
    case _                                           => headSymOf(t).map(x => sym(x).name).getOrElse("?")

  // Recovery backstop (DESIGN.md §8.8): unplaced Java comments are put back after the
  // enclosing member slot, with java coordinates. Dropped members' comments are not recovered.

  /** All declarations (emitted and dropped) per java file, computed once. */
  private[emit] lazy val anchorMembers: Map[String, List[CommentAnchor.Member]] = CommentAnchor.membersOf(program)

  private[emit] def recoverTrivia(cd: Tree.ClassDef, text: String): String =
    val path = cd.origin.javaPath
    if path.isEmpty || path == "<synthetic>" || path == "<unknown>" then text
    else javaSource(path) match
      case scala.None                 => text
      case Some(java) if java.isEmpty => text
      case Some(java) =>
        // WHICH comments are this unit's. A java file may declare several top-level types and
        // becomes that many scala files; without a window each of them would recover the others'
        // comments, and the same comment would land in every one.
        val here    = program.units.filter(_.origin.javaPath == path).sortBy(_.origin.line)
        val idx     = here.indexWhere(_.symbol == cd.symbol)
        val from    = if idx <= 0 then 0 else cd.origin.line
        val until   = if idx >= 0 && idx + 1 < here.size then here(idx + 1).origin.line else Int.MaxValue
        val members = anchorMembers.getOrElse(CommentAnchor.key(path), Nil)
        val lines   = java.linesIterator.toArray
        // PRESENCE is tested through the check's own normalisation — the shared function, never a
        // fork of it, or the emitter and the check disagree about what "already there" means. And
        // the engine's own commentary is stripped first: a porter note names an upstream FQN on
        // purpose, and a marker names an upstream PATH, so either can match a comment that is not
        // actually in the file.
        val hay  = TriviaCheck.normalize(TriviaMark.stripAll(text))
        val seen = collection.mutable.Set.empty[String]
        val put  = collection.mutable.ListBuffer.empty[(Int, String)]
        balticporter.core.CommentScanner.scanAt(java).foreach { a =>
          val line = a.line(java)
          val body = TriviaCheck.normalize(a.text)
          if body.nonEmpty && line >= from && line < until && !hay.contains(body) && seen.add(body) then
            val endLine = line + a.text.count(_ == '\n')
            val owner   = CommentAnchor.owner(lines, line, endLine, members)
            // a member the port drops has no declaration for its javadoc to sit above, and putting
            // it in the file anyway would document a member that is not there.
            if owner.forall(_.emitted) then
              val at   = slots.lastIndexWhere(s => s.origin.javaPath == path && s.origin.line <= line)
              val lvl  = if at >= 0 then slots(at).indent else 0
              val kind = a.kind match
                case balticporter.core.TriviaKind.Line    => TriviaKind.Line
                case balticporter.core.TriviaKind.Block   => TriviaKind.Block
                case balticporter.core.TriviaKind.Javadoc => TriviaKind.Javadoc
              val where = provenance.map(p => sourcePathOf(Origin(path, line, 0), p)).getOrElse(path)
              // …rendered through `triviaText`, so §4.58's rules hold for a recovered comment
              // exactly as for a placed one: a block comment Scala would NEST on goes out
              // line-by-line as `//`, and the indent is re-derived rather than reproduced.
              put += at -> (ind(lvl) + TriviaMark.render(where, line) + "\n" + triviaText(Trivia(kind, a.text), lvl))
        }
        if put.isEmpty then text else splice(text, put.toList)

  /** Insert each rendered block after the slot it anchors on (`-1` = after everything the unit
    * emitted). Slot positions come from the SAME forward-cursor search `srcMapOf` uses, so an
    * anchor and a source-map entry can never disagree. An ENCLOSING slot gains the insertion too
    * (slots nest, so inserting after a nested member falls inside the enclosing class's recorded
    * string — measured as 2 UNLOCATABLE members before this was fixed). */
  private[emit] def splice(text: String, put: List[(Int, String)]): String =
    val starts = Array.fill(slots.size)(-1)
    val ends   = Array.fill(slots.size)(-1)
    var cursor = 0
    slots.zipWithIndex.foreach { (s, k) =>
      if s.text.nonEmpty then
        val at = text.indexOf(s.text, cursor)
        if at >= 0 then { cursor = at + 1; starts(k) = at; ends(k) = at + s.text.length }
    }
    val ins = put.zipWithIndex.map { case ((slot, rendered), n) =>
      val off = if slot >= 0 && slot < ends.length && ends(slot) >= 0 then ends(slot) else text.length
      (off, n, "\n" + rendered)
    // back to front, so an earlier insertion cannot move a later offset — for the unit text and
    // for each slot's own copy alike. Stable within one offset: several comments anchored on one
    // member keep their source order.
    }.sortBy((off, n, _) => (-off, -n))
    val sb = new java.lang.StringBuilder(text)
    ins.foreach { (off, _, s) =>
      sb.insert(off, s)
      slots.zipWithIndex.foreach { (slot, k) =>
        if starts(k) >= 0 && off > starts(k) && off < ends(k) then
          val rel = off - starts(k)
          slot.text = slot.text.substring(0, rel) + s + slot.text.substring(rel)
      }
    }
    sb.toString

  /** Locate every remembered member in the finished unit text. The unit itself is always entry
    * one, spanning the whole file: a line that falls between members (a brace, a blank line, the
    * package clause) then still resolves to the right Java FILE instead of to nothing. */
  private[emit] def srcMapOf(unit: String, cd: Tree.ClassDef, text: String): List[SrcMap.Entry] =
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

  /** The attribution + do-not-edit banner, in the same shape the BIR printer has always emitted —
    * one header, so a port that still runs both backends produces one kind of file. Empty when no
    * [[Provenance]] was given. The "Ported from" line names the ORIGINAL JAVA FILE from the unit's
    * own `Origin`, never reconstructed from its package: a renamed/nested type does not live at
    * the path its FQN suggests, and `Origin` is what still points at the upstream file. */
  private[emit] def header(cd: Tree.ClassDef): String = provenance match
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

  /** Repo-relative source path for headers. Compares via `toRealPath` (CLAUDE.md §5.4).
    * Falls back to raw path with warning when unconfigured. */
  private[emit] def sourcePathOf(o: Origin, p: Provenance): String =
    val raw = o.javaPath
    if raw.isEmpty || raw == "<synthetic>" || raw == "<unknown>" then
      "<unknown — the frontend recorded no source origin for this unit>"
    else
      val root   = p.sourceRoot.stripSuffix("/")
      val marker = p.sourcePathPrefix.stripSuffix("/")
      // §5.4: realpath both operands via `RealPath`.
      def realOrNormal(s: String): java.nio.file.Path = balticporter.core.RealPath.of(java.nio.file.Path.of(s))
      val rel =
        if root.nonEmpty then
          val rraw  = realOrNormal(raw)
          val rroot = realOrNormal(root)
          if rraw.startsWith(rroot) then Some(rroot.relativize(rraw).toString.replace('\\', '/'))
          else scala.None
        else scala.None
      val rel2 = rel.orElse {
        if marker.nonEmpty && raw.contains(marker + "/") then
          Some(raw.substring(raw.indexOf(marker + "/") + marker.length + 1))
        else scala.None
      }
      rel2 match
        case Some(r) if marker.nonEmpty                       => s"$marker/$r"
        case Some(r)                                          => r
        case scala.None if new java.io.File(raw).isAbsolute() =>
          s"$raw  (path as recorded — set Provenance.sourceRoot to relativise it)"
        case scala.None => raw // already relative: reproducible as it stands

  /** Every class at any depth, via `StandardTraversal.allClassDefs`. // ENGINE-LIMITS F8 */
  private[emit] lazy val allDeclaredClasses: List[Tree.ClassDef] =
    program.units.flatMap(u => StandardTraversal.allClassDefs(u)(using program))

  /** Type symbols appearing as parents anywhere -- prevents collapsing to `object`. */
  private[emit] lazy val extendedTypes: Set[SymId] =
    val acc = collection.mutable.Set[SymId]()
    def headSym(t: TypeRepr): Option[SymId] = t match
      case TypeRepr.TypeRef(_, s)      => Some(s)
      case TypeRepr.AppliedType(tc, _) => headSym(tc)
      case _                           => None
    allDeclaredClasses.foreach { cd =>
      cd.parents.foreach {
        case tt: TypeTree => headSym(tt.tpe).foreach(acc += _)
        case term: Term   => headSym(term.tpe).foreach(acc += _)
      }
    }
    acc.toSet

  /** Type symbols the program instantiates -- prevents collapsing to `object`. */
  private[emit] lazy val instantiatedTypes: Set[SymId] =
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

  /** Type symbols named in type positions elsewhere (declaration types + class literals).
    * Prevents collapsing to `object`. Excludes self-references via owner chain;
    * class literals in own unit DO count (log-tag idiom). */
  private[emit] lazy val typeNamedElsewhere: Set[SymId] =
    given Program = program
    val out = collection.mutable.Set[SymId]()

    def headSym(t: TypeRepr): Option[SymId] = t match
      case TypeRepr.TypeRef(_, s)      => Some(s)
      case TypeRepr.AppliedType(tc, _) => headSym(tc)
      case _                           => None

    /** Enclosing symbols from `s` up through its owner chain. */
    def enclosing(s: SymId): Set[SymId] =
      Iterator.iterate(Option(s))(_.flatMap(program.symbolOf(_).map(_.owner)))
        .take(64).takeWhile(o => o.isDefined && o.get != SymId.None).flatten.toSet

    def typesIn(t: TypeRepr): Set[SymId] =
      val seen = collection.mutable.Set[SymId]()
      val collect = new Phase:
        def name: String = "emit/type-named-elsewhere"
        override def transformType(x: TypeRepr)(using Program): TypeRepr =
          headSym(x).foreach(seen += _); x
      StandardTraversal.mapType(collect, t)
      seen.toSet

    // (1) declaration types, every symbol the program has.
    program.symbols.all.foreach { s => out ++= typesIn(s.info) -- enclosing(s.id) }

    // (2) class literals (own unit NOT subtracted -- log-tag idiom).
    program.units.foreach { u =>
      out ++= StandardTraversal.scanClassDef(u, Set.empty[SymId]) { (acc, term) =>
        term match
          case Tree.Literal(Constant.ClassOfC(t), _, _) => acc ++ typesIn(t)
          case _                                        => acc
      }
    }
    out.toSet

  /** Cross-module D6: base types this module names in type position that the base collapsed to `object`. */
  private[emit] lazy val collapsedBaseTypesNamed: List[Surface.Gap] =
    typeNamedElsewhere.toList
      .filterNot(surface.owns)
      .flatMap { s =>
        val fqn = program.symbolOf(s).map(_.fullName).getOrElse("?")
        surface.typeShape(s) match
          case Surface.Answer.Published(shape, module) if shape.form == "object" =>
            List(Surface.Gap(fqn,
              s"$module emitted this type as a bare `object` (its every member is static), and this " +
                "module names it in a TYPE position. An `object` supplies a VALUE and no value is a " +
                "type, so the two modules cannot compile together",
              Some(module), fatal = false,
              fix = s"§1(b) PER-LIBRARY, IN THE BASE: nothing in this module can repair it — $module is " +
                "already emitted. Either that module keeps the type a `class` (its statics move to a " +
                "companion, so every `X.member` call site is unchanged), or this module stops naming it " +
                "as a type"))
          // Non-published types are ordinary (JDK) and not reported here.
          case _ => Nil
      }
      .sortBy(_.subject)

  /** All type symbols declared in this unit (via `StandardTraversal`). */
  private[emit] def declaredTypes(cd: Tree.ClassDef): Set[SymId] =
    StandardTraversal.allClassDefs(cd)(using program).map(_.symbol).toSet

  /** head symbols of a class's parent types (extends + mixins). */
  private[emit] def parentSymsOf(cd: Tree.ClassDef): List[SymId] =
    def headSym(t: TypeRepr): Option[SymId] = t match
      case TypeRepr.TypeRef(_, s) => Some(s)
      case TypeRepr.AppliedType(tc, _) => headSym(tc)
      case _ => None
    cd.parents.flatMap { case tt: TypeTree => headSym(tt.tpe); case term: Term => headSym(term.tpe) }

  /** Statics for a type: reads base's published `statics=` for non-owned types. // DESIGN.md §8.3 */
  private[emit] def basePublishedStatics(s: SymId): Option[Set[String]] =
    if surface.owns(s) then scala.None
    else
      surface.typeShape(s) match
        case Surface.Answer.Own                 => scala.None
        case Surface.Answer.Published(shape, _) => Some(shape.statics.map(esc).toSet)
        case Surface.Answer.Unknown(why, module) =>
          surface.gap(Surface.Gap(sym(s).fullName,
            why + " — this run re-exports its companion, so it needs the static NAMES that companion " +
              "delivers; the local derivation over the base's java stands, and it does not see a " +
              "static the base renamed or dropped",
            module, fatal = false,
            fix = "§1(b) PER-LIBRARY: declare the module that emits this type as a base " +
              "(`base = \"…\"`) and re-run it with this engine so its port map carries `statics=`"))
          scala.None

  /** our-own types that have at least one `static` member (so a companion `object` holds it). */
  private[emit] lazy val typesWithStatics: Set[SymId] =
    val acc = collection.mutable.Set[SymId]()
    def scan(cd: Tree.ClassDef): Unit =
      val statics = basePublishedStatics(cd.symbol) match
        case Some(published) => published.nonEmpty
        case scala.None      => cd.body.exists { case d: Definition => sym(d.symbol).flags.isStatic; case _ => false }
      if statics then acc += cd.symbol
    allDeclaredClasses.foreach(scan)
    acc.toSet

  /** Each type's own declared static member names. */
  private[emit] lazy val ownStaticsBySym: Map[SymId, Set[String]] =
    val m = collection.mutable.Map[SymId, Set[String]]()
    def scan(cd: Tree.ClassDef): Unit =
      m(cd.symbol) = basePublishedStatics(cd.symbol).getOrElse(
        cd.body.collect {
          case d: Definition if sym(d.symbol).flags.isStatic => esc(sym(d.symbol).name)
          // a SPLICED companion member has no symbol; its name rides on the node (§1(b))
          case o: Tree.Opaque if o.companionMember.isDefined => esc(o.companionMember.get)
        }.toSet)
    allDeclaredClasses.foreach(scan); m.toMap

  /** Static names delivered by companion re-export of `s`, mapped to declaring type. */
  private[emit] def staticOwnersOf(s: SymId, seen: Set[SymId] = Set.empty): Map[String, SymId] =
    if seen(s) then Map.empty
    else
      val inherited = parentsBySym.getOrElse(s, Nil)
        .foldLeft(Map.empty[String, SymId])((acc, p) => staticOwnersOf(p, seen + s) ++ acc)
      inherited ++ ownStaticsBySym.getOrElse(s, Set.empty).map(_ -> s).toMap

  /** Each type's static members by emitted name, with their symbol. */
  private[emit] lazy val ownStaticSymsBySym: Map[SymId, Map[String, SymId]] =
    val m = collection.mutable.Map[SymId, Map[String, SymId]]()
    def scan(cd: Tree.ClassDef): Unit =
      // Exclude static initializer blocks (`<clinit>` cannot be a Scala identifier).
      m(cd.symbol) = cd.body.collect {
        case d: Definition if sym(d.symbol).flags.isStatic &&
          (!d.isInstanceOf[Tree.DefDef] || !isInitBlock(d.asInstanceOf[Tree.DefDef])) =>
          esc(sym(d.symbol).name) -> d.symbol
      }.toMap
    allDeclaredClasses.foreach(scan); m.toMap

  /** Non-public statics to exclude from companion re-export (prevents visibility leak). */
  private[emit] def nonPublicStatics(delivered: Map[String, SymId]): Set[String] =
    delivered.collect {
      case (n, owner) if ownStaticSymsBySym.getOrElse(owner, Map.empty).get(n)
        .exists(id => visPlan.getOrElse(id, Visibility.Vis.Public) != Visibility.Vis.Public) => n
    }.toSet

  /** Each type's parent symbols. */
  private[emit] lazy val parentsBySym: Map[SymId, List[SymId]] =
    val m = collection.mutable.Map[SymId, List[SymId]]()
    allDeclaredClasses.foreach(cd => m(cd.symbol) = parentSymsOf(cd)); m.toMap

  /** Whether this type or any ancestor has static members. */
  private[emit] def staticsReachable(s: SymId, seen: Set[SymId] = Set.empty): Boolean =
    !seen(s) && (typesWithStatics(s) || parentsBySym.getOrElse(s, Nil).exists(p => staticsReachable(p, seen + s)))

  /** every strict ancestor of `s`. */
  private[emit] def ancestorsOf(s: SymId, seen: Set[SymId] = Set.empty): Set[SymId] =
    parentsBySym.getOrElse(s, Nil).filterNot(seen).foldLeft(Set.empty[SymId]) { (acc, p) =>
      acc + p ++ ancestorsOf(p, seen + s + p)
    }

  // ---- names ----
  private[emit] def sym(id: SymId): Symbol = program.symbolOf(id).getOrElse(Symbol(id, "?", "?", Flags(), SymId.None, TypeRepr.NoType))
  private[emit] def local(id: SymId): String = esc(sym(id).name)

  /** A method symbol's declared parameter types, or empty for non-method info. */
  private[emit] def methodParams(id: SymId): List[TypeRepr] = sym(id).info match
    case TypeRepr.MethodType(ps, _, _)                       => ps.map(_._2)
    case TypeRepr.PolyType(_, TypeRepr.MethodType(ps, _, _)) => ps.map(_._2)
    case _                                                   => Nil

  /** Members indexed by (owner, name) for callee-based arity resolution. // CLAUDE.md §4.56 */
  private[emit] lazy val membersByOwnerName: Map[(SymId, String), List[SymId]] =
    val buf = collection.mutable.Map.empty[(SymId, String), List[SymId]]
    program.symbols.all.foreach { s =>
      if s.owner != SymId.None && s.name.nonEmpty then
        val key = (s.owner, s.name)
        buf(key) = s.id :: buf.getOrElse(key, Nil)
    }
    buf.toMap

  /** Whether the callee for `memberName` on `typeSym` has parens. Walks ancestry,
    * falls back to injected surface, externalParenless, subtypes, then runtime shims.
    * Default `false` (parenless, as for extension methods). */
  private[emit] def calleeHasParens(typeSym: SymId, memberName: String): Boolean =
    def checkMember(owner: SymId): Option[Boolean] =
      membersByOwnerName.get((owner, memberName)).flatMap { ids =>
        ids.collectFirst {
          case id if program.owns(id) =>
            program.definitionOf(id) match
              case Some(d: Tree.DefDef) => d.paramss.nonEmpty
              case _                    => true
          case id =>
            sym(id).info match
              case _: TypeRepr.MethodType => true
              case _                     => false
        }
      }

    def walkAncestors(s: SymId, seen: Set[SymId]): Option[Boolean] =
      if seen(s) || s == SymId.None then None
      else
        checkMember(s).orElse {
          val newSeen = seen + s
          parentsBySym.getOrElse(s, Nil).iterator
            .filterNot(newSeen)
            .flatMap(a => walkAncestors(a, newSeen))
            .nextOption()
        }

    walkAncestors(typeSym, Set.empty).getOrElse {
      // Fallbacks: (0) injected surface, (0.5) externalParenless, (1) subtypes, (2) runtime shims.
      val ownerFqn = program.symbolOf(typeSym).map(_.fullName).getOrElse("")
      val fromInjected = injectedSurface.memberHasParens(ownerFqn, memberName)
      if fromInjected.isDefined then fromInjected.get
      // Fallback 0.5: manifest-declared external parenless members.
      else if externalParenless.contains(s"$ownerFqn#$memberName") then false
      else if program.owns(typeSym) then false
      else
        val visited = ancestorsOf(typeSym) + typeSym
        // Fallback 1: check program-declared subtypes
        val fromSubtype = parentsBySym.iterator.exists { case (child, parents) =>
          program.owns(child) && parents.exists(visited) &&
            checkMember(child).contains(true)
        }
        if fromSubtype then true
        else
          // Fallback 2: runtime shim types use java arity. // CLAUDE.md §4.5
          val runtimePrefix = balticporter.core.RuntimeArtifact.Package + ".Java"
          program.symbolOf(typeSym).exists(_.fullName.startsWith(runtimePrefix))
    }

  /** Is this member listed in `externalParenless`? Matches `Owner#name` against the set. */
  private[emit] def isExternalParenless(m: SymId): Boolean =
    if externalParenless.isEmpty then false
    else
      val ownerSym = sym(m).owner
      if ownerSym == SymId.None then false
      else externalParenless.contains(s"${sym(ownerSym).fullName}#${sym(m).name}")

  /** backtick an identifier that collides with a Scala keyword. */
  private[emit] def esc(name: String): String = TirEmitter.esc(name)

  /** backtick every keyword SEGMENT of a qualified name (§4.56 separators). */
  private[emit] def escPath(path: String): String = TirEmitter.escPath(path)

  /** Whether this type is an unresolved type variable (marker name, must not reach output). */
  private[emit] def isUnresolvedTypeVar(t: TypeRepr): Boolean = t match
    case TypeRepr.TypeRef(_, s) => Symbol.isUnresolvedTypeVar(sym(s).fullName)
    case _                      => false
  /** a TYPE symbol's rendered name. FULLY QUALIFIED by default: fully-qualified references and NO
    * imports deletes the whole import-decision bug class (a reference becomes a context-free
    * function of the owner chain). Unqualified only for type params and a type declared in THIS
    * unit. Human-readable imports are a separate, optional beautification backend. */
  private[emit] def typeSym(id: SymId): String =
    val s = sym(id)
    // an UNRESOLVED type variable is a marker and not a name — never print it (see
    // [[isUnresolvedTypeVar]]). `?` is what G2 settles an un-nameable type argument renders as, and
    // in the one position where `?` is not a type either, it is a CONTAINED error rather than a
    // lexical one that takes the enclosing statement with it.
    if Symbol.isUnresolvedTypeVar(s.fullName) then "?"
    else if tparamSubst.contains(id) then tpe(tparamSubst(id)) // ctor type param → its bound
    else if s.flags.isParam then esc(s.name)
    // a Java `static` nested class is lowered into the enclosing type's companion `object`, so it
    // is named through the value path `Outer.Inner` — NOT by simple name (companion members aren't
    // in the class's scope) and NOT `Outer#Inner` (a type projection can't reach a companion member).
    else if s.flags.isStatic && s.fullName.contains('$') then nestedPath(id)
    // a Java INNER (non-static) class is a PATH-dependent type in Scala: by simple name it means
    // `this.Inner`, so the same Java type reached through two instances never unifies. Named by
    // PROJECTION everywhere instead — one type for all instances. `extends`/`new` need an
    // instantiable/stable name, so those two positions opt out (see `namedInner`).
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
  private[emit] def inheritedNested(owner: SymId): Boolean =
    owner != SymId.None && classStack.exists(c => c != owner && ancestorsOf(c).contains(owner))

  /** the path to a NESTED type, choosing a separator PER LEVEL: `.` for a java `static` nested
    * class (lowered into the companion `object`), `#` for a genuine inner class (a projection). A
    * blanket `fullName.replace('$', '#')` gets a MIXED chain wrong. Falls back to the blanket form
    * whenever an owner symbol is unknown, so this can only ever add precision. */
  private[emit] def nestedPath(id: SymId): String =
    def go(x: SymId): Option[String] =
      val sx = sym(x)
      if !sx.fullName.contains('$') then Some(escPath(sx.fullName))
      else if sx.owner == SymId.None || program.symbolOf(sx.owner).isEmpty then None
      else go(sx.owner).map(p =>
        if sx.flags.isStatic then s"$p.${esc(sx.name)}" else s"$p${outerFill(sx.owner)}#${esc(sx.name)}")
    // The fallback fires exactly when an owner is UNKNOWN, which for a type we do not define means
    // an external/JDK one. Name those with `.`: a Java nested type is reached as `Outer.Inner` in
    // Scala, and a `#` projection is not even available — it needs the prefix to be an immutable
    // path, which a bare external class name is not (`java.nio.channels.FileChannel#MapMode`).
    go(id).getOrElse:
      val sep = if program.definitionOf(id).isEmpty then '.' else '#'
      escPath(sym(id).fullName).replace('$', sep)

  /** THE `[?, …]` A PROJECTION'S PREFIX NEEDS when the enclosing class is GENERIC. `Outer#Inner`
    * is not a legal projection where `Outer` takes type parameters (scalac needs a TYPE, not an
    * unapplied constructor) — java writes exactly this for an inner class referred to RAW. `?` per
    * parameter is the hand port's own rendering of every raw generic (§3.5); filling from the
    * enclosing scope would invent an instantiation java did not write. Empty for a non-generic owner. */
  private[emit] def outerFill(owner: SymId): String =
    program.definitionOf(owner).collect { case c: Tree.ClassDef => c.tparams.size }
      .filter(_ > 0).map(n => List.fill(n)("?").mkString("[", ", ", "]")).getOrElse("")

  /** a NON-static nested class of one of our own NON-GENERIC classes (not of a companion `object`).
    * A generic enclosing class is excluded: `Octree#OctreeNode` is not a legal projection — the
    * prefix would need type arguments, which the reference does not carry. */
  private[emit] def isInnerClass(id: SymId): Boolean =
    val s = sym(id)
    !s.flags.isStatic && s.owner != SymId.None && s.fullName.contains('$') &&
      !program.symbolOf(s.owner).exists(_.flags.isModule) &&
      program.definitionOf(s.owner).exists { case c: Tree.ClassDef => c.tparams.isEmpty; case _ => false }

  /** inside an `extends` clause or a `new`, where a type projection is not legal — render inner
    * classes by their simple (in-scope) name there. */
  private[emit] var namedInner = false
  private[emit] def byName[A](f: => String): String =
    val prev = namedInner; namedInner = true
    try f finally namedInner = prev

  private[emit] def ind(n: Int): String = "  " * n

  // TRIVIA — the original Java comments, re-emitted above the node that carried them. Three
  // decisions, made once so the output is DETERMINISTIC, not whitespace-faithful: RE-INDENTED to
  // the node (relative alignment inside preserved); exactly ONE newline between comment and node;
  // otherwise VERBATIM. A comment containing `/*` is re-emitted LINE BY LINE as `//`, since Scala's
  // block comments NEST (unlike java's) and would otherwise swallow the rest of the file.

  /** the block of comment lines that precedes a node, with its trailing newline; `""` for none. */
  private[emit] def leading(ts: List[Trivia], i: Int): String =
    if ts.isEmpty then "" else ts.map(triviaText(_, i)).mkString("\n") + "\n"

  /** does this block comment contain a delimiter that Scala would read as nesting? */
  private[emit] def nests(t: Trivia): Boolean =
    val body = t.text.stripPrefix("/**").stripPrefix("/*").stripSuffix("*/")
    body.contains("/*") || body.contains("*/")

  private[emit] def triviaText(t: Trivia, i: Int): String =
    val lines = t.text.replace("\r\n", "\n").replace("\r", "\n").split("\n", -1).toList
    t.kind match
      case TriviaKind.Line                 => ind(i) + lines.head.trim
      case _ if nests(t)                   => lines.map(l => (ind(i) + "//" + l).stripTrailing()).mkString("\n")
      case _                               =>
        val rest   = lines.tail
        val filled = rest.filter(_.trim.nonEmpty)
        val cut    = filled.map(_.takeWhile(c => c == ' ' || c == '\t').length).minOption.getOrElse(0)
        val gutter = filled.nonEmpty && filled.forall(_.trim.startsWith("*"))
        val pre    = ind(i) + (if gutter then " " else "")
        ((ind(i) + lines.head.trim) :: rest.map(l => if l.trim.isEmpty then "" else (pre + l.drop(cut)).stripTrailing()))
          .mkString("\n")

