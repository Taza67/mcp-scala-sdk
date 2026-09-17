package io.github.taza67.mcp.codec.mcp.subscriptions

import io.github.taza67.mcp.protocol.json.JsonArray
import io.github.taza67.mcp.protocol.json.JsonBool
import io.github.taza67.mcp.protocol.json.JsonNull
import io.github.taza67.mcp.protocol.json.JsonNumber
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.json.JsonValue
import io.github.taza67.mcp.protocol.jsonrpc.NumberRequestId
import io.github.taza67.mcp.protocol.jsonrpc.StringRequestId
import io.github.taza67.mcp.protocol.mcp.ClientCapabilities
import io.github.taza67.mcp.protocol.mcp.Implementation
import io.github.taza67.mcp.protocol.mcp.McpProtocolVersion20260728
import io.github.taza67.mcp.protocol.mcp.MetaObject
import io.github.taza67.mcp.protocol.mcp.NotificationMeta
import io.github.taza67.mcp.protocol.mcp.RequestMeta
import io.github.taza67.mcp.protocol.mcp.subscriptions.SubscriptionFilter
import io.github.taza67.mcp.protocol.mcp.subscriptions.SubscriptionsAcknowledgedNotification
import io.github.taza67.mcp.protocol.mcp.subscriptions.SubscriptionsAcknowledgedNotificationParams
import io.github.taza67.mcp.protocol.mcp.subscriptions.SubscriptionsListenRequest
import io.github.taza67.mcp.protocol.mcp.subscriptions.SubscriptionsListenRequestParams
import io.github.taza67.mcp.protocol.mcp.subscriptions.SubscriptionsListenResult
import io.github.taza67.mcp.protocol.mcp.subscriptions.SubscriptionsListenResultMeta
import io.github.taza67.mcp.protocol.mcp.subscriptions.SubscriptionsListenResultResponse
import munit.FunSuite



class SubscriptionsSuite extends FunSuite {

  private val version = "2026-07-28"

  private val requestMeta = RequestMeta(
    protocolVersion = McpProtocolVersion20260728,
    clientCapabilities = ClientCapabilities()
  )

  private val requestMetaJson = JsonObject(
    Map(
      "io.modelcontextprotocol/protocolVersion"     -> JsonString(version),
      "io.modelcontextprotocol/clientCapabilities"  -> JsonObject(Map.empty)
    )
  )

  private def envelope(
      method: String,
      id: Option[JsonValue],
      params: Option[JsonObject]
  ): JsonObject =
    JsonObject(
      Map("jsonrpc" -> JsonString("2.0"), "method" -> JsonString(method)) ++
        id.map("id" -> _) ++ params.map("params" -> _)
    )

  test("subscription filter preserves absent, false, and empty-list optionals") {
    val empty = SubscriptionFilter()
    assertEquals(
      Subscriptions.toSubscriptionFilter(JsonObject(Map.empty)),
      Right(empty)
    )
    assertEquals(
      Subscriptions.fromSubscriptionFilter(empty),
      JsonObject(Map.empty)
    )

    val literal = JsonObject(
      Map(
        "toolsListChanged"      -> JsonBool(false),
        "resourceSubscriptions" -> JsonArray(List.empty)
      )
    )
    val expected = SubscriptionFilter(
      toolsListChanged = Some(false),
      resourceSubscriptions = Some(List.empty)
    )
    assertEquals(Subscriptions.toSubscriptionFilter(literal), Right(expected))
    assertEquals(Subscriptions.fromSubscriptionFilter(expected), literal)

    val full = JsonObject(
      Map(
        "toolsListChanged"      -> JsonBool(true),
        "promptsListChanged"    -> JsonBool(true),
        "resourcesListChanged"  -> JsonBool(false),
        "resourceSubscriptions" -> JsonArray(List(JsonString("file:///a")))
      )
    )
    val fullExpected = SubscriptionFilter(
      toolsListChanged = Some(true),
      promptsListChanged = Some(true),
      resourcesListChanged = Some(false),
      resourceSubscriptions = Some(List("file:///a"))
    )
    assertEquals(Subscriptions.toSubscriptionFilter(full), Right(fullExpected))
    assertEquals(Subscriptions.fromSubscriptionFilter(fullExpected), full)
  }

  test("subscription filter rejects nulls and wrong-typed members") {
    val cases = List(
      JsonObject(Map("toolsListChanged" -> JsonNull)),
      JsonObject(Map("toolsListChanged" -> JsonString("yes"))),
      JsonObject(Map("resourceSubscriptions" -> JsonNull)),
      JsonObject(Map("resourceSubscriptions" -> JsonString("file:///a"))),
      JsonObject(Map("resourceSubscriptions" -> JsonArray(List(JsonNumber(1)))))
    )
    cases.foreach { filter =>
      assert(
        Subscriptions.toSubscriptionFilter(filter).isLeft,
        s"expected rejection for $filter"
      )
    }
  }

