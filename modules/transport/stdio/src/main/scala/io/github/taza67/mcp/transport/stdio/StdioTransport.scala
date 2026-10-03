package io.github.taza67.mcp.transport.stdio

import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.PrintStream
import java.io.PrintWriter
import java.io.Reader
import java.io.Writer
import java.nio.charset.StandardCharsets

import scala.util.control.NonFatal

import io.github.taza67.mcp.codec.WireLimits
import io.github.taza67.mcp.codec.circe.JsonCodec
import io.github.taza67.mcp.codec.circe.JsonRpcCodec
import io.github.taza67.mcp.codec.circe.McpCodec
import io.github.taza67.mcp.codec.mcp.ServerRequests
import io.github.taza67.mcp.protocol.jsonrpc.ErrorCode
import io.github.taza67.mcp.protocol.jsonrpc.ErrorResponse
import io.github.taza67.mcp.protocol.jsonrpc.InternalError
import io.github.taza67.mcp.protocol.jsonrpc.InvalidRequestError
import io.github.taza67.mcp.protocol.jsonrpc.ParseError
import io.github.taza67.mcp.protocol.mcp.McpErrorResponse
import io.github.taza67.mcp.protocol.mcp.McpRequest
import io.github.taza67.mcp.server.Server

/**
 * Newline-delimited MCP JSON-RPC loop over injected streams.
 *
 * Reads one bounded frame per line. Rejected input (oversize, invalid UTF-8,
 * excessive nesting, parse or policy failure) produces a JSON-RPC error
 * response on `out` and a static diagnostic on `err`; a failing [[server]]
 * or encode step produces only the correlated error response. Byte input is
 * decoded as strict UTF-8 per frame. The transport never closes the
 * caller-provided streams.
 *
 * Responses are flushed after each frame and once more at EOF. I/O failures
 * propagate; PrintStream/PrintWriter swallow write failures that are
 * surfaced as `IOException("stdio output failure")` at flush time.
 */
