package io.github.taza67.mcp.protocol.mcp.elicitation

import io.github.taza67.mcp.protocol.jsonrpc.{JsonRpcVersion, JsonRpcVersion20, Method, RequestId}



/** Method name for user elicitation: `"elicitation/create"`. */
object Elicitation {
  val create: Method = Method("elicitation/create")
}

/** Parameters for an elicitation request (form or URL mode). */
sealed trait ElicitRequestParams {
  def message: String
}

object ElicitRequestParams {

  /** Wire key for the user-facing message. */
  val MessageKey: String = "message"

  /** Wire key for elicitation mode (`form` / `url`). */
  val ModeKey: String = "mode"

  /** Whether wire params are URL-mode (`mode` is url, or `url` is present). */
  def isUrlMode(mode: Option[String], hasUrl: Boolean): Boolean =
    mode.contains(ElicitRequestUrlParams.ModeValue) || hasUrl
}

/** Elicit non-sensitive information via a form in the client.
 *
 *  @param message Message describing what information is requested.
 *  @param requestedSchema Restricted JSON Schema (top-level primitives only).
 *  @param mode Elicitation mode; defaults to form when absent on the wire.
 */
case class ElicitRequestFormParams(
    message: String,
    requestedSchema: ElicitRequestedSchema,
    mode: Option[String] = Some("form")
) extends ElicitRequestParams

object ElicitRequestFormParams {

  /** Wire value for form mode. */
  val ModeValue: String = "form"

  /** Wire key for the requested form schema. */
  val RequestedSchemaKey: String = "requestedSchema"

  /** Default form `mode` when absent on the wire. */
  def modeFromWire(mode: Option[String]): Option[String] =
    mode.orElse(Some(ModeValue))
}

/** Elicit information via a URL the user should open (e.g. sensitive credentials).
 *
 *  @param message Explanation of why the interaction is needed.
 *  @param url URL the user should navigate to.
 */
case class ElicitRequestUrlParams(
    message: String,
    url: String
) extends ElicitRequestParams {
  val mode: String = ElicitRequestUrlParams.ModeValue
}

object ElicitRequestUrlParams {

  /** Wire value for URL mode. */
  val ModeValue: String = "url"

  /** Wire key for the URL the user should open. */
  val UrlKey: String = "url"
}

/** Request from the server to elicit additional information from the user via the client.
 *
 *  Direction is server → client. The client must have declared the matching
 *  elicitation capability (`form` and/or `url`).
 */
case class ElicitRequest(
    id: RequestId,
    params: ElicitRequestParams,
    jsonrpc: JsonRpcVersion = JsonRpcVersion20
) {
  val method: Method = Elicitation.create
}
