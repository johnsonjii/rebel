package com.potenciasoftware.rebel

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.tools.nsc.Settings
import scala.tools.nsc.interpreter.shell.ILoop
import scala.tools.nsc.interpreter.shell.ShellConfig

import TestUtils._
import BaseReplTest._

class BaseReplTest extends AnyFlatSpec with Matchers {

  def basic() = new TestRepl()

  "BaseRepl" should "behave like the normal ILoop" in {
    replTest[BaseReplTest]("basic", Seq("42 + 42"))
      .out.asBlock shouldBe """
      |scala> val res0: Int = 84
      |
      |scala> """.stripMargin
  }

  it should "have the default compiler options" in {
    replTest[BaseReplTest]("basic", Seq("def test(): Int = 1", "test")).out(3) shouldBe
      "scala> warning: 1 deprecation (since 2.13.3); for details, " +
      "enable `:setting -deprecation` or `:replay -deprecation`"
  }

  def customCompilerOptions() = new TestRepl {
    override protected def updateSettings(settings: Settings): Unit = {
      // This Wconf value suppresses the deprecation warning that would normally
      // be shown when a zero arg method is called without the parentheses.
      settings.Wconf.value = "msg=.*Auto-application to `\\(\\)`.*:s" :: Nil
    }
  }

  it should "set custom compiler options" in {
    replTest[BaseReplTest]("customCompilerOptions", Seq("def test(): Int = 1", "test"))
      .out.drop(2).take(3).asBlock shouldBe
      """
      |scala> val res0: Int = 1
      |""".stripMargin
  }

  def defaultBanner() = new BaseRepl()

  it should "display the standard ILoop banner by default" in {
    replTest[BaseReplTest]("defaultBanner", Seq())
      .out.takeWhile(_ != "scala> ").asBlock shouldBe
      s"${new ILoop(ShellConfig(new Settings)).welcome}\n"
  }

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
