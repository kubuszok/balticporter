package balticporter.tir

import balticporter.catalog.{ApiRows, Platform, Verdict}

/** `PortabilityCheck` as a §1(b) phase — the TARGET SET is the parameter, and this is what stops the
  * parameterisation from being a lane reset. */
class PortabilityTargetsSpec extends munit.FunSuite:

  private val All = Platform.values.toSet

  test("the default target set selects every UNPORTABILITY rule, in list order") {
    // `== all` is no longer the statement: the list also carries the `Verdict.Depend` rules, which
    // are the `dependency-coverage` lane's and never this one's. What must hold is that a port
    // declaring nothing gets every rule that IS an unportability, in `all`'s own order — the
    // promoted `portability(all)` baselines are byte-identical rather than merely equal in count.
    val lane = PortabilityCheck.rulesFor(All)
    assertEquals(lane, PortabilityCheck.all.filter(lane.contains))
    assertEquals(lane.toSet & PortabilityCheck.dependencyRulesFor(All).toSet,
                 Set.empty[PortabilityCheck.Rule])
  }

  test("an EMPTY target set is the no-op §1(b) asks for") {
    assertEquals(PortabilityCheck.rulesFor(Set.empty), Nil)
    // and it is deliberately NOT what any port gets by default — that is `PortManifest.targets`.
    assertEquals(balticporter.core.PortManifest(name = "x").targets, All)
  }

  test("a JVM-only port asks nothing: every rule is about a NON-JVM backend") {
    assertEquals(PortabilityCheck.rulesFor(Set(Platform.Jvm)), Nil)
  }

  test("a JVM+Native port loses EXACTLY the JS-only rules, and nothing else") {
    val jvmNative = Set(Platform.Jvm, Platform.ScalaNative)
    // over the PORTABILITY lane alone: the `Verdict.Depend` rules narrow by target too, and their
    // narrowing is `DependencyCoverageSpec`'s to assert.
    val dropped = (PortabilityCheck.rulesFor(All).map(_.api).toSet --
                   PortabilityCheck.rulesFor(jvmNative).map(_.api).toSet).toList.sorted
    assertEquals(dropped, List(
      // the eight re-scoped families…
      "java.lang.ProcessBuilder",
      "java.lang.System#getProperty",
      "java.lang.Thread",
      "java.net.",
      "java.nio.channels.",
      "java.nio.file.",
      "java.util.concurrent.",
      // …plus `getenv`, empty on JS and real on Native. `java.util.ServiceLoader` is deliberately
      // NOT here: its JS half leaves and its NATIVE half stays, under the same api, which is the
      // whole point of the split — the assertion below reads which of the two survived.
      "java.lang.System#getenv",
      // …and the one COLLECTION on the list, whose answer is a REFUSAL rather than a mapping:
      // JS's `WeakMap` cannot enumerate and Native has the real class.
      "java.util.WeakHashMap",
      "java.util.zip.",
    ).sorted)
    // the negative half: everything that stays is a rule Scala Native genuinely cannot answer —
    // reflection, class loading, javax, the JUnit/Hamcrest test vocabulary, the socket channels its
    // FILE-channel implementation does not cover, and the whole text/locale residue.
    val kept = PortabilityCheck.rulesFor(jvmNative).map(_.api)
    assert(kept.contains("java.lang.reflect."))
    assert(kept.contains("org.junit."))
    assert(kept.contains("java.nio.channels.SocketChannel"))
    assert(kept.contains("java.text.Collator"))
    // …and ServiceLoader is on NEITHER removal list any more: its row's non-JVM verdicts are
    // `Depend` on a cross-platform wrapper (DESIGN.md §8.19), so both halves moved to the
    // build-graph lane. The Native half is the one this port still asks, and it is asked THERE.
    assertEquals(PortabilityCheck.rulesFor(jvmNative).filter(_.api == "java.util.ServiceLoader"), Nil)
    val loaders = PortabilityCheck.dependencyRulesFor(jvmNative).filter(_.api == "java.util.ServiceLoader")
    assertEquals(loaders.size, 1)
    assert(clue(loaders.head.why).contains("LINK time"))
  }

  test("a Scala.js-only port loses only the NATIVE-only rule — the ServiceLoader split's other half") {
    val js      = Set(Platform.ScalaJs)
    val dropped = PortabilityCheck.all.filterNot(_.asks(js))
    assertEquals(dropped.map(_.api), List("java.util.ServiceLoader"))
    assert(clue(dropped.head.why).contains("LINK time"))
    // …and the JS half is the one it keeps — as a DEPENDENCY rule, which is where both halves went
    // the day the row gained an artifact. The removal lane must not still be asking: a reader told
    // to delete a call that one `libraryDependencies` line plus a redirect makes correct is the
    // unanswerable finding `dependency-coverage` exists to stop.
    assertEquals(PortabilityCheck.rulesFor(js).filter(_.api == "java.util.ServiceLoader"), Nil)
    val kept = PortabilityCheck.dependencyRulesFor(js).filter(_.api == "java.util.ServiceLoader")
    assertEquals(kept.size, 1)
    assert(clue(kept.head.why).contains("does not exist"))
  }

  test("a rule that SPLITS a family precedes it, or its `why` never reaches a reader") {
    // `find` takes the first match. A member-level exception listed AFTER its family prefix is a
    // rule that can only fire for a port targeting the one platform the prefix was re-scoped away
    // from — which is not what "Native's channels are FILE channels only" is there to say.
    def idx(api: String) = PortabilityCheck.all.indexWhere(_.api == api)
    assert(idx("java.nio.channels.SocketChannel") < idx("java.nio.channels."))
    assert(idx("java.nio.channels.ServerSocketChannel") < idx("java.nio.channels."))
    assert(idx("java.net.IDN") < idx("java.net."))
    // and the JS half of the ServiceLoader split precedes the Native half, so a port targeting
    // both is told the thing that stops it dead rather than the thing that merely needs a resource.
    val loaders = PortabilityCheck.all.zipWithIndex.filter(_._1.api == "java.util.ServiceLoader")
    assertEquals(loaders.size, 2)
    assertEquals(loaders.head._1.on, PortabilityCheck.Rule.JsOnly)
  }

  test("the rules the RESEARCH found missing are all present, each citing its row") {
    val missing = List(
      "java.lang.System#getenv", "java.nio.channels.SocketChannel", "java.nio.channels.ServerSocketChannel",
      "java.net.IDN", "java.text.MessageFormat", "java.text.Collator", "java.text.BreakIterator",
      "java.util.Calendar", "java.util.GregorianCalendar", "java.util.TimeZone",
    ).filterNot(a => PortabilityCheck.all.exists(_.api == a))
    assertEquals(missing, Nil, missing.mkString(", "))
    // every one of them cites a row — the availability claim and its version anchor live there,
    // never in the `why`.
    val uncited = PortabilityCheck.all.filter(r => r.api.startsWith("java.text.") || r.api == "java.util.Calendar")
      .filter(_.at.isEmpty).map(_.api)
    assertEquals(uncited, Nil)
  }

  test("the text/locale rules do NOT reach the classes an ARTIFACT supplies") {
    // The area's twenty rows are mostly a DEPENDENCY (`scala-java-time`, `scala-java-locales`), and
    // a dependency reported as an unportability is a finding the reader cannot act on: they are
    // told to remove a call that a one-line `libraryDependencies` entry makes correct. So the
    // refusals here are the RESIDUE — the classes no surveyed source tree implements — and
    // `java.time`, `Locale`, `DecimalFormat` and `SimpleDateFormat` must NOT be on this list.
    val overreach = List("java.time.Instant", "java.util.Locale", "java.text.DecimalFormat",
                         "java.text.SimpleDateFormat", "java.util.Currency", "java.util.Date",
                         "java.nio.charset.StandardCharsets", "java.util.Formatter")
      .filter(fqn => PortabilityCheck.rulesFor(All).exists(r => !r.exactMember && PortabilityCheck.names(r, fqn)))
    assertEquals(overreach, Nil, s"reported as unportable, but supplied by an artifact: $overreach")
  }

  test("no rule claims a platform its own catalog row calls Keep") {
    val bad = PortabilityCheck.all.flatMap { r =>
      PortabilityCheck.rowOf(r).toList.flatMap { row =>
        r.on.toList.sortBy(_.toString).collect {
          case p if row.verdict(p) == Verdict.Keep =>
            s"${r.api} claims $p, but ${row.id} (${row.fqn}) says Keep there"
        }
      }
    }
    assertEquals(bad, Nil, bad.mkString("\n"))
  }

  test("every cited row EXISTS — a citation naming nothing is worse than none") {
    val missing = PortabilityCheck.all.flatMap(r => r.at.filterNot(ApiRows.byId.contains).map(id => s"${r.api} -> $id"))
    assertEquals(missing, Nil, missing.mkString("\n"))
  }

  test("the guard has TEETH — a rule claiming a Keep platform is rejected") {
    // `java.nio.file.` cites JS-P10, whose Native verdict is Keep. The re-scoped rule claims JS
    // alone; the pre-re-scoping shape claimed both, and this is the assertion that would have
    // caught it.
    val row = ApiRows.byId(PortabilityCheck.all.find(_.api == "java.nio.file.").flatMap(_.at).get)
    assertEquals(row.verdict(Platform.ScalaNative), Verdict.Keep)
    assertEquals(row.verdict(Platform.ScalaJs).actionable, true)
  }

  test("a prefix rule cuts at a SEPARATOR — java.lang.Thread is not java.lang.ThreadLocal") {
    val thread = PortabilityCheck.all.find(_.api == "java.lang.Thread").get
    assert(PortabilityCheck.names(thread, "java.lang.Thread"))
    assert(!PortabilityCheck.names(thread, "java.lang.ThreadLocal"),
      "`startsWith` covered ThreadLocal, which Scala.js implements — §4.56's own hazard, live")
    val file = PortabilityCheck.all.find(_.api == "java.nio.file.").get
    assert(PortabilityCheck.names(file, "java.nio.file.Path"))
    assert(PortabilityCheck.names(file, "java.nio.file.attribute.FileTime"))
    assert(!PortabilityCheck.names(file, "java.nio.filesystem.Whatever"))
    // a nested type is reached across `$`, which is the separator the cut is written in terms of.
    val pb = PortabilityCheck.all.find(_.api == "java.lang.ProcessBuilder").get
    assert(PortabilityCheck.names(pb, "java.lang.ProcessBuilder$Redirect"))
  }
