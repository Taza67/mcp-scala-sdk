<a id="readme-top"></a>

[![CI][ci-shield]][ci-url]
[![License][license-shield]][license-url]
[![Scala][scala-shield]][scala-url]

<div align="center">

<h3 align="center">mcp-scala-sdk</h3>

  <p align="center">
    An MCP server SDK for Scala. Protocol model, codecs, and a strict stdio server, version 0.1.0-alpha.1.
    <br />
    <br />
    <a href="https://github.com/Taza67/mcp-scala-sdk/issues/new?labels=bug&template=bug_report.yml">Report Bug</a>
    &middot;
    <a href="https://github.com/Taza67/mcp-scala-sdk/issues/new?labels=enhancement&template=feature_request.yml">Request Feature</a>
  </p>
</div>

<details>
  <summary>Table of Contents</summary>
  <ol>
    <li><a href="#about-the-project">About The Project</a></li>
    <li>
      <a href="#getting-started">Getting Started</a>
      <ul>
        <li><a href="#prerequisites">Prerequisites</a></li>
        <li><a href="#build-and-verify">Build and verify</a></li>
        <li><a href="#run-the-example-server">Run the example server</a></li>
      </ul>
    </li>
    <li><a href="#usage">Usage</a></li>
    <li><a href="#writing-tools">Writing tools</a></li>
    <li><a href="#technical-limitations">Technical limitations</a></li>
    <li><a href="#built-with">Built With</a></li>
    <li><a href="#contributing">Contributing</a></li>
    <li><a href="#license">License</a></li>
  </ol>
</details>

## About The Project

