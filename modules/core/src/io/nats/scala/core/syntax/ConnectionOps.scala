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

package io.nats.scala.core.syntax

import cats.Applicative
import io.nats.scala.core.Connection
import io.nats.scala.core.Headers
import io.nats.scala.core.Message
import io.nats.scala.core.Subject

private[nats] object ConnectionOps {

  trait ToConnectionOps {
    implicit def toConnectionOps[F[_]](connection0: Connection[F]): Ops[F] = new Ops[F] {
      override val connection: Connection[F] = connection0
    }
  }

  trait Ops[F[_]] {
    def connection: Connection[F]

    def reply(message: Message, data: Array[Byte])(implicit A: Applicative[F]): F[Unit] =
      whenReplyTo(message)(_replyTo => connection.publish(_replyTo, data))

    def reply(message: Message, headers: Headers, data: Array[Byte])(implicit A: Applicative[F]): F[Unit] =
      whenReplyTo(message)(_replyTo => connection.publish(_replyTo, headers, data))

    def reply(message: Message, replyTo: Subject.Single, data: Array[Byte])(implicit A: Applicative[F]): F[Unit] =
      whenReplyTo(message)(_replyTo => connection.publish(_replyTo, replyTo, data))

    def reply(message: Message, replyTo: Subject.Single, headers: Headers, data: Array[Byte])(implicit
        A: Applicative[F]
    ): F[Unit] = whenReplyTo(message)(_replyTo => connection.publish(_replyTo, replyTo, headers, data))

    private def whenReplyTo(message: Message)(f: Subject.Single => F[Unit])(implicit A: Applicative[F]): F[Unit] =
      message.replyTo match {
        case None          => A.unit
        case Some(replyTo) => f(replyTo)
      }
  }
}
