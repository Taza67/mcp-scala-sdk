package io.github.taza67.mcp.codec.mcp.resources

import io.github.taza67.mcp.codec.DecodingError
import io.github.taza67.mcp.codec.Fields
import io.github.taza67.mcp.codec.Primitives
import io.github.taza67.mcp.codec.mcp.Content
import io.github.taza67.mcp.codec.mcp.ContinuationFields
import io.github.taza67.mcp.codec.mcp.Input
import io.github.taza67.mcp.codec.mcp.Meta
import io.github.taza67.mcp.codec.mcp.PlainRequests
import io.github.taza67.mcp.codec.mcp.lists.PaginatedListResults
import io.github.taza67.mcp.protocol.json.JsonNumber
import io.github.taza67.mcp.protocol.json.JsonObject
import io.github.taza67.mcp.protocol.json.JsonString
import io.github.taza67.mcp.protocol.json.JsonValue
import io.github.taza67.mcp.protocol.mcp.MetaObject
import io.github.taza67.mcp.protocol.mcp.Icon
import io.github.taza67.mcp.protocol.mcp.RequestOutcome
import io.github.taza67.mcp.protocol.mcp.RequestParams
import io.github.taza67.mcp.protocol.mcp.resources.{Resources => ResourceMethods}
import io.github.taza67.mcp.protocol.mcp.resources.ListResourceTemplatesResult
import io.github.taza67.mcp.protocol.mcp.resources.ListResourcesResult
import io.github.taza67.mcp.protocol.mcp.resources.ReadResourceRequest
import io.github.taza67.mcp.protocol.mcp.resources.ReadResourceRequestParams
import io.github.taza67.mcp.protocol.mcp.resources.ReadResourceResult
import io.github.taza67.mcp.protocol.mcp.resources.Resource
import io.github.taza67.mcp.protocol.mcp.resources.ResourceTemplate



/** Protocol AST bridge for MCP resources-domain types (`JsonObject` ↔ ADT). */
object Resources {

  def fromResource(resource: Resource): JsonObject = {
    val base = Map(
      Resource.NameKey -> JsonString(resource.name),
      Resource.UriKey -> JsonString(resource.uri)
    )
    JsonObject(
      Fields.withOptional(
        base,
        Resource.TitleKey -> resource.title.map(JsonString(_)),
        Resource.DescriptionKey -> resource.description.map(JsonString(_)),
        Resource.MimeTypeKey -> resource.mimeType.map(JsonString(_)),
        Resource.SizeKey -> resource.size.map(JsonNumber(_)),
        Resource.IconsKey -> resource.icons.map(Primitives.fromList(_)(Meta.fromIcon)),
        Resource.AnnotationsKey -> resource.annotations.map(Content.fromAnnotations),
        Resource.MetaKey -> resource.meta.map(m => JsonObject(m.value))
      )
    )
  }

  def fromResourceTemplate(resourceTemplate: ResourceTemplate): JsonObject = {
    val base = Map(
      ResourceTemplate.NameKey -> JsonString(resourceTemplate.name),
      ResourceTemplate.UriTemplateKey -> JsonString(resourceTemplate.uriTemplate)
    )
    JsonObject(
      Fields.withOptional(
        base,
        ResourceTemplate.TitleKey -> resourceTemplate.title.map(JsonString(_)),
        ResourceTemplate.DescriptionKey -> resourceTemplate.description.map(JsonString(_)),
        ResourceTemplate.MimeTypeKey -> resourceTemplate.mimeType.map(JsonString(_)),
        ResourceTemplate.IconsKey -> resourceTemplate.icons.map(Primitives.fromList(_)(Meta.fromIcon)),
        ResourceTemplate.AnnotationsKey ->
          resourceTemplate.annotations.map(Content.fromAnnotations),
        ResourceTemplate.MetaKey -> resourceTemplate.meta.map(m => JsonObject(m.value))
      )
    )
  }

  def fromReadResourceRequestParams(
      readResourceRequestParams: ReadResourceRequestParams
  ): RequestParams = {
    val base =
      Map(ReadResourceRequestParams.UriKey -> JsonString(readResourceRequestParams.uri))
    RequestParams(
      meta = readResourceRequestParams.meta,
      fields = JsonObject(
        Fields.withOptional(
          base,
          Input.fromContinuation(
            ContinuationFields(
              inputResponses = readResourceRequestParams.inputResponses,
              requestState = readResourceRequestParams.requestState
            )
          ): _*
        )
      )
    )
  }

