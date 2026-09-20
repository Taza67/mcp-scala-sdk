package io.github.taza67.mcp.client

import java.io.IOException

import scala.util.control.NonFatal

import io.github.taza67.mcp.codec.mcp.{Messages => McpMessages}
import io.github.taza67.mcp.codec.mcp.notifications.{Notifications => NotificationsCodec}
import io.github.taza67.mcp.codec.mcp.resources.{Resources => ResourcesCodec}
import io.github.taza67.mcp.codec.mcp.subscriptions.{Subscriptions => SubscriptionsCodec}
import io.github.taza67.mcp.protocol.jsonrpc.Method
import io.github.taza67.mcp.protocol.mcp.McpErrorResponse
import io.github.taza67.mcp.protocol.mcp.McpMessage
import io.github.taza67.mcp.protocol.mcp.McpNotification
import io.github.taza67.mcp.protocol.mcp.McpRequest
import io.github.taza67.mcp.protocol.mcp.McpResponse
import io.github.taza67.mcp.protocol.mcp.McpSuccessResponse
import io.github.taza67.mcp.protocol.mcp.notifications.{Notifications => NotificationMethods}
import io.github.taza67.mcp.protocol.mcp.prompts.{Prompts => PromptMethods}
import io.github.taza67.mcp.protocol.mcp.resources.{Resources => ResourceMethods}
import io.github.taza67.mcp.protocol.mcp.subscriptions.SubscriptionFilter
import io.github.taza67.mcp.protocol.mcp.subscriptions.{Subscriptions => SubscriptionMethods}
import io.github.taza67.mcp.protocol.mcp.tools.{Tools => ToolMethods}



/** Pull stream of inbound [[McpMessage]]s belonging to one client request.
 *
 *  `next()` yields each validated message as `Some(...)`: notifications in
 *  order, then the terminal response. Once the terminal response has been
 *  delivered (or the stream was closed), `next()` returns `Right(None)`.
 *  A single consumer pulls; `close()` may be invoked from another thread to
 *  unblock an in-flight `next()` and is idempotent.
 *
 *  [[ClientStream.correlated]] wrappers validate response ids against the
 *  request and close the source on terminal delivery or failure.
 */
trait ClientStream extends AutoCloseable {

  /** Returns the next validated message, `None` once the stream ended. */
  def next(): Either[ClientError, Option[McpMessage]]

  /** Closes the underlying channel; source `NonFatal` failures surface as a
   *  static `MCP stream close failure` `IOException` without the original
   *  detail.
   */
  override def close(): Unit
}

object ClientStream {

  /** Wraps `source` with request-response correlation: matching success ids
   *  and matching error ids are terminal, mismatched or id-less errors fail
   *  typed, requests never arrive, and notifications pass only ordinary
   *  validation (subscription id match, progress token, log-level opt-in,
   *  subscription-stream methods restricted to `subscriptions/listen`).
   *
   *  Idempotent: when `source` is already a correlated stream for the same
   *  request it is returned unchanged instead of double-wrapping.
   */
  def correlated(request: McpRequest, source: ClientStream): ClientStream =
    source match {
      case correlated: Correlated if correlated.request == request => source
      case _ => new Correlated(request, source)
    }

  /** Wraps `source` with `subscriptions/listen` semantics on top of
   *  correlation: the first notification must be a valid
   *  `notifications/subscriptions/acknowledged` whose granted filter is a
   *  subset of `requested`; afterwards only acknowledged list-changed and
   *  opted-in `resources/updated` notifications pass. A correlated error
   *  response may terminate the stream before the acknowledgement; a success
   *  must be a valid listen result carrying this request's subscription id.
   */
  def subscription(
      request: McpRequest,
      requested: SubscriptionFilter,
      source: ClientStream
  ): ClientStream =
    new Subscription(request, requested, correlated(request, source))

