package balticporter.runtime

/** `java.util.OptionalDouble` — [[JavaOptionalInt]]'s reasoning at the other width, and it is a
  * SEPARATE FILE for the same reason: `RuntimeArtifact.vendored` indexes the published module by
  * file name, so one file holding three aliases would be one FQN holding three. */
type JavaOptionalDouble = Option[Double]
