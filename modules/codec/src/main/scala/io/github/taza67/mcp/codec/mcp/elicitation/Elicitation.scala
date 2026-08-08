package io.github.taza67.mcp.codec.mcp.elicitation

import io.github.taza67.mcp.codec.DecodingError
import io.github.taza67.mcp.codec.Fields
import io.github.taza67.mcp.codec.Primitives
import io.github.taza67.mcp.protocol.json.JsonArray
import io.github.taza67.mcp.protocol.json.JsonBool
import io.github.taza67.mcp.protocol.json.JsonNumber
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.json.JsonValue
import io.github.taza67.mcp.protocol.mcp.elicitation.ElicitAction
import io.github.taza67.mcp.protocol.mcp.elicitation.ElicitBooleanValue
import io.github.taza67.mcp.protocol.mcp.elicitation.ElicitContentValue
import io.github.taza67.mcp.protocol.mcp.elicitation.ElicitNumberValue
import io.github.taza67.mcp.protocol.mcp.elicitation.ElicitResult
import io.github.taza67.mcp.protocol.mcp.elicitation.ElicitStringListValue
import io.github.taza67.mcp.protocol.mcp.elicitation.ElicitStringValue



/** Protocol AST bridge for MCP elicitation results (`JsonObject` ↔ ADT).
 *
 *  Currently [[ElicitResult]] / [[ElicitContentValue]].
 */
object Elicitation {

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

  def toElicitContentValue(
      elicitContentValue: JsonValue
  ): Either[DecodingError, ElicitContentValue] =
    elicitContentValue match {
      case JsonString(s) => Right(ElicitStringValue(s))
      case JsonNumber(n) => Right(ElicitNumberValue(n))
      case JsonBool(b)   => Right(ElicitBooleanValue(b))
      case a: JsonArray =>
        Primitives
          .toList(a, ElicitResult.ContentKey)(Primitives.asString(_, ElicitResult.ContentKey))
          .map(ElicitStringListValue(_))
      case _ =>
        Left(
          DecodingError(
            s"Invalid ${ElicitResult.ContentKey} value: expected string, number, boolean, or string array"
          )
        )
    }

  def toElicitAction(elicitAction: JsonValue): Either[DecodingError, ElicitAction] =
    Primitives.asString(elicitAction, ElicitResult.ActionKey).flatMap { s =>
      ElicitAction.fromValue(s).toRight(DecodingError(s"Invalid elicitation action: $s"))
    }

  private def toElicitContent(
      content: JsonObject
  ): Either[DecodingError, Map[String, ElicitContentValue]] =
    content.value
      .foldLeft[Either[DecodingError, List[(String, ElicitContentValue)]]](Right(Nil)) {
        case (acc, (key, value)) =>
          for {
            entries <- acc
            decoded <- toElicitContentValue(value)
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
