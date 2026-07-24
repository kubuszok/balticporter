package com.badlogic.gdx.utils

class XmlWriter extends java.io.Writer {
  private var writer: java.io.Writer = null.asInstanceOf[java.io.Writer]
  private final val stack: com.badlogic.gdx.utils.Array[java.lang.String] = new com.badlogic.gdx.utils.Array()
  private var currentElement: java.lang.String = null.asInstanceOf[java.lang.String]
  private var indentNextClose: scala.Boolean = false
  var indent$field: scala.Int = 0
  def this(writer: java.io.Writer) = {
    this()
    this.writer = writer
  }
  private def indent(): scala.Unit = {
    var count: scala.Int = this.indent$field
    if (this.currentElement != null) {
      count = count + 1
    } else ()
    { var i: scala.Int = 0; while (i < count) { {
      this.writer.write('\t')
    }; i = i + 1 } }
  }
  def element(name: java.lang.String): XmlWriter = {
    if (this.startElementContent()) {
      this.writer.write('\n')
    } else ()
    this.indent()
    this.writer.write('<')
    this.writer.write(name)
    this.currentElement = name
    return this
  }
  def element(name: java.lang.String, text: java.lang.Object): XmlWriter = {
    return this.element(name).text(text).pop()
  }
  private def startElementContent(): scala.Boolean = {
    if (this.currentElement == null) {
      return false
    } else ()
    this.indent$field = this.indent$field + 1
    this.stack.add(this.currentElement)
    this.currentElement = null
    this.writer.write(">")
    return true
  }
  def attribute(name: java.lang.String, value: java.lang.Object): XmlWriter = {
    if (this.currentElement == null) {
      throw new java.lang.IllegalStateException()
    } else ()
    this.writer.write(' ')
    this.writer.write(name)
    this.writer.write("=\"")
    this.writer.write(if (value == null) "null" else value.toString())
    this.writer.write('\"')
    return this
  }
  def text(text: java.lang.Object): XmlWriter = {
    this.startElementContent()
    val string: java.lang.String = if (text == null) "null" else text.toString()
    this.indentNextClose = string.length() > 64
    if (this.indentNextClose) {
      this.writer.write('\n')
      this.indent()
    } else ()
    this.writer.write(string)
    if (this.indentNextClose) {
      this.writer.write('\n')
    } else ()
    return this
  }
  def pop(): XmlWriter = {
    if (this.currentElement != null) {
      this.writer.write("/>\n")
      this.currentElement = null
    } else {
      this.indent$field = java.lang.Math.max(this.indent$field - 1, 0)
      if (this.indentNextClose) {
        this.indent()
      } else ()
      this.writer.write("</")
      this.writer.write(this.stack.pop())
      this.writer.write(">\n")
    }
    this.indentNextClose = true
    return this
  }
  def close(): scala.Unit = {
    while (this.stack.size != 0) {
      this.pop()
    }
    this.writer.close()
  }
  def write(cbuf: scala.Array[scala.Char], off: scala.Int, len: scala.Int): scala.Unit = {
    this.startElementContent()
    this.writer.write(cbuf, off, len)
  }
  def flush(): scala.Unit = {
    this.writer.flush()
  }
}