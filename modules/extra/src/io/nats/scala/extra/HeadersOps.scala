package io.nats.scala.extra

import io.nats.scala.core.HeaderName
import io.nats.scala.core.HeaderValue
import io.nats.scala.core.Headers

import scala.util.Try

object HeadersOps {

  private[nats] val NATS_HEADER_ERROR: HeaderName = "x-nats-scala-error"

  trait ToHeadersOps {
    implicit def toHeadersOps(headers0: Headers): Ops = new Ops {
      override val headers: Headers = headers0
    }
  }

  trait Ops {
    def headers: Headers

    def withError(code: Int): Headers =
      headers.put(NATS_HEADER_ERROR, HeaderValue.assume(code.toString))

    def withError(code: Int, text: HeaderValue): Headers =
      headers.put(NATS_HEADER_ERROR, HeaderValue.assume(code.toString), text)

    def hasError: Boolean =
      headers.contains(NATS_HEADER_ERROR)

    def getErrorCode: Option[Int] =
      headers
        .getFirst(NATS_HEADER_ERROR)
        .flatMap(value => Try(value.value.toInt).toOption)

    def getErrorText: Option[HeaderValue] =
      headers.getLast(NATS_HEADER_ERROR)
  }

}
