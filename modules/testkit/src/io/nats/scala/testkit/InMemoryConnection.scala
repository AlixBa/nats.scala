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

package io.nats.scala.testkit

import cats.effect.Ref
import cats.effect.Temporal
import cats.effect.kernel.Resource
import cats.effect.std.Queue
import cats.effect.syntax.spawn.genSpawnOps
import cats.effect.syntax.temporal.genTemporalOps
import cats.syntax.applicative.catsSyntaxApplicativeId
import cats.syntax.flatMap.toFlatMapOps
import cats.syntax.functor.toFunctorOps
import cats.syntax.option.catsSyntaxOptionId
import cats.syntax.option.none
import io.nats.scala.core.Connection
import io.nats.scala.core.Dispatcher
import io.nats.scala.core.Headers
import io.nats.scala.core.Message
import io.nats.scala.core.MessageHandler
import io.nats.scala.core.QueueName
import io.nats.scala.core.Subject
import io.nats.scala.core.Subscription

import scala.concurrent.duration.FiniteDuration

object InMemoryConnection {

  def instance[F[_]: Temporal]: F[Connection[F]] =
    for {
      server <- InMemoryNatsServer.instance[F]
      sid <- Ref.of(1)
    } yield new Connection[F] {

      override def stream(subject: Subject.Wildcard): F[(fs2.Stream[F, Message], F[Unit])] =
        stream(subject, none)

      override def stream(subject: Subject.Wildcard, queueName: QueueName): F[(fs2.Stream[F, Message], F[Unit])] =
        stream(subject, queueName.some)

      private def stream(
          subject: Subject.Wildcard,
          queueName: Option[QueueName]
      ): F[(fs2.Stream[F, Message], F[Unit])] =
        (for {
          sid <- sid.getAndUpdate(_ + 1)
          channel <- server.subscribe(sid, subject, queueName)
        } yield (sid, channel)).map { case (sid, channel) =>
          val close = for {
            _ <- server.unsubscribe(sid)
            _ <- channel.close
          } yield ()

          (channel.stream, close)
        }

      override def publish(subject: Subject.Single, data: Array[Byte]): F[Unit] =
        server.publish(subject, none, Headers.empty, data)

      override def publish(subject: Subject.Single, headers: Headers, data: Array[Byte]): F[Unit] =
        server.publish(subject, none, headers, data)

      override def publish(subject: Subject.Single, replyTo: Subject.Single, data: Array[Byte]): F[Unit] =
        server.publish(subject, replyTo.some, Headers.empty, data)

      override def publish(
          subject: Subject.Single,
          replyTo: Subject.Single,
          headers: Headers,
          data: Array[Byte]
      ): F[Unit] = server.publish(subject, replyTo.some, headers, data)

      override def request(subject: Subject.Single, data: Array[Byte]): F[Message] =
        request(subject, Headers.empty, data)

      override def request(subject: Subject.Single, data: Array[Byte], timeout: FiniteDuration): F[Message] =
        request(subject, Headers.empty, data, timeout)

      override def request(subject: Subject.Single, headers: Headers, data: Array[Byte]): F[Message] =
        (for {
          sid <- Resource.eval(sid.getAndUpdate(_ + 1))

          replyTo = "_INBOX." + sid
          wildcard = Subject.Wildcard.assume(replyTo)
          single = Subject.Single.assume(replyTo)

          sub <- Resource.make(server.subscribe(sid, wildcard, none))(_ => server.unsubscribe(sid))
          _ <- Resource.eval(server.publish(subject, single.some, headers, data))
        } yield sub).use(_.stream.head.compile.lastOrError)

      override def request(
          subject: Subject.Single,
          headers: Headers,
          data: Array[Byte],
          timeout: FiniteDuration
      ): F[Message] = request(subject, headers, data).timeout(timeout)

      override def subscribe(subject: Subject.Wildcard): Resource[F, Subscription.Synchronous[F]] =
        subscribe(subject, none)

      override def subscribe(
          subject: Subject.Wildcard,
          queueName: QueueName
      ): Resource[F, Subscription.Synchronous[F]] =
        subscribe(subject, queueName.some)

      private def subscribe(
          subject: Subject.Wildcard,
          queueName: Option[QueueName]
      ): Resource[F, Subscription.Synchronous[F]] =
        for {
          sid <- Resource.eval(sid.getAndUpdate(_ + 1))
          queue <- Resource.eval(Queue.unbounded[F, Message])
          sub <- Resource.make(server.subscribe(sid, subject, queueName))(_ => server.unsubscribe(sid))
          _ <- sub.stream.evalMap(queue.offer).compile.drain.background
        } yield new Subscription.Synchronous[F] {
          override def setPendingLimits(messages: Long, bytes: Long): F[Unit] = ().pure
          override def getPendingMessageLimit: Long = 0
          override def getPendingByteLimit: Long = 0
          override def next(timeout: FiniteDuration): F[Message] = queue.take.timeout(timeout)
        }

      override def dispatcher(): Resource[F, Dispatcher[F]] =
        dispatcher(_ => ().pure)

      override def dispatcher(handler: MessageHandler[F]): Resource[F, Dispatcher.WithHandler[F]] =
        Resource.pure(new Dispatcher.WithHandler[F] {
          override def subscribe(subject: Subject.Wildcard, handler: MessageHandler[F]): Resource[F, Subscription[F]] =
            subscribe(subject, none, handler)

          override def subscribe(
              subject: Subject.Wildcard,
              queueName: QueueName,
              handler: MessageHandler[F]
          ): Resource[F, Subscription[F]] =
            subscribe(subject, queueName.some, handler)

          private def subscribe(
              subject: Subject.Wildcard,
              queueName: Option[QueueName],
              handler: MessageHandler[F]
          ): Resource[F, Subscription[F]] =
            for {
              sid <- Resource.eval(sid.getAndUpdate(_ + 1))
              sub <- Resource.make(server.subscribe(sid, subject, queueName))(_ => server.unsubscribe(sid))
              _ <- sub.stream.evalMap(handler).compile.drain.background
            } yield new Subscription[F] {
              override def setPendingLimits(messages: Long, bytes: Long): F[Unit] = ().pure
              override def getPendingMessageLimit: Long = 0
              override def getPendingByteLimit: Long = 0
            }

          override def subscribe(subject: Subject.Wildcard): Resource[F, Unit] =
            subscribe(subject, none, handler).void

          override def subscribe(subject: Subject.Wildcard, queueName: QueueName): Resource[F, Unit] =
            subscribe(subject, queueName.some, handler).void
        })

    }
}
