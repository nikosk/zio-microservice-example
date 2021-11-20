package com.getlabeltext.domain.user


import com.getlabeltext.domain.user.UserModel.{User, UserForm, UserId}
import com.getlabeltext.domain.user.UserService.Service
import com.getlabeltext.system.ErrorMessage
import zhttp.http._
import zio._
import zio.json._
import zio.logging._

import java.util.UUID
import scala.util.Try

object UserWeb {
  val userEndpoints: Http[Has[Logger[String]] with Has[Service], Nothing, Request, UHttpResponse] = Http.collectM[Request] {
    case req@Method.POST -> Root / "api" / "user" =>
      val result: ZIO[Has[Service] with Logging, ErrorMessage, User] = for {
        body <- ZIO.fromEither(Either.cond(req.getBodyAsString.isDefined, req.getBodyAsString.get, ErrorMessage("Body is empty")))
        _ <- log.info(s"Received create new user request: ${body}")
        maybeValid <- ZIO.fromEither(body.fromJson[UserForm].left.map(ErrorMessage(_)))
        data <- ZIO.fromEither(maybeValid.validate().toEitherWith(err => ErrorMessage(err.mkString(", "))))
        user <- UserService.Service.create(data._1, data._2)
        _ <- log.info(s"Created new user: ${user}")
      } yield user
      result.fold(err => {
        Response.http(
          Status.UNPROCESSABLE_ENTITY,
          List(Header.contentTypeJson),
          HttpData.CompleteData(Chunk.fromArray(err.toJson.getBytes(HTTP_CHARSET)))
        )
      }, user => {
        Response.http(
          Status.OK,
          List(Header.contentTypeJson),
          HttpData.CompleteData(Chunk.fromArray(user.toJson.getBytes(HTTP_CHARSET)))
        )
      })
    case Method.GET -> Root / "api" / "user" / userId =>
      val result: ZIO[Logging with Has[UserService.Service], ErrorMessage, Option[User]] = for {
        _ <- log.info(s"Loading user by id: ${userId}")
        uuid <- ZIO.fromEither(Try(UUID.fromString(_)).map(_.apply(userId)).toEither.left.map(t => ErrorMessage(t.getMessage)))
        user <- UserService.Service.findById(UserId(uuid)).mapError(t => ErrorMessage(t.getMessage))
        _ <- log.info(s"Loaded user: ${user}")
      } yield user
      result.fold(err => {
        Response.http(
          Status.BAD_REQUEST,
          List(Header.contentTypeJson),
          HttpData.CompleteData(Chunk.fromArray(err.toJson.getBytes(HTTP_CHARSET)))
        )
      }, {
        case None => Response.http(
          Status.NOT_FOUND
        )
        case Some(user) => Response.http(
          Status.OK,
          List(Header.contentTypeJson),
          HttpData.CompleteData(Chunk.fromArray(user.toJson.getBytes(HTTP_CHARSET)))
        )
      })
  }

}
