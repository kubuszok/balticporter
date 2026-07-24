package com.badlogic.gdx.utils

class JsonSkimmer {
  var nameString: com.badlogic.gdx.utils.JsonSkimmer.JsonToken = null.asInstanceOf[com.badlogic.gdx.utils.JsonSkimmer.JsonToken]
  var value$field: com.badlogic.gdx.utils.JsonSkimmer.JsonToken = null.asInstanceOf[com.badlogic.gdx.utils.JsonSkimmer.JsonToken]
  var stack: scala.Array[scala.Int] = new Array[scala.Int](8)
  final val buffer: com.badlogic.gdx.utils.CharArray = new com.badlogic.gdx.utils.CharArray()
  var stop$field: scala.Boolean = false
  this.nameString = new com.badlogic.gdx.utils.JsonSkimmer.JsonToken(this.buffer)
  this.value$field = new com.badlogic.gdx.utils.JsonSkimmer.JsonToken(this.buffer)
  def parse(json: java.lang.String): scala.Unit = {
    val data: scala.Array[scala.Char] = json.toCharArray()
    this.parse(data, 0, data.length)
  }
  def parse(reader: java.io.Reader): scala.Unit = {
    var data: scala.Array[scala.Char] = new Array[scala.Char](1024)
    var offset: scala.Int = 0
    try {
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
    return Array[scala.Byte](0, 1, 0, 1, 1, 1, 2, 1, 3, 1, 4, 1, 6, 1, 7, 1, 9, 1, 10, 2, 8, 5, 2, 8, 7, 2, 10, 1, 2, 10, 3)
  }
  private def init__json_key_offsets_0(): scala.Array[scala.Short] = {
    return Array[scala.Short](0, 0, 11, 13, 15, 24, 30, 36, 38, 49, 56, 63, 72, 81, 83, 85, 94, 96, 98, 100, 102, 113, 120, 127, 138, 149, 151, 153, 164, 166, 168, 170, 175, 180, 180)
  }
  private def init__json_trans_keys_0(): scala.Array[scala.Char] = {
    return Array[scala.Char](13, 32, 34, 44, 47, 58, 91, 93, 123, 9, 10, 42, 47, 42, 47, 13, 32, 34, 44, 47, 58, 125, 9, 10, 13, 32, 47, 58, 9, 10, 13, 32, 47, 58, 9, 10, 42, 47, 13, 32, 34, 44, 47, 58, 91, 93, 123, 9, 10, 9, 10, 13, 32, 44, 47, 125, 9, 10, 13, 32, 44, 47, 125, 13, 32, 34, 44, 47, 58, 125, 9, 10, 13, 32, 34, 44, 47, 58, 125, 9, 10, 42, 47, 42, 47, 13, 32, 34, 44, 47, 58, 125, 9, 10, 42, 47, 42, 47, 42, 47, 42, 47, 13, 32, 34, 44, 47, 58, 91, 93, 123, 9, 10, 9, 10, 13, 32, 44, 47, 93, 9, 10, 13, 32, 44, 47, 93, 13, 32, 34, 44, 47, 58, 91, 93, 123, 9, 10, 13, 32, 34, 44, 47, 58, 91, 93, 123, 9, 10, 42, 47, 42, 47, 13, 32, 34, 44, 47, 58, 91, 93, 123, 9, 10, 42, 47, 42, 47, 42, 47, 13, 32, 47, 9, 10, 13, 32, 47, 9, 10, 0)
  }
  private def init__json_single_lengths_0(): scala.Array[scala.Byte] = {
    return Array[scala.Byte](0, 9, 2, 2, 7, 4, 4, 2, 9, 7, 7, 7, 7, 2, 2, 7, 2, 2, 2, 2, 9, 7, 7, 9, 9, 2, 2, 9, 2, 2, 2, 3, 3, 0, 0)
  }
  private def init__json_range_lengths_0(): scala.Array[scala.Byte] = {
    return Array[scala.Byte](0, 1, 0, 0, 1, 1, 1, 0, 1, 0, 0, 1, 1, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 1, 1, 0, 0, 1, 0, 0, 0, 1, 1, 0, 0)
  }
  private def init__json_index_offsets_0(): scala.Array[scala.Short] = {
    return Array[scala.Short](0, 0, 11, 14, 17, 26, 32, 38, 41, 52, 60, 68, 77, 86, 89, 92, 101, 104, 107, 110, 113, 124, 132, 140, 151, 162, 165, 168, 179, 182, 185, 188, 193, 198, 199)
  }
  private def init__json_indicies_0(): scala.Array[scala.Byte] = {
    return Array[scala.Byte](1, 1, 2, 3, 4, 3, 5, 3, 6, 1, 0, 7, 7, 3, 8, 8, 3, 10, 10, 11, 12, 13, 3, 14, 10, 9, 15, 15, 16, 17, 15, 3, 18, 18, 19, 20, 18, 3, 21, 21, 3, 20, 20, 23, 3, 24, 3, 25, 3, 26, 20, 22, 27, 28, 28, 27, 29, 30, 31, 3, 32, 33, 33, 32, 12, 34, 14, 3, 33, 33, 11, 35, 36, 3, 14, 33, 9, 35, 35, 11, 3, 37, 3, 3, 35, 9, 38, 38, 3, 39, 39, 3, 12, 12, 11, 3, 40, 3, 14, 12, 9, 41, 41, 3, 42, 42, 3, 43, 43, 3, 44, 44, 3, 46, 46, 47, 48, 49, 3, 50, 51, 52, 46, 45, 53, 54, 54, 53, 55, 56, 57, 3, 58, 59, 59, 58, 48, 60, 51, 3, 59, 59, 47, 61, 62, 3, 50, 51, 52, 59, 45, 61, 61, 47, 3, 63, 3, 50, 3, 52, 61, 45, 64, 64, 3, 65, 65, 3, 48, 48, 47, 3, 66, 3, 50, 51, 52, 48, 45, 67, 67, 3, 68, 68, 3, 69, 69, 3, 70, 70, 71, 70, 3, 72, 72, 73, 72, 3, 3, 3, 0)
  }
  private def init__json_trans_targs_0(): scala.Array[scala.Byte] = {
    return Array[scala.Byte](31, 1, 31, 0, 3, 32, 32, 32, 1, 5, 4, 5, 15, 19, 33, 6, 7, 8, 6, 7, 8, 6, 9, 9, 18, 10, 10, 10, 11, 15, 17, 33, 10, 11, 17, 12, 14, 13, 12, 11, 16, 15, 10, 8, 4, 21, 20, 21, 27, 30, 22, 34, 22, 22, 23, 27, 29, 34, 22, 23, 29, 24, 26, 25, 24, 23, 28, 27, 22, 20, 32, 2, 32, 2)
  }
  private def init__json_trans_actions_0(): scala.Array[scala.Byte] = {
    return Array[scala.Byte](11, 0, 13, 0, 0, 5, 1, 9, 9, 19, 0, 22, 0, 0, 3, 15, 15, 15, 0, 0, 0, 9, 11, 13, 0, 5, 1, 17, 17, 17, 17, 25, 0, 0, 0, 0, 0, 0, 9, 9, 0, 9, 9, 9, 9, 11, 0, 13, 0, 0, 5, 7, 1, 17, 17, 17, 17, 28, 0, 0, 0, 0, 0, 0, 9, 9, 0, 9, 9, 9, 17, 17, 0, 0)
  }
  private def init__json_eof_actions_0(): scala.Array[scala.Byte] = {
    return Array[scala.Byte](0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 17, 0, 0, 0)
  }
  class JsonToken {
    var buffer: com.badlogic.gdx.utils.CharArray = null.asInstanceOf[com.badlogic.gdx.utils.CharArray]
    var chars: scala.Array[scala.Char] = null.asInstanceOf[scala.Array[scala.Char]]
    var start: scala.Int = 0
    var length: scala.Int = 0
    var unescape$field: scala.Boolean = false
    var `type`: com.badlogic.gdx.utils.JsonSkimmer.JsonToken.TokenType = com.badlogic.gdx.utils.JsonSkimmer.JsonToken.TokenType.other
    def this(buffer: com.badlogic.gdx.utils.CharArray) = {
      this()
      this.buffer = buffer
    }
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
          this.buffer.append(c)
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
        this.buffer.append(c)
      };  } }
      return this.buffer.toString()
    }
  }
  object JsonToken {
    sealed abstract class TokenType
    object TokenType {
      case object nullValue extends TokenType
      case object trueValue extends TokenType
      case object falseValue extends TokenType
      case object other extends TokenType
      def values(): Array[TokenType] = Array(nullValue, trueValue, falseValue, other)
    }
  }
}