package io.github.taza67.mcp.transport.http

import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.{HttpResponse => JdkHttpResponse}
import java.net.http.HttpResponse.BodyHandlers
import java.time.Duration
import java.util.Locale
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal

import io.github.taza67.mcp.client.ClientError
import io.github.taza67.mcp.client.ClientStream
import io.github.taza67.mcp.client.StreamingClientTransport
import io.github.taza67.mcp.codec.Decoder
import io.github.taza67.mcp.codec.Encoder
import io.github.taza67.mcp.codec.WireLimits
import io.github.taza67.mcp.codec.mcp.{Messages => McpMessages}
import io.github.taza67.mcp.protocol.json.JsonValue
import io.github.taza67.mcp.protocol.mcp.McpErrorResponse
import io.github.taza67.mcp.protocol.mcp.McpMessage
import io.github.taza67.mcp.protocol.mcp.McpNotification
import io.github.taza67.mcp.protocol.mcp.McpRequest
import io.github.taza67.mcp.protocol.mcp.McpResponse



/** JDK `HttpClient` transport for the MCP streamable-HTTP binding.
 *
 *  `open` POSTs a JSON-RPC request and returns a [[ClientStream]] already
 *  wrapped in request-id correlation: `application/json` responses become a
 *  one-item stream carrying the decoded response, while a 200
 *  `text/event-stream` body stays open and yields each SSE `data` event as a
 *  lazily decoded message. `exchange` consumes notifications through
 *  `onNotification` and returns the terminal response.
 *
 *  `timeout` bounds network work only: waiting for headers
 *  (`HttpRequest.timeout`) plus the total `exchange` including body reads.
 *  A shared daemon scheduler ([[HttpTimers]]) closes the active body when
 *  the exchange deadline passes. Streams returned by `open` carry no
 *  cumulative body timeout; the caller owns and closes them. `onNotification`
 *  is caller-owned code and must return cooperatively: it runs on the
 *  reading thread and is not separately time-bounded or isolated.
 *
 *  Protocol-owned headers (`MCP-*`, `Accept`, `Content-Type`, hop-by-hop
 *  headers) cannot be overridden through `additionalHeaders`; `Authorization`
 *  and other safe ASCII headers are allowed, without case-variant
 *  duplicates. The configured `httpClient` must never follow redirects so
 *  credentials are not forwarded.
 */
