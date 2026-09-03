/*
 * Ported from gdx-gltf - https://github.com/mgsx-dev/gdx-gltf
 * Licensed under the Apache License, Version 2.0
 *
 * Scala port copyright 2025-2026 Mateusz Kubuszok
 */
package sge.gltf.data.geometry

/** A morph target is a map of attribute names to accessor indices.
  *
  * In the upstream Java, `GLTFMorphTarget extends ObjectMap<String, Integer> implements
  * Json.Serializable`. The retarget maps `ObjectMap` to lls `lowlevel.util.ObjectMap`, which is
  * `final` -- so a subclass cannot extend it. The hand port (`../sge/sge-extension/gltf`) solved
  * this by making it extend `HashMap[String, Int]`, dropping the `Json.Serializable` interface
  * (the whole Json serialization layer is replaced by Jsoniter codecs in `GLTFCodecs`).
  *
  * This injection reproduces the hand port's shape. The `write` and `read` methods are not needed:
  * `Json` is dropped by the base port (reflective serialization replaced by Jsoniter), so the
  * `Serializable` interface does not exist in the emitted surface.
  *
  * K37 SubclassOfTarget: a program class extending a retarget target whose target is final.
  * Classification: section 1(c) -- which class extends which final retarget target is knowledge
  * about this library alone.
  */
@scala.annotation.nowarn("msg=inheritance from class HashMap.*is deprecated")
class GLTFMorphTarget extends scala.collection.mutable.HashMap[String, Int]
