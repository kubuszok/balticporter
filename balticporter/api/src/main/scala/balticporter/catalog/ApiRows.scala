package balticporter.catalog

import Area.*
import Availability.*
import Verdict.*

/** which of the three backends a claim is about. */
enum Platform:
  case Jvm, ScalaJs, ScalaNative

/** what the PLATFORM provides. This is the (a) FACT half of an [[ApiRow]] — true of every port, and
  * no manifest may contradict it, because asserting otherwise is how a port silently declares a gap
  * closed. */
enum Availability:
  case Full
  /** present, but not equivalent — `why` says how it differs */
  case Partial(why: String)
  /** present and INERT: it links and does nothing useful. The most dangerous state, because it
    * fails at run time rather than at link time */
  case Stub(why: String)
  case Absent

/** what a port should DO about the row on that platform. This is the RECOMMENDATION half, and it is
  * a default rather than a fact — another port may ship its own shim, vendor a subset, or accept the
  * refusal. The mechanism that lets a port say so (`PortManifest.verdictOverrides`) belongs to the
  * chunk that parameterises `PortabilityCheck`; until then a verdict is advice this registry states
  * and nothing reads. */
enum Verdict:
  /** the JDK type works as it stands */
  case Keep
  /** rewrite to a Scala target. Named `MapTo` and not `Map` for the obvious clash */
  case MapTo(target: String)
  /** the API exists in a third-party artifact the build must add */
  case Depend(dep: ArtifactDep)
  /** the port must supply the behaviour itself; `where` names the shim */
  case Shim(where: String)
  /** no faithful target exists — say so loudly rather than degrade */
  case Refuse(why: String)

  /** Does a port TARGETING THIS PLATFORM have to do something about the row?
    *
    * `Keep` is the only "nothing to do", and everything else is work of one kind or another — which
    * is what makes this the criterion a portability rule is derived through rather than
    * `isInstanceOf[Refuse]`. Two of the engine's oldest rules settle it: `java.lang.Thread` and
    * `java.util.concurrent.` are `MapTo` on Scala.js (a per-backend trait, the reference port's own
    * answer) and have been reported as violations since the check had a rule for them. Reading only
    * `Refuse` would have deleted both, which is the lane reset the platform chunk exists not to be. */
  def actionable: Boolean = this != Keep

  /** …and WHICH KIND of work. A `Depend` is a BUILD-GRAPH fact — the API exists, in an artifact the
    * port's build does not name — and conflating that with "no faithful target exists" is what makes
    * a `WeakReference` finding unanswerable: the reader is told to remove a call that a one-line
    * `libraryDependencies` entry makes correct. */
  def dependency: Option[ArtifactDep] = this match
    case Depend(d) => Some(d)
    case _         => scala.None

/** How a dependency's ARTIFACT NAME is built — the `%` / `%%` / `%%%` question, as a fact about the
  * artifact rather than about the port. */
enum CrossKind:
  /** a plain Java artifact: `"org" % "name" % "rev"`. */
  case Java
  /** cross-built per SCALA version: `%%`. */
  case Scala
  /** cross-built per scala version AND per PLATFORM: `%%%`, which needs the crossproject plugin —
    * which a port declaring a platform-cross dependency necessarily has. */
  case Platform

/** a build-graph coordinate a [[Verdict.Depend]] names.
  *
  * Every field is a literal or an enum case. What is deliberately NOT here is the PLATFORM SET: a
  * `platforms` field would be a second statement of something the ROW already makes — which
  * platforms need the artifact is exactly `verdict`'s per-platform map, and two coordinates for one
  * artifact (`MessageDigest` wants `scala-crypto` on Scala.js and `scala-native-crypto` on Native)
  * are two `Depend` verdicts rather than one dep with a set. It would also be the one shape
  * `ApiRowCarriesNoPolicySpec` forbids outright.
  *
  * @param resolver
  *   WHERE the artifact resolves from, when that is not the default repository. A fact about the
  *   ARTIFACT and not about the port, exactly as [[cross]] is — and it fails the same way when it is
  *   absent: `%%` written where `%%%` was meant asks for a jar that does not exist, and a coordinate
  *   published outside Maven Central asks a resolver set that has never heard of it. `None` is the
  *   default and means the ordinary resolver set, so every coordinate written before this field
  *   existed says exactly what it always did and no generated build moves.
  *
  *   ONE URL, not a name/URL pair and not a collection. The NAME an sbt `resolvers` entry needs
  *   carries no information a reader could check, so [[balticporter.sbtgen.SbtGen.Dep]] derives it
  *   from the URL — deterministically, which is what stops it from lying — and a coordinate
  *   resolvable from two places is one this file has no way to choose between.
  *   `ApiRowCarriesNoPolicySpec` forbids a collection in a row for its own reason, and an `Option`
  *   of a literal is exactly what stays inside it. */
final case class ArtifactDep(org: String, name: String, rev: String, cross: CrossKind = CrossKind.Scala,
                             resolver: Option[String] = scala.None):
  override def toString: String = s"$org${CrossKind.sep(cross)}$name:$rev"

object CrossKind:
  /** the separator as a build file spells it, and as a `toString` reads it. */
  def sep(k: CrossKind): String = k match
    case Java     => ":"
    case Scala    => "::"
    case Platform => ":::"

