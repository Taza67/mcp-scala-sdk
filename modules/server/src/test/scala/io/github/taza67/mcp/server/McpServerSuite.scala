package io.github.taza67.mcp.server

import io.github.taza67.mcp.codec.mcp.Messages
import io.github.taza67.mcp.codec.mcp.discover.Discover
import io.github.taza67.mcp.codec.mcp.prompts.Prompts
import io.github.taza67.mcp.codec.mcp.resources.Resources
import io.github.taza67.mcp.codec.mcp.roots.Roots
import io.github.taza67.mcp.codec.mcp.tools.Tools
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.jsonrpc.{Method, MethodNotFoundError, StringRequestId}
import io.github.taza67.mcp.protocol.jsonrpc.ApplicationError
import io.github.taza67.mcp.protocol.jsonrpc.Error
import io.github.taza67.mcp.protocol.jsonrpc.InternalError
import io.github.taza67.mcp.protocol.jsonrpc.InvalidParamsError
import io.github.taza67.mcp.protocol.mcp.AssistantRole
import io.github.taza67.mcp.protocol.mcp.ClientCapabilities
import io.github.taza67.mcp.protocol.mcp.CompleteResultType
import io.github.taza67.mcp.protocol.mcp.Cursor
import io.github.taza67.mcp.protocol.mcp.Implementation
import io.github.taza67.mcp.protocol.mcp.InputResponses
import io.github.taza67.mcp.protocol.mcp.McpErrorResponse
import io.github.taza67.mcp.protocol.mcp.McpProtocolVersion20260728
import io.github.taza67.mcp.protocol.mcp.McpRequest
import io.github.taza67.mcp.protocol.mcp.McpResponse
import io.github.taza67.mcp.protocol.mcp.McpSuccessResponse
import io.github.taza67.mcp.protocol.mcp.MetaObject
import io.github.taza67.mcp.protocol.mcp.PaginatedRequestParams
import io.github.taza67.mcp.protocol.mcp.PrivateCacheScope
import io.github.taza67.mcp.protocol.mcp.PublicCacheScope
import io.github.taza67.mcp.protocol.mcp.RequestMeta
import io.github.taza67.mcp.protocol.mcp.RequestParams
import io.github.taza67.mcp.protocol.mcp.Result
import io.github.taza67.mcp.protocol.mcp.ResultMeta
import io.github.taza67.mcp.protocol.mcp.ServerCapabilities
import io.github.taza67.mcp.protocol.mcp.TextContent
import io.github.taza67.mcp.protocol.mcp.TextResourceContents
import io.github.taza67.mcp.protocol.mcp.ToolsCapability
import io.github.taza67.mcp.protocol.mcp.discover.DiscoverResult
import io.github.taza67.mcp.protocol.mcp.discover.ServerDiscover
import io.github.taza67.mcp.protocol.mcp.prompts.{Prompts => PromptMethods}
import io.github.taza67.mcp.protocol.mcp.prompts.GetPromptResult
import io.github.taza67.mcp.protocol.mcp.prompts.PromptMessage
import io.github.taza67.mcp.protocol.mcp.resources.{Resources => ResourceMethods}
import io.github.taza67.mcp.protocol.mcp.resources.ReadResourceResult
import io.github.taza67.mcp.protocol.mcp.roots.{ListRootsResult, Root, Roots => RootsMethods}
import io.github.taza67.mcp.protocol.mcp.tools.{Tools => ToolMethods}
import io.github.taza67.mcp.protocol.mcp.tools.CallToolRequestParams
import io.github.taza67.mcp.protocol.mcp.tools.CallToolResult
import io.github.taza67.mcp.protocol.mcp.tools.ListToolsResult
import io.github.taza67.mcp.protocol.mcp.tools.Tool
import io.github.taza67.mcp.protocol.mcp.tools.ToolInputSchema
import munit.FunSuite



class McpServerSuite extends FunSuite {

  private val requestId = StringRequestId("test")

