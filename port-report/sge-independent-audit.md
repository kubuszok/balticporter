# Independent audit of the SGE translation

Date: 2026-09-10

## Objective and scope

Compare Baltic Porter's SGE and extension translations with `../sge`, using the vendored Java sources as the behavioral reference and recorded SGE decisions as the basis for intentional changes.

The objective is to replace the previous LLM translations with deterministic, configuration-guided output. Drop-in API compatibility is not required. Upstream functionality should survive unless an explicit decision modifies, replaces, or removes it. A type-replacement decision does not, by itself, authorize losing the original type's behavior.

The intended replacement stack is **generated LLS + `sge-l0`**. The existing extension ports still use the older `sge` translation and published LLS `0.3.0`; findings against that stack are identified separately.

This was a targeted source audit with focused runtime probes, not an exhaustive behavioral proof or a fresh full-suite run. The audit inspected configuration, generated sources, replacement sources, reference implementations, and existing progress records. No implementation fixes were made.

## Findings in the intended replacement stack

### 1. [P1] LegacyJson silently serializes unsupported objects as null

**Status:** Confirmed by source tracing; not verified in an end-to-end application run.

**Evidence:** [LegacyJson.scala](../balticporter/corpus/ladder-overrides/sge/utils/LegacyJson.scala), lines 161–166.

When an object matches none of the supported cases and has no registered serializer, `writeValue` calls `writer.value(null)`. Upstream [Json.java](../../sge/original-src/libgdx/gdx/src/com/badlogic/gdx/utils/Json.java), around lines 689–691, writes the object's fields. The older Baltic Porter [Json replacement](../balticporter/corpus/libgdx-overrides/sge/utils/Json.scala), lines 143–147, explicitly throws for this unsupported case.

**Impact:** An unsupported non-null object can become JSON `null` while serialization appears successful. This is silent data loss, distinct from the already documented decoding stubs.

**Decision assessment:** Replacing reflection with codecs is intentional. Silently discarding values that lack codec coverage is an additional behavioral change for which this audit found no cited authorization.

**Correction:** Complete the codec mapping for supported values. Until then, fail explicitly for unsupported objects and account for affected consumer paths.

### 2. [P1] DataBuffer is dropped to avoid a compilation problem, without a replacement

**Status:** Confirmed by configuration and source inspection.

**Evidence:** [LibgdxLadderMigrate.scala](../balticporter/corpus/src/main/scala/balticporter/corpus/libgdx/LibgdxLadderMigrate.scala), lines 1106–1108.

The drop cites Scala.js's inaccessible `FilterOutputStream.out`, the absence of internal callers, and SGE's rewritten version. No corresponding replacement was found in the ladder injections or generated output.

The reference [SGE DataBuffer.scala](../../sge/sge/src/main/scala/sge/utils/DataBuffer.scala), lines 30–39, is itself incomplete: its comment says it is abstract because Java interface methods are unimplemented. Its `size` returns backing-array capacity rather than the number of bytes written. Upstream [DataBuffer.java](../../sge/original-src/libgdx/gdx/src/com/badlogic/gdx/utils/DataBuffer.java) supplies a concrete buffered data-output implementation.

**Impact:** The new translation removes a capability whose previous LLM translation was incomplete, instead of completing it. Lack of internal callers does not establish that a public utility is unnecessary.

**Decision assessment:** A recorded implementation drop exists, but this audit found no cited user decision to remove the capability. The progress log records the drop as a Scala.js compilation fix.

**Correction:** Preserve the buffered output operations through a portable implementation, or obtain and record a deliberate capability-removal decision. Do not copy the incomplete reference implementation as-is.

### 3. [P2] FloatArray retargeting changes equality semantics

**Status:** Reproduced against compiled generated LLS classes; source mapping traced.

**Evidence:** [LibgdxLadderMigrate.scala](../balticporter/corpus/src/main/scala/balticporter/corpus/libgdx/LibgdxLadderMigrate.scala), lines 566–593; [generated DynamicArray.scala](../ported/lls/src_managed/main/scala/lowlevel/util/DynamicArray.scala), lines 350–365 and 823.

