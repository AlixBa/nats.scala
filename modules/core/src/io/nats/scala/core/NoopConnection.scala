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

import cats.Applicative
import cats.effect.Spawn
import cats.effect.kernel.Resource
import cats.syntax.applicative.catsSyntaxApplicativeId

import scala.concurrent.duration.FiniteDuration

private[nats] object NoopConnection {

  def instance[F[_]: Spawn]: Connection[F] = new Connection[F] {
    val unit = Applicative[F].unit
    val never = Spawn[F].never[Message]

    val subscriber = Resource.pure[F, Subscription.Synchronous[F]](new Subscription.Synchronous[F] {
      override def setPendingLimits(messages: Long, bytes: Long): F[Unit] = unit
      override def getPendingMessageLimit: Long = 0
      override def getPendingByteLimit: Long = 0
      override def next(timeout: FiniteDuration): F[Message] = never
    })

    val dispatcher0 = Resource.pure[F, Dispatcher.WithHandler[F]](new Dispatcher.WithHandler[F] {
      override def subscribe(
          subject: Subject.Wildcard,
          handler: MessageHandler[F]
      ): Resource[F, Subscription[F]] = subscriber

      override def subscribe(
          subject: Subject.Wildcard,
          queueName: QueueName,
          handler: MessageHandler[F]
      ): Resource[F, Subscription[F]] = subscriber

      override def subscribe(subject: Subject.Wildcard): Resource[F, Unit] = Resource.unit

      override def subscribe(subject: Subject.Wildcard, queueName: QueueName): Resource[F, Unit] = Resource.unit
    })

    override def stream(subject: Subject.Wildcard): F[(fs2.Stream[F, Message], F[Unit])] =
      (fs2.Stream.never[F].covaryOutput[Message], unit).pure[F]

    override def stream(subject: Subject.Wildcard, queueName: QueueName): F[(fs2.Stream[F, Message], F[Unit])] =
      (fs2.Stream.never[F].covaryOutput[Message], unit).pure[F]

    override def publish(subject: Subject.Single, data: Array[Byte]): F[Unit] = unit

    override def publish(subject: Subject.Single, headers: Headers, data: Array[Byte]): F[Unit] = unit

    override def publish(subject: Subject.Single, replyTo: Subject.Single, data: Array[Byte]): F[Unit] = unit

    override def publish(
        subject: Subject.Single,
        replyTo: Subject.Single,
        headers: Headers,
        data: Array[Byte]
    ): F[Unit] = unit

    override def request(subject: Subject.Single, data: Array[Byte]): F[Message] = never

    override def request(subject: Subject.Single, data: Array[Byte], timeout: FiniteDuration): F[Message] = never

    override def request(subject: Subject.Single, headers: Headers, data: Array[Byte]): F[Message] = never

    override def request(
        subject: Subject.Single,
        headers: Headers,
        data: Array[Byte],
        timeout: FiniteDuration
    ): F[Message] = never

    override def subscribe(subject: Subject.Wildcard): Resource[F, Subscription.Synchronous[F]] = subscriber

    override def subscribe(subject: Subject.Wildcard, queueName: QueueName): Resource[F, Subscription.Synchronous[F]] =
      subscriber

    override def dispatcher(): Resource[F, Dispatcher[F]] = dispatcher0

    override def dispatcher(handler: MessageHandler[F]): Resource[F, Dispatcher.WithHandler[F]] = dispatcher0
  }

}
