package balticporter.corpus.noise4j

import balticporter.runner.PortConfig

import java.nio.file.Path

/** Migrate **noise4j** (`src`, 12 types — a dependency-free procedural map-generation library).
  *
  *   corpus/runMain balticporter.corpus.noise4j.Noise4jMigrate [--determinism=full]
  *
  * ==This program is ONE LINE, and that is the point==
  * The whole port is `corpus/ports/noise4j/main.conf` — read that, not this file. What remains here
  * is a `main` whose only job is to name the configuration and give the run its report identity:
  * `CheckReport.dir` is derived from the main class's simple name, so a per-port `main` is what
  * keeps `port-report/Noise4jMigrate` a stable measurement baseline.
  * `balticporter.runner.PortConfigMain` runs any conf without one.
  *
  * ==Why noise4j is the fourth corpus library==
  * It is small, and it was not chosen for that. It is the first library in the corpus with **Java
  * enum constant bodies** — an abstract enum method overridden per constant — and it has three
  * independent instances of the shape:
  *
  *   1. `Generator.GenerationMode` — five constants, each overriding `modify(Grid, int, int, float)`.
  *   2. `RoomType.DefaultRoomType` — five constants implementing an INTERFACE as well, two of them
  *      also overriding `isValid`, and two carrying their own `static final` constants inside the
  *      constant body.
  *   3. `DungeonGenerator.Direction` — four constants overriding one abstract method and, between
  *      them, two of four non-abstract ones, so the fall-through inherited body matters.
  *
  * Scala 3's `enum` cannot hold a per-case body, so this is a construct with no syntactic
  * counterpart at all. The reference hand port (`../sge/sge-extension/noise`) SOLVED it, by
  * rewriting each into an ordinary `enum` plus a `this match` in the base method — a redesign, not
  * a translation, and therefore not something a mechanical engine may copy.
  *
  * Three more shapes it exercises that the corpus has thin coverage of:
  *
  *   - **an interface CONSTANT read unqualified from an implementor.** `Grid.CellConsumer` declares
  *     `boolean BREAK = true, CONTINUE = false` (two fields in one Java declaration), and
  *     `NoiseGenerator` / `CellularAutomataGenerator` `return CONTINUE` while merely implementing
  *     the interface. Java interface fields are `static` and inherited; a Scala companion is not.
  *   - **`continue` inside a doubly-nested `for`** (`CellularAutomataGenerator.countLivingNeighbors`),
  *     which CLAUDE.md §4.4 says is invisible to a compile in both directions.
  *   - **`java.util` mutation through the iterator** — `Iterator.remove()` over a `LinkedList` in
  *     `DungeonGenerator.removeDeadEnds`, and `List.set` used for its RETURN value in
  *     `Generators.shuffle`. Neither has a `scala.collection` counterpart, which is why this port
  *     deliberately runs no `collections` phase; see the conf.
  *
  * ==It has no behavioural gate, and that is a measured fact rather than an oversight==
  * noise4j ships **no test sources at all** — `find` over the upstream tree returns `src/` and
  * `examples/` (nine PNGs) and nothing else. The 13 MUnit cases in the reference hand port are
  * hand-written Scala; there is no Java suite to put through this pipeline, so there is no
  * `test.conf` beside this one and `just noise4j-measure` has no run phase. `PROGRESS.md` §noise4j
  * states what that costs: everything CLAUDE.md §4.4 lists is unmeasured for this port.
  */
object Noise4jMigrate:

  def main(args: Array[String]): Unit =
    PortConfig.load(Noise4jPort.conf("main.conf"), args.toSeq).execute()

/** Where this port's configuration lives, for the `main` that names it. */
object Noise4jPort:

  def repoRoot: Path =
    Path.of(sys.props.getOrElse("balticporter.root", ".")).toAbsolutePath.normalize

  def conf(name: String): Path = repoRoot.resolve("corpus/ports/noise4j").resolve(name)
