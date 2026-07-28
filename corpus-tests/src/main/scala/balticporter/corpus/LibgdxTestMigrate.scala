package balticporter.corpus

import balticporter.core.FrontendConfig
import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.{OmissionCheck, Pipeline, PortabilityCheck, RewriteTrace}
import balticporter.transform.{CollectionsTransform, MutableParamsTransform, PanamaFfiTransform,
  TestFrameworkTransform}

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** Port libGDX's own JUnit suite (`gdx/test`) through the same pipeline as `gdx/src`.
  *
  * This is the port's only BEHAVIOURAL gate. Everything `LibgdxCoreMigrate` measures is *compiles*,
  * and four silent correctness defects have already been found in this corpus that compiled green —
  * dropped `static { }` blocks, dropped `super(args)`, dropped anonymous-class bodies, and the
  * typer-only blind spot itself. `gdx/test` carries 221 `@Test` methods making ~900 assertions;
  * those assertions are the only evidence of behaviour this project can have.
  *
  * The test sources are CONVERTED; `gdx/src` is a RESOLUTION root only — it is ported separately by
  * [[LibgdxCoreMigrate]], and re-emitting it here would fork the output. That split is what
  * `FrontendConfig.resolutionRoots` is for.
  *
  * Same transform pipeline as the main port, minus the two per-library policy phases: test code
  * touches neither the reflection wrapper nor the class table, and giving them a second, differently
  * configured instantiation is exactly the drift `CLAUDE.md` §1 warns about.
  */
object LibgdxTestMigrate:

  def main(args: Array[String]): Unit =
    val repoRoot = Path.of(sys.props.getOrElse("balticporter.root", ".")).toAbsolutePath.normalize
    val srcRoot  = repoRoot.resolve("../sge/original-src/libgdx/gdx/src").normalize
    val testRoot = repoRoot.resolve("../sge/original-src/libgdx/gdx/test").normalize

    val files = Files.walk(testRoot).iterator().asScala
      .filter(p => p.toString.endsWith(".java"))
      .map(p => testRoot.relativize(p).toString)
      .filterNot(f => f.endsWith("package-info.java") || f.endsWith("module-info.java"))
      .toList.sorted

    println(s"[libgdx-test] building model over ${files.size} test files (resolving against gdx/src)…")
    val types = SpoonTir.buildModel(
      FrontendConfig(testRoot, files, Nil, resolutionRoots = List(srcRoot)), lenient = true)
    val raw0 = SpoonTir.fromTypes(types)
    val program = Pipeline.run(raw0,
      List(new CollectionsTransform, new MutableParamsTransform, new PanamaFfiTransform(),
           new TestFrameworkTransform()))
    println(s"[libgdx-test] TIR: ${program.units.size} units, ${program.symbols.all.size} symbols")

    // The same anti-omission checks the main port runs. A test that silently loses a statement is
    // worse than one that fails to compile: it PASSES, and reports that the code under test works.
    RewriteTrace.check(program) match
      case Nil  => println("[libgdx-test] signature check: all call sites agree with their declarations")
      case bad  => println(s"[libgdx-test] SIGNATURE MISMATCHES: ${bad.size}\n${bad.take(20).map("  " + _).mkString("\n")}")
    val omissions = OmissionCheck.check(program)
    println(s"[libgdx-test] OMISSIONS (emitted code silently loses these): ${omissions.size}")
    println(OmissionCheck.summary(omissions))

    // The test port was NEVER portability-checked — this call did not exist, and `org.junit` was
    // not a rule, so the suite emitted as JUnit-in-Scala looked clean twice over. A ported suite is
    // this project's only behavioural gate, and a JVM-only one cannot run on the targets the port
    // exists for; that has to be a NUMBER on every run rather than an assumption.
    val unportable = PortabilityCheck.check(program)
    println(s"[libgdx-test] PORTABILITY (cannot run on Scala.js / Native): ${unportable.size}")
    println(PortabilityCheck.summary(unportable))

    val outDir = balticporter.sbtgen.SbtGen.managedTest(repoRoot.resolve("libgdx-core"))
    if Files.exists(outDir) then Files.walk(outDir).iterator().asScala.toList.reverse.foreach(Files.delete)
    Files.createDirectories(outDir)
    // Spoon's model spans the resolution root as well, so the TIR carries every `gdx/src` unit too.
    // Emit only the TEST units — `gdx/src` is ported by `LibgdxCoreMigrate`, and writing it again
    // from here would fork the output into two copies that drift.
    val testFqns = files.map(f => f.stripSuffix(".java").replace('/', '.').replace('\\', '.')).toSet
    val emitter  = new TirEmitter(program)
    var written  = 0
    program.units.foreach { u =>
      val full = program.symbolOf(u.symbol).map(_.fullName).getOrElse("Unit")
      if testFqns(full) then
        val p = outDir.resolve(full.replace('.', '/') + ".scala")
        Files.createDirectories(p.getParent)
        Files.writeString(p, emitter.emitUnit(u))
        written += 1
    }
    // the suite base class the transform retyped onto — the output is compiled standalone.
    TestFrameworkTransform.runtimeSources.foreach { (fqn, src) =>
      val p = outDir.resolve(fqn.replace('.', '/') + ".scala")
      Files.createDirectories(p.getParent)
      Files.writeString(p, src)
      written += 1
    }
    val missing = testFqns -- program.units.flatMap(u => program.symbolOf(u.symbol).map(_.fullName))
    if missing.nonEmpty then
      println(s"[libgdx-test] WARNING: ${missing.size} test file(s) produced no unit: ${missing.toList.sorted.mkString(", ")}")
    println(s"[libgdx-test] wrote $written Scala test files -> $outDir")
    println("[libgdx-test] now: bash scripts/gdx_test_measure.sh")
