package balticporter.corpus

import balticporter.core.{FrontendConfig, PortMap, Substitutions}
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.Pipeline
import balticporter.transform.PortMapTransform

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** The acceptance case of `DESIGN.md` §5.4, on the REAL sources and the REAL artifact.
  *
  * Ashley's `ImmutableArray.toArray(Class)` forwards to libGDX's `Array.toArray(Class)`, which the
  * base drops. It was found by `RewriteTrace`'s orphaned-call check on Ashley's first run — AFTER
  * translating and emitting, and saying only that a member had no declaration. The claim being
  * tested is that with the base's published map it is a lookup answerable BEFORE translation, whose
  * message names the module that dropped it.
  *
  * Ashley's manifest has since declared its own `dropMethods` key for that forwarder, so the real
  * migration no longer contains the call at all. This therefore models the state the map is supposed
  * to help with — a dependent that has NOT yet learned the base's decision — by parsing Ashley's 21
  * files with no substitutions, exactly as its first run did.
  *
  * It costs a full Spoon model over libGDX plus Ashley (~20s). That is paid deliberately: the same
  * property against a two-file stub passed while the phase was still reporting 263 findings on real
  * sources, 255 of them in the base's own files and every `toArray` site `Ambiguous`. A stub could
  * not have shown either defect, because both are properties of a real library's size and its
  * same-arity overloads.
  */
class PortMapAcceptanceSpec extends munit.FunSuite:

  override val munitTimeout = scala.concurrent.duration.Duration(600, "s")

  private val repoRoot = Path.of(sys.props.getOrElse("balticporter.root", ".")).toAbsolutePath.normalize
  private val ashley   = repoRoot.resolve("../sge/original-src/ashley/ashley/src").normalize
  private val gdxSrc   = repoRoot.resolve("../sge/original-src/libgdx/gdx/src").normalize

  /** libGDX core's published map — `run-latest` when a run has produced one, falling back to the
    * COMMITTED BASELINE.
    *
    * `run-latest` is preferred because a fix to the MAP WRITER (D16: `PortMap.of` now passes the
    * full rename table so type renames are inverted correctly in the `upstream` column) produces a
    * correct artifact that the committed baseline does not yet reflect. A spec that reads the
    * baseline while `run-latest` carries the fix would keep asserting against the DEFECTIVE map it
    * exists to test. The baseline is the fallback for a fresh checkout where nothing has been run.
    *
    * A missing or unreadable map is FATAL for the same reason a declared `classpathFile` that is not
    * there is (§4.57): a spec that meant to check this and silently did not looks exactly like one
    * that checked it. */
  private val reportDir = repoRoot.resolve("port-report/LibgdxCoreMigrate")
  private val baseMapPath = List("run-latest", "baseline").iterator
    .map(d => reportDir.resolve(d).resolve("port-map.tsv"))
    .find(Files.isRegularFile(_))
    .getOrElse(reportDir.resolve("baseline/port-map.tsv"))

  private def baseMap: PortMap.Map0 =
    if !Files.isRegularFile(baseMapPath) then
      fail(s"the base's map is missing: neither run-latest nor baseline at $reportDir — " +
        "this spec is not skippable; run `just gdx-measure` or restore the baseline from git")
    PortMap.read(baseMapPath).fold(e => fail(s"$baseMapPath is unreadable: $e"), identity)

  test("ACCEPTANCE: the base's published map reports the forwarder BEFORE emission, naming the base") {
    // The vendored sources are FATAL too, and for §5.1's reason rather than for convenience: this
    // spec's whole claim is a property of a real library's size and its same-arity overloads, so a
    // checkout that cannot see them cannot check it, and saying so is the only honest outcome.
    assert(Files.isDirectory(ashley) && Files.isDirectory(gdxSrc),
      s"vendored corpus sources absent ($ashley, $gdxSrc) — run this from a checkout that has ../sge")
    val map = baseMap

    val files = Files.walk(ashley).iterator().asScala
      .filter(_.toString.endsWith(".java"))
      .map(p => ashley.relativize(p).toString)
      .filterNot(f => f.endsWith("package-info.java") || f.endsWith("module-info.java"))
      .toList.sorted
    val types = SpoonTir.buildModel(
      FrontendConfig(ashley, files, Nil, resolutionRoots = List(gdxSrc)), lenient = true)
    val phase = new PortMapTransform(List(map))
    // NO substitutions: this is Ashley as it stood before it learned the base's decisions, which is
    // the state a dependent's first run is in and the only state where the finding can arise.
    Pipeline.run(SpoonTir.fromTypes(types, Substitutions.none), List(phase))

    val dropped = phase.findings.filter(_.issue == PortMapTransform.Issue.DroppedMember)
    assertEquals(clue(dropped).map(f => (f.symbol, f.base)),
      List(("com.badlogic.gdx.utils.Array#toArray(Class)", "sge")))
    assert(clue(dropped.head.origin.javaPath).endsWith("com/badlogic/ashley/utils/ImmutableArray.java"))

    // Every finding is in ASHLEY's own files. libGDX is a resolution root, so its 596 units and
    // every call it makes into its own dropped members are in this program too — reporting those
    // told the dependent's author about 255 sites in a module they neither own nor can fix.
    assert(clue(phase.findings.map(_.origin.javaPath).distinct).forall(_.contains("/ashley/")))

    // …and nothing was undecidable. `Array#toArray(ArraySupplier)` is Ported and `toArray(Class)` is
    // Dropped at the SAME ARITY, so arity alone cannot separate them and every site here came back
    // `Ambiguous` until the callee symbol's own `info` supplied the precise key.
    assertEquals(clue(phase.findings).filter(_.issue == PortMapTransform.Issue.Ambiguous), Nil)

    // The other thing the map surfaces early on this corpus, kept as a number rather than a list so
    // it moves when the port does: Ashley's references to types the base drops.
    //
    // 7 -> 68 (wave 3.3a, D16): the map's `upstream` column now carries java's own FQN for
    // type-renamed types, and phantom emitted-type entries (types in both `emittedTypes` and
    // `dropTypes` due to a namespace mismatch in the filter) are excluded. With correct upstream
    // names, `ownedByBase` now matches base types correctly (so base-owned references are filtered
    // out), AND `byUpstream` now returns the `Dropped` entry instead of a phantom `Renamed` one for
    // 35 retargeted types. The 68 are ALL in Ashley's own files — references to types the base
    // drops that were previously invisible because the map's upstream names were wrong or because
    // the `Dropped` row was shadowed by a phantom `Renamed` row.
    // Wave 3.2g drops `Pool` too — but INJECTS sge's trait at the same FQN, so the map records it
    // `Substituted`, and `DroppedType` is deliberately not raised for a `Substituted` type (a
    // replacement stands at the name). The count therefore stays at the D16 number.
    assertEquals(phase.findings.count(_.issue == PortMapTransform.Issue.DroppedType), 68)
  }
