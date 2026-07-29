package io.github.taza67.mcp.protocol



sealed trait ID

case class StringID(value: String) extends ID

case class NumberID(value: Integer) extends ID