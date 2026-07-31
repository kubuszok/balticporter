/*
 * The behavioural gate for the libgdx-screenmanager port — HAND-WRITTEN, and stated as such.
 *
 * Assertions marked "upstream" reproduce an assertion from libgdx-screenmanager's own JUnit suite
 * (Apache-2.0, copyright 2020-2023 damios); the rest are adapted from the reference hand port's
 * MUnit suites (`../sge/sge-extension/screens/src/test/scala`) or written here for the seam this
 * port added.
 *
 * ==Why the upstream suite is not MIGRATED, with the numbers==
 * Upstream ships 7 test files and 12 `@Test` methods. TEN of the twelve are structurally out of
 * reach of this corpus, and neither reason is something a transform can fix:
 *
 *   - `LibgdxUnitTest`, the base class of six of the seven files, boots
 *     `com.badlogic.gdx.backends.headless.HeadlessApplication`. NO libGDX BACKEND IS PORTED — not
 *     by this engine and not by the reference hand port (PROGRESS.md §1.1: "an engine-driven port
 *     yields a library that cannot open a window") — so the fixture has no counterpart to compile
 *     against, let alone to run.
 *   - `ScreenManagerUnitTest` adds `Mockito.mockStatic(ScreenFboUtils.class)` and
 *     `Mockito.spy(new ScreenManager())`. Both instrument JVM bytecode of the type under test at
 *     run time; `mockStatic` in particular replaces a static method of the ported library so the
 *     tests never touch GL. That is a JVM-only capability, and the port's whole reason for
 *     existing is Scala.js and Scala Native.
 *
 * The two that need neither are `BasicInputMultiplexerTest` and `TimedScreenTransitionTest`, and
 * their assertions are reproduced below, marked. `just screens-measure` prints all three numbers —
 * upstream `@Test`, emitted, hand-written — rather than letting a migrated count of zero read as
 * agreement.
 *
 * ==What this suite can and cannot reach==
 * Everything below runs with NO GL context and no `Gdx.app`. That is a real limit and it is where
 * the residue is: `NestableFrameBuffer`'s nesting contract, `QuadMeshGenerator`'s vertex layout
 * and `ScreenManager.render`'s framebuffer round trip all issue GL calls on the first statement,
 * so nothing here proves them. What IS reachable is every path that is pure state — the transition
 * clock, the input multiplexer, the sliding factors, the screen/transition lifecycle — plus the
 * guacamole seam this port introduced, which is asserted through the PORTED code rather than on
 * the shims directly wherever the ported code reaches it.
 */
package sge.screen

import sge.screen.transition.{ScreenTransition, TimedTransition}
import sge.screen.transition.impl.SlidingDirection
import sge.screen.utils.BasicInputMultiplexer

