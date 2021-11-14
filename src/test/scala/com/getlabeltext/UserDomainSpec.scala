package com.getlabeltext

import com.getlabeltext.domain._
import com.getlabeltext.domain.user.UserDomain._
import com.getlabeltext.domain.user._
import com.getlabeltext.system.ErrorMessage
import zhttp.http.HttpData.CompleteData
import zhttp.http._
import zhttp.test._
import zio._
import zio.json._
import zio.magic._
import zio.test.Assertion.{equalTo, hasField, isRight, isSome}
import zio.test._

import java.util.UUID

object UserDomainSpec extends DefaultRunnableSpec {

  implicit val decoder: JsonDecoder[User] = DeriveJsonDecoder.gen[User]

  val user: User = User(UserId(UUID.randomUUID()), Email("email@example.com"), Password("password"))

  val app = UserDomain.userEndpoints

  val testUserServiceHappy: ULayer[Has[UserDomain.Service]] = ZLayer.succeed(new UserDomain.Service {
    override def create(email: Email, password: Password): IO[ErrorMessage, User] = ZIO.succeed(user)

    override def findById(id: UserId): Task[Option[User]] = ZIO.succeed(Some(user))
  })

  def spec: Spec[Any, TestFailure[Option[Any]], TestSuccess] = {
    suite("get user by id endpoint succeeds with")(
      testM("status ok") {
        val req = Request(Method.GET -> URL(Root / "api" / "user" / user.id.value.toString), Nil, HttpData.empty)
        assertM(app(req).map(resp => resp.status))(equalTo(Status.OK))
      }.inject(TestLogging.layer, testUserServiceHappy),

      testM("header application/json") {
        val req = Request(Method.GET -> URL(Root / "api" / "user" / user.id.value.toString), Nil, HttpData.empty)
        assertM(app(req).map(resp => resp.getHeaderValue(Header.contentTypeJson.name)))(isSome(equalTo(Header.contentTypeJson.value.toString)))
      }.inject(TestLogging.layer, testUserServiceHappy),

      testM("json that represents user") {
        val req = Request(Method.GET -> URL(Root / "api" / "user" / user.id.value.toString), Nil, HttpData.empty)
        val resp = app(req).map(resp => {
          resp.content match {
            case CompleteData(data) => data.map(_.toChar).mkString("").fromJson[User]
            case _ => null
          }
        })
        assertM(resp)(
          isRight(
            hasField("id", _.id.value, equalTo(user.id.value))
          )
        )
      }.inject(TestLogging.layer, testUserServiceHappy)
    )
  }
}
