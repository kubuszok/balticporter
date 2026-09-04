package balticporter.corpus

import balticporter.testkit.PortSuite
import balticporter.tir.JdkSurfaceCheck
import balticporter.transform.CollectionsTransform

/** K23's `listIterator` refusal, RE-READ and closed — and its sibling, which is not. */
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

  test("`spliterator()` reproduces JAVA'S OWN DEFAULT at the owner the receiver was typed by") {
    val p = port(
      """package demo;
        |import java.util.*;
        |class Wrapped {
        |  private final List<String> items = new ArrayList<String>();
        |  private final Set<String> tags   = new HashSet<String>();
        |  Spliterator<String> a() { return items.spliterator(); }
        |  Spliterator<String> b() { return tags.spliterator(); }
        |}
        |""".stripMargin, new CollectionsTransform)
    // java re-declares the member three times with three characteristic sets — `List` passes
    // `ORDERED`, `Set` passes `DISTINCT` — and `Spliterators.spliterator(Collection, int)` ORs in
    // `SIZED | SUBSIZED` for both. The emitted call NAMES which of java's declarations it is,
    // rather than carrying the constant, so a reader can check it against the JDK source.
    assertEmits(p, "balticporter.runtime.JavaCollections.orderedSpliterator(this.items)")
    assertEmits(p, "balticporter.runtime.JavaCollections.distinctSpliterator(this.tags)")
    // …and NOT through `asJava`, which is the near miss the refusal actually rested on: that
    // wrapper reports neither `ORDERED` nor `SIZED` where the `ArrayList` java held reports both.
    assertNotEmits(p, "asJava.spliterator")
  }

  test("the refusal that STAYS is `Collection`'s, and it is keyed where a CALL resolves") {
    // java declares `spliterator()` on `Collection` and RE-DECLARES it on `List` and `Set`, so the
    // owner a call resolves at is whichever type the receiver was declared as. Keyed at `Collection`
    // alone the refusal once matched nothing on a `List` receiver and the site read as `unhandled` —
    // a reader sent to a wall instead of to the reason (§4.45).
    val keys = JdkSurfaceCheck.Refusals.map(_.api).toSet
    assert(keys.contains("java.util.Collection#spliterator"), keys.toList.sorted.mkString(", "))
    assert(!keys.contains("java.util.List#spliterator"),
           "the `List#spliterator` refusal is STALE — the phase now answers for it")
    assert(!keys.contains("java.util.Set#spliterator"),
           "the `Set#spliterator` refusal is STALE — the phase now answers for it")
    assert(!keys.contains("java.util.List#listIterator"),
           "the `listIterator` refusal is STALE — the phase now answers for it")
  }

  test("the refusal and the phase table do not CONTRADICT each other") {
    // the stale-refusal guard's own condition, asserted here so that a future arm for `spliterator`
    // has to remove the refusal in the same commit rather than leaving a comment that says the code
    // does not do what it does.
    val refused = JdkSurfaceCheck.Refusals.map(_.api).toSet
    val handled = CollectionsTransform.handledInstance.values.flatten.toSet
    // `Collection#spliterator` is the one row left and it is NOT a contradiction: the member is
    // handled at a MAPPED kind, and the refusal is about the receiver this phase did not map. The
    // guard is therefore asked of the pair the table can actually see — a refused key whose owner
    // this phase HAS a kind for.
    // …asked of the owners whose target this phase actually REWRITES.
    val rewritten = CollectionsTransform.typeMap.collect {
      case (owner, (target, _)) if !CollectionsTransform.ShimFqns.contains(target) => owner
    }.toSet
    assert(!refused.exists(k => rewritten.contains(k.take(k.indexOf('#'))) &&
                                handled.contains(k.substring(k.indexOf('#') + 1))),
           s"a member is refused AND handled at a rewritten owner: $refused")
    assert(handled.contains("listIterator"), "listIterator is answered and must say so")
    assert(handled.contains("spliterator"), "spliterator is answered and must say so")
  }
