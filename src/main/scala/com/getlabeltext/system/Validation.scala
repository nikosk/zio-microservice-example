package com.getlabeltext.system

import zio.json.{DeriveJsonEncoder, JsonEncoder}

case class ErrorMessage(message: String)

object ErrorMessage {
  implicit val encoder: JsonEncoder[ErrorMessage] = DeriveJsonEncoder.gen[ErrorMessage]
}

case class ValidationError(key: String, message: String)
