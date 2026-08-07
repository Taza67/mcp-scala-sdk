package io.github.taza67.mcp.protocol.mcp

/** Sender or recipient of messages and data in a conversation. */
sealed trait Role {
  def value: String
}

object Role {

  /** Classify a wire role string. */
  def fromValue(value: String): Option[Role] =
    value match {
      case UserRole.value      => Some(UserRole)
      case AssistantRole.value => Some(AssistantRole)
      case _                   => None
    }
}

case object UserRole extends Role {
  val value: String = "user"
}

case object AssistantRole extends Role {
  val value: String = "assistant"
}
