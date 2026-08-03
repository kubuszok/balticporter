package balticporter.catalog

import java.nio.file.{Files, Path}

/** STATUS ENFORCEMENT RULE (i): **a row whose twin names a CLOSED `ENGINE-LIMITS.md` entry may not
  * claim `Open`.**
  *
  * Why it is a spec and not a habit: four of the catalog's headline `Open` rows were FIXED inside
  * one week while the document describing them was being written, and nothing could see that they
  * had. All four would have fallen out of a single question asked of `ENGINE-LIMITS.md`, which is
  * maintained in the commit that measures — so the twin column is more current than the status
  * column by construction, and this spec is what makes preferring it mechanical rather than advisory.
  *
  * It reads the committed file, because the stable ids ARE the citation surface and the CLOSED
  * marker is already there. No new artifact, no generation step. `scripts/catalog-status.sh` is the
  * same rule for an agent holding a checkout rather than a test runner, and the two must agree —
  * they read the same file by the same shape.
  *
  * ITS HONEST LIMIT, stated because an over-claimed guarantee is how a mechanism stops being
  * audited: it sees a row only THROUGH its twin. A row with `Twin.Predicted` is invisible to it,
  * which is why `Twin.NoTwin` is refused for an `Open` row below — a row claiming to be open owes a
  * pointer at the record that would contradict it. And a twin CLOSED on one face and open on another
  * is `AMBIGUOUS` rather than a verdict; the spec reports those and does not fail on them, because a
  * half-closed entry is a human's call by construction.
  *
  * NOT SKIPPABLE. If the file cannot be found the spec FAILS. `CLAUDE.md` §5.1: an `assume`-guarded
  * spec whose precondition is an artifact is one nobody is running, and it can go stale for as long
  * as nobody happens to produce that artifact first. `ENGINE-LIMITS.md` is committed at the
  * repository root, so a checkout always has it and its absence is a real failure. */
