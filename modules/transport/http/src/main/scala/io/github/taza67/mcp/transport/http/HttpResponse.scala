package io.github.taza67.mcp.transport.http

import io.github.taza67.mcp.protocol.json.JsonObject



/** Transport-neutral HTTP response: status, optional JSON body, headers.
 *
 *  Bodies stay in the protocol AST; wire text encoding is left to the adapter
 *  that owns a JSON backend.
 */
final case class HttpResponse(
    status: Int,
    body: Option[JsonObject] = None,
    headers: Map[String, String] = Map.empty
)
