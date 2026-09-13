package io.github.taza67.mcp.codec



/** Shared wire-input limits and a cheap pre-parse nesting guard.
 *
 *  The guard counts `{}` and `[]` depth on raw JSON text while skipping string
 *  contents and backslash escapes; it is not a JSON parser. Validating the
 *  document itself remains the decoder's job.
 */
object WireLimits {

  /** Default maximum message size: 8 MiB of UTF-8 bytes. */
  val DefaultMaxMessageSize: Int = 8 * 1024 * 1024

  /** Default maximum JSON nesting depth per message. */
  val DefaultMaxNestingDepth: Int = 128

  /** True when `text` nests `{}`/`[]` deeper than `maxNestingDepth`. */
  def exceedsNesting(text: String, maxNestingDepth: Int): Boolean = {
    require(
      maxNestingDepth > 0,
      "maxNestingDepth must be positive"
    )
    var depth = 0
    var inString = false
    var escaped = false
    var exceeded = false
    var i = 0
    while (!exceeded && i < text.length) {
      val c = text.charAt(i)
      if (inString) {
        if (escaped) escaped = false
        else if (c == '\\') escaped = true
        else if (c == '"') inString = false
      } else if (c == '"') inString = true
      else if (c == '{' || c == '[') {
        depth += 1
        if (depth > maxNestingDepth) exceeded = true
      } else if (c == '}' || c == ']') depth -= 1
      i += 1
    }
    exceeded
  }
}
