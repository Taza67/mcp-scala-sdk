package io.github.taza67.mcp.server

import io.github.taza67.mcp.protocol.jsonrpc.Method



trait HandlerRegistry {
  def find(method: Method): Option[Handler]
}
