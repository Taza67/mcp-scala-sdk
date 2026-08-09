# [ADR-0005] Protocol modeling from MCP 2026-07-28 SPECS

* Status: accepted
* Deciders: taza67
* Date: 2026-08-04

## Context and Problem Statement

We are implementing protocol types from the MCP schema revision **2026-07-28** (vendored locally; see [`SPECS.md`](../../SPECS.md)). That schema lists a large “Common Types” section alongside JSON-RPC envelopes and many method-specific shapes. Earlier ADRs already require a codec-neutral JSON AST (ADR-0003) and a split between JSON-RPC and MCP packages (ADR-0004). Open questions remained: which protocol era to target first, how to name types, where SPECS “Common” types live, and whether JSON-RPC `params` should allow arrays.

How should we implement `SPECS.md` in the `protocol` module without fighting ADR-0003/0004 or dumping the entire schema at once?

## Decision Drivers

* Spec fidelity to MCP **2026-07-28** (stateless / per-request `_meta`, `server/discover`, `resultType`)
* Preserve layering: JSON AST ≠ JSON-RPC envelope ≠ MCP semantics (ADR-0003, ADR-0004)
* Incremental delivery: implement **layer by layer**, not the full schema in one pass
* Scala-idiomatic API names for SDK users (not TypeScript export spelling)
* Avoid a catch-all `common` package that re-mixes JSON-RPC and MCP
* MCP’s JSON-RPC request/notification `params` in SPECS are object-shaped (`{ [key: string]: any }`)

## Considered Options

* Implement the full SPECS surface in one shot under a SPECS-mirrored `common/` package and TypeScript-like names (`JSONRPCRequest`, …)
* Target 2026-07-28 only; implement layer by layer; Scala-idiomatic names; place SPECS “Common” types into `json` / `jsonrpc` / `mcp` by real layer; JSON-RPC `params` as `Option[JsonObject]` only
* Keep dual-era (`2025-11-25` + `2026-07-28`) and `JsonStructure` (object | array) in the first cut

## Decision Outcome

Chosen option: "Target 2026-07-28 only; implement layer by layer; Scala-idiomatic names; place SPECS Common types by layer; `params` as `JsonObject` only", because it matches the product target revision, keeps ADR-0004 layering honest, and avoids a premature dual-era and array-params model that SPECS does not use for MCP envelopes.

This **amends** ADR-0004 on one point: JSON-RPC `params` are modeled as `Option[JsonObject]`, not `Option[JsonStructure]`. Dual-era support remains desirable later but is out of scope for this cut.

### Consequences

* Good, because work proceeds in ordered layers (`json` → `jsonrpc` → `mcp` foundation → domains).
* Good, because naming stays Scala-idiomatic (`JsonRpcRequest`, `RequestMeta`, …) while SPECS remains the semantic source.
* Good, because SPECS “Common Types” are filed under the layer they belong to (AST → `json`; envelopes/ids/errors → `jsonrpc`; meta/result/capabilities/content helpers → `mcp`).
* Good, because object-only `params` match SPECS MCP JSON-RPC shapes and simplify the model.
* Bad, because legacy `2025-11-25` (`initialize`, …) is deferred.
* Bad, because readers must map SPECS TypeScript names to Scala names mentally.
* Bad, because a temporary `common/` WIP (if present) must be cleaned up / redistributed.

### Confirmation

* No requirement to ship dual-era types in the first protocol cut.
* Package layout remains `protocol.json`, `protocol.jsonrpc`, `protocol.mcp` (no long-lived mixed `common` dump).
* Public protocol type names follow Scala idioms, not SPECS export identifiers.
* JSON-RPC request/notification `params` use `Option[JsonObject]` (no `JsonStructure`).
* Implementation order starts with the JSON AST layer, then JSON-RPC envelopes, then MCP foundation.

## Pros and Cons of the Options

### Full SPECS dump with `common/` and TS-like names

* Good, because closest 1:1 mirror of the schema document.
* Bad, because fights ADR-0004 layering and creates a mixed “common” bag.
* Bad, because TypeScript-style names are awkward in Scala APIs.
* Bad, because one-shot scope is too large to keep correct.

### Layer-by-layer 2026-07-28, idiomatic names, layered placement (chosen)

* Good, because incremental and reviewable.
* Good, because preserves JSON / JSON-RPC / MCP boundaries.
* Good, because matches MCP envelope `params` as objects in SPECS.
* Bad, because dual-era clients/servers need a later phase.
* Bad, because renaming vs SPECS requires discipline in docs/comments.

### Dual-era + `JsonStructure` in the first cut

* Good, because closer to full JSON-RPC and older MCP revisions early.
* Bad, because expands scope before the modern kernel is solid.
* Bad, because array `params` are unused by SPECS MCP envelopes and add API surface.

## More Information

* Source: [`SPECS.md`](../../SPECS.md) and [`docs/specs/2026-07-28/schema.ts`](../specs/2026-07-28/schema.ts) (MCP schema reference for **2026-07-28**)
* Related: [ADR-0003](0003-protocol-json-ast-without-codec-dependency.md), [ADR-0004](0004-separate-jsonrpc-and-mcp-layers.md)
* Amends: ADR-0004 (`params` typing: `JsonObject` instead of `JsonStructure`)
* Suggested build order: (1) `json` AST (2) `jsonrpc` envelopes (3) `mcp` metas + `Result`/`resultType` (4) domain methods (`server/discover`, tools, …)
