package io.github.taza67.mcp.codec.mcp

import io.github.taza67.mcp.codec.DecodingError
import io.github.taza67.mcp.codec.Fields
import io.github.taza67.mcp.codec.Primitives
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.mcp.ClientCapabilities
import io.github.taza67.mcp.protocol.mcp.ElicitationCapability
import io.github.taza67.mcp.protocol.mcp.PromptsCapability
import io.github.taza67.mcp.protocol.mcp.ResourcesCapability
import io.github.taza67.mcp.protocol.mcp.SamplingCapability
import io.github.taza67.mcp.protocol.mcp.ServerCapabilities
import io.github.taza67.mcp.protocol.mcp.ToolsCapability



/** Protocol AST bridge for MCP capability bags (`JsonObject` ↔ ADT). */
private[mcp] object Capabilities {

  def fromSamplingCapability(sampling: SamplingCapability): JsonObject =
    JsonObject(
      Fields.withOptional(
        Map.empty,
        "context" -> sampling.context,
        "tools" -> sampling.tools
      )
    )

  def fromElicitationCapability(elicitation: ElicitationCapability): JsonObject =
    JsonObject(
      Fields.withOptional(
        Map.empty,
        "form" -> elicitation.form,
        "url" -> elicitation.url
      )
    )

  def fromPromptsCapability(prompts: PromptsCapability): JsonObject =
    JsonObject(
      Fields.withOptional(
        Map.empty,
        "listChanged" -> prompts.listChanged.map(Primitives.fromBool)
      )
    )

  def fromResourcesCapability(resources: ResourcesCapability): JsonObject =
    JsonObject(
      Fields.withOptional(
        Map.empty,
        "subscribe" -> resources.subscribe.map(Primitives.fromBool),
        "listChanged" -> resources.listChanged.map(Primitives.fromBool)
      )
    )

  def fromToolsCapability(tools: ToolsCapability): JsonObject =
    JsonObject(
      Fields.withOptional(
        Map.empty,
        "listChanged" -> tools.listChanged.map(Primitives.fromBool)
      )
    )

  def fromClientCapabilities(capabilities: ClientCapabilities): JsonObject = {
    val extensionEntries =
      capabilities.extensions
        .getOrElse(Map.empty)
        .filterNot { case (key, _) => ClientCapabilities.KnownKeys.contains(key) }
    JsonObject(
      Fields.withOptional(
        Map.empty,
        "experimental" -> capabilities.experimental.filter(_.nonEmpty).map(Fields.fromObjectMap),
        "roots" -> capabilities.roots,
        "sampling" -> capabilities.sampling.map(fromSamplingCapability),
        "elicitation" -> capabilities.elicitation.map(fromElicitationCapability),
        "extensions" -> Option.when(extensionEntries.nonEmpty)(
          Fields.fromObjectMap(extensionEntries)
        )
      )
    )
  }

  def fromServerCapabilities(capabilities: ServerCapabilities): JsonObject = {
    val extensionEntries =
      capabilities.extensions
        .getOrElse(Map.empty)
        .filterNot { case (key, _) => ServerCapabilities.KnownKeys.contains(key) }
    JsonObject(
      Fields.withOptional(
        Map.empty,
        "experimental" -> capabilities.experimental.filter(_.nonEmpty).map(Fields.fromObjectMap),
        "logging" -> capabilities.logging,
        "completions" -> capabilities.completions,
        "prompts" -> capabilities.prompts.map(fromPromptsCapability),
        "resources" -> capabilities.resources.map(fromResourcesCapability),
        "tools" -> capabilities.tools.map(fromToolsCapability),
        "extensions" -> Option.when(extensionEntries.nonEmpty)(
          Fields.fromObjectMap(extensionEntries)
        )
      )
    )
  }

  def toSamplingCapability(value: JsonObject): Either[DecodingError, SamplingCapability] = {
    val fields = value.value
    for {
      context <- Fields.optionalObject(fields, "context")
      tools <- Fields.optionalObject(fields, "tools")
    } yield SamplingCapability(context, tools)
  }

  def toElicitationCapability(value: JsonObject): Either[DecodingError, ElicitationCapability] = {
    val fields = value.value
    for {
      form <- Fields.optionalObject(fields, "form")
      url <- Fields.optionalObject(fields, "url")
    } yield ElicitationCapability(form, url)
  }

  def toPromptsCapability(value: JsonObject): Either[DecodingError, PromptsCapability] = {
    val fields = value.value
    for {
      listChanged <- Fields.optionalBool(fields, "listChanged")
    } yield PromptsCapability(listChanged)
  }

  def toResourcesCapability(value: JsonObject): Either[DecodingError, ResourcesCapability] = {
    val fields = value.value
    for {
      subscribe <- Fields.optionalBool(fields, "subscribe")
      listChanged <- Fields.optionalBool(fields, "listChanged")
    } yield ResourcesCapability(subscribe, listChanged)
  }

  def toToolsCapability(value: JsonObject): Either[DecodingError, ToolsCapability] = {
    val fields = value.value
    for {
      listChanged <- Fields.optionalBool(fields, "listChanged")
    } yield ToolsCapability(listChanged)
  }

  def toClientCapabilities(value: JsonObject): Either[DecodingError, ClientCapabilities] = {
    val fields = value.value
    for {
      experimental <- Fields.optional(fields, "experimental")(v =>
        Fields.asObject(v, "experimental").flatMap(Fields.toObjectMap(_, "experimental"))
      )
      roots <- Fields.optionalObject(fields, "roots")
      sampling <- Fields.optional(fields, "sampling")(v =>
        Fields.asObject(v, "sampling").flatMap(toSamplingCapability)
      )
      elicitation <- Fields.optional(fields, "elicitation")(v =>
        Fields.asObject(v, "elicitation").flatMap(toElicitationCapability)
      )
      extensions <- Fields
        .optional(fields, "extensions")(v =>
          Fields.asObject(v, "extensions").flatMap(Fields.toObjectMap(_, "extensions"))
        )
        .map(_.map(_.filterNot { case (key, _) => ClientCapabilities.KnownKeys.contains(key) }))
    } yield ClientCapabilities(
      experimental = experimental.filter(_.nonEmpty),
      roots = roots,
      sampling = sampling,
      elicitation = elicitation,
      extensions = extensions.filter(_.nonEmpty)
    )
  }

  def toServerCapabilities(value: JsonObject): Either[DecodingError, ServerCapabilities] = {
    val fields = value.value
    for {
      experimental <- Fields.optional(fields, "experimental")(v =>
        Fields.asObject(v, "experimental").flatMap(Fields.toObjectMap(_, "experimental"))
      )
      logging <- Fields.optionalObject(fields, "logging")
      completions <- Fields.optionalObject(fields, "completions")
      prompts <- Fields.optional(fields, "prompts")(v =>
        Fields.asObject(v, "prompts").flatMap(toPromptsCapability)
      )
      resources <- Fields.optional(fields, "resources")(v =>
        Fields.asObject(v, "resources").flatMap(toResourcesCapability)
      )
      tools <- Fields.optional(fields, "tools")(v =>
        Fields.asObject(v, "tools").flatMap(toToolsCapability)
      )
      extensions <- Fields
        .optional(fields, "extensions")(v =>
          Fields.asObject(v, "extensions").flatMap(Fields.toObjectMap(_, "extensions"))
        )
        .map(_.map(_.filterNot { case (key, _) => ServerCapabilities.KnownKeys.contains(key) }))
    } yield ServerCapabilities(
      experimental = experimental.filter(_.nonEmpty),
      logging = logging,
      completions = completions,
      prompts = prompts,
      resources = resources,
      tools = tools,
      extensions = extensions.filter(_.nonEmpty)
    )
  }
}
