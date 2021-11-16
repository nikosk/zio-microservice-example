package com.getlabeltext

import com.typesafe.config.{Config, ConfigFactory}

object Settings {

  private val profile = System.getenv("profile")

  private lazy val config: Config = if (profile != null) {
    ConfigFactory.load(s"application-${profile}")
      .withFallback(
        ConfigFactory.load()
      )
  } else {
    ConfigFactory.load()
  }

  def port(): Int = config.getInt("app.port")

  def debug(): Boolean = config.getBoolean("app.debug")

  def dbUrl(): String = config.getString("app.db.url")

  def dbUser(): String = config.getString("app.db.user")

  def dbPass(): String = config.getString("app.db.pass")

}


