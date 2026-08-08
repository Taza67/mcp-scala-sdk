package io.github.taza67.mcp.codec.mcp.sampling

import io.github.taza67.mcp.codec.DecodingError
import io.github.taza67.mcp.codec.Fields
import io.github.taza67.mcp.codec.Primitives
import io.github.taza67.mcp.codec.mcp.Content
import io.github.taza67.mcp.codec.mcp.ObjectRequests
import io.github.taza67.mcp.codec.mcp.tools.{Tools => ToolsCodec}
import io.github.taza67.mcp.protocol.json.JsonNumber
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.json.JsonValue
import io.github.taza67.mcp.protocol.mcp.MetaObject
import io.github.taza67.mcp.protocol.mcp.sampling.CreateMessageRequest
import io.github.taza67.mcp.protocol.mcp.sampling.CreateMessageRequestParams
import io.github.taza67.mcp.protocol.mcp.sampling.CreateMessageResult
import io.github.taza67.mcp.protocol.mcp.sampling.IncludeContext
import io.github.taza67.mcp.protocol.mcp.sampling.ModelHint
import io.github.taza67.mcp.protocol.mcp.sampling.ModelPreferences
import io.github.taza67.mcp.protocol.mcp.sampling.{Sampling => SamplingMethods}
import io.github.taza67.mcp.protocol.mcp.sampling.SamplingMessage
import io.github.taza67.mcp.protocol.mcp.sampling.ToolChoice
import io.github.taza67.mcp.protocol.mcp.sampling.ToolChoiceMode



/** Protocol AST bridge for MCP sampling-domain types (`JsonObject` ↔ ADT).
 *
 *  @deprecated Deprecated as of protocol version 2026-07-28 (SEP-2577). Remains for at least twelve months.
 */
object Sampling {

  def fromModelHint(modelHint: ModelHint): JsonObject =
    JsonObject(
      Fields.withOptional(
        Map.empty,
        ModelHint.NameKey -> modelHint.name.map(JsonString(_))
      )
    )

  def fromModelPreferences(modelPreferences: ModelPreferences): JsonObject =
    JsonObject(
      Fields.withOptional(
        Map.empty,
        ModelPreferences.HintsKey -> modelPreferences.hints.map(
          Primitives.fromList(_)(fromModelHint)
        ),
        ModelPreferences.CostPriorityKey -> modelPreferences.costPriority.map(
          Primitives.fromDouble
        ),
        ModelPreferences.SpeedPriorityKey ->
          modelPreferences.speedPriority.map(Primitives.fromDouble),
        ModelPreferences.IntelligencePriorityKey ->
          modelPreferences.intelligencePriority.map(Primitives.fromDouble)
      )
    )

  def fromIncludeContext(includeContext: IncludeContext): JsonString =
    JsonString(includeContext.value)

  def fromToolChoiceMode(toolChoiceMode: ToolChoiceMode): JsonString =
    JsonString(toolChoiceMode.value)

  def fromToolChoice(toolChoice: ToolChoice): JsonObject =
    JsonObject(
      Fields.withOptional(
        Map.empty,
        ToolChoice.ModeKey -> toolChoice.mode.map(fromToolChoiceMode)
      )
    )

  def fromSamplingMessage(samplingMessage: SamplingMessage): JsonObject = {
    val base = Map(
      SamplingMessage.RoleKey -> Content.fromRole(samplingMessage.role),
      SamplingMessage.ContentKey -> Content.fromSamplingMessageContent(samplingMessage.content)
    )
    JsonObject(
      Fields.withOptional(
        base,
        SamplingMessage.MetaKey -> samplingMessage.meta.map(m => JsonObject(m.value))
      )
    )
  }

  def fromCreateMessageRequestParams(
      createMessageRequestParams: CreateMessageRequestParams
  ): JsonObject = {
    val base = Map(
      CreateMessageRequestParams.MessagesKey ->
        Primitives.fromList(createMessageRequestParams.messages)(fromSamplingMessage),
      CreateMessageRequestParams.MaxTokensKey ->
        JsonNumber(createMessageRequestParams.maxTokens)
    )
    JsonObject(
      Fields.withOptional(
        base,
        CreateMessageRequestParams.ModelPreferencesKey ->
          createMessageRequestParams.modelPreferences.map(fromModelPreferences),
        CreateMessageRequestParams.SystemPromptKey ->
          createMessageRequestParams.systemPrompt.map(JsonString(_)),
        CreateMessageRequestParams.IncludeContextKey ->
          createMessageRequestParams.includeContext.map(fromIncludeContext),
        CreateMessageRequestParams.TemperatureKey ->
          createMessageRequestParams.temperature.map(Primitives.fromDouble),
        CreateMessageRequestParams.StopSequencesKey ->
          createMessageRequestParams.stopSequences.map(
            Primitives.fromList(_)(JsonString(_))
          ),
        CreateMessageRequestParams.MetadataKey -> createMessageRequestParams.metadata,
        CreateMessageRequestParams.ToolsKey -> createMessageRequestParams.tools.map(
          Primitives.fromList(_)(ToolsCodec.fromTool)
        ),
        CreateMessageRequestParams.ToolChoiceKey ->
          createMessageRequestParams.toolChoice.map(fromToolChoice)
      )
    )
  }

