package com.badlogic.gdx.graphics.g3d

class ModelCache(sorter$p: com.badlogic.gdx.graphics.g3d.utils.RenderableSorter, meshPool$p: com.badlogic.gdx.graphics.g3d.ModelCache.MeshPool) extends com.badlogic.gdx.utils.Disposable with com.badlogic.gdx.graphics.g3d.RenderableProvider {
  private var renderables: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.Renderable] = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.Renderable]()
  private var renderablesPool: com.badlogic.gdx.utils.FlushablePool[com.badlogic.gdx.graphics.g3d.Renderable] = new com.badlogic.gdx.utils.FlushablePool[com.badlogic.gdx.graphics.g3d.Renderable]() {
    override def newObject(): com.badlogic.gdx.graphics.g3d.Renderable = {
      return new com.badlogic.gdx.graphics.g3d.Renderable()
    }
  }
  private var meshPartPool: com.badlogic.gdx.utils.FlushablePool[com.badlogic.gdx.graphics.g3d.model.MeshPart] = new com.badlogic.gdx.utils.FlushablePool[com.badlogic.gdx.graphics.g3d.model.MeshPart]() {
    override def newObject(): com.badlogic.gdx.graphics.g3d.model.MeshPart = {
      return new com.badlogic.gdx.graphics.g3d.model.MeshPart()
    }
  }
  private var items: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.Renderable] = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.Renderable]()
  private var tmp: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.Renderable] = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.Renderable]()
  private var meshBuilder: com.badlogic.gdx.graphics.g3d.utils.MeshBuilder = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.utils.MeshBuilder]
  private var building: scala.Boolean = false
  private var sorter: com.badlogic.gdx.graphics.g3d.utils.RenderableSorter = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.utils.RenderableSorter]
  private var meshPool: com.badlogic.gdx.graphics.g3d.ModelCache.MeshPool = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.ModelCache.MeshPool]
  private var camera: com.badlogic.gdx.graphics.Camera = null.asInstanceOf[com.badlogic.gdx.graphics.Camera]
  def this() = {
    this(new com.badlogic.gdx.graphics.g3d.ModelCache.Sorter(), new com.badlogic.gdx.graphics.g3d.ModelCache.SimpleMeshPool())
  }
  this.sorter = sorter$p
  this.meshPool = meshPool$p
  this.meshBuilder = new com.badlogic.gdx.graphics.g3d.utils.MeshBuilder()
  def begin(): scala.Unit = {
    this.begin(null)
  }
  def begin(camera: com.badlogic.gdx.graphics.Camera): scala.Unit = {
    if (this.building) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Call end() after calling begin()")
    } else ()
    this.building = true
    this.camera = camera
    this.renderablesPool.flush()
    this.renderables.clear()
    this.items.clear()
    this.meshPartPool.flush()
    this.meshPool.flush()
  }
  private def obtainRenderable(material: com.badlogic.gdx.graphics.g3d.Material, primitiveType: scala.Int): com.badlogic.gdx.graphics.g3d.Renderable = {
    val result: com.badlogic.gdx.graphics.g3d.Renderable = this.renderablesPool.obtain()
    result.bones = null
    result.environment = null
    result.material = material
    result.meshPart.mesh = null
    result.meshPart.offset = 0
    result.meshPart.size = 0
    result.meshPart.primitiveType = primitiveType
    result.meshPart.center.set(0, 0, 0)
    result.meshPart.halfExtents.set(0, 0, 0)
    result.meshPart.radius = -1.0f
    result.shader = null
    result.userData = null
    result.worldTransform.idt()
    return result
  }
  def `end`(): scala.Unit = {
    if (!this.building) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Call begin() prior to calling end()")
    } else ()
    this.building = false
    if (this.items.size == 0) {
      return
    } else ()
    this.sorter.sort(this.camera, this.items)
    val itemCount: scala.Int = this.items.size
    val initCount: scala.Int = this.renderables.size
    val first: com.badlogic.gdx.graphics.g3d.Renderable = this.items.get(0)
    var vertexAttributes: com.badlogic.gdx.graphics.VertexAttributes = first.meshPart.mesh.getVertexAttributes()
    var material: com.badlogic.gdx.graphics.g3d.Material = first.material
    var primitiveType: scala.Int = first.meshPart.primitiveType
    var offset: scala.Int = this.renderables.size
    this.meshBuilder.begin(vertexAttributes)
    var part: com.badlogic.gdx.graphics.g3d.model.MeshPart = this.meshBuilder.part("", primitiveType, this.meshPartPool.obtain())
    this.renderables.add(this.obtainRenderable(material, primitiveType));
    { var i: scala.Int = 0; val n: scala.Int = this.items.size; while (i < n) { {
      val renderable: com.badlogic.gdx.graphics.g3d.Renderable = this.items.get(i)
      val va: com.badlogic.gdx.graphics.VertexAttributes = renderable.meshPart.mesh.getVertexAttributes()
      val mat: com.badlogic.gdx.graphics.g3d.Material = renderable.material
      val pt: scala.Int = renderable.meshPart.primitiveType
      val sameAttributes: scala.Boolean = va.equals(vertexAttributes)
      val indexedMesh: scala.Boolean = renderable.meshPart.mesh.getNumIndices() > 0
      val verticesToAdd: scala.Int = if (indexedMesh) renderable.meshPart.mesh.getNumVertices() else renderable.meshPart.size
      val canHoldVertices: scala.Boolean = (this.meshBuilder.getNumVertices() + verticesToAdd) <= com.badlogic.gdx.graphics.g3d.utils.MeshBuilder.MAX_VERTICES
      val sameMesh: scala.Boolean = sameAttributes && canHoldVertices
      val samePart: scala.Boolean = (sameMesh && (pt == primitiveType)) && mat.same(material, true)
      if (!samePart) {
        if (!sameMesh) {
          var mesh: com.badlogic.gdx.graphics.Mesh = this.meshBuilder.`end`(this.meshPool.obtain(vertexAttributes, this.meshBuilder.getNumVertices(), this.meshBuilder.getNumIndices()))
          while (offset < this.renderables.size) {
            this.renderables.get({ offset += 1; offset }).meshPart.mesh = mesh
          }
          this.meshBuilder.begin({
            vertexAttributes = va
            vertexAttributes
          })
        } else ()
        val newPart: com.badlogic.gdx.graphics.g3d.model.MeshPart = this.meshBuilder.part("", pt, this.meshPartPool.obtain())
        val previous: com.badlogic.gdx.graphics.g3d.Renderable = this.renderables.get(this.renderables.size - 1)
        previous.meshPart.offset = part.offset
        previous.meshPart.size = part.size
        part = newPart
        this.renderables.add(this.obtainRenderable({
          material = mat
          material
        }, {
          primitiveType = pt
          primitiveType
        }))
      } else ()
      this.meshBuilder.setVertexTransform(renderable.worldTransform)
      this.meshBuilder.addMesh(renderable.meshPart.mesh, renderable.meshPart.offset, renderable.meshPart.size)
    }; i = i + 1 } }
    var mesh: com.badlogic.gdx.graphics.Mesh = this.meshBuilder.`end`(this.meshPool.obtain(vertexAttributes, this.meshBuilder.getNumVertices(), this.meshBuilder.getNumIndices()))
    while (offset < this.renderables.size) {
      this.renderables.get({ offset += 1; offset }).meshPart.mesh = mesh
    }
    val previous: com.badlogic.gdx.graphics.g3d.Renderable = this.renderables.get(this.renderables.size - 1)
    previous.meshPart.offset = part.offset
    previous.meshPart.size = part.size
  }
  def add(renderable: com.badlogic.gdx.graphics.g3d.Renderable): scala.Unit = {
    if (!this.building) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Can only add items to the ModelCache in between .begin() and .end()")
    } else ()
    if (renderable.bones == null) {
      this.items.add(renderable)
    } else {
      this.renderables.add(renderable)
    }
  }
  def add(renderableProvider: com.badlogic.gdx.graphics.g3d.RenderableProvider): scala.Unit = {
    renderableProvider.getRenderables(this.tmp, this.renderablesPool);
    { var i: scala.Int = 0; val n: scala.Int = this.tmp.size; while (i < n) { {
      this.add(this.tmp.get(i))
    }; i = i + 1 } }
    this.tmp.clear()
  }
  def add[T <: com.badlogic.gdx.graphics.g3d.RenderableProvider](renderableProviders: balticporter.runtime.JavaIterable[T]): scala.Unit = {
    for (renderableProvider <- renderableProviders) {
      this.add(renderableProvider)
    }
  }
  def getRenderables(renderables: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.Renderable], pool: com.badlogic.gdx.utils.Pool[com.badlogic.gdx.graphics.g3d.Renderable]): scala.Unit = {
    if (this.building) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Cannot render a ModelCache in between .begin() and .end()")
    } else ()
    for (r <- this.renderables) {
      r.shader = null
      r.environment = null
    }
    renderables.addAll(this.renderables)
  }
  def dispose(): scala.Unit = {
    if (this.building) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Cannot dispose a ModelCache in between .begin() and .end()")
    } else ()
    this.meshPool.dispose()
  }
}
object ModelCache {
  trait MeshPool extends com.badlogic.gdx.utils.Disposable {
    def obtain(vertexAttributes: com.badlogic.gdx.graphics.VertexAttributes, vertexCount: scala.Int, indexCount: scala.Int): com.badlogic.gdx.graphics.Mesh
    def flush(): scala.Unit
  }
  class SimpleMeshPool extends com.badlogic.gdx.graphics.g3d.ModelCache.MeshPool {
    private var freeMeshes: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.Mesh] = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.Mesh]()
    private var usedMeshes: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.Mesh] = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.Mesh]()
    def flush(): scala.Unit = {
      this.freeMeshes.addAll(this.usedMeshes)
      this.usedMeshes.clear()
    }
    def obtain(vertexAttributes: com.badlogic.gdx.graphics.VertexAttributes, vertexCount$arg: scala.Int, indexCount$arg: scala.Int): com.badlogic.gdx.graphics.Mesh = {
      var vertexCount: scala.Int = vertexCount$arg
      var indexCount: scala.Int = indexCount$arg;
      { var i: scala.Int = 0; val n: scala.Int = this.freeMeshes.size; while (i < n) { {
        val mesh: com.badlogic.gdx.graphics.Mesh = this.freeMeshes.get(i)
        if ((mesh.getVertexAttributes().equals(vertexAttributes) && (mesh.getMaxVertices() >= vertexCount)) && (mesh.getMaxIndices() >= indexCount)) {
          this.freeMeshes.removeIndex(i)
          this.usedMeshes.add(mesh)
          return mesh
        } else ()
      }; i = i + 1 } }
      vertexCount = com.badlogic.gdx.graphics.g3d.utils.MeshBuilder.MAX_VERTICES
      indexCount = java.lang.Math.max(vertexCount, 1 << (32 - java.lang.Integer.numberOfLeadingZeros(indexCount - 1)))
      val result: com.badlogic.gdx.graphics.Mesh = new com.badlogic.gdx.graphics.Mesh(false, vertexCount, indexCount, vertexAttributes)
      this.usedMeshes.add(result)
      return result
    }
    def dispose(): scala.Unit = {
      for (m <- this.usedMeshes) {
        m.dispose()
      }
      this.usedMeshes.clear()
      for (m <- this.freeMeshes) {
        m.dispose()
      }
      this.freeMeshes.clear()
    }
  }
  class TightMeshPool extends com.badlogic.gdx.graphics.g3d.ModelCache.MeshPool {
    private var freeMeshes: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.Mesh] = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.Mesh]()
    private var usedMeshes: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.Mesh] = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.Mesh]()
    def flush(): scala.Unit = {
      this.freeMeshes.addAll(this.usedMeshes)
      this.usedMeshes.clear()
    }
    def obtain(vertexAttributes: com.badlogic.gdx.graphics.VertexAttributes, vertexCount: scala.Int, indexCount: scala.Int): com.badlogic.gdx.graphics.Mesh = {
      { var i: scala.Int = 0; val n: scala.Int = this.freeMeshes.size; while (i < n) { {
        val mesh: com.badlogic.gdx.graphics.Mesh = this.freeMeshes.get(i)
        if ((mesh.getVertexAttributes().equals(vertexAttributes) && (mesh.getMaxVertices() == vertexCount)) && (mesh.getMaxIndices() == indexCount)) {
          this.freeMeshes.removeIndex(i)
          this.usedMeshes.add(mesh)
          return mesh
        } else ()
      }; i = i + 1 } }
      val result: com.badlogic.gdx.graphics.Mesh = new com.badlogic.gdx.graphics.Mesh(true, vertexCount, indexCount, vertexAttributes)
      this.usedMeshes.add(result)
      return result
    }
    def dispose(): scala.Unit = {
      for (m <- this.usedMeshes) {
        m.dispose()
      }
      this.usedMeshes.clear()
      for (m <- this.freeMeshes) {
        m.dispose()
      }
      this.freeMeshes.clear()
    }
  }
  class Sorter extends com.badlogic.gdx.graphics.g3d.utils.RenderableSorter with java.util.Comparator[com.badlogic.gdx.graphics.g3d.Renderable] {
    def sort(camera: com.badlogic.gdx.graphics.Camera, renderables: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.Renderable]): scala.Unit = {
      renderables.sort(this)
    }
    def compare(arg0: com.badlogic.gdx.graphics.g3d.Renderable, arg1: com.badlogic.gdx.graphics.g3d.Renderable): scala.Int = {
      val va0: com.badlogic.gdx.graphics.VertexAttributes = arg0.meshPart.mesh.getVertexAttributes()
      val va1: com.badlogic.gdx.graphics.VertexAttributes = arg1.meshPart.mesh.getVertexAttributes()
      val vc: scala.Int = va0.compareTo(va1)
      if (vc == 0) {
        val mc: scala.Int = arg0.material.compareTo(arg1.material)
        if (mc == 0) {
          return arg0.meshPart.primitiveType - arg1.meshPart.primitiveType
        } else ()
        return mc
      } else ()
      return vc
    }
  }
}