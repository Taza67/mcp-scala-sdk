package io.github.taza67.mcp.protocol.mcp.subscriptions

import io.github.taza67.mcp.protocol.mcp._
import io.github.taza67.mcp.protocol.jsonrpc.{JsonRpcVersion, JsonRpcVersion20, Method, RequestId}



/** Method names for the subscriptions domain. */
object Subscriptions {
  val listen: Method = Method("subscriptions/listen")
  val acknowledgedNotification: Method = Method("notifications/subscriptions/acknowledged")
}

/** Notification types a client may opt into on a `subscriptions/listen` stream.
 *
 *  Each type is '''opt-in'''; the server MUST NOT send notification types the
 *  client has not explicitly requested here.
 *
 *  @param toolsListChanged Receive `notifications/tools/list_changed`.
 *  @param promptsListChanged Receive `notifications/prompts/list_changed`.
 *  @param resourcesListChanged Receive `notifications/resources/list_changed`.
 *  @param resourceSubscriptions URIs for `notifications/resources/updated`
 *                               (replaces the former `resources/subscribe` RPC).
 */
case class SubscriptionFilter(
    toolsListChanged: Option[Boolean] = None,
    promptsListChanged: Option[Boolean] = None,
    resourcesListChanged: Option[Boolean] = None,
    resourceSubscriptions: Option[List[String]] = None
)

/** Parameters for a `subscriptions/listen` request.
 *
 *  @param meta Required request metadata.
 *  @param notifications Notification types the client opts into on this stream.
 */
case class SubscriptionsListenRequestParams(
    meta: RequestMeta,
    notifications: SubscriptionFilter
)

/** Opens a long-lived channel for notifications outside a specific request.
 *
 *  Replaces the former HTTP GET notification endpoint and keeps HTTP / STDIO
 *  behavior consistent.
 */
case class SubscriptionsListenRequest(
    id: RequestId,
    params: SubscriptionsListenRequestParams,
    jsonrpc: JsonRpcVersion = JsonRpcVersion20
) {
  def toMcpRequest: McpRequest =
    McpRequest(
      method = Subscriptions.listen,
      id = id,
      params = Some(RequestParams(meta = params.meta)),
      jsonrpc = jsonrpc
    )
}

/** Result `_meta` for a graceful `subscriptions/listen` teardown.
 *
 *  Extends [[ResultMeta]] with a '''required''' subscription-stream id.
 *
 *  @param subscriptionId JSON-RPC id of the listen request that opened the stream
 *                        (equals this response's `id`).
 *  @param serverInfo Optional self-reported server identity.
 *  @param extensions Additional `_meta` keys beyond the reserved fields.
 */
case class SubscriptionsListenResultMeta(
    subscriptionId: RequestId,
    serverInfo: Option[Implementation] = None,
    extensions: MetaObject = MetaObject.empty
)

/** Signals that a `subscriptions/listen` subscription ended gracefully.
 *
 *  Sent only when the server tears the stream down (e.g. shutdown). An abrupt
 *  transport close carries no response. The result body is otherwise empty.
 *
 *  @param meta Required meta including the subscription id being closed.
 *  @param resultType Normally [[CompleteResultType]].
 */
case class SubscriptionsListenResult(
    meta: SubscriptionsListenResultMeta,
    resultType: ResultType = CompleteResultType
)

/** Successful JSON-RPC response closing a `subscriptions/listen` stream. */
case class SubscriptionsListenResultResponse(
    result: SubscriptionsListenResult,
    id: RequestId,
    jsonrpc: JsonRpcVersion = JsonRpcVersion20
)

/** Parameters for `notifications/subscriptions/acknowledged`.
 *
 *  @param notifications Notification types the server agreed to honor.
 *  @param meta SHOULD include `subscriptionId` correlating to the listen request.
 */
case class SubscriptionsAcknowledgedNotificationParams(
    notifications: SubscriptionFilter,
    meta: Option[NotificationMeta] = None
)

/** First notification on a listen stream: subscription established.
 *
 *  MUST be the first message carrying this subscription's id in
 *  `io.modelcontextprotocol/subscriptionId`, and reports which notification
 *  types the server agreed to honor.
 */
case class SubscriptionsAcknowledgedNotification(
    params: SubscriptionsAcknowledgedNotificationParams,
    jsonrpc: JsonRpcVersion = JsonRpcVersion20
) {
  def toMcpNotification: McpNotification =
    McpNotification(
      method = Subscriptions.acknowledgedNotification,
      params = Some(NotificationParams(meta = params.meta)),
      jsonrpc = jsonrpc
    )
}