final case class HttpClientTransport(
    endpoint: URI,
    decoder: Decoder[JsonValue],
    encoder: Encoder[JsonValue],
    httpClient: HttpClient = HttpClient
      .newBuilder()
      .followRedirects(HttpClient.Redirect.NEVER)
      .connectTimeout(Duration.ofSeconds(30))
      .build(),
    additionalHeaders: Map[String, String] = Map.empty,
    timeout: Duration = Duration.ofSeconds(30),
    maxMessageSize: Int = WireLimits.DefaultMaxMessageSize,
    maxNestingDepth: Int = WireLimits.DefaultMaxNestingDepth,
    onNotification: McpNotification => Unit = _ => ()
) extends StreamingClientTransport {

  require(
    endpoint.getScheme != null &&
      (endpoint.getScheme.equalsIgnoreCase("http") ||
        endpoint.getScheme.equalsIgnoreCase("https")) &&
      endpoint.getHost != null &&
      endpoint.getUserInfo == null &&
      endpoint.getFragment == null,
    "endpoint must be an absolute http(s) URI with a host and without user-info or fragment"
  )
  require(
    httpClient.followRedirects() == HttpClient.Redirect.NEVER,
    "httpClient must never follow redirects"
  )
  require(
    HttpClientTransport.fitsNanos(timeout),
    "timeout must be positive and fit in a Long of nanoseconds"
  )
  require(
    maxMessageSize > 0 && maxMessageSize < Int.MaxValue,
    "maxMessageSize must be positive and less than Int.MaxValue"
  )
  require(
    maxNestingDepth > 0 && maxNestingDepth < Int.MaxValue,
    "maxNestingDepth must be positive and less than Int.MaxValue"
  )
  require(
    additionalHeaders.forall { case (name, value) =>
      HttpClientTransport.headerNameOk(name) &&
        HeaderValues.isPlainText(value) &&
        !HttpClientTransport.reservedHeader(name)
    },
    "additionalHeaders must use token names, plain ASCII values, and must not override managed headers"
  )
  require(
    additionalHeaders.keys
      .map(_.toLowerCase(Locale.ROOT))
      .toSet
      .size == additionalHeaders.size,
    "additionalHeaders must not contain case-insensitive duplicates"
  )

  def open(request: McpRequest): Either[ClientError, ClientStream] = {
    val started = System.nanoTime()
    val outcome =
      try {
        RequestHeaders.fromRequest(request) match {
          case Left(_) => Left(ClientError.TransportFailure)
          case Right(protocolHeaders) =>
            encodeRequestBody(request) match {
              case Left(error) => Left(error)
              case Right(bodyBytes) =>
                val response = httpClient.send(
                  buildRequest(bodyBytes, protocolHeaders),
                  BodyHandlers.ofInputStream()
                )
                openResponse(response, started)
            }
        }
      } catch { case NonFatal(_) => Left(ClientError.TransportFailure) }
    outcome.map(source => ClientStream.correlated(request, source))
  }

  def exchange(request: McpRequest): Either[ClientError, McpResponse] = {
    val started = System.nanoTime()
    open(request) match {
      case Left(error) => Left(error)
      case Right(stream) =>
        val delay = timeout.toNanos - (System.nanoTime() - started)
        val deadline = HttpTimers.scheduler.schedule(
          (() => closeQuietly(stream)): Runnable,
          math.max(delay, 0L),
          TimeUnit.NANOSECONDS
        )
        try {
          var outcome: Either[ClientError, McpResponse] =
            Left(ClientError.TransportFailure)
          var reading = true
          while (reading) {
            if (expired(started)) {
              // The deadline passed between items; nothing buffered is
              // accepted once network time ran out.
              outcome = Left(ClientError.TransportFailure)
              reading = false
            } else
              stream.next() match {
                case Left(error) =>
                  outcome = Left(error)
                  reading = false
                case Right(None) =>
                  reading = false
                case Right(Some(response: McpResponse)) =>
                  outcome =
                    if (expired(started)) Left(ClientError.TransportFailure)
                    else Right(response)
                  reading = false
                case Right(Some(notification: McpNotification)) =>
                  try onNotification(notification)
                  catch {
                    case NonFatal(_) =>
                      outcome = Left(ClientError.TransportFailure)
                      reading = false
                  }
                case Right(Some(_)) =>
                  reading = false
              }
          }
          outcome
        } finally {
          deadline.cancel(false)
          closeQuietly(stream)
        }
    }
  }

  private def expired(started: Long): Boolean =
    System.nanoTime() - started >= timeout.toNanos

  private def buildRequest(
      bodyBytes: Array[Byte],
      protocolHeaders: Map[String, String]
  ): HttpRequest = {
    val builder = HttpRequest
      .newBuilder(endpoint)
      .timeout(timeout)
      .header("Content-Type", "application/json")
      .header("Accept", "application/json, text/event-stream")
      .POST(BodyPublishers.ofByteArray(bodyBytes))
    protocolHeaders.foreach { case (name, value) =>
      builder.header(name, value)
    }
    additionalHeaders.foreach { case (name, value) =>
      builder.header(name, value)
    }
    builder.build()
  }

  /** Encodes the request AST strictly and bounds it to `maxMessageSize`. */
  private def encodeRequestBody(
      request: McpRequest
  ): Either[ClientError, Array[Byte]] =
    try
      HttpUtf8.encode(encoder.encode(McpMessages.fromRequest(request))) match {
        case Right(bytes) if bytes.length <= maxMessageSize => Right(bytes)
        case _ => Left(ClientError.TransportFailure)
      }
    catch { case NonFatal(_) => Left(ClientError.TransportFailure) }

  /** Routes a response by status and Content-Type. The acquired body stays
   *  owned here until a successful SSE handoff; the close-once owner is
   *  shared with the deadline timer so every outcome closes the underlying
   *  stream exactly once. A successful JSON decode still fails when the
   *  owned cleanup fails; a primary `Left` or throwable is preserved.
   */
  private def openResponse(
      response: JdkHttpResponse[InputStream],
      started: Long
  ): Either[ClientError, ClientStream] = {
    val body = new CloseOnceInputStream(response.body())
    var handedOff = false
    var primaryFatal: Throwable = null
    try {
      val status = response.statusCode()
      val headers = headerMapOf(response.headers())
      if (status >= 300 && status < 400) Left(ClientError.TransportFailure)
      else if (status == 202 || status == 204) Left(ClientError.TransportFailure)
      else if (
        HttpMedia
          .headerValues("content-encoding", headers)
          .exists(!_.equalsIgnoreCase("identity"))
      ) Left(ClientError.TransportFailure)
      else if (
        status == 200 && HttpMedia.contentTypeIs(headers, "text/event-stream")
      ) {
        val stream: ClientStream =
          new SseBodyStream(new SseReader(body, maxMessageSize), body)
        handedOff = true
        Right(stream)
      } else if (HttpMedia.contentTypeIs(headers, "application/json"))
        readJsonBody(body, started).flatMap { message =>
          if (status / 100 != 2 && !message.isInstanceOf[McpErrorResponse])
            Left(ClientError.TransportFailure)
          else
            body.closeForResult().map { _ =>
              new SingleStream(message): ClientStream
            }
        }
      else Left(ClientError.TransportFailure)
    } catch {
      case fatal: Throwable if !NonFatal(fatal) =>
        primaryFatal = fatal
        preserveCloseOnFatal(body)
        throw fatal
    } finally {
      if (!handedOff)
        // A secondary close failure must not mask an in-flight fatal.
        if (primaryFatal == null) discardClose(body)
        else preserveCloseOnFatal(body)
    }
  }

  /** Runs the close-once cleanup where a primary `Left` or returned value
   *  is already decided: the cached `NonFatal` close outcome is discarded
   *  while a fatal close failure still propagates.
   */
  private def discardClose(body: CloseOnceInputStream): Unit = {
    body.closeForResult()
    ()
  }

  /** Runs the close-once cleanup while a primary fatal throwable is being
   *  propagated: a secondary close failure must not replace it. Only used
   *  on the fatal-unwind path, never as generic cleanup.
   */
  private def preserveCloseOnFatal(body: CloseOnceInputStream): Unit =
    try {
      body.closeForResult()
      ()
    } catch { case _: Throwable => () }

  /** Reads a bounded JSON body under the remaining exchange deadline and
   *  decodes an [[McpResponse]]; results decoded after the deadline are
   *  discarded. The body itself is closed by the caller.
   */
  private def readJsonBody(
      body: CloseOnceInputStream,
      started: Long
  ): Either[ClientError, McpResponse] = {
    val remaining = timeout.toNanos - (System.nanoTime() - started)
    if (remaining <= 0L) Left(ClientError.TransportFailure)
    else {
      val deadline = HttpTimers.scheduler.schedule(
        (() => discardClose(body)): Runnable,
        remaining,
        TimeUnit.NANOSECONDS
      )
      try {
        val decoded =
          HttpInput.read(body, maxMessageSize) match {
            case Left(_) => Left(ClientError.TransportFailure)
            case Right(text) =>
              decodeMessage(text).flatMap {
                case response: McpResponse => Right(response)
                case _                     => Left(ClientError.TransportFailure)
              }
          }
        decoded match {
          case Right(_) if expired(started) =>
            Left(ClientError.TransportFailure)
          case other => other
        }
      } finally deadline.cancel(false)
    }
  }

  private def decodeMessage(
      text: String
  ): Either[ClientError, McpMessage] =
    if (WireLimits.exceedsNesting(text, maxNestingDepth))
      Left(ClientError.TransportFailure)
    else
      try
        decoder.decode(text).flatMap(McpMessages.toMessage(_)) match {
          case Right(message) => Right(message)
          case Left(_)        => Left(ClientError.TransportFailure)
        }
      catch { case NonFatal(_) => Left(ClientError.TransportFailure) }

  private def closeQuietly(closeable: AutoCloseable): Unit =
    try closeable.close()
    catch { case NonFatal(_) => () }

  private def headerMapOf(
      headers: java.net.http.HttpHeaders
  ): Map[String, List[String]] =
    headers.map().asScala.toMap.map { case (name, values) =>
      name -> values.asScala.toList
    }

  /** Live SSE-backed stream: pulls one decoded message per event; end of the
   *  body is `None` (the correlating wrapper turns an early EOF into a
   *  transport failure). `closed` is atomic so `close()` from another thread
   *  races safely with an in-flight `next()` and closes the body exactly
   *  once.
   */
  private final class SseBodyStream(
      reader: SseReader,
      body: CloseOnceInputStream
  ) extends ClientStream {
    private val closed = new AtomicBoolean(false)

    def next(): Either[ClientError, Option[McpMessage]] =
      if (closed.get) Right(None)
      else {
        val pulled =
          try reader.next()
          catch {
            case NonFatal(_) => Left(ClientError.TransportFailure)
            case fatal: Throwable =>
              closeInternal()
              throw fatal
          }
        if (closed.get) Right(None)
        else
          pulled match {
            case Left(_) =>
              closeInternal()
              Left(ClientError.TransportFailure)
            case Right(None) =>
              closeInternal()
              Right(None)
            case Right(Some(data)) =>
              val decoded =
                try decodeMessage(data)
                catch {
                  case fatal: Throwable =>
                    closeInternal()
                    throw fatal
                }
              decoded match {
                case Left(error) =>
                  closeInternal()
                  Left(error)
                case Right(message) => Right(Some(message))
              }
          }
      }

    private def closeInternal(): Unit =
      if (closed.compareAndSet(false, true)) discardClose(body)

    def close(): Unit =
      if (closed.compareAndSet(false, true)) body.close()
  }

  /** Response-body owner whose underlying `close()` runs exactly once no
   *  matter which path wins: the deadline timer, rejection cleanup, or an
   *  explicit stream close. `closeForResult` caches the outcome so a
   *  `NonFatal` close failure can be folded into the open result instead of
   *  being thrown from `finally`; a fatal close failure is cached and
   *  rethrown.
   */
  private final class CloseOnceInputStream(delegate: InputStream)
      extends FilterInputStream(delegate) {
    private var closeResult: Either[ClientError, Unit] = _
    private var closeFatal: Throwable = _

    def closeForResult(): Either[ClientError, Unit] =
      this.synchronized {
        if (closeFatal != null) throw closeFatal
        if (closeResult == null) {
          try {
            delegate.close()
            closeResult = Right(())
          } catch {
            case NonFatal(_) =>
              closeResult = Left(ClientError.TransportFailure)
            case fatal: Throwable =>
              closeFatal = fatal
              throw fatal
          }
        }
        closeResult
      }

    /** Throws on a failed close so the correlating wrapper maps it to its
     *  static `MCP stream close failure` `IOException`.
     */
    override def close(): Unit =
      closeForResult().fold(
        _ => throw new IOException("MCP response body close failure"),
        _ => ()
      )
  }

  /** One-item stream for a JSON response already fully read. */
  private final class SingleStream(message: McpMessage) extends ClientStream {
    private val pending = new AtomicBoolean(true)

    def next(): Either[ClientError, Option[McpMessage]] =
      if (pending.getAndSet(false)) Right(Some(message))
      else Right(None)

    def close(): Unit = pending.set(false)
  }
}

