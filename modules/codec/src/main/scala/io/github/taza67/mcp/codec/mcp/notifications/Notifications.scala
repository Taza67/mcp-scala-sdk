package io.github.taza67.mcp.codec.mcp.notifications

import io.github.taza67.mcp.codec.DecodingError
import io.github.taza67.mcp.codec.Fields
import io.github.taza67.mcp.codec.Primitives
import io.github.taza67.mcp.codec.mcp.PlainNotifications
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.json.JsonValue
import io.github.taza67.mcp.protocol.mcp.NotificationParams
import io.github.taza67.mcp.protocol.mcp.notifications.CancelledNotification
import io.github.taza67.mcp.protocol.mcp.notifications.CancelledNotificationParams
import io.github.taza67.mcp.protocol.mcp.notifications.{Notifications => NotificationMethods}



/** Protocol AST bridge for MCP notification-domain types (`JsonObject` / `JsonValue` to/from ADT). */
object Notifications {

  def fromCancelledNotificationParams(
      params: CancelledNotificationParams
  ): NotificationParams =
    NotificationParams(
      meta = params.meta,
      fields = JsonObject(
        Fields.withOptional(
          Map(
            CancelledNotificationParams.RequestIdKey ->
              Primitives.fromRequestId(params.requestId)
          ),
          CancelledNotificationParams.ReasonKey -> params.reason.map(JsonString(_))
        )
      )
    )

  def toCancelledNotificationParams(
      params: NotificationParams
  ): Either[DecodingError, CancelledNotificationParams] = {
    val fields = params.fields.value
    for {
      requestId <- Fields
        .required(fields, CancelledNotificationParams.RequestIdKey)
        .flatMap(Primitives.toRequestId)
      reason <- Fields.optionalString(fields, CancelledNotificationParams.ReasonKey)
    } yield CancelledNotificationParams(
      requestId = requestId,
      reason = reason,
      meta = params.meta
    )
  }

  def fromCancelledNotification(notification: CancelledNotification): JsonObject =
    PlainNotifications.fromNotification(
      method = NotificationMethods.cancelled,
      params = fromCancelledNotificationParams(notification.params),
      jsonrpc = notification.jsonrpc
    )

  def toCancelledNotification(
      message: JsonValue
  ): Either[DecodingError, CancelledNotification] =
    PlainNotifications.toNotification(NotificationMethods.cancelled, message)(
      toCancelledNotificationParams
    ) { (params, jsonrpc) =>
      CancelledNotification(params = params, jsonrpc = jsonrpc)
    }
}
