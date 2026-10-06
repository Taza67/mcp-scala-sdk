# Frozen Engineering State

Snapshot date: 2026-10-06. Local SDK implementation is stopped.
Only handoff preparation is authorized in the local session now.

## Repository Checkpoint

- Repository: `Taza67/mcp-scala-sdk`.
- Branch at the engineering stop: `main`.
- Last reviewed SDK commit: `05431df2586dfe7e84b13a6eb7a20bbf673ec2f1`.
- Remote `main` after handoff push: `8a763c934d24b8fd4e60042fa0f0172128a10062`.
- The 41 local SDK commits were pushed to `origin/main`; a normal clone retrieves them.
- Verify the remote with a fresh read before assuming the checkpoint is current.

The archive manifest is the source of truth for the exported commit and patch.
Additional handoff documentation can make the delivered HEAD newer than the
last SDK implementation checkpoint. Verify the supplied code and gate logs,
not merely whether a clone has a recent HEAD.

## Delivered And Committed

- Strict JSON-RPC error correlation, including id-less parse/envelope errors.
- Server-bound MCP metadata/version validation and `NonFatal` handler isolation.
- Declarative server tools, discovery, tools list/call, optional completion.
- Bounded stdio server framing, strict input UTF-8, write-failure detection.
- Relocatable stdio example distribution and independent Python process checks.
- Circe and zio-json wire backends, including strict zio-json syntax/trailing input.
- Completion request/reference/result AST codecs and typed server handler.
- Synchronous client core with discovery, completion, tools list/call, and
  `InputRequired` outcomes surfaced without automatic retry.
- Progress, logging, cancellation, list-change, and resource-update codecs.
- Subscription filters, listen/acknowledgement/teardown codecs.
- Client pull streams with id correlation, first-ack and opt-in filter validation.
- Shared wire nesting/size defaults and strict HTTP UTF-8/header-value helpers.
- HTTP POST boundary, Origin allowlist, required headers and protocol errors.
- JDK HTTP server and client transports with JSON and request-scoped SSE.
- First stream error mapped to HTTP status before SSE commits; unknown methods
  remain HTTP 404 rather than a misleading HTTP 200 event stream.
- Bounded `SubscriptionHub`, separate streams for reused request ids, graceful
  teardown, abrupt cancellation, and idle HTTP keep-alive/cancellation handling.
- Cancellable server streams and explicit tool-list-change discovery capability.
- Shared `StdioFrames` extraction, preserving the old borrowed-stream server API.

These are implemented capabilities, not a claim of exhaustive MCP conformance
or interoperability with an independently maintained official client.

## Uncommitted Draft

Four paths are deliberately not part of the reviewed SDK commit:

```text
build.sbt
modules/transport/stdio/src/main/scala/io/github/taza67/mcp/transport/stdio/StdioClientTransport.scala
modules/transport/stdio/src/test/scala/io/github/taza67/mcp/transport/stdio/StdioClientTransportSuite.scala
modules/transport/stdio/src/test/scala/io/github/taza67/mcp/transport/stdio/StdioPeerMain.scala
```

`build.sbt` adds the client dependency and a test-process classpath resource.
The client draft has owned-stream/process factories, bounded reader/writer pumps,
pending request routing, cancellation frames, and process fixtures.
The draft's last three-suite gate passed, but full review identified real defects.
The seven-point rework in `STDIO_REPAIR.md` was interrupted before any code change.

Do not discard this draft, commit it as production-ready, or recreate it from
scratch. Import the working-tree patch, add the missing regressions, and repair it.

## Existing Architecture And Protocol Decisions

- Namespace: `io.github.taza67.mcp`.
- MCP revision: `2026-07-28`, not the older initialize/session-based era.
- JSON AST/model ownership: `protocol`; shared ADT projections: `codec`.
- Backend-specific wire JSON: `codec/circe` and `codec/ziojson`.
- Server and client logic depend inward; transport code is separate.
- Every server-bound request currently requires `params._meta` with the flat keys
  `io.modelcontextprotocol/protocolVersion` and
  `io.modelcontextprotocol/clientCapabilities`.
- Default frame size: 8 MiB; default nesting depth: 128.
- Request ids: signed 64-bit integral numbers or non-blank strings.
- Error-response ids are optional; an uncorrelated error encodes without `id`.
- `McpClient.callTool` returns `RequestOutcome`, including `InputRequired`.
- `McpClient.stream`/`listen` return caller-owned `ClientStream` values.
- `StdioTransport.run` borrows streams. The draft stdio client's
  `ownedStreams` factory explicitly transfers ownership.
