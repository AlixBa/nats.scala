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

package io.nats.scala.otel

import io.nats.client.Connection
import io.nats.client.Consumer
import io.nats.client.Message as JMessage
import io.nats.scala.core.Message
import io.nats.scala.otel.log.LogContext

package object instances {

  object log {

    implicit val clg: LogContext[Connection] = LogContext.of(conn =>
      Map(
        "connection.name" -> Option(conn.getOptions().getConnectionName()).getOrElse("<null>"),
        "connection.clientId" -> conn.getServerInfo().getClientId().toString(),
        "connection.serverId" -> conn.getServerInfo().getServerId()
      )
    )

    implicit val clg2: LogContext[Consumer] = LogContext.of(cons => Map("consumer" -> cons.hashCode().toString()))

    implicit val mlg: LogContext[Message] = LogContext.of(msg =>
      Map(
        "message.subject" -> msg.subject.value,
        "message.replyTo" -> msg.replyTo.map(_.value).getOrElse("<null>"),
        "message.size" -> msg.data.length.toString()
      )
    )

    implicit val mlg2: LogContext[JMessage] = LogContext.of(msg =>
      Map(
        "message.subject" -> msg.getSubject(),
        "message.replyTo" -> Option(msg.getReplyTo()).getOrElse("<null>"),
        "message.size" -> Option(msg.getData()).map(_.size).getOrElse(0).toString()
      )
    )

  }

}
