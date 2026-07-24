package com.badlogic.gdx

trait Input {
  def getAccelerometerX(): scala.Float
  def getAccelerometerY(): scala.Float
  def getAccelerometerZ(): scala.Float
  def getGyroscopeX(): scala.Float
  def getGyroscopeY(): scala.Float
  def getGyroscopeZ(): scala.Float
  def getMaxPointers(): scala.Int
  def getX(): scala.Int
  def getX(pointer: scala.Int): scala.Int
  def getDeltaX(): scala.Int
  def getDeltaX(pointer: scala.Int): scala.Int
  def getY(): scala.Int
  def getY(pointer: scala.Int): scala.Int
  def getDeltaY(): scala.Int
  def getDeltaY(pointer: scala.Int): scala.Int
  def isTouched(): scala.Boolean
  def justTouched(): scala.Boolean
  def isTouched(pointer: scala.Int): scala.Boolean
  def getPressure(): scala.Float
  def getPressure(pointer: scala.Int): scala.Float
  def isButtonPressed(button: scala.Int): scala.Boolean
  def isButtonJustPressed(button: scala.Int): scala.Boolean
  def isKeyPressed(key: scala.Int): scala.Boolean
  def isKeyJustPressed(key: scala.Int): scala.Boolean
  def getTextInput(listener: TextInputListener, title: java.lang.String, text: java.lang.String, hint: java.lang.String): scala.Unit
  def getTextInput(listener: TextInputListener, title: java.lang.String, text: java.lang.String, hint: java.lang.String, `type`: OnscreenKeyboardType): scala.Unit
  def setOnscreenKeyboardVisible(visible: scala.Boolean): scala.Unit
  def setOnscreenKeyboardVisible(visible: scala.Boolean, `type`: OnscreenKeyboardType): scala.Unit
  def openTextInputField(configuration: com.badlogic.gdx.input.NativeInputConfiguration): scala.Unit
  def closeTextInputField(isConfirmative: scala.Boolean): scala.Unit = {
    this.closeTextInputField(isConfirmative, null)
  }
  def closeTextInputField(isConfirmative: scala.Boolean, callback: com.badlogic.gdx.input.NativeInputConfiguration#NativeInputCloseCallback): scala.Unit = {
    ()
  }
  def isTextInputFieldOpened(): scala.Boolean = {
    return false
  }
  def setKeyboardHeightObserver(observer: KeyboardHeightObserver): scala.Unit
  def vibrate(milliseconds: scala.Int): scala.Unit
  def vibrate(milliseconds: scala.Int, fallback: scala.Boolean): scala.Unit
  def vibrate(milliseconds: scala.Int, amplitude: scala.Int, fallback: scala.Boolean): scala.Unit
  def vibrate(vibrationType: VibrationType): scala.Unit
  def getAzimuth(): scala.Float
  def getPitch(): scala.Float
  def getRoll(): scala.Float
  def getRotationMatrix(matrix: scala.Array[scala.Float]): scala.Unit
  def getCurrentEventTime(): scala.Long
  def setCatchKey(keycode: scala.Int, catchKey: scala.Boolean): scala.Unit
  def isCatchKey(keycode: scala.Int): scala.Boolean
  def setInputProcessor(processor: com.badlogic.gdx.InputProcessor): scala.Unit
  def getInputProcessor(): com.badlogic.gdx.InputProcessor
  def isPeripheralAvailable(peripheral: Peripheral): scala.Boolean
  def getRotation(): scala.Int
  def getNativeOrientation(): Orientation
  def setCursorCatched(catched: scala.Boolean): scala.Unit
  def isCursorCatched(): scala.Boolean
  def setCursorPosition(x: scala.Int, y: scala.Int): scala.Unit
  trait TextInputListener {
    def input(text: java.lang.String): scala.Unit
    def canceled(): scala.Unit
  }
  object Buttons {
    final val LEFT: scala.Int = 0
    final val RIGHT: scala.Int = 1
    final val MIDDLE: scala.Int = 2
    final val BACK: scala.Int = 3
    final val FORWARD: scala.Int = 4
  }
  object Keys {
    final val ANY_KEY: scala.Int = -1
    final val NUM_0: scala.Int = 7
    final val NUM_1: scala.Int = 8
    final val NUM_2: scala.Int = 9
    final val NUM_3: scala.Int = 10
    final val NUM_4: scala.Int = 11
    final val NUM_5: scala.Int = 12
    final val NUM_6: scala.Int = 13
    final val NUM_7: scala.Int = 14
    final val NUM_8: scala.Int = 15
    final val NUM_9: scala.Int = 16
    final val A: scala.Int = 29
    final val ALT_LEFT: scala.Int = 57
    final val ALT_RIGHT: scala.Int = 58
    final val APOSTROPHE: scala.Int = 75
    final val AT: scala.Int = 77
    final val B: scala.Int = 30
    final val BACK: scala.Int = 4
    final val BACKSLASH: scala.Int = 73
    final val C: scala.Int = 31
    final val CALL: scala.Int = 5
    final val CAMERA: scala.Int = 27
    final val CAPS_LOCK: scala.Int = 115
    final val CLEAR: scala.Int = 28
    final val COMMA: scala.Int = 55
    final val D: scala.Int = 32
    final val DEL: scala.Int = 67
    final val BACKSPACE: scala.Int = 67
    final val FORWARD_DEL: scala.Int = 112
    final val DPAD_CENTER: scala.Int = 23
    final val DPAD_DOWN: scala.Int = 20
    final val DPAD_LEFT: scala.Int = 21
    final val DPAD_RIGHT: scala.Int = 22
    final val DPAD_UP: scala.Int = 19
    final val CENTER: scala.Int = 23
    final val DOWN: scala.Int = 20
    final val LEFT: scala.Int = 21
    final val RIGHT: scala.Int = 22
    final val UP: scala.Int = 19
    final val E: scala.Int = 33
    final val ENDCALL: scala.Int = 6
    final val ENTER: scala.Int = 66
    final val ENVELOPE: scala.Int = 65
    final val EQUALS: scala.Int = 70
    final val EXPLORER: scala.Int = 64
    final val F: scala.Int = 34
    final val FOCUS: scala.Int = 80
    final val G: scala.Int = 35
    final val GRAVE: scala.Int = 68
    final val H: scala.Int = 36
    final val HEADSETHOOK: scala.Int = 79
    final val HOME: scala.Int = 3
    final val I: scala.Int = 37
    final val J: scala.Int = 38
    final val K: scala.Int = 39
    final val L: scala.Int = 40
    final val LEFT_BRACKET: scala.Int = 71
    final val M: scala.Int = 41
    final val MEDIA_FAST_FORWARD: scala.Int = 90
    final val MEDIA_NEXT: scala.Int = 87
    final val MEDIA_PLAY_PAUSE: scala.Int = 85
    final val MEDIA_PREVIOUS: scala.Int = 88
    final val MEDIA_REWIND: scala.Int = 89
    final val MEDIA_STOP: scala.Int = 86
    final val MENU: scala.Int = 82
    final val MINUS: scala.Int = 69
    final val MUTE: scala.Int = 91
    final val N: scala.Int = 42
    final val NOTIFICATION: scala.Int = 83
    final val NUM: scala.Int = 78
    final val O: scala.Int = 43
    final val P: scala.Int = 44
    final val PAUSE: scala.Int = 121
    final val PERIOD: scala.Int = 56
    final val PLUS: scala.Int = 81
    final val POUND: scala.Int = 18
    final val POWER: scala.Int = 26
    final val PRINT_SCREEN: scala.Int = 120
    final val Q: scala.Int = 45
    final val R: scala.Int = 46
    final val RIGHT_BRACKET: scala.Int = 72
    final val S: scala.Int = 47
    final val SCROLL_LOCK: scala.Int = 116
    final val SEARCH: scala.Int = 84
    final val SEMICOLON: scala.Int = 74
    final val SHIFT_LEFT: scala.Int = 59
    final val SHIFT_RIGHT: scala.Int = 60
    final val SLASH: scala.Int = 76
    final val SOFT_LEFT: scala.Int = 1
    final val SOFT_RIGHT: scala.Int = 2
    final val SPACE: scala.Int = 62
    final val STAR: scala.Int = 17
    final val SYM: scala.Int = 63
    final val T: scala.Int = 48
    final val TAB: scala.Int = 61
    final val U: scala.Int = 49
    final val UNKNOWN: scala.Int = 0
    final val V: scala.Int = 50
    final val VOLUME_DOWN: scala.Int = 25
    final val VOLUME_UP: scala.Int = 24
    final val W: scala.Int = 51
    final val X: scala.Int = 52
    final val Y: scala.Int = 53
    final val Z: scala.Int = 54
    final val META_ALT_LEFT_ON: scala.Int = 16
    final val META_ALT_ON: scala.Int = 2
    final val META_ALT_RIGHT_ON: scala.Int = 32
    final val META_SHIFT_LEFT_ON: scala.Int = 64
    final val META_SHIFT_ON: scala.Int = 1
    final val META_SHIFT_RIGHT_ON: scala.Int = 128
    final val META_SYM_ON: scala.Int = 4
    final val CONTROL_LEFT: scala.Int = 129
    final val CONTROL_RIGHT: scala.Int = 130
    final val ESCAPE: scala.Int = 111
    final val END: scala.Int = 123
    final val INSERT: scala.Int = 124
    final val PAGE_UP: scala.Int = 92
    final val PAGE_DOWN: scala.Int = 93
    final val PICTSYMBOLS: scala.Int = 94
    final val SWITCH_CHARSET: scala.Int = 95
    final val BUTTON_CIRCLE: scala.Int = 255
    final val BUTTON_A: scala.Int = 96
    final val BUTTON_B: scala.Int = 97
    final val BUTTON_C: scala.Int = 98
    final val BUTTON_X: scala.Int = 99
    final val BUTTON_Y: scala.Int = 100
    final val BUTTON_Z: scala.Int = 101
    final val BUTTON_L1: scala.Int = 102
    final val BUTTON_R1: scala.Int = 103
    final val BUTTON_L2: scala.Int = 104
    final val BUTTON_R2: scala.Int = 105
    final val BUTTON_THUMBL: scala.Int = 106
    final val BUTTON_THUMBR: scala.Int = 107
    final val BUTTON_START: scala.Int = 108
    final val BUTTON_SELECT: scala.Int = 109
    final val BUTTON_MODE: scala.Int = 110
    final val NUMPAD_0: scala.Int = 144
    final val NUMPAD_1: scala.Int = 145
    final val NUMPAD_2: scala.Int = 146
    final val NUMPAD_3: scala.Int = 147
    final val NUMPAD_4: scala.Int = 148
    final val NUMPAD_5: scala.Int = 149
    final val NUMPAD_6: scala.Int = 150
    final val NUMPAD_7: scala.Int = 151
    final val NUMPAD_8: scala.Int = 152
    final val NUMPAD_9: scala.Int = 153
    final val NUMPAD_DIVIDE: scala.Int = 154
    final val NUMPAD_MULTIPLY: scala.Int = 155
    final val NUMPAD_SUBTRACT: scala.Int = 156
    final val NUMPAD_ADD: scala.Int = 157
    final val NUMPAD_DOT: scala.Int = 158
    final val NUMPAD_COMMA: scala.Int = 159
    final val NUMPAD_ENTER: scala.Int = 160
    final val NUMPAD_EQUALS: scala.Int = 161
    final val NUMPAD_LEFT_PAREN: scala.Int = 162
    final val NUMPAD_RIGHT_PAREN: scala.Int = 163
    final val NUM_LOCK: scala.Int = 143
    final val WORLD_1: scala.Int = 240
    final val WORLD_2: scala.Int = 241
    final val COLON: scala.Int = 243
    final val F1: scala.Int = 131
    final val F2: scala.Int = 132
    final val F3: scala.Int = 133
    final val F4: scala.Int = 134
    final val F5: scala.Int = 135
    final val F6: scala.Int = 136
    final val F7: scala.Int = 137
    final val F8: scala.Int = 138
    final val F9: scala.Int = 139
    final val F10: scala.Int = 140
    final val F11: scala.Int = 141
    final val F12: scala.Int = 142
    final val F13: scala.Int = 183
    final val F14: scala.Int = 184
    final val F15: scala.Int = 185
    final val F16: scala.Int = 186
    final val F17: scala.Int = 187
    final val F18: scala.Int = 188
    final val F19: scala.Int = 189
    final val F20: scala.Int = 190
    final val F21: scala.Int = 191
    final val F22: scala.Int = 192
    final val F23: scala.Int = 193
    final val F24: scala.Int = 194
    final val MAX_KEYCODE: scala.Int = 255
    private var keyNames: com.badlogic.gdx.utils.ObjectIntMap[java.lang.String] = null.asInstanceOf[com.badlogic.gdx.utils.ObjectIntMap[java.lang.String]]
    def toString(keycode: scala.Int): java.lang.String = {
      if (keycode < 0) {
        throw new java.lang.IllegalArgumentException("keycode cannot be negative, keycode: " + keycode)
      } else ()
      if (keycode > Keys.MAX_KEYCODE) {
        throw new java.lang.IllegalArgumentException("keycode cannot be greater than 255, keycode: " + keycode)
      } else ()
      keycode match {
        case Keys.UNKNOWN => {
          return "Unknown"
        }
        case Keys.SOFT_LEFT => {
          return "Soft Left"
        }
        case Keys.SOFT_RIGHT => {
          return "Soft Right"
        }
        case Keys.HOME => {
          return "Home"
        }
        case Keys.BACK => {
          return "Back"
        }
        case Keys.CALL => {
          return "Call"
        }
        case Keys.ENDCALL => {
          return "End Call"
        }
        case Keys.NUM_0 => {
          return "0"
        }
        case Keys.NUM_1 => {
          return "1"
        }
        case Keys.NUM_2 => {
          return "2"
        }
        case Keys.NUM_3 => {
          return "3"
        }
        case Keys.NUM_4 => {
          return "4"
        }
        case Keys.NUM_5 => {
          return "5"
        }
        case Keys.NUM_6 => {
          return "6"
        }
        case Keys.NUM_7 => {
          return "7"
        }
        case Keys.NUM_8 => {
          return "8"
        }
        case Keys.NUM_9 => {
          return "9"
        }
        case Keys.STAR => {
          return "*"
        }
        case Keys.POUND => {
          return "#"
        }
        case Keys.UP => {
          return "Up"
        }
        case Keys.DOWN => {
          return "Down"
        }
        case Keys.LEFT => {
          return "Left"
        }
        case Keys.RIGHT => {
          return "Right"
        }
        case Keys.CENTER => {
          return "Center"
        }
        case Keys.VOLUME_UP => {
          return "Volume Up"
        }
        case Keys.VOLUME_DOWN => {
          return "Volume Down"
        }
        case Keys.POWER => {
          return "Power"
        }
        case Keys.CAMERA => {
          return "Camera"
        }
        case Keys.CLEAR => {
          return "Clear"
        }
        case Keys.A => {
          return "A"
        }
        case Keys.B => {
          return "B"
        }
        case Keys.C => {
          return "C"
        }
        case Keys.D => {
          return "D"
        }
        case Keys.E => {
          return "E"
        }
        case Keys.F => {
          return "F"
        }
        case Keys.G => {
          return "G"
        }
        case Keys.H => {
          return "H"
        }
        case Keys.I => {
          return "I"
        }
        case Keys.J => {
          return "J"
        }
        case Keys.K => {
          return "K"
        }
        case Keys.L => {
          return "L"
        }
        case Keys.M => {
          return "M"
        }
        case Keys.N => {
          return "N"
        }
        case Keys.O => {
          return "O"
        }
        case Keys.P => {
          return "P"
        }
        case Keys.Q => {
          return "Q"
        }
        case Keys.R => {
          return "R"
        }
        case Keys.S => {
          return "S"
        }
        case Keys.T => {
          return "T"
        }
        case Keys.U => {
          return "U"
        }
        case Keys.V => {
          return "V"
        }
        case Keys.W => {
          return "W"
        }
        case Keys.X => {
          return "X"
        }
        case Keys.Y => {
          return "Y"
        }
        case Keys.Z => {
          return "Z"
        }
        case Keys.COMMA => {
          return ","
        }
        case Keys.PERIOD => {
          return "."
        }
        case Keys.ALT_LEFT => {
          return "L-Alt"
        }
        case Keys.ALT_RIGHT => {
          return "R-Alt"
        }
        case Keys.SHIFT_LEFT => {
          return "L-Shift"
        }
        case Keys.SHIFT_RIGHT => {
          return "R-Shift"
        }
        case Keys.TAB => {
          return "Tab"
        }
        case Keys.SPACE => {
          return "Space"
        }
        case Keys.SYM => {
          return "SYM"
        }
        case Keys.EXPLORER => {
          return "Explorer"
        }
        case Keys.ENVELOPE => {
          return "Envelope"
        }
        case Keys.ENTER => {
          return "Enter"
        }
        case Keys.DEL => {
          return "Delete"
        }
        case Keys.GRAVE => {
          return "`"
        }
        case Keys.MINUS => {
          return "-"
        }
        case Keys.EQUALS => {
          return "="
        }
        case Keys.LEFT_BRACKET => {
          return "["
        }
        case Keys.RIGHT_BRACKET => {
          return "]"
        }
        case Keys.BACKSLASH => {
          return "\\"
        }
        case Keys.SEMICOLON => {
          return ";"
        }
        case Keys.APOSTROPHE => {
          return "'"
        }
        case Keys.SLASH => {
          return "/"
        }
        case Keys.AT => {
          return "@"
        }
        case Keys.NUM => {
          return "Num"
        }
        case Keys.HEADSETHOOK => {
          return "Headset Hook"
        }
        case Keys.FOCUS => {
          return "Focus"
        }
        case Keys.PLUS => {
          return "Plus"
        }
        case Keys.MENU => {
          return "Menu"
        }
        case Keys.NOTIFICATION => {
          return "Notification"
        }
        case Keys.SEARCH => {
          return "Search"
        }
        case Keys.MEDIA_PLAY_PAUSE => {
          return "Play/Pause"
        }
        case Keys.MEDIA_STOP => {
          return "Stop Media"
        }
        case Keys.MEDIA_NEXT => {
          return "Next Media"
        }
        case Keys.MEDIA_PREVIOUS => {
          return "Prev Media"
        }
        case Keys.MEDIA_REWIND => {
          return "Rewind"
        }
        case Keys.MEDIA_FAST_FORWARD => {
          return "Fast Forward"
        }
        case Keys.MUTE => {
          return "Mute"
        }
        case Keys.PAGE_UP => {
          return "Page Up"
        }
        case Keys.PAGE_DOWN => {
          return "Page Down"
        }
        case Keys.PICTSYMBOLS => {
          return "PICTSYMBOLS"
        }
        case Keys.SWITCH_CHARSET => {
          return "SWITCH_CHARSET"
        }
        case Keys.BUTTON_A => {
          return "A Button"
        }
        case Keys.BUTTON_B => {
          return "B Button"
        }
        case Keys.BUTTON_C => {
          return "C Button"
        }
        case Keys.BUTTON_X => {
          return "X Button"
        }
        case Keys.BUTTON_Y => {
          return "Y Button"
        }
        case Keys.BUTTON_Z => {
          return "Z Button"
        }
        case Keys.BUTTON_L1 => {
          return "L1 Button"
        }
        case Keys.BUTTON_R1 => {
          return "R1 Button"
        }
        case Keys.BUTTON_L2 => {
          return "L2 Button"
        }
        case Keys.BUTTON_R2 => {
          return "R2 Button"
        }
        case Keys.BUTTON_THUMBL => {
          return "Left Thumb"
        }
        case Keys.BUTTON_THUMBR => {
          return "Right Thumb"
        }
        case Keys.BUTTON_START => {
          return "Start"
        }
        case Keys.BUTTON_SELECT => {
          return "Select"
        }
        case Keys.BUTTON_MODE => {
          return "Button Mode"
        }
        case Keys.FORWARD_DEL => {
          return "Forward Delete"
        }
        case Keys.CONTROL_LEFT => {
          return "L-Ctrl"
        }
        case Keys.CONTROL_RIGHT => {
          return "R-Ctrl"
        }
        case Keys.ESCAPE => {
          return "Escape"
        }
        case Keys.END => {
          return "End"
        }
        case Keys.INSERT => {
          return "Insert"
        }
        case Keys.NUMPAD_0 => {
          return "Numpad 0"
        }
        case Keys.NUMPAD_1 => {
          return "Numpad 1"
        }
        case Keys.NUMPAD_2 => {
          return "Numpad 2"
        }
        case Keys.NUMPAD_3 => {
          return "Numpad 3"
        }
        case Keys.NUMPAD_4 => {
          return "Numpad 4"
        }
        case Keys.NUMPAD_5 => {
          return "Numpad 5"
        }
        case Keys.NUMPAD_6 => {
          return "Numpad 6"
        }
        case Keys.NUMPAD_7 => {
          return "Numpad 7"
        }
        case Keys.NUMPAD_8 => {
          return "Numpad 8"
        }
        case Keys.NUMPAD_9 => {
          return "Numpad 9"
        }
        case Keys.COLON => {
          return ":"
        }
        case Keys.F1 => {
          return "F1"
        }
        case Keys.F2 => {
          return "F2"
        }
        case Keys.F3 => {
          return "F3"
        }
        case Keys.F4 => {
          return "F4"
        }
        case Keys.F5 => {
          return "F5"
        }
        case Keys.F6 => {
          return "F6"
        }
        case Keys.F7 => {
          return "F7"
        }
        case Keys.F8 => {
          return "F8"
        }
        case Keys.F9 => {
          return "F9"
        }
        case Keys.F10 => {
          return "F10"
        }
        case Keys.F11 => {
          return "F11"
        }
        case Keys.F12 => {
          return "F12"
        }
        case Keys.F13 => {
          return "F13"
        }
        case Keys.F14 => {
          return "F14"
        }
        case Keys.F15 => {
          return "F15"
        }
        case Keys.F16 => {
          return "F16"
        }
        case Keys.F17 => {
          return "F17"
        }
        case Keys.F18 => {
          return "F18"
        }
        case Keys.F19 => {
          return "F19"
        }
        case Keys.F20 => {
          return "F20"
        }
        case Keys.F21 => {
          return "F21"
        }
        case Keys.F22 => {
          return "F22"
        }
        case Keys.F23 => {
          return "F23"
        }
        case Keys.F24 => {
          return "F24"
        }
        case Keys.NUMPAD_DIVIDE => {
          return "Num /"
        }
        case Keys.NUMPAD_MULTIPLY => {
          return "Num *"
        }
        case Keys.NUMPAD_SUBTRACT => {
          return "Num -"
        }
        case Keys.NUMPAD_ADD => {
          return "Num +"
        }
        case Keys.NUMPAD_DOT => {
          return "Num ."
        }
        case Keys.NUMPAD_COMMA => {
          return "Num ,"
        }
        case Keys.NUMPAD_ENTER => {
          return "Num Enter"
        }
        case Keys.NUMPAD_EQUALS => {
          return "Num ="
        }
        case Keys.NUMPAD_LEFT_PAREN => {
          return "Num ("
        }
        case Keys.NUMPAD_RIGHT_PAREN => {
          return "Num )"
        }
        case Keys.NUM_LOCK => {
          return "Num Lock"
        }
        case Keys.CAPS_LOCK => {
          return "Caps Lock"
        }
        case Keys.SCROLL_LOCK => {
          return "Scroll Lock"
        }
        case Keys.PAUSE => {
          return "Pause"
        }
        case Keys.PRINT_SCREEN => {
          return "Print"
        }
        case _ => {
          return null
        }
      }
    }
    def valueOf(keyname: java.lang.String): scala.Int = {
      if (Keys.keyNames == null) {
        Keys.initializeKeyNames()
      } else ()
      return Keys.keyNames.get(keyname, -1)
    }
    private def initializeKeyNames(): scala.Unit = {
      Keys.keyNames = new com.badlogic.gdx.utils.ObjectIntMap[java.lang.String]()
      { var i: scala.Int = 0; while (i < 256) { {
        val name: java.lang.String = Keys.toString(i)
        if (name != null) {
          Keys.keyNames.put(name, i)
        } else ()
      }; i = i + 1 } }
    }
  }
  sealed abstract class Peripheral
  object Peripheral {
    case object HardwareKeyboard extends Peripheral
    case object OnscreenKeyboard extends Peripheral
    case object MultitouchScreen extends Peripheral
    case object Accelerometer extends Peripheral
    case object Compass extends Peripheral
    case object Vibrator extends Peripheral
    case object HapticFeedback extends Peripheral
    case object Gyroscope extends Peripheral
    case object RotationVector extends Peripheral
    case object Pressure extends Peripheral
    def values(): Array[Peripheral] = Array(HardwareKeyboard, OnscreenKeyboard, MultitouchScreen, Accelerometer, Compass, Vibrator, HapticFeedback, Gyroscope, RotationVector, Pressure)
  }
  trait InputStringValidator {
    def validate(toCheck: java.lang.String): scala.Boolean
  }
  trait KeyboardHeightObserver {
    def onKeyboardHeightChanged(height: scala.Int): scala.Unit
    def onKeyboardShow(height: scala.Int): scala.Unit
    def onKeyboardHide(): scala.Unit
  }
  sealed abstract class OnscreenKeyboardType
  object OnscreenKeyboardType {
    case object Default extends OnscreenKeyboardType
    case object NumberPad extends OnscreenKeyboardType
    case object PhonePad extends OnscreenKeyboardType
    case object Email extends OnscreenKeyboardType
    case object Password extends OnscreenKeyboardType
    case object URI extends OnscreenKeyboardType
    def values(): Array[OnscreenKeyboardType] = Array(Default, NumberPad, PhonePad, Email, Password, URI)
  }
  sealed abstract class VibrationType
  object VibrationType {
    case object LIGHT extends VibrationType
    case object MEDIUM extends VibrationType
    case object HEAVY extends VibrationType
    def values(): Array[VibrationType] = Array(LIGHT, MEDIUM, HEAVY)
  }
  sealed abstract class Orientation
  object Orientation {
    case object Landscape extends Orientation
    case object Portrait extends Orientation
    def values(): Array[Orientation] = Array(Landscape, Portrait)
  }
}