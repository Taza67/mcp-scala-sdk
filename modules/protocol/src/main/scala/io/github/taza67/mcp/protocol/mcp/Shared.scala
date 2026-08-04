package io.github.taza67.mcp.protocol.mcp

import io.github.taza67.mcp.protocol.json.{JsonObject, JsonValue}



sealed trait McpProtocolVersion {
  def value: String
}

case object McpProtocolVersion20260728 extends McpProtocolVersion {
  val value: String = "2026-07-28"
}

/** The sender or recipient of messages and data in a conversation (SPECS Role). */
sealed trait Role {
  def value: String
}

case object UserRole extends Role {
  val value: String = "user"
}

case object AssistantRole extends Role {
  val value: String = "assistant"
}

/** Optional annotations for the client (SPECS Annotations). */
case class Annotations(
    audience: Option[List[Role]] = None,
    /** 1 = most important / required; 0 = least important / optional. */
    priority: Option[Double] = None,
    /** ISO 8601 timestamp when the resource was last modified. */
    lastModified: Option[String] = None
)

sealed trait IconTheme {
  def value: String
}

case object LightIconTheme extends IconTheme {
  val value: String = "light"
}

case object DarkIconTheme extends IconTheme {
  val value: String = "dark"
}

/** An optionally-sized icon that can be displayed in a UI (SPECS Icon). */
case class Icon(
    src: String,
    mimeType: Option[String] = None,
    sizes: Option[List[String]] = None,
    theme: Option[IconTheme] = None
)

/** An opaque token used to represent a cursor for pagination (SPECS Cursor). */
case class Cursor(value: String)

/** Contents of a `_meta` field (SPECS MetaObject). */
case class MetaObject(value: Map[String, JsonValue] = Map.empty) {
  def toJsonObject: JsonObject = JsonObject(value)
}

object MetaObject {
  val empty: MetaObject = MetaObject()
}
