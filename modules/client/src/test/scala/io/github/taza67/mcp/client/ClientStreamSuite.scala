package io.github.taza67.mcp.client

import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

import io.github.taza67.mcp.protocol.json.JsonArray
import io.github.taza67.mcp.protocol.json.JsonBool
import io.github.taza67.mcp.protocol.json.JsonNumber
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.jsonrpc.ApplicationError
import io.github.taza67.mcp.protocol.jsonrpc.Method
import io.github.taza67.mcp.protocol.jsonrpc.NumberRequestId
import io.github.taza67.mcp.protocol.jsonrpc.StringRequestId
import io.github.taza67.mcp.protocol.mcp.ClientCapabilities
import io.github.taza67.mcp.protocol.mcp.CompleteResultType
import io.github.taza67.mcp.protocol.mcp.InfoLoggingLevel
import io.github.taza67.mcp.protocol.mcp.McpErrorResponse
import io.github.taza67.mcp.protocol.mcp.McpMessage
import io.github.taza67.mcp.protocol.mcp.McpNotification
import io.github.taza67.mcp.protocol.mcp.McpProtocolVersion20260728
import io.github.taza67.mcp.protocol.mcp.McpRequest
import io.github.taza67.mcp.protocol.mcp.McpSuccessResponse
import io.github.taza67.mcp.protocol.mcp.NotificationMeta
import io.github.taza67.mcp.protocol.mcp.NotificationParams
import io.github.taza67.mcp.protocol.mcp.RequestMeta
import io.github.taza67.mcp.protocol.mcp.RequestParams
import io.github.taza67.mcp.protocol.mcp.Result
import io.github.taza67.mcp.protocol.mcp.ResultMeta
import io.github.taza67.mcp.protocol.mcp.MetaObject
import io.github.taza67.mcp.protocol.mcp.StringProgressToken
import io.github.taza67.mcp.protocol.mcp.notifications.{Notifications => NotificationMethods}
import io.github.taza67.mcp.protocol.mcp.prompts.{Prompts => PromptMethods}
import io.github.taza67.mcp.protocol.mcp.resources.{Resources => ResourceMethods}
import io.github.taza67.mcp.protocol.mcp.subscriptions.SubscriptionFilter
import io.github.taza67.mcp.protocol.mcp.subscriptions.{Subscriptions => SubscriptionMethods}
import io.github.taza67.mcp.protocol.mcp.subscriptions.SubscriptionsListenResultMeta
import io.github.taza67.mcp.protocol.mcp.tools.{Tools => ToolMethods}
import munit.FunSuite



class ClientStreamSuite extends FunSuite {

  private val meta = RequestMeta(
    protocolVersion = McpProtocolVersion20260728,
    clientCapabilities = ClientCapabilities()
  )

  private val request = McpRequest(
    method = Method("example/stream"),
    id = NumberRequestId(7),
    params = Some(RequestParams(meta = meta))
  )

  /** Preloaded pull source; counts closes. */
  private final class FixtureStream(
      items: List[Either[ClientError, Option[McpMessage]]]
  ) extends ClientStream {
    private val queue = scala.collection.mutable.Queue(items: _*)
    var closes = 0

    def next(): Either[ClientError, Option[McpMessage]] =
      if (queue.isEmpty) Right(None) else queue.dequeue()

    def close(): Unit = closes += 1
  }

  /** Source whose `next()` blocks until `close()` releases it. */
  private final class BlockingStream extends ClientStream {
    private val gate = new CountDownLatch(1)
    val entered = new CountDownLatch(1)

    def next(): Either[ClientError, Option[McpMessage]] = {
      entered.countDown()
      gate.await()
      Right(None)
    }

    def close(): Unit = gate.countDown()
  }

  private def notification(
      method: Method,
      subscriptionId: Option[NumberRequestId] = None,
      fields: JsonObject = JsonObject(Map.empty)
  ): McpNotification =
    McpNotification(
      method,
      Some(
        NotificationParams(
          meta = subscriptionId.map(id => NotificationMeta(Some(id))),
          fields = fields
        )
      )
    )

  private def acknowledged(
      subscriptionId: Option[NumberRequestId],
      fields: JsonObject
  ): McpNotification =
    notification(
      SubscriptionMethods.acknowledgedNotification,
      subscriptionId,
      JsonObject(Map("notifications" -> fields))
    )

