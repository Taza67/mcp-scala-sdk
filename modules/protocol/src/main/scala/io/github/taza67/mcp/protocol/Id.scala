package io.github.taza67.mcp.protocol

sealed trait Id

case class StringId(value: String) extends Id

case class NumberId(value: Integer) extends Id