  test("literal listen request decodes and re-encodes exactly") {
    val literal = envelope(
      "subscriptions/listen",
      Some(JsonString("listen-1")),
      Some(
        JsonObject(
          Map(
            "notifications" -> JsonObject(
              Map(
                "toolsListChanged"      -> JsonBool(true),
                "resourceSubscriptions" -> JsonArray(List(JsonString("file:///a")))
              )
            ),
            "_meta" -> requestMetaJson
          )
        )
      )
    )
    val expected = SubscriptionsListenRequest(
      id = StringRequestId("listen-1"),
      params = SubscriptionsListenRequestParams(
        meta = requestMeta,
        notifications = SubscriptionFilter(
          toolsListChanged = Some(true),
          resourceSubscriptions = Some(List("file:///a"))
        )
      )
    )

    assertEquals(Subscriptions.toSubscriptionsListenRequest(literal), Right(expected))
    assertEquals(Subscriptions.fromSubscriptionsListenRequest(expected), literal)
  }

  test("listen request rejects missing or malformed notifications and meta") {
    val cases = List(
      envelope(
        "subscriptions/listen",
        Some(JsonNumber(1)),
        Some(JsonObject(Map("_meta" -> requestMetaJson)))
      ),
      envelope(
        "subscriptions/listen",
        Some(JsonNumber(1)),
        Some(
          JsonObject(
            Map(
              "notifications" -> JsonNull,
              "_meta"         -> requestMetaJson
            )
          )
        )
      ),
      envelope(
        "subscriptions/listen",
        Some(JsonNumber(1)),
        Some(JsonObject(Map("notifications" -> JsonObject(Map.empty))))
      ),
      envelope(
        "subscriptions/listen",
        Some(JsonNumber(1)),
        None
      ),
      envelope(
        "notifications/subscriptions/acknowledged",
        Some(JsonNumber(1)),
        Some(
          JsonObject(
            Map(
              "notifications" -> JsonObject(Map.empty),
              "_meta"         -> requestMetaJson
            )
          )
        )
      )
    )
    cases.foreach { message =>
      assert(
        Subscriptions.toSubscriptionsListenRequest(message).isLeft,
        s"expected rejection for $message"
      )
    }
  }

  test("literal acknowledged notification decodes and re-encodes exactly") {
    val literal = envelope(
      "notifications/subscriptions/acknowledged",
      None,
      Some(
        JsonObject(
          Map(
            "notifications" -> JsonObject(
              Map("promptsListChanged" -> JsonBool(true))
            ),
            "_meta" -> JsonObject(
              Map(
                "io.modelcontextprotocol/subscriptionId" -> JsonNumber(7)
              )
            )
          )
        )
      )
    )
    val expected = SubscriptionsAcknowledgedNotification(
      params = SubscriptionsAcknowledgedNotificationParams(
        notifications = SubscriptionFilter(promptsListChanged = Some(true)),
        meta = Some(NotificationMeta(subscriptionId = Some(NumberRequestId(7L))))
      )
    )

    assertEquals(
      Subscriptions.toSubscriptionsAcknowledgedNotification(literal),
      Right(expected)
    )
    assertEquals(
      Subscriptions.fromSubscriptionsAcknowledgedNotification(expected),
      literal
    )
  }

  test("acknowledged notification rejects missing notifications member") {
    val cases = List(
      envelope(
        "notifications/subscriptions/acknowledged",
        None,
        Some(JsonObject(Map.empty))
      ),
      envelope(
        "notifications/subscriptions/acknowledged",
        None,
        Some(JsonObject(Map("notifications" -> JsonBool(true))))
      ),
      envelope("notifications/subscriptions/acknowledged", None, None)
    )
    cases.foreach { message =>
      assert(
        Subscriptions.toSubscriptionsAcknowledgedNotification(message).isLeft,
        s"expected rejection for $message"
      )
    }
  }

  private val subMetaJson = JsonObject(
    Map(
      "io.modelcontextprotocol/subscriptionId" -> JsonString("sub-9"),
      "io.modelcontextprotocol/serverInfo"     -> JsonObject(
        Map("name" -> JsonString("srv"), "version" -> JsonString("1.0.0"))
      ),
      "x-ext" -> JsonNumber(1)
    )
  )

  test("listen result meta decodes subscription id, server info, and extensions") {
    val expected = SubscriptionsListenResultMeta(
      subscriptionId = StringRequestId("sub-9"),
      serverInfo = Some(Implementation(name = "srv", version = "1.0.0")),
      extensions = MetaObject(Map("x-ext" -> JsonNumber(1)))
    )

    assertEquals(
      Subscriptions.toSubscriptionsListenResultMeta(subMetaJson),
      Right(expected)
    )
    assertEquals(Subscriptions.fromSubscriptionsListenResultMeta(expected), subMetaJson)

    val numeric = JsonObject(
      Map("io.modelcontextprotocol/subscriptionId" -> JsonNumber(8))
    )
    assertEquals(
      Subscriptions.toSubscriptionsListenResultMeta(numeric),
      Right(SubscriptionsListenResultMeta(subscriptionId = NumberRequestId(8L)))
    )
  }

