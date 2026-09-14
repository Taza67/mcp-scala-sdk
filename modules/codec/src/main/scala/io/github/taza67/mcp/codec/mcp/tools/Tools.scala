package io.github.taza67.mcp.codec.mcp.tools

import io.github.taza67.mcp.codec.DecodingError
import io.github.taza67.mcp.codec.Fields
import io.github.taza67.mcp.codec.Primitives
import io.github.taza67.mcp.codec.mcp.ContinuationFields
import io.github.taza67.mcp.codec.mcp.Content
import io.github.taza67.mcp.codec.mcp.Input
import io.github.taza67.mcp.codec.mcp.Meta
import io.github.taza67.mcp.codec.mcp.PlainNotifications
import io.github.taza67.mcp.codec.mcp.PlainRequests
import io.github.taza67.mcp.codec.mcp.lists.PaginatedListResults
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.json.JsonValue
import io.github.taza67.mcp.protocol.mcp.MetaObject
import io.github.taza67.mcp.protocol.mcp.Icon
import io.github.taza67.mcp.protocol.mcp.RequestParams
import io.github.taza67.mcp.protocol.mcp.tools.{Tools => ToolMethods}
import io.github.taza67.mcp.protocol.mcp.RequestOutcome
import io.github.taza67.mcp.protocol.mcp.tools.CallToolRequest
import io.github.taza67.mcp.protocol.mcp.tools.CallToolRequestParams
import io.github.taza67.mcp.protocol.mcp.tools.CallToolResult
import io.github.taza67.mcp.protocol.mcp.tools.ListToolsResult
import io.github.taza67.mcp.protocol.mcp.tools.Tool
import io.github.taza67.mcp.protocol.mcp.tools.ToolAnnotations
import io.github.taza67.mcp.protocol.mcp.tools.ToolInputSchema
import io.github.taza67.mcp.protocol.mcp.tools.ToolListChangedNotification
import io.github.taza67.mcp.protocol.mcp.tools.ToolOutputSchema



/** Protocol AST bridge for MCP tools-domain types (`JsonObject` ↔ ADT). */
object Tools {

  def fromToolAnnotations(toolAnnotations: ToolAnnotations): JsonObject =
    JsonObject(
      Fields.withOptional(
        Map.empty,
        ToolAnnotations.TitleKey -> toolAnnotations.title.map(JsonString(_)),
        ToolAnnotations.ReadOnlyHintKey -> toolAnnotations.readOnlyHint.map(Primitives.fromBool),
        ToolAnnotations.DestructiveHintKey ->
          toolAnnotations.destructiveHint.map(Primitives.fromBool),
        ToolAnnotations.IdempotentHintKey ->
          toolAnnotations.idempotentHint.map(Primitives.fromBool),
        ToolAnnotations.OpenWorldHintKey ->
          toolAnnotations.openWorldHint.map(Primitives.fromBool)
      )
    )

  def fromToolInputSchema(toolInputSchema: ToolInputSchema): JsonObject = {
    val base =
      toolInputSchema.fields.value +
        (ToolInputSchema.TypeKey -> JsonString(ToolInputSchema.TypeValue))
    JsonObject(
      Fields.withOptional(
        base,
        ToolInputSchema.SchemaKey -> toolInputSchema.schema.map(JsonString(_))
      )
    )
  }

  def fromToolOutputSchema(toolOutputSchema: ToolOutputSchema): JsonObject =
    JsonObject(
      Fields.withOptional(
        toolOutputSchema.fields.value,
        ToolOutputSchema.SchemaKey -> toolOutputSchema.schema.map(JsonString(_))
      )
    )

  def fromTool(tool: Tool): JsonObject = {
    val base = Map(
      Tool.NameKey -> JsonString(tool.name),
      Tool.InputSchemaKey -> fromToolInputSchema(tool.inputSchema)
    )
    JsonObject(
      Fields.withOptional(
        base,
        Tool.TitleKey -> tool.title.map(JsonString(_)),
        Tool.DescriptionKey -> tool.description.map(JsonString(_)),
        Tool.OutputSchemaKey -> tool.outputSchema.map(fromToolOutputSchema),
        Tool.AnnotationsKey -> tool.annotations.map(fromToolAnnotations),
        Tool.IconsKey -> tool.icons.map(Primitives.fromList(_)(Meta.fromIcon)),
        Tool.MetaKey -> tool.meta.map(m => JsonObject(m.value))
      )
    )
  }

