package io.github.taza67.mcp.codec.ziojson

import io.github.taza67.mcp.codec.CodecAssertions
import io.github.taza67.mcp.codec.TestSupport
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
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
import io.github.taza67.mcp.protocol.mcp.MetaObject
import io.github.taza67.mcp.protocol.mcp.NotificationParams
import io.github.taza67.mcp.protocol.mcp.PaginatedRequestParams
import io.github.taza67.mcp.protocol.mcp.RequestParams
import io.github.taza67.mcp.protocol.mcp.Result
import io.github.taza67.mcp.protocol.mcp.ResultMeta
import io.github.taza67.mcp.protocol.mcp.tools.Tools
import munit.FunSuite



class McpCodecSuite extends FunSuite with CodecAssertions {

  test("invalid JSON returns an error") {
    assert(McpCodec.MessageDecoder.decode("{").isLeft)
  }

  test("a request with malformed numeric id 01 rejects as invalid JSON") {
    val raw =
      """{"jsonrpc":"2.0","method":"tools/call","id":01,"params":{"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28","io.modelcontextprotocol/clientCapabilities":{}},"name":"x"}}"""
    assert(McpCodec.MessageDecoder.decode(raw).isLeft)
  }

  test("McpRequest decode rejects missing or malformed _meta") {
    List(
      s"""{"jsonrpc":"2.0","method":"${Tools.list.value}","id":"1","params":{"cursor":"abc"}}""",
      s"""{"jsonrpc":"2.0","method":"${Tools.list.value}","id":"1","params":{"_meta":{"io.modelcontextprotocol/protocolVersion":42}}}"""
    ).foreach { raw =>
      assert(McpCodec.MessageDecoder.decode(raw).isLeft, s"expected rejection for $raw")
    }
  }

  test("unknown protocol version in _meta rejects at the generic codec") {
    val raw =
      """{"jsonrpc":"2.0","method":"tools/call","id":"1","params":{"_meta":{"io.modelcontextprotocol/protocolVersion":"2099-01-01","io.modelcontextprotocol/clientCapabilities":{}},"name":"x"}}"""
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

  test("McpRequest round-trips paginated params with and without cursor") {
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

  test("McpSuccessResponse round-trips with resultType and meta extensions") {
    assertRoundTrip[McpMessage, String](
      McpSuccessResponse(
        result = Result(
          resultType = CompleteResultType,
          fields = JsonObject(Map("ok" -> JsonString("yes"))),
          meta = Some(
            ResultMeta(extensions = MetaObject(Map("x-ext" -> JsonString("1"))))
          )
        ),
        id = StringRequestId("1")
      )
    )(McpCodec.MessageEncoder.encode, McpCodec.MessageDecoder.decode)
  }

  test("uncorrelated error ids: omitted and null decode to None and re-encode omitted") {
    val response: McpMessage = McpErrorResponse(
      error = ParseError(message = "bad json", data = None)
    )
    val encoded = McpCodec.MessageEncoder.encode(response)
    assert(!encoded.contains("\"id\":"))
    assertEquals(McpCodec.MessageDecoder.decode(encoded), Right(response))

    List(
      """{"jsonrpc":"2.0","error":{"code":-32700,"message":"bad json"}}""",
      """{"jsonrpc":"2.0","error":{"code":-32700,"message":"bad json"},"id":null}"""
    ).foreach { raw =>
      val decoded = McpCodec.MessageDecoder.decode(raw)
      assert(decoded.isRight)
      decoded.foreach { message =>
        assert(!McpCodec.MessageEncoder.encode(message).contains("\"id\":"))
      }
    }
  }
}
