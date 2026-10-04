# Agent Instructions

## Project

This is `mcp-scala-sdk`, a multi-module SDK targeting MCP `2026-07-28`.
The user wants a reliable SDK, not only a demonstration server. Communicate
with the user in French, with readable paragraphs and concise progress updates.
Avoid em dashes, marketing language, invented percentages, and speculative ETAs.

Before continuing a transferred session, read `docs/handoff/STATE.md`,
`docs/handoff/STDIO_REPAIR.md`, and `docs/handoff/ROADMAP.md`.
The public README and CONTRIBUTING module-status tables lag the latest code.
Do not mistake their old "placeholder" labels for the current implementation.

## Skills

Load the applicable repository skills before proposing or changing code:

- `.devin/skills/code-principles/SKILL.md`
- `.devin/skills/commit-style/SKILL.md`
- `.devin/skills/mcp-sdk-development/SKILL.md`
- `.devin/skills/mcp-sdk-runtime-safety/SKILL.md`
- `.devin/skills/mcp-sdk-cross-build/SKILL.md`
- `.devin/skills/official-docs/SKILL.md`

Read the full repository files explicitly, even if a global skill has the same
name. If the environment supports invocation, invoke the relevant skills too.
Otherwise apply their contents as manually loaded instructions.
Do not claim native discovery or invocation was verified merely because the
files exist. These are portable, project-specific instructions, not references
to the previous developer's home directory or personal memory.

## Authority

- Network access for official documentation and dependencies is authorized.
- Make regular, focused commits after reviewing and testing each coherent lot.
- The user has authorized pushing local commits. Use normal pushes only after
  checking the remote and the actual branch being pushed.
- Do not force-push, rewrite published history, tag, publish packages, deploy,
  change GitHub settings, or open/merge a PR without explicit authorization.
- Report verification from actual test logs and observed CI results.
- Preserve pre-existing changes, including the unfinished stdio client.
- Never use destructive resets, overwrite another agent's work, or commit secrets.
- Obtain explicit approval before elevated permissions or destructive operations.

## Architecture And Scope

- Keep `protocol` independent of JSON backends, I/O, Cats Effect, and ZIO.
- Keep shared AST projections in `codec`; wire libraries belong in backend modules.
- Keep server/client protocol logic separate from byte and socket transports.
- Effects are separate frontends over shared protocol rules, not duplicated SDKs.
- JVM-specific processes, Java HTTP, threads, and streams must remain JVM-only.
- Implement the requested remaining features, but settle consequential interfaces,
  ownership rules, and tests before dispatching implementation.
- Prefer existing seams and minimal upstream fixes over downstream workarounds.
- Do not add speculative hooks, placeholder products, or cosmetic refactors.

## Working Method

1. Inspect the current worktree, relevant implementation, sibling APIs, and tests.
2. Identify the executing root cause, not just a plausible description of the symptom.
3. State the smallest coherent implementation lot and its regression cases.
4. Implement and verify that lot, then review the complete diff.
5. Batch all review findings into one rework pass where possible.
6. Commit the verified result and report capability, evidence, and remaining work.

If subagents are available, use them for settled implementation and verification
while retaining design, investigation, full diff review, and user-facing actions.
Prompts, instructions, measurements, and evaluation configuration remain
lead-authored. Do not wait on a superseded handoff after a new user message.

Use narrow gates for changed behavior. Do not repeatedly rerun every old suite
after each small edit. Reserve one complete matrix/distribution pass for the
final delivery gate. Reuse an already-running command or server rather than
starting it again; verify its real JVM, not a remembered PID or launch argument.

## Code And Failure Contracts

- Follow sibling naming, parameter order, return shapes, imports, and organization.
- Use Scala 2.13-compatible syntax in shared sources; keep strict warnings enabled.
- Use ASCII for new code and comments unless an existing file requires otherwise.
- Validate untrusted input at boundaries and return static, safe diagnostics.
- Do not expose raw decoder messages, exception text, headers, tokens, or requests.
- Intentionally returned protocol errors/results remain application-owned content.
- Convert `NonFatal` failures where appropriate; preserve genuine fatal errors and
  external interruption, including their identity during secondary cleanup.
- Specify resource ownership. Borrowed streams stay open; explicitly owned ones
  close once. Cancellation must unblock active pulls and prevent late delivery.
- Do not hold a state lock or iterator monitor during blocking I/O or user code.
- Bound bytes, nesting, queues, pending requests, waits, and owned-child shutdown.
- A green suite is evidence for its cases, not proof that reviewed defects vanished.

## Final Delivery

Update README, CONTRIBUTING, CHANGELOG, and relevant ADRs to the actual feature
matrix. Record exact commands, platforms, JVM versions, and test results.
Do not claim comprehensive MCP conformity or external-client interoperability
without corresponding evidence. Use `docs/handoff/FINAL_GATE.md` as the final
verification checklist and report any unverified surface explicitly.
