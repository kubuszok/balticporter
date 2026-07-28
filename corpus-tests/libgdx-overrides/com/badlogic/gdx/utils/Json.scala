package com.badlogic.gdx.utils

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.utils.JsonWriter.OutputType

import java.io.{InputStream, Reader, StringWriter, Writer}

/** INJECTED SCALA (Substitutions.inject) — substitution seam for libGDX's `Json`.
  *
  * libGDX's `Json` is a REFLECTION-driven serializer: it walks `utils.reflect` field metadata to
  * decide how to read and write arbitrary objects. sge does not port it — decoding was replaced by
  * Kindlings' Jsoniter/UBJson codecs, which resolve a codec statically instead of reflecting at
  * runtime. Porting it mechanically is also what produced the corpus's largest error cluster: its
  * own `Class[?]` plumbing drives overload storms on `readValue`/`writeValue`/`convertToString`.
  *
  * This facade preserves the API the ported corpus calls, so libgdx-core compiles against a port
  * that genuinely does not contain the reflective serializer. Two tiers of behaviour:
  *
  *   - CONFIGURATION and the EXPLICIT WRITE path are real: `writeValue`/`writeObjectStart`/… drive
  *     the ported [[JsonWriter]] directly, and `Json.Serializable` objects write themselves. No
  *     reflection is involved, so this is faithful.
  *   - REFLECTIVE paths — `readValue` of an arbitrary type, `fromJson`, `readFields`, `writeFields`,
  *     `copyFields`, `newInstance` — are exactly what Kindlings replaces. They raise
  *     [[UnsupportedOperationException]] naming the seam rather than silently returning null or an
  *     empty document. THIS IS THE SWAP POINT: bind a Kindlings codec in [[codec]] and delegate.
  *
  * Note the tradeoff this encodes: the port compiles and its JSON *writing* works, but the decoding
  * paths are inert until a codec is wired. That is deliberate — a stub that quietly produced empty
  * objects would be far worse than one that names what is missing.
  */
