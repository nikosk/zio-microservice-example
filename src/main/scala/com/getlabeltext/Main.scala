package com.getlabeltext

import com.getlabeltext.domain.user.UserDomain
import com.getlabeltext.system.{Datasource, JdbiInstance}
import zhttp.http.{Http, Request, Response}
import zhttp.service.Server
import zio._
import zio.clock.Clock
import zio.console.Console
import zio.logging._
import zio.magic._

import java.nio.file.Path

object Main extends App {

  val app: Http[Logging with Has[UserDomain.Service], Nothing, Request, Response.HttpResponse[Any, Nothing]] = UserDomain.userEndpoints

  val loggingLive: ZLayer[Console with Clock, Throwable, Logging] = if (Settings.debug()) {
    Logging.console(
      logLevel = LogLevel.Info,
      format = LogFormat.ColoredLogFormat()
    )
  } else {
    Logging.file(Path.of(Settings.logPath()))
  }

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    Server.start(Settings.port(), app)
      .inject(
        Clock.live,
        console.Console.live,
        loggingLive >>> Logging.withRootLoggerName("ROOT"),
        Datasource.live,
        JdbiInstance.live,
        UserDomain.live
      )
      .exitCode
  }

}