  def fromReadResourceRequest(readResourceRequest: ReadResourceRequest): JsonObject =
    PlainRequests.fromRequest(
      ResourceMethods.read,
      readResourceRequest.id,
      fromReadResourceRequestParams(readResourceRequest.params),
      readResourceRequest.jsonrpc
    )

  def fromReadResourceResult(readResourceResult: ReadResourceResult): JsonObject = {
    val tail = PaginatedListResults.fromCacheableTail(
      PaginatedListResults.CacheableTail(
        readResourceResult.ttlMs,
        readResourceResult.cacheScope
      )
    )
    JsonObject(
      Map(
        ReadResourceResult.ContentsKey ->
          Primitives.fromList(readResourceResult.contents)(Content.fromResourceContents)
      ) ++ tail
    )
  }

  def fromListResourcesResult(listResourcesResult: ListResourcesResult): JsonObject = {
    val tail = PaginatedListResults.fromPaginatedTail(
      PaginatedListResults.PaginatedTail(
        PaginatedListResults.CacheableTail(
          listResourcesResult.ttlMs,
          listResourcesResult.cacheScope
        ),
        listResourcesResult.nextCursor
      )
    )
    JsonObject(
      Map(
        ListResourcesResult.ResourcesKey ->
          Primitives.fromList(listResourcesResult.resources)(fromResource)
      ) ++ tail
    )
  }

  def fromListResourceTemplatesResult(
      listResourceTemplatesResult: ListResourceTemplatesResult
  ): JsonObject = {
    val tail = PaginatedListResults.fromPaginatedTail(
      PaginatedListResults.PaginatedTail(
        PaginatedListResults.CacheableTail(
          listResourceTemplatesResult.ttlMs,
          listResourceTemplatesResult.cacheScope
        ),
        listResourceTemplatesResult.nextCursor
      )
    )
    JsonObject(
      Map(
        ListResourceTemplatesResult.ResourceTemplatesKey ->
          Primitives.fromList(listResourceTemplatesResult.resourceTemplates)(fromResourceTemplate)
      ) ++ tail
    )
  }

  def toResource(resource: JsonObject): Either[DecodingError, Resource] = {
    val fields = resource.value
    for {
      name <- Fields.requiredString(fields, Resource.NameKey)
      uri <- Fields.requiredString(fields, Resource.UriKey)
      title <- Fields.optionalString(fields, Resource.TitleKey)
      description <- Fields.optionalString(fields, Resource.DescriptionKey)
      mimeType <- Fields.optionalString(fields, Resource.MimeTypeKey)
      size <- Fields.optional(fields, Resource.SizeKey)(
        Primitives.asLong(_, Resource.SizeKey)
      )
      icons <- Fields.optionalList(fields, Resource.IconsKey) { v =>
        Fields.asObject(v, Icon.IconKey).flatMap(Meta.toIcon)
      }
      annotations <- Fields.optional(fields, Resource.AnnotationsKey)(v =>
        Fields.asObject(v, Resource.AnnotationsKey).flatMap(Content.toAnnotations)
      )
      meta <- Fields.optional(fields, Resource.MetaKey)(v =>
        Fields.asObject(v, Resource.MetaKey).map(obj => MetaObject(obj.value))
      )
    } yield Resource(
      name = name,
      uri = uri,
      title = title,
      description = description,
      mimeType = mimeType,
      size = size,
      icons = icons,
      annotations = annotations,
      meta = meta
    )
  }

  def toResourceTemplate(
      resourceTemplate: JsonObject
  ): Either[DecodingError, ResourceTemplate] = {
    val fields = resourceTemplate.value
    for {
      name <- Fields.requiredString(fields, ResourceTemplate.NameKey)
      uriTemplate <- Fields.requiredString(fields, ResourceTemplate.UriTemplateKey)
      title <- Fields.optionalString(fields, ResourceTemplate.TitleKey)
      description <- Fields.optionalString(fields, ResourceTemplate.DescriptionKey)
      mimeType <- Fields.optionalString(fields, ResourceTemplate.MimeTypeKey)
      icons <- Fields.optionalList(fields, ResourceTemplate.IconsKey) { v =>
        Fields.asObject(v, Icon.IconKey).flatMap(Meta.toIcon)
      }
      annotations <- Fields.optional(fields, ResourceTemplate.AnnotationsKey)(v =>
        Fields.asObject(v, ResourceTemplate.AnnotationsKey).flatMap(Content.toAnnotations)
      )
      meta <- Fields.optional(fields, ResourceTemplate.MetaKey)(v =>
        Fields.asObject(v, ResourceTemplate.MetaKey).map(obj => MetaObject(obj.value))
      )
    } yield ResourceTemplate(
      name = name,
      uriTemplate = uriTemplate,
      title = title,
      description = description,
      mimeType = mimeType,
      icons = icons,
      annotations = annotations,
      meta = meta
    )
  }

