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

import cats.effect.Async
import cats.effect.Resource
import cats.effect.std.Dispatcher as CEDispatcher
import cats.syntax.functor.toFunctorOps
import cats.syntax.option.catsSyntaxOptionId
import cats.syntax.option.none
import io.nats.client.Dispatcher as JDispatcher
import io.nats.client.Message as JMessage
import io.nats.client.MessageHandler as JMessageHandler
import io.nats.scala.core.MessageHandler
import io.nats.scala.core.QueueName
import io.nats.scala.core.Subject.Wildcard
import io.nats.scala.core.Subscription

/** Typed Scala API on top of a [[io.nats.client.Dispatcher]]. Refer to the Nats documentation for more information.
  *
  * The MessageHandler is executed synchronously by the NATS Dispatcher. This can be changed at the
  * [[io.nats.client.Options]] level when creating the connection.
  */
trait Dispatcher[F[_]] {

  def subscribe(
      subject: Subject.Wildcard,
      handler: MessageHandler[F]
  ): Resource[F, Subscription[F]]

  def subscribe(
      subject: Subject.Wildcard,
      queueName: QueueName,
      handler: MessageHandler[F]
  ): Resource[F, Subscription[F]]

}

object Dispatcher {

  trait WithHandler[F[_]] extends Dispatcher[F] {
    def subscribe(subject: Subject.Wildcard): Resource[F, Unit]
    def subscribe(subject: Subject.Wildcard, queueName: QueueName): Resource[F, Unit]
  }

  object WithHandler {
    private[nats] def apply[F[_]: Async](
        dispatcher: JDispatcher
    ): WithHandler[F] = new WithHandler[F] {
      val A: Async[F] = Async[F]
      import A.*
      val delegate: Dispatcher[F] = Dispatcher[F](dispatcher)

      override def subscribe(subject: Wildcard, handler: MessageHandler[F]): Resource[F, Subscription[F]] =
        delegate.subscribe(subject, handler)

      override def subscribe(
          subject: Wildcard,
          queueName: QueueName,
          handler: MessageHandler[F]
      ): Resource[F, Subscription[F]] = delegate.subscribe(subject, queueName, handler)

      override def subscribe(subject: Wildcard): Resource[F, Unit] =
        subscribe(subject, none)

      override def subscribe(subject: Wildcard, queueName: QueueName): Resource[F, Unit] =
        subscribe(subject, queueName.some)

      def subscribe(subject: Wildcard, queueName: Option[QueueName]): Resource[F, Unit] =
        Resource
          .make(delay(queueName match {
            case Some(queueName) => dispatcher.subscribe(subject.value, queueName.value)
            case None            => dispatcher.subscribe(subject.value)
          }))(_ => delay(dispatcher.unsubscribe(subject.value)))
          .void
    }
  }

  private[nats] def apply[F[_]: Async](
      dispatcher: JDispatcher
  ): Dispatcher[F] = new Dispatcher[F] {
    val A: Async[F] = Async[F]
    import A.*

    override def subscribe(subject: Wildcard, handler: MessageHandler[F]): Resource[F, Subscription[F]] =
      subscribe(subject, none, handler)

    override def subscribe(
        subject: Wildcard,
        queueName: QueueName,
        handler: MessageHandler[F]
    ): Resource[F, Subscription[F]] =
      subscribe(subject, queueName.some, handler)

    def subscribe(
        subject: Wildcard,
        queueName: Option[QueueName],
        handler: MessageHandler[F]
    ): Resource[F, Subscription[F]] =
      for {
        ceDispatcher <- CEDispatcher.sequential[F](await = true)
        mh: JMessageHandler = (message: JMessage) => ceDispatcher.unsafeRunSync(handler(Message.asScala(message)))
        subscription <- Resource
          .make(delay(queueName match {
            case Some(queueName) => dispatcher.subscribe(subject.value, queueName.value, mh)
            case None            => dispatcher.subscribe(subject.value, mh)
          }))(subscription => blocking(dispatcher.unsubscribe(subscription)).void)
      } yield Subscription[F](subscription)

  }

}
