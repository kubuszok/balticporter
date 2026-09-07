package balticporter.corpus.libgdx

import balticporter.corpus.JnigenClasspath
import balticporter.core.{FrontendConfig, PortManifest, Provenance, ResourceTree, RuntimeMode}
import balticporter.runner.{Determinism, PortRun, SourceSet, VendoredCommit}
import balticporter.corpus.lls.{LlsMigrate, LlsPolicy}

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** Rung L0 of the libGDX ladder (PROGRESS.md §13.29) ON THE LLS BASE: core minus the utilities family,
  * a DEPENDENT of `ported/lls` (CLAUDE.md §1.5), with no policy of its own — its compile count is the
  * honest measure of Java-as-Scala over the base's decisions, before any of core's own. */
object LibgdxL0Migrate:

  def main(args: Array[String]): Unit =
    val repoRoot = Path.of(sys.props.getOrElse("balticporter.root", ".")).toAbsolutePath.normalize
    val base     = repoRoot.resolve("../sge/original-src/libgdx/gdx/src").normalize
    val steps    = LibgdxLadder.stepsFrom(args)

    // the utilities family is the lls port's (PROGRESS.md §13.29): its files are the BASE's units,
    // resolved through `gdx/src` and never emitted twice.
    val files = Files.walk(base).iterator().asScala
      .filter(p => p.toString.endsWith(".java"))
      .map(p => base.relativize(p).toString)
      .filterNot(f => f.endsWith("package-info.java") || f.endsWith("module-info.java"))
      .filterNot(LlsMigrate.Files.toSet)
      .toList.sorted

    PortRun(
      label     = "sge-l0",
      portRoot  = repoRoot.resolve("ported/sge-l0"),
      sourceSet = SourceSet.Main,
      // gdx-jnigen-loader carries `SharedLibraryLoader`, which `gdx/src` references and no
      // longer declares; without it on the classpath Spoon resolves no declaration for it.
      frontend  = FrontendConfig(base, files, JnigenClasspath.entries(repoRoot), resolutionRoots = List(base)),
      phases    = Nil,
      manifest  = Some(LibgdxLadder.universal(repoRoot, steps)),
      provenance = Some(Provenance(
        upstreamName     = "libGDX",
        upstreamCommit   = VendoredCommit.of(base),
        originalLicense  = "Apache-2.0",
        sourcePathPrefix = "gdx/src",
        sourceRoot       = base.toString,
      )),
      runtimeMode = RuntimeMode.Vendored,
      determinism = Determinism.fromArgs(args.toSeq),
      nextStep    = "just gdx-l0-measure",
    ).execute()

