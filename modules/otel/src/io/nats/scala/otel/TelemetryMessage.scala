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

package io.nats.scala.otel

import io.nats.scala.core.Headers
import io.nats.scala.core.Message
import io.nats.scala.core.Subject.Single
import org.typelevel.otel4s.trace.SpanContext

/** Extension of a [[io.nats.scala.core.Message]] with an optional [[org.typelevel.otel4s.trace.SpanContext]] propagated
  * through the NATS server. Useful for [[io.nats.scala.core.ConnectionStream]] where each stage of the stream
  * processing loses the tracing context.
  */
trait TelemetryMessage extends Message {
  def spanContext: Option[SpanContext]
}

private object TelemetryMessage {

  final private case class Impl(
      message: Message,
      spanContext: Option[SpanContext]
  ) extends TelemetryMessage {
    override val subject: Single = message.subject
    override val replyTo: Option[Single] = message.replyTo
    override val headers: Headers = message.headers
    override val data: Array[Byte] = message.data
  }

  private[nats] def apply(
      message: Message,
      spanContext: Option[SpanContext]
  ): TelemetryMessage = Impl(message, spanContext)

}
