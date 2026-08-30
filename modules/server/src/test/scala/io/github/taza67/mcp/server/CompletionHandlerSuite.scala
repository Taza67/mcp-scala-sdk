package io.github.taza67.mcp.server

import io.github.taza67.mcp.codec.mcp.completion.{Completion => CompletionCodec}
import io.github.taza67.mcp.codec.mcp.discover.{Discover => DiscoverCodec}
import io.github.taza67.mcp.codec.mcp.tools.{Tools => ToolsCodec}
import io.github.taza67.mcp.protocol.json.JsonNumber
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.json.JsonValue
import io.github.taza67.mcp.protocol.jsonrpc.ApplicationError
import io.github.taza67.mcp.protocol.jsonrpc.Error
import io.github.taza67.mcp.protocol.jsonrpc.InternalError
import io.github.taza67.mcp.protocol.jsonrpc.InvalidParamsError
import io.github.taza67.mcp.protocol.jsonrpc.MethodNotFoundError
import io.github.taza67.mcp.protocol.jsonrpc.RequestId
import io.github.taza67.mcp.protocol.jsonrpc.StringRequestId
import io.github.taza67.mcp.protocol.mcp.ClientCapabilities
import io.github.taza67.mcp.protocol.mcp.CompleteResultType
import io.github.taza67.mcp.protocol.mcp.Implementation
import io.github.taza67.mcp.protocol.mcp.McpErrorResponse
import io.github.taza67.mcp.protocol.mcp.McpProtocolVersion20260728
import io.github.taza67.mcp.protocol.mcp.McpRequest
import io.github.taza67.mcp.protocol.mcp.McpSuccessResponse
import io.github.taza67.mcp.protocol.mcp.MetaObject
import io.github.taza67.mcp.protocol.mcp.PaginatedRequestParams
import io.github.taza67.mcp.protocol.mcp.RequestMeta
import io.github.taza67.mcp.protocol.mcp.RequestParams
import io.github.taza67.mcp.protocol.mcp.ResultMeta
import io.github.taza67.mcp.protocol.mcp.TextContent
import io.github.taza67.mcp.protocol.mcp.discover.ServerDiscover
import io.github.taza67.mcp.protocol.mcp.tools.{Tools => ToolMethods}
import io.github.taza67.mcp.protocol.mcp.tools.CallToolRequestParams
import io.github.taza67.mcp.protocol.mcp.tools.CallToolResult
import io.github.taza67.mcp.protocol.mcp.tools.Tool
import io.github.taza67.mcp.protocol.mcp.tools.ToolInputSchema
import io.github.taza67.mcp.protocol.mcp.completion.{Completion => CompletionMethods}
import io.github.taza67.mcp.protocol.mcp.completion.CompleteRequestParams
import io.github.taza67.mcp.protocol.mcp.completion.CompleteResult
import io.github.taza67.mcp.protocol.mcp.completion.CompletionPayload
import io.github.taza67.mcp.protocol.mcp.completion.CompletionReference
import io.github.taza67.mcp.protocol.mcp.completion.CompletionArgument
import io.github.taza67.mcp.protocol.mcp.completion.CompletionContext
import io.github.taza67.mcp.protocol.mcp.completion.PromptReference
import munit.FunSuite



class CompletionHandlerSuite extends FunSuite {

  private val requestId = StringRequestId("c-1")

  private val requestMeta = RequestMeta(
    protocolVersion = McpProtocolVersion20260728,
    clientCapabilities = ClientCapabilities()
  )

  private val refField = JsonObject(
    Map(
      CompletionReference.TypeKey -> JsonString(PromptReference.TypeValue),
      PromptReference.NameKey     -> JsonString("git-commit")
    )
  )

  private val argumentField = JsonObject(
    Map(
      CompletionArgument.NameKey  -> JsonString("prefix"),
      CompletionArgument.ValueKey -> JsonString("f")
    )
  )

  private def params(fields: (String, JsonValue)*): RequestParams =
    RequestParams(meta = requestMeta, fields = JsonObject(Map(fields: _*)))

  private def request(fields: RequestParams): McpRequest =
    McpRequest(method = CompletionMethods.complete, id = requestId, params = Some(fields))

  private val validParams = params(
    CompleteRequestParams.RefKey      -> refField,
    CompleteRequestParams.ArgumentKey -> argumentField
  )

  private val payload = CompletionPayload(
    values = List("main", "feature/x"),
    total = Some(10L),
    hasMore = Some(true)
  )

  private def serverWith(
      run: CompleteRequestParams => Either[Error, CompleteResult]
  ): McpServer =
    McpServer(
      HandlerRegistryInMemory(
        Map(CompletionMethods.complete -> Handler.complete(run))
      )
    )

