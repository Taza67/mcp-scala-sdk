package io.github.taza67.mcp.codec.mcp.completion

import io.github.taza67.mcp.codec.DecodingError
import io.github.taza67.mcp.codec.Fields
import io.github.taza67.mcp.codec.Primitives
import io.github.taza67.mcp.codec.mcp.PlainRequests
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.json.JsonValue
import io.github.taza67.mcp.protocol.mcp.RequestParams
import io.github.taza67.mcp.protocol.mcp.completion.{Completion => CompletionMethods}
import io.github.taza67.mcp.protocol.mcp.completion.CompleteRequest
import io.github.taza67.mcp.protocol.mcp.completion.CompleteRequestParams
import io.github.taza67.mcp.protocol.mcp.completion.CompletionArgument
import io.github.taza67.mcp.protocol.mcp.completion.CompletionContext
import io.github.taza67.mcp.protocol.mcp.completion.CompletionReference
import io.github.taza67.mcp.protocol.mcp.completion.PromptReference
import io.github.taza67.mcp.protocol.mcp.completion.ResourceTemplateReference



/** Protocol AST bridge for MCP completion references (`JsonObject` to/from ADT). */
object Completion {

  def fromCompletionReference(reference: CompletionReference): JsonObject =
    reference match {
      case prompt: PromptReference =>
        JsonObject(
          Fields.withOptional(
            Map(
              CompletionReference.TypeKey -> JsonString(PromptReference.TypeValue),
              PromptReference.NameKey     -> JsonString(prompt.name)
            ),
            PromptReference.TitleKey -> prompt.title.map(JsonString(_))
          )
        )
      case resource: ResourceTemplateReference =>
        JsonObject(
          Map(
            CompletionReference.TypeKey      -> JsonString(ResourceTemplateReference.TypeValue),
            ResourceTemplateReference.UriKey -> JsonString(resource.uri)
          )
        )
    }

  def fromCompletionArgument(argument: CompletionArgument): JsonObject =
    JsonObject(
      Map(
        CompletionArgument.NameKey  -> JsonString(argument.name),
        CompletionArgument.ValueKey -> JsonString(argument.value)
      )
    )

  def fromCompletionContext(context: CompletionContext): JsonObject =
    JsonObject(
      Fields.withOptional(
        Map.empty,
        CompletionContext.ArgumentsKey ->
          context.arguments.map(Primitives.fromStringMap)
      )
    )

  def fromCompleteRequestParams(
      params: CompleteRequestParams
  ): RequestParams =
    RequestParams(
      meta = params.meta,
      fields = JsonObject(
        Fields.withOptional(
          Map(
            CompleteRequestParams.RefKey      -> fromCompletionReference(params.ref),
            CompleteRequestParams.ArgumentKey -> fromCompletionArgument(params.argument)
          ),
          CompleteRequestParams.ContextKey ->
            params.context.map(fromCompletionContext)
        )
      )
    )

  def fromCompleteRequest(request: CompleteRequest): JsonObject =
    PlainRequests.fromRequest(
      CompletionMethods.complete,
      request.id,
      fromCompleteRequestParams(request.params),
      request.jsonrpc
    )

  def toCompletionReference(
      obj: JsonObject
  ): Either[DecodingError, CompletionReference] = {
    val fields = obj.value
    Fields.requiredString(fields, CompletionReference.TypeKey).flatMap {
      case PromptReference.TypeValue           => toPromptReference(fields)
      case ResourceTemplateReference.TypeValue => toResourceTemplateReference(fields)
      case _ => Left(DecodingError("Invalid completion reference type"))
    }
  }

  def toCompletionArgument(
      obj: JsonObject
  ): Either[DecodingError, CompletionArgument] = {
    val fields = obj.value
    for {
      name <- Fields.requiredString(fields, CompletionArgument.NameKey)
      value <- Fields.requiredString(fields, CompletionArgument.ValueKey)
    } yield CompletionArgument(name = name, value = value)
  }

  def toCompletionContext(
      obj: JsonObject
  ): Either[DecodingError, CompletionContext] = {
    val fields = obj.value
    for {
      arguments <- Fields.optional(fields, CompletionContext.ArgumentsKey)(v =>
        Fields
          .asObject(v, CompletionContext.ArgumentsKey)
          .flatMap(Primitives.toStringMap(_, CompletionContext.ArgumentsKey))
          .left
          .map(_ => DecodingError("Invalid completion context arguments"))
      )
    } yield CompletionContext(arguments = arguments)
  }

  def toCompleteRequestParams(
      params: RequestParams
  ): Either[DecodingError, CompleteRequestParams] = {
    val fields = params.fields.value
    for {
      ref <- Fields
        .required(fields, CompleteRequestParams.RefKey)
        .flatMap(v =>
          Fields.asObject(v, CompleteRequestParams.RefKey).flatMap(toCompletionReference)
        )
      argument <- Fields
        .required(fields, CompleteRequestParams.ArgumentKey)
        .flatMap(v =>
          Fields.asObject(v, CompleteRequestParams.ArgumentKey).flatMap(toCompletionArgument)
        )
      context <- Fields.optional(fields, CompleteRequestParams.ContextKey)(v =>
        Fields.asObject(v, CompleteRequestParams.ContextKey).flatMap(toCompletionContext)
      )
    } yield CompleteRequestParams(
      meta = params.meta,
      ref = ref,
      argument = argument,
      context = context
    )
  }

  def toCompleteRequest(
      message: JsonValue
  ): Either[DecodingError, CompleteRequest] =
    PlainRequests.toRequest(CompletionMethods.complete, message)(toCompleteRequestParams) {
      (id, params, jsonrpc) =>
        CompleteRequest(id = id, params = params, jsonrpc = jsonrpc)
    }

  private def toPromptReference(
      fields: Map[String, JsonValue]
  ): Either[DecodingError, PromptReference] =
    for {
      name <- Fields.requiredString(fields, PromptReference.NameKey)
      title <- Fields.optionalString(fields, PromptReference.TitleKey)
    } yield PromptReference(name = name, title = title)

  private def toResourceTemplateReference(
      fields: Map[String, JsonValue]
  ): Either[DecodingError, ResourceTemplateReference] =
    for {
      uri <- Fields.requiredString(fields, ResourceTemplateReference.UriKey)
    } yield ResourceTemplateReference(uri = uri)
}
