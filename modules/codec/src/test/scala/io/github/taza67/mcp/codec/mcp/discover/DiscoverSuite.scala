package io.github.taza67.mcp.codec.mcp.discover

import io.github.taza67.mcp.codec.CodecAssertions
import io.github.taza67.mcp.codec.TestSupport
import io.github.taza67.mcp.codec.mcp.Messages
import io.github.taza67.mcp.codec.mcp.ResultAssertions
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.jsonrpc.StringRequestId
import io.github.taza67.mcp.protocol.mcp.McpProtocolVersion20260728
import io.github.taza67.mcp.protocol.mcp.McpRequest
import io.github.taza67.mcp.protocol.mcp.PrivateCacheScope
import io.github.taza67.mcp.protocol.mcp.PublicCacheScope
import io.github.taza67.mcp.protocol.mcp.RequestParams
import io.github.taza67.mcp.protocol.mcp.Result
import io.github.taza67.mcp.protocol.mcp.ResultMeta
import io.github.taza67.mcp.protocol.mcp.ServerCapabilities
import io.github.taza67.mcp.protocol.mcp.ToolsCapability
import io.github.taza67.mcp.protocol.mcp.discover.DiscoverRequest
import io.github.taza67.mcp.protocol.mcp.discover.DiscoverResult
import io.github.taza67.mcp.protocol.mcp.prompts.Prompts
import munit.FunSuite



class DiscoverSuite extends FunSuite with CodecAssertions with ResultAssertions {

  private val discoverRequest =
    DiscoverRequest(
      id = StringRequestId("1"),
      params = RequestParams(meta = TestSupport.requestMeta)
    )

  private val discoverResultMinimal = DiscoverResult(
    supportedVersions = List(McpProtocolVersion20260728.value),
    capabilities = ServerCapabilities(tools = Some(ToolsCapability())),
    ttlMs = 0L,
    cacheScope = PublicCacheScope
  )

  private val discoverResultRich = DiscoverResult(
    supportedVersions = List(McpProtocolVersion20260728.value),
    capabilities = ServerCapabilities(tools = Some(ToolsCapability(listChanged = Some(true)))),
    ttlMs = 60_000L,
    cacheScope = PrivateCacheScope,
    instructions = Some("Use tools sparingly."),
    meta = Some(ResultMeta(serverInfo = Some(TestSupport.implementation)))
  )

  test("DiscoverRequestParams round-trips") {
    val params = discoverRequest.params
    assertEquals(Discover.toDiscoverRequestParams(params), Right(params))
  }

  test("DiscoverRequest round-trips") {
    assertRoundTrip(discoverRequest)(Discover.fromDiscoverRequest, Discover.toDiscoverRequest)
  }

  test("DiscoverResult round-trips minimal") {
    assertRoundTrip(discoverResultMinimal)(Discover.fromDiscoverResult, Discover.toDiscoverResult)
  }

  test("DiscoverResult round-trips with instructions and meta via Result envelope") {
    assertDomainResultRoundTrip(discoverResultRich)(
      d => Result(resultType = d.resultType, fields = Discover.fromDiscoverResult(d), meta = d.meta),
      Discover.fromDiscoverResult,
      Discover.toDiscoverResult,
      (d, resultType, meta) => d.copy(resultType = resultType, meta = meta)
    )
  }

  test("toDiscoverRequest rejects wrong method") {
    val raw = Messages.fromRequest(
      McpRequest(method = Prompts.list, id = StringRequestId("1"), params = None)
    )
    assert(Discover.toDiscoverRequest(raw).isLeft)
  }

  test("toDiscoverResult rejects missing supportedVersions") {
    assert(Discover.toDiscoverResult(JsonObject(Map.empty)).isLeft)
  }
}
