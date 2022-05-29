package com.potenciasoftware.rebel

import org.scalatest.Tag
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.tools.nsc.Settings
import scala.tools.nsc.interpreter.shell.ILoop
import scala.tools.nsc.interpreter.shell.ShellConfig

import BaseRepl.Parameter
import BaseReplTest._
import TestUtils._

class BaseReplTest extends AnyFlatSpec with Matchers {

  def basic() = new TestRepl()

  "BaseRepl" should "behave like the normal ILoop by default" in {
    replTest[BaseReplTest]("basic", "42 + 42")
      .out.compressed.asBlock shouldBe
      """scala> val res0: Int = 84
      |scala> """.stripMargin
  }

  it should "have the standard compiler options by default" in {
    replTest[BaseReplTest]("basic", "def test(): Int = 1", "test").out(3) shouldBe
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

  it should "allow custom compiler options to be provided" in {
    replTest[BaseReplTest]("customCompilerOptions", "def test(): Int = 1", "test")
      .out.drop(2).take(3).asBlock shouldBe
      """
      |scala> val res0: Int = 1
      |""".stripMargin
  }

  def defaultBanner() = new BaseRepl()

  it should "display the standard ILoop banner by default" in {
    replTest[BaseReplTest]("defaultBanner")
      .out.takeWhile(_ != "scala> ").asBlock shouldBe
      s"${new ILoop(ShellConfig(new Settings)).welcome}\n"
  }

  def customBanner() = new BaseRepl() {
    override protected val banner: String = "Custom Banner"
  }

  it should "allow the banner to be customized" in {
    replTest[BaseReplTest]("customBanner")
      .out.takeWhile(_ != "scala> ").asBlock shouldBe "Custom Banner\n"
  }

  def boundAnswer() = new TestRepl {
    private val Answer: Int = 42
    override protected def boundValues: Seq[Parameter] =
      Seq(Parameter("Answer", Answer))
  }

  it should "allow custom values to be bound to the REPL" in {
    replTest[BaseReplTest]("boundAnswer", "println(Answer)")
      .out(1) shouldBe "scala> println(Answer)42"
  }

  def dynamicPrompt() = new TestRepl {
    override protected def boundValues: Seq[Parameter] =
      Seq(Parameter("options", new Options(this)))
  }

  it should "allow the prompt to be changed dynamically" in {
    replTest[BaseReplTest]("dynamicPrompt",
      """options.PS1 = "test> """",
      """println(s"[${options.PS1}]")"""
      ).out.take(3).asBlock shouldBe
    """
    |scala> // mutated options.PS1
    |test> println(s"[${options.PS1}]")[test> ]""".stripMargin
  }

  it should "use the full classpath" in {
    replTest[BaseReplTest]("basic",
      "import com.potenciasoftware.rebel.BaseReplTest.TestValue",
      "val value = TestValue(42)",
      "println(value)"
    ).out.compressed.take(3).asBlock shouldBe
    """scala> import com.potenciasoftware.rebel.BaseReplTest.TestValue
    |scala> val value = TestValue(42)val value: com.potenciasoftware.rebel.BaseReplTest.TestValue = TestValue(42)
    |scala> println(value)TestValue(42)""".stripMargin
  }

  def startupScript() = new TestRepl {
    override protected def startupScript: String =
      """
      |object printAnswer {
      |
      |  private val answer = 42
      |
      |  def apply(): Unit =
      |    println(s"The answer is: $answer")
      |}""".stripMargin
  }

  it should "allow a silent script to run at startup" in {
    replTest[BaseReplTest]("startupScript",
      "printAnswer()").out.take(2).asBlock shouldBe
        """
        |scala> printAnswer()The answer is: 42""".stripMargin
  }
}

object BaseReplTest {

  object Focus extends Tag("Focus")

  class TestRepl extends BaseRepl {
    // Normally we don't need the banner as part of our test outupt
    override protected val banner: String = ""
  }

  class Options(repl: BaseRepl) {
    def PS1: String = repl.prompt
    def PS1_=(ps1: String): Unit = { repl.prompt = ps1 }
  }

  case class TestValue(value: Any)
}

