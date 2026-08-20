package balticporter.tir

import balticporter.core.ResourceTree

import java.nio.file.{Files, Path}

import scala.jdk.CollectionConverters.*

/** THE SECOND DELIVERABLE OF A PORT THAT IS NOT `.scala` — a classpath resource, carried into the
  * port with NEITHER of its namespaces moved, because it has none.
  *
  * ==What it closes, and why nothing else could==
  * A library reads its own resource through a STRING LITERAL — `getResourceAsStream("/p/q/x.props")`
  * in a static initialiser, a file handle built from `"p/q/skin.json"` at first use. A rename decides
  * ownership STRUCTURALLY and never from a string (`CLAUDE.md` §4.56), so that literal is one no
  * phase may touch: the emitted code asks for the UPSTREAM path, which is the right emission and
  * leaves an obligation nothing in the pipeline discharged. The port then names a resource the run
  * does not ship, and the failure is [[ServiceProviders]]' exactly — no compile error, no check
  * count, no member digest, and the evidence arrives in somebody else's build as an
  * `ExceptionInInitializerError` from a static table that was never read, or as a toolkit that
  * refuses to start.
  *
  * Two libraries in two different families is what promoted this from a per-port workaround to
  * policy: one reads a single properties table for an entity map, the other loads its whole skin,
  * its i18n bundles and six shaders through hardcoded paths and will not start without them.
  *
  * ==COPY, where a descriptor is a REWRITE — the two are siblings and the port picks by CONTENT==
  * [[ServiceProviders]] exists because a `META-INF/services` file is FQNs all the way down: its NAME
  * is an interface's and its LINES are implementations', so a renaming port MUST move both or ship a
  * file advertising a service it does not declare. Everything else on the classpath is bytes the
  * program merely LOCATES — a skin, a font, a shader, a properties table, an image — so the bytes go
  * across untouched and the path with them. Rewriting one of these would break the single lookup it
  * exists for; copying a descriptor would break the loader. One mechanism each, and neither is a
  * special case of the other.
  *
  * ==DECLARED, never scanned — `DESIGN.md` §8.17's argument with a sharper measurement==
  * A resource ROOT is not a source root. Which of a library's resources are part of the DERIVED WORK
  * is a fact about that library, and an upstream resource root routinely holds files belonging to the
  * UPSTREAM BUILD rather than to the library: a cross-compiler module definition, a native-toolchain
  * configuration. Measured on the first port to take this key: of the 24 files under its upstream
  * resource root, 22 are the library's own and 2 are its build's — and one of those two NAMES the
  * upstream package this port renames, so shipping it would advertise sources that do not exist. A
  * scan ships both; a declaration is the port stating what its output contains. The reference hand
  * port of that same library ships exactly those 22, which is the control.
  *
  * ==Three residues, counted rather than assumed==
  *   - a path the emitted code NAMES that this port does not ship, where the file is sitting under a
  *     root the port already declared. That is the defect this object exists for, seen before the
  *     consumer's build sees it;
  *   - a resource SHIPPED that no emitted literal names — legitimate, because a resource routinely
  *     names another (an atlas names its image, a skin names its fonts) and no phase can walk a
  *     resource's own content, and also exactly what a stale declaration looks like. Stated, never
  *     repaired;
  *   - a declared tree with NO files, which is indistinguishable from the resource being absent —
  *     the failure this key exists to remove.
  *
  * A declared file that is not there is FATAL and is the CALLER's check, `Provenance.notices`' rule
  * exactly: a resource the port meant to ship and silently did not looks identical to one it
  * shipped.
  */
