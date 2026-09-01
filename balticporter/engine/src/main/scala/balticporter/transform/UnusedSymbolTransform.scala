package balticporter.transform

import balticporter.tir.*

/** A LATE phase that removes or suppresses unused local definitions and private members.
  *
  * ==Why this is needed==
  * Java has no equivalent of Scala's `-Wunused:locals,privates` — a local or private field that is
  * never read compiles silently. Under sge's strict flags (`-Werror -Wunused:imports,privates,
  * locals,patvars,nowarn`) every such symbol becomes `E198 Unused Symbol Warning` promoted to an
  * error. The port faithfully reproduces Java's dead code, and the reference compile rejects it.
  *
  * ==Kind==
  * CLAUDE.md §1(a) universal. Java allows unused symbols and Scala's strict flags do not, true of
  * every codebase. No configuration, no per-library policy.
  *
  * ==Translation (the refusal enumeration — §3)==
  * For each unused definition the phase chooses the FIRST applicable action:
  *
  *  1. '''DELETE''' — a local or private member whose initialiser is provably side-effect-free
  *     (literal, ident, `this.field`) and which is never read and never written to.
  *  2. '''DISCARD''' — for a local with an initialiser that MAY have effects (a call, a `new`),
  *     keep the effect as a bare expression and drop the binding.
  *  3. '''SUPPRESS''' — `@nowarn` on the declaration:
  *     - `serialVersionUID`: `@nowarn("msg=unused")` matches "unused private member".
  *     - write-only locals: `@nowarn("msg=not read")` matches "mutated but not read".
  *     - write-only private vars: `@nowarn("msg=not read")`.
  *     - unreferenced private with side-effecting init: `@nowarn("msg=unused")`.
  *  4. '''REFUSED''' — an unused NON-PRIVATE member is API surface and not deletable; a private
  *     member whose simple name appears in a `MethodBodyTransform` substitution body of its
  *     owning type is conservatively treated as referenced (`substituted-body-reference` guard).
  *     Both are counted on the `unused-symbol(refused)` lane.
  *
  * ==Substituted-body-reference guard==
  * A `MethodBodyTransform` body is verbatim text injected AFTER all phases (CLAUDE.md §1.5), so
  * the TIR cannot see what it references. A symbol whose simple name occurs as a `\bname\b` token
  * in ANY `Tree.Opaque.raw` text of its owning type is treated as referenced and never deleted or
  * suppressed — a conservative refusal counted on the refused lane.
  *
  * ==Read/write distinction==
  * ONE `StandardTraversal` walk (never a private recursion — CLAUDE.md §3) collects TWO counts per
  * symbol: `allCounts` (every `Ident`/`Select`) and `assignCounts` (how many of those are the
  * direct LHS of a `Tree.Assign`). Post-pass: a symbol is READ if `allCounts(s) > assignCounts(s)`,
  * WRITE-ONLY if equal, and UNREFERENCED if `allCounts(s) == 0`.
  *
  * ==Check lanes==
  * `unused-symbol(handled)` — every symbol the phase acted on (deleted, discarded, or suppressed).
  * `unused-symbol(refused)` — unused symbols the phase left alone, naming the guard. Both are
  * required of every run (unconditional, the phase is in `derivedPhases`).
  *
  * ==Position==
  * Runs AFTER every retyping phase. Runs BEFORE `package-rename` and `suppressed-warnings`. */
