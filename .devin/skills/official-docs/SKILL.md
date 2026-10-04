---
name: official-docs
description: Check exact APIs against authoritative documentation.
---

# Authoritative Documentation

## When To Use

Load when a change depends on MCP specification semantics, a new runtime,
sbt/Scala behavior, effect cancellation APIs, or cross-platform plugin support.

## Procedure

1. Start with the repository's vendored schema and relevant ADR/implementation.
2. Identify the exact library, version, protocol revision, and unanswered question.
3. Use available documentation tools to read a primary source.
4. Record the URL/version and the specific fact it supports.
5. Translate that fact into an exact design and a regression case.

If Context7 is available, resolve a library id before querying unless the id was
explicitly supplied. Use focused, version-aware questions.
If a repository is known, use its official docs/source rather than unrelated
search snippets. If a tool or source is unavailable, say so; never invent an API,
URL, successful fetch, or SDK capability.

Use the cloud environment's available tools. This skill does not require an
unavailable local MCP configuration or a secret from the previous machine.
Do not request or publish credentials merely to reproduce an old tool setup.

## Sources For This Project

- Protocol schema: `docs/specs/2026-07-28/schema.ts` and `schema.json`.
- HTTP: https://modelcontextprotocol.io/specification/2026-07-28/basic/transports/streamable-http
- Stdio: https://modelcontextprotocol.io/specification/2026-07-28/basic/transports/stdio
- Subscriptions: https://modelcontextprotocol.io/specification/2026-07-28/basic/patterns/subscriptions
- sbt matrices: https://www.scala-sbt.org/2.x/docs/en/reference/cross-building-setup.html
- Effect documentation ids: `/typelevel/cats-effect` and `/zio/zio`.

## Pitfalls

Old initialize/session/GET-SSE examples describe a different protocol era.
A search snippet is not evidence for a complex runtime contract.
Cached local dependency paths do not exist automatically in a cloud clone.
An artifact download is not a verified build or runtime gate.

## Verification

Every load-bearing design claim has a repository or primary-source basis.
Separate observed facts from hypotheses, cite actual sources in documentation,
and turn newly assumed semantics into behavior-focused tests.
