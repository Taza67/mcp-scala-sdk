package io.github.taza67.mcp.transport.http

import io.github.taza67.mcp.protocol.json.JsonArray
import io.github.taza67.mcp.protocol.json.JsonBool
import io.github.taza67.mcp.protocol.json.JsonNull
import io.github.taza67.mcp.protocol.json.JsonNumber
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.json.JsonValue
import io.github.taza67.mcp.protocol.jsonrpc.ApplicationError
import io.github.taza67.mcp.protocol.jsonrpc.ErrorCode
import io.github.taza67.mcp.protocol.jsonrpc.MethodNotFoundError
import io.github.taza67.mcp.protocol.jsonrpc.NumberRequestId
import io.github.taza67.mcp.protocol.mcp.CompleteResultType
import io.github.taza67.mcp.protocol.mcp.McpErrorResponse
import io.github.taza67.mcp.protocol.mcp.McpNotification
import io.github.taza67.mcp.protocol.mcp.McpRequest
import io.github.taza67.mcp.protocol.mcp.McpResponse
import io.github.taza67.mcp.protocol.mcp.McpSuccessResponse
import io.github.taza67.mcp.protocol.mcp.RequestMeta
import io.github.taza67.mcp.protocol.mcp.RequestParams
import io.github.taza67.mcp.protocol.mcp.Result
import io.github.taza67.mcp.server.Server
import munit.FunSuite



class HttpEndpointSuite extends FunSuite {

  private val version = "2026-07-28"

  private val mediaHeaders = Map(
    "Content-Type" -> List("application/json"),
    "Accept" -> List("application/json, text/event-stream")
  )

  private def postHeaders(
      protocolVersion: String = version,
      method: String,
      name: Option[String] = None
  ): Map[String, List[String]] =
    mediaHeaders ++ Map(
      "MCP-Protocol-Version" -> List(protocolVersion),
      "Mcp-Method" -> List(method)
    ) ++ name.fold(Map.empty[String, List[String]])(n =>
      Map("Mcp-Name" -> List(n))
    )

  private def metaObject(v: String = version): JsonObject =
    JsonObject(
      Map(
        RequestMeta.ProtocolVersionKey -> JsonString(v),
        RequestMeta.ClientCapabilitiesKey -> JsonObject(Map.empty)
      )
    )

  private def requestBody(
      method: String,
      params: JsonObject,
      id: JsonValue = JsonNumber(1)
  ): JsonObject =
    JsonObject(
      Map(
        "jsonrpc" -> JsonString("2.0"),
        "id" -> id,
        "method" -> JsonString(method),
        "params" -> params
      )
    )

  private val discoverBody =
    requestBody(
      "server/discover",
      JsonObject(Map(RequestParams.MetaKey -> metaObject()))
    )
  private val discoverHeaders = postHeaders(method = "server/discover")

  private final class FakeServer(response: McpResponse) extends Server {
    var calls = 0
    var lastRequest: Option[McpRequest] = None
    override def handle(request: McpRequest): McpResponse = {
      calls += 1
      lastRequest = Some(request)
      response
    }
  }

  private val okResult =
    Result(
      CompleteResultType,
      JsonObject(Map("ok" -> JsonBool(true)))
    )

  private def errorCode(response: HttpResponse): BigDecimal =
    errorObject(response)("code").asInstanceOf[JsonNumber].value

  private def errorObject(response: HttpResponse): Map[String, JsonValue] =
    response.body.get.value("error").asInstanceOf[JsonObject].value

  test("valid request returns 200 with JSON body and no-store headers") {
    val server = new FakeServer(McpSuccessResponse(okResult, NumberRequestId(1)))
    val response = HttpEndpoint(server).handle("POST", discoverHeaders, discoverBody)
    assertEquals(response.status, 200)
    assertEquals(
      response.headers,
      Map("Content-Type" -> "application/json", "Cache-Control" -> "no-store")
    )
    val body = response.body.get.value
    assertEquals(
      body("result"),
      JsonObject(Map("resultType" -> JsonString("complete"), "ok" -> JsonBool(true)))
    )
    assertEquals(body("id"), JsonNumber(1))
    assertEquals(server.calls, 1)
  }

  test("unknown method maps to 404 and request errors map by code") {
    val server = new FakeServer(
      McpErrorResponse(MethodNotFoundError(), NumberRequestId(1))
    )
    val response = HttpEndpoint(server).handle("POST", discoverHeaders, discoverBody)
    assertEquals(response.status, 404)
    assertEquals(errorCode(response), BigDecimal(ErrorCode.MethodNotFound))
  }

