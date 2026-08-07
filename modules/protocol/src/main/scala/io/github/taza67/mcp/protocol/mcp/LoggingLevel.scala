package io.github.taza67.mcp.protocol.mcp

/** Severity of a log message (syslog / RFC-5424).
 *
 *  @see [[https://datatracker.ietf.org/doc/html/rfc5424#section-6.2.1 RFC 5424 §6.2.1]]
 *  @deprecated Deprecated as of protocol version 2026-07-28 (SEP-2577). Remains for at least twelve months.
 */
sealed trait LoggingLevel {
  def value: String
}

object LoggingLevel {

  /** Classify a wire log-level string into the typed hierarchy. */
  def fromValue(value: String): Option[LoggingLevel] =
    value match {
      case DebugLoggingLevel.value     => Some(DebugLoggingLevel)
      case InfoLoggingLevel.value      => Some(InfoLoggingLevel)
      case NoticeLoggingLevel.value    => Some(NoticeLoggingLevel)
      case WarningLoggingLevel.value   => Some(WarningLoggingLevel)
      case ErrorLoggingLevel.value     => Some(ErrorLoggingLevel)
      case CriticalLoggingLevel.value  => Some(CriticalLoggingLevel)
      case AlertLoggingLevel.value     => Some(AlertLoggingLevel)
      case EmergencyLoggingLevel.value => Some(EmergencyLoggingLevel)
      case _                           => None
    }
}

case object DebugLoggingLevel extends LoggingLevel { val value: String = "debug" }
case object InfoLoggingLevel extends LoggingLevel { val value: String = "info" }
case object NoticeLoggingLevel extends LoggingLevel { val value: String = "notice" }
case object WarningLoggingLevel extends LoggingLevel { val value: String = "warning" }
case object ErrorLoggingLevel extends LoggingLevel { val value: String = "error" }
case object CriticalLoggingLevel extends LoggingLevel { val value: String = "critical" }
case object AlertLoggingLevel extends LoggingLevel { val value: String = "alert" }
case object EmergencyLoggingLevel extends LoggingLevel { val value: String = "emergency" }
