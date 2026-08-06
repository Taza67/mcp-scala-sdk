package io.github.taza67.mcp.codec.circe

import io.github.taza67.mcp.codec.{Decoder => McpCodecDecoder, Encoder => McpCodecEncoder}
import io.github.taza67.mcp.codec.DecodingError
import io.github.taza67.mcp.codec.jsonrpc.Messages
import io.github.taza67.mcp.protocol.jsonrpc.Message



/** Circe wire façade for JSON-RPC [[Message]]: compose [[Messages]] with [[JsonCodec]]. */
object JsonRpcCodec {

  implicit object MessageEncoder extends McpCodecEncoder[Message] {
    def encode(value: Message): String =
      JsonCodec.JsonValueEncoder.encode(Messages.fromMessage(value))
  }

  implicit object MessageDecoder extends McpCodecDecoder[Message] {
    def decode(value: String): Either[DecodingError, Message] =
      JsonCodec.JsonValueDecoder.decode(value).flatMap(Messages.toMessage)
  }
}
