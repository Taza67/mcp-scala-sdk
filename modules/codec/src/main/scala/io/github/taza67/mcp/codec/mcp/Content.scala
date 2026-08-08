package io.github.taza67.mcp.codec.mcp

import io.github.taza67.mcp.codec.DecodingError
import io.github.taza67.mcp.codec.Fields
import io.github.taza67.mcp.codec.Primitives
import io.github.taza67.mcp.protocol.json.JsonArray
import io.github.taza67.mcp.protocol.json.JsonNumber
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.json.JsonValue
import io.github.taza67.mcp.protocol.mcp.Annotations
import io.github.taza67.mcp.protocol.mcp.AudioContent
import io.github.taza67.mcp.protocol.mcp.BlobResourceContents
import io.github.taza67.mcp.protocol.mcp.ContentBlock
import io.github.taza67.mcp.protocol.mcp.EmbeddedResource
import io.github.taza67.mcp.protocol.mcp.ImageContent
import io.github.taza67.mcp.protocol.mcp.MetaObject
import io.github.taza67.mcp.protocol.mcp.ResourceContents
import io.github.taza67.mcp.protocol.mcp.ResourceLink
import io.github.taza67.mcp.protocol.mcp.Role
import io.github.taza67.mcp.protocol.mcp.SamplingMessageContentBlock
import io.github.taza67.mcp.protocol.mcp.TextContent
import io.github.taza67.mcp.protocol.mcp.TextResourceContents
import io.github.taza67.mcp.protocol.mcp.ToolResultContent
import io.github.taza67.mcp.protocol.mcp.ToolUseContent
import io.github.taza67.mcp.protocol.mcp.sampling.CreateMessageResult
import io.github.taza67.mcp.protocol.mcp.sampling.MultiSamplingContent
import io.github.taza67.mcp.protocol.mcp.sampling.SamplingMessageContent
import io.github.taza67.mcp.protocol.mcp.sampling.SingleSamplingContent



/** Protocol AST bridge for MCP content blocks (`JsonObject` ↔ ADT).
 *
 *  Covers [[Role]], [[Annotations]], [[ContentBlock]],
 *  [[SamplingMessageContentBlock]], [[ResourceContents]], and
 *  [[SamplingMessageContent]].
 */
object Content {

  def fromRole(role: Role): JsonString =
    JsonString(role.value)

  def fromAnnotations(annotations: Annotations): JsonObject =
    JsonObject(
      Fields.withOptional(
        Map.empty,
        Annotations.AudienceKey -> annotations.audience.map(
          Primitives.fromList(_)(fromRole)
        ),
        Annotations.PriorityKey -> annotations.priority.map(Primitives.fromDouble),
        Annotations.LastModifiedKey -> annotations.lastModified.map(JsonString(_))
      )
    )

  def fromTextContent(textContent: TextContent): JsonObject = {
    val base = Map(
      ContentBlock.TypeKey -> JsonString(textContent.contentType),
      TextContent.TextKey -> JsonString(textContent.text)
    )
    JsonObject(
      Fields.withOptional(
        base,
        ContentBlock.AnnotationsKey -> textContent.annotations.map(fromAnnotations),
        ContentBlock.MetaKey -> textContent.meta.map(m => JsonObject(m.value))
      )
    )
  }

  def fromImageContent(imageContent: ImageContent): JsonObject = {
    val base = Map(
      ContentBlock.TypeKey -> JsonString(imageContent.contentType),
      ImageContent.DataKey -> JsonString(imageContent.data),
      ImageContent.MimeTypeKey -> JsonString(imageContent.mimeType)
    )
    JsonObject(
      Fields.withOptional(
        base,
        ContentBlock.AnnotationsKey -> imageContent.annotations.map(fromAnnotations),
        ContentBlock.MetaKey -> imageContent.meta.map(m => JsonObject(m.value))
      )
    )
  }

