package io.github.taza67.mcp.protocol.mcp

/** MCP protocol revision negotiated per request via `_meta`. */
sealed trait McpProtocolVersion {
  def value: String
}

object McpProtocolVersion {

  /** Classify a wire protocol-version string. */
  def fromValue(value: String): Option[McpProtocolVersion] =
    value match {
      case McpProtocolVersion20260728.value => Some(McpProtocolVersion20260728)
      case _                                => None
    }
}

/** Protocol revision `2026-07-28` (stateless / per-request `_meta`). */
case object McpProtocolVersion20260728 extends McpProtocolVersion {
  val value: String = "2026-07-28"
}
