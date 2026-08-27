package balticporter.corpus.vfx

import balticporter.core.{FrontendConfig, PortManifest, Provenance, RuntimeMode}
import balticporter.corpus.libgdx.LibgdxPolicy
import balticporter.runner.{Determinism, PortRun, SourceSet, VendoredCommit}

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** Migrate **gdx-vfx** (44 types — post-processing shader effects for libGDX) through the TIR.
  *
  *   corpus/runMain balticporter.corpus.vfx.VfxMigrate [--determinism=full]
  *
  * ==Why gdx-vfx is in the corpus==
  * It is the corpus's first GL-facing library, and the first whose whole reason for existing is a
  * resource the JVM does not own: every effect is a `ShaderProgram` compiled from a `.vert`/`.frag`
  * asset, driven through framebuffer ping-pong. Two consequences the earlier libraries never
  * produced:
  *
  *   - **a library whose API surface is mostly libGDX's.** `VfxFrameBuffer`, `ViewportQuadMesh` and
  *     `VfxGLUtils` are thin wrappers over `FrameBuffer`, `Mesh`, `ShaderProgram`, `GL20` and
  *     `Gdx.gl`, so nearly every signature this port emits mentions a type the BASE port emitted.
  *     That is what makes it a real test of §1.5: it can only compile if libGDX core's transforms
  *     did to those signatures exactly what this run assumes they did.
  *   - **a reflective branch that exists only for a backend nobody ports.** `VfxGLUtils`' static
  *     initialiser reaches `ClassReflection.newInstance(ClassReflection.forName("…gwt.
  *     GwtVfxGlExtension"))` when the application type is `WebGL`. See [[VfxPolicy]] for what the
  *     port does with it and why.
  *
  * ==A DEPENDENT port==
  * Every one of the 44 files resolves against libGDX core — `Gdx`, `GL20`, `Texture`, `Pixmap`,
  * `Mesh`, `ShaderProgram`, `FrameBuffer`, `Array`, `Pool`, `Disposable`, `Vector2`, `Matrix4`,
  * `WidgetGroup` — so `gdx/src` is a RESOLUTION root, parsed so references resolve and never
  * emitted here, and the policy is [[LibgdxPolicy.core]] EXTENDED rather than restated
  * (CLAUDE.md §1.5). `LibgdxCoreMigrate` emits the base and the two are compiled together by
  * `just vfx-measure`.
  *
  * ==Scope==
  * The two LIBRARY gradle modules, `gdx-vfx/core/src` (23 types) and `gdx-vfx/effects/src` (21),
  * emitted into ONE sbt module — which is what the reference hand port does too
  * (`../sge/sge-extension/vfx` holds both, at `sge.vfx`). They share one package root
  * (`com.crashinvaders.vfx`), effects depends on core and nothing depends on effects alone, so a
  * second port would buy a module boundary the consumer does not have.
  *
  * Deliberately excluded, and named rather than silently dropped:
  *
  *   - **`gdx-vfx/gwt/src`** (1 file, `GwtVfxGlExtension`) — the GWT backend's `VfxGlExtension`
  *     implementation. sge targets Scala Native and Scala.js; a GWT backend is dead weight, and the
  *     reference port does not carry it either. Its absence is exactly what makes the reflective
  *     branch above unreachable rather than merely unported.
  *   - **`demo/`** (74 files) — five launcher modules and an LML/VisUI-driven demo application.
  *     Not library surface, and it depends on third-party libraries (`gdx-lml`, `VisUI`) that are
  *     not in the corpus.
  *
  * ==There is no upstream suite==
  * `@Test` count over the WHOLE gdx-vfx checkout, comments stripped: **0**. gdx-vfx ships no test
  * source set at all — not a set of demos misread as one (anim8) and not a runnable sample module
  * (jbump), simply nothing. So there is no `VfxTestMigrate` and no emitted test source set; this
  * port's only behavioural evidence is the hand-written MUnit suite committed under
  * `ported/sge-vfx/src/test/scala` (CLAUDE.md §3: a green compile says nothing about behaviour), which is
  * anim8's precedent. `just vfx-measure` prints the upstream zero and the hand-written count side
  * by side, because `0 == 0` must not be allowed to read as agreement.
  */
