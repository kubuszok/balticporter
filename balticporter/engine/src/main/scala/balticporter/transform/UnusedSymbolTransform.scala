package balticporter.transform

import balticporter.tir.*

/** A LATE phase that removes unused local definitions and private members.
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
  *  3. '''SKIP''' — a write-only symbol (assigned but never read), serialVersionUID, or a private
  *     member with side-effecting init is left alone. Write-only vars need `@nowarn("msg=not
  *     read")` and serialVersionUID needs `@nowarn("msg=unused")`, but the emitter does not yet
  *     render annotations on val declarations, so suppression would be a silent no-op that
  *     `-Wunused:nowarn` then flags (ENGINE-LIMITS T26).
  *
  * ==Read/write distinction==
  * ONE `StandardTraversal` walk (never a private recursion — CLAUDE.md §3) collects TWO counts per
  * symbol: `allCounts` (every `Ident`/`Select`) and `assignCounts` (how many of those are the
  * direct LHS of a `Tree.Assign`). Post-pass: a symbol is READ if `allCounts(s) > assignCounts(s)`,
  * WRITE-ONLY if equal, and UNREFERENCED if `allCounts(s) == 0`.
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
    def isUnreferenced(s: SymId): Boolean = allCounts(s) == 0

    // ---- Step 2: classify unused locals and private members ----
    val toDelete  = collection.mutable.Set[SymId]()
    val toDiscard = collection.mutable.Set[SymId]()

    // Scan class bodies for UNREFERENCED private members
    program.units.foreach { u =>
      StandardTraversal.allClassDefs(u).foreach { cd =>
        cd.body.foreach {
          case v: Tree.ValDef =>
            program.symbolOf(v.symbol).foreach { s =>
              if s.flags.isPrivate && !s.flags.isParam && !s.flags.isParamAccessor &&
                 isUnreferenced(v.symbol) &&
                 s.name != "serialVersionUID" &&
                 UnusedSymbolTransform.isSideEffectFree(v.rhs) then
                toDelete += v.symbol
            }
          case d: Tree.DefDef =>
            program.symbolOf(d.symbol).foreach { s =>
              if s.flags.isPrivate && !s.flags.isParam && isUnreferenced(d.symbol) &&
                 s.name != "<init>" && !s.name.endsWith("_=") &&
                 !Set("equals", "hashCode", "toString", "clone", "finalize").contains(s.name) then
                toDelete += d.symbol
            }
          case _ => ()
        }
        StandardTraversal.allAnonClasses(cd).foreach { (anon, _) =>
          anon.body.foreach {
            case v: Tree.ValDef =>
              program.symbolOf(v.symbol).foreach { s =>
                if s.flags.isPrivate && !s.flags.isParam && !s.flags.isParamAccessor &&
                   isUnreferenced(v.symbol) &&
                   s.name != "serialVersionUID" &&
                   UnusedSymbolTransform.isSideEffectFree(v.rhs) then
                  toDelete += v.symbol
              }
            case d: Tree.DefDef =>
              program.symbolOf(d.symbol).foreach { s =>
                if s.flags.isPrivate && !s.flags.isParam && isUnreferenced(d.symbol) &&
                   s.name != "<init>" && !s.name.endsWith("_=") &&
                   !Set("equals", "hashCode", "toString", "clone", "finalize").contains(s.name) then
                  toDelete += d.symbol
              }
            case _ => ()
          }
        }
      }
    }

    // Scan method bodies for UNREFERENCED locals — walk with StandardTraversal
    val localCollector = new Phase:
      def name = "unused-symbol/local-collect"
      override def transformValDef(v: Tree.ValDef)(using p: Program): Tree.ValDef =
        if !isRead(v.symbol) && isUnreferenced(v.symbol) then
          p.symbolOf(v.symbol).foreach { s =>
            if !s.flags.isParam && !s.flags.isParamAccessor &&
               !s.flags.isPrivate && !s.flags.isProtected &&
               !s.flags.isPackagePrivate then
              p.symbolOf(s.owner).foreach { os =>
                if os.descriptor.isDefined || os.name == "<init>" then
                  if UnusedSymbolTransform.isSideEffectFree(v.rhs) then toDelete += v.symbol
                  else toDiscard += v.symbol
              }
          }
        v
    program.units.foreach(u => StandardTraversal.mapClassDef(localCollector, u))

    if toDelete.isEmpty && toDiscard.isEmpty then return program

    // ---- Step 3: record decisions ----
    (toDelete ++ toDiscard).toList.sortBy(_.raw).foreach { id =>
      program.symbolOf(id).foreach { s =>
        val action = if toDelete(id) then "deleted" else "discarded-binding"
        val symbolKind =
          if s.flags.isPrivate then
            s"unused private ${if s.flags.isMutable then "var" else if s.descriptor.isDefined then "def" else "val"}"
          else
            s"unused local ${if s.flags.isMutable then "var" else "val"}"
        record(Decision(
          kind       = Decision.Kind.UnusedSymbolHandled,
          subject    = id,
          subjectFqn = s.fullName,
          detail = Map("action" -> action, "symbol-kind" -> symbolKind),
          reason = Reason.Universal("unused-symbol"),
          origin = Decision.originOf(program, id),
        ))
      }
    }

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
    program.rebuilt(units, program.symbols)

object UnusedSymbolTransform:
  val Name = "unused-symbols"

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