  test("registered complete handler returns a correlated typed result") {
    var seen: Option[CompleteRequestParams] = None
    val resultMeta = ResultMeta(
      extensions = MetaObject(Map("x-ext" -> JsonString("1")))
    )
    val server = serverWith { params =>
      seen = Some(params)
      Right(CompleteResult(completion = payload, meta = Some(resultMeta)))
    }
    val response = server.handle(request(validParams))

    assertEquals(
      seen,
      Some(
        CompleteRequestParams(
          meta = requestMeta,
          ref = PromptReference(name = "git-commit"),
          argument = CompletionArgument(name = "prefix", value = "f")
        )
      )
    )
    response match {
      case success: McpSuccessResponse =>
        assertEquals(success.id, requestId: RequestId)
        assertEquals(success.result.resultType, CompleteResultType)
        assertEquals(success.result.meta, Some(resultMeta))
        assertEquals(
          CompletionCodec.toCompleteResult(success.result.fields),
          Right(CompleteResult(completion = payload))
        )
      case other =>
        fail(s"expected success response, got $other")
    }
  }

  test("generic Handler.of encodes CompleteResult through the implicit encoder") {
    import Results.completeResultEncoder
    val handler = Handler.of[CompleteResult](_ =>
      Right(CompleteResult(completion = CompletionPayload(values = List("a"))))
    )
    val result = handler.execute(Some(validParams))
    assert(result.isRight)
    result.foreach { r =>
      assertEquals(r.resultType, CompleteResultType)
      assertEquals(
        CompletionCodec.toCompleteResult(r.fields),
        Right(CompleteResult(completion = CompletionPayload(values = List("a"))))
      )
    }
  }

  test("missing, paginated, and malformed params reject without invoking the callback") {
    var ran = false
    val server = serverWith { _ => ran = true; Right(CompleteResult(completion = payload)) }

    val secret = "secret-context-key"
    val cases = List(
      McpRequest(method = CompletionMethods.complete, id = requestId),
      McpRequest(
        method = CompletionMethods.complete,
        id = requestId,
        params = Some(PaginatedRequestParams(meta = requestMeta))
      ),
      request(
        params(
          CompleteRequestParams.RefKey -> JsonObject(
            Map(CompletionReference.TypeKey -> JsonString(PromptReference.TypeValue))
          ),
          CompleteRequestParams.ArgumentKey -> argumentField
        )
      ),
      request(
        params(
          CompleteRequestParams.RefKey      -> refField,
          CompleteRequestParams.ArgumentKey -> JsonObject(
            Map(CompletionArgument.NameKey -> JsonString("prefix"))
          )
        )
      ),
      request(
        params(
          CompleteRequestParams.RefKey      -> refField,
          CompleteRequestParams.ArgumentKey -> argumentField,
          CompleteRequestParams.ContextKey  -> JsonObject(
            Map(
              CompletionContext.ArgumentsKey -> JsonObject(
                Map(secret -> JsonNumber(1))
              )
            )
          )
        )
      )
    )

    cases.foreach { req =>
      val response = server.handle(req)
      assert(!ran, s"callback ran for $req")
      response match {
        case error: McpErrorResponse =>
          assertEquals(error.id, Option[RequestId](requestId))
          assert(error.error.isInstanceOf[InvalidParamsError])
          assert(!error.error.message.contains(secret))
          assert(!error.error.data.exists(_.toString.contains(secret)))
        case other =>
          fail(s"expected error response, got $other")
      }
    }
  }

  test("intentional callback errors are preserved on the wire") {
    val server = serverWith { _ =>
      Left(ApplicationError(code = 7, message = "nope"))
    }
    server.handle(request(validParams)) match {
      case error: McpErrorResponse =>
        assertEquals(error.id, Option[RequestId](requestId))
        assertEquals(error.error, ApplicationError(code = 7, message = "nope"))
      case other =>
        fail(s"expected error response, got $other")
    }
  }

  test("throwing callback degrades to InternalError and the server survives") {
    var calls = 0
    val server = serverWith { _ =>
      calls += 1
      if (calls == 1) throw new RuntimeException("secret-crash-detail")
      else Right(CompleteResult(completion = payload))
    }
    server.handle(request(validParams)) match {
      case error: McpErrorResponse =>
        assertEquals(error.id, Option[RequestId](requestId))
        assert(error.error.isInstanceOf[InternalError])
        assert(!error.error.message.contains("secret-crash-detail"))
      case other =>
        fail(s"expected error response, got $other")
    }
    server.handle(request(validParams)) match {
      case success: McpSuccessResponse =>
        assertEquals(success.id, requestId: RequestId)
      case other =>
        fail(s"expected success response, got $other")
    }
  }

  private def discoverRequest: McpRequest =
    McpRequest(
      method = ServerDiscover.method,
      id = requestId,
      params = Some(RequestParams(meta = requestMeta))
    )

  private val impl = Implementation(name = "ex", version = "1")

