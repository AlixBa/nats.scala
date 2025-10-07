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

import cats.Functor
import cats.Show
import cats.effect.kernel.Async
import cats.effect.kernel.Resource
import cats.effect.std.Dispatcher
import cats.syntax.functor.toFunctorOps
import cats.syntax.show.toShow
import io.nats.client.Connection
import io.nats.client.Consumer
import io.nats.client.ErrorListener
import io.nats.client.Message
import io.nats.client.impl.ErrorListenerLoggerImpl
import org.typelevel.log4cats.LoggerFactory

object TelemetryErrorListener {

  def apply[F[_]: LoggerFactory: Async](implicit
      cs0: Show[Connection],
      cs1: Show[Consumer],
      ms: Show[Message]
  ): Resource[F, ErrorListener] =
    Dispatcher.parallel[F](await = true).evalMap(apply(_))

  def apply[F[_]: LoggerFactory: Functor](dispatcher: Dispatcher[F])(implicit
      cs0: Show[Connection],
      cs1: Show[Consumer],
      ms: Show[Message]
  ): F[ErrorListener] = LoggerFactory[F].fromName("io.nats.scala.ErrorListener").map { logger =>
    // Extending the current NATS implementation so we still rely on Java logging
    // in case we didn't override a property. Currently not handling JetStreams.
    new ErrorListenerLoggerImpl {

      override def errorOccurred(conn: Connection, error: String): Unit =
        dispatcher.unsafeRunAndForget(
          logger.error(Map("connection" -> conn.show))(error)
        )

      override def exceptionOccurred(conn: Connection, exp: Exception): Unit =
        dispatcher.unsafeRunAndForget(
          logger.error(Map("connection" -> conn.show), exp)(exp.getMessage())
        )

      override def slowConsumerDetected(conn: Connection, consumer: Consumer): Unit =
        dispatcher.unsafeRunAndForget(
          logger.warn(Map("connection" -> conn.show, "consumer" -> consumer.show))("Slow consumer detected")
        )

      override def messageDiscarded(conn: Connection, msg: Message): Unit =
        dispatcher.unsafeRunAndForget(
          logger.warn(Map("connection" -> conn.show, "message" -> msg.show))("Message discarded")
        )

      override def socketWriteTimeout(conn: Connection): Unit =
        dispatcher.unsafeRunAndForget(
          logger.error(Map("connection" -> conn.show))("Socket write timeout")
        )

    }

  }

}
