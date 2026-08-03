package balticporter.catalog

/** The `JS-{L,P}` half of the (a)/(b) line — NARROWER than `DifferenceTakesNoParameterSpec` rather
  * than absent.
  *
  * An [[ApiRow]] carries maps on purpose: Scala.js and Scala Native disagree on nine families, and
  * a single shared verdict is precisely what makes several of the engine's existing portability
  * rules wrong for one of them. So "every field is a literal" cannot be the rule here. What CAN be
  * the rule, and is:
  *
  *   - a map's KEYS are [[Platform]]s or version-name strings, never anything a port chose;
  *   - a map's VALUES are literals or enum cases, recursively;
  *   - NO field is a `Set`, a predicate, or a `RuleScope` — a TARGET SET is the specific thing that
  *     must never appear, because a row says what is true OF A PLATFORM and which platforms a port
  *     cares about is the port's own answer, in its manifest.
  *
  * Plus the two invariants that make the version half honest: a `why` is never empty, and a row with
  * an EMPTY `asOf` must say `UNSTATED` in its `why`. A coverage claim with no version goes stale on
  * the next release; a claim that cannot say when it was true is one nothing can ever re-check, and
  * the only defence is making the absence visible in the row's own sentence. */
class ApiRowCarriesNoPolicySpec extends munit.FunSuite:

  private def literalOnly(v: Any): Option[String] = v match
    case _: String | _: Int | _: Long | _: Boolean | _: Char | _: Double | _: Float | _: Short | _: Byte =>
      scala.None
    case s: Set[?]                                                   => Some(s"a SET — a target set is exactly what a row may not carry ($s)")
    case _: Function0[?] | _: Function1[?, ?] | _: Function2[?, ?, ?] => Some("a predicate")
    case c: scala.collection.Iterable[?]                             => Some(s"a collection (${c.getClass.getName})")
    case p: Product                                                  => p.productIterator.map(literalOnly).collectFirst { case Some(x) => x }
    case _: reflect.Enum                                             => scala.None
    case other                                                       => Some(s"not a literal (${other.getClass.getName})")

  private def keyedMap(name: String, m: Map[?, ?]): Option[String] =
    val badKey = m.keys.collectFirst {
      case k if !k.isInstanceOf[Platform] && !k.isInstanceOf[String] => s"$name has a key that is neither a Platform nor a version name: $k"
    }
    badKey.orElse(m.values.map(literalOnly).collectFirst { case Some(x) => s"$name holds $x" })

  private def offending(name: String, v: Any): Option[String] = v match
    case m: Map[?, ?] => keyedMap(name, m)
    case other        => literalOnly(other).map(x => s"$name is $x")

  test("no JS-{L,P} row carries a target set, a predicate or a scope — only Platform-keyed facts") {
    val bad = ApiRows.all.flatMap { r =>
      r.productIterator.zip(r.productElementNames).flatMap((v, n) => offending(s"${r.id}.$n", v))
    }
    assertEquals(bad, Nil, bad.mkString("\n"))
  }

  test("every platform is answered on both halves — an unanswered platform is a silent Keep") {
    // A missing key would make `by(p)` throw at the one moment a consumer needed the answer, and
    // a defaulted one would silently claim the API is fine there. Both are worse than a row that
    // says Absent, so every row answers all three explicitly.
    val bad = ApiRows.all.flatMap { r =>
      Platform.values.toList.flatMap { p =>
        List(
          Option.when(!r.by.contains(p))(s"${r.id} has no availability for $p"),
          Option.when(!r.verdict.contains(p))(s"${r.id} has no verdict for $p"),
        ).flatten
      }
    }
    assertEquals(bad, Nil, bad.mkString("\n"))
  }

  test("a row with no version anchor SAYS SO — `asOf` empty implies UNSTATED in its own sentence") {
    val silent = ApiRows.all.filter(r => r.asOf.isEmpty && !r.why.contains("UNSTATED")).map(_.id.toString)
    assertEquals(silent, Nil,
      s"a coverage claim with no version is one nothing can re-check; say UNSTATED: ${silent.mkString(", ")}")
    val empty = ApiRows.all.filter(_.why.trim.isEmpty).map(_.id.toString)
    assertEquals(empty, Nil, s"a row with no reason is a row nobody can audit: ${empty.mkString(", ")}")
  }

  test("no two rows claim the same API — one fqn, one answer") {
    // Two rows for one `fqn` is two verdicts for one question, and whichever a consumer reads first
    // wins silently. The library and platform halves overlap by subject (`java.util.concurrent`,
    // `java.lang.Thread`, `java.nio.charset`), and this is what forces that overlap to be resolved
    // rather than duplicated.
    val dupes = ApiRows.all.groupBy(r => (r.fqn, r.exact)).filter(_._2.sizeIs > 1)
      .map((k, v) => s"${k._1} claimed by ${v.map(_.id).mkString(", ")}").toList.sorted
    assertEquals(dupes, Nil, dupes.mkString("\n"))
  }

  test("the guard has TEETH — a target set and a predicate are both rejected") {
    assert(offending("x", Set(Platform.Jvm)).isDefined)
    assert(offending("x", (p: Platform) => true).isDefined)
    assert(offending("x", Map("k" -> Set("a"))).isDefined)
    assertEquals(offending("x", Map(Platform.Jvm -> Availability.Full)), scala.None)
    assertEquals(offending("x", Map("scala-js" -> "1.22.0")), scala.None)
    assertEquals(offending("x", Verdict.Depend(ArtifactDep("o", "n", "1.0"))), scala.None)
  }