  test("header mismatch rejects 400 correlated with the request id") {
    val server = new FakeServer(McpSuccessResponse(okResult, NumberRequestId(1)))
    val headers = discoverHeaders.updated("Mcp-Method", List("tools/list"))
    val response = HttpEndpoint(server).handle("POST", headers, discoverBody)
    assertEquals(response.status, 400)
    assertEquals(errorCode(response), BigDecimal(ErrorCode.HeaderMismatch))
    assertEquals(response.body.get.value("id"), JsonNumber(1))
    assertEquals(server.calls, 0)
  }

  test("unknown protocol version matching its header returns 400 unsupported") {
    val server = new FakeServer(McpSuccessResponse(okResult, NumberRequestId(1)))
    val body = requestBody(
      "server/discover",
      JsonObject(Map(RequestParams.MetaKey -> metaObject("9999-01-01")))
    )
    val headers = postHeaders("9999-01-01", "server/discover")
    val response = HttpEndpoint(server).handle("POST", headers, body)
    assertEquals(response.status, 400)
    assertEquals(
      errorCode(response),
      BigDecimal(ErrorCode.UnsupportedProtocolVersion)
    )
    assertEquals(server.calls, 0)
  }

  test("header mismatch correlates string and boundary numeric ids") {
    val server = new FakeServer(McpSuccessResponse(okResult, NumberRequestId(1)))
    val headers = discoverHeaders.updated("Mcp-Method", List("tools/list"))
    val stringId = requestBody(
      "server/discover",
      JsonObject(Map(RequestParams.MetaKey -> metaObject())),
      id = JsonString("req-9")
    )
    val boundId = requestBody(
      "server/discover",
      JsonObject(Map(RequestParams.MetaKey -> metaObject())),
      id = JsonNumber(BigDecimal(Long.MaxValue))
    )
    List((stringId, JsonString("req-9")), (boundId, JsonNumber(BigDecimal(Long.MaxValue))))
      .foreach { case (body, expectedId) =>
        val response = HttpEndpoint(server).handle("POST", headers, body)
        assertEquals(response.status, 400)
        assertEquals(errorCode(response), BigDecimal(ErrorCode.HeaderMismatch))
        assertEquals(response.body.get.value("id"), expectedId)
      }
    assertEquals(server.calls, 0)
  }

  test("version differing between header and body rejects as header mismatch") {
    val server = new FakeServer(McpSuccessResponse(okResult, NumberRequestId(1)))
    val headers = discoverHeaders.updated(
      "MCP-Protocol-Version",
      List("9999-01-01")
    )
    val response = HttpEndpoint(server).handle("POST", headers, discoverBody)
    assertEquals(response.status, 400)
    assertEquals(errorCode(response), BigDecimal(ErrorCode.HeaderMismatch))
    assertEquals(server.calls, 0)
  }

  test("missing client capabilities rejects with invalid params after headers") {
    val server = new FakeServer(McpSuccessResponse(okResult, NumberRequestId(1)))
    val body = requestBody(
      "server/discover",
      JsonObject(
        Map(
          RequestParams.MetaKey -> JsonObject(
            Map(RequestMeta.ProtocolVersionKey -> JsonString(version))
          )
        )
      )
    )
    val response = HttpEndpoint(server).handle("POST", discoverHeaders, body)
    assertEquals(response.status, 400)
    assertEquals(errorCode(response), BigDecimal(ErrorCode.InvalidParams))
    assertEquals(response.body.get.value("id"), JsonNumber(1))
    assertEquals(server.calls, 0)
  }

  test("inbound responses, arrays, and malformed ids reject 400 without id") {
    val server = new FakeServer(McpSuccessResponse(okResult, NumberRequestId(1)))
    val endpoint = HttpEndpoint(server)
    val responseShaped = JsonObject(
      Map(
        "jsonrpc" -> JsonString("2.0"),
        "id" -> JsonNumber(1),
        "result" -> JsonObject(Map.empty)
      )
    )
    val ambiguous = JsonObject(
      Map(
        "jsonrpc" -> JsonString("2.0"),
        "id" -> JsonNumber(1),
        "method" -> JsonString("server/discover"),
        "result" -> JsonObject(Map.empty)
      )
    )
    val malformedId = requestBody(
      "server/discover",
      JsonObject(Map(RequestParams.MetaKey -> metaObject())),
      id = JsonObject(Map("x" -> JsonNull))
    )
    List(responseShaped, ambiguous, malformedId, JsonArray(List(JsonNumber(1))),
      JsonString("x")).foreach { body =>
      val response = endpoint.handle("POST", discoverHeaders, body)
      assertEquals(response.status, 400)
      assertEquals(errorCode(response), BigDecimal(ErrorCode.InvalidRequest))
      assert(!response.body.get.value.contains("id"))
    }
    assertEquals(server.calls, 0)
  }

