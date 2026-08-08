package io.github.taza67.mcp.codec.mcp.sampling

import io.github.taza67.mcp.codec.CodecAssertions
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.mcp.AssistantRole
import io.github.taza67.mcp.protocol.mcp.ImageContent
import io.github.taza67.mcp.protocol.mcp.MetaObject
import io.github.taza67.mcp.protocol.mcp.TextContent
import io.github.taza67.mcp.protocol.mcp.UserRole
import io.github.taza67.mcp.protocol.mcp.sampling.CreateMessageRequestParams
import io.github.taza67.mcp.protocol.mcp.sampling.CreateMessageResult
import io.github.taza67.mcp.protocol.mcp.sampling.MultiSamplingContent
import io.github.taza67.mcp.protocol.mcp.sampling.SamplingMessage
import io.github.taza67.mcp.protocol.mcp.sampling.SingleSamplingContent
import io.github.taza67.mcp.protocol.mcp.tools.Tool
import io.github.taza67.mcp.protocol.mcp.tools.ToolInputSchema
import io.github.taza67.mcp.protocol.mcp.sampling.CreateMessageRequest
import io.github.taza67.mcp.protocol.jsonrpc.StringRequestId
import io.github.taza67.mcp.codec.mcp.Messages
import io.github.taza67.mcp.protocol.mcp.elicitation.Elicitation
import munit.FunSuite



class SamplingSuite extends FunSuite with CodecAssertions {

  private val minimalParams =
    CreateMessageRequestParams(
      messages = List(
        SamplingMessage(
          role = UserRole,
          content = SingleSamplingContent(TextContent(text = "What is the capital of France?"))
        )
      ),
      maxTokens = 100L
    )

  test("CreateMessageResult round-trips text content") {
    assertRoundTrip(
      CreateMessageResult(
        model = "test-model",
        role = AssistantRole,
        content = SingleSamplingContent(TextContent(text = "hello from sampling")),
        stopReason = Some("endTurn")
      )
    )(Sampling.fromCreateMessageResult, Sampling.toCreateMessageResult)
  }

  test("CreateMessageResult round-trips multi content and meta") {
    assertRoundTrip(
      CreateMessageResult(
        model = "test-model",
        role = AssistantRole,
        content = MultiSamplingContent(
          List(
            TextContent(text = "a"),
            ImageContent(data = "aW1n", mimeType = "image/png")
          )
        ),
        stopReason = Some("maxTokens"),
        meta = Some(MetaObject(Map("trace" -> JsonString("1"))))
      )
    )(Sampling.fromCreateMessageResult, Sampling.toCreateMessageResult)
  }

  test("toCreateMessageResult rejects invalid role") {
    val raw = Sampling.fromCreateMessageResult(
      CreateMessageResult(
        model = "test-model",
        role = AssistantRole,
        content = SingleSamplingContent(TextContent(text = "x"))
      )
    )
    val broken = JsonObject(
      raw.value + (CreateMessageResult.RoleKey -> JsonString("system"))
    )
    assert(Sampling.toCreateMessageResult(broken).isLeft)
  }

  test("CreateMessageRequestParams round-trips minimal") {
    assertRoundTrip(minimalParams)(
      Sampling.fromCreateMessageRequestParams,
      Sampling.toCreateMessageRequestParams
    )
  }

  test("CreateMessageRequestParams round-trips with tools") {
    assertRoundTrip(
      minimalParams.copy(
        tools = Some(
          List(
            Tool(
              name = "get_weather",
              inputSchema = ToolInputSchema(
                fields = JsonObject(
                  Map(
                    "properties" -> JsonObject(
                      Map("city" -> JsonObject(Map("type" -> JsonString("string"))))
                    )
                  )
                )
              ),
              description = Some("Get current weather for a city")
            )
          )
        )
      )
    )(Sampling.fromCreateMessageRequestParams, Sampling.toCreateMessageRequestParams)
  }

  test("CreateMessageRequest round-trips") {
    assertRoundTrip(
      CreateMessageRequest(id = StringRequestId("1"), params = minimalParams)
    )(Sampling.fromCreateMessageRequest, Sampling.toCreateMessageRequest)
  }

  test("toCreateMessageRequest rejects wrong method") {
    val raw = Messages.fromRequest(
      io.github.taza67.mcp.protocol.mcp.McpRequest(
        method = Elicitation.create,
        id = StringRequestId("1"),
        params = None
      )
    )
    assert(Sampling.toCreateMessageRequest(raw).isLeft)
  }

  test("toCreateMessageRequestParams rejects missing messages") {
    val raw = Sampling.fromCreateMessageRequestParams(minimalParams)
    val broken = JsonObject(raw.value - CreateMessageRequestParams.MessagesKey)
    assert(Sampling.toCreateMessageRequestParams(broken).isLeft)
  }

  test("toToolChoice defaults absent mode to auto") {
    val raw = JsonObject(Map.empty)
    assertEquals(
      Sampling.toToolChoice(raw),
      Right(io.github.taza67.mcp.protocol.mcp.sampling.ToolChoice())
    )
  }
}
