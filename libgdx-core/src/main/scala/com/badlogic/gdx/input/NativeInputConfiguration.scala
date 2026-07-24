package com.badlogic.gdx.input

class NativeInputConfiguration {
  private var `type`: com.badlogic.gdx.Input.OnscreenKeyboardType = com.badlogic.gdx.Input.OnscreenKeyboardType.Default
  private var preventCorrection: scala.Boolean = false
  private var textInputWrapper: com.badlogic.gdx.input.TextInputWrapper = null.asInstanceOf[com.badlogic.gdx.input.TextInputWrapper]
  var isMultiLine$field: scala.Boolean = false
  private var maxLength: scala.Int = -1
  private var validator: com.badlogic.gdx.Input.InputStringValidator = null.asInstanceOf[com.badlogic.gdx.Input.InputStringValidator]
  private var placeholder: java.lang.String = ""
  private var maskInput: scala.Boolean = false
  private var showUnmaskButton: scala.Boolean = false
  private var autoComplete: scala.Array[java.lang.String] = null
  private var closeCallback: com.badlogic.gdx.input.NativeInputConfiguration.NativeInputCloseCallback = (confirm: scala.Boolean) => false
  def getType(): com.badlogic.gdx.Input.OnscreenKeyboardType = {
    return this.`type`
  }
  def setType(`type`: com.badlogic.gdx.Input.OnscreenKeyboardType): NativeInputConfiguration = {
    this.`type` = `type`
    return this
  }
  def isPreventCorrection(): scala.Boolean = {
    return this.preventCorrection
  }
  def setPreventCorrection(preventCorrection: scala.Boolean): NativeInputConfiguration = {
    this.preventCorrection = preventCorrection
    return this
  }
  def getTextInputWrapper(): com.badlogic.gdx.input.TextInputWrapper = {
    return this.textInputWrapper
  }
  def setTextInputWrapper(textInputWrapper: com.badlogic.gdx.input.TextInputWrapper): NativeInputConfiguration = {
    this.textInputWrapper = textInputWrapper
    return this
  }
  def isMultiLine(): scala.Boolean = {
    return this.isMultiLine$field
  }
  def setMultiLine(multiLine: scala.Boolean): NativeInputConfiguration = {
    this.isMultiLine$field = multiLine
    return this
  }
  def getMaxLength(): scala.Int = {
    return this.maxLength
  }
  def setMaxLength(maxLength: scala.Int): NativeInputConfiguration = {
    this.maxLength = maxLength
    return this
  }
  def getValidator(): com.badlogic.gdx.Input.InputStringValidator = {
    return this.validator
  }
  def setValidator(validator: com.badlogic.gdx.Input.InputStringValidator): NativeInputConfiguration = {
    this.validator = validator
    return this
  }
  def getPlaceholder(): java.lang.String = {
    return this.placeholder
  }
  def setPlaceholder(placeholder: java.lang.String): NativeInputConfiguration = {
    this.placeholder = placeholder
    return this
  }
  def setMaskInput(maskInput: scala.Boolean): NativeInputConfiguration = {
    this.maskInput = maskInput
    return this
  }
  def isMaskInput(): scala.Boolean = {
    return this.maskInput
  }
  def isShowUnmaskButton(): scala.Boolean = {
    return this.showUnmaskButton
  }
  def setShowUnmaskButton(showUnmaskButton: scala.Boolean): NativeInputConfiguration = {
    this.showUnmaskButton = showUnmaskButton
    return this
  }
  def getAutoComplete(): scala.Array[java.lang.String] = {
    return this.autoComplete
  }
  def setAutoComplete(autoComplete: scala.Array[java.lang.String]): NativeInputConfiguration = {
    this.autoComplete = autoComplete
    return this
  }
  def getCloseCallback(): com.badlogic.gdx.input.NativeInputConfiguration.NativeInputCloseCallback = {
    return this.closeCallback
  }
  def setCloseCallback(closeCallback: com.badlogic.gdx.input.NativeInputConfiguration.NativeInputCloseCallback): NativeInputConfiguration = {
    this.closeCallback = closeCallback
    return this
  }
  def validate(): scala.Unit = {
    var message: java.lang.String = null
    if (this.`type` == null) {
      message = "OnscreenKeyboardType needs to be non null"
    } else ()
    if (this.textInputWrapper == null) {
      message = "TextInputWrapper needs to be non null"
    } else ()
    if (this.showUnmaskButton && (!this.maskInput)) {
      message = "ShowUnmaskButton only works with MaskInput"
    } else ()
    if (this.placeholder == null) {
      message = "Placeholder needs to be non null"
    } else ()
    if ((this.autoComplete != null) && (this.`type` != com.badlogic.gdx.Input.OnscreenKeyboardType.Default)) {
      message = "AutoComplete should only be used with OnscreenKeyboardType.Default"
    } else ()
    if ((this.autoComplete != null) && this.isMultiLine$field) {
      message = "AutoComplete shouldn't be used with multiline"
    } else ()
    if (this.closeCallback == null) {
      message = "CloseCallback needs to be non null"
    } else ()
    if (message != null) {
      throw new java.lang.IllegalArgumentException("NativeInputConfiguration validation failed: " + message)
    } else ()
  }
}
object NativeInputConfiguration {
  trait NativeInputCloseCallback {
    def onClose(confirmativeAction: scala.Boolean): scala.Boolean
  }
}