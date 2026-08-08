package io.github.taza67.mcp.protocol.mcp.elicitation

/** Restricted primitive field schemas for form elicitation (no nested objects/arrays). */
sealed trait PrimitiveSchema {
  def title: Option[String]
  def description: Option[String]
}

object PrimitiveSchema {

  /** Wire key for the JSON Schema `type`. */
  val TypeKey: String = "type"

  /** Wire key for optional field title. */
  val TitleKey: String = "title"

  /** Wire key for optional field description. */
  val DescriptionKey: String = "description"

  /** Wire key for optional default value. */
  val DefaultKey: String = "default"
}

/** Boolean form field. */
case class BooleanSchema(
    title: Option[String] = None,
    description: Option[String] = None,
    default: Option[Boolean] = None
) extends PrimitiveSchema

object BooleanSchema {

  /** Wire `type` value for boolean fields. */
  val TypeValue: String = "boolean"
}

/** String format hints for [[StringSchema]]. */
sealed trait StringFormat {
  def value: String
}

object StringFormat {

  /** Classify a wire string-format hint. */
  def fromValue(value: String): Option[StringFormat] =
    value match {
      case UriStringFormat.value      => Some(UriStringFormat)
      case EmailStringFormat.value    => Some(EmailStringFormat)
      case DateStringFormat.value     => Some(DateStringFormat)
      case DateTimeStringFormat.value => Some(DateTimeStringFormat)
      case _                          => None
    }
}

case object UriStringFormat extends StringFormat { val value = "uri" }
case object EmailStringFormat extends StringFormat { val value = "email" }
case object DateStringFormat extends StringFormat { val value = "date" }
case object DateTimeStringFormat extends StringFormat { val value = "date-time" }

/** String form field. */
case class StringSchema(
    title: Option[String] = None,
    description: Option[String] = None,
    minLength: Option[Int] = None,
    maxLength: Option[Int] = None,
    format: Option[StringFormat] = None,
    default: Option[String] = None
) extends PrimitiveSchema

object StringSchema {

  /** Wire `type` value for string fields. */
  val TypeValue: String = "string"

  /** Wire key for optional minimum length. */
  val MinLengthKey: String = "minLength"

  /** Wire key for optional maximum length. */
  val MaxLengthKey: String = "maxLength"

  /** Wire key for optional format hint. */
  val FormatKey: String = "format"
}

/** Numeric form field (`number` or `integer`). */
sealed trait NumberSchemaKind {
  def value: String
}

object NumberSchemaKind {

  /** Classify a wire numeric schema `type`. */
  def fromValue(value: String): Option[NumberSchemaKind] =
    value match {
      case NumberKind.value  => Some(NumberKind)
      case IntegerKind.value => Some(IntegerKind)
      case _                 => None
    }
}

case object NumberKind extends NumberSchemaKind { val value = "number" }
case object IntegerKind extends NumberSchemaKind { val value = "integer" }

case class NumberSchema(
    kind: NumberSchemaKind = NumberKind,
    title: Option[String] = None,
    description: Option[String] = None,
    minimum: Option[BigDecimal] = None,
    maximum: Option[BigDecimal] = None,
    default: Option[BigDecimal] = None
) extends PrimitiveSchema

object NumberSchema {

  /** Wire key for optional minimum. */
  val MinimumKey: String = "minimum"

  /** Wire key for optional maximum. */
  val MaximumKey: String = "maximum"
}

/** Enumeration form field (single-select, multi-select, or legacy titled). */
sealed trait EnumSchema extends PrimitiveSchema

/** Single-select enum without per-option titles. */
case class UntitledSingleSelectEnumSchema(
    values: List[String],
    title: Option[String] = None,
    description: Option[String] = None,
    default: Option[String] = None
) extends EnumSchema

object UntitledSingleSelectEnumSchema {

  /** Wire `type` value for untitled single-select enums. */
  val TypeValue: String = StringSchema.TypeValue

  /** Wire key for enum values. */
  val EnumKey: String = "enum"
}

/** Labeled option for titled enums (`const` + display `title`). */
case class EnumOption(
    const: String,
    title: String
)

object EnumOption {

  /** Wire key for the option value. */
  val ConstKey: String = "const"

  /** Wire key for the option display title. */
  val TitleKey: String = "title"
}

/** Single-select enum with display titles per option. */
case class TitledSingleSelectEnumSchema(
    oneOf: List[EnumOption],
    title: Option[String] = None,
    description: Option[String] = None,
    default: Option[String] = None
) extends EnumSchema

object TitledSingleSelectEnumSchema {

  /** Wire `type` value for titled single-select enums. */
  val TypeValue: String = StringSchema.TypeValue

  /** Wire key for titled options. */
  val OneOfKey: String = "oneOf"
}

/** Multi-select enum without per-option titles. */
case class UntitledMultiSelectEnumSchema(
    values: List[String],
    title: Option[String] = None,
    description: Option[String] = None,
    minItems: Option[Int] = None,
    maxItems: Option[Int] = None,
    default: Option[List[String]] = None
) extends EnumSchema

object UntitledMultiSelectEnumSchema {

  /** Wire `type` value for multi-select enums. */
  val TypeValue: String = "array"

  /** Wire key for array item schema. */
  val ItemsKey: String = "items"

  /** Wire key for optional minimum selection count. */
  val MinItemsKey: String = "minItems"

  /** Wire key for optional maximum selection count. */
  val MaxItemsKey: String = "maxItems"
}

/** Multi-select enum with display titles per option. */
case class TitledMultiSelectEnumSchema(
    anyOf: List[EnumOption],
    title: Option[String] = None,
    description: Option[String] = None,
    minItems: Option[Int] = None,
    maxItems: Option[Int] = None,
    default: Option[List[String]] = None
) extends EnumSchema

object TitledMultiSelectEnumSchema {

  /** Wire `type` value for titled multi-select enums. */
  val TypeValue: String = UntitledMultiSelectEnumSchema.TypeValue

  /** Wire key for titled options inside `items`. */
  val AnyOfKey: String = "anyOf"
}

/** Legacy titled single-select enum (`enum` + `enumNames`).
 *
 *  Prefer [[TitledSingleSelectEnumSchema]]. Will be removed in a future revision.
 */
case class LegacyTitledEnumSchema(
    values: List[String],
    title: Option[String] = None,
    description: Option[String] = None,
    enumNames: Option[List[String]] = None,
    default: Option[String] = None
) extends EnumSchema

object LegacyTitledEnumSchema {

  /** Wire `type` value for legacy titled enums. */
  val TypeValue: String = StringSchema.TypeValue

  /** Wire key for legacy display names. */
  val EnumNamesKey: String = "enumNames"
}

/** Restricted object schema for form elicitation: top-level primitives only.
 *
 *  @param properties Field name to primitive schema.
 *  @param required Required field names.
 *  @param schema Optional `$schema` URI.
 */
case class ElicitRequestedSchema(
    properties: Map[String, PrimitiveSchema],
    required: Option[List[String]] = None,
    schema: Option[String] = None
)

object ElicitRequestedSchema {

  /** Wire key for optional `$schema` URI. */
  val SchemaKey: String = "$schema"

  /** Wire key for the object `type`. */
  val TypeKey: String = PrimitiveSchema.TypeKey

  /** Wire `type` value for requested schemas. */
  val TypeValue: String = "object"

  /** Wire key for property schemas. */
  val PropertiesKey: String = "properties"

  /** Wire key for required property names. */
  val RequiredKey: String = "required"
}
