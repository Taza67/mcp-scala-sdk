package io.github.taza67.mcp.codec.mcp.completion

import io.github.taza67.mcp.codec.TestSupport
import io.github.taza67.mcp.codec.mcp.completion.{Completion => CompletionCodec}
import io.github.taza67.mcp.protocol.json.JsonNull
import io.github.taza67.mcp.protocol.json.JsonNumber
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.json.JsonValue
import io.github.taza67.mcp.protocol.jsonrpc.NumberRequestId
import io.github.taza67.mcp.protocol.jsonrpc.StringRequestId
import io.github.taza67.mcp.protocol.mcp.McpProtocolVersion20260728
import io.github.taza67.mcp.protocol.mcp.MetaObject
import io.github.taza67.mcp.protocol.mcp.RequestMeta
import io.github.taza67.mcp.protocol.mcp.RequestParams
import io.github.taza67.mcp.protocol.mcp.completion.{Completion => CompletionMethods}
import io.github.taza67.mcp.protocol.mcp.completion.CompleteRequest
import io.github.taza67.mcp.protocol.mcp.completion.CompleteRequestParams
import io.github.taza67.mcp.protocol.mcp.completion.CompletionArgument
import io.github.taza67.mcp.protocol.mcp.completion.CompletionContext
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

  test("completion argument encodes and decodes literally, empty value valid") {
    val raw = JsonObject(
      Map(
        CompletionArgument.NameKey  -> JsonString("prefix"),
        CompletionArgument.ValueKey -> JsonString("f")
      )
    )
    val expected = CompletionArgument(name = "prefix", value = "f")
    assertEquals(CompletionCodec.toCompletionArgument(raw), Right(expected))
    assertEquals(CompletionCodec.fromCompletionArgument(expected), raw)

    val empty = JsonObject(
      Map(
        CompletionArgument.NameKey  -> JsonString("prefix"),
        CompletionArgument.ValueKey -> JsonString("")
      )
    )
    assertEquals(
      CompletionCodec.toCompletionArgument(empty),
      Right(CompletionArgument(name = "prefix", value = ""))
    )
  }

  test("argument missing or wrong-typed members reject") {
    val cases = List(
      JsonObject(Map(CompletionArgument.ValueKey -> JsonString("f"))),
      JsonObject(Map(CompletionArgument.NameKey -> JsonString("prefix"))),
      JsonObject(
        Map(
          CompletionArgument.NameKey  -> JsonNumber(1),
          CompletionArgument.ValueKey -> JsonString("f")
        )
      ),
      JsonObject(
        Map(
          CompletionArgument.NameKey  -> JsonString("prefix"),
          CompletionArgument.ValueKey -> JsonNumber(1)
        )
      )
    )
    cases.foreach(raw =>
      assert(CompletionCodec.toCompletionArgument(raw).isLeft, s"expected rejection for $raw")
    )
  }

  test("context arguments distinguish absent, empty, and populated") {
    val absent = JsonObject(Map.empty)
    assertEquals(
      CompletionCodec.toCompletionContext(absent),
      Right(CompletionContext(arguments = None))
    )
    assertEquals(CompletionCodec.fromCompletionContext(CompletionContext(None)), absent)

    val empty = JsonObject(
      Map(CompletionContext.ArgumentsKey -> JsonObject(Map.empty))
    )
    val emptyExpected = CompletionContext(arguments = Some(Map.empty))
    assertEquals(CompletionCodec.toCompletionContext(empty), Right(emptyExpected))
    assertEquals(CompletionCodec.fromCompletionContext(emptyExpected), empty)

    val populated = JsonObject(
      Map(
        CompletionContext.ArgumentsKey -> JsonObject(
          Map("branch" -> JsonString("main"), "path" -> JsonString("src/"))
        )
      )
    )
    val populatedExpected =
      CompletionContext(arguments = Some(Map("branch" -> "main", "path" -> "src/")))
    assertEquals(CompletionCodec.toCompletionContext(populated), Right(populatedExpected))
    assertEquals(CompletionCodec.fromCompletionContext(populatedExpected), populated)
  }

  test("invalid context arguments reject without echoing secrets") {
    val secret = "secret-context-key"
    val cases = List(
      JsonObject(
        Map(
          CompletionContext.ArgumentsKey -> JsonObject(
            Map(secret -> JsonNumber(1))
          )
        )
      ),
      JsonObject(
        Map(
          CompletionContext.ArgumentsKey -> JsonObject(
            Map(secret -> JsonNull)
          )
        )
      ),
      JsonObject(Map(CompletionContext.ArgumentsKey -> JsonNumber(1))),
      JsonObject(Map(CompletionContext.ArgumentsKey -> JsonNull))
    )
    cases.foreach { raw =>
      val result = CompletionCodec.toCompletionContext(raw)
      assert(result.isLeft, s"expected rejection for $raw")
      val message = result.swap.toOption.get.message
      assert(!message.contains(secret))
    }
  }

  test("complete request params preserve meta, members, and meta extensions") {
    val extendedMeta = TestSupport.requestMeta.copy(
      extensions = MetaObject(Map("x-extra" -> JsonString("v")))
    )
    val fields = JsonObject(
      Map(
        CompleteRequestParams.RefKey -> JsonObject(
          Map(
            CompletionReference.TypeKey -> JsonString(PromptReference.TypeValue),
            PromptReference.NameKey     -> JsonString("git-commit")
          )
        ),
        CompleteRequestParams.ArgumentKey -> JsonObject(
          Map(
            CompletionArgument.NameKey  -> JsonString("prefix"),
            CompletionArgument.ValueKey -> JsonString("f")
          )
        ),
        CompleteRequestParams.ContextKey -> JsonObject(
          Map(
            CompletionContext.ArgumentsKey -> JsonObject(
              Map("branch" -> JsonString("main"))
            )
          )
        )
      )
    )
    val params = RequestParams(meta = extendedMeta, fields = fields)
    val expected = CompleteRequestParams(
      meta = extendedMeta,
      ref = PromptReference(name = "git-commit"),
      argument = CompletionArgument(name = "prefix", value = "f"),
      context = Some(CompletionContext(arguments = Some(Map("branch" -> "main"))))
    )
    assertEquals(CompletionCodec.toCompleteRequestParams(params), Right(expected))
    val encoded = CompletionCodec.fromCompleteRequestParams(expected)
    assertEquals(encoded.fields, fields)
    assertEquals(encoded.meta, extendedMeta)
  }

  test("complete request round-trips literal envelope for string and numeric ids") {
    def envelope(id: JsonValue): JsonObject =
      JsonObject(
        Map(
          "jsonrpc" -> JsonString("2.0"),
          "id"      -> id,
          "method"  -> JsonString(CompletionMethods.complete.value),
          "params"  -> JsonObject(
            Map(
              RequestParams.MetaKey -> JsonObject(
                Map(
                  RequestMeta.ProtocolVersionKey ->
                    JsonString(McpProtocolVersion20260728.value),
                  RequestMeta.ClientCapabilitiesKey -> JsonObject(Map.empty)
                )
              ),
              CompleteRequestParams.RefKey -> JsonObject(
                Map(
                  CompletionReference.TypeKey -> JsonString(PromptReference.TypeValue),
                  PromptReference.NameKey     -> JsonString("git-commit")
                )
              ),
              CompleteRequestParams.ArgumentKey -> JsonObject(
                Map(
                  CompletionArgument.NameKey  -> JsonString("prefix"),
                  CompletionArgument.ValueKey -> JsonString("f")
                )
              )
            )
          )
        )
      )
    val baseParams = CompleteRequestParams(
      meta = TestSupport.requestMeta,
      ref = PromptReference(name = "git-commit"),
      argument = CompletionArgument(name = "prefix", value = "f")
    )
    val stringId = envelope(JsonString("c-1"))
    val stringExpected = CompleteRequest(id = StringRequestId("c-1"), params = baseParams)
    assertEquals(CompletionCodec.toCompleteRequest(stringId), Right(stringExpected))
    assertEquals(CompletionCodec.fromCompleteRequest(stringExpected), stringId)

    val numericId = envelope(JsonNumber(7))
    val numericExpected = CompleteRequest(id = NumberRequestId(7), params = baseParams)
    assertEquals(CompletionCodec.toCompleteRequest(numericId), Right(numericExpected))
    assertEquals(CompletionCodec.fromCompleteRequest(numericExpected), numericId)
  }

  test("complete request rejects wrong method, missing params, and malformed members") {
    def envelope(params: JsonValue): JsonObject =
      JsonObject(
        Map(
          "jsonrpc" -> JsonString("2.0"),
          "id"      -> JsonString("c-1"),
          "method"  -> JsonString(CompletionMethods.complete.value),
          "params"  -> params
        )
      )
    val validMeta = JsonObject(
      Map(
        RequestMeta.ProtocolVersionKey ->
          JsonString(McpProtocolVersion20260728.value),
        RequestMeta.ClientCapabilitiesKey -> JsonObject(Map.empty)
      )
    )
    val validRef = JsonObject(
      Map(
        CompletionReference.TypeKey -> JsonString(PromptReference.TypeValue),
        PromptReference.NameKey     -> JsonString("git-commit")
      )
    )
    val validArg = JsonObject(
      Map(
        CompletionArgument.NameKey  -> JsonString("prefix"),
        CompletionArgument.ValueKey -> JsonString("f")
      )
    )
    def params(members: (String, JsonValue)*): JsonObject =
      JsonObject(Map(RequestParams.MetaKey -> validMeta) ++ members)

    val wrongMethod = JsonObject(
      Map(
        "jsonrpc" -> JsonString("2.0"),
        "id"      -> JsonString("c-1"),
        "method"  -> JsonString("tools/call"),
        "params"  -> params(
          CompleteRequestParams.RefKey      -> validRef,
          CompleteRequestParams.ArgumentKey -> validArg
        )
      )
    )
    val missingParams = JsonObject(
      Map(
        "jsonrpc" -> JsonString("2.0"),
        "id"      -> JsonString("c-1"),
        "method"  -> JsonString(CompletionMethods.complete.value)
      )
    )
    val cases = List(
      wrongMethod,
      missingParams,
      envelope(
        params(
          CompleteRequestParams.RefKey -> JsonObject(
            Map(CompletionReference.TypeKey -> JsonString(PromptReference.TypeValue))
          ),
          CompleteRequestParams.ArgumentKey -> validArg
        )
      ),
      envelope(
        params(
          CompleteRequestParams.RefKey      -> validRef,
          CompleteRequestParams.ArgumentKey -> JsonObject(
            Map(CompletionArgument.NameKey -> JsonString("prefix"))
          )
        )
      ),
      envelope(
        params(
          CompleteRequestParams.RefKey      -> validRef,
          CompleteRequestParams.ArgumentKey -> validArg,
          CompleteRequestParams.ContextKey  -> JsonNumber(1)
        )
      )
    )
    cases.foreach(raw =>
      assert(CompletionCodec.toCompleteRequest(raw).isLeft, s"expected rejection for $raw")
    )
  }
}
