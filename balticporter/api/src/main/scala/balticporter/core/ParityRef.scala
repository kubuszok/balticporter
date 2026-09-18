package balticporter.core

import java.nio.file.Path

/** Which hand port [[ApiParityCheck]] compares this module's emitted output against — one or more source roots. `packageMapping` overrides the manifest's `effectivePackageRenames` when the hand
  * port's namespace does not follow the rename rules; empty means use the manifest's mapping as-is. Empty/absent means the check is a no-op. Not inherited — a dependent has its own hand-port tree.
  */
final case class ParityRef(
  /** root directories of the hand port's Scala source tree(s). */
  roots: List[Path],
  /** explicit package-prefix mapping from hand-port namespace to emitted namespace, if the manifest's `effectivePackageRenames` is not the right inverse. Empty means use the manifest renames.
    */
  packageMapping: Map[String, String] = Map.empty,
  /** header substrings that make a hand-port file a party to the comparison; a file naming none of them is listed as `api-parity(hand-original)` and compared against nothing. Empty means every file
    * is a party.
    */
  upstreamMarkers: List[String] = ParityRef.DefaultUpstreamMarkers,
  /** whether to run the surface comparison (the `api-parity(*)` checks). `false` keeps the reference only as the source phases derive spelling policy from (`RunScope.derived`), without reporting the
    * comparison.
    */
  compare: Boolean = true
)

object ParityRef:
  /** The spellings the reference hand ports write above a ported file's own declarations. */
  val DefaultUpstreamMarkers: List[String] =
    List("Ported from", "Original source:", "Covenant-java-reference:")
