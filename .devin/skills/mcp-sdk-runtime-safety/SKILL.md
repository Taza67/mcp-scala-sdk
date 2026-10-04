---
name: mcp-sdk-runtime-safety
description: Verify ownership, cancellation, and failure semantics.
---

# Runtime Safety

## When To Use

Load for streams, HTTP bodies, stdio processes, background workers, queues,
timeouts, effect cancellation, and resource cleanup.

## Procedure

1. Name every owned and borrowed resource and its single release authority.
2. Define active, terminal, cancelled, and failed states before writing concurrency.
3. Keep blocking I/O, iterator pulls, callbacks, and joins outside state locks.
4. Make cancellation wake blocked consumers and suppress messages delivered late.
5. Stop producers after a terminal response; never perform another pull afterward.
6. Bound bytes, depth, queues, pending requests, deadlines, and shutdown waits.
7. Add deterministic regressions for each transition and every failure/cleanup edge.

## Failure Semantics

- A failed codec, body read, or callback is not a successful default result.
- A successful decode still fails if critical owned cleanup fails.
- Poisoning a connection must stop its pumps and release owned pipes/children,
  not merely set a flag while a writer waits forever.
- A per-entry queue overflow must not orphan a live remote subscription.
- Relay unsolicited interruption and genuine fatal failures with their identity.
- Suppress an interrupt only when your own cancellation state explains it.
- A secondary cleanup failure must not mask an already-propagating actual fatal.
  Never use that exception-preservation rule for a merely nonfatal primary.
- Do not swallow fatal cleanup behind a static nonfatal error.
- Do not treat a failed queue put as a delivered failure marker; an existing
  interrupt flag may make the first put throw before any marker is queued.

## Pitfalls

Closing a borrowed stream changes a caller's resource and is forbidden.
An explicitly owned stream/body closes once even when timer, decoder, terminal
response, and user cancellation race. Clear active-puller state on every exit:
a later close must not interrupt a thread doing unrelated work.

Do not assume closing process stdin is nonblocking: a buffered writer may hold
its monitor while blocked on a full child pipe. Use bounded teardown and
terminate only the child the transport created.
Check liveness after joins/termination; report failure if an owned worker remains.

## Regression Fixtures

Use controlled input/output streams, latches, and literal wire messages.
Test a read already in flight, not only a close that wins before reading starts.
Test buffered notification/response delivery after cancellation, encoder failure
during cancel, fatal pull plus fatal close, failed headers after source creation,
manual self-interruption, a full child stdin pipe, and resource-close counters.

Release test gates and owned processes/threads in `finally`. Do not use a client's
timeout as the only evidence that a server's own handshake worker terminated.

## Verification

Inspect the full implementation and regression diff, including state transitions
and cleanup ordering. Check the narrow suite logs and owned-worker liveness.
Document cooperative callback/stream requirements honestly; do not claim an
uncooperative user computation can be forcibly cancelled by an effect wrapper.