  test("Unknown method returns an error") {
    val handlerRegistry = HandlerRegistryInMemory(Map.empty)
    val mcpServer = McpServer(handlerRegistry)
    val request = McpRequest(method = Method("unknown"), id = requestId)
    val response = mcpServer.handle(request)

    assertEquals(response, McpErrorResponse(error = MethodNotFoundError(), id = request.id))
  }

  test("Registered handler returns a successful empty result") {
    val ping = Method("ping")
    val handlerRegistry = HandlerRegistryInMemory(
      Map(ping -> Handler.empty(_ => Right(())))
    )
    val mcpServer = McpServer(handlerRegistry)
    val request = McpRequest(method = ping, id = requestId)
    val response = mcpServer.handle(request)

    assertEquals(
      response,
      McpSuccessResponse(result = Result.empty(), id = request.id)
    )
  }

  test("Typed handler result encodes through the server response path") {
    val expected = ListRootsResult(
      roots = List(
        Root(uri = "file:///workspace", name = Some("workspace"))
      )
    )
    val handlerRegistry = HandlerRegistryInMemory(
      Map(
        RootsMethods.list -> Handler.of(_ => Right(expected))(Results.listRootsResultEncoder)
      )
    )
    val mcpServer = McpServer(handlerRegistry)
    val request = McpRequest(method = RootsMethods.list, id = requestId)
    val response = mcpServer.handle(request)

    response match {
      case success: McpSuccessResponse =>
        val result = success.result
        val id = success.id
        assertEquals(id, request.id)
        assertEquals(result.resultType, CompleteResultType)
        assertEquals(Roots.toListRootsResult(result.fields), Right(expected))

        val wire = Messages.fromSuccessResponse(McpSuccessResponse(result = result, id = id))
        val resultObject = wire.value
          .get("result")
          .collect { case o: JsonObject => o }
          .get
        assertEquals(
          resultObject.value.get(Result.ResultTypeKey),
          Some(JsonString(CompleteResultType.value))
        )
        assertEquals(
          Messages.toMessage(wire),
          Right(McpSuccessResponse(result = result, id = id))
        )
      case other =>
        fail(s"expected success response, got $other")
    }
  }

  test("ListTools handler encodes through Results.listTools") {
    val expected = ListToolsResult(
      tools = List(
        Tool(
          name = "ping",
          inputSchema = ToolInputSchema(fields = JsonObject(Map.empty))
        )
      ),
      ttlMs = 0L,
      cacheScope = PublicCacheScope
    )
    val handlerRegistry = HandlerRegistryInMemory(
      Map(
        ToolMethods.list -> Handler.of(_ => Right(expected))(Results.listToolsResultEncoder)
      )
    )
    val mcpServer = McpServer(handlerRegistry)
    val request = McpRequest(method = ToolMethods.list, id = requestId)
    val response = mcpServer.handle(request)

    response match {
      case success: McpSuccessResponse =>
        assertEquals(Tools.toListToolsResult(success.result.fields), Right(expected))
      case other =>
        fail(s"expected success response, got $other")
    }
  }

  test("CallTool handler encodes through Results.callTool") {
    val expected = CallToolResult(content = List(TextContent(text = "pong")))
    val handlerRegistry = HandlerRegistryInMemory(
      Map(
        ToolMethods.call -> Handler.of(_ => Right(expected))(Results.callToolResultEncoder)
      )
    )
    val mcpServer = McpServer(handlerRegistry)
    val request = McpRequest(method = ToolMethods.call, id = requestId)
    val response = mcpServer.handle(request)

    response match {
      case success: McpSuccessResponse =>
        assertEquals(Tools.toCallToolResult(success.result.fields), Right(expected))
        assertEquals(
          Messages.toMessage(Messages.fromSuccessResponse(success)),
          Right(success)
        )
      case other =>
        fail(s"expected success response, got $other")
    }
  }

