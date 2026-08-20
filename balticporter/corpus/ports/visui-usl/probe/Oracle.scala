/** The PORT half of USL's conformance oracle. Its twin is `OracleJava.java` beside it, and
  * `just usl-oracle-measure` runs both and diffs the transcripts line for line.
  *
  * ==What this is, and what it is NOT==
  * This is **not a ported test** and must never be counted as one (CLAUDE.md §3). USL's own seven
  * `@Test` are ported by `UslTestMigrate` and counted there; this is a measurement harness, in the
  * same category as `scripts/_lib.sh` and jbump's differential probe — hand-written, owned by this
  * port, and the thing that makes the conformance claim REPRODUCIBLE.
  *
  * ==Why it is worth having BESIDE the ported suite==
  * The suite runs six templates written to exercise the language; this runs the **nineteen real
  * skins upstream ships**, including the one the released library is actually built from. That is a
  * different population and a much larger one — `visui-1.4.11.usl` alone is 200 lines of output
  * over a full scene2d widget set — and it is the population where §4.4's silent forms live. A
  * post-increment read as a value (28 sites in these lexer/parser sources), a `break` that ran on,
  * a reference `==` inside an `equals`: none moves a compile-error count, and all of them produce
  * *slightly different JSON*.
  *
  * ==Zero authoring, on BOTH tiers==
  * No expected value is written anywhere in this file, so none can be written down wrong:
  *
  *   - the ABSOLUTE tier is upstream's own checked-in `uiskin.json`, and WHICH fixtures reproduce
  *     it is derived by running the program rather than listed;
  *   - the DIFFERENTIAL tier is the upstream JAVA, run on the same fixtures in the same order, and
  *     the whole assertion is `diff java.txt scala.txt`.
  *
  * ==The one line that names the PORT's surface rather than upstream's==
  * `sge.visui.usl.USL.parse` — the package rename this port declares. Everything else is java's own
  * shape: `parse(File)` keeps java's arity and its `String` result, which is itself a small piece of
  * evidence, since a signature change here would be a surface change and this probe would not
  * compile.
  */
object Oracle:

  def main(args: Array[String]): Unit =
    val stylesDir = new java.io.File(args(0))
    val knownGood = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(args(1))),
                               java.nio.charset.StandardCharsets.UTF_8)

    val fixtures = Option(stylesDir.listFiles((_, n) => n.endsWith(".usl")))
      .getOrElse(Array.empty[java.io.File])
    if fixtures.isEmpty then
      throw new IllegalStateException("no .usl fixtures under " + stylesDir)
    // Sorted, for the reason the java half states: a directory listing's order is the filesystem's.
    val sorted = fixtures.sortBy(_.getPath)

    println("fixtures: " + sorted.length)
    for f <- sorted do
      println("=== " + f.getName + " ===")
      // A THROW is transcript content rather than a harness failure — whether the port throws where
      // java throws is exactly what this gate asks. `Throwable`, deliberately: catching less would
      // turn a divergence into a crash and lose every fixture after it.
      //
      // `getSimpleName`, not `getName`, and its twin says why: this transcript is diffed against one
      // the upstream JAVA produced, whose exceptions are `com.kotcrab.vis.usl.*` while these are
      // `sge.visui.usl.*`. Qualified, every throw would diff — reporting the package rename, which
      // is this port's own declared policy, as a behavioural divergence (CLAUDE.md §4.56).
      try
        val out = sge.visui.usl.USL.parse(f)
        println("known-good: " + (if out == knownGood then "EXACT" else "DIFFERS"))
        println(out)
      catch
        case t: Throwable =>
          println("THREW " + t.getClass.getSimpleName + ": " + String.valueOf(t.getMessage).replace('\n', ' '))
