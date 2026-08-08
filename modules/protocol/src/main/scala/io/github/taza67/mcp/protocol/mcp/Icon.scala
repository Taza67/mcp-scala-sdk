package io.github.taza67.mcp.protocol.mcp

/** Visual theme an [[Icon]] is designed for. */
sealed trait IconTheme {
  def value: String
}

object IconTheme {

  /** Classify a wire icon-theme string. */
  def fromValue(value: String): Option[IconTheme] =
    value match {
      case LightIconTheme.value => Some(LightIconTheme)
      case DarkIconTheme.value  => Some(DarkIconTheme)
      case _                    => None
    }
}

case object LightIconTheme extends IconTheme {
  val value: String = "light"
}

case object DarkIconTheme extends IconTheme {
  val value: String = "dark"
}

/** Optionally-sized icon for a user interface.
 *
 *  Consumers SHOULD prefer icons from the same or a trusted domain, and take
 *  care with SVGs (they may contain executable JavaScript).
 *
 *  @param src HTTP(S) URL or `data:` URI with Base64 image data.
 *  @param mimeType MIME override when the source type is missing or generic
 *                  (e.g. `"image/png"`, `"image/svg+xml"`).
 *  @param sizes Sizes in `WxH` form (e.g. `"48x48"`) or `"any"` for scalable formats.
 *  @param theme Designed for a light or dark background; absent means any theme.
 */
case class Icon(
    src: String,
    mimeType: Option[String] = None,
    sizes: Option[List[String]] = None,
    theme: Option[IconTheme] = None
)

object Icon {
  val SrcKey: String = "src"
  val MimeTypeKey: String = "mimeType"
  val SizesKey: String = "sizes"
  val ThemeKey: String = "theme"
}