  private final class Correlated(val request: McpRequest, source: ClientStream)
      extends ClientStream {

    private val lock = new Object
    private var closed = false
    private var terminal = false

    def next(): Either[ClientError, Option[McpMessage]] =
      if (lock.synchronized { closed || terminal }) Right(None)
      else {
        // The pull and the message checks stay outside the state lock so a
        // cross-thread close() never waits on a blocked read. The boolean in
        // a Right marks the terminal response.
        val evaluated: Either[ClientError, (McpMessage, Boolean)] =
          try
            source.next() match {
              case Left(error) => Left(error)
              case Right(None) => Left(ClientError.TransportFailure)
              case Right(Some(response: McpResponse)) =>
                ClientResponses
                  .validateId(request.id, response)
                  .map(_ => (response: McpMessage, true))
              case Right(Some(notification: McpNotification)) =>
                validateNotification(notification)
                  .map(_ => (notification: McpMessage, false))
              case Right(Some(_)) => Left(ClientError.TransportFailure)
            }
          catch {
            case NonFatal(_) => Left(ClientError.TransportFailure)
            case fatal: Throwable =>
              closeSourceOnce()
              throw fatal
          }
        // An explicit close racing the pull or the evaluation wins
        // atomically under the state lock: late messages never surface and
        // the close winner owns source cleanup exactly once.
        val decision: Option[Either[ClientError, (McpMessage, Boolean)]] =
          lock.synchronized {
            if (closed) None
            else {
              evaluated.foreach { case (_, isTerminal) =>
                if (isTerminal) terminal = true
              }
              Some(evaluated)
            }
          }
        decision match {
          case None => Right(None)
          case Some(Left(error)) =>
            closeSourceOnce()
            Left(error)
          case Some(Right((message, isTerminal))) =>
            if (isTerminal)
              closeSourceOnce() match {
                case Left(closeError) => Left(closeError)
                case Right(())        => Right(Some(message))
              }
            else Right(Some(message))
        }
      }

    /** Marks `closed` exactly once; the winner runs `source.close()` outside
     *  the lock so a blocking `next()` is never closed under the state lock.
     */
    private def initiateClose(): Boolean =
      lock.synchronized {
        if (closed) false
        else {
          closed = true
          true
        }
      }

    /** Closes the source once for internal cleanup: a `NonFatal` failure
     *  becomes `TransportFailure`, fatal failures propagate.
     */
    private def closeSourceOnce(): Either[ClientError, Unit] =
      if (initiateClose())
        try {
          source.close()
          Right(())
        } catch { case NonFatal(_) => Left(ClientError.TransportFailure) }
      else Right(())

    /** Ordinary in-stream notification rules: a present `subscriptionId`
     *  must equal this request's id, `notifications/progress` must decode
     *  and carry the request's `progressToken`, `notifications/message` is
     *  only valid when the request opted into a `logLevel`, and the
     *  subscription-stream notification methods are only legal on a
     *  `subscriptions/listen` request (the subscription layer then enforces
     *  the ack/filter policy). Custom notifications pass through.
     */
    private def validateNotification(
        notification: McpNotification
    ): Either[ClientError, Unit] = {
      val subscriptionId =
        notification.params.flatMap(_.meta).flatMap(_.subscriptionId)
      if (subscriptionId.exists(_ != request.id))
        Left(ClientError.TransportFailure)
      else if (
        Correlated.ListenOnlyMethods.contains(notification.method) &&
        request.method != SubscriptionMethods.listen
      ) Left(ClientError.TransportFailure)
      else if (notification.method == NotificationMethods.progress)
        request.params.map(_.meta).flatMap(_.progressToken) match {
          case Some(token) =>
            NotificationsCodec.toProgressNotification(
              McpMessages.fromNotification(notification)
            ) match {
              case Right(decoded) if decoded.params.progressToken == token =>
                Right(())
              case _ => Left(ClientError.TransportFailure)
            }
          case None => Left(ClientError.TransportFailure)
        }
      else if (notification.method == NotificationMethods.message)
        request.params.map(_.meta).flatMap(_.logLevel) match {
          case Some(_) =>
            NotificationsCodec.toLoggingMessageNotification(
              McpMessages.fromNotification(notification)
            ) match {
              case Right(_) => Right(())
              case Left(_)  => Left(ClientError.TransportFailure)
            }
          case None => Left(ClientError.TransportFailure)
        }
      else Right(())
    }

    def close(): Unit =
      if (initiateClose())
        try source.close()
        catch {
          case NonFatal(_) =>
            throw new IOException("MCP stream close failure")
        }
  }

  private object Correlated {

    /** Notification methods only valid on a `subscriptions/listen` stream. */
    val ListenOnlyMethods: Set[Method] = Set(
      SubscriptionMethods.acknowledgedNotification,
      ToolMethods.listChangedNotification,
      PromptMethods.listChangedNotification,
      ResourceMethods.listChangedNotification,
      ResourceMethods.updatedNotification
    )
  }

