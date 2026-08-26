package io.github.taza67.mcp.codec.mcp.completion

import io.github.taza67.mcp.codec.DecodingError
import io.github.taza67.mcp.codec.Fields
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.json.JsonValue
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
