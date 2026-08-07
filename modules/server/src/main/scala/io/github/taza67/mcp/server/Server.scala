package io.github.taza67.mcp.server

import io.github.taza67.mcp.protocol.mcp.{McpRequest, McpResponse}



/** MCP server face: accept an [[McpRequest]], return an [[McpResponse]]. */
trait Server {
  def handle(request: McpRequest): McpResponse
}
