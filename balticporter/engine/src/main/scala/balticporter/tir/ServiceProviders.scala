package balticporter.tir

import java.nio.file.{Files, Path}

/** THE SPI DELIVERABLE — a `META-INF/services/<interface FQN>` file, carried into the port with BOTH
  * of its namespaces moved.
  *
  * ==What it closes, and why nothing else could==
  * `ServiceLoader.load(X.class)` reads `META-INF/services/<X's FQN>`, whose lines are provider class
  * names. The engine emits `.scala` AND NOTHING ELSE, so upstream's descriptor reached the emitted
  * port by nothing at all — and the failure is the quietest one this project has (`ENGINE-LIMITS.md`
  * P5, and it is `CLAUDE.md`'s CT7 family one step short of CT7): the providers are constructed
  * reflectively from OUTSIDE the program, so the closure sees no instantiation and concludes,
  * correctly and uselessly, that nothing has to be fixed. With the file absent the loader finds ZERO
  * providers, every registration the library performs at class-initialisation silently no-ops, and
  * there is no compile error, no check count and no finding. A library then turns "not registered"
  * into a plausible wrong answer rather than an error.
  *
  * ==The two namespaces, which is what makes this a rewrite and not a copy==
  * `CLAUDE.md` §4.56: the descriptor's NAME is an interface FQN and its LINES are implementation
  * FQNs, and a renaming port moves all of them. Copied verbatim, the file names a service the port
  * does not declare and lists providers that do not exist — and no instrument can see it, because a
  * resource is not on any check's traversal. So every name here goes through the RUN'S OWN
  * `emittedName` — `PackageRenameTransform`'s rule, reached through the phase the run actually
  * placed, never a hand-written `startsWith`, and never `packageRenames` alone (a `typeRenames` or
  * `subPackages` entry moves a provider the prefix map does not mention).
  *
  * ==Three residues, counted rather than assumed==
  *   - a provider the port DROPS (`Substitutions.dropTypes`) — the descriptor would advertise a
  *     class that is not there, which is a `ServiceConfigurationError` at the first `load` and
  *     nothing before it;
  *   - the SERVICE ITSELF dropped — the same fact one level up, and worth its own kind because the
  *     next action is different (the port drops the whole SPI and should drop the descriptor with
  *     it, rather than fix a line);
  *   - a name the rename did NOT move, in a port that renames — reported, never repaired. It is
  *     legitimate (a provider genuinely outside the renamed namespace) and it is also exactly what a
  *     stale descriptor looks like, so the engine states the fact and the port decides.
  *
  * ==Format==
  * The JDK's own (`ServiceLoader`'s specification): UTF-8, one binary class name per line, `#`
  * begins a comment, blank lines and surrounding whitespace ignored. Comments and blank lines are
  * carried through VERBATIM — a descriptor's comment is upstream's text and §4.58's rule about
  * reproducing what a licence obliges a derived work to reproduce does not stop at `.scala`.
  */
