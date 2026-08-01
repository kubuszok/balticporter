package balticporter.transform

import java.nio.file.{Files, Path}

/** The phase's HANDLED TABLES against the phase's own arms — a bijection, both directions.
  *
  * `CollectionsTransform.handledStatics` and `handledInstance` exist so `JdkSurfaceCheck` can ask
  * what this phase covers instead of guessing it from a type's name (CLAUDE.md §4.56). A table
  * beside the code it describes is only worth having while the two agree, and nothing about a
  * `Set[String]` beside a `match` makes them agree — the arms compile with the table stale, the
  * table compiles with an arm removed, and the check then reports a mapping that is not there or a
  * hole that is filled. Both are silent: no compile error, no check count, no test.
  *
  * So the table is derived from the SOURCE TEXT of the phase and compared, the way
  * `RuntimeMembersDerivationSpec` derives the runtime member map from the published runtime
  * sources. Both directions, because they fail differently: a missing table entry makes a handled
  * member read as the port's JDK wall, and a stale entry makes a real hole read as covered.
  *
  * ==The scan is SLICED to the arms, and that is not tidiness==
  * The tables now live in the same FILE as the arms, so a scan over the whole source would find its
  * own declaration and pass vacuously in both directions — the exact failure this spec exists to
  * prevent, wearing the spec's own clothes. Each derivation therefore cuts the region between the
  * function it is reading and the next top-level doc comment, and asserts the region does not
  * contain the table it is about to compare against.
  *
  * ==What is pinned and what is not==
  * The static table is pinned EXACTLY: those arms are keyed on `owner#name` string literals, so the
  * derivation is the set of literals. The instance table is pinned as a UNION of member NAMES: the
  * arms are keyed on `(name, args, kind)` — one `add` arm serves `java.util.List`,
  * `java.util.Collection`, `java.util.Queue` and every other Seq — and the KIND is a `Kind.X`
  * pattern rather than a literal. Splitting the union across kinds is read by eye and is
  * deliberately not asserted here: assigning a name to more kinds than the arm covers can only make
  * the check kinder (a "mapped" row that could have been a finding), never make it miss a hole in a
  * kind that genuinely has none.
  *
  * The OTHER direction is not symmetric and is worth naming, because it is the one that costs
  * something: a name assigned to FEWER kinds than its arm covers makes `jdk-surface` report
  * `unhandled` for a member the phase demonstrably rewrites — a finding that is simply false. That
  * is not a kind error, it is a REPORT-CREDIBILITY error (§4.45): the agent it is written for is in
  * another repository, and it spends a full investigation on a hole that is already filled. One
  * such row teaches that this check's findings need checking, which is worse than the check not
  * existing. So when a union is split by hand, err WIDE.
  */
