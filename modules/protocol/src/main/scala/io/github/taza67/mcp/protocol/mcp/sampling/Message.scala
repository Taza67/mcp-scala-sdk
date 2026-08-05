package io.github.taza67.mcp.protocol.mcp.sampling

import io.github.taza67.mcp.protocol.mcp.{MetaObject, Role, SamplingMessageContentBlock}



/** Message body for sampling: a single content block or a list (as on the wire). */
sealed trait SamplingMessageContent

/** Single content block (object form on the wire). */
case class SingleSamplingContent(
    block: SamplingMessageContentBlock
) extends SamplingMessageContent

/** Multiple content blocks (array form on the wire). */
case class MultiSamplingContent(
    blocks: List[SamplingMessageContentBlock]
) extends SamplingMessageContent

/** Message issued to or received from an LLM API during sampling.
 *
 *  @param role Sender role for this message.
 *  @param content One block or a list of blocks.
 *  @param meta Optional open metadata.
 *
 *  @deprecated Deprecated as of protocol version 2026-07-28 (SEP-2577). Remains for at least twelve months.
 */
case class SamplingMessage(
    role: Role,
    content: SamplingMessageContent,
    meta: Option[MetaObject] = None
)