object LibgdxLadder:

  /** JUnit's `@Rule TestWatcher` has no MUnit model; the field is dropped, as on the full port. */
  private val watcherDrop = "com.badlogic.gdx.utils.JsonMatcherTests#watcher"

  /** The test manifest: JUnit -> MUnit only (`TestFrameworkTransform`), inheriting `universal`;
    * `externalParenless` is P11 (munit's JS/Native `Description` is parenless). No sge policy. */
  def universalTest(repoRoot: Path, steps: Set[String] = DefaultSteps): PortManifest = universal(repoRoot, steps).extendedBy(PortManifest(
    name        = "sge-l0-test",
    dropMethods = Set(watcherDrop),
    // the test tree calls into sge-l0: it FOLLOWS what sge-l0 PUBLISHED (properties, parenless), D14.
    surface     = List(new balticporter.transform.TestFrameworkTransform(dropFields = Set(watcherDrop)),
                       balticporter.transform.PortMapTransform.forBases("sge-l0")) ++
      // CT7: `AnimationControllerTest` is constructed by MUnit, so the threaded context cannot reach
      // it as a parameter — it takes one from the hand-written fixture (`ported/sge-l0/src/test`).
      (if steps("context") then List(new balticporter.transform.GlobalsToImplicitsTransform(extensions = List(
        balticporter.transform.ContextHolderExtension(
          holder       = "com.badlogic.gdx.Gdx",
          selfSupplied = Map("com.badlogic.gdx.graphics.g3d.utils.AnimationControllerTest" -> "sge.SgeTestFixture.testSge()")))))
       else Nil),
    externalParenless = Set(
      "org.junit.runner.Description#getTestClass",
      "org.junit.runner.Description#getMethodName",
      "org.junit.runner.Description#getAnnotations",
    ),
  ))

  /** `--steps=a,b` on the command line; absent or empty = [[DefaultSteps]] (the steps landed so
    * far), `--steps=none` = the bare universal translation. */
  def stepsFrom(args: Array[String]): Set[String] =
    args.collectFirst { case a if a.startsWith("--steps=") => a.stripPrefix("--steps=").trim }
      .filter(_.nonEmpty) match
      case None         => DefaultSteps
      case Some("none") => Set.empty
      case Some(v)      => v.split(',').map(_.trim).filter(_.nonEmpty).toSet

  /** Core's declarations that allocate an array at their OWN type parameter, or construct a
    * `DynamicArray` at it, so they take the `MkArray` clause (PROGRESS.md §13.29, step "witness");
    * the null-as-empty tables (`IntMap`, `ObjectIntMap`, …) stay refused and counted (K41). */
  val CoreWitnessSubjects: Map[String, List[Int]] = Map(
    "com.badlogic.gdx.utils.SnapshotArray"                         -> List(0),
    "com.badlogic.gdx.utils.DelayedRemovalArray"                   -> List(0),
    "com.badlogic.gdx.utils.Queue"                                 -> List(0),
    "com.badlogic.gdx.graphics.g2d.Animation"                      -> List(0),
    "com.badlogic.gdx.graphics.g3d.particles.ParallelArray$ObjectChannel" -> List(0),
    "com.badlogic.gdx.math.Octree"                                 -> List(0),
    "com.badlogic.gdx.graphics.g3d.particles.batches.BufferedParticleBatch" -> List(0),
  )

  /** The step fragments, cumulative; each merges with the base's instance of the same phase at
    * the base's position (CLAUDE.md §1.5). */
  /** the GL statics' two-hop path: the property step renames the getter (`gl20`); before it, the call. */
  private def glPath(sel: Set[String], n: String): String =
    if sel("properties") then s"graphics.gl$n" else s"graphics.getGL$n()"
  /** sge's remaining GL enums (all in the injected `GLEnum.scala`): one opaque per family, seeded at
    * every GL parameter sge types with it, GL20 through GL32 (PROGRESS.md §13.30 step 1: sge's ANGLE
    * bindings implement THAT surface). A `def`: a phase instance carries binding state. */
  /** the derive step: an opaque spec's seeds and fence are the REFERENCE's alone (PROGRESS.md §13.31
    * step 1) — the hand-listed hints and the propagation fences were the pre-derivation device. */
  private def opaque(spec: balticporter.tir.OpaqueSpec)(using derive: Boolean): balticporter.tir.Phase =
    new balticporter.transform.PrimitiveToOpaqueTransform(
      if derive then spec.copy(hints = Set.empty, extraHints = Set.empty, scope = balticporter.tir.RuleScope.Everywhere(Set.empty), derive = true)
      else spec)
  private def glEnum(name: String, hints: String*)(using derive: Boolean): balticporter.tir.Phase = glEnumExcept(name, Set.empty, hints*)
  /** `except`: declarations the flow would reach that sge keeps `Int` (member or parameter names under `com.badlogic.gdx.graphics.`). */
  private def glEnumExcept(name: String, except: Set[String], hints: String*)(using derive: Boolean): balticporter.tir.Phase =
    opaque(balticporter.tir.OpaqueSpec(
      fqn        = "com.badlogic.gdx.graphics." + name,
      target     = balticporter.tir.OpaqueSpec.Target.Existing(typeFqn = "sge.graphics." + name, wrapName = "apply", unwrapName = "toInt"),
      hints      = hints.map("com.badlogic.gdx.graphics." + _).toSet,
      underlying = balticporter.tir.OpaqueSpec.Primitive.Int,
      scope      = balticporter.tir.RuleScope.Everywhere(except.map("com.badlogic.gdx.graphics." + _)),
      derive     = derive))
  private def moreGlEnums(using derive: Boolean): List[balticporter.tir.Phase] = List(
    glEnum("BufferTarget", "GL20#glBindBuffer#target", "GL20#glBufferData#target", "GL20#glBufferSubData#target", "GL20#glGetBufferParameteriv#target",
      "GL30#glUnmapBuffer#target", "GL30#glGetBufferPointerv#target", "GL30#glMapBufferRange#target", "GL30#glFlushMappedBufferRange#target",
      "GL30#glBindBufferRange#target", "GL30#glBindBufferBase#target", "GL30#glCopyBufferSubData#readTarget", "GL30#glCopyBufferSubData#writeTarget",
      "GL30#glGetBufferParameteri64v#target"),
    glEnum("BufferUsage", "GL20#glBufferData#usage"),
    glEnum("TextureTarget", "GL20#glBindTexture#target", "GL20#glCompressedTexImage2D#target", "GL20#glCompressedTexSubImage2D#target",
      "GL20#glCopyTexImage2D#target", "GL20#glCopyTexSubImage2D#target", "GL20#glTexImage2D#target", "GL20#glTexParameterf#target",
      "GL20#glTexSubImage2D#target", "GL20#glFramebufferTexture2D#textarget", "GL20#glGenerateMipmap#target", "GL20#glGetTexParameterfv#target",
      "GL20#glGetTexParameteriv#target", "GL20#glTexParameterfv#target", "GL20#glTexParameteri#target", "GL20#glTexParameteriv#target",
      "GL30#glTexImage2D#target", "GL30#glTexImage3D#target", "GL30#glTexSubImage2D#target", "GL30#glTexSubImage3D#target", "GL30#glCopyTexSubImage3D#target",
      "GL31#glTexStorage2DMultisample#target", "GL31#glGetTexLevelParameteriv#target", "GL31#glGetTexLevelParameterfv#target",
      "GL32#glTexParameterIiv#target", "GL32#glTexParameterIuiv#target", "GL32#glGetTexParameterIiv#target", "GL32#glGetTexParameterIuiv#target",
      "GL32#glTexBuffer#target", "GL32#glTexBufferRange#target", "GL32#glTexStorage3DMultisample#target"),
    glEnum("BlendFactor", "GL20#glBlendFunc#sfactor", "GL20#glBlendFunc#dfactor", "GL20#glBlendFuncSeparate#srcRGB", "GL20#glBlendFuncSeparate#dstRGB",
      "GL20#glBlendFuncSeparate#srcAlpha", "GL20#glBlendFuncSeparate#dstAlpha", "GL32#glBlendFunci#src", "GL32#glBlendFunci#dst",
      "GL32#glBlendFuncSeparatei#srcRGB", "GL32#glBlendFuncSeparatei#dstRGB", "GL32#glBlendFuncSeparatei#srcAlpha", "GL32#glBlendFuncSeparatei#dstAlpha"),
    glEnum("BlendEquation", "GL20#glBlendEquation#mode", "GL20#glBlendEquationSeparate#modeRGB", "GL20#glBlendEquationSeparate#modeAlpha",
      "GL32#glBlendEquationi#mode", "GL32#glBlendEquationSeparatei#modeRGB", "GL32#glBlendEquationSeparatei#modeAlpha"),
    glEnum("CullFace", "GL20#glCullFace#mode", "GL20#glStencilFuncSeparate#face", "GL20#glStencilMaskSeparate#face", "GL20#glStencilOpSeparate#face"),
    glEnum("ShaderType", "GL20#glCreateShader#type", "GL20#glGetShaderPrecisionFormat#shadertype", "GL31#glCreateShaderProgramv#type"),
    glEnum("StencilOp", "GL20#glStencilOp#fail", "GL20#glStencilOp#zfail", "GL20#glStencilOp#zpass",
      "GL20#glStencilOpSeparate#fail", "GL20#glStencilOpSeparate#zfail", "GL20#glStencilOpSeparate#zpass"),
    // sge keeps `internalformat` a plain Int (only `format` and `type` are typed): fence the flow at the GL sinks
    glEnumExcept("PixelFormat", Set("GL20#glTexImage2D#internalformat", "GL30#glTexImage2D#internalformat", "GL30#glTexImage3D#internalformat"),
      "GL20#glCompressedTexSubImage2D#format", "GL20#glReadPixels#format", "GL20#glTexImage2D#format", "GL20#glTexSubImage2D#format",
      "GL30#glTexImage2D#format", "GL30#glTexImage3D#format", "GL30#glTexSubImage2D#format", "GL30#glTexSubImage3D#format", "GL32#glReadnPixels#format"),
    glEnum("DataType", "GL20#glDrawElements#type", "GL20#glReadPixels#type", "GL20#glTexImage2D#type", "GL20#glTexSubImage2D#type", "GL20#glVertexAttribPointer#type",
      "GL30#glDrawRangeElements#type", "GL30#glTexImage2D#type", "GL30#glTexImage3D#type", "GL30#glTexSubImage2D#type", "GL30#glTexSubImage3D#type",
      "GL30#glVertexAttribIPointer#type", "GL30#glDrawElementsInstanced#type", "GL30#glVertexAttribPointer#type",
      "GL31#glDrawElementsIndirect#type", "GL31#glVertexAttribFormat#type", "GL31#glVertexAttribIFormat#type",
      "GL32#glDrawElementsBaseVertex#type", "GL32#glDrawRangeElementsBaseVertex#type", "GL32#glDrawElementsInstancedBaseVertex#type", "GL32#glReadnPixels#type"),
  )

  def Steps: Map[String, List[balticporter.tir.Phase]] = stepsFor(Set.empty)
  def stepsFor(sel: Set[String]): Map[String, List[balticporter.tir.Phase]] =
    given derive: Boolean = sel("derive")
    Map(
    // the reference-derived spelling step: no phase of its own — it switches `derive` on in the
    // opaque, nullability and arity phases and declares sge's tree as the manifest's reference.
    "derive" -> Nil,
    // java's `Gdx.app.log/error/debug(tag, msg[, t])` become sge's context-free `Log` (PROGRESS.md
    // §13.31 step 2): a class that only LOGS then takes no context (sge commented the particle
    // values' call out — a skip; the port keeps java's call). Placed BEFORE the context step.
    "logging" -> List(new balticporter.transform.CallSiteSubstitutionTransform(Map(
      "com.badlogic.gdx.Application#log(String,String)"             -> "sge.utils.Log.info({arg0} + \": \" + {arg1})",
      "com.badlogic.gdx.Application#log(String,String,Throwable)"   -> "sge.utils.Log.info({arg0} + \": \" + {arg1} + \"\\n\" + {arg2})",
      "com.badlogic.gdx.Application#error(String,String)"           -> "sge.utils.Log.error({arg0} + \": \" + {arg1})",
      "com.badlogic.gdx.Application#error(String,String,Throwable)" -> "sge.utils.Log.error({arg0} + \": \" + {arg1}, {arg2})",
      "com.badlogic.gdx.Application#debug(String,String)"           -> "sge.utils.Log.debug({arg0} + \": \" + {arg1})",
      "com.badlogic.gdx.Application#debug(String,String,Throwable)" -> "sge.utils.Log.debug({arg0} + \": \" + {arg1} + \"\\n\" + {arg2})"))),
    // sge's platform contract and its JVM implementations, copied (PROGRESS.md §13.30 step 1): no phase, injections only.
    "backend-jvm" -> Nil,
    // the 59 java `native` members answered on the JVM (PROGRESS.md §13.30 step 2): bodies from
    // `LibgdxNativeBodies`, the objects they call injected (`Gdx2DNative`, `BufferUtilsNative`, `ETC1Native`).
    "natives" -> List(new balticporter.transform.MethodBodyTransform(LibgdxNativeBodies.all)),
    // sge's desktop backend (GLFW window, input, files, preferences, net, miniaudio), copied (PROGRESS.md §13.30 step 3): injections only.
    "backend-desktop" -> Nil,
    // sge's typed JSON/UBJSON documents with Kindlings-derived codecs replace java's reflective JSON stack, one
    // consumer family at a time (PROGRESS.md §13.30, JSON step): first the g3d model loader.
    "json" -> Nil,
    "witness" -> List(
      new balticporter.transform.GlobalsToImplicitsTransform(requiredGivens =
        balticporter.transform.ElementWitnessTransform.constructorGivens(CoreWitnessSubjects, LlsPolicy.Witness)),
      new balticporter.transform.ElementWitnessTransform(
        witness      = LlsPolicy.Witness,
        subjectTypes = CoreWitnessSubjects,
        // the clause is threaded; java's implicit `<: Object` bound STAYS on core's subjects — their
        // collaborators (`ObjectSet[T]`) keep theirs (ENGINE-LIMITS.md K48).
        dropBound    = Set.empty,
        boxedWitness = Some("lowlevel.MkArray.anyRef[scala.AnyRef].asInstanceOf[lowlevel.MkArray[{elem}]]"))),
    // core's collections onto lls's and the JDK table, `Comparator -> Ordering`: the base's instance
    // widened to core's entry (merged `Only` scopes, CLAUDE.md §1.5 D12).
    "collections" -> List(
      new balticporter.transform.CollectionsTransform(
        scope    = balticporter.tir.RuleScope.Only(Set("com.badlogic.gdx")),
        retarget = Map("java.util.Comparator" -> "scala.math.Ordering"))),
    // `@Null -> lowlevel.Nullable` on core's entry: merges with lls's instance (`Only` union), so an
    // override of a base member the base retyped (`SnapshotArray.replaceFirst`, 2 `E120` name
    // clashes after erasure) moves with its component; ahead of `enrich`, whose value-map templates
    // are written against the nullable API.
    "nullability" -> List(new balticporter.transform.NullabilityTransform(
      annotations = Set("com.badlogic.gdx.utils.Null"),
      target      = balticporter.transform.NullabilityTransform.Target.Named("lowlevel.Nullable"),
      scope       = balticporter.tir.RuleScope.Only(Set("com.badlogic.gdx")),
      deriveMembers = sel("derive"))),
    // lls's added API on core's own collections, and the factories core's subclasses of lls's
    // types must declare themselves (`LibgdxEnrich`).
    "enrich" -> List(LibgdxEnrich.transform(w = true, n = sel("nullability"))),
    // no runtime reflection: the reflective `Json` and the `reflect` package go (types below), the
    // one class lookup by name becomes a table (`AssetTypeRegistry`, injected), and `ClassReflection`'s
    // statics are `java.lang.Class`'s own — the full port's policy, lifted (`LibgdxPolicy`).
    "net" -> Nil,
    // sge's renames that need no injection: `Disposable -> java.lang.AutoCloseable` (`dispose` ->
    // `close`, whole override component), and the two member renames the full port carries
    // (`InputEvent.type` -> `eventType`, `List.toString(T)` -> `itemToString`: java overloads
    // `Object.toString`, scala reads a clash).
    "renames" -> List(
      new balticporter.transform.TypeRedirectTransform(
        redirects     = Map("com.badlogic.gdx.utils.Disposable" -> "java.lang.AutoCloseable"),
        memberRenames = Map("com.badlogic.gdx.utils.Disposable" -> Map("dispose" -> "close"))),
      new balticporter.transform.MemberRenameTransform(renames = Map(
        "com.badlogic.gdx.scenes.scene2d.InputEvent#type"       -> "eventType",
        "com.badlogic.gdx.scenes.scene2d.ui.List#toString(T)"   -> "itemToString",
        // sge's vector spellings (`Vectors.scala`): the whole override component moves with `Vector`.
        "com.badlogic.gdx.math.Vector#len"  -> "length",   "com.badlogic.gdx.math.Vector#len2" -> "lengthSq",
        "com.badlogic.gdx.math.Vector#dst"  -> "distance", "com.badlogic.gdx.math.Vector#dst2" -> "distanceSq",
        "com.badlogic.gdx.math.Vector#scl"  -> "scale",    "com.badlogic.gdx.math.Vector#nor"  -> "normalize"))),
        // (the classes' own overloads — `dst(x, y)`, static `len(x, y)` — keep java's names: a second
        // key on the same component REFUSES the whole rename, 3 -> 23 policy rows; counted residue)

    // the implicit `Sge` context instead of the `Gdx` globals: the full port's holder policy lifted
    // verbatim (attach on the CLASS, read by `summon`, refuse at the boundary, two lazy statics);
    // the context type is the injected `sge.Sge`. Late by design: every constructor moves.
    "context" -> List(new balticporter.transform.GlobalsToImplicitsTransform(holders = List(
      balticporter.transform.ContextHolder(
        holder   = "com.badlogic.gdx.Gdx",
        context  = balticporter.transform.ContextType.Injected("sge.Sge"),
        members  = Map(
          "app" -> "application", "graphics" -> "graphics", "audio" -> "audio", "input" -> "input",
          "files" -> "files", "net" -> "net",
          // the GL statics, two hops through the service that owns them — as GETTER CALLS until the
          // property step renames them (`graphics.gl20` in the full port).
          "gl" -> glPath(sel, "20"), "gl20" -> glPath(sel, "20"), "gl30" -> glPath(sel, "30"),
          "gl31" -> glPath(sel, "31"), "gl32" -> glPath(sel, "32")),
        attach   = balticporter.transform.ContextAttach.Class,
        reader   = balticporter.transform.ContextReader.Summon,
        boundary = balticporter.transform.ContextBoundary.Refuse,
        // `Pixmap.dispose` is left OUT of the closure so the class takes no clause (sge's `Pixmap` is
        // context-free, `Pixmap(w, h, format)` in the demos; its statics still take one); the
        // `Gdx.app.error` inside stays a counted residual global read.
        scope    = balticporter.tir.RuleScope.Everywhere(Set("com.badlogic.gdx.graphics.Pixmap#dispose")),
        sites    = Map(
          "com.badlogic.gdx.scenes.scene2d.ui.TextField#DEFAULT_ONSCREEN_KEYBOARD" -> balticporter.transform.ContextSite.LazyInit,
          "com.badlogic.gdx.scenes.scene2d.ui.Table#cellPool" -> balticporter.transform.ContextSite.LazyInit))))),
    // `Seconds`: a frame delta is not a bare `Float` (sge's opaque type, injected from sge's own
    // file). Seeded at the two producers on `Graphics`; the phase propagates along pure moves
    // (`render(delta)`, `act(delta)`) and coerces at the boundary.
    "seconds" -> List(opaque(balticporter.tir.OpaqueSpec(
      fqn        = "com.badlogic.gdx.utils.Seconds",
      target     = balticporter.tir.OpaqueSpec.Target.Existing(
        typeFqn = "sge.utils.Seconds", wrapName = "apply", unwrapName = "toFloat"),
      derive     = sel("derive"),
      hints      = Set("com.badlogic.gdx.Graphics#getDeltaTime", "com.badlogic.gdx.Graphics#getRawDeltaTime"),
      underlying = balticporter.tir.OpaqueSpec.Primitive.Float,
      // core's own declarations, never the base's: a shared int utility (`MathUtils.isPowerOfTwo`)
      // is a HUB the symmetric propagation would otherwise ride into unrelated ints (a touch bitmask).
      scope      = balticporter.tir.RuleScope.Everywhere(Set.empty)))),
    // `Pool` as sge's TRAIT (injected from sge's own files, with `Pool.Default`, `Pool.Flushable`
    // and the `Poolable` type class the demos use): java's `Pool`/`DefaultPool`/`FlushablePool` go,
    // their references re-point, and a subclass's constructor arguments become the trait's
    // abstract vals (the full port's `ClassToTraitTransform` specs).
    "pool" -> List(
      new balticporter.transform.TypeRedirectTransform(
        // `FlushablePool` stays java's CLASS over the injected trait (as in the full port): a subclass
        // with several constructors passing different `super(...)` arguments cannot map onto one val.
        redirects = Map(
          "com.badlogic.gdx.utils.DefaultPool"               -> "sge.utils.Pool.Default",
          "com.badlogic.gdx.utils.DefaultPool$PoolSupplier"  -> "scala.Function0"), // `T get()` is `() => A`
        memberRenames = Map("com.badlogic.gdx.utils.DefaultPool$PoolSupplier" -> Map("get" -> "apply"))),
      new balticporter.transform.ClassToTraitTransform(specs = Map(
        "com.badlogic.gdx.utils.Pool" -> PoolMappings))),
    // `Pixels`: a screen coordinate or size is not a bare `Int` (sge's opaque type, injected).
    // Seeded at the producers on `Graphics` and `Input` and at the two resize callbacks; the
    // phase propagates along pure moves and coerces at the boundary.
    "pixels" -> List(opaque(balticporter.tir.OpaqueSpec(
      fqn        = "com.badlogic.gdx.Pixels",
      target     = balticporter.tir.OpaqueSpec.Target.Existing(
        typeFqn = "sge.Pixels", wrapName = "apply", unwrapName = "toInt"),
      derive     = sel("derive"),
      hints      = Set(
        "com.badlogic.gdx.Graphics#getWidth", "com.badlogic.gdx.Graphics#getHeight",
        "com.badlogic.gdx.Graphics#getBackBufferWidth", "com.badlogic.gdx.Graphics#getBackBufferHeight",
        "com.badlogic.gdx.Graphics#getSafeInsetLeft", "com.badlogic.gdx.Graphics#getSafeInsetTop",
        "com.badlogic.gdx.Graphics#getSafeInsetBottom", "com.badlogic.gdx.Graphics#getSafeInsetRight",
        "com.badlogic.gdx.Input#getX", "com.badlogic.gdx.Input#getY",
        "com.badlogic.gdx.Input#getDeltaX", "com.badlogic.gdx.Input#getDeltaY",
        // a PARAMETER seed is `owner#method#param`
        "com.badlogic.gdx.ApplicationListener#resize#width", "com.badlogic.gdx.ApplicationListener#resize#height",
        "com.badlogic.gdx.Screen#resize#width", "com.badlogic.gdx.Screen#resize#height")
        // sge types these GL20 parameters in `Pixels` (its ANGLE bindings implement that surface, §13.30)
        ++ Set(
        "glCompressedTexImage2D#width", "glCompressedTexImage2D#height",
        "glCompressedTexSubImage2D#xoffset", "glCompressedTexSubImage2D#yoffset", "glCompressedTexSubImage2D#width", "glCompressedTexSubImage2D#height",
        "glCopyTexImage2D#x", "glCopyTexImage2D#y", "glCopyTexImage2D#width", "glCopyTexImage2D#height",
        "glCopyTexSubImage2D#xoffset", "glCopyTexSubImage2D#yoffset", "glCopyTexSubImage2D#x", "glCopyTexSubImage2D#y", "glCopyTexSubImage2D#width", "glCopyTexSubImage2D#height",
        "glReadPixels#x", "glReadPixels#y", "glReadPixels#width", "glReadPixels#height",
        "glScissor#x", "glScissor#y", "glScissor#width", "glScissor#height",
        "glTexImage2D#width", "glTexImage2D#height",
        "glTexSubImage2D#xoffset", "glTexSubImage2D#yoffset", "glTexSubImage2D#width", "glTexSubImage2D#height",
        "glViewport#x", "glViewport#y", "glViewport#width", "glViewport#height",
        "glRenderbufferStorage#width", "glRenderbufferStorage#height",
        ).map("com.badlogic.gdx.graphics.GL20#" + _),
      underlying = balticporter.tir.OpaqueSpec.Primitive.Int,
      // sge sizes a `Pixmap` in plain `Int` (image dimensions are not screen pixels; the demos write
      // `Pixmap(w, h, format)`): the flow stops at its declarations, the call sites coerce.
      scope      = balticporter.tir.RuleScope.Everywhere(Set("com.badlogic.gdx.graphics.Pixmap",
        // sge types GL20's sizes in `Pixels` and keeps GL30's plain `Int`: the flow stops at these members, the calls coerce
        "com.badlogic.gdx.graphics.GL30#glTexImage2D", "com.badlogic.gdx.graphics.GL30#glTexImage3D",
        "com.badlogic.gdx.graphics.GL30#glTexSubImage2D", "com.badlogic.gdx.graphics.GL30#glTexSubImage3D",
        "com.badlogic.gdx.graphics.GL30#glCopyTexSubImage3D", "com.badlogic.gdx.graphics.GL30#glBlitFramebuffer",
        "com.badlogic.gdx.graphics.GL30#glRenderbufferStorageMultisample",
        // sge sizes ETC1 in plain Int throughout (its JNI-shaped statics answer through the contract, §13.30 step 2)
        "com.badlogic.gdx.graphics.glutils.ETC1",
        // sge sizes a NinePatch in plain Int (the flow had reached `left`/`right` and not `top`/`bottom`)
        "com.badlogic.gdx.graphics.g2d.NinePatch"))))),
    // sge's helper API the demos use: the `gl` alias, `rendering { … }` around `begin`/`end`,
    // class-tag `load` and a `Nullable` `get` on the asset manager.
    "helpers" -> List(
      new balticporter.transform.AddMembersTransform(Map(
        "com.badlogic.gdx.Graphics" -> List(
          balticporter.transform.AddMembersTransform.MemberSpec("gl", 0,
            "def gl: sge.graphics.GL20 = gl20",
            balticporter.tir.Reason.Configured("add-members", "com.badlogic.gdx.Graphics#gl"),
            Some("sge's `graphics.gl` alias of the GL20 property (PROGRESS.md §13.29)"), false)),
        "com.badlogic.gdx.graphics.g2d.Batch" -> List(
          balticporter.transform.AddMembersTransform.MemberSpec("rendering", 1,
            "inline def rendering[A](inline body: => A): A = {{ begin(); try body finally end() }}",
            balticporter.tir.Reason.Configured("add-members", "com.badlogic.gdx.graphics.g2d.Batch#rendering"),
            Some("sge's `rendering {{ … }}` around `begin()`/`end()` (PROGRESS.md §13.29)"), false)),
        "com.badlogic.gdx.graphics.g3d.ModelBatch" -> List(
          balticporter.transform.AddMembersTransform.MemberSpec("rendering", 2,
            "inline def rendering[A](cam: sge.graphics.Camera)(inline body: => A): A = {{ begin(cam); try body finally end() }}",
            balticporter.tir.Reason.Configured("add-members", "com.badlogic.gdx.graphics.g3d.ModelBatch#rendering"),
            Some("sge's `rendering(camera) {{ … }}` around `begin(cam)`/`end()` (PROGRESS.md §13.29)"), false)),
        // sge's `OrthogonalTiledMapRenderer(map, unitScale, batch, ownsBatch)`: java's three-argument
        // constructor with the ownership flag it fixes at `false` made explicit.
        "com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer" -> List(
          balticporter.transform.AddMembersTransform.MemberSpec("this", 4,
            "def this(map: sge.maps.tiled.TiledMap, unitScale: scala.Float, batch: sge.graphics.g2d.Batch, ownsBatch: scala.Boolean)(using sge.Sge) = { this(map, unitScale, batch); this.ownsBatch = ownsBatch }",
            balticporter.tir.Reason.Configured("add-members", "com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer#<init>"),
            Some("sge's four-argument constructor: the batch-ownership flag made explicit (PROGRESS.md §13.29)"), false)),
        // java's `T...` is emitted `Array[T]`; sge spells these four as repeated parameters and the
        // demos call them so — one overload each, until the varargs mechanism lands (PROGRESS.md §13.29).
        "com.badlogic.gdx.graphics.g3d.Material" -> List(
          balticporter.transform.AddMembersTransform.MemberSpec("this", 1,
            "def this(attributes: sge.graphics.g3d.Attribute*) = this(attributes.toArray)",
            balticporter.tir.Reason.Configured("add-members", "com.badlogic.gdx.graphics.g3d.Material#<init>"),
            Some("sge's repeated-parameter spelling of java's `T...` (the port emits `Array[T]`; PROGRESS.md §13.29 card 1)"), false),
          balticporter.transform.AddMembersTransform.MemberSpec("this", 2,
            "def this(id: java.lang.String, attributes: sge.graphics.g3d.Attribute*) = this(id, attributes.toArray)",
            balticporter.tir.Reason.Configured("add-members", "com.badlogic.gdx.graphics.g3d.Material#<init>"),
            Some("sge's repeated-parameter spelling of java's `T...` (the port emits `Array[T]`; PROGRESS.md §13.29 card 1)"), false)),
        "com.badlogic.gdx.graphics.Mesh" -> List(
          balticporter.transform.AddMembersTransform.MemberSpec("this", 3,
            "def this(isStatic: scala.Boolean, maxVertices: scala.Int, maxIndices: scala.Int)(attributes: sge.graphics.VertexAttribute*)(using sge.Sge) = this(isStatic, maxVertices, maxIndices, attributes.toArray)",
            balticporter.tir.Reason.Configured("add-members", "com.badlogic.gdx.graphics.Mesh#<init>"),
            Some("sge's repeated-parameter spelling of java's `T...` (the port emits `Array[T]`; PROGRESS.md §13.29 card 1)"), false)),
        "com.badlogic.gdx.math.Bezier" -> List(
          // a secondary constructor cannot unify the class's F-bounded `T` with its own inside a
          // self-invocation (scalac 3.8), so the repeated-points constructor is the companion's `apply`
          balticporter.transform.AddMembersTransform.MemberSpec("apply", 1,
            "def apply[T <: sge.math.Vector[T]](points: T*)(using lowlevel.MkArray[T]): sge.math.Bezier[T] = { val b = new sge.math.Bezier[T](); b.set(points*); b }",
            balticporter.tir.Reason.Configured("add-members", "com.badlogic.gdx.math.Bezier#apply"),
            Some("sge's repeated-parameter constructor, as the companion's `apply` (PROGRESS.md §13.29 card 1)"), true),
          balticporter.transform.AddMembersTransform.MemberSpec("set", 1,
            "def set(points: T*): Bezier[?] = { val d = new lowlevel.util.DynamicArray[T](); points.foreach(p => d.add(p)); set(d, 0, points.size) }",
            balticporter.tir.Reason.Configured("add-members", "com.badlogic.gdx.math.Bezier#set"),
            Some("sge's repeated-parameter spelling of java's `T...` (the port emits `Array[T]`; PROGRESS.md §13.29 card 1)"), false)),
        "com.badlogic.gdx.utils.TextFormatter" -> List(
          balticporter.transform.AddMembersTransform.MemberSpec("format", 2,
            "def format(pattern: java.lang.String, args: java.lang.Object*): java.lang.String = format(pattern, args.toArray)",
            balticporter.tir.Reason.Configured("add-members", "com.badlogic.gdx.utils.TextFormatter#format"),
            Some("sge's repeated-parameter spelling of java's `T...` (the port emits `Array[T]`; PROGRESS.md §13.29 card 1)"), false)),
        // sge nests the five argument-free resolvers in `FileHandleResolver`'s companion
        // (`AssetManager(FileHandleResolver.Internal())` in the demos); each is the java class under sge's name.
        "com.badlogic.gdx.assets.loaders.FileHandleResolver" -> List(
          balticporter.transform.AddMembersTransform.MemberSpec("Absolute", 0,
            "class Absolute(using sge.Sge) extends sge.assets.loaders.resolvers.AbsoluteFileHandleResolver",
            balticporter.tir.Reason.Configured("add-members", "com.badlogic.gdx.assets.loaders.FileHandleResolver#Absolute"),
            Some("sge nests the resolvers in the companion: `FileHandleResolver.Absolute()` (PROGRESS.md §13.29)"), true),
          balticporter.transform.AddMembersTransform.MemberSpec("Classpath", 0,
            "class Classpath(using sge.Sge) extends sge.assets.loaders.resolvers.ClasspathFileHandleResolver",
            balticporter.tir.Reason.Configured("add-members", "com.badlogic.gdx.assets.loaders.FileHandleResolver#Classpath"),
            Some("sge nests the resolvers in the companion: `FileHandleResolver.Classpath()` (PROGRESS.md §13.29)"), true),
          balticporter.transform.AddMembersTransform.MemberSpec("External", 0,
            "class External(using sge.Sge) extends sge.assets.loaders.resolvers.ExternalFileHandleResolver",
            balticporter.tir.Reason.Configured("add-members", "com.badlogic.gdx.assets.loaders.FileHandleResolver#External"),
            Some("sge nests the resolvers in the companion: `FileHandleResolver.External()` (PROGRESS.md §13.29)"), true),
          balticporter.transform.AddMembersTransform.MemberSpec("Internal", 0,
            "class Internal(using sge.Sge) extends sge.assets.loaders.resolvers.InternalFileHandleResolver",
            balticporter.tir.Reason.Configured("add-members", "com.badlogic.gdx.assets.loaders.FileHandleResolver#Internal"),
            Some("sge nests the resolvers in the companion: `FileHandleResolver.Internal()` (PROGRESS.md §13.29)"), true),
          balticporter.transform.AddMembersTransform.MemberSpec("Local", 0,
            "class Local(using sge.Sge) extends sge.assets.loaders.resolvers.LocalFileHandleResolver",
            balticporter.tir.Reason.Configured("add-members", "com.badlogic.gdx.assets.loaders.FileHandleResolver#Local"),
            Some("sge nests the resolvers in the companion: `FileHandleResolver.Local()` (PROGRESS.md §13.29)"), true)),
        "com.badlogic.gdx.assets.AssetManager" -> List(
          balticporter.transform.AddMembersTransform.MemberSpec("load", 1,
            "def load[T <: java.lang.Object](fileName: java.lang.String)(using ct: scala.reflect.ClassTag[T]): scala.Unit = load(fileName, ct.runtimeClass.asInstanceOf[java.lang.Class[T]])",
            balticporter.tir.Reason.Configured("add-members", "com.badlogic.gdx.assets.AssetManager#load"),
            Some("sge's class-tag `load[T](fileName)` (PROGRESS.md §13.29)"), false),
          balticporter.transform.AddMembersTransform.MemberSpec("load", 2,
            "def load[T <: java.lang.Object](fileName: java.lang.String, parameter: sge.assets.AssetLoaderParameters[T])(using ct: scala.reflect.ClassTag[T]): scala.Unit = load(fileName, ct.runtimeClass.asInstanceOf[java.lang.Class[T]], parameter)",
            balticporter.tir.Reason.Configured("add-members", "com.badlogic.gdx.assets.AssetManager#load"),
            Some("sge's class-tag `load[T](fileName, parameter)` (PROGRESS.md §13.29)"), false)))),
      // sge's `Screen` gives every lifecycle member but `render` an empty default (a screen overrides
      // what it needs — the demos implement `show`/`render`/`resize`/`hide`/`close` only).
      new balticporter.transform.MethodBodyTransform(Map(
        "com.badlogic.gdx.Screen#show"   -> "{}", "com.badlogic.gdx.Screen#resize" -> "{}",
        "com.badlogic.gdx.Screen#pause"  -> "{}", "com.badlogic.gdx.Screen#resume" -> "{}",
        "com.badlogic.gdx.Screen#hide"   -> "{}")),
      // `AssetManager.get` answers `Nullable` in sge (the demos write `.get`); java throws on a miss.
      new balticporter.transform.NullabilityTransform(
        annotations     = Set.empty,
        target          = balticporter.transform.NullabilityTransform.Target.Named("lowlevel.Nullable"),
        scope           = balticporter.tir.RuleScope.Only(Set("com.badlogic.gdx")),
        nullableMembers = Set("com.badlogic.gdx.assets.AssetManager#get",
          // parameters sge accepts as `Nullable` where java wrote no annotation (the demos pass `Nullable.empty`)
          "com.badlogic.gdx.graphics.g3d.ModelBatch#<init>#context", "com.badlogic.gdx.graphics.g3d.ModelBatch#<init>#shaderProvider",
          "com.badlogic.gdx.graphics.g3d.ModelBatch#<init>#sorter", "com.badlogic.gdx.utils.Clipboard#setContents#content",
          "com.badlogic.gdx.maps.tiled.TiledMapTileLayer#setCell#cell"))),
    // sge's audio opaques (`Volume`, `Pitch`, `Pan`, `SoundId`), each fenced to the files sge keeps it in.
    "audio" -> List(
      opaque(balticporter.tir.OpaqueSpec(
        fqn        = "com.badlogic.gdx.audio.Volume",
        target     = balticporter.tir.OpaqueSpec.Target.Existing(typeFqn = "sge.audio.Volume", wrapName = "unsafeMake", unwrapName = "toFloat"),
        hints      = Set(
        "com.badlogic.gdx.audio.Sound#play#volume",
        "com.badlogic.gdx.audio.Sound#loop#volume",
        "com.badlogic.gdx.audio.Sound#setVolume#volume",
        "com.badlogic.gdx.audio.Sound#setPan#volume",
        "com.badlogic.gdx.audio.Music#setVolume#volume",
        "com.badlogic.gdx.audio.Music#getVolume",
        "com.badlogic.gdx.audio.Music#setPan#volume",
        "com.badlogic.gdx.audio.AudioDevice#setVolume#volume"),
        underlying = balticporter.tir.OpaqueSpec.Primitive.Float,
        scope      = balticporter.tir.RuleScope.Only(Set(
        "com.badlogic.gdx.audio.Sound",
        "com.badlogic.gdx.audio.Music",
        "com.badlogic.gdx.audio.AudioDevice",
        "com.badlogic.gdx.Input")))),
      opaque(balticporter.tir.OpaqueSpec(
        fqn        = "com.badlogic.gdx.audio.Pitch",
        target     = balticporter.tir.OpaqueSpec.Target.Existing(typeFqn = "sge.audio.Pitch", wrapName = "unsafeMake", unwrapName = "toFloat"),
        hints      = Set(
        "com.badlogic.gdx.audio.Sound#play#pitch",
        "com.badlogic.gdx.audio.Sound#loop#pitch",
        "com.badlogic.gdx.audio.Sound#setPitch#pitch"),
        underlying = balticporter.tir.OpaqueSpec.Primitive.Float,
        scope      = balticporter.tir.RuleScope.Only(Set(
        "com.badlogic.gdx.audio.Sound")))),
      opaque(balticporter.tir.OpaqueSpec(
        fqn        = "com.badlogic.gdx.audio.Pan",
        target     = balticporter.tir.OpaqueSpec.Target.Existing(typeFqn = "sge.audio.Pan", wrapName = "unsafeMake", unwrapName = "toFloat"),
        hints      = Set(
        "com.badlogic.gdx.audio.Sound#play#pan",
        "com.badlogic.gdx.audio.Sound#loop#pan",
        "com.badlogic.gdx.audio.Sound#setPan#pan",
        "com.badlogic.gdx.audio.Music#setPan#pan"),
        underlying = balticporter.tir.OpaqueSpec.Primitive.Float,
        scope      = balticporter.tir.RuleScope.Only(Set(
        "com.badlogic.gdx.audio.Sound",
        "com.badlogic.gdx.audio.Music")))),
      opaque(balticporter.tir.OpaqueSpec(
        fqn        = "com.badlogic.gdx.audio.SoundId",
        target     = balticporter.tir.OpaqueSpec.Target.Existing(typeFqn = "sge.audio.SoundId", wrapName = "apply", unwrapName = "toLong"),
        hints      = Set(
        "com.badlogic.gdx.audio.Sound#play",
        "com.badlogic.gdx.audio.Sound#loop",
        "com.badlogic.gdx.audio.Sound#stop#soundId",
        "com.badlogic.gdx.audio.Sound#pause#soundId",
        "com.badlogic.gdx.audio.Sound#resume#soundId",
        "com.badlogic.gdx.audio.Sound#setLooping#soundId",
        "com.badlogic.gdx.audio.Sound#setPitch#soundId",
        "com.badlogic.gdx.audio.Sound#setVolume#soundId",
        "com.badlogic.gdx.audio.Sound#setPan#soundId"),
        underlying = balticporter.tir.OpaqueSpec.Primitive.Long,
        scope      = balticporter.tir.RuleScope.Only(Set(
        "com.badlogic.gdx.audio.Sound"))))),
    // sge's time opaques (`Millis`, `Nanos`) over `TimeUtils`, fenced to sge's files.
    "time" -> List(
      opaque(balticporter.tir.OpaqueSpec(
        fqn        = "com.badlogic.gdx.utils.Millis",
        target     = balticporter.tir.OpaqueSpec.Target.Existing(typeFqn = "sge.utils.Millis", wrapName = "apply", unwrapName = "toLong"),
        hints      = Set(
        "com.badlogic.gdx.utils.TimeUtils#millis",
        "com.badlogic.gdx.utils.TimeUtils#nanosToMillis",
        "com.badlogic.gdx.utils.TimeUtils#millisToNanos#millis",
        "com.badlogic.gdx.utils.TimeUtils#timeSinceMillis",
        "com.badlogic.gdx.utils.TimeUtils#timeSinceMillis#prevTime"),
        underlying = balticporter.tir.OpaqueSpec.Primitive.Long,
        scope      = balticporter.tir.RuleScope.Only(Set(
        "com.badlogic.gdx.utils.TimeUtils",
        "com.badlogic.gdx.maps.tiled.tiles.AnimatedTiledMapTile",
        "com.badlogic.gdx.scenes.scene2d.utils.ClickListener",
        "com.badlogic.gdx.assets.AssetManager")))),
      opaque(balticporter.tir.OpaqueSpec(
        fqn        = "com.badlogic.gdx.utils.Nanos",
        target     = balticporter.tir.OpaqueSpec.Target.Existing(typeFqn = "sge.utils.Nanos", wrapName = "apply", unwrapName = "toLong"),
        hints      = Set(
        "com.badlogic.gdx.utils.TimeUtils#nanoTime",
        "com.badlogic.gdx.utils.TimeUtils#millisToNanos",
        "com.badlogic.gdx.utils.TimeUtils#nanosToMillis#nanos",
        "com.badlogic.gdx.utils.TimeUtils#timeSinceNanos",
        "com.badlogic.gdx.utils.TimeUtils#timeSinceNanos#prevTime"),
        underlying = balticporter.tir.OpaqueSpec.Primitive.Long,
        scope      = balticporter.tir.RuleScope.Only(Set(
        "com.badlogic.gdx.utils.TimeUtils",
        "com.badlogic.gdx.scenes.scene2d.utils.ClickListener",
        "com.badlogic.gdx.input.GestureDetector",
        "com.badlogic.gdx.input.RemoteInput",
        "com.badlogic.gdx.utils.PerformanceCounters",
        "com.badlogic.gdx.utils.PerformanceCounter",
        "com.badlogic.gdx.InputEventQueue",
        "com.badlogic.gdx.Input",
        "com.badlogic.gdx.graphics.FPSLogger",
        "com.badlogic.gdx.assets.AssetLoadingTask"))))),
    // sge's typed GL enums (`GLEnum.scala`, injected) at the GL20 parameters the demos reach; the raw
    // `GL_*` constants stay java's `inline val`s (a constant is never a seed, K51 xv) and wrap at the call.
    "glenum" -> (List(
      opaque(balticporter.tir.OpaqueSpec(
        fqn        = "com.badlogic.gdx.graphics.EnableCap",
        target     = balticporter.tir.OpaqueSpec.Target.Existing(typeFqn = "sge.graphics.EnableCap", wrapName = "apply", unwrapName = "toInt"),
        hints      = Set("com.badlogic.gdx.graphics.GL20#glEnable#cap", "com.badlogic.gdx.graphics.GL20#glDisable#cap", "com.badlogic.gdx.graphics.GL20#glIsEnabled#cap"),
        underlying = balticporter.tir.OpaqueSpec.Primitive.Int,
        scope      = balticporter.tir.RuleScope.Everywhere(Set.empty),
        derive     = derive)),
      opaque(balticporter.tir.OpaqueSpec(
        fqn        = "com.badlogic.gdx.graphics.PrimitiveMode",
        target     = balticporter.tir.OpaqueSpec.Target.Existing(typeFqn = "sge.graphics.PrimitiveMode", wrapName = "apply", unwrapName = "toInt"),
        hints      = Set("GL20#glDrawArrays#mode", "GL20#glDrawElements#mode", "GL30#glDrawRangeElements#mode", "GL30#glBeginTransformFeedback#primitiveMode",
          "GL30#glDrawArraysInstanced#mode", "GL30#glDrawElementsInstanced#mode", "GL31#glDrawArraysIndirect#mode", "GL31#glDrawElementsIndirect#mode",
          "GL32#glDrawElementsBaseVertex#mode", "GL32#glDrawRangeElementsBaseVertex#mode", "GL32#glDrawElementsInstancedBaseVertex#mode").map("com.badlogic.gdx.graphics." + _),
        underlying = balticporter.tir.OpaqueSpec.Primitive.Int,
        scope      = balticporter.tir.RuleScope.Everywhere(Set.empty),
        derive     = derive)),
      opaque(balticporter.tir.OpaqueSpec(
        fqn        = "com.badlogic.gdx.graphics.CompareFunc",
        target     = balticporter.tir.OpaqueSpec.Target.Existing(typeFqn = "sge.graphics.CompareFunc", wrapName = "apply", unwrapName = "toInt"),
        hints      = Set("com.badlogic.gdx.graphics.GL20#glDepthFunc#func", "com.badlogic.gdx.graphics.GL20#glStencilFunc#func", "com.badlogic.gdx.graphics.GL20#glStencilFuncSeparate#func"),
        underlying = balticporter.tir.OpaqueSpec.Primitive.Int,
        scope      = balticporter.tir.RuleScope.Everywhere(Set.empty),
        derive     = derive)),
      opaque(balticporter.tir.OpaqueSpec(
        fqn        = "com.badlogic.gdx.graphics.ClearMask",
        target     = balticporter.tir.OpaqueSpec.Target.Existing(typeFqn = "sge.graphics.ClearMask", wrapName = "apply", unwrapName = "toInt"),
        hints      = Set("com.badlogic.gdx.graphics.GL20#glClear#mask", "com.badlogic.gdx.graphics.GL30#glBlitFramebuffer#mask"),
        underlying = balticporter.tir.OpaqueSpec.Primitive.Int,
        scope      = balticporter.tir.RuleScope.Everywhere(Set.empty),
        derive     = derive))) ++ moreGlEnums),
    // `WorldUnits`: world-space sizes are not bare `Float`s (sge's opaque type, injected from sge's
    // own file). Seeded at the viewport's and the camera's world-size fields.
    "worldunits" -> List(opaque(balticporter.tir.OpaqueSpec(
      fqn        = "com.badlogic.gdx.WorldUnits",
      target     = balticporter.tir.OpaqueSpec.Target.Existing(
        typeFqn = "sge.WorldUnits", wrapName = "apply", unwrapName = "toFloat"),
      derive     = sel("derive"),
      hints      = Set(
        "com.badlogic.gdx.utils.viewport.Viewport#worldWidth", "com.badlogic.gdx.utils.viewport.Viewport#worldHeight",
        "com.badlogic.gdx.graphics.Camera#viewportWidth", "com.badlogic.gdx.graphics.Camera#viewportHeight"),
      underlying = balticporter.tir.OpaqueSpec.Primitive.Float,
      // sge keeps world units INSIDE the viewports and the cameras (13 files; `Stage`, the shadow
      // light and the math library wrap at the call): the flow is fenced there, the call sites
      // coerce — unfenced it reached `Sprite.scaleX` and `Batch.draw`, 237 then 85 errors.
      scope      = balticporter.tir.RuleScope.Only(Set(
        "com.badlogic.gdx.utils.viewport", "com.badlogic.gdx.graphics.Camera",
        "com.badlogic.gdx.graphics.OrthographicCamera", "com.badlogic.gdx.graphics.PerspectiveCamera"))))),
    // properties and parenless getters: the full port's bean pairs and targets (`LibgdxPolicy`),
    // lifted by reference, on core's entry; the `Only` scope merges with lls's arity instance.
    "properties" -> List(
      // sge's own setter decisions the demos rely on (`game.screen = …`, `batch.projectionMatrix = …`):
      // configured pairs, which the behaviour-setter guard does not apply to (`Cell.setTile` is FLUENT:
      // a configured pair collapses it, the property's setter returns Unit, the chain is counted, K51 xix).
      new balticporter.transform.BeanPropertyTransform(LibgdxPolicy.beanPropertyPairs ++ Map(
          "com.badlogic.gdx.Game#screen"                            -> "getScreen/setScreen",
          "com.badlogic.gdx.graphics.g2d.Batch#projectionMatrix"    -> "getProjectionMatrix/setProjectionMatrix",
          "com.badlogic.gdx.maps.tiled.TiledMapTileLayer$Cell#tile" -> "getTile/setTile"),
        LibgdxPolicy.beanPropertyTargets,
        scope = balticporter.tir.RuleScope.Only(Set("com.badlogic.gdx"))),
      new balticporter.transform.NullaryArityTransform(scope = balticporter.tir.RuleScope.Only(Set("com.badlogic.gdx")),
        // sge's `clip.hasContents` — parenless although the body reads the platform clipboard
        force = Set("com.badlogic.gdx.utils.Clipboard#hasContents"),
        derive = sel("derive"))),
    // sge's graphics API spellings the demos use: `ShapeRenderer.rect -> rectangle` (its four
    // overloads, one component) and the `drawing(type) { … }` helper around `begin`/`end`.
    "graphics" -> List(
      new balticporter.transform.MemberRenameTransform(renames = Map(
        "com.badlogic.gdx.graphics.glutils.ShapeRenderer#rect" -> "rectangle")),
      new balticporter.transform.AddMembersTransform(Map(
        "com.badlogic.gdx.graphics.glutils.ShapeRenderer" -> List(
          balticporter.transform.AddMembersTransform.MemberSpec("drawing", 2,
            "inline def drawing[A](shapeType: ShapeRenderer.ShapeType)(inline body: => A): A = { begin(shapeType); try body finally end() }",
            balticporter.tir.Reason.Configured("add-members", "com.badlogic.gdx.graphics.glutils.ShapeRenderer#drawing"),
            Some("sge's `drawing(type) { … }` around `begin`/`end`, `end` guaranteed (PROGRESS.md §13.29)"), false),
          balticporter.transform.AddMembersTransform.MemberSpec("drawing", 1,
            "inline def drawing[A](inline body: => A): A = { begin(); try body finally end() }",
            balticporter.tir.Reason.Configured("add-members", "com.badlogic.gdx.graphics.glutils.ShapeRenderer#drawing"),
            Some("sge's `drawing { … }` around `begin()`/`end` (auto shape type) (PROGRESS.md §13.29)"), false))))),
    "reflection" -> List(
      new balticporter.transform.ClassTableTransform(Map(
        "com.badlogic.gdx.utils.reflect.ClassReflection#forName" ->
          "com.badlogic.gdx.graphics.g3d.particles.AssetTypeRegistry#classFor")),
      new balticporter.transform.StaticForwarderTransform(List(
        balticporter.transform.StaticForwarderTransform.Forwarder(
          wrapper  = "com.badlogic.gdx.utils.reflect.ClassReflection",
          receiver = "java.lang.Class",
          members  = Set("getSimpleName", "isInstance", "isAssignableFrom", "isArray",
                         "isEnum", "isInterface", "isPrimitive", "isAnnotation", "getComponentType"))))),
  )

  /** `Pool(int initialCapacity, int max)` onto sge's trait: the two vals a subclass site overrides;
    * a site passing no argument keeps the trait's defaults (java's, carried by the injected file). */
  val PoolMappings: List[balticporter.transform.ClassToTraitTransform.ParamMapping] = List(
    balticporter.transform.ClassToTraitTransform.ParamMapping(0, "initialCapacity"),
    balticporter.transform.ClassToTraitTransform.ParamMapping(1, "max"))

  /** per step, the TYPES it removes (each replaced by an injection or made dead by the step). */
  val stepTypeDrops: Map[String, Set[String]] = Map(
    "json" -> Set("com.badlogic.gdx.graphics.g3d.loader.G3dModelLoader", "com.badlogic.gdx.scenes.scene2d.ui.Skin"),
    "pool" -> Set("com.badlogic.gdx.utils.Pool", "com.badlogic.gdx.utils.DefaultPool"),
    // the JVM-only `HttpURLConnection` client: nothing in core references it; the backends supply
    // their own `Net` (sge's capability convention, PROGRESS.md §13.29 R9).
    "net" -> Set("com.badlogic.gdx.net.NetJavaImpl"),
    "reflection" -> Set(
      "com.badlogic.gdx.utils.Json",
      // the `Class`-keyed static pool registry minted `ReflectionPool`s and registers, at class
      // initialisation, constructors that take the context; no core reader; sge has no `Pools`.
      "com.badlogic.gdx.utils.Pools",
      "com.badlogic.gdx.utils.ReflectionPool",
      "com.badlogic.gdx.utils.reflect.Annotation",
      "com.badlogic.gdx.utils.reflect.Field",
      "com.badlogic.gdx.utils.reflect.ArrayReflection",
      "com.badlogic.gdx.utils.reflect.ClassReflection",
      "com.badlogic.gdx.utils.reflect.Constructor",
      "com.badlogic.gdx.utils.reflect.Method",
      "com.badlogic.gdx.utils.reflect.ReflectionException",
    ),
  ).withDefaultValue(Set.empty)

  /** per step, the hand-written injections (standing order 4): `ladder-overrides/` holds the
    * reflection-free `Json`, `ReflectionException` and the asset-type registry. */
  def stepInjects(repoRoot: Path): Map[String, List[Path]] = Map(
    "reflection" -> List(repoRoot.resolve("balticporter/corpus/ladder-overrides")),
    "context"    -> List(repoRoot.resolve("balticporter/corpus/ladder-overrides-context")),
    "seconds"    -> List(repoRoot.resolve("balticporter/corpus/ladder-overrides-seconds")),
    "pool"       -> List(repoRoot.resolve("balticporter/corpus/ladder-overrides-pool")),
    "pixels"     -> List(repoRoot.resolve("balticporter/corpus/ladder-overrides-pixels")),
    "worldunits" -> List(repoRoot.resolve("balticporter/corpus/ladder-overrides-worldunits")),
    "audio"      -> List(repoRoot.resolve("balticporter/corpus/ladder-overrides-audio")),
    "time"       -> List(repoRoot.resolve("balticporter/corpus/ladder-overrides-time")),
    "glenum"     -> List(repoRoot.resolve("balticporter/corpus/ladder-overrides-glenum")),
    // the backend steps' SHARED half (sge's `scala/` files); their JVM half is a platform row below
    "backend-jvm" -> List(repoRoot.resolve("balticporter/corpus/ladder-overrides-backend-jvm/shared")),
    "natives"     -> List(repoRoot.resolve("balticporter/corpus/ladder-overrides-natives/shared")),
    "backend-desktop" -> List(repoRoot.resolve("balticporter/corpus/ladder-overrides-backend-desktop/shared")),
    "json"        -> List(repoRoot.resolve("balticporter/corpus/ladder-overrides-json")),
  ).withDefaultValue(Nil)

  /** Per step, the PLATFORM ROWS' injections (`PortManifest.platformDirs`, PROGRESS.md §13.31 step 3):
    * sge's `scalajvm`/`scaladesktop` layers and the port's own JVM-only files go to the `jvm` row
    * (`src_managed/jvm/scala`), which only that row compiles. */
  def stepPlatformInjects(repoRoot: Path): Map[String, Map[String, List[Path]]] = Map(
    "backend-jvm"     -> Map("jvm" -> List(repoRoot.resolve("balticporter/corpus/ladder-overrides-backend-jvm/jvm"))),
    "natives"         -> Map("jvm" -> List(repoRoot.resolve("balticporter/corpus/ladder-overrides-natives/jvm"))),
    "backend-desktop" -> Map("jvm" -> List(repoRoot.resolve("balticporter/corpus/ladder-overrides-backend-desktop/jvm"))),
  ).withDefaultValue(Map.empty)

  /** Per step, the members the step makes dead: the reflective `Class`-typed constructors the
    * witness replaces (each has a portable twin; the full port dropped the same, `LibgdxPolicy`). */
  val stepDrops: Map[String, Set[String]] = Map(
    "witness" -> Set(
      "com.badlogic.gdx.utils.SnapshotArray#<init>(boolean,int,Class)",
      "com.badlogic.gdx.utils.SnapshotArray#<init>(Class)",
      "com.badlogic.gdx.utils.DelayedRemovalArray#<init>(boolean,int,Class)",
      "com.badlogic.gdx.utils.DelayedRemovalArray#<init>(Class)",
      "com.badlogic.gdx.utils.Queue#<init>(int,Class)",
      "com.badlogic.gdx.graphics.g3d.particles.batches.BufferedParticleBatch#<init>(Class)",
    ),
    "reflection" -> Set(
      "com.badlogic.gdx.scenes.scene2d.ui.Skin#setEnabledReflection",
      "com.badlogic.gdx.scenes.scene2d.ui.Skin#findMethod",
      "com.badlogic.gdx.graphics.g3d.particles.ParallelArray$ChannelDescriptor#<init>(int,Class,int)",
    ),
  ).withDefaultValue(Set.empty)

  val StepOrder: List[String] = List("logging", "witness", "collections", "nullability", "enrich", "reflection", "net", "renames", "context", "seconds", "pool", "pixels", "worldunits", "properties", "graphics", "helpers", "audio", "time", "glenum", "backend-jvm", "natives", "backend-desktop", "json", "derive")
  /** the steps LANDED so far (measured, baselined, PROGRESS.md §13.29). */
  val DefaultSteps: Set[String] = Set("witness", "collections", "nullability", "enrich", "reflection", "net", "renames", "logging", "context", "seconds", "pool", "pixels", "graphics", "properties", "worldunits", "helpers", "audio", "time", "glenum", "backend-jvm", "natives", "backend-desktop", "json", "derive")

  /** L0's manifest: a dependent of the lls port carrying the universal facts only. `packageRenames`
    * for the rest of core (the base's `utils`/`math -> lowlevel.*` are inherited, longest prefix
    * wins); the `List` rename keeps `scala.List` out; `MutableParamsTransform` is inherited from the
    * base. No drop, inject, resolutions or parity (PROGRESS.md §13.29). */
  def universal(repoRoot: Path, steps: Set[String] = DefaultSteps): PortManifest =
    val unknown = steps -- Steps.keySet
    require(unknown.isEmpty, s"unknown ladder steps: ${unknown.mkString(",")}; known: ${Steps.keySet.toList.sorted.mkString(",")}")
    LlsPolicy.core(repoRoot, LlsPolicy.DefaultRungs).extendedBy(PortManifest(
      name           = "sge-l0",
      governs        = Set("com.badlogic.gdx"),
      dropTypes      = StepOrder.filter(steps).flatMap(stepTypeDrops).toSet,
      dropMethods    = StepOrder.filter(steps).flatMap(stepDrops).toSet,
      // sge ships `TextFormatter` public (java: package-private): declared, the split publishes it (K51 xviii).
      allowPackageSplit = if steps("helpers") then Set("com.badlogic.gdx.utils.TextFormatter") else Set.empty,
      inject         = StepOrder.filter(steps).flatMap(stepInjects(repoRoot)),
      platformDirs   = StepOrder.filter(steps).flatMap(stepPlatformInjects(repoRoot)(_).toList)
                         .groupMapReduce(_._1)(_._2)(_ ++ _),
      // a dependent FOLLOWS the base's published member spellings (`first()` -> `first`, D14): the
      // port-map follow reads what lls PUBLISHED, never re-derives it (CLAUDE.md §1.5).
      surface        = StepOrder.filter(steps).flatMap(stepsFor(steps)(_)) :+
                       balticporter.transform.PortMapTransform.forBases("lls"),
      packageRenames = Map("com.badlogic.gdx" -> "sge"),
      // java's reflective `Json` (dropped by the reflection step, a refusing stub injected) keeps the name
      // `LegacyJson`: `Json` is the Kindlings JSON AST sge's Skin and Tiled loaders read (json step).
      typeRenames    = Map("com.badlogic.gdx.scenes.scene2d.ui.List" -> "SgeList", "com.badlogic.gdx.utils.Json" -> "LegacyJson"),
      resources      = List(ResourceTree(
        root  = repoRoot.resolve("../sge/original-src/libgdx/gdx/res").normalize,
        files = List(
          "com/badlogic/gdx/utils/lsans-15.fnt", "com/badlogic/gdx/utils/lsans-15.png",
          "com/badlogic/gdx/graphics/g3d/shaders/default.vertex.glsl",
          "com/badlogic/gdx/graphics/g3d/shaders/default.fragment.glsl",
          "com/badlogic/gdx/graphics/g3d/shaders/depth.vertex.glsl",
          "com/badlogic/gdx/graphics/g3d/shaders/depth.fragment.glsl"))),
      dependencies   = List(balticporter.catalog.ArtifactDep("com.badlogicgames.gdx", "gdx-jnigen-loader", "2.5.2",
                                                             balticporter.catalog.CrossKind.Java)),
      // the derive step's reference: sge core's own tree (the three source rows the JVM build sees);
      // spelling is DERIVED from it, the comparison report stays off until the surface is close.
      parity         = if steps("derive") then Some(balticporter.core.ParityRef(
        roots   = List("scala", "scalajvm", "scaladesktop").map(d => repoRoot.resolve(s"../sge/sge/src/main/$d").normalize),
        compare = false)) else None,
    ))

