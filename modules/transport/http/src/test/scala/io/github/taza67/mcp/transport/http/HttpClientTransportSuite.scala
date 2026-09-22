package io.github.taza67.mcp.transport.http

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.Authenticator
import java.net.CookieHandler
import java.net.InetSocketAddress
import java.net.ProxySelector
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpHeaders
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.WebSocket
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.Optional
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLParameters

import scala.jdk.CollectionConverters._

import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer

import io.github.taza67.mcp.client.ClientError
import io.github.taza67.mcp.client.McpClient
import io.github.taza67.mcp.codec.Encoder
import io.github.taza67.mcp.codec.WireLimits
import io.github.taza67.mcp.codec.circe.JsonCodec
import io.github.taza67.mcp.protocol.json.JsonValue
import io.github.taza67.mcp.protocol.jsonrpc.ApplicationError
import io.github.taza67.mcp.protocol.jsonrpc.Method
import io.github.taza67.mcp.protocol.jsonrpc.NumberRequestId
import io.github.taza67.mcp.protocol.mcp.ClientCapabilities
import io.github.taza67.mcp.protocol.mcp.Completed
import io.github.taza67.mcp.protocol.mcp.Implementation
import io.github.taza67.mcp.protocol.mcp.McpMessage
import io.github.taza67.mcp.protocol.mcp.McpNotification
import io.github.taza67.mcp.protocol.mcp.McpProtocolVersion20260728
import io.github.taza67.mcp.protocol.mcp.McpRequest
import io.github.taza67.mcp.protocol.mcp.McpSuccessResponse
import io.github.taza67.mcp.protocol.mcp.RequestMeta
import io.github.taza67.mcp.protocol.mcp.RequestParams
import io.github.taza67.mcp.protocol.mcp.StringProgressToken
import io.github.taza67.mcp.protocol.mcp.TextContent
import io.github.taza67.mcp.protocol.mcp.discover.ServerDiscover
import io.github.taza67.mcp.protocol.mcp.subscriptions.SubscriptionFilter
import io.github.taza67.mcp.protocol.mcp.subscriptions.SubscriptionsListenRequestParams
import io.github.taza67.mcp.protocol.mcp.tools.CallToolRequestParams
import io.github.taza67.mcp.protocol.mcp.tools.Tool
import io.github.taza67.mcp.protocol.mcp.tools.ToolInputSchema
import io.github.taza67.mcp.server.McpServer
import io.github.taza67.mcp.server.ServerTool
import io.github.taza67.mcp.server.ToolCall
import munit.FunSuite



class HttpClientTransportSuite extends FunSuite {

  private val Utf8 = StandardCharsets.UTF_8

  private val meta = RequestMeta(
    protocolVersion = McpProtocolVersion20260728,
    clientCapabilities = ClientCapabilities()
  )

  private val pingTool = ServerTool(
    definition = Tool(name = "ping", inputSchema = ToolInputSchema()),
    run = (_: ToolCall) => Right(
      io.github.taza67.mcp.protocol.mcp.tools.CallToolResult(
        content = List(TextContent("pong"))
      )
    )
  )

  private val mcpServer = McpServer(
    info = Implementation(name = "ex", version = "1"),
    tools = Seq(pingTool)
  )

  private def mcpHandler: HttpHandler =
    HttpTransport(
      HttpEndpoint(mcpServer),
      JsonCodec.JsonValueDecoder,
      JsonCodec.JsonValueEncoder
    )

