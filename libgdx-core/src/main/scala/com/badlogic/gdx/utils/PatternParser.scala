package com.badlogic.gdx.utils

object PatternParser {
  private final val _parser_actions: scala.Array[scala.Byte] = PatternParser.init__parser_actions_0()
  private final val _parser_key_offsets: scala.Array[scala.Byte] = PatternParser.init__parser_key_offsets_0()
  private final val _parser_trans_keys: scala.Array[scala.Char] = PatternParser.init__parser_trans_keys_0()
  private final val _parser_single_lengths: scala.Array[scala.Byte] = PatternParser.init__parser_single_lengths_0()
  private final val _parser_range_lengths: scala.Array[scala.Byte] = PatternParser.init__parser_range_lengths_0()
  private final val _parser_index_offsets: scala.Array[scala.Short] = PatternParser.init__parser_index_offsets_0()
  private final val _parser_indicies: scala.Array[scala.Byte] = PatternParser.init__parser_indicies_0()
  private final val _parser_trans_targs: scala.Array[scala.Byte] = PatternParser.init__parser_trans_targs_0()
  private final val _parser_trans_actions: scala.Array[scala.Byte] = PatternParser.init__parser_trans_actions_0()
  private final val _parser_eof_actions: scala.Array[scala.Byte] = PatternParser.init__parser_eof_actions_0()
  final val parser_start: scala.Int = 1
  def parse(matcher: com.badlogic.gdx.utils.JsonMatcher, text: java.lang.String, processor: com.badlogic.gdx.utils.JsonMatcher.Processor): com.badlogic.gdx.utils.JsonMatcher.Pattern = {
    val data: scala.Array[scala.Char] = text.toCharArray()
    var cs: scala.Int = 0
    var p: scala.Int = 0
    val pe: scala.Int = data.length
    val eof: scala.Int = pe
    var s: scala.Int = 0
    var e: scala.Int = 0
    var c: scala.Int = -1
    var escaped: scala.Boolean = false
    var quoted: scala.Boolean = false
    var brackets: scala.Boolean = false
    var at: scala.Boolean = false
    var keyCapture: scala.Boolean = false
    var star: scala.Boolean = false
    var starStar: scala.Boolean = false
    val matches: com.badlogic.gdx.utils.Array[com.badlogic.gdx.utils.JsonMatcher.Match] = new com.badlogic.gdx.utils.Array(((size: scala.Int) => new scala.Array[com.badlogic.gdx.utils.JsonMatcher.Match](size))).asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.utils.JsonMatcher.Match]]
    var root: com.badlogic.gdx.utils.JsonMatcher.Node = null
    var prev: com.badlogic.gdx.utils.JsonMatcher.Node = null
    var backtrack: com.badlogic.gdx.utils.JsonMatcher.Node = null
    var processEach: scala.Boolean = false
    var hasCapture: scala.Boolean = false
    try {
      {
        cs = PatternParser.parser_start
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
                _keys = PatternParser._parser_key_offsets(cs)
                _trans = PatternParser._parser_index_offsets(cs)
                _klen = PatternParser._parser_single_lengths(cs)
                if (_klen > 0) {
                  var _lower: scala.Int = _keys
                  var _mid: scala.Int = 0
                  var _upper: scala.Int = (_keys + _klen) - 1
                  while (true) {
                    if (_upper < _lower) {
                      /* break */ ()
                    } else ()
                    _mid = _lower + ((_upper - _lower) >> 1)
                    if (data(p) < PatternParser._parser_trans_keys(_mid)) {
                      _upper = _mid - 1
                    } else {
                      if (data(p) > PatternParser._parser_trans_keys(_mid)) {
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
                _klen = PatternParser._parser_range_lengths(cs)
                if (_klen > 0) {
                  var _lower: scala.Int = _keys
                  var _mid: scala.Int = 0
                  var _upper: scala.Int = (_keys + (_klen << 1)) - 2
                  while (true) {
                    if (_upper < _lower) {
                      /* break */ ()
                    } else ()
                    _mid = _lower + (((_upper - _lower) >> 1) & (~1))
                    if (data(p) < PatternParser._parser_trans_keys(_mid)) {
                      _upper = _mid - 2
                    } else {
                      if (data(p) > PatternParser._parser_trans_keys(_mid + 1)) {
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
              _trans = PatternParser._parser_indicies(_trans)
              cs = PatternParser._parser_trans_targs(_trans)
              if (PatternParser._parser_trans_actions(_trans) != 0) {
                _acts = PatternParser._parser_trans_actions(_trans)
                _nacts = PatternParser._parser_actions({ _acts += 1; _acts }).asInstanceOf[scala.Int]
                while ({ _nacts -= 1; _nacts } > 0) {
                  PatternParser._parser_actions({ _acts += 1; _acts }) match {
                    case 0 => {
                      {
                        s = p
                      }
                    }
                    case 1 => {
                      {
                        e = p
                      }
                    }
                    case 2 => {
                      {
                        escaped = true
                      }
                    }
                    case 3 => {
                      {
                        quoted = true
                      }
                    }
                    case 4 => {
                      {
                        var name: java.lang.String = new java.lang.String(data, s, e - s)
                        if (quoted) {
                          name = name.substring(1, name.length() - 1)
                        } else ()
                        if (escaped) {
                          name = name.replace("''", "'")
                        } else ()
                        val `match`: com.badlogic.gdx.utils.JsonMatcher.Match = matcher.newMatch(name, brackets, at, processEach, c >= 0, keyCapture, star, starStar)
                        matches.add(`match`)
                        if (starStar && ((`match`.flags & com.badlogic.gdx.utils.JsonMatcher.process$field) != 0)) {
                          processEach = true
                        } else ()
                        escaped = false
                        quoted = false
                        brackets = false
                        at = false
                        keyCapture = false
                        star = false
                        starStar = false
                      }
                    }
                    case 5 => {
                      {
                        brackets = true
                        if (c < 0) {
                          throw new java.lang.IllegalArgumentException("[] must be within a capture.")
                        } else ()
                      }
                    }
                    case 6 => {
                      {
                        at = true
                      }
                    }
                    case 7 => {
                      {
                        star = true
                        keyCapture = true
                        hasCapture = true
                      }
                    }
                    case 8 => {
                      {
                        star = true
                      }
                    }
                    case 9 => {
                      {
                        starStar = true
                      }
                    }
                    case 10 => {
                      {
                        c = matches.size
                      }
                    }
                    case 11 => {
                      {
                        c = -1
                        hasCapture = true
                      }
                    }
                    case 12 => {
                      {
                        { var i: scala.Int = c; val n: scala.Int = matches.size; while (i < n) { {
                          matches.get(i).flags = matches.get(i).flags | com.badlogic.gdx.utils.JsonMatcher.process$field
                        }; i = i + 1 } }
                      }
                    }
                    case 13 => {
                      {
                        val node: com.badlogic.gdx.utils.JsonMatcher.Node = matcher.newNode(matches.toArray(), processEach, backtrack, prev)
                        if (node.starStar) {
                          if (matches.size > 1) {
                            throw new java.lang.IllegalArgumentException("** cannot have other matches at the same level.")
                          } else ()
                          backtrack = node
                        } else ()
                        matches.clear()
                        if (root == null) {
                          root = if (node.prev != null) node.prev else node
                        } else ()
                        prev = node
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
                var __acts: scala.Int = PatternParser._parser_eof_actions(cs)
                var __nacts: scala.Int = PatternParser._parser_actions({ __acts += 1; __acts }).asInstanceOf[scala.Int]
                while ({ __nacts -= 1; __nacts } > 0) {
                  PatternParser._parser_actions({ __acts += 1; __acts }) match {
                    case 1 => {
                      {
                        e = p
                      }
                    }
                    case 3 => {
                      {
                        quoted = true
                      }
                    }
                    case 4 => {
                      {
                        var name: java.lang.String = new java.lang.String(data, s, e - s)
                        if (quoted) {
                          name = name.substring(1, name.length() - 1)
                        } else ()
                        if (escaped) {
                          name = name.replace("''", "'")
                        } else ()
                        val `match`: com.badlogic.gdx.utils.JsonMatcher.Match = matcher.newMatch(name, brackets, at, processEach, c >= 0, keyCapture, star, starStar)
                        matches.add(`match`)
                        if (starStar && ((`match`.flags & com.badlogic.gdx.utils.JsonMatcher.process$field) != 0)) {
                          processEach = true
                        } else ()
                        escaped = false
                        quoted = false
                        brackets = false
                        at = false
                        keyCapture = false
                        star = false
                        starStar = false
                      }
                    }
                    case 11 => {
                      {
                        c = -1
                        hasCapture = true
                      }
                    }
                    case 13 => {
                      {
                        val node: com.badlogic.gdx.utils.JsonMatcher.Node = matcher.newNode(matches.toArray(), processEach, backtrack, prev)
                        if (node.starStar) {
                          if (matches.size > 1) {
                            throw new java.lang.IllegalArgumentException("** cannot have other matches at the same level.")
                          } else ()
                          backtrack = node
                        } else ()
                        matches.clear()
                        if (root == null) {
                          root = if (node.prev != null) node.prev else node
                        } else ()
                        prev = node
                      }
                    }
                  }
                }
              } else ()
            }
            case 1 => {
              while ({ {
                _keys = PatternParser._parser_key_offsets(cs)
                _trans = PatternParser._parser_index_offsets(cs)
                _klen = PatternParser._parser_single_lengths(cs)
                if (_klen > 0) {
                  var _lower: scala.Int = _keys
                  var _mid: scala.Int = 0
                  var _upper: scala.Int = (_keys + _klen) - 1
                  while (true) {
                    if (_upper < _lower) {
                      /* break */ ()
                    } else ()
                    _mid = _lower + ((_upper - _lower) >> 1)
                    if (data(p) < PatternParser._parser_trans_keys(_mid)) {
                      _upper = _mid - 1
                    } else {
                      if (data(p) > PatternParser._parser_trans_keys(_mid)) {
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
                _klen = PatternParser._parser_range_lengths(cs)
                if (_klen > 0) {
                  var _lower: scala.Int = _keys
                  var _mid: scala.Int = 0
                  var _upper: scala.Int = (_keys + (_klen << 1)) - 2
                  while (true) {
                    if (_upper < _lower) {
                      /* break */ ()
                    } else ()
                    _mid = _lower + (((_upper - _lower) >> 1) & (~1))
                    if (data(p) < PatternParser._parser_trans_keys(_mid)) {
                      _upper = _mid - 2
                    } else {
                      if (data(p) > PatternParser._parser_trans_keys(_mid + 1)) {
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
              _trans = PatternParser._parser_indicies(_trans)
              cs = PatternParser._parser_trans_targs(_trans)
              if (PatternParser._parser_trans_actions(_trans) != 0) {
                _acts = PatternParser._parser_trans_actions(_trans)
                _nacts = PatternParser._parser_actions({ _acts += 1; _acts }).asInstanceOf[scala.Int]
                while ({ _nacts -= 1; _nacts } > 0) {
                  PatternParser._parser_actions({ _acts += 1; _acts }) match {
                    case 0 => {
                      {
                        s = p
                      }
                    }
                    case 1 => {
                      {
                        e = p
                      }
                    }
                    case 2 => {
                      {
                        escaped = true
                      }
                    }
                    case 3 => {
                      {
                        quoted = true
                      }
                    }
                    case 4 => {
                      {
                        var name: java.lang.String = new java.lang.String(data, s, e - s)
                        if (quoted) {
                          name = name.substring(1, name.length() - 1)
                        } else ()
                        if (escaped) {
                          name = name.replace("''", "'")
                        } else ()
                        val `match`: com.badlogic.gdx.utils.JsonMatcher.Match = matcher.newMatch(name, brackets, at, processEach, c >= 0, keyCapture, star, starStar)
                        matches.add(`match`)
                        if (starStar && ((`match`.flags & com.badlogic.gdx.utils.JsonMatcher.process$field) != 0)) {
                          processEach = true
                        } else ()
                        escaped = false
                        quoted = false
                        brackets = false
                        at = false
                        keyCapture = false
                        star = false
                        starStar = false
                      }
                    }
                    case 5 => {
                      {
                        brackets = true
                        if (c < 0) {
                          throw new java.lang.IllegalArgumentException("[] must be within a capture.")
                        } else ()
                      }
                    }
                    case 6 => {
                      {
                        at = true
                      }
                    }
                    case 7 => {
                      {
                        star = true
                        keyCapture = true
                        hasCapture = true
                      }
                    }
                    case 8 => {
                      {
                        star = true
                      }
                    }
                    case 9 => {
                      {
                        starStar = true
                      }
                    }
                    case 10 => {
                      {
                        c = matches.size
                      }
                    }
                    case 11 => {
                      {
                        c = -1
                        hasCapture = true
                      }
                    }
                    case 12 => {
                      {
                        { var i: scala.Int = c; val n: scala.Int = matches.size; while (i < n) { {
                          matches.get(i).flags = matches.get(i).flags | com.badlogic.gdx.utils.JsonMatcher.process$field
                        }; i = i + 1 } }
                      }
                    }
                    case 13 => {
                      {
                        val node: com.badlogic.gdx.utils.JsonMatcher.Node = matcher.newNode(matches.toArray(), processEach, backtrack, prev)
                        if (node.starStar) {
                          if (matches.size > 1) {
                            throw new java.lang.IllegalArgumentException("** cannot have other matches at the same level.")
                          } else ()
                          backtrack = node
                        } else ()
                        matches.clear()
                        if (root == null) {
                          root = if (node.prev != null) node.prev else node
                        } else ()
                        prev = node
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
                var __acts: scala.Int = PatternParser._parser_eof_actions(cs)
                var __nacts: scala.Int = PatternParser._parser_actions({ __acts += 1; __acts }).asInstanceOf[scala.Int]
                while ({ __nacts -= 1; __nacts } > 0) {
                  PatternParser._parser_actions({ __acts += 1; __acts }) match {
                    case 1 => {
                      {
                        e = p
                      }
                    }
                    case 3 => {
                      {
                        quoted = true
                      }
                    }
                    case 4 => {
                      {
                        var name: java.lang.String = new java.lang.String(data, s, e - s)
                        if (quoted) {
                          name = name.substring(1, name.length() - 1)
                        } else ()
                        if (escaped) {
                          name = name.replace("''", "'")
                        } else ()
                        val `match`: com.badlogic.gdx.utils.JsonMatcher.Match = matcher.newMatch(name, brackets, at, processEach, c >= 0, keyCapture, star, starStar)
                        matches.add(`match`)
                        if (starStar && ((`match`.flags & com.badlogic.gdx.utils.JsonMatcher.process$field) != 0)) {
                          processEach = true
                        } else ()
                        escaped = false
                        quoted = false
                        brackets = false
                        at = false
                        keyCapture = false
                        star = false
                        starStar = false
                      }
                    }
                    case 11 => {
                      {
                        c = -1
                        hasCapture = true
                      }
                    }
                    case 13 => {
                      {
                        val node: com.badlogic.gdx.utils.JsonMatcher.Node = matcher.newNode(matches.toArray(), processEach, backtrack, prev)
                        if (node.starStar) {
                          if (matches.size > 1) {
                            throw new java.lang.IllegalArgumentException("** cannot have other matches at the same level.")
                          } else ()
                          backtrack = node
                        } else ()
                        matches.clear()
                        if (root == null) {
                          root = if (node.prev != null) node.prev else node
                        } else ()
                        prev = node
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
                var __acts: scala.Int = PatternParser._parser_eof_actions(cs)
                var __nacts: scala.Int = PatternParser._parser_actions({ __acts += 1; __acts }).asInstanceOf[scala.Int]
                while ({ __nacts -= 1; __nacts } > 0) {
                  PatternParser._parser_actions({ __acts += 1; __acts }) match {
                    case 1 => {
                      {
                        e = p
                      }
                    }
                    case 3 => {
                      {
                        quoted = true
                      }
                    }
                    case 4 => {
                      {
                        var name: java.lang.String = new java.lang.String(data, s, e - s)
                        if (quoted) {
                          name = name.substring(1, name.length() - 1)
                        } else ()
                        if (escaped) {
                          name = name.replace("''", "'")
                        } else ()
                        val `match`: com.badlogic.gdx.utils.JsonMatcher.Match = matcher.newMatch(name, brackets, at, processEach, c >= 0, keyCapture, star, starStar)
                        matches.add(`match`)
                        if (starStar && ((`match`.flags & com.badlogic.gdx.utils.JsonMatcher.process$field) != 0)) {
                          processEach = true
                        } else ()
                        escaped = false
                        quoted = false
                        brackets = false
                        at = false
                        keyCapture = false
                        star = false
                        starStar = false
                      }
                    }
                    case 11 => {
                      {
                        c = -1
                        hasCapture = true
                      }
                    }
                    case 13 => {
                      {
                        val node: com.badlogic.gdx.utils.JsonMatcher.Node = matcher.newNode(matches.toArray(), processEach, backtrack, prev)
                        if (node.starStar) {
                          if (matches.size > 1) {
                            throw new java.lang.IllegalArgumentException("** cannot have other matches at the same level.")
                          } else ()
                          backtrack = node
                        } else ()
                        matches.clear()
                        if (root == null) {
                          root = if (node.prev != null) node.prev else node
                        } else ()
                        prev = node
                      }
                    }
                  }
                }
              } else ()
            }
            case 4 => {
              if (p == eof) {
                var __acts: scala.Int = PatternParser._parser_eof_actions(cs)
                var __nacts: scala.Int = PatternParser._parser_actions({ __acts += 1; __acts }).asInstanceOf[scala.Int]
                while ({ __nacts -= 1; __nacts } > 0) {
                  PatternParser._parser_actions({ __acts += 1; __acts }) match {
                    case 1 => {
                      {
                        e = p
                      }
                    }
                    case 3 => {
                      {
                        quoted = true
                      }
                    }
                    case 4 => {
                      {
                        var name: java.lang.String = new java.lang.String(data, s, e - s)
                        if (quoted) {
                          name = name.substring(1, name.length() - 1)
                        } else ()
                        if (escaped) {
                          name = name.replace("''", "'")
                        } else ()
                        val `match`: com.badlogic.gdx.utils.JsonMatcher.Match = matcher.newMatch(name, brackets, at, processEach, c >= 0, keyCapture, star, starStar)
                        matches.add(`match`)
                        if (starStar && ((`match`.flags & com.badlogic.gdx.utils.JsonMatcher.process$field) != 0)) {
                          processEach = true
                        } else ()
                        escaped = false
                        quoted = false
                        brackets = false
                        at = false
                        keyCapture = false
                        star = false
                        starStar = false
                      }
                    }
                    case 11 => {
                      {
                        c = -1
                        hasCapture = true
                      }
                    }
                    case 13 => {
                      {
                        val node: com.badlogic.gdx.utils.JsonMatcher.Node = matcher.newNode(matches.toArray(), processEach, backtrack, prev)
                        if (node.starStar) {
                          if (matches.size > 1) {
                            throw new java.lang.IllegalArgumentException("** cannot have other matches at the same level.")
                          } else ()
                          backtrack = node
                        } else ()
                        matches.clear()
                        if (root == null) {
                          root = if (node.prev != null) node.prev else node
                        } else ()
                        prev = node
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
      if (p < pe) {
        val start: scala.Int = java.lang.Math.max(0, p - 32)
        throw new java.lang.IllegalArgumentException((("Error parsing pattern near: " + new java.lang.String(data, start, p - start)) + "ERROR") + new java.lang.String(data, p, java.lang.Math.min(64, pe - p)))
      } else ()
      if (!hasCapture) {
        throw new java.lang.IllegalArgumentException("A capture is required.")
      } else ()
      return matcher.newPattern(root, processor)
    } catch {
      case ex: java.lang.Exception => {
        throw new java.lang.IllegalArgumentException("Error parsing pattern: " + text, ex)
      }
    }
  }
  private def init__parser_actions_0(): scala.Array[scala.Byte] = {
    return scala.Array[scala.Byte](0.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 4.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 5.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 6.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 7.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 9.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 11.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 12.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 8.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 4.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 6.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 4.asInstanceOf[scala.Byte], 13.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 10.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 11.asInstanceOf[scala.Byte], 13.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 4.asInstanceOf[scala.Byte], 13.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 4.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 6.asInstanceOf[scala.Byte], 4.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 4.asInstanceOf[scala.Byte], 13.asInstanceOf[scala.Byte])
  }
  private def init__parser_key_offsets_0(): scala.Array[scala.Byte] = {
    return scala.Array[scala.Byte](0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 9.asInstanceOf[scala.Byte], 10.asInstanceOf[scala.Byte], 11.asInstanceOf[scala.Byte], 12.asInstanceOf[scala.Byte], 21.asInstanceOf[scala.Byte], 29.asInstanceOf[scala.Byte], 38.asInstanceOf[scala.Byte], 39.asInstanceOf[scala.Byte], 44.asInstanceOf[scala.Byte], 45.asInstanceOf[scala.Byte], 47.asInstanceOf[scala.Byte], 48.asInstanceOf[scala.Byte], 51.asInstanceOf[scala.Byte], 52.asInstanceOf[scala.Byte], 56.asInstanceOf[scala.Byte], 61.asInstanceOf[scala.Byte], 68.asInstanceOf[scala.Byte], 70.asInstanceOf[scala.Byte], 73.asInstanceOf[scala.Byte], 78.asInstanceOf[scala.Byte], 81.asInstanceOf[scala.Byte], 83.asInstanceOf[scala.Byte], 87.asInstanceOf[scala.Byte])
  }
  private def init__parser_trans_keys_0(): scala.Array[scala.Char] = {
    return scala.Array[scala.Char](39.asInstanceOf[scala.Char], 40.asInstanceOf[scala.Char], 41.asInstanceOf[scala.Char], 42.asInstanceOf[scala.Char], 44.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 64.asInstanceOf[scala.Char], 91.asInstanceOf[scala.Char], 93.asInstanceOf[scala.Char], 93.asInstanceOf[scala.Char], 39.asInstanceOf[scala.Char], 39.asInstanceOf[scala.Char], 39.asInstanceOf[scala.Char], 40.asInstanceOf[scala.Char], 41.asInstanceOf[scala.Char], 42.asInstanceOf[scala.Char], 44.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 64.asInstanceOf[scala.Char], 91.asInstanceOf[scala.Char], 93.asInstanceOf[scala.Char], 41.asInstanceOf[scala.Char], 44.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 64.asInstanceOf[scala.Char], 91.asInstanceOf[scala.Char], 93.asInstanceOf[scala.Char], 39.asInstanceOf[scala.Char], 42.asInstanceOf[scala.Char], 39.asInstanceOf[scala.Char], 40.asInstanceOf[scala.Char], 41.asInstanceOf[scala.Char], 42.asInstanceOf[scala.Char], 44.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 64.asInstanceOf[scala.Char], 91.asInstanceOf[scala.Char], 93.asInstanceOf[scala.Char], 39.asInstanceOf[scala.Char], 39.asInstanceOf[scala.Char], 41.asInstanceOf[scala.Char], 44.asInstanceOf[scala.Char], 64.asInstanceOf[scala.Char], 91.asInstanceOf[scala.Char], 39.asInstanceOf[scala.Char], 41.asInstanceOf[scala.Char], 44.asInstanceOf[scala.Char], 93.asInstanceOf[scala.Char], 41.asInstanceOf[scala.Char], 44.asInstanceOf[scala.Char], 64.asInstanceOf[scala.Char], 41.asInstanceOf[scala.Char], 41.asInstanceOf[scala.Char], 44.asInstanceOf[scala.Char], 64.asInstanceOf[scala.Char], 91.asInstanceOf[scala.Char], 41.asInstanceOf[scala.Char], 42.asInstanceOf[scala.Char], 44.asInstanceOf[scala.Char], 64.asInstanceOf[scala.Char], 91.asInstanceOf[scala.Char], 44.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 64.asInstanceOf[scala.Char], 91.asInstanceOf[scala.Char], 93.asInstanceOf[scala.Char], 39.asInstanceOf[scala.Char], 42.asInstanceOf[scala.Char], 44.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 44.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 64.asInstanceOf[scala.Char], 39.asInstanceOf[scala.Char], 44.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 64.asInstanceOf[scala.Char], 91.asInstanceOf[scala.Char], 44.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 64.asInstanceOf[scala.Char], 44.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 44.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 64.asInstanceOf[scala.Char], 91.asInstanceOf[scala.Char], 42.asInstanceOf[scala.Char], 44.asInstanceOf[scala.Char], 47.asInstanceOf[scala.Char], 64.asInstanceOf[scala.Char], 91.asInstanceOf[scala.Char], 0.asInstanceOf[scala.Char])
  }
  private def init__parser_single_lengths_0(): scala.Array[scala.Byte] = {
    return scala.Array[scala.Byte](0.asInstanceOf[scala.Byte], 9.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 9.asInstanceOf[scala.Byte], 6.asInstanceOf[scala.Byte], 9.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 5.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 4.asInstanceOf[scala.Byte], 5.asInstanceOf[scala.Byte], 5.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 5.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 4.asInstanceOf[scala.Byte], 5.asInstanceOf[scala.Byte])
  }
  private def init__parser_range_lengths_0(): scala.Array[scala.Byte] = {
    return scala.Array[scala.Byte](0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte])
  }
  private def init__parser_index_offsets_0(): scala.Array[scala.Short] = {
    return scala.Array[scala.Short](0.asInstanceOf[scala.Short], 0.asInstanceOf[scala.Short], 10.asInstanceOf[scala.Short], 12.asInstanceOf[scala.Short], 14.asInstanceOf[scala.Short], 16.asInstanceOf[scala.Short], 26.asInstanceOf[scala.Short], 34.asInstanceOf[scala.Short], 44.asInstanceOf[scala.Short], 46.asInstanceOf[scala.Short], 52.asInstanceOf[scala.Short], 54.asInstanceOf[scala.Short], 57.asInstanceOf[scala.Short], 59.asInstanceOf[scala.Short], 63.asInstanceOf[scala.Short], 65.asInstanceOf[scala.Short], 70.asInstanceOf[scala.Short], 76.asInstanceOf[scala.Short], 83.asInstanceOf[scala.Short], 86.asInstanceOf[scala.Short], 90.asInstanceOf[scala.Short], 96.asInstanceOf[scala.Short], 100.asInstanceOf[scala.Short], 103.asInstanceOf[scala.Short], 108.asInstanceOf[scala.Short])
  }
  private def init__parser_indicies_0(): scala.Array[scala.Byte] = {
    return scala.Array[scala.Byte](1.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 4.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 4.asInstanceOf[scala.Byte], 4.asInstanceOf[scala.Byte], 4.asInstanceOf[scala.Byte], 4.asInstanceOf[scala.Byte], 4.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 5.asInstanceOf[scala.Byte], 4.asInstanceOf[scala.Byte], 7.asInstanceOf[scala.Byte], 6.asInstanceOf[scala.Byte], 9.asInstanceOf[scala.Byte], 8.asInstanceOf[scala.Byte], 11.asInstanceOf[scala.Byte], 12.asInstanceOf[scala.Byte], 13.asInstanceOf[scala.Byte], 14.asInstanceOf[scala.Byte], 4.asInstanceOf[scala.Byte], 4.asInstanceOf[scala.Byte], 4.asInstanceOf[scala.Byte], 4.asInstanceOf[scala.Byte], 4.asInstanceOf[scala.Byte], 10.asInstanceOf[scala.Byte], 16.asInstanceOf[scala.Byte], 17.asInstanceOf[scala.Byte], 4.asInstanceOf[scala.Byte], 18.asInstanceOf[scala.Byte], 19.asInstanceOf[scala.Byte], 4.asInstanceOf[scala.Byte], 4.asInstanceOf[scala.Byte], 15.asInstanceOf[scala.Byte], 11.asInstanceOf[scala.Byte], 12.asInstanceOf[scala.Byte], 4.asInstanceOf[scala.Byte], 14.asInstanceOf[scala.Byte], 4.asInstanceOf[scala.Byte], 4.asInstanceOf[scala.Byte], 4.asInstanceOf[scala.Byte], 4.asInstanceOf[scala.Byte], 4.asInstanceOf[scala.Byte], 10.asInstanceOf[scala.Byte], 21.asInstanceOf[scala.Byte], 20.asInstanceOf[scala.Byte], 22.asInstanceOf[scala.Byte], 23.asInstanceOf[scala.Byte], 24.asInstanceOf[scala.Byte], 25.asInstanceOf[scala.Byte], 26.asInstanceOf[scala.Byte], 4.asInstanceOf[scala.Byte], 28.asInstanceOf[scala.Byte], 27.asInstanceOf[scala.Byte], 29.asInstanceOf[scala.Byte], 30.asInstanceOf[scala.Byte], 4.asInstanceOf[scala.Byte], 31.asInstanceOf[scala.Byte], 4.asInstanceOf[scala.Byte], 29.asInstanceOf[scala.Byte], 30.asInstanceOf[scala.Byte], 32.asInstanceOf[scala.Byte], 4.asInstanceOf[scala.Byte], 33.asInstanceOf[scala.Byte], 4.asInstanceOf[scala.Byte], 16.asInstanceOf[scala.Byte], 17.asInstanceOf[scala.Byte], 18.asInstanceOf[scala.Byte], 19.asInstanceOf[scala.Byte], 4.asInstanceOf[scala.Byte], 16.asInstanceOf[scala.Byte], 34.asInstanceOf[scala.Byte], 17.asInstanceOf[scala.Byte], 18.asInstanceOf[scala.Byte], 19.asInstanceOf[scala.Byte], 4.asInstanceOf[scala.Byte], 36.asInstanceOf[scala.Byte], 37.asInstanceOf[scala.Byte], 38.asInstanceOf[scala.Byte], 39.asInstanceOf[scala.Byte], 4.asInstanceOf[scala.Byte], 4.asInstanceOf[scala.Byte], 35.asInstanceOf[scala.Byte], 40.asInstanceOf[scala.Byte], 41.asInstanceOf[scala.Byte], 4.asInstanceOf[scala.Byte], 40.asInstanceOf[scala.Byte], 41.asInstanceOf[scala.Byte], 42.asInstanceOf[scala.Byte], 4.asInstanceOf[scala.Byte], 43.asInstanceOf[scala.Byte], 44.asInstanceOf[scala.Byte], 45.asInstanceOf[scala.Byte], 46.asInstanceOf[scala.Byte], 47.asInstanceOf[scala.Byte], 4.asInstanceOf[scala.Byte], 48.asInstanceOf[scala.Byte], 49.asInstanceOf[scala.Byte], 50.asInstanceOf[scala.Byte], 4.asInstanceOf[scala.Byte], 48.asInstanceOf[scala.Byte], 49.asInstanceOf[scala.Byte], 4.asInstanceOf[scala.Byte], 36.asInstanceOf[scala.Byte], 37.asInstanceOf[scala.Byte], 38.asInstanceOf[scala.Byte], 39.asInstanceOf[scala.Byte], 4.asInstanceOf[scala.Byte], 51.asInstanceOf[scala.Byte], 36.asInstanceOf[scala.Byte], 37.asInstanceOf[scala.Byte], 38.asInstanceOf[scala.Byte], 39.asInstanceOf[scala.Byte], 4.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte])
  }
  private def init__parser_trans_targs_0(): scala.Array[scala.Byte] = {
    return scala.Array[scala.Byte](17.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 5.asInstanceOf[scala.Byte], 24.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 19.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 20.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 20.asInstanceOf[scala.Byte], 6.asInstanceOf[scala.Byte], 8.asInstanceOf[scala.Byte], 14.asInstanceOf[scala.Byte], 23.asInstanceOf[scala.Byte], 16.asInstanceOf[scala.Byte], 6.asInstanceOf[scala.Byte], 21.asInstanceOf[scala.Byte], 7.asInstanceOf[scala.Byte], 11.asInstanceOf[scala.Byte], 12.asInstanceOf[scala.Byte], 8.asInstanceOf[scala.Byte], 9.asInstanceOf[scala.Byte], 10.asInstanceOf[scala.Byte], 21.asInstanceOf[scala.Byte], 7.asInstanceOf[scala.Byte], 11.asInstanceOf[scala.Byte], 12.asInstanceOf[scala.Byte], 8.asInstanceOf[scala.Byte], 9.asInstanceOf[scala.Byte], 21.asInstanceOf[scala.Byte], 7.asInstanceOf[scala.Byte], 13.asInstanceOf[scala.Byte], 11.asInstanceOf[scala.Byte], 15.asInstanceOf[scala.Byte], 15.asInstanceOf[scala.Byte], 17.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 18.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 18.asInstanceOf[scala.Byte], 4.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 18.asInstanceOf[scala.Byte], 2.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 22.asInstanceOf[scala.Byte], 23.asInstanceOf[scala.Byte])
  }
  private def init__parser_trans_actions_0(): scala.Array[scala.Byte] = {
    return scala.Array[scala.Byte](1.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 36.asInstanceOf[scala.Byte], 21.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 9.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 5.asInstanceOf[scala.Byte], 5.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 1.asInstanceOf[scala.Byte], 13.asInstanceOf[scala.Byte], 21.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 24.asInstanceOf[scala.Byte], 24.asInstanceOf[scala.Byte], 27.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 46.asInstanceOf[scala.Byte], 46.asInstanceOf[scala.Byte], 50.asInstanceOf[scala.Byte], 30.asInstanceOf[scala.Byte], 5.asInstanceOf[scala.Byte], 5.asInstanceOf[scala.Byte], 7.asInstanceOf[scala.Byte], 7.asInstanceOf[scala.Byte], 9.asInstanceOf[scala.Byte], 11.asInstanceOf[scala.Byte], 13.asInstanceOf[scala.Byte], 15.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 24.asInstanceOf[scala.Byte], 42.asInstanceOf[scala.Byte], 27.asInstanceOf[scala.Byte], 3.asInstanceOf[scala.Byte], 7.asInstanceOf[scala.Byte], 33.asInstanceOf[scala.Byte], 11.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 46.asInstanceOf[scala.Byte], 54.asInstanceOf[scala.Byte], 50.asInstanceOf[scala.Byte], 30.asInstanceOf[scala.Byte], 17.asInstanceOf[scala.Byte], 39.asInstanceOf[scala.Byte], 19.asInstanceOf[scala.Byte], 15.asInstanceOf[scala.Byte])
  }
  private def init__parser_eof_actions_0(): scala.Array[scala.Byte] = {
    return scala.Array[scala.Byte](0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 0.asInstanceOf[scala.Byte], 42.asInstanceOf[scala.Byte], 33.asInstanceOf[scala.Byte], 33.asInstanceOf[scala.Byte], 54.asInstanceOf[scala.Byte], 39.asInstanceOf[scala.Byte], 39.asInstanceOf[scala.Byte], 42.asInstanceOf[scala.Byte], 42.asInstanceOf[scala.Byte])
  }
}