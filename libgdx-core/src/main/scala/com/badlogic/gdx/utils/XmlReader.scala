package com.badlogic.gdx.utils

class XmlReader {
  private final val elements: com.badlogic.gdx.utils.Array[com.badlogic.gdx.utils.XmlReader.Element] = new com.badlogic.gdx.utils.Array(8).asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.utils.XmlReader.Element]]
  private var root: com.badlogic.gdx.utils.XmlReader.Element = null.asInstanceOf[com.badlogic.gdx.utils.XmlReader.Element]
  private var current: com.badlogic.gdx.utils.XmlReader.Element = null.asInstanceOf[com.badlogic.gdx.utils.XmlReader.Element]
  private final val textBuffer: java.lang.StringBuilder = new java.lang.StringBuilder(64)
  private var entitiesText: java.lang.String = null.asInstanceOf[java.lang.String]
  def parse(xml: java.lang.String): com.badlogic.gdx.utils.XmlReader.Element = {
    val data: scala.Array[scala.Char] = xml.toCharArray()
    return this.parse(data, 0, data.length)
  }
  def parse(reader: java.io.Reader): com.badlogic.gdx.utils.XmlReader.Element = {
    try {
      var data: scala.Array[scala.Char] = new scala.Array[scala.Char](1024)
      var offset: scala.Int = 0
      while (true) {
        val length: scala.Int = reader.read(data, offset, data.length - offset)
        if (length == (-1)) {
          /* break */ ()
        } else ()
        if (length == 0) {
          val newData: scala.Array[scala.Char] = new scala.Array[scala.Char](data.length * 2)
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
  def parse(input: java.io.InputStream): com.badlogic.gdx.utils.XmlReader.Element = {
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
  def parse(file: com.badlogic.gdx.files.FileHandle): com.badlogic.gdx.utils.XmlReader.Element = {
    try {
      return this.parse(file.reader("UTF-8"))
    } catch {
      case ex: java.lang.Exception => {
        throw new com.badlogic.gdx.utils.SerializationException("Error parsing file: " + file, ex)
      }
    }
  }
  def parse(data: scala.Array[scala.Char], offset: scala.Int, length: scala.Int): com.badlogic.gdx.utils.XmlReader.Element = {
    var cs: scala.Int = 0
    var p: scala.Int = offset
    val pe: scala.Int = length
    var s: scala.Int = 0
    var attributeName: java.lang.String = null
    var hasBody: scala.Boolean = false;
    {
      cs = XmlReader.xml_start
    };
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
                        };
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
                      this.close();
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
                      this.close();
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
                        };
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
                      this.close();
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
                      this.close();
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
      var lineNumber: scala.Int = 1;
      { var i: scala.Int = 0; while (i < p) { {
        if (data(i) == '\n') {
          lineNumber = lineNumber + 1
        } else ()
      }; i = i + 1 } }
      throw new com.badlogic.gdx.utils.SerializationException((("Error parsing XML on line " + lineNumber) + " near: ") + new java.lang.String(data, p, java.lang.Math.min(32, pe - p)))
    } else {
      if (this.elements.size != 0) {
        val element: com.badlogic.gdx.utils.XmlReader.Element = this.elements.peek()
        this.elements.clear()
        throw new com.badlogic.gdx.utils.SerializationException("Error parsing XML, unclosed element: " + element.getName())
      } else ()
    }
    var root: com.badlogic.gdx.utils.XmlReader.Element = this.root
    this.root = null
    return root
  }
  def open(name: java.lang.String): scala.Unit = {
    val child: com.badlogic.gdx.utils.XmlReader.Element = new com.badlogic.gdx.utils.XmlReader.Element(name, this.current)
    val parent: com.badlogic.gdx.utils.XmlReader.Element = this.current
    if (parent != null) {
      parent.addChild(child)
    } else ()
    this.elements.add(child)
    this.current = child
  }
  def attribute(name: java.lang.String, value: java.lang.String): scala.Unit = {
    this.current.setAttribute(name, value)
  }
  @com.badlogic.gdx.utils.Null
  def entity(name: java.lang.String): java.lang.String = {
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
      return java.lang.Character.toString(java.lang.Integer.parseInt(name.substring(2), 16).asInstanceOf[scala.Char].asInstanceOf[scala.Char])
    } else ()
    return null
  }
  def text(text: java.lang.String): scala.Unit = {
    val existing: java.lang.String = this.current.getText()
    this.current.setText(if (existing != null) existing + text else text)
  }
  def close(): scala.Unit = {
    this.root = this.elements.pop()
    this.current = if (this.elements.size > 0) this.elements.peek() else null.asInstanceOf[com.badlogic.gdx.utils.XmlReader.Element]
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
    return scala.Array[scala.Byte](0.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 4.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 5.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 4.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 4.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 6.asInstanceOf[scala.Byte], 7.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 6.asInstanceOf[scala.Byte], 8.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 6.asInstanceOf[scala.Byte], 7.asInstanceOf[scala.Byte])
  }
  private def init__xml_key_offsets_0(): scala.Array[scala.Byte] = {
    return scala.Array[scala.Byte](0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 4.asInstanceOf[scala.Byte], 9.asInstanceOf[scala.Byte], 14.asInstanceOf[scala.Byte], 20.asInstanceOf[scala.Byte], 26.asInstanceOf[scala.Byte], 30.asInstanceOf[scala.Byte], 35.asInstanceOf[scala.Byte], 36.asInstanceOf[scala.Byte], 37.asInstanceOf[scala.Byte], 42.asInstanceOf[scala.Byte], 46.asInstanceOf[scala.Byte], 50.asInstanceOf[scala.Byte], 51.asInstanceOf[scala.Byte], 52.asInstanceOf[scala.Byte], 56.asInstanceOf[scala.Byte], 57.asInstanceOf[scala.Byte], 62.asInstanceOf[scala.Byte], 67.asInstanceOf[scala.Byte], 73.asInstanceOf[scala.Byte], 79.asInstanceOf[scala.Byte], 83.asInstanceOf[scala.Byte], 88.asInstanceOf[scala.Byte], 89.asInstanceOf[scala.Byte], 90.asInstanceOf[scala.Byte], 95.asInstanceOf[scala.Byte], 99.asInstanceOf[scala.Byte], 103.asInstanceOf[scala.Byte], 104.asInstanceOf[scala.Byte], 108.asInstanceOf[scala.Byte], 109.asInstanceOf[scala.Byte], 110.asInstanceOf[scala.Byte], 111.asInstanceOf[scala.Byte], 112.asInstanceOf[scala.Byte], 115.asInstanceOf[scala.Byte])
  }
  private def init__xml_trans_keys_0(): scala.Array[scala.Char] = {
    return scala.Array[scala.Char](32.asInstanceOf[scala.Char], 60.asInstanceOf[scala.Char], 9.asInstanceOf[scala.Char], 13.asInstanceOf[scala.Char], 32.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 62.asInstanceOf[scala.Char], 9.asInstanceOf[scala.Char], 13.asInstanceOf[scala.Char], 32.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 62.asInstanceOf[scala.Char], 9.asInstanceOf[scala.Char], 13.asInstanceOf[scala.Char], 32.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 61.asInstanceOf[scala.Char], 62.asInstanceOf[scala.Char], 9.asInstanceOf[scala.Char], 13.asInstanceOf[scala.Char], 32.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 61.asInstanceOf[scala.Char], 62.asInstanceOf[scala.Char], 9.asInstanceOf[scala.Char], 13.asInstanceOf[scala.Char], 32.asInstanceOf[scala.Char], 61.asInstanceOf[scala.Char], 9.asInstanceOf[scala.Char], 13.asInstanceOf[scala.Char], 32.asInstanceOf[scala.Char], 34.asInstanceOf[scala.Char], 39.asInstanceOf[scala.Char], 9.asInstanceOf[scala.Char], 13.asInstanceOf[scala.Char], 34.asInstanceOf[scala.Char], 34.asInstanceOf[scala.Char], 32.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 62.asInstanceOf[scala.Char], 9.asInstanceOf[scala.Char], 13.asInstanceOf[scala.Char], 32.asInstanceOf[scala.Char], 62.asInstanceOf[scala.Char], 9.asInstanceOf[scala.Char], 13.asInstanceOf[scala.Char], 32.asInstanceOf[scala.Char], 62.asInstanceOf[scala.Char], 9.asInstanceOf[scala.Char], 13.asInstanceOf[scala.Char], 39.asInstanceOf[scala.Char], 39.asInstanceOf[scala.Char], 32.asInstanceOf[scala.Char], 60.asInstanceOf[scala.Char], 9.asInstanceOf[scala.Char], 13.asInstanceOf[scala.Char], 60.asInstanceOf[scala.Char], 32.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 62.asInstanceOf[scala.Char], 9.asInstanceOf[scala.Char], 13.asInstanceOf[scala.Char], 32.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 62.asInstanceOf[scala.Char], 9.asInstanceOf[scala.Char], 13.asInstanceOf[scala.Char], 32.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 61.asInstanceOf[scala.Char], 62.asInstanceOf[scala.Char], 9.asInstanceOf[scala.Char], 13.asInstanceOf[scala.Char], 32.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 61.asInstanceOf[scala.Char], 62.asInstanceOf[scala.Char], 9.asInstanceOf[scala.Char], 13.asInstanceOf[scala.Char], 32.asInstanceOf[scala.Char], 61.asInstanceOf[scala.Char], 9.asInstanceOf[scala.Char], 13.asInstanceOf[scala.Char], 32.asInstanceOf[scala.Char], 34.asInstanceOf[scala.Char], 39.asInstanceOf[scala.Char], 9.asInstanceOf[scala.Char], 13.asInstanceOf[scala.Char], 34.asInstanceOf[scala.Char], 34.asInstanceOf[scala.Char], 32.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 62.asInstanceOf[scala.Char], 9.asInstanceOf[scala.Char], 13.asInstanceOf[scala.Char], 32.asInstanceOf[scala.Char], 62.asInstanceOf[scala.Char], 9.asInstanceOf[scala.Char], 13.asInstanceOf[scala.Char], 32.asInstanceOf[scala.Char], 62.asInstanceOf[scala.Char], 9.asInstanceOf[scala.Char], 13.asInstanceOf[scala.Char], 60.asInstanceOf[scala.Char], 32.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 9.asInstanceOf[scala.Char], 13.asInstanceOf[scala.Char], 62.asInstanceOf[scala.Char], 62.asInstanceOf[scala.Char], 39.asInstanceOf[scala.Char], 39.asInstanceOf[scala.Char], 32.asInstanceOf[scala.Char], 9.asInstanceOf[scala.Char], 13.asInstanceOf[scala.Char], 0.asInstanceOf[scala.Char])
  }
  private def init__xml_single_lengths_0(): scala.Array[scala.Byte] = {
    return scala.Array[scala.Byte](0.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 4.asInstanceOf[scala.Byte], 4.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 4.asInstanceOf[scala.Byte], 4.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte])
  }
  private def init__xml_range_lengths_0(): scala.Array[scala.Byte] = {
    return scala.Array[scala.Byte](0.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte])
  }
  private def init__xml_index_offsets_0(): scala.Array[scala.Short] = {
    return scala.Array[scala.Short](0.asInstanceOf[scala.Short], 0.asInstanceOf[scala.Short], 4.asInstanceOf[scala.Short], 9.asInstanceOf[scala.Short], 14.asInstanceOf[scala.Short], 20.asInstanceOf[scala.Short], 26.asInstanceOf[scala.Short], 30.asInstanceOf[scala.Short], 35.asInstanceOf[scala.Short], 37.asInstanceOf[scala.Short], 39.asInstanceOf[scala.Short], 44.asInstanceOf[scala.Short], 48.asInstanceOf[scala.Short], 52.asInstanceOf[scala.Short], 54.asInstanceOf[scala.Short], 56.asInstanceOf[scala.Short], 60.asInstanceOf[scala.Short], 62.asInstanceOf[scala.Short], 67.asInstanceOf[scala.Short], 72.asInstanceOf[scala.Short], 78.asInstanceOf[scala.Short], 84.asInstanceOf[scala.Short], 88.asInstanceOf[scala.Short], 93.asInstanceOf[scala.Short], 95.asInstanceOf[scala.Short], 97.asInstanceOf[scala.Short], 102.asInstanceOf[scala.Short], 106.asInstanceOf[scala.Short], 110.asInstanceOf[scala.Short], 112.asInstanceOf[scala.Short], 116.asInstanceOf[scala.Short], 118.asInstanceOf[scala.Short], 120.asInstanceOf[scala.Short], 122.asInstanceOf[scala.Short], 124.asInstanceOf[scala.Short], 127.asInstanceOf[scala.Short])
  }
  private def init__xml_indicies_0(): scala.Array[scala.Byte] = {
    return scala.Array[scala.Byte](0.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 5.asInstanceOf[scala.Byte], 6.asInstanceOf[scala.Byte], 7.asInstanceOf[scala.Byte], 5.asInstanceOf[scala.Byte], 4.asInstanceOf[scala.Byte], 9.asInstanceOf[scala.Byte], 10.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 11.asInstanceOf[scala.Byte], 9.asInstanceOf[scala.Byte], 8.asInstanceOf[scala.Byte], 13.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 14.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 13.asInstanceOf[scala.Byte], 12.asInstanceOf[scala.Byte], 15.asInstanceOf[scala.Byte], 16.asInstanceOf[scala.Byte], 15.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 16.asInstanceOf[scala.Byte], 17.asInstanceOf[scala.Byte], 18.asInstanceOf[scala.Byte], 16.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 20.asInstanceOf[scala.Byte], 19.asInstanceOf[scala.Byte], 22.asInstanceOf[scala.Byte], 21.asInstanceOf[scala.Byte], 9.asInstanceOf[scala.Byte], 10.asInstanceOf[scala.Byte], 11.asInstanceOf[scala.Byte], 9.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 23.asInstanceOf[scala.Byte], 24.asInstanceOf[scala.Byte], 23.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 25.asInstanceOf[scala.Byte], 11.asInstanceOf[scala.Byte], 25.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 20.asInstanceOf[scala.Byte], 26.asInstanceOf[scala.Byte], 22.asInstanceOf[scala.Byte], 27.asInstanceOf[scala.Byte], 29.asInstanceOf[scala.Byte], 30.asInstanceOf[scala.Byte], 29.asInstanceOf[scala.Byte], 28.asInstanceOf[scala.Byte], 32.asInstanceOf[scala.Byte], 31.asInstanceOf[scala.Byte], 30.asInstanceOf[scala.Byte], 34.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 30.asInstanceOf[scala.Byte], 33.asInstanceOf[scala.Byte], 36.asInstanceOf[scala.Byte], 37.asInstanceOf[scala.Byte], 38.asInstanceOf[scala.Byte], 36.asInstanceOf[scala.Byte], 35.asInstanceOf[scala.Byte], 40.asInstanceOf[scala.Byte], 41.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 42.asInstanceOf[scala.Byte], 40.asInstanceOf[scala.Byte], 39.asInstanceOf[scala.Byte], 44.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 45.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 44.asInstanceOf[scala.Byte], 43.asInstanceOf[scala.Byte], 46.asInstanceOf[scala.Byte], 47.asInstanceOf[scala.Byte], 46.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 47.asInstanceOf[scala.Byte], 48.asInstanceOf[scala.Byte], 49.asInstanceOf[scala.Byte], 47.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 51.asInstanceOf[scala.Byte], 50.asInstanceOf[scala.Byte], 53.asInstanceOf[scala.Byte], 52.asInstanceOf[scala.Byte], 40.asInstanceOf[scala.Byte], 41.asInstanceOf[scala.Byte], 42.asInstanceOf[scala.Byte], 40.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 54.asInstanceOf[scala.Byte], 55.asInstanceOf[scala.Byte], 54.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 56.asInstanceOf[scala.Byte], 42.asInstanceOf[scala.Byte], 56.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 57.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 57.asInstanceOf[scala.Byte], 34.asInstanceOf[scala.Byte], 57.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 58.asInstanceOf[scala.Byte], 59.asInstanceOf[scala.Byte], 58.asInstanceOf[scala.Byte], 51.asInstanceOf[scala.Byte], 60.asInstanceOf[scala.Byte], 53.asInstanceOf[scala.Byte], 61.asInstanceOf[scala.Byte], 62.asInstanceOf[scala.Byte], 62.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte])
  }
  private def init__xml_trans_targs_0(): scala.Array[scala.Byte] = {
    return scala.Array[scala.Byte](1.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 4.asInstanceOf[scala.Byte], 11.asInstanceOf[scala.Byte], 34.asInstanceOf[scala.Byte], 5.asInstanceOf[scala.Byte], 4.asInstanceOf[scala.Byte], 11.asInstanceOf[scala.Byte], 34.asInstanceOf[scala.Byte], 5.asInstanceOf[scala.Byte], 6.asInstanceOf[scala.Byte], 7.asInstanceOf[scala.Byte], 6.asInstanceOf[scala.Byte], 7.asInstanceOf[scala.Byte], 8.asInstanceOf[scala.Byte], 13.asInstanceOf[scala.Byte], 9.asInstanceOf[scala.Byte], 10.asInstanceOf[scala.Byte], 9.asInstanceOf[scala.Byte], 10.asInstanceOf[scala.Byte], 12.asInstanceOf[scala.Byte], 34.asInstanceOf[scala.Byte], 12.asInstanceOf[scala.Byte], 14.asInstanceOf[scala.Byte], 14.asInstanceOf[scala.Byte], 16.asInstanceOf[scala.Byte], 15.asInstanceOf[scala.Byte], 17.asInstanceOf[scala.Byte], 16.asInstanceOf[scala.Byte], 17.asInstanceOf[scala.Byte], 18.asInstanceOf[scala.Byte], 30.asInstanceOf[scala.Byte], 18.asInstanceOf[scala.Byte], 19.asInstanceOf[scala.Byte], 26.asInstanceOf[scala.Byte], 28.asInstanceOf[scala.Byte], 20.asInstanceOf[scala.Byte], 19.asInstanceOf[scala.Byte], 26.asInstanceOf[scala.Byte], 28.asInstanceOf[scala.Byte], 20.asInstanceOf[scala.Byte], 21.asInstanceOf[scala.Byte], 22.asInstanceOf[scala.Byte], 21.asInstanceOf[scala.Byte], 22.asInstanceOf[scala.Byte], 23.asInstanceOf[scala.Byte], 32.asInstanceOf[scala.Byte], 24.asInstanceOf[scala.Byte], 25.asInstanceOf[scala.Byte], 24.asInstanceOf[scala.Byte], 25.asInstanceOf[scala.Byte], 27.asInstanceOf[scala.Byte], 28.asInstanceOf[scala.Byte], 27.asInstanceOf[scala.Byte], 29.asInstanceOf[scala.Byte], 31.asInstanceOf[scala.Byte], 35.asInstanceOf[scala.Byte], 33.asInstanceOf[scala.Byte], 33.asInstanceOf[scala.Byte], 34.asInstanceOf[scala.Byte])
  }
  private def init__xml_trans_actions_0(): scala.Array[scala.Byte] = {
    return scala.Array[scala.Byte](0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 13.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 9.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 11.asInstanceOf[scala.Byte], 11.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 25.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 19.asInstanceOf[scala.Byte], 5.asInstanceOf[scala.Byte], 16.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 22.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 13.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 9.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 11.asInstanceOf[scala.Byte], 11.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 25.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 19.asInstanceOf[scala.Byte], 5.asInstanceOf[scala.Byte], 16.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 7.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte])
  }
  class Element(name$p: java.lang.String, parent$p: com.badlogic.gdx.utils.XmlReader.Element) {
    private var name: java.lang.String = null.asInstanceOf[java.lang.String]
    private var attributes: com.badlogic.gdx.utils.ObjectMap[java.lang.String, java.lang.String] = null.asInstanceOf[com.badlogic.gdx.utils.ObjectMap[java.lang.String, java.lang.String]]
    private var children: com.badlogic.gdx.utils.Array[com.badlogic.gdx.utils.XmlReader.Element] = null.asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.utils.XmlReader.Element]]
    private var text: java.lang.String = null.asInstanceOf[java.lang.String]
    private var parent: com.badlogic.gdx.utils.XmlReader.Element = null.asInstanceOf[com.badlogic.gdx.utils.XmlReader.Element]
    this.name = name$p
    this.parent = parent$p
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
        this.attributes = new com.badlogic.gdx.utils.ObjectMap(8).asInstanceOf[com.badlogic.gdx.utils.ObjectMap[java.lang.String, java.lang.String]]
      } else ()
      this.attributes.put(name, value)
    }
    def getChildCount(): scala.Int = {
      if (this.children == null) {
        return 0
      } else ()
      return this.children.size
    }
    def getChildren(): com.badlogic.gdx.utils.Array[com.badlogic.gdx.utils.XmlReader.Element] = {
      return this.children
    }
    def getChild(index: scala.Int): com.badlogic.gdx.utils.XmlReader.Element = {
      if (this.children == null) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("Element has no children: " + this.name)
      } else ()
      return this.children.get(index)
    }
    def addChild(element: com.badlogic.gdx.utils.XmlReader.Element): scala.Unit = {
      if (element == null) {
        throw new java.lang.IllegalArgumentException("element cannot be null.")
      } else ()
      if (this.children == null) {
        this.children = new com.badlogic.gdx.utils.Array(8).asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.utils.XmlReader.Element]]
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
        val removedChild: com.badlogic.gdx.utils.XmlReader.Element = this.children.removeIndex(index)
        if (removedChild != null) {
          removedChild.parent = null
        } else ()
      } else ()
    }
    def removeChild(child: com.badlogic.gdx.utils.XmlReader.Element): scala.Unit = {
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
    def replaceChild(child: com.badlogic.gdx.utils.XmlReader.Element, replacement: com.badlogic.gdx.utils.XmlReader.Element): scala.Unit = {
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
    def getParent(): com.badlogic.gdx.utils.XmlReader.Element = {
      return this.parent
    }
    override def toString(): java.lang.String = {
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
    @com.badlogic.gdx.utils.Null
    def getChildByName(name: java.lang.String): com.badlogic.gdx.utils.XmlReader.Element = {
      if (this.children == null) {
        return null
      } else ();
      { var i: scala.Int = 0; while (i < this.children.size) { {
        val element: com.badlogic.gdx.utils.XmlReader.Element = this.children.get(i)
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
    @com.badlogic.gdx.utils.Null
    def getChildByNameRecursive(name: java.lang.String): com.badlogic.gdx.utils.XmlReader.Element = {
      if (this.children == null) {
        return null
      } else ();
      { var i: scala.Int = 0; while (i < this.children.size) { {
        val element: com.badlogic.gdx.utils.XmlReader.Element = this.children.get(i)
        if (element.name.equals(name)) {
          return element
        } else ()
        val found: com.badlogic.gdx.utils.XmlReader.Element = element.getChildByNameRecursive(name)
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
    def getChildrenByName(name: java.lang.String): com.badlogic.gdx.utils.Array[com.badlogic.gdx.utils.XmlReader.Element] = {
      val result: com.badlogic.gdx.utils.Array[com.badlogic.gdx.utils.XmlReader.Element] = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.utils.XmlReader.Element]()
      if (this.children == null) {
        return result
      } else ();
      { var i: scala.Int = 0; while (i < this.children.size) { {
        val child: com.badlogic.gdx.utils.XmlReader.Element = this.children.get(i)
        if (child.name.equals(name)) {
          result.add(child)
        } else ()
      }; i = i + 1 } }
      return result
    }
    def getChildrenByNameRecursively(name: java.lang.String): com.badlogic.gdx.utils.Array[com.badlogic.gdx.utils.XmlReader.Element] = {
      val result: com.badlogic.gdx.utils.Array[com.badlogic.gdx.utils.XmlReader.Element] = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.utils.XmlReader.Element]()
      this.getChildrenByNameRecursively(name, result)
      return result
    }
    private def getChildrenByNameRecursively(name: java.lang.String, result: com.badlogic.gdx.utils.Array[com.badlogic.gdx.utils.XmlReader.Element]): scala.Unit = {
      if (this.children == null) {
        return
      } else ();
      { var i: scala.Int = 0; while (i < this.children.size) { {
        val child: com.badlogic.gdx.utils.XmlReader.Element = this.children.get(i)
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
      val child: com.badlogic.gdx.utils.XmlReader.Element = this.getChildByName(name)
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