  test("origin policy: absent allowed, listed accepted, others 403") {
    val server = new FakeServer(McpSuccessResponse(okResult, NumberRequestId(1)))
    val endpoint =
      HttpEndpoint(server, allowedOrigins = Set("https://app.example.com"))
    assertEquals(
      endpoint.handle("POST", discoverHeaders, discoverBody).status,
      200
    )
    val allowed = endpoint.handle(
      "POST",
      discoverHeaders ++ Map("Origin" -> List("https://app.example.com")),
      discoverBody
    )
    assertEquals(allowed.status, 200)
    assertEquals(server.calls, 2)

    val callsBefore = server.calls
    List(
      Map("Origin" -> List("https://evil.example.com")),
      Map("origin" -> List("not an origin")),
      Map("Origin" -> List.empty[String]),
      Map("Origin" -> List("https://a.com"), "ORIGIN" -> List("https://a.com"))
    ).foreach { extra =>
      val response = endpoint.handle("POST", discoverHeaders ++ extra, discoverBody)
      assertEquals(response.status, 403)
      assertEquals(response.body, None)
    }
    assertEquals(server.calls, callsBefore)
  }

  test("present origin rejects by default and invalid configuration fails") {
    val server = new FakeServer(McpSuccessResponse(okResult, NumberRequestId(1)))
    val response = HttpEndpoint(server).handle(
      "POST",
      discoverHeaders ++ Map("Origin" -> List("https://app.example.com")),
      discoverBody
    )
    assertEquals(response.status, 403)
    List("*", "null", "https://user@x.com", "https://x.com/path", "x.com",
      "ftp://x.com", "https://x.com:99999").foreach { origin =>
      intercept[IllegalArgumentException](HttpEndpoint(server, Set(origin)))
    }
  }

  test("non-POST methods reject 405 with Allow POST") {
    val server = new FakeServer(McpSuccessResponse(okResult, NumberRequestId(1)))
    List("GET", "DELETE").foreach { method =>
      val response =
        HttpEndpoint(server).handle(method, discoverHeaders, discoverBody)
      assertEquals(response.status, 405)
      assertEquals(response.headers, Map("Allow" -> "POST"))
      assertEquals(response.body, None)
    }
    assertEquals(server.calls, 0)
  }

  test("accept must explicitly include both media types") {
    val server = new FakeServer(McpSuccessResponse(okResult, NumberRequestId(1)))
    val endpoint = HttpEndpoint(server)
    List(
      Map.empty[String, List[String]],
      Map("Accept" -> List("application/json")),
      Map("Accept" -> List("text/event-stream")),
      Map("Accept" -> List("application/json;q=0, text/event-stream")),
      Map("Accept" -> List("application/json;q=bogus, text/event-stream")),
      Map("Accept" -> List("application/json;q, text/event-stream")),
      Map("Accept" -> List("application/json;q=1e0, text/event-stream")),
      Map("Accept" -> List("application/json;q=\"0.5\", text/event-stream")),
      Map("Accept" -> List("application/json;q=0.5;q=0.5, text/event-stream")),
      Map("Accept" -> List("application/json;q=\"0.5, text/event-stream"))
    ).foreach { accept =>
      val headers =
        discoverHeaders.filterNot(_._1.equalsIgnoreCase("accept")) ++ accept
      assertEquals(endpoint.handle("POST", headers, discoverBody).status, 406)
    }
    // Case-insensitive, multi-line, and qualified values still count.
    val ok = discoverHeaders.updated(
      "Accept",
      List("APPLICATION/JSON;q=1.0", "text/event-stream; q=0.9")
    ) ++ Map("accept" -> List("application/json"))
    assertEquals(endpoint.handle("POST", ok, discoverBody).status, 200)
  }

