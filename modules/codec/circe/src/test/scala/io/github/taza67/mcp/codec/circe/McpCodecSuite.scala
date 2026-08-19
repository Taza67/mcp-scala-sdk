package io.github.taza67.mcp.codec.circe

import io.github.taza67.mcp.codec.CodecAssertions
import io.github.taza67.mcp.codec.TestSupport
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.jsonrpc.ApplicationError
import io.github.taza67.mcp.protocol.jsonrpc.Method
import io.github.taza67.mcp.protocol.jsonrpc.ParseError
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



class McpCodecSuite extends FunSuite with CodecAssertions {

  test("Invalid JSON returns an error") {
    assert(McpCodec.MessageDecoder.decode("{").isLeft)
  }

  test("McpRequest decode rejects params without _meta") {
    val raw =
      s"""{"jsonrpc":"2.0","method":"${Tools.list.value}","id":"1","params":{"cursor":"abc"}}"""
    assert(McpCodec.MessageDecoder.decode(raw).isLeft)
  }

  test("McpRequest round-trips plain RequestParams") {
    assertRoundTrip[McpMessage, String](
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
    )(McpCodec.MessageEncoder.encode, McpCodec.MessageDecoder.decode)
  }

  test("McpRequest round-trips paginated params with cursor") {
    assertRoundTrip[McpMessage, String](
      McpRequest(
        method = Tools.list,
        id = StringRequestId("1"),
        params = Some(
          PaginatedRequestParams(
            meta = TestSupport.requestMeta,
            cursor = Some(Cursor("abc"))
          )
        )
      )
    )(McpCodec.MessageEncoder.encode, McpCodec.MessageDecoder.decode)
  }

  test("McpRequest round-trips paginated params without cursor") {
    assertRoundTrip[McpMessage, String](
      McpRequest(
        method = Tools.list,
        id = StringRequestId("1"),
        params = Some(PaginatedRequestParams(meta = TestSupport.requestMeta))
      )
    )(McpCodec.MessageEncoder.encode, McpCodec.MessageDecoder.decode)
  }

  test("McpNotification round-trips without _meta") {
    assertRoundTrip[McpMessage, String](
      McpNotification(
        method = Method("notifications/initialized"),
        params = Some(NotificationParams())
      )
    )(McpCodec.MessageEncoder.encode, McpCodec.MessageDecoder.decode)
  }

  test("McpSuccessResponse round-trips with resultType") {
    assertRoundTrip[McpMessage, String](
      McpSuccessResponse(
        result = Result(
          resultType = CompleteResultType,
          fields = JsonObject(Map("ok" -> JsonString("yes")))
        ),
        id = StringRequestId("1")
      )
    )(McpCodec.MessageEncoder.encode, McpCodec.MessageDecoder.decode)
  }

  test("McpErrorResponse round-trips") {
    assertRoundTrip[McpMessage, String](
      McpErrorResponse(
        error = ApplicationError(code = 42, message = "boom"),
        id = StringRequestId("1")
      )
    )(McpCodec.MessageEncoder.encode, McpCodec.MessageDecoder.decode)
  }

  test("Uncorrelated McpErrorResponse encodes to wire text without id") {
    val response: McpMessage = McpErrorResponse(
      error = ParseError(message = "bad json", data = None)
    )
    val encoded = McpCodec.MessageEncoder.encode(response)
    assert(!encoded.contains("\"id\":"))
    assertEquals(McpCodec.MessageDecoder.decode(encoded), Right(response))
  }

  test("McpErrorResponse decodes wire text without id and re-encodes without id") {
    val raw = """{"jsonrpc":"2.0","error":{"code":-32700,"message":"bad json"}}"""
    val decoded = McpCodec.MessageDecoder.decode(raw)
    assert(decoded.isRight)
    decoded.foreach { message =>
      assert(!McpCodec.MessageEncoder.encode(message).contains("\"id\":"))
    }
  }

  test("McpErrorResponse decodes wire text with null id as uncorrelated") {
    val raw = """{"jsonrpc":"2.0","error":{"code":-32700,"message":"bad json"},"id":null}"""
    val decoded = McpCodec.MessageDecoder.decode(raw)
    assert(decoded.isRight)
    decoded.foreach { message =>
      assert(!McpCodec.MessageEncoder.encode(message).contains("\"id\":"))
    }
  }
}
