package io.github.taza67.mcp.codec.mcp

import io.github.taza67.mcp.codec.DecodingError
import io.github.taza67.mcp.codec.Fields
import io.github.taza67.mcp.codec.Primitives
import io.github.taza67.mcp.codec.mcp.elicitation.{Elicitation => ElicitationCodec}
import io.github.taza67.mcp.codec.mcp.roots.{Roots => RootsCodec}
import io.github.taza67.mcp.codec.mcp.sampling.{Sampling => SamplingCodec}
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.json.JsonValue
import io.github.taza67.mcp.protocol.mcp.CustomInputRequest
import io.github.taza67.mcp.protocol.mcp.CustomInputResponse
import io.github.taza67.mcp.protocol.mcp.ElicitationInputRequest
import io.github.taza67.mcp.protocol.mcp.ElicitationInputResponse
import io.github.taza67.mcp.protocol.mcp.InputRequest
import io.github.taza67.mcp.protocol.mcp.InputRequests
import io.github.taza67.mcp.protocol.mcp.InputRequired
import io.github.taza67.mcp.protocol.mcp.InputRequiredResult
import io.github.taza67.mcp.protocol.mcp.InputRequiredResultType
import io.github.taza67.mcp.protocol.mcp.InputResponse
import io.github.taza67.mcp.protocol.mcp.InputResponses
import io.github.taza67.mcp.protocol.mcp.Completed
import io.github.taza67.mcp.protocol.mcp.RequestOutcome
import io.github.taza67.mcp.protocol.mcp.Result
import io.github.taza67.mcp.protocol.mcp.ResultType
import io.github.taza67.mcp.protocol.mcp.RootsInputRequest
import io.github.taza67.mcp.protocol.mcp.RootsInputResponse
import io.github.taza67.mcp.protocol.mcp.SamplingInputRequest
import io.github.taza67.mcp.protocol.mcp.SamplingInputResponse
import io.github.taza67.mcp.protocol.mcp.elicitation.Elicitation
import io.github.taza67.mcp.protocol.mcp.elicitation.ElicitResult
import io.github.taza67.mcp.protocol.mcp.roots.ListRootsResult
import io.github.taza67.mcp.protocol.mcp.roots.Roots
import io.github.taza67.mcp.protocol.mcp.sampling.CreateMessageResult
import io.github.taza67.mcp.protocol.mcp.sampling.Sampling



/** Continuation fields shared by rich request params (`inputResponses`, `requestState`). */
private[mcp] case class ContinuationFields(
    inputResponses: Option[InputResponses] = None,
    requestState: Option[String] = None
)

