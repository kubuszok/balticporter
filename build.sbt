ThisBuild / organization := "com.kubuszok"
ThisBuild / scalaVersion := "3.8.4"
ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Wunused:all",
)

val munit = "org.scalameta" %% "munit" % "1.2.0" % Test

lazy val core = project
  .in(file("core"))
  .settings(
    name := "balticporter-core",
    libraryDependencies += munit,
  )

lazy val `frontend-spoon` = project
  .in(file("frontend-spoon"))
  .dependsOn(core)
  .settings(
    name := "balticporter-frontend-spoon",
    libraryDependencies ++= Seq(
      "fr.inria.gforge.spoon" % "spoon-core" % "11.5.0",
      munit,
    ),
  )

lazy val `scala-emit` = project
  .in(file("scala-emit"))
  .dependsOn(core)
  .settings(
    name := "balticporter-scala-emit",
    libraryDependencies += munit,
  )

lazy val testkit = project
  .in(file("testkit"))
  .dependsOn(core, `frontend-spoon`, `scala-emit`)
  .settings(
    name := "balticporter-testkit",
    libraryDependencies += "org.scalameta" %% "munit" % "1.2.0",
  )

lazy val runner = project
  .in(file("runner"))
  .dependsOn(core, `frontend-spoon`, `scala-emit`)
  .settings(
    name := "balticporter-runner",
    libraryDependencies += munit,
  )

lazy val `corpus-tests` = project
  .in(file("corpus-tests"))
  .dependsOn(runner, testkit)
  .settings(
    name := "balticporter-corpus-tests",
    libraryDependencies += munit,
    publish / skip := true,
  )

lazy val root = project
  .in(file("."))
  .aggregate(core, `frontend-spoon`, `scala-emit`, testkit, runner, `corpus-tests`)
  .settings(
    name := "balticporter",
    publish / skip := true,
  )
