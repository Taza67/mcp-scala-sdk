package io.github.taza67.mcp.client

import scala.util.control.NonFatal

import io.github.taza67.mcp.codec.DecodingError
import io.github.taza67.mcp.codec.mcp.Input
import io.github.taza67.mcp.codec.mcp.completion.{Completion => CompletionCodec}
import io.github.taza67.mcp.codec.mcp.discover.{Discover => DiscoverCodec}
import io.github.taza67.mcp.codec.mcp.tools.{Tools => ToolsCodec}
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.jsonrpc.Method
import io.github.taza67.mcp.protocol.jsonrpc.RequestId
import io.github.taza67.mcp.protocol.mcp.Completed
import io.github.taza67.mcp.protocol.mcp.Cursor
import io.github.taza67.mcp.protocol.mcp.InputRequired
import io.github.taza67.mcp.protocol.mcp.InputRequiredResultType
import io.github.taza67.mcp.protocol.mcp.McpErrorResponse
import io.github.taza67.mcp.protocol.mcp.McpRequest
import io.github.taza67.mcp.protocol.mcp.McpRequestParams
import io.github.taza67.mcp.protocol.mcp.McpResponse
import io.github.taza67.mcp.protocol.mcp.McpSuccessResponse
import io.github.taza67.mcp.protocol.mcp.PaginatedRequestParams
import io.github.taza67.mcp.protocol.mcp.RequestMeta
import io.github.taza67.mcp.protocol.mcp.RequestOutcome
import io.github.taza67.mcp.protocol.mcp.RequestParams
import io.github.taza67.mcp.protocol.mcp.Result
import io.github.taza67.mcp.protocol.mcp.ResultMeta
import io.github.taza67.mcp.protocol.mcp.ResultType
import io.github.taza67.mcp.protocol.mcp.completion.{Completion => CompletionMethods}
import io.github.taza67.mcp.protocol.mcp.completion.CompleteRequestParams
import io.github.taza67.mcp.protocol.mcp.completion.CompleteResult
import io.github.taza67.mcp.protocol.mcp.discover.DiscoverResult
import io.github.taza67.mcp.protocol.mcp.discover.ServerDiscover
import io.github.taza67.mcp.protocol.mcp.tools.{Tools => ToolMethods}
import io.github.taza67.mcp.protocol.mcp.tools.CallToolRequestParams
import io.github.taza67.mcp.protocol.mcp.tools.CallToolResult
import io.github.taza67.mcp.protocol.mcp.tools.ListToolsResult



/** Minimal synchronous MCP client core.
 *
 *  Each [[request]] allocates an id, sends one [[McpRequest]] through
 *  [[ClientTransport]], and correlates the response before exposing it:
 *  matching success ids yield the [[Result]] untouched, matching error ids
 *  become [[ClientError.RemoteError]], mismatched ids
 *  [[ClientError.ResponseIdMismatch]], and id-less errors
 *  [[ClientError.UncorrelatedResponse]].
 *
 *  Request metadata travels inside `params`; the client holds no connection
 *  state and owns no transport lifecycle. A throwing transport degrades to
 *  [[ClientError.TransportFailure]] without echoing exception details; fatal
 *  failures propagate.
 *
 *  @param transport Synchronous exchange port (borrowed, not owned).
 *  @param requestIds Id allocator shared across requests on this client.
 */
