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

import cats.effect.Concurrent
import cats.effect.kernel.Resource
import cats.mtl.Local
import fs2.Stream
import io.nats.scala.core.Connection
import io.nats.scala.core.ConnectionStream
import io.nats.scala.core.Dispatcher
import io.nats.scala.core.Dispatcher.WithHandler
import io.nats.scala.core.Headers
import io.nats.scala.core.Message
import io.nats.scala.core.MessageHandler
import io.nats.scala.core.QueueName
import io.nats.scala.core.Subject.Single
import io.nats.scala.core.Subject.Wildcard
import io.nats.scala.core.Subscription.Synchronous
import io.nats.scala.otel.TelemetryDispatcher.withLogging
import org.typelevel.log4cats.StructuredLogger
import org.typelevel.otel4s.oteljava.context.Context
import org.typelevel.otel4s.trace.Tracer

import scala.concurrent.duration.FiniteDuration

private object TelemetryConnection {

  private[nats] def apply[F[_]: Concurrent: Tracer: StructuredLogger](
      connection: Connection[F]
  )(implicit local: Local[F, Context]): Connection[F] = new Connection[F] {

    val connectionStream: ConnectionStream[F] = ConnectionStream[F](this)

    override def publish(subject: Single, data: Array[Byte]): F[Unit] =
      connection.publish(subject, data)

    override def publish(subject: Single, headers: Headers, data: Array[Byte]): F[Unit] =
      connection.publish(subject, headers, data)

    override def publish(subject: Single, replyTo: Single, data: Array[Byte]): F[Unit] =
      connection.publish(subject, replyTo, data)

    override def publish(subject: Single, replyTo: Single, headers: Headers, data: Array[Byte]): F[Unit] =
      connection.publish(subject, replyTo, headers, data)

    override def request(subject: Single, data: Array[Byte]): F[Message] =
      connection.request(subject, data)

    override def request(subject: Single, data: Array[Byte], timeout: FiniteDuration): F[Message] =
      connection.request(subject, data, timeout)

    override def request(subject: Single, headers: Headers, data: Array[Byte]): F[Message] =
      connection.request(subject, headers, data)

    override def request(subject: Single, headers: Headers, data: Array[Byte], timeout: FiniteDuration): F[Message] =
      connection.request(subject, headers, data, timeout)

    override def subscribe(subject: Wildcard): Resource[F, Synchronous[F]] =
      withLogging(connection.subscribe(subject), Map("subject" -> subject.value))

    override def subscribe(subject: Wildcard, queueName: QueueName): Resource[F, Synchronous[F]] =
      withLogging(
        connection.subscribe(subject, queueName),
        Map("subject" -> subject.value, "queueName" -> queueName.value)
      )

    override def dispatcher(): Resource[F, Dispatcher[F]] =
      connection.dispatcher().map(TelemetryDispatcher(_))

    override def dispatcher(handler: MessageHandler[F]): Resource[F, WithHandler[F]] =
      connection.dispatcher(TelemetryMessageHandler(handler)).map(TelemetryDispatcher.WithHandler(_))

    override def stream(subject: Wildcard): F[(Stream[F, Message], F[Unit])] =
      connectionStream.stream(subject)

    override def stream(subject: Wildcard, queueName: QueueName): F[(Stream[F, Message], F[Unit])] =
      connectionStream.stream(subject, queueName)

  }

}
