package io.github.taza67.mcp.codec.mcp.discover

import io.github.taza67.mcp.codec.DecodingError
import io.github.taza67.mcp.codec.Fields
import io.github.taza67.mcp.codec.Primitives
import io.github.taza67.mcp.codec.mcp.Capabilities
import io.github.taza67.mcp.codec.mcp.PlainRequests
import io.github.taza67.mcp.codec.mcp.lists.PaginatedListResults
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.json.JsonValue
import io.github.taza67.mcp.protocol.mcp.RequestParams
import io.github.taza67.mcp.protocol.mcp.discover.DiscoverRequest
import io.github.taza67.mcp.protocol.mcp.discover.DiscoverResult
import io.github.taza67.mcp.protocol.mcp.discover.ServerDiscover



/** Protocol AST bridge for MCP discover-domain types (`JsonObject` ↔ ADT). */
object Discover {

  def fromDiscoverRequest(discoverRequest: DiscoverRequest): JsonObject =
    PlainRequests.fromRequest(
      ServerDiscover.method,
      discoverRequest.id,
      discoverRequest.params,
      discoverRequest.jsonrpc
    )

  def toDiscoverRequestParams(
      params: RequestParams
  ): Either[DecodingError, RequestParams] =
    Right(params)

  def fromDiscoverResult(discoverResult: DiscoverResult): JsonObject = {
    val tail = PaginatedListResults.fromCacheableTail(
      PaginatedListResults.CacheableTail(
        discoverResult.ttlMs,
        discoverResult.cacheScope
      )
    )
    JsonObject(
      Map(
        DiscoverResult.SupportedVersionsKey ->
          Primitives.fromList(discoverResult.supportedVersions)(JsonString(_)),
        DiscoverResult.CapabilitiesKey ->
          Capabilities.fromServerCapabilities(discoverResult.capabilities)
      ) ++ tail ++ Fields.withOptional(
        Map.empty,
        DiscoverResult.InstructionsKey -> discoverResult.instructions.map(JsonString(_))
      )
    )
  }

  def toDiscoverRequest(message: JsonValue): Either[DecodingError, DiscoverRequest] =
    PlainRequests.toRequest(ServerDiscover.method, message)(toDiscoverRequestParams) {
      (id, params, jsonrpc) => DiscoverRequest(id = id, params = params, jsonrpc = jsonrpc)
    }

  def toDiscoverResult(discoverResult: JsonObject): Either[DecodingError, DiscoverResult] = {
    val fields = discoverResult.value
    for {
      supportedVersions <- Fields
        .required(fields, DiscoverResult.SupportedVersionsKey)
        .flatMap(
          Primitives.toList(_, DiscoverResult.SupportedVersionsKey)(
            Primitives.asString(_, DiscoverResult.SupportedVersionsKey)
          )
        )
      capabilities <- Fields
        .requiredObject(fields, DiscoverResult.CapabilitiesKey)
        .flatMap(Capabilities.toServerCapabilities)
      tail <- PaginatedListResults.toCacheableTail(fields)
      instructions <- Fields.optionalString(fields, DiscoverResult.InstructionsKey)
    } yield DiscoverResult(
      supportedVersions = supportedVersions,
      capabilities = capabilities,
      ttlMs = tail.ttlMs,
      cacheScope = tail.cacheScope,
      instructions = instructions
    )
  }
}
