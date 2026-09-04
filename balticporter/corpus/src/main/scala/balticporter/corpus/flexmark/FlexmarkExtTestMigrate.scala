package balticporter.corpus.flexmark

import balticporter.runner.PortConfig

/** Port the EXTENSION half's own JUnit suite — the behavioural gate for milestone 2.
  *
  *   corpus/runMain balticporter.corpus.flexmark.FlexmarkExtTestMigrate [--determinism=full]
  *
  * The port is `balticporter/corpus/ports/ssg-md/ext-test.conf` -- read that, not this file. An
  * extension's failure mode is SILENT (a registration that did not take renders every document
  * that doesn't use it perfectly, `ENGINE-LIMITS.md` K22's shape at a library's own extension
  * point), so `CLAUDE.md` §3's compile-is-not-the-gate rule applies with extra force here;
  * `AsideParserTest` closes it by round-tripping through the BUILT options a registered extension
  * produced. The corpus's first THREE-LINK base chain (`ext-test.conf` -> `ext.conf` ->
  * `main.conf`), exercising `extendedBy` composed twice and `ManifestAgreement` over a chain.
  */
object FlexmarkExtTestMigrate:

  def main(args: Array[String]): Unit =
    FlexmarkTestClasspath.ensure(FlexmarkPort.repoRoot)
    PortConfig.load(FlexmarkPort.conf("ext-test.conf"), args.toSeq).execute()
