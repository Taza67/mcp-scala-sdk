package io.github.taza67.mcp.transport.http

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandlers
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

import com.sun.net.httpserver.Headers
import com.sun.net.httpserver.HttpContext
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpPrincipal
import com.sun.net.httpserver.HttpServer

import io.github.taza67.mcp.client.ClientError
import io.github.taza67.mcp.client.McpClient
import io.github.taza67.mcp.codec.Encoder
import io.github.taza67.mcp.codec.circe.JsonCodec
import io.github.taza67.mcp.codec.mcp.notifications.{Notifications => NotificationCodec}
import io.github.taza67.mcp.codec.mcp.subscriptions.{Subscriptions => SubscriptionsCodec}
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.json.JsonValue
import io.github.taza67.mcp.protocol.jsonrpc.Error
import io.github.taza67.mcp.protocol.jsonrpc.InternalError
import io.github.taza67.mcp.protocol.jsonrpc.InvalidRequestError
import io.github.taza67.mcp.protocol.jsonrpc.Method
import io.github.taza67.mcp.protocol.jsonrpc.NumberRequestId
import io.github.taza67.mcp.protocol.jsonrpc.RequestId
import io.github.taza67.mcp.protocol.jsonrpc.StringRequestId
import io.github.taza67.mcp.protocol.mcp.ClientCapabilities
import io.github.taza67.mcp.protocol.mcp.Implementation
import io.github.taza67.mcp.protocol.mcp.InfoLoggingLevel
import io.github.taza67.mcp.protocol.mcp.McpErrorResponse
import io.github.taza67.mcp.protocol.mcp.McpMessage
import io.github.taza67.mcp.protocol.mcp.McpNotification
import io.github.taza67.mcp.protocol.mcp.McpProtocolVersion20260728
import io.github.taza67.mcp.protocol.mcp.McpRequest
import io.github.taza67.mcp.protocol.mcp.McpResponse
import io.github.taza67.mcp.protocol.mcp.McpSuccessResponse
import io.github.taza67.mcp.protocol.mcp.NotificationMeta
import io.github.taza67.mcp.protocol.mcp.RequestMeta
import io.github.taza67.mcp.protocol.mcp.RequestParams
import io.github.taza67.mcp.protocol.mcp.Result
import io.github.taza67.mcp.protocol.mcp.StringProgressToken
import io.github.taza67.mcp.protocol.mcp.notifications.LoggingMessageNotificationParams
import io.github.taza67.mcp.protocol.mcp.notifications.ProgressNotificationParams
import io.github.taza67.mcp.protocol.mcp.notifications.{Notifications => NotificationMethods}
import io.github.taza67.mcp.protocol.mcp.discover.ServerDiscover
import io.github.taza67.mcp.protocol.mcp.subscriptions.SubscriptionFilter
import io.github.taza67.mcp.protocol.mcp.subscriptions.SubscriptionsAcknowledgedNotificationParams
import io.github.taza67.mcp.protocol.mcp.subscriptions.SubscriptionsListenRequestParams
import io.github.taza67.mcp.protocol.mcp.subscriptions.{Subscriptions => SubscriptionMethods}
import io.github.taza67.mcp.server.McpServer
import io.github.taza67.mcp.server.ServerStream
import io.github.taza67.mcp.server.StreamingServer
import io.github.taza67.mcp.server.SubscriptionHub
import io.github.taza67.mcp.server.SubscriptionServer
import munit.FunSuite



/** End-to-end SSE streaming over the JDK server transport. */
class HttpStreamingTransportSuite extends FunSuite {

  private val Utf8 = StandardCharsets.UTF_8
  private val info = Implementation(name = "srv", version = "1")

  private val meta = RequestMeta(
    protocolVersion = McpProtocolVersion20260728,
    clientCapabilities = ClientCapabilities()
  )

  private def transport(
      server: StreamingServer,
      encoder: Encoder[JsonValue] = JsonCodec.JsonValueEncoder,
      heartbeatInterval: Duration = Duration.ofSeconds(5)
  ): HttpTransport =
    HttpTransport(
      HttpEndpoint(server),
      JsonCodec.JsonValueDecoder,
      encoder,
      heartbeatInterval = heartbeatInterval
    )

