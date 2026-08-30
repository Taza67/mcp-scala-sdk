package io.github.taza67.mcp.server

import io.github.taza67.mcp.codec.mcp.completion.{Completion => CompletionCodec}
import io.github.taza67.mcp.codec.mcp.tools.{Tools => ToolsCodec}
import io.github.taza67.mcp.protocol.jsonrpc.Error
import io.github.taza67.mcp.protocol.jsonrpc.InvalidParamsError
import io.github.taza67.mcp.protocol.mcp.McpRequestParams
import io.github.taza67.mcp.protocol.mcp.RequestParams
import io.github.taza67.mcp.protocol.mcp.Result
import io.github.taza67.mcp.protocol.mcp.completion.CompleteRequestParams
import io.github.taza67.mcp.protocol.mcp.completion.CompleteResult
import io.github.taza67.mcp.protocol.mcp.tools.CallToolRequestParams
import io.github.taza67.mcp.protocol.mcp.tools.CallToolResult



/** Handles one MCP method: params in, [[Result]] or protocol [[Error]] out. */
trait Handler {
  def execute(parameters: Option[McpRequestParams]): Either[Error, Result]
}

object Handler {

  /** Wraps a domain-typed handler with a [[ResultEncoder]]. */
  def of[A](execute: Option[McpRequestParams] => Either[Error, A])(implicit
      encoder: ResultEncoder[A]
  ): Handler =
    (parameters: Option[McpRequestParams]) => execute(parameters).map(encoder.encode)

  /** Handler that returns an empty successful [[Result]]. */
  def empty(execute: Option[McpRequestParams] => Either[Error, Unit]): Handler =
    of(execute)

  /** Decodes `tools/call` params, then runs the domain handler.
   *
   *  Decode and lookup failures answer a generic [[InvalidParamsError]]: raw
   *  decoder details and tool names never reach the wire.
   */
  def callTool(
      execute: CallToolRequestParams => Either[Error, CallToolResult]
  ): Handler =
    of { parameters =>
      parameters match {
        case Some(plain: RequestParams) =>
          ToolsCodec.toCallToolRequestParams(plain) match {
            case Left(_)     => Left(InvalidParamsError())
            case Right(call) => execute(call)
          }
        case _ =>
          Left(InvalidParamsError(message = "missing tools/call params"))
      }
    }(Results.callToolResultEncoder)

  /** Decodes `completion/complete` params, then runs the domain handler.
   *
   *  Decode failures answer a generic [[InvalidParamsError]]: raw decoder
   *  details never reach the wire.
   */
  def complete(
      execute: CompleteRequestParams => Either[Error, CompleteResult]
  ): Handler =
    of { parameters =>
      parameters match {
        case Some(plain: RequestParams) =>
          CompletionCodec.toCompleteRequestParams(plain) match {
            case Left(_)      => Left(InvalidParamsError())
            case Right(value) => execute(value)
          }
        case _ =>
          Left(InvalidParamsError(message = "missing completion/complete params"))
      }
    }(Results.completeResultEncoder)

  /** Routes `tools/call` by tool name, then runs that tool with a [[ToolCall]].
   *
   *  Fails fast on blank or duplicate tool names; this helper owns the
   *  name-uniqueness invariant for `tools/call` routing.
   */
  def tools(
      handlers: (String, ToolCall => Either[Error, CallToolResult])*
  ): Handler = {
    val names = handlers.map(_._1)
    require(names.forall(name => !name.isBlank), "blank tool name")
    require(names.distinct.size == names.size, "duplicate tool names")
    val byName = handlers.toMap
    callTool { call =>
      byName.get(call.name) match {
        case Some(run) => run(ToolCall.from(call))
        case None =>
          Left(InvalidParamsError(message = "unknown tool"))
      }
    }
  }
}
