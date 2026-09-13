package io.github.taza67.mcp.transport.http

import java.net.URI

import scala.util.control.NonFatal

import io.github.taza67.mcp.codec.jsonrpc.{Messages => JsonRpcMessages}
import io.github.taza67.mcp.codec.mcp.{Messages => McpMessages}
import io.github.taza67.mcp.codec.mcp.ServerRequests
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonValue
import io.github.taza67.mcp.protocol.jsonrpc.Error
import io.github.taza67.mcp.protocol.jsonrpc.ErrorCode
import io.github.taza67.mcp.protocol.jsonrpc.ErrorResponse
import io.github.taza67.mcp.protocol.jsonrpc.InternalError
import io.github.taza67.mcp.protocol.jsonrpc.InvalidRequestError
import io.github.taza67.mcp.protocol.jsonrpc.Message
import io.github.taza67.mcp.protocol.jsonrpc.Notification
import io.github.taza67.mcp.protocol.jsonrpc.Request
import io.github.taza67.mcp.protocol.jsonrpc.RequestId
import io.github.taza67.mcp.protocol.mcp.McpErrorResponse
import io.github.taza67.mcp.protocol.mcp.McpNotification
import io.github.taza67.mcp.protocol.mcp.McpRequest
import io.github.taza67.mcp.protocol.mcp.McpSuccessResponse
import io.github.taza67.mcp.protocol.mcp.notifications.{Notifications => NotificationMethods}
import io.github.taza67.mcp.server.Server



/** Transport-neutral MCP streamable-HTTP POST boundary (no sockets/SSE).
 *
 *  [[preflight]] owns HTTP media policy (Origin allowlist, POST only,
 *  Content-Type, Accept). [[handle]] runs preflight, then classifies the raw
 *  JSON-RPC envelope: requests are header-validated, boundary-decoded through
 *  [[ServerRequests]], and dispatched to the [[Server]]; notifications are
 *  fully decoded and handed to an explicit callback. Inbound responses are
 *  rejected: HTTP carries only inbound client requests, unlike stdio.
 *  No session or `Last-Event-ID` state is consulted.
 */
