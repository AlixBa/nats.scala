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

package io.nats.scala.core

import io.nats.client.Message as JMessage
import io.nats.client.impl.NatsMessage
import io.nats.scala.core.Headers

/** Typed Scala API on top of a [[io.nats.client.Message]]. Refer to the Nats documentation for more information. */
trait Message {
  def subject: Subject.Single
  def replyTo: Option[Subject.Single]
  def headers: Headers
  def data: Array[Byte]
}

object Message {

  def apply(
      subject: Subject.Single,
      replyTo: Option[Subject.Single],
      headers: Headers,
      data: Array[Byte]
  ): Message = Impl(subject, replyTo, headers, data)

  final private case class Impl(
      subject: Subject.Single,
      replyTo: Option[Subject.Single],
      headers: Headers,
      data: Array[Byte]
  ) extends Message

  private[nats] def asScala(message: JMessage): Message = Impl(
    Subject.Single.assume(message.getSubject()),
    Option(message.getReplyTo()).map(Subject.Single.assume(_)),
    Option(message.getHeaders()).map(Headers.asScala(_)).getOrElse(Headers.empty),
    Option(message.getData()).getOrElse(Array.emptyByteArray)
  )

  private[nats] def asJava(message: Message): JMessage = new NatsMessage(
    message.subject.value,
    message.replyTo.map(_.value).orNull,
    Headers.asJava(message.headers),
    message.data
  )

}
