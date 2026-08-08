package io.github.taza67.mcp.protocol.mcp.prompts

import io.github.taza67.mcp.protocol.jsonrpc.{JsonRpcVersion, JsonRpcVersion20, Method, RequestId}
import io.github.taza67.mcp.protocol.mcp._



/** Method names for the prompts domain. */
object Prompts {
  val list: Method = Method("prompts/list")
  val get: Method = Method("prompts/get")
  val listChangedNotification: Method = Method("notifications/prompts/list_changed")
}

/** Describes an argument that a [[Prompt]] can accept.
 *
 *  @param name Programmatic argument name.
 *  @param title Human-readable title for UI contexts.
 *  @param description Hint describing the argument.
 *  @param required Whether the client must supply this argument.
 */
case class PromptArgument(
    name: String,
    title: Option[String] = None,
    description: Option[String] = None,
    required: Option[Boolean] = None
)

object PromptArgument {

  /** Wire key for the programmatic argument name. */
  val NameKey: String = "name"

  /** Wire key for optional human-readable title. */
  val TitleKey: String = "title"

  /** Wire key for optional description. */
  val DescriptionKey: String = "description"

  /** Wire key for whether the argument is required. */
  val RequiredKey: String = "required"
}

/** A prompt or prompt template that the server offers.
 *
 *  @param name Programmatic name; also display fallback when `title` is absent.
 *  @param title Human-readable title for UI contexts.
 *  @param description Hint for clients/LLMs about what the prompt does.
 *  @param arguments Arguments the prompt accepts, if any.
 *  @param icons Optional UI icons.
 *  @param meta Optional open metadata.
 */
case class Prompt(
    name: String,
    title: Option[String] = None,
    description: Option[String] = None,
    arguments: Option[List[PromptArgument]] = None,
    icons: Option[List[Icon]] = None,
    meta: Option[MetaObject] = None
)

object Prompt {

  /** Wire key for the programmatic prompt name. */
  val NameKey: String = "name"

  /** Wire key for optional human-readable title. */
  val TitleKey: String = "title"

  /** Wire key for optional description. */
  val DescriptionKey: String = "description"

  /** Wire key for optional accepted arguments. */
  val ArgumentsKey: String = "arguments"

  /** Wire key for optional UI icons. */
  val IconsKey: String = "icons"

  /** Wire key for optional open metadata. */
  val MetaKey: String = RequestParams.MetaKey
}

/** A message returned as part of a prompt.
 *
 *  Similar to a sampling message, but also supports embedding resources from
 *  the MCP server via [[ContentBlock]].
 *
 *  @param role Sender role for this message.
 *  @param content Content block for the message body.
 */
case class PromptMessage(
    role: Role,
    content: ContentBlock
)

object PromptMessage {

  /** Wire key for the message role. */
  val RoleKey: String = "role"

  /** Wire key for the message content block. */
  val ContentKey: String = "content"
}

/** Sent from the client to list prompts the server offers (paginated). */
case class ListPromptsRequest(
    id: RequestId,
    params: PaginatedRequestParams,
    jsonrpc: JsonRpcVersion = JsonRpcVersion20
)

/** Result of a `prompts/list` request.
 *
 *  @param prompts Prompts currently offered by the server.
 *  @param ttlMs Cache freshness hint in milliseconds (like Cache-Control `max-age`).
 *  @param cacheScope Whether the cached response may be shared across auth contexts.
 *  @param nextCursor Pagination token for the next page, if more results exist.
 *  @param resultType Normally [[CompleteResultType]].
 *  @param meta Optional result metadata.
 */
case class ListPromptsResult(
    prompts: List[Prompt],
    ttlMs: Long,
    cacheScope: CacheScope,
    nextCursor: Option[Cursor] = None,
    resultType: ResultType = CompleteResultType,
    meta: Option[ResultMeta] = None
)

object ListPromptsResult {

  /** Wire key for the prompts array. */
  val PromptsKey: String = "prompts"
}

/** Successful JSON-RPC response to a `prompts/list` request. */
case class ListPromptsResultResponse(
    result: ListPromptsResult,
    id: RequestId,
    jsonrpc: JsonRpcVersion = JsonRpcVersion20
)

/** Parameters for a `prompts/get` request.
 *
 *  @param meta Required request metadata.
 *  @param name Prompt name to retrieve.
 *  @param arguments String-valued arguments for the prompt template.
 *  @param inputResponses Client answers to a prior [[InputRequiredResult]].
 *  @param requestState Opaque state echoed from a prior [[InputRequiredResult]].
 */
case class GetPromptRequestParams(
    meta: RequestMeta,
    name: String,
    arguments: Option[Map[String, String]] = None,
    inputResponses: Option[InputResponses] = None,
    requestState: Option[String] = None
)

object GetPromptRequestParams {

  /** Wire key for the prompt name inside JSON-RPC `params`. */
  val NameKey: String = "name"

  /** Wire key for string-valued prompt arguments inside JSON-RPC `params`. */
  val ArgumentsKey: String = "arguments"
}

/** Used by the client to get a prompt provided by the server. */
case class GetPromptRequest(
    id: RequestId,
    params: GetPromptRequestParams,
    jsonrpc: JsonRpcVersion = JsonRpcVersion20
)

/** Result of a completed `prompts/get` request.
 *
 *  @param messages Prompt messages to present to the model / user.
 *  @param description Optional description of the retrieved prompt.
 *  @param resultType Normally [[CompleteResultType]].
 *  @param meta Optional result metadata.
 */
case class GetPromptResult(
    messages: List[PromptMessage],
    description: Option[String] = None,
    resultType: ResultType = CompleteResultType,
    meta: Option[ResultMeta] = None
)

object GetPromptResult {

  /** Wire key for prompt messages. */
  val MessagesKey: String = "messages"

  /** Wire key for optional prompt description. */
  val DescriptionKey: String = "description"
}

/** Successful JSON-RPC response to a `prompts/get` request. */
case class GetPromptResultResponse(
    result: RequestOutcome[GetPromptResult],
    id: RequestId,
    jsonrpc: JsonRpcVersion = JsonRpcVersion20
)

/** Optional notification that the server's prompt list changed.
 *
 *  Delivered only on a `subscriptions/listen` stream when the client opted in via
 *  `promptsListChanged`.
 */
case class PromptListChangedNotification(
    params: Option[NotificationParams] = None,
    jsonrpc: JsonRpcVersion = JsonRpcVersion20
)
