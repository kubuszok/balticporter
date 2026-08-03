package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.testkit.PortSuite
import balticporter.tir.{Decision, DecisionLog, Pipeline, Program}
import balticporter.transform.CollectionsTransform

/** The THIRD reified position — a generic type ARGUMENT a third party reads out of the class file's
  * generic signature and CONSTRUCTS from (`ENGINE-LIMITS.md` K20).
  *
  * K18's two reified positions are written in the java: `x instanceof T` and `(T) x`. This one is
  * written nowhere. The occurrence is a type argument in a declaration, so a phase walking every
  * `InstanceOf` and every `Typed` visits nothing; there is no value, so no coercion has anywhere to
  * go; and no slot disagrees with another, so no boundary count fires. The port compiles, every
  * check count stays flat, and the evidence is an exception thrown from inside someone else's
  * library — `Cannot construct instance of scala.collection.mutable.Map`. This suite is that
  * evidence at the size a spec can hold.
  *
  * What is asserted, and the last four are what a first cut gets wrong:
  *
  *   1. a declared carrier's type argument stays in JAVA's namespace, at every position it occurs —
  *      the field's type, the anonymous subclass's parent, a method's parameter and result;
  *   2. the declaration AROUND it keeps the mapping. That is the whole point of a per-argument list
  *      and is what neither of K20's two nearby answers does (a `RuleScope` exclusion holds the
  *      whole declaration back; a producer wrap leaves the argument retyped);
  *   3. the value is BRIDGED at the use, by the external-producer seam and not by new machinery;
  *   4. an argument that mentions NO mapped type decides nothing and records nothing;
  *   5. an UNDECLARED carrier is not preserved — the list is policy, and a phase that guessed from
  *      the shape of a name would be §4.56's failure;
  *   6. `java.lang.Class` needs no entry: java reifies it itself, so the engine carries it;
  *   7. the preservation is RECORDED, with the §1 classification a reader needs to know which
  *      repository the fix lives in — `Configured` for a declared carrier, `Universal` for Class.
  */
