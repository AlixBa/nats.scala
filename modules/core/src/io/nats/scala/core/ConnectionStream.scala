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

import cats.effect.Concurrent
import cats.syntax.applicative.catsSyntaxApplicativeId
import cats.syntax.applicativeError.catsSyntaxApplicativeError
import cats.syntax.flatMap.toFlatMapOps
import cats.syntax.functor.toFunctorOps
import cats.syntax.option.catsSyntaxOptionId
import cats.syntax.option.none
import fs2.Stream
import fs2.concurrent.Channel
import io.nats.scala.core.Connection
import io.nats.scala.core.Message
import io.nats.scala.core.QueueName
import io.nats.scala.core.Subject.Wildcard

/** Stream API on top of a [[io.nats.client.Connection]] */
trait ConnectionStream[F[_]] {

  /** Creates an infinite stream and its cancelling action for a subject. The cancelling action can be used to drain the
    * stream and close the underlying dispatcher. If the stream is closed without the cancelling action, buferred
    * messages on the dispatcher will be dropped.
    *
    * @param subject
    *   the subject to listen to
    * @return
    *   the stream and its cancelling action
    */
  def stream(subject: Subject.Wildcard): F[(Stream[F, Message], F[Unit])]

  /** Creates an infinite stream and its cancelling action for a subject with a queue. The cancelling action can be used
    * to drain the stream and close the underlying dispatcher. If the stream is closed without the cancelling action,
    * buferred messages on the dispatcher will be dropped.
    *
    * @param subject
    *   the subject to listen to
    * @return
    *   the stream and its cancelling action
    */
  def stream(subject: Subject.Wildcard, queueName: QueueName): F[(Stream[F, Message], F[Unit])]

}

object ConnectionStream {

  private[nats] def apply[F[_]: Concurrent](
      connection: Connection[F]
  ): ConnectionStream[F] = new ConnectionStream[F] {
    override def stream(subject: Wildcard): F[(Stream[F, Message], F[Unit])] =
      stream(subject, none)

    override def stream(subject: Wildcard, queueName: QueueName): F[(Stream[F, Message], F[Unit])] =
      stream(subject, queueName.some)

    def stream(subject: Wildcard, queueName: Option[QueueName]): F[(Stream[F, Message], F[Unit])] =
      for {
        channel <- Channel.synchronous[F, Message]
        dispatcherAndDrain <- connection.dispatcher(message => channel.send(message).void).allocated
        (dispatcher, drain) = dispatcherAndDrain
        // allocate, without considering the cleanup,
        // it will be drained by the dispatcher above
        _ <- (queueName match {
          case Some(queueName) => dispatcher.subscribe(subject, queueName)
          case None            => dispatcher.subscribe(subject)
        }).allocated
      } yield {
        val safeDrain = drain.recoverWith {
          case exception: IllegalStateException if exception.getMessage().equals("Consumer is closed") => ().pure
          // TODO: add a log in case we're not closing properly
        }

        // since we might have pending elements in the buffer of the dispatcher
        // we need to consume them until there is nothing left to consume
        def drainChannel: F[Unit] = channel.stream.compile.count.flatMap {
          case 0 => ().pure
          case _ => drainChannel
        }

        (
          channel.stream.onFinalize(
            for {
              // we close the channel first to stop receiving incoming messages
              // from the dispatcher (channel.send == Left(Closed)).
              // It's either already closed because the stream terminated thanks
              // to the cancel action, or we want to ignore buffered messages
              // because the stream isn't processing elements anymore (such as
              // stream.take(n), or stream.head)
              _ <- channel.close
              _ <- drainChannel
              _ <- safeDrain
            } yield ()
          ),
          safeDrain.flatTap(_ => channel.close)
        )
      }

  }

}
