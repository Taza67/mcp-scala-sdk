package io.github.taza67.mcp.transport.stdio

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.Reader
import java.io.Writer
import java.nio.charset.StandardCharsets

import scala.util.control.NonFatal

import io.github.taza67.mcp.codec.circe.JsonCodec
import io.github.taza67.mcp.codec.circe.JsonRpcCodec
import io.github.taza67.mcp.codec.circe.McpCodec
import io.github.taza67.mcp.codec.mcp.ServerRequests
import io.github.taza67.mcp.protocol.jsonrpc.ErrorCode
import io.github.taza67.mcp.protocol.jsonrpc.ErrorResponse
import io.github.taza67.mcp.protocol.jsonrpc.InternalError
import io.github.taza67.mcp.protocol.jsonrpc.ParseError
import io.github.taza67.mcp.protocol.mcp.McpErrorResponse
import io.github.taza67.mcp.protocol.mcp.McpRequest
import io.github.taza67.mcp.server.Server



/** Newline-delimited MCP JSON-RPC loop over injected streams.
 *
 *  Each input line is parsed as JSON text, validated by [[ServerRequests]],
 *  dispatched through [[server]], and answered with one compact response line.
 *  Rejected input produces a JSON-RPC error response on [[out]] (uncorrelated
 *  for malformed input, correlated when a request id could be read) plus a
 *  static diagnostic on [[err]]. Notifications and inbound responses produce
 *  no output. A failing [[server]] or encode step degrades to a correlated
 *  `InternalError`; fatal errors and stream I/O failures still propagate.
 *  End of input ends the loop (stdio graceful shutdown).
 *
 *  Byte-stream [[run]] always decodes and encodes UTF-8. It does not close the
 *  streams: the process owns `System.in` / `System.out` / `System.err`.
 */
case class StdioTransport(server: Server) {

  def runProcess(): Unit =
    run(System.in, System.out, System.err)

  def run(in: InputStream, out: OutputStream, err: OutputStream): Unit =
    run(
      new InputStreamReader(in, StandardCharsets.UTF_8),
      new OutputStreamWriter(out, StandardCharsets.UTF_8),
      new OutputStreamWriter(err, StandardCharsets.UTF_8)
    )

  def run(in: Reader, out: Writer, err: Writer): Unit = {
    val reader = new BufferedReader(in)
    Iterator.continually(reader.readLine()).takeWhile(_ != null).foreach { line =>
      dispatch(line, out, err)
    }
    out.flush()
    err.flush()
  }

  private def dispatch(line: String, out: Writer, err: Writer): Unit =
    JsonCodec.JsonValueDecoder.decode(line) match {
      case Left(_) =>
        writeResponse(out, ErrorResponse(ParseError()))
        writeDiagnostic(err, ErrorCode.ParseError)
      case Right(json) =>
        ServerRequests.toRequest(json) match {
          case Left(rejection) =>
            writeResponse(out, rejection)
            writeDiagnostic(err, rejection.error.code)
          case Right(None)          => ()
          case Right(Some(request)) =>
            writeLine(out, encodeSafely(request))
        }
    }

  /** One request failure stays one error response; fatal errors propagate. */
  private def encodeSafely(request: McpRequest): String =
    try McpCodec.MessageEncoder.encode(server.handle(request))
    catch {
      case NonFatal(_) =>
        McpCodec.MessageEncoder.encode(
          McpErrorResponse(error = InternalError(), id = request.id)
        )
    }

  private def writeResponse(out: Writer, response: ErrorResponse): Unit =
    writeLine(out, JsonRpcCodec.MessageEncoder.encode(response))

  /** Rejection diagnostic: static text and the integer error code only. */
  private def writeDiagnostic(err: Writer, code: Int): Unit =
    writeLine(err, s"rejected inbound message (error code $code)")

  private def writeLine(writer: Writer, line: String): Unit = {
    require(
      !line.contains('\n') && !line.contains('\r'),
      "stdio message must not contain embedded newlines"
    )
    writer.write(line)
    writer.write('\n')
    writer.flush()
  }
}
