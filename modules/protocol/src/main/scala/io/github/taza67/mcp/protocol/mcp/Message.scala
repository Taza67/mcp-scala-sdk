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



/** Common params for any request (SPECS RequestParams).
 *
 *  `_meta` is required. Method-specific fields live in `fields` (open bag).
 */
case class RequestParams(
    meta: RequestMeta,
    fields: JsonObject = JsonObject(Map.empty)
)

/** Common params for paginated requests (SPECS PaginatedRequestParams). */
case class PaginatedRequestParams(
    meta: RequestMeta,
    cursor: Option[Cursor] = None,
    fields: JsonObject = JsonObject(Map.empty)
)

/** Common params for any notification (SPECS NotificationParams). */
case class NotificationParams(
    meta: Option[NotificationMeta] = None,
    fields: JsonObject = JsonObject(Map.empty)
)

/** Common result fields (SPECS Result).
 *
 *  `resultType` is required. Method-specific fields live in `fields` (open bag).
 */
case class Result(
    resultType: ResultType,
    fields: JsonObject = JsonObject(Map.empty),
    meta: Option[ResultMeta] = None
)

object Result {

  /** A result that indicates success but carries no data (SPECS EmptyResult). */
  def empty(meta: Option[ResultMeta] = None): Result =
    Result(resultType = CompleteResultType, fields = JsonObject(Map.empty), meta = meta)
}

/** MCP-level message (mirrors JSON-RPC Message with MCP params/result). */
sealed trait McpMessage {
  def jsonrpc: JsonRpcVersion
  def toJsonRpc: Message
}

case class McpRequest(
    method: Method,
    id: RequestId,
    params: Option[RequestParams] = None,
    jsonrpc: JsonRpcVersion = JsonRpcVersion20
) extends McpMessage {
  def toJsonRpc: Request =
    Request(method = method, id = id, params = params.map(_.fields), jsonrpc = jsonrpc)
}

case class McpNotification(
    method: Method,
    params: Option[NotificationParams] = None,
    jsonrpc: JsonRpcVersion = JsonRpcVersion20
) extends McpMessage {
  def toJsonRpc: Notification =
    Notification(method = method, params = params.map(_.fields), jsonrpc = jsonrpc)
}

sealed trait McpResponse extends McpMessage {
  def id: RequestId
  def toJsonRpc: Response
}

case class McpSuccessResponse(
    result: Result,
    id: RequestId,
    jsonrpc: JsonRpcVersion = JsonRpcVersion20
) extends McpResponse {
  def toJsonRpc: SuccessResponse =
    SuccessResponse(result = result.fields, id = id, jsonrpc = jsonrpc)
}

case class McpErrorResponse(
    error: Error,
    id: RequestId,
    jsonrpc: JsonRpcVersion = JsonRpcVersion20
) extends McpResponse {
  def toJsonRpc: ErrorResponse =
    ErrorResponse(error = error, id = id, jsonrpc = jsonrpc)
}