  test("a matching success response is terminal and closes the source") {
    val response = McpSuccessResponse(Result.empty(), NumberRequestId(7))
    val source = new FixtureStream(List(Right(Some(response))))
    val stream = ClientStream.correlated(request, source)

    assertEquals(stream.next(), Right(Some(response)))
    assertEquals(stream.next(), Right(None))
    assertEquals(stream.next(), Right(None))
    assertEquals(source.closes, 1)
  }

  test("successes with mismatched or foreign ids fail correlation") {
    val wrong = McpSuccessResponse(Result.empty(), NumberRequestId(9))
    val source = new FixtureStream(List(Right(Some(wrong))))
    val stream = ClientStream.correlated(request, source)

    assertEquals(stream.next(), Left(ClientError.ResponseIdMismatch))
    assertEquals(stream.next(), Right(None))
    assertEquals(source.closes, 1)
  }

  test("error responses correlate by id or fail as uncorrelated") {
    val remote = ApplicationError(code = 5, message = "nope")

    val matched = McpErrorResponse(remote, Some(NumberRequestId(7)))
    val matchedStream =
      ClientStream.correlated(request, new FixtureStream(List(Right(Some(matched)))))
    assertEquals(matchedStream.next(), Right(Some(matched)))
    assertEquals(matchedStream.next(), Right(None))

    val wrong = McpErrorResponse(remote, Some(NumberRequestId(3)))
    val wrongStream =
      ClientStream.correlated(request, new FixtureStream(List(Right(Some(wrong)))))
    assertEquals(wrongStream.next(), Left(ClientError.ResponseIdMismatch))

    val bare = McpErrorResponse(remote)
    val bareStream =
      ClientStream.correlated(request, new FixtureStream(List(Right(Some(bare)))))
    assertEquals(bareStream.next(), Left(ClientError.UncorrelatedResponse))
  }

  test("string request ids correlate the same way") {
    val stringRequest = request.copy(id = StringRequestId("sub-1"))
    val response = McpSuccessResponse(Result.empty(), StringRequestId("sub-1"))
    val stream =
      ClientStream.correlated(stringRequest, new FixtureStream(List(Right(Some(response)))))
    assertEquals(stream.next(), Right(Some(response)))
    assertEquals(stream.next(), Right(None))
  }

  test("an inbound request and EOF before a terminal response fail") {
    val inbound = McpRequest(Method("server/asks"), NumberRequestId(1))
    val stream =
      ClientStream.correlated(request, new FixtureStream(List(Right(Some(inbound)))))
    assertEquals(stream.next(), Left(ClientError.TransportFailure))

    val eof = ClientStream.correlated(request, new FixtureStream(Nil))
    assertEquals(eof.next(), Left(ClientError.TransportFailure))
  }

  test("a source failure passes through and closes the source") {
    val source =
      new FixtureStream(List(Left(ClientError.TransportFailure)))
    val stream = ClientStream.correlated(request, source)
    assertEquals(stream.next(), Left(ClientError.TransportFailure))
    assertEquals(source.closes, 1)
  }

  test("notifications pass; a mismatched subscription id is rejected") {
    val ok = notification(Method("vendor/custom"))
    val source = new FixtureStream(List(Right(Some(ok))))
    val stream = ClientStream.correlated(request, source)
    assertEquals(stream.next(), Right(Some(ok)))

    val foreign = notification(
      Method("vendor/custom"),
      subscriptionId = Some(NumberRequestId(42))
    )
    val bad = ClientStream.correlated(
      request,
      new FixtureStream(List(Right(Some(foreign))))
    )
    assertEquals(bad.next(), Left(ClientError.TransportFailure))
  }

  test("progress requires the request progress token") {
    val progressFields = JsonObject(
      Map(
        "progressToken" -> JsonString("t-1"),
        "progress" -> JsonNumber(BigDecimal(1))
      )
    )
    val progress = notification(NotificationMethods.progress, fields = progressFields)

    val withToken = request.copy(
      params = Some(
        RequestParams(meta = meta.copy(progressToken = Some(StringProgressToken("t-1"))))
      )
    )
    val okStream = ClientStream.correlated(
      withToken,
      new FixtureStream(List(Right(Some(progress))))
    )
    assertEquals(okStream.next(), Right(Some(progress)))

    val wrongToken = request.copy(
      params = Some(
        RequestParams(meta = meta.copy(progressToken = Some(StringProgressToken("t-2"))))
      )
    )
    val badStream = ClientStream.correlated(
      wrongToken,
      new FixtureStream(List(Right(Some(progress))))
    )
    assertEquals(badStream.next(), Left(ClientError.TransportFailure))

    val noToken = ClientStream.correlated(
      request,
      new FixtureStream(List(Right(Some(progress))))
    )
    assertEquals(noToken.next(), Left(ClientError.TransportFailure))
  }

