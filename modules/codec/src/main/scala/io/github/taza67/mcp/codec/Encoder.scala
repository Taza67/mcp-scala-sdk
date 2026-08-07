package io.github.taza67.mcp.codec

/** Encode an ADT value into a wire `String`. */
trait Encoder[A] {
  def encode(value: A): String
}
