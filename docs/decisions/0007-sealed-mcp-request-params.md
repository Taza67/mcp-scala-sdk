# [ADR-0007] Sealed MCP request params on the envelope

* Status: accepted
* Deciders: taza67
* Date: 2026-08-08

## Context and Problem Statement

`McpRequest.params` was typed as `Option[RequestParams]` only. Domain list requests
(`tools/list`, `prompts/list`, `resources/list`, `resources/templates/list`) carry
`PaginatedRequestParams` with a typed optional `cursor`. Any lift into `McpRequest`
had to rewrap as `RequestParams` and drop `cursor`. Partial `toMcpRequest` helpers
on domain ADTs encoded that loss.

How should the MCP envelope represent plain vs paginated request params on Scala
2.13 without dishonest lifts, including the first page where `cursor` is absent?

## Decision Drivers

* Honest typing: `cursor` must be representable on `McpRequest` when present
* First page: absent `cursor` must still decode as [[PaginatedRequestParams]] for list methods
* Scala 2.13: no native union types — use a sealed trait sum
* Keep ADR-0006: ADT ↔ AST projection stays in `modules/codec`
* Avoid partial protocol helpers that silently drop fields
* One discriminant: JSON-RPC method selects the params subtype (not key presence alone)

## Considered Options

* Keep `Option[RequestParams]` and stuff `cursor` into `fields`
* Domain ADTs encode directly to `JsonObject`, skipping `McpRequest` for lists
* Widen `McpRequest.params` to a sealed sum; decode by presence of the `cursor` key
* Widen `McpRequest.params` to a sealed sum; decode by JSON-RPC method

## Decision Outcome

Chosen option: "Widen `McpRequest.params` to a sealed sum; decode by JSON-RPC method",
because the envelope should mirror wire shapes the domain already models, Scala 2.13
expresses that as `sealed trait McpRequestParams`, and list methods remain
`PaginatedRequestParams` whether or not `cursor` appears on the wire.

Normative details:

* `sealed trait McpRequestParams` with `meta` / `fields`; `RequestParams` and
  `PaginatedRequestParams` extend it
* `McpRequest.params: Option[McpRequestParams]`
* Codec decode: `toMcpRequestParams(method, params)` — if `method` is in
  `lists.PaginatedListMethods.All` (`tools/list`, `prompts/list`, `resources/list`,
  `resources/templates/list`) → `toPaginatedRequestParams`; otherwise →
  `toRequestParams`, and a top-level `cursor` key is a decoding error (reserved)
* Codec encode: `fromMcpRequestParams` pattern-matches the ADT
* `RequestParams.fields` MUST NOT contain `cursor`
* Remove protocol `toMcp*` stubs; method-specific lifts belong in `modules/codec`

### Consequences

* Good, because list requests can sit on `McpRequest` without dropping `cursor`.
* Good, because first-page lists stay `PaginatedRequestParams` after round-trip.
* Good, because reserved `cursor` cannot leak into plain `RequestParams.fields` via decode.
* Bad, because callers matching on `params` must handle both subtypes.
* Bad, because the codec must know the set of paginated methods (sourced from
  protocol `Method` constants — single spelling of each method name).

### Confirmation

* `McpRequest.params` is `Option[McpRequestParams]`.
* Envelope round-trips cover plain params, paginated with cursor, and paginated without cursor.
* Non-list methods with a top-level `cursor` fail decode.
* No `toMcp*` helpers remain on protocol domain ADTs.

## Pros and Cons of the Options

### Keep `Option[RequestParams]` and stuff `cursor` into `fields`

* Good, because the envelope type stays simple.
* Bad, because `cursor` is a reserved sibling of `_meta` on the wire, not an open field.
* Bad, because typed `PaginatedRequestParams` and the envelope disagree.

### Domain ADTs encode directly to `JsonObject`

* Good, because lists need not fit `McpRequest`.
* Bad, because the generic MCP envelope layer cannot carry pagination.
* Bad, because runtime routing that speaks `McpRequest` still loses `cursor`.

### Widen sum; decode by `cursor` key presence

* Good, because no method table is needed.
* Bad, because first-page lists (`cursor` absent) decode as plain `RequestParams`.
* Bad, because type identity is unstable across encode/decode.

### Widen sum; decode by JSON-RPC method

* Good, because domain and envelope agree on pagination for all list pages.
* Good, because it fits Scala 2.13 without union syntax.
* Bad, because the codec maintains the paginated-method set (mitigated by reusing
  protocol `Method` vals).

## More Information

* Related: [ADR-0004](0004-separate-jsonrpc-and-mcp-layers.md), [ADR-0006](0006-adt-json-ast-projection-ownership.md)
* Evidence: `McpRequestParams` in `protocol.mcp.Message`; `Params.toMcpRequestParams(method, …)`
  in `modules/codec`
