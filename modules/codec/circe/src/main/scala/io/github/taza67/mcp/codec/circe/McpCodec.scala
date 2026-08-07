package io.github.taza67.mcp.codec.circe

import io.github.taza67.mcp.codec.{Decoder => CodecDecoder, Encoder => CodecEncoder}
import io.github.taza67.mcp.codec.DecodingError
import io.github.taza67.mcp.codec.mcp.Messages
import io.github.taza67.mcp.protocol.mcp.McpMessage



/** Circe wire façade for MCP [[McpMessage]]: compose [[Messages]] with [[JsonCodec]]. */
object McpCodec {

  implicit object MessageEncoder extends CodecEncoder[McpMessage] {
    def encode(value: McpMessage): String =
      JsonCodec.JsonValueEncoder.encode(Messages.fromMessage(value))
  }

  implicit object MessageDecoder extends CodecDecoder[McpMessage] {
    def decode(value: String): Either[DecodingError, McpMessage] =
      JsonCodec.JsonValueDecoder.decode(value).flatMap(Messages.toMessage)
  }
}
