package io.github.taza67.mcp.codec

import io.github.taza67.mcp.protocol.json.JsonArray
import io.github.taza67.mcp.protocol.json.JsonBool
import io.github.taza67.mcp.protocol.json.JsonNumber
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.json.JsonValue
import io.github.taza67.mcp.protocol.jsonrpc.NumberRequestId
import io.github.taza67.mcp.protocol.jsonrpc.RequestId
import io.github.taza67.mcp.protocol.jsonrpc.StringRequestId



/** Encode/decode helpers for scalar / collection AST values shared across codec packages. */
private[codec] object Primitives {

  def fromList[A](items: List[A])(f: A => JsonValue): JsonArray =
    JsonArray(items.map(f))

  def fromBool(value: Boolean): JsonBool =
    JsonBool(value)

  def fromRequestId(requestId: RequestId): JsonValue =
    requestId match {
      case StringRequestId(value) => JsonString(value)
      case NumberRequestId(value) => JsonNumber(value)
    }

  def asString(
      value: JsonValue,
      label: String = "string"
  ): Either[DecodingError, String] =
    value match {
      case JsonString(s) => Right(s)
      case _             => Left(DecodingError(s"Invalid $label: expected a string"))
    }

  def asBool(
      value: JsonValue,
      label: String = "boolean"
  ): Either[DecodingError, Boolean] =
    value match {
      case JsonBool(b) => Right(b)
      case _           => Left(DecodingError(s"Invalid $label: expected a boolean"))
    }

  def asArray(
      value: JsonValue,
      label: String = "array"
  ): Either[DecodingError, JsonArray] =
    value match {
      case a: JsonArray => Right(a)
      case _            => Left(DecodingError(s"Invalid $label: expected array"))
    }

  /** Decode an integral JSON number without throwing on non-integral / out-of-range values. */
  def asLong(
      value: JsonValue,
      label: String = "number"
  ): Either[DecodingError, Long] =
    value match {
      case JsonNumber(n) =>
        try Right(n.toLongExact)
        catch {
          case _: ArithmeticException =>
            Left(DecodingError(s"Invalid $label: expected an integral number"))
        }
      case _ => Left(DecodingError(s"Invalid $label: expected a number"))
    }

  /** Decode a 32-bit integral JSON number without throwing on out-of-range values. */
  def asInt(
      value: JsonValue,
      label: String = "number"
  ): Either[DecodingError, Int] =
    value match {
      case JsonNumber(n) =>
        try Right(n.toIntExact)
        catch {
          case _: ArithmeticException =>
            Left(DecodingError(s"Invalid $label: expected a 32-bit integer"))
        }
      case _ => Left(DecodingError(s"Invalid $label: expected a number"))
    }

  /** Decode a JSON string-or-number id (non-blank string, or integral number).
   *  Used for JSON-RPC request ids and MCP progress tokens.
   */
  def asStringOrLong[A](
      value: JsonValue,
      label: String
  )(fromString: String => A, fromLong: Long => A): Either[DecodingError, A] =
    value match {
      case JsonNumber(_)                 => asLong(value, label).map(fromLong)
      case JsonString(s) if !s.isBlank() => Right(fromString(s))
      case _                             => Left(DecodingError(s"Invalid $label"))
    }

  /** Decode a JSON array by applying `f` to each element (cats-free traverse). */
  def toList[A](
      value: JsonValue,
      label: String = "array"
  )(f: JsonValue => Either[DecodingError, A]): Either[DecodingError, List[A]] =
    asArray(value, label).flatMap { arr =>
      arr.value
        .foldLeft[Either[DecodingError, List[A]]](Right(Nil)) { (acc, elem) =>
          for {
            xs <- acc
            x <- f(elem)
          } yield x :: xs
        }
        .map(_.reverse)
    }

  def toRequestId(requestId: JsonValue): Either[DecodingError, RequestId] =
    asStringOrLong(requestId, "request ID")(StringRequestId(_), NumberRequestId(_))
}
