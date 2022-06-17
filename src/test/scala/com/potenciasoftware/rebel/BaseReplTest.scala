package com.potenciasoftware.rebel

import com.potenciasoftware.rebel.executionWrapper.ExecutionWrapper
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.tools.nsc.Settings
import scala.tools.nsc.interpreter.shell.ILoop
import scala.tools.nsc.interpreter.shell.ShellConfig

import BaseRepl._
import BaseReplTest._
import TestUtils._

class BaseReplTest extends AnyFlatSpec with Matchers {

  def echo(input: Seq[String]) = new EchoRepl(input)

  "BaseRepl" should "behave like the normal ILoop by default" in {
    replTest[BaseReplTest]("echo", "42 + 42")
      .out.compressed.asBlock shouldBe
      """scala> 42 + 42
      |val res0: Int = 84
      |scala> """.stripMargin
  }

  def basic(input: Seq[String]) = new EchoRepl()

  it should "have the standard compiler options by default" in {
    replTest[BaseReplTest]("basic", "def test(): Int = 1", "test")
      .out(5) shouldBe
      "scala> warning: 1 deprecation (since 2.13.3); for details, " +
      "enable `:setting -deprecation` or `:replay -deprecation`"
  }

  def customCompilerOptions(input: Seq[String]) = new EchoRepl(input) {
    override protected def updateSettings(settings: Settings): Unit = {
      // This Wconf value suppresses the deprecation warning that would normally
      // be shown when a zero arg method is called without the parentheses.
      settings.Wconf.value = "msg=.*Auto-application to `\\(\\)`.*:s" :: Nil
    }
  }

  it should "allow custom compiler options to be provided" in {
    replTest[BaseReplTest]("customCompilerOptions", "def test(): Int = 1", "test")
      .out.drop(4).take(4).asBlock shouldBe
      """
      |scala> test
      |val res0: Int = 1
      |""".stripMargin
  }

  def defaultBanner(input: Seq[String]) = new BaseRepl()

  it should "display the standard ILoop banner by default" in {
    replTest[BaseReplTest]("defaultBanner")
      .out.takeWhile(_ != "scala> ").asBlock shouldBe
      s"${new ILoop(ShellConfig(new Settings)).welcome}\n"
  }

  def customBanner(input: Seq[String]) = new BaseRepl() {
    override protected val banner: String = "Custom Banner"
  }

  it should "allow the banner to be customized" in {
    replTest[BaseReplTest]("customBanner")
      .out.takeWhile(_ != "scala> ").asBlock shouldBe "Custom Banner\n"
  }

  def boundAnswer(input: Seq[String]) = new EchoRepl() {
    private val Answer: Int = 42
    override protected def boundValues: Seq[Parameter] =
      Seq(Parameter("Answer", Answer))
  }

  it should "allow custom values to be bound to the REPL" in {
    replTest[BaseReplTest]("boundAnswer", "println(Answer)")
      .out.drop(1).take(3).asBlock shouldBe
      """
      |scala> println(Answer)
      |42""".stripMargin
  }

  def dynamicPrompt(input: Seq[String]) = new EchoRepl() {
    override protected def boundValues: Seq[Parameter] =
      Seq(Parameter("options", new Options(this)))
  }

  it should "allow the prompt to be changed dynamically" in {
    replTest[BaseReplTest]("dynamicPrompt",
      """options.PS1 = "test> """",
      """println(s"[${options.PS1}]")"""
      ).out.drop(1).take(5).trim.asBlock shouldBe
    """
    |scala>
    |// mutated options.PS1
    |test> println(s"[${options.PS1}]")
    |[test> ]""".stripMargin
  }

  it should "use the full classpath" in {
    replTest[BaseReplTest]("basic",
      "import com.potenciasoftware.rebel.BaseReplTest.TestValue",
      "val value = TestValue(42)",
      "println(value)"
    ).out.compressed.drop(1).take(5).asBlock shouldBe
    """import com.potenciasoftware.rebel.BaseReplTest.TestValue
    |scala> val value = TestValue(42)
    |val value: com.potenciasoftware.rebel.BaseReplTest.TestValue = TestValue(42)
    |scala> println(value)
    |TestValue(42)""".stripMargin
  }

  def startupScript(input: Seq[String]) = new EchoRepl() {
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
      "printAnswer()").out.drop(1).take(3).asBlock shouldBe
        """
        |scala> printAnswer()
        |The answer is: 42""".stripMargin
  }

  def customCommand(input: Seq[String]) = new EchoRepl(input) { repl =>
    override protected def customCommands: Seq[LoopCommand] =
      Seq(LoopCommand(
        "ps1", "<promptText>", "Change the prompt text",
        { text =>
          repl.prompt = "\n" + text
          LoopCommand.Result(true, None)
        }))
  }

  it should "allow custom commands" in {
    replTest[BaseReplTest]("customCommand",
      ":help ps1", ":ps1 $", "1+1"
    ).out.drop(1).trim.asBlock shouldBe
    """
    |scala>
    |Change the prompt text
    |
    |scala>
    |$1+1
    |val res0: Int = 2
    |
    |$""".stripMargin
  }

  def customQuit(input: Seq[String]) = new TestRepl {
    override def onQuit(): Unit = {
      import zio._
      print("Quitting")
      // The quit command will only wait 5 seconds before issuing a sys.exit()
      Thread.sleep(1.minute.toMillis)
      println("...")
    }
  }

  it should "allow custom logic during quit (up to 5 seconds)" in {
    replTest[BaseReplTest]("customQuit").out(1) shouldBe "scala> Quitting"
  }

  def executionWrapper(input: Seq[String]) = new TestRepl {
    override protected val executionWrapper = TestExecutionWrapper
  }

  it should "allow a custom execution wrapper to be installed" in {
    replTest[BaseReplTest]("executionWrapper", "val two = 1+1")
      .out.take(4).trim.asBlock shouldBe
      """
      |scala>
      |// Custom Wrapping Happened
      |val two: Int = 2""".stripMargin
  }
}

object BaseReplTest {

  class TestRepl extends BaseRepl {
    // Normally we don't need the banner as part of our test output
    override protected val banner: String = ""
  }

  class EchoRepl(inputs: Seq[String] = Seq.empty) extends TestRepl {
    override protected val executionWrapper: ExecutionWrapper = EchoRepl
    EchoRepl.inputs = ("" +: inputs.filterNot(_ startsWith ":")).iterator
  }

  object EchoRepl extends ExecutionWrapper {

    override val code: String =
      "_root_.com.potenciasoftware.rebel.BaseReplTest.EchoRepl.execute"

    private var inputs: Iterator[String] = Iterator.empty

    def execute(a: => Any): String = {
      println(inputs.nextOption().getOrElse(""))
      a.toString
    }
  }

  class Options(repl: BaseRepl) {
    def PS1: String = repl.prompt
    def PS1_=(ps1: String): Unit = { repl.prompt = ps1 }
  }

  case class TestValue(value: Any)

  object TestExecutionWrapper extends ExecutionWrapper {
    override val code: String =
      "_root_.com.potenciasoftware.rebel.BaseReplTest.TestExecutionWrapper.execute"
    def execute(code: => Any): String = {
      "\n// Custom Wrapping Happened\n" + code
    }
  }
}

