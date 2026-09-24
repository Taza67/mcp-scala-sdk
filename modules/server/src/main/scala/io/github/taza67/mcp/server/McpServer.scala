package io.github.taza67.mcp.server

import scala.util.control.NonFatal

import io.github.taza67.mcp.protocol.jsonrpc.Error
import io.github.taza67.mcp.protocol.jsonrpc.InternalError
import io.github.taza67.mcp.protocol.jsonrpc.InvalidParamsError
import io.github.taza67.mcp.protocol.jsonrpc.Method
import io.github.taza67.mcp.protocol.jsonrpc.MethodNotFoundError
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.mcp.Implementation
import io.github.taza67.mcp.protocol.mcp.McpErrorResponse
import io.github.taza67.mcp.protocol.mcp.McpProtocolVersion20260728
import io.github.taza67.mcp.protocol.mcp.McpRequest
import io.github.taza67.mcp.protocol.mcp.McpResponse
import io.github.taza67.mcp.protocol.mcp.McpSuccessResponse
import io.github.taza67.mcp.protocol.mcp.PaginatedRequestParams
import io.github.taza67.mcp.protocol.mcp.PublicCacheScope
import io.github.taza67.mcp.protocol.mcp.RequestParams
import io.github.taza67.mcp.protocol.mcp.ResultMeta
import io.github.taza67.mcp.protocol.mcp.ServerCapabilities
import io.github.taza67.mcp.protocol.mcp.ToolsCapability
import io.github.taza67.mcp.protocol.mcp.completion.{Completion => CompletionMethods}
import io.github.taza67.mcp.protocol.mcp.completion.CompleteRequestParams
import io.github.taza67.mcp.protocol.mcp.completion.CompleteResult
import io.github.taza67.mcp.protocol.mcp.discover.DiscoverResult
import io.github.taza67.mcp.protocol.mcp.discover.ServerDiscover
import io.github.taza67.mcp.protocol.mcp.tools.{Tools => ToolMethods}
import io.github.taza67.mcp.protocol.mcp.tools.CallToolResult
import io.github.taza67.mcp.protocol.mcp.tools.ListToolsResult



/** Synchronous MCP request dispatcher over a [[HandlerRegistry]]. */
case class McpServer(handlerRegistry: HandlerRegistry) extends Server {

  override def handle(request: McpRequest): McpResponse =
    try {
      val handler = handlerRegistry.find(request.method)
      handler match {
        case Some(h) =>
          h.execute(request.params) match {
            case Left(e)  => McpErrorResponse(error = e, id = request.id)
            case Right(r) => McpSuccessResponse(result = r, id = request.id)
          }
        case None => McpErrorResponse(error = MethodNotFoundError(), id = request.id)
      }
    } catch {
      // One failing request must not take down the dispatch path; fatal
      // conditions (InterruptedException, LinkageError, VM errors) still propagate.
      case NonFatal(_) =>
        McpErrorResponse(error = InternalError(), id = request.id)
    }
}

object McpServer {

  /** Builds a server from identity, tool declarations, and an optional
   *  completion callback.
   *
   *  Registers `server/discover` always. Registers `tools/list` and `tools/call`
   *  when [[tools]] is non-empty. Registers `completion/complete` when
   *  [[completion]] is defined and advertises the completions capability in
   *  discovery. Duplicate tool names fail fast.
   *
   *  [[toolsListChanged]] advertises `tools.listChanged` in discovery; enable
   *  it only when notifications are actually published (e.g. a
   *  [[SubscriptionServer]] fronting this server whose hub grants
   *  `toolsListChanged`). Other capability flags remain caller-built for
   *  custom handlers.
   */
  def apply(
      info: Implementation,
      tools: Seq[ServerTool] = Seq.empty,
      instructions: Instructions = Instructions.none,
      completion: Option[CompleteRequestParams => Either[Error, CompleteResult]] = None,
      toolsListChanged: Boolean = false
  ): McpServer = {
    val meta = Some(ResultMeta(serverInfo = Some(info)))
    val discoverHandler =
      Handler.of {
        case Some(_: RequestParams) =>
          Right(
            discoverResult(
              tools,
              instructions,
              meta,
              completion.isDefined,
              toolsListChanged
            )
          )
        case _ =>
          Left(InvalidParamsError())
      }(Results.discoverResultEncoder)
    val toolMethods: Map[Method, Handler] =
      if (tools.isEmpty) Map.empty
      else
        Map(
          ToolMethods.list -> Handler.of {
            case Some(params: PaginatedRequestParams) if params.cursor.isEmpty =>
              Right(listToolsResult(tools, meta))
            case _ =>
              Left(InvalidParamsError())
          }(Results.listToolsResultEncoder),
          ToolMethods.call -> Handler.tools(
            tools.map { tool =>
              val runTool: ToolCall => Either[Error, CallToolResult] = run(tool, meta)
              tool.definition.name -> runTool
            }: _*
          )
        )
    val completionMethods: Map[Method, Handler] =
      completion match {
        case Some(run) =>
          Map(
            CompletionMethods.complete ->
              Handler.complete(runCompletion(run, meta))
          )
        case None => Map.empty
      }
    val methods =
      Map(ServerDiscover.method -> discoverHandler) ++ toolMethods ++ completionMethods
    McpServer(HandlerRegistryInMemory(methods))
  }

  private def discoverResult(
      tools: Seq[ServerTool],
      instructions: Instructions,
      meta: Option[ResultMeta],
      hasCompletion: Boolean,
      toolsListChanged: Boolean
  ): DiscoverResult =
    DiscoverResult(
      supportedVersions = List(McpProtocolVersion20260728.value),
      capabilities = ServerCapabilities(
        completions =
          if (hasCompletion) Some(JsonObject(Map.empty)) else None,
        tools =
          if (tools.nonEmpty)
            Some(
              ToolsCapability(
                listChanged = if (toolsListChanged) Some(true) else None
              )
            )
          else None
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

  /** Merges handler-supplied result meta with the default: extensions and an
   *  explicit `serverInfo` are preserved; a missing identity is filled.
   */
  private def mergeMeta(
      supplied: Option[ResultMeta],
      fallback: Option[ResultMeta]
  ): Option[ResultMeta] =
    supplied match {
      case Some(suppliedMeta) =>
        Some(
          suppliedMeta.copy(
            serverInfo = suppliedMeta.serverInfo.orElse(fallback.flatMap(_.serverInfo))
          )
        )
      case None => fallback
    }

  private def run(
      tool: ServerTool,
      meta: Option[ResultMeta]
  )(call: ToolCall): Either[Error, CallToolResult] =
    tool.run(call).map { result =>
      result.copy(meta = mergeMeta(result.meta, meta))
    }

  private def runCompletion(
      run: CompleteRequestParams => Either[Error, CompleteResult],
      meta: Option[ResultMeta]
  )(params: CompleteRequestParams): Either[Error, CompleteResult] =
    run(params).map { result =>
      result.copy(meta = mergeMeta(result.meta, meta))
    }
}