/** The ladder port's TEST source set: libGDX's own `gdx/test` tree converted to MUnit on the
  * universal translation, a dependent of `sge-l0` (+ `lls`) — the suite is the step gate the
  * standing orders require (PROGRESS.md §13.29); one exclusion list, empty at L0. */
object LibgdxL0TestMigrate:

  def main(args: Array[String]): Unit =
    val repoRoot = Path.of(sys.props.getOrElse("balticporter.root", ".")).toAbsolutePath.normalize
    val srcRoot  = repoRoot.resolve("../sge/original-src/libgdx/gdx/src").normalize
    val testRoot = repoRoot.resolve("../sge/original-src/libgdx/gdx/test").normalize
    val steps    = LibgdxLadder.stepsFrom(args)

    // the one exclusion (1 java test): JUnit's `Parameterized` runner declares
    // `Collection<Object[]> parameters()` and fills it from `new ArrayList<>()` — the collections
    // step's `Collection`/`ArrayList` seam, uncoerced under the merged entry scope (K2 in a TEST tree;
    // the full port coerces it). A named delta, not an edited assertion (standing order 1).
    val excludedFiles = Set("com/badlogic/gdx/math/BezierTest.java")

    val files = Files.walk(testRoot).iterator().asScala
      .filter(p => p.toString.endsWith(".java"))
      .map(p => testRoot.relativize(p).toString)
      .filterNot(f => f.endsWith("package-info.java") || f.endsWith("module-info.java"))
      .filterNot(excludedFiles)
      .toList.sorted

    PortRun(
      label     = "sge-l0-test",
      portRoot  = repoRoot.resolve("ported/sge-l0"),
      sourceSet = SourceSet.Test,
      // NO frontend classpath, as `LibgdxTestMigrate`: with a jar present Spoon leaves the JUnit
      // static imports (`assertTrue`, …) attributed to the suite itself and `TestFrameworkTransform`
      // sees no `org.junit.Assert` call to convert — 161 `E008` on the first test compile.
      frontend  = FrontendConfig(testRoot, files, Nil, resolutionRoots = List(srcRoot)),
      phases    = Nil,
      manifest  = Some(LibgdxLadder.universalTest(repoRoot, steps)),
      provenance = Some(Provenance(
        upstreamName     = "libGDX",
        upstreamCommit   = VendoredCommit.of(testRoot),
        originalLicense  = "Apache-2.0",
        sourcePathPrefix = "gdx/test",
        sourceRoot       = testRoot.toString,
      )),
      runtimeMode = RuntimeMode.Dependency,
      determinism = Determinism.fromArgs(args.toSeq),
      nextStep    = "just gdx-l0-test-measure",
    ).execute()
