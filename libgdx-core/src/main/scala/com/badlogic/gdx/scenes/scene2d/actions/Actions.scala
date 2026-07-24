package com.badlogic.gdx.scenes.scene2d.actions

object Actions {
  final val ACTION_POOLS: com.badlogic.gdx.utils.PoolManager = new com.badlogic.gdx.utils.PoolManager()
  def registerAction[T <: com.badlogic.gdx.scenes.scene2d.Action](poolClass: java.lang.Class[T], supplier: com.badlogic.gdx.utils.DefaultPool#PoolSupplier[T]): scala.Unit = {
    Actions.ACTION_POOLS.addPool(poolClass, supplier)
  }
  def action[T <: com.badlogic.gdx.scenes.scene2d.Action](`type`: java.lang.Class[T]): T = {
    val pool: com.badlogic.gdx.utils.Pool[T] = Actions.ACTION_POOLS.getPoolOrNull(`type`)
    if (pool == null) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException(("No action pool registered for type " + `type`) + ". Register it with Actions#registerAction.")
    } else ()
    val action: T = pool.obtain()
    action.setPool(pool)
    return action
  }
  def addAction(action: com.badlogic.gdx.scenes.scene2d.Action): com.badlogic.gdx.scenes.scene2d.actions.AddAction = {
    val addAction: com.badlogic.gdx.scenes.scene2d.actions.AddAction = Actions.action(classOf[java.lang.Class])
    addAction.setAction(action)
    return addAction
  }
  def addAction(action: com.badlogic.gdx.scenes.scene2d.Action, targetActor: com.badlogic.gdx.scenes.scene2d.Actor): com.badlogic.gdx.scenes.scene2d.actions.AddAction = {
    val addAction: com.badlogic.gdx.scenes.scene2d.actions.AddAction = Actions.action(classOf[java.lang.Class])
    addAction.setTarget(targetActor)
    addAction.setAction(action)
    return addAction
  }
  def removeAction(action: com.badlogic.gdx.scenes.scene2d.Action): com.badlogic.gdx.scenes.scene2d.actions.RemoveAction = {
    val removeAction: com.badlogic.gdx.scenes.scene2d.actions.RemoveAction = Actions.action(classOf[java.lang.Class])
    removeAction.setAction(action)
    return removeAction
  }
  def removeAction(action: com.badlogic.gdx.scenes.scene2d.Action, targetActor: com.badlogic.gdx.scenes.scene2d.Actor): com.badlogic.gdx.scenes.scene2d.actions.RemoveAction = {
    val removeAction: com.badlogic.gdx.scenes.scene2d.actions.RemoveAction = Actions.action(classOf[java.lang.Class])
    removeAction.setTarget(targetActor)
    removeAction.setAction(action)
    return removeAction
  }
  def moveTo(x: scala.Float, y: scala.Float): com.badlogic.gdx.scenes.scene2d.actions.MoveToAction = {
    return Actions.moveTo(x, y, 0, null)
  }
  def moveTo(x: scala.Float, y: scala.Float, duration: scala.Float): com.badlogic.gdx.scenes.scene2d.actions.MoveToAction = {
    return Actions.moveTo(x, y, duration, null)
  }
  def moveTo(x: scala.Float, y: scala.Float, duration: scala.Float, interpolation: com.badlogic.gdx.math.Interpolation): com.badlogic.gdx.scenes.scene2d.actions.MoveToAction = {
    val action: com.badlogic.gdx.scenes.scene2d.actions.MoveToAction = Actions.action(classOf[java.lang.Class])
    action.setPosition(x, y)
    action.setDuration(duration)
    action.setInterpolation(interpolation)
    return action
  }
  def moveToAligned(x: scala.Float, y: scala.Float, alignment: scala.Int): com.badlogic.gdx.scenes.scene2d.actions.MoveToAction = {
    return Actions.moveToAligned(x, y, alignment, 0, null)
  }
  def moveToAligned(x: scala.Float, y: scala.Float, alignment: scala.Int, duration: scala.Float): com.badlogic.gdx.scenes.scene2d.actions.MoveToAction = {
    return Actions.moveToAligned(x, y, alignment, duration, null)
  }
  def moveToAligned(x: scala.Float, y: scala.Float, alignment: scala.Int, duration: scala.Float, interpolation: com.badlogic.gdx.math.Interpolation): com.badlogic.gdx.scenes.scene2d.actions.MoveToAction = {
    val action: com.badlogic.gdx.scenes.scene2d.actions.MoveToAction = Actions.action(classOf[java.lang.Class])
    action.setPosition(x, y, alignment)
    action.setDuration(duration)
    action.setInterpolation(interpolation)
    return action
  }
  def moveBy(amountX: scala.Float, amountY: scala.Float): com.badlogic.gdx.scenes.scene2d.actions.MoveByAction = {
    return Actions.moveBy(amountX, amountY, 0, null)
  }
  def moveBy(amountX: scala.Float, amountY: scala.Float, duration: scala.Float): com.badlogic.gdx.scenes.scene2d.actions.MoveByAction = {
    return Actions.moveBy(amountX, amountY, duration, null)
  }
  def moveBy(amountX: scala.Float, amountY: scala.Float, duration: scala.Float, interpolation: com.badlogic.gdx.math.Interpolation): com.badlogic.gdx.scenes.scene2d.actions.MoveByAction = {
    val action: com.badlogic.gdx.scenes.scene2d.actions.MoveByAction = Actions.action(classOf[java.lang.Class])
    action.setAmount(amountX, amountY)
    action.setDuration(duration)
    action.setInterpolation(interpolation)
    return action
  }
  def sizeTo(x: scala.Float, y: scala.Float): com.badlogic.gdx.scenes.scene2d.actions.SizeToAction = {
    return Actions.sizeTo(x, y, 0, null)
  }
  def sizeTo(x: scala.Float, y: scala.Float, duration: scala.Float): com.badlogic.gdx.scenes.scene2d.actions.SizeToAction = {
    return Actions.sizeTo(x, y, duration, null)
  }
  def sizeTo(x: scala.Float, y: scala.Float, duration: scala.Float, interpolation: com.badlogic.gdx.math.Interpolation): com.badlogic.gdx.scenes.scene2d.actions.SizeToAction = {
    val action: com.badlogic.gdx.scenes.scene2d.actions.SizeToAction = Actions.action(classOf[java.lang.Class])
    action.setSize(x, y)
    action.setDuration(duration)
    action.setInterpolation(interpolation)
    return action
  }
  def sizeBy(amountX: scala.Float, amountY: scala.Float): com.badlogic.gdx.scenes.scene2d.actions.SizeByAction = {
    return Actions.sizeBy(amountX, amountY, 0, null)
  }
  def sizeBy(amountX: scala.Float, amountY: scala.Float, duration: scala.Float): com.badlogic.gdx.scenes.scene2d.actions.SizeByAction = {
    return Actions.sizeBy(amountX, amountY, duration, null)
  }
  def sizeBy(amountX: scala.Float, amountY: scala.Float, duration: scala.Float, interpolation: com.badlogic.gdx.math.Interpolation): com.badlogic.gdx.scenes.scene2d.actions.SizeByAction = {
    val action: com.badlogic.gdx.scenes.scene2d.actions.SizeByAction = Actions.action(classOf[java.lang.Class])
    action.setAmount(amountX, amountY)
    action.setDuration(duration)
    action.setInterpolation(interpolation)
    return action
  }
  def scaleTo(x: scala.Float, y: scala.Float): com.badlogic.gdx.scenes.scene2d.actions.ScaleToAction = {
    return Actions.scaleTo(x, y, 0, null)
  }
  def scaleTo(x: scala.Float, y: scala.Float, duration: scala.Float): com.badlogic.gdx.scenes.scene2d.actions.ScaleToAction = {
    return Actions.scaleTo(x, y, duration, null)
  }
  def scaleTo(x: scala.Float, y: scala.Float, duration: scala.Float, interpolation: com.badlogic.gdx.math.Interpolation): com.badlogic.gdx.scenes.scene2d.actions.ScaleToAction = {
    val action: com.badlogic.gdx.scenes.scene2d.actions.ScaleToAction = Actions.action(classOf[java.lang.Class])
    action.setScale(x, y)
    action.setDuration(duration)
    action.setInterpolation(interpolation)
    return action
  }
  def scaleBy(amountX: scala.Float, amountY: scala.Float): com.badlogic.gdx.scenes.scene2d.actions.ScaleByAction = {
    return Actions.scaleBy(amountX, amountY, 0, null)
  }
  def scaleBy(amountX: scala.Float, amountY: scala.Float, duration: scala.Float): com.badlogic.gdx.scenes.scene2d.actions.ScaleByAction = {
    return Actions.scaleBy(amountX, amountY, duration, null)
  }
  def scaleBy(amountX: scala.Float, amountY: scala.Float, duration: scala.Float, interpolation: com.badlogic.gdx.math.Interpolation): com.badlogic.gdx.scenes.scene2d.actions.ScaleByAction = {
    val action: com.badlogic.gdx.scenes.scene2d.actions.ScaleByAction = Actions.action(classOf[java.lang.Class])
    action.setAmount(amountX, amountY)
    action.setDuration(duration)
    action.setInterpolation(interpolation)
    return action
  }
  def rotateTo(rotation: scala.Float): com.badlogic.gdx.scenes.scene2d.actions.RotateToAction = {
    return Actions.rotateTo(rotation, 0, null)
  }
  def rotateTo(rotation: scala.Float, duration: scala.Float): com.badlogic.gdx.scenes.scene2d.actions.RotateToAction = {
    return Actions.rotateTo(rotation, duration, null)
  }
  def rotateTo(rotation: scala.Float, duration: scala.Float, interpolation: com.badlogic.gdx.math.Interpolation): com.badlogic.gdx.scenes.scene2d.actions.RotateToAction = {
    val action: com.badlogic.gdx.scenes.scene2d.actions.RotateToAction = Actions.action(classOf[java.lang.Class])
    action.setRotation(rotation)
    action.setDuration(duration)
    action.setInterpolation(interpolation)
    return action
  }
  def rotateBy(rotationAmount: scala.Float): com.badlogic.gdx.scenes.scene2d.actions.RotateByAction = {
    return Actions.rotateBy(rotationAmount, 0, null)
  }
  def rotateBy(rotationAmount: scala.Float, duration: scala.Float): com.badlogic.gdx.scenes.scene2d.actions.RotateByAction = {
    return Actions.rotateBy(rotationAmount, duration, null)
  }
  def rotateBy(rotationAmount: scala.Float, duration: scala.Float, interpolation: com.badlogic.gdx.math.Interpolation): com.badlogic.gdx.scenes.scene2d.actions.RotateByAction = {
    val action: com.badlogic.gdx.scenes.scene2d.actions.RotateByAction = Actions.action(classOf[java.lang.Class])
    action.setAmount(rotationAmount)
    action.setDuration(duration)
    action.setInterpolation(interpolation)
    return action
  }
  def color(color: com.badlogic.gdx.graphics.Color): com.badlogic.gdx.scenes.scene2d.actions.ColorAction = {
    return Actions.color(color, 0, null)
  }
  def color(color: com.badlogic.gdx.graphics.Color, duration: scala.Float): com.badlogic.gdx.scenes.scene2d.actions.ColorAction = {
    return Actions.color(color, duration, null)
  }
  def color(color: com.badlogic.gdx.graphics.Color, duration: scala.Float, interpolation: com.badlogic.gdx.math.Interpolation): com.badlogic.gdx.scenes.scene2d.actions.ColorAction = {
    val action: com.badlogic.gdx.scenes.scene2d.actions.ColorAction = Actions.action(classOf[java.lang.Class])
    action.setEndColor(color)
    action.setDuration(duration)
    action.setInterpolation(interpolation)
    return action
  }
  def alpha(a: scala.Float): com.badlogic.gdx.scenes.scene2d.actions.AlphaAction = {
    return Actions.alpha(a, 0, null)
  }
  def alpha(a: scala.Float, duration: scala.Float): com.badlogic.gdx.scenes.scene2d.actions.AlphaAction = {
    return Actions.alpha(a, duration, null)
  }
  def alpha(a: scala.Float, duration: scala.Float, interpolation: com.badlogic.gdx.math.Interpolation): com.badlogic.gdx.scenes.scene2d.actions.AlphaAction = {
    val action: com.badlogic.gdx.scenes.scene2d.actions.AlphaAction = Actions.action(classOf[java.lang.Class])
    action.setAlpha(a)
    action.setDuration(duration)
    action.setInterpolation(interpolation)
    return action
  }
  def fadeOut(duration: scala.Float): com.badlogic.gdx.scenes.scene2d.actions.AlphaAction = {
    return Actions.alpha(0, duration, null)
  }
  def fadeOut(duration: scala.Float, interpolation: com.badlogic.gdx.math.Interpolation): com.badlogic.gdx.scenes.scene2d.actions.AlphaAction = {
    val action: com.badlogic.gdx.scenes.scene2d.actions.AlphaAction = Actions.action(classOf[java.lang.Class])
    action.setAlpha(0)
    action.setDuration(duration)
    action.setInterpolation(interpolation)
    return action
  }
  def fadeIn(duration: scala.Float): com.badlogic.gdx.scenes.scene2d.actions.AlphaAction = {
    return Actions.alpha(1, duration, null)
  }
  def fadeIn(duration: scala.Float, interpolation: com.badlogic.gdx.math.Interpolation): com.badlogic.gdx.scenes.scene2d.actions.AlphaAction = {
    val action: com.badlogic.gdx.scenes.scene2d.actions.AlphaAction = Actions.action(classOf[java.lang.Class])
    action.setAlpha(1)
    action.setDuration(duration)
    action.setInterpolation(interpolation)
    return action
  }
  def show(): com.badlogic.gdx.scenes.scene2d.actions.VisibleAction = {
    return Actions.visible(true)
  }
  def hide(): com.badlogic.gdx.scenes.scene2d.actions.VisibleAction = {
    return Actions.visible(false)
  }
  def visible(visible: scala.Boolean): com.badlogic.gdx.scenes.scene2d.actions.VisibleAction = {
    val action: com.badlogic.gdx.scenes.scene2d.actions.VisibleAction = Actions.action(classOf[java.lang.Class])
    action.setVisible(visible)
    return action
  }
  def touchable(touchable: com.badlogic.gdx.scenes.scene2d.Touchable): com.badlogic.gdx.scenes.scene2d.actions.TouchableAction = {
    val action: com.badlogic.gdx.scenes.scene2d.actions.TouchableAction = Actions.action(classOf[java.lang.Class])
    action.setTouchable(touchable)
    return action
  }
  def removeActor(): com.badlogic.gdx.scenes.scene2d.actions.RemoveActorAction = {
    return Actions.action(classOf[java.lang.Class])
  }
  def removeActor(removeActor: com.badlogic.gdx.scenes.scene2d.Actor): com.badlogic.gdx.scenes.scene2d.actions.RemoveActorAction = {
    val action: com.badlogic.gdx.scenes.scene2d.actions.RemoveActorAction = Actions.action(classOf[java.lang.Class])
    action.setTarget(removeActor)
    return action
  }
  def delay(duration: scala.Float): com.badlogic.gdx.scenes.scene2d.actions.DelayAction = {
    val action: com.badlogic.gdx.scenes.scene2d.actions.DelayAction = Actions.action(classOf[java.lang.Class])
    action.setDuration(duration)
    return action
  }
  def delay(duration: scala.Float, delayedAction: com.badlogic.gdx.scenes.scene2d.Action): com.badlogic.gdx.scenes.scene2d.actions.DelayAction = {
    val action: com.badlogic.gdx.scenes.scene2d.actions.DelayAction = Actions.action(classOf[java.lang.Class])
    action.setDuration(duration)
    action.setAction(delayedAction)
    return action
  }
  def timeScale(scale: scala.Float, scaledAction: com.badlogic.gdx.scenes.scene2d.Action): com.badlogic.gdx.scenes.scene2d.actions.TimeScaleAction = {
    val action: com.badlogic.gdx.scenes.scene2d.actions.TimeScaleAction = Actions.action(classOf[java.lang.Class])
    action.setScale(scale)
    action.setAction(scaledAction)
    return action
  }
  def sequence(action1: com.badlogic.gdx.scenes.scene2d.Action): com.badlogic.gdx.scenes.scene2d.actions.SequenceAction = {
    val action: com.badlogic.gdx.scenes.scene2d.actions.SequenceAction = Actions.action(classOf[java.lang.Class])
    action.addAction(action1)
    return action
  }
  def sequence(action1: com.badlogic.gdx.scenes.scene2d.Action, action2: com.badlogic.gdx.scenes.scene2d.Action): com.badlogic.gdx.scenes.scene2d.actions.SequenceAction = {
    val action: com.badlogic.gdx.scenes.scene2d.actions.SequenceAction = Actions.action(classOf[java.lang.Class])
    action.addAction(action1)
    action.addAction(action2)
    return action
  }
  def sequence(action1: com.badlogic.gdx.scenes.scene2d.Action, action2: com.badlogic.gdx.scenes.scene2d.Action, action3: com.badlogic.gdx.scenes.scene2d.Action): com.badlogic.gdx.scenes.scene2d.actions.SequenceAction = {
    val action: com.badlogic.gdx.scenes.scene2d.actions.SequenceAction = Actions.action(classOf[java.lang.Class])
    action.addAction(action1)
    action.addAction(action2)
    action.addAction(action3)
    return action
  }
  def sequence(action1: com.badlogic.gdx.scenes.scene2d.Action, action2: com.badlogic.gdx.scenes.scene2d.Action, action3: com.badlogic.gdx.scenes.scene2d.Action, action4: com.badlogic.gdx.scenes.scene2d.Action): com.badlogic.gdx.scenes.scene2d.actions.SequenceAction = {
    val action: com.badlogic.gdx.scenes.scene2d.actions.SequenceAction = Actions.action(classOf[java.lang.Class])
    action.addAction(action1)
    action.addAction(action2)
    action.addAction(action3)
    action.addAction(action4)
    return action
  }
  def sequence(action1: com.badlogic.gdx.scenes.scene2d.Action, action2: com.badlogic.gdx.scenes.scene2d.Action, action3: com.badlogic.gdx.scenes.scene2d.Action, action4: com.badlogic.gdx.scenes.scene2d.Action, action5: com.badlogic.gdx.scenes.scene2d.Action): com.badlogic.gdx.scenes.scene2d.actions.SequenceAction = {
    val action: com.badlogic.gdx.scenes.scene2d.actions.SequenceAction = Actions.action(classOf[java.lang.Class])
    action.addAction(action1)
    action.addAction(action2)
    action.addAction(action3)
    action.addAction(action4)
    action.addAction(action5)
    return action
  }
  def sequence(actions: scala.Array[com.badlogic.gdx.scenes.scene2d.Action]): com.badlogic.gdx.scenes.scene2d.actions.SequenceAction = {
    val action: com.badlogic.gdx.scenes.scene2d.actions.SequenceAction = Actions.action(classOf[java.lang.Class])
    { var i: scala.Int = 0; val n: scala.Int = actions.length; while (i < n) { {
      action.addAction(actions(i))
    }; i = i + 1 } }
    return action
  }
  def sequence(): com.badlogic.gdx.scenes.scene2d.actions.SequenceAction = {
    return Actions.action(classOf[java.lang.Class])
  }
  def parallel(action1: com.badlogic.gdx.scenes.scene2d.Action): com.badlogic.gdx.scenes.scene2d.actions.ParallelAction = {
    val action: com.badlogic.gdx.scenes.scene2d.actions.ParallelAction = Actions.action(classOf[java.lang.Class])
    action.addAction(action1)
    return action
  }
  def parallel(action1: com.badlogic.gdx.scenes.scene2d.Action, action2: com.badlogic.gdx.scenes.scene2d.Action): com.badlogic.gdx.scenes.scene2d.actions.ParallelAction = {
    val action: com.badlogic.gdx.scenes.scene2d.actions.ParallelAction = Actions.action(classOf[java.lang.Class])
    action.addAction(action1)
    action.addAction(action2)
    return action
  }
  def parallel(action1: com.badlogic.gdx.scenes.scene2d.Action, action2: com.badlogic.gdx.scenes.scene2d.Action, action3: com.badlogic.gdx.scenes.scene2d.Action): com.badlogic.gdx.scenes.scene2d.actions.ParallelAction = {
    val action: com.badlogic.gdx.scenes.scene2d.actions.ParallelAction = Actions.action(classOf[java.lang.Class])
    action.addAction(action1)
    action.addAction(action2)
    action.addAction(action3)
    return action
  }
  def parallel(action1: com.badlogic.gdx.scenes.scene2d.Action, action2: com.badlogic.gdx.scenes.scene2d.Action, action3: com.badlogic.gdx.scenes.scene2d.Action, action4: com.badlogic.gdx.scenes.scene2d.Action): com.badlogic.gdx.scenes.scene2d.actions.ParallelAction = {
    val action: com.badlogic.gdx.scenes.scene2d.actions.ParallelAction = Actions.action(classOf[java.lang.Class])
    action.addAction(action1)
    action.addAction(action2)
    action.addAction(action3)
    action.addAction(action4)
    return action
  }
  def parallel(action1: com.badlogic.gdx.scenes.scene2d.Action, action2: com.badlogic.gdx.scenes.scene2d.Action, action3: com.badlogic.gdx.scenes.scene2d.Action, action4: com.badlogic.gdx.scenes.scene2d.Action, action5: com.badlogic.gdx.scenes.scene2d.Action): com.badlogic.gdx.scenes.scene2d.actions.ParallelAction = {
    val action: com.badlogic.gdx.scenes.scene2d.actions.ParallelAction = Actions.action(classOf[java.lang.Class])
    action.addAction(action1)
    action.addAction(action2)
    action.addAction(action3)
    action.addAction(action4)
    action.addAction(action5)
    return action
  }
  def parallel(actions: scala.Array[com.badlogic.gdx.scenes.scene2d.Action]): com.badlogic.gdx.scenes.scene2d.actions.ParallelAction = {
    val action: com.badlogic.gdx.scenes.scene2d.actions.ParallelAction = Actions.action(classOf[java.lang.Class])
    { var i: scala.Int = 0; val n: scala.Int = actions.length; while (i < n) { {
      action.addAction(actions(i))
    }; i = i + 1 } }
    return action
  }
  def parallel(): com.badlogic.gdx.scenes.scene2d.actions.ParallelAction = {
    return Actions.action(classOf[java.lang.Class])
  }
  def repeat(count: scala.Int, repeatedAction: com.badlogic.gdx.scenes.scene2d.Action): com.badlogic.gdx.scenes.scene2d.actions.RepeatAction = {
    val action: com.badlogic.gdx.scenes.scene2d.actions.RepeatAction = Actions.action(classOf[java.lang.Class])
    action.setCount(count)
    action.setAction(repeatedAction)
    return action
  }
  def forever(repeatedAction: com.badlogic.gdx.scenes.scene2d.Action): com.badlogic.gdx.scenes.scene2d.actions.RepeatAction = {
    val action: com.badlogic.gdx.scenes.scene2d.actions.RepeatAction = Actions.action(classOf[java.lang.Class])
    action.setCount(com.badlogic.gdx.scenes.scene2d.actions.RepeatAction.FOREVER)
    action.setAction(repeatedAction)
    return action
  }
  def run(runnable: java.lang.Runnable): com.badlogic.gdx.scenes.scene2d.actions.RunnableAction = {
    val action: com.badlogic.gdx.scenes.scene2d.actions.RunnableAction = Actions.action(classOf[java.lang.Class])
    action.setRunnable(runnable)
    return action
  }
  def layout(enabled: scala.Boolean): com.badlogic.gdx.scenes.scene2d.actions.LayoutAction = {
    val action: com.badlogic.gdx.scenes.scene2d.actions.LayoutAction = Actions.action(classOf[java.lang.Class])
    action.setLayoutEnabled(enabled)
    return action
  }
  def after(action: com.badlogic.gdx.scenes.scene2d.Action): com.badlogic.gdx.scenes.scene2d.actions.AfterAction = {
    val afterAction: com.badlogic.gdx.scenes.scene2d.actions.AfterAction = Actions.action(classOf[java.lang.Class])
    afterAction.setAction(action)
    return afterAction
  }
  def addListener(listener: com.badlogic.gdx.scenes.scene2d.EventListener, capture: scala.Boolean): com.badlogic.gdx.scenes.scene2d.actions.AddListenerAction = {
    val addAction: com.badlogic.gdx.scenes.scene2d.actions.AddListenerAction = Actions.action(classOf[java.lang.Class])
    addAction.setListener(listener)
    addAction.setCapture(capture)
    return addAction
  }
  def addListener(listener: com.badlogic.gdx.scenes.scene2d.EventListener, capture: scala.Boolean, targetActor: com.badlogic.gdx.scenes.scene2d.Actor): com.badlogic.gdx.scenes.scene2d.actions.AddListenerAction = {
    val addAction: com.badlogic.gdx.scenes.scene2d.actions.AddListenerAction = Actions.action(classOf[java.lang.Class])
    addAction.setTarget(targetActor)
    addAction.setListener(listener)
    addAction.setCapture(capture)
    return addAction
  }
  def removeListener(listener: com.badlogic.gdx.scenes.scene2d.EventListener, capture: scala.Boolean): com.badlogic.gdx.scenes.scene2d.actions.RemoveListenerAction = {
    val addAction: com.badlogic.gdx.scenes.scene2d.actions.RemoveListenerAction = Actions.action(classOf[java.lang.Class])
    addAction.setListener(listener)
    addAction.setCapture(capture)
    return addAction
  }
  def removeListener(listener: com.badlogic.gdx.scenes.scene2d.EventListener, capture: scala.Boolean, targetActor: com.badlogic.gdx.scenes.scene2d.Actor): com.badlogic.gdx.scenes.scene2d.actions.RemoveListenerAction = {
    val addAction: com.badlogic.gdx.scenes.scene2d.actions.RemoveListenerAction = Actions.action(classOf[java.lang.Class])
    addAction.setTarget(targetActor)
    addAction.setListener(listener)
    addAction.setCapture(capture)
    return addAction
  }
  def targeting(target: com.badlogic.gdx.scenes.scene2d.Actor, action: com.badlogic.gdx.scenes.scene2d.Action): com.badlogic.gdx.scenes.scene2d.Action = {
    action.setTarget(target)
    return action
  }
}