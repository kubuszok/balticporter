package com.badlogic.gdx.scenes.scene2d.actions

class RunnableAction extends com.badlogic.gdx.scenes.scene2d.Action {
  private var runnable: java.lang.Runnable = null.asInstanceOf[java.lang.Runnable]
  private var ran: scala.Boolean = false
  override def act(delta: scala.Float): scala.Boolean = {
    if (!this.ran) {
      this.ran = true
      this.run()
    } else ()
    return true
  }
  def run(): scala.Unit = {
    val pool: com.badlogic.gdx.utils.Pool[?] = this.getPool().asInstanceOf[com.badlogic.gdx.utils.Pool[?]]
    this.setPool(null)
    try {
      this.runnable.run()
    } finally {
      this.setPool(pool.asInstanceOf[com.badlogic.gdx.utils.Pool[?]])
    }
  }
  override def restart(): scala.Unit = {
    this.ran = false
  }
  override def reset(): scala.Unit = {
    super.reset()
    this.runnable = null
  }
  def getRunnable(): java.lang.Runnable = {
    return this.runnable
  }
  def setRunnable(runnable: java.lang.Runnable): scala.Unit = {
    this.runnable = runnable
  }
}