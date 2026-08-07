package io.github.taza67.mcp.codec.circe

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

  test("Invalid JSON returns an error") {
    assert(JsonCodec.JsonValueDecoder.decode("{").isLeft)
  }

  test("JsonValue round-trips") {
    assertRoundTrip[JsonValue, String](
      JsonObject(
        Map(
          "project" -> JsonString("Scala MCP SDK"),
          "description" -> JsonString("MCP SDK for all Scala ecosystems"),
          "capabilities" -> JsonArray(
            List(
              JsonString("Prompts"),
              JsonString("Tools"),
              JsonString("Elicitation"),
              JsonString("Etc")
            )
          ),
          "author" -> JsonObject(
            Map(
              "github" -> JsonString("Taza67"),
              "gitlab" -> JsonString("Taza67"),
              "stars" -> JsonNumber(10_000_000),
              "active" -> JsonBool(true)
            )
          ),
          "version" -> JsonNull
        )
      )
    )(JsonCodec.JsonValueEncoder.encode, JsonCodec.JsonValueDecoder.decode)
  }
}
