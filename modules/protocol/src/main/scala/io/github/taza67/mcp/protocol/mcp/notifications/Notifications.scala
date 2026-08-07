package io.github.taza67.mcp.protocol.mcp.notifications

import io.github.taza67.mcp.protocol.mcp._
import io.github.taza67.mcp.protocol.json.JsonValue
import io.github.taza67.mcp.protocol.jsonrpc.{JsonRpcVersion, JsonRpcVersion20, Method, RequestId}



/** Method names for core lifecycle / logging notifications. */
object Notifications {
  val progress: Method = Method("notifications/progress")
  val cancelled: Method = Method("notifications/cancelled")
  val message: Method = Method("notifications/message")
}

/** Parameters for a `notifications/progress` notification.
 *
 *  @param progressToken Opaque token from the related request's `_meta.progressToken`.
 *  @param progress Progress so far (units defined by the sender).
 *  @param total Expected total progress, if known.
 *  @param message Optional human-readable status message.
 *  @param meta Optional notification metadata.
 */
case class ProgressNotificationParams(
    progressToken: ProgressToken,
    progress: Double,
    total: Option[Double] = None,
    message: Option[String] = None,
    meta: Option[NotificationMeta] = None
)

/** Out-of-band progress update for a long-running request. */
case class ProgressNotification(
    params: ProgressNotificationParams,
    jsonrpc: JsonRpcVersion = JsonRpcVersion20
) {
  def toMcpNotification: McpNotification =
    McpNotification(
      method = Notifications.progress,
      params = Some(NotificationParams(meta = params.meta)),
      jsonrpc = jsonrpc
    )
}

/** Parameters for a `notifications/cancelled` notification.
 *
 *  @param requestId Id of the request being cancelled (or of `subscriptions/listen`
 *                   when the server uses this to tear down a listen stream on STDIO).
 *  @param reason Optional human-readable cancellation reason.
 *  @param meta Optional notification metadata.
 */
case class CancelledNotificationParams(
    requestId: RequestId,
    reason: Option[String] = None,
    meta: Option[NotificationMeta] = None
)

/** Indicates cancellation of a previously issued request.
 *
 *  Clients send this to cancel their own in-flight requests. On STDIO, servers
 *  also send it to terminate a `subscriptions/listen` stream by referencing that
 *  listen request's id. Servers MUST NOT require a response.
 */
case class CancelledNotification(
    params: CancelledNotificationParams,
    jsonrpc: JsonRpcVersion = JsonRpcVersion20
) {
  def toMcpNotification: McpNotification =
    McpNotification(
      method = Notifications.cancelled,
      params = Some(NotificationParams(meta = params.meta)),
      jsonrpc = jsonrpc
    )
}

/** Parameters for a `notifications/message` log notification.
 *
 *  @param level Syslog severity of the message.
 *  @param data Arbitrary log payload defined by the sender.
 *  @param logger Optional logger name / category.
 *  @param meta Optional notification metadata.
 */
case class LoggingMessageNotificationParams(
    level: LoggingLevel,
    data: JsonValue,
    logger: Option[String] = None,
    meta: Option[NotificationMeta] = None
)

/** Log message from server to client.
 *
 *  The client opts in by setting `io.modelcontextprotocol/logLevel` on a request's
 *  `_meta`. Deprecated as of protocol version 2026-07-28 (SEP-2577); remains for
 *  at least twelve months.
 */
case class LoggingMessageNotification(
    params: LoggingMessageNotificationParams,
    jsonrpc: JsonRpcVersion = JsonRpcVersion20
) {
  def toMcpNotification: McpNotification =
    McpNotification(
      method = Notifications.message,
      params = Some(NotificationParams(meta = params.meta)),
      jsonrpc = jsonrpc
    )
}
