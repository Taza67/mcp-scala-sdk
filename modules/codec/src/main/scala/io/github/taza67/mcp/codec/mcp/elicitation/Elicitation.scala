package io.github.taza67.mcp.codec.mcp.elicitation

import io.github.taza67.mcp.codec.DecodingError
import io.github.taza67.mcp.codec.Fields
import io.github.taza67.mcp.codec.mcp.ObjectRequests
import io.github.taza67.mcp.codec.Primitives
import io.github.taza67.mcp.protocol.json.JsonArray
import io.github.taza67.mcp.protocol.json.JsonBool
import io.github.taza67.mcp.protocol.json.JsonNumber
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.json.JsonValue
import io.github.taza67.mcp.protocol.mcp.elicitation.BooleanSchema
import io.github.taza67.mcp.protocol.mcp.elicitation.ElicitAction
import io.github.taza67.mcp.protocol.mcp.elicitation.ElicitBooleanValue
import io.github.taza67.mcp.protocol.mcp.elicitation.ElicitContentValue
import io.github.taza67.mcp.protocol.mcp.elicitation.ElicitNumberValue
import io.github.taza67.mcp.protocol.mcp.elicitation.ElicitRequestedSchema
import io.github.taza67.mcp.protocol.mcp.elicitation.ElicitRequest
import io.github.taza67.mcp.protocol.mcp.elicitation.{Elicitation => ElicitationMethods}
import io.github.taza67.mcp.protocol.mcp.elicitation.ElicitRequestFormParams
import io.github.taza67.mcp.protocol.mcp.elicitation.ElicitRequestParams
import io.github.taza67.mcp.protocol.mcp.elicitation.ElicitRequestUrlParams
import io.github.taza67.mcp.protocol.mcp.elicitation.ElicitResult
import io.github.taza67.mcp.protocol.mcp.elicitation.ElicitStringListValue
import io.github.taza67.mcp.protocol.mcp.elicitation.ElicitStringValue
import io.github.taza67.mcp.protocol.mcp.elicitation.EnumOption
import io.github.taza67.mcp.protocol.mcp.elicitation.LegacyTitledEnumSchema
import io.github.taza67.mcp.protocol.mcp.elicitation.NumberSchema
import io.github.taza67.mcp.protocol.mcp.elicitation.NumberSchemaKind
import io.github.taza67.mcp.protocol.mcp.elicitation.PrimitiveSchema
import io.github.taza67.mcp.protocol.mcp.elicitation.StringFormat
import io.github.taza67.mcp.protocol.mcp.elicitation.StringSchema
import io.github.taza67.mcp.protocol.mcp.elicitation.TitledMultiSelectEnumSchema
import io.github.taza67.mcp.protocol.mcp.elicitation.TitledSingleSelectEnumSchema
import io.github.taza67.mcp.protocol.mcp.elicitation.UntitledMultiSelectEnumSchema
import io.github.taza67.mcp.protocol.mcp.elicitation.UntitledSingleSelectEnumSchema



/** Protocol AST bridge for MCP elicitation-domain types (`JsonObject` ↔ ADT). */
object Elicitation {

  def fromEnumOption(enumOption: EnumOption): JsonObject =
    JsonObject(
      Map(
        EnumOption.ConstKey -> JsonString(enumOption.const),
        EnumOption.TitleKey -> JsonString(enumOption.title)
      )
    )

  def fromStringFormat(stringFormat: StringFormat): JsonString =
    JsonString(stringFormat.value)

  def fromBooleanSchema(booleanSchema: BooleanSchema): JsonObject = {
    val base = Map(PrimitiveSchema.TypeKey -> JsonString(BooleanSchema.TypeValue))
    JsonObject(
      Fields.withOptional(
        base,
        PrimitiveSchema.TitleKey -> booleanSchema.title.map(JsonString(_)),
        PrimitiveSchema.DescriptionKey -> booleanSchema.description.map(JsonString(_)),
        PrimitiveSchema.DefaultKey -> booleanSchema.default.map(Primitives.fromBool)
      )
    )
  }

