package balticporter.corpus.demo

import balticporter.core.*
import balticporter.emit.ScalaPrinter
import balticporter.frontend.spoon.SpoonFrontend
import balticporter.runner.M0Pipeline
import balticporter.vocab.{PackageRenamePass, Vocabulary, VocabPass}

import java.nio.file.{Files, Path}

/** Tier-2/Tier-3 gate: translate an engine-owned demo unit through the pass
  * pipeline (vocabulary table + package rename), assert the rewrites landed,
  * compile the result with scalac, and check double-translation determinism.
  * Exit != 0 on any failure.
  */
object VocabDemo:

  def main(args: Array[String]): Unit =
    val repoRoot = Path.of(sys.props.getOrElse("balticporter.root", ".")).toAbsolutePath.normalize
    val demoRoot = repoRoot.resolve("corpus/vocab-demo")
    val sourceRoot = demoRoot.resolve("src")

    val vocabulary = Vocabulary.loadFile(demoRoot.resolve("demo.vocab"))
    val passes: List[BirPass] = List(
      new VocabPass(vocabulary),
      new PackageRenamePass("demo", "vocabdemo"),
    )
    println(s"[vocab] passes: ${PassPipeline.fingerprint(passes)}")
    println(s"[vocab] table: ${vocabulary.typeMap.size} types, ${vocabulary.methodMap.size} methods")

    def translate(): String =
      val cfg = FrontendConfig(sourceRoot, List("demo/Registry.java"), Nil, Nil)
      val unit = new SpoonFrontend().parse(cfg).head
      val prov = Provenance("balticporter-demo", "n/a", "Apache-2.0", "corpus/vocab-demo/src")
      ScalaPrinter.print(PassPipeline.run(passes, unit), prov)

    val out = translate()

    var failures = List.empty[String]
    def check(cond: Boolean, what: String): Unit =
      if !cond then failures ::= what

    check(out.contains("package vocabdemo"), s"package rename missing:\n$out")
    check(out.contains("scala.collection.mutable.ArrayBuffer"), "type map missing (ArrayBuffer)")
    check(!out.contains("ArrayList"), "original type leaked (ArrayList)")
    check(out.contains(".append("), "method map missing (add -> append)")
    check(out.contains(".appendAll("), "method map missing (addAll -> appendAll)")
    check(out.contains("keep insertion order"), "comment lost through the pass pipeline")

    check(translate() == out, "double translation not byte-identical")

    val outDir = repoRoot.resolve("out/vocab-demo/vocabdemo")
    Files.createDirectories(outDir)
    Files.writeString(outDir.resolve("Registry.scala"), out)
    M0Pipeline.compileGate("3.8.4", List(outDir.getParent)) match
      case Right(()) => println("[vocab] scalac gate: OK")
      case Left(err) => failures ::= s"scalac gate failed: ${err.take(600)}"

    if failures.nonEmpty then
      failures.reverse.foreach(f => System.err.println(s"[vocab] FAIL: $f"))
      sys.exit(1)
    println("[vocab] GATE GREEN")
