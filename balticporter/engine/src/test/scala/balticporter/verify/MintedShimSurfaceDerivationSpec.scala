package balticporter.verify

import scala.meta.*
import balticporter.core.RuntimeArtifact
import balticporter.transform.CollectionsTransform

/** `CollectionsTransform.OverridesShim` says what the four STANDALONE targets declare, and this
  * suite is the derivation that proves it — `ENGINE-LIMITS.md` K28. */
class MintedShimSurfaceDerivationSpec extends munit.FunSuite:

  /** every member a trait DECLARES, abstract and concrete alike, as `(name, arity)`. */
  private def declared(source: String): Map[String, (Set[String], Set[(String, Int)])] =
    val tree = dialects.Scala3(source).parse[Source].get

    // the definition that OWNS a member — `RuntimeMembersDerivationSpec`'s own walk, and for its
    // reason: a `def` inside another `def`, an object or an anonymous body is not a trait member.
    def owner(t: Tree): Option[Tree] = t.parent.flatMap {
      case o @ (_: Defn.Trait | _: Defn.Class | _: Defn.Object | _: Defn.Def | _: Term.NewAnonymous) => Some(o)
      case other                                                                                     => owner(other)
    }

    tree.collect { case t: Defn.Trait => t }.map { t =>
      val fqn     = s"${RuntimeArtifact.Package}.${t.name.value}"
      val parents = t.templ.inits.map(_.tpe).collect {
        case Type.Apply(Type.Name(n), _) => s"${RuntimeArtifact.Package}.$n"
        case Type.Name(n)                => s"${RuntimeArtifact.Package}.$n"
      }.toSet
      val members = t.collect {
        case d: Defn.Def if owner(d).contains(t) && !d.mods.exists(_.is[Mod.Private]) =>
          (d.name.value, d.paramClauses.map(_.values.size).sum)
        case d: Decl.Def if owner(d).contains(t) && !d.mods.exists(_.is[Mod.Private]) =>
          (d.name.value, d.paramClauses.map(_.values.size).sum)
      }.toSet
      fqn -> (parents, members)
    }.toMap

  private lazy val all: Map[String, (Set[String], Set[(String, Int)])] =
    RuntimeArtifact.vendored.values.map(declared).reduce(_ ++ _)

  /** …closed over the shim's own parents, which is what an override actually sees. */
  private def surfaceOf(fqn: String): Set[(String, Int)] =
    all.get(fqn).map((ps, ms) => ms ++ ps.flatMap(surfaceOf)).getOrElse(Set.empty)

  test("every OverridesShim row is exactly what the published shim declares") {
    CollectionsTransform.OverridesShim.foreach { (fqn, row) =>
      assertEquals(row.map(m => m.name -> m.arity), surfaceOf(fqn),
                   s"$fqn's row has drifted from balticporter/runtime/src/main/scala")
    }
  }

  test("every STANDALONE target has a row — an absent one keeps every modifier, silently") {
    assertEquals(CollectionsTransform.OverridesShim.keySet, CollectionsTransform.standaloneTargets)
  }

  test("the derivation is not vacuous: JavaIterable declares iterator() and NOTHING else") {
    assertEquals(surfaceOf(s"${RuntimeArtifact.Package}.JavaIterable"), Set("iterator" -> 0))
  }

  test("a shim's PARENT contributes — JavaListIterator's surface includes JavaIterator's three") {
    val s = surfaceOf(s"${RuntimeArtifact.Package}.JavaListIterator")
    assert(clue(s).contains("hasNext" -> 0))
    assert(s.contains("next" -> 0))
    assert(s.contains("remove" -> 0))
    assert(s.contains("previousIndex" -> 0))
  }

  test("java's `forEach` family is on NO shim, which is why every one of them is an E037") {
    CollectionsTransform.standaloneTargets.foreach { fqn =>
      val s = surfaceOf(fqn)
      assert(!s.exists(_._1 == "forEach"), s"$fqn")
      assert(!s.exists(_._1 == "forEachRemaining"), s"$fqn")
      assert(!s.exists(_._1 == "spliterator"), s"$fqn")
    }
  }
