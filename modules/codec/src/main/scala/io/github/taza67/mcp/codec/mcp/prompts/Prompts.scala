package io.github.taza67.mcp.codec.mcp.prompts

import io.github.taza67.mcp.codec.DecodingError
import io.github.taza67.mcp.codec.Fields
import io.github.taza67.mcp.codec.Primitives
import io.github.taza67.mcp.codec.mcp.Content
import io.github.taza67.mcp.codec.mcp.ContinuationFields
import io.github.taza67.mcp.codec.mcp.Input
import io.github.taza67.mcp.codec.mcp.Meta
import io.github.taza67.mcp.codec.mcp.PlainRequests
import io.github.taza67.mcp.codec.mcp.lists.PaginatedListResults
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.json.JsonValue
import io.github.taza67.mcp.protocol.mcp.MetaObject
import io.github.taza67.mcp.protocol.mcp.Icon
import io.github.taza67.mcp.protocol.mcp.RequestOutcome
import io.github.taza67.mcp.protocol.mcp.RequestParams
import io.github.taza67.mcp.protocol.mcp.prompts.{Prompts => PromptMethods}
import io.github.taza67.mcp.protocol.mcp.prompts.GetPromptRequest
import io.github.taza67.mcp.protocol.mcp.prompts.GetPromptRequestParams
import io.github.taza67.mcp.protocol.mcp.prompts.GetPromptResult
import io.github.taza67.mcp.protocol.mcp.prompts.ListPromptsResult
import io.github.taza67.mcp.protocol.mcp.prompts.Prompt
import io.github.taza67.mcp.protocol.mcp.prompts.PromptArgument
import io.github.taza67.mcp.protocol.mcp.prompts.PromptMessage



/** Protocol AST bridge for MCP prompts-domain types (`JsonObject` ↔ ADT). */
object Prompts {

  def fromPromptArgument(promptArgument: PromptArgument): JsonObject =
    JsonObject(
      Fields.withOptional(
        Map(PromptArgument.NameKey -> JsonString(promptArgument.name)),
        PromptArgument.TitleKey -> promptArgument.title.map(JsonString(_)),
        PromptArgument.DescriptionKey -> promptArgument.description.map(JsonString(_)),
        PromptArgument.RequiredKey -> promptArgument.required.map(Primitives.fromBool)
      )
    )

  def fromPrompt(prompt: Prompt): JsonObject = {
    val base = Map(Prompt.NameKey -> JsonString(prompt.name))
    JsonObject(
      Fields.withOptional(
        base,
        Prompt.TitleKey -> prompt.title.map(JsonString(_)),
        Prompt.DescriptionKey -> prompt.description.map(JsonString(_)),
        Prompt.ArgumentsKey -> prompt.arguments.map(
          Primitives.fromList(_)(fromPromptArgument)
        ),
        Prompt.IconsKey -> prompt.icons.map(Primitives.fromList(_)(Meta.fromIcon)),
        Prompt.MetaKey -> prompt.meta.map(m => JsonObject(m.value))
      )
    )
  }

  def fromPromptMessage(promptMessage: PromptMessage): JsonObject =
    JsonObject(
      Map(
        PromptMessage.RoleKey -> Content.fromRole(promptMessage.role),
        PromptMessage.ContentKey -> Content.fromContentBlock(promptMessage.content)
      )
    )

  def fromGetPromptRequestParams(
      getPromptRequestParams: GetPromptRequestParams
  ): RequestParams = {
    val base = Map(GetPromptRequestParams.NameKey -> JsonString(getPromptRequestParams.name))
    RequestParams(
      meta = getPromptRequestParams.meta,
      fields = JsonObject(
        Fields.withOptional(
          base,
          (GetPromptRequestParams.ArgumentsKey ->
            getPromptRequestParams.arguments.map(Primitives.fromStringMap)) +:
            Input.fromContinuation(
              ContinuationFields(
                inputResponses = getPromptRequestParams.inputResponses,
                requestState = getPromptRequestParams.requestState
              )
            ): _*
        )
      )
    )
  }

  def fromGetPromptRequest(getPromptRequest: GetPromptRequest): JsonObject =
    PlainRequests.fromRequest(
      PromptMethods.get,
      getPromptRequest.id,
      fromGetPromptRequestParams(getPromptRequest.params),
      getPromptRequest.jsonrpc
    )

  def fromGetPromptResult(getPromptResult: GetPromptResult): JsonObject =
    JsonObject(
      Fields.withOptional(
        Map(
          GetPromptResult.MessagesKey ->
            Primitives.fromList(getPromptResult.messages)(fromPromptMessage)
        ),
        GetPromptResult.DescriptionKey -> getPromptResult.description.map(JsonString(_))
      )
    )

  def fromListPromptsResult(listPromptsResult: ListPromptsResult): JsonObject = {
    val tail = PaginatedListResults.fromPaginatedTail(
      PaginatedListResults.PaginatedTail(
        PaginatedListResults.CacheableTail(
          listPromptsResult.ttlMs,
          listPromptsResult.cacheScope
        ),
        listPromptsResult.nextCursor
      )
    )
    JsonObject(
      Map(
        ListPromptsResult.PromptsKey ->
          Primitives.fromList(listPromptsResult.prompts)(fromPrompt)
      ) ++ tail
    )
  }