  private def clientFor(
      uri: String,
      seen: Option[AtomicReference[List[McpNotification]]] = None
  ): HttpClientTransport =
    HttpClientTransport(
      URI.create(uri),
      JsonCodec.JsonValueDecoder,
      JsonCodec.JsonValueEncoder,
      timeout = Duration.ofSeconds(10),
      onNotification = seen match {
        case Some(reference) => n => reference.set(reference.get() :+ n)
        case None            => _ => ()
      }
    )

  private def withServer[A](handler: HttpTransport)(f: String => A): A = {
    val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/mcp", handler)
    val executor = Executors.newCachedThreadPool()
    server.setExecutor(executor)
    server.start()
    try f(s"http://127.0.0.1:${server.getAddress.getPort}/mcp")
    finally {
      server.stop(0)
      executor.shutdownNow()
      assert(
        executor.awaitTermination(5, TimeUnit.SECONDS),
        "test executor did not stop"
      )
    }
  }

  /** A streaming server whose `open` returns a scripted stream; `handle`
   *  answers an empty success for any method.
   */
  private final class ScriptedServer(stream: => ServerStream)
      extends StreamingServer {
    val opens = new AtomicInteger(0)
    def handle(request: McpRequest): McpResponse =
      McpSuccessResponse(Result.empty(), request.id)
    def open(request: McpRequest): Either[Error, ServerStream] = {
      opens.incrementAndGet()
      Right(stream)
    }
  }

  private final class CountingStream(delegate: ServerStream)
      extends ServerStream {
    val closes = new AtomicInteger(0)
    def next(): Option[McpMessage] = delegate.next()
    def close(): Unit = {
      closes.incrementAndGet()
      delegate.close()
    }
  }

  private def success(id: io.github.taza67.mcp.protocol.jsonrpc.RequestId) =
    McpSuccessResponse(result = Result.empty(), id = id)

  private def progressEvent(token: String): McpNotification =
    McpNotification(
      method = NotificationMethods.progress,
      params = Some(
        NotificationCodec.fromProgressNotificationParams(
          ProgressNotificationParams(
            progressToken = StringProgressToken(token),
            progress = 0.5
          )
        )
      )
    )

  private def loggingEvent: McpNotification =
    McpNotification(
      method = NotificationMethods.message,
      params = Some(
        NotificationCodec.fromLoggingMessageNotificationParams(
          LoggingMessageNotificationParams(
            level = InfoLoggingLevel,
            data = JsonString("hello")
          )
        )
      )
    )

  private def listenAck(
      id: RequestId,
      granted: SubscriptionFilter
  ): McpNotification =
    McpNotification(
      method = SubscriptionMethods.acknowledgedNotification,
      params = Some(
        SubscriptionsCodec.fromSubscriptionsAcknowledgedNotificationParams(
          SubscriptionsAcknowledgedNotificationParams(
            notifications = granted,
            meta = Some(NotificationMeta(subscriptionId = Some(id)))
          )
        )
      )
    )

  private def vendorEvent(
      subscriptionId: Option[RequestId] = None
  ): McpNotification =
    McpNotification(
      method = Method("vendor/custom"),
      params = Some(
        io.github.taza67.mcp.protocol.mcp.NotificationParams(
          meta = subscriptionId.map(id => NotificationMeta(Some(id)))
        )
      )
    )

  /** Server whose `open` streams the scripted messages for the request. */
  private def scriptServer(
      messages: McpRequest => List[McpMessage]
  ): StreamingServer =
    new StreamingServer {
      def handle(request: McpRequest): McpResponse = success(request.id)
      def open(request: McpRequest): Either[Error, ServerStream] =
        Right(ServerStream.fromIterator(messages(request).iterator))
    }

