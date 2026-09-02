package balticporter.corpus.gdxai

import balticporter.core.{FrontendConfig, ParityRef, PortManifest, Provenance, RuntimeMode}
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
  * ==The reflective parser, and why it is FIVE BODIES rather than a dropped subtree==
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
  * port's to discharge", read one step out). So this port drops nothing there and converts every
  * file upstream compiles; what it replaces is one TYPE OCCURRENCE and five method BODIES, over an
  * injected `TaskRegistry` adapted from the reference hand port's. PROGRESS.md §10.7.8 holds the
  * reasoning, the numbers and what the table costs the port.
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
        // THE BEHAVIOUR-TREE PARSER'S REFLECTIVE HALF, and the ONE seam that makes the rest of it
        // keepable. `DefaultBehaviorTreeReader` names the base's dropped `reflect.Field` in THREE
        // SIGNATURES — `getField`'s return type, `setField`'s and `castValue`'s first parameter —
        // and a body substitution can never reach a signature. `dropMethods` was the obvious cut
        // and is measurably the wrong one, for two reasons that are facts about this file:
        //
        //   - `castValue` is `protected` and its javadoc says "Subclasses may override this method
        //     to parse unsupported types". Dropping it removes a documented extension point to fix
        //     a type the port never wanted;
        //   - the three CALL SITES are inside `Statement.TreeTask`, an ENUM CONSTANT WITH A BODY,
        //     and no policy key can name a member there: the constant's symbol is
        //     `…$Statement#TreeTask`, so its members spell `…$Statement#TreeTask#attribute(…)` and
        //     `MemberKey.parse` refuses a second `#` (`ENGINE-LIMITS.md` T23). Drop the three and
        //     that arm is a `substitution(dangling)` this port caused, with nothing able to repair
        //     it.
        //
        // Re-pointing the TYPE fixes all three signatures at once and leaves every call site
        // mechanically translated — the seam `TypeRedirectTransform` exists for, and the same shape
        // Ashley uses for `ReflectionPool`. The base drops `Field` and injects nothing, so nothing
        // stands at that name and a dependent may claim it (CLAUDE.md §1.5).
        //
        // The TARGET is written in the UPSTREAM namespace so the base's inherited
        // `com.badlogic.gdx -> sge` carries it, exactly as it carries every emitted unit.
        //
        // SCOPED, and the scope is not tidiness. A dependent's `Program` CONTAINS its base
        // (`ENGINE-LIMITS.md` D2), so an unscoped redirect re-points `Field` inside libGDX's own
        // declarations too — which this module never emits, and which therefore changes only what
        // it DERIVES for them. Measured: libGDX's `Json$FieldMetadata` takes a `reflect.Field` and
        // its published contract row says `primary=(Field)`; unscoped, this run re-derived
        // `primary=(TaskField)` and `base-surface` went 0 -> 1 FATAL. No compile could have said
        // so — the base's file is not emitted here — which is precisely what the published map is
        // for (§1.5).
        new balticporter.transform.TypeRedirectTransform(
          redirects = Map(
            "com.badlogic.gdx.utils.reflect.Field" -> "com.badlogic.gdx.ai.btree.utils.TaskField",
          ),
          scopes = Map(
            "com.badlogic.gdx.utils.reflect.Field" ->
              balticporter.tir.RuleScope.Only(Set("com.badlogic.gdx.ai")),
          ),
        ),
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
        // the body below reads it through the accessor `this.size` rather than through the
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
              |      java.lang.System.arraycopy(this.items, this.head, newItems, 0, this.size)
              |    } else if (this.size > 0) {
              |      // NOTE: when head == tail the buffer can be empty or full
              |      java.lang.System.arraycopy(this.items, this.head, newItems, 0, this.items.length - this.head)
              |      java.lang.System.arraycopy(this.items, 0, newItems, this.items.length - this.head, this.tail)
              |    }
              |    this.head = 0
              |    this.tail = this.size
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

          // ---------------------------------------------------------------------------------
          // THE BEHAVIOUR-TREE PARSER — FIVE BODIES, and nothing else in the file moves.
          //
          // `DefaultBehaviorTreeReader` asked the JVM three questions at run time: *build me the
          // class with this name*, *what does `@TaskConstraint`/`@TaskAttribute` say about this
          // class*, and *give me this field so I can coerce a value into it*. Each becomes a lookup
          // in `sge.ai.btree.utils.TaskRegistry` — a hand-written table this port injects, adapted
          // from the reference hand port's own `TaskRegistry`/`TaskMeta`/`AttrInfo` trio
          // (`../sge/sge-extension/ai/src/main/scala/sge/ai/btree/utils/BehaviorTreeParser.scala`),
          // which answers the same wall the same way and for the same reason: a SETTER CLOSURE
          // knows the field's type where it is written, which is exactly what `castValue` had to
          // ask the JVM for.
          //
          // The other 770 lines of the file — the four `Statement` constants, the stack discipline,
          // the subtree table, the integrity checks — stay mechanically translated, which is the
          // whole point of choosing five body seams over a `dropTypes` of the parser. The parser's
          // PUBLIC surface is untouched: `DEBUG_NONE`, four `parse` overloads, `printTree`, the
          // nested reader and every member the seven consumer types reach.
          //
          // WHAT THE PORT LOSES, stated rather than discovered later: java's mechanism is OPEN (any
          // classpath type carrying the two annotations works with no registration), and a table is
          // CLOSED. An unregistered task name is a `ReflectionException` — the SAME exception java's
          // `forName` threw for a class that is not on the classpath, so `openTask`'s own `catch`
          // still produces java's `"Cannot parse behavior tree!!!"`. `@Inherited` IS reproduced:
          // `metaOf` walks the superclass chain, so a consumer's `class X extends LeafTask` gets
          // `(0, 0)` without registering a constraint, exactly as java's annotation gave it one.

          // `ClassReflection.newInstance(ClassReflection.forName(className))`. Everything else in
          // this body is the emitted translation verbatim, INCLUDING the `catch` — which is what
          // makes the registry's refusal arrive at the caller in java's own words.
          "com.badlogic.gdx.ai.btree.utils.BehaviorTreeParser$DefaultBehaviorTreeReader#openTask(String,boolean)" ->
            """{
              |    try {
              |      var task: sge.ai.btree.Task[E] = null.asInstanceOf[sge.ai.btree.Task[E]]
              |      if (this.isSubtreeRef) {
              |        task = this.subtreeRootTaskInstance(name)
              |      } else {
              |        var className: java.lang.String = this.getImport(name)
              |        if (className == null) {
              |          className = name
              |        } else ()
              |        task = sge.ai.btree.utils.TaskRegistry.newTask(className).asInstanceOf[sge.ai.btree.Task[E]]
              |      }
              |      if (!this.currentTree.inited()) {
              |        this.initCurrentTree(task, this.indent)
              |        this.indent = 0
              |      } else {
              |        if (!isGuard) {
              |          val stackedTask: sge.ai.btree.utils.BehaviorTreeParser.DefaultBehaviorTreeReader.StackedTask[E] = this.prevTask
              |          this.indent = this.indent - this.currentTreeStartIndent
              |          if (stackedTask.task eq this.currentTree.rootTask) {
              |            this.step = this.indent
              |          } else ()
              |          if (this.indent > this.currentDepth) {
              |            // push
              |            this.stack.add(stackedTask)
              |          } else {
              |            if (this.indent <= this.currentDepth) {
              |              // Pop tasks from the stack based on indentation
              |              // and check their minimum number of children
              |              val i: scala.Int = (this.currentDepth - this.indent) / this.step
              |              this.popAndCheckMinChildren(this.stack.size - i)
              |            } else ()
              |          }
              |          // Check the max number of children of the parent
              |          val stackedParent: sge.ai.btree.utils.BehaviorTreeParser.DefaultBehaviorTreeReader.StackedTask[E] = { if (this.stack.isEmpty) throw new java.lang.IllegalStateException("Array is empty."); this.stack.peek }
              |          val maxChildren: scala.Int = stackedParent.metadata.maxChildren
              |          if (stackedParent.task.childCount >= maxChildren) {
              |            throw this.stackedTaskException(stackedParent, ((("max number of children exceeded (" + (stackedParent.task.childCount + 1)) + " > ") + maxChildren) + ")")
              |          } else ()
              |          // Add child task to the parent
              |          stackedParent.task.addChild(task)
              |        } else ()
              |      }
              |      this.updateCurrentTask(this.createStackedTask(name, task), this.indent, isGuard)
              |    } catch {
              |      case e: sge.utils.reflect.ReflectionException => {
              |        throw new sge.utils.GdxRuntimeException("Cannot parse behavior tree!!!", e)
              |      }
              |    }
              |  }""".stripMargin,

          // `getAnnotation(clazz, TaskConstraint.class)` + `getFields` + each field's
          // `getDeclaredAnnotation(TaskAttribute.class)`. The CACHE and the `null` protocol are
          // java's own and are kept: `null` means *no `@TaskConstraint` in this hierarchy*, which
          // `createStackedTask` turns into upstream's "annotation not found" message. `Metadata` and
          // `AttrInfo` are the port's own emitted nested classes, built here from the registry's
          // rows exactly as java built them from the annotations.
          //
          // …AND THE BASE'S `@Null` SURFACE IS READ OFF THE GENERATED CALLER (`CLAUDE.md` §1). A
          // hand-written body is outside the threading closure, so when the base moved
          // `ObjectMap#get(K)` to `lowlevel.Nullable[V]` under the `Named` target nothing threaded
          // this text and nothing could: the mechanical call sites in this very port took the
          // engine's `.get` unwrap (`this.pools.get(ref).get`) while this one kept java's spelling
          // and became `E171 missing argument for parameter defaultValue` — scalac falling through
          // to the two-argument overload, because the one-argument result no longer conforms.
          // What it must NOT take is that same `.get`: the wrapper's `get` is the UNCHECKED unwrap
          // (NPE on empty, which is java's semantics at a DEREFERENCE), and here the empty case is
          // the normal one — a cache MISS is what the `null` check exists to drive, and every first
          // lookup is a miss. `isEmpty` is the test, and java's protocol survives unchanged below.
          "com.badlogic.gdx.ai.btree.utils.BehaviorTreeParser$DefaultBehaviorTreeReader#findMetadata(Class)" ->
            """{
              |    val cached: lowlevel.Nullable[sge.ai.btree.utils.BehaviorTreeParser.DefaultBehaviorTreeReader.Metadata] =
              |      this.metadataCache.get(clazz)
              |    var metadata: sge.ai.btree.utils.BehaviorTreeParser.DefaultBehaviorTreeReader.Metadata =
              |      if (cached.isEmpty) null else cached.get
              |    if (metadata == null) {
              |      val meta: sge.ai.btree.utils.TaskRegistry.Meta = sge.ai.btree.utils.TaskRegistry.metaOf(clazz)
              |      if (meta != null) {
              |        val taskAttributes: lowlevel.util.ObjectMap[java.lang.String, sge.ai.btree.utils.BehaviorTreeParser.DefaultBehaviorTreeReader.AttrInfo] =
              |          lowlevel.util.ObjectMap[java.lang.String, sge.ai.btree.utils.BehaviorTreeParser.DefaultBehaviorTreeReader.AttrInfo]()
              |        val attrs: scala.Array[sge.ai.btree.utils.TaskRegistry.Attr] = meta.attributes
              |        var i: scala.Int = 0
              |        while (i < attrs.length) {
              |          val a: sge.ai.btree.utils.TaskRegistry.Attr = attrs(i)
              |          val ai: sge.ai.btree.utils.BehaviorTreeParser.DefaultBehaviorTreeReader.AttrInfo =
              |            new sge.ai.btree.utils.BehaviorTreeParser.DefaultBehaviorTreeReader.AttrInfo(a.name, a.fieldName, a.required)
              |          taskAttributes.put(ai.name, ai)
              |          i = i + 1
              |        }
              |        metadata = new sge.ai.btree.utils.BehaviorTreeParser.DefaultBehaviorTreeReader.Metadata(meta.minChildren, meta.maxChildren, taskAttributes)
              |        this.metadataCache.put(clazz, metadata)
              |      } else ()
              |    } else ()
              |    metadata
              |  }""".stripMargin,

          // `ClassReflection.getField(clazz, name)`. Java raised a `GdxRuntimeException` wrapping the
          // `ReflectionException` for a field that is not there; so does this, with the one sentence
          // that says where the answer now comes from.
          "com.badlogic.gdx.ai.btree.utils.BehaviorTreeParser$DefaultBehaviorTreeReader#getField(Class,String)" ->
            """{
              |    val f: sge.ai.btree.utils.TaskField = sge.ai.btree.utils.TaskRegistry.fieldOf(clazz, name)
              |    if (f == null) {
              |      throw new sge.utils.GdxRuntimeException(new sge.utils.reflect.ReflectionException(
              |        ("no @TaskAttribute field '" + name + "' is registered for " + clazz.getName()) +
              |        "; this port resolves task attributes through sge.ai.btree.utils.TaskRegistry " +
              |        "rather than through runtime reflection, which the libGDX base drops"))
              |    } else ()
              |    f
              |  }""".stripMargin,

          // `field.setAccessible(true)` has no counterpart and needs none; EVERYTHING ELSE is java's
          // own control flow, `castValue`'s `null` protocol and its error message included — which
          // is what keeps `castValue` below a real override point rather than a member the port
          // happens still to declare.
          "com.badlogic.gdx.ai.btree.utils.BehaviorTreeParser$DefaultBehaviorTreeReader#setField(Field,Task,Object)" ->
            """{
              |    val valueObject: java.lang.Object = this.castValue(field, value)
              |    if (valueObject == null) {
              |      this.throwAttributeTypeException(this.currentTask.name, field.getName(), field.getTypeName())
              |    } else ()
              |    field.set(task, valueObject)
              |  }""".stripMargin,

          // THE OVERRIDE POINT UPSTREAM DOCUMENTS, kept. Java's body was a `switch` over
          // `field.getType()` because the type was only knowable at run time; the redirected
          // `TaskField` carries the coercion for its own declared type, so the body is the one
          // delegation and a subclass overriding this still decides what a value becomes.
          "com.badlogic.gdx.ai.btree.utils.BehaviorTreeParser$DefaultBehaviorTreeReader#castValue(Field,Object)" ->
            """{
              |    field.cast(value, this.btParser.distributionAdapters)
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
      // THE SERVICE-LOCATOR FACADE, replaced whole — this port's ONE drop, and its ONE injection.
      //
      // `GdxAI` chooses two of its three services by SNIFFING THE AMBIENT ENVIRONMENT at class
      // initialisation (`logger = Gdx.app == null ? new NullLogger() : new GdxLogger()`, and the
      // same for `fileSystem`), and the base retired `Gdx.*` into a context threaded through
      // `(using sge.Sge)`. A static field's initialiser runs before anything could pass it one, so
      // this is not a mechanism that is missing — it is a QUESTION THE PORT CANNOT ASK, because the
      // global whose presence answers it is the one the base removed. That is the whole of this
      // port's `context-seam 5` and of four of its first-emit errors.
      //
      // The replacement installs JAVA'S OWN NEGATIVE BRANCH unconditionally — `NullLogger` and
      // `StandaloneFileSystem` — which is what java installs when no libGDX environment is running,
      // and running gdx-ai out of a libGDX application is upstream's own headline use case. The
      // libGDX-backed pair is still emitted and is installed through `setLogger`/`setFileSystem`,
      // which is the sentence java's own class javadoc already writes for every platform its sniff
      // cannot serve. Everything else in the injected file is the emitted translation verbatim.
      //
      // WHY NOT A CONTEXT-SEAM POLICY: both exits were measured and both are worse; the numbers and
      // the shapes are `PROGRESS.md` §10.7.6. Briefly — `residual-global` answers only the SPELLING
      // of the read and leaves the two `unsuppliable use` seams, and `lazy-init` cannot express a
      // MUTABLE static, so java's own setters became `E052 Reassignment to val` while the threading
      // cascaded into two singletons in other files (14 = 14 errors, four of them NEW, 53 member
      // digests). `MethodBodyTransform` cannot reach it either: these are FIELD initialisers.
      //
      // THE CLOSURE IS ONE TYPE, verified rather than assumed: every reference to `GdxAI` anywhere
      // in these 166 files is a call to one of its six static accessors, all of which the injected
      // `object` declares at the same FQN — so `substitution(dangling)` stays 0, which is the gate
      // §10.7.3 refused the parser drop on.
      dropTypes = Set("com.badlogic.gdx.ai.GdxAI"),
      // THREE injected files, and only ONE of them replaces a drop. `sge/ai/GdxAI.scala` stands at
      // the FQN above; `sge/ai/btree/utils/TaskField.scala` and `TaskRegistry.scala` stand at names
      // nothing dropped and nothing emits — the redirect TARGET and the reflection-free table the
      // five substituted bodies read. `inject` is a directory copy, so all three ride the same key,
      // and it is deliberately NOT inherited by `test` below: exactly one module ships each file or
      // the FQN is defined twice.
      inject    = List(repoRoot.resolve("balticporter/corpus/gdxai-overrides")),
      // THE REFERENCE HAND PORT for sge-ai. NOT inherited (DESIGN.md §8.23).
      parity = Some(ParityRef(roots = List(
        repoRoot.resolve("../sge/sge-extension/ai/src/main/scala").normalize))),
    ))

  /** …and the TEST source set's, which is `core` EXTENDED and adds exactly one phase.
    *
    * `TestFrameworkTransform` is the only difference there can be: everything else about the surface
    * — the drops, the substituted bodies, the inherited rename, the base-map residue check — is a
    * fact about the API this suite compiles against, and a test manifest that restated any of it
    * would be free to drift from the main one (§1.5).
    *
    * `inject` is NOT restated, and that is the field where restating it would be a REAL defect
    * rather than a stylistic one: the main source set ships `sge/ai/GdxAI.scala`, and a second
    * manifest naming the same directory would emit a second definition of that FQN into the test
    * source set. `PortManifest` makes `inject` per-manifest for exactly this. */
  def test(repoRoot: Path): PortManifest = core(repoRoot).extendedBy(PortManifest(
    name    = "sge-ai-test",
    surface = List(new balticporter.transform.TestFrameworkTransform()),
  ))
