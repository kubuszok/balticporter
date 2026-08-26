package balticporter.runtime

/** `java.util.Stack`, as Scala — a `Buffer` with java's LIFO five on top of it.
  *
  * ==Why not `scala.collection.mutable.Stack`==
  * Because the two disagree about which END is the top, and nothing in a compile can see it.
  * Java's `Stack extends Vector extends List`: `push` APPENDS, so the top is the LAST element,
  * `get(0)` is the bottom, and the iterator runs bottom-to-top. Scala's `mutable.Stack` is an
  * `ArrayDeque` whose `push` PREPENDS, so its top is element 0 and it iterates top-to-bottom. The
  * two agree on `push`/`pop`/`peek` in isolation and disagree on every LIST-shaped read of the same
  * object — a `for`, a `get(i)`, an `indexOf`, a `toString` — which is `CLAUDE.md` §4.4's defect
  * class arriving through a type mapping rather than through a statement.
  *
  * ==Why not a plain `ArrayBuffer` plus five call rewrites==
  * That was built first and it cannot work, for a reason worth recording: `CollectionsTransform`
  * decides a call rewrite from the RETYPED receiver's type, so two java types that share a scala
  * target share every rewrite. `java.util.ArrayList` already maps to `ArrayBuffer`; a `Stack` there
  * too is a receiver the phase can no longer tell from a list, and `peek()` — the DEQUE `peek`, the
  * FIRST element and `null` when empty — is what the shared arm answers.
  *
  * So the target is its own type, and because it is, java's five can simply BE members with java's
  * own names and java's own contracts. Extending `ArrayBuffer` is what keeps
  * `Stack <: Vector <: List` intact on the scala side: `java.util.Vector` maps to `ArrayBuffer`
  * and `java.util.List` to `Buffer`, so every assignment java allows still type-checks.
  *
  * ==The one member that cannot be java's==
  * `empty()`. Scala's collection API already declares `empty` (the factory, `IterableFactoryDefaults`),
  * with an incompatible result type, so a `def empty: Boolean` here is a compile error rather than a
  * choice. `CollectionsTransform` renames that call to `isEmpty`, which is the same question.
  */
class JavaStack[A] extends scala.collection.mutable.ArrayBuffer[A] {

  /** `java.util.Stack.push(item)` — appends, and RETURNS THE ITEM.
    *
    * `+=` hands back the buffer, so a `push` in expression position would give its caller a
    * collection where java gives it the element. */
  def push(item: A): A = {
    this += item
    item
  }

  /** `java.util.Stack.pop()` — removes and returns the TOP, which is the LAST element.
    *
    * `java.util.EmptyStackException` is java's own, and is named rather than approximated: a
    * `NoSuchElementException` compiles everywhere and silently changes what a
    * `catch (EmptyStackException e)` catches. */
  def pop(): A = {
    if isEmpty then throw new java.util.EmptyStackException
    remove(length - 1)
  }

  /** `java.util.Stack.peek()` — the TOP, without removing it. [[pop]]'s guard, and note this is the
    * OPPOSITE END from `java.util.Deque.peek()`, which the collections phase maps elsewhere. */
  def peek(): A = {
    if isEmpty then throw new java.util.EmptyStackException
    apply(length - 1)
  }

  /** `java.util.Stack.search(o)` — the 1-BASED distance from the TOP, or `-1` when absent.
    *
    * Java's own definition is `size() - lastIndexOf(o)`, so the top is `1` and the bottom is
    * `size`. Nothing in the scala collections counts from either end this way, which is what the
    * platform survey flagged when it said this member has no counterpart. The equality is
    * `lastIndexOf`'s — the PROBE's `equals` — as java's is. */
  def search(o: Any): Int = {
    val i = lastIndexWhere(e => if o == null then e == null else o.equals(e))
    if i < 0 then -1 else length - i
  }
}
