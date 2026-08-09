# Contributing

Thank you for your interest in **mcp-scala-sdk**. This project targets the [MCP 2026-07-28](https://modelcontextprotocol.io/specification/2026-07-28) specification with a shared protocol kernel and multiple frontend styles (see [ADR-0001](docs/decisions/0001-multi-frontend-mcp-scala-sdk-architecture.md)).

## Before you start

- Search [existing issues](https://github.com/taza67/mcp-scala-sdk/issues) to avoid duplicate work.
- For large or architectural changes, open an issue first or read the [architecture decision records](docs/decisions/).
- New cross-cutting design choices should be captured as an ADR before or alongside the implementation.

## Development setup

### Prerequisites

- JDK 17 or later (JDK 8+ may work; CI uses 17)
- [sbt](https://www.scala.org/download/) 2.0.x (see `project/build.properties`)

### Clone and test

```bash
git clone https://github.com/taza67/mcp-scala-sdk.git
cd mcp-scala-sdk
sbt ';codec/test;server/test;codecCirce/test'
```

### Formatting

Sources are formatted with [Scalafmt](https://scalameta.org/scalafmt/). Run it from your editor or:

```bash
sbt scalafmtAll
```

## Pull requests

1. Fork the repository and create a branch from `main`.
2. Make focused changes; keep PRs reviewable.
3. Add or update tests for behavior you change.
4. Ensure the test command above passes locally.
5. Open a pull request with a clear description and link related issues.

## Commit messages

Use [Conventional Commits](https://www.conventionalcommits.org/):

```text
type: <imperative verb> <subject>

- bullet describing a concrete change
- another bullet if needed
```

- **Types:** `feat`, `fix`, `refactor`, `docs`, `test`, `chore`
- **Summary:** lowercase, no trailing period, imperative mood (`add`, `fix`, `update`)
- **Body:** optional bullet list; lowercase except proper nouns

Example:

```text
feat: add list tools result AST codec

- add fromListToolsResult and toListToolsResult in codec.tools
- add round-trip tests in ToolsSuite
```

Do not add `Co-authored-by` trailers for automated tools.

## Code layout

| Module | Role |
|--------|------|
| `modules/protocol` | Codec-neutral MCP and JSON-RPC types |
| `modules/codec` | Hand-written AST ↔ ADT projections |
| `modules/codec/circe` | Circe bridge for wire JSON |
| `modules/server` | In-memory MCP server orchestration |
| `modules/transport/*` | Transport adapters (placeholders) |

Protocol types stay independent of JSON libraries; wire encoding lives in `codec` (see [ADR-0003](docs/decisions/0003-protocol-json-ast-without-codec-dependency.md) and [ADR-0006](docs/decisions/0006-adt-json-ast-projection-ownership.md)).

## Code of conduct

This project follows the [Contributor Covenant](CODE_OF_CONDUCT.md). By participating, you agree to uphold it.

## Security

Report vulnerabilities as described in [SECURITY.md](SECURITY.md). Do not open public issues for security-sensitive reports.