class ClosedTwinStatusSpec extends munit.FunSuite:

  /** Walk up from the working directory. A forked test JVM's cwd is the SUBPROJECT, not the
    * repository root, so a relative path with a fixed number of `..` segments is a path that breaks
    * the next time a module moves — which is exactly what the repository restructure did. */
  private lazy val limits: Path =
    def up(p: Path, fuel: Int): Option[Path] =
      if fuel == 0 || p == null then scala.None
      else
        val c = p.resolve("ENGINE-LIMITS.md")
        if Files.isRegularFile(c) then Some(c) else up(p.getParent, fuel - 1)
    up(Path.of("").toAbsolutePath, 8).getOrElse(
      fail("ENGINE-LIMITS.md was not found above " + Path.of("").toAbsolutePath +
        " — this spec reads the committed file and must never be skipped for its absence"))

  /** id -> CLOSED / OPEN / AMBIGUOUS / UNMARKED, from the file's own shape: `### <ID>. <heading>`
    * (the trailing dot is optional — several ids are written `### K5.6 A cast …`), and the marker is
    * read from the heading OR from the entry's first paragraph, because the file uses both
    * conventions.
    *
    * The token is matched in UPPER CASE only, deliberately: one entry's heading reads "OPEN; the
    * counting is closed, the pipeline is not", and a case-insensitive match would call the one entry
    * that says it is open, closed. */
  private lazy val verdicts: Map[String, String] =
    val lines = Files.readAllLines(limits).toArray(Array.empty[String]).toList
    val out   = collection.mutable.LinkedHashMap.empty[String, String]
    var id    = ""
    var head  = ""
    var para  = ""
    var seen  = false
    def flush(): Unit =
      if id.nonEmpty then
        val c = head.contains("CLOSED") || para.contains("CLOSED")
        val o = head.contains("OPEN") || para.contains("OPEN")
        out(id) = if c && o then "AMBIGUOUS" else if c then "CLOSED" else if o then "OPEN" else "UNMARKED"
      id = ""; head = ""; para = ""; seen = false
    for line <- lines do
      if line.startsWith("### ") then
        flush()
        head = line.drop(4)
        id = head.takeWhile(c => !c.isWhitespace).stripSuffix(".")
      else if line.startsWith("#") then flush()
      else if id.nonEmpty && !seen then
        if line.isBlank then { if para.nonEmpty then seen = true }
        else para = para + " " + line
    flush()
    out.toMap

  private def engineLimitTwins: List[(DiffId, String, Status)] =
    Differences.all.collect { case d if d.twin.isInstanceOf[Twin.EngineLimit] =>
      (d.id, d.twin.asInstanceOf[Twin.EngineLimit].id, d.status)
    }

  test("the ENGINE-LIMITS parse finds the ids the catalog actually cites, and reads their markers") {
    // A parser that silently found nothing would make every assertion below vacuously true, which is
    // the failure mode of every text-scanning check. Pin the shape, not a total: totals go stale.
    assert(verdicts.sizeIs > 100, s"only ${verdicts.size} entries parsed — the heading shape has moved")
    assertEquals(verdicts.get("F5"), Some("CLOSED"))
    assertEquals(verdicts.get("F6"), Some("CLOSED"))
    assertEquals(verdicts.get("G22"), Some("CLOSED"))
    assertEquals(verdicts.get("C12"), Some("CLOSED"))
    assertEquals(verdicts.get("G24"), Some("OPEN"))
    assertEquals(verdicts.get("K17"), Some("OPEN"))
    // UNMARKED is a real third state, not a parse failure: this entry describes a shipped fix and a
    // residue counted to zero without ever using the word, and calling that CLOSED would be the
    // spec inventing the claim it exists to check.
    assertEquals(verdicts.get("F1"), Some("UNMARKED"))
  }

  test("every cited ENGINE-LIMITS twin RESOLVES — a citation to nothing makes a status unfalsifiable") {
    val dangling = engineLimitTwins.collect { case (id, t, _) if !verdicts.contains(t) => s"$id cites `$t`" }
    assertEquals(dangling, Nil, dangling.mkString("\n"))
  }

  test("rule (i): no row claims Open against a twin that reads CLOSED") {
    val stale = engineLimitTwins.collect {
      case (id, t, s) if s.isOpen && verdicts(t) == "CLOSED" => s"$id claims Open, but twin $t reads CLOSED"
    }
    assertEquals(stale, Nil, stale.mkString("\n"))
  }

  test("a row claiming Open carries a twin — the rule above can only see a row that has one") {
    val untwinned = Differences.all.collect { case d if d.status.isOpen && d.twin == Twin.NoTwin => d.id.toString }
    assertEquals(untwinned, Nil,
      s"an Open row owes a pointer at the record that would contradict it: ${untwinned.mkString(", ")}")
  }

  // -------------------------------------------------------------------------------------------
  // registry integrity — the invariants that keep an id meaning one thing forever
  // -------------------------------------------------------------------------------------------

  test("ids are unique, and a RETIRED id is never reused") {
    val dupes = Differences.all.groupBy(_.id).filter(_._2.sizeIs > 1).keys.toList.map(_.toString).sorted
    assertEquals(dupes, Nil, dupes.mkString(", "))
    val revived = Differences.retired.map(_.id).filter(Differences.byId.contains).map(_.toString).sorted
    assertEquals(revived, Nil, s"a retired id is out of circulation FOREVER: ${revived.mkString(", ")}")
    val absorbedIntoNothing = Differences.retired.flatMap(_.into).filterNot(Differences.byId.contains).map(_.toString)
    assertEquals(absorbedIntoNothing, Nil, s"absorbed into a row that does not exist: ${absorbedIntoNothing.mkString(", ")}")
    val apiDupes = ApiRows.all.groupBy(_.id).filter(_._2.sizeIs > 1).keys.toList.map(_.toString).sorted
    assertEquals(apiDupes, Nil, apiDupes.mkString(", "))
  }

  test("a language row is JS-{E,S,C,G} and an API row is JS-{L,P} — the two shapes never mix") {
    assertEquals(Differences.all.map(_.id.area).distinct.sortBy(_.ordinal), List(Area.E, Area.S, Area.C, Area.G))
    assertEquals(ApiRows.all.map(_.id.area).distinct.sortBy(_.ordinal), List(Area.L, Area.P))
  }

  test("evidence names a SYMBOL, never a line number") {
    // This pass alone re-read a dozen line numbers that had moved. A registry of stale line numbers
    // is worse than no registry: it reads as precision and sends the reader to the wrong member.
    val lined = Differences.all.filter(d => d.evidence.matches(""".*[:.]\d{2,}.*""")).map(d => s"${d.id}: ${d.evidence}")
    assertEquals(lined, Nil, lined.mkString("\n"))
  }

  test("every row says something in every field — an empty citation is a row that was never finished") {
    val bad = Differences.all.flatMap { d =>
      List(
        Option.when(d.title.trim.isEmpty)(s"${d.id} has no title"),
        Option.when(d.jls.trim.isEmpty)(s"${d.id} has no JLS citation"),
        Option.when(d.scala.trim.isEmpty)(s"${d.id} has no Scala-side citation"),
        Option.when(d.evidence.trim.isEmpty)(s"${d.id} has no evidence"),
      ).flatten
    }
    assertEquals(bad, Nil, bad.mkString("\n"))
  }

  test("the Scala-side citation GAP is data, and its size is printed rather than asserted") {
    // `UNCITED — ` is a real state: for many of these rules there is a Scala 3 reference page and we
    // did not locate it, and for some there is genuinely no normative text. Making it a prefix means
    // the gap is a number that can go DOWN, instead of a sentence in a document nobody re-reads. It
    // is deliberately not a failing assertion — a spec that failed on it would be a spec someone
    // silences by inventing a citation, which is worse than the gap.
    val uncited = Differences.all.count(_.scala.startsWith("UNCITED"))
    println(s"[catalog] Scala-side citation gap: $uncited of ${Differences.all.size} language rows")
    assert(uncited <= Differences.all.size)
  }