  private def transportFor(
      uri: String,
      additionalHeaders: Map[String, String] = Map.empty,
      timeout: Duration = Duration.ofSeconds(5),
      maxMessageSize: Int = WireLimits.DefaultMaxMessageSize,
      maxNestingDepth: Int = WireLimits.DefaultMaxNestingDepth,
      onNotification: McpNotification => Unit = _ => (),
      httpClient: HttpClient = HttpClient
        .newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER)
        .connectTimeout(Duration.ofSeconds(5))
        .build()
  ): HttpClientTransport =
    HttpClientTransport(
      endpoint = URI.create(uri),
      decoder = JsonCodec.JsonValueDecoder,
      encoder = JsonCodec.JsonValueEncoder,
      httpClient = httpClient,
      additionalHeaders = additionalHeaders,
      timeout = timeout,
      maxMessageSize = maxMessageSize,
      maxNestingDepth = maxNestingDepth,
      onNotification = onNotification
    )

  private def withServer[A](handler: HttpHandler)(f: String => A): A = {
    val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/mcp", handler)
    val executor = Executors.newSingleThreadExecutor()
    server.setExecutor(executor)
    server.start()
    try f(s"http://127.0.0.1:${server.getAddress.getPort}/mcp")
    finally {
      server.stop(0)
      executor.shutdown()
    }
  }

  private def canned(
      status: Int,
      body: String,
      contentType: String = "application/json",
      extraHeaders: Map[String, String] = Map.empty
  ): HttpHandler =
    exchange => {
      exchange.getRequestBody.readAllBytes()
      val bytes = body.getBytes(Utf8)
      exchange.getResponseHeaders.set("Content-Type", contentType)
      extraHeaders.foreach { case (k, v) =>
        exchange.getResponseHeaders.set(k, v)
      }
      exchange.sendResponseHeaders(status, bytes.length.toLong)
      exchange.getResponseBody.write(bytes)
      exchange.getResponseBody.flush()
      exchange.close()
    }

  private def sse(events: String*): HttpHandler =
    exchange => {
      exchange.getRequestBody.readAllBytes()
      val body = events.map(Sse.encode).mkString.getBytes(Utf8)
      exchange.getResponseHeaders.set("Content-Type", "text/event-stream")
      exchange.sendResponseHeaders(200, body.length.toLong)
      exchange.getResponseBody.write(body)
      exchange.getResponseBody.flush()
      exchange.close()
    }

  private def successJson(id: Int = 1, result: String = """{"resultType":"complete"}""") =
    s"""{"jsonrpc":"2.0","id":$id,"result":$result}"""

  /** Counts how many times the response body is closed. */
  private final class CountingStream(text: String) extends InputStream {
    private val inner = new ByteArrayInputStream(text.getBytes(Utf8))
    val closes = new AtomicInteger(0)

    def read(): Int = inner.read()

    override def close(): Unit = {
      closes.incrementAndGet()
      inner.close()
    }
  }

  private def headersOf(contentType: String): HttpHeaders =
    HttpHeaders.of(
      Map("content-type" -> java.util.List.of(contentType)).asJava,
      (_: String, _: String) => true
    )

  private val cannedHeaders: HttpHeaders = headersOf("application/json")

  private def cannedResponse(
      status: => Int,
      responseHeaders: => HttpHeaders,
      payload: InputStream
  ): HttpResponse[InputStream] =
    new HttpResponse[InputStream] {
      def statusCode(): Int = status
      def request(): HttpRequest =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1/")).build()
      def previousResponse(): Optional[HttpResponse[InputStream]] =
        Optional.empty()
      def headers(): HttpHeaders = responseHeaders
      def body(): InputStream = payload
      def sslSession(): Optional[javax.net.ssl.SSLSession] = Optional.empty()
      def uri(): URI = URI.create("http://127.0.0.1/")
      def version(): HttpClient.Version = HttpClient.Version.HTTP_1_1
    }

  private def cannedResponse(
      status: Int,
      contentType: String,
      payload: InputStream
  ): HttpResponse[InputStream] =
    cannedResponse(status, headersOf(contentType), payload)

  /** A stub `HttpClient` whose `send` evaluates `response`. */
  private def stubClient(
      response: => HttpResponse[InputStream]
  ): HttpClient =
    new HttpClient {
      def cookieHandler() = Optional.empty[CookieHandler]
      def connectTimeout() = Optional.empty[Duration]
      def followRedirects() = HttpClient.Redirect.NEVER
      def proxy() = Optional.empty[ProxySelector]
      def sslContext() = SSLContext.getDefault
      def sslParameters() = new SSLParameters
      def authenticator() = Optional.empty[Authenticator]
      def version() = HttpClient.Version.HTTP_1_1
      def executor() = Optional.empty[Executor]
      def send[A](
          request: HttpRequest,
          handler: HttpResponse.BodyHandler[A]
      ): HttpResponse[A] = response.asInstanceOf[HttpResponse[A]]
      def sendAsync[A](
          request: HttpRequest,
          handler: HttpResponse.BodyHandler[A]
      ): CompletableFuture[HttpResponse[A]] =
        throw new UnsupportedOperationException
      def sendAsync[A](
          request: HttpRequest,
          handler: HttpResponse.BodyHandler[A],
          pushPromise: HttpResponse.PushPromiseHandler[A]
      ): CompletableFuture[HttpResponse[A]] =
        throw new UnsupportedOperationException
      override def newWebSocketBuilder(): WebSocket.Builder =
        throw new UnsupportedOperationException
    }

  test("typed discover, tools list, and tools call round-trip over HTTP") {
    withServer(mcpHandler) { uri =>
      val client = McpClient(transportFor(uri))

      val discovered = client.discover(meta)
      assert(discovered.isRight, s"discover failed: $discovered")

      val listed = client.listTools(meta)
      assertEquals(listed.map(_.tools.map(_.name)), Right(List("ping")))

      val called =
        client.callTool(CallToolRequestParams(meta = meta, name = "ping"))
      called match {
        case Right(Completed(result)) =>
          assertEquals(result.content, List(TextContent("pong")))
        case other => fail(s"unexpected callTool outcome: $other")
      }
    }
  }

  test("the POST carries literal MCP headers, body, and safe extras") {
    val recordedHeaders = new AtomicReference[Map[String, List[String]]]()
    val recordedBody = new AtomicReference[String]()
    val handler: HttpHandler = exchange => {
      recordedHeaders.set(
        exchange.getRequestHeaders.asScala.toMap.map { case (k, vs) =>
          k -> vs.asScala.toList
        }
      )
      recordedBody.set(
        new String(exchange.getRequestBody.readAllBytes(), Utf8)
      )
      canned(200, successJson()).handle(exchange)
    }
    withServer(handler) { uri =>
      val transport = transportFor(
        uri,
        additionalHeaders = Map("Authorization" -> "Bearer t-1", "X-Custom" -> "yes")
      )
      val result = McpClient(transport)
        .request(ServerDiscover.method, RequestParams(meta = meta))
      assert(result.isRight, s"request failed: $result")

      val headers = recordedHeaders.get()
      assertEquals(
        headers.collectFirst { case (k, v) if k.equalsIgnoreCase("content-type") => v },
        Some(List("application/json"))
      )
      assertEquals(
        headers.collectFirst { case (k, v) if k.equalsIgnoreCase("accept") => v },
        Some(List("application/json, text/event-stream"))
      )
      assertEquals(
        headers.collectFirst {
          case (k, v) if k.equalsIgnoreCase("mcp-protocol-version") => v
        },
        Some(List("2026-07-28"))
      )
      assertEquals(
        headers.collectFirst { case (k, v) if k.equalsIgnoreCase("mcp-method") => v },
        Some(List("server/discover"))
      )
      assertEquals(
        headers.collectFirst { case (k, v) if k.equalsIgnoreCase("authorization") => v },
        Some(List("Bearer t-1"))
      )
      assertEquals(
        headers.collectFirst { case (k, v) if k.equalsIgnoreCase("x-custom") => v },
        Some(List("yes"))
      )
      val body = recordedBody.get()
      assert(body.contains("\"method\":\"server/discover\""), body)
      assert(body.contains("\"id\":1"), body)
      assert(body.contains("\"_meta\""), body)
      assert(!body.contains("t-1"), "credential leaked into the JSON body")
    }
  }

  test("a JSON 400 with a correlated error surfaces as RemoteError") {
    val body =
      """{"jsonrpc":"2.0","id":1,"error":{"code":-32010,"message":"caller reason"}}"""
    withServer(canned(400, body)) { uri =>
      val client = McpClient(transportFor(uri))
      assertEquals(
        client.discover(meta),
        Left(ClientError.RemoteError(ApplicationError(-32010, "caller reason")))
      )
    }
  }

  test("mismatched and uncorrelated response ids fail correlation") {
    withServer(canned(200, successJson(id = 2))) { uri =>
      assertEquals(
        McpClient(transportFor(uri)).discover(meta),
        Left(ClientError.ResponseIdMismatch)
      )
    }
    val idlessError = """{"jsonrpc":"2.0","error":{"code":-32603,"message":"x"}}"""
    withServer(canned(500, idlessError)) { uri =>
      assertEquals(
        McpClient(transportFor(uri)).discover(meta),
        Left(ClientError.UncorrelatedResponse)
      )
    }
  }

  test("redirects are never followed and the target is never hit") {
    val hits = new AtomicInteger(0)
    val counter: HttpHandler = exchange => {
      hits.incrementAndGet()
      canned(200, successJson()).handle(exchange)
    }
    val target = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    target.createContext("/mcp", counter)
    val executor = Executors.newSingleThreadExecutor()
    target.setExecutor(executor)
    target.start()
    try {
      val targetUri = s"http://127.0.0.1:${target.getAddress.getPort}/mcp"
      val redirect: HttpHandler = exchange => {
        exchange.getRequestBody.readAllBytes()
        exchange.getResponseHeaders.set("Location", targetUri)
        exchange.sendResponseHeaders(302, -1)
        exchange.close()
      }
      withServer(redirect) { uri =>
        assertEquals(
          McpClient(transportFor(uri)).discover(meta),
          Left(ClientError.TransportFailure)
        )
        assertEquals(hits.get(), 0)
      }
    } finally {
      target.stop(0)
      executor.shutdown()
    }
  }

  test("wrong content types, statuses, and body shapes fail safely") {
    withServer(canned(200, successJson(), contentType = "text/plain")) { uri =>
      assertEquals(
        McpClient(transportFor(uri)).discover(meta),
        Left(ClientError.TransportFailure)
      )
    }
    withServer(canned(500, successJson())) { uri =>
      // A non-2xx must carry an error response, not a success.
      assertEquals(
        McpClient(transportFor(uri)).discover(meta),
        Left(ClientError.TransportFailure)
      )
    }
    withServer(canned(400, "not json")) { uri =>
      assertEquals(
        McpClient(transportFor(uri)).discover(meta),
        Left(ClientError.TransportFailure)
      )
    }
    withServer(canned(202, "")) { uri =>
      assertEquals(
        McpClient(transportFor(uri)).discover(meta),
        Left(ClientError.TransportFailure)
      )
    }
  }

  test("invalid UTF-8, trailing JSON, deep nesting, and oversize fail") {
    val rawBad: HttpHandler = exchange => {
      exchange.getRequestBody.readAllBytes()
      val bytes = Array[Byte](0x7b, 0xff.toByte, 0x7d)
      exchange.getResponseHeaders.set("Content-Type", "application/json")
      exchange.sendResponseHeaders(200, bytes.length.toLong)
      exchange.getResponseBody.write(bytes)
      exchange.getResponseBody.flush()
      exchange.close()
    }
    withServer(rawBad) { uri =>
      assertEquals(
        McpClient(transportFor(uri)).discover(meta),
        Left(ClientError.TransportFailure)
      )
    }
    withServer(canned(200, successJson() + " {}")) { uri =>
      assertEquals(
        McpClient(transportFor(uri)).discover(meta),
        Left(ClientError.TransportFailure)
      )
    }
    val deep = successJson(result = """{"a":{"b":{"c":{"d":{"e":{"f":{}}}}}}""")
    withServer(canned(200, deep)) { uri =>
      assertEquals(
        McpClient(transportFor(uri, maxNestingDepth = 4)).discover(meta),
        Left(ClientError.TransportFailure)
      )
    }
    withServer(canned(200, successJson())) { uri =>
      assertEquals(
        McpClient(transportFor(uri, maxMessageSize = 32)).discover(meta),
        Left(ClientError.TransportFailure)
      )
    }
  }

  test("an SSE stream delivers notifications and the terminal response") {
    val tokenMeta = meta.copy(progressToken = Some(StringProgressToken("t-1")))
    val events = List(
      """{"jsonrpc":"2.0","method":"notifications/progress","params":{"progressToken":"t-1","progress":0.5}}""",
      """{"jsonrpc":"2.0","method":"vendor/custom","params":{"x":1}}""",
      successJson()
    )
    val seen = new AtomicReference[List[McpNotification]](Nil)
    withServer(sse(events: _*)) { uri =>
      val transport = transportFor(
        uri,
        onNotification = n => seen.set(seen.get() :+ n)
      )
      val result = McpClient(transport)
        .request(ServerDiscover.method, RequestParams(meta = tokenMeta))
      assert(result.isRight, s"request failed: $result")
      assertEquals(
        seen.get().map(_.method.value),
        List("notifications/progress", "vendor/custom")
      )
    }
  }

  test("subscription notifications are rejected on ordinary streams") {
    List(
      """{"jsonrpc":"2.0","method":"notifications/tools/list_changed","params":{"_meta":{"io.modelcontextprotocol/subscriptionId":1}}}""",
      """{"jsonrpc":"2.0","method":"notifications/resources/updated","params":{"uri":"file:///a","_meta":{"io.modelcontextprotocol/subscriptionId":1}}}""",
      """{"jsonrpc":"2.0","method":"notifications/subscriptions/acknowledged","params":{"notifications":{},"_meta":{"io.modelcontextprotocol/subscriptionId":1}}}"""
    ).foreach { notification =>
      withServer(sse(notification, successJson())) { uri =>
        assertEquals(
          McpClient(transportFor(uri)).discover(meta),
          Left(ClientError.TransportFailure)
        )
      }
    }
  }

  test("listen pulls the acknowledgement then a subscribed resource event") {
    val events = List(
      """{"jsonrpc":"2.0","method":"notifications/subscriptions/acknowledged","params":{"notifications":{"resourcesListChanged":true,"resourceSubscriptions":["file:///a"]},"_meta":{"io.modelcontextprotocol/subscriptionId":1}}}""",
      """{"jsonrpc":"2.0","method":"notifications/resources/updated","params":{"uri":"file:///a","_meta":{"io.modelcontextprotocol/subscriptionId":1}}}""",
      """{"jsonrpc":"2.0","id":1,"result":{"resultType":"complete","_meta":{"io.modelcontextprotocol/subscriptionId":1}}}"""
    )
    withServer(sse(events: _*)) { uri =>
      val client = McpClient(transportFor(uri))
      val stream = client.listen(
        SubscriptionsListenRequestParams(
          meta = meta,
          notifications = SubscriptionFilter(
            resourcesListChanged = Some(true),
            resourceSubscriptions = Some(List("file:///a"))
          )
        )
      )
      assert(stream.isRight, s"listen failed: $stream")
      val s = stream.toOption.get
      val ack = s.next()
      assertEquals(
        ack.map(_.map(_.asInstanceOf[McpNotification].method.value)),
        Right(Some("notifications/subscriptions/acknowledged"))
      )
      val updated = s.next()
      assertEquals(
        updated.map(_.map(_.asInstanceOf[McpNotification].method.value)),
        Right(Some("notifications/resources/updated"))
      )
      assert(s.next().exists(_.exists(_.isInstanceOf[McpSuccessResponse])))
      assertEquals(s.next(), Right(None))
      s.close()
    }
  }

  test("an independent request inside SSE is rejected") {
    val events = List(
      """{"jsonrpc":"2.0","id":9,"method":"tools/call","params":{}}""",
      successJson()
    )
    withServer(sse(events: _*)) { uri =>
      assertEquals(
        McpClient(transportFor(uri)).discover(meta),
        Left(ClientError.TransportFailure)
      )
    }
  }

  test("a truncated SSE body fails instead of hanging") {
    withServer(sse("""{"jsonrpc":"2.0","method":"vendor/custom","params":{}}""")) { uri =>
      // The body ends after one notification with no terminal response.
      assertEquals(
        McpClient(transportFor(uri)).discover(meta),
        Left(ClientError.TransportFailure)
      )
    }
  }

  test("a throwing notification callback fails statically") {
    val events = List(
      """{"jsonrpc":"2.0","method":"vendor/custom","params":{}}""",
      successJson()
    )
    withServer(sse(events: _*)) { uri =>
      val transport = transportFor(
        uri,
        onNotification = _ => throw new RuntimeException("callback-secret")
      )
      assertEquals(
        McpClient(transport).discover(meta),
        Left(ClientError.TransportFailure)
      )
    }
  }

  test("closing the stream unblocks an in-flight next()") {
    val serving = new CountDownLatch(1)
    val handler: HttpHandler = exchange => {
      try {
        exchange.getRequestBody.readAllBytes()
        val ack = Sse.encode(
          """{"jsonrpc":"2.0","method":"notifications/subscriptions/acknowledged","params":{"notifications":{"toolsListChanged":true},"_meta":{"io.modelcontextprotocol/subscriptionId":1}}}"""
        ).getBytes(Utf8)
        exchange.getResponseHeaders.set("Content-Type", "text/event-stream")
        exchange.sendResponseHeaders(200, 0) // chunked, stays open
        exchange.getResponseBody.write(ack)
        exchange.getResponseBody.flush()
        serving.await(30, TimeUnit.SECONDS)
      } finally exchange.close()
    }
    withServer(handler) { uri =>
      try {
        val client = McpClient(transportFor(uri))
        val stream = client
          .listen(
            SubscriptionsListenRequestParams(
              meta = meta,
              notifications = SubscriptionFilter(toolsListChanged = Some(true))
            )
          )
          .toOption
          .get
        val ack = stream.next()
        assertEquals(
          ack.map(_.map(_.asInstanceOf[McpNotification].method.value)),
          Right(Some("notifications/subscriptions/acknowledged"))
        )

        val outcome =
          new AtomicReference[Either[ClientError, Option[McpMessage]]]()
        val entered = new CountDownLatch(1)
        val finished = new CountDownLatch(1)
        val consumer = new Thread(() => {
          entered.countDown()
          outcome.set(stream.next())
          finished.countDown()
        })
        consumer.start()
        assert(entered.await(10, TimeUnit.SECONDS), "consumer never started")
        stream.close()
        assert(finished.await(10, TimeUnit.SECONDS), "next() stayed blocked")
        assertEquals(outcome.get(), Right(None))
      } finally serving.countDown()
    }
  }

  test("a hung JSON body fails at the exchange deadline") {
    val release = new CountDownLatch(1)
    val handler: HttpHandler = exchange => {
      try {
        exchange.getRequestBody.readAllBytes()
        exchange.getResponseHeaders.set("Content-Type", "application/json")
        exchange.sendResponseHeaders(200, 0) // chunked; body never arrives
        exchange.getResponseBody.flush()
        release.await(30, TimeUnit.SECONDS)
      } finally exchange.close()
    }
    withServer(handler) { uri =>
      try {
        val transport = transportFor(uri, timeout = Duration.ofMillis(400))
        val result = McpClient(transport).discover(meta)
        assertEquals(result, Left(ClientError.TransportFailure))
      } finally release.countDown()
    }
  }

  test("a hung SSE body fails at the exchange deadline") {
    val release = new CountDownLatch(1)
    val handler: HttpHandler = exchange => {
      try {
        exchange.getRequestBody.readAllBytes()
        exchange.getResponseHeaders.set("Content-Type", "text/event-stream")
        exchange.sendResponseHeaders(200, 0) // chunked; no events ever arrive
        exchange.getResponseBody.flush()
        release.await(30, TimeUnit.SECONDS)
      } finally exchange.close()
    }
    withServer(handler) { uri =>
      try {
        val transport = transportFor(uri, timeout = Duration.ofMillis(400))
        val result = McpClient(transport).discover(meta)
        assertEquals(result, Left(ClientError.TransportFailure))
      } finally release.countDown()
    }
  }

  test("interrupting an exchange propagates InterruptedException") {
    val received = new CountDownLatch(1)
    val release = new CountDownLatch(1)
    val handler: HttpHandler = exchange => {
      try {
        exchange.getRequestBody.readAllBytes()
        received.countDown()
        release.await(30, TimeUnit.SECONDS)
      } finally exchange.close()
    }
    withServer(handler) { uri =>
      val transport = transportFor(uri, timeout = Duration.ofSeconds(20))
      val outcome = new AtomicReference[Option[Throwable]](None)
      val finished = new CountDownLatch(1)
      val caller = new Thread(() => {
        try {
          McpClient(transport).discover(meta)
          ()
        } catch {
          case t: Throwable => outcome.set(Some(t))
        }
        finished.countDown()
      })
      try {
        caller.start()
        assert(received.await(10, TimeUnit.SECONDS), "server never saw request")
        caller.interrupt()
        assert(finished.await(10, TimeUnit.SECONDS), "exchange stayed blocked")
        assert(outcome.get().exists(_.isInstanceOf[InterruptedException]),
          s"expected InterruptedException, got ${outcome.get()}")
      } finally release.countDown()
    }
  }

  test("configuration rejects unsafe endpoints, clients, headers, limits") {
    val dec = JsonCodec.JsonValueDecoder
    val enc = JsonCodec.JsonValueEncoder
    val good = URI.create("http://127.0.0.1:1/mcp")
    def build(
        endpoint: URI = good,
        httpClient: HttpClient = HttpClient
          .newBuilder()
          .followRedirects(HttpClient.Redirect.NEVER)
          .build(),
        additionalHeaders: Map[String, String] = Map.empty,
        timeout: Duration = Duration.ofSeconds(1),
        maxMessageSize: Int = 1024,
        maxNestingDepth: Int = 8
    ) = HttpClientTransport(
      endpoint, dec, enc, httpClient, additionalHeaders, timeout,
      maxMessageSize, maxNestingDepth
    )

    intercept[IllegalArgumentException](build(endpoint = URI.create("ftp://h")))
    intercept[IllegalArgumentException](build(endpoint = URI.create("/mcp")))
    intercept[IllegalArgumentException](
      build(endpoint = URI.create("http://u:p@h/"))
    )
    intercept[IllegalArgumentException](
      build(endpoint = URI.create("http://h/#f"))
    )
    intercept[IllegalArgumentException](
      build(httpClient = HttpClient
        .newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build())
    )
    intercept[IllegalArgumentException](
      build(timeout = Duration.ofNanos(0))
    )
    intercept[IllegalArgumentException](
      build(timeout = Duration.ofDays(1000000))
    )
    intercept[IllegalArgumentException](build(maxMessageSize = 0))
    intercept[IllegalArgumentException](build(maxNestingDepth = 0))

    List(
      Map("Accept" -> "x"),
      Map("MCP-Session" -> "s"),
      Map("mcp-param-x" -> "y"),
      Map("Content-Length" -> "5"),
      Map("Connection" -> "keep-alive"),
      Map("Content-Encoding" -> "gzip"),
      Map("Expect" -> "100-continue"),
      Map("Upgrade" -> "websocket"),
      Map("X-Bad" -> "a\r\nb"),
      Map("X-Bad" -> " padded "),
      Map("X-Bad" -> "caf\u00e9"),
      Map("Bad Name" -> "v")
    ).foreach(headers =>
      intercept[IllegalArgumentException](build(additionalHeaders = headers))
    )
    // Case-insensitive duplicates are rejected even when the name is legal.
    intercept[IllegalArgumentException](
      build(additionalHeaders =
        Map("Authorization" -> "a", "authorization" -> "b")
      )
    )
    // Authorization and ordinary token headers remain legal.
    build(additionalHeaders = Map("Authorization" -> "Bearer x", "X-Ok" -> "1"))
  }

  test("every transport shares the single deadline scheduler") {
    withServer(canned(200, successJson())) { uri =>
      (1 to 8).foreach { _ =>
        val result = McpClient(transportFor(uri))
          .request(ServerDiscover.method, RequestParams(meta = meta))
        assert(result.isRight, s"exchange failed: $result")
      }
      assertEquals(HttpTimers.createdThreads.get(), 1)
    }
  }

  test("the response body closes exactly once on each rejection path") {
    def rejectedOnce(
        status: Int,
        contentType: String,
        body: String,
        timeout: Duration = Duration.ofSeconds(5)
    ): Unit = {
      val stream = new CountingStream(body)
      val transport = transportFor(
        "http://127.0.0.1:1/mcp",
        httpClient = stubClient(cannedResponse(status, contentType, stream)),
        timeout = timeout
      )
      assertEquals(
        McpClient(transport).discover(meta),
        Left(ClientError.TransportFailure)
      )
      assertEquals(stream.closes.get(), 1)
    }
    rejectedOnce(302, "application/json", successJson())
    rejectedOnce(200, "text/plain", successJson())
    rejectedOnce(200, "application/json", "not json")
    rejectedOnce(500, "application/json", successJson())
    // A buffered response arriving after the deadline is rejected too.
    rejectedOnce(
      200,
      "application/json",
      successJson(),
      timeout = Duration.ofNanos(1)
    )
  }

  test("a fatal decode failure closes the body and propagates") {
    val stream = new CountingStream(successJson())
    val transport = HttpClientTransport(
      endpoint = URI.create("http://127.0.0.1:1/mcp"),
      decoder = (_: String) => throw new LinkageError("fatal"),
      encoder = JsonCodec.JsonValueEncoder,
      httpClient = stubClient(
        cannedResponse(200, "application/json", stream)
      )
    )
    var sawFatal = false
    try {
      McpClient(transport).discover(meta)
      ()
    } catch { case _: LinkageError => sawFatal = true }
    assert(sawFatal, "expected LinkageError to propagate")
    assertEquals(stream.closes.get(), 1)
  }

  test("a fatal decode failure is preserved when the body close fails") {
    val closes = new AtomicInteger(0)
    val body = new InputStream {
      private val inner =
        new ByteArrayInputStream(successJson().getBytes(Utf8))
      def read() = inner.read()
      override def close() = {
        closes.incrementAndGet()
        throw new IOException("close-secret")
      }
    }
    val transport = HttpClientTransport(
      endpoint = URI.create("http://127.0.0.1:1/mcp"),
      decoder = (_: String) => throw new LinkageError("fatal"),
      encoder = JsonCodec.JsonValueEncoder,
      httpClient = stubClient(
        cannedResponse(200, "application/json", body)
      )
    )
    var sawFatal = false
    try {
      McpClient(transport).discover(meta)
      ()
    } catch { case _: LinkageError => sawFatal = true }
    assert(sawFatal, "expected LinkageError to propagate")
    assertEquals(closes.get(), 1)
  }

  test("a failing JSON body close fails a successful decode once") {
    List[Throwable](
      new IOException("close-secret"),
      new RuntimeException("close-secret")
    ).foreach { failure =>
      val closes = new AtomicInteger(0)
      val body = new InputStream {
        private val inner =
          new ByteArrayInputStream(successJson().getBytes(Utf8))
        def read() = inner.read()
        override def close() = {
          closes.incrementAndGet()
          throw failure
        }
      }
      val transport = transportFor(
        "http://127.0.0.1:1/mcp",
        httpClient = stubClient(cannedResponse(200, "application/json", body))
      )
      assertEquals(
        McpClient(transport).discover(meta),
        Left(ClientError.TransportFailure)
      )
      assertEquals(closes.get(), 1)
    }
  }

  test("a firing deadline unblocks a JSON read and closes the body once") {
    val gate = new CountDownLatch(1)
    val closes = new AtomicInteger(0)
    val body = new InputStream {
      def read() = {
        gate.await(30, TimeUnit.SECONDS)
        -1
      }
      override def close() =
        if (closes.incrementAndGet() == 1) gate.countDown()
    }
    val transport = transportFor(
      "http://127.0.0.1:1/mcp",
      httpClient = stubClient(cannedResponse(200, "application/json", body)),
      timeout = Duration.ofMillis(300)
    )
    // Only the scheduled deadline can release the gated read.
    assertEquals(
      McpClient(transport).discover(meta),
      Left(ClientError.TransportFailure)
    )
    assertEquals(closes.get(), 1)
  }

  test("metadata failures after body acquisition close the body once") {
    List("status", "headers").foreach { failOn =>
      val closes = new AtomicInteger(0)
      val body = new InputStream {
        private val inner =
          new ByteArrayInputStream(successJson().getBytes(Utf8))
        def read() = inner.read()
        override def close() = {
          closes.incrementAndGet()
          inner.close()
        }
      }
      val response = cannedResponse(
        status =
          if (failOn == "status") throw new RuntimeException("status-secret")
          else 200,
        responseHeaders =
          if (failOn == "headers") throw new RuntimeException("headers-secret")
          else cannedHeaders,
        payload = body
      )
      val transport = transportFor(
        "http://127.0.0.1:1/mcp",
        httpClient = stubClient(response)
      )
      assertEquals(
        McpClient(transport).discover(meta),
        Left(ClientError.TransportFailure)
      )
      assertEquals(closes.get(), 1)
    }
  }

  test("an SSE body closes once on terminal delivery and on explicit close") {
    val payload =
      Sse.encode(
        """{"jsonrpc":"2.0","method":"vendor/custom","params":{}}"""
      ) + Sse.encode(successJson())
    val request = McpRequest(
      method = Method("server/discover"),
      id = NumberRequestId(1),
      params = Some(RequestParams(meta = meta))
    )

    val terminalBody = new CountingStream(payload)
    val terminal = transportFor(
      "http://127.0.0.1:1/mcp",
      httpClient = stubClient(
        cannedResponse(200, "text/event-stream", terminalBody)
      )
    )
    assert(terminal.exchange(request).isRight)
    assertEquals(terminalBody.closes.get(), 1)

    val explicitBody = new CountingStream(payload)
    val opened = transportFor(
      "http://127.0.0.1:1/mcp",
      httpClient = stubClient(
        cannedResponse(200, "text/event-stream", explicitBody)
      )
    ).open(request)
    assert(opened.isRight, s"open failed: $opened")
    val stream = opened.toOption.get
    assert(stream.next().isRight)
    stream.close()
    assertEquals(explicitBody.closes.get(), 1)
  }

  test("a fatal body close on a rejected response propagates") {
    val closes = new AtomicInteger(0)
    val body = new InputStream {
      private val inner =
        new ByteArrayInputStream(successJson().getBytes(Utf8))
      def read() = inner.read()
      override def close() = {
        closes.incrementAndGet()
        throw new LinkageError("close-fatal")
      }
    }
    val transport = transportFor(
      "http://127.0.0.1:1/mcp",
      httpClient = stubClient(cannedResponse(302, "application/json", body))
    )
    var sawFatal = false
    try {
      McpClient(transport).discover(meta)
      ()
    } catch { case _: LinkageError => sawFatal = true }
    assert(sawFatal, "expected LinkageError to propagate")
    assertEquals(closes.get(), 1)
  }

  test("a fatal close propagates over a nonfatal metadata failure") {
    val closes = new AtomicInteger(0)
    val body = new InputStream {
      private val inner =
        new ByteArrayInputStream(successJson().getBytes(Utf8))
      def read() = inner.read()
      override def close() = {
        closes.incrementAndGet()
        throw new LinkageError("close-fatal")
      }
    }
    val response = cannedResponse(
      status = throw new RuntimeException("status-secret"),
      responseHeaders = cannedHeaders,
      payload = body
    )
    val transport = transportFor(
      "http://127.0.0.1:1/mcp",
      httpClient = stubClient(response)
    )
    var sawFatal = false
    try {
      McpClient(transport).discover(meta)
      ()
    } catch { case _: LinkageError => sawFatal = true }
    assert(sawFatal, "expected LinkageError to propagate")
    assertEquals(closes.get(), 1)
  }

  test("a secondary fatal close never masks the primary fatal") {
    val closes = new AtomicInteger(0)
    val body = new InputStream {
      private val inner =
        new ByteArrayInputStream(successJson().getBytes(Utf8))
      def read() = inner.read()
      override def close() = {
        closes.incrementAndGet()
        throw new LinkageError("close-fatal")
      }
    }
    val transport = HttpClientTransport(
      endpoint = URI.create("http://127.0.0.1:1/mcp"),
      decoder = (_: String) => throw new LinkageError("decode-fatal"),
      encoder = JsonCodec.JsonValueEncoder,
      httpClient = stubClient(cannedResponse(200, "application/json", body))
    )
    var saw: Option[LinkageError] = None
    try {
      McpClient(transport).discover(meta)
      ()
    } catch { case e: LinkageError => saw = Some(e) }
    assertEquals(saw.map(_.getMessage), Some("decode-fatal"))
    assertEquals(closes.get(), 1)
  }

  test("a fatal SSE body close propagates instead of failing typed") {
    val closes = new AtomicInteger(0)
    val payload = Sse.encode(
      """{"jsonrpc":"2.0","method":"vendor/custom","params":{}}"""
    )
    val body = new InputStream {
      private val inner = new ByteArrayInputStream(payload.getBytes(Utf8))
      def read() = inner.read()
      override def close() = {
        closes.incrementAndGet()
        throw new LinkageError("close-fatal")
      }
    }
    val transport = transportFor(
      "http://127.0.0.1:1/mcp",
      httpClient = stubClient(cannedResponse(200, "text/event-stream", body))
    )
    val request = McpRequest(
      method = Method("server/discover"),
      id = NumberRequestId(1),
      params = Some(RequestParams(meta = meta))
    )
    var sawFatal = false
    try {
      transport.exchange(request)
      ()
    } catch { case _: LinkageError => sawFatal = true }
    assert(sawFatal, "expected LinkageError to propagate")
    assertEquals(closes.get(), 1)
  }

  test("a throwing http client fails without exposing detail") {
    val throwingClient = new HttpClient {
      def cookieHandler() = Optional.empty[CookieHandler]
      def connectTimeout() = Optional.empty[Duration]
      def followRedirects() = HttpClient.Redirect.NEVER
      def proxy() = Optional.empty[ProxySelector]
      def sslContext() = SSLContext.getDefault
      def sslParameters() = new SSLParameters
      def authenticator() = Optional.empty[Authenticator]
      def version() = HttpClient.Version.HTTP_1_1
      def executor() = Optional.empty[Executor]
      def send[A](
          request: HttpRequest,
          handler: HttpResponse.BodyHandler[A]
      ): HttpResponse[A] =
        throw new IOException("socket-secret")
      def sendAsync[A](
          request: HttpRequest,
          handler: HttpResponse.BodyHandler[A]
      ): CompletableFuture[HttpResponse[A]] =
        throw new IOException("socket-secret")
      def sendAsync[A](
          request: HttpRequest,
          handler: HttpResponse.BodyHandler[A],
          pushPromise: HttpResponse.PushPromiseHandler[A]
      ): CompletableFuture[HttpResponse[A]] =
        throw new IOException("socket-secret")
      override def newWebSocketBuilder(): WebSocket.Builder =
        throw new UnsupportedOperationException
    }
    val transport = transportFor(
      "http://127.0.0.1:1/mcp",
      httpClient = throwingClient
    )
    val outcome = McpClient(transport).discover(meta)
    assertEquals(outcome, Left(ClientError.TransportFailure))
  }

  test("a fatal encoder failure propagates") {
    val fatalEncoder = new Encoder[JsonValue] {
      def encode(value: JsonValue): String = throw new LinkageError("fatal")
    }
    val transport = HttpClientTransport(
      endpoint = URI.create("http://127.0.0.1:1/mcp"),
      decoder = JsonCodec.JsonValueDecoder,
      encoder = fatalEncoder,
      httpClient = HttpClient
        .newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()
    )
    var sawFatal = false
    try {
      McpClient(transport).discover(meta)
      ()
    } catch { case _: LinkageError => sawFatal = true }
    assert(sawFatal, "expected LinkageError to propagate")
  }
}