  def fromStringSchema(stringSchema: StringSchema): JsonObject = {
    val base = Map(PrimitiveSchema.TypeKey -> JsonString(StringSchema.TypeValue))
    JsonObject(
      Fields.withOptional(
        base,
        PrimitiveSchema.TitleKey -> stringSchema.title.map(JsonString(_)),
        PrimitiveSchema.DescriptionKey -> stringSchema.description.map(JsonString(_)),
        StringSchema.MinLengthKey -> stringSchema.minLength.map(n => JsonNumber(n)),
        StringSchema.MaxLengthKey -> stringSchema.maxLength.map(n => JsonNumber(n)),
        StringSchema.FormatKey -> stringSchema.format.map(fromStringFormat),
        PrimitiveSchema.DefaultKey -> stringSchema.default.map(JsonString(_))
      )
    )
  }

  def fromNumberSchema(numberSchema: NumberSchema): JsonObject = {
    val base = Map(PrimitiveSchema.TypeKey -> JsonString(numberSchema.kind.value))
    JsonObject(
      Fields.withOptional(
        base,
        PrimitiveSchema.TitleKey -> numberSchema.title.map(JsonString(_)),
        PrimitiveSchema.DescriptionKey -> numberSchema.description.map(JsonString(_)),
        NumberSchema.MinimumKey -> numberSchema.minimum.map(JsonNumber(_)),
        NumberSchema.MaximumKey -> numberSchema.maximum.map(JsonNumber(_)),
        PrimitiveSchema.DefaultKey -> numberSchema.default.map(JsonNumber(_))
      )
    )
  }

  def fromUntitledSingleSelectEnumSchema(
      untitledSingleSelectEnumSchema: UntitledSingleSelectEnumSchema
  ): JsonObject = {
    val base = Map(
      PrimitiveSchema.TypeKey -> JsonString(UntitledSingleSelectEnumSchema.TypeValue),
      UntitledSingleSelectEnumSchema.EnumKey ->
        Primitives.fromList(untitledSingleSelectEnumSchema.values)(JsonString(_))
    )
    JsonObject(
      Fields.withOptional(
        base,
        PrimitiveSchema.TitleKey -> untitledSingleSelectEnumSchema.title.map(JsonString(_)),
        PrimitiveSchema.DescriptionKey ->
          untitledSingleSelectEnumSchema.description.map(JsonString(_)),
        PrimitiveSchema.DefaultKey -> untitledSingleSelectEnumSchema.default.map(JsonString(_))
      )
    )
  }

  def fromTitledSingleSelectEnumSchema(
      titledSingleSelectEnumSchema: TitledSingleSelectEnumSchema
  ): JsonObject = {
    val base = Map(
      PrimitiveSchema.TypeKey -> JsonString(TitledSingleSelectEnumSchema.TypeValue),
      TitledSingleSelectEnumSchema.OneOfKey ->
        Primitives.fromList(titledSingleSelectEnumSchema.oneOf)(fromEnumOption)
    )
    JsonObject(
      Fields.withOptional(
        base,
        PrimitiveSchema.TitleKey -> titledSingleSelectEnumSchema.title.map(JsonString(_)),
        PrimitiveSchema.DescriptionKey ->
          titledSingleSelectEnumSchema.description.map(JsonString(_)),
        PrimitiveSchema.DefaultKey -> titledSingleSelectEnumSchema.default.map(JsonString(_))
      )
    )
  }

