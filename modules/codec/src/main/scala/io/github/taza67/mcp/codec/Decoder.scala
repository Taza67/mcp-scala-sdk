package io.github.taza67.mcp.codec

case class DecodingError(message: String, cause: Option[Throwable] = None)

trait Decoder[A] {
  def decode(value: String): Either[DecodingError, A]
}