  test("logging notifications require a request log level") {
    val logFields = JsonObject(
      Map("level" -> JsonString("info"), "data" -> JsonString("hi"))
    )
    val log = notification(NotificationMethods.message, fields = logFields)

    val withoutLevel = ClientStream.correlated(
      request,
      new FixtureStream(List(Right(Some(log))))
    )
    assertEquals(withoutLevel.next(), Left(ClientError.TransportFailure))

    val opted = request.copy(
      params =
        Some(RequestParams(meta = meta.copy(logLevel = Some(InfoLoggingLevel))))
    )
    val withLevel = ClientStream.correlated(
      opted,
      new FixtureStream(List(Right(Some(log))))
    )
    assertEquals(withLevel.next(), Right(Some(log)))
  }

  test("close unblocks an idle source and is idempotent") {
    val source = new BlockingStream
    val stream = ClientStream.correlated(request, source)
    val outcome = new AtomicReference[Either[ClientError, Option[McpMessage]]]()
    val finished = new CountDownLatch(1)
    val consumer = new Thread(() => {
      outcome.set(stream.next())
      finished.countDown()
    })
    consumer.start()
    assert(
      source.entered.await(10, TimeUnit.SECONDS),
      "next() never reached the source"
    )

    stream.close()
    assert(finished.await(10, TimeUnit.SECONDS), "next() stayed blocked")
    assertEquals(outcome.get(), Right(None))
    assertEquals(stream.next(), Right(None))

    stream.close()
    stream.close()
  }

  test("a message buffered at close time is never delivered") {
    val entered = new CountDownLatch(1)
    val gate = new CountDownLatch(1)
    val payload =
      new AtomicReference[Either[ClientError, Option[McpMessage]]](Right(None))
    val closes = new AtomicInteger(0)
    val source = new ClientStream {
      def next() = {
        entered.countDown()
        gate.await()
        payload.get()
      }
      def close() = {
        closes.incrementAndGet()
        gate.countDown()
      }
    }
    val stream = ClientStream.correlated(request, source)
    val outcome = new AtomicReference[Either[ClientError, Option[McpMessage]]]()
    val finished = new CountDownLatch(1)
    val consumer = new Thread(() => {
      outcome.set(stream.next())
      finished.countDown()
    })
    consumer.start()
    assert(entered.await(10, TimeUnit.SECONDS), "pull never started")
    // The source produces a message only because close() released the pull;
    // the wrapper must still drop it.
    payload.set(Right(Some(notification(Method("vendor/custom")))))
    stream.close()
    assert(finished.await(10, TimeUnit.SECONDS), "next() stayed blocked")
    assertEquals(outcome.get(), Right(None))
    assertEquals(closes.get(), 1)
  }

  test("a valid response surfaced at close time is never delivered") {
    val entered = new CountDownLatch(1)
    val gate = new CountDownLatch(1)
    val payload =
      new AtomicReference[Either[ClientError, Option[McpMessage]]](Right(None))
    val closes = new AtomicInteger(0)
    val source = new ClientStream {
      def next() = {
        entered.countDown()
        gate.await()
        payload.get()
      }
      def close() = {
        closes.incrementAndGet()
        gate.countDown()
      }
    }
    val stream = ClientStream.correlated(request, source)
    val outcome = new AtomicReference[Either[ClientError, Option[McpMessage]]]()
    val finished = new CountDownLatch(1)
    val consumer = new Thread(() => {
      outcome.set(stream.next())
      finished.countDown()
    })
    consumer.start()
    assert(entered.await(10, TimeUnit.SECONDS), "pull never started")
    payload.set(
      Right(Some(McpSuccessResponse(Result.empty(), NumberRequestId(7))))
    )
    stream.close()
    assert(finished.await(10, TimeUnit.SECONDS), "next() stayed blocked")
    assertEquals(outcome.get(), Right(None))
    assertEquals(closes.get(), 1)
  }

  test("a terminal delivery whose source close fails becomes a failure") {
    val response = McpSuccessResponse(Result.empty(), NumberRequestId(7))
    val closes = new AtomicInteger(0)
    val source = new ClientStream {
      def next() = Right(Some(response))
      def close() = {
        closes.incrementAndGet()
        throw new RuntimeException("close-secret")
      }
    }
    val stream = ClientStream.correlated(request, source)
    assertEquals(stream.next(), Left(ClientError.TransportFailure))
    assertEquals(closes.get(), 1)
    assertEquals(stream.next(), Right(None))
  }

