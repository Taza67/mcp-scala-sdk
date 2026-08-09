<a id="readme-top"></a>

[![CI][ci-shield]][ci-url]
[![License][license-shield]][license-url]
[![Scala][scala-shield]][scala-url]

<div align="center">

<h3 align="center">mcp-scala-sdk</h3>

  <p align="center">
    MCP for Scala — Cats, ZIO, or neither. A shared protocol kernel with frontend artifacts for building MCP servers and clients.
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
    <li>
      <a href="#about-the-project">About The Project</a>
      <ul>
        <li><a href="#built-with">Built With</a></li>
      </ul>
    </li>
    <li>
      <a href="#getting-started">Getting Started</a>
      <ul>
        <li><a href="#prerequisites">Prerequisites</a></li>
        <li><a href="#installation">Installation</a></li>
      </ul>
    </li>
    <li><a href="#contributing">Contributing</a></li>
    <li><a href="#license">License</a></li>
  </ol>
</details>

## About The Project

`mcp-scala-sdk` is a Scala SDK for the [Model Context Protocol](https://modelcontextprotocol.io/). One protocol implementation ships with separate frontend artifacts so you can use pragmatic Scala, Cats Effect, or ZIO.

The SDK implements the [2026-07-28](https://modelcontextprotocol.io/specification/2026-07-28) specification and will adopt new MCP releases as they ship.

### Built With

* [Scala](https://www.scala-lang.org/) 2.13
* [sbt](https://www.scala-sbt.org/)
* [Model Context Protocol](https://modelcontextprotocol.io/)
* [Circe](https://circe.github.io/circe/)
* [MUnit](https://scalameta.org/munit/)

<p align="right"><a href="#readme-top" title="Back to top">↑</a></p>

## Getting Started

### Prerequisites

* JDK 17+
* sbt 2.0.x

### Installation

```bash
git clone https://github.com/Taza67/mcp-scala-sdk.git
cd mcp-scala-sdk
sbt test
```

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
