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

import cats.effect.kernel.Async
import cats.effect.kernel.Resource
import cats.mtl.Local
import cats.syntax.flatMap.toFlatMapOps
import cats.syntax.functor.toFunctorOps
import io.nats.client.Connection as JConnection
import io.nats.client.Nats as JNats
import io.nats.client.Options as JOptions
import io.nats.scala.core.Connection
import io.nats.scala.otel.TelemetryConnection
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.instrumentation.nats.v2_17.NatsTelemetry
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.otel4s.oteljava.context.Context
import org.typelevel.otel4s.trace.TracerProvider

import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters.IterableHasAsJava
import scala.jdk.DurationConverters.*

/** Creates a [[io.nats.scala.core.Connection]] with NATS instrumentation from the Java instrumentation and context
  * propagation to the Scala effect of [[io.nats.scala.core.MessageHandler]].
  */
object TelemetryNats {

  def connect[F[_]: Async: TracerProvider: LoggerFactory](
      openTelemetry: OpenTelemetry,
      options: JOptions
  )(implicit local: Local[F, Context]): Resource[F, Connection[F]] =
    connect[F](openTelemetry, options, List.empty, 30.seconds)

  def connect[F[_]: Async: TracerProvider: LoggerFactory](
      openTelemetry: OpenTelemetry,
      options: JOptions,
      capturedHeaders: List[String]
  )(implicit local: Local[F, Context]): Resource[F, Connection[F]] =
    connect[F](openTelemetry, options, capturedHeaders, 30.seconds)

  def connect[F[_]: Async: TracerProvider: LoggerFactory](
      openTelemetry: OpenTelemetry,
      options: JOptions,
      drainTimeout: FiniteDuration
  )(implicit local: Local[F, Context]): Resource[F, Connection[F]] =
    connect[F](openTelemetry, options, List.empty, drainTimeout)

  def connect[F[_]: Async: TracerProvider: LoggerFactory](
      openTelemetry: OpenTelemetry,
      options: JOptions,
      capturedHeaders: List[String],
      drainTimeout: FiniteDuration
  )(implicit local: Local[F, Context]): Resource[F, Connection[F]] =
    connect[F](openTelemetry, options, (options: JOptions) => JNats.connect(options), capturedHeaders, drainTimeout)

  def connectReconnectOnConnect[F[_]: Async: TracerProvider: LoggerFactory](
      openTelemetry: OpenTelemetry,
      options: JOptions
  )(implicit local: Local[F, Context]): Resource[F, Connection[F]] =
    connectReconnectOnConnect[F](openTelemetry, options, List.empty, 30.seconds)

  def connectReconnectOnConnect[F[_]: Async: TracerProvider: LoggerFactory](
      openTelemetry: OpenTelemetry,
      options: JOptions,
      capturedHeaders: List[String]
  )(implicit local: Local[F, Context]): Resource[F, Connection[F]] =
    connectReconnectOnConnect[F](openTelemetry, options, capturedHeaders, 30.seconds)

  def connectReconnectOnConnect[F[_]: Async: TracerProvider: LoggerFactory](
      openTelemetry: OpenTelemetry,
      options: JOptions,
      drainTimeout: FiniteDuration
  )(implicit local: Local[F, Context]): Resource[F, Connection[F]] =
    connectReconnectOnConnect[F](openTelemetry, options, List.empty, drainTimeout)

  def connectReconnectOnConnect[F[_]: Async: TracerProvider: LoggerFactory](
      openTelemetry: OpenTelemetry,
      options: JOptions,
      capturedHeaders: List[String],
      drainTimeout: FiniteDuration
  )(implicit local: Local[F, Context]): Resource[F, Connection[F]] =
    connect[F](
      openTelemetry = openTelemetry,
      options = options,
      connection = (options: JOptions) => JNats.connectReconnectOnConnect(options),
      capturedHeaders = capturedHeaders,
      drainTimeout = drainTimeout
    )

  private def connect[F[_]: Async: TracerProvider: LoggerFactory](
      openTelemetry: OpenTelemetry,
      options: JOptions,
      connection: JOptions => JConnection,
      capturedHeaders: List[String],
      drainTimeout: FiniteDuration
  )(implicit local: Local[F, Context]): Resource[F, Connection[F]] = {
    val A: Async[F] = Async[F]
    import A.*

    val acquire = blocking {
      NatsTelemetry
        .builder(openTelemetry)
        .setCapturedHeaders(capturedHeaders.asJavaCollection)
        .build()
        .newConnection(options, options => connection(options))
    }

    Resource
      .eval(for {
        tracer <- TracerProvider[F].get("io.nats.scala")
        logger <- LoggerFactory[F].fromName("io.nats.scala.Connection")
      } yield (tracer, logger))
      .flatMap { case (tracer, logger) =>
        implicit val _tracer = tracer
        implicit val _logger = logger
        Resource
          .make(acquire)(connection => fromCompletableFuture(blocking(connection.drain(drainTimeout.toJava))).void)
          .map(connection => TelemetryConnection(Connection(connection, drainTimeout)))
      }
  }

}
