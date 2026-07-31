# anim8-core — the hand-written half of the port

`src_managed/` is the emitted port and is a build product (CLAUDE.md §5.5). This directory is the
only thing here that a human wrote, and it exists for one reason: **anim8-gdx has no upstream test
suite to migrate.**

Its `src/test/java` holds 20 files and **zero `@Test` annotations** — every one is an
`ApplicationAdapter` demo or a startup bench driven by `gdx-backend-lwjgl3` (`StillImageDemo`,
`VideoConvertDemo`, `InteractiveReducer`, `ShaderCaptureDemo`, …), and no libGDX backend is ported.
So `just anim8-measure`'s discovery block reports `@Test in upstream java: 0` and the port would
otherwise compile and prove nothing, which CLAUDE.md §3 says is not a gate at all.

These four suites are that gate. They are adapted from the reference hand port's own
(`../sge/sge-extension/anim8/src/test`), which is where the choice of what to cover comes from —
and one of them deliberately disagrees with it; see `ConstantDataSuite`.

They are MUnit, and they are compiled and run by `just anim8-measure` on the same `scala-cli`
invocation as `libgdx-core/src_managed/main` and `anim8-core/src_managed/main`.
