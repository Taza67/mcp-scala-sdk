package io.github.taza67.mcp.codec.mcp.sampling

import io.github.taza67.mcp.codec.CodecAssertions
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.mcp.AssistantRole
import io.github.taza67.mcp.protocol.mcp.ImageContent
import io.github.taza67.mcp.protocol.mcp.MetaObject
import io.github.taza67.mcp.protocol.mcp.TextContent
import io.github.taza67.mcp.protocol.mcp.sampling.CreateMessageResult
import io.github.taza67.mcp.protocol.mcp.sampling.MultiSamplingContent
import io.github.taza67.mcp.protocol.mcp.sampling.SingleSamplingContent
import munit.FunSuite



class SamplingSuite extends FunSuite with CodecAssertions {

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
}
