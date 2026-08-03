package io.github.taza67.mcp.protocol

case class ErrorCode(value: Int)

object ErrorCode {
  val ParseError = ErrorCode(-32700)
  val InvalidRequest = ErrorCode(-32600)
  val MethodNotFound = ErrorCode(-32601)
  val InvalidParams = ErrorCode(-32602)
  val InternalError = ErrorCode(-32603)
}

case class ErrorMessage(value: String)

object ErrorMessage {
  val ParseError = ErrorMessage("Invalid JSON")
  val InvalidRequest = ErrorMessage("Invalid JSON-RPC request")
  val MethodNotFound = ErrorMessage("Unknown method")
  val InvalidParams = ErrorMessage("Invalid parameters")
  val InternalError = ErrorMessage("Internal error")
}

sealed trait ErrorData

case class Error(code: ErrorCode, message: ErrorMessage, data: Option[ErrorData] = None)

object Error {
  val ParseError = Error(ErrorCode.ParseError, ErrorMessage.ParseError)
  val InvalidRequest = Error(ErrorCode.InvalidRequest, ErrorMessage.InvalidRequest)
  val MethodNotFound = Error(ErrorCode.MethodNotFound, ErrorMessage.MethodNotFound)
  val InvalidParams = Error(ErrorCode.InvalidParams, ErrorMessage.InvalidParams)
  val InternalError = Error(ErrorCode.InternalError, ErrorMessage.InternalError)
}
