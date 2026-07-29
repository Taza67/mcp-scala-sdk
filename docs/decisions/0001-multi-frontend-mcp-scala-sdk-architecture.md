# [ADR-0001] Multi-frontend MCP Scala SDK architecture

* Status: accepted
* Deciders: taza67
* Date: 2026-07-29

## Context and Problem Statement

We are creating an open-source Model Context Protocol (MCP) SDK for Scala. Existing Scala MCP libraries mostly target Scala 3 and a single effect stack (Cats Effect *or* ZIO, sometimes Ox). That leaves out a large part of the Scala audience, especially teams still on Scala 2.13, and forces adopters into one FP style.

How should we structure the SDK (Scala versions, effect/style frontends, modules, and coordinates) so it reaches more than one camp, stays maintainable, and can grow without shipping the full matrix at once?

## Decision Drivers

* Reach: support Scala 2.13 users now and Scala 3 users over time
* Audience breadth: pure FP (Cats Effect and ZIO) plus pragmatic FP (sync / `Either` / `Future`)
* Maintainability: one protocol implementation; avoid duplicate products or repositories
* Differentiation: fill gaps left by Scala-3-only, single-stack MCP libraries
* Incremental delivery: server-first, JVM-first; defer client, Scala 3 faces, and JS/Native
* Stable naming: GitHub-based Maven/package namespace without a personal `.fr` domain
* Clear module boundaries: nested directories for humans, flat sbt/Maven artifacts for tooling

## Considered Options

* Single-stack Scala 3 SDK (Cats Effect *or* ZIO only), one artifact family
* One repository with a shared protocol kernel and multiple frontend artifacts (Cats Effect, ZIO, pragmatic); Scala 2.13 first, Scala 3 faces later; nested module layout
* Two separately branded products or repositories sharing a protocol
* Scala 3-only kernel, with Scala 2.13 / Spark-world users expected to use `for3Use2_13` or out-of-process integration only
* Effect-polymorphic `F[_]` core only (no dedicated ZIO or pragmatic modules)

## Decision Outcome

Chosen option: "One repository with a shared protocol kernel and multiple frontend artifacts; Scala 2.13 first; nested layout; GitHub coordinates", because it maximizes reach across FP camps and Scala 2.13 users while keeping a single protocol implementation and a maintainable monorepo. Separate branded products over-split the community; Scala 3-only or single-stack options abandon the reach goal; a lone `F[_]` API does not comfortably serve ZIO and pragmatic users.

### Consequences

* Good, because one protocol serves Cats Effect, ZIO, and pragmatic frontends without duplicate repos.
* Good, because a Scala 2.13 kernel matches the large installed base; Scala 3 faces can follow later.
* Good, because nested modules with flat artifacts keep the layout readable and conventional (`io.github.taza67.mcp`, `mcp-*`).
* Good, because server-first JVM delivery and deferred Central publishing keep early scope bounded.
* Bad, because more modules and docs than a single-stack SDK, with a risk of API drift across frontends.
* Bad, because client, Scala 3, and JS/Native still need later phases.

### Confirmation

* Frontends depend on `server-core` → `protocol` and not on each other.
* Initial design has no Ox modules.
* Package/group namespace is `io.github.taza67.mcp`.

## Pros and Cons of the Options

### Single-stack Scala 3 SDK (one effect system)

* Good, because matches most existing Scala MCP libraries and modern Scala DX.
* Good, because smallest maintenance surface.
* Bad, because excludes Scala 2.13 users and the other FP camp.
* Bad, because weak differentiation in an already crowded Scala 3 niche.

### One repository, shared kernel, multiple frontends

* Good, because one protocol implementation with CE, ZIO, and pragmatic faces.
* Good, because Scala 2.13 kernel serves the large installed base; Scala 3 can be added later.
* Good, because nested directories stay readable while Maven artifacts stay flat (`mcp-*`).
* Good, because server-first and deferred Central publishing keep early scope bounded.
* Bad, because more modules and docs than a single-stack SDK.
* Bad, because behavioral parity across frontends must be disciplined or APIs drift.
* Bad, because full matrix (client, Scala 3, JS/Native) still needs phased delivery.

### Two separately branded products or repositories

* Good, because dependency graphs and messaging stay sharply separated.
* Good, because release cadence can differ per product.
* Bad, because splits stars, issues, and contributors.
* Bad, because duplicate bugs and “which do I use?” support cost for a greenfield project.

### Scala 3-only kernel

* Good, because best language DX and alignment with Cats/ZIO ecosystem defaults.
* Bad, because fights the reach goal for Scala 2.13 users.
* Bad, because a Scala 3-only kernel is awkward for publishing to 2.13 consumers compared with a 2.13 kernel plus later Scala 3 faces.

### Effect-polymorphic `F[_]` core only

* Good, because elegant for Typelevel users and one API surface.
* Bad, because ZIO users often want a first-class ZIO module, not an interop afterthought.
* Bad, because pragmatic (non-effect-runtime) users still need a different face.

## More Information

* Coordinates and layout target: `io.github.taza67.mcp` with `modules/protocol`, `modules/server/{core,stdio,http}`, `modules/frontend/{std,cats,zio}`, and `examples/`.
* First publishable cut when publishing: server, Scala 2.13, JVM; Maven Central deferred until needed.
* Spark is a reach signal only, not a product dependency.
* Ox / direct style is out of scope for now.
