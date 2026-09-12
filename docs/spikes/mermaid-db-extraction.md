# Spike: Mermaid Diagram DB Extraction

## Inventory

Mermaid's `packages/mermaid/src/diagrams/` contains 20 diagram directories with 96 non-spec
TypeScript/JavaScript files. The diagram databases (state holders) are the directly translatable
subset — they hold data models, not rendering.

### DB files assessed

| File | Lang | LOC | d3 | DOM | Verdict |
|------|------|-----|----|----|---------|
| blockDB.ts | TS | 318 | 0 | 0 | **directly translatable** |
| commonDb.ts | TS | 32 | 0 | 0 | **directly translatable** |
| populateCommonDb.ts | TS | 14 | 0 | 0 | **directly translatable** |
| infoDb.ts | TS | 10 | 0 | 0 | **directly translatable** |
| mindmapDb.ts | TS | 159 | 0 | 0 | **directly translatable** |
| db.ts (packet) | TS | 59 | 0 | 0 | **directly translatable** |
| pieDb.ts | TS | 66 | 0 | 0 | **directly translatable** |
| quadrantDb.ts | TS | 176 | 0 | 0 | **directly translatable** |
| sankeyDB.ts | TS | 87 | 0 | 0 | **directly translatable** |
| xychartDb.ts | TS | 229 | 0 | 0 | **directly translatable** |
| interactionDb.ts | TS | 10 | 0 | 0 | **directly translatable** |
| sequenceDb.ts | TS | 668 | 0 | 2 | **mostly translatable** (2 DOM refs) |
| erDb.js | JS | 103 | 0 | 0 | needs JS frontend |
| c4Db.js | JS | 833 | 0 | 0 | needs JS frontend |
| requirementDb.js | JS | 168 | 0 | 0 | needs JS frontend |
| timelineDb.js | JS | 102 | 0 | 0 | needs JS frontend |
| journeyDb.js | JS | 130 | 0 | 0 | needs JS frontend |
| stateDb.js | JS | 626 | 0 | 1 | needs JS frontend |
| classDb.ts | TS | 514 | 3 | 9 | **needs substitution** (d3 + DOM) |
| flowDb.ts | TS | 963 | 1 | 6 | **needs substitution** (d3 + DOM) |
| ganttDb.js | JS | 817 | 2 | 3 | needs JS frontend + substitution |

### Top-level config/core

| File | LOC | d3 | DOM | Verdict |
|------|-----|----|----|---------|
| config.type.ts | 1211 | 0 | 0 | **directly translatable** (generated interfaces) |
| config.ts | 106 | 0 | 0 | **directly translatable** |
| defaultConfig.ts | ~200 | 0 | 0 | **directly translatable** |
| errors.ts | ~50 | 0 | 0 | **directly translatable** |
| internals.ts | ~30 | 0 | 0 | **directly translatable** |
| logger.ts | ~80 | 0 | 2 | mostly translatable |
| Diagram.ts | ~200 | 0 | 0 | mostly translatable (uses dynamic import) |
| mermaid.ts | ~180 | 0 | 15 | **needs substitution** (browser entry point) |

## Summary

- **11 TypeScript DB files** (1,828 LOC) are directly translatable with the existing TS frontend
- **6 JavaScript DB files** (2,779 LOC) need the JS frontend (`allowJs` mode)
- **2 TypeScript DB files** (1,477 LOC) need d3/DOM substitution
- **1 JavaScript DB file** (817 LOC) needs both JS frontend and substitution
- **Config types** (1,567 LOC) are directly translatable

Total directly translatable: **3,395 LOC** (38% of DB+config surface)
Total with substitutions: **4,872 LOC** (55%)
Blocked on JS frontend: **3,596 LOC** (remaining)

## Recommendation

Start with the 11 pure TypeScript DB files as a `.conf` port. These exercise:
- Module-level state (variables)
- Function exports
- Interface/type definitions
- Array/Map/Set operations
- No external dependencies

The config types are also a strong candidate — they're auto-generated from JSON Schema and are
pure interface definitions (1,211 LOC of typed interfaces).

## Stop rule

Do not attempt the d3-dependent files (classDb, flowDb, ganttDb) or the rendering layer until
the substitution mechanism is proven on the rough.js family.
