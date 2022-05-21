package com.potenciasoftware.rebel

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.tools.nsc.Settings
import scala.tools.nsc.interpreter.shell.ILoop
import scala.tools.nsc.interpreter.shell.ShellConfig

import TestUtils._
import BaseReplTest._

class BaseReplTest extends AnyFlatSpec with Matchers {

  "BaseRepl" should "behave like the normal ILoop" in {
    replTest[BaseReplTest]("basic", Seq("42 + 42"))
      .out.asBlock shouldBe """
      |scala> val res0: Int = 84
      |
      |scala> """.stripMargin
  }

  def basic() = new TestRepl()

  it should "display the standard ILoop banner by default" in {
    replTest[BaseReplTest]("defaultBanner", Seq())
      .out.takeWhile(_ != "scala> ").asBlock shouldBe
      s"${new ILoop(ShellConfig(new Settings)).welcome}\n"
  }

  def defaultBanner() = new BaseRepl()

  it should "display a custom banner" in {
    replTest[BaseReplTest]("customBanner", Seq())
      .out.takeWhile(_ != "scala> ").asBlock shouldBe "Custom Banner\n"
  }

  def customBanner() = new BaseRepl() {
    override protected val banner: String = "Custom Banner"
  }
}

object BaseReplTest {

  class TestRepl extends BaseRepl {
    // Normally we don't need the banner as part of our test outupt
    override protected val banner: String = ""
  }
}
