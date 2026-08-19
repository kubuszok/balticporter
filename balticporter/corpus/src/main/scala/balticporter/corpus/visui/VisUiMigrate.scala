package balticporter.corpus.visui

import balticporter.core.{FrontendConfig, PortManifest, Provenance, RuntimeMode}
import balticporter.corpus.libgdx.LibgdxPolicy
import balticporter.runner.{Determinism, PortRun, SourceSet, VendoredCommit}

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** Migrate **VisUI**'s `ui/` module (162 files — a scene2d widget toolkit: dialogs, a file chooser,
  * a colour picker, a tabbed pane, spinners, validators and a form/layout framework) through the
  * TIR.
  *
  *   corpus/runMain balticporter.corpus.visui.VisUiMigrate [--determinism=full]
  *
  * ==A DEPENDENT port, and the most scene2d-dominated one in the corpus==
  * `ui/build.gradle` declares exactly ONE compile coordinate — `com.badlogicgames.gdx:gdx` — so
  * `gdx/src` is a RESOLUTION root (parsed so every reference resolves, never emitted here) and the
  * policy is [[LibgdxPolicy.core]] EXTENDED rather than restated (CLAUDE.md §1.5). There is no
  * third-party jar of any kind, which makes this the OPPOSITE half of `sge-textra`'s lesson: the
  * whole external surface is the base's, and 326 of the ~610 upstream import occurrences are
  * `scenes.scene2d.*`.
  *
  * ==Scope: `ui/` ONLY, and `usl/` is a NAMED follow-up rather than a silent drop==
  * The upstream checkout has two gradle modules. `ui/` is 164 `.java` files (162 in scope — two are
  * `package-info.java`, javadoc-only placeholders that declare no type); `usl/` is a further 18
  * files / 1,604 LOC implementing a skin-DSL compiler. `usl/` is genuinely self-contained in ONE
  * direction — it imports nothing from libGDX and `ui/` references none of its types — and the
  * coupling is BUILD-TIME only: the root `build.gradle` runs an already-published USL over
  * the `usl/styles` fixtures and checks the compiled `uiskin.json` into `ui/src/main/resources`.
  * (The doc text deliberately does not spell that glob: scala block comments NEST, §4.58, and the
  * slash-star inside it opens one.) So `ui/`
  * ships USL's OUTPUT and never calls its CODE, and porting `ui/` first costs the follow-up
  * nothing. `PROGRESS.md` §10.9 holds that follow-up with the zero-authoring oracle it already
  * has; this milestone does not touch it and does not pretend it is not there.
  *
  * ==NO TEST SOURCE SET IN THIS WAVE, and upstream's own number is small rather than zero==
  * Upstream's `ui/src/test` declares **2** real `@Test` (`GreaterThanValidatorTest`,
  * `LesserThanValidatorTest`); the other 28 files there are `extends VisWindow` demos, 27 of them
  * under the `test.manual` package `ui/build.gradle` excludes by name and one OUTSIDE it that the
  * build's own include glob still matches, and 7 of the checkout's 9 real `@Test` are in `usl/`. That
  * is `sge-gltf`'s shape rather than `sge-jbump`'s — a suite exists and is nearly empty — so the
  * lane re-derives BOTH numbers on every run rather than trusting this comment, and says so the day
  * either moves. What behavioural evidence this port can have at any scale is DIFFERENTIAL, against
  * the reference hand port's own 72-case MUnit suite; both are later waves, scoped in `PROGRESS.md`
  * §10.9.
  */
