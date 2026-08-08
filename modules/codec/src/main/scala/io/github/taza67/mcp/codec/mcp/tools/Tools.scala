package io.github.taza67.mcp.codec.mcp.tools

import io.github.taza67.mcp.codec.DecodingError
import io.github.taza67.mcp.codec.Fields
import io.github.taza67.mcp.codec.mcp.ContinuationFields
import io.github.taza67.mcp.codec.mcp.Input
import io.github.taza67.mcp.codec.mcp.PlainRequests
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.json.JsonValue
import io.github.taza67.mcp.protocol.mcp.RequestParams
import io.github.taza67.mcp.protocol.mcp.tools.{Tools => ToolMethods}
import io.github.taza67.mcp.protocol.mcp.tools.CallToolRequest
import io.github.taza67.mcp.protocol.mcp.tools.CallToolRequestParams



/** Protocol AST bridge for MCP tools-domain requests (`JsonObject` ↔ ADT).
 *
 *  Currently [[CallToolRequest]] / [[CallToolRequestParams]]. Continuation
 *  fields use shared [[Input]] helpers (typed [[InputResponse]] variants plus
 *  opaque [[CustomInputResponse]]).
 */
object Tools {

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
}
