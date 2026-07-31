package balticporter.corpus.jbump

import balticporter.runner.PortConfig

import java.nio.file.Path

/** Migrate **jbump** (`jbump/src`, 19 types — a dependency-free 2D AABB collision library).
  *
  *   corpus/runMain balticporter.corpus.jbump.JbumpMigrate [--determinism=full]
  *
  * ==This program is ONE LINE, and that is the point==
  * The whole port is `corpus/ports/jbump/main.conf` — read that, not this file. What remains here
  * is a `main` whose only job is to name the configuration and give the run its report identity:
  * `CheckReport.dir` is derived from the main class's simple name, so a per-port `main` is what
  * keeps `port-report/JbumpMigrate` a stable measurement baseline.
  *
  * ==jbump has NO test suite, and that is the first thing to know about this port==
  * Upstream's `test` gradle module is a runnable libGDX demo (`TestBump extends
  * ApplicationAdapter`, LWJGL3 + shapedrawer, driven by mouse and WASD) with **zero** `@Test`
  * methods. The reference hand port in sge has 32 Scala test cases, but they were WRITTEN there —
  * they are not translations of anything, so there is nothing for this engine to port. `just
  * jbump-measure` re-derives the zero on every run rather than taking it on trust, and PROGRESS.md
  * §jbump records what the absence costs: this port's evidence stops at the compiler, and
  * CLAUDE.md §3 is explicit that a green compile says nothing about behaviour.
  *
  * ==What jbump adds to the corpus==
  * It is small, and it was not chosen for that. Four constructs it forces, none of which
  * simple-graphs or Ashley has:
  *
  *   1. **A class that is both `Iterable<T>` and `Iterator<T>`** — `IntIntMap.Entries`, reached
  *      through an `IntIntMap` that is only `Iterable`. That is CLAUDE.md §4.5's shape, and
  *      `IntIntMap` is *literally libGDX's own class* vendored into jbump, which makes this the
  *      control experiment for whether `GdxSharedIteratorRule` is a §1(c) rule about libGDX or a
  *      §1(b) mechanism about a family of Java collections.
  *   2. **Interface constants that are anonymous classes** — `CollisionFilter.defaultFilter` and
  *      `Response.{slide,touch,cross,bounce}`. Java interface fields are `static` and INHERITED, so
  *      `World.check`'s anonymous `CollisionFilter` calls `defaultFilter` unqualified; a Scala
  *      companion inherits nothing.
  *   3. **A field and a method sharing a name, three times** — `Collisions.size`/`size()`,
  *      `IntIntMap.Entries.hasNext`/`hasNext()`, `MathUtils.random`/`random(int)`. CLAUDE.md §4.55.
  *   4. **`size++` read as a value**, eight times in `IntIntMap` (`if (size++ >= threshold)`).
  *      CLAUDE.md §4.4's post-increment row, where it decides whether the map resizes one insert
  *      early.
  *
  * ==One place this port is expected to be BETTER than the reference==
  * The hand port lost `Collisions`' public copy constructor `Collisions(Collisions)` — a
  * regression pinned in sge by a deliberately-red test (`CollisionsCopyCtorRedSuite`). The engine
  * ports constructors mechanically, so it should simply survive here; `JbumpPortSpec` asserts that
  * it does, because "should simply survive" is exactly the kind of claim that stops being true
  * without anybody noticing.
  */
object JbumpMigrate:

  def main(args: Array[String]): Unit =
    PortConfig.load(JbumpPort.conf("main.conf"), args.toSeq).execute()

/** Where this port's configuration lives, for the `main` that names it. */
object JbumpPort:

  def repoRoot: Path =
    Path.of(sys.props.getOrElse("balticporter.root", ".")).toAbsolutePath.normalize

  def conf(name: String): Path = repoRoot.resolve("corpus/ports/jbump").resolve(name)