  def fromCreateMessageRequest(createMessageRequest: CreateMessageRequest): JsonObject =
    ObjectRequests.fromRequest(
      SamplingMethods.createMessage,
      createMessageRequest.id,
      fromCreateMessageRequestParams(createMessageRequest.params),
      createMessageRequest.jsonrpc
    )

  def fromCreateMessageResult(createMessageResult: CreateMessageResult): JsonObject = {
    val base = Map(
      CreateMessageResult.ModelKey -> JsonString(createMessageResult.model),
      CreateMessageResult.RoleKey -> Content.fromRole(createMessageResult.role),
      CreateMessageResult.ContentKey -> Content.fromSamplingMessageContent(
        createMessageResult.content
      )
    )
    JsonObject(
      Fields.withOptional(
        base,
        CreateMessageResult.StopReasonKey -> createMessageResult.stopReason.map(JsonString(_)),
        CreateMessageResult.MetaKey -> createMessageResult.meta.map(m => JsonObject(m.value))
      )
    )
  }

  def toModelHint(modelHint: JsonObject): Either[DecodingError, ModelHint] = {
    val fields = modelHint.value
    for {
      name <- Fields.optionalString(fields, ModelHint.NameKey)
    } yield ModelHint(name = name)
  }

  def toModelPreferences(
      modelPreferences: JsonObject
  ): Either[DecodingError, ModelPreferences] = {
    val fields = modelPreferences.value
    for {
      hints <- Fields.optionalList(fields, ModelPreferences.HintsKey)(v =>
        Fields.asObject(v, ModelPreferences.HintsKey).flatMap(toModelHint)
      )
      costPriority <- Fields.optional(fields, ModelPreferences.CostPriorityKey)(
        Primitives.asDouble(_, ModelPreferences.CostPriorityKey)
      )
      speedPriority <- Fields.optional(fields, ModelPreferences.SpeedPriorityKey)(
        Primitives.asDouble(_, ModelPreferences.SpeedPriorityKey)
      )
      intelligencePriority <- Fields.optional(fields, ModelPreferences.IntelligencePriorityKey)(
        Primitives.asDouble(_, ModelPreferences.IntelligencePriorityKey)
      )
    } yield ModelPreferences(
      hints = hints,
      costPriority = costPriority,
      speedPriority = speedPriority,
      intelligencePriority = intelligencePriority
    )
  }

  def toIncludeContext(includeContext: JsonValue): Either[DecodingError, IncludeContext] =
    Primitives.asString(includeContext, CreateMessageRequestParams.IncludeContextKey).flatMap { s =>
      IncludeContext
        .fromValue(s)
        .toRight(DecodingError(s"Invalid ${CreateMessageRequestParams.IncludeContextKey}: $s"))
    }

  def toToolChoiceMode(toolChoiceMode: JsonValue): Either[DecodingError, ToolChoiceMode] =
    Primitives.asString(toolChoiceMode, ToolChoice.ModeKey).flatMap { s =>
      ToolChoiceMode.fromValue(s).toRight(DecodingError(s"Invalid ${ToolChoice.ModeKey}: $s"))
    }

  def toToolChoice(toolChoice: JsonObject): Either[DecodingError, ToolChoice] = {
    val fields = toolChoice.value
    for {
      modeWire <- Fields.optionalString(fields, ToolChoice.ModeKey)
      mode <- ToolChoice
        .modeFromWire(modeWire)
        .toRight(
          DecodingError(
            s"Invalid ${ToolChoice.ModeKey}: ${modeWire.getOrElse("<missing>")}"
          )
        )
    } yield ToolChoice(mode = Some(mode))
  }

