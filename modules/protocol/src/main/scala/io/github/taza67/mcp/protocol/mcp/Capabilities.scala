package io.github.taza67.mcp.protocol.mcp

import io.github.taza67.mcp.protocol.json.JsonObject



/** The severity of a log message (syslog / RFC-5424).
 *
 *  Deprecated as of protocol version 2026-07-28 (SEP-2577); remains for at least twelve months.
 */
sealed trait LoggingLevel {
  def value: String
}

case object DebugLoggingLevel extends LoggingLevel { val value = "debug" }
case object InfoLoggingLevel extends LoggingLevel { val value = "info" }
case object NoticeLoggingLevel extends LoggingLevel { val value = "notice" }
case object WarningLoggingLevel extends LoggingLevel { val value = "warning" }
case object ErrorLoggingLevel extends LoggingLevel { val value = "error" }
case object CriticalLoggingLevel extends LoggingLevel { val value = "critical" }
case object AlertLoggingLevel extends LoggingLevel { val value = "alert" }
case object EmergencyLoggingLevel extends LoggingLevel { val value = "emergency" }

/** Describes the MCP implementation (SPECS Implementation). */
case class Implementation(
    name: String,
    version: String,
    title: Option[String] = None,
    description: Option[String] = None,
    websiteUrl: Option[String] = None,
    icons: Option[List[Icon]] = None
)

/** Present if the client supports sampling from an LLM.
 *
 *  Deprecated as of protocol version 2026-07-28 (SEP-2577).
 */
case class SamplingCapability(
    context: Option[JsonObject] = None,
    tools: Option[JsonObject] = None
)

/** Present if the client supports elicitation from the server. */
case class ElicitationCapability(
    form: Option[JsonObject] = None,
    url: Option[JsonObject] = None
)

/** Capabilities a client may support for a given request (SPECS ClientCapabilities). */
case class ClientCapabilities(
    experimental: Option[Map[String, JsonObject]] = None,
    /** Present if the client supports listing roots. Deprecated as of 2026-07-28. */
    roots: Option[JsonObject] = None,
    sampling: Option[SamplingCapability] = None,
    elicitation: Option[ElicitationCapability] = None,
    extensions: Option[Map[String, JsonObject]] = None
)

/** Present if the server offers prompt templates. */
case class PromptsCapability(
    listChanged: Option[Boolean] = None
)

/** Present if the server offers resources to read. */
case class ResourcesCapability(
    subscribe: Option[Boolean] = None,
    listChanged: Option[Boolean] = None
)

/** Present if the server offers tools to call. */
case class ToolsCapability(
    listChanged: Option[Boolean] = None
)

/** Capabilities that a server may support (SPECS ServerCapabilities). */
case class ServerCapabilities(
    experimental: Option[Map[String, JsonObject]] = None,
    /** Deprecated as of 2026-07-28 (SEP-2577). */
    logging: Option[JsonObject] = None,
    completions: Option[JsonObject] = None,
    prompts: Option[PromptsCapability] = None,
    resources: Option[ResourcesCapability] = None,
    tools: Option[ToolsCapability] = None,
    extensions: Option[Map[String, JsonObject]] = None
)
