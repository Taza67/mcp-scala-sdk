# MCP Schema Reference (2026-07-28)

Local vendored copy of the [official MCP schema](https://modelcontextprotocol.io/specification/2026-07-28/schema).

| Format | Path |
|--------|------|
| TypeScript | [`docs/specs/2026-07-28/schema.ts`](docs/specs/2026-07-28/schema.ts) |
| JSON Schema | [`docs/specs/2026-07-28/schema.json`](docs/specs/2026-07-28/schema.json) |

Upstream: [modelcontextprotocol/modelcontextprotocol/schema/2026-07-28](https://github.com/modelcontextprotocol/modelcontextprotocol/tree/main/schema/2026-07-28)

## Categories

The TypeScript schema groups types by `@category` comments:

- **JSON-RPC** — wire envelopes, errors, request/response unions
- **Common Types** — shared MCP shapes (`Meta`, `Content`, `Capabilities`, …)
- **Errors** — protocol error codes and payloads
- **Content** — text, image, audio, resource blocks
- **Methods** — request/result/notification types per MCP method (e.g. `tools/list`, `server/discover`)

Browse [`schema.ts`](docs/specs/2026-07-28/schema.ts) on GitHub for syntax-highlighted navigation and jump-to-definition.
