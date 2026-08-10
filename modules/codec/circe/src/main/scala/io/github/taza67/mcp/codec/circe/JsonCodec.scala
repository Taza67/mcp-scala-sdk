package io.github.taza67.mcp.codec.circe

import io.circe.{Json => CirceJson, JsonObject => CirceJsonObject}
import io.circe.parser.{parse => circeParse}
import io.github.taza67.mcp.codec.{Decoder => CodecDecoder, Encoder => CodecEncoder}
import io.github.taza67.mcp.codec.DecodingError
import io.github.taza67.mcp.protocol.json.JsonArray
import io.github.taza67.mcp.protocol.json.JsonBool
import io.github.taza67.mcp.protocol.json.JsonNull
import io.github.taza67.mcp.protocol.json.JsonNumber
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.json.JsonValue



/** Circe wire façade for protocol [[JsonValue]]: AST ↔ JSON text.
 *
 *  Keeps Circe confined to this module; the protocol AST stays codec-neutral.
 */
object JsonCodec {
  private def toCirce(v: JsonValue): CirceJson =
    v match {
      case JsonNull      => CirceJson.Null
      case JsonBool(b)   => CirceJson.fromBoolean(b)
      case JsonNumber(n) => CirceJson.fromBigDecimal(n)
      case JsonString(s) => CirceJson.fromString(s)
      case JsonArray(a)  => CirceJson.fromValues(a.map(toCirce(_)))
      case JsonObject(o) =>
        CirceJson.fromJsonObject(CirceJsonObject.fromMap(o.transform { case (_, value) =>
          toCirce(value)
        }))
    }

  implicit object JsonValueEncoder extends CodecEncoder[JsonValue] {
    def encode(value: JsonValue): String =
      toCirce(value).noSpaces
  }

  private def fromCirce(v: CirceJson): Either[DecodingError, JsonValue] =
    if (v.isNull) Right(JsonNull)
    else if (v.isBoolean) Right(JsonBool(v.asBoolean.get))
    else if (v.isNumber)
      v.asNumber.get.toBigDecimal
        .toRight(DecodingError(s"Invalid JSON number: ${v.asNumber.get}"))
        .map(JsonNumber(_))
    else if (v.isString) Right(JsonString(v.asString.get))
    else if (v.isArray) v.asArray.get.foldLeft[Either[DecodingError, List[JsonValue]]](Right(Nil)) {
      case (Right(acc), elem) => fromCirce(elem).map(_ :: acc)
      case (left, _)          => left
    }.map(entries => JsonArray(entries.reverse))
    else
      v.asObject.get.toMap.foldLeft[Either[DecodingError, Map[String, JsonValue]]](Right(Map.empty)) {
        case (Right(acc), (key, value)) =>
          fromCirce(value).map(decoded => acc + (key -> decoded))
        case (left, _) => left
      }.map(entries => JsonObject(entries))

  implicit object JsonValueDecoder extends CodecDecoder[JsonValue] {
    def decode(value: String): Either[DecodingError, JsonValue] =
      circeParse(value) match {
        case Left(e)  => Left(DecodingError(e.message))
        case Right(j) => fromCirce(j)
      }
  }
}
