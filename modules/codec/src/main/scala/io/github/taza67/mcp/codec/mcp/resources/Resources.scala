package io.github.taza67.mcp.codec.mcp.resources

import io.github.taza67.mcp.codec.DecodingError
import io.github.taza67.mcp.codec.Fields
import io.github.taza67.mcp.codec.mcp.ContinuationFields
import io.github.taza67.mcp.codec.mcp.Input
import io.github.taza67.mcp.codec.mcp.PlainRequests
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.json.JsonValue
import io.github.taza67.mcp.protocol.mcp.RequestParams
import io.github.taza67.mcp.protocol.mcp.resources.{Resources => ResourceMethods}
import io.github.taza67.mcp.protocol.mcp.resources.ReadResourceRequest
import io.github.taza67.mcp.protocol.mcp.resources.ReadResourceRequestParams



/** Protocol AST bridge for MCP resources-domain requests (`JsonObject` ↔ ADT).
 *
 *  Currently [[ReadResourceRequest]] / [[ReadResourceRequestParams]].
 */
object Resources {

  def fromReadResourceRequestParams(
      readResourceRequestParams: ReadResourceRequestParams
  ): RequestParams = {
    val base =
      Map(ReadResourceRequestParams.UriKey -> JsonString(readResourceRequestParams.uri))
    RequestParams(
      meta = readResourceRequestParams.meta,
      fields = JsonObject(
        Fields.withOptional(
          base,
          Input.fromContinuation(
            ContinuationFields(
              inputResponses = readResourceRequestParams.inputResponses,
              requestState = readResourceRequestParams.requestState
            )
          ): _*
        )
      )
    )
  }

  def fromReadResourceRequest(readResourceRequest: ReadResourceRequest): JsonObject =
    PlainRequests.fromRequest(
      ResourceMethods.read,
      readResourceRequest.id,
      fromReadResourceRequestParams(readResourceRequest.params),
      readResourceRequest.jsonrpc
    )

  def toReadResourceRequestParams(
      params: RequestParams
  ): Either[DecodingError, ReadResourceRequestParams] = {
    val fields = params.fields.value
    for {
      uri <- Fields.requiredString(fields, ReadResourceRequestParams.UriKey)
      continuation <- Input.toContinuation(fields)
    } yield ReadResourceRequestParams(
      meta = params.meta,
      uri = uri,
      inputResponses = continuation.inputResponses,
      requestState = continuation.requestState
    )
  }

  def toReadResourceRequest(message: JsonValue): Either[DecodingError, ReadResourceRequest] =
    PlainRequests.toRequest(ResourceMethods.read, message)(toReadResourceRequestParams) {
      (id, readResourceRequestParams, jsonrpc) =>
        ReadResourceRequest(id = id, params = readResourceRequestParams, jsonrpc = jsonrpc)
    }
}
