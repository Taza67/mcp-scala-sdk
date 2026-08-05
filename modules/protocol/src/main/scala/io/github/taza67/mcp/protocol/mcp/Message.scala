package io.github.taza67.mcp.protocol.mcp

import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.jsonrpc.{
  Error,
  ErrorResponse,
  JsonRpcVersion,
  JsonRpcVersion20,
  Message,
  Method,
  Notification,
  Request,
  RequestId,
  Response,
  SuccessResponse
}



/** Common parameters for any MCP request.
 *
 *  `_meta` is required. Method-specific fields live in [[fields]] until typed
 *  request-param ADTs are used for a given method.
 *
 *  @param meta Required request metadata (protocol version, client capabilities, …).
 *  @param fields Open bag of method-specific parameter fields.
 */
case class RequestParams(
    meta: RequestMeta,
    fields: JsonObject = JsonObject(Map.empty)
)

/** Common parameters for paginated list-style requests.
 *
 *  @param meta Required request metadata.
 *  @param cursor Opaque pagination position; when set, the server returns results after it.
 *  @param fields Open bag of additional method-specific fields.
 */
case class PaginatedRequestParams(
    meta: RequestMeta,
    cursor: Option[Cursor] = None,
    fields: JsonObject = JsonObject(Map.empty)
)

/** Common parameters for any MCP notification.
 *
 *  @param meta Optional notification metadata (e.g. subscription id).
 *  @param fields Open bag of method-specific parameter fields.
 */
case class NotificationParams(
    meta: Option[NotificationMeta] = None,
    fields: JsonObject = JsonObject(Map.empty)
)

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

  /** Successful result that carries no method-specific data. */
  def empty(meta: Option[ResultMeta] = None): Result =
    Result(resultType = CompleteResultType, fields = JsonObject(Map.empty), meta = meta)
}

/** MCP message with MCP-typed params/result, convertible to a JSON-RPC [[Message]]. */
sealed trait McpMessage {
  def jsonrpc: JsonRpcVersion
  def toJsonRpc: Message
}

/** MCP request that expects an [[McpResponse]].
 *
 *  @param method Method name to invoke.
 *  @param id Correlates this request with its response.
 *  @param params MCP request parameters (`_meta` required when present).
 */
case class McpRequest(
    method: Method,
    id: RequestId,
    params: Option[RequestParams] = None,
    jsonrpc: JsonRpcVersion = JsonRpcVersion20
) extends McpMessage {
  def toJsonRpc: Request =
    Request(method = method, id = id, params = params.map(_.fields), jsonrpc = jsonrpc)
}

/** MCP notification that does not expect a response. */
case class McpNotification(
    method: Method,
    params: Option[NotificationParams] = None,
    jsonrpc: JsonRpcVersion = JsonRpcVersion20
) extends McpMessage {
  def toJsonRpc: Notification =
    Notification(method = method, params = params.map(_.fields), jsonrpc = jsonrpc)
}

/** MCP response to an [[McpRequest]] (success or error). */
sealed trait McpResponse extends McpMessage {
  def id: RequestId
  def toJsonRpc: Response
}

/** Successful MCP response carrying a [[Result]]. */
case class McpSuccessResponse(
    result: Result,
    id: RequestId,
    jsonrpc: JsonRpcVersion = JsonRpcVersion20
) extends McpResponse {
  def toJsonRpc: SuccessResponse =
    SuccessResponse(result = result.fields, id = id, jsonrpc = jsonrpc)
}

/** MCP error response carrying a JSON-RPC [[Error]]. */
case class McpErrorResponse(
    error: Error,
    id: RequestId,
    jsonrpc: JsonRpcVersion = JsonRpcVersion20
) extends McpResponse {
  def toJsonRpc: ErrorResponse =
    ErrorResponse(error = error, id = id, jsonrpc = jsonrpc)
}
