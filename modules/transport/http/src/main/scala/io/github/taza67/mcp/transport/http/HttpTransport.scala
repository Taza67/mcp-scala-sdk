package io.github.taza67.mcp.transport.http

import java.nio.charset.StandardCharsets

import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler

import io.github.taza67.mcp.codec.Decoder
import io.github.taza67.mcp.codec.Encoder
import io.github.taza67.mcp.codec.WireLimits
import io.github.taza67.mcp.codec.jsonrpc.{Messages => JsonRpcMessages}
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonValue
import io.github.taza67.mcp.protocol.jsonrpc.Error
import io.github.taza67.mcp.protocol.jsonrpc.ErrorResponse
import io.github.taza67.mcp.protocol.jsonrpc.InternalError
import io.github.taza67.mcp.protocol.jsonrpc.InvalidRequestError
import io.github.taza67.mcp.protocol.jsonrpc.ParseError
import io.github.taza67.mcp.protocol.jsonrpc.SuccessResponse



/** JDK `HttpHandler` adapter over an [[HttpEndpoint]].
 *
 *  The `HttpServer` instance and its executor stay caller-owned; this handler
 *  owns only the per-exchange request/response streams and always closes the
 *  exchange. Exact context-path match is required (JDK contexts prefix-match).
 *  Preflight runs before the body is read; input is bounded, strict-UTF-8,
 *  nesting-guarded, then decoded through the injected backend. Adapter-level
 *  parse/limit error bodies are id-less JSON-RPC errors (no request id was
 *  established); request-level errors produced inside [[HttpEndpoint]] carry
 *  the request's correlation id. JSON no-store headers accompany bodies.
 */
final case class HttpTransport(
    endpoint: HttpEndpoint,
    decoder: Decoder[JsonValue],
    encoder: Encoder[JsonValue],
    maxMessageSize: Int = WireLimits.DefaultMaxMessageSize,
    maxNestingDepth: Int = WireLimits.DefaultMaxNestingDepth
) extends HttpHandler {

  require(
    maxMessageSize > 0 && maxMessageSize < Int.MaxValue,
    "maxMessageSize must be positive and strictly below Int.MaxValue"
  )
  require(
    maxNestingDepth > 0 && maxNestingDepth < Int.MaxValue,
    "maxNestingDepth must be positive and strictly below Int.MaxValue"
  )

  private val jsonHeaders =
    Map("Content-Type" -> "application/json", "Cache-Control" -> "no-store")

  private val fallbackBodyBytes: Array[Byte] =
    """{"jsonrpc":"2.0","error":{"code":-32603,"message":"Internal error"}}"""
      .getBytes(StandardCharsets.UTF_8)

  override def handle(exchange: HttpExchange): Unit =
    try {
      if (
        exchange.getRequestURI.getPath != exchange.getHttpContext.getPath
      ) writeResponse(exchange, HttpResponse(404))
      else
        endpoint.preflight(
          exchange.getRequestMethod,
          headersOf(exchange)
        ) match {
          case Left(response) => writeResponse(exchange, response)
          case Right(())      => readBody(exchange)
        }
    } finally exchange.close()

  private def readBody(exchange: HttpExchange): Unit =
    HttpInput.read(exchange.getRequestBody, maxMessageSize) match {
      case Left(error: InvalidRequestError) => writeError(exchange, 413, error)
      case Left(error)                      => writeError(exchange, 400, error)
      case Right(text) =>
        if (WireLimits.exceedsNesting(text, maxNestingDepth))
          writeError(
            exchange,
            400,
            InvalidRequestError("HTTP message exceeds nesting limit")
          )
        else decodeAndDispatch(exchange, text)
    }

  /** Decoder `Left` and thrown failures become static wire errors; the
   *  response body is never written inside the guard.
   */
  private def decodeAndDispatch(exchange: HttpExchange, text: String): Unit = {
    val outcome =
      try {
        decoder.decode(text) match {
          case Left(_) => Left(ParseError())
          case Right(ast) =>
            Right(
              endpoint.handle(
                exchange.getRequestMethod,
                headersOf(exchange),
                ast
              )
            )
        }
      } catch {
        case NonFatal(_) => Left(InternalError())
      }
    outcome match {
      case Left(_: ParseError) => writeError(exchange, 400, ParseError())
      case Left(error)         => writeError(exchange, 500, error)
      case Right(response)     => writeResponse(exchange, response)
    }
  }

  private def writeError(exchange: HttpExchange, status: Int, error: Error): Unit =
    writeResponse(
      exchange,
      HttpResponse(
        status,
        Some(JsonRpcMessages.fromMessage(ErrorResponse(error))),
        jsonHeaders
      )
    )

  private def writeResponse(exchange: HttpExchange, response: HttpResponse): Unit =
    response.body match {
      case None =>
        response.headers.foreach { case (name, value) =>
          exchange.getResponseHeaders.set(name, value)
        }
        exchange.sendResponseHeaders(response.status, -1)
      case Some(body) =>
        // Encode before committing the status so a throwing encoder cannot
        // half-commit a 200.
        val (status, bytes) = encodeBody(body, response.status)
        response.headers.foreach { case (name, value) =>
          exchange.getResponseHeaders.set(name, value)
        }
        exchange.sendResponseHeaders(status, bytes.length)
        val out = exchange.getResponseBody
        out.write(bytes)
        out.flush()
    }

  private def encodeBody(body: JsonObject, status: Int): (Int, Array[Byte]) =
    encodeOrFail(body) match {
      case Some(bytes) => (status, bytes)
      case None =>
        encodeOrFail(internalErrorFor(body)) match {
          case Some(bytes) => (500, bytes)
          // Last resort when even the configured encoder cannot render a
          // static error: a fixed ASCII fallback, deliberately id-less.
          case None        => (500, fallbackBodyBytes)
        }
    }

  /** Renders a body with the configured encoder; a thrown `NonFatal` or
   *  unencodable UTF-16 (e.g. unpaired surrogates) is an encoding failure.
   */
  private def encodeOrFail(body: JsonObject): Option[Array[Byte]] =
    try HttpUtf8.encode(encoder.encode(body)).toOption
    catch { case NonFatal(_) => None }

  /** Preserves correlation on encoder failure by reusing the id already
   *  validated into the response AST, never re-derived from raw text.
   */
  private def internalErrorFor(body: JsonObject): JsonObject = {
    val id = JsonRpcMessages.toMessage(body).toOption.flatMap {
      case response: SuccessResponse => Some(response.id)
      case response: ErrorResponse   => response.id
      case _                         => None
    }
    JsonRpcMessages.fromMessage(ErrorResponse(InternalError(), id))
  }

  private def headersOf(exchange: HttpExchange): Map[String, List[String]] =
    exchange.getRequestHeaders.asScala.iterator
      .map { case (name, values) => name -> values.asScala.toList }
      .toMap
}
