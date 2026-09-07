package io.github.taza67.mcp.client

import io.github.taza67.mcp.codec.mcp.tools.{Tools => ToolsCodec}
import io.github.taza67.mcp.protocol.json.JsonArray
import io.github.taza67.mcp.protocol.json.JsonBool
import io.github.taza67.mcp.protocol.json.JsonNumber
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.jsonrpc.ApplicationError
import io.github.taza67.mcp.protocol.jsonrpc.Method
import io.github.taza67.mcp.protocol.jsonrpc.NumberRequestId
import io.github.taza67.mcp.protocol.mcp.ClientCapabilities
import io.github.taza67.mcp.protocol.mcp.CompleteResultType
import io.github.taza67.mcp.protocol.mcp.Completed
import io.github.taza67.mcp.protocol.mcp.Cursor
import io.github.taza67.mcp.protocol.mcp.CustomInputRequest
import io.github.taza67.mcp.protocol.mcp.CustomInputResponse
import io.github.taza67.mcp.protocol.mcp.CustomResultType
import io.github.taza67.mcp.protocol.mcp.Implementation
import io.github.taza67.mcp.protocol.mcp.InputRequired
import io.github.taza67.mcp.protocol.mcp.InputRequiredResult
import io.github.taza67.mcp.protocol.mcp.InputRequiredResultType
import io.github.taza67.mcp.protocol.mcp.InputRequests
import io.github.taza67.mcp.protocol.mcp.InputResponses
import io.github.taza67.mcp.protocol.mcp.McpErrorResponse
import io.github.taza67.mcp.protocol.mcp.McpProtocolVersion20260728
import io.github.taza67.mcp.protocol.mcp.McpRequest
import io.github.taza67.mcp.protocol.mcp.McpResponse
import io.github.taza67.mcp.protocol.mcp.McpSuccessResponse
import io.github.taza67.mcp.protocol.mcp.MetaObject
import io.github.taza67.mcp.protocol.mcp.PaginatedRequestParams
import io.github.taza67.mcp.protocol.mcp.PrivateCacheScope
import io.github.taza67.mcp.protocol.mcp.RequestMeta
import io.github.taza67.mcp.protocol.mcp.RequestParams
import io.github.taza67.mcp.protocol.mcp.Result
import io.github.taza67.mcp.protocol.mcp.ResultMeta
import io.github.taza67.mcp.protocol.mcp.ResultType
import io.github.taza67.mcp.protocol.mcp.TextContent
import io.github.taza67.mcp.protocol.mcp.tools.{Tools => ToolMethods}
import io.github.taza67.mcp.protocol.mcp.tools.CallToolRequestParams
import io.github.taza67.mcp.protocol.mcp.tools.CallToolResult
import io.github.taza67.mcp.protocol.mcp.tools.ListToolsResult
import io.github.taza67.mcp.protocol.mcp.tools.Tool
import io.github.taza67.mcp.protocol.mcp.tools.ToolInputSchema
import munit.FunSuite



class ToolsClientSuite extends FunSuite {

  private val requestMeta = RequestMeta(
    protocolVersion = McpProtocolVersion20260728,
    clientCapabilities = ClientCapabilities()
  )

