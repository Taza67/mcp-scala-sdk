package io.github.taza67.mcp.codec.mcp

import io.github.taza67.mcp.codec.DecodingError
import io.github.taza67.mcp.codec.Fields
import io.github.taza67.mcp.codec.Primitives
import io.github.taza67.mcp.codec.mcp.lists.PaginatedListMethods
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.json.JsonValue
import io.github.taza67.mcp.protocol.jsonrpc.Method
import io.github.taza67.mcp.protocol.mcp.Cursor
import io.github.taza67.mcp.protocol.mcp.McpRequestParams
import io.github.taza67.mcp.protocol.mcp.NotificationParams
import io.github.taza67.mcp.protocol.mcp.PaginatedRequestParams
import io.github.taza67.mcp.protocol.mcp.RequestParams
import io.github.taza67.mcp.protocol.mcp.Result
import io.github.taza67.mcp.protocol.mcp.ResultType



/** Protocol AST bridge for MCP param bags (`JsonObject` ↔ ADT), including wire `_meta`. */
private[mcp] object Params {

  def fromRequestParams(params: RequestParams): JsonObject =
    JsonObject(
      params.fields.value + (RequestParams.MetaKey -> Meta.fromRequestMeta(params.meta))
    )

  def fromCursor(cursor: Cursor): JsonString =
    JsonString(cursor.value)

  def fromPaginatedRequestParams(params: PaginatedRequestParams): JsonObject = {
    val withCursor = Fields.withOptional(
      params.fields.value,
      PaginatedRequestParams.CursorKey -> params.cursor.map(fromCursor)
    )
    JsonObject(withCursor + (RequestParams.MetaKey -> Meta.fromRequestMeta(params.meta)))
  }

  def fromMcpRequestParams(params: McpRequestParams): JsonObject =
    params match {
      case plain: RequestParams              => fromRequestParams(plain)
      case paginated: PaginatedRequestParams => fromPaginatedRequestParams(paginated)
    }

  def fromNotificationParams(params: NotificationParams): JsonObject =
    JsonObject(
      Fields.withOptional(
        params.fields.value,
        NotificationParams.MetaKey -> params.meta.map(Meta.fromNotificationMeta)
      )
    )

  def fromResultType(resultType: ResultType): JsonString =
    JsonString(resultType.value)

  def fromResult(result: Result): JsonObject = {
    val base =
      result.fields.value + (Result.ResultTypeKey -> fromResultType(result.resultType))
    JsonObject(
      Fields.withOptional(
        base,
        Result.MetaKey -> result.meta.map(Meta.fromResultMeta)
      )
    )
  }

  def toRequestParams(params: JsonObject): Either[DecodingError, RequestParams] = {
    val fields = params.value
    for {
      metaObject <- Fields.requiredObject(fields, RequestParams.MetaKey)
      meta <- Meta.toRequestMeta(metaObject)
    } yield RequestParams(
      meta = meta,
      fields = JsonObject(fields - RequestParams.MetaKey)
    )
  }

  def toCursor(cursor: JsonValue): Either[DecodingError, Cursor] =
    cursor match {
      case JsonString(s) if !s.isBlank() => Right(Cursor(s))
      case _                             => Left(DecodingError("Invalid cursor"))
    }

  def toPaginatedRequestParams(
      params: JsonObject
  ): Either[DecodingError, PaginatedRequestParams] = {
    val fields = params.value
    for {
      metaObject <- Fields.requiredObject(fields, RequestParams.MetaKey)
      meta <- Meta.toRequestMeta(metaObject)
      cursor <- Fields.optional(fields, PaginatedRequestParams.CursorKey)(toCursor)
    } yield PaginatedRequestParams(
      meta = meta,
      cursor = cursor,
      fields = JsonObject(
        fields - RequestParams.MetaKey - PaginatedRequestParams.CursorKey
      )
    )
  }

  /** Decode request `params` using the JSON-RPC [[method]] (ADR-0007). */
  def toMcpRequestParams(
      method: Method,
      params: JsonObject
  ): Either[DecodingError, McpRequestParams] =
    if (PaginatedListMethods.All.contains(method)) toPaginatedRequestParams(params)
    else if (params.value.contains(PaginatedRequestParams.CursorKey))
      Left(
        DecodingError(
          s"${PaginatedRequestParams.CursorKey} is reserved for paginated request params"
        )
      )
    else toRequestParams(params)

  def toNotificationParams(params: JsonObject): Either[DecodingError, NotificationParams] = {
    val fields = params.value
    for {
      meta <- Fields.optional(fields, NotificationParams.MetaKey)(v =>
        Fields.asObject(v, NotificationParams.MetaKey).flatMap(Meta.toNotificationMeta)
      )
    } yield NotificationParams(
      meta = meta,
      fields = JsonObject(fields - NotificationParams.MetaKey)
    )
  }

  def toResultType(resultType: JsonValue): Either[DecodingError, ResultType] =
    Primitives.asString(resultType, Result.ResultTypeKey).map(ResultType.fromValue)

  def toResult(result: JsonObject): Either[DecodingError, Result] = {
    val fields = result.value
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
