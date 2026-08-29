package io.github.taza67.mcp.codec.ziojson

import io.github.taza67.mcp.codec.CodecAssertions
import io.github.taza67.mcp.protocol.json.JsonArray
import io.github.taza67.mcp.protocol.json.JsonBool
import io.github.taza67.mcp.protocol.json.JsonNull
import io.github.taza67.mcp.protocol.json.JsonNumber
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.json.JsonValue
import munit.FunSuite



class JsonCodecSuite extends FunSuite with CodecAssertions {

  test("all six AST variants round-trip, nested with empty containers") {
    val value: JsonValue = JsonObject(
      Map(
        "null"    -> JsonNull,
        "true"    -> JsonBool(true),
        "false"   -> JsonBool(false),
        "string"  -> JsonString("plain"),
        "number"  -> JsonNumber(BigDecimal("42")),
        "emptyArr" -> JsonArray(Nil),
        "emptyObj" -> JsonObject(Map.empty),
        "nested" -> JsonArray(
          List(
            JsonObject(Map("inner" -> JsonArray(List(JsonNull, JsonBool(true))))),
            JsonString("tail")
          )
        )
      )
    )
    assertRoundTrip[JsonValue, String](value)(
      JsonCodec.JsonValueEncoder.encode,
      JsonCodec.JsonValueDecoder.decode
    )
  }

  test("literal JSON text decodes to the expected AST") {
    assertEquals(
      JsonCodec.JsonValueDecoder.decode("{\"a\":1,\"b\":[null,true,\"x\"]}"),
      Right(
        JsonObject(
          Map(
            "a" -> JsonNumber(BigDecimal(1)),
            "b" -> JsonArray(List(JsonNull, JsonBool(true), JsonString("x")))
          )
        )
      )
    )
  }

  test("high-precision and exponent numbers are preserved exactly") {
    val value = JsonObject(
      Map(
        "big"    -> JsonNumber(BigDecimal("123456789012345678901234567890.123456789")),
        "exp"    -> JsonNumber(BigDecimal("1.5e300")),
        "tiny"   -> JsonNumber(BigDecimal("0.000000000000000000001"))
      )
    )
    assertRoundTrip[JsonValue, String](value)(
      JsonCodec.JsonValueEncoder.encode,
      JsonCodec.JsonValueDecoder.decode
    )
    val decoded = JsonCodec.JsonValueDecoder.decode(
      JsonCodec.JsonValueEncoder.encode(value)
    )
    assertEquals(decoded, Right(value))
  }

  test("unicode, quote, backslash, and newline strings round-trip compactly") {
    val tricky = "caf\u00e9 \"quoted\" \\\nline"
    val value = JsonObject(Map("id" -> JsonString(tricky)))
    val encoded = JsonCodec.JsonValueEncoder.encode(value)
    assert(!encoded.contains('\n'))
    assertEquals(JsonCodec.JsonValueDecoder.decode(encoded), Right(value))
  }

  test("malformed and empty input reject with a generic error") {
    val secret = "secret-marker"
    List("{", "[1,", "", "   ", secret).foreach { raw =>
      val result = JsonCodec.JsonValueDecoder.decode(raw)
      assert(result.isLeft, s"expected rejection for $raw")
      assert(!result.swap.toOption.get.message.contains(secret))
    }
  }

  test("trailing data after a value rejects; legal whitespace is accepted") {
    List("{}{}", "true false", "1x", "[1] trailing").foreach { raw =>
      assert(
        JsonCodec.JsonValueDecoder.decode(raw).isLeft,
        s"expected rejection for $raw"
      )
    }
    assertEquals(
      JsonCodec.JsonValueDecoder.decode("  \t\r\n [1,2] \n"),
      Right(JsonArray(List(JsonNumber(1), JsonNumber(2))))
    )
  }

  test("non-JSON whitespace control characters reject") {
    assert(JsonCodec.JsonValueDecoder.decode("[1]\u000b").isLeft)
  }

  test("duplicate object keys collapse last-wins") {
    assertEquals(
      JsonCodec.JsonValueDecoder.decode("{\"a\":1,\"a\":2}"),
      Right(JsonObject(Map("a" -> JsonNumber(2))))
    )
  }
}
