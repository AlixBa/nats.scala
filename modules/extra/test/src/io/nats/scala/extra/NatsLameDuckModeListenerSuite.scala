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

package io.nats.scala.extra

import cats.effect.IO
import io.nats.client.ConnectionListener.Events
import io.nats.scala.core.NatsFixtures
import munit.AnyFixture
import munit.CatsEffectSuite
import munit.catseffect.IOFixture

class NatsLameDuckModeListenerSuite extends CatsEffectSuite {

  val fixtures: IOFixture[NatsFixtures] = ResourceSuiteLocalFixture(
    "fixtures",
    NatsFixtures.resource
  )

  override def munitFixtures: Seq[AnyFixture[?]] = List(fixtures)

  test("reconnect on LDM") {
    val _fixtures = fixtures()
    import _fixtures.*

    val listener = NatsLameDuckModeListener.create()

    for {
      _ <- IO.delay(jconnection.addConnectionListener(listener))
      before <- IO.delay(jconnection.getStatistics().getReconnects())
      _ <- IO.delay(listener.connectionEvent(jconnection, Events.LAME_DUCK))
      after <- IO.delay(jconnection.getStatistics().getReconnects()).iterateWhile(_ <= before)
    } yield assertEquals(after, before + 1)
  }

}
