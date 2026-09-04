package balticporter.core

import balticporter.tir.Phase

/** Reading a manifest's DECLARATIVE half (drops, renames, governs) as data, in the schema
  * [[PortMap]] publishes. The `surface` (phases) is code and cannot be expressed as data.
  * [[fromPortMap]] recovers a dependent's declarative half from what a base published;
  * `PortConfig` (DESIGN.md §5.7) reads what an author wrote. */
object PortManifestConfig:

  /** Extract (dropTypes, dropMethods, packageRenames) from a base's published map.
    * `Dropped`/`Substituted` types both become drops; renames are recovered as longest common prefixes. */
  def declarativeFrom(map: PortMap.Map0): (Set[String], Set[String], Map[String, String]) =
    val drops = map.types.collect {
      case e if e.disposition == PortMap.Disposition.Dropped || e.disposition == PortMap.Disposition.Substituted =>
        e.upstream
    }.toSet
    val dropMethods = map.members.collect {
      case e if e.disposition == PortMap.Disposition.Dropped => e.upstream
    }.toSet
    // Recover prefix pairs from renamed types: strip the common SUFFIX of upstream and emitted,
    // and what remains on each side is the pair. Cut at a separator so `com.foo -> sge` is derived
    // and never `com.foo.Ba -> sge.Ba`.
    val renames = map.types.collect {
      case e if e.disposition == PortMap.Disposition.Renamed && e.upstream.nonEmpty && e.emitted.nonEmpty =>
        var i = 0
        while i < e.upstream.length && i < e.emitted.length &&
          e.upstream.charAt(e.upstream.length - 1 - i) == e.emitted.charAt(e.emitted.length - 1 - i) do i += 1
        // Shrink the common suffix until it begins at a separator.
        while i > 0 && !".$#".contains(e.upstream.charAt(e.upstream.length - i)) do i -= 1
        (e.upstream.dropRight(i), e.emitted.dropRight(i))
    }.filter((f, t) => f.nonEmpty && t.nonEmpty).distinct.toMap
    (drops, dropMethods, renames)

  /** Build a manifest whose declarative half comes from a base's published map.
    * `bases` is left empty; callers with the base value should use `extendedBy` instead. */
  def fromPortMap(
      name: String,
      map: PortMap.Map0,
      surface: List[Phase] = Nil,
      inject: List[java.nio.file.Path] = Nil,
      governs: Set[String] = Set.empty,
  ): PortManifest =
    val (dt, dm, pr) = declarativeFrom(map)
    PortManifest(name = name, governs = governs, dropTypes = dt, dropMethods = dm,
      packageRenames = pr, surface = surface, inject = inject)

  private val Header = "#setting\tvalue\tto"

  /** Render the declarative half as TSV. The `surface` is NOT rendered (it is code); its count
    * is stated instead. */
  def render(m: PortManifest): String =
    val ls = collection.mutable.ListBuffer(s"# balticporter port manifest\tschema=${PortMap.Schema}\tmodule=${m.name}")
    ls += Header
    m.governs.toList.sorted.foreach(g => ls += s"governs\t$g\t")
    m.dropTypes.toList.sorted.foreach(d => ls += s"dropType\t$d\t")
    m.dropMethods.toList.sorted.foreach(d => ls += s"dropMethod\t$d\t")
    m.packageRenames.toList.sortBy(_._1).foreach((f, t) => ls += s"packageRename\t$f\t$t")
    m.typeRenames.toList.sortBy(_._1).foreach((f, t) => ls += s"typeRename\t$f\t$t")
    m.subPackages.toList.sortBy(_._1).foreach((f, t) => ls += s"subPackage\t$f\t$t")
    m.flattenNestedTypes.toList.sorted.foreach(f => ls += s"flattenNestedType\t$f\t")
    m.allowPackageSplit.toList.sorted.foreach(f => ls += s"allowPackageSplit\t$f\t")
    ls += s"# surface: ${m.surface.size} phase(s), NOT represented here — a phase is code, not data"
    m.surface.foreach(p => ls += s"# surface\t${PortManifest.fingerprint(p)}\t")
    ls.mkString("", "\n", "\n")

  /** Parse what [[render]] writes. `surface` must be supplied by the caller (phases are code). */
  def parse(text: String, surface: List[Phase]): Either[String, PortManifest] =
    val lines = text.linesIterator.toList
    val meta  = lines.headOption.getOrElse("")
    """schema=(\d+)""".r.findFirstMatchIn(meta).map(_.group(1).toInt) match
      case None => Left("port manifest has no schema header")
      case Some(s) if s != PortMap.Schema =>
        Left(s"port manifest declares schema $s; this engine reads ${PortMap.Schema}")
      case _ =>
        val name = """module=([^\t]+)""".r.findFirstMatchIn(meta).map(_.group(1)).getOrElse("?")
        val rows = lines.filterNot(l => l.startsWith("#") || l.isBlank).map(_.split("\t", -1))
        def all(k: String) = rows.collect { case Array(`k`, v, _*) if v.nonEmpty => v }.toSet
        Right(PortManifest(
          name           = name,
          governs        = all("governs"),
          dropTypes      = all("dropType"),
          dropMethods    = all("dropMethod"),
          packageRenames = rows.collect { case Array("packageRename", f, t, _*) if f.nonEmpty => f -> t }.toMap,
          typeRenames    = rows.collect { case Array("typeRename", f, t, _*) if f.nonEmpty => f -> t }.toMap,
          subPackages    = rows.collect { case Array("subPackage", f, t, _*) if f.nonEmpty => f -> t }.toMap,
          flattenNestedTypes = all("flattenNestedType"),
          allowPackageSplit  = all("allowPackageSplit"),
          surface        = surface,
        ))
