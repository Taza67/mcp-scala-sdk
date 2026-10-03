package io.github.taza67.mcp.transport.http

import java.io.IOException
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler

import io.github.taza67.mcp.client.ClientError
import io.github.taza67.mcp.client.ClientStream
import io.github.taza67.mcp.codec.Decoder
import io.github.taza67.mcp.codec.Encoder
import io.github.taza67.mcp.codec.WireLimits
import io.github.taza67.mcp.codec.jsonrpc.{Messages => JsonRpcMessages}
import io.github.taza67.mcp.codec.mcp.{Messages => McpMessages}
import io.github.taza67.mcp.codec.mcp.subscriptions.{
  Subscriptions => SubscriptionsCodec
}
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonValue
import io.github.taza67.mcp.protocol.jsonrpc.Error
import io.github.taza67.mcp.protocol.jsonrpc.ErrorResponse
import io.github.taza67.mcp.protocol.jsonrpc.InternalError
import io.github.taza67.mcp.protocol.jsonrpc.InvalidRequestError
import io.github.taza67.mcp.protocol.jsonrpc.Message
import io.github.taza67.mcp.protocol.jsonrpc.ParseError
import io.github.taza67.mcp.protocol.jsonrpc.Request
import io.github.taza67.mcp.protocol.jsonrpc.RequestId
import io.github.taza67.mcp.protocol.jsonrpc.SuccessResponse
import io.github.taza67.mcp.protocol.mcp.McpErrorResponse
import io.github.taza67.mcp.protocol.mcp.McpMessage
import io.github.taza67.mcp.protocol.mcp.McpRequest
import io.github.taza67.mcp.protocol.mcp.McpResponse
import io.github.taza67.mcp.protocol.mcp.RequestParams
import io.github.taza67.mcp.protocol.mcp.subscriptions.{
  Subscriptions => SubscriptionMethods
}
import io.github.taza67.mcp.server.ServerStream
import io.github.taza67.mcp.server.StreamingServer



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
 *
 *  When `endpoint.server` is a [[StreamingServer]] and the body is a request,
 *  the transport opens a [[ServerStream]] and peeks at its first item before
 *  committing: a first error response keeps the ordinary JSON status mapping
 *  (unknown methods stay 404), while a success or notification answers with
 *  SSE (`text/event-stream`). One daemon producer thread per active request
 *  pulls the stream, validated through the shared
 *  [[io.github.taza67.mcp.client.ClientStream]] correlation pipeline, into a
 *  bounded queue while the connection thread drains it, writing `:\n\n`
 *  heartbeat comments every [[heartbeatInterval]] of idle time. Synchronous
 *  [[io.github.taza67.mcp.server.Server]]s stay JSON-only. No GET streaming,
 *  sessions, or `Last-Event-ID` support.
 */
