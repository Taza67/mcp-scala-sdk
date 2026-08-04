package io.github.taza67.mcp.server

import io.github.taza67.mcp.protocol.mcp.{McpRequest, McpResponse}



trait Server {
  def handle(request: McpRequest): McpResponse
}
