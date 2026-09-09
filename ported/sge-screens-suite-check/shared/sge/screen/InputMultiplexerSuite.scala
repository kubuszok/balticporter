package sge
package screen

import sge.screen.utils.BasicInputMultiplexer

class InputMultiplexerSuite extends munit.FunSuite {

  test("addProcessor, removeProcessor, clear") {
    val mult = new BasicInputMultiplexer()
    assertEquals(mult.size, 0)

    val p1 = new InputAdapter()
    val p2 = new InputAdapter()
    mult.addProcessor(p1)
    mult.addProcessor(p2)
    assertEquals(mult.size, 2)

    mult.removeProcessor(p1)
    mult.removeProcessor(p2)
    assertEquals(mult.size, 0)

    mult.addProcessor(new InputAdapter())
    mult.addProcessor(new InputAdapter())
    mult.addProcessor(new InputAdapter())
    assertEquals(mult.size, 3)

    mult.clear()
    assertEquals(mult.size, 0)
  }
}
