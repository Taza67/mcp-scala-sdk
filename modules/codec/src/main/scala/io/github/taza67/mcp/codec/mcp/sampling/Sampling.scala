package io.github.taza67.mcp.codec.mcp.sampling

import io.github.taza67.mcp.codec.DecodingError
import io.github.taza67.mcp.codec.Fields
import io.github.taza67.mcp.codec.mcp.Content
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.mcp.MetaObject
import io.github.taza67.mcp.protocol.mcp.sampling.CreateMessageResult



/** Protocol AST bridge for MCP sampling-domain results (`JsonObject` ↔ ADT).
 *
 *  Currently [[CreateMessageResult]] (via [[Content]] helpers).
 */
object Sampling {

  def fromCreateMessageResult(createMessageResult: CreateMessageResult): JsonObject = {
    val base = Map(
      CreateMessageResult.ModelKey -> JsonString(createMessageResult.model),
      CreateMessageResult.RoleKey -> Content.fromRole(createMessageResult.role),
      CreateMessageResult.ContentKey -> Content.fromSamplingMessageContent(
        createMessageResult.content
      )
    )
    JsonObject(
      Fields.withOptional(
        base,
        CreateMessageResult.StopReasonKey -> createMessageResult.stopReason.map(JsonString(_)),
        CreateMessageResult.MetaKey -> createMessageResult.meta.map(m => JsonObject(m.value))
      )
    )
  }

  def toCreateMessageResult(
      createMessageResult: JsonObject
  ): Either[DecodingError, CreateMessageResult] = {
    val fields = createMessageResult.value
    for {
      model <- Fields.requiredString(fields, CreateMessageResult.ModelKey)
      role <- Fields.required(fields, CreateMessageResult.RoleKey).flatMap(Content.toRole)
      content <- Fields
        .required(fields, CreateMessageResult.ContentKey)
        .flatMap(Content.toSamplingMessageContent)
      stopReason <- Fields.optionalString(fields, CreateMessageResult.StopReasonKey)
      meta <- Fields.optional(fields, CreateMessageResult.MetaKey)(v =>
        Fields.asObject(v, CreateMessageResult.MetaKey).map(obj => MetaObject(obj.value))
      )
    } yield CreateMessageResult(
      model = model,
      role = role,
      content = content,
      stopReason = stopReason,
      meta = meta
    )
  }
}
