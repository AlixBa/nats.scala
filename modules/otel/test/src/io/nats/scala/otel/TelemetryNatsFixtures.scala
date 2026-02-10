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

import cats.effect.IO
import cats.effect.kernel.Resource
import io.nats.client.Nats
import io.nats.client.Options as JOptions
import io.nats.scala.core.NatsFixtures
import io.nats.scala.otel.instances.log.*
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.instrumentation.nats.v2_17.NatsTelemetry
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.testing.StructuredTestingLogger
import org.typelevel.otel4s.context.LocalProvider
import org.typelevel.otel4s.oteljava.context.Context
import org.typelevel.otel4s.oteljava.testkit.context.IOLocalTestContextStorage
import org.typelevel.otel4s.oteljava.testkit.trace.TracesTestkit

import scala.concurrent.duration.DurationInt
import scala.jdk.DurationConverters.*

final case class TelemetryNatsFixtures(
    natsFixtures: NatsFixtures,
    tracesTestkit: TracesTestkit[IO],
    logger: StructuredTestingLogger[IO]
)

object TelemetryNatsFixtures {

  val resource = for {
    openTelemetry <- InMemoryJOpenTelemetry.resource

    localProvider = IOLocalTestContextStorage.localProvider[IO]
    local <- {
      implicit val _localProvider = localProvider
      LocalProvider[IO, Context].local.toResource
    }

    tracesTestkit <- {
      implicit val _localProvider = localProvider
      TracesTestkit
        .builder[IO]
        .withInMemorySpanExporter(openTelemetry.spanExporter)
        .withTextMapPropagators(List(W3CTraceContextPropagator.getInstance()))
        .build
    }

    logger: StructuredTestingLogger[IO] = StructuredTestingLogger.impl[IO]()

    loggerFactory = new LoggerFactory[IO] {
      override def getLoggerFromName(name: String): SelfAwareStructuredLogger[IO] = logger
      override def fromName(name: String): IO[SelfAwareStructuredLogger[IO]] = IO.pure(logger)
    }

    connectionListener <- {
      implicit val _loggerFactory = loggerFactory
      TelemetryConnectionListener.resource[IO]
    }

    errorListener <- {
      implicit val _loggerFactory = loggerFactory
      TelemetryErrorListener.resource[IO]
    }

    readListener <- {
      implicit val _loggerFactory = loggerFactory
      TelemetryReadListener.resource[IO]
    }

    options = (natsUrl: String) =>
      new JOptions.Builder()
        .server(natsUrl)
        .connectionListener(connectionListener)
        .errorListener(errorListener)
        .readListener(readListener)
        .build()

    fixtures <- NatsFixtures.resource(
      { natsUrl =>
        implicit val _local = local
        implicit val _tracerProvider = tracesTestkit.tracerProvider
        implicit val _loggerFactory = loggerFactory

        TelemetryNats.connect[IO](openTelemetry.openTelemetry, options(natsUrl))
      },
      natsUrl => {
        val telemetry = NatsTelemetry.builder(openTelemetry.openTelemetry).build()
        Resource.make(
          IO.delay(telemetry.createConnection(options(natsUrl), (options: JOptions) => Nats.connect(options)))
        )(connection => IO.fromCompletableFuture(IO.delay(connection.drain(30.seconds.toJava))).void)
      }
    )
  } yield TelemetryNatsFixtures(fixtures, tracesTestkit, logger)

}
