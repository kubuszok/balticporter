package balticporter.core

import java.nio.file.Path

/** The §1(b) parameter for [[ApiParityCheck]]: WHERE IS THE HAND PORT whose public surface this
  * module's emitted output is compared against — one or more source roots. `packageMapping`
  * overrides the manifest's `effectivePackageRenames` when the hand port's namespace does not
  * follow the rename rules; empty means use the manifest's mapping as-is. Empty/absent parity ref
  * = no-op (§1b). NOT inherited — a dependent has its own hand-port tree. */
final case class ParityRef(
    /** root directories of the hand port's Scala source tree(s). */
    roots: List[Path],
    /** explicit package-prefix mapping from hand-port namespace to emitted namespace, if the
      * manifest's `effectivePackageRenames` is not the right inverse. Empty means use the
      * manifest renames. */
    packageMapping: Map[String, String] = Map.empty,
)
