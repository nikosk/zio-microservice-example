package com.getlabeltext.system

import com.getlabeltext.Settings
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.h2.H2DatabasePlugin
import org.jdbi.v3.core.statement.Slf4JSqlLogger
import org.slf4j.{Logger, LoggerFactory}
import zio._


object Datasource {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  private def make(): HikariDataSource = {
    val config = new HikariConfig()
    config.setJdbcUrl(Settings.dbUrl())
    config.setUsername(Settings.dbUser())
    config.setPassword(Settings.dbPass())
    new HikariDataSource(config)
  }

  private def createDatasource(): ZIO[Any, Throwable, HikariDataSource] = ZIO.effect(make())

  private val closeDatasource: HikariDataSource => ZIO[Any, Nothing, Unit] =
    (datasource: HikariDataSource) => ZIO.effect {
      logger.info("closing connection pool")
      datasource.close()
    }.catchAll {
      a =>
        a.printStackTrace()
        ZIO.unit
    }

  private def managedConnectionPool(): ZManaged[Any, Throwable, HikariDataSource] =
    ZManaged.make(createDatasource())(closeDatasource)

  val live: ZLayer[Any, Throwable, Has[HikariDataSource]] = ZLayer.fromManaged(managedConnectionPool())
}

object JdbiInstance {

  private def make(ds: HikariDataSource): Jdbi = {
    val result = Jdbi.create(ds)
    result.installPlugin(new H2DatabasePlugin())
    if (Settings.debug()) {
      result.setSqlLogger(new Slf4JSqlLogger())
    }
    result
  }

  val live: ZLayer[Has[HikariDataSource], Nothing, Has[Jdbi]] =
    ZLayer.fromService[HikariDataSource, Jdbi] {
      make
    }


}
