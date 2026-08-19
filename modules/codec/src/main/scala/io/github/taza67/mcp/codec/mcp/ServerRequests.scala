package io.github.taza67.mcp.codec.mcp

import io.github.taza67.mcp.codec.jsonrpc.{Messages => JsonRpcMessages}
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.json.JsonValue
import io.github.taza67.mcp.protocol.jsonrpc.Error
import io.github.taza67.mcp.protocol.jsonrpc.ErrorResponse
import io.github.taza67.mcp.protocol.jsonrpc.InvalidParamsError
import io.github.taza67.mcp.protocol.jsonrpc.InvalidRequestError
import io.github.taza67.mcp.protocol.jsonrpc.Message
import io.github.taza67.mcp.protocol.jsonrpc.Notification
import io.github.taza67.mcp.protocol.jsonrpc.Request
import io.github.taza67.mcp.protocol.jsonrpc.RequestId
import io.github.taza67.mcp.protocol.jsonrpc.UnsupportedProtocolVersionError
import io.github.taza67.mcp.protocol.mcp.McpProtocolVersion
import io.github.taza67.mcp.protocol.mcp.McpProtocolVersion20260728
import io.github.taza67.mcp.protocol.mcp.McpRequest
import io.github.taza67.mcp.protocol.mcp.RequestMeta
import io.github.taza67.mcp.protocol.mcp.RequestParams



/** Server-facing boundary policy: validate an inbound [[JsonValue]] as a client request.
 *
 *  Returns `Right(Some(request))` for a valid [[McpRequest]], `Right(None)` for
 *  inbound notifications and responses (never answered), or `Left` with a ready
 *  to encode [[ErrorResponse]] for malformed or out-of-policy input. Rejections
 *  carry stable default messages; raw decoding diagnostics stay internal.
 */
object ServerRequests {

  def toRequest(value: JsonValue): Either[ErrorResponse, Option[McpRequest]] =
    value match {
      case obj: JsonObject => toRequestObject(obj)
      case _               => Left(uncorrelated(InvalidRequestError()))
    }

  private def toRequestObject(obj: JsonObject): Either[ErrorResponse, Option[McpRequest]] = {
    val fields = obj.value
    val responseShaped =
      !fields.contains(Message.MethodKey) &&
        (fields.contains(Message.ResultKey) || fields.contains(Message.ErrorKey))
    if (responseShaped) Right(None)
    else
      // Validate the envelope without params: notifications answer nothing, and
      // invalid or ambiguous headers cannot produce a correlated response.
      JsonRpcMessages.toMessage(JsonObject(fields - Message.ParamsKey)) match {
        case Right(_: Notification)  => Right(None)
        case Right(request: Request) => validatedRequest(obj, request)
        case _                       => Left(uncorrelated(InvalidRequestError()))
      }
  }

  private def validatedRequest(
      obj: JsonObject,
      header: Request
  ): Either[ErrorResponse, Option[McpRequest]] =
    JsonRpcMessages.toRequest(obj) match {
      case Left(_) =>
        Left(correlated(InvalidParamsError(), header.id))
      case Right(request) =>
        request.params match {
          case None => Left(correlated(InvalidParamsError(), request.id))
          case Some(params) =>
            unsupportedProtocolVersion(params) match {
              case Some(requested) =>
                Left(
                  correlated(
                    UnsupportedProtocolVersionError(
                      supported = List(McpProtocolVersion20260728.value),
                      requested = requested
                    ),
                    request.id
                  )
                )
              case None =>
                Messages.toRequest(request) match {
                  case Right(mcpRequest) => Right(Some(mcpRequest))
                  case Left(_)           => Left(correlated(InvalidParamsError(), request.id))
                }
            }
        }
    }

  /** Wire `params._meta` protocol version when it is a present-but-unknown string. */
  private def unsupportedProtocolVersion(params: JsonObject): Option[String] =
    params.value.get(RequestParams.MetaKey) match {
      case Some(meta: JsonObject) =>
        meta.value.get(RequestMeta.ProtocolVersionKey) match {
          case Some(JsonString(s)) if McpProtocolVersion.fromValue(s).isEmpty => Some(s)
          case _                                                            => None
        }
      case _ => None
    }

  private def correlated(error: Error, id: RequestId): ErrorResponse =
    ErrorResponse(error, id)

  private def uncorrelated(error: Error): ErrorResponse =
    ErrorResponse(error)
}
