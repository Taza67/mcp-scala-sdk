# Changelog

All notable changes to this project are documented here.

## 0.1.0-alpha.1 (unreleased)

Local alpha build of the MCP server SDK. Not published to Maven.

### Added

- Strict server-boundary request validation via `codec.mcp.ServerRequests`: envelope, version, params presence, metadata, and protocol-version checks with deterministic JSON-RPC errors and correct correlation.
- Declarative tool registration through `ServerTool` and `ToolCall`, with blank and duplicate name rejection.
- Per-request failure isolation in `McpServer` and `StdioTransport`: `NonFatal` handler, registry, and encoding failures degrade to a generic `InternalError` without leaking exception details.
- Result metadata merge: tool-supplied `ResultMeta` extensions and explicit `serverInfo` are preserved, and a missing `serverInfo` is filled with the server identity.
- Bounded stdio framing: per-line frames capped at 8 MiB (UTF-8 bytes on `InputStream`, UTF-16 units on `Reader`) and 128 levels of JSON nesting, both configurable; strict UTF-8 decoding; over-limit frames are drained and rejected once while the loop continues.
- Reliable I/O: swallowed `PrintStream`/`PrintWriter` failures surface as `IOException("stdio output failure")` on flush; caller-owned streams are never closed.
- Runnable stdio example distribution via `sbt exampleStdio/stage`: relocatable `target/stdio-example` with a manifest classpath bootstrap jar, a POSIX launcher, and a bundled `LICENSE`.
- Independent black-box process suite at `scripts/test_stdio.py` exercising the staged launcher end to end.
- CI job covering `sbt test`, staging, and the process suite.
- Alternative zio-json wire backend in `modules/codec/ziojson` with strict trailing-input checks, selectable instead of the Circe codec.
- Completion-domain AST codecs in `codec.mcp.completion` covering references, arguments, context, request params, and result payloads, plus typed handler registration via `Handler.complete`; the default `McpServer` factory registers `completion/complete` and advertises the completions capability only when the optional `completion` callback parameter is supplied.
- Synchronous client core in `modules/client`: a `ClientTransport` exchange port, a typed `ClientError` algebra, monotonic `RequestIds` allocation, and `McpClient` with correlated request dispatch plus typed `discover`, `complete`, `listTools`, and `callTool` operations. `callTool` returns `RequestOutcome`, surfacing input-required results to the caller without retrying. Concrete client transports are not included yet.

### Changed

- `jsonrpc.ErrorResponse.id` and `mcp.McpErrorResponse.id` are now `Option[RequestId]` so uncorrelated wire errors can omit `id`. `Response` and `McpResponse` no longer expose a base `id` accessor. `apply(error, id)` convenience overloads are retained, but `copy` and pattern matches on error responses now use `Option`.
- All server-bound wire requests now require `params` with valid `_meta` as a global server-bound policy, not only for default-factory methods.
- The default factory's `tools/list` requires `PaginatedRequestParams` with no cursor and serves only the first page.
- Request params decode errors and unknown tool names now map to generic `InvalidParams` errors instead of reflecting decoder messages or tool names.
- Error-response ids accept `null` or absence on decode and normalize to `None`; the strict numeric/string request-id policy is unchanged.
