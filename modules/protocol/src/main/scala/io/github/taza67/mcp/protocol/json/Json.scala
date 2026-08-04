package io.github.taza67.mcp.protocol.json

/** Codec-neutral JSON AST for open MCP / JSON-RPC fields (ADR-0003). */
sealed trait JsonValue

case object JsonNull extends JsonValue

case class JsonBool(value: Boolean) extends JsonValue

case class JsonNumber(value: BigDecimal) extends JsonValue

case class JsonString(value: String) extends JsonValue

case class JsonArray(value: List[JsonValue]) extends JsonValue

case class JsonObject(value: Map[String, JsonValue]) extends JsonValue
