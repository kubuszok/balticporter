package balticporter.core

import balticporter.tir.Phase

/** Reading a manifest's DECLARATIVE half as data, in the schema [[PortMap]] publishes.
  *
  * ==Why the two halves are read differently==
  * A `PortManifest` is two things in one value. `dropTypes`, `dropMethods`, `packageRenames`,
  * `governs` and `inject` are string-keyed DATA — no behaviour, nothing a compiler checks that a
  * parser could not. `surface` is a list of PHASES, and a phase is code: a §1(c) rule is a
  * traversal encoding an invariant of one library's design, and no config format expresses it.
  * Trying would turn the engine into a plugin registry, which `CLAUDE.md` §2.1 explicitly refuses
  * ("Implement `balticporter.tir.Phase`; there is no registry, service loader or plugin
  * descriptor").
  *
  * So the declarative half can be data and the surface cannot, and the honest design says so
  * rather than pretending a manifest is uniformly one or the other.
  *
  * ==The schema is the port map's, deliberately==
  * A key means the same thing in both: a type is a fully-qualified name, a member is the ERASED
  * `owner#name(P1,P2)` form (`PortMap.erase`), and a rename is an upstream prefix paired with a
  * port prefix. Sharing them is what makes [[fromPortMap]] possible at all — and that method is
  * the point of the exercise.
  *
  * ==Why a DEPENDENT should rarely write this by hand==
  * The reason a dependent restates a base's drops and renames today is that it had no way to LEARN
  * them. [[fromPortMap]] removes that reason: the base publishes what it did, and the dependent
  * reads it. Hand-authoring is then for the BASE of a library — the module that decides the policy
  * — and for the parts an agent edits deliberately.
  */
object PortManifestConfig:

  /** The declarative facts a base's published map implies for its dependents.
    *
    * A `Dropped` or `Substituted` type is a drop either way: both mean "do not translate this
    * mechanically", and the difference — whether something stands at the name — is the BASE's to
    * act on, not the dependent's. A `Renamed` entry contributes the longest common prefix pair it
    * witnesses, which is how a package rename is recovered from individual types.
    */
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
        // SHRINK the common suffix until it begins at a separator, so a partial segment is never
        // taken as a prefix — `com.badlogic.gdx.Batch`/`sge.Batch` must yield `com.badlogic.gdx`
        // and `sge`, never a pair cut inside `Batch`. Growing the suffix (backing the CUT off
        // instead) is the same idea applied to the wrong end and eats a real segment from both.
        while i > 0 && !".$#".contains(e.upstream.charAt(e.upstream.length - i)) do i -= 1
        (e.upstream.dropRight(i), e.emitted.dropRight(i))
    }.filter((f, t) => f.nonEmpty && t.nonEmpty).distinct.toMap
    (drops, dropMethods, renames)

  /** A manifest whose declarative half is READ from a base's published map rather than restated.
    *
    * This is the answer to "should the configuration be config?": for a dependent, most of it
    * should not exist. What remains for the caller is what is genuinely this module's — its name,
    * its own phases, its own injections.
    *
    * `bases` is left empty on purpose: the returned value carries the base's FACTS, not a link to
    * the base's manifest value. A caller that has the base value should use `extendedBy`, which is
    * stronger (drift becomes unrepresentable rather than merely detected). This is for the case
    * `mirroring` exists for — a base in a repository this module only reads artifacts from.
    */
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

  /** Render the declarative half, in the port map's TSV conventions. The `surface` is NOT rendered:
    * it is code, and a config that silently omitted a phase list would be read as "no phases". The
    * count is stated instead, so a reader can see something was left out. */
  def render(m: PortManifest): String =
    val ls = collection.mutable.ListBuffer(s"# balticporter port manifest\tschema=${PortMap.Schema}\tmodule=${m.name}")
    ls += Header
    m.governs.toList.sorted.foreach(g => ls += s"governs\t$g\t")
    m.dropTypes.toList.sorted.foreach(d => ls += s"dropType\t$d\t")
    m.dropMethods.toList.sorted.foreach(d => ls += s"dropMethod\t$d\t")
    m.packageRenames.toList.sortBy(_._1).foreach((f, t) => ls += s"packageRename\t$f\t$t")
    ls += s"# surface: ${m.surface.size} phase(s), NOT represented here — a phase is code, not data"
    m.surface.foreach(p => ls += s"# surface\t${PortManifest.fingerprint(p)}\t")
    ls.mkString("", "\n", "\n")

  /** Parse what [[render]] writes. `surface` comes from the caller, because it cannot come from
    * here — the parameter is required rather than defaulted so that omitting the phases is a
    * decision the caller makes explicitly. */
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
          surface        = surface,
        ))
