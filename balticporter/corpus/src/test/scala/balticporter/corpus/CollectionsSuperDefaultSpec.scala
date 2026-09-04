package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.testkit.PortSuite
import balticporter.tir.{Decision, Pipeline, PorterNote, Program, Reason}
import balticporter.transform.CollectionsTransform

/** A `super.<JDK DEFAULT>` ON A CLASS THE PHASE RE-PARENTED — `ENGINE-LIMITS.md` K29, and
  * `CLAUDE.md` §1's *an obligation the engine's own translation created*. */
class CollectionsSuperDefaultSpec extends PortSuite:

  /** `Fast` is the positive — a class the mapping re-parents onto `mutable.ArrayBuffer`, calling all
    * four defaults through `super` exactly as `java.util.AbstractCollection`'s own subclasses do. */
  private val src =
    """package demo;
      |import java.util.*;
      |public class Fast extends java.util.ArrayList<String> {
      |  public boolean containsAll(Collection<?> c) { return super.containsAll(c); }
      |  public boolean addAll(Collection<? extends String> c) { return super.addAll(c); }
      |  public boolean removeAll(Collection<?> c) { return super.removeAll(c); }
      |  public boolean retainAll(Collection<?> c) { return super.retainAll(c); }
      |  public List<String> subList(int a, int b) { return super.subList(a, b); }
      |}
      |""".stripMargin

  private def emitted(text: String): String =
    new TirEmitter(Pipeline.run(SpoonTir.fromSource(text), List(new CollectionsTransform()))).emit

  // -------------------------------------------------------------------------
  // the positive
  // -------------------------------------------------------------------------

  test("all FOUR JDK bulk defaults stand on `this` through the helper that reproduces them") {
    val out = emitted(src)
    assert(out.contains("balticporter.runtime.JavaCollections.containsAll(this, c)"), out)
    assert(out.contains("balticporter.runtime.JavaCollections.addAll(this, c)"), out)
    assert(out.contains("balticporter.runtime.JavaCollections.removeAll(this, c)"), out)
    assert(out.contains("balticporter.runtime.JavaCollections.retainAll(this, c)"), out)
    // …and NOT the refusal that stood here before: `super.containsAll` names a member
    // `mutable.ArrayBuffer` does not have, and `super.addAll` names one that answers `this.type`
    // where java answered `boolean` — both silent in a spec that never compiles the fixture.
    assert(!out.contains("super.containsAll("), out)
    assert(!out.contains("super.removeAll("), out)
    assert(!out.contains("super.retainAll("), out)
    // `++=` is the OTHER wrong answer for `addAll`: legal as `super.++=(c)`, and it returns the
    // collection where java's caller branches on a boolean.
    assert(!out.contains("super.++=("), out)
  }

  test("…and it is a RECORDED decision, universal, carrying the JDK BODY that licenses it") {
    val log = Pipeline.runTraced(SpoonTir.fromSource(src), List(new CollectionsTransform()))._2
    val ds  = log.of(Decision.Kind.SubstitutedCall).filter(_.subjectFqn.contains("Fast#"))
    assertEquals(clue(ds.map(_.subjectFqn).toSet.size), 4, clue(ds.map(_.subjectFqn)).toString)
    assert(ds.forall(_.reason.isInstanceOf[Reason.Universal]), clue(ds.map(_.reason)).toString)
    val one = ds.find(_.subjectFqn.endsWith("Fast#containsAll")).getOrElse(fail("no containsAll row"))
    val ps  = PorterNote.pairs(one).toMap
    assertEquals(ps.get("was"), Some("super.containsAll"))
    // the licence itself, at the line — an agent reading the emitted file cannot otherwise recover
    // WHY a `super` call became a static call on `this` (§4.575).
    assert(clue(ps.getOrElse("jdkDefault", "")).contains("!contains(e)"))
    // …and it is rendered where the member is, not in a sibling TSV.
    assert(PorterNote.Rendered.contains(Decision.Kind.SubstitutedCall))
    assert(PorterNote.AtDeclaration.contains(Decision.Kind.SubstitutedCall))
  }

  // -------------------------------------------------------------------------
  // the negatives
  // -------------------------------------------------------------------------

  test("NEGATIVE — a member NOT in the table keeps its refusal, at the same shape") {
    // `subList` is a helper taking the receiver as an ARGUMENT, exactly like the four above, so
    // `superPlaced` refuses it for exactly the reason it used to refuse them. What separates them
    // is not the shape of the rewrite: it is that `AbstractList.subList` reads the receiver's own
    // FIELDS, so no helper standing on `this` computes what `super` named. The call stays as java
    // wrote it and fails to compile naming the member, which is M6's refusal working.
    val out = emitted(src)
    assert(out.contains("super.subList("), out)
    assert(!out.contains("JavaCollections.subList(this,"), out)
  }

  test("NEGATIVE — a program-declared ANCESTOR that declares the member: `super` still has a target") {
    // `super.removeAll` here names `Base#removeAll`, which this port EMITS. The JDK default is not
    // what java meant, and substituting the helper would silently run a different program — no
    // compile error, no moved count, nothing but a method that stopped consulting its superclass.
    val out = emitted(
      """package demo;
        |import java.util.*;
        |class Base extends java.util.ArrayList<String> {
        |  public boolean removeAll(Collection<?> c) { return false; }
        |}
        |class Sub extends Base {
        |  public boolean removeAll(Collection<?> c) { return super.removeAll(c); }
        |}
        |""".stripMargin)
    assert(out.contains("super.removeAll("), out)
    assert(!out.contains("JavaCollections.removeAll(this,"), out)
  }

  test("NEGATIVE — a class the phase did NOT re-parent is owed nothing") {
    // No mapped parent means `parentClash` has no entry, `super.containsAll` still resolves to the
    // library's own member, and the phase has no standing to say anything about it (§4.56).
    val out = emitted(
      """package demo;
        |import java.util.*;
        |class Own { public boolean containsAll(Collection<?> c) { return false; } }
        |class Mine extends Own {
        |  public boolean containsAll(Collection<?> c) { return super.containsAll(c); }
        |}
        |""".stripMargin)
    assert(out.contains("super.containsAll("), out)
    assert(!out.contains("JavaCollections.containsAll(this,"), out)
  }

  test("NEGATIVE — a SHIM parent already HAS java's members, which is K29's two-way bind") {
    // `java.util.AbstractCollection` maps to the shim, which carries java's own member NAMES and
    // arity by construction (§4.5) — so `super.containsAll(c)` resolves there and there is nothing
    // to supply. This is precisely why `AbstractCollection` never had K29's problem and
    // `AbstractSet` does: the difference is the target, not the member.
    val out = emitted(
      """package demo;
        |import java.util.*;
        |class Bag extends java.util.AbstractCollection<String> {
        |  public boolean containsAll(Collection<?> c) { return super.containsAll(c); }
        |  public Iterator<String> iterator() { return null; }
        |  public int size() { return 0; }
        |}
        |""".stripMargin)
    assert(out.contains("super.containsAll("), out)
    assert(!out.contains("JavaCollections.containsAll(this,"), out)
  }

  test("the no-op, by arithmetic: a program with no such `super` records NOTHING") {
    val log = Pipeline.runTraced(
      SpoonTir.fromSource(
        """package demo;
          |import java.util.*;
          |class Plain { boolean ask(List<String> xs, Collection<String> c) { return xs.containsAll(c); } }
          |""".stripMargin),
      List(new CollectionsTransform()))._2
    assertEquals(clue(log.of(Decision.Kind.SubstitutedCall)).size, 0)
  }
