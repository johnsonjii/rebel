package com.potenciasoftware.rebel.executionWrapper

import org.scalamock.scalatest.MockFactory
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import zio._

import ExecuteInFiber.{SIGINT, Idle, Running}

class ExecuteInFiberTest extends AnyFlatSpec with Matchers with MockFactory {

  trait PlainExecuteInFiber extends ExecuteInFiber with ResultMapper.Identity

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

  name("code") should "return the correct command" in {
    ExecuteInFiberTest.code shouldBe
    "com.potenciasoftware.rebel.executionWrapper.ExecuteInFiberTest.execute"
  }

  name("handler") should "call cancel when a fiber is active" in {

    val mockCancel = mockFunction[Fiber.Runtime[_, _], Unit]("cancel")
    val mockExit = mockFunction[Unit]("exit")
    val testExecutor = new PlainExecuteInFiber {
      override private[executionWrapper] def cancel(
        fiber: Fiber.Runtime[_, _]
      ): Unit = mockCancel(fiber)
      override private[rebel] def exit(): Unit = mockExit()
    }

    mockCancel.expects(*)

    Runtime.default.unsafeRun {
      for {
        f <- ZIO.attempt {
          testExecutor.execute {
            Thread.sleep(10000)
          }
        }.disconnect.fork
        _ <- ZIO.sleep(20.millis)
        _ <- ZIO.attempt {
          testExecutor.handler.handle(SIGINT)
        }
        _ <- f.interrupt
      } yield ()
    }
  }

  it should "call exit when a no fiber is active" in {

    val mockCancel = mockFunction[Fiber.Runtime[_, _], Unit]("cancel")
    val mockExit = mockFunction[Unit]("exit")
    val testExecutor = new PlainExecuteInFiber {
      override private[executionWrapper] def cancel(
        fiber: Fiber.Runtime[_, _]
      ): Unit = mockCancel(fiber)
      override private[rebel] def exit(): Unit = mockExit()
    }

    mockExit.expects()

    Runtime.default.unsafeRun {
      for {
        _ <- ZIO.attempt {
          testExecutor.handler.handle(SIGINT)
        }
      } yield ()
    }
  }

  name("execute") should "run in a fiber that is stored in the state var during execution" in {

    val mockInstallSigInt = mockFunction[Unit]("installSigInt")
    val stateIsRunning = mockFunction[Boolean, Unit]("stateIsRunning")
    val mockUninstallSigInt = mockFunction[Unit]("uninstallSigInt")

    val testExecutor = new PlainExecuteInFiber {
      override private[executionWrapper] def installSigInt(): Unit = mockInstallSigInt()
      override private[executionWrapper] def uninstallSigInt(): Unit = mockUninstallSigInt()
    }

    inSequence {
      mockInstallSigInt.expects()
      stateIsRunning.expects(true)
      mockUninstallSigInt.expects()
    }

    testExecutor.state shouldBe Idle
    testExecutor.execute {
      Thread.sleep(20)
      stateIsRunning(testExecutor.state.isInstanceOf[Running])
    }
    testExecutor.state shouldBe Idle
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

    testExecutor.state shouldBe Idle
    Runtime.default.unsafeRun {
      for {
        f <- ZIO.attempt {
          testExecutor.execute {
            Thread.sleep(10000)
          }
        }.fork
        _ <- ZIO.attempt {
          Thread.sleep(20)
          testExecutor.handler.handle(SIGINT)
        }
        r <- f.join
          .catchSome {
            case FiberFailure(cause) =>
              ZIO.failCause(cause)
          }.sandbox.either
      } yield r
    } match {
      case Left(Cause.Interrupt(_, _)) =>
        testExecutor.state shouldBe Idle
      case o => fail(s"Expected the fiber to be interrupted. Got $o instead.")
    }
  }
}

object ExecuteInFiberTest extends ExecuteInFiber with ResultMapper.Identity
