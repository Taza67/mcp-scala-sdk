# First Cloud Lot: Repair The Stdio Client

This is a lead-authored implementation brief, not a completed change.
Read the current draft and tests before editing. The last 79-pass gate does
not cover the defects below. None of these repairs was started locally.

## Preserve

- Existing `StreamingClientTransport`, `ClientStream`, and correlation policies.
- Bounded owned reader/writer pumps, explicit `ownedStreams`, no shell spawning.
- Per-request routing, typed failures, cancellation frames, bounded tombstones.
- The committed borrowed-stream `StdioTransport` behavior and `StdioFrames`.
- The acyclic process-fixture classpath generator in the unfinished `build.sbt`.
- Genuine fatal failures and external interruption, including primary identity.

## Reviewed Defects And Required Regressions

1. Connection poison does not tear down owned resources.
   `poisonLocked` and `failConnectionFatal` only set flags/fail entries; they do
   not stop the writer parked on `outbound.take`, close owned pipes, or terminate
   an owned live child. Arrange exactly-once teardown outside the registry lock.
   Never join/close while holding that lock. Deliver pending failure/fatal signals
   before cleanup; retain an already-enqueued terminal response on normal EOF.
   Prove bad input, unknown ids, registry overflow, and writer failure end the
   owned connection without an additional user call to `transport.close()`.

2. Per-entry overflow abandons a still-running remote request.
   `offerOrFail` removes the entry and marks a connection-failure terminal signal.
   Its later close therefore sends no cancellation. Use explicit connection
   poison/owned teardown on overflow instead of silently orphaning subscriptions.
   Test a flooded bounded queue and actual remote EOF/termination.

3. Cancellation and send setup are not exception-safe.
   `Entry.close` sends the cancellation before clearing its queue/enqueuing the
   end marker. A fatal encoder can leave a blocked pull asleep forever.
   Retire/unblock the entry even when cancel encoding fails; preserve the fatal.
   Roll back registered entries on a failed/fatal request send, including their
   progress-token reservation. Clean up an owned child if construction or pump
   startup fails after spawn. Validate a null executable without echoing argv.
   Test fatal cancel encoding, consumer unblock, and retry after send failure.

4. Close can mask fatal cleanup and report success with live workers.
   An earlier nonfatal output-close failure currently wins over a later fatal
   input-close failure. Give genuine fatal errors priority over nonfatal cleanup;
   preserve the first genuine fatal over subsequent ones.
   Check liveness after bounded joins and the final child termination wait.
   Surface a static shutdown failure rather than silently leaving a live worker.
   Preserve external interruption; do not swallow it in `joinQuietly`.

5. Close can block before it reaches owned-child termination.
   The writer can hold a `BufferedOutputStream` monitor while blocked on a full
   child stdin pipe. `out.close()` first then waits for the same monitor forever,
   and child destruction is never reached.
   Use a bounded teardown strategy: attempt graceful EOF, terminate only the
   owned child if a blocked writer prevents it, then complete closure and joins.
   Do not add a per-request executor. For uncooperative transferred streams,
   report bounded failure instead of a false success.
   Add a REAL `--hang` child with a valid request larger than a pipe buffer,
   ensure the writer is blocked, then prove close completes and the child/pumps
   stop. The existing stuck-child test writes no request and misses this case.

6. Unsolicited writer interruption is downgraded.
   `writerLoop` treats every InterruptedException as connection poison.
   Suppress it only when teardown initiated cancellation; otherwise relay the
   original fatal/control-flow signal to pending callers, as on the reader path.
   An interrupted entry wait must keep its original identity even if cancellation
   cleanup throws a second fatal. Do not catch all throwables on ordinary cleanup.

7. A terminal response can be accepted after the total deadline.
   `nextWithin` checks the remaining time before polling only. Check total elapsed
   transport time before accepting the terminal response as well.
   Do not restart the timeout for each progress notification. User callbacks are
   caller-owned/cooperative; do not add a callback executor to conceal blocking.

## Implementation Discipline

Use one coherent rework pass, with regression tests for all seven findings.
Choose a minimal ownership/teardown design before dispatching code work.
Do not rewrite the transport from scratch or alter the protocol kernel to fit
incorrect fixtures. Use `StringProgressToken`/`NumberProgressToken`, not an
invented `ProgressToken.apply`.

Keep new test peers independent where checking literal framing/headers.
Use latches or controlled blocking streams instead of arbitrary sleeps.
Release gates and close/join all owned peers in `finally`, including failing tests.
Never kill an unrelated process during environment repair.

The review must cover the full final diff, including tests and build wiring.
Capture pre-rework snapshots so follow-up review can inspect exact changed hunks,
rather than rereading every unchanged 1,000-line fixture file.

## Narrow Gate

Use a real Java 17 process and record its version. The previous foreground sbt
batch exited; no old thin-client server is running.

```bash
JAVA_HOME=/path/to/jdk17 sbt --server -batch \
  'transportStdio/testOnly io.github.taza67.mcp.transport.stdio.StdioClientTransportSuite'
```

The framing and old server suites passed 12/12 and 31/31 on the unchanged,
committed extraction. Rerun them only if their implementation changes.
Do not run all old HTTP/codec suites after this ownership-only repair.
Save a new log and distinguish it from `mcp-stdio-client-green.log`.

After a complete review and a green repair gate, commit the client plus its
acyclic test fixture wiring. Do not include unrelated cloud-bootstrap artifacts
or unreviewed files in that implementation commit.
