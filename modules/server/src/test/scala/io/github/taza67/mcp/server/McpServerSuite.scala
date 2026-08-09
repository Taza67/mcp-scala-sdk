package io.github.taza67.mcp.server

import io.github.taza67.mcp.codec.mcp.Messages
import io.github.taza67.mcp.codec.mcp.discover.Discover
import io.github.taza67.mcp.codec.mcp.tools.Tools
import io.github.taza67.mcp.protocol.mcp.McpProtocolVersion20260728
import io.github.taza67.mcp.protocol.mcp.PrivateCacheScope
import io.github.taza67.mcp.codec.mcp.prompts.Prompts
import io.github.taza67.mcp.codec.mcp.resources.Resources
import io.github.taza67.mcp.protocol.mcp.AssistantRole
import io.github.taza67.mcp.protocol.mcp.PublicCacheScope
import io.github.taza67.mcp.protocol.mcp.TextContent
import io.github.taza67.mcp.protocol.mcp.TextResourceContents
import io.github.taza67.mcp.protocol.mcp.prompts.{Prompts => PromptMethods}
import io.github.taza67.mcp.protocol.mcp.prompts.GetPromptResult
import io.github.taza67.mcp.protocol.mcp.prompts.PromptMessage
import io.github.taza67.mcp.protocol.mcp.resources.{Resources => ResourceMethods}
import io.github.taza67.mcp.protocol.mcp.resources.ReadResourceResult
import io.github.taza67.mcp.protocol.mcp.tools.CallToolResult
import io.github.taza67.mcp.protocol.mcp.ServerCapabilities
import io.github.taza67.mcp.protocol.mcp.ToolsCapability
import io.github.taza67.mcp.protocol.mcp.discover.DiscoverResult
import io.github.taza67.mcp.protocol.mcp.discover.ServerDiscover
import io.github.taza67.mcp.protocol.mcp.tools.ListToolsResult
import io.github.taza67.mcp.protocol.mcp.tools.Tool
import io.github.taza67.mcp.protocol.mcp.tools.ToolInputSchema
import io.github.taza67.mcp.codec.mcp.roots.Roots
import io.github.taza67.mcp.protocol.mcp.tools.{Tools => ToolMethods}
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.jsonrpc.{Method, MethodNotFoundError, StringRequestId}
import io.github.taza67.mcp.protocol.mcp.CompleteResultType
import io.github.taza67.mcp.protocol.mcp.McpErrorResponse
import io.github.taza67.mcp.protocol.mcp.McpRequest
import io.github.taza67.mcp.protocol.mcp.McpSuccessResponse
import io.github.taza67.mcp.protocol.mcp.Result
import io.github.taza67.mcp.protocol.mcp.roots.{ListRootsResult, Root, Roots => RootsMethods}
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
}
