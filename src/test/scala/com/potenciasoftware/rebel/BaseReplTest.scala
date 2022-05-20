package com.potenciasoftware.rebel

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.tools.nsc.interpreter.shell.ILoop

import TestUtils._

class BaseReplTest extends AnyFlatSpec with Matchers {

  "BaseRepl" should "look behave like the normal ILoop" in {

    replTest[BaseReplTest]("basic", Seq("42 + 42")).out.mkString("\n") shouldBe
    """
    |scala> val res0: Int = 84
    |
    |scala> """.stripMargin
  }

  def basic(): Unit = {
    new BaseRepl {
      override protected def repl: ILoop = new ILoop(config) {
        override def welcome: String = ""
      }
    }.run()
  }
}

