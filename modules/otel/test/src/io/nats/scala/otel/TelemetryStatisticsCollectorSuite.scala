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
import io.nats.scala.otel.SimpleMetric.cmp
import munit.CatsEffectSuite
import org.typelevel.otel4s.Attribute
import org.typelevel.otel4s.Attributes
import org.typelevel.otel4s.metrics.BucketBoundaries
import org.typelevel.otel4s.oteljava.testkit.metrics.MetricsTestkit
import org.typelevel.otel4s.oteljava.testkit.metrics.data.Metric

class TelemetryStatisticsCollectorSuite extends CatsEffectSuite {

  test("all metrics") {
    val connectionName = "connectionName"
    val name = (n: String) => s"prefix.$n"
    val attributes = Attributes(
      Attribute("connection.name", connectionName),
      Attribute("messaging.system", "nats")
    )

    (for {
      openTelemetry <- InMemoryJOpenTelemetry.resource
      metricsTestkit <- MetricsTestkit.fromInMemory[IO](openTelemetry.metricReader)
      collector <- {
        implicit val _meterProvider = metricsTestkit.meterProvider
        TelemetryStatisticsCollector[IO](connectionName, name, BucketBoundaries(10))
      }
    } yield (metricsTestkit, collector)).use { case (metricsTestkit, collector) =>
      for {
        _ <- IO.delay(collector.incrementPingCount())
        _ <- IO.delay(collector.incrementReconnects())
        _ <- IO.delay(collector.incrementDroppedCount())
        _ <- IO.delay(collector.incrementOkCount())
        _ <- IO.delay(collector.incrementErrCount())
        _ <- IO.delay(collector.incrementExceptionCount())
        _ <- IO.delay(collector.incrementRequestsSent())
        _ <- IO.delay(collector.incrementRepliesReceived())
        _ <- IO.delay(collector.incrementDuplicateRepliesReceived())
        _ <- IO.delay(collector.incrementOrphanRepliesReceived())
        _ <- IO.delay(collector.incrementInMsgs())
        _ <- IO.delay(collector.incrementOutMsgs())
        _ <- IO.delay(collector.incrementInBytes(2))
        _ <- IO.delay(collector.incrementOutBytes(2))
        _ <- IO.delay(collector.incrementFlushCounter())
        _ <- IO.delay(collector.incrementOutstandingRequests())
        _ <- IO.delay(collector.incrementOutstandingRequests())
        _ <- IO.delay(collector.decrementOutstandingRequests())
        _ <- IO.delay(collector.registerRead(2))
        _ <- IO.delay(collector.registerWrite(2))
        metrics <- metricsTestkit.collectMetrics[Metric].iterateWhile(_.size < 18)
      } yield assertEquals(
        metrics,
        List[SimpleMetric](
          SimpleMetric.CounterLong("prefix.messaging.client.pings", attributes, List(1)),
          SimpleMetric.CounterLong("prefix.messaging.client.reconnects", attributes, List(1)),
          SimpleMetric.CounterLong("prefix.messaging.client.consumed.messages.dropped", attributes, List(1)),
          SimpleMetric.CounterLong("prefix.messaging.client.oks", attributes, List(1)),
          SimpleMetric.CounterLong("prefix.messaging.client.errors", attributes, List(1)),
          SimpleMetric.CounterLong("prefix.messaging.client.exceptions", attributes, List(1)),
          SimpleMetric.CounterLong("prefix.messaging.client.sent.requests", attributes, List(1)),
          SimpleMetric.CounterLong("prefix.messaging.client.consumed.replies", attributes, List(1)),
          SimpleMetric.CounterLong("prefix.messaging.client.consumed.replies.duplicated", attributes, List(1)),
          SimpleMetric.CounterLong("prefix.messaging.client.consumed.replies.orphan", attributes, List(1)),
          SimpleMetric.CounterLong("prefix.messaging.client.consumed.messages", attributes, List(1)),
          SimpleMetric.CounterLong("prefix.messaging.client.sent.messages", attributes, List(1)),
          SimpleMetric.CounterLong("prefix.messaging.client.consumed.bytes", attributes, List(2)),
          SimpleMetric.CounterLong("prefix.messaging.client.sent.bytes", attributes, List(2)),
          SimpleMetric.CounterLong("prefix.messaging.client.flushes", attributes, List(1)),
          SimpleMetric.CounterLong("prefix.messaging.client.sent.requests.outstanding", attributes, List(1)),
          SimpleMetric.HistogramDouble("prefix.messaging.client.read.bytes", attributes, List(2)),
          SimpleMetric.HistogramDouble("prefix.messaging.client.write.bytes", attributes, List(2))
        )
      )
    }
  }

}
