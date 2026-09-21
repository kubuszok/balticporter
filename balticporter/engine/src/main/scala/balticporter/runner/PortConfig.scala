package balticporter.runner

import balticporter.catalog.Platform
import balticporter.core.{ AnnotationPolicy, FrontendConfig, FrontendRegistry, ParityRef, PortManifest, Provenance, RealPath, RuntimeMode, TirFrontend }
import balticporter.sbtgen.SbtGen
import balticporter.tir.{ ConfigError, ConfigView }

import java.nio.file.{ FileSystems, Files, Path }
import scala.jdk.CollectionConverters.*

/** Constructs a [[PortRun]] from a HOCON `.conf` file, through the same constructors the Scala path uses. Manifest inheritance is `base.extendedBy(dependent)`; anything config cannot express arrives
  * as SPI-discovered code ([[balticporter.tir.TransformFactory]]); unread keys are refused (see [[HoconView]]). `label`/`input`/`output`/`manifest` required, rest optional; paths are relative to the
  * conf file; `base = "main.conf"` inherits the manifest only.
  */
object PortConfig:

  /** Default file selection: every `.java` minus declaration-only files. Keys are `includeGlobs`/`excludeGlobs` because `include` is a HOCON keyword.
    */
  val DefaultInclude: List[String] = List("**.java")
  val DefaultExclude: List[String] = List(
    "**/package-info.java",
    "package-info.java",
    "**/module-info.java",
    "module-info.java"
  )

  /** Load a `.conf` into a [[PortRun]].
    *
    * `roots` overrides the configuration's NAMED PATH ROOTS. A configuration declares `roots { name = "path" }` (relative to the file, like every path) and writes a path under one as `"@name/rest"`;
    * whoever runs it — a build that took the configuration out of a jar — says where those roots are on ITS disk. An override for a name the file does not declare is refused: it would silently do
    * nothing.
    */
  def load(
    conf:             Path,
    args:             Seq[String] = Nil,
    registry:         TransformRegistry = TransformRegistry.discover(),
    frontendRegistry: FrontendRegistry = FrontendRegistry.discover(),
    roots:            Map[String, Path] = Map.empty
  ): PortRun =
    val file = conf.toAbsolutePath.normalize
    val view = HoconView.root(HoconView.parse(file))
    refuseUndeclaredRoots(view, file, roots)
    val run = read(view, file, args, registry, frontendRegistry, Nil, roots)
    refuseUnread(view, file)
    run

  /** Extract only the manifest from a `.conf`, for spec or embedder use. */
  def manifest(conf: Path, registry: TransformRegistry = TransformRegistry.discover(), roots: Map[String, Path] = Map.empty): PortManifest =
    val file = conf.toAbsolutePath.normalize
    val view = HoconView.root(HoconView.parse(file))
    refuseUndeclaredRoots(view, file, roots)
    manifestOnly(view)
    val m = readManifest(view, file, registry, Nil, roots)
    refuseUnread(view, file)
    m

  /** Where a configuration's paths are resolved: the file's directory, and its named roots — the file's own declarations with the caller's overrides on top. A base configuration is anchored at ITS
    * directory with ITS declarations; the same overrides apply to the names it declares.
    */
  final private class Anchor(val dir: Path, val roots: Map[String, Path])

  private def anchorOf(view: HoconView, file: Path, overrides: Map[String, Path]): Anchor =
    val dir      = file.getParent
    val declared = view.stringMap("roots").getOrElse(Map.empty).map { (name, s) =>
      val p = Path.of(s)
      name -> (if p.isAbsolute then p.normalize else dir.resolve(p).normalize)
    }
    new Anchor(dir, declared ++ overrides.filter((name, _) => declared.contains(name)).map((name, p) => name -> p.toAbsolutePath.normalize))

  private def refuseUndeclaredRoots(view: HoconView, file: Path, overrides: Map[String, Path]): Unit =
    val declared = view.stringMap("roots").getOrElse(Map.empty).keySet
    val unknown  = overrides.keySet -- declared
    if unknown.nonEmpty then
      throw ConfigError(
        file.toString,
        s"path root(s) ${unknown.toList.sorted.mkString(", ")} overridden by the caller but not declared under `roots` " +
          s"(declared: ${if declared.isEmpty then "none" else declared.toList.sorted.mkString(", ")}). An override nothing reads would silently do nothing."
      )

  /** Mark non-manifest keys as read so they are not reported as junk when loading as a base. */
  private def manifestOnly(view: HoconView): Unit =
    view.keys.filterNot(k => k == "manifest" || k == "base").foreach(view.markRead)

  // -------------------------------------------------------------------------------------------

  private def refuseUnread(view: HoconView, file: Path): Unit =
    val junk = view.unread
    if junk.nonEmpty then
      throw ConfigError(
        file.toString,
        s"${junk.size} key(s) nobody read: ${junk.mkString(", ")}. HOCON accepts any key it is " +
          "given, so a misspelt one is a policy entry that silently does nothing — the silent no-op " +
          "this engine refuses everywhere else. Fix the spelling, or delete the key."
      )

  private def read(
    view:             HoconView,
    file:             Path,
    args:             Seq[String],
    registry:         TransformRegistry,
    frontendRegistry: FrontendRegistry,
    seen:             List[Path],
    roots:            Map[String, Path]
  ): PortRun =
    val dir     = anchorOf(view, file, roots)
    val label   = view.requireString("label")
    val input   = view.requireChild("input")
    val output  = view.requireChild("output")
    val srcRoot = resolvePath(dir, input.requireString("sourceRoot"))

    val tirFrontend: TirFrontend = frontendRegistry.get(input.string("frontend").getOrElse("java-spoon"))

    val frontend = FrontendConfig(
      sourceRoot = srcRoot,
      files = selectFiles(input, srcRoot),
      classpath = classpath(input, dir),
      resolutionRoots = input.strings("resolutionRoots").getOrElse(Nil).map(resolvePath(dir, _)),
      // Relative to whichever resolution root contains them, not to the config directory.
      resolutionExcludes = input.strings("resolutionExcludes").getOrElse(Nil),
      // FQN prefixes in upstream namespace. Absent = none.
      preservedAnnotations = AnnotationPolicy(input.strings("preservedAnnotations").getOrElse(Nil))
    )

    PortRun(
      label = label,
      portRoot = resolvePath(dir, output.requireString("portRoot")),
      sourceSet = output.enumerated("sourceSet", Map("main" -> SourceSet.Main, "test" -> SourceSet.Test)).getOrElse(throw ConfigError(output.at("sourceSet"), "required, and absent")),
      frontend = frontend,
      // Phases come from the manifest; this path never passes a second source.
      phases = Nil,
      manifest = Some(readManifest(view, file, registry, seen, roots)),
      provenance = view
        .child("provenance")
        .map(p =>
          Provenance(
            upstreamName = p.requireString("upstreamName"),
            upstreamCommit = p.string("upstreamCommit").getOrElse(VendoredCommit.of(srcRoot)),
            originalLicense = p.requireString("originalLicense"),
            sourcePathPrefix = p.requireString("sourcePathPrefix"),
            sourceRoot = p.string("sourceRoot").map(resolvePath(dir, _).toString).getOrElse(srcRoot.toString),
            // conf-relative; the upstream root is not the source root.
            notices = p.strings("notices").getOrElse(Nil).map(resolvePath(dir, _))
          )
        ),
      runtimeMode = view.enumerated("runtimeMode", Map("dependency" -> RuntimeMode.Dependency, "vendored" -> RuntimeMode.Vendored)).getOrElse(RuntimeMode.Dependency),
      supportSources = view.stringMap("supportSources").getOrElse(Map.empty),
      project = view.child("project").map(projectSpec),
      determinism = determinismOf(view, args),
      cache = view.string("cache").map(resolvePath(dir, _)),
      lenient = view.bool("lenient").getOrElse(true),
      preview = view.bool("preview").getOrElse(false),
      nextStep = view.string("nextStep").getOrElse(""),
      // Every remedy this classpath declares, for load-time validation of selections.
      knownRemedies = registry.remedies,
      tirFrontend = tirFrontend
    )

  /** CLI `--determinism=` flag beats the file, which beats the default. */
  private def determinismOf(view: HoconView, args: Seq[String]): Determinism =
    // Read unconditionally so the unread-key check does not report it as junk.
    val declared = view.enumerated("determinism",
                                   Map(
                                     "off" -> Determinism.Off,
                                     "emission" -> Determinism.Emission,
                                     "full" -> Determinism.Full
                                   )
    )
    if args.contains(Determinism.FullFlag) || args.contains(Determinism.OffFlag) then Determinism.fromArgs(args)
    else declared.getOrElse(Determinism.Emission)

  private def readManifest(
    view:     HoconView,
    file:     Path,
    registry: TransformRegistry,
    seen:     List[Path],
    roots:    Map[String, Path]
  ): PortManifest =
    val dir = anchorOf(view, file, roots)
    val m   = view.requireChild("manifest")
    // Anchor base report paths before surface entries (port-map-migration loads maps at construction).
    // Only for this run's own conf (`seen.isEmpty`), not a base's.
    val reports = view.strings("baseReports").getOrElse(Nil).map(resolvePath(dir, _))
    if reports.nonEmpty && seen.isEmpty then System.setProperty(balticporter.tir.DebugFlags.Prefix + "baseReports", reports.mkString(java.io.File.pathSeparator))
    // Surface entries read before the manifest so selections can be validated.
    val surface = m.children("surface").getOrElse(Nil).map(surfaceEntry(registry))
    val own     = PortManifest(
      name = m.requireString("name"),
      governs = m.strings("governs").getOrElse(Nil).toSet,
      dropTypes = m.strings("dropTypes").getOrElse(Nil).toSet,
      dropMethods = m.strings("dropMethods").getOrElse(Nil).toSet,
      packageRenames = m.stringMap("packageRenames").getOrElse(Map.empty),
      // Per-type rename; placed by PortRun, not a surface entry.
      typeRenames = m.stringMap("typeRenames").getOrElse(Map.empty),
      subPackages = m.stringMap("subPackages").getOrElse(Map.empty),
      flattenNestedTypes = m.strings("flattenNestedTypes").getOrElse(Nil).toSet,
      allowPackageSplit = m.strings("allowPackageSplit").getOrElse(Nil).toSet,
      surface = surface,
      // Per-location remedy selection; validated at load against known remedies.
      resolutions = readResolutions(m, surface, registry),
      inject = m.strings("inject").getOrElse(Nil).map(resolvePath(dir, _)),
      // Ready-made Scala for ONE platform row. Not inherited; absent or `{}` = no-op.
      platformDirs = readPlatformDirs(dir, m),
      // SPI descriptors copied with both namespaces renamed. Not inherited; missing = fatal.
      serviceProviders = m.strings("serviceProviders").getOrElse(Nil).map(resolvePath(dir, _)),
      // Classpath resources copied verbatim. Not inherited; missing = fatal.
      resources = m.children("resources").getOrElse(Nil).map(resourceEntry(dir)),
      baseReports = if seen.isEmpty then reports else Nil,
      // Omitted = all three platforms. Not inherited.
      targets = m.strings("targets").map(readTargets(m)).getOrElse(Platform.values.toSet),
      // Declared dependency artifacts. Not inherited; empty = no-op.
      dependencies = m.children("dependencies").getOrElse(Nil).map(dependencyEntry),
      // External members parenless on some platforms. Not inherited; empty = no-op.
      externalParenless = m.strings("externalParenless").getOrElse(Nil).toSet,
      // Reference hand port for parity check. Not inherited; absent = no-op.
      parity = m
        .child("parity")
        .map(p =>
          ParityRef(
            roots = p.strings("roots").getOrElse(Nil).map(resolvePath(dir, _)),
            packageMapping = p.stringMap("packageMapping").getOrElse(Map.empty),
            // Header substrings making a hand-port file a party; absent = the default spellings,
            // explicit `[]` = every file is a party (the no-op value).
            upstreamMarkers = p.strings("upstreamMarkers").getOrElse(ParityRef.DefaultUpstreamMarkers)
          )
        )
    )
    view.string("base") match
      case scala.None     => own
      case Some(basePath) =>
        val baseFile = resolvePath(dir, basePath)
        // Cycle test uses realpath; resolution stays lexical.
        if seen.exists(s => RealPath.of(s) == RealPath.of(baseFile)) then
          throw ConfigError(
            view.at("base"),
            s"$baseFile is already in this base chain (${(seen :+ baseFile).mkString(" -> ")}); " +
              "a manifest cannot be a base of itself"
          )
        val baseView = HoconView.root(HoconView.parse(baseFile))
        manifestOnly(baseView)
        val base = readManifest(baseView, baseFile, registry, seen :+ baseFile, roots)
        refuseUnread(baseView, baseFile)
        base.extendedBy(own)

  /** Validate `resolutions` entries against the known remedy vocabulary at load time. An unrecognised id is a `ConfigError`. Key shape is validated by the binder, not here.
    */
  private def readResolutions(
    m:        ConfigView,
    surface:  List[balticporter.tir.Phase],
    registry: TransformRegistry
  ): Map[String, String] =
    val declared = m.stringMap("resolutions").getOrElse(Map.empty)
    if declared.isEmpty then Map.empty
    else
      val known = knownRemedies(surface, registry)
      declared.toList.sortBy(_._1).foreach { (key, id) =>
        if !known.contains(id) then
          throw ConfigError(
            m.at("resolutions"),
            s"""'$id' (selected at "$key") is not a remedy this engine offers. """ +
              (if known.isEmpty then
                 "No phase or check on this classpath declares one at all — a remedy is DECLARED by " +
                   "the mechanism that can carry it out, so either the phase that offers it is " +
                   "missing from this `surface`, or its library is not on the classpath."
               else s"Declared here: ${known.ids.mkString(", ")}.")
          )
      }
      declared

  /** All remedies known to this conf: engine checks + factory-declared + surface phases. */
  private def knownRemedies(
    surface:  List[balticporter.tir.Phase],
    registry: TransformRegistry
  ): balticporter.tir.RemedyVocabulary =
    balticporter.tir.RemedyVocabulary.from(
      PortRun.CheckRemedies ++ surface.collect { case r: balticporter.tir.RemedySource => r }
    ) ++ registry.remedies

  /** An unknown platform name is a `ConfigError` (a typo would silently narrow the target set). */
  private val TargetNames: Map[String, Platform] = Map(
    "jvm" -> Platform.Jvm,
    "js" -> Platform.ScalaJs,
    "scala-js" -> Platform.ScalaJs,
    "native" -> Platform.ScalaNative,
    "scala-native" -> Platform.ScalaNative
  )

  /** Parse `platformDirs { <row> = [paths…] }`.
    *
    * The keys are ROW names ([[PortManifest.PlatformRows]]) and not target names, because a row is a directory the generated build compiles: an alias no build spells would produce a tree nothing
    * reads, so an unknown key is a `ConfigError` rather than a silent skip. Absent, or `{}`, is the no-op empty map.
    */
  private def readPlatformDirs(dir: Anchor, m: ConfigView): Map[String, List[Path]] =
    m.child("platformDirs") match
      case scala.None     => Map.empty
      case Some(rowsView) =>
        rowsView.keys.map { row =>
          if !PortManifest.PlatformRows.contains(row) then
            throw ConfigError(
              rowsView.at(row),
              s"'$row' is not a platform row; one of ${PortManifest.PlatformRows.keys.toList.sorted.mkString(", ")}"
            )
          row -> rowsView.strings(row).getOrElse(Nil).map(resolvePath(dir, _))
        }.toMap

  /** Parse platform names from a `.conf`. */
  def readTargets(m: ConfigView)(names: List[String]): Set[Platform] =
    names.map { n =>
      TargetNames.getOrElse(
        n.toLowerCase,
        throw ConfigError(m.at("targets"), s"'$n' is not a platform; one of ${TargetNames.keys.toList.sorted.mkString(", ")}")
      )
    }.toSet

  /** Parse one `dependencies` entry. `cross` defaults to `scala`; unknown value is a `ConfigError`. `resolver` is optional (most artifacts are on Maven Central).
    */
  private def dependencyEntry(entry: ConfigView): balticporter.catalog.ArtifactDep =
    val cross = entry.string("cross").getOrElse("scala").toLowerCase match
      case "java"     => balticporter.catalog.CrossKind.Java
      case "scala"    => balticporter.catalog.CrossKind.Scala
      case "platform" => balticporter.catalog.CrossKind.Platform
      case other      => throw ConfigError(entry.at("cross"), s"'$other' is not a cross kind; one of java, scala, platform")
    balticporter.catalog.ArtifactDep(entry.requireString("org"), entry.requireString("name"), entry.requireString("rev"), cross, entry.string("resolver"))

  /** Parse one `resources` entry. Root is conf-relative; files are classpath paths. Absent files = empty. */
  private def resourceEntry(dir: Anchor)(entry: ConfigView): balticporter.core.ResourceTree =
    balticporter.core.ResourceTree(resolvePath(dir, entry.requireString("root")), entry.strings("files").getOrElse(Nil))

  private def surfaceEntry(registry: TransformRegistry)(entry: ConfigView): balticporter.tir.Phase =
    val name = entry.requireString("transform")
    registry.phase(name, entry, entry.at("transform"))

  // -------------------------------------------------------------------------------------------
  // paths and file selection
  // -------------------------------------------------------------------------------------------

  /** Frontend classpath from `classpath` entries and/or `classpathFile` (path-separator-joined). A declared file that is missing is fatal (an unresolved classpath causes silent misresolution).
    */
  private def classpath(input: ConfigView, dir: Anchor): List[Path] =
    val listed = input.strings("classpath").getOrElse(Nil).map(resolvePath(dir, _))
    // `classpathCoordinates`: maven coordinates the run resolves ITSELF (through coursier) into `classpathFile`,
    // reused while the file still records these coordinates and every jar it names exists.
    input.strings("classpathCoordinates").filter(_.nonEmpty).foreach { coordinates =>
      val file = input
        .string("classpathFile")
        .getOrElse(
          throw ConfigError(
            input.at("classpathCoordinates"),
            "`classpathCoordinates` needs `classpathFile`: the file the resolved classpath is kept in"
          )
        )
      val out = resolvePath(dir, file)
      Option(out.getParent).foreach(Files.createDirectories(_))
      ClasspathCache.ensure(out, out.getFileName.toString, coordinates)
    }
    val fromFile = input.string("classpathFile").toList.flatMap { s =>
      val f = resolvePath(dir, s)
      if !Files.isRegularFile(f) then
        throw ConfigError(
          input.at("classpathFile"),
          s"$f does not exist. A classpath that silently resolves to nothing does not fail the " +
            "frontend — it makes it resolve every unresolvable reference WRONGLY, and the port " +
            "then emits nonsense and reports success."
        )
      Files.readString(f).trim.split(java.io.File.pathSeparator).filter(_.nonEmpty).map(e => resolvePath(dir, e)).toList
    }
    listed ++ fromFile

  /** Resolve `@name/rest` under the named root, anything else relative to the conf file's directory; absolute paths taken as-is. Lexical normalize. A root nobody declared is refused by name. */
  private def resolvePath(anchor: Anchor, s: String): Path =
    if s.startsWith("@") then
      val (name, rest) = s.drop(1).span(_ != '/')
      val root         = anchor.roots.getOrElse(
        name,
        throw ConfigError(
          s,
          s"no path root named `$name` (declared: ${if anchor.roots.isEmpty then "none" else anchor.roots.keys.toList.sorted.mkString(", ")}); declare it under `roots { $name = \"…\" }`"
        )
      )
      root.resolve(rest.stripPrefix("/")).normalize
    else
      val p = Path.of(s)
      if p.isAbsolute then p.normalize else anchor.dir.resolve(p).normalize

  /** File list: explicit `files` or glob-walked. Sorted for deterministic emission order. */
  private def selectFiles(input: ConfigView, sourceRoot: Path): List[String] =
    input.strings("files") match
      case Some(fs) =>
        if input.keys.contains("includeGlobs") || input.keys.contains("excludeGlobs") then
          throw ConfigError(
            input.at("files"),
            "`files` states the list outright and `includeGlobs`/`excludeGlobs` derive it; declaring both " +
              "leaves no honest reading of which one the port meant"
          )
        fs.sorted
      case scala.None =>
        if !Files.isDirectory(sourceRoot) then throw ConfigError(input.at("sourceRoot"), s"$sourceRoot is not a directory")
        val include = matchers(input, "includeGlobs", DefaultInclude)
        val exclude = matchers(input, "excludeGlobs", DefaultExclude)
        // Links are followed: a source root that IS a symbolic link (a submodule linked in from another
        // checkout) otherwise walks as the link alone — zero files, and a run that converts nothing.
        val walk     = Files.walk(sourceRoot, java.nio.file.FileVisitOption.FOLLOW_LINKS)
        val selected =
          try
            walk.iterator.asScala
              .filter(Files.isRegularFile(_))
              .map(p => sourceRoot.relativize(p))
              .filter(rel => include.exists(_.matches(rel)) && !exclude.exists(_.matches(rel)))
              .map(_.toString)
              .toList
              .sorted
          finally walk.close()
        if selected.isEmpty then
          throw ConfigError(
            input.at("sourceRoot"),
            s"no file under $sourceRoot matches the include/exclude globs — a port that converts nothing is a misconfiguration, not a result"
          )
        selected

  private def matchers(input: ConfigView, key: String, default: List[String]) =
    input
      .strings(key)
      .getOrElse(default)
      .map(g =>
        try FileSystems.getDefault.getPathMatcher("glob:" + g)
        catch case e: Exception => throw ConfigError(input.at(key), s"'$g' is not a valid glob: ${e.getMessage}")
      )

  // -------------------------------------------------------------------------------------------

  /** Opt-in build generation. Omitting = `None`. `engineFingerprint` is not configurable. */
  private def projectSpec(p: ConfigView): SbtGen.ProjectSpec =
    SbtGen.ProjectSpec(
      moduleName = p.requireString("moduleName"),
      organization = p.requireString("organization"),
      scalaVersion = p.requireString("scalaVersion"),
      sbtVersion = p.requireString("sbtVersion"),
      deps = p.strings("deps").getOrElse(Nil).map(dep(p, "deps")),
      testDeps = p.strings("testDeps").getOrElse(Nil).map(dep(p, "testDeps")),
      testFramework = p.string("testFramework"),
      engineFingerprint = balticporter.core.EngineInfo.fingerprint
    )

  /** `org::artifact:version` (Scala-cross) or `org:artifact:version` (Java). */
  private def dep(p: ConfigView, key: String)(s: String): SbtGen.Dep =
    s.split(":").toList match
      case org :: "" :: artifact :: version :: Nil => SbtGen.Dep(org, artifact, version, crossScala = true)
      case org :: artifact :: version :: Nil       => SbtGen.Dep(org, artifact, version)
      case _                                       =>
        throw ConfigError(
          p.at(key),
          s"'$s' is not a dependency; write org:artifact:version, or org::artifact:version for a " +
            "Scala-cross-versioned one"
        )
