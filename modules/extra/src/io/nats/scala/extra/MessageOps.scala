package io.nats.scala.extra

import io.nats.scala.core.HeaderValue
import io.nats.scala.core.Message
import io.nats.scala.extra.syntax.headers.toHeadersOps

object MessageOps {

  trait ToMessageOps {
    implicit def toMessageOps(message0: Message): Ops = new Ops {
      override val message: Message = message0
    }
  }

  trait Ops {
    def message: Message

    def hasError: Boolean =
      message.headers.hasError

    def getErrorCode: Option[Int] =
      message.headers.getErrorCode

    def getErrorText: Option[HeaderValue] =
      message.headers.getErrorText
  }

}
