package com.getlabeltext

import zio.test.Assertion.equalTo
import zio.test.assert
import zio.test.junit.JUnitRunnableSpec

object MainSpec extends JUnitRunnableSpec {
  def spec = suite("Test environment")(
    test("expect call with input satisfying assertion") {
      assert(40 + 2)(equalTo(42))
    }
  )
}


