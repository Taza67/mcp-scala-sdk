package io.github.taza67.mcp.client

import io.github.taza67.mcp.protocol.jsonrpc.Error



/** Typed failure algebra for client request exchanges.
 *
 *  Variants carry no raw exceptions, input bytes, or decoded values beyond the
 *  protocol data a caller deliberately receives, such as a remote
 *  [[ClientError.RemoteError]].
 */
sealed trait ClientError

object ClientError {

  /** The transport failed to move the request or response. */
  case object TransportFailure extends ClientError

  /** The response id does not match the id of the in-flight request. */
  case object ResponseIdMismatch extends ClientError

  /** A response arrived that no in-flight request can claim. */
  case object UncorrelatedResponse extends ClientError

  /** The response result could not be decoded into the expected type. */
  case object InvalidResult extends ClientError

  /** The request-id allocator cannot produce another distinct id. */
  case object RequestIdsExhausted extends ClientError

  /** The configured transport cannot open request streams. */
  case object StreamingUnsupported extends ClientError

  /** The peer returned a JSON-RPC error response for the request. */
  final case class RemoteError(error: Error) extends ClientError
}
