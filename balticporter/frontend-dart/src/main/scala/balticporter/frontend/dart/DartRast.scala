package balticporter.frontend.dart

import com.github.plokhotnyuk.jsoniter_scala.core.*
import com.github.plokhotnyuk.jsoniter_scala.macros.*

/** Resolved AST (RAST) for Dart — the JSON interchange format between the Dart
  * analyzer exporter and the Scala frontend. Mirrors the TS RAST but with
  * Dart-specific constructs: mixins, extensions, named parameters, factory
  * constructors, late fields, cascade expressions, pattern matching. */

final case class DartRastFile(
    version: Int,
    path: String,
    sha256: String,
    nodes: List[DartRastNode],
    symbols: Map[String, DartRastSymbol],
    types: Map[String, DartRastType],
)

final case class DartRastNode(
    kind: String,
    pos: (Int, Int),
    children: List[DartRastNode] = Nil,
    symbol: Option[String] = None,
    `type`: Option[String] = None,
    resolvedSymbol: Option[String] = None,
    value: Option[DartRastValue] = None,
    text: Option[String] = None,
    operator: Option[String] = None,
    flags: List[String] = Nil,
    comments: List[DartRastComment] = Nil,
    // Dart-specific
    namedArgs: Option[Map[String, String]] = None,
    isFactory: Option[Boolean] = None,
    isLate: Option[Boolean] = None,
    isExtension: Option[Boolean] = None,
    mixins: Option[List[String]] = None,
    withClauses: Option[List[String]] = None,
)

final case class DartRastSymbol(
    name: String,
    flags: List[String] = Nil,
    declarationType: Option[String] = None,
    parent: Option[String] = None,
    isNullable: Option[Boolean] = None,
)

final case class DartRastType(
    kind: String,
    text: String,
    isNullable: Boolean = false,
    members: Option[Map[String, String]] = None,
    types: Option[List[String]] = None,
    target: Option[String] = None,
    typeArguments: Option[List[String]] = None,
    parameters: Option[List[DartRastParam]] = None,
    returnType: Option[String] = None,
    elementType: Option[String] = None,
    value: Option[DartRastValue] = None,
    // Dart-specific
    bound: Option[String] = None,
)

final case class DartRastParam(
    name: String,
    `type`: String,
    isNamed: Boolean = false,
    isOptional: Boolean = false,
    hasDefault: Boolean = false,
    defaultValue: Option[String] = None,
)

final case class DartRastComment(
    kind: String,
    text: String,
    pos: (Int, Int),
)

type DartRastValue = balticporter.frontend.ts.RastValue

object DartRast:
  given fileCodec: JsonValueCodec[DartRastFile] = JsonCodecMaker.make(
    CodecMakerConfig
      .withDiscriminatorFieldName(None)
      .withAllowRecursiveTypes(true)
  )

  def readFile(path: java.nio.file.Path): DartRastFile =
    val bytes = java.nio.file.Files.readAllBytes(path)
    readFromArray[DartRastFile](bytes)

  def readFile(json: String): DartRastFile =
    readFromString[DartRastFile](json)