class ScreenmanagerSuite extends munit.FunSuite:

  // ---------------------------------------------------------------------------------------
  // BasicInputMultiplexer — upstream `BasicInputMultiplexerTest.test()`, assertion for assertion.
  //
  // One of exactly two upstream tests that needs no backend. It is also the only coverage this
  // port has of a type the reference hand port does not have at all (sge uses libGDX's
  // `InputMultiplexer` directly and drops the batch add/remove pair being exercised here).
  // ---------------------------------------------------------------------------------------

  test("BasicInputMultiplexer: batch and single add/remove (upstream)") {
    val i = new BasicInputMultiplexer()
    assertEquals(i.getProcessors().size, 0)

    val inputProcessors = new sge.utils.Array[sge.InputProcessor](4)
    inputProcessors.add(new sge.InputAdapter())
    inputProcessors.add(new sge.InputAdapter())

    i.addProcessors(inputProcessors)
    assertEquals(i.getProcessors().size, 2)

    i.removeProcessors(inputProcessors)
    assertEquals(i.getProcessors().size, 0)

    i.addProcessor(new sge.InputAdapter())
    i.addProcessor(new sge.InputAdapter())
    i.addProcessor(new sge.InputAdapter())
    assertEquals(i.getProcessors().size, 3)

    i.removeProcessors()
    assertEquals(i.getProcessors().size, 0)
  }

  // ---------------------------------------------------------------------------------------
  // TimedTransition — upstream `TimedScreenTransitionTest.test()` plus the interpolation and
  // clamping cases the reference hand port added.
  //
  // The clock is the interesting part and it is pure arithmetic, so it runs headless. Note the
  // upstream test asserts `isDone` remains true after a further render and false again after
  // `show()`; that pair is what catches a `timePassed` reset written into the wrong method.
  // ---------------------------------------------------------------------------------------

  /** Concrete `TimedTransition` that records what the four-argument render was handed. */
  private class RecordingTimedTransition(duration: Float, interpolation: sge.math.Interpolation)
      extends TimedTransition(duration, interpolation):
    def this(duration: Float) = this(duration, null)
    var lastProgress: Float = -1f
    var renderCount: Int    = 0
    override def render(delta: Float, last: sge.graphics.g2d.TextureRegion,
                        curr: sge.graphics.g2d.TextureRegion, progress: Float): Unit =
      lastProgress = progress
      renderCount += 1
    override def resize(width: Int, height: Int): Unit = ()
    override def dispose(): Unit                       = ()

  test("TimedTransition: isDone after the duration elapses, and show() resets it (upstream)") {
    val t = new RecordingTimedTransition(5)

    t.render(1, null, null)
    assert(!t.isDone())

    t.render(1, null, null)
    t.render(1, null, null)
    t.render(1, null, null)
    t.resize(12, 15)
    t.render(1, null, null)
    assert(t.isDone())

    t.render(1, null, null)
    assert(t.isDone())

    t.show()
    assert(!t.isDone())
  }

  test("TimedTransition: progress is timePassed/duration and clamps at 1") {
    val t = new RecordingTimedTransition(1.0f)
    t.show()
    t.render(0.25f, null, null)
    assertEqualsFloat(t.lastProgress, 0.25f, 0.001f)
    t.render(0.25f, null, null)
    assertEqualsFloat(t.lastProgress, 0.5f, 0.001f)
    // Overshoot: the four-argument render must never see a progress above 1.
    t.render(5.0f, null, null)
    assertEqualsFloat(t.lastProgress, 1.0f, 0.001f)
    assertEquals(t.renderCount, 3)
  }

  test("TimedTransition: interpolation is applied to progress, not to delta") {
    val square: sge.math.Interpolation = new sge.math.Interpolation:
      override def apply(a: Float): Float = a * a
    val t = new RecordingTimedTransition(1.0f, square)
    t.show()
    t.render(0.5f, null, null)
    // Uninterpolated this is 0.5; squared it is 0.25.
    assertEqualsFloat(t.lastProgress, 0.25f, 0.01f)
  }

  test("TimedTransition: a non-positive duration is rejected") {
    // The seam: upstream writes `Preconditions.checkArgument(duration > 0)` and this port
    // re-points that call at its own shim. The exception TYPE is what proves the redirect landed
    // on something with upstream's contract rather than on something merely named alike.
    intercept[IllegalArgumentException](new RecordingTimedTransition(0f))
    intercept[IllegalArgumentException](new RecordingTimedTransition(-1f))
  }

  // ---------------------------------------------------------------------------------------
  // SlidingDirection — a Java enum with constructor arguments, emitted as a sealed class plus
  // case objects. The factors decide which way every sliding transition moves, so a transposed
  // pair is a defect no signature check can see.
  // ---------------------------------------------------------------------------------------

  test("SlidingDirection: four directions, with the factors upstream declares") {
    assertEquals(SlidingDirection.values().length, 4)
    assertEquals(SlidingDirection.UP.xPosFactor, 0)
    assertEquals(SlidingDirection.UP.yPosFactor, 1)
    assertEquals(SlidingDirection.DOWN.xPosFactor, 0)
    assertEquals(SlidingDirection.DOWN.yPosFactor, -1)
    assertEquals(SlidingDirection.LEFT.xPosFactor, -1)
    assertEquals(SlidingDirection.LEFT.yPosFactor, 0)
    assertEquals(SlidingDirection.RIGHT.xPosFactor, 1)
    assertEquals(SlidingDirection.RIGHT.yPosFactor, 0)
  }

  test("SlidingDirection: name() and valueOf round-trip, and an unknown name throws") {
    SlidingDirection.values().foreach(d => assertEquals(SlidingDirection.valueOf(d.name()), d))
    intercept[IllegalArgumentException](SlidingDirection.valueOf("SIDEWAYS"))
  }

  // ---------------------------------------------------------------------------------------
  // The screen and transition lifecycle — pure state, so it runs headless.
  // ---------------------------------------------------------------------------------------

  private class RecordingScreen extends ManagedScreen:
    val calls = scala.collection.mutable.ArrayBuffer.empty[String]
    override def show(): Unit                          = calls += "show"
    override def hide(): Unit                          = calls += "hide"
    override def render(delta: Float): Unit            = calls += "render"
    override def resize(width: Int, height: Int): Unit = calls += "resize"
    override def pause(): Unit                         = calls += "pause"
    override def resume(): Unit                        = calls += "resume"
    override def dispose(): Unit                       = calls += "dispose"

  test("ManagedScreen: the clear colour defaults to BLACK and input processors start empty") {
    val s = new RecordingScreen
    assertEquals(s.getClearColor(), sge.graphics.Color.BLACK)
    assertEquals(s.getInputProcessors().size, 0)
    s.addInputProcessor(new sge.InputAdapter())
    assertEquals(s.getInputProcessors().size, 1)
  }

  test("ManagedScreenAdapter and BlankScreen: every lifecycle method is a no-op that returns") {
    // `ManagedScreenAdapter` and `BlankScreen` are two of the three types the reference hand port
    // does not have at all, so this is their only coverage anywhere.
    val b = new BlankScreen()
    b.show(); b.render(0.016f); b.resize(800, 600); b.pause(); b.resume(); b.hide(); b.dispose()
    val a = new ManagedScreenAdapter()
    a.show(); a.render(0.016f); a.resize(1, 1); a.hide(); a.dispose()
    assertEquals(a.getClearColor(), sge.graphics.Color.BLACK)
  }

  test("ScreenTransition: show/hide default to no-ops and the clear colour defaults to BLACK") {
    val t = new ScreenTransition:
      override def render(d: Float, l: sge.graphics.g2d.TextureRegion, c: sge.graphics.g2d.TextureRegion): Unit = ()
      override def isDone(): Boolean                     = true
      override def resize(width: Int, height: Int): Unit = ()
      override def dispose(): Unit                       = ()
    t.show()
    t.hide()
    assertEquals(t.getClearColor(), sge.graphics.Color.BLACK)
  }

  // ---------------------------------------------------------------------------------------
  // ScreenManager — the two paths that do not reach GL.
  //
  // Everything else in this type renders through a framebuffer on its first statement, so this is
  // the whole of what a headless suite can honestly assert about it. Both cases run through the
  // guacamole `Preconditions` seam, and BOTH exception types matter: a shim that folded
  // `checkState` into Scala's `require` would raise `IllegalArgumentException` here and the second
  // assertion is what says so.
  // ---------------------------------------------------------------------------------------

  test("ScreenManager: pushing a null screen is a NullPointerException") {
    val sm = new ScreenManager[ManagedScreen, ScreenTransition]()
    // The two `pushScreen` overloads are both reachable with two nulls, so the screen argument is
    // ascribed. Java resolved this by the same rule and never had to say so — an overload set that
    // is unambiguous in Java and ambiguous in Scala is a translation fact worth a line here.
    intercept[NullPointerException](sm.pushScreen(null.asInstanceOf[ManagedScreen], null))
  }

  test("ScreenManager: using it before initialize() is an IllegalStateException, not an argument error") {
    val sm = new ScreenManager[ManagedScreen, ScreenTransition]()
    intercept[IllegalStateException](sm.render(0.016f))
    intercept[IllegalStateException](sm.resize(100, 100))
    intercept[IllegalStateException](sm.pause())
    intercept[IllegalStateException](sm.resume())
  }

  test("ScreenManager: a queued push is held as a Pair of suppliers, transition first") {
    // `Supplier` is the third type the reference hand port does not have, and the `Pair` field
    // NAMES (`x`, `y`) are load-bearing — the emitted `ScreenManager` reads them directly, so a
    // `Tuple2` substitution would compile at the declaration and fail at every use.
    val screen = new RecordingScreen
    val sm     = new ScreenManager[ManagedScreen, ScreenTransition]()
    sm.pushScreen(screen, null)
    assertEquals(sm.transitionQueue.size, 1)
    val queued = sm.transitionQueue.head
    assertEquals(queued.y.get().asInstanceOf[AnyRef], screen.asInstanceOf[AnyRef])
    // A NULL transition is still WRAPPED: upstream writes `pushScreen(() -> screen, () -> transition)`
    // unconditionally, so `x` is a non-null supplier that yields null — not a null supplier. The
    // difference decides whether `render` NPEs or plays no transition, and it is invisible to
    // every count; this assertion was written the other way round first and the run said so.
    assert(queued.x != null)
    assertEquals(queued.x.get().asInstanceOf[AnyRef], null)
  }

  // ---------------------------------------------------------------------------------------
  // The guacamole shims, directly. These carry no upstream Java in this port's source set, so
  // nothing else in the pipeline says anything about them at all.
  // ---------------------------------------------------------------------------------------

  test("Preconditions: each check raises the JDK exception upstream raises") {
    import sge.screen.guacamole.Preconditions
    intercept[IllegalArgumentException](Preconditions.checkArgument(false))
    assertEquals(intercept[IllegalArgumentException](Preconditions.checkArgument(false, "why")).getMessage, "why")
    intercept[IllegalStateException](Preconditions.checkState(false))
    assertEquals(intercept[IllegalStateException](Preconditions.checkState(false, "why")).getMessage, "why")
    intercept[NullPointerException](Preconditions.checkNotNull(null))
    assertEquals(intercept[NullPointerException](Preconditions.checkNotNull(null, "why")).getMessage, "why")
    // …and passes silently when the condition holds.
    Preconditions.checkArgument(true)
    Preconditions.checkState(true)
    Preconditions.checkNotNull("x")
  }

  test("Pair: fields, equality, hash and toString match upstream") {
    import sge.screen.guacamole.Pair
    val p = new Pair[String, Integer]("a", 1)
    assertEquals(p.x, "a")
    assertEquals(p.y, Integer.valueOf(1))
    assertEquals(p, new Pair[String, Integer]("a", 1))
    assertEquals(p.hashCode(), new Pair[String, Integer]("a", 1).hashCode())
    assertNotEquals(p: Any, new Pair[String, Integer]("a", 2): Any)
    assertEquals(p.toString, "Pair{a,1}")
    // Both slots are nullable upstream, and `equals` uses `Objects.equals` on each.
    assertEquals(new Pair[String, String](null, null), new Pair[String, String](null, null))
  }

  test("ShaderCompatibilityHelper: the 120→150 rewrites, which need no GL context") {
    import sge.screen.guacamole.ShaderCompatibilityHelper as H
    assertEquals(H.toVert150("\nattribute vec4 a;\nvarying vec2 v;"), "\nin vec4 a;\nout vec2 v;")
    assertEquals(H.toFrag150("\nvarying vec2 v;"), "\nin vec2 v;")
    // `gl_FragColor` has no GLSL 150 counterpart, so an `out` declaration is injected for it.
    val frag = H.toFrag150("void main(){ gl_FragColor = texture2D(t, v); }")
    assert(frag.contains("out vec4 fragColor;"), frag)
    assert(frag.contains("fragColor = texture(t, v)"), frag)
    assert(!frag.contains("gl_FragColor"), frag)
  }

  private def assertEqualsFloat(actual: Float, expected: Float, delta: Float)(using munit.Location): Unit =
    assert(Math.abs(actual - expected) <= delta, s"expected $expected +/- $delta but got $actual")
