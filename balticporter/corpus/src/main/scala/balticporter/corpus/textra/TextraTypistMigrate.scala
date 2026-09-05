package balticporter.corpus.textra

import balticporter.core.{FrontendConfig, ParityRef, PortManifest, Provenance, RuntimeMode}
import balticporter.corpus.{ClasspathCache, LlsClasspath}
import balticporter.corpus.libgdx.LibgdxPolicy
import balticporter.runner.{Determinism, PortRun, SourceSet, VendoredCommit}

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** Migrate **TextraTypist** (`src/main/java`, 92 types — libGDX's rich-text label family: a
  * font engine with markup, effects and scene2d widgets) through the TIR. A DEPENDENT port with
  * a THIRD-PARTY compile dependency of its own (`regexodus`, see [[TextraTypistClasspath]]):
  * `gdx/src` a RESOLUTION root, policy [[LibgdxPolicy.core]] EXTENDED (§1.5). NO TEST SOURCE SET
  * (upstream declares zero `@Test`); evidence is a DIFFERENTIAL probe (`PROGRESS.md` §10.8). */
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
      frontend  = FrontendConfig(base, files, LlsClasspath.entries(repoRoot) ++ TextraTypistClasspath.entries(repoRoot),
                                 resolutionRoots = List(gdxSrc)),
      phases    = Nil, // supplied by the manifest — the two sources are mutually exclusive
      manifest  = Some(TextraTypistPolicy.core(repoRoot)),
      provenance = Some(Provenance(
        upstreamName     = "TextraTypist",
        upstreamCommit   = VendoredCommit.of(base),
        originalLicense  = "Apache-2.0",
        sourcePathPrefix = "textratypist/src/main/java",
        sourceRoot       = base.toString,
        // TWO LICENCE REGIMES need a `notices` key here (CLAUDE.md §4.57): the emoji-regex MIT
        // notice is INLINE in `EmojiProcessor.java`'s own header; the typing-label MIT notice is
        // in NO file's comment anywhere (upstream discharges it via a repo-root file), so it is
        // declared explicitly. The reference hand port ships NEITHER file — this port is more
        // compliant here.
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
  * `com.github.tommyettinger:regexodus` is a pure-java regex engine (no `java.util.regex`
  * on GWT); it arrives as a CLASSPATH entry via [[ClasspathCache]]. Version read off
  * upstream's `gradle.properties` `regexodusVersion` rather than guessed. */
object TextraTypistClasspath:

  val Coordinates: List[String] = List("com.github.tommyettinger:regexodus:0.1.21")

  def cache(repoRoot: Path): Path = repoRoot.resolve("out/textra-classpath.txt")

  def entries(repoRoot: Path): List[Path] =
    ClasspathCache.entries(cache(repoRoot), "textratypist", Coordinates)

/** TextraTypist's per-library policy -- a DEPENDENT of libGDX core's, deliberately almost
  * empty. `dropTypes`/`dropMethods`/`packageRenames`/every signature-affecting phase are
  * INHERITED, not restated (CLAUDE.md §1.5); `inject` is NOT inherited. This wave adds a
  * namespace claim, a rename, one build coordinate and the base-surface residue check,
  * nothing else. */
object TextraTypistPolicy:

  def core(repoRoot: Path): PortManifest =
    LibgdxPolicy.core(repoRoot).extendedBy(PortManifest(
      name    = "sge-textra",
      governs = Set("com.github.tommyettinger.textra"),
      // ONE PAIR, UNIFORM; the reference port's one deviation (hoisting `LzmaUtils` to
      // top-level) is deliberately NOT reproduced — MEASURED and found not to apply here (§3.5):
      // the base already ports libGDX's compression sub-package under the inherited rename, and
      // nothing outside the destination package consumes the hoisted spelling. If a consumer is
      // ever found that needs the hand spelling, it is one `typeRenames` entry away.
      packageRenames = Map("com.github.tommyettinger.textra" -> "sge.textra"),
      // THE ARTIFACT THIS MODULE'S BUILD ADDS — what `SbtGen` writes into `libraryDependencies`
      // and `dependency-coverage` reads against. Not inherited (§1.5). RESIDUE: RegExodus is
      // JVM-only (no `_sjs1_3`/`_native0.5_3` published), while this module inherits the
      // all-platform default — narrowing `targets` is NOT the answer; `PROGRESS.md` §10.8 holds
      // it as a residue (RegExodus exists BECAUSE `java.util.regex` is missing off the JVM).
      dependencies = List(
        balticporter.catalog.ArtifactDep("com.github.tommyettinger", "regexodus", "0.1.21",
                                         balticporter.catalog.CrossKind.Java)),
      surface = List(
        globals,
        // DEPENDENT OPAQUE SEEDS for the Align family — five declarations propagation cannot
        // reach (their connection to Align is bitwise ops, not pure-move flows). Folds into the
        // base's ONE `PrimitiveToOpaqueTransform` instance via `MergeablePolicy` (`hints`
        // union; identity fields must agree with `LibgdxPolicy.core`'s entry). Four FIELDS and
        // one PARAMETER, upstream FQNs (the rename runs last, §4.56).
        new balticporter.transform.PrimitiveToOpaqueTransform(balticporter.tir.OpaqueSpec(
          fqn        = "com.badlogic.gdx.utils.Align",
          target     = balticporter.tir.OpaqueSpec.Target.Existing(
            typeFqn    = "sge.utils.Align",
            wrapName   = "apply",
            unwrapName = "toInt",
          ),
          hints      = Set(
            "com.github.tommyettinger.textra.TextraLabel#align",
            "com.github.tommyettinger.textra.TextraField#textHAlign",
            "com.github.tommyettinger.textra.TextraListBox#alignment",
            "com.github.tommyettinger.textra.TextraSelectBox#alignment",
            "com.github.tommyettinger.textra.Font#drawGlyphs#align",
          ),
          underlying = balticporter.tir.OpaqueSpec.Primitive.Int,
        )),
        // 3.1ba: body substitutions for retarget-chained-call residue — THREE FAMILIES, each a
        // Collect-produced DynamicArray at a slot the chained call cannot reach: (1) Parser:
        // `keys().toArray(tokens)` inlined via `foreachKey(tokens.add)`; (2) TextraListBox/
        // SelectBox selectedIndex: lls `OrderedSet` does NOT extend `ObjectSet` (K37), inline
        // `selection.items` directly. Font ctor/loadJSON: counted `CollectChainedCall` residue.
        new balticporter.transform.MethodBodyTransform(Map(
          // --- (2) selectedIndex: inline selection.items usage ---
          "com.github.tommyettinger.textra.TextraListBox#getSelectedIndex" ->
            """{
              |  val selected: lowlevel.util.OrderedSet[T] = this.selection$field.items
              |  return if (selected.size == 0) -1 else this.items$field.indexOf(selected.first)
              |}""".stripMargin,
          "com.github.tommyettinger.textra.TextraSelectBox#getSelectedIndex" ->
            """{
              |  val selected: lowlevel.util.OrderedSet[sge.textra.TextraLabel] = this.selection$field.items
              |  return if (selected.size == 0) -1 else this.items$field.indexOf(selected.first)
              |}""".stripMargin,
        )),
        // LAST, deliberately (as AshleyPolicy/GdxAiPolicy): reads what the BASE actually
        // emitted; must run after any seam re-pointing such a reference.
        balticporter.transform.PortMapTransform.forBases("sge"),
      ),
      // THE REFERENCE HAND PORT for sge-textra. NOT inherited (DESIGN.md §8.23).
      parity = Some(ParityRef(roots = List(
        repoRoot.resolve("../sge/sge-extension/textra/src/main/scala").normalize))),
    ))

  /** WHAT A DEPENDENT ADDS TO THE BASE'S CONTEXT HOLDER (`ENGINE-LIMITS.md` CT8) — closed the
    * way the REFERENCE HAND PORT closed it. `LinkEffect#onApply` calls `Gdx.net.openURI(link)`
    * from a LAMBDA in a companion-initialised registry, where no context is threadable; the
    * standard exits were priced and refused (`PROGRESS.md` §10.8.9). The hand port's own answer
    * (§3.5): `retain` on `TextraLabel` plus `selfSupplied` reading `this.label.sgeContext`. */
  def globals: balticporter.transform.GlobalsToImplicitsTransform =
    new balticporter.transform.GlobalsToImplicitsTransform(extensions = List(
      balticporter.transform.ContextHolderExtension(
        holder = "com.badlogic.gdx.Gdx",
        retain = Map("com.github.tommyettinger.textra.TextraLabel" -> "sgeContext"),
        selfSupplied = Map(
          "com.github.tommyettinger.textra.effects.LinkEffect" -> "this.label.sgeContext"),
      )
    ))

