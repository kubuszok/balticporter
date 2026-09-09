/*
 * Adapted from sge's jbump JbumpNullProjectRedSuite for the machine-ported jbump API.
 *
 * Tests that World.project works correctly when passed a null item, matching
 * the original Java behavior (World.java:240-280 supports item == null).
 *
 * Differences from the hand-port's tests:
 *   - Passes raw `null` instead of `Nullable.Null`
 *   - CollisionFilter.filter receives raw (possibly-null) Item[?]
 *   - Collision.item is a raw reference (null when item was null)
 *   - Collisions.get returns Collision directly
 */
package sge
package jbump

import scala.collection.mutable.ArrayBuffer

class NullProjectSuite extends munit.FunSuite {

  test("project with null item returns collisions like Java (Collisions.add must accept null item)") {
    val world = new World[String](1f)
    val wall  = new Item[String]("wall")
    world.add(wall, 3, 0, 1, 1)

    val collisions = new Collisions()
    val result     = world.project(null, 2.5f, 0f, 1f, 1f, 2.5f, 0f, collisions)

    assertEquals(result.size(), 1)
    val col = result.get(0)
    assert(col.other eq wall)
    // Java stores the null item; the read-back collision has item == null.
    assert(col.item eq null)
    assert(col.overlaps)
    assertEqualsFloat(col.ti, -0.5f, 0.001f)
    assertEquals(col.normal.x, -1)
    assertEquals(col.normal.y, 0)
    assertEqualsFloat(col.touch.x, 2f, 0.001f)
    assertEqualsFloat(col.touch.y, 0f, 0.001f)
    assertEqualsFloat(col.move.x, 0f, 0.001f)
    assertEqualsFloat(col.move.y, 0f, 0.001f)
    assert(col.`type` eq Response.slide)
  }

  test("project with null item invokes a user filter that safely inspects the item param") {
    val world = new World[String](1f)
    val wall  = new Item[String]("wall")
    world.add(wall, 3, 0, 1, 1)

    val received = ArrayBuffer.empty[Option[Item[?]]]
    val inspectingFilter: CollisionFilter = new CollisionFilter {
      override def filter(item: Item[?], other: Item[?]): Response = {
        received += Option(item) // null becomes None
        Response.slide
      }
    }

    val collisions = new Collisions()
    val result     = world.project(null, 2.5f, 0f, 1f, 1f, 2.5f, 0f, inspectingFilter, collisions)

    // The filter was invoked exactly once, with a null item.
    assertEquals(received.size, 1)
    assertEquals(received.head, None)
    assertEquals(result.size(), 1)
    assert(result.get(0).other eq wall)
  }

  test("project with non-null item (control)") {
    val world  = new World[String](1f)
    val player = new Item[String]("player")
    world.add(player, 0, 0, 1, 1)
    val wall = new Item[String]("wall")
    world.add(wall, 3, 0, 1, 1)

    val collisions = new Collisions()
    val result     = world.project(player, 0f, 0f, 1f, 1f, 5f, 0f, collisions)

    assertEquals(result.size(), 1)
    val col = result.get(0)
    assert(col.item eq player)
    assert(col.other eq wall)
    assert(!col.overlaps)
    assertEqualsFloat(col.ti, 0.4f, 0.001f)
    assertEquals(col.normal.x, -1)
    assertEquals(col.normal.y, 0)
    assertEqualsFloat(col.touch.x, 2f, 0.001f)
    assertEqualsFloat(col.touch.y, 0f, 0.001f)
    assert(col.`type` eq Response.slide)
  }
}
