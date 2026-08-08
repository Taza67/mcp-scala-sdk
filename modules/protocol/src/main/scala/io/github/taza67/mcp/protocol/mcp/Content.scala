package io.github.taza67.mcp.protocol.mcp

import io.github.taza67.mcp.protocol.json.{JsonObject, JsonValue}



/** Contents of a resource, either textual or binary.
 *
 *  Used inside [[EmbeddedResource]] and resource-read results.
 */
sealed trait ResourceContents {
  def uri: String
  def mimeType: Option[String]
  def meta: Option[MetaObject]
}

object ResourceContents {

  /** Wire key for the resource URI. */
  val UriKey: String = "uri"

  /** Wire key for optional MIME type. */
  val MimeTypeKey: String = "mimeType"

  /** Wire key for optional open metadata. */
  val MetaKey: String = RequestParams.MetaKey
}

/** Textual resource contents.
 *
 *  `text` MUST only be set when the item can actually be represented as text
 *  (not binary data).
 *
 *  @param uri URI of this resource.
 *  @param text Text representation of the resource.
 *  @param mimeType MIME type if known.
 *  @param meta Optional open metadata.
 */
case class TextResourceContents(
    uri: String,
    text: String,
    mimeType: Option[String] = None,
    meta: Option[MetaObject] = None
) extends ResourceContents

object TextResourceContents {

  /** Wire key for textual payload. */
  val TextKey: String = "text"
}

/** Binary resource contents.
 *
 *  @param uri URI of this resource.
 *  @param blob Base64-encoded binary data.
 *  @param mimeType MIME type if known.
 *  @param meta Optional open metadata.
 */
case class BlobResourceContents(
    uri: String,
    blob: String,
    mimeType: Option[String] = None,
    meta: Option[MetaObject] = None
) extends ResourceContents

object BlobResourceContents {

  /** Wire key for base64 binary payload. */
  val BlobKey: String = "blob"
}

/** Content block used in prompts and tool-call results.
 *
 *  One of [[TextContent]], [[ImageContent]], [[AudioContent]], [[ResourceLink]],
 *  or [[EmbeddedResource]].
 */
sealed trait ContentBlock {
  def contentType: String
  def annotations: Option[Annotations]
  def meta: Option[MetaObject]
}

object ContentBlock {

  /** Wire key for the content-block discriminant. */
  val TypeKey: String = "type"

  /** Wire key for optional display / usage annotations. */
  val AnnotationsKey: String = "annotations"

  /** Wire key for optional open metadata. */
  val MetaKey: String = RequestParams.MetaKey
}

/** Content block allowed in a sampling message.
 *
 *  One of [[TextContent]], [[ImageContent]], [[AudioContent]], [[ToolUseContent]],
 *  or [[ToolResultContent]].
 *
 *  @deprecated Deprecated as of protocol version 2026-07-28 (SEP-2577). Remains for at least twelve months.
 */
sealed trait SamplingMessageContentBlock {
  def contentType: String
  def meta: Option[MetaObject]
}

/** Text provided to or from an LLM.
 *
 *  @param text Text content of the message.
 *  @param annotations Optional display / usage annotations for the client.
 *  @param meta Optional open metadata.
 */
case class TextContent(
    text: String,
    annotations: Option[Annotations] = None,
    meta: Option[MetaObject] = None
) extends ContentBlock
    with SamplingMessageContentBlock {
  val contentType: String = "text"
}

object TextContent {

  /** Wire key for the text payload. */
  val TextKey: String = "text"
}

/** Image provided to or from an LLM.
 *
 *  @param data Base64-encoded image data.
 *  @param mimeType MIME type of the image (providers may support different types).
 *  @param annotations Optional display / usage annotations for the client.
 *  @param meta Optional open metadata.
 */
case class ImageContent(
    data: String,
    mimeType: String,
    annotations: Option[Annotations] = None,
    meta: Option[MetaObject] = None
) extends ContentBlock
    with SamplingMessageContentBlock {
  val contentType: String = "image"
}

object ImageContent {

  /** Wire key for base64 image data. */
  val DataKey: String = "data"

  /** Wire key for image MIME type. */
  val MimeTypeKey: String = "mimeType"
}

/** Audio provided to or from an LLM.
 *
 *  @param data Base64-encoded audio data.
 *  @param mimeType MIME type of the audio (providers may support different types).
 *  @param annotations Optional display / usage annotations for the client.
 *  @param meta Optional open metadata.
 */
case class AudioContent(
    data: String,
    mimeType: String,
    annotations: Option[Annotations] = None,
    meta: Option[MetaObject] = None
) extends ContentBlock
    with SamplingMessageContentBlock {
  val contentType: String = "audio"
}

