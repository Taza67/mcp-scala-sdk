package io.github.taza67.mcp.client

import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.jsonrpc.ApplicationError
import io.github.taza67.mcp.protocol.jsonrpc.JsonRpcVersion20
import io.github.taza67.mcp.protocol.jsonrpc.Method
import io.github.taza67.mcp.protocol.jsonrpc.NumberRequestId
import io.github.taza67.mcp.protocol.mcp.ClientCapabilities
import io.github.taza67.mcp.protocol.mcp.Cursor
import io.github.taza67.mcp.protocol.mcp.CustomResultType
import io.github.taza67.mcp.protocol.mcp.McpErrorResponse
import io.github.taza67.mcp.protocol.mcp.McpProtocolVersion20260728
import io.github.taza67.mcp.protocol.mcp.McpRequest
import io.github.taza67.mcp.protocol.mcp.McpRequestParams
import io.github.taza67.mcp.protocol.mcp.McpResponse
import io.github.taza67.mcp.protocol.mcp.McpSuccessResponse
import io.github.taza67.mcp.protocol.mcp.MetaObject
import io.github.taza67.mcp.protocol.mcp.PaginatedRequestParams
import io.github.taza67.mcp.protocol.mcp.RequestMeta
import io.github.taza67.mcp.protocol.mcp.RequestParams
import io.github.taza67.mcp.protocol.mcp.Result
import io.github.taza67.mcp.protocol.mcp.ResultMeta
import munit.FunSuite



class McpClientSuite extends FunSuite {

  private val requestMeta = RequestMeta(
    protocolVersion = McpProtocolVersion20260728,
    clientCapabilities = ClientCapabilities()
  )

  private val plainParams: McpRequestParams = RequestParams(meta = requestMeta)

  private val richResult = Result(
    resultType = CustomResultType("future-kind"),
    fields = JsonObject(Map("k" -> JsonString("v"))),
    meta = Some(
      ResultMeta(extensions = MetaObject(Map("x-ext" -> JsonString("1"))))
    )
  )

  private final class RecordingTransport(
      respond: McpRequest => Either[ClientError, McpResponse]
  ) extends ClientTransport {
    var calls: Int = 0
    var lastRequest: Option[McpRequest] = None

    def exchange(request: McpRequest): Either[ClientError, McpResponse] = {
      calls += 1
      lastRequest = Some(request)
      respond(request)
    }
  }

  test("request allocates the id and forwards method and params untouched") {
    val transport = new RecordingTransport(request =>
      Right(McpSuccessResponse(richResult, request.id))
    )
    val client = McpClient(transport)

    assertEquals(
      client.request(Method("example/ping"), plainParams),
      Right(richResult)
    )
    assertEquals(transport.calls, 1)
    transport.lastRequest match {
      case Some(request) =>
        assertEquals(request.method, Method("example/ping"))
        assertEquals(request.id, NumberRequestId(1L))
        assertEquals(request.params, Some(plainParams))
        assertEquals(request.jsonrpc, JsonRpcVersion20)
      case None =>
        fail("transport saw no request")
    }
  }

  test("request ids are allocated sequentially") {
    val ids = scala.collection.mutable.ListBuffer.empty[NumberRequestId]
    val transport = new RecordingTransport(request => {
      ids += request.id.asInstanceOf[NumberRequestId]
      Right(McpSuccessResponse(Result.empty(), request.id))
    })
    val client = McpClient(transport)

    client.request(Method("example/a"), plainParams)
    client.request(Method("example/b"), plainParams)
    assertEquals(
      ids.toList,
      List(NumberRequestId(1L), NumberRequestId(2L))
    )
  }

  test("paginated params are forwarded without rewrapping or dropping fields") {
    val paginated = PaginatedRequestParams(
      meta = requestMeta,
      cursor = Some(Cursor("c-1")),
      fields = JsonObject(Map("extra" -> JsonString("x")))
    )
    val transport = new RecordingTransport(request =>
      Right(McpSuccessResponse(Result.empty(), request.id))
    )
    val client = McpClient(transport)

    client.request(Method("example/list"), paginated)
    assertEquals(transport.lastRequest.flatMap(_.params), Some(paginated))
  }

  test("a success response with a mismatched id fails correlation") {
    val transport = new RecordingTransport(_ =>
      Right(McpSuccessResponse(Result.empty(), NumberRequestId(99L)))
    )
    val client = McpClient(transport)

    assertEquals(
      client.request(Method("example/ping"), plainParams),
      Left(ClientError.ResponseIdMismatch)
    )
  }

  test("correlated errors surface as RemoteError; other errors fail correlation") {
    val remote = ApplicationError(code = 7, message = "nope")
    val correlated = McpClient(new RecordingTransport(request =>
      Right(McpErrorResponse(remote, request.id))
    ))
    assertEquals(
      correlated.request(Method("example/ping"), plainParams),
      Left(ClientError.RemoteError(remote))
    )

    val mismatched = McpClient(new RecordingTransport(_ =>
      Right(McpErrorResponse(remote, NumberRequestId(99L)))
    ))
    assertEquals(
      mismatched.request(Method("example/ping"), plainParams),
      Left(ClientError.ResponseIdMismatch)
    )

    val uncorrelated = McpClient(new RecordingTransport(_ =>
      Right(McpErrorResponse(remote))
    ))
    assertEquals(
      uncorrelated.request(Method("example/ping"), plainParams),
      Left(ClientError.UncorrelatedResponse)
    )
  }

  test("transport Left results pass through unchanged") {
    val client = McpClient(new RecordingTransport(_ =>
      Left(ClientError.TransportFailure)
    ))
    assertEquals(
      client.request(Method("example/ping"), plainParams),
      Left(ClientError.TransportFailure)
    )
  }

  test("a throwing transport degrades to TransportFailure and the client survives") {
    var calls = 0
    val transport = new RecordingTransport(request => {
      calls += 1
      if (calls == 1) throw new RuntimeException("secret-crash-detail")
      else Right(McpSuccessResponse(richResult, request.id))
    })
    val client = McpClient(transport)

    assertEquals(
      client.request(Method("example/ping"), plainParams),
      Left(ClientError.TransportFailure)
    )
    assertEquals(
      client.request(Method("example/ping"), plainParams),
      Right(richResult)
    )
  }

  test("allocator exhaustion fails the request without calling the transport") {
    val transport = new RecordingTransport(request =>
      Right(McpSuccessResponse(Result.empty(), request.id))
    )
    val client = McpClient(
      transport,
      requestIds = RequestIds.monotonic(initial = Long.MaxValue)
    )

    assertEquals(
      client.request(Method("example/ping"), plainParams),
      Left(ClientError.RequestIdsExhausted)
    )
    assertEquals(transport.calls, 0)
  }

  test("interrupted and linkage failures from the transport propagate") {
    val interrupted = McpClient(new RecordingTransport(_ =>
      throw new InterruptedException("stop")
    ))
    var sawInterrupted = false
    try {
      interrupted.request(Method("example/ping"), plainParams)
      ()
    } catch {
      case _: InterruptedException => sawInterrupted = true
    }
    assert(sawInterrupted, "expected InterruptedException to propagate")

    val linkage = McpClient(new RecordingTransport(_ =>
      throw new LinkageError("bad link")
    ))
    var sawLinkage = false
    try {
      linkage.request(Method("example/ping"), plainParams)
      ()
    } catch {
      case _: LinkageError => sawLinkage = true
    }
    assert(sawLinkage, "expected LinkageError to propagate")
  }
}
