package io.github.taza67.mcp.codec.mcp.lists

import io.github.taza67.mcp.codec.DecodingError
import io.github.taza67.mcp.codec.Fields
import io.github.taza67.mcp.codec.Primitives
import io.github.taza67.mcp.codec.mcp.Params
import io.github.taza67.mcp.protocol.json.JsonNumber
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.json.JsonValue
import io.github.taza67.mcp.protocol.mcp.CacheableResult
import io.github.taza67.mcp.protocol.mcp.CacheScope
import io.github.taza67.mcp.protocol.mcp.Cursor
import io.github.taza67.mcp.protocol.mcp.PaginatedResult



/** Shared encode/decode for cacheable/paginated result payload tails (`ttlMs`, `cacheScope`, …).
 *
 *  `resultType` and `_meta` belong to the [[io.github.taza67.mcp.protocol.mcp.Result]] envelope
 *  and are encoded by [[io.github.taza67.mcp.codec.mcp.Params.fromResult]].
 */
private[mcp] object PaginatedListResults {

  case class CacheableTail(
      ttlMs: Long,
      cacheScope: CacheScope
  )

  case class PaginatedTail(
      cacheable: CacheableTail,
      nextCursor: Option[Cursor]
  )

  def fromCacheScope(cacheScope: CacheScope): JsonString =
    JsonString(cacheScope.value)

  def toCacheScope(value: JsonValue): Either[DecodingError, CacheScope] =
    Primitives.asString(value, CacheableResult.CacheScopeKey).flatMap { s =>
      CacheScope
        .fromValue(s)
        .toRight(DecodingError(s"Invalid ${CacheableResult.CacheScopeKey}: $s"))
    }

  def fromCacheableTail(tail: CacheableTail): Map[String, JsonValue] =
    Map(
      CacheableResult.TtlMsKey -> JsonNumber(tail.ttlMs),
      CacheableResult.CacheScopeKey -> fromCacheScope(tail.cacheScope)
    )

  def toCacheableTail(fields: Map[String, JsonValue]): Either[DecodingError, CacheableTail] =
    for {
      ttlMs <- Fields
        .required(fields, CacheableResult.TtlMsKey)
        .flatMap(Primitives.asLong(_, CacheableResult.TtlMsKey))
      cacheScope <- Fields.required(fields, CacheableResult.CacheScopeKey).flatMap(toCacheScope)
    } yield CacheableTail(ttlMs, cacheScope)

  def fromPaginatedTail(tail: PaginatedTail): Map[String, JsonValue] =
    fromCacheableTail(tail.cacheable) ++ Fields.withOptional(
      Map.empty,
      PaginatedResult.NextCursorKey -> tail.nextCursor.map(Params.fromCursor)
    )

  def toPaginatedTail(fields: Map[String, JsonValue]): Either[DecodingError, PaginatedTail] =
    for {
      cacheable <- toCacheableTail(fields)
      nextCursor <- Fields.optional(fields, PaginatedResult.NextCursorKey)(Params.toCursor)
    } yield PaginatedTail(cacheable, nextCursor)
}
