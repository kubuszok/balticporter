package balticporter.sbt

import sbt.*
import sbt.Keys.*

/** sbt plugin that replaces hand-copied BalticPorterGen.scala files in ssg and sge.
  *
  * Provides a `balticporterGenerate` task that runs Baltic Porter's engine or
  * non-Java frontend emitters as a sourceGenerator, writing output to
  * `(Compile / sourceManaged) / "balticporter"`.
  *
  * Settings:
  *   - `balticporterConf`      — the .conf file (resource name or file path)
  *   - `balticporterUpstream`  — upstream source directory (submodule root)
  *   - `balticporterReference` — reference/ directory for parity-derive (optional)
  *   - `balticporterRast`      — pre-exported RAST directory (optional; if absent, exports at build time)
  *   - `balticporterBase`      — base project whose published port map feeds this port (optional)
  *   - `balticporterPostProcess` — regex post-processing rules (each is a VISIBLE finding)
  */
object BalticPorterPlugin extends AutoPlugin {

  object autoImport {
    val balticporterConf = settingKey[File](
      "Path to the Baltic Porter .conf file for this module")

    val balticporterUpstream = settingKey[File](
      "Path to the upstream source directory for a non-Java port")

    val balticporterReference = settingKey[Option[File]](
      "Path to the reference/ directory for parity-derive (None = no parity)")

    val balticporterRast = settingKey[Option[File]](
      "Path to pre-exported RAST directory (None = export at build time)")

    val balticporterBase = settingKey[Option[ProjectRef]](
      "Base project whose published port map feeds this port's dependent surface")

    val balticporterPostProcess = settingKey[Seq[(String, String)]](
      "Regex post-processing rules: (pattern, replacement). Each is a visible finding.")

    val balticporterGenerate = taskKey[Seq[File]](
      "Generate Scala sources from Baltic Porter")

    val balticporterForceRegen = settingKey[Boolean](
      "Force regeneration even if the cache marker is current")
  }

  import autoImport.*

  override def trigger = noTrigger

  override def projectSettings: Seq[Setting[?]] = Seq(
    balticporterReference := None,
    balticporterRast := None,
    balticporterBase := None,
    balticporterPostProcess := Nil,
    balticporterForceRegen := false,
    balticporterGenerate := Def.uncached {
      val log = streams.value.log
      val outDir = (Compile / sourceManaged).value / "balticporter"
      val conf = balticporterConf.value
      val upstream = balticporterUpstream.value
      val reference = balticporterReference.value
      val rast = balticporterRast.value
      val postProcess = balticporterPostProcess.value

      if (postProcess.nonEmpty) {
        postProcess.foreach { case (pattern, _) =>
          log.warn(s"[sbt-balticporter] postProcess rule: $pattern — this is a visible finding")
        }
      }

      log.info(s"[sbt-balticporter] Conf: $conf")
      log.info(s"[sbt-balticporter] Upstream: $upstream")
      reference.foreach(r => log.info(s"[sbt-balticporter] Reference: $r"))
      rast.foreach(r => log.info(s"[sbt-balticporter] RAST: $r"))

      // Determine port type from conf
      val portType = PortType.detect(conf)
      log.info(s"[sbt-balticporter] Port type: $portType")

      portType match {
        case PortType.Java =>
          JavaPortTask.run(JavaPortTask.Config(
            conf = conf.toPath,
            upstream = upstream.toPath,
            outputDir = outDir.toPath,
          ), msg => log.info(msg))

        case PortType.NonJava(language) =>
          NonJavaPortTask.run(NonJavaPortTask.Config(
            library = conf.getName.stripSuffix(".conf"),
            language = language,
            upstream = upstream.toPath,
            reference = reference.map(_.toPath),
            rastDir = rast.map(_.toPath),
            outputDir = outDir.toPath,
          ), msg => log.info(msg))
      }

      // Collect generated files
      if (outDir.exists()) {
        (outDir ** "*.scala").get().toSeq
      } else {
        Seq.empty
      }
    },
    // Consumer wires: Compile / sourceGenerators += balticporterGenerate.taskValue
  )
}
