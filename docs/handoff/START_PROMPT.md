# Devin Cloud Start Prompt

Paste the following prompt into the cloud session. Attach the handoff archive
if the session cannot see the unfinished stdio files. The prompt deliberately
requires explicit reads, so it does not depend on undocumented native skill
discovery behavior.

```text
Continue the existing mcp-scala-sdk work, rather than starting a new SDK.
Repository: Taza67/mcp-scala-sdk. The user communicates in French.

The local implementation was deliberately stopped for this cloud handoff.
Your task is to finish the requested expanded SDK scope, with rigorous,
behavior-focused verification and regular focused commits.

First verify what code you actually received:
1. Read AGENTS.md.
2. Read docs/handoff/README.md, STATE.md, STDIO_REPAIR.md, ROADMAP.md,
   and FINAL_GATE.md in full.
3. Read the complete six repository SKILL.md files listed in AGENTS.md,
   even when a global skill has the same name. Invoke the relevant skills
   if your environment supports it; otherwise apply the repository text
   as manually loaded instructions. State the mechanism actually available.
4. Inspect git status and the handoff manifest. A normal clone may contain
   the committed SDK but NOT the unfinished stdio client or its build change.
   Restore the attached working-tree.patch only after git apply --check and
   only into a compatible worktree. Never overwrite unrelated changes.

The first engineering lot is the STDIO_REPAIR.md checklist. The current
uncommitted stdio client had 79 tests pass across three suites, but subsequent
full source review found real lifecycle, cancellation, and fatal-error bugs.
Those fixes were NOT implemented. Do not commit that client merely because
the old suite was green. Add regressions for the reviewed defects and repair
them as one coherent lot.

Then continue ROADMAP.md: stdio server streaming/cancellation, remaining HTTP
parameter-header behavior, Cats Effect and ZIO frontends, Scala 3, portable
Scala.js/Native rows, and accurate documentation/CI. Settle exact interfaces
and ownership before delegating implementation. Keep the protocol kernel free
of effect runtimes and JVM I/O.

Work in coherent lots, not dozens of tiny delegation/review cycles:
- one design and a complete regression-case list;
- implementation with the smallest relevant test gates;
- complete diff review, one consolidated rework pass if needed;
- focused Conventional Commit;
- short French progress update with evidence and remaining scope.

Reuse the frozen evidence supplied in STATE.md. Do not re-run unchanged
derivations or full suites on every handoff. Run one complete final matrix,
staging, and independent process gate when the remaining features are ready.
If a gate blocks, diagnose the concrete dependency/process failure rather
than looping or inventing a successful result.

Network access for official docs/tools/dependencies and normal pushes has been
authorized. Verify the remote before pushing. No force-push, published-history
rewrite, package publication, deployment, tag, or PR action without explicit
approval. Never commit secrets or discard existing work.

Do not claim that this is nearly finished: finish the remaining lots and
clearly report verified capabilities and any genuine limitations.
```
