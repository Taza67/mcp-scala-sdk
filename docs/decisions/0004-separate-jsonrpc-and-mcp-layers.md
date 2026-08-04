# [ADR-0004] Separate JSON-RPC and MCP layers in protocol

* Status: accepted
* Deciders: taza67
* Date: 2026-08-04

## Context and Problem Statement

MCP rides on JSON-RPC 2.0: wire messages are JSON-RPC envelopes (`jsonrpc`, `method`, `params`, `id`, result/error), while MCP adds semantics such as `_meta`, protocol version, capabilities, and method families (`tools/*`, etc.). Early protocol types in this SDK mixed both concerns in one flat package: request/response shapes carried MCP params/meta as if they were part of JSON-RPC.

How should we structure types inside the shared `protocol` module so JSON-RPC and MCP stay distinct, while still sharing the JSON AST (ADR-0003)?

## Decision Drivers

* Spec fidelity: `_meta` and MCP protocol version are not JSON-RPC concepts
* Clear mental model: envelope vs MCP message semantics
* Maintainability: dual-era MCP (`2025-11-25` handshake vs `2026-07-28` per-request meta) should not pollute JSON-RPC types
* Incremental delivery: one sbt `protocol` module for now; avoid premature multi-artifact split
* Consistency with ADR-0001 / ADR-0003: shared kernel, codec-neutral JSON AST

## Considered Options

* Keep a single flat `protocol` package mixing JSON-RPC envelopes and MCP fields
* Split packages inside `protocol`: `json`, `jsonrpc`, and `mcp`, with MCP types converting to JSON-RPC envelopes
* Split into separate sbt modules (`protocol-jsonrpc`, `protocol-mcp`) immediately

## Decision Outcome

Chosen option: "Split packages inside `protocol`: `json`, `jsonrpc`, and `mcp`, with MCP types converting to JSON-RPC envelopes", because it separates layers without changing the monorepo artifact graph yet. Flat mixing hid the boundary; separate sbt modules can wait until independent versioning or reuse justifies them.

This decision is reflected in commit `6854927` (`refactor: separate JSON-RPC and MCP protocol layers`).

### Consequences

* Good, because JSON-RPC `Request` / `Notification` / `Response` stay free of MCP `_meta` and use `JsonStructure` for `params` (object or array only).
* Good, because MCP exposes `McpRequest` / `McpNotification` / `McpResponse` (and related params/result/meta) with `toJsonRpc` for a later codec path.
* Good, because the shared JSON AST lives under `protocol.json` and serves both layers (ADR-0003).
* Bad, because callers must import the right package and map between MCP and JSON-RPC when encoding.
* Bad, because `toJsonRpc` does not yet merge lifted `_meta` back into the JSON object (deferred to codec work).

### Confirmation

* `io.github.taza67.mcp.protocol.jsonrpc` types do not reference MCP meta, protocol version, or capabilities.
* MCP message types live under `io.github.taza67.mcp.protocol.mcp` and depend on `jsonrpc` / `json`, not the reverse.
* Server runtime handles `McpRequest` / `McpResponse`, not bare JSON-RPC envelopes.
* JSON-RPC `params` is `Option[JsonStructure]` (`JsonObject` | `JsonArray`), not arbitrary `JsonValue`.

## Pros and Cons of the Options

### Flat mixed `protocol` package

* Good, because fewer packages and shorter imports.
* Bad, because JSON-RPC and MCP concepts blur (e.g. MCP meta on “JSON-RPC” requests).
* Bad, because dual-era MCP changes would keep touching envelope types.

### Packages `json` / `jsonrpc` / `mcp` in one module (chosen)

* Good, because layering is visible in packages and dependency direction.
* Good, because one `protocol` artifact stays simple for early delivery.
* Good, because MCP can evolve (`ClosedMeta`, `McpProtocolVersion`, …) without rewriting JSON-RPC.
* Bad, because more files and renaming for existing call sites.
* Bad, because conversion (`toJsonRpc`) must stay honest as codecs land.

### Separate sbt modules now

* Good, because strongest physical boundary and optional independent publishing later.
* Bad, because extra build/publish complexity before the API has stabilized.
* Bad, because overkill while server and codecs still live in the same repo cut.

## More Information

* Related: [ADR-0001](0001-multi-frontend-mcp-scala-sdk-architecture.md) (shared protocol kernel)
* Related: [ADR-0003](0003-protocol-json-ast-without-codec-dependency.md) (JSON AST placement)
* Evidence: `modules/protocol/.../json|jsonrpc|mcp/`; commit `6854927`
* Revisit when adding codec modules or if JSON-RPC types are reused outside MCP (then consider a dedicated `protocol-jsonrpc` artifact)