  def fromUntitledMultiSelectEnumSchema(
      untitledMultiSelectEnumSchema: UntitledMultiSelectEnumSchema
  ): JsonObject = {
    val items = JsonObject(
      Map(
        PrimitiveSchema.TypeKey -> JsonString(StringSchema.TypeValue),
        UntitledSingleSelectEnumSchema.EnumKey ->
          Primitives.fromList(untitledMultiSelectEnumSchema.values)(JsonString(_))
      )
    )
    val base = Map(
      PrimitiveSchema.TypeKey -> JsonString(UntitledMultiSelectEnumSchema.TypeValue),
      UntitledMultiSelectEnumSchema.ItemsKey -> items
    )
    JsonObject(
      Fields.withOptional(
        base,
        PrimitiveSchema.TitleKey -> untitledMultiSelectEnumSchema.title.map(JsonString(_)),
        PrimitiveSchema.DescriptionKey ->
          untitledMultiSelectEnumSchema.description.map(JsonString(_)),
        UntitledMultiSelectEnumSchema.MinItemsKey ->
          untitledMultiSelectEnumSchema.minItems.map(n => JsonNumber(n)),
        UntitledMultiSelectEnumSchema.MaxItemsKey ->
          untitledMultiSelectEnumSchema.maxItems.map(n => JsonNumber(n)),
        PrimitiveSchema.DefaultKey -> untitledMultiSelectEnumSchema.default.map(
          Primitives.fromList(_)(JsonString(_))
        )
      )
    )
  }

  def fromTitledMultiSelectEnumSchema(
      titledMultiSelectEnumSchema: TitledMultiSelectEnumSchema
  ): JsonObject = {
    val items = JsonObject(
      Map(
        TitledMultiSelectEnumSchema.AnyOfKey ->
          Primitives.fromList(titledMultiSelectEnumSchema.anyOf)(fromEnumOption)
      )
    )
    val base = Map(
      PrimitiveSchema.TypeKey -> JsonString(TitledMultiSelectEnumSchema.TypeValue),
      UntitledMultiSelectEnumSchema.ItemsKey -> items
    )
    JsonObject(
      Fields.withOptional(
        base,
        PrimitiveSchema.TitleKey -> titledMultiSelectEnumSchema.title.map(JsonString(_)),
        PrimitiveSchema.DescriptionKey ->
          titledMultiSelectEnumSchema.description.map(JsonString(_)),
        UntitledMultiSelectEnumSchema.MinItemsKey ->
          titledMultiSelectEnumSchema.minItems.map(n => JsonNumber(n)),
        UntitledMultiSelectEnumSchema.MaxItemsKey ->
          titledMultiSelectEnumSchema.maxItems.map(n => JsonNumber(n)),
        PrimitiveSchema.DefaultKey -> titledMultiSelectEnumSchema.default.map(
          Primitives.fromList(_)(JsonString(_))
        )
      )
    )
  }

  def fromLegacyTitledEnumSchema(
      legacyTitledEnumSchema: LegacyTitledEnumSchema
  ): JsonObject = {
    val base = Map(
      PrimitiveSchema.TypeKey -> JsonString(LegacyTitledEnumSchema.TypeValue),
      UntitledSingleSelectEnumSchema.EnumKey ->
        Primitives.fromList(legacyTitledEnumSchema.values)(JsonString(_))
    )
    JsonObject(
      Fields.withOptional(
        base,
        PrimitiveSchema.TitleKey -> legacyTitledEnumSchema.title.map(JsonString(_)),
        PrimitiveSchema.DescriptionKey -> legacyTitledEnumSchema.description.map(JsonString(_)),
        LegacyTitledEnumSchema.EnumNamesKey -> legacyTitledEnumSchema.enumNames.map(
          Primitives.fromList(_)(JsonString(_))
        ),
        PrimitiveSchema.DefaultKey -> legacyTitledEnumSchema.default.map(JsonString(_))
      )
    )
  }

  def fromPrimitiveSchema(primitiveSchema: PrimitiveSchema): JsonObject =
    primitiveSchema match {
      case s: BooleanSchema                  => fromBooleanSchema(s)
      case s: StringSchema                   => fromStringSchema(s)
      case s: NumberSchema                   => fromNumberSchema(s)
      case s: UntitledSingleSelectEnumSchema => fromUntitledSingleSelectEnumSchema(s)
      case s: TitledSingleSelectEnumSchema   => fromTitledSingleSelectEnumSchema(s)
      case s: UntitledMultiSelectEnumSchema  => fromUntitledMultiSelectEnumSchema(s)
      case s: TitledMultiSelectEnumSchema    => fromTitledMultiSelectEnumSchema(s)
      case s: LegacyTitledEnumSchema         => fromLegacyTitledEnumSchema(s)
    }

