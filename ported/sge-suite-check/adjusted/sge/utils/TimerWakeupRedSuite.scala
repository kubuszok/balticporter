/*
 * Copyright 2025-2026 Mateusz Kubuszok
 * Licensed under the Apache License, Version 2.0
 *
 * ADAPTED for the port: PumpApplication extends JavaLoggingApplication
 * (port's Application requires applicationLogger surface).
 * Red tests for ISS-504 (Timer lost wakeup). See sge original for full design notes.
 */
package sge
package utils

import java.util.concurrent.{ CountDownLatch, TimeUnit }
import java.util.concurrent.atomic.AtomicLong

import scala.collection.mutable.ArrayBuffer

class TimerWakeupRedSuite extends munit.FunSuite {

  final private class PumpApplication extends JavaLoggingApplication {
    private val queue = ArrayBuffer.empty[Runnable]

    def drainAndRun(): Unit = {
      val toRun = queue.synchronized {
        val copy = queue.toList
        queue.clear()
        copy
      }
      toRun.foreach(_.run())
    }

    def applicationListener:              ApplicationListener         = throw new UnsupportedOperationException
    def graphics:                         Graphics                    = throw new UnsupportedOperationException
    def audio:                            Audio                       = throw new UnsupportedOperationException
    def input:                            Input                       = throw new UnsupportedOperationException
    def files:                            Files                       = throw new UnsupportedOperationException
    def net:                              Net                         = throw new UnsupportedOperationException
    def applicationType:                  Application.ApplicationType = Application.ApplicationType.HeadlessDesktop
    def version:                          Int                         = 0
    def javaHeap:                         Long                        = 0L
    def nativeHeap:                       Long                        = 0L
    def getPreferences(name: String):     Preferences                 = throw new UnsupportedOperationException
    def clipboard:                        Clipboard                   = throw new UnsupportedOperationException
    def postRunnable(runnable: Runnable): Unit                        = queue.synchronized {
      queue += runnable
      ()
    }
    def exit():                                               Unit = ()
    def addLifecycleListener(listener:    LifecycleListener): Unit = ()
    def removeLifecycleListener(listener: LifecycleListener): Unit = ()
  }

  private def pumpUntil(app: PumpApplication, latch: CountDownLatch, timeoutMillis: Long): Boolean = {
    val deadlineNanos = System.nanoTime() + timeoutMillis * 1000000L
    var opened        = latch.getCount == 0L
    while (!opened && System.nanoTime() < deadlineNanos) {
      app.drainAndRun()
      opened = latch.await(10L, TimeUnit.MILLISECONDS)
    }
    app.drainAndRun()
    if (!opened) opened = latch.getCount == 0L
    opened
  }

  test("ISS-504 red: one-shot task scheduled while the loop idles fires ~0.1s later, not after the 5s idle cap".ignore) {
    Timer.disposeThread()
    val app   = new PumpApplication
    given Sge = SgeTestFixture.testSge(application = app)
    try {
      val timer = new Timer()

      val dummyFired = new CountDownLatch(1)
      timer.scheduleTask(new Timer.Task {
        def run(): Unit = dummyFired.countDown()
      })
      assert(pumpUntil(app, dummyFired, 12000L), "setup: zero-delay dummy task never fired — timer loop appears dead")

      Thread.sleep(250L)

      val fireNanos = new AtomicLong(0L)
      val fired     = new CountDownLatch(1)
      val t0        = System.nanoTime()
      timer.scheduleTask(
        new Timer.Task {
          def run(): Unit = {
            fireNanos.set(System.nanoTime())
            fired.countDown()
          }
        },
        delaySeconds = Seconds(0.1f)
      )
      val firedInTime = pumpUntil(app, fired, 15000L)
      if (!firedInTime) {
        val elapsedMillis = (System.nanoTime() - t0) / 1000000L
        fail(
          s"task scheduled with 0.1s delay had not fired after ${elapsedMillis}ms — " +
            "lost wakeup: scheduleTask's notifyAll has no matching wait, the loop slept out its 5s idle cap"
        )
      }
      val latencyMillis = (fireNanos.get() - t0) / 1000000L
      assert(
        latencyMillis < 4500L,
        s"task scheduled with 0.1s delay fired after ${latencyMillis}ms (expected ~100ms, bound 4500ms) — lost wakeup"
      )
    } finally
      Timer.disposeThread()
  }

  test("control: consecutive firings of a repeating task honour the interval (loop machinery works)".ignore) {
    Timer.disposeThread()
    val app   = new PumpApplication
    given Sge = SgeTestFixture.testSge(application = app)
    try {
      val timer      = new Timer()
      val fireTimes  = ArrayBuffer.empty[Long]
      val twiceFired = new CountDownLatch(2)
      timer.scheduleTask(
        new Timer.Task {
          def run(): Unit = {
            fireTimes.synchronized {
              fireTimes += System.nanoTime()
              ()
            }
            twiceFired.countDown()
          }
        },
        delaySeconds = Seconds.zero,
        intervalSeconds = Seconds(0.2f),
        repeatCount = 1
      )
      assert(pumpUntil(app, twiceFired, 15000L), "control setup: repeating task did not fire twice within 15s")
      val gapMillis = fireTimes.synchronized((fireTimes(1) - fireTimes(0)) / 1000000L)
      assert(
        gapMillis < 1500L,
        s"control: inter-firing gap of a 0.2s-interval task was ${gapMillis}ms (expected ~200ms, bound 1500ms)"
      )
    } finally
      Timer.disposeThread()
  }
}
