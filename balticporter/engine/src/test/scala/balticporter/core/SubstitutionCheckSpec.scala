package balticporter.core

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** [[SubstitutionCheck]] is a LIFT, not a redesign — CHECK 1 and CHECK 2 were inline in
  * `LibgdxCoreMigrate` and had to keep behaving exactly as they did. */
class SubstitutionCheckSpec extends munit.FunSuite:

  // ---- the originals, copied verbatim from LibgdxCoreMigrate before the lift ----

  /** `LibgdxCoreMigrate.scala:203` */
  private def originalCheck1(outDir: Path, subs: Substitutions): Set[String] =
    subs.dropTypes.filter(fqn => Files.exists(outDir.resolve(fqn.replace('.', '/') + ".scala")))

  /** `LibgdxCoreMigrate.scala:237–245` */
  private def originalCheck2(outDir: Path, subs: Substitutions): List[(String, Int)] =
    val sources = Files.walk(outDir).iterator().asScala
      .filter(p => p.toString.endsWith(".scala")).toList
      .map(p => p -> Files.readString(p))
    subs.dropTypes.toList.sorted.flatMap { fqn =>
      if Files.exists(outDir.resolve(fqn.replace('.', '/') + ".scala")) then None
      else
        val refs = sources.count((_, src) => src.contains(fqn))
        if refs == 0 then None else Some(fqn -> refs)
    }

  private def tree(files: (String, String)*): Path =
    val dir = Files.createTempDirectory("subcheck")
    files.foreach { (rel, src) =>
      val p = dir.resolve(rel)
      Files.createDirectories(p.getParent)
      Files.writeString(p, src)
    }
    dir

  private def bothAgree(dir: Path, subs: Substitutions)(using munit.Location): Unit =
    assertEquals(
      SubstitutionCheck.emittedDroppedTypes(dir, subs).map(_.fqn).toSet,
      originalCheck1(dir, subs),
      "CHECK 1 diverged from the inline original",
    )
    assertEquals(
      SubstitutionCheck.dangling(dir, subs).map(f => f.fqn -> f.references),
      originalCheck2(dir, subs),
      "CHECK 2 diverged from the inline original",
    )

  test("clean tree — a dropped type with an injected replacement and no dangling reference") {
    val dir = tree(
      "com/x/Dropped.scala" -> "package com.x\nclass Dropped",
      "com/x/User.scala"    -> "package com.x\nclass User { val d = new com.x.Dropped }",
    )
    val subs = Substitutions(dropTypes = Set("com.x.Dropped"))
    bothAgree(dir, subs)
    assertEquals(SubstitutionCheck.emittedDroppedTypes(dir, subs).size, 1) // present = replaced OR leaked
    assertEquals(SubstitutionCheck.dangling(dir, subs), Nil)
  }

  test("CHECK 1 fires: the emitter wrote a file for a dropped type") {
    val dir  = tree("com/x/Dropped.scala" -> "package com.x\nclass Dropped")
    val subs = Substitutions(dropTypes = Set("com.x.Dropped"))
    bothAgree(dir, subs)
    val f = SubstitutionCheck.emittedDroppedTypes(dir, subs)
    assertEquals(f.map(_.fqn), List("com.x.Dropped"))
    assert(clue(f.head.render).contains("§1(a) engine"), "CHECK 1 must classify as an engine fault")
  }

  test("CHECK 2 fires: dropped, unreplaced, still referenced — with the reference COUNT") {
    val dir = tree(
      "com/x/A.scala" -> "package com.x\nclass A { def d: com.x.Dropped = ??? }",
      "com/x/B.scala" -> "package com.x\nclass B { def d: com.x.Dropped = ??? }",
      "com/x/C.scala" -> "package com.x\nclass C",
    )
    val subs = Substitutions(dropTypes = Set("com.x.Dropped"))
    bothAgree(dir, subs)
    assertEquals(SubstitutionCheck.dangling(dir, subs), List(SubstitutionCheck.Finding(SubstitutionCheck.Kind.Dangling, "com.x.Dropped", 2)))
    assert(clue(SubstitutionCheck.dangling(dir, subs).head.render).contains("§1(b)/(c) per-library"))
  }

  test("the SUCCESS case: dropped, unreplaced, and every use rewritten away is NOT a finding") {
    val dir  = tree("com/x/A.scala" -> "package com.x\nclass A")
    val subs = Substitutions(dropTypes = Set("com.x.Dropped"))
    bothAgree(dir, subs)
    assertEquals(SubstitutionCheck.dangling(dir, subs), Nil)
  }

  test("empty manifest is a no-op, and a missing output directory is not an error") {
    val dir = tree("com/x/A.scala" -> "package com.x\nclass A")
    bothAgree(dir, Substitutions.none)
    assertEquals(SubstitutionCheck.dangling(dir.resolve("nope"), Substitutions(dropTypes = Set("com.x.D"))), Nil)
    assertEquals(SubstitutionCheck.scalaSources(dir.resolve("nope")), Nil)
  }

  test("findings are sorted, so a report is stable run to run") {
    val dir = tree(
      "com/x/Z.scala"   -> "package com.x\nclass Z",
      "com/x/Ref.scala" -> "package com.x\nclass Ref { def a: com.x.Bbb = ???; def b: com.x.Aaa = ??? }",
    )
    val subs = Substitutions(dropTypes = Set("com.x.Bbb", "com.x.Aaa"))
    bothAgree(dir, subs)
    assertEquals(SubstitutionCheck.dangling(dir, subs).map(_.fqn), List("com.x.Aaa", "com.x.Bbb"))
  }