`FloatArray` becomes `DynamicArray[Float]`. The generic search implementation uses object equality, while upstream [FloatArray.java](../../sge/original-src/libgdx/gdx/src/com/badlogic/gdx/utils/FloatArray.java), lines 224–235, uses primitive floating-point equality.

| Operation | Upstream primitive semantics | Generated replacement |
|---|---:|---:|
| `[NaN].contains(NaN)` | `false` | `true` |
| `[-0.0f].contains(+0.0f)` | `true` | `false` |

**Impact:** Searches disagree for NaN and signed zero. Related search/removal operations using the same equality distinction require review.

**Decision assessment:** Unifying collection types does not require changing the originating collection's equality semantics.

**Correction:** Supply primitive-aware operations for primitive-array translations while retaining object equality for translations of generic Java arrays.

### 4. [P2] Bulk incr narrows floats and longs through Int

**Status:** Defective configured expression reproduced against generated LLS. Latent rule defect: no current core call to the one-argument overload was found.

**Evidence:** [LibgdxLadderMigrate.scala](../balticporter/corpus/src/main/scala/balticporter/corpus/libgdx/LibgdxLadderMigrate.scala), line 587.

The shared one-argument `incr` rewrite converts each existing element to `Int` before adding the increment. The rule is assigned to both `FloatArray` and `LongArray`.

| Initial value and increment | Expected | Configured expression |
|---|---:|---:|
| `1.75f + 0.5f` | `2.25f` | `1.5f` |
| `4294967296L + 1L` | `4294967297L` | `1L` |

Upstream performs addition in the source primitive type; see `incr` in [FloatArray.java](../../sge/original-src/libgdx/gdx/src/com/badlogic/gdx/utils/FloatArray.java) and [LongArray.java](../../sge/original-src/libgdx/gdx/src/com/badlogic/gdx/utils/LongArray.java).

**Impact:** Float fractions and the high bits of long values are lost when this rule is used.

**Correction:** Preserve the source primitive's arithmetic width. Narrow only where the original Java operation requires narrowing.

## Findings in the older stack still used by extensions

### 5. [P1] Listener removal during dispatch can skip listeners and throw

**Status:** Generated call path traced; its loop behavior reproduced against the configured published LLS dependency.

**Evidence:** [LibgdxCoreMigrate.scala](../balticporter/corpus/src/main/scala/balticporter/corpus/libgdx/LibgdxCoreMigrate.scala), around line 910; [generated Actor.scala](../ported/sge/src_managed/main/scala/sge/scenes/scene2d/Actor.scala), lines 251–258.

`DelayedRemovalArray` becomes published LLS's `DynamicArray`. Its snapshot support does not defer removals. Generated `Actor.notify` ignores the snapshot returned by `begin()` and indexes the changing collection using its original size.

With two listeners, if the first removes itself, the reproduced loop skips the second and throws `IndexOutOfBoundsException`. Upstream delays removal until dispatch ends.

**Correction:** Preserve deferred-removal behavior or translate the entire dispatch/removal protocol faithfully. Mapping the collection type alone is insufficient.

**Applicability:** The newer `sge-l0` path retains a translated `DelayedRemovalArray`; this finding does not apply to that newer implementation.

### 6. [P2] IdentityMap loses identity-based keys

**Status:** Replacement behavior reproduced against the configured published LLS dependency; source mapping traced.

**Evidence:** [LibgdxCoreMigrate.scala](../balticporter/corpus/src/main/scala/balticporter/corpus/libgdx/LibgdxCoreMigrate.scala), lines 398 and 675–687.

The mapping chooses `ArrayMap`, whose key lookup uses equality. Inserting two distinct `new String("key")` instances yields one entry. Upstream [IdentityMap.java](../../sge/original-src/libgdx/gdx/src/com/badlogic/gdx/utils/IdentityMap.java) preserves both through reference-identity comparison.

**Impact:** Distinct identity keys can overwrite one another. The configuration's comment claims identity semantics, but the construction and lookup rules do not supply them.

**Correction:** Use an identity-preserving representation or explicit identity-aware key operations.

