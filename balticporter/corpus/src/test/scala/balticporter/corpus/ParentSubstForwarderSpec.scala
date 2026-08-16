package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.Pipeline

/** A DIAMOND FORWARDER carries the PARENT's signature, and a generic parent writes that signature
  * in its OWN scope — so every type parameter it mentions must be substituted by what the subclass
  * instantiates the parent with (`CLAUDE.md` §4.56, `balticporter.tir.ParentSubst`).
  *
  * ==The defect==
  * Java has single inheritance of implementation, so a concrete superclass method simply IMPLEMENTS
  * an interface's default of the same name; scala linearises and refuses, so the emitter declares
  * the forwarder itself. It rendered `d.returnTpt` and `d.paramss` — the parent's DefDef — verbatim:
  *
  * {{{
  *   abstract class LeafBase extends fbound.SeqBase[fbound.Leaf] with fbound.Leaf {
  *     override def split(c: Char): scala.Array[T] = super[SeqBase].split(c)   // Not found: type T
  *   }
  * }}}
  *
  * Valid-looking Scala naming a type the emitting class does not declare. The instantiation is
  * right there in the `extends` clause, so the repair is exact rather than a guess.
  *
  * ==F-BOUNDED, because that is the shape that makes it unavoidable==
  * `Seq<T extends Seq<T>>` is the self-typed-builder idiom every sequence library uses, and it
  * forces the parameter into RESULT position on almost every member — so the forwarder cannot avoid
  * mentioning it. `ENGINE-LIMITS.md` G8 is about an F-bound with no consistent FILL; this is the
  * opposite case and must not be confused with it: nothing is being inferred here, the argument is
  * written down.
  *
  * ==Three shapes, and the two negatives are the point==
  *  - `LeafBase` — the parent is instantiated with a CONCRETE type: `T` becomes `fbound.Leaf`;
  *  - `MidBase[T]` — the subclass passes its OWN parameter through, so the substitution must land on
  *    a type the emitted class really declares. A repair that erased to the BOUND, or to `Any`,
  *    passes the first assertion and fails this one;
  *  - `Motor` — a non-generic hierarchy, whose forwarder must be byte-identical to what it was.
  *
  * ==TRANSITIVE, which is the half the first walk got wrong==
  * `TwiceRemoved extends LeafBase` names a NON-GENERIC parent, and the member it forwards is
  * `SeqBase`'s, whose `T` is bound two levels up. A walk that only descends into APPLIED parents
  * stops at `LeafBase` and reports an empty map — right for the immediate parent, silent for every
  * hierarchy with a plain class in the middle, which is the majority shape in the corpus.
  */
class ParentSubstForwarderSpec extends munit.FunSuite:

  private val src =
    """package fbound;
      |/** F-bounded, with the parameter in RESULT position — a `default` so the mixin side of the
      |  * diamond is CONCRETE, which is what makes scala refuse and the emitter forward. */
      |public interface Seq<T extends Seq<T>> {
      |  default T trim() { return null; }
      |  default T merge(T other) { return other; }
      |  default T[] split(char c) { return null; }
      |}
      |public abstract class SeqBase<T extends Seq<T>> implements Seq<T> {
      |  public T trim() { return null; }
      |  public T merge(T other) { return other; }
      |  public T[] split(char c) { return null; }
      |}
      |/** the F-bound closed at a concrete type — the `BasedSequence extends IRichSequence<BasedSequence>`
      |  * shape, invented here so nothing in the engine names a ported library (§1). */
      |public interface Leaf extends Seq<Leaf> { }
      |/** superclass CONCRETE, mixin CONCRETE (inherited default): the diamond. */
      |public abstract class LeafBase extends SeqBase<Leaf> implements Leaf { }
      |/** one more level, through a NON-GENERIC parent. */
      |public abstract class TwiceRemoved extends LeafBase implements Leaf { }
      |/** the subclass passes its OWN parameter through: the substitution must land on `T` as THIS
      |  * class declares it, never on the bound and never on `Any`. */
      |public abstract class MidBase<T extends Seq<T>> extends SeqBase<T> implements Seq<T> { }
      |/** the member's OWN type parameter — not the class's, and substituted away by nothing. */
      |public class Box<V> { }
      |public interface Keyed { default <V> V pick(Box<V> b) { return null; } }
      |public class KeyedBase { public <V> V pick(Box<V> b) { return null; } }
      |public class KeyedImpl extends KeyedBase implements Keyed { }
      |/** NEGATIVE — nothing generic anywhere; the forwarder must be exactly what it always was. */
      |public interface Tickable { default void tick() { } }
      |public class Engine { public void tick() { } }
      |public class Motor extends Engine implements Tickable { }
      |""".stripMargin

  private val program = Pipeline.run(SpoonTir.fromSource(src), Nil)
  private val out     = new TirEmitter(program).emit

  test("a forwarder in a class that closes the F-bound names the ARGUMENT, not the parent's parameter") {
    assert(clue(out).contains("override def trim(): fbound.Leaf = super[SeqBase].trim()"))
    assert(out.contains("override def split(c: scala.Char): scala.Array[fbound.Leaf] = super[SeqBase].split(c)"))
    // the PARAMETER position too — a repair that only substituted the result type leaves this one,
    // and `merge` is the member that has both.
    assert(out.contains("override def merge(other: fbound.Leaf): fbound.Leaf = super[SeqBase].merge(other)"))
  }

  test("TRANSITIVE — the binding is two levels up, through a NON-GENERIC parent") {
    assert(clue(out).contains("override def trim(): fbound.Leaf = super[LeafBase].trim()"))
    assert(out.contains("override def split(c: scala.Char): scala.Array[fbound.Leaf] = super[LeafBase].split(c)"))
  }

  test("a subclass that passes its OWN parameter through keeps it — no erasure to the bound") {
    assert(clue(out).contains("override def trim(): T = super[SeqBase].trim()"))
    assert(out.contains("override def merge(other: T): T = super[SeqBase].merge(other)"))
    // the two things a lazy repair would have written instead
    assert(!out.contains("override def trim(): fbound.Seq[T]"))
    assert(!out.contains("override def trim(): scala.Any"))
  }

  test("a forwarded GENERIC METHOD keeps its OWN type parameters") {
    // the second face of the same error text and a different cause: `<V> V pick(Box<V>)` forwarded
    // without its `[V]` names a type nothing declares, and the class-parameter repair does not
    // reach it — `super[KeyedBase].pick(b)` infers `V` from the argument, so no explicit type
    // application is written.
    val fwd = raw"""override def pick\[V[^\]]*\]\(b: fbound\.Box\[V\]\): V = super\[KeyedBase\]\.pick\(b\)""".r
    assert(fwd.findFirstIn(clue(out)).isDefined)
  }

  test("NEGATIVE — a non-generic hierarchy's forwarder is untouched") {
    assert(clue(out).contains("override def tick(): scala.Unit = super[Engine].tick()"))
  }
