package com.potenciasoftware.rebel.executionWrapper

import org.scalamock.function.MockFunction0
import org.scalamock.scalatest.MockFactory
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import zio._

class ExecuteInFiberTest extends AnyFlatSpec with Matchers with MockFactory {

  trait PlainExecuteInFiber extends ExecuteInFiber with ResultMapper.Identity

  ExecuteInFiberTest.mockInstallSigInt =
    mockFunction[Unit]("installSigInt")

  def name(member: String = null) =
    Option(member)
      .foldLeft("ExecuteInFiber")(_ + "." + _)

  name() should "allow the mapping of results" in {

    val testExecutor = new ExecuteInFiber {
      override def mapResult(result: String): String =
        (result.toInt + 1).toString
    }

    testExecutor.execute(41) shouldBe "42"
  }

  name("code") should "install the signal handler only once and return the correct command multiple times" in {

    ExecuteInFiberTest.mockInstallSigInt.expects().once()

    ExecuteInFiberTest.code shouldBe
    "com.potenciasoftware.rebel.executionWrapper.ExecuteInFiberTest.execute"

    ExecuteInFiberTest.code shouldBe
    "com.potenciasoftware.rebel.executionWrapper.ExecuteInFiberTest.execute"
  }

  name("handler") should "call cancel when a fiber is active" in {

    val mockCancel = mockFunction[Unit]("cancel")
    val mockExit = mockFunction[Unit]("exit")
    val testExecutor = new PlainExecuteInFiber {
      override private[rebel] def cancel(): Unit = mockCancel()
      override private[rebel] def exit(): Unit = mockExit()
    }

    mockCancel.expects()

    Runtime.default.unsafeRun {
      for {
        f <- ZIO.attempt {
          testExecutor.execute {
            Thread.sleep(10000)
          }
        }.forkDaemon
        _ <- ZIO.sleep(20.millis)
        _ <- ZIO.attempt {
          testExecutor.handler.handle(ExecuteInFiber.SIGINT)
        }
        _ <- f.interruptFork
      } yield ()
    }
  }

  it should "call exit when a no fiber is active" in {

    val mockCancel = mockFunction[Unit]("cancel")
    val mockExit = mockFunction[Unit]("exit")
    val testExecutor = new PlainExecuteInFiber {
      override private[rebel] def cancel(): Unit = mockCancel()
      override private[rebel] def exit(): Unit = mockExit()
    }

    mockExit.expects()

    Runtime.default.unsafeRun {
      for {
        _ <- ZIO.attempt {
          testExecutor.handler.handle(ExecuteInFiber.SIGINT)
        }
      } yield ()
    }
  }

  name("execute") should "run in a fiber that is stored in the fiber var during execution" in {

    var isDefinedDuringExecution: Boolean = false

    val testExecutor = new PlainExecuteInFiber {}

    testExecutor.fiber.isDefined shouldBe false
    testExecutor.execute {
      Thread.sleep(20)
      isDefinedDuringExecution = testExecutor.fiber.isDefined
    }
    testExecutor.fiber.isDefined shouldBe false
    isDefinedDuringExecution shouldBe true
  }

  it should "throw expetions thrown during execution" in {

    val testExecutor = new PlainExecuteInFiber {}
    intercept[Exception] {
      testExecutor.execute {
        throw new Exception("Bad thing.")
      }
    }.getMessage shouldBe "Bad thing."
  }

  name("cancel") should "interrupt the active fiber" in {

    val testExecutor = new PlainExecuteInFiber {}

    testExecutor.fiber shouldBe None
    Runtime.default.unsafeRun {
      for {
        f <- ZIO.attempt {
          testExecutor.execute {
            Thread.sleep(2000)
          }
        }.fork
        _ <- ZIO.attempt {
          Thread.sleep(20)
          testExecutor.cancel()
        }
        r <- f.join
          .catchSome {
            case FiberFailure(cause) =>
              ZIO.failCause(cause)
          }.sandbox.either
      } yield r
    } match {
      case Left(Cause.Interrupt(_, _)) =>
        testExecutor.fiber shouldBe None
      case o => fail(s"Expected the fiber to be interrupted. Got $o instead.")
    }
  }

  it should "do nothing when there is no active fiber" in {
    val testExecutor = new PlainExecuteInFiber {}
    testExecutor.fiber shouldBe None
    testExecutor.cancel()
    testExecutor.fiber shouldBe None
  }
}

object ExecuteInFiberTest extends ExecuteInFiber with ResultMapper.Identity {

  var mockInstallSigInt: MockFunction0[Unit] = _

  override def installSigInt(): Unit = mockInstallSigInt()
}
