package balticporter.catalog

/** Renders the registry as markdown — `just catalog`.
  *
  * THE OUTPUT IS A BUILD PRODUCT, and that is the whole design. `CLAUDE.md` §5.5 says emitted code
  * lives in a gitignored directory because a `git status` that cannot tell a DECISION from an
  * ARTEFACT defeats the measurement discipline; a generated document is the same thing one medium
  * over. Committed, this markdown would be a seventh document nobody loads (§3.6), it would accrete
  * a status section, and it would start disagreeing with the code it was generated from. So it goes
  * to `.balticporter/`, which is gitignored, and the code stays the single truth.
  *
  * It writes to stdout and lets the caller redirect, because a renderer that owns a path is a
  * renderer with a second opinion about where the answer lives. */
object CatalogDoc:

  def render: String =
    val sb = StringBuilder()
    sb ++= "# The difference catalog\n\n"
    sb ++= "GENERATED from `balticporter.catalog` — a build product, never committed (CLAUDE.md §5.5).\n"
    sb ++= "Edit the registry; regenerate with `just catalog`.\n\n"

    for (area, rows) <- Differences.all.groupBy(_.id.area).toList.sortBy(_._1.ordinal) do
      sb ++= s"## JS-$area — ${rows.size} rows\n\n"
      sb ++= "| id | title | sev | status | twin | fix | evidence |\n|---|---|---|---|---|---|---|\n"
      for d <- rows.sortBy(_.id.n) do
        sb ++= s"| ${d.id} | ${d.title} | ${short(d.severity)} | ${short(d.status)} | ${short(d.twin)} | ${short(d.fix)} | ${d.evidence} |\n"
      sb ++= "\n"

    sb ++= s"## Retired ids — ${Differences.retired.size}\n\n"
    sb ++= "| id | absorbed into | why |\n|---|---|---|\n"
    for r <- Differences.retired do
      sb ++= s"| ${r.id} | ${r.into.fold("—")(_.toString)} | ${r.why} |\n"
    sb ++= "\n"

    for (area, rows) <- ApiRows.all.groupBy(_.id.area).toList.sortBy(_._1.ordinal) do
      sb ++= s"## JS-$area — ${rows.size} rows\n\n"
      sb ++= "| id | fqn | JVM | Scala.js | Scala Native | asOf | why |\n|---|---|---|---|---|---|---|\n"
      for r <- rows.sortBy(_.id.n) do
        def cell(p: Platform) = s"${short(r.by(p))} / ${short(r.verdict(p))}"
        val asOf = if r.asOf.isEmpty then "—" else r.asOf.toList.sorted.map((k, v) => s"$k=$v").mkString("; ")
        sb ++= s"| ${r.id} | `${r.fqn}`${if r.exact then " (exact)" else ""} | ${cell(Platform.Jvm)} | " +
          s"${cell(Platform.ScalaJs)} | ${cell(Platform.ScalaNative)} | $asOf | ${r.why} |\n"
      sb ++= "\n"
    sb.result()

  /** an enum case as its own name plus its parameters, without the `Status.` noise a table does not
    * need. Derived from the value, never from a second table of strings. */
  private def short(v: Any): String = v match
    case p: Product if p.productArity > 0 => s"${p.productPrefix}(${p.productIterator.map(short).mkString(", ")})"
    case s: String                        => s
    case other                            => other.toString

  def main(args: Array[String]): Unit = print(render)
