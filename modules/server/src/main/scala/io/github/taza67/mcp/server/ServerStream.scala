package io.github.taza67.mcp.server

import java.util.concurrent.atomic.AtomicReference

import io.github.taza67.mcp.protocol.mcp.McpMessage
import io.github.taza67.mcp.protocol.mcp.McpResponse



/** Pull stream of outbound [[McpMessage]]s produced for one client request.
 *
 *  A single consumer pulls; `next()` yields each message in order and
 *  returns `None` once the stream ends. `close()` may be invoked from
 *  another thread, is idempotent, and must unblock an in-flight `next()`
 *  which then returns `None` rather than a late message.
 *
 *  Both contracts are cooperative: a producer blocked inside `next()` is
 *  released by interrupting the pulling thread, so message evaluation must
 *  honor interruption.
 */
trait ServerStream extends AutoCloseable {

  /** Returns the next message, or `None` once the stream ended or was
   *  closed.
   */
  def next(): Option[McpMessage]

  /** Cancels the stream: an in-flight or later `next()` returns `None` and
   *  no terminal response is emitted.
   */
  override def close(): Unit
}

object ServerStream {

  /** One-shot stream that evaluates `response` lazily on the first pull.
   *
   *  `close()` before or during that pull wins: the pulling thread is
   *  interrupted and `next()` returns `None`. No lock is held while the
   *  response is evaluated.
   */
  def single(response: => McpResponse): ServerStream =
    new ServerStream {
      private val state =
        new AtomicReference[ServerStream.State](ServerStream.Pending)

      def next(): Option[McpMessage] = {
        val pulling = ServerStream.Pulling(Thread.currentThread())
        if (!state.compareAndSet(ServerStream.Pending, pulling)) None
        else
          try {
            val result = response
            if (state.compareAndSet(pulling, ServerStream.Done)) Some(result)
            else None
          } catch {
            case interrupted: InterruptedException =>
              if (state.get() == ServerStream.Cancelled) None
              else throw interrupted
          } finally {
            // Never leave Pulling behind once evaluation exits: a later
            // close() must not interrupt a thread that is no longer pulling.
            val _ = state.compareAndSet(pulling, ServerStream.Done)
          }
      }

      def close(): Unit =
        state.getAndSet(ServerStream.Cancelled) match {
          case ServerStream.Pulling(thread) => thread.interrupt()
          case _                            => ()
        }
    }

  /** Adapts an iterator of messages to the same ownership and cooperative
   *  close contract.
   *
   *  `onClose` runs exactly once, on exhaustion or explicit `close()`,
   *  whichever comes first; its failures are never swallowed. A blocking
   *  `next()` on the iterator is released by interrupting the pulling
   *  thread.
   */
  def fromIterator(
      messages: Iterator[McpMessage],
      onClose: () => Unit = () => ()
  ): ServerStream =
    new ServerStream {
      private val lock = new Object
      private var closed = false
      private var finished = false
      private var closeRan = false
      private var pullThread: Thread = _

      def next(): Option[McpMessage] = {
        lock.synchronized {
          if (closed || finished) return None
          pullThread = Thread.currentThread()
        }
        // The iterator is never locked: a single consumer owns pulls, and
        // onClose may need to synchronize on the same iterator to release a
        // blocking read it wraps.
        val pulled =
          try {
            if (messages.hasNext) Some(messages.next()) else None
          } catch {
            case interrupted: InterruptedException =>
              val wasClosed = lock.synchronized {
                pullThread = null
                closed
              }
              if (wasClosed) return None else throw interrupted
            case failure: Throwable =>
              lock.synchronized { pullThread = null }
              throw failure
          }
        val (outcome, exhausted) = lock.synchronized {
          pullThread = null
          if (closed) (None, false)
          else if (pulled.isEmpty) {
            finished = true
            (None, true)
          } else (pulled, false)
        }
        if (exhausted) runCloseOnce()
        outcome
      }

      def close(): Unit = {
        val thread = lock.synchronized {
          if (closed) null
          else {
            closed = true
            pullThread
          }
        }
        if (thread != null) thread.interrupt()
        runCloseOnce()
      }

      private def runCloseOnce(): Unit = {
        val firstRun = lock.synchronized {
          if (closeRan) false
          else {
            closeRan = true
            true
          }
        }
        if (firstRun) onClose()
      }
    }

  private sealed trait State
  private case object Pending extends State
  private final case class Pulling(thread: Thread) extends State
  private case object Done extends State
  private case object Cancelled extends State
}
