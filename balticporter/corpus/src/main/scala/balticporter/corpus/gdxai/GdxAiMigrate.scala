package balticporter.corpus.gdxai

import balticporter.core.{FrontendConfig, PortManifest, Provenance, RuntimeMode}
import balticporter.corpus.libgdx.LibgdxPolicy
import balticporter.runner.{Determinism, PortRun, SourceSet, VendoredCommit}

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** Migrate **gdx-ai** (`gdx-ai/src`, 166 types — libGDX's AI extension: behaviour trees, state
  * machines, message dispatch, pathfinding, steering, formation motion and scheduling) through the
  * TIR.
  *
  *   corpus/runMain balticporter.corpus.gdxai.GdxAiMigrate [--determinism=full]
  *
  * ==A DEPENDENT port, and the largest one so far==
  * `gdx-ai/build.gradle` declares exactly one compile dependency — `com.badlogicgames.gdx:gdx` —
  * and its 166 files reference 39 distinct `com.badlogic.gdx.*` symbols outside their own
  * namespace: `MathUtils`, `Matrix3`, `Vector2`/`Vector3`/`Vector`, the `utils` collections
  * (`Array`, `BinaryHeap`, `BooleanArray`, `IntArray`, `IntMap`, `ObjectMap`, `ObjectSet`, `Pool`,
  * `GdxRuntimeException`, `SerializationException`, `StreamUtils`, `TimeUtils`), the `assets`
  * loader family, `files.FileHandle` and `utils.reflect`. So `gdx/src` is a RESOLUTION root —
  * parsed so every reference resolves, never emitted here — and the policy is [[LibgdxPolicy.core]]
  * EXTENDED rather than restated (CLAUDE.md §1.5). `LibgdxCoreMigrate` emits the base and `just
  * ai-measure` compiles the two together.
  *
  * ==Scope: 166 of 167, and the one exclusion is UPSTREAM'S OWN==
  * `gdx-ai/src` holds 167 `.java` files and javac never sees one of them.
  * `com/badlogic/gdx/emu/com/badlogic/gdx/ai/StandaloneFileSystem.java` is GWT SUPER-SOURCE — the
  * module descriptor beside it declares `<super-source path="emu"/>`, so that tree REPLACES classes
  * for the GWT compile and is on no JVM classpath — and upstream's own build says so outright:
  * `[compileJava, compileTestJava, javadoc]*.exclude("com/badlogic/gdx/emu")`.
  *
  * It is not a taste question here, it is a NAME COLLISION: that file declares
  * `package com.badlogic.gdx.ai` and so does `com/badlogic/gdx/ai/StandaloneFileSystem.java`, so
  * handing both to the frontend puts TWO declarations of one FQN in one model and makes the emitter
  * write one `sge/ai/StandaloneFileSystem.scala` from whichever unit came second in the sorted file
  * list. No check reports that — the port would compile, and the emitted class would be the GWT
  * variant with the JVM one silently gone. The filter is therefore a fact this port reads off
  * upstream's build, not a scope preference.
  *
  * `gdx-ai/tests` (2 files, 10 `@Test`) is a real JUnit 4 suite and is NOT in this source set; it is
  * the next milestone's `GdxAiTestMigrate`. The separate top-level `tests/` gradle project (111
  * files, 54 of them named `*Test*.java`) is NOT a suite at all — it declares ZERO `@Test`, every
  * file is an `ApplicationAdapter`/`Game` demo launched through `GdxAiTestUtils.launch(new
  * LwjglApplication(…))`, and the `steer.box2d`/`steer.bullet` subpackages additionally want native
  * Box2D/Bullet. `just ai-measure` re-derives both numbers on every run rather than trusting this
  * comment.
  *
  * ==What this port has NOT decided, and why the first emit says nothing about it==
  * The scoping work recommended dropping a four-file "reflective parser subtree"
  * (`BehaviorTreeParser`, `BehaviorTreeLoader` and the two `btree.annotation` types) on the ground
  * that the parser is a self-contained leaf. It is not a leaf, and the drop as scoped is not
  * implementable: `BehaviorTreeLibrary` holds a `BehaviorTreeParser<?>` FIELD, constructs one, and
  * reads its `DEBUG_NONE` constant; `BehaviorTreeLibraryManager` and `PooledBehaviorTreeLibrary`
  * are built on the library; and `Include` — an ordinary `btree.decorator` task in the class
  * hierarchy every behaviour tree uses — reaches `BehaviorTreeLibraryManager.getInstance()` from
  * `createSubtreeRootTask()`. So the closure of that drop is seven types plus a `dropMethods` cut
  * into a task class, which is a REDESIGN and not a scope cut.
  *
  * A `dropTypes` entry whose references survive is not a smaller port, it is a
  * `substitution(dangling)` finding classified §1(b)/(c) — a defect the PORT caused, which is
  * exactly what CLAUDE.md §1 refuses ("an obligation the engine's own translation created is not a
  * port's to discharge", read one step out). So milestone 1 drops NOTHING and converts every file
  * upstream compiles. The reflective sites are reported by the checks, classified, and answered in a
  * later, separately-measured wave — the hand port's own `TaskRegistry`/`AttrInfo` trio is
  * reflection-free Scala at these FQNs and is what `inject` exists for. PROGRESS.md §sge-ai holds
  * the reasoning and the numbers.
  *
  * The two `btree.annotation` types stay for a smaller reason of the same shape: they are
  * self-contained `@interface` declarations that name nothing outside `java.lang.annotation`, and
  * every USE of them is an annotation the default `AnnotationPolicy` already declines to carry.
  * Dropping them would remove library surface to fix nothing.
  */
object GdxAiMigrate:

  def main(args: Array[String]): Unit =
    val repoRoot = Path.of(sys.props.getOrElse("balticporter.root", ".")).toAbsolutePath.normalize
    val upstream = repoRoot.resolve("../sge/original-src/gdx-ai").normalize
    val base     = upstream.resolve("gdx-ai/src").normalize
    val gdxSrc   = repoRoot.resolve("../sge/original-src/libgdx/gdx/src").normalize

    val files = Files.walk(base).iterator().asScala
      .filter(p => p.toString.endsWith(".java"))
      .map(p => base.relativize(p).toString)
      .filterNot(f => f.endsWith("package-info.java") || f.endsWith("module-info.java"))
      // GWT SUPER-SOURCE — upstream's own `compileJava` exclusion, and a second declaration of
      // `com.badlogic.gdx.ai.StandaloneFileSystem` if it is not honoured. See the scope note above.
      // Matched on the path SEGMENT and not as a substring: `com/badlogic/gdx/emu/` is a directory,
      // and a bare `contains("emu")` would also name a package called `emulation` (CLAUDE.md §4.56).
      .filterNot(f => f.startsWith("com/badlogic/gdx/emu/"))
      .toList.sorted

    PortRun(
      label     = "sge-ai",
      portRoot  = repoRoot.resolve("ported/sge-ai"),
      sourceSet = SourceSet.Main,
      frontend  = FrontendConfig(base, files, Nil, resolutionRoots = List(gdxSrc)),
      phases    = Nil, // supplied by the manifest — the two sources are mutually exclusive
      manifest  = Some(GdxAiPolicy.core(repoRoot)),
      provenance = Some(Provenance(
        upstreamName     = "gdx-ai",
        upstreamCommit   = VendoredCommit.of(base),
        originalLicense  = "Apache-2.0",
        sourcePathPrefix = "gdx-ai/src",
        sourceRoot       = base.toString,
        // ONE FILE IN 167 CARRIES NO PER-FILE NOTICE, and that is the whole reason this key is here
        // (CLAUDE.md §4.57). Apache-2.0 ports normally state nothing: the licence lives in every
        // source file, so reproducing each file's comment (§4.58) plus the emitted banner meets the
        // obligation by construction. `btree/utils/PooledBehaviorTreeLibrary.java` is the exception —
        // a later community contribution with no header at all — so for that ONE emitted file the
        // banner NAMES Apache-2.0 and reproduces no notice, which is precisely the pointer-instead-of-
        // inclusion gap §4.57 was written about. Shipping the upstream `LICENSE` beside the emitted
        // code costs one file and removes the question; a declared file that is not there is fatal,
        // so this cannot rot into a claim.
        notices          = List(upstream.resolve("LICENSE")),
      )),
      // NOT `Vendored`: `LibgdxCoreMigrate` already vendors the collection shims into the module
      // this output is compiled beside. Vendoring again would define every support type twice —
      // which the JVM tolerates only while the copies agree, and the Scala.js/Native linkers reject.
      runtimeMode = RuntimeMode.Dependency,
      determinism = Determinism.fromArgs(args.toSeq),
      nextStep    = "just ai-measure",
    ).execute()

/** gdx-ai's per-library policy — a DEPENDENT of libGDX core's, and deliberately almost empty.
  *
  * The base's `dropTypes`, `dropMethods`, `packageRenames` and every signature-affecting phase are
  * INHERITED, not restated: they are facts about the surface gdx-ai compiles against, and a
  * dependent that re-declared them would be free to drift (CLAUDE.md §1.5). That is also where the
  * collections and mutable-parameter phases come from — a libGDX dependent does not start them, it
  * receives the base's ONE instance, and adding a second would be a `SurfaceDivergence` for a
  * composition nobody designed.
  *
  * `inject` is deliberately NOT inherited (see [[balticporter.core.PortManifest]]): a drop is an
  * observation about the shared API and binds every module that sees it, but exactly one module
  * ships each replacement file. libGDX core ships the replacements for the types it dropped.
  *
  * Milestone 1 adds a namespace CLAIM and the base-surface residue check, and NOTHING else — not
  * even a rename, which is the one thing that makes this port different from every other libGDX
  * dependent (see `packageRenames` below). A wall measured under invented policy says nothing about
  * the engine, so every entry after these arrives with the number that justified it.
  */
object GdxAiPolicy:

  def core(repoRoot: Path): PortManifest =
    LibgdxPolicy.core(repoRoot).extendedBy(PortManifest(
      name    = "sge-ai",
      governs = Set("com.badlogic.gdx.ai"),
      // NO `packageRenames`, AND THAT IS THE ONE THING THAT MAKES THIS PORT DIFFERENT FROM EVERY
      // OTHER LIBGDX DEPENDENT IN THE CORPUS.
      //
      // Ashley declares `com.badlogic.ashley`, anim8 `com.github.tommyettinger.anim8`, gdx-gltf
      // `net.mgsx.gltf` — each is a namespace OUTSIDE the base's, so each has to state its own
      // destination. gdx-ai declares `com.badlogic.gdx.ai`, which is INSIDE `com.badlogic.gdx`, and
      // the base's inherited `com.badlogic.gdx -> sge` therefore already carries every one of these
      // 166 units to `sge.ai.*` — the exact destination the reference hand port uses
      // (`../sge/sge-extension/ai/src/main/scala/sge/ai`, a package-for-package mirror).
      //
      // Written out, `com.badlogic.gdx.ai -> sge.ai` computes the same strings and is still WRONG,
      // and this is not a style point: `ManifestAgreement` reports it as a fatal `RenameOverride`
      // ("renamed to sge.ai here; the base claims this namespace and leaves it in place"), because a
      // dependent holding its own rule for a prefix inside the base's namespace is a rule free to
      // drift the day the base's changes — two ports that each compile alone and cannot compile
      // together (CLAUDE.md §1.5). Measured: it was declared on the first emit and that check is
      // what found it — `manifest 1 -> 0`, errors flat at 20, and 166 member digests moved, one per
      // unit, every one this port's own `RenamedPackage` decision changing its porter note's `key=`
      // to the base's pair. The emitted PACKAGE is identical on both sides, which is exactly why
      // nothing but `ManifestAgreement` could have reported it.
      //
      // `governs` STAYS, and is the sub-claim the drift check needs: it says which slice of the
      // base's namespace this module's declarations occupy, and it is what would catch a future
      // dependent renaming part of it.
      surface = List(
        // THE TWO SINGLE-SITE REFLECTIVE MEMBERS — Ashley's `Engine#createComponent` shape, twice.
        //
        // Both classes are ordinary algorithmic code with exactly one member the base's dropped
        // `com.badlogic.gdx.utils.reflect` reaches, so dropping either TYPE to fix one method would
        // fork it from upstream permanently and dropping the METHOD leaves its callers with
        // nothing. The signature — and therefore every call site — is untouched.
        //
        // NB the two namespaces, exactly as `AshleyPolicy` states: the KEY names the member in the
        // UPSTREAM namespace, because the phase matches it against the model BEFORE the rename
        // runs; the BODY is spliced verbatim into emitted code, so it is written in the port's
        // FINAL namespace — including the EMITTED SPELLING of any member a §4.55 rename moved
        // (`CircularBuffer.size` collides with `size()` and emits as `size$field`, which is why
        // the body below reads it through the accessor `this.size()` rather than through the
        // field: the accessor's name is java's own and cannot move under it).
        new balticporter.transform.MethodBodyTransform(Map(
          // `ArrayReflection.newInstance(items.getClass().getComponentType(), n)` — the generic
          // array allocation java has no other spelling for. It is NOT an approximation here, it
          // is the SAME ARRAY: this class's own constructor writes `(T[]) new Object[capacity]`,
          // so `items.getClass().getComponentType()` IS `java.lang.Object` at every call and
          // java's reflective allocation returns an `Object[]` too. The port already emits
          // `new scala.Array[java.lang.Object](…).asInstanceOf[scala.Array[T]]` for the
          // constructor's half of the same fact.
          "com.badlogic.gdx.ai.utils.CircularBuffer#resize(int)" ->
            """{
              |    val newItems: scala.Array[T] =
              |      new scala.Array[java.lang.Object](newCapacity).asInstanceOf[scala.Array[T]]
              |    if (this.tail > this.head) {
              |      java.lang.System.arraycopy(this.items, this.head, newItems, 0, this.size())
              |    } else if (this.size() > 0) {
              |      // NOTE: when head == tail the buffer can be empty or full
              |      java.lang.System.arraycopy(this.items, this.head, newItems, 0, this.items.length - this.head)
              |      java.lang.System.arraycopy(this.items, 0, newItems, this.items.length - this.head, this.tail)
              |    }
              |    this.head = 0
              |    this.tail = this.size()
              |    this.items = newItems
              |  }""".stripMargin,

          // `ClassReflection.newInstance(this.getClass())` — a reflective SELF-CLONE, and the one
          // site in this library with no mechanical image at all. There is no portable way to
          // instantiate a class known only at run time, and the alternative the reference hand port
          // took (an abstract `newInstance()` every one of the 30-odd task classes implements) is a
          // SIGNATURE change this phase deliberately does not make.
          //
          // So this is the JAVA CONTRACT'S OWN REFUSAL (CLAUDE.md §1): `cloneTask` declares
          // `@throws TaskCloneException if the task cannot be successfully cloned`, and a
          // `TaskCloneException` is therefore a conforming outcome — LOUDER than java, never
          // quieter, and never a stand-in that compiles and silently clones the wrong thing. The
          // `TASK_CLONER` branch is java's own escape hatch and is kept verbatim: a port that needs
          // cloning sets one, which is exactly what upstream tells a GWT user to do.
          "com.badlogic.gdx.ai.btree.Task#cloneTask()" ->
            """{
              |    if (sge.ai.btree.Task.TASK_CLONER != null) {
              |      try sge.ai.btree.Task.TASK_CLONER.cloneTask(this)
              |      catch { case t: java.lang.Throwable => throw new sge.ai.btree.TaskCloneException(t) }
              |    } else throw new sge.ai.btree.TaskCloneException(
              |      "cloneTask() without a Task.TASK_CLONER needs reflective instantiation, which " +
              |      "this port does not have (com.badlogic.gdx.utils.reflect is dropped: Scala.js " +
              |      "and Scala Native cannot do it). Set Task.TASK_CLONER, which is the same escape " +
              |      "hatch upstream documents for GWT.")
              |  }""".stripMargin,
        )),
        // LAST, deliberately, for the reason `AshleyPolicy` states: this reads what the BASE
        // actually emitted and reports a reference the base does not ship, so it must run after any
        // seam that re-points such a reference, or it reports the very sites the next phase repairs.
        // A residue check, exactly like `PortabilityCheck`. Milestone 1 has no such seam yet, which
        // is the point — the rows it files ARE the milestone's finding, and `utils.reflect` is the
        // family they name: 14 `DroppedType` sites, 12 in `BehaviorTreeParser` and one each in
        // `Task.cloneTask()` and `CircularBuffer.resize(int)`. `MessageDispatcher`'s
        // `ClassReflection.isInstance` files NO row and produces no error — the engine already
        // rewrites it to `isInstanceOf`, which is the difference between a reflective API and a
        // reflective MECHANISM.
        balticporter.transform.PortMapTransform.forBases("sge"),
      ),
    ))
