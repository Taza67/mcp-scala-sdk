package io.github.taza67.mcp.codec.mcp.lists

import io.github.taza67.mcp.codec.CodecAssertions
import io.github.taza67.mcp.codec.TestSupport
import io.github.taza67.mcp.codec.mcp.Messages
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.json.JsonValue
import io.github.taza67.mcp.protocol.jsonrpc.StringRequestId
import io.github.taza67.mcp.protocol.mcp.Cursor
import io.github.taza67.mcp.protocol.mcp.McpRequest
import io.github.taza67.mcp.protocol.mcp.PaginatedRequestParams
import io.github.taza67.mcp.protocol.mcp.prompts.ListPromptsRequest
import io.github.taza67.mcp.protocol.mcp.prompts.Prompts
import io.github.taza67.mcp.protocol.mcp.resources.ListResourcesRequest
import io.github.taza67.mcp.protocol.mcp.resources.ListResourceTemplatesRequest
import io.github.taza67.mcp.protocol.mcp.resources.Resources
import io.github.taza67.mcp.protocol.mcp.tools.ListToolsRequest
import io.github.taza67.mcp.protocol.mcp.tools.Tools
import munit.FunSuite



class PaginatedListsSuite extends FunSuite with CodecAssertions {

  private val page2 = PaginatedRequestParams(
    meta = TestSupport.requestMeta,
    cursor = Some(Cursor("page-2")),
    fields = JsonObject(Map("filter" -> JsonString("x")))
  )

  private val firstPage = PaginatedRequestParams(meta = TestSupport.requestMeta)

  test("ListToolsRequest round-trips with cursor") {
    assertRoundTrip(
      ListToolsRequest(id = StringRequestId("1"), params = page2)
    )(PaginatedLists.fromListToolsRequest, PaginatedLists.toListToolsRequest)
  }

  test("ListToolsRequest round-trips first page without cursor") {
    assertRoundTrip(
      ListToolsRequest(id = StringRequestId("1"), params = firstPage)
    )(PaginatedLists.fromListToolsRequest, PaginatedLists.toListToolsRequest)
  }

  test("ListPromptsRequest round-trips with cursor") {
    assertRoundTrip(
      ListPromptsRequest(id = StringRequestId("2"), params = page2)
    )(PaginatedLists.fromListPromptsRequest, PaginatedLists.toListPromptsRequest)
  }

  test("ListResourcesRequest round-trips with cursor") {
    assertRoundTrip(
      ListResourcesRequest(id = StringRequestId("3"), params = page2)
    )(PaginatedLists.fromListResourcesRequest, PaginatedLists.toListResourcesRequest)
  }

  test("ListResourceTemplatesRequest round-trips with cursor") {
    assertRoundTrip(
      ListResourceTemplatesRequest(id = StringRequestId("4"), params = page2)
    )(
      PaginatedLists.fromListResourceTemplatesRequest,
      PaginatedLists.toListResourceTemplatesRequest
    )
  }

  test("toListToolsRequest rejects wrong method") {
    val raw = PaginatedLists.fromListPromptsRequest(
      ListPromptsRequest(id = StringRequestId("1"), params = firstPage)
    )
    assert(PaginatedLists.toListToolsRequest(raw).isLeft)
  }

  test("toListToolsRequest rejects missing params") {
    val raw = Messages.fromRequest(
      McpRequest(method = Tools.list, id = StringRequestId("1"), params = None)
    )
    assert(PaginatedLists.toListToolsRequest(raw).isLeft)
  }

  test("encoded ListToolsRequest keeps cursor at params top level") {
    val raw = PaginatedLists.fromListToolsRequest(
      ListToolsRequest(id = StringRequestId("1"), params = page2)
    )
    val params = raw.value("params") match {
      case o: JsonObject => o
      case other         => fail(s"expected params object, got $other")
    }
    assertEquals(params.value.get(PaginatedRequestParams.CursorKey), Some(JsonString("page-2")))
    assertEquals(params.value.get("filter"), Some(JsonString("x")))
  }

  test("sibling list codecs use distinct methods") {
    def methodOf(raw: JsonObject): JsonValue =
      raw.value.getOrElse("method", fail("missing method"))

    assertEquals(
      methodOf(
        PaginatedLists.fromListToolsRequest(
          ListToolsRequest(id = StringRequestId("1"), params = firstPage)
        )
      ),
      JsonString(Tools.list.value)
    )
    assertEquals(
      methodOf(
        PaginatedLists.fromListPromptsRequest(
          ListPromptsRequest(id = StringRequestId("1"), params = firstPage)
        )
      ),
      JsonString(Prompts.list.value)
    )
    assertEquals(
      methodOf(
        PaginatedLists.fromListResourcesRequest(
          ListResourcesRequest(id = StringRequestId("1"), params = firstPage)
        )
      ),
      JsonString(Resources.list.value)
    )
    assertEquals(
      methodOf(
        PaginatedLists.fromListResourceTemplatesRequest(
          ListResourceTemplatesRequest(id = StringRequestId("1"), params = firstPage)
        )
      ),
      JsonString(Resources.templatesList.value)
    )
  }
}
