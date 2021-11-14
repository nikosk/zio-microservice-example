package com.getlabeltext.domain

import com.getlabeltext.system.Patterns
import zio.json.{JsonDecoder, JsonEncoder}
import zio.prelude.Validation

final case class Email private(value: String)

object Email {

  implicit val encoder: JsonEncoder[Email] = JsonEncoder[String].contramap(_.value)
  implicit val decoder: JsonDecoder[Email] = JsonDecoder[String].map(str => Email(str))

  def create(maybeEmail: String): Validation[String, Email] =
    if (maybeEmail == null || maybeEmail.isEmpty) Validation.fail("Email was empty")
    else if (!Patterns.EMAIL_PATTERN.matches(maybeEmail)) Validation.fail("Invalid email")
    else Validation.succeed(Email(maybeEmail))
}
