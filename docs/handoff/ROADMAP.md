# Remaining Expanded SDK Scope

The user explicitly asked to implement the previously deferred features.
This is the remaining scope, not a claim that these modules already exist.
Finish the first stdio repair lot before starting unrelated architecture changes.

## 1. Stdio Server Streaming And Cancellation

The committed stdio server is still synchronous. It cannot keep a
`subscriptions/listen` request open while reading subsequent requests/cancellations.

Reuse `StreamingServer`, `ServerStream`, `SubscriptionHub`, `StdioFrames`, and
shared message-validation rules. Keep the existing borrowed-stream `run` API
compatible. If background readers require ownership to unblock reliably, expose
an explicitly owned lifecycle API rather than silently closing borrowed streams.

Required cases: interleaved ordinary requests and subscription notifications,
per-connection request-id uniqueness, ack-before-event, cancellation referencing
the active request id, no output after cancellation, bounded queues/workers,
EOF shutdown, writer failure releasing readers, and primary fatal preservation.
Do not hold the stdout lock during handler execution or a blocking stream pull.
Keep stdout exclusively JSON-RPC; diagnostics stay on stderr.

## 2. HTTP Tool Parameter Headers

Standard protocol/method/name headers are implemented. Schema-derived
`Mcp-Param-*` headers from `x-mcp-header` annotations are not.

Read the official revision's schema-extension and validation sections first.
Use the latest obtained tool input schema, or explicit preloaded definitions.
Reject invalid annotated tool definitions without discarding other valid tools.
Server-side validation must compare decoded header values with body arguments.

Verified specification constraints to preserve:

- Annotation names are nonempty HTTP field-name tokens and case-insensitively
  unique within a tool schema.
- Only string, integer, and boolean parameters may be annotated; not `number`.
- Integers must fit JavaScript's safe integer range.
- An annotated property is reachable through `properties` chains only, including
  nested object properties, never through arrays, composition, conditionals, or refs.
- Missing/null arguments omit the header; present values require matching headers.
- Conversion is exact string, decimal integer, or lowercase boolean, followed by
  the existing safe UTF-8/Base64-sentinel encoding.
- Invalid or missing recognized headers produce HTTP 400 and code `-32020`.

Do not implement a general JSON Schema validator just to extract these headers.
Settle the compiled binding/lookup API and cache invalidation rules before coding.
Add literal-schema and real-wire tests for nested paths, case collisions, nulls,
Unicode, wrong primitives, safe-integer edges, and stale schemas.

## 3. Cats Effect And ZIO Frontends

Create separate modules under `modules/frontend/cats` and `modules/frontend/zio`
as contemplated by the architecture records. Reuse the protocol/client/server
semantics rather than duplicating wire validation and result decoding.

Cover client operations and server/request-stream entry points with practical
effect APIs, typed protocol/client errors, and explicit resource lifetimes.
Keep the synchronous core usable without either runtime.

Use interruptible/cancellable boundaries, not a plain blocking wrapper that
claims cancellation works while its underlying task stays blocked.
Cats Effect and ZIO interruption must remain interruption, not a manufactured
success or a generic typed failure that masks control flow.

Regression cases: lazy id allocation, preserved RemoteError/InputRequired
outcomes, effect execution order, actual blocked-pull cancellation, source close
exactly once, borrowed resources left open, and failed/fatal cleanup behavior.
Do not use unsafe runtime execution inside a shared model or codec.

## 4. Scala And Platform Cross-Builds

Add Scala 3 while retaining Scala 2.13. Inspect the actual Scala-3 dependency
artifacts and warning flags rather than assuming Scala-2 options are accepted.
The local candidate compiler `3.3.7` was fetched, not tested against this SDK.

Add Scala.js and Scala Native only to genuinely portable layers. The first
portable candidates are `protocol` and shared `codec`; do not cross-build Java
HTTP/process/thread frontends by pretending unavailable APIs exist.

sbt 2 includes project matrices. Preserve logical module names, source ownership,
configuration mappings, test-fixture resources, and staged distribution references.
Do not add the old sbt-1 projectmatrix plugin by habit.

Verified plugin pins fetched locally:

```scala
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.22.0")
addSbtPlugin("org.scala-native" % "sbt-scala-native" % "0.5.12")
```

The corresponding sbt-2 artifacts were `sbt-scalajs_sbt2_3` and
`sbt-scala-native_sbt2_3`. Fetching them is not a successful cross-build.
Inspect Node and Clang in the cloud. Run actual linked JS/Native tests, not only
Scala source compilation. A backend or frontend belongs on a platform only
after its dependencies and runtime behavior pass there.

Keep Scala compiler and scala-library versions compatible when introducing
Cats Effect `3.7.0`, whose Scala-2 artifact required library `2.13.18`.
Do not silently disable `-Werror` or delete strict tests to get a green matrix.

## 5. Documentation, CI, And Final Delivery

Update README usage, current feature/platform matrix, ownership rules,
CONTRIBUTING, CHANGELOG, and an ADR for the expanded delivery.
Preserve historical decisions as history; explain their superseding scope.

Provide working client/server examples and executable commands that match the
actual sbt project ids after any matrix migration.
CI must cover the newly claimed platforms and real process/socket behavior,
with no package publication or deployment hidden in a workflow change.

Use `FINAL_GATE.md`. Report implementation, tested behavior, external
interoperability, and unverified limitations separately.

## Secondary Client Follow-Ups

Generic request APIs and typed discover/complete/tools operations exist.
Typed resources/prompts operations and optional MRTR retry orchestration can be
added when a concrete caller needs them, without hiding `InputRequired` or
silently retrying side-effecting calls. Do not let speculative convenience APIs
delay the expressly requested transports, effects, and cross-builds.

## Official References

- MCP HTTP: https://modelcontextprotocol.io/specification/2026-07-28/basic/transports/streamable-http
- MCP stdio: https://modelcontextprotocol.io/specification/2026-07-28/basic/transports/stdio
- MCP subscriptions: https://modelcontextprotocol.io/specification/2026-07-28/basic/patterns/subscriptions
- Vendored protocol schema: `docs/specs/2026-07-28/schema.ts` and `schema.json`.
- sbt 2 matrices: https://www.scala-sbt.org/2.x/docs/en/reference/cross-building-setup.html
- Scala.js sbt-2 release: https://www.scala-js.org/news/2026/06/20/announcing-scalajs-1.22.0/
- Scala Native pin: https://scala-native.org/en/latest/changelog/0.5.x/0.5.12.html
- Cats Effect documentation id: `/typelevel/cats-effect`.
- ZIO documentation id: `/zio/zio`.

These sources were consulted during local work. Recheck exact APIs when changing
versions; do not replace this protocol era with older initialize/session examples.
