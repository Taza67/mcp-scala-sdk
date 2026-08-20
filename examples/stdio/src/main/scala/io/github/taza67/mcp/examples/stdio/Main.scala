package io.github.taza67.mcp.examples.stdio

import java.io.IOException

import io.github.taza67.mcp.protocol.json.JsonBool
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.jsonrpc.Error
import io.github.taza67.mcp.protocol.jsonrpc.InvalidParamsError
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
 *  Stage with `sbt exampleStdio/stage`, then run
 *  `target/stdio-example/bin/mcp-stdio-example`.
 */
object Main {

  def main(args: Array[String]): Unit =
    try {
      val server = McpServer(
        info = Implementation(
          name = "mcp-scala-sdk-example-stdio",
          version = ExampleBuildInfo.version
        ),
        instructions = Instructions("Call the ping tool."),
        tools = Seq(ping)
      )
      StdioTransport(server).runProcess()
    } catch {
      case _: IOException =>
        System.err.println("stdio transport I/O failure")
        System.exit(1)
    }

  private val ping: ServerTool =
    ServerTool(
      definition = Tool(
        name = "ping",
        title = Some("Ping"),
        inputSchema = ToolInputSchema(
          fields = JsonObject(
            Map(
              "properties" -> JsonObject(Map.empty),
              "additionalProperties" -> JsonBool(false)
            )
          )
        ),
        description = Some("Returns pong. Takes no arguments."),
        annotations = Some(ToolAnnotations(readOnlyHint = Some(true)))
      ),
      run = pingRun
    )

  private def pingRun(call: ToolCall): Either[Error, CallToolResult] =
    call.arguments match {
      case Some(arguments) if arguments.value.nonEmpty =>
        Left(InvalidParamsError())
      case _ => Right(CallToolResult(content = List(TextContent(text = "pong"))))
    }
}
