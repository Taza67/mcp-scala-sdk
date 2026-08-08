package io.github.taza67.mcp.codec.jsonrpc

import io.github.taza67.mcp.codec.DecodingError
import io.github.taza67.mcp.codec.Fields
import io.github.taza67.mcp.protocol.json.JsonNumber
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.json.JsonValue
import io.github.taza67.mcp.protocol.jsonrpc.Error
import io.github.taza67.mcp.protocol.jsonrpc.ErrorResponse
import io.github.taza67.mcp.protocol.jsonrpc.Message
import io.github.taza67.mcp.protocol.jsonrpc.Notification
import io.github.taza67.mcp.protocol.jsonrpc.Request
import io.github.taza67.mcp.protocol.jsonrpc.SuccessResponse



/** Protocol AST bridge for JSON-RPC [[Message]] envelopes (`JsonObject` ↔ ADT). */
object Messages {

  private def fromRequest(request: Request): JsonObject = {
    val base = Map(
      Message.MethodKey -> Primitives.fromMethod(request.method),
      Message.IdKey -> Primitives.fromRequestId(request.id),
      Message.JsonRpcKey -> Primitives.fromJsonRpcVersion(request.jsonrpc)
    )
    JsonObject(Fields.withOptional(base, Message.ParamsKey -> request.params))
  }

  private def fromNotification(notification: Notification): JsonObject = {
    val base =
      Map(
        Message.MethodKey -> Primitives.fromMethod(notification.method),
        Message.JsonRpcKey -> Primitives.fromJsonRpcVersion(notification.jsonrpc)
      )
    JsonObject(Fields.withOptional(base, Message.ParamsKey -> notification.params))
  }

  private def fromSuccessResponse(successResponse: SuccessResponse): JsonObject =
    JsonObject(
      Map(
        Message.ResultKey -> successResponse.result,
        Message.IdKey -> Primitives.fromRequestId(successResponse.id),
        Message.JsonRpcKey -> Primitives.fromJsonRpcVersion(successResponse.jsonrpc)
      )
    )

  private def fromError(error: Error): JsonObject = {
    val base = Map(
      Error.CodeKey -> JsonNumber(error.code),
      Error.MessageKey -> JsonString(error.message)
    )
    JsonObject(Fields.withOptional(base, Error.DataKey -> error.data))
  }

  private def fromErrorResponse(errorResponse: ErrorResponse): JsonObject =
    JsonObject(
      Map(
        Message.ErrorKey -> fromError(errorResponse.error),
        Message.IdKey -> Primitives.fromRequestId(errorResponse.id),
        Message.JsonRpcKey -> Primitives.fromJsonRpcVersion(errorResponse.jsonrpc)
      )
    )

  def fromMessage(message: Message): JsonObject =
    message match {
      case r: Request         => fromRequest(r)
      case n: Notification    => fromNotification(n)
      case s: SuccessResponse => fromSuccessResponse(s)
      case e: ErrorResponse   => fromErrorResponse(e)
    }

  def toRequest(request: JsonObject): Either[DecodingError, Request] = {
    val fields = request.value
    for {
      method <- Fields.required(fields, Message.MethodKey).flatMap(Primitives.toMethod)
      id <- Fields.required(fields, Message.IdKey).flatMap(Primitives.toRequestId)
      params <- Fields.optionalObject(fields, Message.ParamsKey)
      jsonrpc <- Fields.required(fields, Message.JsonRpcKey).flatMap(Primitives.toJsonRpcVersion)
    } yield Request(method, id, params, jsonrpc)
  }

  def toNotification(notification: JsonObject): Either[DecodingError, Notification] = {
    val fields = notification.value
    for {
      method <- Fields.required(fields, Message.MethodKey).flatMap(Primitives.toMethod)
      params <- Fields.optionalObject(fields, Message.ParamsKey)
      jsonrpc <- Fields.required(fields, Message.JsonRpcKey).flatMap(Primitives.toJsonRpcVersion)
    } yield Notification(method, params, jsonrpc)
  }

  def toSuccessResponse(successResponse: JsonObject): Either[DecodingError, SuccessResponse] = {
    val fields = successResponse.value
    for {
      result <- Fields.required(fields, Message.ResultKey)
      id <- Fields.required(fields, Message.IdKey).flatMap(Primitives.toRequestId)
      jsonrpc <- Fields.required(fields, Message.JsonRpcKey).flatMap(Primitives.toJsonRpcVersion)
    } yield SuccessResponse(result, id, jsonrpc)
  }

  def toError(error: JsonObject): Either[DecodingError, Error] = {
    val fields = error.value
    for {
      code <- Fields.required(fields, Error.CodeKey).flatMap(Primitives.toErrorCode)
      message <- Fields.required(fields, Error.MessageKey).flatMap(Primitives.toErrorMessage)
      data <- Fields.optionalValue(fields, Error.DataKey)
    } yield Error.classify(code, message, data)
  }

  def toErrorResponse(errorResponse: JsonObject): Either[DecodingError, ErrorResponse] = {
    val fields = errorResponse.value
    for {
      errorObject <- Fields.requiredObject(fields, Message.ErrorKey)
      error <- toError(errorObject)
      id <- Fields.required(fields, Message.IdKey).flatMap(Primitives.toRequestId)
      jsonrpc <- Fields.required(fields, Message.JsonRpcKey).flatMap(Primitives.toJsonRpcVersion)
    } yield ErrorResponse(error, id, jsonrpc)
  }

  def toMessage(message: JsonValue): Either[DecodingError, Message] =
    Fields.asObject(message, "JSON-RPC message").flatMap(toMessageObject)

  private def toMessageObject(message: JsonObject): Either[DecodingError, Message] = {
    val fields = message.value
    val hasMethod = fields.contains(Message.MethodKey)
    val hasId = fields.contains(Message.IdKey)
    val hasResult = fields.contains(Message.ResultKey)
    val hasError = fields.contains(Message.ErrorKey)

    (hasMethod, hasId, hasResult, hasError) match {
      case (true, true, false, false)  => toRequest(message)
      case (true, false, false, false) => toNotification(message)
      case (false, _, true, false)     => toSuccessResponse(message)
      case (false, _, false, true)     => toErrorResponse(message)
      case _ =>
        Left(DecodingError("Ambiguous or invalid JSON-RPC message shape"))
    }
  }
}
