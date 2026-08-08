package io.github.taza67.mcp.codec.mcp.tools

import io.github.taza67.mcp.codec.CodecAssertions
import io.github.taza67.mcp.codec.TestSupport
import io.github.taza67.mcp.codec.mcp.Messages
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.jsonrpc.StringRequestId
import io.github.taza67.mcp.protocol.mcp.AssistantRole
import io.github.taza67.mcp.protocol.mcp.CustomInputResponse
import io.github.taza67.mcp.protocol.mcp.ElicitationInputResponse
import io.github.taza67.mcp.protocol.mcp.InputRequiredResult
import io.github.taza67.mcp.protocol.mcp.InputResponses
import io.github.taza67.mcp.protocol.mcp.McpRequest
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
import io.github.taza67.mcp.protocol.mcp.tools.{Tools => ToolMethods}
import io.github.taza67.mcp.protocol.mcp.tools.CallToolRequest
import io.github.taza67.mcp.protocol.mcp.tools.CallToolRequestParams
import munit.FunSuite



class ToolsSuite extends FunSuite with CodecAssertions {

  private val customResponse =
    CustomInputResponse(JsonObject(Map("note" -> JsonString("ok"))))

  private val minimal = CallToolRequestParams(
    meta = TestSupport.requestMeta,
    name = "get_weather"
  )

  private val rich = CallToolRequestParams(
    meta = TestSupport.requestMeta,
    name = "get_weather",
    arguments = Some(JsonObject(Map("city" -> JsonString("Paris")))),
    inputResponses = Some(InputResponses(Map("step-1" -> customResponse))),
    requestState = Some("opaque-state")
  )

  private val typedResponses = CallToolRequestParams(
    meta = TestSupport.requestMeta,
    name = "get_weather",
    inputResponses = Some(
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
    )
  )

  test("CallToolRequestParams round-trips minimal") {
    assertRoundTrip(minimal)(Tools.fromCallToolRequestParams, Tools.toCallToolRequestParams)
  }

  test("CallToolRequestParams round-trips with arguments, inputResponses, requestState") {
    assertRoundTrip(rich)(Tools.fromCallToolRequestParams, Tools.toCallToolRequestParams)
  }

  test("CallToolRequestParams round-trips typed inputResponses") {
    assertRoundTrip(typedResponses)(
      Tools.fromCallToolRequestParams,
      Tools.toCallToolRequestParams
    )
  }

  test("CallToolRequest round-trips") {
    assertRoundTrip(
      CallToolRequest(id = StringRequestId("1"), params = rich)
    )(Tools.fromCallToolRequest, Tools.toCallToolRequest)
  }

  test("toCallToolRequest rejects wrong method") {
    val raw = Messages.fromRequest(
      McpRequest(method = ToolMethods.list, id = StringRequestId("1"), params = None)
    )
    assert(Tools.toCallToolRequest(raw).isLeft)
  }

  test("toCallToolRequest rejects missing params") {
    val raw = Messages.fromRequest(
      McpRequest(method = ToolMethods.call, id = StringRequestId("1"), params = None)
    )
    assert(Tools.toCallToolRequest(raw).isLeft)
  }

  test("toCallToolRequestParams rejects missing name") {
    val params = Tools
      .fromCallToolRequestParams(minimal)
      .copy(
        fields = JsonObject(Map.empty)
      )
    assert(Tools.toCallToolRequestParams(params).isLeft)
  }

  test("encoded CallToolRequest keeps name and arguments at params top level") {
    val raw = Tools.fromCallToolRequest(
      CallToolRequest(id = StringRequestId("1"), params = rich)
    )
    val params = paramsObject(raw)
    assertEquals(params.value.get(CallToolRequestParams.NameKey), Some(JsonString("get_weather")))
    assertEquals(
      params.value.get(CallToolRequestParams.ArgumentsKey),
      Some(JsonObject(Map("city" -> JsonString("Paris"))))
    )
    assertEquals(
      params.value.get(InputRequiredResult.RequestStateKey),
      Some(JsonString("opaque-state"))
    )
    assert(params.value.contains(InputResponses.InputResponsesKey))
  }
}
