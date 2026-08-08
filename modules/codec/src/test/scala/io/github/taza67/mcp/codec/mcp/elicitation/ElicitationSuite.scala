package io.github.taza67.mcp.codec.mcp.elicitation

import io.github.taza67.mcp.codec.CodecAssertions
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.mcp.elicitation.BooleanSchema
import io.github.taza67.mcp.protocol.mcp.elicitation.ElicitAccept
import io.github.taza67.mcp.protocol.mcp.elicitation.ElicitBooleanValue
import io.github.taza67.mcp.protocol.mcp.elicitation.ElicitCancel
import io.github.taza67.mcp.protocol.mcp.elicitation.ElicitNumberValue
import io.github.taza67.mcp.protocol.mcp.elicitation.ElicitRequest
import io.github.taza67.mcp.protocol.mcp.elicitation.ElicitRequestFormParams
import io.github.taza67.mcp.protocol.jsonrpc.StringRequestId
import io.github.taza67.mcp.codec.mcp.Messages
import io.github.taza67.mcp.protocol.mcp.sampling.Sampling
import io.github.taza67.mcp.protocol.mcp.elicitation.ElicitRequestUrlParams
import io.github.taza67.mcp.protocol.mcp.elicitation.ElicitRequestedSchema
import io.github.taza67.mcp.protocol.mcp.elicitation.ElicitResult
import io.github.taza67.mcp.protocol.mcp.elicitation.ElicitStringListValue
import io.github.taza67.mcp.protocol.mcp.elicitation.ElicitStringValue
import io.github.taza67.mcp.protocol.mcp.elicitation.EmailStringFormat
import io.github.taza67.mcp.protocol.mcp.elicitation.StringSchema
import munit.FunSuite



class ElicitationSuite extends FunSuite with CodecAssertions {

  private val minimalSchema =
    ElicitRequestedSchema(
      properties = Map("name" -> StringSchema()),
      required = Some(List("name"))
    )

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

  test("ElicitRequestedSchema round-trips string and boolean fields") {
    assertRoundTrip(
      ElicitRequestedSchema(
        properties = Map(
          "name" -> StringSchema(format = Some(EmailStringFormat)),
          "ok" -> BooleanSchema(default = Some(false))
        ),
        required = Some(List("name"))
      )
    )(Elicitation.fromElicitRequestedSchema, Elicitation.toElicitRequestedSchema)
  }

  test("ElicitRequestFormParams round-trips") {
    assertRoundTrip(
      ElicitRequestFormParams(
        message = "Please provide your GitHub username",
        requestedSchema = minimalSchema
      )
    )(Elicitation.fromElicitRequestFormParams, Elicitation.toElicitRequestFormParams)
  }

  test("ElicitRequestUrlParams round-trips") {
    assertRoundTrip(
      ElicitRequestUrlParams(
        message = "Open this URL to continue",
        url = "https://example.com/auth"
      )
    )(Elicitation.fromElicitRequestUrlParams, Elicitation.toElicitRequestUrlParams)
  }

  test("toElicitRequestParams classifies form vs url") {
    val form = Elicitation.fromElicitRequestFormParams(
      ElicitRequestFormParams(message = "hi", requestedSchema = minimalSchema)
    )
    val url = Elicitation.fromElicitRequestUrlParams(
      ElicitRequestUrlParams(message = "hi", url = "https://example.com")
    )
    assert(Elicitation.toElicitRequestParams(form).exists(_.isInstanceOf[ElicitRequestFormParams]))
    assert(Elicitation.toElicitRequestParams(url).exists(_.isInstanceOf[ElicitRequestUrlParams]))
  }

  test("ElicitRequest round-trips form mode") {
    assertRoundTrip(
      ElicitRequest(
        id = StringRequestId("1"),
        params = ElicitRequestFormParams(
          message = "Please provide your GitHub username",
          requestedSchema = minimalSchema
        )
      )
    )(Elicitation.fromElicitRequest, Elicitation.toElicitRequest)
  }

  test("toElicitRequest rejects wrong method") {
    val raw = Messages.fromRequest(
      io.github.taza67.mcp.protocol.mcp.McpRequest(
        method = Sampling.createMessage,
        id = StringRequestId("1"),
        params = None
      )
    )
    assert(Elicitation.toElicitRequest(raw).isLeft)
  }

  test("toElicitRequestedSchema rejects non-object type") {
    val raw = JsonObject(
      Map(
        ElicitRequestedSchema.TypeKey -> JsonString("array"),
        ElicitRequestedSchema.PropertiesKey -> JsonObject(Map.empty)
      )
    )
    assert(Elicitation.toElicitRequestedSchema(raw).isLeft)
  }
}
