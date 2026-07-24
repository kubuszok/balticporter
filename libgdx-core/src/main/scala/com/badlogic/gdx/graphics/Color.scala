package com.badlogic.gdx.graphics

class Color {
  var r: scala.Float = 0.0f
  var g: scala.Float = 0.0f
  var b: scala.Float = 0.0f
  var a: scala.Float = 0.0f
  def this(r: scala.Float, g: scala.Float, b: scala.Float, a: scala.Float) = {
    this()
    this.r = r
    this.g = g
    this.b = b
    this.a = a
    this.clamp()
  }
  def this(rgba8888: scala.Int) = {
    this()
    Color.rgba8888ToColor(this, rgba8888)
  }
  def this(color: Color) = {
    this()
    this.set(color)
  }
  def set(color: Color): Color = {
    this.r = color.r
    this.g = color.g
    this.b = color.b
    this.a = color.a
    return this
  }
  def set(rgb: Color, alpha: scala.Float): Color = {
    this.r = rgb.r
    this.g = rgb.g
    this.b = rgb.b
    this.a = com.badlogic.gdx.math.MathUtils.clamp(alpha, 0.0f, 1.0f)
    return this
  }
  def mul(color: Color): Color = {
    this.r = this.r * color.r
    this.g = this.g * color.g
    this.b = this.b * color.b
    this.a = this.a * color.a
    return this.clamp()
  }
  def mul(value: scala.Float): Color = {
    this.r = this.r * value
    this.g = this.g * value
    this.b = this.b * value
    this.a = this.a * value
    return this.clamp()
  }
  def add(color: Color): Color = {
    this.r = this.r + color.r
    this.g = this.g + color.g
    this.b = this.b + color.b
    this.a = this.a + color.a
    return this.clamp()
  }
  def sub(color: Color): Color = {
    this.r = this.r - color.r
    this.g = this.g - color.g
    this.b = this.b - color.b
    this.a = this.a - color.a
    return this.clamp()
  }
  def clamp(): Color = {
    if (this.r < 0) {
      this.r = 0
    } else {
      if (this.r > 1) {
        this.r = 1
      } else ()
    }
    if (this.g < 0) {
      this.g = 0
    } else {
      if (this.g > 1) {
        this.g = 1
      } else ()
    }
    if (this.b < 0) {
      this.b = 0
    } else {
      if (this.b > 1) {
        this.b = 1
      } else ()
    }
    if (this.a < 0) {
      this.a = 0
    } else {
      if (this.a > 1) {
        this.a = 1
      } else ()
    }
    return this
  }
  def set(r: scala.Float, g: scala.Float, b: scala.Float, a: scala.Float): Color = {
    this.r = r
    this.g = g
    this.b = b
    this.a = a
    return this.clamp()
  }
  def set(rgba: scala.Int): Color = {
    Color.rgba8888ToColor(this, rgba)
    return this
  }
  def add(r: scala.Float, g: scala.Float, b: scala.Float, a: scala.Float): Color = {
    this.r = this.r + r
    this.g = this.g + g
    this.b = this.b + b
    this.a = this.a + a
    return this.clamp()
  }
  def sub(r: scala.Float, g: scala.Float, b: scala.Float, a: scala.Float): Color = {
    this.r = this.r - r
    this.g = this.g - g
    this.b = this.b - b
    this.a = this.a - a
    return this.clamp()
  }
  def mul(r: scala.Float, g: scala.Float, b: scala.Float, a: scala.Float): Color = {
    this.r = this.r * r
    this.g = this.g * g
    this.b = this.b * b
    this.a = this.a * a
    return this.clamp()
  }
  def lerp(target: Color, t: scala.Float): Color = {
    this.r = this.r + (t * (target.r - this.r))
    this.g = this.g + (t * (target.g - this.g))
    this.b = this.b + (t * (target.b - this.b))
    this.a = this.a + (t * (target.a - this.a))
    return this.clamp()
  }
  def lerp(r: scala.Float, g: scala.Float, b: scala.Float, a: scala.Float, t: scala.Float): Color = {
    this.r = this.r + (t * (r - this.r))
    this.g = this.g + (t * (g - this.g))
    this.b = this.b + (t * (b - this.b))
    this.a = this.a + (t * (a - this.a))
    return this.clamp()
  }
  def premultiplyAlpha(): Color = {
    this.r = this.r * this.a
    this.g = this.g * this.a
    this.b = this.b * this.a
    return this
  }
  def equals(o: java.lang.Object): scala.Boolean = {
    if (this == o) {
      return true
    } else ()
    if ((o == null) || (this.getClass() != o.getClass())) {
      return false
    } else ()
    val color: Color = o.asInstanceOf[Color].asInstanceOf[Color]
    return this.toIntBits() == color.toIntBits()
  }
  def hashCode(): scala.Int = {
    var result: scala.Int = if (this.r != (+0.0f)) com.badlogic.gdx.utils.NumberUtils.floatToIntBits(this.r) else 0
    result = (31 * result) + (if (this.g != (+0.0f)) com.badlogic.gdx.utils.NumberUtils.floatToIntBits(this.g) else 0)
    result = (31 * result) + (if (this.b != (+0.0f)) com.badlogic.gdx.utils.NumberUtils.floatToIntBits(this.b) else 0)
    result = (31 * result) + (if (this.a != (+0.0f)) com.badlogic.gdx.utils.NumberUtils.floatToIntBits(this.a) else 0)
    return result
  }
  def toFloatBits(): scala.Float = {
    val color: scala.Int = ((((255 * this.a).asInstanceOf[scala.Int] << 24) | ((255 * this.b).asInstanceOf[scala.Int] << 16)) | ((255 * this.g).asInstanceOf[scala.Int] << 8)) | (255 * this.r).asInstanceOf[scala.Int]
    return com.badlogic.gdx.utils.NumberUtils.intToFloatColor(color)
  }
  def toIntBits(): scala.Int = {
    return ((((255 * this.a).asInstanceOf[scala.Int] << 24) | ((255 * this.b).asInstanceOf[scala.Int] << 16)) | ((255 * this.g).asInstanceOf[scala.Int] << 8)) | (255 * this.r).asInstanceOf[scala.Int]
  }
  def toString(): java.lang.String = {
    var value: java.lang.String = java.lang.Integer.toHexString(((((255 * this.r).asInstanceOf[scala.Int] << 24) | ((255 * this.g).asInstanceOf[scala.Int] << 16)) | ((255 * this.b).asInstanceOf[scala.Int] << 8)) | (255 * this.a).asInstanceOf[scala.Int])
    while (value.length() < 8) {
      value = "0" + value
    }
    return value
  }
  def fromHsv(h: scala.Float, s: scala.Float, v: scala.Float): Color = {
    val x: scala.Float = ((h / 60.0f) + 6) % 6
    val i: scala.Int = x.asInstanceOf[scala.Int].asInstanceOf[scala.Int]
    val f: scala.Float = x - i
    val p: scala.Float = v * (1 - s)
    val q: scala.Float = v * (1 - (s * f))
    val t: scala.Float = v * (1 - (s * (1 - f)))
    i match {
      case 0 => {
        this.r = v
        this.g = t
        this.b = p
      }
      case 1 => {
        this.r = q
        this.g = v
        this.b = p
      }
      case 2 => {
        this.r = p
        this.g = v
        this.b = t
      }
      case 3 => {
        this.r = p
        this.g = q
        this.b = v
      }
      case 4 => {
        this.r = t
        this.g = p
        this.b = v
      }
      case _ => {
        this.r = v
        this.g = p
        this.b = q
      }
    }
    return this.clamp()
  }
  def fromHsv(hsv: scala.Array[scala.Float]): Color = {
    return this.fromHsv(hsv(0), hsv(1), hsv(2))
  }
  def toHsv(hsv: scala.Array[scala.Float]): scala.Array[scala.Float] = {
    val max: scala.Float = java.lang.Math.max(java.lang.Math.max(this.r, this.g), this.b)
    val min: scala.Float = java.lang.Math.min(java.lang.Math.min(this.r, this.g), this.b)
    val range: scala.Float = max - min
    if (range == 0) {
      hsv(0) = 0
    } else {
      if (max == this.r) {
        hsv(0) = (((60 * (this.g - this.b)) / range) + 360) % 360
      } else {
        if (max == this.g) {
          hsv(0) = ((60 * (this.b - this.r)) / range) + 120
        } else {
          hsv(0) = ((60 * (this.r - this.g)) / range) + 240
        }
      }
    }
    if (max > 0) {
      hsv(1) = 1 - (min / max)
    } else {
      hsv(1) = 0
    }
    hsv(2) = max
    return hsv
  }
  def cpy(): Color = {
    return new Color(this)
  }
}
object Color {
  final val WHITE: Color = new Color(1, 1, 1, 1)
  final val LIGHT_GRAY: Color = new Color(-1077952513)
  final val GRAY: Color = new Color(2139062271)
  final val DARK_GRAY: Color = new Color(1061109759)
  final val BLACK: Color = new Color(0, 0, 0, 1)
  final val WHITE_FLOAT_BITS: scala.Float = Color.WHITE.toFloatBits()
  final val CLEAR: Color = new Color(0, 0, 0, 0)
  final val CLEAR_WHITE: Color = new Color(1, 1, 1, 0)
  final val BLUE: Color = new Color(0, 0, 1, 1)
  final val NAVY: Color = new Color(0, 0, 0.5f, 1)
  final val ROYAL: Color = new Color(1097458175)
  final val SLATE: Color = new Color(1887473919)
  final val SKY: Color = new Color(-2016482305)
  final val CYAN: Color = new Color(0, 1, 1, 1)
  final val TEAL: Color = new Color(0, 0.5f, 0.5f, 1)
  final val GREEN: Color = new Color(16711935)
  final val CHARTREUSE: Color = new Color(2147418367)
  final val LIME: Color = new Color(852308735)
  final val FOREST: Color = new Color(579543807)
  final val OLIVE: Color = new Color(1804477439)
  final val YELLOW: Color = new Color(-65281)
  final val GOLD: Color = new Color(-2686721)
  final val GOLDENROD: Color = new Color(-626712321)
  final val ORANGE: Color = new Color(-5963521)
  final val BROWN: Color = new Color(-1958407169)
  final val TAN: Color = new Color(-759919361)
  final val FIREBRICK: Color = new Color(-1306385665)
  final val RED: Color = new Color(-16776961)
  final val SCARLET: Color = new Color(-13361921)
  final val CORAL: Color = new Color(-8433409)
  final val SALMON: Color = new Color(-92245249)
  final val PINK: Color = new Color(-9849601)
  final val MAGENTA: Color = new Color(1, 0, 1, 1)
  final val PURPLE: Color = new Color(-1608453889)
  final val VIOLET: Color = new Color(-293409025)
  final val MAROON: Color = new Color(-1339006721)
  def valueOf(hex: java.lang.String): Color = {
    return Color.valueOf(hex, new Color())
  }
  def valueOf(hex$arg: java.lang.String, color: Color): Color = {
    var hex: java.lang.String = hex$arg
    hex = if (hex.charAt(0) == '#') hex.substring(1) else hex
    color.r = java.lang.Integer.parseInt(hex.substring(0, 2), 16) / 255.0f
    color.g = java.lang.Integer.parseInt(hex.substring(2, 4), 16) / 255.0f
    color.b = java.lang.Integer.parseInt(hex.substring(4, 6), 16) / 255.0f
    color.a = if (hex.length() != 8) 1 else java.lang.Integer.parseInt(hex.substring(6, 8), 16) / 255.0f
    return color
  }
  def toFloatBits(r: scala.Int, g: scala.Int, b: scala.Int, a: scala.Int): scala.Float = {
    val color: scala.Int = (((a << 24) | (b << 16)) | (g << 8)) | r
    val floatColor: scala.Float = com.badlogic.gdx.utils.NumberUtils.intToFloatColor(color)
    return floatColor
  }
  def toFloatBits(r: scala.Float, g: scala.Float, b: scala.Float, a: scala.Float): scala.Float = {
    val color: scala.Int = ((((255 * a).asInstanceOf[scala.Int] << 24) | ((255 * b).asInstanceOf[scala.Int] << 16)) | ((255 * g).asInstanceOf[scala.Int] << 8)) | (255 * r).asInstanceOf[scala.Int]
    return com.badlogic.gdx.utils.NumberUtils.intToFloatColor(color)
  }
  def toIntBits(r: scala.Int, g: scala.Int, b: scala.Int, a: scala.Int): scala.Int = {
    return (((a << 24) | (b << 16)) | (g << 8)) | r
  }
  def alpha(alpha: scala.Float): scala.Int = {
    return (alpha * 255.0f).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
  }
  def luminanceAlpha(luminance: scala.Float, alpha: scala.Float): scala.Int = {
    return ((luminance * 255.0f).asInstanceOf[scala.Int] << 8) | (alpha * 255).asInstanceOf[scala.Int]
  }
  def rgb565(r: scala.Float, g: scala.Float, b: scala.Float): scala.Int = {
    return (((r * 31).asInstanceOf[scala.Int] << 11) | ((g * 63).asInstanceOf[scala.Int] << 5)) | (b * 31).asInstanceOf[scala.Int]
  }
  def rgba4444(r: scala.Float, g: scala.Float, b: scala.Float, a: scala.Float): scala.Int = {
    return ((((r * 15).asInstanceOf[scala.Int] << 12) | ((g * 15).asInstanceOf[scala.Int] << 8)) | ((b * 15).asInstanceOf[scala.Int] << 4)) | (a * 15).asInstanceOf[scala.Int]
  }
  def rgb888(r: scala.Float, g: scala.Float, b: scala.Float): scala.Int = {
    return (((r * 255).asInstanceOf[scala.Int] << 16) | ((g * 255).asInstanceOf[scala.Int] << 8)) | (b * 255).asInstanceOf[scala.Int]
  }
  def rgba8888(r: scala.Float, g: scala.Float, b: scala.Float, a: scala.Float): scala.Int = {
    return ((((r * 255).asInstanceOf[scala.Int] << 24) | ((g * 255).asInstanceOf[scala.Int] << 16)) | ((b * 255).asInstanceOf[scala.Int] << 8)) | (a * 255).asInstanceOf[scala.Int]
  }
  def argb8888(a: scala.Float, r: scala.Float, g: scala.Float, b: scala.Float): scala.Int = {
    return ((((a * 255).asInstanceOf[scala.Int] << 24) | ((r * 255).asInstanceOf[scala.Int] << 16)) | ((g * 255).asInstanceOf[scala.Int] << 8)) | (b * 255).asInstanceOf[scala.Int]
  }
  def rgb565(color: Color): scala.Int = {
    return (((color.r * 31).asInstanceOf[scala.Int] << 11) | ((color.g * 63).asInstanceOf[scala.Int] << 5)) | (color.b * 31).asInstanceOf[scala.Int]
  }
  def rgba4444(color: Color): scala.Int = {
    return ((((color.r * 15).asInstanceOf[scala.Int] << 12) | ((color.g * 15).asInstanceOf[scala.Int] << 8)) | ((color.b * 15).asInstanceOf[scala.Int] << 4)) | (color.a * 15).asInstanceOf[scala.Int]
  }
  def rgb888(color: Color): scala.Int = {
    return (((color.r * 255).asInstanceOf[scala.Int] << 16) | ((color.g * 255).asInstanceOf[scala.Int] << 8)) | (color.b * 255).asInstanceOf[scala.Int]
  }
  def rgba8888(color: Color): scala.Int = {
    return ((((color.r * 255).asInstanceOf[scala.Int] << 24) | ((color.g * 255).asInstanceOf[scala.Int] << 16)) | ((color.b * 255).asInstanceOf[scala.Int] << 8)) | (color.a * 255).asInstanceOf[scala.Int]
  }
  def argb8888(color: Color): scala.Int = {
    return ((((color.a * 255).asInstanceOf[scala.Int] << 24) | ((color.r * 255).asInstanceOf[scala.Int] << 16)) | ((color.g * 255).asInstanceOf[scala.Int] << 8)) | (color.b * 255).asInstanceOf[scala.Int]
  }
  def rgb565ToColor(color: Color, value: scala.Int): scala.Unit = {
    color.r = ((value & 63488) >>> 11) / 31.0f
    color.g = ((value & 2016) >>> 5) / 63.0f
    color.b = ((value & 31) >>> 0) / 31.0f
  }
  def rgba4444ToColor(color: Color, value: scala.Int): scala.Unit = {
    color.r = ((value & 61440) >>> 12) / 15.0f
    color.g = ((value & 3840) >>> 8) / 15.0f
    color.b = ((value & 240) >>> 4) / 15.0f
    color.a = (value & 15) / 15.0f
  }
  def rgb888ToColor(color: Color, value: scala.Int): scala.Unit = {
    color.r = ((value & 16711680) >>> 16) / 255.0f
    color.g = ((value & 65280) >>> 8) / 255.0f
    color.b = (value & 255) / 255.0f
  }
  def rgba8888ToColor(color: Color, value: scala.Int): scala.Unit = {
    color.r = ((value & -16777216) >>> 24) / 255.0f
    color.g = ((value & 16711680) >>> 16) / 255.0f
    color.b = ((value & 65280) >>> 8) / 255.0f
    color.a = (value & 255) / 255.0f
  }
  def argb8888ToColor(color: Color, value: scala.Int): scala.Unit = {
    color.a = ((value & -16777216) >>> 24) / 255.0f
    color.r = ((value & 16711680) >>> 16) / 255.0f
    color.g = ((value & 65280) >>> 8) / 255.0f
    color.b = (value & 255) / 255.0f
  }
  def abgr8888ToColor(color: Color, value: scala.Int): scala.Unit = {
    color.a = ((value & -16777216) >>> 24) / 255.0f
    color.b = ((value & 16711680) >>> 16) / 255.0f
    color.g = ((value & 65280) >>> 8) / 255.0f
    color.r = (value & 255) / 255.0f
  }
  def abgr8888ToColor(color: Color, value: scala.Float): scala.Unit = {
    val c: scala.Int = com.badlogic.gdx.utils.NumberUtils.floatToIntColor(value)
    color.a = ((c & -16777216) >>> 24) / 255.0f
    color.b = ((c & 16711680) >>> 16) / 255.0f
    color.g = ((c & 65280) >>> 8) / 255.0f
    color.r = (c & 255) / 255.0f
  }
}