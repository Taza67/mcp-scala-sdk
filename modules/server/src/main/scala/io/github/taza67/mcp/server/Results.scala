package io.github.taza67.mcp.server

import io.github.taza67.mcp.codec.mcp.discover.{Discover => DiscoverCodec}
import io.github.taza67.mcp.codec.mcp.prompts.{Prompts => PromptsCodec}
import io.github.taza67.mcp.codec.mcp.resources.{Resources => ResourcesCodec}
import io.github.taza67.mcp.codec.mcp.roots.{Roots => RootsCodec}
import io.github.taza67.mcp.codec.mcp.tools.{Tools => ToolsCodec}
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.mcp.CompleteResultType
import io.github.taza67.mcp.protocol.mcp.Result
import io.github.taza67.mcp.protocol.mcp.ResultMeta
import io.github.taza67.mcp.protocol.mcp.ResultType
import io.github.taza67.mcp.protocol.mcp.discover.DiscoverResult
import io.github.taza67.mcp.protocol.mcp.prompts.GetPromptResult
import io.github.taza67.mcp.protocol.mcp.prompts.ListPromptsResult
import io.github.taza67.mcp.protocol.mcp.resources.ListResourcesResult
import io.github.taza67.mcp.protocol.mcp.resources.ReadResourceResult
import io.github.taza67.mcp.protocol.mcp.roots.ListRootsResult
import io.github.taza67.mcp.protocol.mcp.tools.CallToolResult
import io.github.taza67.mcp.protocol.mcp.tools.ListToolsResult



/** Builds protocol [[Result]] values from domain result ADTs. */
object Results {

  def complete(
      fields: JsonObject,
      resultType: ResultType = CompleteResultType,
      meta: Option[ResultMeta] = None
  ): Result =
    Result(resultType = resultType, fields = fields, meta = meta)

  def empty(meta: Option[ResultMeta] = None): Result =
    Result.empty(meta)

  def listTools(listToolsResult: ListToolsResult): Result =
    complete(
      fields = ToolsCodec.fromListToolsResult(listToolsResult),
      resultType = listToolsResult.resultType,
      meta = listToolsResult.meta
    )

  def callTool(callToolResult: CallToolResult): Result =
    complete(
      fields = ToolsCodec.fromCallToolResult(callToolResult),
      resultType = callToolResult.resultType,
      meta = callToolResult.meta
    )

  def listResources(listResourcesResult: ListResourcesResult): Result =
    complete(
      fields = ResourcesCodec.fromListResourcesResult(listResourcesResult),
      resultType = listResourcesResult.resultType,
      meta = listResourcesResult.meta
    )

  def readResource(readResourceResult: ReadResourceResult): Result =
    complete(
      fields = ResourcesCodec.fromReadResourceResult(readResourceResult),
      resultType = readResourceResult.resultType,
      meta = readResourceResult.meta
    )

  def listPrompts(listPromptsResult: ListPromptsResult): Result =
    complete(
      fields = PromptsCodec.fromListPromptsResult(listPromptsResult),
      resultType = listPromptsResult.resultType,
      meta = listPromptsResult.meta
    )

  def getPrompt(getPromptResult: GetPromptResult): Result =
    complete(
      fields = PromptsCodec.fromGetPromptResult(getPromptResult),
      resultType = getPromptResult.resultType,
      meta = getPromptResult.meta
    )

  def discover(discoverResult: DiscoverResult): Result =
    complete(
      fields = DiscoverCodec.fromDiscoverResult(discoverResult),
      resultType = discoverResult.resultType,
      meta = discoverResult.meta
    )

  def listRoots(listRootsResult: ListRootsResult): Result =
    complete(fields = RootsCodec.fromListRootsResult(listRootsResult))

  implicit val listToolsResultEncoder: ResultEncoder[ListToolsResult] =
    ResultEncoder(listTools)

  implicit val callToolResultEncoder: ResultEncoder[CallToolResult] =
    ResultEncoder(callTool)

  implicit val listResourcesResultEncoder: ResultEncoder[ListResourcesResult] =
    ResultEncoder(listResources)

  implicit val readResourceResultEncoder: ResultEncoder[ReadResourceResult] =
    ResultEncoder(readResource)

  implicit val listPromptsResultEncoder: ResultEncoder[ListPromptsResult] =
    ResultEncoder(listPrompts)

  implicit val getPromptResultEncoder: ResultEncoder[GetPromptResult] =
    ResultEncoder(getPrompt)

  implicit val discoverResultEncoder: ResultEncoder[DiscoverResult] =
    ResultEncoder(discover)

  implicit val listRootsResultEncoder: ResultEncoder[ListRootsResult] =
    ResultEncoder(listRoots)
}