  private final class FixedTransport(
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

  private val envelopeMeta = ResultMeta(
    serverInfo = Some(Implementation(name = "srv", version = "2")),
    extensions = MetaObject(Map("x-ext" -> JsonString("1")))
  )

  test("listTools decodes literal fields and forwards paginated params") {
    val fields = JsonObject(
      Map(
        "tools" -> JsonArray(
          List(
            JsonObject(
              Map(
                "name"        -> JsonString("ping"),
                "inputSchema" -> JsonObject(
                  Map(
                    "type"       -> JsonString("object"),
                    "properties" -> JsonObject(
                      Map("arg" -> JsonObject(Map("type" -> JsonString("string"))))
                    )
                  )
                )
              )
            )
          )
        ),
        "ttlMs"      -> JsonNumber(5000),
        "cacheScope" -> JsonString("private"),
        "nextCursor" -> JsonString("next-1")
      )
    )
    val transport = new FixedTransport(request =>
      Right(
        McpSuccessResponse(
          Result(
            resultType = CustomResultType("future-kind"),
            fields = fields,
            meta = Some(envelopeMeta)
          ),
          request.id
        )
      )
    )
    val client = McpClient(transport)

    assertEquals(
      client.listTools(requestMeta, cursor = Some(Cursor("c-1"))),
      Right(
        ListToolsResult(
          tools = List(
            Tool(
              name = "ping",
              inputSchema = ToolInputSchema(
                fields = JsonObject(
                  Map(
                    "properties" -> JsonObject(
                      Map("arg" -> JsonObject(Map("type" -> JsonString("string"))))
                    )
                  )
                )
              )
            )
          ),
          ttlMs = 5000L,
          cacheScope = PrivateCacheScope,
          nextCursor = Some(Cursor("next-1")),
          resultType = CustomResultType("future-kind"),
          meta = Some(envelopeMeta)
        )
      )
    )
    transport.lastRequest match {
      case Some(request) =>
        assertEquals(request.method, ToolMethods.list)
        assertEquals(request.id, NumberRequestId(1L))
        assertEquals(
          request.params,
          Some(PaginatedRequestParams(meta = requestMeta, cursor = Some(Cursor("c-1"))))
        )
      case None =>
        fail("transport saw no request")
    }
  }

  test("callTool preserves outbound params and decodes the literal result") {
    val supplied = CallToolRequestParams(
      meta = requestMeta.copy(
        extensions = MetaObject(Map("x-req" -> JsonString("7")))
      ),
      name = "echo",
      arguments = Some(JsonObject(Map("x" -> JsonString("y")))),
      inputResponses = Some(
        InputResponses(
          Map("q1" -> CustomInputResponse(JsonObject(Map("answer" -> JsonString("42")))))
        )
      ),
      requestState = Some("opaque-1")
    )
    val literalResult = Result(
      resultType = CompleteResultType,
      fields = JsonObject(
        Map(
          "content" -> JsonArray(
            List(
              JsonObject(Map("type" -> JsonString("text"), "text" -> JsonString("pong")))
            )
          ),
          "structuredContent" -> JsonObject(Map("ok" -> JsonBool(true))),
          "isError"           -> JsonBool(false)
        )
      ),
      meta = Some(envelopeMeta)
    )
    val transport = new FixedTransport(request =>
      Right(McpSuccessResponse(literalResult, request.id))
    )
    val client = McpClient(transport)

    assertEquals(
      client.callTool(supplied),
      Right(
        Completed(
          CallToolResult(
            content = List(TextContent(text = "pong")),
            structuredContent = Some(JsonObject(Map("ok" -> JsonBool(true)))),
            isError = Some(false),
            resultType = CompleteResultType,
            meta = Some(envelopeMeta)
          )
        )
      )
    )
    transport.lastRequest match {
      case Some(request) =>
        assertEquals(request.method, ToolMethods.call)
        request.params match {
          case Some(params: RequestParams) =>
            assertEquals(
              ToolsCodec.toCallToolRequestParams(params),
              Right(supplied)
            )
          case other =>
            fail(s"expected plain request params, got $other")
        }
      case None =>
        fail("transport saw no request")
    }
  }

  test("input-required outcomes return InputRequired once without retrying") {
    val transport = new FixedTransport(request =>
      Right(
        McpSuccessResponse(
          Result(
            resultType = InputRequiredResultType,
            fields = JsonObject(Map("requestState" -> JsonString("opaque-2"))),
            meta = Some(envelopeMeta)
          ),
          request.id
        )
      )
    )
    val client = McpClient(transport)

    assertEquals(
      client.callTool(
        CallToolRequestParams(meta = requestMeta, name = "needs-input")
      ),
      Right(
        InputRequired(
          InputRequiredResult(
            requestState = Some("opaque-2"),
            resultType = InputRequiredResultType,
            meta = Some(envelopeMeta)
          )
        )
      )
    )
    assertEquals(transport.calls, 1)
  }

  test("input-required results decode nested input requests with state and meta") {
    val transport = new FixedTransport(request =>
      Right(
        McpSuccessResponse(
          Result(
            resultType = InputRequiredResultType,
            fields = JsonObject(
              Map(
                "inputRequests" -> JsonObject(
                  Map(
                    "question1" -> JsonObject(
                      Map(
                        "method" -> JsonString("custom/question"),
                        "params" -> JsonObject(Map("topic" -> JsonString("safe")))
                      )
                    )
                  )
                ),
                "requestState" -> JsonString("st-9")
              )
            ),
            meta = Some(envelopeMeta)
          ),
          request.id
        )
      )
    )
    val client = McpClient(transport)

    assertEquals(
      client.callTool(CallToolRequestParams(meta = requestMeta, name = "needs-input")),
      Right(
        InputRequired(
          InputRequiredResult(
            inputRequests = Some(
              InputRequests(
                Map(
                  "question1" -> CustomInputRequest(
                    method = Method("custom/question"),
                    params = Some(JsonObject(Map("topic" -> JsonString("safe"))))
                  )
                )
              )
            ),
            requestState = Some("st-9"),
            resultType = InputRequiredResultType,
            meta = Some(envelopeMeta)
          )
        )
      )
    )
    assertEquals(transport.calls, 1)
  }

  test("malformed list, call, and input-required bodies fail as InvalidResult") {
    val secret = "secret-result-fragment"
    def clientWith(fields: JsonObject, resultType: ResultType) =
      McpClient(new FixedTransport(request =>
        Right(
          McpSuccessResponse(Result(resultType, fields), request.id)
        )
      ))

    val badList = clientWith(
      JsonObject(Map("tools" -> JsonString(secret))),
      CompleteResultType
    )
    assertEquals(
      badList.listTools(requestMeta),
      Left(ClientError.InvalidResult)
    )

    val badCall = clientWith(
      JsonObject(Map("content" -> JsonString(secret))),
      CompleteResultType
    )
    assertEquals(
      badCall.callTool(CallToolRequestParams(meta = requestMeta, name = "x")),
      Left(ClientError.InvalidResult)
    )

    val emptyInputRequired = clientWith(JsonObject(Map.empty), InputRequiredResultType)
    assertEquals(
      emptyInputRequired.callTool(CallToolRequestParams(meta = requestMeta, name = "x")),
      Left(ClientError.InvalidResult)
    )
  }

  test("remote errors pass through callTool unchanged") {
    val remote = ApplicationError(code = 9, message = "no")
    val transport = new FixedTransport(request =>
      Right(McpErrorResponse(remote, request.id))
    )
    val client = McpClient(transport)

    assertEquals(
      client.callTool(CallToolRequestParams(meta = requestMeta, name = "x")),
      Left(ClientError.RemoteError(remote))
    )
  }
}