  test("callTool decodes params before the domain handler") {
    val expected = CallToolResult(content = List(TextContent(text = "pong")))
    val handlerRegistry = HandlerRegistryInMemory(
      Map(
        ToolMethods.call -> Handler.callTool { call =>
          assertEquals(call.name, "ping")
          Right(expected)
        }
      )
    )
    val mcpServer = McpServer(handlerRegistry)
    val request = McpRequest(
      method = ToolMethods.call,
      id = requestId,
      params = Some(
        RequestParams(
          meta = RequestMeta(
            protocolVersion = McpProtocolVersion20260728,
            clientCapabilities = ClientCapabilities()
          ),
          fields = JsonObject(Map(CallToolRequestParams.NameKey -> JsonString("ping")))
        )
      )
    )
    val response = mcpServer.handle(request)

    response match {
      case success: McpSuccessResponse =>
        assertEquals(Tools.toCallToolResult(success.result.fields), Right(expected))
      case other =>
        fail(s"expected success response, got $other")
    }
  }

  test("callTool rejects missing params before the domain handler") {
    var ran = false
    val handlerRegistry = HandlerRegistryInMemory(
      Map(
        ToolMethods.call -> Handler.callTool { _ =>
          ran = true
          Right(CallToolResult(content = List(TextContent(text = "pong"))))
        }
      )
    )
    val mcpServer = McpServer(handlerRegistry)
    val request = McpRequest(method = ToolMethods.call, id = requestId)
    val response = mcpServer.handle(request)

    assertEquals(ran, false)
    response match {
      case error: McpErrorResponse =>
        assert(error.error.isInstanceOf[InvalidParamsError])
      case other =>
        fail(s"expected error response, got $other")
    }
  }

  test("tools routes by name and does not run other tools") {
    val expected = CallToolResult(content = List(TextContent(text = "pong")))
    var otherRan = false
    val handlerRegistry = HandlerRegistryInMemory(
      Map(
        ToolMethods.call -> Handler.tools(
          "ping" -> ((_: ToolCall) => Right(expected)),
          "other" -> ((_: ToolCall) => {
            otherRan = true
            Right(CallToolResult(content = List(TextContent(text = "nope"))))
          })
        )
      )
    )
    val mcpServer = McpServer(handlerRegistry)
    val request = McpRequest(
      method = ToolMethods.call,
      id = requestId,
      params = Some(
        RequestParams(
          meta = RequestMeta(
            protocolVersion = McpProtocolVersion20260728,
            clientCapabilities = ClientCapabilities()
          ),
          fields = JsonObject(Map(CallToolRequestParams.NameKey -> JsonString("ping")))
        )
      )
    )
    val response = mcpServer.handle(request)

    assertEquals(otherRan, false)
    response match {
      case success: McpSuccessResponse =>
        assertEquals(Tools.toCallToolResult(success.result.fields), Right(expected))
      case other =>
        fail(s"expected success response, got $other")
    }
  }

  test("tools rejects an unknown tool name before any tool function") {
    var ran = false
    val handlerRegistry = HandlerRegistryInMemory(
      Map(
        ToolMethods.call -> Handler.tools(
          "ping" -> ((_: ToolCall) => {
            ran = true
            Right(CallToolResult(content = List(TextContent(text = "pong"))))
          })
        )
      )
    )
    val mcpServer = McpServer(handlerRegistry)
    val request = McpRequest(
      method = ToolMethods.call,
      id = requestId,
      params = Some(
        RequestParams(
          meta = RequestMeta(
            protocolVersion = McpProtocolVersion20260728,
            clientCapabilities = ClientCapabilities()
          ),
          fields = JsonObject(Map(CallToolRequestParams.NameKey -> JsonString("nope")))
        )
      )
    )
    val response = mcpServer.handle(request)

    assertEquals(ran, false)
    response match {
      case error: McpErrorResponse =>
        assert(error.error.isInstanceOf[InvalidParamsError])
      case other =>
        fail(s"expected error response, got $other")
    }
  }

  test("tools does not reflect an unknown tool name in the error") {
    var ran = false
    val handlerRegistry = HandlerRegistryInMemory(
      Map(
        ToolMethods.call -> Handler.tools(
          "ping" -> ((_: ToolCall) => {
            ran = true
            Right(CallToolResult(content = List(TextContent(text = "pong"))))
          })
        )
      )
    )
    val mcpServer = McpServer(handlerRegistry)
    val secretName = "secret-tool\nname"
    val response = mcpServer.handle(callRequest(secretName))

    assertEquals(ran, false)
    response match {
      case error: McpErrorResponse =>
        assert(error.error.isInstanceOf[InvalidParamsError])
        assert(!error.error.message.contains("secret-tool"))
        assertEquals(error.error.data, None)
      case other =>
        fail(s"expected error response, got $other")
    }
  }

