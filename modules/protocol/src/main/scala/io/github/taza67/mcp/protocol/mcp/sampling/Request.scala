package io.github.taza67.mcp.protocol.mcp.sampling

import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.jsonrpc.{JsonRpcVersion, JsonRpcVersion20, Method, RequestId}
import io.github.taza67.mcp.protocol.mcp.{MetaObject, Role}
import io.github.taza67.mcp.protocol.mcp.tools.Tool



/** Method name for LLM sampling: `"sampling/createMessage"`.
 *
 *  @deprecated Deprecated as of protocol version 2026-07-28 (SEP-2577). Remains for at least twelve months.
 */
object Sampling {
  val createMessage: Method = Method("sampling/createMessage")
}

/** Parameters for a `sampling/createMessage` request.
 *
 *  Direction is server → client. The client has full discretion over model
 *  selection and SHOULD inform the user before sampling (human in the loop).
 *
 *  @param messages Conversation messages to sample from.
 *  @param maxTokens Requested maximum tokens to sample (client MAY sample fewer).
 *  @param modelPreferences Advisory model-selection preferences.
 *  @param systemPrompt Optional system prompt (client MAY modify or omit).
 *  @param includeContext Request to attach MCP server context; default none.
 *  @param temperature Optional sampling temperature.
 *  @param stopSequences Optional stop sequences.
 *  @param metadata Provider-specific metadata passed through to the LLM.
 *  @param tools Tools the model may use (requires `ClientCapabilities.sampling.tools`).
 *  @param toolChoice Tool-use policy (requires `ClientCapabilities.sampling.tools`).
 */
case class CreateMessageRequestParams(
    messages: List[SamplingMessage],
    maxTokens: Long,
    modelPreferences: Option[ModelPreferences] = None,
    systemPrompt: Option[String] = None,
    includeContext: Option[IncludeContext] = None,
    temperature: Option[Double] = None,
    stopSequences: Option[List[String]] = None,
    metadata: Option[JsonObject] = None,
    tools: Option[List[Tool]] = None,
    toolChoice: Option[ToolChoice] = None
)

/** Server→client request to sample an LLM via the client.
 *
 *  @deprecated Deprecated as of protocol version 2026-07-28 (SEP-2577). Remains for at least twelve months.
 */
case class CreateMessageRequest(
    id: RequestId,
    params: CreateMessageRequestParams,
    jsonrpc: JsonRpcVersion = JsonRpcVersion20
) {
  def method: Method = Sampling.createMessage
}

/** Client response to a [[CreateMessageRequest]].
 *
 *  The client SHOULD inform the user before returning the sampled message.
 *
 *  @param model Name of the model that generated the message.
 *  @param role Role of the sampled message (typically assistant).
 *  @param content One block or a list of blocks.
 *  @param stopReason Why sampling stopped, if known (`endTurn`, `stopSequence`,
 *                    `maxTokens`, `toolUse`, or a provider-specific string).
 *  @param meta Optional open metadata.
 *
 *  @deprecated Deprecated as of protocol version 2026-07-28 (SEP-2577). Remains for at least twelve months.
 */
case class CreateMessageResult(
    model: String,
    role: Role,
    content: SamplingMessageContent,
    stopReason: Option[String] = None,
    meta: Option[MetaObject] = None
)
