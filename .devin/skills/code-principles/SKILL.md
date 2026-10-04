---
name: code-principles
description: Apply scoped, consistent, boundary-safe coding rules.
---

# Code Principles For This SDK

## When To Use

Load before proposing, writing, or reviewing SDK code. Apply within the touched
module; do not expand a bug fix into an unrelated modernization.

## Procedure

1. Read the existing implementation and at least one sibling of the same role.
2. Establish the executing root cause, inputs, errors, and resource ownership.
3. Choose the smallest upstream change that addresses the actual requirement.
4. Preserve paired naming, parameter order, return shapes, comments, and layout.
5. Add behavior-focused regressions and review the complete final diff.

## Priority

- Correctness, security, and privacy override consistency or appearance.
- Validate input at boundaries; fail explicitly on invalid critical state.
- Keep dependencies inward; inject I/O boundaries rather than constructing them
  inside models or codecs.
- Keep illegal states out of public APIs. Prefer sum types and composition.
- Preserve ownership and fatal/nonfatal distinctions.
- Follow local patterns unless they violate a higher-priority invariant.
- Keep the solution simple; do not add future hooks without a concrete caller.
- Factor shared protocol knowledge, not merely similar-looking independent rules.

Do not leak raw exceptions, tokens, headers, arguments, or decoder input into
wire errors. An intentionally returned application result remains caller-owned.
CLI configuration/secrets may come from the environment; library configuration
can be explicit parameters. Do not hardcode secrets.

## Pitfalls

- A symptom explanation is not evidence that a particular code path executed.
- Adding a second error hierarchy where the protocol already defines one.
- A renamed or reordered public parameter breaking an existing caller.
- Introducing an effect or Java I/O dependency into the portable kernel.
- Swallowing a failure behind an arbitrary success or default value.
- Silently changing a neighboring implementation to make files look symmetric.

## Verification

Account for every modified file. Verify paired APIs, boundary cases, source
compatibility, ownership, diagnostics, and the narrow regression gates.
Report debt or an unverified surface rather than claiming it is fixed.
