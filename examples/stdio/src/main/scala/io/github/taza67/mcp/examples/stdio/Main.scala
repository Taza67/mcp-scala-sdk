package io.github.taza67.mcp.examples.stdio

import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.jsonrpc.Error
import io.github.taza67.mcp.protocol.mcp.Implementation
import io.github.taza67.mcp.protocol.mcp.TextContent
import io.github.taza67.mcp.protocol.mcp.tools.CallToolResult
import io.github.taza67.mcp.protocol.mcp.tools.Tool
import io.github.taza67.mcp.protocol.mcp.tools.ToolAnnotations
import io.github.taza67.mcp.protocol.mcp.tools.ToolInputSchema
import io.github.taza67.mcp.server.Instructions
import io.github.taza67.mcp.server.McpServer
import io.github.taza67.mcp.server.ServerTool
import io.github.taza67.mcp.server.ToolCall
import io.github.taza67.mcp.transport.stdio.StdioTransport



/** Example MCP stdio process: `server/discover`, `tools/list`, and a `ping` tool.
 *
 *  Run with stdin/stdout attached: `sbt exampleStdio/run`
 */
object Main {

  def main(args: Array[String]): Unit = {
    val server = McpServer(
      info = Implementation(name = "mcp-scala-sdk-example-stdio", version = "0.1.0"),
      instructions = Instructions("Call the ping tool."),
      tools = Seq(ping)
    )
    StdioTransport(server).runProcess()
  }

  private val ping: ServerTool =
    ServerTool(
      definition = Tool(
        name = "ping",
        title = Some("Ping"),
        inputSchema = ToolInputSchema(
          fields = JsonObject(Map("properties" -> JsonObject(Map.empty)))
        ),
        description = Some("Returns pong. Takes no arguments."),
        annotations = Some(ToolAnnotations(readOnlyHint = Some(true)))
      ),
      run = pingRun
    )

  private def pingRun(call: ToolCall): Either[Error, CallToolResult] =
    Right(CallToolResult(content = List(TextContent(text = "pong"))))
}
