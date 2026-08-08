package io.github.taza67.mcp.codec.mcp

import io.github.taza67.mcp.codec.CodecAssertions
import io.github.taza67.mcp.codec.TestSupport
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.jsonrpc.ApplicationError
import io.github.taza67.mcp.protocol.jsonrpc.Method
import io.github.taza67.mcp.protocol.jsonrpc.StringRequestId
import io.github.taza67.mcp.protocol.mcp.CompleteResultType
import io.github.taza67.mcp.protocol.mcp.Cursor
import io.github.taza67.mcp.protocol.mcp.McpErrorResponse
import io.github.taza67.mcp.protocol.mcp.McpMessage
import io.github.taza67.mcp.protocol.mcp.McpNotification
import io.github.taza67.mcp.protocol.mcp.McpRequest
import io.github.taza67.mcp.protocol.mcp.McpSuccessResponse
import io.github.taza67.mcp.protocol.mcp.NotificationParams
import io.github.taza67.mcp.protocol.mcp.PaginatedRequestParams
import io.github.taza67.mcp.protocol.mcp.RequestParams
import io.github.taza67.mcp.protocol.mcp.Result
import io.github.taza67.mcp.protocol.mcp.tools.Tools
import munit.FunSuite



class MessagesSuite extends FunSuite with CodecAssertions {

  test("McpRequest decode rejects params without _meta") {
    val raw = JsonObject(
      Map(
        "jsonrpc" -> JsonString("2.0"),
        "method" -> JsonString(Tools.list.value),
        "id" -> JsonString("1"),
        "params" -> JsonObject(Map("cursor" -> JsonString("abc")))
      )
    )
    assert(Messages.toMessage(raw).isLeft)
  }

  test("McpRequest round-trips plain RequestParams") {
    assertRoundTrip[McpMessage, JsonObject](
      McpRequest(
        method = Tools.call,
        id = StringRequestId("1"),
        params = Some(
          RequestParams(
            meta = TestSupport.requestMeta,
            fields = JsonObject(Map("name" -> JsonString("echo")))
          )
        )
      )
    )(Messages.fromMessage, Messages.toMessage)
  }

  test("McpRequest round-trips paginated params with cursor") {
    val message: McpMessage = McpRequest(
      method = Tools.list,
      id = StringRequestId("1"),
      params = Some(
        PaginatedRequestParams(
          meta = TestSupport.requestMeta,
          cursor = Some(Cursor("abc"))
        )
      )
    )
    assertRoundTrip[McpMessage, JsonObject](message)(Messages.fromMessage, Messages.toMessage)
    val params = paramsObject(Messages.fromMessage(message))
    assertEquals(params.value.get(PaginatedRequestParams.CursorKey), Some(JsonString("abc")))
  }

  test("McpRequest round-trips paginated params without cursor as PaginatedRequestParams") {
    assertRoundTrip[McpMessage, JsonObject](
      McpRequest(
        method = Tools.list,
        id = StringRequestId("1"),
        params = Some(PaginatedRequestParams(meta = TestSupport.requestMeta))
      )
    )(Messages.fromMessage, Messages.toMessage)
  }

  test("McpRequest decode rejects cursor on non-paginated method") {
    val raw = JsonObject(
      Map(
        "jsonrpc" -> JsonString("2.0"),
        "method" -> JsonString(Tools.call.value),
        "id" -> JsonString("1"),
        "params" -> JsonObject(
          Map(
            RequestParams.MetaKey -> Meta.fromRequestMeta(TestSupport.requestMeta),
            PaginatedRequestParams.CursorKey -> JsonString("abc")
          )
        )
      )
    )
    assert(Messages.toMessage(raw).isLeft)
  }

  test("McpNotification round-trips without _meta") {
    assertRoundTrip[McpMessage, JsonObject](
      McpNotification(
        method = Method("notifications/initialized"),
        params = Some(NotificationParams())
      )
    )(Messages.fromMessage, Messages.toMessage)
  }

  test("McpSuccessResponse round-trips with resultType") {
    assertRoundTrip[McpMessage, JsonObject](
      McpSuccessResponse(
        result = Result(
          resultType = CompleteResultType,
          fields = JsonObject(Map("ok" -> JsonString("yes")))
        ),
        id = StringRequestId("1")
      )
    )(Messages.fromMessage, Messages.toMessage)
  }

  test("McpErrorResponse round-trips") {
    assertRoundTrip[McpMessage, JsonObject](
      McpErrorResponse(
        error = ApplicationError(code = 42, message = "boom"),
        id = StringRequestId("1")
      )
    )(Messages.fromMessage, Messages.toMessage)
  }
}
