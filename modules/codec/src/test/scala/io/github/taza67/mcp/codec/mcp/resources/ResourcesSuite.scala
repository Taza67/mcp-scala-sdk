package io.github.taza67.mcp.codec.mcp.resources

import io.github.taza67.mcp.codec.CodecAssertions
import io.github.taza67.mcp.codec.TestSupport
import io.github.taza67.mcp.codec.mcp.Input
import io.github.taza67.mcp.codec.mcp.Messages
import io.github.taza67.mcp.codec.mcp.ResultAssertions
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.jsonrpc.StringRequestId
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
import io.github.taza67.mcp.protocol.mcp.TextResourceContents
import io.github.taza67.mcp.protocol.mcp.resources.{Resources => ResourceMethods}
import io.github.taza67.mcp.protocol.mcp.resources.ListResourceTemplatesResult
import io.github.taza67.mcp.protocol.mcp.resources.ListResourcesResult
import io.github.taza67.mcp.protocol.mcp.resources.ReadResourceRequest
import io.github.taza67.mcp.protocol.mcp.resources.ReadResourceRequestParams
import io.github.taza67.mcp.protocol.mcp.resources.ReadResourceResult
import io.github.taza67.mcp.protocol.mcp.resources.Resource
import io.github.taza67.mcp.protocol.mcp.resources.ResourceTemplate
import munit.FunSuite



class ResourcesSuite extends FunSuite with CodecAssertions with ResultAssertions {

  private val sampleResource =
    Resource(name = "readme", uri = "file:///README.md", mimeType = Some("text/markdown"))

  private val sampleTemplate =
    ResourceTemplate(name = "logs", uriTemplate = "file:///logs/{date}.txt")

  private val listResourcesMinimal =
    ListResourcesResult(resources = List(sampleResource), ttlMs = 0L, cacheScope = PublicCacheScope)

  private val listResourcesRich = ListResourcesResult(
    resources = List(sampleResource),
    ttlMs = 60_000L,
    cacheScope = PublicCacheScope,
    nextCursor = Some(Cursor("page-2")),
    meta = Some(ResultMeta(serverInfo = Some(TestSupport.implementation)))
  )

  private val listTemplatesMinimal = ListResourceTemplatesResult(
    resourceTemplates = List(sampleTemplate),
    ttlMs = 0L,
    cacheScope = PublicCacheScope
  )

  private val listTemplatesRich = ListResourceTemplatesResult(
    resourceTemplates = List(sampleTemplate),
    ttlMs = 60_000L,
    cacheScope = PublicCacheScope,
    nextCursor = Some(Cursor("page-2")),
    meta = Some(ResultMeta(serverInfo = Some(TestSupport.implementation)))
  )

  private val readResourceMinimal = ReadResourceResult(
    contents = List(TextResourceContents(uri = "file:///tmp/a.txt", text = "hello")),
    ttlMs = 0L,
    cacheScope = PublicCacheScope
  )

  private val readResourceRich = ReadResourceResult(
    contents = List(TextResourceContents(uri = "file:///tmp/a.txt", text = "hello")),
    ttlMs = 60_000L,
    cacheScope = PublicCacheScope,
    meta = Some(ResultMeta(serverInfo = Some(TestSupport.implementation)))
  )

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

  test("ListResourcesResult round-trips minimal") {
    assertRoundTrip(listResourcesMinimal)(
      Resources.fromListResourcesResult,
      Resources.toListResourcesResult
    )
  }

  test("ListResourcesResult round-trips with pagination tail via Result envelope") {
    assertDomainResultRoundTrip(listResourcesRich)(
      d => Result(resultType = d.resultType, fields = Resources.fromListResourcesResult(d), meta = d.meta),
      Resources.fromListResourcesResult,
      Resources.toListResourcesResult,
      (d, resultType, meta) => d.copy(resultType = resultType, meta = meta)
    )
  }

  test("ListResourceTemplatesResult round-trips minimal") {
    assertRoundTrip(listTemplatesMinimal)(
      Resources.fromListResourceTemplatesResult,
      Resources.toListResourceTemplatesResult
    )
  }

  test("ListResourceTemplatesResult round-trips with pagination tail via Result envelope") {
    assertDomainResultRoundTrip(listTemplatesRich)(
      d =>
        Result(
          resultType = d.resultType,
          fields = Resources.fromListResourceTemplatesResult(d),
          meta = d.meta
        ),
      Resources.fromListResourceTemplatesResult,
      Resources.toListResourceTemplatesResult,
      (d, resultType, meta) => d.copy(resultType = resultType, meta = meta)
    )
  }

  test("ReadResourceResult round-trips minimal") {
    assertRoundTrip(readResourceMinimal)(
      Resources.fromReadResourceResult,
      Resources.toReadResourceResult
    )
  }

  test("ReadResourceResult round-trips with cache tail via Result envelope") {
    assertDomainResultRoundTrip(readResourceRich)(
      d => Result(resultType = d.resultType, fields = Resources.fromReadResourceResult(d), meta = d.meta),
      Resources.fromReadResourceResult,
      Resources.toReadResourceResult,
      (d, resultType, meta) => d.copy(resultType = resultType, meta = meta)
    )
  }

  test("ReadResourceResult outcome round-trips") {
    val completed = Completed(readResourceMinimal)
    assertEquals(
      Resources.toReadResourceOutcome(Resources.fromReadResourceOutcome(completed)),
      Right(completed)
    )
  }

  test("toReadResourceOutcome classifies completed and input_required") {
    val completed = Resources.fromReadResourceResult(readResourceMinimal)
    assertEquals(
      Resources.toReadResourceOutcome(completed),
      Right(Completed(readResourceMinimal))
    )

    val inputRequired = Input.fromInputRequiredResult(
      InputRequiredResult(requestState = Some("opaque"))
    )
    assert(Resources.toReadResourceOutcome(inputRequired).exists(_.isInstanceOf[InputRequired]))
  }

  test("toListResourcesResult rejects missing resources") {
    assert(Resources.toListResourcesResult(JsonObject(Map.empty)).isLeft)
  }

  test("Resource and ResourceTemplate round-trip") {
    assertRoundTrip(sampleResource)(Resources.fromResource, Resources.toResource)
    assertRoundTrip(sampleTemplate)(Resources.fromResourceTemplate, Resources.toResourceTemplate)
  }

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
