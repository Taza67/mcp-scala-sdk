package io.github.taza67.mcp.transport.http

import io.github.taza67.mcp.codec.DecodingError
import io.github.taza67.mcp.protocol.json.JsonBool
import io.github.taza67.mcp.protocol.json.JsonNumber
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.json.JsonValue
import io.github.taza67.mcp.protocol.jsonrpc.ErrorCode
import io.github.taza67.mcp.protocol.jsonrpc.HeaderMismatchError
import io.github.taza67.mcp.protocol.jsonrpc.Method
import io.github.taza67.mcp.protocol.jsonrpc.NumberRequestId
import io.github.taza67.mcp.protocol.mcp.ClientCapabilities
import io.github.taza67.mcp.protocol.mcp.Cursor
import io.github.taza67.mcp.protocol.mcp.McpProtocolVersion20260728
import io.github.taza67.mcp.protocol.mcp.McpRequest
import io.github.taza67.mcp.protocol.mcp.McpRequestParams
import io.github.taza67.mcp.protocol.mcp.PaginatedRequestParams
import io.github.taza67.mcp.protocol.mcp.RequestMeta
import io.github.taza67.mcp.protocol.mcp.RequestParams
import io.github.taza67.mcp.protocol.mcp.discover.ServerDiscover
import io.github.taza67.mcp.protocol.mcp.prompts.Prompts
import io.github.taza67.mcp.protocol.mcp.resources.Resources
import io.github.taza67.mcp.protocol.mcp.tools.Tools
import munit.FunSuite



class RequestHeadersSuite extends FunSuite {

  private val invalid = Left(DecodingError("Invalid MCP request headers"))
  private val mismatch = Left(HeaderMismatchError("HTTP header mismatch"))

  private val meta = RequestMeta(
    protocolVersion = McpProtocolVersion20260728,
    clientCapabilities = ClientCapabilities()
  )

  private def request(
      method: Method,
      fields: Map[String, JsonValue],
      paginated: Boolean = false
  ): McpRequest = {
    val params: McpRequestParams =
      if (paginated)
        PaginatedRequestParams(meta, Some(Cursor("c-1")), JsonObject(fields))
      else RequestParams(meta, JsonObject(fields))
    McpRequest(method, NumberRequestId(1), Some(params))
  }

  private def body(method: String, params: JsonObject): JsonObject =
    JsonObject(Map("method" -> JsonString(method), "params" -> params))

  private def metaObject(version: String): JsonObject =
    JsonObject(Map(RequestMeta.ProtocolVersionKey -> JsonString(version)))

  private val callBody = body(
    "tools/call",
    JsonObject(
      Map(
        "name" -> JsonString("safe-tool"),
        RequestParams.MetaKey -> metaObject("2026-07-28")
      )
    )
  )

  private val callHeaders = Map(
    "mcp-protocol-version" -> List("2026-07-28"),
    "MCP-Method" -> List("tools/call"),
    "mcp-name" -> List("safe-tool"),
    "X-Custom" -> List("ignored")
  )

  test("fromRequest emits exact protocol and method headers without a name") {
    assertEquals(
      RequestHeaders.fromRequest(request(ServerDiscover.method, Map.empty)),
      Right(Map("MCP-Protocol-Version" -> "2026-07-28", "Mcp-Method" -> "server/discover"))
    )
    assertEquals(
      RequestHeaders.fromRequest(request(Tools.list, Map.empty, paginated = true)),
      Right(Map("MCP-Protocol-Version" -> "2026-07-28", "Mcp-Method" -> "tools/list"))
    )
  }

  test("fromRequest encodes name fields for call, get, and read") {
    assertEquals(
      RequestHeaders.fromRequest(
        request(Tools.call, Map("name" -> JsonString("safe-tool")))
      ),
      Right(
        Map(
          "MCP-Protocol-Version" -> "2026-07-28",
          "Mcp-Method" -> "tools/call",
          "Mcp-Name" -> "safe-tool"
        )
      )
    )
    assertEquals(
      RequestHeaders.fromRequest(
        request(Prompts.get, Map("name" -> JsonString("caf\u00e9")))
      ),
      Right(
        Map(
          "MCP-Protocol-Version" -> "2026-07-28",
          "Mcp-Method" -> "prompts/get",
          "Mcp-Name" -> "=?base64?Y2Fmw6k=?="
        )
      )
    )
    assertEquals(
      RequestHeaders.fromRequest(
        request(Resources.read, Map("uri" -> JsonString("file:///a\nb")))
      ),
      Right(
        Map(
          "MCP-Protocol-Version" -> "2026-07-28",
          "Mcp-Method" -> "resources/read",
          "Mcp-Name" -> "=?base64?ZmlsZTovLy9hCmI=?="
        )
      )
    )
  }

