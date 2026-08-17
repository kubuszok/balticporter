package balticporter.emit

import balticporter.frontend.spoon.SpoonTir

/** A java FIELD named like a method an UNPARSED ancestor declares — `ENGINE-LIMITS.md` K28.2.
  *
  * `TirEmitter.resolveFieldShadowing` compared a field against the library's own hierarchy and
  * against nothing else, so `private boolean finalize` and `private CharSequence chars` were emitted
  * as `private var` under a method scala says they cannot override. Neither is a typer error, so
  * neither is visible until the port reaches 0 (`CLAUDE.md` §3).
  *
  * THE NEGATIVES ARE THE POINT. `ExternalSurface.mayDeclare` answers YES for a type it has no row
  * for, and this reader must NOT take that arm: unknown-is-yes renames a field on every class with
  * an unparsed parent, which moves emitted surface on every port for no evidence at all.
  */
class FieldShadowsExternalSpec extends munit.FunSuite:

  private def fieldNames(java: String, owner: String): Set[String] =
    val p = TirEmitter.resolveFieldShadowing(SpoonTir.fromSource(java))
    p.symbols.all.filter(_.fullName.startsWith(owner + "#")).map(_.name).toSet

  // -- java.lang.Object: above every type whether or not a parent list says so ----------------

  test("a field named `finalize` is renamed — the far side is java.lang.Object's") {
    val names = fieldNames(
      """
      class BlockContinueImpl {
        private boolean finalize;
        public boolean isFinalize() { return finalize; }
      }
      """, "BlockContinueImpl")
    assert(names.contains("finalize$shadow"), names)
    assert(!names.contains("finalize"), names)
  }

  test("…and so is one named `wait`, which no parent list mentions either") {
    val names = fieldNames("class Holder { private int wait; }", "Holder")
    assert(names.contains("wait$shadow"), names)
  }

  // -- a KNOWN platform interface: an absence from its set is proof ---------------------------

  test("a field named `chars` on a CharSequence implementor is renamed") {
    val names = fieldNames(
      """
      class RepeatedSequence implements CharSequence {
        private CharSequence chars;
        public int length() { return 0; }
        public char charAt(int i) { return 'a'; }
        public CharSequence subSequence(int a, int b) { return this; }
      }
      """, "RepeatedSequence")
    assert(names.contains("chars$shadow"), names)
    assert(!names.contains("chars"), names)
  }

  test("NEGATIVE — the same field name on a class that does NOT implement CharSequence stays") {
    val names = fieldNames("class ContentNode { private CharSequence chars; }", "ContentNode")
    assert(names.contains("chars"), names)
    assert(!names.exists(_.startsWith("chars$")), names)
  }

  test("NEGATIVE — ARITY is compared: Comparable declares compareTo(1), not compareTo(0)") {
    val names = fieldNames(
      """
      class Ranked implements Comparable<Ranked> {
        private int compareTo;
        public int compareTo(Ranked o) { return 0; }
      }
      """, "Ranked")
    assert(names.contains("compareTo"), names)
  }

  // -- an UNKNOWN external ancestor: unknown does NOT rename ----------------------------------

  test("NEGATIVE — an unparsed parent with no stated surface leaves the field alone") {
    val names = fieldNames(
      """
      class Rolls extends java.util.Random {
        private int nextInt;
      }
      """, "Rolls")
    assert(names.contains("nextInt"), names)
    assert(!names.exists(_.startsWith("nextInt$")), names)
  }

  test("NEGATIVE — a name no external ancestor declares at all is untouched") {
    val names = fieldNames(
      """
      class Seq implements CharSequence {
        private int startIndex;
        public int length() { return 0; }
        public char charAt(int i) { return 'a'; }
        public CharSequence subSequence(int a, int b) { return this; }
      }
      """, "Seq")
    assert(names.contains("startIndex"), names)
  }

  // -- the external question is asked of the FRESH name too -----------------------------------

  test("a rename forced by BOTH halves lands on a name neither side declares") {
    val names = fieldNames(
      """
      class Base { protected int toString; }
      class Derived extends Base { private int toString; }
      """, "Derived")
    assert(names.exists(_.startsWith("toString$")), names)
    assert(!names.contains("toString"), names)
  }
