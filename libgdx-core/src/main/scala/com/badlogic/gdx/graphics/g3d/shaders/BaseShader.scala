package com.badlogic.gdx.graphics.g3d.shaders

abstract class BaseShader extends com.badlogic.gdx.graphics.g3d.Shader {
  private final val uniforms: com.badlogic.gdx.utils.Array[java.lang.String] = new com.badlogic.gdx.utils.Array[java.lang.String]()
  private final val validators: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Validator] = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Validator]()
  private final val setters: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Setter] = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Setter]()
  private var locations: scala.Array[scala.Int] = null.asInstanceOf[scala.Array[scala.Int]]
  private final val globalUniforms: com.badlogic.gdx.utils.IntArray = new com.badlogic.gdx.utils.IntArray()
  private final val localUniforms: com.badlogic.gdx.utils.IntArray = new com.badlogic.gdx.utils.IntArray()
  private final val attributes: com.badlogic.gdx.utils.IntIntMap = new com.badlogic.gdx.utils.IntIntMap()
  private final val instancedAttributes: com.badlogic.gdx.utils.IntIntMap = new com.badlogic.gdx.utils.IntIntMap()
  var program: com.badlogic.gdx.graphics.glutils.ShaderProgram = null.asInstanceOf[com.badlogic.gdx.graphics.glutils.ShaderProgram]
  var context: com.badlogic.gdx.graphics.g3d.utils.RenderContext = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.utils.RenderContext]
  var camera: com.badlogic.gdx.graphics.Camera = null.asInstanceOf[com.badlogic.gdx.graphics.Camera]
  private var currentMesh: com.badlogic.gdx.graphics.Mesh = null.asInstanceOf[com.badlogic.gdx.graphics.Mesh]
  private final val tempArray: com.badlogic.gdx.utils.IntArray = new com.badlogic.gdx.utils.IntArray()
  private final val tempArray2: com.badlogic.gdx.utils.IntArray = new com.badlogic.gdx.utils.IntArray()
  private var combinedAttributes: com.badlogic.gdx.graphics.g3d.Attributes = new com.badlogic.gdx.graphics.g3d.Attributes()
  def register(alias: java.lang.String, validator: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Validator, setter: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Setter): scala.Int = {
    if (this.locations != null) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Cannot register an uniform after initialization")
    } else ()
    val existing: scala.Int = this.getUniformID(alias)
    if (existing >= 0) {
      this.validators.set(existing, validator)
      this.setters.set(existing, setter)
      return existing
    } else ()
    this.uniforms.add(alias)
    this.validators.add(validator)
    this.setters.add(setter)
    return this.uniforms.size - 1
  }
  def register(alias: java.lang.String, validator: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Validator): scala.Int = {
    return this.register(alias, validator, null)
  }
  def register(alias: java.lang.String, setter: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Setter): scala.Int = {
    return this.register(alias, null, setter)
  }
  def register(alias: java.lang.String): scala.Int = {
    return this.register(alias, null, null)
  }
  def register(uniform: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform, setter: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Setter): scala.Int = {
    return this.register(uniform.alias, uniform, setter)
  }
  def register(uniform: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform): scala.Int = {
    return this.register(uniform, null)
  }
  def getUniformID(alias: java.lang.String): scala.Int = {
    val n: scala.Int = this.uniforms.size;
    { var i: scala.Int = 0; while (i < n) { {
      if (this.uniforms.get(i).equals(alias)) {
        return i
      } else ()
    }; i = i + 1 } }
    return -1
  }
  def getUniformAlias(id: scala.Int): java.lang.String = {
    return this.uniforms.get(id)
  }
  def init(program: com.badlogic.gdx.graphics.glutils.ShaderProgram, renderable: com.badlogic.gdx.graphics.g3d.Renderable): scala.Unit = {
    if (this.locations != null) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Already initialized")
    } else ()
    if (!program.isCompiled()) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException(program.getLog())
    } else ()
    this.program = program
    val n: scala.Int = this.uniforms.size
    this.locations = new scala.Array[scala.Int](n);
    { var i: scala.Int = 0; while (i < n) { {
      val input: java.lang.String = this.uniforms.get(i)
      val validator: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Validator = this.validators.get(i)
      val setter: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Setter = this.setters.get(i)
      if ((validator != null) && (!validator.validate(this, i, renderable))) {
        this.locations(i) = -1
      } else {
        this.locations(i) = program.fetchUniformLocation(input, false)
        if ((this.locations(i) >= 0) && (setter != null)) {
          if (setter.isGlobal(this, i)) {
            this.globalUniforms.add(i)
          } else {
            this.localUniforms.add(i)
          }
        } else ()
      }
      if (this.locations(i) < 0) {
        this.validators.set(i, null)
        this.setters.set(i, null)
      } else ()
    }; i = i + 1 } }
    if (renderable != null) {
      val attrs: com.badlogic.gdx.graphics.VertexAttributes = renderable.meshPart.mesh.getVertexAttributes()
      val c: scala.Int = attrs.size();
      { var i: scala.Int = 0; while (i < c) { {
        val attr: com.badlogic.gdx.graphics.VertexAttribute = attrs.get(i)
        val location: scala.Int = program.getAttributeLocation(attr.alias)
        if (location >= 0) {
          this.attributes.put(attr.getKey(), location)
        } else ()
      }; i = i + 1 } }
      val iattrs: com.badlogic.gdx.graphics.VertexAttributes = renderable.meshPart.mesh.getInstancedAttributes()
      if (iattrs != null) {
        val ic: scala.Int = iattrs.size();
        { var i: scala.Int = 0; while (i < ic) { {
          val attr: com.badlogic.gdx.graphics.VertexAttribute = iattrs.get(i)
          val location: scala.Int = program.getAttributeLocation(attr.alias)
          if (location >= 0) {
            this.instancedAttributes.put(attr.getKey(), location)
          } else ()
        }; i = i + 1 } }
      } else ()
    } else ()
  }
  def begin(camera: com.badlogic.gdx.graphics.Camera, context: com.badlogic.gdx.graphics.g3d.utils.RenderContext): scala.Unit = {
    this.camera = camera
    this.context = context
    this.program.bind()
    this.currentMesh = null;
    { var u: scala.Int = 0; var i: scala.Int = 0; while (i < this.globalUniforms.size) { {
      if (this.setters.get({
        u = this.globalUniforms.get(i)
        u
      }) != null) {
        this.setters.get(u).set(this, u, null, null)
      } else ()
    }; i = i + 1 } }
  }
  private final def getAttributeLocations(attrs: com.badlogic.gdx.graphics.VertexAttributes): scala.Array[scala.Int] = {
    this.tempArray.clear()
    val n: scala.Int = attrs.size();
    { var i: scala.Int = 0; while (i < n) { {
      this.tempArray.add(this.attributes.get(attrs.get(i).getKey(), -1))
    }; i = i + 1 } }
    this.tempArray.shrink()
    return this.tempArray.items
  }
  private final def getInstancedAttributeLocations(attrs: com.badlogic.gdx.graphics.VertexAttributes): scala.Array[scala.Int] = {
    if (attrs == null) {
      return null
    } else ()
    this.tempArray2.clear()
    val n: scala.Int = attrs.size();
    { var i: scala.Int = 0; while (i < n) { {
      this.tempArray2.add(this.instancedAttributes.get(attrs.get(i).getKey(), -1))
    }; i = i + 1 } }
    this.tempArray2.shrink()
    return this.tempArray2.items
  }
  def render(renderable: com.badlogic.gdx.graphics.g3d.Renderable): scala.Unit = {
    if (renderable.worldTransform.det3x3() == 0) {
      return
    } else ()
    this.combinedAttributes.clear()
    if (renderable.environment != null) {
      this.combinedAttributes.set(renderable.environment)
    } else ()
    if (renderable.material != null) {
      this.combinedAttributes.set(renderable.material)
    } else ()
    this.render(renderable, this.combinedAttributes)
  }
  def render(renderable: com.badlogic.gdx.graphics.g3d.Renderable, combinedAttributes: com.badlogic.gdx.graphics.g3d.Attributes): scala.Unit = {
    { var u: scala.Int = 0; var i: scala.Int = 0; while (i < this.localUniforms.size) { {
      if (this.setters.get({
        u = this.localUniforms.get(i)
        u
      }) != null) {
        this.setters.get(u).set(this, u, renderable, combinedAttributes)
      } else ()
    }; i = i + 1 } }
    if (this.currentMesh != renderable.meshPart.mesh) {
      if (this.currentMesh != null) {
        this.currentMesh.unbind(this.program, this.tempArray.items, this.tempArray2.items)
      } else ()
      this.currentMesh = renderable.meshPart.mesh
      this.currentMesh.bind(this.program, this.getAttributeLocations(renderable.meshPart.mesh.getVertexAttributes()), this.getInstancedAttributeLocations(renderable.meshPart.mesh.getInstancedAttributes()))
    } else ()
    renderable.meshPart.render(this.program, false)
  }
  def `end`(): scala.Unit = {
    if (this.currentMesh != null) {
      this.currentMesh.unbind(this.program, this.tempArray.items, this.tempArray2.items)
      this.currentMesh = null
    } else ()
  }
  def dispose(): scala.Unit = {
    this.program = null
    this.uniforms.clear()
    this.validators.clear()
    this.setters.clear()
    this.localUniforms.clear()
    this.globalUniforms.clear()
    this.locations = null
  }
  final def has(inputID: scala.Int): scala.Boolean = {
    return ((inputID >= 0) && (inputID < this.locations.length)) && (this.locations(inputID) >= 0)
  }
  final def loc(inputID: scala.Int): scala.Int = {
    return if ((inputID >= 0) && (inputID < this.locations.length)) this.locations(inputID) else -1
  }
  final def set(uniform: scala.Int, value: com.badlogic.gdx.math.Matrix4): scala.Boolean = {
    if (this.locations(uniform) < 0) {
      return false
    } else ()
    this.program.setUniformMatrix(this.locations(uniform), value)
    return true
  }
  final def set(uniform: scala.Int, value: com.badlogic.gdx.math.Matrix3): scala.Boolean = {
    if (this.locations(uniform) < 0) {
      return false
    } else ()
    this.program.setUniformMatrix(this.locations(uniform), value)
    return true
  }
  final def set(uniform: scala.Int, value: com.badlogic.gdx.math.Vector3): scala.Boolean = {
    if (this.locations(uniform) < 0) {
      return false
    } else ()
    this.program.setUniformf(this.locations(uniform), value)
    return true
  }
  final def set(uniform: scala.Int, value: com.badlogic.gdx.math.Vector2): scala.Boolean = {
    if (this.locations(uniform) < 0) {
      return false
    } else ()
    this.program.setUniformf(this.locations(uniform), value)
    return true
  }
  final def set(uniform: scala.Int, value: com.badlogic.gdx.graphics.Color): scala.Boolean = {
    if (this.locations(uniform) < 0) {
      return false
    } else ()
    this.program.setUniformf(this.locations(uniform), value)
    return true
  }
  final def set(uniform: scala.Int, value: scala.Float): scala.Boolean = {
    if (this.locations(uniform) < 0) {
      return false
    } else ()
    this.program.setUniformf(this.locations(uniform), value)
    return true
  }
  final def set(uniform: scala.Int, v1: scala.Float, v2: scala.Float): scala.Boolean = {
    if (this.locations(uniform) < 0) {
      return false
    } else ()
    this.program.setUniformf(this.locations(uniform), v1, v2)
    return true
  }
  final def set(uniform: scala.Int, v1: scala.Float, v2: scala.Float, v3: scala.Float): scala.Boolean = {
    if (this.locations(uniform) < 0) {
      return false
    } else ()
    this.program.setUniformf(this.locations(uniform), v1, v2, v3)
    return true
  }
  final def set(uniform: scala.Int, v1: scala.Float, v2: scala.Float, v3: scala.Float, v4: scala.Float): scala.Boolean = {
    if (this.locations(uniform) < 0) {
      return false
    } else ()
    this.program.setUniformf(this.locations(uniform), v1, v2, v3, v4)
    return true
  }
  final def set(uniform: scala.Int, value: scala.Int): scala.Boolean = {
    if (this.locations(uniform) < 0) {
      return false
    } else ()
    this.program.setUniformi(this.locations(uniform), value)
    return true
  }
  final def set(uniform: scala.Int, v1: scala.Int, v2: scala.Int): scala.Boolean = {
    if (this.locations(uniform) < 0) {
      return false
    } else ()
    this.program.setUniformi(this.locations(uniform), v1, v2)
    return true
  }
  final def set(uniform: scala.Int, v1: scala.Int, v2: scala.Int, v3: scala.Int): scala.Boolean = {
    if (this.locations(uniform) < 0) {
      return false
    } else ()
    this.program.setUniformi(this.locations(uniform), v1, v2, v3)
    return true
  }
  final def set(uniform: scala.Int, v1: scala.Int, v2: scala.Int, v3: scala.Int, v4: scala.Int): scala.Boolean = {
    if (this.locations(uniform) < 0) {
      return false
    } else ()
    this.program.setUniformi(this.locations(uniform), v1, v2, v3, v4)
    return true
  }
  final def set(uniform: scala.Int, textureDesc: com.badlogic.gdx.graphics.g3d.utils.TextureDescriptor[?]): scala.Boolean = {
    if (this.locations(uniform) < 0) {
      return false
    } else ()
    this.program.setUniformi(this.locations(uniform), this.context.textureBinder.bind(textureDesc.asInstanceOf[com.badlogic.gdx.graphics.g3d.utils.TextureDescriptor[?]]))
    return true
  }
  final def set(uniform: scala.Int, texture: com.badlogic.gdx.graphics.GLTexture): scala.Boolean = {
    if (this.locations(uniform) < 0) {
      return false
    } else ()
    this.program.setUniformi(this.locations(uniform), this.context.textureBinder.bind(texture))
    return true
  }
}
object BaseShader {
  trait Validator {
    def validate(shader: BaseShader, inputID: scala.Int, renderable: com.badlogic.gdx.graphics.g3d.Renderable): scala.Boolean
  }
  trait Setter {
    def isGlobal(shader: BaseShader, inputID: scala.Int): scala.Boolean
    def set(shader: BaseShader, inputID: scala.Int, renderable: com.badlogic.gdx.graphics.g3d.Renderable, combinedAttributes: com.badlogic.gdx.graphics.g3d.Attributes): scala.Unit
  }
  abstract class GlobalSetter extends com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Setter {
    def isGlobal(shader: BaseShader, inputID: scala.Int): scala.Boolean = {
      return true
    }
  }
  abstract class LocalSetter extends com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Setter {
    def isGlobal(shader: BaseShader, inputID: scala.Int): scala.Boolean = {
      return false
    }
  }
  class Uniform(alias$p: java.lang.String, materialMask$p: scala.Long, environmentMask$p: scala.Long, overallMask$p: scala.Long) extends com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Validator {
    var alias: java.lang.String = null.asInstanceOf[java.lang.String]
    var materialMask: scala.Long = 0L
    var environmentMask: scala.Long = 0L
    var overallMask: scala.Long = 0L
    def this(alias: java.lang.String, materialMask: scala.Long, environmentMask: scala.Long) = {
      this(alias, materialMask, environmentMask, 0)
    }
    def this(alias: java.lang.String, overallMask: scala.Long) = {
      this(alias, 0, 0, overallMask)
    }
    def this(alias: java.lang.String) = {
      this(alias, 0, 0)
    }
    this.alias = alias$p
    this.materialMask = materialMask$p
    this.environmentMask = environmentMask$p
    this.overallMask = overallMask$p
    def validate(shader: BaseShader, inputID: scala.Int, renderable: com.badlogic.gdx.graphics.g3d.Renderable): scala.Boolean = {
      val matFlags: scala.Long = if ((renderable != null) && (renderable.material != null)) renderable.material.getMask() else 0
      val envFlags: scala.Long = if ((renderable != null) && (renderable.environment != null)) renderable.environment.getMask() else 0
      return (((matFlags & this.materialMask) == this.materialMask) && ((envFlags & this.environmentMask) == this.environmentMask)) && (((matFlags | envFlags) & this.overallMask) == this.overallMask)
    }
  }
}