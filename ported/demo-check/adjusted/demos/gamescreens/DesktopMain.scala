/*
 * ADJUSTED COPY of sge's demos/game-screens/src/main/scaladesktop/demos/gamescreens/DesktopMain.scala
 * (ported/demo-check/ADJUSTMENTS.tsv): the listener goes through DesktopLauncher.budgeted so the
 * demo-run lane's frame budget applies to a demo that does not use DesktopLauncher.launch.
 */
package demos.gamescreens

import demos.shared.DesktopLauncher
import sge.{ ApplicationListener, DesktopApplicationConfig, DesktopApplicationFactory, Sge }

object DesktopMain {
  def main(args: Array[String]): Unit = {
    val config = DesktopApplicationConfig()
    config.title = "SGE Game/Screen"
    config.windowWidth = 800
    config.windowHeight = 600
    config.foregroundFPS = 60
    val app: Sge ?=> ApplicationListener = new GameScreensDemo()
    DesktopApplicationFactory(DesktopLauncher.budgeted(app), config)
  }
}
