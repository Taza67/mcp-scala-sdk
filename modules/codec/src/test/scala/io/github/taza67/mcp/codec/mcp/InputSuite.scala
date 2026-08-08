package io.github.taza67.mcp.codec.mcp

import io.github.taza67.mcp.codec.CodecAssertions
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.mcp.AssistantRole
import io.github.taza67.mcp.protocol.mcp.CustomInputResponse
import io.github.taza67.mcp.protocol.mcp.ElicitationInputResponse
import io.github.taza67.mcp.protocol.mcp.InputResponses
import io.github.taza67.mcp.protocol.mcp.RootsInputResponse
import io.github.taza67.mcp.protocol.mcp.SamplingInputResponse
import io.github.taza67.mcp.protocol.mcp.TextContent
import io.github.taza67.mcp.protocol.mcp.elicitation.ElicitAccept
import io.github.taza67.mcp.protocol.mcp.elicitation.ElicitResult
import io.github.taza67.mcp.protocol.mcp.elicitation.ElicitStringValue
import io.github.taza67.mcp.protocol.mcp.roots.ListRootsResult
import io.github.taza67.mcp.protocol.mcp.roots.Root
import io.github.taza67.mcp.protocol.mcp.sampling.CreateMessageResult
import io.github.taza67.mcp.protocol.mcp.sampling.SingleSamplingContent
import munit.FunSuite



class InputSuite extends FunSuite with CodecAssertions {

  private val customResponse =
    CustomInputResponse(JsonObject(Map("note" -> JsonString("ok"))))

  test("InputResponses round-trips typed and custom entries") {
    assertRoundTrip(
      InputResponses(
        Map(
          "elicit-1" -> ElicitationInputResponse(
            ElicitResult(
              action = ElicitAccept,
              content = Some(Map("city" -> ElicitStringValue("Paris")))
            )
          ),
          "roots-1" -> RootsInputResponse(
            ListRootsResult(roots = List(Root(uri = "file:///project")))
          ),
          "sample-1" -> SamplingInputResponse(
            CreateMessageResult(
              model = "test-model",
              role = AssistantRole,
              content = SingleSamplingContent(TextContent(text = "sampled"))
            )
          ),
          "custom-1" -> customResponse
        )
      )
    )(Input.fromInputResponses, Input.toInputResponses)
  }

  test("toInputResponses rejects non-object map entries") {
    val raw = JsonObject(Map("step-1" -> JsonString("ok")))
    assert(Input.toInputResponses(raw).isLeft)
  }
}
