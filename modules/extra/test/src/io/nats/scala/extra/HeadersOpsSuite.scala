/*
 * Copyright 2025 AlixBa
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
