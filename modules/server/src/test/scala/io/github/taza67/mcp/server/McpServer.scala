package io.github.taza67.mcp.server

import io.github.taza67.mcp.protocol.{Error, ErrorResponse, Method, Request, StringId}
import munit.FunSuite



class McpServerSuite extends FunSuite {
  test("Unknown method returns an error") {
    val handlerRegistry = HandlerRegistryInMemory(Map())
    val mcpServer = McpServer(handlerRegistry)
    val request = Request(method = Method("unknown"), id = StringId("Test"))
    val response = mcpServer.handle(request)

    assertEquals(response, ErrorResponse(error = Error.MethodNotFound, id = request.id))
  }
}
