package io.github.taza67.mcp.codec.mcp

import io.github.taza67.mcp.codec.DecodingError
import io.github.taza67.mcp.codec.Fields
import io.github.taza67.mcp.codec.jsonrpc.{Messages => JsonRpcMessages}
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonValue
import io.github.taza67.mcp.protocol.jsonrpc.JsonRpcVersion
import io.github.taza67.mcp.protocol.jsonrpc.Method
import io.github.taza67.mcp.protocol.jsonrpc.Request
import io.github.taza67.mcp.protocol.jsonrpc.RequestId



/** Shared encode/decode for domain requests whose params are plain [[JsonObject]] bags. */
private[mcp] object ObjectRequests {

  def fromRequest(
      method: Method,
      id: RequestId,
      params: Option[JsonObject],
      jsonrpc: JsonRpcVersion
  ): JsonObject =
    JsonRpcMessages.fromMessage(
      Request(method = method, id = id, params = params, jsonrpc = jsonrpc)
    )

  def fromRequest(
      method: Method,
      id: RequestId,
      params: JsonObject,
      jsonrpc: JsonRpcVersion
  ): JsonObject =
    fromRequest(method, id, Some(params), jsonrpc)

  def toRequest[P, A](
      expected: Method,
      message: JsonValue
  )(
      decodeParams: JsonObject => Either[DecodingError, P]
  )(
      build: (RequestId, P, JsonRpcVersion) => A
  ): Either[DecodingError, A] =
    JsonRpcMessages.toMessage(message).flatMap {
      case Request(`expected`, id, params, jsonrpc) =>
        params
          .toRight(DecodingError(s"${expected.value} requires params"))
          .flatMap(decodeParams)
          .map(p => build(id, p, jsonrpc))
      case _ =>
        Left(DecodingError(s"Expected ${expected.value} request"))
    }

  def toRequestOptional[P, A](
      expected: Method,
      message: JsonValue
  )(
      decodeParams: JsonObject => Either[DecodingError, P]
  )(
      build: (RequestId, Option[P], JsonRpcVersion) => A
  ): Either[DecodingError, A] =
    JsonRpcMessages.toMessage(message).flatMap {
      case Request(`expected`, id, params, jsonrpc) =>
        Fields.traverseOptional(params)(decodeParams).map(p => build(id, p, jsonrpc))
      case _ =>
        Left(DecodingError(s"Expected ${expected.value} request"))
    }
}
