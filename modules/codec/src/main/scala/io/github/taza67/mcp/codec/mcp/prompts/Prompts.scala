package io.github.taza67.mcp.codec.mcp.prompts

import io.github.taza67.mcp.codec.DecodingError
import io.github.taza67.mcp.codec.Fields
import io.github.taza67.mcp.codec.Primitives
import io.github.taza67.mcp.codec.mcp.ContinuationFields
import io.github.taza67.mcp.codec.mcp.Input
import io.github.taza67.mcp.codec.mcp.PlainRequests
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.json.JsonValue
import io.github.taza67.mcp.protocol.mcp.RequestParams
import io.github.taza67.mcp.protocol.mcp.prompts.{Prompts => PromptMethods}
import io.github.taza67.mcp.protocol.mcp.prompts.GetPromptRequest
import io.github.taza67.mcp.protocol.mcp.prompts.GetPromptRequestParams



/** Protocol AST bridge for MCP prompts-domain requests (`JsonObject` ↔ ADT).
 *
 *  Currently [[GetPromptRequest]] / [[GetPromptRequestParams]].
 */
object Prompts {

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
}