class Json:

  def this(outputType: OutputType) =
    this()
    setOutputType(outputType)

  private var writer: JsonWriter               = null
  private var reader: JsonReader               = new JsonReader
  private var outputType: OutputType           = OutputType.minimal
  private var quoteLongValues: Boolean         = false
  private var ignoreUnknownFields: Boolean     = false
  private var ignoreDeprecated: Boolean        = false
  private var readDeprecated: Boolean          = true
  private var enumNames: Boolean               = true
  private var sortFields: Boolean              = false
  private var usePrototypes: Boolean           = true
  private var typeName: String                 = "class"
  private var defaultSerializer: Json.Serializer[?] = null
  private val classToTag                       = new ObjectMap[Class[?], String]
  private val tagToClass                       = new ObjectMap[String, Class[?]]
  private val classToSerializer                = new ObjectMap[Class[?], Json.Serializer[?]]

  /** THE SWAP POINT: bind the Kindlings Jsoniter/UBJson codec here and delegate the reflective
    * paths to it. Until then they fail loudly rather than pretending to decode. */
  private def codec(operation: String): Nothing =
    throw new UnsupportedOperationException(
      operation + " is not ported: sge replaces libGDX's reflection-based Json with Kindlings " +
        "Jsoniter/UBJson codecs. Bind the codec in Json.codec and delegate this call to it.")

  // ---- configuration (real) ----------------------------------------------
  def setIgnoreUnknownFields(ignoreUnknownFields: Boolean): Unit = this.ignoreUnknownFields = ignoreUnknownFields
  def getIgnoreUnknownFields(): Boolean                          = this.ignoreUnknownFields
  def setIgnoreDeprecated(ignoreDeprecated: Boolean): Unit       = this.ignoreDeprecated = ignoreDeprecated
  def setReadDeprecated(readDeprecated: Boolean): Unit           = this.readDeprecated = readDeprecated
  def setEnumNames(enumNames: Boolean): Unit                     = this.enumNames = enumNames
  def setSortFields(sortFields: Boolean): Unit                   = this.sortFields = sortFields
  def setUsePrototypes(usePrototypes: Boolean): Unit             = this.usePrototypes = usePrototypes
  def setTypeName(typeName: String): Unit                        = this.typeName = typeName
  def setQuoteLongValues(quoteLongValues: Boolean): Unit =
    this.quoteLongValues = quoteLongValues
    if this.writer != null then this.writer.setQuoteLongValues(quoteLongValues)

  def setOutputType(outputType: OutputType): Unit =
    this.outputType = outputType
    if this.writer != null then this.writer.setOutputType(outputType)

  def setDefaultSerializer(defaultSerializer: Json.Serializer[?]): Unit =
    this.defaultSerializer = defaultSerializer

  /** Java's parameter is `Serializer<T>`; ours is `Serializer[?]`, for the same reason `read`
    * returns `Object` above. libGDX registers RAW `new ReadOnlySerializer() {…}` instances, and a
    * raw anonymous class gives Scala nothing to infer the parent's argument FROM — the expected
    * type does not propagate into an anonymous class's parent, so it infers `Nothing` and
    * `Serializer[Nothing]` matches no `Serializer[X]`. Accepting the erased registration is the
    * only faithful rendering: javac accepted it unchecked, and the map below is untyped anyway. */
  def setSerializer[T](`type`: Class[T], serializer: Json.Serializer[?]): Unit =
    this.classToSerializer.put(`type`, serializer)

  def getSerializer[T](`type`: Class[T]): Json.Serializer[T] =
    this.classToSerializer.get(`type`).asInstanceOf[Json.Serializer[T]]

  def addClassTag(tag: String, `type`: Class[?]): Unit =
    this.tagToClass.put(tag, `type`)
    this.classToTag.put(`type`, tag)

  def getClass(tag: String): Class[?] = this.tagToClass.get(tag)
  def getTag(`type`: Class[?]): String = this.classToTag.get(`type`)

  /** element/deprecation metadata is a reflection concern — recorded but unused by the codec seam. */
  def setElementType(`type`: Class[?], fieldName: String, elementType: Class[?]): Unit = ()
  def setDeprecated(`type`: Class[?], fieldName: String, deprecated: Boolean): Unit    = ()

  def setWriter(writer: Writer): Unit =
    val jw = writer match
      case w: JsonWriter => w
      case w             => new JsonWriter(w)
    jw.setOutputType(this.outputType)
    jw.setQuoteLongValues(this.quoteLongValues)
    this.writer = jw

  def getWriter(): JsonWriter    = this.writer
  def setReader(reader: JsonReader): Unit = this.reader = reader
  def getReader(): JsonReader    = this.reader

  // ---- explicit write path (real — drives the ported JsonWriter) ----------
  def writeObjectStart(): Unit                    = this.writer.`object`()
  def writeObjectStart(name: String): Unit        = this.writer.`object`(name)
  def writeObjectStart(actualType: Class[?], knownType: Class[?]): Unit =
    this.writer.`object`()
    if knownType == null || knownType != actualType then writeType(actualType)
  def writeObjectStart(name: String, actualType: Class[?], knownType: Class[?]): Unit =
    this.writer.name(name)
    writeObjectStart(actualType, knownType)
  def writeObjectEnd(): Unit  = this.writer.pop()
  def writeArrayStart(): Unit = this.writer.array()
  def writeArrayStart(name: String): Unit = this.writer.array(name)
  def writeArrayEnd(): Unit   = this.writer.pop()

  def writeType(`type`: Class[?]): Unit =
    if this.typeName != null then
      val tag = if getTag(`type`) != null then getTag(`type`) else `type`.getName
      this.writer.set(this.typeName, tag)

  def writeValue(value: Object): Unit =
    if value == null then this.writer.value(null)
    else writeValue(value, value.getClass, null)

  def writeValue(value: Object, knownType: Class[?]): Unit = writeValue(value, knownType, null)

  def writeValue(value: Object, knownType: Class[?], elementType: Class[?]): Unit =
    value match
      case null                        => this.writer.value(null)
      case v: (String | java.lang.Number | java.lang.Boolean | java.lang.Character) =>
        this.writer.value(v)
      case s: Json.Serializable =>
        writeObjectStart(s.getClass, knownType)
        s.write(this)
        writeObjectEnd()
      case v =>
        val serializer = this.classToSerializer.get(v.getClass)
        if serializer != null then
          serializer.asInstanceOf[Json.Serializer[Object]].write(this, v, knownType)
        else codec("Json.writeValue of " + v.getClass.getName)

  def writeValue(name: String, value: Object): Unit =
    this.writer.name(name)
    writeValue(value)

  def writeValue(name: String, value: Object, knownType: Class[?]): Unit =
    this.writer.name(name)
    writeValue(value, knownType, null)

  def writeValue(name: String, value: Object, knownType: Class[?], elementType: Class[?]): Unit =
    this.writer.name(name)
    writeValue(value, knownType, elementType)

  // ---- reflective paths (the Kindlings swap point) -----------------------
  def writeFields(`object`: Object): Unit                                        = codec("Json.writeFields")
  def writeField(`object`: Object, name: String): Unit                           = codec("Json.writeField")
  def writeField(`object`: Object, name: String, elementType: Class[?]): Unit    = codec("Json.writeField")
  def writeField(`object`: Object, fieldName: String, jsonName: String): Unit    = codec("Json.writeField")
  def writeField(`object`: Object, fieldName: String, jsonName: String, elementType: Class[?]): Unit =
    codec("Json.writeField")

  def readFields(`object`: Object, jsonMap: JsonValue): Unit                            = codec("Json.readFields")
  def readField(`object`: Object, name: String, jsonData: JsonValue): Unit              = codec("Json.readField")
  def readField(`object`: Object, name: String, elementType: Class[?], jsonData: JsonValue): Unit =
    codec("Json.readField")
  def readField(`object`: Object, fieldName: String, jsonName: String, jsonData: JsonValue): Unit =
    codec("Json.readField")
  def readField(`object`: Object, fieldName: String, jsonName: String, elementType: Class[?], jsonMap: JsonValue): Unit =
    codec("Json.readField")

  /** `protected boolean ignoreUnknownField (Class type, String fieldName)` — libgdx's Json calls it
    * from `readFields`, and `Skin` overrides it. Absent here, that override compiled to nothing. */
  def ignoreUnknownField(`type`: Class[?], fieldName: String): Boolean = false

  def readValue[T](`type`: Class[T], jsonData: JsonValue): T = codec("Json.readValue")
  def readValue[T](`type`: Class[T], elementType: Class[?], jsonData: JsonValue): T = codec("Json.readValue")
  def readValue[T](`type`: Class[T], elementType: Class[?], defaultValue: T, jsonData: JsonValue): T =
    codec("Json.readValue")
  def readValue[T](name: String, `type`: Class[T], jsonMap: JsonValue): T = codec("Json.readValue")
  def readValue[T](name: String, `type`: Class[T], defaultValue: T, jsonMap: JsonValue): T = codec("Json.readValue")
  def readValue[T](name: String, `type`: Class[T], elementType: Class[?], jsonMap: JsonValue): T =
    codec("Json.readValue")
  def readValue[T](name: String, `type`: Class[T], elementType: Class[?], defaultValue: T, jsonMap: JsonValue): T =
    codec("Json.readValue")

  def fromJson[T](`type`: Class[T], reader: Reader): T                                  = codec("Json.fromJson")
  def fromJson[T](`type`: Class[T], elementType: Class[?], reader: Reader): T           = codec("Json.fromJson")
  def fromJson[T](`type`: Class[T], input: InputStream): T                              = codec("Json.fromJson")
  def fromJson[T](`type`: Class[T], elementType: Class[?], input: InputStream): T       = codec("Json.fromJson")
  def fromJson[T](`type`: Class[T], file: FileHandle): T                                = codec("Json.fromJson")
  def fromJson[T](`type`: Class[T], elementType: Class[?], file: FileHandle): T         = codec("Json.fromJson")
  def fromJson[T](`type`: Class[T], data: scala.Array[Char], offset: Int, length: Int): T = codec("Json.fromJson")
  def fromJson[T](`type`: Class[T], elementType: Class[?], data: scala.Array[Char], offset: Int, length: Int): T =
    codec("Json.fromJson")
  def fromJson[T](`type`: Class[T], json: String): T                                    = codec("Json.fromJson")
  def fromJson[T](`type`: Class[T], elementType: Class[?], json: String): T             = codec("Json.fromJson")

  def copyFields(from: Object, to: Object): Unit = codec("Json.copyFields")

  // ---- whole-document helpers -------------------------------------------
  def toJson(`object`: Object): String                                       = toJson(`object`, null, null)
  def toJson(`object`: Object, knownType: Class[?]): String                  = toJson(`object`, knownType, null)
  def toJson(`object`: Object, knownType: Class[?], elementType: Class[?]): String =
    val buffer = new StringWriter
    toJson(`object`, knownType, elementType, buffer)
    buffer.toString

  def toJson(`object`: Object, file: FileHandle): Unit                                = toJson(`object`, null, null, file)
  def toJson(`object`: Object, knownType: Class[?], file: FileHandle): Unit           = toJson(`object`, knownType, null, file)
  def toJson(`object`: Object, knownType: Class[?], elementType: Class[?], file: FileHandle): Unit =
    val buffer = new StringWriter
    toJson(`object`, knownType, elementType, buffer)
    file.writeString(buffer.toString, false)

  def toJson(`object`: Object, writer: Writer): Unit                      = toJson(`object`, null, null, writer)
  def toJson(`object`: Object, knownType: Class[?], writer: Writer): Unit = toJson(`object`, knownType, null, writer)
  def toJson(`object`: Object, knownType: Class[?], elementType: Class[?], writer: Writer): Unit =
    setWriter(writer)
    try writeValue(`object`, knownType, elementType)
    finally
      this.writer.close()
      this.writer = null

  def prettyPrint(`object`: Object): String                  = prettyPrint(`object`, 0)
  def prettyPrint(json: String): String                      = prettyPrint(json, 0)
  def prettyPrint(`object`: Object, singleLineColumns: Int): String =
    prettyPrint(toJson(`object`), singleLineColumns)
  def prettyPrint(json: String, singleLineColumns: Int): String =
    this.reader.parse(json).prettyPrint(this.outputType, singleLineColumns)
  def prettyPrint(`object`: Object, settings: JsonValue.PrettyPrintSettings): String =
    prettyPrint(toJson(`object`), settings)
  def prettyPrint(json: String, settings: JsonValue.PrettyPrintSettings): String =
    this.reader.parse(json).prettyPrint(settings)