  test("tools does not reflect malformed params contents in the error") {
    var ran = false
    val handlerRegistry = HandlerRegistryInMemory(
      Map(
        ToolMethods.call -> Handler.tools(
          "ping" -> ((_: ToolCall) => {
            ran = true
            Right(CallToolResult(content = List(TextContent(text = "pong"))))
          })
        )
      )
    )
    val mcpServer = McpServer(handlerRegistry)
    val request = McpRequest(
      method = ToolMethods.call,
      id = requestId,
      params = Some(
        RequestParams(
          meta = requestMeta,
          fields = JsonObject(
            Map(
              CallToolRequestParams.NameKey -> JsonString("ping"),
              InputResponses.InputResponsesKey -> JsonObject(
                Map("private-input-key\nsecret" -> JsonString("not-an-object"))
              )
            )
          )
        )
      )
    )
    val response = mcpServer.handle(request)

    assertEquals(ran, false)
    response match {
      case error: McpErrorResponse =>
        assert(error.error.isInstanceOf[InvalidParamsError])
        assert(!error.error.message.contains("private-input-key"))
        assert(!error.error.message.contains("not-an-object"))
        assertEquals(error.error.data, None)
      case other =>
        fail(s"expected error response, got $other")
    }
  }

  test("ReadResource handler encodes through Results.readResource") {
    val expected = ReadResourceResult(
      contents = List(TextResourceContents(uri = "file:///tmp/a.txt", text = "hello")),
      ttlMs = 0L,
      cacheScope = PublicCacheScope
    )
    val handlerRegistry = HandlerRegistryInMemory(
      Map(
        ResourceMethods.read ->
          Handler.of(_ => Right(expected))(Results.readResourceResultEncoder)
      )
    )
    val mcpServer = McpServer(handlerRegistry)
    val request = McpRequest(method = ResourceMethods.read, id = requestId)
    val response = mcpServer.handle(request)

    response match {
      case success: McpSuccessResponse =>
        assertEquals(Resources.toReadResourceResult(success.result.fields), Right(expected))
      case other =>
        fail(s"expected success response, got $other")
    }
  }

  test("GetPrompt handler encodes through Results.getPrompt") {
    val expected = GetPromptResult(
      messages = List(
        PromptMessage(role = AssistantRole, content = TextContent(text = "Commit: fix login"))
      )
    )
    val handlerRegistry = HandlerRegistryInMemory(
      Map(
        PromptMethods.get -> Handler.of(_ => Right(expected))(Results.getPromptResultEncoder)
      )
    )
    val mcpServer = McpServer(handlerRegistry)
    val request = McpRequest(method = PromptMethods.get, id = requestId)
    val response = mcpServer.handle(request)

    response match {
      case success: McpSuccessResponse =>
        assertEquals(Prompts.toGetPromptResult(success.result.fields), Right(expected))
      case other =>
        fail(s"expected success response, got $other")
    }
  }

  test("Discover handler encodes through Results.discover") {
    val expected = DiscoverResult(
      supportedVersions = List(McpProtocolVersion20260728.value),
      capabilities = ServerCapabilities(tools = Some(ToolsCapability())),
      ttlMs = 60_000L,
      cacheScope = PrivateCacheScope,
      instructions = Some("Use tools sparingly.")
    )
    val handlerRegistry = HandlerRegistryInMemory(
      Map(
        ServerDiscover.method -> Handler.of(_ => Right(expected))(Results.discoverResultEncoder)
      )
    )
    val mcpServer = McpServer(handlerRegistry)
    val request = McpRequest(method = ServerDiscover.method, id = requestId)
    val response = mcpServer.handle(request)

    response match {
      case success: McpSuccessResponse =>
        assertEquals(Discover.toDiscoverResult(success.result.fields), Right(expected))
      case other =>
        fail(s"expected success response, got $other")
    }
  }

