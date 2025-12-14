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

import cats.effect.Async
import cats.effect.Resource
import cats.effect.std.Dispatcher
import cats.syntax.functor.toFunctorOps
import io.nats.client.Connection
import io.nats.client.ConnectionListener
import io.nats.scala.otel.log.LogContext
import org.typelevel.log4cats.LoggerFactory

import java.lang

object TelemetryConnectionListener {

  /** Creates a [[io.nats.client.ConnectionListener]] logging all events received on the connection. Use the semigroup
    * instance from the `core` module to combine several ConnectionListeners into one.
    */
  def resource[F[_]: Async: LoggerFactory](implicit
      clg: LogContext[Connection]
  ): Resource[F, ConnectionListener] =
    Dispatcher.parallel[F](true).evalMap { dispatcher =>
      LoggerFactory[F].fromName("io.nats.scala.ConnectionListener").map { logger =>
        new ConnectionListener {

          override def connectionEvent(
              conn: Connection,
              `type`: ConnectionListener.Events,
              time: lang.Long,
              uriDetails: String
          ): Unit =
            dispatcher.unsafeRunAndForget(
              logger.debug(
                Map("event" -> `type`.getEvent(), "time" -> time.toString(), "uriDetails" -> uriDetails) ++
                  clg.toContext(conn)
              )("NATS connection event received")
            )

          override def connectionEvent(
              conn: Connection,
              `type`: ConnectionListener.Events
          ): Unit = ()

        }
      }
    }

}
