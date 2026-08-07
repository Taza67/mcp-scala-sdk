package io.github.taza67.mcp.server

import io.github.taza67.mcp.protocol.jsonrpc.MethodNotFoundError
import io.github.taza67.mcp.protocol.mcp.{
  McpErrorResponse,
  McpRequest,
  McpResponse,
  McpSuccessResponse
}



/** Synchronous MCP request dispatcher over a [[HandlerRegistry]]. */
case class McpServer(handlerRegistry: HandlerRegistry) extends Server {

  override def handle(request: McpRequest): McpResponse = {
    val handler = handlerRegistry.find(request.method)
    handler match {
      case Some(h) =>
        h.execute(request.params) match {
          case Left(e)  => McpErrorResponse(error = e, id = request.id)
          case Right(r) => McpSuccessResponse(result = r, id = request.id)
        }
      case _ => McpErrorResponse(error = MethodNotFoundError(), id = request.id)
    }
  }
}
