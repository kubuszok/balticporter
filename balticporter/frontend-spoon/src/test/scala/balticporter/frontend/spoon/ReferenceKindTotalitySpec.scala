package balticporter.frontend.spoon

import scala.jdk.CollectionConverters.*

/** REFERENCE-KIND TOTALITY — [[NodeKindTotalitySpec]]'s argument at the THIRD Spoon package, which
  * is where the catalog's fourth obligation surface gets its keys.
  *
  * `SpoonTir.tpe` is an ORDERED match over `CtTypeReference` and its four sub-interfaces, and its
  * final arm is the supertype's. So a reference kind Spoon adds tomorrow is absorbed there
  * silently and renders as an ordinary class reference — the `CtTextBlock` shape one package over,
  * except that the wrong answer here is a TYPE, which reaches every signature that names it.
  *
  * The method is the other spec's, unchanged and deliberately so:
  *
  *   declared   = every `Ct*` under `spoon.reflect.reference` IN THE RESOLVED JAR
  *   excluded   = `SpoonKinds.refExcluded` — a COMMITTED, DIFFABLE Set[String]
  *   registry   = `SpoonKinds.references`
  *   assert       declared -- excluded == registry
  *
  * TWO REGISTRIES AND TWO SCANS, never one of each. Folding the reference kinds into
  * `SpoonKinds.registry` would make a single total answer for two independent taxonomies, and a
  * Spoon upgrade's diff — the whole product of this mechanism — would stop saying which package
  * moved.
  */
