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

  /** Exact JSON number grammar (RFC 8259): no leading zeros, digits required
   *  on both sides of the fraction, and a non-empty exponent body.
   */
  private val NumberPattern =
    "-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?".r.pattern

  /** Preflight numeric lexemes outside strings: zio-json 0.7.44 accepts
   *  invalid literals such as `01`, `-01`, and `1.`, so number-shaped tokens
   *  are checked against [[NumberPattern]] before the library parses them.
   *  String contents (including escaped quotes) are skipped untouched.
   */
  private def validNumberLexemes(input: String): Boolean = {
    var i = 0
    var inString = false
    var escaped = false
    val len = input.length
    var ok = true
    while (ok && i < len) {
      val c = input.charAt(i)
      if (inString) {
        if (escaped) escaped = false
        else if (c == '\\') escaped = true
        else if (c == '"') inString = false
        i += 1
      } else if (c == '"') {
        inString = true
        i += 1
      } else if ((c >= '0' && c <= '9') || c == '-') {
        var j = i + 1
        while (
          j < len && {
            val t = input.charAt(j)
            (t >= '0' && t <= '9') || t == '+' || t == '-' || t == '.' ||
              t == 'e' || t == 'E'
          }
        ) j += 1
        ok = NumberPattern.matcher(input.substring(i, j)).matches()
        i = j
      } else i += 1
    }
    ok
  }

  implicit object JsonValueDecoder extends CodecDecoder[JsonValue] {
    def decode(value: String): Either[DecodingError, JsonValue] =
      // The appended whitespace normalizes zio-json's boundary-char retraction:
      // a number lexer that hits raw EOF leaves its last digit in the reader,
      // which the end-of-input guard would otherwise reject as trailing data.
      if (!validNumberLexemes(value)) Left(DecodingError("Invalid JSON"))
      else
        try
          strictParser.decodeJson(value + ' ') match {
            case Left(_)     => Left(DecodingError("Invalid JSON"))
            case Right(json) => Right(fromZioJson(json))
          }
        catch {
          case NonFatal(_) => Left(DecodingError("Invalid JSON"))
        }
  }
}
