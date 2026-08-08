package io.github.taza67.mcp.codec.mcp.lists

import io.github.taza67.mcp.protocol.jsonrpc.Method
import io.github.taza67.mcp.protocol.mcp.prompts.Prompts
import io.github.taza67.mcp.protocol.mcp.resources.Resources
import io.github.taza67.mcp.protocol.mcp.tools.Tools



/** JSON-RPC methods whose request `params` use paginated request params (ADR-0007). */
private[mcp] object PaginatedListMethods {

  val All: Set[Method] =
    Set(Tools.list, Prompts.list, Resources.list, Resources.templatesList)
}
