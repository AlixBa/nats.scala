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

import munit.Assertions
import munit.Compare
import munit.Location
import org.typelevel.otel4s.Attributes
import org.typelevel.otel4s.oteljava.testkit.metrics.data.Metric
import org.typelevel.otel4s.oteljava.testkit.metrics.data.MetricData.DoubleGauge
import org.typelevel.otel4s.oteljava.testkit.metrics.data.MetricData.DoubleSum
import org.typelevel.otel4s.oteljava.testkit.metrics.data.MetricData.ExponentialHistogram
import org.typelevel.otel4s.oteljava.testkit.metrics.data.MetricData.Histogram
import org.typelevel.otel4s.oteljava.testkit.metrics.data.MetricData.LongGauge
import org.typelevel.otel4s.oteljava.testkit.metrics.data.MetricData.LongSum
import org.typelevel.otel4s.oteljava.testkit.metrics.data.MetricData.Summary

sealed trait SimpleMetric {
  def name: String
}

object SimpleMetric {

  final case class CounterLong(name: String, attributes: Attributes, values: List[Long]) extends SimpleMetric
  final case class CounterDouble(name: String, attributes: Attributes, values: List[Double]) extends SimpleMetric
  final case class GaugeLong(name: String, attributes: Attributes, values: List[Long]) extends SimpleMetric
  final case class GaugeDouble(name: String, attributes: Attributes, values: List[Double]) extends SimpleMetric
  final case class SummaryDouble(name: String, attributes: Attributes, values: List[Double]) extends SimpleMetric
  final case class HistogramDouble(name: String, attributes: Attributes, values: List[Double]) extends SimpleMetric
  final case class ExpHistogramDouble(name: String, attributes: Attributes, values: List[Double]) extends SimpleMetric

  implicit def cmp(implicit
      l: Compare[List[SimpleMetric], List[SimpleMetric]]
  ): Compare[List[Metric], List[SimpleMetric]] =
    new Compare[List[Metric], List[SimpleMetric]] {

      override def isEqual(obtained: List[Metric], expected: List[SimpleMetric]): Boolean =
        l.isEqual(SimpleMetric(obtained).sortBy(_.name), expected.sortBy(_.name))

      override def failEqualsComparison(
          obtained: List[Metric],
          expected: List[SimpleMetric],
          title: Any,
          loc: Location,
          assertions: Assertions
      ): Nothing =
        l.failEqualsComparison(SimpleMetric(obtained).sortBy(_.name), expected.sortBy(_.name), title, loc, assertions)

    }

  private def apply(metrics: List[Metric]): List[SimpleMetric] =
    metrics.map { metric =>
      val name = metric.name
      val attr = metric.data.points.foldLeft(Attributes.empty) { case (attr, data) =>
        attr ++ data.attributes
      }

      metric.data match {
        case data: LongGauge            => GaugeLong(name, attr, data.points.map(_.value))
        case data: DoubleGauge          => GaugeDouble(name, attr, data.points.map(_.value))
        case data: LongSum              => CounterLong(name, attr, data.points.map(_.value))
        case data: DoubleSum            => CounterDouble(name, attr, data.points.map(_.value))
        case data: Summary              => SummaryDouble(name, attr, data.points.map(_.value.sum))
        case data: Histogram            => HistogramDouble(name, attr, data.points.map(_.value.sum))
        case data: ExponentialHistogram => ExpHistogramDouble(name, attr, data.points.map(_.value.sum))
      }
    }

}
