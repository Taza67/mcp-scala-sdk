package io.github.taza67.mcp.protocol

case class Method(value: String)

case class Request(
    jsonrpc: Version = Version20,
    method: Method,
    params: Option[Parameters] = None,
    id: Id
)
