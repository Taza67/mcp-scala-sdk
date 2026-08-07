package io.github.taza67.mcp.codec.jsonrpc

import io.github.taza67.mcp.codec.{Primitives => SharedPrimitives}
import io.github.taza67.mcp.codec.DecodingError
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.json.JsonValue
import io.github.taza67.mcp.protocol.jsonrpc.JsonRpcVersion
import io.github.taza67.mcp.protocol.jsonrpc.JsonRpcVersion20
import io.github.taza67.mcp.protocol.jsonrpc.Method
import io.github.taza67.mcp.protocol.jsonrpc.RequestId



/** Encode/decode helpers for JSON-RPC scalar / id fields. */
private[jsonrpc] object Primitives {

  def fromJsonRpcVersion(jsonRpcVersion: JsonRpcVersion): JsonString =
    JsonString(jsonRpcVersion.value)

  def fromMethod(method: Method): JsonString =
    JsonString(method.value)

  def fromRequestId(requestId: RequestId): JsonValue =
    SharedPrimitives.fromRequestId(requestId)

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
    SharedPrimitives.toRequestId(requestId)

  def toErrorCode(code: JsonValue): Either[DecodingError, Int] =
    SharedPrimitives.asInt(code, "error code")

  def toErrorMessage(message: JsonValue): Either[DecodingError, String] =
    SharedPrimitives.asString(message, "error message")
}