  private final class Subscription(
      request: McpRequest,
      requested: SubscriptionFilter,
      inner: ClientStream
  ) extends ClientStream {

    private var accepted: Option[SubscriptionFilter] = None

    def next(): Either[ClientError, Option[McpMessage]] =
      inner.next() match {
        case Right(Some(message)) =>
          try check(message)
          catch {
            case NonFatal(_) => fail()
            case fatal: Throwable =>
              closeQuietly()
              throw fatal
          }
        case other => other
      }

    private def fail(): Either[ClientError, Option[McpMessage]] = {
      closeQuietly()
      Left(ClientError.TransportFailure)
    }

    private def check(
        message: McpMessage
    ): Either[ClientError, Option[McpMessage]] =
      message match {
        case notification: McpNotification =>
          val subscriptionId =
            notification.params.flatMap(_.meta).flatMap(_.subscriptionId)
          if (subscriptionId != Some(request.id)) fail()
          else
            accepted match {
              case None =>
                if (
                  notification.method !=
                    SubscriptionMethods.acknowledgedNotification
                ) fail()
                else
                  decodeAcknowledged(notification) match {
                    case Some(filter) if filterSubset(filter, requested) =>
                      accepted = Some(filter)
                      Right(Some(notification))
                    case _ => fail()
                  }
              case Some(filter) => checkSubscribed(notification, filter)
            }
        case success: McpSuccessResponse =>
          if (accepted.isEmpty) fail()
          else
            SubscriptionsCodec.toSubscriptionsListenResultResponse(
              McpMessages.fromSuccessResponse(success)
            ) match {
              case Right(_) => Right(Some(success))
              case Left(_)  => fail()
            }
        case _: McpErrorResponse => Right(Some(message))
        case _                   => fail()
      }

    private def checkSubscribed(
        notification: McpNotification,
        filter: SubscriptionFilter
    ): Either[ClientError, Option[McpMessage]] =
      if (
        notification.method == ToolMethods.listChangedNotification &&
        filter.toolsListChanged.contains(true)
      ) Right(Some(notification))
      else if (
        notification.method == PromptMethods.listChangedNotification &&
        filter.promptsListChanged.contains(true)
      ) Right(Some(notification))
      else if (
        notification.method == ResourceMethods.listChangedNotification &&
        filter.resourcesListChanged.contains(true)
      ) Right(Some(notification))
      else if (notification.method == ResourceMethods.updatedNotification)
        decodeUpdatedUri(notification) match {
          case Some(uri)
              if filter.resourceSubscriptions.getOrElse(Nil).contains(uri) =>
            Right(Some(notification))
          case _ => fail()
        }
      else fail()

    private def decodeAcknowledged(
        notification: McpNotification
    ): Option[SubscriptionFilter] =
      SubscriptionsCodec
        .toSubscriptionsAcknowledgedNotification(
          McpMessages.fromNotification(notification)
        )
        .toOption
        .map(_.params.notifications)

    private def decodeUpdatedUri(notification: McpNotification): Option[String] =
      ResourcesCodec
        .toResourceUpdatedNotification(McpMessages.fromNotification(notification))
        .toOption
        .map(_.params.uri)

    /** Every true flag and subscribed URI the server acknowledged must have
     *  been requested.
     */
    private def filterSubset(
        acceptedFilter: SubscriptionFilter,
        requestedFilter: SubscriptionFilter
    ): Boolean =
      (!acceptedFilter.toolsListChanged.contains(true) ||
        requestedFilter.toolsListChanged.contains(true)) &&
      (!acceptedFilter.promptsListChanged.contains(true) ||
        requestedFilter.promptsListChanged.contains(true)) &&
      (!acceptedFilter.resourcesListChanged.contains(true) ||
        requestedFilter.resourcesListChanged.contains(true)) &&
      acceptedFilter.resourceSubscriptions
        .getOrElse(Nil)
        .forall(uri =>
          requestedFilter.resourceSubscriptions.getOrElse(Nil).contains(uri)
        )

    private def closeQuietly(): Unit =
      try inner.close()
      catch { case NonFatal(_) => () }

    def close(): Unit = inner.close()
  }
}
