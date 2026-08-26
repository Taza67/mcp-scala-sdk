package io.github.taza67.mcp.codec.mcp.completion

import io.github.taza67.mcp.codec.mcp.completion.{Completion => CompletionCodec}
import io.github.taza67.mcp.protocol.json.JsonNull
import io.github.taza67.mcp.protocol.json.JsonNumber
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.json.JsonValue
import io.github.taza67.mcp.protocol.mcp.completion.CompletionReference
import io.github.taza67.mcp.protocol.mcp.completion.PromptReference
import io.github.taza67.mcp.protocol.mcp.completion.ResourceTemplateReference
import munit.FunSuite



class CompletionSuite extends FunSuite {

  test("prompt reference with title decodes and encodes exactly") {
    val raw = JsonObject(
      Map(
        CompletionReference.TypeKey -> JsonString(PromptReference.TypeValue),
        PromptReference.NameKey     -> JsonString("git-commit"),
        PromptReference.TitleKey    -> JsonString("Commit helper")
      )
    )
    val expected = PromptReference(name = "git-commit", title = Some("Commit helper"))
    assertEquals(CompletionCodec.toCompletionReference(raw), Right(expected))
    assertEquals(CompletionCodec.fromCompletionReference(expected), raw)
  }

  test("prompt reference without title omits the key, no null") {
    val raw = JsonObject(
      Map(
        CompletionReference.TypeKey -> JsonString(PromptReference.TypeValue),
        PromptReference.NameKey     -> JsonString("git-commit")
      )
    )
    val expected = PromptReference(name = "git-commit")
    assertEquals(CompletionCodec.toCompletionReference(raw), Right(expected))
    val encoded = CompletionCodec.fromCompletionReference(expected)
    assertEquals(encoded, raw)
    assert(!encoded.value.contains(PromptReference.TitleKey))
  }

  test("resource-template reference round-trips the URI template") {
    val raw = JsonObject(
      Map(
        CompletionReference.TypeKey      -> JsonString(ResourceTemplateReference.TypeValue),
        ResourceTemplateReference.UriKey -> JsonString("file:///{path}")
      )
    )
    val expected = ResourceTemplateReference(uri = "file:///{path}")
    assertEquals(CompletionCodec.toCompletionReference(raw), Right(expected))
    assertEquals(CompletionCodec.fromCompletionReference(expected), raw)
  }

  test("unknown, missing, or non-string type rejects without echoing the value") {
    val secret = "secret-type-tag"
    val cases = List(
      JsonObject(
        Map(
          CompletionReference.TypeKey -> JsonString(secret),
          PromptReference.NameKey     -> JsonString("git-commit")
        )
      ),
      JsonObject(Map(PromptReference.NameKey -> JsonString("git-commit"))),
      JsonObject(
        Map(
          CompletionReference.TypeKey -> JsonNumber(1),
          PromptReference.NameKey     -> JsonString("git-commit")
        )
      )
    )
    cases.foreach { raw =>
      val result = CompletionCodec.toCompletionReference(raw)
      assert(result.isLeft, s"expected rejection for $raw")
      assert(!result.swap.toOption.get.message.contains(secret))
    }
  }

  test("invalid required and optional members reject") {
    def prompt(fields: (String, JsonValue)*): JsonObject =
      JsonObject(
        Map(CompletionReference.TypeKey -> JsonString(PromptReference.TypeValue)) ++ fields
      )
    def resource(fields: (String, JsonValue)*): JsonObject =
      JsonObject(
        Map(
          CompletionReference.TypeKey -> JsonString(ResourceTemplateReference.TypeValue)
        ) ++ fields
      )
    val cases = List(
      prompt(),
      prompt(PromptReference.NameKey -> JsonNumber(1)),
      prompt(
        PromptReference.NameKey -> JsonString("git-commit"),
        PromptReference.TitleKey -> JsonNumber(1)
      ),
      prompt(
        PromptReference.NameKey -> JsonString("git-commit"),
        PromptReference.TitleKey -> JsonNull
      ),
      resource(),
      resource(ResourceTemplateReference.UriKey -> JsonNumber(1))
    )
    cases.foreach(raw =>
      assert(CompletionCodec.toCompletionReference(raw).isLeft, s"expected rejection for $raw")
    )
  }
}