object VisUiMigrate:

  def main(args: Array[String]): Unit =
    val repoRoot = Path.of(sys.props.getOrElse("balticporter.root", ".")).toAbsolutePath.normalize
    val upstream = repoRoot.resolve("../sge/original-src/vis-ui").normalize
    val base     = upstream.resolve("ui/src/main/java").normalize
    val gdxSrc   = repoRoot.resolve("../sge/original-src/libgdx/gdx/src").normalize

    val files = Files.walk(base).iterator().asScala
      .filter(p => p.toString.endsWith(".java"))
      .map(p => base.relativize(p).toString)
      .filterNot(f => f.endsWith("package-info.java") || f.endsWith("module-info.java"))
      .toList.sorted

    PortRun(
      label     = "sge-visui",
      portRoot  = repoRoot.resolve("ported/sge-visui"),
      sourceSet = SourceSet.Main,
      frontend  = FrontendConfig(base, files, Nil, resolutionRoots = List(gdxSrc)),
      phases    = Nil, // supplied by the manifest — the two sources are mutually exclusive
      manifest  = Some(VisUiPolicy.core(repoRoot)),
      provenance = Some(Provenance(
        upstreamName     = "VisUI",
        upstreamCommit   = VendoredCommit.of(base),
        originalLicense  = "Apache-2.0",
        sourcePathPrefix = "vis-ui/ui/src/main/java",
        sourceRoot       = base.toString,
        // TWO REGIMES, and the SECOND one is not about the code at all (CLAUDE.md §4.57).
        //
        //   (a) Apache-2.0, the per-file header — carried by **163 of the 164** sources and by
        //       **161 of the 162 IN SCOPE** (both `package-info.java` carry it too and are emitted
        //       nowhere, which is why the lane's denominator is the converted set and not the
        //       tree). The per-file harvest (§4.58) plus the emitted banner meets that obligation
        //       by construction for those 161. `layout/FlowGroup.java` is the one exception — a
        //       later contribution opening with a bare `package` and a javadoc — so for that ONE
        //       emitted file the banner NAMES Apache-2.0 and reproduces no notice, which is
        //       exactly §4.57's pointer-instead-of-inclusion gap. That is `GdxAiPolicy`'s one-file
        //       case reproduced exactly, and it is the FIRST reason the key below exists;
        //
        //   (b) CC BY-ND 3.0, and it is a DIFFERENT LICENCE ON A DIFFERENT ARTEFACT. `ui/NOTICE`
        //       says outright that VisUI's shipped ICONS are licensed under CC BY-ND 3.0 and points
        //       at `ui/icons-license`, whose own text asks that the file be included in the source
        //       of an open-source project. Those icons are not source — they are baked into the
        //       `uiskin.atlas`/`uiskin.png` pairs the emitted code loads by classpath string — so no
        //       harvest, no banner and no per-file comment can ever reach the obligation, and no
        //       check in this engine could report its absence. It is the first NON-CODE licence in
        //       the corpus and the first NO-DERIVATIVES one, and it is the strongest form of the
        //       argument §4.57 makes with MIT: the port compiles, every count is flat, and it ships
        //       nothing.
        //
        // Apache-2.0 §4(d) is what makes the second entry unconditional rather than contingent on
        // whether this module ends up shipping the atlas: a derivative of a Work that includes a
        // NOTICE file must carry that file's attribution notices, and `ui/NOTICE` is one. Both are
        // therefore declared, and the ROOT `LICENSE` beside them for (a). The reference hand port
        // ships NEITHER of the two notice files — grepped: no file under `../sge/sge-extension/visui`
        // names `icons-license`, `CC BY-ND` or Templarian anywhere, although it does copy the atlas
        // the notice is about — so this is the second port in a row where the mechanical port is
        // measurably the more compliant one. A declared file that is not there is fatal, so none of
        // this can rot into a claim.
        notices          = List(upstream.resolve("LICENSE"),
                                upstream.resolve("ui/NOTICE"),
                                upstream.resolve("ui/icons-license")),
      )),
      // NOT `Vendored`: `LibgdxCoreMigrate` already vendors the collection shims into the module
      // this output is compiled beside. Vendoring again would define every support type twice —
      // which the JVM tolerates only while the copies agree, and the Scala.js/Native linkers reject.
      runtimeMode = RuntimeMode.Dependency,
      determinism = Determinism.fromArgs(args.toSeq),
      nextStep    = "just visui-measure",
    ).execute()

/** VisUI's per-library policy — a DEPENDENT of libGDX core's, and deliberately almost empty.
  *
  * The base's `dropTypes`, `dropMethods`, `packageRenames` and every signature-affecting phase are
  * INHERITED, not restated: they are facts about the surface these 162 files compile against, and a
  * dependent that re-declared them would be free to drift (CLAUDE.md §1.5). That is also where the
  * collections and mutable-parameter phases come from — a libGDX dependent does not start them, it
  * receives the base's ONE instance, and adding a second would be a `SurfaceDivergence` for a
  * composition nobody designed.
  *
  * `inject` is deliberately NOT inherited (see [[balticporter.core.PortManifest]]): a drop is an
  * observation about the shared API and binds every module that sees the type, but exactly one
  * module ships each replacement file. libGDX core ships the replacements for the types IT dropped.
  *
  * This wave adds a namespace claim, ONE rename and the base-surface residue check, and NOTHING
  * else — no drop, no substitution, no redirect, no scope, no context extension. A wall measured
  * under invented policy says nothing about the engine, so every entry after these arrives with the
  * number that justified it.
  */
