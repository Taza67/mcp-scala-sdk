package io.github.taza67.mcp.codec

trait Encoder[A] {
  def encode(value: A): String
}