  test("ServerTool declaration wires discover, list, and call") {
    val definition = Tool(name = "ping", inputSchema = ToolInputSchema())
    val expected = CallToolResult(content = List(TextContent(text = "pong")))
    val server = McpServer(
      info = Implementation(name = "ex", version = "1"),
      tools = Seq(
        ServerTool(definition, _ => Right(expected))
      )
    )
    val meta = RequestMeta(
      protocolVersion = McpProtocolVersion20260728,
      clientCapabilities = ClientCapabilities()
    )
    val call = McpRequest(
      method = ToolMethods.call,
      id = requestId,
      params = Some(
        RequestParams(
          meta = meta,
          fields = JsonObject(Map(CallToolRequestParams.NameKey -> JsonString("ping")))
        )
      )
    )
    val list = McpRequest(
      method = ToolMethods.list,
      id = requestId,
      params = Some(PaginatedRequestParams(meta = meta))
    )
    val discover = McpRequest(
      method = ServerDiscover.method,
      id = requestId,
      params = Some(RequestParams(meta = meta))
    )

    server.handle(list) match {
      case success: McpSuccessResponse =>
        val listed = Tools.toListToolsResult(success.result.fields)
        assertEquals(listed.map(_.tools.map(_.name)), Right(List("ping")))
      case other =>
        fail(s"expected list success, got $other")
    }
    server.handle(call) match {
      case success: McpSuccessResponse =>
        assertEquals(
          Tools.toCallToolResult(success.result.fields).map(_.content),
          Right(expected.content)
        )
      case other =>
        fail(s"expected call success, got $other")
    }
    server.handle(discover) match {
      case success: McpSuccessResponse =>
        val result = Discover.toDiscoverResult(success.result.fields)
        assert(result.exists(_.capabilities.tools.nonEmpty))
      case other =>
        fail(s"expected discover success, got $other")
    }
  }

  test("duplicate ServerTool names fail fast") {
    val definition = Tool(name = "ping", inputSchema = ToolInputSchema())
    val tool = ServerTool(definition, _ => Right(CallToolResult(content = Nil)))
    intercept[IllegalArgumentException] {
      McpServer(info = Implementation(name = "ex", version = "1"), tools = Seq(tool, tool))
    }
  }

  private val requestMeta = RequestMeta(
    protocolVersion = McpProtocolVersion20260728,
    clientCapabilities = ClientCapabilities()
  )

  private def callRequest(name: String): McpRequest =
    McpRequest(
      method = ToolMethods.call,
      id = requestId,
      params = Some(
        RequestParams(
          meta = requestMeta,
          fields = JsonObject(Map(CallToolRequestParams.NameKey -> JsonString(name)))
        )
      )
    )

  private def assertInvalidParams(response: McpResponse): Unit =
    response match {
      case error: McpErrorResponse =>
        assert(error.error.isInstanceOf[InvalidParamsError])
        assertEquals(error.id, Some(requestId))
      case other =>
        fail(s"expected error response, got $other")
    }

  test("NonFatal handler failure returns InternalError and the server recovers") {
    var calls = 0
    val ping = Method("ping")
    val handlerRegistry = HandlerRegistryInMemory(
      Map(
        ping -> Handler.empty { _ =>
          calls += 1
          if (calls == 1) throw new RuntimeException("sensitive internals")
          else Right(())
        }
      )
    )
    val mcpServer = McpServer(handlerRegistry)
    val request = McpRequest(method = ping, id = requestId)

    mcpServer.handle(request) match {
      case error: McpErrorResponse =>
        assert(error.error.isInstanceOf[InternalError])
        assertEquals(error.error.message, "Internal error")
        assertEquals(error.error.data, None)
        assertEquals(error.id, Some(requestId))
      case other =>
        fail(s"expected error response, got $other")
    }
    assertEquals(
      mcpServer.handle(request),
      McpSuccessResponse(result = Result.empty(), id = request.id)
    )
  }

