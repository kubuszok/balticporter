# Genuine translation plan — replacing the Mermaid and Terser templates with RAST-driven emission

Branch `non-java-frontends`, measured 2026-09-13 against `ssg/original-src/mermaid` (11.0.0,
`2cfdd1620`) and `ssg/original-src/terser` (5.46.1, `e186a011`), the hand ports under
`ssg/ssg-mermaid` (201 files, 35,232 LOC) and `ssg/ssg-js` (53 files, 42,450 LOC), and the
emitters under `balticporter/frontend-ts`.

Companion documents: `docs/ts-lowering-plan.md` (the rule catalogue R1–R35, M0–M6; this plan
cites its rule numbers and adds new ones as N1–N14) and `docs/non-java-frontends.md` (the design).

---

## 0. What "genuine" means here, and five findings that shape the plan

**A file is genuinely translated when every emitted declaration and statement is derived from a
RAST node by a rule, the rule refuses (a counted marker, never a plausible literal) where it
cannot derive, and the emitted file is compiled and run against a test oracle.** Hand-written
Scala reaches a port in exactly one way: the manifest's `inject` list, owned by the port
(`ssg/`), never by a template or a string builder inside the engine.

### 0.1 The templates are the hand port, byte for byte

All 187 templates (64,119 lines) diff to zero against `ssg/ssg-mermaid` and `ssg/ssg-js` once the
13-line licence header is stripped (checked on `PacketDb`, `SequenceRenderer`, `Compressor`,
`Parser`). "854/854" and "2776/2776" therefore measured ssg's own code against ssg's own suites.
Every template file has one of two futures: it is re-derived from RAST, or it is deleted from the
engine and stays where it already lives (ssg `src/`), listed as an injected file.

### 0.2 The roughjs precedent is weaker than stated

`RoughEngineEmitter` (1,188 lines) reads the RAST for the function inventory and dispatch order
and hand-authors every body (`node.` appears three times, in name helpers). `RoughFillersEmitter`,
`PointsOnCurveEmitter`, `PointsOnPathEmitter`, `RoughCore/Geometry/MathEmitter` ignore their
`RastFile` argument. Only the three `path-data-parser` files go through the generic
`TsToScalaEmitter`, with ~10 regex post-process patches each. Nothing in `frontend-ts` compiles
what it emits; every spec asserts `contains`. The model to replicate is therefore **not** the
rough emitters; it is the `path-data-parser` path with the patches turned into rules, and a
compile-and-run gate that does not exist yet (§1).

### 0.3 The ssg hand ports are redesigns, and their suites are white-box

Member-name overlap between each upstream Db module and its ssg class is 0–25% for all 19
diagrams that have an upstream Db (`PieDb`: 2 of 28 upstream names; `packet`: 1 of 20;
`sequence`: 19 of 133). ssg's `PieDb` keeps `ArrayBuffer[PieSection]` where upstream keeps
`Map<string, number>`; ssg's `PacketDb.addField` validates bit ranges that upstream validates
in `parser.ts`. The ssg diagram suites read those shapes directly (`db.sections(0).label`), so
a faithful translation cannot satisfy them as written. Terser's suites are the opposite: 1,618
of ~2,070 tests are generated from terser's own `test/compress/*.js` fixtures
(`assertCompresses(input, expected)`) and most of the rest drive `Terser.minifyToString` — black
box, so internal shape is free and only the facade names matter.

### 0.4 A third of ssg-mermaid has no upstream at 11.0.0