object Json:

  /** a type's custom read/write strategy — the Kindlings codec's counterpart.
    *
    * `read` returns `Object`, not `T`, and that is deliberate. Java declares `T read(…)`, but every
    * serializer libGDX registers is a RAW `new ReadOnlySerializer() {…}`, so javac checks the body
    * at the ERASED signature and never verifies the result against `T`. `Skin` relies on exactly
    * that: the serializer registered for `TintedDrawable` returns whatever `newDrawable` gives it,
    * a plain `Drawable`, which is NOT a `TintedDrawable`. Declaring `read: T` would make the raw
    * registration untranslatable — the only types satisfying both the anonymous body and the
    * `setSerializer` argument are contradictory, and Scala resolves that to `Nothing`.
    *
    * So the erased contract is not a weakening for convenience; it is the contract libGDX's call
    * sites actually depend on, and writing it down is what lets them port at all. An override
    * MAY still narrow the result (covariant return), and the ported `Color` serializer does. */
  trait Serializer[T]:
    def write(json: Json, `object`: T, knownType: Class[?]): Unit
    def read(json: Json, jsonData: JsonValue, `type`: Class[?]): Object

  abstract class ReadOnlySerializer[T] extends Serializer[T]:
    def write(json: Json, `object`: T, knownType: Class[?]): Unit = ()
    def read(json: Json, jsonData: JsonValue, `type`: Class[?]): Object

  /** implemented by types that serialize themselves — the non-reflective path, kept fully working. */
  trait Serializable:
    def write(json: Json): Unit
    def read(json: Json, jsonData: JsonValue): Unit
