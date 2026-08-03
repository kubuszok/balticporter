package balticporter.corpus.demo

/** The per-library policy the BIR SCOUTS share — stated HERE, in the porting programs, because
  * `frontend-spoon` may not name a ported library or its dependencies (CLAUDE.md §1).
  *
  * These programs survey ssg's java libraries (liqp, xwiki, flexmark) and sge's jbump. What they
  * have in common is a JVM-FAITHFUL disposition: an annotation that a framework reads at run time
  * is behaviour and must survive the port, even though a cross-platform port would substitute it
  * instead (ssg's own disposition for jackson).
  */
object ScoutPolicy:

  /** annotation packages these surveys carry through to the output verbatim.
    *
    *   - `org.junit.` / `junit.` — a suite's annotations ARE the suite; dropped, a translated test
    *     file is a class of ordinary methods that nothing runs;
    *   - `com.fasterxml.jackson.` — behaviour-bearing on the JVM: liqp's custom serializers drive
    *     its eager-render path off exactly these.
    *
    * Nothing else survives: a nullness hint is advisory (`org.jetbrains.annotations.`), and an
    * annotation nobody claims is reported by the frontend rather than silently kept.
    */
  val PreservedAnnotationPrefixes: List[String] =
    List("org.junit.", "junit.", "com.fasterxml.jackson.")
