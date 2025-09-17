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
import cats.syntax.either.catsSyntaxEitherId
import cats.syntax.option.catsSyntaxOptionId
import cats.syntax.option.none
import munit.AnyFixture
import munit.CatsEffectSuite
import munit.catseffect.IOFixture

import java.util.concurrent.CancellationException
import scala.concurrent.duration.DurationInt

class RequestSuite extends CatsEffectSuite {

  val fixtures: IOFixture[NatsFixtures] = ResourceSuiteLocalFixture(
    "fixtures",
    NatsFixtures.resource
  )

  override def munitFixtures: Seq[AnyFixture[?]] = List(fixtures)

  test("request(subject, data)") {
    val _fixtures = fixtures()
    import _fixtures.*

    for {
      message <- connection.request(echoSubject, defaultData)
    } yield {
      assertEquals(message.subject.value.split("\\.").headOption, "_INBOX".some)
      assertEquals(message.replyTo, none)
      assertEquals(message.headers, Headers.empty)
      assertEquals(message.data.toList, defaultData.toList)
    }
  }

  test("request(subject, data, timeout)") {
    val _fixtures = fixtures()
    import _fixtures.*

    for {
      message1 <- connection.request(echoSubject, defaultData, 100.millis)
      message2 <- connection.request(nullSubject, defaultData, 100.millis).attempt
      message2 <- IO.delay(message2.left.map(_.getClass()))
    } yield {
      assertEquals(message1.subject.value.split("\\.").headOption, "_INBOX".some)
      assertEquals(message1.replyTo, none)
      assertEquals(message1.headers, Headers.empty)
      assertEquals(message1.data.toList, defaultData.toList)
      assertEquals(message2, classOf[CancellationException].asLeft)
    }
  }

  test("request(subject, headers, data)") {
    val _fixtures = fixtures()
    import _fixtures.*

    val headers = Headers.empty.add("header", "value")

    for {
      message <- connection.request(echoSubject, headers, defaultData)
    } yield {
      assertEquals(message.subject.value.split("\\.").headOption, "_INBOX".some)
      assertEquals(message.replyTo, none)
      assertEquals(message.headers, headers)
      assertEquals(message.data.toList, defaultData.toList)
    }
  }

  test("request(subject, headers, data, timeout)") {
    val _fixtures = fixtures()
    import _fixtures.*

    val headers = Headers.empty.add("header", "value")

    for {
      message1 <- connection.request(echoSubject, headers, defaultData, 100.millis)
      message2 <- connection.request(nullSubject, headers, defaultData, 100.millis).attempt
      message2 <- IO.delay(message2.left.map(_.getClass()))
    } yield {
      assertEquals(message1.subject.value.split("\\.").headOption, "_INBOX".some)
      assertEquals(message1.replyTo, none)
      assertEquals(message1.headers, headers)
      assertEquals(message1.data.toList, defaultData.toList)
      assertEquals(message2, classOf[CancellationException].asLeft)
    }
  }

}
