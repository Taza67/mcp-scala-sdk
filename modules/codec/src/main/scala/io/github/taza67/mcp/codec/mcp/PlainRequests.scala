package io.github.taza67.mcp.codec.mcp

import io.github.taza67.mcp.codec.DecodingError
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonValue
import io.github.taza67.mcp.protocol.jsonrpc.JsonRpcVersion
import io.github.taza67.mcp.protocol.jsonrpc.Method
import io.github.taza67.mcp.protocol.jsonrpc.RequestId
import io.github.taza67.mcp.protocol.mcp.McpRequest
import io.github.taza67.mcp.protocol.mcp.RequestParams



/** Shared encode/decode for domain requests whose params are plain [[RequestParams]]. */
private[mcp] object PlainRequests {

  def fromRequest(
      method: Method,
      id: RequestId,
      params: RequestParams,
      jsonrpc: JsonRpcVersion
  ): JsonObject =
    Messages.fromRequest(
      McpRequest(method = method, id = id, params = Some(params), jsonrpc = jsonrpc)
    )

  def toRequest[P, A](
      expected: Method,
      message: JsonValue
  )(
      decodeParams: RequestParams => Either[DecodingError, P]
  )(
      build: (RequestId, P, JsonRpcVersion) => A
  ): Either[DecodingError, A] =
    Messages.toMessage(message).flatMap {
      case McpRequest(`expected`, id, Some(params: RequestParams), jsonrpc) =>
        decodeParams(params).map(p => build(id, p, jsonrpc))
      case McpRequest(`expected`, _, None, _) =>
        Left(DecodingError(s"${expected.value} requires params"))
      case McpRequest(`expected`, _, Some(_), _) =>
        Left(DecodingError(s"${expected.value} requires plain request params"))
      case _ =>
        Left(DecodingError(s"Expected ${expected.value} request"))
    }
}
