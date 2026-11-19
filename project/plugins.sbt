// sbt-kubuszok bundles sbt-pgp, sbt-git, sbt-scalafmt, sbt-commandmatrix, sbt-scalajs and
// sbt-scala-native, and provides ci-release, publishTo → Maven Central Snapshots, projectType-based
// publish gating. Scala.js / Scala Native come from it rather than being pinned here: an emitted port
// is linked by the consumer's toolchain, and kubuszok repos get theirs from the same plugin.
addSbtPlugin("com.kubuszok" % "sbt-kubuszok" % "0.2.5")