  /** Minimal exchange stub: canned request in, in-memory response out, and
   *  an optional `sendResponseHeaders` failure.
   */
  private final class StubExchange(
      context: HttpContext,
      requestBody: Array[Byte],
      requestHeaders: Headers,
      failSendHeaders: Boolean
  ) extends HttpExchange {
    private val responseHeaders = new Headers()
    private val responseBody = new ByteArrayOutputStream()
    val closed = new AtomicBoolean(false)

    def getRequestHeaders: Headers = requestHeaders
    def getResponseHeaders: Headers = responseHeaders
    def getRequestURI: URI = URI.create("/mcp")
    def getRequestMethod: String = "POST"
    def getHttpContext: HttpContext = context
    def close(): Unit = closed.set(true)
    def getRequestBody: InputStream = new ByteArrayInputStream(requestBody)
    def getResponseBody: OutputStream = responseBody
    def sendResponseHeaders(code: Int, length: Long): Unit =
      if (failSendHeaders) throw new IOException("send-failed") else ()
    def getRemoteAddress: InetSocketAddress =
      new InetSocketAddress("127.0.0.1", 40000)
    def getLocalAddress: InetSocketAddress =
      new InetSocketAddress("127.0.0.1", 80)
    def getProtocol: String = "HTTP/1.1"
    def getAttribute(name: String): Object = null
    def setAttribute(name: String, value: Object): Unit = ()
    def setStreams(in: InputStream, out: OutputStream): Unit = ()
    def getPrincipal: HttpPrincipal = null
    def getResponseCode: Int = -1
  }

  private def stubRequestHeaders(method: String): Headers = {
    val headers = new Headers()
    headers.add("Content-Type", "application/json")
    headers.add("Accept", "application/json, text/event-stream")
    headers.add("MCP-Protocol-Version", "2026-07-28")
    headers.add("Mcp-Method", method)
    headers
  }

  private def post(
      uri: String,
      body: String,
      headers: Seq[(String, String)]
  ): java.net.http.HttpResponse[String] = {
    val builder = HttpRequest
      .newBuilder(URI.create(uri))
      .timeout(Duration.ofSeconds(10))
      .POST(BodyPublishers.ofString(body, Utf8))
    headers.foreach { case (name, value) => builder.header(name, value) }
    HttpClient.newHttpClient.send(builder.build(), BodyHandlers.ofString(Utf8))
  }

  private def baseHeaders(method: String): Seq[(String, String)] =
    Seq(
      "Content-Type" -> "application/json",
      "Accept" -> "application/json, text/event-stream",
      "MCP-Protocol-Version" -> "2026-07-28",
      "Mcp-Method" -> method
    )

  private val discoverJson: String =
    s"""{"jsonrpc":"2.0","id":1,"method":"server/discover","params":{"_meta":{"${RequestMeta.ProtocolVersionKey}":"2026-07-28","${RequestMeta.ClientCapabilitiesKey}":{}}}}"""

  private def sseProducerThreads: Int =
    Thread.getAllStackTraces.keySet.toArray.count {
      case thread: Thread =>
        thread.isAlive && thread.getName == "mcp-http-sse-producer"
      case _ => false
    }

  test("listen receives ack, a published resource event, then a graceful end") {
    val hub = new SubscriptionHub(
      SubscriptionFilter(resourceSubscriptions = Some(List("file:///a"))),
      serverInfo = Some(info)
    )
    val server = SubscriptionServer(McpServer(info = info), hub)
    try {
      withServer(transport(server)) { uri =>
        val client = McpClient(clientFor(uri))
        val opened = client.listen(
          SubscriptionsListenRequestParams(
            meta = meta,
            notifications = SubscriptionFilter(
              resourceSubscriptions = Some(List("file:///a"))
            )
          )
        )
        assert(opened.isRight, s"listen failed: $opened")
        val stream = opened.toOption.get
        stream.next() match {
          case Right(Some(notification: McpNotification)) =>
            assertEquals(
              notification.method.value,
              "notifications/subscriptions/acknowledged"
            )
          case other => fail(s"expected ack, got $other")
        }
        hub.publishResourceUpdated("file:///a")
        stream.next() match {
          case Right(Some(notification: McpNotification)) =>
            assertEquals(
              notification.method.value,
              "notifications/resources/updated"
            )
          case other => fail(s"expected updated notification, got $other")
        }
        hub.close()
        stream.next() match {
          case Right(Some(_: McpSuccessResponse)) => ()
          case other => fail(s"expected terminal success, got $other")
        }
        assertEquals(stream.next(), Right(None))
        stream.close()
      }
      assertEquals(sseProducerThreads, 0)
    } finally hub.close()
  }

