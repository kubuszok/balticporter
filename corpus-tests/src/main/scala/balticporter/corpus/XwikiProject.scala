package balticporter.corpus

import balticporter.core.*
import balticporter.emit.{ScalaPrinter, SentinelRegistry}
import balticporter.frontend.spoon.SpoonFrontend
import balticporter.sbtgen.SbtGen

import java.nio.file.{Files, Path, StandardCopyOption}
import scala.jdk.CollectionConverters.*

/** M6 cold-port assembly: the flexmark-ext-xwiki-macros dependency closure
  * (ssg never ported it) flattened into a single sbt 2.0 module and driven
  * through the whole-module scalac gate. Constructor-level overrides
  * (XwikiOverrides) handle the irreducible ctors; everything else is engine-
  * translated. This is the real M6 gate — a library with no hand-port corpus.
  */
object XwikiProject:

  def main(args: Array[String]): Unit =
    val repoRoot = Path.of(sys.props.getOrElse("balticporter.root", ".")).toAbsolutePath.normalize
    val ssgRoot = repoRoot.resolve("../ssg").normalize
    val fmRoot = ssgRoot.resolve("original-src/flexmark-java")
    val projRoot = repoRoot.resolve("out/xwiki-project")

    val moduleRoots = XwikiSurvey.closureModules
      .map(m => fmRoot.resolve(m).resolve("src/main/java"))
      .filter(Files.isDirectory(_))
    val files = moduleRoots.flatMap { root =>
      Files.walk(root).iterator().asScala
        .filter(p => p.toString.endsWith(".java") && !p.toString.endsWith("package-info.java"))
        .map(p => fmRoot.relativize(p).toString)
    }.sorted
    println(s"[xwiki] ${files.length} closure files across ${moduleRoots.length} modules")

    val annotationsJar =
      val pb = new ProcessBuilder("cs", "fetch", "--classpath", "org.jetbrains:annotations:24.1.0")
        .redirectErrorStream(true)
      val proc = pb.start()
      val out = new String(proc.getInputStream.readAllBytes()).trim
      if proc.waitFor() != 0 then throw new RuntimeException(s"coursier fetch failed:\n$out")
      out.linesIterator.toList.last.split(java.io.File.pathSeparatorChar).toList.map(Path.of(_))
    val cp = annotationsJar ++ LiqpClasspath.junitClasspath(repoRoot)

    val cfg = FrontendConfig(fmRoot, files, cp, resolutionRoots = moduleRoots)
    val prov = Provenance("flexmark-java", "cold-port", "BSD-2-Clause", "flexmark-java")
    val frontend = new SpoonFrontend
    val units = frontend.parse(cfg)
    val sentinels = SentinelRegistry.compute(units)
    val ctorReg = Some(new balticporter.emit.CtorRegistry(units))

    // ---- cache (same key discipline as LiqpProject) ----
    val cacheEnabled = sys.props.getOrElse("balticporter.cache", "on") != "off" && !args.contains("--no-cache")
    val cache = new ActionCache(repoRoot.resolve("out/.bpcache-xwiki"), cacheEnabled)
    val engineFp = EngineFingerprint.value
    val sentinelDigest = Digest.string(sentinels.toList.sorted.mkString(","))
    val ctorDigest = Digest.string(ctorReg.map(_.digestInput).getOrElse(""))
    val ovrDigest = Digest.string(XwikiOverrides.map.toList.sortBy(_._1).map((k, v) => s"$k${v.headerSuffix}${v.body.mkString}").mkString)
    val fqcnToUnit: Map[String, BUnit] =
      units.flatMap(u => u.types.map(t => (if u.pkg.isEmpty then t.name else s"${u.pkg}.${t.name}") -> u)).toMap
    val ifaceHash: Map[String, String] = units.map(u => u.sourcePath -> InterfaceHash.of(u)).toMap

    val srcDir = projRoot.resolve("src/main/scala")
    if Files.exists(srcDir) then Files.walk(srcDir).iterator().asScala.toList.reverse.foreach(Files.delete)

    // PLAN §7 whole-file overrides: hand-ported Scala for the irreducible
    // F-bounded / same-erasure-varargs families the engine can't mechanize
    // (keyed by the flattened package path; each must carry the header). The unit
    // is still parsed for cross-closure resolution — only its OUTPUT is replaced.
    val overridesRoot = repoRoot.resolve("corpus-tests/xwiki-overrides")
    var cacheHits = 0
    var translated = 0
    var overridden = 0
    var commentFailures = 0
    units.foreach { u =>
      // flatten: package path from the Scala package, not the Maven module dir
      val pkgPath = u.pkg.replace('.', '/')
      val fileName = u.sourcePath.substring(u.sourcePath.lastIndexOf('/') + 1).stripSuffix(".java") + ".scala"
      val target = srcDir.resolve(pkgPath).resolve(fileName)
      Files.createDirectories(target.getParent)

      val ovrFile = overridesRoot.resolve(pkgPath).resolve(fileName)
      if Files.exists(ovrFile) then
        val content = Files.readString(ovrFile)
        require(content.contains("HANDWRITTEN OVERRIDE"), s"override without header: $ovrFile")
        overridden += 1
        Files.writeString(target, content)
      else
        val deps = UnitDeps.of(u, fqcnToUnit.keySet).flatMap(fqcnToUnit.get).map(_.sourcePath).toList.distinct.sorted
        val key = Digest.combined(
          ("src" -> Digest.file(fmRoot.resolve(u.sourcePath)))
            :: ("engine" -> engineFp) :: ("sentinels" -> sentinelDigest) :: ("ctors" -> ctorDigest)
            :: ("ovr" -> ovrDigest) :: ("prov" -> Digest.string(prov.toString))
            :: deps.map(d => s"dep:$d" -> ifaceHash(d))
        )
        val out = cache.get(key) match
          case Some(c) => cacheHits += 1; c
          case None =>
            translated += 1
            val fresh = ScalaPrinter.print(u, prov, sentinels, ctorReg, XwikiOverrides.map)
            cache.put(key, fresh)
            fresh
        if CommentCheck.check(u, out).nonEmpty then commentFailures += 1
        Files.writeString(target, out)
    }
    println(s"[xwiki] cache: $cacheHits hits, $translated translated, $overridden whole-file overrides; comment failures=$commentFailures")
    if commentFailures > 0 then System.err.println(s"[xwiki] $commentFailures comment failures");

    SbtGen.emit(
      projRoot,
      SbtGen.ProjectSpec(
        moduleName = "xwiki-macros-port",
        organization = "com.kubuszok.balticporter.out",
        scalaVersion = "3.8.4",
        sbtVersion = "2.0.3",
        // junit is a MAIN dep: flexmark-test-util is a main-scope spec-test framework
        deps = List(
          SbtGen.Dep("org.jetbrains", "annotations", "24.1.0"),
          SbtGen.Dep("junit", "junit", "4.13.2"),
        ),
        testDeps = List(SbtGen.Dep("com.github.sbt", "junit-interface", "0.13.3")),
        engineFingerprint = EngineInfo.fingerprint,
      ),
    )
    println(s"[xwiki] emitted ${units.length} sources + build to $projRoot")

    println("[xwiki] running sbt compile (whole-module scalac gate)...")
    val pb = new ProcessBuilder("sbt", "-batch", "compile").directory(projRoot.toFile).redirectErrorStream(true)
    val proc = pb.start()
    val outTxt = new String(proc.getInputStream.readAllBytes())
    val code = proc.waitFor()
    Files.writeString(repoRoot.resolve("out/xwiki-project-compile.log"), outTxt)
    val errors = outTxt.linesIterator.filter(_.contains("[error]")).toList
    println(s"[xwiki] sbt exit=$code, error lines=${errors.length} (log: out/xwiki-project-compile.log)")
    if code == 0 then println("[xwiki] WHOLE-MODULE COMPILE GREEN")
    else
      val kinds = errors
        .map(_.replaceAll(".*\\[error\\]\\s*", "").take(70))
        .filterNot(l => l.startsWith("--") || l.matches("^\\d+ \\|.*") || l.trim.startsWith("^") || l.trim.startsWith("|"))
        .groupBy(identity).view.mapValues(_.length).toList.sortBy(-_._2)
      println("[xwiki] top compile-error kinds:")
      kinds.take(15).foreach((k, n) => println(f"[xwiki]  $n%4d  $k"))
      sys.exit(1)