case class StdioTransport(
    server: Server,
    maxMessageSize: Int = StdioTransport.DefaultMaxMessageSize,
    maxNestingDepth: Int = StdioTransport.DefaultMaxNestingDepth
) {

  import StdioTransport._

  require(
    maxMessageSize > 0 && maxMessageSize < Int.MaxValue,
    "maxMessageSize must be positive and strictly below Int.MaxValue"
  )
  require(
    maxNestingDepth > 0 && maxNestingDepth < Int.MaxValue,
    "maxNestingDepth must be positive and strictly below Int.MaxValue"
  )

  def runProcess(): Unit =
    run(System.in, System.out, System.err)

  /** Byte-oriented loop: frames are bounded in UTF-8 bytes and decoded strictly. */
  def run(in: InputStream, out: OutputStream, err: OutputStream): Unit = {
    val reader = new BufferedInputStream(in)
    val outWriter = outputWriterFor(out)
    val errWriter = outputWriterFor(err)
    var reading = true
    while (reading)
      StdioFrames.read(reader, maxMessageSize) match {
        case Right(Some(text)) => dispatchText(text, outWriter, errWriter)
        case Right(None)       => reading = false
        case Left(error)       =>
          writeResponse(outWriter, ErrorResponse(error))
          writeDiagnostic(errWriter, error.code)
      }
    checkedFlush(outWriter)
    checkedFlush(errWriter)
  }

  /** Character-oriented loop: frames are bounded in UTF-16 code units. */
  def run(in: Reader, out: Writer, err: Writer): Unit = {
    val reader = new BufferedReader(in)
    Iterator.continually(readCharFrame(reader))
      .takeWhile(_ != EndOfInput)
      .foreach(frame => dispatchFrame(frame, out, err))
    checkedFlush(out)
    checkedFlush(err)
  }

  /** Same frame contract as the byte loop, in UTF-16 code units. */
  private def readCharFrame(reader: BufferedReader): Frame = {
    val buffer = new StringBuilder()
    var oversized = false
    var eof = false
    var done = false
    var last = -1
    while (!done) {
      val unit = reader.read()
      if (unit == -1) {
        eof = true
        done = true
      } else if (unit == '\n') done = true
      else {
        last = unit
        if (buffer.length <= maxMessageSize) buffer.append(unit.toChar)
        else oversized = true
      }
    }
    val count = buffer.length
    if (count == 0 && eof) EndOfInput
    else {
      val effective = if (last == '\r') count - 1 else count
      if (oversized || effective > maxMessageSize) OversizedFrame
      else CharFrame(buffer.toString)
    }
  }

  private def dispatchFrame(frame: Frame, out: Writer, err: Writer): Unit =
    frame match {
      case CharFrame(text) => dispatchText(text, out, err)
      case OversizedFrame =>
        writeResponse(out, ErrorResponse(InvalidRequestError(SizeLimitMessage)))
        writeDiagnostic(err, ErrorCode.InvalidRequest)
      case EndOfInput => ()
    }

  /**
   * Handles one decoded frame: text decoding errors become an uncorrelated
   * ParseError; rejected requests become the error projected by
   * ServerRequests; anything else produces no output. A valid request is
   * handed to the server and its response encoded back onto `out`.
   */
  private def dispatchText(text: String, out: Writer, err: Writer): Unit =
    if (WireLimits.exceedsNesting(text, maxNestingDepth)) {
      writeResponse(out, ErrorResponse(InvalidRequestError(NestingLimitMessage)))
      writeDiagnostic(err, ErrorCode.InvalidRequest)
    } else
      JsonCodec.JsonValueDecoder.decode(text) match {
        case Left(_) =>
          writeResponse(out, ErrorResponse(ParseError()))
          writeDiagnostic(err, ErrorCode.ParseError)
        case Right(json) =>
          ServerRequests.toRequest(json) match {
            case Left(rejection) =>
              writeResponse(out, rejection)
              writeDiagnostic(err, rejection.error.code)
            case Right(None) => ()
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
    checkedFlush(writer)
  }

  /**
   * Flushes a writer and surfaces I/O failures that PrintWriter swallowed:
   * checkError reports whether any write or flush has failed on it.
   */
  private def checkedFlush(writer: Writer): Unit = {
    writer.flush()
    writer match {
      case printWriter: PrintWriter if printWriter.checkError() =>
        throw new IOException("stdio output failure")
      case _ => ()
    }
  }

  private def outputWriterFor(out: OutputStream): Writer =
    new OutputStreamWriter(checkingStream(out), StandardCharsets.UTF_8)

  /**
   * Wraps a PrintStream so its swallowed failures surface on flush; other
   * streams keep their own IOException propagation.
   */
  private def checkingStream(out: OutputStream): OutputStream =
    out match {
      case printStream: PrintStream => new CheckedPrintStream(printStream)
      case other                    => other
    }
}

object StdioTransport {

  /** Default maximum frame size: 8 MiB of UTF-8 bytes (UTF-16 units on Readers). */
  val DefaultMaxMessageSize: Int = WireLimits.DefaultMaxMessageSize

  /** Default maximum JSON nesting depth per frame. */
  val DefaultMaxNestingDepth: Int = WireLimits.DefaultMaxNestingDepth

  private val SizeLimitMessage = "stdio message exceeds size limit"
  private val NestingLimitMessage = "stdio message exceeds nesting limit"

  private sealed trait Frame
  private case object EndOfInput extends Frame
  private case object OversizedFrame extends Frame
  private case class CharFrame(text: String) extends Frame

  /**
   * Non-closing adapter that turns a swallowed PrintStream failure into an
   * IOException on flush, so OutputStreamWriter flushing can detect it.
   */
  private class CheckedPrintStream(printStream: PrintStream) extends OutputStream {
    override def write(b: Int): Unit = printStream.write(b)
    override def write(b: Array[Byte], off: Int, len: Int): Unit =
      printStream.write(b, off, len)
    override def flush(): Unit =
      if (printStream.checkError()) throw new IOException("stdio output failure")
    override def close(): Unit = ()
  }
}
