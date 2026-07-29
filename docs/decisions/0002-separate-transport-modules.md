# [ADR-0002] Separate transport modules from server

* Status: accepted
* Deciders: taza67
* Date: 2026-07-29

## Context and Problem Statement

[ADR-0001](0001-multi-frontend-mcp-scala-sdk-architecture.md) placed stdio and Streamable HTTP under `modules/server/{stdio,http}/`. During implementation, that layout blurred two roles: **server logic** (routing MCP messages, handlers) and **transport** (moving bytes on stdin, HTTP, etc.).

How should we lay out transport modules so the server module stays transport-agnostic, transports stay reusable, and the monorepo remains readable?

## Decision Drivers

* Clear separation: protocol types vs server runtime vs I/O plumbing
* Reuse: transports may serve server and client later without living under `server/`
* Maintainability: dependency direction must stay explicit (`transport` → `server` → `protocol`)
* Consistency with ADR-0001: nested directories, flat sbt/Maven artifacts, `io.github.taza67.mcp` namespace

## Considered Options

* Keep stdio and HTTP under `modules/server/` (ADR-0001 layout)
* Separate `modules/transport/{stdio,http}/` sibling to `modules/server/`
* Flat modules at repository root (`modules/stdio/`, `modules/http/`) with no `transport` folder

## Decision Outcome

Chosen option: "Separate `modules/transport/{stdio,http}/` sibling to a flat `modules/server/` module", because it separates I/O plumbing from server logic without changing the dependency graph. Keeping transports under `server/` groups unrelated concerns; flat root modules scatter the tree without a transport grouping.

This decision **amends the module layout** described in ADR-0001. Drivers, frontends, and coordinates from ADR-0001 remain unchanged.

### Consequences

* Good, because the server module is clearly transport-agnostic (messages in/out, no stdin or HTTP).
* Good, because transport modules can be named and documented as shared plumbing (`mcp-transport-stdio`, etc.).
* Good, because the dependency rule stays simple: `transport-*` → `server` → `protocol`.
* Bad, because a small layout migration is needed (`build.sbt`, empty directories, packages).
* Bad, because ADR-0001’s directory tree in *More Information* is outdated until readers consult this ADR.

### Confirmation

* `modules/server/` is the server runtime module (no `stdio/` or `http/` children under `server/`).
* `modules/transport/stdio/` and `modules/transport/http/` exist as sbt subprojects when implemented.
* `serverStdio` / `serverHttp` in `build.sbt` are renamed or replaced by transport-scoped projects pointing at the new paths.
* Packages under `io.github.taza67.mcp.transport` (e.g. `.stdio`, `.http`) for transport code; `io.github.taza67.mcp.server` for server logic.

## Pros and Cons of the Options

### Keep transports under `modules/server/`

* Good, because all server runtime code lives under one subtree.
* Good, because no migration from the first ADR layout.
* Bad, because transport and server logic are mixed in naming and mental model.
* Bad, because client transports later would not mirror cleanly under `server/`.

### Separate `modules/transport/{stdio,http}/` (chosen)

* Good, because transport vs server responsibilities are obvious in the tree.
* Good, because the same transport concept can apply to server and client later.
* Good, because artifacts can be named `mcp-transport-stdio` without a `server` prefix.
* Bad, because one more top-level folder under `modules/`.
* Bad, because requires updating scaffold paths and ADR-0001 references.

### Flat `modules/stdio/` and `modules/http/`

* Good, because shallow paths.
* Bad, because `stdio` and `http` lose a shared transport grouping as more transports appear.
* Bad, because less clear in a large monorepo than a `transport/` parent folder.

## More Information

* Amends: [ADR-0001](0001-multi-frontend-mcp-scala-sdk-architecture.md) (module layout only)
* Target layout:

  ```
  modules/protocol/
  modules/server/
  modules/transport/stdio/
  modules/transport/http/
  modules/frontend/{std,cats,zio}/   (later)
  examples/
  ```

* First implementation cut remains: `protocol`, `server`, `transport-stdio` on JVM / Scala 2.13; `transport-http` reserved.
