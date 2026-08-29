package io.github.taza67.mcp.codec.ziojson

import scala.util.control.NonFatal

import zio.Chunk
import zio.json.JsonDecoder
import zio.json.JsonError
import zio.json.ast.{Json => ZioJson}
import zio.json.internal.RetractReader

import io.github.taza67.mcp.codec.DecodingError
import io.github.taza67.mcp.codec.{Decoder => CodecDecoder}
import io.github.taza67.mcp.codec.{Encoder => CodecEncoder}
import io.github.taza67.mcp.protocol.json.JsonArray
import io.github.taza67.mcp.protocol.json.JsonBool
import io.github.taza67.mcp.protocol.json.JsonNull
import io.github.taza67.mcp.protocol.json.JsonNumber
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.json.JsonValue



/** zio-json wire facade for protocol [[JsonValue]]: AST to/from JSON text.
 *
 *  Keeps zio-json confined to this module; the protocol AST stays codec-neutral.
 */
object JsonCodec {

  private def toZioJson(v: JsonValue): ZioJson =
    v match {
      case JsonNull      => ZioJson.Null
      case JsonBool(b)   => ZioJson.Bool(b)
      case JsonNumber(n) => ZioJson.Num(n.bigDecimal)
      case JsonString(s) => ZioJson.Str(s)
      case JsonArray(a)  => ZioJson.Arr(Chunk.fromIterable(a.map(toZioJson)))
      case JsonObject(o) =>
        ZioJson.Obj(Chunk.fromIterable(o.map { case (k, value) => k -> toZioJson(value) }))
    }

  implicit object JsonValueEncoder extends CodecEncoder[JsonValue] {
    def encode(value: JsonValue): String =
      ZioJson.encoder.encodeJson(toZioJson(value), None).toString
  }

  private def fromZioJson(v: ZioJson): JsonValue =
    v match {
      case ZioJson.Null      => JsonNull
      case ZioJson.Bool(b)   => JsonBool(b)
      case ZioJson.Num(n)    => JsonNumber(BigDecimal(n))
      case ZioJson.Str(s)    => JsonString(s)
      case ZioJson.Arr(items) =>
        JsonArray(items.map(fromZioJson).toList)
      case ZioJson.Obj(fields) =>
        JsonObject(
          fields.foldLeft(Map.empty[String, JsonValue]) { case (acc, (key, value)) =>
            acc + (key -> fromZioJson(value))
          }
        )
    }

  /** End-of-input guard: `decodeJson` does not check trailing input, so only
   *  JSON whitespace may remain after the decoded value.
   */
  private object EndOfInput extends JsonDecoder[Unit] {
    def unsafeDecode(trace: List[JsonError], in: RetractReader): Unit = {
      var c = in.read()
      while (c != -1) {
        c.toChar match {
          case ' ' | '\t' | '\r' | '\n' => ()
          case _ => throw new IllegalArgumentException("Trailing JSON data")
        }
        c = in.read()
      }
    }
  }

  /** zio-json `decodeJson` does not check trailing input: decode the value,
   *  then run the end-of-input guard on the same reader.
   */
  private val strictParser: JsonDecoder[ZioJson] =
    new JsonDecoder[ZioJson] {
      def unsafeDecode(trace: List[JsonError], in: RetractReader): ZioJson = {
        val value = ZioJson.decoder.unsafeDecode(trace, in)
        EndOfInput.unsafeDecode(trace, in)
        value
      }
    }

  implicit object JsonValueDecoder extends CodecDecoder[JsonValue] {
    def decode(value: String): Either[DecodingError, JsonValue] =
      try
        strictParser.decodeJson(value) match {
          case Left(_)     => Left(DecodingError("Invalid JSON"))
          case Right(json) => Right(fromZioJson(json))
        }
      catch {
        case NonFatal(_) => Left(DecodingError("Invalid JSON"))
      }
  }
}
