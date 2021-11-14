package com.getlabeltext.domain.user

import com.getlabeltext.domain.Email
import com.getlabeltext.system.ErrorMessage
import org.jdbi.v3.core.mapper.RowMapper
import org.jdbi.v3.core.statement.StatementContext
import org.jdbi.v3.core.{Handle, Jdbi}
import zhttp.http._
import zio._
import zio.json._
import zio.logging._
import zio.prelude._

import java.sql.ResultSet
import java.time.LocalDateTime
import java.util.UUID
import scala.util.Try


object UserDomain {

  final case class UserId(value: UUID)

  object UserId {
    implicit val encoder: JsonEncoder[UserId] = JsonEncoder[UUID].contramap(_.value)
    implicit val decoder: JsonDecoder[UserId] = JsonDecoder[UUID].map(uuid => UserId(uuid))
  }

  final case class Password private(value: String)

  object Password {

    implicit val encoder: JsonEncoder[Password] = JsonEncoder[String].contramap(_.value)
    implicit val decoder: JsonDecoder[Password] = JsonDecoder[String].map(str => Password(str))

    def create(maybePassword: String): Validation[String, Password] =
      if (maybePassword == null) Validation.fail("Password was empty")
      else if (maybePassword.length < 6) Validation.fail("Password was too soft")
      else Validation.succeed(Password(maybePassword))
  }

  final case class User(id: UserId, email: Email, password: Password, createdAt: LocalDateTime = LocalDateTime.now(), modifiedAt: Option[LocalDateTime] = None)

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

  trait Service {

    def create(email: Email, password: Password): IO[ErrorMessage, User]

    def findById(id: UserId): Task[Option[User]]

  }

  private final case class ServiceLive(jdbi: Jdbi) extends Service {

    private final class UserRowMapper extends RowMapper[User] {
      override def map(rs: ResultSet, ctx: StatementContext): User = {
        User(
          UserId(UUID.fromString(rs.getString("id"))),
          Email(rs.getString("email")),
          Password(rs.getString("password")),
          rs.getTimestamp("created_at").toLocalDateTime,
          if (rs.getTimestamp("modified_at") != null) Some(rs.getTimestamp("modified_at").toLocalDateTime) else None
        )
      }
    }

    private val userRowMapper = new UserRowMapper()

    override def create(email: Email, password: Password): IO[ErrorMessage, User] = {
      val throwableToErrorMessage: Throwable => ErrorMessage = e => ErrorMessage(e.getMessage)
      val result = for {
        exists <- notExistsByEmail(email).mapError(throwableToErrorMessage)
        userId <-
          if (exists) {
            ZIO.fail(ErrorMessage("Email is already in use"))
          } else {
            ZIO.effect {
              val id = UUID.randomUUID()
              jdbi.inTransaction[Int, Exception]((handle: Handle) => {
                handle.createUpdate("INSERT INTO users(id, email, password) VALUES (:id, :email, :password)")
                  .bind("id", id)
                  .bind("email", email.value)
                  .bind("password", password.value)
                  .execute()
              })
              UserId(id)
            }.mapError(throwableToErrorMessage)
          }
        maybeUser <- findById(userId).mapError(throwableToErrorMessage)
        user <- ZIO.fromEither(Either.cond(maybeUser.isDefined, maybeUser.get, ErrorMessage("WTF?")))
      } yield user
      result
    }

    override def findById(id: UserId): Task[Option[User]] = ZIO.effect {
      jdbi.withHandle((handle: Handle) => {
        val result = handle.createQuery("SELECT * FROM users WHERE id = :id")
          .bind("id", id.value)
          .map(userRowMapper)
          .findFirst()
        if (result.isEmpty) {
          None
        } else {
          Some(result.get())
        }
      })
    }

    private def notExistsByEmail(email: Email): Task[Boolean] =
      ZIO.effect {
        jdbi.withHandle((handle: Handle) => {
          handle.createQuery("SELECT COUNT(*) = 0 FROM users WHERE email = :email")
            .bind("email", email.value)
            .map { (rs: ResultSet, col: Int, _) => rs.getBoolean(col) }
            .first()
        })
      }
  }

  object Service {

    def create(email: Email, password: Password): ZIO[Has[Service], ErrorMessage, User] = ZIO.serviceWith[Service](_.create(email, password))

    def findById(id: UserId): ZIO[Has[Service], Throwable, Option[User]] = ZIO.serviceWith[Service](_.findById(id))

  }

  val live: ZLayer[Has[Jdbi], Nothing, Has[Service]] = ZLayer.fromService[Jdbi, Service] {
    ServiceLive
  }

  val userEndpoints: Http[Has[Logger[String]] with Has[Service], Nothing, Request, UHttpResponse] = Http.collectM[Request] {
    case req@Method.POST -> Root / "api" / "user" =>
      val result: ZIO[Has[UserDomain.Service] with Logging, ErrorMessage, UserDomain.User] = for {
        body <- ZIO.fromEither(Either.cond(req.getBodyAsString.isDefined, req.getBodyAsString.get, ErrorMessage("Body is empty")))
        _ <- log.info(s"Received create new user request: ${body}")
        maybeValid <- ZIO.fromEither(body.fromJson[UserForm].left.map(ErrorMessage(_)))
        data <- ZIO.fromEither(maybeValid.validate().toEitherWith(err => ErrorMessage(err.mkString(", "))))
        user <- UserDomain.Service.create(data._1, data._2)
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
      val result: ZIO[Logging with Has[UserDomain.Service], ErrorMessage, Option[UserDomain.User]] = for {
        _ <- log.info(s"Loading user by id: ${userId}")
        uuid <- ZIO.fromEither(Try(UUID.fromString(_)).map(_.apply(userId)).toEither.left.map(t => ErrorMessage(t.getMessage)))
        user <- UserDomain.Service.findById(UserId(uuid)).mapError(t => ErrorMessage(t.getMessage))
        _ <- log.info(s"Loaded user: ${user}")
      } yield user
      result.fold(err => {
        Response.http(
          Status.INTERNAL_SERVER_ERROR,
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
