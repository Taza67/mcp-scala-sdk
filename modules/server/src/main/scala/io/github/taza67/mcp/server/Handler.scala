package io.github.taza67.mcp.server

import io.github.taza67.mcp.codec.mcp.tools.{Tools => ToolsCodec}
import io.github.taza67.mcp.protocol.jsonrpc.Error
import io.github.taza67.mcp.protocol.jsonrpc.InvalidParamsError
import io.github.taza67.mcp.protocol.mcp.McpRequestParams
import io.github.taza67.mcp.protocol.mcp.RequestParams
import io.github.taza67.mcp.protocol.mcp.Result
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

  /** Decodes `tools/call` params, then runs the domain handler. */
  def callTool(
      execute: CallToolRequestParams => Either[Error, CallToolResult]
  ): Handler =
    of { parameters =>
      parameters match {
        case Some(plain: RequestParams) =>
          ToolsCodec.toCallToolRequestParams(plain) match {
            case Left(error) => Left(InvalidParamsError(message = error.message))
            case Right(call) => execute(call)
          }
        case _ =>
          Left(InvalidParamsError(message = "missing tools/call params"))
      }
    }(Results.callToolResultEncoder)

  /** Routes `tools/call` by tool name, then runs that tool with a [[ToolCall]]. */
  def tools(
      handlers: (String, ToolCall => Either[Error, CallToolResult])*
  ): Handler = {
    val byName = handlers.toMap
    callTool { call =>
      byName.get(call.name) match {
        case Some(run) => run(ToolCall.from(call))
        case None =>
          Left(InvalidParamsError(message = s"unknown tool: ${call.name}"))
      }
    }
  }
}
