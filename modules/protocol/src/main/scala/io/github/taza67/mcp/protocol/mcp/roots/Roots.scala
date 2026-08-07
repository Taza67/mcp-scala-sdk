package io.github.taza67.mcp.protocol.mcp.roots

import io.github.taza67.mcp.protocol.mcp._
import io.github.taza67.mcp.protocol.jsonrpc.{JsonRpcVersion, JsonRpcVersion20, Method, RequestId}



/** Method name for listing roots: `"roots/list"`.
 *
 *  @deprecated Deprecated as of protocol version 2026-07-28 (SEP-2577). Remains for at least twelve months.
 */
object Roots {
  val list: Method = Method("roots/list")
}

/** A root directory or file the server can operate on.
 *
 *  `uri` MUST start with `file://` for now; other schemes may be allowed later.
 *
 *  @param uri URI identifying the root.
 *  @param name Optional human-readable label for display / reference.
 *  @param meta Optional open metadata.
 *
 *  @deprecated Deprecated as of protocol version 2026-07-28 (SEP-2577). Remains for at least twelve months.
 */
case class Root(
    uri: String,
    name: Option[String] = None,
    meta: Option[MetaObject] = None
)

/** Optional parameters for a `roots/list` request.
 *
 *  @param meta Optional open metadata on the request.
 */
case class ListRootsRequestParams(
    meta: Option[MetaObject] = None
)

/** Server→client request for root URIs the client allows the server to use.
 *
 *  Typical use: repositories or directories the server should operate on.
 *
 *  @deprecated Deprecated as of protocol version 2026-07-28 (SEP-2577). Remains for at least twelve months.
 */
case class ListRootsRequest(
    id: RequestId,
    params: Option[ListRootsRequestParams] = None,
    jsonrpc: JsonRpcVersion = JsonRpcVersion20
) {
  def toMcpRequest: McpRequest =
    McpRequest(method = Roots.list, id = id, params = None, jsonrpc = jsonrpc)
}

/** Client response to a [[ListRootsRequest]].
 *
 *  @param roots Roots the server may operate on.
 *
 *  @deprecated Deprecated as of protocol version 2026-07-28 (SEP-2577). Remains for at least twelve months.
 */
case class ListRootsResult(
    roots: List[Root]
)
