package balticporter.core

import java.nio.file.Path

/** The §1(b) parameter for [[ApiParityCheck]]: WHERE IS THE HAND PORT whose public surface this
  * module's emitted output is compared against.
  *
  * ==What it names==
  * One or more source roots of the REFERENCE hand port for THIS module — the directories that
  * contain the `.scala` files a drop-in replacement would need to match. For sge-ecs that is
  * `../sge/sge-extension/ecs/src/main/scala` (plus any platform-specific directories if the hand
  * port has them).
  *
  * ==The package mapping==
  * The hand port and the emitted port may use different package prefixes — the hand port was
  * written in the destination namespace and the engine emits through `packageRenames`. The check
  * must normalise both sides before comparing, and the manifest's own `effectivePackageRenames`
  * is usually the right mapping. `packageMapping` here is an OVERRIDE for the case where the
  * hand port's namespace does not follow the manifest's rename rules (e.g. a hand port that
  * predates the rename). Empty means "use the manifest's effectivePackageRenames as-is".
  *
  * ==Empty / absent = no-op==
  * §1(b)'s rule: an empty parameter is a no-op, and the check records nothing.
  *
  * ==NOT inherited==
  * A hand port is a fact about THIS module's destination, not the shared surface. A dependent
  * does not inherit its base's parity reference — the two have different hand-port trees.
  */
final case class ParityRef(
    /** root directories of the hand port's Scala source tree(s). */
    roots: List[Path],
    /** explicit package-prefix mapping from hand-port namespace to emitted namespace, if the
      * manifest's `effectivePackageRenames` is not the right inverse. Empty means use the
      * manifest renames. */
    packageMapping: Map[String, String] = Map.empty,
)
