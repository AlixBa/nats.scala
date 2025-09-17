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
import cats.syntax.option.catsSyntaxOptionId
import munit.AnyFixture
import munit.CatsEffectSuite
import munit.catseffect.IOFixture

class StreamSuite extends CatsEffectSuite {

  val fixtures: IOFixture[NatsFixtures] = ResourceSuiteLocalFixture(
    "fixtures",
    NatsFixtures.resource
  )

  override def munitFixtures: Seq[AnyFixture[?]] = List(fixtures)

  test("stream(subject)") {
    val _fixtures = fixtures()
    import _fixtures.*

    val subject: Subject.Single = "subject.1"

    for {
      streamAndCancel <- connection.stream("subject.>")
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
      _ <- connection.request(subject, defaultData)
      _ <- cancel
      message <- fiber.joinWithNever
    } yield {
      assertEquals(message.subject, subject)
      assertEquals(message.replyTo.flatMap(_.value.split("\\.").headOption), "_INBOX".some)
      assertEquals(message.headers, Headers.empty)
      assertEquals(message.data.toList, defaultData.toList)
    }
  }

  test("stream(subject, queueName)") {
    val _fixtures = fixtures()
    import _fixtures.*

    val subject: Subject.Single = "subject"

    val replyTo = (message: Message) =>
      message.replyTo match {
        case Some(replyTo) => connection.publish(replyTo, defaultData)
        case None          => IO.raiseError(new Exception("replyTo expected"))
      }

    for {
      streamAndCancel1 <- connection.stream("subject", defaultQueueName)
      (stream1, cancel1) = streamAndCancel1
      streamAndCancel2 <- connection.stream("subject", defaultQueueName)
      (stream2, cancel2) = streamAndCancel2
      fiber1 <- stream1.evalTap(replyTo).compile.last.uncancelable.start
      fiber2 <- stream2.evalTap(replyTo).compile.last.uncancelable.start
      _ <- connection.request(subject, defaultData)
      _ <- cancel1
      _ <- cancel2
      message1 <- fiber1.joinWithNever
      message2 <- fiber2.joinWithNever
    } yield {
      val message = (message1, message2) match {
        case (Some(message), None) => message
        case (None, Some(message)) => message
        case _                     => fail("One message should have been received")
      }
      assertEquals(message.subject, subject)
      assertEquals(message.replyTo.flatMap(_.value.split("\\.").headOption), "_INBOX".some)
      assertEquals(message.headers, Headers.empty)
      assertEquals(message.data.toList, defaultData.toList)
    }
  }

  test("stream(subject).take(1)") {
    val _fixtures = fixtures()
    import _fixtures.*

    val subject: Subject.Single = "subject.1"

    for {
      streamAndCancel <- connection.stream("subject.>")
      (stream, _) = streamAndCancel
      fiber <- stream
        .evalTap(message =>
          message.replyTo match {
            case Some(replyTo) => connection.publish(replyTo, defaultData)
            case None          => IO.raiseError(new Exception("replyTo expected"))
          }
        )
        .take(1)
        .compile
        .lastOrError
        .uncancelable
        .start
      _ <- connection.request(subject, defaultData)
      message <- fiber.joinWithNever
    } yield {
      assertEquals(message.subject, subject)
      assertEquals(message.replyTo.flatMap(_.value.split("\\.").headOption), "_INBOX".some)
      assertEquals(message.headers, Headers.empty)
      assertEquals(message.data.toList, defaultData.toList)
    }
  }

}
