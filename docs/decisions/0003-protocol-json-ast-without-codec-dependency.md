# [ADR-0003] Protocol JSON AST without a codec library dependency

* Status: accepted
* Deciders: taza67
* Date: 2026-08-04

## Context and Problem Statement

MCP messages carry open JSON shapes: request/notification parameters, results, `error.data`, and `_meta` bags. The shared `protocol` module (ADR-0001) must model those values so server and future frontends can work with typed messages.

How should `protocol` represent arbitrary JSON so it stays usable across pragmatic, Cats Effect, and ZIO stacks without forcing one JSON library into the kernel?

## Decision Drivers

* Multi-frontend neutrality (ADR-0001): Cats, ZIO, and pragmatic users must not inherit a Circe-only (or zio-json-only) kernel
* Clear module boundaries: protocol types vs encode/decode adapters
* Faithful MCP modeling: open objects/values need a structured in-memory form, not only opaque strings
* Incremental delivery: ship message types now; defer Circe / zio-json codec modules
* Scala 2.13 first: keep the kernel dependency set small and portable

## Considered Options

* Depend on Circe in `protocol` and use `io.circe.Json` for open fields
* Depend on zio-json (or another single codec) in `protocol` for open fields
* Keep open JSON as raw `String` / bytes only until codecs exist
* Define a sealed internal JSON AST in `protocol`; add Circe / zio-json (etc.) as separate codec modules later

## Decision Outcome

Chosen option: "Define a sealed internal JSON AST in `protocol`; add codec libraries as separate modules later", because it keeps the protocol kernel stack-neutral and still gives a precise model for MCP’s open JSON fields. Putting Circe or zio-json in `protocol` would bias the multi-frontend design; raw strings defer structure and push parsing into every consumer.

This decision is reflected in commit `ad7a66e` (`feat: add protocol JSON payload model`).

### Consequences

* Good, because `protocol` has no Circe / zio-json dependency; frontends and codec modules can choose adapters later.
* Good, because open MCP fields (`params`, `result`, `error.data`, `_meta` bags) share one AST (`JsonValue` / `JsonObject`, etc.).
* Good, because nominal wrappers (`RequestParameters`, `NotificationParameters`, `Result`, `ErrorData`, meta types) can sit on top of the AST without locking a wire codec.
* Bad, because encode/decode is not free: dedicated codec modules and conversions must be built.
* Bad, because the AST duplicates concepts that Circe or zio-json already provide, with a small maintenance cost.

### Confirmation

* `modules/protocol` does not declare Circe, zio-json, or similar JSON codec dependencies.
* Open JSON in protocol types is expressed via the internal AST (or wrappers over it), not via a third-party `Json` type.
* When codecs land, they live outside the protocol kernel (separate modules/artifacts) and depend on `protocol`, not the reverse.

## Pros and Cons of the Options

### Depend on Circe in `protocol`

* Good, because mature JSON ADT and ecosystem; fast to encode/decode.
* Good, because many Scala HTTP stacks already use Circe.
* Bad, because Cats-leaning default conflicts with ZIO- and pragmatic-first neutrality (ADR-0001).
* Bad, because every consumer of `protocol` pays the Circe dependency even if they use another codec.

### Depend on zio-json (or another single codec) in `protocol`

* Good, because strong fit for ZIO frontends.
* Bad, because mirrors the Circe bias problem for non-ZIO users.
* Bad, because still couples the kernel to one serialization stack.

### Raw `String` / bytes only

* Good, because zero JSON library and minimal types in the kernel.
* Bad, because handlers and tests cannot inspect structured params/results without ad hoc parsing.
* Bad, because MCP’s object-shaped fields become untyped blobs, weakening the protocol model.

### Internal JSON AST; codecs later (chosen)

* Good, because stack-neutral and aligned with a shared protocol kernel.
* Good, because structured open fields are available before any codec module ships.
* Good, because multiple codecs can target the same AST.
* Bad, because requires designing and maintaining the AST and later adapters.
* Bad, because short-term verbosity versus reusing an existing `Json` ADT.

## More Information

* Related: [ADR-0001](0001-multi-frontend-mcp-scala-sdk-architecture.md) (shared protocol kernel, multi-frontend)
* Related: [ADR-0002](0002-separate-transport-modules.md) (protocol remains above transport)
* Evidence: `modules/protocol/.../Json.scala`; commit `ad7a66e`
* Revisit when the first codec module is added: confirm dependency direction (`codec-*` → `protocol`) and that no codec type leaks into the kernel API surface
