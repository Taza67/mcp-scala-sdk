package io.github.taza67.mcp.protocol.mcp.tools

import io.github.taza67.mcp.protocol.json.{JsonObject, JsonValue}
import io.github.taza67.mcp.protocol.jsonrpc.{JsonRpcVersion, JsonRpcVersion20, Method, RequestId}
import io.github.taza67.mcp.protocol.mcp._



/** Method names for the tools domain. */
object Tools {
  val list: Method = Method("tools/list")
  val call: Method = Method("tools/call")
  val listChangedNotification: Method = Method("notifications/tools/list_changed")
}

/** Hints describing a [[Tool]] to clients.
 *
 *  All properties are '''hints''' only — they are not guaranteed to describe tool
 *  behavior faithfully. Clients should never make tool-use decisions based on
 *  annotations from untrusted servers.
 *
 *  @param title Human-readable title; display precedence is `title`, then
 *               `annotations.title`, then `name`.
 *  @param readOnlyHint If true, the tool does not modify its environment.
 *  @param destructiveHint If true, the tool may perform destructive updates
 *                         (relevant when `readOnlyHint` is false).
 *  @param idempotentHint If true, repeated calls with the same args have no
 *                        additional effect (relevant when `readOnlyHint` is false).
 *  @param openWorldHint If true, the tool may interact with an open world of
 *                       external entities (vs a closed domain).
 */
case class ToolAnnotations(
    title: Option[String] = None,
    readOnlyHint: Option[Boolean] = None,
    destructiveHint: Option[Boolean] = None,
    idempotentHint: Option[Boolean] = None,
    openWorldHint: Option[Boolean] = None
)

object ToolAnnotations {

  /** Wire key for optional display title hint. */
  val TitleKey: String = "title"

  /** Wire key for read-only hint. */
  val ReadOnlyHintKey: String = "readOnlyHint"

  /** Wire key for destructive-update hint. */
  val DestructiveHintKey: String = "destructiveHint"

  /** Wire key for idempotent-call hint. */
  val IdempotentHintKey: String = "idempotentHint"

  /** Wire key for open-world hint. */
  val OpenWorldHintKey: String = "openWorldHint"
}

/** JSON Schema for tool input: root `type` is always `"object"`.
 *
 *  Any JSON Schema 2020-12 keyword may appear in [[fields]] (`properties`,
 *  `required`, composition keywords, …).
 *
 *  @param fields Schema keywords other than `type` / `$schema`.
 *  @param schema Optional `$schema` URI; defaults to JSON Schema 2020-12 when absent.
 */
case class ToolInputSchema(
    fields: JsonObject = JsonObject(Map.empty),
    schema: Option[String] = None
)

object ToolInputSchema {

  /** Wire key for schema `type`. */
  val TypeKey: String = "type"

  /** Wire `type` value for tool input schemas. */
  val TypeValue: String = "object"

  /** Wire key for optional `$schema` URI. */
  val SchemaKey: String = "$schema"
}

/** Optional JSON Schema describing [[CallToolResult.structuredContent]].
 *
 *  @param fields Schema body (any JSON Schema 2020-12 keywords).
 *  @param schema Optional `$schema` URI; defaults to JSON Schema 2020-12 when absent.
 */
case class ToolOutputSchema(
    fields: JsonObject = JsonObject(Map.empty),
    schema: Option[String] = None
)

object ToolOutputSchema {

  /** Wire key for optional `$schema` URI. */
  val SchemaKey: String = "$schema"
}

/** Definition of a tool the client can call.
 *
 *  @param name Programmatic name; also display fallback when titles are absent.
 *  @param inputSchema JSON Schema for tool arguments (object-shaped).
 *  @param title Human-readable title for UI contexts.
 *  @param description Hint for clients/LLMs about what the tool does.
 *  @param outputSchema Optional schema for structured tool output.
 *  @param annotations Optional behavioral / display hints (not authoritative).
 *  @param icons Optional UI icons.
 *  @param meta Optional open metadata.
 */
case class Tool(
    name: String,
    inputSchema: ToolInputSchema,
    title: Option[String] = None,
    description: Option[String] = None,
    outputSchema: Option[ToolOutputSchema] = None,
    annotations: Option[ToolAnnotations] = None,
    icons: Option[List[Icon]] = None,
    meta: Option[MetaObject] = None
)

object Tool {

  /** Wire key for the programmatic tool name. */
  val NameKey: String = "name"

  /** Wire key for the input JSON Schema. */
  val InputSchemaKey: String = "inputSchema"

  /** Wire key for optional human-readable title. */
  val TitleKey: String = "title"

