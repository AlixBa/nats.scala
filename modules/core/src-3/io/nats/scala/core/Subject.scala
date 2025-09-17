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
import io.github.iltotore.iron.RuntimeConstraint
import io.github.iltotore.iron.constraint.string.Match
import io.github.iltotore.iron.macros

// https://github.com/nats-io/nats-architecture-and-design/blob/709a2878a72c96e40b7ae2b3703ff7fdefd0eafb/adr/ADR-6.md
@SuppressWarnings(Array("DisableSyntax.asInstanceOf"))
object Subject {

  // term: [\\x21-\\x29\\x2B-\\x2D\\x2F-\\x3D\\x3F-\\x7E]+
  // single: ^($term+)(\\.$term+)*$$
  private type SingleMatch =
    Match["^([\\x21-\\x29\\x2B-\\x2D\\x2F-\\x3D\\x3F-\\x7E]+)(\\.[\\x21-\\x29\\x2B-\\x2D\\x2F-\\x3D\\x3F-\\x7E]+)*$$"]

  type Single = Single.T
  object Single extends RefinedType[String, SingleMatch] {
    given Conversion[Single, Wildcard] = single => Wildcard.assume(single.value)
    implicit inline def str2s(
        inline value: String
    )(using inline constraint: Constraint[String, SingleMatch]): Single =
      inline if (macros.isIronType[String, SingleMatch]) { value.asInstanceOf[Single] }
      else {
        macros.assertCondition(value, constraint.test(value), constraint.message)
        value.asInstanceOf[Single]
      }
  }

  // term: [\\x21-\\x29\\x2B-\\x2D\\x2F-\\x3D\\x3F-\\x7E]+
  // wildcard: ^($term+)(\\.$term+|\\*)*(\\.>)?$$
  private type WildcardMatch = Match[
    "^([\\x21-\\x29\\x2B-\\x2D\\x2F-\\x3D\\x3F-\\x7E]+)(\\.([\\x21-\\x29\\x2B-\\x2D\\x2F-\\x3D\\x3F-\\x7E]+|\\*))*(\\.>)?$$"
  ]

  type Wildcard = Wildcard.T
  object Wildcard extends RefinedType[String, WildcardMatch] {
    implicit inline def str2s(
        inline value: String
    )(using inline constraint: Constraint[String, WildcardMatch]): Wildcard =
      inline if (macros.isIronType[String, WildcardMatch]) { value.asInstanceOf[Wildcard] }
      else {
        macros.assertCondition(value, constraint.test(value), constraint.message)
        value.asInstanceOf[Wildcard]
      }
  }

}
