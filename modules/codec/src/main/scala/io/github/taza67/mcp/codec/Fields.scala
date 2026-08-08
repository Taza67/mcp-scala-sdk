package io.github.taza67.mcp.codec

import io.github.taza67.mcp.codec.DecodingError
import io.github.taza67.mcp.protocol.json.JsonArray
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonValue



/** Helpers for reading optional / required keys from a JSON object map. */
private[codec] object Fields {

  /** Merge zero or more optional entries into `base` (absent options leave the map unchanged). */
  def withOptional(
      base: Map[String, JsonValue],
      options: (String, Option[JsonValue])*
  ): Map[String, JsonValue] =
    options.foldLeft(base) {
      case (acc, (key, Some(value))) => acc + (key -> value)
      case (acc, _)                  => acc
    }

  def required(
      fields: Map[String, JsonValue],
      key: String
  ): Either[DecodingError, JsonValue] =
    fields.get(key) match {
      case Some(value) => Right(value)
      case None        => Left(DecodingError(s"Missing $key"))
    }

  def asObject(
      value: JsonValue,
      label: String
  ): Either[DecodingError, JsonObject] =
    value match {
      case o: JsonObject => Right(o)
      case _             => Left(DecodingError(s"Invalid $label: expected an object"))
    }

  /** Widen `Map[String, JsonObject]` to AST (`Map` is invariant in the value type). */
  def fromObjectMap(entries: Map[String, JsonObject]): JsonObject =
    JsonObject(entries.map { case (key, value) => key -> (value: JsonValue) })

  /** Decode a JSON object whose values must each be objects (fail on non-objects). */
  def toObjectMap(
      obj: JsonObject,
      label: String
  ): Either[DecodingError, Map[String, JsonObject]] =
    obj.value
      .foldLeft[Either[DecodingError, List[(String, JsonObject)]]](Right(Nil)) {
        case (acc, (key, value)) =>
          for {
            entries <- acc
            nested <- asObject(value, s"$label.$key")
          } yield (key -> nested) :: entries
      }
      .map(_.reverse.toMap)

  def requiredObject(
      fields: Map[String, JsonValue],
      key: String
  ): Either[DecodingError, JsonObject] =
    required(fields, key).flatMap(asObject(_, key))

  def requiredString(
      fields: Map[String, JsonValue],
      key: String
  ): Either[DecodingError, String] =
    required(fields, key).flatMap(Primitives.asString(_, key))

  def optionalObject(
      fields: Map[String, JsonValue],
      key: String
  ): Either[DecodingError, Option[JsonObject]] =
    optional(fields, key)(asObject(_, key))

  def optionalArray(
      fields: Map[String, JsonValue],
      key: String
  ): Either[DecodingError, Option[JsonArray]] =
    optional(fields, key)(Primitives.asArray(_, key))

  def optionalList[A](
      fields: Map[String, JsonValue],
      key: String
  )(f: JsonValue => Either[DecodingError, A]): Either[DecodingError, Option[List[A]]] =
    optional(fields, key)(Primitives.toList(_, key)(f))

  def optionalString(
      fields: Map[String, JsonValue],
      key: String
  ): Either[DecodingError, Option[String]] =
    optional(fields, key)(Primitives.asString(_, key))

  def optionalBool(
      fields: Map[String, JsonValue],
      key: String
  ): Either[DecodingError, Option[Boolean]] =
    optional(fields, key)(Primitives.asBool(_, key))

  def optionalInt(
      fields: Map[String, JsonValue],
      key: String
  ): Either[DecodingError, Option[Int]] =
    optional(fields, key)(Primitives.asInt(_, key))

  /** Optional field that may be any JSON value (e.g. `error.data`). */
  def optionalValue(
      fields: Map[String, JsonValue],
      key: String
  ): Either[DecodingError, Option[JsonValue]] =
    Right(fields.get(key))

  /** Optional field decoded with `f` when present. */
  def optional[A](
      fields: Map[String, JsonValue],
      key: String
  )(f: JsonValue => Either[DecodingError, A]): Either[DecodingError, Option[A]] =
    optionalValue(fields, key).flatMap(traverseOptional(_)(f))

  /** Cats-free `Option.traverse`: apply `f` only when the value is present. */
  def traverseOptional[A, B](
      option: Option[A]
  )(f: A => Either[DecodingError, B]): Either[DecodingError, Option[B]] =
    option match {
      case None    => Right(None)
      case Some(a) => f(a).map(Some(_))
    }

  /** Fail unless `actual` equals `expected` (e.g. fixed wire `type` values). */
  def requireEquals[A](
      actual: A,
      expected: A,
      key: String
  ): Either[DecodingError, Unit] =
    if (actual == expected) Right(())
    else Left(DecodingError(s"Invalid $key: expected $expected"))

  /** Fail unless at least one of the optional fields is present. */
  def requireAtLeastOne(
      options: (String, Option[_])*
  ): Either[DecodingError, Unit] = {
    val present = options.foldLeft(false) { case (acc, (_, value)) =>
      acc || value.isDefined
    }
    if (present) Right(())
    else {
      val keys = options.map(_._1).mkString(" or ")
      Left(DecodingError(s"At least one of $keys must be present"))
    }
  }
}
