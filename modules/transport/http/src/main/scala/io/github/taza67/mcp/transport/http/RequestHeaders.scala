package io.github.taza67.mcp.transport.http

import io.github.taza67.mcp.codec.DecodingError
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.jsonrpc.HeaderMismatchError
import io.github.taza67.mcp.protocol.jsonrpc.Message
import io.github.taza67.mcp.protocol.jsonrpc.Method
import io.github.taza67.mcp.protocol.mcp.McpRequest
import io.github.taza67.mcp.protocol.mcp.RequestMeta
import io.github.taza67.mcp.protocol.mcp.RequestParams
import io.github.taza67.mcp.protocol.mcp.prompts.GetPromptRequestParams
import io.github.taza67.mcp.protocol.mcp.prompts.Prompts
import io.github.taza67.mcp.protocol.mcp.resources.ReadResourceRequestParams
import io.github.taza67.mcp.protocol.mcp.resources.Resources
import io.github.taza67.mcp.protocol.mcp.tools.CallToolRequestParams
import io.github.taza67.mcp.protocol.mcp.tools.Tools



/** Standard MCP request headers for streamable-HTTP requests.
 *
 *  [[fromRequest]] projects a typed request to its header map; [[validate]]
 *  checks a raw request body against received headers before typed MCP decode,
 *  so an unknown protocol version still matches its header and the later
 *  version policy owns the rejection. `Mcp-Name` is emitted only for
 *  `tools/call` and `prompts/get` (`params.name`) and `resources/read`
 *  (`params.uri`); its value goes through [[HeaderValues]] encoding.
 */
object RequestHeaders {

  val ProtocolVersion: String = "MCP-Protocol-Version"
  val Method: String = "Mcp-Method"
  val Name: String = "Mcp-Name"

  private def invalid: DecodingError = DecodingError("Invalid MCP request headers")
  private val mismatch: HeaderMismatchError =
    HeaderMismatchError("HTTP header mismatch")

  private def nameKey(method: Method): Option[String] = nameKeyOf(method.value)

  private def nameKeyOf(method: String): Option[String] =
    if (method == Tools.call.value) Some(CallToolRequestParams.NameKey)
    else if (method == Prompts.get.value) Some(GetPromptRequestParams.NameKey)
    else if (method == Resources.read.value) Some(ReadResourceRequestParams.UriKey)
    else None

  private def isPlainFieldSafe(value: String): Boolean =
    value.nonEmpty &&
      value.charAt(0) != ' ' && value.charAt(0) != '\t' &&
      value.charAt(value.length - 1) != ' ' &&
      value.charAt(value.length - 1) != '\t' &&
      value.forall(c => (c >= 0x21 && c <= 0x7e) || c == ' ' || c == '\t')

  private def requiredString(
      fields: JsonObject,
      key: String
  ): Either[DecodingError, String] =
    fields.value.get(key) match {
      case Some(JsonString(value)) => Right(value)
      case _                       => Left(invalid)
    }

  /** Projects a typed request to its standard MCP headers. */
  def fromRequest(
      request: McpRequest
  ): Either[DecodingError, Map[String, String]] =
    for {
      params <- request.params.toRight(invalid)
      version <- Right(params.meta.protocolVersion.value)
        .filterOrElse(isPlainFieldSafe, invalid)
      method <- Right(request.method.value)
        .filterOrElse(isPlainFieldSafe, invalid)
      name <- nameKey(request.method) match {
        case Some(key) =>
          requiredString(params.fields, key)
            .flatMap(HeaderValues.encode(_).left.map(_ => invalid))
            .map(Some(_))
        case None => Right(None)
      }
    } yield name match {
      case Some(value) =>
        Map(ProtocolVersion -> version, Method -> method, Name -> value)
      case None =>
        Map(ProtocolVersion -> version, Method -> method)
    }

  /** Header values for `name` across case variants; exactly one is required. */
  private def singleHeader(
      name: String,
      headers: Map[String, List[String]]
  ): Either[HeaderMismatchError, String] =
    headers.collect { case (key, values) if key.equalsIgnoreCase(name) => values }
      .flatten
      .toList match {
      case value :: Nil => Right(value)
      case _            => Left(mismatch)
    }

  private def requiredField(
      fields: JsonObject,
      key: String
  ): Either[HeaderMismatchError, JsonString] =
    fields.value.get(key) match {
      case Some(value: JsonString) => Right(value)
      case _                       => Left(mismatch)
    }

  /** Checks raw request body fields against received headers.
   *
   *  Header names match case-insensitively and every recognized header must
   *  carry exactly one value; values compare case-sensitively. Unknown headers
   *  are ignored.
   */
  def validate(
      body: JsonObject,
      headers: Map[String, List[String]]
  ): Either[HeaderMismatchError, Unit] =
    for {
      methodJson <- requiredField(body, Message.MethodKey)
      params <- body.value.get(Message.ParamsKey) match {
        case Some(value: JsonObject) => Right(value)
        case _                       => Left(mismatch)
      }
      meta <- params.value.get(RequestParams.MetaKey) match {
        case Some(value: JsonObject) => Right(value)
        case _                       => Left(mismatch)
      }
      versionJson <- requiredField(meta, RequestMeta.ProtocolVersionKey)
      versionHeader <- singleHeader(ProtocolVersion, headers)
      _ <- Either.cond(
        isPlainFieldSafe(versionHeader) && versionHeader == versionJson.value,
        (),
        mismatch
      )
      methodHeader <- singleHeader(Method, headers)
      _ <- Either.cond(
        isPlainFieldSafe(methodHeader) && methodHeader == methodJson.value,
        (),
        mismatch
      )
      _ <- nameKeyOf(methodJson.value) match {
        case Some(key) =>
          for {
            nameJson <- requiredField(params, key)
            nameHeader <- singleHeader(Name, headers)
            decoded <- HeaderValues.decode(nameHeader).left.map(_ => mismatch)
            _ <- Either.cond(decoded == nameJson.value, (), mismatch)
          } yield ()
        case None => Right(())
      }
    } yield ()
}
