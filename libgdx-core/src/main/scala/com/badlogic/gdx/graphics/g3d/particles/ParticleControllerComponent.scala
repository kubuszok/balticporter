package com.badlogic.gdx.graphics.g3d.particles

abstract class ParticleControllerComponent extends com.badlogic.gdx.utils.Disposable with com.badlogic.gdx.utils.Json#Serializable with com.badlogic.gdx.graphics.g3d.particles.ResourceData#Configurable {
  protected var controller: com.badlogic.gdx.graphics.g3d.particles.ParticleController = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.ParticleController]
  def activateParticles(startIndex: scala.Int, count: scala.Int): scala.Unit = {
    ()
  }
  def killParticles(startIndex: scala.Int, count: scala.Int): scala.Unit = {
    ()
  }
  def update(): scala.Unit = {
    ()
  }
  def init(): scala.Unit = {
    ()
  }
  def start(): scala.Unit = {
    ()
  }
  def `end`(): scala.Unit = {
    ()
  }
  def dispose(): scala.Unit = {
    ()
  }
  def copy(): ParticleControllerComponent
  def allocateChannels(): scala.Unit = {
    ()
  }
  def set(particleController: com.badlogic.gdx.graphics.g3d.particles.ParticleController): scala.Unit = {
    this.controller = particleController
  }
  def save(manager: com.badlogic.gdx.assets.AssetManager, data: com.badlogic.gdx.graphics.g3d.particles.ResourceData): scala.Unit = {
    ()
  }
  def load(manager: com.badlogic.gdx.assets.AssetManager, data: com.badlogic.gdx.graphics.g3d.particles.ResourceData): scala.Unit = {
    ()
  }
  def write(json: com.badlogic.gdx.utils.Json): scala.Unit = {
    ()
  }
  def read(json: com.badlogic.gdx.utils.Json, jsonData: com.badlogic.gdx.utils.JsonValue): scala.Unit = {
    ()
  }
}
object ParticleControllerComponent {
  protected final val TMP_V1: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  protected final val TMP_V2: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  protected final val TMP_V3: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  protected final val TMP_V4: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  protected final val TMP_V5: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  protected final val TMP_V6: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  protected final val TMP_Q: com.badlogic.gdx.math.Quaternion = new com.badlogic.gdx.math.Quaternion()
  protected final val TMP_Q2: com.badlogic.gdx.math.Quaternion = new com.badlogic.gdx.math.Quaternion()
  protected final val TMP_M3: com.badlogic.gdx.math.Matrix3 = new com.badlogic.gdx.math.Matrix3()
  protected final val TMP_M4: com.badlogic.gdx.math.Matrix4 = new com.badlogic.gdx.math.Matrix4()
}