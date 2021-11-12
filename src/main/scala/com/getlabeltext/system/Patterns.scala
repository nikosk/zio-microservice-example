package com.getlabeltext.system

import scala.util.matching.Regex

object Patterns {

  val EMAIL_PATTERN: Regex = """[a-zA-Z0-9+._%\-]{1,256}@[a-zA-Z0-9][a-zA-Z0-9\-]{0,64}(\.[a-zA-Z0-9][a-zA-Z0-9\-]{0,25})+""".r
}
