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
  * CLAUDE.md §1(a). The mechanism is universal — Java allows unused symbols and Scala's strict
  * flags do not, true of every codebase. No configuration, no per-library policy.
  *
  * ==Translation order (the refusal enumeration — §3)==
  * For each unused definition the phase chooses the FIRST applicable action:
  *
  *  1. '''DELETE''' a local whose initialiser is provably side-effect-free (literal, ident, field
  *     read, `this` selection) and which is never read — a `var` only written likewise.
  *  2. '''DISCARD''' — for an initialiser that MAY have effects (a method call, a `new`), keep the
  *     effect as a bare expression and drop the binding (`expr` replaces `val x = expr`).
  *  3. '''SUPPRESS''' — `@nowarn("msg=unused")` at the declaration. Used for:
  *     - `serialVersionUID` (the JVM reads it reflectively — deleting changes serialization)
  *     - unused private members whose init may have effects (cannot safely delete a constructor
  *       call or method call that might register, log, or mutate shared state)
  *
  * ==Read/write distinction==
  * A symbol that is only ASSIGNED TO but never READ is "mutated but not read", which
  * `-Wunused:privates` flags. The reference collection distinguishes Assign.lhs (write) from
  * other positions (read) by walking the tree with context, falling back to StandardTraversal
  * for any node kind not explicitly enumerated (safe: the fallback is conservative, counting
  * every occurrence as a read).
  *
  * ==Position==
  * Runs AFTER every retyping phase (sees the FINAL tree, same as `SuppressionPhase`).
  * Runs BEFORE `package-rename` (the `@nowarn` annotation FQN is in the scala namespace). */