  def fromElicitRequestedSchema(elicitRequestedSchema: ElicitRequestedSchema): JsonObject = {
    val base = Map(
      ElicitRequestedSchema.TypeKey -> JsonString(ElicitRequestedSchema.TypeValue),
      ElicitRequestedSchema.PropertiesKey -> JsonObject(
        elicitRequestedSchema.properties.map { case (key, schema) =>
          key -> fromPrimitiveSchema(schema)
        }
      )
    )
    JsonObject(
      Fields.withOptional(
        base,
        ElicitRequestedSchema.SchemaKey -> elicitRequestedSchema.schema.map(JsonString(_)),
        ElicitRequestedSchema.RequiredKey -> elicitRequestedSchema.required.map(
          Primitives.fromList(_)(JsonString(_))
        )
      )
    )
  }

  def fromElicitRequestFormParams(
      elicitRequestFormParams: ElicitRequestFormParams
  ): JsonObject = {
    val base = Map(
      ElicitRequestParams.MessageKey -> JsonString(elicitRequestFormParams.message),
      ElicitRequestFormParams.RequestedSchemaKey ->
        fromElicitRequestedSchema(elicitRequestFormParams.requestedSchema)
    )
    JsonObject(
      Fields.withOptional(
        base,
        ElicitRequestParams.ModeKey -> elicitRequestFormParams.mode.map(JsonString(_))
      )
    )
  }

  def fromElicitRequestUrlParams(
      elicitRequestUrlParams: ElicitRequestUrlParams
  ): JsonObject =
    JsonObject(
      Map(
        ElicitRequestParams.MessageKey -> JsonString(elicitRequestUrlParams.message),
        ElicitRequestParams.ModeKey -> JsonString(elicitRequestUrlParams.mode),
        ElicitRequestUrlParams.UrlKey -> JsonString(elicitRequestUrlParams.url)
      )
    )

  def fromElicitRequestParams(elicitRequestParams: ElicitRequestParams): JsonObject =
    elicitRequestParams match {
      case form: ElicitRequestFormParams => fromElicitRequestFormParams(form)
      case url: ElicitRequestUrlParams   => fromElicitRequestUrlParams(url)
    }

  def fromElicitRequest(elicitRequest: ElicitRequest): JsonObject =
    ObjectRequests.fromRequest(
      ElicitationMethods.create,
      elicitRequest.id,
      fromElicitRequestParams(elicitRequest.params),
      elicitRequest.jsonrpc
    )

  def fromElicitContentValue(elicitContentValue: ElicitContentValue): JsonValue =
    elicitContentValue match {
      case ElicitStringValue(value)     => JsonString(value)
      case ElicitNumberValue(value)     => JsonNumber(value)
      case ElicitBooleanValue(value)    => Primitives.fromBool(value)
      case ElicitStringListValue(value) => Primitives.fromList(value)(JsonString(_))
    }

  def fromElicitResult(elicitResult: ElicitResult): JsonObject = {
    val base = Map(ElicitResult.ActionKey -> JsonString(elicitResult.action.value))
    JsonObject(
      Fields.withOptional(
        base,
        ElicitResult.ContentKey -> elicitResult.content.map(entries =>
          JsonObject(entries.map { case (key, value) => key -> fromElicitContentValue(value) })
        )
      )
    )
  }

  def toEnumOption(enumOption: JsonObject): Either[DecodingError, EnumOption] = {
    val fields = enumOption.value
    for {
      const <- Fields.requiredString(fields, EnumOption.ConstKey)
      title <- Fields.requiredString(fields, EnumOption.TitleKey)
    } yield EnumOption(const = const, title = title)
  }

  def toStringFormat(stringFormat: JsonValue): Either[DecodingError, StringFormat] =
    Primitives.asString(stringFormat, StringSchema.FormatKey).flatMap { s =>
      StringFormat.fromValue(s).toRight(DecodingError(s"Invalid ${StringSchema.FormatKey}: $s"))
    }

