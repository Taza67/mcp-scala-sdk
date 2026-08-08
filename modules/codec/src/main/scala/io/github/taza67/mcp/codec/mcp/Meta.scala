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



/** Protocol AST bridge for MCP `_meta` values (`JsonObject` / `JsonValue` ↔ ADT). */
object Meta {

  def fromNotificationMeta(notificationMeta: NotificationMeta): JsonObject =
    JsonObject(
      Fields.withOptional(
        notificationMeta.extensions.value -- NotificationMeta.ReservedKeys,
        NotificationMeta.SubscriptionIdKey ->
          notificationMeta.subscriptionId.map(Primitives.fromRequestId)
      )
    )

  def fromIconTheme(iconTheme: IconTheme): JsonString =
    JsonString(iconTheme.value)

  def fromIcon(icon: Icon): JsonObject = {
    val base = Map(Icon.SrcKey -> JsonString(icon.src))
    JsonObject(
      Fields.withOptional(
        base,
        Icon.MimeTypeKey -> icon.mimeType.map(JsonString(_)),
        Icon.SizesKey -> icon.sizes.map(Primitives.fromList(_)(JsonString(_))),
        Icon.ThemeKey -> icon.theme.map(fromIconTheme)
      )
    )
  }

  def fromImplementation(implementation: Implementation): JsonObject = {
    val base = Map(
      Implementation.NameKey -> JsonString(implementation.name),
      Implementation.VersionKey -> JsonString(implementation.version)
    )
    JsonObject(
      Fields.withOptional(
        base,
        Implementation.TitleKey -> implementation.title.map(JsonString(_)),
        Implementation.DescriptionKey -> implementation.description.map(JsonString(_)),
        Implementation.WebsiteUrlKey -> implementation.websiteUrl.map(JsonString(_)),
        Implementation.IconsKey -> implementation.icons.map(Primitives.fromList(_)(fromIcon))
      )
    )
  }

  def fromResultMeta(resultMeta: ResultMeta): JsonObject =
    JsonObject(
      Fields.withOptional(
        resultMeta.extensions.value -- ResultMeta.ReservedKeys,
        ResultMeta.ServerInfoKey -> resultMeta.serverInfo.map(fromImplementation)
      )
    )

  def fromMcpProtocolVersion(version: McpProtocolVersion): JsonString =
    JsonString(version.value)

  /** @deprecated Deprecated as of protocol version 2026-07-28 (SEP-2577). Remains for at least twelve months. */
  def fromLoggingLevel(level: LoggingLevel): JsonString =
    JsonString(level.value)

  def fromProgressToken(token: ProgressToken): JsonValue =
    token match {
      case StringProgressToken(value) => JsonString(value)
      case NumberProgressToken(value) => JsonNumber(value)
    }

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

  def toNotificationMeta(notificationMeta: JsonObject): Either[DecodingError, NotificationMeta] = {
    val fields = notificationMeta.value
    val extensions = MetaObject(fields -- NotificationMeta.ReservedKeys)
    for {
      subscriptionId <- Fields.optional(fields, NotificationMeta.SubscriptionIdKey)(
        Primitives.toRequestId
      )
    } yield NotificationMeta(subscriptionId, extensions)
  }

  def toIconTheme(iconTheme: JsonValue): Either[DecodingError, IconTheme] =
    Primitives.asString(iconTheme, Icon.ThemeKey).flatMap { s =>
      IconTheme
        .fromValue(s)
        .toRight(DecodingError(s"Invalid ${Icon.ThemeKey}: $s"))
    }

  def toIcon(icon: JsonObject): Either[DecodingError, Icon] = {
    val fields = icon.value
    for {
      src <- Fields.requiredString(fields, Icon.SrcKey)
      mimeType <- Fields.optionalString(fields, Icon.MimeTypeKey)
      sizes <- Fields.optionalList(fields, Icon.SizesKey)(Primitives.asString(_, Icon.SizesKey))
      theme <- Fields.optional(fields, Icon.ThemeKey)(toIconTheme)
    } yield Icon(src, mimeType, sizes, theme)
  }

  def toImplementation(implementation: JsonObject): Either[DecodingError, Implementation] = {
    val fields = implementation.value
    for {
      name <- Fields.requiredString(fields, Implementation.NameKey)
      version <- Fields.requiredString(fields, Implementation.VersionKey)
      title <- Fields.optionalString(fields, Implementation.TitleKey)
      description <- Fields.optionalString(fields, Implementation.DescriptionKey)
      websiteUrl <- Fields.optionalString(fields, Implementation.WebsiteUrlKey)
      icons <- Fields.optionalList(fields, Implementation.IconsKey) { v =>
        Fields.asObject(v, Icon.IconKey).flatMap(toIcon)
      }
    } yield Implementation(name, version, title, description, websiteUrl, icons)
  }

  def toResultMeta(resultMeta: JsonObject): Either[DecodingError, ResultMeta] = {
    val fields = resultMeta.value
    val extensions = MetaObject(fields -- ResultMeta.ReservedKeys)
    for {
      serverInfo <- Fields.optional(fields, ResultMeta.ServerInfoKey)(v =>
        Fields.asObject(v, ResultMeta.ServerInfoKey).flatMap(toImplementation)
      )
    } yield ResultMeta(serverInfo, extensions)
  }

  def toMcpProtocolVersion(version: JsonValue): Either[DecodingError, McpProtocolVersion] =
    Primitives.asString(version, RequestMeta.ProtocolVersionKey).flatMap { s =>
      McpProtocolVersion
        .fromValue(s)
        .toRight(DecodingError(s"Invalid ${RequestMeta.ProtocolVersionKey}: $s"))
    }

  /** @deprecated Deprecated as of protocol version 2026-07-28 (SEP-2577). Remains for at least twelve months. */
  def toLoggingLevel(level: JsonValue): Either[DecodingError, LoggingLevel] =
    Primitives.asString(level, RequestMeta.LogLevelKey).flatMap { s =>
      LoggingLevel
        .fromValue(s)
        .toRight(DecodingError(s"Invalid ${RequestMeta.LogLevelKey}: $s"))
    }

  def toProgressToken(token: JsonValue): Either[DecodingError, ProgressToken] =
    Primitives.asStringOrLong(token, "progress token")(
      StringProgressToken(_),
      NumberProgressToken(_)
    )

  def toRequestMeta(requestMeta: JsonObject): Either[DecodingError, RequestMeta] = {
    val fields = requestMeta.value
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
