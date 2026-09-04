package balticporter.verify

import scala.meta.*
import balticporter.core.RuntimeArtifact

/** `TirEmitter`'s `externalConcrete` exists ONLY because the injected supertypes used to be
  * unparseable text. They are a real, published module now, so the data CAN be derived — and this
  * suite is the derivation, run against `RuntimeArtifact.concreteMembers` to prove the declared
  * table is not merely plausible. */
class RuntimeMembersDerivationSpec extends munit.FunSuite:

  /** the CONCRETE instance members a support type brings, as `(name, params per clause)`. */
  private def derive(source: String): Map[String, Set[(String, List[Int])]] =
    val tree = dialects.Scala3(source).parse[Source].get

    // the definition that OWNS a member: the nearest enclosing template-bearing tree. A `def`
    // nested inside another `def`, an object or an anonymous class is not a member of the trait.
    def owner(t: Tree): Option[Tree] = t.parent.flatMap {
      case o @ (_: Defn.Trait | _: Defn.Class | _: Defn.Object | _: Defn.Def | _: Term.NewAnonymous) => Some(o)
      case other                                                                                     => owner(other)
    }

    tree.collect { case t: Defn.Trait => t }.map { t =>
      val fqn = s"${RuntimeArtifact.Package}.${t.name.value}"
      val concrete = t.collect {
        // Defn.Def has a body; Decl.Def is abstract. That is the whole distinction.
        case d: Defn.Def if owner(d).contains(t) && !d.mods.exists(_.is[Mod.Private]) =>
          (d.name.value, d.paramClauses.map(_.values.size).toList)
      }.toSet
      fqn -> concrete
    }.toMap

  private def sources = RuntimeArtifact.vendored

  test("the declared external-concrete table equals what the published module actually declares") {
    val derived = sources.values.map(derive).reduce(_ ++ _)
    assertEquals(derived, RuntimeArtifact.concreteMembers)
  }

  test("the derivation is not vacuous: JavaIterator's remove is concrete and nilary") {
    val d = derive(sources(s"${RuntimeArtifact.Package}.JavaIterator"))
    assertEquals(d(s"${RuntimeArtifact.Package}.JavaIterator"), Set(("remove", List(0))))
    // hasNext()/next() are DECLARATIONS — abstract, so not external-concrete, so not a diamond
    assert(!d(s"${RuntimeArtifact.Package}.JavaIterator").exists(_._1 == "hasNext"))
  }

  test("a nilary def and a parameterless def are distinguished — the thing reflection cannot do") {
    val d = derive("""package balticporter.runtime
                     |trait T:
                     |  def nilary(): Unit = ()
                     |  def parameterless: Unit = ()
                     |""".stripMargin)
    assertEquals(
      d(s"${RuntimeArtifact.Package}.T"),
      Set(("nilary", List(0)), ("parameterless", List.empty[Int])),
    )
  }
