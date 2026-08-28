# Baltic Porter — the measurement suite (CLAUDE.md §5).
#
# One entry point for every number this project quotes. `just` with no recipe lists them.
#
#   just gdx-measure                 libGDX core        (emit → checks → break residue → compile → correlate)
#   just gdx-test-measure            libGDX's own suite (… → compile → RUN → correlate)
#   just ashley-measure              Ashley + its suite, compiled WITH libGDX core (a dependent port)
#   just anim8-measure               anim8-gdx, compiled WITH libGDX core (a dependent port)
#   just gltf-measure                gdx-gltf + its suite, compiled WITH libGDX core (a dependent port)
#   just screens-measure             libgdx-screenmanager, compiled WITH libGDX core (a dependent port)
#   just vfx-measure                 gdx-vfx, compiled WITH libGDX core (a dependent port)
#   just ai-measure                  gdx-ai, compiled WITH libGDX core (a dependent port)
#   just ai-test-measure             gdx-ai's own 10-@Test JUnit suite — emitted, compiled and RUN
#   just textra-measure              TextraTypist, compiled WITH libGDX core (a dependent port)
#   just textra-diff-measure         the REFERENCE hand port's own suite over the emitted sge.textra
#   just visui-measure               VisUI's ui/ module, compiled WITH libGDX core (a dependent port)
#   just visui-diff-measure          the REFERENCE hand port's own suite over the emitted sge.visui
#   just sg-measure                  simple-graphs + its suite
#   just noise4j-measure             noise4j — emit, checks, break residue, compile, correlate
#   just jbump-measure               jbump (no suite upstream — the lane re-derives the zero)
#   just liqp-measure                liqp + its own 105-file suite (emitted and censused; RUN when it compiles)
#   just md-measure                  flexmark-java core + the eleven util modules (no test set in scope)
#   just md-test-measure             flexmark-util's own 730-@Test suite (emitted and censused; RUN when it compiles)
#   just md-ext-measure              flexmark's extension modules as ONE dependent port, compiled WITH the base
#   just measure-all                 every lane, SERIALLY, in dependency order
#   just decision-counts             decisions.tsv row counts by kind, every port
#   just catalog-coverage            catalog.tsv across every port — rows the corpus never reaches
#   just members-unchanged [PORT]    members.tsv against its committed baseline — the blast radius
#   just baseline-list                        every port: baseline size and last run
#   just baseline-show   PORT                 the run's full report
#   just baseline-diff   PORT                 the run against the committed baseline
#   just baseline-accept PORT                 promote run-latest to baseline
#
# …and the DEBUGGING surface — the CLAUDE.md §4.6 flags and the diagnostics over emitted code.
# These are the same tools the lanes use, reachable without knowing which main class exists:
#
#   just debug-flags [PORT]          WHICH layer defines each balticporter.* flag right now
#   just debug-set   KEY VALUE       write one flag into .balticporter/debug.properties (wins)
#   just debug-clear [KEY]           remove one flag, or ALL of them (no key = the file goes)
#   just debug-emit  ROOT FQN [PHASES] [FLAGS…]   one type's TIR + Scala, around a phase boundary
#   just correlate   OUT [--scalac f] [--tests f] [--srcmap [scope=]f]…
#                                    join a compiler / test-runner log you produced BY HAND back
#                                    to members and Java origins (CLAUDE.md §5.1)
#   just debug-selfcheck             proves the four above do what they say — no sbt, no ports
#   just lane-selfcheck              proves the TEST-OUTCOME GATES every lane shares — same, no sbt
#
# WHY ONE FILE AND NOT A SCRIPT PER LANE. The four lanes are one measurement over four sets of
# paths, and as four scripts the differences between them were invisible: each held its own copy of
# the compile-and-count block, so which lane prints the bare-error breakdown, which one correlates
# when the compile FAILS, and which one passes the base port's `srcmap.tsv` were all facts you could
# only get by diffing four files. Here they are side by side. The mechanism stays in
# `scripts/_lib.sh` — sourced by every lane, and the place the reasoning lives — and the POLICY,
# which sbt project and which upstream tree and which dependency coordinates, is a variable at the
# top of this file, so a module rename is one line and never a grep through shell.
#
# THREE THINGS THAT ARE NOT STYLE, each of which cost a measurement when it was got wrong:
#
#  - **No `set -e`, anywhere in a lane.** `grep -c` exits 1 when it counts zero, and
#    `ERRORS=$(grep -cE '^-- .*Error' …)` counting zero is the SUCCESS case; `[ "$a" != "$b" ] &&
#    echo …` is the shape of the test-discovery guards. Under `set -e` a lane would abort exactly
#    when the port is green. Every guard is explicit instead, and each one says what went wrong —
#    which `set -e` cannot. `baseline-accept` is the one recipe that DOES set it, because it is a
#    file-copying command where a failed copy must stop the promotion.
#  - **Scratch captures go under the CHECKOUT** (`.balticporter/tmp`, `MEASURE_TMP` in the helper),
#    never /tmp: the fixed /tmp names collided the moment two worktrees measured concurrently and
#    one checkout's compile output silently counted, and then CORRELATED, as the other's.
#  - **A lane with NO test set still measures test discovery.** `noise4j-measure` is the first one:
#    noise4j ships no Java tests at all, so the lane asserts that upstream `@Test` count is ZERO
#    rather than omitting the block. Omitting it is how a suite that runs no tests reports success
#    — and here it would also hide the day upstream gains one.
#  - **The lanes are SERIAL and ordered.** Each re-emits into `src_managed/`, so the dependent lanes
#    compile against what the base lane just wrote. `measure-all` runs them one at a time and stops
#    at the first failure rather than measuring a stale emit.

# ---------------------------------------------------------------------------------------------
# Policy: sbt project identifiers, emitted-port directories, upstream trees, compile dependencies.
# A module rename is a change HERE and nowhere else.
# ---------------------------------------------------------------------------------------------

# sbt projects
corpus        := "corpus"                # holds the migration programs (balticporter.corpus.*)
core_project  := "engine"                # holds balticporter.tir.CorrelateMain

# ported modules (their emitted Scala lives in <module>/src_managed/{main,test}/scala)
gdx_module    := "ported/sge"
ashley_module := "ported/sge-ecs"
sg_module     := "ported/sge-graphs"
anim8_module  := "ported/sge-anim8"
n4j_module    := "ported/sge-noise"
jbump_module  := "ported/sge-jbump"
gltf_module   := "ported/sge-gltf"
screens_module := "ported/sge-screens"
vfx_module    := "ported/sge-vfx"
ai_module     := "ported/sge-ai"
textra_module := "ported/sge-textra"
visui_module  := "ported/sge-visui"
# VisUI's OTHER gradle module, at its OWN port root and NOT a glob added to `visui_module`. §1.5's
# rule that an upstream's module count is not the port's count is about modules that "all declare
# under one package root", and these do not: `com.kotcrab.vis.ui` and `com.kotcrab.vis.usl` are
# siblings at two independent upstream versions. `corpus/ports/visui-usl/main.conf` carries the
# whole argument; the short form is that USL imports NO libGDX, so a scope edit would make a
# build-time tool unbuildable without a rendering engine.
usl_module    := "ported/sge-visui-usl"
liqp_module   := "ported/ssg-liquid"
md_module     := "ported/ssg-md"
# ssg-md's EXTENSION half, at its OWN port root (`corpus/ports/ssg-md/ext.conf` D-mde-1/2). A run's
# emission identity is the pair (`portRoot`, `sourceSet`), `SourceSet` is `main | test` and
# `PortRun` opens its emission with an unconditional `wipe(emitDir)` — so `ported/ssg-md`'s two
# slots are `main.conf`'s and `test.conf`'s, and a third run there would DELETE one of them. The two
# trees are disjoint by package (`ssg.md.*` against `ssg.md.ext.*`) and are compiled together on
# every lane below, which is what makes them one module in the consumer's build.
md_ext_module := "ported/ssg-md-ext"

# upstream Java, relative to the checkout root
gdx_src       := "../sge/original-src/libgdx/gdx"
ashley_src    := "../sge/original-src/ashley"
sg_src        := "../sge/original-src/simple-graphs"
anim8_src     := "../sge/original-src/anim8-gdx"
n4j_src       := "../sge/original-src/noise4j"
jbump_src     := "../sge/original-src/jbump/jbump/src"
# jbump's WHOLE upstream checkout, not just the ported module: the `@Test` census runs over it so
# that "jbump ships no test suite" is a number this lane re-derives, never a claim in a document.
# Upstream's `test` gradle module is a runnable libGDX demo (`TestBump extends ApplicationAdapter`),
# and the day somebody adds a real suite there this lane is what says so.
jbump_upstream := "../sge/original-src/jbump"
gltf_src      := "../sge/original-src/gdx-gltf/gltf/src"
screens_src   := "../sge/original-src/libgdx-screenmanager"
vfx_src       := "../sge/original-src/gdx-vfx"
# gdx-ai's WHOLE upstream checkout, and the census below reads TWO trees out of it separately for a
# reason: `gdx-ai/gdx-ai/tests` is a real JUnit 4 source set (2 files, 10 `@Test`) and the top-level
# `gdx-ai/tests` is a 111-file LWJGL DEMO APPLICATION declaring zero `@Test` — 54 of whose files are
# named `*Test*.java`. A filename census over this checkout reproduces "54" and a directory-name one
# reproduces "111"; only `java_test_count` reproduces 10, which is why the lane runs it on each tree
# and prints both.
ai_src        := "../sge/original-src/gdx-ai"
ai_tests      := "../sge/original-src/gdx-ai/gdx-ai/tests"
ai_demos      := "../sge/original-src/gdx-ai/tests"
# The REFERENCE HAND PORT's own MUnit suite over `sge.ai.*` — the DIFFERENTIAL lane's denominator.
# It is not an input to any migration and never becomes one: `ai-diff-measure` reads it to
# re-derive the census population (24 files / 196 `test(`) rather than assert it, so the day the
# hand port gains or loses a file the lane says the §10.7.12 census is stale instead of quietly
# measuring a smaller reference.
ai_ref_tests  := "../sge/sge-extension/ai/src/test/scala"
# TextraTypist's WHOLE upstream checkout — the port converts `src/main/java` and the lane censuses
# `src/test/java` separately, for `ai_src`'s reason with the sign flipped. That test tree is 128
# files and declares ZERO `@Test`: `build.gradle` names no JUnit coordinate at all, 113 of the 128
# declare `public static void main`, and 107 extend `ApplicationAdapter`/`Game`/`InputAdapter`. A
# filename census over it reproduces a large number and only `java_test_count` reproduces the 0,
# which is why the lane runs it on the tree rather than omitting the block — and it is what says so
# the day upstream gains a real suite.
textra_src    := "../sge/original-src/textratypist"
# VisUI's WHOLE upstream checkout — the port converts `ui/src/main/java` and the lane censuses THREE
# other trees out of it separately, because every one of them is a number a document would get
# wrong. `ui/src/test` is 30 files declaring **2** real `@Test` (28 of the rest are
# `extends VisWindow` demos that `ui/build.gradle` excludes by NAME, and one more is a demo sitting
# OUTSIDE that excluded directory that matches the `**/*Test.**` include and contributes zero); the
# out-of-scope `usl/` module is 18 files holding 7 of the checkout's 9 real `@Test`; and the
# RESOURCES the emitted code names by hardcoded classpath string are a fourth. A filename census
# over any of them reproduces a wrong number and only `java_test_count` reproduces the 2.
visui_src     := "../sge/original-src/vis-ui"
# gdx-gltf's WHOLE test tree, not the one file the port migrates: SEVEN java files sit there and
# only ONE is a suite (`AttributesCompareTest`, 8 `@Test`). The other six are `extends Game` demos
# with a `main` that opens an lwjgl window. `java_test_count` over the tree is what re-derives the
# 8 — and what says so the day a second file gains a real `@Test`.
gltf_tests    := "../sge/original-src/gdx-gltf/gltf/test"
# liqp is the one corpus library whose upstream is a submodule of **ssg**, not of sge. Its own
# `src/main/java` is what the port converts; `target/generated-sources/antlr4` is UNTRACKED ANTLR
# output that `LiqpClasspath` javacs into `{{liqp_parser_classes}}` and hands the frontend as a
# CLASSPATH (decision D-liqp-1, stated in balticporter/corpus/ports/liqp/main.conf).
#
# ONE directory, read by the frontend, by scalac and by the test run. It was two until D-liqp-1b:
# the parser's own signature named `liqp.TemplateParser.ErrorMode` while the port emits
# `ssg.liquid`, so scalac needed upstream `liqp` beside the parser or it ABORTED
# (`AssertionError` out of `ClassfileParser`) rather than reporting anything — and the frontend had
# to be kept away from exactly those class files, a `liqp` class file being a second definition of
# every ported type. `LiqpClasspath` now rewrites the generated sources into the emitted namespace
# before javac reads them, so there is no seam left for a second directory to soften.
liqp_src      := "../ssg/original-src/liqp"
liqp_parser_classes := "out/liqp-parser-classes"
# flexmark-java is the second corpus library vendored under **ssg** rather than under sge. Its
# `sourceRoot` is the CHECKOUT (decision D-md-1, `balticporter/corpus/ports/ssg-md/main.conf`): 53 maven
# modules, all declaring under one package root, so no single directory is both a package root and
# a scope boundary. Milestone 1 converts the twelve below and nothing else.
md_src        := "../ssg/original-src/flexmark-java"
# THE SCOPE, restated. `main.conf`'s `includeGlobs` is the authority and nothing compares the two,
# so a module added there is added here — which is what the lane's own file count is for: it
# re-derives the denominator from THIS list on every run, and a divergence shows up as a scope
# figure that no longer matches the port's `converted` line.
md_modules    := "flexmark flexmark-util-ast flexmark-util-builder flexmark-util-collection flexmark-util-data flexmark-util-dependency flexmark-util-format flexmark-util-html flexmark-util-misc flexmark-util-options flexmark-util-sequence flexmark-util-visitor"
# MILESTONE 2's scope, restated for the same reason and read the same way: `ext.conf`'s
# `includeGlobs` is the authority, nothing compares the two, and `md-ext-measure` re-derives its
# denominator from THIS list on every run — so a module added to one and not the other shows up as a
# scope figure that no longer matches the port's `converted` line. A BATCH WAVE IS ONE MODULE NAME
# ADDED HERE AND ONE GLOB ADDED THERE (`PROGRESS.md` §10.6.8). How many of the 29 are IN is a number
# the lane PRINTS (`modules in scope: N of 29 covered`) rather than one this comment carries: a count
# written here is one a wave has to remember to edit, and a stale one reads exactly like a scope that
# drifted.
md_ext_modules := "flexmark-ext-aside flexmark-ext-resizable-image flexmark-ext-youtube-embedded flexmark-ext-anchorlink flexmark-ext-escaped-character flexmark-ext-ins flexmark-ext-superscript flexmark-ext-gfm-issues flexmark-ext-autolink flexmark-ext-gfm-users flexmark-ext-admonition flexmark-ext-yaml-front-matter flexmark-ext-jekyll-front-matter flexmark-ext-gfm-tasklist flexmark-ext-jekyll-tag flexmark-ext-abbreviation flexmark-ext-footnotes flexmark-ext-definition flexmark-ext-typographic flexmark-ext-emoji flexmark-ext-gitlab flexmark-ext-macros flexmark-ext-wikilink flexmark-ext-gfm-strikethrough flexmark-ext-media-tags flexmark-ext-attributes flexmark-ext-tables flexmark-ext-enumerated-reference flexmark-ext-toc"

# …and the ONE third-party compile coordinate any of the 29 extension modules declares.
# `flexmark-ext-autolink/pom.xml` pins `org.nibor.autolink:autolink:0.6.0` in its own `<properties>`
# and three of its four main files import `LinkExtractor`/`LinkSpan`/`LinkType`, so the emitted Scala
# NAMES that package and a compile without it reports unresolved references as this port's wall.
#
# It is a SEPARATE variable from `md_deps` on purpose: `md_deps` is the base's compile line and the
# base names nothing from this jar, so putting it there would add a coordinate to `md-measure`'s and
# `md-test-measure`'s measurements for a module neither of them converts. The FRONTEND classpath is
# shared (`FlexmarkClasspath.Coordinates`, whose own doc says why); the COMPILE lines are per lane.
#
# `ext.conf` D-mde-6 is the decision behind it — declared rather than routed around — and the
# residue it leaves: this is a JVM-only artifact on a module claiming three platforms, which nothing
# in the engine reports.
md_ext_deps   := "--dependency org.nibor.autolink:autolink:0.6.0"

# …and the extension suite's java-side denominator, `ext-test.conf`'s `includeGlobs` restated.
#
# FILES AND NOT DIRECTORIES, which is `md_test_src`'s third entry for `md_test_src`'s reason. An
# extension's `src/test` is overwhelmingly `@RunWith(Parameterized.class)` `ComboSpecTestCase`
# subclasses (`PROGRESS.md` §10.6.1's documented refusal) and a `@RunWith(Suite.class)` aggregator, so
# a directory here would put their `@Test`s in `test_discovery_guard`'s denominator and report a
# SCOPE DECISION as tests the port LOST — the one failure that check must not have
# (`ENGINE-LIMITS.md` M5). `java_test_count` takes `find` starting points, and a file is one.
md_ext_test_src := "../ssg/original-src/flexmark-java/flexmark-ext-aside/src/test/java/com/vladsch/flexmark/ext/aside/AsideParserTest.java ../ssg/original-src/flexmark-java/flexmark-ext-autolink/src/test/java/com/vladsch/flexmark/ext/autolink/MergeAutoLinkTest.java ../ssg/original-src/flexmark-java/flexmark-ext-admonition/src/test/java/com/vladsch/flexmark/ext/admonition/AdmonitionParserTest.java ../ssg/original-src/flexmark-java/flexmark-ext-jekyll-tag/src/test/java/com/vladsch/flexmark/ext/jekyll/tag/MergeJekyllTagTest.java ../ssg/original-src/flexmark-java/flexmark-ext-abbreviation/src/test/java/com/vladsch/flexmark/ext/abbreviation/MergeAbbreviationsTest.java ../ssg/original-src/flexmark-java/flexmark-ext-footnotes/src/test/java/com/vladsch/flexmark/ext/footnotes/MergeFootnotesTest.java ../ssg/original-src/flexmark-java/flexmark-ext-definition/src/test/java/com/vladsch/flexmark/ext/definition/DefinitionParserTest.java ../ssg/original-src/flexmark-java/flexmark-ext-macros/src/test/java/com/vladsch/flexmark/ext/macros/MergeMacrosTest.java ../ssg/original-src/flexmark-java/flexmark-ext-attributes/src/test/java/com/vladsch/flexmark/ext/attributes/MergeAttributesTest.java ../ssg/original-src/flexmark-java/flexmark-ext-tables/src/test/java/com/vladsch/flexmark/ext/tables/TableTextCollectingVisitorTest.java ../ssg/original-src/flexmark-java/flexmark-ext-tables/src/test/java/com/vladsch/flexmark/ext/tables/MarkdownTableTest.java ../ssg/original-src/flexmark-java/flexmark-ext-tables/src/test/java/com/vladsch/flexmark/ext/tables/MarkdownTransposeTableTest.java ../ssg/original-src/flexmark-java/flexmark-ext-tables/src/test/java/com/vladsch/flexmark/ext/tables/TableCellOffsetInfoTest.java ../ssg/original-src/flexmark-java/flexmark-ext-tables/src/test/java/com/vladsch/flexmark/ext/tables/MarkdownSortTableTest.java ../ssg/original-src/flexmark-java/flexmark-ext-tables/src/test/java/com/vladsch/flexmark/ext/tables/MarkdownTableTestBase.java ../ssg/original-src/flexmark-java/flexmark-ext-enumerated-reference/src/test/java/com/vladsch/flexmark/ext/enumerated/reference/MergeEnumeratedReferenceTest.java"

# the compiler every lane measures with — one version, one server-less invocation per lane
scala_version := "3.8.4"

# …AND THE JDK EVERY LANE COMPILES WITH — the OTHER half of "one compiler", and the half that was
# ambient until it broke a measurement (`ENGINE-LIMITS.md` M5.10, `scripts/_lib.sh`'s `jdk_guard`).
#
# `scala-cli` picks its JVM from `--jvm`, then `JAVA_HOME`, then the system default — so with no
# flag the lanes compiled on whatever JDK the operator's shell happened to hold, and the migration
# they measure ran on whatever JDK the sbt SERVER happened to hold. Those are two independent
# ambient choices over one measurement. This pins the half a lane can pin; `jdk_guard` compares it
# against the half it cannot (a `JAVA_HOME` exported by a recipe does not reach a `sbt -client`
# fork) and fails the lane when the two specification versions differ.
#
# WHY 22 AND NOT 17. The reference build compiles with `-release 17` (`../sge/build.sbt`), which is
# a statement about the BYTECODE the sge artifacts target and not about the JDK anything is built
# on; `-release 17` on a JDK 22 is exactly what that build does. What this variable decides is which
# JDK's CLASS FILES scalac reads for `java.*` signatures, and 22 is the state every committed
# baseline in this repository was measured on. Moving it is a change to the measurement and is
# ACKNOWLEDGED by re-accepting every baseline (§5) — not absorbed. `DESIGN.md` §8.24 records the
# delta between the two numbers.
#
# EXPORTED, so `scripts/_lib.sh`'s own two `scala-cli` invocations (`xplat_compile`, `flags_compile`)
# and `jdk_guard`'s probe read the SAME variable rather than a second copy that can drift.
export jdk_version := "22"

# The MIGRATOR invocation. `sbt -client` connects to "a" running server — and in a checkout with
# git worktrees it connected to ANOTHER worktree's server (measured 2026-08-28: a run from
# w13-nullable-members wrote its run-latest/ into w7-uniform-deps/port-report/, and two promotion
# runs hung inside an `sbtn` that had nothing to talk to — ENGINE-LIMITS M5.11). `-batch` starts a
# server per invocation, in THIS directory, and a recipe-exported JAVA_HOME reaches the fork.
sbt_migrate := "sbt -batch"

# Reference-build scalacOptions (DESIGN.md §8.24, PROGRESS.md §13 wave 1.0).
#
# The flag list is READ from the reference repo's SgePlugin / ssg's build.sbt, not hand-copied:
# - `sge_strict_flags`: SgePlugin.defaultScalacOptions ++ SgePlugin.strictScalacOptions, with
#   -Xmacro-settings:* dropped (macro timeouts, not diagnostics). Source: sge-build/src/main/scala/
#   sge/sbt/SgePlugin.scala (§8.24). Used by the core `sge` project and every module whose
#   build.sbt applies `commonSettings` + `strictSettings`.
# - `sge_relaxed_flags`: the strict set MINUS -Wunused:imports,privates,locals,patvars,nowarn,
#   which is what `SgePlugin.relaxedSettings` removes. Used by sge extension modules.
# - `ssg_flags`: ssg/build.sbt's scalacOptions block, with -Xmacro-settings:* dropped.
#   Source: ssg/build.sbt lines 41-54.
#
# ALL THREE are identical today (ssg copied sge's strict set), so a single variable suffices; the
# three names exist as documentation for where each came from and WHY each lane uses the one it does.
sge_strict_flags  := "-deprecation -feature -language:implicitConversions -no-indent -Werror -Wimplausible-patterns -Wrecurse-with-default -Wenum-comment-discard -Wunused:imports,privates,locals,patvars,nowarn"
sge_relaxed_flags := "-deprecation -feature -language:implicitConversions -no-indent -Werror -Wimplausible-patterns -Wrecurse-with-default -Wenum-comment-discard"
ssg_flags         := "-deprecation -feature -no-indent -Werror -Wimplausible-patterns -Wrecurse-with-default -Wenum-comment-discard -Wunused:imports,privates,locals,patvars,nowarn"

# per-lane compile/test dependencies, verbatim scala-cli flags (word-split on purpose)
gdx_deps      := "--dependency org.junit.jupiter:junit-jupiter:5.10.2 --dependency junit:junit:4.13.2 --dependency org.scalameta::munit:1.0.2 --dependency com.kubuszok::lls:0.3.0"
# The libGDX suite's RUN carries the same three coordinates in a DIFFERENT order, and it is kept
# that way on purpose: this is the order the run that produced the committed `tests.tsv` used, and
# the order of `--dependency` flags is an input to scala-cli's classpath — with junit4, jupiter and
# munit all present, which runner claims a suite is decided by scanning it. Reordering may well be
# harmless; it is not something this file is entitled to change silently, and the lane that would
# discover it costs 221 tests to run.
gdx_run_deps  := "--dependency org.scalameta::munit:1.0.2 --dependency junit:junit:4.13.2 --dependency org.junit.jupiter:junit-jupiter:5.10.2 --dependency com.kubuszok::lls:0.3.0"
# Mockito 1.10.19, NOT a 2.x/5.x: Ashley's `ComponentClassFactory` uses `org.mockito.asm`, removed
# in 2.0. Read from Ashley's own build.gradle rather than guessed — guessing it cost a full cycle.
ashley_deps   := "--dependency junit:junit:4.13.2 --dependency org.mockito:mockito-all:1.10.19 --dependency org.scalameta::munit:1.0.2 --dependency com.kubuszok::lls:0.3.0"
sg_deps       := "--dependency junit:junit:4.12 --dependency org.scalameta::munit:1.0.2"
# anim8 upstream declares NO test framework at all (its `src/test/java` is a set of lwjgl3 demo
# apps, not a suite — see Anim8Migrate's scope note), so the only coordinate this lane needs is the
# one its HAND-WRITTEN suite is written in.
anim8_deps    := "--dependency org.scalameta::munit:1.0.2 --dependency com.kubuszok::lls:0.3.0"
# noise4j declares NO dependencies — its `build.gradle` has an empty `dependencies` block and the
# 12 sources import nothing outside `java.lang`, `java.math` and `java.util`. Stated as an empty
# variable rather than omitted from the lane, so the lane reads the same as every other one and the
# claim "this library needs nothing" is written down where it can be contradicted.
n4j_deps      := ""
# jbump declares NO dependencies at all (`jbump/build.gradle` is four lines and adds none), and it
# is a `RuntimeMode.Vendored` port, so the support types ship inside the emitted source set. Empty
# on purpose, and left as a variable rather than dropped from the lane: the day the port grows a
# test source set this is the line that gains a coordinate.
jbump_deps    := ""
# liqp's `pom.xml`, at COMPILE scope, verbatim — including the one that reads like a typo and is
# not: `jackson.databind.version` is 2.13.4.2 while `jackson.version` is 2.15.0, two properties in
# the same pom. A port resolves what the library DECLARES (see `ashley_deps` for what guessing
# cost). The ANTLR-generated parser is NOT a coordinate — it is a directory of class files the
# lane adds with `--jar` (see `liqp_parser_classes`).
#
# WHAT IS NOT HERE, and why: `multiarch-serviceloader` — the coordinate D-liqp-12's redirect points
# `java.util.ServiceLoader` at, which the EMITTED scala names outright — used to be spelled here as
# a third copy of a fact the port's `.conf` and the generated build already state. Nothing compared
# the copies, so a revision bumped in the manifest and not here would compile this lane against a
# DIFFERENT JAR with every check count, every member digest and every test outcome flat. The run now
# PUBLISHES what it declared (`run-latest/dependencies.tsv`) and the lane derives its
# `--dependency`/`--repository` from that file through `declared_dep_flags` (scripts/_lib.sh), so
# the coordinate — and the Central Portal snapshot repo it needs, which is not Maven Central — can
# only be wrong in one place. What stays below is what `pom.xml` declares and the manifest does not.
liqp_deps     := "--dependency org.antlr:antlr4-runtime:4.13.0 --dependency com.fasterxml.jackson.core:jackson-core:2.15.0 --dependency com.fasterxml.jackson.core:jackson-databind:2.13.4.2 --dependency com.fasterxml.jackson.core:jackson-annotations:2.15.0 --dependency com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.15.0 --dependency ua.co.k:strftime4j:1.0.6"
# …and what the TEST source set adds on top of it. `junit:junit:4.13.1` is the ONE test-scope
# dependency `pom.xml` declares; `org.hamcrest:hamcrest-core:1.3` arrives with it TRANSITIVELY and
# is deliberately NOT named here — a port resolves what the library DECLARES, and hamcrest is not
# a coordinate liqp has.
#
# BOTH jars are RUN dependencies, not only frontend ones, and that is a decision rather than an
# oversight: `TestFrameworkTransform` maps `org.junit.Assert` onto `munit.Assertions` and has no
# matcher algebra, so liqp's 767 `assertThat(x, is(y))` sites stay on hamcrest. Measured — a
# hamcrest `AssertionError` produces MUnit's `==> X` marker per test and the suite CONTINUES, so
# `reconcile_outcomes` loses nothing by it. munit is the runner the conversion targets.
liqp_test_deps := "--dependency junit:junit:4.13.1 --dependency org.scalameta::munit:1.0.2"
# JUnit 4.12 — gdx-gltf's OWN `junitVersion`, from its root `build.gradle`, not the 4.13.2 the
# other lanes happen to use. The suite is converted to MUnit by `TestFrameworkTransform`, so the
# junit coordinate is not what RUNS it; it is here because scala-cli must resolve the same surface
# the frontend did, and because a port resolves what the library DECLARES (see `ashley_deps`).
gltf_deps     := "--dependency junit:junit:4.12 --dependency org.scalameta::munit:1.0.2 --dependency com.kubuszok::lls:0.3.0"
# libgdx-screenmanager's `build.gradle` declares gdx 1.13.5 and `com.github.crykn.guacamole:gdx`.
# libGDX arrives as EMITTED SCALA on this compile, not as a jar, and guacamole is replaced by the
# hand-written Scala in `ported/sge-screens/src/main/scala`. The annotation jar was the third coordinate
# and IS NOW RETIRED: `ScreensPolicy.nullability` consumes `org.jspecify.annotations.Nullable` into
# the TYPE, so no emitted declaration names it and nothing is left to resolve. That retirement is
# the PROOF, not a tidy-up — a jar still on the compile line would let a surviving annotation
# resolve and the port would look converted while it was not. munit is for the hand-written suite.
screens_deps  := "--dependency org.scalameta::munit:1.0.2 --dependency com.kubuszok::lls:0.3.0"
# gdx-vfx's only compile dependency is libGDX itself, which this lane supplies as the SOURCE the
# base port emitted rather than as a coordinate. So the only coordinate here is the one its
# HAND-WRITTEN suite is written in — the same shape, and for the same reason, as anim8's.
vfx_deps      := "--dependency org.scalameta::munit:1.0.2 --dependency com.kubuszok::lls:0.3.0"
# gdx-ai's only compile dependency is libGDX itself (`gdx-ai/build.gradle`: one `api
# "com.badlogicgames.gdx:gdx"` and JUnit for tests), which this lane supplies as the SOURCE the base
# port emitted rather than as a coordinate. Milestone 1 compiles ONE source set and has no
# hand-written suite, so there is no test coordinate to add either — and this line stays empty
# rather than carrying munit speculatively: a dependency on the compile line that nothing needs is a
# dependency nobody can later prove was required.
ai_deps       := "--dependency com.kubuszok::lls:0.3.0"
# …and the TEST lane's, which is not empty: the emitted suite is MUnit and any UNCONVERTED residue
# is still JUnit, so both runners have to be resolvable or a refused conversion is a compile error
# instead of a counted refusal. JUnit 4.12 is gdx-ai's OWN declared version
# (`gdx-ai/build.gradle`), read off the build rather than aligned with Ashley's 4.13.2 — see
# `ashley_deps` for what guessing a version costs. MUnit FIRST, for `gdx_run_deps`' reason: with
# two runners present, which one claims a suite is decided by scanning it, and the flag order is an
# input to that classpath.
ai_test_deps  := "--dependency org.scalameta::munit:1.0.2 --dependency junit:junit:4.12 --dependency com.kubuszok::lls:0.3.0"
# TextraTypist declares TWO `api` coordinates and this lane names NEITHER. libGDX arrives as the
# SOURCE the base port emitted rather than as a jar, exactly as it does for every other dependent;
# and `com.github.tommyettinger:regexodus` — which the emitted Scala names outright, six classes of
# it — is DECLARED BY THE PORT (`TextraTypistPolicy.dependencies`), so the lane derives its
# `--dependency` from what the run published rather than restating the coordinate here. That is
# `liqp_deps`' own lesson: a revision bumped in the manifest and not in the lane compiles the port
# against a DIFFERENT JAR with every check count, every member digest and every outcome flat. There
# is no hand-written suite either, so this stays empty rather than carrying munit speculatively.
textra_deps   := "--dependency com.kubuszok::lls:0.3.0"
# …and the DIFFERENTIAL lane's two. `textra_ref_tests` is the reference hand port's own MUnit tree —
# THREE platform source directories (`scala`, `scalajvm`, `scalanative`), which is why the census
# reads the parent and not `…/scala`: two of the three hold suites the census classifies, and a lane
# that counted one directory would report a population smaller than the one it is classifying.
# `textra_test_deps` carries munit for the hand-written half; regexodus is DERIVED from what the
# port published, exactly as `textra-measure` derives it (`declared_dep_flags`).
textra_ref_tests := "../sge/sge-extension/textra/src/test"
textra_test_deps := "--dependency org.scalameta::munit:1.0.2 --dependency com.kubuszok::lls:0.3.0"
# VisUI's `ui/build.gradle` declares ONE compile coordinate — `com.badlogicgames.gdx:gdx` — which
# arrives as the SOURCE the base port emitted rather than as a jar, exactly as it does for every
# other libGDX dependent. So this is empty and stays empty, and the lane still derives whatever the
# RUN published (`declared_dep_flags`) rather than assuming the port declares nothing: an empty
# variable is this port's fact today and a derived flag is what says so the day it stops being one.
visui_deps    := "--dependency com.kubuszok::lls:0.3.0"
# …and the DIFFERENTIAL lane's three. `visui_ref_tests` is the reference hand port's own MUnit tree
# — TWO platform source directories (`scala`, `scalajvm`), which is why the census reads the parent
# and not `…/scala`: both hold suites the census classifies, and a lane that counted one directory
# would report a population smaller than the one it is classifying (`textra_ref_tests`' lesson).
# `visui_test_deps` carries munit for the hand-written half.
#
# `visui_closure` IS THIS LANE'S ONE UNUSUAL VARIABLE, and it exists because this is the first
# differential gate over a port that is NOT at zero. §3's rule is that a single typer error skips
# `RefChecks` for the WHOLE program, so a compile carrying the port's 8-error floor
# (`PROGRESS.md` §10.9.10) can never take the second, `RefChecks`-honest census pass the two earlier
# differential lanes both required — and it can never RUN anything either, since scalac reaching no
# backend phase writes no class file. These five files are the TRANSITIVE CLOSURE, over the emitted
# tree, of what the adapted suites name; the lane VERIFIES rather than asserts that none of them is
# one of the 8, by reading the run's own `errors.tsv`. Three guards keep the list from going stale
# silently: that check, the 0-error requirement (a suite that grew to need a sixth file fails with
# `Not Found` rather than being quietly narrowed), and the census population gate.
visui_ref_tests := "../sge/sge-extension/visui/src/test"
visui_test_deps := "--dependency org.scalameta::munit:1.0.2 --dependency com.kubuszok::lls:0.3.0"
visui_closure := "Sizes.scala util/ColorUtils.scala util/OsUtils.scala util/Validators.scala util/InputValidator.scala"
# USL's own four. `usl_deps` is EMPTY and says why: this library imports nothing outside the JDK,
# which the lane re-derives on every run rather than trusting this line — a port with genuinely no
# dependencies is rare enough that the day one appears, that derivation is what says so.
usl_src       := "../sge/original-src/vis-ui/usl"
usl_deps      := ""
# THE ORACLE'S TWO INPUT SETS, and they are not the same kind of evidence (`PROGRESS.md` §10.9.13).
# `usl_styles` is the 19 shipped `.usl` fixtures the root `build.gradle` compiles the skin FROM;
# `usl_known_good` is the artifact it compiled — checked into the SIBLING module's resources, which
# is the whole reason this is a zero-authoring gate rather than a test somebody wrote.
usl_styles     := "../sge/original-src/vis-ui/usl/styles"
usl_known_good := "../sge/original-src/vis-ui/ui/src/main/resources/com/kotcrab/vis/ui/skin/x1/uiskin.json"
# …and the suite's own inputs, which are RESOURCES rather than sources and are therefore the one
# thing the emitted code cannot carry. Every test reads `/test-*.usl` through
# `getResourceAsStream`, a STRING LITERAL no rename may touch (§4.56) — so the upstream tree has to
# be handed to the runner at its upstream paths, unchanged. That is `PROGRESS.md` §11's item 7
# ("the port's own classpath resources are not part of its output, and nothing says so") met at the
# smallest scale in the corpus: 12 files, and the lane supplying them IS the obligation being paid.
usl_test_res  := "../sge/original-src/vis-ui/usl/src/test/resources"
usl_test_deps := "--dependency org.scalameta::munit:1.0.2"
# flexmark's one compile-scope coordinate, `org.jetbrains:annotations:24.0.1`, is a FRONTEND input
# AND a compile one — and this line was written empty on the reasoning that it could not be, which
# the port's first run disproved in one number. The reasoning was: the annotations are markers,
# milestone 1 declares no `preservedAnnotations`, and `AnnotationPolicy`'s default claims no family,
# so nothing emitted could name them. What the run showed is that `claimed` gates an annotation WITH
# ARGUMENTS and a MARKER is carried unconditionally (`SpoonTir.annotationsOf`), so
# `@org.jetbrains.annotations.NotNull` is emitted on 237 of the 468 files this port writes — and
# `value jetbrains is not a member of org` was **1976 of the first run's 2184 errors**.
#
# The coordinate goes on the compile line rather than the emission being changed, because those are
# two different acts and only one of them belongs to this milestone: the emitted code NAMES that
# type, so a compile without it reports unresolved references as this port's wall, which is the same
# refusal `liqp-measure` makes about the generated parser. WHETHER a marker whose family the port
# does not claim should be emitted at all is an engine question (`PROGRESS.md` §10.6 states it with
# the number), and it has a portability half beside it — this module claims all three platforms and
# that jar is JVM-only. Both are the next wave's, with a measurement each.
md_deps       := "--dependency org.jetbrains:annotations:24.0.1"

# ssg-md's SUITE lives in THREE modules the port's MAIN scope does not convert, and this variable is
# the java-side denominator over exactly those (`corpus/ports/ssg-md/test.conf` D-mdt-1/4/5). None of
# them is one of `md_modules`, so `md-measure`'s discovery zero stays a true statement about ITS
# scope: the two lanes count different trees.
#
# THE THIRD ENTRY IS FIVE FILES AND NOT A DIRECTORY, and that is the whole point of writing it out.
# `flexmark-core-test/src/test/java` holds 40 files of which 35 are `@RunWith(Parameterized)` combo
# suites this milestone does not carry, so a directory here would put their `@Test`s in the
# denominator and report them as tests the port LOST — a discovery guard crying wolf about a scope
# decision, which is the one failure that check must not have (ENGINE-LIMITS M5). `java_test_count`
# takes `find` starting points, and a file is one.
#
# The five contribute ZERO to it: `FullOrigSpec*CoreTest` declare no `@Test` of their own, they
# INHERIT the one `FullSpecTestCase` declares. That is a fact about java's own runner too — javac
# and JUnit run an inherited `@Test` once per concrete subclass — so both sides of this subtraction
# count DECLARATIONS and the guard stays exact.
md_test_src   := "../ssg/original-src/flexmark-java/flexmark-util/src/test ../ssg/original-src/flexmark-java/flexmark-test-util/src/main/java ../ssg/original-src/flexmark-java/flexmark-core-test/src/test/java/com/vladsch/flexmark/core/test/util/renderer/OrigSpecCoreTest.java ../ssg/original-src/flexmark-java/flexmark-core-test/src/test/java/com/vladsch/flexmark/core/test/util/renderer/FullOrigSpecCoreTest.java ../ssg/original-src/flexmark-java/flexmark-core-test/src/test/java/com/vladsch/flexmark/core/test/util/renderer/FullOrigSpec027CoreTest.java ../ssg/original-src/flexmark-java/flexmark-core-test/src/test/java/com/vladsch/flexmark/core/test/util/renderer/FullOrigSpec028CoreTest.java ../ssg/original-src/flexmark-java/flexmark-core-test/src/test/java/com/vladsch/flexmark/core/test/util/renderer/FullOrigSpec029CoreTest.java"

# THE CLASSPATH RESOURCES THE RUN NEEDS, at their UPSTREAM paths, and there are three kinds.
#
# `--resource-dir` is repeatable and is the shape `liqp-measure` uses for the emitted
# `META-INF/services` descriptors. UPSTREAM bytes rather than copies: a spec file is the INPUT the
# port is measured against, and rewriting one would be measuring the port against something this
# repository wrote.
#
#   1. `flexmark-test-specs` — six versions of the CommonMark spec (0.26-0.30 plus the unversioned
#      `spec.txt`), 618-652 examples each. The four conformance suites reach them through
#      `Class.getResourceAsStream("/spec.0.29.txt")`, and a missing one is loud:
#      `ResourceLocation` throws `IllegalStateException("Could not load …")`.
#   2. `flexmark-util-sequence` — `entities.properties`, and THIS ONE IS THE LIBRARY'S OWN, not the
#      harness's. `Html5Entities` reads it in a static initialiser to build the HTML5 entity table,
#      so every `&nbsp;` in every document needs it; absent, the port throws
#      `ExceptionInInitializerError` and then `NoClassDefFoundError` at the SECOND use, which is
#      what three of the four conformance suites did on their first run. Nothing before this wave
#      could see it — the unit suite never unescapes an entity — and no compile, no check and no
#      count moves when it is missing.
#      **IT IS NOW THE PORT'S OWN OUTPUT** — `md_lib_res` points at what the run WRITES, not at
#      upstream. `main.conf`'s `resources` key declares the file and the run copies it verbatim into
#      `src_managed/main/resources` (`DESIGN.md` §8.22). Pointed at upstream, this flag made the
#      SUITE pass while the PORT shipped nothing, which is exactly the consumer obligation
#      `PROGRESS.md` §11 item 7 raised; pointed here, the lane measures the deliverable.
#   3. `flexmark-test-util` — one `com.vladsch.flexmark.test.util.txt` marker the module-root
#      helpers locate a source tree by.
#
# ==1 AND 3 STAY UPSTREAM, AND THAT IS THE LINE== A port ships what its own emitted code reads. The
# spec files and the marker are the HARNESS's input, in modules this port does not convert, so they
# are flags on a test lane and not `resources` entries — a port that shipped its test fixtures would
# be putting somebody else's data in the deliverable.
#
# ==THE PATHS ARE THE UPSTREAM ONES, AND THAT IS NOT AN OVERSIGHT== The lookup is a STRING LITERAL
# (`"/com/vladsch/flexmark/util/sequence/entities.properties"`), and a rename decides ownership
# structurally and never from a string (`CLAUDE.md` §4.56) — so `packageRenames` does not touch it
# and must not. The emitted `ssg.md.util.sequence.Html5Entities` therefore asks for the upstream
# path, which is why the file the run ships sits at that path inside the port's own resource tree.
md_spec_res   := "../ssg/original-src/flexmark-java/flexmark-test-specs/src/main/resources"
md_lib_res    := "ported/ssg-md/src_managed/main/resources"
md_tutil_res  := "../ssg/original-src/flexmark-java/flexmark-test-util/src/main/resources"

# junit is a RUN dependency and not only a frontend one: six files declare
# `@Rule ExpectedException` fields the phase emits as ordinary values and reports as unconvertible
# (test.conf D-mdt-3), and `org.hamcrest:hamcrest-core:1.3` arrives with junit transitively for the
# two files that import it — deliberately not named, exactly as `liqp_test_deps` does not name it.
# 4.13.2 is what the parent pom's `dependencyManagement` pins. munit is the runner the conversion
# targets.
md_test_deps  := "--dependency junit:junit:4.13.2 --dependency org.scalameta::munit:1.0.2"

root          := justfile_directory()

# The checkout whose `.balticporter/` the debug recipes read and write, and the directory the
# baseline recipes read. Both are this checkout, always, in normal use — they are variables so
# `just debug-selfcheck` can point them at a throwaway directory and PROVE the recipes rather
# than describe them. A self-check that has to mutate the real `port-report/` to run is a
# self-check nobody runs twice.
bp_root       := env_var_or_default("BP_ROOT", justfile_directory())
report_root   := env_var_or_default("BP_REPORT", justfile_directory() / "port-report")

_default:
    @{{just_executable()}} --list --unsorted

# ---------------------------------------------------------------------------------------------
# libGDX core — emit, checks, break residue, compile, correlate.
#
# scala-cli is the consistent gate; sbt incremental lies. This is the command CLAUDE.md §5 tells
# everyone to run, so it must show everything the migration knows: the block below used to be
# `grep -E "wrote" | head -1`, and the checks were computed on every run and then DISCARDED by the
# one command anybody runs.
# ---------------------------------------------------------------------------------------------
[doc("libGDX core — emit, checks, break residue, compile, correlate")]
gdx-measure:
    #!/usr/bin/env bash
    cd "{{root}}"
    ROOT="$(pwd)"
    export CORE_PROJECT="{{core_project}}"
    . scripts/_lib.sh

    # Make persisted findings machine-independent: paths in the artifact are relative to this root,
    # so a baseline committed from one checkout diffs cleanly against a run from another (or from a
    # worktree). See balticporter.tir.CheckReport.relativise.
    write_run_props "$ROOT" "balticporter.reportPathRoot=$ROOT/{{gdx_src}}"
    REPORT="$ROOT/port-report/LibgdxCoreMigrate"

    # ABORT if the migration itself did not run. Piping straight into `grep wrote` discarded the exit
    # status, so an engine that failed to COMPILE printed nothing and the lane went on to measure the
    # PREVIOUS emit — reporting a stale number as a result. Two consecutive measurements were read as
    # "no change" when the change had never been built.
    MIGRATE_OUT=$({{sbt_migrate}} "{{corpus}}/runMain balticporter.corpus.libgdx.LibgdxCoreMigrate" 2>&1 | sed 's/\x1b\[[0-9;]*m//g')
    if ! grep -qE "wrote [0-9]+ Scala files" <<<"$MIGRATE_OUT"; then
      echo "!! MIGRATION DID NOT RUN — refusing to measure stale output"
      grep -E "^\[error\].*\.scala:[0-9]+|^\[error\] +\|" <<<"$MIGRATE_OUT" | head -20
      exit 1
    fi

    echo "-- migration (ALL checks, untruncated, as the migration printed them) --"
    # The whole block the migration emitted, in order, from its first line to its last. A `grep` for
    # named lines is how the checks got lost in the first place: it silently drops any line a future
    # check adds.
    sed -n '/building model over/,/wrote [0-9]* Scala files/p' <<<"$MIGRATE_OUT"

    echo
    echo "-- checks: persisted, untruncated, diffed against the baseline --"
    show_check_report "$REPORT"
    findings_baseline_guard "$REPORT"
    port_map_guard "$REPORT"

    echo
    break_residue {{gdx_module}}/src_managed/main/scala

    # The JDK is an INPUT to this measurement: the frontend read its class files on ONE JVM and
    # the compile below runs on another. Nothing compared them until an `override` emitted on
    # JDK 24 failed a JDK-22 compile with every other artifact flat (ENGINE-LIMITS M5.10).
    jdk_guard "$REPORT"
    echo "-- compile --"
    # DECLARED is whatever the port's own manifest published (`declared_dep_flags`, scripts/_lib.sh).
    # Before the Named target this was empty and the compile was standalone; now it carries `lls`,
    # because the emitted types reference `lowlevel.Nullable`.
    DECLARED=$(declared_dep_flags "$REPORT" | tr '\n' ' ')
    scala-cli compile --scala {{scala_version}} --server=false --jvm {{jdk_version}} $DECLARED {{gdx_module}}/src_managed/main/scala 2>&1 | sed 's/\x1b\[[0-9;]*m//g' > "$MEASURE_TMP"/gdxmeasure.txt
    CLI_STATUS=${PIPESTATUS[0]}
    # count ALL errors: coded `-- [Exxx] ... Error` AND bare `-- Error:` (e.g. "secondary constructor
    # must call a preceding constructor" carries no code). The coded-only count silently undercounts.
    ERRORS=$(grep -cE '^-- (\[E[0-9]+\] )?.*Error' "$MEASURE_TMP"/gdxmeasure.txt)
    compile_guard "$CLI_STATUS" "$ERRORS" "$MEASURE_TMP"/gdxmeasure.txt
    echo "TOTAL ERRORS: $ERRORS  (coded $(grep -cE '\[E[0-9]+\].*Error' "$MEASURE_TMP"/gdxmeasure.txt) + bare $(grep -cE '^-- Error:' "$MEASURE_TMP"/gdxmeasure.txt))"
    error_baseline_guard "$ERRORS" "$REPORT"
    grep -oE "\[E[0-9]+\][^:]*Error" "$MEASURE_TMP"/gdxmeasure.txt | sort | uniq -c | sort -rn | head
    echo "-- bare (uncoded) errors by message --"
    grep -A1 '^-- Error:' "$MEASURE_TMP"/gdxmeasure.txt | grep -vE '^-- Error:|^--$' | sed -E 's/^[0-9]+ \|//; s/[0-9]+//g' | sed -E 's/^ +//' | sort | uniq -c | sort -rn | head

    # Cross-platform compile gates (ENGINE-LIMITS P1: a COMPILE gate, not a portability gate).
    # Same deps as the JVM compile — the emitted types reference `lowlevel.Nullable`.
    xplat_compile scala-js {{scala_version}} "$REPORT" gdxmeasure {{gdx_module}}/src_managed/main/scala -- $DECLARED
    xplat_compile scala-native {{scala_version}} "$REPORT" gdxmeasure {{gdx_module}}/src_managed/main/scala -- $DECLARED

    # Reference-flags compile (DESIGN.md §8.24): the reference build's own scalacOptions.
    flags_compile {{scala_version}} "$REPORT" gdxmeasure "{{sge_strict_flags}}" {{gdx_module}}/src_managed/main/scala -- $DECLARED

    # A count is not a triage. Join every error back to the member and the JAVA LINE it came from, and
    # split it into "at a region the engine marked approximate" vs "engine gap" (DESIGN.md §6.3).
    # With no markers minted yet everything lands in the second lane — which is the honest answer,
    # and the lane an agent in another repository has to act on.
    echo
    echo "-- correlation: every error located to its member and its Java origin --"
    correlate "$REPORT/run-latest" --scalac "$MEASURE_TMP"/gdxmeasure.txt --srcmap "$REPORT/run-latest/srcmap.tsv"

    headline "$ERRORS" "$REPORT"

# ---------------------------------------------------------------------------------------------
# libGDX's own JUnit suite — re-emitted and compiled TOGETHER with the ported core, then RUN.
#
# The tests are the port's only BEHAVIOURAL gate; everything `gdx-measure` reports is "compiles".
# Note the discovery check: a JUnit suite with no @Test annotations runs ZERO tests and reports
# success, which is exactly the silent-omission failure this project keeps finding.
# ---------------------------------------------------------------------------------------------
[doc("libGDX's own suite — the same, then RUN it")]
gdx-test-measure:
    #!/usr/bin/env bash
    cd "{{root}}"
    ROOT="$(pwd)"
    export CORE_PROJECT="{{core_project}}"
    . scripts/_lib.sh

    write_run_props "$ROOT" "balticporter.reportPathRoot=$ROOT/{{gdx_src}}"
    REPORT="$ROOT/port-report/LibgdxTestMigrate"

    # ABORT if the migration did not run — the same stale-output defect fixed in `gdx-measure`: piping
    # into grep discards the exit status, so an engine that fails to COMPILE measures the PREVIOUS emit
    # and reports it as a result.
    MIGRATE_OUT=$({{sbt_migrate}} "{{corpus}}/runMain balticporter.corpus.libgdx.LibgdxTestMigrate" 2>&1 | sed 's/\x1b\[[0-9;]*m//g')
    if ! grep -qE "wrote [0-9]+ Scala test files" <<<"$MIGRATE_OUT"; then
      echo "!! TEST MIGRATION DID NOT RUN — refusing to measure stale output"
      grep -E "^\[error\].*\.scala:[0-9]+" <<<"$MIGRATE_OUT" | head -20
      exit 1
    fi

    echo "-- migration (every line it printed) --"
    sed -n '/building model over/,/wrote [0-9]* Scala test files/p' <<<"$MIGRATE_OUT"

    echo
    echo "-- checks: persisted, untruncated, diffed against the baseline --"
    show_check_report "$REPORT"
    findings_baseline_guard "$REPORT"
    port_map_guard "$REPORT"

    echo
    echo "-- test discovery --"
    # Count what each FRAMEWORK would actually discover. A ported suite is MUnit (`test("name") {…}`)
    # and the residue is still JUnit (`@Test`), so counting only annotations under-reports by every
    # converted suite — the check must sum both or it lies in the safe-looking direction.
    JAVA_TESTS=$(java_test_count {{gdx_src}}/test)
    JUNIT_LEFT=$(junit_residue {{gdx_module}}/src_managed/test/scala)
    MUNIT_TESTS=$(munit_emitted {{gdx_module}}/src_managed/test/scala)
    SCALA_TESTS=$((JUNIT_LEFT + MUNIT_TESTS))
    echo "@Test in Java: $JAVA_TESTS   discoverable in emitted Scala: $SCALA_TESTS (munit $MUNIT_TESTS + junit $JUNIT_LEFT)"
    # …and the LOSS IS BASELINED (scripts/_lib.sh). Printed and not gated, this was a digit inside a
    # line an operator reads past; the verdict is deferred to `headline` so the compile still runs.
    test_discovery_guard "$JAVA_TESTS" "$SCALA_TESTS" "$REPORT"

    echo
    break_residue {{gdx_module}}/src_managed/test/scala

    # The JDK is an INPUT to this measurement: the frontend read its class files on ONE JVM and
    # the compile below runs on another. Nothing compared them until an `override` emitted on
    # JDK 24 failed a JDK-22 compile with every other artifact flat (ENGINE-LIMITS M5.10).
    jdk_guard "$REPORT"
    echo "-- compile --"
    # `{{gdx_module}}/src/test/scala` is the HAND-WRITTEN half of this port's test source set, and it
    # is on the line for the same reason `ported/sge-screens/src` and `ported/sge-vfx/src` are on theirs: an
    # emitted suite the globals policy marks `selfSupplied` gets `private given sge.Sge =
    # sge.SgeTestFixture.testSge()`, and the fixture is a `src/` file a human may write where the
    # generated one is not (CLAUDE.md §5.5, `ENGINE-LIMITS.md` CT7). Leaving it off compiles the
    # emitted suite against a fixture that is not there — one error, and it is the port's own.
    scala-cli compile --test --scala {{scala_version}} --server=false --jvm {{jdk_version}} {{gdx_deps}} \
      {{gdx_module}}/src_managed/main/scala {{gdx_module}}/src_managed/test/scala \
      {{gdx_module}}/src/test/scala 2>&1 | sed 's/\x1b\[[0-9;]*m//g' > "$MEASURE_TMP"/gdxtestmeasure.txt
    CLI_STATUS=${PIPESTATUS[0]}
    ERRORS=$(grep -cE '^-- (\[E[0-9]+\] )?.*Error' "$MEASURE_TMP"/gdxtestmeasure.txt)
    compile_guard "$CLI_STATUS" "$ERRORS" "$MEASURE_TMP"/gdxtestmeasure.txt
    echo "TOTAL ERRORS: $ERRORS"
    error_baseline_guard "$ERRORS" "$REPORT"
    grep -oE "\[E[0-9]+\][^:]*Error" "$MEASURE_TMP"/gdxtestmeasure.txt | sort | uniq -c | sort -rn | head

    # Cross-platform compile gates — same source dirs and deps as the JVM compile.
    xplat_compile scala-js {{scala_version}} "$REPORT" gdxtestmeasure \
      {{gdx_module}}/src_managed/main/scala {{gdx_module}}/src_managed/test/scala \
      {{gdx_module}}/src/test/scala -- --test {{gdx_deps}}
    xplat_compile scala-native {{scala_version}} "$REPORT" gdxtestmeasure \
      {{gdx_module}}/src_managed/main/scala {{gdx_module}}/src_managed/test/scala \
      {{gdx_module}}/src/test/scala -- --test {{gdx_deps}}

    # Reference-flags compile (DESIGN.md §8.24): the reference build's own scalacOptions.
    flags_compile {{scala_version}} "$REPORT" gdxtestmeasure "{{sge_strict_flags}}" {{gdx_module}}/src_managed/main/scala {{gdx_module}}/src_managed/test/scala {{gdx_module}}/src/test/scala -- --test {{gdx_deps}}

    # -------------------------------------------------------------------------------------------
    # RUN them. Compiling a test suite measures nothing about behaviour, and CLAUDE.md §4.4 lists ten
    # Java forms that translate to VALID Scala meaning something else — reference `==`, `x++` as a
    # value, `break`/`continue`, `switch` fall-out, a dropped `super(args)`, `@Before`. Not one of them
    # moves the error count above. Running the suite is the only gate that sees them.
    # -------------------------------------------------------------------------------------------
    if [ "$ERRORS" = "0" ]; then
      echo
      echo "-- run --"
      scala-cli test --scala {{scala_version}} --server=false --jvm {{jdk_version}} {{gdx_run_deps}} \
        -Duser.language=en -Duser.country=US \
        {{gdx_module}}/src_managed/main/scala {{gdx_module}}/src_managed/test/scala \
        {{gdx_module}}/src/test/scala 2>&1 |
        sed 's/\x1b\[[0-9;]*m//g' > "$MEASURE_TMP"/gdxtestrun.txt
      reconcile_outcomes "$MEASURE_TMP"/gdxtestrun.txt "$MUNIT_TESTS"; RECONCILED=$?

      # Anchor every failure on the first stack frame that lands in PORTED code and resolve it, through
      # both ports' source maps, to a member and a Java origin — then diff the pass/fail sets against
      # the baseline. A newly-failing test whose member also changed digest is the highest-value signal
      # this engine can produce, and it is the only lane that catches a §4.4 regression.
      echo
      echo "-- correlation: test failures located to members and Java origins --"
      correlate "$REPORT/run-latest" --tests "$MEASURE_TMP"/gdxtestrun.txt \
        --srcmap "$ROOT/port-report/LibgdxCoreMigrate/run-latest/srcmap.tsv" \
        --srcmap "test=$REPORT/run-latest/srcmap.tsv"
      # THE GATE for a test that stopped RUNNING — the diff `correlate` just wrote is the only
      # thing that can tell a NEW skip from one this port has accepted (scripts/_lib.sh).
      test_outcome_guard "$REPORT/run-latest" "$RECONCILED" || exit 1
    else
      echo "(not running the suite: it does not compile — a test that cannot run is not a test that passed)"
    fi

    headline "$ERRORS" "$REPORT"

# ---------------------------------------------------------------------------------------------
# Ashley (main + its JUnit suite), compiled BOTH together with the ported libGDX core.
#
# Ashley is a DEPENDENT port (RuntimeMode.Dependency): the collection shims are vendored by
# sge, so both source sets must be on the same scala-cli invocation. Compiling sge-ecs
# alone measures nothing — every one of its 21 files resolves against libGDX.
# ---------------------------------------------------------------------------------------------
[doc("Ashley + its suite, compiled WITH libGDX core (a dependent port)")]
ashley-measure:
    #!/usr/bin/env bash
    cd "{{root}}"
    ROOT="$(pwd)"
    export CORE_PROJECT="{{core_project}}"
    . scripts/_lib.sh

    write_run_props "$ROOT" "balticporter.reportPathRoot=$ROOT/{{ashley_src}}"
    REPORT="$ROOT/port-report/AshleyMigrate"
    TREPORT="$ROOT/port-report/AshleyTestMigrate"

    for M in AshleyMigrate AshleyTestMigrate; do
      OUT=$({{sbt_migrate}} "{{corpus}}/runMain balticporter.corpus.ashley.$M" 2>&1 | sed 's/\x1b\[[0-9;]*m//g')
      if ! grep -qE "wrote [0-9]+ Scala( test)? files" <<<"$OUT"; then
        echo "!! $M DID NOT RUN — refusing to measure stale output"
        grep -E "^\[error\].*\.scala:[0-9]+|^\[error\] +\|" <<<"$OUT" | head -20
        exit 1
      fi
      echo "-- $M (every line it printed) --"
      sed -n '/building model over/,/wrote [0-9]* Scala\( test\)\? files/p' <<<"$OUT"
      echo
    done

    echo "-- checks: persisted, untruncated, diffed against the baseline --"
    show_check_report "$REPORT"
    findings_baseline_guard "$REPORT"
    port_map_guard "$REPORT"
    show_check_report "$TREPORT"
    findings_baseline_guard "$TREPORT"
    port_map_guard "$TREPORT"

    echo
    echo "-- test discovery --"
    # The same both-frameworks sum `gdx-test-measure` uses: a ported suite is MUnit and any residue is
    # still JUnit, so counting only one under-reports by every converted suite — in the safe-looking
    # direction, which is the dangerous one.
    JAVA_TESTS=$(java_test_count {{ashley_src}}/ashley/tests)
    JUNIT_LEFT=$(junit_residue {{ashley_module}}/src_managed/test/scala)
    MUNIT_TESTS=$(munit_emitted {{ashley_module}}/src_managed/test/scala)
    SCALA_TESTS=$((JUNIT_LEFT + MUNIT_TESTS))
    echo "@Test in Java: $JAVA_TESTS   discoverable in emitted Scala: $SCALA_TESTS (munit $MUNIT_TESTS + junit $JUNIT_LEFT)"
    # …and the LOSS IS BASELINED (scripts/_lib.sh). Printed and not gated, this was a digit inside a
    # line an operator reads past; the verdict is deferred to `headline` so the compile still runs.
    test_discovery_guard "$JAVA_TESTS" "$SCALA_TESTS" "$TREPORT"

    DEPS="{{ashley_deps}}"

    echo
    break_residue {{ashley_module}}/src_managed

    # The JDK is an INPUT to this measurement: the frontend read its class files on ONE JVM and
    # the compile below runs on another. Nothing compared them until an `override` emitted on
    # JDK 24 failed a JDK-22 compile with every other artifact flat (ENGINE-LIMITS M5.10).
    jdk_guard "$REPORT"
    echo "-- compile --"
    # NOTE the ANSI strip: without it `grep -cE '^-- .*Error'` matches nothing and reports 0 for a port
    # that does not compile — a false NEGATIVE on the headline number.
    scala-cli compile --test --scala {{scala_version}} --server=false --jvm {{jdk_version}} $DEPS \
      {{gdx_module}}/src_managed/main/scala {{ashley_module}}/src_managed/main/scala {{ashley_module}}/src_managed/test/scala \
      2>&1 | sed 's/\x1b\[[0-9;]*m//g' > "$MEASURE_TMP"/ashleymeasure.txt
    CLI_STATUS=${PIPESTATUS[0]}
    ERRORS=$(grep -cE '^-- (\[E[0-9]+\] )?.*Error' "$MEASURE_TMP"/ashleymeasure.txt)
    compile_guard "$CLI_STATUS" "$ERRORS" "$MEASURE_TMP"/ashleymeasure.txt
    echo "TOTAL ERRORS: $ERRORS  (coded $(grep -cE '\[E[0-9]+\].*Error' "$MEASURE_TMP"/ashleymeasure.txt) + bare $(grep -cE '^-- Error:' "$MEASURE_TMP"/ashleymeasure.txt))"
    error_baseline_guard "$ERRORS" "$TREPORT"
    grep -oE "\[E[0-9]+\][^:]*Error" "$MEASURE_TMP"/ashleymeasure.txt | sort | uniq -c | sort -rn | head

    # Cross-platform compile gates — same source dirs and deps as the JVM compile.
    xplat_compile scala-js {{scala_version}} "$TREPORT" ashleymeasure \
      {{gdx_module}}/src_managed/main/scala {{ashley_module}}/src_managed/main/scala {{ashley_module}}/src_managed/test/scala -- --test {{ashley_deps}}
    xplat_compile scala-native {{scala_version}} "$TREPORT" ashleymeasure \
      {{gdx_module}}/src_managed/main/scala {{ashley_module}}/src_managed/main/scala {{ashley_module}}/src_managed/test/scala -- --test {{ashley_deps}}

    # Reference-flags compile (DESIGN.md §8.24): the reference build's own scalacOptions.
    flags_compile {{scala_version}} "$TREPORT" ashleymeasure "{{sge_relaxed_flags}}" {{gdx_module}}/src_managed/main/scala {{ashley_module}}/src_managed/main/scala {{ashley_module}}/src_managed/test/scala -- --test {{ashley_deps}}

    if [ "$ERRORS" = "0" ]; then
      echo
      echo "-- run --"
      scala-cli test --scala {{scala_version}} --server=false --jvm {{jdk_version}} $DEPS -Duser.language=en -Duser.country=US \
        {{gdx_module}}/src_managed/main/scala {{ashley_module}}/src_managed/main/scala {{ashley_module}}/src_managed/test/scala \
        2>&1 | sed 's/\x1b\[[0-9;]*m//g' > "$MEASURE_TMP"/ashleyrun.txt
      reconcile_outcomes "$MEASURE_TMP"/ashleyrun.txt "$MUNIT_TESTS"; RECONCILED=$?
      echo
      echo "-- correlation: test failures located to members and Java origins --"
      # BOTH ports' maps: only the library's own map can anchor a failure on the member that threw,
      # and only the suite's can name the test. libGDX's is passed too — a stack that reaches the base
      # is exactly what a dependent's failure looks like.
      correlate "$TREPORT/run-latest" --tests "$MEASURE_TMP"/ashleyrun.txt \
        --srcmap "$ROOT/port-report/LibgdxCoreMigrate/run-latest/srcmap.tsv" \
        --srcmap "$REPORT/run-latest/srcmap.tsv" \
        --srcmap "test=$TREPORT/run-latest/srcmap.tsv"
      # THE GATE for a test that stopped RUNNING — the diff `correlate` just wrote is the only
      # thing that can tell a NEW skip from one this port has accepted (scripts/_lib.sh).
      test_outcome_guard "$TREPORT/run-latest" "$RECONCILED" || exit 1
    else
      echo "(not running the suite: it does not compile — a test that cannot run is not a test that passed)"
    fi

    headline "$ERRORS" "$TREPORT" "$REPORT"

# ---------------------------------------------------------------------------------------------
# anim8-gdx, compiled TOGETHER with the ported libGDX core.
#
# A DEPENDENT port with the same shape as Ashley's — every one of its 16 files resolves against
# libGDX, the collection shims are vendored by sge, so both source sets must be on the same
# scala-cli invocation and this lane must run AFTER `gdx-measure` has re-emitted the base.
#
# WHERE THIS LANE DIFFERS FROM EVERY OTHER ONE, and it is not a shortcut: anim8 has NO upstream
# suite. Its `src/test/java` holds 20 files and ZERO `@Test` annotations — every one is an
# `ApplicationAdapter` demo or a startup bench driven by `gdx-backend-lwjgl3`, and no backend is
# ported. So there is no `Anim8TestMigrate` and no emitted test source set; the port's behavioural
# gate is the HAND-WRITTEN MUnit suite committed under `ported/sge-anim8/src/test/scala` (CLAUDE.md §5.5:
# `src/` is the hand-written half of a port). The discovery block below states both numbers
# explicitly rather than letting `0 == 0` read as agreement — a suite with no discoverable tests
# runs ZERO and reports SUCCESS, which is the exact failure `java_test_count` exists to catch, and
# a lane whose java side is legitimately zero must say so out loud or it teaches its reader nothing.
# ---------------------------------------------------------------------------------------------
[doc("anim8-gdx, compiled WITH libGDX core (a dependent port)")]
anim8-measure:
    #!/usr/bin/env bash
    cd "{{root}}"
    ROOT="$(pwd)"
    export CORE_PROJECT="{{core_project}}"
    . scripts/_lib.sh

    write_run_props "$ROOT" "balticporter.reportPathRoot=$ROOT/{{anim8_src}}"
    REPORT="$ROOT/port-report/Anim8Migrate"

    OUT=$({{sbt_migrate}} "{{corpus}}/runMain balticporter.corpus.anim8.Anim8Migrate" 2>&1 | sed 's/\x1b\[[0-9;]*m//g')
    if ! grep -qE "wrote [0-9]+ Scala files" <<<"$OUT"; then
      echo "!! Anim8Migrate DID NOT RUN — refusing to measure stale output"
      grep -E "^\[error\].*\.scala:[0-9]+|^\[error\] +\|" <<<"$OUT" | head -20
      exit 1
    fi
    echo "-- Anim8Migrate (every line it printed) --"
    sed -n '/building model over/,/wrote [0-9]* Scala files/p' <<<"$OUT"
    echo

    echo "-- checks: persisted, untruncated, diffed against the baseline --"
    show_check_report "$REPORT"
    findings_baseline_guard "$REPORT"
    port_map_guard "$REPORT"

    echo
    echo "-- test discovery --"
    # The java count is computed and printed even though it is expected to be 0: an upstream that
    # gains a suite must show up here rather than being assumed away by a comment.
    JAVA_TESTS=$(java_test_count {{anim8_src}}/src/test)
    # `munit_emitted`, not a grep written out here — the four hand-written-suite lanes each carried
    # their OWN copy of the shared counter's anchor, so the day the shared one learned MUnit's
    # multi-line and interpolated registrations they would have gone on reading the old number, at
    # no diff and no finding. One value, one spelling (CLAUDE.md §1.5, read at an instrument).
    HAND_TESTS=$(munit_emitted {{anim8_module}}/src/test/scala)
    EMITTED_TESTS=$(munit_emitted {{anim8_module}}/src_managed/test/scala)
    echo "@Test in upstream java: $JAVA_TESTS (upstream ships DEMOS, not a suite — nothing to port)"
    echo "hand-written munit in {{anim8_module}}/src/test/scala: $HAND_TESTS   emitted: $EMITTED_TESTS"
    [ "$JAVA_TESTS" != "0" ] && echo "!! UPSTREAM NOW HAS A SUITE — $JAVA_TESTS @Test method(s) that this port does not migrate; add an Anim8TestMigrate"
    [ "$HAND_TESTS" = "0" ] && echo "!! NO BEHAVIOURAL GATE — this port would compile and prove nothing (CLAUDE.md §3)"

    echo
    break_residue {{anim8_module}}/src_managed

    # The JDK is an INPUT to this measurement: the frontend read its class files on ONE JVM and
    # the compile below runs on another. Nothing compared them until an `override` emitted on
    # JDK 24 failed a JDK-22 compile with every other artifact flat (ENGINE-LIMITS M5.10).
    jdk_guard "$REPORT"
    echo "-- compile --"
    # NOTE the ANSI strip: without it `grep -cE '^-- .*Error'` matches nothing and reports 0 for a
    # port that does not compile — a false NEGATIVE on the headline number.
    DEPS="{{anim8_deps}}"
    scala-cli compile --test --scala {{scala_version}} --server=false --jvm {{jdk_version}} $DEPS \
      {{gdx_module}}/src_managed/main/scala {{anim8_module}}/src_managed/main/scala {{anim8_module}}/src/test/scala \
      2>&1 | sed 's/\x1b\[[0-9;]*m//g' > "$MEASURE_TMP"/anim8measure.txt
    CLI_STATUS=${PIPESTATUS[0]}
    ERRORS=$(grep -cE '^-- (\[E[0-9]+\] )?.*Error' "$MEASURE_TMP"/anim8measure.txt)
    compile_guard "$CLI_STATUS" "$ERRORS" "$MEASURE_TMP"/anim8measure.txt
    echo "TOTAL ERRORS: $ERRORS  (coded $(grep -cE '\[E[0-9]+\].*Error' "$MEASURE_TMP"/anim8measure.txt) + bare $(grep -cE '^-- Error:' "$MEASURE_TMP"/anim8measure.txt))"
    error_baseline_guard "$ERRORS" "$REPORT"
    grep -oE "\[E[0-9]+\][^:]*Error" "$MEASURE_TMP"/anim8measure.txt | sort | uniq -c | sort -rn | head
    echo "-- bare (uncoded) errors by message --"
    grep -A1 '^-- Error:' "$MEASURE_TMP"/anim8measure.txt | grep -vE '^-- Error:|^--$' | sed -E 's/^[0-9]+ \|//; s/[0-9]+//g' | sed -E 's/^ +//' | sort | uniq -c | sort -rn | head

    # Cross-platform compile gates — same source dirs and deps as the JVM compile.
    xplat_compile scala-js {{scala_version}} "$REPORT" anim8measure \
      {{gdx_module}}/src_managed/main/scala {{anim8_module}}/src_managed/main/scala -- {{anim8_deps}}
    xplat_compile scala-native {{scala_version}} "$REPORT" anim8measure \
      {{gdx_module}}/src_managed/main/scala {{anim8_module}}/src_managed/main/scala -- {{anim8_deps}}

    # Reference-flags compile (DESIGN.md §8.24): the reference build's own scalacOptions.
    flags_compile {{scala_version}} "$REPORT" anim8measure "{{sge_relaxed_flags}}" {{gdx_module}}/src_managed/main/scala {{anim8_module}}/src_managed/main/scala -- {{anim8_deps}}

    if [ "$ERRORS" = "0" ]; then
      echo
      echo "-- run --"
      scala-cli test --scala {{scala_version}} --server=false --jvm {{jdk_version}} $DEPS -Duser.language=en -Duser.country=US \
        {{gdx_module}}/src_managed/main/scala {{anim8_module}}/src_managed/main/scala {{anim8_module}}/src/test/scala \
        2>&1 | sed 's/\x1b\[[0-9;]*m//g' > "$MEASURE_TMP"/anim8run.txt
      reconcile_outcomes "$MEASURE_TMP"/anim8run.txt "$HAND_TESTS"; RECONCILED=$?
      echo
      echo "-- correlation: test failures located to members and Java origins --"
      # BOTH ports' maps. There is no `test=` map: the suite is hand-written, so no srcmap can
      # anchor a test FRAME on a Java origin — but a failure inside the LIBRARY still resolves
      # through anim8's own map, and one that reaches the base resolves through libGDX's, which is
      # exactly what a dependent's failure looks like.
      correlate "$REPORT/run-latest" --tests "$MEASURE_TMP"/anim8run.txt \
        --srcmap "$ROOT/port-report/LibgdxCoreMigrate/run-latest/srcmap.tsv" \
        --srcmap "$REPORT/run-latest/srcmap.tsv"
      # THE GATE for a test that stopped RUNNING — the diff `correlate` just wrote is the only
      # thing that can tell a NEW skip from one this port has accepted (scripts/_lib.sh).
      test_outcome_guard "$REPORT/run-latest" "$RECONCILED" || exit 1
    else
      echo
      echo "-- correlation: every error located to its member and its Java origin --"
      correlate "$REPORT/run-latest" --scalac "$MEASURE_TMP"/anim8measure.txt \
        --srcmap "$ROOT/port-report/LibgdxCoreMigrate/run-latest/srcmap.tsv" \
        --srcmap "$REPORT/run-latest/srcmap.tsv"
      echo "(not running the suite: it does not compile — a test that cannot run is not a test that passed)"
    fi

    headline "$ERRORS" "$REPORT"

# ---------------------------------------------------------------------------------------------
# gdx-gltf (main + its one JUnit suite), compiled TOGETHER with the ported libGDX core.
#
# A DEPENDENT port of the same shape as Ashley's and anim8's — all 118 distinct `com.badlogic.*`
# imports across its 135 files resolve inside `gdx/src`, and the collection shims are vendored by
# sge — so both source sets must be on the same scala-cli invocation and this lane must run
# AFTER `gdx-measure` has re-emitted the base.
#
# WHERE THIS LANE'S TEST DISCOVERY EARNS ITS KEEP. Upstream `gltf/test` holds SEVEN files and only
# ONE of them is a suite; the other six are `extends Game` / `extends ApplicationAdapter` demos with
# a `main` that opens an lwjgl window, and the ONLY import in the whole checkout that `gdx/src`
# cannot resolve is theirs (`com.badlogic.gdx.backends.lwjgl`). `GltfTestMigrate` therefore names
# its one input file rather than globbing, and this block counts `@Test` over the WHOLE tree so the
# 8 is a number the lane re-derives and not a claim in a comment — and so the day a second file
# gains a real `@Test`, the equality guard below says so instead of absorbing it.
# ---------------------------------------------------------------------------------------------
[doc("gdx-gltf + its suite, compiled WITH libGDX core (a dependent port)")]
gltf-measure:
    #!/usr/bin/env bash
    cd "{{root}}"
    ROOT="$(pwd)"
    export CORE_PROJECT="{{core_project}}"
    . scripts/_lib.sh

    write_run_props "$ROOT" "balticporter.reportPathRoot=$ROOT/{{gltf_src}}"
    REPORT="$ROOT/port-report/GltfMigrate"
    TREPORT="$ROOT/port-report/GltfTestMigrate"

    for M in GltfMigrate GltfTestMigrate; do
      OUT=$({{sbt_migrate}} "{{corpus}}/runMain balticporter.corpus.gltf.$M" 2>&1 | sed 's/\x1b\[[0-9;]*m//g')
      if ! grep -qE "wrote [0-9]+ Scala( test)? files" <<<"$OUT"; then
        echo "!! $M DID NOT RUN — refusing to measure stale output"
        grep -E "^\[error\].*\.scala:[0-9]+|^\[error\] +\|" <<<"$OUT" | head -20
        exit 1
      fi
      echo "-- $M (every line it printed) --"
      sed -n '/building model over/,/wrote [0-9]* Scala\( test\)\? files/p' <<<"$OUT"
      echo
    done

    echo "-- checks: persisted, untruncated, diffed against the baseline --"
    show_check_report "$REPORT"
    findings_baseline_guard "$REPORT"
    port_map_guard "$REPORT"
    show_check_report "$TREPORT"
    findings_baseline_guard "$TREPORT"
    port_map_guard "$TREPORT"

    echo
    echo "-- test discovery --"
    # Both frameworks summed, as `gdx-test-measure` and `ashley-measure` do: a ported suite is MUnit
    # and any residue is still JUnit, so counting one under-reports in the safe-LOOKING direction.
    JAVA_TESTS=$(java_test_count {{gltf_tests}})
    JUNIT_LEFT=$(junit_residue {{gltf_module}}/src_managed/test/scala)
    MUNIT_TESTS=$(munit_emitted {{gltf_module}}/src_managed/test/scala)
    SCALA_TESTS=$((JUNIT_LEFT + MUNIT_TESTS))
    echo "@Test in Java (whole {{gltf_tests}} tree): $JAVA_TESTS   discoverable in emitted Scala: $SCALA_TESTS (munit $MUNIT_TESTS + junit $JUNIT_LEFT)"
    # …and the LOSS IS BASELINED (scripts/_lib.sh). Printed and not gated, this was a digit inside a
    # line an operator reads past; the verdict is deferred to `headline` so the compile still runs.
    test_discovery_guard "$JAVA_TESTS" "$SCALA_TESTS" "$TREPORT"
    # …and the HAND-WRITTEN half, counted and printed separately rather than summed into the line
    # above. Upstream's whole suite is 8 attribute-comparison tests, which says nothing about the
    # glTF reader that is most of the library; `ported/sge-gltf/src/test/scala` is what covers the §4.4
    # hazards in `GLTFTypes` (CLAUDE.md §5.5 — `src/` is the hand-written half of a port). Keeping
    # the two numbers apart is the point: a ported test and a written one are different evidence.
    HAND_TESTS=$(munit_emitted {{gltf_module}}/src/test/scala)
    echo "hand-written munit in {{gltf_module}}/src/test/scala: $HAND_TESTS"
    [ "$HAND_TESTS" = "0" ] && echo "!! the hand-written suite is GONE — the port's only cover for the loader would be missing"
    ALL_TESTS=$((MUNIT_TESTS + HAND_TESTS))

    DEPS="{{gltf_deps}}"

    echo
    break_residue {{gltf_module}}/src_managed

    # The JDK is an INPUT to this measurement: the frontend read its class files on ONE JVM and
    # the compile below runs on another. Nothing compared them until an `override` emitted on
    # JDK 24 failed a JDK-22 compile with every other artifact flat (ENGINE-LIMITS M5.10).
    jdk_guard "$REPORT"
    echo "-- compile --"
    # NOTE the ANSI strip: without it `grep -cE '^-- .*Error'` matches nothing and reports 0 for a port
    # that does not compile — a false NEGATIVE on the headline number.
    scala-cli compile --test --scala {{scala_version}} --server=false --jvm {{jdk_version}} $DEPS \
      {{gdx_module}}/src_managed/main/scala {{gltf_module}}/src_managed/main/scala \
      {{gltf_module}}/src_managed/test/scala {{gltf_module}}/src/test/scala \
      2>&1 | sed 's/\x1b\[[0-9;]*m//g' > "$MEASURE_TMP"/gltfmeasure.txt
    CLI_STATUS=${PIPESTATUS[0]}
    ERRORS=$(grep -cE '^-- (\[E[0-9]+\] )?.*Error' "$MEASURE_TMP"/gltfmeasure.txt)
    compile_guard "$CLI_STATUS" "$ERRORS" "$MEASURE_TMP"/gltfmeasure.txt
    echo "TOTAL ERRORS: $ERRORS  (coded $(grep -cE '\[E[0-9]+\].*Error' "$MEASURE_TMP"/gltfmeasure.txt) + bare $(grep -cE '^-- Error:' "$MEASURE_TMP"/gltfmeasure.txt))"
    error_baseline_guard "$ERRORS" "$TREPORT"
    grep -oE "\[E[0-9]+\][^:]*Error" "$MEASURE_TMP"/gltfmeasure.txt | sort | uniq -c | sort -rn | head
    echo "-- bare (uncoded) errors by message --"
    grep -A1 '^-- Error:' "$MEASURE_TMP"/gltfmeasure.txt | grep -vE '^-- Error:|^--$' | sed -E 's/^[0-9]+ \|//; s/[0-9]+//g' | sed -E 's/^ +//' | sort | uniq -c | sort -rn | head

    # Cross-platform compile gates — same source dirs and deps as the JVM compile.
    xplat_compile scala-js {{scala_version}} "$TREPORT" gltfmeasure \
      {{gdx_module}}/src_managed/main/scala {{gltf_module}}/src_managed/main/scala {{gltf_module}}/src_managed/test/scala -- --test {{gltf_deps}}
    xplat_compile scala-native {{scala_version}} "$TREPORT" gltfmeasure \
      {{gdx_module}}/src_managed/main/scala {{gltf_module}}/src_managed/main/scala {{gltf_module}}/src_managed/test/scala -- --test {{gltf_deps}}

    # Reference-flags compile (DESIGN.md §8.24): the reference build's own scalacOptions.
    flags_compile {{scala_version}} "$TREPORT" gltfmeasure "{{sge_relaxed_flags}}" {{gdx_module}}/src_managed/main/scala {{gltf_module}}/src_managed/main/scala {{gltf_module}}/src_managed/test/scala -- --test {{gltf_deps}}

    if [ "$ERRORS" = "0" ]; then
      echo
      echo "-- run --"
      scala-cli test --scala {{scala_version}} --server=false --jvm {{jdk_version}} $DEPS -Duser.language=en -Duser.country=US \
        {{gdx_module}}/src_managed/main/scala {{gltf_module}}/src_managed/main/scala \
        {{gltf_module}}/src_managed/test/scala {{gltf_module}}/src/test/scala \
        2>&1 | sed 's/\x1b\[[0-9;]*m//g' > "$MEASURE_TMP"/gltfrun.txt
      # Reconciled against the SUM: both source sets are on the one invocation, so an outcome
      # count that matched only the ported half would report success for a hand-written suite that
      # never ran (CLAUDE.md §5.1).
      reconcile_outcomes "$MEASURE_TMP"/gltfrun.txt "$ALL_TESTS"; RECONCILED=$?
      echo
      echo "-- correlation: test failures located to members and Java origins --"
      # All three maps: only the library's own can anchor a failure on the member that threw, only
      # the suite's can name the test, and libGDX's is passed because a stack that reaches the base
      # is exactly what a dependent's failure looks like.
      correlate "$TREPORT/run-latest" --tests "$MEASURE_TMP"/gltfrun.txt \
        --srcmap "$ROOT/port-report/LibgdxCoreMigrate/run-latest/srcmap.tsv" \
        --srcmap "$REPORT/run-latest/srcmap.tsv" \
        --srcmap "test=$TREPORT/run-latest/srcmap.tsv"
      # THE GATE for a test that stopped RUNNING — the diff `correlate` just wrote is the only
      # thing that can tell a NEW skip from one this port has accepted (scripts/_lib.sh).
      test_outcome_guard "$TREPORT/run-latest" "$RECONCILED" || exit 1
    else
      echo
      echo "-- correlation: every error located to its member and its Java origin --"
      correlate "$REPORT/run-latest" --scalac "$MEASURE_TMP"/gltfmeasure.txt \
        --srcmap "$ROOT/port-report/LibgdxCoreMigrate/run-latest/srcmap.tsv" \
        --srcmap "$REPORT/run-latest/srcmap.tsv" \
        --srcmap "test=$TREPORT/run-latest/srcmap.tsv"
      echo "(not running the suite: it does not compile — a test that cannot run is not a test that passed)"
    fi

    headline "$ERRORS" "$TREPORT" "$REPORT"

# ---------------------------------------------------------------------------------------------
# libgdx-screenmanager, compiled TOGETHER with the ported libGDX core.
#
# A DEPENDENT port of the same shape as Ashley's and anim8's — every one of its 22 files resolves
# against libGDX and the collection shims are vendored by sge, so both source sets are on
# one scala-cli invocation and this lane must run AFTER `gdx-measure` has re-emitted the base.
#
# TWO THINGS THIS LANE HAS THAT NO OTHER ONE DOES:
#
#  - **A HAND-WRITTEN SOURCE SET IN `src/main`.** Every other port's `src/main` is empty. This one
#    ships Scala for ten guacamole types (`com.github.crykn.guacamole:gdx`, a separate upstream this
#    corpus resolves against and does not port), which `TypeRedirectTransform` re-points the emitted
#    references at. They are on the compile because without them the port does not have a
#    `NestableFrameBuffer` — the type upstream depends on guacamole FOR.
#  - **A MIGRATED TEST COUNT OF ZERO THAT IS NOT AN OMISSION.** Upstream ships 12 `@Test`, and 10 of
#    them boot `gdx-backend-headless` or call `Mockito.mockStatic`/`spy` on the type under test. No
#    libGDX backend is ported and neither is bytecode instrumentation portable, so there is no
#    `ScreensTestMigrate`. The block below prints upstream, emitted and hand-written side by side
#    rather than letting `0` read as agreement — the same rule `anim8-measure` follows, and the day
#    a backend is ported it is this block that says the 12 became reachable.
# ---------------------------------------------------------------------------------------------
[doc("libgdx-screenmanager, compiled WITH libGDX core (a dependent port)")]
screens-measure:
    #!/usr/bin/env bash
    cd "{{root}}"
    ROOT="$(pwd)"
    export CORE_PROJECT="{{core_project}}"
    . scripts/_lib.sh

    write_run_props "$ROOT" "balticporter.reportPathRoot=$ROOT/{{screens_src}}"
    REPORT="$ROOT/port-report/ScreensMigrate"

    OUT=$({{sbt_migrate}} "{{corpus}}/runMain balticporter.corpus.screens.ScreensMigrate" 2>&1 | sed 's/\x1b\[[0-9;]*m//g')
    if ! grep -qE "wrote [0-9]+ Scala files" <<<"$OUT"; then
      echo "!! ScreensMigrate DID NOT RUN — refusing to measure stale output"
      grep -E "^\[error\].*\.scala:[0-9]+|^\[error\] +\|" <<<"$OUT" | head -20
      exit 1
    fi
    echo "-- ScreensMigrate (every line it printed) --"
    sed -n '/building model over/,/wrote [0-9]* Scala files/p' <<<"$OUT"
    echo

    echo "-- checks: persisted, untruncated, diffed against the baseline --"
    show_check_report "$REPORT"
    findings_baseline_guard "$REPORT"
    port_map_guard "$REPORT"

    echo
    echo "-- test discovery --"
    JAVA_TESTS=$(java_test_count {{screens_src}}/src/test)
    HAND_TESTS=$(munit_emitted {{screens_module}}/src/test/scala)
    EMITTED_TESTS=$(munit_emitted {{screens_module}}/src_managed/test/scala)
    echo "@Test in upstream java: $JAVA_TESTS   emitted by this port: $EMITTED_TESTS"
    echo "  (10 of the 12 need gdx-backend-headless — NO libGDX backend is ported — or Mockito"
    echo "   mockStatic/spy over the type under test, which is JVM bytecode instrumentation and"
    echo "   not portable to the Scala.js/Native targets this port exists for. See PROGRESS.md.)"
    echo "hand-written munit in {{screens_module}}/src/test/scala: $HAND_TESTS"
    [ "$JAVA_TESTS" != "12" ] && echo "!! UPSTREAM'S @Test COUNT MOVED ($JAVA_TESTS, was 12) — re-read whether the suite is now migratable"
    [ "$HAND_TESTS" = "0" ] && echo "!! NO BEHAVIOURAL GATE — this port would compile and prove nothing (CLAUDE.md §3)"

    echo
    break_residue {{screens_module}}/src_managed
    echo "-- hand-written support sources (CLAUDE.md §5.5: src/ is the hand-written half) --"
    echo "$(find {{screens_module}}/src/main/scala -name '*.scala' | wc -l | tr -d ' ') file(s), $(cat $(find {{screens_module}}/src/main/scala -name '*.scala') | wc -l | tr -d ' ') lines — the guacamole replacements TypeRedirectTransform points at"

    # The JDK is an INPUT to this measurement: the frontend read its class files on ONE JVM and
    # the compile below runs on another. Nothing compared them until an `override` emitted on
    # JDK 24 failed a JDK-22 compile with every other artifact flat (ENGINE-LIMITS M5.10).
    jdk_guard "$REPORT"
    echo "-- compile --"
    # NOTE the ANSI strip: without it `grep -cE '^-- .*Error'` matches nothing and reports 0 for a
    # port that does not compile — a false NEGATIVE on the headline number.
    DEPS="{{screens_deps}}"
    # `{{gdx_module}}/src/test/scala` is the BASE port's hand-written test fixture (`sge.SgeTestFixture`).
    # It is on this line because the base retires `Gdx` into a threaded context and this port's
    # hand-written suite has to construct one — the same fixture the base's own `selfSupplied` suite
    # is given, rather than a third copy of it in every dependent (CLAUDE.md §1.5's spirit, one
    # artifact down: a value the dependent imports, never policy it repeats).
    scala-cli compile --test --scala {{scala_version}} --server=false --jvm {{jdk_version}} $DEPS \
      {{gdx_module}}/src_managed/main/scala {{screens_module}}/src_managed/main/scala \
      {{screens_module}}/src/main/scala {{screens_module}}/src/test/scala \
      {{gdx_module}}/src/test/scala \
      2>&1 | sed 's/\x1b\[[0-9;]*m//g' > "$MEASURE_TMP"/screensmeasure.txt
    CLI_STATUS=${PIPESTATUS[0]}
    ERRORS=$(grep -cE '^-- (\[E[0-9]+\] )?.*Error' "$MEASURE_TMP"/screensmeasure.txt)
    compile_guard "$CLI_STATUS" "$ERRORS" "$MEASURE_TMP"/screensmeasure.txt
    echo "TOTAL ERRORS: $ERRORS  (coded $(grep -cE '\[E[0-9]+\].*Error' "$MEASURE_TMP"/screensmeasure.txt) + bare $(grep -cE '^-- Error:' "$MEASURE_TMP"/screensmeasure.txt))"
    error_baseline_guard "$ERRORS" "$REPORT"
    grep -oE "\[E[0-9]+\][^:]*Error" "$MEASURE_TMP"/screensmeasure.txt | sort | uniq -c | sort -rn | head
    echo "-- bare (uncoded) errors by message --"
    grep -A1 '^-- Error:' "$MEASURE_TMP"/screensmeasure.txt | grep -vE '^-- Error:|^--$' | sed -E 's/^[0-9]+ \|//; s/[0-9]+//g' | sed -E 's/^ +//' | sort | uniq -c | sort -rn | head

    # Cross-platform compile gates — same source dirs and deps as the JVM compile.
    xplat_compile scala-js {{scala_version}} "$REPORT" screensmeasure \
      {{gdx_module}}/src_managed/main/scala {{screens_module}}/src_managed/main/scala -- {{screens_deps}}
    xplat_compile scala-native {{scala_version}} "$REPORT" screensmeasure \
      {{gdx_module}}/src_managed/main/scala {{screens_module}}/src_managed/main/scala -- {{screens_deps}}

    # Reference-flags compile (DESIGN.md §8.24): the reference build's own scalacOptions.
    flags_compile {{scala_version}} "$REPORT" screensmeasure "{{sge_relaxed_flags}}" {{gdx_module}}/src_managed/main/scala {{screens_module}}/src_managed/main/scala -- {{screens_deps}}

    if [ "$ERRORS" = "0" ]; then
      echo
      echo "-- run --"
      scala-cli test --scala {{scala_version}} --server=false --jvm {{jdk_version}} $DEPS -Duser.language=en -Duser.country=US \
        {{gdx_module}}/src_managed/main/scala {{screens_module}}/src_managed/main/scala \
        {{screens_module}}/src/main/scala {{screens_module}}/src/test/scala \
        {{gdx_module}}/src/test/scala \
        2>&1 | sed 's/\x1b\[[0-9;]*m//g' > "$MEASURE_TMP"/screensrun.txt
      reconcile_outcomes "$MEASURE_TMP"/screensrun.txt "$HAND_TESTS"; RECONCILED=$?
      echo
      echo "-- correlation: test failures located to members and Java origins --"
      # BOTH ports' maps, and no `test=` map: the suite is hand-written, so no srcmap can anchor a
      # test FRAME on a Java origin — but a failure inside the LIBRARY still resolves through this
      # port's map, and one that reaches the base resolves through libGDX's.
      correlate "$REPORT/run-latest" --tests "$MEASURE_TMP"/screensrun.txt \
        --srcmap "$ROOT/port-report/LibgdxCoreMigrate/run-latest/srcmap.tsv" \
        --srcmap "$REPORT/run-latest/srcmap.tsv"
      # THE GATE for a test that stopped RUNNING — the diff `correlate` just wrote is the only
      # thing that can tell a NEW skip from one this port has accepted (scripts/_lib.sh).
      test_outcome_guard "$REPORT/run-latest" "$RECONCILED" || exit 1
    else
      echo
      echo "-- correlation: every error located to its member and its Java origin --"
      correlate "$REPORT/run-latest" --scalac "$MEASURE_TMP"/screensmeasure.txt \
        --srcmap "$ROOT/port-report/LibgdxCoreMigrate/run-latest/srcmap.tsv" \
        --srcmap "$REPORT/run-latest/srcmap.tsv"
      echo "(not running the suite: it does not compile — a test that cannot run is not a test that passed)"
    fi

    headline "$ERRORS" "$REPORT"

# ---------------------------------------------------------------------------------------------
# gdx-vfx, compiled TOGETHER with the ported libGDX core.
#
# A DEPENDENT port with the same shape as anim8's — every one of its 44 files resolves against
# libGDX, the collection shims are vendored by sge, so both source sets must be on the same
# scala-cli invocation and this lane must run AFTER `gdx-measure` has re-emitted the base.
#
# THE TEST STORY, stated rather than assumed: gdx-vfx ships NO test source set. The `@Test` census
# below runs over the WHOLE upstream checkout (library, gwt backend and the 74-file demo alike) and
# is expected to be 0 — this is the third corpus library with no upstream suite and the only one
# where the zero is total rather than "the test directory holds demos". The behavioural gate is
# therefore the HAND-WRITTEN MUnit suite committed under `ported/sge-vfx/src/test/scala` (CLAUDE.md §5.5:
# `src/` is the hand-written half of a port). Both numbers are printed, because `0 == 0` must not
# read as agreement — a suite with no discoverable tests runs ZERO and reports SUCCESS, which is
# the exact failure `java_test_count` exists to catch.
# ---------------------------------------------------------------------------------------------
[doc("gdx-vfx, compiled WITH libGDX core (a dependent port)")]
vfx-measure:
    #!/usr/bin/env bash
    cd "{{root}}"
    ROOT="$(pwd)"
    export CORE_PROJECT="{{core_project}}"
    . scripts/_lib.sh

    write_run_props "$ROOT" "balticporter.reportPathRoot=$ROOT/{{vfx_src}}"
    REPORT="$ROOT/port-report/VfxMigrate"

    OUT=$({{sbt_migrate}} "{{corpus}}/runMain balticporter.corpus.vfx.VfxMigrate" 2>&1 | sed 's/\x1b\[[0-9;]*m//g')
    if ! grep -qE "wrote [0-9]+ Scala files" <<<"$OUT"; then
      echo "!! VfxMigrate DID NOT RUN — refusing to measure stale output"
      grep -E "^\[error\].*\.scala:[0-9]+|^\[error\] +\|" <<<"$OUT" | head -20
      exit 1
    fi
    echo "-- VfxMigrate (every line it printed) --"
    sed -n '/building model over/,/wrote [0-9]* Scala files/p' <<<"$OUT"
    echo

    echo "-- checks: persisted, untruncated, diffed against the baseline --"
    show_check_report "$REPORT"
    findings_baseline_guard "$REPORT"
    port_map_guard "$REPORT"

    echo
    echo "-- test discovery --"
    JAVA_TESTS=$(java_test_count {{vfx_src}})
    HAND_TESTS=$(munit_emitted {{vfx_module}}/src/test/scala)
    EMITTED_TESTS=$(munit_emitted {{vfx_module}}/src_managed/test/scala)
    echo "@Test in upstream java (WHOLE checkout): $JAVA_TESTS (gdx-vfx ships no test source set — nothing to port)"
    echo "hand-written munit in {{vfx_module}}/src/test/scala: $HAND_TESTS   emitted: $EMITTED_TESTS"
    [ "$JAVA_TESTS" != "0" ] && echo "!! UPSTREAM NOW HAS A SUITE — $JAVA_TESTS @Test method(s) that this port does not migrate; add a VfxTestMigrate"
    [ "$HAND_TESTS" = "0" ] && echo "!! NO BEHAVIOURAL GATE — this port would compile and prove nothing (CLAUDE.md §3)"

    echo
    break_residue {{vfx_module}}/src_managed

    # The JDK is an INPUT to this measurement: the frontend read its class files on ONE JVM and
    # the compile below runs on another. Nothing compared them until an `override` emitted on
    # JDK 24 failed a JDK-22 compile with every other artifact flat (ENGINE-LIMITS M5.10).
    jdk_guard "$REPORT"
    echo "-- compile --"
    DEPS="{{vfx_deps}}"
    # `{{gdx_module}}/src/test/scala` is the BASE port's hand-written test fixture
    # (`sge.SgeTestFixture`), on this line for the reason `screens-measure` states: the base retires
    # `Gdx` into a threaded context and this port's hand-written suite has to construct one.
    scala-cli compile --test --scala {{scala_version}} --server=false --jvm {{jdk_version}} $DEPS \
      {{gdx_module}}/src_managed/main/scala {{vfx_module}}/src_managed/main/scala {{vfx_module}}/src/test/scala \
      {{gdx_module}}/src/test/scala \
      2>&1 | sed 's/\x1b\[[0-9;]*m//g' > "$MEASURE_TMP"/vfxmeasure.txt
    CLI_STATUS=${PIPESTATUS[0]}
    ERRORS=$(grep -cE '^-- (\[E[0-9]+\] )?.*Error' "$MEASURE_TMP"/vfxmeasure.txt)
    compile_guard "$CLI_STATUS" "$ERRORS" "$MEASURE_TMP"/vfxmeasure.txt
    echo "TOTAL ERRORS: $ERRORS  (coded $(grep -cE '\[E[0-9]+\].*Error' "$MEASURE_TMP"/vfxmeasure.txt) + bare $(grep -cE '^-- Error:' "$MEASURE_TMP"/vfxmeasure.txt))"
    error_baseline_guard "$ERRORS" "$REPORT"
    grep -oE "\[E[0-9]+\][^:]*Error" "$MEASURE_TMP"/vfxmeasure.txt | sort | uniq -c | sort -rn | head
    echo "-- bare (uncoded) errors by message --"
    grep -A1 '^-- Error:' "$MEASURE_TMP"/vfxmeasure.txt | grep -vE '^-- Error:|^--$' | sed -E 's/^[0-9]+ \|//; s/[0-9]+//g' | sed -E 's/^ +//' | sort | uniq -c | sort -rn | head

    # Cross-platform compile gates — same source dirs and deps as the JVM compile.
    xplat_compile scala-js {{scala_version}} "$REPORT" vfxmeasure \
      {{gdx_module}}/src_managed/main/scala {{vfx_module}}/src_managed/main/scala -- {{vfx_deps}}
    xplat_compile scala-native {{scala_version}} "$REPORT" vfxmeasure \
      {{gdx_module}}/src_managed/main/scala {{vfx_module}}/src_managed/main/scala -- {{vfx_deps}}

    # Reference-flags compile (DESIGN.md §8.24): the reference build's own scalacOptions.
    flags_compile {{scala_version}} "$REPORT" vfxmeasure "{{sge_relaxed_flags}}" {{gdx_module}}/src_managed/main/scala {{vfx_module}}/src_managed/main/scala -- {{vfx_deps}}

    if [ "$ERRORS" = "0" ]; then
      echo
      echo "-- run --"
      scala-cli test --scala {{scala_version}} --server=false --jvm {{jdk_version}} $DEPS -Duser.language=en -Duser.country=US \
        {{gdx_module}}/src_managed/main/scala {{vfx_module}}/src_managed/main/scala {{vfx_module}}/src/test/scala \
        {{gdx_module}}/src/test/scala \
        2>&1 | sed 's/\x1b\[[0-9;]*m//g' > "$MEASURE_TMP"/vfxrun.txt
      reconcile_outcomes "$MEASURE_TMP"/vfxrun.txt "$HAND_TESTS"; RECONCILED=$?
      echo
      echo "-- correlation: test failures located to members and Java origins --"
      # BOTH ports' maps. There is no `test=` map: the suite is hand-written, so no srcmap can
      # anchor a test FRAME on a Java origin — but a failure inside the LIBRARY still resolves
      # through vfx's own map, and one that reaches the base resolves through libGDX's, which is
      # exactly what a dependent's failure looks like.
      correlate "$REPORT/run-latest" --tests "$MEASURE_TMP"/vfxrun.txt \
        --srcmap "$ROOT/port-report/LibgdxCoreMigrate/run-latest/srcmap.tsv" \
        --srcmap "$REPORT/run-latest/srcmap.tsv"
      # THE GATE for a test that stopped RUNNING — the diff `correlate` just wrote is the only
      # thing that can tell a NEW skip from one this port has accepted (scripts/_lib.sh).
      test_outcome_guard "$REPORT/run-latest" "$RECONCILED" || exit 1
    else
      echo
      echo "-- correlation: every error located to its member and its Java origin --"
      correlate "$REPORT/run-latest" --scalac "$MEASURE_TMP"/vfxmeasure.txt \
        --srcmap "$ROOT/port-report/LibgdxCoreMigrate/run-latest/srcmap.tsv" \
        --srcmap "$REPORT/run-latest/srcmap.tsv"
      echo "(not running the suite: it does not compile — a test that cannot run is not a test that passed)"
    fi

    headline "$ERRORS" "$REPORT"

# ---------------------------------------------------------------------------------------------
# gdx-ai, compiled TOGETHER with the ported libGDX core.
#
# A DEPENDENT port of the same shape as anim8's and vfx's — `gdx-ai/build.gradle` declares ONE
# compile dependency (`com.badlogicgames.gdx:gdx`), the collection shims are vendored by sge, so both
# source sets go on the same scala-cli invocation and this lane must run AFTER `gdx-measure` has
# re-emitted the base. At 166 files it is the largest dependent port in the corpus.
#
# THE TEST STORY, and it is the one number a reader of this port is most likely to get wrong.
# PROGRESS.md's own hand-port table records "24 / 196" against `sge-ai`; that figure describes the
# REFERENCE HAND PORT's MUnit suite (`../sge/sge-extension/ai/src/test/scala`, 24 files), not
# anything upstream gdx-ai ships. Upstream's real JUnit surface is TWO files and TEN `@Test`
# methods, in `gdx-ai/gdx-ai/tests` — `IndexedAStarPathFinderTest` (5) and `ParallelTest` (5, with a
# `@Before`) — and the separate top-level `gdx-ai/tests` gradle project is an LWJGL DEMO
# APPLICATION: 111 files, 54 of them named `*Test*.java`, and ZERO `@Test` anywhere in it. So this
# lane censuses the two trees APART and gates each on its own expected number, because a single
# figure over the checkout cannot tell a suite from a demo and every wrong answer this library has
# produced came from exactly that conflation.
#
# Milestone 1 ports the MAIN source set only. The 10 upstream tests are a `GdxAiTestMigrate` of
# their own and are not lost silently: the guard below fails the lane the day that number moves in
# either direction, which is the only thing standing between "10 tests we have not ported yet" and
# "10 tests nobody remembers".
# ---------------------------------------------------------------------------------------------
[doc("gdx-ai, compiled WITH libGDX core (a dependent port)")]
ai-measure:
    #!/usr/bin/env bash
    cd "{{root}}"
    ROOT="$(pwd)"
    export CORE_PROJECT="{{core_project}}"
    . scripts/_lib.sh

    write_run_props "$ROOT" "balticporter.reportPathRoot=$ROOT/{{ai_src}}"
    REPORT="$ROOT/port-report/GdxAiMigrate"

    OUT=$({{sbt_migrate}} "{{corpus}}/runMain balticporter.corpus.gdxai.GdxAiMigrate" 2>&1 | sed 's/\x1b\[[0-9;]*m//g')
    if ! grep -qE "wrote [0-9]+ Scala files" <<<"$OUT"; then
      echo "!! GdxAiMigrate DID NOT RUN — refusing to measure stale output"
      grep -E "^\[error\].*\.scala:[0-9]+|^\[error\] +\|" <<<"$OUT" | head -20
      exit 1
    fi
    echo "-- GdxAiMigrate (every line it printed) --"
    sed -n '/building model over/,/wrote [0-9]* Scala files/p' <<<"$OUT"
    echo

    echo "-- checks: persisted, untruncated, diffed against the baseline --"
    show_check_report "$REPORT"
    findings_baseline_guard "$REPORT"
    port_map_guard "$REPORT"

    echo
    echo "-- scope --"
    # RE-DERIVED, not asserted in a comment. `gdx-ai/src` holds 167 `.java` files and javac compiles
    # 166: `com/badlogic/gdx/emu/` is GWT super-source, excluded by upstream's own build
    # (`[compileJava, compileTestJava, javadoc]*.exclude("com/badlogic/gdx/emu")`), and it declares a
    # SECOND `com.badlogic.gdx.ai.StandaloneFileSystem`. Included, the emitter would write one
    # `StandaloneFileSystem.scala` from whichever unit sorted last and no check would report it.
    ALL_JAVA=$(find {{ai_src}}/gdx-ai/src -name '*.java' | wc -l | tr -d ' ')
    EMU_JAVA=$(find {{ai_src}}/gdx-ai/src/com/badlogic/gdx/emu -name '*.java' 2>/dev/null | wc -l | tr -d ' ')
    echo "upstream .java: $ALL_JAVA   GWT super-source excluded: $EMU_JAVA   in scope: $((ALL_JAVA - EMU_JAVA))"
    [ "$EMU_JAVA" = "0" ] && echo "!! THE emu/ TREE IS GONE — the scope filter in GdxAiMigrate now excludes nothing; re-read it"

    echo
    echo "-- test discovery --"
    # TWO trees, censused apart — see the header. Both numbers are printed and both are gated: a
    # suite with no discoverable tests runs ZERO and reports SUCCESS, and a demo project counted as a
    # suite is the same lie with the sign flipped.
    JAVA_TESTS=$(java_test_count {{ai_tests}})
    DEMO_TESTS=$(java_test_count {{ai_demos}})
    echo "@Test in the upstream JUnit source set ({{ai_tests}}): $JAVA_TESTS   (expected 10 — not ported at milestone 1)"
    echo "@Test in the upstream DEMO project ({{ai_demos}}): $DEMO_TESTS   (expected 0 — 111 files, 54 named *Test*.java, an LWJGL application)"
    echo "emitted test files: 0 (this port has no test source set yet — a GdxAiTestMigrate is the next milestone)"
    [ "$JAVA_TESTS" != "10" ] && echo "!! UPSTREAM SUITE MOVED — $JAVA_TESTS @Test, not 10. Milestone 1 ports NONE of them, so nothing else would notice; re-read gdx-ai/gdx-ai/tests before touching this number"
    [ "$DEMO_TESTS" != "0" ] && echo "!! THE DEMO PROJECT NOW DECLARES $DEMO_TESTS @Test — it was an application; it may now be a suite, and this lane must say which"
    echo "   (no run phase: every CLAUDE.md §4.4 form is UNMEASURED for this port — see PROGRESS.md §sge-ai)"

    echo
    break_residue {{ai_module}}/src_managed

    # The JDK is an INPUT to this measurement: the frontend read its class files on ONE JVM and
    # the compile below runs on another. Nothing compared them until an `override` emitted on
    # JDK 24 failed a JDK-22 compile with every other artifact flat (ENGINE-LIMITS M5.10).
    jdk_guard "$REPORT"
    echo "-- compile --"
    # NOTE the ANSI strip: without it `grep -cE '^-- .*Error'` matches nothing and reports 0 for a
    # port that does not compile — a false NEGATIVE on the headline number.
    #
    # No `--test` and no test directory: milestone 1 has ONE source set. When `GdxAiTestMigrate`
    # arrives the flag comes with it — `scala-cli compile` reports on the MAIN scope whatever
    # directories it is handed, so a test tree added here without `--test` would have its errors read
    # and not reported (CLAUDE.md §4.56).
    DEPS="{{ai_deps}}"
    scala-cli compile --scala {{scala_version}} --server=false --jvm {{jdk_version}} $DEPS \
      {{gdx_module}}/src_managed/main/scala {{ai_module}}/src_managed/main/scala \
      2>&1 | sed 's/\x1b\[[0-9;]*m//g' > "$MEASURE_TMP"/aimeasure.txt
    CLI_STATUS=${PIPESTATUS[0]}
    ERRORS=$(grep -cE '^-- (\[E[0-9]+\] )?.*Error' "$MEASURE_TMP"/aimeasure.txt)
    compile_guard "$CLI_STATUS" "$ERRORS" "$MEASURE_TMP"/aimeasure.txt
    echo "TOTAL ERRORS: $ERRORS  (coded $(grep -cE '\[E[0-9]+\].*Error' "$MEASURE_TMP"/aimeasure.txt) + bare $(grep -cE '^-- Error:' "$MEASURE_TMP"/aimeasure.txt))"
    error_baseline_guard "$ERRORS" "$REPORT"
    grep -oE "\[E[0-9]+\][^:]*Error" "$MEASURE_TMP"/aimeasure.txt | sort | uniq -c | sort -rn | head
    echo "-- bare (uncoded) errors by message --"
    grep -A1 '^-- Error:' "$MEASURE_TMP"/aimeasure.txt | grep -vE '^-- Error:|^--$' | sed -E 's/^[0-9]+ \|//; s/[0-9]+//g' | sed -E 's/^ +//' | sort | uniq -c | sort -rn | head

    # Cross-platform compile gates — no deps (ai_deps is empty).
    xplat_compile scala-js {{scala_version}} "$REPORT" aimeasure \
      {{gdx_module}}/src_managed/main/scala {{ai_module}}/src_managed/main/scala -- {{ai_deps}}
    xplat_compile scala-native {{scala_version}} "$REPORT" aimeasure \
      {{gdx_module}}/src_managed/main/scala {{ai_module}}/src_managed/main/scala -- {{ai_deps}}

    # Reference-flags compile (DESIGN.md §8.24): the reference build's own scalacOptions.
    flags_compile {{scala_version}} "$REPORT" aimeasure "{{sge_relaxed_flags}}" {{gdx_module}}/src_managed/main/scala {{ai_module}}/src_managed/main/scala -- {{ai_deps}}

    echo
    echo "-- correlation: every error located to its member and its Java origin --"
    # Run WHETHER OR NOT it compiled, for `noise4j-measure`'s reason: with no suite there is no
    # second thing to correlate, so the compile output is the only diagnostic this port has and it is
    # always worth attributing. BOTH ports' maps — an error inside gdx-ai resolves through its own,
    # and one that reaches the base resolves through libGDX's, which is what a dependent's wall
    # looks like.
    correlate "$REPORT/run-latest" --scalac "$MEASURE_TMP"/aimeasure.txt \
      --srcmap "$ROOT/port-report/LibgdxCoreMigrate/run-latest/srcmap.tsv" \
      --srcmap "$REPORT/run-latest/srcmap.tsv"

    headline "$ERRORS" "$REPORT"

# ---------------------------------------------------------------------------------------------
# gdx-ai's OWN JUnit suite — the first evidence of BEHAVIOUR this port has.
#
# TWO upstream files and TEN `@Test`, and the number is the whole point. `PROGRESS.md` §1.1's
# hand-port column reads `24 / 196` against `sge-ai`; that is the REFERENCE HAND PORT's own MUnit
# suite, hand-WRITTEN for the port, and it is not what upstream ships. Upstream ships
# `IndexedAStarPathFinderTest` (5) and `ParallelTest` (5, with a `@Before`) in `gdx-ai/gdx-ai/tests`
# — while the separate top-level `gdx-ai/tests` gradle project, 111 files with 54 named
# `*Test*.java`, declares ZERO `@Test` and is an LWJGL demo application. `ai-measure` already
# censuses both trees apart and gates each; this lane re-derives the same two numbers rather than
# trusting that one ran first.
#
# A DEPENDENT OF A DEPENDENT: three emitted source sets on one `scala-cli` invocation — libGDX
# core's, gdx-ai's and this suite's — so the lane must run AFTER `just gdx-measure` and
# `just ai-measure`, which is where `measure-all` puts it.
#
# WHAT TEN PASSING TESTS WOULD AND WOULD NOT SAY: the suite validates two of gdx-ai's eight
# packages (`pfa.indexed`, `btree.branch`) and nothing about `msg`, `fsm`, `sched`, `fma`, `steer`
# or the rest of `btree`/`pfa`. It is still the only instrument this port has for CLAUDE.md §4.4 —
# ten Java forms that translate to valid Scala meaning something else, none of which moves a
# compile-error count.
# ---------------------------------------------------------------------------------------------
[doc("gdx-ai's own JUnit suite, compiled WITH libGDX core and gdx-ai")]
ai-test-measure:
    #!/usr/bin/env bash
    cd "{{root}}"
    ROOT="$(pwd)"
    export CORE_PROJECT="{{core_project}}"
    . scripts/_lib.sh

    write_run_props "$ROOT" "balticporter.reportPathRoot=$ROOT/{{ai_src}}"
    REPORT="$ROOT/port-report/GdxAiTestMigrate"

    OUT=$({{sbt_migrate}} "{{corpus}}/runMain balticporter.corpus.gdxai.GdxAiTestMigrate" 2>&1 | sed 's/\x1b\[[0-9;]*m//g')
    if ! grep -qE "wrote [0-9]+ Scala test files" <<<"$OUT"; then
      echo "!! GdxAiTestMigrate DID NOT RUN — refusing to measure stale output"
      grep -E "^\[error\].*\.scala:[0-9]+|^\[error\] +\|" <<<"$OUT" | head -20
      exit 1
    fi
    echo "-- GdxAiTestMigrate (every line it printed) --"
    sed -n '/building model over/,/wrote [0-9]* Scala test files/p' <<<"$OUT"
    echo

    echo "-- checks: persisted, untruncated, diffed against the baseline --"
    show_check_report "$REPORT"
    findings_baseline_guard "$REPORT"
    port_map_guard "$REPORT"

    echo
    echo "-- test discovery --"
    # The same both-frameworks sum every other test lane uses: a CONVERTED suite is MUnit and a
    # REFUSED one is still JUnit, so counting only one under-reports by exactly the conversions —
    # in the safe-looking direction, which is the dangerous one. The DEMO project is censused
    # beside it and held to zero, because a single figure over this checkout cannot tell a suite
    # from a demo and every wrong answer this library has produced came from that conflation.
    JAVA_TESTS=$(java_test_count {{ai_tests}})
    DEMO_TESTS=$(java_test_count {{ai_demos}})
    JUNIT_LEFT=$(junit_residue {{ai_module}}/src_managed/test/scala)
    MUNIT_TESTS=$(munit_emitted {{ai_module}}/src_managed/test/scala)
    SCALA_TESTS=$((JUNIT_LEFT + MUNIT_TESTS))
    echo "@Test in Java: $JAVA_TESTS   discoverable in emitted Scala: $SCALA_TESTS (munit $MUNIT_TESTS + junit $JUNIT_LEFT)"
    echo "@Test in the upstream DEMO project ({{ai_demos}}): $DEMO_TESTS   (expected 0 — 111 files, 54 named *Test*.java, an LWJGL application)"
    [ "$DEMO_TESTS" != "0" ] && echo "!! THE DEMO PROJECT NOW DECLARES $DEMO_TESTS @Test — it was an application; it may now be a suite, and this lane must say which"
    test_discovery_guard "$JAVA_TESTS" "$SCALA_TESTS" "$REPORT"

    echo
    break_residue {{ai_module}}/src_managed/test/scala

    # The JDK is an INPUT to this measurement: the frontend read its class files on ONE JVM and
    # the compile below runs on another. Nothing compared them until an `override` emitted on
    # JDK 24 failed a JDK-22 compile with every other artifact flat (ENGINE-LIMITS M5.10).
    jdk_guard "$REPORT"
    echo "-- compile --"
    # `--test`: without it `scala-cli` READS the test directories and reports only the MAIN scope,
    # so a suite that does not compile measures 0 (§4.56's instrument-invocation rule — measured at
    # 0 against 6 on the one port whose test scope had stopped compiling).
    scala-cli compile --test --scala {{scala_version}} --server=false --jvm {{jdk_version}} {{ai_test_deps}} \
      {{gdx_module}}/src_managed/main/scala {{ai_module}}/src_managed/main/scala \
      {{ai_module}}/src_managed/test/scala \
      2>&1 | sed 's/\x1b\[[0-9;]*m//g' > "$MEASURE_TMP"/aitestmeasure.txt
    CLI_STATUS=${PIPESTATUS[0]}
    ERRORS=$(grep -cE '^-- (\[E[0-9]+\] )?.*Error' "$MEASURE_TMP"/aitestmeasure.txt)
    compile_guard "$CLI_STATUS" "$ERRORS" "$MEASURE_TMP"/aitestmeasure.txt
    echo "TOTAL ERRORS: $ERRORS  (coded $(grep -cE '\[E[0-9]+\].*Error' "$MEASURE_TMP"/aitestmeasure.txt) + bare $(grep -cE '^-- Error:' "$MEASURE_TMP"/aitestmeasure.txt))"
    error_baseline_guard "$ERRORS" "$REPORT"
    grep -oE "\[E[0-9]+\][^:]*Error" "$MEASURE_TMP"/aitestmeasure.txt | sort | uniq -c | sort -rn | head

    # Cross-platform compile gates — same source dirs and deps as the JVM compile.
    xplat_compile scala-js {{scala_version}} "$REPORT" aitestmeasure \
      {{gdx_module}}/src_managed/main/scala {{ai_module}}/src_managed/main/scala {{ai_module}}/src_managed/test/scala -- --test {{ai_test_deps}}
    xplat_compile scala-native {{scala_version}} "$REPORT" aitestmeasure \
      {{gdx_module}}/src_managed/main/scala {{ai_module}}/src_managed/main/scala {{ai_module}}/src_managed/test/scala -- --test {{ai_test_deps}}

    # Reference-flags compile (DESIGN.md §8.24): the reference build's own scalacOptions.
    flags_compile {{scala_version}} "$REPORT" aitestmeasure "{{sge_relaxed_flags}}" {{gdx_module}}/src_managed/main/scala {{ai_module}}/src_managed/main/scala {{ai_module}}/src_managed/test/scala -- --test {{ai_test_deps}}

    if [ "$ERRORS" = "0" ]; then
      echo
      echo "-- run --"
      scala-cli test --scala {{scala_version}} --server=false --jvm {{jdk_version}} {{ai_test_deps}} \
        -Duser.language=en -Duser.country=US \
        {{gdx_module}}/src_managed/main/scala {{ai_module}}/src_managed/main/scala \
        {{ai_module}}/src_managed/test/scala \
        2>&1 | sed 's/\x1b\[[0-9;]*m//g' > "$MEASURE_TMP"/aitestrun.txt
      reconcile_outcomes "$MEASURE_TMP"/aitestrun.txt "$MUNIT_TESTS"; RECONCILED=$?
      echo
      echo "-- correlation: test failures located to members and Java origins --"
      # THREE maps: only gdx-ai's own can anchor a failure on the member that threw, only the
      # suite's can name the test, and libGDX's is what a stack reaching the base resolves through.
      correlate "$REPORT/run-latest" --tests "$MEASURE_TMP"/aitestrun.txt \
        --srcmap "$ROOT/port-report/LibgdxCoreMigrate/run-latest/srcmap.tsv" \
        --srcmap "$ROOT/port-report/GdxAiMigrate/run-latest/srcmap.tsv" \
        --srcmap "test=$REPORT/run-latest/srcmap.tsv"
      test_outcome_guard "$REPORT/run-latest" "$RECONCILED" || exit 1
    else
      echo "(not running the suite: it does not compile — a test that cannot run is not a test that passed)"
    fi

    headline "$ERRORS" "$REPORT"

# ---------------------------------------------------------------------------------------------
# gdx-ai's DIFFERENTIAL gate — the REFERENCE HAND PORT's own MUnit suite, run against the
# mechanically emitted `sge.ai.*`.
#
# WHY THIS LANE EXISTS. Upstream gdx-ai ships 2 test files / 10 `@Test`, and `ai-test-measure`
# ports and runs all ten. Six of the library's packages are reached by none of them. The reference
# hand port (`../sge/sge-extension/ai`) wrote its OWN suite over the same library — 24 files, 196
# `test(…)` — and that suite is hand-written Scala, so a compiled port can be run against it with
# nothing translated at all. That is the jbump precedent (`jbump-measure`'s probe: a port with no
# upstream suite gated by hand-written code) at suite scale.
#
# WHAT IT IS NOT. These are NOT ported tests and are never counted as any (CLAUDE.md §3). The
# emitted-test figures belong to `ai-test-measure`; this lane's population is a number about the
# CENSUS, and the two must not be added.
#
# THE CENSUS IS RE-DERIVED HERE, NOT ASSERTED. `PROGRESS.md` §10.7.12 classifies each of the 24
# reference files (a) compatible as-is / (b) compatible after the mapping / (c) incompatible, and
# the population that classification was made against is READ OFF THE REFERENCE TREE on every run.
# A hand port that gains a file, loses one, or gains a `test(…)` makes the census stale, and
# nothing else in this repository could say so — the adapted copies would keep passing at their own
# smaller number for as long as nobody looked (CLAUDE.md §4.56's instrument-silence rule).
# ---------------------------------------------------------------------------------------------
[doc("gdx-ai's DIFFERENTIAL gate — the hand port's own suite, run against the emitted port")]
ai-diff-measure:
    #!/usr/bin/env bash
    cd "{{root}}"
    ROOT="$(pwd)"
    export CORE_PROJECT="{{core_project}}"
    . scripts/_lib.sh

    REPORT="$ROOT/port-report/GdxAiDifferential"
    TREE="{{ai_module}}/src/test/scala"

    # NO MIGRATION RUNS HERE, so there is no check report and no `show_check_report`. That is not a
    # lane with fewer gates: this lane's whole subject is emitted code ANOTHER lane produced and
    # already checked, and re-printing its counts here would be two readings of one artifact that
    # can disagree (CLAUDE.md §4.6's `PortMap.searchPath` argument, at a report). What this lane
    # owns is the compile of the hand-written half and the OUTCOMES.
    echo "-- census population: RE-DERIVED from the reference hand port, never asserted --"
    REF_FILES=$(find {{ai_ref_tests}} -name '*.scala' | wc -l | tr -d ' ')
    REF_TESTS=$(munit_emitted {{ai_ref_tests}})
    ADAPTED_FILES=$(find "$TREE" -name '*Suite.scala' | wc -l | tr -d ' ')
    ADAPTED_TESTS=$(munit_emitted "$TREE")
    echo "reference hand port ({{ai_ref_tests}}): $REF_FILES file(s), $REF_TESTS test(…)"
    echo "adapted here (class (a)+(b) of §10.7.12): $ADAPTED_FILES suite file(s), $ADAPTED_TESTS test(…)"
    echo "class (c), left out and counted: $((REF_FILES - ADAPTED_FILES)) file(s), $((REF_TESTS - ADAPTED_TESTS)) test(…)"
    # 196 is `munit_emitted`'s count — the SHARED mechanism every other lane's discovery figure uses.
    # It read 194 here until that counter learned MUnit's registration SHAPE: the two it missed put
    # the NAME ON THE NEXT LINE (`fma/FormationPatternAdditiveGetterIss730RedSuite:41`,
    # `pfa/PathFinderBroadcastDispatchRedSuite:146`) — not the `test(s"…")` this comment used to
    # claim, which nobody had looked at — and both are in class (c) files, so the ADAPTED population
    # (95) is unmoved and only this reference denominator is. That blind spot was stated
    # here, with its number and with the argument that it did not matter FOR THIS SUITE — an argument
    # that stopped holding one library later, which is why the counter reads the call and not the
    # name now (CLAUDE.md §4.56; `scripts/_lib.sh`).
    if [ "$REF_FILES" != "24" ] || [ "$REF_TESTS" != "196" ]; then
      echo "!! THE REFERENCE SUITE MOVED — $REF_FILES files / $REF_TESTS tests, not 24 / 196."
      echo "   PROGRESS.md §10.7.12's census was taken against 24 / 196 and is now STALE: a file"
      echo "   added there is a file nobody has classified, and one removed may be one of the ten"
      echo "   this lane copied. Re-run the census before trusting the outcomes below."
      exit 1
    fi

    echo
    # The JDK is an INPUT to this measurement: the frontend read its class files on ONE JVM and
    # the compile below runs on another. Nothing compared them until an `override` emitted on
    # JDK 24 failed a JDK-22 compile with every other artifact flat (ENGINE-LIMITS M5.10).
    jdk_guard "$ROOT/port-report/GdxAiMigrate"
    echo "-- compile --"
    # `--test`: without it `scala-cli` READS the test directory and reports only the MAIN scope, so
    # a differential suite that does not compile measures 0 (CLAUDE.md §4.56's instrument-invocation
    # rule — measured at 0 against 6 on the one port whose test scope had stopped compiling).
    #
    # This lane is where `RefChecks` actually runs for the hand-written half, and that is the whole
    # reason the §10.7.12 census had to be taken TWICE: a per-file typer-error count is a FLOOR
    # (CLAUDE.md §3), and two files that typed clean turned out to declare 18 unimplemented members
    # and an override of nothing between them. A census read off the typer alone would have shipped
    # both.
    scala-cli compile --test --scala {{scala_version}} --server=false --jvm {{jdk_version}} {{ai_test_deps}} \
      {{gdx_module}}/src_managed/main/scala {{ai_module}}/src_managed/main/scala "$TREE" \
      2>&1 | sed 's/\x1b\[[0-9;]*m//g' > "$MEASURE_TMP"/aidiffmeasure.txt
    CLI_STATUS=${PIPESTATUS[0]}
    ERRORS=$(grep -cE '^-- (\[E[0-9]+\] )?.*Error' "$MEASURE_TMP"/aidiffmeasure.txt)
    compile_guard "$CLI_STATUS" "$ERRORS" "$MEASURE_TMP"/aidiffmeasure.txt
    echo "TOTAL ERRORS: $ERRORS  (coded $(grep -cE '\[E[0-9]+\].*Error' "$MEASURE_TMP"/aidiffmeasure.txt) + bare $(grep -cE '^-- Error:' "$MEASURE_TMP"/aidiffmeasure.txt))"
    error_baseline_guard "$ERRORS" "$REPORT"
    grep -oE "\[E[0-9]+\][^:]*Error" "$MEASURE_TMP"/aidiffmeasure.txt | sort | uniq -c | sort -rn | head

    if [ "$ERRORS" != "0" ]; then
      echo "(not running the suite: it does not compile — a test that cannot run is not a test that passed)"
      headline "$ERRORS" "$REPORT"
      exit 0
    fi

    echo
    echo "-- run --"
    scala-cli test --scala {{scala_version}} --server=false --jvm {{jdk_version}} {{ai_test_deps}} \
      -Duser.language=en -Duser.country=US \
      {{gdx_module}}/src_managed/main/scala {{ai_module}}/src_managed/main/scala "$TREE" \
      2>&1 | sed 's/\x1b\[[0-9;]*m//g' > "$MEASURE_TMP"/aidiffrun.txt
    reconcile_outcomes "$MEASURE_TMP"/aidiffrun.txt "$ADAPTED_TESTS"; RECONCILED=$?

    echo
    echo "-- correlation: test failures located to members and Java origins --"
    # TWO maps and no `test=` one: the suite is HAND-WRITTEN, so it has no source map and cannot
    # have one. That is the property this lane wants rather than a gap — a failure here anchors
    # `main-frame`, on the LIBRARY member that threw, which is exactly the question a differential
    # suite is asking.
    correlate "$REPORT/run-latest" --tests "$MEASURE_TMP"/aidiffrun.txt \
      --srcmap "$ROOT/port-report/LibgdxCoreMigrate/run-latest/srcmap.tsv" \
      --srcmap "$ROOT/port-report/GdxAiMigrate/run-latest/srcmap.tsv"
    test_outcome_guard "$REPORT/run-latest" "$RECONCILED" || exit 1

    headline "$ERRORS" "$REPORT"

# ---------------------------------------------------------------------------------------------
# simple-graphs + its suite — the same gate as `gdx-measure`.
#
# simple-graphs is a VENDORED-runtime port (RuntimeMode.Vendored): the shim family it retypes onto
# is written into its own source set, so the compile is over one directory and needs no classpath.
# ---------------------------------------------------------------------------------------------
[doc("simple-graphs + its suite")]
sg-measure:
    #!/usr/bin/env bash
    cd "{{root}}"
    ROOT="$(pwd)"
    export CORE_PROJECT="{{core_project}}"
    . scripts/_lib.sh

    # The finding ids are hashed from paths relative to this root (CLAUDE.md §4.6): set anywhere else
    # and every finding diffs as removed-and-re-added against a baseline whose counts are identical.
    write_run_props "$ROOT" "balticporter.reportPathRoot=$ROOT/{{sg_src}}"
    REPORT="$ROOT/port-report/SimpleGraphsMigrate"

    TREPORT="$ROOT/port-report/SimpleGraphsTestMigrate"

    for M in SimpleGraphsMigrate SimpleGraphsTestMigrate; do
      OUT=$({{sbt_migrate}} "{{corpus}}/runMain balticporter.corpus.simplegraphs.$M" 2>&1 | sed 's/\x1b\[[0-9;]*m//g')
      if ! grep -qE "wrote [0-9]+ Scala( test)? files" <<<"$OUT"; then
        echo "!! $M DID NOT RUN — refusing to measure stale output"
        grep -E "^\[error\].*\.scala:[0-9]+|^\[error\] +\|" <<<"$OUT" | head -20
        exit 1
      fi
      echo "-- $M (ALL checks, untruncated, as the migration printed them) --"
      sed -n '/building model over/,/wrote [0-9]* Scala\( test\)\? files/p' <<<"$OUT"
      echo
    done

    echo "-- checks: persisted, untruncated, diffed against the baseline --"
    show_check_report "$REPORT"
    findings_baseline_guard "$REPORT"
    port_map_guard "$REPORT"
    show_check_report "$TREPORT"
    findings_baseline_guard "$TREPORT"
    port_map_guard "$TREPORT"

    echo
    echo "-- test discovery --"
    # Both frameworks summed, as in `gdx-test-measure`: a ported suite is MUnit and any residue is still
    # JUnit, so counting one under-reports by every converted suite — in the safe-looking direction. A
    # suite with no discoverable tests runs ZERO and reports SUCCESS.
    JAVA_TESTS=$(java_test_count {{sg_src}}/src/test)
    JUNIT_LEFT=$(junit_residue {{sg_module}}/src_managed/test/scala)
    MUNIT_TESTS=$(munit_emitted {{sg_module}}/src_managed/test/scala)
    SCALA_TESTS=$((JUNIT_LEFT + MUNIT_TESTS))
    echo "@Test in Java: $JAVA_TESTS   discoverable in emitted Scala: $SCALA_TESTS (munit $MUNIT_TESTS + junit $JUNIT_LEFT)"
    # …and the LOSS IS BASELINED (scripts/_lib.sh). Printed and not gated, this was a digit inside a
    # line an operator reads past; the verdict is deferred to `headline` so the compile still runs.
    test_discovery_guard "$JAVA_TESTS" "$SCALA_TESTS" "$TREPORT"

    echo
    break_residue {{sg_module}}/src_managed

    # The JDK is an INPUT to this measurement: the frontend read its class files on ONE JVM and
    # the compile below runs on another. Nothing compared them until an `override` emitted on
    # JDK 24 failed a JDK-22 compile with every other artifact flat (ENGINE-LIMITS M5.10).
    jdk_guard "$REPORT"
    echo "-- compile --"
    # NOTE the ANSI strip. Dropped once, and `grep -cE '^-- .*Error'` then matched nothing because every
    # line begins with a colour escape — reporting 0 errors for a port that had 20. A false NEGATIVE on
    # the project's headline number is the worst failure a measure lane can have.
    # BOTH source sets on one invocation: the main port is RuntimeMode.Vendored, so the shims live in
    # `src_managed/main` and the suite links against them there. Compiling either alone measures nothing.
    DEPS="{{sg_deps}}"
    scala-cli compile --test --scala {{scala_version}} --server=false --jvm {{jdk_version}} $DEPS \
      {{sg_module}}/src_managed/main/scala {{sg_module}}/src_managed/test/scala \
      2>&1 | sed 's/\x1b\[[0-9;]*m//g' > "$MEASURE_TMP"/sgmeasure.txt
    CLI_STATUS=${PIPESTATUS[0]}
    ERRORS=$(grep -cE '^-- (\[E[0-9]+\] )?.*Error' "$MEASURE_TMP"/sgmeasure.txt)
    compile_guard "$CLI_STATUS" "$ERRORS" "$MEASURE_TMP"/sgmeasure.txt
    echo "TOTAL ERRORS: $ERRORS  (coded $(grep -cE '\[E[0-9]+\].*Error' "$MEASURE_TMP"/sgmeasure.txt) + bare $(grep -cE '^-- Error:' "$MEASURE_TMP"/sgmeasure.txt))"
    error_baseline_guard "$ERRORS" "$TREPORT"
    grep -oE "\[E[0-9]+\][^:]*Error" "$MEASURE_TMP"/sgmeasure.txt | sort | uniq -c | sort -rn | head
    echo "-- bare (uncoded) errors by message --"
    grep -A1 '^-- Error:' "$MEASURE_TMP"/sgmeasure.txt | grep -vE '^-- Error:|^--$' | sed -E 's/^[0-9]+ \|//; s/[0-9]+//g' | sed -E 's/^ +//' | sort | uniq -c | sort -rn | head

    # Cross-platform compile gates — same source dirs and deps as the JVM compile.
    xplat_compile scala-js {{scala_version}} "$TREPORT" sgmeasure \
      {{sg_module}}/src_managed/main/scala {{sg_module}}/src_managed/test/scala -- --test {{sg_deps}}
    xplat_compile scala-native {{scala_version}} "$TREPORT" sgmeasure \
      {{sg_module}}/src_managed/main/scala {{sg_module}}/src_managed/test/scala -- --test {{sg_deps}}

    # Reference-flags compile (DESIGN.md §8.24): the reference build's own scalacOptions.
    flags_compile {{scala_version}} "$TREPORT" sgmeasure "{{sge_relaxed_flags}}" {{sg_module}}/src_managed/main/scala {{sg_module}}/src_managed/test/scala -- --test {{sg_deps}}

    # -------------------------------------------------------------------------------------------
    # RUN them. Compiling a suite measures nothing about behaviour: CLAUDE.md §4.4 lists ten java forms
    # that translate to VALID scala meaning something else, and not one moves the count above. For this
    # library the live question is ORDER — `Collections.sort(list, cmp)` and `Comparator.reversed()` both
    # compile whichever way round they sort.
    # -------------------------------------------------------------------------------------------
    if [ "$ERRORS" = "0" ]; then
      echo
      echo "-- run --"
      scala-cli test --scala {{scala_version}} --server=false --jvm {{jdk_version}} $DEPS -Duser.language=en -Duser.country=US \
        {{sg_module}}/src_managed/main/scala {{sg_module}}/src_managed/test/scala \
        2>&1 | sed 's/\x1b\[[0-9;]*m//g' > "$MEASURE_TMP"/sgrun.txt
      reconcile_outcomes "$MEASURE_TMP"/sgrun.txt "$MUNIT_TESTS"; RECONCILED=$?
      echo
      echo "-- correlation: test failures located to members and Java origins --"
      correlate "$TREPORT/run-latest" --tests "$MEASURE_TMP"/sgrun.txt \
        --srcmap "$REPORT/run-latest/srcmap.tsv" \
        --srcmap "test=$TREPORT/run-latest/srcmap.tsv"
      # THE GATE for a test that stopped RUNNING — the diff `correlate` just wrote is the only
      # thing that can tell a NEW skip from one this port has accepted (scripts/_lib.sh).
      test_outcome_guard "$TREPORT/run-latest" "$RECONCILED" || exit 1
    else
      echo
      echo "-- correlation: every error located to its member and its Java origin --"
      correlate "$REPORT/run-latest" --scalac "$MEASURE_TMP"/sgmeasure.txt \
        --srcmap "$REPORT/run-latest/srcmap.tsv" \
        --srcmap "test=$TREPORT/run-latest/srcmap.tsv"
      echo "(not running the suite: it does not compile — a test that cannot run is not a test that passed)"
    fi

    headline "$ERRORS" "$TREPORT" "$REPORT"

# ---------------------------------------------------------------------------------------------
# noise4j — the same gate as `sg-measure`, minus the run.
#
# noise4j is a VENDORED-runtime port and a STANDALONE base port: one source set, no resolution
# roots, no dependencies, so the compile is over one directory with no classpath at all.
#
# IT HAS NO RUN PHASE, and that is the one thing to read before quoting a number from it. noise4j
# ships no test sources — `find` over the upstream tree returns `src/` and `examples/` (nine PNGs)
# and nothing else — so there is no Java suite to put through the pipeline and no `test.conf`. The
# lane therefore ASSERTS that fact instead of skipping the discovery block: a lane that silently
# has no tests is indistinguishable from a lane whose tests all vanished, which is the failure
# `java_test_count` exists to catch. Everything CLAUDE.md §4.4 lists is UNMEASURED for this port;
# `PROGRESS.md` §noise4j says so in the same words.
# ---------------------------------------------------------------------------------------------
[doc("noise4j — emit, checks, break residue, compile, correlate (no test set upstream)")]
noise4j-measure:
    #!/usr/bin/env bash
    cd "{{root}}"
    ROOT="$(pwd)"
    export CORE_PROJECT="{{core_project}}"
    . scripts/_lib.sh

    # The finding ids are hashed from paths relative to this root (CLAUDE.md §4.6): set anywhere else
    # and every finding diffs as removed-and-re-added against a baseline whose counts are identical.
    write_run_props "$ROOT" "balticporter.reportPathRoot=$ROOT/{{n4j_src}}"
    REPORT="$ROOT/port-report/Noise4jMigrate"

    # ABORT if the migration itself did not run, or the lane measures the PREVIOUS emit and reports a
    # stale number as a result.
    OUT=$({{sbt_migrate}} "{{corpus}}/runMain balticporter.corpus.noise4j.Noise4jMigrate" 2>&1 | sed 's/\x1b\[[0-9;]*m//g')
    if ! grep -qE "wrote [0-9]+ Scala files" <<<"$OUT"; then
      echo "!! Noise4jMigrate DID NOT RUN — refusing to measure stale output"
      grep -E "^\[error\].*\.scala:[0-9]+|^\[error\] +\|" <<<"$OUT" | head -20
      exit 1
    fi
    echo "-- Noise4jMigrate (ALL checks, untruncated, as the migration printed them) --"
    sed -n '/building model over/,/wrote [0-9]* Scala files/p' <<<"$OUT"
    echo

    echo "-- checks: persisted, untruncated, diffed against the baseline --"
    show_check_report "$REPORT"
    findings_baseline_guard "$REPORT"
    port_map_guard "$REPORT"

    echo
    echo "-- test discovery --"
    # ASSERTED, not omitted. noise4j has no test sources; if it ever gains one, this lane says so
    # loudly rather than continuing to report a port with no behavioural evidence as complete.
    JAVA_TESTS=$(java_test_count {{n4j_src}}/src {{n4j_src}}/test {{n4j_src}}/tests)
    echo "@Test in Java: $JAVA_TESTS   emitted test files: 0 (this port has no test source set)"
    [ "$JAVA_TESTS" != "0" ] && echo "!! UPSTREAM NOW HAS $JAVA_TESTS @Test — this port has no test.conf, so none of them runs; add one (CLAUDE.md §3)"
    echo "   (no run phase: every CLAUDE.md §4.4 form is UNMEASURED for this port — see PROGRESS.md §noise4j)"

    echo
    break_residue {{n4j_module}}/src_managed

    # The JDK is an INPUT to this measurement: the frontend read its class files on ONE JVM and
    # the compile below runs on another. Nothing compared them until an `override` emitted on
    # JDK 24 failed a JDK-22 compile with every other artifact flat (ENGINE-LIMITS M5.10).
    jdk_guard "$REPORT"
    echo "-- compile --"
    # NOTE the ANSI strip: without it `grep -cE '^-- .*Error'` matches nothing and reports 0 for a port
    # that does not compile — a false NEGATIVE on the headline number.
    DEPS="{{n4j_deps}}"
    scala-cli compile --scala {{scala_version}} --server=false --jvm {{jdk_version}} $DEPS \
      {{n4j_module}}/src_managed/main/scala \
      2>&1 | sed 's/\x1b\[[0-9;]*m//g' > "$MEASURE_TMP"/n4jmeasure.txt
    CLI_STATUS=${PIPESTATUS[0]}
    ERRORS=$(grep -cE '^-- (\[E[0-9]+\] )?.*Error' "$MEASURE_TMP"/n4jmeasure.txt)
    compile_guard "$CLI_STATUS" "$ERRORS" "$MEASURE_TMP"/n4jmeasure.txt
    echo "TOTAL ERRORS: $ERRORS  (coded $(grep -cE '\[E[0-9]+\].*Error' "$MEASURE_TMP"/n4jmeasure.txt) + bare $(grep -cE '^-- Error:' "$MEASURE_TMP"/n4jmeasure.txt))"
    error_baseline_guard "$ERRORS" "$REPORT"
    grep -oE "\[E[0-9]+\][^:]*Error" "$MEASURE_TMP"/n4jmeasure.txt | sort | uniq -c | sort -rn | head
    echo "-- bare (uncoded) errors by message --"
    grep -A1 '^-- Error:' "$MEASURE_TMP"/n4jmeasure.txt | grep -vE '^-- Error:|^--$' | sed -E 's/^[0-9]+ \|//; s/[0-9]+//g' | sed -E 's/^ +//' | sort | uniq -c | sort -rn | head

    # Cross-platform compile gates — no deps (n4j_deps is empty).
    xplat_compile scala-js {{scala_version}} "$REPORT" n4jmeasure \
      {{n4j_module}}/src_managed/main/scala
    xplat_compile scala-native {{scala_version}} "$REPORT" n4jmeasure \
      {{n4j_module}}/src_managed/main/scala

    # Reference-flags compile (DESIGN.md §8.24): the reference build's own scalacOptions.
    flags_compile {{scala_version}} "$REPORT" n4jmeasure "{{sge_relaxed_flags}}" {{n4j_module}}/src_managed/main/scala

    echo
    echo "-- correlation: every error located to its member and its Java origin --"
    # Run WHETHER OR NOT it compiled. With no suite there is no second thing to correlate, so the
    # compile output is the only diagnostic this port has and it is always worth attributing.
    correlate "$REPORT/run-latest" --scalac "$MEASURE_TMP"/n4jmeasure.txt \
      --srcmap "$REPORT/run-latest/srcmap.tsv"

    headline "$ERRORS" "$REPORT"

# ---------------------------------------------------------------------------------------------
# jbump — the same gate as `sg-measure`, minus the one stage jbump cannot have.
#
# jbump ships NO TEST SUITE. Its `test` gradle module is a runnable libGDX demo (`TestBump extends
# ApplicationAdapter`, LWJGL3 + shapedrawer, driven by mouse and WASD) and declares zero `@Test`
# methods, so there is nothing for this engine to port and this port's evidence stops at the
# compiler. CLAUDE.md §3 is explicit about what that leaves unmeasured, so the lane does two things
# rather than quietly omitting the stage:
#
#   * it RE-DERIVES the zero on every run, with `java_test_count` over the whole upstream checkout
#     rather than over the ported module — a claim in a document rots, a number in a lane does not,
#     and the day upstream adds a suite this is the line that says so;
#   * it says out loud, in the run, that the behavioural gate is absent. A lane that simply had no
#     test stage would read as a lane whose tests passed.
# ---------------------------------------------------------------------------------------------
[doc("jbump — emit, checks, break residue, compile, correlate (no suite upstream: see the lane)")]
jbump-measure:
    #!/usr/bin/env bash
    cd "{{root}}"
    ROOT="$(pwd)"
    export CORE_PROJECT="{{core_project}}"
    . scripts/_lib.sh

    # The finding ids are hashed from paths relative to this root (CLAUDE.md §4.6): set anywhere else
    # and every finding diffs as removed-and-re-added against a baseline whose counts are identical.
    write_run_props "$ROOT" "balticporter.reportPathRoot=$ROOT/{{jbump_src}}"
    REPORT="$ROOT/port-report/JbumpMigrate"

    # ABORT if the migration itself did not run. Piping straight into `grep wrote` discards the exit
    # status, so an engine that failed to COMPILE would leave the lane measuring the PREVIOUS emit.
    MIGRATE_OUT=$({{sbt_migrate}} "{{corpus}}/runMain balticporter.corpus.jbump.JbumpMigrate" 2>&1 | sed 's/\x1b\[[0-9;]*m//g')
    if ! grep -qE "wrote [0-9]+ Scala files" <<<"$MIGRATE_OUT"; then
      echo "!! MIGRATION DID NOT RUN — refusing to measure stale output"
      grep -E "^\[error\].*\.scala:[0-9]+|^\[error\] +\|" <<<"$MIGRATE_OUT" | head -20
      exit 1
    fi

    echo "-- migration (ALL checks, untruncated, as the migration printed them) --"
    sed -n '/building model over/,/wrote [0-9]* Scala files/p' <<<"$MIGRATE_OUT"

    echo
    echo "-- checks: persisted, untruncated, diffed against the baseline --"
    show_check_report "$REPORT"
    findings_baseline_guard "$REPORT"
    port_map_guard "$REPORT"

    echo
    echo "-- test discovery --"
    JAVA_TESTS=$(java_test_count {{jbump_upstream}})
    echo "@Test in the WHOLE jbump upstream checkout: $JAVA_TESTS"
    if [ "$JAVA_TESTS" = "0" ]; then
      echo "   NO SUITE UPSTREAM — nothing for the engine to port, so the behavioural gate for this"
      echo "   port is the DIFFERENTIAL PROBE below, not a ported suite. It is hand-written and must"
      echo "   never be counted as a ported test (CLAUDE.md §3); PROGRESS.md §jbump says what it covers."
    else
      echo "!! A SUITE HAS APPEARED UPSTREAM — $JAVA_TESTS @Test method(s). This port has no test"
      echo "   source set; add balticporter/corpus/ports/jbump/test.conf (\`base = \"main.conf\"\`) and a lane stage."
    fi

    echo
    break_residue {{jbump_module}}/src_managed

    # The JDK is an INPUT to this measurement: the frontend read its class files on ONE JVM and
    # the compile below runs on another. Nothing compared them until an `override` emitted on
    # JDK 24 failed a JDK-22 compile with every other artifact flat (ENGINE-LIMITS M5.10).
    jdk_guard "$REPORT"
    echo "-- compile --"
    # NOTE the ANSI strip — dropped once, and every line then began with an escape, reporting 0
    # errors for a port that had 20. A false NEGATIVE on the headline number is the worst failure a
    # measure lane can have.
    DEPS="{{jbump_deps}}"
    scala-cli compile --scala {{scala_version}} --server=false --jvm {{jdk_version}} $DEPS \
      {{jbump_module}}/src_managed/main/scala \
      2>&1 | sed 's/\x1b\[[0-9;]*m//g' > "$MEASURE_TMP"/jbumpmeasure.txt
    CLI_STATUS=${PIPESTATUS[0]}
    ERRORS=$(grep -cE '^-- (\[E[0-9]+\] )?.*Error' "$MEASURE_TMP"/jbumpmeasure.txt)
    compile_guard "$CLI_STATUS" "$ERRORS" "$MEASURE_TMP"/jbumpmeasure.txt
    echo "TOTAL ERRORS: $ERRORS  (coded $(grep -cE '\[E[0-9]+\].*Error' "$MEASURE_TMP"/jbumpmeasure.txt) + bare $(grep -cE '^-- Error:' "$MEASURE_TMP"/jbumpmeasure.txt))"
    error_baseline_guard "$ERRORS" "$REPORT"
    grep -oE "\[E[0-9]+\][^:]*Error" "$MEASURE_TMP"/jbumpmeasure.txt | sort | uniq -c | sort -rn | head
    echo "-- bare (uncoded) errors by message --"
    grep -A1 '^-- Error:' "$MEASURE_TMP"/jbumpmeasure.txt | grep -vE '^-- Error:|^--$' | sed -E 's/^[0-9]+ \|//; s/[0-9]+//g' | sed -E 's/^ +//' | sort | uniq -c | sort -rn | head

    # Cross-platform compile gates — no deps (jbump_deps is empty).
    xplat_compile scala-js {{scala_version}} "$REPORT" jbumpmeasure \
      {{jbump_module}}/src_managed/main/scala
    xplat_compile scala-native {{scala_version}} "$REPORT" jbumpmeasure \
      {{jbump_module}}/src_managed/main/scala

    # Reference-flags compile (DESIGN.md §8.24): the reference build's own scalacOptions.
    flags_compile {{scala_version}} "$REPORT" jbumpmeasure "{{sge_relaxed_flags}}" {{jbump_module}}/src_managed/main/scala

    # -------------------------------------------------------------------------------------------
    # RUN it — differentially, against the upstream Java.
    #
    # This is the stage a library with no suite would otherwise not have, and CLAUDE.md §3 says what
    # skipping it would cost: a green compile is not evidence, and jbump contains six of §4.4's ten
    # forms (reference `==`, `x++` as a value, `break`/`continue`, a `switch`, a `static {}` block,
    # a `super`-less secondary-constructor funnel) — none of which moves the count above.
    #
    # `balticporter/corpus/ports/jbump/probe/{ProbeJava.java,Probe.scala}` walk the SAME scenario, one against
    # `{{jbump_src}}` and one against the emitted port, and the gate is that their transcripts are
    # IDENTICAL. No expected value is written anywhere, so none can be written down wrong: the
    # upstream Java is the authority and the diff is the whole assertion.
    # -------------------------------------------------------------------------------------------
    echo
    if [ "$ERRORS" != "0" ]; then
      echo "-- correlation: every error located to its member and its Java origin --"
      correlate "$REPORT/run-latest" --scalac "$MEASURE_TMP"/jbumpmeasure.txt \
        --srcmap "$REPORT/run-latest/srcmap.tsv"
      echo "(not running the differential probe: the port does not compile — a probe that cannot run is not a probe that agreed)"
      headline "$ERRORS" "$REPORT"
      exit 0
    fi

    echo "-- differential probe: emitted Scala vs upstream Java, same scenario --"
    PROBE="$MEASURE_TMP/jbump-probe"
    rm -rf "$PROBE"; mkdir -p "$PROBE/classes"
    javac -nowarn -d "$PROBE/classes" -sourcepath {{jbump_src}} balticporter/corpus/ports/jbump/probe/ProbeJava.java \
      > "$PROBE/javac.txt" 2>&1
    if [ "$?" != "0" ]; then
      echo "!! PROBE DID NOT COMPILE against the upstream Java — the AUTHORITY half is broken, so the"
      echo "   port half proves nothing. Fix balticporter/corpus/ports/jbump/probe/ProbeJava.java:"
      grep -v '^Note:' "$PROBE/javac.txt" | head -20 | sed 's/^/     /'
      exit 1
    fi
    java -cp "$PROBE/classes" ProbeJava > "$PROBE/java.txt" 2>&1
    JAVA_ST=$?
    scala-cli run --scala {{scala_version}} --server=false --jvm {{jdk_version}} $DEPS \
      {{jbump_module}}/src_managed/main/scala balticporter/corpus/ports/jbump/probe/Probe.scala \
      2>&1 | sed 's/\x1b\[[0-9;]*m//g' \
      | grep -vE '^Warning: setting |deprecation warning|^[0-9]+ warning' > "$PROBE/scala.txt"
    SCALA_ST=${PIPESTATUS[0]}
    # Both halves must have RUN. A crashed probe whose partial output happens to match is the §3
    # false green one artifact later, so the exit statuses gate before the diff does.
    if [ "$JAVA_ST" != "0" ] || [ "$SCALA_ST" != "0" ]; then
      echo "!! PROBE DID NOT RUN (java exit $JAVA_ST, scala exit $SCALA_ST) — refusing to diff partial output"
      tail -20 "$PROBE/scala.txt" | sed 's/^/     /'
      exit 1
    fi
    LINES=$(grep -c "" "$PROBE/java.txt")
    if diff -u "$PROBE/java.txt" "$PROBE/scala.txt" > "$PROBE/diff.txt"; then
      echo "probe: $LINES transcript line(s), emitted Scala IDENTICAL to upstream Java"
    else
      echo "!! PROBE DIVERGED — the port behaves differently from the library it was made from."
      echo "   Left = upstream Java (the authority), right = emitted Scala:"
      sed 's/^/     /' "$PROBE/diff.txt"
      exit 1
    fi

    echo
    echo "-- correlation: nothing to locate (0 errors); the source map is still published --"
    correlate "$REPORT/run-latest" --scalac "$MEASURE_TMP"/jbumpmeasure.txt \
      --srcmap "$REPORT/run-latest/srcmap.tsv"

    headline "$ERRORS" "$REPORT"


# ---------------------------------------------------------------------------------------------
# USL — VisUI's skin-language compiler, a STANDALONE port with no base and no resolution roots.
#
# `jbump-measure`'s shape, with the four re-derivations this port's scope decision rests on run as
# part of the measurement rather than trusted from a comment (CLAUDE.md §4.56's instrument-silence
# rule): a claim in a document rots and a number in a lane does not, and every one of these four is
# a reason `corpus/ports/visui-usl/main.conf` gives for being its own port root.
# ---------------------------------------------------------------------------------------------
[doc("USL — emit, checks, break residue, compile, correlate (VisUI's skin-language compiler)")]
usl-measure:
    #!/usr/bin/env bash
    cd "{{root}}"
    ROOT="$(pwd)"
    export CORE_PROJECT="{{core_project}}"
    . scripts/_lib.sh

    # The finding ids are hashed from paths relative to this root (CLAUDE.md §4.6): set anywhere else
    # and every finding diffs as removed-and-re-added against a baseline whose counts are identical.
    write_run_props "$ROOT" "balticporter.reportPathRoot=$ROOT/{{visui_src}}"
    REPORT="$ROOT/port-report/UslMigrate"

    # ABORT if the migration itself did not run. Piping straight into `grep wrote` discards the exit
    # status, so an engine that failed to COMPILE would leave the lane measuring the PREVIOUS emit.
    MIGRATE_OUT=$({{sbt_migrate}} "{{corpus}}/runMain balticporter.corpus.visuiusl.UslMigrate" 2>&1 | sed 's/\x1b\[[0-9;]*m//g')
    if ! grep -qE "wrote [0-9]+ Scala files" <<<"$MIGRATE_OUT"; then
      echo "!! MIGRATION DID NOT RUN — refusing to measure stale output"
      grep -E "^\[error\].*\.scala:[0-9]+|^\[error\] +\|" <<<"$MIGRATE_OUT" | head -20
      exit 1
    fi

    echo "-- migration (ALL checks, untruncated, as the migration printed them) --"
    sed -n '/building model over/,/wrote [0-9]* Scala files/p' <<<"$MIGRATE_OUT"

    echo
    echo "-- checks: persisted, untruncated, diffed against the baseline --"
    show_check_report "$REPORT"
    findings_baseline_guard "$REPORT"
    port_map_guard "$REPORT"

    # -------------------------------------------------------------------------------------------
    # THE SCOPE DECISION, RE-DERIVED. Four numbers, each of which is a reason this is its own port
    # root rather than a glob added to `visui-measure` — and each of which a comment would let rot.
    # -------------------------------------------------------------------------------------------
    echo
    echo "-- scope: the four facts the port root rests on, re-derived --"
    USL_JAVA=$(find {{usl_src}}/src/main/java -name '*.java' | wc -l | tr -d ' ')
    USL_USES_GDX=$(grep -rl "com\.badlogic\.gdx" {{usl_src}}/src/main/java | wc -l | tr -d ' ')
    USL_USES_UI=$(grep -rl "com\.kotcrab\.vis\.ui" {{usl_src}}/src/main/java | wc -l | tr -d ' ')
    UI_USES_USL=$(grep -rl "com\.kotcrab\.vis\.usl" {{visui_src}}/ui/src/main/java | wc -l | tr -d ' ')
    USL_THIRD_PARTY=$(grep -rhE '^import ' {{usl_src}}/src/main/java | sed 's/.*import \(static \)*//' \
      | grep -vE '^(java|javax)\.|^com\.kotcrab\.vis\.usl' | sort -u | wc -l | tr -d ' ')
    HEADERS=$(grep -rl 'Licensed under the Apache License' {{usl_src}}/src/main/java | wc -l | tr -d ' ')
    echo "usl/ java files: $USL_JAVA   with an Apache header: $HEADERS"
    echo "usl/ files naming com.badlogic.gdx: $USL_USES_GDX      usl/ files naming com.kotcrab.vis.ui: $USL_USES_UI"
    echo "ui/ files naming com.kotcrab.vis.usl: $UI_USES_USL      usl/ imports outside the JDK and itself: $USL_THIRD_PARTY"
    if [ "$USL_USES_GDX" != "0" ] || [ "$USL_USES_UI" != "0" ] || [ "$UI_USES_USL" != "0" ]; then
      echo "!! THE COUPLING HAS CHANGED — this port is standalone BECAUSE all three of those read 0."
      echo "   A non-zero reading means the scope decision in corpus/ports/visui-usl/main.conf has to be"
      echo "   re-taken: a module that references its sibling is not a port with no base."
      exit 1
    fi
    if [ "$USL_THIRD_PARTY" != "0" ]; then
      echo "!! USL HAS GAINED A DEPENDENCY — \`usl_deps\` is empty and \`input\` names no classpathFile"
      echo "   BECAUSE this read 0. An unresolvable import does not fail the frontend, it resolves"
      echo "   WRONGLY (recorded three times: libGDX's junit, Ashley's mockito, simple-graphs')."
      exit 1
    fi
    if [ "$HEADERS" != "$USL_JAVA" ]; then
      echo "!! A SOURCE HAS LOST ITS APACHE HEADER ($HEADERS of $USL_JAVA) — this port declares NO"
      echo "   \`notices\` BECAUSE every file carries the notice itself (CLAUDE.md §4.57). With one"
      echo "   missing, the banner NAMES a licence and reproduces no notice, and nothing else reports it."
      exit 1
    fi

    echo
    break_residue {{usl_module}}/src_managed

    # The JDK is an INPUT to this measurement: the frontend read its class files on ONE JVM and
    # the compile below runs on another. Nothing compared them until an `override` emitted on
    # JDK 24 failed a JDK-22 compile with every other artifact flat (ENGINE-LIMITS M5.10).
    jdk_guard "$REPORT"
    echo "-- compile --"
    # NOTE the ANSI strip — dropped once, and every line then began with an escape, reporting 0
    # errors for a port that had 20. A false NEGATIVE on the headline number is the worst failure a
    # measure lane can have.
    DEPS="{{usl_deps}}"
    scala-cli compile --scala {{scala_version}} --server=false --jvm {{jdk_version}} $DEPS \
      {{usl_module}}/src_managed/main/scala \
      2>&1 | sed 's/\x1b\[[0-9;]*m//g' > "$MEASURE_TMP"/uslmeasure.txt
    CLI_STATUS=${PIPESTATUS[0]}
    ERRORS=$(grep -cE '^-- (\[E[0-9]+\] )?.*Error' "$MEASURE_TMP"/uslmeasure.txt)
    compile_guard "$CLI_STATUS" "$ERRORS" "$MEASURE_TMP"/uslmeasure.txt
    echo "TOTAL ERRORS: $ERRORS  (coded $(grep -cE '\[E[0-9]+\].*Error' "$MEASURE_TMP"/uslmeasure.txt) + bare $(grep -cE '^-- Error:' "$MEASURE_TMP"/uslmeasure.txt))"
    error_baseline_guard "$ERRORS" "$REPORT"
    grep -oE "\[E[0-9]+\][^:]*Error" "$MEASURE_TMP"/uslmeasure.txt | sort | uniq -c | sort -rn | head
    echo "-- bare (uncoded) errors by message --"
    grep -A1 '^-- Error:' "$MEASURE_TMP"/uslmeasure.txt | grep -vE '^-- Error:|^--$' | sed -E 's/^[0-9]+ \|//; s/[0-9]+//g' | sed -E 's/^ +//' | sort | uniq -c | sort -rn | head

    # Cross-platform compile gates — no deps (usl_deps is empty).
    xplat_compile scala-js {{scala_version}} "$REPORT" uslmeasure \
      {{usl_module}}/src_managed/main/scala
    xplat_compile scala-native {{scala_version}} "$REPORT" uslmeasure \
      {{usl_module}}/src_managed/main/scala

    # Reference-flags compile (DESIGN.md §8.24): the reference build's own scalacOptions.
    flags_compile {{scala_version}} "$REPORT" uslmeasure "{{sge_relaxed_flags}}" {{usl_module}}/src_managed/main/scala

    echo
    echo "-- correlation: every error located to its member and its Java origin --"
    # Run WHETHER OR NOT it compiled: attributing the wall is what makes it a §1-classifiable list
    # rather than a pile of typer errors (CLAUDE.md §4.45).
    correlate "$REPORT/run-latest" --scalac "$MEASURE_TMP"/uslmeasure.txt \
      --srcmap "$REPORT/run-latest/srcmap.tsv"

    # -------------------------------------------------------------------------------------------
    # THE CONFORMANCE ORACLE — 19 shipped fixtures, and upstream wrote BOTH SIDES.
    #
    # This is jbump's differential probe with a second tier upstream handed us for free, and it is
    # the strongest behavioural gate in the corpus per line of harness. Two tiers, both with NO
    # authored expectation anywhere:
    #
    #   ABSOLUTE — `usl/styles/*.usl` are the skin templates the root `build.gradle`'s `compileUsl`
    #     task compiles the shipped skin FROM, and `ui/src/main/resources/.../x1/uiskin.json` is the
    #     artifact it wrote. So a fixture reproducing that file byte-for-byte is the port
    #     reproducing a RELEASED ARTIFACT of the library it was made from.
    #
    #   DIFFERENTIAL — every fixture's output, port against upstream JAVA, run on the same inputs in
    #     the same order. The authority is the java, so no expected value is written down and none
    #     can be written down wrong; the whole assertion is one `diff`.
    #
    # WHY BOTH. The absolute tier is the stronger claim and covers only the 7 fixtures that produce
    # today's skin; the differential tier covers all 19, including twelve OLDER templates whose
    # (different, older) output nothing has ever checked in. Neither subsumes the other, and a lane
    # reporting only the first would call twelve fixtures "not applicable" when they are in fact the
    # widest input this port has.
    #
    # THE COUNTS ARE DERIVED ON BOTH SIDES, never listed — `CLAUDE.md` §4.56's instrument rule. WHICH
    # fixtures reproduce the skin is a fact about upstream's templates, so the java half computes it
    # and the scala half computes it and the lane compares the two numbers. A hard-coded list would
    # go stale the first time a template is edited, silently and in the passing direction.
    # -------------------------------------------------------------------------------------------
    echo
    if [ "$ERRORS" != "0" ]; then
      echo "-- oracle: NOT RUN — the port does not compile, and scalac reaching no backend phase"
      echo "   writes no class file, so there is nothing to run (CLAUDE.md §3.5). A gate that cannot"
      echo "   run is not a gate that agreed."
      headline "$ERRORS" "$REPORT"
      exit 0
    fi

    echo "-- oracle: 19 shipped .usl fixtures, port vs upstream java vs the checked-in uiskin.json --"
    # The include directive is what would make this gate ONLINE and non-deterministic (upstream's own
    # `RemoteTest` is `@Ignore`d for exactly that reason), so the absence is re-derived rather than
    # assumed. `gdx.usl` mentions the word in a COMMENT, which is why this greps for the DIRECTIVE.
    INCLUDES=$(grep -rlE '^[[:space:]]*include[[:space:]]*<' {{usl_styles}} | wc -l | tr -d ' ')
    if [ "$INCLUDES" != "0" ]; then
      echo "!! A FIXTURE NOW USES AN \`include <…>\` DIRECTIVE ($INCLUDES file(s)) — this gate is offline"
      echo "   BECAUSE none did. An include resolves through Lexer.addIncludeSource or DOWNLOADS over"
      echo "   HTTP, and neither belongs in a measurement lane. Re-take the oracle's design first."
      exit 1
    fi

    ORACLE="$MEASURE_TMP/usl-oracle"
    rm -rf "$ORACLE"; mkdir -p "$ORACLE/classes"
    javac -nowarn -d "$ORACLE/classes" -sourcepath {{usl_src}}/src/main/java \
      balticporter/corpus/ports/visui-usl/probe/OracleJava.java > "$ORACLE/javac.txt" 2>&1
    if [ "$?" != "0" ]; then
      echo "!! THE AUTHORITY HALF DID NOT COMPILE against the upstream java, so the port half proves"
      echo "   nothing. Fix balticporter/corpus/ports/visui-usl/probe/OracleJava.java:"
      grep -v '^Note:' "$ORACLE/javac.txt" | head -20 | sed 's/^/     /'
      exit 1
    fi
    java -cp "$ORACLE/classes" OracleJava {{usl_styles}} {{usl_known_good}} > "$ORACLE/java.txt" 2>&1
    JAVA_ST=$?
    # `--main-class Oracle` IS REQUIRED, and the reason is a fact about this library worth keeping:
    # USL is a command-line TOOL, so the port emits `sge.visui.usl.Main` — a second main class in
    # the same run. Without the flag scala-cli refuses to choose and exits 1, which the status gate
    # below would report as "the oracle did not run" rather than as the ambiguity it is.
    #
    # …and the transcript is cut from `fixtures:` ONWARD rather than filtered line by line. The port
    # compiles with 5 deprecation warnings (§4.4's non-local return, `PROGRESS.md` §10.9.13), and a
    # `grep -v` list of warning shapes is a filter that has to be extended every time scalac phrases
    # one differently — the same enumerate-the-accepted-forms mistake §4.56's counter rule is about.
    # The probe's own first line is a marker nothing else emits, so anchoring on it is exact.
    scala-cli run --scala {{scala_version}} --server=false --jvm {{jdk_version}} $DEPS --main-class Oracle \
      {{usl_module}}/src_managed/main/scala balticporter/corpus/ports/visui-usl/probe/Oracle.scala \
      -- {{usl_styles}} {{usl_known_good}} \
      > "$ORACLE/scala-raw.txt" 2>&1
    SCALA_ST=$?
    sed 's/\x1b\[[0-9;]*m//g' "$ORACLE/scala-raw.txt" | sed -n '/^fixtures: /,$p' > "$ORACLE/scala.txt"
    # Both halves must have RUN. A crashed half whose partial output happens to match is the §3 false
    # green one artifact later, so the exit statuses gate before the diff does.
    if [ "$JAVA_ST" != "0" ] || [ "$SCALA_ST" != "0" ]; then
      echo "!! THE ORACLE DID NOT RUN (java exit $JAVA_ST, scala exit $SCALA_ST) — refusing to diff partial output"
      tail -20 "$ORACLE/scala.txt" | sed 's/^/     /'
      exit 1
    fi

    FIXTURES=$(sed -n 's/^fixtures: //p' "$ORACLE/java.txt")
    J_EXACT=$(grep -c '^known-good: EXACT' "$ORACLE/java.txt")
    S_EXACT=$(grep -c '^known-good: EXACT' "$ORACLE/scala.txt")
    S_THREW=$(grep -c '^THREW ' "$ORACLE/scala.txt")
    J_THREW=$(grep -c '^THREW ' "$ORACLE/java.txt")
    echo "fixtures compiled: $FIXTURES   reproduce the checked-in uiskin.json — java: $J_EXACT, port: $S_EXACT   threw — java: $J_THREW, port: $S_THREW"

    # TIER 1, the ABSOLUTE gate. Held to the JAVA's number rather than to a literal, so a template
    # edit upstream moves both sides together and only a real divergence fails.
    if [ "$S_EXACT" != "$J_EXACT" ]; then
      echo "!! ABSOLUTE TIER FAILED — the port reproduces the released uiskin.json for $S_EXACT fixture(s)"
      echo "   where upstream java reproduces it for $J_EXACT."
      exit 1
    fi
    if [ "$J_EXACT" = "0" ]; then
      echo "!! THE AUTHORITY REPRODUCES THE CHECKED-IN SKIN FOR NO FIXTURE AT ALL. That is not a port"
      echo "   failure — it means the oracle's own premise is gone (a re-compiled skin, a moved file),"
      echo "   and a tier that passes by comparing 0 against 0 is exactly the bar CLAUDE.md §5 refuses."
      exit 1
    fi

    # TIER 2, the DIFFERENTIAL gate — every byte of every fixture's output, port against java.
    if diff -u "$ORACLE/java.txt" "$ORACLE/scala.txt" > "$ORACLE/diff.txt"; then
      LINES=$(grep -c "" "$ORACLE/java.txt")
      echo "oracle: $FIXTURES fixture(s), $LINES transcript line(s) — emitted Scala IDENTICAL to upstream java,"
      echo "        and $S_EXACT of them reproduce the RELEASED uiskin.json byte for byte."
    else
      echo "!! ORACLE DIVERGED — the port compiles a .usl file differently from the library it was made"
      echo "   from. Left = upstream java (the authority), right = emitted Scala:"
      head -60 "$ORACLE/diff.txt" | sed 's/^/     /'
      echo "   (full diff: $ORACLE/diff.txt)"
      exit 1
    fi

    headline "$ERRORS" "$REPORT"


# ---------------------------------------------------------------------------------------------
# USL's own JUnit suite — 2 files, 7 `@Test`, SEVEN OF VISUI'S NINE.
#
# `sg-measure`'s shape (emit both → checks for both → discovery → break residue → compile both →
# RUN) with one difference, and it is the one worth reading: THE SUITE'S INPUTS ARE RESOURCES.
#
# Every test calls `getResourceAsStream("/test-visui.usl")` and compares against
# `/test-visui-expected.json` beside it. A classpath string is a STRING LITERAL and no rename may
# touch one (CLAUDE.md §4.56), so the emitted Scala names the upstream paths and the RUNNER has to
# supply that tree — which is `PROGRESS.md` §11 item 7's standing obligation ("the port's own
# classpath resources are not part of its output, and nothing says so") met at the smallest scale
# the corpus has. Absent, every test fails on a null stream; supplied, the suite is a conformance
# gate over six real templates whose expected output UPSTREAM WROTE.
#
# So this lane and `usl-measure`'s oracle are the same kind of evidence over two populations: six
# language-exercising templates with checked-in expectations here, nineteen real shipped skins
# there. Neither is a substitute for the other and both are zero-authoring.
# ---------------------------------------------------------------------------------------------
[doc("USL's own suite — 7 @Test over checked-in .usl/.json pairs; emit, compile, RUN")]
usl-test-measure:
    #!/usr/bin/env bash
    cd "{{root}}"
    ROOT="$(pwd)"
    export CORE_PROJECT="{{core_project}}"
    . scripts/_lib.sh

    # The finding ids are hashed from paths relative to this root (CLAUDE.md §4.6): set anywhere else
    # and every finding diffs as removed-and-re-added against a baseline whose counts are identical.
    write_run_props "$ROOT" "balticporter.reportPathRoot=$ROOT/{{visui_src}}"
    REPORT="$ROOT/port-report/UslMigrate"
    TREPORT="$ROOT/port-report/UslTestMigrate"

    for M in UslMigrate UslTestMigrate; do
      OUT=$({{sbt_migrate}} "{{corpus}}/runMain balticporter.corpus.visuiusl.$M" 2>&1 | sed 's/\x1b\[[0-9;]*m//g')
      if ! grep -qE "wrote [0-9]+ Scala( test)? files" <<<"$OUT"; then
        echo "!! $M DID NOT RUN — refusing to measure stale output"
        grep -E "^\[error\].*\.scala:[0-9]+|^\[error\] +\|" <<<"$OUT" | head -20
        exit 1
      fi
      echo "-- $M (ALL checks, untruncated, as the migration printed them) --"
      sed -n '/building model over/,/wrote [0-9]* Scala\( test\)\? files/p' <<<"$OUT"
      echo
    done

    echo "-- checks: persisted, untruncated, diffed against the baseline --"
    show_check_report "$REPORT"
    findings_baseline_guard "$REPORT"
    port_map_guard "$REPORT"
    show_check_report "$TREPORT"
    findings_baseline_guard "$TREPORT"
    port_map_guard "$TREPORT"

    echo
    echo "-- test discovery --"
    # Both frameworks summed: a converted suite is MUnit and any residue is still JUnit, so counting
    # one under-reports by every converted suite — in the safe-looking direction. A suite with no
    # discoverable tests runs ZERO and reports SUCCESS.
    #
    # `java_test_count` over the WHOLE usl tree, which is what re-derives the 7 — and, beside
    # `visui-measure`'s own 2, the claim that these are seven of VisUI's nine. A filename census
    # over the same tree reports 2 (both files end in `Test.java`) and is a different number.
    JAVA_TESTS=$(java_test_count {{usl_src}}/src/test)
    JUNIT_LEFT=$(junit_residue {{usl_module}}/src_managed/test/scala)
    MUNIT_TESTS=$(munit_emitted {{usl_module}}/src_managed/test/scala)
    SCALA_TESTS=$((JUNIT_LEFT + MUNIT_TESTS))
    echo "@Test in usl/src/test: $JAVA_TESTS   discoverable in emitted Scala: $SCALA_TESTS (munit $MUNIT_TESTS + junit $JUNIT_LEFT)"
    test_discovery_guard "$JAVA_TESTS" "$SCALA_TESTS" "$TREPORT"

    echo
    break_residue {{usl_module}}/src_managed

    # The JDK is an INPUT to this measurement: the frontend read its class files on ONE JVM and
    # the compile below runs on another. Nothing compared them until an `override` emitted on
    # JDK 24 failed a JDK-22 compile with every other artifact flat (ENGINE-LIMITS M5.10).
    jdk_guard "$TREPORT"
    echo "-- compile --"
    # BOTH source sets on ONE invocation: the main port is RuntimeMode.Vendored, so the shims live
    # in `src_managed/main` and the suite links against them there. Compiling either alone measures
    # nothing. `--test` is not optional — without it scala-cli reports on the MAIN scope whatever
    # directories it is handed, so the test sources' ERRORS are simply not printed and the lane
    # reports a main-only figure under a two-scope headline (§4.56's instrument rule, measured at
    # 0 against 6 on identical inputs).
    DEPS="{{usl_deps}} {{usl_test_deps}}"
    scala-cli compile --test --scala {{scala_version}} --server=false --jvm {{jdk_version}} $DEPS \
      {{usl_module}}/src_managed/main/scala {{usl_module}}/src_managed/test/scala \
      2>&1 | sed 's/\x1b\[[0-9;]*m//g' > "$MEASURE_TMP"/usltmeasure.txt
    CLI_STATUS=${PIPESTATUS[0]}
    ERRORS=$(grep -cE '^-- (\[E[0-9]+\] )?.*Error' "$MEASURE_TMP"/usltmeasure.txt)
    compile_guard "$CLI_STATUS" "$ERRORS" "$MEASURE_TMP"/usltmeasure.txt
    echo "TOTAL ERRORS: $ERRORS  (coded $(grep -cE '\[E[0-9]+\].*Error' "$MEASURE_TMP"/usltmeasure.txt) + bare $(grep -cE '^-- Error:' "$MEASURE_TMP"/usltmeasure.txt))"
    error_baseline_guard "$ERRORS" "$TREPORT"
    grep -oE "\[E[0-9]+\][^:]*Error" "$MEASURE_TMP"/usltmeasure.txt | sort | uniq -c | sort -rn | head
    echo "-- bare (uncoded) errors by message --"
    grep -A1 '^-- Error:' "$MEASURE_TMP"/usltmeasure.txt | grep -vE '^-- Error:|^--$' | sed -E 's/^[0-9]+ \|//; s/[0-9]+//g' | sed -E 's/^ +//' | sort | uniq -c | sort -rn | head

    # Cross-platform compile gates — same source dirs and deps as the JVM compile.
    xplat_compile scala-js {{scala_version}} "$TREPORT" usltmeasure \
      {{usl_module}}/src_managed/main/scala {{usl_module}}/src_managed/test/scala -- --test {{usl_test_deps}}
    xplat_compile scala-native {{scala_version}} "$TREPORT" usltmeasure \
      {{usl_module}}/src_managed/main/scala {{usl_module}}/src_managed/test/scala -- --test {{usl_test_deps}}

    # Reference-flags compile (DESIGN.md §8.24): the reference build's own scalacOptions.
    flags_compile {{scala_version}} "$TREPORT" usltmeasure "{{sge_relaxed_flags}}" {{usl_module}}/src_managed/main/scala {{usl_module}}/src_managed/test/scala -- --test {{usl_test_deps}}

    if [ "$ERRORS" = "0" ]; then
      echo
      echo "-- run --"
      # THE RESOURCE DIRECTORY IS THE LOAD-BEARING ARGUMENT. Every test resolves its input through
      # `getResourceAsStream("/test-*.usl")`, so without it `readFile` receives a null stream and
      # all six fail identically — which would read exactly like a conversion defect.
      scala-cli test --scala {{scala_version}} --server=false --jvm {{jdk_version}} $DEPS \
        --resource-dir {{usl_test_res}} \
        -Duser.language=en -Duser.country=US \
        {{usl_module}}/src_managed/main/scala {{usl_module}}/src_managed/test/scala \
        2>&1 | sed 's/\x1b\[[0-9;]*m//g' > "$MEASURE_TMP"/usltrun.txt
      reconcile_outcomes "$MEASURE_TMP"/usltrun.txt "$MUNIT_TESTS"; RECONCILED=$?
      echo
      echo "-- correlation: test failures located to members and Java origins --"
      correlate "$TREPORT/run-latest" --tests "$MEASURE_TMP"/usltrun.txt \
        --srcmap "$REPORT/run-latest/srcmap.tsv" \
        --srcmap "test=$TREPORT/run-latest/srcmap.tsv"
      test_outcome_guard "$TREPORT/run-latest" "$RECONCILED" || exit 1
    else
      echo
      echo "-- correlation: every error located to its member and its Java origin --"
      correlate "$REPORT/run-latest" --scalac "$MEASURE_TMP"/usltmeasure.txt \
        --srcmap "$REPORT/run-latest/srcmap.tsv" \
        --srcmap "test=$TREPORT/run-latest/srcmap.tsv"
      echo "(not running the suite: it does not compile — a test that cannot run is not a test that passed)"
    fi

    headline "$ERRORS" "$TREPORT" "$REPORT"


# ---------------------------------------------------------------------------------------------
# liqp — the MAIN source set AND its own 105-file JUnit suite.
#
# `sg-measure`'s shape (emit both ports → checks for both → discovery → break residue → compile
# both → RUN when it compiles) with three differences, and each of them is the reason this lane is
# worth reading:
#
#   * THE COMPILE HAS A CLASSPATH THAT IS NOT ONLY COORDINATES. Decision D-liqp-1 keeps the
#     ANTLR-generated parser EXTERNAL and D-liqp-1b compiles it into the namespace the port emits:
#     `LiqpClasspath` rewrites `target/generated-sources/antlr4` and javacs it into
#     `{{liqp_parser_classes}}`, which the frontend, scalac and the test run all read, so every
#     half of the port agrees about what `liquid.parser.v4` is. `--jar` takes a directory of
#     compiled classes, which is what that flag's second name (`--extra-jars`) hides. The lane
#     REFUSES to compile if the directory is not there rather than reporting the resulting import
#     failures as the port's error count.
#   * ONE COMPILE, TWO NUMBERS. Both source sets go through scalac together — they must, the suite
#     links against what the main port emitted — and the count is then SPLIT by the path scalac
#     itself printed, so `PROGRESS.md` §10.5's main-port figure stays a number this lane reproduces
#     rather than one merged past recovery. A test-set error is frequently a CASCADE of a main-set
#     one, which is exactly why the two are never added up into a single wall.
#   * THE SUITE IS EMITTED AND, TODAY, NOT RUN. The main port stands at a measured wall, and the
#     run stage is gated on 0 errors exactly as `sg-measure` gates it — with a line that SAYS the
#     suite did not run. A lane that silently skipped the stage would read as a lane whose tests
#     passed, which is the failure `noise4j-measure` states from the other direction. Everything
#     `CLAUDE.md` §4.4 lists stays unmeasured for this port until that line stops printing.
#
# THE RUN'S WORKING DIRECTORY IS PART OF THE MEASUREMENT, and it is why the run stage looks
# unlike every other lane's. 45 of the 639 tests read `./snippets/`, `./_includes/` and
# `src/test/jekyll/` by RELATIVE path, and `TemplateTest.parseWithInputStream` reaches one through
# `new FileInputStream(new File(…))` — the PROCESS working directory, which `-Duser.dir` does not
# reach (verified). The paths are deliberately NOT parameterised in the Java: they are what
# upstream asserts, and rewriting them would make the port a different program (`CLAUDE.md` §3.5).
# So the lane builds a SYMLINK fixture tree under its own scratch and runs from it, which keeps
# `.scala-build/` out of the ssg submodule's working tree. Two directories of the upstream
# checkout are deliberately absent from that tree: the repo-root `alternative_includes/` is
# referenced by nothing (all three `withSnippetsFolderName` sites name
# `src/test/jekyll/alternative_includes`, which arrives inside the jekyll link), and `ruby/` is a
# standalone shell harness a human invokes — no test shells out, and its `.rb` fragments appear in
# the suite only as Javadoc quotations of the reference behaviour.
#
# The compile is EXPECTED to report errors at this milestone. That is the deliverable — a census,
# classified per CLAUDE.md §1 — so `compile_guard` reporting a non-zero count is data, and the
# lane runs `correlate` whether or not it compiled, because the compiler output is then the only
# diagnostic this port has. A compile that ABORTED is not data and stops the lane there
# (`compile_guard`'s third state): the census would be a floor, and this port's whole deliverable is
# the number.
# ---------------------------------------------------------------------------------------------
[doc("liqp + its own 105-file suite: emit, checks, discovery, break residue, compile, RUN when it compiles")]
liqp-measure:
    #!/usr/bin/env bash
    cd "{{root}}"
    ROOT="$(pwd)"
    export CORE_PROJECT="{{core_project}}"
    . scripts/_lib.sh

    # The finding ids are hashed from paths relative to this root (CLAUDE.md §4.6): set anywhere else
    # and every finding diffs as removed-and-re-added against a baseline whose counts are identical.
    write_run_props "$ROOT" "balticporter.reportPathRoot=$ROOT/{{liqp_src}}"
    REPORT="$ROOT/port-report/LiqpMigrate"
    TREPORT="$ROOT/port-report/LiqpTestMigrate"

    # ABORT if either migration did not run, or the lane measures the PREVIOUS emit and reports a
    # stale number as a result. The test port is a DEPENDENT (`test.conf` has `base = "main.conf"`),
    # so the order is not arbitrary: it resolves against the base's Java and inherits its manifest.
    for M in LiqpMigrate LiqpTestMigrate; do
      OUT=$({{sbt_migrate}} "{{corpus}}/runMain balticporter.corpus.liqp.$M" 2>&1 | sed 's/\x1b\[[0-9;]*m//g')
      if ! grep -qE "wrote [0-9]+ Scala( test)? files" <<<"$OUT"; then
        echo "!! $M DID NOT RUN — refusing to measure stale output"
        grep -E "^\[error\].*\.scala:[0-9]+|^\[error\] +\||IllegalStateException|\[ssg-liquid\]" <<<"$OUT" | head -30
        exit 1
      fi
      echo "-- $M (ALL checks, untruncated, as the migration printed them) --"
      sed -n '/building model over/,/wrote [0-9]* Scala\( test\)\? files/p' <<<"$OUT"
      echo
    done

    echo "-- checks: persisted, untruncated, diffed against the baseline --"
    show_check_report "$REPORT"
    findings_baseline_guard "$REPORT"
    port_map_guard "$REPORT"
    show_check_report "$TREPORT"
    findings_baseline_guard "$TREPORT"
    port_map_guard "$TREPORT"

    echo
    echo "-- test discovery --"
    # Both frameworks summed, as in `sg-measure`: a ported suite is MUnit and any residue is still
    # JUnit, so counting one under-reports by every converted suite — in the safe-looking direction.
    # A suite with no discoverable tests runs ZERO and reports SUCCESS.
    #
    # 639, not the 640 a raw grep finds: `filters/date/FuzzyDateDateParserTest`'s is commented out
    # upstream, which is exactly what `java_test_count`'s comment-aware count is for.
    JAVA_TESTS=$(java_test_count {{liqp_src}}/src/test)
    JUNIT_LEFT=$(junit_residue {{liqp_module}}/src_managed/test/scala)
    MUNIT_TESTS=$(munit_emitted {{liqp_module}}/src_managed/test/scala)
    SCALA_TESTS=$((JUNIT_LEFT + MUNIT_TESTS))
    echo "@Test in Java: $JAVA_TESTS   discoverable in emitted Scala: $SCALA_TESTS (munit $MUNIT_TESTS + junit $JUNIT_LEFT)"
    # …and the LOSS IS BASELINED (scripts/_lib.sh). This lane is the reason: 64 is a PERMANENT
    # figure here (D-liqp-5's four `excludeGlobs` files and D-liqp-7's three `dropMethods` keys),
    # so an accidental 65th changed one digit inside a line nobody had to act on. Deferred to
    # `headline` so the compile and the correlation still run.
    test_discovery_guard "$JAVA_TESTS" "$SCALA_TESTS" "$TREPORT"

    # -------------------------------------------------------------------------------------------
    # THE RUN'S HAND-WRITTEN INPUTS, CHECKED HERE AND NOT AT THE RUN. Both are fatal, and both are
    # checked BEFORE the compile gate on purpose: gated behind `ERRORS = 0` they would first fire
    # on the day the port went green, which is the one day nobody is looking for a missing fixture.
    # A missing input is fatal, never a smaller measurement (CLAUDE.md §5.1).
    # -------------------------------------------------------------------------------------------
    # The SPI descriptor is now EMITTED (`serviceProviders` in main.conf, ENGINE-LIMITS.md P5) — a
    # build product under `src_managed/`, not the hand-written file this used to guard. The guard
    # stays, and is now a guard on the RUN: if the run stopped writing it the suite's ServiceLoader
    # lookups find zero providers and say nothing, which is the whole reason the key exists.
    SERVICES="{{liqp_module}}/src_managed/main/resources/META-INF/services/ssg.liquid.spi.TypesSupport"
    if [ ! -f "$SERVICES" ]; then
      echo "!! $SERVICES is MISSING — the suite's ServiceLoader lookups would find zero providers,"
      echo "   applyCustomDateTypes() would silently no-op, and no compile error, check count or"
      echo "   finding would say so (ENGINE-LIMITS.md P5). The run writes it from the port's"
      echo "   \`serviceProviders\` key; a run that emitted nothing here did not emit this."
      exit 1
    fi
    for F in snippets _includes src/test/jekyll; do
      if [ ! -d "{{liqp_src}}/$F" ]; then
        echo "!! the fixture root {{liqp_src}}/$F is MISSING — 45 tests read it by relative path and"
        echo "   would fail as if the port were wrong. Refusing to measure a suite whose inputs are absent."
        exit 1
      fi
    done

    echo
    break_residue {{liqp_module}}/src_managed

    # The JDK is an INPUT to this measurement: the frontend read its class files on ONE JVM and
    # the compile below runs on another. Nothing compared them until an `override` emitted on
    # JDK 24 failed a JDK-22 compile with every other artifact flat (ENGINE-LIMITS M5.10).
    jdk_guard "$REPORT"
    echo "-- compile --"
    # The generated parser is a directory of CLASS FILES the frontend already read (D-liqp-1). If
    # scalac does not read the same `liquid.parser.v4` the two halves of the port disagree about
    # what it is, and the import failures that follow would count as this port's errors — so this
    # is a refusal, not a smaller number. ONE directory since D-liqp-1b: it is compiled against the
    # namespace the port emits, so nothing here needs upstream `liqp` and nothing may have it.
    if [ ! -d "{{liqp_parser_classes}}/liquid/parser/v4" ]; then
      echo "!! {{liqp_parser_classes}} holds no compiled parser — LiqpClasspath did not build it."
      echo "   Refusing to compile: the resulting unresolved-import errors are not this port's wall."
      exit 1
    fi
    # NOTE the ANSI strip: without it `grep -cE '^-- .*Error'` matches nothing and reports 0 for a port
    # that does not compile — a false NEGATIVE on the headline number.
    # BOTH source sets on one invocation: the main port is RuntimeMode.Vendored, so the shims live in
    # `src_managed/main` and the suite links against them there. Compiling either alone measures nothing.
    # …plus whatever THE PORT ITSELF DECLARED, read from what the run published rather than
    # re-typed here (`declared_dep_flags`, scripts/_lib.sh). Both report directories, because the
    # two source sets are one compile and the suite's manifest may declare a coordinate the main
    # one does not; the helper deduplicates the repositories across them.
    DECLARED=$(declared_dep_flags "$REPORT" "$TREPORT" | tr '\n' ' ')
    if [ -z "$DECLARED" ]; then
      echo "!! run-latest/dependencies.tsv named no classpath coordinate for either source set —"
      echo "   this port DECLARES multiarch-serviceloader and the emitted scala names it outright,"
      echo "   so an empty derivation is a missing artifact and not an empty manifest. Refusing to"
      echo "   compile against a classpath that is short one jar and to report the result as errors."
      exit 1
    fi
    echo "-- declared coordinates, from the run's own dependencies.tsv --"
    echo "   $DECLARED"
    DEPS="{{liqp_deps}} {{liqp_test_deps}} $DECLARED"
    scala-cli compile --test --scala {{scala_version}} --server=false --jvm {{jdk_version}} $DEPS \
      --jar "{{liqp_parser_classes}}" \
      {{liqp_module}}/src_managed/main/scala {{liqp_module}}/src_managed/test/scala \
      2>&1 | sed 's/\x1b\[[0-9;]*m//g' > "$MEASURE_TMP"/liqpmeasure.txt
    CLI_STATUS=${PIPESTATUS[0]}
    ERRORS=$(grep -cE '^-- (\[E[0-9]+\] )?.*Error' "$MEASURE_TMP"/liqpmeasure.txt)
    compile_guard "$CLI_STATUS" "$ERRORS" "$MEASURE_TMP"/liqpmeasure.txt
    echo "TOTAL ERRORS: $ERRORS  (coded $(grep -cE '\[E[0-9]+\].*Error' "$MEASURE_TMP"/liqpmeasure.txt) + bare $(grep -cE '^-- Error:' "$MEASURE_TMP"/liqpmeasure.txt))"
    error_baseline_guard "$ERRORS" "$TREPORT"
    # …and the SPLIT, from the path scalac printed in each error header. The two source sets are one
    # compile and two walls: the main port's figure is what `PROGRESS.md` §10.5 quotes, and a
    # test-set error is often a cascade of a main-set one. `correlate` below attributes every one of
    # them to a member and a Java origin; this is only the shape of the total.
    E_MAIN=$(grep -E '^-- (\[E[0-9]+\] )?.*Error' "$MEASURE_TMP"/liqpmeasure.txt | grep -c "/src_managed/main/")
    E_TEST=$(grep -E '^-- (\[E[0-9]+\] )?.*Error' "$MEASURE_TMP"/liqpmeasure.txt | grep -c "/src_managed/test/")
    echo "  main source set: $E_MAIN   test source set: $E_TEST   elsewhere: $((ERRORS - E_MAIN - E_TEST))"
    grep -oE "\[E[0-9]+\][^:]*Error" "$MEASURE_TMP"/liqpmeasure.txt | sort | uniq -c | sort -rn | head -20
    echo "-- bare (uncoded) errors by message --"
    grep -A1 '^-- Error:' "$MEASURE_TMP"/liqpmeasure.txt | grep -vE '^-- Error:|^--$' | sed -E 's/^[0-9]+ \|//; s/[0-9]+//g' | sed -E 's/^ +//' | sort | uniq -c | sort -rn | head -20

    # Cross-platform compile gates — same deps as the JVM compile, including --jar for the
    # ANTLR parser classes (scalac on JS/Native type-checks against JVM class files fine).
    xplat_compile scala-js {{scala_version}} "$TREPORT" liqpmeasure \
      {{liqp_module}}/src_managed/main/scala {{liqp_module}}/src_managed/test/scala -- --test $DEPS --jar "{{liqp_parser_classes}}"
    xplat_compile scala-native {{scala_version}} "$TREPORT" liqpmeasure \
      {{liqp_module}}/src_managed/main/scala {{liqp_module}}/src_managed/test/scala -- --test $DEPS --jar "{{liqp_parser_classes}}"

    # Reference-flags compile (DESIGN.md §8.24): the reference build's own scalacOptions.
    flags_compile {{scala_version}} "$TREPORT" liqpmeasure "{{ssg_flags}}" {{liqp_module}}/src_managed/main/scala {{liqp_module}}/src_managed/test/scala -- --test $DEPS --jar "{{liqp_parser_classes}}"

    # -------------------------------------------------------------------------------------------
    # RUN them. Compiling a suite measures nothing about behaviour: CLAUDE.md §4.4 lists the java
    # forms that translate to VALID scala meaning something else, and not one moves the count above.
    # For this library the live questions are a `switch` with no `default`, 38 anonymous classes, and
    # whether the ServiceLoader providers were found at all.
    # -------------------------------------------------------------------------------------------
    if [ "$ERRORS" = "0" ]; then
      echo
      echo "-- run --"
      # THE CWD IS PART OF THE MEASUREMENT — see the lane header for why a system property cannot
      # stand in for it, and which two upstream directories are deliberately not linked.
      FIX="$MEASURE_TMP/liqp-run"
      mkdir -p "$FIX/src/test"
      ln -sfn "$ROOT/{{liqp_src}}/snippets"        "$FIX/snippets"
      ln -sfn "$ROOT/{{liqp_src}}/_includes"       "$FIX/_includes"
      ln -sfn "$ROOT/{{liqp_src}}/src/test/jekyll" "$FIX/src/test/jekyll"
      # `--workspace` keeps scala-cli's own `.scala-build/` beside the fixture rather than under the
      # cwd it inherits; `--resource-dir` is what puts the EMITTED
      # META-INF/services/ssg.liquid.spi.TypesSupport on the test JVM's classpath, and without it the
      # suite's ServiceLoader lookups find nothing AND SAY NOTHING (ENGINE-LIMITS.md P5). It reads
      # `src_managed/main/resources` because the descriptor is a build product the run writes from
      # the port's `serviceProviders` key — it was a hand-written `src/main/resources` file until
      # that key existed, which is the state P5's second half described.
      ( cd "$FIX" && scala-cli test --workspace "$FIX" --scala {{scala_version}} --server=false --jvm {{jdk_version}} $DEPS \
          -Duser.language=en -Duser.country=US \
          --jar "$ROOT/{{liqp_parser_classes}}" \
          --resource-dir "$ROOT/{{liqp_module}}/src_managed/main/resources" \
          "$ROOT/{{liqp_module}}/src_managed/main/scala" \
          "$ROOT/{{liqp_module}}/src_managed/test/scala" ) \
        2>&1 | sed 's/\x1b\[[0-9;]*m//g' > "$MEASURE_TMP"/liqprun.txt
      reconcile_outcomes "$MEASURE_TMP"/liqprun.txt "$MUNIT_TESTS"; RECONCILED=$?
      echo
      echo "-- correlation: test failures located to members and Java origins --"
      correlate "$TREPORT/run-latest" --tests "$MEASURE_TMP"/liqprun.txt \
        --srcmap "$REPORT/run-latest/srcmap.tsv" \
        --srcmap "test=$TREPORT/run-latest/srcmap.tsv"
      # THE GATE for a test that stopped RUNNING — the diff `correlate` just wrote is the only
      # thing that can tell a NEW skip from one this port has accepted (scripts/_lib.sh).
      test_outcome_guard "$TREPORT/run-latest" "$RECONCILED" || exit 1
    else
      echo
      echo "-- correlation: every error located to its member and its Java origin --"
      # BOTH maps, scoped: an error in the emitted suite resolves through the test port's map and one
      # in the library through the main port's, so the two walls stay distinguishable after the join.
      correlate "$REPORT/run-latest" --scalac "$MEASURE_TMP"/liqpmeasure.txt \
        --srcmap "$REPORT/run-latest/srcmap.tsv" \
        --srcmap "test=$TREPORT/run-latest/srcmap.tsv"
      echo "(not running the suite: it does not compile — a test that cannot run is not a test that passed)"
      echo "   $JAVA_TESTS java @Test are emitted as $MUNIT_TESTS munit registrations and NONE OF THEM RUNS."
      echo "   Every CLAUDE.md §4.4 form in this port is UNMEASURED until that line stops printing."
    fi

    headline "$ERRORS" "$TREPORT" "$REPORT"

# ---------------------------------------------------------------------------------------------
# ssg-md — flexmark-java, MILESTONE 1: `flexmark` core plus the eleven `flexmark-util-*` modules.
#
# `noise4j-measure`'s shape — emit, checks, discovery, break residue, compile, correlate — because
# this milestone has exactly one source set. Three things about it are worth reading before quoting
# a number from it:
#
#   * THE SCOPE IS A SELECTION AND THE LANE RE-DERIVES IT. `md_src` is the whole 53-module
#     checkout and `md_modules` is the twelve this port converts, so the lane counts the java files
#     under those twelve on every run and prints them beside what the migration says it converted.
#     A scope stated in two files with nothing comparing them is a scope that drifts, and this is
#     the number that would show it: the port's `converted` line and this denominator move together
#     or somebody edited one of them.
#   * THE TEST DISCOVERY BLOCK ASSERTS A ZERO, and the zero is a fact about the SCOPE rather than
#     about the library. flexmark ships 1,306 `@Test` methods, 730 of them in `flexmark-util`'s own
#     module — but not one of the twelve modules here has a `src/test` directory at all, because
#     the split util libraries are tested from that aggregator. So `java_test_count` over the
#     scoped trees is 0 and this lane re-derives it, exactly as `noise4j-measure` and
#     `jbump-measure` re-derive theirs: a lane that silently has no tests is indistinguishable from
#     a lane whose tests all vanished. What that leaves UNMEASURED is everything CLAUDE.md §4.4
#     lists, on a library that is a character-level parser — which is a larger gap here than on any
#     port before it, and `PROGRESS.md` §10.6 says so in the same words.
#   * THE COMPILE CARRIES THE ANNOTATION JAR, and it is not the frontend's copy of it. flexmark's
#     one compile-scope coordinate is resolved by `FlexmarkClasspath` so SPOON can read
#     `@NotNull`/`@Nullable`, and it turns out to be needed AGAIN by scalac, because a MARKER
#     annotation is carried into the emitted Scala whatever the port claims — 237 of 468 emitted
#     files name it. Without it the lane reports 1976 unresolved references as this port's wall.
#     See `md_deps` for the number and for the engine question underneath it.
#
# The compile is EXPECTED to report errors at this milestone. That is the deliverable — a census,
# classified per CLAUDE.md §1 — so `compile_guard` reporting a non-zero count is data, and the lane
# runs `correlate` whether or not it compiled, because the compiler output is then the only
# diagnostic this port has. A compile that ABORTED is not data and stops the lane there
# (`compile_guard`'s third state): the census would be a floor, and this port's whole deliverable is
# the number.
# ---------------------------------------------------------------------------------------------
[doc("flexmark-java core + the eleven util modules — emit, checks, break residue, compile, correlate")]
md-measure:
    #!/usr/bin/env bash
    cd "{{root}}"
    ROOT="$(pwd)"
    export CORE_PROJECT="{{core_project}}"
    . scripts/_lib.sh

    # The finding ids are hashed from paths relative to this root (CLAUDE.md §4.6): set anywhere else
    # and every finding diffs as removed-and-re-added against a baseline whose counts are identical.
    write_run_props "$ROOT" "balticporter.reportPathRoot=$ROOT/{{md_src}}"
    REPORT="$ROOT/port-report/FlexmarkMigrate"

    # ABORT if the migration itself did not run, or the lane measures the PREVIOUS emit and reports a
    # stale number as a result.
    OUT=$({{sbt_migrate}} "{{corpus}}/runMain balticporter.corpus.flexmark.FlexmarkMigrate" 2>&1 | sed 's/\x1b\[[0-9;]*m//g')
    if ! grep -qE "wrote [0-9]+ Scala files" <<<"$OUT"; then
      echo "!! FlexmarkMigrate DID NOT RUN — refusing to measure stale output"
      grep -E "^\[error\].*\.scala:[0-9]+|^\[error\] +\||IllegalStateException|\[ssg-md\]" <<<"$OUT" | head -30
      exit 1
    fi
    echo "-- FlexmarkMigrate (ALL checks, untruncated, as the migration printed them) --"
    sed -n '/building model over/,/wrote [0-9]* Scala files/p' <<<"$OUT"
    echo

    echo "-- scope: the twelve modules this milestone converts --"
    # The denominator, from `md_modules` — see the lane header for why it is recomputed rather than
    # quoted. `package-info.java` is counted here and NOT converted (the loader's default excludes
    # it), so the two numbers differ by exactly the declaration-only files.
    SCOPE_DIRS=""
    for m in {{md_modules}}; do SCOPE_DIRS="$SCOPE_DIRS {{md_src}}/$m/src/main/java"; done
    SCOPE_ALL=$(find $SCOPE_DIRS -name '*.java' 2>/dev/null | wc -l | tr -d ' ')
    SCOPE_PKG=$(find $SCOPE_DIRS -name 'package-info.java' -o -name 'module-info.java' 2>/dev/null | wc -l | tr -d ' ')
    echo "java files in scope: $SCOPE_ALL   declaration-only (package-info/module-info, not converted): $SCOPE_PKG   expected units: $((SCOPE_ALL - SCOPE_PKG))"

    echo
    echo "-- checks: persisted, untruncated, diffed against the baseline --"
    show_check_report "$REPORT"
    findings_baseline_guard "$REPORT"
    port_map_guard "$REPORT"

    echo
    echo "-- test discovery --"
    # ASSERTED, not omitted — see the lane header. The twelve scoped modules ship no `src/test` at
    # all; flexmark's suites live in `flexmark-util`, `flexmark-core-test` and the extensions, none
    # of which this milestone parses.
    TEST_DIRS=""
    for m in {{md_modules}}; do TEST_DIRS="$TEST_DIRS {{md_src}}/$m/src/test"; done
    JAVA_TESTS=$(java_test_count $TEST_DIRS)
    echo "@Test in the twelve scoped modules: $JAVA_TESTS   emitted test files: 0 (this milestone has no test source set)"
    if [ "$JAVA_TESTS" = "0" ]; then
      echo "   NO SUITE IN SCOPE — the split util libraries are tested from flexmark-util's own module,"
      echo "   which milestone 1 does not parse. Every CLAUDE.md §4.4 form in this port is UNMEASURED,"
      echo "   and this library is a character-level parser: see PROGRESS.md §10.6 for what that costs."
    else
      echo "!! A SUITE HAS APPEARED IN SCOPE — $JAVA_TESTS @Test method(s) under a module this port"
      echo "   converts, and none of them runs. Add balticporter/corpus/ports/ssg-md/test.conf (\`base = \"main.conf\"\`)"
      echo "   and a lane stage, or narrow the scope deliberately (CLAUDE.md §3)."
    fi

    echo
    break_residue {{md_module}}/src_managed

    # The JDK is an INPUT to this measurement: the frontend read its class files on ONE JVM and
    # the compile below runs on another. Nothing compared them until an `override` emitted on
    # JDK 24 failed a JDK-22 compile with every other artifact flat (ENGINE-LIMITS M5.10).
    jdk_guard "$REPORT"
    echo "-- compile --"
    # NOTE the ANSI strip: without it `grep -cE '^-- .*Error'` matches nothing and reports 0 for a port
    # that does not compile — a false NEGATIVE on the headline number.
    DEPS="{{md_deps}}"
    scala-cli compile --scala {{scala_version}} --server=false --jvm {{jdk_version}} $DEPS \
      {{md_module}}/src_managed/main/scala \
      2>&1 | sed 's/\x1b\[[0-9;]*m//g' > "$MEASURE_TMP"/mdmeasure.txt
    CLI_STATUS=${PIPESTATUS[0]}
    ERRORS=$(grep -cE '^-- (\[E[0-9]+\] )?.*Error' "$MEASURE_TMP"/mdmeasure.txt)
    compile_guard "$CLI_STATUS" "$ERRORS" "$MEASURE_TMP"/mdmeasure.txt
    echo "TOTAL ERRORS: $ERRORS  (coded $(grep -cE '\[E[0-9]+\].*Error' "$MEASURE_TMP"/mdmeasure.txt) + bare $(grep -cE '^-- Error:' "$MEASURE_TMP"/mdmeasure.txt))"
    error_baseline_guard "$ERRORS" "$REPORT"
    grep -oE "\[E[0-9]+\][^:]*Error" "$MEASURE_TMP"/mdmeasure.txt | sort | uniq -c | sort -rn | head -20
    echo "-- bare (uncoded) errors by message --"
    grep -A1 '^-- Error:' "$MEASURE_TMP"/mdmeasure.txt | grep -vE '^-- Error:|^--$' | sed -E 's/^[0-9]+ \|//; s/[0-9]+//g' | sed -E 's/^ +//' | sort | uniq -c | sort -rn | head -20

    # Cross-platform compile gates — same deps as the JVM compile.
    xplat_compile scala-js {{scala_version}} "$REPORT" mdmeasure \
      {{md_module}}/src_managed/main/scala -- {{md_deps}}
    xplat_compile scala-native {{scala_version}} "$REPORT" mdmeasure \
      {{md_module}}/src_managed/main/scala -- {{md_deps}}

    # Reference-flags compile (DESIGN.md §8.24): the reference build's own scalacOptions.
    flags_compile {{scala_version}} "$REPORT" mdmeasure "{{ssg_flags}}" {{md_module}}/src_managed/main/scala -- {{md_deps}}

    echo
    echo "-- correlation: every error located to its member and its Java origin --"
    # Run WHETHER OR NOT it compiled. With no suite there is no second thing to correlate, so the
    # compile output is the only diagnostic this port has and it is always worth attributing.
    correlate "$REPORT/run-latest" --scalac "$MEASURE_TMP"/mdmeasure.txt \
      --srcmap "$REPORT/run-latest/srcmap.tsv"

    headline "$ERRORS" "$REPORT"

# ---------------------------------------------------------------------------------------------
# ssg-md's SUITE — `flexmark-util/src/test/java`, 52 files and 730 plain `@Test`.
#
# `gdx-test-measure`'s two-lane shape rather than `liqp-measure`'s one-lane one, and the reason is
# `md-measure`'s number: ssg-md MAIN is a census this repository quotes (`PROGRESS.md` §10.6.3), so
# it keeps a lane of its own that reproduces it alone. This lane then compiles BOTH source sets on
# one invocation — it must, the suite links against what the main port emitted, and the main port is
# `RuntimeMode.Vendored` so the shims live in `src_managed/main` — and SPLITS the count by the path
# scalac itself printed. A test-set error is frequently a cascade of a main-set one, which is exactly
# why the two are never added into one wall.
#
# Three things about it worth reading before quoting a number:
#
#   * THE SUITE IS EMITTED AND, TODAY, NOT RUN. ssg-md MAIN stands at a measured wall, so the run
#     stage is gated on 0 errors exactly as `liqp-measure` and `sg-measure` gate theirs — with a line
#     that SAYS the suite did not run. A lane that silently skipped the stage would read as a lane
#     whose tests passed, which is the failure `noise4j-measure` states from the other direction.
#     Everything `CLAUDE.md` §4.4 lists stays unmeasured for this port until that line stops
#     printing, and on a character-level markdown parser that is the largest such population the
#     corpus has.
#   * THE TEST TREE IS NOT ONE OF THE TWELVE MODULES THE PORT CONVERTS. The split `flexmark-util-*`
#     libraries are tested from the `flexmark-util` AGGREGATOR, whose own `src/main/java` is empty.
#     So `md-measure`'s discovery block still asserts its zero and this lane counts a different tree
#     — the two numbers are about different scopes and neither is the other's residue.
#   * THE COMPILE CARRIES THREE COORDINATES AND ONLY ONE IS THIS SOURCE SET'S. `md_deps` is the
#     annotation jar the EMITTED main code names (a marker annotation is carried whatever the port
#     claims — 237 of 468 files); junit is here because the six `@Rule ExpectedException` fields and
#     one JUnit-3 static import survive the conversion (test.conf D-mdt-3), and hamcrest arrives with
#     it transitively; munit is the runner the conversion targets.
# ---------------------------------------------------------------------------------------------
[doc("flexmark-util's own 730-@Test suite: emit, checks, discovery, break residue, compile, RUN when it compiles")]
md-test-measure:
    #!/usr/bin/env bash
    cd "{{root}}"
    ROOT="$(pwd)"
    export CORE_PROJECT="{{core_project}}"
    . scripts/_lib.sh

    # The finding ids are hashed from paths relative to this root (CLAUDE.md §4.6): set anywhere else
    # and every finding diffs as removed-and-re-added against a baseline whose counts are identical.
    write_run_props "$ROOT" "balticporter.reportPathRoot=$ROOT/{{md_src}}"
    REPORT="$ROOT/port-report/FlexmarkMigrate"
    TREPORT="$ROOT/port-report/FlexmarkTestMigrate"

    # ABORT if the migration did not run, or the lane measures the PREVIOUS emit and reports a stale
    # number as a result. Only the TEST port runs here: `md-measure` precedes this lane in
    # `measure-all` and has already re-emitted the main source set this compiles against.
    OUT=$({{sbt_migrate}} "{{corpus}}/runMain balticporter.corpus.flexmark.FlexmarkTestMigrate" 2>&1 | sed 's/\x1b\[[0-9;]*m//g')
    if ! grep -qE "wrote [0-9]+ Scala( test)? files" <<<"$OUT"; then
      echo "!! FlexmarkTestMigrate DID NOT RUN — refusing to measure stale output"
      grep -E "^\[error\].*\.scala:[0-9]+|^\[error\] +\||IllegalStateException|\[ssg-md\]" <<<"$OUT" | head -30
      exit 1
    fi
    echo "-- FlexmarkTestMigrate (ALL checks, untruncated, as the migration printed them) --"
    sed -n '/building model over/,/wrote [0-9]* Scala\( test\)\? files/p' <<<"$OUT"
    echo

    echo "-- checks: persisted, untruncated, diffed against the baseline --"
    show_check_report "$TREPORT"
    findings_baseline_guard "$TREPORT"
    port_map_guard "$TREPORT"

    echo
    echo "-- test discovery --"
    # Both frameworks summed: a ported suite is MUnit and any residue is still JUnit, so counting one
    # under-reports by every converted suite — in the safe-looking direction. A suite with no
    # discoverable tests runs ZERO and reports SUCCESS, which is the whole reason this is baselined.
    JAVA_TESTS=$(java_test_count {{md_test_src}})
    JUNIT_LEFT=$(junit_residue {{md_module}}/src_managed/test/scala)
    MUNIT_TESTS=$(munit_emitted {{md_module}}/src_managed/test/scala)
    SCALA_TESTS=$((JUNIT_LEFT + MUNIT_TESTS))
    echo "@Test in Java: $JAVA_TESTS   discoverable in emitted Scala: $SCALA_TESTS (munit $MUNIT_TESTS + junit $JUNIT_LEFT)"
    test_discovery_guard "$JAVA_TESTS" "$SCALA_TESTS" "$TREPORT"

    echo
    break_residue {{md_module}}/src_managed/test

    # The JDK is an INPUT to this measurement: the frontend read its class files on ONE JVM and
    # the compile below runs on another. Nothing compared them until an `override` emitted on
    # JDK 24 failed a JDK-22 compile with every other artifact flat (ENGINE-LIMITS M5.10).
    jdk_guard "$TREPORT"
    echo "-- compile --"
    # NOTE the ANSI strip: without it `grep -cE '^-- .*Error'` matches nothing and reports 0 for a
    # port that does not compile — a false NEGATIVE on the headline number.
    # BOTH source sets on one invocation: the main port is RuntimeMode.Vendored, so the shims live in
    # `src_managed/main` and the suite links against them there. Compiling either alone measures
    # nothing.
    DEPS="{{md_deps}} {{md_test_deps}}"
    scala-cli compile --test --scala {{scala_version}} --server=false --jvm {{jdk_version}} $DEPS \
      {{md_module}}/src_managed/main/scala {{md_module}}/src_managed/test/scala \
      2>&1 | sed 's/\x1b\[[0-9;]*m//g' > "$MEASURE_TMP"/mdtestmeasure.txt
    CLI_STATUS=${PIPESTATUS[0]}
    ERRORS=$(grep -cE '^-- (\[E[0-9]+\] )?.*Error' "$MEASURE_TMP"/mdtestmeasure.txt)
    compile_guard "$CLI_STATUS" "$ERRORS" "$MEASURE_TMP"/mdtestmeasure.txt
    echo "TOTAL ERRORS: $ERRORS  (coded $(grep -cE '\[E[0-9]+\].*Error' "$MEASURE_TMP"/mdtestmeasure.txt) + bare $(grep -cE '^-- Error:' "$MEASURE_TMP"/mdtestmeasure.txt))"
    error_baseline_guard "$ERRORS" "$TREPORT"
    # …and the SPLIT, from the path scalac printed in each error header. `md-measure`'s figure is
    # what PROGRESS.md §10.6.3 quotes, and a test-set error is often a cascade of a main-set one.
    E_MAIN=$(grep -E '^-- (\[E[0-9]+\] )?.*Error' "$MEASURE_TMP"/mdtestmeasure.txt | grep -c "/src_managed/main/")
    E_TEST=$(grep -E '^-- (\[E[0-9]+\] )?.*Error' "$MEASURE_TMP"/mdtestmeasure.txt | grep -c "/src_managed/test/")
    echo "  main source set: $E_MAIN   test source set: $E_TEST   elsewhere: $((ERRORS - E_MAIN - E_TEST))"
    grep -oE "\[E[0-9]+\][^:]*Error" "$MEASURE_TMP"/mdtestmeasure.txt | sort | uniq -c | sort -rn | head -20
    echo "-- bare (uncoded) errors by message --"
    grep -A1 '^-- Error:' "$MEASURE_TMP"/mdtestmeasure.txt | grep -vE '^-- Error:|^--$' | sed -E 's/^[0-9]+ \|//; s/[0-9]+//g' | sed -E 's/^ +//' | sort | uniq -c | sort -rn | head -20

    # Cross-platform compile gates — same deps as the JVM compile, no --resource-dir.
    xplat_compile scala-js {{scala_version}} "$TREPORT" mdtestmeasure \
      {{md_module}}/src_managed/main/scala {{md_module}}/src_managed/test/scala -- --test {{md_deps}} {{md_test_deps}}
    xplat_compile scala-native {{scala_version}} "$TREPORT" mdtestmeasure \
      {{md_module}}/src_managed/main/scala {{md_module}}/src_managed/test/scala -- --test {{md_deps}} {{md_test_deps}}

    # Reference-flags compile (DESIGN.md §8.24): the reference build's own scalacOptions.
    flags_compile {{scala_version}} "$TREPORT" mdtestmeasure "{{ssg_flags}}" {{md_module}}/src_managed/main/scala {{md_module}}/src_managed/test/scala -- --test {{md_deps}} {{md_test_deps}}

    if [ "$ERRORS" = "0" ]; then
      echo
      echo "-- run --"
      # `--resource-dir` is what puts the spec files, the library's OWN `entities.properties` and the
      # harness marker on the test JVM's classpath; see `md_spec_res` for all three, for why the
      # harness's two are the upstream's own bytes at the upstream's own paths, and for why the
      # library's own is now the PORT's output instead (`DESIGN.md` §8.22).
      scala-cli test --scala {{scala_version}} --server=false --jvm {{jdk_version}} $DEPS \
        --resource-dir "$ROOT/{{md_spec_res}}" \
        --resource-dir "$ROOT/{{md_lib_res}}" \
        --resource-dir "$ROOT/{{md_tutil_res}}" \
        {{md_module}}/src_managed/main/scala {{md_module}}/src_managed/test/scala \
        2>&1 | sed 's/\x1b\[[0-9;]*m//g' > "$MEASURE_TMP"/mdtestrun.txt
      reconcile_outcomes "$MEASURE_TMP"/mdtestrun.txt "$MUNIT_TESTS"; RECONCILED=$?
      echo
      echo "-- correlation: test failures located to members and Java origins --"
      correlate "$TREPORT/run-latest" --tests "$MEASURE_TMP"/mdtestrun.txt \
        --srcmap "$REPORT/run-latest/srcmap.tsv" \
        --srcmap "test=$TREPORT/run-latest/srcmap.tsv"
      test_outcome_guard "$TREPORT/run-latest" "$RECONCILED" || exit 1
    else
      echo
      echo "-- correlation: every error located to its member and its Java origin --"
      # BOTH maps, scoped: an error in the emitted suite resolves through the test port's map and one
      # in the library through the main port's, so the two walls stay distinguishable after the join.
      # The OUTPUT goes to the TEST report and not the main one, which is the difference a two-lane
      # split makes: this compile carries both source sets, so writing its 87-row `errors.tsv` into
      # `FlexmarkMigrate/run-latest` would overwrite the 43-row artifact `md-measure` had just
      # written — the file `PROGRESS.md` §10.6.3's census is counted from, replaced by a superset
      # after the lane that produced it had already passed.
      correlate "$TREPORT/run-latest" --scalac "$MEASURE_TMP"/mdtestmeasure.txt \
        --srcmap "$REPORT/run-latest/srcmap.tsv" \
        --srcmap "test=$TREPORT/run-latest/srcmap.tsv"
      echo "(not running the suite: it does not compile — a test that cannot run is not a test that passed)"
      echo "   $JAVA_TESTS java @Test are emitted as $MUNIT_TESTS munit registrations and NONE OF THEM RUNS."
      echo "   Every CLAUDE.md §4.4 form in this port is UNMEASURED until that line stops printing."
    fi

    headline "$ERRORS" "$TREPORT"

# ---------------------------------------------------------------------------------------------
# ssg-md's COMMONMARK CONFORMANCE CONTROL — the upstream JAVA, measured.
#
# THE ONE LANE HERE THAT DOES NOT MEASURE A PORT. `PROGRESS.md` §10.6.7 quotes "1,870 of 1,870 spec
# examples (100 %), against a MEASURED green java control" and a per-example table beside it; the
# port's half of that is `md-test-measure`'s, and the CONTROL's half was produced by hand. §5's rule
# is that a number is reproduced by a lane or it is not quoted, and a control is exactly the number
# an agent cannot re-derive from anything else in this repository: `md-test-measure` reporting green
# says the port agrees with the java library, and says nothing about whether that library agrees with
# CommonMark.
#
# Three things about it worth reading before quoting a number:
#
#   * IT IS NOT IN `measure-all`, deliberately. Its inputs are the upstream java tree and six spec
#     resources, and neither moves between corpus waves — a lane whose inputs are constant does not
#     belong in the serial gate every commit runs. It is run when the conformance claim is quoted,
#     changed or doubted.
#   * THE PER-EXAMPLE SPLIT NEEDS A DRIVER, and the driver is `scripts/md-conformance/`. The suite's
#     own `@Test` is ONE `assertEquals` over the whole rendered spec, so `OK (4 tests)` answers
#     pass/fail per spec FILE and says nothing about how much of CommonMark either side implements.
#     Both drivers call the suite's OWN `create`/`readExamples`/`getFullSpec`/`getExpectedFullSpec`
#     and add only the split at `SpecReader.EXAMPLE_BREAK` — a driver that built its own parser and
#     renderer would agree with the suite by luck.
#   * `spec.0.29.txt` IS DRIVEN AND IS NEVER ADDED IN. Upstream disables it
#     (`FullOrigSpec029CoreTest.getSpecResourceLocation` returns `ResourceLocation.NULL` under
#     `// FIX: implement 0.29 spec and enable test`), so java runs zero of its 649 examples and so
#     does the port. The lane drives it through the class's public `RESOURCE_LOCATION` because the
#     port's own 0.29 residue is unreadable without a control — a failure java's own renderer shares
#     is a rule flexmark never implemented, not a port defect — and it is reported apart from the
#     three-spec total, which is the only conformance claim anybody makes.
#
# `--with-port` adds the same census over the emitted Scala and classifies 0.29 example by example.
# It is off by default because it compiles both emitted source sets; it needs `md-test-measure` to
# have emitted them, and it does not re-emit.
# ---------------------------------------------------------------------------------------------
[doc("the CommonMark conformance CONTROL: compile the upstream java, run the four spec suites, split per example (--with-port also drives the port and classifies 0.29)")]
md-conformance *ARGS:
    #!/usr/bin/env bash
    cd "{{root}}"
    export MD_SRC="{{md_src}}"
    export MD_MODULES="{{md_modules}}"
    export MD_TEST_SRC="{{md_test_src}}"
    export MD_SPEC_RES="{{md_spec_res}}"
    export MD_LIB_RES="{{md_lib_res}}"
    export MD_TUTIL_RES="{{md_tutil_res}}"
    export MD_DEPS="{{md_deps}}"
    export MD_TEST_DEPS="{{md_test_deps}}"
    export MD_MODULE="{{md_module}}"
    export SCALA_VERSION="{{scala_version}}"
    . scripts/md-conformance.sh {{ARGS}}

# ---------------------------------------------------------------------------------------------
# ssg-md's EXTENSION half — MILESTONE 2, as ONE dependent port of the base (`ext.conf`).
#
# `ashley-measure`'s shape: a DEPENDENT port compiled WITH the base it resolved against, never
# alone. What is new here is what it is a dependent OF — a twelve-module base with a single package
# root — and what it converts: library code that implements the base's OWN extension points
# (`Parser.ParserExtension`, `HtmlRenderer.HtmlRendererExtension`, `Formatter.FormatterExtension`,
# `BlockParserFactory`, `NodeRendererFactory`, `NodeFormatterFactory`). So the question this lane
# asks and no earlier one could is whether the shared surface the base EMITTED is one a third party
# can implement against, which is exactly what `base-surface` and `manifest` exist to answer and the
# first place on this corpus where they have real content: 458 shared types compared on every run.
#
# Four things about it worth reading before quoting a number:
#
#   * IT RUNS AFTER `md-measure`, and the ordering is a dependency order. This port resolves against
#     the twelve modules' JAVA and inherits their manifest, and its compile links against the Scala
#     `md-measure` just wrote — run first it would measure the previous engine's emit of the library
#     it extends. `PortMap.discoverIn` makes the same point one artifact over: with no fresh
#     `run-latest`, the base's COMMITTED baseline map answers instead, and the run says which it
#     read.
#   * THE COMPILE CARRIES BOTH SOURCE SETS ON ONE INVOCATION, for `md-test-measure`'s reason. The
#     base is `RuntimeMode.Vendored` and this port is `dependency`, so the support types live in
#     `ported/ssg-md/src_managed/main` and this tree links against them there. Compiling either
#     alone measures nothing — and the split by the path scalac printed is what keeps a base error
#     from being counted as this port's wall.
#   * THE SCOPE IS A SELECTION AND THE LANE RE-DERIVES IT, exactly as `md-measure` does — see
#     `md_ext_modules`. This is the number a batch wave moves, and it is the number that catches a
#     wave that edited the Justfile and not the conf.
#   * IT DRIVES TWO PORTS, `liqp-measure`'s shape rather than the two-lane split `md-measure` and
#     `md-test-measure` use. That split exists because ssg-md MAIN's census is a number this
#     repository quotes and needs a lane that reproduces it alone; milestone 2 has no such number, and
#     the extension's suite is the whole point of the milestone rather than a second deliverable.
#     `ext-test.conf` is a dependent of `ext.conf`, so the order inside the lane is a dependency order
#     too. The TEST discovery figure is baselined (`expected-lost`), because a suite with no
#     discoverable tests runs ZERO and reports SUCCESS.
# ---------------------------------------------------------------------------------------------
[doc("flexmark's extension modules as ONE dependent port of ssg-md — emit, checks, compile WITH the base, RUN")]
md-ext-measure:
    #!/usr/bin/env bash
    cd "{{root}}"
    ROOT="$(pwd)"
    export CORE_PROJECT="{{core_project}}"
    . scripts/_lib.sh

    # The finding ids are hashed from paths relative to this root (CLAUDE.md §4.6): set anywhere else
    # and every finding diffs as removed-and-re-added against a baseline whose counts are identical.
    # The SAME root as `md-measure`'s — one upstream checkout, one namespace for both halves of it.
    write_run_props "$ROOT" "balticporter.reportPathRoot=$ROOT/{{md_src}}"
    REPORT="$ROOT/port-report/FlexmarkMigrate"
    EREPORT="$ROOT/port-report/FlexmarkExtMigrate"
    ETREPORT="$ROOT/port-report/FlexmarkExtTestMigrate"

    # ABORT if either migration did not run, or the lane measures the PREVIOUS emit and reports a
    # stale number as a result. The order is a dependency order: `ext-test.conf` declares
    # `base = "ext.conf"`, which declares `base = "main.conf"` — the corpus's first three-link chain.
    for M in FlexmarkExtMigrate FlexmarkExtTestMigrate; do
      OUT=$({{sbt_migrate}} "{{corpus}}/runMain balticporter.corpus.flexmark.$M" 2>&1 | sed 's/\x1b\[[0-9;]*m//g')
      if ! grep -qE "wrote [0-9]+ Scala( test)? files" <<<"$OUT"; then
        echo "!! $M DID NOT RUN — refusing to measure stale output"
        grep -E "^\[error\].*\.scala:[0-9]+|^\[error\] +\||IllegalStateException|\[ssg-md-ext" <<<"$OUT" | head -30
        exit 1
      fi
      echo "-- $M (ALL checks, untruncated, as the migration printed them) --"
      sed -n '/building model over/,/wrote [0-9]* Scala\( test\)\? files/p' <<<"$OUT"
      echo
      # WHICH base map answered. `manifest` and `base-surface` are the two checks this port exists to
      # give content to, and both read the base's published map — `run-latest` when `md-measure` has
      # run in this checkout, the COMMITTED baseline when it has not. Two different artifacts can give
      # two different answers with every count identical, so the lane prints which one it was rather
      # than leaving it in the scrollback.
      grep -E "MANIFEST agreement|BASE SURFACE" <<<"$OUT"
      echo
    done

    echo
    echo "-- scope: the extension modules this port converts --"
    # The denominator, from `md_ext_modules` — see the lane header for why it is recomputed rather
    # than quoted. `package-info.java` is counted here and NOT converted (the loader's default
    # excludes it), so the two numbers differ by exactly the declaration-only files.
    SCOPE_DIRS=""
    for m in {{md_ext_modules}}; do SCOPE_DIRS="$SCOPE_DIRS {{md_src}}/$m/src/main/java"; done
    SCOPE_ALL=$(find $SCOPE_DIRS -name '*.java' 2>/dev/null | wc -l | tr -d ' ')
    SCOPE_PKG=$(find $SCOPE_DIRS -name 'package-info.java' -o -name 'module-info.java' 2>/dev/null | wc -l | tr -d ' ')
    MODCOUNT=$(echo {{md_ext_modules}} | wc -w | tr -d ' ')
    echo "modules in scope: $MODCOUNT of 29 covered   java files: $SCOPE_ALL   declaration-only (not converted): $SCOPE_PKG   expected units: $((SCOPE_ALL - SCOPE_PKG))"

    echo
    echo "-- checks: persisted, untruncated, diffed against the baseline --"
    for R in "$EREPORT" "$ETREPORT"; do
      show_check_report "$R"
      findings_baseline_guard "$R"
      port_map_guard "$R"
      echo
    done

    echo "-- test discovery --"
    # Both frameworks summed: a ported suite is MUnit and any residue is still JUnit, so counting one
    # under-reports by every converted suite — in the safe-looking direction.
    #
    # THE DENOMINATOR IS `md_ext_test_src` AND NOT THE MODULES' `src/test`, and that is a scope
    # decision rather than an omission: the extension suites are overwhelmingly
    # `@RunWith(Parameterized.class)` `ComboSpecTestCase` subclasses (`PROGRESS.md` §10.6.1's
    # documented refusal), and counting their `@Test`s here would report a decision as tests the port
    # LOST — the one failure this guard must not have (ENGINE-LIMITS M5). `java_test_count` takes
    # `find` starting points and a file is one, which is why the variable names files.
    JAVA_TESTS=$(java_test_count {{md_ext_test_src}})
    JUNIT_LEFT=$(junit_residue {{md_ext_module}}/src_managed/test/scala)
    MUNIT_TESTS=$(munit_emitted {{md_ext_module}}/src_managed/test/scala)
    SCALA_TESTS=$((JUNIT_LEFT + MUNIT_TESTS))
    echo "@Test in Java: $JAVA_TESTS   discoverable in emitted Scala: $SCALA_TESTS (munit $MUNIT_TESTS + junit $JUNIT_LEFT)"
    test_discovery_guard "$JAVA_TESTS" "$SCALA_TESTS" "$ETREPORT"

    echo
    break_residue {{md_ext_module}}/src_managed

    # The JDK is an INPUT to this measurement: the frontend read its class files on ONE JVM and
    # the compile below runs on another. Nothing compared them until an `override` emitted on
    # JDK 24 failed a JDK-22 compile with every other artifact flat (ENGINE-LIMITS M5.10).
    jdk_guard "$EREPORT"
    echo "-- compile --"
    # NOTE the ANSI strip: without it `grep -cE '^-- .*Error'` matches nothing and reports 0 for a
    # port that does not compile — a false NEGATIVE on the headline number.
    # BOTH TREES on one invocation: the base is RuntimeMode.Vendored, so the shims live in its
    # `src_managed/main` and this dependent links against them there. Compiling either alone measures
    # nothing — and this is also the only thing that can catch the §1.5 failure the whole milestone is
    # about, two ports that each compile alone and cannot compile together.
    #
    # `--test` is not optional: without it scala-cli reports on the MAIN scope whatever directories it
    # is handed, so the test tree's WARNINGS print and its ERRORS do not, and the split below would
    # read a structural 0 for the suite (CLAUDE.md §4.56's third occurrence).
    #
    # `ported/ssg-md/src_managed/test/scala` is deliberately NOT here: `ext-test.conf` D-mdet-1 chose
    # extensions whose plain `@Test`s need no `flexmark-test-util`, and adding that tree would put
    # ssg-md-test's own 725 registrations on this lane's run.
    #
    # `md_ext_deps` is this lane's and not the base's — see its own comment: the emitted extension
    # code NAMES `org.nibor.autolink`, and the base names nothing from it.
    DEPS="{{md_deps}} {{md_test_deps}} {{md_ext_deps}}"
    scala-cli compile --test --scala {{scala_version}} --server=false --jvm {{jdk_version}} $DEPS \
      {{md_module}}/src_managed/main/scala \
      {{md_ext_module}}/src_managed/main/scala {{md_ext_module}}/src_managed/test/scala \
      2>&1 | sed 's/\x1b\[[0-9;]*m//g' > "$MEASURE_TMP"/mdextmeasure.txt
    CLI_STATUS=${PIPESTATUS[0]}
    ERRORS=$(grep -cE '^-- (\[E[0-9]+\] )?.*Error' "$MEASURE_TMP"/mdextmeasure.txt)
    compile_guard "$CLI_STATUS" "$ERRORS" "$MEASURE_TMP"/mdextmeasure.txt
    echo "TOTAL ERRORS: $ERRORS  (coded $(grep -cE '\[E[0-9]+\].*Error' "$MEASURE_TMP"/mdextmeasure.txt) + bare $(grep -cE '^-- Error:' "$MEASURE_TMP"/mdextmeasure.txt))"
    error_baseline_guard "$ERRORS" "$EREPORT"
    # …and the SPLIT, from the path scalac printed in each error header. `md-measure`'s figure is what
    # `PROGRESS.md` §10.6.3 quotes and it must not absorb an extension's error, nor the reverse: an
    # extension error is THIS port's wall and a base error is a regression `md-measure` already failed
    # on.
    E_BASE=$(grep -E '^-- (\[E[0-9]+\] )?.*Error' "$MEASURE_TMP"/mdextmeasure.txt | grep -c "/ssg-md/src_managed/")
    E_EXT=$(grep -E '^-- (\[E[0-9]+\] )?.*Error' "$MEASURE_TMP"/mdextmeasure.txt | grep -c "/ssg-md-ext/src_managed/main/")
    E_ETST=$(grep -E '^-- (\[E[0-9]+\] )?.*Error' "$MEASURE_TMP"/mdextmeasure.txt | grep -c "/ssg-md-ext/src_managed/test/")
    echo "  base tree: $E_BASE   extension main: $E_EXT   extension test: $E_ETST   elsewhere: $((ERRORS - E_BASE - E_EXT - E_ETST))"
    grep -oE "\[E[0-9]+\][^:]*Error" "$MEASURE_TMP"/mdextmeasure.txt | sort | uniq -c | sort -rn | head -20
    echo "-- bare (uncoded) errors by message --"
    grep -A1 '^-- Error:' "$MEASURE_TMP"/mdextmeasure.txt | grep -vE '^-- Error:|^--$' | sed -E 's/^[0-9]+ \|//; s/[0-9]+//g' | sed -E 's/^ +//' | sort | uniq -c | sort -rn | head -20

    # Cross-platform compile gates — same deps as the JVM compile.
    xplat_compile scala-js {{scala_version}} "$EREPORT" mdextmeasure \
      {{md_module}}/src_managed/main/scala {{md_ext_module}}/src_managed/main/scala {{md_ext_module}}/src_managed/test/scala -- --test {{md_deps}} {{md_test_deps}} {{md_ext_deps}}
    xplat_compile scala-native {{scala_version}} "$EREPORT" mdextmeasure \
      {{md_module}}/src_managed/main/scala {{md_ext_module}}/src_managed/main/scala {{md_ext_module}}/src_managed/test/scala -- --test {{md_deps}} {{md_test_deps}} {{md_ext_deps}}

    # Reference-flags compile (DESIGN.md §8.24): the reference build's own scalacOptions.
    flags_compile {{scala_version}} "$EREPORT" mdextmeasure "{{ssg_flags}}" {{md_module}}/src_managed/main/scala {{md_ext_module}}/src_managed/main/scala {{md_ext_module}}/src_managed/test/scala -- --test {{md_deps}} {{md_test_deps}} {{md_ext_deps}}

    if [ "$ERRORS" = "0" ]; then
      echo
      echo "-- run --"
      # `--resource-dir` puts the LIBRARY'S OWN `entities.properties` on the test JVM's classpath —
      # `md_lib_res`, which is the BASE PORT'S OWN OUTPUT and no longer the upstream tree
      # (`DESIGN.md` §8.22): `Html5Entities` reads it in a static initialiser to build the HTML5
      # entity table, so every `&nbsp;` in every document needs it and its absence is an
      # `ExceptionInInitializerError` that no compile, check or count can see. Pointed at upstream
      # this flag made the suite pass while the port shipped nothing. The spec files and the harness
      # marker are `md-test-measure`'s and are not on this lane's path.
      scala-cli test --scala {{scala_version}} --server=false --jvm {{jdk_version}} $DEPS \
        --resource-dir "$ROOT/{{md_lib_res}}" \
        {{md_module}}/src_managed/main/scala \
        {{md_ext_module}}/src_managed/main/scala {{md_ext_module}}/src_managed/test/scala \
        2>&1 | sed 's/\x1b\[[0-9;]*m//g' > "$MEASURE_TMP"/mdextrun.txt
      reconcile_outcomes "$MEASURE_TMP"/mdextrun.txt "$MUNIT_TESTS"; RECONCILED=$?
      echo
      echo "-- correlation: test failures located to members and Java origins --"
      correlate "$ETREPORT/run-latest" --tests "$MEASURE_TMP"/mdextrun.txt \
        --srcmap "$REPORT/run-latest/srcmap.tsv" \
        --srcmap "$EREPORT/run-latest/srcmap.tsv" \
        --srcmap "test=$ETREPORT/run-latest/srcmap.tsv"
      test_outcome_guard "$ETREPORT/run-latest" "$RECONCILED" || exit 1
    else
      echo
      echo "-- correlation: every error located to its member and its Java origin --"
      # ALL THREE maps, scoped: an error in the emitted suite resolves through the test port's map, one
      # in an extension through this port's and one in the library through the base's, so the three
      # walls stay distinguishable after the join. The OUTPUT goes to the TEST report and not the
      # main one — writing it into `FlexmarkMigrate/run-latest` would overwrite the artifact
      # `md-measure` had just written, which is `PROGRESS.md` §10.6.3's census.
      correlate "$ETREPORT/run-latest" --scalac "$MEASURE_TMP"/mdextmeasure.txt \
        --srcmap "$REPORT/run-latest/srcmap.tsv" \
        --srcmap "$EREPORT/run-latest/srcmap.tsv" \
        --srcmap "test=$ETREPORT/run-latest/srcmap.tsv"
      echo "(not running the suite: it does not compile — a test that cannot run is not a test that passed)"
      echo "   $JAVA_TESTS java @Test are emitted as $MUNIT_TESTS munit registrations and NONE OF THEM RUNS."
      echo "   An extension is a REGISTRATION mechanism and every failure mode of one is silent; until"
      echo "   that line stops printing this milestone has a compile and no evidence (CLAUDE.md §3)."
    fi

    headline "$ERRORS" "$EREPORT"

# ---------------------------------------------------------------------------------------------
# TextraTypist — a libGDX dependent, and the first port whose own build names a THIRD-PARTY jar.
#
# ONE source set and no suite stage, for the two reasons the lane prints rather than asserts:
# upstream's `src/test/java` declares zero `@Test` (it is 128 manual LWJGL3 demos), and this port
# has no hand-written suite yet. That makes it `jbump-measure`'s shape without the differential
# probe — the probe against the reference hand port's own 32-file MUnit suite is a later wave, and
# PROGRESS.md §10.8 holds its scope.
#
# The compile line carries NO coordinate of its own (`textra_deps` is empty and says why): libGDX
# arrives as the base's emitted SOURCE, and regexodus is derived from what the run published.
# ---------------------------------------------------------------------------------------------
[doc("TextraTypist, compiled WITH libGDX core (a dependent port)")]
textra-measure:
    #!/usr/bin/env bash
    cd "{{root}}"
    ROOT="$(pwd)"
    export CORE_PROJECT="{{core_project}}"
    . scripts/_lib.sh

    # The finding ids are hashed from paths relative to this root (CLAUDE.md §4.6): set anywhere else
    # and every finding diffs as removed-and-re-added against a baseline whose counts are identical.
    write_run_props "$ROOT" "balticporter.reportPathRoot=$ROOT/{{textra_src}}"
    REPORT="$ROOT/port-report/TextraTypistMigrate"

    OUT=$({{sbt_migrate}} "{{corpus}}/runMain balticporter.corpus.textra.TextraTypistMigrate" 2>&1 | sed 's/\x1b\[[0-9;]*m//g')
    if ! grep -qE "wrote [0-9]+ Scala files" <<<"$OUT"; then
      echo "!! TextraTypistMigrate DID NOT RUN — refusing to measure stale output"
      grep -E "^\[error\].*\.scala:[0-9]+|^\[error\] +\|" <<<"$OUT" | head -20
      exit 1
    fi
    echo "-- TextraTypistMigrate (every line it printed) --"
    sed -n '/building model over/,/wrote [0-9]* Scala files/p' <<<"$OUT"
    echo

    echo "-- checks: persisted, untruncated, diffed against the baseline --"
    show_check_report "$REPORT"
    findings_baseline_guard "$REPORT"
    port_map_guard "$REPORT"

    echo
    echo "-- scope --"
    # RE-DERIVED, not asserted in a comment. `src/main/java` holds 95 `.java` files and three of them
    # are `package-info.java` — javadoc-only placeholders declaring no type — so 92 are in scope.
    ALL_JAVA=$(find {{textra_src}}/src/main/java -name '*.java' | wc -l | tr -d ' ')
    PKG_INFO=$(find {{textra_src}}/src/main/java -name 'package-info.java' | wc -l | tr -d ' ')
    echo "upstream .java: $ALL_JAVA   package-info: $PKG_INFO   in scope: $((ALL_JAVA - PKG_INFO))"

    echo
    echo "-- licence obligations: THREE regimes, and only one of them is a manifest key --"
    # (a) and (b) are reproduced BY THE COMMENT HARVEST and (c) by `Provenance.notices`. §4.58's own
    # argument is why (b) is grepped rather than assumed: the harvest regressed to `Nil` once and the
    # emitted Scala was still valid, so a notice reproduced "by construction" is reproduced by a
    # mechanism that can fail silently. The `notices` half needs no grep — a declared file that is
    # not there is fatal at the run — but the COPY is asserted, because §4.57's whole point is that
    # naming a licence is not including it.
    # THE DENOMINATOR IS UPSTREAM'S, NOT THE EMITTED COUNT, and that is the whole point of the line.
    # 10 of the 95 upstream files carry NO licence header at all — a bare `package` and a javadoc —
    # so "82 of 92" against the emitted total reads as a ten-file LOSS and is a faithful
    # reproduction. Both sides are re-derived and the gate is that they AGREE; the headerless ten
    # are the SECOND thing (c) answers, and are `GdxAiPolicy`'s one-file case at ten times the scale.
    #
    # …AND THE UPSTREAM SIDE IS THE SCOPE THIS RUN CONVERTS, not the tree — which is §4.56's own rule
    # read at a denominator, and it fired here on the first run. All three `package-info.java` DO
    # carry the Apache header and none of them is EMITTED (they declare no type), so a count over the
    # tree reads 85 against 82 and reports a three-file loss where there is nothing to attribute: the
    # port emits no file those notices could head. Excluded, the two sides agree exactly.
    UP_APACHE=$(grep -rl --exclude=package-info.java "Licensed under the Apache License" {{textra_src}}/src/main/java | wc -l | tr -d ' ')
    APACHE=$(grep -rl "Licensed under the Apache License" {{textra_module}}/src_managed/main/scala | wc -l | tr -d ' ')
    EMITTED=$(find {{textra_module}}/src_managed/main/scala -name '*.scala' | wc -l | tr -d ' ')
    echo "(a) Apache-2.0 per-file notice: $UP_APACHE upstream file(s) carry it, $APACHE of $EMITTED emitted file(s) reproduce it"
    echo "    the other $((EMITTED - APACHE)) emitted file(s) come from upstream files that carry NO per-file notice —"
    echo "    which is the second obligation (c) discharges, and why 'reproduced by construction' is not enough here"
    [ "$APACHE" = "0" ] && echo "!! THE PER-FILE HARVEST IS PRODUCING NOTHING — every emitted file is an unattributed derived work"
    [ "$APACHE" != "$UP_APACHE" ] && echo "!! THE TWO SIDES DISAGREE — $UP_APACHE upstream against $APACHE emitted. Either the harvest lost a notice or upstream's headers moved; §4.58 says only a text-to-text comparison can see this."
    if grep -rqs "Mathias Bynens" {{textra_module}}/src_managed/main/scala; then
      echo "(b) the emoji-regex MIT notice, reproduced inline (EmojiProcessor)"
    else
      echo "!! (b) THE EMOJI-REGEX MIT NOTICE IS GONE — it is a self-contained MIT text in one upstream"
      echo "   file's leading comment, so losing it is a licence failure the compile cannot see (§4.58)"
    fi
    for n in LICENSE typing-label.LICENSE; do
      if [ -f "{{textra_module}}/src_managed/main/$n" ] || [ -f "{{textra_module}}/src_managed/$n" ]; then
        echo "(c) $n copied beside the emitted code"
      else
        echo "!! (c) $n WAS NOT COPIED — declared in Provenance.notices; MIT's one condition is that"
        echo "   the notice be INCLUDED in copies, and the port names it without shipping it (§4.57)"
      fi
    done

    echo
    echo "-- test discovery --"
    # UPSTREAM'S OWN ZERO, re-derived rather than quoted — `jbump-measure`'s stage, and the reason it
    # is a stage and not an omission: a suite with no discoverable tests runs ZERO and reports
    # SUCCESS, and omitting the block is how the day upstream gains a real suite goes unnoticed.
    JAVA_TESTS=$(java_test_count {{textra_src}}/src/test/java)
    DEMO_FILES=$(find {{textra_src}}/src/test/java -name '*.java' | wc -l | tr -d ' ')
    echo "@Test in the upstream test tree ({{textra_src}}/src/test/java, $DEMO_FILES files): $JAVA_TESTS"
    if [ "$JAVA_TESTS" = "0" ]; then
      echo "   NO SUITE UPSTREAM — build.gradle names no JUnit coordinate and every file there is a"
      echo "   manual LWJGL3 demo with a main(). There is nothing for the engine to port, so this port"
      echo "   has NO behavioural evidence at all yet (CLAUDE.md §3) and every §4.4 form in it is"
      echo "   UNMEASURED. PROGRESS.md §10.8 scopes the differential probe that would change that."
    else
      echo "!! A SUITE HAS APPEARED UPSTREAM — $JAVA_TESTS @Test method(s). This port has no test"
      echo "   source set; add a TextraTypistTestMigrate and a lane stage."
    fi
    echo "emitted test files: 0 (this port has no test source set)"

    echo
    break_residue {{textra_module}}/src_managed

    # The JDK is an INPUT to this measurement: the frontend read its class files on ONE JVM and
    # the compile below runs on another. Nothing compared them until an `override` emitted on
    # JDK 24 failed a JDK-22 compile with every other artifact flat (ENGINE-LIMITS M5.10).
    jdk_guard "$REPORT"
    echo "-- compile --"
    # NOTE the ANSI strip: without it `grep -cE '^-- .*Error'` matches nothing and reports 0 for a
    # port that does not compile — a false NEGATIVE on the headline number.
    #
    # No `--test` and no test directory: this port has ONE source set. `scala-cli compile` reports on
    # the MAIN scope whatever directories it is handed, so a test tree added here without `--test`
    # would have its errors read and not reported (CLAUDE.md §4.56).
    DECLARED=$(declared_dep_flags "$REPORT" | tr '\n' ' ')
    echo "declared coordinates on the compile line: ${DECLARED:-(none)}"
    DEPS="{{textra_deps}} $DECLARED"
    scala-cli compile --scala {{scala_version}} --server=false --jvm {{jdk_version}} $DEPS \
      {{gdx_module}}/src_managed/main/scala {{textra_module}}/src_managed/main/scala \
      2>&1 | sed 's/\x1b\[[0-9;]*m//g' > "$MEASURE_TMP"/textrameasure.txt
    CLI_STATUS=${PIPESTATUS[0]}
    ERRORS=$(grep -cE '^-- (\[E[0-9]+\] )?.*Error' "$MEASURE_TMP"/textrameasure.txt)
    compile_guard "$CLI_STATUS" "$ERRORS" "$MEASURE_TMP"/textrameasure.txt
    echo "TOTAL ERRORS: $ERRORS  (coded $(grep -cE '\[E[0-9]+\].*Error' "$MEASURE_TMP"/textrameasure.txt) + bare $(grep -cE '^-- Error:' "$MEASURE_TMP"/textrameasure.txt))"
    error_baseline_guard "$ERRORS" "$REPORT"
    grep -oE "\[E[0-9]+\][^:]*Error" "$MEASURE_TMP"/textrameasure.txt | sort | uniq -c | sort -rn | head
    echo "-- bare (uncoded) errors by message --"
    grep -A1 '^-- Error:' "$MEASURE_TMP"/textrameasure.txt | grep -vE '^-- Error:|^--$' | sed -E 's/^[0-9]+ \|//; s/[0-9]+//g' | sed -E 's/^ +//' | sort | uniq -c | sort -rn | head

    # Cross-platform compile gates — same deps as the JVM compile (textra_deps + declared).
    xplat_compile scala-js {{scala_version}} "$REPORT" textrameasure \
      {{gdx_module}}/src_managed/main/scala {{textra_module}}/src_managed/main/scala -- $DEPS
    xplat_compile scala-native {{scala_version}} "$REPORT" textrameasure \
      {{gdx_module}}/src_managed/main/scala {{textra_module}}/src_managed/main/scala -- $DEPS

    # Reference-flags compile (DESIGN.md §8.24): the reference build's own scalacOptions.
    flags_compile {{scala_version}} "$REPORT" textrameasure "{{sge_relaxed_flags}}" {{gdx_module}}/src_managed/main/scala {{textra_module}}/src_managed/main/scala -- $DEPS

    echo
    echo "-- correlation: every error located to its member and its Java origin --"
    # Run WHETHER OR NOT it compiled, for `noise4j-measure`'s reason: with no suite there is no
    # second thing to correlate, so the compile output is the only diagnostic this port has and it is
    # always worth attributing. BOTH ports' maps — an error inside TextraTypist resolves through its
    # own, and one that reaches the base resolves through libGDX's, which is what a dependent's wall
    # looks like.
    correlate "$REPORT/run-latest" --scalac "$MEASURE_TMP"/textrameasure.txt \
      --srcmap "$ROOT/port-report/LibgdxCoreMigrate/run-latest/srcmap.tsv" \
      --srcmap "$REPORT/run-latest/srcmap.tsv"

    headline "$ERRORS" "$REPORT"

# ---------------------------------------------------------------------------------------------
# TextraTypist's DIFFERENTIAL gate — the REFERENCE HAND PORT's own MUnit suite, run against the
# mechanically emitted `sge.textra.*`.
#
# WHY THIS LANE EXISTS, and it is a stronger reason than gdx-ai's. Upstream TextraTypist declares
# ZERO `@Test`: its `src/test/java` is 128 manual LWJGL3 demos, which `textra-measure` re-derives on
# every run. So before this lane the port had a compile and NO behavioural evidence at all
# (CLAUDE.md §3), and `textra-measure`'s own comment said so. The reference hand port
# (`../sge/sge-extension/textra`) wrote its own suite over the same library — 32 files, 239
# `test(…)` — and that suite is hand-written Scala, so a compiled port can be run against it with
# nothing translated. `ai-diff-measure` is the precedent and `PROGRESS.md` §10.8.17 is the census.
#
# WHAT IT IS NOT. These are NOT ported tests and are never counted as any (CLAUDE.md §3). Upstream
# ships no suite for this library, so there is no emitted-test figure to add them to — which makes
# the confusion cheaper to make here, not harder.
#
# THE CENSUS IS RE-DERIVED HERE, NOT ASSERTED, for `ai-diff-measure`'s reason: a hand port that
# gains a file, loses one or gains a `test(…)` makes §10.8.17 stale, and nothing else in this
# repository could say so — the adapted copies would keep passing at their own smaller number for as
# long as nobody looked (CLAUDE.md §4.56's instrument-silence rule). The 239 INCLUDES the five tests
# of `scalanative/TextraLzmaFontRedSuite.scala`, which is a byte-identical duplicate of the
# `scalajvm` file: the reference port compiles it twice for two platforms and the census counts what
# is there, since a population that quietly de-duplicates is one nobody can reproduce by counting.
# ---------------------------------------------------------------------------------------------
[doc("TextraTypist's DIFFERENTIAL gate — the hand port's own suite, run against the emitted port")]
textra-diff-measure:
    #!/usr/bin/env bash
    cd "{{root}}"
    ROOT="$(pwd)"
    export CORE_PROJECT="{{core_project}}"
    . scripts/_lib.sh

    REPORT="$ROOT/port-report/TextraTypistDifferential"
    TREE="{{textra_module}}/src/test/scala"

    # NO MIGRATION RUNS HERE, so there is no check report and no `show_check_report` — this lane's
    # subject is emitted code `textra-measure` produced and already checked, and re-printing its
    # counts here would be two readings of one artifact that can disagree. What this lane owns is the
    # compile of the hand-written half and the OUTCOMES.
    echo "-- census population: RE-DERIVED from the reference hand port, never asserted --"
    REF_FILES=$(find {{textra_ref_tests}} -name '*.scala' | wc -l | tr -d ' ')
    REF_TESTS=$(munit_emitted {{textra_ref_tests}})
    ADAPTED_FILES=$(find "$TREE" -name '*Suite.scala' | wc -l | tr -d ' ')
    ADAPTED_TESTS=$(munit_emitted "$TREE")
    echo "reference hand port ({{textra_ref_tests}}): $REF_FILES file(s), $REF_TESTS test(…)"
    echo "adapted here (class (a) of §10.8.17): $ADAPTED_FILES suite file(s), $ADAPTED_TESTS test(…)"
    # NOT "class (c)": §10.8.17 re-classified the residue after MEASURING what blocks it. 18 files /
    # 69 tests are class (b) — they construct the hand port's own nilary `Font()`, which java does
    # not have — one file / 5 tests is a byte-identical `scalanative` duplicate of a file that IS
    # adapted, and one declares no test at all (the reference fixture, rewritten rather than copied).
    echo "not copied, and counted: $((REF_FILES - ADAPTED_FILES)) file(s), $((REF_TESTS - ADAPTED_TESTS)) test(…)"
    if [ "$REF_FILES" != "32" ] || [ "$REF_TESTS" != "239" ]; then
      echo "!! THE REFERENCE SUITE MOVED — $REF_FILES files / $REF_TESTS tests, not 32 / 239."
      echo "   PROGRESS.md §10.8.17's census was taken against 32 / 239 and is now STALE: a file"
      echo "   added there is a file nobody has classified, and one removed may be one of the eleven"
      echo "   this lane copied. Re-run the census before trusting the outcomes below."
      exit 1
    fi

    echo
    # The JDK is an INPUT to this measurement: the frontend read its class files on ONE JVM and
    # the compile below runs on another. Nothing compared them until an `override` emitted on
    # JDK 24 failed a JDK-22 compile with every other artifact flat (ENGINE-LIMITS M5.10).
    jdk_guard "$ROOT/port-report/TextraTypistMigrate"
    echo "-- compile --"
    # `--test`: without it `scala-cli` READS the test directory and reports only the MAIN scope, so a
    # differential suite that does not compile measures 0 (CLAUDE.md §4.56's instrument-invocation
    # rule). This lane is also where `RefChecks` actually runs for the hand-written half, which is
    # why §10.8.15's census had to be re-taken at a zero-error compile (and §10.8.17 a third time,
    # against the RUN): a per-file typer count is a
    # FLOOR (CLAUDE.md §3), and on gdx-ai that difference moved four files and 16 tests.
    #
    # The regexodus coordinate is DERIVED from what `textra-measure`'s run published, never restated
    # here — a revision bumped in the manifest and not in the lane compiles against a DIFFERENT JAR
    # with every count flat.
    DECLARED=$(declared_dep_flags "$ROOT/port-report/TextraTypistMigrate" | tr '\n' ' ')
    echo "declared coordinates on the compile line: ${DECLARED:-(none)}"
    scala-cli compile --test --scala {{scala_version}} --server=false --jvm {{jdk_version}} {{textra_test_deps}} $DECLARED \
      {{gdx_module}}/src_managed/main/scala {{textra_module}}/src_managed/main/scala "$TREE" \
      2>&1 | sed 's/\x1b\[[0-9;]*m//g' > "$MEASURE_TMP"/textradiffmeasure.txt
    CLI_STATUS=${PIPESTATUS[0]}
    ERRORS=$(grep -cE '^-- (\[E[0-9]+\] )?.*Error' "$MEASURE_TMP"/textradiffmeasure.txt)
    compile_guard "$CLI_STATUS" "$ERRORS" "$MEASURE_TMP"/textradiffmeasure.txt
    echo "TOTAL ERRORS: $ERRORS  (coded $(grep -cE '\[E[0-9]+\].*Error' "$MEASURE_TMP"/textradiffmeasure.txt) + bare $(grep -cE '^-- Error:' "$MEASURE_TMP"/textradiffmeasure.txt))"
    error_baseline_guard "$ERRORS" "$REPORT"
    grep -oE "\[E[0-9]+\][^:]*Error" "$MEASURE_TMP"/textradiffmeasure.txt | sort | uniq -c | sort -rn | head

    if [ "$ERRORS" != "0" ]; then
      echo "(not running the suite: it does not compile — a test that cannot run is not a test that passed)"
      headline "$ERRORS" "$REPORT"
      exit 0
    fi

    echo
    echo "-- run --"
    scala-cli test --scala {{scala_version}} --server=false --jvm {{jdk_version}} {{textra_test_deps}} $DECLARED \
      -Duser.language=en -Duser.country=US \
      {{gdx_module}}/src_managed/main/scala {{textra_module}}/src_managed/main/scala "$TREE" \
      2>&1 | sed 's/\x1b\[[0-9;]*m//g' > "$MEASURE_TMP"/textradiffrun.txt
    reconcile_outcomes "$MEASURE_TMP"/textradiffrun.txt "$ADAPTED_TESTS"; RECONCILED=$?

    echo
    echo "-- correlation: test failures located to members and Java origins --"
    # TWO maps and no `test=` one: the suite is HAND-WRITTEN, so it has no source map and cannot have
    # one. That is the property this lane wants rather than a gap — a failure here anchors
    # `main-frame`, on the LIBRARY member that threw, which is the question a differential suite asks.
    correlate "$REPORT/run-latest" --tests "$MEASURE_TMP"/textradiffrun.txt \
      --srcmap "$ROOT/port-report/LibgdxCoreMigrate/run-latest/srcmap.tsv" \
      --srcmap "$ROOT/port-report/TextraTypistMigrate/run-latest/srcmap.tsv"
    test_outcome_guard "$REPORT/run-latest" "$RECONCILED" || exit 1

    headline "$ERRORS" "$REPORT"

# ---------------------------------------------------------------------------------------------
# VisUI's `ui/` module — a libGDX dependent, and the first port whose licence obligation is about
# something that is NOT CODE.
#
# ONE source set and no suite stage. Upstream's `ui/src/test` declares TWO real `@Test`, which is
# `gltf-measure`'s shape rather than `jbump-measure`'s — a suite exists and is nearly empty — so the
# lane re-derives that 2 and refuses to let it drift, while the port itself has no test source set
# yet. `PROGRESS.md` §10.9 holds both later waves (the two-test port, and the differential probe
# against the reference hand port's 72-case MUnit suite).
#
# The compile line carries NO coordinate of its own (`visui_deps` is empty and says why): libGDX
# arrives as the base's emitted SOURCE, and there is no third-party jar in this library at all.
# ---------------------------------------------------------------------------------------------
[doc("VisUI's ui/ module, compiled WITH libGDX core (a dependent port)")]
visui-measure:
    #!/usr/bin/env bash
    cd "{{root}}"
    ROOT="$(pwd)"
    export CORE_PROJECT="{{core_project}}"
    . scripts/_lib.sh

    # The finding ids are hashed from paths relative to this root (CLAUDE.md §4.6): set anywhere else
    # and every finding diffs as removed-and-re-added against a baseline whose counts are identical.
    write_run_props "$ROOT" "balticporter.reportPathRoot=$ROOT/{{visui_src}}"
    REPORT="$ROOT/port-report/VisUiMigrate"

    OUT=$({{sbt_migrate}} "{{corpus}}/runMain balticporter.corpus.visui.VisUiMigrate" 2>&1 | sed 's/\x1b\[[0-9;]*m//g')
    if ! grep -qE "wrote [0-9]+ Scala files" <<<"$OUT"; then
      echo "!! VisUiMigrate DID NOT RUN — refusing to measure stale output"
      grep -E "^\[error\].*\.scala:[0-9]+|^\[error\] +\|" <<<"$OUT" | head -20
      exit 1
    fi
    echo "-- VisUiMigrate (every line it printed) --"
    sed -n '/building model over/,/wrote [0-9]* Scala files/p' <<<"$OUT"
    echo

    echo "-- checks: persisted, untruncated, diffed against the baseline --"
    show_check_report "$REPORT"
    findings_baseline_guard "$REPORT"
    port_map_guard "$REPORT"

    echo
    echo "-- scope: ui/ ONLY, and usl/ is a NAMED follow-up rather than a silent drop --"
    # RE-DERIVED, not asserted in a comment. `ui/src/main/java` holds 164 `.java` files and two are
    # `package-info.java` — javadoc-only placeholders declaring no type — so 162 are in scope.
    ALL_JAVA=$(find {{visui_src}}/ui/src/main/java -name '*.java' | wc -l | tr -d ' ')
    PKG_INFO=$(find {{visui_src}}/ui/src/main/java -name 'package-info.java' | wc -l | tr -d ' ')
    USL_JAVA=$(find {{visui_src}}/usl/src/main/java -name '*.java' | wc -l | tr -d ' ')
    echo "ui/ .java: $ALL_JAVA   package-info: $PKG_INFO   in scope: $((ALL_JAVA - PKG_INFO))"
    echo "usl/ .java: $USL_JAVA  — OUT OF SCOPE for this port and stated as such (PROGRESS.md §10.9)"
    # The one-directional independence is what makes the deferral cost nothing, and it is a GREP
    # rather than a claim: `usl/` names no libGDX type, and `ui/` names no `com.kotcrab.vis.usl`
    # type. The coupling is build-time only — the root `build.gradle` runs an already-published USL
    # over `usl/styles/*.usl` and checks the compiled `uiskin.json` into `ui/`'s resources — so `ui/`
    # ships USL's OUTPUT and never calls its CODE. The day either direction gains a reference, this
    # is what says the deferral has stopped being free.
    UI_USES_USL=$(grep -rl "com\.kotcrab\.vis\.usl" {{visui_src}}/ui/src/main/java | wc -l | tr -d ' ')
    USL_USES_GDX=$(grep -rl "com\.badlogic\.gdx" {{visui_src}}/usl/src/main/java | wc -l | tr -d ' ')
    echo "ui/ files referencing usl: $UI_USES_USL   usl/ files referencing libGDX: $USL_USES_GDX"
    if [ "$UI_USES_USL" != "0" ] || [ "$USL_USES_GDX" != "0" ]; then
      echo "!! THE TWO MODULES ARE NO LONGER INDEPENDENT — the USL deferral was priced on that"
      echo "   independence (PROGRESS.md §10.9) and the price has changed."
    fi

    echo
    echo "-- licence obligations: TWO regimes, and the second is not about CODE --"
    # (a) is reproduced BY THE COMMENT HARVEST and (b) by `Provenance.notices`. §4.58's own argument
    # is why (a) is grepped on both sides rather than assumed: the harvest regressed to `Nil` once
    # and the emitted Scala was still valid. The `notices` half needs no grep to prove it was
    # DECLARED — a declared file that is not there is fatal at the run — but the COPY is asserted,
    # because §4.57's whole point is that naming a licence is not including it.
    #
    # THE DENOMINATOR IS THE SCOPE THIS RUN CONVERTS, NOT THE TREE — §4.56's rule read at a
    # denominator, and `textra-measure` is where it fired first. Both `package-info.java` DO carry
    # the Apache header and neither is EMITTED, so a count over the tree reports a two-file loss with
    # nothing to attribute it to: there is no emitted file those notices could head.
    UP_APACHE=$(grep -rl --exclude=package-info.java "Licensed under the Apache License" {{visui_src}}/ui/src/main/java | wc -l | tr -d ' ')
    APACHE=$(grep -rl "Licensed under the Apache License" {{visui_module}}/src_managed/main/scala | wc -l | tr -d ' ')
    EMITTED=$(find {{visui_module}}/src_managed/main/scala -name '*.scala' | wc -l | tr -d ' ')
    echo "(a) Apache-2.0 per-file notice: $UP_APACHE upstream file(s) in scope carry it, $APACHE of $EMITTED emitted file(s) reproduce it"
    echo "    the other $((EMITTED - APACHE)) emitted file(s) come from upstream files that carry NO per-file"
    echo "    notice — GdxAiPolicy's one-file case exactly, and the first reason (b)'s key exists"
    [ "$APACHE" = "0" ] && echo "!! THE PER-FILE HARVEST IS PRODUCING NOTHING — every emitted file is an unattributed derived work"
    [ "$APACHE" != "$UP_APACHE" ] && echo "!! THE TWO SIDES DISAGREE — $UP_APACHE upstream against $APACHE emitted. Either the harvest lost a notice or upstream's headers moved; §4.58 says only a text-to-text comparison can see this."
    for n in LICENSE NOTICE icons-license; do
      if [ -f "{{visui_module}}/src_managed/main/$n" ] || [ -f "{{visui_module}}/src_managed/$n" ]; then
        echo "(b) $n copied beside the emitted code"
      else
        echo "!! (b) $n WAS NOT COPIED — declared in Provenance.notices. ui/NOTICE states that the"
        echo "   shipped ICONS are CC BY-ND 3.0 and points at icons-license; Apache-2.0 §4(d) makes"
        echo "   carrying that NOTICE unconditional for a derivative, and no harvest can reach a"
        echo "   licence that lives on a PNG (§4.57)"
      fi
    done

    echo
    echo "-- the resources this port SHIPS: byte-for-byte against upstream --"
    # WAS a printed residue — "24 upstream, 9 paths named in emitted Scala, 0 shipped" — and is a
    # GATE now that the mechanism exists (`DESIGN.md` §8.22): `PortManifest.resources` declares the
    # files and the run copies them into `src_managed/main/resources`, unrenamed, because a classpath
    # lookup is a STRING LITERAL no rename may move (§4.56).
    #
    # The engine's own `resources` lane is in the check report above, one row per file, diffed
    # against the baseline. What THIS stage adds is the one question no check inside the run can
    # answer: are the SHIPPED BYTES upstream's? That is §4.58's text-to-text rule read at a resource —
    # a copy that re-encoded a `.png`, normalised a `.properties` or rewrote a path inside a skin
    # would produce the same count, the same emitted Scala and the same member digests.
    UP_RES=$(find {{visui_src}}/ui/src/main/resources -type f | wc -l | tr -d ' ')
    RES_DIR="{{visui_module}}/src_managed/main/resources"
    SHIPPED=$(find "$RES_DIR" -type f 2>/dev/null | wc -l | tr -d ' ')
    echo "upstream resources: $UP_RES   shipped by this run: $SHIPPED"
    if [ "$SHIPPED" != "22" ]; then
      echo "!! THIS PORT SHIPS $SHIPPED RESOURCES, NOT 22 — the emitted code names paths a consumer's"
      echo "   build has to supply, and a missing one fails at first use with no compile error, no"
      echo "   check count and no member digest. Re-read \`resources\` in VisUiMigrate.scala."
      exit 1
    fi
    DIFFER=0
    for f in $(find "$RES_DIR" -type f | sort); do
      REL="${f#$RES_DIR/}"
      if ! cmp -s "$f" "{{visui_src}}/ui/src/main/resources/$REL"; then
        echo "!! NOT VERBATIM: $REL differs from the upstream file it was copied from"
        DIFFER=$((DIFFER + 1))
      fi
    done
    echo "byte-identical to upstream: $((SHIPPED - DIFFER)) of $SHIPPED"
    [ "$DIFFER" != "0" ] && exit 1
    # …and the TWO the port deliberately does not ship, asserted so the decision cannot rot into an
    # oversight. Both belong to the UPSTREAM BUILD rather than to the library, and the GWT module is
    # FQNs and java paths — the REWRITE shape, naming a namespace this port renames.
    for b in "com/kotcrab/vis/vis-ui.gwt.xml" "META-INF/robovm/ios/robovm.xml"; do
      if [ -f "$RES_DIR/$b" ]; then
        echo "!! $b IS SHIPPED, and it is the upstream BUILD's file rather than this library's."
        echo "   PROGRESS.md §10.9.3 and DESIGN.md §8.22 say why a DECLARATION and not a scan."
        exit 1
      fi
    done
    echo "the 2 upstream-build files under that root are correctly NOT shipped (24 - 22)"

    echo
    echo "-- test discovery --"
    # UPSTREAM'S OWN SMALL NUMBER, re-derived rather than quoted — `gltf-measure`'s stage. A suite
    # that exists and is nearly empty is worse than none, because a filename census over that tree
    # reports 30 files and a test census reports 2, and only the second is a fact about behaviour.
    UI_TESTS=$(java_test_count {{visui_src}}/ui/src/test)
    UI_TEST_FILES=$(find {{visui_src}}/ui/src/test -name '*.java' | wc -l | tr -d ' ')
    MANUAL=$(find {{visui_src}}/ui/src/test -name '*.java' -path '*/test/manual/*' | wc -l | tr -d ' ')
    USL_TESTS=$(java_test_count {{visui_src}}/usl/src/test)
    echo "@Test in ui/src/test ($UI_TEST_FILES files): $UI_TESTS"
    echo "@Test in usl/src/test (OUT OF SCOPE): $USL_TESTS"
    if [ "$UI_TESTS" = "2" ]; then
      echo "   TWO real tests upstream, both validator unit tests. The other $((UI_TEST_FILES - 2)) files are"
      echo "   \"extends VisWindow\" demos needing a GL context: $MANUAL under test.manual, which"
      echo "   ui/build.gradle excludes by name, and $((UI_TEST_FILES - 2 - MANUAL)) OUTSIDE it that the include glob"
      echo "   \"**/*Test.**\" still matches and that declares zero @Test. This port has NO test source set yet, so it has NO"
      echo "   behavioural evidence at all (CLAUDE.md §3) and every §4.4 form in it is UNMEASURED."
      echo "   PROGRESS.md §10.9 scopes both waves that would change that."
    else
      echo "!! THE UPSTREAM TEST COUNT HAS MOVED — was 2. Re-read PROGRESS.md §10.9's test plan."
    fi
    echo "emitted test files: 0 (this port has no test source set)"

    echo
    break_residue {{visui_module}}/src_managed

    # The JDK is an INPUT to this measurement: the frontend read its class files on ONE JVM and
    # the compile below runs on another. Nothing compared them until an `override` emitted on
    # JDK 24 failed a JDK-22 compile with every other artifact flat (ENGINE-LIMITS M5.10).
    jdk_guard "$REPORT"
    echo "-- compile --"
    # NOTE the ANSI strip: without it `grep -cE '^-- .*Error'` matches nothing and reports 0 for a
    # port that does not compile — a false NEGATIVE on the headline number.
    #
    # No `--test` and no test directory: this port has ONE source set. `scala-cli compile` reports on
    # the MAIN scope whatever directories it is handed, so a test tree added here without `--test`
    # would have its errors read and not reported (CLAUDE.md §4.56).
    DECLARED=$(declared_dep_flags "$REPORT" | tr '\n' ' ')
    echo "declared coordinates on the compile line: ${DECLARED:-(none)}"
    DEPS="{{visui_deps}} $DECLARED"
    scala-cli compile --scala {{scala_version}} --server=false --jvm {{jdk_version}} $DEPS \
      {{gdx_module}}/src_managed/main/scala {{visui_module}}/src_managed/main/scala \
      2>&1 | sed 's/\x1b\[[0-9;]*m//g' > "$MEASURE_TMP"/visuimeasure.txt
    CLI_STATUS=${PIPESTATUS[0]}
    ERRORS=$(grep -cE '^-- (\[E[0-9]+\] )?.*Error' "$MEASURE_TMP"/visuimeasure.txt)
    compile_guard "$CLI_STATUS" "$ERRORS" "$MEASURE_TMP"/visuimeasure.txt
    echo "TOTAL ERRORS: $ERRORS  (coded $(grep -cE '\[E[0-9]+\].*Error' "$MEASURE_TMP"/visuimeasure.txt) + bare $(grep -cE '^-- Error:' "$MEASURE_TMP"/visuimeasure.txt))"
    error_baseline_guard "$ERRORS" "$REPORT"
    grep -oE "\[E[0-9]+\][^:]*Error" "$MEASURE_TMP"/visuimeasure.txt | sort | uniq -c | sort -rn | head
    echo "-- bare (uncoded) errors by message --"
    grep -A1 '^-- Error:' "$MEASURE_TMP"/visuimeasure.txt | grep -vE '^-- Error:|^--$' | sed -E 's/^[0-9]+ \|//; s/[0-9]+//g' | sed -E 's/^ +//' | sort | uniq -c | sort -rn | head

    # Cross-platform compile gates — same deps as the JVM compile (visui_deps + declared).
    xplat_compile scala-js {{scala_version}} "$REPORT" visuimeasure \
      {{gdx_module}}/src_managed/main/scala {{visui_module}}/src_managed/main/scala -- $DEPS
    xplat_compile scala-native {{scala_version}} "$REPORT" visuimeasure \
      {{gdx_module}}/src_managed/main/scala {{visui_module}}/src_managed/main/scala -- $DEPS

    # Reference-flags compile (DESIGN.md §8.24): the reference build's own scalacOptions.
    flags_compile {{scala_version}} "$REPORT" visuimeasure "{{sge_relaxed_flags}}" {{gdx_module}}/src_managed/main/scala {{visui_module}}/src_managed/main/scala -- $DEPS

    echo
    echo "-- correlation: every error located to its member and its Java origin --"
    # Run WHETHER OR NOT it compiled, for `noise4j-measure`'s reason: with no suite there is no
    # second thing to correlate, so the compile output is the only diagnostic this port has and it is
    # always worth attributing. BOTH ports' maps — an error inside VisUI resolves through its own,
    # and one that reaches the base resolves through libGDX's, which is what a dependent's wall
    # looks like.
    correlate "$REPORT/run-latest" --scalac "$MEASURE_TMP"/visuimeasure.txt \
      --srcmap "$ROOT/port-report/LibgdxCoreMigrate/run-latest/srcmap.tsv" \
      --srcmap "$REPORT/run-latest/srcmap.tsv"

    headline "$ERRORS" "$REPORT"

# ---------------------------------------------------------------------------------------------
# VisUI's DIFFERENTIAL gate — the REFERENCE HAND PORT's own MUnit suite, run against the
# mechanically emitted `sge.visui.*`.
#
# WHY THIS LANE EXISTS. Upstream VisUI declares TWO real `@Test` over 162 files (`visui-measure`
# re-derives that 2 on every run), so before this lane the port had a compile and NO behavioural
# evidence at all — the state `CLAUDE.md` §3 says proves nothing, held over a library whose §4.4
# surface is a widget toolkit. The reference hand port (`../sge/sge-extension/visui`) wrote 72
# MUnit cases over the same library, an 8x richer suite, and it is hand-written Scala, so a compiled
# port can be run against it with nothing translated. `ai-diff-measure` and `textra-diff-measure`
# are the precedents; `PROGRESS.md` §10.9.12 is the census.
#
# WHAT IT IS NOT. These are NOT ported tests and are never counted as any (CLAUDE.md §3). Upstream
# ships two `@Test` for this library and this lane touches neither of them.
#
# AND THE COMPILE IS SCOPED, WHICH NO EARLIER DIFFERENTIAL LANE HAD TO BE. gdx-ai and TextraTypist
# were both at ZERO when their gates were built; this port stands at its attributed 8-error floor
# (`PROGRESS.md` §10.9.10 — 3 C3 refusals, 3 upstream version skew, 1 K13-base, 1 unsuppliable-use)
# and nothing on that list is close to moving. §3's rule then bites twice over: with any typer error
# outstanding `RefChecks` never runs, so the census's second pass would be impossible, and scalac
# reaching no backend phase writes no class file, so NOTHING could be run at all. So the compile
# carries `visui_closure` — the five emitted files the adapted suites transitively name — instead of
# the whole tree, and the lane says so in its own output rather than letting `0 errors` be read as
# `the port compiles`. The claim that keeps this honest is CHECKED and not asserted: not one of the
# five is a file the run reported an error in.
# ---------------------------------------------------------------------------------------------
[doc("VisUI's DIFFERENTIAL gate — the hand port's own suite, run against the emitted port")]
visui-diff-measure:
    #!/usr/bin/env bash
    cd "{{root}}"
    ROOT="$(pwd)"
    export CORE_PROJECT="{{corpus}}"
    . scripts/_lib.sh

    REPORT="$ROOT/port-report/VisUiDifferential"
    TREE="{{visui_module}}/src/test/scala"
    EMIT="{{visui_module}}/src_managed/main/scala/sge/visui"

    # NO MIGRATION RUNS HERE, so there is no check report and no `show_check_report` — this lane's
    # subject is emitted code `visui-measure` produced and already checked, and re-printing its
    # counts here would be two readings of one artifact that can disagree. What this lane owns is
    # the compile of the hand-written half and the OUTCOMES.
    echo "-- census population: RE-DERIVED from the reference hand port, never asserted --"
    REF_FILES=$(find {{visui_ref_tests}} -name '*.scala' | wc -l | tr -d ' ')
    REF_TESTS=$(munit_emitted {{visui_ref_tests}})
    ADAPTED_FILES=$(find "$TREE" -name '*Suite.scala' | wc -l | tr -d ' ')
    ADAPTED_TESTS=$(munit_emitted "$TREE")
    echo "reference hand port ({{visui_ref_tests}}): $REF_FILES file(s), $REF_TESTS test(…)"
    echo "adapted here (class (a) of §10.9.12): $ADAPTED_FILES suite file(s), $ADAPTED_TESTS test(…)"
    # NOT "class (c)": §10.9.12 classified the residue after MEASURING what blocks it. 7 files / 22
    # tests are class (b) — every one needs `VisUITestFixture.headlessSge()`, which rests on types
    # this port does not emit, and behind it the skin resources §10.9.3 counts as NAMED-but-unshipped
    # — and one file declares no test at all (that fixture).
    echo "not copied, and counted: $((REF_FILES - ADAPTED_FILES)) file(s), $((REF_TESTS - ADAPTED_TESTS)) test(…)"
    if [ "$REF_FILES" != "12" ] || [ "$REF_TESTS" != "72" ]; then
      echo "!! THE REFERENCE SUITE MOVED — $REF_FILES files / $REF_TESTS tests, not 12 / 72."
      echo "   PROGRESS.md §10.9.12's census was taken against 12 / 72 and is now STALE: a file"
      echo "   added there is a file nobody has classified, and one removed may be one of the four"
      echo "   this lane copied. Re-run the census before trusting the outcomes below."
      exit 1
    fi

    echo
    # The JDK is an INPUT to this measurement: the frontend read its class files on ONE JVM and
    # the compile below runs on another. Nothing compared them until an `override` emitted on
    # JDK 24 failed a JDK-22 compile with every other artifact flat (ENGINE-LIMITS M5.10).
    jdk_guard "$ROOT/port-report/VisUiMigrate"
    echo "-- compile scope: the CLOSURE, because this port is not at zero --"
    # The whole-tree compile is `visui-measure`'s and its floor is that lane's baseline; what this
    # one has to establish is that the five files under test are not among the erroring ones, which
    # is the entire warrant for compiling a subset. Read off the run's OWN artifact rather than a
    # list repeated here — a second copy of the eight is a second thing to keep in step.
    ERRTSV="$ROOT/port-report/VisUiMigrate/run-latest/errors.tsv"
    if [ ! -f "$ERRTSV" ]; then
      echo "!! no $ERRTSV — run \`just visui-measure\` first; this lane cannot verify its own scope."
      exit 1
    fi
    CLOSURE_ARGS=""
    OVERLAP=""
    for f in {{visui_closure}}; do
      if [ ! -f "$EMIT/$f" ]; then
        echo "!! closure file $EMIT/$f is not emitted — the port moved and \`visui_closure\` is stale."
        exit 1
      fi
      CLOSURE_ARGS="$CLOSURE_ARGS $EMIT/$f"
      if cut -f2 "$ERRTSV" | grep -q "/sge/visui/$f\$"; then OVERLAP="$OVERLAP $f"; fi
    done
    echo "closure: $(echo {{visui_closure}} | wc -w | tr -d ' ') emitted file(s) of $(find $EMIT -name '*.scala' | wc -l | tr -d ' ')"
    if [ -n "$OVERLAP" ]; then
      echo "!! A CLOSURE FILE IS ONE THE PORT CANNOT COMPILE:$OVERLAP"
      echo "   The subset compile below would be measuring a file the whole-port run reports an"
      echo "   error in, which is the one thing scoping the compile must never hide. Classify those"
      echo "   suites (c)-by-the-floor in PROGRESS.md §10.9.12 and drop them from this lane."
      exit 1
    fi
    echo "none of the closure files appears in errors.tsv — the port's 8 are all in widget/ and layout/"

    echo
    echo "-- compile --"
    # `--test`: without it `scala-cli` READS the test directory and reports only the MAIN scope, so
    # a differential suite that does not compile measures 0 (CLAUDE.md §4.56's instrument-invocation
    # rule). This is also where `RefChecks` runs for the hand-written half, which is why §10.9.12's
    # census had to be taken twice: a per-file typer count is a FLOOR (CLAUDE.md §3), and on gdx-ai
    # that difference moved four files and 16 tests in the dangerous direction.
    DECLARED=$(declared_dep_flags "$ROOT/port-report/VisUiMigrate" | tr '\n' ' ')
    echo "declared coordinates on the compile line: ${DECLARED:-(none)}"
    scala-cli compile --test --scala {{scala_version}} --server=false --jvm {{jdk_version}} {{visui_test_deps}} $DECLARED \
      {{gdx_module}}/src_managed/main/scala $CLOSURE_ARGS "$TREE" \
      2>&1 | sed 's/\x1b\[[0-9;]*m//g' > "$MEASURE_TMP"/visuidiffmeasure.txt
    CLI_STATUS=${PIPESTATUS[0]}
    ERRORS=$(grep -cE '^-- (\[E[0-9]+\] )?.*Error' "$MEASURE_TMP"/visuidiffmeasure.txt)
    compile_guard "$CLI_STATUS" "$ERRORS" "$MEASURE_TMP"/visuidiffmeasure.txt
    echo "TOTAL ERRORS: $ERRORS  (coded $(grep -cE '\[E[0-9]+\].*Error' "$MEASURE_TMP"/visuidiffmeasure.txt) + bare $(grep -cE '^-- Error:' "$MEASURE_TMP"/visuidiffmeasure.txt))"
    error_baseline_guard "$ERRORS" "$REPORT"
    grep -oE "\[E[0-9]+\][^:]*Error" "$MEASURE_TMP"/visuidiffmeasure.txt | sort | uniq -c | sort -rn | head

    if [ "$ERRORS" != "0" ]; then
      echo "(not running the suite: it does not compile — a test that cannot run is not a test that passed)"
      headline "$ERRORS" "$REPORT"
      exit 0
    fi

    echo
    echo "-- run --"
    scala-cli test --scala {{scala_version}} --server=false --jvm {{jdk_version}} {{visui_test_deps}} $DECLARED \
      -Duser.language=en -Duser.country=US \
      {{gdx_module}}/src_managed/main/scala $CLOSURE_ARGS "$TREE" \
      2>&1 | sed 's/\x1b\[[0-9;]*m//g' > "$MEASURE_TMP"/visuidiffrun.txt
    reconcile_outcomes "$MEASURE_TMP"/visuidiffrun.txt "$ADAPTED_TESTS"; RECONCILED=$?

    echo
    echo "-- correlation: test failures located to members and Java origins --"
    # TWO maps and no `test=` one: the suite is HAND-WRITTEN, so it has no source map and cannot
    # have one. That is the property this lane wants rather than a gap — a failure here anchors
    # `main-frame`, on the LIBRARY member that threw, which is what a differential suite asks.
    correlate "$REPORT/run-latest" --tests "$MEASURE_TMP"/visuidiffrun.txt \
      --srcmap "$ROOT/port-report/LibgdxCoreMigrate/run-latest/srcmap.tsv" \
      --srcmap "$ROOT/port-report/VisUiMigrate/run-latest/srcmap.tsv"
    test_outcome_guard "$REPORT/run-latest" "$RECONCILED" || exit 1

    headline "$ERRORS" "$REPORT"

# ---------------------------------------------------------------------------------------------
# Every lane, SERIALLY, in dependency order — never in parallel.
#
# Each lane re-emits into `src_managed/`, so `gdx-test-measure` and `ashley-measure` compile against
# what `gdx-measure` just wrote. Run concurrently they would measure each other's half-written
# output; run out of order they would measure the previous engine's. A lane that fails stops the
# sequence, for the same reason every lane aborts on a migration that did not run: the next number
# would be stale, and a stale number reads exactly like a result.
# ---------------------------------------------------------------------------------------------
[doc("every lane, SERIALLY, in dependency order — never in parallel")]
measure-all:
    #!/usr/bin/env bash
    cd "{{root}}"
    # The two ssg-md lanes are LAST and nothing downstream compiles against what they wrote — ssg-md
    # is a standalone base port at milestone 1 — so a lane that stops the sequence there costs no
    # other lane its number. That is a property of THIS port's position and not a general licence:
    # the ordering is a dependency order and stays one, which is exactly why `md-test-measure`
    # follows `md-measure` and not the other way round. The test port is a DEPENDENT (`test.conf`
    # has `base = "main.conf"`) and its compile links against the main source set's `src_managed/`,
    # so run first it would measure the PREVIOUS engine's emit of the library it tests. `md-ext-measure`
    # is a dependent of the same base for the same reason and follows both.
    # `ai-measure` is APPENDED rather than slotted in beside the other libGDX dependents. Its only
    # ordering constraint is `gdx-measure`, which is first, so any position after that one is
    # correct — and last is the position that leaves the fourteen established lanes' order, and
    # therefore their numbers, untouched by this port's arrival.
    # `ai-test-measure` follows `ai-measure` for `md-test-measure`'s reason: it is a DEPENDENT of
    # that source set and its compile links against what `ai-measure` just wrote, so run first it
    # would measure the previous engine's emit of the library it tests.
    # `ai-diff-measure` is LAST and follows `ai-measure` for `ai-test-measure`'s reason: it compiles
    # the hand-written differential suite against what `ai-measure` just wrote, so run earlier it
    # would measure the previous engine's emit of the library it probes. It follows
    # `ai-test-measure` rather than preceding it because the ported suite is the port's own gate and
    # this one is a gate on top of it — a differential failure is only worth reading once the
    # library's own ten tests have been run.
    # `textra-measure` is next-to-LAST, for the reason `ai-measure` was APPENDED rather than slotted
    # in: its only ordering constraint is `gdx-measure`, which is first, so any position after that
    # one is correct — and the end is the position that leaves the seventeen established lanes'
    # order, and therefore their numbers, untouched by this port's arrival.
    # `textra-diff-measure` is LAST and follows `textra-measure` for `ai-diff-measure`'s reason: it
    # compiles the hand-written differential suite against what `textra-measure` just wrote, so run
    # earlier it would measure the previous engine's emit of the library it probes. Upstream ships no
    # suite for this library, so unlike gdx-ai there is no ported gate for it to sit on top of — this
    # IS the port's behavioural gate, and it is the only one it has.
    # `visui-measure` is next-to-LAST, for the reason `textra-measure` was appended next-to-last:
    # its only ordering constraint is `gdx-measure`, which is first, so any position after that one
    # is correct — and the end is the position that leaves the nineteen established lanes' order,
    # and therefore their numbers, untouched by this port's arrival.
    # `visui-diff-measure` is LAST and follows `visui-measure`, for `textra-diff-measure`'s reason
    # and one of its own: it compiles the hand-written differential suite against what
    # `visui-measure` just wrote, AND it reads that run's `errors.tsv` to verify that no file in its
    # scoped compile is one the port cannot compile. Run earlier it would check this engine's suite
    # against the previous engine's emit and the previous engine's error list.
    # The two `usl-*` lanes are LAST and their only ordering constraint is between THEMSELVES: USL is
    # a STANDALONE port with no base and no resolution roots, so nothing it emits is read by another
    # lane and nothing another lane emits is read by it. Last is therefore the position that leaves
    # the twenty-one established lanes' order — and their numbers — untouched by this port's arrival,
    # which is `ai-measure`'s own reason for being appended rather than slotted in.
    # `usl-test-measure` follows `usl-measure` for `md-test-measure`'s reason: it is a DEPENDENT of
    # that source set (`test.conf` has `base = "main.conf"`) and its compile links against the main
    # `src_managed/`, so run first it would measure the previous engine's emit of the library it
    # tests. It re-emits BOTH source sets, which is what keeps the pair honest either way round.
    for lane in gdx-measure gdx-test-measure ashley-measure anim8-measure gltf-measure vfx-measure sg-measure noise4j-measure jbump-measure screens-measure liqp-measure md-measure md-test-measure md-ext-measure ai-measure ai-test-measure ai-diff-measure textra-measure textra-diff-measure visui-measure visui-diff-measure usl-measure usl-test-measure; do
      echo
      echo "################################################################## just $lane"
      if ! {{just_executable()}} "$lane"; then
        echo "!! $lane FAILED — stopping; the remaining lanes would compile against a stale emit"
        exit 1
      fi
    done

# ---------------------------------------------------------------------------------------------
# The difference catalog, rendered.
#
# The registry (`balticporter.catalog`) is the truth and this is a VIEW of it, written to
# `.balticporter/`, which is gitignored. That is CLAUDE.md §5.5's rule for emitted code applied one
# medium over: committed, the markdown would be a seventh document nobody loads (§3.6), it would
# accrete a status section, and it would start disagreeing with the code it was generated from.
#
# `catalog-twins` is the other half and is the one an agent runs while EDITING a status: it derives
# CLOSED / OPEN / AMBIGUOUS / UNMARKED per ENGINE-LIMITS entry, and — given a `<id> <status> <twin>`
# table — fails on a row that claims OPEN against a CLOSED twin. `ClosedTwinStatusSpec` is the same
# rule inside the test run; both read the same file by the same shape, so they cannot disagree.
# ---------------------------------------------------------------------------------------------
[doc("render balticporter.catalog to .balticporter/catalog.md (a build product)")]
catalog:
    #!/usr/bin/env bash
    cd "{{root}}"
    mkdir -p .balticporter
    sbt -batch "api/runMain balticporter.catalog.CatalogDoc" | sed -n '/^# The difference catalog/,$p' > .balticporter/catalog.md
    echo "-> .balticporter/catalog.md ($(wc -l < .balticporter/catalog.md) lines)"

[doc("ENGINE-LIMITS CLOSED/OPEN per entry; with a rows.tsv, fail on a stale OPEN status")]
catalog-twins rows="":
    #!/usr/bin/env bash
    cd "{{root}}"
    scripts/catalog-status.sh {{rows}}

# ---------------------------------------------------------------------------------------------
# Per-port `decisions.tsv` row counts by KIND, for every port that has a run.
#
# The decision log is NOT baselined (`baseline-accept` promotes findings/counts/members/tests/
# port-map and deliberately not this one), so nothing else in the workflow states its size. A
# provenance artifact whose row count nobody prints is one nobody notices going empty.
# ---------------------------------------------------------------------------------------------
[doc("decisions.tsv row counts by kind, every port")]
decision-counts:
    #!/usr/bin/env bash
    cd "{{root}}"
    for d in port-report/*/; do
      f="$d/run-latest/decisions.tsv"
      [ -f "$f" ] || continue
      n=$(( $(grep -vc '^#' "$f") ))
      echo "$(basename "$d"): $n row(s)"
      grep -v '^#' "$f" | cut -f1 | sort | uniq -c | sed 's/^/    /'
    done

# ---------------------------------------------------------------------------------------------
# THE DIFFERENCE CATALOG'S CORPUS-WIDE COVERAGE — `catalog.tsv`, aggregated across every port.
#
# The per-port lane cannot answer the question that matters. A row unreached on jbump (2,000 lines,
# no suite) is normal; a row unreached on ALL of them is a branch nothing in the corpus exercises,
# which is either dead code or an untested rule — and CLAUDE.md §3's whole argument is that those
# are the same thing until a test says otherwise. So `catalog(unreached)` is per-port informational
# with a baseline, and THIS is the recipe an agent runs before claiming a rule is live.
#
# The same shape as `decision-counts` and for the same reason: the artifact is written by every run
# and nothing else in the workflow states its corpus-wide total.
# ---------------------------------------------------------------------------------------------
[doc("catalog.tsv aggregated over every port — which difference rows the whole corpus never reaches")]
catalog-coverage:
    #!/usr/bin/env bash
    cd "{{root}}"
    files=$(ls port-report/*/run-latest/catalog.tsv 2>/dev/null)
    if [ -z "$files" ]; then
      echo "!! no port has written a catalog.tsv — run a measure lane first"
      exit 1
    fi
    echo "== per port =="
    for f in $files; do
      port=$(basename "$(dirname "$(dirname "$f")")")
      reached=$(awk -F'\t' '!/^#/ && ($4>0 || $6>0)' "$f" | wc -l | tr -d ' ')
      # …and `rendering:` beside them. THE FILTER IS THE ONE THING HERE THAT CAN GO STALE SILENTLY:
      # written when there were two discharge surfaces it kept answering for two after a third was
      # built, so twenty area-S rows were neither counted as mechanised nor eligible to be REPORTED
      # as never-reached — and this recipe is the one an agent runs before claiming a rule is live.
      # It is CLAUDE.md §4.56's fast-path-guard rule in a shell script: a guard is derived from ALL
      # of a mechanism's targets or it is not written. Anything not `unmechanised`/`none` is a
      # surface, which is why the test is now stated that way round.
      mech=$(awk -F'\t' '!/^#/ && $3!="unmechanised" && $3!="none"' "$f" | wc -l | tr -d ' ')
      unmech=$(awk -F'\t' '!/^#/ && $3=="unmechanised"' "$f" | wc -l | tr -d ' ')
      none=$(awk -F'\t' '!/^#/ && $3=="none"' "$f" | wc -l | tr -d ' ')
      echo "$port: $reached reached / $mech mechanised, $unmech unmechanised, $none owe nothing"
    done
    echo
    echo "== NEVER REACHED BY ANY PORT (mechanised rows only — the ones a lane may claim about) =="
    # union of reached ids over every port, subtracted from the mechanised set of any one of them.
    # `unmechanised` rows are excluded by construction: a row whose discharge surface does not exist
    # cannot be "unreached by the corpus", only unmeasured, and the two must never share a number.
    reached=$(mktemp); mech=$(mktemp)
    for f in $files; do
      awk -F'\t' '!/^#/ && ($4>0 || $6>0) {print $1}' "$f" >> "$reached"
      awk -F'\t' '!/^#/ && $3!="unmechanised" && $3!="none" {print $1"\t"$2"\t"$3}' "$f" >> "$mech"
    done
    sort -u "$reached" -o "$reached"; sort -u "$mech" -o "$mech"
    join -v1 -t$'\t' "$mech" "$reached" | sed 's/^/  /'
    n=$(join -v1 -t$'\t' "$mech" "$reached" | wc -l | tr -d ' ')
    echo "  -> $n mechanised row(s) that NO port in the corpus reaches"
    rm -f "$reached" "$mech"

# ---------------------------------------------------------------------------------------------
# `members.tsv` against its committed baseline, for every port with a run (or just PORT).
#
# The member digest is the BLAST RADIUS and it is available before a compile (CLAUDE.md §5.1):
# identical files mean the emitted text is byte-for-byte unchanged, which is a stronger revert
# check than any count — no check count moves for most transform regressions. Exits non-zero if
# anything moved, so it is usable as a gate.
#
# A MISSING INPUT IS FATAL, and this is the half that was wrong: both sides were required with
# `[ -f "$b" ] && [ -f "$r" ] || continue`, so a port named on the command line whose baseline (or
# whose run) did not exist printed NOTHING and exited 0 — the §5.1 rule ("a `--tests` path that
# does not exist is fatal, never a headline of 0 passing, 0 failing") failing inside the one gate
# that is supposed to catch what no count moves. In a fresh checkout `run-latest/` is gitignored,
# so `just members-unchanged SimpleGraphsMigrate` reported a clean blast radius for a comparison
# that never happened.
#
# The two modes differ deliberately, and only in what "considered" means:
#
#   * a port NAMED on the command line is an assertion about that port — every missing side is
#     fatal, and an unknown name lists the ports there are;
#   * the SWEEP considers every port directory. One that has run and has no baseline is fatal
#     wherever it appears (an artifact exists and nothing checks it — the false green). One that
#     has a baseline and has not run in this checkout is printed as NOT COMPARED, because a lane
#     nobody has run here is not a regression; the sweep fails only if that describes them ALL,
#     which is the "printed nothing, exited 0" state this recipe exists to refuse.
# ---------------------------------------------------------------------------------------------
[doc("members.tsv against its committed baseline (all ports, or one)")]
members-unchanged port="":
    #!/usr/bin/env bash
    cd "{{root}}"
    R="{{report_root}}"
    if [ ! -d "$R" ]; then
      echo "!! no report directory at $R — nothing has been measured in this checkout"
      exit 1
    fi
    known=$(ls -1 "$R" 2>/dev/null | tr '\n' ' ')
    if [ -n "{{port}}" ] && [ ! -d "$R/{{port}}" ]; then
      echo "!! NO SUCH PORT: {{port}}"
      echo "   ports with a report directory: $known"
      exit 1
    fi
    rc=0; compared=0
    for d in "$R"/*/; do
      p="$(basename "$d")"
      [ -z "{{port}}" ] || [ "$p" = "{{port}}" ] || continue
      b="$d/baseline/members.tsv"; r="$d/run-latest/members.tsv"
      if [ -f "$b" ] && [ -f "$r" ]; then
        n=$(diff "$b" "$r" | grep -c '^[<>]')
        echo "$p: $n member(s) changed"
        compared=$((compared + 1))
        [ "$n" = "0" ] || rc=1
      elif [ -f "$r" ]; then
        echo "!! $p: MEASURED BUT UNBASELINED — run-latest/members.tsv exists, baseline/members.tsv does not."
        echo "   Nothing is comparing this port's emitted text. Accept one: just baseline-accept $p"
        rc=1
      elif [ -f "$b" ]; then
        echo "   $p: NOT COMPARED — no run-latest/members.tsv (its lane has not run in this checkout)"
        [ -z "{{port}}" ] || { echo "!! and you asked about $p specifically — run its measure lane first"; rc=1; }
      else
        echo "   $p: NOT COMPARED — neither a baseline nor a run"
        [ -z "{{port}}" ] || { echo "!! and you asked about $p specifically"; rc=1; }
      fi
    done
    if [ "$compared" = "0" ] && [ "$rc" = "0" ]; then
      echo "!! NOTHING WAS COMPARED — a blast radius nobody computed is not a blast radius of zero"
      rc=1
    fi
    exit $rc

# ---------------------------------------------------------------------------------------------
# THE DEBUGGING SURFACE — CLAUDE.md §4.6, reachable.
#
# Every tool below already existed; what did not exist was a way to reach it without knowing which
# main class or which hand-written file to create. That is a real cost and not a convenience: the
# consumer of this engine is an agent in ANOTHER repository (§4.45) with none of this session's
# folklore, and a diagnostic it cannot find is a diagnostic it re-invents — by copying
# `src_managed`, adding a `println` and rebuilding, which is exactly what these replaced.
#
# The layering is the §4.6 one and these recipes do not reimplement it: `debug-flags` renders
# `DebugFlags.resolution`, the same fold `DebugFlags.get` performs, so the explanation and the
# behaviour cannot drift apart. `debug-set`/`debug-clear` only edit the file the operator owns.
# ---------------------------------------------------------------------------------------------
[doc("WHICH layer defines each balticporter.* flag right now (and what PORT's last run saw)")]
debug-flags PORT="":
    #!/usr/bin/env bash
    cd "{{root}}"
    mkdir -p .balticporter/tmp
    CAP=".balticporter/tmp/debug-flags-$$.txt"
    ARGS="--root {{bp_root}}"
    [ -n "{{PORT}}" ] && ARGS="$ARGS --port {{PORT}}"
    {{sbt_migrate}} "{{core_project}}/runMain balticporter.tir.DebugFlagsMain $ARGS" 2>&1 |
      sed $'s/\033\\[[0-9;]*[a-zA-Z]//g' > "$CAP"
    st=${PIPESTATUS[0]}
    # from the report's own first line, so sbt's preamble is dropped without dropping the report:
    # every line of it that matters starts with `[balticporter]` or two spaces, and a blanket
    # `grep -v '^\['` would eat the headline itself.
    sed -n '/flag resolution under/,$p' "$CAP" | grep -vE '^\[(info|warn|error|success)\]'
    if [ "$st" != "0" ]; then
      echo "!! debug-flags DID NOT RUN — sbt exited $st; its output:"
      tail -20 "$CAP" | sed 's/^/     /'
      rm -f "$CAP"; exit 1
    fi
    rm -f "$CAP"

[doc("write one flag into .balticporter/debug.properties — the hand-written layer, which beats run.properties (a -D beats both, but never reaches a forked migration)")]
debug-set KEY VALUE:
    #!/usr/bin/env bash
    cd "{{root}}"
    F="{{bp_root}}/.balticporter/debug.properties"
    mkdir -p "$(dirname "$F")"
    K="{{KEY}}"
    case "$K" in balticporter.*) ;; *) K="balticporter.$K" ;; esac
    [ -f "$F" ] || printf '# hand-written debug flags (CLAUDE.md §4.6) — `just debug-clear` removes them\n' > "$F"
    # IDEMPOTENT, and that is not tidiness: java.util.Properties keeps the LAST occurrence, so an
    # appended duplicate makes the effective value depend on the order of a file nobody reads.
    # Exact-prefix match rather than a regex — a key is full of dots, and `.` matches anything.
    awk -v k="$K=" 'substr($0, 1, length(k)) != k' "$F" > "$F.tmp" && mv "$F.tmp" "$F"
    printf '%s=%s\n' "$K" "{{VALUE}}" >> "$F"
    echo "$F now holds:"
    grep -v '^#' "$F" | sed 's/^/  /'
    echo "(confirm what a run will resolve:  just debug-flags)"

[doc("remove one flag from .balticporter/debug.properties, or ALL of them (no KEY)")]
debug-clear KEY="":
    #!/usr/bin/env bash
    cd "{{root}}"
    F="{{bp_root}}/.balticporter/debug.properties"
    if [ ! -f "$F" ]; then echo "no debug flags set ($F is absent)"; exit 0; fi
    if [ -z "{{KEY}}" ]; then
      # The whole file GOES. A stale debug flag is invisible — it moves no count, fails no check,
      # and quietly changes what every later run in this checkout emits — so "clear" has to mean
      # a state an operator can verify at a glance, and absent is that state.
      echo "removing all hand-written debug flags:"
      grep -v '^#' "$F" | sed 's/^/  -/'
      rm -f "$F"
    else
      K="{{KEY}}"
      case "$K" in balticporter.*) ;; *) K="balticporter.$K" ;; esac
      awk -v k="$K=" 'substr($0, 1, length(k)) != k' "$F" > "$F.tmp" && mv "$F.tmp" "$F"
      echo "removed $K; $F now holds:"
      grep -v '^#' "$F" | sed 's/^/  /'
      # …and an empty file is removed too, so `.balticporter/` never carries a file whose only
      # content is a header that reads as configuration.
      if [ -z "$(grep -v '^#' "$F")" ]; then rm -f "$F"; echo "(file removed — nothing left in it)"; fi
    fi

[doc("one type's TIR + emitted Scala, around a phase boundary (ROOT is a JAVA source root)")]
debug-emit ROOT FQN PHASES="" *FLAGS:
    #!/usr/bin/env bash
    cd "{{root}}"
    # Absolute, always: the main resolves a relative path against `balticporter.root`, which for an
    # UNFORKED `engine/runMain` is the sbt server's directory and not this shell's.
    R="{{ROOT}}"; case "$R" in /*) ;; *) R="$(pwd)/$R" ;; esac
    ARGS="--root $R --fqn {{FQN}} --scala"
    # A phase list means "show me this type ACROSS that boundary" — run the phases and bracket each
    # one. Without it the type is printed as the frontend built it, which is the other question.
    [ -n "{{PHASES}}" ] && ARGS="$ARGS --phases {{PHASES}} --dump-before {{PHASES}} --dump-after {{PHASES}}"
    echo "+ {{core_project}}/runMain balticporter.runner.DebugEmit $ARGS {{FLAGS}}"
    echo "  (add --fast to parse ONLY the included files: seconds instead of minutes on a large"
    echo "   library, at the cost of resolution fidelity; --include <substr> narrows what is converted)"
    mkdir -p .balticporter/tmp
    CAP=".balticporter/tmp/debug-emit-$$.txt"
    {{sbt_migrate}} "{{core_project}}/runMain balticporter.runner.DebugEmit $ARGS {{FLAGS}}" 2>&1 |
      sed $'s/\033\\[[0-9;]*[a-zA-Z]//g' > "$CAP"
    st=${PIPESTATUS[0]}
    # from the tool's own first line. `index`, not a regex: the marker is `[debug-emit]`, and as a
    # regex those brackets are a CHARACTER CLASS that matches most of a stack trace.
    awk 'f || index($0, "[debug-emit]") > 0 { f = 1; print }' "$CAP" | grep -vE '^\[(info|warn|success)\]'
    if [ "$st" != "0" ]; then
      echo "!! debug-emit DID NOT RUN — sbt exited $st; its output:"
      tail -20 "$CAP" | sed 's/^/     /'
      rm -f "$CAP"; exit 1
    fi
    rm -f "$CAP"

[doc("CorrelateMain standalone — a compiler or test log you produced BY HAND, joined to members")]
correlate OUT *ARGS:
    #!/usr/bin/env bash
    cd "{{root}}"
    ROOT="$(pwd)"
    export CORE_PROJECT="{{core_project}}"
    . scripts/_lib.sh
    if [ -z "{{ARGS}}" ]; then
      echo "usage: just correlate <out-dir> [--scalac <file>] [--tests <file>] [--srcmap [scope=]<file>]…"
      echo
      echo "  CLAUDE.md §5.1: never open an emitted file to work out which member an error is in."
      echo "  The lanes do this for you; this is the same command for a compile you ran by hand."
      echo
      echo "  scala-cli compile --scala 3.8.4 --server=false --jvm {{jdk_version}} <port>/src_managed/main/scala > /tmp/c.txt"
      echo "  just correlate port-report/<Port>/run-latest --scalac /tmp/c.txt \\"
      echo "       --srcmap port-report/<Port>/run-latest/srcmap.tsv"
      echo
      echo "  --baseline defaults to <out>/../baseline, which is where the diffs come from."
      exit 2
    fi
    abs() { case "$1" in /*) printf '%s' "$1" ;; *) printf '%s' "$ROOT/$1" ;; esac; }
    OUT="$(abs "{{OUT}}")"
    # EVERY path argument is absolutised, and this is not politeness. `engine/runMain` is FORKED,
    # so the correlator's working directory is the SUBPROJECT: a relative `--scalac out.txt` that
    # reads correctly in this shell resolves to `engine/out.txt` and the run dies naming a file
    # nobody wrote. The lanes never met it because they compose `$ROOT/…` throughout; a recipe an
    # operator types by hand meets it immediately (measured, first try).
    set -- {{ARGS}}
    A=(); prev=""
    for a in "$@"; do
      case "$prev" in
        --scalac|--tests|--markers|--baseline|--out) a="$(abs "$a")" ;;
        --srcmap) case "$a" in
                    main=*|test=*) a="${a%%=*}=$(abs "${a#*=}")" ;;
                    *)             a="$(abs "$a")" ;;
                  esac ;;
      esac
      A+=("$a"); prev="$a"
    done
    # The lanes' own helper, deliberately: one invocation path, one set of guards (a correlation
    # that did not happen must not render as an empty-but-tidy block).
    correlate "$OUT" "${A[@]}"

# ---------------------------------------------------------------------------------------------
# The debug recipes, proving themselves. No sbt, no ports, no network — it runs in seconds, so
# there is no reason for it to rot.
#
# What it covers is the half a Scala spec cannot: the SHELL. `debug-set` appending a duplicate key
# instead of replacing it, `debug-clear` leaving a header that reads as configuration, and
# `members-unchanged` exiting 0 on an input that does not exist are all defects in bash, and the
# last of them shipped. The engine-side halves (the precedence rule, the dump boundaries, the
# emitted text) are proven by DebugFlagsSpec, DebugFlagsMainSpec, PipelineDebugSpec and
# DebugEmitSpec — this is not a substitute for those.
# ---------------------------------------------------------------------------------------------
[doc("prove the debug recipes do what they say — set/clear/members-unchanged, in a temp root")]
debug-selfcheck:
    #!/usr/bin/env bash
    cd "{{root}}"
    T="$(mktemp -d)"
    trap 'rm -rf "$T"' EXIT
    fail=0
    ok()   { echo "  ok   $1"; }
    bad()  { echo "  FAIL $1"; fail=1; }
    want() { [ "$2" = "$3" ] && ok "$1" || bad "$1 (want [$3], got [$2])"; }
    J="{{just_executable()}}"
    F="$T/.balticporter/debug.properties"

    echo "-- debug-set / debug-clear (root=$T) --"
    BP_ROOT="$T" $J debug-set skipPhases '*' > /dev/null
    want "debug-set writes the file"            "$(grep -c . "$F" 2>/dev/null)" "2"
    want "…with the balticporter. prefix added" "$(grep -c '^balticporter.skipPhases=\*$' "$F")" "1"

    BP_ROOT="$T" $J debug-set skipPhases 'collections' > /dev/null
    want "debug-set is IDEMPOTENT — one entry"  "$(grep -c '^balticporter.skipPhases=' "$F")" "1"
    want "…and it is the NEW value"             "$(grep -c '^balticporter.skipPhases=collections$' "$F")" "1"

    BP_ROOT="$T" $J debug-set balticporter.tracePhases true > /dev/null
    want "an already-prefixed key is not double-prefixed" "$(grep -c '^balticporter.tracePhases=true$' "$F")" "1"
    want "…beside the first, which survives"    "$(grep -vc '^#' "$F")" "2"

    BP_ROOT="$T" $J debug-clear skipPhases > /dev/null
    want "debug-clear KEY removes one flag"     "$(grep -c '^balticporter.skipPhases=' "$F")" "0"
    want "…and leaves the other"                "$(grep -c '^balticporter.tracePhases=true$' "$F")" "1"

    BP_ROOT="$T" $J debug-clear tracePhases > /dev/null
    [ -f "$F" ] && bad "an emptied debug.properties must be REMOVED, not left as a header" \
                || ok "an emptied debug.properties is removed"

    BP_ROOT="$T" $J debug-set dumpOnly p.Foo > /dev/null
    BP_ROOT="$T" $J debug-clear > /dev/null
    [ -f "$F" ] && bad "debug-clear with no KEY must remove the file" || ok "debug-clear with no KEY removes the file"
    BP_ROOT="$T" $J debug-clear > /dev/null 2>&1
    want "…and is idempotent on an absent file" "$?" "0"

    echo "-- correlate: no arguments is a USAGE, not a silent no-op --"
    out=$($J correlate some/out 2>&1); rc=$?
    want "correlate with no options exits 2"      "$rc" "2"
    case "$out" in *"§5.1"*) ok "…and points at the rule it serves" ;; *) bad "…rule: $out" ;; esac

    echo "-- _lib.sh: an unset CORE_PROJECT is FATAL, never a dead default --"
    # The lanes all export it; what this pins is the file's behaviour WITHOUT the Justfile, which is
    # how the old default (`core`) survived the module restructure — it named a project that no
    # longer existed, and the comment above it claimed the file was correct on its own.
    out=$( { unset CORE_PROJECT; . scripts/_lib.sh; correlate "$T/out"; } 2>&1 ); rc=$?
    want "correlate without CORE_PROJECT is fatal"  "$rc" "1"
    case "$out" in *CORE_PROJECT*Justfile*) ok "…and names the variable and where it is set" ;;
                   *) bad "…and names the variable and where it is set: $out" ;; esac
    case "$out" in *runMain*) bad "…it must not reach sbt at all" ;; *) ok "…before sbt is invoked" ;; esac

    echo "-- members-unchanged: a missing input is FATAL, and names the port --"
    R="$T/port-report"
    mkdir -p "$R/Both/baseline" "$R/Both/run-latest"
    printf 'a\tb\n' > "$R/Both/baseline/members.tsv"
    printf 'a\tb\n' > "$R/Both/run-latest/members.tsv"
    out=$(BP_REPORT="$R" $J members-unchanged Both); rc=$?
    want "an identical pair is 0 changed, exit 0" "$rc" "0"
    case "$out" in *"Both: 0 member(s) changed"*) ok "…and says so" ;; *) bad "…and says so: $out" ;; esac

    printf 'a\tc\n' > "$R/Both/run-latest/members.tsv"
    BP_REPORT="$R" $J members-unchanged Both > /dev/null 2>&1; rc=$?
    want "a moved member is exit 1"               "$rc" "1"

    out=$(BP_REPORT="$R" $J members-unchanged NoSuchPort 2>&1); rc=$?
    want "an unknown port is FATAL"               "$rc" "1"
    case "$out" in *"NO SUCH PORT: NoSuchPort"*) ok "…and names it" ;; *) bad "…and names it: $out" ;; esac

    mkdir -p "$R/NoBaseline/run-latest"; printf 'a\tb\n' > "$R/NoBaseline/run-latest/members.tsv"
    out=$(BP_REPORT="$R" $J members-unchanged NoBaseline 2>&1); rc=$?
    want "a run with NO BASELINE is fatal"        "$rc" "1"
    case "$out" in *"MEASURED BUT UNBASELINED"*) ok "…and says which state it is in" ;; *) bad "…state: $out" ;; esac

    mkdir -p "$R/NoRun/baseline"; printf 'a\tb\n' > "$R/NoRun/baseline/members.tsv"
    out=$(BP_REPORT="$R" $J members-unchanged NoRun 2>&1); rc=$?
    want "a NAMED port with no run is fatal"      "$rc" "1"
    case "$out" in *"NOT COMPARED"*) ok "…and says the comparison did not happen" ;; *) bad "…state: $out" ;; esac

    out=$(BP_REPORT="$T/nothing-here" $J members-unchanged 2>&1); rc=$?
    want "an absent report directory is fatal"    "$rc" "1"

    E="$T/empty-report"; mkdir -p "$E"
    out=$(BP_REPORT="$E" $J members-unchanged 2>&1); rc=$?
    want "a sweep that compares NOTHING is fatal" "$rc" "1"
    case "$out" in *"NOTHING WAS COMPARED"*) ok "…and says so" ;; *) bad "…says so: $out" ;; esac

    echo
    [ "$fail" = "0" ] && echo "debug-selfcheck: PASS" || { echo "debug-selfcheck: FAILED"; exit 1; }

# ---------------------------------------------------------------------------------------------
# Proves the two TEST-OUTCOME GATES every measure lane shares — no sbt, no ports, no compile.
#
# These are the guards that decide whether a lane may report success, and a guard is exactly the
# thing that must not be believed on the strength of its comment. Both were shipped as lines that
# PRINTED and returned 0: ashley's lane said `!! DID NOT RUN — 2 emitted test(s) never executed`
# on every run and exited 0 anyway, which is CLAUDE.md §3's "a test that stopped running is
# reported as such, never as a pass" failing inside the measurement itself.
#
# The awkward half is that the gate must NOT fire on a skip the port has already accepted — so the
# cases below prove both directions against the shapes `Correlate.TestDiff` really writes.
# ---------------------------------------------------------------------------------------------
[doc("proves the test-outcome gates every lane shares — no sbt, no ports")]
lane-selfcheck:
    #!/usr/bin/env bash
    cd "{{root}}"
    export CORE_PROJECT="{{core_project}}"
    . scripts/_lib.sh
    T="$(mktemp -d)"
    trap 'rm -rf "$T"' EXIT
    fail=0
    ok()   { echo "  ok   $1"; }
    bad()  { echo "  FAIL $1"; fail=1; }
    want() { [ "$2" = "$3" ] && ok "$1" || bad "$1 (want [$3], got [$2])"; }

    echo "-- test_outcome_guard --"
    # The shape ashley really has: two skips, both in the baseline, so `TestDiff.newlySkipped` is
    # empty and the diff carries no block. This MUST pass, or the gate is a lane nobody can run.
    printf 'TEST OUTCOMES: 110 passing, 0 failing  SKIPPED=2 — these never ran\n\n-- still failing (0)\n\n' > "$T/tests-diff.txt"
    test_outcome_guard "$T" 0 > /dev/null 2>&1
    want "an ACCEPTED skip (baselined) does not fail the lane" "$?" "0"

    # …and the same suite the first time nobody has accepted it.
    printf 'TEST OUTCOMES: 110 passing\n\n-- NEWLY SKIPPED (2) — the runner did NOT run these\n   p.S.t\n        anchor=none\n\n-- still failing (0)\n' > "$T/tests-diff.txt"
    out=$(test_outcome_guard "$T" 0 2>&1); rc=$?
    want "a NEWLY SKIPPED test FAILS the lane" "$rc" "1"
    case "$out" in *"baseline-accept"*) ok "…and names the promotion command" ;; *) bad "…names the promotion command" ;; esac

    # A correlation that did not write a diff is not a clean one (CLAUDE.md §5.1's missing-input rule).
    printf 'x\n' > "$T/tests-diff.txt"; rm -f "$T/tests-diff.txt"
    out=$(test_outcome_guard "$T" 0 2>&1); rc=$?
    want "a MISSING tests-diff.txt is fatal, never clean" "$rc" "1"
    case "$out" in *"NO TEST DIFF"*) ok "…and says which comparison never happened" ;; *) bad "…says so" ;; esac

    # OUTCOMES LOST is carried in from `reconcile_outcomes` so the correlation still gets to run.
    printf 'TEST OUTCOMES: ok\n\n' > "$T/tests-diff.txt"
    want "a carried OUTCOMES-LOST status fails the lane" "$(test_outcome_guard "$T" 1 > /dev/null 2>&1; echo $?)" "1"

    # …and the OTHER way a suite stops running: the tests are never EMITTED, so no row exists for
    # any baseline to hold an opinion about. `TestDiff.disappeared` is what sees it, and it was
    # rendered and ungated for the life of the field.
    printf 'TEST OUTCOMES: 100 passing\n\n-- tests in the baseline that DID NOT RUN (2) — a suite that stopped running is not a suite that passed\n   p.S.a\n   p.S.b\n\n' > "$T/tests-diff.txt"
    out=$(test_outcome_guard "$T" 0 2>&1); rc=$?
    want "a test that DISAPPEARED from the artifact fails the lane" "$rc" "1"
    case "$out" in *"baseline-accept"*) ok "…and names the promotion command" ;; *) bad "…names the promotion command" ;; esac

    echo "-- jdk_guard --"
    # THE JDK IS AN INPUT TO THE MEASUREMENT (ENGINE-LIMITS M5.10). The three cases below are the
    # three answers the guard can give, and the middle one is why it exists at all: a frontend on a
    # JDK the compiler does not have produces a port that fails to compile with every check count,
    # every finding and every port-map fingerprint flat.
    J="$T/jdk"; mkdir -p "$J/run-latest"
    # the compile half, derived the way the guard derives it, so this selfcheck asserts a RELATION
    # and never a number — a machine on another `jdk_version` must still be able to run it.
    printf 'specification\t%s\nversion\tx\nvendor\tx\nhome\tx\n' "{{jdk_version}}" > "$J/run-latest/jvm.txt"
    out=$(jdk_guard "$J" 2>&1); rc=$?
    want "a run recorded on the lane's own jdk_version passes" "$rc" "0"
    case "$out" in *"frontend jdk"*"compile jdk"*) ok "…and PRINTS both halves on every run" ;; *) bad "…prints both halves" ;; esac

    printf 'specification\tnot-a-jdk\nversion\tx\nvendor\tx\nhome\tx\n' > "$J/run-latest/jvm.txt"
    out=$(jdk_guard "$J" 2>&1); rc=$?
    want "a frontend on ANOTHER jdk FAILS the lane" "$rc" "1"
    case "$out" in *"JDK SPLIT"*) ok "…and names both versions" ;; *) bad "…names both versions" ;; esac

    rm -f "$J/run-latest/jvm.txt"
    out=$(jdk_guard "$J" 2>&1); rc=$?
    want "a MISSING jvm.txt is fatal, never clean" "$rc" "1"
    case "$out" in *"NO JVM RECORD"*) ok "…because 'nothing compared this' reads exactly like 'this compares clean'" ;; *) bad "…says which comparison never happened" ;; esac

    echo "-- reconcile_outcomes --"
    printf '  + a 0.0s\n  + b 0.0s\n' > "$T/run.txt"
    want "an emitted test with NO outcome line is a failure" "$(reconcile_outcomes "$T/run.txt" 3 > /dev/null; echo $?)" "1"
    want "…and a fully reconciled run is not"                "$(reconcile_outcomes "$T/run.txt" 2 > /dev/null; echo $?)" "0"
    # The OTHER direction, which is a wrong EMITTED count and not a lost test: reported, never fatal.
    # Gated, it would stop a green hand-written-suite lane over a `test("` grep (anim8/screens/vfx).
    want "MORE outcomes than emitted does NOT fail the lane"  "$(reconcile_outcomes "$T/run.txt" 1 > /dev/null; echo $?)" "0"
    case "$(reconcile_outcomes "$T/run.txt" 1)" in
      *"ABOVE THE EMITTED COUNT"*) ok "…and says which figure is the wrong one" ;;
      *) bad "an over-count must say the EMITTED figure is what is wrong" ;;
    esac
    # A skip IS an outcome line, so it reconciles; whether it is a NEW one is the guard's question
    # and deliberately not this function's — it has no baseline to ask.
    printf '  + a 0.0s\n==> s p.S.b skipped 0.0s\n' > "$T/run.txt"
    want "a SKIP counts as an outcome and does not fail here" "$(reconcile_outcomes "$T/run.txt" 2 > /dev/null; echo $?)" "0"
    case "$(reconcile_outcomes "$T/run.txt" 2)" in
      *"DID NOT RUN"*) ok "…but is still REPORTED, loudly" ;;
      *) bad "a skip must still print DID NOT RUN" ;;
    esac

    echo "-- munit_emitted (and the stream it reads) --"
    # THE DENOMINATOR every test lane hands `reconcile_outcomes` and `test_discovery_guard`. Anchored
    # on `test("` on ONE LINE it missed 37 registrations in one reference suite, 21 of them in files
    # a census keeps (CLAUDE.md §4.56). Every case below is a shape MEASURED in the corpus: the
    # multi-line and interpolated forms the anchor missed, and the array read / declaration /
    # selection a looser `test(` would have counted (181 of the first in liqp's emitted suite alone).
    MU="$T/munit"; mkdir -p "$MU"
    {
      printf 'package p\n'
      printf 'class S extends munit.FunSuite {\n'
      printf '  /* a block comment holding test("commented out") { } */\n'
      printf '  test("plain") { }\n'
      printf '  test(\n    "wrapped onto the next line"\n  ) { }\n'
      printf '  test(s"interpolated") { }\n'
      printf '  val n = "computed"\n'
      printf '  test(n) { }\n'
      printf '  other.test("a selection, not a registration") { }\n'
      printf '  def test(i: Int): Int = i\n'
      printf '  val k = arr(test(0) + test(1))\n'
      printf '  @Test def notTranslated(): Unit = ()\n'
      printf '}\n'
    } > "$MU/S.scala"
    want "the four registration shapes are counted and the four negatives are not" "$(munit_emitted "$MU" 2>/dev/null)" "4"
    want "…and junit_residue reads the SAME stream unaffected by the file markers" "$(junit_residue "$MU")" "1"
    want "a directory with no Scala at all is an exact 0, never an empty string" "$(munit_emitted "$T/nothing-here" 2>/dev/null)" "0"

    # …and the shape it CANNOT classify is reported rather than silently dropped: a named call
    # applied to no body is neither a registration this counter knows nor one of its three negatives.
    printf 'package p\nclass U extends munit.FunSuite {\n  test("named but never applied")\n}\n' > "$MU/U.scala"
    want "an unclassifiable call does not change the count" "$(munit_emitted "$MU" 2>/dev/null)" "4"
    case "$(munit_emitted "$MU" 2>&1 >/dev/null)" in
      *"APPLIED TO NO BODY"*"U.scala:3"*) ok "…and is reported on stderr with its file and LINE" ;;
      *) bad "…and is reported on stderr with its file and line: $(munit_emitted "$MU" 2>&1 >/dev/null)" ;;
    esac

    echo "-- error_baseline_guard --"
    # The gate the screens lane's 0 -> 3 walked straight through. Both directions are failures and
    # a missing baseline is a third: "nothing is comparing this" reads exactly like "this is clean".
    mkdir -p "$T/eb/baseline" "$T/eb/run-latest"
    printf '3\n' > "$T/eb/baseline/expected-errors"
    out=$(error_baseline_guard 3 "$T/eb" 2>&1); rc=$?
    want "an UNCHANGED error count does not fail the lane" "$rc" "0"
    want "…and the run's observed count is written for baseline-accept" "$(cat "$T/eb/run-latest/errors-count")" "3"
    ( headline 3 "$T/eb" ) > /dev/null 2>&1
    want "…and headline exits 0 after it"                 "$?" "0"

    out=$(error_baseline_guard 5 "$T/eb" 2>&1); rc=$?
    want "a RISEN error count FAILS the lane" "$rc" "1"
    case "$out" in *"ERRORS ROSE"*"3 -> 5"*) ok "…and states the before->after" ;; *) bad "…states the before->after" ;; esac
    # The verdict has to CROSS A SUBSHELL — the capture above is one, and a shell variable set in it
    # reaches nobody. That is exactly how this gate first shipped, and the marker file is the fix.
    ( headline 5 "$T/eb" ) > /dev/null 2>&1
    want "…and headline EXITS NON-ZERO for it, across the capture" "$?" "1"

    out=$(error_baseline_guard 1 "$T/eb" 2>&1); rc=$?
    want "a FALLEN error count fails the lane too" "$rc" "1"
    case "$out" in *"baseline-accept"*) ok "…and names the promotion command" ;; *) bad "…names the promotion command" ;; esac

    rm -f "$T/eb/baseline/expected-errors"
    out=$(error_baseline_guard 0 "$T/eb" 2>&1); rc=$?
    want "a MISSING error baseline is fatal, never clean" "$rc" "1"
    case "$out" in *"NO ERROR BASELINE"*) ok "…and says nothing is comparing the count" ;; *) bad "…says so" ;; esac

    # …and a marker left by a PREVIOUS run must not fail a run that is now green.
    printf '7\n' > "$T/eb/baseline/expected-errors"
    error_baseline_guard 7 "$T/eb" > /dev/null 2>&1
    ( headline 7 "$T/eb" ) > /dev/null 2>&1
    want "a STALE failure marker is cleared by the next guard" "$?" "0"

    echo "-- test_discovery_guard --"
    # The gate a NON-FAILING echo left open: `!! TESTS LOST — 64 of 639` printed on every liqp run,
    # so a 65th accidental loss changed one digit in a line nobody had to act on. A port that
    # DECLARES the loss (excludeGlobs, dropMethods) still loses those tests, so the number is real
    # and is baselined exactly as the error count is — and moves in EITHER direction fail.
    mkdir -p "$T/td/baseline" "$T/td/run-latest"
    printf '64\n' > "$T/td/baseline/expected-lost"
    out=$(test_discovery_guard 639 575 "$T/td" 2>&1); rc=$?
    want "an ACKNOWLEDGED loss does not fail the lane" "$rc" "0"
    want "…and the run's observed loss is written for baseline-accept" "$(cat "$T/td/run-latest/tests-lost")" "64"
    ( headline 0 "$T/td" ) > /dev/null 2>&1
    want "…and headline exits 0 after it" "$?" "0"

    out=$(test_discovery_guard 639 574 "$T/td" 2>&1); rc=$?
    want "one MORE test lost FAILS the lane" "$rc" "1"
    case "$out" in *"64 -> 65"*) ok "…and states the before->after" ;; *) bad "…states the before->after" ;; esac
    # …across a subshell, for the reason `error_baseline_guard` uses a marker rather than a variable.
    ( headline 0 "$T/td" ) > /dev/null 2>&1
    want "…and headline EXITS NON-ZERO for it, across the capture" "$?" "1"

    out=$(test_discovery_guard 639 576 "$T/td" 2>&1); rc=$?
    want "a GAIN fails too — it is a change, not an improvement to absorb" "$rc" "1"
    case "$out" in *"baseline-accept"*) ok "…and names the promotion command" ;; *) bad "…names the promotion command" ;; esac

    # A port that loses NOTHING is the normal case and must be held to zero, not exempted.
    printf '0\n' > "$T/td/baseline/expected-lost"
    want "a port that loses nothing passes at zero" "$(test_discovery_guard 221 221 "$T/td" > /dev/null 2>&1; echo $?)" "0"
    out=$(test_discovery_guard 221 220 "$T/td" 2>&1); rc=$?
    want "…and ONE lost test fails it" "$rc" "1"

    rm -f "$T/td/baseline/expected-lost"
    out=$(test_discovery_guard 639 575 "$T/td" 2>&1); rc=$?
    want "a MISSING discovery baseline is fatal, never clean" "$rc" "1"
    case "$out" in *"NO TEST-DISCOVERY BASELINE"*) ok "…and says nothing is comparing the count" ;; *) bad "…says so" ;; esac

    # …and a marker from a PREVIOUS run must not fail a run that is now acknowledged.
    printf '64\n' > "$T/td/baseline/expected-lost"
    test_discovery_guard 639 575 "$T/td" > /dev/null 2>&1
    ( headline 0 "$T/td" ) > /dev/null 2>&1
    want "a STALE discovery marker is cleared by the next guard" "$?" "0"

    echo "-- findings_baseline_guard --"
    # The FIFTH promoted baseline and the one nothing read. Its whole point is that every COUNT can
    # be identical while a row's owner, its UsageKind or a running total printed inside its own text
    # has moved — so the fixtures below hold `counts.tsv` constant by construction and move only the
    # content, which is exactly the shape that used to pass.
    mkdir -p "$T/fb/baseline" "$T/fb/run-latest"
    hdr='#id\tcheck\tkind\towner\tpath\tline\tdetail\n'
    printf "$hdr"'aaa\tportability(all)\tJdk\tp.A#m\ta/A.java\t7\tsome detail\n' > "$T/fb/baseline/findings.tsv"
    printf "$hdr"'aaa\tportability(all)\tJdk\tp.A#m\ta/A.java\t7\tsome detail\n' > "$T/fb/run-latest/findings.tsv"
    out=$(findings_baseline_guard "$T/fb" 2>&1); rc=$?
    want "IDENTICAL findings do not fail the lane" "$rc" "0"
    ( headline 0 "$T/fb" ) > /dev/null 2>&1
    want "…and headline exits 0 after it" "$?" "0"

    # the same row, a DIFFERENT id — the churn the file was left ungated for. Must not fail.
    printf "$hdr"'zzz\tportability(all)\tJdk\tp.A#m\ta/A.java\t7\tsome detail\n' > "$T/fb/run-latest/findings.tsv"
    want "a row whose ID moved but whose CONTENT did not is not a change" \
      "$(findings_baseline_guard "$T/fb" > /dev/null 2>&1; echo $?)" "0"

    # a moved OWNER at an identical count — one row out, one row in, `counts.tsv` unchanged.
    printf "$hdr"'aaa\tportability(all)\tJdk\tp.A$1#run\ta/A.java\t7\tsome detail\n' > "$T/fb/run-latest/findings.tsv"
    out=$(findings_baseline_guard "$T/fb" 2>&1); rc=$?
    want "a moved OWNER fails the lane, at an unchanged count" "$rc" "1"
    case "$out" in *"FINDINGS CONTENT MOVED"*) ok "…and says what moved" ;; *) bad "…says what moved" ;; esac
    case "$out" in *"baseline-accept"*) ok "…and names the promotion command" ;; *) bad "…names the promotion command" ;; esac
    ( headline 0 "$T/fb" ) > /dev/null 2>&1
    want "…and headline EXITS NON-ZERO for it, across the capture" "$?" "1"

    # …and a lane that gated TWO reports must have named both, or the second's marker reaches nobody.
    mkdir -p "$T/fb2/run-latest"
    : > "$T/fb2/run-latest/findings-baseline-failed"
    ( headline 0 "$T/fb" "$T/fb2" ) > /dev/null 2>&1
    want "a SECOND report's marker fails the lane when headline is told about it" "$?" "1"

    rm -f "$T/fb/baseline/findings.tsv"
    printf "$hdr"'aaa\tportability(all)\tJdk\tp.A#m\ta/A.java\t7\tsome detail\n' > "$T/fb/run-latest/findings.tsv"
    out=$(findings_baseline_guard "$T/fb" 2>&1); rc=$?
    want "a MISSING findings baseline is fatal, never clean" "$rc" "1"
    case "$out" in *"NO FINDINGS BASELINE"*) ok "…and says nothing is comparing the content" ;; *) bad "…says so" ;; esac

    # …and a marker from a PREVIOUS run must not fail a run that is now acknowledged.
    printf "$hdr"'aaa\tportability(all)\tJdk\tp.A#m\ta/A.java\t7\tsome detail\n' > "$T/fb/baseline/findings.tsv"
    findings_baseline_guard "$T/fb" > /dev/null 2>&1
    ( headline 0 "$T/fb" ) > /dev/null 2>&1
    want "a STALE findings marker is cleared by the next guard" "$?" "0"

    echo "-- port_map_guard --"
    # The SIXTH promoted baseline and the second one nothing read — and the only one another RUN
    # reads, because a dependent's emitted text comes out of its base's map. It went stale twice
    # (PROGRESS.md §12.2.5's 60 member rows, §12.4.6's nine stale `policy=` headers) and both were
    # found by hand. The fixtures below are those two shapes plus the three absences.
    mkdir -p "$T/pm/baseline" "$T/pm/run-latest"
    pmhdr() { printf '# balticporter port map\tschema=3\tmodule=sge\tengine=bp/0.1\tsources=aaaa\tfiles=2\tpolicy=%s\n' "$1"; }
    pmbody() { printf '#kind\tupstream\temitted\tdisposition\tbody\tjavaPath\tjavaLine\tdigest\tshape\ntype\tp.A\tq.A\tRenamed\t-\t\t0\t\t%s\n' "$1"; }
    { pmhdr pol1; pmbody 'form=var'; } > "$T/pm/baseline/port-map.tsv"
    { pmhdr pol1; pmbody 'form=var'; } > "$T/pm/run-latest/port-map.tsv"
    out=$(port_map_guard "$T/pm" 2>&1); rc=$?
    want "an IDENTICAL port map does not fail the lane" "$rc" "0"
    ( headline 0 "$T/pm" ) > /dev/null 2>&1
    want "…and headline exits 0 after it" "$?" "0"

    # §12.4.6's shape: the ROWS are byte-identical and only the policy fingerprint moved, which is
    # what a dependent carries stale when a base's manifest changes under it.
    { pmhdr pol2; pmbody 'form=var'; } > "$T/pm/run-latest/port-map.tsv"
    out=$(port_map_guard "$T/pm" 2>&1); rc=$?
    want "a stale policy= HEADER fails the lane, at identical rows" "$rc" "1"
    case "$out" in *"policy="*"pol1"*"pol2"*) ok "…and names the field and its before->after" ;; *) bad "…names the field" ;; esac
    case "$out" in *"MANIFEST changed"*) ok "…and says what a moved policy digest MEANS" ;; *) bad "…says what it means" ;; esac
    ( headline 0 "$T/pm" ) > /dev/null 2>&1
    want "…and headline EXITS NON-ZERO for it, across the capture" "$?" "1"

    # §12.2.5's shape: the header is identical and a member's published SHAPE moved — the column a
    # dependent's collapse comparison reads, and one no check COUNT can see.
    { pmhdr pol1; pmbody 'form=accessor'; } > "$T/pm/run-latest/port-map.tsv"
    out=$(port_map_guard "$T/pm" 2>&1); rc=$?
    want "a moved SHAPE column fails the lane, at an identical header" "$rc" "1"
    case "$out" in *"PORT MAP MOVED"*) ok "…and says what moved" ;; *) bad "…says what moved" ;; esac
    case "$out" in *"baseline-accept"*) ok "…and names the promotion command" ;; *) bad "…names the promotion command" ;; esac
    case "$out" in *"DEPENDENT"*) ok "…and says the dependents must be re-measured" ;; *) bad "…says the dependents must be re-measured" ;; esac

    # …and a lane that gated TWO reports must have named both, or the second's marker reaches nobody.
    mkdir -p "$T/pm2/run-latest"
    : > "$T/pm2/run-latest/port-map-baseline-failed"
    ( headline 0 "$T/pm" "$T/pm2" ) > /dev/null 2>&1
    want "a SECOND report's marker fails the lane when headline is told about it" "$?" "1"

    { pmhdr pol1; pmbody 'form=var'; } > "$T/pm/run-latest/port-map.tsv"
    rm -f "$T/pm/baseline/port-map.tsv"
    out=$(port_map_guard "$T/pm" 2>&1); rc=$?
    want "a MISSING port-map baseline is fatal, never clean" "$rc" "1"
    case "$out" in *"NO PORT-MAP BASELINE"*) ok "…and says nothing is comparing what it publishes" ;; *) bad "…says so" ;; esac

    # The other absence, and the one that is NOT symmetric: a run that stopped publishing leaves its
    # dependents reading the COMMITTED map (PortMap.discoverIn falls back to baseline/), so the
    # regression is invisible from every other artifact.
    { pmhdr pol1; pmbody 'form=var'; } > "$T/pm/baseline/port-map.tsv"
    rm -f "$T/pm/run-latest/port-map.tsv"
    out=$(port_map_guard "$T/pm" 2>&1); rc=$?
    want "a run that PUBLISHED NO MAP fails the lane" "$rc" "1"
    case "$out" in *"PORT MAP DISAPPEARED"*) ok "…and says the dependents fall back to the committed one" ;; *) bad "…says so" ;; esac

    # …and a report that has neither is a caller with no artifact layer, not a failure.
    rm -f "$T/pm/baseline/port-map.tsv"
    want "a report with NEITHER map is not a failure" "$(port_map_guard "$T/pm" > /dev/null 2>&1; echo $?)" "0"

    # …and a marker from a PREVIOUS run must not fail a run that is now acknowledged.
    { pmhdr pol1; pmbody 'form=var'; } > "$T/pm/baseline/port-map.tsv"
    { pmhdr pol1; pmbody 'form=var'; } > "$T/pm/run-latest/port-map.tsv"
    port_map_guard "$T/pm" > /dev/null 2>&1
    ( headline 0 "$T/pm" ) > /dev/null 2>&1
    want "a STALE port-map marker is cleared by the next guard" "$?" "0"

    echo
    [ "$fail" = "0" ] && echo "lane-selfcheck: PASS" || { echo "lane-selfcheck: FAILED"; exit 1; }

# ---------------------------------------------------------------------------------------------
# The baseline half of the check report: list, show, diff and ACCEPT.
#
# <port> is a directory under port-report/, named after the migration program's main class
# (LibgdxCoreMigrate, LibgdxTestMigrate, …) — balticporter.tir.CheckReport derives it, so no
# migration program has to be told what it is called.
#
# Promotion is EXPLICIT, deliberately: `run-latest/` is overwritten by every run, `baseline/` moves
# only when a human accepts a step. That is golden-test discipline, and it is what makes
# "omissions 31->33" a fact rather than a memory (CLAUDE.md §5).
# ---------------------------------------------------------------------------------------------
[doc("every port: baseline size and last run")]
baseline-list:
    #!/usr/bin/env bash
    set -e
    cd "{{root}}"
    ROOT="$(pwd)"
    if [ ! -d "$ROOT/port-report" ]; then echo "no port-report/ yet — run a migration first"; exit 0; fi
    for d in "$ROOT"/port-report/*/; do
      name="$(basename "$d")"
      b="no baseline"; [ -f "$d/baseline/counts.tsv" ] && b="baseline: $(grep -vc '^#' "$d/baseline/findings.tsv" || echo 0) findings"
      r="no run"; [ -f "$d/run-latest/subject.txt" ] && r="$(cat "$d/run-latest/subject.txt")"
      printf '%-24s %-22s %s\n' "$name" "$b" "$r"
    done

[doc("the run's full report")]
baseline-show PORT:
    #!/usr/bin/env bash
    set -e
    cd "{{root}}"
    cat "port-report/{{PORT}}/run-latest/report.md"

[doc("the run against the committed baseline")]
baseline-diff PORT:
    #!/usr/bin/env bash
    set -e
    cd "{{root}}"
    cat "port-report/{{PORT}}/run-latest/diff.txt"

[doc("promote run-latest to baseline (commit it with the change)")]
baseline-accept PORT:
    #!/usr/bin/env bash
    set -e
    cd "{{root}}"
    DIR="$(pwd)/port-report/{{PORT}}"
    # EVIDENCE THAT A RUN HAPPENED, and it is not always a check report. A migration lane produces
    # `findings.tsv`; a DIFFERENTIAL lane runs no migration at all — its subject is emitted code
    # another lane already checked — and its evidence is `tests.tsv`. Gated on `findings.tsv` alone
    # this recipe refuses to promote a baseline the lane genuinely produced, which pushes an
    # operator towards hand-writing `expected-errors` — the one baseline CLAUDE.md §5 says must
    # never be typed, because a hand-edited floor can disagree with the run that produced it.
    [ -f "$DIR/run-latest/findings.tsv" ] || [ -f "$DIR/run-latest/tests.tsv" ] || \
      ls "$DIR/run-latest/errors-count.dropin."* 1>/dev/null 2>&1 || \
      { echo "no run-latest for {{PORT}} — run its lane first (a migration writes findings.tsv; a differential lane writes tests.tsv; a dropin lane writes errors-count.dropin.*)"; exit 1; }
    mkdir -p "$DIR/baseline"
    # Only DETERMINISTIC, position-free files are promoted:
    #   findings.tsv / counts.tsv  — every check. `counts.tsv` is gated by `show_check_report` and
    #                                `findings.tsv` by `findings_baseline_guard`, which diffs it with
    #                                the ID COLUMN STRIPPED: the id is a hash with a `/2`, `/3`
    #                                sequence assigned in line order, so an upstream whitespace edit
    #                                renumbers rows that did not change. Ungated it hid eight stale
    #                                dependent baselines for two waves (PROGRESS.md §12.2.5), because
    #                                a moved OWNER, a moved `UsageKind` and a moved running total are
    #                                none of them a count
    #   members.tsv                — one digest per emitted member; this is what makes "you changed
    #                                3 members you did not intend to" answerable before a compile,
    #                                and it is line-free so a member that only MOVED does not churn
    #   tests.tsv                  — the pass/fail set; the behavioural baseline, and the only one
    #                                that can catch a CLAUDE.md §4.4 regression
    #   port-map.tsv               — what this port PUBLISHES to its dependents, and the only
    #                                baseline another RUN reads. Gated by `port_map_guard`, which
    #                                strips nothing: the file has no id column and every field it
    #                                does have is a fact somebody has to acknowledge. Ungated it went
    #                                stale twice — 60 member rows in one commit, and nine dependent
    #                                `policy=` headers for days (PROGRESS.md §12.2.5, §12.4.6)
    #   errors-count               — the LANE's compile-error total, promoted as `expected-errors`.
    #                                Written by `error_baseline_guard` on every run precisely so
    #                                that nobody ever types this number: a hand-edited floor is the
    #                                one baseline that can disagree with the run that produced it.
    # srcmap.tsv is deliberately NOT promoted: it is positional by construction and would rewrite
    # itself on every emit. report.md carries the absolute source root and diff.txt is derived.
    for f in findings.tsv counts.tsv members.tsv tests.tsv port-map.tsv divergence.tsv; do
      if [ -f "$DIR/run-latest/$f" ]; then cp "$DIR/run-latest/$f" "$DIR/baseline/"; fi
    done
    if [ -f "$DIR/run-latest/errors-count" ]; then
      cp "$DIR/run-latest/errors-count" "$DIR/baseline/expected-errors"
      echo "expected-errors: $(cat "$DIR/baseline/expected-errors")"
    fi
    # Cross-platform error baselines (CLAUDE.md §5, xplat compile gate): promoted as
    # expected-errors.js and expected-errors.native, written by xplat_compile on every run.
    for plat_suffix in js native; do
      if [ -f "$DIR/run-latest/errors-count.${plat_suffix}" ]; then
        cp "$DIR/run-latest/errors-count.${plat_suffix}" "$DIR/baseline/expected-errors.${plat_suffix}"
        echo "expected-errors.${plat_suffix}: $(cat "$DIR/baseline/expected-errors.${plat_suffix}")"
      fi
    done
    # Reference-flags error baseline (CLAUDE.md §5, DESIGN.md §8.24): promoted as
    # expected-errors.ref, written by flags_compile on every run.
    if [ -f "$DIR/run-latest/errors-count.ref" ]; then
      cp "$DIR/run-latest/errors-count.ref" "$DIR/baseline/expected-errors.ref"
      echo "expected-errors.ref: $(cat "$DIR/baseline/expected-errors.ref")"
    fi
    # Drop-in baselines: per-platform error counts and test outcomes, written by `ecs-dropin`.
    # Promoted into `baseline/dropin/` so they do not collide with the measure lane's own baselines.
    if ls "$DIR/run-latest/errors-count.dropin."* 1>/dev/null 2>&1; then
      mkdir -p "$DIR/baseline/dropin"
      for f in "$DIR"/run-latest/errors-count.dropin.*; do
        platl="${f##*.dropin.}"
        cp "$f" "$DIR/baseline/dropin/expected-errors.${platl}"
        echo "dropin expected-errors.${platl}: $(cat "$DIR/baseline/dropin/expected-errors.${platl}")"
      done
      for f in "$DIR"/run-latest/tests.*.tsv; do
        [ -f "$f" ] || continue
        bn=$(basename "$f")
        cp "$f" "$DIR/baseline/dropin/$bn"
        echo "dropin $bn: $(grep -c $'\tpass$' "$f" || true) pass, $(grep -c $'\tfail$' "$f" || true) fail"
      done
    fi
    #   tests-lost                 — how many of the library's @Test this port does not emit,
    #                                promoted as `expected-lost`. Same rule as the error count and
    #                                for the same reason: written by `test_discovery_guard` on every
    #                                run so nobody types it, and gated in BOTH directions, because a
    #                                loss that grows is a suite that quietly got smaller and passed.
    if [ -f "$DIR/run-latest/tests-lost" ]; then
      cp "$DIR/run-latest/tests-lost" "$DIR/baseline/expected-lost"
      echo "expected-lost:   $(cat "$DIR/baseline/expected-lost")"
    fi
    echo "baseline accepted for {{PORT}}:"
    # Guarded for the same reason as the run-evidence test above: a DIFFERENTIAL lane runs no
    # migration, so it has no check report to print. `set -e` is in force here, so an unguarded
    # `cat` does not merely print an error — it ABORTS the promotion after the files are already
    # copied, leaving a baseline half-accepted and an operator staring at a `cat: No such file`.
    if [ -f "$DIR/baseline/counts.tsv" ]; then
      cat "$DIR/baseline/counts.tsv"
    else
      echo "  (no check report — this lane runs no migration; its baselines are the compile-error count and the outcomes)"
    fi
    if [ -f "$DIR/baseline/members.tsv" ]; then
      echo "members: $(grep -vc '^#' "$DIR/baseline/members.tsv" || true)"
    fi
    if [ -f "$DIR/baseline/tests.tsv" ]; then
      echo "tests:   $(grep -c $'\tpass$' "$DIR/baseline/tests.tsv" || true) passing, $(grep -c $'\tfail$' "$DIR/baseline/tests.tsv" || true) failing"
    fi
    # A failing test in the baseline is a REGRESSION-FREE state only if something says why it fails.
    # Most of the time nothing has to: a failure whose stack reaches a type in the port's
    # `Substitutions.dropTypes` is DERIVED as expected from `run-latest/dropped-types.tsv`, which
    # PortRun regenerates every run. The warning below is for the residue — a failure no drop
    # explains and that is still a decision.
    # Matched WITHOUT a trailing tab, so `unexpected#stale-declaration` is caught by the same test:
    # a declared row whose ANCHOR stopped matching is the one shape here that already has a line in
    # the file and is still a failure nobody has read.
    if [ -f "$DIR/run-latest/test-failures.tsv" ] && grep -q $'\tunexpected' "$DIR/run-latest/test-failures.tsv"; then
      echo
      echo "NOTE: this baseline contains failing tests that NO SUBSTITUTION explains:"
      grep $'\tunexpected' "$DIR/run-latest/test-failures.tsv" | cut -f1,2 | sed 's/^/        /'
      echo "      Either they are regressions, or they are decisions — and a decision belongs in"
      echo "      baseline/expected-failures.tsv ('#suite<TAB>test<TAB>reason<TAB>frame=<class>',"
      echo "      '*' for a whole suite) with a reason someone can defend. Left unstated they read"
      echo "      as regressions that someone once accepted."
      echo "      A row marked 'unexpected#stale-declaration' ALREADY has a line: its anchor moved,"
      echo "      so what fails is not what the row is about — read the failure, then re-anchor it."
    fi
    echo
    echo "commit port-report/{{PORT}}/baseline/ with the change that produced it."

# ---------------------------------------------------------------------------------------------
# Upstream pin check — vendored trees vs reference repo submodules (CLAUDE.md §3.5 fourth question).
#
# Every vendored upstream tree this repo resolves against lives in sge's or ssg's `original-src/`
# submodule. A mismatch means the port is resolving against a DIFFERENT VERSION of the upstream
# than the reference hand port — and the errors that introduces read exactly like engine gaps
# (measured: three `E007`s on `OnscreenKeyboard.show(true)` from a single minor-version delta).
#
# The mapping is: the Justfile variable that names the vendored tree -> the submodule name in
# sge or ssg -> the commit the reference repo pins. The lane reads both sides and diffs them.
# A mismatch is FATAL (§13 decision table: "a mismatch is FATAL").
#
# WHY NOT RE-PIN AUTOMATICALLY: a re-pin changes what every port resolves against, which is a
# per-port measurement with its own baseline to acknowledge. The lane reports; the fix is manual.
# ---------------------------------------------------------------------------------------------
[doc("check every vendored upstream tree against the reference repo's submodule pin")]
upstream-pin:
    #!/usr/bin/env bash
    cd "{{root}}"
    ROOT="$(pwd)"
    echo "-- upstream pin check: vendored trees vs sge/ssg submodule pins --"
    echo
    MISMATCH=0
    TOTAL=0
    # Each row: label, vendored-tree-root (the git checkout), ref-repo, submodule-name
    while IFS='|' read -r label vendored refrepo submod; do
      TOTAL=$((TOTAL + 1))
      vendored_real=$(cd "$vendored" 2>/dev/null && pwd) || vendored_real="$vendored"
      # the vendored tree may be a subdirectory of the checkout; walk up to the .git
      vgit="$vendored_real"
      while [ -n "$vgit" ] && [ ! -d "$vgit/.git" ] && [ ! -f "$vgit/.git" ]; do
        vgit=$(dirname "$vgit")
        [ "$vgit" = "/" ] && vgit="" && break
      done
      if [ -z "$vgit" ]; then
        printf "%-20s  %-40s  NOT-A-GIT-REPO\n" "$label" "$vendored"
        MISMATCH=$((MISMATCH + 1))
        continue
      fi
      v_commit=$(git -C "$vgit" rev-parse HEAD 2>/dev/null || echo "UNKNOWN")
      # the reference repo's submodule pin
      ref_commit=$(git -C "$refrepo" submodule status "$submod" 2>/dev/null \
        | sed 's/^ *//' | cut -d' ' -f1 | tr -d '+' || echo "UNKNOWN")
      if [ "$v_commit" = "$ref_commit" ]; then
        printf "%-20s  %-40s  %s  MATCH\n" "$label" "$v_commit" "$ref_commit"
      else
        printf "%-20s  %-40s  %s  !! MISMATCH\n" "$label" "$v_commit" "$ref_commit"
        MISMATCH=$((MISMATCH + 1))
      fi
    done <<'PINS'
    libgdx|{{gdx_src}}|../sge|original-src/libgdx
    ashley|{{ashley_src}}|../sge|original-src/ashley
    simple-graphs|{{sg_src}}|../sge|original-src/simple-graphs
    anim8-gdx|{{anim8_src}}|../sge|original-src/anim8-gdx
    noise4j|{{n4j_src}}|../sge|original-src/noise4j
    jbump|{{jbump_src}}|../sge|original-src/jbump
    gdx-gltf|{{gltf_src}}|../sge|original-src/gdx-gltf
    screenmanager|{{screens_src}}|../sge|original-src/libgdx-screenmanager
    gdx-vfx|{{vfx_src}}|../sge|original-src/gdx-vfx
    gdx-ai|{{ai_src}}|../sge|original-src/gdx-ai
    textratypist|{{textra_src}}|../sge|original-src/textratypist
    vis-ui|{{visui_src}}|../sge|original-src/vis-ui
    liqp|{{liqp_src}}|../ssg|original-src/liqp
    flexmark-java|{{md_src}}|../ssg|original-src/flexmark-java
    PINS
    echo
    echo "checked $TOTAL vendored trees: $((TOTAL - MISMATCH)) match, $MISMATCH mismatch"
    if [ "$MISMATCH" != "0" ]; then
      echo "!! FATAL — $MISMATCH vendored tree(s) do not match the reference repo's pin."
      echo "   Re-pinning is a per-port decision with a measurement (PROGRESS.md §13)."
      exit 1
    fi

# ---------------------------------------------------------------------------------------------
# The drop-in gate — the EMITTED port replaces the HAND PORT inside the reference repo's own
# build, and the reference repo's own suite runs against it. This is the §13 "done bar" for
# every module: the emitted tree can be dropped into sge/ssg in place of the hand-ported files,
# and the full suite passes on every platform.
#
# WHY A WORKTREE OF THE REFERENCE REPO AND NOT THE LIVE CHECKOUT:
#   - the lane must not leave ../sge (or ../ssg) with files deleted or a broken build;
#   - the lane must be idempotent — re-running it must not fail because the previous run
#     already removed files;
#   - a worktree at a known commit is a reproducible starting point, and the commit is the one
#     the reference repo currently has checked out (HEAD), so the comparison is fair.
#
# WHY `unmanagedSourceDirectories` AND NOT COPYING FILES:
#   - the emitted tree is a BUILD PRODUCT (§5.5), regenerated by every engine change;
#   - copying into the worktree would mix emitted code with hand-written code and make
#     `git status` unable to distinguish the two (§5.5's diagnostic reason);
#   - `unmanagedSourceDirectories` is the sbt-projectMatrix idiom ../sge/build.sbt already uses
#     (~8 occurrences), so a `local-dropin.sbt` following that pattern is ordinary.
#
# WHAT IS REPLACED: every file whose header matches `Ported from <lib>` (sge spelling) or
# `Ported from: <path>` (ssg spelling). The census function classifies by header; everything
# else (SGE-original files, hand-added tests with no port header) stays in place.
#
# WHAT IS NOT REPLACED: platform-specific files (scalajvm/, scalajs/, scalanative/) that are
# SGE-original, and any test file that has no `Ported from` header — those are the module's own
# hand-written tests and are kept.
# ---------------------------------------------------------------------------------------------

# sge-ecs drop-in policy
ecs_dropin_ref       := "../sge"
ecs_dropin_module    := "sge-extension/ecs"
ecs_dropin_label     := "sge-ecs"
ecs_dropin_header    := "Ported from Ashley"
ecs_dropin_sbt_ids   := "sge-ecs sge-ecsJS sge-ecsNative"

[doc("sge-ecs drop-in: emitted port replaces hand port inside sge's own build")]
ecs-dropin:
    #!/usr/bin/env bash
    cd "{{root}}"
    ROOT="$(pwd)"
    . scripts/_lib.sh

    REF_REPO="$(cd {{ecs_dropin_ref}} && pwd)"
    MODULE_DIR="{{ecs_dropin_module}}"
    HEADER_PATTERN="{{ecs_dropin_header}}"
    PORT_MODULE="{{ashley_module}}"
    REPORT="$ROOT/port-report/AshleyDropIn"
    mkdir -p "$REPORT/run-latest"

    # ------------------------------------------------------------------
    # 1. Create/refresh a disposable clone of the reference repo
    # ------------------------------------------------------------------
    # WHY A CLONE AND NOT A WORKTREE: sbt-git uses jgit, which cannot resolve objects through a
    # worktree's `.git` file pointing at the parent's `.git/worktrees/<name>`. The error is
    # `MissingObjectException` on HEAD itself. A `--shared` clone creates a proper `.git/` that
    # jgit can read while sharing the object store with the original repo (no disk cost for
    # objects). Submodules are NOT cloned — the lane does not need them.
    DROPIN_DIR="$ROOT/.balticporter/dropin/sge"
    REF_COMMIT=$(git -C "$REF_REPO" rev-parse HEAD)
    echo "-- reference repo: $REF_REPO at $REF_COMMIT --"
    if [ -d "$DROPIN_DIR" ]; then
      CURRENT=$(git -C "$DROPIN_DIR" rev-parse HEAD 2>/dev/null || echo "NONE")
      if [ "$CURRENT" != "$REF_COMMIT" ]; then
        echo "   clone exists at $CURRENT, updating to $REF_COMMIT"
        git -C "$DROPIN_DIR" fetch origin 2>/dev/null
        git -C "$DROPIN_DIR" checkout --detach "$REF_COMMIT" 2>/dev/null || {
          echo "   checkout failed, re-creating clone"
          rm -rf "$DROPIN_DIR"
        }
      fi
      # Restore the working tree to a clean state: the previous run deleted files and wrote a
      # project/dropin.scala. Without this reset a re-run at the same commit finds 0 ported files.
      if [ -d "$DROPIN_DIR" ]; then
        git -C "$DROPIN_DIR" checkout -- . 2>/dev/null
        rm -f "$DROPIN_DIR/project/dropin.scala"
      fi
    fi
    if [ ! -d "$DROPIN_DIR" ]; then
      echo "   creating shared clone at $DROPIN_DIR"
      mkdir -p "$(dirname "$DROPIN_DIR")"
      git clone --shared --no-checkout "$REF_REPO" "$DROPIN_DIR"
      git -C "$DROPIN_DIR" checkout --detach "$REF_COMMIT"
    fi
    echo "   clone ready: $(git -C "$DROPIN_DIR" rev-parse --short HEAD)"

    # ------------------------------------------------------------------
    # 2. Census: classify every file in the module by header
    # ------------------------------------------------------------------
    echo
    echo "-- census: classifying files by header --"
    SRC_BASE="$DROPIN_DIR/$MODULE_DIR/src"
    PORTED_MAIN=0; PORTED_TEST=0; ORIGINAL_MAIN=0; ORIGINAL_TEST=0
    NEITHER_MAIN=0; NEITHER_TEST=0
    PORTED_FILES_MAIN=()
    PORTED_FILES_TEST=()

    classify_file() {
      local f="$1" scope="$2"
      if head -5 "$f" | grep -qi "$HEADER_PATTERN"; then
        if [ "$scope" = "main" ]; then
          PORTED_MAIN=$((PORTED_MAIN + 1))
          PORTED_FILES_MAIN+=("$f")
        else
          PORTED_TEST=$((PORTED_TEST + 1))
          PORTED_FILES_TEST+=("$f")
        fi
      elif head -5 "$f" | grep -qi 'Origin: SGE-original\|SGE-original\|Origin:.*SGE'; then
        if [ "$scope" = "main" ]; then ORIGINAL_MAIN=$((ORIGINAL_MAIN + 1))
        else ORIGINAL_TEST=$((ORIGINAL_TEST + 1)); fi
      else
        if [ "$scope" = "main" ]; then NEITHER_MAIN=$((NEITHER_MAIN + 1))
        else NEITHER_TEST=$((NEITHER_TEST + 1)); fi
      fi
    }

    # main sources: scala/, scalajs/, scalajvm/, scalanative/
    for subdir in scala scalajs scalajvm scalanative; do
      dir="$SRC_BASE/main/$subdir"
      [ -d "$dir" ] || continue
      while IFS= read -r -d '' f; do
        classify_file "$f" "main"
      done < <(find "$dir" -name '*.scala' -print0)
    done

    # test sources
    for subdir in scala scalajs scalajvm scalanative; do
      dir="$SRC_BASE/test/$subdir"
      [ -d "$dir" ] || continue
      while IFS= read -r -d '' f; do
        classify_file "$f" "test"
      done < <(find "$dir" -name '*.scala' -print0)
    done

    echo "main:  $PORTED_MAIN ported, $ORIGINAL_MAIN SGE-original, $NEITHER_MAIN other"
    echo "test:  $PORTED_TEST ported, $ORIGINAL_TEST SGE-original, $NEITHER_TEST other"
    echo "total: $((PORTED_MAIN + PORTED_TEST)) ported files to replace"

    if [ "$((PORTED_MAIN + PORTED_TEST))" = "0" ]; then
      echo "!! FATAL — 0 'Ported from' files found in $SRC_BASE"
      exit 1
    fi

    # ------------------------------------------------------------------
    # 3. Remove ported files, wire in emitted sources via local-dropin.sbt
    # ------------------------------------------------------------------
    echo
    echo "-- replacing ported files with emitted sources --"
    for f in "${PORTED_FILES_MAIN[@]}" "${PORTED_FILES_TEST[@]}"; do
      rm -f "$f"
    done
    echo "   removed $((PORTED_MAIN + PORTED_TEST)) ported file(s)"

    # Verify the emitted source exists
    EMIT_MAIN="$ROOT/$PORT_MODULE/src_managed/main/scala"
    EMIT_TEST="$ROOT/$PORT_MODULE/src_managed/test/scala"
    INJECT_MAIN="$ROOT/$PORT_MODULE/src/main/scala"
    INJECT_TEST="$ROOT/$PORT_MODULE/src/test/scala"
    if [ ! -d "$EMIT_MAIN" ]; then
      echo "!! FATAL — emitted main source not found at $EMIT_MAIN"
      echo "   Run the ashley-measure lane first to produce it."
      exit 1
    fi

    # Write a project/*.scala AutoPlugin that wires the emitted sources into the right projects.
    # An AutoPlugin is more reliable than a local-dropin.sbt for sbt-projectMatrix: the
    # `projectSettings` reach ALL platform variants (JVM/JS/Native) through the base directory
    # name match, which is the sbt-projectMatrix idiom ../sge/build.sbt already uses.
    mkdir -p "$DROPIN_DIR/project"
    dropin_scala="$DROPIN_DIR/project/dropin.scala"
    {
      echo 'import sbt._'
      echo 'import sbt.Keys._'
      echo ''
      echo 'object DropIn extends AutoPlugin {'
      echo '  override def trigger = allRequirements'
      echo ''
      echo '  override def projectSettings = Seq('
      echo '    Compile / unmanagedSourceDirectories ++= {'
      echo "      if (thisProject.value.base.getName.startsWith(\"sge-ecs\"))"
      echo "        Seq(file(\"$EMIT_MAIN\"))"
      echo "          ++ (if (file(\"$INJECT_MAIN\").isDirectory) Seq(file(\"$INJECT_MAIN\")) else Nil)"
      echo '      else Nil'
      echo '    },'
      echo '    Test / unmanagedSourceDirectories ++= {'
      echo "      if (thisProject.value.base.getName.startsWith(\"sge-ecs\"))"
      echo "        Seq(file(\"$EMIT_TEST\"))"
      echo "          ++ (if (file(\"$INJECT_TEST\").isDirectory) Seq(file(\"$INJECT_TEST\")) else Nil)"
      echo '      else Nil'
      echo '    }'
      echo '  )'
      echo '}'
    } > "$dropin_scala"
    echo "   wrote $dropin_scala to wire emitted sources"

    # ------------------------------------------------------------------
    # 4. Compile and test on each platform
    # ------------------------------------------------------------------
    echo
    # The JDK is an INPUT to this measurement: the frontend read its class files on ONE JVM and
    # the compile below runs on another. Nothing compared them until an `override` emitted on
    # JDK 24 failed a JDK-22 compile with every other artifact flat (ENGINE-LIMITS M5.10).
    jdk_guard "$ROOT/port-report/AshleyMigrate"
    echo "-- compile and test --"
    # Project ids: sge-ecs (JVM — no suffix), sge-ecsJS, sge-ecsNative. The JVM project does
    # not carry the `JVM` suffix because sbt-projectMatrix's `defaultAxes` includes `VirtualAxis.jvm`.
    PLAT_IDS="sge-ecs:jvm:JVM sge-ecsJS:js:JS sge-ecsNative:native:Native"
    ALL_OK=1
    BASELINE_FAILED=0
    for entry in $PLAT_IDS; do
      sbt_id=$(echo "$entry" | cut -d: -f1)
      platl=$(echo "$entry" | cut -d: -f2)
      plat=$(echo "$entry" | cut -d: -f3)
      LOG="$REPORT/run-latest/dropin-${platl}.log"
      echo
      echo "-- platform: $plat (sbt: $sbt_id/test) --"
      # Run sbt in the dropin clone
      (cd "$DROPIN_DIR" && sbt -batch "${sbt_id}/test" 2>&1) \
        | sed 's/\x1b\[[0-9;]*m//g' > "$LOG"
      SBT_STATUS=${PIPESTATUS[0]}
      # Count errors
      ERRORS=$(grep -cE '^\[error\]' "$LOG")
      # Count test outcomes from the munit markers
      PASS=$(grep -cE '^  \+ ' "$LOG")
      FAIL=$(grep -c '^==> X ' "$LOG")
      SKIP=$(grep -cE '^==> [^X] ' "$LOG")
      echo "  sbt exit: $SBT_STATUS  errors: $ERRORS  pass: $PASS  fail: $FAIL  skip: $SKIP"
      echo "  log: $LOG"
      # Write per-platform error count for baseline-accept
      echo "$ERRORS" > "$REPORT/run-latest/errors-count.dropin.${platl}"
      # Write a summary tests file per platform
      {
        echo "# platform=$plat  pass=$PASS  fail=$FAIL  skip=$SKIP"
        grep -E '^  \+ ' "$LOG" | sed 's/^  + //' | while read -r line; do
          echo -e "${line}\tpass"
        done
        grep '^==> X ' "$LOG" | sed 's/^==> X //' | while read -r line; do
          echo -e "${line}\tfail"
        done
        grep -E '^==> [^X] ' "$LOG" | sed 's/^==> [^ ]* //' | while read -r line; do
          echo -e "${line}\tskipped"
        done
      } > "$REPORT/run-latest/tests.${platl}.tsv"

      if [ "$SBT_STATUS" != "0" ]; then
        ALL_OK=0
        # Show the first few errors
        echo "  FIRST ERRORS:"
        grep -E '^\[error\]' "$LOG" | head -20 | sed 's/^/     /'
      fi

      # -- per-platform baseline comparison --
      local_expected="$REPORT/baseline/dropin/expected-errors.${platl}"
      if [ -f "$local_expected" ]; then
        expected_val=$(tr -dc '0-9' < "$local_expected")
        if [ "$ERRORS" = "$expected_val" ]; then
          echo "  errors vs baseline (dropin ${platl}): $ERRORS = $expected_val  (unchanged)"
        elif [ "$ERRORS" -gt "$expected_val" ]; then
          echo "!! dropin ${platl} ERRORS ROSE — $expected_val -> $ERRORS."
          BASELINE_FAILED=1
        else
          echo "!! dropin ${platl} ERRORS FELL — $expected_val -> $ERRORS. Acknowledge: just baseline-accept AshleyDropIn"
          BASELINE_FAILED=1
        fi
      else
        echo "  (no dropin error baseline for ${platl} — seed with: just baseline-accept AshleyDropIn)"
      fi

      local_tests_base="$REPORT/baseline/dropin/tests.${platl}.tsv"
      if [ -f "$local_tests_base" ]; then
        # Compare outcome counts — a test that moved pass->fail or appeared/disappeared is a change.
        base_pass=$(grep -c $'\tpass$' "$local_tests_base" || true)
        base_fail=$(grep -c $'\tfail$' "$local_tests_base" || true)
        if [ "$PASS" = "$base_pass" ] && [ "$FAIL" = "$base_fail" ]; then
          echo "  tests vs baseline (dropin ${platl}): pass=$PASS fail=$FAIL  (unchanged)"
        else
          echo "!! dropin ${platl} TESTS MOVED — pass: $base_pass -> $PASS, fail: $base_fail -> $FAIL."
          echo "   Acknowledge: just baseline-accept AshleyDropIn"
          BASELINE_FAILED=1
        fi
      else
        echo "  (no dropin test baseline for ${platl} — seed with: just baseline-accept AshleyDropIn)"
      fi
    done

    # ------------------------------------------------------------------
    # 4b. Discover scalacOptions from the reference repo
    # ------------------------------------------------------------------
    echo
    echo "-- scalacOptions --"
    (cd "$DROPIN_DIR" && sbt -batch "show sge-ecs/scalacOptions" 2>&1) \
      | sed 's/\x1b\[[0-9;]*m//g' | grep '^\[info\] \*' | sed 's/^\[info\] \* //' \
      > "$REPORT/run-latest/scalacOptions.txt"
    echo "scalacOptions: $(wc -l < "$REPORT/run-latest/scalacOptions.txt" | tr -d ' ') flags recorded"

    echo
    echo "=================================================================="
    echo "DROP-IN SUMMARY for {{ecs_dropin_label}}"
    for entry in $PLAT_IDS; do
      platl=$(echo "$entry" | cut -d: -f2)
      plat=$(echo "$entry" | cut -d: -f3)
      LOG="$REPORT/run-latest/dropin-${platl}.log"
      ERRORS=$(grep -cE '^\[error\]' "$LOG")
      PASS=$(grep -cE '^  \+ ' "$LOG")
      FAIL=$(grep -c '^==> X ' "$LOG")
      SKIP=$(grep -cE '^==> [^X] ' "$LOG")
      printf "  %-8s  errors=%-4s  pass=%-4s  fail=%-4s  skip=%-4s\n" "$plat" "$ERRORS" "$PASS" "$FAIL" "$SKIP"
    done
    echo "=================================================================="
    if [ "$BASELINE_FAILED" = "1" ]; then
      echo "!! this lane FAILED its drop-in baseline — see the per-platform lines above"
      exit 1
    fi
    if [ "$ALL_OK" != "1" ]; then
      echo "(expected red — the emitted API is not yet at parity)"
    fi

# ---------------------------------------------------------------------------------------------
# The divergence census — every non-surface-only difference between the emitted port and the
# hand port, enriched with header evidence and joined against a committed verdict file.
# The census feeds the divergence-investigator agent (one row per invocation), and its verdicts
# are the durable record of whether a hand-port adjustment was justified.
#
# NOT in measure-all: the census reads api-parity findings (which measure-all produces) and
# does not re-emit anything. NOT in dropin-all: it does not replace files.
# ---------------------------------------------------------------------------------------------

# sge-ecs divergence policy
ecs_divergence_verdicts := "ported/sge-ecs/divergence-verdicts.tsv"

[doc("sge-ecs divergence census: every hand-port adjustment, enriched with evidence")]
ecs-divergence:
    #!/usr/bin/env bash
    cd "{{root}}"
    ROOT="$(pwd)"
    . scripts/_lib.sh

    REF_REPO="$(cd {{ecs_dropin_ref}} && pwd)"
    MODULE_DIR="{{ecs_dropin_module}}"
    REPORT="$ROOT/port-report/AshleyMigrate"
    VERDICT_FILE="$ROOT/{{ecs_divergence_verdicts}}"

    echo "-- sge-ecs divergence census --"
    echo "   reference repo: $REF_REPO"
    echo "   verdict file:   $VERDICT_FILE"
    echo

    divergence_census "$REPORT" "$REF_REPO" "$MODULE_DIR" \
      "{{ecs_dropin_header}}" "$VERDICT_FILE" "sge-ecs"

    echo
    divergence_baseline_guard "$REPORT"

# The drop-in aggregator — all drop-in lanes, NOT in measure-all.
[doc("every drop-in lane — NOT in measure-all (expected red)")]
dropin-all:
    #!/usr/bin/env bash
    cd "{{root}}"
    for lane in ecs-dropin; do
      echo
      echo "################################################################## just $lane"
      {{just_executable()}} "$lane" || echo "!! $lane exited non-zero (expected for a red drop-in)"
    done