  def fromCallToolRequestParams(callToolRequestParams: CallToolRequestParams): RequestParams = {
    val base = Map(CallToolRequestParams.NameKey -> JsonString(callToolRequestParams.name))
    RequestParams(
      meta = callToolRequestParams.meta,
      fields = JsonObject(
        Fields.withOptional(
          base,
          (CallToolRequestParams.ArgumentsKey -> callToolRequestParams.arguments) +:
            Input.fromContinuation(
              ContinuationFields(
                inputResponses = callToolRequestParams.inputResponses,
                requestState = callToolRequestParams.requestState
              )
            ): _*
        )
      )
    )
  }

  def fromCallToolRequest(callToolRequest: CallToolRequest): JsonObject =
    PlainRequests.fromRequest(
      ToolMethods.call,
      callToolRequest.id,
      fromCallToolRequestParams(callToolRequest.params),
      callToolRequest.jsonrpc
    )

  def toToolAnnotations(
      toolAnnotations: JsonObject
  ): Either[DecodingError, ToolAnnotations] = {
    val fields = toolAnnotations.value
    for {
      title <- Fields.optionalString(fields, ToolAnnotations.TitleKey)
      readOnlyHint <- Fields.optionalBool(fields, ToolAnnotations.ReadOnlyHintKey)
      destructiveHint <- Fields.optionalBool(fields, ToolAnnotations.DestructiveHintKey)
      idempotentHint <- Fields.optionalBool(fields, ToolAnnotations.IdempotentHintKey)
      openWorldHint <- Fields.optionalBool(fields, ToolAnnotations.OpenWorldHintKey)
    } yield ToolAnnotations(
      title = title,
      readOnlyHint = readOnlyHint,
      destructiveHint = destructiveHint,
      idempotentHint = idempotentHint,
      openWorldHint = openWorldHint
    )
  }

  def toToolInputSchema(
      toolInputSchema: JsonObject
  ): Either[DecodingError, ToolInputSchema] = {
    val fields = toolInputSchema.value
    for {
      typeValue <- Fields.requiredString(fields, ToolInputSchema.TypeKey)
      _ <- Fields.requireEquals(typeValue, ToolInputSchema.TypeValue, ToolInputSchema.TypeKey)
      schema <- Fields.optionalString(fields, ToolInputSchema.SchemaKey)
    } yield ToolInputSchema(
      fields = JsonObject(fields - ToolInputSchema.TypeKey - ToolInputSchema.SchemaKey),
      schema = schema
    )
  }

  def toToolOutputSchema(
      toolOutputSchema: JsonObject
  ): Either[DecodingError, ToolOutputSchema] = {
    val fields = toolOutputSchema.value
    for {
      schema <- Fields.optionalString(fields, ToolOutputSchema.SchemaKey)
    } yield ToolOutputSchema(
      fields = JsonObject(fields - ToolOutputSchema.SchemaKey),
      schema = schema
    )
  }

  def toTool(tool: JsonObject): Either[DecodingError, Tool] = {
    val fields = tool.value
    for {
      name <- Fields.requiredString(fields, Tool.NameKey)
      inputSchema <- Fields
        .requiredObject(fields, Tool.InputSchemaKey)
        .flatMap(toToolInputSchema)
      title <- Fields.optionalString(fields, Tool.TitleKey)
      description <- Fields.optionalString(fields, Tool.DescriptionKey)
      outputSchema <- Fields.optional(fields, Tool.OutputSchemaKey)(v =>
        Fields.asObject(v, Tool.OutputSchemaKey).flatMap(toToolOutputSchema)
      )
      annotations <- Fields.optional(fields, Tool.AnnotationsKey)(v =>
        Fields.asObject(v, Tool.AnnotationsKey).flatMap(toToolAnnotations)
      )
      icons <- Fields.optionalList(fields, Tool.IconsKey) { v =>
        Fields.asObject(v, Icon.IconKey).flatMap(Meta.toIcon)
      }
      meta <- Fields.optional(fields, Tool.MetaKey)(v =>
        Fields.asObject(v, Tool.MetaKey).map(obj => MetaObject(obj.value))
      )
    } yield Tool(
      name = name,
      inputSchema = inputSchema,
      title = title,
      description = description,
      outputSchema = outputSchema,
      annotations = annotations,
      icons = icons,
      meta = meta
    )
  }

