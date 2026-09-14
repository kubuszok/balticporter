package balticporter.sbt

import java.io.File

/** Classifies a port as Java (engine-driven) or non-Java (RAST + emitter). */
enum PortType:
  case Java
  case NonJava(language: String)

object PortType:
  /** Detect port type from the .conf file name or content.
    *
    * Convention: a conf whose name contains a known non-Java library
    * is a non-Java port; everything else is Java.
    */
  def detect(conf: File): PortType =
    val name = conf.getName.toLowerCase
    if name.contains("terser") || name.contains("katex") || name.contains("mermaid") ||
       name.contains("roughjs") || name.contains("graphs") then
      PortType.NonJava("typescript")
    else if name.contains("sass") || name.contains("dart") then
      PortType.NonJava("dart")
    else
      PortType.Java
