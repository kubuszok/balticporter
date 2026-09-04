package balticporter.corpus.demo

/** The per-library policy the BIR SCOUTS share — stated HERE since `frontend-spoon` may not name
  * a ported library or its dependencies (CLAUDE.md §1). Surveys ssg's java libraries (liqp,
  * xwiki, flexmark) and sge's jbump; common thread is a JVM-FAITHFUL disposition — an annotation
  * a framework reads at run time is behaviour and must survive the port, even where a
  * cross-platform port would substitute it (ssg's own disposition for jackson). */
object ScoutPolicy:

  /** annotation packages these surveys carry through to the output verbatim: `org.junit.`/`junit.`
    * (a suite's annotations ARE the suite), `com.fasterxml.jackson.` (behaviour-bearing on the
    * JVM — liqp's custom serializers drive its eager-render path off exactly these). Nothing else
    * survives: a nullness hint is advisory, and an unclaimed annotation is reported by the
    * frontend rather than silently kept. */
  val PreservedAnnotationPrefixes: List[String] =
    List("org.junit.", "junit.", "com.fasterxml.jackson.")
