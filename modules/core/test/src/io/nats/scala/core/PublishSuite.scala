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

class PublishSuite extends CatsEffectSuite {

  val fixtures: IOFixture[NatsFixtures] = ResourceSuiteLocalFixture(
    "fixtures",
    NatsFixtures.resource
  )

  override def munitFixtures: Seq[AnyFixture[?]] = List(fixtures)

  test("publish(subject, data)") {
    val _fixtures = fixtures()
    import _fixtures.*

    for {
      _ <- connection.publish(synchronousSubject, defaultData)
      message <- synchronousSubscription.next(100.millis)
    } yield {
      assertEquals(message.subject, synchronousSubject)
      assertEquals(message.replyTo, none)
      assertEquals(message.headers, Headers.empty.add("n", "v"))
      assertEquals(message.data.toList, defaultData.toList)
    }
  }

  test("publish(subject, headers, data)") {
    val _fixtures = fixtures()
    import _fixtures.*

    val headers = Headers.empty.add("header", "value")

    for {
      _ <- connection.publish(synchronousSubject, headers, defaultData)
      message <- synchronousSubscription.next(100.millis)
    } yield {
      assertEquals(message.subject, synchronousSubject)
      assertEquals(message.replyTo, none)
      assertEquals(message.headers, headers)
      assertEquals(message.data.toList, defaultData.toList)
    }
  }

  test("publish(subject, replyTo, data)") {
    val _fixtures = fixtures()
    import _fixtures.*

    val replyTo: Subject.Single = "replyTo"

    for {
      _ <- connection.publish(synchronousSubject, replyTo, defaultData)
      message <- synchronousSubscription.next(100.millis)
    } yield {
      assertEquals(message.subject, synchronousSubject)
      assertEquals(message.replyTo, replyTo.some)
      assertEquals(message.headers, Headers.empty)
      assertEquals(message.data.toList, defaultData.toList)
    }
  }

  test("publish(subject, replyTo, headers, data)") {
    val _fixtures = fixtures()
    import _fixtures.*

    val replyTo: Subject.Single = "replyTo"
    val headers = Headers.empty.add("header", "value")

    for {
      _ <- connection.publish(synchronousSubject, replyTo, headers, defaultData)
      message <- synchronousSubscription.next(100.millis)
    } yield {
      assertEquals(message.subject, synchronousSubject)
      assertEquals(message.replyTo, replyTo.some)
      assertEquals(message.headers, headers)
      assertEquals(message.data.toList, defaultData.toList)
    }
  }

}