- Shared sources stay Scala-2.13-compatible; no effect runtime enters `protocol`.

The modern HTTP binding has one POST endpoint, no protocol sessions, no GET
notification stream, no DELETE session teardown, and no Last-Event-ID resume.
Notifications accepted by an explicit extension callback use HTTP 202 with no
body. Core HTTP cancellation is response-stream closure, not a cancelled POST.
Long-lived core change notifications belong on `subscriptions/listen`.
Origin rejection precedes body parsing; default Origin allowlist rejects present
origins while allowing requests without an Origin header.

## Frozen Verification Evidence

The archive copies selected logs into `evidence/`. The original local names are
listed here so both local and cloud readers can identify the same evidence.

| Evidence File | Verified Surface | Result |
| --- | --- | --- |
| `mcp-http-client-final-fatal-gate.log` | HTTP client, JSON/SSE, ownership/fatal regressions | 32 passed |
| `mcp-http-sse-server-final.log` | HTTP server streaming and subscription integration | 27 passed |
| `mcp-client-stream-fatal-identity.log` | Client stream policy and fatal identity | 26 passed |
| `mcp-server-stream-rework.log` | Server streams and subscription hub | 14 + 13 passed |
| `mcp-domain-notifications-green.log` | Notification codec domain suites | 12 + 4 + 4 + 5 passed |
| `mcp-domain-subscriptions-green.log` | Subscription codecs | 12 passed |
| `mcp-stdio-client-green.log` | Draft stdio client + framing + old server | 36 + 12 + 31 passed |

The stdio client log is the pre-review-defect checkpoint, not proof of the
unimplemented repairs. Committed framing/server extraction was accepted from
that gate; the client draft was not.

Earlier full verification on Temurin 17 passed 330 Scala tests and 15 Python
checks before the later HTTP/streaming increments. That is historical evidence,
NOT a complete final gate for the current expanded SDK. The cloud task must run
the eventual full verification after finishing the remaining scope.

Logs showing a deliberate `LinkageError` on a JDK HTTP worker are tests of fatal
relay. Read the suite summary and regression assertion; do not blindly silence
or convert those fatal paths to satisfy a log-noise preference.

## Environment At Stop

- Build: sbt `2.0.3`, Scala `2.13.16`, `-release:17`, MUnit `1.3.3`.
- Backends: Circe `0.14.14`, zio-json `0.7.44`.
- Formatting configuration: Scalafmt `3.9.4`.
- Verified local SDK runtime: Temurin `17.0.20.1`.
- Default system Java was 27; one thin-client restart accidentally used it.
  The cloud agent must verify the actual JVM for each fresh build session.
- No Java, sbt, test-child, or stdio-pump process remained running at the stop.
  Old PID 6249 is dead; never reuse that number as current evidence.
- Local Node was `22.23.3`, Clang/Clang++ `23.1.1`; cloud tools must be inspected,
  not assumed identical.
- Dependencies/tools fetched into local user caches included Cats Effect `3.7.0`,
  ZIO `2.1.19`, Scala 3 compiler `3.3.7`, Scalafmt `3.9.4`, Scala.js sbt plugin
  `1.22.0`, and Scala Native sbt plugin `0.5.12`.
- Those caches are not transferred. Authorized network downloads may be needed.

Cats Effect `3.7.0`'s local Scala-2.13 POM required scala-library `2.13.18`.
Do not add it blindly to a compiler pinned at `2.13.16`; align compiler/library
versions deliberately and validate the chosen dependency graph.

## Known Build Trap

The first stdio process-fixture resource generator used `Test/fullClasspath`.
That includes this project's own resources and created a dependency cycle:
`resources -> fullClasspath -> resources`. sbt parked with no test JVM spawned.

The unfinished build change correctly uses `Test/dependencyClasspath` plus
`Test/classDirectory`, and that acyclic generator produced the 79-pass gate.
Preserve it when adding project matrices. A fullClasspath resource-generator
cycle is not evidence that the test or client transport itself hung.

## Documentation Debt

README and CONTRIBUTING still describe HTTP as absent and omit most of these
later capabilities. CHANGELOG also trails the delivered increments.
Update them after the implementation lots land, not by claiming planned features
already exist. Preserve historical ADRs; add a new decision record when changing
their original deferred platform/effect scope.
