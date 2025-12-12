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

import cats.syntax.option.catsSyntaxOptionId
import cats.syntax.option.none
import munit.AnyFixture
import munit.CatsEffectSuite
import munit.catseffect.IOFixture

import scala.concurrent.duration.DurationInt

class ReplySuite extends CatsEffectSuite {

  val fixtures: IOFixture[NatsFixtures] = ResourceSuiteLocalFixture(
    "fixtures",
    NatsFixtures.resource
  )

  override def munitFixtures: Seq[AnyFixture[?]] = List(fixtures)

  test("reply(message, data)") {
    val _fixtures = fixtures()
    import _fixtures.*

    for {
      _ <- connection.reply(Message(nullSubject, replyTo = synchronousSubject.some), defaultData)
      message <- synchronousSubscription.next(100.millis)
      _ <- connection.reply(Message(nullSubject, replyTo = none), defaultData)
      errorF <- synchronousSubscription.next(100.millis).attempt
      error = errorF.left.map(_.getMessage())
    } yield {
      assertEquals(message.subject, synchronousSubject)
      assertEquals(message.replyTo, none)
      assertEquals(message.headers, Headers.empty)
      assertEquals(message.data.toList, defaultData.toList)
      assertEquals(error, Left("Timed out while waiting for a message"))
    }
  }

  test("reply(subject, headers, data)") {
    val _fixtures = fixtures()
    import _fixtures.*

    val headers = Headers.empty.add("header", "value")

    for {
      _ <- connection.reply(Message(nullSubject, replyTo = synchronousSubject.some), headers, defaultData)
      message <- synchronousSubscription.next(100.millis)
      _ <- connection.reply(Message(nullSubject, replyTo = none), headers, defaultData)
      errorF <- synchronousSubscription.next(100.millis).attempt
      error = errorF.left.map(_.getMessage())
    } yield {
      assertEquals(message.subject, synchronousSubject)
      assertEquals(message.replyTo, none)
      assertEquals(message.headers, headers)
      assertEquals(message.data.toList, defaultData.toList)
      assertEquals(error, Left("Timed out while waiting for a message"))
    }
  }

  test("reply(subject, replyTo, data)") {
    val _fixtures = fixtures()
    import _fixtures.*

    val replyTo: Subject.Single = "replyTo"

    for {
      _ <- connection.reply(Message(nullSubject, replyTo = synchronousSubject.some), replyTo, defaultData)
      message <- synchronousSubscription.next(100.millis)
      _ <- connection.reply(Message(nullSubject, replyTo = none), replyTo, defaultData)
      errorF <- synchronousSubscription.next(100.millis).attempt
      error = errorF.left.map(_.getMessage())
    } yield {
      assertEquals(message.subject, synchronousSubject)
      assertEquals(message.replyTo, replyTo.some)
      assertEquals(message.headers, Headers.empty)
      assertEquals(message.data.toList, defaultData.toList)
      assertEquals(error, Left("Timed out while waiting for a message"))
    }
  }

  test("reply(subject, replyTo, headers, data)") {
    val _fixtures = fixtures()
    import _fixtures.*

    val replyTo: Subject.Single = "replyTo"
    val headers = Headers.empty.add("header", "value")

    for {
      _ <- connection.reply(Message(nullSubject, replyTo = synchronousSubject.some), replyTo, headers, defaultData)
      message <- synchronousSubscription.next(100.millis)
      _ <- connection.reply(Message(nullSubject, replyTo = none), replyTo, headers, defaultData)
      errorF <- synchronousSubscription.next(100.millis).attempt
      error = errorF.left.map(_.getMessage())
    } yield {
      assertEquals(message.subject, synchronousSubject)
      assertEquals(message.replyTo, replyTo.some)
      assertEquals(message.headers, headers)
      assertEquals(message.data.toList, defaultData.toList)
      assertEquals(error, Left("Timed out while waiting for a message"))
    }
  }

}
