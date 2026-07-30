package balticporter.corpus

import balticporter.core.*
import balticporter.emit.{ScalaPrinter, SentinelRegistry}
import balticporter.frontend.spoon.SpoonFrontend

import java.nio.file.{Files, Path, StandardCopyOption}
import scala.jdk.CollectionConverters.*

/** M5 bump gate (DESIGN.md §3.9): an upstream pin move must trigger SCOPED
  * regeneration — retranslate exactly the changed units plus the
  * interface-ripple, everything else served from the action cache.
  *
  * The vendored tree is never touched: the "pin move" is simulated on staged
  * copies under out/bump-demo. v2 differs from v1 by (a) a body-only edit in a
  * leaf unit (a comment inside Abs.calculate — must retranslate exactly that
  * unit, no ripple) and (b) a signature addition in Filter.java (new public
  * method — must ripple to every unit whose translation depends on Filter).
  * Exit != 0 on any violated expectation.
  */
object BumpDemo:

  private case class Sweep(
      units: List[String],
      hits: Int,
      translated: List[String],
      ifaceHash: Map[String, String],
      outDigest: Map[String, String],
      commentFailures: Int,
  )

  def main(args: Array[String]): Unit =
    val repoRoot = Path.of(sys.props.getOrElse("balticporter.root", ".")).toAbsolutePath.normalize
    val ssgRoot = repoRoot.resolve("../ssg").normalize
    val upstream = ssgRoot.resolve("original-src/liqp/src/main/java")
    val demoRoot = repoRoot.resolve("out/bump-demo")
    if Files.exists(demoRoot) then deleteTree(demoRoot)

    val v1 = demoRoot.resolve("v1")
    val v2 = demoRoot.resolve("v2")
    copyTree(upstream, v1)
    copyTree(upstream, v2)

    // ---- the simulated pin move -------------------------------------------
    val bodyEditRel = "liqp/filters/Abs.java"
    val sigEditRel = "liqp/filters/Filter.java"
    edit(v2.resolve(bodyEditRel), "        return 0;",
      "        // bump-demo v2: body-only upstream drift\n        return 0;")
    edit(v2.resolve(sigEditRel), "        return params[index];\n    }",
      "        return params[index];\n    }\n\n    /** bump-demo v2: interface addition. */\n    public String bumpDemoProbe() {\n        return \"v2\";\n    }")

    val cache = new ActionCache(demoRoot.resolve(".cache"), enabled = true)
    val cp = LiqpClasspath.resolve(repoRoot)

    println("[bump] sweep 1: cold on v1")
    val cold = sweep(v1, cache, cp)
    println(s"[bump]   units=${cold.units.length} hits=${cold.hits} translated=${cold.translated.length}")

    println("[bump] sweep 2: warm on v1")
    val warm = sweep(v1, cache, cp)
    println(s"[bump]   units=${warm.units.length} hits=${warm.hits} translated=${warm.translated.length}")

    println("[bump] sweep 3: v2 (the pin move)")
    val bump = sweep(v2, cache, cp)
    println(s"[bump]   units=${bump.units.length} hits=${bump.hits} translated=${bump.translated.length}")
    println(s"[bump]   retranslated: ${bump.translated.sorted.take(8).mkString(", ")}${if bump.translated.length > 8 then s" … (${bump.translated.length} total)" else ""}")

    println("[bump] sweep 4: warm on v2 (determinism)")
    val warm2 = sweep(v2, cache, cp)

    // ---- expectations ------------------------------------------------------
    var failures = List.empty[String]
    def expect(cond: Boolean, what: String): Unit = if !cond then failures ::= what

    expect(cold.translated.length == cold.units.length && cold.hits == 0, "cold sweep must translate every unit")
    expect(warm.translated.isEmpty && warm.hits == warm.units.length, s"warm v1 sweep must be all hits (translated=${warm.translated.length})")
    expect(cold.commentFailures == 0 && bump.commentFailures == 0, "comment invariant must hold on both versions")

    expect(bump.ifaceHash(bodyEditRel) == cold.ifaceHash(bodyEditRel),
      "body-only edit must not change the unit's interface hash")
    expect(bump.ifaceHash(sigEditRel) != cold.ifaceHash(sigEditRel),
      "signature addition must change the unit's interface hash")

    // dependents of Filter in v2 = units whose dependency edge set includes it
    val expectedRipple = bump.units.filter(rel => rel != sigEditRel && dependsOn(rel, sigEditRel))
    val expected = (Set(bodyEditRel, sigEditRel) ++ expectedRipple).toList.sorted
    expect(bump.translated.sorted == expected,
      s"scoped regen mismatch:\n  expected (${expected.length}): ${expected.mkString(", ")}\n  actual (${bump.translated.length}): ${bump.translated.sorted.mkString(", ")}")
    expect(expectedRipple.nonEmpty, "the signature edit must ripple to at least one dependent")

    expect(warm2.translated.isEmpty, s"warm v2 sweep must be all hits (translated=${warm2.translated.length})")
    expect(warm2.outDigest == bump.outDigest, "v2 outputs must be byte-stable across sweeps")

    if failures.nonEmpty then
      failures.reverse.foreach(f => System.err.println(s"[bump] FAIL: $f"))
      sys.exit(1)
    println(s"[bump] scoped regen: ${bump.translated.length}/${bump.units.length} units (1 body edit + 1 signature edit + ${expectedRipple.length} ripple)")
    println("[bump] GATE GREEN")

  /** dependency edge query for the assertion — recomputed from the last sweep. */
  private var lastDeps: Map[String, Set[String]] = Map.empty
  private def dependsOn(rel: String, dep: String): Boolean = lastDeps.getOrElse(rel, Set.empty).contains(dep)

  private def sweep(root: Path, cache: ActionCache, cp: List[Path]): Sweep =
    val files = Files.walk(root).iterator().asScala
      .filter(p => p.toString.endsWith(".java") && !p.toString.endsWith("Examples.java"))
      .map(p => root.relativize(p).toString)
      .toList.sorted
    val cfg = FrontendConfig(root, files, cp, resolutionRoots = List(root))
    val prov = Provenance("Liqp", "bump-demo", "MIT", "liqp/src/main/java")
    val parsed = new SpoonFrontend().parseTolerant(cfg)
    val units = parsed.collect { case (_, Right(u)) => u }
    val sentinels = SentinelRegistry.compute(units)
    val ctorReg = new balticporter.emit.CtorRegistry(units)
    val engineFp = EngineFingerprint.value
    val sentinelDigest = Digest.string(sentinels.toList.sorted.mkString(","))
    val ctorDigest = Digest.string(ctorReg.digestInput)
    val fqcnToUnit: Map[String, BUnit] =
      units.flatMap(u => u.types.map(t => (if u.pkg.isEmpty then t.name else s"${u.pkg}.${t.name}") -> u)).toMap
    val ifaceHash: Map[String, String] = units.map(u => u.sourcePath -> InterfaceHash.of(u)).toMap

    var hits = 0
    val translated = List.newBuilder[String]
    val outDigest = Map.newBuilder[String, String]
    var commentFailures = 0
    val depsB = Map.newBuilder[String, Set[String]]
    units.foreach { u =>
      val deps = UnitDeps.of(u, fqcnToUnit.keySet).flatMap(fqcnToUnit.get).map(_.sourcePath).toList.distinct.sorted
      depsB += u.sourcePath -> deps.toSet
      val key = Digest.combined(
        ("src" -> Digest.file(root.resolve(u.sourcePath)))
          :: ("engine" -> engineFp)
          :: ("sentinels" -> sentinelDigest)
          :: ("ctors" -> ctorDigest)
          :: ("prov" -> Digest.string(prov.toString))
          :: deps.map(d => s"dep:$d" -> ifaceHash(d))
      )
      val out = cache.get(key) match
        case Some(cached) => hits += 1; cached
        case None =>
          scala.util.Try(ScalaPrinter.print(u, prov, sentinels, Some(ctorReg))) match
            case scala.util.Success(fresh) =>
              translated += u.sourcePath
              cache.put(key, fresh)
              fresh
            case scala.util.Failure(_) => "" // unsupported units stay out of the metric entirely
      if out.nonEmpty then
        if CommentCheck.check(u, out).nonEmpty then commentFailures += 1
        outDigest += u.sourcePath -> Digest.string(out)
    }
    lastDeps = depsB.result()
    val ok = outDigest.result()
    Sweep(ok.keySet.toList.sorted, hits, translated.result(), ifaceHash, ok, commentFailures)

  private def edit(p: Path, from: String, to: String): Unit =
    val s = Files.readString(p)
    if !s.contains(from) then throw new IllegalStateException(s"edit anchor missing in $p")
    Files.writeString(p, s.replace(from, to))

  private def copyTree(from: Path, to: Path): Unit =
    Files.walk(from).iterator().asScala.foreach { p =>
      val t = to.resolve(from.relativize(p).toString)
      if Files.isDirectory(p) then Files.createDirectories(t)
      else
        Files.createDirectories(t.getParent)
        Files.copy(p, t, StandardCopyOption.REPLACE_EXISTING)
    }

  private def deleteTree(root: Path): Unit =
    Files.walk(root).sorted(java.util.Comparator.reverseOrder()).iterator().asScala.foreach(Files.delete)
