/*
 * Adapted from sge's jbump CollisionsCopyCtorRedSuite for the machine-ported jbump API.
 *
 * The copy constructor `Collisions(Collisions other)` exists in the machine port as
 * `def this(other: Collisions)`. This suite verifies it works correctly.
 *
 * Differences from the hand-port's tests:
 *   - No Nullable wrapping: item/other/type are raw references
 *   - `new Collisions(src)` instead of `Collisions(src)`
 *   - Collisions.get returns Collision directly
 */
package sge
package jbump

class CollisionsCopyCtorSuite extends munit.FunSuite {

  test("copy constructor duplicates all struct-of-arrays and reference lists (Collisions.java:59-80)") {
    val wall = new Item[String]("wall")
    val src  = new Collisions()
    src.add(
      true,            // overlap
      0.5f,            // ti
      1f,              // moveX
      2f,              // moveY
      -1,              // normalX
      0,               // normalY
      3f,              // touchX
      4f,              // touchY
      10f,             // x1
      11f,             // y1
      12f,             // w1
      13f,             // h1
      20f,             // x2
      21f,             // y2
      22f,             // w2
      23f,             // h2
      wall,            // item (raw reference)
      null,            // other (null, not Nullable.Null)
      Response.slide   // type (raw Response)
    )

    val copy = new Collisions(src)

    assertEquals(copy.size(), src.size())
    assertEquals(copy.size(), 1)

    val col = copy.get(0)
    assert(col.overlaps)
    assertEqualsFloat(col.ti, 0.5f, 0.001f)
    assertEqualsFloat(col.move.x, 1f, 0.001f)
    assertEqualsFloat(col.move.y, 2f, 0.001f)
    assertEquals(col.normal.x, -1)
    assertEquals(col.normal.y, 0)
    assertEqualsFloat(col.touch.x, 3f, 0.001f)
    assertEqualsFloat(col.touch.y, 4f, 0.001f)
    assert(col.item eq wall)
    assert(col.other eq null)
    assert(col.`type` eq Response.slide)

    // The copy owns independent backing buffers: clearing it must not disturb the source.
    copy.clear()
    assertEquals(copy.size(), 0)
    assertEquals(src.size(), 1)
  }
}
