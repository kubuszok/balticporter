package balticporter.corpus.flexmark

import balticporter.runner.PortConfig

/** Port the EXTENSION half's own JUnit suite — the behavioural gate for milestone 2.
  *
  *   corpus/runMain balticporter.corpus.flexmark.FlexmarkExtTestMigrate [--determinism=full]
  *
  * The port is `balticporter/corpus/ports/ssg-md/ext-test.conf` — read that, not this file. What is
  * here is the `main` that names it and gives the run its report identity, so
  * `port-report/FlexmarkExtTestMigrate` is a baseline of its own and neither the base's census nor
  * `FlexmarkExtMigrate`'s can move because this ran (`CLAUDE.md` §2.1).
  *
  * ==Why an extension needs behavioural evidence MORE than a library does==
  * `FlexmarkExtMigrate` compiles at 0 errors, and `CLAUDE.md` §3 says what that is worth: the count
  * is typer-only and none of §4.4's forms moves it. An extension is a REGISTRATION mechanism — a
  * `ParserExtension` handed to a builder, a factory appended to a list, a handler consulted in a loop
  * — and every failure mode of one is SILENT: a parser built with an extension that registered
  * nothing renders the ninety-nine documents that do not use it perfectly, which is
  * `ENGINE-LIMITS.md` K22's shape read at a library's own extension point.
  *
  * `AsideParserTest` is the assertion that closes it. It builds a `Parser` with
  * `AsideExtension.create()` registered, reads `Parser.SPECIAL_LEAD_IN_HANDLERS` back out of the
  * BUILT options, and round-trips `escape`/`unEscape` through whatever handlers it finds there — so
  * a registration that did not take fails it, and nothing else in this milestone could.
  *
  * ==The corpus's first THREE-LINK base chain==
  * `ext-test.conf` declares `base = "ext.conf"`, which declares `base = "main.conf"`. Every earlier
  * dependent in this repository is one hop from its base. What that exercises is `extendedBy`
  * composing twice — `governs`, `packageRenames` and the base's two surface phases arrive through
  * two hops and this port appends `test-framework` to them — and `ManifestAgreement` reading a chain
  * rather than a pair.
  */
object FlexmarkExtTestMigrate:

  def main(args: Array[String]): Unit =
    FlexmarkTestClasspath.ensure(FlexmarkPort.repoRoot)
    PortConfig.load(FlexmarkPort.conf("ext-test.conf"), args.toSeq).execute()