/** Shared continuation-field helpers for rich request params and input-required results.
 *
 *  [[InputResponse]] values are classified by key presence: elicitation (`action`),
 *  roots (`roots`), sampling (`model`+`role`), or opaque [[CustomInputResponse]].
 *  [[InputRequest]] values are classified by `method`: elicitation, sampling, roots,
 *  or opaque [[CustomInputRequest]]. Map entries must be JSON objects.
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

  def fromInputRequest(inputRequest: InputRequest): JsonObject = {
    val params = inputRequest match {
      case ElicitationInputRequest(params) =>
        Some(ElicitationCodec.fromElicitRequestParams(params))
      case SamplingInputRequest(params) =>
        Some(SamplingCodec.fromCreateMessageRequestParams(params))
      case RootsInputRequest(params) =>
        params.map(RootsCodec.fromListRootsRequestParams)
      case CustomInputRequest(_, params) => params
    }
    JsonObject(
      Fields.withOptional(
        Map(InputRequest.MethodKey -> Primitives.fromMethod(inputRequest.method)),
        InputRequest.ParamsKey -> params
      )
    )
  }

  def fromInputRequests(inputRequests: InputRequests): JsonObject =
    JsonObject(inputRequests.value.map { case (key, request) =>
      key -> fromInputRequest(request)
    })

  /** Encode continuation fields as optional entries for [[Fields.withOptional]]. */
  def fromContinuation(
      continuation: ContinuationFields
  ): Seq[(String, Option[JsonValue])] =
    Seq(
      InputResponses.InputResponsesKey -> continuation.inputResponses.map(fromInputResponses),
      InputRequiredResult.RequestStateKey -> continuation.requestState.map(JsonString(_))
    )

  def fromInputRequiredResult(inputRequiredResult: InputRequiredResult): JsonObject = {
    val base = Map(Result.ResultTypeKey -> Params.fromResultType(inputRequiredResult.resultType))
    JsonObject(
      Fields.withOptional(
        base,
        InputRequiredResult.InputRequestsKey ->
          inputRequiredResult.inputRequests.map(fromInputRequests),
        InputRequiredResult.RequestStateKey ->
          inputRequiredResult.requestState.map(JsonString(_)),
        Result.MetaKey -> inputRequiredResult.meta.map(Meta.fromResultMeta)
      )
    )
  }

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

  def toInputRequest(inputRequest: JsonObject): Either[DecodingError, InputRequest] = {
    val fields = inputRequest.value
    for {
      method <- Fields
        .required(fields, InputRequest.MethodKey)
        .flatMap(Primitives.toMethod(_, InputRequest.MethodKey))
      params <- Fields.optionalObject(fields, InputRequest.ParamsKey)
      request <- method match {
        case Elicitation.create =>
          params
            .toRight(DecodingError(s"Missing ${InputRequest.ParamsKey} for ${method.value}"))
            .flatMap(ElicitationCodec.toElicitRequestParams)
            .map(ElicitationInputRequest(_))
        case Sampling.createMessage =>
          params
            .toRight(DecodingError(s"Missing ${InputRequest.ParamsKey} for ${method.value}"))
            .flatMap(SamplingCodec.toCreateMessageRequestParams)
            .map(SamplingInputRequest(_))
        case Roots.list =>
          Fields
            .traverseOptional(params)(RootsCodec.toListRootsRequestParams)
            .map(RootsInputRequest(_))
        case other =>
          Right(CustomInputRequest(method = other, params = params))
      }
    } yield request
  }

  def toInputRequests(
      inputRequests: JsonObject
  ): Either[DecodingError, InputRequests] =
    inputRequests.value
      .foldLeft[Either[DecodingError, List[(String, InputRequest)]]](Right(Nil)) {
        case (acc, (key, value)) =>
          for {
            entries <- acc
            obj <- Fields.asObject(value, s"${InputRequiredResult.InputRequestsKey}.$key")
            request <- toInputRequest(obj)
          } yield (key -> request) :: entries
      }
      .map(entries => InputRequests(entries.reverse.toMap))

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

  def toInputRequiredResult(
      inputRequiredResult: JsonObject
  ): Either[DecodingError, InputRequiredResult] = {
    val fields = inputRequiredResult.value
    for {
      inputRequests <- Fields.optional(fields, InputRequiredResult.InputRequestsKey)(v =>
        Fields
          .asObject(v, InputRequiredResult.InputRequestsKey)
          .flatMap(toInputRequests)
      )
      requestState <- Fields.optionalString(fields, InputRequiredResult.RequestStateKey)
      _ <- Fields.requireAtLeastOne(
        InputRequiredResult.InputRequestsKey -> inputRequests,
        InputRequiredResult.RequestStateKey -> requestState
      )
      resultType <- Fields.optionalString(fields, Result.ResultTypeKey).map(ResultType.fromWire)
      meta <- Fields.optional(fields, Result.MetaKey)(v =>
        Fields.asObject(v, Result.MetaKey).flatMap(Meta.toResultMeta)
      )
    } yield InputRequiredResult(
      inputRequests = inputRequests,
      requestState = requestState,
      resultType = resultType,
      meta = meta
    )
  }

  def fromRequestOutcome[A](outcome: RequestOutcome[A])(
      encodeCompleted: A => JsonObject
  ): JsonObject =
    outcome match {
      case Completed(result)       => encodeCompleted(result)
      case InputRequired(result)   => fromInputRequiredResult(result)
    }

  def toRequestOutcome[A](result: JsonObject)(
      decodeCompleted: JsonObject => Either[DecodingError, A]
  ): Either[DecodingError, RequestOutcome[A]] =
    Fields.optionalString(result.value, Result.ResultTypeKey).map(ResultType.fromWire).flatMap {
      resultType =>
        if (resultType == InputRequiredResultType)
          toInputRequiredResult(result).map(InputRequired(_))
        else
          decodeCompleted(result).map(Completed(_))
    }
}
