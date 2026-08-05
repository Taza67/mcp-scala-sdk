package io.github.taza67.mcp.protocol.mcp.sampling

/** Hint for model selection during sampling.
 *
 *  Keys beyond [[name]] are unspecified; clients interpret them as they wish.
 *
 *  @param name Substring hint for a model name (e.g. `"claude-3-sonnet"`, `"sonnet"`).
 */
case class ModelHint(
    name: Option[String] = None
)

/** Advisory preferences for which model the client should pick when sampling.
 *
 *  Clients MAY ignore these. When multiple [[hints]] are given, clients MUST
 *  evaluate them in order (first match wins) and SHOULD prefer hints over the
 *  numeric priorities.
 *
 *  Priority fields are in `[0, 1]`: `0` = unimportant, `1` = most important.
 *
 *  @param hints Ordered model-name hints.
 *  @param costPriority How much to prioritize cost.
 *  @param speedPriority How much to prioritize latency.
 *  @param intelligencePriority How much to prioritize capability.
 */
case class ModelPreferences(
    hints: Option[List[ModelHint]] = None,
    costPriority: Option[Double] = None,
    speedPriority: Option[Double] = None,
    intelligencePriority: Option[Double] = None
)

/** Whether to attach MCP server context to the sampling prompt.
 *
 *  Default when absent is [[IncludeContextNone]]. Values other than none are
 *  deprecated (SEP-2596); servers SHOULD omit the field or use none unless the
 *  client declared `ClientCapabilities.sampling.context`.
 */
sealed trait IncludeContext {
  def value: String
}

/** Do not include MCP server context (default). */
case object IncludeContextNone extends IncludeContext { val value = "none" }

/** Include context from the calling server only.
 *
 *  @deprecated Deprecated as of protocol version 2025-11-25 (SEP-2596).
 */
case object IncludeContextThisServer extends IncludeContext { val value = "thisServer" }

/** Include context from all MCP servers.
 *
 *  @deprecated Deprecated as of protocol version 2025-11-25 (SEP-2596).
 */
case object IncludeContextAllServers extends IncludeContext { val value = "allServers" }

/** Controls how the model may use tools during sampling.
 *
 *  Default when `mode` is absent is [[ToolChoiceAuto]].
 *
 *  @param mode Selection mode; defaults to auto when absent.
 */
case class ToolChoice(
    mode: Option[ToolChoiceMode] = Some(ToolChoiceAuto)
)

/** Tool-selection mode for [[ToolChoice]]. */
sealed trait ToolChoiceMode {
  def value: String
}

/** Model decides whether to use tools (default). */
case object ToolChoiceAuto extends ToolChoiceMode { val value = "auto" }

/** Model MUST use at least one tool before completing. */
case object ToolChoiceRequired extends ToolChoiceMode { val value = "required" }

/** Model MUST NOT use any tools. */
case object ToolChoiceNone extends ToolChoiceMode { val value = "none" }
