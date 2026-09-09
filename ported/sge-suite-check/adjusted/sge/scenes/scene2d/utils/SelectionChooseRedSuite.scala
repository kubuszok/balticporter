/*
 * Copyright 2025-2026 Mateusz Kubuszok
 * Licensed under the Apache License, Version 2.0
 *
 * Adapted from sge's SelectionChooseRedSuite:
 *   override protected def changed() -> override def changed()
 *   (port widened Selection.changed to public)
 */
package sge
package scenes
package scene2d
package utils

import lowlevel.Nullable

class SelectionChooseRedSuite extends munit.FunSuite {

  final private class CountingSelection(using Sge) extends Selection[String] {
    var changedCalls:       Int  = 0
    override def changed(): Unit =
      changedCalls += 1
  }

  final private class CtrlHeldInput extends Input {
    private val delegate = new sge.noop.NoopInput
    export delegate.{ isKeyPressed as _, * }
    override def isKeyPressed(key: Input.Key): Boolean =
      key == Input.Keys.SYM || key == Input.Keys.CONTROL_LEFT || key == Input.Keys.CONTROL_RIGHT
  }

  final private class Harness(ctrlHeld: Boolean) {
    given sge: Sge =
      if (ctrlHeld) SgeTestFixture.testSge(input = new CtrlHeldInput)
      else SgeTestFixture.testSge()
    val selection:    CountingSelection = new CountingSelection
    val actor:        Actor             = new Actor()
    var changeEvents: Int               = 0
    actor.addListener(new ChangeListener {
      override def changed(event: ChangeListener.ChangeEvent, actor: Actor): Unit =
        changeEvents += 1
    })
    selection.setActor(Nullable(actor))

    def resetCounters(): Unit = {
      changeEvents = 0
      selection.changedCalls = 0
    }
  }

  test("ISS-501 choose on already-selected single item fires no ChangeEvent and no changed()") {
    val h = new Harness(ctrlHeld = false)
    h.selection.choose("a")
    assertEquals(h.changeEvents, 1, "sanity: initial choose fires one event")
    h.resetCounters()
    h.selection.choose("a")
    assertEquals(h.selection.size, 1)
    assert(h.selection.contains(Nullable("a")))
    assertEquals(
      h.changeEvents,
      0,
      "Java early return `if (selected.size == 1 && selected.contains(item)) return;` must prevent a spurious ChangeEvent"
    )
    assertEquals(h.selection.changedCalls, 0, "changed() must not be invoked on a no-op choose")
  }

  test("ISS-501 toggle=true required=true choose(selected) keeps item selected and fires no event") {
    val h = new Harness(ctrlHeld = false)
    h.selection.toggle = true
    h.selection.required = true
    h.selection.choose("a")
    assertEquals(h.changeEvents, 1, "sanity: initial choose fires one event")
    h.resetCounters()
    h.selection.choose("a")
    assertEquals(h.selection.size, 1, "required: the last selected item must stay selected")
    assert(h.selection.contains(Nullable("a")))
    assertEquals(
      h.changeEvents,
      0,
      "Java early return `if (required && selected.size == 1) return;` must prevent a spurious ChangeEvent"
    )
    assertEquals(h.selection.changedCalls, 0, "changed() must not be invoked when required vetoes the deselect")
  }

  test("ISS-501 first-time choose fires exactly one ChangeEvent and one changed()") {
    val h = new Harness(ctrlHeld = false)
    h.selection.choose("a")
    assertEquals(h.selection.size, 1)
    assert(h.selection.contains(Nullable("a")))
    assertEquals(h.changeEvents, 1, "a genuine selection change must fire exactly one ChangeEvent")
    assertEquals(h.selection.changedCalls, 1, "a genuine selection change must invoke changed() exactly once")
    h.selection.choose("b")
    assertEquals(h.changeEvents, 2)
    assertEquals(h.selection.changedCalls, 2)
  }

  test("ISS-501 ctrl-click on sole selected item with required=true keeps selection and fires no event") {
    val h = new Harness(ctrlHeld = true)
    h.selection.required = true
    h.selection.choose("a")
    assertEquals(h.changeEvents, 1, "sanity: initial ctrl-choose fires one event")
    h.resetCounters()
    h.selection.choose("a")
    assertEquals(h.selection.size, 1, "required: ctrl-click must not deselect the last item")
    assert(h.selection.contains(Nullable("a")))
    assertEquals(h.changeEvents, 0, "no ChangeEvent when required vetoes the ctrl-deselect")
    assertEquals(h.selection.changedCalls, 0, "no changed() when required vetoes the ctrl-deselect")
  }

  test("ISS-501 ctrl-click deselect without required fires exactly one event (positive pin)") {
    val h = new Harness(ctrlHeld = true)
    h.selection.choose("a")
    h.resetCounters()
    h.selection.choose("a")
    assert(h.selection.isEmpty, "ctrl-click on selected item without required deselects it")
    assertEquals(h.changeEvents, 1, "a genuine deselect must fire exactly one ChangeEvent")
    assertEquals(h.selection.changedCalls, 1)
  }
}
