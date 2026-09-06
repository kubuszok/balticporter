package lowlevel.util

/** java's `Collections.allocateIterators` flag, the one static the lls port's twelve read from a type
  * lls does not declare — shipped by the BASE as a support type (CLAUDE.md §1.5, ENGINE-LIMITS.md K43). */
object Collections:
  var allocateIterators: Boolean = false
