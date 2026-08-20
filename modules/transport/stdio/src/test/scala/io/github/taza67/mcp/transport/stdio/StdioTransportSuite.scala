package io.github.taza67.mcp.transport.stdio

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.PrintStream
import java.io.PrintWriter
import java.io.Reader
import java.io.StringReader
import java.io.StringWriter
import java.io.Writer
import java.nio.charset.StandardCharsets

import io.github.taza67.mcp.codec.circe.McpCodec
import io.github.taza67.mcp.protocol.jsonrpc.ErrorCode
import io.github.taza67.mcp.protocol.jsonrpc.Method
import io.github.taza67.mcp.protocol.jsonrpc.NumberRequestId
import io.github.taza67.mcp.protocol.jsonrpc.RequestId
import io.github.taza67.mcp.protocol.jsonrpc.StringRequestId
import io.github.taza67.mcp.protocol.mcp.ClientCapabilities
import io.github.taza67.mcp.protocol.mcp.McpErrorResponse
import io.github.taza67.mcp.protocol.mcp.McpMessage
import io.github.taza67.mcp.protocol.mcp.McpProtocolVersion20260728
import io.github.taza67.mcp.protocol.mcp.McpRequest
import io.github.taza67.mcp.protocol.mcp.McpSuccessResponse
import io.github.taza67.mcp.protocol.mcp.RequestMeta
import io.github.taza67.mcp.protocol.mcp.RequestParams
import io.github.taza67.mcp.protocol.mcp.Result
import io.github.taza67.mcp.server.Handler
import io.github.taza67.mcp.server.HandlerRegistryInMemory
import io.github.taza67.mcp.server.McpServer
import io.github.taza67.mcp.server.Server
import munit.FunSuite



class StdioTransportSuite extends FunSuite {

  private val ping = Method("ping")
  private val requestMeta = RequestMeta(
    protocolVersion = McpProtocolVersion20260728,
    clientCapabilities = ClientCapabilities()
  )
  private val server = McpServer(
    HandlerRegistryInMemory(Map(ping -> Handler.empty(_ => Right(()))))
  )
  private val transport = StdioTransport(server)

  private def request(method: Method, id: RequestId): McpRequest =
    McpRequest(method, id, params = Some(RequestParams(meta = requestMeta)))

  private def requestLine(method: Method, id: RequestId): String =
    McpCodec.MessageEncoder.encode(request(method, id))

  private def lines(text: String): List[String] =
    text.split("\n", -1).toList.filter(_.nonEmpty)

  private def decodeLine(line: String): McpMessage =
    McpCodec.MessageDecoder.decode(line) match {
      case Right(message) => message
      case Left(error)    => fail(s"output line did not decode: ${error.message}")
    }

  private def assertErrorLine(line: String, code: Int, id: Option[RequestId]): Unit =
    decodeLine(line) match {
      case response: McpErrorResponse =>
        assertEquals(response.error.code, code)
        assertEquals(response.id, id)
      case other =>
        fail(s"expected error response line, got $other")
    }

  private def assertSuccessLine(line: String, id: RequestId): Unit =
    assertEquals(
      decodeLine(line),
      McpSuccessResponse(result = Result.empty(), id = id)
    )

  private def assertErrorLine(
      line: String,
      code: Int,
      id: Option[RequestId],
      message: String
  ): Unit =
    decodeLine(line) match {
      case response: McpErrorResponse =>
        assertEquals(response.error.code, code)
        assertEquals(response.id, id)
        assertEquals(response.error.message, message)
      case other =>
        fail(s"expected error response line, got $other")
    }

