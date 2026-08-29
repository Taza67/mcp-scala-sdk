package io.github.taza67.mcp.codec.ziojson

import io.github.taza67.mcp.codec.{Decoder => CodecDecoder, Encoder => CodecEncoder}
import io.github.taza67.mcp.codec.DecodingError
import io.github.taza67.mcp.codec.jsonrpc.Messages
import io.github.taza67.mcp.protocol.jsonrpc.Message



/** zio-json wire facade for JSON-RPC [[Message]]: compose [[Messages]] with [[JsonCodec]]. */
object JsonRpcCodec {

  implicit object MessageEncoder extends CodecEncoder[Message] {
    def encode(value: Message): String =
      JsonCodec.JsonValueEncoder.encode(Messages.fromMessage(value))
  }

  implicit object MessageDecoder extends CodecDecoder[Message] {
    def decode(value: String): Either[DecodingError, Message] =
      JsonCodec.JsonValueDecoder.decode(value).flatMap(Messages.toMessage)
  }
}
