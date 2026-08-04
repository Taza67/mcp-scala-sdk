package io.github.taza67.mcp.protocol.mcp

import io.github.taza67.mcp.protocol.jsonrpc.RequestId



/** A progress token, used to associate progress notifications with the original request (SPECS ProgressToken). */
sealed trait ProgressToken

case class StringProgressToken(value: String) extends ProgressToken

case class NumberProgressToken(value: Long) extends ProgressToken

/** Indicates the type of a Result object (SPECS ResultType). */
sealed trait ResultType {
  def value: String
}

case object CompleteResultType extends ResultType {
  val value: String = "complete"
}

case object InputRequiredResultType extends ResultType {
  val value: String = "input_required"
}

/** Extension or unrecognized `resultType` values (SPECS ResultType includes `string`). */
case class CustomResultType(value: String) extends ResultType

/** Request `_meta` (SPECS RequestMetaObject). Extends MetaObject with reserved keys. */
case class RequestMeta(
    /** MCP protocol version for this request. Required. */
    protocolVersion: McpProtocolVersion,
    /** Client capabilities for this request. Required; empty means no optional capabilities. */
    clientCapabilities: ClientCapabilities,
    /** Client software identity. Clients SHOULD include this. */
    clientInfo: Option[Implementation] = None,
    /** Opt-in log level for this request. Deprecated as of 2026-07-28. */
    logLevel: Option[LoggingLevel] = None,
    progressToken: Option[ProgressToken] = None,
    /** Additional `_meta` keys (SPECS index signature). */
    extensions: MetaObject = MetaObject.empty
)

/** Notification `_meta` (SPECS NotificationMetaObject). */
case class NotificationMeta(
    /** Correlates a notification with its `subscriptions/listen` stream. */
    subscriptionId: Option[RequestId] = None,
    extensions: MetaObject = MetaObject.empty
)

/** Result `_meta` (SPECS ResultMetaObject). */
case class ResultMeta(
    /** Server software identity. Servers SHOULD include this. */
    serverInfo: Option[Implementation] = None,
    extensions: MetaObject = MetaObject.empty
)
