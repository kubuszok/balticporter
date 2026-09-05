package balticporter.corpus.lls

import balticporter.core.{FrontendConfig, ParityRef, PortManifest, Provenance, RuntimeMode}
import balticporter.runner.{Determinism, PortRun, SourceSet, VendoredCommit}
import balticporter.transform.{CollectionsTransform, MutableParamsTransform, NullabilityTransform}

import java.nio.file.Path

/** Migrate the twelve libGDX sources **lls** carries `Ported from` headers for — `utils.{Array,
  * ObjectMap, ObjectSet, OrderedMap, OrderedSet, ArrayMap, Sort, TimSort, ComparableTimSort,
  * Select, QuickSelect}` and `math.MathUtils` — onto `lowlevel.{util,math}`. A STANDALONE base
  * with no resolution root: every reference to the rest of libGDX is EXTERNAL, and a COUNTED seam
  * rather than a drop or a shim (`PROGRESS.md` §13.28). */
object LlsMigrate:

  /** The libGDX UTILITIES family (maintainer, 2026-09-06): every platform-free collection, pool and
    * helper under `utils` plus `math.MathUtils`, closed over their references inside `utils`/`math`
    * (54 files, no escape into another package) — the base beneath the rest of the port (PROGRESS.md §13.29). */
  val Files: List[String] = List(
    "com/badlogic/gdx/math/MathUtils.java",
    "com/badlogic/gdx/math/RandomXS128.java",
    "com/badlogic/gdx/utils/Align.java",
    "com/badlogic/gdx/utils/Array.java",
    "com/badlogic/gdx/utils/ArrayMap.java",
    "com/badlogic/gdx/utils/ArraySupplier.java",
    "com/badlogic/gdx/utils/AtomicQueue.java",
    "com/badlogic/gdx/utils/Base64Coder.java",
    "com/badlogic/gdx/utils/BinaryHeap.java",
    "com/badlogic/gdx/utils/Bits.java",
    "com/badlogic/gdx/utils/BooleanArray.java",
    "com/badlogic/gdx/utils/ByteArray.java",
    "com/badlogic/gdx/utils/CharArray.java",
    "com/badlogic/gdx/utils/Collections.java",
    "com/badlogic/gdx/utils/ComparableTimSort.java",
    "com/badlogic/gdx/utils/DefaultPool.java",
    "com/badlogic/gdx/utils/DelayedRemovalArray.java",
    "com/badlogic/gdx/utils/FloatArray.java",
    "com/badlogic/gdx/utils/FlushablePool.java",
    "com/badlogic/gdx/utils/GdxRuntimeException.java",
    "com/badlogic/gdx/utils/IdentityMap.java",
    "com/badlogic/gdx/utils/IntArray.java",
    "com/badlogic/gdx/utils/IntFloatMap.java",
    "com/badlogic/gdx/utils/IntIntMap.java",
    "com/badlogic/gdx/utils/IntMap.java",
    "com/badlogic/gdx/utils/IntSet.java",
    "com/badlogic/gdx/utils/LongArray.java",
    "com/badlogic/gdx/utils/LongMap.java",
    "com/badlogic/gdx/utils/LongQueue.java",
    "com/badlogic/gdx/utils/NonNull.java",
    "com/badlogic/gdx/utils/NonNullByDefault.java",
    "com/badlogic/gdx/utils/Null.java",
    "com/badlogic/gdx/utils/NumberUtils.java",
    "com/badlogic/gdx/utils/ObjectFloatMap.java",
    "com/badlogic/gdx/utils/ObjectIntMap.java",
    "com/badlogic/gdx/utils/ObjectLongMap.java",
    "com/badlogic/gdx/utils/ObjectMap.java",
    "com/badlogic/gdx/utils/ObjectSet.java",
    "com/badlogic/gdx/utils/OrderedMap.java",
    "com/badlogic/gdx/utils/OrderedSet.java",
    "com/badlogic/gdx/utils/Pool.java",
    "com/badlogic/gdx/utils/PoolManager.java",
    "com/badlogic/gdx/utils/PooledLinkedList.java",
    "com/badlogic/gdx/utils/Predicate.java",
    "com/badlogic/gdx/utils/Queue.java",
    "com/badlogic/gdx/utils/QuickSelect.java",
    "com/badlogic/gdx/utils/Select.java",
    "com/badlogic/gdx/utils/ShortArray.java",
    "com/badlogic/gdx/utils/SnapshotArray.java",
    "com/badlogic/gdx/utils/Sort.java",
    "com/badlogic/gdx/utils/SortedIntList.java",
    "com/badlogic/gdx/utils/TimSort.java",
    "com/badlogic/gdx/utils/TimeUtils.java",
    "com/badlogic/gdx/utils/reflect/ArrayReflection.java",
  )

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
      frontend  = FrontendConfig(base, Files, Nil, resolutionRoots = Nil),
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

  /** The decision rungs a run may switch on above L0, each a manifest fragment (PROGRESS.md §13.29). */
  val Rungs: Map[String, List[balticporter.tir.Phase]] = Map(
    "nullable" -> List(new NullabilityTransform(
      annotations = Set("com.badlogic.gdx.utils.Null"),
      target      = NullabilityTransform.Target.Named("lowlevel.Nullable"),
      scope       = balticporter.tir.RuleScope.Everywhere(Set.empty))),
    "ordering" -> List(new CollectionsTransform(retarget = Map("java.util.Comparator" -> "scala.math.Ordering"))),
  )

  def core(repoRoot: Path, rungs: Set[String] = Set.empty): PortManifest =
    val unknown = rungs -- Rungs.keySet
    require(unknown.isEmpty, s"unknown lls rungs: ${unknown.mkString(",")}; known: ${Rungs.keys.toList.sorted.mkString(",")}")
    PortManifest(
      name    = "lls",
      governs = Set("com.badlogic.gdx.utils", "com.badlogic.gdx.math"),
      // The hand port's namespace. `packageRenames` is DATA, not a surface entry: `PortRun`
      // appends it LAST (CLAUDE.md §4.56).
      packageRenames = Map(
        "com.badlogic.gdx.utils" -> "lowlevel.util",
        "com.badlogic.gdx.math"  -> "lowlevel.math",
      ),
      // lls renamed `Array` to `DynamicArray`; `scala.Array` is not a name a port may shadow.
      typeRenames = Map(
        "com.badlogic.gdx.utils.Array" -> "DynamicArray",
      ),
      // L0 of the lls ladder: the universal phases only (`MutableParamsTransform` is universal but
      // per-port today); the decision rungs are added one at a time (PROGRESS.md §13.29).
      surface = List(new MutableParamsTransform) ++ Rungs.toList.sortBy(_._1).collect { case (k, ps) if rungs(k) => ps }.flatten,
      // THE REFERENCE HAND PORT for lls. NOT inherited (DESIGN.md §8.23).
      parity = Some(ParityRef(roots = List(
        repoRoot.resolve("../lls/lls/src/main/scala").normalize))),
    )
