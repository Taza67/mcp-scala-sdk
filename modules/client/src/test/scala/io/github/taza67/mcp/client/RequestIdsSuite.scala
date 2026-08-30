package io.github.taza67.mcp.client

import io.github.taza67.mcp.protocol.jsonrpc.ApplicationError
import io.github.taza67.mcp.protocol.jsonrpc.Method
import io.github.taza67.mcp.protocol.jsonrpc.NumberRequestId
import io.github.taza67.mcp.protocol.mcp.McpRequest
import munit.FunSuite



class RequestIdsSuite extends FunSuite {

  test("monotonic with the default initial yields 1 then 2") {
    val ids = RequestIds.monotonic()
    assertEquals(ids.next(), Right(NumberRequestId(1L)))
    assertEquals(ids.next(), Right(NumberRequestId(2L)))
  }

  test("monotonic honours a negative custom initial") {
    val ids = RequestIds.monotonic(initial = -3L)
    assertEquals(ids.next(), Right(NumberRequestId(-2L)))
    assertEquals(ids.next(), Right(NumberRequestId(-1L)))
  }

  test("initial Long.MaxValue is immediately exhausted and never wraps") {
    val ids = RequestIds.monotonic(initial = Long.MaxValue)
    assertEquals(ids.next(), Left(ClientError.RequestIdsExhausted))
    assertEquals(ids.next(), Left(ClientError.RequestIdsExhausted))
  }

  test("initial Long.MaxValue - 1 yields Long.MaxValue then stays exhausted") {
    val ids = RequestIds.monotonic(initial = Long.MaxValue - 1L)
    assertEquals(ids.next(), Right(NumberRequestId(Long.MaxValue)))
    assertEquals(ids.next(), Left(ClientError.RequestIdsExhausted))
    assertEquals(ids.next(), Left(ClientError.RequestIdsExhausted))
  }

  test("transport port exchanges a request and RemoteError preserves the error") {
    val failing: ClientTransport = _ => Left(ClientError.TransportFailure)
    val remote: ClientTransport = _ =>
      Left(ClientError.RemoteError(ApplicationError(code = 7, message = "nope")))
    val request = McpRequest(method = Method("example/ping"), id = NumberRequestId(1L))

    assertEquals(failing.exchange(request), Left(ClientError.TransportFailure))
    remote.exchange(request) match {
      case Left(ClientError.RemoteError(error)) =>
        assertEquals(error, ApplicationError(code = 7, message = "nope"))
      case other =>
        fail(s"expected RemoteError, got $other")
    }
  }
}
