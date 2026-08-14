package io.github.taza67.mcp.server

import io.github.taza67.mcp.protocol.jsonrpc.Error
import io.github.taza67.mcp.protocol.jsonrpc.MethodNotFoundError
import io.github.taza67.mcp.protocol.mcp.Implementation
import io.github.taza67.mcp.protocol.mcp.McpErrorResponse
import io.github.taza67.mcp.protocol.mcp.McpProtocolVersion20260728
import io.github.taza67.mcp.protocol.mcp.McpRequest
import io.github.taza67.mcp.protocol.mcp.McpResponse
import io.github.taza67.mcp.protocol.mcp.McpSuccessResponse
import io.github.taza67.mcp.protocol.mcp.PublicCacheScope
import io.github.taza67.mcp.protocol.mcp.ResultMeta
import io.github.taza67.mcp.protocol.mcp.ServerCapabilities
import io.github.taza67.mcp.protocol.mcp.ToolsCapability
import io.github.taza67.mcp.protocol.mcp.discover.DiscoverResult
import io.github.taza67.mcp.protocol.mcp.discover.ServerDiscover
import io.github.taza67.mcp.protocol.mcp.tools.{Tools => ToolMethods}
import io.github.taza67.mcp.protocol.mcp.tools.CallToolResult
import io.github.taza67.mcp.protocol.mcp.tools.ListToolsResult



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
      case None => McpErrorResponse(error = MethodNotFoundError(), id = request.id)
    }
  }
}

object McpServer {

  /** Builds a server from identity and tool declarations.
   *
   *  Registers `server/discover` always. Registers `tools/list` and `tools/call`
   *  when [[tools]] is non-empty. Duplicate tool names fail fast.
   */
  def apply(
      info: Implementation,
      tools: Seq[ServerTool] = Seq.empty,
      instructions: Instructions = Instructions.none
  ): McpServer = {
    val names = tools.map(_.definition.name)
    require(names.distinct.size == names.size, "duplicate tool names")
    val meta = Some(ResultMeta(serverInfo = Some(info)))
    val discoverHandler =
      Handler.of(_ => Right(discoverResult(info, tools, instructions, meta)))(
        Results.discoverResultEncoder
      )
    val methods =
      if (tools.isEmpty)
        Map(ServerDiscover.method -> discoverHandler)
      else
        Map(
          ServerDiscover.method -> discoverHandler,
          ToolMethods.list -> Handler.of(_ => Right(listToolsResult(tools, meta)))(
            Results.listToolsResultEncoder
          ),
          ToolMethods.call -> Handler.tools(
            tools.map { tool =>
              val runTool: ToolCall => Either[Error, CallToolResult] = run(tool, meta)
              tool.definition.name -> runTool
            }: _*
          )
        )
    McpServer(HandlerRegistryInMemory(methods))
  }

  private def discoverResult(
      info: Implementation,
      tools: Seq[ServerTool],
      instructions: Instructions,
      meta: Option[ResultMeta]
  ): DiscoverResult =
    DiscoverResult(
      supportedVersions = List(McpProtocolVersion20260728.value),
      capabilities = ServerCapabilities(
        tools = if (tools.nonEmpty) Some(ToolsCapability()) else None
      ),
      ttlMs = 0L,
      cacheScope = PublicCacheScope,
      instructions = instructions.toDiscoverField,
      meta = meta
    )

  private def listToolsResult(
      tools: Seq[ServerTool],
      meta: Option[ResultMeta]
  ): ListToolsResult =
    ListToolsResult(
      tools = tools.map(_.definition).toList,
      ttlMs = 0L,
      cacheScope = PublicCacheScope,
      meta = meta
    )

  private def run(
      tool: ServerTool,
      meta: Option[ResultMeta]
  )(call: ToolCall): Either[Error, CallToolResult] =
    tool.run(call).map(result => result.copy(meta = result.meta.orElse(meta)))
}
