package balticporter.corpus.textra

import balticporter.core.{FrontendConfig, PortManifest, Provenance, RuntimeMode}
import balticporter.corpus.ClasspathCache
import balticporter.corpus.libgdx.LibgdxPolicy
import balticporter.runner.{Determinism, PortRun, SourceSet, VendoredCommit}

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** Migrate **TextraTypist** (`src/main/java`, 92 types — libGDX's rich-text label family: a font
  * engine with markup, effects and scene2d widgets) through the TIR.
  *
  *   corpus/runMain balticporter.corpus.textra.TextraTypistMigrate [--determinism=full]
  *
  * ==A DEPENDENT port, and the first with a THIRD-PARTY compile dependency of its own==
  * `build.gradle` declares exactly two `api` coordinates — `com.badlogicgames.gdx:gdx` and
  * `com.github.tommyettinger:regexodus` — so `gdx/src` is a RESOLUTION root (parsed so every
  * reference resolves, never emitted here) and the policy is [[LibgdxPolicy.core]] EXTENDED rather
  * than restated (CLAUDE.md §1.5). `LibgdxCoreMigrate` emits the base and `just textra-measure`
  * compiles the two together. RegExodus is neither of those things: it is a JAR, and it arrives
  * through the frontend CLASSPATH and through `PortManifest.dependencies` — see
  * [[TextraTypistClasspath]] and the `dependencies` key below.
  *
  * ==Scope: 92 of 95, and the three are `package-info.java`==
  * `src/main/java` holds 95 `.java` files in three packages (`textra`, `textra.effects`,
  * `textra.utils`); three of them are `package-info.java`, javadoc-only placeholders that declare no
  * type and carry no licence header of their own. `just textra-measure` re-derives both numbers on
  * every run rather than trusting this comment.
  *
  * ==NO TEST SOURCE SET, and it is upstream's own zero==
  * Upstream's `src/test/java` is 128 files and declares **zero** `@Test`: `build.gradle` names no
  * JUnit coordinate at all, 113 of the 128 declare `public static void main` and 107 extend
  * `ApplicationAdapter`/`Game`/`InputAdapter`. It is a directory of manual LWJGL3 demos, which is
  * `sge-jbump`'s precedent (a library that ships NO suite, so the lane re-derives that zero) rather
  * than `sge-ai`'s. There is therefore no `TextraTypistTestMigrate` and there is nothing for one to
  * convert. What behavioural evidence this port can have is a DIFFERENTIAL probe against the
  * reference hand port's own hand-WRITTEN MUnit suite, which is a later wave and is scoped in
  * `PROGRESS.md` §10.8.
  */
object TextraTypistMigrate:

  def main(args: Array[String]): Unit =
    val repoRoot = Path.of(sys.props.getOrElse("balticporter.root", ".")).toAbsolutePath.normalize
    val upstream = repoRoot.resolve("../sge/original-src/textratypist").normalize
    val base     = upstream.resolve("src/main/java").normalize
    val gdxSrc   = repoRoot.resolve("../sge/original-src/libgdx/gdx/src").normalize

    val files = Files.walk(base).iterator().asScala
      .filter(p => p.toString.endsWith(".java"))
      .map(p => base.relativize(p).toString)
      .filterNot(f => f.endsWith("package-info.java") || f.endsWith("module-info.java"))
      .toList.sorted

    PortRun(
      label     = "sge-textra",
      portRoot  = repoRoot.resolve("ported/sge-textra"),
      sourceSet = SourceSet.Main,
      frontend  = FrontendConfig(base, files, TextraTypistClasspath.entries(repoRoot),
                                 resolutionRoots = List(gdxSrc)),
      phases    = Nil, // supplied by the manifest — the two sources are mutually exclusive
      manifest  = Some(TextraTypistPolicy.core(repoRoot)),
      provenance = Some(Provenance(
        upstreamName     = "TextraTypist",
        upstreamCommit   = VendoredCommit.of(base),
        originalLicense  = "Apache-2.0",
        sourcePathPrefix = "textratypist/src/main/java",
        sourceRoot       = base.toString,
        // THREE LICENCE REGIMES, and only ONE of them needs a key (CLAUDE.md §4.57).
        //
        //   (a) Apache-2.0, the uniform per-file header every one of the 95 sources carries. The
        //       per-file harvest (§4.58) plus the emitted banner meets that obligation by
        //       construction, which is why no Apache-2.0 port in this corpus has ever needed a
        //       `notices` entry;
        //   (b) the emoji-regex MIT notice, which is INLINE in `EmojiProcessor.java`'s own leading
        //       comment — full MIT text, Copyright Mathias Bynens, self-contained. It is ordinary
        //       file trivia and rides the same harvest as (a). The lane greps the emitted file for
        //       it rather than assuming, because a licence reproduced "by construction" is
        //       reproduced by a mechanism that can regress silently;
        //   (c) the typing-label MIT notice, which is in NO file's comment anywhere. Upstream is a
        //       derivative of Rafa Skoberg's typing-label, whose sources carry no headers at all, so
        //       upstream discharges MIT by shipping the text as a repo-root file and saying so in
        //       its README. That is precisely §4.57's pointer-instead-of-inclusion gap: nothing in
        //       the Java sources structurally identifies which subset the notice covers, so no
        //       harvest can find it and no check could report its absence.
        //
        // (c) is what this key is for, and BOTH files are declared because a derived work of an
        // Apache+MIT original ships both texts. The reference hand port ships NEITHER — grepped: no
        // scala, build or doc file in `../sge/sge-extension/textra` names typing-label, Skoberg or
        // MIT outside `EmojiProcessor.scala`'s inherited (b) — so this is a place the mechanical
        // port is more compliant than the hand one, which is worth stating rather than discovering.
        // A declared file that is not there is fatal, so it cannot rot into a claim.
        notices          = List(upstream.resolve("LICENSE"), upstream.resolve("typing-label.LICENSE")),
      )),
      // NOT `Vendored`: `LibgdxCoreMigrate` already vendors the collection shims into the module
      // this output is compiled beside. Vendoring again would define every support type twice —
      // which the JVM tolerates only while the copies agree, and the Scala.js/Native linkers reject.
      runtimeMode = RuntimeMode.Dependency,
      determinism = Determinism.fromArgs(args.toSeq),
      nextStep    = "just textra-measure",
    ).execute()

