package io.github.taza67.mcp.server

import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

import io.github.taza67.mcp.codec.mcp.{Messages => McpMessages}
import io.github.taza67.mcp.codec.mcp.subscriptions.{Subscriptions => SubscriptionsCodec}
import io.github.taza67.mcp.protocol.jsonrpc.StringRequestId
import io.github.taza67.mcp.protocol.mcp.Implementation
import io.github.taza67.mcp.protocol.mcp.McpMessage
import io.github.taza67.mcp.protocol.mcp.McpNotification
import io.github.taza67.mcp.protocol.mcp.McpSuccessResponse
import io.github.taza67.mcp.protocol.mcp.subscriptions.{Subscriptions => SubscriptionMethods}
import io.github.taza67.mcp.protocol.mcp.subscriptions.SubscriptionFilter
import io.github.taza67.mcp.protocol.mcp.subscriptions.SubscriptionsAcknowledgedNotificationParams
import munit.FunSuite



class SubscriptionHubSuite extends FunSuite {

  private val id = StringRequestId("sub-1")

  private val supportedAll = SubscriptionFilter(
    toolsListChanged = Some(true),
    promptsListChanged = Some(true),
    resourcesListChanged = Some(true),
    resourceSubscriptions = Some(List("file:///a", "file:///b"))
  )

  private def requestedAll = SubscriptionFilter(
    toolsListChanged = Some(true),
    promptsListChanged = Some(true),
    resourcesListChanged = Some(true),
    resourceSubscriptions = Some(List("file:///a", "file:///missing"))
  )