object AudioContent {

  /** Wire key for base64 audio data. */
  val DataKey: String = "data"

  /** Wire key for audio MIME type. */
  val MimeTypeKey: String = "mimeType"
}

/** A resource the server can read, included in a prompt or tool-call result.
 *
 *  Resource links returned by tools are '''not''' guaranteed to appear in
 *  `resources/list` results.
 *
 *  @param name Programmatic name; also display fallback when `title` is absent.
 *  @param uri URI of this resource.
 *  @param title Human-readable title for UI contexts.
 *  @param description Hint for clients/LLMs about what the resource represents.
 *  @param mimeType MIME type if known.
 *  @param size Raw content size in bytes (before base64), if known.
 *  @param icons Optional UI icons.
 *  @param annotations Optional display / usage annotations for the client.
 *  @param meta Optional open metadata.
 */
case class ResourceLink(
    name: String,
    uri: String,
    title: Option[String] = None,
    description: Option[String] = None,
    mimeType: Option[String] = None,
    size: Option[Long] = None,
    icons: Option[List[Icon]] = None,
    annotations: Option[Annotations] = None,
    meta: Option[MetaObject] = None
) extends ContentBlock {
  val contentType: String = "resource_link"
}

object ResourceLink {

  /** Wire key for programmatic name. */
  val NameKey: String = "name"

  /** Wire key for resource URI. */
  val UriKey: String = "uri"

  /** Wire key for optional human-readable title. */
  val TitleKey: String = "title"

  /** Wire key for optional description. */
  val DescriptionKey: String = "description"

  /** Wire key for optional MIME type. */
  val MimeTypeKey: String = "mimeType"

  /** Wire key for optional raw size in bytes. */
  val SizeKey: String = "size"

  /** Wire key for optional UI icons. */
  val IconsKey: String = "icons"
}

/** Resource contents embedded into a prompt or tool-call result.
 *
 *  It is up to the client how best to render embedded resources for the LLM
 *  and/or the user.
 *
 *  @param resource Text or binary resource payload.
 *  @param annotations Optional display / usage annotations for the client.
 *  @param meta Optional open metadata.
 */
case class EmbeddedResource(
    resource: ResourceContents,
    annotations: Option[Annotations] = None,
    meta: Option[MetaObject] = None
) extends ContentBlock {
  val contentType: String = "resource"
}

object EmbeddedResource {

  /** Wire key for nested resource contents. */
  val ResourceKey: String = "resource"
}

/** A request from the assistant to call a tool (sampling).
 *
 *  @param id Unique id used to match a later [[ToolResultContent]].
 *  @param name Tool name to call.
 *  @param input Arguments conforming to the tool's input schema.
 *  @param meta Optional metadata; clients SHOULD preserve it across sampling rounds.
 *
 *  @deprecated Deprecated as of protocol version 2026-07-28 (SEP-2577). Remains for at least twelve months.
 */
case class ToolUseContent(
    id: String,
    name: String,
    input: JsonObject,
    meta: Option[MetaObject] = None
) extends SamplingMessageContentBlock {
  val contentType: String = "tool_use"
}

object ToolUseContent {

  /** Wire key for the tool-use id. */
  val IdKey: String = "id"

  /** Wire key for the tool name. */
  val NameKey: String = "name"

  /** Wire key for tool input arguments. */
  val InputKey: String = "input"
}

/** Result of a tool use, provided back to the assistant (sampling).
 *
 *  @param toolUseId Must match a prior [[ToolUseContent.id]].
 *  @param content Unstructured result blocks (same shape as tool-call results).
 *  @param structuredContent Optional structured JSON value (SHOULD match `outputSchema` when set).
 *  @param isError Whether the tool use failed; default false when absent.
 *  @param meta Optional metadata; clients SHOULD preserve it across sampling rounds.
 *
 *  @deprecated Deprecated as of protocol version 2026-07-28 (SEP-2577). Remains for at least twelve months.
 */
case class ToolResultContent(
    toolUseId: String,
    content: List[ContentBlock],
    structuredContent: Option[JsonValue] = None,
    isError: Option[Boolean] = None,
    meta: Option[MetaObject] = None
) extends SamplingMessageContentBlock {
  val contentType: String = "tool_result"
}

object ToolResultContent {

  /** Wire key matching a prior [[ToolUseContent.id]]. */
  val ToolUseIdKey: String = "toolUseId"

  /** Wire key for unstructured result blocks. */
  val ContentKey: String = "content"

  /** Wire key for optional structured JSON result. */
  val StructuredContentKey: String = "structuredContent"

  /** Wire key for optional error flag. */
  val IsErrorKey: String = "isError"
}
