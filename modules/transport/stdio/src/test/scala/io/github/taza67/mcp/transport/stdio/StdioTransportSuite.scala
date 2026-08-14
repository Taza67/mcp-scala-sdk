package io.github.taza67.mcp.transport.stdio

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.StringReader
import java.io.StringWriter
import java.nio.charset.StandardCharsets

import io.github.taza67.mcp.codec.circe.McpCodec
import io.github.taza67.mcp.protocol.jsonrpc.Method
import io.github.taza67.mcp.protocol.jsonrpc.StringRequestId
import io.github.taza67.mcp.protocol.mcp.McpNotification
import io.github.taza67.mcp.protocol.mcp.McpRequest
import io.github.taza67.mcp.protocol.mcp.McpSuccessResponse
import io.github.taza67.mcp.protocol.mcp.Result
import io.github.taza67.mcp.server.Handler
import io.github.taza67.mcp.server.HandlerRegistryInMemory
import io.github.taza67.mcp.server.McpServer
import munit.FunSuite



class StdioTransportSuite extends FunSuite {

  private val ping = Method("ping")
  private val transport = StdioTransport(
    McpServer(
      HandlerRegistryInMemory(Map(ping -> Handler.empty(_ => Right(()))))
    )
  )

  test("Request line yields one compact response line on stdout") {
    val request = McpRequest(method = ping, id = StringRequestId("1"))
    val in = new StringReader(McpCodec.MessageEncoder.encode(request) + "\n")
    val out = new StringWriter()
    val err = new StringWriter()

    transport.run(in, out, err)

    val stdout = out.toString
    assertEquals(err.toString, "")
    assert(!stdout.contains('\r'))
    assertEquals(stdout.count(_ == '\n'), 1)
    assertEquals(
      McpCodec.MessageDecoder.decode(stdout.stripLineEnd),
      Right(McpSuccessResponse(result = Result.empty(), id = request.id))
    )
  }

  test("Notification line writes nothing to stdout") {
    val notification = McpNotification(method = Method("notifications/cancelled"))
    val in = new StringReader(McpCodec.MessageEncoder.encode(notification) + "\n")
    val out = new StringWriter()
    val err = new StringWriter()

    transport.run(in, out, err)

    assertEquals(out.toString, "")
    assertEquals(err.toString, "")
  }

  test("Invalid JSON writes stderr and leaves stdout empty") {
    val in = new StringReader("{\n")
    val out = new StringWriter()
    val err = new StringWriter()

    transport.run(in, out, err)

    assertEquals(out.toString, "")
    assert(err.toString.nonEmpty)
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

    assertEquals(out.toString, "")
    assert(err.toString.nonEmpty)
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
    val request = McpRequest(method = ping, id = StringRequestId("café"))
    val in = new ByteArrayInputStream(
      (McpCodec.MessageEncoder.encode(request) + "\n").getBytes(StandardCharsets.UTF_8)
    )
    val out = new ByteArrayOutputStream()
    val err = new ByteArrayOutputStream()

    transport.run(in, out, err)

    val stdout = new String(out.toByteArray, StandardCharsets.UTF_8)
    assertEquals(err.size(), 0)
    assertEquals(
      McpCodec.MessageDecoder.decode(stdout.stripLineEnd),
      Right(McpSuccessResponse(result = Result.empty(), id = request.id))
    )
  }
}
