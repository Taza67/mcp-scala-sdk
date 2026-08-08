package io.github.taza67.mcp.codec.mcp.roots

import io.github.taza67.mcp.codec.DecodingError
import io.github.taza67.mcp.codec.Fields
import io.github.taza67.mcp.codec.Primitives
import io.github.taza67.mcp.codec.mcp.ObjectRequests
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.json.JsonValue
import io.github.taza67.mcp.protocol.mcp.MetaObject
import io.github.taza67.mcp.protocol.mcp.roots.ListRootsRequest
import io.github.taza67.mcp.protocol.mcp.roots.ListRootsRequestParams
import io.github.taza67.mcp.protocol.mcp.roots.ListRootsResult
import io.github.taza67.mcp.protocol.mcp.roots.Root
import io.github.taza67.mcp.protocol.mcp.roots.{Roots => RootsMethods}



/** Protocol AST bridge for MCP roots-domain types (`JsonObject` ↔ ADT).
 *
 *  @deprecated Deprecated as of protocol version 2026-07-28 (SEP-2577). Remains for at least twelve months.
 */
object Roots {

  def fromRoot(root: Root): JsonObject = {
    val base = Map(Root.UriKey -> JsonString(root.uri))
    JsonObject(
      Fields.withOptional(
        base,
        Root.NameKey -> root.name.map(JsonString(_)),
        Root.MetaKey -> root.meta.map(m => JsonObject(m.value))
      )
    )
  }

  def fromListRootsRequest(listRootsRequest: ListRootsRequest): JsonObject =
    ObjectRequests.fromRequest(
      method = RootsMethods.list,
      id = listRootsRequest.id,
      params = listRootsRequest.params.map(fromListRootsRequestParams),
      jsonrpc = listRootsRequest.jsonrpc
    )

  def fromListRootsRequestParams(
      listRootsRequestParams: ListRootsRequestParams
  ): JsonObject =
    JsonObject(
      Fields.withOptional(
        Map.empty,
        ListRootsRequestParams.MetaKey ->
          listRootsRequestParams.meta.map(m => JsonObject(m.value))
      )
    )

  def fromListRootsResult(listRootsResult: ListRootsResult): JsonObject =
    JsonObject(
      Map(
        ListRootsResult.RootsKey ->
          Primitives.fromList(listRootsResult.roots)(fromRoot)
      )
    )

  def toRoot(root: JsonObject): Either[DecodingError, Root] = {
    val fields = root.value
    for {
      uri <- Fields.requiredString(fields, Root.UriKey)
      name <- Fields.optionalString(fields, Root.NameKey)
      meta <- Fields.optional(fields, Root.MetaKey)(v =>
        Fields.asObject(v, Root.MetaKey).map(obj => MetaObject(obj.value))
      )
    } yield Root(uri = uri, name = name, meta = meta)
  }

  def toListRootsRequest(message: JsonValue): Either[DecodingError, ListRootsRequest] =
    ObjectRequests.toRequestOptional(RootsMethods.list, message)(toListRootsRequestParams) {
      (id, params, jsonrpc) => ListRootsRequest(id = id, params = params, jsonrpc = jsonrpc)
    }

  def toListRootsRequestParams(
      listRootsRequestParams: JsonObject
  ): Either[DecodingError, ListRootsRequestParams] = {
    val fields = listRootsRequestParams.value
    for {
      meta <- Fields.optional(fields, ListRootsRequestParams.MetaKey)(v =>
        Fields.asObject(v, ListRootsRequestParams.MetaKey).map(obj => MetaObject(obj.value))
      )
    } yield ListRootsRequestParams(meta = meta)
  }

  def toListRootsResult(listRootsResult: JsonObject): Either[DecodingError, ListRootsResult] = {
    val fields = listRootsResult.value
    for {
      roots <- Fields
        .required(fields, ListRootsResult.RootsKey)
        .flatMap(
          Primitives.toList(_, ListRootsResult.RootsKey)(v =>
            Fields.asObject(v, ListRootsResult.RootsKey).flatMap(toRoot)
          )
        )
    } yield ListRootsResult(roots = roots)
  }
}
