// ---------------------------------------------------------------------------------------------
// HAND-WRITTEN. The T9 exclusion's CASCADE, and the only part of it a port can answer.
//
// `liqp/TemplateTest.java` is one of the four files `test.conf` excludes because the frontend
// refuses a method-LOCAL named class outright (ENGINE-LIMITS.md T9). That exclusion costs 62
// `@Test`, which is loud, counted and NOT fixable from here — it closes the day the frontend grows
// the node, by DELETING the exclusion.
//
// What is fixable from here is the part of the loss that is not about `TemplateTest` at all: four
// OTHER suites — `liqp/nodes/{Gt,GtEq,Lt,LtEq}NodeTest.java` — `import liqp.TemplateTest` for one
// nested public helper, and each is a whole suite that would not compile because a file it merely
// borrows a data type from is absent. That is 12 scalac errors in the emitted suite and, behind
// them, four suites' worth of tests that never run.
//
// The upstream, verbatim (`liqp/TemplateTest.java:35-46`):
//
//     public static class ComparableBase implements Comparable<ComparableBase> {
//         public final int val;
//         public ComparableBase(int val) { this.val = val; }
//         @Override public int compareTo(ComparableBase o) { return Integer.compare(val, o.val); }
//     }
//
// THE SURFACE IS READ OFF THE FOUR CALL SITES AND IS NOT WIDENED. Each of them writes exactly
// `new TemplateTest.ComparableBase(n)` three times and then renders `{{ a > b }}`, so what they
// need is the constructor and `Comparable`. `val` is reproduced because it is `public final` in a
// class the templating engine reflects over, and dropping a public field from a hand-written stand-in
// is precisely the silent divergence CLAUDE.md §3 is about; it is spelled `` `val` `` because Scala
// reserves the word and Java does not.
//
// NOTHING ELSE from `TemplateTest.java` is here. The other nested type (`Foo implements
// Inspectable`) and all 24 of its `@Test` belong to the excluded file and stay lost — a stand-in
// that grew the rest of the suite back would be a suite nobody ported, reporting green.
// ---------------------------------------------------------------------------------------------
package ssg.liquid

object TemplateTest:

  class ComparableBase(val `val`: Int) extends java.lang.Comparable[ComparableBase]:
    override def compareTo(o: ComparableBase): Int =
      java.lang.Integer.compare(this.`val`, o.`val`)
