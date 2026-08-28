package io.github.taza67.mcp.protocol.mcp.completion

import io.github.taza67.mcp.protocol.mcp._
import io.github.taza67.mcp.protocol.jsonrpc.{JsonRpcVersion, JsonRpcVersion20, Method, RequestId}

/** Method name for argument autocompletion. */
object Completion {
  val complete: Method = Method("completion/complete")
}

/** Reference identifying what is being completed. */
sealed trait CompletionReference {
  def referenceType: String
}

object CompletionReference {

  /** Wire key for the reference discriminator. */
  val TypeKey: String = "type"
}

/** Identifies a prompt for completion. */
case class PromptReference(
    name: String,
    title: Option[String] = None
) extends CompletionReference {
  val referenceType: String = PromptReference.TypeValue
}

object PromptReference {

  /** Wire key for the prompt name. */
  val NameKey: String = "name"

  /** Wire key for the optional prompt title. */
  val TitleKey: String = "title"

  /** Wire discriminator value for prompt references. */
  val TypeValue: String = "ref/prompt"
}

/** Reference to a resource or resource-template definition for completion.
 *
 *  @param uri URI or URI template of the resource.
 */
case class ResourceTemplateReference(
    uri: String
) extends CompletionReference {
  val referenceType: String = ResourceTemplateReference.TypeValue
}

object ResourceTemplateReference {

  /** Wire key for the resource or resource-template URI. */
  val UriKey: String = "uri"

  /** Wire discriminator value for resource-template references. */
  val TypeValue: String = "ref/resource"
}

/** Argument currently being completed.
 *
 *  @param name Argument name.
 *  @param value Partial value entered so far.
 */
case class CompletionArgument(
    name: String,
    value: String
)

object CompletionArgument {

  /** Wire key for the argument name. */
  val NameKey: String = "name"

  /** Wire key for the partial argument value. */
  val ValueKey: String = "value"
}

/** Previously resolved arguments providing context for completion.
 *
 *  @param arguments Already-known argument name/value pairs.
 */
case class CompletionContext(
    arguments: Option[Map[String, String]] = None
)

object CompletionContext {

  /** Wire key for the optional already-known arguments map. */
  val ArgumentsKey: String = "arguments"
}

/** Parameters for a `completion/complete` request.
 *
 *  @param meta Required request metadata.
 *  @param ref Prompt or resource-template being completed against.
 *  @param argument Argument currently being completed.
 *  @param context Optional already-resolved neighboring arguments.
 */
case class CompleteRequestParams(
    meta: RequestMeta,
    ref: CompletionReference,
    argument: CompletionArgument,
    context: Option[CompletionContext] = None
)

object CompleteRequestParams {

  /** Wire key for the reference being completed against. */
  val RefKey: String = "ref"

  /** Wire key for the argument currently being completed. */
  val ArgumentKey: String = "argument"

  /** Wire key for the optional completion context. */
  val ContextKey: String = "context"
}

/** Asks the server for completion options for an argument. */
case class CompleteRequest(
    id: RequestId,
    params: CompleteRequestParams,
    jsonrpc: JsonRpcVersion = JsonRpcVersion20
)

/** Completion suggestions returned by the server.
 *
 *  @param values Suggested completion strings.
 *  @param total Total number of matching completions, if known.
 *  @param hasMore Whether more completions exist beyond [[values]].
 */
case class CompletionPayload(
    values: List[String],
    total: Option[Long] = None,
    hasMore: Option[Boolean] = None
) {
  require(
    values.lengthCompare(CompletionPayload.MaxValues) <= 0,
    "completion values must not exceed 100 items"
  )
}

object CompletionPayload {

  /** Wire key for the completion values array. */
  val ValuesKey: String = "values"

  /** Wire key for the optional total count. */
  val TotalKey: String = "total"

  /** Wire key for the optional has-more flag. */
  val HasMoreKey: String = "hasMore"

  /** Normative upper bound on the values array. */
  val MaxValues: Int = 100
}

/** Result of a `completion/complete` request.
 *
 *  @param completion Suggested values (and optional paging hints).
 *  @param resultType Normally [[CompleteResultType]].
 *  @param meta Optional result metadata.
 */
case class CompleteResult(
    completion: CompletionPayload,
    resultType: ResultType = CompleteResultType,
    meta: Option[ResultMeta] = None
)

object CompleteResult {

  /** Wire key for the completion payload. */
  val CompletionKey: String = "completion"
}

/** Successful JSON-RPC response to a `completion/complete` request. */
case class CompleteResultResponse(
    result: CompleteResult,
    id: RequestId,
    jsonrpc: JsonRpcVersion = JsonRpcVersion20
)
