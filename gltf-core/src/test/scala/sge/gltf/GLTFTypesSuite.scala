package sge.gltf

import sge.gltf.data.data.GLTFAccessor
import sge.gltf.loaders.exceptions.GLTFIllegalException
import sge.gltf.loaders.shared.GLTFTypes
import sge.gltf.loaders.shared.animation.Interpolation
import sge.gltf.scene3d.model.{CubicQuaternion, CubicVector3, CubicWeightVector, WeightVector}

/** HAND-WRITTEN behavioural gate for the parts of gdx-gltf that need no GL context.
  *
  * Upstream's own suite is EIGHT tests in one file, all of them `Attribute.compareTo` (see
  * `GltfTestMigrate`), so the ported suite says nothing about the 135th of the library that is
  * actually a glTF reader. This file is what the `gltf-measure` lane counts beside it, and the two
  * numbers are printed separately and honestly.
  *
  * `GLTFTypes` is the target because it is where the specification's enumerations become libGDX
  * values — every mode, filter, wrap, interpolation and accessor size the loader depends on — and
  * because it is 300 lines of pure functions over plain data, with no `Gdx.gl`, no file and no
  * asset. Nothing else in the library gives this much behaviour per line of harness.
  *
  * ==Every assertion here is pointed at a CLAUDE.md §4.4 hazard, not at coverage==
  * The §4.4 table is the list of Java forms that translate to valid Scala meaning something else;
  * none of them moves a compile-error count, and running a test is the only way any of them has
  * ever been found. The four this file exists to catch:
  *
  *   - **a `switch` with no `default`.** `mapPrimitiveMode` and `mapTextureMinFilter` fall OUT of
  *     their switch on an unknown value and reach a `throw` beneath it. A `match` without a
  *     fall-out arm throws `MatchError` instead — a different exception, at a different place, and
  *     `intercept[GLTFIllegalException]` is what tells the two apart.
  *   - **an `Integer` scrutinee against `int` case labels.** Java unboxes the switch subject;
  *     Scala's constant pattern compares with `==`. The mapping tests read every arm, so a
  *     scrutinee that silently matched nothing would fail here rather than in a loaded model.
  *   - **a mutated PARAMETER.** `map(CubicWeightVector, float[], int)` does `offset += w.count`
  *     twice, and the port renames the parameter and introduces a `var`. The three sub-vectors
  *     therefore have to come from three DIFFERENT windows of the input, which is exactly what a
  *     lost `+=` would collapse.
  *   - **`x++` and off-by-one in general.** The `map(…, fv, offset)` family indexes from an offset;
  *     every one of them is asserted at a NON-ZERO offset, because at offset 0 an ignored offset
  *     and a respected one are indistinguishable.
  *
  * Adapted from the reference hand port's `GLTFTypesSuite`
  * (`../sge/sge-extension/gltf/src/test/scala/sge/gltf/loaders/shared/`), which asserts the same
  * mappings against sge's own `Nullable`-based API; the values are upstream's, the shapes are this
  * port's.
  */
