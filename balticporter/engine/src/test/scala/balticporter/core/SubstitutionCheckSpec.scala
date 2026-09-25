package balticporter.core

import java.nio.file.{ Files, Path }
import scala.jdk.CollectionConverters.*

/** [[SubstitutionCheck]] is a LIFT, not a redesign — CHECK 1 and CHECK 2 were inline in `LibgdxCoreMigrate` and had to keep behaving exactly as they did.
  */
class SubstitutionCheckSpec extends munit.FunSuite:

  // ---- the originals, copied verbatim from LibgdxCoreMigrate before the lift ----

  /** `LibgdxCoreMigrate.scala:203` */
  private def originalCheck1(outDir: Path, subs: Substitutions): Set[String] =
    subs.dropTypes.filter(fqn => Files.exists(outDir.resolve(fqn.replace('.', '/') + ".scala")))

  /** `LibgdxCoreMigrate.scala:237–245` */
  private def originalCheck2(outDir: Path, subs: Substitutions): List[(String, Int)] =
    val sources = Files.walk(outDir).iterator().asScala.filter(p => p.toString.endsWith(".scala")).toList.map(p => p -> Files.readString(p))
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
      "CHECK 1 diverged from the inline original"
    )
    assertEquals(
      SubstitutionCheck.dangling(dir, subs).map(f => f.fqn -> f.references),
      originalCheck2(dir, subs),
      "CHECK 2 diverged from the inline original"
    )

  test("clean tree — a dropped type with an injected replacement and no dangling reference") {
    val dir = tree(
      "com/x/Dropped.scala" -> "package com.x\nclass Dropped",
      "com/x/User.scala" -> "package com.x\nclass User { val d = new com.x.Dropped }"
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
    assert(clue(f.head.render).contains("engine bug"), "CHECK 1 must classify as an engine fault")
  }

  test("CHECK 2 fires: dropped, unreplaced, still referenced — with the reference COUNT") {
    val dir = tree(
      "com/x/A.scala" -> "package com.x\nclass A { def d: com.x.Dropped = ??? }",
      "com/x/B.scala" -> "package com.x\nclass B { def d: com.x.Dropped = ??? }",
      "com/x/C.scala" -> "package com.x\nclass C"
    )
    val subs = Substitutions(dropTypes = Set("com.x.Dropped"))
    bothAgree(dir, subs)
    assertEquals(
      SubstitutionCheck.dangling(dir, subs),
      List(SubstitutionCheck.Finding(SubstitutionCheck.Kind.Dangling, "com.x.Dropped", 2))
    )
    assert(clue(SubstitutionCheck.dangling(dir, subs).head.render).contains("port policy or library-specific rule"))
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
      "com/x/Z.scala" -> "package com.x\nclass Z",
      "com/x/Ref.scala" -> "package com.x\nclass Ref { def a: com.x.Bbb = ???; def b: com.x.Aaa = ??? }"
    )
    val subs = Substitutions(dropTypes = Set("com.x.Bbb", "com.x.Aaa"))
    bothAgree(dir, subs)
    assertEquals(SubstitutionCheck.dangling(dir, subs).map(_.fqn), List("com.x.Aaa", "com.x.Bbb"))
  }

  test("a replacement the consumer PROVIDES counts as present; a type provided nowhere still dangles") {
    val dir  = tree("com/x/Ref.scala" -> "package com.x\nclass Ref { def a: com.x.Aaa = ???; def b: com.x.Bbb = ??? }")
    val subs = Substitutions(dropTypes = Set("com.x.Aaa", "com.x.Bbb"))
    assertEquals(SubstitutionCheck.dangling(dir, subs, Set("com.x.Aaa")).map(_.fqn), List("com.x.Bbb"))
    // nothing provided is exactly the check without the parameter
    assertEquals(SubstitutionCheck.dangling(dir, subs, _ => false), SubstitutionCheck.dangling(dir, subs))
  }

  // ---- code versus comments: only code can dangle ----

  private val javadocOnly =
    """package com.x
      |/** Parses the input.
      |  * @throws com.x.FooException in case the input is malformed
      |  */
      |class Parser { def parse(s: String): Int = throw new com.y.PortError(s) }
      |""".stripMargin

  test("a mention only in an upstream comment is not fatal, and is one doc-mention row per file") {
    val dir  = tree("com/x/Parser.scala" -> javadocOnly, "com/x/Other.scala" -> "package com.x\n// see com.x.FooException\nclass Other")
    val subs = Substitutions(dropTypes = Set("com.x.FooException"))
    val s    = SubstitutionCheck.scan(dir, subs)
    assertEquals(s.dangling, Nil)
    assertEquals(
      s.docMentions.map(f => (f.kind, f.fqn, f.file, f.line)).sortBy(_._3),
      List(
        (SubstitutionCheck.Kind.DocMention, "com.x.FooException", "com/x/Other.scala", 2),
        (SubstitutionCheck.Kind.DocMention, "com.x.FooException", "com/x/Parser.scala", 3)
      )
    )
    assert(s.docMentions.forall(!_.fatal))
    assert(clue(s.docMentions.head.render).contains("an upstream comment names com.x.FooException"))
  }

  test("a code reference by the upstream name is fatal, and a comment in the same file adds no doc-mention row") {
    val dir  = tree("com/x/Parser.scala" -> (javadocOnly + "class Q { def e: com.x.FooException = ??? }\n"))
    val subs = Substitutions(dropTypes = Set("com.x.FooException"))
    val s    = SubstitutionCheck.scan(dir, subs)
    assertEquals(s.dangling, List(SubstitutionCheck.Finding(SubstitutionCheck.Kind.Dangling, "com.x.FooException", 1)))
    assert(s.dangling.forall(_.fatal))
    assertEquals(s.docMentions, Nil)
  }

  test("a code reference by the RENAMED name is fatal; the renamed name in a comment is a doc mention") {
    val dir = tree(
      "org/port/A.scala" -> "package org.port\nclass A { def e: org.port.FooException = ??? }",
      "org/port/B.scala" -> "package org.port\n/** throws org.port.FooException */\nclass B"
    )
    val subs    = Substitutions(dropTypes = Set("com.x.FooException"))
    val renamed = (fqn: String) => fqn.replace("com.x.", "org.port.")
    val s       = SubstitutionCheck.scan(dir, subs, emittedName = renamed)
    assertEquals(s.dangling.map(f => f.fqn -> f.references), List("com.x.FooException" -> 1))
    assertEquals(s.docMentions.map(_.file), List("org/port/B.scala"))
    // without the emitted name the code reference is invisible, which is the miss this closes
    assertEquals(SubstitutionCheck.dangling(dir, subs), Nil)
  }

  test("a mention inside a string literal is neither a code reference nor a doc mention") {
    val src =
      "package com.x\nclass A {\n  val m = \"com.x.FooException\"\n  val c = '\"'\n  val n = s\"was com.x.FooException $m\"\n  val t = \"\"\"com.x.FooException\"\"\"\n}\n"
    val dir = tree("com/x/A.scala" -> src)
    val s   = SubstitutionCheck.scan(dir, Substitutions(dropTypes = Set("com.x.FooException")))
    assertEquals(s, SubstitutionCheck.Scan(Nil, Nil))
  }

  test("a match is bounded by identifiers: a longer name is not a reference, a nested type is") {
    val dir = tree(
      "com/x/A.scala" -> "package com.x\nclass A { def a: com.x.FooExceptionHandler = ??? }",
      "com/x/B.scala" -> "package com.x\nclass B { def b: com.x.FooException.Kind = ??? }"
    )
    val s = SubstitutionCheck.scan(dir, Substitutions(dropTypes = Set("com.x.FooException")))
    assertEquals(s.dangling.map(_.references), List(1))
  }

  test("porter notes naming a dropped type are neither code nor an upstream comment") {
    val dir = tree(
      "com/x/A.scala" -> "package com.x\n/* porter: dropped-type reason=configured phase=substitutions key=com.x.FooException */\nclass A"
    )
    assertEquals(SubstitutionCheck.scan(dir, Substitutions(dropTypes = Set("com.x.FooException"))), SubstitutionCheck.Scan(Nil, Nil))
  }
