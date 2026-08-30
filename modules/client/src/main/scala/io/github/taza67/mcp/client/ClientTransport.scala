package io.github.taza67.mcp.client

import io.github.taza67.mcp.protocol.mcp.McpRequest
import io.github.taza67.mcp.protocol.mcp.McpResponse



/** Synchronous one-request/one-response port for MCP clients.
 *
 *  Implementations move a single request to the peer and return its response.
 *  The client core validates id correlation and decodes typed results; the
 *  transport only reports success, a remote error response, or a
 *  [[ClientError.TransportFailure]].
 *
 *  This port borrows whatever connection the implementation wraps and defines
 *  no lifecycle or close ownership.
 */
trait ClientTransport {

  /** Sends [[request]] and waits for its response. */
  def exchange(request: McpRequest): Either[ClientError, McpResponse]
}
