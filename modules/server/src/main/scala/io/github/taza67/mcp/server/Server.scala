package io.github.taza67.mcp.server

import io.github.taza67.mcp.protocol.{Request, Response}



trait Server {
  def handle(request: Request): Response
}
