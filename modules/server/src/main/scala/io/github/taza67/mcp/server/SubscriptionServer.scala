package io.github.taza67.mcp.server

import scala.util.control.NonFatal

import io.github.taza67.mcp.codec.mcp.subscriptions.{Subscriptions => SubscriptionsCodec}
import io.github.taza67.mcp.protocol.jsonrpc.Error
import io.github.taza67.mcp.protocol.jsonrpc.InternalError
import io.github.taza67.mcp.protocol.jsonrpc.InvalidParamsError
import io.github.taza67.mcp.protocol.mcp.McpRequest
import io.github.taza67.mcp.protocol.mcp.McpResponse
import io.github.taza67.mcp.protocol.mcp.RequestParams
import io.github.taza67.mcp.protocol.mcp.subscriptions.{Subscriptions => SubscriptionMethods}



/** [[StreamingServer]] adapter that adds `subscriptions/listen` handling in
 *  front of a synchronous [[Server]].
 *
 *  Ordinary methods open a lazy one-shot [[ServerStream]] backed by
 *  `delegate.handle` (the delegate runs on the first pull, not at `open`),
 *  while `handle` keeps delegating synchronously. `subscriptions/listen`
 *  opens a stream on `hub`; malformed or missing params return a static
 *  [[InvalidParamsError]]. There is no request-id registry: each listen
 *  request gets an independent subscription.
 */
final case class SubscriptionServer(
    delegate: Server,
    hub: SubscriptionHub
) extends StreamingServer {

  def handle(request: McpRequest): McpResponse = delegate.handle(request)

  def open(request: McpRequest): Either[Error, ServerStream] =
    try {
      if (request.method == SubscriptionMethods.listen)
        request.params match {
          case Some(params: RequestParams) =>
            SubscriptionsCodec.toSubscriptionsListenRequestParams(params) match {
              case Right(listen) =>
                Right(hub.open(request.id, listen.notifications))
              case Left(_) => Left(InvalidParamsError())
            }
          case _ => Left(InvalidParamsError())
        }
      else
        Right(ServerStream.single(delegate.handle(request)))
    } catch {
      case NonFatal(_) => Left(InternalError())
    }
}
