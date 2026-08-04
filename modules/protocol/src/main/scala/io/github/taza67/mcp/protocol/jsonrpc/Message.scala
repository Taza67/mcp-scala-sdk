package io.github.taza67.mcp.protocol.jsonrpc

import io.github.taza67.mcp.protocol.json.{JsonObject, JsonValue}



sealed trait JsonRpcVersion {
  def value: String
}

case object JsonRpcVersion20 extends JsonRpcVersion {
  val value: String = "2.0"
}

case class Method(value: String)

/** A uniquely identifying ID for a request in JSON-RPC (SPECS RequestId). */
sealed trait RequestId

case class StringRequestId(value: String) extends RequestId

case class NumberRequestId(value: Long) extends RequestId

/** Any valid JSON-RPC message (SPECS JSONRPCMessage). */
sealed trait Message {
  def jsonrpc: JsonRpcVersion
}

/** A request that expects a response (SPECS JSONRPCRequest). */
case class Request(
    method: Method,
    id: RequestId,
    params: Option[JsonObject] = None,
    jsonrpc: JsonRpcVersion = JsonRpcVersion20
) extends Message

/** A notification that does not expect a response (SPECS JSONRPCNotification). */
case class Notification(
    method: Method,
    params: Option[JsonObject] = None,
    jsonrpc: JsonRpcVersion = JsonRpcVersion20
) extends Message

/** A response to a request (SPECS JSONRPCResponse). */
sealed trait Response extends Message {
  def id: RequestId
}

/** A successful (non-error) response (SPECS JSONRPCResultResponse). */
case class SuccessResponse(
    result: JsonValue,
    id: RequestId,
    jsonrpc: JsonRpcVersion = JsonRpcVersion20
) extends Response

/** A response indicating an error (SPECS JSONRPCErrorResponse). */
case class ErrorResponse(
    error: Error,
    id: RequestId,
    jsonrpc: JsonRpcVersion = JsonRpcVersion20
) extends Response