  test("explicit close maps any NonFatal source failure statically") {
    val source = new ClientStream {
      def next() = Right(None)
      def close() = throw new RuntimeException("boom-secret")
    }
    val stream = ClientStream.correlated(request, source)
    val thrown = intercept[IOException](stream.close())
    assertEquals(thrown.getMessage, "MCP stream close failure")
    assertEquals(thrown.getCause, null)
    // Idempotent: the source close is attempted exactly once.
    stream.close()
  }

  test("a fatal source failure closes the source and propagates") {
    val closes = new AtomicInteger(0)
    val source = new ClientStream {
      def next() = throw new LinkageError("fatal")
      def close() = closes.incrementAndGet()
    }
    val stream = ClientStream.correlated(request, source)
    var sawFatal = false
    try {
      stream.next()
      ()
    } catch { case _: LinkageError => sawFatal = true }
    assert(sawFatal, "expected LinkageError to propagate")
    assertEquals(closes.get(), 1)
    assertEquals(stream.next(), Right(None))
  }

  test("correlated factories collapse for the same request only") {
    val inner = ClientStream.correlated(request, new FixtureStream(Nil))
    assert(ClientStream.correlated(request, inner) eq inner)

    val other = request.copy(id = NumberRequestId(8))
    val wrapped = ClientStream.correlated(other, inner)
    assert(!(wrapped eq inner))

    // A different request still gets its own correlation policy.
    val response = McpSuccessResponse(Result.empty(), NumberRequestId(7))
    val source = new FixtureStream(List(Right(Some(response))))
    val seven = ClientStream.correlated(request, source)
    val eight = ClientStream.correlated(other, seven)
    assertEquals(eight.next(), Left(ClientError.ResponseIdMismatch))
  }

  test("subscription-stream methods are rejected on ordinary requests") {
    List(
      SubscriptionMethods.acknowledgedNotification,
      ToolMethods.listChangedNotification,
      PromptMethods.listChangedNotification,
      ResourceMethods.listChangedNotification,
      ResourceMethods.updatedNotification
    ).foreach { method =>
      val n = notification(method, subscriptionId = Some(NumberRequestId(7)))
      val stream =
        ClientStream.correlated(request, new FixtureStream(List(Right(Some(n)))))
      assertEquals(
        stream.next(),
        Left(ClientError.TransportFailure),
        s"${method.value} must not be delivered on an ordinary stream"
      )
    }
  }

  test("a source close IOException surfaces as a static failure") {
    val source = new ClientStream {
      def next() = Right(None)
      def close() = throw new IOException("socket detail")
    }
    val stream = ClientStream.correlated(request, source)
    val thrown = intercept[IOException](stream.close())
    assertEquals(thrown.getMessage, "MCP stream close failure")
    assertEquals(thrown.getCause, null)
  }

  private def listenRequest(
      subscriptionId: NumberRequestId = NumberRequestId(7)
  ): McpRequest =
    request.copy(method = SubscriptionMethods.listen, id = subscriptionId)

  private def subscriptionStream(
      requested: SubscriptionFilter,
      items: List[Either[ClientError, Option[McpMessage]]]
  ): ClientStream =
    ClientStream.subscription(listenRequest(), requested, new FixtureStream(items))

  test("listen requires the acknowledged notification first") {
    val stream = subscriptionStream(
      SubscriptionFilter(toolsListChanged = Some(true)),
      List(
        Right(Some(notification(ToolMethods.listChangedNotification, Some(NumberRequestId(7)))))
      )
    )
    assertEquals(stream.next(), Left(ClientError.TransportFailure))
  }

  test("listen notifications must carry the request subscription id") {
    val stream = subscriptionStream(
      SubscriptionFilter(toolsListChanged = Some(true)),
      List(
        Right(Some(
          acknowledged(
            Some(NumberRequestId(9)),
            JsonObject(Map("toolsListChanged" -> JsonBool(true)))
          )
        ))
      )
    )
    assertEquals(stream.next(), Left(ClientError.TransportFailure))
  }

