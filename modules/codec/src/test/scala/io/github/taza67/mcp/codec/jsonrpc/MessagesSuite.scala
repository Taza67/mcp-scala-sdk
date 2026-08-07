package io.github.taza67.mcp.codec.jsonrpc

import io.github.taza67.mcp.codec.CodecAssertions
import io.github.taza67.mcp.protocol.json.JsonNull
import io.github.taza67.mcp.protocol.json.JsonNumber
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.jsonrpc.ApplicationError
import io.github.taza67.mcp.protocol.jsonrpc.ErrorCode
import io.github.taza67.mcp.protocol.jsonrpc.ErrorResponse
import io.github.taza67.mcp.protocol.jsonrpc.Message
import io.github.taza67.mcp.protocol.jsonrpc.Method
import io.github.taza67.mcp.protocol.jsonrpc.MethodNotFoundError
import io.github.taza67.mcp.protocol.jsonrpc.Notification
import io.github.taza67.mcp.protocol.jsonrpc.NumberRequestId
import io.github.taza67.mcp.protocol.jsonrpc.ParseError
import io.github.taza67.mcp.protocol.jsonrpc.Request
import io.github.taza67.mcp.protocol.jsonrpc.StringRequestId
import io.github.taza67.mcp.protocol.jsonrpc.SuccessResponse
import munit.FunSuite



class MessagesSuite extends FunSuite with CodecAssertions {

  test("Non-object message returns an error") {
    assert(Messages.toMessage(JsonString("nope")).isLeft)
  }

  test("Ambiguous JSON-RPC object returns an error") {
    val ambiguous = JsonObject(
      Map(
        "jsonrpc" -> JsonString("2.0"),
        "method" -> JsonString("x"),
        "result" -> JsonNumber(1),
        "id" -> JsonString("1")
      )
    )
    assert(Messages.toMessage(ambiguous).isLeft)
  }

  test("Invalid version and blank method return errors") {
    val badVersion = JsonObject(
      Map(
        "jsonrpc" -> JsonString("1.0"),
        "method" -> JsonString("ping"),
        "id" -> JsonString("1")
      )
    )
    assert(Messages.toMessage(badVersion).isLeft)

    val blankMethod = JsonObject(
      Map(
        "jsonrpc" -> JsonString("2.0"),
        "method" -> JsonString("   "),
        "id" -> JsonString("1")
      )
    )
    assert(Messages.toMessage(blankMethod).isLeft)
  }

  test("Request round-trips with string id and params") {
    assertRoundTrip[Message, JsonObject](
      Request(
        method = Method("tools/list"),
        id = StringRequestId("1"),
        params = Some(JsonObject(Map("cursor" -> JsonString("abc"))))
      )
    )(Messages.fromMessage, Messages.toMessage)
  }

  test("Request round-trips with number id and omitted params") {
    assertRoundTrip[Message, JsonObject](
      Request(method = Method("ping"), id = NumberRequestId(42L))
    )(Messages.fromMessage, Messages.toMessage)
  }

  test("Notification round-trips") {
    assertRoundTrip[Message, JsonObject](
      Notification(method = Method("notifications/initialized"))
    )(Messages.fromMessage, Messages.toMessage)
  }

  test("Notification round-trips with params") {
    assertRoundTrip[Message, JsonObject](
      Notification(
        method = Method("notifications/message"),
        params = Some(JsonObject(Map("level" -> JsonString("info"))))
      )
    )(Messages.fromMessage, Messages.toMessage)
  }

  test("SuccessResponse round-trips") {
    assertRoundTrip[Message, JsonObject](
      SuccessResponse(
        result = JsonObject(Map("ok" -> JsonString("yes"))),
        id = StringRequestId("1")
      )
    )(Messages.fromMessage, Messages.toMessage)
  }

  test("ErrorResponse round-trips ApplicationError with data") {
    assertRoundTrip[Message, JsonObject](
      ErrorResponse(
        error = ApplicationError(code = 42, message = "boom", data = Some(JsonNull)),
        id = StringRequestId("1")
      )
    )(Messages.fromMessage, Messages.toMessage)
  }

  test("ErrorResponse classifies known JSON-RPC codes") {
    assertRoundTrip[Message, JsonObject](
      ErrorResponse(
        error = ParseError(message = "bad json", data = None),
        id = NumberRequestId(7L)
      )
    )(Messages.fromMessage, Messages.toMessage)
    assertRoundTrip[Message, JsonObject](
      ErrorResponse(
        error = MethodNotFoundError(message = "nope", data = None),
        id = StringRequestId("x")
      )
    )(Messages.fromMessage, Messages.toMessage)
  }

  test("Error without data omits the field on encode") {
    val encoded = Messages.fromMessage(
      ErrorResponse(
        error = ParseError(message = "bad", data = None),
        id = StringRequestId("1")
      )
    )
    assertEquals(
      encoded.value("error"),
      JsonObject(
        Map(
          "code" -> JsonNumber(ErrorCode.ParseError),
          "message" -> JsonString("bad")
        )
      )
    )
  }
}
