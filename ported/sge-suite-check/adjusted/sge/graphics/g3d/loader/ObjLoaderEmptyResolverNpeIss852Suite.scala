/*
 * Copyright 2025-2026 Mateusz Kubuszok
 * Licensed under the Apache License, Version 2.0
 *
 * Java-parity pin — ISS-852 (minor), wave 2026-07-18-H, territory H1.
 *
 * The no-arg `ObjLoader()` constructor threads an EMPTY resolver
 * (Nullable.empty[FileHandleResolver]) up the ctor chain
 * (ObjLoader.scala:77-78 -> ModelLoader -> AsynchronousAssetLoader ->
 * AssetLoader.scala:32). This mirrors the original, where the no-arg ctor
 * passes a raw `null` resolver:
 *
 *   // com/badlogic/gdx/graphics/g3d/loader/ObjLoader.java:89-95
 *   public ObjLoader ()                            { this(null); }
 *   public ObjLoader (FileHandleResolver resolver) { super(resolver); }
 *
 * In the original, calling `resolve(String)` on such a loader dereferences the
 * null resolver and raises a NullPointerException:
 *
 *   // com/badlogic/gdx/assets/loaders/AssetLoader.java:41-43
 *   public FileHandle resolve (String fileName) {
 *     return resolver.resolve(fileName);   // NPE when resolver == null
 *   }
 *
 * The faithful Scala port keeps the same behaviour: AssetLoader stores
 * `resolver$p.orNull` into the `resolver` field, so an empty Nullable
 * becomes a null field. Calling `this.resolver.resolve(fileName)` dereferences
 * null and raises a NullPointerException, exactly as the original does.
 *
 * This suite pins that Java-parity contract: a no-arg ObjLoader's `resolve`
 * throws NPE. It guards against a regression that would silently swallow the
 * missing resolver (e.g. defaulting resolve to a no-op or a bogus FileHandle)
 * instead of failing like the original.
 */
package sge
package graphics
package g3d
package loader

class ObjLoaderEmptyResolverNpeIss852Suite extends munit.FunSuite {

  private given Sge = SgeTestFixture.testSge()

  test(
    "ISS-852: `resolve` on a no-arg ObjLoader (empty resolver) throws NPE — Java parity (AssetLoader.java:41-43, null resolver)"
  ) {
    // No-arg ctor => Nullable.empty resolver unwrapped to null via orNull.
    // AssetLoader.resolve dereferences the null field, raising NPE.
    val loader = ObjLoader()
    intercept[NullPointerException] {
      loader.resolve("x")
    }
  }
}
