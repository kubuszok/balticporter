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

  /** Schema version, in the file. A consumer refuses a map written by a NEWER engine rather than
    * mis-reading it — silently mis-reading is the failure this number prevents. An OLDER one is
    * read, and every question its columns cannot answer degrades to
    * [[balticporter.tir.Surface.Answer.Unknown]] (see [[read]]).
    *
    * 2 — the header gained `sources=` / `files=`, the SOURCE fingerprint that makes design risk R1
    *     (a map gone stale against the base's emitted output) detectable rather than assumed.
    * 3 — the BASE-SURFACE CONTRACT (`DESIGN.md` §8.3). One new column, `shape`, carrying what the
    *     port EMITTED for each type and member in the porter-note `k=v` grammar; and one new header
    *     field, `policy=`, the base's sorted `SurfacePolicy` fingerprint. The two land together on
    *     purpose: a schema that changes twice regenerates every committed baseline twice, for one
    *     design that was known at the first bump. */
  val Schema = 3

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
    * @param shape    WHAT THIS PORT EMITTED, in the porter-note `k=v` grammar — schema 3's
    *                 base-surface contract (`DESIGN.md` §8.3). Sparse: most member rows carry
    *                 nothing, and a row that says nothing costs one empty column.
    *
    *                 '''Every name inside it is an EMITTED name''' (§4.56), while [[upstream]] stays
    *                 the manifest-shaped upstream name because it is the join key. The split is not
    *                 symmetric and it is deliberate: every consumer of `shape` compares it against
    *                 emitted text — a reference, a `super[X]`, an `export` selector, a stack frame —
    *                 and [[of]] is the one point where both namespaces are in scope.
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
      shape: String = "",
  ):
    def tsv: String =
      s"$kind\t$upstream\t$emitted\t$disposition\t${if body then "body" else "-"}\t$javaPath\t$javaLine\t$digest\t$shape"

    /** the contract row, parsed. `None` for a member row, for a schema-2 row, and for a type this
      * port DROPPED — a dropped type has no emitted form to describe. */
    def typeShape: Option[balticporter.tir.Surface.TypeShape] =
      if kind == "type" then balticporter.tir.Surface.parseType(shape) else scala.None

    def memberShape: balticporter.tir.Surface.MemberShape =
      balticporter.tir.Surface.parseMember(shape)

  /** @param sources a fingerprint of the base's JAVA at the moment the map was published — see
    *                [[sourcesDigest]]. Empty for a map assembled without a source root.
    * @param files   how many distinct Java files that fingerprint covers, so a consumer can say
    *                how much of the base it was able to check rather than only whether it agreed.
    * @param policy  the publisher's sorted `SurfacePolicy` fingerprint — see [[policyDigest]]. The
    *                THIRD fingerprint, and the one schema 3 could not do without: [[sources]] and
    *                [[engine]] both stay put when the base's MANIFEST changes, and schema 3's
    *                `shape` payload is full of policy outcomes (an emitted member name is a property
    *                pair, a `form` is a drop or a collapse, a `vis` is one rename entry away from a
    *                different qualifier). Without it the map is `Fresh` and WRONG — D4's signature
    *                failure re-entering through the artifact built to prevent it. Empty only for a
    *                map published by a pre-schema-3 engine.
    * @param schema  the schema the map was READ at, so a consumer can say "published by an older
    *                engine" per question instead of refusing the file (`DESIGN.md` §8.3).
    */
  final case class Map0(
      module: String,
      engine: String,
      entries: List[Entry],
      sources: String = "",
      files: Int = 0,
      policy: String = "",
      schema: Int = Schema,
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

    /** …and each of those paths ALSO as a PACKAGE-RELATIVE one, where the two differ.
      *
      * A published `javaPath` is relative to the PUBLISHER's source root, and nothing says a
      * consumer has that root. It does not, in the one shape this matters for: a base whose
      * `sourceRoot` is a multi-module CHECKOUT publishes
      * `flexmark-util-ast/src/main/java/com/vladsch/…/Block.java`, while a dependent resolves the
      * same library through the MODULE directories themselves — so **422 of 422** of that base's
      * paths lay outside every one of its dependent's roots and `PortMap.freshness` could check
      * nothing at all (`ENGINE-LIMITS.md` D11's second half). The map is still USED — `Unverified`
      * is deliberately a third value and never a `no` — and the guarantee this artifact exists to
      * give was switched off for the whole chain by one port's root.
      *
      * This is D11's OWN insight read at a PATH instead of at a package name: the declared package
      * is a SUFFIX of the path-derived one by construction, so the package-relative path is the tail
      * of `javaPath` that begins where the package begins — and the package is in the `upstream`
      * column, which is the UNRENAMED name (§4.56: an artifact joining policy to observed code
      * carries both namespaces, and this is the reading side of that). Nothing is guessed and no
      * schema column is added: a consumer derives it from rows it already has.
      *
      * A path that is ALREADY package-relative contributes nothing — there is no second form — so a
      * port whose `sourceRoot` is a package root is untouched by arithmetic rather than by a
      * branch. */
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
    * only the TOP-LEVEL type.
    *
    * ==A DIRECTORY IS NOT A PACKAGE, which is the half "the file gives the PACKAGE" assumed==
    * That sentence is true only where the `javaPath` is relative to a PACKAGE ROOT, which every
    * corpus port's `sourceRoot` was until one was a 53-module CHECKOUT: there
    * `flexmark/src/main/java/com/vladsch/flexmark/ast/Heading.java` reads as the package
    * `flexmark.src.main.java.com.vladsch.flexmark.ast`, and **9,261 of that port's 9,370 published
    * rows carried it**. Nothing could see it — the port compiles, `port-map` is a diff against a
    * baseline written the same way, and the column is READ only by a DEPENDENT — so the first
    * dependent that module ever had reported **459 fatal `BaseSurfaceAbsent` findings** on its first
    * run, about types its base emits perfectly well. `CLAUDE.md` §4.56 at a PATH: a package derived
    * from a directory is not the package java declared.
    *
    * The two derivations DISAGREE only by leading directory segments, and that is what settles it
    * without a third source of truth: the declared package is a SUFFIX of the path-derived one by
    * construction, so where the path-derived name ends with the unrenamed one, everything before it
    * is the source root's own directory structure. Note what this deliberately does NOT do — it
    * never lets `unrename` OVERRIDE the path, only TRUNCATE it, so Ashley's non-invertible flatten
    * (which `unrename` answers by declining, i.e. by returning the emitted name) fails the suffix
    * test and keeps the origin exactly as this method's own note requires. A port with no renames at
    * all is covered by the same line, because there `unrename` is the identity and the emitted name
    * IS the declared FQN. */
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
      val fromPath = (if pkg.isEmpty then simple else s"$pkg.$simple") + tail
      val declared = unrename(emitted, renames)
      // …and the truncation needs the unrenamed name to be QUALIFIED, which is the guard the first
      // spelling of this did not have. 102 of libGDX core's member rows have an emitted key that is
      // a BARE NAME — a promoted constructor parameter, whose `SrcMap` key carries no owner — and
      // for those `declared` is that bare name, which every path-derived name trivially ends with.
      // Truncating there throws the package away and publishes `list`, which is a different wrong
      // answer from the `com.badlogic.gdx.graphics.list` it replaced and not a better one. A bare
      // name says nothing about where the package starts, so the origin stands.
      val qualifiedHead = declared.indexWhere(c => c == '$' || c == '#') match
        case -1 => declared.contains('.')
        case i  => declared.substring(0, i).contains('.')
      if qualifiedHead && fromPath.endsWith("." + declared) then declared else fromPath

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
    * @param typeShapes    schema 3's contract, keyed by EMITTED FQN — what the emitter actually
    *                      wrote for each type it emitted. Empty makes every `shape` column empty and
    *                      the map a schema-3 file with no contract, which is what a caller that does
    *                      not emit (a snippet, a test) should publish.
    * @param memberShapes  …and per emitted member key, in `SrcMap`'s spelling. This is where the
    *                      §4.55 renames finally reach a consumer: the map's `upstream` column
    *                      already spells Java's name (see the note at the member entries below) and
    *                      the EMITTED name was published nowhere at all — 827 renames in one base,
    *                      recorded only in `decisions.tsv`, which nothing discovers and nothing
    *                      consumes.
    * @param policy        the sorted `SurfacePolicy` fingerprint of the manifest this run used.
    * @param refusedMembers
    *   members this run DID NOT EMIT because an engine rule could not render them — EMITTED member
    *   key → the `shape` payload naming that rule ([[balticporter.tir.Surface.MemberShape.refusal]]).
    *   Distinct from `dropMethods`, which is POLICY, and the distinction is the whole content of the
    *   row for a dependent: a policy drop can be asked back, an engine refusal cannot.
    *
    *   Until this existed, `ENGINE-LIMITS.md` C11's drop reached the contract through
    *   `TypeShape.secondaries`, which SUBTRACTS the constructor and says nothing — `primary=()
    *   primaryKind=not-funnelled` with no `()` among the secondaries is indistinguishable from a
    *   benign class with no second constructor. So a dependent's `new C()` compiled straight into the
    *   wrong answer with nothing counting it. As a `Dropped` MEMBER row it lands in the lane
    *   `PortMapTransform` already has for a dropped member's call sites, and the base's own record
    *   travels with the finding.
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
      typeShapes: scala.collection.Map[String, String] = Map.empty,
      memberShapes: scala.collection.Map[String, String] = Map.empty,
      policy: String = "",
      refusedMembers: scala.collection.Map[String, String] = Map.empty,
  ): Map0 =
    // emitted FQN -> the java file it came from, so `upstreamOf` can use the ORIGIN.
    val originOf: scala.collection.Map[String, String] =
      srcMap.entries.iterator.map(e => e.unit -> e.javaPath).toMap
    val typeEntries = emittedTypes.sorted.map { emitted =>
      val upstream = upstreamOf(emitted, originOf.getOrElse(emitted, ""), renames)
      Entry("type", upstream, emitted,
        if upstream != emitted then Disposition.Renamed else Disposition.Ported,
        shape = typeShapes.getOrElse(emitted, ""))
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
        //
        // A §4.55 MEMBER RENAME needs no undoing here, and that is worth stating rather than
        // leaving as an accident: the §4.55 passes rewrite `Symbol.name` and NOT `Symbol.fullName`,
        // which is a stored field, so the member key the source map records already spells Java's
        // name (`…FileHandle#file`, never `#file$field`) and the join key is right by construction.
        // The EMITTED name is the half that was missing, and it is in `shape`'s `name=`.
        val upstream = erase(upstreamOf(e.member, e.javaPath, renames))
        Entry("member", upstream, e.member,
          if upstream != erase(e.member) then Disposition.Renamed else Disposition.Ported,
          body = bodyKeys(upstream) || bodyKeys(e.member),
          javaPath = e.javaPath, javaLine = e.javaLine, digest = e.digest,
          shape = memberShapes.getOrElse(e.member, ""))
      }

    val droppedMembers = dropMethods.toList.sorted.map(k => Entry("member", k, "", Disposition.Dropped))

    // …and the members an ENGINE RULE refused, in BOTH namespaces (§4.56). The upstream half comes
    // from the same `upstreamOf` every other member row uses — one derivation, so a refused row and
    // an emitted row of the same owner can never disagree about what the java was called — with the
    // owner's java file taken from the unit's own source-map entry, since the refused member has none
    // of its own. The EMITTED half is kept, unlike a policy drop's, because the type IS emitted:
    // only the member is missing, so a reader who greps the emitted file has a name to grep for.
    val refusedEntries = refusedMembers.toList.sortBy(_._1).map { (emitted, shape) =>
      val cut  = emitted.indexWhere(c => c == '$' || c == '#')
      val unit = if cut < 0 then emitted else emitted.substring(0, cut)
      Entry("member", erase(upstreamOf(emitted, originOf.getOrElse(unit, ""), renames)), emitted,
            Disposition.Dropped, shape = shape)
    }

    val bare = Map0(module, engine,
                    typeEntries ++ droppedEntries ++ added ++ memberEntries ++ droppedMembers ++ refusedEntries,
                    policy = policy)
    sourceRoot match
      case scala.None => bare
      case Some(root) =>
        val paths = bare.javaPaths
        bare.copy(sources = sourcesDigest(paths, p => Some(root.resolve(p))), files = paths.size)

  def render(m: Map0): String =
    val head = s"# balticporter port map\tschema=$Schema\tmodule=${m.module}\tengine=${m.engine}" +
      s"\tsources=${m.sources}\tfiles=${m.files}\tpolicy=${m.policy}\n"
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

  /** The publisher's POLICY, fingerprinted — the third thing a map has to pin, and the one that
    * schema 2 could not see at all.
    *
    * [[freshness]] compared an engine fingerprint and a digest over the base's Java, and NEITHER of
    * them moves when the base's MANIFEST changes. Schema 3's `shape` payload is full of policy
    * outcomes: an emitted member `name` is a property pair read from the base manifest, a `form` is
    * a drop or a collapse, a `vis` is one rename entry away from a different qualifier. Edit one
    * entry in the base's manifest, re-run the DEPENDENT alone, and every source digest still matches
    * while the payload is stale — a run that reports clean while the emitted text is wrong, which is
    * `ENGINE-LIMITS.md` D4's signature failure arriving through the artifact built to prevent it.
    *
    * It is the value `ManifestAgreement` ALREADY compares (`PortManifest.fingerprint` over the
    * effective surface), sorted and digested — not a new derivation. That matters: a second
    * derivation of "what is this module's policy" is a second thing to keep in step, and §1.5's
    * guarantee is that a dependent holds the base's manifest AS A VALUE, so it can compute exactly
    * this without loading the base's build.
    *
    * The empty list still digests to something. `""` therefore means "published before schema 3"
    * and never "this module has no surface policy", which is the one confusion that would make the
    * comparison silently inert for every port with an empty surface. */
  def policyDigest(fingerprints: List[String]): String =
    TirPrinter.sha256(fingerprints.sorted.mkString("\n")).take(16)

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

  /** Compare a published map against the engine now running, the sources now on disk, and the
    * POLICY this run inherited from the module that published it.
    *
    * @param roots  where a member's relative `javaPath` may be resolved from — a dependent's
    *               `resolutionRoots`, which by construction include the base's Java.
    * @param policy what [[policyDigest]] says about the base's manifest AS THIS RUN INHERITED IT
    *               (§1.5 — a value, not a build). Empty skips the comparison, which is what a caller
    *               with no manifest (a spec, a snippet) should pass. */
  def freshness(m: Map0, engine: String, roots: List[Path], policy: String = ""): Freshness =
    if m.engine.nonEmpty && m.engine != engine then
      Freshness.Stale(s"published by engine ${m.engine}; this run is $engine — re-run the base port")
    // …before the source digest, deliberately: a policy mismatch is PROVEN staleness and a source
    // digest that matches would otherwise report `Fresh` first and hide it. That ordering IS the
    // finding — every source digest matching is the whole point of this comparison existing.
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
      // …and the PACKAGE-RELATIVE form as the second candidate, for the reason
      // [[Map0.packageRelative]] states: a publisher's root is not a consumer's, and a base whose
      // root is a multi-module checkout is otherwise unverifiable in full.
      //
      // ONE ROOT OR NONE, and that guard is the whole of why this is safe: a package-relative path
      // is by construction the same string in every module that declares that package, so two roots
      // holding one could be two different files and the digest would then be computed over
      // whichever the iterator reached first — a `Fresh` or `Stale` answer about a file the base
      // never published. Ambiguity therefore declines, and `Unverified` stands, which is the same
      // "I could not check" this whole comparison is careful to keep distinct from "it changed".
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

  /** Read a map published by another module.
    *
    * '''A NEWER schema is refused; an OLDER one is read and degrades PER QUESTION.''' The two are
    * not the same risk. A map from a newer engine has columns this engine cannot place, so reading
    * it is guessing — refused. A map from an OLDER engine is a strict prefix of this schema, every
    * column this engine knows how to read means what it says, and the only thing missing is the
    * answer to a question that engine could not answer: `shape` comes back empty and every contract
    * question about it is `Unknown("published by an older engine")`. Refusing it wholesale would
    * tell a dependent "your base is unusable" where the truth is "your base is one engine version
    * behind, and here are the three questions I cannot ask it" (`DESIGN.md` §8.3). */
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
          val es = lines.filterNot(l => l.startsWith("#") || l.isBlank).flatMap { l =>
            // `-1` keeps TRAILING empty fields. Without it Scala's `split` drops them, so every
            // `type` row — which has no javaPath, line or digest — arrived with 5 columns instead
            // of 8, matched no case, and was silently discarded. A map that loses exactly its type
            // entries while reporting success is the worst shape this artifact could fail in.
            //
            // The SAME trap one column later: schema 3's `shape` is empty on most member rows, so a
            // 9-column row whose last field is empty splits to 9 here and would split to 8 without
            // the `-1`. Both arities are accepted — the 8 is a schema-2 row and its contract answer
            // is simply absent.
            l.split("\t", -1) match
              case Array(k, up, em, d, b, jp, jl, dg, sh) =>
                Some(Entry(k, up, em, Disposition.valueOf(d), b == "body", jp, jl.toIntOption.getOrElse(0), dg, sh))
              case Array(k, up, em, d, b, jp, jl, dg) =>
                Some(Entry(k, up, em, Disposition.valueOf(d), b == "body", jp, jl.toIntOption.getOrElse(0), dg))
              case _ => None
          }
          Right(Map0(module, engine, es, sources, files, policy, s))

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
  def discover(reportRoot: Path, exclude: Set[String] = Set.empty,
               configured: List[Path] = Nil): List[Published] =
    discoverIn(reportRoot :: searchPath(configured), exclude)

  /** THE EXTRA ROOTS, and WHOSE ANSWER THEY ARE.
    *
    * A base's map decides emitted text — the funnel's fixpoint, the class-vs-object question, §4.55's
    * field names, the `export` lists — so WHICH maps a run discovers is part of that run's identity,
    * not part of an operator's session. `balticporter.baseReports` alone made it the operator's: a
    * leftover `debug.properties` entry adds a base, and two checkouts at the same commit then emit
    * differently with every count identical. That is `reportPathRoot`'s lesson (§4.6) at an input
    * that shapes the OUTPUT rather than a finding's id.
    *
    * So a port states it — `PortManifest.baseReports`, beside the `bases` it is about — and the flag
    * is a FALLBACK, consulted only where nothing states one: `DebugEmit` and the other tools that
    * have no port configuration at all, and §4.45's consumer before it has written a manifest. It is
    * not merged with a declared value, deliberately: an extra root can only ADD a base, so merging
    * would leave exactly the leftover-flag failure in place for every port that had stated its own. */
  def searchPath(configured: List[Path]): List[Path] =
    if configured.nonEmpty then configured else balticporter.tir.DebugFlags.baseReports

  /** …over SEVERAL roots, nearest first. THE ONE SEARCH PATH, and both readers take it: `PortRun`
    * builds the `Surface` from it and `PortMapTransform` resolves its own base through
    * [[published]], and two discoveries of one artifact answering differently is D6.5's failure
    * shape. Duplicates are collapsed the same way one root's are — first wins per module — so an
    * extra root can only ADD a base, never shadow the run's own tree with a stale copy. */
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
      // first wins per module: `run-latest` is listed before `baseline` for every directory.
      found.filterNot(x => exclude(x.module)).groupBy(_.module).toList.sortBy(_._1).map(_._2.head)

  /** the directory every port's report lives under — the parent of THIS run's own report dir, so a
    * consumer needs no configuration and no knowledge of any other port's layout.
    *
    * A port's own `baseReports` extends it (see [[searchPath]]) for §4.45's consumer, which has no
    * run tree of this shape at all. */
  def reportRoot: Path = CheckReport.dir.toAbsolutePath.normalize.getParent

  /** The map published by `module`, for a porting program constructing a
    * `balticporter.transform.PortMapTransform`. `scala.None` when the base has never been run or
    * its map cannot be read — which the phase reports rather than silently treating as a no-op.
    *
    * `configured` is this PORT's declared search path (`PortManifest.baseReports`), and it must be
    * the same value `PortRun` hands [[discover]]: both readers take one function for D6.5's reason —
    * two loads of one artifact answering differently is the failure the base-surface view exists to
    * remove. */
  def published(module: String, configured: List[Path] = Nil): Option[Map0] =
    discover(reportRoot, configured = configured).find(_.module == module).flatMap(_.map.toOption)
