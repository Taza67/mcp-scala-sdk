package io.github.taza67.mcp.server

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

import io.github.taza67.mcp.codec.mcp.{Messages => McpMessages}
import io.github.taza67.mcp.codec.mcp.subscriptions.{Subscriptions => SubscriptionsCodec}
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.jsonrpc.Method
import io.github.taza67.mcp.protocol.jsonrpc.RequestId
import io.github.taza67.mcp.protocol.jsonrpc.SuccessResponse
import io.github.taza67.mcp.protocol.mcp.CompleteResultType
import io.github.taza67.mcp.protocol.mcp.Implementation
import io.github.taza67.mcp.protocol.mcp.McpMessage
import io.github.taza67.mcp.protocol.mcp.McpNotification
import io.github.taza67.mcp.protocol.mcp.McpResponse
import io.github.taza67.mcp.protocol.mcp.McpSuccessResponse
import io.github.taza67.mcp.protocol.mcp.NotificationMeta
import io.github.taza67.mcp.protocol.mcp.NotificationParams
import io.github.taza67.mcp.protocol.mcp.prompts.{Prompts => PromptMethods}
import io.github.taza67.mcp.protocol.mcp.resources.{Resources => ResourceMethods}
import io.github.taza67.mcp.protocol.mcp.resources.ResourceUpdatedNotificationParams
import io.github.taza67.mcp.protocol.mcp.subscriptions.{Subscriptions => SubscriptionMethods}
import io.github.taza67.mcp.protocol.mcp.subscriptions.SubscriptionFilter
import io.github.taza67.mcp.protocol.mcp.subscriptions.SubscriptionsAcknowledgedNotificationParams
import io.github.taza67.mcp.protocol.mcp.subscriptions.SubscriptionsListenResult
import io.github.taza67.mcp.protocol.mcp.subscriptions.SubscriptionsListenResultMeta
import io.github.taza67.mcp.protocol.mcp.tools.{Tools => ToolMethods}



/** Fan-out hub for `subscriptions/listen` streams.
 *
 *  Each `open` grants the requested [[SubscriptionFilter]] intersected with
 *  `supported`, enqueues the literal acknowledgement notification first,
 *  then registers the stream for publication. Streams are registered by
 *  identity, never by request id, so two clients may reuse the same id.
 *
 *  Publication is bounded: every subscription queue reserves one slot for
 *  the terminal message and normal offers only proceed below
 *  `maxPendingNotifications`. A slow consumer is aborted (queue cleared,
 *  end marker) so clients observe an end of stream rather than a silent
 *  drop or an unbounded backlog.
 *
 *  `close()` gracefully terminates all registered subscriptions with a
 *  `subscriptions/listen` completion result carrying its subscription id;
 *  queued messages are retained and the final response uses the reserved
 *  queue slot. Idempotent: later `open` calls fail fast and publishes
 *  become no-ops. Callers should advertise `tools.listChanged` in
 *  discovery only when `supported.toolsListChanged` is granted (see
 *  [[McpServer]]'s `toolsListChanged` flag).
 *
 *  @param supported notification types this process will publish.
 *  @param maxPendingNotifications per-stream backlog bound before abort.
 *  @param serverInfo optional identity echoed in the teardown result meta.
 */
