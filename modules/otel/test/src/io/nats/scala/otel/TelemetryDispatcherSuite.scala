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
import cats.effect.kernel.Deferred
import cats.syntax.eq.catsSyntaxEq
import io.nats.scala.core.Subject
import munit.AnyFixture
import munit.CatsEffectSuite
import munit.catseffect.IOFixture

class TelemetryDispatcherSuite extends CatsEffectSuite {

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

  test("dispatcher().subscribe(subject, handler)") {
    val _fixtures = fixtures()
    import _fixtures.*;
    import natsFixtures.*;

    val subject: Subject.Single = "subject.1"

    (for {
      dispatcher <- connection.dispatcher()
      deferred <- Deferred[IO, TelemetryMessage].toResource
      tracer <- tracesTestkit.tracerProvider.get("io.nats.scala").toResource
      _ <- dispatcher.subscribe(
        "subject.*",
        {
          case message: TelemetryMessage =>
            tracer.span("complete").surround(deferred.complete(message)).void
          case _ => IO.raiseError(new IllegalArgumentException("TelemetryMessage expected"))
        }
      )
    } yield (tracer, deferred)).use { case (tracer, deferred) =>
      for {
        _ <- tracer.rootSpan("root").surround(connection.publish(subject, defaultData))
        message <- deferred.get
        spans <- tracesTestkit.finishedSpans.iterateUntil(_.size === 4)
      } yield {
        assert(message.spanContext.isDefined)
        assertEquals(
          spans,
          SpanTree("root", SpanTree("subject.1 publish", SpanTree("subject.1 process", SpanTree("complete"))))
        )
      }
    }
  }

  test("dispatcher().subscribe(subject, queueName, handler)") {
    val _fixtures = fixtures()
    import _fixtures.*;
    import natsFixtures.*;

    val subject: Subject.Single = "subject.1"

    (for {
      dispatcher <- connection.dispatcher()
      deferred <- Deferred[IO, TelemetryMessage].toResource
      tracer <- tracesTestkit.tracerProvider.get("io.nats.scala").toResource
      _ <- dispatcher.subscribe(
        "subject.*",
        defaultQueueName,
        {
          case message: TelemetryMessage =>
            tracer.span("complete").surround(deferred.complete(message)).void
          case _ => IO.raiseError(new IllegalArgumentException("TelemetryMessage expected"))
        }
      )
    } yield (tracer, deferred)).use { case (tracer, deferred) =>
      for {
        _ <- tracer.rootSpan("root").surround(connection.publish(subject, defaultData))
        message <- deferred.get
        spans <- tracesTestkit.finishedSpans.iterateUntil(_.size === 4)
      } yield {
        assert(message.spanContext.isDefined)
        assertEquals(
          spans,
          SpanTree("root", SpanTree("subject.1 publish", SpanTree("subject.1 process", SpanTree("complete"))))
        )
      }
    }
  }

  test("dispatcher(handler).subscribe(subject)") {
    val _fixtures = fixtures()
    import _fixtures.*;
    import natsFixtures.*;

    val subject: Subject.Single = "subject.1"

    (for {
      deferred <- Deferred[IO, TelemetryMessage].toResource
      tracer <- tracesTestkit.tracerProvider.get("io.nats.scala").toResource
      dispatcher <- connection.dispatcher {
        case message: TelemetryMessage =>
          tracer.span("complete").surround(deferred.complete(message)).void
        case _ => IO.raiseError(new IllegalArgumentException("TelemetryMessage expected"))
      }
      _ <- dispatcher.subscribe("subject.*")
    } yield (tracer, deferred)).use { case (tracer, deferred) =>
      for {
        _ <- tracer.rootSpan("root").surround(connection.publish(subject, defaultData))
        message <- deferred.get
        spans <- tracesTestkit.finishedSpans.iterateUntil(_.size === 4)
      } yield {
        assert(message.spanContext.isDefined)
        assertEquals(
          spans,
          SpanTree("root", SpanTree("subject.1 publish", SpanTree("subject.1 process", SpanTree("complete"))))
        )
      }
    }
  }

  test("dispatcher(handler).subscribe(subject, queueName)") {
    val _fixtures = fixtures()
    import _fixtures.*;
    import natsFixtures.*;

    val subject: Subject.Single = "subject.1"

    (for {
      deferred <- Deferred[IO, TelemetryMessage].toResource
      tracer <- tracesTestkit.tracerProvider.get("io.nats.scala").toResource
      dispatcher <- connection.dispatcher {
        case message: TelemetryMessage =>
          tracer.span("complete").surround(deferred.complete(message)).void
        case _ => IO.raiseError(new IllegalArgumentException("TelemetryMessage expected"))
      }
      _ <- dispatcher.subscribe("subject.*", defaultQueueName)
    } yield (tracer, deferred)).use { case (tracer, deferred) =>
      for {
        _ <- tracer.rootSpan("root").surround(connection.publish(subject, defaultData))
        message <- deferred.get
        spans <- tracesTestkit.finishedSpans.iterateUntil(_.size === 4)
      } yield {
        assert(message.spanContext.isDefined)
        assertEquals(
          spans,
          SpanTree("root", SpanTree("subject.1 publish", SpanTree("subject.1 process", SpanTree("complete"))))
        )
      }
    }
  }

}
