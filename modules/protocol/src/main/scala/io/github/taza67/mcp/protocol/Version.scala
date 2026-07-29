package io.github.taza67.mcp.protocol



sealed trait Version

case object Version20 extends Version {
    val value: String = "2.0"
}
