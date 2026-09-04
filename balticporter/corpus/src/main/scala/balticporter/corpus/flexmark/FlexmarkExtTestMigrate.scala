package balticporter.corpus.flexmark

import balticporter.runner.PortConfig

/** Port the EXTENSION half's own JUnit suite — the behavioural gate for milestone 2:
  * `.../ports/ssg-md/ext-test.conf`. An extension's failure mode is SILENT (a registration
  * that did not take renders every document not using it perfectly, K22's shape), so §3's
  * compile-is-not-the-gate rule applies with extra force; `AsideParserTest` closes it by
  * round-tripping through the BUILT options. First THREE-LINK base chain (ext-test -> ext -> main). */
object FlexmarkExtTestMigrate:

  def main(args: Array[String]): Unit =
    FlexmarkTestClasspath.ensure(FlexmarkPort.repoRoot)
    PortConfig.load(FlexmarkPort.conf("ext-test.conf"), args.toSeq).execute()
