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
import io.nats.scala.core.Iron.given_RuntimeConstraint_CIString_C
import org.typelevel.ci.CIString

// https://github.com/nats-io/nats-architecture-and-design/blob/709a2878a72c96e40b7ae2b3703ff7fdefd0eafb/adr/ADR-4.md
type HeaderName = HeaderName.T

@SuppressWarnings(Array("DisableSyntax.asInstanceOf"))
object HeaderName extends RefinedType[CIString, HeaderName.HeaderNameMatch] {
  private type HeaderNameMatch = Match["[!-9;-~]+"]

  implicit inline def str2hn(
      inline value: String
  )(using inline constraint: Constraint[String, HeaderNameMatch]): HeaderName =
    inline if (macros.isIronType[String, HeaderNameMatch]) { CIString(value).asInstanceOf[HeaderName] }
    else {
      macros.assertCondition(value, constraint.test(value), constraint.message)
      CIString(value).asInstanceOf[HeaderName]
    }

}