  test("ordinary discover runs over SSE as a single terminal response") {
    val hub = new SubscriptionHub(SubscriptionFilter())
    val server = SubscriptionServer(McpServer(info = info), hub)
    try {
      withServer(transport(server)) { uri =>
        val discovered = McpClient(clientFor(uri)).discover(meta)
        assert(discovered.isRight, s"discover failed: $discovered")
      }
      assertEquals(sseProducerThreads, 0)
    } finally hub.close()
  }

  test("an opted-in progress notification precedes the terminal response") {
    val seen = new AtomicReference[List[McpNotification]](Nil)
    val server = new StreamingServer {
      def handle(request: McpRequest): McpResponse = success(request.id)
      def open(request: McpRequest): Either[Error, ServerStream] =
        Right(
          ServerStream.fromIterator(
            Iterator[McpMessage](
              progressEvent("t-1"),
              success(request.id)
            )
          )
        )
    }
    withServer(transport(server)) { uri =>
      val result = McpClient(clientFor(uri, seen = Some(seen)))
        .request(
          ServerDiscover.method,
          RequestParams(
            meta = meta.copy(progressToken = Some(StringProgressToken("t-1")))
          )
        )
      assert(result.isRight, s"request failed: $result")
      assertEquals(
        seen.get().map(_.method.value),
        List("notifications/progress")
      )
    }
    assertEquals(sseProducerThreads, 0)
  }

  test("a nonfatal source failure emits one generic correlated error") {
    val server = new ScriptedServer(
      ServerStream.single(throw new RuntimeException("status-secret"))
    )
    withServer(transport(server)) { uri =>
      val result = McpClient(clientFor(uri))
        .request(ServerDiscover.method, RequestParams(meta = meta))
      assertEquals(result, Left(ClientError.RemoteError(InternalError())))
    }
    assertEquals(sseProducerThreads, 0)
  }

  test("an independent request on the response stream is rejected") {
    val server = new StreamingServer {
      def handle(request: McpRequest): McpResponse = success(request.id)
      def open(request: McpRequest): Either[Error, ServerStream] =
        Right(
          ServerStream.fromIterator(
            Iterator[McpMessage](
              McpRequest(
                method = Method("tools/call"),
                id = StringRequestId("bad")
              ),
              success(request.id)
            )
          )
        )
    }
    withServer(transport(server)) { uri =>
      val result = McpClient(clientFor(uri))
        .request(ServerDiscover.method, RequestParams(meta = meta))
      assertEquals(result, Left(ClientError.RemoteError(InternalError())))
    }
    assertEquals(sseProducerThreads, 0)
  }

  test("a mismatched response id is replaced by a correlated internal error") {
    val server = new StreamingServer {
      def handle(request: McpRequest): McpResponse = success(request.id)
      def open(request: McpRequest): Either[Error, ServerStream] =
        Right(
          ServerStream.fromIterator(
            Iterator[McpMessage](
              success(StringRequestId("not-the-request-id"))
            )
          )
        )
    }
    withServer(transport(server)) { uri =>
      val result = McpClient(clientFor(uri))
        .request(ServerDiscover.method, RequestParams(meta = meta))
      assertEquals(result, Left(ClientError.RemoteError(InternalError())))
    }
    assertEquals(sseProducerThreads, 0)
  }

