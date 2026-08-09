package io.github.taza67.mcp.server

import io.github.taza67.mcp.protocol.mcp.Result



/** Encodes a domain result ADT into a protocol [[Result]]. */
trait ResultEncoder[A] {
  def encode(value: A): Result
}

object ResultEncoder {

  def apply[A](encode: A => Result): ResultEncoder[A] =
    (value: A) => encode(value)

  implicit val unit: ResultEncoder[Unit] =
    apply(_ => Results.empty())
}
