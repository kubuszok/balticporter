package balticporter.corpus
import balticporter.core.{PortMap, PortManifestConfig}
import java.nio.file.Path
/** Proof that a dependent can derive its inherited declarative policy from the BASE's published
  * map rather than restating it — PORT-MAP-DESIGN step 4, on the real artifact. */
object DeriveCheck:
  def main(args: Array[String]): Unit =
    val repoRoot = Path.of(sys.props.getOrElse("balticporter.root", ".")).toAbsolutePath.normalize
    val mapPath  = repoRoot.resolve("port-report/LibgdxCoreMigrate/run-latest/port-map.tsv")
    PortMap.read(mapPath) match
      case Left(err) => sys.error(err)
      case Right(m) =>
        val derived = PortManifestConfig.fromPortMap("ashley-derived", m)
        val declared = LibgdxPolicy.core(repoRoot)
        println(s"[derive] base map: ${m.types.size} types, ${m.members.size} members")
        println(s"[derive] derived dropTypes   = ${derived.dropTypes.size}  (declared ${declared.dropTypes.size})")
        println(s"[derive] derived dropMethods = ${derived.dropMethods.size}  (declared ${declared.dropMethods.size})")
        println(s"[derive] dropTypes match:   ${derived.dropTypes == declared.dropTypes}")
        println(s"[derive] dropMethods match: ${derived.dropMethods == declared.dropMethods}")
        val missing = declared.dropTypes -- derived.dropTypes
        if missing.nonEmpty then println(s"[derive] NOT recovered: ${missing.toList.sorted.mkString(", ")}")
