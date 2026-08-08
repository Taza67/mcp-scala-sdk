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
        SamplingCapability.ContextKey -> sampling.context,
        SamplingCapability.ToolsKey -> sampling.tools
      )
    )

  def fromElicitationCapability(elicitation: ElicitationCapability): JsonObject =
    JsonObject(
      Fields.withOptional(
        Map.empty,
        ElicitationCapability.FormKey -> elicitation.form,
        ElicitationCapability.UrlKey -> elicitation.url
      )
    )

  def fromPromptsCapability(prompts: PromptsCapability): JsonObject =
    JsonObject(
      Fields.withOptional(
        Map.empty,
        PromptsCapability.ListChangedKey -> prompts.listChanged.map(Primitives.fromBool)
      )
    )

  def fromResourcesCapability(resources: ResourcesCapability): JsonObject =
    JsonObject(
      Fields.withOptional(
        Map.empty,
        ResourcesCapability.SubscribeKey -> resources.subscribe.map(Primitives.fromBool),
        ResourcesCapability.ListChangedKey -> resources.listChanged.map(Primitives.fromBool)
      )
    )

  def fromToolsCapability(tools: ToolsCapability): JsonObject =
    JsonObject(
      Fields.withOptional(
        Map.empty,
        ToolsCapability.ListChangedKey -> tools.listChanged.map(Primitives.fromBool)
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
        ClientCapabilities.ExperimentalKey -> capabilities.experimental
          .filter(_.nonEmpty)
          .map(Fields.fromObjectMap),
        ClientCapabilities.RootsKey -> capabilities.roots,
        ClientCapabilities.SamplingKey -> capabilities.sampling.map(fromSamplingCapability),
        ClientCapabilities.ElicitationKey -> capabilities.elicitation.map(
          fromElicitationCapability
        ),
        ClientCapabilities.ExtensionsKey -> Option.when(extensionEntries.nonEmpty)(
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
        ServerCapabilities.ExperimentalKey -> capabilities.experimental
          .filter(_.nonEmpty)
          .map(Fields.fromObjectMap),
        ServerCapabilities.LoggingKey -> capabilities.logging,
        ServerCapabilities.CompletionsKey -> capabilities.completions,
        ServerCapabilities.PromptsKey -> capabilities.prompts.map(fromPromptsCapability),
        ServerCapabilities.ResourcesKey -> capabilities.resources.map(fromResourcesCapability),
        ServerCapabilities.ToolsKey -> capabilities.tools.map(fromToolsCapability),
        ServerCapabilities.ExtensionsKey -> Option.when(extensionEntries.nonEmpty)(
          Fields.fromObjectMap(extensionEntries)
        )
      )
    )
  }

  def toSamplingCapability(sampling: JsonObject): Either[DecodingError, SamplingCapability] = {
    val fields = sampling.value
    for {
      context <- Fields.optionalObject(fields, SamplingCapability.ContextKey)
      tools <- Fields.optionalObject(fields, SamplingCapability.ToolsKey)
    } yield SamplingCapability(context, tools)
  }

  def toElicitationCapability(
      elicitation: JsonObject
  ): Either[DecodingError, ElicitationCapability] = {
    val fields = elicitation.value
    for {
      form <- Fields.optionalObject(fields, ElicitationCapability.FormKey)
      url <- Fields.optionalObject(fields, ElicitationCapability.UrlKey)
    } yield ElicitationCapability(form, url)
  }

  def toPromptsCapability(prompts: JsonObject): Either[DecodingError, PromptsCapability] = {
    val fields = prompts.value
    for {
      listChanged <- Fields.optionalBool(fields, PromptsCapability.ListChangedKey)
    } yield PromptsCapability(listChanged)
  }

  def toResourcesCapability(resources: JsonObject): Either[DecodingError, ResourcesCapability] = {
    val fields = resources.value
    for {
      subscribe <- Fields.optionalBool(fields, ResourcesCapability.SubscribeKey)
      listChanged <- Fields.optionalBool(fields, ResourcesCapability.ListChangedKey)
    } yield ResourcesCapability(subscribe, listChanged)
  }

  def toToolsCapability(tools: JsonObject): Either[DecodingError, ToolsCapability] = {
    val fields = tools.value
    for {
      listChanged <- Fields.optionalBool(fields, ToolsCapability.ListChangedKey)
    } yield ToolsCapability(listChanged)
  }

  def toClientCapabilities(capabilities: JsonObject): Either[DecodingError, ClientCapabilities] = {
    val fields = capabilities.value
    for {
      experimental <- Fields.optional(fields, ClientCapabilities.ExperimentalKey)(v =>
        Fields
          .asObject(v, ClientCapabilities.ExperimentalKey)
          .flatMap(Fields.toObjectMap(_, ClientCapabilities.ExperimentalKey))
      )
      roots <- Fields.optionalObject(fields, ClientCapabilities.RootsKey)
      sampling <- Fields.optional(fields, ClientCapabilities.SamplingKey)(v =>
        Fields.asObject(v, ClientCapabilities.SamplingKey).flatMap(toSamplingCapability)
      )
      elicitation <- Fields.optional(fields, ClientCapabilities.ElicitationKey)(v =>
        Fields.asObject(v, ClientCapabilities.ElicitationKey).flatMap(toElicitationCapability)
      )
      extensions <- Fields
        .optional(fields, ClientCapabilities.ExtensionsKey)(v =>
          Fields
            .asObject(v, ClientCapabilities.ExtensionsKey)
            .flatMap(Fields.toObjectMap(_, ClientCapabilities.ExtensionsKey))
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

  def toServerCapabilities(capabilities: JsonObject): Either[DecodingError, ServerCapabilities] = {
    val fields = capabilities.value
    for {
      experimental <- Fields.optional(fields, ServerCapabilities.ExperimentalKey)(v =>
        Fields
          .asObject(v, ServerCapabilities.ExperimentalKey)
          .flatMap(Fields.toObjectMap(_, ServerCapabilities.ExperimentalKey))
      )
      logging <- Fields.optionalObject(fields, ServerCapabilities.LoggingKey)
      completions <- Fields.optionalObject(fields, ServerCapabilities.CompletionsKey)
      prompts <- Fields.optional(fields, ServerCapabilities.PromptsKey)(v =>
        Fields.asObject(v, ServerCapabilities.PromptsKey).flatMap(toPromptsCapability)
      )
      resources <- Fields.optional(fields, ServerCapabilities.ResourcesKey)(v =>
        Fields.asObject(v, ServerCapabilities.ResourcesKey).flatMap(toResourcesCapability)
      )
      tools <- Fields.optional(fields, ServerCapabilities.ToolsKey)(v =>
        Fields.asObject(v, ServerCapabilities.ToolsKey).flatMap(toToolsCapability)
      )
      extensions <- Fields
        .optional(fields, ServerCapabilities.ExtensionsKey)(v =>
          Fields
            .asObject(v, ServerCapabilities.ExtensionsKey)
            .flatMap(Fields.toObjectMap(_, ServerCapabilities.ExtensionsKey))
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