  private def runBytes(input: String, target: StdioTransport = transport): String = {
    val in = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8))
    val out = new ByteArrayOutputStream()
    val err = new ByteArrayOutputStream()
    target.run(in, out, err)
    new String(out.toByteArray, StandardCharsets.UTF_8)
  }

  private def nestedRequestLine(id: String, extension: String): String =
    s"""{"jsonrpc":"2.0","method":"${ping.value}","id":"$id","params":{"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28","io.modelcontextprotocol/clientCapabilities":{},"x":$extension}}}"""

  test("Request line yields one compact response line on stdout") {
    val req = request(ping, StringRequestId("1"))
    val in = new StringReader(McpCodec.MessageEncoder.encode(req) + "\n")
    val out = new StringWriter()
    val err = new StringWriter()

    transport.run(in, out, err)

    val stdout = out.toString
    assertEquals(err.toString, "")
    assert(!stdout.contains('\r'))
    assertEquals(stdout.count(_ == '\n'), 1)
    assertSuccessLine(stdout.stripLineEnd, req.id)
  }

  test("Malformed JSON yields one uncorrelated ParseError, then the next request succeeds") {
    val in = new StringReader("{ secret-input\n" + requestLine(ping, StringRequestId("2")) + "\n")
    val out = new StringWriter()
    val err = new StringWriter()

    transport.run(in, out, err)

    val output = lines(out.toString)
    assertEquals(output.size, 2)
    assertErrorLine(output(0), ErrorCode.ParseError, None)
    assertSuccessLine(output(1), StringRequestId("2"))
    assert(!err.toString.contains("secret-input"))
  }

  test("Bad envelope yields one uncorrelated InvalidRequest error") {
    val in = new StringReader("""{"jsonrpc":"2.0","id":"1"}""" + "\n")
    val out = new StringWriter()
    val err = new StringWriter()

    transport.run(in, out, err)

    val output = lines(out.toString)
    assertEquals(output.size, 1)
    assertErrorLine(output(0), ErrorCode.InvalidRequest, None)
  }

  test("Bad request params yield a correlated InvalidParams error") {
    val missingParams = """{"jsonrpc":"2.0","method":"ping","id":"7"}"""
    val missingMeta =
      """{"jsonrpc":"2.0","method":"ping","id":"8","params":{}}"""
    val in = new StringReader(missingParams + "\n" + missingMeta + "\n")
    val out = new StringWriter()
    val err = new StringWriter()

    transport.run(in, out, err)

    val output = lines(out.toString)
    assertEquals(output.size, 2)
    assertErrorLine(output(0), ErrorCode.InvalidParams, Some(StringRequestId("7")))
    assertErrorLine(output(1), ErrorCode.InvalidParams, Some(StringRequestId("8")))
  }

  test("Unsupported protocol version yields a correlated error") {
    val raw =
      """{"jsonrpc":"2.0","method":"ping","id":"9","params":{"_meta":{"io.modelcontextprotocol/protocolVersion":"1999-01-01","io.modelcontextprotocol/clientCapabilities":{}}}}"""
    val in = new StringReader(raw + "\n")
    val out = new StringWriter()
    val err = new StringWriter()

    transport.run(in, out, err)

    val output = lines(out.toString)
    assertEquals(output.size, 1)
    assertErrorLine(output(0), ErrorCode.UnsupportedProtocolVersion, Some(StringRequestId("9")))
  }

  test("Notifications and inbound responses never produce replies") {
    val input = List(
      """{"jsonrpc":"2.0","method":"notifications/initialized"}""",
      """{"jsonrpc":"2.0","method":"notifications/initialized","params":123}""",
      """{"jsonrpc":"2.0","result":{},"id":"5"}""",
      """{"jsonrpc":"2.0","error":{"code":-32601,"message":"x"},"id":"6"}""",
      requestLine(ping, StringRequestId("only"))
    ).mkString("\n") + "\n"
    val in = new StringReader(input)
    val out = new StringWriter()
    val err = new StringWriter()

    transport.run(in, out, err)

    val output = lines(out.toString)
    assertEquals(output.size, 1)
    assertSuccessLine(output(0), StringRequestId("only"))
  }

  test("A throwing Server yields a correlated InternalError and recovers") {
    var calls = 0
    val flaky: Server = (request: McpRequest) => {
      calls += 1
      if (calls == 1) throw new RuntimeException("server-secret-marker")
      else McpSuccessResponse(result = Result.empty(), id = request.id)
    }
    val flakyTransport = StdioTransport(flaky)
    val input = requestLine(ping, StringRequestId("a")) + "\n" +
      requestLine(ping, StringRequestId("b")) + "\n"
    val in = new StringReader(input)
    val out = new StringWriter()
    val err = new StringWriter()

    flakyTransport.run(in, out, err)

    val output = lines(out.toString)
    assertEquals(output.size, 2)
    assertErrorLine(output(0), ErrorCode.InternalError, Some(StringRequestId("a")))
    assertSuccessLine(output(1), StringRequestId("b"))
    assert(!out.toString.contains("server-secret-marker"))
    assert(!err.toString.contains("server-secret-marker"))
  }

  test("An unencodable response falls back to a correlated InternalError") {
    // Returning null makes the response encode step throw inside the guard.
    val broken: Server = (_: McpRequest) => null
    val brokenTransport = StdioTransport(broken)
    val in = new StringReader(requestLine(ping, StringRequestId("c")) + "\n")
    val out = new StringWriter()
    val err = new StringWriter()

    brokenTransport.run(in, out, err)

    val output = lines(out.toString)
    assertEquals(output.size, 1)
    assertErrorLine(output(0), ErrorCode.InternalError, Some(StringRequestId("c")))
  }

  test("Pretty-printed request is not one stdio message") {
    val pretty =
      """{
        |  "jsonrpc": "2.0",
        |  "method": "ping",
        |  "id": "1"
        |}
        |""".stripMargin
    val in = new StringReader(pretty)
    val out = new StringWriter()
    val err = new StringWriter()

    transport.run(in, out, err)

    val output = lines(out.toString)
    assertEquals(output.size, 5)
    output.foreach(line => assertErrorLine(line, ErrorCode.ParseError, None))
  }

  test("Closed input ends the loop without stdout") {
    val in = new StringReader("")
    val out = new StringWriter()
    val err = new StringWriter()

    transport.run(in, out, err)

    assertEquals(out.toString, "")
    assertEquals(err.toString, "")
  }

  test("UTF-8 byte streams round-trip a non-ASCII request id") {
    val req = request(ping, StringRequestId("café"))
    val in = new ByteArrayInputStream(
      (McpCodec.MessageEncoder.encode(req) + "\n").getBytes(StandardCharsets.UTF_8)
    )
    val out = new ByteArrayOutputStream()
    val err = new ByteArrayOutputStream()

    transport.run(in, out, err)

    val stdout = new String(out.toByteArray, StandardCharsets.UTF_8)
    assertEquals(err.size(), 0)
    assertSuccessLine(stdout.stripLineEnd, req.id)
  }

  test("Numeric request id is correlated on the response") {
    val in = new StringReader(requestLine(ping, NumberRequestId(7L)) + "\n")
    val out = new StringWriter()
    val err = new StringWriter()

    transport.run(in, out, err)

    val output = lines(out.toString)
    assertEquals(output.size, 1)
    assertSuccessLine(output(0), NumberRequestId(7L))
  }

  test("Reader IOException propagates and the reader is not closed") {
    var closed = false
    val failing = new Reader {
      def read(buffer: Array[Char], offset: Int, length: Int): Int =
        throw new IOException("read-secret")
      def close(): Unit = closed = true
    }
    val out = new StringWriter()
    val err = new StringWriter()

    intercept[IOException](transport.run(failing, out, err))
    assertEquals(closed, false)
  }

  test("Writer IOException propagates and the writer is not closed") {
    var closed = false
    val failing = new Writer {
      def write(buffer: Array[Char], offset: Int, length: Int): Unit =
        throw new IOException("write-secret")
      def flush(): Unit = ()
      def close(): Unit = closed = true
    }
    val in = new StringReader(requestLine(ping, StringRequestId("1")) + "\n")
    val err = new StringWriter()

    intercept[IOException](transport.run(in, failing, err))
    assertEquals(closed, false)
  }

  test("Nonpositive or overflowing limits are rejected") {
    intercept[IllegalArgumentException](StdioTransport(server, maxMessageSize = 0))
    intercept[IllegalArgumentException](StdioTransport(server, maxMessageSize = -1))
    intercept[IllegalArgumentException](StdioTransport(server, maxMessageSize = Int.MaxValue))
    intercept[IllegalArgumentException](StdioTransport(server, maxNestingDepth = 0))
    intercept[IllegalArgumentException](StdioTransport(server, maxNestingDepth = -1))
    intercept[IllegalArgumentException](StdioTransport(server, maxNestingDepth = Int.MaxValue))
  }

  test("Empty and whitespace-only frames are parse errors") {
    val output = lines(runBytes("\n   \n"))
    assertEquals(output.size, 2)
    output.foreach(line => assertErrorLine(line, ErrorCode.ParseError, None))
  }

  test("CRLF and final unterminated frames are accepted") {
    val input = requestLine(ping, StringRequestId("a")) + "\r\n" +
      requestLine(ping, StringRequestId("b"))
    val output = lines(runBytes(input))
    assertEquals(output.size, 2)
    assertSuccessLine(output(0), StringRequestId("a"))
    assertSuccessLine(output(1), StringRequestId("b"))
  }

  test("Byte frames at the exact limit succeed; oversized frames are rejected once") {
    val line = requestLine(ping, StringRequestId("s"))
    val limited = StdioTransport(
      server,
      maxMessageSize = line.getBytes(StandardCharsets.UTF_8).length
    )
    val input = List(
      line,              // exact limit
      line + "\r",       // trailing CR does not count
      line + "x",        // one unit over
      line + " " * 4096, // far beyond capacity, still one error
      line               // recovery: final frame without LF
    ).mkString("\n")

    val output = lines(runBytes(input, limited))
    assertEquals(output.size, 5)
    assertSuccessLine(output(0), StringRequestId("s"))
    assertSuccessLine(output(1), StringRequestId("s"))
    assertErrorLine(
      output(2),
      ErrorCode.InvalidRequest,
      None,
      "stdio message exceeds size limit"
    )
    assertErrorLine(
      output(3),
      ErrorCode.InvalidRequest,
      None,
      "stdio message exceeds size limit"
    )
    assertSuccessLine(output(4), StringRequestId("s"))
  }

  test("Reader frames at the exact unit limit succeed; oversized ones are rejected") {
    val line = requestLine(ping, StringRequestId("r"))
    val limited = StdioTransport(server, maxMessageSize = line.length)
    val input = List(
      line,
      line + "\r",
      line + "x",
      line + " " * 4096,
      line
    ).mkString("\n")
    val in = new StringReader(input)
    val out = new StringWriter()
    val err = new StringWriter()

    limited.run(in, out, err)

    val output = lines(out.toString)
    assertEquals(output.size, 5)
    assertSuccessLine(output(0), StringRequestId("r"))
    assertSuccessLine(output(1), StringRequestId("r"))
    assertErrorLine(
      output(2),
      ErrorCode.InvalidRequest,
      None,
      "stdio message exceeds size limit"
    )
    assertErrorLine(
      output(3),
      ErrorCode.InvalidRequest,
      None,
      "stdio message exceeds size limit"
    )
    assertSuccessLine(output(4), StringRequestId("r"))
  }

  test("Byte limit counts UTF-8 bytes while Reader limit counts UTF-16 units") {
    val line = requestLine(ping, StringRequestId("café"))
    val limited = StdioTransport(server, maxMessageSize = line.length)

    // café's é is one UTF-16 unit but two UTF-8 bytes: the byte frame is over.
    val byteOutput = lines(runBytes(line + "\n", limited))
    assertEquals(byteOutput.size, 1)
    assertErrorLine(
      byteOutput(0),
      ErrorCode.InvalidRequest,
      None,
      "stdio message exceeds size limit"
    )

    val in = new StringReader(line + "\n")
    val out = new StringWriter()
    val err = new StringWriter()
    limited.run(in, out, err)
    val readerOutput = lines(out.toString)
    assertEquals(readerOutput.size, 1)
    assertSuccessLine(readerOutput(0), StringRequestId("café"))
  }

  test("Invalid UTF-8 yields ParseError lines without invoking the server") {
    var calls = 0
    val counting = McpServer(
      HandlerRegistryInMemory(
        Map(ping -> Handler.empty { _ =>
          calls += 1
          Right(())
        })
      )
    )
    val counted = StdioTransport(counting)
    val truncated = Array('{'.toByte, '"'.toByte, 0xC3.toByte)
    val poisoned = requestLine(ping, StringRequestId("z")).getBytes(StandardCharsets.UTF_8)
    poisoned(poisoned.indexOf('z'.toByte)) = 0xFF.toByte
    val valid = requestLine(ping, StringRequestId("ok")).getBytes(StandardCharsets.UTF_8)
    val input = truncated ++ Array('\n'.toByte) ++ poisoned ++
      Array('\n'.toByte) ++ valid ++ Array('\n'.toByte)
    val in = new ByteArrayInputStream(input)
    val out = new ByteArrayOutputStream()
    val err = new ByteArrayOutputStream()

    counted.run(in, out, err)

    val output = lines(new String(out.toByteArray, StandardCharsets.UTF_8))
    assertEquals(output.size, 3)
    assertErrorLine(output(0), ErrorCode.ParseError, None)
    assertErrorLine(output(1), ErrorCode.ParseError, None)
    assertSuccessLine(output(2), StringRequestId("ok"))
    assertEquals(calls, 1)
  }

  test("Nesting at the limit succeeds; one level deeper is rejected once") {
    val bounded = StdioTransport(server, maxNestingDepth = 8)
    // Frame objects add depth 3 (envelope, params, _meta); arrays add the rest.
    val atLimit = nestedRequestLine("ok", "[" * 5 + "]" * 5)
    val overLimit = nestedRequestLine("deep", "[" * 6 + "]" * 6)
    val input = atLimit + "\n" + overLimit + "\n" +
      requestLine(ping, StringRequestId("after")) + "\n"

    val output = lines(runBytes(input, bounded))
    assertEquals(output.size, 3)
    assertSuccessLine(output(0), StringRequestId("ok"))
    assertErrorLine(
      output(1),
      ErrorCode.InvalidRequest,
      None,
      "stdio message exceeds nesting limit"
    )
    assertSuccessLine(output(2), StringRequestId("after"))
  }

  test("Brackets and escapes inside strings do not count as nesting") {
    val sneaky = "a\\\"b" + ("[" * 200) + "}]}"
    val input = nestedRequestLine("str", "\"" + sneaky + "\"") + "\n"

    val output = lines(runBytes(input))
    assertEquals(output.size, 1)
    assertSuccessLine(output(0), StringRequestId("str"))
  }

  test("Broken OutputStream propagates IOException and stays open") {
    var closed = false
    val broken = new OutputStream {
      def write(b: Int): Unit = throw new IOException("broken output")
      override def close(): Unit = closed = true
    }
    val in = new ByteArrayInputStream(
      (requestLine(ping, StringRequestId("io")) + "\n").getBytes(StandardCharsets.UTF_8)
    )

    intercept[IOException](transport.run(in, broken, new ByteArrayOutputStream))
    assertEquals(closed, false)
  }

  test("Broken PrintStream surfaces an IOException and stays open") {
    var closed = false
    val broken = new OutputStream {
      def write(b: Int): Unit = throw new IOException("pipe dead")
      override def close(): Unit = closed = true
    }
    val printStream = new PrintStream(broken)
    val in = new ByteArrayInputStream(
      (requestLine(ping, StringRequestId("ps")) + "\n").getBytes(StandardCharsets.UTF_8)
    )

    val failure =
      intercept[IOException](transport.run(in, printStream, new ByteArrayOutputStream))
    assertEquals(failure.getMessage, "stdio output failure")
    assertEquals(closed, false)
  }

  test("Broken PrintWriter surfaces an IOException and stays open") {
    var closed = false
    val brokenWriter = new Writer {
      def write(buffer: Array[Char], offset: Int, length: Int): Unit =
        throw new IOException("dead writer")
      def flush(): Unit = ()
      def close(): Unit = closed = true
    }
    val printWriter = new PrintWriter(brokenWriter)
    val in = new StringReader(requestLine(ping, StringRequestId("pw")) + "\n")

    val failure = intercept[IOException](transport.run(in, printWriter, new StringWriter))
    assertEquals(failure.getMessage, "stdio output failure")
    assertEquals(closed, false)
  }

  test("Byte streams remain open after success and error frames") {
    var inClosed = false
    var outClosed = false
    var errClosed = false
    val in = new ByteArrayInputStream(
      ("{\n" + requestLine(ping, StringRequestId("s")) + "\n").getBytes(
        StandardCharsets.UTF_8
      )
    ) {
      override def close(): Unit = inClosed = true
    }
    val out = new ByteArrayOutputStream {
      override def close(): Unit = outClosed = true
    }
    val err = new ByteArrayOutputStream {
      override def close(): Unit = errClosed = true
    }

    transport.run(in, out, err)

    assertEquals(inClosed, false)
    assertEquals(outClosed, false)
    assertEquals(errClosed, false)
    assertEquals(lines(new String(out.toByteArray, StandardCharsets.UTF_8)).size, 2)
  }

  test("Readers and Writers remain open after rejected and successful frames") {
    var inClosed = false
    var outClosed = false
    var errClosed = false
    val in = new StringReader(
      "{\n" + requestLine(ping, StringRequestId("s")) + "\n"
    ) {
      override def close(): Unit = inClosed = true
    }
    val out = new StringWriter {
      override def close(): Unit = outClosed = true
    }
    val err = new StringWriter {
      override def close(): Unit = errClosed = true
    }

    transport.run(in, out, err)

    assertEquals(inClosed, false)
    assertEquals(outClosed, false)
    assertEquals(errClosed, false)
    assertEquals(lines(out.toString).size, 2)
  }

  test("InputStream IOException propagates with no protocol output and stays open") {
    var closed = false
    val in = new InputStream {
      def read(): Int = throw new IOException("read-secret")
      override def close(): Unit = closed = true
    }
    val out = new ByteArrayOutputStream()

    intercept[IOException](transport.run(in, out, new ByteArrayOutputStream))
    assertEquals(out.size(), 0)
    assertEquals(closed, false)
  }

  test("Writer flush failure propagates IOException and stays open") {
    var closed = false
    val flushFailing = new Writer {
      private val buffer = new StringWriter()
      def write(data: Array[Char], offset: Int, length: Int): Unit =
        buffer.write(data, offset, length)
      def flush(): Unit = throw new IOException("flush-secret")
      def close(): Unit = closed = true
    }
    val in = new StringReader(requestLine(ping, StringRequestId("f")) + "\n")

    val failure = intercept[IOException](transport.run(in, flushFailing, new StringWriter))
    assertEquals(failure.getMessage, "flush-secret")
    assertEquals(closed, false)
  }

  test("Broken PrintStream stderr surfaces after the reply was written") {
    var closed = false
    val broken = new OutputStream {
      def write(b: Int): Unit = throw new IOException("err pipe dead")
      override def close(): Unit = closed = true
    }
    val errPrint = new PrintStream(broken)
    val in = new ByteArrayInputStream(
      ("{\n" + requestLine(ping, StringRequestId("s")) + "\n")
        .getBytes(StandardCharsets.UTF_8)
    )
    val out = new ByteArrayOutputStream()

    val failure = intercept[IOException](transport.run(in, out, errPrint))

    assertEquals(failure.getMessage, "stdio output failure")
    assertEquals(closed, false)
    val output = lines(new String(out.toByteArray, StandardCharsets.UTF_8))
    assertEquals(output.size, 1)
    assertErrorLine(output(0), ErrorCode.ParseError, None)
  }
}
