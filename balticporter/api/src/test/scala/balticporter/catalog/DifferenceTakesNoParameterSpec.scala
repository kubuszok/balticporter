package balticporter.catalog

/** THE GUARD RAIL THAT PROTECTS `CLAUDE.md` §1's TAXONOMY.
  *
  * A [[Difference]] is a fact about Java and Scala. If a row needed to know something about a
  * LIBRARY — which declarations, which types, which names — it would not be a difference at all: it
  * would be a (b) phase's policy, and it belongs in that phase's constructor where §1.5's merge
  * contract already governs it. Nothing else in the design catches that mistake, because a row with
  * a `Set[String]` in it compiles perfectly, reads plausibly, and turns the (a) registry into a
  * second home for per-library configuration one field at a time.
  *
  * The rule is mechanical: EVERY field of every row, recursively, is a literal or an enum case.
  * Reached by REFLECTION over the constructed values rather than by a list of allowed field names,
  * for the same reason the node-kind enumeration is derived from the jar — a hand-maintained list of
  * what to check is a list a new field is not on.
  *
  * SCOPE, stated so it cannot be quietly widened: this governs `JS-{E,S,C,G}` ONLY. The `JS-{L,P}`
  * rows are [[ApiRow]]s and they legitimately carry per-platform MAPS, because Scala.js and Scala
  * Native genuinely disagree and one shared verdict is exactly what makes a rule wrong for one of
  * them. Relaxing THIS spec to accommodate them would take the language rows with it, so they have
  * their own, narrower guard — `ApiRowCarriesNoPolicySpec`. */
class DifferenceTakesNoParameterSpec extends munit.FunSuite:

  /** `scala.None` when the value is a literal or an enum case all the way down; otherwise what it
    * was. Ordered deliberately: a PARAMETERISED enum case is both a `Product` and a `reflect.Enum`
    * and must be recursed into, so `Product` is tried first; a parameterless case is neither a
    * product nor a literal and is admitted by the `reflect.Enum` arm below it. */
  private def offending(v: Any): Option[String] = v match
    case _: String | _: Int | _: Long | _: Boolean | _: Char | _: Double | _: Float | _: Short | _: Byte =>
      scala.None
    // rejected BEFORE the Product arm: `List` and `Map` are products of their own elements and
    // would otherwise pass by recursion, which is the exact shape a policy field takes.
    case c: scala.collection.Iterable[?] => Some(s"a collection (${c.getClass.getName})")
    case _: Function0[?] | _: Function1[?, ?] | _: Function2[?, ?, ?] => Some("a predicate or a function")
    case p: Product     => p.productIterator.map(offending).collectFirst { case Some(x) => x }
    case _: reflect.Enum => scala.None
    case other          => Some(s"neither a literal nor an enum case (${other.getClass.getName})")

  test("no JS-{E,S,C,G} row carries a scope, a set, a map or a predicate — the (a)/(b) line, mechanised") {
    val bad = Differences.all.flatMap { d =>
      d.productIterator.zip(d.productElementNames).flatMap { (v, name) =>
        offending(v).map(x => s"${d.id}.$name is $x")
      }
    }
    assertEquals(bad, Nil, bad.mkString("\n"))
  }

  test("…and the retirements are held to the same rule — an absorbed id is still catalog data") {
    val bad = Differences.retired.flatMap { r =>
      r.productIterator.zip(r.productElementNames).flatMap { (v, name) =>
        offending(v).map(x => s"${r.id}.$name is $x")
      }
    }
    assertEquals(bad, Nil, bad.mkString("\n"))
  }

  test("the guard has TEETH — a row holding a library-shaped set is rejected") {
    // A spec that only ever sees passing input cannot distinguish "the rule holds" from "the rule
    // is inert". This is the negative every reflective guard owes: the shape that must fail, failing.
    assert(offending(Set("com.example.Widget")).isDefined)
    assert(offending(Map("a" -> "b")).isDefined)
    assert(offending((s: String) => s.isEmpty).isDefined)
    // …and the shapes a row really holds, passing.
    assertEquals(offending(Differences.all.head), scala.None)
    assertEquals(offending(Status.Partial("a reason")), scala.None)
    assertEquals(offending(Twin.NoTwin), scala.None)
    assertEquals(offending(FixKind.Universal), scala.None)
  }
