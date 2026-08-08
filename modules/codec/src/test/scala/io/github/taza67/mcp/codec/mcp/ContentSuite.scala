package io.github.taza67.mcp.codec.mcp

import io.github.taza67.mcp.codec.CodecAssertions
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.mcp.Annotations
import io.github.taza67.mcp.protocol.mcp.AudioContent
import io.github.taza67.mcp.protocol.mcp.BlobResourceContents
import io.github.taza67.mcp.protocol.mcp.ContentBlock
import io.github.taza67.mcp.protocol.mcp.EmbeddedResource
import io.github.taza67.mcp.protocol.mcp.ImageContent
import io.github.taza67.mcp.protocol.mcp.ResourceLink
import io.github.taza67.mcp.protocol.mcp.TextContent
import io.github.taza67.mcp.protocol.mcp.TextResourceContents
import io.github.taza67.mcp.protocol.mcp.ToolResultContent
import io.github.taza67.mcp.protocol.mcp.ToolUseContent
import io.github.taza67.mcp.protocol.mcp.UserRole
import io.github.taza67.mcp.protocol.mcp.sampling.MultiSamplingContent
import io.github.taza67.mcp.protocol.mcp.sampling.SamplingMessageContent
import io.github.taza67.mcp.protocol.mcp.sampling.SingleSamplingContent
import munit.FunSuite



class ContentSuite extends FunSuite with CodecAssertions {

  test("TextContent round-trips") {
    assertRoundTrip(TextContent(text = "hello"))(Content.fromTextContent, Content.toTextContent)
  }

  test("TextContent round-trips with annotations") {
    assertRoundTrip(
      TextContent(
        text = "annotated",
        annotations = Some(
          Annotations(
            audience = Some(List(UserRole)),
            priority = Some(0.5)
          )
        )
      )
    )(Content.fromTextContent, Content.toTextContent)
  }

  test("ImageContent round-trips") {
    assertRoundTrip(
      ImageContent(data = "aW1n", mimeType = "image/png")
    )(Content.fromImageContent, Content.toImageContent)
  }

  test("AudioContent round-trips") {
    assertRoundTrip(
      AudioContent(data = "YXVk", mimeType = "audio/wav")
    )(Content.fromAudioContent, Content.toAudioContent)
  }

  test("ToolUseContent round-trips") {
    assertRoundTrip(
      ToolUseContent(
        id = "call-1",
        name = "get_weather",
        input = JsonObject(Map("city" -> JsonString("Paris")))
      )
    )(Content.fromToolUseContent, Content.toToolUseContent)
  }

  test("ToolResultContent round-trips with text block") {
    assertRoundTrip(
      ToolResultContent(
        toolUseId = "call-1",
        content = List(TextContent(text = "ok")),
        isError = Some(false)
      )
    )(Content.fromToolResultContent, Content.toToolResultContent)
  }

  test("ResourceLink round-trips") {
    assertRoundTrip(
      ResourceLink(name = "readme", uri = "file:///README.md", mimeType = Some("text/markdown"))
    )(Content.fromResourceLink, Content.toResourceLink)
  }

  test("EmbeddedResource round-trips text contents") {
    assertRoundTrip(
      EmbeddedResource(
        resource = TextResourceContents(uri = "file:///a.txt", text = "hello")
      )
    )(Content.fromEmbeddedResource, Content.toEmbeddedResource)
  }

  test("EmbeddedResource round-trips blob contents") {
    assertRoundTrip(
      EmbeddedResource(
        resource = BlobResourceContents(uri = "file:///a.bin", blob = "Ymlu")
      )
    )(Content.fromEmbeddedResource, Content.toEmbeddedResource)
  }

  test("ContentBlock round-trips text via discriminant") {
    assertRoundTrip(TextContent(text = "via-block"): ContentBlock)(
      Content.fromContentBlock,
      Content.toContentBlock
    )
  }

  test("SamplingMessageContent round-trips single and multi blocks") {
    assertRoundTrip(SingleSamplingContent(TextContent(text = "one")): SamplingMessageContent)(
      Content.fromSamplingMessageContent,
      Content.toSamplingMessageContent
    )
    assertRoundTrip(
      MultiSamplingContent(
        List(
          TextContent(text = "a"),
          ImageContent(data = "aW1n", mimeType = "image/png")
        )
      ): SamplingMessageContent
    )(Content.fromSamplingMessageContent, Content.toSamplingMessageContent)
  }

  test("toRole rejects unknown role") {
    assert(Content.toRole(JsonString("system")).isLeft)
  }

  test("toContentBlock rejects unknown type") {
    assert(
      Content
        .toContentBlock(JsonObject(Map(ContentBlock.TypeKey -> JsonString("unknown"))))
        .isLeft
    )
  }
}