  test("one encoder failure degrades to a correlated internal error") {
    val calls = new AtomicInteger(0)
    val flaky = new Encoder[JsonValue] {
      def encode(value: JsonValue): String =
        if (calls.incrementAndGet() == 1)
          throw new RuntimeException("encode-secret")
        else JsonCodec.JsonValueEncoder.encode(value)
    }
    val server = new StreamingServer {
      def handle(request: McpRequest): McpResponse = success(request.id)
      def open(request: McpRequest): Either[Error, ServerStream] =
        Right(ServerStream.single(success(request.id)))
    }
    withServer(transport(server, encoder = flaky)) { uri =>
      val result = McpClient(clientFor(uri))
        .request(ServerDiscover.method, RequestParams(meta = meta))
      assertEquals(result, Left(ClientError.RemoteError(InternalError())))
    }
    assertEquals(sseProducerThreads, 0)
  }

  test("repeated encoder failures close the stream abruptly") {
    val broken = new Encoder[JsonValue] {
      def encode(value: JsonValue): String =
        throw new RuntimeException("encode-secret")
    }
    val server = new StreamingServer {
      def handle(request: McpRequest): McpResponse = success(request.id)
      def open(request: McpRequest): Either[Error, ServerStream] =
        Right(ServerStream.single(success(request.id)))
    }
    withServer(transport(server, encoder = broken)) { uri =>
      val result = McpClient(clientFor(uri))
        .request(ServerDiscover.method, RequestParams(meta = meta))
      assertEquals(result, Left(ClientError.TransportFailure))
    }
    assertEquals(sseProducerThreads, 0)
  }

  test("client close while idle cancels the source on the next heartbeat") {
    val closed = new CountDownLatch(1)
    // The handshake needs a first item: emit the listen acknowledgement
    // (correlated on the request id), then block until cancelled.
    val server = new StreamingServer {
      def handle(request: McpRequest): McpResponse = success(request.id)
      def open(request: McpRequest): Either[Error, ServerStream] =
        Right(
          new ServerStream {
            private var acked = false
            def next(): Option[McpMessage] =
              if (!acked) {
                acked = true
                Some(
                  listenAck(
                    request.id,
                    SubscriptionFilter(toolsListChanged = Some(true))
                  )
                )
              } else {
                try Thread.sleep(Long.MaxValue)
                catch { case _: InterruptedException => () }
                None
              }
            def close(): Unit = closed.countDown()
          }
        )
    }
    withServer(
      transport(server, heartbeatInterval = Duration.ofMillis(20))
    ) { uri =>
      val client = McpClient(clientFor(uri))
      val opened = client.listen(
        SubscriptionsListenRequestParams(
          meta = meta,
          notifications = SubscriptionFilter(toolsListChanged = Some(true))
        )
      )
      assert(opened.isRight, s"listen failed: $opened")
      val stream = opened.toOption.get
      // Close without consuming: the heartbeat write fails on the aborted
      // connection and cleanup cancels the source.
      stream.close()
      assert(closed.await(10, TimeUnit.SECONDS), "source was not cancelled")
    }
    assertEquals(sseProducerThreads, 0)
  }

  test("a fatal source failure relays to the handler and cleans up once") {
    val source = new CountingStream(
      ServerStream.single(throw new LinkageError("close-fatal"))
    )
    val server = new ScriptedServer(source)
    withServer(transport(server)) { uri =>
      val result = McpClient(clientFor(uri))
        .request(ServerDiscover.method, RequestParams(meta = meta))
      assertEquals(result, Left(ClientError.TransportFailure))
    }
    assertEquals(source.closes.get(), 1)
    assertEquals(sseProducerThreads, 0)
  }

  test("preflight and request validation run before open") {
    val server =
      new ScriptedServer(ServerStream.single(success(StringRequestId("x"))))
    withServer(transport(server)) { uri =>
      // Bad origin: preflight rejects before the body is decoded.
      val forbidden = post(
        uri,
        discoverJson,
        baseHeaders("server/discover") :+ ("Origin" -> "http://evil.example")
      )
      assertEquals(forbidden.statusCode(), 403)
      // Missing protocol version: request headers reject inside decodeRequest.
      val missing = post(
        uri,
        discoverJson,
        Seq(
          "Content-Type" -> "application/json",
          "Accept" -> "application/json, text/event-stream",
          "Mcp-Method" -> "server/discover"
        )
      )
      assertEquals(missing.statusCode(), 400)
      assertEquals(server.opens.get(), 0)
    }
    assertEquals(sseProducerThreads, 0)
  }

