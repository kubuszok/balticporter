package com.badlogic.gdx.graphics.g3d.particles

class ParticleShader(renderable$p: com.badlogic.gdx.graphics.g3d.Renderable, config$p: com.badlogic.gdx.graphics.g3d.particles.ParticleShader.Config, shaderProgram: com.badlogic.gdx.graphics.glutils.ShaderProgram) extends com.badlogic.gdx.graphics.g3d.shaders.BaseShader {
  private var renderable: com.badlogic.gdx.graphics.g3d.Renderable = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.Renderable]
  private var materialMask: scala.Long = 0L
  private var vertexMask: scala.Long = 0L
  var config: com.badlogic.gdx.graphics.g3d.particles.ParticleShader.Config = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.ParticleShader.Config]
  var currentMaterial: com.badlogic.gdx.graphics.g3d.Material = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.Material]
  def this(renderable: com.badlogic.gdx.graphics.g3d.Renderable, config: com.badlogic.gdx.graphics.g3d.particles.ParticleShader.Config, prefix: java.lang.String, vertexShader: java.lang.String, fragmentShader: java.lang.String) = {
    this(renderable, config, new com.badlogic.gdx.graphics.glutils.ShaderProgram(prefix + vertexShader, prefix + fragmentShader))
  }
  def this(renderable: com.badlogic.gdx.graphics.g3d.Renderable, config: com.badlogic.gdx.graphics.g3d.particles.ParticleShader.Config, prefix: java.lang.String) = {
    this(renderable, config, prefix, if (config.vertexShader != null) config.vertexShader else ParticleShader.getDefaultVertexShader(), if (config.fragmentShader != null) config.fragmentShader else ParticleShader.getDefaultFragmentShader())
  }
  def this(renderable: com.badlogic.gdx.graphics.g3d.Renderable, config: com.badlogic.gdx.graphics.g3d.particles.ParticleShader.Config) = {
    this(renderable, config, ParticleShader.createPrefix(renderable, config))
  }
  def this(renderable: com.badlogic.gdx.graphics.g3d.Renderable) = {
    this(renderable, new com.badlogic.gdx.graphics.g3d.particles.ParticleShader.Config())
  }
  this.config = config$p
  this.program = shaderProgram
  this.renderable = renderable$p
  this.materialMask = renderable$p.material.getMask() | ParticleShader.optionalAttributes
  this.vertexMask = renderable$p.meshPart.mesh.getVertexAttributes().getMask()
  if ((!config$p.ignoreUnimplemented) && ((ParticleShader.implementedFlags & this.materialMask) != this.materialMask)) {
    throw new com.badlogic.gdx.utils.GdxRuntimeException(("Some attributes not implemented yet (" + this.materialMask) + ")")
  } else ()
  this.register(com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Inputs.viewTrans, com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Setters.viewTrans)
  this.register(com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Inputs.projViewTrans, com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Setters.projViewTrans)
  this.register(com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Inputs.projTrans, com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Setters.projTrans)
  this.register(com.badlogic.gdx.graphics.g3d.particles.ParticleShader.Inputs.screenWidth, com.badlogic.gdx.graphics.g3d.particles.ParticleShader.Setters.screenWidth)
  this.register(com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Inputs.cameraUp, com.badlogic.gdx.graphics.g3d.particles.ParticleShader.Setters.cameraUp)
  this.register(com.badlogic.gdx.graphics.g3d.particles.ParticleShader.Inputs.cameraRight, com.badlogic.gdx.graphics.g3d.particles.ParticleShader.Setters.cameraRight)
  this.register(com.badlogic.gdx.graphics.g3d.particles.ParticleShader.Inputs.cameraInvDirection, com.badlogic.gdx.graphics.g3d.particles.ParticleShader.Setters.cameraInvDirection)
  this.register(com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Inputs.cameraPosition, com.badlogic.gdx.graphics.g3d.particles.ParticleShader.Setters.cameraPosition)
  this.register(com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Inputs.diffuseTexture, com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Setters.diffuseTexture)
  def init(): scala.Unit = {
    var program: com.badlogic.gdx.graphics.glutils.ShaderProgram = this.program
    this.program = null
    this.init(program, this.renderable)
    this.renderable = null
  }
  def canRender(renderable: com.badlogic.gdx.graphics.g3d.Renderable): scala.Boolean = {
    return (this.materialMask == (renderable.material.getMask() | ParticleShader.optionalAttributes)) && (this.vertexMask == renderable.meshPart.mesh.getVertexAttributes().getMask())
  }
  def compareTo(other: com.badlogic.gdx.graphics.g3d.Shader): scala.Int = {
    if (other == null) {
      return -1
    } else ()
    if (other == this) {
      return 0
    } else ()
    return 0
  }
  def equals(obj: java.lang.Object): scala.Boolean = {
    return obj.isInstanceOf[ParticleShader] && this.equals(obj.asInstanceOf[ParticleShader].asInstanceOf[ParticleShader])
  }
  def equals(obj: ParticleShader): scala.Boolean = {
    return obj == this
  }
  def begin(camera: com.badlogic.gdx.graphics.Camera, context: com.badlogic.gdx.graphics.g3d.utils.RenderContext): scala.Unit = {
    super.begin(camera, context)
  }
  def render(renderable: com.badlogic.gdx.graphics.g3d.Renderable): scala.Unit = {
    if (!renderable.material.has(com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute.Type)) {
      context.setBlending(false, com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA, com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA)
    } else ()
    this.bindMaterial(renderable)
    super.render(renderable)
  }
  def `end`(): scala.Unit = {
    this.currentMaterial = null
    super.`end`()
  }
  def bindMaterial(renderable: com.badlogic.gdx.graphics.g3d.Renderable): scala.Unit = {
    if (this.currentMaterial == renderable.material) {
      return
    } else ()
    val cullFace: scala.Int = if (this.config.defaultCullFace == (-1)) com.badlogic.gdx.graphics.GL20.GL_BACK else this.config.defaultCullFace
    var depthFunc: scala.Int = if (this.config.defaultDepthFunc == (-1)) com.badlogic.gdx.graphics.GL20.GL_LEQUAL else this.config.defaultDepthFunc
    var depthRangeNear: scala.Float = 0.0f
    var depthRangeFar: scala.Float = 1.0f
    var depthMask: scala.Boolean = true
    this.currentMaterial = renderable.material
    for (attr <- this.currentMaterial) {
      val t: scala.Long = attr.`type`
      if (com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute.is(t)) {
        context.setBlending(true, attr.asInstanceOf[com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute].sourceFunction, attr.asInstanceOf[com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute].destFunction)
      } else {
        if ((t & com.badlogic.gdx.graphics.g3d.attributes.DepthTestAttribute.Type) == com.badlogic.gdx.graphics.g3d.attributes.DepthTestAttribute.Type) {
          val dta: com.badlogic.gdx.graphics.g3d.attributes.DepthTestAttribute = attr.asInstanceOf[com.badlogic.gdx.graphics.g3d.attributes.DepthTestAttribute]
          depthFunc = dta.depthFunc
          depthRangeNear = dta.depthRangeNear
          depthRangeFar = dta.depthRangeFar
          depthMask = dta.depthMask
        } else {
          if (!this.config.ignoreUnimplemented) {
            throw new com.badlogic.gdx.utils.GdxRuntimeException("Unknown material attribute: " + attr.toString())
          } else ()
        }
      }
    }
    context.setCullFace(cullFace)
    context.setDepthTest(depthFunc, depthRangeNear, depthRangeFar)
    context.setDepthMask(depthMask)
  }
  def dispose(): scala.Unit = {
    program.dispose()
    super.dispose()
  }
  def getDefaultCullFace(): scala.Int = {
    return if (this.config.defaultCullFace == (-1)) com.badlogic.gdx.graphics.GL20.GL_BACK else this.config.defaultCullFace
  }
  def setDefaultCullFace(cullFace: scala.Int): scala.Unit = {
    this.config.defaultCullFace = cullFace
  }
  def getDefaultDepthFunc(): scala.Int = {
    return if (this.config.defaultDepthFunc == (-1)) com.badlogic.gdx.graphics.GL20.GL_LEQUAL else this.config.defaultDepthFunc
  }
  def setDefaultDepthFunc(depthFunc: scala.Int): scala.Unit = {
    this.config.defaultDepthFunc = depthFunc
  }
}
object ParticleShader {
  export com.badlogic.gdx.graphics.g3d.shaders.BaseShader.{AlignMode => _, Config => _, Inputs => _, ParticleType => _, Setters => _, TMP_VECTOR3 => _, createPrefix => _, defaultFragmentShader => _, defaultVertexShader => _, getDefaultFragmentShader => _, getDefaultVertexShader => _, implementedFlags => _, optionalAttributes => _, *}
  private var defaultVertexShader: java.lang.String = null
  private var defaultFragmentShader: java.lang.String = null
  var implementedFlags: scala.Long = com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute.Type | com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.Diffuse
  final val TMP_VECTOR3: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  private final val optionalAttributes: scala.Long = com.badlogic.gdx.graphics.g3d.attributes.IntAttribute.CullFace | com.badlogic.gdx.graphics.g3d.attributes.DepthTestAttribute.Type
  def getDefaultVertexShader(): java.lang.String = {
    if (ParticleShader.defaultVertexShader == null) {
      ParticleShader.defaultVertexShader = com.badlogic.gdx.Gdx.files.classpath("com/badlogic/gdx/graphics/g3d/particles/particles.vertex.glsl").readString()
    } else ()
    return ParticleShader.defaultVertexShader
  }
  def getDefaultFragmentShader(): java.lang.String = {
    if (ParticleShader.defaultFragmentShader == null) {
      ParticleShader.defaultFragmentShader = com.badlogic.gdx.Gdx.files.classpath("com/badlogic/gdx/graphics/g3d/particles/particles.fragment.glsl").readString()
    } else ()
    return ParticleShader.defaultFragmentShader
  }
  def createPrefix(renderable: com.badlogic.gdx.graphics.g3d.Renderable, config: com.badlogic.gdx.graphics.g3d.particles.ParticleShader.Config): java.lang.String = {
    var prefix: java.lang.String = ""
    if (com.badlogic.gdx.Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Desktop) {
      prefix = prefix + "#version 120\n"
    } else {
      prefix = prefix + "#version 100\n"
    }
    if (config.`type` == com.badlogic.gdx.graphics.g3d.particles.ParticleShader.ParticleType.Billboard) {
      prefix = prefix + "#define billboard\n"
      if (config.align == com.badlogic.gdx.graphics.g3d.particles.ParticleShader.AlignMode.Screen) {
        prefix = prefix + "#define screenFacing\n"
      } else {
        if (config.align == com.badlogic.gdx.graphics.g3d.particles.ParticleShader.AlignMode.ViewPoint) {
          prefix = prefix + "#define viewPointFacing\n"
        } else ()
      }
    } else ()
    return prefix
  }
  sealed abstract class ParticleType {
    def name(): java.lang.String = this.toString()
  }
  object ParticleType {
    case object Billboard extends ParticleType
    case object Point extends ParticleType
    def values(): scala.Array[ParticleType] = scala.Array(Billboard, Point)
    def valueOf(name: java.lang.String): ParticleType = name match {
      case "Billboard" => Billboard
      case "Point" => Point
      case _ => throw new java.lang.IllegalArgumentException(name)
    }
  }
  sealed abstract class AlignMode {
    def name(): java.lang.String = this.toString()
  }
  object AlignMode {
    case object Screen extends AlignMode
    case object ViewPoint extends AlignMode
    def values(): scala.Array[AlignMode] = scala.Array(Screen, ViewPoint)
    def valueOf(name: java.lang.String): AlignMode = name match {
      case "Screen" => Screen
      case "ViewPoint" => ViewPoint
      case _ => throw new java.lang.IllegalArgumentException(name)
    }
  }
  class Config {
    var vertexShader: java.lang.String = null
    var fragmentShader: java.lang.String = null
    var ignoreUnimplemented: scala.Boolean = true
    var defaultCullFace: scala.Int = -1
    var defaultDepthFunc: scala.Int = -1
    var align: com.badlogic.gdx.graphics.g3d.particles.ParticleShader.AlignMode = com.badlogic.gdx.graphics.g3d.particles.ParticleShader.AlignMode.Screen
    var `type`: com.badlogic.gdx.graphics.g3d.particles.ParticleShader.ParticleType = com.badlogic.gdx.graphics.g3d.particles.ParticleShader.ParticleType.Billboard
    def this(align: com.badlogic.gdx.graphics.g3d.particles.ParticleShader.AlignMode, `type`: com.badlogic.gdx.graphics.g3d.particles.ParticleShader.ParticleType) = {
      this()
      this.align = align
      this.`type` = `type`
    }
    def this(align: com.badlogic.gdx.graphics.g3d.particles.ParticleShader.AlignMode) = {
      this()
      this.align = align
    }
    def this(`type`: com.badlogic.gdx.graphics.g3d.particles.ParticleShader.ParticleType) = {
      this()
      this.`type` = `type`
    }
    def this(vertexShader: java.lang.String, fragmentShader: java.lang.String) = {
      this()
      this.vertexShader = vertexShader
      this.fragmentShader = fragmentShader
    }
  }
  object Inputs {
    final val cameraRight: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform("u_cameraRight")
    final val cameraInvDirection: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform("u_cameraInvDirection")
    final val screenWidth: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform("u_screenWidth")
    final val regionSize: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform("u_regionSize")
  }
  object Setters {
    final val cameraRight: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Setter = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Setter() {
      override def isGlobal(shader: com.badlogic.gdx.graphics.g3d.shaders.BaseShader, inputID: scala.Int): scala.Boolean = {
        return true
      }
      override def set(shader: com.badlogic.gdx.graphics.g3d.shaders.BaseShader, inputID: scala.Int, renderable: com.badlogic.gdx.graphics.g3d.Renderable, combinedAttributes: com.badlogic.gdx.graphics.g3d.Attributes): scala.Unit = {
        shader.set(inputID, ParticleShader.TMP_VECTOR3.set(shader.camera.direction).crs(shader.camera.up).nor())
      }
    }
    final val cameraUp: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Setter = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Setter() {
      override def isGlobal(shader: com.badlogic.gdx.graphics.g3d.shaders.BaseShader, inputID: scala.Int): scala.Boolean = {
        return true
      }
      override def set(shader: com.badlogic.gdx.graphics.g3d.shaders.BaseShader, inputID: scala.Int, renderable: com.badlogic.gdx.graphics.g3d.Renderable, combinedAttributes: com.badlogic.gdx.graphics.g3d.Attributes): scala.Unit = {
        shader.set(inputID, ParticleShader.TMP_VECTOR3.set(shader.camera.up).nor())
      }
    }
    final val cameraInvDirection: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Setter = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Setter() {
      override def isGlobal(shader: com.badlogic.gdx.graphics.g3d.shaders.BaseShader, inputID: scala.Int): scala.Boolean = {
        return true
      }
      override def set(shader: com.badlogic.gdx.graphics.g3d.shaders.BaseShader, inputID: scala.Int, renderable: com.badlogic.gdx.graphics.g3d.Renderable, combinedAttributes: com.badlogic.gdx.graphics.g3d.Attributes): scala.Unit = {
        shader.set(inputID, ParticleShader.TMP_VECTOR3.set(-shader.camera.direction.x, -shader.camera.direction.y, -shader.camera.direction.z).nor())
      }
    }
    final val cameraPosition: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Setter = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Setter() {
      override def isGlobal(shader: com.badlogic.gdx.graphics.g3d.shaders.BaseShader, inputID: scala.Int): scala.Boolean = {
        return true
      }
      override def set(shader: com.badlogic.gdx.graphics.g3d.shaders.BaseShader, inputID: scala.Int, renderable: com.badlogic.gdx.graphics.g3d.Renderable, combinedAttributes: com.badlogic.gdx.graphics.g3d.Attributes): scala.Unit = {
        shader.set(inputID, shader.camera.position)
      }
    }
    final val screenWidth: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Setter = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Setter() {
      override def isGlobal(shader: com.badlogic.gdx.graphics.g3d.shaders.BaseShader, inputID: scala.Int): scala.Boolean = {
        return true
      }
      override def set(shader: com.badlogic.gdx.graphics.g3d.shaders.BaseShader, inputID: scala.Int, renderable: com.badlogic.gdx.graphics.g3d.Renderable, combinedAttributes: com.badlogic.gdx.graphics.g3d.Attributes): scala.Unit = {
        shader.set(inputID, com.badlogic.gdx.Gdx.graphics.getWidth().asInstanceOf[scala.Float])
      }
    }
    final val worldViewTrans: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Setter = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Setter() {
      final val temp: com.badlogic.gdx.math.Matrix4 = new com.badlogic.gdx.math.Matrix4()
      override def isGlobal(shader: com.badlogic.gdx.graphics.g3d.shaders.BaseShader, inputID: scala.Int): scala.Boolean = {
        return false
      }
      override def set(shader: com.badlogic.gdx.graphics.g3d.shaders.BaseShader, inputID: scala.Int, renderable: com.badlogic.gdx.graphics.g3d.Renderable, combinedAttributes: com.badlogic.gdx.graphics.g3d.Attributes): scala.Unit = {
        shader.set(inputID, temp.set(shader.camera.view).mul(renderable.worldTransform))
      }
    }
  }
}