/** The ONE third-party jar this port's frontend needs, resolved once and cached.
  *
  * `com.github.tommyettinger:regexodus` is a pure-java regex engine upstream uses instead of
  * `java.util.regex` because the latter does not exist on GWT. Six of its classes are imported
  * across the sources (`Category`, `Compatibility`, `Matcher`, `Pattern`, `REFlags`, `Replacer`),
  * and unlike libGDX it is not a source tree this repository has — so it arrives as a CLASSPATH
  * entry, which is what [[ClasspathCache]] is for and where the reason a stale cache is refetched
  * rather than reused is written down: an import the frontend cannot resolve does not fail, it
  * resolves WRONGLY.
  *
  * The version is `gradle.properties`' own `regexodusVersion`, read off upstream's build rather than
  * guessed — see the `Justfile`'s `ashley_deps` for what guessing a version cost once.
  */
object TextraTypistClasspath:

  val Coordinates: List[String] = List("com.github.tommyettinger:regexodus:0.1.21")

  def cache(repoRoot: Path): Path = repoRoot.resolve("out/textra-classpath.txt")

  def entries(repoRoot: Path): List[Path] =
    ClasspathCache.entries(cache(repoRoot), "textratypist", Coordinates)

/** TextraTypist's per-library policy — a DEPENDENT of libGDX core's, and deliberately almost empty.
  *
  * The base's `dropTypes`, `dropMethods`, `packageRenames` and every signature-affecting phase are
  * INHERITED, not restated: they are facts about the surface these 92 files compile against, and a
  * dependent that re-declared them would be free to drift (CLAUDE.md §1.5). That is also where the
  * collections and mutable-parameter phases come from — a libGDX dependent does not start them, it
  * receives the base's ONE instance, and adding a second would be a `SurfaceDivergence` for a
  * composition nobody designed.
  *
  * `inject` is deliberately NOT inherited (see [[balticporter.core.PortManifest]]): a drop is an
  * observation about the shared API and binds every module that sees the type, but exactly one
  * module ships each replacement file. libGDX core ships the replacements for the types IT dropped.
  *
  * This wave adds a namespace claim, a rename, one build coordinate and the base-surface residue
  * check, and NOTHING else — no drop, no substitution, no redirect. A wall measured under invented
  * policy says nothing about the engine, so every entry after these arrives with the number that
  * justified it.
  */
