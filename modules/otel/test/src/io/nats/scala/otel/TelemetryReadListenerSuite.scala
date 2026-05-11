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
import cats.effect.std.Dispatcher
import cats.syntax.eq.catsSyntaxEq
import io.nats.client.impl.Headers
import io.nats.client.impl.NatsMessage
import io.nats.scala.otel.instances.log.mlg2
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import munit.CatsEffectSuite
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.testing.StructuredTestingLogger
import org.typelevel.log4cats.testing.StructuredTestingLogger.INFO
import org.typelevel.log4cats.testing.StructuredTestingLogger.LogMessage
import org.typelevel.log4cats.testing.StructuredTestingLogger.TRACE
import org.typelevel.otel4s.oteljava.testkit.trace.TracesTestkit

import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec

class TelemetryReadListenerSuite extends CatsEffectSuite {

  test("log protocol messages / nats messages") {
    (for {
      tracesTestkit <- TracesTestkit.inMemory[IO](
        _.withTextMapPropagators(List(W3CTraceContextPropagator.getInstance()))
      )
      tracer <- tracesTestkit.tracerProvider.get("io.nats.scala").toResource

      ar = new AtomicReference(Vector.empty[LogMessage])
      logger = StructuredTestingLogger.make[IO](
        appendLogMessage = lm =>
          tracer.currentSpanContext.map { context =>
            val map = context
              .map(context => Map("traceId" -> context.traceIdHex, "spanId" -> context.spanIdHex))
              .getOrElse(Map.empty)

            @tailrec
            def mod(): Unit = {
              val c = ar.get
              val u = c :+ (lm match {
                case lm: StructuredTestingLogger.INFO => lm.copy(ctx = lm.ctx ++ map)
                case lm                               => lm
              })
              if (!ar.compareAndSet(c, u)) mod()
              else ()
            }
            mod()
          },
        read = () => IO.delay(ar.get())
      )
      factory = new LoggerFactory[IO] {
        override def getLoggerFromName(name: String): SelfAwareStructuredLogger[IO] = logger
        override def fromName(name: String): IO[SelfAwareStructuredLogger[IO]] = IO.pure(logger)
      }

      dispatcher <- Dispatcher.sequential[IO](await = true)
    } yield (dispatcher, factory, logger, tracesTestkit.tracerProvider)).use {
      case (dispatcher, factory, logger, tracerProvider) =>
        implicit val _factory = factory
        implicit val _tracerProvider = tracerProvider

        val message = NatsMessage
          .builder()
          .subject("sub")
          .headers(
            new Headers()
              .add("traceparent", "00-2845b44e41efefe8c7a315175335ac07-f46f1d0f45117391-01")
              .add("tracestate", "")
          )
          .data("data")
          .build()

        for {
          listener <- TelemetryReadListener[IO](dispatcher)
          _ <- IO.delay(listener.protocol("operation", None.orNull))
          _ <- IO.delay(listener.protocol("operation", "full_text"))
          _ <- IO.delay(listener.message("operation", message))
          logs <- logger.logged.iterateUntil(_.size === 3)
        } yield assertEquals(
          logs,
          Vector(
            TRACE("NATS protocol message received", None, Map("op" -> "operation", "text" -> "<null>")),
            TRACE("NATS protocol message received", None, Map("op" -> "operation", "text" -> "full_text")),
            INFO(
              "NATS message received",
              None,
              Map(
                "op" -> "operation",
                "message.subject" -> "sub",
                "message.replyTo" -> "<null>",
                "message.size" -> "4",
                "traceId" -> "2845b44e41efefe8c7a315175335ac07",
                "spanId" -> "f46f1d0f45117391"
              )
            )
          )
        )
    }
  }

}