class ReferenceKindTotalitySpec extends munit.FunSuite:

  /** Every `Ct*` interface name under `spoon.reflect.reference`, read from the jar `CtReference`
    * was loaded from. */
  private lazy val declared: Set[String] =
    val loc = classOf[spoon.reflect.reference.CtReference].getProtectionDomain.getCodeSource.getLocation
    val zf  = java.util.zip.ZipFile(java.nio.file.Path.of(loc.toURI).toFile)
    try
      zf.entries().asScala
        .map(_.getName)
        .filter(_.startsWith("spoon/reflect/reference/"))
        .filter(_.endsWith(".class"))
        .map(n => n.stripSuffix(".class").substring(n.lastIndexOf('/') + 1))
        .filter(n => n.startsWith("Ct") && !n.contains('$'))
        .toSet
    finally zf.close()

  test("the jar scan actually found the reference taxonomy — a silent zero makes the rest vacuous") {
    // Pinned by SHAPE, never by a total (`PortabilityCheck`'s phantom "34 rules"). These four are
    // the load-bearing corners: the root supertype, the plain leaf `tpe`'s final arm takes, the
    // sub-interface whose IMPLEMENTATION extends another registered one, and a reference that is
    // not a type at all.
    assert(declared.sizeIs > 10, s"only ${declared.size} Ct* reference types found — the jar layout has moved")
    assert(declared("CtReference"))
    assert(declared("CtTypeReference"))
    assert(declared("CtWildcardReference"))
    assert(declared("CtExecutableReference"))
  }

  test("TOTALITY: every producible reference kind has a claim, and every claim names a real kind") {
    val producible = declared -- SpoonKinds.refExcluded
    val claimed    = SpoonKinds.byRefName.keySet
    val unclaimed  = (producible -- claimed).toList.sorted
    val phantom    = (claimed -- producible).toList.sorted
    assertEquals(unclaimed, Nil,
      "these Spoon reference kinds have no entry in SpoonKinds.references — say what the frontend " +
        s"does with each, or exclude it with the test that put it there: ${unclaimed.mkString(", ")}")
    assertEquals(phantom, Nil,
      s"SpoonKinds.references claims kinds the jar does not have: ${phantom.mkString(", ")}")
  }

  test("the EXCLUSION set is about kinds that exist") {
    val stale = (SpoonKinds.refExcluded -- declared).toList.sorted
    assertEquals(stale, Nil, s"excluded names not present in the jar: ${stale.mkString(", ")}")
  }

  test("no reference kind is claimed twice, and every catalog pointer resolves") {
    val dupes = SpoonKinds.references.groupBy(_.name).filter(_._2.sizeIs > 1).keys.toList.sorted
    assertEquals(dupes, Nil, dupes.mkString(", "))
    val dangling = SpoonKinds.references.flatMap(_.catalog)
      .filterNot(balticporter.catalog.Differences.byId.contains)
    assertEquals(dangling, Nil, s"a reference kind points at a catalog row that does not exist: ${dangling.mkString(", ")}")
  }

  test("every catalog row attaching to the TYPE-REFERENCE dispatch names a kind that exists") {
    // The registry's half of the derivation, and the exact counterpart of
    // `EmissionFieldCoverageSpec`'s assertion for `Attaches.Rendered`. A misspelt
    // `Attaches.LoweredType` kind attaches to no reference, so it owes no consult, produces no hole
    // and reports `unreached` on every port forever — a failure indistinguishable from a branch the
    // corpus does not exercise.
    val phantom = (balticporter.catalog.Differences.loweredTypeKinds -- SpoonKinds.byRefName.keySet).toList.sorted
    assertEquals(phantom, Nil,
      s"these rows attach to a Spoon reference kind the registry does not have: ${phantom.mkString(", ")}")
  }

  test("a row may only attach where an arm can CONSULT it — the LOWERED reference kinds") {
    // The `Claim.Positional` rule, mechanised at the surface it matters for: a reference the
    // frontend never hands to `tpe` never enters the type dispatch, so a row attached to it would
    // sit on `mechanised` reading `unreached` on every port forever. That is `JS-G39`'s defect
    // exactly — a row whose surface can never be reached, with no hole and no lower consult count
    // to give it away — and here it is a compile-time-cheap assertion instead.
    val notLowered = balticporter.catalog.Differences.loweredTypeKinds.toList.sorted.filterNot(k =>
      SpoonKinds.byRefName.get(k).exists(_.claim.isInstanceOf[SpoonKinds.Claim.Lowered]))
    assertEquals(notLowered, Nil,
      "these rows attach to a reference kind `SpoonTir.tpe` never dispatches on, so the obligation " +
        s"could never be owed: ${notLowered.mkString(", ")}")
  }

  test("`refNameOf` answers the MOST SPECIFIC kind — the wildcard/type-parameter pair") {
    // The one place the shortcut and the structural walk disagree, and the reason the resolver is
    // shared with `nameOf` rather than copied: `CtWildcardReferenceImpl` EXTENDS
    // `CtTypeParameterReferenceImpl`, so a resolver answering the first registered supertype would
    // key every wildcard as a type variable and hand it the wrong arm's obligations.
    assertEquals(SpoonKinds.refNameOf(classOf[spoon.support.reflect.reference.CtWildcardReferenceImpl]),
      "CtWildcardReference")
    assertEquals(SpoonKinds.refNameOf(classOf[spoon.support.reflect.reference.CtTypeParameterReferenceImpl]),
      "CtTypeParameterReference")
    assertEquals(SpoonKinds.refNameOf(classOf[spoon.support.reflect.reference.CtTypeReferenceImpl[?]]),
      "CtTypeReference")
    // …and the two registries stay apart: a NODE kind is not a reference kind, and asking the
    // reference registry for one must not answer a registered name.
    assert(!SpoonKinds.byRefName.contains(SpoonKinds.nameOf(classOf[spoon.support.reflect.code.CtInvocationImpl[?]])))
  }

  test("the accounting, printed — derived from the jar, stated as a constant nowhere") {
    val producible = declared -- SpoonKinds.refExcluded
    println(s"[spoon-refs] jar=${declared.size} excluded=${SpoonKinds.refExcluded.size} " +
      s"producible=${producible.size} (registry=${SpoonKinds.references.size})")
    assertEquals(SpoonKinds.references.size, producible.size)
  }
