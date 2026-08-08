package io.github.taza67.mcp.codec.mcp

import io.github.taza67.mcp.codec.DecodingError
import io.github.taza67.mcp.codec.Fields
import io.github.taza67.mcp.codec.mcp.elicitation.{Elicitation => ElicitationCodec}
import io.github.taza67.mcp.codec.mcp.roots.{Roots => RootsCodec}
import io.github.taza67.mcp.codec.mcp.sampling.{Sampling => SamplingCodec}
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.json.JsonValue
import io.github.taza67.mcp.protocol.mcp.CustomInputResponse
import io.github.taza67.mcp.protocol.mcp.ElicitationInputResponse
import io.github.taza67.mcp.protocol.mcp.InputRequiredResult
import io.github.taza67.mcp.protocol.mcp.InputResponse
import io.github.taza67.mcp.protocol.mcp.InputResponses
import io.github.taza67.mcp.protocol.mcp.RootsInputResponse
import io.github.taza67.mcp.protocol.mcp.SamplingInputResponse
import io.github.taza67.mcp.protocol.mcp.elicitation.ElicitResult
import io.github.taza67.mcp.protocol.mcp.roots.ListRootsResult
import io.github.taza67.mcp.protocol.mcp.sampling.CreateMessageResult



/** Continuation fields shared by rich request params (`inputResponses`, `requestState`). */
private[mcp] case class ContinuationFields(
    inputResponses: Option[InputResponses] = None,
    requestState: Option[String] = None
)

/** Shared continuation-field helpers for rich request params.
 *
 *  [[InputResponse]] values are classified by key presence: elicitation (`action`),
 *  roots (`roots`), sampling (`model`+`role`), or opaque [[CustomInputResponse]].
 *  Map entries must be JSON objects.
 */
private[mcp] object Input {

  def fromInputResponse(inputResponse: InputResponse): JsonValue =
    inputResponse match {
      case CustomInputResponse(value)       => value
      case ElicitationInputResponse(result) => ElicitationCodec.fromElicitResult(result)
      case RootsInputResponse(result)       => RootsCodec.fromListRootsResult(result)
      case SamplingInputResponse(result)    => SamplingCodec.fromCreateMessageResult(result)
    }

  def fromInputResponses(inputResponses: InputResponses): JsonObject =
    JsonObject(inputResponses.value.map { case (key, response) =>
      key -> fromInputResponse(response)
    })

  /** Encode continuation fields as optional entries for [[Fields.withOptional]]. */
  def fromContinuation(
      continuation: ContinuationFields
  ): Seq[(String, Option[JsonValue])] =
    Seq(
      InputResponses.InputResponsesKey -> continuation.inputResponses.map(fromInputResponses),
      InputRequiredResult.RequestStateKey -> continuation.requestState.map(JsonString(_))
    )

  def toInputResponse(inputResponse: JsonObject): Either[DecodingError, InputResponse] = {
    val fields = inputResponse.value
    if (fields.contains(ElicitResult.ActionKey))
      ElicitationCodec.toElicitResult(inputResponse).map(ElicitationInputResponse(_))
    else if (fields.contains(ListRootsResult.RootsKey))
      RootsCodec.toListRootsResult(inputResponse).map(RootsInputResponse(_))
    else if (
      fields.contains(CreateMessageResult.ModelKey) &&
      fields.contains(CreateMessageResult.RoleKey)
    )
      SamplingCodec.toCreateMessageResult(inputResponse).map(SamplingInputResponse(_))
    else
      Right(CustomInputResponse(inputResponse))
  }

  def toInputResponses(
      inputResponses: JsonObject
  ): Either[DecodingError, InputResponses] =
    inputResponses.value
      .foldLeft[Either[DecodingError, List[(String, InputResponse)]]](Right(Nil)) {
        case (acc, (key, value)) =>
          for {
            entries <- acc
            obj <- Fields.asObject(value, s"${InputResponses.InputResponsesKey}.$key")
            response <- toInputResponse(obj)
          } yield (key -> response) :: entries
      }
      .map(entries => InputResponses(entries.reverse.toMap))

  def toContinuation(
      fields: Map[String, JsonValue]
  ): Either[DecodingError, ContinuationFields] =
    for {
      inputResponses <- Fields.optional(fields, InputResponses.InputResponsesKey)(v =>
        Fields
          .asObject(v, InputResponses.InputResponsesKey)
          .flatMap(toInputResponses)
      )
      requestState <- Fields.optionalString(fields, InputRequiredResult.RequestStateKey)
    } yield ContinuationFields(inputResponses, requestState)
}