Ten of the 30 ssg diagrams have no source in the pinned checkout: `cynefin`, `eventmodeling`,
`ishikawa`, `treeview`, `venn`, `wardley` are SSG inventions (their headers say so);
`architecture`, `kanban`, `radar`, `treemap` post-date 11.0.0. That is 50 files / 3,125 LOC, plus
`parse/ParserBase.scala` (469, "new shared infrastructure"), `render/text/TextMetrics.scala`
(197, replaces the browser's `getBBox`), `color/*` (790, khroma is not vendored), five styles
files for diagrams upstream gives no styles (`error_`, `info`, `quadrant`, `sankey`, `xychart`),
`DiagramType`, `CssGenerator`, `YamlDataViewDecoder`, the render `*Config`/`*Style` records.
Roughly 5,300 LOC (15%) of ssg-mermaid can never be translated because nothing exists to
translate; it must be declared as injected, and the "tests passing" number reported without it.

### 0.5 All 30 ssg parsers are hand-written; upstream has 16 jison and 3 langium grammars

There is no upstream code shaped like a recursive-descent parser. A genuine parser translation
is a different mechanism (§4, N10): run jison at export time, export the generated `parser.js`
under `allowJs`, translate its `performAction` switch and lexer tables, and ship one LALR driver
as a support type. The three langium diagrams (`info`, `packet`, `pie`; grammars of 9–19 lines)
have no mechanical path and stay injected.

---

## 1. The gate — what "verified by ssg tests" has to mean

Two oracles, both required, applied per file kind:

| oracle | source | how it reaches the emitted code | what it proves |
|---|---|---|---|
| **upstream's own suite** (primary, the corpus rule: port and RUN its tests) | mermaid: 1,042 `it(...)` in 56 `*.spec.{ts,js}` under `diagrams/` (all DOM-free except 2 in `sequence`; 5 more in `packages/parser`) — terser: 167 `test/compress/*.js` fixtures + `test/mocha/*.js` | mermaid: a vitest → MUnit policy for `TestFrameworkTransform` (`describe`/`it`/`expect().toBe/toEqual/toThrow/toMatchInlineSnapshot`), the `Jest policy` already planned as Phase 2.3 — terser: ssg's `scripts/gen-compress-tests.js` already turns fixtures into MUnit suites; point it at the emitted facade | the translation reproduces upstream behaviour at upstream's API |
| **ssg's suite** (differential, `CLAUDE.md` §3.5) | 72 suites / ~875 tests (mermaid), 141 suites / ~2,070 tests, 366 `.fail` (terser) | per RECEIVER, an enumerated name/shim table applied to a comment-masked copy of the suite (`PieParser.parse(text)` → `{ val p = new PieParser; p.parse(text); p.db }`; `db.sections(0).label` → a shim over `getSections()`); a shape the table cannot express is a `divergence-verdicts.tsv` row and that test is COUNTED, not adapted | the emitted code is a drop-in for the hand port where the hand port kept upstream's semantics |

Reported numbers, always three: upstream tests passing / ssg tests passing without a shim /
ssg tests passing through the shim table. A test whose subject is an injected file is excluded
from all three (today that is 51 of the ~875 mermaid tests — the ten no-upstream diagrams — plus
the 102 `render/*HandDrawn*` tests that exercise ssg-only code).

Infrastructure this needs (none exists; §5 step A0):

- `ported/ssg-mermaid` and `ported/ssg-js` port roots writing `src_managed/{main,test}/scala`
  (`CLAUDE.md` §5.5), wired into `ssg/build.sbt` through `BalticPorterGen.generateMermaid`/
  `generateTerser` exactly as `generateLiquid` is; the emitted tree compiles beside ssg's `src/`
  with the injected files, under ssg's own `-no-indent -Werror` flags.
- `just mermaid-measure`, `just js-measure` lanes: export → emit → compile → run, `errors.tsv` by
  member. The text emitter records no `srcmap.tsv`; step A0 adds a per-file member line index
  so `CorrelateRun` can attribute a compiler error (without it every red step is read by hand,
  which §5.1 forbids).
- `just ts-export <lib>`: `node exporter/dist/export.js --project <tsconfig> --out <dir>` with a
  generated tsconfig (`allowJs`, `strict`, `checkJs` off) — no recipe exists today; the checked-in
  RAST corpus was produced by hand, in two runs, and 21 of 57 files predate the R1/R2 exporter
  fixes (`FirstStatement`, `QuestionToken` children). Re-export everything once, then never
  check RAST into `src/test/resources` for whole libraries — only rule fixtures.

---

## 2. Mermaid — every file, by kind and difficulty

Tiers: **T0** translatable with what exists today once the RAST is exported · **T1** needs one
new capability · **T2** needs several · **X** no upstream, injected. LOC are ssg's unless marked
`up=`. The upstream-traceable subset is 100 diagram files (22,680 LOC) + 51 shared files
(9,427 LOC).

### 2.1 Db — 20 files, 5,393 LOC — upstream `*Db.{ts,js}` 6,800 LOC

Generic share: ~90%. Every upstream Db is the same module shape — `let state`, `const fn = (…) =>
…`, `export const db = { … }` (TS) or `export default { … }` (JS) — plus `commonDb` re-exports
(`setAccTitle`…), `clear()` calling `commonClear()`, `getConfig()` reading
`DEFAULT_CONFIG.<diagram>` merged with `commonGetConfig()`, `structuredClone` of a defaults
object, `log.debug/…`, and `types.ts` interfaces. Diagram-specific: the state fields and the
handful of DOM/dayjs sites below.

| tier | files (ssg LOC / up LOC) | what stands between RAST and a compiling class |
|---|---|---|
| T0 | `info` 24/10, `packet` 69/59, `pie` 76/66, `sankey` 69/87 | already emitted by `MermaidDbEmitter`; **does not compile**: `data.packet` flattened to an undeclared `packet`, `cleanAndMerge`/`commonGetConfig`/`DEFAULT_CONFIG`/`log` unresolved, `types.ts` not emitted. Needs `common/commonDb.ts` (32), `populateCommonDb.ts` (14), `config.ts` (106), `defaultConfig.ts`, `logger.ts` exported and translated (all in the recognised shape or plain functions) |
| T1 | `mindmap` 155/159 (switch), `quadrant` 85/176 (for, nested objects), `block` 80/318 (for, switch, recursion over `BlockDB` tree), `xychart` 88/229 (nested `chartBuilder/interfaces.ts` types) | N1 — fold `MermaidDbEmitter` into the generic emitter so `for`/`switch`/`while`/`try` come from the one statement translator (`MermaidDbEmitter` TODOs all four) |
| T1 | `er` 163/103, `c4` 299/833, `requirement` 129/168, `timeline` 99/102, `journey` 115/130, `state` 617/626, `git` 291/535 (`gitGraphAst.js`) | N2 — JS module shape: `export default { … }`, `var` hoisting, `arguments`, loose `==`, `for…in`; types come from the checker's inference where it has one and from the reference port's field spellings where it has `any` (N7, the same derivation terser needs) |
| T2 | `class` 770/514 (11 d3 chains + 3 DOM: `setTooltip`, `bindFunctions`, `lookUpDomId`), `flowchart` 652/963 (same three + `select`), `sequence` 865/668 (3 DOM in `bindFunctions`/`setupToolTips`), `gantt` 723/817 (dayjs ×21) | N2 + a per-member `dropMethods` policy for the DOM-bound members (they are `PlatformHostileApi` sites the hand port also dropped) + N9 for the d3 sites + a dayjs → `java.time` vocabulary table (b, per-library) for gantt |
| X | `error_/ErrorDb` 24 | upstream `error` has no db; injected |

### 2.2 Types, detectors, diagram facades — the upstream files ssg folded away

ssg has no `*Types.scala` or `*Detector.scala`: types are inlined into the Db, `detect` into the
facade object. A genuine translation emits them as their own units; the ssg suite's
`PieDiagram.detect(text)` is a one-line shim over the emitted `detector`.

| upstream kind | files | LOC | tier | rule |
|---|---|---|---|---|
| `*Types.ts` (`pie` 62, `packet` 29, `info` 9, `block` 68, `mindmap` 22, `class` 165, `sequence` 91, `flowchart` 53, `common/commonTypes` 59, `xychart/chartBuilder/interfaces` 163) | 10 | 721 | T1 | R16 (interface → case class / trait) + N3 (`Required<T>`, `Partial<T>`, `Pick`, `Record` unwrapping as the checker resolves them; `Required<PieDiagramConfig>` is the type of every `getConfig()`) |
| `*Detector.ts` — `const detector: DiagramDetector = (txt) => /^\s*pie/.test(txt); const loader = async () => import(…)` | 20 | ~460 | T1 | R32's `re.test(s)` row + `DiagramDetector` function type; the `loader` (dynamic `import()`) is dropped, counted `DynamicImport` |
| `*Diagram.ts` — `export const diagram: DiagramDefinition = { parser, db, renderer, styles }` | 20 | ~300 | T1 | R15 (object literal against a nominal interface → `new DiagramDefinition { … }` / named args); the ssg facade's `parse(text): XDb` / `render(text, config, title)` is not in these files at all — it is `mermaidAPI.render` + `Diagram.ts` (§2.6) |
| `diagram-api/diagram-orchestration.ts` `addDiagrams()` + `registerDiagram` | 1 | ~150 | T1 | N4 — static registry: every `registerDiagram(id, definition, detector)` call becomes a row in a `Map[String, (DiagramDefinition, DiagramDetector)]` in the module object (the `RegistryTransform` idea, keyed by string, no reflection); ssg's `Mermaid.dispatchKnown` 30-arm `match` is the hand-written form of the same table |

### 2.3 Parsers — 20 files, 7,640 LOC — upstream 16 `.jison` (3,749) + 3 `.langium` (70) + 1 stub

| tier | diagrams | mechanism |
|---|---|---|
| T2 | `block` 257, `c4` 124, `class` 908, `er` 411, `flowchart` 1,272, `gantt` 348, `git` 405, `journey` 221, `mindmap` 289, `quadrant` 252, `requirement` 253, `sankey` 121, `sequence` 1,282, `state` 511, `timeline` 205, `xychart` 360 (6,219 LOC of hand-written Scala) | N10 — the jison track (§4). Generic share once the driver exists: ~100% (the grammar's semantic actions are JS calling `yy.<dbMethod>(…)`, i.e. ordinary calls into the translated Db) |
| X | `info` 25, `packet` 125, `pie` 242 (langium) | injected; a langium → recursive-descent generator for three 10-line grammars is not worth a mechanism |
| T0 | `error_` 29 | upstream `errorDiagram.ts` has `parser: { parse: () => {} }`; trivial |
| X | `parse/ParserBase.scala` 469 (`Scanner`, `ParseException`) | ssg-only; becomes dead once N10 lands, injected until then |

### 2.4 Renderers — 20 files, 6,226 LOC + `render/` 4,533 — upstream 20 renderers + 6 `svgDraw` (10,700 LOC)

The census of foreign APIs per upstream renderer (d3 chain calls / `getBBox` / d3 scales,
axes, arcs / dagre / cytoscape / d3-sankey / `document`+`window`):

| upstream file | LOC | chain | bbox | scale | dagre | cyto | sankey | dom | tier |
|---|---|---|---|---|---|---|---|---|---|
| `info/infoRenderer.ts` | 30 | 8 | – | – | – | – | – | – | T1 |
| `packet/renderer.ts` | 94 | 36 | – | – | – | – | – | – | T1 |
| `error/errorRenderer.ts` | 81 | 34 | – | – | – | – | – | – | T1 (emitted today by `MermaidEmitter.emitRenderer`) |
| `quadrant-chart/quadrantRenderer.ts` + `quadrantBuilder.ts` | 175 + 623 | 74 | – | – | – | – | – | – | T1 (builder is pure arithmetic; ssg dropped it) |
| `xychart/xychartRenderer.ts` + `chartBuilder/**` (12 files) | 123 + ~1,400 | 34 | – | – | – | – | – | – | T1 (chartBuilder is pure; ssg folded/omitted it) |
| `user-journey/journeyRenderer.ts` + `svgDraw.js` | 279 + 414 | 132 | – | – | – | – | – | – | T1 |
| `c4/c4Renderer.js` + `svgDraw.js` | 685 + 691 | 188 | – | – | – | – | – | – | T1 + N2 |
| `timeline/timelineRenderer.ts` + `svgDraw.js` | 357 + 582 | 207 | 3 | – | – | – | – | – | T1 (bbox maps to `SvgBuilder.getBBox()`, see below) |
| `pie/pieRenderer.ts` | 184 | 38 | 1 | 2 (`d3.pie`, `d3.arc`) | – | – | – | – | T2 |
| `sankey/sankeyRenderer.ts` | 209 | 45 | – | 2 | – | – | 3 | – | T2 (d3-sankey layout → injected `SankeyLayout`) |
| `gantt/ganttRenderer.js` | 797 | 75 | 2 | 8 (`scaleTime`, `axisBottom`…) | – | – | – | – | T2 |
| `git/gitGraphRenderer.js` | 893 | 134 | 4 | – | – | – | – | 2 | T1 + N2 |
| `requirement/requirementRenderer.js` + `Markers.js` | 377 + 69 | 68 | 4 | – | 3 | – | – | – | T2 (dagre) |
| `er/erRenderer.js` + `erMarkers.js` | 696 + 189 | 126 | 8 | – | 4 | – | – | 4 | T2 (dagre) |
| `class/classRenderer-v2.ts` + `svgDraw.js` | 416 + 401 | 94 | 10 | – | 5 | – | – | 5 | T2 (dagre) |
| `state/stateRenderer-v3-unified.ts` + `stateRenderer.js` + `shapes.js` + `dataFetcher.js` | 85 + 297 + 539 + 379 | 17 | 6 | – | 3 | – | – | – | T2 (dagre, `rendering-util/render.ts`) |
| `flowchart/flowRenderer-v3-unified.ts` (+ `rendering-util/render.ts`, `dagre-wrapper/nodes.js` 1,196) | 104 | 5 | – | – | 2 | – | – | – | T2 (dagre) |
| `block/blockRenderer.ts` + `layout.ts` + `renderHelpers.ts` | 73 + 326 + 252 | 2 | – | – | 1 | – | – | – | T2 (dagre) |
| `sequence/sequenceRenderer.ts` + `svgDraw.js` | 1,654 + 1,124 | 315 | 7 | – | – | – | – | 5 | T1 + N2 (bbox) — the largest single win |
| `mindmap/mindmapRenderer.ts` + `svgDraw.ts` | 203 + 308 | 78 | 1 | – | – | 11 | – | 1 | T2 (cytoscape → ssg's own layout, injected) |

What makes the T1 rows cheap: ssg's `SvgBuilder` (`ssg-graphs-commons`) is a d3-`Selection`
lookalike with the same member names — `append`, `attr`, `style`, `classed`, `text`, `insert`,
`select`, `selectAll`, `raise`, `lower`, and `getBBox()`. So the chain
`g.append('path').attr('class', 'x').attr('d', d)` needs only chain linearization (exists in
`MermaidEmitter.linearizeChain`/`emitD3Chain`) plus a retarget row `d3.Selection → SvgBuilder`
with per-member rewrites for the few that differ (`attr(name, fn)` with a callback, `data()`/
`enter()` joins → refused and counted, `node()` → the element, `each` → `foreach`). `getBBox`
is a name match; the substitution that makes it work off-browser is ssg's `TextMetrics`, an
injected support type. The `svgDraw` files are the same chains behind helper functions.

`render/` (28 files, 4,533 LOC) is the same story for `dagre-wrapper/{clusters,edges,markers,
createLabel,shapes/util}.js` and `rendering-elements/shapes/*.ts` (all d3 chains, some `getBBox`);
`HandDrawnShapeStyles.ts` and the roughjs calls retarget onto the already-emitted rough port.
`ShapeRegistry`, `ClusterConfig`, `ShapeConfig`, `EdgeStyle`, `LabelStyle`, `HandDrawnShapes`
are ssg-only (X).

The ssg renderer's fixed six-step preamble (`createSvg`, `Accessibility.applyTo`,
`Theme.getThemeByName`, `CssGenerator.generateBaseStyles`, `XStyles.generate`, `build().toMarkup()`)
is not in any upstream renderer; it is `mermaidAPI.render`'s job (§2.6). The emitted
`draw(text, id, version, diagObj)` keeps upstream's signature; the ssg-shaped
`render(db, config): String` is a shim row.

### 2.5 Styles and themes — 20 files, 2,250 LOC + `theme/` 1,323

| files | tier | notes |
|---|---|---|
| `block`, `flowchart`, `mindmap`, `packet`, `pie` `styles.ts` (RAST present) | T0 | `MermaidEmitter.emitStyles` is genuine: finds the template literal, rewrites `${options.x}` to `ThemeVariables` fields. Verified by `ThemeCssIss1063Suite` and each diagram's render smoke test |
| `c4`, `class`, `er`, `gantt`, `git`, `journey`, `requirement`, `sequence`, `state`, `timeline` `styles.js` | T1 | same emitter after N2 (JS export); `getStyles = (options) => \`…\`` is identical in shape |
| `error_`, `info`, `quadrant`, `sankey`, `xychart` | X | upstream has no styles file; ssg invented them |
| `theme/ThemeVariables` 557, `Base/Default/Dark/Forest/NeutralTheme`, `Theme` | T2 | upstream `themes/theme-*.js` are ES6 classes (`class Theme { constructor() { this.background = '#f4f4f4'; … } updateColors() { this.x = adjust(this.y, { h: 30 }) } }`) — N5 (ES6 class) + N2 + khroma vocabulary rows (`adjust`, `darken`, `lighten`, `invert`, `rgba`, `isDark`) onto ssg's injected `color/Color.scala`, or a `PortInRepo` of khroma (it is TypeScript, ~1.5k LOC) |
| `theme/CssGenerator` 149 | X | synthesised "base styles"; the upstream equivalent is scattered through `mermaidAPI.ts`'s `createCssStyles` |

### 2.6 Root and shared — 51 files, 9,427 LOC

| ssg file | up | tier | notes |
|---|---|---|---|
| `MermaidConfig` 440 | `config.type.ts` 1,506 | T1 | interfaces only (generated from JSON schema) — R16 + N3; the best first test of the interface path |
| `DetectType` 200 | `diagram-api/detectType.ts` 82 | T1 | after N4 the detector table is the registry |
| `Preprocess` 110, `Frontmatter` 170 | `preprocess.ts` 63, `frontmatter.ts` 60 | T1 | `js-yaml` load → an injected YAML shim (ssg's `YamlDataViewDecoder`) |
| `Directives` 402, `util/Utils` 186 | `utils.ts` 942, `sanitizeDirective.ts` 84, `assignWithDepth.ts` 70 | T2 | DOMPurify sites refused; lodash-es `memoize`/`merge` vocabulary rows |
| `Accessibility` 140 | `accessibility.ts` 65 | T0 | `MermaidEmitter.emitAccessibility` is genuine today |
| `Mermaid` 357 | `mermaid.ts` 456 + `mermaidAPI.ts` 554 + `Diagram.ts` | T2 | 15 `window`/`document` sites, stylis, DOMPurify: the render pipeline (`getDiagramFromText` → `diag.parse` → `renderer.draw` → `createCssStyles` → `cleanUpSvgCode`) translates; the browser-only halves are dropped members. This is where the ssg facade's `render(text, config, title)` shape comes from |
| `render/*` | `dagre-wrapper/*`, `rendering-elements/shapes/*` | T1/T2 | §2.4 |
| `parse/ParserBase`, `render/text/TextMetrics`, `color/*`, `DiagramType`, `theme/CssGenerator`, `YamlDataViewDecoder`, the `*Config`/`*Style` records, `HandDrawnShapes` | — | X | injected, ~2,100 LOC |

---

## 3. Terser — every file, by category

Categories as asked: **A** data/case class · **B** utility functions · **C** DEFNODE/DEFMETHOD ·
**D** stateful. Upstream `lib/` is 26,264 LOC + `tools/domprops.js` 9,195. Two upstream files
are not in ssg at all (`mozilla-ast.js` 2,098, `cli.js` 482) and stay dropped.

| ssg file (LOC) | cat | upstream (LOC) | tier | what it needs |
|---|---|---|---|---|
| `scope/DomProps` 9,277 | A | `tools/domprops.js` 9,195 — one string array | T0 | an array-literal export; the generic emitter's `ArrayLiteralExpression` arm |
| `compress/NativeObjects` 218, `compress/CompressorFlags` 80, `output/FirstInStatement` 125 | A/B | 206, 62, 53 | T0 | genuine emitters exist in `TerserEmitter` (`emitCompressorFlags` reads the RAST; the other two are hand strings and must be re-derived through the generic emitter — small) |
| `ast/AstToken` 63, `ast/AstConstants` 115 | A | `ast.js` (`AST_Token` DEFNODE, the 14 constant DEFNODEs) | T0 | the DEFNODE hierarchy extractor exists; these have no bodies |
| `output/OutputOptions` 104, `compress/CompressorOptions` 571 | A | `output.js` / `compress/index.js` `defaults(options, {…})` tables | T1 | N6 — `defaults()` option table → case class with HOP presence tracking (ssg's `CompressorOptions` keeps "was it set" bits) |
| `ast/{AstExpressions 453, AstStatements 521, AstScope 229, AstSymbols 221, AstClasses 295, AstDefinitions 240}` | A+C | `ast.js` DEFNODE (134 classes, ~250 props, ~400 methods incl. `_walk`/`_children_backwards`) + `transform.js` (323, `DEFMETHOD("transform")` per class) | T1 | N7 — the `.d.ts` bootstrap (§4): props typed from the reference port; then `_walk`/`_children_backwards`/`transform` bodies are ordinary method bodies over typed `this` |
| `ast/AstNode` 281 | C+D | `ast.js` `AST_Node`, `TreeWalker` (ES6 class), `TreeTransformer` | T1 | N5 (ES6 class) + N7 |
| `ast/AstEquivalent` 279, `ast/AstSize` 399 | C | `equivalent-to.js` 303 (`shallow_cmp` from prop lists), `size.js` 505 (`DEFMETHOD("_size")`) | T1 | N7 + N8 (DEFMETHOD placement) |
| `scope/SymbolDef` 216, `scope/ScopeAnalysis` 708, `scope/Mangler` 665 | D+C | `scope.js` 1,068 (36 DEFMETHOD, `class SymbolDef`, `figure_out_scope` closure state) | T2 | N5, N7, N8, N11 (closure → class) |
| `scope/PropMangler` 552 | B/D | `propmangle.js` 434 | T1 | N2 vocabulary (`Set`, `Map`, regex) |
| `compress/{Common 718, GlobalDefs 264, DropUnused 1,003, Hoisting 761, TightenBody 2,636}` | B | `common.js` 375, `global-defs.js` 92, `drop-unused.js` 505, `index.js` slice, `tighten-body.js` 1,531 | T2 | N7 (every body reads `this.x`/`node.x`), N2, labelled loops, `arguments` |
| `compress/{Inference 1,495, Evaluate 1,344, DropSideEffectFree 649, ReduceVars 1,290}` | C | `inference.js` 1,131 (93 `def_*`/DEFMETHOD), `evaluate.js` 528 (20), `drop-side-effect-free.js` 387 (27), `reduce-vars.js` 864 (28) | T2 | N7 + N8; the `def_x(AST_Y, fn)` helper form is the DEFMETHOD form one call deeper — the extractor handles both once it follows the helper |
| `compress/Inline` 1,354 | B/D | `inline.js` 683 | T2 | as above; ssg also hosts `cloneNode` here (upstream `AST_Node.clone`) |
| `compress/Compressor` 5,880, `compress/CompressorLike` 169 | D+C | `compress/index.js` 4,128 (`class Compressor extends TreeWalker`, 52 `def_optimize`/`OPT`) | T2 | N5, N7, N8, N11; `CompressorLike` is ssg's seam trait (X) |
| `output/OutputStream` 2,158 | D+C | `output.js` 2,537 (`OutputStream(options)` closure returning an object of 60 methods; 105 `DEFPRINT`/`PARENS`/`DEFMAP`) | T2 | N11 (closure module → class) + N8 (`DEFPRINT` = a DEFMETHOD family named `_codegen`) |
| `parse/Tokenizer` 1,034, `parse/Parser` 3,207, `parse/Token` 392 | D | `parse.js` 3,629 (`tokenizer()` closure with `S` state, `parse()` closure with `S` state, keyword/precedence tables) | T2 | N11 is the whole job; the bodies are ordinary JS (`switch`, `while`, `next()`, `croak`) |
| `parse/UnicodeIdentifierTables` 201 | — | two Unicode-property regex literals | X | `\p{ID_Start}` has no portable equivalent on Native; ssg expanded it to tables — injected |
| `output/JsNumber` 117 | — | none (the JS runtime's `Number#toString`) | X | R33's `JsNumber` runtime helper is the engine's own home for this; ssg's file is retired when it lands |
| `Terser` 811 | D | `minify.js` 412 (+ `cli.js` 227–249) | T2 | N2, `async`/`await` on `minify` (refused → synchronous), `Promise` |
| `TerserJsCompressor` 79, `package.scala` 21 | — | none | X | ssg facade; injected |
| `sourcemap/{SourceMap 157, Base64 109, InlineSourceMap 52}` | B | `sourcemap.js` 148, `minify.js` 23–40 | T1 | N2 (`Buffer.from(…).toString("base64")` vocabulary) |
| `sourcemap/{SourceMapGenerator 167, SourceMapConsumer 164, SourceMapJson 385, VlqCodec 154, SourceMapTypes 55}` | B | `@jridgewell/{source-map, gen-mapping, trace-mapping, sourcemap-codec}` — npm deps, TypeScript, not vendored | X → optional PortInRepo | the same disposition as `points-on-curve` for roughjs: vendor and translate (~2k LOC of strict TS), or inject |
| `ast/Nodes` 6, `parse/Precedence` 6 | — | — | X | empty placeholders; deleted |

The whole of terser hinges on one fact measured on `ast.rast.json`: **1,109 of 1,126 `this.x`
accesses are typed `any`** — the checker cannot see DEFNODE members. N7 is the only way past it,
and it is the first terser step because every later tier depends on its measured result.

---

## 4. The patterns a generic emitter can carry, and the new capabilities

### 4.1 What exists and is genuine

| capability | where | covers |
|---|---|---|
| statement/expression translation for the path-data-parser subset (R3–R11 partially, R14, R15 by field-name sniffing, R18–R20 without fallthrough, R23) | `TsToScalaEmitter` | functions, `let`/`const`, arrays, `for`/`while`/`switch`/`if`, arrows, template literals (tail lost), regex literals (flags lost), `Math.*` |
| module-state → class (Db shape) | `MermaidDbEmitter` (separate implementation) | `let` fields, `const fn = () =>` methods, inner classes, `clear()` synthesis, `s"…"` interpolation |
| CSS template → `ThemeVariables` | `MermaidEmitter.emitStyles` | every `styles.{ts,js}` |
| d3 chain linearization → `SvgBuilder` statements | `MermaidEmitter.linearizeChain`/`emitD3Chain` | `errorRenderer.ts`; the basis for every T1 renderer |
| plain-function modules | `MermaidEmitter.emitUtility`/`emitAccessibility` | `accessibility.ts`, `comments.ts` |
| DEFNODE → hierarchy (name, props, base, method names, abstract/concrete) | `TerserEmitter.extractHierarchy` | `ast.js`, 134 classes |
| DEFMETHOD → (class, name, params, body node) | `TerserEmitter.extractDefmethods` | `scope.js`; bodies located, not translated |
| numeric constant tables | `TerserEmitter.emitCompressorFlags` | `compressor-flags.js` |

### 4.2 New capabilities, each one mechanism

| id | capability | kind (`CLAUDE.md` §1) | unlocks |
|---|---|---|---|
| **N1** | one statement translator: fold `MermaidDbEmitter`'s statement/expression code into `TsToScalaEmitter` (keeping the Db class synthesis as a *module-shape* rule: "a module whose top-level `let`s are assigned by its functions is a class with `var` fields"); `commonDb` re-exports become a (b) parameter — `inheritedMembers: Set[fqn]` naming which imported functions are the base class's, default empty | (b) | Db tiers T0/T1, every later shape |
| **N2** | JavaScript module lowering under `allowJs`: `export default {…}`, `module.exports`, `var` hoisting (`VarHoisted`), `arguments` (`ArgumentsObject` refusal), loose `==` (`LooseEquality`), `for…in` over index-signature objects, `typeof` per R28, `Buffer`/`Set`/`Map`/`Object.keys` vocabulary rows (R30/R31 extended, dispatched by RESOLVED SYMBOL not text) | (a) | 7 JS Db files, 10 `styles.js`, themes, all of terser |
| **N3** | mapped/utility types as the checker resolves them: `Required<T>`, `Partial<T>`, `Pick`, `Omit`, `Record<K,V>` → the resolved property set (the checker already flattened it; export `resolvedProperties` for mapped types — an exporter change alongside R11/R12/R15) | (a) | `config.type.ts`, every `getConfig()`, `*Types.ts` |
| **N4** | static string-keyed registry: `registerDiagram(id, def, detector)` / `addDiagrams()` → a `Map` in the module object; dynamic `import()` loaders dropped and counted | (b): which registration function, which key argument | detectors, `DetectType`, `Mermaid` dispatch |
| **N5** | ES6 `class` declarations and expressions: constructor → primary constructor, `this.x = …` in the constructor → fields, `static`, `get`/`set`, `extends`, `super(...)`, class fields | (a) | terser `TreeWalker`/`TreeTransformer`/`Compressor`/`SymbolDef`, mermaid `themes/*.js`, `Namespace.ts` in katex |
| **N6** | option tables: `defaults(options, { key: default, … }, croak)` → case class + presence set | (b): which helper, which positions | `OutputOptions`, `CompressorOptions`, `MinifyOptions` |
| **N7** | **the `.d.ts` bootstrap**: from the DEFNODE hierarchy and the reference port, generate `lib/ast.d.ts` declaring `class AST_X extends AST_Y { prop: T; … }` with props typed from the reference's `AstX` fields (`OpaqueSpec.derive`'s "spelling read off signatures" applied to a class: `expression: AstNode \| Null` → `expression: AST_Node \| null`), and `DEFMETHOD` as per-name overloads whose callback has a `this: AST_X` parameter and the reference's parameter types (`DEFMETHOD(name: "has_side_effects", fn: (this: AST_Node, compressor: Compressor) => boolean): void`) so contextual typing types `this` and the parameters inside every body; re-export with the `.d.ts` in the program. Measured: the `any` ratio on `this.x` before/after (today 98.5%); the stop rule from `non-java-frontends.md` 4.3 applies (close at >30% `DynamicMember` after the bootstrap) | (b): the derivation is generic (any runtime class factory with a typed reference); the DEFNODE recogniser is the (c) value | all of terser beyond the data files |
| **N8** | DEFMETHOD placement: a `DEFMETHOD` family becomes methods on the declared classes (faithful) with `MemberRenameTransform.derive` reading the reference's camelCase spellings; where the reference put the family in an object with `match` dispatch (`Inference.hasSideEffects(node, c)`), that is an API-parity divergence row, and the ssg-suite shim table forwards the object call to the method. `DEFPRINT`/`PARENS`/`DEFMAP`/`def_optimize`/`def_eval` are the same family under other names — a (b) list of the macro names | (b) | ast/transform/size/equivalent-to/scope/output/compress |
| **N9** | d3 `Selection` → `SvgBuilder` retarget: a family table (the `CollectionsTransform` retarget mechanism, applied to a third-party type): identical-name members pass through, `attr(name, callback)`/`data`/`enter`/`join`/`transition` refuse and count (`D3DataJoin`), `select('#'+id)`/`selectSvgElement` → the injected `createSvg`, `getBBox` passes through to ssg's `TextMetrics`-backed implementation | (b): the table is per-library policy; linearization is (a) | every renderer, `render/`, `svgDraw*` |
| **N10** | **the jison track**: at export time run `jison <grammar>` (npm, pinned) to get `parser.js`; export it under `allowJs`; translate `performAction(yytext, yyleng, yylineno, yy, yystate, $$, _$)` (a `switch` over rule numbers whose arms are the grammar's JS actions calling `yy.addVertex(...)` — `yy` is the Db, typed by N7's derivation from the translated Db class) and the lexer rule table (regexes + start conditions); ship the LALR driver (`parse()` ~300 lines, `lexer.next()` ~150 lines) ONCE as a support type in `ssg-mermaid` (it is a base for nothing, so it may not live in `runtime/`; P10). Regex dialect divergences counted per rule (`RegExpDialect`); sticky matching emulated with `region`+`lookingAt` on JVM/Native, native `y` on JS | (b) for the driver (any jison grammar), (c) nothing | 16 parsers, 6,219 LOC of hand-written Scala retired |
| **N11** | closure module → class: a function whose inner functions close over a local `var S = {…}` / local `var`s and which returns an object of those functions (`tokenizer()`, `parse()`, `OutputStream()`) becomes a class with `var` fields and methods; the same rule as N1's module shape one level down | (a) | `parse.js`, `output.js`, `scope.js` `figure_out_scope` |
| **N12** | vitest/jest → MUnit policy for `TestFrameworkTransform`: `describe` → nesting in the suite name, `it` → `test`, `beforeEach` → hoisted per §4.4's instance-state rule, `expect(x).toBe/toEqual/toBeTruthy/toThrow/toMatchInlineSnapshot(literal)` → assertions; `vi.mock` refused and counted | (b) table, the JUnit one is the precedent | the primary oracle for mermaid (1,042 tests) |
| **N13** | per-library vocabulary tables: dayjs → `java.time` (gantt), khroma → `Color` (themes), lodash-es (`memoize`, `merge`, `isEmpty`), `js-yaml` → injected YAML shim, `Buffer` → base64 | (b) tables, (c) values | gantt Db/renderer, themes, `Directives`, sourcemaps |
| **N14** | the four exporter facts from `ts-lowering-plan.md`: tuple types (R12), index signatures (R11), contextual types on literals (R15), library-symbol origin `lib: true` (wave 3); plus `optional`/`rest` flags that R1 dropped without replacement | (a) | every object literal that names a type; every `(owner, member)` vocabulary dispatch |

Everything in this table is written once, in `frontend-ts`, with no library name in code (the
`Segment`/`PathToken` field-name sniffing in `emitObjectLiteral` is deleted by N14/R15; the
`commonDb` set and the DEFNODE names become parameters).

---

## 5. Steps, in order

Ordering rule: (A) what compiles with existing capabilities once the gate and the RAST exist;
(B) one new capability per step; (C) steps needing several. Each step names its files, its
emitter, its rule, and its verification; a step is done when its lane is green under the three
numbers of §1 and the previous steps' numbers did not move.

### A — existing capabilities

| # | files | emitter / rule | verify |
|---|---|---|---|
| A0 | `ported/ssg-mermaid`, `ported/ssg-js` port roots; `just ts-export`, `just mermaid-measure`, `just js-measure`; `BalticPorterGen.generateMermaid/Terser`; a member line index written by the text emitter (`srcmap.tsv`); the `inject` lists of §7 registered; **all 187 templates and `MermaidDiagramEmitters{,2}` deleted**, the string halves of `MermaidEmitter`/`TerserEmitter`/`Rough*Emitter` marked for deletion as each file they cover is re-derived | none (infrastructure) | ssg compiles with `src_managed` empty + injected files present; the three numbers of §1 reported as "0 translated / N injected"; every later step raises the first number |
| A1 | re-export the whole of `mermaid/packages/mermaid/src` (`allowJs`) and `terser/lib` + `tools/domprops.js`; regenerate the 21 stale roughjs/katex/points-on-curve fixtures | exporter, no change | two exports byte-equal; RAST for 148 + 27 files present |
| A2 | mermaid `styles.ts`: `block`, `flowchart`, `mindmap`, `packet`, `pie` | `emitStyles` | `ThemeCssIss1063Suite`; each diagram's `render` smoke test |
| A3 | `accessibility.ts`, `diagram-api/comments.ts`, `common/commonDb.ts`, `common/populateCommonDb.ts`, `config.ts`, `logger.ts` | `emitUtility`/`emitAccessibility`, `MermaidDbEmitter` for `commonDb` | `A11yIss1060Suite`; compile |
| A4 | terser `compressor-flags.js`, `native-objects.js`, `first_in_statement.js`, `tools/domprops.js` | `emitCompressorFlags`; generic emitter for the other three (retire the hand strings) | `AstSizeFirstInStatementIss1173Suite`; `Mangler`-dependent suites once `DomProps` is the emitted one |
| A5 | terser `ast.js` → `AstToken`, the 14 constant classes, the hierarchy skeleton (fields still from the hand table `inferPropertyType`) | `emitAstHierarchy` | compiles beside injected `AstNode`; the hand table is replaced in B7 |

### B — one new capability each

| # | files | capability | verify |
|---|---|---|---|
| B1 | Db T0: `info`, `packet`, `pie`, `sankey` (with A3's `common/`, `types.ts` per R16) | **N1** | upstream `pie.spec.ts` (12), `packet.spec.ts` (9), `info.spec.ts` (4), `sankey.spec.ts` (2) through N12 once it exists — until then the ssg suites through the shim table: `PieDiagramSuite` 16, `PacketDiagramSuite` 11, `InfoDiagramSuite` 5, `SankeyDiagramSuite` 6 (parse tests need the injected langium parsers) |
| B2 | Db T1: `mindmap`, `quadrant`, `block`, `xychart` | N1 (already) — `for`/`switch` from the one translator | `mindmap.spec.ts` 26, `quadrantDb.spec.ts` 4, `block/parser/block.spec.ts` 24 (db half), ssg: `MindmapDiagramSuite` 29, `QuadrantDiagramSuite` 18, `BlockDiagramSuite` 15, `XyChartDiagramSuite` 46 |
| B3 | `config.type.ts`, `defaultConfig.ts`, all `*Types.ts` | **N3** (+R16, R15) | `ConfigFieldsIss1058Suite` 4; every Db's `getConfig()` now types |
| B4 | 20 `*Detector.ts`, `detectType.ts`, `diagram-orchestration.ts`, 20 `*Diagram.ts` | **N4** (+R32 `test` row, R15) | `DetectTypeSequenceIss1071Suite` 4; each suite's `detect:` tests (2 per diagram) |
| B5 | Db T1-JS: `er`, `c4`, `requirement`, `timeline`, `journey`, `state`, `git`; `styles.js` ×10; `sourcemap.js`, `minify.js` slices | **N2** | `er/parser/erDiagram.spec.js` 77 (db half), `state/stateDb.spec.js` 5, `journeyDb.spec.js` 1, `timeline.spec.js` 7; ssg `ErDiagramSuite` 8, `StateDiagramSuite` 8, `GitDiagramSuite` 9, `JourneyDiagramSuite` 8, `TimelineDiagramSuite` 7, `C4DiagramSuite` 6, `RequirementDiagramSuite` 5; terser `SourceMapSuite` 19 |
| B6 | vitest → MUnit | **N12** | the 1,042 upstream `it` blocks emitted to `ported/ssg-mermaid/src_managed/test`; count compiled / passing; from here on every mermaid step reports the upstream number first |
| B7 | terser `ast.d.ts` generation and re-export; `AstNode` + all node classes with derived types; `_walk`/`_children_backwards`/`transform.js` bodies | **N7** (+N5 for `TreeWalker`/`TreeTransformer`) | the `any` ratio on `this.x` after bootstrap, recorded; `AstSuite`; compile of `ast/*` with nothing injected |
| B8 | `equivalent-to.js`, `size.js`, `scope.js` (36 DEFMETHOD), `output.js` DEFPRINT/PARENS/DEFMAP | **N8** | `EquivalentToSuite`, `AstSizeFirstInStatementIss1173Suite`, `ScopeAnalysis.figureOutScope` suites (23 sites), `OutputSuite` 27 once B10 lands |
| B9 | renderers T1: `info`, `packet`, `error`, `quadrant` (+`quadrantBuilder`), `xychart` (+`chartBuilder/**`), `journey` (+`svgDraw`), `timeline` (+`svgDraw`), `sequence` (+`svgDraw`), `c4`, `git`; `render/shapes/*`, `render/{clusters,edges,labels}/*`; `mermaidAPI.render`'s pipeline as the `render(text, config)` entry | **N9** (+N2 for the `.js` ones) | render smoke tests in every ssg suite (`contains("<svg")`, path data assertions in `SequenceWrapIss1202Suite` 11, `SequenceActorMetadataIss1067Suite` 5); `render/shapes/*HandDrawn*` 84 tests move from "injected" to "translated" |
| B10 | `parse.js` → `Tokenizer`, `Parser`, `Token`; `output.js` → `OutputStream`; `scope.js` closures | **N11** | `ParserSuite` 51, `ParserErrorSuite` 29, `ArrowSuite` 10, `DestructuringSuite`, `ClassFieldParseIss1174Suite`, `parse/*` 14; `OutputSuite` 27, `CommentsSuite` 26, `LineEndingsSuite` |
| B11 | `OutputOptions`, `CompressorOptions`, `MinifyOptions` | **N6** | `CompressorDefaultsFalseIss1034Suite`, `MinifySuite` 46 |

### C — several capabilities

| # | files | capabilities | verify |
|---|---|---|---|
| C1 | 16 jison parsers (the grammars' actions call the B1/B2/B5 Dbs) | **N10** + N2 + N7's `yy` typing + `RegExpDialect` rows | the upstream parser specs: `flowchart/parser/*.spec.js` 205, `class/classDiagram.spec.ts` 121 + `classTypes.spec.ts` 93, `er` 77, `git` 66, `sequence` 93, `state` 79, `xychart` 48, `c4` 38, `requirement` 27, `block` 24, `mindmap` 26, `gantt` 13, `quadrant` 14, `journey` 7, `timeline` 7, `sankey` 2 — ~940 of the 1,042; ssg `ClassDiagramSuite` 242, `FlowchartDiagramSuite` 29 and the rest through the shim table; `ParserBase.scala` retired |
| C2 | renderers T2: `pie` (d3 `pie`/`arc`), `gantt` (scales, axes), `sankey` (d3-sankey), `er`/`class`/`state`/`requirement`/`flowchart`/`block` (dagre), `mindmap` (cytoscape); `dagre-wrapper/nodes.js`, `rendering-util/render.ts` | N9 + a `dagre-d3-es` → `DagreLayout` retarget table (ssg-graphs-commons already has the engine) + a d3-scale/axis shim as an injected support type + refusals for cytoscape/d3-sankey (their layouts stay injected: `SankeyLayout`, ssg's mindmap layout) | `GanttDiagramSuite` 12 + 3 `Gantt*Iss*` 9, `PieDiagramSuite` render half, `StateRenderIss1129*` (JVM) 4, `StateDividerIss1064Suite` 7, `FlowchartHandDrawn*` 27, `Flowchart{Link,MaxEdges,NoTarget}Iss*` 20, `ClusterHandDrawnIss1204Suite` 11, `EdgeHandDrawnIss1204Suite` 9 |
| C3 | terser `compress/*` (index, inference, evaluate, drop-side-effect-free, reduce-vars, drop-unused, inline, tighten-body, common, global-defs, hoisting slice), `minify.js`, `propmangle.js` | N7 + N8 + N5 + N2 + labelled `boundary` + `arguments` refusals | the 74 generated compress suites (1,618 tests) — the real oracle; `MinifySuite`, `TerserSuite` 18, the `Iss*` suites; the 366 `.fail` tests stay `.fail` and are reported separately |
| C4 | Db T2: `class`, `flowchart`, `sequence`, `gantt`; `utils.ts`, `sanitizeDirective.ts`, `mermaid.ts`, `themes/*.js` | N2 + N5 + N13 (dayjs, khroma, lodash, DOMPurify refusals) + per-member `dropMethods` | `class/classDiagram.spec.ts` (db half), `flowDb.spec.ts` 7, `ganttDb.spec.ts` 12, `sequenceDiagram.spec.js` 93; ssg `GanttDiagramSuite`, `SequenceDiagramSuite` 25, `ClassDiagramSuite`, `FlowchartDiagramSuite`, `ThemeCssIss1063Suite`, `MermaidIss1068Suite`, the JVM-only `Frontmatter*`/`Config*`/`HtmlLabels*` suites |
| C5 | optional: `@jridgewell/*` as a `PortInRepo` (strict TS, ~2k LOC) replacing five injected sourcemap files; khroma as a `PortInRepo` replacing `color/*` | the roughjs machinery, N5 | `SourceMapSuite`, `InputSourceMapSuite`; `ThemeCssIss1063Suite` |

The minter track (`ts-lowering-plan.md` §5, M0–M6) runs beside this: every rule above is
written with its analysis half in the shared `TsLowering` object, so re-pointing construction
at TIR later does not re-derive the analyses. The text emitter remains the harness until the
minter prints one of the B1 files; the stop rule stands.

---

## 6. How much can be genuinely translated

By ssg LOC (what the templates cover), counting a file as genuine only when a step above
derives it from RAST and its oracle runs:

| tree | total | X — never (no upstream / ssg-only) | after A+B | after C | residue (refused sites inside translated files, est.) |
|---|---|---|---|---|---|
| ssg-mermaid | 35,232 | ~5,300 (15%): 10 diagrams 3,125, `ParserBase` 469, `TextMetrics` 197, `color/*` 790, 5 invented styles ~360, `CssGenerator`, `DiagramType`, `YamlDataViewDecoder`, the record types, 3 langium parsers ~390 | ~13,500 (38%): Db T0/T1 ~1,900, types/detectors/facades ~1,600, 15 styles ~1,900, renderers T1 + `render/` ~6,000, root utilities ~2,000 | ~26,500 (75%): + 16 parsers 6,200, renderers T2 ~3,500, Db T2 ~3,000, themes ~1,300 | DOM/DOMPurify/stylis members dropped; d3 data joins, cytoscape, d3-sankey sites refused; `getBBox` answered by an injected `TextMetrics` — ~1,000–1,500 LOC of counted refusals inside otherwise translated files |
| ssg-js | 42,450 | ~2,300 (5%): `TerserJsCompressor`, `CompressorLike`, `UnicodeIdentifierTables`, `JsNumber` (until R33), `package.scala`, five `@jridgewell` files ~930 (or C5) | ~14,000 (33%): `DomProps` 9,277, data/options ~1,200, `ast/*` ~2,600, `scope.js`, `equivalent-to`, `size`, `parse.js` ~4,600 if B10 lands before B7's gate, else ~9,400 | ~38,000 (90%) **conditional on N7's measurement**: if the `.d.ts` bootstrap leaves >30% of member bodies on `any`, C3 closes at the number and terser stays at ~33% with `compress/*` (17,000 LOC) injected | `arguments`, `new Function`, `Symbol`, `Object.defineProperty` sites; the `.fail` tests |

Honest floor: **mermaid 38% / terser 33%** with steps A+B; ceiling **mermaid 75% / terser 90%**
if the two large bets (N10 jison, N7 `.d.ts`) measure well. The 10 no-upstream mermaid diagrams
are 0% by definition and their 51 tests are excluded from every "genuine" count. Against the
brief's framing ("replace ALL templates"): every template is replaced — 187 deleted in A0 — but
~7,600 LOC of what they contained is hand-written Scala that moves back to ssg's `src/` as
declared injected files, and the plan reports it as such rather than as translated.

---

## 7. The injected list (declared in the port manifests, never in the engine)

ssg-mermaid: `diagrams/{architecture,kanban,radar,treemap,cynefin,eventmodeling,ishikawa,
treeview,venn,wardley}/*` (50), `diagrams/{info,packet,pie}/*Parser.scala` (3),
`diagrams/{error_,info,quadrant,sankey,xychart}/*Styles.scala` (5), `diagrams/error_/ErrorDb.scala`,
`parse/ParserBase.scala` (until C1), `render/text/TextMetrics.scala`, `color/*` (3, until C5),
`theme/CssGenerator.scala`, `DiagramType.scala`, `YamlDataViewDecoder.scala`,
`render/{clusters/ClusterConfig,edges/EdgeStyle,labels/LabelStyle,shapes/ShapeConfig,
shapes/HandDrawnShapes}.scala`, support types minted by the plan: the jison driver (C1), the
d3-scale shim, `SankeyLayout`, the mindmap layout (C2).

ssg-js: `TerserJsCompressor.scala`, `compress/CompressorLike.scala`, `parse/UnicodeIdentifierTables.scala`,
`output/JsNumber.scala` (until R33), `package.scala`, `sourcemap/{SourceMapGenerator,
SourceMapConsumer,SourceMapJson,VlqCodec,SourceMapTypes}.scala` (until C5); `ast/Nodes.scala`
and `parse/Precedence.scala` deleted.

Each injected file keeps its ssg header; none carries an "auto-generated" banner.

---

## 8. Stop rules and what is not planned

- **N7** closes at its measurement (`non-java-frontends.md` 4.3): >30% `DynamicMember` after the
  bootstrap → record the number in `ENGINE-LIMITS.md`, keep `compress/*` injected.
- **N10** closes if the driver exceeds ~600 lines or the lexer needs a regex feature with no
  JVM/Native form beyond the counted dialect rows.
- Not planned: translating the ten no-upstream diagrams (nothing to translate); a langium
  frontend; cytoscape/d3-sankey/dagre-d3-es ports (ssg-graphs-commons already has layouts;
  retarget, do not port); moving the mermaid pin past 11.0.0 to pick up `architecture`/`kanban`/
  `radar`/`treemap` (a separate decision with its own measurement); the TIR re-pointing (its
  own track); comments/trivia for TS (the minter's job).
- Not a shortcut: passing an ssg test by editing the emitted file, by widening a shim beyond a
  name/receiver row, or by keeping a template "for now". A0 deletes them all before anything
  is emitted, so the only way a number goes up is a rule.