  test("fromRequest rejects missing params, unsafe method, and bad name fields") {
    assertEquals(
      RequestHeaders.fromRequest(McpRequest(ServerDiscover.method, NumberRequestId(1))),
      invalid
    )
    assertEquals(
      RequestHeaders.fromRequest(request(Method("bad\nmethod"), Map.empty)),
      invalid
    )
    assertEquals(
      RequestHeaders.fromRequest(request(Method(""), Map.empty)),
      invalid
    )
    assertEquals(
      RequestHeaders.fromRequest(request(Tools.call, Map.empty)),
      invalid
    )
    assertEquals(
      RequestHeaders.fromRequest(request(Tools.call, Map("name" -> JsonNumber(1)))),
      invalid
    )
  }

  test("validate accepts case-insensitive names, case-sensitive values") {
    assertEquals(RequestHeaders.validate(callBody, callHeaders), Right(()))
    // Encoded name header decodes before the literal equality check.
    val unicodeBody = body(
      "prompts/get",
      JsonObject(
        Map(
          "name" -> JsonString("caf\u00e9"),
          RequestParams.MetaKey -> metaObject("2026-07-28")
        )
      )
    )
    val unicodeHeaders = Map(
      "MCP-Protocol-Version" -> List("2026-07-28"),
      "Mcp-Method" -> List("prompts/get"),
      "Mcp-Name" -> List("=?base64?Y2Fmw6k=?=")
    )
    assertEquals(RequestHeaders.validate(unicodeBody, unicodeHeaders), Right(()))
    // Value case must match exactly.
    assertEquals(
      RequestHeaders.validate(
        callBody,
        callHeaders.updated("MCP-Method", List("Tools/Call"))
      ),
      mismatch
    )
  }

  test("validate rejects missing headers, mismatches, and malformed name values") {
    assertEquals(
      RequestHeaders.validate(callBody, callHeaders - "mcp-protocol-version"),
      mismatch
    )
    assertEquals(
      RequestHeaders.validate(callBody, callHeaders - "MCP-Method"),
      mismatch
    )
    assertEquals(RequestHeaders.validate(callBody, callHeaders - "mcp-name"), mismatch)
    assertEquals(
      RequestHeaders.validate(
        callBody,
        callHeaders.updated("mcp-name", List("other-tool"))
      ),
      mismatch
    )
    // Unicode or encoded-invalid name header values are not literal matches.
    assertEquals(
      RequestHeaders.validate(
        callBody,
        callHeaders.updated("mcp-name", List("=?base64?/w==?="))
      ),
      mismatch
    )
    assertEquals(
      RequestHeaders.validate(
        callBody,
        callHeaders.updated("mcp-name", List("=?base64?!!!?="))
      ),
      mismatch
    )
    // Body name field wrong type.
    val badNameBody = body(
      "tools/call",
      JsonObject(
        Map(
          "name" -> JsonBool(true),
          RequestParams.MetaKey -> metaObject("2026-07-28")
        )
      )
    )
    assertEquals(RequestHeaders.validate(badNameBody, callHeaders), mismatch)
  }

  test("mismatch errors carry the -32020 wire code and no data") {
    val Left(error) = RequestHeaders.validate(callBody, callHeaders - "mcp-name"): @unchecked
    assertEquals(error.code, ErrorCode.HeaderMismatch)
    assertEquals(error.data, None)
  }

  test("validate rejects duplicate header values even when identical") {
    val duplicatedCase = callHeaders ++ Map("MCP-PROTOCOL-VERSION" -> List("2026-07-28"))
    assertEquals(RequestHeaders.validate(callBody, duplicatedCase), mismatch)
    val twoValues = callHeaders.updated("mcp-name", List("safe-tool", "safe-tool"))
    assertEquals(RequestHeaders.validate(callBody, twoValues), mismatch)
  }

  test("validate matches unknown protocol versions and rejects broken bodies") {
    val unknownVersionBody = body(
      "tools/list",
      JsonObject(Map(RequestParams.MetaKey -> metaObject("9999-01-01")))
    )
    val headers = Map(
      "MCP-Protocol-Version" -> List("9999-01-01"),
      "Mcp-Method" -> List("tools/list")
    )
    assertEquals(RequestHeaders.validate(unknownVersionBody, headers), Right(()))

    val noMetaBody = body("tools/list", JsonObject(Map.empty))
    assertEquals(RequestHeaders.validate(noMetaBody, headers), mismatch)
    assertEquals(
      RequestHeaders.validate(JsonObject(Map("method" -> JsonNumber(7))), headers),
      mismatch
    )
    assertEquals(
      RequestHeaders.validate(JsonObject(Map("method" -> JsonString("tools/list"))), headers),
      mismatch
    )
  }
}
