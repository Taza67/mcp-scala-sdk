package io.github.taza67.mcp.client

import io.github.taza67.mcp.protocol.jsonrpc.RequestId
import io.github.taza67.mcp.protocol.mcp.McpErrorResponse
import io.github.taza67.mcp.protocol.mcp.McpResponse
import io.github.taza67.mcp.protocol.mcp.McpSuccessResponse



/** Shared response-id correlation policy for one-shot and streaming exchanges. */
private[client] object ClientResponses {

  /** Validates `response` belongs to the request carrying `requestId`:
   *  success ids and present error ids must match, id-less errors are
   *  uncorrelated.
   */
  def validateId(
      requestId: RequestId,
      response: McpResponse
  ): Either[ClientError, Unit] =
    response match {
      case success: McpSuccessResponse =>
        if (success.id == requestId) Right(())
        else Left(ClientError.ResponseIdMismatch)
      case error: McpErrorResponse =>
        error.id match {
          case Some(responseId) if responseId == requestId => Right(())
          case Some(_)                                     =>
            Left(ClientError.ResponseIdMismatch)
          case None => Left(ClientError.UncorrelatedResponse)
        }
    }
}
