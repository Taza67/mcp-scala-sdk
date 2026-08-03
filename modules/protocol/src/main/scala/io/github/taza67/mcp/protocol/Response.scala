package io.github.taza67.mcp.protocol

sealed trait Result

sealed trait Response

case class SuccessResponse(jsonrpc: Version = Version20, result: Result, id: Id) extends Response

case class ErrorResponse(jsonrpc: Version = Version20, error: Error, id: Id) extends Response
