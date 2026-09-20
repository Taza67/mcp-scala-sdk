package io.github.taza67.mcp.transport.http

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

import io.github.taza67.mcp.codec.DecodingError
import munit.FunSuite



class SseSuite extends FunSuite {

  private def reader(text: String, maxEventBytes: Int = 1024): SseReader =
    new SseReader(
      new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)),
      maxEventBytes
    )

  private def readerOf(bytes: Array[Byte], maxEventBytes: Int = 1024): SseReader =
    new SseReader(new ByteArrayInputStream(bytes), maxEventBytes)

  private val invalid = Left(DecodingError("Invalid SSE event"))

  test("encode writes one data field per line and a blank delimiter") {
    assertEquals(Sse.encode("{\"a\":1}"), "data: {\"a\":1}\n\n")
    assertEquals(Sse.encode("a\nb"), "data: a\ndata: b\n\n")
    assertEquals(Sse.encode("a\r\nb"), "data: a\ndata: b\n\n")
  }

  test("an LF-delimited event yields its data payload") {
    val r = reader("data: {\"a\":1}\n\n")
    assertEquals(r.next(), Right(Some("{\"a\":1}")))
    assertEquals(r.next(), Right(None))
  }

  test("CRLF and bare CR delimiters dispatch identically") {
    val crlf = reader("data: x\r\n\r\n")
    assertEquals(crlf.next(), Right(Some("x")))

    val cr = reader("data: x\r\r")
    assertEquals(cr.next(), Right(Some("x")))
    assertEquals(cr.next(), Right(None))
  }

  test("a bare CR does not consume the next line's first byte") {
    // If the parser ate the byte after CR, the second line would corrupt.
    val r = reader("data: a\rdata: b\n\n")
    assertEquals(r.next(), Right(Some("a\nb")))
    assertEquals(r.next(), Right(None))
  }

  test("a stream ending right after a final CR delimiter needs no more bytes") {
    val r = reader("data: x\r")
    assertEquals(r.next(), Right(None))
  }

  test("multiple data lines join with a single newline") {
    val r = reader("data: a\ndata: b\ndata: c\n\n")
    assertEquals(r.next(), Right(Some("a\nb\nc")))
  }

  test("data without space after the colon is accepted") {
    val r = reader("data:x\n\n")
    assertEquals(r.next(), Right(Some("x")))
  }

  test("comments and non-data fields are ignored without dispatching") {
    val r = reader(": ping\n\nevent: e\nid: 9\nretry: 1\ndata: x\n\n")
    assertEquals(r.next(), Right(Some("x")))
    assertEquals(r.next(), Right(None))
  }

  test("a comment-only block at EOF yields no event") {
    val r = reader(": only\n\n")
    assertEquals(r.next(), Right(None))
  }

  test("a leading UTF-8 BOM is skipped") {
    val bytes =
      Array(0xef, 0xbb, 0xbf).map(_.toByte) ++ "data: x\n\n".getBytes(
        StandardCharsets.UTF_8
      )
    assertEquals(readerOf(bytes).next(), Right(Some("x")))
  }

  test("an unfinished event at EOF is discarded") {
    val r = reader("data: pending\n")
    assertEquals(r.next(), Right(None))
  }

  test("invalid UTF-8 fails statically") {
    val bytes =
      "data: ".getBytes(StandardCharsets.UTF_8) ++
        Array(0xff.toByte) ++ "\n\n".getBytes(StandardCharsets.UTF_8)
    assertEquals(readerOf(bytes).next(), invalid)
  }

  test("a physical line beyond the limit fails") {
    val r = reader("data: abcdef\n\n", maxEventBytes = 8)
    assertEquals(r.next(), invalid)
  }

  test("combined data beyond the event limit fails") {
    val r =
      reader("data: aaa\ndata: bbb\ndata: ccc\n\n", maxEventBytes = 10)
    assertEquals(r.next(), invalid)
  }

  test("the reader rejects non-positive limits") {
    List(0, -1, Int.MaxValue).foreach { limit =>
      intercept[IllegalArgumentException](
        reader("data: x\n\n", maxEventBytes = limit)
      )
    }
  }

  test("the data boundary fits exactly and fails one byte over") {
    // The physical lines are 15 and 16 bytes; joined data is 9 + 1 + 10 = 20.
    val exact =
      reader(
        "data: 123456789\ndata: 0123456789\n\n",
        maxEventBytes = 20
      )
    assertEquals(exact.next(), Right(Some("123456789\n0123456789")))

    // One extra data byte crosses the event budget; both lines still fit.
    val over =
      reader(
        "data: 123456789\ndata: 01234567890\n\n",
        maxEventBytes = 20
      )
    assertEquals(over.next(), invalid)
  }

  test("a bare CR event dispatches without reading past the delimiter") {
    val bytes = "data: x\r\r".getBytes(StandardCharsets.UTF_8)
    val noExtraReads = new java.io.InputStream {
      private var position = 0
      def read(): Int = {
        if (position >= bytes.length)
          throw new java.io.IOException("read past the event delimiter")
        val b = bytes(position).toInt & 0xff
        position += 1
        b
      }
    }
    val r = new SseReader(noExtraReads, maxEventBytes = 64)
    assertEquals(r.next(), Right(Some("x")))
  }

  test("the event budget resets after each dispatched event") {
    // 14-byte lines fit the line budget; two 8-byte events would exceed a
    // 14-byte budget only if it failed to reset after each dispatch.
    val r = reader(
      "data: aaaaaaaa\n\ndata: bbbbbbbb\n\n",
      maxEventBytes = 14
    )
    assertEquals(r.next(), Right(Some("aaaaaaaa")))
    assertEquals(r.next(), Right(Some("bbbbbbbb")))
  }
}