  def toPromptArgument(
      promptArgument: JsonObject
  ): Either[DecodingError, PromptArgument] = {
    val fields = promptArgument.value
    for {
      name <- Fields.requiredString(fields, PromptArgument.NameKey)
      title <- Fields.optionalString(fields, PromptArgument.TitleKey)
      description <- Fields.optionalString(fields, PromptArgument.DescriptionKey)
      required <- Fields.optionalBool(fields, PromptArgument.RequiredKey)
    } yield PromptArgument(
      name = name,
      title = title,
      description = description,
      required = required
    )
  }

  def toPrompt(prompt: JsonObject): Either[DecodingError, Prompt] = {
    val fields = prompt.value
    for {
      name <- Fields.requiredString(fields, Prompt.NameKey)
      title <- Fields.optionalString(fields, Prompt.TitleKey)
      description <- Fields.optionalString(fields, Prompt.DescriptionKey)
      arguments <- Fields.optionalList(fields, Prompt.ArgumentsKey)(v =>
        Fields.asObject(v, Prompt.ArgumentsKey).flatMap(toPromptArgument)
      )
      icons <- Fields.optionalList(fields, Prompt.IconsKey) { v =>
        Fields.asObject(v, Icon.IconKey).flatMap(Meta.toIcon)
      }
      meta <- Fields.optional(fields, Prompt.MetaKey)(v =>
        Fields.asObject(v, Prompt.MetaKey).map(obj => MetaObject(obj.value))
      )
    } yield Prompt(
      name = name,
      title = title,
      description = description,
      arguments = arguments,
      icons = icons,
      meta = meta
    )
  }

  def toPromptMessage(promptMessage: JsonObject): Either[DecodingError, PromptMessage] = {
    val fields = promptMessage.value
    for {
      role <- Fields.required(fields, PromptMessage.RoleKey).flatMap(Content.toRole)
      content <- Fields
        .required(fields, PromptMessage.ContentKey)
        .flatMap(v => Fields.asObject(v, PromptMessage.ContentKey).flatMap(Content.toContentBlock))
    } yield PromptMessage(role = role, content = content)
  }

  def toGetPromptRequestParams(
      params: RequestParams
  ): Either[DecodingError, GetPromptRequestParams] = {
    val fields = params.fields.value
    for {
      name <- Fields.requiredString(fields, GetPromptRequestParams.NameKey)
      arguments <- Fields.optional(fields, GetPromptRequestParams.ArgumentsKey)(v =>
        Fields
          .asObject(v, GetPromptRequestParams.ArgumentsKey)
          .flatMap(Primitives.toStringMap(_, GetPromptRequestParams.ArgumentsKey))
      )
      continuation <- Input.toContinuation(fields)
    } yield GetPromptRequestParams(
      meta = params.meta,
      name = name,
      arguments = arguments,
      inputResponses = continuation.inputResponses,
      requestState = continuation.requestState
    )
  }

  def toGetPromptRequest(message: JsonValue): Either[DecodingError, GetPromptRequest] =
    PlainRequests.toRequest(PromptMethods.get, message)(toGetPromptRequestParams) {
      (id, getPromptRequestParams, jsonrpc) =>
        GetPromptRequest(id = id, params = getPromptRequestParams, jsonrpc = jsonrpc)
    }

  def toGetPromptResult(getPromptResult: JsonObject): Either[DecodingError, GetPromptResult] = {
    val fields = getPromptResult.value
    for {
      messages <- Fields
        .required(fields, GetPromptResult.MessagesKey)
        .flatMap(
          Primitives.toList(_, GetPromptResult.MessagesKey)(v =>
            Fields.asObject(v, GetPromptResult.MessagesKey).flatMap(toPromptMessage)
          )
        )
      description <- Fields.optionalString(fields, GetPromptResult.DescriptionKey)
    } yield GetPromptResult(
      messages = messages,
      description = description
    )
  }

  def toListPromptsResult(
      listPromptsResult: JsonObject
  ): Either[DecodingError, ListPromptsResult] = {
    val fields = listPromptsResult.value
    for {
      prompts <- Fields
        .required(fields, ListPromptsResult.PromptsKey)
        .flatMap(
          Primitives.toList(_, ListPromptsResult.PromptsKey)(v =>
            Fields.asObject(v, ListPromptsResult.PromptsKey).flatMap(toPrompt)
          )
        )
      tail <- PaginatedListResults.toPaginatedTail(fields)
    } yield ListPromptsResult(
      prompts = prompts,
      ttlMs = tail.cacheable.ttlMs,
      cacheScope = tail.cacheable.cacheScope,
      nextCursor = tail.nextCursor
    )
  }

  def fromGetPromptOutcome(outcome: RequestOutcome[GetPromptResult]): JsonObject =
    Input.fromRequestOutcome(outcome)(fromGetPromptResult)

  def toGetPromptOutcome(message: JsonObject): Either[DecodingError, RequestOutcome[GetPromptResult]] =
    Input.toRequestOutcome(message)(toGetPromptResult)
}
