package io.github.taza67.mcp.transport.http

import java.io.InputStream

import io.github.taza67.mcp.protocol.jsonrpc.Error
import io.github.taza67.mcp.protocol.jsonrpc.InvalidRequestError
import io.github.taza67.mcp.protocol.jsonrpc.ParseError



/** Bounded strict-UTF-8 request body input for HTTP transports.
 *
 *  Reads at most `maxMessageSize + 1` bytes (never draining unbounded input)
 *  and decodes with `REPORT` actions so malformed input fails instead of being
 *  replaced. The borrowed stream is not closed; `IOException`s propagate.
 */
private[http] object HttpInput {

  private val SizeLimitMessage = "HTTP message exceeds size limit"

  def read(in: InputStream, maxMessageSize: Int): Either[Error, String] = {
    val bytes = in.readNBytes(maxMessageSize + 1)
    if (bytes.length > maxMessageSize)
      Left(InvalidRequestError(SizeLimitMessage))
    else HttpUtf8.decode(bytes).left.map(_ => ParseError())
  }
}