object ServiceProviders:

  /** the `CheckReport` lane. Recorded only by a run whose manifest declares at least one descriptor
    * — see `PortRun.requiredChecks` for why the requirement is conditional and not the row. */
  val Name: String = "service-providers"

  /** the resource path every descriptor lives under, in every jar ever built. */
  val Dir: String = "META-INF/services"

  enum Kind:
    /** the descriptor was written, with this provider line rewritten. The POSITIVE row: a lane that
      * only ever reported trouble could hold its bar at zero by shipping nothing, which is
      * `CLAUDE.md` §5's trivia-family rule read one artifact over. */
    case Shipped
    /** the rewritten provider names a type this port DROPS. */
    case DroppedProvider
    /** the SERVICE this descriptor is named for is a type this port drops. */
    case DroppedService
    /** a name this port's rename rules did not move, on a port that renames something. */
    case Unrenamed
    /** the file holds no provider line at all. */
    case Empty

    def slug: String = this match
      case Shipped         => "shipped"
      case DroppedProvider => "dropped-provider"
      case DroppedService  => "dropped-service"
      case Unrenamed       => "unrenamed"
      case Empty           => "empty"

  /** ONE provider line, in both namespaces. `comment` is a line that is not a provider at all —
    * carried so the emitted file can be reproduced without a second parse. */
  final case class Line(raw: String, upstream: Option[String], emitted: Option[String]):
    /** the line as the port ships it. */
    def rendered: String = (upstream, emitted) match
      case (Some(u), Some(e)) => raw.replace(u, e)
      case _                  => raw

  /** ONE descriptor, planned: where it came from, what it is called at both ends, and its lines. */
  final case class Descriptor(
      source: Path,
      upstreamService: String,
      emittedService: String,
      lines: List[Line],
  ):
    def providers: List[Line] = lines.filter(_.upstream.isDefined)
    /** the resource path this run writes, relative to the resource root. */
    def target: String        = s"$Dir/$emittedService"
    def text: String          = lines.map(_.rendered).mkString("", "\n", "\n")

  /** PLAN every declared descriptor. Pure — no filesystem writes, no report — so a spec asserts the
    * rewrite and the residue without a run directory (`CheckReport`'s own argument).
    *
    * @param sources     the declared upstream files. Existence is the CALLER's fatal check, for
    *                    `Provenance.notices`' reason: a descriptor the port meant to ship and
    *                    silently did not looks exactly like one it shipped.
    * @param emittedName the run's own upstream→emitted translation (`PortRun.emittedName`), which is
    *                    the rename PHASE's rule and covers `typeRenames`/`subPackages` as well as
    *                    the prefix map.
    */
  def plan(sources: List[Path], emittedName: String => String): List[Descriptor] =
    sources.map { src =>
      val service = src.getFileName.toString
      Descriptor(
        source          = src,
        upstreamService = service,
        emittedService  = emittedName(service),
        lines           = Files.readAllLines(src, java.nio.charset.StandardCharsets.UTF_8)
          .toArray(Array.empty[String]).toList.map { raw =>
            val body = raw.indexOf('#') match
              case -1 => raw
              case i  => raw.substring(0, i)
            val name = body.trim
            // A provider line is a binary class name and nothing else. Anything that is not one —
            // a blank line, a comment, an entry with an embedded space — is carried verbatim rather
            // than guessed at: this file is upstream's text, and a "best effort" rewrite of a line
            // the format does not admit is a fabricated fact (§4.6).
            if name.isEmpty || name.exists(c => c.isWhitespace) then Line(raw, scala.None, scala.None)
            else Line(raw, Some(name), Some(emittedName(name)))
          },
      )
    }

  /** the lane, one row per provider shipped plus one per residue. `isDropped` is asked of the
    * UPSTREAM name, because a `Substitutions.dropTypes` key is written in the upstream namespace
    * (§4.56) — asking it of the emitted one is the same key read in the wrong namespace, which is
    * the defect `dropped-types.tsv` had for the life of every renaming port.
    *
    * `renames` is asked only for the [[Kind.Unrenamed]] row and is a BOOLEAN question — does this
    * port move anything at all — so a port with no rename policy does not report every line of every
    * descriptor as unmoved. */
  def findings(ds: List[Descriptor], isDropped: String => Boolean, renaming: Boolean): List[CheckReport.Finding] =
    ds.flatMap { d =>
      val path = CheckReport.relativise(d.source.toString)
      def row(kind: Kind, owner: String, line: Int, detail: String) =
        CheckReport.Finding(Name, kind.slug, owner, path, line, detail)

      val service =
        if isDropped(d.upstreamService) then
          List(row(Kind.DroppedService, d.upstreamService, 0,
            s"this port DROPS the service type itself, so `${d.target}` advertises an interface the " +
              "emitted code does not declare — drop the descriptor with it, or keep the type " +
              "[§1(b): `Substitutions.dropTypes` and `serviceProviders` disagree about one type]"))
        else Nil

      val empty =
        if d.providers.isEmpty then
          List(row(Kind.Empty, d.upstreamService, 0,
            "the descriptor declares no provider, so `ServiceLoader.load` finds nothing through it — " +
              "which is indistinguishable from the resource being absent, the failure this key exists " +
              "to remove [§1(b): check the file this port declared]"))
        else Nil

      val perLine = d.providers.zipWithIndex.flatMap { case (l, i) =>
        val u = l.upstream.get
        val e = l.emitted.get
        if isDropped(u) then
          List(row(Kind.DroppedProvider, u, i + 1,
            s"`${d.target}` would advertise `$e`, which this port DROPS — `ServiceLoader` throws " +
              "`ServiceConfigurationError` on the first load and nothing before it does " +
              "[§1(b): remove the provider from the upstream descriptor's port, or stop dropping it]"))
        else
          row(Kind.Shipped, u, i + 1,
            s"provider shipped as `$e` in `${d.target}`") ::
            (if renaming && e == u then
               List(row(Kind.Unrenamed, u, i + 1,
                 s"this port renames, and `$u` came through unmoved — legitimate for a provider " +
                   "genuinely outside the renamed namespace, and identical to a stale descriptor " +
                   "[§1(b): confirm against `packageRenames`/`typeRenames`]"))
             else Nil)
      }
      service ++ empty ++ perLine
    }

  /** WRITE the planned descriptors under `resourceRoot`, returning what was written.
    *
    * Not gated on the artifact layer, for `Provenance.notices`' reason: this is a DELIVERABLE, and
    * one that shipped only when a diagnostic switch was on would be met by accident. What keeps it
    * scoped is the empty default and the destination — `src_managed/`, the build product this run
    * already owns and `clean` already removes (§5.5), never the port root, where an untracked file
    * would blur the one distinction `git status` has to keep. */
  def write(ds: List[Descriptor], resourceRoot: Path): List[Path] =
    ds.map { d =>
      val dst = resourceRoot.resolve(Dir).resolve(d.emittedService)
      Files.createDirectories(dst.getParent)
      Files.writeString(dst, d.text, java.nio.charset.StandardCharsets.UTF_8)
      dst
    }

  def summary(ds: List[Descriptor]): String =
    if ds.isEmpty then "  none"
    else ds.map(d => s"  ${d.upstreamService} -> ${d.target}  (${d.providers.size} provider(s))").mkString("\n")
