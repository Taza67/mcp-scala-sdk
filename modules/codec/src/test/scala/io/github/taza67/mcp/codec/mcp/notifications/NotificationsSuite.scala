package io.github.taza67.mcp.codec.mcp.notifications

import io.github.taza67.mcp.protocol.json.JsonBool
import io.github.taza67.mcp.protocol.json.JsonNull
import io.github.taza67.mcp.protocol.json.JsonNumber
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.mcp.MetaObject
import io.github.taza67.mcp.protocol.mcp.NotificationMeta
import io.github.taza67.mcp.protocol.mcp.notifications.CancelledNotification
import io.github.taza67.mcp.protocol.mcp.notifications.CancelledNotificationParams
import io.github.taza67.mcp.protocol.jsonrpc.NumberRequestId
import io.github.taza67.mcp.protocol.jsonrpc.StringRequestId
import munit.FunSuite



class NotificationsSuite extends FunSuite {

  private def cancelledMessage(params: JsonObject): JsonObject =
    JsonObject(
      Map(
        "jsonrpc" -> JsonString("2.0"),
        "method"  -> JsonString("notifications/cancelled"),
        "params"  -> params
      )
    )

  test("literal cancelled notification decodes and re-encodes exactly") {
    val literal = cancelledMessage(
      JsonObject(
        Map(
          "requestId" -> JsonNumber(1),
          "reason"    -> JsonString("done"),
          "_meta"     -> JsonObject(
            Map(
              "io.modelcontextprotocol/subscriptionId" -> JsonString("sub-9"),
              "x-ext"                                  -> JsonString("e1")
            )
          )
        )
      )
    )
    val expected = CancelledNotification(
      params = CancelledNotificationParams(
        requestId = NumberRequestId(1L),
        reason = Some("done"),
        meta = Some(
          NotificationMeta(
            subscriptionId = Some(StringRequestId("sub-9")),
            extensions = MetaObject(Map("x-ext" -> JsonString("e1")))
          )
        )
      )
    )

    assertEquals(Notifications.toCancelledNotification(literal), Right(expected))
    assertEquals(Notifications.fromCancelledNotification(expected), literal)
  }

  test("string requestId with omitted reason and meta omits the keys") {
    val literal = cancelledMessage(
      JsonObject(Map("requestId" -> JsonString("abc-1")))
    )
    val expected = CancelledNotification(
      params = CancelledNotificationParams(requestId = StringRequestId("abc-1"))
    )

    assertEquals(Notifications.toCancelledNotification(literal), Right(expected))
    val encoded = Notifications.fromCancelledNotification(expected)
    val params = encoded.value("params").asInstanceOf[JsonObject]
    assert(!params.value.contains("reason"))
    assert(!params.value.contains("_meta"))
    assertEquals(encoded, literal)
  }

  test("invalid requestId and reason members reject without echoing values") {
    val secret = "secret-id"
    val cases = List(
      JsonObject(Map("reason" -> JsonString("x"))),
      JsonObject(Map("requestId" -> JsonNull)),
      JsonObject(Map("requestId" -> JsonBool(true))),
      JsonObject(Map("requestId" -> JsonObject(Map("s" -> JsonString(secret))))),
      JsonObject(Map("requestId" -> JsonString(""), "reason" -> JsonNumber(2))),
      JsonObject(Map("requestId" -> JsonNumber(3), "reason" -> JsonNumber(2)))
    )
    cases.foreach { params =>
      val decoded = Notifications.toCancelledNotification(cancelledMessage(params))
      assert(decoded.isLeft, s"expected rejection for $params")
      decoded.left.foreach { error =>
        assert(!error.message.contains(secret), error.message)
      }
    }
  }

  test("wrong method, missing params, and non-notification shapes reject") {
    val params = JsonObject(Map("requestId" -> JsonNumber(1)))
    val cases = List(
      JsonObject(
        Map(
          "jsonrpc" -> JsonString("2.0"),
          "method"  -> JsonString("notifications/progress"),
          "params"  -> params
        )
      ),
      JsonObject(
        Map(
          "jsonrpc" -> JsonString("2.0"),
          "method"  -> JsonString("notifications/cancelled")
        )
      ),
      JsonObject(
        Map(
          "jsonrpc" -> JsonString("2.0"),
          "id"      -> JsonNumber(1),
          "method"  -> JsonString("notifications/cancelled"),
          "params"  -> params
        )
      ),
      JsonObject(
        Map(
          "jsonrpc" -> JsonString("2.0"),
          "id"      -> JsonNumber(1),
          "result"  -> JsonObject(Map("resultType" -> JsonString("complete")))
        )
      )
    )
    cases.foreach { message =>
      assert(
        Notifications.toCancelledNotification(message).isLeft,
        s"expected rejection for $message"
      )
    }
  }

  test("notification meta preserves numeric subscription ids and extensions") {
    val literal = cancelledMessage(
      JsonObject(
        Map(
          "requestId" -> JsonString("req-7"),
          "_meta"     -> JsonObject(
            Map(
              "io.modelcontextprotocol/subscriptionId" -> JsonNumber(7),
              "x-ext"                                  -> JsonString("e2")
            )
          )
        )
      )
    )

    assertEquals(
      Notifications.toCancelledNotification(literal),
      Right(
        CancelledNotification(
          params = CancelledNotificationParams(
            requestId = StringRequestId("req-7"),
            meta = Some(
              NotificationMeta(
                subscriptionId = Some(NumberRequestId(7L)),
                extensions = MetaObject(Map("x-ext" -> JsonString("e2")))
              )
            )
          )
        )
      )
    )
  }
}