  def toSamplingMessage(
      samplingMessage: JsonObject
  ): Either[DecodingError, SamplingMessage] = {
    val fields = samplingMessage.value
    for {
      role <- Fields.required(fields, SamplingMessage.RoleKey).flatMap(Content.toRole)
      content <- Fields
        .required(fields, SamplingMessage.ContentKey)
        .flatMap(Content.toSamplingMessageContent)
      meta <- Fields.optional(fields, SamplingMessage.MetaKey)(v =>
        Fields.asObject(v, SamplingMessage.MetaKey).map(obj => MetaObject(obj.value))
      )
    } yield SamplingMessage(role = role, content = content, meta = meta)
  }

  def toCreateMessageRequestParams(
      createMessageRequestParams: JsonObject
  ): Either[DecodingError, CreateMessageRequestParams] = {
    val fields = createMessageRequestParams.value
    for {
      messages <- Fields
        .required(fields, CreateMessageRequestParams.MessagesKey)
        .flatMap(
          Primitives.toList(_, CreateMessageRequestParams.MessagesKey)(v =>
            Fields
              .asObject(v, CreateMessageRequestParams.MessagesKey)
              .flatMap(toSamplingMessage)
          )
        )
      maxTokens <- Fields
        .required(fields, CreateMessageRequestParams.MaxTokensKey)
        .flatMap(Primitives.asLong(_, CreateMessageRequestParams.MaxTokensKey))
      modelPreferences <- Fields.optional(fields, CreateMessageRequestParams.ModelPreferencesKey)(
        v =>
          Fields
            .asObject(v, CreateMessageRequestParams.ModelPreferencesKey)
            .flatMap(toModelPreferences)
      )
      systemPrompt <- Fields.optionalString(fields, CreateMessageRequestParams.SystemPromptKey)
      includeContext <- Fields.optional(fields, CreateMessageRequestParams.IncludeContextKey)(
        toIncludeContext
      )
      temperature <- Fields.optional(fields, CreateMessageRequestParams.TemperatureKey)(
        Primitives.asDouble(_, CreateMessageRequestParams.TemperatureKey)
      )
      stopSequences <- Fields.optionalList(fields, CreateMessageRequestParams.StopSequencesKey)(
        Primitives.asString(_, CreateMessageRequestParams.StopSequencesKey)
      )
      metadata <- Fields.optionalObject(fields, CreateMessageRequestParams.MetadataKey)
      tools <- Fields.optionalList(fields, CreateMessageRequestParams.ToolsKey)(v =>
        Fields.asObject(v, CreateMessageRequestParams.ToolsKey).flatMap(ToolsCodec.toTool)
      )
      toolChoice <- Fields.optional(fields, CreateMessageRequestParams.ToolChoiceKey)(v =>
        Fields.asObject(v, CreateMessageRequestParams.ToolChoiceKey).flatMap(toToolChoice)
      )
    } yield CreateMessageRequestParams(
      messages = messages,
      maxTokens = maxTokens,
      modelPreferences = modelPreferences,
      systemPrompt = systemPrompt,
      includeContext = includeContext,
      temperature = temperature,
      stopSequences = stopSequences,
      metadata = metadata,
      tools = tools,
      toolChoice = toolChoice
    )
  }

  def toCreateMessageRequest(message: JsonValue): Either[DecodingError, CreateMessageRequest] =
    ObjectRequests.toRequest(SamplingMethods.createMessage, message)(toCreateMessageRequestParams) {
      (id, createMessageRequestParams, jsonrpc) =>
        CreateMessageRequest(id = id, params = createMessageRequestParams, jsonrpc = jsonrpc)
    }

  def toCreateMessageResult(
      createMessageResult: JsonObject
  ): Either[DecodingError, CreateMessageResult] = {
    val fields = createMessageResult.value
    for {
      model <- Fields.requiredString(fields, CreateMessageResult.ModelKey)
      role <- Fields.required(fields, CreateMessageResult.RoleKey).flatMap(Content.toRole)
      content <- Fields
        .required(fields, CreateMessageResult.ContentKey)
        .flatMap(Content.toSamplingMessageContent)
      stopReason <- Fields.optionalString(fields, CreateMessageResult.StopReasonKey)
      meta <- Fields.optional(fields, CreateMessageResult.MetaKey)(v =>
        Fields.asObject(v, CreateMessageResult.MetaKey).map(obj => MetaObject(obj.value))
      )
    } yield CreateMessageResult(
      model = model,
      role = role,
      content = content,
      stopReason = stopReason,
      meta = meta
    )
  }
}
