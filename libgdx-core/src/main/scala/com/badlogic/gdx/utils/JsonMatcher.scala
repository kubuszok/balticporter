package com.badlogic.gdx.utils

class JsonMatcher extends com.badlogic.gdx.utils.JsonSkimmer {
  var processor: com.badlogic.gdx.utils.JsonMatcher.Processor = null.asInstanceOf[com.badlogic.gdx.utils.JsonMatcher.Processor]
  var patterns: scala.Array[com.badlogic.gdx.utils.JsonMatcher.Pattern] = new Array[com.badlogic.gdx.utils.JsonMatcher.Pattern](0)
  var original: scala.Array[com.badlogic.gdx.utils.JsonMatcher.Pattern] = null.asInstanceOf[scala.Array[com.badlogic.gdx.utils.JsonMatcher.Pattern]]
  var all: scala.Array[com.badlogic.gdx.utils.JsonMatcher.Pattern] = null.asInstanceOf[scala.Array[com.badlogic.gdx.utils.JsonMatcher.Pattern]]
  var total: scala.Int = 0
  var endCaptures: scala.Int = 0
  private var rejected: scala.Boolean = false
  var stoppable: scala.Boolean = true
  var depth$field: scala.Int = 0
  var captured$field: scala.Int = 0
  var chars: scala.Array[scala.Char] = null.asInstanceOf[scala.Array[scala.Char]]
  final val path$field: com.badlogic.gdx.utils.IntArray = new com.badlogic.gdx.utils.IntArray()
  var processPattern: com.badlogic.gdx.utils.JsonMatcher.Pattern = null.asInstanceOf[com.badlogic.gdx.utils.JsonMatcher.Pattern]
  def this(patterns: scala.Array[java.lang.String]) = {
    this()
    for (pattern <- patterns) {
      this.addPattern(pattern)
    }
  }
  def setProcessor(processor: com.badlogic.gdx.utils.JsonMatcher.Processor): scala.Unit = {
    this.processor = processor
  }
  def addPattern(pattern: java.lang.String): scala.Int = {
    return this.addPattern(pattern, null)
  }
  def addPattern(pattern: java.lang.String, processor: com.badlogic.gdx.utils.JsonMatcher.Processor): scala.Int = {
    if (this.chars != null) {
      throw new java.lang.IllegalStateException()
    } else ()
    val newPatterns: scala.Array[com.badlogic.gdx.utils.JsonMatcher.Pattern] = new Array[com.badlogic.gdx.utils.JsonMatcher.Pattern](this.patterns.length + 1)
    java.lang.System.arraycopy(this.patterns, 0, newPatterns, 0, this.patterns.length)
    var newPattern: com.badlogic.gdx.utils.JsonMatcher.Pattern = null.asInstanceOf[com.badlogic.gdx.utils.JsonMatcher.Pattern]
    if (pattern.isEmpty()) {
      newPattern = new com.badlogic.gdx.utils.JsonMatcher.Pattern(new com.badlogic.gdx.utils.JsonMatcher.Node(new Array[com.badlogic.gdx.utils.JsonMatcher.Match](0), false, null), processor, java.lang.Integer.MAX_VALUE, false)
      newPattern.captureRoot = true
      newPattern.captureAll = true
      this.endCaptures = this.endCaptures + 1
    } else {
      newPattern = com.badlogic.gdx.utils.PatternParser.parse(this, pattern, processor)
    }
    newPatterns(this.patterns.length) = newPattern
    this.patterns = newPatterns
    if (JsonMatcher.debug$field) {
      java.lang.System.out.println(newPattern)
    } else ()
    return this.patterns.length - 1
  }
  def parse(data: scala.Array[scala.Char], offset: scala.Int, length: scala.Int): scala.Unit = {
    if (this.chars != null) {
      throw new java.lang.IllegalStateException()
    } else ()
    this.parseStart()
    this.captureRoot()
    this.chars = data
    try {
      super.parse(data, offset, length)
      for (pattern <- this.patterns) {
        this.process(pattern, false)
      }
      this.parseEnd()
    } finally {
      for (pattern <- this.patterns) {
        pattern.reset()
      }
      this.patterns = this.original
      this.depth$field = 0
      this.captured$field = 0
      this.chars = null
      this.path$field.clear()
    }
  }
  private def captureRoot(): scala.Unit = {
    this.original = this.patterns
    if (this.patterns.length == 0) {
      if (this.all == null) {
        this.addPattern("", null)
        this.all = this.patterns
      } else {
        this.patterns = this.all
      }
    } else ()
  }
  def parseStart(): scala.Unit = {
    ()
  }
  def parseEnd(): scala.Unit = {
    ()
  }
  def push(name: com.badlogic.gdx.utils.JsonSkimmer.JsonToken, `object`: scala.Boolean): scala.Unit = {
    if (JsonMatcher.debug$field) {
      this.debug(null, (((("push: " + name) + ":") + (if (`object`) "{}" else "[]")) + ", depth: ") + this.depth$field)
    } else ()
    if (name != null) {
      this.path$field.add(name.start, name.length)
    } else {
      this.path$field.add(if (`object`) 0 else 1, 0)
    }
    if (this.depth$field == 0) {
      for (pattern <- this.patterns) {
        if (pattern.captureRoot) {
          this.captureAllStart(pattern, JsonMatcher.single, null, `object`)
        } else ()
      }
    } else {
      for (pattern <- this.patterns) {
        if (pattern.captureAll) {
          if (JsonMatcher.debug$field) {
            this.debug(pattern, ("current: " + pattern.current) + " CAPTURE ALL")
          } else ()
          val value: com.badlogic.gdx.utils.JsonValue = new com.badlogic.gdx.utils.JsonValue(if (`object`) com.badlogic.gdx.utils.JsonValue.ValueType.`object` else com.badlogic.gdx.utils.JsonValue.ValueType.array)
          this.captureAllValue(pattern, name, value)
          pattern.stack.add(value)
        } else {
          val node: com.badlogic.gdx.utils.JsonMatcher.Node = pattern.current
          if (JsonMatcher.debug$field) {
            this.debug(pattern, ("current: " + node) + (if (this.depth$field <= node.dead) "" else if (node.dead == (-1)) " DONE" else " DEAD"))
          } else ()
          if (this.depth$field <= node.dead) {
            var next: com.badlogic.gdx.utils.JsonMatcher.Node = node.next
            var flags: scala.Int = if (next == null) JsonMatcher.none else next.`match`(name)
            if (flags != JsonMatcher.none) {
              while (true) {
                if ((flags & JsonMatcher.process$field) != 0) {
                  this.process(pattern, true)
                  if (stop$field) {
                    return
                  } else ()
                } else ()
                if ((flags & JsonMatcher.capture) != 0) {
                  if ((flags & JsonMatcher.keys) != 0) {
                    this.captureValue(pattern, flags, null, name.value())
                    this.captured(pattern)
                  } else {
                    this.captureAllStart(pattern, flags, name, `object`)
                  }
                  /* break */ ()
                } else ()
                if (JsonMatcher.debug$field) {
                  this.debug(pattern, (("NEXT: " + next) + ", depth: ") + this.depth$field)
                } else ()
                pattern.current = next
                next.pop = this.depth$field
                val nextNext: com.badlogic.gdx.utils.JsonMatcher.Node = next.next
                if ((!next.starStar) || (nextNext == null)) {
                  /* break */ ()
                } else ()
                flags = nextNext.`match`(name)
                if (flags == JsonMatcher.none) {
                  /* break */ ()
                } else ()
                next = nextNext
                if (JsonMatcher.debug$field) {
                  this.debug(pattern, (("ALSO: " + next) + ", depth: ") + this.depth$field)
                } else ()
              }
            } else {
              if (node.starStar) {
                if (JsonMatcher.debug$field) {
                  this.debug(pattern, (("KEEP: " + node) + ", depth: ") + this.depth$field)
                } else ()
              } else {
                if (node.backtrack != null) {
                  if (JsonMatcher.debug$field) {
                    this.debug(pattern, (("BACKTRACK: " + node.backtrack) + ", depth: ") + this.depth$field)
                  } else ()
                  pattern.current = node.backtrack
                } else {
                  node.dead = this.depth$field
                  if (JsonMatcher.debug$field) {
                    this.debug(pattern, (("DEAD: " + node) + ", depth: ") + this.depth$field)
                  } else ()
                }
              }
            }
          } else ()
        }
      }
    }
    this.depth$field = this.depth$field + 1
  }
  def pop(): scala.Unit = {
    val nextDepth: scala.Int = this.depth$field - 1
    if (JsonMatcher.debug$field) {
      this.debug(null, "pop " + nextDepth)
    } else ()
    for (pattern <- this.patterns) {
      if (pattern.captureAll) {
        pattern.stack.pop()
        if (pattern.stack.notEmpty()) {
          /* continue */ ()
        } else ()
        if (JsonMatcher.debug$field) {
          this.debug(pattern, "CAPTURE ALL END, depth: " + nextDepth)
        } else ()
        pattern.captureAll = pattern.captureRoot
        this.captured(pattern)
      } else ()
      var node: com.badlogic.gdx.utils.JsonMatcher.Node = pattern.current
      if (JsonMatcher.debug$field) {
        this.debug(pattern, (((("current: " + node) + " pop at ") + node.pop) + ", captured: ") + pattern.capture.toJson(com.badlogic.gdx.utils.JsonWriter.OutputType.minimal))
      } else ()
      while (true) {
        if (node.dead == nextDepth) {
          node.dead = java.lang.Integer.MAX_VALUE
        } else ()
        if (node.pop != nextDepth) {
          if (node.processEach) {
            this.process(pattern, true)
            if (stop$field) {
              return
            } else ()
          } else ()
          /* break */ ()
        } else ()
        if (node.processEach || node.processPop) {
          this.process(pattern, true)
          if (stop$field) {
            return
          } else ()
        } else ()
        if (node.prev == null) {
          /* break */ ()
        } else ()
        node = node.prev
        pattern.current = node
      }
    }
    this.depth$field = nextDepth
    this.path$field.size = this.path$field.size - 2
  }
  def value(name: com.badlogic.gdx.utils.JsonSkimmer.JsonToken, value: com.badlogic.gdx.utils.JsonSkimmer.JsonToken): scala.Unit = {
    if (JsonMatcher.debug$field) {
      this.debug(null, (("value: " + name) + "=") + value)
    } else ()
    for (pattern <- this.patterns) {
      if (pattern.captureAll) {
        this.captureAllValue(pattern, name, value.value())
      } else {
        if (this.depth$field <= pattern.current.dead) {
          var next: com.badlogic.gdx.utils.JsonMatcher.Node = pattern.current.next
          var flags: scala.Int = if (next == null) JsonMatcher.none else next.`match`(name)
          if (flags != JsonMatcher.none) {
            while (true) {
              if ((flags & JsonMatcher.process$field) != 0) {
                this.process(pattern, true)
                if (stop$field) {
                  return
                } else ()
              } else ()
              if ((flags & JsonMatcher.capture) != 0) {
                if (JsonMatcher.debug$field) {
                  this.debug(pattern, ((((((("CAPTURE: " + name) + "=") + value) + " (") + (this.captured$field + 1)) + "/") + this.total) + ")")
                } else ()
                if ((flags & JsonMatcher.keys) != 0) {
                  if (name != null) {
                    this.captureValue(pattern, flags, null, name.value())
                    this.captured(pattern)
                  } else ()
                } else {
                  this.captureValue(pattern, flags, name, value.value())
                  this.captured(pattern)
                }
                if ((flags & JsonMatcher.process$field) != 0) {
                  this.process(pattern, true)
                  if (stop$field) {
                    return
                  } else ()
                } else ()
                /* break */ ()
              } else ()
              val nextNext: com.badlogic.gdx.utils.JsonMatcher.Node = next.next
              if ((!next.starStar) || (nextNext == null)) {
                /* break */ ()
              } else ()
              flags = nextNext.`match`(name)
              if (flags == JsonMatcher.none) {
                /* break */ ()
              } else ()
              next = nextNext
              if (JsonMatcher.debug$field) {
                this.debug(pattern, (("ALSO TRY: " + next) + ", depth: ") + this.depth$field)
              } else ()
            }
          } else ()
        } else ()
      }
    }
  }
  private def captureValue(pattern: com.badlogic.gdx.utils.JsonMatcher.Pattern, flags: scala.Int, name: com.badlogic.gdx.utils.JsonSkimmer.JsonToken, value: com.badlogic.gdx.utils.JsonValue): scala.Unit = {
    val capture: com.badlogic.gdx.utils.JsonValue = pattern.capture
    if ((flags & JsonMatcher.single) != 0) {
      capture.name$field = if (name == null) null else name.toString()
      if ((flags & JsonMatcher.array) != 0) {
        if (pattern.captured == 0) {
          capture.setType(com.badlogic.gdx.utils.JsonValue.ValueType.array)
        } else ()
        capture.addChild(value)
      } else {
        capture.set(value)
      }
    } else {
      val key: java.lang.String = if (name == null) "" else name.toString()
      if ((flags & JsonMatcher.array) != 0) {
        var array: com.badlogic.gdx.utils.JsonValue = capture.get(key)
        if (array == null) {
          array = new com.badlogic.gdx.utils.JsonValue(com.badlogic.gdx.utils.JsonValue.ValueType.array)
          capture.addChild(key, array)
        } else ()
        array.addChild(value)
        value.parent$field = array
      } else {
        capture.setChild(key, value)
      }
    }
  }
  private def captured(pattern: com.badlogic.gdx.utils.JsonMatcher.Pattern): scala.Unit = {
    if (this.stoppable && ({ this.captured$field += 1; this.captured$field } >= this.total)) {
      if (JsonMatcher.debug$field) {
        this.debug(null, "END PARSING")
      } else ()
      this.`end`()
    } else ()
    pattern.captured = pattern.captured + 1
    if (pattern.captured >= pattern.total) {
      pattern.current.pop = -1
      pattern.current.dead = -1
      if (JsonMatcher.debug$field) {
        this.debug(pattern, "DONE: " + pattern.current)
      } else ()
    } else ()
  }
  private def captureAllStart(pattern: com.badlogic.gdx.utils.JsonMatcher.Pattern, flags: scala.Int, name: com.badlogic.gdx.utils.JsonSkimmer.JsonToken, `object`: scala.Boolean): scala.Unit = {
    val `type`: com.badlogic.gdx.utils.JsonValue.ValueType = if (`object`) com.badlogic.gdx.utils.JsonValue.ValueType.`object` else com.badlogic.gdx.utils.JsonValue.ValueType.array
    var capture: com.badlogic.gdx.utils.JsonValue = null.asInstanceOf[com.badlogic.gdx.utils.JsonValue]
    if ((flags & (JsonMatcher.single | JsonMatcher.array)) == JsonMatcher.single) {
      capture = pattern.capture
      capture.setType(`type`)
      capture.name$field = if (name == null) null else name.toString()
    } else {
      capture = new com.badlogic.gdx.utils.JsonValue(`type`)
      this.captureValue(pattern, flags, name, capture)
    }
    pattern.stack.add(capture)
    pattern.captureAll = true
    if (JsonMatcher.debug$field) {
      this.debug(pattern, "CAPTURE ALL BEGIN, depth: " + this.depth$field)
    } else ()
  }
  private def captureAllValue(pattern: com.badlogic.gdx.utils.JsonMatcher.Pattern, name: com.badlogic.gdx.utils.JsonSkimmer.JsonToken, value: com.badlogic.gdx.utils.JsonValue): scala.Unit = {
    if (pattern.stack.isEmpty()) {
      pattern.capture.set(value)
      this.captured(pattern)
    } else {
      val parent: com.badlogic.gdx.utils.JsonValue = pattern.stack.peek()
      if (name == null) {
        parent.addChild(value)
      } else {
        parent.setChild(name.toString(), value)
      }
    }
  }
  private def process(pattern: com.badlogic.gdx.utils.JsonMatcher.Pattern, clear: scala.Boolean): scala.Unit = {
    if (pattern.captured == 0) {
      return
    } else ()
    var capture: com.badlogic.gdx.utils.JsonValue = pattern.capture
    if (JsonMatcher.debug$field) {
      this.debug(pattern, "PROCESS: " + capture.toJson(com.badlogic.gdx.utils.JsonWriter.OutputType.minimal))
    } else ()
    this.rejected = false
    this.processPattern = pattern
    try {
      if (pattern.processor != null) {
        pattern.processor.process(capture)
        if (this.rejected) {
          return
        } else ()
      } else ()
      if (this.processor != null) {
        this.processor.process(capture)
        if (this.rejected) {
          return
        } else ()
      } else ()
      this.process(capture)
    } finally {
      if (clear) {
        pattern.clearCapture()
      } else {
        pattern.capture = new com.badlogic.gdx.utils.JsonValue(com.badlogic.gdx.utils.JsonValue.ValueType.`object`)
      }
      this.processPattern = null
    }
  }
  def process(value: com.badlogic.gdx.utils.JsonValue): scala.Unit = {
    ()
  }
  def depth(): scala.Int = {
    return this.depth$field
  }
  def pattern(): scala.Int = {
    { var i: scala.Int = 0; val n: scala.Int = this.patterns.length; while (i < n) { {
      if (this.patterns(i) == this.processPattern) {
        return i
      } else ()
    }; i = i + 1 } }
    return -1
  }
  def stop(): scala.Unit = {
    this.rejected = true
    this.clearAll()
    super.stop()
  }
  def `end`(): scala.Unit = {
    super.stop()
  }
  def reject(): scala.Unit = {
    if (this.processPattern == null) {
      throw new java.lang.IllegalStateException()
    } else ()
    if (JsonMatcher.debug$field) {
      this.debug(this.processPattern, "REJECT" + (this.depth$field - 1))
    } else ()
    this.processPattern.current.dead = this.depth$field - 1
    this.rejected = true
  }
  def reject(patternIndex: scala.Int): scala.Unit = {
    if (this.processPattern == null) {
      throw new java.lang.IllegalStateException()
    } else ()
    if (JsonMatcher.debug$field) {
      this.debug(this.patterns(patternIndex), "REJECT" + (this.depth$field - 1))
    } else ()
    this.patterns(patternIndex).current.dead = this.depth$field - 1
    this.rejected = true
  }
  def rejectAll(): scala.Unit = {
    if (this.processPattern == null) {
      throw new java.lang.IllegalStateException()
    } else ()
    if (JsonMatcher.debug$field) {
      this.debug(null, "REJECT ALL " + (this.depth$field - 1))
    } else ()
    var dead: scala.Int = this.depth$field - 1
    for (pattern <- this.patterns) {
      pattern.current.dead = dead
    }
    this.rejected = true
  }
  def clear(): scala.Unit = {
    if (this.processPattern == null) {
      throw new java.lang.IllegalStateException()
    } else ()
    if (JsonMatcher.debug$field) {
      this.debug(this.processPattern, "CLEAR")
    } else ()
    this.processPattern.clearCapture()
  }
  def clear(patternIndex: scala.Int): scala.Unit = {
    if (this.processPattern == null) {
      throw new java.lang.IllegalStateException()
    } else ()
    if (JsonMatcher.debug$field) {
      this.debug(this.patterns(patternIndex), "CLEAR")
    } else ()
    this.patterns(patternIndex).clearCapture()
  }
  def clearAll(): scala.Unit = {
    if (this.processPattern == null) {
      throw new java.lang.IllegalStateException()
    } else ()
    if (JsonMatcher.debug$field) {
      this.debug(null, "CLEAR ALL")
    } else ()
    for (pattern <- this.patterns) {
      pattern.clearCapture()
    }
  }
  def path(): java.lang.String = {
    this.buffer.size = 0;
    { var i: scala.Int = 0; val n: scala.Int = this.path$field.size; while (i < n) { {
      if (i > 0) {
        buffer.append('/')
      } else ()
      val start: scala.Int = this.path$field.get(i)
      val length: scala.Int = this.path$field.get(i + 1)
      if (length == 0) {
        buffer.append(if (start == 0) "{}" else "[]")
      } else {
        buffer.append(this.chars, start, length)
      }
    }; i = i + 2 } }
    return buffer.toString()
  }
  def parent(): java.lang.String = {
    val n: scala.Int = this.path$field.size
    if (n == 0) {
      return ""
    } else ()
    val start: scala.Int = this.path$field.get(n - 2)
    val length: scala.Int = this.path$field.get(n - 1)
    if (length == 0) {
      return if (start == 0) "{}" else "[]"
    } else ()
    return new java.lang.String(this.chars, start, length)
  }
  def parent(up: scala.Int): java.lang.String = {
    val n: scala.Int = this.path$field.size
    val i: scala.Int = n - (up << 1)
    if (i < 2) {
      return ""
    } else ()
    val start: scala.Int = this.path$field.get(i - 2)
    val length: scala.Int = this.path$field.get(i - 1)
    if (length == 0) {
      return if (start == 0) "{}" else "[]"
    } else ()
    return new java.lang.String(this.chars, start, length)
  }
  def newMatch(name: java.lang.String, brackets: scala.Boolean, at: scala.Boolean, processEach: scala.Boolean, valueCapture: scala.Boolean, keyCapture: scala.Boolean, star: scala.Boolean, starStar: scala.Boolean): com.badlogic.gdx.utils.JsonMatcher.Match = {
    var flags: scala.Int = JsonMatcher.`match`
    if (at || processEach) {
      flags = flags | JsonMatcher.process$field
    } else ()
    if (valueCapture) {
      flags = flags | JsonMatcher.capture
      if (brackets) {
        flags = flags | JsonMatcher.array
      } else ()
      if (processEach) {
        flags = flags | JsonMatcher.single
      } else ()
    } else ()
    if (keyCapture) {
      flags = flags | JsonMatcher.keys
    } else ()
    return new com.badlogic.gdx.utils.JsonMatcher.Match(name, flags, star, starStar)
  }
  def newNode(matches: scala.Array[com.badlogic.gdx.utils.JsonMatcher.Match], processEach: scala.Boolean, backtrack: com.badlogic.gdx.utils.JsonMatcher.Node, prev$arg: com.badlogic.gdx.utils.JsonMatcher.Node): com.badlogic.gdx.utils.JsonMatcher.Node = {
    var prev: com.badlogic.gdx.utils.JsonMatcher.Node = prev$arg
    val node: com.badlogic.gdx.utils.JsonMatcher.Node = new com.badlogic.gdx.utils.JsonMatcher.Node(matches, processEach, backtrack)
    if (prev == null) {
      if (node.starStar) {
        return node
      } else ()
      prev = new com.badlogic.gdx.utils.JsonMatcher.Node(Array[com.badlogic.gdx.utils.JsonMatcher.Match](new com.badlogic.gdx.utils.JsonMatcher.Match(".", 0, false, false)), false, null)
    } else ()
    prev.next = node
    prev.nextStarStar = node.starStar
    node.prev = prev
    return node
  }
  def newPattern(root: com.badlogic.gdx.utils.JsonMatcher.Node, processor: com.badlogic.gdx.utils.JsonMatcher.Processor): com.badlogic.gdx.utils.JsonMatcher.Pattern = {
    var current: com.badlogic.gdx.utils.JsonMatcher.Node = root
    var multi: scala.Boolean = false
    var at: scala.Boolean = false
    var stoppable: scala.Boolean = true
    var captures: scala.Int = 0
    var prevCapture: com.badlogic.gdx.utils.JsonMatcher.Match = null
    while ({ {
      for (`match` <- current.matches) {
        var flags: scala.Int = `match`.flags
        if (((flags & JsonMatcher.array) != 0) || ((`match`.star || `match`.starStar) && ((flags & JsonMatcher.process$field) != 0))) {
          stoppable = false
        } else ()
        if ((flags & (JsonMatcher.capture | JsonMatcher.keys)) != 0) {
          if (prevCapture != null) {
            multi = true
          } else {
            prevCapture = `match`
          }
          if ((flags & JsonMatcher.capture) != 0) {
            captures = captures + 1
          } else ()
        } else ()
        if ((flags & JsonMatcher.process$field) != 0) {
          at = true
          if ((!multi) && (prevCapture != null)) {
            prevCapture.flags = prevCapture.flags | JsonMatcher.single
          } else ()
          multi = false
          prevCapture = null
        } else ()
      }
      current = current.next
    }; current != null }) ()
    if ((!multi) && (prevCapture != null)) {
      prevCapture.flags = prevCapture.flags | JsonMatcher.single
    } else ()
    if (!stoppable) {
      this.stoppable = false
    } else {
      this.total = this.total + captures
    }
    if (at || (!stoppable)) {
      captures = java.lang.Integer.MAX_VALUE
    } else {
      this.endCaptures = this.endCaptures + 1
    }
    return new com.badlogic.gdx.utils.JsonMatcher.Pattern(root, processor, captures, at)
  }
  private def debug(pattern: com.badlogic.gdx.utils.JsonMatcher.Pattern, text: java.lang.String): scala.Unit = {
    { var i: scala.Int = 0; while (i < this.depth$field) { {
      java.lang.System.out.print("  ")
    }; i = i + 1 } };
    { var i: scala.Int = 0; val n: scala.Int = this.patterns.length; while (i < n) { {
      if (this.patterns(i) == pattern) {
        java.lang.System.out.print(("[" + i) + "] ")
      } else ()
    }; i = i + 1 } }
    java.lang.System.out.println(text)
  }
  private def valueStart(): com.badlogic.gdx.utils.JsonValue = {
    this.captureRoot()
    if (this.endCaptures == 0) {
      throw new java.lang.IllegalStateException("Must have at least one pattern without @.")
    } else ()
    if (this.endCaptures == 1) {
      for (pattern <- this.patterns) {
        if (!pattern.at) {
          return pattern.capture
        } else ()
      }
    } else ()
    val value: com.badlogic.gdx.utils.JsonValue = new com.badlogic.gdx.utils.JsonValue(com.badlogic.gdx.utils.JsonValue.ValueType.array)
    for (pattern <- this.patterns) {
      if (!pattern.at) {
        value.addChild(pattern.capture)
      } else ()
    }
    return value
  }
  def parseValue(data: scala.Array[scala.Char], offset: scala.Int, length: scala.Int): com.badlogic.gdx.utils.JsonValue = {
    val value: com.badlogic.gdx.utils.JsonValue = this.valueStart()
    this.parse(data, offset, length)
    return value
  }
  def parseValue(json: java.lang.String): com.badlogic.gdx.utils.JsonValue = {
    val value: com.badlogic.gdx.utils.JsonValue = this.valueStart()
    this.parse(json)
    return value
  }
  def parseValue(reader: java.io.Reader): com.badlogic.gdx.utils.JsonValue = {
    val value: com.badlogic.gdx.utils.JsonValue = this.valueStart()
    this.parse(reader)
    return value
  }
  def parseValue(input: java.io.InputStream): com.badlogic.gdx.utils.JsonValue = {
    val value: com.badlogic.gdx.utils.JsonValue = this.valueStart()
    this.parse(input)
    return value
  }
  def parseValue(file: com.badlogic.gdx.files.FileHandle): com.badlogic.gdx.utils.JsonValue = {
    val value: com.badlogic.gdx.utils.JsonValue = this.valueStart()
    this.parse(file)
    return value
  }
}
object JsonMatcher {
  final val debug$field: scala.Boolean = false
  private final val none: scala.Int = 0
  final val `match`: scala.Int = 1
  final val process$field: scala.Int = 2
  final val capture: scala.Int = 4
  final val array: scala.Int = 8
  final val keys: scala.Int = 16
  final val single: scala.Int = 32
  class Pattern {
    var root: com.badlogic.gdx.utils.JsonMatcher.Node = null.asInstanceOf[com.badlogic.gdx.utils.JsonMatcher.Node]
    var processor: com.badlogic.gdx.utils.JsonMatcher.Processor = null.asInstanceOf[com.badlogic.gdx.utils.JsonMatcher.Processor]
    var capture: com.badlogic.gdx.utils.JsonValue = new com.badlogic.gdx.utils.JsonValue(com.badlogic.gdx.utils.JsonValue.ValueType.`object`)
    var captured: scala.Int = 0
    var total: scala.Int = 0
    var captureAll: scala.Boolean = false
    var captureRoot: scala.Boolean = false
    var at: scala.Boolean = false
    final val stack: com.badlogic.gdx.utils.Array[com.badlogic.gdx.utils.JsonValue] = new com.badlogic.gdx.utils.Array()
    var current: com.badlogic.gdx.utils.JsonMatcher.Node = null.asInstanceOf[com.badlogic.gdx.utils.JsonMatcher.Node]
    def this(root: com.badlogic.gdx.utils.JsonMatcher.Node, processor: com.badlogic.gdx.utils.JsonMatcher.Processor, total: scala.Int, at: scala.Boolean) = {
      this()
      this.root = root
      this.processor = processor
      this.total = total
      this.at = at
      this.current = root
    }
    def clearCapture(): scala.Unit = {
      this.captured = 0
      this.capture.name$field = null
      this.capture.child$field = null
      this.capture.last$field = null
      this.capture.setType(com.badlogic.gdx.utils.JsonValue.ValueType.`object`)
      this.capture.size$field = 0
    }
    def reset(): scala.Unit = {
      var node: com.badlogic.gdx.utils.JsonMatcher.Node = this.root
      while ({ {
        node.dead = java.lang.Integer.MAX_VALUE
        node = node.next
      }; node != null }) ()
      this.clearCapture()
      this.captureAll = this.captureRoot
      this.stack.clear()
      this.current = this.root
    }
    def toString(): java.lang.String = {
      if (!JsonMatcher.debug$field) {
        return super.toString()
      } else ()
      return this.root.toString() + (if (this.total < java.lang.Integer.MAX_VALUE) "!" else "")
    }
  }
  class Node {
    var matches: scala.Array[com.badlogic.gdx.utils.JsonMatcher.Match] = null.asInstanceOf[scala.Array[com.badlogic.gdx.utils.JsonMatcher.Match]]
    var processEach: scala.Boolean = false
    var processPop: scala.Boolean = false
    var starStar: scala.Boolean = false
    var nextStarStar: scala.Boolean = false
    var prev: com.badlogic.gdx.utils.JsonMatcher.Node = null.asInstanceOf[com.badlogic.gdx.utils.JsonMatcher.Node]
    var next: com.badlogic.gdx.utils.JsonMatcher.Node = null.asInstanceOf[com.badlogic.gdx.utils.JsonMatcher.Node]
    var backtrack: com.badlogic.gdx.utils.JsonMatcher.Node = null.asInstanceOf[com.badlogic.gdx.utils.JsonMatcher.Node]
    var pop: scala.Int = 0
    var dead: scala.Int = java.lang.Integer.MAX_VALUE
    def this(matches: scala.Array[com.badlogic.gdx.utils.JsonMatcher.Match], processEach: scala.Boolean, backtrack: com.badlogic.gdx.utils.JsonMatcher.Node) = {
      this()
      this.matches = matches
      this.processEach = processEach
      this.backtrack = backtrack
      for (`match` <- matches) {
        if (`match`.starStar) {
          this.starStar = true
        } else ()
        if ((`match`.flags & JsonMatcher.process$field) != 0) {
          this.processPop = true
        } else ()
      }
    }
    def `match`(name: com.badlogic.gdx.utils.JsonSkimmer.JsonToken): scala.Int = {
      if (name != null) {
        for (`match` <- this.matches) {
          if (`match`.any || name.equalsString(`match`.name)) {
            return `match`.flags
          } else ()
        }
      } else {
        for (`match` <- this.matches) {
          if (`match`.any) {
            return `match`.flags
          } else ()
        }
      }
      return if (this.nextStarStar) this.next.`match`(name) else JsonMatcher.none
    }
    private def toString(buffer: java.lang.StringBuilder): scala.Unit = {
      { var i: scala.Int = 0; val last: scala.Int = this.matches.length - 1; while (i <= last) { {
        this.matches(i).toString(buffer)
        if (i < last) {
          buffer.append(',')
        } else ()
      }; i = i + 1 } }
      if (this.next != null) {
        buffer.append('/')
        this.next.toString(buffer)
      } else ()
    }
    def toString(): java.lang.String = {
      if (!JsonMatcher.debug$field) {
        return super.toString()
      } else ()
      val buffer: java.lang.StringBuilder = new java.lang.StringBuilder()
      this.toString(buffer)
      return buffer.toString()
    }
  }
  class Match {
    var name: java.lang.String = null.asInstanceOf[java.lang.String]
    var flags: scala.Int = 0
    var star: scala.Boolean = false
    var starStar: scala.Boolean = false
    var any: scala.Boolean = false
    def this(name: java.lang.String, flags: scala.Int, star: scala.Boolean, starStar: scala.Boolean) = {
      this()
      this.name = name
      this.flags = flags
      this.star = star
      this.starStar = starStar
      this.any = star || starStar
    }
    def toString(buffer: java.lang.StringBuilder): scala.Unit = {
      if ((this.flags & JsonMatcher.capture) != 0) {
        buffer.append('(')
      } else ()
      buffer.append(this.name)
      if ((this.flags & JsonMatcher.array) != 0) {
        buffer.append("[]")
      } else ()
      if ((this.flags & JsonMatcher.process$field) != 0) {
        buffer.append('@')
      } else ()
      if ((this.flags & JsonMatcher.capture) != 0) {
        buffer.append(')')
      } else ()
      if ((this.flags & JsonMatcher.single) != 0) {
        buffer.append('1')
      } else ()
    }
    def toString(): java.lang.String = {
      if (!JsonMatcher.debug$field) {
        return super.toString()
      } else ()
      val buffer: java.lang.StringBuilder = new java.lang.StringBuilder()
      this.toString(buffer)
      return buffer.toString()
    }
  }
  trait Processor {
    def process(value: com.badlogic.gdx.utils.JsonValue): scala.Unit
  }
}