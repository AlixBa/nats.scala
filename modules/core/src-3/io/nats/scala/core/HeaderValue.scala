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

import io.github.iltotore.iron.Constraint
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.string.Match
import io.github.iltotore.iron.macros

// https://github.com/nats-io/nats-architecture-and-design/blob/709a2878a72c96e40b7ae2b3703ff7fdefd0eafb/adr/ADR-4.md
type HeaderValue = HeaderValue.T

@SuppressWarnings(Array("DisableSyntax.asInstanceOf"))
object HeaderValue extends RefinedType[String, HeaderValue.HeaderValueMatch] {
  private type HeaderValueMatch = Match["^[\\x00-\\x09\\x0B-\\x0C\\x0E-\\x7F]*$"]

  implicit inline def str2hv(
      inline value: String
  )(using inline constraint: Constraint[String, HeaderValueMatch]): HeaderValue =
    inline if (macros.isIronType[String, HeaderValueMatch]) { value.asInstanceOf[HeaderValue] }
    else {
      macros.assertCondition(value, constraint.test(value), constraint.message)
      value.asInstanceOf[HeaderValue]
    }

}