  def toCallToolRequestParams(
      params: RequestParams
  ): Either[DecodingError, CallToolRequestParams] = {
    val fields = params.fields.value
    for {
      name <- Fields.requiredString(fields, CallToolRequestParams.NameKey)
      arguments <- Fields.optionalObject(fields, CallToolRequestParams.ArgumentsKey)
      continuation <- Input.toContinuation(fields)
    } yield CallToolRequestParams(
      meta = params.meta,
      name = name,
      arguments = arguments,
      inputResponses = continuation.inputResponses,
      requestState = continuation.requestState
    )
  }

  def toCallToolRequest(message: JsonValue): Either[DecodingError, CallToolRequest] =
    PlainRequests.toRequest(ToolMethods.call, message)(toCallToolRequestParams) {
      (id, callToolRequestParams, jsonrpc) =>
        CallToolRequest(id = id, params = callToolRequestParams, jsonrpc = jsonrpc)
    }

  def fromCallToolResult(callToolResult: CallToolResult): JsonObject =
    JsonObject(
      Fields.withOptional(
        Map(
          CallToolResult.ContentKey ->
            Primitives.fromList(callToolResult.content)(Content.fromContentBlock)
        ),
        CallToolResult.StructuredContentKey -> callToolResult.structuredContent,
        CallToolResult.IsErrorKey -> callToolResult.isError.map(Primitives.fromBool)
      )
    )

  def toCallToolResult(callToolResult: JsonObject): Either[DecodingError, CallToolResult] = {
    val fields = callToolResult.value
    for {
      content <- Fields
        .required(fields, CallToolResult.ContentKey)
        .flatMap(
          Primitives.toList(_, CallToolResult.ContentKey)(v =>
            Fields.asObject(v, CallToolResult.ContentKey).flatMap(Content.toContentBlock)
          )
        )
      structuredContent <- Fields.optionalValue(fields, CallToolResult.StructuredContentKey)
      isError <- Fields.optionalBool(fields, CallToolResult.IsErrorKey)
    } yield CallToolResult(
      content = content,
      structuredContent = structuredContent,
      isError = isError
    )
  }

  def fromListToolsResult(listToolsResult: ListToolsResult): JsonObject = {
    val tail = PaginatedListResults.fromPaginatedTail(
      PaginatedListResults.PaginatedTail(
        PaginatedListResults.CacheableTail(
          listToolsResult.ttlMs,
          listToolsResult.cacheScope
        ),
        listToolsResult.nextCursor
      )
    )
    JsonObject(
      Map(ListToolsResult.ToolsKey -> Primitives.fromList(listToolsResult.tools)(fromTool)) ++ tail
    )
  }

  def toListToolsResult(listToolsResult: JsonObject): Either[DecodingError, ListToolsResult] = {
    val fields = listToolsResult.value
    for {
      tools <- Fields
        .required(fields, ListToolsResult.ToolsKey)
        .flatMap(
          Primitives.toList(_, ListToolsResult.ToolsKey)(v =>
            Fields.asObject(v, ListToolsResult.ToolsKey).flatMap(toTool)
          )
        )
      tail <- PaginatedListResults.toPaginatedTail(fields)
    } yield ListToolsResult(
      tools = tools,
      ttlMs = tail.cacheable.ttlMs,
      cacheScope = tail.cacheable.cacheScope,
      nextCursor = tail.nextCursor
    )
  }

  def fromCallToolOutcome(outcome: RequestOutcome[CallToolResult]): JsonObject =
    Input.fromRequestOutcome(outcome)(fromCallToolResult)

  def toCallToolOutcome(
      message: JsonObject
  ): Either[DecodingError, RequestOutcome[CallToolResult]] =
    Input.toRequestOutcome(message)(toCallToolResult)

  def fromToolListChangedNotification(
      notification: ToolListChangedNotification
  ): JsonObject =
    PlainNotifications.fromOptionalNotification(
      method = ToolMethods.listChangedNotification,
      params = notification.params,
      jsonrpc = notification.jsonrpc
    )

  def toToolListChangedNotification(
      message: JsonValue
  ): Either[DecodingError, ToolListChangedNotification] =
    PlainNotifications.toOptionalNotification(
      ToolMethods.listChangedNotification,
      message
    ) { (params, jsonrpc) =>
      ToolListChangedNotification(params = params, jsonrpc = jsonrpc)
    }
}
