package io.github.taza67.mcp.transport.http



/** HTTP media and header helpers for [[HttpEndpoint]] preflight policy.
 *
 *  Header names match case-insensitively (JDK `HttpHeaders` style maps);
 *  values stay unnormalized. Only the rules the MCP streamable-HTTP contract
 *  needs are implemented: exact `application/json` Content-Type with optional
 *  utf-8 charset, and an Accept set explicitly containing `application/json`
 *  and `text/event-stream`.
 *
 *  Parameter syntax is strict: `;` and `,` inside quoted strings are not
 *  delimiters, every parameter is `name=value` with a token name and a token
 *  or well-formed quoted value, and `q` must match the HTTP qvalue grammar
 *  (`0[.000-999]` or `1[.0]` forms, in effect (0,1]).
 */
private[http] object HttpMedia {

  /** All values for `name` across case-insensitive header-name variants. */
  def headerValues(name: String, headers: Map[String, List[String]]): List[String] =
    headers.collect { case (key, values) if key.equalsIgnoreCase(name) => values }
      .flatten
      .toList

  /** Exactly one Content-Type of `application/json`; `charset` must be utf-8. */
  def contentTypeOk(headers: Map[String, List[String]]): Boolean =
    headerValues("content-type", headers) match {
      case value :: Nil =>
        parseMedia(value).exists { case (mediaType, params) =>
          mediaType.equalsIgnoreCase("application/json") && {
            params.filter(_._1.equalsIgnoreCase("charset")) match {
              case Nil                   => true
              case (_, charset, _) :: Nil => charset.equalsIgnoreCase("utf-8")
              case _                     => false
            }
          }
        }
      case _ => false
    }

  /** Accept must explicitly list `application/json` and `text/event-stream`. */
  def acceptOk(headers: Map[String, List[String]]): Boolean = {
    val values = headerValues("accept", headers)
    values.nonEmpty && {
      val mediaTypes =
        values.flatMap(v => splitQuoted(v, ',').getOrElse(Nil)).flatMap(parseAcceptItem)
      mediaTypes.exists(_.equalsIgnoreCase("application/json")) &&
        mediaTypes.exists(_.equalsIgnoreCase("text/event-stream"))
    }
  }

  private val QValue = "0(?:\\.[0-9]{0,3})?|1(?:\\.0{0,3})?".r

  /** `type;name=value;...` -> media type plus `(name, value, wasQuoted)` params. */
  private def parseMedia(value: String): Option[(String, List[(String, String, Boolean)])] =
    splitQuoted(value, ';') match {
      case Some(head :: rest) if head.trim.nonEmpty =>
        val mediaType = head.trim
        val params = rest.map(part => parseParam(part.trim))
        if (params.forall(_.isDefined)) Some((mediaType, params.flatten))
        else None
      case _ => None
    }

  /** Accept item: media type survives when `q` is absent or in (0,1]. */
  private def parseAcceptItem(item: String): Option[String] =
    parseMedia(item).flatMap { case (mediaType, params) =>
      params.filter(_._1.equalsIgnoreCase("q")) match {
        case Nil => Some(mediaType)
        case (_, q, quoted) :: Nil =>
          if (!quoted && QValue.matches(q) && q.toDouble > 0.0) Some(mediaType)
          else None
        case _ => None
      }
    }

  /** `name=value`: token name; token or well-formed quoted-string value. */
  private def parseParam(part: String): Option[(String, String, Boolean)] =
    part.split("=", 2) match {
      case Array(name, raw) =>
        val tokenName = name.trim
        val value = raw.trim
        if (tokenName.isEmpty || !tokenName.forall(isTokenChar)) None
        else if (value.nonEmpty && value.forall(isTokenChar))
          Some((tokenName, value, false))
        else unquoteStrict(value).map((tokenName, _, true))
      case _ => None
    }

  private def isTokenChar(c: Char): Boolean =
    (c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') ||
      "!#$%&'*+-.^_`|~".indexOf(c) >= 0

  /** Splits on `delimiter` outside double quotes; `\\` escapes inside quotes
   *  are preserved so `\"` never closes a string. `None` on unclosed quote.
   */
  private def splitQuoted(value: String, delimiter: Char): Option[List[String]] = {
    val parts = List.newBuilder[String]
    val current = new StringBuilder
    var inQuotes = false
    var escaped = false
    var i = 0
    while (i < value.length) {
      val c = value.charAt(i)
      if (escaped) {
        current.append(c)
        escaped = false
      } else if (inQuotes && c == '\\') {
        current.append(c)
        escaped = true
      } else if (c == '"') {
        inQuotes = !inQuotes
        current.append(c)
      } else if (c == delimiter && !inQuotes) {
        parts += current.toString
        current.clear()
      } else current.append(c)
      i += 1
    }
    if (inQuotes || escaped) None
    else {
      parts += current.toString
      Some(parts.result())
    }
  }

  /** `"..."` with `\x` escape pairs unescaped; rejects inner unescaped quotes
   *  and dangling backslashes.
   */
  private def unquoteStrict(value: String): Option[String] =
    if (value.length >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
      val inner = value.substring(1, value.length - 1)
      val out = new StringBuilder
      var ok = true
      var i = 0
      while (i < inner.length && ok) {
        inner.charAt(i) match {
          case '"' => ok = false
          case '\\' if i + 1 < inner.length =>
            out.append(inner.charAt(i + 1))
            i += 1
          case '\\' => ok = false
          case c    => out.append(c)
        }
        i += 1
      }
      if (ok) Some(out.toString) else None
    } else None
}
