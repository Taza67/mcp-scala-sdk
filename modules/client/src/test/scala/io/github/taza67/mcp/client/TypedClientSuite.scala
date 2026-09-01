package io.github.taza67.mcp.client

import io.github.taza67.mcp.codec.mcp.completion.{Completion => CompletionCodec}
import io.github.taza67.mcp.protocol.json.JsonArray
import io.github.taza67.mcp.protocol.json.JsonBool
import io.github.taza67.mcp.protocol.json.JsonNumber
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.jsonrpc.ApplicationError
import io.github.taza67.mcp.protocol.jsonrpc.NumberRequestId
import io.github.taza67.mcp.protocol.mcp.ClientCapabilities
import io.github.taza67.mcp.protocol.mcp.CompleteResultType
import io.github.taza67.mcp.protocol.mcp.CustomResultType
import io.github.taza67.mcp.protocol.mcp.Implementation
import io.github.taza67.mcp.protocol.mcp.McpErrorResponse
import io.github.taza67.mcp.protocol.mcp.McpProtocolVersion20260728
import io.github.taza67.mcp.protocol.mcp.McpRequest
import io.github.taza67.mcp.protocol.mcp.McpResponse
import io.github.taza67.mcp.protocol.mcp.McpSuccessResponse
import io.github.taza67.mcp.protocol.mcp.MetaObject
import io.github.taza67.mcp.protocol.mcp.PrivateCacheScope
import io.github.taza67.mcp.protocol.mcp.RequestMeta
import io.github.taza67.mcp.protocol.mcp.RequestParams
import io.github.taza67.mcp.protocol.mcp.Result
import io.github.taza67.mcp.protocol.mcp.ResultMeta
import io.github.taza67.mcp.protocol.mcp.ServerCapabilities
import io.github.taza67.mcp.protocol.mcp.ToolsCapability
import io.github.taza67.mcp.protocol.mcp.completion.{Completion => CompletionMethods}
import io.github.taza67.mcp.protocol.mcp.completion.CompleteRequestParams
import io.github.taza67.mcp.protocol.mcp.completion.CompleteResult
import io.github.taza67.mcp.protocol.mcp.completion.CompletionArgument
import io.github.taza67.mcp.protocol.mcp.completion.CompletionContext
import io.github.taza67.mcp.protocol.mcp.completion.CompletionPayload
import io.github.taza67.mcp.protocol.mcp.completion.PromptReference
import io.github.taza67.mcp.protocol.mcp.discover.DiscoverResult
import io.github.taza67.mcp.protocol.mcp.discover.ServerDiscover
import munit.FunSuite



class TypedClientSuite extends FunSuite {

  private val requestMeta = RequestMeta(
    protocolVersion = McpProtocolVersion20260728,
    clientCapabilities = ClientCapabilities()
  )

  private final class FixedTransport(
      respond: McpRequest => Either[ClientError, McpResponse]
  ) extends ClientTransport {
    var lastRequest: Option[McpRequest] = None

    def exchange(request: McpRequest): Either[ClientError, McpResponse] = {
      lastRequest = Some(request)
      respond(request)
    }
  }

  test("discover decodes literal fields and attaches envelope type and meta") {
    val serverInfo = Implementation(name = "srv", version = "2")
    val envelopeMeta = ResultMeta(
      serverInfo = Some(serverInfo),
      extensions = MetaObject(Map("x-ext" -> JsonString("1")))
    )
    val fields = JsonObject(
      Map(
        "supportedVersions" -> JsonArray(
          List(JsonString("2026-07-28"), JsonString("2025-11-25"))
        ),
        "capabilities"      -> JsonObject(Map("tools" -> JsonObject(Map.empty))),
        "ttlMs"             -> JsonNumber(60000),
        "cacheScope"        -> JsonString("private"),
        "instructions"      -> JsonString("Use tools sparingly.")
      )
    )
    val transport = new FixedTransport(request =>
      Right(
        McpSuccessResponse(
          Result(
            resultType = CustomResultType("future-kind"),
            fields = fields,
            meta = Some(envelopeMeta)
          ),
          request.id
        )
      )
    )
    val client = McpClient(transport)

    assertEquals(
      client.discover(requestMeta),
      Right(
        DiscoverResult(
          supportedVersions = List("2026-07-28", "2025-11-25"),
          capabilities = ServerCapabilities(tools = Some(ToolsCapability())),
          ttlMs = 60000L,
          cacheScope = PrivateCacheScope,
          instructions = Some("Use tools sparingly."),
          resultType = CustomResultType("future-kind"),
          meta = Some(envelopeMeta)
        )
      )
    )
    transport.lastRequest match {
      case Some(request) =>
        assertEquals(request.method, ServerDiscover.method)
        assertEquals(request.id, NumberRequestId(1L))
        assertEquals(request.params, Some(RequestParams(meta = requestMeta)))
      case None =>
        fail("transport saw no request")
    }
  }

