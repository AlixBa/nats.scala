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
import cats.effect.Async
import cats.effect.Resource
import cats.effect.std.Dispatcher
import cats.syntax.functor.toFunctorOps
import io.nats.client.Message
import io.nats.client.ReadListener
import io.nats.scala.otel.log.LogContext
import org.typelevel.log4cats.LoggerFactory

object TelemetryReadListener {

  /** Creates a [[io.nats.client.ReadListener]] logging all messages received by a connection. */
  def resource[F[_]: Async: LoggerFactory](implicit
      mlg: LogContext[Message]
  ): Resource[F, ReadListener] =
    Dispatcher.parallel[F](true).evalMap(apply[F](_))

  def apply[F[_]: Functor: LoggerFactory](dispatcher: Dispatcher[F])(implicit
      mlg: LogContext[Message]
  ): F[ReadListener] =
    LoggerFactory[F].fromName("io.nats.scala.ReadListener").map { logger =>
      new ReadListener {
        override def protocol(op: String, text: String): Unit =
          dispatcher.unsafeRunAndForget(
            logger.trace(
              Map("op" -> op, "text" -> Option(text).getOrElse("<null>"))
            )("NATS protocol message received")
          )

        override def message(op: String, message: Message): Unit =
          dispatcher.unsafeRunAndForget(
            logger.info(Map("op" -> op) ++ mlg.toContext(message))("NATS message received")
          )
      }
    }

}
