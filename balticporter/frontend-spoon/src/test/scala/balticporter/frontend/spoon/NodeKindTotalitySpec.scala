package balticporter.frontend.spoon

import scala.jdk.CollectionConverters.*

/** NODE-KIND TOTALITY — the half of the total-match requirement that scalac cannot give.
  *
  * The TIR side is already total by construction: `Tree` is sealed and the emitter's dispatch ends
  * with no default arm, so a new node kind is a compile error. The JAVA side has no sealedness at
  * all — `CtElement` is an ordinary interface hierarchy — so a `match` over it is exhaustive only by
  * inspection, and the arms are ORDERED: a kind that extends a kind the frontend dispatches on is
  * absorbed by that supertype's arm, silently, and looks handled from every angle except the emitted
  * output.
  *
  * So this spec is the inspection, mechanised:
  *
  *   declared   = every `Ct*` under `spoon.reflect.{code,declaration}` IN THE RESOLVED JAR
  *   excluded   = `SpoonKinds.excluded` — a COMMITTED, DIFFABLE Set[String]
  *   registry   = `SpoonKinds.registry` — what the frontend claims about each producible kind
  *   assert       declared -- excluded == registry
  *
  * TWO HALVES THAT MUST NOT BE CONFUSED. The COUNT is derived from the jar and is written down
  * nowhere — a constant here would be `PortabilityCheck`'s phantom "34 rules" waiting to happen, and
  * this spec would be the thing that made it credible. The FILTER is hand-maintained on purpose: a
  * reflective predicate would be cheaper and would make the exclusion invisible, and the one thing
  * this spec exists to surface is what somebody decided not to handle. A Spoon upgrade therefore
  * produces a DIFF OF NAMES, in a review, rather than a number that quietly moved.
  *
  * COST, honestly: this fails on a Spoon upgrade that adds a node kind. That is the feature. It is
  * also the only thing in the design that will annoy an unrelated dependency bump, and it is worth
  * it — every kind on the ABSENT list below was found this way and four of them fail silently. */
