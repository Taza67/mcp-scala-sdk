package io.github.taza67.mcp.codec.jsonrpc

import io.github.taza67.mcp.codec.DecodingError
import io.github.taza67.mcp.protocol.json.JsonNumber
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.json.JsonValue
import io.github.taza67.mcp.protocol.jsonrpc.JsonRpcVersion
import io.github.taza67.mcp.protocol.jsonrpc.JsonRpcVersion20
import io.github.taza67.mcp.protocol.jsonrpc.Method
import io.github.taza67.mcp.protocol.jsonrpc.NumberRequestId
import io.github.taza67.mcp.protocol.jsonrpc.RequestId
import io.github.taza67.mcp.protocol.jsonrpc.StringRequestId



/** Encode/decode helpers for JSON-RPC scalar / id fields. */
private[jsonrpc] object Primitives {

  def fromJsonRpcVersion(jsonRpcVersion: JsonRpcVersion): JsonString =
    JsonString(jsonRpcVersion.value)

  def fromMethod(method: Method): JsonString =
    JsonString(method.value)

  def fromRequestId(requestId: RequestId): JsonValue =
    requestId match {
      case StringRequestId(value) => JsonString(value)
      case NumberRequestId(value) => JsonNumber(value)
    }

  def toJsonRpcVersion(version: JsonValue): Either[DecodingError, JsonRpcVersion] =
    version match {
      case JsonString(value) if value == "2.0" => Right(JsonRpcVersion20)
      case _                                   => Left(DecodingError("Invalid version"))
    }

  def toMethod(method: JsonValue): Either[DecodingError, Method] =
    method match {
      case JsonString(value) if !value.isBlank() => Right(Method(value))
      case _                                     => Left(DecodingError("Invalid method"))
    }

  def toRequestId(requestId: JsonValue): Either[DecodingError, RequestId] =
    requestId match {
      case JsonNumber(value)                     => Right(NumberRequestId(value.toLongExact))
      case JsonString(value) if !value.isBlank() => Right(StringRequestId(value))
      case _                                     => Left(DecodingError("Invalid request ID"))
    }

  def toErrorCode(code: JsonValue): Either[DecodingError, Int] =
    code match {
      case JsonNumber(value) if value.isValidInt => Right(value.toIntExact)
      case JsonNumber(_) =>
        Left(DecodingError("Invalid error code: expected a 32-bit integer"))
      case _ => Left(DecodingError("Invalid error code: expected a number"))
    }

  def toErrorMessage(message: JsonValue): Either[DecodingError, String] =
    message match {
      case JsonString(value) => Right(value)
      case _                 => Left(DecodingError("Invalid error message: expected a string"))
    }
}