  test("a valid acknowledgement then only acknowledged categories pass") {
    val requested = SubscriptionFilter(
      toolsListChanged = Some(true),
      resourceSubscriptions = Some(List("file:///a"))
    )
    val ack = acknowledged(
      Some(NumberRequestId(7)),
      JsonObject(
        Map(
          "toolsListChanged" -> JsonBool(true),
          "resourceSubscriptions" -> JsonArray(List(JsonString("file:///a")))
        )
      )
    )
    val toolsChanged =
      notification(ToolMethods.listChangedNotification, Some(NumberRequestId(7)))
    val promptsChanged =
      notification(PromptMethods.listChangedNotification, Some(NumberRequestId(7)))
    val updated = notification(
      ResourceMethods.updatedNotification,
      Some(NumberRequestId(7)),
      JsonObject(Map("uri" -> JsonString("file:///a")))
    )
    val foreignUpdated = notification(
      ResourceMethods.updatedNotification,
      Some(NumberRequestId(7)),
      JsonObject(Map("uri" -> JsonString("file:///b")))
    )

    val good = subscriptionStream(
      requested,
      List(
        Right(Some(ack)),
        Right(Some(toolsChanged)),
        Right(Some(updated))
      )
    )
    assertEquals(good.next(), Right(Some(ack)))
    assertEquals(good.next(), Right(Some(toolsChanged)))
    assertEquals(good.next(), Right(Some(updated)))

    val unrequestedCategory = subscriptionStream(
      requested,
      List(Right(Some(ack)), Right(Some(promptsChanged)))
    )
    unrequestedCategory.next()
    assertEquals(
      unrequestedCategory.next(),
      Left(ClientError.TransportFailure)
    )

    val unrequestedUri = subscriptionStream(
      requested,
      List(Right(Some(ack)), Right(Some(foreignUpdated)))
    )
    unrequestedUri.next()
    assertEquals(unrequestedUri.next(), Left(ClientError.TransportFailure))
  }

  test("an acknowledgement granting more than requested fails") {
    val stream = subscriptionStream(
      SubscriptionFilter(toolsListChanged = Some(true)),
      List(
        Right(Some(
          acknowledged(
            Some(NumberRequestId(7)),
            JsonObject(
              Map(
                "promptsListChanged" -> JsonBool(true)
              )
            )
          )
        ))
      )
    )
    assertEquals(stream.next(), Left(ClientError.TransportFailure))
  }

  test("a repeated acknowledgement and progress notifications fail") {
    val requested = SubscriptionFilter(toolsListChanged = Some(true))
    val ack = acknowledged(
      Some(NumberRequestId(7)),
      JsonObject(
        Map(
          "toolsListChanged" -> JsonBool(true)
        )
      )
    )
    val repeated = subscriptionStream(
      requested,
      List(Right(Some(ack)), Right(Some(ack)))
    )
    repeated.next()
    assertEquals(repeated.next(), Left(ClientError.TransportFailure))

    val progress = notification(
      NotificationMethods.progress,
      Some(NumberRequestId(7)),
      JsonObject(
        Map(
          "progressToken" -> JsonString("t"),
          "progress" -> JsonNumber(BigDecimal(1))
        )
      )
    )
    val progressed = subscriptionStream(
      requested,
      List(Right(Some(ack)), Right(Some(progress)))
    )
    progressed.next()
    assertEquals(progressed.next(), Left(ClientError.TransportFailure))
  }

  test("a correlated error response may terminate before acknowledgement") {
    val remote = ApplicationError(code = -1, message = "rejected")
    val error = McpErrorResponse(remote, Some(NumberRequestId(7)))
    val stream = subscriptionStream(
      SubscriptionFilter(toolsListChanged = Some(true)),
      List(Right(Some(error)))
    )
    assertEquals(stream.next(), Right(Some(error)))
    assertEquals(stream.next(), Right(None))
  }

  test("a terminal success must be a valid listen result after the ack") {
    val requested = SubscriptionFilter(toolsListChanged = Some(true))
    val ack = acknowledged(
      Some(NumberRequestId(7)),
      JsonObject(
        Map("toolsListChanged" -> JsonBool(true))
      )
    )

    val plain = subscriptionStream(
      requested,
      List(Right(Some(ack)), Right(Some(McpSuccessResponse(Result.empty(), NumberRequestId(7)))))
    )
    plain.next()
    assertEquals(plain.next(), Left(ClientError.TransportFailure))

    val listenResult = Result(
      resultType = CompleteResultType,
      meta = Some(
        ResultMeta(
          extensions = MetaObject(
            Map(
              SubscriptionsListenResultMeta.SubscriptionIdKey ->
                JsonNumber(BigDecimal(7))
            )
          )
        )
      )
    )
    val closed = subscriptionStream(
      requested,
      List(
        Right(Some(ack)),
        Right(Some(McpSuccessResponse(listenResult, NumberRequestId(7))))
      )
    )
    closed.next()
    assert(closed.next().isRight, "expected the valid listen result")
    assertEquals(closed.next(), Right(None))
  }
}
