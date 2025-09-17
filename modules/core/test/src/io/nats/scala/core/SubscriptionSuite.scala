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

import cats.syntax.option.none
import munit.AnyFixture
import munit.CatsEffectSuite
import munit.catseffect.IOFixture

import scala.concurrent.duration.DurationInt

class SubscriptionSuite extends CatsEffectSuite {

  val fixtures: IOFixture[NatsFixtures] = ResourceSuiteLocalFixture(
    "fixtures",
    NatsFixtures.resource
  )

  override def munitFixtures: Seq[AnyFixture[?]] = List(fixtures)

  test("subscribe(subject, queueName).next()") {
    val _fixtures = fixtures()
    import _fixtures.*

    val subjectW: Subject.Wildcard = "subject.>"
    val subjectS: Subject.Single = "subject.1"

    (for {
      subscription1 <- connection.subscribe(subjectW, defaultQueueName)
      subscription2 <- connection.subscribe(subjectW, defaultQueueName)
    } yield (subscription1, subscription2)).use { case (subscription1, subscription2) =>
      for {
        _ <- connection.publish(subjectS, defaultData)
        m1m2 <- (
          subscription1.next(100.millis).attempt,
          subscription2.next(100.millis).attempt
        ).parMapN((m1, m2) => (m1, m2))
        (message1, message2) = m1m2
      } yield {
        val message = (message1, message2) match {
          case (Right(message), Left(_)) => message
          case (Left(_), Right(message)) => message
          case _                         => fail("One message should have been received")
        }
        assertEquals(message.subject, subjectS)
        assertEquals(message.replyTo, none)
        assertEquals(message.headers, Headers.empty)
        assertEquals(message.data.toList, defaultData.toList)
      }
    }
  }

  test("setPendingLimits") {
    val _fixtures = fixtures()
    import _fixtures.*

    connection.subscribe("subject").use { subscription =>
      for {
        _ <- subscription.setPendingLimits(1, 2)
      } yield {
        assertEquals(subscription.getPendingMessageLimit, 1L)
        assertEquals(subscription.getPendingByteLimit, 2L)
      }
    }
  }

}
