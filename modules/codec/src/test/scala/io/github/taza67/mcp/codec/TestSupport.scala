package io.github.taza67.mcp.codec

import io.github.taza67.mcp.protocol.mcp.ClientCapabilities
import io.github.taza67.mcp.protocol.mcp.Implementation
import io.github.taza67.mcp.protocol.mcp.McpProtocolVersion20260728
import io.github.taza67.mcp.protocol.mcp.MetaObject
import io.github.taza67.mcp.protocol.mcp.RequestMeta
import munit.Assertions



/** Shared codec test fixtures. */
private[codec] object TestSupport {

  val requestMeta: RequestMeta = RequestMeta(
    protocolVersion = McpProtocolVersion20260728,
    clientCapabilities = ClientCapabilities(),
    extensions = MetaObject.empty
  )

  val implementation: Implementation =
    Implementation(name = "srv", version = "1.0.0")
}

/** Round-trip helpers (`encode` then `decode` ≡ identity) for AST and wire codecs. */
private[codec] trait CodecAssertions { self: Assertions =>

  protected def assertRoundTrip[A, W](
      value: A
  )(encode: A => W, decode: W => Either[DecodingError, A]): Unit =
    assertEquals(decode(encode(value)), Right(value))
}
