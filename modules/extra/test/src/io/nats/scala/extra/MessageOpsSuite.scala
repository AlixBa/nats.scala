package io.nats.scala.extra

import cats.syntax.option.catsSyntaxOptionId
import cats.syntax.option.none
import io.nats.client.impl.Headers as JHeaders
import io.nats.client.impl.NatsMessage
import io.nats.scala.core.HeaderValue
import io.nats.scala.core.Message
import io.nats.scala.extra.HeadersOps.NATS_HEADER_ERROR
import io.nats.scala.extra.syntax.message.toMessageOps
import munit.FunSuite

class MessageOpsSuite extends FunSuite {
  val NATS_HEADER_ERROR_STR = NATS_HEADER_ERROR.value.toString
  def messageBuilder = NatsMessage.builder().subject("sub")

  test("hasError - false") {
    val message = Message.asScala(messageBuilder.build())
    assert(!message.hasError)
  }

  test("hasError - true") {
    val headers = new JHeaders()
    headers.put(NATS_HEADER_ERROR_STR, "400")
    val message = Message.asScala(messageBuilder.headers(headers).build())
    assert(message.hasError)
  }

  test("getErrorCode - none") {
    val message = Message.asScala(messageBuilder.build())
    assertEquals(message.getErrorCode, none)
  }

  test("getErrorCode - some") {
    val headers = new JHeaders()
    headers.put(NATS_HEADER_ERROR_STR, "400")
    val message = Message.asScala(messageBuilder.headers(headers).build())
    assertEquals(message.getErrorCode, 400.some)
  }

  test("getErrorText - none") {
    val message = Message.asScala(messageBuilder.build())
    assertEquals(message.getErrorText, none)
  }

  test("getErrorText - some") {
    val headers = new JHeaders()
    headers.put(NATS_HEADER_ERROR_STR, "400", "Bad Request")
    val message = Message.asScala(messageBuilder.headers(headers).build())
    assertEquals(
      message.getErrorText,
      ("Bad Request": HeaderValue).some
    )
  }

}
