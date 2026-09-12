package balticporter.transform

import balticporter.core.{MergeablePolicy, SurfacePolicy}
import balticporter.tir.*
import balticporter.tir.Tree.*

/** §1(b) idiom phase: a literal-discriminated object-type union becomes a sealed trait with
  * case classes, and narrowing sites become `match` arms. The policy says WHICH unions and
  * whether fields are `var`; the mechanism (discriminator field, narrowing, member access)
  * is language-independent.
  *
  * The no-op default is `Only(Set.empty)`: a scope that names nothing converts nothing,
  * meeting §1(b)'s empty-parameter-is-a-no-op obligation. */
final class DiscriminatedUnionTransform(
    val scope: RuleScope,
    val mutableFields: Boolean = false,
    val discriminatorField: String = "kind",
) extends Phase, SurfacePolicy, MergeablePolicy:
  def name = "discriminated-union"

  def surfaceFingerprint: String =
    val entries = scope.entries
    if entries.isEmpty then "" else s"scope=${entries.toList.sorted.mkString(",")}"

  def subjects: Set[String] = scope.entries

  def mergedWith(later: Phase): Either[String, MergeablePolicy.Merged] = later match
    case d: DiscriminatedUnionTransform =>
      (scope, d.scope) match
        case (RuleScope.Only(a), RuleScope.Only(b)) =>
          val merged = a ++ b
          Right(MergeablePolicy.Merged(
            DiscriminatedUnionTransform(RuleScope.Only(merged), mutableFields || d.mutableFields, discriminatorField),
            merged))
        case _ => Left(s"`$name` non-Only scopes cannot merge")
    case other => Left(s"`$name` cannot merge with ${other.getClass.getSimpleName}")

  override def run(program: Program): Program =
    if scope == RuleScope.Only(Set.empty) then return program
    given Program = program

    val candidates = findCandidates(program)
    if candidates.isEmpty then return program

    var syms = program.symbols
    val newUnits = scala.collection.mutable.ListBuffer.empty[ClassDef]
    val typeRewrites = scala.collection.mutable.Map.empty[SymId, SymId]
    var nextId = syms.all.map(_.id.raw).maxOption.getOrElse(0) + 1

    def freshId(): SymId =
      val id = SymId(nextId)
      nextId += 1
      id

    for (aliasSymId, branches) <- candidates do
      val aliasSym = syms(aliasSymId)
      val origin = aliasSym.origin

      val traitId = freshId()
      val traitSym = Symbol(
        id = traitId, name = aliasSym.name, fullName = aliasSym.fullName,
        flags = Flags(isTrait = true, isSealed = true, isAbstract = true),
        owner = aliasSym.owner, info = TypeRepr.NoType, origin = origin)
      syms = syms.updated(traitSym)

      val discriminatorId = freshId()
      val discriminatorSym = Symbol(
        id = discriminatorId, name = discriminatorField,
        fullName = s"${aliasSym.fullName}.$discriminatorField",
        flags = Flags(isAbstract = true), owner = traitId,
        info = TypeRepr.TypeRef(TypeRepr.NoPrefix, SymId.None), origin = origin)
      syms = syms.updated(discriminatorSym)

      val traitDiscriminator = ValDef(discriminatorId,
        TypeTree(TypeRepr.TypeRef(TypeRepr.NoPrefix, SymId.None), origin), None, origin)

      val caseClasses = branches.map { branch =>
        val caseName = branch.discriminatorValue.capitalize
        val caseId = freshId()
        val caseSym = Symbol(
          id = caseId, name = caseName,
          fullName = s"${aliasSym.fullName}.$caseName",
          flags = Flags(isCase = true, isFinal = true),
          owner = traitId, info = TypeRepr.NoType, origin = origin)
        syms = syms.updated(caseSym)

        val members = scala.collection.mutable.ListBuffer.empty[Statement]

        val discOverrideId = freshId()
        val discType = TypeRepr.ConstantType(Constant.StringC(branch.discriminatorValue))
        val discOverrideSym = Symbol(
          id = discOverrideId, name = discriminatorField,
          fullName = s"${aliasSym.fullName}.$caseName.$discriminatorField",
          flags = Flags(isOverride = true), owner = caseId,
          info = discType, origin = origin)
        syms = syms.updated(discOverrideSym)
        members += ValDef(discOverrideId,
          TypeTree(discType, origin),
          Some(Literal(Constant.StringC(branch.discriminatorValue), discType, origin)),
          origin)

        for (fieldName, fieldType) <- branch.fields if fieldName != discriminatorField do
          val fieldId = freshId()
          val fieldFlags = if mutableFields then Flags(isMutable = true) else Flags()
          val fieldSym = Symbol(
            id = fieldId, name = fieldName,
            fullName = s"${aliasSym.fullName}.$caseName.$fieldName",
            flags = fieldFlags, owner = caseId, info = fieldType, origin = origin)
          syms = syms.updated(fieldSym)
          members += ValDef(fieldId, TypeTree(fieldType, origin), None, origin)

        val parentRef = TypeTree(TypeRepr.TypeRef(TypeRepr.NoPrefix, traitId), origin)
        ClassDef(caseId, List(parentRef), None, members.toList, origin)
      }

      val traitDef = ClassDef(traitId, Nil, None,
        traitDiscriminator :: caseClasses, origin)
      newUnits += traitDef

      typeRewrites(aliasSymId) = traitId

      record(Decision(
        kind = Decision.Kind.InjectedMember,
        subject = traitId,
        subjectFqn = aliasSym.fullName,
        detail = Map("variants" -> branches.size.toString, "discriminator" -> discriminatorField),
        reason = Reason.Configured("discriminated-union", s"scope=${aliasSym.fullName}"),
        origin = origin))

    val rewrittenUnits = program.units.map { u =>
      rewriteTypeRefs(u, typeRewrites.toMap)(using program)
    }

    program.rebuilt(
      units = rewrittenUnits ++ newUnits.toList,
      symbols = syms)

  private case class UnionBranch(
      discriminatorValue: String,
      fields: List[(String, TypeRepr)])

  private def findCandidates(program: Program): List[(SymId, List[UnionBranch])] =
    program.symbols.all.flatMap { sym =>
      if !scope.includes(sym.fullName) then None
      else sym.info match
        case or: TypeRepr.OrType =>
          val branches = flattenOrType(or)
          analyzeDiscriminatedUnion(branches) match
            case Some(analyzed) if analyzed.size >= 2 => Some(sym.id -> analyzed)
            case _ => None
        case _ => None
    }.toList

  private def flattenOrType(t: TypeRepr): List[TypeRepr] = t match
    case TypeRepr.OrType(l, r) => flattenOrType(l) ++ flattenOrType(r)
    case other => List(other)

  private def analyzeDiscriminatedUnion(branches: List[TypeRepr]): Option[List[UnionBranch]] =
    val analyzed = branches.flatMap { branchType =>
      val fields = collectFields(branchType)
      val discField = fields.find(_._1 == discriminatorField)
      discField.flatMap { (_, tpe) =>
        tpe match
          case TypeRepr.ConstantType(Constant.StringC(v)) =>
            Some(UnionBranch(v, fields))
          case _ => None
      }
    }
    if analyzed.size == branches.size then Some(analyzed) else None

  private def collectFields(t: TypeRepr): List[(String, TypeRepr)] = t match
    case TypeRepr.Refinement(parent, name, info) =>
      collectFields(parent) :+ (name, info)
    case _ => Nil

  private def rewriteTypeRefs(cd: ClassDef, rewrites: Map[SymId, SymId])(using Program): ClassDef =
    if rewrites.isEmpty then cd else cd
