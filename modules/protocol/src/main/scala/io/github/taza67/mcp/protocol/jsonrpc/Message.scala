package io.github.taza67.mcp.protocol.jsonrpc

import io.github.taza67.mcp.protocol.json.{JsonObject, JsonValue}



/** JSON-RPC protocol version string carried on every message. */
sealed trait JsonRpcVersion {
  def value: String
}

/** JSON-RPC 2.0 (`"2.0"`). */
case object JsonRpcVersion20 extends JsonRpcVersion {
  val value: String = "2.0"
}

/** Name of the method to be invoked. */
case class Method(value: String)

/** Unique identifier for a JSON-RPC request that expects a response.
 *
 *  JSON-RPC allows a string or a number. Notifications have no id.
 */
sealed trait RequestId

case class StringRequestId(value: String) extends RequestId

case class NumberRequestId(value: Long) extends RequestId

/** Any valid JSON-RPC object that can be decoded from the wire or encoded to send:
 *  a [[Request]], [[Notification]], or [[Response]].
 */
sealed trait Message {
  def jsonrpc: JsonRpcVersion
}

/** A request that expects a [[Response]].
 *
 *  @param method Method name to invoke.
 *  @param id Correlates this request with its response.
 *  @param params Optional object-shaped parameters (MCP uses objects only).
 */
case class Request(
    method: Method,
    id: RequestId,
    params: Option[JsonObject] = None,
    jsonrpc: JsonRpcVersion = JsonRpcVersion20
) extends Message

/** A notification that does not expect a response (no `id`).
 *
 *  @param method Method name to invoke.
 *  @param params Optional object-shaped parameters.
 */
case class Notification(
    method: Method,
    params: Option[JsonObject] = None,
    jsonrpc: JsonRpcVersion = JsonRpcVersion20
) extends Message

/** A response to a [[Request]], containing either a result or an error. */
sealed trait Response extends Message {
  def id: RequestId
}

/** Successful (non-error) response to a request.
 *
 *  @param result Result payload for the method.
 *  @param id Same id as the corresponding request.
 */
case class SuccessResponse(
    result: JsonValue,
    id: RequestId,
    jsonrpc: JsonRpcVersion = JsonRpcVersion20
) extends Response

/** Response indicating that an error occurred while handling the request.
 *
 *  @param error Error object describing the failure.
 *  @param id Same id as the corresponding request (when known).
 */
case class ErrorResponse(
    error: Error,
    id: RequestId,
    jsonrpc: JsonRpcVersion = JsonRpcVersion20
) extends Response
