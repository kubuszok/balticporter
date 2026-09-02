package balticporter.corpus.visui

import balticporter.core.{FrontendConfig, ParityRef, PortManifest, Provenance, RuntimeMode}
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
      // the same relative layout under its own `src/main/resources`. The `resources` key below is
      // what SHIPS them (`DESIGN.md` §8.22), and it is a copy and not a rewrite for exactly the
      // reason stated here.
      packageRenames = Map("com.kotcrab.vis.ui" -> "sge.visui"),
      // THE 22 RESOURCES THE EMITTED CODE ASKS FOR, COPIED VERBATIM (`DESIGN.md` §8.22).
      //
      // ==22 of the 24 files under that root, and the 2 are a DECISION rather than an oversight==
      // `META-INF/robovm/ios/robovm.xml` and `com/kotcrab/vis/vis-ui.gwt.xml` belong to the UPSTREAM
      // BUILD and not to this library. The second one is the worked example §8.22's rejected-scan
      // argument wants: it is FQNs and JAVA PATHS, which is the REWRITE shape and not the copy
      // shape — `<source path='ui'>` with twelve `<exclude name="widget/file/FileChooser.java"/>`
      // entries, and eleven `gdx.reflect.include` values naming `com.kotcrab.vis.ui.*` types this
      // port renames to `sge.visui.*`. Copied verbatim it would advertise sources and reflection
      // roots that do not exist in this port; rewritten it would need a cross-compiler-module
      // rewriter nobody has written. Declining it is the honest act, and the control is the
      // reference hand port, whose `src/main/resources` holds exactly these 22 and neither of them.
      //
      // ==And the list is CONFIRMED by upstream's own enumeration, not just by that control==
      // That same GWT module lists the classpath resources this library needs at run time —
      // `extend-configuration-property name="gdx.files.classpath"` — and its 22 values are exactly
      // the 22 below, family for family (5 skin files × 2 scales, 6 shaders, 6 bundles). So this
      // list is upstream's own answer to the question, read out of a file the port does not ship.
      //
      // ==MEASURED: 2 of the 22 are named by a literal, and the other 20 are the point==
      // The lane compares this list against the string literals in the emitted Scala, and the split
      // is `shipped 2 / unnamed 20`. Only the two `uiskin.json` are named OUTRIGHT — every other
      // file is reached by one of three indirections none of which any phase can walk:
      //
      //   * through ANOTHER RESOURCE's content — the skin json names its `.atlas` and both `.fnt`,
      //     and the atlas names its `.png` page. Nothing in this engine reads a skin;
      //   * through a name the LIBRARY completes — the six bundles are named `…/i18n/ButtonBar`,
      //     without the extension, because libGDX's `I18NBundle` appends `.properties` (and a
      //     locale) itself;
      //   * through a DIRECTORY literal plus a file name — the six colour-picker shaders.
      //
      // So an `unnamed` row means *no literal EQUALS this path*, never *nothing references it*, and
      // the check deliberately stops there: inferring that `…/ButtonBar` plus a suffix is a
      // reference to `…/ButtonBar.properties` would be §4.6's fabricated fact, and it would report
      // a hit for a port where that guess is wrong. The 20 are exactly the population a DECLARATION
      // exists to carry, and they are why a check over emitted literals could never have replaced
      // this list.
      resources = List(balticporter.core.ResourceTree(
        repoRoot.resolve("../sge/original-src/vis-ui/ui/src/main/resources").normalize,
        List(
          // the i18n bundles — `Locales` names each of these base names and libGDX's `I18NBundle`
          // appends the `.properties`.
          "com/kotcrab/vis/ui/i18n/ButtonBar.properties",
          "com/kotcrab/vis/ui/i18n/ColorPicker.properties",
          "com/kotcrab/vis/ui/i18n/Common.properties",
          "com/kotcrab/vis/ui/i18n/Dialogs.properties",
          "com/kotcrab/vis/ui/i18n/FileChooser.properties",
          "com/kotcrab/vis/ui/i18n/TabbedPane.properties",
          // the two SKINS. `VisUI.SkinScale` names the `.json` of each; the `.json` names the
          // `.atlas` and the two `.fnt`, and the `.atlas` names the `.png`.
          "com/kotcrab/vis/ui/skin/x1/default.fnt",
          "com/kotcrab/vis/ui/skin/x1/font-small.fnt",
          "com/kotcrab/vis/ui/skin/x1/uiskin.atlas",
          "com/kotcrab/vis/ui/skin/x1/uiskin.json",
          "com/kotcrab/vis/ui/skin/x1/uiskin.png",
          "com/kotcrab/vis/ui/skin/x2/default.fnt",
          "com/kotcrab/vis/ui/skin/x2/font-small.fnt",
          "com/kotcrab/vis/ui/skin/x2/uiskin.atlas",
          "com/kotcrab/vis/ui/skin/x2/uiskin.json",
          "com/kotcrab/vis/ui/skin/x2/uiskin.png",
          // the colour picker's shaders, whose paths the widget builds from a directory literal.
          "com/kotcrab/vis/ui/widget/color/internal/checkerboard.frag",
          "com/kotcrab/vis/ui/widget/color/internal/default.vert",
          "com/kotcrab/vis/ui/widget/color/internal/hsv.frag",
          "com/kotcrab/vis/ui/widget/color/internal/palette.frag",
          "com/kotcrab/vis/ui/widget/color/internal/rgb.frag",
          "com/kotcrab/vis/ui/widget/color/internal/verticalBar.frag",
        ))),
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
        // THE ONE MEMBER THIS LIBRARY HAS TO MOVE, AND THE BASE IS WHY (`PROGRESS.md` §10.9.7
        // family 2, `ENGINE-LIMITS.md` D13).
        //
        // libGDX core redirects `com.badlogic.gdx.utils.Disposable` onto `java.lang.AutoCloseable`
        // and renames `dispose -> close` across the override component. VisUI declares FIVE
        // `Disposable` implementors, and two of the classes in that component already declare a
        // `close()` of their own — `VisWindow`'s close-button handler (`addCloseButton`,
        // `closeOnEscape`), which `ColorPicker` overrides. One emitted class cannot declare
        // `close()` twice, so `MemberRenamer.OnCollision.Refuse` refused the whole component and
        // the port emitted five classes claiming `AutoCloseable` and implementing none of it.
        //
        // `java.lang.AutoCloseable#close` is not negotiable, so the member that moves is VisUI's
        // OWN — and which of two members keeps a name is exactly what the engine may not invent
        // (that refusal is correct and stays). `closeWindow` is this port's answer: it is free
        // upstream (grepped — the string occurs nowhere in `ui/`), it says what the member does,
        // and it is the WINDOW half of the pair rather than the `AutoCloseable` half, which is the
        // only half a consumer can substitute for. The reference hand port keeps `close()` here
        // and pays for it by NOT retargeting `Disposable` on these types at all — a hand port's
        // freedom, per CLAUDE.md §3.5, and not a mechanical port's option, since the base has
        // already dropped `Disposable`.
        //
        // The KEY names `VisWindow#close` and the rename takes the component, so `ColorPicker`'s
        // override moves with it — the whole-or-none guarantee `MemberRenamer` exists for.
        balticporter.transform.MemberRenameTransform(Map(
          "com.kotcrab.vis.ui.widget.VisWindow#close" -> "closeWindow",
        )),
        // THE CONTEXT SEAM'S EXIT, AND IT IS AN EXTENSION OF THE BASE'S HOLDER — never a second
        // holder (`PROGRESS.md` §10.9.7 family 1, §10.9.10; `ENGINE-LIMITS.md` CT8).
        //
        // libGDX core declares `com.badlogic.gdx.Gdx`; this module inherits that one instance and
        // adds the PER-DECLARATION half for its OWN types, which is the only manifest such a key
        // can be written in. Every key below names a `com.kotcrab.vis.ui` type, so the `governs`
        // screen passes and nothing here re-shapes what the base emits.
        //
        // ==`cache` on `VisUI`, because that is where this library's lifecycle is==
        // `VisUI` is an all-`static` holder: `load(…)` reads `Gdx.files` and `checkBeforeLoad()`
        // reads `Gdx.app`, so the threading puts the clause on its METHODS and nothing constructs
        // it — it is in no `threadedClasses` and `retain` would emit nothing. `sgeInstance` is the
        // REFERENCE HAND PORT'S OWN NAME for this member (`../sge/sge-extension/visui`'s
        // `VisUI.sgeInstance`, over a `_sgeInstance` it assigns inside each `load`), which is what
        // makes it this library's convention rather than the engine's invention.
        //
        // ==`selfSupplied` on the three enums the threading reached and could not sign==
        // An emitted `enum`'s primary IS its java constructor and every case reaches it with its
        // own argument list, so a clause there is a `lost-clause` refusal. These three take the
        // context WITHOUT taking a parameter, from the accessor above — which is the hand port's
        // own second member, read as an expression. The value is captured by whichever `load` the
        // consumer calls, and a class-level `given` is initialised lazily (measured, §10.8.11), so
        // an enum constant built at module initialisation does not read it.
        balticporter.transform.GlobalsToImplicitsTransform(Nil, List(
          balticporter.transform.ContextHolderExtension(
            holder = "com.badlogic.gdx.Gdx",
            cache  = Map("com.kotcrab.vis.ui.VisUI" -> "sgeInstance"),
            // NO `sites` ENTRY FOR `Draggable#BLOCKER`, and the reason is MEASURED rather than
            // assumed — `ENGINE-LIMITS.md` CT11. `lazy-init` on that field DOES fire (the field is
            // a static with its own `new Actor()` initialiser, which is exactly the shape
            // `ContextNeed.fromField` is written for), and it moves the `E172` one member over
            // instead of closing it: the `static { BLOCKER.addListener(…) }` block then READS a
            // field that has become context-taking, and a class initialiser has no clause of its
            // own to supply one. 8 -> 8 errors, `context-seam` 39 -> 40, 9 member digests moved for
            // zero net. It stays the counted `unsuppliable-use` until a mechanism exists for the
            // BLOCK.

            //
            // ==all EIGHT of them, and the second five arrived with the anchor lift==
            // `Locales$CommonText` and its four siblings look up an i18n bundle from a
            // `private static getBundle()`, which was FROZEN on `java.lang.Enum#getBundle` until
            // the graph learned that a `private` member is not inherited (JLS 8.2). Unfreezing it
            // threads that static and its instance callers then reach the enum as a CLASS — which
            // is exactly the `lost-clause` the other three had, arriving five more times. That is
            // `ENGINE-LIMITS.md` CT10's own sequence read forwards: the anchor lift is admissible
            // once the cached context exists to answer what it uncovers, and not before.
            selfSupplied = Map(
              "com.kotcrab.vis.ui.VisUI$SkinScale"                          -> "sge.visui.VisUI.sgeInstance",
              "com.kotcrab.vis.ui.widget.ButtonBar$ButtonType"              -> "sge.visui.VisUI.sgeInstance",
              "com.kotcrab.vis.ui.building.utilities.layouts.TableLayout"   -> "sge.visui.VisUI.sgeInstance",
              "com.kotcrab.vis.ui.Locales$CommonText"                       -> "sge.visui.VisUI.sgeInstance",
              "com.kotcrab.vis.ui.util.dialog.Dialogs$Text"                 -> "sge.visui.VisUI.sgeInstance",
              "com.kotcrab.vis.ui.widget.color.internal.ColorPickerText"    -> "sge.visui.VisUI.sgeInstance",
              "com.kotcrab.vis.ui.widget.file.internal.FileChooserText"     -> "sge.visui.VisUI.sgeInstance",
              "com.kotcrab.vis.ui.widget.tabbedpane.TabbedPane$Text"        -> "sge.visui.VisUI.sgeInstance",
            ),
          ),
        // --- 3.1aq: requiredGivens for generic classes constructing retarget targets
        ), requiredGivens = Map(
          "com.kotcrab.vis.ui.util.adapter.AbstractListAdapter" -> "lowlevel.MkArray",
          "com.kotcrab.vis.ui.util.adapter.CachedItemAdapter" -> "lowlevel.MkArray",
          "com.kotcrab.vis.ui.widget.spinner.ArraySpinnerModel" -> "lowlevel.MkArray",
        )),
        // DEPENDENT SEEDS for the base's `Align` opaque family — the same `MergeablePolicy` merge
        // VfxPolicy uses, for the same reason: propagation follows pure-move flows and does NOT
        // follow a bitwise test (`(alignment & Align.left) != 0`), so VisUI's own `int alignment`
        // fields — which are only ever compared against `Align.*` constants or passed to the base's
        // `Cell.align(int)` — are never reachable from the base's field hints alone.
        //
        // Three fields, each initialised from `Align.*` and consumed by bit tests or by the base's
        // `Cell.align`. Propagation discovers the constructors, getters, and setters from them.
        new balticporter.transform.PrimitiveToOpaqueTransform(balticporter.tir.OpaqueSpec(
          fqn        = "com.badlogic.gdx.utils.Align",
          target     = balticporter.tir.OpaqueSpec.Target.Existing(
            typeFqn    = "sge.utils.Align",
            wrapName   = "apply",
            unwrapName = "toInt",
          ),
          hints      = Set(
            // `Alignment` enum's sole field — `private final int alignment`, initialised from
            // `Align.center`, `Align.top`, etc. Seeding it causes propagation to reach the
            // constructor parameter, `getAlignment()`, and the enum constant arguments.
            "com.kotcrab.vis.ui.building.utilities.Alignment#alignment",
            // `ToastManager`'s own field — `protected int alignment = Align.topRight`, with
            // `setAlignment(int)` and `getAlignment()` beside it.
            "com.kotcrab.vis.ui.util.ToastManager#alignment",
            // `VisTextField`'s own field — `private int textHAlign = Align.left`, with
            // `setAlignment(int)` writing it and layout code reading it through bit tests.
            "com.kotcrab.vis.ui.widget.VisTextField#textHAlign",
          ),
          underlying = balticporter.tir.OpaqueSpec.Primitive.Int,
        )),
        // LAST, deliberately, for the reason `AshleyPolicy`, `GdxAiPolicy` and `TextraTypistPolicy`
        // state: this reads what the BASE actually emitted and reports a reference the base does
        // not ship, so it must run after any seam that re-points such a reference, or it reports
        // the very sites the next phase repairs. A residue check, exactly like `PortabilityCheck`.
        // This wave has no such seam, which is the point — the rows it files ARE the wave's finding.
        balticporter.transform.PortMapTransform.forBases("sge"),
      ),
      // THE REFERENCE HAND PORT for sge-visui. NOT inherited (DESIGN.md §8.23).
      parity = Some(ParityRef(roots = List(
        repoRoot.resolve("../sge/sge-extension/visui/src/main/scala").normalize))),
    ))
