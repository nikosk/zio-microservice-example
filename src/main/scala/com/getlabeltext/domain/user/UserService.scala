package com.getlabeltext.domain.user

import com.getlabeltext.domain.Email
import com.getlabeltext.domain.user.UserModel.{Password, User, UserId}
import com.getlabeltext.system.ErrorMessage
import org.jdbi.v3.core.mapper.RowMapper
import org.jdbi.v3.core.statement.StatementContext
import org.jdbi.v3.core.{Handle, Jdbi}
import zio._

import java.sql.ResultSet
import java.util.UUID


object UserService {

  trait Service {

    def create(email: Email, password: Password): IO[ErrorMessage, User]

    def findById(id: UserId): Task[Option[User]]

  }

  private final case class ServiceLive(jdbi: Jdbi) extends Service {

    private val userRowMapper = new UserRowMapper()

    private final class UserRowMapper extends RowMapper[User] {
      override def map(rs: ResultSet, ctx: StatementContext): User = {
        User(
          UserId(UUID.fromString(rs.getString("id"))),
          Email(rs.getString("email")),
          Some(Password(rs.getString("password"))),
          rs.getTimestamp("created_at").toLocalDateTime,
          if (rs.getTimestamp("modified_at") != null) Some(rs.getTimestamp("modified_at").toLocalDateTime) else None
        )
      }
    }

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

}
