package io.github.taza67.mcp.codec.mcp.lists

import io.github.taza67.mcp.codec.DecodingError
import io.github.taza67.mcp.codec.mcp.Messages
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonValue
import io.github.taza67.mcp.protocol.jsonrpc.JsonRpcVersion
import io.github.taza67.mcp.protocol.jsonrpc.Method
import io.github.taza67.mcp.protocol.jsonrpc.RequestId
import io.github.taza67.mcp.protocol.mcp.McpRequest
import io.github.taza67.mcp.protocol.mcp.PaginatedRequestParams
import io.github.taza67.mcp.protocol.mcp.prompts.ListPromptsRequest
import io.github.taza67.mcp.protocol.mcp.prompts.Prompts
import io.github.taza67.mcp.protocol.mcp.resources.ListResourcesRequest
import io.github.taza67.mcp.protocol.mcp.resources.ListResourceTemplatesRequest
import io.github.taza67.mcp.protocol.mcp.resources.Resources
import io.github.taza67.mcp.protocol.mcp.tools.ListToolsRequest
import io.github.taza67.mcp.protocol.mcp.tools.Tools



/** Protocol AST bridge for paginated MCP list requests (`JsonObject` ↔ ADT).
 *
 *  Covers the list methods fixed in ADR-0007 (`tools/list`, `prompts/list`,
 *  `resources/list`, `resources/templates/list`).
 */
object PaginatedLists {

  private def fromPaginatedList(
      method: Method,
      id: RequestId,
      params: PaginatedRequestParams,
      jsonrpc: JsonRpcVersion
  ): JsonObject = {
    require(
      PaginatedListMethods.All.contains(method),
      s"${method.value} is not a paginated list method"
    )
    Messages.fromRequest(
      McpRequest(method = method, id = id, params = Some(params), jsonrpc = jsonrpc)
    )
  }

  def fromListToolsRequest(listToolsRequest: ListToolsRequest): JsonObject =
    fromPaginatedList(
      Tools.list,
      listToolsRequest.id,
      listToolsRequest.params,
      listToolsRequest.jsonrpc
    )

  def fromListPromptsRequest(listPromptsRequest: ListPromptsRequest): JsonObject =
    fromPaginatedList(
      Prompts.list,
      listPromptsRequest.id,
      listPromptsRequest.params,
      listPromptsRequest.jsonrpc
    )

  def fromListResourcesRequest(listResourcesRequest: ListResourcesRequest): JsonObject =
    fromPaginatedList(
      Resources.list,
      listResourcesRequest.id,
      listResourcesRequest.params,
      listResourcesRequest.jsonrpc
    )

  def fromListResourceTemplatesRequest(
      listResourceTemplatesRequest: ListResourceTemplatesRequest
  ): JsonObject =
    fromPaginatedList(
      Resources.templatesList,
      listResourceTemplatesRequest.id,
      listResourceTemplatesRequest.params,
      listResourceTemplatesRequest.jsonrpc
    )

  private def toPaginatedList[A](
      expected: Method,
      message: JsonValue
  )(
      build: (RequestId, PaginatedRequestParams, JsonRpcVersion) => A
  ): Either[DecodingError, A] = {
    require(
      PaginatedListMethods.All.contains(expected),
      s"${expected.value} is not a paginated list method"
    )
    Messages.toMessage(message).flatMap {
      case McpRequest(`expected`, id, Some(params: PaginatedRequestParams), jsonrpc) =>
        Right(build(id, params, jsonrpc))
      case McpRequest(`expected`, _, None, _) =>
        Left(DecodingError(s"${expected.value} requires params"))
      case McpRequest(`expected`, _, Some(_), _) =>
        Left(DecodingError(s"${expected.value} requires paginated params"))
      case _ =>
        Left(DecodingError(s"Expected ${expected.value} request"))
    }
  }

  def toListToolsRequest(message: JsonValue): Either[DecodingError, ListToolsRequest] =
    toPaginatedList(Tools.list, message)(ListToolsRequest.apply)

  def toListPromptsRequest(message: JsonValue): Either[DecodingError, ListPromptsRequest] =
    toPaginatedList(Prompts.list, message)(ListPromptsRequest.apply)

  def toListResourcesRequest(message: JsonValue): Either[DecodingError, ListResourcesRequest] =
    toPaginatedList(Resources.list, message)(ListResourcesRequest.apply)

  def toListResourceTemplatesRequest(
      message: JsonValue
  ): Either[DecodingError, ListResourceTemplatesRequest] =
    toPaginatedList(Resources.templatesList, message)(ListResourceTemplatesRequest.apply)
}