class CollectionsHandledDerivationSpec extends munit.FunSuite:

  private val source: String =
    val is = Option(getClass.getClassLoader.getResourceAsStream("balticporter/engine-source-dir.txt"))
      .getOrElse(fail("balticporter/engine-source-dir.txt is missing — engine's Test resourceGenerator did not run"))
    val dir = try new String(is.readAllBytes(), "UTF-8").trim finally is.close()
    Files.readString(Path.of(dir).resolve("balticporter/transform/CollectionsTransform.scala"))

  /** the text of ONE function's arms: from its `def` to the next TOP-LEVEL doc comment (two spaces
    * of indent). A doc comment nested inside the function is indented four and cannot end the cut,
    * which is what lets `rewrite`'s `onShim` note stay where it is. */
  private def region(defLine: String): String =
    val from = source.indexOf(defLine)
    assert(from >= 0, s"`$defLine` is not in CollectionsTransform — the derivation reads the wrong shape")
    val to = source.indexOf("\n  /** ", from)
    assert(to > from, s"no top-level doc comment closes `$defLine` — the cut would run to EOF")
    source.substring(from, to)

  private val staticArms   = region("private def staticRewrite")
  private val instanceArms = region("private def rewrite(k: Kind")

  /** every `"owner#name"` STRING LITERAL in the static arms — which is exactly how `staticRewrite`
    * identifies a receiver-less JDK member, and the only way it identifies one. */
  private def staticLiterals: Set[String] =
    """"(java\.[A-Za-z0-9_.$]+#[A-Za-z0-9_]+)"""".r.findAllMatchIn(staticArms).map(_.group(1)).toSet

  /** every member NAME an instance arm is keyed on: the string literals in the first element of a
    * `case ("name" | "other", …)` head. */
  private def instanceArmNames: Set[String] =
    """(?m)^\s*case \((("[A-Za-z0-9_]+"(\s*\|\s*)?)+),""".r
      .findAllMatchIn(instanceArms)
      .flatMap(m => """"([A-Za-z0-9_]+)"""".r.findAllMatchIn(m.group(1)).map(_.group(1)))
      .toSet

  /** …plus the `parenless` set, which is an arm of its own (`case (n, Nil, _) if parenless(n)`) and
    * is declared outside the region above. */
  private def parenlessNames: Set[String] =
    """(?s)private val parenless = Set\((.*?)\)""".r.findFirstMatchIn(source)
      .map(m => """"([A-Za-z0-9_]+)"""".r.findAllMatchIn(m.group(1)).map(_.group(1)).toSet)
      .getOrElse(fail("could not find the `parenless` set — the derivation is reading the wrong shape"))

  test("the derivation reads the ARMS and not the tables — a scan that found its own answer passes vacuously") {
    assert(clue(staticArms).contains("case _ => None"), "the static region does not reach the fall-through arm")
    assert(!staticArms.contains("handledStatics"), "the static region has swallowed the table it is checking")
    assert(!instanceArms.contains("handledInstance"), "the instance region has swallowed the table it is checking")
    assert(clue(staticLiterals).nonEmpty)
    assert(clue(instanceArmNames).nonEmpty)
  }

  test("handledStatics ⊇ every `owner#name` the arms match — no arm is missing from the table") {
    val missing = staticLiterals -- CollectionsTransform.handledStatics
    assert(missing.isEmpty,
      s"""${missing.size} member(s) are matched by a `CollectionsTransform` arm and absent from
         |`handledStatics`, so `jdk-surface` will report them as the port's JDK wall:
         |${missing.toList.sorted.map("  " + _).mkString("\n")}""".stripMargin)
  }

  test("…and ⊆ — no table entry names an arm that is not there") {
    val stale = CollectionsTransform.handledStatics -- staticLiterals
    assert(stale.isEmpty,
      s"""${stale.size} `handledStatics` entry(ies) match no arm in `CollectionsTransform`, so
         |`jdk-surface` will report a mapping the phase does not have:
         |${stale.toList.sorted.map("  " + _).mkString("\n")}""".stripMargin)
  }

  test("handledInstance's union is exactly the instance arms' names, both directions") {
    val declared = CollectionsTransform.handledInstance.values.flatten.toSet
    val derived  = instanceArmNames ++ parenlessNames
    assertEquals(clue(derived -- declared), Set.empty[String], "arm names absent from `handledInstance`")
    assertEquals(clue(declared -- derived), Set.empty[String], "`handledInstance` entries with no arm")
  }

  test("every kind key is a real `Kind` (or the any-kind wildcard) — a typo is a silently empty lane") {
    val known = CollectionsTransform.Kind.values.map(_.toString).toSet + balticporter.tir.JdkSurfaceCheck.AnyKind
    assertEquals(clue(CollectionsTransform.handledInstance.keySet -- known), Set.empty[String])
  }

  test("no retype TARGET is null — the object's vals initialise in declaration order") {
    // `typeMap` names `JavaCollectionFqn`/`JavaIterableFqn`/`JavaIteratorFqn`. Declared above them
    // it is built with nulls, no compile error, and the phase throws inside `run` — measured at 49
    // corpus tests. Nothing else in the pipeline can see this before it detonates.
    val bad = CollectionsTransform.typeMap.filter((_, v) => v._1 == null).keys.toList.sorted
    assertEquals(clue(bad), Nil, "typeMap has been declared ABOVE the *Fqn vals it references")
  }

  test("the mapping the check reads IS the phase's own typeMap — not a second copy") {
    val m = CollectionsTransform.jdkMapping(ran = true)
    assertEquals(m.types.keySet, CollectionsTransform.typeMap.keySet)
    assertEquals(m.types("java.util.List"), ("scala.collection.mutable.Buffer", "Seq"))
    assert(clue(m.shimMembers).get(CollectionsTransform.JavaIteratorFqn).exists(_.contains("remove")),
      "the shim member map is not reaching the check — `java.util.Iterator#remove` would read as a hole")
  }
