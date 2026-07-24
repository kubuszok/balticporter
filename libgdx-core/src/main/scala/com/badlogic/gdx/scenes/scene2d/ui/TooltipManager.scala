package com.badlogic.gdx.scenes.scene2d.ui

class TooltipManager {
  var initialTime: scala.Float = 2
  var subsequentTime: scala.Float = 0
  var resetTime: scala.Float = 1.5f
  var enabled: scala.Boolean = true
  var animations: scala.Boolean = true
  var maxWidth: scala.Float = java.lang.Integer.MAX_VALUE
  var offsetX: scala.Float = 15
  var offsetY: scala.Float = 19
  var edgeDistance: scala.Float = 7
  final val shown: com.badlogic.gdx.utils.Array[com.badlogic.gdx.scenes.scene2d.ui.Tooltip[?]] = new com.badlogic.gdx.utils.Array()
  var time: scala.Float = this.initialTime
  final val resetTask: com.badlogic.gdx.utils.Timer.Task = new com.badlogic.gdx.utils.Timer.Task()
  var showTooltip: com.badlogic.gdx.scenes.scene2d.ui.Tooltip[?] = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Tooltip[?]]
  final val showTask: com.badlogic.gdx.utils.Timer.Task = new com.badlogic.gdx.utils.Timer.Task()
  def touchDown(tooltip: com.badlogic.gdx.scenes.scene2d.ui.Tooltip[?]): scala.Unit = {
    this.showTask.cancel()
    if (tooltip.container.remove()) {
      this.resetTask.cancel()
    } else ()
    this.resetTask.run()
    if (this.enabled || tooltip.always) {
      this.showTooltip = tooltip
      com.badlogic.gdx.utils.Timer.schedule(this.showTask, this.time)
    } else ()
  }
  def enter(tooltip: com.badlogic.gdx.scenes.scene2d.ui.Tooltip[?]): scala.Unit = {
    this.showTooltip = tooltip
    this.showTask.cancel()
    if (this.enabled || tooltip.always) {
      if ((this.time == 0) || tooltip.instant) {
        this.showTask.run()
      } else {
        com.badlogic.gdx.utils.Timer.schedule(this.showTask, this.time)
      }
    } else ()
  }
  def hide(tooltip: com.badlogic.gdx.scenes.scene2d.ui.Tooltip[?]): scala.Unit = {
    this.showTooltip = null
    this.showTask.cancel()
    if (tooltip.container.hasParent()) {
      this.shown.removeValue(tooltip, true)
      this.hideAction(tooltip)
      this.resetTask.cancel()
      com.badlogic.gdx.utils.Timer.schedule(this.resetTask, this.resetTime)
    } else ()
  }
  def showAction(tooltip: com.badlogic.gdx.scenes.scene2d.ui.Tooltip[?]): scala.Unit = {
    val actionTime: scala.Float = if (this.animations) if (this.time > 0) 0.5f else 0.15f else 0.1f
    tooltip.container.setTransform(true)
    tooltip.container.getColor().a = 0.2f
    tooltip.container.setScale(0.05f)
    tooltip.container.addAction(com.badlogic.gdx.scenes.scene2d.actions.Actions.parallel(com.badlogic.gdx.scenes.scene2d.actions.Actions.fadeIn(actionTime, com.badlogic.gdx.math.Interpolation.fade), com.badlogic.gdx.scenes.scene2d.actions.Actions.scaleTo(1, 1, actionTime, com.badlogic.gdx.math.Interpolation.fade)))
  }
  def hideAction(tooltip: com.badlogic.gdx.scenes.scene2d.ui.Tooltip[?]): scala.Unit = {
    tooltip.container.addAction(com.badlogic.gdx.scenes.scene2d.actions.Actions.sequence(com.badlogic.gdx.scenes.scene2d.actions.Actions.parallel(com.badlogic.gdx.scenes.scene2d.actions.Actions.alpha(0.2f, 0.2f, com.badlogic.gdx.math.Interpolation.fade), com.badlogic.gdx.scenes.scene2d.actions.Actions.scaleTo(0.05f, 0.05f, 0.2f, com.badlogic.gdx.math.Interpolation.fade)), com.badlogic.gdx.scenes.scene2d.actions.Actions.removeActor()))
  }
  def hideAll(): scala.Unit = {
    this.resetTask.cancel()
    this.showTask.cancel()
    this.time = this.initialTime
    this.showTooltip = null
    for (tooltip <- this.shown) {
      tooltip.hide()
    }
    this.shown.clear()
  }
  def instant(): scala.Unit = {
    this.time = 0
    this.showTask.run()
    this.showTask.cancel()
  }
}
object TooltipManager {
  private var instance: TooltipManager = null.asInstanceOf[TooltipManager]
  private var files: com.badlogic.gdx.Files = null.asInstanceOf[com.badlogic.gdx.Files]
  def getInstance(): TooltipManager = {
    if ((TooltipManager.files == null) || (TooltipManager.files != com.badlogic.gdx.Gdx.files)) {
      TooltipManager.files = com.badlogic.gdx.Gdx.files
      TooltipManager.instance = new TooltipManager()
    } else ()
    return TooltipManager.instance
  }
}