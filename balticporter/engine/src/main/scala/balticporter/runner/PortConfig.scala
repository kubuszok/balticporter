package balticporter.runner

import balticporter.catalog.Platform
import balticporter.core.{AnnotationPolicy, FrontendConfig, ParityRef, PortManifest, Provenance, RealPath, RuntimeMode}
import balticporter.sbtgen.SbtGen
import balticporter.tir.{ConfigError, ConfigView}

import java.nio.file.{FileSystems, Files, Path}
import scala.jdk.CollectionConverters.*

/** A port, read from a HOCON file — the THIRD front door to [[PortRun]].
  *
  * ==Why this exists, given CLAUDE.md §1.5==
  * §1.5 says the shared surface is a VALUE and warns that "a manifest DSL would move the policy out
  * of reach of the consumer's compiler". That warning is about a SECOND SOURCE OF TRUTH, and it
  * stands. This is not one:
  *
  *   - the conf path CONSTRUCTS `PortManifest`, `FrontendConfig`, `Provenance` and `PortRun` — the
  *     same values a Scala `main` constructs, through the same constructors, with no parallel
  *     model and no second code path inside `PortRun`. Every §1.5 check (`ManifestAgreement`,
  *     `PortManifest.fingerprint`, the resolution-root/base rule) sees a value it cannot tell apart
  *     from a hand-built one, because there is nothing to tell apart;
  *   - manifest INHERITANCE is `base.extendedBy(dependent)`, the operator §1.5 names, called on a
  *     manifest read from the base's own conf. A dependent still cannot restate the shared surface
  *     by accident, because it does not restate it at all;
  *   - and everything a value needs that CONFIG CANNOT EXPRESS arrives as CODE, discovered through
  *     [[balticporter.tir.TransformFactory]]. There is no classname-in-a-string, no expression language and no
  *     reflective construction of a lambda. A factory NAME resolving through `ServiceLoader` to a
  *     class the consumer compiled is the one sanctioned indirection, and it is exactly as typed as
  *     the Scala path: the predicate lives in the consumer's repository, in Scala, checked by the
  *     consumer's compiler.
  *
  * What is genuinely given up is compiler checking of the DECLARATIVE half — a `dropTypes` entry is
  * a `String` either way, and a typo is a no-op in both forms. That is DESIGN.md §5.6's own
  * assessment ("stronger than it appears… thin for exactly the parts proposed as config"), and it
  * is why the Scala path answers it with `PolicyReport` and this path answers it with a
  * refusal on any key nobody read (see [[HoconView]]).
  *
  * ==The schema==
  * Exactly what `PortRun` takes, in the same words. Every key is optional unless marked required.
  *
  * {{{
  * label = "sge-graphs"                   # required — the prefix on every console line
  * base  = "main.conf"                    # a dependent's BASE; see below
  *
  * input {
  *   sourceRoot      = "…/src/main/java"  # required
  *   files           = ["a/B.java"]       # explicit list, relative to sourceRoot; or:
  *   includeGlobs    = ["**.java"]        # globs over the path relative to sourceRoot
  *   excludeGlobs    = ["**\/package-info.java", "package-info.java", …]
  *   classpath       = ["…jar"]
  *   classpathFile   = "out/test-classpath.txt"   # one path-separator-joined line, as `cs` writes
  *   resolutionRoots = ["…/src/main/java"]
  *   resolutionExcludes = ["com/example/emu"]  # paths under a resolution root NOT to parse,
  *                                             # relative to the root — a super-source tree that
  *                                             # REDECLARES classes is otherwise two declarations
  *                                             # of one FQN and the model is refused outright
  *   preservedAnnotations = ["com.fasterxml.jackson."]  # which ARGUMENT-BEARING annotation
  *                                             # families this port claims ON A TYPE. Absent =
  *                                             # none, which is what every port did before the
  *                                             # key existed; a MARKER annotation needs no entry
  * }
  *
  * output { portRoot = "…", sourceSet = "main" }   # both required; sourceSet is main | test
  *
  * manifest {                             # required — a port without one is not a port (§1.5)
  *   name           = "sge-graphs"        # required
  *   governs        = ["space.earlygrey.simplegraphs"]
  *   dropTypes      = []
  *   dropMethods    = []
  *   packageRenames { "space.earlygrey.simplegraphs" = "sge.graphs" }
  *   typeRenames    { "liqp.filters.Map" = "MapFilter" }      # bare name = renamed in place
  *   subPackages    { "p.Algorithms" = "internal" }           # nested under p.internal, in place
  *   flattenNestedTypes = ["p.Connection$DirectedConnection"] # promoted to top level
  *   allowPackageSplit  = []                                  # boundary moves declared deliberate
  *   inject         = ["corpus/overrides"]
  *   serviceProviders = ["../upstream/src/main/resources/META-INF/services/p.Spi"]
  *                                             # upstream SPI descriptors this module ships. Copied
  *                                             # into src_managed's resource tree with the SERVICE
  *                                             # and every PROVIDER renamed through this port's own
  *                                             # rules. Not inherited (`inject`'s line); a declared
  *                                             # file that is not there is fatal
  *   resources      = [ { root  = "../upstream/src/main/resources",
  *                        files = ["p/q/entities.properties"] } ]
  *                                             # the descriptor's COMPLEMENT: classpath resources
  *                                             # this module ships VERBATIM, at the upstream paths
  *                                             # the emitted lookups name. Nothing is rewritten — a
  *                                             # lookup is a string literal no rename may touch —
  *                                             # and the files are DECLARED rather than scanned for
  *                                             # (an upstream resource root also holds the upstream
  *                                             # BUILD's own files). Not inherited; a declared file
  *                                             # that is not there is fatal
  *   surface        = [ { transform = "collections" }, { transform = "mutable-params" } ]
  *   resolutions { "com.foo.Bar#baz" = "wrap-checked" }  # PER-LOCATION remedy selection: pick one
  *                                             # of the alternatives a phase or check OFFERED at
  *                                             # this member. Ids are validated at load against the
  *                                             # remedies this classpath declares. Inherited, and
  *                                             # MUST agree across a chain
  *   targets        = ["jvm", "js", "native"]  # default: all three — what `PortabilityCheck`
  *                                             # asked before it had a parameter. Narrowing is
  *                                             # this module's decision; a DEPENDENT may not
  *                                             # declare a platform its base does not.
  *   dependencies   = [ { org = "io.github.cquiroz", name = "scala-java-time",
  *                        rev = "2.6.0", cross = "scala" } ]   # java | scala (default) | platform
  * }
  *
  * provenance {                           # omitted ⇒ None
  *   upstreamName = "simple-graphs"       # required within the block
  *   originalLicense = "MIT"              # required within the block
  *   sourcePathPrefix = "src/main/java"   # required within the block
  *   upstreamCommit = "…"                 # default: derived from the source tree's git state
  *   sourceRoot     = "…"                 # default: input.sourceRoot, absolute
  *   notices        = ["../LICENSE"]      # upstream notice files copied beside the emitted code
  * }
  *
  * runtimeMode = "vendored"               # dependency (default) | vendored
  * determinism = "emission"               # emission (default) | full | off
  * supportSources { "com.foo.Shim" = "…" }
  * cache = ".balticporter/cache"
  * lenient = true
  * preview = false
  * nextStep = "just sg-measure"
  * project { … }                          # omitted ⇒ None: no build file is written
  * }}}
  *
  * ==Paths are relative to THE CONF FILE==
  * Not to the working directory and not to `balticporter.root`. A configuration that only resolves
  * when it is run from one directory is not a configuration; anchoring on the file makes a port
  * directory relocatable and reproducible, which is what CLAUDE.md §5 asks of every input. HOCON
  * substitutions against `-D` system properties are still available for the values an operator
  * genuinely supplies (`${balticporter.root}`), and are the escape hatch when a path must come from
  * outside the file. Resolution is LEXICAL (`resolve` then `normalize`), exactly as every migration
  * program does it, so a checkout reached through a symlinked parent — a git worktree, CLAUDE.md
  * §5.4 — resolves to the same place either way.
  *
  * ==`base` inherits the MANIFEST, and nothing else==
  * `base = "main.conf"` loads that file's `manifest` and returns `thatManifest.extendedBy(mine)`.
  * It is NOT an include: the base's `input`, `output`, `provenance`, `runtimeMode` and `inject`
  * are facts about the BASE's build, and §1.5's table puts every one of them on the must-differ
  * side. `inject` is the one that looks wrong and is not — exactly one module ships each
  * replacement file, and `extendedBy` already draws that line.
  */