object PortResources:

  /** the `CheckReport` lane. Recorded only by a run whose manifest declares at least one tree — see
    * `PortRun.requiredChecks` for why the requirement is conditional and not the row. */
  val Name: String = "resources"

  enum Kind:
    /** the file was copied, byte for byte, to the path the emitted code names. The POSITIVE row: a
      * lane that only ever reported trouble could hold its bar at zero by shipping nothing, which is
      * `CLAUDE.md` §5's trivia-family rule read one artifact over. */
    case Shipped
    /** an emitted string literal names this path, the file EXISTS under a root this port declared,
      * and the port did not declare the file. */
    case NamedUnshipped
    /** shipped, and no emitted literal names it — see the object doc's second residue. */
    case Unnamed
    /** a declared tree that ships no file at all. */
    case Empty

    def slug: String = this match
      case Shipped        => "shipped"
      case NamedUnshipped => "named-unshipped"
      case Unnamed        => "unnamed"
      case Empty          => "empty"

  /** ONE resource, planned: where it is read from and the classpath path it keeps at both ends.
    *
    * [[path]] is `/`-separated and is BOTH halves — the file to read under `root` and the path to
    * write under the resource root — which is what makes the emitted literal comparable to it
    * without anything reconstructing a second spelling. */
  final case class Res(root: Path, path: String):
    def source: Path = path.split('/').foldLeft(root)((p, seg) => p.resolve(seg))
    /** the classpath form a `Class.getResourceAsStream` literal is written in. */
    def absolute: String = "/" + path

  /** PLAN every declared file. Pure — no filesystem writes and no report — so a spec asserts the
    * copy and the residue without a run directory (`CheckReport`'s own argument). */
  def plan(trees: List[ResourceTree]): List[Res] =
    trees.flatMap(t => t.files.map(f => Res(t.root, normalise(f))))

  /** a declared path with its accidents removed, so `"./a/b"`, `"a//b"` and `"/a/b"` are one entry.
    * A leading `/` is how a `getResourceAsStream` literal is written and is NOT part of the path
    * under the root; keeping it would resolve the copy against the filesystem root. */
  private def normalise(p: String): String =
    p.split('/').iterator.filter(s => s.nonEmpty && s != ".").mkString("/")

  /** every file under the declared roots this port did NOT declare — the CANDIDATE list, and the
    * only population the [[Kind.NamedUnshipped]] question may be asked of.
    *
    * The engine walks a root to ASK, never to SHIP: a scan that shipped would put the upstream
    * build's own files in the deliverable (see the object doc), while a scan that only proposes
    * leaves every decision with the port and cannot be wrong about the output. That is the
    * candidate-list shape `CLAUDE.md` §1 already asks of a reflective-sink list, one artifact over.
    *
    * Bounded by the declared roots on purpose. A string literal that merely LOOKS like a path is not
    * evidence of anything (§4.6's fabricated fact); a literal that equals the path of a file sitting
    * under a root this port declared is. */
  def candidates(trees: List[ResourceTree], declared: List[Res]): List[Res] =
    val have = declared.map(r => (r.root.toString, r.path)).toSet
    trees.distinctBy(_.root.toString).flatMap { t =>
      if !Files.isDirectory(t.root) then Nil
      else
        val walk = Files.walk(t.root)
        try
          walk.iterator().asScala
            .filter(Files.isRegularFile(_))
            .map(p => Res(t.root, normalise(t.root.relativize(p).toString.replace('\\', '/'))))
            .filterNot(r => have.contains((r.root.toString, r.path)))
            .toList.sortBy(_.path)
        finally walk.close()
    }

  /** the lane: one row per resource shipped, plus each residue.
    *
    * @param named does the EMITTED PROGRAM hold a string literal naming this classpath path? Asked
    *              of both spellings, because both are ordinary java — a `getResourceAsStream` is
    *              written with a leading `/` and a classpath file handle without one. Supplied by the
    *              run from the literals of the units it OWNS (`ENGINE-LIMITS.md` D2), so a dependent
    *              does not answer for its base's lookups.
    */
  def findings(shipped: List[Res], candidates: List[Res], trees: List[ResourceTree],
               named: String => Boolean): List[CheckReport.Finding] =
    def row(kind: Kind, owner: String, path: String, detail: String) =
      CheckReport.Finding(Name, kind.slug, owner, path, 0, detail)

    val empties = trees.filter(_.files.isEmpty).map { t =>
      row(Kind.Empty, CheckReport.relativise(t.root.toString),
        CheckReport.relativise(t.root.toString),
        "this tree declares no file, so the run ships nothing from it — which is indistinguishable " +
          "from the resource being absent, the failure this key exists to remove " +
          "[§1(b): name the files this module ships, or remove the entry]")
    }

    val unshipped = candidates.filter(r => named(r.path)).map { r =>
      row(Kind.NamedUnshipped, r.path, CheckReport.relativise(r.source.toString),
        s"the emitted code names `${r.path}` and this port does not ship it, although the file is " +
          s"under a resource root this manifest already declares. A classpath lookup is a STRING " +
          "LITERAL no rename may move (§4.56), so the path is correct and the COPY is what is " +
          "missing: absent, the lookup fails at first use with no compile error, no check count and " +
          "no member digest to say so " +
          "[§1(b): add it to that tree's `files`, or state why this port ships without it]")
    }

    val rows = shipped.map { r =>
      if named(r.path) then
        row(Kind.Shipped, r.path, CheckReport.relativise(r.source.toString),
          s"shipped verbatim as `${r.path}`, which the emitted code names")
      else
        row(Kind.Unnamed, r.path, CheckReport.relativise(r.source.toString),
          s"shipped verbatim as `${r.path}`, and no emitted literal names it — legitimate for a " +
            "resource another resource names (an atlas names its image, a skin its fonts) or one " +
            "this port's consumer reads, and identical to a stale entry, so the engine states it " +
            "and the port decides [§1(b): confirm against this module's `resources`]")
    }

    empties ++ unshipped ++ rows

  /** WRITE the planned resources under `resourceRoot`, VERBATIM, returning what was written.
    *
    * Not gated on the artifact layer, for `Provenance.notices`' reason: this is a DELIVERABLE, and
    * one that shipped only when a diagnostic switch was on would be met by accident. What keeps it
    * scoped is the empty default and the destination — `src_managed/`, the build product this run
    * already owns and `clean` already removes (§5.5). */
  def write(rs: List[Res], resourceRoot: Path): List[Path] =
    rs.map { r =>
      val dst = r.path.split('/').foldLeft(resourceRoot)((p, seg) => p.resolve(seg))
      Files.createDirectories(dst.getParent)
      Files.copy(r.source, dst, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
      dst
    }

  def summary(rs: List[Res]): String =
    if rs.isEmpty then "  none"
    else
      rs.groupBy(_.root.toString).toList.sortBy(_._1).map { (root, files) =>
        s"  ${CheckReport.relativise(root)} -> ${files.size} file(s), verbatim"
      }.mkString("\n")
