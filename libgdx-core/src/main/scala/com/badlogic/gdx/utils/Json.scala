package com.badlogic.gdx.utils

class Json {
  private var writer: com.badlogic.gdx.utils.JsonWriter = null.asInstanceOf[com.badlogic.gdx.utils.JsonWriter]
  private var reader: com.badlogic.gdx.utils.JsonReader = new com.badlogic.gdx.utils.JsonReader()
  private var typeName: java.lang.String = "class"
  private var usePrototypes: scala.Boolean = true
  private var outputType: com.badlogic.gdx.utils.JsonWriter.OutputType = null.asInstanceOf[com.badlogic.gdx.utils.JsonWriter.OutputType]
  private var quoteLongValues: scala.Boolean = false
  private var ignoreUnknownFields: scala.Boolean = false
  private var ignoreDeprecated: scala.Boolean = false
  private var readDeprecated: scala.Boolean = false
  private var enumNames: scala.Boolean = true
  var sortFields$field: scala.Boolean = false
  private var defaultSerializer: com.badlogic.gdx.utils.Json.Serializer[?] = null.asInstanceOf[com.badlogic.gdx.utils.Json.Serializer[?]]
  private final val typeToFields: com.badlogic.gdx.utils.ObjectMap[java.lang.Class[?], com.badlogic.gdx.utils.OrderedMap[java.lang.String, com.badlogic.gdx.utils.Json.FieldMetadata]] = new com.badlogic.gdx.utils.ObjectMap()
  private final val tagToClass: com.badlogic.gdx.utils.ObjectMap[java.lang.String, java.lang.Class[?]] = new com.badlogic.gdx.utils.ObjectMap()
  private final val classToTag: com.badlogic.gdx.utils.ObjectMap[java.lang.Class[?], java.lang.String] = new com.badlogic.gdx.utils.ObjectMap()
  private final val classToSerializer: com.badlogic.gdx.utils.ObjectMap[java.lang.Class[?], com.badlogic.gdx.utils.Json.Serializer[?]] = new com.badlogic.gdx.utils.ObjectMap()
  private final val classToDefaultValues: com.badlogic.gdx.utils.ObjectMap[java.lang.Class[?], scala.Array[java.lang.Object]] = new com.badlogic.gdx.utils.ObjectMap()
  private final val equals1: scala.Array[java.lang.Object] = scala.Array[java.lang.Object](null)
  private final val equals2: scala.Array[java.lang.Object] = scala.Array[java.lang.Object](null)
  def this(outputType: com.badlogic.gdx.utils.JsonWriter.OutputType) = {
    this()
    this.outputType = outputType
  }
  this.outputType = com.badlogic.gdx.utils.JsonWriter.OutputType.minimal
  def setIgnoreUnknownFields(ignoreUnknownFields: scala.Boolean): scala.Unit = {
    this.ignoreUnknownFields = ignoreUnknownFields
  }
  def getIgnoreUnknownFields(): scala.Boolean = {
    return this.ignoreUnknownFields
  }
  def setIgnoreDeprecated(ignoreDeprecated: scala.Boolean): scala.Unit = {
    this.ignoreDeprecated = ignoreDeprecated
  }
  def setReadDeprecated(readDeprecated: scala.Boolean): scala.Unit = {
    this.readDeprecated = readDeprecated
  }
  def setOutputType(outputType: com.badlogic.gdx.utils.JsonWriter.OutputType): scala.Unit = {
    this.outputType = outputType
  }
  def setQuoteLongValues(quoteLongValues: scala.Boolean): scala.Unit = {
    this.quoteLongValues = quoteLongValues
  }
  def setEnumNames(enumNames: scala.Boolean): scala.Unit = {
    this.enumNames = enumNames
  }
  def addClassTag(tag: java.lang.String, `type`: java.lang.Class[?]): scala.Unit = {
    this.tagToClass.put(tag, `type`)
    this.classToTag.put(`type`, tag)
  }
  def getClass(tag: java.lang.String): java.lang.Class[?] = {
    return this.tagToClass.get(tag)
  }
  def getTag(`type`: java.lang.Class[?]): java.lang.String = {
    return this.classToTag.get(`type`)
  }
  def setTypeName(typeName: java.lang.String): scala.Unit = {
    this.typeName = typeName
  }
  def setDefaultSerializer(defaultSerializer: com.badlogic.gdx.utils.Json.Serializer[?]): scala.Unit = {
    this.defaultSerializer = defaultSerializer
  }
  def setSerializer[T](`type`: java.lang.Class[T], serializer: com.badlogic.gdx.utils.Json.Serializer[T]): scala.Unit = {
    this.classToSerializer.put(`type`, serializer)
  }
  def getSerializer[T](`type`: java.lang.Class[T]): com.badlogic.gdx.utils.Json.Serializer[T] = {
    return this.classToSerializer.get(`type`)
  }
  def setUsePrototypes(usePrototypes: scala.Boolean): scala.Unit = {
    this.usePrototypes = usePrototypes
  }
  def setElementType(`type`: java.lang.Class[?], fieldName: java.lang.String, elementType: java.lang.Class[?]): scala.Unit = {
    val metadata: com.badlogic.gdx.utils.Json.FieldMetadata = this.getFields(`type`).get(fieldName)
    if (metadata == null) {
      throw new com.badlogic.gdx.utils.SerializationException(((("Field not found: " + fieldName) + " (") + `type`.getName()) + ")")
    } else ()
    metadata.elementType = elementType
  }
  def setDeprecated(`type`: java.lang.Class[?], fieldName: java.lang.String, deprecated: scala.Boolean): scala.Unit = {
    val metadata: com.badlogic.gdx.utils.Json.FieldMetadata = this.getFields(`type`).get(fieldName)
    if (metadata == null) {
      throw new com.badlogic.gdx.utils.SerializationException(((("Field not found: " + fieldName) + " (") + `type`.getName()) + ")")
    } else ()
    metadata.deprecated = deprecated
  }
  def setSortFields(sortFields: scala.Boolean): scala.Unit = {
    this.sortFields$field = sortFields
  }
  def sortFields(`type`: java.lang.Class[?], fieldNames: com.badlogic.gdx.utils.Array[java.lang.String]): scala.Unit = {
    if (this.sortFields$field) {
      fieldNames.sort()
    } else ()
  }
  private def getFields(`type`: java.lang.Class[?]): com.badlogic.gdx.utils.OrderedMap[java.lang.String, com.badlogic.gdx.utils.Json.FieldMetadata] = {
    val fields: com.badlogic.gdx.utils.OrderedMap[java.lang.String, com.badlogic.gdx.utils.Json.FieldMetadata] = this.typeToFields.get(`type`)
    if (fields != null) {
      return fields
    } else ()
    val classHierarchy: com.badlogic.gdx.utils.Array[java.lang.Class[?]] = new com.badlogic.gdx.utils.Array()
    var nextClass: java.lang.Class[?] = `type`
    while (nextClass != classOf[java.lang.Object]) {
      classHierarchy.add(nextClass)
      nextClass = nextClass.getSuperclass()
    }
    val allFields: scala.collection.mutable.ArrayBuffer[com.badlogic.gdx.utils.reflect.Field] = new scala.collection.mutable.ArrayBuffer();
    { var i: scala.Int = classHierarchy.size - 1; while (i >= 0) { {
      java.util.Collections.addAll(allFields, com.badlogic.gdx.utils.reflect.ClassReflection.getDeclaredFields(classHierarchy.get(i)).asInstanceOf[scala.Array[java.lang.Object]])
    }; i = i - 1 } }
    val nameToField: com.badlogic.gdx.utils.OrderedMap[java.lang.String, com.badlogic.gdx.utils.Json.FieldMetadata] = new com.badlogic.gdx.utils.OrderedMap(allFields.size);
    { var i: scala.Int = 0; val n: scala.Int = allFields.size; while (i < n) { {
      val field: com.badlogic.gdx.utils.reflect.Field = allFields(i)
      if (field.isTransient()) {
        /* continue */ ()
      } else ()
      if (field.isStatic()) {
        /* continue */ ()
      } else ()
      if (field.isSynthetic()) {
        /* continue */ ()
      } else ()
      if (!field.isAccessible()) {
        try {
          field.setAccessible(true)
        } catch {
          case ex: java.lang.RuntimeException => {
            /* continue */ ()
          }
        }
      } else ()
      nameToField.put(field.getName(), new com.badlogic.gdx.utils.Json.FieldMetadata(field))
    }; i = i + 1 } }
    this.sortFields(`type`, nameToField.keys$field)
    this.typeToFields.put(`type`, nameToField)
    return nameToField
  }
  def toJson(`object`: java.lang.Object): java.lang.String = {
    return this.toJson(`object`, if (`object` == null) null.asInstanceOf[java.lang.Class[?]] else `object`.getClass(), null.asInstanceOf[java.lang.Class[?]])
  }
  def toJson(`object`: java.lang.Object, knownType: java.lang.Class[?]): java.lang.String = {
    return this.toJson(`object`, knownType, null.asInstanceOf[java.lang.Class[?]])
  }
  def toJson(`object`: java.lang.Object, knownType: java.lang.Class[?], elementType: java.lang.Class[?]): java.lang.String = {
    val buffer: java.io.StringWriter = new java.io.StringWriter()
    this.toJson(`object`, knownType, elementType, buffer)
    return buffer.toString()
  }
  def toJson(`object`: java.lang.Object, file: com.badlogic.gdx.files.FileHandle): scala.Unit = {
    this.toJson(`object`, if (`object` == null) null.asInstanceOf[java.lang.Class[?]] else `object`.getClass(), null, file)
  }
  def toJson(`object`: java.lang.Object, knownType: java.lang.Class[?], file: com.badlogic.gdx.files.FileHandle): scala.Unit = {
    this.toJson(`object`, knownType, null, file)
  }
  def toJson(`object`: java.lang.Object, knownType: java.lang.Class[?], elementType: java.lang.Class[?], file: com.badlogic.gdx.files.FileHandle): scala.Unit = {
    var writer: java.io.Writer = null
    try {
      writer = file.writer(false, "UTF-8")
      this.toJson(`object`, knownType, elementType, writer)
    } catch {
      case ex: java.lang.Exception => {
        throw new com.badlogic.gdx.utils.SerializationException("Error writing file: " + file, ex)
      }
    } finally {
      com.badlogic.gdx.utils.StreamUtils.closeQuietly(writer)
    }
  }
  def toJson(`object`: java.lang.Object, writer: java.io.Writer): scala.Unit = {
    this.toJson(`object`, if (`object` == null) null.asInstanceOf[java.lang.Class[?]] else `object`.getClass(), null, writer)
  }
  def toJson(`object`: java.lang.Object, knownType: java.lang.Class[?], writer: java.io.Writer): scala.Unit = {
    this.toJson(`object`, knownType, null, writer)
  }
  def toJson(`object`: java.lang.Object, knownType: java.lang.Class[?], elementType: java.lang.Class[?], writer: java.io.Writer): scala.Unit = {
    this.setWriter(writer)
    try {
      this.writeValue(`object`, knownType, elementType)
    } finally {
      com.badlogic.gdx.utils.StreamUtils.closeQuietly(this.writer)
      this.writer = null
    }
  }
  def setWriter(writer$arg: java.io.Writer): scala.Unit = {
    var writer: java.io.Writer = writer$arg
    if (!writer.isInstanceOf[com.badlogic.gdx.utils.JsonWriter]) {
      writer = new com.badlogic.gdx.utils.JsonWriter(writer)
    } else ()
    this.writer = writer.asInstanceOf[com.badlogic.gdx.utils.JsonWriter]
    this.writer.setOutputType(this.outputType)
    this.writer.setQuoteLongValues(this.quoteLongValues)
  }
  def getWriter(): com.badlogic.gdx.utils.JsonWriter = {
    return this.writer
  }
  def setReader(reader: com.badlogic.gdx.utils.JsonReader): scala.Unit = {
    this.reader = reader
  }
  def getReader(): com.badlogic.gdx.utils.JsonReader = {
    return this.reader
  }
  def writeFields(`object`: java.lang.Object): scala.Unit = {
    val `type`: java.lang.Class[?] = `object`.getClass()
    val defaultValues: scala.Array[java.lang.Object] = this.getDefaultValues(`type`)
    val fields: com.badlogic.gdx.utils.OrderedMap[java.lang.String, com.badlogic.gdx.utils.Json.FieldMetadata] = this.getFields(`type`)
    var defaultIndex: scala.Int = 0
    val fieldNames: com.badlogic.gdx.utils.Array[java.lang.String] = fields.orderedKeys();
    { var i: scala.Int = 0; val n: scala.Int = fieldNames.size; while (i < n) { {
      val metadata: com.badlogic.gdx.utils.Json.FieldMetadata = fields.get(fieldNames.get(i))
      if (this.ignoreDeprecated && metadata.deprecated) {
        /* continue */ ()
      } else ()
      val field: com.badlogic.gdx.utils.reflect.Field = metadata.field
      try {
        val value: java.lang.Object = field.get(`object`)
        if (defaultValues != null) {
          val defaultValue: java.lang.Object = defaultValues({ defaultIndex += 1; defaultIndex })
          if ((value == null) && (defaultValue == null)) {
            /* continue */ ()
          } else ()
          if ((value != null) && (defaultValue != null)) {
            if (value.equals(defaultValue)) {
              /* continue */ ()
            } else ()
            if (value.getClass().isArray() && defaultValue.getClass().isArray()) {
              this.equals1(0) = value
              this.equals2(0) = defaultValue
              if (java.util.Arrays.deepEquals(this.equals1, this.equals2)) {
                /* continue */ ()
              } else ()
            } else ()
          } else ()
        } else ()
        if (Json.debug) {
          java.lang.System.out.println(((("Writing field: " + field.getName()) + " (") + `type`.getName()) + ")")
        } else ()
        this.writer.name(field.getName())
        this.writeValue(value, field.getType(), metadata.elementType)
      } catch {
        case ex: com.badlogic.gdx.utils.reflect.ReflectionException => {
          throw new com.badlogic.gdx.utils.SerializationException(((("Error accessing field: " + field.getName()) + " (") + `type`.getName()) + ")", ex)
        }
        case ex: com.badlogic.gdx.utils.SerializationException => {
          ex.addTrace(((java.lang.String.valueOf(field) + " (") + `type`.getName()) + ")")
          throw ex
        }
        case runtimeEx: java.lang.Exception => {
          val ex: com.badlogic.gdx.utils.SerializationException = new com.badlogic.gdx.utils.SerializationException(runtimeEx)
          ex.addTrace(((java.lang.String.valueOf(field) + " (") + `type`.getName()) + ")")
          throw ex
        }
      }
    }; i = i + 1 } }
  }
  private def getDefaultValues(`type`: java.lang.Class[?]): scala.Array[java.lang.Object] = {
    if (!this.usePrototypes) {
      return null
    } else ()
    if (this.classToDefaultValues.containsKey(`type`)) {
      return this.classToDefaultValues.get(`type`)
    } else ()
    var `object`: java.lang.Object = null.asInstanceOf[java.lang.Object]
    try {
      `object` = this.newInstance(`type`)
    } catch {
      case ex: java.lang.Exception => {
        this.classToDefaultValues.put(`type`, null)
        return null
      }
    }
    val fields: com.badlogic.gdx.utils.OrderedMap[java.lang.String, com.badlogic.gdx.utils.Json.FieldMetadata] = this.getFields(`type`)
    val values: scala.Array[java.lang.Object] = new scala.Array[java.lang.Object](fields.size)
    this.classToDefaultValues.put(`type`, values)
    var defaultIndex: scala.Int = 0
    val fieldNames: com.badlogic.gdx.utils.Array[java.lang.String] = fields.orderedKeys();
    { var i: scala.Int = 0; val n: scala.Int = fieldNames.size; while (i < n) { {
      val metadata: com.badlogic.gdx.utils.Json.FieldMetadata = fields.get(fieldNames.get(i))
      if (this.ignoreDeprecated && metadata.deprecated) {
        /* continue */ ()
      } else ()
      val field: com.badlogic.gdx.utils.reflect.Field = metadata.field
      try {
        values({ defaultIndex += 1; defaultIndex }) = field.get(`object`)
      } catch {
        case ex: com.badlogic.gdx.utils.reflect.ReflectionException => {
          throw new com.badlogic.gdx.utils.SerializationException(((("Error accessing field: " + field.getName()) + " (") + `type`.getName()) + ")", ex)
        }
        case ex: com.badlogic.gdx.utils.SerializationException => {
          ex.addTrace(((java.lang.String.valueOf(field) + " (") + `type`.getName()) + ")")
          throw ex
        }
        case runtimeEx: java.lang.RuntimeException => {
          val ex: com.badlogic.gdx.utils.SerializationException = new com.badlogic.gdx.utils.SerializationException(runtimeEx)
          ex.addTrace(((java.lang.String.valueOf(field) + " (") + `type`.getName()) + ")")
          throw ex
        }
      }
    }; i = i + 1 } }
    return values
  }
  def writeField(`object`: java.lang.Object, name: java.lang.String): scala.Unit = {
    this.writeField(`object`, name, name, null)
  }
  def writeField(`object`: java.lang.Object, name: java.lang.String, elementType: java.lang.Class[?]): scala.Unit = {
    this.writeField(`object`, name, name, elementType)
  }
  def writeField(`object`: java.lang.Object, fieldName: java.lang.String, jsonName: java.lang.String): scala.Unit = {
    this.writeField(`object`, fieldName, jsonName, null)
  }
  def writeField(`object`: java.lang.Object, fieldName: java.lang.String, jsonName: java.lang.String, elementType$arg: java.lang.Class[?]): scala.Unit = {
    var elementType: java.lang.Class[?] = elementType$arg
    val `type`: java.lang.Class[?] = `object`.getClass()
    val metadata: com.badlogic.gdx.utils.Json.FieldMetadata = this.getFields(`type`).get(fieldName)
    if (metadata == null) {
      throw new com.badlogic.gdx.utils.SerializationException(((("Field not found: " + fieldName) + " (") + `type`.getName()) + ")")
    } else ()
    val field: com.badlogic.gdx.utils.reflect.Field = metadata.field
    if (elementType == null) {
      elementType = metadata.elementType
    } else ()
    try {
      if (Json.debug) {
        java.lang.System.out.println(((("Writing field: " + field.getName()) + " (") + `type`.getName()) + ")")
      } else ()
      this.writer.name(jsonName)
      this.writeValue(field.get(`object`), field.getType(), elementType)
    } catch {
      case ex: com.badlogic.gdx.utils.reflect.ReflectionException => {
        throw new com.badlogic.gdx.utils.SerializationException(((("Error accessing field: " + field.getName()) + " (") + `type`.getName()) + ")", ex)
      }
      case ex: com.badlogic.gdx.utils.SerializationException => {
        ex.addTrace(((java.lang.String.valueOf(field) + " (") + `type`.getName()) + ")")
        throw ex
      }
      case runtimeEx: java.lang.Exception => {
        val ex: com.badlogic.gdx.utils.SerializationException = new com.badlogic.gdx.utils.SerializationException(runtimeEx)
        ex.addTrace(((java.lang.String.valueOf(field) + " (") + `type`.getName()) + ")")
        throw ex
      }
    }
  }
  def writeValue(name: java.lang.String, value: java.lang.Object): scala.Unit = {
    try {
      this.writer.name(name)
    } catch {
      case ex: java.io.IOException => {
        throw new com.badlogic.gdx.utils.SerializationException(ex)
      }
    }
    if (value == null) {
      this.writeValue(value, null, null)
    } else {
      this.writeValue(value, value.getClass(), null)
    }
  }
  def writeValue(name: java.lang.String, value: java.lang.Object, knownType: java.lang.Class[?]): scala.Unit = {
    try {
      this.writer.name(name)
    } catch {
      case ex: java.io.IOException => {
        throw new com.badlogic.gdx.utils.SerializationException(ex)
      }
    }
    this.writeValue(value, knownType, null)
  }
  def writeValue(name: java.lang.String, value: java.lang.Object, knownType: java.lang.Class[?], elementType: java.lang.Class[?]): scala.Unit = {
    try {
      this.writer.name(name)
    } catch {
      case ex: java.io.IOException => {
        throw new com.badlogic.gdx.utils.SerializationException(ex)
      }
    }
    this.writeValue(value, knownType, elementType)
  }
  def writeValue(value: java.lang.Object): scala.Unit = {
    if (value == null) {
      this.writeValue(value, null, null)
    } else {
      this.writeValue(value, value.getClass(), null)
    }
  }
  def writeValue(value: java.lang.Object, knownType: java.lang.Class[?]): scala.Unit = {
    this.writeValue(value, knownType, null)
  }
  def writeValue(value: java.lang.Object, knownType$arg: java.lang.Class[?], elementType$arg: java.lang.Class[?]): scala.Unit = {
    var knownType: java.lang.Class[?] = knownType$arg
    var elementType: java.lang.Class[?] = elementType$arg
    try {
      if (value == null) {
        this.writer.value(null)
        return
      } else ()
      if (((((((((((knownType != null) && knownType.isPrimitive()) || (knownType == classOf[java.lang.String])) || (knownType == classOf[java.lang.Integer])) || (knownType == classOf[java.lang.Boolean])) || (knownType == classOf[java.lang.Float])) || (knownType == classOf[java.lang.Long])) || (knownType == classOf[java.lang.Double])) || (knownType == classOf[java.lang.Short])) || (knownType == classOf[java.lang.Byte])) || (knownType == classOf[java.lang.Character])) {
        this.writer.value(value)
        return
      } else ()
      var actualType: java.lang.Class[?] = value.getClass()
      if (((((((((actualType.isPrimitive() || (actualType == classOf[java.lang.String])) || (actualType == classOf[java.lang.Integer])) || (actualType == classOf[java.lang.Boolean])) || (actualType == classOf[java.lang.Float])) || (actualType == classOf[java.lang.Long])) || (actualType == classOf[java.lang.Double])) || (actualType == classOf[java.lang.Short])) || (actualType == classOf[java.lang.Byte])) || (actualType == classOf[java.lang.Character])) {
        this.writeObjectStart(actualType, null)
        this.writeValue("value", value)
        this.writeObjectEnd()
        return
      } else ()
      if (value.isInstanceOf[com.badlogic.gdx.utils.Json.Serializable]) {
        this.writeObjectStart(actualType, knownType)
        value.asInstanceOf[com.badlogic.gdx.utils.Json.Serializable].write(this)
        this.writeObjectEnd()
        return
      } else ()
      val serializer: com.badlogic.gdx.utils.Json.Serializer[?] = this.classToSerializer.get(actualType)
      if (serializer != null) {
        serializer.write(this, value, knownType)
        return
      } else ()
      if (value.isInstanceOf[com.badlogic.gdx.utils.Array[?]]) {
        if (((knownType != null) && (actualType != knownType)) && (actualType != classOf[com.badlogic.gdx.utils.Array[?]])) {
          throw new com.badlogic.gdx.utils.SerializationException(((("Serialization of an Array other than the known type is not supported.\n" + "Known type: ") + knownType) + "\nActual type: ") + actualType)
        } else ()
        this.writeArrayStart()
        val array: com.badlogic.gdx.utils.Array[?] = value.asInstanceOf[com.badlogic.gdx.utils.Array[?]].asInstanceOf[com.badlogic.gdx.utils.Array[?]];
        { var i: scala.Int = 0; val n: scala.Int = array.size; while (i < n) { {
          this.writeValue(array.get(i), elementType, null)
        }; i = i + 1 } }
        this.writeArrayEnd()
        return
      } else ()
      if (value.isInstanceOf[com.badlogic.gdx.utils.Queue[?]]) {
        if (((knownType != null) && (actualType != knownType)) && (actualType != classOf[com.badlogic.gdx.utils.Queue[?]])) {
          throw new com.badlogic.gdx.utils.SerializationException(((("Serialization of a Queue other than the known type is not supported.\n" + "Known type: ") + knownType) + "\nActual type: ") + actualType)
        } else ()
        this.writeArrayStart()
        val queue: com.badlogic.gdx.utils.Queue[?] = value.asInstanceOf[com.badlogic.gdx.utils.Queue[?]].asInstanceOf[com.badlogic.gdx.utils.Queue[?]];
        { var i: scala.Int = 0; val n: scala.Int = queue.size; while (i < n) { {
          this.writeValue(queue.get(i), elementType, null)
        }; i = i + 1 } }
        this.writeArrayEnd()
        return
      } else ()
      if (value.isInstanceOf[scala.collection.mutable.Iterable[?]]) {
        if (((this.typeName != null) && (actualType != classOf[java.util.ArrayList[?]])) && ((knownType == null) || (knownType != actualType))) {
          this.writeObjectStart(actualType, knownType)
          this.writeArrayStart("items")
          for (item <- value.asInstanceOf[scala.collection.mutable.Iterable[?]]) {
            this.writeValue(item, elementType, null)
          }
          this.writeArrayEnd()
          this.writeObjectEnd()
        } else {
          this.writeArrayStart()
          for (item <- value.asInstanceOf[scala.collection.mutable.Iterable[?]]) {
            this.writeValue(item, elementType, null)
          }
          this.writeArrayEnd()
        }
        return
      } else ()
      if (actualType.isArray()) {
        if (elementType == null) {
          elementType = actualType.getComponentType()
        } else ()
        val length: scala.Int = com.badlogic.gdx.utils.reflect.ArrayReflection.getLength(value)
        this.writeArrayStart();
        { var i: scala.Int = 0; while (i < length) { {
          this.writeValue(com.badlogic.gdx.utils.reflect.ArrayReflection.get(value, i), elementType, null)
        }; i = i + 1 } }
        this.writeArrayEnd()
        return
      } else ()
      if (value.isInstanceOf[com.badlogic.gdx.utils.ObjectMap[?, ?]]) {
        if (knownType == null) {
          knownType = classOf[com.badlogic.gdx.utils.ObjectMap[?, ?]]
        } else ()
        this.writeObjectStart(actualType, knownType)
        for (entry <- value.asInstanceOf[com.badlogic.gdx.utils.ObjectMap[?, ?]].entries()) {
          this.writer.name(this.convertToString(entry.key))
          this.writeValue(entry.value, elementType, null)
        }
        this.writeObjectEnd()
        return
      } else ()
      if (value.isInstanceOf[com.badlogic.gdx.utils.ObjectIntMap[?]]) {
        if (knownType == null) {
          knownType = classOf[com.badlogic.gdx.utils.ObjectIntMap[?]]
        } else ()
        this.writeObjectStart(actualType, knownType)
        for (entry <- value.asInstanceOf[com.badlogic.gdx.utils.ObjectIntMap[?]].entries()) {
          this.writer.name(this.convertToString(entry.key))
          this.writeValue(entry.value.asInstanceOf[java.lang.Integer], classOf[java.lang.Integer])
        }
        this.writeObjectEnd()
        return
      } else ()
      if (value.isInstanceOf[com.badlogic.gdx.utils.ObjectFloatMap[?]]) {
        if (knownType == null) {
          knownType = classOf[com.badlogic.gdx.utils.ObjectFloatMap[?]]
        } else ()
        this.writeObjectStart(actualType, knownType)
        for (entry <- value.asInstanceOf[com.badlogic.gdx.utils.ObjectFloatMap[?]].entries()) {
          this.writer.name(this.convertToString(entry.key))
          this.writeValue(entry.value.asInstanceOf[java.lang.Float], classOf[java.lang.Float])
        }
        this.writeObjectEnd()
        return
      } else ()
      if (value.isInstanceOf[com.badlogic.gdx.utils.ObjectSet[?]]) {
        if (knownType == null) {
          knownType = classOf[com.badlogic.gdx.utils.ObjectSet[?]]
        } else ()
        this.writeObjectStart(actualType, knownType)
        this.writer.name("values")
        this.writeArrayStart()
        for (entry <- value.asInstanceOf[com.badlogic.gdx.utils.ObjectSet[?]]) {
          this.writeValue(entry, elementType, null)
        }
        this.writeArrayEnd()
        this.writeObjectEnd()
        return
      } else ()
      if (value.isInstanceOf[com.badlogic.gdx.utils.IntMap[?]]) {
        if (knownType == null) {
          knownType = classOf[com.badlogic.gdx.utils.IntMap[?]]
        } else ()
        this.writeObjectStart(actualType, knownType)
        for (entry <- value.asInstanceOf[com.badlogic.gdx.utils.IntMap[?]].entries()) {
          this.writer.name(java.lang.String.valueOf(entry.key))
          this.writeValue(entry.value, elementType, null)
        }
        this.writeObjectEnd()
        return
      } else ()
      if (value.isInstanceOf[com.badlogic.gdx.utils.LongMap[?]]) {
        if (knownType == null) {
          knownType = classOf[com.badlogic.gdx.utils.LongMap[?]]
        } else ()
        this.writeObjectStart(actualType, knownType)
        for (entry <- value.asInstanceOf[com.badlogic.gdx.utils.LongMap[?]].entries()) {
          this.writer.name(java.lang.String.valueOf(entry.key))
          this.writeValue(entry.value, elementType, null)
        }
        this.writeObjectEnd()
        return
      } else ()
      if (value.isInstanceOf[com.badlogic.gdx.utils.IntSet]) {
        if (knownType == null) {
          knownType = classOf[com.badlogic.gdx.utils.IntSet]
        } else ()
        this.writeObjectStart(actualType, knownType)
        this.writer.name("values")
        this.writeArrayStart();
        { val iter: com.badlogic.gdx.utils.IntSet.IntSetIterator = value.asInstanceOf[com.badlogic.gdx.utils.IntSet].iterator(); while (iter.hasNext) { {
          this.writeValue(iter.next().asInstanceOf[java.lang.Integer], classOf[java.lang.Integer], null)
        };  } }
        this.writeArrayEnd()
        this.writeObjectEnd()
        return
      } else ()
      if (value.isInstanceOf[com.badlogic.gdx.utils.ArrayMap[?, ?]]) {
        if (knownType == null) {
          knownType = classOf[com.badlogic.gdx.utils.ArrayMap[?, ?]]
        } else ()
        this.writeObjectStart(actualType, knownType)
        val map: com.badlogic.gdx.utils.ArrayMap[?, ?] = value.asInstanceOf[com.badlogic.gdx.utils.ArrayMap[?, ?]].asInstanceOf[com.badlogic.gdx.utils.ArrayMap[?, ?]];
        { var i: scala.Int = 0; val n: scala.Int = map.size; while (i < n) { {
          this.writer.name(this.convertToString(map.keys$field(i)))
          this.writeValue(map.values$field(i), elementType, null)
        }; i = i + 1 } }
        this.writeObjectEnd()
        return
      } else ()
      if (value.isInstanceOf[scala.collection.mutable.Map[?, ?]]) {
        if (knownType == null) {
          knownType = classOf[java.util.HashMap[?, ?]]
        } else ()
        this.writeObjectStart(actualType, knownType)
        for (entry <- value.asInstanceOf[scala.collection.mutable.Map[?, ?]].entrySet()) {
          this.writer.name(this.convertToString(entry.getKey()))
          this.writeValue(entry.getValue(), elementType, null)
        }
        this.writeObjectEnd()
        return
      } else ()
      if (com.badlogic.gdx.utils.reflect.ClassReflection.isAssignableFrom(classOf[java.lang.Enum[?]], actualType)) {
        if (actualType.getEnumConstants() == null) {
          actualType = actualType.getSuperclass()
        } else ()
        if ((this.typeName != null) && ((knownType == null) || (knownType != actualType))) {
          this.writeObjectStart(actualType, null)
          this.writer.name("value")
          this.writer.value(this.convertToString(value.asInstanceOf[java.lang.Enum[?]].asInstanceOf[java.lang.Enum[?]]))
          this.writeObjectEnd()
        } else {
          this.writer.value(this.convertToString(value.asInstanceOf[java.lang.Enum[?]].asInstanceOf[java.lang.Enum[?]]))
        }
        return
      } else ()
      this.writeObjectStart(actualType, knownType)
      this.writeFields(value)
      this.writeObjectEnd()
    } catch {
      case ex: java.io.IOException => {
        throw new com.badlogic.gdx.utils.SerializationException(ex)
      }
    }
  }
  def writeObjectStart(name: java.lang.String): scala.Unit = {
    try {
      this.writer.name(name)
    } catch {
      case ex: java.io.IOException => {
        throw new com.badlogic.gdx.utils.SerializationException(ex)
      }
    }
    this.writeObjectStart()
  }
  def writeObjectStart(name: java.lang.String, actualType: java.lang.Class[?], knownType: java.lang.Class[?]): scala.Unit = {
    try {
      this.writer.name(name)
    } catch {
      case ex: java.io.IOException => {
        throw new com.badlogic.gdx.utils.SerializationException(ex)
      }
    }
    this.writeObjectStart(actualType, knownType)
  }
  def writeObjectStart(): scala.Unit = {
    try {
      this.writer.`object`()
    } catch {
      case ex: java.io.IOException => {
        throw new com.badlogic.gdx.utils.SerializationException(ex)
      }
    }
  }
  def writeObjectStart(actualType: java.lang.Class[?], knownType: java.lang.Class[?]): scala.Unit = {
    try {
      this.writer.`object`()
    } catch {
      case ex: java.io.IOException => {
        throw new com.badlogic.gdx.utils.SerializationException(ex)
      }
    }
    if ((knownType == null) || (knownType != actualType)) {
      this.writeType(actualType)
    } else ()
  }
  def writeObjectEnd(): scala.Unit = {
    try {
      this.writer.pop()
    } catch {
      case ex: java.io.IOException => {
        throw new com.badlogic.gdx.utils.SerializationException(ex)
      }
    }
  }
  def writeArrayStart(name: java.lang.String): scala.Unit = {
    try {
      this.writer.name(name)
      this.writer.array()
    } catch {
      case ex: java.io.IOException => {
        throw new com.badlogic.gdx.utils.SerializationException(ex)
      }
    }
  }
  def writeArrayStart(): scala.Unit = {
    try {
      this.writer.array()
    } catch {
      case ex: java.io.IOException => {
        throw new com.badlogic.gdx.utils.SerializationException(ex)
      }
    }
  }
  def writeArrayEnd(): scala.Unit = {
    try {
      this.writer.pop()
    } catch {
      case ex: java.io.IOException => {
        throw new com.badlogic.gdx.utils.SerializationException(ex)
      }
    }
  }
  def writeType(`type`: java.lang.Class[?]): scala.Unit = {
    if (this.typeName == null) {
      return
    } else ()
    var className: java.lang.String = this.getTag(`type`)
    if (className == null) {
      className = `type`.getName()
    } else ()
    try {
      this.writer.set(this.typeName, className)
    } catch {
      case ex: java.io.IOException => {
        throw new com.badlogic.gdx.utils.SerializationException(ex)
      }
    }
    if (Json.debug) {
      java.lang.System.out.println("Writing type: " + `type`.getName())
    } else ()
  }
  def fromJson[T](`type`: java.lang.Class[T], reader: java.io.Reader): T = {
    return this.readValue(`type`, null, this.reader.parse(reader)).asInstanceOf[T]
  }
  def fromJson[T](`type`: java.lang.Class[T], elementType: java.lang.Class[?], reader: java.io.Reader): T = {
    return this.readValue(`type`, elementType, this.reader.parse(reader)).asInstanceOf[T]
  }
  def fromJson[T](`type`: java.lang.Class[T], input: java.io.InputStream): T = {
    return this.readValue(`type`, null, this.reader.parse(input)).asInstanceOf[T]
  }
  def fromJson[T](`type`: java.lang.Class[T], elementType: java.lang.Class[?], input: java.io.InputStream): T = {
    return this.readValue(`type`, elementType, this.reader.parse(input)).asInstanceOf[T]
  }
  def fromJson[T](`type`: java.lang.Class[T], file: com.badlogic.gdx.files.FileHandle): T = {
    try {
      return this.readValue(`type`, null, this.reader.parse(file)).asInstanceOf[T]
    } catch {
      case ex: java.lang.Exception => {
        throw new com.badlogic.gdx.utils.SerializationException("Error reading file: " + file, ex)
      }
    }
  }
  def fromJson[T](`type`: java.lang.Class[T], elementType: java.lang.Class[?], file: com.badlogic.gdx.files.FileHandle): T = {
    try {
      return this.readValue(`type`, elementType, this.reader.parse(file)).asInstanceOf[T]
    } catch {
      case ex: java.lang.Exception => {
        throw new com.badlogic.gdx.utils.SerializationException("Error reading file: " + file, ex)
      }
    }
  }
  def fromJson[T](`type`: java.lang.Class[T], data: scala.Array[scala.Char], offset: scala.Int, length: scala.Int): T = {
    return this.readValue(`type`, null, this.reader.parse(data, offset, length)).asInstanceOf[T]
  }
  def fromJson[T](`type`: java.lang.Class[T], elementType: java.lang.Class[?], data: scala.Array[scala.Char], offset: scala.Int, length: scala.Int): T = {
    return this.readValue(`type`, elementType, this.reader.parse(data, offset, length)).asInstanceOf[T]
  }
  def fromJson[T](`type`: java.lang.Class[T], json: java.lang.String): T = {
    return this.readValue(`type`, null, this.reader.parse(json)).asInstanceOf[T]
  }
  def fromJson[T](`type`: java.lang.Class[T], elementType: java.lang.Class[?], json: java.lang.String): T = {
    return this.readValue(`type`, elementType, this.reader.parse(json)).asInstanceOf[T]
  }
  def readField(`object`: java.lang.Object, name: java.lang.String, jsonData: com.badlogic.gdx.utils.JsonValue): scala.Unit = {
    this.readField(`object`, name, name, null, jsonData)
  }
  def readField(`object`: java.lang.Object, name: java.lang.String, elementType: java.lang.Class[?], jsonData: com.badlogic.gdx.utils.JsonValue): scala.Unit = {
    this.readField(`object`, name, name, elementType, jsonData)
  }
  def readField(`object`: java.lang.Object, fieldName: java.lang.String, jsonName: java.lang.String, jsonData: com.badlogic.gdx.utils.JsonValue): scala.Unit = {
    this.readField(`object`, fieldName, jsonName, null, jsonData)
  }
  def readField(`object`: java.lang.Object, fieldName: java.lang.String, jsonName: java.lang.String, elementType$arg: java.lang.Class[?], jsonMap: com.badlogic.gdx.utils.JsonValue): scala.Unit = {
    var elementType: java.lang.Class[?] = elementType$arg
    val `type`: java.lang.Class[?] = `object`.getClass()
    val metadata: com.badlogic.gdx.utils.Json.FieldMetadata = this.getFields(`type`).get(fieldName)
    if (metadata == null) {
      throw new com.badlogic.gdx.utils.SerializationException(((("Field not found: " + fieldName) + " (") + `type`.getName()) + ")")
    } else ()
    val field: com.badlogic.gdx.utils.reflect.Field = metadata.field
    if (elementType == null) {
      elementType = metadata.elementType
    } else ()
    this.readField(`object`, field, jsonName, elementType, jsonMap)
  }
  def readField(`object`: java.lang.Object, field: com.badlogic.gdx.utils.reflect.Field, jsonName: java.lang.String, elementType: java.lang.Class[?], jsonMap: com.badlogic.gdx.utils.JsonValue): scala.Unit = {
    val jsonValue: com.badlogic.gdx.utils.JsonValue = jsonMap.get(jsonName)
    if (jsonValue == null) {
      return
    } else ()
    try {
      field.set(`object`, this.readValue(field.getType(), elementType, jsonValue))
    } catch {
      case ex: com.badlogic.gdx.utils.reflect.ReflectionException => {
        throw new com.badlogic.gdx.utils.SerializationException(((("Error accessing field: " + field.getName()) + " (") + field.getDeclaringClass().getName()) + ")", ex)
      }
      case ex: com.badlogic.gdx.utils.SerializationException => {
        ex.addTrace(((field.getName() + " (") + field.getDeclaringClass().getName()) + ")")
        throw ex
      }
      case runtimeEx: java.lang.RuntimeException => {
        val ex: com.badlogic.gdx.utils.SerializationException = new com.badlogic.gdx.utils.SerializationException(runtimeEx)
        ex.addTrace(jsonValue.trace())
        ex.addTrace(((field.getName() + " (") + field.getDeclaringClass().getName()) + ")")
        throw ex
      }
    }
  }
  def readFields(`object`: java.lang.Object, jsonMap: com.badlogic.gdx.utils.JsonValue): scala.Unit = {
    val `type`: java.lang.Class[?] = `object`.getClass()
    val fields: com.badlogic.gdx.utils.OrderedMap[java.lang.String, com.badlogic.gdx.utils.Json.FieldMetadata] = this.getFields(`type`);
    { var child: com.badlogic.gdx.utils.JsonValue = jsonMap.child$field; while (child != null) { {
      val metadata: com.badlogic.gdx.utils.Json.FieldMetadata = fields.get(child.name().replace(" ", "_"))
      if (metadata == null) {
        if (child.name$field.equals(this.typeName)) {
          /* continue */ ()
        } else ()
        if (this.ignoreUnknownFields || this.ignoreUnknownField(`type`, child.name$field)) {
          if (Json.debug) {
            java.lang.System.out.println(((("Ignoring unknown field: " + child.name$field) + " (") + `type`.getName()) + ")")
          } else ()
          /* continue */ ()
        } else {
          val ex: com.badlogic.gdx.utils.SerializationException = new com.badlogic.gdx.utils.SerializationException(((("Field not found: " + child.name$field) + " (") + `type`.getName()) + ")")
          ex.addTrace(child.trace())
          throw ex
        }
      } else {
        if ((this.ignoreDeprecated && (!this.readDeprecated)) && metadata.deprecated) {
          /* continue */ ()
        } else ()
      }
      val field: com.badlogic.gdx.utils.reflect.Field = metadata.field
      try {
        field.set(`object`, this.readValue(field.getType(), metadata.elementType, child))
      } catch {
        case ex: com.badlogic.gdx.utils.reflect.ReflectionException => {
          throw new com.badlogic.gdx.utils.SerializationException(((("Error accessing field: " + field.getName()) + " (") + `type`.getName()) + ")", ex)
        }
        case ex: com.badlogic.gdx.utils.SerializationException => {
          ex.addTrace(((field.getName() + " (") + `type`.getName()) + ")")
          throw ex
        }
        case runtimeEx: java.lang.RuntimeException => {
          val ex: com.badlogic.gdx.utils.SerializationException = new com.badlogic.gdx.utils.SerializationException(runtimeEx)
          ex.addTrace(child.trace())
          ex.addTrace(((field.getName() + " (") + `type`.getName()) + ")")
          throw ex
        }
      }
    }; child = child.next$field } }
  }
  def ignoreUnknownField(`type`: java.lang.Class[?], fieldName: java.lang.String): scala.Boolean = {
    return false
  }
  def readValue[T](name: java.lang.String, `type`: java.lang.Class[T], jsonMap: com.badlogic.gdx.utils.JsonValue): T = {
    return this.readValue(`type`, null, jsonMap.get(name)).asInstanceOf[T]
  }
  def readValue[T](name: java.lang.String, `type`: java.lang.Class[T], defaultValue: T, jsonMap: com.badlogic.gdx.utils.JsonValue): T = {
    val jsonValue: com.badlogic.gdx.utils.JsonValue = jsonMap.get(name)
    if (jsonValue == null) {
      return defaultValue
    } else ()
    return this.readValue(`type`, null, jsonValue).asInstanceOf[T]
  }
  def readValue[T](name: java.lang.String, `type`: java.lang.Class[T], elementType: java.lang.Class[?], jsonMap: com.badlogic.gdx.utils.JsonValue): T = {
    return this.readValue(`type`, elementType, jsonMap.get(name)).asInstanceOf[T]
  }
  def readValue[T](name: java.lang.String, `type`: java.lang.Class[T], elementType: java.lang.Class[?], defaultValue: T, jsonMap: com.badlogic.gdx.utils.JsonValue): T = {
    val jsonValue: com.badlogic.gdx.utils.JsonValue = jsonMap.get(name)
    return this.readValue(`type`, elementType, defaultValue, jsonValue).asInstanceOf[T]
  }
  def readValue[T](`type`: java.lang.Class[T], elementType: java.lang.Class[?], defaultValue: T, jsonData: com.badlogic.gdx.utils.JsonValue): T = {
    if (jsonData == null) {
      return defaultValue
    } else ()
    return this.readValue(`type`, elementType, jsonData).asInstanceOf[T]
  }
  def readValue[T](`type`: java.lang.Class[T], jsonData: com.badlogic.gdx.utils.JsonValue): T = {
    return this.readValue(`type`, null, jsonData).asInstanceOf[T]
  }
  def readValue[T](type$arg: java.lang.Class[T], elementType$arg: java.lang.Class[?], jsonData$arg: com.badlogic.gdx.utils.JsonValue): T = {
    var `type`: java.lang.Class[T] = type$arg
    var elementType: java.lang.Class[?] = elementType$arg
    var jsonData: com.badlogic.gdx.utils.JsonValue = jsonData$arg
    if (jsonData == null) {
      return null.asInstanceOf[T]
    } else ()
    if (jsonData.isObject()) {
      val className: java.lang.String = if (this.typeName == null) null.asInstanceOf[java.lang.String] else jsonData.getString(this.typeName, null)
      if (className != null) {
        `type` = this.getClass(className)
        if (`type` == null) {
          try {
            `type` = com.badlogic.gdx.utils.reflect.ClassReflection.forName(className)
          } catch {
            case ex: com.badlogic.gdx.utils.reflect.ReflectionException => {
              throw new com.badlogic.gdx.utils.SerializationException(ex)
            }
          }
        } else ()
      } else ()
      if (`type` == null) {
        if (this.defaultSerializer != null) {
          return this.defaultSerializer.read(this, jsonData, `type`).asInstanceOf[T].asInstanceOf[T]
        } else ()
        return jsonData.asInstanceOf[T]
      } else ()
      if ((this.typeName != null) && com.badlogic.gdx.utils.reflect.ClassReflection.isAssignableFrom(classOf[java.util.Collection[?]], `type`)) {
        jsonData = jsonData.get("items")
        if (jsonData == null) {
          throw new com.badlogic.gdx.utils.SerializationException(((("Unable to convert object to collection: " + jsonData) + " (") + `type`.getName()) + ")")
        } else ()
      } else {
        val serializer: com.badlogic.gdx.utils.Json.Serializer[?] = this.classToSerializer.get(`type`)
        if (serializer != null) {
          return serializer.read(this, jsonData, `type`).asInstanceOf[T].asInstanceOf[T]
        } else ()
        if ((((((((((`type` == classOf[java.lang.String]) || (`type` == classOf[java.lang.Integer])) || (`type` == classOf[java.lang.Boolean])) || (`type` == classOf[java.lang.Float])) || (`type` == classOf[java.lang.Long])) || (`type` == classOf[java.lang.Double])) || (`type` == classOf[java.lang.Short])) || (`type` == classOf[java.lang.Byte])) || (`type` == classOf[java.lang.Character])) || com.badlogic.gdx.utils.reflect.ClassReflection.isAssignableFrom(classOf[java.lang.Enum[?]], `type`)) {
          return this.readValue("value", `type`, jsonData).asInstanceOf[T]
        } else ()
        val `object`: java.lang.Object = this.newInstance(`type`)
        if (`object`.isInstanceOf[com.badlogic.gdx.utils.Json.Serializable]) {
          `object`.asInstanceOf[com.badlogic.gdx.utils.Json.Serializable].read(this, jsonData)
          return `object`.asInstanceOf[T].asInstanceOf[T]
        } else ()
        if (`object`.isInstanceOf[com.badlogic.gdx.utils.ObjectMap[?, ?]]) {
          val result: com.badlogic.gdx.utils.ObjectMap[?, ?] = `object`.asInstanceOf[com.badlogic.gdx.utils.ObjectMap[?, ?]].asInstanceOf[com.badlogic.gdx.utils.ObjectMap[?, ?]];
          { var child: com.badlogic.gdx.utils.JsonValue = jsonData.child$field; while (child != null) { {
            result.put(child.name$field, this.readValue(elementType, null, child))
          }; child = child.next$field } }
          return result.asInstanceOf[T]
        } else ()
        if (`object`.isInstanceOf[com.badlogic.gdx.utils.ObjectIntMap[?]]) {
          val result: com.badlogic.gdx.utils.ObjectIntMap[?] = `object`.asInstanceOf[com.badlogic.gdx.utils.ObjectIntMap[?]].asInstanceOf[com.badlogic.gdx.utils.ObjectIntMap[?]];
          { var child: com.badlogic.gdx.utils.JsonValue = jsonData.child$field; while (child != null) { {
            result.put(child.name$field, this.readValue[java.lang.Integer](classOf[java.lang.Integer], null, child))
          }; child = child.next$field } }
          return result.asInstanceOf[T]
        } else ()
        if (`object`.isInstanceOf[com.badlogic.gdx.utils.ObjectFloatMap[?]]) {
          val result: com.badlogic.gdx.utils.ObjectFloatMap[?] = `object`.asInstanceOf[com.badlogic.gdx.utils.ObjectFloatMap[?]].asInstanceOf[com.badlogic.gdx.utils.ObjectFloatMap[?]];
          { var child: com.badlogic.gdx.utils.JsonValue = jsonData.child$field; while (child != null) { {
            result.put(child.name$field, this.readValue[java.lang.Float](classOf[java.lang.Float], null, child))
          }; child = child.next$field } }
          return result.asInstanceOf[T]
        } else ()
        if (`object`.isInstanceOf[com.badlogic.gdx.utils.ObjectSet[?]]) {
          val result: com.badlogic.gdx.utils.ObjectSet[?] = `object`.asInstanceOf[com.badlogic.gdx.utils.ObjectSet[?]].asInstanceOf[com.badlogic.gdx.utils.ObjectSet[?]];
          { var child: com.badlogic.gdx.utils.JsonValue = jsonData.getChild("values"); while (child != null) { {
            result.add(this.readValue(elementType, null, child))
          }; child = child.next$field } }
          return result.asInstanceOf[T]
        } else ()
        if (`object`.isInstanceOf[com.badlogic.gdx.utils.IntMap[?]]) {
          val result: com.badlogic.gdx.utils.IntMap[?] = `object`.asInstanceOf[com.badlogic.gdx.utils.IntMap[?]].asInstanceOf[com.badlogic.gdx.utils.IntMap[?]];
          { var child: com.badlogic.gdx.utils.JsonValue = jsonData.child$field; while (child != null) { {
            result.put(java.lang.Integer.parseInt(child.name$field), this.readValue(elementType, null, child))
          }; child = child.next$field } }
          return result.asInstanceOf[T]
        } else ()
        if (`object`.isInstanceOf[com.badlogic.gdx.utils.LongMap[?]]) {
          val result: com.badlogic.gdx.utils.LongMap[?] = `object`.asInstanceOf[com.badlogic.gdx.utils.LongMap[?]].asInstanceOf[com.badlogic.gdx.utils.LongMap[?]];
          { var child: com.badlogic.gdx.utils.JsonValue = jsonData.child$field; while (child != null) { {
            result.put(java.lang.Long.parseLong(child.name$field), this.readValue(elementType, null, child))
          }; child = child.next$field } }
          return result.asInstanceOf[T]
        } else ()
        if (`object`.isInstanceOf[com.badlogic.gdx.utils.IntSet]) {
          val result: com.badlogic.gdx.utils.IntSet = `object`.asInstanceOf[com.badlogic.gdx.utils.IntSet].asInstanceOf[com.badlogic.gdx.utils.IntSet];
          { var child: com.badlogic.gdx.utils.JsonValue = jsonData.getChild("values"); while (child != null) { {
            result.add(child.asInt())
          }; child = child.next$field } }
          return result.asInstanceOf[T]
        } else ()
        if (`object`.isInstanceOf[com.badlogic.gdx.utils.ArrayMap[?, ?]]) {
          val result: com.badlogic.gdx.utils.ArrayMap[?, ?] = `object`.asInstanceOf[com.badlogic.gdx.utils.ArrayMap[?, ?]].asInstanceOf[com.badlogic.gdx.utils.ArrayMap[?, ?]];
          { var child: com.badlogic.gdx.utils.JsonValue = jsonData.child$field; while (child != null) { {
            result.put(child.name$field, this.readValue(elementType, null, child))
          }; child = child.next$field } }
          return result.asInstanceOf[T]
        } else ()
        if (`object`.isInstanceOf[scala.collection.mutable.Map[?, ?]]) {
          val result: scala.collection.mutable.Map[?, ?] = `object`.asInstanceOf[scala.collection.mutable.Map[?, ?]].asInstanceOf[scala.collection.mutable.Map[?, ?]];
          { var child: com.badlogic.gdx.utils.JsonValue = jsonData.child$field; while (child != null) { {
            if (child.name$field.equals(this.typeName)) {
              /* continue */ ()
            } else ()
            result.update(child.name$field, this.readValue(elementType, null, child))
          }; child = child.next$field } }
          return result.asInstanceOf[T]
        } else ()
        this.readFields(`object`, jsonData)
        return `object`.asInstanceOf[T].asInstanceOf[T]
      }
    } else ()
    if (`type` != null) {
      val serializer: com.badlogic.gdx.utils.Json.Serializer[?] = this.classToSerializer.get(`type`)
      if (serializer != null) {
        return serializer.read(this, jsonData, `type`).asInstanceOf[T].asInstanceOf[T]
      } else ()
      if (com.badlogic.gdx.utils.reflect.ClassReflection.isAssignableFrom(classOf[com.badlogic.gdx.utils.Json.Serializable], `type`)) {
        val `object`: java.lang.Object = this.newInstance(`type`)
        `object`.asInstanceOf[com.badlogic.gdx.utils.Json.Serializable].read(this, jsonData)
        return `object`.asInstanceOf[T].asInstanceOf[T]
      } else ()
    } else ()
    if (jsonData.isArray()) {
      if ((`type` == null) || (`type` == classOf[java.lang.Object])) {
        `type` = classOf[com.badlogic.gdx.utils.Array[?]].asInstanceOf[java.lang.Class[T]]
      } else ()
      if (com.badlogic.gdx.utils.reflect.ClassReflection.isAssignableFrom(classOf[com.badlogic.gdx.utils.Array[?]], `type`)) {
        val result: com.badlogic.gdx.utils.Array[?] = if (`type` == classOf[com.badlogic.gdx.utils.Array[?]]) new com.badlogic.gdx.utils.Array() else this.newInstance(`type`).asInstanceOf[com.badlogic.gdx.utils.Array[?]];
        { var child: com.badlogic.gdx.utils.JsonValue = jsonData.child$field; while (child != null) { {
          result.add(this.readValue(elementType, null, child))
        }; child = child.next$field } }
        return result.asInstanceOf[T]
      } else ()
      if (com.badlogic.gdx.utils.reflect.ClassReflection.isAssignableFrom(classOf[com.badlogic.gdx.utils.Queue[?]], `type`)) {
        val result: com.badlogic.gdx.utils.Queue[?] = if (`type` == classOf[com.badlogic.gdx.utils.Queue[?]]) new com.badlogic.gdx.utils.Queue() else this.newInstance(`type`).asInstanceOf[com.badlogic.gdx.utils.Queue[?]];
        { var child: com.badlogic.gdx.utils.JsonValue = jsonData.child$field; while (child != null) { {
          result.addLast(this.readValue(elementType, null, child))
        }; child = child.next$field } }
        return result.asInstanceOf[T]
      } else ()
      if (com.badlogic.gdx.utils.reflect.ClassReflection.isAssignableFrom(classOf[java.util.Collection[?]], `type`)) {
        val result: scala.collection.mutable.Iterable[?] = if (`type`.isInterface()) new scala.collection.mutable.ArrayBuffer() else this.newInstance(`type`).asInstanceOf[scala.collection.mutable.Iterable[?]];
        { var child: com.badlogic.gdx.utils.JsonValue = jsonData.child$field; while (child != null) { {
          result += this.readValue(elementType, null, child)
        }; child = child.next$field } }
        return result.asInstanceOf[T]
      } else ()
      if (`type`.isArray()) {
        val componentType: java.lang.Class[?] = `type`.getComponentType()
        if (elementType == null) {
          elementType = componentType
        } else ()
        val result: java.lang.Object = com.badlogic.gdx.utils.reflect.ArrayReflection.newInstance(componentType, jsonData.size$field)
        var i: scala.Int = 0;
        { var child: com.badlogic.gdx.utils.JsonValue = jsonData.child$field; while (child != null) { {
          com.badlogic.gdx.utils.reflect.ArrayReflection.set(result, { i += 1; i }, this.readValue(elementType, null, child))
        }; child = child.next$field } }
        return result.asInstanceOf[T].asInstanceOf[T]
      } else ()
      throw new com.badlogic.gdx.utils.SerializationException(((("Unable to convert value to required type: " + jsonData) + " (") + `type`.getName()) + ")")
    } else ()
    if (jsonData.isNumber()) {
      try {
        if (((`type` == null) || (`type` == classOf[scala.Float])) || (`type` == classOf[java.lang.Float])) {
          return jsonData.asFloat().asInstanceOf[java.lang.Float].asInstanceOf[T]
        } else ()
        if ((`type` == classOf[scala.Int]) || (`type` == classOf[java.lang.Integer])) {
          return jsonData.asInt().asInstanceOf[java.lang.Integer].asInstanceOf[T]
        } else ()
        if ((`type` == classOf[scala.Long]) || (`type` == classOf[java.lang.Long])) {
          return jsonData.asLong().asInstanceOf[java.lang.Long].asInstanceOf[T]
        } else ()
        if ((`type` == classOf[scala.Double]) || (`type` == classOf[java.lang.Double])) {
          return jsonData.asDouble().asInstanceOf[java.lang.Double].asInstanceOf[T]
        } else ()
        if (`type` == classOf[java.lang.String]) {
          return jsonData.asString().asInstanceOf[T]
        } else ()
        if ((`type` == classOf[scala.Short]) || (`type` == classOf[java.lang.Short])) {
          return jsonData.asShort().asInstanceOf[java.lang.Short].asInstanceOf[T]
        } else ()
        if ((`type` == classOf[scala.Byte]) || (`type` == classOf[java.lang.Byte])) {
          return jsonData.asByte().asInstanceOf[java.lang.Byte].asInstanceOf[T]
        } else ()
        if ((`type` == classOf[scala.Char]) || (`type` == classOf[java.lang.Character])) {
          return jsonData.asChar().asInstanceOf[java.lang.Character].asInstanceOf[T]
        } else ()
      } catch {
        case ignored: java.lang.NumberFormatException => {
          ()
        }
      }
      jsonData = new com.badlogic.gdx.utils.JsonValue(jsonData.asString())
    } else ()
    if (jsonData.isBoolean()) {
      try {
        if (((`type` == null) || (`type` == classOf[scala.Boolean])) || (`type` == classOf[java.lang.Boolean])) {
          return jsonData.asBoolean().asInstanceOf[java.lang.Boolean].asInstanceOf[T]
        } else ()
      } catch {
        case ignored: java.lang.NumberFormatException => {
          ()
        }
      }
      jsonData = new com.badlogic.gdx.utils.JsonValue(jsonData.asString())
    } else ()
    if (jsonData.isString()) {
      val string: java.lang.String = jsonData.asString()
      if ((`type` == null) || (`type` == classOf[java.lang.String])) {
        return string.asInstanceOf[T]
      } else ()
      try {
        if ((`type` == classOf[scala.Int]) || (`type` == classOf[java.lang.Integer])) {
          return java.lang.Integer.valueOf(string).asInstanceOf[T]
        } else ()
        if ((`type` == classOf[scala.Float]) || (`type` == classOf[java.lang.Float])) {
          return java.lang.Float.valueOf(string).asInstanceOf[T]
        } else ()
        if ((`type` == classOf[scala.Long]) || (`type` == classOf[java.lang.Long])) {
          return java.lang.Long.valueOf(string).asInstanceOf[T]
        } else ()
        if ((`type` == classOf[scala.Double]) || (`type` == classOf[java.lang.Double])) {
          return java.lang.Double.valueOf(string).asInstanceOf[T]
        } else ()
        if ((`type` == classOf[scala.Short]) || (`type` == classOf[java.lang.Short])) {
          return java.lang.Short.valueOf(string).asInstanceOf[T]
        } else ()
        if ((`type` == classOf[scala.Byte]) || (`type` == classOf[java.lang.Byte])) {
          return java.lang.Byte.valueOf(string).asInstanceOf[T]
        } else ()
      } catch {
        case ignored: java.lang.NumberFormatException => {
          ()
        }
      }
      if ((`type` == classOf[scala.Boolean]) || (`type` == classOf[java.lang.Boolean])) {
        return java.lang.Boolean.valueOf(string).asInstanceOf[T]
      } else ()
      if ((`type` == classOf[scala.Char]) || (`type` == classOf[java.lang.Character])) {
        return string.charAt(0).asInstanceOf[java.lang.Character].asInstanceOf[T]
      } else ()
      if (com.badlogic.gdx.utils.reflect.ClassReflection.isAssignableFrom(classOf[java.lang.Enum[?]], `type`)) {
        val constants: scala.Array[java.lang.Enum[?]] = `type`.getEnumConstants().asInstanceOf[scala.Array[java.lang.Enum[?]]].asInstanceOf[scala.Array[java.lang.Enum[?]]];
        { var i: scala.Int = 0; val n: scala.Int = constants.length; while (i < n) { {
          val e: java.lang.Enum[?] = constants(i)
          if (string.equals(this.convertToString(e))) {
            return e.asInstanceOf[T]
          } else ()
        }; i = i + 1 } }
      } else ()
      if (`type` == classOf[java.lang.CharSequence]) {
        return string.asInstanceOf[T]
      } else ()
      throw new com.badlogic.gdx.utils.SerializationException(((("Unable to convert value to required type: " + jsonData) + " (") + `type`.getName()) + ")")
    } else ()
    return null.asInstanceOf[T]
  }
  def copyFields(from: java.lang.Object, to: java.lang.Object): scala.Unit = {
    val toFields: com.badlogic.gdx.utils.OrderedMap[java.lang.String, com.badlogic.gdx.utils.Json.FieldMetadata] = this.getFields(to.getClass())
    for (entry <- this.getFields(from.getClass())) {
      val toField: com.badlogic.gdx.utils.Json.FieldMetadata = toFields.get(entry.key)
      val fromField: com.badlogic.gdx.utils.reflect.Field = entry.value.field
      if (toField == null) {
        throw new com.badlogic.gdx.utils.SerializationException("To object is missing field: " + entry.key)
      } else ()
      try {
        toField.field.set(to, fromField.get(from))
      } catch {
        case ex: com.badlogic.gdx.utils.reflect.ReflectionException => {
          throw new com.badlogic.gdx.utils.SerializationException("Error copying field: " + fromField.getName(), ex)
        }
      }
    }
  }
  private def convertToString(e: java.lang.Enum[?]): java.lang.String = {
    return if (this.enumNames) e.name() else e.toString()
  }
  private def convertToString(`object`: java.lang.Object): java.lang.String = {
    if (`object`.isInstanceOf[java.lang.Enum[?]]) {
      return this.convertToString(`object`.asInstanceOf[java.lang.Enum[?]].asInstanceOf[java.lang.Enum[?]])
    } else ()
    if (`object`.isInstanceOf[java.lang.Class[?]]) {
      return `object`.asInstanceOf[java.lang.Class[?]].getName()
    } else ()
    return java.lang.String.valueOf(`object`)
  }
  def newInstance(type$arg: java.lang.Class[?]): java.lang.Object = {
    var `type`: java.lang.Class[?] = type$arg
    try {
      return com.badlogic.gdx.utils.reflect.ClassReflection.newInstance(`type`)
    } catch {
      case ex: java.lang.Exception => {
        try {
          val constructor: com.badlogic.gdx.utils.reflect.Constructor = com.badlogic.gdx.utils.reflect.ClassReflection.getDeclaredConstructor(`type`)
          constructor.setAccessible(true)
          return constructor.newInstance()
        } catch {
          case ignored: java.lang.SecurityException => {
            ()
          }
          case ignored: com.badlogic.gdx.utils.reflect.ReflectionException => {
            if (com.badlogic.gdx.utils.reflect.ClassReflection.isAssignableFrom(classOf[java.lang.Enum[?]], `type`)) {
              if (`type`.getEnumConstants() == null) {
                `type` = `type`.getSuperclass()
              } else ()
              return `type`.getEnumConstants()(0)
            } else ()
            if (`type`.isArray()) {
              throw new com.badlogic.gdx.utils.SerializationException("Encountered JSON object when expected array of type: " + `type`.getName(), ex)
            } else {
              if (com.badlogic.gdx.utils.reflect.ClassReflection.isMemberClass(`type`) && (!com.badlogic.gdx.utils.reflect.ClassReflection.isStaticClass(`type`))) {
                throw new com.badlogic.gdx.utils.SerializationException("Class cannot be created (non-static member class): " + `type`.getName(), ex)
              } else {
                throw new com.badlogic.gdx.utils.SerializationException("Class cannot be created (missing no-arg constructor): " + `type`.getName(), ex)
              }
            }
          }
          case privateConstructorException: java.lang.Exception => {
            ex = privateConstructorException
          }
        }
        throw new com.badlogic.gdx.utils.SerializationException("Error constructing instance of class: " + `type`.getName(), ex)
      }
    }
  }
  def prettyPrint(`object`: java.lang.Object): java.lang.String = {
    return this.prettyPrint(`object`, 0)
  }
  def prettyPrint(json: java.lang.String): java.lang.String = {
    return this.prettyPrint(json, 0)
  }
  def prettyPrint(`object`: java.lang.Object, singleLineColumns: scala.Int): java.lang.String = {
    return this.prettyPrint(this.toJson(`object`), singleLineColumns)
  }
  def prettyPrint(json: java.lang.String, singleLineColumns: scala.Int): java.lang.String = {
    return this.reader.parse(json).prettyPrint(this.outputType, singleLineColumns)
  }
  def prettyPrint(`object`: java.lang.Object, settings: com.badlogic.gdx.utils.JsonValue.PrettyPrintSettings): java.lang.String = {
    return this.prettyPrint(this.toJson(`object`), settings)
  }
  def prettyPrint(json: java.lang.String, settings: com.badlogic.gdx.utils.JsonValue.PrettyPrintSettings): java.lang.String = {
    return this.reader.parse(json).prettyPrint(settings)
  }
}
object Json {
  private final val debug: scala.Boolean = false
  class FieldMetadata {
    var field: com.badlogic.gdx.utils.reflect.Field = null.asInstanceOf[com.badlogic.gdx.utils.reflect.Field]
    var elementType: java.lang.Class[?] = null.asInstanceOf[java.lang.Class[?]]
    var deprecated: scala.Boolean = false
    def this(field: com.badlogic.gdx.utils.reflect.Field) = {
      this()
      this.field = field
      val index: scala.Int = if (com.badlogic.gdx.utils.reflect.ClassReflection.isAssignableFrom(classOf[com.badlogic.gdx.utils.ObjectMap[?, ?]], field.getType()) || com.badlogic.gdx.utils.reflect.ClassReflection.isAssignableFrom(classOf[java.util.Map[?, ?]], field.getType())) 1 else 0
      this.elementType = field.getElementType(index)
      this.deprecated = field.isAnnotationPresent(classOf[java.lang.Deprecated])
    }
  }
  trait Serializer[T] {
    def write(json: Json, `object`: T, knownType: java.lang.Class[?]): scala.Unit
    def read(json: Json, jsonData: com.badlogic.gdx.utils.JsonValue, `type`: java.lang.Class[?]): T
  }
  abstract class ReadOnlySerializer[T] extends com.badlogic.gdx.utils.Json.Serializer[T] {
    def write(json: Json, `object`: T, knownType: java.lang.Class[?]): scala.Unit = {
      ()
    }
    def read(json: Json, jsonData: com.badlogic.gdx.utils.JsonValue, `type`: java.lang.Class[?]): T
  }
  trait Serializable {
    def write(json: Json): scala.Unit
    def read(json: Json, jsonData: com.badlogic.gdx.utils.JsonValue): scala.Unit
  }
}