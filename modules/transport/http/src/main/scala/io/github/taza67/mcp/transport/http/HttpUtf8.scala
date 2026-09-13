package io.github.taza67.mcp.transport.http

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

import io.github.taza67.mcp.codec.DecodingError



/** Strict UTF-8 transcoding shared by the HTTP transport boundaries.
 *
 *  Malformed input and unencodable UTF-16 (e.g. unpaired surrogates) fail
 *  with a static error instead of being silently replaced.
 */
private[http] object HttpUtf8 {

  private val Invalid = DecodingError("Invalid UTF-8")

  def decode(bytes: Array[Byte]): Either[DecodingError, String] =
    try
      Right(
        StandardCharsets.UTF_8
          .newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(bytes))
          .toString
      )
    catch { case _: CharacterCodingException => Left(Invalid) }

  def encode(text: String): Either[DecodingError, Array[Byte]] =
    try {
      val buffer = StandardCharsets.UTF_8
        .newEncoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .encode(CharBuffer.wrap(text))
      val bytes = new Array[Byte](buffer.remaining())
      buffer.get(bytes)
      Right(bytes)
    } catch { case _: CharacterCodingException => Left(Invalid) }
}
