// ---------------------------------------------------------------------------------------------
// The only plugins this build has, and both are here for ONE module: `balticporter-runtime` is
// the artifact an emitted PORT links against, and a port targets every platform the reference
// port does (CLAUDE.md §1.5 — sge and ssg target JVM, Scala.js and Native wherever possible).
// So the runtime is a `projectMatrix` over those three, and a JS/Native row needs its compiler.
//
// The versions are sge's, deliberately: an emitted port is linked by sge's toolchain, and a
// runtime built by a different Scala.js or Scala Native version is one its linker may reject.
// `../sge/project/plugins.sbt` reaches them through `sbt-kubuszok` 0.2.3, which bundles exactly
// these two; naming them directly keeps this build's plugin surface to what it uses.
//
// `sbt-projectmatrix` is NOT here: it is merged into sbt 2.0, so `projectMatrix` is built in.
addSbtPlugin("org.scala-js"    % "sbt-scalajs"      % "1.22.0")
addSbtPlugin("org.scala-native" % "sbt-scala-native" % "0.5.12")