  test("factory without completion advertises no capability and rejects the method") {
    val server = McpServer(info = impl)

    server.handle(discoverRequest) match {
      case success: McpSuccessResponse =>
        val decoded = DiscoverCodec.toDiscoverResult(success.result.fields)
        assert(decoded.isRight)
        decoded.foreach { result =>
          assertEquals(result.capabilities.completions, None)
          assertEquals(result.capabilities.tools, None)
        }
      case other =>
        fail(s"expected success response, got $other")
    }
    server.handle(request(validParams)) match {
      case error: McpErrorResponse =>
        assertEquals(error.id, Option[RequestId](requestId))
        assert(error.error.isInstanceOf[MethodNotFoundError])
      case other =>
        fail(s"expected error response, got $other")
    }
  }

  test("factory with completion registers the method and advertises it, no tools") {
    val server = McpServer(
      info = impl,
      completion = Some(_ => Right(CompleteResult(completion = payload)))
    )

    server.handle(discoverRequest) match {
      case success: McpSuccessResponse =>
        val decoded = DiscoverCodec.toDiscoverResult(success.result.fields)
        assert(decoded.isRight)
        decoded.foreach { result =>
          assertEquals(result.capabilities.completions, Some(JsonObject(Map.empty)))
          assertEquals(result.capabilities.tools, None)
        }
      case other =>
        fail(s"expected success response, got $other")
    }
    server.handle(request(validParams)) match {
      case success: McpSuccessResponse =>
        assertEquals(success.id, requestId: RequestId)
        assertEquals(
          success.result.meta.flatMap(_.serverInfo),
          Some(impl)
        )
      case other =>
        fail(s"expected success response, got $other")
    }
    server.handle(
      McpRequest(method = ToolMethods.list, id = requestId)
    ) match {
      case error: McpErrorResponse =>
        assert(error.error.isInstanceOf[MethodNotFoundError])
      case other =>
        fail(s"expected error response, got $other")
    }
  }

  test("completion meta merge fills missing identity and preserves supplied values") {
    val extensions = MetaObject(Map("x-ext" -> JsonString("1")))
    val suppliedOnly = McpServer(
      info = impl,
      completion = Some(_ =>
        Right(
          CompleteResult(
            completion = payload,
            meta = Some(ResultMeta(extensions = extensions))
          )
        )
      )
    )
    suppliedOnly.handle(request(validParams)) match {
      case success: McpSuccessResponse =>
        assertEquals(
          success.result.meta,
          Some(ResultMeta(serverInfo = Some(impl), extensions = extensions))
        )
      case other =>
        fail(s"expected success response, got $other")
    }

    val explicit = Implementation(name = "other", version = "9")
    val explicitServer = McpServer(
      info = impl,
      completion = Some(_ =>
        Right(
          CompleteResult(
            completion = payload,
            meta = Some(ResultMeta(serverInfo = Some(explicit)))
          )
        )
      )
    )
    explicitServer.handle(request(validParams)) match {
      case success: McpSuccessResponse =>
        assertEquals(
          success.result.meta.flatMap(_.serverInfo),
          Some(explicit)
        )
      case other =>
        fail(s"expected success response, got $other")
    }
  }

  test("tools and completion coexist; three-arg construction stays valid") {
    val ping = ServerTool(
      Tool(name = "ping", inputSchema = ToolInputSchema()),
      _ => Right(CallToolResult(content = List(TextContent(text = "pong"))))
    )
    // Positional three-argument construction must keep compiling.
    val server = McpServer(
      impl,
      Seq(ping),
      Instructions.none,
      completion = Some(_ => Right(CompleteResult(completion = payload)))
    )

    server.handle(
      McpRequest(
        method = ToolMethods.call,
        id = requestId,
        params = Some(
          params(CallToolRequestParams.NameKey -> JsonString("ping"))
        )
      )
    ) match {
      case success: McpSuccessResponse =>
        assertEquals(
          ToolsCodec.toCallToolResult(success.result.fields),
          Right(CallToolResult(content = List(TextContent(text = "pong"))))
        )
      case other =>
        fail(s"expected success response, got $other")
    }
    server.handle(request(validParams)) match {
      case success: McpSuccessResponse =>
        assertEquals(success.id, requestId: RequestId)
      case other =>
        fail(s"expected success response, got $other")
    }
  }

  test("the 100-item values bound still fails fast at construction") {
    intercept[IllegalArgumentException] {
      CompletionPayload(values = List.fill(CompletionPayload.MaxValues + 1)("v"))
    }
    assertEquals(
      CompletionCodec.toCompleteResult(
        JsonObject(
          Map(
            CompleteResult.CompletionKey -> CompletionCodec.fromCompletionPayload(
              CompletionPayload(values = List.fill(CompletionPayload.MaxValues)("v"))
            )
          )
        )
      ).map(_.completion.values.size),
      Right(CompletionPayload.MaxValues)
    )
  }
}
