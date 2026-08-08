package io.github.taza67.mcp.protocol

/** MCP protocol types for revision 2026-07-28.
 *
 *  Layout under [[mcp]]:
 *   - foundation types at the package root (`Meta`, `Capabilities`, `Message`, …)
 *   - method domains in subpackages (`tools`, `resources`, `prompts`, …)
 *   - shared helpers (`Content`, `Input`) at the package root
 *
 *  Envelope and params projection (`JsonObject` / JSON text) lives in
 *  `modules/codec` (ADR-0004, ADR-0006, ADR-0007). Method-specific domain lifts
 *  are added there as codecs land — not as partial helpers on protocol ADTs.
 */
package object mcp
