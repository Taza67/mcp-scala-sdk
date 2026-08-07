package io.github.taza67.mcp.protocol.mcp

/** Optional annotations the client can use to decide how objects are used or displayed.
 *
 *  @param audience Intended audience(s), e.g. `user` and/or `assistant`.
 *  @param priority Importance for operating the server: `1` = effectively required,
 *                  `0` = entirely optional.
 *  @param lastModified ISO 8601 timestamp of last modification
 *                      (e.g. `"2025-01-12T15:00:58Z"`).
 */
case class Annotations(
    audience: Option[List[Role]] = None,
    priority: Option[Double] = None,
    lastModified: Option[String] = None
)

/** Opaque token representing a pagination position. */
case class Cursor(value: String)

/** Intended cache scope for cacheable results (HTTP Cache-Control analogy).
 *
 *  Used by `server/discover`, list methods, `resources/read`, and similar results.
 *
 *  - [[PublicCacheScope]]: no user-specific data; may be shared across auth contexts.
 *  - [[PrivateCacheScope]]: may be reused only within the same authorization context.
 */
sealed trait CacheScope {
  def value: String
}

object CacheScope {

  /** Classify a wire cache-scope string. */
  def fromValue(value: String): Option[CacheScope] =
    value match {
      case PublicCacheScope.value  => Some(PublicCacheScope)
      case PrivateCacheScope.value => Some(PrivateCacheScope)
      case _                       => None
    }
}

/** Response does not contain user-specific data; intermediaries MAY share the cache. */
case object PublicCacheScope extends CacheScope {
  val value: String = "public"
}

/** Response MAY be cached only within the same authorization context. */
case object PrivateCacheScope extends CacheScope {
  val value: String = "private"
}
