package io.github.taza67.mcp.codec.mcp.subscriptions

import io.github.taza67.mcp.codec.DecodingError
import io.github.taza67.mcp.codec.Fields
import io.github.taza67.mcp.codec.Primitives
import io.github.taza67.mcp.codec.jsonrpc.{Messages => JsonRpcMessages}
import io.github.taza67.mcp.codec.mcp.Meta
import io.github.taza67.mcp.codec.mcp.Params
import io.github.taza67.mcp.codec.mcp.PlainNotifications
import io.github.taza67.mcp.codec.mcp.PlainRequests
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.json.JsonValue
import io.github.taza67.mcp.protocol.jsonrpc.SuccessResponse
import io.github.taza67.mcp.protocol.mcp.MetaObject
import io.github.taza67.mcp.protocol.mcp.NotificationParams
import io.github.taza67.mcp.protocol.mcp.RequestParams
import io.github.taza67.mcp.protocol.mcp.Result
import io.github.taza67.mcp.protocol.mcp.ResultMeta
import io.github.taza67.mcp.protocol.mcp.ResultType
import io.github.taza67.mcp.protocol.mcp.subscriptions.{Subscriptions => SubscriptionMethods}
import io.github.taza67.mcp.protocol.mcp.subscriptions.SubscriptionFilter
import io.github.taza67.mcp.protocol.mcp.subscriptions.SubscriptionsAcknowledgedNotification
import io.github.taza67.mcp.protocol.mcp.subscriptions.SubscriptionsAcknowledgedNotificationParams
import io.github.taza67.mcp.protocol.mcp.subscriptions.SubscriptionsListenRequest
import io.github.taza67.mcp.protocol.mcp.subscriptions.SubscriptionsListenRequestParams
import io.github.taza67.mcp.protocol.mcp.subscriptions.SubscriptionsListenResult
import io.github.taza67.mcp.protocol.mcp.subscriptions.SubscriptionsListenResultMeta
import io.github.taza67.mcp.protocol.mcp.subscriptions.SubscriptionsListenResultResponse



/** Protocol AST bridge for MCP subscriptions-domain types (`JsonObject` to/from ADT). */
object Subscriptions {

  def fromSubscriptionFilter(filter: SubscriptionFilter): JsonObject =
    JsonObject(
      Fields.withOptional(
        Map.empty,
        SubscriptionFilter.ToolsListChangedKey ->
          filter.toolsListChanged.map(Primitives.fromBool),
        SubscriptionFilter.PromptsListChangedKey ->
          filter.promptsListChanged.map(Primitives.fromBool),
        SubscriptionFilter.ResourcesListChangedKey ->
          filter.resourcesListChanged.map(Primitives.fromBool),
        SubscriptionFilter.ResourceSubscriptionsKey ->
          filter.resourceSubscriptions.map(Primitives.fromList(_)(JsonString(_)))
      )
    )

  def toSubscriptionFilter(
      filter: JsonObject
  ): Either[DecodingError, SubscriptionFilter] = {
    val fields = filter.value
    for {
      toolsListChanged <- Fields.optionalBool(
        fields,
        SubscriptionFilter.ToolsListChangedKey
      )
      promptsListChanged <- Fields.optionalBool(
        fields,
        SubscriptionFilter.PromptsListChangedKey
      )
      resourcesListChanged <- Fields.optionalBool(
        fields,
        SubscriptionFilter.ResourcesListChangedKey
      )
      resourceSubscriptions <- Fields.optionalList(
        fields,
        SubscriptionFilter.ResourceSubscriptionsKey
      )(Primitives.asString(_, SubscriptionFilter.ResourceSubscriptionsKey))
    } yield SubscriptionFilter(
      toolsListChanged = toolsListChanged,
      promptsListChanged = promptsListChanged,
      resourcesListChanged = resourcesListChanged,
      resourceSubscriptions = resourceSubscriptions
    )
  }

  def fromSubscriptionsListenRequestParams(
      params: SubscriptionsListenRequestParams
  ): RequestParams =
    RequestParams(
      meta = params.meta,
      fields = JsonObject(
        Map(
          SubscriptionMethods.NotificationsKey ->
            fromSubscriptionFilter(params.notifications)
        )
      )
    )

  def toSubscriptionsListenRequestParams(
      params: RequestParams
  ): Either[DecodingError, SubscriptionsListenRequestParams] =
    Fields
      .requiredObject(params.fields.value, SubscriptionMethods.NotificationsKey)
      .flatMap(toSubscriptionFilter)
      .map(filter =>
        SubscriptionsListenRequestParams(meta = params.meta, notifications = filter)
      )

  def fromSubscriptionsListenRequest(
      request: SubscriptionsListenRequest
  ): JsonObject =
    PlainRequests.fromRequest(
      SubscriptionMethods.listen,
      request.id,
      fromSubscriptionsListenRequestParams(request.params),
      request.jsonrpc
    )

  def toSubscriptionsListenRequest(
      message: JsonValue
  ): Either[DecodingError, SubscriptionsListenRequest] =
    PlainRequests.toRequest(SubscriptionMethods.listen, message)(
      toSubscriptionsListenRequestParams
    ) { (id, params, jsonrpc) =>
      SubscriptionsListenRequest(id = id, params = params, jsonrpc = jsonrpc)
    }

