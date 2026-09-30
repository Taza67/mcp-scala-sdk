package io.github.taza67.mcp.server

import java.util.concurrent.atomic.AtomicInteger

import io.github.taza67.mcp.codec.mcp.subscriptions.{Subscriptions => SubscriptionsCodec}
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.jsonrpc.InternalError
import io.github.taza67.mcp.protocol.jsonrpc.InvalidParamsError
import io.github.taza67.mcp.protocol.jsonrpc.Method
import io.github.taza67.mcp.protocol.jsonrpc.MethodNotFoundError
import io.github.taza67.mcp.protocol.jsonrpc.StringRequestId
import io.github.taza67.mcp.protocol.mcp.McpErrorResponse
import io.github.taza67.mcp.protocol.mcp.ClientCapabilities
import io.github.taza67.mcp.protocol.mcp.McpNotification
import io.github.taza67.mcp.protocol.mcp.McpProtocolVersion20260728
import io.github.taza67.mcp.protocol.mcp.McpRequest
import io.github.taza67.mcp.protocol.mcp.McpSuccessResponse
import io.github.taza67.mcp.protocol.mcp.PaginatedRequestParams
import io.github.taza67.mcp.protocol.mcp.RequestMeta
import io.github.taza67.mcp.protocol.mcp.RequestParams
import io.github.taza67.mcp.protocol.mcp.Result
import io.github.taza67.mcp.protocol.mcp.subscriptions.{Subscriptions => SubscriptionMethods}
import io.github.taza67.mcp.protocol.mcp.subscriptions.SubscriptionFilter
import io.github.taza67.mcp.protocol.mcp.subscriptions.SubscriptionsListenRequestParams
import munit.FunSuite



class SubscriptionServerSuite extends FunSuite {

  private val id = StringRequestId("req-1")

  private val supported = SubscriptionFilter(
    toolsListChanged = Some(true)
  )

  private val meta = RequestMeta(
    protocolVersion = McpProtocolVersion20260728,
    clientCapabilities = ClientCapabilities()
  )

  private def listenRequest(params: Option[RequestParams]) =
    McpRequest(
      method = SubscriptionMethods.listen,
      id = id,
      params = params
    )

  private def listenParams(filter: SubscriptionFilter): RequestParams =
    SubscriptionsCodec.fromSubscriptionsListenRequestParams(
      SubscriptionsListenRequestParams(meta = meta, notifications = filter)
    )

  test("handle delegates ordinary requests synchronously") {
    val delegate =
      McpServer(HandlerRegistryInMemory(Map.empty))
    val hub = new SubscriptionHub(supported)
    try {
      val server = SubscriptionServer(delegate, hub)
      val request = McpRequest(method = Method("unknown"), id = id)
      assertEquals(
        server.handle(request),
        McpErrorResponse(error = MethodNotFoundError(), id = id)
      )
    } finally hub.close()
  }

  test("open returns a lazy single stream for ordinary requests") {
    val executions = new AtomicInteger(0)
    val delegate = new Server {
      def handle(request: McpRequest) = {
        executions.incrementAndGet()
        McpSuccessResponse(result = Result.empty(), id = request.id)
      }
    }
    val hub = new SubscriptionHub(supported)
    try {
      val server = SubscriptionServer(delegate, hub)
      val opened = server.open(McpRequest(method = Method("ping"), id = id))
      assert(opened.isRight)
      // Lazy: the delegate must not run while open is called.
      assertEquals(executions.get(), 0)
      opened.foreach { stream =>
        assertEquals(
          stream.next().map(_.asInstanceOf[McpSuccessResponse].id),
          Some(id)
        )
        assertEquals(executions.get(), 1)
        assertEquals(stream.next(), None)
      }
    } finally hub.close()
  }

  test("open rejects a listen request without valid params") {
    val delegate = McpServer(HandlerRegistryInMemory(Map.empty))
    val hub = new SubscriptionHub(supported)
    try {
      val server = SubscriptionServer(delegate, hub)
      assertEquals(
        server.open(listenRequest(None)),
        Left(InvalidParamsError())
      )
      // Paginated params are not valid listen params.
      assertEquals(
        server.open(
          McpRequest(
            method = SubscriptionMethods.listen,
            id = id,
            params = Some(PaginatedRequestParams(meta = meta))
          )
        ),
        Left(InvalidParamsError())
      )
      // Request params without the required notifications object.
      assertEquals(
        server.open(
          listenRequest(
            Some(
              RequestParams(
                meta = meta,
                fields = JsonObject(Map("x" -> JsonString("y")))
              )
            )
          )
        ),
        Left(InvalidParamsError())
      )
    } finally hub.close()
  }

  test("open opens a hub subscription for a valid listen request") {
    val delegate = McpServer(HandlerRegistryInMemory(Map.empty))
    val hub = new SubscriptionHub(supported)
    try {
      val server = SubscriptionServer(delegate, hub)
      server.open(
        listenRequest(Some(listenParams(SubscriptionFilter(toolsListChanged = Some(true)))))
      ) match {
        case Right(stream) =>
          // First message is the acknowledgement notification.
          stream.next() match {
            case Some(notification: McpNotification) =>
              assertEquals(
                notification.method,
                SubscriptionMethods.acknowledgedNotification
              )
            case other => fail(s"expected ack notification, got $other")
          }
          stream.close()
        case Left(error) => fail(s"expected a stream, got $error")
      }
    } finally hub.close()
  }

  test("open maps a closed hub to a static internal error") {
    val delegate = McpServer(HandlerRegistryInMemory(Map.empty))
    val hub = new SubscriptionHub(supported)
    hub.close()
    val server = SubscriptionServer(delegate, hub)
    assertEquals(
      server.open(
        listenRequest(Some(listenParams(SubscriptionFilter(toolsListChanged = Some(true)))))
      ),
      Left(InternalError())
    )
  }
}
