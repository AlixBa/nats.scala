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

import cats.effect.Spawn
import cats.effect.kernel.Async
import cats.effect.kernel.Resource
import cats.effect.std.Dispatcher as CEDispatcher
import cats.syntax.flatMap.toFlatMapOps
import cats.syntax.functor.toFunctorOps
import cats.syntax.option.catsSyntaxOptionId
import cats.syntax.option.none
import fs2.Stream
import io.nats.client.Connection as JConnection
import io.nats.client.Dispatcher as JDispatcher
import io.nats.client.MessageHandler as JMessageHandler
import io.nats.scala.core.Dispatcher
import io.nats.scala.core.Headers
import io.nats.scala.core.Message
import io.nats.scala.core.MessageHandler
import io.nats.scala.core.QueueName
import io.nats.scala.core.Subject.Single
import io.nats.scala.core.Subject.Wildcard
import io.nats.scala.core.Subscription

import scala.concurrent.duration.FiniteDuration
import scala.jdk.DurationConverters.*

/** Typed Scala API on top of a [[io.nats.client.Connection]]. Refer to the Nats documentation for more information. */
trait Connection[F[_]] extends ConnectionStream[F] {

  def publish(subject: Subject.Single, data: Array[Byte]): F[Unit]
  def publish(subject: Subject.Single, headers: Headers, data: Array[Byte]): F[Unit]
  def publish(subject: Subject.Single, replyTo: Subject.Single, data: Array[Byte]): F[Unit]
  def publish(subject: Subject.Single, replyTo: Subject.Single, headers: Headers, data: Array[Byte]): F[Unit]

  def request(subject: Subject.Single, data: Array[Byte]): F[Message]
  def request(subject: Subject.Single, data: Array[Byte], timeout: FiniteDuration): F[Message]
  def request(subject: Subject.Single, headers: Headers, data: Array[Byte]): F[Message]
  def request(subject: Subject.Single, headers: Headers, data: Array[Byte], timeout: FiniteDuration): F[Message]

  def subscribe(subject: Subject.Wildcard): Resource[F, Subscription.Synchronous[F]]
  def subscribe(subject: Subject.Wildcard, queueName: QueueName): Resource[F, Subscription.Synchronous[F]]

  def dispatcher(): Resource[F, Dispatcher[F]]
  def dispatcher(handler: MessageHandler[F]): Resource[F, Dispatcher.WithHandler[F]]

}

object Connection {

  def noop[F[_]: Spawn]: Connection[F] = NoopConnection.instance[F]

  private[nats] def apply[F[_]: Async](
      connection: JConnection,
      drainTimeout: FiniteDuration
  ): Connection[F] = new Connection[F] {
    val A: Async[F] = Async[F]
    import A._

    val connectionStream: ConnectionStream[F] = ConnectionStream[F](this)

    override def publish(subject: Single, data: Array[Byte]): F[Unit] =
      delay(connection.publish(subject.value, data))

    override def publish(subject: Single, headers: Headers, data: Array[Byte]): F[Unit] =
      delay(connection.publish(subject.value, Headers.asJava(headers), data))

    override def publish(subject: Single, replyTo: Single, data: Array[Byte]): F[Unit] =
      delay(connection.publish(subject.value, replyTo.value, data))

    override def publish(subject: Single, replyTo: Single, headers: Headers, data: Array[Byte]): F[Unit] =
      delay(connection.publish(subject.value, replyTo.value, Headers.asJava(headers), data))

    override def request(subject: Single, data: Array[Byte]): F[Message] =
      fromCompletableFuture(delay(connection.request(subject.value, data)))
        .map(Message.asScala(_))

    override def request(subject: Single, data: Array[Byte], timeout: FiniteDuration): F[Message] =
      fromCompletableFuture(blocking(connection.requestWithTimeout(subject.value, data, timeout.toJava)))
        .map(Message.asScala(_))

    override def request(subject: Single, headers: Headers, data: Array[Byte]): F[Message] =
      fromCompletableFuture(delay(connection.request(subject.value, Headers.asJava(headers), data)))
        .map(Message.asScala(_))

    override def request(subject: Single, headers: Headers, data: Array[Byte], timeout: FiniteDuration): F[Message] =
      fromCompletableFuture(
        blocking(connection.requestWithTimeout(subject.value, Headers.asJava(headers), data, timeout.toJava))
      )
        .map(Message.asScala(_))

    override def subscribe(subject: Wildcard): Resource[F, Subscription.Synchronous[F]] =
      subscribe(subject, none)

    override def subscribe(subject: Wildcard, queueName: QueueName): Resource[F, Subscription.Synchronous[F]] =
      subscribe(subject, queueName.some)

    def subscribe(subject: Wildcard, queueName: Option[QueueName]): Resource[F, Subscription.Synchronous[F]] =
      Resource
        .make(delay(queueName match {
          case Some(queueName) => connection.subscribe(subject.value, queueName.value)
          case None            => connection.subscribe(subject.value)
        }))(subscription => fromCompletableFuture(blocking(subscription.drain(drainTimeout.toJava))).void)
        .map(subscription => Subscription.Synchronous[F](subscription))

    override def dispatcher(): Resource[F, Dispatcher[F]] =
      Resource
        .make(delay(connection.createDispatcher()))(releaseDispatcher)
        .map(dispatcher => Dispatcher[F](dispatcher))

    override def dispatcher(handler: MessageHandler[F]): Resource[F, Dispatcher.WithHandler[F]] =
      for {
        ceDispatcher <- CEDispatcher.sequential[F](await = true)
        mh: JMessageHandler = message => ceDispatcher.unsafeRunSync(handler(Message.asScala(message)))
        dispatcher <- Resource.make(delay(connection.createDispatcher(mh)))(releaseDispatcher)
      } yield Dispatcher.WithHandler[F](dispatcher)

    def releaseDispatcher(dispatcher: JDispatcher): F[Unit] = for {
      _ <- fromCompletableFuture(blocking(dispatcher.drain(drainTimeout.toJava)))
      _ <- delay(connection.closeDispatcher(dispatcher))
    } yield ()

    override def stream(subject: Wildcard): F[(Stream[F, Message], F[Unit])] =
      connectionStream.stream(subject)

    override def stream(subject: Wildcard, queueName: QueueName): F[(Stream[F, Message], F[Unit])] =
      connectionStream.stream(subject, queueName)

  }

}
