package balticporter.core

/** WHICH JVM THIS RUN IS — because the JDK is an INPUT to the emitted text, and nothing recorded it.
  *
  * ==The measurement that made this a rule==
  * The frontend resolves an EXTERNAL symbol's parents, members and modifiers out of a CLASS FILE,
  * and which class file it reads is decided by the JDK the migration JVM happens to be. So the
  * emitted Scala is a function of the JDK, exactly as it is a function of the manifest and of the
  * engine — and until this existed, the JDK was the one input to that function that no artifact
  * named.
  *
  * Measured 2026-08-27: a migration JVM on GraalVM **24** (a launchd job with no `JAVA_HOME`, so
  * `/usr/bin/java` asks `java_home` for the NEWEST JDK) emitted `override def getChars` on
  * `sge.utils.CharArray`, where the same sources under JDK **22** emit no `override` at all —
  * `java.lang.CharSequence` gained `getChars` in 23. `scala-cli` then compiled on 22 and reported
  * `E037 … overrides nothing`. Every check count was flat, every finding identical, the port map's
  * three fingerprints (`engine=`, `sources=`, `policy=`) all matched: the engine, the java and the
  * policy really were unchanged, and the OUTPUT was not.
  *
  * ==Why it is recorded rather than pinned==
  * A lane cannot force the frontend's JVM. `sbt -client` talks to a long-running server whose JVM
  * was chosen when the server started, so a `JAVA_HOME` exported by a measure recipe never reaches
  * the forked migration — the same boundary CLAUDE.md §4.6 records for a `-D` flag, and the same
  * remedy: something the run WRITES crosses it where an environment variable does not. So the run
  * records what it actually ran on, the lane reads that back beside the JDK its compiler will use,
  * and `jdk_guard` fails when the two specification versions disagree.
  *
  * ==What is compared, and what is only reported==
  * [[specification]] — `java.specification.version`, `"22"` / `"24"` — is the comparable half: it is
  * what decides which members `java.lang.CharSequence` has, and two builds of one specification
  * agree about that. [[version]], [[vendor]] and [[home]] are reported and never compared: a lane
  * pinned to a coursier-managed JDK and an sbt server on the vendor's own build of the SAME
  * specification are not a defect, and failing on them would be a guard nobody could satisfy.
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

  /** `run-latest/jvm.txt` — a `key\tvalue` file, one fact per line, in a fixed order.
    *
    * TSV and not prose for `findings.tsv`'s reason: a reader is a shell guard, and a value is
    * extracted by field rather than by a regex over a sentence. It is NOT promoted to a baseline
    * (`just baseline-accept` enumerates what it copies) — a JDK path is machine-specific, and a
    * committed one would be a baseline no second machine could reproduce (§5.4's shape). */
  def render: String =
    List(
      "specification" -> specification,
      "version"       -> version,
      "vendor"        -> vendor,
      "home"          -> home,
    ).map((k, v) => s"$k\t$v").mkString("", "\n", "\n")

  /** A system property that is ABSENT is `?`, never `""`.
    *
    * CLAUDE.md §4.6: a default a caller cannot distinguish from a real answer is a fabricated fact,
    * and an empty specification version would read to `PortMap.freshness` as "this map was published
    * before the field existed" — the one value that means DO NOT COMPARE. `?` compares unequal to
    * every real version, which is the honest direction for a JVM that would not say. */
  private def prop(k: String): String =
    Option(System.getProperty(k)).map(_.trim).filter(_.nonEmpty).getOrElse("?")
