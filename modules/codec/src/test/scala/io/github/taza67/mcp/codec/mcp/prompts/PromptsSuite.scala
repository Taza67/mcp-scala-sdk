package io.github.taza67.mcp.codec.mcp.prompts

import io.github.taza67.mcp.codec.CodecAssertions
import io.github.taza67.mcp.codec.TestSupport
import io.github.taza67.mcp.codec.mcp.Messages
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.jsonrpc.StringRequestId
import io.github.taza67.mcp.protocol.mcp.CustomInputResponse
import io.github.taza67.mcp.protocol.mcp.InputResponses
import io.github.taza67.mcp.protocol.mcp.McpRequest
import io.github.taza67.mcp.protocol.mcp.prompts.{Prompts => PromptMethods}
import io.github.taza67.mcp.protocol.mcp.prompts.GetPromptRequest
import io.github.taza67.mcp.protocol.mcp.prompts.GetPromptRequestParams
import munit.FunSuite



class PromptsSuite extends FunSuite with CodecAssertions {

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
