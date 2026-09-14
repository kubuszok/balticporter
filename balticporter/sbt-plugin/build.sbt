sbtPlugin := true

name         := "sbt-balticporter"
organization := "com.kubuszok"
version      := sys.env.getOrElse("BALTICPORTER_VERSION", "0.1.0-SNAPSHOT")

scalacOptions ++= Seq("-deprecation", "-feature")
