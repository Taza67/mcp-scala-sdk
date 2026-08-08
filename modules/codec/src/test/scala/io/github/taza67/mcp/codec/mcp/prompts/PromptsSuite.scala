package io.github.taza67.mcp.codec.mcp.prompts

import io.github.taza67.mcp.codec.CodecAssertions
import io.github.taza67.mcp.codec.TestSupport
import io.github.taza67.mcp.codec.mcp.Input
import io.github.taza67.mcp.codec.mcp.Messages
import io.github.taza67.mcp.codec.mcp.ResultAssertions
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.jsonrpc.StringRequestId
import io.github.taza67.mcp.protocol.mcp.AssistantRole
import io.github.taza67.mcp.protocol.mcp.Completed
import io.github.taza67.mcp.protocol.mcp.Cursor
import io.github.taza67.mcp.protocol.mcp.CustomInputResponse
import io.github.taza67.mcp.protocol.mcp.InputRequired
import io.github.taza67.mcp.protocol.mcp.InputRequiredResult
import io.github.taza67.mcp.protocol.mcp.InputResponses
import io.github.taza67.mcp.protocol.mcp.McpRequest
import io.github.taza67.mcp.protocol.mcp.PublicCacheScope
import io.github.taza67.mcp.protocol.mcp.Result
import io.github.taza67.mcp.protocol.mcp.ResultMeta
import io.github.taza67.mcp.protocol.mcp.TextContent
import io.github.taza67.mcp.protocol.mcp.prompts.{Prompts => PromptMethods}
import io.github.taza67.mcp.protocol.mcp.prompts.GetPromptRequest
import io.github.taza67.mcp.protocol.mcp.prompts.GetPromptRequestParams
import io.github.taza67.mcp.protocol.mcp.prompts.GetPromptResult
import io.github.taza67.mcp.protocol.mcp.prompts.ListPromptsResult
import io.github.taza67.mcp.protocol.mcp.prompts.Prompt
import io.github.taza67.mcp.protocol.mcp.prompts.PromptMessage
import munit.FunSuite



class PromptsSuite extends FunSuite with CodecAssertions with ResultAssertions {

  private val samplePrompt =
    Prompt(name = "git-commit", description = Some("Generate a commit message"))

  private val listPromptsMinimal =
    ListPromptsResult(prompts = List(samplePrompt), ttlMs = 0L, cacheScope = PublicCacheScope)

  private val listPromptsRich = ListPromptsResult(
    prompts = List(samplePrompt),
    ttlMs = 60_000L,
    cacheScope = PublicCacheScope,
    nextCursor = Some(Cursor("page-2")),
    meta = Some(ResultMeta(serverInfo = Some(TestSupport.implementation)))
  )

  private val getPromptMinimal = GetPromptResult(
    messages = List(
      PromptMessage(role = AssistantRole, content = TextContent(text = "Commit: fix login"))
    )
  )

  private val getPromptRich = GetPromptResult(
    messages = List(
      PromptMessage(role = AssistantRole, content = TextContent(text = "Commit: fix login"))
    ),
    description = Some("Git commit helper"),
    meta = Some(ResultMeta(serverInfo = Some(TestSupport.implementation)))
  )

  private val minimal = GetPromptRequestParams(
    meta = TestSupport.requestMeta,
    name = "git-commit"
  )

  private val rich = GetPromptRequestParams(
    meta = TestSupport.requestMeta,
    name = "git-commit",
    arguments = Some(Map("changes" -> "fix login")),
    inputResponses = Some(
      InputResponses(
        Map("step-1" -> CustomInputResponse(JsonObject(Map("note" -> JsonString("ok")))))
      )
    ),
    requestState = Some("opaque-state")
  )

  test("ListPromptsResult round-trips minimal") {
    assertRoundTrip(listPromptsMinimal)(Prompts.fromListPromptsResult, Prompts.toListPromptsResult)
  }

