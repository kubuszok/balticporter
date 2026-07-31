package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir

/** A field/method clash is decided by PLACEMENT — `TirEmitter.resolveMemberClashes`.
  *
  * CLAUDE.md §4.55's second face: Java keeps fields and methods in separate namespaces and Scala has
  * one, so a field that shares a name with a method has to move. What the pass must not do is read
  * that as "shares a name" alone, because a Java `static` member does not stay in the class — the
  * emitter partitions the body and puts the statics in the companion `object`. A `static` factory
  * and an instance field are then two members of two different scopes, and neither can see the
  * other.
  *
  * That pairing is not a curiosity: it is what a PRIVATE CONSTRUCTOR forces. A class whose
  * constructor is private is built through same-named static factories, and the factory naturally
  * takes the name of the field it fills — Ashley's `Family` has `all`/`one`/`exclude` as final
  * fields and `all(…)`/`one(…)`/`exclude(…)` as static builders, three renames that changed public
  * surface for nothing.
  *
  * The asymmetry between the two scopes is the other half of the rule and is why this is not one
  * `isStatic` test: the INSTANCE scope is inherited, so a field still clashes with a method a
  * SUBCLASS declares; the companion inherits nothing (the emitter re-exports a parent's companion
  * with this type's own static names excluded), so a static field can only meet a static method of
  * its own class.
  */
class MemberClashPlacementSpec extends munit.FunSuite:

  private def emit(src: String): String = new TirEmitter(SpoonTir.fromSource(src)).emit

  // -------------------------------------------------------------------------------------------
  // the false positive — a private constructor's static factory beside the field it fills
  // -------------------------------------------------------------------------------------------

  private val factoryIdiom =
    """package demo;
      |public class Family {
      |  private final int all;
      |  private final int one;
      |  private Family (int all, int one) { this.all = all; this.one = one; }
      |  public int getAll () { return all; }
      |  public static Family all (int n) { return new Family(n, 0); }
      |  public static Family one (int n) { return new Family(0, n); }
      |}
      |""".stripMargin

  test("a STATIC factory does not clash with the instance field it fills — nothing is renamed") {
    val out = emit(factoryIdiom)
    assert(!clue(out).contains("all$field"))
    assert(!out.contains("one$field"))
  }

  test("…and the two really do land in different scopes: the field in the class, the factory in the companion") {
    val out = emit(factoryIdiom)
    val cls = out.indexOf("class Family")
    val obj = out.indexOf("object Family")
    assert(clue(cls) >= 0 && clue(obj) > cls)
    // the factory is emitted after the `object` keyword; the field's reader before it
    assert(out.indexOf("def all(") > obj)
    assert(out.indexOf("def getAll(") < obj)
  }

  // -------------------------------------------------------------------------------------------
  // NEGATIVES — the pass must still rename every clash that is real
  // -------------------------------------------------------------------------------------------

  test("NEGATIVE: an instance field and an INSTANCE method of the same name still clash") {
    val out = emit(
      """package demo;
        |public class Builder {
        |  private int all;
        |  public Builder all (int n) { this.all = n; return this; }
        |}
        |""".stripMargin)
    assert(clue(out).contains("all$field"))
  }

  test("NEGATIVE: an instance field and a method in a SUBCLASS still clash — the instance scope is inherited") {
    val out = emit(
      """package demo;
        |class Base { protected int hasNext; }
        |class Sub extends Base { public boolean hasNext () { return hasNext != 0; } }
        |""".stripMargin)
    assert(clue(out).contains("hasNext$field"))
  }

  test("NEGATIVE: a STATIC field and a STATIC method of the same name still clash — one companion") {
    val out = emit(
      """package demo;
        |public class Holder {
        |  private static int count;
        |  private int keepMeAClass;
        |  public static int count (int n) { return n; }
        |}
        |""".stripMargin)
    assert(clue(out).contains("count$field"))
  }

  test("the other cross-placement pair is clean too: a STATIC field beside an INSTANCE method") {
    val out = emit(
      """package demo;
        |public class Holder {
        |  private static int limit;
        |  public int limit () { return limit; }
        |}
        |""".stripMargin)
    assert(!clue(out).contains("limit$field"))
  }
