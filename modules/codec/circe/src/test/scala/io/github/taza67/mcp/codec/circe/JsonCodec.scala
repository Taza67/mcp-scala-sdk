package io.github.taza67.mcp.codec.circe

import io.github.taza67.mcp.codec.circe.JsonCodec
import io.github.taza67.mcp.protocol.json.{
  JsonArray => ProcotolJsonArray,
  JsonBool => ProtocolJsonBool,
  JsonNull => ProtocolJsonNull,
  JsonNumber => ProtocolJsonNumber,
  JsonObject => ProtocolJsonObject,
  JsonString => ProtocolJsonString,
  JsonValue => ProtocolJsonValue
}
import munit.FunSuite



class JsonCodecSuite extends FunSuite {
  test("Invalid JSON returns an error") {
    val invalidJson = "{"
    val eitherDecode = JsonCodec.JsonValueDecoder.decode(invalidJson)

    assert(eitherDecode.isLeft)
  }

  test("Protocol JSON encoded to Circe JSON is decoded into same Protocol JSON") {
    val initialProtocolJson = ProtocolJsonObject(
      Map(
        "project" -> ProtocolJsonString("Scala MCP SDK"),
        "description" -> ProtocolJsonString("MCP SDK for all Scala ecosystems"),
        "capabilities" -> ProcotolJsonArray(
          List(
            ProtocolJsonString("Prompts"),
            ProtocolJsonString("Tools"),
            ProtocolJsonString("Ellicitation"),
            ProtocolJsonString("Etc")
          )
        ),
        "author" -> ProtocolJsonObject(
          Map(
            "github" -> ProtocolJsonString("Taza67"),
            "gitlab" -> ProtocolJsonString("Taza67"),
            "stars" -> ProtocolJsonNumber(10_000_000),
            "active" -> ProtocolJsonBool(true)
          )
        ),
        "version" -> ProtocolJsonNull
      )
    )
    val encodedCirceJson =
      JsonCodec.JsonValueEncoder.encode(initialProtocolJson)

    val decodedProtocolJson = JsonCodec.JsonValueDecoder.decode(encodedCirceJson)

    assert(decodedProtocolJson.isRight)
    assertEquals(initialProtocolJson, decodedProtocolJson.getOrElse(ProtocolJsonNull))
  }
}
