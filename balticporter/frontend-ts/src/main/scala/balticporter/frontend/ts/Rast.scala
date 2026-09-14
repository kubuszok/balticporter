package balticporter.frontend.ts

import com.github.plokhotnyuk.jsoniter_scala.core.*
import com.github.plokhotnyuk.jsoniter_scala.macros.*

/** Resolved AST (RAST) v1 — the JSON interchange format between the Node.js TypeScript exporter and the Scala frontend. Every lowering decision lives in Scala; the exporter is deliberately dumb.
  */

final case class RastFile(
  version: Int,
  path:    String,
  sha256:  String,
  nodes:   List[RastNode],
  symbols: Map[String, RastSymbol],
  types:   Map[String, RastType]
)

final case class RastNode(
  kind:           String,
  kindCode:       Int,
  pos:            (Int, Int),
  children:       List[RastNode] = Nil,
  symbol:         Option[String] = None,
  `type`:         Option[String] = None,
  resolvedSymbol: Option[String] = None,
  value:          Option[RastValue] = None,
  text:           Option[String] = None,
  operator:       Option[String] = None,
  flags:          List[String] = Nil,
  comments:       List[RastComment] = Nil
)

final case class RastSymbol(
  name:            String,
  flags:           List[String] = Nil,
  declarationType: Option[String] = None,
  parent:          Option[String] = None
)

final case class RastType(
  kind:          String,
  text:          String,
  members:       Option[Map[String, String]] = None,
  types:         Option[List[String]] = None,
  target:        Option[String] = None,
  typeArguments: Option[List[String]] = None,
  parameters:    Option[List[RastParam]] = None,
  returnType:    Option[String] = None,
  elementType:   Option[String] = None,
  value:         Option[RastValue] = None
)

final case class RastParam(
  name:     String,
  `type`:   String,
  optional: Boolean = false
)

final case class RastComment(
  kind: String,
  text: String,
  pos:  (Int, Int)
)

enum RastValue:
  case Str(v: String)
  case Num(v: Double)
  case Bool(v: Boolean)

object RastValue:
  given codec: JsonValueCodec[RastValue] = new JsonValueCodec[RastValue]:
    def decodeValue(in: JsonReader, default: RastValue): RastValue =
      in.nextToken() match
        case '"' =>
          in.rollbackToken()
          Str(in.readString(null))
        case 't' | 'f' =>
          in.rollbackToken()
          Bool(in.readBoolean())
        case _ =>
          in.rollbackToken()
          Num(in.readDouble())
    def encodeValue(x: RastValue, out: JsonWriter): Unit = x match
      case Str(v)  => out.writeVal(v)
      case Num(v)  => out.writeVal(v)
      case Bool(v) => out.writeVal(v)
    def nullValue: RastValue = null

object Rast:
  given fileCodec: JsonValueCodec[RastFile] = JsonCodecMaker.make(
    CodecMakerConfig.withDiscriminatorFieldName(None).withAllowRecursiveTypes(true).withMapMaxInsertNumber(1000000).withSetMaxInsertNumber(1000000)
  )

  def readFile(path: java.nio.file.Path): RastFile =
    val bytes = java.nio.file.Files.readAllBytes(path)
    readFromArray[RastFile](bytes)

  def readFile(json: String): RastFile =
    readFromString[RastFile](json)
