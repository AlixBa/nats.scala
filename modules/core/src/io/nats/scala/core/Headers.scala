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

import cats.syntax.option.catsSyntaxOptionId
import io.nats.client.impl.Headers as JHeaders
import org.typelevel.ci.CIString

import scala.jdk.CollectionConverters.ListHasAsScala
import scala.jdk.CollectionConverters.MapHasAsJava
import scala.jdk.CollectionConverters.SeqHasAsJava
import scala.jdk.CollectionConverters.SetHasAsScala

/** Typed Scala API on top of [[io.nats.client.impl.Headers]]. Refer to the Nats documentation for more information. */
final case class Headers(map: Map[HeaderName, List[HeaderValue]]) {

  def add(name: HeaderName, value: HeaderValue): Headers =
    copy(map = map.updatedWith(name) {
      case Some(list) => (list :+ value).some
      case None       => List(value).some
    })

  def add(name: HeaderName, values: HeaderValue*): Headers =
    copy(map = map.updatedWith(name) {
      case Some(list) => (list ++ values).some
      case None       => values.toList.some
    })

  def put(name: HeaderName, value: HeaderValue): Headers =
    copy(map = map.updated(name, List(value)))

  def put(name: HeaderName, values: HeaderValue*): Headers =
    copy(map = map.updated(name, values.toList))

  def remove(name: HeaderName): Headers =
    copy(map = map.removed(name))

  def remove(names: HeaderName*): Headers =
    copy(map = map.removedAll(names))

  def clear(): Headers = Headers(Map.empty)

  def size: Int = map.size

  def isEmpty: Boolean = map.isEmpty

  def contains(name: HeaderName): Boolean = map.contains(name)

  def get(name: HeaderName): Option[List[HeaderValue]] =
    map.get(name)

  def getFirst(name: HeaderName): Option[HeaderValue] =
    map.get(name).flatMap(_.headOption)

  def getLast(name: HeaderName): Option[HeaderValue] =
    map.get(name).flatMap(_.lastOption)

}

object Headers {

  val empty: Headers = Headers(Map.empty)

  def asScala(headers: JHeaders): Headers = {
    val name = (name: String) => HeaderName.assume(CIString(name))
    val value = (value: String) => HeaderValue.assume(value)

    Headers(
      headers
        .entrySet()
        .asScala
        .map(entry => (entry.getKey()) -> entry.getValue().asScala.toList)
        .toMap
        .map { case (k, v) => name(k) -> v.map(value(_)) }
    )
  }

  def asJava(headers: Headers): JHeaders = {
    val jh = new JHeaders()
    val name = (name: HeaderName) => name.value.toString
    val value = (value: HeaderValue) => value.value: String
    jh.put(headers.map.map { case (k, v) => name(k) -> v.map(value(_)).asJava }.asJava)
    jh
  }

}
