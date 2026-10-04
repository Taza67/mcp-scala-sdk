# Final Verification Gate

Run this once the remaining coherent implementation lots are complete.
Do not repeatedly impose it on every unchanged ownership-only patch.

## Before Running

- Review the complete accumulated diff and preserve unrelated changes.
- Confirm the actual Java 17 executable used by sbt and spawned fixtures.
- Inspect actual sbt project/row ids after any matrix conversion.
- Reuse a live in-flight gate; do not launch duplicates.
- Record the commit, worktree state, commands, and runtime versions with the logs.
- Keep historical logs separate from this final pass.

## Required Surfaces

1. The complete Scala test suite for every enabled JVM Scala version.
2. Actual Scala.js and Native linked tests for every claimed portable row.
3. HTTP JSON/SSE client/server integration, subscriptions, header annotations,
   Origin policy, malformed input, cancellation, and lifecycle regressions.
4. The concrete stdio client, concurrent streams, owned-child and cancellation cases.
5. The staged stdio example and independent Python process suite on Java 17.
6. Strict compilation, configured Scala formatting, Python lint/format,
   shell syntax, and git whitespace hygiene.
7. Documentation/example commands that match the resulting module/platform matrix.

Current pre-matrix commands include:

```bash
sbt test
sbt exampleStdio/stage
JAVA_HOME=/path/to/jdk17 python3 -B scripts/test_stdio.py \
  --launcher target/stdio-example/bin/mcp-stdio-example
ruff check scripts
ruff format --check scripts
sh -n target/stdio-example/bin/mcp-stdio-example
git diff --check
```

Run the pinned Scalafmt `3.9.4` through a verified tool path. There is currently
no `scalafmtAll` sbt task. Do not claim it ran merely from seeing `.scalafmt.conf`.
A new formatter/build plugin requires its own justified configuration change.

The current `test` alias uses `root/test`; inspect whether its future aggregate
includes all desired matrix rows. A root gate is not automatically proof of
platform rows that were left out of aggregation.

## Delivery Evidence

Report exact passed/failed counts from gate logs, not an invented total based on
several overlapping targeted runs. Distinguish a skipped gate from a failed one.
State which external-client interoperability was actually checked.

Do not publish/tag/deploy merely because this checklist is green.
For normal pushes, re-read the remote and avoid overwriting another agent's work.
If CI is reported green, obtain its actual GitHub result after the pushed commit.
