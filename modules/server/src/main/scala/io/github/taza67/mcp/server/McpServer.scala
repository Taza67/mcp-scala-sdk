package io.github.taza67.mcp.server

import io.github.taza67.mcp.protocol.{Error, ErrorResponse, Request, Response, SuccessResponse}



case class McpServer(handlerRegistry: HandlerRegistry) extends Server {

  override def handle(request: Request): Response = {
    val handler = handlerRegistry.find(request.method)
    handler match {
      case Some(h) =>
        h.execute(request.params) match {
          case Left(e)  => ErrorResponse(error = e, id = request.id)
          case Right(r) => SuccessResponse(result = r, id = request.id)
        }
      case _ => ErrorResponse(error = Error.MethodNotFound, id = request.id)
    }
  }
}
