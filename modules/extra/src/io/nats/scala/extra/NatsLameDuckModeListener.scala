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

import io.nats.client.Connection
import io.nats.client.ConnectionListener
import io.nats.client.ForceReconnectOptions

import java.time.Duration as JDuration
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration

object NatsLameDuckModeListener {

  def create(): ConnectionListener =
    create(10.seconds)

  /** Creates a [[io.nats.client.ConnectionListener]] which listens to LAME_DUCK events. Starts by flushing the current
    * connection up to 75% of the grace period of the server then closes the connection and reconnect to any available
    * server in the original list.
    */
  def create(serverGracePeriod: FiniteDuration): ConnectionListener = new ConnectionListener {
    override def connectionEvent(conn: Connection, `type`: ConnectionListener.Events): Unit =
      onEvent(conn, `type`, serverGracePeriod)
  }

  def merge(listener: ConnectionListener): ConnectionListener =
    merge(listener, 10.seconds)

    /** Creates a [[io.nats.client.ConnectionListener]] which listens to LAME_DUCK events. Starts by flushing the
      * current connection up to 75% of the grace period of the server then closes the connection and reconnect to any
      * available server in the original list.
      */
  def merge(
      listener: ConnectionListener,
      serverGracePeriod: FiniteDuration
  ): ConnectionListener = new ConnectionListener {
    override def connectionEvent(conn: Connection, `type`: ConnectionListener.Events): Unit = {
      listener.connectionEvent(conn, `type`)
      onEvent(conn, `type`, serverGracePeriod)
    }
  }

  private def onEvent(
      conn: Connection,
      `type`: ConnectionListener.Events,
      serverGracePeriod: FiniteDuration
  ): Unit =
    if (ConnectionListener.Events.LAME_DUCK.equals(`type`)) {
      conn.forceReconnect(
        ForceReconnectOptions
          .builder()
          .forceClose()
          // let's allow 75% of the grace period to properly flush
          // so we can properly reconnect before the server evicts
          // this running connection, which should be 8s by default.
          .flush(JDuration.ofSeconds(serverGracePeriod.toSeconds * 3 / 4))
          .build()
      )
    }

}
