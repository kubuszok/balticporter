// The PORT half of `just md-conformance --with-port` — the same census over the EMITTED Scala.
//
// It is the deliberate mirror of `MdConformanceControl.java`'s `dump` mode, line for line, over the
// renamed namespace (`com.vladsch.flexmark` -> `ssg.md`), and it writes the SAME three artifacts into
// a different directory so that `MdConformanceControl classify` can put the two renderings of one
// example side by side. Nothing here re-implements a comparison: the classification is one
// codepath, in the control driver, reading two dumps.
//
// WHY A SECOND FILE AND NOT A SHARED ONE. The two drivers name two different sets of types — that is
// the whole point of the port — and there is no language in which one file names both. What they
// DO share is the split, and the split is `SpecReader.EXAMPLE_BREAK`, which is the spec format's own
// constant and is read here from the PORT's own emitted copy of it rather than from a literal: if a
// rename or a retyping ever moved it, this driver must fail, not silently align on a string this
// repository wrote.
//
// THE PORT'S `testSpecExample` IS NOT WHAT RUNS HERE, for the control's reason: it is one
// `assertEquals` over the whole dump. This drives `create` / `readExamples` / `getFullSpec` /
// `getExpectedFullSpec` — the four calls that assertion is made of — and splits.

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}

object MdConformancePort:
  private val Keys = List("spec.txt", "spec.0.27.txt", "spec.0.28.txt", "spec.0.29.txt")

  private def testCase(key: String): ssg.md.test.util.FullSpecTestCase = key match
    case "spec.txt"      => new ssg.md.core.test.util.renderer.FullOrigSpecCoreTest()
    case "spec.0.27.txt" => new ssg.md.core.test.util.renderer.FullOrigSpec027CoreTest()
    case "spec.0.28.txt" => new ssg.md.core.test.util.renderer.FullOrigSpec028CoreTest()
    case "spec.0.29.txt" => new ssg.md.core.test.util.renderer.FullOrigSpec029CoreTest()
    case other           => throw new IllegalArgumentException(s"no suite for $other")

  // The `RESOURCE_LOCATION` FIELD and not `getSpecResourceLocation()` — see the control driver's
  // header. For the three live suites the two are the same value; for 0.29 the method answers
  // `ResourceLocation.NULL` on both sides, which is upstream's decision and not the port's.
  private def location(key: String): ssg.md.test.util.spec.ResourceLocation = key match
    case "spec.txt"      => ssg.md.core.test.util.renderer.FullOrigSpecCoreTest.RESOURCE_LOCATION
    case "spec.0.27.txt" => ssg.md.core.test.util.renderer.FullOrigSpec027CoreTest.RESOURCE_LOCATION
    case "spec.0.28.txt" => ssg.md.core.test.util.renderer.FullOrigSpec028CoreTest.RESOURCE_LOCATION
    case "spec.0.29.txt" => ssg.md.core.test.util.renderer.FullOrigSpec029CoreTest.RESOURCE_LOCATION
    case other           => throw new IllegalArgumentException(s"no location for $other")

  private def live(key: String): Boolean = key != "spec.0.29.txt"

  private def split(text: String): Array[String] =
    text.split(java.util.regex.Pattern.quote(ssg.md.test.util.spec.SpecReader.EXAMPLE_BREAK), -1)

  private def write(p: Path, s: String): Unit =
    Files.write(p, s.getBytes(StandardCharsets.UTF_8)): Unit

  def main(args: Array[String]): Unit =
    if args.length != 1 then
      System.err.println("usage: MdConformancePort <outDir>")
      System.exit(2)

    val outDir = Paths.get(args(0))
    Files.createDirectories(outDir)

    println("spec resource     examples  passing  failing")
    var liveExamples, livePassing, liveFailing = 0

    for key <- Keys do
      val tc = testCase(key)
      val reader = tc.create(location(key))
      reader.readExamples()

      val actual = reader.getFullSpec()
      val expected = reader.getExpectedFullSpec()
      val examples = reader.getExamples()

      val a = split(actual)
      val e = split(expected)
      if a.length != e.length then
        throw new IllegalStateException(
          s"$key: the two dumps split into ${a.length} and ${e.length} chunks — they are not the same document")
      if a.length % 2 != 1 then
        throw new IllegalStateException(
          s"$key: ${a.length} chunks — an example is delimited by a PAIR of breaks, so the count is odd")
      val count = (a.length - 1) / 2
      if count != examples.size then
        throw new IllegalStateException(
          s"$key: split found $count example blocks and the reader read ${examples.size}")
      for i <- 0 until a.length by 2 do
        if a(i) != e(i) then
          throw new IllegalStateException(
            s"$key: prose chunk $i differs between the rendered dump and the spec — the two are misaligned")

      val status = new StringBuilder("# idx\tsection\texample\tverdict\n")
      var passing = 0
      for i <- 0 until count do
        val ok = a(2 * i + 1) == e(2 * i + 1)
        if ok then passing += 1
        val ex = examples(i)
        status.append(i + 1).append('\t')
          .append(if ex.getSection() == null then "" else ex.getSection()).append('\t')
          .append(ex.getExampleNumber()).append('\t')
          .append(if ok then "PASS" else "FAIL").append('\n')

      write(outDir.resolve(key + ".actual"), actual)
      write(outDir.resolve(key + ".expected"), expected)
      write(outDir.resolve(key + ".status.tsv"), status.toString)

      println(f"$key%-16s  $count%8d  $passing%7d  ${count - passing}%7d${if live(key) then "" else "   (NOT RUN by the java suite)"}")

      if live(key) then
        liveExamples += count; livePassing += passing; liveFailing += count - passing

    println(f"${"THE THREE LIVE"}%-16s  $liveExamples%8d  $livePassing%7d  $liveFailing%7d")
    write(outDir.resolve("live-totals.tsv"),
      s"examples\tpassing\tfailing\n$liveExamples\t$livePassing\t$liveFailing\n")
