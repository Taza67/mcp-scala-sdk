package io.github.taza67.mcp.protocol.mcp.elicitation

/** Restricted primitive field schemas for form elicitation (no nested objects/arrays). */
sealed trait PrimitiveSchema {
  def title: Option[String]
  def description: Option[String]
}

/** Boolean form field. */
case class BooleanSchema(
    title: Option[String] = None,
    description: Option[String] = None,
    default: Option[Boolean] = None
) extends PrimitiveSchema

/** String format hints for [[StringSchema]]. */
sealed trait StringFormat {
  def value: String
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

/** Numeric form field (`number` or `integer`). */
sealed trait NumberSchemaKind {
  def value: String
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

/** Enumeration form field (single-select, multi-select, or legacy titled). */
sealed trait EnumSchema extends PrimitiveSchema

/** Single-select enum without per-option titles. */
case class UntitledSingleSelectEnumSchema(
    values: List[String],
    title: Option[String] = None,
    description: Option[String] = None,
    default: Option[String] = None
) extends EnumSchema

/** Labeled option for titled enums (`const` + display `title`). */
case class EnumOption(
    const: String,
    title: String
)

/** Single-select enum with display titles per option. */
case class TitledSingleSelectEnumSchema(
    oneOf: List[EnumOption],
    title: Option[String] = None,
    description: Option[String] = None,
    default: Option[String] = None
) extends EnumSchema

/** Multi-select enum without per-option titles. */
case class UntitledMultiSelectEnumSchema(
    values: List[String],
    title: Option[String] = None,
    description: Option[String] = None,
    minItems: Option[Int] = None,
    maxItems: Option[Int] = None,
    default: Option[List[String]] = None
) extends EnumSchema

/** Multi-select enum with display titles per option. */
case class TitledMultiSelectEnumSchema(
    anyOf: List[EnumOption],
    title: Option[String] = None,
    description: Option[String] = None,
    minItems: Option[Int] = None,
    maxItems: Option[Int] = None,
    default: Option[List[String]] = None
) extends EnumSchema

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
