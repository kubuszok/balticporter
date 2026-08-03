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

  /** the map as libGDX core last PUBLISHED it — not a fixture. */
  private def baseMap: Option[PortMap.Map0] =
    List("run-latest", "baseline").iterator
      .map(d => repoRoot.resolve(s"port-report/LibgdxCoreMigrate/$d/port-map.tsv"))
      .filter(Files.isRegularFile(_))
      .flatMap(p => PortMap.read(p).toOption)
      .nextOption()

  test("ACCEPTANCE: the base's published map reports the forwarder BEFORE emission, naming the base") {
    assume(Files.isDirectory(ashley) && Files.isDirectory(gdxSrc),
      "vendored corpus sources absent — run this from a checkout that has ../sge")
    val map = baseMap
    assume(map.isDefined, "libGDX core has not been run in this checkout, so it has published no map")

    val files = Files.walk(ashley).iterator().asScala
      .filter(_.toString.endsWith(".java"))
      .map(p => ashley.relativize(p).toString)
      .filterNot(f => f.endsWith("package-info.java") || f.endsWith("module-info.java"))
      .toList.sorted
    val types = SpoonTir.buildModel(
      FrontendConfig(ashley, files, Nil, resolutionRoots = List(gdxSrc)), lenient = true)
    val phase = new PortMapTransform(map.toList)
    // NO substitutions: this is Ashley as it stood before it learned the base's decisions, which is
    // the state a dependent's first run is in and the only state where the finding can arise.
    Pipeline.run(SpoonTir.fromTypes(types, Substitutions.none), List(phase))

    val dropped = phase.findings.filter(_.issue == PortMapTransform.Issue.DroppedMember)
    assertEquals(clue(dropped).map(f => (f.symbol, f.base)),
      List(("com.badlogic.gdx.utils.Array#toArray(Class)", "libgdx-core")))
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
    // in `PooledEngine`, and `ClassReflection` + `ReflectionException` in `Engine`.
    //
    // 7 -> 8 when the map's `upstream` column stopped being derived by INVERTING the package rename
    // and started coming from the java ORIGIN. The eighth is `Engine`'s `catch (ReflectionException
    // e)`: a genuine reference to a dropped type that the map could not name while its key was
    // wrong. A number that rises because the lookup got correct is the check starting to work.
    assertEquals(phase.findings.count(_.issue == PortMapTransform.Issue.DroppedType), 8)
  }
