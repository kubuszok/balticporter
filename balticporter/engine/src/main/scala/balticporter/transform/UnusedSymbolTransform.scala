package balticporter.transform

import balticporter.tir.*

/** A LATE phase that removes or suppresses unused local definitions and private members.
  *
  * Java allows unused symbols; Scala's strict `-Wunused` flags do not, and the reference compile
  * promotes every survivor to an error. For each unused definition, the FIRST applicable action
  * wins: DELETE (side-effect-free, unread/unwritten), DISCARD (keep a possibly-effectful
  * initialiser, drop the binding), SUPPRESS (`@nowarn`), or REFUSE — a non-private member (API
  * surface) or a private one whose name appears in a `MethodBodyTransform` substitution body
  * (`Tree.Opaque`, invisible to the TIR walk — CLAUDE.md §1.5), conservatively treated as
  * referenced. `allCounts`/`assignCounts` from one `StandardTraversal` walk classify READ,
  * WRITE-ONLY and UNREFERENCED.
  *
  * CLAUDE.md §1(a) universal. `unused-symbol(handled|refused)` required of every run. Runs after
  * every retyping phase, before `package-rename` and `suppressed-warnings`. */
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
          case Tree.MethodRef(_, m, _, _, _) => allCounts(m) += 1
          case _ => ()
        t

    program.units.foreach(u => StandardTraversal.mapClassDef(refCollector, u))

    def isRead(s: SymId): Boolean        = allCounts(s) > assignCounts(s)
    def isWriteOnly(s: SymId): Boolean   = allCounts(s) > 0 && allCounts(s) == assignCounts(s)
    def isUnreferenced(s: SymId): Boolean = allCounts(s) == 0

    // ---- Step 1b: collect substituted-body words per owning class ----
    // Tree.Opaque is verbatim Scala text the TIR walk cannot see; a symbol whose name appears as a
    // word-boundary token in it is treated as referenced (conservative refusal).
    val substWords = collection.mutable.Map[SymId, Set[String]]().withDefaultValue(Set.empty)
    val opaqueCollector = new Phase:
      def name = "unused-symbol/opaque-collect"
      override def transformDefDef(d: Tree.DefDef)(using p: Program): Tree.DefDef =
        d.rhs.foreach {
          case Tree.Opaque(raw, _, _,  _) =>
            val masked = raw
              .replaceAll("//[^\n]*", "")
              .replaceAll("/\\*[^*]*\\*+(?:[^/*][^*]*\\*+)*/", "")
            val words = masked.split("[^a-zA-Z0-9_$]+").filter(_.nonEmpty).toSet
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