  private def pullAsync(
      stream: ServerStream,
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

  private def decodeAck(
      message: Option[McpMessage]
  ): SubscriptionsAcknowledgedNotificationParams =
    message match {
      case Some(notification: McpNotification)
          if notification.method == SubscriptionMethods.acknowledgedNotification =>
        notification.params.flatMap { params =>
          SubscriptionsCodec
            .toSubscriptionsAcknowledgedNotificationParams(params)
            .toOption
        } match {
          case Some(decoded) => decoded
          case None          => fail(s"undecodable ack params: $message")
        }
      case other => fail(s"expected acknowledgement notification, got $other")
    }

  private def terminalOf(
      message: Option[McpMessage]
  ): Unit =
    message match {
      case Some(response: McpSuccessResponse) =>
        val decoded = SubscriptionsCodec.toSubscriptionsListenResultResponse(
          McpMessages.fromMessage(response)
        )
        assert(decoded.isRight, s"expected listen result response, got $decoded")
      case other => fail(s"expected terminal success response, got $other")
    }

  test("ack is the literal first message and reports the granted subset") {
    val hub = new SubscriptionHub(supportedAll)
    try {
      val stream = hub.open(id, requestedAll)
      val ack = decodeAck(stream.next())
      assertEquals(
        ack.notifications,
        SubscriptionFilter(
          toolsListChanged = Some(true),
          promptsListChanged = Some(true),
          resourcesListChanged = Some(true),
          resourceSubscriptions = Some(List("file:///a"))
        )
      )
      assertEquals(ack.meta.flatMap(_.subscriptionId), Some(id))
    } finally hub.close()
  }

  test("unrequested and unsupported flags are omitted from the grant") {
    val hub = new SubscriptionHub(
      SubscriptionFilter(toolsListChanged = Some(true))
    )
    try {
      val stream = hub.open(id, requestedAll)
      val ack = decodeAck(stream.next())
      assertEquals(
        ack.notifications,
        SubscriptionFilter(toolsListChanged = Some(true))
      )
    } finally hub.close()

    val falseRequested = new SubscriptionHub(supportedAll)
    try {
      val stream = falseRequested.open(
        id,
        SubscriptionFilter(
          toolsListChanged = Some(false),
          promptsListChanged = Some(true)
        )
      )
      val ack = decodeAck(stream.next())
      assertEquals(
        ack.notifications,
        SubscriptionFilter(promptsListChanged = Some(true))
      )
    } finally falseRequested.close()
  }

  test("resource subscriptions grant the intersection with supported URIs") {
    val hub = new SubscriptionHub(supportedAll)
    try {
      val stream = hub.open(
        id,
        SubscriptionFilter(
          resourceSubscriptions = Some(List("file:///b", "file:///x"))
        )
      )
      val ack = decodeAck(stream.next())
      assertEquals(
        ack.notifications.resourceSubscriptions,
        Some(List("file:///b"))
      )
    } finally hub.close()
  }

  test("a publish after open never precedes the acknowledgement") {
    val hub = new SubscriptionHub(supportedAll)
    try {
      val stream = hub.open(id, requestedAll)
      hub.publishToolsListChanged()
      hub.publishPromptsListChanged()
      val first = stream.next()
      val second = stream.next()
      val third = stream.next()
      decodeAck(first)
      assertEquals(
        second.map(_.asInstanceOf[McpNotification].method.value),
        Some("notifications/tools/list_changed")
      )
      assertEquals(
        third.map(_.asInstanceOf[McpNotification].method.value),
        Some("notifications/prompts/list_changed")
      )
    } finally hub.close()
  }

  test("a concurrent publish during open never precedes the acknowledgement") {
    val hub = new SubscriptionHub(supportedAll)
    try
      for (round <- 1 to 20) {
        val barrier = new CyclicBarrier(2)
        val streamRef = new AtomicReference[ServerStream]()
        val failure = new AtomicReference[Throwable]()
        val opener = new Thread(new Runnable {
          def run(): Unit =
            try {
              barrier.await(10, TimeUnit.SECONDS)
              streamRef.set(hub.open(id, requestedAll))
            } catch { case thrown: Throwable => failure.set(thrown) }
        })
        val publisher = new Thread(new Runnable {
          def run(): Unit =
            try {
              barrier.await(10, TimeUnit.SECONDS)
              hub.publishToolsListChanged()
            } catch { case thrown: Throwable => failure.set(thrown) }
        })
        opener.setDaemon(true)
        publisher.setDaemon(true)
        opener.start()
        publisher.start()
        opener.join(10000)
        publisher.join(10000)
        if (failure.get() != null) throw failure.get()
        val stream = streamRef.get()
        assert(stream != null, s"round $round: open produced no stream")
        // Whether the publish ran before or after registration, the first
        // delivered item is always the acknowledgement.
        decodeAck(stream.next())
        stream.close()
        assertEquals(stream.next(), None)
      }
    finally hub.close()
  }

  test("stream close discards buffered messages instead of delivering them") {
    val hub = new SubscriptionHub(supportedAll)
    try {
      val stream = hub.open(id, requestedAll)
      // Queued: ack plus one publication, neither consumed.
      hub.publishToolsListChanged()
      stream.close()
      assertEquals(stream.next(), None)
      assertEquals(stream.next(), None)
    } finally hub.close()
  }

  test("two streams opened with the same request id stay independent") {
    val hub = new SubscriptionHub(supportedAll)
    try {
      val first = hub.open(id, requestedAll)
      val second = hub.open(id, requestedAll)
      decodeAck(first.next())
      decodeAck(second.next())
      hub.publishResourcesListChanged()
      // first stream closed: abrupt end, no terminal
      first.close()
      assertEquals(first.next(), None)
      // second still receives the published notification
      val delivered = second.next()
      assertEquals(
        delivered.map(_.asInstanceOf[McpNotification].method.value),
        Some("notifications/resources/list_changed")
      )
      val meta = delivered.flatMap {
        case notification: McpNotification =>
          notification.params.flatMap(_.meta).flatMap(_.subscriptionId)
        case _ => None
      }
      assertEquals(meta, Some(id))
    } finally hub.close()
  }

  test("unrequested publications are ignored before the terminal response") {
    val hub = new SubscriptionHub(supportedAll)
    try {
      val stream = hub.open(id, SubscriptionFilter())
      decodeAck(stream.next())
      hub.publishToolsListChanged()
      hub.publishPromptsListChanged()
      hub.publishResourcesListChanged()
      hub.publishResourceUpdated("file:///a")
      hub.close()
      terminalOf(stream.next())
      assertEquals(stream.next(), None)
    } finally hub.close()
  }

  test("graceful shutdown enqueues a terminal result with the subscription id") {
    val info = Implementation(name = "srv", version = "1")
    val hub = new SubscriptionHub(supportedAll, serverInfo = Some(info))
    val stream = hub.open(id, requestedAll)
    decodeAck(stream.next())
    hub.close()
    stream.next() match {
      case Some(response: McpSuccessResponse) =>
        val decoded = SubscriptionsCodec.toSubscriptionsListenResultResponse(
          McpMessages.fromMessage(response)
        )
        decoded match {
          case Right(listenResult) =>
            assertEquals(listenResult.result.meta.subscriptionId, id)
            assertEquals(listenResult.result.meta.serverInfo, Some(info))
            assertEquals(listenResult.id, id)
          case Left(error) =>
            fail(s"expected decodable listen result, got $error")
        }
      case other => fail(s"expected terminal success, got $other")
    }
    assertEquals(stream.next(), None)
  }

  test("notification overflow aborts the stream instead of dropping silently") {
    val hub = new SubscriptionHub(supportedAll, maxPendingNotifications = 2)
    try {
      val stream = hub.open(id, requestedAll)
      // Queue: [ack]; capacity 3; publish beyond maxPendingNotifications.
      hub.publishToolsListChanged() // size 1 -> 2
      hub.publishToolsListChanged() // size 2 -> abort: clear + end marker
      assertEquals(stream.next(), None)
      // Stream unregistered: further publishes do not resurrect it.
      hub.publishToolsListChanged()
      assertEquals(stream.next(), None)
    } finally hub.close()
  }

  test("stream close unblocks a waiting consumer and unregisters") {
    val hub = new SubscriptionHub(supportedAll)
    try {
      val stream = hub.open(id, requestedAll)
      decodeAck(stream.next())
      val outcome = new AtomicReference[Option[McpMessage]]()
      val finished = new CountDownLatch(1)
      val consumer = pullAsync(stream, outcome, finished)
      // close() aborts and unblocks the consumer whether or not it has
      // already reached take().
      stream.close()
      assert(finished.await(10, TimeUnit.SECONDS))
      assertEquals(outcome.get(), None)
      consumer.join(1000)
      // Unregistered: publications after close are no-ops for this stream.
      hub.publishToolsListChanged()
      assertEquals(stream.next(), None)
    } finally hub.close()
  }

  test("close is idempotent and open fails fast after shutdown") {
    val hub = new SubscriptionHub(supportedAll)
    val stream = hub.open(id, requestedAll)
    decodeAck(stream.next())
    hub.close()
    hub.close()
    terminalOf(stream.next())
    var observed: Throwable = null
    try {
      val _ = hub.open(StringRequestId("late"), requestedAll)
    } catch { case thrown: Throwable => observed = thrown }
    assert(observed.isInstanceOf[IllegalStateException])
    // publish after shutdown is a no-op
    hub.publishToolsListChanged()
  }

  test("nonpositive maxPendingNotifications is rejected") {
    var observed: Throwable = null
    try {
      val _ = new SubscriptionHub(supportedAll, maxPendingNotifications = 0)
    } catch { case thrown: Throwable => observed = thrown }
    assert(observed.isInstanceOf[IllegalArgumentException])
  }
}