  test("Registry lookup failure returns InternalError") {
    val failingRegistry = new HandlerRegistry {
      def find(method: Method): Option[Handler] =
        throw new RuntimeException("registry internals")
    }
    val mcpServer = McpServer(failingRegistry)
    mcpServer.handle(McpRequest(method = Method("ping"), id = requestId)) match {
      case error: McpErrorResponse =>
        assert(error.error.isInstanceOf[InternalError])
        assertEquals(error.error.data, None)
      case other =>
        fail(s"expected error response, got $other")
    }
  }

  test("Result encoding failure returns InternalError") {
    val failingEncoder: ResultEncoder[Unit] =
      ResultEncoder(_ => throw new RuntimeException("encoder internals"))
    val handlerRegistry = HandlerRegistryInMemory(
      Map(Method("ping") -> Handler.of(_ => Right(()))(failingEncoder))
    )
    val mcpServer = McpServer(handlerRegistry)
    mcpServer.handle(McpRequest(method = Method("ping"), id = requestId)) match {
      case error: McpErrorResponse =>
        assert(error.error.isInstanceOf[InternalError])
        assertEquals(error.error.data, None)
      case other =>
        fail(s"expected error response, got $other")
    }
  }

  test("InterruptedException and LinkageError propagate") {
    val interrupted = McpServer(
      HandlerRegistryInMemory(
        Map(Method("ping") -> Handler.empty(_ => throw new InterruptedException("stop")))
      )
    )
    val request = McpRequest(method = Method("ping"), id = requestId)
    var sawInterrupt = false
    try interrupted.handle(request)
    catch { case _: InterruptedException => sawInterrupt = true }
    assert(sawInterrupt)

    val linkage = McpServer(
      HandlerRegistryInMemory(
        Map(Method("ping") -> Handler.empty(_ => throw new LinkageError("bad link")))
      )
    )
    var sawLinkage = false
    try linkage.handle(request)
    catch { case _: LinkageError => sawLinkage = true }
    assert(sawLinkage)
  }

  test("Intentional handler error is preserved unchanged") {
    val domainError = ApplicationError(code = 42, message = "domain says no")
    val handlerRegistry = HandlerRegistryInMemory(
      Map(Method("ping") -> Handler.empty(_ => Left(domainError)))
    )
    val mcpServer = McpServer(handlerRegistry)
    val request = McpRequest(method = Method("ping"), id = requestId)
    assertEquals(
      mcpServer.handle(request),
      McpErrorResponse(error = domainError, id = request.id)
    )
  }

  test("Factory discover and tools/list validate request params") {
    val tool = ServerTool(
      Tool(name = "ping", inputSchema = ToolInputSchema()),
      _ => Right(CallToolResult(content = List(TextContent(text = "pong"))))
    )
    val server = McpServer(info = Implementation(name = "ex", version = "1"), tools = Seq(tool))

    assertInvalidParams(server.handle(McpRequest(method = ServerDiscover.method, id = requestId)))
    assertInvalidParams(
      server.handle(
        McpRequest(
          method = ServerDiscover.method,
          id = requestId,
          params = Some(PaginatedRequestParams(meta = requestMeta))
        )
      )
    )
    assertInvalidParams(server.handle(McpRequest(method = ToolMethods.list, id = requestId)))
    assertInvalidParams(
      server.handle(
        McpRequest(
          method = ToolMethods.list,
          id = requestId,
          params = Some(RequestParams(meta = requestMeta))
        )
      )
    )
    assertInvalidParams(
      server.handle(
        McpRequest(
          method = ToolMethods.list,
          id = requestId,
          params = Some(PaginatedRequestParams(meta = requestMeta, cursor = Some(Cursor("abc"))))
        )
      )
    )
  }

  test("Factory without tools advertises discovery only") {
    val server = McpServer(info = Implementation(name = "ex", version = "1"))

    server.handle(
      McpRequest(
        method = ServerDiscover.method,
        id = requestId,
        params = Some(RequestParams(meta = requestMeta))
      )
    ) match {
      case success: McpSuccessResponse =>
        val result = Discover.toDiscoverResult(success.result.fields)
        assert(result.exists(_.capabilities.tools.isEmpty))
      case other =>
        fail(s"expected discover success, got $other")
    }
    server.handle(
      McpRequest(
        method = ToolMethods.list,
        id = requestId,
        params = Some(PaginatedRequestParams(meta = requestMeta))
      )
    ) match {
      case error: McpErrorResponse =>
        assert(error.error.isInstanceOf[MethodNotFoundError])
      case other =>
        fail(s"expected error response, got $other")
    }
    server.handle(callRequest("ping")) match {
      case error: McpErrorResponse =>
        assert(error.error.isInstanceOf[MethodNotFoundError])
      case other =>
        fail(s"expected error response, got $other")
    }
  }

