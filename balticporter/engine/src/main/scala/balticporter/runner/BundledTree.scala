package balticporter.runner

import java.nio.file.{ Files, Path }
import scala.jdk.CollectionConverters.*

/** A directory tree shipped INSIDE a jar, for the files a policy injects by path (`PortManifest.inject`, `platformDirs`, a `.conf`): a consumer that resolves the published artifact has no checkout to
  * point at. The tree is listed by `<prefix>/INDEX` (one `/`-separated relative path per line) because a classpath cannot be enumerated portably.
  */
object BundledTree:

  val IndexName = "INDEX"

  /** make `dest` hold exactly the indexed tree: every listed file written (left alone when its bytes already match, so mtimes stay stable), every other file under `dest` removed. A listed resource the
    * loader cannot find is FATAL — a silently shorter tree is a port with a type missing.
    */
  def extract(loader: ClassLoader, prefix: String, dest: Path): Path =
    val indexRes = s"$prefix/$IndexName"
    val index    = Option(loader.getResourceAsStream(indexRes)).getOrElse(throw new IllegalStateException(s"bundled tree: no `$indexRes` on the classpath"))
    val listed   =
      try new String(index.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).linesIterator.map(_.trim).filter(_.nonEmpty).toList
      finally index.close()
    val root    = dest.toAbsolutePath.normalize
    val written = listed.map { rel =>
      val target = root.resolve(rel).normalize
      require(target.startsWith(root), s"bundled tree: `$rel` escapes $root")
      val in = Option(loader.getResourceAsStream(s"$prefix/$rel")).getOrElse(throw new IllegalStateException(s"bundled tree: `$indexRes` lists `$rel`, which is not on the classpath"))
      val bytes =
        try in.readAllBytes()
        finally in.close()
      if !Files.isRegularFile(target) || !java.util.Arrays.equals(Files.readAllBytes(target), bytes) then
        Files.createDirectories(target.getParent)
        Files.write(target, bytes)
      target
    }.toSet
    if Files.isDirectory(root) then
      val walk = Files.walk(root)
      try walk.iterator().asScala.filter(p => Files.isRegularFile(p) && !written(p.normalize)).toList.foreach(Files.delete)
      finally walk.close()
    root
