package io.nats.scala.extra

import cats.syntax.option.catsSyntaxOptionId
import cats.syntax.option.none
import io.nats.scala.core.HeaderValue
import io.nats.scala.core.Headers
import io.nats.scala.extra.HeadersOps.NATS_HEADER_ERROR
import io.nats.scala.extra.syntax.headers.toHeadersOps
import munit.FunSuite

class HeadersOpsSuite extends FunSuite {

  test("withError(code)") {
    assertEquals(
      Headers.empty.withError(400),
      Headers(Map(NATS_HEADER_ERROR -> List("400")))
    )
  }

  test("withError(code, text)") {
    assertEquals(
      Headers.empty.withError(400, "Bad Request"),
      Headers(Map(NATS_HEADER_ERROR -> List("400", "Bad Request")))
    )
  }

  test("hasError - false") {
    assert(!Headers.empty.hasError)
  }

  test("hasError - true") {
    assert(Headers.empty.withError(400).hasError)
  }

  test("getErrorCode - none") {
    assertEquals(Headers.empty.getErrorCode, none)
  }

  test("getErrorCode - some") {
    assertEquals(Headers.empty.withError(400).getErrorCode, 400.some)
  }

  test("getErrorText - none") {
    assertEquals(Headers.empty.getErrorText, none)
  }

  test("getErrorText - some") {
    assertEquals(
      Headers.empty.withError(400, "Bad Request").getErrorText,
      ("Bad Request": HeaderValue).some
    )
  }

}
