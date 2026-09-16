package balticporter.transform

import balticporter.core.{ MergeablePolicy, PolicyFinding, PolicyIssue, PolicyReport, PolicySource, SurfacePolicy }
import balticporter.tir.*

/** Makes a listed java `static final` SCRATCH field thread-confined: the companion's `val f: T = e` becomes `private val f$tl = new ThreadLocal[T] { override def initialValue(): T = e }` beside
  * `def f: T = f$tl.get()`, so every `Owner.f` reference still compiles and each thread reads its own instance. `initialValue`, never `withInitial`: the Scala.js javalib has no such factory (the
  * reference port's own note). CLAUDE.md §1(b): `fields` keys `owner#name`; empty = no-op. Refused and COUNTED (`policyReport`, one row naming the guard): a field that is not static, not final,
  * written after initialisation anywhere in the program, or whose initialiser is not a fresh allocation — each would change what java's single shared instance meant.
  */
final class ThreadConfinedStaticsTransform(val fields: Set[String] = Set.empty) extends Phase, PolicySource, SurfacePolicy, MergeablePolicy, PolicyBound:
  import ThreadConfinedStaticsTransform.*

  def name: String = "thread-confined-statics"

  def surfaceFingerprint: String =
    if fields.isEmpty then "" else s"fields=${fields.toList.sorted.mkString(",")}"

  def subjects: Set[String] = fields.map(MergeablePolicy.subjectOf)

  /** Independent keys union; the same key stated twice is the same decision (`base.extendedBy(…)` can do that legitimately). */
  def mergedWith(later: Phase): Either[String, MergeablePolicy.Merged] = later match
    case o: ThreadConfinedStaticsTransform =>
      Right(
        MergeablePolicy.Merged(new ThreadConfinedStaticsTransform(fields ++ o.fields), (o.fields -- fields).map(MergeablePolicy.subjectOf))
      )
    case _ => Left(s"expected ThreadConfinedStaticsTransform, got ${later.getClass.getSimpleName}")

  private var bySym:    Map[SymId, String]        = Map.empty
  private var records:  List[PolicyBinder.Record] = Nil
  private var runScope: RunScope                  = RunScope.whole
  private var refused:  List[PolicyFinding]       = Nil

  def bindPolicy(binder: PolicyBinder): Unit =
    runScope = binder.run
    bySym = fields.toList.sorted.flatMap { k =>
      binder.bindMembers(name, Setting, k).toOption.getOrElse(Nil).flatMap(_.sym).map(_ -> k)
    }.toMap
    records = binder.recordsFor(name)

  /** unmatched keys from the binding, plus every field [[run]] declined — each row names the guard that refused it. */
  def policyReport: PolicyReport = PolicyReport.fromBindings(records) ++ PolicyReport(refused)

  override def run(program: Program): Program =
    refused = Nil
    if bySym.isEmpty then return program
    given Program = program
    val mint      = new ClassTagParamsTransform.Mint(program)
    // `java.lang.ThreadLocal` as a TYPE the emitter renders, never as text: the holder is `new ThreadLocal[T] { override def initialValue(): T = … }`
    // — the reference's own spelling — because the Scala.js javalib ships the class and `initialValue`/`get` but NOT the `withInitial` static factory.
    lazy val threadLocalSym: SymId = mint.tpe("ThreadLocal", "java.lang.ThreadLocal")

    // a write ANYWHERE in the program is enough: the java field was one instance every writer saw.
    val written: Set[SymId] = program.units.foldLeft(Set.empty[SymId]) { (acc, cd) =>
      StandardTraversal.scanClassDef(cd, acc) { (a, t) =>
        t match
          case Tree.Assign(Tree.Select(_, s, _, _), _, _, _, _) if bySym.contains(s) => a + s
          case Tree.Assign(Tree.Ident(s, _, _), _, _, _, _) if bySym.contains(s)     => a + s
          case _                                                                     => a
      }
    }

    def refuse(key: String, guard: String, issue: PolicyIssue, why: String): Unit =
      refused = refused :+ PolicyFinding(name, Setting, key, issue, s"refused by `$guard`: $why")

    def freshAllocation(t: Term): Boolean = t match
      case _: Tree.New => true
      case Tree.Apply(_: Tree.New, _, _, _, _) => true
      case _                                   => false

    def rewriteVal(v: Tree.ValDef, owner: String): List[Statement] =
      (bySym.get(v.symbol), program.symbolOf(v.symbol)) match
        case (Some(key), Some(s)) if program.owned(v.symbol) && runScope.emitsSymbol(program, v.symbol) =>
          if !s.flags.isStatic then
            refuse(
              key,
              GuardNotStatic,
              PolicyIssue.Malformed,
              "an instance field is already one per object; only a java `static` is shared across threads"
            )
            List(v)
          else if !s.flags.isFinal || s.flags.isMutable then
            refuse(
              key,
              GuardNotFinal,
              PolicyIssue.Malformed,
              "a non-final static can be reassigned; a per-thread holder would hide the write from other threads"
            )
            List(v)
          else if written.contains(v.symbol) then
            refuse(
              key,
              GuardAssignedAfterInit,
              PolicyIssue.Unverifiable,
              "the field is assigned after initialisation; a per-thread holder would hide the write from other threads"
            )
            List(v)
          else
            v.rhs.filter(freshAllocation) match
              case None =>
                refuse(
                  key,
                  GuardInitialiserNotFresh,
                  PolicyIssue.Unverifiable,
                  "the initialiser is not a fresh allocation (`new T(...)`), so evaluating it once per thread may not mean the same as once per class"
                )
                List(v)
              case Some(init) =>
                val holder = holderName(s.name)
                record(
                  Decision(
                    kind = Decision.Kind.ThreadConfinedStatic,
                    subject = v.symbol,
                    subjectFqn = MemberKey(owner, s.name).render,
                    detail = Map(
                      "key" -> key,
                      "holder" -> holder,
                      "from" -> "one companion `val`, shared by every thread",
                      "to" -> s"`def ${s.name}` reading a `java.lang.ThreadLocal` whose `initialValue()` is the java initialiser, evaluated once per thread",
                      "why" -> "a scratch field java shares across threads is confined to the reading thread; every `Owner.name` reference keeps compiling"
                    ),
                    reason = Reason.Configured(name, key),
                    origin = v.origin
                  )
                )
                val elem      = v.tpt.tpe
                val tlType    = TypeRepr.AppliedType(TypeRepr.TypeRef(TypeRepr.NoPrefix, threadLocalSym), List(elem))
                val holderSym = mint.member(holder, MemberKey(owner, holder).render, s.owner, tlType, Flags(isStatic = true, isFinal = true, isPrivate = true))
                val anonFqn   = s"$owner.$holder$$anon"
                val anonSym   = mint.tpe(s"$holder$$anon", anonFqn)
                val initSym   = mint.member("initialValue", MemberKey(anonFqn, "initialValue").render, anonSym, TypeRepr.MethodType(Nil, elem), Flags(isOverride = true))
                val initDef   = Tree.DefDef(initSym, paramss = List(Nil), returnTpt = v.tpt, rhs = Some(init), origin = v.origin)
                val holderDef = Tree.ValDef(
                  holderSym,
                  TypeTree(tlType, v.origin),
                  Some(Tree.New(TypeTree(tlType, v.origin), tlType, v.origin, Some(Tree.AnonClass(anonSym, List(initDef), v.origin)))),
                  v.origin
                )
                val accessor = Tree.DefDef(
                  symbol = v.symbol,
                  paramss = Nil,
                  returnTpt = v.tpt,
                  rhs = Some(Tree.Opaque(s"$holder.get()", v.tpt.tpe, v.origin)),
                  origin = v.origin,
                  leading = v.leading
                )
                List(holderDef, accessor)
        case _ => List(v)

    def rewrite(cd: Tree.ClassDef): Tree.ClassDef =
      val owner = program.symbolOf(cd.symbol).map(_.fullName).getOrElse("")
      val body  = cd.body.flatMap {
        case v: Tree.ValDef   => rewriteVal(v, owner)
        case c: Tree.ClassDef => List(rewrite(c))
        case other => List(other)
      }
      cd.copy(body = body)

    val units = program.units.map(rewrite)
    program.rebuilt(units, symbols = SymbolTable(program.symbols.all ++ mint.minted)) // xref rebuilt by the Pipeline

object ThreadConfinedStaticsTransform:
  val Setting = "ThreadConfinedStaticsTransform(fields)"

  val GuardNotStatic           = "not-static"
  val GuardNotFinal            = "not-final"
  val GuardAssignedAfterInit   = "assigned-after-init"
  val GuardInitialiserNotFresh = "initialiser-not-fresh-allocation"

  /** the per-thread holder's name beside the field it stands for. */
  def holderName(field: String): String = s"$field$$tl"