  private def optionalNumber(
      fields: Map[String, JsonValue],
      key: String
  ): Either[DecodingError, Option[BigDecimal]] =
    Fields.optional(fields, key) {
      case JsonNumber(n) => Right(n)
      case _             => Left(DecodingError(s"Invalid $key: expected a number"))
    }

  private def optionalStringListDefault(
      fields: Map[String, JsonValue]
  ): Either[DecodingError, Option[List[String]]] =
    Fields.optional(fields, PrimitiveSchema.DefaultKey)(
      Primitives.toList(_, PrimitiveSchema.DefaultKey)(
        Primitives.asString(_, PrimitiveSchema.DefaultKey)
      )
    )

  def toBooleanSchema(booleanSchema: JsonObject): Either[DecodingError, BooleanSchema] = {
    val fields = booleanSchema.value
    for {
      title <- Fields.optionalString(fields, PrimitiveSchema.TitleKey)
      description <- Fields.optionalString(fields, PrimitiveSchema.DescriptionKey)
      default <- Fields.optionalBool(fields, PrimitiveSchema.DefaultKey)
    } yield BooleanSchema(title = title, description = description, default = default)
  }

  def toStringSchema(stringSchema: JsonObject): Either[DecodingError, StringSchema] = {
    val fields = stringSchema.value
    for {
      title <- Fields.optionalString(fields, PrimitiveSchema.TitleKey)
      description <- Fields.optionalString(fields, PrimitiveSchema.DescriptionKey)
      minLength <- Fields.optionalInt(fields, StringSchema.MinLengthKey)
      maxLength <- Fields.optionalInt(fields, StringSchema.MaxLengthKey)
      format <- Fields.optional(fields, StringSchema.FormatKey)(toStringFormat)
      default <- Fields.optionalString(fields, PrimitiveSchema.DefaultKey)
    } yield StringSchema(
      title = title,
      description = description,
      minLength = minLength,
      maxLength = maxLength,
      format = format,
      default = default
    )
  }

  def toNumberSchema(numberSchema: JsonObject): Either[DecodingError, NumberSchema] = {
    val fields = numberSchema.value
    for {
      typeValue <- Fields.requiredString(fields, PrimitiveSchema.TypeKey)
      kind <- NumberSchemaKind
        .fromValue(typeValue)
        .toRight(DecodingError(s"Invalid ${PrimitiveSchema.TypeKey}: $typeValue"))
      title <- Fields.optionalString(fields, PrimitiveSchema.TitleKey)
      description <- Fields.optionalString(fields, PrimitiveSchema.DescriptionKey)
      minimum <- optionalNumber(fields, NumberSchema.MinimumKey)
      maximum <- optionalNumber(fields, NumberSchema.MaximumKey)
      default <- optionalNumber(fields, PrimitiveSchema.DefaultKey)
    } yield NumberSchema(
      kind = kind,
      title = title,
      description = description,
      minimum = minimum,
      maximum = maximum,
      default = default
    )
  }

  def toUntitledSingleSelectEnumSchema(
      untitledSingleSelectEnumSchema: JsonObject
  ): Either[DecodingError, UntitledSingleSelectEnumSchema] = {
    val fields = untitledSingleSelectEnumSchema.value
    for {
      values <- Fields
        .required(fields, UntitledSingleSelectEnumSchema.EnumKey)
        .flatMap(
          Primitives.toList(_, UntitledSingleSelectEnumSchema.EnumKey)(
            Primitives.asString(_, UntitledSingleSelectEnumSchema.EnumKey)
          )
        )
      title <- Fields.optionalString(fields, PrimitiveSchema.TitleKey)
      description <- Fields.optionalString(fields, PrimitiveSchema.DescriptionKey)
      default <- Fields.optionalString(fields, PrimitiveSchema.DefaultKey)
    } yield UntitledSingleSelectEnumSchema(
      values = values,
      title = title,
      description = description,
      default = default
    )
  }

