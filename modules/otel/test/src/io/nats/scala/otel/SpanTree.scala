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

import io.opentelemetry.sdk.trace.data.SpanData
import munit.Assertions
import munit.Compare
import munit.Location

final case class SpanTree(name: String, children: List[SpanTree])

object SpanTree {

  implicit def cmp(implicit c: Compare[List[SpanTree], List[SpanTree]]): Compare[List[SpanData], SpanTree] =
    new Compare[List[SpanData], SpanTree] {
      def sort(trees: List[SpanTree]): List[SpanTree] = trees.sortBy(_.name).map(sort)
      def sort(tree: SpanTree): SpanTree = tree.copy(children = sort(tree.children))

      override def isEqual(obtained: List[SpanData], expected: SpanTree): Boolean =
        c.isEqual(sort(SpanTree(obtained)), sort(List(expected)))

      override def failEqualsComparison(
          obtained: List[SpanData],
          expected: SpanTree,
          title: Any,
          loc: Location,
          assertions: Assertions
      ): Nothing =
        c.failEqualsComparison(sort(SpanTree(obtained)), sort(List(expected)), title, loc, assertions)
    }

  def apply(name: String): SpanTree = SpanTree(name, List.empty)
  def apply(name: String, children: SpanTree*): SpanTree = SpanTree(name, children.toList)

  def apply(spans: List[SpanData]): List[SpanTree] = {
    def traceIdParentSpanId(data: SpanData): String = s"${data.getTraceId()}_${data.getParentSpanId()}"
    def traceIdSpanId(data: SpanData): String = s"${data.getTraceId()}_${data.getSpanId()}"

    val parentChildren = spans.groupBy(traceIdParentSpanId)
    val validSpans = spans.map(traceIdSpanId)
    val roots = spans.filterNot(data => validSpans.exists(_.equals(traceIdParentSpanId(data))))

    def build(data: SpanData): SpanTree = SpanTree(
      name = data.getName(),
      children = parentChildren.getOrElse(traceIdSpanId(data), List.empty).map(build)
    )

    roots.map(build)
  }

}
