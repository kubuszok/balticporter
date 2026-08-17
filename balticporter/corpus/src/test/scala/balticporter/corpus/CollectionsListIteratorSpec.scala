package balticporter.corpus

import balticporter.testkit.PortSuite
import balticporter.tir.JdkSurfaceCheck
import balticporter.transform.CollectionsTransform

/** K23's `listIterator` refusal, RE-READ and closed — and its sibling, which is not.
  *
  * The refusal read *scala's `Iterator` is forward-only and read-only, so every mapping is either a
  * different protocol or a detached copy whose `set` updates nothing*. Every word of that is about
  * `scala.collection.Iterator`; the RECEIVER is a `mutable.Buffer`, which has indexed read, indexed
  * update, insert and remove — `ListIterator`'s whole contract. "Nothing to map them onto" is true
  * of a MAPPING and false of a SHIM (`CLAUDE.md` §4.5), which is `ENGINE-LIMITS.md` K5.7's
  * target-versus-parent distinction read one family over.
  *
  * `spliterator` is NOT reopened, and the asymmetry is the whole content of this pair: there is no
  * receiver capability to build a parallel decomposition out of. The near miss is the reason to say
  * so out loud — `buf.asJava.spliterator()` compiles and reports NEITHER `ORDERED` nor `SIZED` where
  * the `ArrayList` java had reports both, so a consumer reading `characteristics()` gets a different
  * answer with nothing to see it (`CLAUDE.md` §4.4).
  */
class CollectionsListIteratorSpec extends PortSuite:

  private val delegating =
    """package demo;
      |import java.util.*;
      |class Wrapped {
      |  private final List<String> items = new ArrayList<String>();
      |  ListIterator<String> cursor()          { return items.listIterator(); }
      |  ListIterator<String> cursor(int index)  { return items.listIterator(index); }
      |}
      |""".stripMargin

  test("`listIterator()` becomes the WRITE-THROUGH cursor over the very buffer") {
    val p = port(delegating, new CollectionsTransform)
    assertEmits(p, "balticporter.runtime.JavaListIterator.over(this.items)")
  }

  test("…and `listIterator(i)` carries java's starting position") {
    val p = port(delegating, new CollectionsTransform)
    assertEmits(p, "balticporter.runtime.JavaListIterator.over(this.items, index)")
  }

  test("the RESULT TYPE moves with it, so the JDK relation `ListIterator <: Iterator` survives") {
    val p = port(delegating, new CollectionsTransform)
    // `java.util.ListIterator extends java.util.Iterator`; `Iterator` maps and, left unmapped, this
    // one splits an edge every `Iterator`-typed slot depends on — the rule `typeMap`'s `Queue`/
    // `Deque` and `ConcurrentHashMap` blocks state three times over, and the one
    // `collection-closure` was already reporting on the port this fix was written for.
    assertEmits(p, "balticporter.runtime.JavaListIterator[java.lang.String]")
    assertNotEmits(p, "java.util.ListIterator")
  }

  test("a class that IMPLEMENTS `java.util.ListIterator` is emitted onto the shim, at java's arity") {
    val p = port(
      """package demo;
        |import java.util.*;
        |class Fixed implements ListIterator<String> {
        |  public boolean hasNext()      { return false; }
        |  public String next()          { return null; }
        |  public boolean hasPrevious()  { return false; }
        |  public String previous()      { return null; }
        |  public int nextIndex()        { return 0; }
        |  public int previousIndex()    { return -1; }
        |  public void remove()          { }
        |  public void set(String e)     { }
        |  public void add(String e)     { }
        |}
        |""".stripMargin, new CollectionsTransform)
    // §4.5: java's arity survives, because the shim carries java's own shape rather than scala's
    // parameterless one.
    assertEmits(p, "extends balticporter.runtime.JavaListIterator[java.lang.String]")
    assertEmits(p, "def hasPrevious(): scala.Boolean")
  }

  test("NEGATIVE — the phase does not invent `listIterator` for a MAP or a SET receiver") {
    val p = port(
      """package demo;
        |import java.util.*;
        |class Users {
        |  private final Set<String> names = new HashSet<String>();
        |  int count() { return names.size(); }
        |}
        |""".stripMargin, new CollectionsTransform)
    // java declares `listIterator` on `List` and nowhere else, so a kind that never had the member
    // must not acquire one — the arm is `Kind.Seq`/`Kind.Stack` for exactly that reason.
    assertNotEmits(p, "JavaListIterator")
  }

  test("`spliterator` STAYS REFUSED, and the refusal is keyed where a CALL resolves") {
    // java declares `spliterator()` on `Collection` and RE-DECLARES it on `List` and `Set` with
    // their own defaults, so the owner a call resolves at is whichever type the receiver was
    // declared as. Keyed at `Collection` alone the refusal matched nothing on a `List` receiver and
    // the site read as `unhandled` — a reader sent to a wall instead of to the reason (§4.45).
    val keys = JdkSurfaceCheck.Refusals.map(_.api).toSet
    assert(keys.contains("java.util.Collection#spliterator"), keys.toList.sorted.mkString(", "))
    assert(keys.contains("java.util.List#spliterator"), keys.toList.sorted.mkString(", "))
    assert(!keys.contains("java.util.List#listIterator"),
           "the `listIterator` refusal is STALE — the phase now answers for it")
  }

  test("the refusal and the phase table do not CONTRADICT each other") {
    // the stale-refusal guard's own condition, asserted here so that a future arm for `spliterator`
    // has to remove the refusal in the same commit rather than leaving a comment that says the code
    // does not do what it does.
    val refused = JdkSurfaceCheck.Refusals.map(_.api).toSet
    val handled = CollectionsTransform.handledInstance.values.flatten.toSet
    assert(!refused.exists(k => handled.contains(k.substring(k.indexOf('#') + 1)) &&
                                k.endsWith("#spliterator")),
           "spliterator is refused AND handled")
    assert(handled.contains("listIterator"), "listIterator is answered and must say so")
  }
