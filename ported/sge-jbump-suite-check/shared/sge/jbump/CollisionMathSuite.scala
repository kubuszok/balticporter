/*
 * Adapted from sge's jbump CollisionMathSuite for the machine-ported jbump API.
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

class CollisionMathSuite extends munit.FunSuite {

  private val eps = 1e-4f

  // ---------------------------------------------------------------------------
  // project() directly
  // ---------------------------------------------------------------------------

  test("project: single tunneling collision pins touch, normal, ti (RectHelper tunnel branch)") {
    val world  = new World[String](64f)
    val player = new Item[String]("player")
    world.add(player, 0, 0, 1, 1)
    val wall = new Item[String]("wall")
    world.add(wall, 3, 0, 1, 1)

    val cols = new Collisions()
    world.project(player, 0, 0, 1, 1, 5, 0, CollisionFilter.defaultFilter, cols)

    assertEquals(cols.size(), 1)
    val col = cols.get(0)
    assertEquals(col.other, wall)
    assert(!col.overlaps)
    assertEqualsFloat(col.ti, 0.4f, eps)
    assertEqualsFloat(col.touch.x, 2.0f, eps)
    assertEqualsFloat(col.touch.y, 0.0f, eps)
    assertEquals(col.normal.x, -1)
    assertEquals(col.normal.y, 0)
    assertEqualsFloat(col.move.x, 5.0f, eps)
    assertEqualsFloat(col.move.y, 0.0f, eps)
  }

  test("project: two walls returned sorted by ti ascending (World.java:276-278 collisions.sort)") {
    val world  = new World[String](64f)
    val player = new Item[String]("player")
    world.add(player, 0, 0, 1, 1)
    val wall1 = new Item[String]("wall1")
    world.add(wall1, 3, 0, 1, 1)
    val wall2 = new Item[String]("wall2")
    world.add(wall2, 2, 0, 1, 1)

    val cols = new Collisions()
    world.project(player, 0, 0, 1, 1, 5, 0, CollisionFilter.defaultFilter, cols)

    assertEquals(cols.size(), 2)
    // Collisions.get reuses a single shared Collision instance, so capture
    // each row's values before requesting the next index.
    val c0       = cols.get(0)
    val c0Other  = c0.other
    val c0Ti     = c0.ti
    val c0TouchX = c0.touch.x
    val c1       = cols.get(1)
    val c1Other  = c1.other
    val c1Ti     = c1.ti
    val c1TouchX = c1.touch.x
    assertEquals(c0Other, wall2)
    assertEqualsFloat(c0Ti, 0.2f, eps)
    assertEqualsFloat(c0TouchX, 1.0f, eps)
    assertEquals(c1Other, wall1)
    assertEqualsFloat(c1Ti, 0.4f, eps)
    assertEqualsFloat(c1TouchX, 2.0f, eps)
  }

  test("project: already-overlapping & not moving -> overlaps, ti=-area, MDV normal (RectHelper.java:114-128)") {
    val world  = new World[String](64f)
    val player = new Item[String]("player")
    world.add(player, 0, 0, 2, 2)
    val other = new Item[String]("other")
    world.add(other, 1, 1, 2, 2)

    val cols = new Collisions()
    world.project(player, 0, 0, 2, 2, 0, 0, CollisionFilter.defaultFilter, cols)

    assertEquals(cols.size(), 1)
    val col = cols.get(0)
    assert(col.overlaps)
    assertEqualsFloat(col.ti, -1.0f, eps)
    assertEquals(col.normal.x, 0)
    assertEquals(col.normal.y, -1)
    assertEqualsFloat(col.touch.x, 0.0f, eps)
    assertEqualsFloat(col.touch.y, -1.0f, eps)
  }

  test("project: filter returning null skips that item (World.java:264-265)") {
    val world  = new World[String](64f)
    val player = new Item[String]("player")
    world.add(player, 0, 0, 1, 1)
    val wall1 = new Item[String]("wall1")
    world.add(wall1, 3, 0, 1, 1)
    val wall2 = new Item[String]("wall2")
    world.add(wall2, 2, 0, 1, 1)

    val skipWall1: CollisionFilter = new CollisionFilter {
      override def filter(item: Item[?], other: Item[?]): Response =
        if ((other ne null) && (other eq wall1)) null else Response.slide
    }

    val cols = new Collisions()
    world.project(player, 0, 0, 1, 1, 5, 0, skipWall1, cols)

    assertEquals(cols.size(), 1)
    assertEquals(cols.get(0).other, wall2)
  }

  // ---------------------------------------------------------------------------
  // Response geometric math
  // ---------------------------------------------------------------------------

  private def firstCollision(
    world:  World[String],
    player: Item[String],
    px:     Float,
    py:     Float,
    pw:     Float,
    ph:     Float,
    goalX:  Float,
    goalY:  Float
  ): Collision = {
    val cols = new Collisions()
    world.project(player, px, py, pw, ph, goalX, goalY, CollisionFilter.defaultFilter, cols)
    cols.get(0)
  }

  test("Response.bounce reflects across X normal -> goal (-1,0) (Response.java:82-108)") {
    val world  = new World[String](64f)
    val player = new Item[String]("player")
    world.add(player, 0, 0, 1, 1)
    val wall = new Item[String]("wall")
    world.add(wall, 3, 0, 1, 1)

    val col    = firstCollision(world, player, 0, 0, 1, 1, 5, 0)
    val result = new Response.Result()
    Response.bounce.response(world, col, 0, 0, 1, 1, 5, 0, CollisionFilter.defaultFilter, result)

    assertEqualsFloat(result.goalX, -1.0f, eps)
    assertEqualsFloat(result.goalY, 0.0f, eps)
    assertEquals(result.projectedCollisions.size(), 0)
  }

  test("Response.slide keeps goalY because normal.x != 0 (Response.java:38-61)") {
    val world  = new World[String](64f)
    val player = new Item[String]("player")
    world.add(player, 0, 0, 1, 1)
    val wall = new Item[String]("wall")
    world.add(wall, 3, 0, 1, 1)

    val col = firstCollision(world, player, 0, 0, 1, 1, 5, 2)
    assertEqualsFloat(col.touch.x, 2.0f, eps)
    assertEqualsFloat(col.touch.y, 0.8f, eps)
    assertEquals(col.normal.x, -1)

    val result = new Response.Result()
    Response.slide.response(world, col, 0, 0, 1, 1, 5, 2, CollisionFilter.defaultFilter, result)

    assertEqualsFloat(result.goalX, 2.0f, eps)
    assertEqualsFloat(result.goalY, 2.0f, eps)
  }

  test("Response.touch sets goal exactly to touch point and clears collisions (Response.java:63-70)") {
    val world  = new World[String](64f)
    val player = new Item[String]("player")
    world.add(player, 0, 0, 1, 1)
    val wall = new Item[String]("wall")
    world.add(wall, 3, 0, 1, 1)

    val col    = firstCollision(world, player, 0, 0, 1, 1, 5, 0)
    val result = new Response.Result()
    Response.touch.response(world, col, 0, 0, 1, 1, 5, 0, CollisionFilter.defaultFilter, result)

    assertEqualsFloat(result.goalX, 2.0f, eps)
    assertEqualsFloat(result.goalY, 0.0f, eps)
    assertEquals(result.projectedCollisions.size(), 0)
  }

  test("Response.cross passes through to the original goal (Response.java:72-80)") {
    val world  = new World[String](64f)
    val player = new Item[String]("player")
    world.add(player, 0, 0, 1, 1)
    val wall = new Item[String]("wall")
    world.add(wall, 3, 0, 1, 1)

    val crossFilter: CollisionFilter = new CollisionFilter {
      override def filter(item: Item[?], other: Item[?]): Response = Response.cross
    }
    val col    = firstCollision(world, player, 0, 0, 1, 1, 5, 0)
    val result = new Response.Result()
    Response.cross.response(world, col, 0, 0, 1, 1, 5, 0, crossFilter, result)

    assertEqualsFloat(result.goalX, 5.0f, eps)
    assertEqualsFloat(result.goalY, 0.0f, eps)
  }

  // ---------------------------------------------------------------------------
  // querySegment / querySegmentWithCoords
  // ---------------------------------------------------------------------------

  test("querySegment returns hits sorted by distance along the segment (World.java:199 sort)") {
    val world = new World[String](64f)
    val itemA = new Item[String]("A")
    world.add(itemA, 0, 0, 1, 1)
    val itemB = new Item[String]("B")
    world.add(itemB, 3, 0, 1, 1)

    val items = ArrayBuffer.empty[Item[?]]
    world.querySegment(-1, 0.5f, 5, 0.5f, CollisionFilter.defaultFilter, items)

    assertEquals(items.size, 2)
    assertEquals(items(0), itemA)
    assertEquals(items(1), itemB)
  }

  test("querySegmentWithCoords pins entry/exit ti fractions and coords (World.java:542-560)") {
    val world = new World[String](64f)
    val itemA = new Item[String]("A")
    world.add(itemA, 0, 0, 1, 1)
    val itemB = new Item[String]("B")
    world.add(itemB, 3, 0, 1, 1)

    val infos = ArrayBuffer.empty[ItemInfo]
    world.querySegmentWithCoords(-1, 0.5f, 5, 0.5f, CollisionFilter.defaultFilter, infos)

    assertEquals(infos.size, 2)
    val a = infos(0)
    val b = infos(1)
    assertEquals(a.item, itemA)
    assertEquals(b.item, itemB)

    assertEqualsFloat(a.ti1, 1f / 6f, eps)
    assertEqualsFloat(a.ti2, 1f / 3f, eps)
    assertEqualsFloat(a.x1, 0.0f, eps)
    assertEqualsFloat(a.y1, 0.5f, eps)
    assertEqualsFloat(a.x2, 1.0f, eps)
    assertEqualsFloat(a.y2, 0.5f, eps)

    assertEqualsFloat(b.ti1, 2f / 3f, eps)
    assertEqualsFloat(b.ti2, 5f / 6f, eps)
    assertEqualsFloat(b.x1, 3.0f, eps)
    assertEqualsFloat(b.x2, 4.0f, eps)
    assertEqualsFloat(b.y1, 0.5f, eps)
    assertEqualsFloat(b.y2, 0.5f, eps)
  }

  test("querySegment filter returning null skips that item (World.java:178/210)") {
    val world = new World[String](64f)
    val itemA = new Item[String]("A")
    world.add(itemA, 0, 0, 1, 1)
    val itemB = new Item[String]("B")
    world.add(itemB, 3, 0, 1, 1)

    val skipB: CollisionFilter = new CollisionFilter {
      override def filter(item: Item[?], other: Item[?]): Response =
        if ((item ne null) && (item eq itemB)) null else Response.slide
    }

    val items = ArrayBuffer.empty[Item[?]]
    world.querySegment(-1, 0.5f, 5, 0.5f, skipB, items)

    assertEquals(items.size, 1)
    assertEquals(items(0), itemA)
  }
}
