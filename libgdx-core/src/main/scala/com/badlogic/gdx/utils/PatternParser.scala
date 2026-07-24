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
    val matches: com.badlogic.gdx.utils.Array[com.badlogic.gdx.utils.JsonMatcher.Match] = new com.badlogic.gdx.utils.Array((() => new scala.Array[com.badlogic.gdx.utils.JsonMatcher.Match]()))
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
    return Array[scala.Byte](0, 1, 0, 1, 1, 1, 2, 1, 4, 1, 5, 1, 6, 1, 7, 1, 9, 1, 11, 1, 12, 2, 0, 8, 2, 1, 4, 2, 1, 6, 2, 3, 1, 2, 4, 13, 2, 10, 0, 2, 11, 13, 3, 1, 4, 13, 3, 3, 1, 4, 3, 3, 1, 6, 4, 3, 1, 4, 13)
  }
  private def init__parser_key_offsets_0(): scala.Array[scala.Byte] = {
    return Array[scala.Byte](0, 0, 9, 10, 11, 12, 21, 29, 38, 39, 44, 45, 47, 48, 51, 52, 56, 61, 68, 70, 73, 78, 81, 83, 87)
  }
  private def init__parser_trans_keys_0(): scala.Array[scala.Char] = {
    return Array[scala.Char](39, 40, 41, 42, 44, 47, 64, 91, 93, 93, 39, 39, 39, 40, 41, 42, 44, 47, 64, 91, 93, 41, 44, 47, 64, 91, 93, 39, 42, 39, 40, 41, 42, 44, 47, 64, 91, 93, 39, 39, 41, 44, 64, 91, 39, 41, 44, 93, 41, 44, 64, 41, 41, 44, 64, 91, 41, 42, 44, 64, 91, 44, 47, 64, 91, 93, 39, 42, 44, 47, 44, 47, 64, 39, 44, 47, 64, 91, 44, 47, 64, 44, 47, 44, 47, 64, 91, 42, 44, 47, 64, 91, 0)
  }
  private def init__parser_single_lengths_0(): scala.Array[scala.Byte] = {
    return Array[scala.Byte](0, 9, 1, 1, 1, 9, 6, 9, 1, 5, 1, 2, 1, 3, 1, 4, 5, 5, 2, 3, 5, 3, 2, 4, 5)
  }
  private def init__parser_range_lengths_0(): scala.Array[scala.Byte] = {
    return Array[scala.Byte](0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0)
  }
  private def init__parser_index_offsets_0(): scala.Array[scala.Short] = {
    return Array[scala.Short](0, 0, 10, 12, 14, 16, 26, 34, 44, 46, 52, 54, 57, 59, 63, 65, 70, 76, 83, 86, 90, 96, 100, 103, 108)
  }
  private def init__parser_indicies_0(): scala.Array[scala.Byte] = {
    return Array[scala.Byte](1, 2, 4, 3, 4, 4, 4, 4, 4, 0, 5, 4, 7, 6, 9, 8, 11, 12, 13, 14, 4, 4, 4, 4, 4, 10, 16, 17, 4, 18, 19, 4, 4, 15, 11, 12, 4, 14, 4, 4, 4, 4, 4, 10, 21, 20, 22, 23, 24, 25, 26, 4, 28, 27, 29, 30, 4, 31, 4, 29, 30, 32, 4, 33, 4, 16, 17, 18, 19, 4, 16, 34, 17, 18, 19, 4, 36, 37, 38, 39, 4, 4, 35, 40, 41, 4, 40, 41, 42, 4, 43, 44, 45, 46, 47, 4, 48, 49, 50, 4, 48, 49, 4, 36, 37, 38, 39, 4, 51, 36, 37, 38, 39, 4, 0)
  }
  private def init__parser_trans_targs_0(): scala.Array[scala.Byte] = {
    return Array[scala.Byte](17, 3, 5, 24, 0, 19, 3, 20, 3, 20, 6, 8, 14, 23, 16, 6, 21, 7, 11, 12, 8, 9, 10, 21, 7, 11, 12, 8, 9, 21, 7, 13, 11, 15, 15, 17, 1, 1, 18, 2, 1, 1, 18, 4, 1, 1, 18, 2, 1, 1, 22, 23)
  }
  private def init__parser_trans_actions_0(): scala.Array[scala.Byte] = {
    return Array[scala.Byte](1, 1, 36, 21, 0, 9, 0, 0, 5, 5, 1, 1, 1, 13, 21, 0, 24, 24, 27, 3, 0, 0, 0, 46, 46, 50, 30, 5, 5, 7, 7, 9, 11, 13, 15, 0, 24, 42, 27, 3, 7, 33, 11, 0, 46, 54, 50, 30, 17, 39, 19, 15)
  }
  private def init__parser_eof_actions_0(): scala.Array[scala.Byte] = {
    return Array[scala.Byte](0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 42, 33, 33, 54, 39, 39, 42, 42)
  }
}