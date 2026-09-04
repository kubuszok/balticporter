package balticporter.corpus.noise4j

import balticporter.runner.PortConfig

import java.nio.file.Path

/** Migrate **noise4j** (`src`, 12 types -- a dependency-free procedural map-generation library).
  *
  *   corpus/runMain balticporter.corpus.noise4j.Noise4jMigrate [--determinism=full]
  *
  * The whole port is `balticporter/corpus/ports/noise4j/main.conf` -- read that, not this file.
  * This `main` only names it and gives the run its report identity.
  *
  * The first corpus library with **Java enum constant bodies** (an abstract enum method overridden
  * per constant), in three independent shapes -- Scala 3's `enum` has no per-case-body counterpart
  * at all, so the reference hand port REWROTE each into an ordinary `enum` plus a `this match`, a
  * redesign no mechanical engine may copy. Also exercises: an interface CONSTANT read unqualified
  * from an implementor (java's interface fields are `static` and inherited; a Scala companion is
  * not); `continue` inside a doubly-nested `for` (CLAUDE.md §4.4); and `java.util` mutation through
  * the iterator (`Iterator.remove()`, `List.set`'s return value), which is why this port runs no
  * `collections` phase.
  *
  * No behavioural gate: noise4j ships no test sources at all, so there is no `test.conf` beside
  * this one (`PROGRESS.md` §noise4j).
  */
object Noise4jMigrate:

  def main(args: Array[String]): Unit =
    PortConfig.load(Noise4jPort.conf("main.conf"), args.toSeq).execute()

/** Where this port's configuration lives, for the `main` that names it. */
object Noise4jPort:

  def repoRoot: Path =
    Path.of(sys.props.getOrElse("balticporter.root", ".")).toAbsolutePath.normalize

  def conf(name: String): Path = repoRoot.resolve("balticporter/corpus/ports/noise4j").resolve(name)
