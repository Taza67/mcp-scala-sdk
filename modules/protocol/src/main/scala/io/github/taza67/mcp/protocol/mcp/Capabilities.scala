package io.github.taza67.mcp.protocol.mcp

import io.github.taza67.mcp.protocol.json.JsonObject



/** Client support for sampling from an LLM.
 *
 *  @param context Whether the client supports context inclusion via `includeContext`.
 *  @param tools Whether the client supports tool use via `tools` / `toolChoice`.
 *  @deprecated Deprecated as of protocol version 2026-07-28 (SEP-2577). Remains for at least twelve months.
 */
case class SamplingCapability(
    context: Option[JsonObject] = None,
    tools: Option[JsonObject] = None
)

/** Client support for elicitation from the server.
 *
 *  @param form Form-mode elicitation support.
 *  @param url URL-mode elicitation support.
 */
case class ElicitationCapability(
    form: Option[JsonObject] = None,
    url: Option[JsonObject] = None
)

/** Capabilities a client may declare for a given request.
 *
 *  Known capabilities are listed here, but the set is open: clients may advertise more.
 *
 *  @param experimental Experimental, non-standard capabilities.
 *  @param roots Present if the client supports listing roots (deprecated as of 2026-07-28).
 *  @param sampling Present if the client supports LLM sampling (deprecated as of 2026-07-28).
 *  @param elicitation Present if the client supports elicitation from the server.
 *  @param extensions Optional MCP extensions (keys MUST be prefixed `_meta`-style names).
 */
case class ClientCapabilities(
    experimental: Option[Map[String, JsonObject]] = None,
    roots: Option[JsonObject] = None,
    sampling: Option[SamplingCapability] = None,
    elicitation: Option[ElicitationCapability] = None,
    extensions: Option[Map[String, JsonObject]] = None
)

object ClientCapabilities {

  /** Top-level wire keys with dedicated fields (including nested `extensions`). */
  val KnownKeys: Set[String] =
    Set("experimental", "roots", "sampling", "elicitation", "extensions")
}

/** Present if the server offers prompt templates.
 *
 *  @param listChanged Whether the server sends prompt-list change notifications.
 */
case class PromptsCapability(
    listChanged: Option[Boolean] = None
)

/** Present if the server offers resources to read.
 *
 *  @param subscribe Whether the server supports resource-update subscriptions.
 *  @param listChanged Whether the server sends resource-list change notifications.
 */
case class ResourcesCapability(
    subscribe: Option[Boolean] = None,
    listChanged: Option[Boolean] = None
)

/** Present if the server offers tools to call.
 *
 *  @param listChanged Whether the server sends tool-list change notifications.
 */
case class ToolsCapability(
    listChanged: Option[Boolean] = None
)

/** Capabilities a server may advertise (e.g. via `server/discover`).
 *
 *  Known capabilities are listed here, but the set is open: servers may advertise more.
 *
 *  @param experimental Experimental, non-standard capabilities.
 *  @param logging Present if the server can send log messages (deprecated as of 2026-07-28).
 *  @param completions Present if the server supports argument autocompletion.
 *  @param prompts Present if the server offers prompt templates.
 *  @param resources Present if the server offers resources to read.
 *  @param tools Present if the server offers tools to call.
 *  @param extensions Optional MCP extensions (keys MUST be prefixed `_meta`-style names).
 */
case class ServerCapabilities(
    experimental: Option[Map[String, JsonObject]] = None,
    logging: Option[JsonObject] = None,
    completions: Option[JsonObject] = None,
    prompts: Option[PromptsCapability] = None,
    resources: Option[ResourcesCapability] = None,
    tools: Option[ToolsCapability] = None,
    extensions: Option[Map[String, JsonObject]] = None
)
