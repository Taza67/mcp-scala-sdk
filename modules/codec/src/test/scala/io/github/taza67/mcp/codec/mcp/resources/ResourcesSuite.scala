package io.github.taza67.mcp.codec.mcp.resources

import io.github.taza67.mcp.codec.CodecAssertions
import io.github.taza67.mcp.codec.TestSupport
import io.github.taza67.mcp.codec.mcp.Messages
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.jsonrpc.StringRequestId
import io.github.taza67.mcp.protocol.mcp.CustomInputResponse
import io.github.taza67.mcp.protocol.mcp.InputRequiredResult
import io.github.taza67.mcp.protocol.mcp.InputResponses
import io.github.taza67.mcp.protocol.mcp.McpRequest
import io.github.taza67.mcp.protocol.mcp.resources.{Resources => ResourceMethods}
import io.github.taza67.mcp.protocol.mcp.resources.ReadResourceRequest
import io.github.taza67.mcp.protocol.mcp.resources.ReadResourceRequestParams
import munit.FunSuite



class ResourcesSuite extends FunSuite with CodecAssertions {

  private val minimal = ReadResourceRequestParams(
    meta = TestSupport.requestMeta,
    uri = "file:///tmp/a.txt"
  )

  private val rich = ReadResourceRequestParams(
    meta = TestSupport.requestMeta,
    uri = "file:///tmp/a.txt",
    inputResponses = Some(
      InputResponses(
        Map("step-1" -> CustomInputResponse(JsonObject(Map("note" -> JsonString("ok")))))
      )
    ),
    requestState = Some("opaque-state")
  )

  test("ReadResourceRequestParams round-trips minimal") {
    assertRoundTrip(minimal)(
      Resources.fromReadResourceRequestParams,
      Resources.toReadResourceRequestParams
    )
  }

  test("ReadResourceRequestParams round-trips with continuation fields") {
    assertRoundTrip(rich)(
      Resources.fromReadResourceRequestParams,
      Resources.toReadResourceRequestParams
    )
  }

  test("ReadResourceRequest round-trips") {
    assertRoundTrip(
      ReadResourceRequest(id = StringRequestId("1"), params = rich)
    )(Resources.fromReadResourceRequest, Resources.toReadResourceRequest)
  }

  test("toReadResourceRequest rejects wrong method") {
    val raw = Messages.fromRequest(
      McpRequest(method = ResourceMethods.list, id = StringRequestId("1"), params = None)
    )
    assert(Resources.toReadResourceRequest(raw).isLeft)
  }

  test("encoded ReadResourceRequest keeps uri at params top level") {
    val raw = Resources.fromReadResourceRequest(
      ReadResourceRequest(id = StringRequestId("1"), params = rich)
    )
    val params = paramsObject(raw)
    assertEquals(
      params.value.get(ReadResourceRequestParams.UriKey),
      Some(JsonString("file:///tmp/a.txt"))
    )
    assertEquals(
      params.value.get(InputRequiredResult.RequestStateKey),
      Some(JsonString("opaque-state"))
    )
  }
}
