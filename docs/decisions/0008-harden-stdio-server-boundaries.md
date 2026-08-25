# [ADR-0008] Strict stdio server boundaries

* Status: accepted
* Deciders: SDK implementation owner
* Date: 2026-10-05

## Context and Problem Statement

The early stdio server trusted its input. Malformed JSON only produced a log
line with no protocol reply, handler and registry failures were not isolated,
and unbounded input or silently replaced invalid encoding could compromise the
process. Decoder messages, exception text, or unknown tool names could leak
into errors, and `PrintStream`/`PrintWriter` sinks swallowed write failures.

How should a synchronous stdio MCP server validate inbound wire requests and
isolate per-request failures without owning caller resources?

## Decision Drivers

* Input distrust: error messages and diagnostics must not reflect raw frames,
  decoder details, exception details, or names the server did not return
  itself; valid correlated ids and protocol-defined data such as the
  requested version are legitimate exceptions
* Correlation: errors carry the request `id` only when it decoded safely;
  uncorrelated frames omit `id` entirely
* Availability: one failing frame or request must not kill the loop
* Caller ownership: the transport never closes injected streams, so no close
  API is needed (the default dispatcher and transport own no resources)
* Bounded work: input buffering and parser depth are limited before content
  is trusted

## Considered Options

* Validate inside `Messages.toRequest` and let the transport stay thin
* A dedicated server-facing projection plus a bounded line-framing transport
* Reuse the client-facing decoders with a pre-filter that only checks `method`

## Decision Outcome

Chosen option: "A dedicated server-facing projection plus a bounded
line-framing transport".

Normative details:

* `codec.mcp.ServerRequests.toRequest` is a backend-neutral policy function
  returning `Either[ErrorResponse, Option[McpRequest]]`: `Right(Some)` is a
  valid request, `Right(None)` an ignored notification or inbound response
  (never answered, avoiding response loops), `Left` a wire `ErrorResponse`
  with static messages and an `id` only when safely decoded
* `RequestId` stays strict (non-blank string or signed 64-bit integer);
  `ErrorResponse.id`/`McpErrorResponse.id` are `Option`, absent `id` encodes
  as omitted, and `null` on decode normalizes to `None`
* `McpServer.handle` wraps registry lookup, dispatch, and result encoding in
  `NonFatal`, degrading to correlated `InternalError`; fatal errors propagate
* `Handler.tools` rejects blank and duplicate names; decode failures and
  unknown tool names map to static `InvalidParams` errors
* `StdioTransport` reads bounded frames without `readLine`: at most
  `maxMessageSize + 1` units are stored, oversized frames are drained to
  LF/EOF and rejected once, a trailing CR is excluded, and a strict UTF-8
  decoder maps `CharacterCodingException` to `ParseError`
* A pre-parse delimiter scan enforces `maxNestingDepth` while skipping string
  contents and escapes; exceeding it yields `InvalidRequestError`
* Flush checks after each response and at EOF surface swallowed
  `PrintStream`/`PrintWriter` failures as `IOException("stdio output
  failure")`; real `IOException` propagates and is never a protocol error
* The example entrypoint catches only `IOException`, prints a static
  diagnostic, and exits 1

### Consequences

* Good, because hostile or malformed input gets deterministic protocol errors
  with no information leakage.
* Good, because resource limits apply before allocation-heavy parsing.
* Good, because streams remain caller-owned and there is no close API to misuse.
* Bad, because valid notifications and inbound responses produce no observable
  feedback, which can hide client bugs.
* Bad, because per-request `NonFatal` isolation adds a boundary layer that new
  transports must replicate consistently.

### Confirmation

* `ServerRequestsSuite` covers envelope, params, metadata, version, and
  correlation cases including notifications and response-shaped input.
* `McpServerSuite` covers `NonFatal` isolation, factory params validation,
  name validation, privacy, and metadata merging.
* `StdioTransportSuite` covers framing limits, strict UTF-8, nesting depth,
  per-request recovery, and swallowed-output detection.
* `scripts/test_stdio.py` replays the same boundaries against the staged
  process end to end.

## Pros and Cons of the Options

### A dedicated server-facing projection plus a bounded line-framing transport

* Good, because server policy (params presence, correlation, version checks,
  notification silence) lives in one backend-neutral place the general codecs
  do not need.
* Good, because byte-level framing enforces size, encoding, and depth limits
  before trust.
* Bad, because the policy is a second decode path that must stay consistent
  with `Messages.toRequest` as the protocol evolves.
* Bad, because the transport owns more logic than a plain `readLine` loop.

### Validate inside `Messages.toRequest`

* Good, because no new projection exists.
* Bad, because client-facing decoders would grow server-only policy:
  notifications are never answered in either role, while the general codecs
  must still represent notifications and responses that the server policy
  may ignore.

### Reuse client decoders with a `method` pre-filter

* Good, because no dedicated projection exists.
* Bad, because correlation, params-presence, and version checks need typed
  context a string filter cannot express, and malformed envelopes would still
  reach the general decoder.

## More Information

* Related: [ADR-0003](0003-protocol-json-ast-without-codec-dependency.md),
  [ADR-0004](0004-separate-jsonrpc-and-mcp-layers.md),
  [ADR-0006](0006-adt-json-ast-projection-ownership.md)
* Evidence: `ServerRequests.scala`, `StdioTransport.scala`, `McpServer.scala`,
  `Handler.scala` and their suites; `scripts/test_stdio.py`