  def toTitledSingleSelectEnumSchema(
      titledSingleSelectEnumSchema: JsonObject
  ): Either[DecodingError, TitledSingleSelectEnumSchema] = {
    val fields = titledSingleSelectEnumSchema.value
    for {
      oneOf <- Fields
        .required(fields, TitledSingleSelectEnumSchema.OneOfKey)
        .flatMap(
          Primitives.toList(_, TitledSingleSelectEnumSchema.OneOfKey)(v =>
            Fields.asObject(v, TitledSingleSelectEnumSchema.OneOfKey).flatMap(toEnumOption)
          )
        )
      title <- Fields.optionalString(fields, PrimitiveSchema.TitleKey)
      description <- Fields.optionalString(fields, PrimitiveSchema.DescriptionKey)
      default <- Fields.optionalString(fields, PrimitiveSchema.DefaultKey)
    } yield TitledSingleSelectEnumSchema(
      oneOf = oneOf,
      title = title,
      description = description,
      default = default
    )
  }

  def toUntitledMultiSelectEnumSchema(
      untitledMultiSelectEnumSchema: JsonObject
  ): Either[DecodingError, UntitledMultiSelectEnumSchema] = {
    val fields = untitledMultiSelectEnumSchema.value
    for {
      items <- Fields.requiredObject(fields, UntitledMultiSelectEnumSchema.ItemsKey)
      values <- Fields
        .required(items.value, UntitledSingleSelectEnumSchema.EnumKey)
        .flatMap(
          Primitives.toList(_, UntitledSingleSelectEnumSchema.EnumKey)(
            Primitives.asString(_, UntitledSingleSelectEnumSchema.EnumKey)
          )
        )
      title <- Fields.optionalString(fields, PrimitiveSchema.TitleKey)
      description <- Fields.optionalString(fields, PrimitiveSchema.DescriptionKey)
      minItems <- Fields.optionalInt(fields, UntitledMultiSelectEnumSchema.MinItemsKey)
      maxItems <- Fields.optionalInt(fields, UntitledMultiSelectEnumSchema.MaxItemsKey)
      default <- optionalStringListDefault(fields)
    } yield UntitledMultiSelectEnumSchema(
      values = values,
      title = title,
      description = description,
      minItems = minItems,
      maxItems = maxItems,
      default = default
    )
  }

  def toTitledMultiSelectEnumSchema(
      titledMultiSelectEnumSchema: JsonObject
  ): Either[DecodingError, TitledMultiSelectEnumSchema] = {
    val fields = titledMultiSelectEnumSchema.value
    for {
      items <- Fields.requiredObject(fields, UntitledMultiSelectEnumSchema.ItemsKey)
      anyOf <- Fields
        .required(items.value, TitledMultiSelectEnumSchema.AnyOfKey)
        .flatMap(
          Primitives.toList(_, TitledMultiSelectEnumSchema.AnyOfKey)(v =>
            Fields.asObject(v, TitledMultiSelectEnumSchema.AnyOfKey).flatMap(toEnumOption)
          )
        )
      title <- Fields.optionalString(fields, PrimitiveSchema.TitleKey)
      description <- Fields.optionalString(fields, PrimitiveSchema.DescriptionKey)
      minItems <- Fields.optionalInt(fields, UntitledMultiSelectEnumSchema.MinItemsKey)
      maxItems <- Fields.optionalInt(fields, UntitledMultiSelectEnumSchema.MaxItemsKey)
      default <- optionalStringListDefault(fields)
    } yield TitledMultiSelectEnumSchema(
      anyOf = anyOf,
      title = title,
      description = description,
      minItems = minItems,
      maxItems = maxItems,
      default = default
    )
  }

  def toLegacyTitledEnumSchema(
      legacyTitledEnumSchema: JsonObject
  ): Either[DecodingError, LegacyTitledEnumSchema] = {
    val fields = legacyTitledEnumSchema.value
    for {
      values <- Fields
        .required(fields, UntitledSingleSelectEnumSchema.EnumKey)
        .flatMap(
          Primitives.toList(_, UntitledSingleSelectEnumSchema.EnumKey)(
            Primitives.asString(_, UntitledSingleSelectEnumSchema.EnumKey)
          )
        )
      title <- Fields.optionalString(fields, PrimitiveSchema.TitleKey)
      description <- Fields.optionalString(fields, PrimitiveSchema.DescriptionKey)
      enumNames <- Fields.optionalList(fields, LegacyTitledEnumSchema.EnumNamesKey)(
        Primitives.asString(_, LegacyTitledEnumSchema.EnumNamesKey)
      )
      default <- Fields.optionalString(fields, PrimitiveSchema.DefaultKey)
    } yield LegacyTitledEnumSchema(
      values = values,
      title = title,
      description = description,
      enumNames = enumNames,
      default = default
    )
  }

