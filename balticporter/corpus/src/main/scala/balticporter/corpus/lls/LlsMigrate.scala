package balticporter.corpus.lls

import balticporter.core.{FrontendConfig, ParityRef, PortManifest, Provenance, RuntimeMode}
import balticporter.runner.{Determinism, PortRun, SourceSet, VendoredCommit}
import balticporter.transform.{CollectionsTransform, ElementWitnessTransform, TypeRedirectTransform,
  GlobalsToImplicitsTransform, MutableParamsTransform, NullabilityTransform}

import java.nio.file.Path

/** Migrate the twelve libGDX sources **lls** carries `Ported from` headers for — `utils.{Array,
  * ObjectMap, ObjectSet, OrderedMap, OrderedSet, ArrayMap, Sort, TimSort, ComparableTimSort,
  * Select, QuickSelect}` and `math.MathUtils` — onto `lowlevel.{util,math}`. A STANDALONE base
  * with no resolution root: every reference to the rest of libGDX is EXTERNAL, and a COUNTED seam
  * rather than a drop or a shim (`PROGRESS.md` §13.28). */
object LlsMigrate:

  /** The java files the REAL lls declares (maintainer, 2026-09-06: narrowed from 54 to 12, ENGINE-LIMITS.md
    * K43) plus the `@Null` annotation their sources carry (a refused site KEEPS it, so the base ships
    * the type once); the five outside references are answered by policy in [[LlsPolicy.core]];
    * everything else under `utils`/`math` is core's, on this base (PROGRESS.md §13.29). */
  val Files: List[String] = List(
    "com/badlogic/gdx/math/MathUtils.java",
    "com/badlogic/gdx/utils/Null.java",
    "com/badlogic/gdx/utils/Array.java",
    "com/badlogic/gdx/utils/ArrayMap.java",
    "com/badlogic/gdx/utils/ComparableTimSort.java",
    "com/badlogic/gdx/utils/ObjectMap.java",
    "com/badlogic/gdx/utils/ObjectSet.java",
    "com/badlogic/gdx/utils/OrderedMap.java",
    "com/badlogic/gdx/utils/OrderedSet.java",
    "com/badlogic/gdx/utils/QuickSelect.java",
    "com/badlogic/gdx/utils/Select.java",
    "com/badlogic/gdx/utils/Sort.java",
    "com/badlogic/gdx/utils/TimSort.java",
  )

  /** the twelve as FQNs (the annotation has no members to scope) — the port's `governs` claim and
    * every scope in its policy. */
  val Fqns: Set[String] = Files.map(_.stripSuffix(".java").replace('/', '.')).toSet - "com.badlogic.gdx.utils.Null"

  def main(args: Array[String]): Unit =
    val repoRoot = Path.of(sys.props.getOrElse("balticporter.root", ".")).toAbsolutePath.normalize
    val base     = repoRoot.resolve("../sge/original-src/libgdx/gdx/src").normalize
    // `--rungs=nullable,ordering`: the decision rungs switched on above L0 (PROGRESS.md §13.29).
    val rungs    = args.collectFirst { case a if a.startsWith("--rungs=") => a.stripPrefix("--rungs=") }
      .toList.flatMap(_.split(',')).map(_.trim).filter(_.nonEmpty).toSet

    PortRun(
      label     = "lls",
      portRoot  = repoRoot.resolve("ported/lls"),
      sourceSet = SourceSet.Main,
      // NO resolution root and NO classpath. `gdx/src` as one puts 593 libGDX types this port does
      // not emit into the program, and 300 of the contract questions they raise shape emitted text
      // with no base to answer them (`DESIGN.md` §8.3) — the run refuses. Standalone means the rest
      // of libGDX is EXTERNAL: unresolved, and counted like any other foreign symbol.
      frontend  = FrontendConfig(base, Files, balticporter.corpus.GdxCoreClasspath.entries(repoRoot), resolutionRoots = Nil),
      phases    = Nil, // supplied by the manifest — the two sources are mutually exclusive
      manifest  = Some(LlsPolicy.core(repoRoot, rungs)),
      provenance = Some(Provenance(
        upstreamName     = "libGDX",
        upstreamCommit   = VendoredCommit.of(base),
        originalLicense  = "Apache-2.0",
        sourcePathPrefix = "gdx/src",
        sourceRoot       = base.toString,
      )),
      // A standalone single source set: a support type any phase retypes onto has to ship beside
      // the emitted code, as noise4j and jbump do.
      runtimeMode = RuntimeMode.Vendored,
      determinism = Determinism.fromArgs(args.toSeq),
      nextStep    = "just lls-measure",
    ).execute()