class GLTFTypesSuite extends munit.FunSuite:

  // ---- constants -------------------------------------------------------
  // `static final String`/`int` are constant variables: java INLINES them, and the port emits
  // `inline val` for exactly that reason (§4.4). Reading them here proves the values survived the
  // rendering-at-the-declared-type that `inline val` requires.

  test("accessor type constants carry the specification's names") {
    assertEquals(GLTFTypes.TYPE_SCALAR, "SCALAR")
    assertEquals(GLTFTypes.TYPE_VEC2, "VEC2")
    assertEquals(GLTFTypes.TYPE_VEC3, "VEC3")
    assertEquals(GLTFTypes.TYPE_VEC4, "VEC4")
    assertEquals(GLTFTypes.TYPE_MAT2, "MAT2")
    assertEquals(GLTFTypes.TYPE_MAT3, "MAT3")
    assertEquals(GLTFTypes.TYPE_MAT4, "MAT4")
  }

  test("component type constants carry the GL enum values") {
    assertEquals(GLTFTypes.C_BYTE, 5120)
    assertEquals(GLTFTypes.C_UBYTE, 5121)
    assertEquals(GLTFTypes.C_SHORT, 5122)
    assertEquals(GLTFTypes.C_USHORT, 5123)
    assertEquals(GLTFTypes.C_UINT, 5125)
    assertEquals(GLTFTypes.C_FLOAT, 5126)
  }

  // ---- mapPrimitiveMode: a switch with no default ----------------------

  test("mapPrimitiveMode maps every glTF mode to its GL constant") {
    assertEquals(GLTFTypes.mapPrimitiveMode(0), sge.graphics.GL20.GL_POINTS)
    assertEquals(GLTFTypes.mapPrimitiveMode(1), sge.graphics.GL20.GL_LINES)
    assertEquals(GLTFTypes.mapPrimitiveMode(2), sge.graphics.GL20.GL_LINE_LOOP)
    assertEquals(GLTFTypes.mapPrimitiveMode(3), sge.graphics.GL20.GL_LINE_STRIP)
    assertEquals(GLTFTypes.mapPrimitiveMode(4), sge.graphics.GL20.GL_TRIANGLES)
    assertEquals(GLTFTypes.mapPrimitiveMode(5), sge.graphics.GL20.GL_TRIANGLE_STRIP)
    assertEquals(GLTFTypes.mapPrimitiveMode(6), sge.graphics.GL20.GL_TRIANGLE_FAN)
  }

  test("mapPrimitiveMode defaults a null mode to GL_TRIANGLES") {
    assertEquals(GLTFTypes.mapPrimitiveMode(null), sge.graphics.GL20.GL_TRIANGLES)
  }

  test("mapPrimitiveMode FALLS OUT of the switch and throws on an unknown mode") {
    // The §4.4 case: java falls out when nothing matches and reaches the `throw` below the switch.
    // A `match` with no fall-out arm raises MatchError instead — a different type, so this
    // assertion is what distinguishes them.
    intercept[GLTFIllegalException](GLTFTypes.mapPrimitiveMode(99))
  }

  // ---- accessor sizes --------------------------------------------------

  private def accessor(t: String, componentType: Int, count: Int = 1): GLTFAccessor =
    val a = new GLTFAccessor()
    a.`type` = t
    a.componentType = componentType
    a.count = count
    a

  test("accessorTypeSize counts the elements of each accessor type") {
    assertEquals(GLTFTypes.accessorTypeSize(accessor("SCALAR", GLTFTypes.C_FLOAT)), 1)
    assertEquals(GLTFTypes.accessorTypeSize(accessor("VEC2", GLTFTypes.C_FLOAT)), 2)
    assertEquals(GLTFTypes.accessorTypeSize(accessor("VEC3", GLTFTypes.C_FLOAT)), 3)
    assertEquals(GLTFTypes.accessorTypeSize(accessor("VEC4", GLTFTypes.C_FLOAT)), 4)
    assertEquals(GLTFTypes.accessorTypeSize(accessor("MAT2", GLTFTypes.C_FLOAT)), 4)
    assertEquals(GLTFTypes.accessorTypeSize(accessor("MAT3", GLTFTypes.C_FLOAT)), 9)
    assertEquals(GLTFTypes.accessorTypeSize(accessor("MAT4", GLTFTypes.C_FLOAT)), 16)
  }

  test("accessorTypeSize refuses an unknown or absent type") {
    intercept[GLTFIllegalException](GLTFTypes.accessorTypeSize(accessor("VEC5", GLTFTypes.C_FLOAT)))
    intercept[GLTFIllegalException](GLTFTypes.accessorTypeSize(accessor(null, GLTFTypes.C_FLOAT)))
  }

  test("accessorComponentTypeSize gives each component type its byte width") {
    assertEquals(GLTFTypes.accessorComponentTypeSize(accessor("SCALAR", GLTFTypes.C_BYTE)), 1)
    assertEquals(GLTFTypes.accessorComponentTypeSize(accessor("SCALAR", GLTFTypes.C_UBYTE)), 1)
    assertEquals(GLTFTypes.accessorComponentTypeSize(accessor("SCALAR", GLTFTypes.C_SHORT)), 2)
    assertEquals(GLTFTypes.accessorComponentTypeSize(accessor("SCALAR", GLTFTypes.C_USHORT)), 2)
    assertEquals(GLTFTypes.accessorComponentTypeSize(accessor("SCALAR", GLTFTypes.C_UINT)), 4)
    assertEquals(GLTFTypes.accessorComponentTypeSize(accessor("SCALAR", GLTFTypes.C_FLOAT)), 4)
  }

  test("accessorComponentTypeSize refuses an unknown component type") {
    intercept[GLTFIllegalException](GLTFTypes.accessorComponentTypeSize(accessor("SCALAR", 4242)))
  }

  test("accessorStrideSize and accessorSize compose the two, times the count") {
    // VEC3 of floats = 3 * 4 = 12 bytes per element; 7 elements = 84 bytes. Both derived, so a
    // wrong factor anywhere upstream shows up here rather than in a buffer overrun at load time.
    val a = accessor("VEC3", GLTFTypes.C_FLOAT, count = 7)
    assertEquals(GLTFTypes.accessorStrideSize(a), 12)
    assertEquals(GLTFTypes.accessorSize(a), 84)
  }

  // ---- texture sampler mapping ----------------------------------------

  test("mapTextureMagFilter maps the two legal values and defaults null to Linear") {
    assertEquals(GLTFTypes.mapTextureMagFilter(null), sge.graphics.Texture.TextureFilter.Linear)
    assertEquals(GLTFTypes.mapTextureMagFilter(9728), sge.graphics.Texture.TextureFilter.Nearest)
    assertEquals(GLTFTypes.mapTextureMagFilter(9729), sge.graphics.Texture.TextureFilter.Linear)
    intercept[GLTFIllegalException](GLTFTypes.mapTextureMagFilter(9987))
  }

  test("mapTextureMinFilter maps all six values and defaults null to Linear") {
    import sge.graphics.Texture.TextureFilter.*
    assertEquals(GLTFTypes.mapTextureMinFilter(null), Linear)
    assertEquals(GLTFTypes.mapTextureMinFilter(9728), Nearest)
    assertEquals(GLTFTypes.mapTextureMinFilter(9729), Linear)
    assertEquals(GLTFTypes.mapTextureMinFilter(9984), MipMapNearestNearest)
    assertEquals(GLTFTypes.mapTextureMinFilter(9985), MipMapLinearNearest)
    assertEquals(GLTFTypes.mapTextureMinFilter(9986), MipMapNearestLinear)
    assertEquals(GLTFTypes.mapTextureMinFilter(9987), MipMapLinearLinear)
    intercept[GLTFIllegalException](GLTFTypes.mapTextureMinFilter(1))
  }

  test("isMipMapFilter switches on the ENUM, and only the four mip filters are true") {
    // A `switch` over an enum with a `default:` arm — the fall-out here is a throw the java
    // reaches only for a value neither branch names, so the two false arms and the four true ones
    // must all be read to know the arms did not merge.
    def sampler(minFilter: java.lang.Integer): sge.gltf.data.texture.GLTFSampler =
      val s = new sge.gltf.data.texture.GLTFSampler()
      s.minFilter = minFilter
      s
    assertEquals(GLTFTypes.isMipMapFilter(sampler(9728)), false)
    assertEquals(GLTFTypes.isMipMapFilter(sampler(9729)), false)
    assertEquals(GLTFTypes.isMipMapFilter(sampler(9984)), true)
    assertEquals(GLTFTypes.isMipMapFilter(sampler(9985)), true)
    assertEquals(GLTFTypes.isMipMapFilter(sampler(9986)), true)
    assertEquals(GLTFTypes.isMipMapFilter(sampler(9987)), true)
    assertEquals(GLTFTypes.isMipMapFilter(sampler(null)), false) // null defaults to Linear
  }

  // ---- animation interpolation ----------------------------------------

  test("mapInterpolation maps the three sampler interpolations and defaults null to LINEAR") {
    assertEquals(GLTFTypes.mapInterpolation(null), Interpolation.LINEAR)
    assertEquals(GLTFTypes.mapInterpolation("LINEAR"), Interpolation.LINEAR)
    assertEquals(GLTFTypes.mapInterpolation("STEP"), Interpolation.STEP)
    assertEquals(GLTFTypes.mapInterpolation("CUBICSPLINE"), Interpolation.CUBICSPLINE)
    intercept[GLTFIllegalException](GLTFTypes.mapInterpolation("BEZIER"))
  }

  // ---- the map(…) family, every one at a NON-ZERO offset ---------------
  // At offset 0 an ignored offset and a respected one are indistinguishable, which is the whole
  // reason these read from the middle of a longer array.

  test("map(Vector3, float[], offset) reads three floats from the offset") {
    val fv = Array(-1f, -1f, 1f, 2f, 3f, -1f)
    val v  = GLTFTypes.map(new sge.math.Vector3(), fv, 2)
    assertEquals(v.x, 1f)
    assertEquals(v.y, 2f)
    assertEquals(v.z, 3f)
  }

  test("map(Quaternion, float[], offset) reads four floats from the offset") {
    val fv = Array(-1f, 1f, 2f, 3f, 4f, -1f)
    val q  = GLTFTypes.map(new sge.math.Quaternion(), fv, 1)
    assertEquals(q.x, 1f)
    assertEquals(q.y, 2f)
    assertEquals(q.z, 3f)
    assertEquals(q.w, 4f)
  }

  test("map(CubicVector3, …) splits nine floats into tangentIn / value / tangentOut IN THAT ORDER") {
    // The specification's cubic-spline layout, and the one place a transposed pair would be
    // silently plausible: three groups of three, read consecutively from the offset.
    val fv = Array(-1f, 1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f)
    val v  = GLTFTypes.map(new CubicVector3(), fv, 1)
    assertEquals((v.tangentIn.x, v.tangentIn.y, v.tangentIn.z), (1f, 2f, 3f))
    assertEquals((v.x, v.y, v.z), (4f, 5f, 6f))
    assertEquals((v.tangentOut.x, v.tangentOut.y, v.tangentOut.z), (7f, 8f, 9f))
  }

  test("map(CubicQuaternion, …) splits twelve floats into three quaternions") {
    val fv = (0 until 13).map(i => i.toFloat).toArray
    val q  = GLTFTypes.map(new CubicQuaternion(), fv, 1)
    assertEquals((q.tangentIn.x, q.tangentIn.w), (1f, 4f))
    assertEquals((q.x, q.w), (5f, 8f))
    assertEquals((q.tangentOut.x, q.tangentOut.w), (9f, 12f))
  }

  test("map(WeightVector, …) copies exactly `count` weights from the offset") {
    val w  = new WeightVector(3)
    val fv = Array(-1f, -1f, 0.25f, 0.5f, 0.75f, -1f)
    GLTFTypes.map(w, fv, 2)
    assertEquals(w.values(0), 0.25f)
    assertEquals(w.values(1), 0.5f)
    assertEquals(w.values(2), 0.75f)
  }

  test("map(CubicWeightVector, …) ADVANCES the offset between the three groups") {
    // The §4.4 mutated-parameter case, and the reason this test is here rather than a simpler one:
    // the java body does `offset += w.count` twice, so the three groups come from three DIFFERENT
    // windows. A port that dropped either `+=` would read the FIRST window three times, and every
    // assertion below would still be a legal float — which is exactly what a compile cannot see.
    val w  = new CubicWeightVector(2)
    val fv = Array(-1f, 1f, 2f, /* value */ 3f, 4f, /* out */ 5f, 6f)
    GLTFTypes.map(w, fv, 1)
    assertEquals((w.tangentIn.values(0), w.tangentIn.values(1)), (1f, 2f))
    assertEquals((w.values(0), w.values(1)), (3f, 4f))
    assertEquals((w.tangentOut.values(0), w.tangentOut.values(1)), (5f, 6f))
  }

  // ---- mapColor --------------------------------------------------------

  test("mapColor falls back to a COPY of the default, not to the default itself") {
    // `new Color(defaultColor)` — a copy. Returning the shared instance would let a caller's
    // mutation leak into every later default, with nothing to see at compile time.
    val fallback = new sge.graphics.Color(0.1f, 0.2f, 0.3f, 0.4f)
    val c        = GLTFTypes.mapColor(null, fallback)
    assertEquals(c.r, 0.1f)
    assertEquals(c.a, 0.4f)
    c.r = 0.9f
    assertEquals(fallback.r, 0.1f, "mapColor returned the default instance instead of a copy")
  }

  test("mapColor treats a three-component array as opaque and a four-component one as given") {
    val rgb = GLTFTypes.mapColor(Array(0.1f, 0.2f, 0.3f), sge.graphics.Color.BLACK)
    assertEquals(rgb.a, 1f)
    val rgba = GLTFTypes.mapColor(Array(0.1f, 0.2f, 0.3f, 0.5f), sge.graphics.Color.BLACK)
    assertEquals(rgba.a, 0.5f)
  }