  test("complete sends codec-derived params and decodes the literal payload") {
    val metaWithExtensions = requestMeta.copy(
      extensions = MetaObject(Map("x-req" -> JsonString("7")))
    )
    val supplied = CompleteRequestParams(
      meta = metaWithExtensions,
      ref = PromptReference(name = "git-commit"),
      argument = CompletionArgument(name = "prefix", value = "f"),
      context = Some(
        CompletionContext(arguments = Some(Map("other" -> "done")))
      )
    )
    val literalResult = Result(
      resultType = CustomResultType("future-kind"),
      fields = JsonObject(
        Map(
          "completion" -> JsonObject(
            Map(
              "values"  -> JsonArray(List(JsonString("main"))),
              "total"   -> JsonNumber(1),
              "hasMore" -> JsonBool(false)
            )
          )
        )
      ),
      meta = Some(ResultMeta(serverInfo = Some(Implementation("srv", "1"))))
    )
    val transport = new FixedTransport(request =>
      Right(McpSuccessResponse(literalResult, request.id))
    )
    val client = McpClient(transport)

    assertEquals(
      client.complete(supplied),
      Right(
        CompleteResult(
          completion = CompletionPayload(
            values = List("main"),
            total = Some(1L),
            hasMore = Some(false)
          ),
          resultType = CustomResultType("future-kind"),
          meta = literalResult.meta
        )
      )
    )
    transport.lastRequest match {
      case Some(request) =>
        assertEquals(request.method, CompletionMethods.complete)
        assertEquals(request.id, NumberRequestId(1L))
        request.params match {
          case Some(params: RequestParams) =>
            assertEquals(
              CompletionCodec.toCompleteRequestParams(params),
              Right(supplied)
            )
          case other =>
            fail(s"expected plain request params, got $other")
        }
      case None =>
        fail("transport saw no request")
    }
  }

  test("malformed results fail as InvalidResult without echoing contents") {
    val secret = "secret-result-fragment"
    val badDiscoverFields = JsonObject(
      Map(
        "supportedVersions" -> JsonString(secret),
        "capabilities"      -> JsonObject(Map.empty),
        "ttlMs"             -> JsonNumber(0),
        "cacheScope"        -> JsonString("public")
      )
    )
    val badCompletionFields = JsonObject(
      Map("completion" -> JsonObject(Map("values" -> JsonString(secret))))
    )
    val discoverClient = McpClient(new FixedTransport(request =>
      Right(
        McpSuccessResponse(Result(CompleteResultType, badDiscoverFields), request.id)
      )
    ))
    val completionClient = McpClient(new FixedTransport(request =>
      Right(
        McpSuccessResponse(Result(CompleteResultType, badCompletionFields), request.id)
      )
    ))

    assertEquals(
      discoverClient.discover(requestMeta),
      Left(ClientError.InvalidResult)
    )
    assertEquals(
      completionClient.complete(
        CompleteRequestParams(
          meta = requestMeta,
          ref = PromptReference(name = "git-commit"),
          argument = CompletionArgument(name = "prefix", value = "f")
        )
      ),
      Left(ClientError.InvalidResult)
    )
  }

  test("remote errors pass through typed calls unchanged") {
    val remote = ApplicationError(code = 9, message = "no")
    val transport = new FixedTransport(request =>
      Right(McpErrorResponse(remote, request.id))
    )
    val client = McpClient(transport)

    assertEquals(
      client.discover(requestMeta),
      Left(ClientError.RemoteError(remote))
    )
  }

  test("request meta extensions travel inside params without session inference") {
    val metaWithExtensions = requestMeta.copy(
      extensions = MetaObject(Map("x-req" -> JsonString("7")))
    )
    val transport = new FixedTransport(request =>
      Right(
        McpSuccessResponse(
          Result(
            resultType = CompleteResultType,
            fields = JsonObject(
              Map(
                "supportedVersions" -> JsonArray(List(JsonString("2026-07-28"))),
                "capabilities"      -> JsonObject(Map.empty),
                "ttlMs"             -> JsonNumber(0),
                "cacheScope"        -> JsonString("public")
              )
            )
          ),
          request.id
        )
      )
    )
    val client = McpClient(transport)

    assert(client.discover(metaWithExtensions).isRight)
    transport.lastRequest match {
      case Some(request) =>
        assertEquals(
          request.params,
          Some(RequestParams(meta = metaWithExtensions))
        )
      case None =>
        fail("transport saw no request")
    }
  }
}
