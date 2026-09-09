package sge
package ecs

import scala.collection.mutable.ArrayBuffer

import sge.ecs.signals.{ Listener, Signal }
import sge.ecs.utils.ImmutableArray
import lowlevel.Nullable
import sge.utils.Pool

// Top-level classes with public no-arg constructors for reflection-based createComponent
class PooledPositionComponent extends Component {
  var x: Float = 0.0f
  var y: Float = 0.0f
}

class PooledComponentA extends Component

class PoolableComponent extends Component with Pool.Poolable {
  var wasReset:         Boolean = true
  override def reset(): Unit    =
    wasReset = true
}

class PooledComponentSpy extends Component with Pool.Poolable {
  var recycled:         Boolean = false
  override def reset(): Unit    =
    recycled = true
}

// JS/Native version: tests that call createComponent are excluded because the ported
// ComponentFactories.create uses JVM reflection (java.lang.reflect.Constructor) as a fallback,
// which the Scala.js/Native linker cannot resolve. Full test coverage runs on JVM.
class PooledEngineSuite extends munit.FunSuite {

  private val deltaTime: Float = 0.16f

  test("createEntity returns pooled entity") {
    val engine = new PooledEngine
    val entity = engine.createEntity()
    // A freshly pooled entity carries no components (PooledEngine.createEntity -> pool.obtain).
    assertEquals(entity.getComponents.size, 0)
  }

  test("recycleEntity: removed entities are reused") {
    val numEntities = 5
    val engine      = new PooledEngine(numEntities, 100, 0, 100)
    val entities    = ArrayBuffer[Entity]()

    for (_ <- 0 until numEntities) {
      val entity = engine.createEntity()
      assert(!entity.removing)
      assertEquals(entity.flags, 0)
      engine.addEntity(entity)
      entities += entity
      entity.flags = 1
    }

    for (entity <- entities) {
      engine.removeEntity(entity)
      assertEquals(entity.flags, 0)
      assert(!entity.removing)
    }

    for (_ <- 0 until numEntities) {
      val entity = engine.createEntity()
      assertEquals(entity.flags, 0)
      assert(!entity.removing)
      assert(entities.contains(entity))
    }
  }

  test("remove entity twice does not crash") {
    val engine = new PooledEngine

    for (_ <- 0 until 10) {
      val entities = ArrayBuffer[Entity]()

      for (_ <- 0 until 10) {
        val entity = engine.createEntity()
        engine.addEntity(entity)
        assertEquals(entity.flags, 0)
        entity.flags = 1
        entities += entity
      }

      for (entity <- entities) {
        engine.removeEntity(entity)
        engine.removeEntity(entity)
      }
    }
  }

  test("clearPools") {
    val engine = new PooledEngine

    for (_ <- 0 until 5) {
      val entity = engine.createEntity()
      engine.addEntity(entity)
    }

    engine.removeAllEntities()
    engine.clearPools()

    // After clearing, new entities should be fresh (not recycled from pool)
    // This is a smoke test -- just verify a fresh, component-less entity is produced
    val entity = engine.createEntity()
    assertEquals(entity.getComponents.size, 0)
  }
}