  def toPrimitiveSchema(
      primitiveSchema: JsonObject
  ): Either[DecodingError, PrimitiveSchema] = {
    val fields = primitiveSchema.value
    Fields.requiredString(fields, PrimitiveSchema.TypeKey).flatMap {
      case BooleanSchema.TypeValue => toBooleanSchema(primitiveSchema)
      case typeValue if NumberSchemaKind.fromValue(typeValue).isDefined =>
        toNumberSchema(primitiveSchema)
      case UntitledMultiSelectEnumSchema.TypeValue =>
        Fields.requiredObject(fields, UntitledMultiSelectEnumSchema.ItemsKey).flatMap { items =>
          if (items.value.contains(TitledMultiSelectEnumSchema.AnyOfKey))
            toTitledMultiSelectEnumSchema(primitiveSchema)
          else if (items.value.contains(UntitledSingleSelectEnumSchema.EnumKey))
            toUntitledMultiSelectEnumSchema(primitiveSchema)
          else
            Left(
              DecodingError(
                s"Invalid multi-select schema: expected ${UntitledSingleSelectEnumSchema.EnumKey} or ${TitledMultiSelectEnumSchema.AnyOfKey} in ${UntitledMultiSelectEnumSchema.ItemsKey}"
              )
            )
        }
      case StringSchema.TypeValue =>
        if (fields.contains(TitledSingleSelectEnumSchema.OneOfKey))
          toTitledSingleSelectEnumSchema(primitiveSchema)
        else if (fields.contains(LegacyTitledEnumSchema.EnumNamesKey))
          toLegacyTitledEnumSchema(primitiveSchema)
        else if (fields.contains(UntitledSingleSelectEnumSchema.EnumKey))
          toUntitledSingleSelectEnumSchema(primitiveSchema)
        else
          toStringSchema(primitiveSchema)
      case other =>
        Left(DecodingError(s"Invalid ${PrimitiveSchema.TypeKey}: $other"))
    }
  }

  private def toProperties(
      properties: JsonObject
  ): Either[DecodingError, Map[String, PrimitiveSchema]] =
    properties.value
      .foldLeft[Either[DecodingError, List[(String, PrimitiveSchema)]]](Right(Nil)) {
        case (acc, (key, value)) =>
          for {
            entries <- acc
            obj <- Fields.asObject(value, s"${ElicitRequestedSchema.PropertiesKey}.$key")
            schema <- toPrimitiveSchema(obj)
          } yield (key -> schema) :: entries
      }
      .map(_.reverse.toMap)

  def toElicitRequestedSchema(
      elicitRequestedSchema: JsonObject
  ): Either[DecodingError, ElicitRequestedSchema] = {
    val fields = elicitRequestedSchema.value
    for {
      typeValue <- Fields.requiredString(fields, ElicitRequestedSchema.TypeKey)
      _ <- Fields.requireEquals(
        typeValue,
        ElicitRequestedSchema.TypeValue,
        ElicitRequestedSchema.TypeKey
      )
      properties <- Fields
        .requiredObject(fields, ElicitRequestedSchema.PropertiesKey)
        .flatMap(toProperties)
      required <- Fields.optionalList(fields, ElicitRequestedSchema.RequiredKey)(
        Primitives.asString(_, ElicitRequestedSchema.RequiredKey)
      )
      schema <- Fields.optionalString(fields, ElicitRequestedSchema.SchemaKey)
    } yield ElicitRequestedSchema(
      properties = properties,
      required = required,
      schema = schema
    )
  }