  test("listen result meta sanitizes reserved-key extension shadowing on encode") {
    val meta = SubscriptionsListenResultMeta(
      subscriptionId = StringRequestId("sub-9"),
      extensions = MetaObject(
        Map(
          "io.modelcontextprotocol/subscriptionId" -> JsonString("spoof"),
          "io.modelcontextprotocol/serverInfo"     -> JsonObject(Map.empty),
          "x-ext"                                  -> JsonNumber(2)
        )
      )
    )
    val encoded = Subscriptions.fromSubscriptionsListenResultMeta(meta)
    assertEquals(
      encoded.value("io.modelcontextprotocol/subscriptionId"),
      JsonString("sub-9")
    )
    assert(!encoded.value.contains("io.modelcontextprotocol/serverInfo"))
    assertEquals(encoded.value("x-ext"), JsonNumber(2))
  }

  test("listen result meta rejects missing, blank, and wrong-typed subscription ids") {
    val cases = List(
      JsonObject(Map.empty),
      JsonObject(Map("io.modelcontextprotocol/subscriptionId" -> JsonNull)),
      JsonObject(Map("io.modelcontextprotocol/subscriptionId" -> JsonString(""))),
      JsonObject(Map("io.modelcontextprotocol/subscriptionId" -> JsonBool(true)))
    )
    cases.foreach { meta =>
      assert(
        Subscriptions.toSubscriptionsListenResultMeta(meta).isLeft,
        s"expected rejection for $meta"
      )
    }
  }

  test("full listen result body decodes and re-encodes exactly") {
    val literal = JsonObject(
      Map(
        "resultType" -> JsonString("complete"),
        "_meta"      -> subMetaJson
      )
    )
    val expected = SubscriptionsListenResult(
      meta = SubscriptionsListenResultMeta(
        subscriptionId = StringRequestId("sub-9"),
        serverInfo = Some(Implementation(name = "srv", version = "1.0.0")),
        extensions = MetaObject(Map("x-ext" -> JsonNumber(1)))
      )
    )

    assertEquals(Subscriptions.toSubscriptionsListenResult(literal), Right(expected))
    assertEquals(Subscriptions.fromSubscriptionsListenResult(expected), literal)
    // Absent resultType defaults to complete for legacy servers.
    assert(
      !Subscriptions
        .toSubscriptionsListenResult(JsonObject(Map("_meta" -> subMetaJson)))
        .isLeft
    )
    assert(
      Subscriptions
        .toSubscriptionsListenResult(
          JsonObject(Map("resultType" -> JsonString("complete")))
        )
        .isLeft
    )
  }

  test("listen result response decodes and re-encodes exactly with matching ids") {
    val literal = JsonObject(
      Map(
        "jsonrpc" -> JsonString("2.0"),
        "id"      -> JsonString("sub-9"),
        "result"  -> JsonObject(
          Map(
            "resultType" -> JsonString("complete"),
            "_meta"      -> subMetaJson
          )
        )
      )
    )
    val expected = SubscriptionsListenResultResponse(
      result = SubscriptionsListenResult(
        meta = SubscriptionsListenResultMeta(
          subscriptionId = StringRequestId("sub-9"),
          serverInfo = Some(Implementation(name = "srv", version = "1.0.0")),
          extensions = MetaObject(Map("x-ext" -> JsonNumber(1)))
        )
      ),
      id = StringRequestId("sub-9")
    )

    assertEquals(
      Subscriptions.toSubscriptionsListenResultResponse(literal),
      Right(expected)
    )
    assertEquals(
      Subscriptions.fromSubscriptionsListenResultResponse(expected),
      literal
    )
  }

  test("listen result response rejects id correlation mismatch and wrong kinds") {
    val mismatched = JsonObject(
      Map(
        "jsonrpc" -> JsonString("2.0"),
        "id"      -> JsonNumber(8),
        "result"  -> JsonObject(
          Map("resultType" -> JsonString("complete"), "_meta" -> subMetaJson)
        )
      )
    )
    assertEquals(
      Subscriptions
        .toSubscriptionsListenResultResponse(mismatched)
        .left
        .map(_.message),
      Left("Invalid subscription response correlation")
    )

    assert(
      Subscriptions
        .toSubscriptionsListenResultResponse(
          JsonObject(
            Map(
              "jsonrpc" -> JsonString("2.0"),
              "method"  -> JsonString("notifications/subscriptions/acknowledged")
            )
          )
        )
        .isLeft
    )

    intercept[IllegalArgumentException](
      Subscriptions.fromSubscriptionsListenResultResponse(
        SubscriptionsListenResultResponse(
          result = SubscriptionsListenResult(
            meta = SubscriptionsListenResultMeta(
              subscriptionId = StringRequestId("sub-9")
            )
          ),
          id = NumberRequestId(8L)
        )
      )
    )
  }
}
