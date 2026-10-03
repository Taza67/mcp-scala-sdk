package io.github.taza67.mcp.transport.http

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
import java.util.concurrent.Executors

import scala.jdk.CollectionConverters._

import com.sun.net.httpserver.Authenticator
import com.sun.net.httpserver.Filter
import com.sun.net.httpserver.Headers
import com.sun.net.httpserver.HttpContext
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpPrincipal
import com.sun.net.httpserver.HttpServer

import io.github.taza67.mcp.codec.Decoder
import io.github.taza67.mcp.codec.Encoder
import io.github.taza67.mcp.codec.WireLimits
import io.github.taza67.mcp.codec.circe.JsonCodec
import io.github.taza67.mcp.protocol.json.JsonValue
import io.github.taza67.mcp.protocol.jsonrpc.ErrorCode
import io.github.taza67.mcp.protocol.mcp.Implementation
import io.github.taza67.mcp.protocol.mcp.McpNotification
import io.github.taza67.mcp.protocol.mcp.McpRequest
import io.github.taza67.mcp.protocol.mcp.McpResponse
import io.github.taza67.mcp.protocol.mcp.RequestMeta
import io.github.taza67.mcp.server.McpServer
import io.github.taza67.mcp.server.Server
import munit.FunSuite



class HttpTransportSuite extends FunSuite {

  private val version = "2026-07-28"
  private val Utf8 = StandardCharsets.UTF_8

  private val mcpServer = McpServer(info = Implementation(name = "ex", version = "1"))

  private def transport(
      ep: HttpEndpoint = HttpEndpoint(mcpServer),
      decoder: Decoder[JsonValue] = JsonCodec.JsonValueDecoder,
      encoder: Encoder[JsonValue] = JsonCodec.JsonValueEncoder,
      maxMessageSize: Int = WireLimits.DefaultMaxMessageSize,
      maxNestingDepth: Int = WireLimits.DefaultMaxNestingDepth
  ): HttpTransport =
    HttpTransport(ep, decoder, encoder, maxMessageSize, maxNestingDepth)

  private val metaJson =
    s"""{"_meta":{"${RequestMeta.ProtocolVersionKey}":"$version","${RequestMeta.ClientCapabilitiesKey}":{}}}"""

  private def requestJson(
      method: String,
      params: String = metaJson,
      id: String = "1"
  ): String =
    s"""{"jsonrpc":"2.0","id":$id,"method":"$method","params":$params}"""

  private val discoverJson = requestJson("server/discover")

  private val client =
    HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()

