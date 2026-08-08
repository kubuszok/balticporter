package balticporter.corpus

import balticporter.testkit.PortSuite
import balticporter.tir.{Decision, IdiomKind}
import balticporter.transform.SamLambdaTransform

/** THE SAM TRANSFORMER — the conversion, its emitted SHAPE, and its attribution.
  *
  * `JS-E06`'s third commit is the precedent for why this suite is the whole evidence and not the
  * corpus: a defect found by the test for the cell NEXT DOOR, at zero corpus sites. What a corpus
  * run can confirm is a BLAST; what only a fixture can confirm is that a guard declines the shape it
  * was written for and that the emitted form is the one intended.
  *
  * The REFUSALS are pinned next door in `IdiomCensusSpec`, against the shared decision function —
  * one mechanism, one seam (§4.6): the census and this transformer ask `SamLambda.decide`, so a
  * second copy of the guards could not disagree with the first.
  */
class SamLambdaTransformSpec extends PortSuite:

  private val runnable =
    """class C {
      |  Runnable make(final String s) {
      |    return new Runnable() { public void run() { System.out.println(s); } };
      |  }
      |}""".stripMargin

  test("a capturing anonymous Runnable becomes a LAMBDA") {
    val p = port(runnable, new SamLambdaTransform)
    assertEmits(p, "=>")
    assertNotEmits(p, "new java.lang.Runnable()")
    assertIdiomConverts(p, IdiomKind.SamLambda, "C#make")
  }

  test("…ASCRIBED to the interface the anonymous class named — the guard the SHAPE replaces") {
    // Not decoration. A java anonymous class is a value whose type javac WROTE DOWN; a bare scala
    // function literal is a poly expression whose type the CALLEE decides. Dropping the type hands
    // every use of the value to scala's own overload resolution, which is the one place this engine
    // knows it cannot follow javac (`JS-C22`/`JS-C23`). With the ascription, nothing about any call
    // moved — which is why there is no seventh guard.
    val p = port(runnable, new SamLambdaTransform)
    assertEmits(p, ": java.lang.Runnable)")
  }

  test("…and the CONSTRUCTOR APPLICATION goes with it — the frontend models `new I(){…}` as an\n" +
       "     `Apply` over the `New`, so a conversion at the `New` alone leaves a lambda APPLIED") {
    // The defect this pins is loud, but only if somebody looks: `((…) => …): I)()` is a lambda
    // applied to the constructor's (empty) argument list. It is why the conversion sits at the
    // `Apply` and reads the argument list there — which is also the belt to guard 1's brace, since
    // java's SAM rule admits only an INTERFACE and an interface has no constructor to pass
    // arguments to.
    val p = port(runnable, new SamLambdaTransform)
    assertNotEmits(p, ": java.lang.Runnable)()")
  }

  test("…and it is an ASCRIPTION, never an `asInstanceOf`") {
    // `Tree.Typed` renders a java CAST as `asInstanceOf`, which would be a run-time assertion over a
    // value whose type is already known — and, at a poly expression, one scalac cannot even type.
    val p = port(runnable, new SamLambdaTransform)
    assertNotEmits(p, "asInstanceOf[java.lang.Runnable]")
  }

  test("a Comparator's TWO parameters survive with their names and their types") {
    val p = port(
      """import java.util.Comparator;
        |class C {
        |  Comparator<String> byLen(final int bias) {
        |    return new Comparator<String>() {
        |      public int compare(String a, String b) { return a.length() - b.length() + bias; }
        |    };
        |  }
        |}""".stripMargin, new SamLambdaTransform)
    assertEmitsMatch(p, """\(a: java\.lang\.String, b: java\.lang\.String\) =>""")
    assertEmits(p, ": java.util.Comparator[java.lang.String])")
  }

  private val valuedReturn =
    """import java.util.Comparator;
      |class C {
      |  Comparator<String> c(final int bias) {
      |    return new Comparator<String>() {
      |      public int compare(String a, String b) {
      |        if (a == null) { return -1; }
      |        return a.compareTo(b) + bias;
      |      }
      |    };
      |  }
      |}""".stripMargin

  test("a VALUE-returning `return` inside the converted body gets the SAM METHOD's result type\n" +
       "     — `ENGINE-LIMITS.md` I9, and the shape that took wave 1 from 0 to 4 typer errors") {
    // java's lambda body is a METHOD body, so `return` is legal in it; scala's lambda is an
    // expression and rejects `return` outright. The emitter restores java's meaning with a nested
    // `def` (`JS-S21`) — and a `def` needs a RESULT TYPE, which is `compare`'s `int` and NEVER the
    // interface's `Comparator[String]`. Before I9 nothing carried it, so the emitter refused (M6)
    // and rendered the body bare: `(a, b) => { if (…) return -1; … }`, four of which were the whole
    // of wave 1's failure on the libGDX base.
    //
    // Asserted on the RENDERED `def` and not on the string "def ", which the ENCLOSING method's own
    // declaration already satisfies — a vacuous assertion is how this passed while the emission was
    // broken.
    val p = port(valuedReturn, new SamLambdaTransform)
    assertIdiomConverts(p, IdiomKind.SamLambda, "C#c")
    assertEmitsMatch(p, """def body\$\d+\(\): scala\.Int = """)
  }

  test("…and the refusal that USED to fire there is counted at ZERO for it") {
    // `OmissionCheck.unnameableLambdaReturn` is M6's residue turned into a number. The point of
    // asserting it here is the NARROWING: this exact tree used to be the residue, and after the
    // thread it is not, so the count is evidence about the fix rather than about the corpus.
    val p = port(valuedReturn, new SamLambdaTransform)
    assertEquals(clue(balticporter.tir.OmissionCheck.unnameableLambdaReturn(p.after)).map(_.owner), Nil)
  }

  test("…and it FIRES where M6 still stands — a lambda the SOURCE wrote, whose method is a class file") {
    // The other half of the narrowing, and what makes the zero above mean anything. javac inferred
    // this lambda's type from `Supplier`'s class file; the program holds no `DefDef` for `get`, so
    // nothing can name the `def`'s result type and the emitter refuses rather than guessing (M6).
    // Non-vacuity by FIXTURE, since the corpus has no such site — which is exactly the state a
    // residue count is worthless without (§3: a check reporting zero is only as good as its
    // coverage).
    val p = port(
      """class C {
        |  java.util.function.Supplier<String> s() {
        |    return () -> { return "x"; };
        |  }
        |}""".stripMargin)
    val fs = balticporter.tir.OmissionCheck.unnameableLambdaReturn(p.after)
    assertEquals(clue(fs).map(_.owner), List("C#s"))
    assertNotEmits(p, "body$")
  }

  test("a VOID-returning `return` keeps rendering `scala.Unit` — the arm the body could always decide") {
    val p = port(
      """class C {
        |  Runnable r(final int n) {
        |    return new Runnable() {
        |      public void run() { if (n < 0) { return; } System.out.println(n); }
        |    };
        |  }
        |}""".stripMargin, new SamLambdaTransform)
    assertEmitsMatch(p, """def body\$\d+\(\): scala\.Unit = """)
  }

  test("every REFUSAL leaves the anonymous class BYTE-IDENTICAL to what the port shipped before") {
    // The property that makes a refusal enumeration worth anything: a declined site is not
    // "converted less", it is untouched.
    val nonCapturing = """class C { Runnable make() { return new Runnable() {
                         |  public void run() { System.out.println("hi"); }
                         |}; } }""".stripMargin
    val bare = port(nonCapturing)
    val run  = port(nonCapturing, new SamLambdaTransform)
    assertEquals(run.out, bare.out)
    assertIdiomRefuses(run, IdiomKind.SamLambda, "NonCapturing")
  }

  test("the DECISION is subjected at the ENCLOSING DECLARATION, never at the site") {
    // `CLAUDE.md` §5.1's granularity rule, and here it is also what makes the wave's blast
    // classification COMPUTABLE: a decision subjected at a `Tree.New` has no declaration symbol for
    // `members.tsv` to be joined on, so every converted site would land in the unexplained residue
    // and the gate would fail on its own successes.
    val p = port(runnable, new SamLambdaTransform)
    assertDecides(p, Decision.Kind.SamLambda, "C#make")
  }

  test("TWO conversions in one method are ONE decision with a COUNT") {
    val p = port(
      """class C {
        |  void two(final String s) {
        |    Runnable a = new Runnable() { public void run() { System.out.println(s); } };
        |    Runnable b = new Runnable() { public void run() { System.out.println(s + "!"); } };
        |    a.run(); b.run();
        |  }
        |}""".stripMargin, new SamLambdaTransform)
    val ds = p.decisions.filter(_.kind == Decision.Kind.SamLambda)
    assertEquals(clue(ds).size, 1)
    assertEquals(ds.head.detail.get("count"), Some("2"))
  }

  test("the decision RECORDS the residue no guard can reach — java's STABLE class name") {
    // A capturing lambda allocates per evaluation, which is what guard 5 buys; its CLASS is still
    // not the anonymous class's. Nothing structural can reach that (every reference to a value can
    // reach `getClass()`), so it is counted where §4.45's reader is: on the conversion's own row.
    val p = port(runnable, new SamLambdaTransform)
    val d = p.decisions.find(_.kind == Decision.Kind.SamLambda).get
    assert(clue(d.detail.getOrElse("was", "")).nonEmpty, "the java class name is not recorded")
    assert(clue(d.detail.getOrElse("why", "")).contains("getClass"))
  }

  test("…and a conversion inside a LOCAL `val` is claimed by the enclosing MEMBER, not by the local") {
    // The failure D3's rule exists to prevent, and it is silent: a decision subjected at a local has
    // no row in `members.tsv` for the wave's blast classification to join on, so every converted
    // site would land in the unexplained residue and the gate would fail on its own successes.
    val p = port(
      """class C {
        |  void two(final String s) {
        |    Runnable a = new Runnable() { public void run() { System.out.println(s); } };
        |    a.run();
        |  }
        |}""".stripMargin, new SamLambdaTransform)
    val ds = p.decisions.filter(_.kind == Decision.Kind.SamLambda)
    assertEquals(clue(ds).map(_.subjectFqn), List("C#two"))
  }

  test("the porter NOTE's PLACEMENT is `AtDeclaration` — a kind in the wrong set is a note that\n" +
       "     never appears (§4.575)") {
    // Asserted structurally rather than on emitted text, because the placement sets are the
    // machinery and the text is the consequence. `NoteCoverageCheck` gates both directions on every
    // real run; what a fixture can pin is that the kind is in the rendered set at all and in the one
    // that matches its subject — the decision's subject is a DECLARATION, so `InBody` (a dropped
    // member, which has no `def` to sit above) and `NotInTree` (a dropped type) are both wrong.
    import balticporter.tir.PorterNote
    assert(PorterNote.Rendered.contains(Decision.Kind.SamLambda))
    assert(PorterNote.AtDeclaration.contains(Decision.Kind.SamLambda))
    assertEquals(PorterNote.slug(Decision.Kind.SamLambda), "sam-lambda")
  }

  test("a RAW-typed target converts, and the ascription it writes is the WILDCARD one — measured,\n" +
       "     not assumed, because the guard it would have justified is not free") {
    // The ascription is `nw.tpt`, and for a RAW generic use this engine renders `[?]` (the reference
    // port's own answer, §3.5). So a raw `new Comparator(){…}` emits
    // `((a, b) => …): java.util.Comparator[?]`, and a scala lambda at a WILDCARD-APPLIED type is a
    // shape worth doubting: if scalac refuses to instantiate a SAM there, the conversion is a
    // compile error the corpus cannot see, because no corpus site has this shape.
    //
    // MEASURED rather than guarded: the emitted text was put through `scala-cli compile --scala
    // 3.8.4` and accepted, exit 0 — the wildcard instantiates to the erasure the raw type already
    // had, and the lambda's parameters are `Object` on both sides. So there is no guard here and
    // the refusal enumeration is unchanged; what this fixture pins is the EMISSION, so that the
    // measurement stays attached to the thing it was made about (`ENGINE-LIMITS.md` S1).
    val p = port(
      """import java.util.Comparator;
        |class C {
        |  Comparator raw(final int bias) {
        |    return new Comparator() {
        |      public int compare(Object a, Object b) { return bias; }
        |    };
        |  }
        |}""".stripMargin, new SamLambdaTransform)
    assertIdiomConverts(p, IdiomKind.SamLambda, "C#raw")
    assertEmits(p, "): java.util.Comparator[?])")
    assertEmitsMatch(p, """\(a: java\.lang\.Object, b: java\.lang\.Object\) =>""")
  }

  test("the phase DECLARES its kind, so a run where it found nothing still prints a zero") {
    assertEquals(new SamLambdaTransform().idiomKinds, Set(IdiomKind.SamLambda))
  }

  test("ORDERING: it runs BEFORE the two engine phases that would move what it ascribes to") {
    // A `CollectionsTransform` retarget moving `java.util.Comparator` to `scala.math.Ordering`
    // changes what the ascription would SAY, and a phase that ran afterwards would be writing a type
    // java never named at that site.
    assertEquals(new SamLambdaTransform().runsBefore,
                 Set("java-collections->scala", "package-rename"))
  }