  test("Blank and duplicate tool names fail fast in factory and Handler.tools") {
    val run: ToolCall => Either[Error, CallToolResult] =
      _ => Right(CallToolResult(content = Nil))
    intercept[IllegalArgumentException](Handler.tools("" -> run))
    intercept[IllegalArgumentException](Handler.tools("   " -> run))
    intercept[IllegalArgumentException](Handler.tools("a" -> run, "a" -> run))
    val blankTool = ServerTool(Tool(name = " ", inputSchema = ToolInputSchema()), run)
    intercept[IllegalArgumentException](
      McpServer(info = Implementation(name = "ex", version = "1"), tools = Seq(blankTool))
    )
  }

  test("Tool result metadata extensions keep the default server identity") {
    val extension = MetaObject(Map("trace" -> JsonString("abc")))
    val tool = ServerTool(
      Tool(name = "ping", inputSchema = ToolInputSchema()),
      _ => Right(CallToolResult(content = Nil, meta = Some(ResultMeta(extensions = extension))))
    )
    val info = Implementation(name = "ex", version = "1")
    val server = McpServer(info = info, tools = Seq(tool))

    server.handle(callRequest("ping")) match {
      case success: McpSuccessResponse =>
        val meta = success.result.meta.getOrElse(fail("missing result meta"))
        assertEquals(meta.serverInfo, Some(info))
        assertEquals(meta.extensions, extension)
      case other =>
        fail(s"expected success response, got $other")
    }
  }

  test("Explicit serverInfo in tool result metadata is preserved") {
    val custom = Implementation(name = "custom", version = "9")
    val tool = ServerTool(
      Tool(name = "ping", inputSchema = ToolInputSchema()),
      _ => Right(
        CallToolResult(content = Nil, meta = Some(ResultMeta(serverInfo = Some(custom))))
      )
    )
    val server = McpServer(info = Implementation(name = "ex", version = "1"), tools = Seq(tool))

    server.handle(callRequest("ping")) match {
      case success: McpSuccessResponse =>
        assertEquals(success.result.meta.flatMap(_.serverInfo), Some(custom))
      case other =>
        fail(s"expected success response, got $other")
    }
  }

  test("toolsListChanged flag is conditional on explicit opt-in") {
    val tool = ServerTool(
      Tool(name = "ping", inputSchema = ToolInputSchema()),
      _ => Right(CallToolResult(content = Nil))
    )
    val discover = McpRequest(
      method = ServerDiscover.method,
      id = requestId,
      params = Some(RequestParams(meta = requestMeta))
    )
    def toolsCapability(server: McpServer): Option[ToolsCapability] =
      server.handle(discover) match {
        case success: McpSuccessResponse =>
          Discover.toDiscoverResult(success.result.fields) match {
            case Right(result) => result.capabilities.tools
            case Left(error)   => fail(s"discover decode failed: $error")
          }
        case other =>
          fail(s"expected discover success, got $other")
      }

    val info = Implementation(name = "ex", version = "1")
    // Default: unchanged capability (no listChanged).
    assertEquals(
      toolsCapability(McpServer(info = info, tools = Seq(tool))),
      Some(ToolsCapability())
    )
    // Flag with no tools advertises no tools capability at all.
    assertEquals(
      toolsCapability(McpServer(info = info, toolsListChanged = true)),
      None
    )
    // Flag + tools: explicit opt-in advertises listChanged.
    assertEquals(
      toolsCapability(
        McpServer(info = info, tools = Seq(tool), toolsListChanged = true)
      ),
      Some(ToolsCapability(listChanged = Some(true)))
    )
  }
}
