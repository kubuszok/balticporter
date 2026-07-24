package com.badlogic.gdx.graphics.g2d

class RepeatablePolygonSprite {
  private var region: com.badlogic.gdx.graphics.g2d.TextureRegion = null.asInstanceOf[com.badlogic.gdx.graphics.g2d.TextureRegion]
  private var density: scala.Float = 0.0f
  private var dirty: scala.Boolean = true
  private var parts: com.badlogic.gdx.utils.Array[scala.Array[scala.Float]] = new com.badlogic.gdx.utils.Array[scala.Array[scala.Float]]()
  private var vertices: com.badlogic.gdx.utils.Array[scala.Array[scala.Float]] = new com.badlogic.gdx.utils.Array[scala.Array[scala.Float]]()
  private var indices: com.badlogic.gdx.utils.Array[scala.Array[scala.Short]] = new com.badlogic.gdx.utils.Array[scala.Array[scala.Short]]()
  private var cols: scala.Int = 0
  private var rows: scala.Int = 0
  private var gridWidth: scala.Float = 0.0f
  private var gridHeight: scala.Float = 0.0f
  var x: scala.Float = 0
  var y: scala.Float = 0
  private var color: com.badlogic.gdx.graphics.Color = com.badlogic.gdx.graphics.Color.WHITE
  var offset$field: com.badlogic.gdx.math.Vector2 = new com.badlogic.gdx.math.Vector2()
  def setPolygon(region: com.badlogic.gdx.graphics.g2d.TextureRegion, vertices: scala.Array[scala.Float]): scala.Unit = {
    this.setPolygon(region, vertices, -1)
  }
  def setPolygon(region: com.badlogic.gdx.graphics.g2d.TextureRegion, vertices$arg: scala.Array[scala.Float], density$arg: scala.Float): scala.Unit = {
    var vertices: scala.Array[scala.Float] = vertices$arg
    var density: scala.Float = density$arg
    this.region = region
    vertices = this.offset(vertices)
    val polygon: com.badlogic.gdx.math.Polygon = new com.badlogic.gdx.math.Polygon(vertices)
    val tmpPoly: com.badlogic.gdx.math.Polygon = new com.badlogic.gdx.math.Polygon()
    val intersectionPoly: com.badlogic.gdx.math.Polygon = new com.badlogic.gdx.math.Polygon()
    val triangulator: com.badlogic.gdx.math.EarClippingTriangulator = new com.badlogic.gdx.math.EarClippingTriangulator()
    var idx: scala.Int = 0
    val boundRect: com.badlogic.gdx.math.Rectangle = polygon.getBoundingRectangle()
    if (density == (-1)) {
      density = boundRect.getWidth() / region.getRegionWidth()
    } else ()
    val regionAspectRatio: scala.Float = region.getRegionHeight().asInstanceOf[scala.Float] / region.getRegionWidth().asInstanceOf[scala.Float]
    this.cols = java.lang.Math.ceil(density).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
    this.gridWidth = boundRect.getWidth() / density
    this.gridHeight = regionAspectRatio * this.gridWidth
    this.rows = java.lang.Math.ceil(boundRect.getHeight() / this.gridHeight).asInstanceOf[scala.Int].asInstanceOf[scala.Int];
    { var col: scala.Int = 0; while (col < this.cols) { {
      { var row: scala.Int = 0; while (row < this.rows) { {
        var verts: scala.Array[scala.Float] = new scala.Array[scala.Float](8)
        idx = 0
        verts({ idx += 1; idx }) = col * this.gridWidth
        verts({ idx += 1; idx }) = row * this.gridHeight
        verts({ idx += 1; idx }) = col * this.gridWidth
        verts({ idx += 1; idx }) = (row + 1) * this.gridHeight
        verts({ idx += 1; idx }) = (col + 1) * this.gridWidth
        verts({ idx += 1; idx }) = (row + 1) * this.gridHeight
        verts({ idx += 1; idx }) = (col + 1) * this.gridWidth
        verts(idx) = row * this.gridHeight
        tmpPoly.setVertices(verts)
        com.badlogic.gdx.math.Intersector.intersectPolygons(polygon, tmpPoly, intersectionPoly)
        verts = intersectionPoly.getVertices()
        if (verts.length > 0) {
          this.parts.add(this.snapToGrid(verts))
          val arr: com.badlogic.gdx.utils.ShortArray = triangulator.computeTriangles(verts)
          this.indices.add(arr.toArray())
        } else {
          this.parts.add(null)
        }
      }; row = row + 1 } }
    }; col = col + 1 } }
    this.buildVertices()
  }
  private def snapToGrid(vertices: scala.Array[scala.Float]): scala.Array[scala.Float] = {
    { var i: scala.Int = 0; while (i < vertices.length) { {
      val numX: scala.Float = (vertices(i) / this.gridWidth) % 1
      val numY: scala.Float = (vertices(i + 1) / this.gridHeight) % 1
      if ((numX > 0.99f) || (numX < 0.01f)) {
        vertices(i) = this.gridWidth * java.lang.Math.round(vertices(i) / this.gridWidth)
      } else ()
      if ((numY > 0.99f) || (numY < 0.01f)) {
        vertices(i + 1) = this.gridHeight * java.lang.Math.round(vertices(i + 1) / this.gridHeight)
      } else ()
    }; i = i + 2 } }
    return vertices
  }
  private def offset(vertices: scala.Array[scala.Float]): scala.Array[scala.Float] = {
    this.offset$field.set(vertices(0), vertices(1));
    { var i: scala.Int = 0; while (i < (vertices.length - 1)) { {
      if (this.offset$field.x > vertices(i)) {
        this.offset$field.x = vertices(i)
      } else ()
      if (this.offset$field.y > vertices(i + 1)) {
        this.offset$field.y = vertices(i + 1)
      } else ()
    }; i = i + 2 } };
    { var i: scala.Int = 0; while (i < vertices.length) { {
      vertices(i) = vertices(i) - this.offset$field.x
      vertices(i + 1) = vertices(i + 1) - this.offset$field.y
    }; i = i + 2 } }
    return vertices
  }
  private def buildVertices(): scala.Unit = {
    this.vertices.clear();
    { var i: scala.Int = 0; while (i < this.parts.size) { {
      val verts: scala.Array[scala.Float] = this.parts.get(i)
      if (verts == null) {
        /* continue */ ()
      } else ()
      val fullVerts: scala.Array[scala.Float] = new scala.Array[scala.Float]((5 * verts.length) / 2)
      var idx: scala.Int = 0
      val col: scala.Int = i / this.rows
      val row: scala.Int = i % this.rows;
      { var j: scala.Int = 0; while (j < verts.length) { {
        fullVerts({ idx += 1; idx }) = (verts(j) + this.offset$field.x) + this.x
        fullVerts({ idx += 1; idx }) = (verts(j + 1) + this.offset$field.y) + this.y
        fullVerts({ idx += 1; idx }) = this.color.toFloatBits()
        var u: scala.Float = (verts(j) % this.gridWidth) / this.gridWidth
        var v: scala.Float = (verts(j + 1) % this.gridHeight) / this.gridHeight
        if (verts(j) == (col * this.gridWidth)) {
          u = 0.0f
        } else ()
        if (verts(j) == ((col + 1) * this.gridWidth)) {
          u = 1.0f
        } else ()
        if (verts(j + 1) == (row * this.gridHeight)) {
          v = 0.0f
        } else ()
        if (verts(j + 1) == ((row + 1) * this.gridHeight)) {
          v = 1.0f
        } else ()
        u = this.region.getU() + ((this.region.getU2() - this.region.getU()) * u)
        v = this.region.getV() + ((this.region.getV2() - this.region.getV()) * v)
        fullVerts({ idx += 1; idx }) = u
        fullVerts({ idx += 1; idx }) = v
      }; j = j + 2 } }
      this.vertices.add(fullVerts)
    }; i = i + 1 } }
    this.dirty = false
  }
  def draw(batch: com.badlogic.gdx.graphics.g2d.PolygonSpriteBatch): scala.Unit = {
    if (this.dirty) {
      this.buildVertices()
    } else ();
    { var i: scala.Int = 0; while (i < this.vertices.size) { {
      batch.draw(this.region.getTexture(), this.vertices.get(i), 0, this.vertices.get(i).length, this.indices.get(i), 0, this.indices.get(i).length)
    }; i = i + 1 } }
  }
  def setColor(color: com.badlogic.gdx.graphics.Color): scala.Unit = {
    this.color = color
    this.dirty = true
  }
  def setPosition(x: scala.Float, y: scala.Float): scala.Unit = {
    this.x = x
    this.y = y
    this.dirty = true
  }
}