  def fromAudioContent(audioContent: AudioContent): JsonObject = {
    val base = Map(
      ContentBlock.TypeKey -> JsonString(audioContent.contentType),
      AudioContent.DataKey -> JsonString(audioContent.data),
      AudioContent.MimeTypeKey -> JsonString(audioContent.mimeType)
    )
    JsonObject(
      Fields.withOptional(
        base,
        ContentBlock.AnnotationsKey -> audioContent.annotations.map(fromAnnotations),
        ContentBlock.MetaKey -> audioContent.meta.map(m => JsonObject(m.value))
      )
    )
  }

  def fromToolUseContent(toolUseContent: ToolUseContent): JsonObject = {
    val base = Map(
      ContentBlock.TypeKey -> JsonString(toolUseContent.contentType),
      ToolUseContent.IdKey -> JsonString(toolUseContent.id),
      ToolUseContent.NameKey -> JsonString(toolUseContent.name),
      ToolUseContent.InputKey -> toolUseContent.input
    )
    JsonObject(
      Fields.withOptional(
        base,
        ContentBlock.MetaKey -> toolUseContent.meta.map(m => JsonObject(m.value))
      )
    )
  }

  def fromTextResourceContents(textResourceContents: TextResourceContents): JsonObject = {
    val base = Map(
      ResourceContents.UriKey -> JsonString(textResourceContents.uri),
      TextResourceContents.TextKey -> JsonString(textResourceContents.text)
    )
    JsonObject(
      Fields.withOptional(
        base,
        ResourceContents.MimeTypeKey -> textResourceContents.mimeType.map(JsonString(_)),
        ResourceContents.MetaKey -> textResourceContents.meta.map(m => JsonObject(m.value))
      )
    )
  }

  def fromBlobResourceContents(blobResourceContents: BlobResourceContents): JsonObject = {
    val base = Map(
      ResourceContents.UriKey -> JsonString(blobResourceContents.uri),
      BlobResourceContents.BlobKey -> JsonString(blobResourceContents.blob)
    )
    JsonObject(
      Fields.withOptional(
        base,
        ResourceContents.MimeTypeKey -> blobResourceContents.mimeType.map(JsonString(_)),
        ResourceContents.MetaKey -> blobResourceContents.meta.map(m => JsonObject(m.value))
      )
    )
  }

  def fromResourceContents(resourceContents: ResourceContents): JsonObject =
    resourceContents match {
      case t: TextResourceContents => fromTextResourceContents(t)
      case b: BlobResourceContents => fromBlobResourceContents(b)
    }

  def fromResourceLink(resourceLink: ResourceLink): JsonObject = {
    val base = Map(
      ContentBlock.TypeKey -> JsonString(resourceLink.contentType),
      ResourceLink.NameKey -> JsonString(resourceLink.name),
      ResourceLink.UriKey -> JsonString(resourceLink.uri)
    )
    JsonObject(
      Fields.withOptional(
        base,
        ResourceLink.TitleKey -> resourceLink.title.map(JsonString(_)),
        ResourceLink.DescriptionKey -> resourceLink.description.map(JsonString(_)),
        ResourceLink.MimeTypeKey -> resourceLink.mimeType.map(JsonString(_)),
        ResourceLink.SizeKey -> resourceLink.size.map(n => JsonNumber(n)),
        ResourceLink.IconsKey -> resourceLink.icons.map(Primitives.fromList(_)(Meta.fromIcon)),
        ContentBlock.AnnotationsKey -> resourceLink.annotations.map(fromAnnotations),
        ContentBlock.MetaKey -> resourceLink.meta.map(m => JsonObject(m.value))
      )
    )
  }

  def fromEmbeddedResource(embeddedResource: EmbeddedResource): JsonObject = {
    val base = Map(
      ContentBlock.TypeKey -> JsonString(embeddedResource.contentType),
      EmbeddedResource.ResourceKey -> fromResourceContents(embeddedResource.resource)
    )
    JsonObject(
      Fields.withOptional(
        base,
        ContentBlock.AnnotationsKey -> embeddedResource.annotations.map(fromAnnotations),
        ContentBlock.MetaKey -> embeddedResource.meta.map(m => JsonObject(m.value))
      )
    )
  }

