package io.github.taza67.mcp.client

import io.github.taza67.mcp.protocol.jsonrpc.NumberRequestId
import io.github.taza67.mcp.protocol.jsonrpc.RequestId



/** Allocates distinct request ids for in-flight client requests. */
trait RequestIds {

  /** Returns the next id, or an error when no further id is available. */
  def next(): Either[ClientError, RequestId]
}

object RequestIds {

  /** Strictly increasing numeric ids starting at `initial + 1`.
   *
   *  Negative initials are permitted because the wire id policy accepts signed
   *  values. The sequence is bounded: once `Long.MaxValue` has been issued (or
   *  `initial` is `Long.MaxValue`), further calls return
   *  [[ClientError.RequestIdsExhausted]] and the counter never wraps. Each
   *  instance owns its own counter; there is no shared or random state.
   */
  def monotonic(initial: Long = 0L): RequestIds =
    new Monotonic(initial)

  private final class Monotonic(private var counter: Long) extends RequestIds {

    def next(): Either[ClientError, RequestId] =
      synchronized {
        if (counter == Long.MaxValue) Left(ClientError.RequestIdsExhausted)
        else {
          counter += 1
          Right(NumberRequestId(counter))
        }
      }
  }
}