final case class McpClient(
    transport: ClientTransport,
    requestIds: RequestIds = RequestIds.monotonic()
) {

  /** Sends one request and returns the correlated result or a typed error. */
  def request(
      method: Method,
      params: McpRequestParams
  ): Either[ClientError, Result] =
    requestIds.next() match {
      case Left(error) => Left(error)
      case Right(id) =>
        val wireRequest =
          McpRequest(method = method, id = id, params = Some(params))
        val exchanged =
          try transport.exchange(wireRequest)
          catch { case NonFatal(_) => Left(ClientError.TransportFailure) }
        exchanged match {
          case Left(error)     => Left(error)
          case Right(response) => correlate(id, response)
        }
    }

  /** Runs `server/discover` with explicit per-call request metadata. */
  def discover(meta: RequestMeta): Either[ClientError, DiscoverResult] =
    request(ServerDiscover.method, RequestParams(meta = meta)).flatMap { result =>
      decodeResult[DiscoverResult](result)(
        DiscoverCodec.toDiscoverResult,
        (decoded, resultType, resultMeta) =>
          decoded.copy(resultType = resultType, meta = resultMeta)
      )
    }

  /** Runs `completion/complete` with the given typed parameters. */
  def complete(
      params: CompleteRequestParams
  ): Either[ClientError, CompleteResult] =
    request(
      CompletionMethods.complete,
      CompletionCodec.fromCompleteRequestParams(params)
    ).flatMap { result =>
      decodeResult[CompleteResult](result)(
        CompletionCodec.toCompleteResult,
        (decoded, resultType, resultMeta) =>
          decoded.copy(resultType = resultType, meta = resultMeta)
      )
    }

  /** Runs `tools/list` with explicit metadata and an optional page cursor. */
  def listTools(
      meta: RequestMeta,
      cursor: Option[Cursor] = None
  ): Either[ClientError, ListToolsResult] =
    request(
      ToolMethods.list,
      PaginatedRequestParams(meta = meta, cursor = cursor)
    ).flatMap { result =>
      decodeResult[ListToolsResult](result)(
        ToolsCodec.toListToolsResult,
        (decoded, resultType, resultMeta) =>
          decoded.copy(resultType = resultType, meta = resultMeta)
      )
    }

  /** Runs `tools/call`; an input-required outcome is returned to the caller
   *  without retrying or issuing a second request.
   */
  def callTool(
      params: CallToolRequestParams
  ): Either[ClientError, RequestOutcome[CallToolResult]] =
    request(
      ToolMethods.call,
      ToolsCodec.fromCallToolRequestParams(params)
    ).flatMap { result =>
      decodeOutcome[CallToolResult](result)(
        ToolsCodec.toCallToolResult,
        (decoded, resultType, resultMeta) =>
          decoded.copy(resultType = resultType, meta = resultMeta)
      )
    }

  /** Decodes a result that may carry the input-required outcome instead of a
   *  completed payload, attaching envelope `resultType`/`meta` either way.
   */
  private def decodeOutcome[A](result: Result)(
      decode: JsonObject => Either[DecodingError, A],
      attach: (A, ResultType, Option[ResultMeta]) => A
  ): Either[ClientError, RequestOutcome[A]] =
    if (result.resultType == InputRequiredResultType)
      Input.toInputRequiredResult(result.fields) match {
        case Left(_) => Left(ClientError.InvalidResult)
        case Right(value) =>
          Right(
            InputRequired(
              value.copy(resultType = result.resultType, meta = result.meta)
            )
          )
      }
    else
      decodeResult(result)(decode, attach).map(Completed(_))

  /** Decodes `result.fields` and attaches the envelope's `resultType` and
   *  `meta` to the decoded domain value.
   */
  private def decodeResult[A](result: Result)(
      decode: JsonObject => Either[DecodingError, A],
      attach: (A, ResultType, Option[ResultMeta]) => A
  ): Either[ClientError, A] =
    decode(result.fields) match {
      case Left(_)      => Left(ClientError.InvalidResult)
      case Right(value) => Right(attach(value, result.resultType, result.meta))
    }

  private def correlate(
      id: RequestId,
      response: McpResponse
  ): Either[ClientError, Result] =
    response match {
      case success: McpSuccessResponse =>
        if (success.id == id) Right(success.result)
        else Left(ClientError.ResponseIdMismatch)
      case error: McpErrorResponse =>
        error.id match {
          case Some(responseId) if responseId == id =>
            Left(ClientError.RemoteError(error.error))
          case Some(_) => Left(ClientError.ResponseIdMismatch)
          case None    => Left(ClientError.UncorrelatedResponse)
        }
    }
}
