package io.github.taza67.mcp.protocol



sealed trait Result

sealed trait Response

case class SuccessResponse(jsonrpc: Version, result: Result, id: ID)

case class ErrorResponse(jsonrpc: Version, error: Error, id: ID)
