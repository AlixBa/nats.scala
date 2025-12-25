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

import cats.effect.Concurrent
import cats.syntax.applicativeError.catsSyntaxApplicativeErrorId
import cats.syntax.flatMap.toFlatMapOps
import cats.syntax.functor.toFunctorOps
import cats.syntax.traverse.toTraverseOps
import fs2.concurrent.Channel
import io.nats.scala.core.Headers
import io.nats.scala.core.Message
import io.nats.scala.core.QueueName
import io.nats.scala.core.Subject

import scala.util.matching.Regex

private[nats] trait InMemoryNatsServer[F[_]] {

  def subscribe(
      sid: Int,
      subject: Subject.Wildcard,
      queueName: Option[QueueName]
  ): F[Channel[F, Message]]

  def unsubscribe(sid: Int): F[Unit]

  def publish(
      subject: Subject.Single,
      replyTo: Option[Subject.Single],
      headers: Headers,
      data: Array[Byte]
  ): F[Unit]

}

private[nats] object InMemoryNatsServer {

  private[nats] case class Subscription[F[_]](
      sid: Int,
      subject: Subject.Wildcard,
      queueName: Option[QueueName],
      channel: Channel[F, Message]
  ) {
    val regex: Regex =
      subject.value.replace(".", "\\.").replace(">", "*").replace("*", ".*").r

    def matches(subject0: Subject.Single): Boolean =
      regex.matches(subject0.value)
  }

  private[nats] case class ServerState[F[_]](
      subscriptions: Map[Int, Subscription[F]]
  ) {
    def subscribe(
        sid: Int,
        subject: Subject.Wildcard,
        queueName: Option[QueueName],
        channel: Channel[F, Message]
    ): ServerState[F] = {
      val subscription = Subscription[F](sid, subject, queueName, channel)
      ServerState(subscriptions.updated(sid, subscription))
    }

    def unsubscribe(sid: Int): ServerState[F] =
      ServerState(subscriptions.removed(sid))

    def candidates(subject: Subject.Single): List[Subscription[F]] =
      subscriptions.values
        .filter(_.matches(subject))
        .groupBy(_.queueName.map(_.value))
        .toList
        .flatMap {
          case (None, subscriptions)    => subscriptions
          case (Some(_), subscriptions) => List(subscriptions.head)
        }
  }

  def instance[F[_]: Concurrent]: F[InMemoryNatsServer[F]] =
    Concurrent[F].ref(ServerState[F](Map.empty)).map { state =>
      new InMemoryNatsServer[F] {

        override def subscribe(
            sid: Int,
            subject: Subject.Wildcard,
            queueName: Option[QueueName]
        ): F[Channel[F, Message]] =
          state.get.flatMap {
            case state if state.subscriptions.contains(sid) =>
              val error = s"Subscription $sid exists already"
              new Exception(error).raiseError
            case _ =>
              Channel.synchronous[F, Message].flatMap { channel =>
                state.modify { state =>
                  (state.subscribe(sid, subject, queueName, channel), channel)
                }
              }
          }

        override def unsubscribe(sid: Int): F[Unit] =
          state.get.flatMap {
            case state if !state.subscriptions.contains(sid) =>
              val error = s"Subscription $sid doest not exist"
              new Exception(error).raiseError
            case _ => state.update(_.unsubscribe(sid))
          }

        override def publish(
            subject: Subject.Single,
            replyTo: Option[Subject.Single],
            headers: Headers,
            data: Array[Byte]
        ): F[Unit] = state.get.flatMap { state =>
          state
            .candidates(subject)
            .traverse(subscription => subscription.channel.send(Message(subject, replyTo, headers, data)))
            .void
        }

      }
    }

}
