package com.badlogic.gdx.graphics.g3d.utils

class ModelBuilder {
  var model: com.badlogic.gdx.graphics.g3d.Model = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.Model]
  var node$field: com.badlogic.gdx.graphics.g3d.model.Node = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.model.Node]
  var builders: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.utils.MeshBuilder] = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.utils.MeshBuilder]()
  private var tmpTransform: com.badlogic.gdx.math.Matrix4 = new com.badlogic.gdx.math.Matrix4()
  private def getBuilder(attributes: com.badlogic.gdx.graphics.VertexAttributes): com.badlogic.gdx.graphics.g3d.utils.MeshBuilder = {
    for (mb <- this.builders) {
      if (mb.getAttributes().equals(attributes) && (mb.lastIndex() < (com.badlogic.gdx.graphics.g3d.utils.MeshBuilder.MAX_VERTICES / 2))) {
        return mb
      } else ()
    }
    val result: com.badlogic.gdx.graphics.g3d.utils.MeshBuilder = new com.badlogic.gdx.graphics.g3d.utils.MeshBuilder()
    result.begin(attributes)
    this.builders.add(result)
    return result
  }
  def begin(): scala.Unit = {
    if (this.model != null) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Call end() first")
    } else ()
    this.node$field = null
    this.model = new com.badlogic.gdx.graphics.g3d.Model()
    this.builders.clear()
  }
  def `end`(): com.badlogic.gdx.graphics.g3d.Model = {
    if (this.model == null) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Call begin() first")
    } else ()
    val result: com.badlogic.gdx.graphics.g3d.Model = this.model
    this.endnode()
    this.model = null
    for (mb <- this.builders) {
      mb.`end`()
    }
    this.builders.clear()
    ModelBuilder.rebuildReferences(result)
    return result
  }
  private def endnode(): scala.Unit = {
    if (this.node$field != null) {
      this.node$field = null
    } else ()
  }
  def node(node: com.badlogic.gdx.graphics.g3d.model.Node): com.badlogic.gdx.graphics.g3d.model.Node = {
    if (this.model == null) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Call begin() first")
    } else ()
    this.endnode()
    this.model.nodes.add(node)
    this.node$field = node
    return node
  }
  def node(): com.badlogic.gdx.graphics.g3d.model.Node = {
    val node: com.badlogic.gdx.graphics.g3d.model.Node = new com.badlogic.gdx.graphics.g3d.model.Node()
    this.node(node)
    node.id = "node" + this.model.nodes.size
    return node
  }
  def node(id: java.lang.String, model: com.badlogic.gdx.graphics.g3d.Model): com.badlogic.gdx.graphics.g3d.model.Node = {
    val node: com.badlogic.gdx.graphics.g3d.model.Node = new com.badlogic.gdx.graphics.g3d.model.Node()
    node.id = id
    node.addChildren(model.nodes)
    this.node(node)
    for (disposable <- model.getManagedDisposables()) {
      this.manage(disposable)
    }
    return node
  }
  def manage(disposable: com.badlogic.gdx.utils.Disposable): scala.Unit = {
    if (this.model == null) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Call begin() first")
    } else ()
    this.model.manageDisposable(disposable)
  }
  def part(meshpart: com.badlogic.gdx.graphics.g3d.model.MeshPart, material: com.badlogic.gdx.graphics.g3d.Material): scala.Unit = {
    if (this.node$field == null) {
      this.node()
    } else ()
    this.node$field.parts.add(new com.badlogic.gdx.graphics.g3d.model.NodePart(meshpart, material))
  }
  def part(id: java.lang.String, mesh: com.badlogic.gdx.graphics.Mesh, primitiveType: scala.Int, offset: scala.Int, size: scala.Int, material: com.badlogic.gdx.graphics.g3d.Material): com.badlogic.gdx.graphics.g3d.model.MeshPart = {
    val meshPart: com.badlogic.gdx.graphics.g3d.model.MeshPart = new com.badlogic.gdx.graphics.g3d.model.MeshPart()
    meshPart.id = id
    meshPart.primitiveType = primitiveType
    meshPart.mesh = mesh
    meshPart.offset = offset
    meshPart.size = size
    this.part(meshPart, material)
    return meshPart
  }
  def part(id: java.lang.String, mesh: com.badlogic.gdx.graphics.Mesh, primitiveType: scala.Int, material: com.badlogic.gdx.graphics.g3d.Material): com.badlogic.gdx.graphics.g3d.model.MeshPart = {
    return this.part(id, mesh, primitiveType, 0, mesh.getNumIndices(), material)
  }
  def part(id: java.lang.String, primitiveType: scala.Int, attributes: com.badlogic.gdx.graphics.VertexAttributes, material: com.badlogic.gdx.graphics.g3d.Material): com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder = {
    val builder: com.badlogic.gdx.graphics.g3d.utils.MeshBuilder = this.getBuilder(attributes)
    this.part(builder.part(id, primitiveType), material)
    return builder
  }
  def part(id: java.lang.String, primitiveType: scala.Int, attributes: scala.Long, material: com.badlogic.gdx.graphics.g3d.Material): com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder = {
    return this.part(id, primitiveType, com.badlogic.gdx.graphics.g3d.utils.MeshBuilder.createAttributes(attributes), material)
  }
  def createBox(width: scala.Float, height: scala.Float, depth: scala.Float, material: com.badlogic.gdx.graphics.g3d.Material, attributes: scala.Long): com.badlogic.gdx.graphics.g3d.Model = {
    return this.createBox(width, height, depth, com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, material, attributes)
  }
  def createBox(width: scala.Float, height: scala.Float, depth: scala.Float, primitiveType: scala.Int, material: com.badlogic.gdx.graphics.g3d.Material, attributes: scala.Long): com.badlogic.gdx.graphics.g3d.Model = {
    this.begin()
    this.part("box", primitiveType, attributes, material).box(width, height, depth)
    return this.`end`()
  }
  def createRect(x00: scala.Float, y00: scala.Float, z00: scala.Float, x10: scala.Float, y10: scala.Float, z10: scala.Float, x11: scala.Float, y11: scala.Float, z11: scala.Float, x01: scala.Float, y01: scala.Float, z01: scala.Float, normalX: scala.Float, normalY: scala.Float, normalZ: scala.Float, material: com.badlogic.gdx.graphics.g3d.Material, attributes: scala.Long): com.badlogic.gdx.graphics.g3d.Model = {
    return this.createRect(x00, y00, z00, x10, y10, z10, x11, y11, z11, x01, y01, z01, normalX, normalY, normalZ, com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, material, attributes)
  }
  def createRect(x00: scala.Float, y00: scala.Float, z00: scala.Float, x10: scala.Float, y10: scala.Float, z10: scala.Float, x11: scala.Float, y11: scala.Float, z11: scala.Float, x01: scala.Float, y01: scala.Float, z01: scala.Float, normalX: scala.Float, normalY: scala.Float, normalZ: scala.Float, primitiveType: scala.Int, material: com.badlogic.gdx.graphics.g3d.Material, attributes: scala.Long): com.badlogic.gdx.graphics.g3d.Model = {
    this.begin()
    this.part("rect", primitiveType, attributes, material).rect(x00, y00, z00, x10, y10, z10, x11, y11, z11, x01, y01, z01, normalX, normalY, normalZ)
    return this.`end`()
  }
  def createCylinder(width: scala.Float, height: scala.Float, depth: scala.Float, divisions: scala.Int, material: com.badlogic.gdx.graphics.g3d.Material, attributes: scala.Long): com.badlogic.gdx.graphics.g3d.Model = {
    return this.createCylinder(width, height, depth, divisions, com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, material, attributes)
  }
  def createCylinder(width: scala.Float, height: scala.Float, depth: scala.Float, divisions: scala.Int, primitiveType: scala.Int, material: com.badlogic.gdx.graphics.g3d.Material, attributes: scala.Long): com.badlogic.gdx.graphics.g3d.Model = {
    return this.createCylinder(width, height, depth, divisions, primitiveType, material, attributes, 0, 360)
  }
  def createCylinder(width: scala.Float, height: scala.Float, depth: scala.Float, divisions: scala.Int, material: com.badlogic.gdx.graphics.g3d.Material, attributes: scala.Long, angleFrom: scala.Float, angleTo: scala.Float): com.badlogic.gdx.graphics.g3d.Model = {
    return this.createCylinder(width, height, depth, divisions, com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, material, attributes, angleFrom, angleTo)
  }
  def createCylinder(width: scala.Float, height: scala.Float, depth: scala.Float, divisions: scala.Int, primitiveType: scala.Int, material: com.badlogic.gdx.graphics.g3d.Material, attributes: scala.Long, angleFrom: scala.Float, angleTo: scala.Float): com.badlogic.gdx.graphics.g3d.Model = {
    this.begin()
    this.part("cylinder", primitiveType, attributes, material).cylinder(width, height, depth, divisions, angleFrom, angleTo)
    return this.`end`()
  }
  def createCone(width: scala.Float, height: scala.Float, depth: scala.Float, divisions: scala.Int, material: com.badlogic.gdx.graphics.g3d.Material, attributes: scala.Long): com.badlogic.gdx.graphics.g3d.Model = {
    return this.createCone(width, height, depth, divisions, com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, material, attributes)
  }
  def createCone(width: scala.Float, height: scala.Float, depth: scala.Float, divisions: scala.Int, primitiveType: scala.Int, material: com.badlogic.gdx.graphics.g3d.Material, attributes: scala.Long): com.badlogic.gdx.graphics.g3d.Model = {
    return this.createCone(width, height, depth, divisions, primitiveType, material, attributes, 0, 360)
  }
  def createCone(width: scala.Float, height: scala.Float, depth: scala.Float, divisions: scala.Int, material: com.badlogic.gdx.graphics.g3d.Material, attributes: scala.Long, angleFrom: scala.Float, angleTo: scala.Float): com.badlogic.gdx.graphics.g3d.Model = {
    return this.createCone(width, height, depth, divisions, com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, material, attributes, angleFrom, angleTo)
  }
  def createCone(width: scala.Float, height: scala.Float, depth: scala.Float, divisions: scala.Int, primitiveType: scala.Int, material: com.badlogic.gdx.graphics.g3d.Material, attributes: scala.Long, angleFrom: scala.Float, angleTo: scala.Float): com.badlogic.gdx.graphics.g3d.Model = {
    this.begin()
    this.part("cone", primitiveType, attributes, material).cone(width, height, depth, divisions, angleFrom, angleTo)
    return this.`end`()
  }
  def createSphere(width: scala.Float, height: scala.Float, depth: scala.Float, divisionsU: scala.Int, divisionsV: scala.Int, material: com.badlogic.gdx.graphics.g3d.Material, attributes: scala.Long): com.badlogic.gdx.graphics.g3d.Model = {
    return this.createSphere(width, height, depth, divisionsU, divisionsV, com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, material, attributes)
  }
  def createSphere(width: scala.Float, height: scala.Float, depth: scala.Float, divisionsU: scala.Int, divisionsV: scala.Int, primitiveType: scala.Int, material: com.badlogic.gdx.graphics.g3d.Material, attributes: scala.Long): com.badlogic.gdx.graphics.g3d.Model = {
    return this.createSphere(width, height, depth, divisionsU, divisionsV, primitiveType, material, attributes, 0, 360, 0, 180)
  }
  def createSphere(width: scala.Float, height: scala.Float, depth: scala.Float, divisionsU: scala.Int, divisionsV: scala.Int, material: com.badlogic.gdx.graphics.g3d.Material, attributes: scala.Long, angleUFrom: scala.Float, angleUTo: scala.Float, angleVFrom: scala.Float, angleVTo: scala.Float): com.badlogic.gdx.graphics.g3d.Model = {
    return this.createSphere(width, height, depth, divisionsU, divisionsV, com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, material, attributes, angleUFrom, angleUTo, angleVFrom, angleVTo)
  }
  def createSphere(width: scala.Float, height: scala.Float, depth: scala.Float, divisionsU: scala.Int, divisionsV: scala.Int, primitiveType: scala.Int, material: com.badlogic.gdx.graphics.g3d.Material, attributes: scala.Long, angleUFrom: scala.Float, angleUTo: scala.Float, angleVFrom: scala.Float, angleVTo: scala.Float): com.badlogic.gdx.graphics.g3d.Model = {
    this.begin()
    this.part("sphere", primitiveType, attributes, material).sphere(width, height, depth, divisionsU, divisionsV, angleUFrom, angleUTo, angleVFrom, angleVTo)
    return this.`end`()
  }
  def createCapsule(radius: scala.Float, height: scala.Float, divisions: scala.Int, material: com.badlogic.gdx.graphics.g3d.Material, attributes: scala.Long): com.badlogic.gdx.graphics.g3d.Model = {
    return this.createCapsule(radius, height, divisions, com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, material, attributes)
  }
  def createCapsule(radius: scala.Float, height: scala.Float, divisions: scala.Int, primitiveType: scala.Int, material: com.badlogic.gdx.graphics.g3d.Material, attributes: scala.Long): com.badlogic.gdx.graphics.g3d.Model = {
    this.begin()
    this.part("capsule", primitiveType, attributes, material).capsule(radius, height, divisions)
    return this.`end`()
  }
  def createXYZCoordinates(axisLength: scala.Float, capLength: scala.Float, stemThickness: scala.Float, divisions: scala.Int, primitiveType: scala.Int, material: com.badlogic.gdx.graphics.g3d.Material, attributes: scala.Long): com.badlogic.gdx.graphics.g3d.Model = {
    this.begin()
    var partBuilder: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder]
    val node: com.badlogic.gdx.graphics.g3d.model.Node = this.node()
    partBuilder = this.part("xyz", primitiveType, attributes, material)
    partBuilder.setColor(com.badlogic.gdx.graphics.Color.RED)
    partBuilder.arrow(0, 0, 0, axisLength, 0, 0, capLength, stemThickness, divisions)
    partBuilder.setColor(com.badlogic.gdx.graphics.Color.GREEN)
    partBuilder.arrow(0, 0, 0, 0, axisLength, 0, capLength, stemThickness, divisions)
    partBuilder.setColor(com.badlogic.gdx.graphics.Color.BLUE)
    partBuilder.arrow(0, 0, 0, 0, 0, axisLength, capLength, stemThickness, divisions)
    return this.`end`()
  }
  def createXYZCoordinates(axisLength: scala.Float, material: com.badlogic.gdx.graphics.g3d.Material, attributes: scala.Long): com.badlogic.gdx.graphics.g3d.Model = {
    return this.createXYZCoordinates(axisLength, 0.1f, 0.1f, 5, com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, material, attributes)
  }
  def createArrow(x1: scala.Float, y1: scala.Float, z1: scala.Float, x2: scala.Float, y2: scala.Float, z2: scala.Float, capLength: scala.Float, stemThickness: scala.Float, divisions: scala.Int, primitiveType: scala.Int, material: com.badlogic.gdx.graphics.g3d.Material, attributes: scala.Long): com.badlogic.gdx.graphics.g3d.Model = {
    this.begin()
    this.part("arrow", primitiveType, attributes, material).arrow(x1, y1, z1, x2, y2, z2, capLength, stemThickness, divisions)
    return this.`end`()
  }
  def createArrow(from: com.badlogic.gdx.math.Vector3, to: com.badlogic.gdx.math.Vector3, material: com.badlogic.gdx.graphics.g3d.Material, attributes: scala.Long): com.badlogic.gdx.graphics.g3d.Model = {
    return this.createArrow(from.x, from.y, from.z, to.x, to.y, to.z, 0.1f, 0.1f, 5, com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, material, attributes)
  }
  def createLineGrid(xDivisions: scala.Int, zDivisions: scala.Int, xSize: scala.Float, zSize: scala.Float, material: com.badlogic.gdx.graphics.g3d.Material, attributes: scala.Long): com.badlogic.gdx.graphics.g3d.Model = {
    this.begin()
    val partBuilder: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder = this.part("lines", com.badlogic.gdx.graphics.GL20.GL_LINES, attributes, material)
    val xlength: scala.Float = xDivisions * xSize
    val zlength: scala.Float = zDivisions * zSize
    val hxlength: scala.Float = xlength / 2
    val hzlength: scala.Float = zlength / 2
    var x1: scala.Float = -hxlength
    var y1: scala.Float = 0
    var z1: scala.Float = hzlength
    var x2: scala.Float = -hxlength
    var y2: scala.Float = 0
    var z2: scala.Float = -hzlength;
    { var i: scala.Int = 0; while (i <= xDivisions) { {
      partBuilder.line(x1, y1, z1, x2, y2, z2)
      x1 = x1 + xSize
      x2 = x2 + xSize
    }; i = i + 1 } }
    x1 = -hxlength
    y1 = 0
    z1 = -hzlength
    x2 = hxlength
    y2 = 0
    z2 = -hzlength;
    { var j: scala.Int = 0; while (j <= zDivisions) { {
      partBuilder.line(x1, y1, z1, x2, y2, z2)
      z1 = z1 + zSize
      z2 = z2 + zSize
    }; j = j + 1 } }
    return this.`end`()
  }
}
object ModelBuilder {
  def rebuildReferences(model: com.badlogic.gdx.graphics.g3d.Model): scala.Unit = {
    model.materials.clear()
    model.meshes.clear()
    model.meshParts.clear()
    for (node <- model.nodes) {
      ModelBuilder.rebuildReferences(model, node)
    }
  }
  private def rebuildReferences(model: com.badlogic.gdx.graphics.g3d.Model, node: com.badlogic.gdx.graphics.g3d.model.Node): scala.Unit = {
    for (mpm <- node.parts) {
      if (!model.materials.contains(mpm.material, true)) {
        model.materials.add(mpm.material)
      } else ()
      if (!model.meshParts.contains(mpm.meshPart, true)) {
        model.meshParts.add(mpm.meshPart)
        if (!model.meshes.contains(mpm.meshPart.mesh, true)) {
          model.meshes.add(mpm.meshPart.mesh)
        } else ()
        model.manageDisposable(mpm.meshPart.mesh)
      } else ()
    }
    for (child <- node.getChildren()) {
      ModelBuilder.rebuildReferences(model, child)
    }
  }
}