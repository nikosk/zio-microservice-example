package com.getlabeltext.domain.user

import com.getlabeltext.domain.Email
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder, jsonExclude}
import zio.prelude.{Validation, ZValidation}

import java.time.LocalDateTime
import java.util.UUID

object UserModel {

  final case class UserId(value: UUID)

  object UserId {
    implicit val encoder: JsonEncoder[UserId] = JsonEncoder[UUID].contramap(_.value)
    implicit val decoder: JsonDecoder[UserId] = JsonDecoder[UUID].map(uuid => UserId(uuid))
  }

  final case class Password(value: String)

  object Password {

    implicit val encoder: JsonEncoder[Password] = JsonEncoder[String].contramap(_.value)
    implicit val decoder: JsonDecoder[Password] = JsonDecoder[String].map(str => Password(str))

    def create(maybePassword: String): Validation[String, Password] =
      if (maybePassword == null) Validation.fail("Password was empty")
      else if (maybePassword.length < 6) Validation.fail("Password was too soft")
      else Validation.succeed(Password(maybePassword))
  }

  final case class User(id: UserId, email: Email, @jsonExclude password: Option[Password] = None, createdAt: LocalDateTime = LocalDateTime.now(), modifiedAt: Option[LocalDateTime] = None)

  object User {
    implicit val encoder: JsonEncoder[User] = DeriveJsonEncoder.gen[User]
  }

  final case class UserForm(email: String, password: String, passwordConfirm: String) {

    def validate(): ZValidation[Nothing, String, (Email, Password)] =
      Validation.validate(
        Email.create(email),
        Password.create(password),
        if (passwordConfirm == null || !passwordConfirm.equals(password)) {
          Validation.fail("Passwors didn't match")
        } else {
          Validation.succeed(Password(passwordConfirm))
        }
      ).map(v => (v._1, v._2))
  }

  object UserForm {
    implicit val decoder: JsonDecoder[UserForm] = DeriveJsonDecoder.gen[UserForm]
  }

}