object PortConfig:

  /** the default file selection: every `.java` under `sourceRoot`, minus Java's two
    * declaration-only files, which have no type to emit. Written out rather than pattern-matched on
    * a suffix, because `**package-info.java` would also name `Mypackage-info.java` — a prefix (or a
    * suffix) is not a structural fact about anything (CLAUDE.md §4.56).
    *
    * The keys are `includeGlobs`/`excludeGlobs` and not the obvious `include`/`exclude` because
    * **`include` is a HOCON KEYWORD**: `include = [...]` is a parse error ("include keyword is not
    * followed by a quoted string"), so the obvious spelling is unusable and a port would meet it as
    * a stack trace from the config library rather than as anything this engine said. */
  val DefaultInclude: List[String] = List("**.java")
  val DefaultExclude: List[String] = List(
    "**/package-info.java", "package-info.java",
    "**/module-info.java", "module-info.java",
  )

  /** THE entry point: a `.conf` and the passthrough args a migration program's `main` receives. */
  def load(
      conf: Path,
      args: Seq[String] = Nil,
      registry: TransformRegistry = TransformRegistry.discover(),
  ): PortRun =
    val file = conf.toAbsolutePath.normalize
    val view = HoconView.root(HoconView.parse(file))
    val run  = read(view, file, args, registry, Nil)
    refuseUnread(view, file)
    run

  /** the MANIFEST alone, for a spec or for an embedder composing manifests from files.
    *
    * Reads the same halves a `base` reference reads and checks the same keys, so `manifest(f)` and
    * "`f` used as somebody's base" cannot disagree about what a file says. The build halves are
    * not checked here because they are not read here — that is [[load]]'s job. */
  def manifest(conf: Path, registry: TransformRegistry = TransformRegistry.discover()): PortManifest =
    val file = conf.toAbsolutePath.normalize
    val view = HoconView.root(HoconView.parse(file))
    manifestOnly(view)
    val m = readManifest(view, file, registry, Nil)
    refuseUnread(view, file)
    m

  /** A conf read for its MANIFEST alone — as a `base`, or through [[manifest]]. Its other halves
    * are facts about THAT module's build (§1.5's must-differ column) and are deliberately not this
    * run's business, so they are not reported as junk. They are still checked, by the run that
    * loads the same file as its own configuration. */
  private def manifestOnly(view: HoconView): Unit =
    view.keys.filterNot(k => k == "manifest" || k == "base").foreach(view.markRead)

  // -------------------------------------------------------------------------------------------

  private def refuseUnread(view: HoconView, file: Path): Unit =
    val junk = view.unread
    if junk.nonEmpty then
      throw ConfigError(file.toString,
        s"${junk.size} key(s) nobody read: ${junk.mkString(", ")}. HOCON accepts any key it is " +
          "given, so a misspelt one is a policy entry that silently does nothing — the §1(b) " +
          "no-op this engine refuses everywhere else. Fix the spelling, or delete the key.")

  private def read(
      view: HoconView, file: Path, args: Seq[String],
      registry: TransformRegistry, seen: List[Path],
  ): PortRun =
    val dir      = file.getParent
    val label    = view.requireString("label")
    val input    = view.requireChild("input")
    val output   = view.requireChild("output")
    val srcRoot  = resolvePath(dir, input.requireString("sourceRoot"))

    val frontend = FrontendConfig(
      sourceRoot      = srcRoot,
      files           = selectFiles(input, srcRoot),
      classpath       = classpath(input, dir),
      resolutionRoots = input.strings("resolutionRoots").getOrElse(Nil).map(resolvePath(dir, _)),
      // …and what to leave OUT of them. RELATIVE to whichever root contains it, and NOT resolved
      // against the config's directory: the same string has to answer for every root, exactly as
      // `includeGlobs`/`excludeGlobs` are relative to `sourceRoot`. Absent is `Nil`, which is the
      // behaviour every port had before the key existed — the root is added whole.
      resolutionExcludes = input.strings("resolutionExcludes").getOrElse(Nil),
      // FQN prefixes, upstream namespace, and NOT paths — a family is a name, so nothing here is
      // resolved against the config's directory. Absent is `AnnotationPolicy.none`, which is the
      // behaviour every port had before the key existed (`ENGINE-LIMITS.md` T16).
      preservedAnnotations = AnnotationPolicy(input.strings("preservedAnnotations").getOrElse(Nil)),
    )

    PortRun(
      label     = label,
      portRoot  = resolvePath(dir, output.requireString("portRoot")),
      sourceSet = output.enumerated("sourceSet",
                    Map("main" -> SourceSet.Main, "test" -> SourceSet.Test))
                    .getOrElse(throw ConfigError(output.at("sourceSet"), "required, and absent")),
      frontend  = frontend,
      // The manifest SUPPLIES phases, subs and packageRenames; `PortRun` refuses a run that passes
      // either source beside it, and this path never has a second source to pass.
      phases    = Nil,
      manifest  = Some(readManifest(view, file, registry, seen)),
      provenance = view.child("provenance").map(p => Provenance(
        upstreamName     = p.requireString("upstreamName"),
        upstreamCommit   = p.string("upstreamCommit").getOrElse(VendoredCommit.of(srcRoot)),
        originalLicense  = p.requireString("originalLicense"),
        sourcePathPrefix = p.requireString("sourcePathPrefix"),
        sourceRoot       = p.string("sourceRoot").map(resolvePath(dir, _).toString)
                             .getOrElse(srcRoot.toString),
        // conf-relative like every other path here, because the upstream ROOT is not the source
        // root: a licence file usually sits several directories above `src/main/java`.
        notices          = p.strings("notices").getOrElse(Nil).map(resolvePath(dir, _)),
      )),
      runtimeMode = view.enumerated("runtimeMode",
                      Map("dependency" -> RuntimeMode.Dependency, "vendored" -> RuntimeMode.Vendored))
                      .getOrElse(RuntimeMode.Dependency),
      supportSources = view.stringMap("supportSources").getOrElse(Map.empty),
      project     = view.child("project").map(projectSpec),
      determinism = determinismOf(view, args),
      cache       = view.string("cache").map(resolvePath(dir, _)),
      lenient     = view.bool("lenient").getOrElse(true),
      preview     = view.bool("preview").getOrElse(false),
      nextStep    = view.string("nextStep").getOrElse(""),
      // …and every remedy this CLASSPATH declares, so the run can tell a selection whose phase is
      // simply not enabled from one whose id does not exist. A factory speaks for the phase it would
      // build, which is what makes the known set complete without constructing anything.
      knownRemedies = registry.remedies,
    )

  /** A CLI `--determinism=` flag beats the file, and the file beats the default.
    *
    * The flag is how an operator asks one run to translate twice from scratch, which is a property
    * of THIS INVOCATION rather than of the port; the file states what the port normally wants. A
    * file value that could not be overridden would make `--determinism=full` a lie on exactly the
    * ports that had opted out of it. */
  private def determinismOf(view: HoconView, args: Seq[String]): Determinism =
    // Read the file's value FIRST and unconditionally, even when the flag will win. A key read only
    // on some code paths is a key the unread-key check reports as junk on the others — which is
    // exactly how this was found: `--determinism=full` short-circuited the read and the run then
    // refused its own valid configuration.
    val declared = view.enumerated("determinism", Map(
      "off" -> Determinism.Off, "emission" -> Determinism.Emission, "full" -> Determinism.Full,
    ))
    if args.contains(Determinism.FullFlag) || args.contains(Determinism.OffFlag) then
      Determinism.fromArgs(args)
    else declared.getOrElse(Determinism.Emission)

  private def readManifest(
      view: HoconView, file: Path, registry: TransformRegistry, seen: List[Path],
  ): PortManifest =
    val dir = file.getParent
    val m   = view.requireChild("manifest")
    // WHERE THE BASES PUBLISHED — read BEFORE the surface entries, and ANCHORED, because a
    // `{ transform = "port-map-migration" }` entry loads its maps at CONSTRUCTION time and a
    // `TransformFactory` takes nothing but its own `ConfigView`. Anchoring publishes the port's own
    // value through the one accessor both readers already take, exactly as `PortRun
    // .anchorReportPaths` does for `reportPathRoot` (§4.6: a value that shapes what a run produces
    // comes from the PORT, and the engine makes it reach every reader rather than asking each one to
    // be handed it). Giving the factory a `reports = […]` key of its own would be a second home for
    // one value, which is what §5.6 refuses.
    //
    // …and only for the conf being loaded as THIS RUN's own (`seen.isEmpty`). A base conf's
    // `baseReports` says where the BASE's bases published, which is a fact about that module's build
    // and none of this run's business — the same line §1.5 draws for `frontend` and `inject`.
    val reports = view.strings("baseReports").getOrElse(Nil).map(resolvePath(dir, _))
    if reports.nonEmpty && seen.isEmpty then
      System.setProperty(balticporter.tir.DebugFlags.Prefix + "baseReports",
                         reports.mkString(java.io.File.pathSeparator))
    // read BEFORE the manifest is built, because the per-location SELECTIONS are validated against
    // the remedies this module's own surface offers and a `PortManifest` cannot be half-constructed.
    val surface = m.children("surface").getOrElse(Nil).map(surfaceEntry(registry))
    val own = PortManifest(
      name           = m.requireString("name"),
      governs        = m.strings("governs").getOrElse(Nil).toSet,
      dropTypes      = m.strings("dropTypes").getOrElse(Nil).toSet,
      dropMethods    = m.strings("dropMethods").getOrElse(Nil).toSet,
      packageRenames = m.stringMap("packageRenames").getOrElse(Map.empty),
      // the PER-TYPE half of the same phase (M6). Data like the map above, and refused the same
      // way if written as a `{ transform = "package-rename" }` surface entry: the rename's position
      // is an obligation no `runsAfter` can state (§4.56), so `PortRun` places it, once.
      typeRenames        = m.stringMap("typeRenames").getOrElse(Map.empty),
      subPackages        = m.stringMap("subPackages").getOrElse(Map.empty),
      flattenNestedTypes = m.strings("flattenNestedTypes").getOrElse(Nil).toSet,
      allowPackageSplit  = m.strings("allowPackageSplit").getOrElse(Nil).toSet,
      surface        = surface,
      // PER-LOCATION REMEDY SELECTION. Data like the maps above — `ConfigView` stays dumb and the
      // LOADER validates, exactly as `TransformFactory.scopeOf` builds a `RuleScope` here and not in
      // the view. What cannot survive this door is anything but a flat lookup: a predicate-selected
      // remedy is refused for `OpaqueSpec.hints`' reason (DESIGN.md §5.7), and a port that needs one
      // writes a §1(c) rule in Scala.
      resolutions    = readResolutions(m, surface, registry),
      inject         = m.strings("inject").getOrElse(Nil).map(resolvePath(dir, _)),
      // …and the SPI half of the deliverable, which is `inject`'s line at a RESOURCE: the upstream
      // `META-INF/services` files this module ships, copied with BOTH namespaces moved through this
      // port's own rename rules. A build fact, so not inherited; empty is the no-op; a declared file
      // that is not there is fatal at the run (ENGINE-LIMITS.md P5).
      serviceProviders = m.strings("serviceProviders").getOrElse(Nil).map(resolvePath(dir, _)),
      // …and the descriptor's COMPLEMENT: everything else on this module's classpath, copied
      // VERBATIM at the upstream path the emitted lookup names (DESIGN.md §8.22). Two keys and not
      // one, because the two acts differ — a descriptor is FQNs and is rewritten, a resource is
      // bytes the program merely locates and must not be. Same three properties: a build fact so
      // not inherited, empty is the no-op, a declared file that is not there is fatal at the run.
      resources      = m.children("resources").getOrElse(Nil).map(resourceEntry(dir)),
      baseReports    = if seen.isEmpty then reports else Nil,
      // WHICH BACKENDS this module is ported for — omitted means all three, which is the semantics
      // `PortabilityCheck` had before it took a parameter. Not inherited (§1.5's right-hand column),
      // so a base conf's value is the base's; unlike `baseReports` there is nothing to anchor,
      // because the value is read off the manifest the run holds.
      targets        = m.strings("targets").map(readTargets(m)).getOrElse(Platform.values.toSet),
      // …and the artifacts this module's build adds because it took a `Verdict.Depend`'s advice.
      // A build fact, so not inherited (`inject`'s line); empty is the no-op and the whole corpus.
      dependencies   = m.children("dependencies").getOrElse(Nil).map(dependencyEntry),
      // EXTERNAL MEMBERS PARENLESS ON SOME PLATFORMS — a build fact about which platform shims
      // this module's classpath resolves. Not inherited (`dependencies`' line); empty is the no-op.
      externalParenless = m.strings("externalParenless").getOrElse(Nil).toSet,
      // THE REFERENCE HAND PORT for this module. NOT inherited — a hand port is a fact about THIS
      // module's destination, not the shared surface. Empty / absent = the check is a no-op AND
      // records nothing (§1(b)'s rule).
      parity         = m.child("parity").map(p =>
        ParityRef(
          roots          = p.strings("roots").getOrElse(Nil).map(resolvePath(dir, _)),
          packageMapping = p.stringMap("packageMapping").getOrElse(Map.empty),
        )),
    )
    view.string("base") match
      case scala.None    => own
      case Some(basePath) =>
        val baseFile = resolvePath(dir, basePath)
        // RESOLUTION stays lexical (see `resolvePath` and the class doc); the CYCLE TEST does not,
        // and using two different functions is what keeps the distinction honest. §5.4's failure
        // here is a CRASH rather than a wrong number: a base chain spelled through a symlink — a
        // git worktree reaching a sibling checkout is the normal case — compares unequal at every
        // hop, so the cycle is never detected and `readManifest` recurses to a `StackOverflowError`
        // instead of raising the `ConfigError` written just below.
        if seen.exists(s => RealPath.of(s) == RealPath.of(baseFile)) then
          throw ConfigError(view.at("base"),
            s"$baseFile is already in this base chain (${(seen :+ baseFile).mkString(" -> ")}); " +
              "a manifest cannot be a base of itself")
        val baseView = HoconView.root(HoconView.parse(baseFile))
        manifestOnly(baseView)
        val base = readManifest(baseView, baseFile, registry, seen :+ baseFile)
        refuseUnread(baseView, baseFile)
        base.extendedBy(own)

  /** `resolutions { "com.foo.Bar#baz" = "wrap-checked" }` — which remedy this port picks WHERE.
    *
    * The value is validated against the CLOSED remedy vocabulary at LOAD, and an unrecognised token
    * is a `ConfigError` naming the alternatives. That is `TransformFactory.scopeOf`'s door, one key
    * over: `ConfigView` reports the map and knows nothing about remedies, and the consuming code
    * turns the strings into a policy or refuses them. A silently ignored value here would be exactly
    * the §1(b) no-op the whole config front door exists to prevent — worse than most, because a port
    * that selected a remedy and got none reads as a port that never asked.
    *
    * The vocabulary is assembled from three places, and the third is what stops this door being too
    * strict: the checks the engine registers, whatever the classpath's `TransformFactory` instances
    * DECLARE (a factory speaks for the phase it would build, so nothing has to be constructed), and
    * the surface phases this module actually configured. An id known to none of those cannot be
    * carried out by any run of this engine and is a typo; an id known to one of them and not enabled
    * HERE is a different mistake, and it is reported at run time as a policy finding naming the
    * phase to add rather than refused here as a spelling error.
    *
    * The KEY's shape is deliberately NOT checked here. It is a `MemberKey`, the binder parses every
    * one of them and its `Malformed` refusal already says what to write — so validating it twice
    * would give the two front doors two different sentences for one mistake. */
  private def readResolutions(
      m: ConfigView, surface: List[balticporter.tir.Phase], registry: TransformRegistry,
  ): Map[String, String] =
    val declared = m.stringMap("resolutions").getOrElse(Map.empty)
    if declared.isEmpty then Map.empty
    else
      val known = knownRemedies(surface, registry)
      declared.toList.sortBy(_._1).foreach { (key, id) =>
        if !known.contains(id) then
          throw ConfigError(m.at("resolutions"),
            s"""'$id' (selected at "$key") is not a remedy this engine offers. """ +
              (if known.isEmpty then
                 "No phase or check on this classpath declares one at all — a remedy is DECLARED by " +
                   "the mechanism that can carry it out, so either the phase that offers it is " +
                   "missing from this `surface`, or its library is not on the classpath."
               else s"Declared here: ${known.ids.mkString(", ")}."))
      }
      declared

  /** every remedy a run loaded from THIS conf could possibly know about — see [[readResolutions]].
    *
    * A factory-declared remedy and the same remedy declared by the phase the factory builds are ONE
    * entry, not a duplicate: `RemedyVocabulary` refuses two DIFFERENT remedies claiming one id and
    * accepts the same one twice, which is exactly the shape this union has. */
  private def knownRemedies(
      surface: List[balticporter.tir.Phase], registry: TransformRegistry,
  ): balticporter.tir.RemedyVocabulary =
    balticporter.tir.RemedyVocabulary.from(
      PortRun.CheckRemedies ++ surface.collect { case r: balticporter.tir.RemedySource => r }
    ) ++ registry.remedies

  /** `targets = ["jvm", "native"]` — the backends this module is ported for.
    *
    * An unknown name is a `ConfigError` and not a silent drop, for `PortManifest.targets`' own
    * reason: a misspelt platform would NARROW the target set, which narrows the rule set, which
    * makes `portability(*)` fall — a port improving because somebody typed `scalanative` is exactly
    * the unreadable baseline promotion the default was chosen to avoid. */
  private val TargetNames: Map[String, Platform] = Map(
    "jvm" -> Platform.Jvm, "js" -> Platform.ScalaJs, "scala-js" -> Platform.ScalaJs,
    "native" -> Platform.ScalaNative, "scala-native" -> Platform.ScalaNative,
  )

  /** the platform names a `.conf` writes, shared by the manifest's own `targets` and by any factory
    * whose phase takes the same §1(b) parameter — one spelling, so a port cannot write `js` in one
    * place and have it mean something else in the other. */
  def readTargets(m: ConfigView)(names: List[String]): Set[Platform] =
    names.map { n =>
      TargetNames.getOrElse(n.toLowerCase, throw ConfigError(m.at("targets"),
        s"'$n' is not a platform; one of ${TargetNames.keys.toList.sorted.mkString(", ")}"))
    }.toSet

  /** one `dependencies = [ { org, name, rev, cross, resolver } ]` entry.
    *
    * `cross` decides `%` / `%%` / `%%%` and defaults to `scala`, which is what most JVM-ecosystem
    * artifacts are. An unknown value is a `ConfigError` rather than a silent default, for
    * `readTargets`' reason: the wrong separator resolves to an artifact that does not exist, and a
    * config typo should say so here rather than in somebody's resolver output.
    *
    * `resolver` is the URL the artifact is published at when the default resolver set does not have
    * it, and it is optional for the same reason `cross` has a default — almost every coordinate is
    * on Maven Central, and a port that says nothing gets the build it always got. It travels WITH
    * the coordinate rather than in a `project` key of its own because a repository stated apart from
    * the artifact that needs it is a build fact stated twice (CLAUDE.md §1.5). */
  private def dependencyEntry(entry: ConfigView): balticporter.catalog.ArtifactDep =
    val cross = entry.string("cross").getOrElse("scala").toLowerCase match
      case "java"     => balticporter.catalog.CrossKind.Java
      case "scala"    => balticporter.catalog.CrossKind.Scala
      case "platform" => balticporter.catalog.CrossKind.Platform
      case other      => throw ConfigError(entry.at("cross"),
        s"'$other' is not a cross kind; one of java, scala, platform")
    balticporter.catalog.ArtifactDep(
      entry.requireString("org"), entry.requireString("name"), entry.requireString("rev"), cross,
      entry.string("resolver"))

  /** one `resources = [ { root = "…", files = ["p/q/x.json", …] } ]` entry.
    *
    * The ROOT is resolved like every other path here (relative to the conf); the FILES are not paths
    * on this machine but CLASSPATH paths — the same strings the emitted code names — so they are
    * carried across as written and joined to the root by the mechanism. A `files` list that is
    * absent is an empty one, which the run reports rather than treats as "everything": a tree that
    * ships nothing is indistinguishable from the resource being missing, which is the failure the
    * key exists to remove, and an engine that read it as "everything under the root" would be the
    * SCAN this design refuses (DESIGN.md §8.22). */
  private def resourceEntry(dir: Path)(entry: ConfigView): balticporter.core.ResourceTree =
    balticporter.core.ResourceTree(
      resolvePath(dir, entry.requireString("root")),
      entry.strings("files").getOrElse(Nil))

  private def surfaceEntry(registry: TransformRegistry)(entry: ConfigView): balticporter.tir.Phase =
    val name = entry.requireString("transform")
    registry.phase(name, entry, entry.at("transform"))

  // -------------------------------------------------------------------------------------------
  // paths and file selection
  // -------------------------------------------------------------------------------------------

  /** The frontend classpath, stated outright or read from a file a dependency resolver wrote.
    *
    * `classpathFile` exists because the realistic source of a classpath is `cs fetch --classpath`
    * (or sbt, or maven), which writes ONE path-separator-joined line — and a conf that had to
    * inline that line would be a conf regenerated by hand on every dependency bump. What it is NOT
    * is a command to run: a config file naming a program to execute would be the
    * strings-that-are-secretly-code the SPI exists to keep out (CLAUDE.md §1.5).
    *
    * A declared file that is not there is FATAL, never an empty classpath. §5.1's rule about a
    * missing `--tests` path applies with more force here: `import static org.junit.Assert.assertEquals`
    * that the frontend cannot resolve does not fail — it resolves WRONGLY, to an unqualified call
    * on the enclosing class — so an unresolved test classpath is a port that emits nonsense and
    * reports success. */
  private def classpath(input: ConfigView, dir: Path): List[Path] =
    val listed = input.strings("classpath").getOrElse(Nil).map(resolvePath(dir, _))
    val fromFile = input.string("classpathFile").toList.flatMap { s =>
      val f = resolvePath(dir, s)
      if !Files.isRegularFile(f) then
        throw ConfigError(input.at("classpathFile"),
          s"$f does not exist. A classpath that silently resolves to nothing does not fail the " +
            "frontend — it makes it resolve every unresolvable reference WRONGLY, and the port " +
            "then emits nonsense and reports success.")
      Files.readString(f).trim.split(java.io.File.pathSeparator).filter(_.nonEmpty)
        .map(e => resolvePath(dir, e)).toList
    }
    listed ++ fromFile

  /** relative to the conf FILE's directory; an absolute path is taken as written. Lexical
    * `normalize`, never `toRealPath` — see the class doc. */
  private def resolvePath(dir: Path, s: String): Path =
    val p = Path.of(s)
    if p.isAbsolute then p.normalize else dir.resolve(p).normalize

  /** The file list, either stated outright or walked. Sorted either way — unit order is emission
    * order, and an unsorted directory walk is a different port on a different filesystem. */
  private def selectFiles(input: ConfigView, sourceRoot: Path): List[String] =
    input.strings("files") match
      case Some(fs) =>
        if input.keys.contains("includeGlobs") || input.keys.contains("excludeGlobs") then
          throw ConfigError(input.at("files"),
            "`files` states the list outright and `includeGlobs`/`excludeGlobs` derive it; declaring both " +
              "leaves no honest reading of which one the port meant")
        fs.sorted
      case scala.None =>
        if !Files.isDirectory(sourceRoot) then
          throw ConfigError(input.at("sourceRoot"), s"$sourceRoot is not a directory")
        val include = matchers(input, "includeGlobs", DefaultInclude)
        val exclude = matchers(input, "excludeGlobs", DefaultExclude)
        val walk    = Files.walk(sourceRoot)
        try
          walk.iterator.asScala
            .filter(Files.isRegularFile(_))
            .map(p => sourceRoot.relativize(p))
            .filter(rel => include.exists(_.matches(rel)) && !exclude.exists(_.matches(rel)))
            .map(_.toString)
            .toList.sorted
        finally walk.close()

  private def matchers(input: ConfigView, key: String, default: List[String]) =
    input.strings(key).getOrElse(default).map(g =>
      try FileSystems.getDefault.getPathMatcher("glob:" + g)
      catch case e: Exception => throw ConfigError(input.at(key), s"'$g' is not a valid glob: ${e.getMessage}"))

  // -------------------------------------------------------------------------------------------

  /** `project` is OPT-IN build generation and the only seam that writes outside `outDir`; omitting
    * the block is `None` and no build file is written, which is the case the engine's real consumer
    * is in (it already owns its build). `engineFingerprint` is NOT configurable — it records which
    * engine produced the output, and a value an operator could type is a value that can lie. */
  private def projectSpec(p: ConfigView): SbtGen.ProjectSpec =
    SbtGen.ProjectSpec(
      moduleName        = p.requireString("moduleName"),
      organization      = p.requireString("organization"),
      scalaVersion      = p.requireString("scalaVersion"),
      sbtVersion        = p.requireString("sbtVersion"),
      deps              = p.strings("deps").getOrElse(Nil).map(dep(p, "deps")),
      testDeps          = p.strings("testDeps").getOrElse(Nil).map(dep(p, "testDeps")),
      testFramework     = p.string("testFramework"),
      engineFingerprint = balticporter.core.EngineInfo.fingerprint,
    )

  /** `"org::artifact:version"` is Scala-cross (sbt's `%%`), `"org:artifact:version"` is not — the
    * coursier spelling, which is what every measure lane and every `scala-cli` invocation already
    * writes, so a port's build deps read the same in both places. */
  private def dep(p: ConfigView, key: String)(s: String): SbtGen.Dep =
    s.split(":").toList match
      case org :: "" :: artifact :: version :: Nil => SbtGen.Dep(org, artifact, version, crossScala = true)
      case org :: artifact :: version :: Nil       => SbtGen.Dep(org, artifact, version)
      case _ => throw ConfigError(p.at(key),
        s"'$s' is not a dependency; write org:artifact:version, or org::artifact:version for a " +
          "Scala-cross-versioned one")
