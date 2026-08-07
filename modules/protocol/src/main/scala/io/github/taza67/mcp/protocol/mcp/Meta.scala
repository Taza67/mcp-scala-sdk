package io.github.taza67.mcp.protocol.mcp

import io.github.taza67.mcp.protocol.json.JsonValue
import io.github.taza67.mcp.protocol.jsonrpc.RequestId



/** Contents of a `_meta` field: open metadata attached to MCP interactions.
 *
 *  Certain key names are reserved for protocol-level metadata; implementations
 *  MUST NOT assume meanings for reserved keys beyond what the protocol defines.
 *
 *  Valid keys have an optional reverse-DNS '''prefix''' ending with `/`, then a
 *  '''name'''. Prefixes whose second label is `modelcontextprotocol` or `mcp`
 *  are reserved for MCP use.
 *
 *  @param value Arbitrary metadata entries keyed by string.
 */
case class MetaObject(value: Map[String, JsonValue] = Map.empty)

object MetaObject {
  val empty: MetaObject = MetaObject()
}

/** Opaque token associating progress notifications with the original request. */
sealed trait ProgressToken

case class StringProgressToken(value: String) extends ProgressToken

case class NumberProgressToken(value: Long) extends ProgressToken

/** How a [[Result]] should be interpreted by the client.
 *
 *  - [[CompleteResultType]]: the request finished; the result holds final content.
 *  - [[InputRequiredResultType]]: more input is needed before retrying.
 *  - [[CustomResultType]]: extension or unrecognized discriminant.
 */
sealed trait ResultType {
  def value: String
}

object ResultType {

  /** Classify a wire `resultType` string (unknown values become [[CustomResultType]]). */
  def fromValue(value: String): ResultType =
    value match {
      case CompleteResultType.value      => CompleteResultType
      case InputRequiredResultType.value => InputRequiredResultType
      case other                         => CustomResultType(other)
    }
}

/** The request completed successfully; the result contains the final content. */
case object CompleteResultType extends ResultType {
  val value: String = "complete"
}

/** The request needs additional input before it can be completed. */
case object InputRequiredResultType extends ResultType {
  val value: String = "input_required"
}

/** Extension or unrecognized `resultType` string. */
case class CustomResultType(value: String) extends ResultType

/** Request `_meta`: protocol version, client identity/capabilities, and extensions.
 *
 *  Extends [[MetaObject]] with reserved MCP keys. All MetaObject key-naming rules apply.
 *
 *  @param protocolVersion MCP protocol version for this request (required).
 *                         On HTTP this MUST match the `MCP-Protocol-Version` header.
 *  @param clientCapabilities Capabilities for '''this''' request (required). Empty means
 *                            no optional capabilities. Servers MUST NOT infer from prior requests.
 *  @param clientInfo Self-reported client software identity. Clients SHOULD include it.
 *                    Intended for display/logging; not for security decisions.
 *  @param logLevel Opt-in log level for this request. If absent, the server MUST NOT
 *                  send `notifications/message` for the request. Deprecated as of
 *                  2026-07-28 (SEP-2577).
 *  @param progressToken When set, the caller requests out-of-band `notifications/progress`
 *                       tagged with this opaque token. The receiver is not obligated to send them.
 *  @param extensions Additional `_meta` keys beyond the reserved fields.
 */
case class RequestMeta(
    protocolVersion: McpProtocolVersion,
    clientCapabilities: ClientCapabilities,
    clientInfo: Option[Implementation] = None,
    logLevel: Option[LoggingLevel] = None,
    progressToken: Option[ProgressToken] = None,
    extensions: MetaObject = MetaObject.empty
)

/** Wire key names for [[RequestMeta]] (schema vocabulary; assembly stays in codec). */
object RequestMeta {
  val ProgressTokenKey: String = "progressToken"
  val ProtocolVersionKey: String = "io.modelcontextprotocol/protocolVersion"
  val ClientInfoKey: String = "io.modelcontextprotocol/clientInfo"
  val ClientCapabilitiesKey: String = "io.modelcontextprotocol/clientCapabilities"
  val LogLevelKey: String = "io.modelcontextprotocol/logLevel"

  /** All reserved wire keys; used to split/sanitize [[RequestMeta.extensions]]. */
  val ReservedKeys: Set[String] = Set(
    ProgressTokenKey,
    ProtocolVersionKey,
    ClientInfoKey,
    ClientCapabilitiesKey,
    LogLevelKey
  )
}

/** Notification `_meta`, including optional subscription correlation.
 *
 *  @param subscriptionId JSON-RPC id of the `subscriptions/listen` request that opened
 *                        the stream. Required on notifications delivered via that stream;
 *                        absent for in-band notifications (e.g. progress for an in-flight request).
 *  @param extensions Additional `_meta` keys beyond the reserved fields.
 */
case class NotificationMeta(
    subscriptionId: Option[RequestId] = None,
    extensions: MetaObject = MetaObject.empty
)

object NotificationMeta {

  /** Wire key names for [[NotificationMeta]] (schema vocabulary; assembly stays in codec). */
  val SubscriptionIdKey: String = "io.modelcontextprotocol/subscriptionId"

  /** All reserved wire keys; used to split/sanitize [[NotificationMeta.extensions]]. */
  val ReservedKeys: Set[String] = Set(SubscriptionIdKey)
}

/** Result `_meta` attached to successful MCP results.
 *
 *  @param serverInfo Self-reported server software identity. Servers SHOULD include it
 *                    on every response. Intended for display/logging; not for security decisions.
 *  @param extensions Additional `_meta` keys beyond the reserved fields.
 */
case class ResultMeta(
    serverInfo: Option[Implementation] = None,
    extensions: MetaObject = MetaObject.empty
)

object ResultMeta {

  /** Wire key names for [[ResultMeta]] (schema vocabulary; assembly stays in codec). */
  val ServerInfoKey: String = "io.modelcontextprotocol/serverInfo"

  /** All reserved wire keys; used to split/sanitize [[ResultMeta.extensions]]. */
  val ReservedKeys: Set[String] = Set(ServerInfoKey)
}