  def fromContentBlock(contentBlock: ContentBlock): JsonObject =
    contentBlock match {
      case t: TextContent      => fromTextContent(t)
      case i: ImageContent     => fromImageContent(i)
      case a: AudioContent     => fromAudioContent(a)
      case r: ResourceLink     => fromResourceLink(r)
      case e: EmbeddedResource => fromEmbeddedResource(e)
    }

  def fromToolResultContent(toolResultContent: ToolResultContent): JsonObject = {
    val base = Map(
      ContentBlock.TypeKey -> JsonString(toolResultContent.contentType),
      ToolResultContent.ToolUseIdKey -> JsonString(toolResultContent.toolUseId),
      ToolResultContent.ContentKey ->
        Primitives.fromList(toolResultContent.content)(fromContentBlock)
    )
    JsonObject(
      Fields.withOptional(
        base,
        ToolResultContent.StructuredContentKey -> toolResultContent.structuredContent,
        ToolResultContent.IsErrorKey -> toolResultContent.isError.map(Primitives.fromBool),
        ContentBlock.MetaKey -> toolResultContent.meta.map(m => JsonObject(m.value))
      )
    )
  }

  def fromSamplingMessageContentBlock(
      samplingMessageContentBlock: SamplingMessageContentBlock
  ): JsonObject =
    samplingMessageContentBlock match {
      case t: TextContent       => fromTextContent(t)
      case i: ImageContent      => fromImageContent(i)
      case a: AudioContent      => fromAudioContent(a)
      case u: ToolUseContent    => fromToolUseContent(u)
      case r: ToolResultContent => fromToolResultContent(r)
    }

  def fromSamplingMessageContent(
      samplingMessageContent: SamplingMessageContent
  ): JsonValue =
    samplingMessageContent match {
      case SingleSamplingContent(block) => fromSamplingMessageContentBlock(block)
      case MultiSamplingContent(blocks) =>
        Primitives.fromList(blocks)(fromSamplingMessageContentBlock)
    }

  def toRole(role: JsonValue): Either[DecodingError, Role] =
    Primitives.asString(role, "role").flatMap { s =>
      Role.fromValue(s).toRight(DecodingError(s"Invalid role: $s"))
    }

  def toAnnotations(annotations: JsonObject): Either[DecodingError, Annotations] = {
    val fields = annotations.value
    for {
      audience <- Fields.optionalList(fields, Annotations.AudienceKey)(toRole)
      priority <- Fields.optional(fields, Annotations.PriorityKey)(
        Primitives.asDouble(_, Annotations.PriorityKey)
      )
      lastModified <- Fields.optionalString(fields, Annotations.LastModifiedKey)
    } yield Annotations(
      audience = audience,
      priority = priority,
      lastModified = lastModified
    )
  }

  def toTextContent(textContent: JsonObject): Either[DecodingError, TextContent] = {
    val fields = textContent.value
    for {
      text <- Fields.requiredString(fields, TextContent.TextKey)
      annotations <- optionalAnnotations(fields)
      meta <- optionalMeta(fields)
    } yield TextContent(text = text, annotations = annotations, meta = meta)
  }

  def toImageContent(imageContent: JsonObject): Either[DecodingError, ImageContent] = {
    val fields = imageContent.value
    for {
      data <- Fields.requiredString(fields, ImageContent.DataKey)
      mimeType <- Fields.requiredString(fields, ImageContent.MimeTypeKey)
      annotations <- optionalAnnotations(fields)
      meta <- optionalMeta(fields)
    } yield ImageContent(
      data = data,
      mimeType = mimeType,
      annotations = annotations,
      meta = meta
    )
  }

  def toAudioContent(audioContent: JsonObject): Either[DecodingError, AudioContent] = {
    val fields = audioContent.value
    for {
      data <- Fields.requiredString(fields, AudioContent.DataKey)
      mimeType <- Fields.requiredString(fields, AudioContent.MimeTypeKey)
      annotations <- optionalAnnotations(fields)
      meta <- optionalMeta(fields)
    } yield AudioContent(
      data = data,
      mimeType = mimeType,
      annotations = annotations,
      meta = meta
    )
  }

