package io.github.taza67.mcp.protocol

case class Notification(jsonrpc: Version, method: Method, params: Option[Parameters])