**Applicability:** The newer path retains the original identity-map implementation. This is not a finding against generated LLS.

### 7. [P2] VFX drops an override that deliberately prevents state changes

**Status:** Confirmed by tracing upstream, configuration, and generated inheritance.

**Evidence:** [VfxMigrate.scala](../balticporter/corpus/src/main/scala/balticporter/corpus/vfx/VfxMigrate.scala), lines 69–70.

The configuration drops `VfxWidgetGroup.setTransform` because the parent setter was renamed. Upstream [VfxWidgetGroup.java](../../sge/original-src/gdx-vfx/gdx-vfx/core/src/com/crashinvaders/vfx/scene2d/VfxWidgetGroup.java), lines 193–198, deliberately ignores this operation because the widget does not support transforms.

**Impact:** Removing the override exposes the inherited setter, permitting callers to enable transforms. Renaming the member has unintentionally changed its behavior.

**Correction:** Rename the override together with the parent and retain its no-op body. An override must not be deleted merely to accommodate a renamed parent member.

## Remaining replacement and verification gaps

### The intended combined stack is not the one used by existing extension checks

[build.sbt](../build.sbt), around lines 479–509, defines the older core with published LLS `0.3.0` and connects extension projects to it. Around lines 542–544, `sge-l0` instead depends on `port-lls`.

Consequently, existing extension checks do not establish that those extensions work with generated LLS plus the newer core. This is an integration gap, not a requirement to preserve the old API.

### Particle decoding still reaches the documented LegacyJson stub

[Generated ParticleEffectLoader.scala](../ported/sge-l0/src_managed/main/scala/sge/graphics/g3d/particles/ParticleEffectLoader.scala), lines 43–45, calls `LegacyJson.fromJson` while discovering dependencies. That replacement throws. Introducing codec infrastructure has not completed this consumer path.

This is a known remaining gap, separate from finding 1's silent serialization fallback.

### Primitive-array replacement coverage is incomplete

The new path drops primitive-array classes, but operations such as `mul` lack equivalent replacement coverage. [LibgdxLadderMigrate.scala](../balticporter/corpus/src/main/scala/balticporter/corpus/libgdx/LibgdxLadderMigrate.scala), around lines 1279–1282, excludes `LongArrayTest` because the replacement lacks operations the test exercises.

A decision to replace a type should enumerate how its functionality survives, or identify the specific operations deliberately removed. Excluding a test due to missing functionality does not establish equivalence.

### Module coverage remains partial

[PROGRESS.md](../PROGRESS.md), section 1.1, records unported modules including colorful, tools, controllers, freetype, physics, and platform modules. These require their own scope decisions. SGE-original and intentionally non-Java implementations should not automatically be classified as missing Java translations.

## Verification and limits

- Executed focused probes against compiled generated LLS for floating-point equality and the configured bulk-increment expression.
- Executed focused probes against published LLS `0.3.0` for the older listener-loop and identity-key mappings. These were isolated reproductions, not full Actor or application tests.
- Traced the JSON fallback, DataBuffer omission, and VFX override loss through their source/configuration paths.
- An attempted isolated LegacyJson runtime probe could not complete because it combined older compiled core classes with generated LLS and encountered a binary linkage mismatch. It is not evidence of an intended-stack runtime failure; finding 1 rests on source tracing.
- Checked several suspicious WebGL stubs against vendored libGDX. They already exist upstream and were excluded from translation findings.
- Did not rerun all migrations, the full test suites, or platform integration tests. Existing generated artifacts and reports can become stale after subsequent changes; line numbers reflect this audit's snapshot.
- No implementation fixes were made. The pre-existing untracked `build.sbt.semanticdb` was left unchanged.

## Assessment

Deterministic translation addresses inconsistent LLM output, but determinism alone does not establish behavioral fidelity. The recurring weakness is that configuration records what changed without necessarily establishing why the behavioral change is authorized.

The highest-value follow-up is to require each replacement or drop to account for the originating capability: translated, implemented by a replacement, explicitly removed by decision, or still incomplete. Compilation-driven drops and mappings validated only at current call sites otherwise reproduce the same omission pattern deterministically.
