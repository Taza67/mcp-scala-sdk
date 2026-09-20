package io.github.taza67.mcp.client

import io.github.taza67.mcp.protocol.mcp.McpRequest



/** Synchronous pull-stream port for MCP clients.
 *
 *  Implementations open a long-lived response channel for a request (e.g. an
 *  HTTP `text/event-stream` body). The client core wraps the returned stream
 *  with correlation and notification validation before exposing it.
 *
 *  Like [[ClientTransport]], this port borrows the underlying connection;
 *  callers own the returned [[ClientStream]] and must close it.
 */
trait StreamingClientTransport extends ClientTransport {

  /** Sends [[request]] and returns the stream of inbound messages for it. */
  def open(request: McpRequest): Either[ClientError, ClientStream]
}
