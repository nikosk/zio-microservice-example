package com.getlabeltext

import com.getlabeltext.domain.user.{UserService, UserWeb}
import com.getlabeltext.system.{Datasource, JdbiInstance}
import zhttp.http.{Http, Request, Response}
import zhttp.service.Server
import zio._
import zio.clock.Clock
import zio.console.Console
import zio.logging._
import zio.logging.slf4j.Slf4jLogger
import zio.magic._

object Main extends App {

  val logFormat = "[correlation-id = %s] %s"

  val app: Http[Logging with Has[UserService.Service], Nothing, Request, Response.HttpResponse[Any, Nothing]] = UserWeb.userEndpoints

  val loggingLive: ZLayer[Console with Clock, Throwable, Logging] =
    Slf4jLogger.make { (context, message) =>
      val correlationId = LogAnnotation.CorrelationId.render(
        context.get(LogAnnotation.CorrelationId)
      )
      logFormat.format(correlationId, message)
    }

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    Server.start(Settings.port(), app)
      .inject(
        Clock.live,
        console.Console.live,
        loggingLive >>> Logging.withRootLoggerName("ROOT"),
        Datasource.live,
        JdbiInstance.live,
        UserService.live
      )
      .exitCode
  }

}