object TextraTypistPolicy:

  def core(repoRoot: Path): PortManifest =
    LibgdxPolicy.core(repoRoot).extendedBy(PortManifest(
      name    = "sge-textra",
      governs = Set("com.github.tommyettinger.textra"),
      // ONE PAIR, UNIFORM, AND THE REFERENCE PORT'S ONE DEVIATION IS DELIBERATELY NOT REPRODUCED.
      //
      // The destination is the reference module `../sge/build.sbt`'s `sge-extension/textra`, which
      // declares `package sge.textra` with `effects` and `utils` beside it — so one prefix pair
      // moves the whole library and longest-prefix-wins does the rest, exactly as
      // `com.badlogic.gdx -> sge` does for the base (INHERITED, not restated).
      //
      // The hand port hoists ONE type out of its package: upstream's `.textra.utils.LzmaUtils` is
      // emitted there as top-level `sge.textra.LzmaUtils`, and its own migration note says why —
      // *"SGE core does not port the compression sub-package"*, so it inlined a self-contained LZMA
      // codec rather than delegate to `com.badlogic.gdx.utils.compression.Lzma`. Reproducing that
      // here would be one `typeRenames` entry and it is NOT written, because both halves of the
      // hand port's reason were MEASURED and neither holds for a mechanical port (§3.5 — quote the
      // reference port for the SHAPE it emitted, and get a number before that shape becomes a
      // manifest entry):
      //
      //   * the base DOES port the sub-package. `port-report/LibgdxCoreMigrate/baseline/port-map.tsv`
      //     carries 392 rows under `com.badlogic.gdx.utils.compression` — `Lzma` itself, `CRC`,
      //     `ICodeProgress`, the `lz` and `lzma` trees — every one `Renamed` to `sge.utils.compression.*`.
      //     So the inherited rename already resolves upstream's `Lzma` import, the delegation
      //     translates mechanically, and the fact that motivated the hoist is a fact about the HAND
      //     port's base and not about this one;
      //   * nothing outside the destination package consumes the hoisted spelling. Grepped
      //     `../sge`: `LzmaUtils` is named in three files (`Font.scala`, `BitmapFontSupport.scala`
      //     and the module's own red suite) and all three declare `package sge.textra` themselves,
      //     so the reference module has no import of it to break — and this port emits fully
      //     qualified names with no imports at all (§6), so the placement moves no call site either
      //     way.
      //
      // §2.1 is what the hoist would have been claimed under and it does not reach here: it names
      // the port DIRECTORY for the destination module and says outright that `packageRenames` is a
      // different axis. A deviation with no measurement behind it is exactly what §3.5 prices at
      // 27 -> 47 errors, so the mechanical answer stands and this comment is the record of the two
      // numbers that chose it. If a consumer is ever found that needs the hand spelling, it is one
      // `typeRenames` entry away and it arrives with that consumer named.
      packageRenames = Map("com.github.tommyettinger.textra" -> "sge.textra"),
      // THE ARTIFACT THIS MODULE'S BUILD ADDS — the OTHER half of the classpath fact above, and not
      // the same statement twice. `TextraTypistClasspath` is what the FRONTEND parses against; this
      // is what the port DECLARES it depends on, which `SbtGen` writes into `libraryDependencies`
      // and which `dependency-coverage` reads to decide whether a requirement is answered. Not
      // inherited (§1.5's right-hand column, `inject`'s line): exactly one module's build names each
      // coordinate, and the base's own entry is `scala-java-locales` for a family this port does not
      // use.
      //
      // `cross = Java` because it is a plain java artifact — `%`, not `%%`.
      //
      // ==WHAT THIS PORT IS NOT SAYING, and it is a residue rather than a claim==
      // RegExodus is published for the JVM ONLY: `com.github.tommyettinger:regexodus:0.1.21`
      // resolves from Maven Central and `regexodus_sjs1_3` / `regexodus_native0.5_3` do not exist at
      // that version (both probed). This module inherits the all-platform default (§1.5: sge and ssg
      // target every platform wherever possible), so the port now DECLARES a JVM-only artifact while
      // claiming three platforms — and nothing in the engine reports that, because `ArtifactDep`
      // carries a `CrossKind` and no per-platform availability at all. It is the same shape
      // `ssg-md-ext` records for `org.nibor.autolink`, and it is library code the port genuinely
      // calls rather than a marker it could stop emitting. Narrowing `targets` is deliberately NOT
      // the answer — it is a whole-PORT key, and setting it to `[jvm]` would empty the rule list for
      // every one of this port's declarations at once, which is §1(b)'s "fifteen baselines improving
      // to zero" at this port's scale. `PROGRESS.md` §10.8 holds it as a residue.
      //
      // Note the irony worth stating once: upstream depends on RegExodus PRECISELY because
      // `java.util.regex` is missing on a non-JVM backend (GWT). The dependency that buys upstream
      // its portability is the one that costs this port its own.
      dependencies = List(
        balticporter.catalog.ArtifactDep("com.github.tommyettinger", "regexodus", "0.1.21",
                                         balticporter.catalog.CrossKind.Java)),
      surface = List(
        // LAST, deliberately, for the reason `AshleyPolicy` and `GdxAiPolicy` state: this reads what
        // the BASE actually emitted and reports a reference the base does not ship, so it must run
        // after any seam that re-points such a reference, or it reports the very sites the next
        // phase repairs. A residue check, exactly like `PortabilityCheck`. This wave has no such
        // seam, which is the point — the rows it files ARE the wave's finding.
        balticporter.transform.PortMapTransform.forBases("sge"),
      ),
    ))
