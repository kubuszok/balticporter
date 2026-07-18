package balticporter.corpus

import balticporter.core.*
import balticporter.emit.{ScalaPrinter, SentinelRegistry}
import balticporter.frontend.spoon.SpoonFrontend
import balticporter.sbtgen.SbtGen

import java.nio.file.{Files, Path, StandardCopyOption}
import scala.jdk.CollectionConverters.*

/** M2 whole-corpus assembly: translate every done-status Liqp file, apply the
  * whole-file overrides, emit an sbt 2.0 project, and run `sbt compile` on it —
  * the real link-time gate (scalac over the whole module).
  *
  * Overrides in corpus-tests/overrides mirror the source tree; each must carry a
  * HANDWRITTEN OVERRIDE header (checked here — no silent hand-edits).
  */
object LiqpProject:

  def main(args: Array[String]): Unit =
    val repoRoot = Path.of(sys.props.getOrElse("balticporter.root", ".")).toAbsolutePath.normalize
    val ssgRoot = repoRoot.resolve("../ssg").normalize
    val sourceRoot = ssgRoot.resolve("original-src/liqp/src/main/java")
    val projRoot = repoRoot.resolve("out/liqp-project")
    val overridesRoot = repoRoot.resolve("corpus-tests/overrides")

    // done + skipped: ssg skipped some packages for cross-platform substitutions
    // (filters/date, spi, where, antlr helpers), but the JVM whole-corpus compile
    // needs the complete upstream surface — the engine translates them all.
    // Examples.java is a demo main, not library surface.
    val rows = Files.readAllLines(ssgRoot.resolve(".rescale/data/migration.tsv")).asScala.toList
      .filterNot(_.startsWith("#"))
      .map(_.split('\t').toList)
      .collect {
        case "liqp" :: sourcePath :: _ :: status :: _ if status == "done" || status == "skipped" => sourcePath
      }
    val files = rows.map(_.stripPrefix("src/main/java/")).filterNot(_ == "liqp/Examples.java").sorted

    val overridden = files.filter { rel =>
      Files.exists(overridesRoot.resolve(rel.stripSuffix(".java") + ".scala"))
    }.toSet
    println(s"[proj] ${files.length} files, ${overridden.size} overridden (${overridden.mkString(", ")})")

    val cfg = FrontendConfig(sourceRoot, files.filterNot(overridden), LiqpClasspath.resolve(repoRoot), List(sourceRoot))
    val prov = Provenance("Liqp", LiqpClasspath.upstreamCommit(repoRoot), "MIT", "liqp/src/main/java")
    val frontend = new SpoonFrontend
    val units = frontend.parse(cfg)
    val sentinels = SentinelRegistry.compute(units)
    val ctorReg = Some(new balticporter.emit.CtorRegistry(units))

    val srcDir = projRoot.resolve("src/main/scala")
    if Files.exists(srcDir) then
      Files.walk(srcDir).iterator().asScala.toList.reverse.foreach(Files.delete)

    // ---- persistent action cache (PLAN §12): key = source digest + dep interface
    // hashes + sentinel set + engine fingerprint; -Dbalticporter.cache=off disables
    val cacheEnabled =
      sys.props.getOrElse("balticporter.cache", "on") != "off" && !args.contains("--no-cache")
    val cache = new ActionCache(repoRoot.resolve("out/.bpcache"), cacheEnabled)
    val engineFp = EngineFingerprint.value
    val sentinelDigest = Digest.string(sentinels.toList.sorted.mkString(","))
    val ctorDigest = Digest.string(ctorReg.map(_.digestInput).getOrElse(""))
    println(s"[proj] key components: engine=${engineFp.take(12)} sentinels=${sentinelDigest.take(12)} ctors=${ctorDigest.take(12)}")
    val fqcnToUnit: Map[String, BUnit] =
      units.flatMap(u => u.types.map(t => (if u.pkg.isEmpty then t.name else s"${u.pkg}.${t.name}") -> u)).toMap
    val ifaceHash: Map[String, String] = units.map(u => u.sourcePath -> InterfaceHash.of(u)).toMap
    var cacheHits = 0
    var translatedCount = 0

    var commentFailures = 0
    units.foreach { u =>
      val deps = UnitDeps
        .of(u, fqcnToUnit.keySet)
        .flatMap(fqcnToUnit.get)
        .map(_.sourcePath)
        .toList
        .distinct
        .sorted
      val key = Digest.combined(
        ("src" -> Digest.file(sourceRoot.resolve(u.sourcePath)))
          :: ("engine" -> engineFp)
          :: ("sentinels" -> sentinelDigest)
          :: ("ctors" -> ctorDigest)
          :: ("prov" -> Digest.string(prov.toString))
          :: deps.map(d => s"dep:$d" -> ifaceHash(d))
      )
      val out = cache.get(key) match
        case Some(cached) =>
          cacheHits += 1
          cached
        case None =>
          translatedCount += 1
          val fresh = ScalaPrinter.print(u, prov, sentinels, ctorReg)
          cache.put(key, fresh)
          fresh
      val lost = CommentCheck.check(u, out)
      if lost.nonEmpty then
        commentFailures += 1
        System.err.println(s"[proj] comment loss: ${u.sourcePath}: ${lost.head.comment.take(100)}")
      val target = srcDir.resolve(u.sourcePath.stripSuffix(".java") + ".scala")
      Files.createDirectories(target.getParent)
      Files.writeString(target, out)
    }
    overridden.toList.sorted.foreach { rel =>
      val src = overridesRoot.resolve(rel.stripSuffix(".java") + ".scala")
      val header = Files.readString(src)
      require(header.contains("HANDWRITTEN OVERRIDE"), s"override without header: $src")
      val target = srcDir.resolve(rel.stripSuffix(".java") + ".scala")
      Files.createDirectories(target.getParent)
      Files.copy(src, target, StandardCopyOption.REPLACE_EXISTING)
    }
    println(s"[proj] cache: $cacheHits hits, $translatedCount translated (enabled=$cacheEnabled)")

    // platform lint: the substitution-disposition worklist for a JS/Native port
    val lintHits = balticporter.verify.PlatformLint.scan(units, fqcnToUnit.keySet)
    val lintReport = lintHits
      .groupBy(_.category)
      .view
      .mapValues(hs => (hs.map(_.unit).distinct.length, hs.length))
      .toList
      .sortBy(-_._2._1)
    Files.writeString(
      repoRoot.resolve("out/liqp-platform-lint.tsv"),
      lintHits.map(h => s"${h.unit}\t${h.category}\t${h.detail}").sorted.distinct.mkString("", "\n", "\n"),
    )
    println("[proj] platform lint (units affected / refs) — JS/Native substitution worklist:")
    lintReport.foreach { case (cat, (us, refs)) => println(f"[proj]   $us%3d units $refs%4d refs  $cat") }
    if commentFailures > 0 then
      System.err.println(s"[proj] $commentFailures comment failures"); sys.exit(1)

    // regenerated ANTLR parser classes ride along as an unmanaged jar
    val libDir = projRoot.resolve("lib")
    Files.createDirectories(libDir)
    val parserJar = libDir.resolve("liquid-parser-gen.jar")
    if !Files.exists(parserJar) then
      LiqpClasspath.resolve(repoRoot) // ensures out/antlr-gen/classes exists
      val pb = new ProcessBuilder(
        "jar", "cf", parserJar.toString, "-C", repoRoot.resolve("out/antlr-gen/classes").toString, ".",
      ).redirectErrorStream(true)
      val proc = pb.start()
      val outTxt = new String(proc.getInputStream.readAllBytes())
      if proc.waitFor() != 0 then throw new RuntimeException(s"jar failed:\n$outTxt")

    SbtGen.emit(
      projRoot,
      SbtGen.ProjectSpec(
        moduleName = "liqp-port",
        organization = "com.kubuszok.balticporter.out",
        scalaVersion = "3.8.4",
        sbtVersion = "2.0.3",
        deps = List(
          SbtGen.Dep("org.antlr", "antlr4-runtime", "4.13.0"),
          SbtGen.Dep("com.fasterxml.jackson.core", "jackson-databind", "2.13.4.2"),
          SbtGen.Dep("com.fasterxml.jackson.datatype", "jackson-datatype-jsr310", "2.13.2"),
          SbtGen.Dep("ua.co.k", "strftime4j", "1.0.6"),
        ),
        testDeps = List(
          SbtGen.Dep("junit", "junit", "4.13.2"),
          SbtGen.Dep("org.hamcrest", "hamcrest-all", "1.3"),
          SbtGen.Dep("com.github.sbt", "junit-interface", "0.13.3"),
        ),
        engineFingerprint = EngineInfo.fingerprint,
      ),
    )
    println(s"[proj] emitted ${units.length + overridden.size} sources + build to $projRoot")

    // ---- tests: translate the upstream JUnit4 suite onto JUnit4 itself (JVM gate;
    // munit/cross-platform is a later phase) -----------------------------------
    val testRoot = ssgRoot.resolve("original-src/liqp/src/test/java")
    val testFiles = Files
      .walk(testRoot)
      .iterator()
      .asScala
      .filter(p => p.toString.endsWith(".java"))
      .map(p => testRoot.relativize(p).toString)
      .toList
      .sorted
    val testCp = LiqpClasspath.resolve(repoRoot) ++ LiqpClasspath.junitClasspath(repoRoot)
    val testCfg = FrontendConfig(testRoot, testFiles, testCp, List(sourceRoot, testRoot))
    val testResults = frontend.parseTolerant(testCfg)
    val testDir = projRoot.resolve("src/test/scala")
    if Files.exists(testDir) then
      Files.walk(testDir).iterator().asScala.toList.reverse.foreach(Files.delete)
    var okTests = 0
    val testFailures = List.newBuilder[(String, String)]
    testResults.foreach {
      case (rel, Right(u)) =>
        scala.util.Try(ScalaPrinter.print(u, prov, sentinels, ctorReg)) match
          case scala.util.Success(out) =>
            okTests += 1
            val target = testDir.resolve(u.sourcePath.stripSuffix(".java") + ".scala")
            Files.createDirectories(target.getParent)
            Files.writeString(target, out)
          case scala.util.Failure(e) => testFailures += rel -> String.valueOf(e.getMessage).take(120)
      case (rel, Left(e)) => testFailures += rel -> String.valueOf(e.getMessage).take(120)
    }
    // upstream test fixtures (template files the tests read relative to the project root)
    List(
      "src/main/resources", // ServiceLoader registrations (META-INF/services) — TypesSupport SPI
      "src/test/jekyll", "src/test/resources", "_includes", "alternative_includes", "snippets",
    ).foreach { rel =>
      val src = ssgRoot.resolve(s"original-src/liqp/$rel")
      if Files.isDirectory(src) then
        val dst = projRoot.resolve(rel)
        Files.walk(src).iterator().asScala.foreach { p =>
          val t = dst.resolve(src.relativize(p).toString)
          if Files.isDirectory(p) then Files.createDirectories(t)
          else
            Files.createDirectories(t.getParent)
            Files.copy(p, t, StandardCopyOption.REPLACE_EXISTING)
        }
        println(s"[proj] fixtures copied: $rel")
    }

    println(s"[proj] tests: $okTests/${testFiles.length} translated")
    val fails = testFailures.result()
    if fails.nonEmpty then
      println("[proj] test translation failures:")
      fails.groupBy(_._2).view.mapValues(_.length).toList.sortBy(-_._2).take(10).foreach { (r, n) =>
        println(f"[proj]   $n%3d  $r")
      }

    println("[proj] running sbt compile (this is the whole-module scalac gate)...")
    val pb = new ProcessBuilder("sbt", "-batch", "compile").directory(projRoot.toFile).redirectErrorStream(true)
    val proc = pb.start()
    val outTxt = new String(proc.getInputStream.readAllBytes())
    val code = proc.waitFor()
    Files.writeString(repoRoot.resolve("out/liqp-project-compile.log"), outTxt)
    val errors = outTxt.linesIterator.filter(_.contains("[error]")).toList
    println(s"[proj] sbt exit=$code, error lines=${errors.length} (log: out/liqp-project-compile.log)")
    if code == 0 then println("[proj] WHOLE-CORPUS COMPILE GREEN")
    else
      // histogram of error messages — the next worklist
      val kinds = errors
        .map(_.replaceAll(".*\\[error\\]\\s*", "").take(60))
        .filterNot(l => l.startsWith("--") || l.matches("^\\d+ \\|.*") || l.trim.startsWith("^") || l.trim.startsWith("|"))
        .groupBy(identity).view.mapValues(_.length).toList.sortBy(-_._2)
      kinds.take(12).foreach((k, n) => println(f"[proj]  $n%4d  $k"))
      sys.exit(1)