/** ONE ROW of the library and platform halves — `JS-L` and `JS-P`.
  *
  * A DIFFERENT KIND OF ROW from a [[Difference]], which is why it has its own shape rather than
  * being forced into that one: a `Difference` states a rule of two LANGUAGES and is true forever; an
  * `ApiRow` states what an IMPLEMENTATION shipped, which is true as of a version and false after the
  * next release. That is what [[asOf]] is for and why it is not decoration.
  *
  * THE NO-POLICY RULE, and it is the `JS-{E,S,C,G}` no-parameter rule adapted rather than relaxed:
  * an `ApiRow` legitimately carries per-platform MAPS, because JS and Native genuinely disagree and
  * a single shared verdict is exactly what makes a rule wrong for one of them. What it may NOT carry
  * is a `RuleScope`, a predicate, or a TARGET SET. A row says what is true of a platform; WHICH
  * platforms a port cares about is the port's, and it lives in the manifest.
  * `ApiRowCarriesNoPolicySpec` is that line, mechanised.
  *
  * @param fqn     a PREFIX (`java.nio.file.`, cut only at a separator) or an exact member
  *                (`java.lang.System#getenv`)
  * @param exact   which of the two `fqn` is
  * @param by      availability per platform — the (a) FACT
  * @param verdict the recommended action per platform — a DEFAULT
  * @param asOf    the versions the claim is anchored to, e.g. `scala-js -> 1.22.0`. EMPTY means the
  *                source survey stated none, and `why` must then say `UNSTATED` out loud: a coverage
  *                claim with no version goes stale on the next release, and a claim that cannot say
  *                when it was true is one nothing can ever re-check
  * @param why     ONE sentence
  */
final case class ApiRow(
    id: DiffId,
    fqn: String,
    exact: Boolean,
    by: Map[Platform, Availability],
    verdict: Map[Platform, Verdict],
    asOf: Map[String, String],
    why: String,
):

  /** The verdict THIS PORT reads for `p` — the row's own recommendation unless the port overrode it.
    *
    * The two halves of a row are two epistemic kinds and only one of them is overridable. [[by]] is
    * the (a) FACT: *`java.time` is absent on Scala Native at 0.5.11* is true of every port, and a
    * manifest that could contradict it is a manifest in which a port silently declares a gap closed.
    * [[verdict]] is a RECOMMENDATION — another port may ship its own shim, vendor a subset, or accept
    * the refusal — so it is the half `PortManifest.verdictOverrides` reaches, and the override is a
    * `Decision` naming the manifest entry verbatim.
    */
  def verdictOn(p: Platform, overrides: Map[DiffId, Map[Platform, Verdict]] = Map.empty): Verdict =
    overrides.get(id).flatMap(_.get(p)).getOrElse(verdict(p))

  /** every declared target this row asks the port to do something for. */
  def actionableOn(targets: Set[Platform], overrides: Map[DiffId, Map[Platform, Verdict]] = Map.empty): Set[Platform] =
    targets.filter(p => verdictOn(p, overrides).actionable)

/** The library and platform halves of the catalog.
  *
  * SOURCE AND ITS LIMITS, stated because a coverage claim without its provenance is the thing this
  * file exists to stop: every row is transcribed from a source-tree survey of the two javalibs, at
  * the versions in [[JsNative]]. Where that survey did not reach a platform the row carries
  * `UNSTATED` and says so in its `why` rather than guessing — there are several, and they are the
  * rows a later pass should measure first.
  *
  * WHAT READS IT, and what each half of a row decides. It is no longer a registry without a
  * consumer — `PortabilityCheck.rowOf` joins a rule to its row through `Rule.at`, and two questions
  * are answered from there on every run:
  *
  *   - WHICH LANE a rule is in. `isDependency` asks whether every platform the rule still asks about
  *     answers with an ARTIFACT, and that decides `rulesFor` (the removal lane, `portability(*)`)
  *     against `dependencyRulesFor` (the build-graph lane, `dependency-coverage`). A rule with no
  *     cited row is never a dependency: the survey is where a coordinate comes from, and a rule with
  *     no row has none to give;
  *   - WHETHER IT STILL ASKS AT ALL. `stillAsks` reads [[ApiRow.verdictOn]], which is the one half of
  *     a row a port may override — so a `verdictOverrides` entry saying `Keep` retires the rule from
  *     both lanes, and one saying it ships a shim stops the row from ever becoming a requirement.
  *     [[ApiRow.by]], the availability FACT, no manifest can contradict.
  *
  * `DependencyCheck.requirements` is the third reader and the one that consumes an [[ArtifactDep]]:
  * the coordinates a finding names are this file's, per platform, because two backends can want
  * different artifacts for one API.
  *
  * WHERE THE ROWS ARE STILL SILENT is the honest residue: a row the survey did not reach carries
  * `UNSTATED` in `asOf` and says so in its `why`, and `catalog(uncited)` counts the registry rows
  * with no Scala-side citation on every run. Neither is hidden inside a number about the port.
  */
