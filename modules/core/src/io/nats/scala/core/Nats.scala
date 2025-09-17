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

import cats.effect.kernel.Async
import cats.effect.kernel.Resource
import cats.syntax.functor.toFunctorOps
import io.nats.client.Connection as JConnection
import io.nats.client.Nats as JNats
import io.nats.client.Options as JOptions

import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import scala.jdk.DurationConverters.*

/** Typed Scala API on top of [[io.nats.client.Nats]]. Refer to the Nats documentation for more information. */
object Nats {

  def connect[F[_]: Async](
      options: JOptions
  ): Resource[F, Connection[F]] =
    connect[F](options, 30.seconds)

  def connect[F[_]: Async](
      options: JOptions,
      drainTimeout: FiniteDuration
  ): Resource[F, Connection[F]] =
    connect(Async[F].blocking(JNats.connect(options)), drainTimeout)

  def connectReconnectOnConnect[F[_]: Async](
      options: JOptions
  ): Resource[F, Connection[F]] =
    connectReconnectOnConnect[F](options, 30.seconds)

  def connectReconnectOnConnect[F[_]: Async](
      options: JOptions,
      drainTimeout: FiniteDuration
  ): Resource[F, Connection[F]] =
    connect(Async[F].blocking(JNats.connectReconnectOnConnect(options)), drainTimeout)

  private def connect[F[_]: Async](
      connection: F[JConnection],
      drainTimeout: FiniteDuration
  ): Resource[F, Connection[F]] = {
    val A: Async[F] = Async[F]
    import A.*

    Resource
      .make(connection)(connection => fromCompletableFuture(blocking(connection.drain(drainTimeout.toJava))).void)
      .map(Connection(_, drainTimeout))
  }

}
