package io.github.taza67.mcp.protocol.json

/** Codec-neutral JSON value for open MCP and JSON-RPC fields.
 *
 *  The protocol module does not depend on a wire codec. Libraries such as Circe
 *  or ujson can encode and decode this AST later without leaking into the
 *  public protocol types.
 */
sealed trait JsonValue

/** JSON `null`. */
case object JsonNull extends JsonValue

/** JSON boolean. */
case class JsonBool(value: Boolean) extends JsonValue

/** JSON number (arbitrary precision). */
case class JsonNumber(value: BigDecimal) extends JsonValue

/** JSON string. */
case class JsonString(value: String) extends JsonValue

/** JSON array. */
case class JsonArray(value: List[JsonValue]) extends JsonValue

/** JSON object: string keys to JSON values. */
case class JsonObject(value: Map[String, JsonValue]) extends JsonValue