  /** Wire key for optional description. */
  val DescriptionKey: String = "description"

  /** Wire key for optional output JSON Schema. */
  val OutputSchemaKey: String = "outputSchema"

  /** Wire key for optional annotations. */
  val AnnotationsKey: String = "annotations"

  /** Wire key for optional UI icons. */
  val IconsKey: String = "icons"

  /** Wire key for optional open metadata. */
  val MetaKey: String = RequestParams.MetaKey
}

/** Parameters for a `tools/call` request.
 *
 *  Extends the common input-response continuation fields used after
 *  [[InputRequiredResult]].
 *
 *  @param meta Required request metadata.
 *  @param name Tool name to invoke.
 *  @param arguments Object-shaped arguments for the tool.
 *  @param inputResponses Client answers to a prior [[InputRequiredResult]].
 *  @param requestState Opaque state echoed from a prior [[InputRequiredResult]].
 */
case class CallToolRequestParams(
    meta: RequestMeta,
    name: String,
    arguments: Option[JsonObject] = None,
    inputResponses: Option[InputResponses] = None,
    requestState: Option[String] = None
)

object CallToolRequestParams {

  /** Wire key for the tool name inside JSON-RPC `params`. */
  val NameKey: String = "name"

  /** Wire key for tool arguments inside JSON-RPC `params`. */
  val ArgumentsKey: String = "arguments"
}

/** Used by the client to invoke a tool provided by the server. */
case class CallToolRequest(
    id: RequestId,
    params: CallToolRequestParams,
    jsonrpc: JsonRpcVersion = JsonRpcVersion20
)

/** Successful unstructured/structured outcome of a tool call.
 *
 *  Tool-originated failures SHOULD appear here with [[isError]] = `true`, not as
 *  a protocol-level JSON-RPC error — otherwise the LLM cannot see the failure.
 *
 *  @param content Unstructured content blocks representing the tool output.
 *  @param structuredContent Optional JSON value conforming to the tool's
 *                           [[Tool.outputSchema]] when one is defined.
 *  @param isError Whether the tool call ended in an error (default false).
 *  @param resultType Normally [[CompleteResultType]].
 *  @param meta Optional result metadata.
 */
case class CallToolResult(
    content: List[ContentBlock],
    structuredContent: Option[JsonValue] = None,
    isError: Option[Boolean] = None,
    resultType: ResultType = CompleteResultType,
    meta: Option[ResultMeta] = None
)

object CallToolResult {

  /** Wire key for unstructured tool output blocks. */
  val ContentKey: String = "content"

  /** Wire key for optional structured JSON output. */
  val StructuredContentKey: String = "structuredContent"

  /** Wire key for optional tool-originated error flag. */
  val IsErrorKey: String = "isError"
}

/** Successful JSON-RPC response to a `tools/call` request. */
case class CallToolResultResponse(
    result: RequestOutcome[CallToolResult],
    id: RequestId,
    jsonrpc: JsonRpcVersion = JsonRpcVersion20
)

/** Sent from the client to list tools the server offers (paginated). */
case class ListToolsRequest(
    id: RequestId,
    params: PaginatedRequestParams,
    jsonrpc: JsonRpcVersion = JsonRpcVersion20
)

/** Result of a `tools/list` request.
 *
 *  @param tools Tools currently offered by the server.
 *  @param ttlMs Cache freshness hint in milliseconds (like Cache-Control `max-age`).
 *  @param cacheScope Whether the cached response may be shared across auth contexts.
 *  @param nextCursor Pagination token for the next page, if more results exist.
 *  @param resultType Normally [[CompleteResultType]].
 *  @param meta Optional result metadata.
 */
case class ListToolsResult(
    tools: List[Tool],
    ttlMs: Long,
    cacheScope: CacheScope,
    nextCursor: Option[Cursor] = None,
    resultType: ResultType = CompleteResultType,
    meta: Option[ResultMeta] = None
)

object ListToolsResult {

  /** Wire key for the tools array. */
  val ToolsKey: String = "tools"
}

/** Successful JSON-RPC response to a `tools/list` request. */
case class ListToolsResultResponse(
    result: ListToolsResult,
    id: RequestId,
    jsonrpc: JsonRpcVersion = JsonRpcVersion20
)

/** Optional notification that the server's tool list changed.
 *
 *  Delivered only on a `subscriptions/listen` stream when the client opted in via
 *  `toolsListChanged`.
 */
case class ToolListChangedNotification(
    params: Option[NotificationParams] = None,
    jsonrpc: JsonRpcVersion = JsonRpcVersion20
)
