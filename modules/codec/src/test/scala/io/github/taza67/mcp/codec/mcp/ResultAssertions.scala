package io.github.taza67.mcp.codec.mcp

import io.github.taza67.mcp.codec.CodecAssertions
import io.github.taza67.mcp.codec.DecodingError
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.mcp.Result
import io.github.taza67.mcp.protocol.mcp.ResultMeta
import io.github.taza67.mcp.protocol.mcp.ResultType
import munit.Assertions



/** Round-trip helpers for domain results via the shared [[Result]] envelope. */
private[mcp] trait ResultAssertions { self: CodecAssertions with Assertions =>

  protected def assertDomainResultRoundTrip[A](
      value: A
  )(
      toResult: A => Result,
      fromFields: A => JsonObject,
      toFields: JsonObject => Either[DecodingError, A],
      withEnvelope: (A, ResultType, Option[ResultMeta]) => A
  ): Unit = {
    val result = toResult(value)
    assertRoundTrip(result)(Params.fromResult, Params.toResult)
    assertEquals(
      Params.toResult(Params.fromResult(result)).flatMap { decoded =>
        toFields(decoded.fields).map(d => withEnvelope(d, decoded.resultType, decoded.meta))
      },
      Right(value)
    )
  }
}
