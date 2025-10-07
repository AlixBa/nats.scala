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

import cats.Show
import io.nats.client.Connection
import io.nats.client.Consumer
import io.nats.client.Message

package object instances {

  // TODO: move to a separate file
  object show {

    implicit val conns: Show[Connection] = Show.show[Connection] { connection =>
      s"Connection(clientId=${connection.getServerInfo().getClientId()})"
    }

    implicit val conss: Show[Consumer] = Show.show[Consumer] { consumer =>
      s"Consumer(${consumer.hashCode()})"
    }

    implicit val ms: Show[Message] = Show.show[Message] { message =>
      s"Message(subject=${message.getSubject()})"
    }

  }

}
