package io.github.taza67.mcp.protocol.mcp

import io.github.taza67.mcp.protocol.json.{JsonObject, JsonValue}
import io.github.taza67.mcp.protocol.jsonrpc.Method
import io.github.taza67.mcp.protocol.mcp.elicitation.{
  Elicitation,
  ElicitRequestParams,
  ElicitResult
}
import io.github.taza67.mcp.protocol.mcp.sampling.{
  CreateMessageRequestParams,
  CreateMessageResult,
  Sampling
}



/** Nested request the client must fulfill as part of multi round-trip input.
 *
 *  Wire shape is `{ "method", "params" }` (no JSON-RPC `id` / `jsonrpc`). Known
 *  methods are elicitation and sampling; other methods use [[CustomInputRequest]].
 */
sealed trait InputRequest {
  def method: Method
}

/** Nested `elicitation/create` request inside [[InputRequests]]. */
case class ElicitationInputRequest(
    params: ElicitRequestParams
) extends InputRequest {
  val method: Method = Elicitation.create
}

/** Nested `sampling/createMessage` request inside [[InputRequests]].
 *
 *  @deprecated Deprecated as of protocol version 2026-07-28 (SEP-2577). Remains for at least twelve months.
 */
case class SamplingInputRequest(
    params: CreateMessageRequestParams
) extends InputRequest {
  val method: Method = Sampling.createMessage
}

/** Nested input request for an unrecognized or extension method. */
case class CustomInputRequest(
    method: Method,
    params: Option[JsonObject] = None
) extends InputRequest

/** Client answer to a nested [[InputRequest]].
 *
 *  Keys in [[InputResponses]] match the server-assigned keys in [[InputRequests]].
 */
sealed trait InputResponse

/** Client answer to an [[ElicitationInputRequest]]. */
case class ElicitationInputResponse(
    result: ElicitResult
) extends InputResponse

/** Client answer to a [[SamplingInputRequest]].
 *
 *  @deprecated Deprecated as of protocol version 2026-07-28 (SEP-2577). Remains for at least twelve months.
 */
case class SamplingInputResponse(
    result: CreateMessageResult
) extends InputResponse

/** Client answer for an unrecognized or extension nested request. */
case class CustomInputResponse(
    value: JsonValue
) extends InputResponse

/** Map of server-initiated nested requests the client must fulfill.
 *
 *  Keys are server-assigned identifiers; values are method+params objects.
 */
case class InputRequests(value: Map[String, InputRequest] = Map.empty)

/** Map of client responses to server-initiated [[InputRequests]].
 *
 *  Keys correspond to the keys in the server's `inputRequests` map.
 */
case class InputResponses(value: Map[String, InputResponse] = Map.empty)

/** Server indicates additional input is needed before the request can complete.
 *
 *  At least one of [[inputRequests]] or [[requestState]] MUST be present.
 *
 *  @param inputRequests Server-initiated requests for the client to answer.
 *  @param requestState Opaque state the client must echo on the next attempt.
 *  @param resultType Normally [[InputRequiredResultType]].
 *  @param meta Optional result metadata.
 */
case class InputRequiredResult(
    inputRequests: Option[InputRequests] = None,
    requestState: Option[String] = None,
    resultType: ResultType = InputRequiredResultType,
    meta: Option[ResultMeta] = None
)

/** Outcome of a method that may pause for additional input.
 *
 *  Used by `tools/call`, `resources/read`, and similar methods whose successful
 *  JSON-RPC result is either a completed payload or an [[InputRequiredResult]].
 *
 *  @tparam A Completed result type for the method.
 */
sealed trait RequestOutcome[+A]

/** The request completed; [[result]] holds the method-specific payload. */
case class Completed[+A](result: A) extends RequestOutcome[A]

/** The request needs more input before it can complete. */
case class InputRequired(result: InputRequiredResult) extends RequestOutcome[Nothing]
