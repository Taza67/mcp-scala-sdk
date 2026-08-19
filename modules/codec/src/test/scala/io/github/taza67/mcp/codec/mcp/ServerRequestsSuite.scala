package io.github.taza67.mcp.codec.mcp

import io.github.taza67.mcp.protocol.json.JsonArray
import io.github.taza67.mcp.protocol.json.JsonBool
import io.github.taza67.mcp.protocol.json.JsonNull
import io.github.taza67.mcp.protocol.json.JsonNumber
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.json.JsonValue
import io.github.taza67.mcp.protocol.jsonrpc.ErrorCode
import io.github.taza67.mcp.protocol.jsonrpc.ErrorResponse
import io.github.taza67.mcp.protocol.jsonrpc.Method
import io.github.taza67.mcp.protocol.jsonrpc.NumberRequestId
import io.github.taza67.mcp.protocol.jsonrpc.RequestId
import io.github.taza67.mcp.protocol.jsonrpc.StringRequestId
import io.github.taza67.mcp.protocol.jsonrpc.UnsupportedProtocolVersionError
import io.github.taza67.mcp.protocol.mcp.ClientCapabilities
import io.github.taza67.mcp.protocol.mcp.McpProtocolVersion20260728
import io.github.taza67.mcp.protocol.mcp.McpRequest
import io.github.taza67.mcp.protocol.mcp.PaginatedRequestParams
import io.github.taza67.mcp.protocol.mcp.RequestMeta
import io.github.taza67.mcp.protocol.mcp.RequestParams
import io.github.taza67.mcp.protocol.mcp.discover.ServerDiscover
import io.github.taza67.mcp.protocol.mcp.tools.Tools
import munit.FunSuite



class ServerRequestsSuite extends FunSuite {

  private val meta = JsonObject(
    Map(
      RequestMeta.ProtocolVersionKey -> JsonString(McpProtocolVersion20260728.value),
      RequestMeta.ClientCapabilitiesKey -> JsonObject(Map.empty)
    )
  )

  private def requestObject(
      method: String,
      id: JsonValue,
      params: Option[JsonValue]
  ): JsonObject = {
    val base = Map(
      "jsonrpc" -> JsonString("2.0"),
      "method" -> JsonString(method),
      "id" -> id
    )
    JsonObject(params.fold(base)(p => base + ("params" -> p)))
  }

  private def withMeta(fields: (String, JsonValue)*): JsonObject =
    JsonObject(Map(RequestParams.MetaKey -> JsonObject(Map(fields: _*))))

  private def assertRejected(
      value: JsonValue,
      code: Int,
      id: Option[RequestId]
  ): Unit =
    ServerRequests.toRequest(value) match {
      case Left(response) =>
        assertEquals(response.error.code, code)
        assertEquals(response.id, id)
      case Right(other) =>
        fail(s"expected rejection, got $other")
    }

  test("valid discovery request preserves RequestParams and metadata") {
    val raw = requestObject(
      ServerDiscover.method.value,
      JsonString("1"),
      Some(withMeta(
        RequestMeta.ProtocolVersionKey -> JsonString(McpProtocolVersion20260728.value),
        RequestMeta.ClientCapabilitiesKey -> JsonObject(Map.empty)
      ))
    )
    ServerRequests.toRequest(raw) match {
      case Right(Some(request)) =>
        assertEquals(request.method, ServerDiscover.method)
        assertEquals(request.id, StringRequestId("1"))
        request.params match {
          case Some(params: RequestParams) =>
            assertEquals(params.meta.protocolVersion, McpProtocolVersion20260728)
            assertEquals(params.meta.clientCapabilities, ClientCapabilities())
          case other =>
            fail(s"expected RequestParams, got $other")
        }
      case other =>
        fail(s"expected request, got $other")
    }
  }

  test("valid list request preserves PaginatedRequestParams subtype and number id") {
    val raw = requestObject(
      Tools.list.value,
      JsonNumber(7),
      Some(JsonObject(Map(RequestParams.MetaKey -> meta)))
    )
    ServerRequests.toRequest(raw) match {
      case Right(Some(request)) =>
        assertEquals(request.method, Tools.list)
        assertEquals(request.id, NumberRequestId(7L))
        assert(request.params.exists(_.isInstanceOf[PaginatedRequestParams]))
      case other =>
        fail(s"expected request, got $other")
    }
  }

  test("request without params is rejected with InvalidParams and its id") {
    assertRejected(
      requestObject("ping", JsonString("req-1"), None),
      ErrorCode.InvalidParams,
      Some(StringRequestId("req-1"))
    )
  }

  test("request with non-object params is rejected with InvalidParams and its id") {
    assertRejected(
      requestObject("ping", JsonString("req-1"), Some(JsonString("nope"))),
      ErrorCode.InvalidParams,
      Some(StringRequestId("req-1"))
    )
  }

