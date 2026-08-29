package balticporter.transform

import balticporter.tir.*

/** A LATE phase that annotates members whose bodies contain deprecated references with
  * `@scala.annotation.nowarn("msg=deprecated")`.
  *
  * ==Why this is a separate phase==
  * The scan was inside `NullabilityTransform`, which runs BEFORE the retarget phases. A retarget
  * that removes the deprecated reference leaves the `@nowarn` annotation in place and
  * `-Wunused:nowarn` reports it: 237 stale annotations on libGDX core after the Array -> DynamicArray
  * retarget. Running the scan LATE — after every retyping phase — means it sees the FINAL tree and
  * annotates only members that still contain a deprecated call.
  *
  * ==Kind==
  * CLAUDE.md §1(a). The mechanism is universal — annotate where a deprecated symbol is called, so
  * the reference compile under `-Werror` does not fail on a lint the port cannot avoid. No policy:
  * every port under `-Werror -deprecation` needs this, and which symbols are deprecated is a fact
  * about the TARGET LANGUAGE'S libraries.
  *
  * ==Position==
  * `runsAfter` every retyping phase: `nullability`, `java-collections->scala`, `type-redirect`,
  * `globals->implicits`. `runsBefore` `package-rename` (the annotation's FQN is in the scala
  * namespace, not the upstream one). A base declares an empty instance at this position (§1.5)
  * so dependents that inherit it get the scan at the right place. */
final class SuppressionPhase extends Phase:

  def name = SuppressionPhase.Name

  // Run AFTER every retyping phase so the scan sees the final tree
  override def runsAfter: Set[String] = Set(
    "nullability",
    "java-collections->scala",
    "type-redirect",
    "globals->implicits",
  )
  override def runsBefore: Set[String] = Set("package-rename")

  override def run(program: Program): Program =
    // Find the `.orNull` symbol(s) — any member named "orNull" whose owner is a wrapper/Option type.
    // The NullabilityTransform has already minted or reused these; we look them up by name.
    val orNullSyms: Set[SymId] = program.symbols.all.iterator
      .filter(s => s.name == "orNull" && s.fullName.endsWith(".orNull"))
      .map(_.id).toSet

    if orNullSyms.isEmpty then return program

    // Scan the FINAL tree for members whose bodies reference `.orNull`
    val annotated = collection.mutable.Set[SymId]()

    def hasOrNull(body: Term): Boolean = StandardTraversal.scanTerm(body, false) {
      case (true, _) => true
      case (_, Tree.Select(_, s, _, _)) if orNullSyms(s) => true
      case (acc, _) => acc
    }

    /** Scan a class body for `.orNull` references.
      *
      * This phase runs AFTER TestFrameworkTransform, so a converted test's body is already a
      * class-body statement — the `case t: Term` arm catches it. No ownerFallback annotation
      * on the class is needed (the old scan in NullabilityTransform annotated the class too
      * because TestFrameworkTransform might later remove the DefDef; HERE the tree is final). */
    def scanBody(members: List[Statement], classSym: SymId): Unit =
      members.foreach {
        case d: Tree.DefDef =>
          d.rhs.foreach { body => if hasOrNull(body) then annotated += d.symbol }
        case v: Tree.ValDef =>
          v.rhs.foreach { body => if hasOrNull(body) then annotated += v.symbol }
        case _: Tree.ClassDef | _: Tree.TypeDef => ()
        case t: Term =>
          // a class-body STATEMENT (primary constructor code, or a test body that
          // TestFrameworkTransform already inlined) — annotate the CLASS
          if hasOrNull(t) then annotated += classSym
        case _ => ()
      }

    given Program = program
    program.units.foreach { u =>
      StandardTraversal.allClassDefs(u).foreach { cd =>
        scanBody(cd.body, cd.symbol)
        StandardTraversal.allAnonClasses(cd).foreach { (anon, _) =>
          scanBody(anon.body, anon.symbol)
        }
      }
    }

    if annotated.isEmpty then return program

    // Find or create the @nowarn symbol
    val existingNowarn = program.symbols.all.find(_.fullName == "scala.annotation.nowarn").map(_.id)
    val nowarnSym = existingNowarn.getOrElse {
      val minId = program.symbols.all.map(_.id.raw).minOption.getOrElse(0)
      SymId(math.min(minId - 1, -2))
    }
    val nowarnAnnot = Annot(
      tpe    = TypeRepr.TypeRef(TypeRepr.NoPrefix, nowarnSym),
      args   = List("value" -> Tree.Literal(
        Constant.StringC("msg=deprecated"),
        TypeRepr.TypeRef(TypeRepr.NoPrefix, SymId.None),
        Origin.synthetic)),
      origin = Origin.synthetic,
    )

    // Add annotations to symbols that have .orNull calls BUT DO NOT already have the annotation
    // (NullabilityTransform may have already annotated some in the same pipeline run if a later
    // phase did not remove the reference)
    val alreadyAnnotated = program.symbols.all.filter { s =>
      s.annotations.exists(a =>
        program.symbolOf(a.tpe match {
          case TypeRepr.TypeRef(_, sym) => sym
          case _ => SymId.None
        }).exists(_.fullName == "scala.annotation.nowarn"))
    }.map(_.id).toSet

    val toAnnotate = annotated.toSet -- alreadyAnnotated
    if toAnnotate.isEmpty then return program

    val updated = program.symbols.all.map { s =>
      if toAnnotate.contains(s.id) then s.copy(annotations = s.annotations :+ nowarnAnnot)
      else s
    }
    val allSyms = if existingNowarn.isDefined then updated
                  else updated ++ List(Symbol(
                    nowarnSym, "nowarn", "scala.annotation.nowarn",
                    Flags(), SymId.None, TypeRepr.NoType))

    // Record decisions
    toAnnotate.toList.sortBy(_.raw).foreach { id =>
      program.symbolOf(id).foreach { s =>
        record(Decision(
          kind       = Decision.Kind.SuppressedWarning,
          subject    = id,
          subjectFqn = s.fullName,
          detail = Map(
            "annotation" -> "@nowarn(\"msg=deprecated\")",
            "why"        -> ("this member's body calls `.orNull` (the null-preserving unwrap at a " +
              "slot that accepts null); lls deprecates `orNull` as a lint so every usage needs " +
              "`@nowarn` — the same pattern sge uses at every Java interop boundary"),
          ),
          reason = Reason.Universal("suppressed-warning(orNull)"),
          origin = Decision.originOf(program, id),
        ))
      }
    }

    program.rebuilt(symbols = SymbolTable(allSyms))

object SuppressionPhase:
  val Name = "suppressed-warnings"
