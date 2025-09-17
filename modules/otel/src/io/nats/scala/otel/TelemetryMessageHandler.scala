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

import cats.FlatMap
import cats.mtl.Local
import cats.syntax.flatMap.toFlatMapOps
import cats.syntax.functor.toFunctorOps
import io.nats.scala.core.MessageHandler
import io.opentelemetry.context.Context as JContext
import org.typelevel.otel4s.oteljava.context.Context
import org.typelevel.otel4s.trace.Tracer

private object TelemetryMessageHandler {

  private[nats] def apply[F[_]: FlatMap: Tracer](
      handler: MessageHandler[F]
  )(implicit local: Local[F, Context]): MessageHandler[F] = message =>
    Local[F, Context].scope(for {
      context <- Tracer[F].currentSpanContext
      _ <- handler(TelemetryMessage(message, context))
    } yield ())(Context.wrap(JContext.current()))

}
