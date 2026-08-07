package io.github.taza67.mcp.server

import io.github.taza67.mcp.protocol.jsonrpc.Method



/** Lookup table from JSON-RPC [[Method]] to [[Handler]]. */
trait HandlerRegistry {
  def find(method: Method): Option[Handler]
}
