---
name: commit-style
description: Create focused Conventional Commits without attribution.
---

# Commit Style

## When To Use

Load before drafting or creating a repository commit.

## Format

Summary: `type: <imperative verb> <subject>`, no trailing period.
Allowed types: `feat`, `fix`, `refactor`, `docs`, `chore`.
Use `add` for a new capability. Lowercase except proper nouns.

Optional body: a blank line, then `-` bullets, each an imperative phrase,
lowercase except proper nouns, without trailing periods.
Do not add `Co-authored-by` or other attribution trailers.

```text
feat: add bounded subscription streams

- acknowledge requested filters before publishing events
- release owned producers on cancellation
```

## Procedure

Review the full diff and narrow gate evidence before committing. Stage explicit
paths, keep unrelated changes untouched, and use a coherent feature/fix boundary.
Do not commit a known-broken draft simply to make the worktree appear clean.

## Pitfalls

Do not amend, reset, or rewrite existing commits without authorization.
Normal push authorization is not force-push or package-publication authorization.
Before pushing, read the remote and preserve concurrent work.

## Verification

Inspect the resulting commit and worktree. Confirm its paths and message,
no attribution trailer, no secret, and no unrelated or unreviewed content.
