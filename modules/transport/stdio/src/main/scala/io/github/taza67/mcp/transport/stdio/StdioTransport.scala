package io.github.taza67.mcp.transport.stdio

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.Reader
import java.io.Writer
import java.nio.charset.StandardCharsets

import io.github.taza67.mcp.codec.circe.McpCodec
import io.github.taza67.mcp.protocol.mcp.McpNotification
import io.github.taza67.mcp.protocol.mcp.McpRequest
import io.github.taza67.mcp.protocol.mcp.McpResponse
import io.github.taza67.mcp.server.Server



/** Newline-delimited MCP JSON-RPC loop over injected streams.
 *
 *  Reads one JSON-RPC message per line from [[in]], dispatches [[McpRequest]]s
 *  through [[server]], and writes compact responses to [[out]]. Decode failures
 *  and unexpected message kinds go to [[err]]; they never become stdout bytes.
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
    McpCodec.MessageDecoder.decode(line) match {
      case Left(error) =>
        writeLine(err, error.message)
      case Right(request: McpRequest) =>
        val encoded = McpCodec.MessageEncoder.encode(server.handle(request))
        writeLine(out, encoded)
      case Right(_: McpNotification) =>
        ()
      case Right(_: McpResponse) =>
        writeLine(err, "stdio server received a JSON-RPC response")
    }

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
