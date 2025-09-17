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
import cats.effect.Resource
import cats.mtl.Local
import io.nats.scala.core.Dispatcher
import io.nats.scala.core.MessageHandler
import io.nats.scala.core.QueueName
import io.nats.scala.core.Subject.Wildcard
import io.nats.scala.core.Subscription
import org.typelevel.otel4s.oteljava.context.Context
import org.typelevel.otel4s.trace.Tracer

private object TelemetryDispatcher {

  object WithHandler {

    private[nats] def apply[F[_]: Concurrent: Tracer](
        dispatcher: Dispatcher.WithHandler[F]
    )(implicit local: Local[F, Context]): Dispatcher.WithHandler[F] =
      new Dispatcher.WithHandler[F] {
        val delegate: Dispatcher[F] = TelemetryDispatcher[F](dispatcher)

        override def subscribe(subject: Wildcard, handler: MessageHandler[F]): Resource[F, Subscription[F]] =
          delegate.subscribe(subject, handler)

        override def subscribe(
            subject: Wildcard,
            queueName: QueueName,
            handler: MessageHandler[F]
        ): Resource[F, Subscription[F]] =
          delegate.subscribe(subject, queueName, handler)

        override def subscribe(subject: Wildcard): Resource[F, Unit] =
          dispatcher.subscribe(subject)

        override def subscribe(subject: Wildcard, queueName: QueueName): Resource[F, Unit] =
          dispatcher.subscribe(subject, queueName)

      }

  }

  private[nats] def apply[F[_]: Concurrent: Tracer](
      dispatcher: Dispatcher[F]
  )(implicit local: Local[F, Context]): Dispatcher[F] =
    new Dispatcher[F] {

      override def subscribe(subject: Wildcard, handler: MessageHandler[F]): Resource[F, Subscription[F]] =
        dispatcher.subscribe(subject, TelemetryMessageHandler(handler))

      override def subscribe(
          subject: Wildcard,
          queueName: QueueName,
          handler: MessageHandler[F]
      ): Resource[F, Subscription[F]] =
        dispatcher.subscribe(subject, queueName, TelemetryMessageHandler(handler))

    }

}