object ApiRows:

  private def l(n: Int) = DiffId(L, n)
  private def p(n: Int) = DiffId(P, n)

  /** the anchor for every row derived from the two javalib source trees. */
  val JsNative: Map[String, String] = Map("scala-js" -> "1.22.0", "scala-native" -> "0.5.11")
  private val Unstated               = Map.empty[String, String]
  private val TimeLib                = Map("scala-java-time" -> "2.6.0")
  private val LocaleLib              = Map("scala-java-locales" -> "1.5.4")

  // THE CROSS KIND IS CHECKED AGAINST THE REPOSITORY, not inferred from the organisation. An
  // artifact a `Depend` names is one a NON-JVM backend has to resolve, so the question the row is
  // really answering is "does this exist for scala.js / native", and every one of the four below is
  // published per PLATFORM as well as per Scala version — `scala-java-time_sjs1_3` and
  // `scala-java-time_native0.5_3` exist beside `scala-java-time_3`, `scala-native-crypto` exists at
  // `_native0.5_3` and NOWHERE else. Spelled `%%` (the default this file used to take) the emitted
  // build asks for the JVM jar on a JS build, which for the first three RESOLVES and then fails at
  // link time, and for the fourth resolves nothing at all.
  private val ScalaJavaTime   = ArtifactDep("io.github.cquiroz", "scala-java-time", "2.6.0", CrossKind.Platform)
  private val ScalaJavaTzdb   = ArtifactDep("io.github.cquiroz", "scala-java-time-tzdb", "2.6.0", CrossKind.Platform)
  private val ScalaJavaLocale = ArtifactDep("io.github.cquiroz", "scala-java-locales", "1.5.4", CrossKind.Platform)
  // `scalajs-weakreferences` is published per Scala.js version as well as per Scala version, which
  // is what `%%%` is for and what makes the cross kind a fact about the ARTIFACT rather than about
  // the port that adds it.
  private val WeakRefs        = ArtifactDep("org.scala-js", "scalajs-weakreferences", "1.0.0", CrossKind.Platform)
  private val NativeCrypto    = ArtifactDep("com.github.lolgab", "scala-native-crypto", "0.1.0", CrossKind.Platform)
  // …and the fifth is a coordinate a SCALA 3 port cannot resolve at all: `com.dedipresta` publishes
  // `scala-crypto` for 2.12 and 2.13, JVM and sjs1, and for no Scala 3 at all. The cross kind is
  // right and the ADVICE is not usable as it stands — recorded here and in the two rows that name
  // it rather than silently corrected, because inventing a replacement artifact is the one thing a
  // survey row may not do (CLAUDE.md §4.6: an answer the caller cannot tell from a real one).
  private val CrossCrypto     = ArtifactDep("com.dedipresta", "scala-crypto", "1.0.0", CrossKind.Platform)
  // …and the sixth is the first coordinate this engine's own maintainer publishes (DESIGN.md §8.19):
  // a cross-platform stand-in for a JDK family that EXISTS on all three backends with DIFFERENT
  // mechanics, rather than a third party's fill-in for one that is absent. It is `%%%` for the
  // reason every row above is — `multiarch-serviceloader_sjs1_3` and `_native0.5_3` are published
  // beside `_3` — and it is the first to need a RESOLVER: the artifact is on the Central Portal
  // SNAPSHOT repository and on NO release repository, so a build naming the coordinate and not the
  // repository resolves nothing.
  //
  // THE REVISION IS PROVISIONAL, until multiarch-scala's next release: `0.4.0-12-gc168b2f-SNAPSHOT`
  // is a git-described snapshot of that repository's `master`, and the release that supersedes it
  // will carry a plain version and need no `resolver` at all. Both halves move together when it
  // lands, and the check matches on organisation and NAME and never on the revision, so a port
  // pinning either one is covered.
  private val MultiArchSpi    = ArtifactDep("com.kubuszok", "multiarch-serviceloader", "0.4.0-12-gc168b2f-SNAPSHOT",
    CrossKind.Platform, Some("https://central.sonatype.com/repository/maven-snapshots"))

  private def row(id: DiffId, fqn: String, exact: Boolean,
                  jvm: (Availability, Verdict), js: (Availability, Verdict), nat: (Availability, Verdict),
                  asOf: Map[String, String], why: String): ApiRow =
    ApiRow(id, fqn, exact,
      Map(Platform.Jvm -> jvm._1, Platform.ScalaJs -> js._1, Platform.ScalaNative -> nat._1),
      Map(Platform.Jvm -> jvm._2, Platform.ScalaJs -> js._2, Platform.ScalaNative -> nat._2),
      asOf, why)

  /** all three platforms agree, and there is nothing to do. */
  private def everywhere(id: DiffId, fqn: String, exact: Boolean, why: String, asOf: Map[String, String] = JsNative): ApiRow =
    row(id, fqn, exact, (Full, Keep), (Full, Keep), (Full, Keep), asOf, why)

  /** the same rewrite on every platform — a MAP that exists for a SHAPE reason, not an availability one. */
  private def mapped(id: DiffId, fqn: String, exact: Boolean, target: String,
                     js: Availability, nat: Availability, why: String): ApiRow =
    row(id, fqn, exact, (Full, MapTo(target)), (js, MapTo(target)), (nat, MapTo(target)), JsNative, why)

  // -------------------------------------------------------------------------------------------
  // JS-L — the library surface. Collections and `java.lang` first, then time / text / locale.
  // -------------------------------------------------------------------------------------------

  val library: List[ApiRow] = List(
    mapped(l(1), "java.util.List", false, "scala.collection.mutable.Buffer", Full, Full,
      "no Scala trait has the JDK's mutable ordered index-access shape, so the mapping is a real need and not a redundant one"),
    mapped(l(2), "java.util.ArrayList", true, "scala.collection.mutable.ArrayBuffer", Full, Full,
      "already in the engine's typeMap and semantically aligned with the List row"),
    mapped(l(3), "java.util.LinkedList", true, "scala.collection.mutable.Queue", Full, Full,
      "Queue rather than ListBuffer, chosen to preserve the source's own subtype relations"),
    mapped(l(4), "java.util.Vector", true, "scala.collection.mutable.ArrayBuffer", Absent, Full,
      "Scala.js has no implementation, and the mapping's one loss is the per-method `synchronized`, which no Scala collection has"),
    // NOT `scala.collection.mutable.Stack`, which this survey recommended until the mapping was
    // built: java's `Stack` is a `List` whose top is its LAST element, and scala's `Stack` is an
    // `ArrayDeque` whose `push` prepends — so the two agree on push/pop/peek and DISAGREE on every
    // list-shaped read of the same object, silently. The `Buffer` keeps java's layout and pays for
    // it with five member rewrites, `search` among them.
    mapped(l(5), "java.util.Stack", true, "scala.collection.mutable.ArrayBuffer", Absent, Absent,
      "absent on BOTH non-JVM backends, and mapping to scala's own Stack would INVERT the sequence order every List-shaped read sees"),
    mapped(l(6), "java.util.Map", false, "scala.collection.mutable.Map", Full, Full,
      "no exact Scala counterpart, and java's null-returning `get` has to be rewritten against Scala's Option"),
    mapped(l(7), "java.util.HashMap", true, "scala.collection.mutable.HashMap", Full, Full,
      "ordering and null-key behaviour line up between the two types"),
    mapped(l(8), "java.util.LinkedHashMap", true, "scala.collection.mutable.LinkedHashMap", Full, Full,
      "a direct insertion-order counterpart"),
    mapped(l(9), "java.util.TreeMap", true, "scala.collection.mutable.TreeMap", Full, Full,
      "the mapping exists, with an unaudited delta between java's Comparator constructor and Scala's implicit Ordering"),
    mapped(l(10), "java.util.Map.Entry", true, "scala.Tuple2", Full, Full,
      "getKey/getValue become _1/_2, but Tuple2 is final so an entry-as-PARENT seam is counted and refused rather than emitted"),
    mapped(l(11), "java.util.Set", false, "scala.collection.mutable.Set", Full, Full,
      "no exact Scala counterpart for the JDK interface shape"),
    mapped(l(12), "java.util.HashSet", true, "scala.collection.mutable.HashSet", Full, Full, "a direct counterpart"),
    mapped(l(13), "java.util.LinkedHashSet", true, "scala.collection.mutable.LinkedHashSet", Full, Full, "a direct counterpart"),
    mapped(l(14), "java.util.TreeSet", true, "scala.collection.mutable.TreeSet", Full, Full, "a direct counterpart"),
    mapped(l(15), "java.util.ArrayDeque", true, "scala.collection.mutable.ArrayDeque", Full, Full,
      "the name collides and the semantics match, so the mapping is mechanical"),
    mapped(l(16), "java.util.Deque", false, "scala.collection.mutable.ArrayDeque", Full, Full,
      "the interface maps onto the same concrete Scala type as its JDK implementation"),
    mapped(l(17), "java.util.Queue", false, "scala.collection.mutable.ArrayDeque", Full, Full,
      "mapping to mutable.Queue would INVERT the source's own subtype relation, which was a measured regression"),
    everywhere(l(18), "java.util.PriorityQueue", true,
      "present on all backends, though the null-on-empty poll/peek deltas make it an unexploited mapping opportunity"),
    mapped(l(19), "java.util.Collections", true, "balticporter.runtime.JavaCollections", Full, Full,
      "a per-static rewrite is required because sort mutates in place and an unmodifiable view must stay a view rather than a copy"),
    everywhere(l(20), "java.util.Arrays.", false,
      "everything other than asList is present on all backends with no semantic delta to reproduce"),
    mapped(l(21), "java.util.Arrays#asList", true, "balticporter.runtime.JavaCollections.asList", Full, Full,
      "only the element form is rewritten: the single-array-argument form is a live aliasing view the engine refuses to silently copy"),
    everywhere(l(22), "java.util.Objects", true, "present on all three backends with no engine action needed"),
    everywhere(l(23), "java.util.Optional", true,
      "present everywhere and unexercised by the corpus, so mapping to Option is future work rather than a correctness need"),
    mapped(l(24), "java.util.OptionalInt", true, "scala.Option", Absent, Full,
      "Scala.js ships no specialised Optional classes while Native does, so a uniform mapping keeps one emitted program valid everywhere"),
    mapped(l(25), "java.util.OptionalLong", true, "scala.Option", Absent, Full, "the same JS-only absence as OptionalInt"),
    mapped(l(26), "java.util.OptionalDouble", true, "scala.Option", Absent, Full, "the same JS-only absence as OptionalInt"),
    row(l(27), "java.util.Iterator", false,
      (Full, Shim("balticporter.runtime.JavaIterator")), (Full, Shim("balticporter.runtime.JavaIterator")),
      (Full, Shim("balticporter.runtime.JavaIterator")), JsNative,
      "Scala's Iterator has no `remove`, which ported code uses polymorphically, so mapping to the stdlib type is a silent behaviour loss"),
    everywhere(l(28), "java.util.ListIterator", false,
      "present on both non-JVM backends and unused by the corpus, so a bidirectional shim is low-priority future work"),
    mapped(l(29), "java.util.Comparator", false, "scala.math.Ordering", Full, Full,
      "Ordering EXTENDS Comparator so the target is a java-usable subtype needing no coercion, and the hand-written reference port converged on it independently"),
    everywhere(l(30), "java.util.Random", true,
      "all backends reimplement java's own LCG, so a seeded sequence reproduces identically and `shuffle` must reproduce java's algorithm rather than delegate"),
    everywhere(l(31), "java.util.StringJoiner", true,
      "fully supported everywhere, and mkString is an expression rather than the stateful incremental builder java offers"),
    everywhere(l(32), "java.util.StringTokenizer", true,
      "present on all three, and `split` has no equivalent for its return-delimiters mode"),
    everywhere(l(33), "java.util.BitSet", true, "present on all three and used unshimmed in the hand-written reference port"),
    row(l(34), "java.util.EnumMap", true,
      (Full, Shim("engine runtime")), (Absent, Shim("engine runtime")), (Absent, Shim("engine runtime")), JsNative,
      "absent on both non-JVM backends AND no Scala type reproduces the ordinal-array iteration-order guarantee, so a shim rather than a map is the honest answer"),
    // A SHIM and not a `mapped(…, "scala.collection.mutable.Set", …)`, which is what this row said
    // until the mapping was built. The availability half is right — Native has it, Scala.js does
    // not — and it is not the whole question: `EnumSet` GUARANTEES iteration in ordinal order and a
    // `mutable.Set` does not, so the map would have reproduced the gap and dropped the guarantee,
    // which is `JS-C42` and the failure mode the engine refuses by name. Its sibling row above said
    // `Shim` for the same reason all along; the two are one decision.
    row(l(35), "java.util.EnumSet", true,
      (Full, Shim("engine runtime")), (Absent, Shim("engine runtime")), (Full, Shim("engine runtime")), JsNative,
      "absent on Scala.js, and no Scala set reproduces the ordinal-order iteration guarantee, so a shim rather than a map is the honest answer"),
    everywhere(l(36), "java.util.IdentityHashMap", true,
      "present on all three, with no corpus use and no stdlib reference-identity map to move to"),
    row(l(37), "java.util.WeakHashMap", true,
      (Full, Keep), (Absent, Refuse("JS's WeakMap requires object keys and cannot enumerate")), (Full, Keep), JsNative,
      "no faithful JS target exists, and degrading to a strong-referencing map would silently change GC behaviour"),
    everywhere(l(38), "java.util.function.", false,
      "all the SAM interfaces are present on both non-JVM backends and Scala 3 SAM conversion targets them directly"),
    mapped(l(39), "java.util.stream.", false, "the Scala collection operations, via chain collapse", Absent, Full,
      "Scala.js has no stream package at all, so keeping the JDK type is impossible there and a uniform mapping is the only one-program-all-backends answer"),
    everywhere(l(40), "java.lang.String", true, "it is literally the same type Scala code already uses on every backend"),
    everywhere(l(41), "java.lang.StringBuilder", true,
      "present everywhere, and Scala's own StringBuilder is a DIFFERENT class that must not be conflated with it"),
    everywhere(l(42), "java.lang.StringBuffer", true, "present on all three backends with no rewrite needed"),
    everywhere(l(43), "java.lang.Character", true,
      "the classification surface exists on both non-JVM backends, with only the completeness of the Unicode data tables differing"),
    everywhere(l(44), "java.lang.Integer", true, "the static parse/valueOf/radix surface is implemented identically on both non-JVM backends"),
    everywhere(l(45), "java.lang.Long", true, "the same static surface is present on all three backends"),
    everywhere(l(46), "java.lang.Short", true, "the same static surface is present on all three backends"),
    everywhere(l(47), "java.lang.Byte", true, "the same static surface is present on all three backends"),
    row(l(48), "java.lang.Float", true,
      (Full, Keep), (Partial("toString omits the trailing zero the JVM prints"), Keep), (Full, Keep), JsNative,
      "the class is fully present and the toString departure is a documented intentional Scala.js semantic, not a coverage gap"),
    row(l(49), "java.lang.Double", true,
      (Full, Keep), (Partial("toString omits the trailing zero the JVM prints"), Keep), (Full, Keep), JsNative,
      "the same documented Scala.js departure, which matters only to a port asserting on exact string output"),
    everywhere(l(50), "java.lang.Math", true, "fully covered on both non-JVM backends"),
    row(l(51), "java.lang.StrictMath", true,
      (Full, Keep), (Absent, Refuse("no bit-for-bit-reproducible target")), (Absent, Refuse("no bit-for-bit-reproducible target")), JsNative,
      "neither non-JVM backend has a separate implementation and both alias it to platform float ops, so a port needing strict rounding should be flagged rather than silently aliased"),
    row(l(52), "java.lang.System", true,
      (Full, Keep), (Partial("exit, getProperty and the other environment statics are browser-appropriate stubs"), Keep), (Full, Keep), JsNative,
      "the stubbing follows from the JS runtime itself rather than from any engine gap; the member-level answers are the JS-P rows"),
    row(l(53), "java.lang.Iterable", false,
      (Full, Shim("balticporter.runtime.JavaIterable")), (Full, Shim("balticporter.runtime.JavaIterable")),
      (Full, Shim("balticporter.runtime.JavaIterable")), JsNative,
      "must move in lockstep with the Iterator shim, or every iterator() call expecting removal capability breaks"),
    everywhere(l(54), "java.lang.Comparable", false,
      "present on all three and compareTo is used directly, since Ordered is not a SAM equivalent"),
    everywhere(l(55), "java.lang.CharSequence", false, "structurally identical on every backend"),
    everywhere(l(56), "java.lang.Enum", true,
      "both non-JVM backends implement the values/valueOf/ordinal behaviour a ported java enum still needs, and a Scala 3 enum is ABI-different"),
    everywhere(l(57), "java.lang.Throwable", true, "both non-JVM backends reimplement the whole hierarchy under the same names"),
    everywhere(l(58), "java.lang.AutoCloseable", true, "present on all three and the safe target of a disposable-resource redirect"),
    row(l(59), "java.lang.Class", true,
      (Full, Keep), (Full, Keep),
      (Partial("no Class.scala; only a thin reflect package with no Method/Field/Constructor"), Refuse("no faithful reflection target")),
      JsNative,
      "reflection fidelity cannot be relied on off the JVM, which is why the engine already redirects raw JDK reflection away"),

    // ---- time, text and locale ----
    row(l(60), "java.time.", false,
      (Full, Keep), (Stub("the working classes live only in an unpublished ext-dummies module"), Depend(ScalaJavaTime)),
      (Absent, Depend(ScalaJavaTime)), TimeLib,
      "nothing else in the ecosystem provides the java.time surface off the JVM, and Scala Native's own docs point at this artifact"),
    row(l(61), "java.time.format.", false,
      (Full, Keep), (Absent, Depend(ScalaJavaTime)), (Absent, Depend(ScalaJavaTime)), TimeLib,
      "the formatter builder needs a working Locale beside it, so this row also pulls in the locales dependency"),
    row(l(62), "java.time.temporal.", false,
      (Full, Keep), (Absent, Depend(ScalaJavaTime)), (Absent, Depend(ScalaJavaTime)), TimeLib,
      "covered by the same artifact as the core java.time row at no separate cost"),
    row(l(63), "java.time.ZoneId#of", true,
      (Full, Keep), (Absent, Depend(ScalaJavaTzdb)), (Absent, Depend(ScalaJavaTzdb)), TimeLib,
      "zone lookup is BY STRING so the whole IANA database ships unless the build generates a trimmed one, which makes this a build-time action and not just a library add"),
    everywhere(l(64), "java.time.ZoneOffset#systemDefault", true,
      "not a platform gap at all — a static-inheritance naming question, recorded so a matrix reader does not misfile it; UNSTATED because no version bears on it", Unstated),
    row(l(65), "java.util.Locale", true,
      (Full, Keep), (Stub("a working class exists only in the unpublished ext-dummies module; the default is ROOT"), Depend(ScalaJavaLocale)),
      (Absent, Depend(ScalaJavaLocale)), LocaleLib,
      "the replacement is CLDR-backed at a NEWER CLDR than the JDK's, so locale-derived strings can differ even where the API matches"),
    row(l(66), "java.text.DecimalFormat", true,
      (Full, Keep), (Stub("an ext-dummies placeholder with almost no surface"), Depend(ScalaJavaLocale)),
      (Absent, Depend(ScalaJavaLocale)), LocaleLib,
      "the family including DecimalFormatSymbols and NumberFormat is fully covered, rounding-mode setter included"),
    row(l(67), "java.text.SimpleDateFormat", true,
      (Full, Keep), (Stub("an ext-dummies placeholder"), Depend(ScalaJavaLocale)), (Absent, Depend(ScalaJavaLocale)), LocaleLib,
      "DateFormat and DateFormatSymbols get the same answer from the same artifact"),
    row(l(68), "java.text.MessageFormat", true,
      (Full, Keep), (Absent, Refuse("no implementation anywhere surveyed")), (Absent, Refuse("no implementation anywhere surveyed")), LocaleLib,
      "neither platform javalib nor the locales artifact provides it, so the only path is a hand-written shim over the format subset a library uses"),
    row(l(69), "java.text.Collator", true,
      (Full, Keep), (Absent, Refuse("no cross-platform collation")), (Absent, Refuse("no cross-platform collation")), LocaleLib,
      "locale-aware collation has no Scala answer today, leaving only lexicographic-by-codepoint ordering off the JVM"),
    row(l(70), "java.text.BreakIterator", true,
      (Full, Keep), (Absent, Refuse("no implementation anywhere surveyed")), (Absent, Refuse("no implementation anywhere surveyed")), LocaleLib,
      "the same reasoning as Collator, with no implementation in any surveyed source tree"),
    mapped(l(71), "java.util.TimeZone", true, "java.time.ZoneId", Absent, Absent,
      "the locales artifact ships only Currency under java/util, and a modern caller already carries a ZoneId rather than a TimeZone"),
    row(l(72), "java.util.Calendar", true,
      (Full, Keep), (Absent, Refuse("no implementation anywhere surveyed")), (Absent, Refuse("no implementation anywhere surveyed")), Unstated,
      "no cross-platform implementation exists; UNSTATED because the survey anchored no version to this claim"),
    everywhere(l(73), "java.util.Date", true,
      "the one row in this area where the plain JDK type works on all three platforms with no dependency; UNSTATED — the survey anchored no version", Unstated),
    row(l(74), "java.util.Currency", true,
      (Full, Keep), (Absent, Depend(ScalaJavaLocale)), (Absent, Depend(ScalaJavaLocale)), LocaleLib,
      "provided by the locales artifact's java/util tree"),
    row(l(75), "java.util.Formatter", true,
      (Full, Keep), (Partial("negative-value hex output differs from the JVM"), Keep),
      (Partial("independently ported; conversion coverage is not guaranteed to match JS"), Keep), Unstated,
      "both core javalibs implement it with no dependency, but locale-sensitive numeric conversions still route through Locale; UNSTATED — no version was anchored"),
    row(l(76), "java.util.regex.", false,
      (Full, Keep), (Partial("compiled to the JS RegExp engine, with documented limitations"), Keep),
      (Partial("RE2-backed, and not a drop-in replacement for java.util.regex"), Keep), Unstated,
      "KEEP is right for the portable core, but the two engines subtract DIFFERENT features, so a construct-level check is what a literal pattern needs; UNSTATED — the JS-side construct list was never fetched"),
  )

  // -------------------------------------------------------------------------------------------
  // JS-P — platform capability. Systems-facing JDK APIs, where JS and Native disagree most.
  // -------------------------------------------------------------------------------------------

  val platform: List[ApiRow] = List(
    row(p(1), "java.io.File", false,
      (Full, Keep), (Absent, Shim("a per-backend Files trait")), (Full, Keep), JsNative,
      "covers FileInputStream/FileOutputStream/RandomAccessFile too: browser JS has no filesystem behind the path-string wrapper while Native has real POSIX file I/O"),
    everywhere(p(2), "java.io.DataInputStream", false,
      "covers DataOutputStream, the ByteArray streams and the Reader/Writer family — in-memory streams with no filesystem dependency port cleanly"),
    row(p(3), "java.io.ObjectInputStream", false,
      (Full, Keep), (Absent, Refuse("no bytecode-level serialization machinery on Scala.js")),
      (Partial("present, rarely used"), Keep), JsNative,
      "covers ObjectOutputStream and Serializable; the Native verdict is the survey's default because no corpus library exercises it"),
    row(p(4), "java.io.Console", false,
      (Full, Keep), (Partial("stdout via console.log, no real stdin"), Shim("stdout only")), (Full, Keep), JsNative,
      "output works on JS through console.log but a browser has no real standard input"),
    everywhere(p(5), "java.nio.ByteBuffer", false,
      "covers the whole Byte/Char/Double/Float/Int/Long/Short buffer family — TypedArray-backed on JS, Ptr-backed on Native"),
    everywhere(p(6), "java.nio.charset.", false, "real, non-dummy implementations including the per-charset classes exist in both core javalibs"),
    row(p(7), "java.nio.channels.", false,
      (Full, Keep), (Absent, Refuse("the package does not exist in the Scala.js javalib")),
      (Partial("FileChannel and FileLock are real; the socket channels are not"), Keep), JsNative,
      "Native needs a MEMBER-level split that a single prefix rule cannot express, which is what JS-P08 and JS-P09 are"),
    row(p(8), "java.nio.channels.SocketChannel", true,
      (Full, Keep), (Absent, Refuse("no java.nio.channels package at all")), (Absent, Refuse("verified absent from the Native javalib")), JsNative,
      "the half of java.nio.channels the prefix rule gets wrong on Native, where FileChannel is real"),
    row(p(9), "java.nio.channels.ServerSocketChannel", true,
      (Full, Keep), (Absent, Refuse("no java.nio.channels package at all")), (Absent, Refuse("verified absent from the Native javalib")), JsNative,
      "the same split as SocketChannel, listed separately because a member rule is what the check matches on"),
    row(p(10), "java.nio.file.", false,
      (Full, Keep), (Absent, Refuse("the package does not exist in the Scala.js javalib")), (Full, Keep), JsNative,
      "the widest JS-vs-Native gap in the survey by API surface — totally absent on JS, and richer than expected on Native"),
    row(p(11), "java.net.Socket", false,
      (Full, Keep), (Absent, Refuse("a browser sandbox has no raw sockets")), (Full, Keep), JsNative,
      "covers ServerSocket; the reference port's own capability matrix marks both supported on desktop JVM and Native and unsupported in the browser"),
    row(p(12), "java.net.URI", false,
      (Full, Keep), (Partial("URI string parsing works; URLConnection/HttpURLConnection are effectively unusable"), MapTo("fetch or XHR for the connection half")),
      (Full, Keep), JsNative,
      "covers URL and URLConnection — pure string parsing ports, and the HTTP half does not without a browser-native call underneath"),
    row(p(13), "java.net.InetAddress", false,
      (Full, Keep), (Partial("DNS resolution is a JS-runtime concern, not exposed with the JVM's shape"), MapTo("the JS runtime's own DNS facility")),
      (Full, Keep), JsNative,
      "name resolution is not exposed with JVM shape on Scala.js while Native resolves for real"),
    row(p(14), "java.net.IDN", false,
      (Full, Keep), (Partial("the class exists and is effectively unusable without ICU-like tables"), Refuse("needs ICU-like tables")),
      (Partial("the class exists and is effectively unusable without ICU-like tables"), Refuse("needs ICU-like tables")), JsNative,
      "a hand-written RFC 3492 Punycode replacement is what a reference port shipped, which is direct evidence the JDK class is not viable cross-platform even though it exists"),
    row(p(15), "java.util.concurrent.ExecutorService", false,
      (Full, Keep), (Absent, MapTo("a per-backend concurrency trait over ExecutionContext")), (Full, Keep), JsNative,
      "covers Executors/ThreadPoolExecutor/ScheduledThreadPoolExecutor/ForkJoinPool, all real JSR-166 ports on Native and none of them on JS"),
    row(p(16), "java.util.concurrent.CompletableFuture", false,
      (Full, Keep), (Partial("only Semaphore exists, and only because acquire never really blocks"), MapTo("a per-backend concurrency trait over the JS microtask queue")),
      (Full, Keep), JsNative,
      "covers CountDownLatch/CyclicBarrier/Semaphore: all four are real on Native and only one is on JS"),
    everywhere(p(17), "java.util.concurrent.ConcurrentHashMap", false,
      "covers ConcurrentLinkedQueue/ConcurrentSkipListSet/CopyOnWriteArrayList — degenerate but correct on JS, real JSR-166 ports on Native"),
    everywhere(p(18), "java.util.concurrent.atomic.", false,
      "covers AtomicInteger/AtomicReference/the field updaters/LongAdder — trivially correct on a single-threaded backend and real on Native"),
    everywhere(p(19), "java.util.concurrent.locks.", false,
      "covers ReentrantLock/ReentrantReadWriteLock/Condition — a lock that never contends is still a correct lock"),
    row(p(20), "java.lang.Thread", true,
      (Full, Keep), (Stub("a private-ctor singleton: getId is hardcoded, run() is empty, currentThread() is always the same instance"),
      MapTo("a per-backend concurrency trait whose yield is a documented no-op")), (Full, Keep), JsNative,
      "you cannot construct a second thread on Scala.js and `new Thread(r)` is a compile error there, while Native has real OS threads"),
    row(p(21), "java.lang.ProcessBuilder", false,
      (Full, Keep), (Absent, Refuse("no process spawning in a browser")), (Full, Keep), JsNative,
      "covers Process and ProcessHandle, all three real on Native — the old no-ProcessBuilder folklore is closed and shipped"),
    row(p(22), "java.lang.Runtime", false,
      (Full, Keep), (Stub("present but reduced; no real process control in a browser"), Shim("a reduced Runtime")), (Full, Keep), JsNative,
      "Native's OS-specific extension is stronger than JVM-parity claims usually assume, while JS has no process control to expose"),
    row(p(23), "java.lang.Class#getName", true,
      (Full, Keep), (Partial("works for any Scala.js object; general reflection is explicitly out"), Keep),
      (Partial("works, and is needed for exception messages; full introspection does not"), Keep), JsNative,
      "covers getSimpleName and the isInstance-style checks — the narrow name-and-identity slice of Class survives on both platforms"),
    row(p(24), "java.lang.reflect.", false,
      (Full, Keep), (Absent, Refuse("no reflective dispatch; it breaks the Scala.js linker")),
      (Stub("Array/AccessibleObject/Executable and the exception types are present; Method/Field/Constructor dispatch is not"),
      Refuse("no reflective dispatch; it breaks the Scala Native link")), JsNative,
      "covers Class.forName, Method.invoke, the Field accessors and Constructor.newInstance; the Native link failure was measured directly and fixed by per-platform substitution"),
    // THE AVAILABILITY HALVES STILL DISAGREE and the VERDICTS no longer do — which is what a
    // cross-platform wrapper IS (DESIGN.md §8.19). JS has no class to reference; Native has a real
    // one whose `load` is a LINK-TIME INTRINSIC accepting only a literal `classOf`, so no
    // `Class`-taking API can delegate to it and `nativeConfig.withServiceProviders` enlistment
    // serves only direct `load(classOf[Concrete])` sites. That measured correction is why the Native
    // verdict is no longer `MapTo("link-time provider enlistment")`: the enlistment is real and it
    // is not reachable from a ported library's generic lookup.
    row(p(25), "java.util.ServiceLoader", false,
      (Full, Keep), (Absent, Depend(MultiArchSpi)),
      (Partial("real, but `load` is a LINK-TIME intrinsic that only accepts a literal classOf, so no Class-taking wrapper can call it"), Depend(MultiArchSpi)), JsNative,
      "the two platforms need DIFFERENT verdicts about what EXISTS and the same one about what to do — one artifact answers both, and a Native-only port still learns that its gap is narrower; and the artifact answers the TYPES only, because off the JVM there is no classpath scan and the wrapper's registration is an object body a ported library never forces, which is COUNTED as `service-providers(off-jvm-unwired)` rather than closed (ENGINE-LIMITS.md P9)"),
    row(p(26), "java.security.MessageDigest", false,
      (Full, Keep), (Absent, Depend(CrossCrypto)), (Absent, Depend(NativeCrypto)), JsNative,
      "the exception types exist so catch sites still compile while nothing throws them from a working digest, and both platforms push to a third-party library — but the JS coordinate is published for SCALA 2.12/2.13 ONLY, so a Scala 3 port taking that half of the advice resolves nothing and needs another artifact or an override"),
    row(p(27), "java.security.SecureRandom", false,
      (Full, Keep), (Absent, Depend(CrossCrypto)), (Absent, Keep), Unstated,
      "UNSTATED for Native — the survey did not confirm its absence, so the rule should be added only once a corpus library is found using it; the JS coordinate carries p(26)'s caveat, being published for Scala 2 only"),
    row(p(28), "java.util.zip.", false,
      (Full, Keep), (Absent, Refuse("no zlib in a browser without a JS dependency")), (Full, Keep), JsNative,
      "covers ZipFile/ZipInputStream/Deflater/Inflater/CRC32/GZIP — actively maintained on Native and absent on JS"),
    row(p(29), "javax.", false,
      (Full, Keep), (Absent, Refuse("the javax.* stack is JVM-only")), (Absent, Refuse("the javax.* stack is JVM-only")), JsNative,
      "javax.crypto, javax.net.ssl and javax.swing are JVM-only on both, and the survey found no evidence contradicting that"),
    row(p(30), "java.lang.Object#finalize", true,
      (Partial("deprecated but functional"), Keep), (Stub("never invoked; no GC hook is exposed the same way"), Refuse("finalizers are never invoked on Scala.js")),
      (Partial("present via the GC; the timing is not equivalent"), Refuse("present but not timing-equivalent to the JVM")), JsNative,
      "neither platform reproduces JVM finalizer timing, and JS does not run them at all"),
    row(p(31), "java.lang.ref.WeakReference", false,
      (Full, Keep), (Absent, Depend(WeakRefs)), (Full, Keep), JsNative,
      "covers ReferenceQueue; the JS answer is a BUILD-GRAPH fact a symbol-reference rule cannot see, which is why it needs a finding kind of its own"),
    row(p(32), "java.lang.ref.Cleaner", false,
      (Full, Keep), (Absent, Depend(WeakRefs)), (Full, Keep), Unstated,
      "UNSTATED on both non-JVM platforms — the survey did not reach this class and the verdicts are inherited from WeakReference by expectation, not by measurement"),
    row(p(33), "java.lang.System#getProperty", true,
      (Full, Keep), (Partial("a link-time-configured properties table, never the live host's"), MapTo("link-time-configured values")),
      (Full, Keep), Map("scala-js" -> "1.22.0"),
      "it compiles and RUNS on Scala.js returning whatever the build configured, so the JVM-only phrasing an existing rule uses is not quite true; UNSTATED for Native"),
    row(p(34), "java.lang.System#getenv", true,
      (Full, Keep), (Stub("getenv() is always an empty map and getenv(name) is always null"), Refuse("always empty, so a read silently returns nothing")),
      (Full, Keep), JsNative,
      "Native implemented it for real while JS returns empty unconditionally — the OPPOSITE shape from getProperty, which a single conflated rule hides"),
  )

  val all: List[ApiRow] = library ++ platform

  val byId: Map[DiffId, ApiRow] = all.map(r => r.id -> r).toMap
