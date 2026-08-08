package io.github.taza67.mcp.codec.mcp.roots

import io.github.taza67.mcp.codec.CodecAssertions
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.mcp.MetaObject
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

  test("toListRootsResult rejects missing roots") {
    assert(Roots.toListRootsResult(JsonObject(Map.empty)).isLeft)
  }
}
