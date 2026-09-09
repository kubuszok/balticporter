package sge
package ai
package fma

import sge.ai.utils.Location
import sge.math.Vector

class FormationPatternAdditiveGetterIss730RedSuite extends munit.FunSuite {

  final private class OriginalShapePattern[T <: Vector[T]] extends FormationPattern[T] {
    override def setNumberOfSlots(numberOfSlots:    Int):                          Unit        = ()
    override def calculateSlotLocation(outLocation: Location[T], slotNumber: Int): Location[T] = outLocation
    override def supportsSlots(slotCount:           Int):                          Boolean     = true
  }

  test(
    "ISS-730 c7: FormationPattern's member set must match the original setter-only interface (no additive numberOfSlots getter)"
  ) {
    val pattern = new OriginalShapePattern[Nothing]()
    pattern.setNumberOfSlots(3)
    assert(pattern.supportsSlots(3))
  }
}