final case class HttpTransport(
    endpoint: HttpEndpoint,
    decoder: Decoder[JsonValue],
    encoder: Encoder[JsonValue],
    maxMessageSize: Int = WireLimits.DefaultMaxMessageSize,
    maxNestingDepth: Int = WireLimits.DefaultMaxNestingDepth,
    heartbeatInterval: Duration = Duration.ofSeconds(15),
    maxPendingMessages: Int = 64
) extends HttpHandler {

  require(
    maxMessageSize > 0 && maxMessageSize < Int.MaxValue,
    "maxMessageSize must be positive and strictly below Int.MaxValue"
  )
  require(
    maxNestingDepth > 0 && maxNestingDepth < Int.MaxValue,
    "maxNestingDepth must be positive and strictly below Int.MaxValue"
  )
  require(
    heartbeatInterval != null &&
      (try heartbeatInterval.toNanos > 0
       catch { case _: ArithmeticException => false }),
    "heartbeatInterval must be positive and fit in nanoseconds"
  )
  require(
    maxPendingMessages > 0 && maxPendingMessages < Int.MaxValue,
    "maxPendingMessages must be positive and strictly below Int.MaxValue"
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
    val decoded =
      try {
        decoder.decode(text) match {
          case Left(_)       => Left(ParseError())
          case Right(ast)    => Right(ast)
        }
      } catch {
        case NonFatal(_) => Left(InternalError())
      }
    decoded match {
      case Left(error) =>
        writeError(exchange, HttpEndpoint.statusFor(error), error)
      case Right(ast) => dispatchDecoded(exchange, ast)
    }
  }

  /** Streaming servers get the `open` path only for request-shaped bodies;
   *  notifications and plain servers keep the synchronous JSON path.
   */
  private def dispatchDecoded(exchange: HttpExchange, ast: JsonValue): Unit =
    endpoint.server match {
      case streaming: StreamingServer if isRequestShape(ast) =>
        dispatchStreaming(exchange, streaming, ast)
      case _ =>
        val outcome =
          try {
            Right(
              endpoint.handle(
                exchange.getRequestMethod,
                headersOf(exchange),
                ast
              )
            )
          } catch {
            case NonFatal(_) => Left(InternalError())
          }
        outcome match {
          case Left(error)     => writeError(exchange, 500, error)
          case Right(response) => writeResponse(exchange, response)
        }
    }

  private def isRequestShape(body: JsonValue): Boolean =
    body match {
      case obj: JsonObject =>
        JsonRpcMessages.toMessage(JsonObject(obj.value - Message.ParamsKey)) match {
          case Right(_: Request) => true
          case _                 => false
        }
      case _ => false
    }

  private def dispatchStreaming(
      exchange: HttpExchange,
      server: StreamingServer,
      ast: JsonValue
  ): Unit =
    endpoint.decodeRequest(headersOf(exchange), ast) match {
      case Left(response) => writeResponse(exchange, response)
      case Right(request) =>
        val opened =
          try server.open(request)
          catch { case NonFatal(_) => Left(InternalError()) }
        opened match {
          case Left(error) =>
            writeResponse(
              exchange,
              HttpResponse(
                HttpEndpoint.statusFor(error),
                Some(
                  JsonRpcMessages.fromMessage(
                    ErrorResponse(error, Some(request.id))
                  )
                ),
                jsonHeaders
              )
            )
          case Right(stream) => streamSse(exchange, request, stream)
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

  /** Serves `request` as SSE over the already-open `source`.
   *
   *  The producer starts before any response bytes and the first queue item
   *  decides the handshake: a first `McpErrorResponse` keeps the ordinary
   *  JSON status mapping (unknown methods stay 404), a first success or
   *  notification commits SSE (200, chunked), a `NonFatal` producer failure
   *  or an end-before-first-item answers a correlated JSON 500, and a fatal
   *  producer failure commits the SSE headers first (so a JDK client does
   *  not retry an unanswered POST) before the original is relayed on this
   *  thread. Once committed there is no JSON fallback: write failures
   *  cancel the source and stop the producer in `finally`, and a producer
   *  still alive after the bounded join surfaces as an `IOException`.
   */
  private def streamSse(
      exchange: HttpExchange,
      request: McpRequest,
      source: ServerStream
  ): Unit = {
    val owned = new HttpTransport.CloseOnceStream(source)
    val validated = validatedStream(request, owned)
    val queue =
      new ArrayBlockingQueue[HttpTransport.StreamItem](maxPendingMessages)
    val stopping = new AtomicBoolean(false)
    val producer = new Thread(
      new Runnable {
        def run(): Unit = produceSse(validated, queue, stopping)
      },
      "mcp-http-sse-producer"
    )
    producer.setDaemon(true)

    var primary: Throwable = null
    try {
      producer.start()
      queue.take() match {
        case HttpTransport.StreamMessage(error: McpErrorResponse) =>
          // Ordinary request errors keep the JSON status mapping; no SSE.
          writeJsonMessage(
            exchange,
            HttpEndpoint.statusFor(error.error),
            error
          )
        case HttpTransport.StreamMessage(_: McpRequest) =>
          // Unreachable through the validated pipeline; never forward.
          writeJsonMessage(exchange, 500, internalError(request.id))
        case HttpTransport.StreamMessage(message) =>
          commitSseHeaders(exchange)
          if (
            emitSseEvent(exchange.getResponseBody, request.id, message) &&
            !message.isInstanceOf[McpResponse]
          ) drainSse(exchange.getResponseBody, queue, request.id)
        case HttpTransport.StreamEnd =>
          // The stream ended before any item: no fabricated success.
          writeJsonMessage(exchange, 500, internalError(request.id))
        case HttpTransport.StreamFailed(failure) =>
          if (NonFatal(failure))
            writeJsonMessage(exchange, 500, internalError(request.id))
          else {
            // Commit so a JDK client sees a completed exchange before the
            // original fatal is relayed on this handler thread.
            try commitSseHeaders(exchange)
            catch { case NonFatal(_) => () }
            throw failure
          }
      }
    } catch {
      case failure: Throwable =>
        primary = failure
        throw failure
    } finally {
      stopping.set(true)
      var cleanup: Throwable = null
      try owned.close()
      catch {
        case NonFatal(_) =>
          cleanup = new IOException("MCP SSE source close failure")
        case fatalError: Throwable => cleanup = fatalError
      }
      producer.interrupt()
      try producer.join(1000L)
      catch {
        case interrupted: InterruptedException =>
          if (cleanup == null) cleanup = interrupted
      }
      if (producer.isAlive && cleanup == null)
        cleanup = new IOException("MCP SSE producer did not stop")
      // A secondary cleanup failure is preserved only over a primary
      // nonfatal outcome; a primary fatal keeps its identity.
      if (cleanup != null && (primary == null || NonFatal(primary)))
        throw cleanup
    }
  }

  private def internalError(id: RequestId): McpErrorResponse =
    McpErrorResponse(InternalError(), Some(id))

  /** Commits the SSE status line and headers (200, chunked body). */
  private def commitSseHeaders(exchange: HttpExchange): Unit = {
    exchange.getResponseHeaders
      .set("Content-Type", "text/event-stream; charset=utf-8")
    exchange.getResponseHeaders.set("Cache-Control", "no-store")
    exchange.getResponseHeaders.set("X-Accel-Buffering", "no")
    exchange.sendResponseHeaders(200, 0)
  }

  private def writeJsonMessage(
      exchange: HttpExchange,
      status: Int,
      message: McpMessage
  ): Unit =
    writeResponse(
      exchange,
      HttpResponse(status, Some(McpMessages.fromMessage(message)), jsonHeaders)
    )

  /** Wraps the borrowed stream in the shared client-side validation
   *  pipeline: `subscriptions/listen` gets the subscription contract
   *  (acknowledgement first, granted-filter notifications) and every other
   *  request gets correlated validation. When the listen params no longer
   *  decode the server stream only carries the `InvalidParams` error, so a
   *  correlated wrapper suffices. The adapter reads through `owned`, whose
   *  close-once guard lets the pipeline's automatic terminal cleanup and
   *  the handler `finally` share one underlying `close`.
   */
  private def validatedStream(
      request: McpRequest,
      owned: HttpTransport.CloseOnceStream
  ): ClientStream = {
    val adapted: ClientStream = new HttpTransport.ServerStreamAdapter(owned)
    if (request.method == SubscriptionMethods.listen)
      request.params match {
        case Some(params: RequestParams) =>
          SubscriptionsCodec.toSubscriptionsListenRequestParams(params) match {
            case Right(listen) =>
              ClientStream.subscription(request, listen.notifications, adapted)
            case Left(_) => ClientStream.correlated(request, adapted)
          }
        case _ => ClientStream.correlated(request, adapted)
      }
    else ClientStream.correlated(request, adapted)
  }

  /** Producer loop: pulls the validated stream into the bounded queue and
   *  marks the end (`None`, `NonFatal`, or fatal) for the handler thread.
   *  A response is terminal: once queued the source is never pulled again.
   *  `InterruptedException` ends quietly only when the handler initiated
   *  cancellation (`stopping`); an unsolicited interrupt is relayed through
   *  the failure marker like any other fatal.
   */
  private def produceSse(
      source: ClientStream,
      queue: ArrayBlockingQueue[HttpTransport.StreamItem],
      stopping: AtomicBoolean
  ): Unit =
    try {
      var live = true
      while (live)
        source.next() match {
          case Right(Some(message)) =>
            queue.put(HttpTransport.StreamMessage(message))
            live = !message.isInstanceOf[McpResponse]
          case Right(None) =>
            queue.put(HttpTransport.StreamEnd)
            live = false
          case Left(_) =>
            // Validation or transport failure: emit a generic internal
            // error; `ClientError` detail never reaches the wire.
            queue.put(
              HttpTransport.StreamFailed(
                new IOException("MCP stream failure")
              )
            )
            live = false
        }
    } catch {
      case _: InterruptedException if stopping.get => ()
      case failure: Throwable                      =>
        offerSseItem(queue, HttpTransport.StreamFailed(failure), stopping)
    }

  /** Delivers a terminal marker even when the producer's interrupt flag was
   *  already set (e.g. a source that interrupts itself then throws): `put`
   *  throws `InterruptedException` and clears the flag, so the offer retries
   *  until the item is in or the handler initiated cancellation.
   */
  private def offerSseItem(
      queue: ArrayBlockingQueue[HttpTransport.StreamItem],
      item: HttpTransport.StreamItem,
      stopping: AtomicBoolean
  ): Unit = {
    var offered = false
    while (!offered && !stopping.get)
      try {
        queue.put(item)
        offered = true
      } catch { case _: InterruptedException => () }
  }

  /** Consumer loop on the connection thread: drains the queue, emits SSE
   *  events, and writes heartbeat comments when idle for the configured
   *  interval. Ends on the terminal response, an abrupt source end, or a
   *  producer failure marker.
   */
  private def drainSse(
      out: OutputStream,
      queue: ArrayBlockingQueue[HttpTransport.StreamItem],
      id: RequestId
  ): Unit = {
    val heartbeatMillis = math.max(1L, heartbeatInterval.toMillis)
    var running = true
    while (running)
      queue.poll(heartbeatMillis, TimeUnit.MILLISECONDS) match {
        case null =>
          out.write(HttpTransport.HeartbeatBytes)
          out.flush()
        case HttpTransport.StreamMessage(_: McpRequest) =>
          // Unreachable through the validated pipeline; never forward.
          emitSseInternalError(out, id)
          running = false
        case HttpTransport.StreamMessage(message) =>
          running =
            emitSseEvent(out, id, message) &&
              !message.isInstanceOf[McpResponse]
        case HttpTransport.StreamEnd =>
          running = false
        case HttpTransport.StreamFailed(failure) =>
          if (!NonFatal(failure)) throw failure
          emitSseInternalError(out, id)
          running = false
      }
  }

  /** Emits one SSE event; returns false when the stream must end (encoder
   *  failure degrades to one generic correlated internal-error event).
   */
  private def emitSseEvent(
      out: OutputStream,
      id: RequestId,
      message: McpMessage
  ): Boolean =
    encodeSseEvent(McpMessages.fromMessage(message)) match {
      case Some(bytes) =>
        out.write(bytes)
        out.flush()
        true
      case None =>
        emitSseInternalError(out, id)
        false
    }

  /** Emits one generic correlated internal-error event through the
   *  configured encoder; a second failure leaves the stream to close
   *  abruptly without hand-writing an id.
   */
  private def emitSseInternalError(out: OutputStream, id: RequestId): Unit =
    encodeSseEvent(
      JsonRpcMessages.fromMessage(ErrorResponse(InternalError(), Some(id)))
    ) match {
      case Some(bytes) =>
        out.write(bytes)
        out.flush()
      case None => ()
    }

  private def encodeSseEvent(body: JsonObject): Option[Array[Byte]] =
    try HttpUtf8.encode(Sse.encode(encoder.encode(body))).toOption
    catch { case NonFatal(_) => None }

  private def headersOf(exchange: HttpExchange): Map[String, List[String]] =
    exchange.getRequestHeaders.asScala.iterator
      .map { case (name, values) => name -> values.asScala.toList }
      .toMap
}

object HttpTransport {

  /** SSE comment heartbeat written when the drain loop idles for one
   *  heartbeat interval.
   */
  private val HeartbeatBytes: Array[Byte] =
    ":\n\n".getBytes(StandardCharsets.US_ASCII)

  /** Producer-to-handler queue item for an active SSE exchange. */
  private sealed trait StreamItem

  /** One stream message pulled from the validated stream. */
  private final case class StreamMessage(message: McpMessage)
      extends StreamItem

  /** The source ended without a further message (abrupt end). */
  private case object StreamEnd extends StreamItem

  /** The source failed; `NonFatal` causes emit one generic internal error,
   *  genuine fatals are relayed on the handler thread.
   */
  private final case class StreamFailed(cause: Throwable) extends StreamItem

  /** Close-once wrapper so the validated pipeline's automatic cleanup and
   *  the handler `finally` share exactly one close of the borrowed source.
   */
  private final class CloseOnceStream(source: ServerStream)
      extends ServerStream {
    private val closed = new AtomicBoolean(false)

    def next(): Option[McpMessage] = source.next()

    override def close(): Unit =
      if (closed.compareAndSet(false, true)) source.close()
  }

  /** Client-stream view over a borrowed [[ServerStream]] so the shared
   *  [[ClientStream]] validation pipeline owns correlation. A `NonFatal`
   *  pull failure maps to `TransportFailure`; fatals propagate raw.
   */
  private final class ServerStreamAdapter(source: ServerStream)
      extends ClientStream {
    def next(): Either[ClientError, Option[McpMessage]] =
      try Right(source.next())
      catch { case NonFatal(_) => Left(ClientError.TransportFailure) }

    def close(): Unit = source.close()
  }
}