final case class HttpEndpoint(
    server: Server,
    allowedOrigins: Set[String] = Set.empty,
    onNotification: Option[McpNotification => Either[Error, Unit]] = None
) {

  allowedOrigins.foreach { origin =>
    if (!HttpEndpoint.isValidOrigin(origin))
      throw new IllegalArgumentException("Invalid allowed origin")
  }

  private val jsonHeaders =
    Map("Content-Type" -> "application/json", "Cache-Control" -> "no-store")

  /** HTTP-level gate applied before the body is touched. */
  def preflight(
      method: String,
      headers: Map[String, List[String]]
  ): Either[HttpResponse, Unit] =
    for {
      _ <- checkOrigin(headers)
      _ <- Either.cond(
        method == "POST",
        (),
        HttpResponse(405, headers = Map("Allow" -> "POST"))
      )
      _ <- Either.cond(HttpMedia.contentTypeOk(headers), (), HttpResponse(415))
      _ <- Either.cond(HttpMedia.acceptOk(headers), (), HttpResponse(406))
    } yield ()

  def handle(
      method: String,
      headers: Map[String, List[String]],
      body: JsonValue
  ): HttpResponse =
    preflight(method, headers) match {
      case Left(response) => response
      case Right(()) =>
        body match {
          case obj: JsonObject => dispatch(obj, headers)
          case _               => jsonError(400, InvalidRequestError())
        }
    }

  private def checkOrigin(
      headers: Map[String, List[String]]
  ): Either[HttpResponse, Unit] =
    if (!headers.keys.exists(_.equalsIgnoreCase("Origin"))) Right(())
    else
      HttpMedia.headerValues("Origin", headers) match {
        case origin :: Nil =>
          Either.cond(allowedOrigins.contains(origin), (), HttpResponse(403))
        case _ => Left(HttpResponse(403))
      }

  /** Trusted envelope classification without `params`. */
  private def dispatch(obj: JsonObject, headers: Map[String, List[String]]): HttpResponse =
    JsonRpcMessages.toMessage(JsonObject(obj.value - Message.ParamsKey)) match {
      case Right(header: Request) => handleRequest(header.id, obj, headers)
      case Right(_: Notification) => handleNotification(obj)
      case _                      => jsonError(400, InvalidRequestError())
    }

  private def handleRequest(
      id: RequestId,
      obj: JsonObject,
      headers: Map[String, List[String]]
  ): HttpResponse =
    RequestHeaders.validate(obj, headers) match {
      case Left(error) =>
        jsonError(HttpEndpoint.statusFor(error), error, Some(id))
      case Right(()) =>
        ServerRequests.toRequest(obj) match {
          case Left(errorResponse) =>
            jsonError(
              HttpEndpoint.statusFor(errorResponse.error),
              errorResponse.error,
              errorResponse.id
            )
          case Right(Some(request)) => invoke(request)
          case Right(None)          => jsonError(400, InvalidRequestError())
        }
    }

  private def invoke(request: McpRequest): HttpResponse =
    try {
      server.handle(request) match {
        case success: McpSuccessResponse =>
          jsonBody(200, McpMessages.fromMessage(success))
        case failure: McpErrorResponse =>
          jsonBody(
            HttpEndpoint.statusFor(failure.error),
            McpMessages.fromMessage(failure)
          )
      }
    } catch {
      case NonFatal(_) => jsonError(500, InternalError(), Some(request.id))
    }

  private def handleNotification(obj: JsonObject): HttpResponse =
    McpMessages.toMessage(obj) match {
      case Right(notification: McpNotification)
          if notification.method == NotificationMethods.cancelled =>
        jsonError(400, InvalidRequestError())
      case Right(notification: McpNotification) =>
        onNotification match {
          case Some(callback) =>
            try {
              callback(notification) match {
                case Right(()) => HttpResponse(202)
                case Left(error) => jsonError(400, error)
              }
            } catch {
              case NonFatal(_) => jsonError(500, InternalError())
            }
          case None => jsonError(400, InvalidRequestError())
        }
      case _ => jsonError(400, InvalidRequestError())
    }

  private def jsonBody(status: Int, body: JsonObject): HttpResponse =
    HttpResponse(status, Some(body), jsonHeaders)

  private def jsonError(
      status: Int,
      error: Error,
      id: Option[RequestId] = None
  ): HttpResponse =
    jsonBody(status, JsonRpcMessages.fromMessage(ErrorResponse(error, id)))
}

object HttpEndpoint {

  /** Serialized `http(s)` origin: ASCII, host present, no userinfo, path,
   *  query, fragment, wildcard, or out-of-range port.
   */
  private def isValidOrigin(origin: String): Boolean = {
    val uri =
      try new URI(origin)
      catch { case NonFatal(_) => null }
    origin.nonEmpty &&
      origin.forall(c => c >= 0x21 && c <= 0x7e) &&
      uri != null &&
      uri.getScheme != null &&
      (uri.getScheme.equalsIgnoreCase("http") ||
        uri.getScheme.equalsIgnoreCase("https")) &&
      uri.getHost != null &&
      uri.getHost.nonEmpty &&
      uri.getUserInfo == null &&
      (uri.getRawPath == null || uri.getRawPath.isEmpty) &&
      uri.getRawQuery == null &&
      uri.getRawFragment == null &&
      uri.getPort <= 65535
  }

  private def statusFor(error: Error): Int =
    error.code match {
      case ErrorCode.ParseError | ErrorCode.InvalidRequest |
          ErrorCode.InvalidParams | ErrorCode.HeaderMismatch |
          ErrorCode.MissingRequiredClientCapability |
          ErrorCode.UnsupportedProtocolVersion =>
        400
      case ErrorCode.MethodNotFound => 404
      case ErrorCode.InternalError  => 500
      case _                        => 200
    }
}
