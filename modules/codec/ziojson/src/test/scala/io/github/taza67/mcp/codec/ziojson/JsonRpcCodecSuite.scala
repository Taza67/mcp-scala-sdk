package io.github.taza67.mcp.codec.ziojson

import io.github.taza67.mcp.codec.CodecAssertions
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.jsonrpc.ApplicationError
import io.github.taza67.mcp.protocol.jsonrpc.ErrorResponse
import io.github.taza67.mcp.protocol.jsonrpc.Message
import io.github.taza67.mcp.protocol.jsonrpc.Method
import io.github.taza67.mcp.protocol.jsonrpc.Notification
import io.github.taza67.mcp.protocol.jsonrpc.NumberRequestId
import io.github.taza67.mcp.protocol.jsonrpc.ParseError
import io.github.taza67.mcp.protocol.jsonrpc.Request
import io.github.taza67.mcp.protocol.jsonrpc.StringRequestId
import io.github.taza67.mcp.protocol.jsonrpc.SuccessResponse
import munit.FunSuite



class JsonRpcCodecSuite extends FunSuite with CodecAssertions {

  test("invalid JSON and malformed envelopes reject") {
    List(
      "{",
      """{"jsonrpc":"2.0","method":"x","result":1,"id":"1"}""",
      """{"jsonrpc":"2.0","method":"x","id":null}""",
      """{"jsonrpc":"2.0","result":1}""",
      """{"jsonrpc":"2.0","result":1,"id":null}"""
    ).foreach { raw =>
      assert(JsonRpcCodec.MessageDecoder.decode(raw).isLeft, s"expected rejection for $raw")
    }
  }

  test("Request round-trips with string and numeric ids preserved") {
    assertRoundTrip[Message, String](
      Request(
        method = Method("tools/list"),
        id = StringRequestId("1"),
        params = Some(JsonObject(Map("cursor" -> JsonString("abc"))))
      )
    )(JsonRpcCodec.MessageEncoder.encode, JsonRpcCodec.MessageDecoder.decode)
    assertRoundTrip[Message, String](
      Request(method = Method("tools/list"), id = NumberRequestId(42))
    )(JsonRpcCodec.MessageEncoder.encode, JsonRpcCodec.MessageDecoder.decode)
  }

  test("Notification round-trips") {
    assertRoundTrip[Message, String](
      Notification(method = Method("notifications/initialized"))
    )(JsonRpcCodec.MessageEncoder.encode, JsonRpcCodec.MessageDecoder.decode)
  }

  test("SuccessResponse round-trips") {
    assertRoundTrip[Message, String](
      SuccessResponse(
        result = JsonObject(Map("ok" -> JsonString("yes"))),
        id = StringRequestId("1")
      )
    )(JsonRpcCodec.MessageEncoder.encode, JsonRpcCodec.MessageDecoder.decode)
  }

  test("ErrorResponse round-trips ApplicationError with correlated id") {
    assertRoundTrip[Message, String](
      ErrorResponse(
        error = ApplicationError(code = 42, message = "boom"),
        id = StringRequestId("1")
      )
    )(JsonRpcCodec.MessageEncoder.encode, JsonRpcCodec.MessageDecoder.decode)
  }

  test("uncorrelated error ids: omitted and null decode to None and re-encode omitted") {
    val response: Message = ErrorResponse(
      error = ParseError(message = "bad json", data = None)
    )
    val encoded = JsonRpcCodec.MessageEncoder.encode(response)
    assert(!encoded.contains("\"id\":"))
    assertEquals(JsonRpcCodec.MessageDecoder.decode(encoded), Right(response))

    List(
      """{"jsonrpc":"2.0","error":{"code":-32700,"message":"bad json"}}""",
      """{"jsonrpc":"2.0","error":{"code":-32700,"message":"bad json"},"id":null}"""
    ).foreach { raw =>
      val decoded = JsonRpcCodec.MessageDecoder.decode(raw)
      assert(decoded.isRight)
      decoded.foreach { message =>
        assert(!JsonRpcCodec.MessageEncoder.encode(message).contains("\"id\":"))
      }
    }
  }
}