final class UnusedSymbolTransform extends Phase:

  def name = UnusedSymbolTransform.Name

  override def runsAfter: Set[String] = Set(
    "nullability",
    "java-collections->scala",
    "type-redirect",
    "globals->implicits",
  )
  override def runsBefore: Set[String] = Set("package-rename")

  override def run(program: Program): Program =
    given Program = program

    // ---- Step 1: collect symbol READ references across the whole program ----
    // A symbol on the LHS of Assign is a WRITE; everywhere else is a READ.
    // The walk enumerates every Term kind the TIR has, with a StandardTraversal fallback for any
    // kind not explicitly listed (conservative: counts as read).
    val readSyms = collection.mutable.Set[SymId]()

    def addRead(t: Term): Unit = t match
      case Tree.Ident(s, _, _)     => readSyms += s
      case Tree.Select(_, s, _, _) => readSyms += s
      case _ => ()

    def collectReads(t: Term): Unit = t match
      case Tree.Assign(lhs, rhs, _, _, _) =>
        // LHS direct symbol is a WRITE — skip it. But sub-expressions of LHS are reads.
        lhs match
          case Tree.Ident(_, _, _) => ()
          case Tree.Select(_: Tree.This, _, _, _) => ()
          case Tree.Select(q, _, _, _) => collectReads(q)
          case Tree.Apply(fun, args, _, _, _) => collectReads(fun); args.foreach(collectReads)
          case other => collectReads(other)
        collectReads(rhs)
      case Tree.Ident(s, _, _)     => readSyms += s
      case Tree.Select(q, s, _, _) => readSyms += s; collectReads(q)
      case Tree.Apply(fun, args, m, _, _) => readSyms += m; collectReads(fun); args.foreach(collectReads)
      case Tree.TypeApply(fun, _, _, _)   => collectReads(fun)
      case _: Tree.Literal | _: Tree.This | _: Tree.Super => ()
      case Tree.New(_, _, _, anon) => anon.foreach(a => collectStatements(a.body))
      case Tree.Lambda(_, body, _, _, _) => collectReads(body)
      case Tree.Block(stats, expr, _, _, _) => collectStatements(stats); collectReads(expr)
      case Tree.If(c, t, e, _, _) => collectReads(c); collectReads(t); collectReads(e)
      case Tree.While(c, b, _, _, _) => collectReads(c); collectReads(b)
      case Tree.DoWhile(b, c, _, _, _) => collectReads(b); collectReads(c)
      case Tree.For(init, cond, upd, body, _, _, _) =>
        collectStatements(init); cond.foreach(collectReads); collectStatements(upd); collectReads(body)
      case Tree.ForEach(_, it, body, _, _, _) => collectReads(it); collectReads(body)
      case t: Tree.Try =>
        collectReads(t.body); t.catches.foreach(c => collectReads(c.body)); t.finalizer.foreach(collectReads)
      case m: Tree.Match =>
        collectReads(m.scrutinee)
        m.cases.foreach { c => c.labels.foreach(collectReads); c.guard.foreach(collectReads); collectReads(c.body) }
      case Tree.Return(e, _, _) => e.foreach(collectReads)
      case Tree.Throw(e, _, _) => collectReads(e)
      case Tree.Typed(e, _, _, _) => collectReads(e)
      case Tree.Labeled(_, body, _, _) => collectReads(body)
      case Tree.InstanceOf(e, _, _, _) => collectReads(e)
      case Tree.ArrayAccess(arr, idx, _, _) => collectReads(arr); collectReads(idx)
      case Tree.ArrayLength(arr, _, _) => collectReads(arr)
      case Tree.NewArray(_, dims, init, _, _) => dims.foreach(collectReads); init.foreach(_.foreach(collectReads))
      case Tree.Repeated(elems, _, _) => elems.foreach(collectReads)
      case Tree.Spread(e, _, _) => collectReads(e)
      case Tree.Break(_, _, _) | Tree.Continue(_, _, _) => ()
      case Tree.Yield(v, _, _) => collectReads(v)
      case Tree.Assert(c, m, _, _) => collectReads(c); m.foreach(collectReads)
      case Tree.IncDec(target, _, _, _, _) => collectReads(target)
      case Tree.Synchronized(lock, body, _, _) => collectReads(lock); collectReads(body)
      case Tree.MethodRef(q, _, _, _, _) =>
        q match { case Left(_) => (); case Right(e) => collectReads(e) }
      case Tree.TypePattern(_, _, _, _) | Tree.RecordPattern(_, _, _, _) | Tree.BindPattern(_, _, _) => ()
      case _ =>
        // Fallback: use StandardTraversal for safety (conservative — counts everything as read)
        StandardTraversal.scanTerm(t, ()) {
          case (_, Tree.Ident(s, _, _))       => readSyms += s
          case (_, Tree.Select(_, s, _, _))   => readSyms += s
          case (_, Tree.Apply(_, _, m, _, _)) => readSyms += m
          case (acc, _) => acc
        }

    def collectStatements(stats: List[Statement]): Unit =
      stats.foreach {
        case t: Term        => collectReads(t)
        case d: Tree.DefDef => d.rhs.foreach(collectReads)
        case v: Tree.ValDef => v.rhs.foreach(collectReads)
        case _ => ()
      }

    // Walk all compilation units
    program.units.foreach { u =>
      StandardTraversal.allClassDefs(u).foreach { cd =>
        collectStatements(cd.body)
        StandardTraversal.allAnonClasses(cd).foreach { (anon, _) => collectStatements(anon.body) }
      }
    }

    // Also collect ALL refs conservatively (including Assign.lhs) with StandardTraversal
    val allRefs = collection.mutable.Set[SymId]()
    val allRefCollector = new Phase:
      def name = "unused-symbol/all-refs"
      override def transformTerm(t: Term)(using Program): Term =
        t match
          case Tree.Ident(s, _, _)           => allRefs += s
          case Tree.Select(_, s, _, _)       => allRefs += s
          case Tree.Apply(_, _, m, _, _)     => allRefs += m
          case _ => ()
        t
    program.units.foreach(u => StandardTraversal.mapClassDef(allRefCollector, u))

    // Three populations:
    //   notReferenced  = not in allRefs (never mentioned anywhere) -> DELETE or DISCARD
    //   writeOnly      = in allRefs but not in readSyms (only assigned to, never read) -> SUPPRESS
    //   read           = in readSyms -> leave alone

    // ---- Step 2: identify unused locals and private members ----
    val toDelete    = collection.mutable.Set[SymId]()
    val toDiscard   = collection.mutable.Set[SymId]()
    val toSuppress  = collection.mutable.Set[SymId]()

    def classifyPrivateMember(v: Tree.ValDef, s: Symbol, isWriteOnly: Boolean): Unit =
      if s.name == "serialVersionUID" then toSuppress += v.symbol
      else if isWriteOnly then toSuppress += v.symbol
      else if UnusedSymbolTransform.isSideEffectFree(v.rhs) then toDelete += v.symbol
      else toSuppress += v.symbol

    def classifyLocal(v: Tree.ValDef, isWriteOnly: Boolean): Unit =
      if isWriteOnly then toSuppress += v.symbol
      else if UnusedSymbolTransform.isSideEffectFree(v.rhs) then toDelete += v.symbol
      else toDiscard += v.symbol

    // Scan class bodies for unused PRIVATE members
    program.units.foreach { u =>
      StandardTraversal.allClassDefs(u).foreach { cd =>
        cd.body.foreach {
          case v: Tree.ValDef =>
            program.symbolOf(v.symbol).foreach { s =>
              if s.flags.isPrivate && !s.flags.isParam && !s.flags.isParamAccessor &&
                 !readSyms.contains(v.symbol) then
                val writeOnly = allRefs.contains(v.symbol) && !readSyms.contains(v.symbol)
                classifyPrivateMember(v, s, writeOnly)
            }
          // Private DEFs: only delete if genuinely unreferenced AND safe.
          // Never delete: constructors (<init>), setters (_=), equals/hashCode/toString (Object overrides).
          case d: Tree.DefDef =>
            program.symbolOf(d.symbol).foreach { s =>
              if s.flags.isPrivate && !s.flags.isParam && !allRefs.contains(d.symbol) &&
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
                   !readSyms.contains(v.symbol) then
                  val writeOnly = allRefs.contains(v.symbol) && !readSyms.contains(v.symbol)
                  classifyPrivateMember(v, s, writeOnly)
              }
            case d: Tree.DefDef =>
              program.symbolOf(d.symbol).foreach { s =>
                if s.flags.isPrivate && !s.flags.isParam && !allRefs.contains(d.symbol) &&
                   s.name != "<init>" && !s.name.endsWith("_=") &&
                   !Set("equals", "hashCode", "toString", "clone", "finalize").contains(s.name) then
                  toDelete += d.symbol
              }
            case _ => ()
          }
        }
      }
    }

    // Scan method bodies for unused LOCALS — walk with StandardTraversal
    val localCollector = new Phase:
      def name = "unused-symbol/local-collect"
      override def transformValDef(v: Tree.ValDef)(using p: Program): Tree.ValDef =
        if !readSyms.contains(v.symbol) then
          p.symbolOf(v.symbol).foreach { s =>
            if !s.flags.isParam && !s.flags.isParamAccessor &&
               !s.flags.isPrivate && !s.flags.isProtected &&
               !s.flags.isPackagePrivate then
              p.symbolOf(s.owner).foreach { os =>
                if os.descriptor.isDefined || os.name == "<init>" then
                  val writeOnly = allRefs.contains(v.symbol) && !readSyms.contains(v.symbol)
                  classifyLocal(v, writeOnly)
              }
          }
        v
    program.units.foreach(u => StandardTraversal.mapClassDef(localCollector, u))

    if toDelete.isEmpty && toDiscard.isEmpty && toSuppress.isEmpty then return program

    // ---- Step 3: record decisions ----
    (toDelete ++ toDiscard ++ toSuppress).toList.sortBy(_.raw).foreach { id =>
      program.symbolOf(id).foreach { s =>
        val action =
          if toDelete(id) then "deleted"
          else if toDiscard(id) then "discarded-binding"
          else "suppressed"
        val symbolKind =
          if s.name == "serialVersionUID" then "serialVersionUID (JVM reads reflectively)"
          else if s.flags.isPrivate then
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

    // ---- Step 5: add @nowarn annotations for suppressed symbols ----
    if toSuppress.isEmpty then return program.rebuilt(units, program.symbols)

    val existingNowarn = program.symbols.all.find(_.fullName == "scala.annotation.nowarn").map(_.id)
    val nowarnSym = existingNowarn.getOrElse {
      val minId = program.symbols.all.map(_.id.raw).minOption.getOrElse(0)
      SymId(math.min(minId - 1, -2))
    }
    val nowarnAnnot = Annot(
      tpe    = TypeRepr.TypeRef(TypeRepr.NoPrefix, nowarnSym),
      args   = List("value" -> Tree.Literal(
        Constant.StringC("msg=unused"),
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

    val needAnnot = toSuppress.toSet -- alreadyAnnotated
    if needAnnot.isEmpty then return program.rebuilt(units, program.symbols)

    val updated = program.symbols.all.map { s =>
      if needAnnot.contains(s.id) then s.copy(annotations = s.annotations :+ nowarnAnnot)
      else s
    }
    val allSyms = if existingNowarn.isDefined then updated
                  else updated ++ List(Symbol(
                    nowarnSym, "nowarn", "scala.annotation.nowarn",
                    Flags(), SymId.None, TypeRepr.NoType))

    program.rebuilt(units, SymbolTable(allSyms))

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