  def toToolUseContent(toolUseContent: JsonObject): Either[DecodingError, ToolUseContent] = {
    val fields = toolUseContent.value
    for {
      id <- Fields.requiredString(fields, ToolUseContent.IdKey)
      name <- Fields.requiredString(fields, ToolUseContent.NameKey)
      input <- Fields.requiredObject(fields, ToolUseContent.InputKey)
      meta <- optionalMeta(fields)
    } yield ToolUseContent(id = id, name = name, input = input, meta = meta)
  }

  def toTextResourceContents(
      textResourceContents: JsonObject
  ): Either[DecodingError, TextResourceContents] = {
    val fields = textResourceContents.value
    for {
      uri <- Fields.requiredString(fields, ResourceContents.UriKey)
      text <- Fields.requiredString(fields, TextResourceContents.TextKey)
      mimeType <- Fields.optionalString(fields, ResourceContents.MimeTypeKey)
      meta <- optionalResourceMeta(fields)
    } yield TextResourceContents(uri = uri, text = text, mimeType = mimeType, meta = meta)
  }

  def toBlobResourceContents(
      blobResourceContents: JsonObject
  ): Either[DecodingError, BlobResourceContents] = {
    val fields = blobResourceContents.value
    for {
      uri <- Fields.requiredString(fields, ResourceContents.UriKey)
      blob <- Fields.requiredString(fields, BlobResourceContents.BlobKey)
      mimeType <- Fields.optionalString(fields, ResourceContents.MimeTypeKey)
      meta <- optionalResourceMeta(fields)
    } yield BlobResourceContents(uri = uri, blob = blob, mimeType = mimeType, meta = meta)
  }

  def toResourceContents(
      resourceContents: JsonObject
  ): Either[DecodingError, ResourceContents] = {
    val fields = resourceContents.value
    if (fields.contains(TextResourceContents.TextKey))
      toTextResourceContents(resourceContents)
    else if (fields.contains(BlobResourceContents.BlobKey))
      toBlobResourceContents(resourceContents)
    else
      Left(
        DecodingError(
          s"Invalid resource contents: expected ${TextResourceContents.TextKey} or ${BlobResourceContents.BlobKey}"
        )
      )
  }

  def toResourceLink(resourceLink: JsonObject): Either[DecodingError, ResourceLink] = {
    val fields = resourceLink.value
    for {
      name <- Fields.requiredString(fields, ResourceLink.NameKey)
      uri <- Fields.requiredString(fields, ResourceLink.UriKey)
      title <- Fields.optionalString(fields, ResourceLink.TitleKey)
      description <- Fields.optionalString(fields, ResourceLink.DescriptionKey)
      mimeType <- Fields.optionalString(fields, ResourceLink.MimeTypeKey)
      size <- Fields.optional(fields, ResourceLink.SizeKey)(
        Primitives.asLong(_, ResourceLink.SizeKey)
      )
      icons <- Fields.optionalList(fields, ResourceLink.IconsKey) { v =>
        Fields.asObject(v, "icon").flatMap(Meta.toIcon)
      }
      annotations <- optionalAnnotations(fields)
      meta <- optionalMeta(fields)
    } yield ResourceLink(
      name = name,
      uri = uri,
      title = title,
      description = description,
      mimeType = mimeType,
      size = size,
      icons = icons,
      annotations = annotations,
      meta = meta
    )
  }

  def toEmbeddedResource(
      embeddedResource: JsonObject
  ): Either[DecodingError, EmbeddedResource] = {
    val fields = embeddedResource.value
    for {
      resource <- Fields
        .requiredObject(fields, EmbeddedResource.ResourceKey)
        .flatMap(toResourceContents)
      annotations <- optionalAnnotations(fields)
      meta <- optionalMeta(fields)
    } yield EmbeddedResource(resource = resource, annotations = annotations, meta = meta)
  }

