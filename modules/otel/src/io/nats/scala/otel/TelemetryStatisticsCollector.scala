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

import cats.effect.Resource
import cats.effect.kernel.Async
import cats.effect.std.Dispatcher
import cats.effect.syntax.resource.effectResourceOps
import cats.syntax.flatMap.toFlatMapOps
import cats.syntax.functor.toFunctorOps
import io.nats.client.StatisticsCollector
import org.typelevel.otel4s.Attribute
import org.typelevel.otel4s.Attributes
import org.typelevel.otel4s.metrics.BucketBoundaries
import org.typelevel.otel4s.metrics.MeterProvider

// scalafmt: { maxColumn = 160 }
// https://opentelemetry.io/blog/2025/how-to-name-your-metrics/
object TelemetryStatisticsCollector {

  val defaultByteBuckets: BucketBoundaries =
    BucketBoundaries(0, 32, 64, 128, 256, 512, 1024, 2048, 4096, 8192, 16384, 32768)

  def apply[F[_]: Async: MeterProvider](
      connectionName: String,
      metricName: String => String,
      byteBuckets: BucketBoundaries
  ): Resource[F, StatisticsCollector] = {
    val ca = Attributes(
      Attribute("messaging.system", "nats"),
      Attribute("connection.name", connectionName)
    )

    val client = (n: String) => metricName(s"messaging.client.$n")
    val sent = (n: String) => client(s"sent.$n")
    val consumed = (n: String) => client(s"consumed.$n")

    (for {
      dispatcher <- Dispatcher.parallel[F](await = true)
      meter <- MeterProvider[F].get("io.nats.scala").toResource
    } yield (dispatcher, meter)).evalMap { case (dispatcher, meter) =>
      for {
        outBytes <- meter.counter[Long](sent("bytes")).withUnit("By").create
        inBytes <- meter.counter[Long](consumed("bytes")).withUnit("By").create

        outMsgs <- meter.counter[Long](sent("messages")).create
        inMsgs <- meter.counter[Long](consumed("messages")).create
        droppedCount <- meter.counter[Long](consumed("messages.dropped")).create

        requestsSent <- meter.counter[Long](sent("requests")).create
        outstandingRequests <- meter.upDownCounter[Long](sent("requests.outstanding")).create
        repliesReceived <- meter.counter[Long](consumed("replies")).create
        duplicateRepliesReceived <- meter.counter[Long](consumed("replies.duplicated")).create
        orphanRepliesReceived <- meter.counter[Long](consumed("replies.orphan")).create

        pingCount <- meter.counter[Long](client("pings")).create
        reconnects <- meter.counter[Long](client("reconnects")).create
        flushCount <- meter.counter[Long](client("flushes")).create

        okCount <- meter.counter[Long](client("oks")).create
        errCount <- meter.counter[Long](client("errors")).create
        exceptionCount <- meter.counter[Long](client("exceptions")).create

        readStats <- meter.histogram[Long](client("read.bytes")).withExplicitBucketBoundaries(byteBuckets).withUnit("By").create
        writeStats <- meter.histogram[Long](client("write.bytes")).withExplicitBucketBoundaries(byteBuckets).withUnit("By").create
      } yield new StatisticsCollector {
        override def setAdvancedTracking(trackAdvanced: Boolean): Unit = ()
        override def incrementPingCount(): Unit = dispatcher.unsafeRunAndForget(pingCount.inc(ca))
        override def incrementReconnects(): Unit = dispatcher.unsafeRunAndForget(reconnects.inc(ca))
        override def incrementDroppedCount(): Unit = dispatcher.unsafeRunAndForget(droppedCount.inc(ca))
        override def incrementOkCount(): Unit = dispatcher.unsafeRunAndForget(okCount.inc(ca))
        override def incrementErrCount(): Unit = dispatcher.unsafeRunAndForget(errCount.inc(ca))
        override def incrementExceptionCount(): Unit = dispatcher.unsafeRunAndForget(exceptionCount.inc(ca))
        override def incrementRequestsSent(): Unit = dispatcher.unsafeRunAndForget(requestsSent.inc(ca))
        override def incrementRepliesReceived(): Unit = dispatcher.unsafeRunAndForget(repliesReceived.inc(ca))
        override def incrementDuplicateRepliesReceived(): Unit = dispatcher.unsafeRunAndForget(duplicateRepliesReceived.inc(ca))
        override def incrementOrphanRepliesReceived(): Unit = dispatcher.unsafeRunAndForget(orphanRepliesReceived.inc(ca))
        override def incrementInMsgs(): Unit = dispatcher.unsafeRunAndForget(inMsgs.inc(ca))
        override def incrementOutMsgs(): Unit = dispatcher.unsafeRunAndForget(outMsgs.inc(ca))
        override def incrementInBytes(bytes: Long): Unit = dispatcher.unsafeRunAndForget(inBytes.add(bytes, ca))
        override def incrementOutBytes(bytes: Long): Unit = dispatcher.unsafeRunAndForget(outBytes.add(bytes, ca))
        override def incrementFlushCounter(): Unit = dispatcher.unsafeRunAndForget(flushCount.inc(ca))
        override def incrementOutstandingRequests(): Unit = dispatcher.unsafeRunAndForget(outstandingRequests.inc(ca))
        override def decrementOutstandingRequests(): Unit = dispatcher.unsafeRunAndForget(outstandingRequests.dec(ca))
        override def registerRead(bytes: Long): Unit = dispatcher.unsafeRunAndForget(readStats.record(bytes, ca))
        override def registerWrite(bytes: Long): Unit = dispatcher.unsafeRunAndForget(writeStats.record(bytes, ca))
        override def getPings: Long = 0
        override def getReconnects: Long = 0
        override def getDroppedCount: Long = 0
        override def getOKs: Long = 0
        override def getErrs: Long = 0
        override def getExceptions: Long = 0
        override def getRequestsSent: Long = 0
        override def getRepliesReceived: Long = 0
        override def getDuplicateRepliesReceived: Long = 0
        override def getOrphanRepliesReceived: Long = 0
        override def getInMsgs: Long = 0
        override def getOutMsgs: Long = 0
        override def getInBytes: Long = 0
        override def getOutBytes: Long = 0
        override def getFlushCounter: Long = 0
        override def getOutstandingRequests: Long = 0
      }
    }
  }

}
