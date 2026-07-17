package balticporter.runner

import balticporter.core.*
import balticporter.emit.ScalaPrinter

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** M0 pipeline: translate a fixed file list, enforce the comment invariant,
  * verify determinism by double-translation, write the output tree, and gate
  * the result through scalac (via scala-cli). PLAN.md §13 M0.
  */
object M0Pipeline:

  final case class Translated(targetPath: String, content: String)

  def translateOnce(plan: UnitPlan, frontend: Frontend): List[Translated] =
    val units = frontend.parse(FrontendConfig(plan.sourceRoot, plan.files, plan.classpath))
    units.map { u =>
      val out = ScalaPrinter.print(u, plan.provenance)
      val missing = CommentCheck.check(u, out)
      if missing.nonEmpty then
        throw new RuntimeException(
          s"comment-preservation violation in ${u.sourcePath}:\n" +
            missing.map(m => s"  lost: ${m.comment.take(120)}").mkString("\n")
        )
      Translated(u.sourcePath.stripSuffix(".java") + ".scala", out)
    }

  /** Translate twice from scratch; byte-compare. Returns the verified result. */
  def translateDeterministic(plan: UnitPlan, mkFrontend: () => Frontend): List[Translated] =
    val a = translateOnce(plan, mkFrontend())
    val b = translateOnce(plan, mkFrontend())
    val diffs = a.zip(b).collect { case (x, y) if x.content != y.content => x.targetPath }
    if diffs.nonEmpty then
      throw new RuntimeException(s"determinism violation: differing output for ${diffs.mkString(", ")}")
    a

  def writeTree(outDir: Path, files: List[Translated]): Unit =
    if Files.exists(outDir) then
      Files.walk(outDir).iterator().asScala.toList.reverse.foreach(Files.delete)
    files.sortBy(_.targetPath).foreach { t =>
      val p = outDir.resolve(t.targetPath)
      Files.createDirectories(p.getParent)
      Files.writeString(p, t.content)
    }

  /** Compile the generated tree (+ handwritten shim dirs) with scalac via scala-cli. */
  def compileGate(scalaVersion: String, dirs: List[Path]): Either[String, Unit] =
    val cmd = List("scala-cli", "compile", "--scala", scalaVersion, "--server=false") ++ dirs.map(_.toString)
    val pb = new ProcessBuilder(cmd*).redirectErrorStream(true)
    val proc = pb.start()
    val out = new String(proc.getInputStream.readAllBytes())
    val code = proc.waitFor()
    if code == 0 then Right(()) else Left(s"scala-cli exited $code:\n$out")
