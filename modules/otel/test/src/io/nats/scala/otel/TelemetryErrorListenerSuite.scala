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

import cats.Show
import cats.effect.IO
import cats.effect.std.Dispatcher
import io.nats.client.Connection
import io.nats.client.Consumer
import io.nats.client.Message
import io.nats.client.impl.NatsMessage
import io.nats.scala.core.NatsFixtures
import munit.AnyFixture
import munit.CatsEffectSuite
import munit.catseffect.IOFixture
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.testing.StructuredTestingLogger
import org.typelevel.log4cats.testing.StructuredTestingLogger.ERROR
import org.typelevel.log4cats.testing.StructuredTestingLogger.WARN

class TelemetryErrorListenerSuite extends CatsEffectSuite {

  val fixtures: IOFixture[NatsFixtures] = ResourceSuiteLocalFixture(
    "fixtures",
    NatsFixtures.resource
  )

  override def munitFixtures: Seq[AnyFixture[?]] = List(fixtures)

  test("log client errors / exceptions / warnings") {
    val _fixtures = fixtures()
    import _fixtures.*

    val logger: StructuredTestingLogger[IO] = StructuredTestingLogger.impl[IO]()

    implicit val factory = new LoggerFactory[IO] {
      override def getLoggerFromName(name: String): SelfAwareStructuredLogger[IO] = logger
      override def fromName(name: String): IO[SelfAwareStructuredLogger[IO]] = IO.pure(logger)
    }

    implicit val cs0: Show[Connection] = Show.show(_ => "connection")
    implicit val cs1: Show[Consumer] = Show.show(_ => "consumer")
    implicit val ms: Show[Message] = Show.show(_ => "message")

    val exception = new Exception("exception")
    val mdc = Map("connection" -> "connection")
    val message = NatsMessage.builder().subject("s").build()

    Dispatcher.sequential[IO](await = true).use { dispatcher =>
      TelemetryErrorListener[IO](dispatcher).flatMap { listener =>
        for {
          consumer <- IO.delay(jconnection.createDispatcher())
          _ <- IO.delay(listener.errorOccurred(jconnection, "an error occurred"))
          _ <- IO.delay(listener.exceptionOccurred(jconnection, exception))
          _ <- IO.delay(listener.slowConsumerDetected(jconnection, consumer))
          _ <- IO.delay(listener.messageDiscarded(jconnection, message))
          _ <- IO.delay(listener.socketWriteTimeout(jconnection))
          logged <- logger.logged.iterateWhile(_.size < 5)
        } yield assertEquals(
          logged,
          Vector(
            ERROR("an error occurred", None, mdc),
            ERROR("exception", Some(exception), mdc),
            WARN("Slow consumer detected", None, mdc ++ Map("consumer" -> "consumer")),
            WARN("Message discarded", None, mdc ++ Map("message" -> "message")),
            ERROR("Socket write timeout", None, mdc)
          )
        )
      }
    }
  }

}
