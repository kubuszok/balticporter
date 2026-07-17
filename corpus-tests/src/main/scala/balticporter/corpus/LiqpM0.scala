package balticporter.corpus

import balticporter.core.*
import balticporter.frontend.spoon.SpoonFrontend
import balticporter.runner.M0Pipeline

import java.nio.file.{Files, Path}

/** M0 gate: 20 hand-picked Liqp files → compiling Scala 3, comments preserved,
  * byte-identical across runs. See GOAL.md for the set's rationale.
  *
  * Out-of-set externals (liqp.LValue, liqp.TemplateContext) follow the Shim
  * disposition (PLAN.md §6): handwritten minimal implementations under
  * corpus-tests/shims, compiled together with the generated tree.
  */
object LiqpM0:

  val files: List[String] = List(
    "liqp/exceptions/ExceededMaxIterationsException.java",
    "liqp/exceptions/IncompatibleTypeComparisonException.java",
    "liqp/exceptions/VariableNotExistException.java",
    "liqp/PlainBigDecimal.java",
    "liqp/filters/Filter.java",
    "liqp/filters/Abs.java",
    "liqp/filters/Append.java",
    "liqp/filters/Ceil.java",
    "liqp/filters/Downcase.java",
    "liqp/filters/Lstrip.java",
    "liqp/filters/Minus.java",
    "liqp/filters/Plus.java",
    "liqp/filters/Prepend.java",
    "liqp/filters/Remove.java",
    "liqp/filters/Remove_First.java",
    "liqp/filters/Replace.java",
    "liqp/filters/Replace_First.java",
    "liqp/filters/Rstrip.java",
    "liqp/filters/Strip.java",
    "liqp/filters/Upcase.java",
  )

  def main(args: Array[String]): Unit =
    val repoRoot = Path.of(sys.props.getOrElse("balticporter.root", ".")).toAbsolutePath.normalize
    val sourceRoot = repoRoot.resolve("../ssg/original-src/liqp/src/main/java").normalize
    val outDir = repoRoot.resolve("out/liqp-m0/src")
    val shims = repoRoot.resolve("corpus-tests/shims")

    val plan = UnitPlan(
      sourceRoot = sourceRoot,
      files = files,
      classpath = LiqpClasspath.resolve(repoRoot),
      outDir = outDir,
      provenance = Provenance(
        upstreamName = "Liqp",
        upstreamCommit = LiqpClasspath.upstreamCommit(repoRoot),
        originalLicense = "MIT",
        sourcePathPrefix = "liqp/src/main/java",
      ),
    )

    println(s"[m0] translating ${plan.files.length} files from $sourceRoot")
    val translated = M0Pipeline.translateDeterministic(plan, () => new SpoonFrontend)
    println("[m0] determinism: OK (double translation byte-identical)")
    println("[m0] comments: OK (preservation invariant held for all units)")

    M0Pipeline.writeTree(outDir, translated)
    println(s"[m0] wrote ${translated.length} files to $outDir")

    M0Pipeline.compileGate("3.8.4", List(outDir, shims)) match
      case Right(()) =>
        println("[m0] compile gate: OK")
        println("[m0] GATE GREEN")
      case Left(err) =>
        System.err.println(err)
        System.err.println("[m0] GATE RED")
        sys.exit(1)

object LiqpClasspath:
  /** Dependency classpath for resolution: the published liqp jar + transitives
    * (LValue/TemplateContext resolve as Spoon shadow classes from the jar).
    */
  def resolve(repoRoot: Path): List[Path] =
    val cache = repoRoot.resolve("out/liqp-classpath.txt")
    val text =
      if Files.exists(cache) then Files.readString(cache).trim
      else
        // 0.9.2.3 is the closest published release to the vendored 0.9.2 commit; the jar is
        // only used for shadow-class resolution of out-of-set types (LValue, TemplateContext).
        val pb = new ProcessBuilder("cs", "fetch", "--classpath", "nl.big-o:liqp:0.9.2.3").redirectErrorStream(true)
        val proc = pb.start()
        val out = new String(proc.getInputStream.readAllBytes()).trim
        if proc.waitFor() != 0 then throw new RuntimeException(s"coursier fetch failed:\n$out")
        val cp = out.linesIterator.toList.last
        Files.createDirectories(cache.getParent)
        Files.writeString(cache, cp)
        cp
    text.split(java.io.File.pathSeparatorChar).toList.map(Path.of(_))

  /** Pin of the vendored upstream: the submodule HEAD recorded by ../ssg. */
  def upstreamCommit(repoRoot: Path): String =
    val pb = new ProcessBuilder("git", "-C", repoRoot.resolve("../ssg/original-src/liqp").toString, "rev-parse", "HEAD")
      .redirectErrorStream(true)
    val proc = pb.start()
    val out = new String(proc.getInputStream.readAllBytes()).trim
    if proc.waitFor() != 0 then "unknown" else out
