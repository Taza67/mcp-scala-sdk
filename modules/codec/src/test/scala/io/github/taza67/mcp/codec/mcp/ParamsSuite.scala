package io.github.taza67.mcp.codec.mcp

import io.github.taza67.mcp.codec.CodecAssertions
import io.github.taza67.mcp.codec.TestSupport
import io.github.taza67.mcp.protocol.json.JsonNumber
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.jsonrpc.StringRequestId
import io.github.taza67.mcp.protocol.mcp.CompleteResultType
import io.github.taza67.mcp.protocol.mcp.Cursor
import io.github.taza67.mcp.protocol.mcp.CustomResultType
import io.github.taza67.mcp.protocol.mcp.InputRequiredResultType
import io.github.taza67.mcp.protocol.mcp.NotificationMeta
import io.github.taza67.mcp.protocol.mcp.NotificationParams
import io.github.taza67.mcp.protocol.mcp.PaginatedRequestParams
import io.github.taza67.mcp.protocol.mcp.RequestParams
import io.github.taza67.mcp.protocol.mcp.Result
import io.github.taza67.mcp.protocol.mcp.ResultMeta
import io.github.taza67.mcp.protocol.mcp.tools.Tools
import munit.FunSuite



class ParamsSuite extends FunSuite with CodecAssertions {

  test("RequestParams decode rejects missing _meta") {
    val raw = JsonObject(Map("cursor" -> JsonString("abc")))
    assert(Params.toRequestParams(raw).isLeft)
  }

  test("RequestParams round-trips with method fields and _meta") {
    assertRoundTrip(
      RequestParams(
        meta = TestSupport.requestMeta,
        fields = JsonObject(Map("filter" -> JsonString("abc")))
      )
    )(Params.fromRequestParams, Params.toRequestParams)
  }

  test("PaginatedRequestParams decode rejects missing _meta") {
    val raw = JsonObject(Map("cursor" -> JsonString("page-2")))
    assert(Params.toPaginatedRequestParams(raw).isLeft)
  }

  test("PaginatedRequestParams round-trips with cursor and _meta") {
    assertRoundTrip(
      PaginatedRequestParams(
        meta = TestSupport.requestMeta,
        cursor = Some(Cursor("page-2")),
        fields = JsonObject(Map("filter" -> JsonString("tools")))
      )
    )(Params.fromPaginatedRequestParams, Params.toPaginatedRequestParams)
  }

  test("PaginatedRequestParams round-trips without cursor") {
    assertRoundTrip(
      PaginatedRequestParams(meta = TestSupport.requestMeta)
    )(Params.fromPaginatedRequestParams, Params.toPaginatedRequestParams)
  }

  test("toMcpRequestParams uses method to keep first-page lists paginated") {
    val raw = Params.fromPaginatedRequestParams(
      PaginatedRequestParams(meta = TestSupport.requestMeta)
    )
    assertEquals(
      Params.toMcpRequestParams(Tools.list, raw),
      Right(PaginatedRequestParams(meta = TestSupport.requestMeta))
    )
  }

  test("toMcpRequestParams rejects cursor on non-paginated methods") {
    val raw = JsonObject(
      Map(
        RequestParams.MetaKey -> Meta.fromRequestMeta(TestSupport.requestMeta),
        PaginatedRequestParams.CursorKey -> JsonString("page-2")
      )
    )
    assert(Params.toMcpRequestParams(Tools.call, raw).isLeft)
  }

  test("Cursor rejects blank and non-string values") {
    assert(Params.toCursor(JsonString("   ")).isLeft)
    assert(Params.toCursor(JsonNumber(1)).isLeft)
    assertEquals(Params.toCursor(JsonString("page-2")), Right(Cursor("page-2")))
  }

  test("ResultType classifies known and custom wire strings") {
    assertEquals(Params.toResultType(JsonString("complete")), Right(CompleteResultType))
    assertEquals(
      Params.toResultType(JsonString("input_required")),
      Right(InputRequiredResultType)
    )
    assertEquals(Params.toResultType(JsonString("vendor/x")), Right(CustomResultType("vendor/x")))
    assert(Params.toResultType(JsonNumber(1)).isLeft)
  }

  test("NotificationParams round-trips without _meta") {
    assertRoundTrip(
      NotificationParams(fields = JsonObject(Map("level" -> JsonString("info"))))
    )(Params.fromNotificationParams, Params.toNotificationParams)
  }

  test("NotificationParams round-trips with subscription id in _meta") {
    assertRoundTrip(
      NotificationParams(
        meta = Some(NotificationMeta(subscriptionId = Some(StringRequestId("listen-1")))),
        fields = JsonObject(Map("level" -> JsonString("info")))
      )
    )(Params.fromNotificationParams, Params.toNotificationParams)
  }

  test("Result round-trips with resultType and optional _meta") {
    assertRoundTrip(
      Result(
        resultType = InputRequiredResultType,
        fields = JsonObject(Map("ok" -> JsonString("yes"))),
        meta = Some(ResultMeta(serverInfo = Some(TestSupport.implementation)))
      )
    )(Params.fromResult, Params.toResult)
  }

  test("Result decode defaults absent resultType to complete") {
    val raw = JsonObject(Map("ok" -> JsonString("yes")))
    assertEquals(
      Params.toResult(raw),
      Right(Result(resultType = CompleteResultType, fields = raw))
    )
  }
}
