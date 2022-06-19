package com.potenciasoftware.rebel.executionWrapper

import sun.misc.Signal
import sun.misc.SignalHandler
import zio._

import ExecuteInFiber.{SIGINT, State, Idle, Running}

/**
 * Execute the command within a ZIO fiber so that commands that
 * run too long can be killed with Ctrl+C without killing the
 * whole REPL.
 */
trait ExecuteInFiber extends ExecutionWrapper with ResultMapper.Identity {

  override val code: String = {
    val cls = getClass()
    Seq(
      cls.getPackage().getName(),
      cls.getSimpleName().stripSuffix("$"),
      "execute") mkString "."
  }

  def execute(a: => Any): String =
    try Runtime.default.unsafeRun {
        for {
          f <- ZIO.attempt(a.toString).sandbox.disconnect.fork
          _ <- runningState(f)
          result <- f.join
            .map(mapResult)
            .ensuring(idleState)
        } yield result
    }
    catch {
      case FiberFailure(Cause.Fail(cause, _)) =>
        cause.asInstanceOf[Cause[Throwable]] match {
          case Cause.Fail(t, _) => throw t
          case Cause.Die(t, _) => throw t
          case Cause.Interrupt(_, _) => throw new InterruptedException()
          case c => throw new Exception(c.prettyPrint)
        }
    }

  private var _state: State = Idle

  // exposed for testing
  private[executionWrapper] def state: State = _state

  private def runningState(f: Fiber.Runtime[_, _]): UIO[Unit] =
    ZIO.succeed {
      installSigInt()
      _state = Running(f)
    }

  private val idleState: UIO[Unit] =
    ZIO.succeed {
      uninstallSigInt()
      _state = Idle
    }

  // exposed for testing
  private[executionWrapper] def cancel(fiber: Fiber.Runtime[_, _]): Unit = {
    Runtime.default.unsafeRun(fiber.interrupt)
  }

  // exposed for testing
  private[executionWrapper] def exit(): Unit = { sys.exit() }

  // exposed for testing
  private[executionWrapper] val handler: SignalHandler = { _ =>
    _state match {

      case Running(fiber) => cancel(fiber)

      // We uninstall this handler when we are trasitioning to Idle state so
      // this case should never be hit. This is here to handle rare conditions.
      case Idle => exit()
    }
  }

  // exposed for testing
  private[executionWrapper] def installSigInt(): Unit =  {
    Signal.handle(SIGINT, handler)
  }

  // exposed for testing
  private[executionWrapper] def uninstallSigInt(): Unit =  {
    Signal.handle(SIGINT, SignalHandler.SIG_DFL)
  }
}

object ExecuteInFiber extends ExecuteInFiber with ResultMapper.Identity {

  // exposed for testing
  private[executionWrapper] final val SIGINT: Signal = new Signal("INT")

  // exposed for testing
  private[executionWrapper] sealed trait State
  private[executionWrapper] case object Idle extends State
  private[executionWrapper] case class Running(
    private[ExecuteInFiber] val fiber: Fiber.Runtime[_, _]
  ) extends State
}