  test("GET and DELETE stay 405 on a streaming endpoint") {
    val server =
      new ScriptedServer(ServerStream.single(success(StringRequestId("x"))))
    withServer(transport(server)) { uri =>
      val get = HttpRequest
        .newBuilder(URI.create(uri))
        .timeout(Duration.ofSeconds(10))
        .GET()
        .build()
      assertEquals(
        HttpClient.newHttpClient.send(get, BodyHandlers.ofString(Utf8))
          .statusCode(),
        405
      )
      val delete = HttpRequest
        .newBuilder(URI.create(uri))
        .timeout(Duration.ofSeconds(10))
        .DELETE()
        .build()
      assertEquals(
        HttpClient.newHttpClient.send(delete, BodyHandlers.ofString(Utf8))
          .statusCode(),
        405
      )
      assertEquals(server.opens.get(), 0)
    }
  }

  test("notifications on a streaming endpoint keep the plain JSON path") {
    val server =
      new ScriptedServer(ServerStream.single(success(StringRequestId("x"))))
    withServer(transport(server)) { uri =>
      val response = post(
        uri,
        """{"jsonrpc":"2.0","method":"notifications/vendor","params":{}}""",
        Seq(
          "Content-Type" -> "application/json",
          "Accept" -> "application/json, text/event-stream"
        )
      )
      // No notification callback configured: 400 InvalidRequest as JSON.
      assertEquals(response.statusCode(), 400)
      assertEquals(
        response.headers().firstValue("Content-Type").orElse(""),
        "application/json"
      )
      assertEquals(server.opens.get(), 0)
    }
  }

  test("an unknown method answers 404 JSON instead of committing SSE") {
    val hub = new SubscriptionHub(SubscriptionFilter())
    val server = SubscriptionServer(McpServer(info = info), hub)
    try {
      withServer(transport(server)) { uri =>
        val response = post(
          uri,
          """{"jsonrpc":"2.0","id":7,"method":"vendor/unknown","params":{"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28","io.modelcontextprotocol/clientCapabilities":{}}}}""",
          baseHeaders("vendor/unknown")
        )
        assertEquals(response.statusCode(), 404)
        assertEquals(
          response.headers().firstValue("Content-Type").orElse(""),
          "application/json"
        )
        assert(response.body().contains("-32601"), response.body())
        assert(response.body().contains("\"id\":7"), response.body())
      }
      assertEquals(sseProducerThreads, 0)
    } finally hub.close()
  }

  test("a first error response maps to its JSON status without SSE") {
    val server = scriptServer(request =>
      List(McpErrorResponse(InvalidRequestError(), Some(request.id)))
    )
    withServer(transport(server)) { uri =>
      val response = post(uri, discoverJson, baseHeaders("server/discover"))
      assertEquals(response.statusCode(), 400)
      assertEquals(
        response.headers().firstValue("Content-Type").orElse(""),
        "application/json"
      )
      assert(response.body().contains("-32600"), response.body())
      assert(response.body().contains("\"id\":1"), response.body())
    }
    assertEquals(sseProducerThreads, 0)
  }

  test("a first success response commits SSE") {
    val server = scriptServer(request => List(success(request.id)))
    withServer(transport(server)) { uri =>
      val response = post(uri, discoverJson, baseHeaders("server/discover"))
      assertEquals(response.statusCode(), 200)
      assertEquals(
        response.headers().firstValue("Content-Type").orElse(""),
        "text/event-stream; charset=utf-8"
      )
      assert(response.body().contains("\"id\":1"), response.body())
    }
    assertEquals(sseProducerThreads, 0)
  }