  private def baseHeaders(method: String): Seq[(String, String)] =
    Seq(
      "Content-Type" -> "application/json",
      "Accept" -> "application/json, text/event-stream",
      "MCP-Protocol-Version" -> version,
      "Mcp-Method" -> method
    )

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
    client.send(builder.build(), BodyHandlers.ofString(Utf8))
  }

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

  private def errorCodeOf(body: String): Int = {
    val marker = "\"code\":"
    body.substring(body.indexOf(marker) + marker.length)
      .takeWhile(c => c.isDigit || c == '-')
      .toInt
  }

  test("valid discover POST returns 200 JSON with correct byte length") {
    withServer(transport()) { uri =>
      val response = post(uri, discoverJson, baseHeaders("server/discover"))
      assertEquals(response.statusCode(), 200)
      assertEquals(
        response.headers().firstValue("Content-Type").orElse(""),
        "application/json"
      )
      assertEquals(
        response.headers().firstValue("Content-Length").orElse("").toInt,
        response.body().getBytes(Utf8).length
      )
      assert(response.body().contains("\"result\":"))
      assert(response.body().contains("\"id\":1"))
    }
  }

  test("unicode request decodes and unicode response uses UTF-8 byte length") {
    val unicodeServer = McpServer(info =
      Implementation(name = "caf\u00e9 \u4e16\u754c", version = "1")
    )
    val body = requestJson(
      "server/discover",
      metaJson.dropRight(1) + ",\"x-note\":\"caf\u00e9 \u4e16\u754c\"}"
    )
    withServer(transport(ep = HttpEndpoint(unicodeServer))) { uri =>
      val response = post(uri, body, baseHeaders("server/discover"))
      assertEquals(response.statusCode(), 200)
      assert(response.body().contains("caf\u00e9"))
      // Multi-byte content: byte length must exceed the char length, and
      // Content-Length must be the UTF-8 byte count.
      val bytes = response.body().getBytes(Utf8)
      assert(bytes.length > response.body().length)
      assertEquals(
        response.headers().firstValue("Content-Length").orElse("").toInt,
        bytes.length
      )
    }
  }

  test("invalid JSON, trailing input, and raw non-UTF-8 bytes return 400") {
    withServer(transport()) { uri =>
      val headers = baseHeaders("server/discover")
      val malformed = post(uri, "{not json", headers)
      assertEquals(malformed.statusCode(), 400)
      assertEquals(errorCodeOf(malformed.body()), ErrorCode.ParseError)

      val trailing = post(uri, discoverJson + " {}", headers)
      assertEquals(trailing.statusCode(), 400)
      assertEquals(errorCodeOf(trailing.body()), ErrorCode.ParseError)

      // Raw 0xFF is never valid UTF-8.
      val raw = HttpRequest
        .newBuilder(URI.create(uri))
        .timeout(Duration.ofSeconds(10))
        .POST(BodyPublishers.ofByteArray(Array[Byte](0x7b, 0x22, 0xff.toByte)))
      headers.foreach { case (n, v) => raw.header(n, v) }
      val badBytes = client.send(raw.build(), BodyHandlers.ofString(Utf8))
      assertEquals(badBytes.statusCode(), 400)
      assertEquals(errorCodeOf(badBytes.body()), ErrorCode.ParseError)
    }
  }

  test("oversize bodies reject 413 for fixed and chunked publishers") {
    withServer(transport(maxMessageSize = 64)) { uri =>
      val big = requestJson(
        "server/discover",
        metaJson.dropRight(1) + s""","pad":"${"x" * 256}"}"""
      )
      val fixed = post(uri, big, baseHeaders("server/discover"))
      assertEquals(fixed.statusCode(), 413)
      assertEquals(errorCodeOf(fixed.body()), ErrorCode.InvalidRequest)

      val chunked = HttpRequest
        .newBuilder(URI.create(uri))
        .timeout(Duration.ofSeconds(10))
        .POST(BodyPublishers.ofByteArrays(java.util.List.of(big.getBytes(Utf8))))
      baseHeaders("server/discover").foreach { case (n, v) => chunked.header(n, v) }
      val chunkedResponse =
        client.send(chunked.build(), BodyHandlers.ofString(Utf8))
      assertEquals(chunkedResponse.statusCode(), 413)
      assertEquals(errorCodeOf(chunkedResponse.body()), ErrorCode.InvalidRequest)
    }
  }

  test("nesting limit rejects deep input but ignores braces in strings") {
    withServer(transport(maxNestingDepth = 6)) { uri =>
      val headers = baseHeaders("tools/list")
      val deep = requestJson(
        "tools/list",
        metaJson.dropRight(1) + ""","deep":{"a":{"b":{"c":{"d":{}}}}}}"""
      )
      val rejected = post(uri, deep, headers)
      assertEquals(rejected.statusCode(), 400)
      assertEquals(errorCodeOf(rejected.body()), ErrorCode.InvalidRequest)
      assert(rejected.body().contains("nesting"))

      // Braces and escaped quotes confined to a string do not count; the
      // valid request reaches the server, which answers MethodNotFound.
      val stringBraces = requestJson(
        "tools/list",
        metaJson.dropRight(1) + ""","x":"a\"}{{{"}"""
      )
      val through = post(uri, stringBraces, headers)
      assertEquals(through.statusCode(), 404)
    }
  }

  test("header mismatch rejects 400 correlated before dispatch") {
    withServer(transport()) { uri =>
      val headers = baseHeaders("server/discover").map {
        case ("Mcp-Method", _) => "Mcp-Method" -> "tools/call"
        case other             => other
      }
      val response = post(uri, discoverJson, headers)
      assertEquals(response.statusCode(), 400)
      assertEquals(errorCodeOf(response.body()), ErrorCode.HeaderMismatch)
      assert(response.body().contains("\"id\":1"))
    }
  }

  test("unknown method 404 and unknown version 400 unsupported") {
    withServer(transport()) { uri =>
      val unknown = post(uri, requestJson("made/up"), baseHeaders("made/up"))
      assertEquals(unknown.statusCode(), 404)
      assertEquals(errorCodeOf(unknown.body()), ErrorCode.MethodNotFound)

      val wrongVersion = requestJson(
        "server/discover",
        metaJson.replace(version, "9999-01-01")
      )
      val response = post(
        uri,
        wrongVersion,
        Seq(
          "Content-Type" -> "application/json",
          "Accept" -> "application/json, text/event-stream",
          "MCP-Protocol-Version" -> "9999-01-01",
          "Mcp-Method" -> "server/discover"
        )
      )
      assertEquals(response.statusCode(), 400)
      assertEquals(
        errorCodeOf(response.body()),
        ErrorCode.UnsupportedProtocolVersion
      )
    }
  }

  test("GET and DELETE return 405, unmatched context path returns 404") {
    withServer(transport()) { uri =>
      List("GET", "DELETE").foreach { method =>
        val request = HttpRequest
          .newBuilder(URI.create(uri))
          .timeout(Duration.ofSeconds(10))
          .method(method, HttpRequest.BodyPublishers.noBody())
        val response = client.send(request.build(), BodyHandlers.ofString(Utf8))
        assertEquals(response.statusCode(), 405)
        assertEquals(response.headers().firstValue("Allow").orElse(""), "POST")
      }
      val subPath = post(uri + "/other", discoverJson, baseHeaders("server/discover"))
      assertEquals(subPath.statusCode(), 404)
      assertEquals(subPath.body(), "")
    }
  }

  test("origin rejection happens before the request body is read") {
    val exchange = new FakeExchange(
      method = "POST",
      requestPath = "/mcp",
      contextPath = "/mcp",
      requestHeaders = Map(
        "Origin" -> List("https://evil.example.com"),
        "Content-Type" -> List("application/json"),
        "Accept" -> List("application/json, text/event-stream")
      )
    )
    transport().handle(exchange)
    assertEquals(exchange.status, 403)
    assertEquals(exchange.responseBody.toByteArray.length, 0)
    assert(exchange.closed)
    assert(!exchange.bodyRead)
  }

  test("accepted notification returns 202 without body") {
    var received = false
    val ep = HttpEndpoint(
      mcpServer,
      onNotification = Some((_: McpNotification) => { received = true; Right(()) })
    )
    withServer(transport(ep = ep)) { uri =>
      val body =
        """{"jsonrpc":"2.0","method":"notifications/progress","params":{"t":1}}"""
      val response = post(
        uri,
        body,
        Seq(
          "Content-Type" -> "application/json",
          "Accept" -> "application/json, text/event-stream"
        )
      )
      assertEquals(response.statusCode(), 202)
      assertEquals(response.body(), "")
      assert(response.headers().firstValue("Content-Type").isEmpty)
      assert(received)
    }
  }

  test("throwing decoder and encoder surface generic 500 responses") {
    val throwingDecoder = new Decoder[JsonValue] {
      def decode(value: String) = throw new RuntimeException("secret decoder")
    }
    withServer(transport(decoder = throwingDecoder)) { uri =>
      val response = post(uri, discoverJson, baseHeaders("server/discover"))
      assertEquals(response.statusCode(), 500)
      assertEquals(errorCodeOf(response.body()), ErrorCode.InternalError)
      assert(response.body().contains("\"message\":\"Internal error\""))
      assert(!response.body().contains("secret"))
    }

    var handled = 0
    val countingServer = new Server {
      def handle(request: McpRequest): McpResponse = {
        handled += 1
        mcpServer.handle(request)
      }
    }
    var encoderCalls = 0
    val onceFailingEncoder = new Encoder[JsonValue] {
      def encode(value: JsonValue): String = {
        encoderCalls += 1
        if (encoderCalls == 1) throw new RuntimeException("secret encoder")
        else JsonCodec.JsonValueEncoder.encode(value)
      }
    }
    withServer(
      transport(ep = HttpEndpoint(countingServer), encoder = onceFailingEncoder)
    ) { uri =>
      val response = post(uri, discoverJson, baseHeaders("server/discover"))
      assertEquals(response.statusCode(), 500)
      assertEquals(errorCodeOf(response.body()), ErrorCode.InternalError)
      // The retry keeps the request correlation id, uses the default
      // InternalError message, carries no data, and never redispatches.
      assert(response.body().contains("\"id\":1"))
      assert(response.body().contains("\"message\":\"Internal error\""))
      assert(!response.body().contains("\"data\""))
      assert(!response.body().contains("secret"))
      assertEquals(encoderCalls, 2)
      assertEquals(handled, 1)
    }

    val alwaysFailing = new Encoder[JsonValue] {
      def encode(value: JsonValue): String =
        throw new RuntimeException("always secret")
    }
    withServer(transport(encoder = alwaysFailing)) { uri =>
      val response = post(uri, discoverJson, baseHeaders("server/discover"))
      assertEquals(response.statusCode(), 500)
      assertEquals(
        response.body(),
        """{"jsonrpc":"2.0","error":{"code":-32603,"message":"Internal error"}}"""
      )
      assert(!response.body().contains("secret"))
    }
  }

  test("encoder emitting unpaired surrogates falls back to static ASCII") {
    var calls = 0
    val surrogateEncoder = new Encoder[JsonValue] {
      def encode(value: JsonValue): String = {
        calls += 1
        "{\"broken\":\"\ud800\"}"
      }
    }
    withServer(transport(encoder = surrogateEncoder)) { uri =>
      val response = post(uri, discoverJson, baseHeaders("server/discover"))
      assertEquals(response.statusCode(), 500)
      // Unencodable UTF-16 is never silently replaced; the fixed fallback
      // is emitted instead.
      assertEquals(
        response.body(),
        """{"jsonrpc":"2.0","error":{"code":-32603,"message":"Internal error"}}"""
      )
      assertEquals(calls, 2)
    }
  }

  test("limit configuration fails fast and the server stays usable") {
    val ep = HttpEndpoint(mcpServer)
    val dec = JsonCodec.JsonValueDecoder
    val enc = JsonCodec.JsonValueEncoder
    List(
      () => HttpTransport(ep, dec, enc, 0, 128),
      () => HttpTransport(ep, dec, enc, 8, 0),
      () => HttpTransport(ep, dec, enc, Int.MaxValue, 128),
      () => HttpTransport(ep, dec, enc, heartbeatInterval = Duration.ZERO),
      () =>
        HttpTransport(ep, dec, enc, heartbeatInterval = Duration.ofSeconds(-1)),
      () =>
        HttpTransport(
          ep,
          dec,
          enc,
          heartbeatInterval = Duration.ofDays(2000000000L)
        ),
      () => HttpTransport(ep, dec, enc, maxPendingMessages = 0),
      () => HttpTransport(ep, dec, enc, maxPendingMessages = Int.MaxValue)
    ).foreach(f => intercept[IllegalArgumentException](f()))

    withServer(transport()) { uri =>
      (1 to 2).foreach { _ =>
        assertEquals(
          post(uri, discoverJson, baseHeaders("server/discover")).statusCode(),
          200
        )
      }
    }
  }

  /** Minimal exchange whose body read is observable and forbidden. */
  private final class FakeExchange(
      method: String,
      requestPath: String,
      contextPath: String,
      requestHeaders: Map[String, List[String]]
  ) extends HttpExchange {

    val responseHeaders = new Headers()
    val responseBody = new ByteArrayOutputStream()
    var status = 0
    var closed = false
    var bodyRead = false

    private val headers = new Headers()
    requestHeaders.foreach { case (k, vs) => headers.put(k, vs.asJava) }

    private val context = new HttpContext {
      def getHandler: HttpHandler = null
      def getPath: String = contextPath
      def getServer: HttpServer = null
      def getAttributes = new java.util.HashMap[String, Object]()
      def getFilters = new java.util.ArrayList[Filter]()
      def setHandler(handler: HttpHandler): Unit = ()
      def getAuthenticator: Authenticator = null
      def setAuthenticator(auth: Authenticator): Authenticator = null
      def getParameters = null
    }

    override def getRequestHeaders = headers
    override def getResponseHeaders = responseHeaders
    override def getRequestURI = URI.create(requestPath)
    override def getRequestMethod = method
    override def getHttpContext = context
    override def getRequestBody: InputStream =
      new InputStream {
        override def read(): Int = {
          bodyRead = true
          throw new IOException("body must not be read")
        }
        override def read(b: Array[Byte], off: Int, len: Int): Int = {
          bodyRead = true
          throw new IOException("body must not be read")
        }
      }
    override def getResponseBody: OutputStream = responseBody
    override def sendResponseHeaders(code: Int, length: Long): Unit =
      status = code
    override def getRemoteAddress = null
    override def getResponseCode = status
    override def getLocalAddress = null
    override def getProtocol = "HTTP/1.1"
    override def getAttribute(name: String) = null
    override def setStreams(in: InputStream, out: OutputStream) = ()
    override def setAttribute(name: String, value: Object) = ()
    override def getPrincipal: HttpPrincipal = null
    override def close(): Unit = closed = true
  }
}
