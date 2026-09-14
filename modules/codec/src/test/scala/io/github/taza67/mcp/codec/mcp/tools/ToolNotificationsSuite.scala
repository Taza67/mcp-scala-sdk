package io.github.taza67.mcp.codec.mcp.tools

import io.github.taza67.mcp.protocol.json.JsonNull
import io.github.taza67.mcp.protocol.json.JsonNumber
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.mcp.MetaObject
import io.github.taza67.mcp.protocol.mcp.NotificationMeta
import io.github.taza67.mcp.protocol.mcp.NotificationParams
import io.github.taza67.mcp.protocol.mcp.tools.ToolListChangedNotification
import io.github.taza67.mcp.protocol.jsonrpc.StringRequestId
import munit.FunSuite



class ToolNotificationsSuite extends FunSuite {

  private def notification(method: String, params: Option[JsonObject]): JsonObject =
    JsonObject(
      Map(
        "jsonrpc" -> JsonString("2.0"),
        "method"  -> JsonString(method)
      ) ++ params.map("params" -> _)
    )

  test("absent params decodes to None and re-encodes without the member") {
    val literal = notification("notifications/tools/list_changed", None)
    val expected = ToolListChangedNotification()

    assertEquals(Tools.toToolListChangedNotification(literal), Right(expected))
    assertEquals(Tools.fromToolListChangedNotification(expected), literal)
  }

  test("empty params object decodes to Some(empty) distinct from absent") {
    val literal = notification(
      "notifications/tools/list_changed",
      Some(JsonObject(Map.empty))
    )
    val expected = ToolListChangedNotification(params = Some(NotificationParams()))

    assertEquals(Tools.toToolListChangedNotification(literal), Right(expected))
    assertEquals(Tools.fromToolListChangedNotification(expected), literal)
  }

  test("notification meta decodes and re-encodes exactly") {
    val literal = notification(
      "notifications/tools/list_changed",
      Some(
        JsonObject(
          Map(
            "_meta" -> JsonObject(
              Map(
                "io.modelcontextprotocol/subscriptionId" -> JsonString("sub-3"),
                "x-ext"                                  -> JsonString("e")
              )
            )
          )
        )
      )
    )
    val expected = ToolListChangedNotification(
      params = Some(
        NotificationParams(
          meta = Some(
            NotificationMeta(
              subscriptionId = Some(StringRequestId("sub-3")),
              extensions = MetaObject(Map("x-ext" -> JsonString("e")))
            )
          )
        )
      )
    )

    assertEquals(Tools.toToolListChangedNotification(literal), Right(expected))
    assertEquals(Tools.fromToolListChangedNotification(expected), literal)
  }

  test("wrong method, request/response shapes, and null params reject") {
    val cases = List(
      notification("notifications/prompts/list_changed", None),
      JsonObject(
        Map(
          "jsonrpc" -> JsonString("2.0"),
          "id"      -> JsonNumber(1),
          "method"  -> JsonString("notifications/tools/list_changed")
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
          "method"  -> JsonString("notifications/tools/list_changed"),
          "params"  -> JsonNull
        )
      )
    )
    cases.foreach { message =>
      assert(
        Tools.toToolListChangedNotification(message).isLeft,
        s"expected rejection for $message"
      )
    }
  }
}
