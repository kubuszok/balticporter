package balticporter.core

/** Which JVM this run is — the JDK is an input to the emitted text (external symbols resolve out of its class files), so it is recorded rather than assumed. `sbt --client`'s server JVM is fixed at
  * startup, so a `JAVA_HOME` export never reaches the forked migration.
  */
object JvmInfo:

  /** the JDK specification this JVM implements — `"22"`, `"24"`. The COMPARABLE half. */
  def specification: String = prop("java.specification.version")

  /** the full build string — `"22.0.2"`. Reported, never compared. */
  def version: String = prop("java.version")

  /** who built it — `"GraalVM Community"`, `"Azul Systems, Inc."`. Reported, never compared. */
  def vendor: String = prop("java.vendor")

  /** where it lives. The one field that says WHICH of several installed JDKs answered. */
  def home: String = prop("java.home")

  /** `run-latest/jvm.txt` — a `key\tvalue` file, one fact per line, in a fixed order: extracted by field, not by regex, for `findings.tsv`. Not promoted to a baseline — a JDK path is machine-specific
    * and a committed one would be unreproducible.
    */
  def render: String =
    List(
      "specification" -> specification,
      "version" -> version,
      "vendor" -> vendor,
      "home" -> home
    ).map((k, v) => s"$k\t$v").mkString("", "\n", "\n")

  /** A system property that is absent is `?`, never `""`: an empty spec-version would read to `PortMap.freshness` as "published before the field existed" (do not compare). `?` compares unequal to
    * every real version — the honest direction.
    */
  private def prop(k: String): String =
    Option(System.getProperty(k)).map(_.trim).filter(_.nonEmpty).getOrElse("?")
