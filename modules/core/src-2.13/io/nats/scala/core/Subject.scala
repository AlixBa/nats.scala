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

object Subject {

  final case class Single(value: String)
  object Single {
    implicit def str2s(value: String): Single = Single(value)
    implicit def s2w(single: Single): Wildcard = Wildcard(single.value)
    def assume(value: String): Single = Single(value)
  }

  final case class Wildcard(value: String)
  object Wildcard {
    implicit def str2w(value: String): Wildcard = Wildcard(value)
    def assume(value: String): Wildcard = Wildcard(value)
  }

}
