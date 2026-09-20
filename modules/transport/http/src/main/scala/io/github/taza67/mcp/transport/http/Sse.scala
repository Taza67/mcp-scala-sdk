package io.github.taza67.mcp.transport.http

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.PushbackInputStream

import io.github.taza67.mcp.codec.DecodingError



/** Minimal SSE (`text/event-stream`) wire helpers.
 *
 *  Only `data` fields are carried; `event`, `id`, `retry`, and comments are
 *  parsed past on read and never emitted on write. No resumption support.
 */
object Sse {

  /** Encodes `data` as one event: each CR/LF-delimited line becomes a
   *  `data: ` field line and the event ends with a blank line.
   */
  def encode(data: String): String =
    data
      .split("\r\n|\r|\n", -1)
      .map(line => s"data: $line\n")
      .mkString + "\n"
}

/** Bounded strict-UTF-8 SSE event reader over a borrowed stream.
 *
 *  `next()` returns the joined `data` payload of the next event, or `None`
 *  at end of stream (an unfinished trailing event is discarded). Physical
 *  lines and the combined event payload are each bounded by `maxEventBytes`;
 *  comment lines do not dispatch events. A lone CR terminates a line
 *  immediately without reading ahead; a following LF is skipped at the start
 *  of the next line read. Failures are the static `Invalid SSE event` error.
 */
private[http] final class SseReader(in: InputStream, maxEventBytes: Int) {
  require(
    maxEventBytes > 0 && maxEventBytes < Int.MaxValue,
    "maxEventBytes must be positive and less than Int.MaxValue"
  )

  private val input = new PushbackInputStream(in, 3)
  private var bomSkipped = false
  private var pendingSkipLf = false
  private var eof = false

  private def invalid: DecodingError = DecodingError("Invalid SSE event")

  /** Skips a leading UTF-8 BOM if present. */
  private def skipBom(): Unit = {
    val first = input.read()
    if (first == 0xef) {
      val second = input.read()
      val third = input.read()
      if (second == 0xbb && third == 0xbf) ()
      else {
        if (third >= 0) input.unread(third)
        if (second >= 0) input.unread(second)
        input.unread(first)
      }
    } else if (first >= 0) input.unread(first)
  }

  /** Reads one physical line as raw bytes (UTF-8 sequences never contain CR or
   *  LF bytes, so byte-level splitting is safe). `None` at end of stream; a
   *  partial line at EOF is discarded.
   */
  private def readLineBytes(): Either[DecodingError, Option[Array[Byte]]] = {
    if (pendingSkipLf) {
      pendingSkipLf = false
      val next = input.read()
      if (next < 0) {
        eof = true
        return Right(None)
      }
      if (next != '\n') input.unread(next)
    }
    val buffer = new ByteArrayOutputStream()
    var reading = true
    while (reading) {
      val b = input.read()
      if (b < 0) {
        eof = true
        return Right(None)
      } else if (b == '\n') reading = false
      else if (b == '\r') {
        pendingSkipLf = true
        reading = false
      } else {
        if (buffer.size() >= maxEventBytes) return Left(invalid)
        buffer.write(b)
      }
    }
    Right(Some(buffer.toByteArray))
  }

  /** Byte offset of the value in a `data` field line, if it is one. */
  private def dataValueOffset(line: String): Option[Int] =
    if (line == "data") Some(line.length)
    else if (line.startsWith("data:")) {
      var offset = "data:".length
      if (offset < line.length && line.charAt(offset) == ' ') offset += 1
      Some(offset)
    } else None

  def next(): Either[DecodingError, Option[String]] = {
    if (!bomSkipped) {
      bomSkipped = true
      skipBom()
    }
    if (eof) return Right(None)
    val data = new StringBuilder
    var dataBytes = 0
    var sawData = false
    var result: Either[DecodingError, Option[String]] = Right(None)
    var reading = true
    while (reading) {
      readLineBytes() match {
        case Left(error) =>
          result = Left(error)
          reading = false
        case Right(None) =>
          reading = false
        case Right(Some(bytes)) =>
          HttpUtf8.decode(bytes) match {
            case Left(_) =>
              result = Left(invalid)
              reading = false
            case Right(line) =>
              if (line.isEmpty) {
                if (sawData) {
                  result = Right(Some(data.toString))
                  reading = false
                }
                // Comment-only blocks fall through without dispatching.
              } else if (line.charAt(0) != ':')
                dataValueOffset(line) match {
                  case Some(offset) =>
                    val valueBytes = bytes.length - offset
                    val joiner = if (sawData) 1 else 0
                    // Compare against the remaining budget so a large limit
                    // cannot overflow the accumulator arithmetic.
                    if (valueBytes > maxEventBytes - dataBytes - joiner) {
                      result = Left(invalid)
                      reading = false
                    } else {
                      if (sawData) data.append('\n')
                      data.append(line.substring(offset))
                      dataBytes += valueBytes + joiner
                      sawData = true
                    }
                  case None => ()
                }
          }
      }
    }
    result
  }
}