class CollectionsCarrierSpec extends PortSuite:

  private val Carrier = "com.fasterxml.jackson.core.type.TypeReference"

  /** …as the emitter SPELLS it. `type` is a scala keyword, so the package segment is backticked —
    * a reminder that an assertion over emitted text is an assertion about the emitter's rendering
    * and not about the FQN a manifest writes. */
  private val Emitted = "com.fasterxml.jackson.core.`type`.TypeReference"

  /** …with the run's DECISION LOG, because `Pipeline` drains a phase's buffer into it (a phase
    * instance re-run with a second phase list must not report the first run's rows) — and the
    * emitter renders porter notes from that same log, which is the pairing `NoteCoverageCheck`
    * holds a real run to. */
  private def ported(source: String, carriers: Set[String] = Set(Carrier))
      : (DecisionLog, Program, String) =
    val ph            = new CollectionsTransform(reifiedCarriers = carriers)
    val (after, notes) = Pipeline.runTraced(SpoonTir.fromSource(source), List(ph))
    (notes, after, new TirEmitter(after, notes = notes).emit)

  /** liqp's `LiquidSupport.LiquidSupportFromInspectable`, cut down to the shape that fails. */
  private val JacksonShape =
    """package demo;
      |import java.util.Map;
      |import com.fasterxml.jackson.core.type.TypeReference;
      |import com.fasterxml.jackson.databind.ObjectMapper;
      |class T {
      |  public static final TypeReference<Map<String, Object>> MAP_TYPE_REF =
      |      new TypeReference<Map<String, Object>>() {};
      |  static Map<String, Object> toMap(ObjectMapper mapper, Object value) {
      |    Map<String, Object> converted = mapper.convertValue(value, MAP_TYPE_REF);
      |    return converted;
      |  }
      |}
      |""".stripMargin

  // -------------------------------------------------------------------------
  // 1-2. the argument stays java's; everything around it still moves
  // -------------------------------------------------------------------------

  test("a declared carrier's type ARGUMENT stays in java's namespace — field type and anon parent") {
    val (_, _, out) = ported(JacksonShape)
    assert(clue(out).contains(s"$Emitted[java.util.Map[java.lang.String, java.lang.Object]]"),
           "the argument jackson reads back out of the generic signature must name java's own type")
    assert(!out.contains(s"$Emitted[scala.collection.mutable.Map"),
           "retyped there, the port asks jackson to construct a trait — 0 compile errors, 10 failures")
    // the anonymous subclass is a SECOND position and is the one that carries the signature at run
    // time: `new TypeReference<…>() {}` is what jackson reflects over.
    assertEquals(clue(out).sliding(s"$Emitted[java.util.Map".length)
                   .count(_ == s"$Emitted[java.util.Map"), 2,
                 "the field's declared type and the `new` whose anonymous subclass CARRIES the\n                   signature jackson reflects over")
  }

  test("…and the declaration AROUND the carrier keeps the mapping") {
    val (_, _, out) = ported(JacksonShape)
    assert(clue(out).contains("scala.collection.mutable.Map[java.lang.String, java.lang.Object]"),
           "a per-ARGUMENT list is exactly what a RuleScope exclusion cannot express (K16)")
    assert(out.contains("def toMap(") && out.contains("): scala.collection.mutable.Map["),
           "the method's own result is a slot, and a slot is retyped")
  }

  // -------------------------------------------------------------------------
  // 3. the bridge at the USE
  // -------------------------------------------------------------------------

  test("the value is BRIDGED where the carrier is used — the external-producer seam, not new machinery") {
    val (_, _, out) = ported(JacksonShape)
    assert(clue(out).contains("balticporter.runtime.JavaCollections.fromJava(mapper.convertValue("),
           "the call now really returns java's map while the slot claims scala's; a live view " +
             "bridges it, and `asScala` writes through so the caller's mutations are not lost")
  }

  test("…and with NO carrier declared the same source is the defect K20 measured") {
    val (_, _, out) = ported(JacksonShape, carriers = Set.empty)
    assert(clue(out).contains(s"$Emitted[scala.collection.mutable.Map"),
           "the pre-K20 behaviour, by the same code path: an empty list is a no-op")
    assert(!out.contains("JavaCollections.fromJava(mapper.convertValue("),
           "and nothing bridges, because the result type OCCURS in the carrier argument and the " +
             "call therefore reads as a generic pass-through")
  }

  // -------------------------------------------------------------------------
  // 4-6. what is NOT preserved
  // -------------------------------------------------------------------------

  test("an argument mentioning no mapped type decides nothing and records nothing") {
    val (log, _, out) = ported(
      """package demo;
        |import com.fasterxml.jackson.core.type.TypeReference;
        |class T {
        |  static final TypeReference<String> S = new TypeReference<String>() {};
        |}
        |""".stripMargin)
    assert(clue(out).contains(s"$Emitted[java.lang.String]"))
    assertEquals(log.all.count(_.kind == Decision.Kind.ReifiedTypeArg), 0,
                 "preserving a `String` decided nothing; a row for it would be noise in every port")
  }

  test("an UNDECLARED carrier is retyped — the list is POLICY, never a guess from a name") {
    val (_, _, out) = ported(
      """package demo;
        |import java.util.Map;
        |class Token<T> {}
        |class T {
        |  static final Token<Map<String, Object>> T1 = new Token<Map<String, Object>>() {};
        |}
        |""".stripMargin)
    assert(clue(out).contains("Token[scala.collection.mutable.Map[java.lang.String, java.lang.Object]]"),
           "a phase concluding `this looks like a super-type token` from a name is §4.56's failure")
  }

  test("java.lang.Class is a carrier the ENGINE carries — no port declares it") {
    val (log, _, out) = ported(
      """package demo;
        |import java.util.Map;
        |class T {
        |  Class<Map<String, Object>> held;
        |}
        |""".stripMargin, carriers = Set.empty)
    assert(clue(out).contains("java.lang.Class[java.util.Map[java.lang.String, java.lang.Object]]"),
           "`Class<T>`'s argument names the class the JVM will be asked for, in every codebase")
    assertEquals(log.all.filter(_.kind == Decision.Kind.ReifiedTypeArg)
                   .map(_.reason.className).distinct, List("universal"),
                 "a port cannot turn this one off and must not be sent to its manifest to try")
  }

  // -------------------------------------------------------------------------
  // 7. the record
  // -------------------------------------------------------------------------

  test("the preservation is RECORDED per declaration, with the entry an agent would edit") {
    val (log, _, out) = ported(JacksonShape)
    val rows = log.all.filter(_.kind == Decision.Kind.ReifiedTypeArg)
    assert(clue(rows.map(_.render)).nonEmpty)
    assert(rows.exists(_.subjectFqn.endsWith("MAP_TYPE_REF")),
           "the field is the declaration whose emitted type a reader is looking at")
    assertEquals(rows.map(_.reason.className).distinct, List("configured"))
    assert(rows.forall(_.reason.detail.contains(Carrier)),
           "the key is the manifest entry VERBATIM — it is the string an agent edits (§4.575)")
    assert(clue(out).contains("/* porter: reified-type-arg reason=configured"),
           "the question is asked at a line of Scala, so the answer is emitted beside it (§4.575)")
  }
