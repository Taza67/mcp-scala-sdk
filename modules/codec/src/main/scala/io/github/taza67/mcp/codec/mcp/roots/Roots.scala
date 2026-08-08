package io.github.taza67.mcp.codec.mcp.roots

import io.github.taza67.mcp.codec.DecodingError
import io.github.taza67.mcp.codec.Fields
import io.github.taza67.mcp.codec.Primitives
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.mcp.MetaObject
import io.github.taza67.mcp.protocol.mcp.roots.ListRootsResult
import io.github.taza67.mcp.protocol.mcp.roots.Root



/** Protocol AST bridge for MCP roots-domain results (`JsonObject` ↔ ADT).
 *
 *  Currently [[Root]] / [[ListRootsResult]].
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