final class SubscriptionHub(
    val supported: SubscriptionFilter,
    maxPendingNotifications: Int = 256,
    serverInfo: Option[Implementation] = None
) extends AutoCloseable {

  require(
    maxPendingNotifications > 0 && maxPendingNotifications < Int.MaxValue - 1,
    "maxPendingNotifications must be positive and less than Int.MaxValue - 1"
  )

  private val lock = new Object
  private var subscriptions = Set.empty[Subscription]
  private var shutdown = false

  /** Opens a subscription stream for `id`, granting the intersection of
   *  `requested` and `supported`.
   *
   *  The acknowledgement notification is enqueued before the stream is
   *  registered, so a concurrent publish can never overtake it.
   *
   *  @throws IllegalStateException if the hub is closed.
   */
  def open(id: RequestId, requested: SubscriptionFilter): ServerStream = {
    val subscription = new Subscription(id, grant(requested))
    lock.synchronized {
      if (shutdown)
        throw new IllegalStateException("SubscriptionHub is closed")
      if (!subscription.enqueueRaw(acknowledgement(subscription)))
        throw new IllegalStateException("Subscription queue saturated")
      subscriptions += subscription
    }
    subscription
  }

  /** Publishes `notifications/tools/list_changed` to opted-in streams. */
  def publishToolsListChanged(): Unit =
    publish(
      ToolMethods.listChangedNotification,
      JsonObject(Map.empty),
      _.granted.toolsListChanged.contains(true)
    )

  /** Publishes `notifications/prompts/list_changed` to opted-in streams. */
  def publishPromptsListChanged(): Unit =
    publish(
      PromptMethods.listChangedNotification,
      JsonObject(Map.empty),
      _.granted.promptsListChanged.contains(true)
    )

  /** Publishes `notifications/resources/list_changed` to opted-in streams. */
  def publishResourcesListChanged(): Unit =
    publish(
      ResourceMethods.listChangedNotification,
      JsonObject(Map.empty),
      _.granted.resourcesListChanged.contains(true)
    )

  /** Publishes `notifications/resources/updated` for `uri` to streams that
   *  listed `uri` in their granted resource subscriptions.
   */
  def publishResourceUpdated(uri: String): Unit =
    publish(
      ResourceMethods.updatedNotification,
      JsonObject(
        Map(ResourceUpdatedNotificationParams.UriKey -> JsonString(uri))
      ),
      subscription =>
        subscription.granted.resourceSubscriptions
          .getOrElse(Nil)
          .contains(uri)
    )

  /** Gracefully ends every registered stream with a terminal
   *  `subscriptions/listen` completion result carrying its subscription id.
   */
  override def close(): Unit =
    lock.synchronized {
      if (!shutdown) {
        shutdown = true
        try subscriptions.foreach(_.terminate())
        finally subscriptions = Set.empty
      }
    }

  private def grant(requested: SubscriptionFilter): SubscriptionFilter =
    SubscriptionFilter(
      toolsListChanged =
        grantFlag(requested.toolsListChanged, supported.toolsListChanged),
      promptsListChanged =
        grantFlag(requested.promptsListChanged, supported.promptsListChanged),
      resourcesListChanged =
        grantFlag(requested.resourcesListChanged, supported.resourcesListChanged),
      resourceSubscriptions =
        requested.resourceSubscriptions.flatMap { uris =>
          val supportedUris =
            supported.resourceSubscriptions.getOrElse(Nil).toSet
          // An empty supported set means the category is unsupported:
          // omit it rather than granting an empty list.
          if (supportedUris.isEmpty) None
          else Some(uris.filter(supportedUris.contains))
        }
    )

  private def grantFlag(
      requested: Option[Boolean],
      allowed: Option[Boolean]
  ): Option[Boolean] =
    if (requested.contains(true) && allowed.contains(true)) Some(true)
    else None

  private def acknowledgement(subscription: Subscription): McpNotification =
    McpNotification(
      method = SubscriptionMethods.acknowledgedNotification,
      params = Some(
        SubscriptionsCodec.fromSubscriptionsAcknowledgedNotificationParams(
          SubscriptionsAcknowledgedNotificationParams(
            notifications = subscription.granted,
            meta = Some(
              NotificationMeta(subscriptionId = Some(subscription.id))
            )
          )
        )
      )
    )

  private def publish(
      method: Method,
      fields: JsonObject,
      eligible: Subscription => Boolean
  ): Unit =
    lock.synchronized {
      if (!shutdown)
        subscriptions.foreach { subscription =>
          if (eligible(subscription) && !subscription.offerNotification(method, fields))
            subscriptions -= subscription
        }
    }

  private def remove(subscription: Subscription): Unit =
    lock.synchronized {
      subscriptions -= subscription
      subscription.abort()
    }

  /** Builds the terminal `subscriptions/listen` completion response through
   *  the codec facade (no hand-written subscription-id fields). A codec
   *  rejection here would mean the hub projected an invalid wire result
   *  itself, so it fails fast instead of silently degrading to an abort.
   */
  private def terminalResponse(id: RequestId): McpSuccessResponse =
    McpMessages
      .toSuccessResponse(
        SuccessResponse(
          result = SubscriptionsCodec.fromSubscriptionsListenResult(
            SubscriptionsListenResult(
              meta = SubscriptionsListenResultMeta(
                subscriptionId = id,
                serverInfo = serverInfo
              ),
              resultType = CompleteResultType
            )
          ),
          id = id
        )
      ) match {
      case Right(response) => response
      case Left(_) =>
        throw new IllegalStateException(
          "Invalid subscriptions/listen terminal result"
        )
    }

  /** One registered subscription: identity-based, backed by a bounded
   *  queue with one reserved terminal slot.
   */
  private final class Subscription(
      val id: RequestId,
      val granted: SubscriptionFilter
  ) extends ServerStream {

    private val queue =
      new ArrayBlockingQueue[SubscriptionHub.QueueItem](
        maxPendingNotifications + 1
      )
    private val ended = new AtomicBoolean(false)
    private val aborted = new AtomicBoolean(false)

    def next(): Option[McpMessage] =
      if (ended.get) None
      else {
        val item =
          try queue.take()
          catch {
            case interrupted: InterruptedException =>
              if (ended.get || aborted.get) return None else throw interrupted
          }
        // Cancellation may win after take() returns: late messages are never
        // delivered once the stream was aborted.
        if (aborted.get) {
          ended.set(true)
          None
        } else
          item match {
            case SubscriptionHub.EndMarker =>
              ended.set(true)
              None
            case SubscriptionHub.QueuedMessage(response: McpResponse) =>
              ended.set(true)
              Some(response)
            case SubscriptionHub.QueuedMessage(message) => Some(message)
          }
      }

    /** Abrupt cancellation: unregisters, clears the backlog, and unblocks
     *  the consumer with the end marker. No terminal response is sent.
     */
    def close(): Unit = remove(this)

    /** Unconditional enqueue (acknowledgement, end marker). Hub-lock only. */
    def enqueueRaw(message: McpMessage): Boolean =
      queue.offer(SubscriptionHub.QueuedMessage(message))

    /** Normal notification offer under the hub lock: at most
     *  `maxPendingNotifications` pending; overflow aborts the stream and
     *  returns false so the hub unregisters it.
     */
    def offerNotification(method: Method, fields: JsonObject): Boolean =
      if (queue.size() < maxPendingNotifications) {
        enqueueRaw(
          McpNotification(
            method = method,
            params = Some(
              NotificationParams(
                meta = Some(NotificationMeta(subscriptionId = Some(id))),
                fields = fields
              )
            )
          )
        )
      } else {
        abort()
        false
      }

    /** Graceful teardown under the hub lock: terminal response in the
     *  reserved slot, or an abrupt end when it cannot be enqueued.
     */
    def terminate(): Unit =
      if (!enqueueRaw(terminalResponse(id))) abort()

    /** Clears the backlog and appends the end marker (abrupt end). Hub-lock
     *  only; safe to call while the consumer is blocked on `take`. Marks the
     *  stream aborted first so a take() that already dequeued an item still
     *  ends without delivering it.
     */
    def abort(): Unit = {
      aborted.set(true)
      queue.clear()
      val _ = queue.offer(SubscriptionHub.EndMarker)
    }
  }
}

object SubscriptionHub {

  /** Internal queue item: a message or the abrupt-end sentinel. */
  private sealed trait QueueItem

  /** One deliverable message (notification or terminal response). */
  private final case class QueuedMessage(message: McpMessage)
      extends QueueItem

  /** Queue sentinel released on abrupt end; never a response. */
  private case object EndMarker extends QueueItem
}
