package zhttp

import zhttp.http.{Http, HttpResult}
import zio.ZIO

/* Helper for executing requests. Copied from the zio-http repository */
package object test {
  implicit class HttpWithTest[R, E, A, B](http: Http[R, E, A, B]) {
    def apply(req: A) = {
      val value: HttpResult[R, E, B] = http.execute(req)
      val evaluate: HttpResult.Out[R, E, B] = value.evaluate
      val effect: ZIO[R, Option[E], B] = evaluate.asEffect
      effect
    }
  }
}
