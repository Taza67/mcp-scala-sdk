package io.github.taza67.mcp.protocol.jsonrpc

import io.github.taza67.mcp.protocol.json.{JsonArray, JsonObject, JsonString, JsonValue}



/** JSON-RPC 2.0 error object carried by an [[ErrorResponse]].
 *
 *  @see [[https://www.jsonrpc.org/specification#error_object JSON-RPC 2.0 Error Object]]
 */
sealed trait Error {

  /** The error type that occurred. */
  def code: Int

  /** Short description of the error. SHOULD be a concise single sentence. */
  def message: String

  /** Additional information defined by the sender (details, nested errors, …). */
  def data: Option[JsonValue]
}

object Error {

  /** Classify a JSON-RPC error payload into the typed hierarchy.
   *
   *  Known codes become dedicated case classes. Structured MCP errors that lack
   *  the expected `data` shape fall back to [[ApplicationError]] so wire data is
   *  preserved. Unknown codes also become [[ApplicationError]].
   */
  def classify(code: Int, message: String, data: Option[JsonValue]): Error =
    code match {
      case ErrorCode.ParseError =>
        ParseError(message, data)
      case ErrorCode.InvalidRequest =>
        InvalidRequestError(message, data)
      case ErrorCode.MethodNotFound =>
        MethodNotFoundError(message, data)
      case ErrorCode.InvalidParams =>
        InvalidParamsError(message, data)
      case ErrorCode.InternalError =>
        InternalError(message, data)
      case ErrorCode.HeaderMismatch =>
        HeaderMismatchError(message, data)
      case ErrorCode.MissingRequiredClientCapability =>
        requiredCapabilitiesFromData(data)
          .map(MissingRequiredClientCapabilityError(message, _))
          .getOrElse(ApplicationError(code, message, data))
      case ErrorCode.UnsupportedProtocolVersion =>
        unsupportedProtocolVersionFromData(data)
          .map { case (supported, requested) =>
            UnsupportedProtocolVersionError(message, supported, requested)
          }
          .getOrElse(ApplicationError(code, message, data))
      case _ =>
        ApplicationError(code, message, data)
    }

  private def requiredCapabilitiesFromData(data: Option[JsonValue]): Option[JsonObject] =
    data.collect { case JsonObject(fields) =>
      fields.get("requiredCapabilities").collect { case caps: JsonObject => caps }
    }.flatten

  private def unsupportedProtocolVersionFromData(
      data: Option[JsonValue]
  ): Option[(List[String], String)] =
    data.collect { case JsonObject(fields) =>
      for {
        supportedValue <- fields.get("supported")
        requestedValue <- fields.get("requested")
        supported <- supportedValue match {
          case JsonArray(values) =>
            val strings = values.collect { case JsonString(s) => s }
            if (strings.size == values.size) Some(strings) else None
          case _ => None
        }
        requested <- requestedValue match {
          case JsonString(s) => Some(s)
          case _             => None
        }
      } yield (supported, requested)
    }.flatten
}

/** Standard and MCP-defined JSON-RPC error codes. */
object ErrorCode {
  val ParseError: Int = -32700
  val InvalidRequest: Int = -32600
  val MethodNotFound: Int = -32601
  val InvalidParams: Int = -32602
  val InternalError: Int = -32603

  /** HTTP headers do not match the request body, or required headers are missing/malformed. */
  val HeaderMismatch: Int = -32020

  /** Server requires a client capability that was not declared in `clientCapabilities`. */
  val MissingRequiredClientCapability: Int = -32021

  /** Request protocol version is unknown or unsupported by the server. */
  val UnsupportedProtocolVersion: Int = -32022
}

/** Invalid JSON was received by the server and could not be parsed. */
case class ParseError(
    message: String = "Parse error: Invalid JSON",
    data: Option[JsonValue] = None
) extends Error {
  val code: Int = ErrorCode.ParseError
}

/** The message is not a valid JSON-RPC request object
 *  (missing `jsonrpc` / `method`, wrong types, …).
 */
case class InvalidRequestError(
    message: String = "Invalid Request",
    data: Option[JsonValue] = None
) extends Error {
  val code: Int = ErrorCode.InvalidRequest
}

/** The requested method does not exist or is not available.
 *
 *  In MCP this covers unknown methods and methods gated behind a server
 *  capability the server did not advertise. Missing ''client'' capabilities
 *  use [[MissingRequiredClientCapabilityError]] (`-32021`) instead.
 */
case class MethodNotFoundError(
    message: String = "Method not found",
    data: Option[JsonValue] = None
) extends Error {
  val code: Int = ErrorCode.MethodNotFound
}

/** Method parameters are invalid or malformed
 *  (unknown tool/prompt name, bad cursor, invalid arguments, …).
 */
case class InvalidParamsError(
    message: String = "Invalid params",
    data: Option[JsonValue] = None
) extends Error {
  val code: Int = ErrorCode.InvalidParams
}

/** Unexpected condition on the receiver that prevents fulfilling the request. */
case class InternalError(
    message: String = "Internal error",
    data: Option[JsonValue] = None
) extends Error {
  val code: Int = ErrorCode.InternalError
}

/** HTTP headers do not match corresponding body values, or required headers
 *  are missing or malformed. For HTTP, the response status MUST be `400 Bad Request`.
 */
case class HeaderMismatchError(
    message: String,
    data: Option[JsonValue] = None
) extends Error {
  val code: Int = ErrorCode.HeaderMismatch
}

/** Processing the request requires a client capability not declared in
 *  `clientCapabilities`. For HTTP, the response status MUST be `400 Bad Request`.
 *
 *  @param requiredCapabilities Capabilities the server needed for this request.
 */
case class MissingRequiredClientCapabilityError(
    message: String,
    requiredCapabilities: JsonObject
) extends Error {
  val code: Int = ErrorCode.MissingRequiredClientCapability
  val data: Option[JsonValue] =
    Some(JsonObject(Map("requiredCapabilities" -> requiredCapabilities)))
}

/** The request's protocol version is unknown or unsupported by the server.
 *  For HTTP, the response status MUST be `400 Bad Request`.
 *
 *  @param supported Protocol versions this server accepts.
 *  @param requested Protocol version sent by the client.
 */
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

/** Application-defined or unrecognized error code outside the standard set. */
case class ApplicationError(
    code: Int,
    message: String,
    data: Option[JsonValue] = None
) extends Error
