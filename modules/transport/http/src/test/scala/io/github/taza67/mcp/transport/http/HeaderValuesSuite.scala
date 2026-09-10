package io.github.taza67.mcp.transport.http

import io.github.taza67.mcp.codec.DecodingError
import munit.FunSuite



class HeaderValuesSuite extends FunSuite {

  private val invalid = Left(DecodingError("Invalid HTTP header value"))

  test("plain ASCII, empty, and inner whitespace values pass through") {
    List("", "abc", "a\tb", "x y", "tok=abc123", "=?BASE64?x?=").foreach { value =>
      assertEquals(HeaderValues.encode(value), Right(value), s"encode $value")
      assertEquals(HeaderValues.decode(value), Right(value), s"decode $value")
    }
  }

  test("unicode, outer whitespace, and control values encode to marked Base64") {
    val cases = List(
      "caf\u00e9"             -> "=?base64?Y2Fmw6k=?=",
      "Hello, \u4e16\u754c"   -> "=?base64?SGVsbG8sIOS4lueVjA==?=",
      " lead"                -> "=?base64?IGxlYWQ=?=",
      "trail "               -> "=?base64?dHJhaWwg?=",
      "\tx"                  -> "=?base64?CXg=?=",
      "a\nb"                 -> "=?base64?YQpi?="
    )
    cases.foreach { case (plain, encoded) =>
      assertEquals(HeaderValues.encode(plain), Right(encoded), s"encode $plain")
      assertEquals(HeaderValues.decode(encoded), Right(plain), s"decode $encoded")
    }
  }

  test("sentinel-looking plain values encode to avoid ambiguity") {
    val tricky = "=?base64?AAA=?="
    val wrapped = "=?base64?PT9iYXNlNjQ/QUFBPT89?="
    assertEquals(HeaderValues.encode(tricky), Right(wrapped))
    assertEquals(HeaderValues.decode(wrapped), Right(tricky))
    // Prefix or suffix alone is not an encoded value.
    assertEquals(HeaderValues.decode("=?base64?x"), Right("=?base64?x"))
    assertEquals(HeaderValues.decode("x?="), Right("x?="))
  }

  test("malformed Base64 and invalid UTF-8 payloads reject with a generic error") {
    List(
      "=?base64?!!!?=",
      "=?base64?A?=",    // invalid Base64 padding
      "=?base64?/w==?=" // decodes to 0xFF, not valid UTF-8
    ).foreach { value =>
      assertEquals(HeaderValues.decode(value), invalid, s"decode $value")
    }
  }

  test("plain decode rejects unicode, controls, and outer whitespace") {
    List("caf\u00e9", "a\nb", "x\u0000y", " x", "x ", "\tx", "x\t").foreach { value =>
      assertEquals(HeaderValues.decode(value), invalid, s"decode $value")
    }
  }

  test("unpaired UTF-16 surrogate encode fails instead of coercing") {
    assertEquals(HeaderValues.encode("a\ud800b"), invalid)
    assertEquals(HeaderValues.encode("\udc00"), invalid)
  }
}
