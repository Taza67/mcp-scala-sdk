package io.github.taza67.mcp.codec.mcp.notifications

import io.github.taza67.mcp.protocol.json.JsonBool
import io.github.taza67.mcp.protocol.json.JsonNull
import io.github.taza67.mcp.protocol.json.JsonNumber
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.mcp.MetaObject
import io.github.taza67.mcp.protocol.mcp.NotificationMeta
import io.github.taza67.mcp.protocol.mcp.NumberProgressToken
import io.github.taza67.mcp.protocol.mcp.StringProgressToken
import io.github.taza67.mcp.protocol.mcp.WarningLoggingLevel
import io.github.taza67.mcp.protocol.mcp.notifications.CancelledNotification
import io.github.taza67.mcp.protocol.mcp.notifications.CancelledNotificationParams
import io.github.taza67.mcp.protocol.mcp.notifications.LoggingMessageNotification
import io.github.taza67.mcp.protocol.mcp.notifications.LoggingMessageNotificationParams
import io.github.taza67.mcp.protocol.mcp.notifications.ProgressNotification
import io.github.taza67.mcp.protocol.mcp.notifications.ProgressNotificationParams
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

  private def progressMessage(params: JsonObject): JsonObject =
    JsonObject(
      Map(
        "jsonrpc" -> JsonString("2.0"),
        "method"  -> JsonString("notifications/progress"),
        "params"  -> params
      )
    )

  test("literal progress notification with string token decodes and re-encodes exactly") {
    val literal = progressMessage(
      JsonObject(
        Map(
          "progressToken" -> JsonString("tok-1"),
          "progress"      -> JsonNumber(0.5),
          "total"         -> JsonNumber(2),
          "message"       -> JsonString("halfway"),
          "_meta"         -> JsonObject(
            Map("x-ext" -> JsonString("e1"))
          )
        )
      )
    )
    val expected = ProgressNotification(
      params = ProgressNotificationParams(
        progressToken = StringProgressToken("tok-1"),
        progress = 0.5,
        total = Some(2.0),
        message = Some("halfway"),
        meta = Some(
          NotificationMeta(extensions = MetaObject(Map("x-ext" -> JsonString("e1"))))
        )
      )
    )

    assertEquals(Notifications.toProgressNotification(literal), Right(expected))
    assertEquals(Notifications.fromProgressNotification(expected), literal)
  }

  test("progress notification with numeric token and absent optionals omits the keys") {
    val literal = progressMessage(
      JsonObject(
        Map(
          "progressToken" -> JsonNumber(42),
          "progress"      -> JsonNumber(3)
        )
      )
    )
    val expected = ProgressNotification(
      params = ProgressNotificationParams(
        progressToken = NumberProgressToken(42L),
        progress = 3.0
      )
    )

    assertEquals(Notifications.toProgressNotification(literal), Right(expected))
    assertEquals(Notifications.fromProgressNotification(expected), literal)
  }

  test("progress notification rejects missing, null, wrong-typed, and non-finite members") {
    val secret = "secret-token"
    val cases = List(
      JsonObject(Map("progress" -> JsonNumber(1))),
      JsonObject(Map("progressToken" -> JsonString("t"))),
      JsonObject(Map("progressToken" -> JsonNull, "progress" -> JsonNumber(1))),
      JsonObject(Map("progressToken" -> JsonString("t"), "progress" -> JsonNull)),
      JsonObject(Map("progressToken" -> JsonString("t"), "progress" -> JsonString("x"))),
      JsonObject(Map("progressToken" -> JsonString("t"), "progress" -> JsonNumber(1), "total" -> JsonNull)),
      JsonObject(Map("progressToken" -> JsonString("t"), "progress" -> JsonNumber(1), "message" -> JsonNumber(2))),
      JsonObject(Map("progressToken" -> JsonObject(Map("s" -> JsonString(secret))), "progress" -> JsonNumber(1))),
      // Beyond Double range: converts to a non-finite value.
      JsonObject(
        Map(
          "progressToken" -> JsonString("t"),
          "progress"      -> JsonNumber(BigDecimal("1e10000"))
        )
      ),
      JsonObject(
        Map(
          "progressToken" -> JsonString("t"),
          "progress"      -> JsonNumber(1),
          "total"         -> JsonNumber(BigDecimal("1e10000"))
        )
      )
    )
    cases.foreach { params =>
      val decoded = Notifications.toProgressNotification(progressMessage(params))
      assert(decoded.isLeft, s"expected rejection for $params")
      decoded.left.foreach { error =>
        assert(!error.message.contains(secret), error.message)
      }
    }
  }

  test("progress params encoding rejects non-finite doubles fail-fast") {
    assert(
      intercept[IllegalArgumentException](
        Notifications.fromProgressNotificationParams(
          ProgressNotificationParams(StringProgressToken("t"), Double.NaN)
        )
      ).getMessage.contains("finite")
    )
    assert(
      intercept[IllegalArgumentException](
        Notifications.fromProgressNotificationParams(
          ProgressNotificationParams(
            StringProgressToken("t"),
            1.0,
            total = Some(Double.PositiveInfinity)
          )
        )
      ).getMessage.contains("finite")
    )
  }

  private def loggingMessage(params: JsonObject): JsonObject =
    JsonObject(
      Map(
        "jsonrpc" -> JsonString("2.0"),
        "method"  -> JsonString("notifications/message"),
        "params"  -> params
      )
    )

  test("literal logging message notification decodes and re-encodes exactly") {
    val literal = loggingMessage(
      JsonObject(
        Map(
          "level"  -> JsonString("warning"),
          "data"   -> JsonObject(Map("detail" -> JsonString("disk low"))),
          "logger" -> JsonString("sys"),
          "_meta"  -> JsonObject(
            Map("io.modelcontextprotocol/subscriptionId" -> JsonString("sub-1"))
          )
        )
      )
    )
    val expected = LoggingMessageNotification(
      params = LoggingMessageNotificationParams(
        level = WarningLoggingLevel,
        data = JsonObject(Map("detail" -> JsonString("disk low"))),
        logger = Some("sys"),
        meta = Some(NotificationMeta(subscriptionId = Some(StringRequestId("sub-1"))))
      )
    )

    assertEquals(Notifications.toLoggingMessageNotification(literal), Right(expected))
    assertEquals(Notifications.fromLoggingMessageNotification(expected), literal)
  }

  test("logging message accepts any JSON data including null and omits absent logger") {
    val literal = loggingMessage(
      JsonObject(
        Map(
          "level" -> JsonString("warning"),
          "data"  -> JsonNull
        )
      )
    )
    val expected = LoggingMessageNotification(
      params = LoggingMessageNotificationParams(
        level = WarningLoggingLevel,
        data = JsonNull
      )
    )

    assertEquals(Notifications.toLoggingMessageNotification(literal), Right(expected))
    assertEquals(Notifications.fromLoggingMessageNotification(expected), literal)
  }

  test("logging message rejects unknown level without echoing and invalid members") {
    val secret = "not-a-level-secret"
    val cases = List(
      JsonObject(Map("level" -> JsonString(secret), "data" -> JsonNull)),
      JsonObject(Map("level" -> JsonNumber(3), "data" -> JsonNull)),
      JsonObject(Map("data" -> JsonNull)),
      JsonObject(Map("level" -> JsonString("info"))),
      JsonObject(
        Map(
          "level"  -> JsonString("info"),
          "data"   -> JsonNull,
          "logger" -> JsonNumber(9)
        )
      )
    )
    cases.foreach { params =>
      val decoded = Notifications.toLoggingMessageNotification(loggingMessage(params))
      assert(decoded.isLeft, s"expected rejection for $params")
      decoded.left.foreach { error =>
        assert(!error.message.contains(secret), error.message)
      }
    }
    assertEquals(
      Notifications.toLoggingMessageNotification(
        loggingMessage(
          JsonObject(Map("level" -> JsonString(secret), "data" -> JsonNull))
        )
      ).left.map(_.message),
      Left("Invalid logging level")
    )
  }
}
