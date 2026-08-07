package io.github.taza67.mcp.codec.mcp

import io.github.taza67.mcp.codec.CodecAssertions
import io.github.taza67.mcp.codec.TestSupport
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.jsonrpc.StringRequestId
import io.github.taza67.mcp.protocol.mcp.CompleteResultType
import io.github.taza67.mcp.protocol.mcp.InputRequiredResultType
import io.github.taza67.mcp.protocol.mcp.NotificationMeta
import io.github.taza67.mcp.protocol.mcp.NotificationParams
import io.github.taza67.mcp.protocol.mcp.RequestParams
import io.github.taza67.mcp.protocol.mcp.Result
import io.github.taza67.mcp.protocol.mcp.ResultMeta
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
        fields = JsonObject(Map("cursor" -> JsonString("abc")))
      )
    )(Params.fromRequestParams, Params.toRequestParams)
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
