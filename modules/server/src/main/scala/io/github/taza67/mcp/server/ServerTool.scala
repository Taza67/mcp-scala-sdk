package io.github.taza67.mcp.server

import io.github.taza67.mcp.protocol.jsonrpc.Error
import io.github.taza67.mcp.protocol.mcp.tools.CallToolResult
import io.github.taza67.mcp.protocol.mcp.tools.Tool



/** One advertised tool: wire [[Tool]] plus the function to run on `tools/call`. */
case class ServerTool(
    definition: Tool,
    run: ToolCall => Either[Error, CallToolResult]
)
