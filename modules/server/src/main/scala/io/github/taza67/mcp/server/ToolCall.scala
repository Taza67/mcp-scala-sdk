package io.github.taza67.mcp.server

import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.mcp.InputResponses
import io.github.taza67.mcp.protocol.mcp.RequestMeta
import io.github.taza67.mcp.protocol.mcp.tools.CallToolRequestParams



/** A `tools/call` after the tool name has been routed.
 *
 *  The function for one tool does not see [[CallToolRequestParams.name]]:
 *  unknown names are rejected before this value is built.
 */
case class ToolCall(
    arguments: Option[JsonObject],
    meta: RequestMeta,
    inputResponses: Option[InputResponses] = None,
    requestState: Option[String] = None
)

object ToolCall {

  def from(params: CallToolRequestParams): ToolCall =
    ToolCall(
      arguments = params.arguments,
      meta = params.meta,
      inputResponses = params.inputResponses,
      requestState = params.requestState
    )
}
