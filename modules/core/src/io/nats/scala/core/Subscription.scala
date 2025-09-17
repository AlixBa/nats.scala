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

import cats.effect.kernel.Sync
import cats.syntax.applicative.catsSyntaxApplicativeId
import cats.syntax.flatMap.toFlatMapOps
import io.nats.client.Subscription as JSubscription
import io.nats.scala.core.Message

import java.util.concurrent.TimeoutException
import scala.concurrent.duration.FiniteDuration
import scala.jdk.DurationConverters.*

/** Typed Scala API on top of a [[io.nats.client.Subscription]]. Refer to the Nats documentation for more information.
  */
trait Subscription[F[_]] {
  def setPendingLimits(messages: Long, bytes: Long): F[Unit]

  def getPendingMessageLimit: Long
  def getPendingByteLimit: Long
}

object Subscription {

  trait Synchronous[F[_]] extends Subscription[F] {
    def next(timeout: FiniteDuration): F[Message]
  }

  object Synchronous {
    private[nats] def apply[F[_]: Sync](subscription: JSubscription): Synchronous[F] = new Synchronous[F] {
      val delegate: Subscription[F] = Subscription[F](subscription)

      override def setPendingLimits(messages: Long, bytes: Long): F[Unit] =
        delegate.setPendingLimits(messages, bytes)

      override def getPendingMessageLimit: Long =
        delegate.getPendingMessageLimit

      override def getPendingByteLimit: Long =
        delegate.getPendingByteLimit

      override def next(timeout: FiniteDuration): F[Message] =
        Sync[F]
          .blocking(subscription.nextMessage(timeout.toJava))
          .flatMap(message =>
            Option(message) match {
              case Some(message) => Message.asScala(message).pure[F]
              case None          =>
                val message = "Timed out while waiting for a message"
                Sync[F].raiseError(new TimeoutException(message))
            }
          )
    }
  }

  private[nats] def apply[F[_]: Sync](subscription: JSubscription): Subscription[F] = new Subscription[F] {
    override def setPendingLimits(messages: Long, bytes: Long): F[Unit] =
      Sync[F].delay(subscription.setPendingLimits(messages, bytes))

    override def getPendingMessageLimit: Long =
      subscription.getPendingMessageLimit()

    override def getPendingByteLimit: Long =
      subscription.getPendingByteLimit()
  }

}
