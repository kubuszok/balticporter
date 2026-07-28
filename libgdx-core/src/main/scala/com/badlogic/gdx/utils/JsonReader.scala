package com.badlogic.gdx.utils

class JsonReader extends com.badlogic.gdx.utils.BaseJsonReader {
  private final val elements: com.badlogic.gdx.utils.Array[com.badlogic.gdx.utils.JsonValue] = new com.badlogic.gdx.utils.Array(8).asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.utils.JsonValue]]
  private var root: com.badlogic.gdx.utils.JsonValue = null.asInstanceOf[com.badlogic.gdx.utils.JsonValue]
  private var current: com.badlogic.gdx.utils.JsonValue = null.asInstanceOf[com.badlogic.gdx.utils.JsonValue]
  var stop$field: scala.Boolean = false
  override def parse(json: java.lang.String): com.badlogic.gdx.utils.JsonValue = {
    val data: scala.Array[scala.Char] = json.toCharArray()
    return this.parse(data, 0, data.length)
  }
  override def parse(reader: java.io.Reader): com.badlogic.gdx.utils.JsonValue = {
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
    return this.parse(data, 0, offset)
  }
  override def parse(input: java.io.InputStream): com.badlogic.gdx.utils.JsonValue = {
    var reader: java.io.Reader = null.asInstanceOf[java.io.Reader]
    try {
      reader = new java.io.InputStreamReader(input, "UTF-8")
    } catch {
      case ex: java.lang.Exception => {
        throw new com.badlogic.gdx.utils.SerializationException("Error reading stream.", ex)
      }
    }
    return this.parse(reader)
  }
  override def parse(file: com.badlogic.gdx.files.FileHandle): com.badlogic.gdx.utils.JsonValue = {
    var reader: java.io.Reader = null.asInstanceOf[java.io.Reader]
    try {
      reader = file.reader("UTF-8")
    } catch {
      case ex: java.lang.Exception => {
        throw new com.badlogic.gdx.utils.SerializationException("Error reading file: " + file, ex)
      }
    }
    try {
      return this.parse(reader)
    } catch {
      case ex: java.lang.Exception => {
        throw new com.badlogic.gdx.utils.SerializationException("Error parsing file: " + file, ex)
      }
    }
  }
  def parse(data: scala.Array[scala.Char], offset: scala.Int, length: scala.Int): com.badlogic.gdx.utils.JsonValue = {
    this.stop$field = false
    var cs: scala.Int = 0
    var p: scala.Int = offset
    val pe: scala.Int = length
    val eof: scala.Int = pe
    var top: scala.Int = 0
    var stack: scala.Array[scala.Int] = new scala.Array[scala.Int](4)
    var s: scala.Int = 0
    var name: java.lang.String = null
    var needsUnescape: scala.Boolean = false
    var stringIsName: scala.Boolean = false
    var stringIsUnquoted: scala.Boolean = false
    var parseRuntimeEx: java.lang.RuntimeException = null
    val debug: scala.Boolean = false
    if (debug) {
      java.lang.System.out.println()
    } else ()
    try {
      {
        cs = JsonReader.json_start
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
                _keys = JsonReader._json_key_offsets(cs)
                _trans = JsonReader._json_index_offsets(cs)
                _klen = JsonReader._json_single_lengths(cs)
                if (_klen > 0) {
                  var _lower: scala.Int = _keys
                  var _mid: scala.Int = 0
                  var _upper: scala.Int = (_keys + _klen) - 1
                  while (true) {
                    if (_upper < _lower) {
                      /* break */ ()
                    } else ()
                    _mid = _lower + ((_upper - _lower) >> 1)
                    if (data(p) < JsonReader._json_trans_keys(_mid)) {
                      _upper = _mid - 1
                    } else {
                      if (data(p) > JsonReader._json_trans_keys(_mid)) {
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
                _klen = JsonReader._json_range_lengths(cs)
                if (_klen > 0) {
                  var _lower: scala.Int = _keys
                  var _mid: scala.Int = 0
                  var _upper: scala.Int = (_keys + (_klen << 1)) - 2
                  while (true) {
                    if (_upper < _lower) {
                      /* break */ ()
                    } else ()
                    _mid = _lower + (((_upper - _lower) >> 1) & (~1))
                    if (data(p) < JsonReader._json_trans_keys(_mid)) {
                      _upper = _mid - 2
                    } else {
                      if (data(p) > JsonReader._json_trans_keys(_mid + 1)) {
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
              _trans = JsonReader._json_indicies(_trans)
              cs = JsonReader._json_trans_targs(_trans)
              if (JsonReader._json_trans_actions(_trans) != 0) {
                _acts = JsonReader._json_trans_actions(_trans)
                _nacts = JsonReader._json_actions({ _acts += 1; _acts }).asInstanceOf[scala.Int]
                while ({ _nacts -= 1; _nacts } > 0) {
                  JsonReader._json_actions({ _acts += 1; _acts }) match {
                    case 0 => {
                      {
                        stringIsName = true
                      }
                    }
                    case 1 => {
                      {
                        var value: java.lang.String = new java.lang.String(data, s, p - s)
                        if (needsUnescape) {
                          value = this.unescape(value)
                        } else ()
                        if (stringIsName) {
                          stringIsName = false
                          if (debug) {
                            java.lang.System.out.println("name: " + value)
                          } else ()
                          name = value
                        } else {
                          val valueName: java.lang.String = name
                          name = null
                          if (stringIsUnquoted) {
                            if (value.equals("true")) {
                              if (debug) {
                                java.lang.System.out.println(("boolean: " + valueName) + "=true")
                              } else ()
                              this.bool(valueName, true)
                              /* break */ ()
                            } else {
                              if (value.equals("false")) {
                                if (debug) {
                                  java.lang.System.out.println(("boolean: " + valueName) + "=false")
                                } else ()
                                this.bool(valueName, false)
                                /* break */ ()
                              } else {
                                if (value.equals("null")) {
                                  this.string(valueName, null)
                                  /* break */ ()
                                } else ()
                              }
                            }
                            var couldBeDouble: scala.Boolean = false
                            var couldBeLong: scala.Boolean = true;
                            { var i: scala.Int = s; while (i < p) { {
                              data(i) match {
                                case '0' | '1' | '2' | '3' | '4' | '5' | '6' | '7' | '8' | '9' | '-' | '+' => {
                                  ()
                                }
                                case '.' | 'e' | 'E' => {
                                  couldBeDouble = true
                                  couldBeLong = false
                                }
                                case _ => {
                                  couldBeDouble = false
                                  couldBeLong = false
                                }
                              }
                            }; i = i + 1 } }
                            if (couldBeDouble) {
                              try {
                                if (debug) {
                                  java.lang.System.out.println((("double: " + valueName) + "=") + java.lang.Double.parseDouble(value))
                                } else ()
                                this.number(valueName, java.lang.Double.parseDouble(value), value)
                                /* break */ ()
                              } catch {
                                case ignored: java.lang.NumberFormatException => {
                                  ()
                                }
                              }
                            } else {
                              if (couldBeLong) {
                                if (debug) {
                                  java.lang.System.out.println((("double: " + valueName) + "=") + java.lang.Double.parseDouble(value))
                                } else ()
                                try {
                                  this.number(valueName, java.lang.Long.parseLong(value), value)
                                  /* break */ ()
                                } catch {
                                  case ignored: java.lang.NumberFormatException => {
                                    ()
                                  }
                                }
                              } else ()
                            }
                          } else ()
                          if (debug) {
                            java.lang.System.out.println((("string: " + valueName) + "=") + value)
                          } else ()
                          this.string(valueName, value)
                        }
                        if (this.stop$field) {
                          /* break */ ()
                        } else ()
                        stringIsUnquoted = false
                        s = p
                      }
                    }
                    case 2 => {
                      {
                        if (debug) {
                          java.lang.System.out.println("startObject: " + name)
                        } else ()
                        this.startObject(name)
                        if (this.stop$field) {
                          /* break */ ()
                        } else ()
                        name = null;
                        {
                          if (top == stack.length) {
                            stack = java.util.Arrays.copyOf(stack, stack.length * 2)
                          } else ();
                          {
                            stack({ top += 1; top }) = cs
                            cs = 5
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
                          java.lang.System.out.println("endObject")
                        } else ()
                        this.pop()
                        if (this.stop$field) {
                          /* break */ ()
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
                        if (debug) {
                          java.lang.System.out.println("startArray: " + name)
                        } else ()
                        this.startArray(name)
                        if (this.stop$field) {
                          /* break */ ()
                        } else ()
                        name = null;
                        {
                          if (top == stack.length) {
                            stack = java.util.Arrays.copyOf(stack, stack.length * 2)
                          } else ();
                          {
                            stack({ top += 1; top }) = cs
                            cs = 23
                            _goto_targ = 2
                            if (true) {
                              /* continue */ ()
                            } else ()
                          }
                        }
                      }
                    }
                    case 5 => {
                      {
                        if (debug) {
                          java.lang.System.out.println("endArray")
                        } else ()
                        this.pop()
                        if (this.stop$field) {
                          /* break */ ()
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
                    case 6 => {
                      {
                        val start: scala.Int = p - 1
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
                          java.lang.System.out.println("comment " + new java.lang.String(data, start, p - start))
                        } else ()
                      }
                    }
                    case 7 => {
                      {
                        if (debug) {
                          java.lang.System.out.println("unquotedChars")
                        } else ()
                        s = p
                        needsUnescape = false
                        stringIsUnquoted = true
                        if (stringIsName) {
                          while (true) {
                            data(p) match {
                              case '\\' => {
                                needsUnescape = true
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
                              case ':' | '\r' | '\n' => {
                                ()
                              }
                            }
                            if (debug) {
                              java.lang.System.out.println(("unquotedChar (name): '" + data(p)) + "'")
                            } else ()
                            p = p + 1
                            if (p == eof) {
                              /* break */ ()
                            } else ()
                          }
                        } else {
                          while (true) {
                            data(p) match {
                              case '\\' => {
                                needsUnescape = true
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
                              case '}' | ']' | ',' | '\r' | '\n' => {
                                ()
                              }
                            }
                            if (debug) {
                              java.lang.System.out.println(("unquotedChar (value): '" + data(p)) + "'")
                            } else ()
                            p = p + 1
                            if (p == eof) {
                              /* break */ ()
                            } else ()
                          }
                        }
                        p = p - 1
                        while (java.lang.Character.isSpace(data(p))) {
                          p = p - 1
                        }
                      }
                    }
                    case 8 => {
                      {
                        if (debug) {
                          java.lang.System.out.println("quotedChars")
                        } else ()
                        s = { p += 1; p }
                        needsUnescape = false
                        while (true) {
                          data(p) match {
                            case '\\' => {
                              needsUnescape = true
                              p = p + 1
                            }
                            case '\"' => {
                              ()
                            }
                          }
                          if (debug) {
                            java.lang.System.out.println(("quotedChar: '" + data(p)) + "'")
                          } else ()
                          p = p + 1
                          if (p == eof) {
                            /* break */ ()
                          } else ()
                        }
                        p = p - 1
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
                var __acts: scala.Int = JsonReader._json_eof_actions(cs)
                var __nacts: scala.Int = JsonReader._json_actions({ __acts += 1; __acts }).asInstanceOf[scala.Int]
                while ({ __nacts -= 1; __nacts } > 0) {
                  JsonReader._json_actions({ __acts += 1; __acts }) match {
                    case 1 => {
                      {
                        var value: java.lang.String = new java.lang.String(data, s, p - s)
                        if (needsUnescape) {
                          value = this.unescape(value)
                        } else ()
                        if (stringIsName) {
                          stringIsName = false
                          if (debug) {
                            java.lang.System.out.println("name: " + value)
                          } else ()
                          name = value
                        } else {
                          val valueName: java.lang.String = name
                          name = null
                          if (stringIsUnquoted) {
                            if (value.equals("true")) {
                              if (debug) {
                                java.lang.System.out.println(("boolean: " + valueName) + "=true")
                              } else ()
                              this.bool(valueName, true)
                              /* break */ ()
                            } else {
                              if (value.equals("false")) {
                                if (debug) {
                                  java.lang.System.out.println(("boolean: " + valueName) + "=false")
                                } else ()
                                this.bool(valueName, false)
                                /* break */ ()
                              } else {
                                if (value.equals("null")) {
                                  this.string(valueName, null)
                                  /* break */ ()
                                } else ()
                              }
                            }
                            var couldBeDouble: scala.Boolean = false
                            var couldBeLong: scala.Boolean = true;
                            { var i: scala.Int = s; while (i < p) { {
                              data(i) match {
                                case '0' | '1' | '2' | '3' | '4' | '5' | '6' | '7' | '8' | '9' | '-' | '+' => {
                                  ()
                                }
                                case '.' | 'e' | 'E' => {
                                  couldBeDouble = true
                                  couldBeLong = false
                                }
                                case _ => {
                                  couldBeDouble = false
                                  couldBeLong = false
                                }
                              }
                            }; i = i + 1 } }
                            if (couldBeDouble) {
                              try {
                                if (debug) {
                                  java.lang.System.out.println((("double: " + valueName) + "=") + java.lang.Double.parseDouble(value))
                                } else ()
                                this.number(valueName, java.lang.Double.parseDouble(value), value)
                                /* break */ ()
                              } catch {
                                case ignored: java.lang.NumberFormatException => {
                                  ()
                                }
                              }
                            } else {
                              if (couldBeLong) {
                                if (debug) {
                                  java.lang.System.out.println((("double: " + valueName) + "=") + java.lang.Double.parseDouble(value))
                                } else ()
                                try {
                                  this.number(valueName, java.lang.Long.parseLong(value), value)
                                  /* break */ ()
                                } catch {
                                  case ignored: java.lang.NumberFormatException => {
                                    ()
                                  }
                                }
                              } else ()
                            }
                          } else ()
                          if (debug) {
                            java.lang.System.out.println((("string: " + valueName) + "=") + value)
                          } else ()
                          this.string(valueName, value)
                        }
                        if (this.stop$field) {
                          /* break */ ()
                        } else ()
                        stringIsUnquoted = false
                        s = p
                      }
                    }
                  }
                }
              } else ()
            }
            case 1 => {
              while ({ {
                _keys = JsonReader._json_key_offsets(cs)
                _trans = JsonReader._json_index_offsets(cs)
                _klen = JsonReader._json_single_lengths(cs)
                if (_klen > 0) {
                  var _lower: scala.Int = _keys
                  var _mid: scala.Int = 0
                  var _upper: scala.Int = (_keys + _klen) - 1
                  while (true) {
                    if (_upper < _lower) {
                      /* break */ ()
                    } else ()
                    _mid = _lower + ((_upper - _lower) >> 1)
                    if (data(p) < JsonReader._json_trans_keys(_mid)) {
                      _upper = _mid - 1
                    } else {
                      if (data(p) > JsonReader._json_trans_keys(_mid)) {
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
                _klen = JsonReader._json_range_lengths(cs)
                if (_klen > 0) {
                  var _lower: scala.Int = _keys
                  var _mid: scala.Int = 0
                  var _upper: scala.Int = (_keys + (_klen << 1)) - 2
                  while (true) {
                    if (_upper < _lower) {
                      /* break */ ()
                    } else ()
                    _mid = _lower + (((_upper - _lower) >> 1) & (~1))
                    if (data(p) < JsonReader._json_trans_keys(_mid)) {
                      _upper = _mid - 2
                    } else {
                      if (data(p) > JsonReader._json_trans_keys(_mid + 1)) {
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
              _trans = JsonReader._json_indicies(_trans)
              cs = JsonReader._json_trans_targs(_trans)
              if (JsonReader._json_trans_actions(_trans) != 0) {
                _acts = JsonReader._json_trans_actions(_trans)
                _nacts = JsonReader._json_actions({ _acts += 1; _acts }).asInstanceOf[scala.Int]
                while ({ _nacts -= 1; _nacts } > 0) {
                  JsonReader._json_actions({ _acts += 1; _acts }) match {
                    case 0 => {
                      {
                        stringIsName = true
                      }
                    }
                    case 1 => {
                      {
                        var value: java.lang.String = new java.lang.String(data, s, p - s)
                        if (needsUnescape) {
                          value = this.unescape(value)
                        } else ()
                        if (stringIsName) {
                          stringIsName = false
                          if (debug) {
                            java.lang.System.out.println("name: " + value)
                          } else ()
                          name = value
                        } else {
                          val valueName: java.lang.String = name
                          name = null
                          if (stringIsUnquoted) {
                            if (value.equals("true")) {
                              if (debug) {
                                java.lang.System.out.println(("boolean: " + valueName) + "=true")
                              } else ()
                              this.bool(valueName, true)
                              /* break */ ()
                            } else {
                              if (value.equals("false")) {
                                if (debug) {
                                  java.lang.System.out.println(("boolean: " + valueName) + "=false")
                                } else ()
                                this.bool(valueName, false)
                                /* break */ ()
                              } else {
                                if (value.equals("null")) {
                                  this.string(valueName, null)
                                  /* break */ ()
                                } else ()
                              }
                            }
                            var couldBeDouble: scala.Boolean = false
                            var couldBeLong: scala.Boolean = true;
                            { var i: scala.Int = s; while (i < p) { {
                              data(i) match {
                                case '0' | '1' | '2' | '3' | '4' | '5' | '6' | '7' | '8' | '9' | '-' | '+' => {
                                  ()
                                }
                                case '.' | 'e' | 'E' => {
                                  couldBeDouble = true
                                  couldBeLong = false
                                }
                                case _ => {
                                  couldBeDouble = false
                                  couldBeLong = false
                                }
                              }
                            }; i = i + 1 } }
                            if (couldBeDouble) {
                              try {
                                if (debug) {
                                  java.lang.System.out.println((("double: " + valueName) + "=") + java.lang.Double.parseDouble(value))
                                } else ()
                                this.number(valueName, java.lang.Double.parseDouble(value), value)
                                /* break */ ()
                              } catch {
                                case ignored: java.lang.NumberFormatException => {
                                  ()
                                }
                              }
                            } else {
                              if (couldBeLong) {
                                if (debug) {
                                  java.lang.System.out.println((("double: " + valueName) + "=") + java.lang.Double.parseDouble(value))
                                } else ()
                                try {
                                  this.number(valueName, java.lang.Long.parseLong(value), value)
                                  /* break */ ()
                                } catch {
                                  case ignored: java.lang.NumberFormatException => {
                                    ()
                                  }
                                }
                              } else ()
                            }
                          } else ()
                          if (debug) {
                            java.lang.System.out.println((("string: " + valueName) + "=") + value)
                          } else ()
                          this.string(valueName, value)
                        }
                        if (this.stop$field) {
                          /* break */ ()
                        } else ()
                        stringIsUnquoted = false
                        s = p
                      }
                    }
                    case 2 => {
                      {
                        if (debug) {
                          java.lang.System.out.println("startObject: " + name)
                        } else ()
                        this.startObject(name)
                        if (this.stop$field) {
                          /* break */ ()
                        } else ()
                        name = null;
                        {
                          if (top == stack.length) {
                            stack = java.util.Arrays.copyOf(stack, stack.length * 2)
                          } else ();
                          {
                            stack({ top += 1; top }) = cs
                            cs = 5
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
                          java.lang.System.out.println("endObject")
                        } else ()
                        this.pop()
                        if (this.stop$field) {
                          /* break */ ()
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
                        if (debug) {
                          java.lang.System.out.println("startArray: " + name)
                        } else ()
                        this.startArray(name)
                        if (this.stop$field) {
                          /* break */ ()
                        } else ()
                        name = null;
                        {
                          if (top == stack.length) {
                            stack = java.util.Arrays.copyOf(stack, stack.length * 2)
                          } else ();
                          {
                            stack({ top += 1; top }) = cs
                            cs = 23
                            _goto_targ = 2
                            if (true) {
                              /* continue */ ()
                            } else ()
                          }
                        }
                      }
                    }
                    case 5 => {
                      {
                        if (debug) {
                          java.lang.System.out.println("endArray")
                        } else ()
                        this.pop()
                        if (this.stop$field) {
                          /* break */ ()
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
                    case 6 => {
                      {
                        val start: scala.Int = p - 1
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
                          java.lang.System.out.println("comment " + new java.lang.String(data, start, p - start))
                        } else ()
                      }
                    }
                    case 7 => {
                      {
                        if (debug) {
                          java.lang.System.out.println("unquotedChars")
                        } else ()
                        s = p
                        needsUnescape = false
                        stringIsUnquoted = true
                        if (stringIsName) {
                          while (true) {
                            data(p) match {
                              case '\\' => {
                                needsUnescape = true
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
                              case ':' | '\r' | '\n' => {
                                ()
                              }
                            }
                            if (debug) {
                              java.lang.System.out.println(("unquotedChar (name): '" + data(p)) + "'")
                            } else ()
                            p = p + 1
                            if (p == eof) {
                              /* break */ ()
                            } else ()
                          }
                        } else {
                          while (true) {
                            data(p) match {
                              case '\\' => {
                                needsUnescape = true
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
                              case '}' | ']' | ',' | '\r' | '\n' => {
                                ()
                              }
                            }
                            if (debug) {
                              java.lang.System.out.println(("unquotedChar (value): '" + data(p)) + "'")
                            } else ()
                            p = p + 1
                            if (p == eof) {
                              /* break */ ()
                            } else ()
                          }
                        }
                        p = p - 1
                        while (java.lang.Character.isSpace(data(p))) {
                          p = p - 1
                        }
                      }
                    }
                    case 8 => {
                      {
                        if (debug) {
                          java.lang.System.out.println("quotedChars")
                        } else ()
                        s = { p += 1; p }
                        needsUnescape = false
                        while (true) {
                          data(p) match {
                            case '\\' => {
                              needsUnescape = true
                              p = p + 1
                            }
                            case '\"' => {
                              ()
                            }
                          }
                          if (debug) {
                            java.lang.System.out.println(("quotedChar: '" + data(p)) + "'")
                          } else ()
                          p = p + 1
                          if (p == eof) {
                            /* break */ ()
                          } else ()
                        }
                        p = p - 1
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
                var __acts: scala.Int = JsonReader._json_eof_actions(cs)
                var __nacts: scala.Int = JsonReader._json_actions({ __acts += 1; __acts }).asInstanceOf[scala.Int]
                while ({ __nacts -= 1; __nacts } > 0) {
                  JsonReader._json_actions({ __acts += 1; __acts }) match {
                    case 1 => {
                      {
                        var value: java.lang.String = new java.lang.String(data, s, p - s)
                        if (needsUnescape) {
                          value = this.unescape(value)
                        } else ()
                        if (stringIsName) {
                          stringIsName = false
                          if (debug) {
                            java.lang.System.out.println("name: " + value)
                          } else ()
                          name = value
                        } else {
                          val valueName: java.lang.String = name
                          name = null
                          if (stringIsUnquoted) {
                            if (value.equals("true")) {
                              if (debug) {
                                java.lang.System.out.println(("boolean: " + valueName) + "=true")
                              } else ()
                              this.bool(valueName, true)
                              /* break */ ()
                            } else {
                              if (value.equals("false")) {
                                if (debug) {
                                  java.lang.System.out.println(("boolean: " + valueName) + "=false")
                                } else ()
                                this.bool(valueName, false)
                                /* break */ ()
                              } else {
                                if (value.equals("null")) {
                                  this.string(valueName, null)
                                  /* break */ ()
                                } else ()
                              }
                            }
                            var couldBeDouble: scala.Boolean = false
                            var couldBeLong: scala.Boolean = true;
                            { var i: scala.Int = s; while (i < p) { {
                              data(i) match {
                                case '0' | '1' | '2' | '3' | '4' | '5' | '6' | '7' | '8' | '9' | '-' | '+' => {
                                  ()
                                }
                                case '.' | 'e' | 'E' => {
                                  couldBeDouble = true
                                  couldBeLong = false
                                }
                                case _ => {
                                  couldBeDouble = false
                                  couldBeLong = false
                                }
                              }
                            }; i = i + 1 } }
                            if (couldBeDouble) {
                              try {
                                if (debug) {
                                  java.lang.System.out.println((("double: " + valueName) + "=") + java.lang.Double.parseDouble(value))
                                } else ()
                                this.number(valueName, java.lang.Double.parseDouble(value), value)
                                /* break */ ()
                              } catch {
                                case ignored: java.lang.NumberFormatException => {
                                  ()
                                }
                              }
                            } else {
                              if (couldBeLong) {
                                if (debug) {
                                  java.lang.System.out.println((("double: " + valueName) + "=") + java.lang.Double.parseDouble(value))
                                } else ()
                                try {
                                  this.number(valueName, java.lang.Long.parseLong(value), value)
                                  /* break */ ()
                                } catch {
                                  case ignored: java.lang.NumberFormatException => {
                                    ()
                                  }
                                }
                              } else ()
                            }
                          } else ()
                          if (debug) {
                            java.lang.System.out.println((("string: " + valueName) + "=") + value)
                          } else ()
                          this.string(valueName, value)
                        }
                        if (this.stop$field) {
                          /* break */ ()
                        } else ()
                        stringIsUnquoted = false
                        s = p
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
                var __acts: scala.Int = JsonReader._json_eof_actions(cs)
                var __nacts: scala.Int = JsonReader._json_actions({ __acts += 1; __acts }).asInstanceOf[scala.Int]
                while ({ __nacts -= 1; __nacts } > 0) {
                  JsonReader._json_actions({ __acts += 1; __acts }) match {
                    case 1 => {
                      {
                        var value: java.lang.String = new java.lang.String(data, s, p - s)
                        if (needsUnescape) {
                          value = this.unescape(value)
                        } else ()
                        if (stringIsName) {
                          stringIsName = false
                          if (debug) {
                            java.lang.System.out.println("name: " + value)
                          } else ()
                          name = value
                        } else {
                          val valueName: java.lang.String = name
                          name = null
                          if (stringIsUnquoted) {
                            if (value.equals("true")) {
                              if (debug) {
                                java.lang.System.out.println(("boolean: " + valueName) + "=true")
                              } else ()
                              this.bool(valueName, true)
                              /* break */ ()
                            } else {
                              if (value.equals("false")) {
                                if (debug) {
                                  java.lang.System.out.println(("boolean: " + valueName) + "=false")
                                } else ()
                                this.bool(valueName, false)
                                /* break */ ()
                              } else {
                                if (value.equals("null")) {
                                  this.string(valueName, null)
                                  /* break */ ()
                                } else ()
                              }
                            }
                            var couldBeDouble: scala.Boolean = false
                            var couldBeLong: scala.Boolean = true;
                            { var i: scala.Int = s; while (i < p) { {
                              data(i) match {
                                case '0' | '1' | '2' | '3' | '4' | '5' | '6' | '7' | '8' | '9' | '-' | '+' => {
                                  ()
                                }
                                case '.' | 'e' | 'E' => {
                                  couldBeDouble = true
                                  couldBeLong = false
                                }
                                case _ => {
                                  couldBeDouble = false
                                  couldBeLong = false
                                }
                              }
                            }; i = i + 1 } }
                            if (couldBeDouble) {
                              try {
                                if (debug) {
                                  java.lang.System.out.println((("double: " + valueName) + "=") + java.lang.Double.parseDouble(value))
                                } else ()
                                this.number(valueName, java.lang.Double.parseDouble(value), value)
                                /* break */ ()
                              } catch {
                                case ignored: java.lang.NumberFormatException => {
                                  ()
                                }
                              }
                            } else {
                              if (couldBeLong) {
                                if (debug) {
                                  java.lang.System.out.println((("double: " + valueName) + "=") + java.lang.Double.parseDouble(value))
                                } else ()
                                try {
                                  this.number(valueName, java.lang.Long.parseLong(value), value)
                                  /* break */ ()
                                } catch {
                                  case ignored: java.lang.NumberFormatException => {
                                    ()
                                  }
                                }
                              } else ()
                            }
                          } else ()
                          if (debug) {
                            java.lang.System.out.println((("string: " + valueName) + "=") + value)
                          } else ()
                          this.string(valueName, value)
                        }
                        if (this.stop$field) {
                          /* break */ ()
                        } else ()
                        stringIsUnquoted = false
                        s = p
                      }
                    }
                  }
                }
              } else ()
            }
            case 4 => {
              if (p == eof) {
                var __acts: scala.Int = JsonReader._json_eof_actions(cs)
                var __nacts: scala.Int = JsonReader._json_actions({ __acts += 1; __acts }).asInstanceOf[scala.Int]
                while ({ __nacts -= 1; __nacts } > 0) {
                  JsonReader._json_actions({ __acts += 1; __acts }) match {
                    case 1 => {
                      {
                        var value: java.lang.String = new java.lang.String(data, s, p - s)
                        if (needsUnescape) {
                          value = this.unescape(value)
                        } else ()
                        if (stringIsName) {
                          stringIsName = false
                          if (debug) {
                            java.lang.System.out.println("name: " + value)
                          } else ()
                          name = value
                        } else {
                          val valueName: java.lang.String = name
                          name = null
                          if (stringIsUnquoted) {
                            if (value.equals("true")) {
                              if (debug) {
                                java.lang.System.out.println(("boolean: " + valueName) + "=true")
                              } else ()
                              this.bool(valueName, true)
                              /* break */ ()
                            } else {
                              if (value.equals("false")) {
                                if (debug) {
                                  java.lang.System.out.println(("boolean: " + valueName) + "=false")
                                } else ()
                                this.bool(valueName, false)
                                /* break */ ()
                              } else {
                                if (value.equals("null")) {
                                  this.string(valueName, null)
                                  /* break */ ()
                                } else ()
                              }
                            }
                            var couldBeDouble: scala.Boolean = false
                            var couldBeLong: scala.Boolean = true;
                            { var i: scala.Int = s; while (i < p) { {
                              data(i) match {
                                case '0' | '1' | '2' | '3' | '4' | '5' | '6' | '7' | '8' | '9' | '-' | '+' => {
                                  ()
                                }
                                case '.' | 'e' | 'E' => {
                                  couldBeDouble = true
                                  couldBeLong = false
                                }
                                case _ => {
                                  couldBeDouble = false
                                  couldBeLong = false
                                }
                              }
                            }; i = i + 1 } }
                            if (couldBeDouble) {
                              try {
                                if (debug) {
                                  java.lang.System.out.println((("double: " + valueName) + "=") + java.lang.Double.parseDouble(value))
                                } else ()
                                this.number(valueName, java.lang.Double.parseDouble(value), value)
                                /* break */ ()
                              } catch {
                                case ignored: java.lang.NumberFormatException => {
                                  ()
                                }
                              }
                            } else {
                              if (couldBeLong) {
                                if (debug) {
                                  java.lang.System.out.println((("double: " + valueName) + "=") + java.lang.Double.parseDouble(value))
                                } else ()
                                try {
                                  this.number(valueName, java.lang.Long.parseLong(value), value)
                                  /* break */ ()
                                } catch {
                                  case ignored: java.lang.NumberFormatException => {
                                    ()
                                  }
                                }
                              } else ()
                            }
                          } else ()
                          if (debug) {
                            java.lang.System.out.println((("string: " + valueName) + "=") + value)
                          } else ()
                          this.string(valueName, value)
                        }
                        if (this.stop$field) {
                          /* break */ ()
                        } else ()
                        stringIsUnquoted = false
                        s = p
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
    var root: com.badlogic.gdx.utils.JsonValue = this.root
    this.root = null
    this.current = null
    if (!this.stop$field) {
      if (p < pe) {
        var lineNumber: scala.Int = 1;
        { var i: scala.Int = 0; while (i < p) { {
          if (data(i) == '\n') {
            lineNumber = lineNumber + 1
          } else ()
        }; i = i + 1 } }
        val start: scala.Int = java.lang.Math.max(0, p - 32)
        throw new com.badlogic.gdx.utils.SerializationException((((("Error parsing JSON on line " + lineNumber) + " near: ") + new java.lang.String(data, start, p - start)) + "*ERROR*") + new java.lang.String(data, p, java.lang.Math.min(64, pe - p)), parseRuntimeEx)
      } else ()
      if (this.elements.size != 0) {
        val element: com.badlogic.gdx.utils.JsonValue = this.elements.peek()
        this.elements.clear()
        if ((element != null) && element.isObject()) {
          throw new com.badlogic.gdx.utils.SerializationException("Error parsing JSON, unmatched brace.")
        } else {
          throw new com.badlogic.gdx.utils.SerializationException("Error parsing JSON, unmatched bracket.")
        }
      } else ()
      if (parseRuntimeEx != null) {
        throw new com.badlogic.gdx.utils.SerializationException("Error parsing JSON: " + new java.lang.String(data), parseRuntimeEx)
      } else ()
    } else ()
    return root
  }
  def stop(): scala.Unit = {
    this.stop$field = true
  }
  def isStopped(): scala.Boolean = {
    return this.stop$field
  }
  private def addChild(name: java.lang.String, child: com.badlogic.gdx.utils.JsonValue): scala.Unit = {
    child.setName(name)
    if (this.current == null) {
      this.current = child
      this.root = child
    } else {
      if (this.current.isArray() || this.current.isObject()) {
        this.current.addChild(child)
      } else {
        this.root = this.current
      }
    }
  }
  def startObject(name: java.lang.String): scala.Unit = {
    val value: com.badlogic.gdx.utils.JsonValue = new com.badlogic.gdx.utils.JsonValue(com.badlogic.gdx.utils.JsonValue.ValueType.`object`)
    if (this.current != null) {
      this.addChild(name, value)
    } else ()
    this.elements.add(value)
    this.current = value
  }
  def startArray(name: java.lang.String): scala.Unit = {
    val value: com.badlogic.gdx.utils.JsonValue = new com.badlogic.gdx.utils.JsonValue(com.badlogic.gdx.utils.JsonValue.ValueType.array)
    if (this.current != null) {
      this.addChild(name, value)
    } else ()
    this.elements.add(value)
    this.current = value
  }
  def pop(): scala.Unit = {
    this.root = this.elements.pop()
    this.current = if (this.elements.size > 0) this.elements.peek() else null.asInstanceOf[com.badlogic.gdx.utils.JsonValue]
  }
  def string(name: java.lang.String, value: java.lang.String): scala.Unit = {
    this.addChild(name, new com.badlogic.gdx.utils.JsonValue(value))
  }
  def number(name: java.lang.String, value: scala.Double, stringValue: java.lang.String): scala.Unit = {
    this.addChild(name, new com.badlogic.gdx.utils.JsonValue(value, stringValue))
  }
  def number(name: java.lang.String, value: scala.Long, stringValue: java.lang.String): scala.Unit = {
    this.addChild(name, new com.badlogic.gdx.utils.JsonValue(value, stringValue))
  }
  def bool(name: java.lang.String, value: scala.Boolean): scala.Unit = {
    this.addChild(name, new com.badlogic.gdx.utils.JsonValue(value))
  }
  def unescape(value: java.lang.String): java.lang.String = {
    val length: scala.Int = value.length()
    val buffer: java.lang.StringBuilder = new java.lang.StringBuilder(length + 16);
    { var i: scala.Int = 0; while (i < length) { {
      var c: scala.Char = value.charAt({ i += 1; i })
      if (c != '\\') {
        buffer.append(c)
        /* continue */ ()
      } else ()
      if (i == length) {
        /* break */ ()
      } else ()
      c = value.charAt({ i += 1; i })
      if (c == 'u') {
        buffer.append(java.lang.Character.toChars(java.lang.Integer.parseInt(value.substring(i, i + 4), 16)))
        i = i + 4
        /* continue */ ()
      } else ()
      c match {
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
      buffer.append(c)
    };  } }
    return buffer.toString()
  }
}
object JsonReader {
  private final val _json_actions: scala.Array[scala.Byte] = JsonReader.init__json_actions_0()
  private final val _json_key_offsets: scala.Array[scala.Short] = JsonReader.init__json_key_offsets_0()
  private final val _json_trans_keys: scala.Array[scala.Char] = JsonReader.init__json_trans_keys_0()
  private final val _json_single_lengths: scala.Array[scala.Byte] = JsonReader.init__json_single_lengths_0()
  private final val _json_range_lengths: scala.Array[scala.Byte] = JsonReader.init__json_range_lengths_0()
  private final val _json_index_offsets: scala.Array[scala.Short] = JsonReader.init__json_index_offsets_0()
  private final val _json_indicies: scala.Array[scala.Byte] = JsonReader.init__json_indicies_0()
  private final val _json_trans_targs: scala.Array[scala.Byte] = JsonReader.init__json_trans_targs_0()
  private final val _json_trans_actions: scala.Array[scala.Byte] = JsonReader.init__json_trans_actions_0()
  private final val _json_eof_actions: scala.Array[scala.Byte] = JsonReader.init__json_eof_actions_0()
  final val json_start: scala.Int = 1
  final val json_first_final: scala.Int = 35
  final val json_error: scala.Int = 0
  final val json_en_object: scala.Int = 5
  final val json_en_array: scala.Int = 23
  final val json_en_main: scala.Int = 1
  private def init__json_actions_0(): scala.Array[scala.Byte] = {
    return scala.Array[scala.Byte](0.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 4.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 5.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 6.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 7.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 8.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 7.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 8.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 5.asInstanceOf[scala.Byte])
  }
  private def init__json_key_offsets_0(): scala.Array[scala.Short] = {
    return scala.Array[scala.Short](0.asInstanceOf[scala.Short], 0.asInstanceOf[scala.Short], 11.asInstanceOf[scala.Short], 13.asInstanceOf[scala.Short], 14.asInstanceOf[scala.Short], 16.asInstanceOf[scala.Short], 25.asInstanceOf[scala.Short], 31.asInstanceOf[scala.Short], 37.asInstanceOf[scala.Short], 39.asInstanceOf[scala.Short], 50.asInstanceOf[scala.Short], 57.asInstanceOf[scala.Short], 64.asInstanceOf[scala.Short], 73.asInstanceOf[scala.Short], 74.asInstanceOf[scala.Short], 83.asInstanceOf[scala.Short], 85.asInstanceOf[scala.Short], 87.asInstanceOf[scala.Short], 96.asInstanceOf[scala.Short], 98.asInstanceOf[scala.Short], 100.asInstanceOf[scala.Short], 101.asInstanceOf[scala.Short], 103.asInstanceOf[scala.Short], 105.asInstanceOf[scala.Short], 116.asInstanceOf[scala.Short], 123.asInstanceOf[scala.Short], 130.asInstanceOf[scala.Short], 141.asInstanceOf[scala.Short], 142.asInstanceOf[scala.Short], 153.asInstanceOf[scala.Short], 155.asInstanceOf[scala.Short], 157.asInstanceOf[scala.Short], 168.asInstanceOf[scala.Short], 170.asInstanceOf[scala.Short], 172.asInstanceOf[scala.Short], 174.asInstanceOf[scala.Short], 179.asInstanceOf[scala.Short], 184.asInstanceOf[scala.Short], 184.asInstanceOf[scala.Short])
  }
  private def init__json_trans_keys_0(): scala.Array[scala.Char] = {
    return scala.Array[scala.Char](13.asInstanceOf[scala.Char], 32.asInstanceOf[scala.Char], 34.asInstanceOf[scala.Char], 44.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 58.asInstanceOf[scala.Char], 91.asInstanceOf[scala.Char], 93.asInstanceOf[scala.Char], 123.asInstanceOf[scala.Char], 9.asInstanceOf[scala.Char], 10.asInstanceOf[scala.Char], 42.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 34.asInstanceOf[scala.Char], 42.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 13.asInstanceOf[scala.Char], 32.asInstanceOf[scala.Char], 34.asInstanceOf[scala.Char], 44.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 58.asInstanceOf[scala.Char], 125.asInstanceOf[scala.Char], 9.asInstanceOf[scala.Char], 10.asInstanceOf[scala.Char], 13.asInstanceOf[scala.Char], 32.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 58.asInstanceOf[scala.Char], 9.asInstanceOf[scala.Char], 10.asInstanceOf[scala.Char], 13.asInstanceOf[scala.Char], 32.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 58.asInstanceOf[scala.Char], 9.asInstanceOf[scala.Char], 10.asInstanceOf[scala.Char], 42.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 13.asInstanceOf[scala.Char], 32.asInstanceOf[scala.Char], 34.asInstanceOf[scala.Char], 44.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 58.asInstanceOf[scala.Char], 91.asInstanceOf[scala.Char], 93.asInstanceOf[scala.Char], 123.asInstanceOf[scala.Char], 9.asInstanceOf[scala.Char], 10.asInstanceOf[scala.Char], 9.asInstanceOf[scala.Char], 10.asInstanceOf[scala.Char], 13.asInstanceOf[scala.Char], 32.asInstanceOf[scala.Char], 44.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 125.asInstanceOf[scala.Char], 9.asInstanceOf[scala.Char], 10.asInstanceOf[scala.Char], 13.asInstanceOf[scala.Char], 32.asInstanceOf[scala.Char], 44.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 125.asInstanceOf[scala.Char], 13.asInstanceOf[scala.Char], 32.asInstanceOf[scala.Char], 34.asInstanceOf[scala.Char], 44.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 58.asInstanceOf[scala.Char], 125.asInstanceOf[scala.Char], 9.asInstanceOf[scala.Char], 10.asInstanceOf[scala.Char], 34.asInstanceOf[scala.Char], 13.asInstanceOf[scala.Char], 32.asInstanceOf[scala.Char], 34.asInstanceOf[scala.Char], 44.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 58.asInstanceOf[scala.Char], 125.asInstanceOf[scala.Char], 9.asInstanceOf[scala.Char], 10.asInstanceOf[scala.Char], 42.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 42.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 13.asInstanceOf[scala.Char], 32.asInstanceOf[scala.Char], 34.asInstanceOf[scala.Char], 44.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 58.asInstanceOf[scala.Char], 125.asInstanceOf[scala.Char], 9.asInstanceOf[scala.Char], 10.asInstanceOf[scala.Char], 42.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 42.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 34.asInstanceOf[scala.Char], 42.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 42.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 13.asInstanceOf[scala.Char], 32.asInstanceOf[scala.Char], 34.asInstanceOf[scala.Char], 44.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 58.asInstanceOf[scala.Char], 91.asInstanceOf[scala.Char], 93.asInstanceOf[scala.Char], 123.asInstanceOf[scala.Char], 9.asInstanceOf[scala.Char], 10.asInstanceOf[scala.Char], 9.asInstanceOf[scala.Char], 10.asInstanceOf[scala.Char], 13.asInstanceOf[scala.Char], 32.asInstanceOf[scala.Char], 44.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 93.asInstanceOf[scala.Char], 9.asInstanceOf[scala.Char], 10.asInstanceOf[scala.Char], 13.asInstanceOf[scala.Char], 32.asInstanceOf[scala.Char], 44.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 93.asInstanceOf[scala.Char], 13.asInstanceOf[scala.Char], 32.asInstanceOf[scala.Char], 34.asInstanceOf[scala.Char], 44.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 58.asInstanceOf[scala.Char], 91.asInstanceOf[scala.Char], 93.asInstanceOf[scala.Char], 123.asInstanceOf[scala.Char], 9.asInstanceOf[scala.Char], 10.asInstanceOf[scala.Char], 34.asInstanceOf[scala.Char], 13.asInstanceOf[scala.Char], 32.asInstanceOf[scala.Char], 34.asInstanceOf[scala.Char], 44.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 58.asInstanceOf[scala.Char], 91.asInstanceOf[scala.Char], 93.asInstanceOf[scala.Char], 123.asInstanceOf[scala.Char], 9.asInstanceOf[scala.Char], 10.asInstanceOf[scala.Char], 42.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 42.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 13.asInstanceOf[scala.Char], 32.asInstanceOf[scala.Char], 34.asInstanceOf[scala.Char], 44.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 58.asInstanceOf[scala.Char], 91.asInstanceOf[scala.Char], 93.asInstanceOf[scala.Char], 123.asInstanceOf[scala.Char], 9.asInstanceOf[scala.Char], 10.asInstanceOf[scala.Char], 42.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 42.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 42.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 13.asInstanceOf[scala.Char], 32.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 9.asInstanceOf[scala.Char], 10.asInstanceOf[scala.Char], 13.asInstanceOf[scala.Char], 32.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 9.asInstanceOf[scala.Char], 10.asInstanceOf[scala.Char], 0.asInstanceOf[scala.Char])
  }
  private def init__json_single_lengths_0(): scala.Array[scala.Byte] = {
    return scala.Array[scala.Byte](0.asInstanceOf[scala.Byte], 9.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 7.asInstanceOf[scala.Byte], 4.asInstanceOf[scala.Byte], 4.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 9.asInstanceOf[scala.Byte], 7.asInstanceOf[scala.Byte], 7.asInstanceOf[scala.Byte], 7.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 7.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 7.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 9.asInstanceOf[scala.Byte], 7.asInstanceOf[scala.Byte], 7.asInstanceOf[scala.Byte], 9.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 9.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 9.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte])
  }
  private def init__json_range_lengths_0(): scala.Array[scala.Byte] = {
    return scala.Array[scala.Byte](0.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte])
  }
  private def init__json_index_offsets_0(): scala.Array[scala.Short] = {
    return scala.Array[scala.Short](0.asInstanceOf[scala.Short], 0.asInstanceOf[scala.Short], 11.asInstanceOf[scala.Short], 14.asInstanceOf[scala.Short], 16.asInstanceOf[scala.Short], 19.asInstanceOf[scala.Short], 28.asInstanceOf[scala.Short], 34.asInstanceOf[scala.Short], 40.asInstanceOf[scala.Short], 43.asInstanceOf[scala.Short], 54.asInstanceOf[scala.Short], 62.asInstanceOf[scala.Short], 70.asInstanceOf[scala.Short], 79.asInstanceOf[scala.Short], 81.asInstanceOf[scala.Short], 90.asInstanceOf[scala.Short], 93.asInstanceOf[scala.Short], 96.asInstanceOf[scala.Short], 105.asInstanceOf[scala.Short], 108.asInstanceOf[scala.Short], 111.asInstanceOf[scala.Short], 113.asInstanceOf[scala.Short], 116.asInstanceOf[scala.Short], 119.asInstanceOf[scala.Short], 130.asInstanceOf[scala.Short], 138.asInstanceOf[scala.Short], 146.asInstanceOf[scala.Short], 157.asInstanceOf[scala.Short], 159.asInstanceOf[scala.Short], 170.asInstanceOf[scala.Short], 173.asInstanceOf[scala.Short], 176.asInstanceOf[scala.Short], 187.asInstanceOf[scala.Short], 190.asInstanceOf[scala.Short], 193.asInstanceOf[scala.Short], 196.asInstanceOf[scala.Short], 201.asInstanceOf[scala.Short], 206.asInstanceOf[scala.Short], 207.asInstanceOf[scala.Short])
  }
  private def init__json_indicies_0(): scala.Array[scala.Byte] = {
    return scala.Array[scala.Byte](1.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 4.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 5.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 6.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 7.asInstanceOf[scala.Byte], 7.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 8.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 9.asInstanceOf[scala.Byte], 9.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 11.asInstanceOf[scala.Byte], 11.asInstanceOf[scala.Byte], 12.asInstanceOf[scala.Byte], 13.asInstanceOf[scala.Byte], 14.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 15.asInstanceOf[scala.Byte], 11.asInstanceOf[scala.Byte], 10.asInstanceOf[scala.Byte], 16.asInstanceOf[scala.Byte], 16.asInstanceOf[scala.Byte], 17.asInstanceOf[scala.Byte], 18.asInstanceOf[scala.Byte], 16.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 19.asInstanceOf[scala.Byte], 19.asInstanceOf[scala.Byte], 20.asInstanceOf[scala.Byte], 21.asInstanceOf[scala.Byte], 19.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 22.asInstanceOf[scala.Byte], 22.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 21.asInstanceOf[scala.Byte], 21.asInstanceOf[scala.Byte], 24.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 25.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 26.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 27.asInstanceOf[scala.Byte], 21.asInstanceOf[scala.Byte], 23.asInstanceOf[scala.Byte], 28.asInstanceOf[scala.Byte], 29.asInstanceOf[scala.Byte], 29.asInstanceOf[scala.Byte], 28.asInstanceOf[scala.Byte], 30.asInstanceOf[scala.Byte], 31.asInstanceOf[scala.Byte], 32.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 33.asInstanceOf[scala.Byte], 34.asInstanceOf[scala.Byte], 34.asInstanceOf[scala.Byte], 33.asInstanceOf[scala.Byte], 13.asInstanceOf[scala.Byte], 35.asInstanceOf[scala.Byte], 15.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 34.asInstanceOf[scala.Byte], 34.asInstanceOf[scala.Byte], 12.asInstanceOf[scala.Byte], 36.asInstanceOf[scala.Byte], 37.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 15.asInstanceOf[scala.Byte], 34.asInstanceOf[scala.Byte], 10.asInstanceOf[scala.Byte], 16.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 36.asInstanceOf[scala.Byte], 36.asInstanceOf[scala.Byte], 12.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 38.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 36.asInstanceOf[scala.Byte], 10.asInstanceOf[scala.Byte], 39.asInstanceOf[scala.Byte], 39.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 40.asInstanceOf[scala.Byte], 40.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 13.asInstanceOf[scala.Byte], 13.asInstanceOf[scala.Byte], 12.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 41.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 15.asInstanceOf[scala.Byte], 13.asInstanceOf[scala.Byte], 10.asInstanceOf[scala.Byte], 42.asInstanceOf[scala.Byte], 42.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 43.asInstanceOf[scala.Byte], 43.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 28.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 44.asInstanceOf[scala.Byte], 44.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 45.asInstanceOf[scala.Byte], 45.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 47.asInstanceOf[scala.Byte], 47.asInstanceOf[scala.Byte], 48.asInstanceOf[scala.Byte], 49.asInstanceOf[scala.Byte], 50.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 51.asInstanceOf[scala.Byte], 52.asInstanceOf[scala.Byte], 53.asInstanceOf[scala.Byte], 47.asInstanceOf[scala.Byte], 46.asInstanceOf[scala.Byte], 54.asInstanceOf[scala.Byte], 55.asInstanceOf[scala.Byte], 55.asInstanceOf[scala.Byte], 54.asInstanceOf[scala.Byte], 56.asInstanceOf[scala.Byte], 57.asInstanceOf[scala.Byte], 58.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 59.asInstanceOf[scala.Byte], 60.asInstanceOf[scala.Byte], 60.asInstanceOf[scala.Byte], 59.asInstanceOf[scala.Byte], 49.asInstanceOf[scala.Byte], 61.asInstanceOf[scala.Byte], 52.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 60.asInstanceOf[scala.Byte], 60.asInstanceOf[scala.Byte], 48.asInstanceOf[scala.Byte], 62.asInstanceOf[scala.Byte], 63.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 51.asInstanceOf[scala.Byte], 52.asInstanceOf[scala.Byte], 53.asInstanceOf[scala.Byte], 60.asInstanceOf[scala.Byte], 46.asInstanceOf[scala.Byte], 54.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 62.asInstanceOf[scala.Byte], 62.asInstanceOf[scala.Byte], 48.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 64.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 51.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 53.asInstanceOf[scala.Byte], 62.asInstanceOf[scala.Byte], 46.asInstanceOf[scala.Byte], 65.asInstanceOf[scala.Byte], 65.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 66.asInstanceOf[scala.Byte], 66.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 49.asInstanceOf[scala.Byte], 49.asInstanceOf[scala.Byte], 48.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 67.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 51.asInstanceOf[scala.Byte], 52.asInstanceOf[scala.Byte], 53.asInstanceOf[scala.Byte], 49.asInstanceOf[scala.Byte], 46.asInstanceOf[scala.Byte], 68.asInstanceOf[scala.Byte], 68.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 69.asInstanceOf[scala.Byte], 69.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 70.asInstanceOf[scala.Byte], 70.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 8.asInstanceOf[scala.Byte], 8.asInstanceOf[scala.Byte], 71.asInstanceOf[scala.Byte], 8.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 72.asInstanceOf[scala.Byte], 72.asInstanceOf[scala.Byte], 73.asInstanceOf[scala.Byte], 72.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte])
  }
  private def init__json_trans_targs_0(): scala.Array[scala.Byte] = {
    return scala.Array[scala.Byte](35.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 4.asInstanceOf[scala.Byte], 36.asInstanceOf[scala.Byte], 36.asInstanceOf[scala.Byte], 36.asInstanceOf[scala.Byte], 36.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 6.asInstanceOf[scala.Byte], 5.asInstanceOf[scala.Byte], 13.asInstanceOf[scala.Byte], 17.asInstanceOf[scala.Byte], 22.asInstanceOf[scala.Byte], 37.asInstanceOf[scala.Byte], 7.asInstanceOf[scala.Byte], 8.asInstanceOf[scala.Byte], 9.asInstanceOf[scala.Byte], 7.asInstanceOf[scala.Byte], 8.asInstanceOf[scala.Byte], 9.asInstanceOf[scala.Byte], 7.asInstanceOf[scala.Byte], 10.asInstanceOf[scala.Byte], 20.asInstanceOf[scala.Byte], 21.asInstanceOf[scala.Byte], 11.asInstanceOf[scala.Byte], 11.asInstanceOf[scala.Byte], 11.asInstanceOf[scala.Byte], 12.asInstanceOf[scala.Byte], 17.asInstanceOf[scala.Byte], 19.asInstanceOf[scala.Byte], 37.asInstanceOf[scala.Byte], 11.asInstanceOf[scala.Byte], 12.asInstanceOf[scala.Byte], 19.asInstanceOf[scala.Byte], 14.asInstanceOf[scala.Byte], 16.asInstanceOf[scala.Byte], 15.asInstanceOf[scala.Byte], 14.asInstanceOf[scala.Byte], 12.asInstanceOf[scala.Byte], 18.asInstanceOf[scala.Byte], 17.asInstanceOf[scala.Byte], 11.asInstanceOf[scala.Byte], 9.asInstanceOf[scala.Byte], 5.asInstanceOf[scala.Byte], 24.asInstanceOf[scala.Byte], 23.asInstanceOf[scala.Byte], 27.asInstanceOf[scala.Byte], 31.asInstanceOf[scala.Byte], 34.asInstanceOf[scala.Byte], 25.asInstanceOf[scala.Byte], 38.asInstanceOf[scala.Byte], 25.asInstanceOf[scala.Byte], 25.asInstanceOf[scala.Byte], 26.asInstanceOf[scala.Byte], 31.asInstanceOf[scala.Byte], 33.asInstanceOf[scala.Byte], 38.asInstanceOf[scala.Byte], 25.asInstanceOf[scala.Byte], 26.asInstanceOf[scala.Byte], 33.asInstanceOf[scala.Byte], 28.asInstanceOf[scala.Byte], 30.asInstanceOf[scala.Byte], 29.asInstanceOf[scala.Byte], 28.asInstanceOf[scala.Byte], 26.asInstanceOf[scala.Byte], 32.asInstanceOf[scala.Byte], 31.asInstanceOf[scala.Byte], 25.asInstanceOf[scala.Byte], 23.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 36.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte])
  }
  private def init__json_trans_actions_0(): scala.Array[scala.Byte] = {
    return scala.Array[scala.Byte](13.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 15.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 7.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 11.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 11.asInstanceOf[scala.Byte], 17.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 20.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 5.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 11.asInstanceOf[scala.Byte], 13.asInstanceOf[scala.Byte], 15.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 7.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 23.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 11.asInstanceOf[scala.Byte], 11.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 11.asInstanceOf[scala.Byte], 11.asInstanceOf[scala.Byte], 11.asInstanceOf[scala.Byte], 11.asInstanceOf[scala.Byte], 13.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 15.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 7.asInstanceOf[scala.Byte], 9.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 26.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 11.asInstanceOf[scala.Byte], 11.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 11.asInstanceOf[scala.Byte], 11.asInstanceOf[scala.Byte], 11.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte])
  }
  private def init__json_eof_actions_0(): scala.Array[scala.Byte] = {
    return scala.Array[scala.Byte](0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte])
  }
}