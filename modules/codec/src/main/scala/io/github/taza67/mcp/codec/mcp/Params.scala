package io.github.taza67.mcp.codec.mcp

import io.github.taza67.mcp.codec.DecodingError
import io.github.taza67.mcp.codec.Fields
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.mcp.NotificationParams
import io.github.taza67.mcp.protocol.mcp.RequestParams
import io.github.taza67.mcp.protocol.mcp.Result
import io.github.taza67.mcp.protocol.mcp.ResultType



/** Protocol AST bridge for MCP param bags (`JsonObject` ↔ ADT), including wire `_meta`. */
private[mcp] object Params {

  def fromRequestParams(params: RequestParams): JsonObject =
    JsonObject(
      params.fields.value + (RequestParams.MetaKey -> Meta.fromRequestMeta(params.meta))
    )

  def fromNotificationParams(params: NotificationParams): JsonObject =
    JsonObject(
      Fields.withOptional(
        params.fields.value,
        NotificationParams.MetaKey -> params.meta.map(Meta.fromNotificationMeta)
      )
    )

  def fromResult(result: Result): JsonObject = {
    val base =
      result.fields.value + (Result.ResultTypeKey -> JsonString(result.resultType.value))
    JsonObject(
      Fields.withOptional(
        base,
        Result.MetaKey -> result.meta.map(Meta.fromResultMeta)
      )
    )
  }

  def toRequestParams(value: JsonObject): Either[DecodingError, RequestParams] = {
    val fields = value.value
    for {
      metaObject <- Fields.requiredObject(fields, RequestParams.MetaKey)
      meta <- Meta.toRequestMeta(metaObject)
    } yield RequestParams(
      meta = meta,
      fields = JsonObject(fields - RequestParams.MetaKey)
    )
  }

  def toNotificationParams(value: JsonObject): Either[DecodingError, NotificationParams] = {
    val fields = value.value
    for {
      meta <- Fields.optional(fields, NotificationParams.MetaKey)(v =>
        Fields.asObject(v, NotificationParams.MetaKey).flatMap(Meta.toNotificationMeta)
      )
    } yield NotificationParams(
      meta = meta,
      fields = JsonObject(fields - NotificationParams.MetaKey)
    )
  }

  def toResult(value: JsonObject): Either[DecodingError, Result] = {
    val fields = value.value
    for {
      resultType <- Fields.optionalString(fields, Result.ResultTypeKey).map(ResultType.fromWire)
      meta <- Fields.optional(fields, Result.MetaKey)(v =>
        Fields.asObject(v, Result.MetaKey).flatMap(Meta.toResultMeta)
      )
    } yield Result(
      resultType = resultType,
      fields = JsonObject(fields - Result.ResultTypeKey - Result.MetaKey),
      meta = meta
    )
  }
}
