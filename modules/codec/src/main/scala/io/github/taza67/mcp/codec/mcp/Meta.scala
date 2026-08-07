package io.github.taza67.mcp.codec.mcp

import io.github.taza67.mcp.codec.DecodingError
import io.github.taza67.mcp.codec.Fields
import io.github.taza67.mcp.codec.Primitives
import io.github.taza67.mcp.protocol.json.JsonNumber
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.json.JsonValue
import io.github.taza67.mcp.protocol.mcp.Icon
import io.github.taza67.mcp.protocol.mcp.IconTheme
import io.github.taza67.mcp.protocol.mcp.Implementation
import io.github.taza67.mcp.protocol.mcp.LoggingLevel
import io.github.taza67.mcp.protocol.mcp.McpProtocolVersion
import io.github.taza67.mcp.protocol.mcp.MetaObject
import io.github.taza67.mcp.protocol.mcp.NotificationMeta
import io.github.taza67.mcp.protocol.mcp.NumberProgressToken
import io.github.taza67.mcp.protocol.mcp.ProgressToken
import io.github.taza67.mcp.protocol.mcp.RequestMeta
import io.github.taza67.mcp.protocol.mcp.ResultMeta
import io.github.taza67.mcp.protocol.mcp.StringProgressToken



/** Protocol AST bridge for MCP `_meta` values (`JsonObject` / `JsonValue` ↔ ADT).
 *
 *  Package façades: [[Messages]] for envelopes, [[Meta]] for `_meta` values.
 *  Helpers: package-private [[Capabilities]] and [[Params]].
 */
object Meta {

  def fromMcpProtocolVersion(version: McpProtocolVersion): JsonString =
    JsonString(version.value)

  def fromLoggingLevel(level: LoggingLevel): JsonString =
    JsonString(level.value)

  def fromProgressToken(token: ProgressToken): JsonValue =
    token match {
      case StringProgressToken(value) => JsonString(value)
      case NumberProgressToken(value) => JsonNumber(value)
    }

  def fromIconTheme(iconTheme: IconTheme): JsonString =
    JsonString(iconTheme.value)

  def fromIcon(icon: Icon): JsonObject = {
    val base = Map("src" -> JsonString(icon.src))
    JsonObject(
      Fields.withOptional(
        base,
        "mimeType" -> icon.mimeType.map(JsonString(_)),
        "sizes" -> icon.sizes.map(Primitives.fromList(_)(JsonString(_))),
        "theme" -> icon.theme.map(fromIconTheme)
      )
    )
  }

  def fromImplementation(implementation: Implementation): JsonObject = {
    val base = Map(
      "name" -> JsonString(implementation.name),
      "version" -> JsonString(implementation.version)
    )
    JsonObject(
      Fields.withOptional(
        base,
        "title" -> implementation.title.map(JsonString(_)),
        "description" -> implementation.description.map(JsonString(_)),
        "websiteUrl" -> implementation.websiteUrl.map(JsonString(_)),
        "icons" -> implementation.icons.map(Primitives.fromList(_)(fromIcon))
      )
    )
  }

  def fromNotificationMeta(notificationMeta: NotificationMeta): JsonObject =
    JsonObject(
      Fields.withOptional(
        notificationMeta.extensions.value -- NotificationMeta.ReservedKeys,
        NotificationMeta.SubscriptionIdKey ->
          notificationMeta.subscriptionId.map(Primitives.fromRequestId)
      )
    )

  def fromResultMeta(resultMeta: ResultMeta): JsonObject =
    JsonObject(
      Fields.withOptional(
        resultMeta.extensions.value -- ResultMeta.ReservedKeys,
        ResultMeta.ServerInfoKey -> resultMeta.serverInfo.map(fromImplementation)
      )
    )

  def fromRequestMeta(requestMeta: RequestMeta): JsonObject = {
    val base =
      (requestMeta.extensions.value -- RequestMeta.ReservedKeys) ++ Map(
        RequestMeta.ProtocolVersionKey -> fromMcpProtocolVersion(requestMeta.protocolVersion),
        RequestMeta.ClientCapabilitiesKey -> Capabilities.fromClientCapabilities(
          requestMeta.clientCapabilities
        )
      )
    JsonObject(
      Fields.withOptional(
        base,
        RequestMeta.ClientInfoKey -> requestMeta.clientInfo.map(fromImplementation),
        RequestMeta.LogLevelKey -> requestMeta.logLevel.map(fromLoggingLevel),
        RequestMeta.ProgressTokenKey -> requestMeta.progressToken.map(fromProgressToken)
      )
    )
  }

