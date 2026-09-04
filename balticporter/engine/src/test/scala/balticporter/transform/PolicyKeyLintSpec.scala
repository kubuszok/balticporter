package balticporter.transform

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** THE LINT — no phase reconstructs member identity from a STRING. */
class PolicyKeyLintSpec extends munit.FunSuite:

  /** the engine's own `src/main/scala`, written into the test resources by build.sbt so the check
    * does not depend on where the suite is run from (the same device `RuntimeArtifactSpec` uses). */
  private val transformDir: Path =
    val is = Option(getClass.getClassLoader.getResourceAsStream("balticporter/engine-source-dir.txt"))
      .getOrElse(fail("balticporter/engine-source-dir.txt is missing — engine's Test resourceGenerator did not run"))
    val s = try new String(is.readAllBytes(), "UTF-8").trim finally is.close()
    Path.of(s).resolve("balticporter/transform")

  /** ONE forbidden shape: the NAME an allow-list entry cites, the test, and what to reach for. */
  private final case class Shape(name: String, hits: String => Boolean, instead: String)

  /** a `$`-interpolation IMMEDIATELY BESIDE a `#` — `s"${owner}#$name"`, `s"$owner#$name"`,
    * `s"$owner#${name}"`. Adjacency is the whole test: prose that merely mentions `owner#member`
    * beside an interpolated value is not building a key, and a `#` used as some other separator
    * (`PortMapTransform`'s fingerprint) is allow-listed by name rather than by weakening this. */
  private val Interpolator = raw"""(^|[^A-Za-z0-9_])(s|f|raw)\"""".r
  private val HashAdjacent = raw"""(#\s*\$$)|(\}#)|(\$$[A-Za-z_][A-Za-z0-9_]*#)""".r

  /** the forbidden shapes, each with what a phase should reach for instead. */
  private val Forbidden: List[Shape] = List(
    Shape("fullName ==", _.contains("fullName =="),
      "compare SYMBOLS (`SymId`), or bind the name through `PolicyBinder` — a name is not a " +
        "structural fact about anything (§4.56)"),
    Shape("fullName.startsWith", _.contains("fullName.startsWith"),
      "a prefix that does not cut at a separator matches `com.foobar` from `com.foo`; use " +
        "`RuleScope.covers`, which is the one implementation of that rule"),
    Shape("""+ "#" +""", _.contains("""+ "#" +"""),
      "a member key rebuilt from `owner` and `name` carries no parameter list, so it is the same " +
        "string for every overload; ask `PolicyBinder`, or render a `MemberKey`"),
    Shape("interpolated #",
      s => Interpolator.findFirstIn(s).isDefined && HashAdjacent.findFirstIn(s).isDefined,
      "the same rebuilt key one spelling along — `s\"$owner#$name\"` is `owner + \"#\" + name`. " +
        "Render a `MemberKey`, which is the one place that grammar is written"),
  )

  /** file → the shapes it is allowed to use, and WHY. One line each, or it is a site to fix. */
  private val AllowList: Map[String, Map[String, String]] = Map(
    "SuppressionPhase.scala" -> Map(
      "fullName ==" ->
        ("`scala.annotation.nowarn` is a WELL-KNOWN external annotation type the program never " +
          "declares; this is a find-or-create for the annotation's SymId and a filter for members " +
          "that already carry it — the same mint-or-reuse question MemberRenameTransform's " +
          "`targetName` asks"),
    ),
    "UnusedSymbolTransform.scala" -> Map(
      "fullName ==" ->
        ("`scala.annotation.nowarn` lookup — the same find-or-create as SuppressionPhase: a " +
          "WELL-KNOWN external annotation type, never a program-declared symbol"),
    ),
    "TypeRedirectTransform.scala" -> Map(
      "fullName.startsWith" ->
        ("renaming a twinned member's own `fullName` by replacing its owner's prefix, carrying the " +
          "`#`/`$` separators across verbatim (§4.56) rather than re-deriving them"),
    ),
    "MemberRenameTransform.scala" -> Map(
      "fullName ==" ->
        ("`scala.annotation.targetName` is a WELL-KNOWN external annotation type the program never " +
          "declares; this is a find-or-create for the annotation's TypeRef, which only has a fullName " +
          "to key on — the same mint-or-reuse question as PublicFieldAccessorTransform's `Object`"),
    ),
    "PackageRenameTransform.scala" -> Map(
      "fullName.startsWith" ->
        ("this phase IS the prefix rule §4.56 is about; it owns the separator cut and is specced " +
          "against it"),
      "fullName ==" -> "the same phase, reading back what it rewrote",
    ),
    "CollectionsTransform.scala" -> Map(
      "fullName ==" ->
        ("the JDK side of this phase is a TYPE MAPPING keyed by FQN, which is what the map IS — the " +
          "declarations it selects go through `RuleScope` and `Program.owned`"),
      "interpolated #" ->
        ("the `#` in `retargetRewriteSyms` and `retargetRewritesDigest` separates a SOURCE FQN from " +
          "a rewrite-target name or a member/arity — it names no member, it is a synthetic symbol " +
          "namespace for minted SymIds and a fingerprint rendering format"),
    ),
    // CollectionsRetarget/Reified/Boundary split out of CollectionsTransform (context diet S3):
    // same phase, same sites, same reasoning as the `CollectionsTransform.scala` entry above —
    // each `fullName ==` below is a mint-or-reuse lookup against a WELL-KNOWN external FQN
    // (`java.util.Iterator`, `balticporter.runtime.JavaIterator`, `scala.Array`, `java.lang.Object`,
    // the phase's own `StreamFqn`, `scala.collection.
    "CollectionsRetarget.scala" -> Map(
      "fullName ==" ->
        ("the JDK side of this phase is a TYPE MAPPING keyed by FQN — mint-or-reuse against " +
          "`java.util.Iterator`/`balticporter.runtime.JavaIterator`/`scala.Array`, externals the " +
          "program never declares, exactly as in `CollectionsTransform.scala`"),
    ),
    "CollectionsReified.scala" -> Map(
      "fullName ==" ->
        ("`java.lang.Object` mint-or-reuse — the root every java class inherits and no program " +
          "declares, the same question `SamLambda.scala` and `PublicFieldAccessorTransform.scala` " +
          "ask of it"),
    ),
    "CollectionsBoundary.scala" -> Map(
      "fullName ==" ->
        ("mint-or-reuse against `java.lang.Object`, the phase's own `StreamFqn`, and " +
          "`scala.collection.Iterator` — externals the program never declares, plus a bridged-" +
          "target FQN compared to the CANDIDATE's own `fullName`, not to a program-declared name"),
    ),
    "PublicFieldAccessorTransform.scala" -> Map(
      "fullName ==" ->
        ("`java.lang.Object` (the one type whose name says NOTHING about a value, which is why the " +
          "getter bridges at that field and nowhere else) and `scala.Unit` (a java setter returns " +
          "`void`) — both externals the program never declares, so this is mint-or-reuse and there " +
          "is no symbol to bind"),
    ),
    "StaticForwarderTransform.scala" -> Map(
      "fullName ==" ->
        ("the forwarder RECEIVER is a type this program need not declare (it is the JDK's, normally), " +
          "so this is the same mint-or-reuse question `TypeRedirectTransform` asks about its target"),
    ),
    "PortMapTransform.scala" -> Map(
      "interpolated #" ->
        ("the `#` in `surfaceFingerprint` separates a map's SOURCE FINGERPRINT from its entry " +
          "count — it names no member, and rendering a `MemberKey` there would be a lie about " +
          "what the string is"),
      "fullName ==" ->
        ("a published port map's `upstream` column is a string in the EMITTED namespace, joined " +
          "against a key rebuilt the same way (ENGINE-LIMITS D1); both ends of that join move " +
          "together or neither does"),
    ),
    "PrimitiveToOpaqueTransform.scala" -> Map(
      "fullName ==" ->
        ("the opaque type's UNDERLYING is a primitive — `scala.Int` — which is an external this " +
          "program never declares, so this is mint-or-reuse and there is no symbol to bind"),
      "fullName.startsWith" ->
        ("`scala.<op>#` is the ENGINE's own synthetic operator namespace, minted by the frontend " +
          "under exactly that key; it is engine identity, never a policy key"),
    ),
    "TestFrameworkTransform.scala" -> Map(
      // NO `fullName ==` entry any more. The JUnit FQNs this phase knows are still §1(a) universal
      // knowledge about a test framework — but there are THREE assertion classes, so the question
      // became set membership (`AssertClasses`) and the equality spelling left the file. An
      // exemption for a shape nobody uses is a rule nobody is following, which is what this spec's
      // second half exists to say.
      "fullName.startsWith" ->
        ("`scala.<op>#` again — the engine's synthetic operator namespace, not a policy key"),
    ),
    "BeanPropertyTransform.scala" -> Map(
      "fullName ==" ->
        ("`scala.Unit` is the ENGINE's own rendering of java `void` — an external this program never " +
          "declares and has no symbol to bind, so \"did the frontend write void here\" has no other " +
          "instrument. The phase's KEYS all go through `PolicyBinder`."),
    ),
    "NullaryArityTransform.scala" -> Map(
      "fullName ==" ->
        ("`scala.Unit` is the ENGINE's own rendering of java `void` — an external this program never " +
          "declares and has no symbol to bind, so \"did the frontend write void here\" has no other " +
          "instrument. Same rationale as `BeanPropertyTransform`."),
    ),
    "NullabilityTransform.scala" -> Map(
      "fullName ==" ->
        ("`scala.Null`, the engine's synthetic operator namespace and the CONFIGURED wrapper are " +
          "types this program never declares, so there is no symbol to bind — the same mint-or-reuse " +
          "question `PrimitiveToOpaqueTransform` asks about its underlying primitive"),
    ),
    "SamLambda.scala" -> Map(
      "fullName ==" ->
        ("`java.lang.Object` is the root EVERY java class inherits and no program declares, so " +
          "there is no symbol to bind — mint-or-reuse, exactly as `PrimitiveToOpaqueTransform` " +
          "asks it of `scala.Int`. It is also load-bearing rather than incidental: guard 4 has to " +
          "know which members a BARE reference inside an anonymous body would re-resolve under a " +
          "lambda, and those are precisely the ones java bound to the ANON through this root. The " +
          "phase has no policy keys at all — it is §1(a) and takes no parameters"),
    ),
    "ClassTableTransform.scala" -> Map(
      "fullName ==" ->
        ("the redirect DESTINATION is an injected table this program never parses — mint or reuse, " +
          "as above. Its KEYS are bound."),
    ),
    "ClassToTraitTransform.scala" -> Map(
      "fullName ==" ->
        ("the nominated type FQN from `specs` is a CONFIG PARAMETER (§1(b)) the port hands in; " +
          "the program-level lookup matches it against every symbol once, and the `java.lang.Object` " +
          "check is the same mint-or-reuse as PublicFieldAccessorTransform — an external the program " +
          "never declares"),
      "interpolated #" ->
        ("the `#` in MINTED symbol fullNames separates owner from member name — the same " +
          "synthetic namespace the engine builds for every phase-created symbol, not a policy key"),
    ),
  )

  private def sources: List[(String, String)] =
    assert(Files.isDirectory(transformDir), s"$transformDir is not a directory")
    Files.list(transformDir).iterator().asScala.toList
      .filter(_.getFileName.toString.endsWith(".scala"))
      .sortBy(_.getFileName.toString)
      .map(p => p.getFileName.toString -> Files.readString(p))

  /** a `//`-comment or a scaladoc line is prose, not code — the rule is about what RUNS. */
  private def codeLines(src: String): List[(Int, String)] =
    src.linesIterator.zipWithIndex.map((l, i) => (i + 1, l)).toList
      .filterNot((_, l) => { val t = l.trim; t.startsWith("//") || t.startsWith("*") || t.startsWith("/*") })

  test("no phase reconstructs member identity from a STRING — the §8.1 convention, enforced") {
    assert(sources.nonEmpty, "the transform package has no sources — this lint proves nothing")
    val violations = for
      (file, src)  <- sources
      shape        <- Forbidden
      if !AllowList.get(file).exists(_.contains(shape.name))
      (line, text) <- codeLines(src)
      if shape.hits(text)
    yield s"$file:$line  `${shape.name}`  — ${shape.instead}\n    ${text.trim}"
    assertEquals(violations, Nil,
      s"\n${violations.size} forbidden string test(s) in the transform package:\n" +
        violations.mkString("\n") +
        "\n\nIf the site is genuinely right, add it to `AllowList` WITH ITS REASON — one line that " +
        "says why a name is the correct instrument there.")
  }

  test("every ALLOW-LIST entry is still LIVE — a stale exemption is a rule nobody is following") {
    // The half that rots. An allow-list entry whose site was fixed or deleted goes on permitting
    // something nobody does, and the next real violation lands under it in silence.
    val bySource = sources.toMap
    val byName   = Forbidden.map(s => s.name -> s).toMap
    val stale = for
      (file, shapes) <- AllowList.toList.sortBy(_._1)
      (shape, _)     <- shapes.toList.sortBy(_._1)
      src = bySource.getOrElse(file, fail(s"allow-list names $file, which the transform package does not have"))
      test = byName.getOrElse(shape, fail(s"$file allows `$shape`, which is not a forbidden shape")).hits
      if !codeLines(src).exists((_, t) => test(t))
    yield s"$file allows `$shape` and no longer uses it"
    assertEquals(stale, Nil, s"\nstale allow-list entries:\n${stale.mkString("\n")}")
  }

  test("the allow-list is a set of REASONS, not a count") {
    AllowList.foreach { (file, shapes) =>
      shapes.foreach { (shape, why) =>
        assert(why.length > 40, s"$file / $shape: the reason is too short to be one ($why)")
      }
    }
  }
