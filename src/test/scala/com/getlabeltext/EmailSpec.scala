package com.getlabeltext

import com.getlabeltext.domain.Email
import zio.NonEmptyChunk
import zio.test.Assertion.{isLeft, isRight}
import zio.test._
import zio.test.environment.TestEnvironment

object EmailSpec extends DefaultRunnableSpec {

  val flattenErrors: NonEmptyChunk[String] => String = errs => errs.mkString(" ,")

  def spec: Spec[TestEnvironment, TestFailure[Nothing], TestSuccess] =
    suite("email")(
      test("should be accepted if valid") {
        assert(Email.create("valid@example.com").toEitherWith(flattenErrors))(isRight)
      },
      test("should be rejected if invalid") {
        assert(Email.create("invalid email").toEitherWith(flattenErrors))(isLeft)
      },
      test("should be rejected if null") {
        assert(Email.create(null).toEitherWith(flattenErrors))(isLeft)
      },
      test("should be rejected if empty") {
        assert(Email.create("").toEitherWith(flattenErrors))(isLeft)
      },
    )
}
