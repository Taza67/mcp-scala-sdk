package io.github.taza67.mcp.protocol.mcp

import io.github.taza67.mcp.protocol.json.{JsonObject, JsonValue}



/** MCP protocol revision negotiated per request via `_meta`. */
sealed trait McpProtocolVersion {
  def value: String
}

/** Protocol revision `2026-07-28` (stateless / per-request `_meta`). */
case object McpProtocolVersion20260728 extends McpProtocolVersion {
  val value: String = "2026-07-28"
}

/** Sender or recipient of messages and data in a conversation. */
sealed trait Role {
  def value: String
}

case object UserRole extends Role {
  val value: String = "user"
}

case object AssistantRole extends Role {
  val value: String = "assistant"
}

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

/** Visual theme an [[Icon]] is designed for. */
sealed trait IconTheme {
  def value: String
}

case object LightIconTheme extends IconTheme {
  val value: String = "light"
}

case object DarkIconTheme extends IconTheme {
  val value: String = "dark"
}

/** Optionally-sized icon for a user interface.
 *
 *  Consumers SHOULD prefer icons from the same or a trusted domain, and take
 *  care with SVGs (they may contain executable JavaScript).
 *
 *  @param src HTTP(S) URL or `data:` URI with Base64 image data.
 *  @param mimeType MIME override when the source type is missing or generic
 *                  (e.g. `"image/png"`, `"image/svg+xml"`).
 *  @param sizes Sizes in `WxH` form (e.g. `"48x48"`) or `"any"` for scalable formats.
 *  @param theme Designed for a light or dark background; absent means any theme.
 */
case class Icon(
    src: String,
    mimeType: Option[String] = None,
    sizes: Option[List[String]] = None,
    theme: Option[IconTheme] = None
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

/** Response does not contain user-specific data; intermediaries MAY share the cache. */
case object PublicCacheScope extends CacheScope {
  val value: String = "public"
}

/** Response MAY be cached only within the same authorization context. */
case object PrivateCacheScope extends CacheScope {
  val value: String = "private"
}

/** Contents of a `_meta` field: open metadata attached to MCP interactions.
 *
 *  Certain key names are reserved for protocol-level metadata; implementations
 *  MUST NOT assume meanings for reserved keys beyond what the protocol defines.
 *
 *  Valid keys have an optional reverse-DNS '''prefix''' ending with `/`, then a
 *  '''name'''. Prefixes whose second label is `modelcontextprotocol` or `mcp`
 *  are reserved for MCP use.
 *
 *  @param value Arbitrary metadata entries keyed by string.
 */
case class MetaObject(value: Map[String, JsonValue] = Map.empty) {
  def toJsonObject: JsonObject = JsonObject(value)
}

object MetaObject {
  val empty: MetaObject = MetaObject()
}
