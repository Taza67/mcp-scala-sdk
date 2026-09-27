package io.github.taza67.mcp.server

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

import io.github.taza67.mcp.protocol.jsonrpc.Method
import io.github.taza67.mcp.protocol.jsonrpc.StringRequestId
import io.github.taza67.mcp.protocol.mcp.McpMessage
import io.github.taza67.mcp.protocol.mcp.McpNotification
import io.github.taza67.mcp.protocol.mcp.McpSuccessResponse
import io.github.taza67.mcp.protocol.mcp.Result
import munit.FunSuite



class ServerStreamSuite extends FunSuite {

  private val response =
    McpSuccessResponse(result = Result.empty(), id = StringRequestId("r1"))

  private def notification(name: String): McpNotification =
    McpNotification(method = Method(name))

  private def pullAsync(stream: ServerStream)(
      outcome: AtomicReference[Option[McpMessage]],
      finished: CountDownLatch
  ): Thread = {
    val consumer = new Thread(new Runnable {
      def run(): Unit = {
        outcome.set(stream.next())
        finished.countDown()
      }
    })
    consumer.setDaemon(true)
    consumer.start()
    consumer
  }

  test("single evaluates the response lazily on the first pull") {
    val evaluations = new AtomicInteger(0)
    val stream = ServerStream.single {
      evaluations.incrementAndGet()
      response
    }
    assertEquals(evaluations.get(), 0)
    assertEquals(stream.next(), Some(response))
    assertEquals(evaluations.get(), 1)
    assertEquals(stream.next(), None)
    assertEquals(evaluations.get(), 1)
  }

  test("single close before the first pull skips evaluation") {
    val evaluations = new AtomicInteger(0)
    val stream = ServerStream.single {
      evaluations.incrementAndGet()
      response
    }
    stream.close()
    assertEquals(stream.next(), None)
    assertEquals(evaluations.get(), 0)
  }

  test("single close interrupts and unblocks a pull in flight") {
    val entered = new CountDownLatch(1)
    val interrupted = new AtomicInteger(0)
    val stream = ServerStream.single {
      entered.countDown()
      try {
        Thread.sleep(Long.MaxValue)
        response
      } catch {
        case interruptedException: InterruptedException =>
          interrupted.incrementAndGet()
          throw interruptedException
      }
    }
    val outcome = new AtomicReference[Option[McpMessage]]()
    val finished = new CountDownLatch(1)
    val consumer = pullAsync(stream)(outcome, finished)
    assert(entered.await(10, TimeUnit.SECONDS))
    stream.close()
    assert(finished.await(10, TimeUnit.SECONDS))
    assertEquals(outcome.get(), None)
    assertEquals(interrupted.get(), 1)
    consumer.join(1000)
  }

  test("single close is idempotent") {
    val stream = ServerStream.single(response)
    stream.close()
    stream.close()
    assertEquals(stream.next(), None)
  }

  test("single propagates a fatal thrown by the lazy response") {
    val fatal = new LinkageError("single-fatal")
    val stream = ServerStream.single(throw fatal)
    var observed: Throwable = null
    try {
      val _ = stream.next()
    } catch { case thrown: Throwable => observed = thrown }
    assertEquals(observed, fatal)
  }

  test("single propagates a nonfatal thrown by the lazy response") {
    val failure = new RuntimeException("single-failure")
    val stream = ServerStream.single(throw failure)
    var observed: Throwable = null
    try {
      val _ = stream.next()
    } catch { case thrown: Throwable => observed = thrown }
    assertEquals(observed, failure)
  }

  test("single failure marks the stream done so close does not interrupt") {
    val evaluations = new AtomicInteger(0)
    val stream = ServerStream.single {
      evaluations.incrementAndGet()
      throw new RuntimeException("single-failure")
    }
    var observed: Throwable = null
    try {
      val _ = stream.next()
    } catch { case thrown: Throwable => observed = thrown }
    assert(observed.isInstanceOf[RuntimeException])
    stream.close()
    // The pulling thread already exited evaluation: close must not
    // interrupt this (now unrelated) caller thread.
    assert(!Thread.interrupted())
    assertEquals(stream.next(), None)
    assertEquals(evaluations.get(), 1)
  }

  test("single fatal failure marks the stream done and keeps its identity") {
    val fatal = new LinkageError("single-fatal")
    val stream = ServerStream.single(throw fatal)
    var observed: Throwable = null
    try {
      val _ = stream.next()
    } catch { case thrown: Throwable => observed = thrown }
    assertEquals(observed, fatal)
    stream.close()
    assert(!Thread.interrupted())
  }