  test("an unsolicited producer interruption is relayed, not heartbeat-forever") {
    val source = new CountingStream(new ServerStream {
      def next(): Option[McpMessage] =
        throw new InterruptedException("manual-interrupt")
      def close(): Unit = ()
    })
    val server = new ScriptedServer(source)
    withServer(transport(server)) { uri =>
      val result = McpClient(clientFor(uri))
        .request(ServerDiscover.method, RequestParams(meta = meta))
      assertEquals(result, Left(ClientError.TransportFailure))
    }
    assertEquals(source.closes.get(), 1)
    assertEquals(sseProducerThreads, 0)
  }

  test("the source is never pulled again after the terminal response") {
    val pulls = new AtomicInteger(0)
    val server = new StreamingServer {
      def handle(request: McpRequest): McpResponse = success(request.id)
      def open(request: McpRequest): Either[Error, ServerStream] =
        Right(
          ServerStream.fromIterator(new Iterator[McpMessage] {
            def hasNext: Boolean = pulls.incrementAndGet() == 1
            def next(): McpMessage = success(request.id)
          })
        )
    }
    withServer(transport(server)) { uri =>
      val result = McpClient(clientFor(uri))
        .request(ServerDiscover.method, RequestParams(meta = meta))
      assert(result.isRight, s"request failed: $result")
    }
    assertEquals(pulls.get(), 1)
    assertEquals(sseProducerThreads, 0)
  }

  test("a progress notification with a foreign token is rejected") {
    val server = scriptServer(request =>
      List(progressEvent("other-token"), success(request.id))
    )
    withServer(transport(server)) { uri =>
      val result = McpClient(clientFor(uri))
        .request(
          ServerDiscover.method,
          RequestParams(
            meta = meta.copy(progressToken = Some(StringProgressToken("t-1")))
          )
        )
      assertEquals(result, Left(ClientError.RemoteError(InternalError())))
    }
    assertEquals(sseProducerThreads, 0)
  }

  test("a logging notification without opt-in is rejected") {
    val server = scriptServer(request =>
      List(loggingEvent, success(request.id))
    )
    withServer(transport(server)) { uri =>
      val result = McpClient(clientFor(uri))
        .request(ServerDiscover.method, RequestParams(meta = meta))
      assertEquals(result, Left(ClientError.RemoteError(InternalError())))
    }
    assertEquals(sseProducerThreads, 0)
  }

  test("a notification with a foreign subscription id is rejected") {
    val server = scriptServer(request =>
      List(
        vendorEvent(subscriptionId = Some(StringRequestId("other-sub"))),
        success(request.id)
      )
    )
    withServer(transport(server)) { uri =>
      val result = McpClient(clientFor(uri))
        .request(ServerDiscover.method, RequestParams(meta = meta))
      assertEquals(result, Left(ClientError.RemoteError(InternalError())))
    }
    assertEquals(sseProducerThreads, 0)
  }

  test("an unrequested list-change notification is rejected") {
    val server = scriptServer(request =>
      List(
        McpNotification(Method("notifications/tools/list_changed")),
        success(request.id)
      )
    )
    withServer(transport(server)) { uri =>
      val result = McpClient(clientFor(uri))
        .request(ServerDiscover.method, RequestParams(meta = meta))
      assertEquals(result, Left(ClientError.RemoteError(InternalError())))
    }
    assertEquals(sseProducerThreads, 0)
  }

  test("a custom notification still passes through validation") {
    val seen = new AtomicReference[List[McpNotification]](Nil)
    val server = scriptServer(request =>
      List(vendorEvent(), success(request.id))
    )
    withServer(transport(server)) { uri =>
      val result = McpClient(clientFor(uri, seen = Some(seen)))
        .request(ServerDiscover.method, RequestParams(meta = meta))
      assert(result.isRight, s"request failed: $result")
      assertEquals(
        seen.get().map(_.method.value),
        List("vendor/custom")
      )
    }
    assertEquals(sseProducerThreads, 0)
  }

