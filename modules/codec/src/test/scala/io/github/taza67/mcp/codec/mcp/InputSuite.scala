package io.github.taza67.mcp.codec.mcp

import io.github.taza67.mcp.codec.CodecAssertions
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.jsonrpc.Method
import io.github.taza67.mcp.protocol.mcp.AssistantRole
import io.github.taza67.mcp.protocol.mcp.CustomInputRequest
import io.github.taza67.mcp.protocol.mcp.CustomInputResponse
import io.github.taza67.mcp.protocol.mcp.ElicitationInputRequest
import io.github.taza67.mcp.protocol.mcp.ElicitationInputResponse
import io.github.taza67.mcp.protocol.mcp.InputRequest
import io.github.taza67.mcp.protocol.mcp.InputRequests
import io.github.taza67.mcp.protocol.mcp.InputRequiredResult
import io.github.taza67.mcp.protocol.mcp.InputResponses
import io.github.taza67.mcp.protocol.mcp.RootsInputRequest
import io.github.taza67.mcp.protocol.mcp.RootsInputResponse
import io.github.taza67.mcp.protocol.mcp.SamplingInputRequest
import io.github.taza67.mcp.protocol.mcp.SamplingInputResponse
import io.github.taza67.mcp.protocol.mcp.TextContent
import io.github.taza67.mcp.protocol.mcp.UserRole
import io.github.taza67.mcp.protocol.mcp.elicitation.ElicitAccept
import io.github.taza67.mcp.protocol.mcp.elicitation.ElicitRequestFormParams
import io.github.taza67.mcp.protocol.mcp.elicitation.ElicitRequestedSchema
import io.github.taza67.mcp.protocol.mcp.elicitation.ElicitResult
import io.github.taza67.mcp.protocol.mcp.elicitation.ElicitStringValue
import io.github.taza67.mcp.protocol.mcp.elicitation.StringSchema
import io.github.taza67.mcp.protocol.mcp.roots.ListRootsRequestParams
import io.github.taza67.mcp.protocol.mcp.roots.ListRootsResult
import io.github.taza67.mcp.protocol.mcp.roots.Root
import io.github.taza67.mcp.protocol.mcp.sampling.CreateMessageRequestParams
import io.github.taza67.mcp.protocol.mcp.sampling.CreateMessageResult
import io.github.taza67.mcp.protocol.mcp.sampling.SamplingMessage
import io.github.taza67.mcp.protocol.mcp.sampling.SingleSamplingContent
import munit.FunSuite



class InputSuite extends FunSuite with CodecAssertions {

  private val customResponse =
    CustomInputResponse(JsonObject(Map("note" -> JsonString("ok"))))

  private val formElicit =
    ElicitationInputRequest(
      ElicitRequestFormParams(
        message = "Please provide your GitHub username",
        requestedSchema = ElicitRequestedSchema(
          properties = Map("name" -> StringSchema()),
          required = Some(List("name"))
        )
      )
    )

  private val samplingRequest =
    SamplingInputRequest(
      CreateMessageRequestParams(
        messages = List(
          SamplingMessage(
            role = UserRole,
            content = SingleSamplingContent(TextContent(text = "What is the capital of France?"))
          )
        ),
        maxTokens = 100L
      )
    )

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

  test("InputRequests round-trips typed and custom entries") {
    assertRoundTrip(
      InputRequests(
        Map(
          "elicit-1" -> formElicit,
          "roots-1" -> RootsInputRequest(),
          "roots-2" -> RootsInputRequest(Some(ListRootsRequestParams())),
          "sample-1" -> samplingRequest,
          "custom-1" -> CustomInputRequest(
            method = Method("ext/ping"),
            params = Some(JsonObject(Map("x" -> JsonString("1"))))
          )
        )
      )
    )(Input.fromInputRequests, Input.toInputRequests)
  }

  test("toInputRequests rejects non-object map entries") {
    val raw = JsonObject(Map("step-1" -> JsonString("ok")))
    assert(Input.toInputRequests(raw).isLeft)
  }

  test("toInputRequest rejects known method with invalid params") {
    val raw = JsonObject(
      Map(
        InputRequest.MethodKey -> JsonString("elicitation/create"),
        InputRequest.ParamsKey -> JsonObject(Map("message" -> JsonString("hi")))
      )
    )
    assert(Input.toInputRequest(raw).isLeft)
  }

  test("InputRequiredResult round-trips with requests and state") {
    assertRoundTrip(
      InputRequiredResult(
        inputRequests = Some(
          InputRequests(
            Map(
              "elicit-1" -> formElicit,
              "sample-1" -> samplingRequest
            )
          )
        ),
        requestState = Some("opaque-state")
      )
    )(Input.fromInputRequiredResult, Input.toInputRequiredResult)
  }

  test("InputRequiredResult round-trips with state only") {
    assertRoundTrip(
      InputRequiredResult(requestState = Some("load-shed"))
    )(Input.fromInputRequiredResult, Input.toInputRequiredResult)
  }

  test("toInputRequiredResult rejects when both inputRequests and requestState absent") {
    val raw = Input.fromInputRequiredResult(InputRequiredResult(requestState = Some("x")))
    val broken = JsonObject(raw.value - InputRequiredResult.RequestStateKey)
    assert(Input.toInputRequiredResult(broken).isLeft)
  }
}
