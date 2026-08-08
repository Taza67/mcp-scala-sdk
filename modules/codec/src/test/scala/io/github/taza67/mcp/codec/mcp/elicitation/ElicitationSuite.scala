package io.github.taza67.mcp.codec.mcp.elicitation

import io.github.taza67.mcp.codec.CodecAssertions
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.mcp.elicitation.ElicitAccept
import io.github.taza67.mcp.protocol.mcp.elicitation.ElicitBooleanValue
import io.github.taza67.mcp.protocol.mcp.elicitation.ElicitCancel
import io.github.taza67.mcp.protocol.mcp.elicitation.ElicitNumberValue
import io.github.taza67.mcp.protocol.mcp.elicitation.ElicitResult
import io.github.taza67.mcp.protocol.mcp.elicitation.ElicitStringListValue
import io.github.taza67.mcp.protocol.mcp.elicitation.ElicitStringValue
import munit.FunSuite



class ElicitationSuite extends FunSuite with CodecAssertions {

  test("ElicitResult round-trips accept with mixed content") {
    assertRoundTrip(
      ElicitResult(
        action = ElicitAccept,
        content = Some(
          Map(
            "name" -> ElicitStringValue("Ada"),
            "age" -> ElicitNumberValue(BigDecimal(42)),
            "ok" -> ElicitBooleanValue(true),
            "tags" -> ElicitStringListValue(List("a", "b"))
          )
        )
      )
    )(Elicitation.fromElicitResult, Elicitation.toElicitResult)
  }

  test("ElicitResult round-trips cancel without content") {
    assertRoundTrip(ElicitResult(action = ElicitCancel))(
      Elicitation.fromElicitResult,
      Elicitation.toElicitResult
    )
  }

  test("toElicitResult rejects invalid action") {
    val raw = JsonObject(Map(ElicitResult.ActionKey -> JsonString("maybe")))
    assert(Elicitation.toElicitResult(raw).isLeft)
  }
}