/** lls's per-library policy AS A VALUE (`CLAUDE.md` §1.5), the manifest libGDX core is to extend
  * (`PROGRESS.md` §13.28). It declares the two namespace facts and the hand port to compare
  * against and NOTHING else — no drops, no bodies, no surface phases — so every divergence the
  * run reports is a measurement rather than a policy decision. */
object LlsPolicy:

  /** lls's array type class, and the declarations whose element arrays it allocates: the
    * ARRAY-LIKE family, upstream FQN -> the element type-parameter indexes. The seven
    * open-addressed tables are deliberately absent — `keyTable[i] == null` is their occupancy
    * test, counted as `witness(OccupancySentinel)` (PROGRESS.md §13.29, ENGINE-LIMITS.md K41). */
  val Witness = "lowlevel.MkArray"

  val WitnessSubjects: Map[String, List[Int]] = Map(
    "com.badlogic.gdx.utils.Array"               -> List(0),
    "com.badlogic.gdx.utils.ArrayMap"            -> List(0, 1),
    // the two nested views CONSTRUCT a `DynamicArray` at their own parameter, so they take the
    // clause even though they allocate nothing themselves.
    "com.badlogic.gdx.utils.ArrayMap$Values"     -> List(0),
    "com.badlogic.gdx.utils.ArrayMap$Keys"       -> List(0),
  )

  /** …and the declarations that only LOSE java's implicit `Object` bound: the three sort/select
    * entry points the array family calls with its own element type. `TimSort` is NOT among them —
    * `Sort` holds it in a RAW field and hands it `Object[]`, so an unbounded element type there
    * would type-check and throw (`witness(ErasedArrayCast)`, PROGRESS.md §13.29). */
  val WitnessUnbound: Set[String] = WitnessSubjects.keySet ++ Set(
    "com.badlogic.gdx.utils.Sort",
    "com.badlogic.gdx.utils.Select",
    "com.badlogic.gdx.utils.QuickSelect",
  )

  /** The DEFAULT array factory this fork of libGDX threads through its constructors. With the rung
    * on, the witness IS that factory: one added companion member and one call-site substitution
    * keep java's `ArraySupplier` API and make its default allocate through the type class. */
  /** `ArraySupplier` is not lls's: java's `T[] get(int)` becomes `scala.Function1[Int, T[]]` and the
    * default supplier is the witness (`MkArray.create`), so no supplier type is emitted (K43). */
  val ArraySupplier = "com.badlogic.gdx.utils.ArraySupplier"
  def collections(rungs: Set[String]): CollectionsTransform = new CollectionsTransform(
    scope = Twelve,
    retarget = Map(ArraySupplier -> "scala.Function1") ++
      (if rungs("ordering") then Map("java.util.Comparator" -> "scala.math.Ordering") else Map.empty),
    retargetTypeArgs = Map(ArraySupplier -> List(
      CollectionsTransform.RetargetArg.FixedType("scala.Int"), CollectionsTransform.RetargetArg.SourceArg(0))),
    retargetRewrites = Map(ArraySupplier -> Map(("get", 1) -> CollectionsTransform.RetargetRewrite.Rename("apply"))))

  /** The decision rungs a run may switch on above L0, each a manifest fragment (PROGRESS.md §13.29).
    * @param rungs what else is on — `enrich`'s verbatim factories are written against the
    *              signatures `witness` decides, so they are not independent of it. */
  def rungPhases(rungs: Set[String]): Map[String, List[balticporter.tir.Phase]] = Map(
    "nullable" -> List(new NullabilityTransform(
      annotations = Set("com.badlogic.gdx.utils.Null"),
      target      = NullabilityTransform.Target.Named("lowlevel.Nullable"),
      scope       = balticporter.tir.RuleScope.Only(Annotated))),
    "ordering" -> List(collections(rungs)),
    // L1 candidates (PROGRESS.md §13.29): getter-like nullary methods lose `()`; java-convention
    // accessor pairs become properties (empty explicit tables: derivation only).
    "arity"    -> List(new balticporter.transform.NullaryArityTransform(scope = Twelve)),
    "bean"     -> List(new balticporter.transform.BeanPropertyTransform(Map.empty, Map.empty, scope = Twelve)),
    "enrich"   -> List(LlsEnrich.transform(rungs("witness"))),
    "witness"  -> List(
      // the CONSTRUCTOR half of the clause, threaded by the phase that owns that mechanism (CT7)
      new GlobalsToImplicitsTransform(requiredGivens =
        ElementWitnessTransform.constructorGivens(WitnessSubjects, Witness)),
      new ElementWitnessTransform(
        witness      = Witness,
        subjectTypes = WitnessSubjects,
        dropBound    = WitnessUnbound,
        // java's own default array factory: with the rung on, the witness IS it.
        defaultSuppliers = Map(
          "com.badlogic.gdx.utils.ArraySupplier#object()" ->
            "((size: scala.Int) => scala.Predef.summon[lowlevel.MkArray[{elem}]].create(size))"),
        // the witness for an element type that KEEPS java's `Object` bound: the one lls itself
        // uses for reference elements, which is the representation java's `Object[]` already had.
        boxedWitness = Some("lowlevel.MkArray.anyRef[scala.AnyRef].asInstanceOf[lowlevel.MkArray[{elem}]]"))),
  )

  /** the rung NAMES, for validation and for the `--rungs=` error message. */
  val Rungs: Set[String] = rungPhases(Set.empty).keySet

  /** the order the rungs occupy in `surface` — a pipeline position, not the alphabet. */
  val RungOrder: List[String] = List("bean", "arity", "nullable", "ordering", "enrich", "witness")

  /** the rungs lls carries by default (the lane's `LLS_RUNGS` default spells the same set). */
  val DefaultRungs: Set[String] = Set("arity", "nullable", "ordering", "enrich", "witness")

  /** every lls rung stops at lls's own declarations (D12): the inherited surface must not decide
    * core's, which takes each decision as a rung of its own (PROGRESS.md §13.29). */
  val Twelve: balticporter.tir.RuleScope = balticporter.tir.RuleScope.Only(LlsMigrate.Fqns)

  /** the four of the twelve that carry `@Null` plus the two whose OVERRIDES they reach — a scope cut
    * through an override component splits it instead of refusing (ENGINE-LIMITS.md K13.8), and an
    * entry holding nothing back is a `policy` row, so neither `Twelve` nor the four alone will do. */
  val Annotated: Set[String] = Set("Array", "ArrayMap", "ObjectMap", "ObjectSet", "OrderedMap", "OrderedSet")
    .map("com.badlogic.gdx.utils." + _)

  /** `GdxRuntimeException` and `RandomXS128` are core's: inside the twelve they are the JDK types lls
    * used (`RuntimeException` where lls chose per site; `java.util.Random`), scoped to the ENTRY
    * (D12) so the inherited surface leaves core's own uses alone (K43). */
  val redirects: TypeRedirectTransform = new TypeRedirectTransform(
    redirects = Map(
      "com.badlogic.gdx.utils.GdxRuntimeException" -> "java.lang.RuntimeException",
      "com.badlogic.gdx.utils.Collections"         -> "lowlevel.util.Collections",
      "com.badlogic.gdx.math.RandomXS128"          -> "java.util.Random"),
    scopes = Map(
      "com.badlogic.gdx.utils.GdxRuntimeException" -> Twelve,
      // the drop is inherited, so its replacement is too: every dependent's `Collections` read lands
      // on the one injected flag holder (`Everywhere`, unlike the two java-type answers above).
      "com.badlogic.gdx.utils.Collections"         -> balticporter.tir.RuleScope.Everywhere(Set.empty),
      "com.badlogic.gdx.math.RandomXS128"          -> balticporter.tir.RuleScope.Only(Set("com.badlogic.gdx.math.MathUtils"))))


  def core(repoRoot: Path, rungs: Set[String] = Set.empty): PortManifest =
    val unknown = rungs -- Rungs
    require(unknown.isEmpty, s"unknown lls rungs: ${unknown.mkString(",")}; known: ${Rungs.toList.sorted.mkString(",")}")
    require(RungOrder.toSet == Rungs, s"RungOrder does not cover every rung: ${Rungs -- RungOrder.toSet}")
    // `enrich`'s bodies are written against the EMITTED signatures, which `nullable` decides
    // (`contains(Nullable[T], Boolean)` vs `contains(T, Boolean)`) — a rung, not a free choice.
    require(!rungs("enrich") || rungs("nullable"), "lls rung `enrich` requires `nullable`")
    PortManifest(
      name    = "lls",
      governs = LlsMigrate.Fqns,
      // the five references the twelve make outside themselves, answered the way lls did (K43):
      // `Collections` -> the injected flag holder (dropped for the DEPENDENT, redirected here, where
      // it is a class-file external); the reflective `Class`-typed constructors and `toArray(Class)`
      // go with `ArrayReflection`; `select(Predicate)` goes with `Predicate`; `ArraySupplier` retargets.
      dropTypes = Set("com.badlogic.gdx.utils.Collections"),
      dropMethods = Set(
        "com.badlogic.gdx.utils.Array#<init>(boolean,int,Class)",
        "com.badlogic.gdx.utils.Array#<init>(Class)",
        "com.badlogic.gdx.utils.Array#toArray(Class)",
        "com.badlogic.gdx.utils.Array#of(Class)",
        "com.badlogic.gdx.utils.Array#of(boolean,int,Class)",
        "com.badlogic.gdx.utils.Array#select(Predicate)",
        "com.badlogic.gdx.utils.Array#predicateIterable",
        "com.badlogic.gdx.utils.ArrayMap#<init>(boolean,int,Class,Class)",
        "com.badlogic.gdx.utils.ArrayMap#<init>(Class,Class)",
      ),
      inject = List(repoRoot.resolve("balticporter/corpus/lls-overrides")),
      // lls's OWN types live under `lowlevel` (maintainer, 2026-09-06): a per-type move (a dotted
      // `typeRenames` target), never a package claim — the rest of `utils`/`math` is core's and
      // follows core's own rename (`sge.*`). lls renamed `Array` to `DynamicArray` (`scala.Array`).
      // the three of the twelve whose package-private members core reads (`ObjectSet.tableSize`,
      // `ObjectMap.dummy`, …): the move is declared, so those members ship public (§8.7 widenings).
      allowPackageSplit = Set("com.badlogic.gdx.utils.Array", "com.badlogic.gdx.utils.ObjectMap",
                              "com.badlogic.gdx.utils.ObjectSet"),
      typeRenames = LlsMigrate.Files.map { f =>
        val fqn    = f.stripSuffix(".java").replace('/', '.')
        val simple = if fqn.endsWith(".Array") then "DynamicArray" else fqn.substring(fqn.lastIndexOf('.') + 1)
        val pkg    = if fqn.startsWith("com.badlogic.gdx.math.") then "lowlevel.math" else "lowlevel.util"
        fqn -> s"$pkg.$simple"
      }.toMap,
      // L0 of the lls ladder: the universal phases only (`MutableParamsTransform` is universal but
      // per-port today); the decision rungs are added one at a time (PROGRESS.md §13.29).
      // `enrich` LAST: its members are verbatim text written against what the rungs below it
      // emit, so it reads the surface rather than contributing one another phase must walk.
      surface = List(new MutableParamsTransform, redirects) ++
        RungOrder.filter(rungs).flatMap(rungPhases(rungs)(_)) ++
        (if rungs("ordering") then Nil else List(collections(rungs))),
      // THE REFERENCE HAND PORT for lls. NOT inherited (DESIGN.md §8.23).
      parity = Some(ParityRef(roots = List(
        repoRoot.resolve("../lls/lls/src/main/scala").normalize))),
    )
