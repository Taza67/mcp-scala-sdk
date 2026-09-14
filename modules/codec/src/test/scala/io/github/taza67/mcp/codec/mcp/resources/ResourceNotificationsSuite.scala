package io.github.taza67.mcp.codec.mcp.resources

import io.github.taza67.mcp.protocol.json.JsonNull
import io.github.taza67.mcp.protocol.json.JsonNumber
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.mcp.MetaObject
import io.github.taza67.mcp.protocol.mcp.NotificationMeta
import io.github.taza67.mcp.protocol.mcp.NotificationParams
import io.github.taza67.mcp.protocol.mcp.resources.ResourceListChangedNotification
import io.github.taza67.mcp.protocol.mcp.resources.ResourceUpdatedNotification
import io.github.taza67.mcp.protocol.mcp.resources.ResourceUpdatedNotificationParams
import io.github.taza67.mcp.protocol.jsonrpc.StringRequestId
import munit.FunSuite



class ResourceNotificationsSuite extends FunSuite {

  private def notification(method: String, params: Option[JsonObject]): JsonObject =
    JsonObject(
      Map(
        "jsonrpc" -> JsonString("2.0"),
        "method"  -> JsonString(method)
      ) ++ params.map("params" -> _)
    )

  test("absent params decodes to None and re-encodes without the member") {
    val literal = notification("notifications/resources/list_changed", None)
    val expected = ResourceListChangedNotification()

    assertEquals(Resources.toResourceListChangedNotification(literal), Right(expected))
    assertEquals(Resources.fromResourceListChangedNotification(expected), literal)
  }

  test("empty params object decodes to Some(empty) distinct from absent") {
    val literal = notification(
      "notifications/resources/list_changed",
      Some(JsonObject(Map.empty))
    )
    val expected = ResourceListChangedNotification(params = Some(NotificationParams()))

    assertEquals(Resources.toResourceListChangedNotification(literal), Right(expected))
    assertEquals(Resources.fromResourceListChangedNotification(expected), literal)
  }

  test("wrong method, request/response shapes, and null params reject") {
    val cases = List(
      notification("notifications/tools/list_changed", None),
      JsonObject(
        Map(
          "jsonrpc" -> JsonString("2.0"),
          "id"      -> JsonNumber(1),
          "method"  -> JsonString("notifications/resources/list_changed")
        )
      ),
      JsonObject(
        Map(
          "jsonrpc" -> JsonString("2.0"),
          "id"      -> JsonNumber(1),
          "result"  -> JsonObject(Map("resultType" -> JsonString("complete")))
        )
      ),
      JsonObject(
        Map(
          "jsonrpc" -> JsonString("2.0"),
          "method"  -> JsonString("notifications/resources/list_changed"),
          "params"  -> JsonNull
        )
      )
    )
    cases.foreach { message =>
      assert(
        Resources.toResourceListChangedNotification(message).isLeft,
        s"expected rejection for $message"
      )
    }
  }

  test("literal resource-updated notification decodes and re-encodes exactly") {
    val literal = notification(
      "notifications/resources/updated",
      Some(
        JsonObject(
          Map(
            "uri"   -> JsonString("file:///project/a.txt"),
            "_meta" -> JsonObject(
              Map(
                "io.modelcontextprotocol/subscriptionId" -> JsonString("sub-5"),
                "x-ext"                                  -> JsonString("e")
              )
            )
          )
        )
      )
    )
    val expected = ResourceUpdatedNotification(
      params = ResourceUpdatedNotificationParams(
        uri = "file:///project/a.txt",
        meta = Some(
          NotificationMeta(
            subscriptionId = Some(StringRequestId("sub-5")),
            extensions = MetaObject(Map("x-ext" -> JsonString("e")))
          )
        )
      )
    )

    assertEquals(Resources.toResourceUpdatedNotification(literal), Right(expected))
    assertEquals(Resources.fromResourceUpdatedNotification(expected), literal)
  }

  test("resource-updated rejects missing, null, and wrong-typed uri and missing params") {
    val secret = "file:///secret"
    val cases = List(
      notification("notifications/resources/updated", None),
      notification(
        "notifications/resources/updated",
        Some(JsonObject(Map.empty))
      ),
      notification(
        "notifications/resources/updated",
        Some(JsonObject(Map("uri" -> JsonNull)))
      ),
      notification(
        "notifications/resources/updated",
        Some(JsonObject(Map("uri" -> JsonNumber(3))))
      ),
      notification(
        "notifications/resources/updated",
        Some(JsonObject(Map("uri" -> JsonObject(Map("s" -> JsonString(secret))))))
      ),
      notification(
        "notifications/tools/list_changed",
        Some(JsonObject(Map("uri" -> JsonString("file:///a"))))
      )
    )
    cases.foreach { message =>
      val decoded = Resources.toResourceUpdatedNotification(message)
      assert(decoded.isLeft, s"expected rejection for $message")
      decoded.left.foreach { error =>
        assert(!error.message.contains(secret), error.message)
      }
    }
  }
}
