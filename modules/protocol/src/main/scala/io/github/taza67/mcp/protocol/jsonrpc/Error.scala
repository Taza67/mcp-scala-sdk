package io.github.taza67.mcp.protocol.jsonrpc

import io.github.taza67.mcp.protocol.json.{JsonArray, JsonObject, JsonString, JsonValue}



/** JSON-RPC / MCP error object (SPECS Error). */
sealed trait Error {
  def code: Int
  def message: String
  def data: Option[JsonValue]
}

object ErrorCode {
  val ParseError: Int = -32700
  val InvalidRequest: Int = -32600
  val MethodNotFound: Int = -32601
  val InvalidParams: Int = -32602
  val InternalError: Int = -32603

  /** SPECS HEADER_MISMATCH */
  val HeaderMismatch: Int = -32020

  /** SPECS MISSING_REQUIRED_CLIENT_CAPABILITY */
  val MissingRequiredClientCapability: Int = -32021

  /** SPECS UNSUPPORTED_PROTOCOL_VERSION */
  val UnsupportedProtocolVersion: Int = -32022
}

/** Invalid JSON was received (SPECS ParseError). */
case class ParseError(
    message: String = "Parse error: Invalid JSON",
    data: Option[JsonValue] = None
) extends Error {
  val code: Int = ErrorCode.ParseError
}

/** Request is not a valid JSON-RPC request object (SPECS InvalidRequestError). */
case class InvalidRequestError(
    message: String = "Invalid Request",
    data: Option[JsonValue] = None
) extends Error {
  val code: Int = ErrorCode.InvalidRequest
}

/** Method does not exist or is not available (SPECS MethodNotFoundError). */
case class MethodNotFoundError(
    message: String = "Method not found",
    data: Option[JsonValue] = None
) extends Error {
  val code: Int = ErrorCode.MethodNotFound
}

/** Method parameters are invalid or malformed (SPECS InvalidParamsError). */
case class InvalidParamsError(
    message: String = "Invalid params",
    data: Option[JsonValue] = None
) extends Error {
  val code: Int = ErrorCode.InvalidParams
}

/** Unexpected condition on the receiver (SPECS InternalError). */
case class InternalError(
    message: String = "Internal error",
    data: Option[JsonValue] = None
) extends Error {
  val code: Int = ErrorCode.InternalError
}

/** HTTP headers do not match the request body (SPECS error code HEADER_MISMATCH). */
case class HeaderMismatchError(
    message: String,
    data: Option[JsonValue] = None
) extends Error {
  val code: Int = ErrorCode.HeaderMismatch
}

/** Server requires a client capability not declared in the request (SPECS -32021). */
case class MissingRequiredClientCapabilityError(
    message: String,
    requiredCapabilities: JsonObject
) extends Error {
  val code: Int = ErrorCode.MissingRequiredClientCapability
  val data: Option[JsonValue] =
    Some(JsonObject(Map("requiredCapabilities" -> requiredCapabilities)))
}

/** Request protocol version is unsupported (SPECS -32022). */
case class UnsupportedProtocolVersionError(
    message: String = "Unsupported protocol version",
    supported: List[String],
    requested: String
) extends Error {
  val code: Int = ErrorCode.UnsupportedProtocolVersion
  val data: Option[JsonValue] = Some(
    JsonObject(
      Map(
        "supported" -> JsonArray(supported.map(JsonString(_))),
        "requested" -> JsonString(requested)
      )
    )
  )
}

/** Application-defined or unrecognized error code. */
case class ApplicationError(
    code: Int,
    message: String,
    data: Option[JsonValue] = None
) extends Error
