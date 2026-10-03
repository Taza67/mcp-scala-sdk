package io.github.taza67.mcp.transport.stdio

import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger

import io.github.taza67.mcp.protocol.jsonrpc.ErrorCode
import io.github.taza67.mcp.protocol.jsonrpc.InvalidRequestError
import io.github.taza67.mcp.protocol.jsonrpc.ParseError
import munit.FunSuite



class StdioFramesSuite extends FunSuite {

  private def streamOf(bytes: Array[Byte]): BufferedInputStream =
    new BufferedInputStream(new ByteArrayInputStream(bytes))

  private def bytes(text: String): Array[Byte] =
    text.getBytes(StandardCharsets.UTF_8)

  test("reads a fragmented multibyte UTF-8 frame") {
    // e-acute (U+00E9) occupies two UTF-8 bytes inside the frame.
    val accent = 0xe9.toChar.toString.getBytes(StandardCharsets.UTF_8)
    val frame =
      Array.concat(bytes("{\"a\":\""), accent, bytes("\"}")) :+ '\n'.toByte
    assertEquals(
      StdioFrames.read(streamOf(frame), maxMessageSize = 64),
      Right(Some("{\"a\":\"" + 0xe9.toChar + "\"}"))
    )
  }

  test("LF terminates a frame and a trailing CR is excluded") {
    val in = streamOf(bytes("one\r\ntwo\n"))
    assertEquals(StdioFrames.read(in, 64), Right(Some("one\r")))
    assertEquals(StdioFrames.read(in, 64), Right(Some("two")))
    assertEquals(StdioFrames.read(in, 64), Right(None))
  }

  test("a trailing CR is excluded from the size accounting") {
    // "ab\r" stores 3 bytes; the effective size of 2 fits the limit of 2.
    assertEquals(StdioFrames.read(streamOf(bytes("ab\r\n")), 2), Right(Some("ab\r")))
    // Against limit 1 even the CR-excluded size is too big.
    assertEquals(
      StdioFrames.read(streamOf(bytes("ab\r\n")), 1),
      Left(InvalidRequestError("stdio message exceeds size limit"))
    )
  }

  test("a partial final frame at EOF is delivered") {
    assertEquals(
      StdioFrames.read(streamOf(bytes("tail")), 64),
      Right(Some("tail"))
    )
  }

  test("clean EOF returns None") {
    assertEquals(StdioFrames.read(streamOf(Array.emptyByteArray), 64), Right(None))
  }

  test("invalid UTF-8 fails with a static parse error") {
    val in = streamOf(Array(0xff.toByte, 0xfe.toByte) :+ '\n'.toByte)
    assertEquals(StdioFrames.read(in, 64), Left(ParseError()))
  }

  test("oversized frames are rejected and the remainder is drained") {
    val in = streamOf(bytes("too-big\nnext\n"))
    assertEquals(
      StdioFrames.read(in, maxMessageSize = 3),
      Left(InvalidRequestError("stdio message exceeds size limit"))
    )
    assertEquals(StdioFrames.read(in, 64), Right(Some("next")))
  }

  test("huge oversize drains through LF without unbounded storage") {
    val in = streamOf(
      Array.concat(Array.fill[Byte](100000)('x'), Array('\n'.toByte), bytes("ok\n"))
    )
    val result = StdioFrames.read(in, 4)
    assert(result.isLeft, s"expected rejection, got $result")
    result match {
      case Left(error) => assertEquals(error.code, ErrorCode.InvalidRequest)
      case Right(_)    => fail("expected rejection")
    }
    assertEquals(StdioFrames.read(in, 64), Right(Some("ok")))
  }

  test("read never closes the borrowed input") {
    val closes = new AtomicInteger(0)
    val borrowed = new BufferedInputStream(new ByteArrayInputStream(bytes("x\n"))) {
      override def close(): Unit = {
        closes.incrementAndGet()
        super.close()
      }
    }
    assertEquals(StdioFrames.read(borrowed, 64), Right(Some("x")))
    assertEquals(StdioFrames.read(borrowed, 64), Right(None))
    assertEquals(closes.get(), 0)
  }

  test("IOException from the input propagates") {
    val failing = new BufferedInputStream(new InputStream {
      override def read(): Int = throw new IOException("injected read failure")
    })
    val caught =
      try {
        StdioFrames.read(failing, 64)
        fail("expected IOException")
      } catch { case e: IOException => e }
    assertEquals(caught.getMessage, "injected read failure")
  }

  test("encode round-trips strict UTF-8") {
    val text = "h" + 0xe9.toChar + "llo"
    assertEquals(
      StdioFrames.encode(text).toOption.map(_.toSeq),
      Some(text.getBytes(StandardCharsets.UTF_8).toSeq)
    )
  }

  test("encode rejects unpaired UTF-16 surrogates") {
    val lone = "bad " + 0xd800.toChar + " lead"
    val result = StdioFrames.encode(lone)
    assert(result.isLeft, s"expected failure, got $result")
    result match {
      case Left(error) =>
        assertEquals(error.cause, None)
        assertEquals(error.message, "Message is not encodable as UTF-8")
      case Right(_) => fail("expected rejection")
    }
  }
}