  test("single external interruption ends the stream without re-evaluating") {
    val evaluations = new AtomicInteger(0)
    val stream = ServerStream.single {
      evaluations.incrementAndGet()
      throw new InterruptedException("external")
    }
    var observed: Throwable = null
    try {
      val _ = stream.next()
    } catch { case thrown: Throwable => observed = thrown }
    assert(observed.isInstanceOf[InterruptedException])
    assertEquals(stream.next(), None)
    assertEquals(evaluations.get(), 1)
  }

  test("fromIterator yields messages then None on exhaustion") {
    val closed = new AtomicInteger(0)
    val stream = ServerStream.fromIterator(
      Iterator(notification("a"), notification("b")),
      onClose = () => { closed.incrementAndGet(); () }
    )
    assertEquals(
      stream.next().map(_.asInstanceOf[McpNotification].method.value),
      Some("a")
    )
    assertEquals(
      stream.next().map(_.asInstanceOf[McpNotification].method.value),
      Some("b")
    )
    assertEquals(stream.next(), None)
    assertEquals(stream.next(), None)
    assertEquals(closed.get(), 1)
  }

  test("fromIterator close runs onClose exactly once and unblocks") {
    val entered = new CountDownLatch(1)
    val closed = new AtomicInteger(0)
    val blockingIterator = new Iterator[McpMessage] {
      def hasNext: Boolean = {
        entered.countDown()
        Thread.sleep(Long.MaxValue)
        true
      }
      def next(): McpMessage = notification("never")
    }
    val stream = ServerStream.fromIterator(
      blockingIterator,
      onClose = () => { closed.incrementAndGet(); () }
    )
    val outcome = new AtomicReference[Option[McpMessage]]()
    val finished = new CountDownLatch(1)
    val consumer = pullAsync(stream)(outcome, finished)
    assert(entered.await(10, TimeUnit.SECONDS))
    stream.close()
    stream.close()
    assert(finished.await(10, TimeUnit.SECONDS))
    assertEquals(outcome.get(), None)
    assertEquals(closed.get(), 1)
    consumer.join(1000)
  }

  test("fromIterator does not swallow a nonfatal onClose failure") {
    val failure = new RuntimeException("close-failure")
    val stream = ServerStream.fromIterator(
      Iterator.empty,
      onClose = () => throw failure
    )
    var observed: Throwable = null
    try {
      stream.close()
    } catch { case thrown: Throwable => observed = thrown }
    assertEquals(observed, failure)
  }

  test("fromIterator propagates a fatal from the iterator") {
    val fatal = new LinkageError("iterator-fatal")
    val stream = ServerStream.fromIterator(
      new Iterator[McpMessage] {
        def hasNext: Boolean = throw fatal
        def next(): McpMessage = notification("never")
      }
    )
    var observed: Throwable = null
    try {
      val _ = stream.next()
    } catch { case thrown: Throwable => observed = thrown }
    assertEquals(observed, fatal)
  }

  test("fromIterator close does not deadlock on the iterator monitor") {
    val entered = new CountDownLatch(1)
    val gate = new CountDownLatch(1)
    val iterator = new Iterator[McpMessage] {
      def hasNext: Boolean = {
        entered.countDown()
        // Ignores interrupts: only onClose opening the gate releases this.
        var released = false
        while (!released)
          try {
            released = gate.await(10, TimeUnit.SECONDS)
          } catch { case _: InterruptedException => () }
        false
      }
      def next(): McpMessage = notification("never")
    }
    val closed = new AtomicInteger(0)
    val stream = ServerStream.fromIterator(
      iterator,
      onClose = () =>
        iterator.synchronized {
          // The pull must not hold the iterator monitor while blocked in
          // hasNext, or this acquisition would deadlock.
          closed.incrementAndGet()
          gate.countDown()
        }
    )
    val outcome = new AtomicReference[Option[McpMessage]]()
    val finished = new CountDownLatch(1)
    val consumer = pullAsync(stream)(outcome, finished)
    assert(entered.await(10, TimeUnit.SECONDS))
    stream.close()
    assert(finished.await(10, TimeUnit.SECONDS))
    assertEquals(outcome.get(), None)
    assertEquals(closed.get(), 1)
    consumer.join(1000)
  }
}
