/*
 * Adapted from sge's jbump WorldSuite for the machine-ported jbump API.
 *
 * Differences from the hand-port's tests:
 *   - No Nullable wrapping: the machine port uses raw references (Java semantics)
 *   - Item/World/Collisions use `new` (no companion apply)
 *   - CollisionFilter.filter takes raw Item[?] and returns raw Response
 *   - Collisions.get returns Collision directly (not Nullable[Collision])
 *   - Collision fields (item, other, type) are raw references (not Nullable)
 */
package sge
package jbump

import scala.collection.mutable.ArrayBuffer

class WorldSuite extends munit.FunSuite {

  test("add and hasItem") {
    val world = new World[String]()
    val item  = new Item[String]("block")
    world.add(item, 0, 0, 32, 32)
    assert(world.hasItem(item))
    assertEquals(world.countItems(), 1)
  }

  test("remove item") {
    val world = new World[String]()
    val item  = new Item[String]("block")
    world.add(item, 0, 0, 32, 32)
    world.remove(item)
    assert(!world.hasItem(item))
    assertEquals(world.countItems(), 0)
  }

  test("getRect returns correct rect") {
    val world = new World[String]()
    val item  = new Item[String]("block")
    world.add(item, 10, 20, 30, 40)
    val rect = world.getRect(item)
    assertEqualsFloat(rect.x, 10f, 0.001f)
    assertEqualsFloat(rect.y, 20f, 0.001f)
    assertEqualsFloat(rect.w, 30f, 0.001f)
    assertEqualsFloat(rect.h, 40f, 0.001f)
  }

  test("move with slide response stops at wall") {
    val world = new World[String](1f)
    val player = new Item[String]("player")
    world.add(player, 0, 0, 1, 1)
    val wall = new Item[String]("wall")
    world.add(wall, 3, 0, 1, 1)

    val result = world.move(player, 5f, 0f, CollisionFilter.defaultFilter)

    assertEqualsFloat(result.goalX, 2f, 0.001f)
    assertEqualsFloat(result.goalY, 0f, 0.001f)
    assertEquals(result.projectedCollisions.size(), 1)
    val col = result.projectedCollisions.get(0)
    assert(col.other eq wall, "collision other must be the wall")
    assert(col.item eq player, "collision item must be the player")
    assertEquals(col.normal.x, -1)
    assertEquals(col.normal.y, 0)
  }

  test("move with slide response allows sliding") {
    val world  = new World[String](1f)
    val player = new Item[String]("player")
    world.add(player, 0, 0, 1, 1)
    val wall = new Item[String]("wall")
    world.add(wall, 3, 0, 1, 1)

    val result = world.move(player, 5f, 2f, CollisionFilter.defaultFilter)

    assertEqualsFloat(result.goalX, 2f, 0.001f)
    assertEqualsFloat(result.goalY, 2f, 0.001f)
  }

  test("move with cross response passes through") {
    val world  = new World[String](1f)
    val player = new Item[String]("player")
    world.add(player, 0, 0, 1, 1)
    val trigger = new Item[String]("trigger")
    world.add(trigger, 2, 0, 1, 1)

    val crossFilter: CollisionFilter = new CollisionFilter {
      override def filter(item: Item[?], other: Item[?]): Response = Response.cross
    }

    val result = world.move(player, 5f, 0f, crossFilter)

    assertEqualsFloat(result.goalX, 5f, 0.001f)
    assertEqualsFloat(result.goalY, 0f, 0.001f)
    assertEquals(result.projectedCollisions.size(), 1)
    val col = result.projectedCollisions.get(0)
    assert(col.other eq trigger, "collision other must be the trigger")
    assert(col.item eq player, "collision item must be the player")
    assertEquals(col.normal.x, -1)
    assertEquals(col.normal.y, 0)
  }