  test("requests with missing or malformed meta members are rejected with InvalidParams") {
    val id = JsonString("req-1")
    val expected = Some(StringRequestId("req-1"))
    val cases = List(
      // params without _meta
      JsonObject(Map("cursor" -> JsonString("abc"))),
      // _meta not an object
      JsonObject(Map(RequestParams.MetaKey -> JsonString("nope"))),
      // _meta without protocol version
      withMeta(RequestMeta.ClientCapabilitiesKey -> JsonObject(Map.empty)),
      // _meta without client capabilities
      withMeta(RequestMeta.ProtocolVersionKey -> JsonString(McpProtocolVersion20260728.value)),
      // protocol version not a string
      withMeta(
        RequestMeta.ProtocolVersionKey -> JsonNumber(1),
        RequestMeta.ClientCapabilitiesKey -> JsonObject(Map.empty)
      ),
      // client capabilities not an object
      withMeta(
        RequestMeta.ProtocolVersionKey -> JsonString(McpProtocolVersion20260728.value),
        RequestMeta.ClientCapabilitiesKey -> JsonString("nope")
      )
    )
    cases.foreach { params =>
      assertRejected(
        requestObject("ping", id, Some(params)),
        ErrorCode.InvalidParams,
        expected
      )
    }
  }

  test("unsupported protocol version is rejected with UnsupportedProtocolVersionError") {
    val raw = requestObject(
      "ping",
      JsonString("9"),
      Some(withMeta(
        RequestMeta.ProtocolVersionKey -> JsonString("1999-01-01"),
        RequestMeta.ClientCapabilitiesKey -> JsonObject(Map.empty)
      ))
    )
    ServerRequests.toRequest(raw) match {
      case Left(response) =>
        assertEquals(response.id, Some(StringRequestId("9")))
        response.error match {
          case error: UnsupportedProtocolVersionError =>
            assertEquals(error.supported, List(McpProtocolVersion20260728.value))
            assertEquals(error.requested, "1999-01-01")
          case other =>
            fail(s"expected UnsupportedProtocolVersionError, got $other")
        }
      case Right(other) =>
        fail(s"expected rejection, got $other")
    }
  }

  test("unknown method with valid params reaches the dispatcher") {
    val raw = requestObject(
      "totally/unknown",
      JsonString("3"),
      Some(JsonObject(Map(RequestParams.MetaKey -> meta)))
    )
    ServerRequests.toRequest(raw) match {
      case Right(Some(request)) =>
        assertEquals(request.method, Method("totally/unknown"))
      case other =>
        fail(s"expected request, got $other")
    }
  }

  test("malformed envelopes are rejected with InvalidRequest and no id") {
    val cases = List[JsonValue](
      // non-object messages
      JsonArray(List(JsonObject(Map.empty))),
      JsonString("nope"),
      JsonNumber(1),
      JsonNull,
      JsonBool(true),
      // missing jsonrpc version
      JsonObject(Map("method" -> JsonString("ping"), "id" -> JsonString("1"))),
      // wrong jsonrpc version
      JsonObject(
        Map(
          "jsonrpc" -> JsonString("1.0"),
          "method" -> JsonString("ping"),
          "id" -> JsonString("1")
        )
      ),
      // non-string method
      JsonObject(
        Map(
          "jsonrpc" -> JsonString("2.0"),
          "method" -> JsonNumber(1),
          "id" -> JsonString("1")
        )
      ),
      // non-scalar id
      JsonObject(
        Map(
          "jsonrpc" -> JsonString("2.0"),
          "method" -> JsonString("ping"),
          "id" -> JsonBool(true)
        )
      ),
      // ambiguous: method plus result
      JsonObject(
        Map(
          "jsonrpc" -> JsonString("2.0"),
          "method" -> JsonString("ping"),
          "id" -> JsonString("1"),
          "result" -> JsonObject(Map.empty)
        )
      ),
      // neither request, notification, nor response
      JsonObject(Map("jsonrpc" -> JsonString("2.0"), "id" -> JsonString("1")))
    )
    cases.foreach(raw => assertRejected(raw, ErrorCode.InvalidRequest, None))
  }

  test("notifications are ignored, even with invalid params") {
    val valid = JsonObject(
      Map(
        "jsonrpc" -> JsonString("2.0"),
        "method" -> JsonString("notifications/initialized")
      )
    )
    val badParams = JsonObject(
      Map(
        "jsonrpc" -> JsonString("2.0"),
        "method" -> JsonString("notifications/initialized"),
        "params" -> JsonString("nope")
      )
    )
    assertEquals(ServerRequests.toRequest(valid), Right(None))
    assertEquals(ServerRequests.toRequest(badParams), Right(None))
  }

  test("response-shaped messages are ignored, even malformed") {
    val cases = List[JsonValue](
      JsonObject(
        Map(
          "jsonrpc" -> JsonString("2.0"),
          "result" -> JsonObject(Map.empty),
          "id" -> JsonString("1")
        )
      ),
      JsonObject(
        Map(
          "jsonrpc" -> JsonString("2.0"),
          "error" -> JsonObject(
            Map("code" -> JsonNumber(-32601), "message" -> JsonString("nope"))
          ),
          "id" -> JsonString("1")
        )
      ),
      // malformed responses: no jsonrpc, no id, both members
      JsonObject(Map("result" -> JsonNull)),
      JsonObject(
        Map(
          "result" -> JsonObject(Map.empty),
          "error" -> JsonObject(Map.empty)
        )
      )
    )
    cases.foreach(raw => assertEquals(ServerRequests.toRequest(raw), Right(None)))
  }
}
