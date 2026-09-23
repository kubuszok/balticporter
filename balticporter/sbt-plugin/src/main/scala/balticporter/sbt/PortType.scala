package balticporter.sbt

import java.io.File

/** Classifies a port as Java (engine-driven) or non-Java (RAST + emitter). */
enum PortType:
  case Java
  case NonJava(language: String)

object PortType:
  /** Detect port type from the .conf file content.
    *
    * Reads the `input.frontend` value: a non-Java frontend means a non-Java port; everything else
    * defaults to Java. The consumer controls this through the configuration, not the file name.
    */
  def detect(conf: File): PortType =
    val content = scala.io.Source.fromFile(conf).mkString.toLowerCase
    if content.contains("frontend = \"typescript\"") || content.contains("frontend = \"javascript\"") then
      PortType.NonJava("typescript")
    else if content.contains("frontend = \"dart\"") then
      PortType.NonJava("dart")
    else
      PortType.Java
