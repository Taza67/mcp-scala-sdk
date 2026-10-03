package io.github.taza67.mcp.transport.stdio

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

import io.github.taza67.mcp.codec.DecodingError
import io.github.taza67.mcp.protocol.jsonrpc.Error
import io.github.taza67.mcp.protocol.jsonrpc.InvalidRequestError
import io.github.taza67.mcp.protocol.jsonrpc.ParseError



/** Shared newline-delimited byte framing for the stdio transports.
 *
 *  One frame is the bytes up to and excluding LF, or up to EOF; a single
 *  trailing CR is excluded from the size accounting. At most
 *  `maxMessageSize + 1` bytes are stored; beyond that the rest of the frame
 *  is drained without counting so the guard cannot wrap on huge input. EOF
 *  with no pending bytes ends input, EOF mid-frame returns the partial
 *  frame. Frame bytes are decoded as strict UTF-8: malformed or unmappable
 *  input fails instead of replacing.
 *
 *  Neither helper closes its stream. `IOException` from the underlying read
 *  propagates to the caller.
 */
private[stdio] object StdioFrames {

  private val SizeLimitMessage = "stdio message exceeds size limit"

  /** Reads one bounded frame: `Right(None)` on clean EOF, `Right(Some)` for
   *  a strict-UTF-8 decoded frame, `Left` with a static error on oversize or
   *  invalid UTF-8. Never closes `in`; `IOException` propagates.
   */
  def read(
      in: BufferedInputStream,
      maxMessageSize: Int
  ): Either[Error, Option[String]] = {
    val buffer = new ByteArrayOutputStream()
    var oversized = false
    var eof = false
    var done = false
    var last = -1
    while (!done) {
      val unit = in.read()
      if (unit == -1) {
        eof = true
        done = true
      } else if (unit == '\n') done = true
      else {
        last = unit
        if (buffer.size() <= maxMessageSize) buffer.write(unit)
        else oversized = true
      }
    }
    val count = buffer.size()
    if (count == 0 && eof) Right(None)
    else {
      val effective = if (last == '\r') count - 1 else count
      if (oversized || effective > maxMessageSize)
        Left(InvalidRequestError(SizeLimitMessage))
      else
        decodeUtf8(buffer.toByteArray) match {
          case Right(text) => Right(Some(text))
          case Left(_)     => Left(ParseError())
        }
    }
  }

  /** Strict UTF-8 encoding for outbound frames: unpaired surrogates and
   *  other malformed UTF-16 fail instead of silently writing replacement
   *  bytes. The failure is a static [[DecodingError]] without a cause.
   */
  def encode(text: String): Either[DecodingError, Array[Byte]] =
    try {
      val encoded = StandardCharsets.UTF_8
        .newEncoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .encode(CharBuffer.wrap(text))
      val bytes = new Array[Byte](encoded.remaining())
      encoded.get(bytes)
      Right(bytes)
    } catch {
      case _: CharacterCodingException =>
        Left(DecodingError("Message is not encodable as UTF-8"))
    }

  private def decodeUtf8(
      bytes: Array[Byte]
  ): Either[CharacterCodingException, String] =
    try
      Right(
        StandardCharsets.UTF_8
          .newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(bytes))
          .toString
      )
    catch { case e: CharacterCodingException => Left(e) }
}
