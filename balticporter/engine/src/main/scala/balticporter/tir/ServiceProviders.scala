package balticporter.tir

import java.nio.file.{Files, Path}

/** Plans, rewrites and writes `META-INF/services/` descriptors with both namespaces moved
  * via the run's own `emittedName`. Reports four residues: dropped provider, dropped service,
  * unrenamed name, and off-JVM-unwired. // ENGINE-LIMITS P5, P9 */
object ServiceProviders:

  /** CheckReport lane. Recorded only when the manifest declares at least one descriptor. */
  val Name: String = "service-providers"

  /** the resource path every descriptor lives under, in every jar ever built. */
  val Dir: String = "META-INF/services"

  enum Kind:
    case Shipped
    case DroppedProvider
    case DroppedService
    case Unrenamed
    case Empty
    case OffJvmUnwired

    def slug: String = this match
      case Shipped         => "shipped"
      case DroppedProvider => "dropped-provider"
      case DroppedService  => "dropped-service"
      case Unrenamed       => "unrenamed"
      case Empty           => "empty"
      case OffJvmUnwired   => "off-jvm-unwired"

  /** One provider line in both namespaces; non-provider lines are carried verbatim. */
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

  /** Plan every declared descriptor. Pure — no filesystem writes. Caller must verify source existence.
    * @param sources     upstream descriptor files.
    * @param emittedName upstream→emitted FQN translation (covers all rename rules). */
  def plan(sources: List[Path], emittedName: String => String): List[Descriptor] =
    sources.map { src =>
      val service = src.getFileName.toString
      Descriptor(
        source          = src,
        upstreamService = service,
        emittedService  = emittedName(service),
        lines           = stripBom(Files.readAllLines(src, java.nio.charset.StandardCharsets.UTF_8)
          .toArray(Array.empty[String]).toList).map { raw =>
            val body = raw.indexOf('#') match
              case -1 => raw
              case i  => raw.substring(0, i)
            val name = body.trim
            // non-provider lines (blanks, comments, embedded spaces) are carried verbatim
            if name.isEmpty || name.exists(c => c.isWhitespace) then Line(raw, scala.None, scala.None)
            else Line(raw, Some(name), Some(emittedName(name)))
          },
      )
    }

  // BOM stripped from raw lines — left in place it corrupts the first provider name
  private val Bom: Char = 0xFEFF.toChar

  private def stripBom(lines: List[String]): List[String] = lines match
    case first :: rest if first.nonEmpty && first.head == Bom => first.tail :: rest
    case all                                                  => all

  /** One finding per provider shipped plus one per residue.
    * `isDropped` is asked of the UPSTREAM name (§4.56). `offJvm` produces one row per descriptor. */
  def findings(ds: List[Descriptor], isDropped: String => Boolean, renaming: Boolean,
               offJvm: Set[balticporter.catalog.Platform] = Set.empty): List[CheckReport.Finding] =
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

      // one row per descriptor for non-JVM targets that cannot read it
      val offJvmRow =
        if offJvm.isEmpty || d.providers.isEmpty then Nil
        else
          val ps = offJvm.toList.map(_.toString).sorted.mkString(", ")
          List(row(Kind.OffJvmUnwired, d.upstreamService, 0,
            s"`${d.target}` is shipped for JVM discovery, and this port declares $ps — where there " +
              "is NO classpath scan and providers resolve by REGISTRATION. The wrapper's " +
              "registration sits in a generated object body that nothing in a ported library ever " +
              "forces, so `load` answers an EMPTY iterator on those backends with no compile error " +
              "and no other count — P5's defect one platform over. UNWIRED, not broken: the " +
              "trigger emission is named as future work rather than built " +
              "[§1(a) ENGINE: `ENGINE-LIMITS.md` P9, `DESIGN.md` §8.19 — narrow `targets` to " +
              "`[jvm]` if this module is not built off the JVM, which is a statement and not a " +
              "silencer]"))

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
      service ++ empty ++ offJvmRow ++ perLine
    }

  /** Write planned descriptors under `resourceRoot`. Not gated on the artifact layer. */
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