object HttpClientTransport {

  /** Header names callers must not override: protocol-owned `MCP-*`,
   *  representation metadata, and hop-by-hop or JDK-managed fields.
   */
  private val ReservedHeaders = Set(
    "accept",
    "content-type",
    "host",
    "content-length",
    "content-encoding",
    "connection",
    "transfer-encoding",
    "expect",
    "upgrade"
  )

  private def fitsNanos(timeout: Duration): Boolean =
    try timeout.toNanos > 0
    catch { case _: ArithmeticException => false }

  private def headerNameOk(name: String): Boolean =
    name.nonEmpty && name.forall(HttpMedia.isTokenChar)

  private def reservedHeader(name: String): Boolean = {
    val lower = name.toLowerCase(Locale.ROOT)
    lower.startsWith("mcp-") || ReservedHeaders.contains(lower)
  }
}

/** Shared daemon deadline scheduler for all [[HttpClientTransport]]
 *  instances: one thread at most, ever; cancelled tasks are removed.
 */
private[http] object HttpTimers {

  /** Deadline threads ever created; a test seam proving transports share
   *  this scheduler instead of owning a pool each.
   */
  private[http] val createdThreads = new AtomicInteger(0)

  private[http] val scheduler: ScheduledThreadPoolExecutor = {
    val executor = new ScheduledThreadPoolExecutor(
      1,
      (runnable: Runnable) => {
        val thread = new Thread(runnable, "mcp-http-client-deadline")
        thread.setDaemon(true)
        createdThreads.incrementAndGet()
        thread
      }
    )
    executor.setRemoveOnCancelPolicy(true)
    executor
  }
}
