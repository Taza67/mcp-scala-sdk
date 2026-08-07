package io.github.taza67.mcp.codec.mcp

import io.github.taza67.mcp.codec.TestSupport
import io.github.taza67.mcp.protocol.json.JsonBool
import io.github.taza67.mcp.protocol.json.JsonNumber
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.jsonrpc.StringRequestId
import io.github.taza67.mcp.protocol.mcp.ClientCapabilities
import io.github.taza67.mcp.protocol.mcp.DebugLoggingLevel
import io.github.taza67.mcp.protocol.mcp.Implementation
import io.github.taza67.mcp.protocol.mcp.McpProtocolVersion20260728
import io.github.taza67.mcp.protocol.mcp.MetaObject
import io.github.taza67.mcp.protocol.mcp.NotificationMeta
import io.github.taza67.mcp.protocol.mcp.NumberProgressToken
import io.github.taza67.mcp.protocol.mcp.PromptsCapability
import io.github.taza67.mcp.protocol.mcp.RequestMeta
import io.github.taza67.mcp.protocol.mcp.ResourcesCapability
import io.github.taza67.mcp.protocol.mcp.ResultMeta
import io.github.taza67.mcp.protocol.mcp.ServerCapabilities
import io.github.taza67.mcp.protocol.mcp.StringProgressToken
import io.github.taza67.mcp.protocol.mcp.ToolsCapability
import munit.FunSuite



class MetaSuite extends FunSuite {

  test("ClientCapabilities rejects non-object experimental entries") {
    val raw = JsonObject(
      Map(
        "experimental" -> JsonObject(Map("x" -> JsonString("nope")))
      )
    )
    assert(Capabilities.toClientCapabilities(raw).isLeft)
  }

  test("ClientCapabilities.extensions drops KnownKeys on decode") {
    val ui = JsonObject(Map("mimeTypes" -> JsonString("text/html")))
    val raw = JsonObject(
      Map(
        "extensions" -> JsonObject(
          Map(
            "io.modelcontextprotocol/ui" -> ui,
            "experimental" -> JsonObject(Map.empty)
          )
        )
      )
    )
    assertEquals(
      Capabilities.toClientCapabilities(raw),
      Right(ClientCapabilities(extensions = Some(Map("io.modelcontextprotocol/ui" -> ui))))
    )
  }

  test("ServerCapabilities round-trips with nested capability flags") {
    val caps = ServerCapabilities(
      logging = Some(JsonObject(Map.empty)),
      prompts = Some(PromptsCapability(listChanged = Some(true))),
      resources = Some(ResourcesCapability(subscribe = Some(true), listChanged = Some(false))),
      tools = Some(ToolsCapability(listChanged = Some(true))),
      extensions =
        Some(Map("io.modelcontextprotocol/ui" -> JsonObject(Map("ok" -> JsonBool(true)))))
    )
    assertEquals(
      Capabilities.toServerCapabilities(Capabilities.fromServerCapabilities(caps)),
      Right(caps)
    )
  }

  test("ServerCapabilities.extensions drops KnownKeys on decode") {
    val ui = JsonObject(Map("mimeTypes" -> JsonString("text/html")))
    val raw = JsonObject(
      Map(
        "extensions" -> JsonObject(
          Map(
            "io.modelcontextprotocol/ui" -> ui,
            "tools" -> JsonObject(Map("listChanged" -> JsonBool(true)))
          )
        )
      )
    )
    assertEquals(
      Capabilities.toServerCapabilities(raw),
      Right(ServerCapabilities(extensions = Some(Map("io.modelcontextprotocol/ui" -> ui))))
    )
  }

  test("ServerCapabilities rejects non-boolean listChanged") {
    val raw = JsonObject(
      Map(
        "prompts" -> JsonObject(Map("listChanged" -> JsonString("yes")))
      )
    )
    assert(Capabilities.toServerCapabilities(raw).isLeft)
  }

  test("ProgressToken rejects non-integral numbers") {
    assert(Meta.toProgressToken(JsonNumber(1.5)).isLeft)
    assertEquals(Meta.toProgressToken(JsonNumber(2)), Right(NumberProgressToken(2L)))
  }

  test("NotificationMeta round-trips with subscription id") {
    val meta = NotificationMeta(
      subscriptionId = Some(StringRequestId("listen-1")),
      extensions = MetaObject(Map("ext/a" -> JsonString("x")))
    )
    assertEquals(Meta.toNotificationMeta(Meta.fromNotificationMeta(meta)), Right(meta))
  }

  test("ResultMeta round-trips with serverInfo") {
    val meta = ResultMeta(
      serverInfo = Some(TestSupport.implementation),
      extensions = MetaObject.empty
    )
    assertEquals(Meta.toResultMeta(Meta.fromResultMeta(meta)), Right(meta))
  }

  test("RequestMeta round-trips with nested clientCapabilities.extensions") {
    val ui = JsonObject(Map("mimeTypes" -> JsonString("text/html")))
    val caps = ClientCapabilities(
      extensions = Some(Map("io.modelcontextprotocol/ui" -> ui))
    )
    val meta = RequestMeta(
      protocolVersion = McpProtocolVersion20260728,
      clientCapabilities = caps,
      clientInfo = Some(Implementation(name = "client", version = "0.1.0")),
      logLevel = Some(DebugLoggingLevel),
      progressToken = Some(StringProgressToken("p1")),
      extensions = MetaObject(Map("custom/k" -> JsonNumber(1)))
    )
    val encoded = Meta.fromRequestMeta(meta)
    assertEquals(
      encoded.value(RequestMeta.ClientCapabilitiesKey),
      JsonObject(
        Map(
          "extensions" -> JsonObject(
            Map("io.modelcontextprotocol/ui" -> ui)
          )
        )
      )
    )
    assertEquals(Meta.toRequestMeta(encoded), Right(meta))
  }
}
