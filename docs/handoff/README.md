# Cloud Handoff

This pack transfers the engineering state, working rules, and unfinished code
without relying on the previous machine's home directory or tool memory.

## Read Order

1. `AGENTS.md`: repository-wide authority and workflow.
2. `STATE.md`: frozen implementation and verification checkpoint.
3. `STDIO_REPAIR.md`: the first unfinished lot and its reviewed defects.
4. `ROADMAP.md`: the remaining expanded SDK scope.
5. `FINAL_GATE.md`: the eventual complete verification gate.
6. The six `.devin/skills/*/SKILL.md` files.

`START_PROMPT.md` is the main cloud-session prompt. `STDIO_REPAIR.md` can also
be given to a narrowly scoped implementation task after the lead has read it.
The state and decisions are authoritative inputs; do not rediscover completed
work merely to produce another inventory.

## Getting The Right Code

There are two distinct states to transfer:

- The reviewed SDK commits on the repository branch.
- The uncommitted `build.sbt` change and three unfinished stdio-client files.

A push does not transfer the second group. Do not assume a GitHub clone has
the client draft just because it has the latest committed HTTP transport.

The accompanying archive includes `repository.bundle`, `working-tree.patch`,
`manifest.json`, checksums, and selected verification logs. It contains no
dependency cache, staged binaries, personal memory, or home-directory config.
The git bundle is a recovery source; because the SDK commits are now on the
remote, a normal clone followed by the working-tree patch is usually enough.

Before importing anything:

```bash
git status --short
git rev-parse HEAD
git apply --check /path/to/handoff/working-tree.patch
```

If the check succeeds and the worktree has no conflicting changes:

```bash
git apply /path/to/handoff/working-tree.patch
```

If the cloud clone is missing the committed work, inspect the bundle first:

```bash
git bundle verify /path/to/handoff/repository.bundle
git fetch /path/to/handoff/repository.bundle main:refs/remotes/handoff/main
```

On a clean compatible worktree, checkout `main` and apply the patch. If the
clone is stale relative to the pushed checkpoint, fast-forward or fetch first.
If the cloud worktree is dirty, inspect and preserve it; do not reset it.
Do not use force-push to resolve an import disagreement.

## Skills And Cloud Limitations

The skills are physically inside this repository and use relative references.
No symlink points into `/home`, and no global editor installation is required.
They are project-specific instructions, not a copied personal-memory database.

Native skill discovery in the user's cloud session has not been verified from
this local session. The start prompt therefore explicitly requires invocation
when supported, or a full manual read otherwise. Do not report "skills loaded"
without saying which of those mechanisms was actually used.

The logs are historical evidence, not CI results for arbitrary later changes.
Their source checkpoints and limitations are recorded in `STATE.md`.
