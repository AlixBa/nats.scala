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
import io.nats.scala.core.Subject
import munit.AnyFixture
import munit.CatsEffectSuite
import munit.Clues
import munit.catseffect.IOFixture

class TelemetryStreamSuite extends CatsEffectSuite {

  val fixtures: IOFixture[TelemetryNatsFixtures] = ResourceSuiteLocalFixture(
    "fixtures",
    TelemetryNatsFixtures.resource
  )

  override def munitFixtures: Seq[AnyFixture[?]] = List(fixtures)

  override def afterEach(context: AfterEach): Unit = {
    val _fixtures = fixtures()
    import _fixtures.*;

    tracesTestkit.resetSpans.unsafeRunSync()
  }

  test("stream(subject)") {
    val _fixtures = fixtures()
    import _fixtures.*;
    import natsFixtures.*;

    val subject: Subject.Single = "subject.1"

    for {
      tracer <- tracesTestkit.tracerProvider.get("io.nats.scala")
      streamAndCancel <- connection.stream("subject.*")
      (stream, cancel) = streamAndCancel
      fiber <- stream
        .evalTap(message =>
          message.replyTo match {
            case Some(replyTo) => connection.publish(replyTo, defaultData)
            case None          => IO.raiseError(new Exception("replyTo expected"))
          }
        )
        .compile
        .lastOrError
        .uncancelable
        .start
      _ <- tracer.rootSpan("root").surround(connection.request(subject, defaultData))
      _ <- cancel
      message <- fiber.joinWithNever
    } yield message match {
      case _: TelemetryMessage =>
      case _                   => fail("TelemetryMessage expected", Clues.fromValue(message))
    }
  }

  test("stream(subject, queueName)") {
    val _fixtures = fixtures()
    import _fixtures.*;
    import natsFixtures.*;

    val subject: Subject.Single = "subject.1"

    for {
      tracer <- tracesTestkit.tracerProvider.get("io.nats.scala")
      streamAndCancel <- connection.stream("subject.*")
      (stream, cancel) = streamAndCancel
      fiber <- stream
        .evalTap(message =>
          message.replyTo match {
            case Some(replyTo) => connection.publish(replyTo, defaultData)
            case None          => IO.raiseError(new Exception("replyTo expected"))
          }
        )
        .compile
        .lastOrError
        .uncancelable
        .start
      _ <- tracer.rootSpan("root").surround(connection.request(subject, defaultData))
      _ <- cancel
      message <- fiber.joinWithNever
    } yield message match {
      case _: TelemetryMessage =>
      case _                   => fail("TelemetryMessage expected", Clues.fromValue(message))
    }
  }

}