  test("a listen stream without a valid acknowledgement is rejected") {
    val server = scriptServer(request =>
      List(vendorEvent(), success(request.id))
    )
    withServer(transport(server)) { uri =>
      val opened = McpClient(clientFor(uri)).listen(
        SubscriptionsListenRequestParams(
          meta = meta,
          notifications = SubscriptionFilter(toolsListChanged = Some(true))
        )
      )
      // Server-side validation rejected before SSE: the JSON internal
      // error arrives as the stream's terminal error response.
      assert(opened.isRight, s"listen open failed: $opened")
      opened.toOption.get.next() match {
        case Right(Some(response: McpErrorResponse)) =>
          assertEquals(response.error, InternalError())
        case other => fail(s"expected internal error response, got $other")
      }
      opened.toOption.get.close()
    }
    assertEquals(sseProducerThreads, 0)
  }

  test("a listen acknowledgement granting an unrequested uri is rejected") {
    val server = scriptServer(request =>
      List(
        listenAck(
          request.id,
          SubscriptionFilter(
            resourceSubscriptions = Some(List("file:///unrequested"))
          )
        ),
        success(request.id)
      )
    )
    withServer(transport(server)) { uri =>
      val opened = McpClient(clientFor(uri)).listen(
        SubscriptionsListenRequestParams(
          meta = meta,
          notifications = SubscriptionFilter(
            resourceSubscriptions = Some(List("file:///a"))
          )
        )
      )
      assert(opened.isRight, s"listen open failed: $opened")
      opened.toOption.get.next() match {
        case Right(Some(response: McpErrorResponse)) =>
          assertEquals(response.error, InternalError())
        case other => fail(s"expected internal error response, got $other")
      }
      opened.toOption.get.close()
    }
    assertEquals(sseProducerThreads, 0)
  }

  test("a header-send failure cleans source and producer exactly once") {
    // An unstarted server only supplies a real HttpContext for the stub.
    val holder = HttpServer.create()
    val context = holder.createContext("/mcp")
    try {
      val source = new CountingStream(
        ServerStream.single(success(NumberRequestId(1)))
      )
      val handler = transport(new ScriptedServer(source))
      val exchange = new StubExchange(
        context,
        discoverJson.getBytes(Utf8),
        stubRequestHeaders("server/discover"),
        failSendHeaders = true
      )
      var observed: Throwable = null
      try handler.handle(exchange)
      catch { case thrown: Throwable => observed = thrown }
      assert(observed.isInstanceOf[IOException])
      // The first item was a valid success: the SSE headers were being
      // committed when sendResponseHeaders failed (not a JSON error path).
      assertEquals(
        exchange.getResponseHeaders.getFirst("Content-Type"),
        "text/event-stream; charset=utf-8"
      )
      assertEquals(source.closes.get(), 1)
      assert(exchange.closed.get())
      assertEquals(sseProducerThreads, 0)
    } finally holder.stop(0)
  }

  test("a failure marker is still delivered when the producer was interrupted") {
    // An unstarted server only supplies a real HttpContext for the stub.
    val holder = HttpServer.create()
    val context = holder.createContext("/mcp")
    try {
      val manual = new InterruptedException("manual-interrupt")
      // The source sets the producer's interrupt flag itself and throws:
      // queue.put then throws InterruptedException and clears the flag, so
      // the failure marker must be retried rather than dropped.
      val source = new CountingStream(new ServerStream {
        def next(): Option[McpMessage] = {
          Thread.currentThread().interrupt()
          throw manual
        }
        def close(): Unit = ()
      })
      val handler = transport(new ScriptedServer(source))
      val exchange = new StubExchange(
        context,
        discoverJson.getBytes(Utf8),
        stubRequestHeaders("server/discover"),
        failSendHeaders = false
      )
      val observed = new AtomicReference[Throwable]()
      val finished = new CountDownLatch(1)
      val runner = new Thread(new Runnable {
        def run(): Unit =
          try handler.handle(exchange)
          catch { case thrown: Throwable => observed.set(thrown) }
          finally finished.countDown()
      })
      runner.setDaemon(true)
      runner.start()
      assert(
        finished.await(10, TimeUnit.SECONDS),
        "handler did not finish"
      )
      assertEquals(observed.get(), manual)
      assertEquals(source.closes.get(), 1)
      assert(exchange.closed.get())
      assertEquals(sseProducerThreads, 0)
    } finally holder.stop(0)
  }
}
