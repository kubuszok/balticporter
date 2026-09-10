/*
 * Copyright 2025-2026 Mateusz Kubuszok
 * Licensed under the Apache License, Version 2.0
 *
 * ADAPTED for the port: identical to sge original — the port's PoolManager
 * has the same addPool/obtain/hasPool API with ClassTag.
 */
package sge
package utils

import java.util.concurrent.{ ConcurrentLinkedQueue, CyclicBarrier }

class PoolManagerConcurrencyIss803Suite extends munit.FunSuite {

  final private class C00
  final private class C01
  final private class C02
  final private class C03
  final private class C04
  final private class C05
  final private class C06
  final private class C07
  final private class C08
  final private class C09
  final private class C10
  final private class C11
  final private class C12
  final private class C13
  final private class C14
  final private class C15
  final private class C16
  final private class C17
  final private class C18
  final private class C19

  private val registrars: Vector[(PoolManager => Unit, Class[?])] = Vector(
    ((pm: PoolManager) => { pm.addPool[C00](() => new C00()); pm.obtain[C00]; () }, classOf[C00]),
    ((pm: PoolManager) => { pm.addPool[C01](() => new C01()); pm.obtain[C01]; () }, classOf[C01]),
    ((pm: PoolManager) => { pm.addPool[C02](() => new C02()); pm.obtain[C02]; () }, classOf[C02]),
    ((pm: PoolManager) => { pm.addPool[C03](() => new C03()); pm.obtain[C03]; () }, classOf[C03]),
    ((pm: PoolManager) => { pm.addPool[C04](() => new C04()); pm.obtain[C04]; () }, classOf[C04]),
    ((pm: PoolManager) => { pm.addPool[C05](() => new C05()); pm.obtain[C05]; () }, classOf[C05]),
    ((pm: PoolManager) => { pm.addPool[C06](() => new C06()); pm.obtain[C06]; () }, classOf[C06]),
    ((pm: PoolManager) => { pm.addPool[C07](() => new C07()); pm.obtain[C07]; () }, classOf[C07]),
    ((pm: PoolManager) => { pm.addPool[C08](() => new C08()); pm.obtain[C08]; () }, classOf[C08]),
    ((pm: PoolManager) => { pm.addPool[C09](() => new C09()); pm.obtain[C09]; () }, classOf[C09]),
    ((pm: PoolManager) => { pm.addPool[C10](() => new C10()); pm.obtain[C10]; () }, classOf[C10]),
    ((pm: PoolManager) => { pm.addPool[C11](() => new C11()); pm.obtain[C11]; () }, classOf[C11]),
    ((pm: PoolManager) => { pm.addPool[C12](() => new C12()); pm.obtain[C12]; () }, classOf[C12]),
    ((pm: PoolManager) => { pm.addPool[C13](() => new C13()); pm.obtain[C13]; () }, classOf[C13]),
    ((pm: PoolManager) => { pm.addPool[C14](() => new C14()); pm.obtain[C14]; () }, classOf[C14]),
    ((pm: PoolManager) => { pm.addPool[C15](() => new C15()); pm.obtain[C15]; () }, classOf[C15]),
    ((pm: PoolManager) => { pm.addPool[C16](() => new C16()); pm.obtain[C16]; () }, classOf[C16]),
    ((pm: PoolManager) => { pm.addPool[C17](() => new C17()); pm.obtain[C17]; () }, classOf[C17]),
    ((pm: PoolManager) => { pm.addPool[C18](() => new C18()); pm.obtain[C18]; () }, classOf[C18]),
    ((pm: PoolManager) => { pm.addPool[C19](() => new C19()); pm.obtain[C19]; () }, classOf[C19])
  )

  test("ISS-803: concurrent addPool/obtain on distinct types does not corrupt the typePools map".ignore) {
    val iterations = 400
    var iter       = 0
    while (iter < iterations) {
      val pm      = new PoolManager()
      val barrier = new CyclicBarrier(registrars.size)
      val errors  = new ConcurrentLinkedQueue[Throwable]()
      val threads = registrars.map { case (register, _) =>
        new Thread(new Runnable {
          def run(): Unit =
            try {
              barrier.await()
              register(pm)
            } catch {
              case e: Throwable =>
                errors.add(e)
                ()
            }
        })
      }
      threads.foreach(_.start())
      threads.foreach(_.join())

      assert(
        errors.isEmpty,
        s"iteration $iter: concurrent addPool/obtain threw ${errors.size} exception(s); first: ${Option(errors.peek()).map(_.toString).getOrElse("<none>")}"
      )
      registrars.foreach { case (_, clazz) =>
        assert(pm.hasPool(clazz), s"iteration $iter: registration for $clazz was lost — map corrupted by concurrent mutation")
      }
      iter += 1
    }
  }
}