class NodeKindTotalitySpec extends munit.FunSuite:

  /** Every `Ct*` interface name under the two node packages, read from the jar `CtElement` was
    * loaded from. Not a `Class.forName` sweep over a hand-written list — that is the shape that
    * cannot see a kind nobody thought to name, which is the whole failure this spec addresses.
    *
    * Nested classfiles (`CtComment$CommentType` and friends) are dropped by the `$` test and are not
    * node kinds; the two `enum`s that DO live in these packages are named in `SpoonKinds.notNodeKinds`
    * instead, because "it is not an interface" is a fact worth having in the diffable list rather
    * than in a predicate. */
  private lazy val declared: Set[String] =
    val loc = classOf[spoon.reflect.declaration.CtElement].getProtectionDomain.getCodeSource.getLocation
    val zf  = java.util.zip.ZipFile(java.nio.file.Path.of(loc.toURI).toFile)
    try
      zf.entries().asScala
        .map(_.getName)
        .filter(n => n.startsWith("spoon/reflect/code/") || n.startsWith("spoon/reflect/declaration/"))
        .filter(_.endsWith(".class"))
        .map(n => n.stripSuffix(".class").substring(n.lastIndexOf('/') + 1))
        .filter(n => n.startsWith("Ct") && !n.contains('$'))
        .toSet
    finally zf.close()

  test("the jar scan actually found the taxonomy — a silent zero would make every assertion below vacuous") {
    // Pinned by SHAPE, not by a total: a total is the constant this spec exists not to have. These
    // four are the load-bearing corners — a supertype, a leaf, a kind nothing reaches, and one of
    // the two enums that live in the same packages and are not node kinds.
    assert(declared.sizeIs > 50, s"only ${declared.size} Ct* types found — the jar layout has moved")
    assert(declared("CtElement"))
    assert(declared("CtInvocation"))
    assert(declared("CtTextBlock"))
    assert(declared("CtImportKind"))
    assert(!declared.exists(_.contains("$")), "a nested classfile reached the taxonomy")
  }

  test("TOTALITY: every producible Spoon kind has a claim, and every claim names a real kind") {
    val producible = declared -- SpoonKinds.excluded
    val claimed    = SpoonKinds.byName.keySet

    // Reported as two directions, never as one set comparison, because they are two different
    // failures with two different fixes. A kind the jar has and the registry does not is a Spoon
    // upgrade nobody has read; a kind the registry has and the jar does not is a name that was
    // removed or misspelt, and it is the one that makes an exclusion list rot quietly.
    val unclaimed = (producible -- claimed).toList.sorted
    val phantom   = (claimed -- producible).toList.sorted
    assertEquals(unclaimed, Nil,
      "these Spoon kinds have no entry in SpoonKinds.registry — say what the frontend does with each, " +
        s"or add it to the exclusion set with the test that put it there: ${unclaimed.mkString(", ")}")
    assertEquals(phantom, Nil,
      "SpoonKinds.registry claims kinds the jar does not have — a renamed or misspelt entry: " +
        phantom.mkString(", "))
  }

  test("the EXCLUSION set is about kinds that exist — a stale marker name is a hole with a lid on it") {
    // An excluded name the jar no longer has is worse than a missing one: the list looks
    // maintained, and the kind that replaced it is unclaimed with nothing pointing at the gap.
    val stale = (SpoonKinds.excluded -- declared).toList.sorted
    assertEquals(stale, Nil, s"excluded names not present in the jar: ${stale.mkString(", ")}")
  }

  test("no kind is claimed twice, and every catalog pointer resolves") {
    val dupes = SpoonKinds.registry.groupBy(_.name).filter(_._2.sizeIs > 1).keys.toList.sorted
    assertEquals(dupes, Nil, dupes.mkString(", "))
    val dangling = SpoonKinds.registry.flatMap(_.catalog).filterNot(balticporter.catalog.Differences.byId.contains)
    assertEquals(dangling, Nil, s"a kind points at a catalog row that does not exist: ${dangling.mkString(", ")}")
  }

  test("the ABSENT kinds are PINNED — the list may move, and it may not move by accident") {
    // Pinned as three named lists rather than as a count, because the three failure modes are
    // nothing like each other in a port: a loud refusal costs the whole compilation unit, a silent
    // absorption costs a construct with a green compile and no moved count, and a never-visited
    // kind costs whatever was written in a file the walk does not enter. A single number would let
    // one shrink while another grew.
    assertEquals(SpoonKinds.absentBy(SpoonKinds.Absence.AbsorbedSilently),
      List("CtAnnotationFieldAccess", "CtAnnotationMethod", "CtRecord", "CtTextBlock"))
    // …and a FOURTH, added when `DESIGN.md` §6.2's marker took over the first two of
    // `SpoonTir.unsupported`'s six sites. A marked kind still blocks the port — the emission gate
    // refuses on any open marker — but the failure is now the size of the CONSTRUCT rather than the
    // size of the FILE, which is the difference between "this library cannot be ported" and "these
    // three declarations cannot".
    assertEquals(SpoonKinds.absentBy(SpoonKinds.Absence.MarkedUnportable),
      List("CtCasePattern", "CtSwitchExpression", "CtYieldStatement"))
    // What is LEFT here is the four sites whose SHAPE a term-level marker cannot take: a `Constant`,
    // a `ValDef`, the type operand of an `instanceof`, a lambda with no body. Each is a real mint
    // site wanting a marker of its own kind, and putting a term where the tree needs a declaration
    // would be worse than the throw.
    assertEquals(SpoonKinds.absentBy(SpoonKinds.Absence.RefusedLoudly),
      List("CtRecordPattern", "CtTypePattern", "CtUnnamedPattern"))
    assertEquals(SpoonKinds.absentBy(SpoonKinds.Absence.NeverVisited),
      List("CtModule", "CtModuleRequirement", "CtPackage", "CtPackageDeclaration", "CtPackageExport",
        "CtProvidedService", "CtReceiverParameter", "CtRecordComponent", "CtUsedService"))
  }

  test("the accounting, printed — derived from the jar, stated as a constant nowhere") {
    val producible = declared -- SpoonKinds.excluded
    println(s"[spoon-kinds] jar=${declared.size} excluded=${SpoonKinds.excluded.size} " +
      s"producible=${producible.size} " +
      s"(lowered=${SpoonKinds.lowered.size} positional=${SpoonKinds.positional.size} absent=${SpoonKinds.absent.size})")
    assertEquals(SpoonKinds.registry.size, producible.size)
  }
