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

import cats.syntax.option.none
import io.nats.scala.core.Headers
import io.nats.scala.core.Message.Impl

/** All apply methods to create a message with or without default parameters */
private[core] trait MessageFactory {

  def apply(
      subject: Subject.Single,
      replyTo: Option[Subject.Single]
  ): Message = Impl(subject, replyTo, Headers.empty, Array.emptyByteArray)

  def apply(
      subject: Subject.Single,
      headers: Headers
  ): Message = Impl(subject, none, headers, Array.emptyByteArray)

  def apply(
      subject: Subject.Single,
      data: Array[Byte]
  ): Message = Impl(subject, none, Headers.empty, data)

  def apply(
      subject: Subject.Single,
      replyTo: Option[Subject.Single],
      headers: Headers
  ): Message = Impl(subject, replyTo, headers, Array.emptyByteArray)

  def apply(
      subject: Subject.Single,
      replyTo: Option[Subject.Single],
      data: Array[Byte]
  ): Message = Impl(subject, replyTo, Headers.empty, data)

  def apply(
      subject: Subject.Single,
      headers: Headers,
      data: Array[Byte]
  ): Message = Impl(subject, none, headers, data)

  def apply(
      subject: Subject.Single,
      replyTo: Option[Subject.Single],
      headers: Headers,
      data: Array[Byte]
  ): Message = Impl(subject, replyTo, headers, data)

}
