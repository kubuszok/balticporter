/*
 * SGE Demos — shared desktop launcher for JVM and Scala Native.
 * Copyright 2025-2026 Mateusz Kubuszok
 *
 * ADJUSTED COPY (ported/demo-check/ADJUSTMENTS.tsv): the `demo-run` lane sets `-Dsge.demo.frames=N`
 * and the launcher exits after N rendered frames, printing `DEMO-RUN-FRAMES N`. Without the property
 * the launcher is sge's own.
 */
package demos.shared

import sge.{ ApplicationListener, DesktopApplicationConfig, DesktopApplicationFactory, Sge }

/** Creates a desktop window and runs a [[DemoScene]] until the user closes it.
  *
  * Shared between JVM (Panama FFM) and Scala Native (@extern C FFI) platforms. The platform-specific `DesktopApplicationFactory` is resolved at link time.
  */
object DesktopLauncher {

  /** Runs the scene for a fixed number of frames, then asks the application to exit. */
  final class FrameBudgetApp(scene: DemoScene, budget: Int)(using sge: Sge) extends SingleSceneApp(scene) {
    private var frames = 0
    override def render(): Unit = {
      super.render()
      frames += 1
      if (frames >= budget) {
        println(s"DEMO-RUN-FRAMES $frames")
        sge.application.exit()
      }
    }
  }

  /** The same budget around any ApplicationListener (demos with their own main, e.g. game-screens). */
  final class FrameBudgetListener(inner: ApplicationListener, budget: Int)(using sge: Sge) extends ApplicationListener {
    private var frames = 0
    override def create(): Unit = inner.create()
    override def resize(width: _root_.sge.Pixels, height: _root_.sge.Pixels): Unit = inner.resize(width, height)
    override def render(): Unit = {
      inner.render()
      frames += 1
      if (frames >= budget) {
        println(s"DEMO-RUN-FRAMES $frames")
        sge.application.exit()
      }
    }
    override def pause(): Unit = inner.pause()
    override def resume(): Unit = inner.resume()
    override def dispose(): Unit = inner.dispose()
  }

  /** Wraps `app` in the budget when `-Dsge.demo.frames` is set; otherwise `app` itself. */
  def budgeted(app: Sge ?=> ApplicationListener): Sge ?=> ApplicationListener =
    sys.props.get("sge.demo.frames").map(_.trim.toInt) match {
      case Some(n) => new FrameBudgetListener(app, n)
      case None    => app
    }

  def launch(scene: DemoScene, title: String, width: Int = 800, height: Int = 600): Unit = {
    val config = DesktopApplicationConfig()
    config.title = title
    config.windowWidth = width
    config.windowHeight = height
    config.foregroundFPS = 60
    val budget = sys.props.get("sge.demo.frames").map(_.trim.toInt)
    val app: Sge ?=> ApplicationListener = budget match {
      case Some(n) => new FrameBudgetApp(scene, n)
      case None    => new SingleSceneApp(scene)
    }
    DesktopApplicationFactory(app, config)
  }
}
