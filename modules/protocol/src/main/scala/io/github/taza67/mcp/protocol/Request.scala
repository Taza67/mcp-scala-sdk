package io.github.taza67.mcp.protocol



case class Method(value: String)

case class Request(jsonrpc: Version, method: Method, params: Option[Parameters], id: ID)