  test("content-type must be single application/json with utf-8 charset") {
    val server = new FakeServer(McpSuccessResponse(okResult, NumberRequestId(1)))
    val endpoint = HttpEndpoint(server)
    List(
      Map("Content-Type" -> List("text/plain")),
      Map("Content-Type" -> List("application/json", "application/json")),
      Map("Content-Type" -> List("application/json;charset=iso-8859-1")),
      Map("Content-Type" -> List("application/json;charset=utf-8;charset=UTF-8")),
      Map("Content-Type" -> List("application/json;charset")),
      Map("Content-Type" -> List("application/json;charset=")),
      Map("Content-Type" -> List("application/json;charset=\"utf-8")),
      Map.empty[String, List[String]]
    ).foreach { contentType =>
      val headers =
        discoverHeaders.filterNot(_._1.equalsIgnoreCase("content-type")) ++
          contentType
      assertEquals(endpoint.handle("POST", headers, discoverBody).status, 415)
    }
    val quoted = discoverHeaders.updated(
      "Content-Type",
      List("APPLICATION/JSON; charset=\"UTF-8\"")
    )
    assertEquals(endpoint.handle("POST", quoted, discoverBody).status, 200)
    // Delimiters inside a well-formed quoted unknown parameter do not split.
    val exotic = discoverHeaders.updated(
      "Content-Type",
      List("application/json;foo=\"a;b,c\"")
    )
    assertEquals(endpoint.handle("POST", exotic, discoverBody).status, 200)
  }

  test("explicit notification callback returns 202 with no body") {
    var received: Option[McpNotification] = None
    val server = new FakeServer(McpSuccessResponse(okResult, NumberRequestId(1)))
    val endpoint = HttpEndpoint(
      server,
      onNotification = Some(n => { received = Some(n); Right(()) })
    )
    val body = JsonObject(
      Map(
        "jsonrpc" -> JsonString("2.0"),
        "method" -> JsonString("notifications/progress"),
        "params" -> JsonObject(Map("progressToken" -> JsonString("t-1")))
      )
    )
    val response = endpoint.handle("POST", mediaHeaders, body)
    assertEquals(response.status, 202)
    assertEquals(response.body, None)
    assertEquals(response.headers, Map.empty[String, String])
    assertEquals(received.map(_.method.value), Some("notifications/progress"))
    assertEquals(server.calls, 0)
  }

  test("notifications reject 400 by default, cancelled always, malformed too") {
    val server = new FakeServer(McpSuccessResponse(okResult, NumberRequestId(1)))
    val endpoint = HttpEndpoint(server)
    val progress = JsonObject(
      Map(
        "jsonrpc" -> JsonString("2.0"),
        "method" -> JsonString("notifications/progress"),
        "params" -> JsonObject(Map("progressToken" -> JsonString("t")))
      )
    )
    val cancelled = JsonObject(
      Map(
        "jsonrpc" -> JsonString("2.0"),
        "method" -> JsonString("notifications/cancelled"),
        "params" -> JsonObject(Map("requestId" -> JsonNumber(5)))
      )
    )
    val malformed = JsonObject(
      Map(
        "jsonrpc" -> JsonString("2.0"),
        "method" -> JsonString("notifications/progress"),
        "params" -> JsonObject(Map("_meta" -> JsonString("bad")))
      )
    )
    var invoked = false
    val withCallback = endpoint.copy(onNotification =
      Some(_ => { invoked = true; Right(()) })
    )
    List(progress, cancelled, malformed).foreach { body =>
      assertEquals(endpoint.handle("POST", mediaHeaders, body).status, 400)
    }
    assertEquals(withCallback.handle("POST", mediaHeaders, cancelled).status, 400)
    assert(!invoked)
    assertEquals(withCallback.handle("POST", mediaHeaders, malformed).status, 400)
    assertEquals(server.calls, 0)
  }

  test("notification callback errors: Left 400, thrown 500, fatal propagates") {
    val server = new FakeServer(McpSuccessResponse(okResult, NumberRequestId(1)))
    val body = JsonObject(
      Map(
        "jsonrpc" -> JsonString("2.0"),
        "method" -> JsonString("notifications/progress")
      )
    )
    // Caller-owned Left errors pass through verbatim.
    val failing = HttpEndpoint(
      server,
      onNotification = Some(_ => Left(ApplicationError(-32010, "caller-owned reason")))
    )
    val declined = failing.handle("POST", mediaHeaders, body)
    assertEquals(declined.status, 400)
    assertEquals(errorCode(declined), BigDecimal(-32010))
    assertEquals(
      errorObject(declined)("message"),
      JsonString("caller-owned reason")
    )
    assert(!declined.body.get.value.contains("id"))

    val throwing = HttpEndpoint(
      server,
      onNotification = Some(_ => throw new RuntimeException("secret"))
    )
    val boom = throwing.handle("POST", mediaHeaders, body)
    assertEquals(boom.status, 500)
    assertEquals(errorCode(boom), BigDecimal(ErrorCode.InternalError))
    // Thrown exceptions surface as the exact InternalError default, no leak.
    assertEquals(errorObject(boom)("message"), JsonString("Internal error"))
    assert(!errorObject(boom).contains("data"))

    val fatal = HttpEndpoint(
      server,
      onNotification = Some(_ => throw new LinkageError("fatal"))
    )
    var propagated = false
    try fatal.handle("POST", mediaHeaders, body)
    catch { case _: LinkageError => propagated = true }
    assert(propagated)
  }

