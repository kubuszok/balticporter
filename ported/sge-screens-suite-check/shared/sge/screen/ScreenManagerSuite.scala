package sge
package screen

import scala.collection.mutable.ArrayBuffer

import sge.graphics.Color
import sge.graphics.g2d.TextureRegion
import sge.screen.transition.ScreenTransition

class ScreenManagerSuite extends munit.FunSuite {

  class TestScreen extends ManagedScreen {
    val calls: ArrayBuffer[String] = ArrayBuffer.empty

    override def show():                            Unit = calls += "show"
    override def hide():                            Unit = calls += "hide"
    override def render(delta: Float):              Unit = calls += "render"
    override def resize(width: Int, height: Int):   Unit = calls += "resize"
    override def pause():                           Unit = calls += "pause"
    override def resume():                          Unit = calls += "resume"
    override def close():                           Unit = calls += "close"
  }

  class TestTransition(rendersUntilDone: Int = 2) extends ScreenTransition {
    val calls: ArrayBuffer[String] = ArrayBuffer.empty
    private var renderCount = 0

    override def show():                                                                    Unit = calls += "show"
    override def hide():                                                                    Unit = calls += "hide"
    override def render(delta: Float, lastScreen: TextureRegion, currScreen: TextureRegion): Unit = {
      calls += "render"
      renderCount += 1
    }
    override def done:                              Boolean = renderCount >= rendersUntilDone
    override def resize(width: Int, height: Int):   Unit    = calls += "resize"
    override def close():                           Unit    = calls += "close"
  }

  test("ManagedScreen has default clearColor of BLACK") {
    val screen = new TestScreen
    assertEquals(screen.clearColor.get, Color.BLACK)
  }

  test("ManagedScreen inputProcessors starts empty") {
    val screen = new TestScreen
    assertEquals(screen.inputProcessors.size, 0)
  }

  test("BlankScreen does nothing") {
    val screen = new BlankScreen()
    screen.show()
    screen.render(0.016f)
    screen.resize(800, 600)
    screen.pause()
    screen.resume()
    screen.hide()
    screen.close()
  }

  test("ScreenTransition default show and hide are no-ops") {
    val transition = new TestTransition
    transition.show()
    transition.hide()
    assert(transition.calls.contains("show"))
    assert(transition.calls.contains("hide"))
  }

  test("ScreenTransition default clearColor is BLACK") {
    val transition = new TestTransition
    assertEquals(transition.clearColor.get, Color.BLACK)
  }
}