  test("move with touch response stops at first contact") {
    val world  = new World[String](1f)
    val player = new Item[String]("player")
    world.add(player, 0, 0, 1, 1)
    val wall = new Item[String]("wall")
    world.add(wall, 3, 0, 1, 1)

    val touchFilter: CollisionFilter = new CollisionFilter {
      override def filter(item: Item[?], other: Item[?]): Response = Response.touch
    }

    val result = world.move(player, 5f, 0f, touchFilter)
    assertEqualsFloat(result.goalX, 2f, 0.001f)
    assertEqualsFloat(result.goalY, 0f, 0.001f)
  }

  test("queryRect finds intersecting items") {
    val world = new World[String](1f)
    val item1 = new Item[String]("a")
    world.add(item1, 0, 0, 2, 2)
    val item2 = new Item[String]("b")
    world.add(item2, 5, 5, 2, 2)
    val item3 = new Item[String]("c")
    world.add(item3, 1, 1, 2, 2)

    val items = ArrayBuffer.empty[Item[?]]
    world.queryRect(0, 0, 3, 3, CollisionFilter.defaultFilter, items)

    assert(items.contains(item1))
    assert(items.contains(item3))
    assert(!items.contains(item2))
  }

  test("queryPoint finds items containing the point") {
    val world = new World[String](1f)
    val item1 = new Item[String]("a")
    world.add(item1, 0, 0, 2, 2)
    val item2 = new Item[String]("b")
    world.add(item2, 5, 5, 2, 2)

    val items = ArrayBuffer.empty[Item[?]]
    world.queryPoint(1, 1, CollisionFilter.defaultFilter, items)

    assert(items.contains(item1))
    assert(!items.contains(item2))
  }

  test("update changes item position") {
    val world = new World[String]()
    val item  = new Item[String]("block")
    world.add(item, 0, 0, 32, 32)
    world.update(item, 100, 100)
    val rect = world.getRect(item)
    assertEqualsFloat(rect.x, 100f, 0.001f)
    assertEqualsFloat(rect.y, 100f, 0.001f)
  }

  test("reset clears the world") {
    val world = new World[String]()
    val item  = new Item[String]("block")
    world.add(item, 0, 0, 32, 32)
    world.reset()
    assertEquals(world.countItems(), 0)
    assertEquals(world.countCells(), 0)
  }

  test("multiple items and collision detection") {
    val world = new World[String](1f)
    for (i <- 0 until 10) {
      val wall = new Item[String](s"wall$i")
      world.add(wall, i.toFloat, 0, 1, 1)
    }
    val player = new Item[String]("player")
    world.add(player, 5, 1, 1, 1)

    val result = world.move(player, 5f, -1f, CollisionFilter.defaultFilter)

    assertEqualsFloat(result.goalX, 5f, 0.001f)
    assertEqualsFloat(result.goalY, 1f, 0.001f)
  }

  test("bounce response reflects movement") {
    val world  = new World[String](1f)
    val player = new Item[String]("player")
    world.add(player, 0, 0, 1, 1)
    val wall = new Item[String]("wall")
    world.add(wall, 3, 0, 1, 1)

    val bounceFilter: CollisionFilter = new CollisionFilter {
      override def filter(item: Item[?], other: Item[?]): Response = Response.bounce
    }

    val result = world.move(player, 5f, 0f, bounceFilter)

    assertEqualsFloat(result.goalX, -1f, 0.001f)
    assertEqualsFloat(result.goalY, 0f, 0.001f)
  }

  test("add returns same item if already present") {
    val world     = new World[String]()
    val item      = new Item[String]("block")
    val returned1 = world.add(item, 0, 0, 32, 32)
    val returned2 = world.add(item, 100, 100, 32, 32)
    assert(returned1 eq returned2)
    val rect = world.getRect(item)
    assertEqualsFloat(rect.x, 0f, 0.001f)
    assertEqualsFloat(rect.y, 0f, 0.001f)
  }
}
