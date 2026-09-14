package io.github.taza67.mcp.codec.mcp.notifications

import io.github.taza67.mcp.codec.DecodingError
import io.github.taza67.mcp.codec.Fields
import io.github.taza67.mcp.codec.Primitives
import io.github.taza67.mcp.codec.mcp.Meta
import io.github.taza67.mcp.codec.mcp.PlainNotifications
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.json.JsonValue
import io.github.taza67.mcp.protocol.mcp.NotificationParams
import io.github.taza67.mcp.protocol.mcp.notifications.CancelledNotification
import io.github.taza67.mcp.protocol.mcp.notifications.CancelledNotificationParams
import io.github.taza67.mcp.protocol.mcp.notifications.LoggingMessageNotification
import io.github.taza67.mcp.protocol.mcp.notifications.LoggingMessageNotificationParams
import io.github.taza67.mcp.protocol.mcp.notifications.ProgressNotification
import io.github.taza67.mcp.protocol.mcp.notifications.ProgressNotificationParams
import io.github.taza67.mcp.protocol.mcp.notifications.{Notifications => NotificationMethods}



/** Protocol AST bridge for MCP notification-domain types (`JsonObject` / `JsonValue` to/from ADT). */
object Notifications {

  /** Progress counters must stay finite: a huge `JsonNumber` (e.g. `1e10000`)
   *  converts to a non-finite `Double` and a `NaN` / `Infinity` `Double` cannot
   *  be rendered as a JSON number at all.
   */
  private def toFiniteDouble(
      value: JsonValue,
      key: String
  ): Either[DecodingError, Double] =
    Primitives.asDouble(value, key).flatMap { d =>
      if (d.isFinite) Right(d)
      else Left(DecodingError(s"Invalid $key: expected a finite number"))
    }

  private def requireFinite(value: Double, key: String): Unit =
    require(value.isFinite, s"$key must be a finite number")

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

  def fromProgressNotificationParams(
      params: ProgressNotificationParams
  ): NotificationParams = {
    requireFinite(params.progress, ProgressNotificationParams.ProgressKey)
    params.total.foreach(
      requireFinite(_, ProgressNotificationParams.TotalKey)
    )
    NotificationParams(
      meta = params.meta,
      fields = JsonObject(
        Fields.withOptional(
          Map(
            ProgressNotificationParams.ProgressTokenKey ->
              Meta.fromProgressToken(params.progressToken),
            ProgressNotificationParams.ProgressKey ->
              Primitives.fromDouble(params.progress)
          ),
          ProgressNotificationParams.TotalKey ->
            params.total.map(Primitives.fromDouble),
          ProgressNotificationParams.MessageKey ->
            params.message.map(JsonString(_))
        )
      )
    )
  }

  def toProgressNotificationParams(
      params: NotificationParams
  ): Either[DecodingError, ProgressNotificationParams] = {
    val fields = params.fields.value
    for {
      progressToken <- Fields
        .required(fields, ProgressNotificationParams.ProgressTokenKey)
        .flatMap(Meta.toProgressToken)
      progress <- Fields
        .required(fields, ProgressNotificationParams.ProgressKey)
        .flatMap(toFiniteDouble(_, ProgressNotificationParams.ProgressKey))
      total <- Fields.optional(fields, ProgressNotificationParams.TotalKey)(v =>
        toFiniteDouble(v, ProgressNotificationParams.TotalKey)
      )
      message <- Fields.optionalString(fields, ProgressNotificationParams.MessageKey)
    } yield ProgressNotificationParams(
      progressToken = progressToken,
      progress = progress,
      total = total,
      message = message,
      meta = params.meta
    )
  }

  def fromProgressNotification(notification: ProgressNotification): JsonObject =
    PlainNotifications.fromNotification(
      method = NotificationMethods.progress,
      params = fromProgressNotificationParams(notification.params),
      jsonrpc = notification.jsonrpc
    )

  def toProgressNotification(
      message: JsonValue
  ): Either[DecodingError, ProgressNotification] =
    PlainNotifications.toNotification(NotificationMethods.progress, message)(
      toProgressNotificationParams
    ) { (params, jsonrpc) =>
      ProgressNotification(params = params, jsonrpc = jsonrpc)
    }

  def fromLoggingMessageNotificationParams(
      params: LoggingMessageNotificationParams
  ): NotificationParams =
    NotificationParams(
      meta = params.meta,
      fields = JsonObject(
        Fields.withOptional(
          Map(
            LoggingMessageNotificationParams.LevelKey ->
              Meta.fromLoggingLevel(params.level),
            LoggingMessageNotificationParams.DataKey -> params.data
          ),
          LoggingMessageNotificationParams.LoggerKey ->
            params.logger.map(JsonString(_))
        )
      )
    )

  def toLoggingMessageNotificationParams(
      params: NotificationParams
  ): Either[DecodingError, LoggingMessageNotificationParams] = {
    val fields = params.fields.value
    for {
      level <- Fields
        .required(fields, LoggingMessageNotificationParams.LevelKey)
        .flatMap(v =>
          Meta.toLoggingLevel(v).left.map(_ => DecodingError("Invalid logging level"))
        )
      data <- Fields.required(fields, LoggingMessageNotificationParams.DataKey)
      logger <- Fields.optionalString(fields, LoggingMessageNotificationParams.LoggerKey)
    } yield LoggingMessageNotificationParams(
      level = level,
      data = data,
      logger = logger,
      meta = params.meta
    )
  }

  def fromLoggingMessageNotification(
      notification: LoggingMessageNotification
  ): JsonObject =
    PlainNotifications.fromNotification(
      method = NotificationMethods.message,
      params = fromLoggingMessageNotificationParams(notification.params),
      jsonrpc = notification.jsonrpc
    )

  def toLoggingMessageNotification(
      message: JsonValue
  ): Either[DecodingError, LoggingMessageNotification] =
    PlainNotifications.toNotification(NotificationMethods.message, message)(
      toLoggingMessageNotificationParams
    ) { (params, jsonrpc) =>
      LoggingMessageNotification(params = params, jsonrpc = jsonrpc)
    }
}