object VfxMigrate:

  def main(args: Array[String]): Unit =
    val repoRoot = Path.of(sys.props.getOrElse("balticporter.root", ".")).toAbsolutePath.normalize
    // The COMMON root of the two library modules. Spoon takes each file as its own input resource
    // (`SpoonTir` adds them one at a time), so a source root that is a plain ancestor rather than a
    // package root is fine — and it is what keeps `PortRun.converted`'s "under sourceRoot, not
    // under a resolutionRoot" test able to see both modules as this run's own.
    val base   = repoRoot.resolve("../sge/original-src/gdx-vfx/gdx-vfx").normalize
    val gdxSrc = repoRoot.resolve("../sge/original-src/libgdx/gdx/src").normalize

    // `gwt/` is the third directory under `base` and is NOT converted — see the scope note.
    val files = Files.walk(base).iterator().asScala
      .filter(p => p.toString.endsWith(".java"))
      .map(p => base.relativize(p).toString)
      .filter(f => f.startsWith("core/src/") || f.startsWith("effects/src/"))
      .filterNot(f => f.endsWith("package-info.java") || f.endsWith("module-info.java"))
      .toList.sorted

    PortRun(
      label     = "sge-vfx",
      portRoot  = repoRoot.resolve("ported/sge-vfx"),
      sourceSet = SourceSet.Main,
      frontend  = FrontendConfig(base, files, Nil, resolutionRoots = List(gdxSrc)),
      phases    = Nil, // supplied by the manifest — the two sources are mutually exclusive
      manifest  = Some(VfxPolicy.core(repoRoot)),
      provenance = Some(Provenance(
        upstreamName     = "gdx-vfx",
        upstreamCommit   = VendoredCommit.of(base),
        originalLicense  = "Apache-2.0",
        // `sourceRoot` above is `<checkout>/gdx-vfx`, so a relativised origin reads
        // `core/src/com/crashinvaders/…` and this prefix restores the upstream repo-relative form.
        sourcePathPrefix = "gdx-vfx",
        sourceRoot       = base.toString,
      )),
      // NOT `Vendored`: `LibgdxCoreMigrate` already vendors the collection shims into the module
      // this output is compiled beside. Vendoring again would define every support type twice —
      // which the JVM tolerates only while the copies agree, and the Scala.js/Native linkers reject.
      runtimeMode = RuntimeMode.Dependency,
      determinism = Determinism.fromArgs(args.toSeq),
      nextStep    = "just vfx-measure",
    ).execute()

/** gdx-vfx's per-library policy — a DEPENDENT of libGDX core's.
  *
  * The base's `dropTypes`, `dropMethods`, `packageRenames` and signature-affecting phases are
  * INHERITED, not restated: they are facts about the surface gdx-vfx compiles against, and a
  * dependent that re-declared them would be free to drift. What gdx-vfx adds is its own namespace
  * claim, its own rename, and the one seam its 44 files need.
  *
  * `inject` is deliberately NOT inherited (see [[balticporter.core.PortManifest]]): a drop is an
  * observation about the shared API and binds every module that sees it, but exactly one module
  * ships each replacement file. libGDX core ships the replacements for the types it dropped.
  */