  def toContentBlock(contentBlock: JsonObject): Either[DecodingError, ContentBlock] =
    Fields.requiredString(contentBlock.value, ContentBlock.TypeKey).flatMap {
      case "text"          => toTextContent(contentBlock)
      case "image"         => toImageContent(contentBlock)
      case "audio"         => toAudioContent(contentBlock)
      case "resource_link" => toResourceLink(contentBlock)
      case "resource"      => toEmbeddedResource(contentBlock)
      case other =>
        Left(DecodingError(s"Invalid ${ContentBlock.TypeKey}: unsupported content block `$other`"))
    }

  def toToolResultContent(
      toolResultContent: JsonObject
  ): Either[DecodingError, ToolResultContent] = {
    val fields = toolResultContent.value
    for {
      toolUseId <- Fields.requiredString(fields, ToolResultContent.ToolUseIdKey)
      content <- Fields
        .required(fields, ToolResultContent.ContentKey)
        .flatMap(
          Primitives.toList(_, ToolResultContent.ContentKey)(v =>
            Fields.asObject(v, ToolResultContent.ContentKey).flatMap(toContentBlock)
          )
        )
      structuredContent <- Fields.optionalValue(fields, ToolResultContent.StructuredContentKey)
      isError <- Fields.optionalBool(fields, ToolResultContent.IsErrorKey)
      meta <- optionalMeta(fields)
    } yield ToolResultContent(
      toolUseId = toolUseId,
      content = content,
      structuredContent = structuredContent,
      isError = isError,
      meta = meta
    )
  }

  def toSamplingMessageContentBlock(
      samplingMessageContentBlock: JsonObject
  ): Either[DecodingError, SamplingMessageContentBlock] =
    Fields.requiredString(samplingMessageContentBlock.value, ContentBlock.TypeKey).flatMap {
      case "text"        => toTextContent(samplingMessageContentBlock)
      case "image"       => toImageContent(samplingMessageContentBlock)
      case "audio"       => toAudioContent(samplingMessageContentBlock)
      case "tool_use"    => toToolUseContent(samplingMessageContentBlock)
      case "tool_result" => toToolResultContent(samplingMessageContentBlock)
      case other =>
        Left(
          DecodingError(
            s"Invalid ${ContentBlock.TypeKey}: unsupported sampling content block `$other`"
          )
        )
    }

  def toSamplingMessageContent(
      samplingMessageContent: JsonValue
  ): Either[DecodingError, SamplingMessageContent] =
    samplingMessageContent match {
      case o: JsonObject =>
        toSamplingMessageContentBlock(o).map(SingleSamplingContent(_))
      case a: JsonArray =>
        Primitives
          .toList(a, CreateMessageResult.ContentKey)(v =>
            Fields
              .asObject(v, CreateMessageResult.ContentKey)
              .flatMap(toSamplingMessageContentBlock)
          )
          .map(MultiSamplingContent(_))
      case _ =>
        Left(
          DecodingError(
            s"Invalid ${CreateMessageResult.ContentKey}: expected object or array"
          )
        )
    }

  private def optionalAnnotations(
      fields: Map[String, JsonValue]
  ): Either[DecodingError, Option[Annotations]] =
    Fields.optional(fields, ContentBlock.AnnotationsKey)(v =>
      Fields.asObject(v, ContentBlock.AnnotationsKey).flatMap(toAnnotations)
    )

  private def optionalMeta(
      fields: Map[String, JsonValue]
  ): Either[DecodingError, Option[MetaObject]] =
    Fields.optional(fields, ContentBlock.MetaKey)(v =>
      Fields.asObject(v, ContentBlock.MetaKey).map(obj => MetaObject(obj.value))
    )

  private def optionalResourceMeta(
      fields: Map[String, JsonValue]
  ): Either[DecodingError, Option[MetaObject]] =
    Fields.optional(fields, ResourceContents.MetaKey)(v =>
      Fields.asObject(v, ResourceContents.MetaKey).map(obj => MetaObject(obj.value))
    )
}
