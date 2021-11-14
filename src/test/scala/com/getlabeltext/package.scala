package com.getlabeltext

import zio._
import zio.logging._


object TestLogging {

  class LoggerTestService(ref: Ref[Vector[String]]) extends Logger[String] {

    override def locally[R1, E, A1](f: LogContext => LogContext)(zio: ZIO[R1, E, A1]): ZIO[R1, E, A1] = zio

    override def log(line: => String): UIO[Unit] = ref.update(_ :+ line).unit

    override def logContext: UIO[LogContext] = ZIO.succeed(LogContext.empty)
  }


  val layer: ULayer[Has[Logger[String]]] = ZLayer.fromEffect(
    for {
      ref <- Ref.make(Vector.empty[String])
      logger <- ZIO.fromFunction[Any, Logger[String]](_ => new LoggerTestService(ref).asInstanceOf[Logger[String]])
    } yield logger
  )
}

