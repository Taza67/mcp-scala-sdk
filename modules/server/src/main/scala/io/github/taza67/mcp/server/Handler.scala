package io.github.taza67.mcp.server

import io.github.taza67.mcp.protocol.jsonrpc.Error
import io.github.taza67.mcp.protocol.mcp.{RequestParams, Result}



/** Handles one MCP method: params in, [[Result]] or protocol [[Error]] out. */
trait Handler {
  def execute(parameters: Option[RequestParams]): Either[Error, Result]
}
