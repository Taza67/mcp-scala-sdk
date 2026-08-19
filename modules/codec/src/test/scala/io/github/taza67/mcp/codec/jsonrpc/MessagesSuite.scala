package io.github.taza67.mcp.codec.jsonrpc

import io.github.taza67.mcp.codec.CodecAssertions
import io.github.taza67.mcp.protocol.json.JsonBool
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

  test("ErrorResponse decodes omitted id and re-encodes without id") {
    val error = JsonObject(
      Map(
        "code" -> JsonNumber(ErrorCode.ParseError),
        "message" -> JsonString("bad json")
      )
    )
    val raw = JsonObject(
      Map(
        "jsonrpc" -> JsonString("2.0"),
        "error" -> error
      )
    )
    assertEquals(Messages.toErrorResponse(raw).map(Messages.fromMessage), Right(raw))
  }

  test("ErrorResponse decodes explicit null id as uncorrelated") {
    val error = JsonObject(
      Map(
        "code" -> JsonNumber(ErrorCode.ParseError),
        "message" -> JsonString("bad json")
      )
    )
    val raw = JsonObject(
      Map(
        "jsonrpc" -> JsonString("2.0"),
        "error" -> error,
        "id" -> JsonNull
      )
    )
    assertEquals(Messages.toErrorResponse(raw).map(_.id), Right(None))
    assertEquals(
      Messages.toErrorResponse(raw).map(Messages.fromMessage),
      Right(
        JsonObject(
          Map(
            "jsonrpc" -> JsonString("2.0"),
            "error" -> error
          )
        )
      )
    )
  }

  test("Uncorrelated ErrorResponse round-trips with omitted id") {
    val response: Message = ErrorResponse(
      error = ParseError(message = "bad json", data = None)
    )
    assertRoundTrip[Message, JsonObject](response)(Messages.fromMessage, Messages.toMessage)
    assert(!Messages.fromMessage(response).value.contains(Message.IdKey))
  }

  test("ErrorResponse rejects non-scalar id") {
    val raw = JsonObject(
      Map(
        "jsonrpc" -> JsonString("2.0"),
        "error" -> JsonObject(
          Map(
            "code" -> JsonNumber(ErrorCode.ParseError),
            "message" -> JsonString("bad json")
          )
        ),
        "id" -> JsonBool(true)
      )
    )
    assert(Messages.toErrorResponse(raw).isLeft)
  }

  test("ErrorResponse preserves correlated string and number ids") {
    val error = JsonObject(
      Map(
        "code" -> JsonNumber(ErrorCode.ParseError),
        "message" -> JsonString("bad json")
      )
    )
    val stringId = JsonObject(
      Map(
        "jsonrpc" -> JsonString("2.0"),
        "error" -> error,
        "id" -> JsonString("x")
      )
    )
    val numberId = JsonObject(
      Map(
        "jsonrpc" -> JsonString("2.0"),
        "error" -> error,
        "id" -> JsonNumber(7)
      )
    )
    assertEquals(Messages.toErrorResponse(stringId).map(Messages.fromMessage), Right(stringId))
    assertEquals(Messages.toErrorResponse(numberId).map(Messages.fromMessage), Right(numberId))
  }

  test("Request rejects null id") {
    val raw = JsonObject(
      Map(
        "jsonrpc" -> JsonString("2.0"),
        "method" -> JsonString("ping"),
        "id" -> JsonNull
      )
    )
    assert(Messages.toMessage(raw).isLeft)
  }

  test("Request rejects missing id") {
    val raw = JsonObject(
      Map(
        "jsonrpc" -> JsonString("2.0"),
        "method" -> JsonString("ping")
      )
    )
    assert(Messages.toRequest(raw).isLeft)
  }

  test("SuccessResponse rejects null id") {
    val raw = JsonObject(
      Map(
        "jsonrpc" -> JsonString("2.0"),
        "result" -> JsonObject(Map("ok" -> JsonString("yes"))),
        "id" -> JsonNull
      )
    )
    assert(Messages.toMessage(raw).isLeft)
  }

  test("SuccessResponse rejects missing id") {
    val raw = JsonObject(
      Map(
        "jsonrpc" -> JsonString("2.0"),
        "result" -> JsonObject(Map("ok" -> JsonString("yes")))
      )
    )
    assert(Messages.toMessage(raw).isLeft)
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
