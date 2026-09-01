# Contributing

Thank you for your interest in **mcp-scala-sdk**. The SDK targets the [MCP 2026-07-28](https://modelcontextprotocol.io/specification/2026-07-28) revision and implements a subset of server functionality with a codec-neutral protocol kernel (see [ADR-0001](docs/decisions/0001-multi-frontend-mcp-scala-sdk-architecture.md)).

## Before you start

- Search [existing issues](https://github.com/Taza67/mcp-scala-sdk/issues) to avoid duplicate work.
- For large or architectural changes, open an issue first or read the [architecture decision records](docs/decisions/).
- New cross-cutting design choices should be captured as an ADR before or alongside the implementation.

## Development setup

### Prerequisites

- JDK 17 or later (the build compiles with `-release:17`; CI uses 17)
- [sbt](https://www.scala-sbt.org/) 2.0.x (see `project/build.properties`)
- Scala 2.13 (pinned via `ThisBuild / scalaVersion`)
- Python 3.10+ for the independent stdio process checks

### Clone and test

```bash
git clone https://github.com/Taza67/mcp-scala-sdk.git
cd mcp-scala-sdk
sbt test
```

### Narrow test commands

Run only the suite related to your change, for example:

```bash
sbt 'codec/testOnly io.github.taza67.mcp.codec.mcp.ServerRequestsSuite'
sbt 'server/testOnly io.github.taza67.mcp.server.McpServerSuite'
sbt 'transportStdio/testOnly io.github.taza67.mcp.transport.stdio.StdioTransportSuite'
```

### Distribution and process checks

```bash
sbt exampleStdio/stage
python3 -B scripts/test_stdio.py --launcher target/stdio-example/bin/mcp-stdio-example
```

### Compiler flags

All modules compile with `-Werror`, `-release:17`, and `-Wunused` selectors. Keep new code warning-free.

### Formatting

`.scalafmt.conf` pins Scalafmt 3.9.4. There is no sbt formatting plugin and no `scalafmtAll` task; run the pinned formatter through your editor integration instead.

## Pull requests

1. Fork the repository and create a branch from `main`.
2. Make focused changes; keep PRs reviewable.
3. Add or update tests for behavior you change.
4. Ensure the test command above passes locally.
5. Open a pull request with a clear description and link related issues.

## Commit messages

Follow [Conventional Commits](https://www.conventionalcommits.org/).

- **Types:** `feat`, `fix`, `refactor`, `docs`, `chore`
- **Description:** imperative mood, lowercase, no trailing period
- **Body:** optional; blank line after the description, then `-` bullets, lowercase except proper nouns, imperative, no trailing period
- No attribution trailers

## Code layout

| Module | Status | Role |
|--------|--------|------|
| `modules/protocol` | implemented | Codec-neutral MCP and JSON-RPC types |
| `modules/codec` | implemented | Hand-written AST to ADT projections |
| `modules/codec/circe` | implemented | Circe bridge for wire JSON |
| `modules/client` | partial | Synchronous client request core with typed discover/complete; no concrete transports |
| `modules/server` | implemented | Synchronous MCP server orchestration |
| `modules/transport/stdio` | implemented | Bounded stdio transport |
| `examples/stdio` | implemented | Runnable example server and staged distribution |
| `modules/codec/ziojson` | implemented | zio-json bridge for wire JSON |
| `modules/transport/http` | placeholder | HTTP transport, not implemented |

Protocol types stay independent of JSON libraries; wire encoding lives in `codec` (see [ADR-0003](docs/decisions/0003-protocol-json-ast-without-codec-dependency.md) and [ADR-0006](docs/decisions/0006-adt-json-ast-projection-ownership.md)).

## Code of conduct

This project follows the [Contributor Covenant](CODE_OF_CONDUCT.md). By participating, you agree to uphold it.

## Security

Report vulnerabilities as described in [SECURITY.md](SECURITY.md). Do not open public issues for security-sensitive reports.
