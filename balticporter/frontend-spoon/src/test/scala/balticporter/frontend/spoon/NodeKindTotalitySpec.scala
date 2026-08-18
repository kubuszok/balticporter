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
      // ONE, and the three kinds that left went the two DIFFERENT ways this classification exists
      // to tell apart. `CtTextBlock` left when `TextBlockSpec` established that the absorption is
      // FAITHFUL — `CtLiteral.getValue` is JLS 3.10.6's denoted string, so the arm that takes it is
      // the right arm — and `CtRecord` left when the absorption turned out to be four defects at
      // once (`JS-C43`) and each was fixed. `CtAnnotationMethod` left the second way: the probe
      // that pinned it (`AbsorbedProbeSpec`) said an emitted `@interface` had NO elements at all,
      // and the elements are now the emitted class's parameters (`ENGINE-LIMITS.md` T22,
      // `AnnotationTypeSpec`). "Absorbed silently" is a SUSPICION; a probe either retires it or
      // turns it into work, and both outcomes have now happened.
      List("CtAnnotationFieldAccess"))
    // …and a FOURTH, added when `DESIGN.md` §6.2's marker took over the first two of
    // `SpoonTir.unsupported`'s six sites. A marked kind still blocks the port — the emission gate
    // refuses on any open marker — but the failure is now the size of the CONSTRUCT rather than the
    // size of the FILE, which is the difference between "this library cannot be ported" and "these
    // three declarations cannot".
    // ONE, and the two that left are `JS-S09`: `CtSwitchExpression` and `CtYieldStatement` are
    // `Lowered` now, because a scala `match` IS an expression and the image was `Tree.Match` all
    // along — what was missing was the arm, not the node. The same shape as `CtTextBlock`'s exit
    // from the list above, arrived at the other way round: there a probe retired a suspicion, here
    // a lowering retired a refusal.
    // `CtCasePattern` left when `JS-S10`'s TYPE-pattern half was lowered: the wrapper's arm exists
    // and reads the pattern, and WHICH pattern it holds is a fact the three rows below carry. What
    // ONE, and the one that left is `CtRecordPattern`: `JS-C43` derives an `unapply` over the
    // ACCESSORS — JLS 14.30.1's own member — so the pattern is an ordinary constructor pattern and
    // the arm is written (T19). What is left is a TYPE pattern, and only where it is an `instanceof`
    // OPERAND: java flow-scopes that binding and no lexical `val` placement is faithful (T18). As a
    // CASE LABEL the same kind lowers, which is why the refusal is about the position and not the
    // kind — and is exactly what this list cannot express, so it is said here.
    assertEquals(SpoonKinds.absentBy(SpoonKinds.Absence.MarkedUnportable),
      List("CtTypePattern"))
    // EMPTY, and the last three to leave are the correction worth keeping. The comment that used to
    // stand here named "the type operand of an `instanceof`" as a shape a term-level marker cannot
    // take — true of the OPERAND and false of the construct, because the whole `instanceof` is a
    // boolean expression and marking there refuses the same thing at the size of an expression.
    // No kind a java source can produce now costs a whole compilation unit.
    assertEquals(SpoonKinds.absentBy(SpoonKinds.Absence.RefusedLoudly), Nil)
    // NINE, and the ninth is a kind that was on the REFUSED list until a probe went looking for a
    // fixture that reaches it and found none: `CtUnnamedPattern` is not something this parser builds
    // from any source it accepts. A refusal nobody can trigger reads exactly like a refusal that
    // fires, which is the reason this census is three named lists and not a total.
    // `CtRecordComponent` left this list for `positional`: `getRecordComponents` IS called now, and
    // the component is not a member the walk reaches but the declaration `JS-C43`'s three derived
    // members are read from.
    assertEquals(SpoonKinds.absentBy(SpoonKinds.Absence.NeverVisited),
      List("CtModule", "CtModuleRequirement", "CtPackage", "CtPackageDeclaration", "CtPackageExport",
        "CtProvidedService", "CtReceiverParameter", "CtUnnamedPattern",
        "CtUsedService"))
  }

  test("the accounting, printed — derived from the jar, stated as a constant nowhere") {
    val producible = declared -- SpoonKinds.excluded
    println(s"[spoon-kinds] jar=${declared.size} excluded=${SpoonKinds.excluded.size} " +
      s"producible=${producible.size} " +
      s"(lowered=${SpoonKinds.lowered.size} positional=${SpoonKinds.positional.size} absent=${SpoonKinds.absent.size})")
    assertEquals(SpoonKinds.registry.size, producible.size)
  }
