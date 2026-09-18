package balticporter.runner

import java.net.URLClassLoader
import java.nio.file.{ Files, Path }

class BundledTreeSpec extends munit.FunSuite:

  private def classpath(files: Map[String, String]): (Path, ClassLoader) =
    val root = Files.createTempDirectory("bundled-tree-cp")
    files.foreach { (rel, text) =>
      val p = root.resolve(rel)
      Files.createDirectories(p.getParent)
      Files.writeString(p, text)
    }
    (root, new URLClassLoader(Array(root.toUri.toURL), null))

  test("the indexed tree is written under the destination, nested paths included") {
    val (_, loader) = classpath(
      Map(
        "bundle/INDEX" -> "a/One.scala\nb/c/Two.scala\n",
        "bundle/a/One.scala" -> "object One",
        "bundle/b/c/Two.scala" -> "object Two"
      )
    )
    val dest = Files.createTempDirectory("bundled-tree-dest")
    val root = BundledTree.extract(loader, "bundle", dest)
    assertEquals(Files.readString(root.resolve("a/One.scala")), "object One")
    assertEquals(Files.readString(root.resolve("b/c/Two.scala")), "object Two")
  }

  test("a file the index no longer lists is removed, and an unchanged one is not rewritten") {
    val (_, loader) = classpath(Map("bundle/INDEX" -> "Keep.scala\n", "bundle/Keep.scala" -> "object Keep"))
    val dest        = Files.createTempDirectory("bundled-tree-dest")
    Files.writeString(dest.resolve("Stale.scala"), "object Stale")
    BundledTree.extract(loader, "bundle", dest)
    val stamp = Files.getLastModifiedTime(dest.resolve("Keep.scala"))
    assert(!Files.exists(dest.resolve("Stale.scala")))
    BundledTree.extract(loader, "bundle", dest)
    assertEquals(Files.getLastModifiedTime(dest.resolve("Keep.scala")), stamp)
  }

  test("a listed resource that is missing is fatal, and so is a missing index") {
    val (_, loader) = classpath(Map("bundle/INDEX" -> "Gone.scala\n"))
    val dest        = Files.createTempDirectory("bundled-tree-dest")
    val missing     = intercept[IllegalStateException](BundledTree.extract(loader, "bundle", dest))
    assert(missing.getMessage.contains("Gone.scala"), missing.getMessage)
    val noIndex = intercept[IllegalStateException](BundledTree.extract(loader, "absent", dest))
    assert(noIndex.getMessage.contains("absent/INDEX"), noIndex.getMessage)
  }

  test("an index entry may not leave the destination") {
    val (_, loader) = classpath(Map("bundle/INDEX" -> "../Escape.scala\n", "Escape.scala" -> "object Escape"))
    val dest        = Files.createTempDirectory("bundled-tree-dest")
    intercept[IllegalArgumentException](BundledTree.extract(loader, "bundle", dest))
  }
