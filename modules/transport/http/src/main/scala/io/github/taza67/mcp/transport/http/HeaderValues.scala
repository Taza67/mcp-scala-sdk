package io.github.taza67.mcp.transport.http

import java.util.Base64

import io.github.taza67.mcp.codec.DecodingError



/** Mirrors MCP streamable-HTTP header value encoding (2026-07-28).
 *
 *  A value stays plain only when every character is visible ASCII
 *  (0x21-0x7E) or an inner SP/HTAB, with no leading/trailing SP or HTAB, and it
 *  does not look like the `=?base64?...?=` sentinel pair. Anything else is
 *  strict-UTF-8 encoded and wrapped in the sentinel markers. Markers are
 *  case-sensitive; this is a JVM transport utility, not the portable kernel.
 */
object HeaderValues {

  private val Prefix = "=?base64?"
  private val Suffix = "?="

  private def invalid: DecodingError = DecodingError("Invalid HTTP header value")

  private def hasSentinel(value: String): Boolean =
    value.length >= Prefix.length + Suffix.length &&
      value.startsWith(Prefix) &&
      value.endsWith(Suffix)

  private def isPlainText(value: String): Boolean =
    (value.isEmpty ||
      (value.charAt(0) != ' ' && value.charAt(0) != '\t' &&
        value.charAt(value.length - 1) != ' ' &&
        value.charAt(value.length - 1) != '\t')) &&
      value.forall { c => (c >= 0x21 && c <= 0x7e) || c == ' ' || c == '\t' }

  def encode(value: String): Either[DecodingError, String] =
    if (isPlainText(value) && !hasSentinel(value)) Right(value)
    else
      HttpUtf8.encode(value).map { bytes =>
        Prefix + Base64.getEncoder.encodeToString(bytes) + Suffix
      }.left.map(_ => invalid)

  def decode(value: String): Either[DecodingError, String] =
    if (hasSentinel(value)) {
      val payload = value.substring(Prefix.length, value.length - Suffix.length)
      try {
        val bytes = Base64.getDecoder.decode(payload)
        HttpUtf8.decode(bytes).left.map(_ => invalid)
      } catch {
        case _: IllegalArgumentException => Left(invalid)
      }
    } else if (isPlainText(value)) Right(value)
    else Left(invalid)
}
