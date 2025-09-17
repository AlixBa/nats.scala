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
import cats.effect.kernel.Resource
import io.nats.client.Options as JOptions
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName

final case class NatsFixtures(
    connection: Connection[IO],
    synchronousSubject: Subject.Single,
    synchronousSubscription: Subscription.Synchronous[IO],
    echoSubject: Subject.Single,
    echoWildcardSubject: Subject.Wildcard,
    nullSubject: Subject.Single,
    defaultQueueName: QueueName,
    defaultData: Array[Byte]
)

object NatsFixtures {

  val resource: Resource[IO, NatsFixtures] =
    resource(natsUrl => Nats.connect(new JOptions.Builder().server(natsUrl).build()))

  def resource(
      connection: String => Resource[IO, Connection[IO]]
  ): Resource[IO, NatsFixtures] = {
    val synchronousSubject: Subject.Single = "synchronous"
    val echoSubject: Subject.Single = "echo.1"
    val echoWildcardSubject: Subject.Wildcard = "echo.>"
    val nullSubject: Subject.Single = "null"
    val defaultQueueName: QueueName = "queueName"
    val defaultData: Array[Byte] = "data".getBytes()

    val acquireContainer: IO[GenericContainer[?]] = IO
      .delay(new GenericContainer(DockerImageName.parse("nats:2.11.9-scratch")): GenericContainer[?])
      .map[GenericContainer[?]](_.withExposedPorts(4222))
      .flatTap(container => IO.delay(container.start()))

    val echoMessageHandler: Connection[IO] => MessageHandler[IO] = connection =>
      message =>
        message.replyTo match {
          case Some(replyTo) => connection.publish(replyTo, message.headers, message.data)
          case None          => IO.unit
        }

    for {
      container <- Resource.make(acquireContainer)(container => IO.delay(container.close()))
      natsUrl = s"nats://${container.getHost()}:${container.getMappedPort(4222)}"
      connection <- connection(natsUrl)
      synchronousSubscription <- connection.subscribe(synchronousSubject)
      dispatcher <- connection.dispatcher(echoMessageHandler(connection))
      _ <- dispatcher.subscribe(echoWildcardSubject)
    } yield NatsFixtures(
      connection = connection,
      synchronousSubject = synchronousSubject,
      synchronousSubscription = synchronousSubscription,
      echoSubject = echoSubject,
      echoWildcardSubject = echoWildcardSubject,
      nullSubject = nullSubject,
      defaultQueueName = defaultQueueName,
      defaultData = defaultData
    )
  }

}
