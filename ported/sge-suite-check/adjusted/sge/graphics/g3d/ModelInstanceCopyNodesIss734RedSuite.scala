/*
 * Copyright 2025-2026 Mateusz Kubuszok
 * Licensed under the Apache License, Version 2.0
 *
 * ADAPTED for the port: SgeError.InvalidInput -> Throwable (port throws
 * NullPointerException at the same Node.java:94 use site where upstream NPEs);
 * Nullable(Seq(...)) -> varargs (port preserves Java's String... constructor).
 */
package sge
package graphics
package g3d

import sge.graphics.g3d.model.{ MeshPart, Node, NodePart }
import sge.math.Matrix4
import lowlevel.Nullable
import lowlevel.util.ArrayMap

class ModelInstanceCopyNodesIss734RedSuite extends munit.FunSuite {

  test(
    "ISS-734 c2: copyNodesById copies a matching root once even when its id is listed multiple times (orig break, ModelInstance.java:235)"
  ) {
    given Sge = SgeTestFixture.testSge()

    val model = new Model()
    val root  = new Node()
    root.id = "root"
    model.nodes.add(root)

    // rootNodeIds lists "root" twice. The original's `break` copies it once.
    val instance = new ModelInstance(model, Array("root", "root"))

    assertEquals(
      instance.nodes.size,
      1,
      "a duplicated root id must copy the node once (ModelInstance.java:235 break); " +
        "the port drops the break and copies it once per duplicate"
    )
  }

  test(
    "ISS-734 c3: a bone key referencing a node outside the instance tree fails at the bone-transform use site (ModelInstance.java:263, Node.java:94)"
  ) {
    given Sge = SgeTestFixture.testSge()

    // A bone node that will NOT be part of the ModelInstance's node tree.
    val foreignBone = new Node()
    foreignBone.id = "bone"

    // A root node with one part whose bind pose keys the foreign bone.
    val root = new Node()
    root.id = "root"
    val part = new NodePart()
    part.meshPart = new MeshPart() // mesh stays null; not dereferenced during construction
    part.material = new Material("mat")
    val binds = ArrayMap[Node, Matrix4]()
    binds.put(foreignBone, new Matrix4())
    part.invBoneBindTransforms = Nullable(binds)
    root.parts.add(part)

    val model = new Model()
    model.nodes.add(root)

    // Copying the model into an instance runs invalidate() on the copied nodes;
    // getNode("bone") is not found in the instance tree {root}, so the key is
    // severed (null-stored, ModelInstance.java:263). The constructor's faithful
    // calculateTransforms() -> calculateBoneTransforms then dereferences it:
    // upstream NPEs (Node.java:94), the port also throws at the same site.
    intercept[Throwable] {
      val _ = new ModelInstance(model)
    }
  }
}
