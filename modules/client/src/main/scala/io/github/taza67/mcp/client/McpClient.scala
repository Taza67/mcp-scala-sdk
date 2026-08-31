package io.github.taza67.mcp.client

import scala.util.control.NonFatal

import io.github.taza67.mcp.protocol.jsonrpc.Method
import io.github.taza67.mcp.protocol.jsonrpc.RequestId
import io.github.taza67.mcp.protocol.mcp.McpErrorResponse
import io.github.taza67.mcp.protocol.mcp.McpRequest
import io.github.taza67.mcp.protocol.mcp.McpRequestParams
import io.github.taza67.mcp.protocol.mcp.McpResponse
import io.github.taza67.mcp.protocol.mcp.McpSuccessResponse
import io.github.taza67.mcp.protocol.mcp.Result



/** Minimal synchronous MCP client core.
 *
 *  Each [[request]] allocates an id, sends one [[McpRequest]] through
 *  [[ClientTransport]], and correlates the response before exposing it:
 *  matching success ids yield the [[Result]] untouched, matching error ids
 *  become [[ClientError.RemoteError]], mismatched ids
 *  [[ClientError.ResponseIdMismatch]], and id-less errors
 *  [[ClientError.UncorrelatedResponse]].
 *
 *  Request metadata travels inside `params`; the client holds no connection
 *  state and owns no transport lifecycle. A throwing transport degrades to
 *  [[ClientError.TransportFailure]] without echoing exception details; fatal
 *  failures propagate.
 *
 *  @param transport Synchronous exchange port (borrowed, not owned).
 *  @param requestIds Id allocator shared across requests on this client.
 */
final case class McpClient(
    transport: ClientTransport,
    requestIds: RequestIds = RequestIds.monotonic()
) {

  /** Sends one request and returns the correlated result or a typed error. */
  def request(
      method: Method,
      params: McpRequestParams
  ): Either[ClientError, Result] =
    requestIds.next() match {
      case Left(error) => Left(error)
      case Right(id) =>
        val wireRequest =
          McpRequest(method = method, id = id, params = Some(params))
        val exchanged =
          try transport.exchange(wireRequest)
          catch { case NonFatal(_) => Left(ClientError.TransportFailure) }
        exchanged match {
          case Left(error)     => Left(error)
          case Right(response) => correlate(id, response)
        }
    }

  private def correlate(
      id: RequestId,
      response: McpResponse
  ): Either[ClientError, Result] =
    response match {
      case success: McpSuccessResponse =>
        if (success.id == id) Right(success.result)
        else Left(ClientError.ResponseIdMismatch)
      case error: McpErrorResponse =>
        error.id match {
          case Some(responseId) if responseId == id =>
            Left(ClientError.RemoteError(error.error))
          case Some(_) => Left(ClientError.ResponseIdMismatch)
          case None    => Left(ClientError.UncorrelatedResponse)
        }
    }
}
