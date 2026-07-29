package balticporter.core

import balticporter.tir.SrcMap

import java.nio.file.{Files, Path}

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
    * map written by a newer engine — silently mis-reading is the failure this number prevents. */
  val Schema = 1

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

  final case class Map0(module: String, engine: String, entries: List[Entry]):
    def types: List[Entry]   = entries.filter(_.kind == "type")
    def members: List[Entry] = entries.filter(_.kind == "member")
    /** upstream name → what a dependent will actually find. The lookup a `PortMapTransform` needs. */
    def byUpstream: scala.collection.Map[String, Entry] = entries.iterator.map(e => e.upstream -> e).toMap

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

  /** Assemble the map. Pure: every argument is something the run already holds.
    *
    * @param emittedTypes  fully-qualified names of the units this run WROTE
    * @param srcMap        the emitter's own member recording
    * @param dropTypes     the effective drop set (the module's own plus every base's)
    * @param dropMethods   likewise, for members
    * @param injectedFqns  types supplied as ready-made Scala — replacements and additions alike
    * @param bodyKeys      member keys whose body was hand-supplied
    * @param renames       the package renames this run applied
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
  ): Map0 =
    val typeEntries = emittedTypes.sorted.map { emitted =>
      val upstream = unrename(emitted, renames)
      Entry("type", upstream, emitted,
        if upstream != emitted then Disposition.Renamed else Disposition.Ported)
    }

    // A dropped type is SUBSTITUTED when something stands at its name and DROPPED when nothing
    // does. The distinction is the whole content of the entry for a dependent: one is "call it, you
    // get a different implementation", the other is "every call must be gone".
    val droppedEntries = dropTypes.toList.sorted.map { fqn =>
      Entry("type", fqn, if injectedFqns(fqn) then fqn else "",
        if injectedFqns(fqn) then Disposition.Substituted else Disposition.Dropped)
    }

    val added = (injectedFqns -- dropTypes).toList.sorted.map(fqn =>
      Entry("type", "", fqn, Disposition.Added))

    val memberEntries = srcMap.entries
      .filter(_.kind != "class")
      .sortBy(e => (e.unit, e.member))
      .map { e =>
        // `upstream` is the LOOKUP key and therefore the erased, manifest-shaped form; `emitted`
        // keeps the precise signature the emitter produced.
        val upstream = erase(unrename(e.member, renames))
        Entry("member", upstream, e.member,
          if upstream != erase(e.member) then Disposition.Renamed else Disposition.Ported,
          body = bodyKeys(upstream) || bodyKeys(e.member),
          javaPath = e.javaPath, javaLine = e.javaLine, digest = e.digest)
      }

    val droppedMembers = dropMethods.toList.sorted.map(k => Entry("member", k, "", Disposition.Dropped))

    Map0(module, engine, typeEntries ++ droppedEntries ++ added ++ memberEntries ++ droppedMembers)

  def render(m: Map0): String =
    val head = s"# balticporter port map\tschema=$Schema\tmodule=${m.module}\tengine=${m.engine}\n"
    (head + Header + "\n" + m.entries.map(_.tsv).mkString("\n") + "\n")

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
          val module = """module=([^\t]+)""".r.findFirstMatchIn(meta).map(_.group(1)).getOrElse("?")
          val engine = """engine=([^\t]+)""".r.findFirstMatchIn(meta).map(_.group(1)).getOrElse("?")
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
          Right(Map0(module, engine, es))
