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

import cats.Semigroup
import io.nats.client.Connection
import io.nats.client.ConnectionListener

import java.lang

package object instances {

  object connectionlistener {

    implicit val semigroup: Semigroup[ConnectionListener] =
      Semigroup.instance[ConnectionListener]((a, b) =>
        new ConnectionListener {
          override def connectionEvent(
              conn: Connection,
              `type`: ConnectionListener.Events,
              time: lang.Long,
              uriDetails: String
          ): Unit = {
            a.connectionEvent(conn, `type`, time, uriDetails)
            b.connectionEvent(conn, `type`, time, uriDetails)
          }

          override def connectionEvent(
              conn: Connection,
              `type`: ConnectionListener.Events
          ): Unit = {
            a.connectionEvent(conn, `type`)
            b.connectionEvent(conn, `type`)
          }
        }
      )

  }

}
