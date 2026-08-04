package io.github.taza67.mcp.server

import io.github.taza67.mcp.protocol.jsonrpc.{Method, MethodNotFoundError, StringRequestId}
import io.github.taza67.mcp.protocol.mcp.{McpErrorResponse, McpRequest}
import munit.FunSuite



class McpServerSuite extends FunSuite {
  test("Unknown method returns an error") {
    val handlerRegistry = HandlerRegistryInMemory(Map())
    val mcpServer = McpServer(handlerRegistry)
    val request = McpRequest(method = Method("unknown"), id = StringRequestId("Test"))
    val response = mcpServer.handle(request)

    assertEquals(response, McpErrorResponse(error = MethodNotFoundError(), id = request.id))
  }
}
