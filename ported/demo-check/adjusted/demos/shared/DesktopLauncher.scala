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
