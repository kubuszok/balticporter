package balticporter.core

import balticporter.tir.{CheckReport, SrcMap, TirPrinter}

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** What a module's port actually did to its upstream surface, published for its dependents.
  * A projection of SrcMap, Substitutions, PackageRenameTransform, RuntimePlan and MethodBodyTransform.
  * Invariant: a module's map is an OUTPUT, never an input to its own run. TSV-encoded. */
object PortMap:

  /** Schema version. A NEWER schema is refused; an OLDER one degrades per question to `Unknown`.
    * 2: `sources=`/`files=`; 3: `shape` column + `policy=`; 4: `jdk=`. // ENGINE-LIMITS M5.10 */
  val Schema = 4

  enum Disposition:
    /** translated mechanically, at the same fully-qualified name. */
    case Ported
    /** translated, but emitted at a DIFFERENT name (a package rename, or a type rename). */
    case Renamed
    /** not translated; replaced at the same FQN by injected Scala. A caller sees the same name and
      * a different implementation. */
    case Substituted
    /** not emitted and NOT replaced. Every reference must have been rewritten away — a dependent
      * that still calls it will not compile, which is the point of recording it. */
    case Dropped
    /** present in the port and absent upstream: an injected type, a runtime shim, or a member a
      * library-specific rule introduced. */
    case Added

  /** @param upstream Java-side name (`owner#name(P1,P2)` for a member)
    * @param emitted  emitted Scala name; empty when [[Disposition.Dropped]]
    * @param body     member has a hand-supplied body (`MethodBodyTransform`)
    * @param shape    porter-note `k=v` grammar describing what was emitted (DESIGN.md §8.3);
    *                 names inside are EMITTED names, while `upstream` stays the join key */
  final case class Entry(
      kind: String, // "type" | "member"
      upstream: String,
      emitted: String,
      disposition: Disposition,
      body: Boolean = false,
      javaPath: String = "",
      javaLine: Int = 0,
      digest: String = "",
      shape: String = "",
  ):
    def tsv: String =
      s"$kind\t$upstream\t$emitted\t$disposition\t${if body then "body" else "-"}\t$javaPath\t$javaLine\t$digest\t$shape"

    /** Parsed type shape, or `None` for member rows, schema-2 rows, or dropped types. */
    def typeShape: Option[balticporter.tir.Surface.TypeShape] =
      if kind == "type" then balticporter.tir.Surface.parseType(shape) else scala.None

    def memberShape: balticporter.tir.Surface.MemberShape =
      balticporter.tir.Surface.parseMember(shape)

  /** @param sources source fingerprint at publication time (see [[sourcesDigest]])
    * @param files   count of distinct Java files the fingerprint covers
    * @param policy  sorted `SurfacePolicy` fingerprint (see [[policyDigest]])
    * @param jdk     `java.specification.version` of the publishing JVM
    * @param schema  schema the map was read at */
  final case class Map0(
      module: String,
      engine: String,
      entries: List[Entry],
      sources: String = "",
      files: Int = 0,
      policy: String = "",
      schema: Int = Schema,
      jdk: String = "",
  ):
    def types: List[Entry]   = entries.filter(_.kind == "type")
    def members: List[Entry] = entries.filter(_.kind == "member")
    /** upstream name → what a dependent will actually find. The lookup a `PortMapTransform` needs. */
    def byUpstream: scala.collection.Map[String, Entry] = entries.iterator.map(e => e.upstream -> e).toMap
    /** byUpstream restricted to one kind ("type" or "member"). */
    def byUpstream(kind: String): scala.collection.Map[String, Entry] =
      entries.iterator.filter(_.kind == kind).map(e => e.upstream -> e).toMap
    /** Distinct Java files this map attributes members to. Paths in angle brackets excluded. */
    def javaPaths: List[String] =
      members.map(_.javaPath).filter(p => p.nonEmpty && !p.startsWith("<")).distinct.sorted

    /** Each `javaPath` also as a package-relative path (where the two differ), so a consumer
      * whose resolution roots differ from the publisher's source root can still resolve files.
      * Derived from the `upstream` column's package. // ENGINE-LIMITS D11 */
    def packageRelative: scala.collection.Map[String, String] =
      members.iterator.flatMap { e =>
        val norm = e.javaPath.replace('\\', '/')
        val cut  = norm.lastIndexOf('/')
        if norm.isEmpty || norm.startsWith("<") || cut <= 0 then scala.None
        else
          val dir  = norm.substring(0, cut)
          val file = norm.substring(cut + 1)
          val head = e.upstream.indexWhere(c => c == '$' || c == '#') match
            case -1 => e.upstream
            case i  => e.upstream.substring(0, i)
          val pkg = head.lastIndexOf('.') match
            case j if j > 0 => head.substring(0, j).replace('.', '/')
            case _          => ""
          if pkg.isEmpty || dir == pkg || !dir.endsWith("/" + pkg) then scala.None
          else Some(e.javaPath -> s"$pkg/$file")
      }.toMap

  object Map0:
    /** the empty map — what an unconfigured consumer holds, and a total no-op by arithmetic. */
    val empty: Map0 = Map0("", "", Nil)

  private val Header =
    "#kind\tupstream\temitted\tdisposition\tbody\tjavaPath\tjavaLine\tdigest\tshape"

  /** Reverse a package rename: emitted name -> upstream name.
    * Longest matching value prefix wins; ambiguous targets decline. */
  /** Strip generic arguments from a member key's parameter list: `f(Class<T>)` -> `f(Class)`.
    * Reconciles emitted (generic) keys to the erased manifest form for lookup. */
  private[core] def erase(key: String): String =
    val i = key.indexOf('(')
    if i < 0 then key
    else
      val sb = new StringBuilder(key.substring(0, i + 1))
      var depth = 0
      key.substring(i + 1).foreach {
        case '<'          => depth += 1
        case '>'          => depth -= 1
        case c if depth == 0 => sb.append(c)
        case _            => ()
      }
      sb.toString

  private def unrename(emitted: String, renames: scala.collection.Map[String, String]): String =
    def boundary(c: Char) = c == '.' || c == '$' || c == '#'
    def covers(s: String, p: String) =
      p.nonEmpty && s.startsWith(p) && (s.length == p.length || boundary(s.charAt(p.length)))
    val hits = renames.toList.filter((_, to) => covers(emitted, to))
    hits.sortBy(-_._2.length) match
      case (from, to) :: rest if !rest.exists(_._2.length == to.length) => from + emitted.substring(to.length)
      case _                                                           => emitted

  /** The upstream FQN, derived from the java ORIGIN path rather than the emitted name.
    * `unrename` inverts the rename table; the path is ground truth where `unrename` is ambiguous.
    * The declared package (a suffix of the path-derived one) truncates leading directory segments.
    * // CLAUDE.md §4.57 */
  private def upstreamOf(emitted: String, javaPath: String, renames: scala.collection.Map[String, String]): String =
    if javaPath.isEmpty || javaPath.startsWith("<") then unrename(emitted, renames)
    else
      // The file gives the PACKAGE, never the type name (a file may declare multiple top-level types).
      val dir = javaPath.stripSuffix(".java").replace('\\', '/')
      val pkg = dir.lastIndexOf('/') match
        case i if i > 0 => dir.substring(0, i).replace('/', '.')
        case _          => ""
      // emitted name's top-level simple name, plus `$Inner` / `#m(…)` tail.
      val cut  = emitted.indexWhere(c => c == '$' || c == '#')
      val head = if cut < 0 then emitted else emitted.substring(0, cut)
      val tail = if cut < 0 then "" else emitted.substring(cut)
      val simple = head.substring(head.lastIndexOf('.') + 1)
      val fromPath = (if pkg.isEmpty then simple else s"$pkg.$simple") + tail
      val declared = unrename(emitted, renames)
      // `declared` inverts both package and type renames (D16). `fromPath` is the fallback.
      // `qualifiedHead` guard: bare member keys have no package to truncate.
      val qualifiedHead = declared.indexWhere(c => c == '$' || c == '#') match
        case -1 => declared.contains('.')
        case i  => declared.substring(0, i).contains('.')
      if qualifiedHead && declared != emitted then declared
      else if qualifiedHead && fromPath.endsWith("." + declared) then declared
      else fromPath

  /** Assemble the map. Pure: every argument comes from the run. */
  def of(
      module: String,
      engine: String,
      emittedTypes: List[String],
      srcMap: SrcMap.Recording,
      dropTypes: Set[String],
      dropMethods: Set[String],
      injectedFqns: Set[String],
      bodyKeys: Set[String],
      /** Package renames AND per-type renames, composed via `PackageRenameTransform.upstreamTable`.
        * // ENGINE-LIMITS D16 */
      renames: scala.collection.Map[String, String],
      sourceRoot: Option[Path] = scala.None,
      typeShapes: scala.collection.Map[String, String] = Map.empty,
      memberShapes: scala.collection.Map[String, String] = Map.empty,
      policy: String = "",
      refusedMembers: scala.collection.Map[String, String] = Map.empty,
      /** Member renames: current fullName -> original java fullName. */
      memberOriginals: scala.collection.Map[String, String] = Map.empty,
      /** Publishing JVM's `java.specification.version`. `""` = "not stated" (reported as `Unverified`). */
      jdk: String = "",
  ): Map0 =
    // emitted FQN -> origin java file
    val originOf: scala.collection.Map[String, String] =
      srcMap.entries.iterator.map(e => e.unit -> e.javaPath).toMap
    val typeEntries = emittedTypes.sorted.flatMap { emitted =>
      val upstream = upstreamOf(emitted, originOf.getOrElse(emitted, ""), renames)
      // Exclude types whose upstream FQN is in dropTypes (namespace mismatch); droppedEntries handles them.
      if dropTypes(upstream) then scala.None
      else Some(Entry("type", upstream, emitted,
        if upstream != emitted then Disposition.Renamed else Disposition.Ported,
        shape = typeShapes.getOrElse(emitted, "")))
    }

    // Substituted when an injection stands at the name; Dropped when nothing does.
    // `dropTypes` is upstream namespace, `injectedFqns` is emitted -- translate via the rename rule. // CLAUDE.md §4.56
    def emittedAt(fqn: String): String =
      balticporter.transform.PackageRenameTransform.renamed(fqn, renames.toMap)
    val droppedEntries = dropTypes.toList.sorted.map { fqn =>
      val at = emittedAt(fqn)
      // Substituted: shape from the caller. Dropped: emitted-namespace name + minimal shape
      // so `PublishedSurface.typeRows` can find it.
      val emitted = at // the EMITTED-namespace FQN, for the dependent's lookup
      if injectedFqns(at) then
        Entry("type", fqn, emitted, Disposition.Substituted,
          shape = typeShapes.getOrElse(at, ""))
      else
        Entry("type", fqn, emitted, Disposition.Dropped,
          shape = typeShapes.getOrElse(at, "form=class"))
    }

    // Genuine additions: files that replace no drop (runtime support, port helpers).
    val replacements = dropTypes.map(emittedAt)
    val added = (injectedFqns -- replacements).toList.sorted.map(fqn =>
      Entry("type", "", fqn, Disposition.Added))

    val memberEntries = srcMap.entries
      .filter(_.kind != "class")
      .sortBy(e => (e.unit, e.member))
      .map { e =>
        // `upstream` is erased manifest form; `emitted` keeps the precise signature.
        // Bean/nullary renames update fullName, so `memberOriginals` carries the reverse mapping.
        val emittedUpstream = erase(upstreamOf(e.member, e.javaPath, renames))
        // Look up bare FQN in memberOriginals (decision log omits descriptors).
        val bareUpstream = emittedUpstream.indexOf('(') match
          case i if i > 0 => emittedUpstream.substring(0, i)
          case _          => emittedUpstream
        val upstream = memberOriginals.get(bareUpstream).map { orig =>
          // The original FQN is also bare. Append the parameter part from emittedUpstream.
          val params = if emittedUpstream.length > bareUpstream.length then emittedUpstream.substring(bareUpstream.length) else ""
          // original java name replaces the member part, keeping owner
          val origCut = orig.lastIndexOf('#')
          val upCut   = bareUpstream.lastIndexOf('#')
          if origCut >= 0 && upCut >= 0 then
            // owner from upstream + original member name + params
            bareUpstream.substring(0, upCut + 1) + orig.substring(origCut + 1) + params
          else emittedUpstream
        }.getOrElse(emittedUpstream)
        val shape = memberShapes.getOrElse(e.member, "")
        // A parenless member's emitted column drops `()`.
        val emitted =
          if shape.contains("form=parenless") && e.member.endsWith("()") then e.member.stripSuffix("()")
          else e.member
        Entry("member", upstream, emitted,
          if upstream != erase(emitted) then Disposition.Renamed else Disposition.Ported,
          body = bodyKeys(upstream) || bodyKeys(e.member) || bodyKeys(emitted),
          javaPath = e.javaPath, javaLine = e.javaLine, digest = e.digest,
          shape = shape)
      }

    val droppedMembers = dropMethods.toList.sorted.map(k => Entry("member", k, "", Disposition.Dropped))

    // Engine-refused members, in both namespaces (§4.56).
    val refusedEntries = refusedMembers.toList.sortBy(_._1).map { (emitted, shape) =>
      val cut  = emitted.indexWhere(c => c == '$' || c == '#')
      val unit = if cut < 0 then emitted else emitted.substring(0, cut)
      Entry("member", erase(upstreamOf(emitted, originOf.getOrElse(unit, ""), renames)), emitted,
            Disposition.Dropped, shape = shape)
    }

    val bare = Map0(module, engine,
                    typeEntries ++ droppedEntries ++ added ++ memberEntries ++ droppedMembers ++ refusedEntries,
                    policy = policy, jdk = jdk)
    sourceRoot match
      case scala.None => bare
      case Some(root) =>
        val paths = bare.javaPaths
        bare.copy(sources = sourcesDigest(paths, p => Some(root.resolve(p))), files = paths.size)

  def render(m: Map0): String =
    val head = s"# balticporter port map\tschema=$Schema\tmodule=${m.module}\tengine=${m.engine}" +
      s"\tsources=${m.sources}\tfiles=${m.files}\tpolicy=${m.policy}\tjdk=${m.jdk}\n"
    (head + Header + "\n" + m.entries.map(_.tsv).mkString("\n") + "\n")

  // -------------------------------------------------------------------------
  // R1 — is the map still true of the base? (staleness)
  // -------------------------------------------------------------------------

  /** Fingerprint of the base's Java. File list is derived from [[Map0.javaPaths]].
    * An unresolvable path contributes `?`, reported as `Unverified`. */
  def sourcesDigest(paths: List[String], resolve: String => Option[Path]): String =
    val lines = paths.sorted.map { p =>
      val d = resolve(p).filter(Files.isRegularFile(_)) match
        case Some(f) => TirPrinter.sha256(Files.readString(f)).take(16)
        case scala.None => "?"
      s"$p\t$d"
    }
    TirPrinter.sha256(lines.mkString("\n")).take(16)

  /** Digest of the sorted `SurfacePolicy` fingerprints. `""` means "published before schema 3",
    * never "no surface policy". // ENGINE-LIMITS D4 */
  def policyDigest(fingerprints: List[String]): String =
    TirPrinter.sha256(fingerprints.sorted.mkString("\n")).take(16)

  /** Can this map be believed about the base's output, right now? */
  enum Freshness:
    /** the engine and the base's sources are the ones the map was published from. */
    case Fresh
    /** Proven out of date. Must not be used. */
    case Stale(reason: String)
    /** Not proven either way. The map IS used; the gap is reported. */
    case Unverified(reason: String)
    /** Published by a JVM with a different JDK specification. Distinct from [[Stale]]:
      * nothing changed, but the frontend read different class files. // ENGINE-LIMITS M5.10 */
    case JdkMismatch(published: String, running: String)

  /** Compare a published map against the current engine, sources on disk, policy and JDK.
    * Empty `policy`/`jdk` skips the respective comparison. */
  def freshness(m: Map0, engine: String, roots: List[Path], policy: String = "",
                jdk: String = ""): Freshness =
    if m.engine.nonEmpty && m.engine != engine then
      Freshness.Stale(s"published by engine ${m.engine}; this run is $engine — re-run the base port")
    // JDK check before policy/sources: this mismatch hides behind matching source digests.
    else if jdk.nonEmpty && m.jdk.nonEmpty && m.jdk != jdk then
      Freshness.JdkMismatch(m.jdk, jdk)
    else if jdk.nonEmpty && m.jdk.isEmpty then
      Freshness.Unverified(
        s"the map carries no `jdk=` fingerprint (published by an engine before schema $Schema), so a " +
          "base emitted under a different JDK cannot be detected — its emitted text is a function of " +
          "the class files its frontend read")
    // Policy check before source digest: a policy mismatch is proven staleness.
    else if policy.nonEmpty && m.policy.nonEmpty && m.policy != policy then
      Freshness.Stale(
        s"the base's MANIFEST has changed since the map was published (policy ${m.policy} vs $policy) — " +
          "its emitted names, forms and visibilities are policy outcomes, so the contract describes a " +
          "run that no longer exists even though every source file is unchanged. Re-run the base port")
    else if policy.nonEmpty && m.policy.isEmpty then
      Freshness.Unverified(
        s"the map carries no policy fingerprint (published by an engine before schema $Schema), so a " +
          "change to the base's manifest cannot be detected")
    else if m.sources.isEmpty then
      Freshness.Unverified("the map carries no source fingerprint (published by an older engine)")
    else
      val paths = m.javaPaths
      // Package-relative fallback; only used when exactly one root resolves it (ambiguity declines).
      val alt = m.packageRelative
      def under(q: String): List[Path] =
        roots.iterator.map(_.resolve(q)).filter(Files.isRegularFile(_)).toList
      def resolve(p: String): Option[Path] =
        under(p).headOption.orElse(alt.get(p).map(under).filter(_.sizeIs == 1).map(_.head))
      val missing = paths.filterNot(p => resolve(p).isDefined)
      if missing.nonEmpty then
        Freshness.Unverified(
          s"${missing.size} of ${paths.size} base source file(s) are not under this run's resolution " +
            s"roots (e.g. ${missing.take(3).mkString(", ")}), so freshness could not be checked")
      else if sourcesDigest(paths, p => resolve(p)) != m.sources then
        Freshness.Stale(
          s"the base's Java has changed since the map was published (${paths.size} file(s) " +
            "fingerprinted) — re-run the base port before trusting its map")
      else Freshness.Fresh

  def write(out: Path, m: Map0): Path =
    Files.createDirectories(out)
    val p = out.resolve("port-map.tsv")
    Files.writeString(p, render(m))
    p

  /** Read a map. A NEWER schema is refused; an OLDER one degrades per question to `Unknown`. */
  def read(p: Path): Either[String, Map0] =
    if !Files.exists(p) then Left(s"no port map at $p")
    else
      val lines = Files.readAllLines(p).toArray(Array.empty[String]).toList
      val meta  = lines.headOption.getOrElse("")
      val schema = """schema=(\d+)""".r.findFirstMatchIn(meta).map(_.group(1).toInt)
      schema match
        case Some(s) if s > Schema =>
          Left(s"port map at $p declares schema $s; this engine reads $Schema — it was published by a " +
            "NEWER engine, whose columns this one cannot place. Re-run this port with that engine, or " +
            "re-run the base with this one")
        case Some(s) if s < 1 => Left(s"port map at $p declares schema $s, which is not a schema")
        case None => Left(s"port map at $p has no schema header")
        case Some(s) =>
          val module  = field(meta, "module").getOrElse("?")
          val engine  = field(meta, "engine").getOrElse("?")
          val sources = field(meta, "sources").getOrElse("")
          val files   = field(meta, "files").flatMap(_.toIntOption).getOrElse(0)
          val policy  = field(meta, "policy").getOrElse("")
          val jdk     = field(meta, "jdk").getOrElse("")
          val es = lines.filterNot(l => l.startsWith("#") || l.isBlank).flatMap { l =>
            // `-1` keeps trailing empty fields (type rows and empty `shape` columns).
            l.split("\t", -1) match
              case Array(k, up, em, d, b, jp, jl, dg, sh) =>
                Some(Entry(k, up, em, Disposition.valueOf(d), b == "body", jp, jl.toIntOption.getOrElse(0), dg, sh))
              case Array(k, up, em, d, b, jp, jl, dg) =>
                Some(Entry(k, up, em, Disposition.valueOf(d), b == "body", jp, jl.toIntOption.getOrElse(0), dg))
              case _ => None
          }
          Right(Map0(module, engine, es, sources, files, policy, s, jdk))

  /** one `key=value` out of the metadata line. Tab-delimited, so a value may contain `=`. */
  private def field(meta: String, key: String): Option[String] =
    meta.split('\t').iterator.map(_.trim).collectFirst {
      case kv if kv.startsWith(s"$key=") => kv.substring(key.length + 1)
    }

  // -------------------------------------------------------------------------
  // discovery — a dependent finds its bases' maps without being told where they are
  // -------------------------------------------------------------------------

  /** A map found on disk, with its source ("run-latest" or "baseline"). */
  final case class Published(module: String, path: Path, source: String, map: Either[String, Map0])

  /** Discover all port maps under `reportRoot`, keyed by module name from the file header.
    * `run-latest` wins over `baseline`. `exclude` prevents a module from reading its own map. */
  def discover(reportRoot: Path, exclude: Set[String] = Set.empty,
               configured: List[Path] = Nil): List[Published] =
    discoverIn(reportRoot :: searchPath(configured), exclude)

  /** Extra report roots: `configured` if non-empty, else the `baseReports` debug flag as fallback.
    * Not merged: an extra root can only add a base. */
  def searchPath(configured: List[Path]): List[Path] =
    if configured.nonEmpty then configured else balticporter.tir.DebugFlags.baseReports

  /** Discover over several roots, nearest first. First wins per module. */
  def discoverIn(roots: List[Path], exclude: Set[String]): List[Published] =
    val dirs = roots.map(RealPath.of).distinct.filter(Files.isDirectory(_)).flatMap { r =>
      Files.list(r).iterator().asScala.filter(Files.isDirectory(_)).toList.sortBy(_.toString)
    }
    if dirs.isEmpty then Nil
    else
      val found = for
        d      <- dirs
        source <- List("run-latest", "baseline")
        p       = d.resolve(source).resolve("port-map.tsv")
        if Files.isRegularFile(p)
      yield
        val head   = Files.readAllLines(p).asScala.headOption.getOrElse("")
        val module = field(head, "module").getOrElse(d.getFileName.toString)
        Published(module, p, source, read(p))
      // first wins per module: run-latest listed before baseline
      found.filterNot(x => exclude(x.module)).groupBy(_.module).toList.sortBy(_._1).map(_._2.head)

  /** Parent of this run's own report dir -- the root all port reports live under. */
  def reportRoot: Path = CheckReport.dir.toAbsolutePath.normalize.getParent

  /** The map published by `module`, or `None` when unavailable. */
  def published(module: String, configured: List[Path] = Nil): Option[Map0] =
    discover(reportRoot, configured = configured).find(_.module == module).flatMap(_.map.toOption)