  def toMcpProtocolVersion(value: JsonValue): Either[DecodingError, McpProtocolVersion] =
    Primitives.asString(value, RequestMeta.ProtocolVersionKey).flatMap { s =>
      McpProtocolVersion
        .fromValue(s)
        .toRight(DecodingError(s"Unsupported protocol version: $s"))
    }

  def toLoggingLevel(value: JsonValue): Either[DecodingError, LoggingLevel] =
    Primitives.asString(value, RequestMeta.LogLevelKey).flatMap { s =>
      LoggingLevel
        .fromValue(s)
        .toRight(DecodingError(s"Invalid log level: $s"))
    }

  def toProgressToken(value: JsonValue): Either[DecodingError, ProgressToken] =
    Primitives.asStringOrLong(value, "progress token")(
      StringProgressToken(_),
      NumberProgressToken(_)
    )

  def toIconTheme(value: JsonValue): Either[DecodingError, IconTheme] =
    Primitives.asString(value, "theme").flatMap { s =>
      IconTheme
        .fromValue(s)
        .toRight(DecodingError(s"Invalid icon theme: $s"))
    }

  def toIcon(value: JsonObject): Either[DecodingError, Icon] = {
    val fields = value.value
    for {
      src <- Fields.requiredString(fields, "src")
      mimeType <- Fields.optionalString(fields, "mimeType")
      sizes <- Fields.optionalList(fields, "sizes")(Primitives.asString(_, "size"))
      theme <- Fields.optional(fields, "theme")(toIconTheme)
    } yield Icon(src, mimeType, sizes, theme)
  }

  def toImplementation(value: JsonObject): Either[DecodingError, Implementation] = {
    val fields = value.value
    for {
      name <- Fields.requiredString(fields, "name")
      version <- Fields.requiredString(fields, "version")
      title <- Fields.optionalString(fields, "title")
      description <- Fields.optionalString(fields, "description")
      websiteUrl <- Fields.optionalString(fields, "websiteUrl")
      icons <- Fields.optionalList(fields, "icons") { v =>
        Fields.asObject(v, "icon").flatMap(toIcon)
      }
    } yield Implementation(name, version, title, description, websiteUrl, icons)
  }

  def toNotificationMeta(value: JsonObject): Either[DecodingError, NotificationMeta] = {
    val fields = value.value
    val extensions = MetaObject(fields -- NotificationMeta.ReservedKeys)
    for {
      subscriptionId <- Fields.optional(fields, NotificationMeta.SubscriptionIdKey)(
        Primitives.toRequestId
      )
    } yield NotificationMeta(subscriptionId, extensions)
  }

  def toResultMeta(value: JsonObject): Either[DecodingError, ResultMeta] = {
    val fields = value.value
    val extensions = MetaObject(fields -- ResultMeta.ReservedKeys)
    for {
      serverInfo <- Fields.optional(fields, ResultMeta.ServerInfoKey)(v =>
        Fields.asObject(v, ResultMeta.ServerInfoKey).flatMap(toImplementation)
      )
    } yield ResultMeta(serverInfo, extensions)
  }

  def toRequestMeta(value: JsonObject): Either[DecodingError, RequestMeta] = {
    val fields = value.value
    val extensions = MetaObject(fields -- RequestMeta.ReservedKeys)
    for {
      protocolVersion <- Fields
        .required(fields, RequestMeta.ProtocolVersionKey)
        .flatMap(toMcpProtocolVersion)
      clientCapabilities <- Fields
        .requiredObject(fields, RequestMeta.ClientCapabilitiesKey)
        .flatMap(Capabilities.toClientCapabilities)
      clientInfo <- Fields.optional(fields, RequestMeta.ClientInfoKey)(v =>
        Fields.asObject(v, RequestMeta.ClientInfoKey).flatMap(toImplementation)
      )
      logLevel <- Fields.optional(fields, RequestMeta.LogLevelKey)(toLoggingLevel)
      progressToken <- Fields.optional(fields, RequestMeta.ProgressTokenKey)(toProgressToken)
    } yield RequestMeta(
      protocolVersion = protocolVersion,
      clientCapabilities = clientCapabilities,
      clientInfo = clientInfo,
      logLevel = logLevel,
      progressToken = progressToken,
      extensions = extensions
    )
  }
}
