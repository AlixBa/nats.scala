package io.nats.scala.extra

package object syntax {
  object headers extends HeadersOps.ToHeadersOps
  object message extends MessageOps.ToMessageOps
}
