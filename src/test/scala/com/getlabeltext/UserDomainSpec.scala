package com.getlabeltext

import com.getlabeltext.domain._
import com.getlabeltext.domain.user.UserModel.{Password, User, UserId}
import com.getlabeltext.domain.user.{UserService, UserWeb}
import com.getlabeltext.system.ErrorMessage
import zhttp.http.HttpData.CompleteData
import zhttp.http._
import zhttp.test._
import zio._
import zio.json._
import zio.magic._
import zio.test.Assertion._
import zio.test._

import java.util.UUID

object UserDomainSpec extends DefaultRunnableSpec {

  implicit val decoder: JsonDecoder[User] = DeriveJsonDecoder.gen[User]

  val user = User(UserId(UUID.randomUUID()), Email("email@example.com"), Some(Password("password")))

  val app = UserWeb.userEndpoints

  val testUserServiceHappy: ULayer[Has[UserService.Service]] = ZLayer.succeed(new UserService.Service {
    override def create(email: Email, password: Password) = ZIO.succeed(user)

    override def findById(id: UserId) = ZIO.succeed(Some(user))
  })

  val getUserByIdRequest = Request(Method.GET -> URL(Root / "api" / "user" / user.id.value.toString), Nil, HttpData.empty)

  val userByIdSuccessSuite = suite("get user by id endpoint succeeds with")(
    testM("status ok") {
      assertM(app(getUserByIdRequest).map(resp => resp.status))(equalTo(Status.OK))
    },
    testM("header application/json") {
      assertM(app(getUserByIdRequest).map(resp => resp.getHeaderValue(Header.contentTypeJson.name)))(isSome(equalTo(Header.contentTypeJson.value.toString)))
    },
    testM("json that represents user") {
      val resp = app(getUserByIdRequest).map(resp => {
        resp.content match {
          case CompleteData(data) => data.map(_.toChar).mkString("").fromJson[User]
          case _ => null
        }
      })
      val assertCorrectId: Assertion[User] = hasField("id", _.id.value, equalTo(user.id.value))
      val assertCorrectEmail: Assertion[User] = hasField("email", _.email.value, equalTo(user.email.value))
      val assertNoPassword: Assertion[User] = not(hasField("password", _.password, equalTo(user.password)))
      assertM(resp)(
        isRight(assertCorrectId && assertCorrectEmail && assertNoPassword)
      )
    }
  ).inject(TestLogging.layer, testUserServiceHappy)

  val testUserServiceSad: ULayer[Has[UserService.Service]] = ZLayer.succeed(new UserService.Service {
    override def create(email: Email, password: Password) = ZIO.fail(ErrorMessage(""))

    override def findById(id: UserId) = ZIO.succeed(None)
  })

  val userByIdFailureSuite = suite("get user by id endpoint fails with")(
    testM("status not found when user does not exist") {
      assertM(app(getUserByIdRequest).map(resp => resp.status))(equalTo(Status.NOT_FOUND))
    },
    testM("status bad request when id invalid") {
      assertM(
        app(Request(Method.GET -> URL(Root / "api" / "user" / "invalid_id"), Nil, HttpData.empty)).map(_.status)
      )(equalTo(Status.BAD_REQUEST))
    }
  ).inject(TestLogging.layer, testUserServiceSad)

  def spec = {
    suite("get user by id")(userByIdSuccessSuite, userByIdFailureSuite)
  }
}
