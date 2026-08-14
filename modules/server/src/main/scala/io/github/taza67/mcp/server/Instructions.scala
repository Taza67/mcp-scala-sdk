package io.github.taza67.mcp.server

/** Natural-language guidance advertised on `server/discover`.
 *
 *  Blank text is omitted on the wire.
 */
case class Instructions(text: String) {

  def toDiscoverField: Option[String] = {
    val trimmed = text.trim
    if (trimmed.isEmpty) None else Some(trimmed)
  }
}

object Instructions {
  val none: Instructions = Instructions("")
}