  def toReadResourceRequestParams(
      params: RequestParams
  ): Either[DecodingError, ReadResourceRequestParams] = {
    val fields = params.fields.value
    for {
      uri <- Fields.requiredString(fields, ReadResourceRequestParams.UriKey)
      continuation <- Input.toContinuation(fields)
    } yield ReadResourceRequestParams(
      meta = params.meta,
      uri = uri,
      inputResponses = continuation.inputResponses,
      requestState = continuation.requestState
    )
  }

  def toReadResourceRequest(message: JsonValue): Either[DecodingError, ReadResourceRequest] =
    PlainRequests.toRequest(ResourceMethods.read, message)(toReadResourceRequestParams) {
      (id, readResourceRequestParams, jsonrpc) =>
        ReadResourceRequest(id = id, params = readResourceRequestParams, jsonrpc = jsonrpc)
    }

  def toReadResourceResult(
      readResourceResult: JsonObject
  ): Either[DecodingError, ReadResourceResult] = {
    val fields = readResourceResult.value
    for {
      contents <- Fields
        .required(fields, ReadResourceResult.ContentsKey)
        .flatMap(
          Primitives.toList(_, ReadResourceResult.ContentsKey)(v =>
            Fields
              .asObject(v, ReadResourceResult.ContentsKey)
              .flatMap(Content.toResourceContents(_, ReadResourceResult.ContentsKey))
          )
        )
      tail <- PaginatedListResults.toCacheableTail(fields)
    } yield ReadResourceResult(
      contents = contents,
      ttlMs = tail.ttlMs,
      cacheScope = tail.cacheScope
    )
  }

  def toListResourcesResult(
      listResourcesResult: JsonObject
  ): Either[DecodingError, ListResourcesResult] = {
    val fields = listResourcesResult.value
    for {
      resources <- Fields
        .required(fields, ListResourcesResult.ResourcesKey)
        .flatMap(
          Primitives.toList(_, ListResourcesResult.ResourcesKey)(v =>
            Fields.asObject(v, ListResourcesResult.ResourcesKey).flatMap(toResource)
          )
        )
      tail <- PaginatedListResults.toPaginatedTail(fields)
    } yield ListResourcesResult(
      resources = resources,
      ttlMs = tail.cacheable.ttlMs,
      cacheScope = tail.cacheable.cacheScope,
      nextCursor = tail.nextCursor
    )
  }

  def toListResourceTemplatesResult(
      listResourceTemplatesResult: JsonObject
  ): Either[DecodingError, ListResourceTemplatesResult] = {
    val fields = listResourceTemplatesResult.value
    for {
      resourceTemplates <- Fields
        .required(fields, ListResourceTemplatesResult.ResourceTemplatesKey)
        .flatMap(
          Primitives.toList(_, ListResourceTemplatesResult.ResourceTemplatesKey)(v =>
            Fields
              .asObject(v, ListResourceTemplatesResult.ResourceTemplatesKey)
              .flatMap(toResourceTemplate)
          )
        )
      tail <- PaginatedListResults.toPaginatedTail(fields)
    } yield ListResourceTemplatesResult(
      resourceTemplates = resourceTemplates,
      ttlMs = tail.cacheable.ttlMs,
      cacheScope = tail.cacheable.cacheScope,
      nextCursor = tail.nextCursor
    )
  }

  def fromReadResourceOutcome(outcome: RequestOutcome[ReadResourceResult]): JsonObject =
    Input.fromRequestOutcome(outcome)(fromReadResourceResult)

  def toReadResourceOutcome(
      message: JsonObject
  ): Either[DecodingError, RequestOutcome[ReadResourceResult]] =
    Input.toRequestOutcome(message)(toReadResourceResult)
}
