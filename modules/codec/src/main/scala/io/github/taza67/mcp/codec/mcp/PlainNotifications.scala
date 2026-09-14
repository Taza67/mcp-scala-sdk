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

  /** Encode a notification whose `params` member is optional on the wire
   *  (e.g. `list_changed` notifications), preserving `None` vs `Some`.
   */
  def fromOptionalNotification(
      method: Method,
      params: Option[NotificationParams],
      jsonrpc: JsonRpcVersion
  ): JsonObject =
    Messages.fromNotification(
      McpNotification(method = method, params = params, jsonrpc = jsonrpc)
    )

  /** Decode a notification whose `params` member is optional on the wire;
   *  malformed or explicit-null `params` are rejected by the envelope codec.
   */
  def toOptionalNotification[A](
      expected: Method,
      message: JsonValue
  )(
      build: (Option[NotificationParams], JsonRpcVersion) => A
  ): Either[DecodingError, A] =
    Messages.toMessage(message).flatMap {
      case McpNotification(`expected`, params, jsonrpc) =>
        Right(build(params, jsonrpc))
      case _ =>
        Left(DecodingError(s"Expected ${expected.value} notification"))
    }
}
