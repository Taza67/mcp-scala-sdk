package io.github.taza67.mcp.protocol

/** MCP protocol types for revision 2026-07-28.
 *
 *  Layout under [[mcp]]:
 *   - foundation types at the package root (`Meta`, `Capabilities`, `Message`, …)
 *   - method domains in subpackages (`tools`, `resources`, `prompts`, …)
 *   - shared helpers (`Content`, `Input`) at the package root
 *
 *  Domain helpers named `toMcpRequest` / `toMcpNotification` / `toMcpResponse`
 *  are '''partial''' lifts: they set method / id / meta scaffolding but do not
 *  yet project method-specific fields into `fields` (ADR-0006 codec WIP).
 *  Wire envelopes are projected only via `modules/codec` (no `toJsonRpc` on
 *  [[mcp.McpMessage]]).
 */
package object mcp