  test("ListPromptsResult round-trips with pagination tail via Result envelope") {
    assertDomainResultRoundTrip(listPromptsRich)(
      d => Result(resultType = d.resultType, fields = Prompts.fromListPromptsResult(d), meta = d.meta),
      Prompts.fromListPromptsResult,
      Prompts.toListPromptsResult,
      (d, resultType, meta) => d.copy(resultType = resultType, meta = meta)
    )
  }

  test("GetPromptResult round-trips minimal") {
    assertRoundTrip(getPromptMinimal)(Prompts.fromGetPromptResult, Prompts.toGetPromptResult)
  }

  test("GetPromptResult round-trips with description and meta via Result envelope") {
    assertDomainResultRoundTrip(getPromptRich)(
      d => Result(resultType = d.resultType, fields = Prompts.fromGetPromptResult(d), meta = d.meta),
      Prompts.fromGetPromptResult,
      Prompts.toGetPromptResult,
      (d, resultType, meta) => d.copy(resultType = resultType, meta = meta)
    )
  }

  test("GetPromptResult outcome round-trips") {
    val completed = Completed(getPromptMinimal)
    assertEquals(
      Prompts.toGetPromptOutcome(Prompts.fromGetPromptOutcome(completed)),
      Right(completed)
    )
  }

  test("toGetPromptOutcome classifies completed and input_required") {
    val completed = Prompts.fromGetPromptResult(getPromptMinimal)
    assertEquals(Prompts.toGetPromptOutcome(completed), Right(Completed(getPromptMinimal)))

    val inputRequired = Input.fromInputRequiredResult(
      InputRequiredResult(requestState = Some("opaque"))
    )
    assert(Prompts.toGetPromptOutcome(inputRequired).exists(_.isInstanceOf[InputRequired]))
  }

  test("toListPromptsResult rejects missing prompts") {
    assert(Prompts.toListPromptsResult(JsonObject(Map.empty)).isLeft)
  }

  test("GetPromptRequestParams round-trips minimal") {
    assertRoundTrip(minimal)(Prompts.fromGetPromptRequestParams, Prompts.toGetPromptRequestParams)
  }

  test("GetPromptRequestParams round-trips with string arguments and continuation") {
    assertRoundTrip(rich)(Prompts.fromGetPromptRequestParams, Prompts.toGetPromptRequestParams)
  }

  test("GetPromptRequest round-trips") {
    assertRoundTrip(
      GetPromptRequest(id = StringRequestId("1"), params = rich)
    )(Prompts.fromGetPromptRequest, Prompts.toGetPromptRequest)
  }

  test("toGetPromptRequest rejects wrong method") {
    val raw = Messages.fromRequest(
      McpRequest(method = PromptMethods.list, id = StringRequestId("1"), params = None)
    )
    assert(Prompts.toGetPromptRequest(raw).isLeft)
  }

  test("toGetPromptRequestParams rejects non-string argument values") {
    val params = Prompts
      .fromGetPromptRequestParams(minimal)
      .copy(
        fields = JsonObject(
          Map(
            GetPromptRequestParams.NameKey -> JsonString("git-commit"),
            GetPromptRequestParams.ArgumentsKey ->
              JsonObject(Map("changes" -> JsonObject(Map.empty)))
          )
        )
      )
    assert(Prompts.toGetPromptRequestParams(params).isLeft)
  }

  test("encoded GetPromptRequest keeps string arguments at params top level") {
    val raw = Prompts.fromGetPromptRequest(
      GetPromptRequest(id = StringRequestId("1"), params = rich)
    )
    val params = paramsObject(raw)
    assertEquals(params.value.get(GetPromptRequestParams.NameKey), Some(JsonString("git-commit")))
    assertEquals(
      params.value.get(GetPromptRequestParams.ArgumentsKey),
      Some(JsonObject(Map("changes" -> JsonString("fix login"))))
    )
  }
}