  def toElicitRequestFormParams(
      elicitRequestFormParams: JsonObject
  ): Either[DecodingError, ElicitRequestFormParams] = {
    val fields = elicitRequestFormParams.value
    for {
      message <- Fields.requiredString(fields, ElicitRequestParams.MessageKey)
      requestedSchema <- Fields
        .requiredObject(fields, ElicitRequestFormParams.RequestedSchemaKey)
        .flatMap(toElicitRequestedSchema)
      mode <- Fields.optionalString(fields, ElicitRequestParams.ModeKey)
    } yield ElicitRequestFormParams(
      message = message,
      requestedSchema = requestedSchema,
      mode = ElicitRequestFormParams.modeFromWire(mode)
    )
  }

  def toElicitRequestUrlParams(
      elicitRequestUrlParams: JsonObject
  ): Either[DecodingError, ElicitRequestUrlParams] = {
    val fields = elicitRequestUrlParams.value
    for {
      message <- Fields.requiredString(fields, ElicitRequestParams.MessageKey)
      url <- Fields.requiredString(fields, ElicitRequestUrlParams.UrlKey)
    } yield ElicitRequestUrlParams(message = message, url = url)
  }

  def toElicitRequestParams(
      elicitRequestParams: JsonObject
  ): Either[DecodingError, ElicitRequestParams] = {
    val fields = elicitRequestParams.value
    val mode = fields.get(ElicitRequestParams.ModeKey).collect { case JsonString(s) => s }
    if (ElicitRequestParams.isUrlMode(mode, fields.contains(ElicitRequestUrlParams.UrlKey)))
      toElicitRequestUrlParams(elicitRequestParams)
    else
      toElicitRequestFormParams(elicitRequestParams)
  }

  def toElicitRequest(message: JsonValue): Either[DecodingError, ElicitRequest] =
    ObjectRequests.toRequest(ElicitationMethods.create, message)(toElicitRequestParams) {
      (id, elicitRequestParams, jsonrpc) =>
        ElicitRequest(id = id, params = elicitRequestParams, jsonrpc = jsonrpc)
    }

  def toElicitContentValue(
      elicitContentValue: JsonValue,
      label: String = ElicitResult.ContentKey
  ): Either[DecodingError, ElicitContentValue] =
    elicitContentValue match {
      case JsonString(s) => Right(ElicitStringValue(s))
      case JsonNumber(n) => Right(ElicitNumberValue(n))
      case JsonBool(b)   => Right(ElicitBooleanValue(b))
      case a: JsonArray =>
        Primitives
          .toList(a, label)(Primitives.asString(_, label))
          .map(ElicitStringListValue(_))
      case _ =>
        Left(
          DecodingError(
            s"Invalid $label: expected string, number, boolean, or string array"
          )
        )
    }

  def toElicitAction(elicitAction: JsonValue): Either[DecodingError, ElicitAction] =
    Primitives.asString(elicitAction, ElicitResult.ActionKey).flatMap { s =>
      ElicitAction.fromValue(s).toRight(DecodingError(s"Invalid ${ElicitResult.ActionKey}: $s"))
    }

  private def toElicitContent(
      content: JsonObject
  ): Either[DecodingError, Map[String, ElicitContentValue]] =
    content.value
      .foldLeft[Either[DecodingError, List[(String, ElicitContentValue)]]](Right(Nil)) {
        case (acc, (key, value)) =>
          for {
            entries <- acc
            decoded <- toElicitContentValue(value, s"${ElicitResult.ContentKey}.$key")
          } yield (key -> decoded) :: entries
      }
      .map(_.reverse.toMap)

  def toElicitResult(elicitResult: JsonObject): Either[DecodingError, ElicitResult] = {
    val fields = elicitResult.value
    for {
      action <- Fields.required(fields, ElicitResult.ActionKey).flatMap(toElicitAction)
      content <- Fields.optional(fields, ElicitResult.ContentKey)(v =>
        Fields.asObject(v, ElicitResult.ContentKey).flatMap(toElicitContent)
      )
    } yield ElicitResult(action = action, content = content)
  }
}
