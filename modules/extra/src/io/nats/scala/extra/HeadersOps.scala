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
