package io.github.taza67.mcp.protocol.mcp.resources

import io.github.taza67.mcp.protocol.jsonrpc.{JsonRpcVersion, JsonRpcVersion20, Method, RequestId}
import io.github.taza67.mcp.protocol.mcp._



/** Method names for the resources domain. */
object Resources {
  val list: Method = Method("resources/list")
  val read: Method = Method("resources/read")
  val templatesList: Method = Method("resources/templates/list")
  val listChangedNotification: Method = Method("notifications/resources/list_changed")
  val updatedNotification: Method = Method("notifications/resources/updated")
}

/** A known resource that the server is capable of reading.
 *
 *  @param name Programmatic name; also display fallback when `title` is absent.
 *  @param uri URI of this resource.
 *  @param title Human-readable title for UI contexts.
 *  @param description Hint for clients/LLMs about what the resource represents.
 *  @param mimeType MIME type if known.
 *  @param size Raw content size in bytes (before base64), if known.
 *  @param icons Optional UI icons.
 *  @param annotations Optional display / usage annotations for the client.
 *  @param meta Optional open metadata.
 */
case class Resource(
    name: String,
    uri: String,
    title: Option[String] = None,
    description: Option[String] = None,
    mimeType: Option[String] = None,
    size: Option[Long] = None,
    icons: Option[List[Icon]] = None,
    annotations: Option[Annotations] = None,
    meta: Option[MetaObject] = None
)

/** Template description for parameterized resources available on the server.
 *
 *  @param name Programmatic name; also display fallback when `title` is absent.
 *  @param uriTemplate URI template (RFC 6570) for resources of this kind.
 *  @param title Human-readable title for UI contexts.
 *  @param description Hint for clients/LLMs about resources matching this template.
 *  @param mimeType MIME type if known for instantiated resources.
 *  @param icons Optional UI icons.
 *  @param annotations Optional display / usage annotations for the client.
 *  @param meta Optional open metadata.
 */
case class ResourceTemplate(
    name: String,
    uriTemplate: String,
    title: Option[String] = None,
    description: Option[String] = None,
    mimeType: Option[String] = None,
    icons: Option[List[Icon]] = None,
    annotations: Option[Annotations] = None,
    meta: Option[MetaObject] = None
)

/** Sent from the client to list resources the server offers (paginated). */
case class ListResourcesRequest(
    id: RequestId,
    params: PaginatedRequestParams,
    jsonrpc: JsonRpcVersion = JsonRpcVersion20
)

/** Result of a `resources/list` request.
 *
 *  @param resources Resources currently offered by the server.
 *  @param ttlMs Cache freshness hint in milliseconds (like Cache-Control `max-age`).
 *  @param cacheScope Whether the cached response may be shared across auth contexts.
 *  @param nextCursor Pagination token for the next page, if more results exist.
 *  @param resultType Normally [[CompleteResultType]].
 *  @param meta Optional result metadata.
 */
case class ListResourcesResult(
    resources: List[Resource],
    ttlMs: Long,
    cacheScope: CacheScope,
    nextCursor: Option[Cursor] = None,
    resultType: ResultType = CompleteResultType,
    meta: Option[ResultMeta] = None
)

/** Successful JSON-RPC response to a `resources/list` request. */
case class ListResourcesResultResponse(
    result: ListResourcesResult,
    id: RequestId,
    jsonrpc: JsonRpcVersion = JsonRpcVersion20
)

/** Parameters for a `resources/read` request.
 *
 *  @param meta Required request metadata.
 *  @param uri URI of the resource to read.
 *  @param inputResponses Client answers to a prior [[InputRequiredResult]].
 *  @param requestState Opaque state echoed from a prior [[InputRequiredResult]].
 */
case class ReadResourceRequestParams(
    meta: RequestMeta,
    uri: String,
    inputResponses: Option[InputResponses] = None,
    requestState: Option[String] = None
)

object ReadResourceRequestParams {

  /** Wire key for the resource URI inside JSON-RPC `params`. */
  val UriKey: String = "uri"
}

/** Sent from the client to read a specific resource URI. */
case class ReadResourceRequest(
    id: RequestId,
    params: ReadResourceRequestParams,
    jsonrpc: JsonRpcVersion = JsonRpcVersion20
)

/** Successful contents of a `resources/read` request.
 *
 *  @param contents One or more text/binary content payloads for the URI.
 *  @param ttlMs Cache freshness hint in milliseconds (like Cache-Control `max-age`).
 *  @param cacheScope Whether the cached response may be shared across auth contexts.
 *  @param resultType Normally [[CompleteResultType]].
 *  @param meta Optional result metadata.
 */
case class ReadResourceResult(
    contents: List[ResourceContents],
    ttlMs: Long,
    cacheScope: CacheScope,
    resultType: ResultType = CompleteResultType,
    meta: Option[ResultMeta] = None
)

/** Successful JSON-RPC response to a `resources/read` request. */
case class ReadResourceResultResponse(
    result: RequestOutcome[ReadResourceResult],
    id: RequestId,
    jsonrpc: JsonRpcVersion = JsonRpcVersion20
)

/** Sent from the client to list resource templates the server offers (paginated). */
case class ListResourceTemplatesRequest(
    id: RequestId,
    params: PaginatedRequestParams,
    jsonrpc: JsonRpcVersion = JsonRpcVersion20
)

/** Result of a `resources/templates/list` request.
 *
 *  @param resourceTemplates Templates currently offered by the server.
 *  @param ttlMs Cache freshness hint in milliseconds (like Cache-Control `max-age`).
 *  @param cacheScope Whether the cached response may be shared across auth contexts.
 *  @param nextCursor Pagination token for the next page, if more results exist.
 *  @param resultType Normally [[CompleteResultType]].
 *  @param meta Optional result metadata.
 */
case class ListResourceTemplatesResult(
    resourceTemplates: List[ResourceTemplate],
    ttlMs: Long,
    cacheScope: CacheScope,
    nextCursor: Option[Cursor] = None,
    resultType: ResultType = CompleteResultType,
    meta: Option[ResultMeta] = None
)

/** Successful JSON-RPC response to a `resources/templates/list` request. */
case class ListResourceTemplatesResultResponse(
    result: ListResourceTemplatesResult,
    id: RequestId,
    jsonrpc: JsonRpcVersion = JsonRpcVersion20
)

/** Optional notification that the server's resource list changed.
 *
 *  Delivered only on a `subscriptions/listen` stream when the client opted in via
 *  `resourcesListChanged`.
 */
case class ResourceListChangedNotification(
    params: Option[NotificationParams] = None,
    jsonrpc: JsonRpcVersion = JsonRpcVersion20
)

/** Parameters for a `notifications/resources/updated` notification.
 *
 *  @param uri URI of the resource that changed.
 *  @param meta Optional notification metadata (e.g. subscription id).
 */
case class ResourceUpdatedNotificationParams(
    uri: String,
    meta: Option[NotificationMeta] = None
)

/** Notification that a subscribed resource changed and may need to be read again.
 *
 *  Sent only for URIs the client opted into via `resourceSubscriptions` on
 *  `subscriptions/listen`.
 */
case class ResourceUpdatedNotification(
    params: ResourceUpdatedNotificationParams,
    jsonrpc: JsonRpcVersion = JsonRpcVersion20
)
