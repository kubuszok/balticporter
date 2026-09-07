/*
 * Scala port copyright 2025-2026 Mateusz Kubuszok
 *
 * Migration notes:
 *   Source: backends/gdx-backend-headless/.../mock/input/MockInput.java
 *   Renames: MockInput -> NoopInput
 *   Convention: setInputProcessor stores value (Java ignores); inputProcessor initializes eagerly (Java lazy null check);
 *     setCursorCatched/cursorCatched track state (Java ignores); maxPointers returns 1 (Java returns 0)
 *   Idiom: split packages
 *   Audited: 2026-03-03
 *
 * Covenant: full-port
 * Covenant-baseline-spec-pass: 0
 * Covenant-baseline-loc: 151
 * Covenant-baseline-methods: NoopInput,_cursorCatched,_inputProcessor,accelerometerX,accelerometerY,accelerometerZ,azimuth,closeTextInputField,currentEventTime,cursorCatched,deltaX,deltaY,getRotationMatrix,getTextInput,gyroscopeX,gyroscopeY,gyroscopeZ,inputProcessor,isButtonJustPressed,isButtonPressed,isCatchKey,isKeyJustPressed,isKeyPressed,isPeripheralAvailable,isTouched,justTouched,maxPointers,nativeOrientation,openTextInputField,pitch,pressure,roll,rotation,setCatchKey,setCursorCatched,setCursorPosition,setInputProcessor,setKeyboardHeightObserver,setOnscreenKeyboardVisible,touched,vibrate,x,y
 * Covenant-source-reference: backends/gdx-backend-headless/src/com/badlogic/gdx/backends/headless/mock/input/MockInput.java
 * Covenant-verified: 2026-04-19
 *
 * upstream-commit: 892dee6814526372c81d9d72276e0d9df86da0c5
 */
package sge
package noop

import sge.Input.*

/** A no-op [[sge.Input]] implementation for headless/testing use. All input queries return zero/false defaults.
  */
final class NoopInput extends Input {

  private var _inputProcessor: InputProcessor = new InputAdapter()
  private var _cursorCatched:  Boolean        = false

  // ---- accelerometer / gyroscope ----

  override def accelerometerX: Float = 0.0f

  override def accelerometerY: Float = 0.0f

  override def accelerometerZ: Float = 0.0f

  override def gyroscopeX: Float = 0.0f

  override def gyroscopeY: Float = 0.0f

  override def gyroscopeZ: Float = 0.0f

  override def maxPointers: Int = 1

  // ---- pointer position ----

  override def x: Pixels = Pixels.zero

  override def getX(pointer: Int): Pixels = Pixels.zero

  override def deltaX: Pixels = Pixels.zero

  override def getDeltaX(pointer: Int): Pixels = Pixels.zero

  override def y: Pixels = Pixels.zero

  override def getY(pointer: Int): Pixels = Pixels.zero

  override def deltaY: Pixels = Pixels.zero

  override def getDeltaY(pointer: Int): Pixels = Pixels.zero

  // ---- touch / button / key ----

  override def touched: Boolean = false

  override def justTouched(): Boolean = false

  override def isTouched(pointer: Int): Boolean = false

  override def pressure: Float = 0.0f

  override def getPressure(pointer: Int): Float = 0.0f

  override def isButtonPressed(button: Int): Boolean = false

  override def isButtonJustPressed(button: Int): Boolean = false

  override def isKeyPressed(key: Int): Boolean = false

  override def isKeyJustPressed(key: Int): Boolean = false

  // ---- text input / keyboard ----

  override def getTextInput(listener: TextInputListener, title: String, text: String, hint: String): Unit = {}

  override def getTextInput(
    listener: TextInputListener,
    title:    String,
    text:     String,
    hint:     String,
    `type`:   OnscreenKeyboardType
  ): Unit = {}

  override def setOnscreenKeyboardVisible(visible: Boolean): Unit = {}

  override def setOnscreenKeyboardVisible(visible: Boolean, `type`: OnscreenKeyboardType): Unit = {}

  override def openTextInputField(configuration: input.NativeInputConfiguration): Unit = {}

  override def closeTextInputField(sendReturn: Boolean): Unit = {}

  override def setKeyboardHeightObserver(observer: KeyboardHeightObserver): Unit = {}

  // ---- vibration ----

  override def vibrate(milliseconds: Int): Unit = {}

  override def vibrate(milliseconds: Int, fallback: Boolean): Unit = {}

  override def vibrate(milliseconds: Int, amplitude: Int, fallback: Boolean): Unit = {}

  override def vibrate(vibrationType: VibrationType): Unit = {}

  // ---- orientation / rotation ----

  override def azimuth: Float = 0.0f

  override def pitch: Float = 0.0f

  override def roll: Float = 0.0f

  override def getRotationMatrix(matrix: Array[Float]): Unit = {}

  override def currentEventTime: Long = 0L

  // ---- catch keys ----

  override def setCatchKey(keycode: Int, catchKey: Boolean): Unit = {}

  override def isCatchKey(keycode: Int): Boolean = false

  // ---- input processor ----

  override def inputProcessor_=(processor: InputProcessor): Unit =
    _inputProcessor = processor

  override def inputProcessor: InputProcessor = _inputProcessor

  // ---- peripherals ----

  override def isPeripheralAvailable(peripheral: Peripheral): Boolean = false

  override def rotation: Int = 0

  override def nativeOrientation: Orientation = Orientation.Landscape

  // ---- cursor ----

  override def setCursorCatched(catched: Boolean): Unit =
    _cursorCatched = catched

  override def cursorCatched: Boolean = _cursorCatched

  override def setCursorPosition(x: Int, y: Int): Unit = {}
}
