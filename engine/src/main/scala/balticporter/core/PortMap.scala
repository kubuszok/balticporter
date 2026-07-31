package balticporter.core

import balticporter.tir.{CheckReport, SrcMap, TirPrinter}

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** What a module's port ACTUALLY DID to its upstream surface, published for its dependents.
  *
  * ==Why this exists==
  * A dependent module's frontend can only parse **Java**. Ashley resolves against
  * `libgdx/gdx/src` — the upstream sources — never against the 596 Scala files the libGDX port
  * emitted. So a dependent reaches the base's decisions by RE-DERIVING them: it inherits the base's
  * [[PortManifest]] and re-runs identically-configured phases over the same Java, and
  * [[ManifestAgreement]] verifies the two derivations agree.
  *
  * That answers *"did we intend the same thing?"* It cannot answer *"what did you produce?"* — and
  * `ManifestAgreement` documents exactly where the difference bites: it cannot see a parameterised
  * phase's CONFIGURATION unless the phase implements [[SurfacePolicy]], cannot see nested-type
  * drops, and cannot see the base's emitted output at all.
  *
  * The concrete case that prompted this: Ashley's `ImmutableArray.toArray(Class)` forwards to
  * `Array.toArray(Class)`, which the base drops. It was found by `RewriteTrace`'s orphaned-call
  * check AFTER translating and emitting. Against a published map it is a lookup, answerable before
  * translation begins.
  *
  * ==This is a PROJECTION, not new analysis==
  * Every field comes from something the engine already computes: [[SrcMap]] for members, origins
  * and digests; `Substituted` tags and `SubstitutionCheck` for drops; `PackageRenameTransform` for
  * renames; `Substitutions.inject` and `RuntimePlan` for what was added; `MethodBodyTransform` for
  * substituted bodies; `EnginePin` for the engine identity. Assembling them in one declared schema
  * is the whole of it.
  *
  * ==One rule that must not be broken==
  * A module's map is an OUTPUT and never an input to its own run. Only DEPENDENTS read it. If a
  * module's own behaviour ever depended on its own map, the map would become a second source of
  * truth able to disagree with the manifest, and re-running a port would stop being reproducible
  * from sources plus policy (CLAUDE.md §5.5). `PortMapSpec` pins this: deleting a module's own map
  * and re-running must produce byte-identical output.
  *
  * ==Encoding==
  * TSV, like every other artifact here, and for the reason already recorded for `findings.tsv`: a
  * one-entry change is a one-line diff, which a committed baseline lives or dies by. JSON either
  * re-indents on every edit or is one enormous line. The SCHEMA — the field names and their
  * meanings — is what a future `PortManifest.fromJson` would share; the encoding is not.
  */
