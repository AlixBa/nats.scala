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
import io.nats.client.Options as JOptions
import io.nats.scala.core.NatsFixtures
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import org.typelevel.otel4s.context.LocalProvider
import org.typelevel.otel4s.oteljava.context.Context
import org.typelevel.otel4s.oteljava.testkit.context.IOLocalTestContextStorage
import org.typelevel.otel4s.oteljava.testkit.trace.TracesTestkit

final case class TelemetryNatsFixtures(
    natsFixtures: NatsFixtures,
    tracesTestkit: TracesTestkit[IO]
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
      TracesTestkit.fromInMemory[IO](
        inMemorySpanExporter = openTelemetry.spanExporter,
        textMapPropagators = List(W3CTraceContextPropagator.getInstance())
      )
    }
    tracer <- tracesTestkit.tracerProvider.get("io.nats.scala").toResource

    fixtures <- NatsFixtures.resource { natsUrl =>
      implicit val _local = local
      implicit val _tracer = tracer

      val options = new JOptions.Builder().server(natsUrl).build()
      TelemetryNats.connect[IO](openTelemetry.openTelemetry, options)
    }
  } yield TelemetryNatsFixtures(fixtures, tracesTestkit)

}
