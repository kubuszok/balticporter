package balticporter.frontend.ts

import java.nio.file.{ Files, Path }

/** The share of translated bodies per registered library over the checked-in fixtures, read back from `bodies.tsv` (left under `target/non-java-bodies/<library>`). The numbers are pinned in both
  * directions: a rise is accepted here, never assumed.
  */
class NonJavaBodiesMeasuredSpec extends munit.FunSuite:

  override val munitTimeout = scala.concurrent.duration.Duration(10, "min")

  private val workingDir = Path.of(sys.props.getOrElse("user.dir", ".")).toAbsolutePath

  private val resources: Option[Path] =
    Iterator
      .iterate(Option(workingDir))(_.flatMap(p => Option(p.getParent)))
      .takeWhile(_.isDefined)
      .flatten
      .flatMap(p => List(p.resolve("src/test/resources"), p.resolve("balticporter/frontend-ts/src/test/resources")))
      .find(p => Files.isDirectory(p.resolve("rast")) && Files.isDirectory(p.resolve("reference")))

  /** `translated`/`offered` cover the members the skeleton reader offers in files whose module was built — what the emitters' own summaries count; `rows` and `byWhy` cover the whole table. */
  final private case class Counts(translated: Int, offered: Int, rows: Int, byWhy: Map[String, Int])

  private def measure(library: String, keepReferenceOnRefusal: Boolean): Counts =
    val root      = resources.get
    val reference = root.resolve("reference").resolve(library)
    val rast      = root.resolve("rast").resolve(library)
    val report    = workingDir.resolve(s"target/non-java-bodies/$library${if keepReferenceOnRefusal then "" else "-holes-emitted"}")
    NonJavaBodies.forLibrary(library, reference, rast) match
      case refused: NonJavaBodies.Refused => fail(refused.message)
      case built:   NonJavaBodies.Built   =>
        val run     = built.copy(policy = built.policy.copy(keepReferenceOnRefusal = keepReferenceOnRefusal)).derive(report.resolve("out"), report)
        val fed     = built.files.collect { case (file, bodies) if bodies.refusal.isEmpty => file }.toSet
        val table   = Files.readString(report.resolve(BodiesReport.FileName)).linesIterator.drop(1).map(_.split("\t", -1).toList).toList
        val inFed   = table.filter(row => fed(row(0)) && row(2) != "-")
        val summary = run.summary
        assertEquals(table.count(_(3) == "translated"), summary.translated, "the summary and the table disagree")
        assertEquals(summary.translated + summary.byWhy.map(_._2).sum, table.size)
        println(s"$library keepReferenceOnRefusal=$keepReferenceOnRefusal: ${summary.line}")
        Counts(inFed.count(_(3) == "translated"), inFed.size, table.size, summary.byWhy.toMap)

  // What each emitter's own in-memory summaries counted before this table existed, a body with a hole in it counting as translated. katex's own count was 210 of 288: two module rows name one
  // reference file with a single member, counted once per row there and once here; and two members past the last translated occurrence of their name were handed the FIRST occurrence's body.
  private val emittersOwnCounts = List("katex" -> (208, 287), "terser" -> (142, 451), "dart-sass" -> (620, 985), "mermaid" -> (12, 15))

  // (translated, rows, translator-refusal, unclassified) under each library's registered policy, which keeps the reference wherever the translator left a hole.
  private val registeredPolicy = List("katex" -> (154, 398, 61, 8), "terser" -> (136, 1002, 6, 16), "dart-sass" -> (382, 1416, 255, 333), "mermaid" -> (0, 29, 12, 14))

  // A consumer passes its source ROOT, package directories and all; the module table's paths must still be found under it.
  for (library, module, packageDir) <- List(
      ("katex", "ssg-katex", "ssg/katex/"),
      ("terser", "ssg-js", "ssg/js/"),
      ("dart-sass", "ssg-sass", "ssg/sass/"),
      ("mermaid", "ssg-mermaid", "ssg/mermaid/diagrams/")
    )
  do
    test(s"$library: the consumer's reference root is read with its package directories"):
      assume(resources.isDefined, "the fixture directories were not found from the working directory")
      val reference = ConsumerCheckout.referenceScala(module)
      assume(reference.isDefined, s"no ssg checkout with a $module reference was found")
      NonJavaBodies.forLibrary(library, reference.get._1, resources.get.resolve("rast").resolve(library)) match
        case refused: NonJavaBodies.Refused => fail(refused.message)
        case built:   NonJavaBodies.Built   =>
          assert(built.files.nonEmpty, "no reference file was matched to a module")
          assert(built.files.keys.forall(_.startsWith(packageDir)), built.files.keys.toList.sorted.take(5).mkString(", "))

  for (library, expected) <- emittersOwnCounts do
    test(s"$library: the table agrees with the emitter's own counts when a body with a hole still counts"):
      assume(resources.isDefined, "the fixture directories were not found from the working directory")
      val counts = measure(library, keepReferenceOnRefusal = false)
      assertEquals((counts.translated, counts.offered), expected)

  for (library, expected) <- registeredPolicy do
    test(s"$library: the registered policy's table, and every body it turns away for a hole was counted as translated before"):
      assume(resources.isDefined, "the fixture directories were not found from the working directory")
      val kept    = measure(library, keepReferenceOnRefusal = true)
      val emitted = measure(library, keepReferenceOnRefusal = false)
      assertEquals(
        (kept.translated, kept.rows, kept.byWhy.getOrElse("translator-refusal", 0), kept.byWhy.getOrElse("unclassified", 0)),
        expected
      )
      assertEquals(kept.rows, emitted.rows)
      assertEquals(
        emitted.translated - kept.translated,
        kept.byWhy.getOrElse("translator-refusal", 0) - emitted.byWhy.getOrElse("translator-refusal", 0)
      )
