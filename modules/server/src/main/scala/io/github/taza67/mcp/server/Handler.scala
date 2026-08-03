package io.github.taza67.mcp.server

import io.github.taza67.mcp.protocol.{Error, Parameters, Result}



trait Handler {
  def execute(parameters: Option[Parameters]): Either[Error, Result]
}