final class UnusedSymbolTransform extends Phase:

  def name = UnusedSymbolTransform.Name

  override def runsAfter: Set[String] = Set(
    "nullability",
    "java-collections->scala",
    "type-redirect",
    "globals->implicits",
  )
  override def runsBefore: Set[String] = Set("package-rename", SuppressionPhase.Name)

  // Exposed for PortRun to build check-lane findings
  var handledSymbols: List[Decision] = Nil
  var refusedRows: List[(String, String, Origin)] = Nil  // (fqn, guard, origin)

  override def run(program: Program): Program =
    given Program = program

    // ---- Step 1: ONE StandardTraversal walk with count-based read/write distinction ----
    val allCounts    = collection.mutable.Map[SymId, Int]().withDefaultValue(0)
    val assignCounts = collection.mutable.Map[SymId, Int]().withDefaultValue(0)

    val refCollector = new Phase:
      def name = "unused-symbol/ref-collect"
      override def transformTerm(t: Term)(using Program): Term =
        t match
          case Tree.Assign(lhs, _, _, _, _) =>
            lhs match
              case Tree.Ident(s, _, _)     => assignCounts(s) += 1
              case Tree.Select(_, s, _, _) => assignCounts(s) += 1
              case _ => ()
          case Tree.Ident(s, _, _)       => allCounts(s) += 1
          case Tree.Select(_, s, _, _)   => allCounts(s) += 1
          case Tree.Apply(_, _, m, _, _) => allCounts(m) += 1
          case _ => ()
        t

    program.units.foreach(u => StandardTraversal.mapClassDef(refCollector, u))

    def isRead(s: SymId): Boolean        = allCounts(s) > assignCounts(s)
    def isWriteOnly(s: SymId): Boolean   = allCounts(s) > 0 && allCounts(s) == assignCounts(s)
    def isUnreferenced(s: SymId): Boolean = allCounts(s) == 0

    // ---- Step 1b: collect substituted-body words per owning class ----
    // A MethodBodyTransform body is Tree.Opaque — verbatim Scala text the TIR walk cannot see.
    // Any symbol whose simple name appears as a word boundary token in that text is treated as
    // referenced (conservative refusal). Comment-mask the text before scanning.
    val substWords = collection.mutable.Map[SymId, Set[String]]().withDefaultValue(Set.empty)
    val opaqueCollector = new Phase:
      def name = "unused-symbol/opaque-collect"
      override def transformDefDef(d: Tree.DefDef)(using p: Program): Tree.DefDef =
        d.rhs.foreach {
          case Tree.Opaque(raw, _, _,  _) =>
            // Strip comments: // to EOL, /* ... */
            val masked = raw
              .replaceAll("//[^\n]*", "")
              .replaceAll("/\\*[^*]*\\*+(?:[^/*][^*]*\\*+)*/", "")
            val words = masked.split("[^a-zA-Z0-9_$]+").filter(_.nonEmpty).toSet
            // Walk owner chain to find enclosing class
            p.symbolOf(d.symbol).foreach { ds =>
              substWords(ds.owner) = substWords(ds.owner) ++ words
            }
          case _ => ()
        }
        d
    program.units.foreach(u => StandardTraversal.mapClassDef(opaqueCollector, u))

    def isSubstitutionReferenced(sym: Symbol): Boolean =
      substWords(sym.owner).contains(sym.name)

    // ---- Step 2: classify unused locals and private members ----
    val toDelete   = collection.mutable.Set[SymId]()
    val toDiscard  = collection.mutable.Set[SymId]()
    val toSuppress = collection.mutable.Map[SymId, String]()  // sym -> nowarn msg
    val refused    = collection.mutable.ListBuffer[(String, String, Origin)]()  // (fqn, guard, origin)

    def classifyPrivateMember(v: Tree.ValDef): Unit =
      program.symbolOf(v.symbol).foreach { s =>
        if !s.flags.isPrivate || s.flags.isParam || s.flags.isParamAccessor then ()
        else if isSubstitutionReferenced(s) then
          refused += ((s.fullName, "substituted-body-reference", v.origin))
        else if s.name == "serialVersionUID" then
          toSuppress(v.symbol) = "msg=unused"
        else if isUnreferenced(v.symbol) then
          if UnusedSymbolTransform.isSideEffectFree(v.rhs) then toDelete += v.symbol
          else toSuppress(v.symbol) = "msg=unused"
        else if isWriteOnly(v.symbol) && s.flags.isMutable then
          toSuppress(v.symbol) = "msg=not read"
      }

    def classifyPrivateDef(d: Tree.DefDef): Unit =
      program.symbolOf(d.symbol).foreach { s =>
        if s.flags.isPrivate && !s.flags.isParam && isUnreferenced(d.symbol) &&
           s.name != "<init>" && !s.name.endsWith("_=") &&
           !Set("equals", "hashCode", "toString", "clone", "finalize").contains(s.name) then
          if isSubstitutionReferenced(s) then
            refused += ((s.fullName, "substituted-body-reference", d.origin))
          else toDelete += d.symbol
      }

    def classifyNonPrivateDef(d: Tree.DefDef): Unit =
      program.symbolOf(d.symbol).foreach { s =>
        if !s.flags.isPrivate && !s.flags.isParam && s.name != "<init>" &&
           !s.flags.isOverride && isUnreferenced(d.symbol) then
          refused += ((s.fullName, "non-private member", d.origin))
      }

    // Scan class bodies
    program.units.foreach { u =>
      StandardTraversal.allClassDefs(u).foreach { cd =>
        cd.body.foreach {
          case v: Tree.ValDef => classifyPrivateMember(v)
          case d: Tree.DefDef =>
            classifyPrivateDef(d)
            classifyNonPrivateDef(d)
          case _ => ()
        }
        StandardTraversal.allAnonClasses(cd).foreach { (anon, _) =>
          anon.body.foreach {
            case v: Tree.ValDef => classifyPrivateMember(v)
            case d: Tree.DefDef =>
              classifyPrivateDef(d)
              classifyNonPrivateDef(d)
            case _ => ()
          }
        }
      }
    }

    // Scan method bodies for unused locals — walk with StandardTraversal
    val localCollector = new Phase:
      def name = "unused-symbol/local-collect"
      override def transformValDef(v: Tree.ValDef)(using p: Program): Tree.ValDef =
        if !isRead(v.symbol) then
          p.symbolOf(v.symbol).foreach { s =>
            if !s.flags.isParam && !s.flags.isParamAccessor &&
               !s.flags.isPrivate && !s.flags.isProtected &&
               !s.flags.isPackagePrivate then
              p.symbolOf(s.owner).foreach { os =>
                if os.descriptor.isDefined || os.name == "<init>" then
                  if isUnreferenced(v.symbol) then
                    if UnusedSymbolTransform.isSideEffectFree(v.rhs) then toDelete += v.symbol
                    else toDiscard += v.symbol
                  else if isWriteOnly(v.symbol) && s.flags.isMutable then
                    toSuppress(v.symbol) = "msg=not read"
              }
          }
        v
    program.units.foreach(u => StandardTraversal.mapClassDef(localCollector, u))

    // ---- Step 3: record decisions ----
    val allHandled = collection.mutable.ListBuffer[Decision]()

    (toDelete ++ toDiscard).toList.sortBy(_.raw).foreach { id =>
      program.symbolOf(id).foreach { s =>
        val action = if toDelete(id) then "deleted" else "discarded-binding"
        val symbolKind = UnusedSymbolTransform.symbolKindOf(s)
        val d = Decision(
          kind       = Decision.Kind.UnusedSymbolHandled,
          subject    = id,
          subjectFqn = s.fullName,
          detail = Map("action" -> action, "symbol-kind" -> symbolKind),
          reason = Reason.Universal("unused-symbol"),
          origin = Decision.originOf(program, id),
        )
        record(d)
        allHandled += d
      }
    }
    toSuppress.toList.sortBy(_._1.raw).foreach { (id, msg) =>
      program.symbolOf(id).foreach { s =>
        val symbolKind = UnusedSymbolTransform.symbolKindOf(s)
        val why = if s.name == "serialVersionUID" then "serialVersionUID — JVM reads reflectively"
                  else if msg == "msg=not read" then "write-only — assigned but never read"
                  else "unreferenced private with side-effecting init"
        val d = Decision(
          kind       = Decision.Kind.UnusedSymbolHandled,
          subject    = id,
          subjectFqn = s.fullName,
          detail = Map("action" -> "suppressed", "symbol-kind" -> symbolKind,
                       "annotation" -> s"""@nowarn("$msg")""", "why" -> why),
          reason = Reason.Universal("unused-symbol"),
          origin = Decision.originOf(program, id),
        )
        record(d)
        allHandled += d
      }
    }

    handledSymbols = allHandled.toList
    refusedRows = refused.toList

    if toDelete.isEmpty && toDiscard.isEmpty && toSuppress.isEmpty then return program

    // ---- Step 4: rewrite trees ----
    def rewriteStats(stats: List[Statement]): List[Statement] =
      stats.flatMap {
        case v: Tree.ValDef if toDelete(v.symbol) => None
        case v: Tree.ValDef if toDiscard(v.symbol) => v.rhs.toList
        case d: Tree.DefDef if toDelete(d.symbol) => None
        case other => Some(other)
      }

    val rewritePhase = new Phase:
      def name: String = "unused-symbol/rewrite"
      override def transformClassDef(t: Tree.ClassDef)(using Program): Tree.ClassDef =
        val newBody = rewriteStats(t.body)
        if newBody.size == t.body.size then t else t.copy(body = newBody)
      override def transformBlock(t: Tree.Block)(using Program): Term =
        val newStats = rewriteStats(t.stats)
        if newStats.size == t.stats.size then t else t.copy(stats = newStats)
      override def transformTerm(t: Term)(using Program): Term = t match
        case f: Tree.For =>
          val newInit = rewriteStats(f.init)
          if newInit.size == f.init.size then f else f.copy(init = newInit)
        case other => other

    val units = program.units.map(u => StandardTraversal.mapClassDef(rewritePhase, u))

    // ---- Step 5: attach @nowarn annotations to suppressed symbols ----
    if toSuppress.isEmpty then
      return program.rebuilt(units, program.symbols)

    val existingNowarn = program.symbols.all.find(_.fullName == "scala.annotation.nowarn").map(_.id)
    val nowarnSym = existingNowarn.getOrElse {
      val minId = program.symbols.all.map(_.id.raw).minOption.getOrElse(0)
      SymId(math.min(minId - 1, -2))
    }

    def nowarnAnnot(msg: String): Annot = Annot(
      tpe    = TypeRepr.TypeRef(TypeRepr.NoPrefix, nowarnSym),
      args   = List("value" -> Tree.Literal(
        Constant.StringC(msg),
        TypeRepr.TypeRef(TypeRepr.NoPrefix, SymId.None),
        Origin.synthetic)),
      origin = Origin.synthetic,
    )

    val alreadyAnnotated = program.symbols.all.filter { s =>
      s.annotations.exists(a =>
        program.symbolOf(a.tpe match {
          case TypeRepr.TypeRef(_, sym) => sym
          case _ => SymId.None
        }).exists(_.fullName == "scala.annotation.nowarn"))
    }.map(_.id).toSet

    val toAnnotate = toSuppress.toMap -- alreadyAnnotated
    if toAnnotate.isEmpty then
      return program.rebuilt(units, program.symbols)

    val updated = program.symbols.all.map { s =>
      toAnnotate.get(s.id) match
        case Some(msg) => s.copy(annotations = s.annotations :+ nowarnAnnot(msg))
        case None      => s
    }
    val allSyms = if existingNowarn.isDefined then updated
                  else updated ++ List(Symbol(
                    nowarnSym, "nowarn", "scala.annotation.nowarn",
                    Flags(), SymId.None, TypeRepr.NoType))

    program.rebuilt(units, SymbolTable(allSyms))

object UnusedSymbolTransform:
  val Name = "unused-symbols"

  val Handled = "unused-symbol(handled)"
  val Refused = "unused-symbol(refused)"

  def symbolKindOf(s: Symbol): String =
    if s.flags.isPrivate then
      s"unused private ${if s.flags.isMutable then "var" else if s.descriptor.isDefined then "def" else "val"}"
    else
      s"unused local ${if s.flags.isMutable then "var" else "val"}"

  def isSideEffectFree(rhs: Option[Term]): Boolean = rhs match
    case None    => true
    case Some(t) => isSideEffectFreeTerm(t)

  def isSideEffectFreeTerm(t: Term): Boolean = t match
    case _: Tree.Literal               => true
    case _: Tree.This                  => true
    case _: Tree.Ident                 => true
    case Tree.Select(_: Tree.This, _, _, _) => true
    case Tree.Select(q, _, _, _)       => isSideEffectFreeTerm(q)
    case Tree.Typed(e, _, _, _)        => isSideEffectFreeTerm(e)
    case Tree.Block(Nil, e, _, _, _)   => isSideEffectFreeTerm(e)
    case _ => false
