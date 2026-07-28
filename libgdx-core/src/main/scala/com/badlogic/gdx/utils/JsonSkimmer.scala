package com.badlogic.gdx.utils

class JsonSkimmer {
  var nameString: com.badlogic.gdx.utils.JsonSkimmer.JsonToken = null.asInstanceOf[com.badlogic.gdx.utils.JsonSkimmer.JsonToken]
  var value$field: com.badlogic.gdx.utils.JsonSkimmer.JsonToken = null.asInstanceOf[com.badlogic.gdx.utils.JsonSkimmer.JsonToken]
  var stack: scala.Array[scala.Int] = new scala.Array[scala.Int](8)
  final val buffer: com.badlogic.gdx.utils.CharArray = new com.badlogic.gdx.utils.CharArray()
  var stop$field: scala.Boolean = false
  this.nameString = new com.badlogic.gdx.utils.JsonSkimmer.JsonToken(this.buffer)
  this.value$field = new com.badlogic.gdx.utils.JsonSkimmer.JsonToken(this.buffer)
  def parse(json: java.lang.String): scala.Unit = {
    val data: scala.Array[scala.Char] = json.toCharArray()
    this.parse(data, 0, data.length)
  }
  def parse(reader: java.io.Reader): scala.Unit = {
    var data: scala.Array[scala.Char] = new scala.Array[scala.Char](1024)
    var offset: scala.Int = 0
    try {
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
    } catch {
      case ex: java.io.IOException => {
        throw new com.badlogic.gdx.utils.SerializationException("Error reading input.", ex)
      }
    } finally {
      com.badlogic.gdx.utils.StreamUtils.closeQuietly(reader)
    }
    this.parse(data, 0, offset)
  }
  def parse(input: java.io.InputStream): scala.Unit = {
    var reader: java.io.Reader = null.asInstanceOf[java.io.Reader]
    try {
      reader = new java.io.InputStreamReader(input, "UTF-8")
    } catch {
      case ex: java.lang.Exception => {
        throw new com.badlogic.gdx.utils.SerializationException("Error reading stream.", ex)
      }
    }
    this.parse(reader)
  }
  def parse(file: com.badlogic.gdx.files.FileHandle): scala.Unit = {
    var reader: java.io.Reader = null.asInstanceOf[java.io.Reader]
    try {
      reader = file.reader("UTF-8")
    } catch {
      case ex: java.lang.Exception => {
        throw new com.badlogic.gdx.utils.SerializationException("Error reading file: " + file, ex)
      }
    }
    try {
      this.parse(reader)
    } catch {
      case ex: java.lang.Exception => {
        throw new com.badlogic.gdx.utils.SerializationException("Error parsing file: " + file, ex)
      }
    }
  }
  def parse(data: scala.Array[scala.Char], offset: scala.Int, length: scala.Int): scala.Unit = {
    this.stop$field = false
    var cs: scala.Int = 0
    var p: scala.Int = offset
    val pe: scala.Int = length
    val eof: scala.Int = pe
    var top: scala.Int = 0
    var stack: scala.Array[scala.Int] = this.stack
    val nameString: com.badlogic.gdx.utils.JsonSkimmer.JsonToken = this.nameString
    val value: com.badlogic.gdx.utils.JsonSkimmer.JsonToken = this.value$field
    var string: com.badlogic.gdx.utils.JsonSkimmer.JsonToken = value
    var name: com.badlogic.gdx.utils.JsonSkimmer.JsonToken = null
    nameString.chars = data
    value.chars = data
    var parseRuntimeEx: java.lang.RuntimeException = null
    val debug: scala.Boolean = false
    if (debug) {
      java.lang.System.out.println()
    } else ()
    try {
      {
        cs = JsonSkimmer.json_start
        top = 0
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
                _keys = JsonSkimmer._json_key_offsets(cs)
                _trans = JsonSkimmer._json_index_offsets(cs)
                _klen = JsonSkimmer._json_single_lengths(cs)
                if (_klen > 0) {
                  var _lower: scala.Int = _keys
                  var _mid: scala.Int = 0
                  var _upper: scala.Int = (_keys + _klen) - 1
                  while (true) {
                    if (_upper < _lower) {
                      /* break */ ()
                    } else ()
                    _mid = _lower + ((_upper - _lower) >> 1)
                    if (data(p) < JsonSkimmer._json_trans_keys(_mid)) {
                      _upper = _mid - 1
                    } else {
                      if (data(p) > JsonSkimmer._json_trans_keys(_mid)) {
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
                _klen = JsonSkimmer._json_range_lengths(cs)
                if (_klen > 0) {
                  var _lower: scala.Int = _keys
                  var _mid: scala.Int = 0
                  var _upper: scala.Int = (_keys + (_klen << 1)) - 2
                  while (true) {
                    if (_upper < _lower) {
                      /* break */ ()
                    } else ()
                    _mid = _lower + (((_upper - _lower) >> 1) & (~1))
                    if (data(p) < JsonSkimmer._json_trans_keys(_mid)) {
                      _upper = _mid - 2
                    } else {
                      if (data(p) > JsonSkimmer._json_trans_keys(_mid + 1)) {
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
              _trans = JsonSkimmer._json_indicies(_trans)
              cs = JsonSkimmer._json_trans_targs(_trans)
              if (JsonSkimmer._json_trans_actions(_trans) != 0) {
                _acts = JsonSkimmer._json_trans_actions(_trans)
                _nacts = JsonSkimmer._json_actions({ _acts += 1; _acts }).asInstanceOf[scala.Int]
                while ({ _nacts -= 1; _nacts } > 0) {
                  JsonSkimmer._json_actions({ _acts += 1; _acts }) match {
                    case 0 => {
                      {
                        if (debug) {
                          java.lang.System.out.println("startObject: " + name)
                        } else ()
                        this.push(name, true)
                        if (this.stop$field) {
                          return
                        } else ()
                        name = null;
                        {
                          if (top == stack.length) {
                            stack = {
                              this.stack = java.util.Arrays.copyOf(stack, stack.length << 1)
                              this.stack
                            }
                          } else ();
                          {
                            stack({ top += 1; top }) = cs
                            cs = 4
                            _goto_targ = 2
                            if (true) {
                              /* continue */ ()
                            } else ()
                          }
                        }
                      }
                    }
                    case 1 => {
                      {
                        if (debug) {
                          java.lang.System.out.println("endObject")
                        } else ()
                        this.pop()
                        if (this.stop$field) {
                          return
                        } else ();
                        {
                          cs = stack({ top -= 1; top })
                          _goto_targ = 2
                          if (true) {
                            /* continue */ ()
                          } else ()
                        }
                      }
                    }
                    case 2 => {
                      {
                        if (debug) {
                          java.lang.System.out.println("startArray: " + name)
                        } else ()
                        this.push(name, false)
                        if (this.stop$field) {
                          return
                        } else ()
                        name = null;
                        {
                          if (top == stack.length) {
                            stack = {
                              this.stack = java.util.Arrays.copyOf(stack, stack.length << 1)
                              this.stack
                            }
                          } else ();
                          {
                            stack({ top += 1; top }) = cs
                            cs = 20
                            _goto_targ = 2
                            if (true) {
                              /* continue */ ()
                            } else ()
                          }
                        }
                      }
                    }
                    case 3 => {
                      {
                        if (debug) {
                          java.lang.System.out.println("endArray")
                        } else ()
                        this.pop()
                        if (this.stop$field) {
                          return
                        } else ();
                        {
                          cs = stack({ top -= 1; top })
                          _goto_targ = 2
                          if (true) {
                            /* continue */ ()
                          } else ()
                        }
                      }
                    }
                    case 4 => {
                      {
                        var start: scala.Int = p
                        if (data({ p += 1; p }) == '/') {
                          while ((p != eof) && (data(p) != '\n')) {
                            p = p + 1
                          }
                          p = p - 1
                        } else {
                          while (((p + 1) < eof) && ((data(p) != '*') || (data(p + 1) != '/'))) {
                            p = p + 1
                          }
                          p = p + 1
                        }
                        if (debug) {
                          java.lang.System.out.println("comment " + new java.lang.String(data, start - 1, (p - start) + 2))
                        } else ()
                      }
                    }
                    case 5 => {
                      {
                        if (debug) {
                          java.lang.System.out.println("unquotedName")
                        } else ()
                        var start: scala.Int = p
                        string.start = start
                        var ws: scala.Boolean = false
                        while (true) {
                          data(p) match {
                            case '\\' => {
                              string.unescape$field = true
                            }
                            case '/' => {
                              if ((p + 1) == eof) {
                                /* break */ ()
                              } else ()
                              val c: scala.Char = data(p + 1)
                              if ((c == '/') || (c == '*')) {
                                /* break */ ()
                              } else ()
                            }
                            case ' ' | '\t' => {
                              ws = true
                            }
                            case ':' | '\r' | '\n' => {
                              ()
                            }
                          }
                          if (debug) {
                            java.lang.System.out.println(("name char: '" + data(p)) + "'")
                          } else ()
                          p = p + 1
                          if (p == eof) {
                            /* break */ ()
                          } else ()
                        }
                        p = p - 1
                        if (ws) {
                          while (true) {
                            data(p) match {
                              case ' ' | '\t' => {
                                p = p - 1
                                /* continue */ ()
                              }
                            }
                            /* break */ ()
                          }
                        } else ()
                        string.length = (p - start) + 1
                      }
                    }
                    case 6 => {
                      {
                        if (debug) {
                          java.lang.System.out.println("unquotedValue")
                        } else ()
                        var start: scala.Int = p
                        string.start = start
                        var ws: scala.Boolean = false
                        while (true) {
                          data(p) match {
                            case '\\' => {
                              string.unescape$field = true
                            }
                            case '/' => {
                              if ((p + 1) == eof) {
                                /* break */ ()
                              } else ()
                              val c: scala.Char = data(p + 1)
                              if ((c == '/') || (c == '*')) {
                                /* break */ ()
                              } else ()
                            }
                            case ' ' | '\t' => {
                              ws = true
                            }
                            case '\r' | '\n' | '}' | ']' | ',' => {
                              ()
                            }
                          }
                          if (debug) {
                            java.lang.System.out.println(("value char: '" + data(p)) + "'")
                          } else ()
                          p = p + 1
                          if (p == eof) {
                            /* break */ ()
                          } else ()
                        }
                        p = p - 1
                        if (ws) {
                          while (true) {
                            data(p) match {
                              case ' ' | '\t' => {
                                p = p - 1
                                /* continue */ ()
                              }
                            }
                            /* break */ ()
                          }
                        } else ()
                        string.length = (p - start) + 1
                        string.`type` = com.badlogic.gdx.utils.JsonSkimmer.JsonToken.TokenType.other
                        if (string.length == 4) {
                          if ((((data(start) == 't') && (data(start + 1) == 'r')) && (data(start + 2) == 'u')) && (data(start + 3) == 'e')) {
                            string.`type` = com.badlogic.gdx.utils.JsonSkimmer.JsonToken.TokenType.trueValue
                          } else {
                            if ((((data(start) == 'n') && (data(start + 1) == 'u')) && (data(start + 2) == 'l')) && (data(start + 3) == 'l')) {
                              string.`type` = com.badlogic.gdx.utils.JsonSkimmer.JsonToken.TokenType.nullValue
                            } else ()
                          }
                        } else {
                          if (string.length == 5) {
                            if (((((data(start) == 'f') && (data(start + 1) == 'a')) && (data(start + 2) == 'l')) && (data(start + 3) == 's')) && (data(start + 4) == 'e')) {
                              string.`type` = com.badlogic.gdx.utils.JsonSkimmer.JsonToken.TokenType.falseValue
                            } else ()
                          } else ()
                        }
                      }
                    }
                    case 7 => {
                      {
                        if (debug) {
                          java.lang.System.out.println("quotedString")
                        } else ()
                        string.start = { p += 1; p }
                        while (true) {
                          data(p) match {
                            case '\\' => {
                              string.unescape$field = true
                              p = p + 1
                            }
                            case '\"' => {
                              ()
                            }
                          }
                          if (debug) {
                            java.lang.System.out.println(("quoted char: '" + data(p)) + "'")
                          } else ()
                          p = p + 1
                          if (p == eof) {
                            /* break */ ()
                          } else ()
                        }
                        string.length = p - string.start
                      }
                    }
                    case 8 => {
                      {
                        name = nameString
                        string = nameString
                        if (debug) {
                          java.lang.System.out.println("name start " + p)
                        } else ()
                      }
                    }
                    case 9 => {
                      {
                        if (debug) {
                          java.lang.System.out.println((("name: " + p) + ", ") + name)
                        } else ()
                        nameString.unescape$field = false
                        string = value
                      }
                    }
                    case 10 => {
                      {
                        if (debug) {
                          java.lang.System.out.println((("value: " + name) + "=") + value)
                        } else ()
                        this.value(name, value)
                        if (this.stop$field) {
                          return
                        } else ()
                        value.unescape$field = false
                        value.`type` = com.badlogic.gdx.utils.JsonSkimmer.JsonToken.TokenType.other
                        name = null
                        string = value
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
              if (p == eof) {
                var __acts: scala.Int = JsonSkimmer._json_eof_actions(cs)
                var __nacts: scala.Int = JsonSkimmer._json_actions({ __acts += 1; __acts }).asInstanceOf[scala.Int]
                while ({ __nacts -= 1; __nacts } > 0) {
                  JsonSkimmer._json_actions({ __acts += 1; __acts }) match {
                    case 10 => {
                      {
                        if (debug) {
                          java.lang.System.out.println((("value: " + name) + "=") + value)
                        } else ()
                        this.value(name, value)
                        if (this.stop$field) {
                          return
                        } else ()
                        value.unescape$field = false
                        value.`type` = com.badlogic.gdx.utils.JsonSkimmer.JsonToken.TokenType.other
                        name = null
                        string = value
                      }
                    }
                  }
                }
              } else ()
            }
            case 1 => {
              while ({ {
                _keys = JsonSkimmer._json_key_offsets(cs)
                _trans = JsonSkimmer._json_index_offsets(cs)
                _klen = JsonSkimmer._json_single_lengths(cs)
                if (_klen > 0) {
                  var _lower: scala.Int = _keys
                  var _mid: scala.Int = 0
                  var _upper: scala.Int = (_keys + _klen) - 1
                  while (true) {
                    if (_upper < _lower) {
                      /* break */ ()
                    } else ()
                    _mid = _lower + ((_upper - _lower) >> 1)
                    if (data(p) < JsonSkimmer._json_trans_keys(_mid)) {
                      _upper = _mid - 1
                    } else {
                      if (data(p) > JsonSkimmer._json_trans_keys(_mid)) {
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
                _klen = JsonSkimmer._json_range_lengths(cs)
                if (_klen > 0) {
                  var _lower: scala.Int = _keys
                  var _mid: scala.Int = 0
                  var _upper: scala.Int = (_keys + (_klen << 1)) - 2
                  while (true) {
                    if (_upper < _lower) {
                      /* break */ ()
                    } else ()
                    _mid = _lower + (((_upper - _lower) >> 1) & (~1))
                    if (data(p) < JsonSkimmer._json_trans_keys(_mid)) {
                      _upper = _mid - 2
                    } else {
                      if (data(p) > JsonSkimmer._json_trans_keys(_mid + 1)) {
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
              _trans = JsonSkimmer._json_indicies(_trans)
              cs = JsonSkimmer._json_trans_targs(_trans)
              if (JsonSkimmer._json_trans_actions(_trans) != 0) {
                _acts = JsonSkimmer._json_trans_actions(_trans)
                _nacts = JsonSkimmer._json_actions({ _acts += 1; _acts }).asInstanceOf[scala.Int]
                while ({ _nacts -= 1; _nacts } > 0) {
                  JsonSkimmer._json_actions({ _acts += 1; _acts }) match {
                    case 0 => {
                      {
                        if (debug) {
                          java.lang.System.out.println("startObject: " + name)
                        } else ()
                        this.push(name, true)
                        if (this.stop$field) {
                          return
                        } else ()
                        name = null;
                        {
                          if (top == stack.length) {
                            stack = {
                              this.stack = java.util.Arrays.copyOf(stack, stack.length << 1)
                              this.stack
                            }
                          } else ();
                          {
                            stack({ top += 1; top }) = cs
                            cs = 4
                            _goto_targ = 2
                            if (true) {
                              /* continue */ ()
                            } else ()
                          }
                        }
                      }
                    }
                    case 1 => {
                      {
                        if (debug) {
                          java.lang.System.out.println("endObject")
                        } else ()
                        this.pop()
                        if (this.stop$field) {
                          return
                        } else ();
                        {
                          cs = stack({ top -= 1; top })
                          _goto_targ = 2
                          if (true) {
                            /* continue */ ()
                          } else ()
                        }
                      }
                    }
                    case 2 => {
                      {
                        if (debug) {
                          java.lang.System.out.println("startArray: " + name)
                        } else ()
                        this.push(name, false)
                        if (this.stop$field) {
                          return
                        } else ()
                        name = null;
                        {
                          if (top == stack.length) {
                            stack = {
                              this.stack = java.util.Arrays.copyOf(stack, stack.length << 1)
                              this.stack
                            }
                          } else ();
                          {
                            stack({ top += 1; top }) = cs
                            cs = 20
                            _goto_targ = 2
                            if (true) {
                              /* continue */ ()
                            } else ()
                          }
                        }
                      }
                    }
                    case 3 => {
                      {
                        if (debug) {
                          java.lang.System.out.println("endArray")
                        } else ()
                        this.pop()
                        if (this.stop$field) {
                          return
                        } else ();
                        {
                          cs = stack({ top -= 1; top })
                          _goto_targ = 2
                          if (true) {
                            /* continue */ ()
                          } else ()
                        }
                      }
                    }
                    case 4 => {
                      {
                        var start: scala.Int = p
                        if (data({ p += 1; p }) == '/') {
                          while ((p != eof) && (data(p) != '\n')) {
                            p = p + 1
                          }
                          p = p - 1
                        } else {
                          while (((p + 1) < eof) && ((data(p) != '*') || (data(p + 1) != '/'))) {
                            p = p + 1
                          }
                          p = p + 1
                        }
                        if (debug) {
                          java.lang.System.out.println("comment " + new java.lang.String(data, start - 1, (p - start) + 2))
                        } else ()
                      }
                    }
                    case 5 => {
                      {
                        if (debug) {
                          java.lang.System.out.println("unquotedName")
                        } else ()
                        var start: scala.Int = p
                        string.start = start
                        var ws: scala.Boolean = false
                        while (true) {
                          data(p) match {
                            case '\\' => {
                              string.unescape$field = true
                            }
                            case '/' => {
                              if ((p + 1) == eof) {
                                /* break */ ()
                              } else ()
                              val c: scala.Char = data(p + 1)
                              if ((c == '/') || (c == '*')) {
                                /* break */ ()
                              } else ()
                            }
                            case ' ' | '\t' => {
                              ws = true
                            }
                            case ':' | '\r' | '\n' => {
                              ()
                            }
                          }
                          if (debug) {
                            java.lang.System.out.println(("name char: '" + data(p)) + "'")
                          } else ()
                          p = p + 1
                          if (p == eof) {
                            /* break */ ()
                          } else ()
                        }
                        p = p - 1
                        if (ws) {
                          while (true) {
                            data(p) match {
                              case ' ' | '\t' => {
                                p = p - 1
                                /* continue */ ()
                              }
                            }
                            /* break */ ()
                          }
                        } else ()
                        string.length = (p - start) + 1
                      }
                    }
                    case 6 => {
                      {
                        if (debug) {
                          java.lang.System.out.println("unquotedValue")
                        } else ()
                        var start: scala.Int = p
                        string.start = start
                        var ws: scala.Boolean = false
                        while (true) {
                          data(p) match {
                            case '\\' => {
                              string.unescape$field = true
                            }
                            case '/' => {
                              if ((p + 1) == eof) {
                                /* break */ ()
                              } else ()
                              val c: scala.Char = data(p + 1)
                              if ((c == '/') || (c == '*')) {
                                /* break */ ()
                              } else ()
                            }
                            case ' ' | '\t' => {
                              ws = true
                            }
                            case '\r' | '\n' | '}' | ']' | ',' => {
                              ()
                            }
                          }
                          if (debug) {
                            java.lang.System.out.println(("value char: '" + data(p)) + "'")
                          } else ()
                          p = p + 1
                          if (p == eof) {
                            /* break */ ()
                          } else ()
                        }
                        p = p - 1
                        if (ws) {
                          while (true) {
                            data(p) match {
                              case ' ' | '\t' => {
                                p = p - 1
                                /* continue */ ()
                              }
                            }
                            /* break */ ()
                          }
                        } else ()
                        string.length = (p - start) + 1
                        string.`type` = com.badlogic.gdx.utils.JsonSkimmer.JsonToken.TokenType.other
                        if (string.length == 4) {
                          if ((((data(start) == 't') && (data(start + 1) == 'r')) && (data(start + 2) == 'u')) && (data(start + 3) == 'e')) {
                            string.`type` = com.badlogic.gdx.utils.JsonSkimmer.JsonToken.TokenType.trueValue
                          } else {
                            if ((((data(start) == 'n') && (data(start + 1) == 'u')) && (data(start + 2) == 'l')) && (data(start + 3) == 'l')) {
                              string.`type` = com.badlogic.gdx.utils.JsonSkimmer.JsonToken.TokenType.nullValue
                            } else ()
                          }
                        } else {
                          if (string.length == 5) {
                            if (((((data(start) == 'f') && (data(start + 1) == 'a')) && (data(start + 2) == 'l')) && (data(start + 3) == 's')) && (data(start + 4) == 'e')) {
                              string.`type` = com.badlogic.gdx.utils.JsonSkimmer.JsonToken.TokenType.falseValue
                            } else ()
                          } else ()
                        }
                      }
                    }
                    case 7 => {
                      {
                        if (debug) {
                          java.lang.System.out.println("quotedString")
                        } else ()
                        string.start = { p += 1; p }
                        while (true) {
                          data(p) match {
                            case '\\' => {
                              string.unescape$field = true
                              p = p + 1
                            }
                            case '\"' => {
                              ()
                            }
                          }
                          if (debug) {
                            java.lang.System.out.println(("quoted char: '" + data(p)) + "'")
                          } else ()
                          p = p + 1
                          if (p == eof) {
                            /* break */ ()
                          } else ()
                        }
                        string.length = p - string.start
                      }
                    }
                    case 8 => {
                      {
                        name = nameString
                        string = nameString
                        if (debug) {
                          java.lang.System.out.println("name start " + p)
                        } else ()
                      }
                    }
                    case 9 => {
                      {
                        if (debug) {
                          java.lang.System.out.println((("name: " + p) + ", ") + name)
                        } else ()
                        nameString.unescape$field = false
                        string = value
                      }
                    }
                    case 10 => {
                      {
                        if (debug) {
                          java.lang.System.out.println((("value: " + name) + "=") + value)
                        } else ()
                        this.value(name, value)
                        if (this.stop$field) {
                          return
                        } else ()
                        value.unescape$field = false
                        value.`type` = com.badlogic.gdx.utils.JsonSkimmer.JsonToken.TokenType.other
                        name = null
                        string = value
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
              if (p == eof) {
                var __acts: scala.Int = JsonSkimmer._json_eof_actions(cs)
                var __nacts: scala.Int = JsonSkimmer._json_actions({ __acts += 1; __acts }).asInstanceOf[scala.Int]
                while ({ __nacts -= 1; __nacts } > 0) {
                  JsonSkimmer._json_actions({ __acts += 1; __acts }) match {
                    case 10 => {
                      {
                        if (debug) {
                          java.lang.System.out.println((("value: " + name) + "=") + value)
                        } else ()
                        this.value(name, value)
                        if (this.stop$field) {
                          return
                        } else ()
                        value.unescape$field = false
                        value.`type` = com.badlogic.gdx.utils.JsonSkimmer.JsonToken.TokenType.other
                        name = null
                        string = value
                      }
                    }
                  }
                }
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
              if (p == eof) {
                var __acts: scala.Int = JsonSkimmer._json_eof_actions(cs)
                var __nacts: scala.Int = JsonSkimmer._json_actions({ __acts += 1; __acts }).asInstanceOf[scala.Int]
                while ({ __nacts -= 1; __nacts } > 0) {
                  JsonSkimmer._json_actions({ __acts += 1; __acts }) match {
                    case 10 => {
                      {
                        if (debug) {
                          java.lang.System.out.println((("value: " + name) + "=") + value)
                        } else ()
                        this.value(name, value)
                        if (this.stop$field) {
                          return
                        } else ()
                        value.unescape$field = false
                        value.`type` = com.badlogic.gdx.utils.JsonSkimmer.JsonToken.TokenType.other
                        name = null
                        string = value
                      }
                    }
                  }
                }
              } else ()
            }
            case 4 => {
              if (p == eof) {
                var __acts: scala.Int = JsonSkimmer._json_eof_actions(cs)
                var __nacts: scala.Int = JsonSkimmer._json_actions({ __acts += 1; __acts }).asInstanceOf[scala.Int]
                while ({ __nacts -= 1; __nacts } > 0) {
                  JsonSkimmer._json_actions({ __acts += 1; __acts }) match {
                    case 10 => {
                      {
                        if (debug) {
                          java.lang.System.out.println((("value: " + name) + "=") + value)
                        } else ()
                        this.value(name, value)
                        if (this.stop$field) {
                          return
                        } else ()
                        value.unescape$field = false
                        value.`type` = com.badlogic.gdx.utils.JsonSkimmer.JsonToken.TokenType.other
                        name = null
                        string = value
                      }
                    }
                  }
                }
              } else ()
            }
            case 5 => {
              ()
            }
          }
          /* break */ ()
        }
      }
    } catch {
      case ex: java.lang.RuntimeException => {
        parseRuntimeEx = ex
      }
    }
    if (p < pe) {
      var lineNumber: scala.Int = 1;
      { var i: scala.Int = 0; while (i < p) { {
        if (data(i) == '\n') {
          lineNumber = lineNumber + 1
        } else ()
      }; i = i + 1 } }
      var start: scala.Int = java.lang.Math.max(0, p - 32)
      throw new com.badlogic.gdx.utils.SerializationException((((("Error parsing JSON on line " + lineNumber) + " near: ") + new java.lang.String(data, start, p - start)) + "*ERROR*") + new java.lang.String(data, p, java.lang.Math.min(64, pe - p)), parseRuntimeEx)
    } else ()
    if (parseRuntimeEx != null) {
      throw new com.badlogic.gdx.utils.SerializationException("Error parsing JSON: " + new java.lang.String(data), parseRuntimeEx)
    } else ()
  }
  def stop(): scala.Unit = {
    this.stop$field = true
  }
  def isStopped(): scala.Boolean = {
    return this.stop$field
  }
  def push(name: com.badlogic.gdx.utils.JsonSkimmer.JsonToken, `object`: scala.Boolean): scala.Unit = {
    ()
  }
  def pop(): scala.Unit = {
    ()
  }
  def value(name: com.badlogic.gdx.utils.JsonSkimmer.JsonToken, value: com.badlogic.gdx.utils.JsonSkimmer.JsonToken): scala.Unit = {
    ()
  }
}
object JsonSkimmer {
  private final val _json_actions: scala.Array[scala.Byte] = JsonSkimmer.init__json_actions_0()
  private final val _json_key_offsets: scala.Array[scala.Short] = JsonSkimmer.init__json_key_offsets_0()
  private final val _json_trans_keys: scala.Array[scala.Char] = JsonSkimmer.init__json_trans_keys_0()
  private final val _json_single_lengths: scala.Array[scala.Byte] = JsonSkimmer.init__json_single_lengths_0()
  private final val _json_range_lengths: scala.Array[scala.Byte] = JsonSkimmer.init__json_range_lengths_0()
  private final val _json_index_offsets: scala.Array[scala.Short] = JsonSkimmer.init__json_index_offsets_0()
  private final val _json_indicies: scala.Array[scala.Byte] = JsonSkimmer.init__json_indicies_0()
  private final val _json_trans_targs: scala.Array[scala.Byte] = JsonSkimmer.init__json_trans_targs_0()
  private final val _json_trans_actions: scala.Array[scala.Byte] = JsonSkimmer.init__json_trans_actions_0()
  private final val _json_eof_actions: scala.Array[scala.Byte] = JsonSkimmer.init__json_eof_actions_0()
  final val json_start: scala.Int = 1
  final val json_first_final: scala.Int = 31
  final val json_error: scala.Int = 0
  final val json_en_object: scala.Int = 4
  final val json_en_array: scala.Int = 20
  final val json_en_main: scala.Int = 1
  private def init__json_actions_0(): scala.Array[scala.Byte] = {
    return scala.Array[scala.Byte](0.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 4.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 6.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 7.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 9.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 10.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 8.asInstanceOf[scala.Byte], 5.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 8.asInstanceOf[scala.Byte], 7.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 10.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 10.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte])
  }
  private def init__json_key_offsets_0(): scala.Array[scala.Short] = {
    return scala.Array[scala.Short](0.asInstanceOf[scala.Short], 0.asInstanceOf[scala.Short], 11.asInstanceOf[scala.Short], 13.asInstanceOf[scala.Short], 15.asInstanceOf[scala.Short], 24.asInstanceOf[scala.Short], 30.asInstanceOf[scala.Short], 36.asInstanceOf[scala.Short], 38.asInstanceOf[scala.Short], 49.asInstanceOf[scala.Short], 56.asInstanceOf[scala.Short], 63.asInstanceOf[scala.Short], 72.asInstanceOf[scala.Short], 81.asInstanceOf[scala.Short], 83.asInstanceOf[scala.Short], 85.asInstanceOf[scala.Short], 94.asInstanceOf[scala.Short], 96.asInstanceOf[scala.Short], 98.asInstanceOf[scala.Short], 100.asInstanceOf[scala.Short], 102.asInstanceOf[scala.Short], 113.asInstanceOf[scala.Short], 120.asInstanceOf[scala.Short], 127.asInstanceOf[scala.Short], 138.asInstanceOf[scala.Short], 149.asInstanceOf[scala.Short], 151.asInstanceOf[scala.Short], 153.asInstanceOf[scala.Short], 164.asInstanceOf[scala.Short], 166.asInstanceOf[scala.Short], 168.asInstanceOf[scala.Short], 170.asInstanceOf[scala.Short], 175.asInstanceOf[scala.Short], 180.asInstanceOf[scala.Short], 180.asInstanceOf[scala.Short])
  }
  private def init__json_trans_keys_0(): scala.Array[scala.Char] = {
    return scala.Array[scala.Char](13.asInstanceOf[scala.Char], 32.asInstanceOf[scala.Char], 34.asInstanceOf[scala.Char], 44.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 58.asInstanceOf[scala.Char], 91.asInstanceOf[scala.Char], 93.asInstanceOf[scala.Char], 123.asInstanceOf[scala.Char], 9.asInstanceOf[scala.Char], 10.asInstanceOf[scala.Char], 42.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 42.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 13.asInstanceOf[scala.Char], 32.asInstanceOf[scala.Char], 34.asInstanceOf[scala.Char], 44.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 58.asInstanceOf[scala.Char], 125.asInstanceOf[scala.Char], 9.asInstanceOf[scala.Char], 10.asInstanceOf[scala.Char], 13.asInstanceOf[scala.Char], 32.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 58.asInstanceOf[scala.Char], 9.asInstanceOf[scala.Char], 10.asInstanceOf[scala.Char], 13.asInstanceOf[scala.Char], 32.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 58.asInstanceOf[scala.Char], 9.asInstanceOf[scala.Char], 10.asInstanceOf[scala.Char], 42.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 13.asInstanceOf[scala.Char], 32.asInstanceOf[scala.Char], 34.asInstanceOf[scala.Char], 44.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 58.asInstanceOf[scala.Char], 91.asInstanceOf[scala.Char], 93.asInstanceOf[scala.Char], 123.asInstanceOf[scala.Char], 9.asInstanceOf[scala.Char], 10.asInstanceOf[scala.Char], 9.asInstanceOf[scala.Char], 10.asInstanceOf[scala.Char], 13.asInstanceOf[scala.Char], 32.asInstanceOf[scala.Char], 44.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 125.asInstanceOf[scala.Char], 9.asInstanceOf[scala.Char], 10.asInstanceOf[scala.Char], 13.asInstanceOf[scala.Char], 32.asInstanceOf[scala.Char], 44.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 125.asInstanceOf[scala.Char], 13.asInstanceOf[scala.Char], 32.asInstanceOf[scala.Char], 34.asInstanceOf[scala.Char], 44.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 58.asInstanceOf[scala.Char], 125.asInstanceOf[scala.Char], 9.asInstanceOf[scala.Char], 10.asInstanceOf[scala.Char], 13.asInstanceOf[scala.Char], 32.asInstanceOf[scala.Char], 34.asInstanceOf[scala.Char], 44.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 58.asInstanceOf[scala.Char], 125.asInstanceOf[scala.Char], 9.asInstanceOf[scala.Char], 10.asInstanceOf[scala.Char], 42.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 42.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 13.asInstanceOf[scala.Char], 32.asInstanceOf[scala.Char], 34.asInstanceOf[scala.Char], 44.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 58.asInstanceOf[scala.Char], 125.asInstanceOf[scala.Char], 9.asInstanceOf[scala.Char], 10.asInstanceOf[scala.Char], 42.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 42.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 42.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 42.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 13.asInstanceOf[scala.Char], 32.asInstanceOf[scala.Char], 34.asInstanceOf[scala.Char], 44.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 58.asInstanceOf[scala.Char], 91.asInstanceOf[scala.Char], 93.asInstanceOf[scala.Char], 123.asInstanceOf[scala.Char], 9.asInstanceOf[scala.Char], 10.asInstanceOf[scala.Char], 9.asInstanceOf[scala.Char], 10.asInstanceOf[scala.Char], 13.asInstanceOf[scala.Char], 32.asInstanceOf[scala.Char], 44.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 93.asInstanceOf[scala.Char], 9.asInstanceOf[scala.Char], 10.asInstanceOf[scala.Char], 13.asInstanceOf[scala.Char], 32.asInstanceOf[scala.Char], 44.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 93.asInstanceOf[scala.Char], 13.asInstanceOf[scala.Char], 32.asInstanceOf[scala.Char], 34.asInstanceOf[scala.Char], 44.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 58.asInstanceOf[scala.Char], 91.asInstanceOf[scala.Char], 93.asInstanceOf[scala.Char], 123.asInstanceOf[scala.Char], 9.asInstanceOf[scala.Char], 10.asInstanceOf[scala.Char], 13.asInstanceOf[scala.Char], 32.asInstanceOf[scala.Char], 34.asInstanceOf[scala.Char], 44.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 58.asInstanceOf[scala.Char], 91.asInstanceOf[scala.Char], 93.asInstanceOf[scala.Char], 123.asInstanceOf[scala.Char], 9.asInstanceOf[scala.Char], 10.asInstanceOf[scala.Char], 42.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 42.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 13.asInstanceOf[scala.Char], 32.asInstanceOf[scala.Char], 34.asInstanceOf[scala.Char], 44.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 58.asInstanceOf[scala.Char], 91.asInstanceOf[scala.Char], 93.asInstanceOf[scala.Char], 123.asInstanceOf[scala.Char], 9.asInstanceOf[scala.Char], 10.asInstanceOf[scala.Char], 42.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 42.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 42.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 13.asInstanceOf[scala.Char], 32.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 9.asInstanceOf[scala.Char], 10.asInstanceOf[scala.Char], 13.asInstanceOf[scala.Char], 32.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 9.asInstanceOf[scala.Char], 10.asInstanceOf[scala.Char], 0.asInstanceOf[scala.Char])
  }
  private def init__json_single_lengths_0(): scala.Array[scala.Byte] = {
    return scala.Array[scala.Byte](0.asInstanceOf[scala.Byte], 9.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 7.asInstanceOf[scala.Byte], 4.asInstanceOf[scala.Byte], 4.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 9.asInstanceOf[scala.Byte], 7.asInstanceOf[scala.Byte], 7.asInstanceOf[scala.Byte], 7.asInstanceOf[scala.Byte], 7.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 7.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 9.asInstanceOf[scala.Byte], 7.asInstanceOf[scala.Byte], 7.asInstanceOf[scala.Byte], 9.asInstanceOf[scala.Byte], 9.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 9.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte])
  }
  private def init__json_range_lengths_0(): scala.Array[scala.Byte] = {
    return scala.Array[scala.Byte](0.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte])
  }
  private def init__json_index_offsets_0(): scala.Array[scala.Short] = {
    return scala.Array[scala.Short](0.asInstanceOf[scala.Short], 0.asInstanceOf[scala.Short], 11.asInstanceOf[scala.Short], 14.asInstanceOf[scala.Short], 17.asInstanceOf[scala.Short], 26.asInstanceOf[scala.Short], 32.asInstanceOf[scala.Short], 38.asInstanceOf[scala.Short], 41.asInstanceOf[scala.Short], 52.asInstanceOf[scala.Short], 60.asInstanceOf[scala.Short], 68.asInstanceOf[scala.Short], 77.asInstanceOf[scala.Short], 86.asInstanceOf[scala.Short], 89.asInstanceOf[scala.Short], 92.asInstanceOf[scala.Short], 101.asInstanceOf[scala.Short], 104.asInstanceOf[scala.Short], 107.asInstanceOf[scala.Short], 110.asInstanceOf[scala.Short], 113.asInstanceOf[scala.Short], 124.asInstanceOf[scala.Short], 132.asInstanceOf[scala.Short], 140.asInstanceOf[scala.Short], 151.asInstanceOf[scala.Short], 162.asInstanceOf[scala.Short], 165.asInstanceOf[scala.Short], 168.asInstanceOf[scala.Short], 179.asInstanceOf[scala.Short], 182.asInstanceOf[scala.Short], 185.asInstanceOf[scala.Short], 188.asInstanceOf[scala.Short], 193.asInstanceOf[scala.Short], 198.asInstanceOf[scala.Short], 199.asInstanceOf[scala.Short])
  }
  private def init__json_indicies_0(): scala.Array[scala.Byte] = {
    return scala.Array[scala.Byte](1.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 4.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 5.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 6.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 7.asInstanceOf[scala.Byte], 7.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 8.asInstanceOf[scala.Byte], 8.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 10.asInstanceOf[scala.Byte], 10.asInstanceOf[scala.Byte], 11.asInstanceOf[scala.Byte], 12.asInstanceOf[scala.Byte], 13.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 14.asInstanceOf[scala.Byte], 10.asInstanceOf[scala.Byte], 9.asInstanceOf[scala.Byte], 15.asInstanceOf[scala.Byte], 15.asInstanceOf[scala.Byte], 16.asInstanceOf[scala.Byte], 17.asInstanceOf[scala.Byte], 15.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 18.asInstanceOf[scala.Byte], 18.asInstanceOf[scala.Byte], 19.asInstanceOf[scala.Byte], 20.asInstanceOf[scala.Byte], 18.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 21.asInstanceOf[scala.Byte], 21.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 20.asInstanceOf[scala.Byte], 20.asInstanceOf[scala.Byte], 23.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 24.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 25.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 26.asInstanceOf[scala.Byte], 20.asInstanceOf[scala.Byte], 22.asInstanceOf[scala.Byte], 27.asInstanceOf[scala.Byte], 28.asInstanceOf[scala.Byte], 28.asInstanceOf[scala.Byte], 27.asInstanceOf[scala.Byte], 29.asInstanceOf[scala.Byte], 30.asInstanceOf[scala.Byte], 31.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 32.asInstanceOf[scala.Byte], 33.asInstanceOf[scala.Byte], 33.asInstanceOf[scala.Byte], 32.asInstanceOf[scala.Byte], 12.asInstanceOf[scala.Byte], 34.asInstanceOf[scala.Byte], 14.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 33.asInstanceOf[scala.Byte], 33.asInstanceOf[scala.Byte], 11.asInstanceOf[scala.Byte], 35.asInstanceOf[scala.Byte], 36.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 14.asInstanceOf[scala.Byte], 33.asInstanceOf[scala.Byte], 9.asInstanceOf[scala.Byte], 35.asInstanceOf[scala.Byte], 35.asInstanceOf[scala.Byte], 11.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 37.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 35.asInstanceOf[scala.Byte], 9.asInstanceOf[scala.Byte], 38.asInstanceOf[scala.Byte], 38.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 39.asInstanceOf[scala.Byte], 39.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 12.asInstanceOf[scala.Byte], 12.asInstanceOf[scala.Byte], 11.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 40.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 14.asInstanceOf[scala.Byte], 12.asInstanceOf[scala.Byte], 9.asInstanceOf[scala.Byte], 41.asInstanceOf[scala.Byte], 41.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 42.asInstanceOf[scala.Byte], 42.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 43.asInstanceOf[scala.Byte], 43.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 44.asInstanceOf[scala.Byte], 44.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 46.asInstanceOf[scala.Byte], 46.asInstanceOf[scala.Byte], 47.asInstanceOf[scala.Byte], 48.asInstanceOf[scala.Byte], 49.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 50.asInstanceOf[scala.Byte], 51.asInstanceOf[scala.Byte], 52.asInstanceOf[scala.Byte], 46.asInstanceOf[scala.Byte], 45.asInstanceOf[scala.Byte], 53.asInstanceOf[scala.Byte], 54.asInstanceOf[scala.Byte], 54.asInstanceOf[scala.Byte], 53.asInstanceOf[scala.Byte], 55.asInstanceOf[scala.Byte], 56.asInstanceOf[scala.Byte], 57.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 58.asInstanceOf[scala.Byte], 59.asInstanceOf[scala.Byte], 59.asInstanceOf[scala.Byte], 58.asInstanceOf[scala.Byte], 48.asInstanceOf[scala.Byte], 60.asInstanceOf[scala.Byte], 51.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 59.asInstanceOf[scala.Byte], 59.asInstanceOf[scala.Byte], 47.asInstanceOf[scala.Byte], 61.asInstanceOf[scala.Byte], 62.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 50.asInstanceOf[scala.Byte], 51.asInstanceOf[scala.Byte], 52.asInstanceOf[scala.Byte], 59.asInstanceOf[scala.Byte], 45.asInstanceOf[scala.Byte], 61.asInstanceOf[scala.Byte], 61.asInstanceOf[scala.Byte], 47.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 63.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 50.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 52.asInstanceOf[scala.Byte], 61.asInstanceOf[scala.Byte], 45.asInstanceOf[scala.Byte], 64.asInstanceOf[scala.Byte], 64.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 65.asInstanceOf[scala.Byte], 65.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 48.asInstanceOf[scala.Byte], 48.asInstanceOf[scala.Byte], 47.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 66.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 50.asInstanceOf[scala.Byte], 51.asInstanceOf[scala.Byte], 52.asInstanceOf[scala.Byte], 48.asInstanceOf[scala.Byte], 45.asInstanceOf[scala.Byte], 67.asInstanceOf[scala.Byte], 67.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 68.asInstanceOf[scala.Byte], 68.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 69.asInstanceOf[scala.Byte], 69.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 70.asInstanceOf[scala.Byte], 70.asInstanceOf[scala.Byte], 71.asInstanceOf[scala.Byte], 70.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 72.asInstanceOf[scala.Byte], 72.asInstanceOf[scala.Byte], 73.asInstanceOf[scala.Byte], 72.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte])
  }
  private def init__json_trans_targs_0(): scala.Array[scala.Byte] = {
    return scala.Array[scala.Byte](31.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 31.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 32.asInstanceOf[scala.Byte], 32.asInstanceOf[scala.Byte], 32.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 5.asInstanceOf[scala.Byte], 4.asInstanceOf[scala.Byte], 5.asInstanceOf[scala.Byte], 15.asInstanceOf[scala.Byte], 19.asInstanceOf[scala.Byte], 33.asInstanceOf[scala.Byte], 6.asInstanceOf[scala.Byte], 7.asInstanceOf[scala.Byte], 8.asInstanceOf[scala.Byte], 6.asInstanceOf[scala.Byte], 7.asInstanceOf[scala.Byte], 8.asInstanceOf[scala.Byte], 6.asInstanceOf[scala.Byte], 9.asInstanceOf[scala.Byte], 9.asInstanceOf[scala.Byte], 18.asInstanceOf[scala.Byte], 10.asInstanceOf[scala.Byte], 10.asInstanceOf[scala.Byte], 10.asInstanceOf[scala.Byte], 11.asInstanceOf[scala.Byte], 15.asInstanceOf[scala.Byte], 17.asInstanceOf[scala.Byte], 33.asInstanceOf[scala.Byte], 10.asInstanceOf[scala.Byte], 11.asInstanceOf[scala.Byte], 17.asInstanceOf[scala.Byte], 12.asInstanceOf[scala.Byte], 14.asInstanceOf[scala.Byte], 13.asInstanceOf[scala.Byte], 12.asInstanceOf[scala.Byte], 11.asInstanceOf[scala.Byte], 16.asInstanceOf[scala.Byte], 15.asInstanceOf[scala.Byte], 10.asInstanceOf[scala.Byte], 8.asInstanceOf[scala.Byte], 4.asInstanceOf[scala.Byte], 21.asInstanceOf[scala.Byte], 20.asInstanceOf[scala.Byte], 21.asInstanceOf[scala.Byte], 27.asInstanceOf[scala.Byte], 30.asInstanceOf[scala.Byte], 22.asInstanceOf[scala.Byte], 34.asInstanceOf[scala.Byte], 22.asInstanceOf[scala.Byte], 22.asInstanceOf[scala.Byte], 23.asInstanceOf[scala.Byte], 27.asInstanceOf[scala.Byte], 29.asInstanceOf[scala.Byte], 34.asInstanceOf[scala.Byte], 22.asInstanceOf[scala.Byte], 23.asInstanceOf[scala.Byte], 29.asInstanceOf[scala.Byte], 24.asInstanceOf[scala.Byte], 26.asInstanceOf[scala.Byte], 25.asInstanceOf[scala.Byte], 24.asInstanceOf[scala.Byte], 23.asInstanceOf[scala.Byte], 28.asInstanceOf[scala.Byte], 27.asInstanceOf[scala.Byte], 22.asInstanceOf[scala.Byte], 20.asInstanceOf[scala.Byte], 32.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 32.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte])
  }
  private def init__json_trans_actions_0(): scala.Array[scala.Byte] = {
    return scala.Array[scala.Byte](11.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 13.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 5.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 9.asInstanceOf[scala.Byte], 9.asInstanceOf[scala.Byte], 19.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 22.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 15.asInstanceOf[scala.Byte], 15.asInstanceOf[scala.Byte], 15.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 9.asInstanceOf[scala.Byte], 11.asInstanceOf[scala.Byte], 13.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 5.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 17.asInstanceOf[scala.Byte], 17.asInstanceOf[scala.Byte], 17.asInstanceOf[scala.Byte], 17.asInstanceOf[scala.Byte], 25.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 9.asInstanceOf[scala.Byte], 9.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 9.asInstanceOf[scala.Byte], 9.asInstanceOf[scala.Byte], 9.asInstanceOf[scala.Byte], 9.asInstanceOf[scala.Byte], 11.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 13.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 5.asInstanceOf[scala.Byte], 7.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 17.asInstanceOf[scala.Byte], 17.asInstanceOf[scala.Byte], 17.asInstanceOf[scala.Byte], 17.asInstanceOf[scala.Byte], 28.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 9.asInstanceOf[scala.Byte], 9.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 9.asInstanceOf[scala.Byte], 9.asInstanceOf[scala.Byte], 9.asInstanceOf[scala.Byte], 17.asInstanceOf[scala.Byte], 17.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte])
  }
  private def init__json_eof_actions_0(): scala.Array[scala.Byte] = {
    return scala.Array[scala.Byte](0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 17.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte])
  }
  class JsonToken(buffer$p: com.badlogic.gdx.utils.CharArray) {
    var buffer: com.badlogic.gdx.utils.CharArray = null.asInstanceOf[com.badlogic.gdx.utils.CharArray]
    var chars: scala.Array[scala.Char] = null.asInstanceOf[scala.Array[scala.Char]]
    var start: scala.Int = 0
    var length: scala.Int = 0
    var unescape$field: scala.Boolean = false
    var `type`: com.badlogic.gdx.utils.JsonSkimmer.JsonToken.TokenType = com.badlogic.gdx.utils.JsonSkimmer.JsonToken.TokenType.other
    this.buffer = buffer$p
    def equalsString(string: java.lang.String): scala.Boolean = {
      if (string == null) {
        return false
      } else ()
      if (this.unescape$field) {
        return this.toString().equals(string)
      } else ()
      val n: scala.Int = this.length
      if (string.length() != n) {
        return false
      } else ()
      val chars: scala.Array[scala.Char] = this.chars;
      { var c: scala.Int = this.start; var s: scala.Int = 0; while (s < n) { {
        if (chars(c) != string.charAt(s)) {
          return false
        } else ()
      }; c = c + 1; s = s + 1 } }
      return true
    }
    def toString(): java.lang.String = {
      if (this.`type` == com.badlogic.gdx.utils.JsonSkimmer.JsonToken.TokenType.nullValue) {
        return "null"
      } else ()
      return if (this.unescape$field) this.unescape() else new java.lang.String(this.chars, this.start, this.length)
    }
    def value(): com.badlogic.gdx.utils.JsonValue = {
      this.`type` match {
        case com.badlogic.gdx.utils.JsonSkimmer.JsonToken.TokenType.nullValue => {
          return new com.badlogic.gdx.utils.JsonValue(com.badlogic.gdx.utils.JsonValue.ValueType.nullValue)
        }
        case com.badlogic.gdx.utils.JsonSkimmer.JsonToken.TokenType.trueValue => {
          return new com.badlogic.gdx.utils.JsonValue(true)
        }
        case com.badlogic.gdx.utils.JsonSkimmer.JsonToken.TokenType.falseValue => {
          return new com.badlogic.gdx.utils.JsonValue(false)
        }
        case _ => {
          return new com.badlogic.gdx.utils.JsonValue(this.toString())
        }
      }
    }
    private def equals(string: java.lang.String): scala.Boolean = {
      val n: scala.Int = this.length
      val chars: scala.Array[scala.Char] = this.chars;
      { var c: scala.Int = this.start; var s: scala.Int = 0; while (s < n) { {
        if (chars(c) != string.charAt(s)) {
          return false
        } else ()
      }; c = c + 1; s = s + 1 } }
      return true
    }
    private def unescape(): java.lang.String = {
      val chars: scala.Array[scala.Char] = this.chars
      this.buffer.size = 0
      this.buffer.ensureCapacity(this.length + 16);
      { var i: scala.Int = this.start; val n: scala.Int = i + this.length; while (i < n) { {
        var c: scala.Char = chars({ i += 1; i })
        if (c != '\\') {
          (this.buffer.append: (scala.Char) => com.badlogic.gdx.utils.CharArray)(c)
          /* continue */ ()
        } else ()
        if (i == n) {
          throw new com.badlogic.gdx.utils.SerializationException("Illegal escape sequence: \\")
        } else ()
        c = chars({ i += 1; i })
        c match {
          case 'u' => {
            if ((i + 4) > n) {
              throw new com.badlogic.gdx.utils.SerializationException("Illegal escape sequence: \\u")
            } else ()
            this.buffer.size = this.buffer.size + java.lang.Character.toChars((((java.lang.Character.digit(chars({ i += 1; i }), 16) << 12) | (java.lang.Character.digit(chars({ i += 1; i }), 16) << 8)) | (java.lang.Character.digit(chars({ i += 1; i }), 16) << 4)) | java.lang.Character.digit(chars({ i += 1; i }), 16), this.buffer.items, this.buffer.size)
            /* continue */ ()
          }
          case '\"' | '\\' | '/' => {
            ()
          }
          case 'b' => {
            c = ''
          }
          case 'f' => {
            c = ''
          }
          case 'n' => {
            c = '\n'
          }
          case 'r' => {
            c = '\r'
          }
          case 't' => {
            c = '\t'
          }
          case _ => {
            throw new com.badlogic.gdx.utils.SerializationException("Illegal escaped character: \\" + c)
          }
        }
        (this.buffer.append: (scala.Char) => com.badlogic.gdx.utils.CharArray)(c)
      };  } }
      return this.buffer.toString()
    }
  }
  object JsonToken {
    sealed abstract class TokenType {
      def name(): java.lang.String = this.toString()
    }
    object TokenType {
      case object nullValue extends TokenType
      case object trueValue extends TokenType
      case object falseValue extends TokenType
      case object other extends TokenType
      def values(): scala.Array[TokenType] = scala.Array(nullValue, trueValue, falseValue, other)
      def valueOf(name: java.lang.String): TokenType = name match {
        case "nullValue" => nullValue
        case "trueValue" => trueValue
        case "falseValue" => falseValue
        case "other" => other
        case _ => throw new java.lang.IllegalArgumentException(name)
      }
    }
  }
}