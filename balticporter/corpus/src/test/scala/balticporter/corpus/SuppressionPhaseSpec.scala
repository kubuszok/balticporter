package balticporter.corpus

import balticporter.runner.PortRun
import balticporter.transform.SuppressionPhase

/** `SuppressionPhase` is derived unconditionally by `PortRun` — not declared per port.
  *
  * ==Why==
  * The phase scans the FINAL tree for `.orNull` calls (minted by `NullabilityTransform` with a
  * `Named` target) and adds `@nowarn("msg=deprecated")` to members that hold them. Without a
  * `Named` target, no `.orNull` symbols exist and the phase returns early (a no-op). So:
  *   - every port under `-Werror -deprecation` with a `Named` nullability target needs it;
  *   - every other port is unaffected.
  *
  * Declaring it per port is the §1.5 drift the conditional-lane pattern exists to prevent: the
  * next port using `Named` would silently lose its suppressions. `PortRun.derivedPhases` includes
  * it unconditionally, the same way `remedyPhases` includes the remedy phases.
  */
class SuppressionPhaseSpec extends munit.FunSuite:

  test("PortRun.derivedPhases includes SuppressionPhase") {
    val phases = PortRun.derivedPhases
    assert(
      clue(phases).exists(_.isInstanceOf[SuppressionPhase]),
      "SuppressionPhase must be in PortRun.derivedPhases — it is §1(a) universal and a no-op " +
        "when no Named nullability target is in the pipeline"
    )
  }

  test("SuppressionPhase has correct ordering constraints") {
    val phase = new SuppressionPhase
    // Must run AFTER every retyping phase (so it sees the FINAL tree)
    assert(clue(phase.runsAfter).contains("nullability"))
    assert(clue(phase.runsAfter).contains("java-collections->scala"))
    assert(clue(phase.runsAfter).contains("type-redirect"))
    assert(clue(phase.runsAfter).contains("globals->implicits"))
    // Must run BEFORE package-rename (the annotation FQN is in the scala namespace)
    assert(clue(phase.runsBefore).contains("package-rename"))
  }

  test("SuppressionPhase name is suppressed-warnings") {
    assertEquals(new SuppressionPhase().name, SuppressionPhase.Name)
    assertEquals(SuppressionPhase.Name, "suppressed-warnings")
  }
