package io.github.taza67.mcp.protocol.mcp.elicitation

/** User action in response to an elicitation. */
sealed trait ElicitAction {
  def value: String
}

/** User submitted the form / confirmed the action. */
case object ElicitAccept extends ElicitAction { val value = "accept" }

/** User explicitly declined the action. */
case object ElicitDecline extends ElicitAction { val value = "decline" }

/** User dismissed without making an explicit choice. */
case object ElicitCancel extends ElicitAction { val value = "cancel" }

/** Submitted form value (string, number, boolean, or string list). */
sealed trait ElicitContentValue

case class ElicitStringValue(value: String) extends ElicitContentValue

case class ElicitNumberValue(value: BigDecimal) extends ElicitContentValue

case class ElicitBooleanValue(value: Boolean) extends ElicitContentValue

case class ElicitStringListValue(value: List[String]) extends ElicitContentValue

/** Client response to an [[ElicitRequest]].
 *
 *  @param action Whether the user accepted, declined, or cancelled.
 *  @param content Submitted form data when `action` is accept and mode was form.
 *                 Omitted for URL-mode accept responses.
 */
case class ElicitResult(
    action: ElicitAction,
    content: Option[Map[String, ElicitContentValue]] = None
)
