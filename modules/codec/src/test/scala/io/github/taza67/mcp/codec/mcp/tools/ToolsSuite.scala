package io.github.taza67.mcp.codec.mcp.tools

import io.github.taza67.mcp.codec.CodecAssertions
import io.github.taza67.mcp.codec.TestSupport
import io.github.taza67.mcp.codec.mcp.Input
import io.github.taza67.mcp.codec.mcp.Messages
import io.github.taza67.mcp.codec.mcp.ResultAssertions
import io.github.taza67.mcp.protocol.json.JsonNumber
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.jsonrpc.StringRequestId
import io.github.taza67.mcp.protocol.mcp.AssistantRole
import io.github.taza67.mcp.protocol.mcp.Cursor
import io.github.taza67.mcp.protocol.mcp.PublicCacheScope
import io.github.taza67.mcp.protocol.mcp.Result
import io.github.taza67.mcp.protocol.mcp.ResultMeta
import io.github.taza67.mcp.protocol.mcp.Completed
import io.github.taza67.mcp.protocol.mcp.CustomInputResponse
import io.github.taza67.mcp.protocol.mcp.InputRequired
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
import io.github.taza67.mcp.protocol.mcp.tools.CallToolResult
import io.github.taza67.mcp.protocol.mcp.tools.ListToolsResult
import io.github.taza67.mcp.protocol.mcp.tools.Tool
import io.github.taza67.mcp.protocol.mcp.tools.ToolInputSchema
import munit.FunSuite



class ToolsSuite extends FunSuite with CodecAssertions with ResultAssertions {

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

  private val sampleTool =
    Tool(
      name = "get_weather",
      inputSchema = ToolInputSchema(),
      description = Some("Get weather")
    )

  private val listToolsMinimal =
    ListToolsResult(tools = List(sampleTool), ttlMs = 0L, cacheScope = PublicCacheScope)

  private val listToolsRich = ListToolsResult(
    tools = List(sampleTool),
    ttlMs = 60_000L,
    cacheScope = PublicCacheScope,
    nextCursor = Some(Cursor("page-2")),
    meta = Some(ResultMeta(serverInfo = Some(TestSupport.implementation)))
  )

  private val callToolMinimal = CallToolResult(content = List(TextContent(text = "sunny")))

  private val callToolRich = CallToolResult(
    content = List(TextContent(text = "sunny")),
    structuredContent = Some(JsonObject(Map("temp" -> JsonNumber(22)))),
    isError = Some(false),
    meta = Some(ResultMeta(serverInfo = Some(TestSupport.implementation)))
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

  test("ListToolsResult round-trips minimal") {
    assertRoundTrip(listToolsMinimal)(Tools.fromListToolsResult, Tools.toListToolsResult)
  }

  test("ListToolsResult round-trips with pagination tail via Result envelope") {
    assertDomainResultRoundTrip(listToolsRich)(
      d => Result(resultType = d.resultType, fields = Tools.fromListToolsResult(d), meta = d.meta),
      Tools.fromListToolsResult,
      Tools.toListToolsResult,
      (d, resultType, meta) => d.copy(resultType = resultType, meta = meta)
    )
  }

  test("CallToolResult round-trips minimal") {
    assertRoundTrip(callToolMinimal)(Tools.fromCallToolResult, Tools.toCallToolResult)
  }

  test("CallToolResult round-trips with structured output via Result envelope") {
    assertDomainResultRoundTrip(callToolRich)(
      d => Result(resultType = d.resultType, fields = Tools.fromCallToolResult(d), meta = d.meta),
      Tools.fromCallToolResult,
      Tools.toCallToolResult,
      (d, resultType, meta) => d.copy(resultType = resultType, meta = meta)
    )
  }

  test("toListToolsResult rejects missing tools") {
    assert(Tools.toListToolsResult(JsonObject(Map.empty)).isLeft)
  }

  test("Tool round-trips minimal input schema") {
    assertRoundTrip(
      Tool(
        name = "get_weather",
        inputSchema = ToolInputSchema(),
        description = Some("Get weather")
      )
    )(Tools.fromTool, Tools.toTool)
  }

  test("toTool rejects inputSchema without object type") {
    val raw = Tools.fromTool(Tool(name = "x", inputSchema = ToolInputSchema()))
    val schema = raw.value(Tool.InputSchemaKey).asInstanceOf[JsonObject]
    val broken = JsonObject(
      raw.value + (Tool.InputSchemaKey -> JsonObject(
        schema.value + (ToolInputSchema.TypeKey -> JsonString("array"))
      ))
    )
    assert(Tools.toTool(broken).isLeft)
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

  test("CallToolOutcome round-trips completed") {
    val completed = Completed(callToolMinimal)
    assertEquals(
      Tools.toCallToolOutcome(Tools.fromCallToolOutcome(completed)),
      Right(completed)
    )
  }

  test("toCallToolOutcome classifies completed and input_required") {
    val completed = Tools.fromCallToolResult(
      CallToolResult(content = List(TextContent(text = "done")))
    )
    assertEquals(
      Tools.toCallToolOutcome(completed),
      Right(Completed(CallToolResult(content = List(TextContent(text = "done")))))
    )

    val inputRequired = Input.fromInputRequiredResult(
      InputRequiredResult(requestState = Some("opaque"))
    )
    assert(
      Tools.toCallToolOutcome(inputRequired).exists(_.isInstanceOf[InputRequired])
    )
  }
}
