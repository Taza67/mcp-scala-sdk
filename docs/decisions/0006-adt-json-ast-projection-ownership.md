# [ADR-0006] Ownership of ADT ↔ JSON AST projections

* Status: accepted
* Deciders: taza67
* Date: 2026-08-07

## Context and Problem Statement

ADR-0003 places a codec-neutral JSON AST (`JsonValue` / `JsonObject`, …) in `protocol`, while Circe / zio-json (and similar) live in separate codec modules. That creates **three** layers, not two:

1. Rich domain ADTs (`NotificationMeta`, `Request`, `Error`, …)
2. Protocol JSON AST
3. Wire text (`String`) via a backend library

Work on JSON-RPC codecs and MCP `_meta` made the ownership question concrete: reserved-key assembly/split (e.g. `NotificationMeta` ↔ `JsonObject`) and envelope mapping (e.g. `Message` ↔ `JsonObject`) feel like “protocol logic,” yet putting them beside Circe would duplicate them per backend. `Error.classify` already lives in `protocol`, while JSON-RPC `Messages` lives under `modules/codec`, so practice is already mixed.

Where should **ADT ↔ `JsonObject` / `JsonValue` projections** live so module boundaries stay clear, multi-backend codecs stay thin, and we do not pretend trivial wrappers are real projections?

## Decision Drivers

* Preserve ADR-0003: no Circe / zio-json (or equivalent) dependency in `protocol`
* Multi-backend codecs (Circe first, zio-json later) must not each reimplement MCP/JSON-RPC key assembly
* SDK users who speak the wire always take a codec path; `protocol`-only ADT↔AST is not a required product surface
* Keep `protocol` focused on types and small pure domain rules, not a large serialization surface
* Make an explicit rule so Error / Meta / Message placement stops being rediscussed ad hoc
* Avoid misleading APIs (e.g. trivial `MetaObject.toJsonObject` with no validation responsibility)

## Considered Options

* Policy A: ADT ↔ AST projections live in `protocol` (companions); backend modules only map AST ↔ `String`
* Policy B: ADT ↔ AST projections live in backend-neutral `modules/codec`; backend modules only map AST ↔ `String`
* Policy C: Unstated hybrid — pure classify in `protocol`, all map key walking in whichever module is convenient

## Decision Outcome

Chosen option: "Policy B: ADT ↔ AST projections live in backend-neutral `modules/codec`; backend modules only map AST ↔ `String`", because codec consumers already need a non-`protocol` artifact to reach the wire, Circe/zio-json should stay thin printers/parsers over the shared AST, and concentrating key-assembly in `modules/codec` matches the existing JSON-RPC `Messages` layout while keeping `protocol` free of a growing `fromJsonObject` surface and free of `DecodingError` coupling.

**Placement rule (normative):**

* If the code **walks object keys** or **assembles/splits** a `JsonObject` / `JsonValue` from/to a rich ADT → `modules/codec` (e.g. `codec.jsonrpc`, `codec.mcp`).
* If the code **chooses a domain subtype from values already extracted** (no key walking) → may live in `protocol` (e.g. `Error.classify`).
* Circe / zio-json modules → **only** `JsonValue` ↔ `String` (and SDK `Encoder`/`Decoder` façades that compose the above).
* Do not add trivial “projection” methods on newtypes that only wrap `JsonObject(map)` without real invariants; call sites use `JsonObject(meta.value)` or equivalent.

This decision **clarifies** ADR-0003’s “codecs live outside protocol”: “codec” here includes the shared AST-projection module, not only vendor JSON libraries.

### Consequences

* Good, because Circe and zio-json do not duplicate `NotificationMeta` / JSON-RPC envelope rules.
* Good, because `protocol` stays the typed MCP/JSON-RPC model plus AST for open fields, without owning full serialize graphs.
* Good, because a one-line rule (key walking → `codec`; pure classify → `protocol`) ends ad hoc debates.
* Bad, because “protocol semantics” appear under `modules/codec` by name — readers must treat that module as **AST projection / schema bridge**, not as Circe.
* Bad, because `protocol`-only users cannot project ADTs to `JsonObject` without depending on `codec` (accepted: not a target use case for wire).
* Bad, because existing or future pure helpers in `protocol` (like `Error.classify`) must stay clearly non-parsing so the rule remains honest.

### Confirmation

* No Circe / zio-json imports in `modules/protocol` or in `modules/codec` AST-projection packages.
* JSON-RPC / MCP ADT ↔ `JsonObject` mappings live under `modules/codec` (packages such as `codec.jsonrpc`, `codec.mcp`).
* `modules/codec/circe` (and future zio-json) compose projection + `JsonValue` ↔ `String` only.
* New ADT↔AST work is reviewed against the key-walking vs classify rule above.
* Trivial newtype unwrap helpers are not presented as codec projections.

## Pros and Cons of the Options

### Policy A: ADT ↔ AST projections live in `protocol`

* Good, because `protocol` alone can round-trip ADTs through the shared AST without a codec artifact.
* Good, because it mirrors “the schema is the protocol module” mental model.
* Bad, because `protocol` accumulates a large serialization surface and needs a protocol-local error type (must not depend on `codec.DecodingError`).
* Bad, because it fights the current JSON-RPC `Messages` placement and grows review surface for every MCP meta/params type in the kernel.
* Bad, because ADR-0003’s “codecs outside protocol” becomes blurrier when most encode logic is still in `protocol`.

### Policy B: ADT ↔ AST projections live in backend-neutral `modules/codec`

* Good, because one projection implementation serves every wire backend.
* Good, because vendor modules stay small and swappable.
* Good, because it matches shipped JSON-RPC codec layout and the usual SDK dependency story (`protocol` + `codec` + `codec-circe`).
* Bad, because domain key rules live outside the `protocol` directory name (documentation discipline required).
* Bad, because a `protocol`-only consumer cannot project without `codec` (acceptable if unused).

### Policy C: Unstated hybrid

* Good, because it allows short-term pragmatism (`Error.classify` here, `Messages` there).
* Bad, because every new type reopens the ownership debate (as with `_meta`).
* Bad, because inconsistent placement confuses contributors and invites Circe leakage “just this once.”
* Bad, because trivial helpers and real projections get mixed without criteria.

## More Information

* Related: [ADR-0003](0003-protocol-json-ast-without-codec-dependency.md) (AST in `protocol`; vendor codecs outside)
* Related: [ADR-0004](0004-separate-jsonrpc-and-mcp-layers.md) (JSON-RPC vs MCP; dishonest `toJsonRpc` removed in favor of codec projection)
* Related: [ADR-0005](0005-protocol-modeling-from-mcp-2026-07-28-specs.md) (layer build order)
* Evidence in tree: `Error.classify` in `protocol.jsonrpc`; `codec.jsonrpc.Messages` for envelopes; Circe `JsonCodec` / `JsonRpcCodec` as String façades
* Revisit if a supported product use case requires ADT↔AST with a `protocol`-only dependency, or if `modules/codec` should be renamed/published as a dedicated `mcp-schema` / `mcp-ast` artifact for clarity

### Deferred codec domains (current cut)

Protocol types for **notifications**, **subscriptions**, and **completion** live under `protocol.mcp` but have no `codec.mcp` twin packages yet. That gap is intentional (YAGNI): the server runtime and first codec pass do not need wire projection for those domains. `codec.mcp.Messages` documents the omission; add aligned `codec.mcp` packages when transport or server work requires them — do not leave silent gaps.
