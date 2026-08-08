package io.github.taza67.mcp.protocol.mcp

/** Describes a client or server MCP implementation.
 *
 *  @param name Programmatic / logical name; also display fallback when `title` is absent.
 *  @param version Implementation version string.
 *  @param title Human-readable title for UI contexts.
 *  @param description Optional description of purpose and capabilities.
 *  @param websiteUrl Optional website URL.
 *  @param icons Optional UI icons. Clients that render icons MUST support `image/png`
 *               and `image/jpeg`; SHOULD also support `image/svg+xml` and `image/webp`.
 */
case class Implementation(
    name: String,
    version: String,
    title: Option[String] = None,
    description: Option[String] = None,
    websiteUrl: Option[String] = None,
    icons: Option[List[Icon]] = None
)

object Implementation {
  val NameKey: String = "name"
  val VersionKey: String = "version"
  val TitleKey: String = "title"
  val DescriptionKey: String = "description"
  val WebsiteUrlKey: String = "websiteUrl"
  val IconsKey: String = "icons"
}
