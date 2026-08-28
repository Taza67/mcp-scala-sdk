package io.github.taza67.mcp.codec.mcp

import io.github.taza67.mcp.codec.DecodingError
import io.github.taza67.mcp.codec.Fields
import io.github.taza67.mcp.codec.jsonrpc.{Messages => JsonRpcMessages}
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonValue
import io.github.taza67.mcp.protocol.jsonrpc.ErrorResponse
import io.github.taza67.mcp.protocol.jsonrpc.Notification
import io.github.taza67.mcp.protocol.jsonrpc.Request
import io.github.taza67.mcp.protocol.jsonrpc.SuccessResponse
import io.github.taza67.mcp.protocol.mcp.McpErrorResponse
import io.github.taza67.mcp.protocol.mcp.McpMessage
import io.github.taza67.mcp.protocol.mcp.McpNotification
import io.github.taza67.mcp.protocol.mcp.McpRequest
import io.github.taza67.mcp.protocol.mcp.McpSuccessResponse



/** Protocol AST bridge for MCP [[McpMessage]] envelopes (`JsonObject` ↔ ADT).
 *
 *  Domain façades: [[Content]] (content blocks), package [[lists]] (paginated
 *  lists), packages [[tools]] / [[resources]] / [[prompts]] / [[elicitation]] /
 *  [[discover]] / [[roots]] / [[sampling]] / [[completion]] (rich requests and
 *  results).
 *
 *  Deferred codec domains (protocol types exist; YAGNI for current cut — see
 *  ADR-0006): [[io.github.taza67.mcp.protocol.mcp.notifications]],
 *  [[io.github.taza67.mcp.protocol.mcp.subscriptions]].
 */
object Messages {

  def fromRequest(request: McpRequest): JsonObject =
    JsonRpcMessages.fromMessage(
      Request(
        method = request.method,
        id = request.id,
        params = request.params.map(Params.fromMcpRequestParams),
        jsonrpc = request.jsonrpc
      )
    )

  def fromNotification(notification: McpNotification): JsonObject =
    JsonRpcMessages.fromMessage(
      Notification(
        method = notification.method,
        params = notification.params.map(Params.fromNotificationParams),
        jsonrpc = notification.jsonrpc
      )
    )

  def fromSuccessResponse(response: McpSuccessResponse): JsonObject =
    JsonRpcMessages.fromMessage(
      SuccessResponse(
        result = Params.fromResult(response.result),
        id = response.id,
        jsonrpc = response.jsonrpc
      )
    )

  def fromErrorResponse(response: McpErrorResponse): JsonObject =
    JsonRpcMessages.fromMessage(
      ErrorResponse(
        error = response.error,
        id = response.id,
        jsonrpc = response.jsonrpc
      )
    )

  def fromMessage(message: McpMessage): JsonObject =
    message match {
      case r: McpRequest         => fromRequest(r)
      case n: McpNotification    => fromNotification(n)
      case s: McpSuccessResponse => fromSuccessResponse(s)
      case e: McpErrorResponse   => fromErrorResponse(e)
    }

  def toRequest(request: Request): Either[DecodingError, McpRequest] =
    Fields.traverseOptional(request.params)(Params.toMcpRequestParams(request.method, _)).map {
      params =>
        McpRequest(
          method = request.method,
          id = request.id,
          params = params,
          jsonrpc = request.jsonrpc
        )
    }

  def toNotification(notification: Notification): Either[DecodingError, McpNotification] =
    Fields.traverseOptional(notification.params)(Params.toNotificationParams).map { params =>
      McpNotification(
        method = notification.method,
        params = params,
        jsonrpc = notification.jsonrpc
      )
    }

  def toSuccessResponse(
      response: SuccessResponse
  ): Either[DecodingError, McpSuccessResponse] =
    for {
      resultObject <- Fields.asObject(response.result, "result")
      result <- Params.toResult(resultObject)
    } yield McpSuccessResponse(
      result = result,
      id = response.id,
      jsonrpc = response.jsonrpc
    )

  def toErrorResponse(response: ErrorResponse): Either[DecodingError, McpErrorResponse] =
    Right(
      McpErrorResponse(
        error = response.error,
        id = response.id,
        jsonrpc = response.jsonrpc
      )
    )

  def toMessage(message: JsonValue): Either[DecodingError, McpMessage] =
    JsonRpcMessages.toMessage(message).flatMap {
      case r: Request         => toRequest(r)
      case n: Notification    => toNotification(n)
      case s: SuccessResponse => toSuccessResponse(s)
      case e: ErrorResponse   => toErrorResponse(e)
    }
}
