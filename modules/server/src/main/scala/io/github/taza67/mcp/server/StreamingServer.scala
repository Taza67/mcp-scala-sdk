package io.github.taza67.mcp.server

import io.github.taza67.mcp.protocol.jsonrpc.Error
import io.github.taza67.mcp.protocol.mcp.McpRequest



/** [[Server]] that can additionally answer a request with a lazy response
 *  stream (for example SSE over HTTP).
 *
 *  `open` itself must not block on request processing: it returns a
 *  protocol [[Error]] or a [[ServerStream]] whose messages are produced on
 *  pull. Transports that do not support streams keep using `handle`.
 */
trait StreamingServer extends Server {

  /** Returns either a protocol error or a lazy stream for `request`. */
  def open(request: McpRequest): Either[Error, ServerStream]
}
