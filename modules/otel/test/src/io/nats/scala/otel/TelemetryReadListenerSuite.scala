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
import io.nats.client.impl.NatsMessage
import io.nats.scala.otel.instances.log.mlg2
import munit.CatsEffectSuite
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.testing.StructuredTestingLogger
import org.typelevel.log4cats.testing.StructuredTestingLogger.INFO
import org.typelevel.log4cats.testing.StructuredTestingLogger.TRACE

class TelemetryReadListenerSuite extends CatsEffectSuite {

  test("log protocol messages / nats messages") {
    val logger: StructuredTestingLogger[IO] = StructuredTestingLogger.impl[IO]()

    implicit val factory = new LoggerFactory[IO] {
      override def getLoggerFromName(name: String): SelfAwareStructuredLogger[IO] = logger
      override def fromName(name: String): IO[SelfAwareStructuredLogger[IO]] = IO.pure(logger)
    }

    val message = NatsMessage.builder().subject("sub").data("data").build()

    Dispatcher.sequential[IO](await = true).use { dispatcher =>
      TelemetryReadListener[IO](dispatcher).flatMap(listener =>
        for {
          _ <- IO.delay(listener.protocol("operation", null))
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
              Map("op" -> "operation", "message.subject" -> "sub", "message.replyTo" -> "<null>", "message.size" -> "4")
            )
          )
        )
      )
    }
  }

}
