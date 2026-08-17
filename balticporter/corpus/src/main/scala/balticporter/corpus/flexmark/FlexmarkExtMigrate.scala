package balticporter.corpus.flexmark

import balticporter.runner.PortConfig

/** Migrate flexmark's EXTENSION half — milestone 2, as ONE dependent port of [[FlexmarkMigrate]].
  *
  *   corpus/runMain balticporter.corpus.flexmark.FlexmarkExtMigrate [--determinism=full]
  *
  * The port is `balticporter/corpus/ports/ssg-md/ext.conf` — read that, not this file. What is here
  * is the `main` that names the configuration and gives the run its report identity: `CheckReport.dir`
  * is derived from the main class's simple name, so `port-report/FlexmarkExtMigrate` is a baseline of
  * its own and neither ssg-md MAIN's census (`PROGRESS.md` §10.6.3) nor its suite's outcomes can move
  * because this ran (`CLAUDE.md` §2.1).
  *
  * ==THE IDENTITY IS STABLE ACROSS THE BATCH WAVES, WHICH IS THE POINT OF IT==
  * Milestone 2 admits the 29 covered extension modules a wave at a time, and a wave is one line in
  * `ext.conf`'s `includeGlobs`. `CLAUDE.md` §2.1's third exemption is exactly this: the report
  * directory is the MEASUREMENT identity, so it must not move when the scope does — a baseline whose
  * directory changes with every wave is a baseline nobody can diff across one. There is therefore no
  * `FlexmarkExtAsideMigrate`, and there will be no `FlexmarkExtTablesMigrate`.
  *
  * ==Why the extensions are ONE dependent port and not twenty-nine==
  * `ext.conf`'s D-mde-1 argues it in full; the mechanical half is that a run's emission identity is
  * the pair (`portRoot`, `sourceSet`), `SourceSet` is `main | test`, and `PortRun` opens its emission
  * with an unconditional `wipe(emitDir)` — so `ported/ssg-md` has two emission slots and the base
  * port holds both. The reference hand port puts all twenty-five of its extension packages inside the
  * ONE `ssg-md` module (`../ssg/ssg-md/src/main/scala/ssg/md/ext/`), so twenty-nine port roots would
  * be twenty-nine names for a module `../ssg/build.sbt` does not have.
  *
  * ==What this port exercises that no earlier one did==
  * ssg-md is the first corpus library whose dependent is a dependent of a MULTI-MODULE base with a
  * single package root. Ashley and gdx-gltf each resolve against one libGDX tree; `test.conf`
  * resolves against twelve but converts a source set rather than a library. This converts LIBRARY
  * code that extends the base's own extension points — `Parser.ParserExtension`,
  * `HtmlRenderer.HtmlRendererExtension`, `Formatter.FormatterExtension`, `BlockParserFactory`,
  * `NodeRendererFactory`, `NodeFormatterFactory` — so what it measures is whether the shared surface
  * the base emitted is one a third party can implement against. That is the question `base-surface`
  * exists to ask, and this is the first port on this corpus with real content for it.
  *
  * The classpath is [[FlexmarkClasspath]]'s, unchanged: every `flexmark-ext-*` pom inherits the same
  * compile-scope `org.jetbrains:annotations`, and the two halves of one module must not be modelled
  * against two different jars.
  */
object FlexmarkExtMigrate:

  def main(args: Array[String]): Unit =
    FlexmarkClasspath.ensure(FlexmarkPort.repoRoot)
    PortConfig.load(FlexmarkPort.conf("ext.conf"), args.toSeq).execute()
