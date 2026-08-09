package io.github.taza67.mcp.server

import io.github.taza67.mcp.protocol.jsonrpc.Error
import io.github.taza67.mcp.protocol.mcp.{McpRequestParams, Result}



/** Handles one MCP method: params in, [[Result]] or protocol [[Error]] out. */
trait Handler {
  def execute(parameters: Option[McpRequestParams]): Either[Error, Result]
}

object Handler {

  /** Wraps a domain-typed handler with a [[ResultEncoder]]. */
  def of[A](execute: Option[McpRequestParams] => Either[Error, A])(
      implicit encoder: ResultEncoder[A]
  ): Handler =
    (parameters: Option[McpRequestParams]) =>
      execute(parameters).map(encoder.encode)

  /** Handler that returns an empty successful [[Result]]. */
  def empty(execute: Option[McpRequestParams] => Either[Error, Unit]): Handler =
    of(execute)
}
