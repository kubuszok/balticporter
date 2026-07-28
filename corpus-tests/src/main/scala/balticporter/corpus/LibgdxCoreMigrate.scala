package balticporter.corpus

import balticporter.core.{FrontendConfig, Substituted, Substitutions}
import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.{OmissionCheck, Pipeline, PortabilityCheck, Program, RewriteTrace}
import balticporter.transform.{ClassTableTransform, CollectionsTransform, MutableParamsTransform, PanamaFfiTransform, StaticForwarderTransform}

import java.nio.file.{Files, Path, StandardCopyOption}
import scala.jdk.CollectionConverters.*

/** Migrate libGDX's CORE module (`gdx/src`, 605 types — the backend-agnostic, JDK-only heart
  * of libGDX) through the TIR to the `libgdx-core` sbt submodule, then compile it with
  * `sbt libgdx-core/compile`. This is the M6-scale target: a real, whole library recompiled.
  *
  *   corpus-tests/runMain balticporter.corpus.LibgdxCoreMigrate [--raw]
  *
  * `--raw` skips the transform pipeline (libGDX core uses its own collections, so the java
  * collections transform barely applies here; the port is essentially structural).
  */
object LibgdxCoreMigrate:

  def main(args: Array[String]): Unit =
    val raw      = args.contains("--raw")
    val repoRoot = Path.of(sys.props.getOrElse("balticporter.root", ".")).toAbsolutePath.normalize
    val base     = repoRoot.resolve("../sge/original-src/libgdx/gdx/src").normalize
    // Typed substitution manifest: constructs sge dropped upstream (and their
    // ready-made Scala replacements). `SharedLibraryLoader`/`Os` were removed in
    // favour of a dedicated native-extraction library; the corpus still references
    // them, so we inject standalone Scala at those FQNs (see corpus-tests/libgdx-
    // overrides). dropTypes/dropMethods are the seams for opting an in-source type
    // or method out of mechanical translation when a replacement is supplied here.
    val overridesRoot = repoRoot.resolve("corpus-tests/libgdx-overrides")
    val subs = Substitutions(
      // `utils.reflect` is libGDX's thin cross-platform wrapper over `java.lang.reflect`. sge does
      // not port it — the reflection-driven decoding it served was replaced by Kindlings'
      // Jsoniter/UBJson codecs — so it is substituted wholesale by injected Scala at the same FQNs.
      dropTypes = Set(
        // the reflection-based serializer itself — replaced by Kindlings Jsoniter/UBJson codecs.
        // (JsonValue/JsonReader/JsonWriter/JsonMatcher are DOM/parsing types and port fine.)
        "com.badlogic.gdx.utils.Json",
        // `Pools` fabricated a pool from a `Class` via `ReflectionPool` (reflective no-arg ctor
        // lookup + invoke) — the one thing Scala.js/Native cannot do. Replaced by an injected
        // `Pools` whose creation path takes a factory; `ReflectionPool` is dropped outright
        // (upstream deprecated it for the factory-backed `DefaultPool`, which ports mechanically).
        "com.badlogic.gdx.utils.Pools",
        // dropped with NO replacement — every reference eliminated, so CHECK 2 proves they are gone
        "com.badlogic.gdx.utils.ReflectionPool",
        "com.badlogic.gdx.utils.reflect.Annotation",
        "com.badlogic.gdx.utils.reflect.Field",
        "com.badlogic.gdx.utils.reflect.ArrayReflection",
        "com.badlogic.gdx.utils.reflect.ClassReflection",
        "com.badlogic.gdx.utils.reflect.Constructor",
        "com.badlogic.gdx.utils.reflect.Method",
        "com.badlogic.gdx.utils.reflect.ReflectionException",
        // `NetJavaImpl` implements `Net`'s HTTP half over `java.net.HttpURLConnection` — a type
        // Scala.js and Scala Native do not have, so NO member of it survives to either target and
        // there is nothing to port mechanically. It is a BACKEND helper: nothing in `gdx/src`
        // references it (the desktop/android backends do), so like `ReflectionPool` it is dropped
        // with no replacement and CHECK 2 proves the references are gone. The portable `Net`
        // interface, `HttpRequestBuilder`, `HttpStatus` and `HttpParametersUtils` all stay.
        //
        // NB — dropping it CONCEALS a real engine gap, recorded in LIBGDX-PORT-STATUS.md: the one
        // compile error it produced was `CollectionsTransform` rewriting OUR signature to
        // `mutable.Map[String, Buffer[String]]` while the body returned an unported JDK method's
        // real `java.util.Map`. That JDK/Scala collection boundary is universal and still open;
        // this drop is justified by portability alone and must not be read as closing it.
        "com.badlogic.gdx.net.NetJavaImpl",
      ),
      // libGDX itself deprecated `setEnabledReflection` (superseded by the typed
      // `setEnabled(Styleable, Boolean)`, already ported); its private `findMethod` helper was the
      // only reflective method scan left. Dropping both removes the last use of `reflect.Method`.
      dropMethods = Set(
        "com.badlogic.gdx.scenes.scene2d.ui.Skin#setEnabledReflection",
        "com.badlogic.gdx.scenes.scene2d.ui.Skin#findMethod",
        // `ArrayReflection` — `java.lang.reflect.Array`, which neither Scala.js nor Native has.
        // Every remaining use of it sits inside a member libGDX ITSELF deprecated in favour of an
        // `ArraySupplier` overload that is already portable (`Array(boolean, int, ArraySupplier)`,
        // `toArray(ArraySupplier)`, `ChannelDescriptor(int, ArraySupplier, int)`, …). The corpus
        // calls none of the deprecated forms — `ParticleChannels` already builds every descriptor
        // with `float[]::new` — so dropping them costs no call site and removes the last JVM-only
        // dependency outright. Overload-precise keys: the `ArraySupplier` twins must survive.
        "com.badlogic.gdx.utils.Array#<init>(boolean,int,Class)",
        "com.badlogic.gdx.utils.Array#<init>(Class)",
        "com.badlogic.gdx.utils.Array#toArray(Class)",
        "com.badlogic.gdx.utils.Array#of(Class)",
        "com.badlogic.gdx.utils.Array#of(boolean,int,Class)",
        // the Array subclasses' own deprecated pairs only forward to Array's, so they go with them.
        // (`DelayedRemovalArray` was found by RewriteTrace's orphaned-call check, not by grep.)
        "com.badlogic.gdx.utils.SnapshotArray#<init>(boolean,int,Class)",
        "com.badlogic.gdx.utils.SnapshotArray#<init>(Class)",
        "com.badlogic.gdx.utils.DelayedRemovalArray#<init>(boolean,int,Class)",
        "com.badlogic.gdx.utils.DelayedRemovalArray#<init>(Class)",
        "com.badlogic.gdx.utils.ArrayMap#<init>(boolean,int,Class,Class)",
        "com.badlogic.gdx.utils.ArrayMap#<init>(Class,Class)",
        "com.badlogic.gdx.utils.Queue#<init>(int,Class)",
        "com.badlogic.gdx.graphics.g3d.particles.ParallelArray$ChannelDescriptor#<init>(int,Class,int)",
        // the one in-corpus caller of a dropped ctor: itself deprecated, with an `ArraySupplier`
        // twin, and both subclasses (`BillboardParticleBatch`, `PointSpriteParticleBatch`) already
        // call the twin — so it too goes with no call site left behind.
        "com.badlogic.gdx.graphics.g3d.particles.batches.BufferedParticleBatch#<init>(Class)",
      ),
      inject = List(overridesRoot),
    )

    // NB: a dropped type is still PARSED — only its OUTPUT is replaced by the injected Scala.
    // Removing it from the model instead would leave references to it unresolved, silently
    // degrading translation of the code that USES it (a `Field` of unknown type stops being
    // recognised as a non-String operand, so Java string concat loses its `String.valueOf` wrap).
    val files = Files.walk(base).iterator().asScala
      .filter(p => p.toString.endsWith(".java"))
      .map(p => base.relativize(p).toString)
      .filterNot(f => f.endsWith("package-info.java") || f.endsWith("module-info.java"))
      .toList.sorted

    println(s"[libgdx-core] building model over ${files.size} files…")
    val types   = SpoonTir.buildModel(FrontendConfig(base, files, Nil, Nil), lenient = true)
    val raw0    = SpoonTir.fromTypes(types, subs)
    // libGDX's ONE runtime class lookup by name (`ResourceData.AssetData` resolving a persisted
    // asset type) has no counterpart off the JVM, so it is re-pointed at an explicit name->class
    // table — injected as `particles.AssetTypeRegistry`, seeded with the types libGDX core itself
    // stores and open for a downstream port to extend.
    val classTable = new ClassTableTransform(Map(
      "com.badlogic.gdx.utils.reflect.ClassReflection#forName" ->
        "com.badlogic.gdx.graphics.g3d.particles.AssetTypeRegistry#classFor"
    ))
    // libGDX routes every reflective operation through `ClassReflection` so its GWT/Android
    // backends can supply their own implementation. sge drops that wrapper: it targets Scala
    // Native and Scala.js, where runtime reflection does not exist. But most of what the corpus
    // actually calls is not reflection at all — these are plain `java.lang.Class` members that
    // both platforms DO provide, reached through the call's first argument. Re-pointing them
    // leaves behind exactly the members that genuinely need replacing (`forName` above, and the
    // declared-field/method/constructor readers, which stay in `Substitutions.dropTypes`).
    val unwrapReflection = new StaticForwarderTransform(List(
      StaticForwarderTransform.Forwarder(
        wrapper  = "com.badlogic.gdx.utils.reflect.ClassReflection",
        receiver = "java.lang.Class",
        members  = Set("getSimpleName", "getName", "isInstance", "isAssignableFrom", "isArray",
                       "isEnum", "isInterface", "isPrimitive", "isAnnotation", "getComponentType"),
      )
    ))
    val program = if raw then raw0
                  else Pipeline.run(raw0, List(new CollectionsTransform, new MutableParamsTransform, new PanamaFfiTransform(), unwrapReflection, classTable))
    println(s"[libgdx-core] TIR: ${program.units.size} units, ${program.symbols.all.size} symbols")

    // Signature-changing rewrites are only safe if every USE follows. Report the blast radius of
    // the substituted types, then check the whole program for uses that disagree with their
    // declaration's current signature — derived from the tree, so it catches sites no one listed.
    val substituted = program.symbols.all.collect { case s if Substituted.tags(s) => s.id }.toSet
    if substituted.nonEmpty then
      println(s"[libgdx-core] substitution blast radius:\n${RewriteTrace.impactSummary(program, substituted)}")
    val mismatches = RewriteTrace.check(program)
    if mismatches.isEmpty then println("[libgdx-core] signature check: all call sites agree with their declarations")
    else
      println(s"[libgdx-core] signature check: ${mismatches.size} site(s) disagree with their declaration")
      mismatches.take(20).foreach(m => println("  " + m.render))

    // Cross-platform target (sge ships Scala.js / Native): report every JDK API the port still
    // depends on that those platforms cannot provide. `--js` COMPILE does not catch these — only
    // the Scala.js linker does, and only for code reachable from an entry point, which a library
    // has none of. Over the TIR we see all of it.
    val droppedIds = program.symbols.all.collect { case s if subs.dropsType(s.fullName) => s.id }.toSet
    // Constructs carried in the TIR but not emitted — a silent omission compiles green and
    // misbehaves at runtime, so it must show up as a number on every run.
    val omissions = OmissionCheck.check(program)
    if omissions.isEmpty then println("[libgdx-core] omissions: none")
    else
      println(s"[libgdx-core] OMISSIONS (emitted code silently loses these): ${omissions.size}")
      println(OmissionCheck.summary(omissions).linesIterator.take(14).mkString("\n"))

    val portability = PortabilityCheck.inEmittedCode(program, PortabilityCheck.check(program), droppedIds)
    println(s"[libgdx-core] portability (Scala.js/Native): ${portability.size} site(s) on JVM-only APIs in EMITTED code")
    println(PortabilityCheck.summary(portability))

    val outDir = repoRoot.resolve("libgdx-core/src/main/scala")
    if Files.exists(outDir) then Files.walk(outDir).iterator().asScala.toList.reverse.foreach(Files.delete)
    Files.createDirectories(outDir)
    val emitter = new TirEmitter(program)
    var written = 0
    var dropped = 0
    program.units.foreach { u =>
      val full = program.symbolOf(u.symbol).map(_.fullName).getOrElse("Unit")
      // Substitutions.dropTypes: parsed (so every reference to it still resolves) but NOT emitted —
      // the injected Scala below supplies this FQN instead.
      if subs.dropsType(full) then dropped += 1
      else
        val rel = full.replace('.', '/') + ".scala"
        val p   = outDir.resolve(rel)
        Files.createDirectories(p.getParent)
        Files.writeString(p, emitter.emitUnit(u))
        written += 1
    }
    // Support types a phase RETYPED code onto (see CollectionsTransform.runtimeSources): the
    // output is compiled standalone, so a target type outside the scala stdlib ships with it.
    CollectionsTransform.runtimeSources.foreach { (fqn, src) =>
      val p = outDir.resolve(fqn.replace('.', '/') + ".scala")
      Files.createDirectories(p.getParent)
      Files.writeString(p, src)
      written += 1
    }
    // CHECK 1 — the engine itself must not have emitted any dropped type. (Run before injection,
    // so a file present here can only have come from the emitter.)
    val leaked = subs.dropTypes.filter(fqn => Files.exists(outDir.resolve(fqn.replace('.', '/') + ".scala")))
    if leaked.nonEmpty then
      sys.error(s"[libgdx-core] dropped types were still EMITTED: ${leaked.toList.sorted.mkString(", ")}")

    // inject ready-made Scala verbatim (survives the wipe-and-regenerate above):
    // new types with no Java counterpart, or whole-file replacements for dropped ones.
    var injected = 0
    subs.inject.filter(Files.exists(_)).foreach { root =>
      Files.walk(root).iterator().asScala
        .filter(p => p.toString.endsWith(".scala"))
        .foreach { src =>
          val rel = root.relativize(src).toString
          val dst = outDir.resolve(rel)
          Files.createDirectories(dst.getParent)
          Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING)
          injected += 1
        }
    }
    // injected replacements bypass the TIR — scan their text for the same portability rules, so a
    // hand-written shim cannot quietly reintroduce the API the substitution was meant to remove.
    val injectedViolations = subs.inject.filter(Files.exists(_)).flatMap { root =>
      Files.walk(root).iterator().asScala.filter(_.toString.endsWith(".scala")).toList.flatMap { src =>
        PortabilityCheck.inInjectedSource(root.relativize(src).toString, Files.readString(src))
      }
    }
    if injectedViolations.isEmpty then println("[libgdx-core] portability of injected replacements: clean")
    else
      println(s"[libgdx-core] portability of injected replacements: ${injectedViolations.size} finding(s)")
      injectedViolations.foreach(v => println("  " + v))

    // CHECK 2 — what stands at a dropped type's FQN in the FINAL code must be either an injected
    // replacement or nothing at all (every use rewritten away by a phase, via the `Substituted`
    // tag). A dangling reference means the substitution was declared but never carried out — fail
    // loudly rather than ship code that depends on a type this port claims not to have.
    val sources = Files.walk(outDir).iterator().asScala
      .filter(p => p.toString.endsWith(".scala")).toList
      .map(p => p -> Files.readString(p))
    val incomplete = subs.dropTypes.toList.sorted.flatMap { fqn =>
      if Files.exists(outDir.resolve(fqn.replace('.', '/') + ".scala")) then None // replaced
      else
        val refs = sources.count((_, src) => src.contains(fqn))
        if refs == 0 then None else Some(fqn -> refs) // rewritten away vs. dangling
    }
    if incomplete.nonEmpty then
      incomplete.foreach: (fqn, n) =>
        System.err.println(s"[libgdx-core] SUBSTITUTION INCOMPLETE: $fqn is dropped, has no replacement, and is still referenced by $n file(s)")
      sys.error(s"[libgdx-core] ${incomplete.size} declared substitution(s) not carried out")
    println(s"[libgdx-core] substitutions: ${subs.dropTypes.size} dropped types verified removed from the final code")

    println(s"[libgdx-core] wrote $written Scala files ($dropped dropped, $injected injected) -> $outDir")
    println(s"[libgdx-core] now: sbt libgdx-core/compile")
