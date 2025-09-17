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

package io.nats.scala.core

import cats.effect.IO
import cats.effect.kernel.Deferred
import cats.effect.kernel.Ref
import cats.syntax.option.none
import munit.AnyFixture
import munit.CatsEffectSuite
import munit.catseffect.IOFixture

import scala.concurrent.duration.DurationInt

class DispatcherSuite extends CatsEffectSuite {

  val fixtures: IOFixture[NatsFixtures] = ResourceSuiteLocalFixture(
    "fixtures",
    NatsFixtures.resource
  )

  override def munitFixtures: Seq[AnyFixture[?]] = List(fixtures)

  test("dispatcher().subscribe(subject, handler)") {
    val _fixtures = fixtures()
    import _fixtures.*

    val subject: Subject.Single = "subject.1"

    (for {
      dispatcher <- connection.dispatcher()
      deferred <- Deferred[IO, Message].toResource
      _ <- dispatcher.subscribe("subject.*", message => deferred.complete(message).void)
    } yield deferred).use { deferred =>
      for {
        _ <- connection.publish(subject, defaultData)
        message <- deferred.get
      } yield {
        assertEquals(message.subject, subject)
        assertEquals(message.replyTo, none)
        assertEquals(message.headers, Headers.empty)
        assertEquals(message.data.toList, defaultData.toList)
      }
    }
  }

  test("dispatcher().subscribe(subject, queueName, handler)") {
    val _fixtures = fixtures()
    import _fixtures.*

    val subject: Subject.Single = "subject.1"

    (for {
      dispatcher <- connection.dispatcher()
      deferred1 <- Deferred[IO, Message].toResource
      deferred2 <- Deferred[IO, Message].toResource
      _ <- dispatcher.subscribe("subject.*", defaultQueueName, message => deferred1.complete(message).void)
      _ <- dispatcher.subscribe("subject.*", defaultQueueName, message => deferred2.complete(message).void)
    } yield (deferred1, deferred2)).use { case (deferred1, deferred2) =>
      for {
        _ <- connection.publish(subject, defaultData)
        m1m2 <- (
          deferred1.get.timeout(100.millis).attempt,
          deferred2.get.timeout(100.millis).attempt
        ).parMapN((d1, d2) => (d1, d2))
        (message1, message2) = m1m2
      } yield {
        val message = (message1, message2) match {
          case (Right(message), Left(_)) => message
          case (Left(_), Right(message)) => message
          case _                         => fail("One message should have been received")
        }
        assertEquals(message.subject, subject)
        assertEquals(message.replyTo, none)
        assertEquals(message.headers, Headers.empty)
        assertEquals(message.data.toList, defaultData.toList)
      }
    }
  }

  test("dispatcher(handler).subscribe(subject)") {
    val _fixtures = fixtures()
    import _fixtures.*

    val subject: Subject.Single = "subject.1"

    (for {
      deferred <- Deferred[IO, Message].toResource
      dispatcher <- connection.dispatcher(message => deferred.complete(message).void)
      _ <- dispatcher.subscribe("subject.*")
    } yield deferred).use { deferred =>
      for {
        _ <- connection.publish(subject, defaultData)
        message <- deferred.get
      } yield {
        assertEquals(message.subject, subject)
        assertEquals(message.replyTo, none)
        assertEquals(message.headers, Headers.empty)
        assertEquals(message.data.toList, defaultData.toList)
      }
    }
  }

  test("dispatcher(handler).subscribe(subject, queueName)") {
    val _fixtures = fixtures()
    import _fixtures.*

    val subject: Subject.Single = "subject.1"

    (for {
      ref <- Ref.empty[IO, List[Message]].toResource
      dispatcher <- connection.dispatcher(message => ref.update(_ :+ message).void)
      _ <- dispatcher.subscribe("subject.*", defaultQueueName)
      _ <- dispatcher.subscribe("subject.*", defaultQueueName)
    } yield ref).use { ref =>
      for {
        _ <- connection.publish(subject, defaultData)
        messages <- ref.get.delayBy(100.millis)
      } yield {
        val message = messages match {
          case List(message) => message
          case _             => fail("One message should have been received")
        }
        assertEquals(message.subject, subject)
        assertEquals(message.replyTo, none)
        assertEquals(message.headers, Headers.empty)
        assertEquals(message.data.toList, defaultData.toList)
      }
    }
  }

}