object VfxPolicy:

  def core(repoRoot: Path): PortManifest =
    LibgdxPolicy.core(repoRoot).extendedBy(PortManifest(
      name    = "sge-vfx",
      governs = Set("com.crashinvaders.vfx"),
      // sge puts gdx-vfx at `sge.vfx` (`../sge/sge-extension/vfx/src/main/scala/sge/vfx`), with the
      // upstream subpackages carried straight through — `sge/vfx/effects`, `sge/vfx/framebuffer`,
      // `sge/vfx/gl`, `sge/vfx/utils` are all there in the hand port — so one prefix pair moves the
      // whole library. libGDX's `com.badlogic.gdx -> sge` is INHERITED from the base manifest, not
      // restated; longest-prefix-wins keeps the two apart.
      packageRenames = Map("com.crashinvaders.vfx" -> "sge.vfx"),
      // TWO PER-LOCATION SELECTIONS (`DESIGN.md` §8.16/§8.21), one on each of the two lanes that
      // gained a menu last. Both are `accept`-shaped, neither changes a byte of emitted code, and
      // what each changes is that a residue nobody had read becomes a residue somebody has.
      resolutions = Map(
        // `@SuppressWarnings("unchecked")` on the CLASS — `omissions`' `annotation dropped`, at a
        // TYPE subject, which is why the id is the `-type-` one (`Remedy.subject` is per remedy and
        // this lane's rows sit at both kinds of subject).
        //
        // READ, and the reading is stronger than the general argument. `@SuppressWarnings` names a
        // javac WARNING and scala has neither the warning nor the annotation, so carrying it would
        // put text on the emitted class that says nothing to any tool that reads it — that much is
        // true of every row of this kind. What is true HERE is that the annotation suppresses
        // nothing even in JAVA: all 193 lines are a non-generic abstract class over a
        // `ShaderProgram`, with no cast, no type variable and no raw type anywhere in it, so there
        // is no unchecked conversion for javac to have warned about. The port drops a marker that
        // was already vestigial upstream. This is exactly the population
        // `FrontendConfig.preservedAnnotations` exists to let a port claim FAMILY BY FAMILY
        // (ENGINE-LIMITS.md T16), and this entry is its complement: the port states that this one is
        // right to lose rather than claiming the whole `java.lang` family is worth carrying.
        "com.crashinvaders.vfx.effects.ShaderVfxEffect" -> "accept-dropped-type-annotation",

        // `java.util.Map#clear()` — `jdk-surface`'s `unhandled`, at the EXTERNAL CALLEE, which is
        // what that lane's subject column names and therefore what its key is, verbatim.
        //
        // READ: the one site is `ValueArrayMap#clear()`, whose `map` field
        // `CollectionsTransform` retyped from `java.util.Map<K,V>` to
        // `scala.collection.mutable.Map[K,V]`. The phase has no table entry for `clear`, and the
        // emitted `this.map.clear()` compiles and behaves because `mutable.Map` inherits `clear()`
        // from `Clearable` with java's own semantics — remove every mapping. That is COVERAGE BY
        // COINCIDENCE, which is precisely what the row is for; whether this port relies on the
        // coincidence is a reading of the call and not of a table, and it is emphatically not a
        // `JdkSurfaceCheck.Refusals` entry, which would be the ENGINE silencing the row on all
        // fifteen ports at once.
        "java.util.Map#clear()" -> "accept-jdk-member",
      ),
      surface = List(
        // `VfxGLUtils`' STATIC INITIALISER is gdx-vfx's one reflective site, and the reflection is
        // there to reach a class this port does not have:
        //
        //   if (Gdx.app.getType() == ApplicationType.WebGL)
        //     glExtension = (VfxGlExtension) ClassReflection.newInstance(
        //         ClassReflection.forName("com.crashinvaders.vfx.gwt.GwtVfxGlExtension"));
        //   else
        //     glExtension = new DefaultVfxGlExtension();
        //
        // `ClassReflection` is a type the BASE drops — reflective instantiation is the one thing
        // Scala.js and Scala Native cannot do — and `GwtVfxGlExtension` lives in `gdx-vfx/gwt`,
        // which is out of scope (sge targets Native and JS, not GWT). So the branch is not merely
        // unported, it is UNREACHABLE: nothing in this port can ever be a WebGL application whose
        // GL extension is that class. Keeping the reflective call to preserve a branch that cannot
        // be taken would fail to compile for no behaviour.
        //
        // The reference hand port reached the same place: `../sge/sge-extension/vfx/src/main/scala/
        // sge/vfx/gl/VfxGLUtils.scala` has no WebGL branch at all — `initExtension()` assigns
        // `DefaultVfxGlExtension()` unconditionally (CLAUDE.md §3.5: it SOLVED this, it did not
        // skip it). This is the same solution, expressed as policy rather than as a fork: the other
        // ~100 lines of `VfxGLUtils` — shader compilation, the viewport query, the GL state query —
        // still translate mechanically and still track upstream.
        //
        // NB the KEY is the UPSTREAM namespace (the phase matches the model before the rename runs)
        // and the BODY is the port's FINAL namespace (it is spliced verbatim and the rename never
        // sees it). `<clinit>` is what the frontend names a `static { }` block; an instance
        // initialiser is `<initblock>`.
        //
        // ==THE PAIR, and why the initialisation had to LEAVE the class initialiser==
        // The base port retires `Gdx` into a threaded `sge.Sge` context (DESIGN.md §8.4) and
        // `DefaultVfxGlExtension` — whose one method reads `Gdx.gl` — is one of the classes it
        // threads, so constructing one now takes the clause. A `static { }` block has no clause and
        // no caller to take one from, which is precisely the boundary the globals phase counts. So
        // the class initialiser becomes EMPTY and the construction moves to the first call that has
        // a context: `VfxFrameBuffer#getBoundFboHandle`, an instance method of a threaded class.
        //
        // That is once more the reference hand port's own answer rather than an invention —
        // `../sge/sge-extension/vfx/.../VfxGLUtils.scala` has `def initExtension()(using Sge)` with
        // exactly this null guard, called from `getBoundFboHandle()(using Sge)`. It sits one member
        // further out here for a mechanical reason: the emitted `VfxGLUtils.getBoundFboHandle()`
        // reads no holder of its own, so nothing threaded it, and a body substitution may change
        // what a member DOES but never what it takes.
        //
        // Ordering: the null guard is not a race the port introduced. Java ran the same assignment
        // at class initialisation, i.e. before any caller could touch a framebuffer; the guard runs
        // it at the first framebuffer bind instead, which is the first moment a GL context exists at
        // all. `VfxFrameBuffer#getBoundFboHandle` is the ONLY reader of `VfxGLUtils.glExtension`
        // INSIDE this port — but it is not the only way in, which is the third entry.
        //
        // ==AND `VfxGLUtils.getBoundFboHandle()` IS PUBLIC API, so the guard is not enough==
        // `public static int getBoundFboHandle()` is upstream's own entry point and both it and the
        // `public static VfxGlExtension glExtension` field are part of gdx-vfx's surface. A consumer
        // — sge, an effect written against this library, anything outside the port — may call it
        // without ever having touched a `VfxFrameBuffer`, and in JAVA that always worked, because
        // the class initialiser had already run by definition. With `<clinit>` emptied above it
        // NULLs, at a line whose text says nothing about why.
        //
        // The member cannot initialise: constructing a `DefaultVfxGlExtension` takes the threaded
        // `sge.Sge` and this is a `static` with no clause and no caller to take one from — the same
        // boundary that moved the initialisation out of `<clinit>` in the first place, and CLAUDE.md
        // §1(b)'s rule for a body substitution (it may change what a member DOES, never what it
        // TAKES) says the fix has to sit at a member the closure already reached, which is why the
        // entry above is on `VfxFrameBuffer` and not here.
        //
        // What it CAN do is fail informatively. An `IllegalStateException` naming the initialisation
        // path turns a bare NPE into the one sentence its reader needs, and it is honest about the
        // port's own decision rather than about a bug in the caller. This is the residue the
        // reference hand port does not have to carry: sge's `VfxGLUtils.initExtension()(using Sge)`
        // is a hand-written member that takes the clause, and a generated one cannot be edited to.
        new balticporter.transform.MethodBodyTransform(Map(
          "com.crashinvaders.vfx.gl.VfxGLUtils#<clinit>" -> "{ }",
          "com.crashinvaders.vfx.framebuffer.VfxFrameBuffer#getBoundFboHandle" ->
            """{
              |  if (sge.vfx.gl.VfxGLUtils.glExtension == null)
              |    sge.vfx.gl.VfxGLUtils.glExtension = new sge.vfx.gl.DefaultVfxGlExtension()
              |  sge.vfx.gl.VfxGLUtils.getBoundFboHandle()
              |}""".stripMargin,
          "com.crashinvaders.vfx.gl.VfxGLUtils#getBoundFboHandle" ->
            """{
              |  if (sge.vfx.gl.VfxGLUtils.glExtension == null)
              |    throw new java.lang.IllegalStateException(
              |      "sge.vfx.gl.VfxGLUtils.glExtension is not initialised. Upstream assigned it in a " +
              |        "static initialiser; this port cannot, because constructing a DefaultVfxGlExtension " +
              |        "needs the sge.Sge context and a class initialiser has no clause to take it from. " +
              |        "It is initialised on the first VfxFrameBuffer bind (VfxFrameBuffer.getBoundFboHandle); " +
              |        "bind one first, or assign VfxGLUtils.glExtension yourself.")
              |  sge.vfx.gl.VfxGLUtils.glExtension.boundFboHandle
              |}""".stripMargin,
        )),
        // WHAT A DEPENDENT ADDS TO THE BASE'S CONTEXT HOLDER — `ENGINE-LIMITS.md` CT8.
        //
        // The holder itself is SHARED SURFACE and is inherited from `LibgdxPolicy.core` (§1.5): the
        // context type, the member map, the attachment mode, the read shape and the boundary default
        // are all facts about signatures this port compiles against, and a `ContextHolderExtension`
        // has no field in which any of them could be restated. What it carries is the
        // PER-DECLARATION half, which is keyed on DECLARATIONS — and gdx-vfx's boundaries are in
        // gdx-vfx's own types, which the base neither governs nor parses.
        //
        // There is exactly one: `VfxFrameBuffer#tmpCam` is a `private static final
        // OrthographicCamera` initialised at class initialisation, and `OrthographicCamera` is one
        // of the classes the base threads. A static has no clause and no caller, so without this
        // entry it is a scalac error the correlator classifies `EngineGap` — correctly, because the
        // port would have nowhere to put the fix. With it, the initialisation moves to first READ,
        // which is a counted `deferred-init` seam and a `DeferredInit` decision with a porter note.
        //
        // Two things it does NOT carry, both deliberate:
        //   - `VfxGLUtils#<clinit>`, which READS the holder rather than initialising a static from a
        //     threaded construction. `lazy-init` is the wrong site kind there — measured as a
        //     `never matched` policy finding — and its answer is the body substitution above;
        //   - a `selfSupplied` entry. gdx-vfx's suite is HAND-WRITTEN `src/` Scala, so it declares
        //     its own `given` (CLAUDE.md §5.5); `selfSupplied` exists for the suites a port cannot
        //     edit because the engine emitted them.
        new balticporter.transform.GlobalsToImplicitsTransform(extensions = List(
          balticporter.transform.ContextHolderExtension(
            holder = "com.badlogic.gdx.Gdx",
            sites  = Map(
              "com.crashinvaders.vfx.framebuffer.VfxFrameBuffer#tmpCam" ->
                balticporter.transform.ContextSite.LazyInit,
            ),
          )
        )),
        // LAST, deliberately, for the reason AshleyPolicy states: this reads what the BASE actually
        // emitted and reports a reference the base does not ship, so it must run after any seam
        // that re-points such a reference, or it reports the very sites the next phase repairs. A
        // residue check, exactly like `PortabilityCheck`.
        balticporter.transform.PortMapTransform.forBases("sge"),
      ),
    ))
