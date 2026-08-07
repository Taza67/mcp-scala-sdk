package io.github.taza67.mcp.codec.circe

import io.github.taza67.mcp.codec.CodecAssertions
import io.github.taza67.mcp.protocol.json.JsonNull
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.jsonrpc.ApplicationError
import io.github.taza67.mcp.protocol.jsonrpc.ErrorResponse
import io.github.taza67.mcp.protocol.jsonrpc.Message
import io.github.taza67.mcp.protocol.jsonrpc.Method
import io.github.taza67.mcp.protocol.jsonrpc.Notification
import io.github.taza67.mcp.protocol.jsonrpc.Request
import io.github.taza67.mcp.protocol.jsonrpc.StringRequestId
import io.github.taza67.mcp.protocol.jsonrpc.SuccessResponse
import munit.FunSuite



class JsonRpcCodecSuite extends FunSuite with CodecAssertions {

  test("Invalid JSON returns an error") {
    assert(JsonRpcCodec.MessageDecoder.decode("{").isLeft)
  }

  test("Ambiguous JSON-RPC object returns an error") {
    val ambiguous = """{"jsonrpc":"2.0","method":"x","result":1,"id":"1"}"""
    assert(JsonRpcCodec.MessageDecoder.decode(ambiguous).isLeft)
  }

  test("Request round-trips with string id and params") {
    assertRoundTrip[Message, String](
      Request(
        method = Method("tools/list"),
        id = StringRequestId("1"),
        params = Some(JsonObject(Map("cursor" -> JsonString("abc"))))
      )
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

  test("ErrorResponse round-trips ApplicationError with data") {
    assertRoundTrip[Message, String](
      ErrorResponse(
        error = ApplicationError(code = 42, message = "boom", data = Some(JsonNull)),
        id = StringRequestId("1")
      )
    )(JsonRpcCodec.MessageEncoder.encode, JsonRpcCodec.MessageDecoder.decode)
  }
}
