package io.github.taza67.mcp.codec.circe

import io.circe.{Json => CirceJson, JsonObject => CirceJsonObject}
import io.circe.parser.{parse => circeParse}
import io.github.taza67.mcp.codec.{Decoder => McpCodecDecoder, Encoder => McpCodecEncoder}
import io.github.taza67.mcp.codec.DecodingError
import io.github.taza67.mcp.protocol.json.{
  JsonArray => ProtocolJsonArray,
  JsonBool => ProtocolJsonBool,
  JsonNull => ProtocolJsonNull,
  JsonNumber => ProtocolJsonNumber,
  JsonObject => ProtocolJsonObject,
  JsonString => ProtocolJsonString,
  JsonValue => ProtocolJsonValue
}



/** Circe bridge for the protocol JSON AST:
 *  [[io.github.taza67.mcp.protocol.json.JsonValue]] ↔ JSON text.
 *
 *  Keeps Circe confined to this module; the protocol AST stays codec-neutral.
 */
object JsonCodec {
  private def toCirce(v: ProtocolJsonValue): CirceJson =
    v match {
      case ProtocolJsonNull      => CirceJson.Null
      case ProtocolJsonBool(b)   => CirceJson.fromBoolean(b)
      case ProtocolJsonNumber(n) => CirceJson.fromBigDecimal(n)
      case ProtocolJsonString(s) => CirceJson.fromString(s)
      case ProtocolJsonArray(a)  => CirceJson.fromValues(a.map(toCirce(_)))
      case ProtocolJsonObject(o) =>
        CirceJson.fromJsonObject(CirceJsonObject.fromMap(o.transform { case (_, value) =>
          toCirce(value)
        }))
    }

  implicit object JsonValueEncoder extends McpCodecEncoder[ProtocolJsonValue] {
    def encode(value: ProtocolJsonValue): String =
      toCirce(value).noSpaces
  }

  private def fromCirce(v: CirceJson): ProtocolJsonValue =
    v.fold(
      ProtocolJsonNull,
      b => ProtocolJsonBool(b),
      n => ProtocolJsonNumber(n.toBigDecimal.getOrElse(BigDecimal(n.toString))),
      s => ProtocolJsonString(s),
      a => ProtocolJsonArray(a.map(fromCirce).toList),
      o => ProtocolJsonObject(o.toMap.transform { case (_, value) => fromCirce(value) })
    )

  implicit object JsonValueDecoder extends McpCodecDecoder[ProtocolJsonValue] {
    def decode(value: String): Either[DecodingError, ProtocolJsonValue] =
      circeParse(value) match {
        case Left(e)  => Left(DecodingError(e.message))
        case Right(j) => Right(fromCirce(j))
      }
  }
}
