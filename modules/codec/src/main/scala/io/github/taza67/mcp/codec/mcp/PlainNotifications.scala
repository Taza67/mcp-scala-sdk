package io.github.taza67.mcp.codec.mcp

import io.github.taza67.mcp.codec.DecodingError
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonValue
import io.github.taza67.mcp.protocol.jsonrpc.JsonRpcVersion
import io.github.taza67.mcp.protocol.jsonrpc.Method
import io.github.taza67.mcp.protocol.mcp.McpNotification
import io.github.taza67.mcp.protocol.mcp.NotificationParams



/** Shared encode/decode for domain notifications carrying [[NotificationParams]]. */
private[mcp] object PlainNotifications {

  def fromNotification(
      method: Method,
      params: NotificationParams,
      jsonrpc: JsonRpcVersion
  ): JsonObject =
    Messages.fromNotification(
      McpNotification(method = method, params = Some(params), jsonrpc = jsonrpc)
    )

  def toNotification[P, A](
      expected: Method,
      message: JsonValue
  )(
      decodeParams: NotificationParams => Either[DecodingError, P]
  )(
      build: (P, JsonRpcVersion) => A
  ): Either[DecodingError, A] =
    Messages.toMessage(message).flatMap {
      case McpNotification(`expected`, Some(params), jsonrpc) =>
        decodeParams(params).map(p => build(p, jsonrpc))
      case McpNotification(`expected`, None, _) =>
        Left(DecodingError(s"${expected.value} requires params"))
      case _ =>
        Left(DecodingError(s"Expected ${expected.value} notification"))
    }
}
