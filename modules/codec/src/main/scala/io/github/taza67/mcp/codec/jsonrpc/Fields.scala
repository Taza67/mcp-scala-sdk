package io.github.taza67.mcp.codec.jsonrpc

import io.github.taza67.mcp.codec.DecodingError
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonValue



/** Helpers for reading optional / required keys from a JSON object map. */
private[jsonrpc] object Fields {

  def withOptional(
      base: Map[String, JsonValue],
      key: String,
      option: Option[JsonValue]
  ): Map[String, JsonValue] =
    base ++ option.map(v => Map(key -> v)).getOrElse(Map.empty)

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
      case _             => Left(DecodingError(s"Invalid $label: expected object"))
    }

  def requiredObject(
      fields: Map[String, JsonValue],
      key: String
  ): Either[DecodingError, JsonObject] =
    required(fields, key).flatMap(asObject(_, key))

  def optionalObject(
      fields: Map[String, JsonValue],
      key: String
  ): Either[DecodingError, Option[JsonObject]] =
    fields.get(key) match {
      case None    => Right(None)
      case Some(v) => asObject(v, key).map(Some(_))
    }

  /** Optional field that may be any JSON value (e.g. `error.data`). */
  def optionalValue(
      fields: Map[String, JsonValue],
      key: String
  ): Either[DecodingError, Option[JsonValue]] =
    Right(fields.get(key))
}
