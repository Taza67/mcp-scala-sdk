package io.github.taza67.mcp.codec.mcp.roots

import io.github.taza67.mcp.codec.CodecAssertions
import io.github.taza67.mcp.codec.mcp.Messages
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.jsonrpc.StringRequestId
import io.github.taza67.mcp.protocol.mcp.McpRequest
import io.github.taza67.mcp.protocol.mcp.MetaObject
import io.github.taza67.mcp.protocol.mcp.elicitation.Elicitation
import io.github.taza67.mcp.protocol.mcp.roots.ListRootsRequest
import io.github.taza67.mcp.protocol.mcp.roots.ListRootsRequestParams
import io.github.taza67.mcp.protocol.mcp.roots.ListRootsResult
import io.github.taza67.mcp.protocol.mcp.roots.Root
import munit.FunSuite



class RootsSuite extends FunSuite with CodecAssertions {

  test("Root round-trips") {
    assertRoundTrip(
      Root(
        uri = "file:///project",
        name = Some("project"),
        meta = Some(MetaObject(Map("k" -> JsonString("v"))))
      )
    )(Roots.fromRoot, Roots.toRoot)
  }

  test("ListRootsResult round-trips") {
    assertRoundTrip(
      ListRootsResult(
        roots = List(
          Root(uri = "file:///a"),
          Root(uri = "file:///b", name = Some("b"))
        )
      )
    )(Roots.fromListRootsResult, Roots.toListRootsResult)
  }

  test("ListRootsRequestParams round-trips with meta") {
    assertRoundTrip(
      ListRootsRequestParams(meta = Some(MetaObject(Map("k" -> JsonString("v")))))
    )(Roots.fromListRootsRequestParams, Roots.toListRootsRequestParams)
  }

  test("ListRootsRequest round-trips without params") {
    assertRoundTrip(
      ListRootsRequest(id = StringRequestId("list-roots-example"))
    )(Roots.fromListRootsRequest, Roots.toListRootsRequest)
  }

  test("ListRootsRequest round-trips with params") {
    assertRoundTrip(
      ListRootsRequest(
        id = StringRequestId("1"),
        params = Some(ListRootsRequestParams(meta = Some(MetaObject(Map("k" -> JsonString("v"))))))
      )
    )(Roots.fromListRootsRequest, Roots.toListRootsRequest)
  }

  test("toListRootsRequest rejects wrong method") {
    val raw = Messages.fromRequest(
      McpRequest(method = Elicitation.create, id = StringRequestId("1"), params = None)
    )
    assert(Roots.toListRootsRequest(raw).isLeft)
  }

  test("toListRootsResult rejects missing roots") {
    assert(Roots.toListRootsResult(JsonObject(Map.empty)).isLeft)
  }
}
