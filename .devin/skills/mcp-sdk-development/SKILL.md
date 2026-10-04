---
name: mcp-sdk-development
description: Extend protocol, codec, and transport boundaries safely.
---

# MCP SDK Development

## When To Use

Load for model, codec, client, server, notification, and transport work in
`mcp-scala-sdk`. Read `AGENTS.md` and the handoff state first.

## Protocol Grounding

The target is MCP `2026-07-28`. Use `docs/specs/2026-07-28/schema.ts` and
`schema.json`, the architecture records, and current official documentation.
Do not reuse older initialize/session/GET-SSE examples as the modern binding.

## Procedure

1. Locate the protocol ADT, its wire-key constants, shared AST projection,
   generic envelope facade, and existing literal-AST tests.
2. Decide whether the operation consumes full wire data or a stripped field bag.
   `Result.fields` excludes `resultType` and `_meta`; specialized input-required
   and subscription teardown helpers may require the full result object.
3. Reuse `Messages`, `ServerRequests`, `PlainRequests`, `PlainNotifications`,
   metadata helpers, and existing result encoders before adding a new seam.
4. Preserve absent versus explicit-null behavior and sanitize reserved metadata
   extension keys. Keep unknown discriminants safe and diagnostics static.
5. Add literal schema/AST/bytes fixtures, not only encoder-decoder round trips.
6. Integrate the typed runtime operation only after its codec contract is settled.

## Transport Rules

- Stdio is one newline-delimited shared channel; messages contain no literal
  framing newlines. Response ids and subscription metadata route messages.
- HTTP is one POST endpoint with explicit per-request headers and metadata.
- No protocol sessions, GET streams, DELETE sessions, or Last-Event-ID resume.
- Accepted extension notifications use 202 with no body.
- Unknown RPC methods use 404 with the JSON-RPC method-not-found error.
- Header mismatches and unsupported versions use 400 protocol errors.
- Origin validation precedes body parsing; default local binding is loopback.
- Long-lived core change notifications use `subscriptions/listen`, with ack first,
  a granted subset, and the listen request id on every notification.
- Server-to-client input is embedded in `InputRequired`, not independent requests.

## Pitfalls

Do not promote codec coverage into a turnkey-runtime or full-conformance claim.
Do not silently retry tool calls, discard InputRequired, echo decoder input,
invent a second HeaderMismatch type, or parse ids differently in each transport.

## Verification

Run only changed domain suites and relevant socket/process integration gates.
Check correlated and id-less errors, malformed envelopes, explicit null,
unknown methods/versions, metadata, notification silence/ordering, and recovery.
Consult `docs/handoff/FINAL_GATE.md` only for the eventual complete delivery pass.
