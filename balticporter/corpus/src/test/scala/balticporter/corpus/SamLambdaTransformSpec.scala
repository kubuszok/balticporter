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

  test("a `return` inside the converted body still means LEAVE THE LAMBDA") {
    // java's lambda body is a METHOD body, so `return` is legal in it; scala's lambda is an
    // expression and rejects `return` outright. The emitter's own `Tree.Lambda` arm restores java's
    // meaning with a nested `def` (`JS-S21`), and this pins that the conversion hands it a body it
    // can render rather than one that will not compile.
    val p = port(
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
        |}""".stripMargin, new SamLambdaTransform)
    assertIdiomConverts(p, IdiomKind.SamLambda, "C#c")
    assertEmits(p, "def ")   // the nested `def` the emitter interposes for a `return`
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