object PortMap:

  /** Schema version, in the file. A consumer refuses an unknown MAJOR rather than mis-reading a
    * map written by a newer engine — silently mis-reading is the failure this number prevents.
    *
    * 2 — the header gained `sources=` / `files=`, the SOURCE fingerprint that makes design risk R1
    *     (a map gone stale against the base's emitted output) detectable rather than assumed. */
  val Schema = 2

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

  /** @param upstream the Java-side name (`owner#name(P1,P2)` for a member)
    * @param emitted  the name in the emitted Scala; empty when [[Disposition.Dropped]]
    * @param body     the member is emitted with a HAND-SUPPLIED body (`MethodBodyTransform`). The
    *                 signature is upstream's and the behaviour is not — a caller cannot see this
    *                 from the signature, which is precisely why it is recorded.
    */
  final case class Entry(
      kind: String, // "type" | "member"
      upstream: String,
      emitted: String,
      disposition: Disposition,
      body: Boolean = false,
      javaPath: String = "",
      javaLine: Int = 0,
      digest: String = "",
  ):
    def tsv: String =
      s"$kind\t$upstream\t$emitted\t$disposition\t${if body then "body" else "-"}\t$javaPath\t$javaLine\t$digest"

  /** @param sources a fingerprint of the base's JAVA at the moment the map was published — see
    *                [[sourcesDigest]]. Empty for a map assembled without a source root.
    * @param files   how many distinct Java files that fingerprint covers, so a consumer can say
    *                how much of the base it was able to check rather than only whether it agreed.
    */
  final case class Map0(
      module: String,
      engine: String,
      entries: List[Entry],
      sources: String = "",
      files: Int = 0,
  ):
    def types: List[Entry]   = entries.filter(_.kind == "type")
    def members: List[Entry] = entries.filter(_.kind == "member")
    /** upstream name → what a dependent will actually find. The lookup a `PortMapTransform` needs. */
    def byUpstream: scala.collection.Map[String, Entry] = entries.iterator.map(e => e.upstream -> e).toMap
    /** …restricted to one kind, because `type` and `member` share the namespace only by accident:
      * a member key always carries a `#`, but relying on that is a parse where a filter will do. */
    def byUpstream(kind: String): scala.collection.Map[String, Entry] =
      entries.iterator.filter(_.kind == kind).map(e => e.upstream -> e).toMap
    /** every distinct Java FILE this map attributes a member to — the file set [[sources]] covers,
      * derivable by a CONSUMER from the map alone, which is what makes the check reproducible
      * without the base telling the dependent which files it converted.
      *
      * A path in angle brackets is excluded: `<synthetic>` is the origin of a member no Java file
      * produced, and `SrcMap.relativise` leaves it alone precisely because it is not a path. One
      * such member in libGDX core put an unresolvable entry in the file set, which made every
      * dependent report the map as `Unverified` — a check crying wolf on its first real run. */
    def javaPaths: List[String] =
      members.map(_.javaPath).filter(p => p.nonEmpty && !p.startsWith("<")).distinct.sorted

  object Map0:
    /** the empty map — what an unconfigured consumer holds, and a total no-op by arithmetic. */
    val empty: Map0 = Map0("", "", Nil)

  private val Header =
    "#kind\tupstream\temitted\tdisposition\tbody\tjavaPath\tjavaLine\tdigest"

  /** Reverse a package rename: emitted name → the upstream name it came from.
    *
    * Longest matching VALUE prefix wins, mirroring `PackageRenameTransform`'s longest-KEY-prefix
    * rule so a round trip is exact. Cut only at a separator, for the same reason the forward
    * direction does: `sge` must not match `sgex`. When two renames share a target the reversal is
    * genuinely ambiguous; the emitted name is then reported as its own upstream rather than a
    * guess, because a wrong upstream name in a published map is worse than an absent one. */
  /** Strip generic ARGUMENTS from a member key's parameter list: `f(Class<T>)` → `f(Class)`.
    *
    * The engine spells a member key two ways and this reconciles them. [[SrcMap]] records the
    * emitted signature, generics included, because its consumer is a human reading a source map and
    * a correlator matching a stack frame. `Substitutions.dropMethods`, `MethodBodyTransform` and
    * every manifest key use the ERASED simple names, because that is what a policy author writes
    * and what Java erasure makes stable.
    *
    * A published map is a LOOKUP TABLE, so its `upstream` column must be the form a consumer holds
    * — the manifest form. Without this, `Engine#createComponent(Class)` in a manifest never matches
    * `Engine#createComponent(Class<T>)` in the map, and the miss is silent: the body-substitution
    * flag simply never appears, which is how this was found. The precise emitted signature is not
    * lost; it stays in the `emitted` column. */
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

  /** The upstream FQN a unit came from, taken from its JAVA ORIGIN rather than from its emitted
    * name.
    *
    * `unrename` inverts a package rename, and a rename is not always invertible: Ashley's policy
    * flattens `com.badlogic.ashley.core` AND `com.badlogic.ashley` onto `sge.ecs`, so `sge.ecs.X`
    * genuinely could have come from either and no tie-break gets both `sge.ecs.Engine` (from
    * `…core`) and `sge.ecs.signals.Signal` (from `…ashley`) right. Declining to guess is correct —
    * and it made every one of Ashley's 21 shared types unfindable to its own test port, because a
    * dependent looks the base up by upstream name.
    *
    * The origin is ground truth and rename-proof: `com/badlogic/ashley/core/Component.java` says
    * exactly what the java was called. This is the same rule the provenance header already follows
    * (CLAUDE.md §4.57 — take the path from `Origin`, never reconstruct it from the FQN).
    *
    * The trailing `$Inner` / `#member` of the emitted name is carried across, because the file names
    * only the TOP-LEVEL type. */
  private def upstreamOf(emitted: String, javaPath: String, renames: scala.collection.Map[String, String]): String =
    if javaPath.isEmpty || javaPath.startsWith("<") then unrename(emitted, renames)
    else
      // The file gives the PACKAGE, never the type name. A java file may declare more than one
      // top-level type — only the public one has to match the filename — and libGDX has exactly
      // that: `MtlLoader` lives in `ObjLoader.java`. Taking the FQN from the path renamed it to
      // `ObjLoader` and left the base's map without an entry a dependent could find.
      //
      // A package rename moves the PACKAGE and leaves the simple name alone, so the two halves come
      // from the two places that actually know them.
      val dir = javaPath.stripSuffix(".java").replace('\\', '/')
      val pkg = dir.lastIndexOf('/') match
        case i if i > 0 => dir.substring(0, i).replace('/', '.')
        case _          => ""
      // the emitted name's own top-level simple name, plus whatever follows it (`$Inner`, `#m(…)`).
      val cut  = emitted.indexWhere(c => c == '$' || c == '#')
      val head = if cut < 0 then emitted else emitted.substring(0, cut)
      val tail = if cut < 0 then "" else emitted.substring(cut)
      val simple = head.substring(head.lastIndexOf('.') + 1)
      (if pkg.isEmpty then simple else s"$pkg.$simple") + tail

  /** Assemble the map. Pure: every argument is something the run already holds.
    *
    * @param emittedTypes  fully-qualified names of the units this run WROTE
    * @param srcMap        the emitter's own member recording
    * @param dropTypes     the effective drop set (the module's own plus every base's)
    * @param dropMethods   likewise, for members
    * @param injectedFqns  types supplied as ready-made Scala — replacements and additions alike
    * @param bodyKeys      member keys whose body was hand-supplied
    * @param renames       the package renames this run applied
    * @param sourceRoot    the Java root the member `javaPath`s are relative to. Supplied so the map
    *                      can carry a fingerprint of the sources it was derived FROM ([[Freshness]]);
    *                      absent, the map publishes no fingerprint and a dependent can only say it
    *                      could not check.
    */
  def of(
      module: String,
      engine: String,
      emittedTypes: List[String],
      srcMap: SrcMap.Recording,
      dropTypes: Set[String],
      dropMethods: Set[String],
      injectedFqns: Set[String],
      bodyKeys: Set[String],
      renames: scala.collection.Map[String, String],
      sourceRoot: Option[Path] = scala.None,
  ): Map0 =
    // emitted FQN -> the java file it came from, so `upstreamOf` can use the ORIGIN.
    val originOf: scala.collection.Map[String, String] =
      srcMap.entries.iterator.map(e => e.unit -> e.javaPath).toMap
    val typeEntries = emittedTypes.sorted.map { emitted =>
      val upstream = upstreamOf(emitted, originOf.getOrElse(emitted, ""), renames)
      Entry("type", upstream, emitted,
        if upstream != emitted then Disposition.Renamed else Disposition.Ported)
    }

    // A dropped type is SUBSTITUTED when something stands at its name and DROPPED when nothing
    // does. The distinction is the whole content of the entry for a dependent: one is "call it, you
    // get a different implementation", the other is "every call must be gone".
    //
    // AND THE TWO SIDES ARE IN DIFFERENT NAMESPACES (CLAUDE.md §4.56). `dropTypes` is a manifest
    // key, so it is UPSTREAM; `injectedFqns` is the set of files the run actually WROTE, so it is
    // EMITTED. Compared directly, `injectedFqns(fqn)` is false for every renaming port — libGDX
    // drops `com.badlogic.gdx.utils.Json` and injects `sge.utils.Json`, and the same map came out
    // carrying `Dropped com.badlogic.gdx.utils.Json` beside `Added sge.utils.Json` with nothing
    // joining them. `Substituted` had therefore NEVER been produced by a renaming port, and the
    // first dependent to reference an injected replacement (gdx-gltf, on `Json`) was told by
    // `PortMapTransform` that the base "emits nothing at that name and nothing replaces it" about
    // a type the base ships and it compiles against — **10 false findings**.
    //
    // Translate with the rename phase's OWN rule rather than a hand-written `startsWith`; §4.56
    // spells out why (a prefix must cut only at a separator, and everything after it is carried
    // across verbatim).
    def emittedAt(fqn: String): String =
      balticporter.transform.PackageRenameTransform.renamed(fqn, renames.toMap)
    val droppedEntries = dropTypes.toList.sorted.map { fqn =>
      val at = emittedAt(fqn)
      Entry("type", fqn, if injectedFqns(at) then at else "",
        if injectedFqns(at) then Disposition.Substituted else Disposition.Dropped)
    }

    // What is left is a genuine ADDITION — a file the run wrote that replaces no drop (the runtime
    // support types, and a port's own new helpers). Subtracted in the EMITTED namespace for the
    // same reason.
    val replacements = dropTypes.map(emittedAt)
    val added = (injectedFqns -- replacements).toList.sorted.map(fqn =>
      Entry("type", "", fqn, Disposition.Added))

    val memberEntries = srcMap.entries
      .filter(_.kind != "class")
      .sortBy(e => (e.unit, e.member))
      .map { e =>
        // `upstream` is the LOOKUP key and therefore the erased, manifest-shaped form; `emitted`
        // keeps the precise signature the emitter produced.
        val upstream = erase(upstreamOf(e.member, e.javaPath, renames))
        Entry("member", upstream, e.member,
          if upstream != erase(e.member) then Disposition.Renamed else Disposition.Ported,
          body = bodyKeys(upstream) || bodyKeys(e.member),
          javaPath = e.javaPath, javaLine = e.javaLine, digest = e.digest)
      }

    val droppedMembers = dropMethods.toList.sorted.map(k => Entry("member", k, "", Disposition.Dropped))

    val bare = Map0(module, engine, typeEntries ++ droppedEntries ++ added ++ memberEntries ++ droppedMembers)
    sourceRoot match
      case scala.None => bare
      case Some(root) =>
        val paths = bare.javaPaths
        bare.copy(sources = sourcesDigest(paths, p => Some(root.resolve(p))), files = paths.size)

  def render(m: Map0): String =
    val head = s"# balticporter port map\tschema=$Schema\tmodule=${m.module}\tengine=${m.engine}" +
      s"\tsources=${m.sources}\tfiles=${m.files}\n"
    (head + Header + "\n" + m.entries.map(_.tsv).mkString("\n") + "\n")

  // -------------------------------------------------------------------------
  // R1 — is the map still true of the base? (staleness)
  // -------------------------------------------------------------------------

  /** A fingerprint of the base's JAVA, computed identically by the publisher and the consumer.
    *
    * The list of files is not configuration and is not told to the consumer: it is DERIVED from the
    * map itself ([[Map0.javaPaths]]), which attributes every member to the Java file it came from.
    * So a dependent recomputes the same digest from the map plus the sources it already resolves
    * against, with nothing to agree on beyond the map.
    *
    * A path the consumer cannot resolve contributes `?`, which can only ever make the digest
    * DIFFER. That is why an unresolvable path is reported as [[Freshness.Unverified]] before the
    * digests are compared — "I could not check" and "it changed" are different answers and a check
    * that conflates them is worse than one that admits the gap (CLAUDE.md §3). */
  def sourcesDigest(paths: List[String], resolve: String => Option[Path]): String =
    val lines = paths.sorted.map { p =>
      val d = resolve(p).filter(Files.isRegularFile(_)) match
        case Some(f) => TirPrinter.sha256(Files.readString(f)).take(16)
        case scala.None => "?"
      s"$p\t$d"
    }
    TirPrinter.sha256(lines.mkString("\n")).take(16)

  /** Can this map be believed about the base's output, right now? */
  enum Freshness:
    /** the engine and the base's sources are the ones the map was published from. */
    case Fresh
    /** PROVEN out of date. A consumer must not use it: it describes output the base no longer
      * produces, so an entry read from it is a statement about a run that no longer exists. */
    case Stale(reason: String)
    /** not proven either way — no fingerprint in the map, or sources this run cannot see. The map
      * IS used (absence of proof is not proof) and the gap is reported. */
    case Unverified(reason: String)

  /** Compare a published map against the engine now running and the sources now on disk.
    *
    * @param roots where a member's relative `javaPath` may be resolved from — a dependent's
    *              `resolutionRoots`, which by construction include the base's Java. */
  def freshness(m: Map0, engine: String, roots: List[Path]): Freshness =
    if m.engine.nonEmpty && m.engine != engine then
      Freshness.Stale(s"published by engine ${m.engine}; this run is $engine — re-run the base port")
    else if m.sources.isEmpty then
      Freshness.Unverified("the map carries no source fingerprint (published by an older engine)")
    else
      val paths = m.javaPaths
      def resolve(p: String): Option[Path] =
        roots.iterator.map(_.resolve(p)).find(Files.isRegularFile(_))
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

  /** Read a map published by another module. Refuses an unknown MAJOR schema rather than guessing
    * at fields it does not understand. */
  def read(p: Path): Either[String, Map0] =
    if !Files.exists(p) then Left(s"no port map at $p")
    else
      val lines = Files.readAllLines(p).toArray(Array.empty[String]).toList
      val meta  = lines.headOption.getOrElse("")
      val schema = """schema=(\d+)""".r.findFirstMatchIn(meta).map(_.group(1).toInt)
      schema match
        case Some(s) if s != Schema =>
          Left(s"port map at $p declares schema $s; this engine reads $Schema — regenerate it with a matching engine")
        case None => Left(s"port map at $p has no schema header")
        case _ =>
          val module  = field(meta, "module").getOrElse("?")
          val engine  = field(meta, "engine").getOrElse("?")
          val sources = field(meta, "sources").getOrElse("")
          val files   = field(meta, "files").flatMap(_.toIntOption).getOrElse(0)
          val es = lines.filterNot(l => l.startsWith("#") || l.isBlank).flatMap { l =>
            // `-1` keeps TRAILING empty fields. Without it Scala's `split` drops them, so every
            // `type` row — which has no javaPath, line or digest — arrived with 5 columns instead
            // of 8, matched no case, and was silently discarded. A map that loses exactly its type
            // entries while reporting success is the worst shape this artifact could fail in.
            l.split("\t", -1) match
              case Array(k, up, em, d, b, jp, jl, dg) =>
                Some(Entry(k, up, em, Disposition.valueOf(d), b == "body", jp, jl.toIntOption.getOrElse(0), dg))
              case _ => None
          }
          Right(Map0(module, engine, es, sources, files))

  /** one `key=value` out of the metadata line. Tab-delimited, so a value may contain `=`. */
  private def field(meta: String, key: String): Option[String] =
    meta.split('\t').iterator.map(_.trim).collectFirst {
      case kv if kv.startsWith(s"$key=") => kv.substring(key.length + 1)
    }

  // -------------------------------------------------------------------------
  // discovery — a dependent finds its bases' maps without being told where they are
  // -------------------------------------------------------------------------

  /** A map found on disk, with WHERE it came from — a consumer that reports a disagreement has to
    * be able to say which artifact it read, and a run directory and a committed baseline are not
    * the same claim. */
  final case class Published(module: String, path: Path, source: String, map: Either[String, Map0])

  /** every port-report directory's map, newest-run-first, keyed by the module that PUBLISHED it.
    *
    * The module name comes out of the file's own header, not out of the directory name: a report
    * directory is named after the migration PROGRAM (`CheckReport.dir`), and a `PortManifest` names
    * the MODULE. Those two strings agree today by convention and nothing enforces it, so the lookup
    * uses the one the map itself states.
    *
    * `run-latest` wins over `baseline` when both exist: the run directory is what the base most
    * recently produced, and a dependent run in the same session must see it. The baseline is the
    * committed fallback for a fresh checkout where nothing has been run.
    *
    * `exclude` is how design risk R2 is enforced at the only place it could be violated: a module
    * must never read its OWN map, or its behaviour would depend on its previous output and a port
    * would stop being reproducible from sources plus policy.
    *
    * KNOWN HAZARD, stated rather than guarded: two modules that publish under the SAME `module`
    * name are indistinguishable here, and the first directory alphabetically wins. That is not
    * hypothetical — every `PortRun` driven from a unit-test JVM writes into one shared
    * `port-report/<main class>` directory (`CheckReport.dir` keys on `sun.java.command`, which is
    * the launcher there), so a suite's runs overwrite each other's map. No consumer in the corpus is
    * affected, because a map is looked up by the base MANIFEST's name and no test's run label
    * matches one. Give a module a name nothing else uses.
    */
  def discover(reportRoot: Path, exclude: Set[String] = Set.empty): List[Published] =
    if !Files.isDirectory(reportRoot) then Nil
    else
      val dirs = Files.list(reportRoot).iterator().asScala.filter(Files.isDirectory(_)).toList.sortBy(_.toString)
      val found = for
        d      <- dirs
        source <- List("run-latest", "baseline")
        p       = d.resolve(source).resolve("port-map.tsv")
        if Files.isRegularFile(p)
      yield
        val head   = Files.readAllLines(p).asScala.headOption.getOrElse("")
        val module = field(head, "module").getOrElse(d.getFileName.toString)
        Published(module, p, source, read(p))
      // first wins per module: `run-latest` is listed before `baseline` for every directory.
      found.filterNot(x => exclude(x.module)).groupBy(_.module).toList.sortBy(_._1).map(_._2.head)

  /** the directory every port's report lives under — the parent of THIS run's own report dir, so a
    * consumer needs no configuration and no knowledge of any other port's layout. */
  def reportRoot: Path = CheckReport.dir.toAbsolutePath.normalize.getParent

  /** The map published by `module`, for a porting program constructing a
    * `balticporter.transform.PortMapTransform`. `scala.None` when the base has never been run or
    * its map cannot be read — which the phase reports rather than silently treating as a no-op. */
  def published(module: String): Option[Map0] =
    discover(reportRoot).find(_.module == module).flatMap(_.map.toOption)
