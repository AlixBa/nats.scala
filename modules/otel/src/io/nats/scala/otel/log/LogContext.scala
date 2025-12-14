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

package io.nats.scala.otel.log

/** Typeclass to use along with Log4Cats which helps delegating how to transform a value into a Map[String, String] for
  * structured logging.
  */
trait LogContext[A] {
  def toContext(value: A): Map[String, String]
}

object LogContext {

  def of[A](f: A => Map[String, String]): LogContext[A] = new LogContext[A] {
    override def toContext(value: A): Map[String, String] = f(value)
  }

}
