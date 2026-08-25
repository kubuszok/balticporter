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

  /** libGDX core's COMMITTED BASELINE map — the artifact a FRESH CHECKOUT has, and deliberately not
    * `run-latest`.
    *
    * `run-latest` is ANOTHER RUN's output, so a spec keyed on it does not execute until somebody
    * happens to run the base first — and `sbt <project>/testOnly *` gates on nothing, printing
    * `Skipped 1` and exiting 0 (`CLAUDE.md` §5.1). That is not hypothetical here: this spec asserted
    * a `DroppedType` count of **8** while the answer had been **7** since the base gained an injected
    * `ReflectionException`, and every `corpus/test` in between reported success without executing it.
    *
    * The baseline is committed, `just baseline-accept LibgdxCoreMigrate` is what moves it, and every
    * number below is therefore read off an artifact somebody acknowledged rather than one this
    * machine happened to produce. A missing or unreadable baseline is FATAL for the same reason a
    * declared `classpathFile` that is not there is (§4.57): a spec that meant to check this and
    * silently did not looks exactly like one that checked it. */
  private val baseMapPath = repoRoot.resolve("port-report/LibgdxCoreMigrate/baseline/port-map.tsv")

  private def baseMap: PortMap.Map0 =
    if !Files.isRegularFile(baseMapPath) then
      fail(s"the base's COMMITTED baseline map is missing: $baseMapPath — this spec is not skippable; "
        + "restore it from git or run `just baseline-accept LibgdxCoreMigrate`")
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
    // it moves when the port does: Ashley's references to types the base drops — `ReflectionPool`
    // in `PooledEngine` (6 sites) and `ClassReflection` in `Engine` (1).
    //
    // 7 -> 8 when the map's `upstream` column stopped being derived by INVERTING the package rename
    // and started coming from the java ORIGIN. The eighth was `Engine`'s `catch (ReflectionException
    // e)`: a genuine reference to a dropped type that the map could not name while its key was
    // wrong. A number that rises because the lookup got correct is the check starting to work.
    //
    // 8 -> 7 when the base gained an INJECTED `sge.utils.reflect.ReflectionException`, which moves
    // that type from `Dropped` to `Substituted` in the published map — and `DroppedType` is
    // deliberately not raised for a `Substituted` one, because a replacement stands at the same
    // name and the reference is callable. So the eighth site is still a reference to the same type;
    // it is no longer a PROBLEM, and the phase saying so is the phase being right. Read the
    // disposition in the map (`grep ReflectionException port-report/LibgdxCoreMigrate/baseline/
    // port-map.tsv`) before ever moving this number: `Dropped` and `Substituted` are one column
    // apart and mean opposite things here.
    assertEquals(phase.findings.count(_.issue == PortMapTransform.Issue.DroppedType), 7)
  }
