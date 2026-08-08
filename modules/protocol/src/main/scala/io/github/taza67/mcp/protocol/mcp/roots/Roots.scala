package io.github.taza67.mcp.protocol.mcp.roots

import io.github.taza67.mcp.protocol.jsonrpc.{JsonRpcVersion, JsonRpcVersion20, Method, RequestId}
import io.github.taza67.mcp.protocol.mcp._



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

object Root {

  /** Wire key for the root URI. */
  val UriKey: String = "uri"

  /** Wire key for the optional display name. */
  val NameKey: String = "name"

  /** Wire key for optional open metadata. */
  val MetaKey: String = RequestParams.MetaKey
}

/** Optional parameters for a `roots/list` request.
 *
 *  @param meta Optional open metadata on the request.
 */
case class ListRootsRequestParams(
    meta: Option[MetaObject] = None
)

object ListRootsRequestParams {

  /** Wire key for optional open metadata. */
  val MetaKey: String = RequestParams.MetaKey
}

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
  val method: Method = Roots.list
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

object ListRootsResult {

  /** Wire key for the roots array. */
  val RootsKey: String = "roots"
}
