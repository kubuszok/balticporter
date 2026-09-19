package balticporter.corpus

import java.nio.file.{ Files, Path }
import scala.jdk.CollectionConverters.*

/** The configurations a consumer runs out of the published jar name every path under a declared root, so the consumer can say where its checkout, its scratch directory and its output are. A path
  * written relative to the file would point into the directory the jar was unpacked to.
  */
class RelocatableConfsSpec extends munit.FunSuite:

  private val portsDir: Path =
    Iterator
      .iterate(Path.of("").toAbsolutePath)(_.getParent)
      .takeWhile(_ != null)
      .map(_.resolve("balticporter/corpus/ports"))
      .find(Files.isDirectory(_))
      .getOrElse(fail("balticporter/corpus/ports not found above the working directory"))

  /** run by a consumer's build: the liquid and markdown ports. */
  private val relocatable = List("liqp", "ssg-md")

  private def confs: List[Path] =
    relocatable.flatMap { dir =>
      val s = Files.list(portsDir.resolve(dir))
      try s.iterator.asScala.filter(_.toString.endsWith(".conf")).toList.sortBy(_.toString)
      finally s.close()
    }

  /** the file's settings without `#` comments and without its `roots { … }` block — the one place a relative default belongs. */
  private def settings(conf: Path): List[(Int, String)] =
    val lines   = Files.readAllLines(conf).asScala.toList.zipWithIndex.map((l, i) => (i + 1, l))
    val inRoots = lines.dropWhile((_, l) => !l.trim.startsWith("roots {")).takeWhile((_, l) => l.trim != "}").map(_._1).toSet
    lines.filterNot((n, _) => inRoots(n)).map((n, l) => (n, l.takeWhile(_ != '#'))).filter((_, l) => l.trim.nonEmpty)

  test("there are configurations to check") {
    assert(clue(confs.size) >= 4)
  }

  test("every relocatable configuration declares its roots") {
    confs.foreach(c => assert(Files.readString(c).contains("roots {"), s"$c declares no roots"))
  }

  test("no path outside the roots block climbs out of the configuration's directory") {
    val offenders = for
      c <- confs
      (n, l) <- settings(c)
      if l.contains("\"../")
    yield s"${portsDir.relativize(c)}:$n: ${l.trim}"
    assertEquals(offenders, Nil)
  }
