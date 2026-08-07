package io.github.taza67.mcp.codec

/** Failure while projecting a protocol AST value into a domain ADT. */
case class DecodingError(message: String, cause: Option[Throwable] = None)

/** Decode a wire `String` into an ADT value. */
trait Decoder[A] {
  def decode(value: String): Either[DecodingError, A]
}
