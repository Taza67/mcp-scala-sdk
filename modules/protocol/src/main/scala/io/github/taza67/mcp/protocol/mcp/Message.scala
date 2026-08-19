package io.github.taza67.mcp.protocol.mcp

import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.jsonrpc.Error
import io.github.taza67.mcp.protocol.jsonrpc.JsonRpcVersion
import io.github.taza67.mcp.protocol.jsonrpc.JsonRpcVersion20
import io.github.taza67.mcp.protocol.jsonrpc.Method
import io.github.taza67.mcp.protocol.jsonrpc.RequestId



/** MCP request `params` carried by [[McpRequest]] (Scala 2.13 sealed sum).
 *
 *  [[RequestParams]] for ordinary methods; [[PaginatedRequestParams]] for list-style
 *  methods (`tools/list`, `prompts/list`, `resources/list`, `resources/templates/list`)
 *  with an optional wire `cursor`. Envelope decode selects the subtype from the
 *  JSON-RPC method (ADR-0007), not from whether `cursor` is present.
 */
sealed trait McpRequestParams {
  def meta: RequestMeta
  def fields: JsonObject
}

/** Common parameters for any non-paginated MCP request.
 *
 *  `_meta` is required. Method-specific fields live in [[fields]] until typed
 *  request-param ADTs are used for a given method.
 *
 *  [[fields]] MUST NOT contain [[PaginatedRequestParams.CursorKey]] — that key is
 *  reserved for [[PaginatedRequestParams]].
 *
 *  @param meta Required request metadata (protocol version, client capabilities, …).
 *  @param fields Open bag of method-specific parameter fields.
 */
case class RequestParams(
    meta: RequestMeta,
    fields: JsonObject = JsonObject(Map.empty)
) extends McpRequestParams

object RequestParams {

  /** Wire key for request metadata inside JSON-RPC `params`. */
  val MetaKey: String = "_meta"
}

/** Common parameters for paginated list-style requests.
 *
 *  Used when the JSON-RPC method is a list method (see [[McpRequestParams]]).
 *  Absent `cursor` means the first page — still this type, not [[RequestParams]].
 *
 *  @param meta Required request metadata.
 *  @param cursor Opaque pagination position; when set, the server returns results after it.
 *  @param fields Open bag of additional method-specific fields (not including `cursor`).
 */
case class PaginatedRequestParams(
    meta: RequestMeta,
    cursor: Option[Cursor] = None,
    fields: JsonObject = JsonObject(Map.empty)
) extends McpRequestParams

object PaginatedRequestParams {

  /** Wire key for the opaque pagination cursor inside JSON-RPC `params`. */
  val CursorKey: String = "cursor"
}

/** Common parameters for any MCP notification.
 *
 *  @param meta Optional notification metadata (e.g. subscription id).
 *  @param fields Open bag of method-specific parameter fields.
 */
case class NotificationParams(
    meta: Option[NotificationMeta] = None,
    fields: JsonObject = JsonObject(Map.empty)
)

object NotificationParams {

  /** Wire key for notification metadata inside JSON-RPC `params`. */
  val MetaKey: String = RequestParams.MetaKey
}

/** Common MCP result fields shared by successful method responses.
 *
 *  Servers for this protocol revision MUST include [[resultType]]. Clients talking
 *  to older servers that omit it MUST treat the absent field as `"complete"`.
 *
 *  @param resultType Discriminant telling the client how to parse the result.
 *  @param fields Open bag of method-specific result fields.
 *  @param meta Optional result metadata (e.g. server info).
 */
case class Result(
    resultType: ResultType,
    fields: JsonObject = JsonObject(Map.empty),
    meta: Option[ResultMeta] = None
)

object Result {

  /** Wire key for the result discriminant inside JSON-RPC `result`. */
  val ResultTypeKey: String = "resultType"

  /** Wire key for result metadata inside JSON-RPC `result`. */
  val MetaKey: String = RequestParams.MetaKey

  /** Successful result that carries no method-specific data. */
  def empty(meta: Option[ResultMeta] = None): Result =
    Result(resultType = CompleteResultType, fields = JsonObject(Map.empty), meta = meta)
}

/** Shared wire keys for cacheable MCP results (lists, reads, discover). */
object CacheableResult {

  /** Wire key for cache freshness hint in milliseconds. */
  val TtlMsKey: String = "ttlMs"

  /** Wire key for cache scope (`public` / `private`). */
  val CacheScopeKey: String = "cacheScope"
}

/** Shared wire keys for paginated MCP list results. */
object PaginatedResult {

  /** Wire key for the next-page cursor token. */
  val NextCursorKey: String = "nextCursor"
}

/** MCP message with MCP-typed params/result.
 *
 *  Wire projection (`JsonObject` / JSON text) lives in `modules/codec`
 *  (ADR-0004, ADR-0006), not on these ADTs.
 */
sealed trait McpMessage {
  def jsonrpc: JsonRpcVersion
}

/** MCP request that expects an [[McpResponse]].
 *
 *  @param method Method name to invoke.
 *  @param id Correlates this request with its response.
 *  @param params MCP request parameters (`_meta` required when present; may be
 *                plain or paginated).
 */
case class McpRequest(
    method: Method,
    id: RequestId,
    params: Option[McpRequestParams] = None,
    jsonrpc: JsonRpcVersion = JsonRpcVersion20
) extends McpMessage

/** MCP notification that does not expect a response. */
case class McpNotification(
    method: Method,
    params: Option[NotificationParams] = None,
    jsonrpc: JsonRpcVersion = JsonRpcVersion20
) extends McpMessage

/** MCP response to an [[McpRequest]] (success or error). */
sealed trait McpResponse extends McpMessage

/** Successful MCP response carrying a [[Result]]. */
case class McpSuccessResponse(
    result: Result,
    id: RequestId,
    jsonrpc: JsonRpcVersion = JsonRpcVersion20
) extends McpResponse

/** MCP error response carrying a JSON-RPC [[Error]].
 *
 *  @param error Error object describing the failure.
 *  @param id Same id as the corresponding request when the error is correlated;
 *            `None` for uncorrelated errors (e.g. a parse failure before an id
 *            could be read), which encode with the `id` member omitted.
 */
case class McpErrorResponse(
    error: Error,
    id: Option[RequestId] = None,
    jsonrpc: JsonRpcVersion = JsonRpcVersion20
) extends McpResponse

object McpErrorResponse {

  /** Correlated error response carrying the corresponding request's id. */
  def apply(error: Error, id: RequestId): McpErrorResponse =
    McpErrorResponse(error, Some(id))

  /** Correlated error response with an explicit protocol version. */
  def apply(error: Error, id: RequestId, jsonrpc: JsonRpcVersion): McpErrorResponse =
    McpErrorResponse(error, Some(id), jsonrpc)
}