  test("server failure maps to 500 correlated; fatal server errors propagate") {
    val throwing = new Server {
      override def handle(request: McpRequest): McpResponse =
        throw new RuntimeException("secret internals")
    }
    val response =
      HttpEndpoint(throwing).handle("POST", discoverHeaders, discoverBody)
    assertEquals(response.status, 500)
    assertEquals(errorCode(response), BigDecimal(ErrorCode.InternalError))
    assertEquals(errorObject(response)("message"), JsonString("Internal error"))
    assert(!errorObject(response).contains("data"))
    assertEquals(response.body.get.value("id"), JsonNumber(1))

    val fatal = new Server {
      override def handle(request: McpRequest): McpResponse =
        throw new InterruptedException("stop")
    }
    var propagated = false
    try HttpEndpoint(fatal).handle("POST", discoverHeaders, discoverBody)
    catch { case _: InterruptedException => propagated = true }
    assert(propagated)
  }

  test("session and Last-Event-ID headers are ignored") {
    val server = new FakeServer(McpSuccessResponse(okResult, NumberRequestId(1)))
    val headers = discoverHeaders ++ Map(
      "Mcp-Session-Id" -> List("s-1"),
      "Last-Event-ID" -> List("42")
    )
    val response = HttpEndpoint(server).handle("POST", headers, discoverBody)
    assertEquals(response.status, 200)
    assert(!response.headers.keys.exists(k =>
      k.equalsIgnoreCase("Mcp-Session-Id") || k.equalsIgnoreCase("Last-Event-ID")
    ))
  }

  test("decodeRequest returns the validated request without dispatching") {
    val server = new FakeServer(McpSuccessResponse(okResult, NumberRequestId(1)))
    val endpoint = HttpEndpoint(server)
    endpoint.decodeRequest(discoverHeaders, discoverBody) match {
      case Right(request) =>
        assertEquals(request.method.value, "server/discover")
        assertEquals(request.id, NumberRequestId(1))
      case Left(response) =>
        fail(s"expected a decoded request, got $response")
    }
    // decodeRequest must never invoke the server.
    assertEquals(server.calls, 0)
  }

  test("decodeRequest keeps header and version correlation on rejection") {
    val server = new FakeServer(McpSuccessResponse(okResult, NumberRequestId(1)))
    val endpoint = HttpEndpoint(server)
    val mismatched = postHeaders(protocolVersion = "1999-01-01", method = "server/discover")
    endpoint.decodeRequest(mismatched, discoverBody) match {
      case Left(response) =>
        assertEquals(response.status, 400)
        // Correlated with the request id, as the dispatch path does.
        assertEquals(response.body.get.value("id"), JsonNumber(1))
      case Right(_) => fail("expected a header rejection")
    }
    assertEquals(server.calls, 0)
  }

  test("decodeRequest rejects notifications and responses as 400") {
    val server = new FakeServer(McpSuccessResponse(okResult, NumberRequestId(1)))
    val endpoint = HttpEndpoint(server)
    val notification = JsonObject(
      Map(
        "jsonrpc" -> JsonString("2.0"),
        "method" -> JsonString("notifications/vendor")
      )
    )
    endpoint.decodeRequest(discoverHeaders, notification) match {
      case Left(response) => assertEquals(response.status, 400)
      case Right(_)       => fail("expected notification rejection")
    }
    val responseBody = JsonObject(
      Map(
        "jsonrpc" -> JsonString("2.0"),
        "id" -> JsonNumber(1),
        "result" -> JsonObject(Map.empty)
      )
    )
    endpoint.decodeRequest(discoverHeaders, responseBody) match {
      case Left(response) => assertEquals(response.status, 400)
      case Right(_)       => fail("expected response rejection")
    }
    assertEquals(server.calls, 0)
  }
}