object VisUiPolicy:

  def core(repoRoot: Path): PortManifest =
    LibgdxPolicy.core(repoRoot).extendedBy(PortManifest(
      name    = "sge-visui",
      // THE CLAIM IS THE MODULE'S SCOPE, NOT THE UPSTREAM ORGANISATION'S NAMESPACE. `com.kotcrab.vis`
      // covers `com.kotcrab.vis.usl` too, and this port emits none of it — a claim wider than what
      // the module emits is what `SurfaceIntrusion` screens against once a sibling module exists
      // (§1.5's "ask what the base EMITS"), and the USL follow-up is exactly such a sibling. So the
      // claim is the sub-namespace this run converts and the follow-up states its own.
      governs = Set("com.kotcrab.vis.ui"),
      // ONE PAIR, UNIFORM, AND VERIFIED 1:1 AGAINST THE REFERENCE PORT'S OWN TREE.
      //
      // The destination is the reference module `../sge/build.sbt`'s `sge-extension/visui`, which
      // declares `package sge.visui` and mirrors upstream's 22 sub-packages name for name
      // (`sge/visui/widget/file/internal` is `com/kotcrab/vis/ui/widget/file/internal`, and so on
      // for every one). So one prefix pair moves the whole library and longest-prefix-wins does the
      // rest, exactly as `com.badlogic.gdx -> sge` does for the base (INHERITED, not restated) —
      // and unlike `sge-textra`, the hand port makes NO per-type deviation from it that a
      // `typeRenames` entry would have to price.
      //
      // ==WHAT THE RENAME DOES NOT REACH, and it is a real obligation rather than a footnote==
      // VisUI loads its own skin through a HARDCODED CLASSPATH STRING —
      // `Gdx.files.classpath("com/kotcrab/vis/ui/skin/x1/uiskin.json")` in `VisUI.java`, and the
      // same shape for the i18n bundles and the colour picker's shaders. A rename moves SYMBOLS;
      // it does not and must not touch a string literal (§4.56). So the 22 resources that back
      // those strings belong at their UPSTREAM classpath path whatever this port's types are
      // called — which is verified to be exactly what the reference hand port does, byte-for-byte
      // the same relative layout under its own `src/main/resources`. This engine has no general
      // resource-copy mechanism (`PortManifest.serviceProviders` copies ONE well-known family and
      // rewrites both namespaces through the rename, which is the wrong act here — these paths must
      // NOT be rewritten), so the port emits code naming a classpath resource the run does not
      // ship. That is a stated residue and the §1 classification is (b): the mechanism is
      // `serviceProviders`' generalised to a declared, VERBATIM tree, and the file list is the
      // port's. `PROGRESS.md` §10.9 holds it with the numbers; it is emphatically not a drop.
      packageRenames = Map("com.kotcrab.vis.ui" -> "sge.visui"),
      // NOTHING IS WRITTEN HERE FOR `AsyncTask`, AND THAT IS A DECISION RATHER THAN AN OMISSION.
      // Upstream has `com.kotcrab.vis.ui.util.async.AsyncTask`, a STATEFUL abstract class with a
      // `Status` enum, a `CountDownLatch` and a listener list across four files; libGDX core has
      // `com.badlogic.gdx.utils.async.AsyncTask`, a single-abstract-method interface. They share a
      // simple name and nothing else — grepped both ways: `ui/` never imports
      // `com.badlogic.gdx.utils.async` at all, and the reference hand port's `sge.visui` never
      // names `sge.utils.async`. So this is §4.56's trap and not a construct with two answers: the
      // base RENAMES its own straight (`port-map.tsv`: `sge.utils.async.AsyncTask`, still a trait)
      // and the `() => Unit` collapse that `CLAUDE.md` §3.5 quotes is the HAND port's idiom choice,
      // which no phase in this engine performs. The obligation here is negative — no rename, no
      // substitution and no redirect keyed on that simple name may reach across — and the way to
      // discharge it is to write none, which is what this manifest does.
      surface = List(
        // LAST, deliberately, for the reason `AshleyPolicy`, `GdxAiPolicy` and `TextraTypistPolicy`
        // state: this reads what the BASE actually emitted and reports a reference the base does
        // not ship, so it must run after any seam that re-points such a reference, or it reports
        // the very sites the next phase repairs. A residue check, exactly like `PortabilityCheck`.
        // This wave has no such seam, which is the point — the rows it files ARE the wave's finding.
        balticporter.transform.PortMapTransform.forBases("sge"),
      ),
    ))
