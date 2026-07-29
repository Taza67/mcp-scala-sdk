package io.github.taza67.mcp.protocol



case class ErrorCode(value: Int)

case class ErrorMessage(value: String)

sealed trait ErrorData

case class Error(code: ErrorCode, message: ErrorMessage, data: Option[ErrorData])