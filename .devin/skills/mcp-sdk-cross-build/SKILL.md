---
name: mcp-sdk-cross-build
description: Cross-build portable layers without JVM API leakage.
---

# SDK Cross-Build

## When To Use

Load when adding Scala versions, platform rows, effect dependencies, build
plugins, classpath generators, staged distributions, or platform CI.

## Procedure

1. Inspect `build.sbt`, `project/build.properties`, plugin declarations, the
   module dependency graph, and source imports before choosing rows.
2. Verify sbt/Scala/plugin compatibility and published dependency variants.
3. Keep Scala-2.13-compatible shared syntax and warning-free Scala-3 compilation.
4. Add non-JVM rows only where the source and dependencies truly work.
5. Preserve test configuration mappings and fixture/staging paths.
6. Run linked platform tests, then document only the rows that actually pass.

## Build Rules

The current build uses sbt 2.0.3. Project matrices are included in sbt 2;
do not add the old sbt-1 matrix plugin by habit.
The first portable candidates are `protocol` and shared `codec`.
Java HTTP/process/thread transports remain JVM-specific.

Treat fetched plugins as candidates, not proof of a working cross-build.
Local candidates were Scala.js 1.22.0, Scala Native 0.5.12, and Scala 3.3.7.
Inspect the actual cloud Node/Clang/Java and dependency variants.

Align Scala compiler and scala-library versions deliberately. Cats Effect 3.7.0's
Scala-2 artifact required library 2.13.18, while the current compiler is 2.13.16.
Do not solve compatibility failures by disabling `-Werror` or strict tests.
Compute version-dependent compiler options in the appropriate project/row scope;
do not assume a ThisBuild expression observes a row-specific Scala version.

## Pitfalls

- A resource generator evaluating its own `Test/fullClasspath` can create
  `resources -> fullClasspath -> resources` and silently park sbt.
  Preserve the stdio fixture's dependencyClasspath plus classDirectory recipe.
- A matrix rename can break `exampleStdio/stage`, docs, aliases, and CI.
- A root aggregate can omit a claimed platform row.
- Source compilation alone does not prove JS execution or Native linking.
- The shell's `JAVA_HOME` does not prove a reused thin-client JVM changed.

## Verification

Read actual project ids and resolved artifacts. Verify Java 17 runtime,
strict compilation, platform test executables, staged launcher contents, and
the independent process checks. Record unsupported rows explicitly.