`mcp-scala-sdk` is a Scala SDK for building [Model Context Protocol](https://modelcontextprotocol.io/) servers. It targets the [2026-07-28](https://modelcontextprotocol.io/specification/2026-07-28) revision and implements a subset of server functionality: a codec-neutral protocol model, ADT projections over a minimal JSON AST, selectable Circe or zio-json wire codecs, a synchronous server, and a strict stdio transport.

Version `0.1.0-alpha.1` is an unreleased local build, not published to Maven. The default `McpServer` factory always registers `server/discover`, and registers `tools/list` and `tools/call` when tools are provided; the shipped example registers a `ping` tool. The lower-level `Handler` API supports custom method registrations, and the protocol and codec modules model domains broader than the turnkey server covers.

Client support, HTTP transport, Cats and ZIO frontends, Scala 3, Scala.js, and Scala Native are not implemented. Completion AST codecs exist in `codec.mcp.completion`, but completion runtime callbacks are still pending; the notification/subscription domains remain deferred at codec and runtime level.

<p align="right"><a href="#readme-top" title="Back to top">↑</a></p>

## Getting Started

### Prerequisites

* JDK 17 or later
* sbt 2.0.x
* Python 3.10+ for the independent process checks (optional)

### Build and verify

```bash
git clone https://github.com/Taza67/mcp-scala-sdk.git
cd mcp-scala-sdk
sbt test
```

Stage the example server as a relocatable `java -jar` distribution:

```bash
sbt exampleStdio/stage
```

This writes `target/stdio-example/` with a bootstrap jar, a `lib/` directory, a launcher under `bin/`, and a copy of the repository `LICENSE`. Run the independent process checks against it:

```bash
python3 -B scripts/test_stdio.py --launcher target/stdio-example/bin/mcp-stdio-example
```

### Run the example server

```bash
./target/stdio-example/bin/mcp-stdio-example
```

The process waits for newline-delimited JSON-RPC on stdin and exits at EOF. Feed it one request per line, for example:

```bash
./target/stdio-example/bin/mcp-stdio-example <<'EOF'
{"jsonrpc":"2.0","id":1,"method":"server/discover","params":{"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28","io.modelcontextprotocol/clientCapabilities":{}}}}
{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28","io.modelcontextprotocol/clientCapabilities":{}}}}
{"jsonrpc":"2.0","id":"ping-1","method":"tools/call","params":{"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28","io.modelcontextprotocol/clientCapabilities":{}},"name":"ping","arguments":{}}}
EOF
```

Expect three compact success lines, including `"text":"pong"`, then a normal exit when stdin closes. Request metadata uses flat slash keys under `params._meta`, not a nested `io.modelcontextprotocol` object.

<p align="right"><a href="#readme-top" title="Back to top">↑</a></p>

## Usage

### Compatible clients

Clients must attach the request metadata shown above to every call: `params._meta` carries the flat slash keys `io.modelcontextprotocol/protocolVersion` (value `2026-07-28`) and `io.modelcontextprotocol/clientCapabilities` on each request. Clients built around an older `initialize` handshake are not compatible with this server.

For clients that do fit this shape, point them at the launcher as the server command, for example with an absolute path:

```json
{
  "command": "/absolute/path/to/mcp-scala-sdk/target/stdio-example/bin/mcp-stdio-example"
}
```

A plain JVM invocation works too:

```bash
java -jar target/stdio-example/mcp-stdio-example.jar
```

Do not use `sbt run` for protocol clients: build logs can pollute stdout, which is reserved for JSON-RPC messages.

## Writing tools

The example registers a `ping` tool through `McpServer` and `ServerTool`; see the complete source in [examples/stdio](examples/stdio/src/main/scala/io/github/taza67/mcp/examples/stdio/Main.scala), the registration shape in [ServerTool](modules/server/src/main/scala/io/github/taza67/mcp/server/ServerTool.scala), and the factory defaults in [McpServer](modules/server/src/main/scala/io/github/taza67/mcp/server/McpServer.scala).

A few rules the current server enforces:

* Every wire request must carry `params._meta` with the flat `io.modelcontextprotocol/*` keys shown above; missing or malformed metadata is a `-32602` error.
* The SDK does not automatically JSON-schema validate arbitrary tool arguments. Validate inside your handler like `ping` does for its own `additionalProperties: false` schema.
* Expected business failures should return a `CallToolResult` with safe content and `isError = Some(true)`. Unexpected `NonFatal` failures from handlers, the registry, or result encoding degrade to a generic `InternalError` without leaking exception details. Handlers must keep their deliberately returned error and result content safe; the SDK does not redact intentional responses.
* Callers own all handler resources and the transport streams. EOF ends the read loop; there is no close API because the default dispatcher and transport own no resources.

<p align="right"><a href="#readme-top" title="Back to top">↑</a></p>

## Technical limitations

* Request frames are limited to 8 MiB (UTF-8 bytes on stream input, UTF-16 units on `Reader` input) and 128 levels of JSON nesting; both limits are configurable on `StdioTransport`. Frames over either limit are rejected with a protocol error while recognized notifications and inbound responses are ignored silently.
* `tools/list` serves a single page only.
* Request ids must be signed 64-bit integers or non-blank strings; all uncorrelated errors omit `id`, including malformed envelopes and over-limit frames, not only unparseable JSON.
* No older `initialize` handshake, cancellation, or subscriptions support; only the 2026-07-28 revision is served.
* Tool execution has no timeout and result size is not bounded.
* I/O failures propagate to the caller in the library; the example process catches them at its entrypoint, prints a static diagnostic, and exits 1.
* The Scala codebase is synchronous. No compatibility claim is made with specific external MCP clients.

<p align="right"><a href="#readme-top" title="Back to top">↑</a></p>

## Built With

* [Scala](https://www.scala-lang.org/) 2.13
* [sbt](https://www.scala-sbt.org/) 2.x
* [Circe](https://circe.github.io/circe/) or [zio-json](https://zio.dev/zio-json/) (selectable wire codec)
* [MUnit](https://scalameta.org/munit/)

<p align="right"><a href="#readme-top" title="Back to top">↑</a></p>

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Please read [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) before participating.

<p align="right"><a href="#readme-top" title="Back to top">↑</a></p>

## License

Distributed under the Apache License 2.0. See [LICENSE](LICENSE) for more information.

<p align="right"><a href="#readme-top" title="Back to top">↑</a></p>

<!-- MARKDOWN LINKS & IMAGES -->
[ci-shield]: https://github.com/Taza67/mcp-scala-sdk/actions/workflows/ci.yml/badge.svg
[ci-url]: https://github.com/Taza67/mcp-scala-sdk/actions/workflows/ci.yml
[license-shield]: https://img.shields.io/badge/License-Apache%202.0-blue.svg
[license-url]: https://github.com/Taza67/mcp-scala-sdk/blob/main/LICENSE
[scala-shield]: https://img.shields.io/badge/Scala-2.13-red.svg?logo=scala&logoColor=white
[scala-url]: https://www.scala-lang.org/
