package balticporter.corpus.visuiusl

import balticporter.runner.PortConfig

import java.nio.file.Path

/** Migrate **USL** — VisUI's skin-definition language compiler (`usl/src/main/java`, 18 files /
  * 1,604 LOC: a lexer, a recursive parser, a style merger and a JSON writer).
  *
  *   corpus/runMain balticporter.corpus.visuiusl.UslMigrate [--determinism=full]
  *
  * ==This program is ONE LINE, and that is the point==
  * The whole port is `balticporter/corpus/ports/visui-usl/main.conf` — read that, not this file.
  * What remains here is a `main` whose only job is to name the configuration and give the run its
  * report identity: `CheckReport.dir` is derived from the main class's simple name, so a per-port
  * `main` is what keeps `port-report/UslMigrate` a stable measurement baseline across any later
  * rename of the module (CLAUDE.md §2.1's third exemption).
  *
  * ==A STANDALONE port, and NOT a scope edit to its sibling==
  * `sge-visui` ports VisUI's `ui/` module as a DEPENDENT of libGDX core. This ports the OTHER
  * gradle module in the same upstream checkout, and it is its own port root rather than a glob
  * added to that one. The reasoning is in the conf and the short form is that upstream publishes
  * these as two maven coordinates at two independent versions, that `com.kotcrab.vis.ui` and
  * `com.kotcrab.vis.usl` are siblings rather than one package root, and that `VisUiPolicy` already
  * narrowed its `governs` claim in writing so that "the follow-up states its own".
  *
  * ==THE CORPUS'S FIRST CHANCE TO EXCEED A REFERENCE PORT==
  * Every other library here is measured against a hand port that already exists, and the best a
  * mechanical port has managed is parity plus a licence file. The reference hand port
  * (`../sge/sge-extension/visui`) **never ported USL at all** — grepped: no file under it names
  * `usl`, `Lexer`, `StyleMerger` or `USLJsonWriter`. So there is no reference to match here, and
  * anything this engine emits is a capability sge does not have. CLAUDE.md §3.5 is explicit that a
  * skip is not a model — "this project exists precisely to port what sge left out" — and this is
  * the first time that sentence has a whole upstream module behind it.
  *
  * ==What it forces the engine to get right==
  *   1. **A hand-written CHARACTER SCANNER.** `Lexer` and `Parser` are ordinary imperative java
  *      driven by a `char` index. **28 post-increment/decrement sites**, in the one kind of code
  *      where CLAUDE.md §4.4's post-increment row decides every token boundary — and none of it
  *      moves a compile-error count.
  *   2. **A ZERO-AUTHORING ORACLE.** Upstream ships both sides of the answer: 19 `.usl` fixtures
  *      under `usl/styles`, six `test-*.usl`/`test-*-expected.json` pairs under the test
  *      resources, and a `uiskin.json` checked into `ui/src/main/resources` that the root build
  *      compiles FROM one of those fixtures. Nobody has to write a test for any of it; see
  *      `test.conf` and `PROGRESS.md` §10.9.13 for what it verdicts.
  */
object UslMigrate:

  def main(args: Array[String]): Unit =
    PortConfig.load(UslPort.conf("main.conf"), args.toSeq).execute()

/** Where this port's configuration lives, for the `main` that names it. */
object UslPort:

  def repoRoot: Path =
    Path.of(sys.props.getOrElse("balticporter.root", ".")).toAbsolutePath.normalize

  def conf(name: String): Path = repoRoot.resolve("balticporter/corpus/ports/visui-usl").resolve(name)
