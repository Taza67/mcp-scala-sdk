package io.github.taza67.mcp.protocol.mcp

import io.github.taza67.mcp.protocol.jsonrpc.{JsonRpcVersion, JsonRpcVersion20, Method, RequestId}



/** Method name for server capability discovery: `"server/discover"`. */
object ServerDiscover {
  val method: Method = Method("server/discover")
}

/** Asks the server to advertise supported protocol versions, capabilities, and metadata.
 *
 *  Servers '''MUST''' implement `server/discover`. Clients '''MAY''' call it; version
 *  negotiation can also happen inline via per-request `_meta`.
 *
 *  @param id JSON-RPC request id.
 *  @param params Request parameters (`_meta` required).
 */
case class DiscoverRequest(
    id: RequestId,
    params: RequestParams,
    jsonrpc: JsonRpcVersion = JsonRpcVersion20
) {
  def toMcpRequest: McpRequest =
    McpRequest(
      method = ServerDiscover.method,
      id = id,
      params = Some(params),
      jsonrpc = jsonrpc
    )
}

/** Result of a `server/discover` request.
 *
 *  @param supportedVersions Protocol versions this server supports; the client should
 *                           pick one for subsequent requests.
 *  @param capabilities Capabilities of the server.
 *  @param ttlMs Cache freshness hint in milliseconds (like Cache-Control `max-age`).
 *               `0` means immediately stale; positive means fresh for that many ms.
 *  @param cacheScope Whether the cached response may be shared across auth contexts.
 *  @param instructions Natural-language guidance for using the server effectively
 *                      (e.g. for a system prompt). Should not duplicate tool descriptions.
 *  @param resultType Result discriminant; normally [[CompleteResultType]].
 *  @param meta Optional result metadata (servers SHOULD include `serverInfo`).
 */
case class DiscoverResult(
    supportedVersions: List[String],
    capabilities: ServerCapabilities,
    ttlMs: Long,
    cacheScope: CacheScope,
    instructions: Option[String] = None,
    resultType: ResultType = CompleteResultType,
    meta: Option[ResultMeta] = None
)

/** Successful JSON-RPC response to a `server/discover` request.
 *
 *  @param result Typed discover payload.
 *  @param id Same id as the corresponding [[DiscoverRequest]].
 */
case class DiscoverResultResponse(
    result: DiscoverResult,
    id: RequestId,
    jsonrpc: JsonRpcVersion = JsonRpcVersion20
) {
  def toMcpResponse: McpSuccessResponse =
    McpSuccessResponse(
      result = Result(
        resultType = result.resultType,
        meta = result.meta
      ),
      id = id,
      jsonrpc = jsonrpc
    )
}
