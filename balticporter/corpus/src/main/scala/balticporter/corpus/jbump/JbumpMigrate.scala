package balticporter.corpus.jbump

import balticporter.runner.PortConfig

import java.nio.file.Path

/** Migrate **jbump** (`jbump/src`, 19 types -- a dependency-free 2D AABB collision library).
  *
  *   corpus/runMain balticporter.corpus.jbump.JbumpMigrate [--determinism=full]
  *
  * The whole port is `balticporter/corpus/ports/jbump/main.conf` -- read that, not this file. This
  * `main` only names it and gives the run its report identity.
  *
  * NO test suite: upstream's `test` gradle module is a runnable libGDX demo with zero `@Test`; the
  * reference hand port's 32 Scala test cases were WRITTEN there, not translated (`PROGRESS.md`
  * §jbump), so this port's evidence stops at the compiler (CLAUDE.md §3).
  *
  * Four constructs it forces, none of which simple-graphs or Ashley has: a class that is both
  * `Iterable<T>` and `Iterator<T>` (`IntIntMap.Entries`, CLAUDE.md §4.5 -- `IntIntMap` is literally
  * libGDX's own class vendored in, the control experiment for whether `GdxSharedIteratorRule` is
  * §1(c) or §1(b)); interface constants that are anonymous classes (java's `static` fields are
  * INHERITED, a Scala companion inherits nothing); a field and a method sharing a name three times
  * (CLAUDE.md §4.55); and `size++` read as a value, eight times in `IntIntMap` (CLAUDE.md §4.4).
  *
  * `JbumpPortSpec` asserts that `Collisions`' public copy constructor survives here, since the hand
  * port lost it (`CollisionsCopyCtorRedSuite`) and the engine ports constructors mechanically.
  */
object JbumpMigrate:

  def main(args: Array[String]): Unit =
    PortConfig.load(JbumpPort.conf("main.conf"), args.toSeq).execute()

/** Where this port's configuration lives, for the `main` that names it. */
object JbumpPort:

  def repoRoot: Path =
    Path.of(sys.props.getOrElse("balticporter.root", ".")).toAbsolutePath.normalize

  def conf(name: String): Path = repoRoot.resolve("balticporter/corpus/ports/jbump").resolve(name)
