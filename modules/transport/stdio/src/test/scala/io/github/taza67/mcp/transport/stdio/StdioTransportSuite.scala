package io.github.taza67.mcp.transport.stdio

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
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
import io.github.taza67.mcp.protocol.mcp.McpNotification
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
  private val transport = StdioTransport(
    McpServer(
      HandlerRegistryInMemory(Map(ping -> Handler.empty(_ => Right(()))))
    )
  )

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
}
