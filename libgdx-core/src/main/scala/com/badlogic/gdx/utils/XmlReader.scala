package com.badlogic.gdx.utils

class XmlReader {
  private final val elements: com.badlogic.gdx.utils.Array[Element] = new com.badlogic.gdx.utils.Array(8)
  private var root: Element = null.asInstanceOf[Element]
  private var current: Element = null.asInstanceOf[Element]
  private final val textBuffer: java.lang.StringBuilder = new java.lang.StringBuilder(64)
  private var entitiesText: java.lang.String = null.asInstanceOf[java.lang.String]
  def parse(xml: java.lang.String): Element = {
    val data: scala.Array[scala.Char] = xml.toCharArray()
    return this.parse(data, 0, data.length)
  }
  def parse(reader: java.io.Reader): Element = {
    try {
      var data: scala.Array[scala.Char] = new Array[scala.Char](1024)
      var offset: scala.Int = 0
      while (true) {
        val length: scala.Int = reader.read(data, offset, data.length - offset)
        if (length == (-1)) {
          /* break */ ()
        } else ()
        if (length == 0) {
          val newData: scala.Array[scala.Char] = new Array[scala.Char](data.length * 2)
          java.lang.System.arraycopy(data, 0, newData, 0, data.length)
          data = newData
        } else {
          offset = offset + length
        }
      }
      return this.parse(data, 0, offset)
    } catch {
      case ex: java.io.IOException => {
        throw new com.badlogic.gdx.utils.SerializationException(ex)
      }
    } finally {
      com.badlogic.gdx.utils.StreamUtils.closeQuietly(reader)
    }
  }
  def parse(input: java.io.InputStream): Element = {
    try {
      return this.parse(new java.io.InputStreamReader(input, "UTF-8"))
    } catch {
      case ex: java.io.IOException => {
        throw new com.badlogic.gdx.utils.SerializationException(ex)
      }
    } finally {
      com.badlogic.gdx.utils.StreamUtils.closeQuietly(input)
    }
  }
  def parse(file: com.badlogic.gdx.files.FileHandle): Element = {
    try {
      return this.parse(file.reader("UTF-8"))
    } catch {
      case ex: java.lang.Exception => {
        throw new com.badlogic.gdx.utils.SerializationException("Error parsing file: " + file, ex)
      }
    }
  }
  def parse(data: scala.Array[scala.Char], offset: scala.Int, length: scala.Int): Element = {
    var cs: scala.Int = 0
    var p: scala.Int = offset
    val pe: scala.Int = length
    var s: scala.Int = 0
    var attributeName: java.lang.String = null
    var hasBody: scala.Boolean = false
    {
      cs = XmlReader.xml_start
    }
    {
      var _klen: scala.Int = 0
      var _trans: scala.Int = 0
      var _acts: scala.Int = 0
      var _nacts: scala.Int = 0
      var _keys: scala.Int = 0
      var _goto_targ: scala.Int = 0
      while (true) {
        _goto_targ match {
          case 0 => {
            if (p == pe) {
              _goto_targ = 4
              /* continue */ ()
            } else ()
            if (cs == 0) {
              _goto_targ = 5
              /* continue */ ()
            } else ()
            while ({ {
              _keys = XmlReader._xml_key_offsets(cs)
              _trans = XmlReader._xml_index_offsets(cs)
              _klen = XmlReader._xml_single_lengths(cs)
              if (_klen > 0) {
                var _lower: scala.Int = _keys
                var _mid: scala.Int = 0
                var _upper: scala.Int = (_keys + _klen) - 1
                while (true) {
                  if (_upper < _lower) {
                    /* break */ ()
                  } else ()
                  _mid = _lower + ((_upper - _lower) >> 1)
                  if (data(p) < XmlReader._xml_trans_keys(_mid)) {
                    _upper = _mid - 1
                  } else {
                    if (data(p) > XmlReader._xml_trans_keys(_mid)) {
                      _lower = _mid + 1
                    } else {
                      _trans = _trans + (_mid - _keys)
                      /* break */ ()
                    }
                  }
                }
                _keys = _keys + _klen
                _trans = _trans + _klen
              } else ()
              _klen = XmlReader._xml_range_lengths(cs)
              if (_klen > 0) {
                var _lower: scala.Int = _keys
                var _mid: scala.Int = 0
                var _upper: scala.Int = (_keys + (_klen << 1)) - 2
                while (true) {
                  if (_upper < _lower) {
                    /* break */ ()
                  } else ()
                  _mid = _lower + (((_upper - _lower) >> 1) & (~1))
                  if (data(p) < XmlReader._xml_trans_keys(_mid)) {
                    _upper = _mid - 2
                  } else {
                    if (data(p) > XmlReader._xml_trans_keys(_mid + 1)) {
                      _lower = _mid + 2
                    } else {
                      _trans = _trans + ((_mid - _keys) >> 1)
                      /* break */ ()
                    }
                  }
                }
                _trans = _trans + _klen
              } else ()
            }; false }) ()
            _trans = XmlReader._xml_indicies(_trans)
            cs = XmlReader._xml_trans_targs(_trans)
            if (XmlReader._xml_trans_actions(_trans) != 0) {
              _acts = XmlReader._xml_trans_actions(_trans)
              _nacts = XmlReader._xml_actions({ _acts += 1; _acts }).asInstanceOf[scala.Int]
              while ({ _nacts -= 1; _nacts } > 0) {
                XmlReader._xml_actions({ _acts += 1; _acts }) match {
                  case 0 => {
                    {
                      s = p
                    }
                  }
                  case 1 => {
                    {
                      val c: scala.Char = data(s)
                      if ((c == '?') || (c == '!')) {
                        if (((((((data(s + 1) == '[') && (data(s + 2) == 'C')) && (data(s + 3) == 'D')) && (data(s + 4) == 'A')) && (data(s + 5) == 'T')) && (data(s + 6) == 'A')) && (data(s + 7) == '[')) {
                          s = s + 8
                          p = s + 2
                          while (((data(p - 2) != ']') || (data(p - 1) != ']')) || (data(p) != '>')) {
                            p = p + 1
                          }
                          this.text(new java.lang.String(data, s, (p - s) - 2))
                        } else {
                          if (((c == '!') && (data(s + 1) == '-')) && (data(s + 2) == '-')) {
                            p = s + 3
                            while (((data(p) != '-') || (data(p + 1) != '-')) || (data(p + 2) != '>')) {
                              p = p + 1
                            }
                            p = p + 2
                          } else {
                            while (data(p) != '>') {
                              p = p + 1
                            }
                          }
                        }
                        {
                          cs = 15
                          _goto_targ = 2
                          if (true) {
                            /* continue */ ()
                          } else ()
                        }
                      } else ()
                      hasBody = true
                      this.open(new java.lang.String(data, s, p - s))
                    }
                  }
                  case 2 => {
                    {
                      hasBody = false
                      this.close()
                      {
                        cs = 15
                        _goto_targ = 2
                        if (true) {
                          /* continue */ ()
                        } else ()
                      }
                    }
                  }
                  case 3 => {
                    {
                      this.close()
                      {
                        cs = 15
                        _goto_targ = 2
                        if (true) {
                          /* continue */ ()
                        } else ()
                      }
                    }
                  }
                  case 4 => {
                    {
                      if (hasBody) {
                        cs = 15
                        _goto_targ = 2
                        if (true) {
                          /* continue */ ()
                        } else ()
                      } else ()
                    }
                  }
                  case 5 => {
                    {
                      attributeName = new java.lang.String(data, s, p - s)
                    }
                  }
                  case 6 => {
                    {
                      var `end`: scala.Int = p
                      while (`end` != s) {
                        data(`end` - 1) match {
                          case ' ' | '\t' | '\n' | '\r' => {
                            `end` = `end` - 1
                            /* continue */ ()
                          }
                        }
                        /* break */ ()
                      }
                      var current: scala.Int = s
                      var entityFound: scala.Boolean = false
                      while (current != `end`) {
                        if (data({ current += 1; current }) != '&') {
                          /* continue */ ()
                        } else ()
                        val entityStart: scala.Int = current
                        while (current != `end`) {
                          if (data({ current += 1; current }) != ';') {
                            /* continue */ ()
                          } else ()
                          this.textBuffer.append(data, s, (entityStart - s) - 1)
                          val name: java.lang.String = new java.lang.String(data, entityStart, (current - entityStart) - 1)
                          val value: java.lang.String = this.entity(name)
                          this.textBuffer.append(if (value != null) value else name)
                          s = current
                          entityFound = true
                          /* break */ ()
                        }
                      }
                      if (entityFound) {
                        if (s < `end`) {
                          this.textBuffer.append(data, s, `end` - s)
                        } else ()
                        this.entitiesText = this.textBuffer.toString()
                        this.textBuffer.setLength(0)
                      } else {
                        this.entitiesText = new java.lang.String(data, s, `end` - s)
                      }
                    }
                  }
                  case 7 => {
                    {
                      this.attribute(attributeName, this.entitiesText)
                    }
                  }
                  case 8 => {
                    {
                      this.text(this.entitiesText)
                    }
                  }
                }
              }
            } else ()
            if (cs == 0) {
              _goto_targ = 5
              /* continue */ ()
            } else ()
            if ({ p += 1; p } != pe) {
              _goto_targ = 1
              /* continue */ ()
            } else ()
          }
          case 1 => {
            while ({ {
              _keys = XmlReader._xml_key_offsets(cs)
              _trans = XmlReader._xml_index_offsets(cs)
              _klen = XmlReader._xml_single_lengths(cs)
              if (_klen > 0) {
                var _lower: scala.Int = _keys
                var _mid: scala.Int = 0
                var _upper: scala.Int = (_keys + _klen) - 1
                while (true) {
                  if (_upper < _lower) {
                    /* break */ ()
                  } else ()
                  _mid = _lower + ((_upper - _lower) >> 1)
                  if (data(p) < XmlReader._xml_trans_keys(_mid)) {
                    _upper = _mid - 1
                  } else {
                    if (data(p) > XmlReader._xml_trans_keys(_mid)) {
                      _lower = _mid + 1
                    } else {
                      _trans = _trans + (_mid - _keys)
                      /* break */ ()
                    }
                  }
                }
                _keys = _keys + _klen
                _trans = _trans + _klen
              } else ()
              _klen = XmlReader._xml_range_lengths(cs)
              if (_klen > 0) {
                var _lower: scala.Int = _keys
                var _mid: scala.Int = 0
                var _upper: scala.Int = (_keys + (_klen << 1)) - 2
                while (true) {
                  if (_upper < _lower) {
                    /* break */ ()
                  } else ()
                  _mid = _lower + (((_upper - _lower) >> 1) & (~1))
                  if (data(p) < XmlReader._xml_trans_keys(_mid)) {
                    _upper = _mid - 2
                  } else {
                    if (data(p) > XmlReader._xml_trans_keys(_mid + 1)) {
                      _lower = _mid + 2
                    } else {
                      _trans = _trans + ((_mid - _keys) >> 1)
                      /* break */ ()
                    }
                  }
                }
                _trans = _trans + _klen
              } else ()
            }; false }) ()
            _trans = XmlReader._xml_indicies(_trans)
            cs = XmlReader._xml_trans_targs(_trans)
            if (XmlReader._xml_trans_actions(_trans) != 0) {
              _acts = XmlReader._xml_trans_actions(_trans)
              _nacts = XmlReader._xml_actions({ _acts += 1; _acts }).asInstanceOf[scala.Int]
              while ({ _nacts -= 1; _nacts } > 0) {
                XmlReader._xml_actions({ _acts += 1; _acts }) match {
                  case 0 => {
                    {
                      s = p
                    }
                  }
                  case 1 => {
                    {
                      val c: scala.Char = data(s)
                      if ((c == '?') || (c == '!')) {
                        if (((((((data(s + 1) == '[') && (data(s + 2) == 'C')) && (data(s + 3) == 'D')) && (data(s + 4) == 'A')) && (data(s + 5) == 'T')) && (data(s + 6) == 'A')) && (data(s + 7) == '[')) {
                          s = s + 8
                          p = s + 2
                          while (((data(p - 2) != ']') || (data(p - 1) != ']')) || (data(p) != '>')) {
                            p = p + 1
                          }
                          this.text(new java.lang.String(data, s, (p - s) - 2))
                        } else {
                          if (((c == '!') && (data(s + 1) == '-')) && (data(s + 2) == '-')) {
                            p = s + 3
                            while (((data(p) != '-') || (data(p + 1) != '-')) || (data(p + 2) != '>')) {
                              p = p + 1
                            }
                            p = p + 2
                          } else {
                            while (data(p) != '>') {
                              p = p + 1
                            }
                          }
                        }
                        {
                          cs = 15
                          _goto_targ = 2
                          if (true) {
                            /* continue */ ()
                          } else ()
                        }
                      } else ()
                      hasBody = true
                      this.open(new java.lang.String(data, s, p - s))
                    }
                  }
                  case 2 => {
                    {
                      hasBody = false
                      this.close()
                      {
                        cs = 15
                        _goto_targ = 2
                        if (true) {
                          /* continue */ ()
                        } else ()
                      }
                    }
                  }
                  case 3 => {
                    {
                      this.close()
                      {
                        cs = 15
                        _goto_targ = 2
                        if (true) {
                          /* continue */ ()
                        } else ()
                      }
                    }
                  }
                  case 4 => {
                    {
                      if (hasBody) {
                        cs = 15
                        _goto_targ = 2
                        if (true) {
                          /* continue */ ()
                        } else ()
                      } else ()
                    }
                  }
                  case 5 => {
                    {
                      attributeName = new java.lang.String(data, s, p - s)
                    }
                  }
                  case 6 => {
                    {
                      var `end`: scala.Int = p
                      while (`end` != s) {
                        data(`end` - 1) match {
                          case ' ' | '\t' | '\n' | '\r' => {
                            `end` = `end` - 1
                            /* continue */ ()
                          }
                        }
                        /* break */ ()
                      }
                      var current: scala.Int = s
                      var entityFound: scala.Boolean = false
                      while (current != `end`) {
                        if (data({ current += 1; current }) != '&') {
                          /* continue */ ()
                        } else ()
                        val entityStart: scala.Int = current
                        while (current != `end`) {
                          if (data({ current += 1; current }) != ';') {
                            /* continue */ ()
                          } else ()
                          this.textBuffer.append(data, s, (entityStart - s) - 1)
                          val name: java.lang.String = new java.lang.String(data, entityStart, (current - entityStart) - 1)
                          val value: java.lang.String = this.entity(name)
                          this.textBuffer.append(if (value != null) value else name)
                          s = current
                          entityFound = true
                          /* break */ ()
                        }
                      }
                      if (entityFound) {
                        if (s < `end`) {
                          this.textBuffer.append(data, s, `end` - s)
                        } else ()
                        this.entitiesText = this.textBuffer.toString()
                        this.textBuffer.setLength(0)
                      } else {
                        this.entitiesText = new java.lang.String(data, s, `end` - s)
                      }
                    }
                  }
                  case 7 => {
                    {
                      this.attribute(attributeName, this.entitiesText)
                    }
                  }
                  case 8 => {
                    {
                      this.text(this.entitiesText)
                    }
                  }
                }
              }
            } else ()
            if (cs == 0) {
              _goto_targ = 5
              /* continue */ ()
            } else ()
            if ({ p += 1; p } != pe) {
              _goto_targ = 1
              /* continue */ ()
            } else ()
          }
          case 2 => {
            if (cs == 0) {
              _goto_targ = 5
              /* continue */ ()
            } else ()
            if ({ p += 1; p } != pe) {
              _goto_targ = 1
              /* continue */ ()
            } else ()
          }
          case 4 | 5 => {
            ()
          }
        }
        /* break */ ()
      }
    }
    this.entitiesText = null
    if (p < pe) {
      var lineNumber: scala.Int = 1
      { var i: scala.Int = 0; while (i < p) { {
        if (data(i) == '\n') {
          lineNumber = lineNumber + 1
        } else ()
      }; i = i + 1 } }
      throw new com.badlogic.gdx.utils.SerializationException((("Error parsing XML on line " + lineNumber) + " near: ") + new java.lang.String(data, p, java.lang.Math.min(32, pe - p)))
    } else {
      if (this.elements.size != 0) {
        val element: Element = this.elements.peek()
        this.elements.clear()
        throw new com.badlogic.gdx.utils.SerializationException("Error parsing XML, unclosed element: " + element.getName())
      } else ()
    }
    var root: Element = this.root
    this.root = null
    return root
  }
  protected def open(name: java.lang.String): scala.Unit = {
    val child: Element = new Element(name, this.current)
    val parent: Element = this.current
    if (parent != null) {
      parent.addChild(child)
    } else ()
    this.elements.add(child)
    this.current = child
  }
  protected def attribute(name: java.lang.String, value: java.lang.String): scala.Unit = {
    this.current.setAttribute(name, value)
  }
  protected def entity(name: java.lang.String): java.lang.String = {
    if (name.equals("lt")) {
      return "<"
    } else ()
    if (name.equals("gt")) {
      return ">"
    } else ()
    if (name.equals("amp")) {
      return "&"
    } else ()
    if (name.equals("apos")) {
      return "'"
    } else ()
    if (name.equals("quot")) {
      return "\""
    } else ()
    if (name.startsWith("#x")) {
      return java.lang.Character.toString(java.lang.Integer.parseInt(name.substring(2), 16).asInstanceOf[scala.Char])
    } else ()
    return null
  }
  protected def text(text: java.lang.String): scala.Unit = {
    val existing: java.lang.String = this.current.getText()
    this.current.setText(if (existing != null) existing + text else text)
  }
  protected def close(): scala.Unit = {
    this.root = this.elements.pop()
    this.current = if (this.elements.size > 0) this.elements.peek() else null
  }
  class Element {
    private var name: java.lang.String = null.asInstanceOf[java.lang.String]
    private var attributes: com.badlogic.gdx.utils.ObjectMap[java.lang.String, java.lang.String] = null.asInstanceOf[com.badlogic.gdx.utils.ObjectMap[java.lang.String, java.lang.String]]
    private var children: com.badlogic.gdx.utils.Array[Element] = null.asInstanceOf[com.badlogic.gdx.utils.Array[Element]]
    private var text: java.lang.String = null.asInstanceOf[java.lang.String]
    private var parent: Element = null.asInstanceOf[Element]
    def this(name: java.lang.String, parent: Element) = {
      this()
      this.name = name
      this.parent = parent
    }
    def getName(): java.lang.String = {
      return this.name
    }
    def getAttributes(): com.badlogic.gdx.utils.ObjectMap[java.lang.String, java.lang.String] = {
      return this.attributes
    }
    def getAttribute(name: java.lang.String): java.lang.String = {
      if (this.attributes == null) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException((("Element " + this.name) + " doesn't have attribute: ") + name)
      } else ()
      val value: java.lang.String = this.attributes.get(name)
      if (value == null) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException((("Element " + this.name) + " doesn't have attribute: ") + name)
      } else ()
      return value
    }
    def getAttribute(name: java.lang.String, defaultValue: java.lang.String): java.lang.String = {
      if (this.attributes == null) {
        return defaultValue
      } else ()
      val value: java.lang.String = this.attributes.get(name)
      if (value == null) {
        return defaultValue
      } else ()
      return value
    }
    def hasAttribute(name: java.lang.String): scala.Boolean = {
      if (this.attributes == null) {
        return false
      } else ()
      return this.attributes.containsKey(name)
    }
    def setAttribute(name: java.lang.String, value: java.lang.String): scala.Unit = {
      if (this.attributes == null) {
        this.attributes = new com.badlogic.gdx.utils.ObjectMap(8)
      } else ()
      this.attributes.put(name, value)
    }
    def getChildCount(): scala.Int = {
      if (this.children == null) {
        return 0
      } else ()
      return this.children.size
    }
    def getChildren(): com.badlogic.gdx.utils.Array[Element] = {
      return this.children
    }
    def getChild(index: scala.Int): Element = {
      if (this.children == null) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("Element has no children: " + this.name)
      } else ()
      return this.children.get(index)
    }
    def addChild(element: Element): scala.Unit = {
      if (element == null) {
        throw new java.lang.IllegalArgumentException("element cannot be null.")
      } else ()
      if (this.children == null) {
        this.children = new com.badlogic.gdx.utils.Array(8)
      } else ()
      this.children.add(element)
      element.parent = this
    }
    def getText(): java.lang.String = {
      return this.text
    }
    def setText(text: java.lang.String): scala.Unit = {
      this.text = text
    }
    def removeChild(index: scala.Int): scala.Unit = {
      if (this.children != null) {
        val removedChild: Element = this.children.removeIndex(index)
        if (removedChild != null) {
          removedChild.parent = null
        } else ()
      } else ()
    }
    def removeChild(child: Element): scala.Unit = {
      if (this.children != null) {
        val removeSuccess: scala.Boolean = this.children.removeValue(child, true)
        if (removeSuccess) {
          child.parent = null
        } else ()
      } else ()
    }
    def remove(): scala.Unit = {
      this.parent.removeChild(this)
      this.parent = null
    }
    def replaceChild(child: Element, replacement: Element): scala.Unit = {
      if (child == null) {
        throw new java.lang.IllegalArgumentException("child cannot be null.")
      } else ()
      if (replacement == null) {
        throw new java.lang.IllegalArgumentException("replacement cannot be null.")
      } else ()
      if (this.children == null) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("Element has no children: " + this.name)
      } else ()
      if (!this.children.replaceFirst(child, true, replacement)) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException((("Element '" + this.name) + "' does not contain child: ") + child)
      } else {
        replacement.parent = child.parent
        child.parent = null
      }
    }
    def getParent(): Element = {
      return this.parent
    }
    def toString(): java.lang.String = {
      return this.toString("")
    }
    def toString(indent: java.lang.String): java.lang.String = {
      val buffer: java.lang.StringBuilder = new java.lang.StringBuilder(128)
      buffer.append(indent)
      buffer.append('<')
      buffer.append(this.name)
      if (this.attributes != null) {
        for (entry <- this.attributes.entries()) {
          buffer.append(' ')
          buffer.append(entry.key)
          buffer.append("=\"")
          buffer.append(entry.value)
          buffer.append('\"')
        }
      } else ()
      if ((this.children == null) && ((this.text == null) || (this.text.length() == 0))) {
        buffer.append("/>")
      } else {
        buffer.append(">\n")
        val childIndent: java.lang.String = indent + '\t'
        if ((this.text != null) && (this.text.length() > 0)) {
          buffer.append(childIndent)
          buffer.append(this.text)
          buffer.append('\n')
        } else ()
        if (this.children != null) {
          for (child <- this.children) {
            buffer.append(child.toString(childIndent))
            buffer.append('\n')
          }
        } else ()
        buffer.append(indent)
        buffer.append("</")
        buffer.append(this.name)
        buffer.append('>')
      }
      return buffer.toString()
    }
    def getChildByName(name: java.lang.String): Element = {
      if (this.children == null) {
        return null
      } else ()
      { var i: scala.Int = 0; while (i < this.children.size) { {
        val element: Element = this.children.get(i)
        if (element.name.equals(name)) {
          return element
        } else ()
      }; i = i + 1 } }
      return null
    }
    def hasChild(name: java.lang.String): scala.Boolean = {
      if (this.children == null) {
        return false
      } else ()
      return this.getChildByName(name) != null
    }
    def getChildByNameRecursive(name: java.lang.String): Element = {
      if (this.children == null) {
        return null
      } else ()
      { var i: scala.Int = 0; while (i < this.children.size) { {
        val element: Element = this.children.get(i)
        if (element.name.equals(name)) {
          return element
        } else ()
        val found: Element = element.getChildByNameRecursive(name)
        if (found != null) {
          return found
        } else ()
      }; i = i + 1 } }
      return null
    }
    def hasChildRecursive(name: java.lang.String): scala.Boolean = {
      if (this.children == null) {
        return false
      } else ()
      return this.getChildByNameRecursive(name) != null
    }
    def getChildrenByName(name: java.lang.String): com.badlogic.gdx.utils.Array[Element] = {
      val result: com.badlogic.gdx.utils.Array[Element] = new com.badlogic.gdx.utils.Array[Element]()
      if (this.children == null) {
        return result
      } else ()
      { var i: scala.Int = 0; while (i < this.children.size) { {
        val child: Element = this.children.get(i)
        if (child.name.equals(name)) {
          result.add(child)
        } else ()
      }; i = i + 1 } }
      return result
    }
    def getChildrenByNameRecursively(name: java.lang.String): com.badlogic.gdx.utils.Array[Element] = {
      val result: com.badlogic.gdx.utils.Array[Element] = new com.badlogic.gdx.utils.Array[Element]()
      this.getChildrenByNameRecursively(name, result)
      return result
    }
    private def getChildrenByNameRecursively(name: java.lang.String, result: com.badlogic.gdx.utils.Array[Element]): scala.Unit = {
      if (this.children == null) {
        return
      } else ()
      { var i: scala.Int = 0; while (i < this.children.size) { {
        val child: Element = this.children.get(i)
        if (child.name.equals(name)) {
          result.add(child)
        } else ()
        child.getChildrenByNameRecursively(name, result)
      }; i = i + 1 } }
    }
    def getFloatAttribute(name: java.lang.String): scala.Float = {
      return java.lang.Float.parseFloat(this.getAttribute(name))
    }
    def getFloatAttribute(name: java.lang.String, defaultValue: scala.Float): scala.Float = {
      val value: java.lang.String = this.getAttribute(name, null)
      if (value == null) {
        return defaultValue
      } else ()
      return java.lang.Float.parseFloat(value)
    }
    def getIntAttribute(name: java.lang.String): scala.Int = {
      return java.lang.Integer.parseInt(this.getAttribute(name))
    }
    def getIntAttribute(name: java.lang.String, defaultValue: scala.Int): scala.Int = {
      val value: java.lang.String = this.getAttribute(name, null)
      if (value == null) {
        return defaultValue
      } else ()
      return java.lang.Integer.parseInt(value)
    }
    def getBooleanAttribute(name: java.lang.String): scala.Boolean = {
      return java.lang.Boolean.parseBoolean(this.getAttribute(name))
    }
    def getBooleanAttribute(name: java.lang.String, defaultValue: scala.Boolean): scala.Boolean = {
      val value: java.lang.String = this.getAttribute(name, null)
      if (value == null) {
        return defaultValue
      } else ()
      return java.lang.Boolean.parseBoolean(value)
    }
    def get(name: java.lang.String): java.lang.String = {
      val value: java.lang.String = this.get(name, null)
      if (value == null) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException((("Element " + this.name) + " doesn't have attribute or child: ") + name)
      } else ()
      return value
    }
    def get(name: java.lang.String, defaultValue: java.lang.String): java.lang.String = {
      if (this.attributes != null) {
        val value: java.lang.String = this.attributes.get(name)
        if (value != null) {
          return value
        } else ()
      } else ()
      val child: Element = this.getChildByName(name)
      if (child == null) {
        return defaultValue
      } else ()
      val value: java.lang.String = child.getText()
      if (value == null) {
        return defaultValue
      } else ()
      return value
    }
    def getInt(name: java.lang.String): scala.Int = {
      val value: java.lang.String = this.get(name, null)
      if (value == null) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException((("Element " + this.name) + " doesn't have attribute or child: ") + name)
      } else ()
      return java.lang.Integer.parseInt(value)
    }
    def getInt(name: java.lang.String, defaultValue: scala.Int): scala.Int = {
      val value: java.lang.String = this.get(name, null)
      if (value == null) {
        return defaultValue
      } else ()
      return java.lang.Integer.parseInt(value)
    }
    def getFloat(name: java.lang.String): scala.Float = {
      val value: java.lang.String = this.get(name, null)
      if (value == null) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException((("Element " + this.name) + " doesn't have attribute or child: ") + name)
      } else ()
      return java.lang.Float.parseFloat(value)
    }
    def getFloat(name: java.lang.String, defaultValue: scala.Float): scala.Float = {
      val value: java.lang.String = this.get(name, null)
      if (value == null) {
        return defaultValue
      } else ()
      return java.lang.Float.parseFloat(value)
    }
    def getBoolean(name: java.lang.String): scala.Boolean = {
      val value: java.lang.String = this.get(name, null)
      if (value == null) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException((("Element " + this.name) + " doesn't have attribute or child: ") + name)
      } else ()
      return java.lang.Boolean.parseBoolean(value)
    }
    def getBoolean(name: java.lang.String, defaultValue: scala.Boolean): scala.Boolean = {
      val value: java.lang.String = this.get(name, null)
      if (value == null) {
        return defaultValue
      } else ()
      return java.lang.Boolean.parseBoolean(value)
    }
  }
}
object XmlReader {
  private final val _xml_actions: scala.Array[scala.Byte] = XmlReader.init__xml_actions_0()
  private final val _xml_key_offsets: scala.Array[scala.Byte] = XmlReader.init__xml_key_offsets_0()
  private final val _xml_trans_keys: scala.Array[scala.Char] = XmlReader.init__xml_trans_keys_0()
  private final val _xml_single_lengths: scala.Array[scala.Byte] = XmlReader.init__xml_single_lengths_0()
  private final val _xml_range_lengths: scala.Array[scala.Byte] = XmlReader.init__xml_range_lengths_0()
  private final val _xml_index_offsets: scala.Array[scala.Short] = XmlReader.init__xml_index_offsets_0()
  private final val _xml_indicies: scala.Array[scala.Byte] = XmlReader.init__xml_indicies_0()
  private final val _xml_trans_targs: scala.Array[scala.Byte] = XmlReader.init__xml_trans_targs_0()
  private final val _xml_trans_actions: scala.Array[scala.Byte] = XmlReader.init__xml_trans_actions_0()
  final val xml_start: scala.Int = 1
  final val xml_first_final: scala.Int = 34
  final val xml_error: scala.Int = 0
  final val xml_en_elementBody: scala.Int = 15
  final val xml_en_main: scala.Int = 1
  private def init__xml_actions_0(): scala.Array[scala.Byte] = {
    return Array[scala.Byte](0, 1, 0, 1, 1, 1, 2, 1, 3, 1, 4, 1, 5, 2, 1, 4, 2, 2, 4, 2, 6, 7, 2, 6, 8, 3, 0, 6, 7)
  }
  private def init__xml_key_offsets_0(): scala.Array[scala.Byte] = {
    return Array[scala.Byte](0, 0, 4, 9, 14, 20, 26, 30, 35, 36, 37, 42, 46, 50, 51, 52, 56, 57, 62, 67, 73, 79, 83, 88, 89, 90, 95, 99, 103, 104, 108, 109, 110, 111, 112, 115)
  }
  private def init__xml_trans_keys_0(): scala.Array[scala.Char] = {
    return Array[scala.Char](32, 60, 9, 13, 32, 47, 62, 9, 13, 32, 47, 62, 9, 13, 32, 47, 61, 62, 9, 13, 32, 47, 61, 62, 9, 13, 32, 61, 9, 13, 32, 34, 39, 9, 13, 34, 34, 32, 47, 62, 9, 13, 32, 62, 9, 13, 32, 62, 9, 13, 39, 39, 32, 60, 9, 13, 60, 32, 47, 62, 9, 13, 32, 47, 62, 9, 13, 32, 47, 61, 62, 9, 13, 32, 47, 61, 62, 9, 13, 32, 61, 9, 13, 32, 34, 39, 9, 13, 34, 34, 32, 47, 62, 9, 13, 32, 62, 9, 13, 32, 62, 9, 13, 60, 32, 47, 9, 13, 62, 62, 39, 39, 32, 9, 13, 0)
  }
  private def init__xml_single_lengths_0(): scala.Array[scala.Byte] = {
    return Array[scala.Byte](0, 2, 3, 3, 4, 4, 2, 3, 1, 1, 3, 2, 2, 1, 1, 2, 1, 3, 3, 4, 4, 2, 3, 1, 1, 3, 2, 2, 1, 2, 1, 1, 1, 1, 1, 0)
  }
  private def init__xml_range_lengths_0(): scala.Array[scala.Byte] = {
    return Array[scala.Byte](0, 1, 1, 1, 1, 1, 1, 1, 0, 0, 1, 1, 1, 0, 0, 1, 0, 1, 1, 1, 1, 1, 1, 0, 0, 1, 1, 1, 0, 1, 0, 0, 0, 0, 1, 0)
  }
  private def init__xml_index_offsets_0(): scala.Array[scala.Short] = {
    return Array[scala.Short](0, 0, 4, 9, 14, 20, 26, 30, 35, 37, 39, 44, 48, 52, 54, 56, 60, 62, 67, 72, 78, 84, 88, 93, 95, 97, 102, 106, 110, 112, 116, 118, 120, 122, 124, 127)
  }
  private def init__xml_indicies_0(): scala.Array[scala.Byte] = {
    return Array[scala.Byte](0, 2, 0, 1, 2, 1, 1, 2, 3, 5, 6, 7, 5, 4, 9, 10, 1, 11, 9, 8, 13, 1, 14, 1, 13, 12, 15, 16, 15, 1, 16, 17, 18, 16, 1, 20, 19, 22, 21, 9, 10, 11, 9, 1, 23, 24, 23, 1, 25, 11, 25, 1, 20, 26, 22, 27, 29, 30, 29, 28, 32, 31, 30, 34, 1, 30, 33, 36, 37, 38, 36, 35, 40, 41, 1, 42, 40, 39, 44, 1, 45, 1, 44, 43, 46, 47, 46, 1, 47, 48, 49, 47, 1, 51, 50, 53, 52, 40, 41, 42, 40, 1, 54, 55, 54, 1, 56, 42, 56, 1, 57, 1, 57, 34, 57, 1, 1, 58, 59, 58, 51, 60, 53, 61, 62, 62, 1, 1, 0)
  }
  private def init__xml_trans_targs_0(): scala.Array[scala.Byte] = {
    return Array[scala.Byte](1, 0, 2, 3, 3, 4, 11, 34, 5, 4, 11, 34, 5, 6, 7, 6, 7, 8, 13, 9, 10, 9, 10, 12, 34, 12, 14, 14, 16, 15, 17, 16, 17, 18, 30, 18, 19, 26, 28, 20, 19, 26, 28, 20, 21, 22, 21, 22, 23, 32, 24, 25, 24, 25, 27, 28, 27, 29, 31, 35, 33, 33, 34)
  }
  private def init__xml_trans_actions_0(): scala.Array[scala.Byte] = {
    return Array[scala.Byte](0, 0, 0, 1, 0, 3, 3, 13, 1, 0, 0, 9, 0, 11, 11, 0, 0, 0, 0, 1, 25, 0, 19, 5, 16, 0, 1, 0, 1, 0, 0, 0, 22, 1, 0, 0, 3, 3, 13, 1, 0, 0, 9, 0, 11, 11, 0, 0, 0, 0, 1, 25, 0, 19, 5, 16, 0, 0, 0, 7, 1, 0, 0)
  }
}