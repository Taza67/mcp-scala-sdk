package io.github.taza67.mcp.server

import io.github.taza67.mcp.protocol.jsonrpc.Method



case class HandlerRegistryInMemory(registry: Map[Method, Handler]) extends HandlerRegistry {

  override def find(method: Method): Option[Handler] = registry.get(method)

  def register(method: Method, handler: Handler): HandlerRegistryInMemory =
    HandlerRegistryInMemory(registry.updated(method, handler))

  def remove(method: Method): HandlerRegistryInMemory =
    HandlerRegistryInMemory(registry.removed(method))
}
