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

package io.nats.scala.testkit

import cats.effect.IO
import cats.effect.kernel.Resource
import cats.syntax.option.catsSyntaxOptionId
import io.nats.scala.core.Connection
import io.nats.scala.core.Headers
import io.nats.scala.core.Message
import io.nats.scala.core.Subject
import io.nats.scala.core.syntax.connection.toConnectionOps
import munit.AnyFixture
import munit.CatsEffectSuite
import munit.catseffect.IOFixture

import scala.concurrent.duration.DurationInt

class InMemoryConnectionSuite extends CatsEffectSuite {

  val fixtures: IOFixture[Connection[IO]] = ResourceSuiteLocalFixture(
    "fixtures",
    Resource.eval(InMemoryConnection.instance[IO])
  )

  override def munitFixtures: Seq[AnyFixture[?]] = List(fixtures)

  test("subscribe(subject, queueName)") {
    val connection = fixtures()
    import connection.*

    (for {
      s1 <- subscribe("subject.>", "queue")
      s2 <- subscribe("subject.>", "queue")
    } yield (s1, s2)).use { case (s1, s2) =>
      val subject: Subject.Single = "subject.1"
      val replyTo: Subject.Single = "replyTo"
      val headers = Headers.empty.add("name", "value")
      val data = "data".getBytes()

      for {
        _ <- publish("not-subject", replyTo, headers, data)
        _ <- publish(subject, replyTo, headers, data)
        m1m2 <- (
          s1.next(100.millis).attempt,
          s2.next(100.millis).attempt
        ).parMapN((m1, m2) => (m1, m2))
        (message1, message2) = m1m2
      } yield {
        val message = (message1, message2) match {
          case (Right(message), Left(_)) => message
          case (Left(_), Right(message)) => message
          case _                         => fail("One message should have been received")
        }

        assertEquals(message.subject, subject)
        assertEquals(message.replyTo, replyTo.some)
        assertEquals(message.headers, headers)
        assertEquals(message.data.toList, data.toList)
      }
    }
  }

  test("dispatcher()") {
    val connection = fixtures()
    import connection.*

    val reply = (data: String) => (msg: Message) => connection.reply(msg, data.getBytes())

    (for {
      dispatcher <- dispatcher(reply("default-handler"))
      _ <- dispatcher.subscribe("subject.1")
      _ <- dispatcher.subscribe("subject.2", reply("handler"))
      _ <- dispatcher.subscribe("subject.3", "queue", reply("handler-queue"))
      _ <- dispatcher.subscribe("subject.3", "queue", reply("handler-queue"))
    } yield ()).surround(for {
      msg1 <- request("subject.1", Array.emptyByteArray)
      msg2 <- request("subject.2", Array.emptyByteArray)
      msg3 <- request("subject.3", Array.emptyByteArray)
    } yield {
      assertEquals(msg1.data.toList, "default-handler".getBytes().toList)
      assertEquals(msg2.data.toList, "handler".getBytes().toList)
      assertEquals(msg3.data.toList, "handler-queue".getBytes().toList)
    })

  }
}
