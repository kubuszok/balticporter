// sbt-kubuszok bundles sbt-pgp, sbt-git, sbt-scalafmt, sbt-commandmatrix, and provides
// ci-release, publishTo → Maven Central Snapshots, projectType-based publish gating.
// sge and ssg use 0.2.3; keeping the same version ensures the publish coordinates match.
addSbtPlugin("com.kubuszok"     % "sbt-kubuszok"         % "0.2.3")
// The versions are sge's, deliberately: an emitted port is linked by sge's toolchain, and a
// runtime built by a different Scala.js or Scala Native version is one its linker may reject.
addSbtPlugin("org.scala-js"    % "sbt-scalajs"      % "1.22.0")
addSbtPlugin("org.scala-native" % "sbt-scala-native" % "0.5.12")
