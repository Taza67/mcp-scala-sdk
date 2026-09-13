package io.github.taza67.mcp.codec

import munit.FunSuite



class WireLimitsSuite extends FunSuite {

  test("nesting at the limit passes, one past rejects") {
    val legal = "[" * 128 + "]" * 128
    assert(!WireLimits.exceedsNesting(legal, WireLimits.DefaultMaxNestingDepth))
    val over = "[" * 129 + "]" * 129
    assert(WireLimits.exceedsNesting(over, WireLimits.DefaultMaxNestingDepth))
  }

  test("braces inside strings and escaped quotes are ignored") {
    assert(!WireLimits.exceedsNesting("""{"a":"{{{{"}}""", 1))
    assert(!WireLimits.exceedsNesting("""{"a":"\"}}}}"}""", 1))
    assert(WireLimits.exceedsNesting("""{"a":{"b":{}}}""", 2))
  }

  test("non-positive depth fails fast") {
    intercept[IllegalArgumentException](WireLimits.exceedsNesting("{}", 0))
    intercept[IllegalArgumentException](WireLimits.exceedsNesting("{}", -1))
  }
}