  def fromSubscriptionsAcknowledgedNotificationParams(
      params: SubscriptionsAcknowledgedNotificationParams
  ): NotificationParams =
    NotificationParams(
      meta = params.meta,
      fields = JsonObject(
        Map(
          SubscriptionMethods.NotificationsKey ->
            fromSubscriptionFilter(params.notifications)
        )
      )
    )

  def toSubscriptionsAcknowledgedNotificationParams(
      params: NotificationParams
  ): Either[DecodingError, SubscriptionsAcknowledgedNotificationParams] =
    Fields
      .requiredObject(params.fields.value, SubscriptionMethods.NotificationsKey)
      .flatMap(toSubscriptionFilter)
      .map(filter =>
        SubscriptionsAcknowledgedNotificationParams(
          notifications = filter,
          meta = params.meta
        )
      )

  def fromSubscriptionsAcknowledgedNotification(
      notification: SubscriptionsAcknowledgedNotification
  ): JsonObject =
    PlainNotifications.fromNotification(
      method = SubscriptionMethods.acknowledgedNotification,
      params = fromSubscriptionsAcknowledgedNotificationParams(notification.params),
      jsonrpc = notification.jsonrpc
    )

  def toSubscriptionsAcknowledgedNotification(
      message: JsonValue
  ): Either[DecodingError, SubscriptionsAcknowledgedNotification] =
    PlainNotifications.toNotification(
      SubscriptionMethods.acknowledgedNotification,
      message
    )(toSubscriptionsAcknowledgedNotificationParams) { (params, jsonrpc) =>
      SubscriptionsAcknowledgedNotification(params = params, jsonrpc = jsonrpc)
    }

  def fromSubscriptionsListenResultMeta(
      meta: SubscriptionsListenResultMeta
  ): JsonObject = {
    val sanitized = ResultMeta(
      serverInfo = meta.serverInfo,
      extensions = MetaObject(
        meta.extensions.value -- SubscriptionsListenResultMeta.ReservedKeys
      )
    )
    JsonObject(
      Meta.fromResultMeta(sanitized).value +
        (SubscriptionsListenResultMeta.SubscriptionIdKey ->
          Primitives.fromRequestId(meta.subscriptionId))
    )
  }

  def toSubscriptionsListenResultMeta(
      meta: JsonObject
  ): Either[DecodingError, SubscriptionsListenResultMeta] =
    for {
      resultMeta <- Meta.toResultMeta(meta)
      subscriptionId <- Fields
        .required(meta.value, SubscriptionsListenResultMeta.SubscriptionIdKey)
        .flatMap(Primitives.toRequestId)
    } yield SubscriptionsListenResultMeta(
      subscriptionId = subscriptionId,
      serverInfo = resultMeta.serverInfo,
      extensions = MetaObject(
        resultMeta.extensions.value - SubscriptionsListenResultMeta.SubscriptionIdKey
      )
    )

  /** Encodes a [[SubscriptionsListenResult]] as the full JSON-RPC `result`
   *  object, including `resultType` and the required `_meta` member
   *  (same convention as `Input.fromInputRequiredResult`).
   */
  def fromSubscriptionsListenResult(
      result: SubscriptionsListenResult
  ): JsonObject =
    JsonObject(
      Map(
        Result.ResultTypeKey -> Params.fromResultType(result.resultType),
        Result.MetaKey -> fromSubscriptionsListenResultMeta(result.meta)
      )
    )

  /** Decodes a full JSON-RPC `result` object (with `resultType` / `_meta`),
   *  not a pre-stripped `Result.fields` bag.
   */
  def toSubscriptionsListenResult(
      result: JsonObject
  ): Either[DecodingError, SubscriptionsListenResult] = {
    val fields = result.value
    for {
      resultType <- Fields
        .optionalString(fields, Result.ResultTypeKey)
        .map(ResultType.fromWire)
      meta <- Fields
        .requiredObject(fields, Result.MetaKey)
        .flatMap(toSubscriptionsListenResultMeta)
    } yield SubscriptionsListenResult(meta = meta, resultType = resultType)
  }

  def fromSubscriptionsListenResultResponse(
      response: SubscriptionsListenResultResponse
  ): JsonObject = {
    require(
      response.id == response.result.meta.subscriptionId,
      "subscription response id must match the result subscriptionId"
    )
    JsonRpcMessages.fromMessage(
      SuccessResponse(
        result = fromSubscriptionsListenResult(response.result),
        id = response.id,
        jsonrpc = response.jsonrpc
      )
    )
  }

  def toSubscriptionsListenResultResponse(
      message: JsonValue
  ): Either[DecodingError, SubscriptionsListenResultResponse] =
    JsonRpcMessages.toMessage(message).flatMap {
      case response: SuccessResponse =>
        for {
          resultObject <- Fields.asObject(response.result, "result")
          result <- toSubscriptionsListenResult(resultObject)
          _ <- Either.cond(
            response.id == result.meta.subscriptionId,
            (),
            DecodingError("Invalid subscription response correlation")
          )
        } yield SubscriptionsListenResultResponse(
          result = result,
          id = response.id,
          jsonrpc = response.jsonrpc
        )
      case _ =>
        Left(DecodingError("Expected a subscriptions/listen result response"))
    }
}
